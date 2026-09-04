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
}
