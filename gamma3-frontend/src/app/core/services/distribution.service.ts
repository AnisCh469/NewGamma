import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UniteUtilisatrice } from './unite.service';
import { Item } from '../../features/catalogue/item.service';

import { environment } from '../../../environments/environment';
export interface BonSortie {
  id?: number;
  numeroBs: string;
  dateSortie: string;
  transporteur?: string;
  vehiculeMatricule?: string;
  statut?: 'PREPARE' | 'LIVRE';
  magasinId?: number;
  createdAt?: string;
}

export interface DemandeMateriel {
  id?: number;
  numeroDemande: string;
  dateDemande: string;
  statut?: 'EN_ATTENTE_VALIDATION_COMMANDANT' | 'REFUSE_COMMANDANT' | 'SOUMIS'
    | 'EN_COURS_ARBITRAGE' | 'ESCALADE_SGS' | 'APPROUVE_PARTIEL' | 'APPROUVE_TOTAL'
    | 'REFUSE' | 'LIVRE';
  motifRefus?: string;
  motifEscalade?: string;
  unite: UniteUtilisatrice;
  magasinDesigne?: { id: number; code: string; nom: string; localisation?: string } | null;
  bonSortie?: BonSortie;
  lignes?: LigneDemande[];
  createdAt?: string;
}

export interface ValidationCommandantRequestDto {
  accepter: boolean;
  motifRefus?: string;
}

export interface LigneDemande {
  id?: number;
  item: Item;
  quantiteDemandee: number;
  quantiteAccordee?: number;
}

export interface DemandeRequestDto {
  demande: DemandeMateriel;
  lignes: LigneDemande[];
}

export interface ArbitrageRequestDto {
  decision: 'APPROUVE_TOTAL' | 'APPROUVE_PARTIEL' | 'REFUSE';
  motifRefus?: string;
  lignes: LigneDemande[];
  magasinId?: number;
}

@Injectable({
  providedIn: 'root'
})
export class DistributionService {
  private apiUrl = `${environment.apiUrl}/api/v1/demandes`;

  constructor(private http: HttpClient) { }

  getDemandes(): Observable<DemandeMateriel[]> {
    return this.http.get<DemandeMateriel[]>(this.apiUrl);
  }

  /** File des demandes escaladées en attente de validation de transfert inter-magasin (ADMIN/SGS). */
  getEscalades(): Observable<DemandeMateriel[]> {
    return this.http.get<DemandeMateriel[]>(`${this.apiUrl}/escalades`);
  }

  getDemandesByUnite(uniteId: number): Observable<DemandeMateriel[]> {
    return this.http.get<DemandeMateriel[]>(`${this.apiUrl}/unite/${uniteId}`);
  }

  getLignesByDemande(demandeId: number): Observable<LigneDemande[]> {
    return this.http.get<LigneDemande[]>(`${this.apiUrl}/${demandeId}/lignes`);
  }

  createDemande(dto: DemandeRequestDto): Observable<DemandeMateriel> {
    return this.http.post<DemandeMateriel>(this.apiUrl, dto);
  }

  arbitrerDemande(demandeId: number, dto: ArbitrageRequestDto): Observable<DemandeMateriel> {
    return this.http.post<DemandeMateriel>(`${this.apiUrl}/${demandeId}/arbitrer`, dto);
  }

  /** Le Commandant de l'unité valide ou refuse une demande soumise par son Gestionnaire
   *  (voir StatutDemande : double vérification avant envoi à GAMMA3). */
  validerCommandant(demandeId: number, dto: ValidationCommandantRequestDto): Observable<DemandeMateriel> {
    return this.http.post<DemandeMateriel>(`${this.apiUrl}/${demandeId}/valider-commandant`, dto);
  }

  getLignesDetailleesByDemande(demandeId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/${demandeId}/lignes-detaillees`);
  }

  /** Le DA_MANAGER escalade une demande vers SGS quand son stock local est insuffisant. */
  escaladeDemande(demandeId: number, motifEscalade: string): Observable<DemandeMateriel> {
    return this.http.post<DemandeMateriel>(`${this.apiUrl}/${demandeId}/escalader`, { motifEscalade });
  }

  /** L'ADMIN (SGS) valide (ou refuse) le transfert inter-magasin d'une demande escaladée. */
  validerTransfert(demandeId: number, accepter: boolean, motifRefus?: string): Observable<DemandeMateriel> {
    return this.http.post<DemandeMateriel>(`${this.apiUrl}/${demandeId}/valider-transfert`, { accepter, motifRefus });
  }

  confirmerLivraison(bonSortieId: number): Observable<BonSortie> {
    return this.http.post<BonSortie>(`${this.apiUrl}/bons-sortie/${bonSortieId}/confirmer-livraison`, {});
  }
}
