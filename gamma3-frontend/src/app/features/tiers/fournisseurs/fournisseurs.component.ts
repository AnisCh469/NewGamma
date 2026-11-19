import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FournisseurService, Fournisseur } from '../../../core/services/fournisseur.service';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ToolbarModule } from 'primeng/toolbar';
import { DropdownModule } from 'primeng/dropdown';
import { TagModule } from 'primeng/tag';
import { InputNumberModule } from 'primeng/inputnumber';

import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-fournisseurs',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DialogModule, InputTextModule, InputNumberModule, ToolbarModule, DropdownModule, TagModule, RouterModule],
  templateUrl: './fournisseurs.component.html',
  styleUrl: './fournisseurs.component.css'
})
export class FournisseursComponent implements OnInit {
  fournisseurs: Fournisseur[] = [];
  selectedFournisseur: Fournisseur = { code: '', nom: '' };
  displayDialog: boolean = false;
  isEditMode: boolean = false;

  /** Terme de recherche lié à la barre de recherche premium (filtre client-side via filterGlobal). */
  searchTerm = '';

  /** Efface la recherche et réinitialise le filtre PrimeNG. */
  clearSearch(dt: any) {
    this.searchTerm = '';
    dt.filterGlobal('', 'contains');
  }
  statutOptions = [
    { label: 'Prospect', value: 'PROSPECT' },
    { label: 'En Évaluation', value: 'EN_EVALUATION' },
    { label: 'Homologué', value: 'HOMOLOGUE' },
    { label: 'Suspendu', value: 'SUSPENDU' }
  ];

  constructor(
    private fournisseurService: FournisseurService,
    public authService: AuthService,
    private router: Router
  ) {}

  isAdminOrDa(): boolean {
    const role = this.authService.currentUser()?.role;
    return role === 'SUPER_ADMIN' || role === 'ADMIN' || role === 'DA_MANAGER';
  }

  ngOnInit(): void {
    if (this.authService.currentUser()?.role === 'UNIT_USER') {
      this.router.navigate(['/catalogue']);
      return;
    }
    this.loadFournisseurs();
  }

  loadFournisseurs() {
    this.fournisseurService.getFournisseurs().subscribe(data => {
      this.fournisseurs = (data || []).sort((a, b) => (a.code || '').localeCompare(b.code || '', undefined, { numeric: true, sensitivity: 'base' }));
    });
  }

  openNew() {
    this.selectedFournisseur = { code: '', nom: '', matriculeFiscal: '', adresse: '', contactNom: '', telephone: '', email: '', fax: '', note: undefined, statut: 'PROSPECT' };
    this.isEditMode = false;
    this.displayDialog = true;
  }

  editFournisseur(fournisseur: Fournisseur) {
    this.selectedFournisseur = { ...fournisseur };
    this.isEditMode = true;
    this.displayDialog = true;
  }

  saveFournisseur() {
    // Validation stricte de la note (0 à 10)
    if (this.selectedFournisseur.note !== undefined && this.selectedFournisseur.note !== null) {
      if (this.selectedFournisseur.note < 0) this.selectedFournisseur.note = 0;
      if (this.selectedFournisseur.note > 10) this.selectedFournisseur.note = 10;
    }

    if (this.isEditMode && this.selectedFournisseur.id) {
      this.fournisseurService.updateFournisseur(this.selectedFournisseur.id, this.selectedFournisseur).subscribe(() => {
        this.loadFournisseurs();
        this.displayDialog = false;
      });
    } else {
      this.fournisseurService.createFournisseur(this.selectedFournisseur).subscribe(() => {
        this.loadFournisseurs();
        this.displayDialog = false;
      });
    }
  }

  /**
   * RÈGLE MÉTIER & TRAÇABILITÉ MILITAIRE :
   * La suppression définitive d'un fournisseur est formellement interdite afin de conserver
   * l'intégrité de l'historique des marchés, commandes et réceptions (BPR).
   * L'action bascule entre l'état SUSPENDU et HOMOLOGUE/PROSPECT.
   */
  toggleSuspendreFournisseur(fournisseur: Fournisseur) {
    if (!fournisseur.id) return;
    if (fournisseur.statut !== 'SUSPENDU') {
      if (confirm(`Confirmez-vous la suspension du fournisseur "${fournisseur.nom}" ? Il sera marqué inactif mais l'historique réglementaire sera intégralement conservé.`)) {
        this.fournisseurService.deleteFournisseur(fournisseur.id).subscribe(() => {
          this.loadFournisseurs();
        });
      }
    } else {
      if (confirm(`Voulez-vous réactiver le fournisseur "${fournisseur.nom}" ?`)) {
        const updateData: Fournisseur = { ...fournisseur, statut: 'HOMOLOGUE' };
        this.fournisseurService.updateFournisseur(fournisseur.id, updateData).subscribe(() => {
          this.loadFournisseurs();
        });
      }
    }
  }

  getStatutSeverity(statut: string | undefined): 'success' | 'secondary' | 'info' | 'warning' | 'danger' | 'contrast' | undefined {
    switch(statut) {
      case 'HOMOLOGUE': return 'success';
      case 'SUSPENDU': return 'danger';
      case 'EN_EVALUATION': return 'warning';
      case 'PROSPECT': return 'info';
      default: return 'info';
    }
  }
}
