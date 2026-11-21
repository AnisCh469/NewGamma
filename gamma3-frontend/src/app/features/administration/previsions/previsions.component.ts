import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TabViewModule } from 'primeng/tabview';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { TooltipModule } from 'primeng/tooltip';
import {
  PrevisionService,
  AlertePrevision,
  SyntheseConsolidee,
  ExpressionBesoin,
  LigneExpressionBesoin,
  ModeAcquisition,
  DecisionCommission
} from '../../../core/services/prevision.service';
import { AuthService } from '../../../core/auth/auth.service';
import { DocumentService } from '../../../core/services/document.service';

@Component({
  selector: 'app-previsions',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    ButtonModule, TagModule, TabViewModule, DialogModule, DropdownModule, TooltipModule
  ],
  templateUrl: './previsions.component.html'
})
export class PrevisionsComponent implements OnInit {
  private service = inject(PrevisionService);
  public authService = inject(AuthService);
  public documentService = inject(DocumentService);
  private router = inject(Router);

  activeTabIndex = 0;
  anneeCible: number = new Date().getFullYear() + 1;
  anneesDisponibles = [
    { label: `${new Date().getFullYear() + 1} (A+1)`, value: new Date().getFullYear() + 1 },
    { label: `${new Date().getFullYear() + 2} (A+2)`, value: new Date().getFullYear() + 2 },
    { label: `${new Date().getFullYear() + 3} (A+3)`, value: new Date().getFullYear() + 3 }
  ];

  // Onglet 1 : Synthèse consolidée & Alertes mathématiques
  synthese: SyntheseConsolidee[] = [];
  alertes: AlertePrevision[] = [];
  isLoadingSynthese = false;

  // Onglet 2 : Dossiers des Unités
  dossiers: ExpressionBesoin[] = [];
  isLoadingDossiers = false;
  selectedDossier: ExpressionBesoin | null = null;
  displayEtudeModal = false;

  // Onglet 3 : Commission d'Arbitrage
  displayArbitrageModal = false;
  selectedDossierArbitrage: ExpressionBesoin | null = null;

  // Modes et décisions
  modesAcquisition = [
    { label: 'Marché Cadre', value: 'MARCHE_CADRE' },
    { label: 'Appel d\'Offres', value: 'APPEL_OFFRES' },
    { label: 'Bon de Commande', value: 'BON_COMMANDE' },
    { label: 'Achat Direct par Régie', value: 'REGIE' },
    { label: 'FMS', value: 'FMS' }
  ];

  decisionsCommission = [
    { label: 'Accordé', value: 'ACCORDE' },
    { label: 'Rejeté', value: 'REJETE' },
    { label: 'Ajourné', value: 'AJOURNE' }
  ];

  // Onglet 4 : Passerelle Achats
  lignesPourAchat: { ligne: LigneExpressionBesoin; uniteNom?: string; dossierNum?: string; checked: boolean }[] = [];
  modeAcquisitionSelectionne: ModeAcquisition = 'MARCHE_CADRE';
  isTransfering = false;

  ngOnInit(): void {
    this.chargerTout();
  }

  chargerTout(): void {
    this.chargerSynthese();
    this.chargerDossiers();
  }

  chargerSynthese(): void {
    this.isLoadingSynthese = true;
    this.service.getSyntheseConsolidee(this.anneeCible).subscribe({
      next: (data) => {
        this.synthese = data;
        this.isLoadingSynthese = false;
      },
      error: () => {
        this.isLoadingSynthese = false;
      }
    });
  }

  chargerDossiers(): void {
    this.isLoadingDossiers = true;
    this.service.getBesoins(this.anneeCible).subscribe({
      next: (data) => {
        this.dossiers = data;
        this.isLoadingDossiers = false;
        this.preparerLignesPourAchat();
      },
      error: () => {
        this.isLoadingDossiers = false;
      }
    });
  }

  preparerLignesPourAchat(): void {
    this.lignesPourAchat = [];
    for (const d of this.dossiers) {
      if (d.statut === 'ARBITRE' || d.statut === 'VALIDE_PADI') {
        for (const l of d.lignes) {
          if (l.decisionCommission === 'ACCORDE' && !l.transfereAchat) {
            this.lignesPourAchat.push({
              ligne: l,
              uniteNom: d.uniteNom,
              dossierNum: d.numeroDossier,
              checked: true
            });
          }
        }
      }
    }
  }

  ouvrirEtudeSEP(dossier: ExpressionBesoin): void {
    this.selectedDossier = JSON.parse(JSON.stringify(dossier));
    for (const l of this.selectedDossier!.lignes) {
      if (l.quantiteProposeeSEP == null) {
        l.quantiteProposeeSEP = l.quantiteDemandee;
      }
    }
    this.displayEtudeModal = true;
  }

  enregistrerEtudeSEP(): void {
    if (!this.selectedDossier?.id) return;
    this.service.etudierSEP(this.selectedDossier.id, this.selectedDossier.lignes).subscribe({
      next: () => {
        this.displayEtudeModal = false;
        this.chargerTout();
      },
      error: (err) => alert(err.error?.message || 'Erreur lors de l\'enregistrement de l\'étude SEP.')
    });
  }

  ouvrirArbitrageCommission(dossier: ExpressionBesoin): void {
    this.selectedDossierArbitrage = JSON.parse(JSON.stringify(dossier));
    for (const l of this.selectedDossierArbitrage!.lignes) {
      if (l.quantiteRetenue == null) {
        l.quantiteRetenue = l.quantiteProposeeSEP != null ? l.quantiteProposeeSEP : l.quantiteDemandee;
      }
      if (!l.decisionCommission || l.decisionCommission === 'EN_ATTENTE') {
        l.decisionCommission = 'ACCORDE';
      }
      if (!l.modeAcquisitionRecommande) {
        l.modeAcquisitionRecommande = 'MARCHE_CADRE';
      }
    }
    this.displayArbitrageModal = true;
  }

  enregistrerArbitrage(): void {
    if (!this.selectedDossierArbitrage?.id) return;
    this.service.arbitrerCommission(this.selectedDossierArbitrage.id, this.selectedDossierArbitrage.lignes).subscribe({
      next: () => {
        this.displayArbitrageModal = false;
        this.chargerTout();
      },
      error: (err) => alert(err.error?.message || 'Erreur lors de l\'arbitrage.')
    });
  }

  validerPADI(dossier: ExpressionBesoin): void {
    if (!dossier.id) return;
    if (!confirm(`Valider officiellement le dossier ${dossier.numeroDossier} au titre du PADI / EMAM ?`)) return;
    this.service.validerPADI(dossier.id).subscribe({
      next: () => this.chargerTout(),
      error: (err) => alert(err.error?.message || 'Erreur lors de la validation PADI.')
    });
  }

  transfererSelectionVersAchats(): void {
    const selection = this.lignesPourAchat.filter(item => item.checked && item.ligne.id).map(item => item.ligne.id!);
    if (selection.length === 0) {
      alert('Veuillez sélectionner au moins une ligne arbitrée.');
      return;
    }

    if (!confirm(`Confirmez-vous le transfert de ${selection.length} ligne(s) vers un Dossier d'Achat P02 (${this.modeAcquisitionSelectionne}) ?`)) {
      return;
    }

    this.isTransfering = true;
    this.service.transfererAchats(this.anneeCible, this.modeAcquisitionSelectionne, selection).subscribe({
      next: (dossierAchatId) => {
        this.isTransfering = false;
        alert(`Dossier d'achat P02 créé avec succès !`);
        this.chargerTout();
        this.router.navigate(['/commandes/achats']);
      },
      error: (err) => {
        this.isTransfering = false;
        alert(err.error?.message || 'Erreur lors du transfert.');
      }
    });
  }

  getBadgeStatut(statut?: string): { severity: 'secondary' | 'info' | 'warning' | 'success' | 'contrast', label: string } {
    switch (statut) {
      case 'SOUMIS': return { severity: 'info', label: 'Soumis à la DA (SEP)' };
      case 'ETUDIE_SEP': return { severity: 'warning', label: 'Étudié (Filtrage SEP)' };
      case 'ARBITRE': return { severity: 'success', label: 'Arbitré en Commission' };
      case 'VALIDE_PADI': return { severity: 'contrast', label: 'Validé PADI / EMAM' };
      default: return { severity: 'secondary', label: 'Brouillon' };
    }
  }

  getBadgePriorite(priorite?: string): { severity: 'danger' | 'warning' | 'info', label: string } {
    switch (priorite) {
      case 'PRIORITE_1': return { severity: 'danger', label: 'P1 : Immédiat' };
      case 'PRIORITE_2': return { severity: 'warning', label: 'P2 : Maintenance' };
      default: return { severity: 'info', label: 'P3 : Confort' };
    }
  }
}
