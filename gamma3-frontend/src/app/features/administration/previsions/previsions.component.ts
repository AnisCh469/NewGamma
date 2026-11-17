import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { PrevisionService, AlertePrevision } from '../../../core/services/prevision.service';
import { AuthService } from '../../../core/auth/auth.service';

/**
 * Écran « Administration > Prévisions » — Procédure P01 du CCF, version allégée (voir
 * docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md §2.3, validée par l'utilisateur le
 * 2026-09-21). Réservé à la Division de l'Approvisionnement, cloisonné Section Étude et
 * Prévision (SEP) côté backend (voir PrevisionController).
 *
 * Liste les articles dont le stock potentiel est descendu au niveau ou en dessous de leur seuil
 * d'alerte calculé (Sa, formules CCF RG5/RG6) — la version numérique de l'« état de prévision des
 * nouveaux besoins » (OP2/OP3 de P01), sans le formalisme des fiches papier F01-F04.
 *
 * Calcul déclenché À LA DEMANDE (bouton), pas au chargement de l'écran — voir la javadoc de
 * PrevisionService côté backend pour le raisonnement de performance.
 */
@Component({
  selector: 'app-previsions',
  standalone: true,
  imports: [CommonModule, RouterModule, ButtonModule, TagModule],
  templateUrl: './previsions.component.html'
})
export class PrevisionsComponent {
  private service = inject(PrevisionService);
  public authService = inject(AuthService);

  alertes: AlertePrevision[] = [];
  isLoading = false;
  aCalcule = false;
  errorMessage = '';

  calculer(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.service.calculerAlertes().subscribe({
      next: (liste) => {
        this.alertes = liste;
        this.isLoading = false;
        this.aCalcule = true;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.status === 403
          ? 'Réservé à la Division de l\'Approvisionnement (Section Étude et Prévision).'
          : 'Impossible de calculer les prévisions.';
      }
    });
  }

  /** Sévérité du badge selon l'ampleur du déficit (stock potentiel très négatif = urgent). */
  severite(a: AlertePrevision): 'danger' | 'warning' {
    return a.stockPotentiel < 0 ? 'danger' : 'warning';
  }
}
