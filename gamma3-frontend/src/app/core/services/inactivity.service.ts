import { Injectable, NgZone, inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { ParametreService } from './parametre.service';

/**
 * RÔLE : déconnecter automatiquement l'utilisateur après une période d'inactivité prolongée
 * (aucun clic, frappe clavier, défilement ni mouvement de souris), demande utilisateur du
 * 2026-09-11 pour motif de sécurité. Durée par défaut 5 minutes, configurable par
 * l'administrateur (voir ParametreService / écran Administration > Sécurité de session).
 *
 * FONCTIONNEMENT : un minuteur est (ré)armé à chaque événement d'activité détecté sur le
 * document. S'il arrive à expiration sans avoir été réarmé, la session est terminée
 * (AuthService.logout('INACTIVITE')) et l'utilisateur est renvoyé à l'écran de connexion.
 *
 * PERFORMANCE : les écouteurs sont attachés HORS de la zone Angular (`ngZone.runOutsideAngular`)
 * pour qu'un simple mouvement de souris ne déclenche pas un cycle de détection de changements
 * Angular à chaque pixel parcouru — seul le déclenchement final de la déconnexion rentre dans
 * la zone Angular (`ngZone.run`), via AuthService.
 */
@Injectable({ providedIn: 'root' })
export class InactivityService {
  private authService = inject(AuthService);
  private parametreService = inject(ParametreService);
  private ngZone = inject(NgZone);

  private timeoutId: ReturnType<typeof setTimeout> | null = null;
  private delaiMs = 5 * 60 * 1000; // valeur de repli tant que le paramètre n'est pas encore chargé
  private actif = false;
  private readonly evenements = ['click', 'keydown', 'mousemove', 'scroll', 'touchstart'] as const;
  private readonly ecouteur = () => this.rearmer();

  demarrer(): void {
    if (this.actif) return;
    this.actif = true;

    this.parametreService.getInactiviteMinutes().subscribe({
      next: (r) => { this.delaiMs = Math.max(1, r.inactiviteMinutes) * 60 * 1000; this.rearmer(); },
      error: () => this.rearmer() // repli sur la valeur par défaut (5 min) si le paramètre n'a pas pu être lu
    });

    this.ngZone.runOutsideAngular(() => {
      this.evenements.forEach(ev => document.addEventListener(ev, this.ecouteur, { passive: true }));
    });
  }

  arreter(): void {
    this.actif = false;
    this.evenements.forEach(ev => document.removeEventListener(ev, this.ecouteur));
    if (this.timeoutId) { clearTimeout(this.timeoutId); this.timeoutId = null; }
  }

  private rearmer(): void {
    if (!this.actif) return;
    if (this.timeoutId) clearTimeout(this.timeoutId);
    this.timeoutId = setTimeout(() => {
      this.ngZone.run(() => {
        this.arreter();
        this.authService.logout('INACTIVITE');
      });
    }, this.delaiMs);
  }
}
