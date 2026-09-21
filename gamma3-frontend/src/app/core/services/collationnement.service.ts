import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UniteUtilisatrice } from './unite.service';
import { Item } from '../../features/catalogue/item.service';

export interface CampagneCollationnement {
  id: number;
  unite: UniteUtilisatrice;
  datePlanifiee: string;
  statut: 'PLANIFIEE' | 'EN_COURS' | 'TERMINEE';
  membresCommission: string | null;
  dateCloture: string | null;
  conclusionGenerale: 'CONFORME' | 'ECART_CONSTATE' | null;
  observationsGenerales: string | null;
  createdAt: string;
}

export interface LigneCollationnement {
  id: number;
  item: Item;
  quantiteTheorique: number;
  quantitePhysiqueConstatee: number | null;
  observations: string | null;
  ecart: number | null;
}

export interface LigneComptage {
  ligneId: number;
  quantitePhysiqueConstatee: number | null;
  observations: string | null;
}

/**
 * Procédure P07 du CCF (Comptabilité / Collationnement annuel) — voir
 * docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md. Réservé à la Division de l'Approvisionnement,
 * cloisonné Section Comptabilité (SC) côté backend (voir CollationnementController).
 */
@Injectable({ providedIn: 'root' })
export class CollationnementService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/v1/collationnements`;

  lister(): Observable<CampagneCollationnement[]> {
    return this.http.get<CampagneCollationnement[]>(this.base);
  }

  getCampagne(id: number): Observable<CampagneCollationnement> {
    return this.http.get<CampagneCollationnement>(`${this.base}/${id}`);
  }

  getLignes(id: number): Observable<LigneCollationnement[]> {
    return this.http.get<LigneCollationnement[]>(`${this.base}/${id}/lignes`);
  }

  creerCampagne(uniteId: number, datePlanifiee: string, membresCommission: string): Observable<CampagneCollationnement> {
    return this.http.post<CampagneCollationnement>(this.base, { uniteId, datePlanifiee, membresCommission });
  }

  saisirComptage(id: number, lignes: LigneComptage[]): Observable<CampagneCollationnement> {
    return this.http.put<CampagneCollationnement>(`${this.base}/${id}/comptage`, { lignes });
  }

  cloturer(id: number, observationsGenerales: string): Observable<CampagneCollationnement> {
    return this.http.post<CampagneCollationnement>(`${this.base}/${id}/cloturer`, { observationsGenerales });
  }
}
