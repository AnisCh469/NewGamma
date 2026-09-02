import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UniteUtilisatrice } from './unite.service';
import { Item } from '../../features/catalogue/item.service';

import { environment } from '../../../environments/environment';
export interface BonSortie {
  id?: number;
  numeroBs: string;
  dateSortie: string;
  transporteur?: string;
  vehiculeMatricule?: string;
  statut?: 'PREPARE' | 'LIVRE';
  magasinId?: number;
  createdAt?: string;
}

export interface DemandeMateriel {
  id?: number;
  numeroDemande: string;
  dateDemande: string;
  statut?: 'SOUMIS' | 'APPROUVE_PARTIEL' | 'APPROUVE_TOTAL' | 'REFUSE';
  motifRefus?: string;
  unite: UniteUtilisatrice;
  bonSortie?: BonSortie;
  lignes?: LigneDemande[];
  createdAt?: string;
}

export interface LigneDemande {
  id?: number;
  item: Item;
  quantiteDemandee: number;
  quantiteAccordee?: number;
}

export interface DemandeRequestDto {
  demande: DemandeMateriel;
  lignes: LigneDemande[];
}

export interface ArbitrageRequestDto {
  decision: 'APPROUVE_TOTAL' | 'APPROUVE_PARTIEL' | 'REFUSE';
  motifRefus?: string;
  lignes: LigneDemande[];
  magasinId?: number;
}

@Injectable({
  providedIn: 'root'
})
export class DistributionService {
  private apiUrl = `${environment.apiUrl}/api/v1/demandes`;

  constructor(private http: HttpClient) { }

  getDemandes(): Observable<DemandeMateriel[]> {
    return this.http.get<DemandeMateriel[]>(this.apiUrl);
  }

  getDemandesByUnite(uniteId: number): Observable<DemandeMateriel[]> {
    return this.http.get<DemandeMateriel[]>(`${this.apiUrl}/unite/${uniteId}`);
  }

  getLignesByDemande(demandeId: number): Observable<LigneDemande[]> {
    return this.http.get<LigneDemande[]>(`${this.apiUrl}/${demandeId}/lignes`);
  }

  createDemande(dto: DemandeRequestDto): Observable<DemandeMateriel> {
    return this.http.post<DemandeMateriel>(this.apiUrl, dto);
  }

  arbitrerDemande(demandeId: number, dto: ArbitrageRequestDto): Observable<DemandeMateriel> {
    return this.http.post<DemandeMateriel>(`${this.apiUrl}/${demandeId}/arbitrer`, dto);
  }

  getLignesDetailleesByDemande(demandeId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/${demandeId}/lignes-detaillees`);
  }

  confirmerLivraison(bonSortieId: number): Observable<BonSortie> {
    return this.http.post<BonSortie>(`${this.apiUrl}/bons-sortie/${bonSortieId}/confirmer-livraison`, {});
  }
}
