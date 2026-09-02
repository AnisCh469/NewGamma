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

import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-fournisseurs',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DialogModule, InputTextModule, ToolbarModule, DropdownModule, TagModule, RouterModule],
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

  constructor(private fournisseurService: FournisseurService) {}

  ngOnInit(): void {
    this.loadFournisseurs();
  }

  loadFournisseurs() {
    this.fournisseurService.getFournisseurs().subscribe(data => {
      this.fournisseurs = data;
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

  deleteFournisseur(fournisseur: Fournisseur) {
    if (confirm('Voulez-vous vraiment supprimer ce fournisseur ?') && fournisseur.id) {
      this.fournisseurService.deleteFournisseur(fournisseur.id).subscribe(() => {
        this.loadFournisseurs();
      });
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
