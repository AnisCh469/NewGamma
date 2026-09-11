import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Paramètres système modifiables par l'ADMIN. Premier réglage exposé : le délai d'inactivité
 * avant déconnexion automatique (voir InactivityService).
 */
@Injectable({ providedIn: 'root' })
export class ParametreService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/v1/parametres`;

  getInactiviteMinutes(): Observable<{ inactiviteMinutes: number }> {
    return this.http.get<{ inactiviteMinutes: number }>(`${this.base}/session`);
  }

  setInactiviteMinutes(minutes: number): Observable<{ inactiviteMinutes: number }> {
    return this.http.put<{ inactiviteMinutes: number }>(`${this.base}/session`, { inactiviteMinutes: minutes });
  }
}
