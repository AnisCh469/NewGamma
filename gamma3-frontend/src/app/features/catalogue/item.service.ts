import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
export interface Item {
  id: string;
  classeCode: string;
  sousClasseCode: string;
  categorieCode: string;
  serieCode: string;
  itemCode: string;
  nomenclature: string;
  designation: string;
  prixUnitaire: number;
  stockSecurite: number;
  uniteGestionCode?: string;
  photoUrl?: string;
  quantiteTotale: number;
  quantiteReservee?: number;
  quantiteType?: number;
  quantiteReelle?: number;
  documents?: { id: string, fileName: string, fileUrl: string }[];
  dangerClass?: 'NONE' | 'EXPLOSIVE' | 'FLAMMABLE' | 'TOXIC' | 'CORROSIVE' | 'RADIOACTIVE' | 'ENVIRONMENTAL_HAZARD' | 'OXIDIZING' | 'COMPRESSED_GAS' | 'HARMFUL';
  typeConsommabilite?: 'CONSOMMABLE' | 'NON_CONSOMMABLE';
  createdAt?: string;
  updatedAt?: string;

  // ─── Fiche Signalétique d'Article (FSA) — ajouté le 2026-09-11, audit de conformité CCF
  //     (voir docs/AUDIT_CONFORMITE_CCF_2026-09-11.md §4). Tous facultatifs : la FSA du CCF
  //     est complétée progressivement par plusieurs acteurs, jamais en une seule saisie.
  // Données de base
  poids?: number;                 // kg
  volume?: number;                // m³
  normeIso?: string;
  echelonMaintenance?: string;
  codeConditionnement?: string;   // ex: "Xx0000" ou "Fs(0000)" — voir CCF §2-4-7
  // Classification
  criticite?: 'CRITIQUE' | 'NON_CRITIQUE';
  codeTypeMateriel?: 'MC' | 'EQ' | 'BE' | 'DT' | 'PS' | 'PR' | 'PC' | 'AC' | 'OD' | 'OS' | 'MP' | 'MS';
  // Planification
  stockMinimum?: number;
  stockMaximum?: number;
  codeGestion?: 'CLF' | 'CLU' | 'CIU' | 'CEI' | 'CSM' | 'CLM';
  codeSoutien?: 'S10' | 'S20' | 'S30' | 'S40' | 'S50';
  codePositionAdministrative?: 'AN' | 'DC' | 'DA' | 'SB' | 'VA' | 'PS' | 'PX';
  codeModeApprovisionnement?: 'GRC' | 'ADO' | 'BDC' | 'ADR' | 'MAX' | 'LCE';
  codeOrigineMateriel?: 'AFN' | 'AFE' | 'FMS' | 'DOC' | 'CL1' | 'CL2' | 'CL3' | 'CL4' | 'PR1' | 'AD1' | 'AD2' | 'AX1';
  reparabilite?: 'REPARABLE' | 'NON_REPARABLE';
  delaiApprovisionnementAnnees?: number;
  blocageApprovisionnement?: boolean;
  cma?: number;                   // Consommation Moyenne Annuelle
  etatArticle?: string;
  // Stockage
  dureeConservation?: string;
  // Références constructeur
  nsn?: string;
  oem?: string;
}


export interface PagedResult {
  content: Item[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

@Injectable({
  providedIn: 'root'
})
export class ItemService {
  private apiUrl = `${environment.apiUrl}/api/v1/items`;
  
  // Signals Angular 17 pour la gestion d'état réactive
  items = signal<Item[]>([]);
  loading = signal<boolean>(false);
  totalElements = signal<number>(0);
  totalPages = signal<number>(0);

  constructor(private http: HttpClient) {}

  // ─── fetchItems ───────────────────────────────────────────────────────────
  // RÔLE : Charge la liste paginée des articles depuis le backend.
  // PARAMÈTRES :
  //   page                 – numéro de page (0-indexed)
  //   size                 – taille de la page
  //   search               – texte libre de recherche (nomenclature ou désignation)
  //   sortBy               – champ de tri
  //   direction            – 'asc' | 'desc'
  //   bypassPlanArmement   – true pour ignorer le filtre PA (DA Managers)
  //   typeConsommabilite   – filtre de type d'article optionnel
  //   magasinId            – ID de la soute pour filtrer les articles et afficher le stock local
  // RETOUR : void — les résultats sont poussés dans les Signals items, totalElements, totalPages
  fetchItems(page: number = 0, size: number = 25, search?: string, sortBy?: string, direction: 'asc' | 'desc' = 'asc', bypassPlanArmement: boolean = false, typeConsommabilite?: 'CONSOMMABLE' | 'NON_CONSOMMABLE', magasinId?: number) {
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('direction', direction)
      .set('bypassPlanArmement', bypassPlanArmement.toString());
    
    if (sortBy) {
      params = params.set('sortBy', sortBy);
    }
    
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }

    if (typeConsommabilite) {
      params = params.set('typeConsommabilite', typeConsommabilite);
    }

    // ÉTAPE : Si un identifiant de magasin est fourni, le transmettre au backend
    // pour filtrer les articles et retourner la quantité locale (non la somme globale)
    if (magasinId != null) {
      params = params.set('magasinId', magasinId.toString());
    }

    this.http.get<PagedResult>(this.apiUrl, { params }).subscribe({
      next: (data) => {
        this.items.set(data.content);
        this.totalElements.set(data.totalElements);
        this.totalPages.set(data.totalPages);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erreur lors du chargement des articles', err);
        this.items.set([]);
        this.loading.set(false);
      }
    });
  }

  getItemById(id: string): Observable<Item> {
    return this.http.get<Item>(`${this.apiUrl}/${id}`);
  }

  searchItems(search: string): Observable<PagedResult> {
    const params = new HttpParams()
      .set('page', '0')
      .set('size', '20')
      .set('search', search.trim())
      .set('bypassPlanArmement', 'true');
    return this.http.get<PagedResult>(this.apiUrl, { params });
  }

  // ─── createItem ───────────────────────────────────────────────────────────
  // RÔLE : Crée un nouvel article dans le catalogue (bouton "Nouvel Article").
  // PARAMÈTRES : item — objet partiel décrivant l'article (nomenclature et
  //              désignation obligatoires ; id/documents/dates sont ignorés,
  //              le backend les gère lui-même).
  // RETOUR : Observable de l'article persistant, avec son UUID généré.
  createItem(item: Partial<Item>): Observable<Item> {
    return this.http.post<Item>(this.apiUrl, item);
  }

  // ─── updateItem ───────────────────────────────────────────────────────────
  // RÔLE : Modalité de modification d'un article (CCF §2-2), ajoutée le 2026-09-11 avec la
  //        Fiche Signalétique d'Article étendue. La nomenclature n'est pas modifiable par
  //        cette route (voir ItemController.updateItem côté backend).
  updateItem(id: string, item: Partial<Item>): Observable<Item> {
    return this.http.put<Item>(`${this.apiUrl}/${id}`, item);
  }

  // ─── getNomenclatureNextLevel ──────────────────────────────────────────────
  // RÔLE : Alimente les listes déroulantes en cascade du formulaire "Nouvel
  //        Article" (Sous-classe > Catégorie > Série > Code Article) avec les
  //        codes déjà utilisés dans le catalogue existant, niveau par niveau.
  // PARAMÈTRES : classe/sousClasse/categorie/serie — segments déjà choisis, dans
  //              l'ordre ; ne pas fournir un segment sans avoir fourni les
  //              précédents (voir ItemController.getNomenclatureNextLevel).
  // RETOUR : Observable de {level, options: [{code, exempleDesignation,
  //          nombreArticles}]} pour un niveau intermédiaire, ou
  //          {level: 'item', existingCount, suggestedCode} une fois les 4
  //          segments de classification renseignés.
  getNomenclatureNextLevel(classe?: string, sousClasse?: string, categorie?: string, serie?: string): Observable<any> {
    let params = new HttpParams();
    if (classe) params = params.set('classe', classe);
    if (sousClasse) params = params.set('sousClasse', sousClasse);
    if (categorie) params = params.set('categorie', categorie);
    if (serie) params = params.set('serie', serie);
    return this.http.get<any>(`${this.apiUrl}/nomenclature/next-level`, { params });
  }

  getItemReservations(itemId: string): Observable<any[]> {
    return this.http.get<any[]>(`${environment.apiUrl}/api/v1/demandes/item/${itemId}/reservations`);
  }

  updateDangerClass(id: string, dangerClass: string): Observable<Item> {
    return this.http.put<Item>(`${this.apiUrl}/${id}/danger-class`, { dangerClass });
  }

  uploadPhoto(id: string, file: File): Observable<Item> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Item>(`${this.apiUrl}/${id}/upload-photo`, formData);
  }

  uploadDoc(id: string, file: File): Observable<Item> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Item>(`${this.apiUrl}/${id}/upload-doc`, formData);
  }
}
