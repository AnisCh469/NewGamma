import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/**
 * RÔLE : ouvrir les documents officiels PDF générés par le serveur
 * (endpoints /api/v1/documents/**, voir docs/AUDIT_IMPRESSION.md).
 *
 * POURQUOI un service dédié plutôt que `window.print()` dans chaque composant :
 *   - le rendu est fait UNE fois, côté serveur, de façon déterministe (même PDF quel que
 *     soit le poste / navigateur / imprimante), sans en-tête parasite du navigateur ;
 *   - le PDF est authentifié (l'intercepteur JWT ajoute le jeton), téléchargeable et
 *     archivable ;
 *   - l'utilisateur imprime depuis le lecteur PDF, avec un aperçu fidèle.
 *
 * Le PDF est récupéré en `blob` (donc via HttpClient => jeton ajouté), puis ouvert dans
 * un nouvel onglet. L'onglet est ouvert AU MOMENT DU CLIC (avant l'appel réseau) pour ne
 * pas être bloqué par le bloqueur de pop-ups.
 */
@Injectable({ providedIn: 'root' })
export class DocumentService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/v1/documents`;

  /**
   * @param cheminRelatif ex. "bon-sortie/19", "etiquette/article/0100AL000002"
   */
  ouvrir(cheminRelatif: string): void {
    const onglet = window.open('', '_blank');
    if (onglet) {
      onglet.document.write(
        '<!doctype html><meta charset="utf-8"><title>Document GAMMA 3</title>' +
        '<body style="font-family:system-ui,Arial,sans-serif;color:#334;padding:2rem">' +
        '<p>Génération du document en cours…</p></body>');
    }

    this.http.get(`${this.base}/${cheminRelatif}`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        if (onglet) {
          onglet.location.href = url;
        } else {
          window.open(url, '_blank');
        }
        // Libère la mémoire une fois le PDF chargé (délai large pour laisser le rendu se faire).
        setTimeout(() => URL.revokeObjectURL(url), 120000);
      },
      error: (err) => {
        const msg = err?.status === 403
          ? "Vous n'avez pas l'autorisation d'imprimer ce document."
          : err?.status === 404
            ? "Le document n'existe pas encore (l'opération correspondante n'est peut-être pas finalisée)."
            : "Le document n'a pas pu être généré. Réessayez ou contactez l'administrateur.";
        if (onglet) {
          onglet.document.body.innerHTML =
            '<p style="font-family:system-ui,Arial,sans-serif;color:#a33;padding:2rem">' + msg + '</p>';
        }
        // eslint-disable-next-line no-console
        console.error('DocumentService.ouvrir', cheminRelatif, err);
      }
    });
  }
}
