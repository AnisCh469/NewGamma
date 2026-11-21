import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ModeAcquisition } from './prevision.service';

export type StatutAchat = 'DOSSIER_CREE' | 'CONSULTATION_LANCEE' | 'OFFRES_DEPOUILLEES' | 'COMMANDE_NOTIFIEE' | 'LIVRAISON_PARTIELLE' | 'SOLDE';

export interface LigneDossierAchat {
  id?: string;
  dossierAchatId?: string;
  itemId?: string;
  itemNomenclature?: string;
  designation: string;
  reference?: string;
  quantiteCommandee: number;
  prixUnitaire?: number;
  montantTotal?: number;
  sourceLigneBesoinId?: string;
}

export interface DossierAchat {
  id?: string;
  numeroDossier?: string;
  annee: number;
  objet: string;
  modeAcquisition: ModeAcquisition;
  modeAcquisitionLibelle?: string;
  fournisseurId?: number;
  fournisseurNom?: string;
  statut: StatutAchat;
  statutLibelle?: string;
  referenceMarcheOuBon?: string;
  montantEstimeTotal?: number;
  montantReelTotal?: number;
  dateCreation?: string;
  dateNotification?: string;
  dateLivraisonPrevue?: string;
  observations?: string;
  creeParNom?: string;
  lignes: LigneDossierAchat[];
}

@Injectable({ providedIn: 'root' })
export class DossierAchatService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/v1/commandes/achats`;

  getAchats(annee?: number, statut?: StatutAchat): Observable<DossierAchat[]> {
    let params = new HttpParams();
    if (annee) params = params.set('annee', annee.toString());
    if (statut) params = params.set('statut', statut);
    return this.http.get<DossierAchat[]>(this.base, { params });
  }

  getById(id: string): Observable<DossierAchat> {
    return this.http.get<DossierAchat>(`${this.base}/${id}`);
  }

  enregistrerAchat(dto: DossierAchat): Observable<DossierAchat> {
    return this.http.post<DossierAchat>(this.base, dto);
  }

  changerStatut(id: string, statut: StatutAchat, referenceMarche?: string, dateNotification?: string, observations?: string): Observable<DossierAchat> {
    let params = new HttpParams().set('statut', statut);
    if (referenceMarche) params = params.set('referenceMarche', referenceMarche);
    if (dateNotification) params = params.set('dateNotification', dateNotification);
    if (observations) params = params.set('observations', observations);
    return this.http.patch<DossierAchat>(`${this.base}/${id}/statut`, {}, { params });
  }
}
