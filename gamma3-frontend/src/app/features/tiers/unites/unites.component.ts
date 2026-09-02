import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UniteService, UniteUtilisatrice } from '../../../core/services/unite.service';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';

import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-unites',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DialogModule, InputTextModule, RouterModule],
  templateUrl: './unites.component.html',
  styleUrl: './unites.component.css'
})
export class UnitesComponent implements OnInit {
  unites: UniteUtilisatrice[] = [];
  selectedUnite: UniteUtilisatrice = { code: '', nom: '' };
  displayDialog: boolean = false;
  isEditMode: boolean = false;

  /** Terme de recherche lié à la barre de recherche premium (filtre client-side via filterGlobal). */
  searchTerm = '';

  /** Efface la recherche et réinitialise le filtre PrimeNG. */
  clearSearch(dt: any) {
    this.searchTerm = '';
    dt.filterGlobal('', 'contains');
  }
  constructor(private uniteService: UniteService) {}

  ngOnInit(): void {
    this.loadUnites();
  }

  loadUnites() {
    this.uniteService.getUnites().subscribe(data => {
      this.unites = data;
    });
  }

  openNew() {
    this.selectedUnite = { code: '', nom: '', baseNavale: '' };
    this.isEditMode = false;
    this.displayDialog = true;
  }

  editUnite(unite: UniteUtilisatrice) {
    this.selectedUnite = { ...unite };
    this.isEditMode = true;
    this.displayDialog = true;
  }

  saveUnite() {
    if (this.isEditMode && this.selectedUnite.id) {
      this.uniteService.updateUnite(this.selectedUnite.id, this.selectedUnite).subscribe(() => {
        this.loadUnites();
        this.displayDialog = false;
      });
    } else {
      this.uniteService.createUnite(this.selectedUnite).subscribe(() => {
        this.loadUnites();
        this.displayDialog = false;
      });
    }
  }

  deleteUnite(unite: UniteUtilisatrice) {
    if (confirm('Voulez-vous vraiment supprimer cette unité ?') && unite.id) {
      this.uniteService.deleteUnite(unite.id).subscribe(() => {
        this.loadUnites();
      });
    }
  }
}
