import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type TypeDocumentSigne =
  | 'BON_SORTIE' | 'BON_ENTREE' | 'PV_REFORME' | 'BON_COMMANDE'
  | 'FICHE_ARTICLE' | 'FICHE_FOURNISSEUR' | 'FICHE_UNITE' | 'AUTRE';

export interface DocumentSigne {
  id: number;
  typeDocument: TypeDocumentSigne;
  referenceId: string;
  referenceLibelle: string | null;
  nomFichier: string;
  taille: number;
  typeMime: string;
  note: string | null;
  dateUpload: string;
  uploadePar: string;
}

/**
 * RÔLE : gérer les « documents signés » — le scan d'un document officiel une fois signé/
 * cacheté, rattaché à l'enregistrement qui l'a produit (demande, réception, réforme…).
 * Voir docs/AUDIT_IMPRESSION.md et le composant partagé <app-documents-signes>.
 */
@Injectable({ providedIn: 'root' })
export class DocumentSigneService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/v1/documents-signes`;

  lister(typeDocument: TypeDocumentSigne, referenceId: string | number): Observable<DocumentSigne[]> {
    return this.http.get<DocumentSigne[]>(this.base, {
      params: { typeDocument, referenceId: String(referenceId) }
    });
  }

  televerser(
    typeDocument: TypeDocumentSigne,
    referenceId: string | number,
    file: File,
    referenceLibelle?: string,
    note?: string
  ): Observable<DocumentSigne> {
    const form = new FormData();
    form.append('typeDocument', typeDocument);
    form.append('referenceId', String(referenceId));
    if (referenceLibelle) form.append('referenceLibelle', referenceLibelle);
    if (note) form.append('note', note);
    form.append('file', file);
    return this.http.post<DocumentSigne>(this.base, form);
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** Ouvre le fichier dans un nouvel onglet (récupéré en blob → jeton JWT ajouté par l'intercepteur). */
  ouvrir(id: number): void {
    const onglet = window.open('', '_blank');
    this.http.get(`${this.base}/${id}/fichier`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        if (onglet) { onglet.location.href = url; } else { window.open(url, '_blank'); }
        setTimeout(() => URL.revokeObjectURL(url), 120000);
      },
      error: () => {
        if (onglet) {
          onglet.document.body.innerHTML =
            '<p style="font-family:system-ui,Arial,sans-serif;color:#a33;padding:2rem">Fichier indisponible.</p>';
        }
      }
    });
  }
}
