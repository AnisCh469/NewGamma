import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';

import { environment } from '../../../environments/environment';
export interface AuthResponse {
  token: string;
  matricule: string;
  fullName: string;
  role: string;
  requires2fa?: boolean;
}

export interface LoginRequest {
  matricule: string;
  password: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = `${environment.apiUrl}/api/v1/auth`;
  
  // Angular Signal to hold the current user state
  public currentUser = signal<AuthResponse | null>(this.getUserFromStorage());

  constructor(private http: HttpClient, private router: Router) {}

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, credentials).pipe(
      tap(response => {
        if (response && response.token && !response.requires2fa) {
          localStorage.setItem('gamma3_user', JSON.stringify(response));
          this.currentUser.set(response);
        }
      })
    );
  }

  verify2Fa(matricule: string, code: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/verify-2fa`, { matricule, code }).pipe(
      tap(response => {
        if (response && response.token) {
          localStorage.setItem('gamma3_user', JSON.stringify(response));
          this.currentUser.set(response);
        }
      })
    );
  }

  setup2Fa(matricule: string): Observable<{ secret: string }> {
    return this.http.post<{ secret: string }>(`${this.apiUrl}/2fa/setup?matricule=${matricule}`, {});
  }

  enable2Fa(matricule: string, code: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/2fa/enable?matricule=${matricule}&code=${code}`, {});
  }

  disable2Fa(matricule: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/2fa/disable?matricule=${matricule}`, {});
  }

  logout(): void {
    localStorage.removeItem('gamma3_user');
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    const user = this.currentUser();
    return user ? user.token : null;
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  private getUserFromStorage(): AuthResponse | null {
    const userJson = localStorage.getItem('gamma3_user');
    if (userJson) {
      try {
        return JSON.parse(userJson);
      } catch (e) {
        return null;
      }
    }
    return null;
  }
}
