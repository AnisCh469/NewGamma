import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export type Role = 'SUPER_ADMIN' | 'ADMIN' | 'DA_MANAGER' | 'UNIT_USER';
export type FonctionUnite = 'GESTIONNAIRE' | 'COMMANDANT';

export interface Magasin {
  id?: number;
  code: string;
  nom: string;
  localisation?: string;
}

export interface UniteUtilisatriceRef {
  id?: number;
  code: string;
  nom: string;
}

export interface AdminUser {
  id?: number;
  matricule: string;
  fullName: string;
  role: Role;
  magasin?: Magasin | null;
  unite?: UniteUtilisatriceRef | null;
  fonctionUnite?: FonctionUnite | null;
  enabled?: boolean;
  twoFactorEnabled?: boolean;
}

export interface CreateUserRequest {
  matricule: string;
  password: string;
  fullName: string;
  role: Role;
  magasinId?: number | null;
  uniteId?: number | null;
  fonctionUnite?: FonctionUnite | null;
}

export interface UpdateUserRequest {
  fullName: string;
  role: Role;
  magasinId?: number | null;
  uniteId?: number | null;
  fonctionUnite?: FonctionUnite | null;
}

@Injectable({
  providedIn: 'root'
})
export class UserAdminService {
  private apiUrl = `${environment.apiUrl}/api/v1/admin/users`;

  constructor(private http: HttpClient) { }

  getUsers(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>(this.apiUrl);
  }

  getMagasins(): Observable<Magasin[]> {
    return this.http.get<Magasin[]>(`${this.apiUrl}/magasins`);
  }

  getUnites(): Observable<UniteUtilisatriceRef[]> {
    return this.http.get<UniteUtilisatriceRef[]>(`${this.apiUrl}/unites`);
  }

  createUser(request: CreateUserRequest): Observable<AdminUser> {
    return this.http.post<AdminUser>(this.apiUrl, request);
  }

  updateUser(id: number, request: UpdateUserRequest): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.apiUrl}/${id}`, request);
  }

  setEnabled(id: number, enabled: boolean): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.apiUrl}/${id}/enabled`, { enabled });
  }

  resetPassword(id: number, newPassword: string): Observable<{ message: string }> {
    return this.http.put<{ message: string }>(`${this.apiUrl}/${id}/password`, { newPassword });
  }
}
