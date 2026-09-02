import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UniteUtilisatrice } from './unite.service';
import { Item } from '../../features/catalogue/item.service';

import { environment } from '../../../environments/environment';
export interface PlanArmement {
  id?: number;
  unite: UniteUtilisatrice;
  item: Item;
  quantiteType: number;
  quantiteReelle: number;
  quantiteVirtuelle: number;
  updatedAt?: string;
}

export interface DemandeDotation {
  id?: number;
  unite?: UniteUtilisatrice;
  item: Item;
  quantiteType: number;
  statut?: 'SOUMISE' | 'APPROUVEE' | 'REJETEE';
  motifRefus?: string;
  createdAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PlanArmementService {
  private apiUrl = `${environment.apiUrl}/api/v1/plans-armement`;
  private demandesApiUrl = `${environment.apiUrl}/api/v1/demandes-dotation`;

  constructor(private http: HttpClient) { }

  getPlans(): Observable<PlanArmement[]> {
    return this.http.get<PlanArmement[]>(this.apiUrl);
  }

  getPlan(id: number): Observable<PlanArmement> {
    return this.http.get<PlanArmement>(`${this.apiUrl}/${id}`);
  }

  getPlansByUnite(uniteId: number): Observable<PlanArmement[]> {
    return this.http.get<PlanArmement[]>(`${this.apiUrl}/unite/${uniteId}`);
  }

  getPlansByUniteCode(uniteCode: string): Observable<PlanArmement[]> {
    return this.http.get<PlanArmement[]>(`${this.apiUrl}/unite-code/${uniteCode}`);
  }

  createPlan(plan: PlanArmement): Observable<PlanArmement> {
    return this.http.post<PlanArmement>(this.apiUrl, plan);
  }

  updatePlan(id: number, plan: PlanArmement): Observable<PlanArmement> {
    return this.http.put<PlanArmement>(`${this.apiUrl}/${id}`, plan);
  }

  deletePlan(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  // --- Demandes de Dotation (Sprint 13/14 Addition) ---
  getDemandesDotation(): Observable<DemandeDotation[]> {
    return this.http.get<DemandeDotation[]>(this.demandesApiUrl);
  }

  getDemandesDotationByUnite(uniteId: number): Observable<DemandeDotation[]> {
    return this.http.get<DemandeDotation[]>(`${this.demandesApiUrl}/unite/${uniteId}`);
  }

  creerDemandeDotation(demande: DemandeDotation): Observable<DemandeDotation> {
    return this.http.post<DemandeDotation>(this.demandesApiUrl, demande);
  }

  approuverDemandeDotation(id: number): Observable<DemandeDotation> {
    return this.http.post<DemandeDotation>(`${this.demandesApiUrl}/${id}/approuver`, {});
  }

  refuserDemandeDotation(id: number, motif: string): Observable<DemandeDotation> {
    return this.http.post<DemandeDotation>(`${this.demandesApiUrl}/${id}/refuser?motif=${encodeURIComponent(motif)}`, {});
  }
}
