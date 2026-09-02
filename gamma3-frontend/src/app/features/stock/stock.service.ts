import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Stock, MouvementDto } from '../../core/models/stock.model';

import { environment } from '../../../environments/environment';
@Injectable({
  providedIn: 'root'
})
export class StockService {
  private apiUrl = `${environment.apiUrl}/api/v1/stocks`;

  constructor(private http: HttpClient) {}

  getStockByItemId(itemId: string): Observable<Stock[]> {
    return this.http.get<Stock[]>(`${this.apiUrl}/item/${itemId}`);
  }

  effectuerMouvement(dto: MouvementDto): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/mouvement`, dto);
  }
}
