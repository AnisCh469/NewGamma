import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';

import { environment } from '../../../environments/environment';
export type FonctionDACode = 'GEST' | 'ADEST' | 'SC' | 'SCS' | 'SEP' | 'SRR' | 'SRE';

export interface AuthResponse {
  token: string;
  matricule: string;
  fullName: string;
  role: string;
  requires2fa?: boolean;
  /** Rattachement Unité (UNIT_USER uniquement) — voir User.unite côté backend. Depuis le
   *  2026-09-04, chaque unité a deux comptes (Gestionnaire + Commandant) avec des matricules
   *  libres : ne plus supposer que matricule === code de l'unité. */
  uniteId?: number | null;
  uniteCode?: string | null;
  fonctionUnite?: 'GESTIONNAIRE' | 'COMMANDANT' | null;
  /** Section spécialisée Direction de l'Approvisionnement (ADMIN uniquement) — voir
   *  User.fonctionDA côté backend. Permet au frontend de respecter le principe Zero-Trust UI
   *  en masquant les modules et boutons d'action hors habilitation de section. */
  fonctionDA?: FonctionDACode | null;
}

export interface LoginRequest {
  matricule: string;
  password: string;
}

export type MotifDeconnexion = 'MANUEL' | 'INACTIVITE' | 'FERMETURE';

const STORAGE_KEY = 'gamma3_user';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = `${environment.apiUrl}/api/v1/auth`;

  // Angular Signal to hold the current user state
  public currentUser = signal<AuthResponse | null>(this.getUserFromStorage());

  constructor(private http: HttpClient, private router: Router) {}

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, credentials).pipe(
      tap(response => {
        if (response && response.token && !response.requires2fa) {
          this.stockerSession(response);
        }
      })
    );
  }

  verify2Fa(matricule: string, code: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/verify-2fa`, { matricule, code }).pipe(
      tap(response => {
        if (response && response.token) {
          this.stockerSession(response);
        }
      })
    );
  }

  setup2Fa(matricule: string): Observable<{ secret: string }> {
    return this.http.post<{ secret: string }>(`${this.apiUrl}/2fa/setup?matricule=${matricule}`, {});
  }

  enable2Fa(matricule: string, code: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/2fa/enable?matricule=${matricule}&code=${code}`, {});
  }

  disable2Fa(matricule: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/2fa/disable?matricule=${matricule}`, {});
  }

  /**
   * RÔLE : mettre fin à la session, quel qu'en soit le motif (bouton « Déconnexion »,
   * inactivité prolongée, fermeture de l'onglet/navigateur).
   *
   * POURQUOI `sessionStorage` et pas `localStorage` (2026-09-11, motif sécurité) :
   * `sessionStorage` est automatiquement vidé par le navigateur à la fermeture de l'onglet
   * OU du navigateur — exactement le comportement demandé ("terminer la session dès que
   * l'utilisateur ferme l'onglet ou le navigateur"). `localStorage`, lui, survit
   * indéfiniment : un jeton y resterait valable pour quiconque rouvre le navigateur sur ce
   * poste, même des jours plus tard.
   *
   * LIMITE ASSUMÉE : ceci protège le POSTE (le jeton disparaît du navigateur), mais le jeton
   * JWT lui-même reste valable côté serveur jusqu'à sa propre expiration
   * (`application.security.jwt.expiration`, 24 h par défaut) — voir la note dans
   * ParametreSystemeService (backend). Une révocation serveur immédiate demanderait des
   * jetons à courte durée de vie + jeton de rafraîchissement, hors périmètre de ce correctif.
   */
  logout(motif: MotifDeconnexion = 'MANUEL'): void {
    // Best-effort : consigne la déconnexion dans le journal d'audit. Ne bloque jamais la
    // déconnexion elle-même (le jeton est effacé même si cet appel échoue).
    const token = this.getToken();
    if (token) {
      this.http.post(`${this.apiUrl}/logout`, { motif }).subscribe({ error: () => {} });
    }
    sessionStorage.removeItem(STORAGE_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/login'], motif === 'INACTIVITE' ? { queryParams: { expiree: '1' } } : undefined);
  }

  /**
   * Variante appelée depuis `window.beforeunload`/`pagehide` (fermeture d'onglet/navigateur) :
   * ni `subscribe()` ni la navigation Angular ne sont fiables à ce moment précis (la page est
   * en train de se détruire). `fetch(..., { keepalive: true })` est le seul mécanisme qui
   * garantisse (au mieux) l'envoi de la requête après le déchargement de la page — et,
   * contrairement à `navigator.sendBeacon`, il permet d'ajouter l'en-tête d'autorisation.
   * Best-effort par nature : peut ne pas aboutir selon le navigateur/l'OS. La sécurité réelle
   * ne repose PAS sur cet appel mais sur l'effacement automatique de `sessionStorage`.
   */
  notifierFermeture(): void {
    const token = this.getToken();
    if (!token) return;
    try {
      fetch(`${this.apiUrl}/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ motif: 'FERMETURE' }),
        keepalive: true
      }).catch(() => {});
    } catch { /* best-effort */ }
  }

  getToken(): string | null {
    const user = this.currentUser();
    return user ? user.token : null;
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  /**
   * RÔLE : Vérifie si l'utilisateur actuellement connecté est habilité à accéder
   * à une ressource ou action rattachée à une (ou plusieurs) section(s) DA.
   *
   * CONFORMITÉ ZERO-TRUST UI (.antigravityrules §1) & CCF Phase B :
   * Cette logique reproduit strictement la matrice de décision du backend (AccesDAUtil.java) :
   * 1. SUPER_ADMIN : toujours autorisé (superset système).
   * 2. DA_MANAGER : autorisé selon le paramètre daManagerAutorise.
   * 3. ADMIN généraliste (sans section ou explicitement 'GEST') : toujours autorisé (comportement historique).
   * 4. ADMIN restreint (ex: 'SRR', 'SRE', 'SEP', 'SC', 'SCS') : autorisé UNIQUEMENT si sa section figure dans les sections demandées.
   * 5. Tout autre rôle (UNIT_USER, non connecté) : refusé (false).
   *
   * @param sections Liste des codes de section autorisés (ex: ['SRR'])
   * @param daManagerAutorise Indique si un magasinier (DA_MANAGER) est habilité sur cet écran/action (true par défaut)
   * @returns boolean true si autorisé, false sinon
   */
  hasSectionAccess(
    sections: FonctionDACode[],
    daManagerAutorise: boolean = true
  ): boolean {
    const user = this.currentUser();
    if (!user) return false;
    if (user.role === 'SUPER_ADMIN') return true;
    if (user.role === 'DA_MANAGER') return daManagerAutorise;
    if (user.role === 'ADMIN') {
      const f = user.fonctionDA;
      if (!f || f === 'GEST') return true;
      return sections.includes(f);
    }
    return false;
  }

  private stockerSession(response: AuthResponse): void {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(response));
    this.currentUser.set(response);
  }

  private getUserFromStorage(): AuthResponse | null {
    const userJson = sessionStorage.getItem(STORAGE_KEY);
    if (userJson) {
      try {
        return JSON.parse(userJson);
      } catch (e) {
        return null;
      }
    }
    return null;
  }
}
