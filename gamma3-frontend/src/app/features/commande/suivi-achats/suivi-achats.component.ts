import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { TooltipModule } from 'primeng/tooltip';
import { DossierAchatService, DossierAchat, StatutAchat } from '../../../core/services/dossier-achat.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-suivi-achats',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    ButtonModule, TagModule, DialogModule, DropdownModule, TooltipModule
  ],
  templateUrl: './suivi-achats.component.html'
})
export class SuiviAchatsComponent implements OnInit {
  private service = inject(DossierAchatService);
  public authService = inject(AuthService);

  dossiers: DossierAchat[] = [];
  isLoading = false;
  selectedAnnee: number = new Date().getFullYear() + 1;
  anneesDisponibles = [
    { label: `${new Date().getFullYear() + 1}`, value: new Date().getFullYear() + 1 },
    { label: `${new Date().getFullYear()}`, value: new Date().getFullYear() },
    { label: `${new Date().getFullYear() - 1}`, value: new Date().getFullYear() - 1 }
  ];

  // Modal changement statut
  displayStatutModal = false;
  selectedDossier: DossierAchat | null = null;
  nouveauStatut: StatutAchat = 'CONSULTATION_LANCEE';
  referenceMarche = '';
  dateNotification = '';
  observations = '';

  // Modal détail
  displayDetailModal = false;
  detailDossier: DossierAchat | null = null;

  statutsOptions = [
    { label: 'Dossier Créé', value: 'DOSSIER_CREE' },
    { label: 'Consultation / Appel d\'offres Lancé', value: 'CONSULTATION_LANCEE' },
    { label: 'Offres Dépouillées', value: 'OFFRES_DEPOUILLEES' },
    { label: 'Marché / Bon Notifié', value: 'COMMANDE_NOTIFIEE' },
    { label: 'Livraison Partielle', value: 'LIVRAISON_PARTIELLE' },
    { label: 'Soldé / Entrée en Soute Effectuée', value: 'SOLDE' }
  ];

  ngOnInit(): void {
    this.chargerDossiers();
  }

  chargerDossiers(): void {
    this.isLoading = true;
    this.service.getAchats(this.selectedAnnee).subscribe({
      next: (data) => {
        this.dossiers = data;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  ouvrirDetail(dossier: DossierAchat): void {
    this.detailDossier = dossier;
    this.displayDetailModal = true;
  }

  ouvrirStatutModal(dossier: DossierAchat): void {
    this.selectedDossier = dossier;
    this.nouveauStatut = dossier.statut;
    this.referenceMarche = dossier.referenceMarcheOuBon || '';
    this.dateNotification = dossier.dateNotification || '';
    this.observations = dossier.observations || '';
    this.displayStatutModal = true;
  }

  enregistrerNouveauStatut(): void {
    if (!this.selectedDossier?.id) return;
    this.service.changerStatut(
      this.selectedDossier.id,
      this.nouveauStatut,
      this.referenceMarche,
      this.dateNotification,
      this.observations
    ).subscribe({
      next: () => {
        this.displayStatutModal = false;
        this.chargerDossiers();
      },
      error: (err) => alert(err.error?.message || 'Erreur lors de la mise à jour du statut.')
    });
  }

  getBadgeStatut(statut?: string): { severity: 'secondary' | 'info' | 'warning' | 'success' | 'contrast', label: string } {
    switch (statut) {
      case 'CONSULTATION_LANCEE': return { severity: 'info', label: 'Consultation Lancée' };
      case 'OFFRES_DEPOUILLEES': return { severity: 'warning', label: 'Offres Dépouillées' };
      case 'COMMANDE_NOTIFIEE': return { severity: 'success', label: 'Marché / Bon Notifié' };
      case 'LIVRAISON_PARTIELLE': return { severity: 'warning', label: 'Livraison Partielle' };
      case 'SOLDE': return { severity: 'contrast', label: 'Soldé / En Soute' };
      default: return { severity: 'secondary', label: 'Dossier Créé' };
    }
  }
}
