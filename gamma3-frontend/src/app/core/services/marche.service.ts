import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Fournisseur } from './fournisseur.service';

import { environment } from '../../../environments/environment';
export interface Marche {
  id?: number;
  numeroMarche: string;
  designation: string;
  montantTotalHt: number;
  montantTotalTtc: number;
  dateDebut?: string;
  dateFin?: string;
  fournisseur: Fournisseur;
  statut?: 'ACTIF' | 'CLOTURE';
  createdAt?: string;
}

export interface CommandeFournisseur {
  id?: number;
  numeroCommande: string;
  dateCommande: string;
  montantTotal: number;
  fournisseur: Fournisseur;
  marche?: Marche;
  statut?: 'EN_ATTENTE' | 'LIVRE' | 'ANNULE';
  createdAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class MarcheService {
  private apiUrl = `${environment.apiUrl}/api/v1/marches`;

  constructor(private http: HttpClient) { }

  // --- MARCHES ---

  getMarches(): Observable<Marche[]> {
    return this.http.get<Marche[]>(this.apiUrl);
  }

  getMarche(id: number): Observable<Marche> {
    return this.http.get<Marche>(`${this.apiUrl}/${id}`);
  }

  getMarchesByFournisseur(fournisseurId: number): Observable<Marche[]> {
    return this.http.get<Marche[]>(`${this.apiUrl}/fournisseur/${fournisseurId}`);
  }

  createMarche(marche: Marche): Observable<Marche> {
    return this.http.post<Marche>(this.apiUrl, marche);
  }

  updateMarche(id: number, marche: Marche): Observable<Marche> {
    return this.http.put<Marche>(`${this.apiUrl}/${id}`, marche);
  }

  deleteMarche(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  // --- COMMANDES FOURNISSEURS ---

  getCommandes(): Observable<CommandeFournisseur[]> {
    return this.http.get<CommandeFournisseur[]>(`${this.apiUrl}/commandes`);
  }

  getCommandesByFournisseur(fournisseurId: number): Observable<CommandeFournisseur[]> {
    return this.http.get<CommandeFournisseur[]>(`${this.apiUrl}/commandes/fournisseur/${fournisseurId}`);
  }

  getCommandesByMarche(marcheId: number): Observable<CommandeFournisseur[]> {
    return this.http.get<CommandeFournisseur[]>(`${this.apiUrl}/commandes/marche/${marcheId}`);
  }

  createCommande(commande: CommandeFournisseur): Observable<CommandeFournisseur> {
    return this.http.post<CommandeFournisseur>(`${this.apiUrl}/commandes`, commande);
  }

  deleteCommande(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/commandes/${id}`);
  }
}
