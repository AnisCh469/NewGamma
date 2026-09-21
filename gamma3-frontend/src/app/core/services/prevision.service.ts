import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AlertePrevision {
  itemId: string;
  nomenclature: string;
  designation: string;
  typeConsommabilite: 'CONSOMMABLE' | 'NON_CONSOMMABLE' | null;
  stockActuel: number;
  quantiteReservee: number;
  stockPotentiel: number;
  consommationMoyenneAnnuelle: number;
  parc: number;
  delaiApprovisionnementAnnees: number;
  seuilAlerte: number;
  seuilSecuriteCalcule: number | null;
  seuilMinimalCalcule: number | null;
  stockSecuriteActuel: number;
}

/**
 * Procédure P01 du CCF (Prévisions) — voir docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md.
 * Réservé à la Division de l'Approvisionnement, cloisonné Section Étude et Prévision (SEP) côté
 * backend (voir PrevisionController).
 */
@Injectable({ providedIn: 'root' })
export class PrevisionService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/v1/previsions`;

  calculerAlertes(): Observable<AlertePrevision[]> {
    return this.http.get<AlertePrevision[]>(this.base);
  }
}
