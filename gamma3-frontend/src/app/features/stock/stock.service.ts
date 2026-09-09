import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Stock, MouvementDto } from '../../core/models/stock.model';

import { environment } from '../../../environments/environment';

/** Une ligne de l'historique des mouvements de stock d'un article. */
export interface MouvementHistorique {
  date: string;
  type: 'ENTREE' | 'SORTIE' | 'INVENTAIRE' | 'REFORME';
  sens: '+' | '-' | '=';
  quantite: number;
  magasinCode: string;
  magasinNom: string;
  reference: string;
  motif: string;
  agent: string;
}
@Injectable({
  providedIn: 'root'
})
export class StockService {
  private apiUrl = `${environment.apiUrl}/api/v1/stocks`;

  constructor(private http: HttpClient) {}

  getStockByItemId(itemId: string): Observable<Stock[]> {
    return this.http.get<Stock[]>(`${this.apiUrl}/item/${itemId}`);
  }

  /** Historique des mouvements de stock d'un article (réceptions, sorties, transferts, réformes). */
  getItemHistorique(itemId: string): Observable<MouvementHistorique[]> {
    return this.http.get<MouvementHistorique[]>(`${this.apiUrl}/item/${itemId}/historique`);
  }

  effectuerMouvement(dto: MouvementDto): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/mouvement`, dto);
  }
}
