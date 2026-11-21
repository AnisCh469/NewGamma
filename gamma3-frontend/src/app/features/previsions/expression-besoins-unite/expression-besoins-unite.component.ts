import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { TooltipModule } from 'primeng/tooltip';
import { PrevisionService, ExpressionBesoin, LigneExpressionBesoin, PrioriteBesoin } from '../../../core/services/prevision.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ItemService, Item, PagedResult } from '../../catalogue/item.service';

@Component({
  selector: 'app-expression-besoins-unite',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    ButtonModule, TagModule, DialogModule, DropdownModule,
    InputTextModule, InputNumberModule, InputTextareaModule, AutoCompleteModule, TooltipModule
  ],
  templateUrl: './expression-besoins-unite.component.html'
})
export class ExpressionBesoinsUniteComponent implements OnInit {
  private previsionService = inject(PrevisionService);
  public authService = inject(AuthService);
  private itemService = inject(ItemService);

  dossiers: ExpressionBesoin[] = [];
  isLoading = false;
  selectedAnnee: number = new Date().getFullYear() + 1;
  anneesDisponibles = [
    { label: `${new Date().getFullYear() + 1} (A+1)`, value: new Date().getFullYear() + 1 },
    { label: `${new Date().getFullYear() + 2} (A+2)`, value: new Date().getFullYear() + 2 },
    { label: `${new Date().getFullYear() + 3} (A+3)`, value: new Date().getFullYear() + 3 }
  ];

  prioritesOptions = [
    { label: 'Priorité 1 : Immédiat / Opérationnel', value: 'PRIORITE_1' },
    { label: 'Priorité 2 : Maintenance programmée', value: 'PRIORITE_2' },
    { label: 'Priorité 3 : Confort / Amélioration', value: 'PRIORITE_3' }
  ];

  // Modal création / édition
  displayModal = false;
  isNouveauDossier = true;
  currentDossier: ExpressionBesoin = this.createEmptyDossier();

  // Modal ajout ligne
  displayLigneModal = false;
  isHorsCatalogue = false;
  currentLigne: LigneExpressionBesoin = this.createEmptyLigne();

  // Recherche catalogue
  itemsSuggestions: Item[] = [];
  selectedItemForLigne: Item | null = null;

  // Modal visualisation détail
  displayDetailModal = false;
  detailDossier: ExpressionBesoin | null = null;

  ngOnInit(): void {
    this.chargerDossiers();
  }

  chargerDossiers(): void {
    this.isLoading = true;
    this.previsionService.getBesoins(this.selectedAnnee).subscribe({
      next: (data) => {
        this.dossiers = data;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  ouvrirNouveauDossier(): void {
    this.isNouveauDossier = true;
    this.currentDossier = this.createEmptyDossier();
    this.displayModal = true;
  }

  editerDossier(dossier: ExpressionBesoin): void {
    if (dossier.statut !== 'BROUILLON') return;
    this.isNouveauDossier = false;
    this.currentDossier = JSON.parse(JSON.stringify(dossier));
    this.displayModal = true;
  }

  ouvrirDetail(dossier: ExpressionBesoin): void {
    this.detailDossier = dossier;
    this.displayDetailModal = true;
  }

  ouvrirAjoutLigne(horsCatalogue: boolean = false): void {
    this.isHorsCatalogue = horsCatalogue;
    this.selectedItemForLigne = null;
    this.currentLigne = this.createEmptyLigne(horsCatalogue);
    this.displayLigneModal = true;
  }

  searchItems(event: any): void {
    const query = event.query || '';
    if (query.length < 2) return;
    this.itemService.searchItems(query).subscribe({
      next: (res: PagedResult) => {
        this.itemsSuggestions = res?.content || [];
      }
    });
  }

  onItemSelected(event: any): void {
    const item: Item = event?.value || event;
    this.selectedItemForLigne = item;
    this.currentLigne.itemId = item.id;
    this.currentLigne.itemNomenclature = item.nomenclature;
    this.currentLigne.itemDesignation = item.designation;
  }

  ajouterLigneAuDossier(): void {
    if (!this.isHorsCatalogue && !this.currentLigne.itemId) {
      alert('Veuillez sélectionner un article du catalogue.');
      return;
    }
    if (this.isHorsCatalogue && (!this.currentLigne.designationHorsCatalogue || this.currentLigne.designationHorsCatalogue.trim().length === 0)) {
      alert('Veuillez saisir la désignation du nouveau besoin.');
      return;
    }
    if (!this.currentLigne.quantiteDemandee || this.currentLigne.quantiteDemandee <= 0) {
      alert('La quantité demandée doit être supérieure à 0.');
      return;
    }

    this.currentDossier.lignes.push({ ...this.currentLigne });
    this.displayLigneModal = false;
  }

  supprimerLigne(index: number): void {
    this.currentDossier.lignes.splice(index, 1);
  }

  enregistrerDossier(): void {
    if (!this.currentDossier.objet || this.currentDossier.objet.trim().length === 0) {
      alert('Veuillez saisir l\'objet de votre expression de besoin.');
      return;
    }
    if (this.currentDossier.lignes.length === 0) {
      alert('Veuillez ajouter au moins un article ou un nouveau besoin.');
      return;
    }

    this.previsionService.enregistrerBesoin(this.currentDossier).subscribe({
      next: () => {
        this.displayModal = false;
        this.chargerDossiers();
      },
      error: (err) => {
        alert(err.error?.message || 'Erreur lors de l\'enregistrement.');
      }
    });
  }

  soumettreDossier(dossier: ExpressionBesoin): void {
    if (!dossier.id) return;
    if (!confirm(`Confirmez-vous la soumission officielle du dossier ${dossier.numeroDossier} à la Division de l'Approvisionnement (Section SEP) ? Après soumission, il ne sera plus modifiable.`)) {
      return;
    }

    this.previsionService.soumettreBesoin(dossier.id).subscribe({
      next: () => {
        this.chargerDossiers();
      },
      error: (err) => {
        alert(err.error?.message || 'Erreur lors de la soumission.');
      }
    });
  }

  getBadgeStatut(statut?: string): { severity: 'secondary' | 'info' | 'warning' | 'success' | 'contrast', label: string } {
    switch (statut) {
      case 'SOUMIS': return { severity: 'info', label: 'Soumis à la DA (SEP)' };
      case 'ETUDIE_SEP': return { severity: 'warning', label: 'Étudié (Filtrage SEP)' };
      case 'ARBITRE': return { severity: 'success', label: 'Arbitré (Commission DA)' };
      case 'VALIDE_PADI': return { severity: 'contrast', label: 'Validé PADI / EMAM' };
      default: return { severity: 'secondary', label: 'Brouillon' };
    }
  }

  getBadgePriorite(priorite?: string): { severity: 'danger' | 'warning' | 'info', label: string } {
    switch (priorite) {
      case 'PRIORITE_1': return { severity: 'danger', label: 'P1 : Immédiat / Opérationnel' };
      case 'PRIORITE_2': return { severity: 'warning', label: 'P2 : Maintenance programmée' };
      default: return { severity: 'info', label: 'P3 : Confort / Amélioration' };
    }
  }

  private createEmptyDossier(): ExpressionBesoin {
    return {
      anneeBudgetaire: this.selectedAnnee,
      objet: `Expression des besoins prévisionnels ${this.selectedAnnee}`,
      commentaires: '',
      lignes: []
    };
  }

  private createEmptyLigne(horsCatalogue: boolean = false): LigneExpressionBesoin {
    return {
      nouveauBesoin: horsCatalogue,
      quantiteDemandee: 1,
      priorite: 'PRIORITE_2',
      designationHorsCatalogue: '',
      referenceConstructeur: '',
      equipementParent: '',
      justificationTechnique: ''
    };
  }
}
