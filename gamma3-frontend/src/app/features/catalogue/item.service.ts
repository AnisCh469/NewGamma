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
