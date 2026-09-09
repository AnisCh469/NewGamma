import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface LegacyTableInfo {
  schema: string;
  table: string;
  rowCount: number;
}

export interface AuditReport {
  legacyReachable: boolean;
  legacyError: string | null;
  legacyTables: LegacyTableInfo[];
  gamma3Counts: Record<string, number>;
  connuMigre: string[];
  aVerifier: string[];
}

@Injectable({
  providedIn: 'root'
})
export class MigrationAuditService {
  private apiUrl = `${environment.apiUrl}/api/v1/migration`;

  constructor(private http: HttpClient) { }

  getAudit(): Observable<AuditReport> {
    return this.http.get<AuditReport>(`${this.apiUrl}/audit`);
  }

  /** Rapatrie l'historique des mouvements de stock de Gamma 2 (idempotent, archives seulement). */
  migrerMouvements(): Observable<MigrationMouvementsResult> {
    return this.http.post<MigrationMouvementsResult>(`${this.apiUrl}/mouvements-stock`, {});
  }

  /** Recharge le Plan d'Armement depuis Gamma 2 (idempotent : remplace tout). */
  migrerPlanArmement(): Observable<string> {
    return this.http.post(`${this.apiUrl}/plan-armement`, {}, { responseType: 'text' });
  }
}

export interface MigrationMouvementsResult {
  message: string;
  lignesLues: number;
  mouvementsInseres: number;
  ignoresArticleNonMigre: number;
  dureeMs: number;
}
