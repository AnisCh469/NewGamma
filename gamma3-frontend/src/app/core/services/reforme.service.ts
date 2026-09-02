import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
export interface LigneReforme {
  id?: number;
  item: { id: any; designation?: string; nomenclature?: string };
  quantite: number;
  observations?: string;
}

export interface DossierReforme {
  id?: number;
  numeroDossier?: string;
  dateDemande: string;
  unite: { id: number; designation?: string; code?: string };
  statut: string; // BROUILLON, SOUMIS, COMMISSION, TRAITE, REJETE
  motif: string;
  decisionCommission?: string;
  dateCommission?: string;
  membresCommission?: string;
  observationsCommission?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ReformeService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/v1/reformes`;

  getAllDossiers(): Observable<DossierReforme[]> {
    return this.http.get<DossierReforme[]>(this.apiUrl);
  }

  getDossiersByUnite(uniteId: number): Observable<DossierReforme[]> {
    return this.http.get<DossierReforme[]>(`${this.apiUrl}/unite/${uniteId}`);
  }

  getLignesByDossier(dossierId: number): Observable<LigneReforme[]> {
    return this.http.get<LigneReforme[]>(`${this.apiUrl}/${dossierId}/lignes`);
  }

  creerDossier(uniteId: number, motif: string, lignes: LigneReforme[]): Observable<DossierReforme> {
    return this.http.post<DossierReforme>(`${this.apiUrl}?uniteId=${uniteId}&motif=${motif}`, lignes);
  }

  traiterCommission(id: number, decision: string, membres: string, observations: string, date?: string): Observable<DossierReforme> {
    let url = `${this.apiUrl}/${id}/commission?decision=${decision}&membres=${membres}&observations=${observations}`;
    if (date) url += `&dateCommission=${date}`;
    return this.http.post<DossierReforme>(url, {});
  }
}
