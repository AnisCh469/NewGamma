import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AlertePrevision {
  itemId: string;
  nomenclature: string;
  designation: string;
  typeConsommabilite: 'CONSOMMABLE' | 'NON_CONSOMMABLE' | null;
  stockActuel: number;
  quantiteReservee: number;
  stockPotentiel: number;
  consommationMoyenneAnnuelle: number;
  parc: number;
  delaiApprovisionnementAnnees: number;
  seuilAlerte: number;
  seuilSecuriteCalcule: number | null;
  seuilMinimalCalcule: number | null;
  stockSecuriteActuel: number;
}

export type PrioriteBesoin = 'PRIORITE_1' | 'PRIORITE_2' | 'PRIORITE_3';
export type StatutBesoin = 'BROUILLON' | 'SOUMIS' | 'ETUDIE_SEP' | 'ARBITRE' | 'VALIDE_PADI' | 'CLOTURE';
export type ModeAcquisition = 'MARCHE_CADRE' | 'APPEL_OFFRES' | 'BON_COMMANDE' | 'REGIE' | 'FMS';
export type DecisionCommission = 'EN_ATTENTE' | 'ACCORDE' | 'REJETE' | 'AJOURNE';

export interface LigneExpressionBesoin {
  id?: string;
  expressionBesoinId?: string;
  itemId?: string;
  itemNomenclature?: string;
  itemDesignation?: string;
  nouveauBesoin: boolean;
  designationHorsCatalogue?: string;
  referenceConstructeur?: string;
  equipementParent?: string;
  justificationTechnique?: string;
  quantiteDemandee: number;
  priorite: PrioriteBesoin;
  prioriteLibelle?: string;
  quantiteProposeeSEP?: number;
  avisSEP?: string;
  quantiteRetenue?: number;
  prixUnitaireEstime?: number;
  modeAcquisitionRecommande?: ModeAcquisition;
  decisionCommission?: DecisionCommission;
  decisionLibelle?: string;
  motifArbitrage?: string;
  transfereAchat?: boolean;
}

export interface ExpressionBesoin {
  id?: string;
  numeroDossier?: string;
  anneeBudgetaire: number;
  uniteId?: number;
  uniteCode?: string;
  uniteNom?: string;
  objet: string;
  commentaires?: string;
  statut?: StatutBesoin;
  statutLibelle?: string;
  dateCreation?: string;
  dateSoumission?: string;
  dateArbitrage?: string;
  creeParNom?: string;
  arbitreParNom?: string;
  lignes: LigneExpressionBesoin[];
}

export interface SyntheseConsolidee {
  itemId?: string;
  nomenclature: string;
  designation: string;
  nouveauBesoin: boolean;
  referenceConstructeur?: string;
  totalQuantiteDemandee: number;
  nombreUnitesDemandeuses: number;
  stockActuel: number;
  quantiteReservee: number;
  stockPotentiel: number;
  consommationMoyenneAnnuelle: number;
  seuilAlerte: number;
  deficitCalcule: number;
  quantiteRecommandeeAchat: number;
  prioriteLaPlusHaute: PrioriteBesoin;
}

@Injectable({ providedIn: 'root' })
export class PrevisionService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/v1/previsions`;

  calculerAlertes(): Observable<AlertePrevision[]> {
    return this.http.get<AlertePrevision[]>(this.base);
  }

  getBesoins(annee?: number, statut?: StatutBesoin): Observable<ExpressionBesoin[]> {
    let params = new HttpParams();
    if (annee) params = params.set('annee', annee.toString());
    if (statut) params = params.set('statut', statut);
    return this.http.get<ExpressionBesoin[]>(`${this.base}/besoins`, { params });
  }

  getBesoinById(id: string): Observable<ExpressionBesoin> {
    return this.http.get<ExpressionBesoin>(`${this.base}/besoins/${id}`);
  }

  enregistrerBesoin(dto: ExpressionBesoin): Observable<ExpressionBesoin> {
    return this.http.post<ExpressionBesoin>(`${this.base}/besoins`, dto);
  }

  soumettreBesoin(id: string): Observable<ExpressionBesoin> {
    return this.http.post<ExpressionBesoin>(`${this.base}/besoins/${id}/soumettre`, {});
  }

  etudierSEP(id: string, etudes: LigneExpressionBesoin[]): Observable<ExpressionBesoin> {
    return this.http.post<ExpressionBesoin>(`${this.base}/besoins/${id}/etudier-sep`, etudes);
  }

  arbitrerCommission(id: string, arbitrages: LigneExpressionBesoin[]): Observable<ExpressionBesoin> {
    return this.http.post<ExpressionBesoin>(`${this.base}/besoins/${id}/arbitrer`, arbitrages);
  }

  validerPADI(id: string): Observable<ExpressionBesoin> {
    return this.http.post<ExpressionBesoin>(`${this.base}/besoins/${id}/valider-padi`, {});
  }

  transfererAchats(annee: number, mode: ModeAcquisition, ligneIds: string[]): Observable<string> {
    let params = new HttpParams().set('annee', annee.toString()).set('mode', mode);
    return this.http.post<string>(`${this.base}/besoins/transferer-achats`, ligneIds, { params, responseType: 'text' as 'json' });
  }

  getSyntheseConsolidee(annee?: number): Observable<SyntheseConsolidee[]> {
    let params = new HttpParams();
    if (annee) params = params.set('annee', annee.toString());
    return this.http.get<SyntheseConsolidee[]>(`${this.base}/synthese-consolidee`, { params });
  }
}
