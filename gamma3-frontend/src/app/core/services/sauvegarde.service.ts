import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Sauvegarde {
  nomFichier: string;
  tailleOctets: number;
  dateCreation: string;
}

/**
 * Administration > Sauvegardes (SUPER_ADMIN uniquement) — déclenchement et consultation des
 * sauvegardes de la base. Volontairement AUCUNE méthode de téléchargement : le fichier de
 * sauvegarde reste strictement côté serveur (voir SauvegardeService.java côté backend).
 */
@Injectable({ providedIn: 'root' })
export class SauvegardeService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/v1/sauvegardes`;

  lister(): Observable<Sauvegarde[]> {
    return this.http.get<Sauvegarde[]>(this.base);
  }

  declencher(): Observable<Sauvegarde> {
    return this.http.post<Sauvegarde>(this.base, {});
  }
}
