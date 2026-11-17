import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { CollationnementService, CampagneCollationnement } from '../../../core/services/collationnement.service';
import { UniteService, UniteUtilisatrice } from '../../../core/services/unite.service';
import { AuthService } from '../../../core/auth/auth.service';

/**
 * Écran « Administration > Collationnement » — liste des campagnes (Procédure P07 du CCF,
 * version allégée, voir docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md §3.3, validée par
 * l'utilisateur le 2026-09-21). Réservé à la Division de l'Approvisionnement, cloisonné Section
 * Comptabilité (SC) côté backend (voir CollationnementController).
 */
@Component({
  selector: 'app-collationnement-liste',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ButtonModule, DialogModule, DropdownModule, InputTextModule, InputTextareaModule, TagModule, ToastModule],
  providers: [MessageService],
  templateUrl: './collationnement-liste.component.html'
})
export class CollationnementListeComponent implements OnInit {
  private service = inject(CollationnementService);
  private uniteService = inject(UniteService);
  private messageService = inject(MessageService);
  public authService = inject(AuthService);

  campagnes: CampagneCollationnement[] = [];
  unites: UniteUtilisatrice[] = [];
  isLoading = false;
  errorMessage = '';

  displayCreation = false;
  isCreation = false;
  nouvelleCampagne = {
    uniteId: null as number | null,
    datePlanifiee: new Date().toISOString().substring(0, 10),
    membresCommission: ''
  };

  ngOnInit(): void {
    this.charger();
    this.uniteService.getUnites().subscribe({ next: (u) => (this.unites = u) });
  }

  charger(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.service.lister().subscribe({
      next: (liste) => { this.campagnes = liste; this.isLoading = false; },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.status === 403
          ? 'Réservé à la Division de l\'Approvisionnement (Section Comptabilité).'
          : 'Impossible de charger les campagnes de collationnement.';
      }
    });
  }

  ouvrirCreation(): void {
    this.nouvelleCampagne = { uniteId: null, datePlanifiee: new Date().toISOString().substring(0, 10), membresCommission: '' };
    this.displayCreation = true;
  }

  creer(): void {
    if (!this.nouvelleCampagne.uniteId) return;
    this.isCreation = true;
    this.service.creerCampagne(this.nouvelleCampagne.uniteId, this.nouvelleCampagne.datePlanifiee, this.nouvelleCampagne.membresCommission).subscribe({
      next: () => {
        this.isCreation = false;
        this.displayCreation = false;
        this.messageService.add({ severity: 'success', summary: 'Campagne créée', detail: 'Les lignes ont été générées à partir du Plan d\'Armement de l\'unité.' });
        this.charger();
      },
      error: (err) => {
        this.isCreation = false;
        this.messageService.add({ severity: 'error', summary: 'Échec', detail: typeof err?.error === 'string' ? err.error : 'Création impossible.' });
      }
    });
  }

  severiteStatut(statut: string): 'info' | 'warning' | 'success' {
    if (statut === 'TERMINEE') return 'success';
    if (statut === 'EN_COURS') return 'warning';
    return 'info';
  }

  severiteConclusion(conclusion: string | null): 'success' | 'danger' | undefined {
    if (conclusion === 'CONFORME') return 'success';
    if (conclusion === 'ECART_CONSTATE') return 'danger';
    return undefined;
  }
}
