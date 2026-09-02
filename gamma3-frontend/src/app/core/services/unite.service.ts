import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
export interface UniteUtilisatrice {
  id?: number;
  code: string;
  nom: string;
  baseNavale?: string;
  logoUrl?: string;
  planCount?: number;
  createdAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UniteService {
  private apiUrl = `${environment.apiUrl}/api/v1/unites`;

  constructor(private http: HttpClient) { }

  getUnites(): Observable<UniteUtilisatrice[]> {
    return this.http.get<UniteUtilisatrice[]>(this.apiUrl);
  }

  getUnite(id: number): Observable<UniteUtilisatrice> {
    return this.http.get<UniteUtilisatrice>(`${this.apiUrl}/${id}`);
  }

  getUniteByCode(code: string): Observable<UniteUtilisatrice> {
    return this.http.get<UniteUtilisatrice>(`${this.apiUrl}/code/${code}`);
  }

  createUnite(unite: UniteUtilisatrice): Observable<UniteUtilisatrice> {
    return this.http.post<UniteUtilisatrice>(this.apiUrl, unite);
  }

  updateUnite(id: number, unite: UniteUtilisatrice): Observable<UniteUtilisatrice> {
    return this.http.put<UniteUtilisatrice>(`${this.apiUrl}/${id}`, unite);
  }

  deleteUnite(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  uploadLogo(id: number, file: File): Observable<UniteUtilisatrice> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<UniteUtilisatrice>(`${this.apiUrl}/${id}/upload-logo`, formData);
  }
}
