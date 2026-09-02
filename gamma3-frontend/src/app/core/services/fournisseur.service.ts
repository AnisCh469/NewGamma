import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
export interface Fournisseur {
  id?: number;
  code: string;
  nom: string;
  matriculeFiscal?: string;
  adresse?: string;
  contactNom?: string;
  telephone?: string;
  email?: string;
  fax?: string;
  note?: number;
  statut?: 'PROSPECT' | 'EN_EVALUATION' | 'HOMOLOGUE' | 'SUSPENDU';
  logoUrl?: string;
  createdAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class FournisseurService {
  private apiUrl = `${environment.apiUrl}/api/v1/fournisseurs`;

  constructor(private http: HttpClient) { }

  getFournisseurs(): Observable<Fournisseur[]> {
    return this.http.get<Fournisseur[]>(this.apiUrl);
  }

  getFournisseur(id: number): Observable<Fournisseur> {
    return this.http.get<Fournisseur>(`${this.apiUrl}/${id}`);
  }

  createFournisseur(fournisseur: Fournisseur): Observable<Fournisseur> {
    return this.http.post<Fournisseur>(this.apiUrl, fournisseur);
  }

  updateFournisseur(id: number, fournisseur: Fournisseur): Observable<Fournisseur> {
    return this.http.put<Fournisseur>(`${this.apiUrl}/${id}`, fournisseur);
  }

  deleteFournisseur(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  uploadLogo(id: number, file: File): Observable<Fournisseur> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Fournisseur>(`${this.apiUrl}/${id}/upload-logo`, formData);
  }
}
