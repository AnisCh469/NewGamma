import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Fournisseur } from './fournisseur.service';
import { CommandeFournisseur } from './marche.service';
import { Item } from '../../features/catalogue/item.service';
import { Magasin } from '../models/stock.model';

import { environment } from '../../../environments/environment';
export interface BonProvisoireReception {
  id?: number;
  numeroBpr: string;
  dateReception: string;
  statut?: 'ATTENTE_PV' | 'VALIDE' | 'REJETE';
  fournisseur: Fournisseur;
  commande?: CommandeFournisseur;
  magasin: Magasin;
  createdAt?: string;
}

export interface LigneReception {
  id?: number;
  bpr?: BonProvisoireReception;
  item: Item;
  quantiteLivree: number;
  prixUnitaire?: number;
}

export interface PvCommission {
  id?: number;
  numeroPv: string;
  dateCommission: string;
  membres: string;
  decision: 'ACCEPTE' | 'ACCEPTE_AVEC_RESERVE' | 'REFUSE';
  observations?: string;
  bpr: BonProvisoireReception;
  createdAt?: string;
}

export interface BonEntree {
  id?: number;
  numeroBe: string;
  dateEntree: string;
  pvCommission: PvCommission;
  createdAt?: string;
}

export interface BprRequestDto {
  bpr: BonProvisoireReception;
  lignes: LigneReception[];
}

@Injectable({
  providedIn: 'root'
})
export class ReceptionService {
  private apiUrl = `${environment.apiUrl}/api/v1/receptions`;

  constructor(private http: HttpClient) { }

  // --- MAGASINS ---
  getMagasins(): Observable<Magasin[]> {
    return this.http.get<Magasin[]>(`${this.apiUrl}/magasins`);
  }

  // --- BPR ---
  getAllBpr(): Observable<BonProvisoireReception[]> {
    return this.http.get<BonProvisoireReception[]>(`${this.apiUrl}/bpr`);
  }

  getBpr(id: number): Observable<BonProvisoireReception> {
    return this.http.get<BonProvisoireReception>(`${this.apiUrl}/bpr/${id}`);
  }

  getLignesByBpr(bprId: number): Observable<LigneReception[]> {
    return this.http.get<LigneReception[]>(`${this.apiUrl}/bpr/${bprId}/lignes`);
  }

  createBpr(dto: BprRequestDto): Observable<BonProvisoireReception> {
    return this.http.post<BonProvisoireReception>(`${this.apiUrl}/bpr`, dto);
  }

  getPvByBpr(bprId: number): Observable<PvCommission> {
    return this.http.get<PvCommission>(`${this.apiUrl}/bpr/${bprId}/pv`);
  }

  // --- PV ---
  getAllPv(): Observable<PvCommission[]> {
    return this.http.get<PvCommission[]>(`${this.apiUrl}/pv`);
  }

  createPv(pv: PvCommission): Observable<PvCommission> {
    return this.http.post<PvCommission>(`${this.apiUrl}/pv`, pv);
  }

  getBeByPv(pvId: number): Observable<BonEntree> {
    return this.http.get<BonEntree>(`${this.apiUrl}/pv/${pvId}/be`);
  }

  // --- BE ---
  getAllBe(): Observable<BonEntree[]> {
    return this.http.get<BonEntree[]>(`${this.apiUrl}/be`);
  }

  createBe(be: BonEntree): Observable<BonEntree> {
    return this.http.post<BonEntree>(`${this.apiUrl}/be`, be);
  }
}
