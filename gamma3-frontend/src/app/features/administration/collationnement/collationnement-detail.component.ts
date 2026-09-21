import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { CollationnementService, CampagneCollationnement, LigneCollationnement } from '../../../core/services/collationnement.service';

/**
 * Détail d'une campagne de collationnement : saisie du constat physique ligne par ligne, puis
 * clôture (conclusion CONFORME/ECART_CONSTATE déterminée automatiquement côté serveur — voir
 * CollationnementService.cloturerCampagne).
 */
@Component({
  selector: 'app-collationnement-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ButtonModule, InputNumberModule, InputTextareaModule, TagModule, ToastModule],
  providers: [MessageService],
  templateUrl: './collationnement-detail.component.html'
})
export class CollationnementDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private service = inject(CollationnementService);
  private messageService = inject(MessageService);

  campagne: CampagneCollationnement | null = null;
  lignes: LigneCollationnement[] = [];
  isLoading = true;
  errorMessage = '';
  isSavingComptage = false;
  isCloture = false;
  observationsCloture = '';

  get estTerminee(): boolean {
    return this.campagne?.statut === 'TERMINEE';
  }

  get toutesLignesComptees(): boolean {
    return this.lignes.length > 0 && this.lignes.every(l => l.quantitePhysiqueConstatee !== null);
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) { this.errorMessage = 'Identifiant introuvable.'; this.isLoading = false; return; }
    this.charger(id);
  }

  charger(id: number): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.service.getCampagne(id).subscribe({
      next: (c) => {
        this.campagne = c;
        this.service.getLignes(id).subscribe({
          next: (l) => { this.lignes = l; this.isLoading = false; },
          error: () => { this.isLoading = false; this.errorMessage = 'Impossible de charger les lignes.'; }
        });
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.status === 403
          ? 'Réservé à la Division de l\'Approvisionnement (Section Comptabilité).'
          : 'Campagne introuvable.';
      }
    });
  }

  ecart(l: LigneCollationnement): number | null {
    return l.quantitePhysiqueConstatee === null ? null : l.quantitePhysiqueConstatee - l.quantiteTheorique;
  }

  enregistrerComptage(): void {
    if (!this.campagne) return;
    this.isSavingComptage = true;
    const payload = this.lignes.map(l => ({
      ligneId: l.id,
      quantitePhysiqueConstatee: l.quantitePhysiqueConstatee,
      observations: l.observations
    }));
    this.service.saisirComptage(this.campagne.id, payload).subscribe({
      next: (c) => {
        this.isSavingComptage = false;
        this.campagne = c;
        this.messageService.add({ severity: 'success', summary: 'Comptage enregistré', detail: 'Le constat physique a bien été enregistré.' });
      },
      error: (err) => {
        this.isSavingComptage = false;
        this.messageService.add({ severity: 'error', summary: 'Échec', detail: typeof err?.error === 'string' ? err.error : 'Enregistrement impossible.' });
      }
    });
  }

  cloturer(): void {
    if (!this.campagne) return;
    if (!confirm('Clôturer cette campagne ? Le comptage ne pourra plus être modifié ensuite.')) return;
    this.isCloture = true;
    this.service.cloturer(this.campagne.id, this.observationsCloture).subscribe({
      next: (c) => {
        this.isCloture = false;
        this.campagne = c;
        this.messageService.add({
          severity: c.conclusionGenerale === 'CONFORME' ? 'success' : 'warn',
          summary: 'Campagne clôturée',
          detail: c.conclusionGenerale === 'CONFORME' ? 'Aucun écart constaté.' : 'Au moins un écart a été constaté.'
        });
      },
      error: (err) => {
        this.isCloture = false;
        this.messageService.add({ severity: 'error', summary: 'Échec', detail: typeof err?.error === 'string' ? err.error : 'Clôture impossible.' });
      }
    });
  }
}
