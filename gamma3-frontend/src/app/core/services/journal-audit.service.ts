import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type TypeActionAudit =
  | 'CONNEXION' | 'CONNEXION_ECHEC'
  | 'DECONNEXION_MANUELLE' | 'DECONNEXION_INACTIVITE' | 'DECONNEXION_FERMETURE'
  | 'PARAMETRE_MODIFIE'
  | 'COMPTE_CREE' | 'COMPTE_MODIFIE' | 'COMPTE_BLOQUE' | 'COMPTE_DEBLOQUE'
  | 'MOT_DE_PASSE_REINITIALISE'
  | 'SAUVEGARDE_DECLENCHEE' | 'SAUVEGARDE_ECHEC';

export interface EntreeAudit {
  id: number;
  dateEvenement: string;
  acteurMatricule: string;
  acteurNom: string | null;
  action: TypeActionAudit;
  details: string | null;
  adresseIp: string | null;
}

export interface PageAudit {
  content: EntreeAudit[];
  totalElements: number;
  totalPages: number;
  number: number; // page courante (0-based)
  size: number;
}

/**
 * Lecture du journal d'audit — traçabilité de chaque intervenant (connexions, déconnexions,
 * modifications de comptes, changements de paramètres système…). Écran Administration >
 * Journal d'Audit, réservé ADMIN. Lecture seule : aucune méthode de modification/suppression.
 */
@Injectable({ providedIn: 'root' })
export class JournalAuditService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/v1/audit-journal`;

  lister(page: number, size: number, matricule?: string, action?: TypeActionAudit): Observable<PageAudit> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (matricule) params['matricule'] = matricule;
    if (action) params['action'] = action;
    return this.http.get<PageAudit>(this.base, { params });
  }
}
