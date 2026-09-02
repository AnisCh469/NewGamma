import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
export interface Kpis {
  totalItems: number;
  totalValue: number;
  alertItems: number;
  pendingDemandes: number;
}

export interface MagasinStat {
  magasinId: number;
  code: string;
  nom: string;
  totalVolume: number;
  totalValue: number;
}

export interface MensuelleStat {
  month: number;
  volume: number;
}

export interface ItemStat {
  nomenclature: string;
  designation: string;
  volumeTotal: number;
}

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  private analyticsUrl = `${environment.apiUrl}/api/v1/analytics`;
  private reportsUrl = `${environment.apiUrl}/api/v1/reports`;

  constructor(private http: HttpClient) { }

  getKpis(): Observable<Kpis> {
    return this.http.get<Kpis>(`${this.analyticsUrl}/kpis`);
  }

  getStockByMagasin(): Observable<MagasinStat[]> {
    return this.http.get<MagasinStat[]>(`${this.analyticsUrl}/stock-by-magasin`);
  }

  getConsommationMensuelle(year?: number): Observable<MensuelleStat[]> {
    let params = new HttpParams();
    if (year) params = params.set('year', year.toString());
    return this.http.get<MensuelleStat[]>(`${this.analyticsUrl}/consommation-mensuelle`, { params });
  }

  getTopItems(): Observable<ItemStat[]> {
    return this.http.get<ItemStat[]>(`${this.analyticsUrl}/top-items`);
  }

  downloadInventaire(format: 'pdf' | 'xlsx'): Observable<Blob> {
    return this.http.get(`${this.reportsUrl}/inventaire`, {
      params: { format },
      responseType: 'blob'
    });
  }

  downloadMouvements(startDate: string, endDate: string, format: 'pdf' | 'xlsx'): Observable<Blob> {
    return this.http.get(`${this.reportsUrl}/mouvements`, {
      params: { startDate, endDate, format },
      responseType: 'blob'
    });
  }

  downloadConsommationUnite(uniteId: number, format: 'pdf' | 'xlsx'): Observable<Blob> {
    return this.http.get(`${this.reportsUrl}/consommation/unite/${uniteId}`, {
      params: { format },
      responseType: 'blob'
    });
  }
}
