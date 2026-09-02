import { Component, OnInit, inject, computed, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ItemService, Item } from '../item.service';
import { AuthService } from '../../../core/auth/auth.service';
import { PlanArmementService, DemandeDotation } from '../../../core/services/plan-armement.service';
import { UniteService, UniteUtilisatrice } from '../../../core/services/unite.service';
import { DistributionService } from '../../../core/services/distribution.service';

@Component({
  selector: 'app-demander-dotation',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule, 
    RouterModule,
    TableModule, 
    InputTextModule, 
    ButtonModule,
    DialogModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './demander-dotation.component.html',
  styleUrls: ['./demander-dotation.component.css']
})
export class DemanderDotationComponent implements OnInit {
  
  itemService = inject(ItemService);
  authService = inject(AuthService);
  planArmementService = inject(PlanArmementService);
  uniteService = inject(UniteService);
  distributionService = inject(DistributionService);
  private messageService = inject(MessageService);
  private router = inject(Router);

  currentUnite: UniteUtilisatrice | null = null;

  // Cart / Panier logic
  cart: { item: Item; quantiteDemandee: number }[] = [];
  displayCartDialog: boolean = false;

  addToCart(item: Item): void {
    const existing = this.cart.find(c => c.item.id === item.id);
    const remaining = this.getRemainingDotation(item);
    
    if (remaining <= 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Dotation Atteinte',
        detail: `La dotation réglementaire maximale est déjà allouée pour ${item.designation}.`
      });
      return;
    }

    if (existing) {
      if (existing.quantiteDemandee < remaining) {
        existing.quantiteDemandee += 1;
        this.messageService.add({
          severity: 'success',
          summary: 'Panier Mis à Jour',
          detail: `Quantité demandée passée à ${existing.quantiteDemandee} pour ${item.designation}.`
        });
      } else {
        this.messageService.add({
          severity: 'error',
          summary: 'Limite Dépassée',
          detail: `Impossible de dépasser la dotation restante de ${remaining} unité(s).`
        });
      }
    } else {
      this.cart.push({ item, quantiteDemandee: 1 });
      this.messageService.add({
        severity: 'success',
        summary: 'Ajouté au Panier',
        detail: `L'article ${item.designation} a été ajouté au panier.`
      });
    }
  }

  removeFromCart(index: number): void {
    this.cart.splice(index, 1);
    this.messageService.add({
      severity: 'info',
      summary: 'Panier',
      detail: 'Article retiré du panier.'
    });
  }

  clearCart(): void {
    this.cart = [];
    this.messageService.add({
      severity: 'info',
      summary: 'Panier',
      detail: 'Panier vidé avec succès.'
    });
  }

  getCartTotalCount(): number {
    return this.cart.reduce((sum, c) => sum + c.quantiteDemandee, 0);
  }

  getRemainingDotation(item: Item): number {
    return Math.max(0, (item.quantiteType || 0) - (item.quantiteReelle || 0));
  }

  openCartDialog(): void {
    if (this.cart.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Panier Vide',
        detail: 'Votre panier est vide. Veuillez d\'abord ajouter des matériels de votre Plan.'
      });
      return;
    }
    this.displayCartDialog = true;
  }

  submitCartDemande(): void {
    if (this.cart.length === 0 || !this.currentUnite || !this.currentUnite.id) return;

    // Validation des limites
    for (const c of this.cart) {
      const remaining = this.getRemainingDotation(c.item);
      if (c.quantiteDemandee <= 0 || c.quantiteDemandee > remaining) {
        this.messageService.add({
          severity: 'error',
          summary: 'Limite Dépassée',
          detail: `La quantité pour ${c.item.designation} doit être comprise entre 1 et ${remaining}.`
        });
        return;
      }
    }

    const payload = {
      demande: {
        numeroDemande: 'DEM-' + new Date().getFullYear() + '-' + Math.floor(1000 + Math.random() * 9000),
        dateDemande: new Date().toISOString().split('T')[0],
        unite: { id: this.currentUnite.id } as any
      },
      lignes: this.cart.map(c => ({
        item: { id: c.item.id } as any,
        quantiteDemandee: c.quantiteDemandee
      }))
    };

    this.distributionService.createDemande(payload).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Demande Soumise',
          detail: `Votre demande de matériel avec ${this.cart.length} ligne(s) a été envoyée à la DA.`
        });
        this.cart = [];
        this.displayCartDialog = false;
        this.loadItems();
      },
      error: (err: any) => {
        const errorMsg = typeof err.error === 'string' ? err.error : 'Impossible de soumettre la demande.';
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: errorMsg });
      }
    });
  }
  
  // Data from computed signal (calling with bypassPlanArmement = true)
  filteredData = computed(() => this.itemService.items());

  // Pagination & Tri
  currentPage = 0;
  pageSize = 25;
  searchTerm = '';
  sortField = 'classeCode,sousClasseCode,categorieCode,serieCode,itemCode';
  sortOrder: 'asc' | 'desc' = 'asc';
  private searchTimeout: any;

  // Dialog State
  displayDialog: boolean = false;
  selectedItem: Item | null = null;
  quantiteDotation: number = 10; // Default requested quantity

  ngOnInit(): void {
    const currentUser = this.authService.currentUser();
    if (!currentUser || currentUser.role !== 'UNIT_USER') {
      this.router.navigate(['/catalogue']);
      return;
    }

    // Load unit details using the logged-in user's matricule
    this.uniteService.getUnites().subscribe({
      next: (unites) => {
        const found = unites.find(u => u.code === currentUser.matricule);
        if (found) {
          this.currentUnite = found;
        } else {
          this.messageService.add({
            severity: 'error',
            summary: 'Erreur',
            detail: 'Impossible de localiser votre unité.'
          });
        }
      }
    });

    // Fetch items with bypassPlanArmement = true to view the entire catalogue
    this.loadItems();
  }

  loadItems() {
    this.itemService.fetchItems(
      this.currentPage, 
      this.pageSize, 
      this.searchTerm || undefined, 
      this.sortField, 
      this.sortOrder,
      true // bypassPlanArmement = true
    );
  }

  onPageChange(event: any) {
    this.currentPage = Math.floor(event.first / event.rows);
    this.pageSize = event.rows;
    
    if (event.sortField) {
      this.sortField = event.sortField === 'nomenclature' 
        ? 'classeCode,sousClasseCode,categorieCode,serieCode,itemCode' 
        : event.sortField;
      this.sortOrder = event.sortOrder === 1 ? 'asc' : 'desc';
    }

    this.loadItems();
  }

  onSearch() {
    clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => {
      this.currentPage = 0;
      this.loadItems();
    }, 300);
  }

  clearSearch() {
    this.searchTerm = '';
    this.currentPage = 0;
    this.loadItems();
  }

  @HostListener('document:keydown', ['$event'])
  handleCtrlK(event: KeyboardEvent) {
    if ((event.ctrlKey || event.metaKey) && event.key === 'k') {
      event.preventDefault();
      const input = document.getElementById('search-input') as HTMLInputElement;
      if (input) input.focus();
    }
  }

  openRequestDialog(item: Item, event: MouseEvent) {
    event.stopPropagation(); // Avoid row click selection if any
    this.selectedItem = item;
    this.quantiteDotation = 10; // default quantity
    this.displayDialog = true;
  }

  submitRequest() {
    if (!this.selectedItem || !this.currentUnite || !this.currentUnite.id) {
      this.messageService.add({
        severity: 'error',
        summary: 'Erreur',
        detail: 'Données incomplètes pour soumettre la demande.'
      });
      return;
    }

    if (this.quantiteDotation <= 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Quantité invalide',
        detail: 'Veuillez saisir une quantité supérieure à 0.'
      });
      return;
    }

    const payload: DemandeDotation = {
      unite: { id: this.currentUnite.id, code: this.currentUnite.code, nom: this.currentUnite.nom },
      item: this.selectedItem,
      quantiteType: this.quantiteDotation
    };

    this.planArmementService.creerDemandeDotation(payload).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Demande Soumise',
          detail: `Votre demande pour l'article ${this.selectedItem?.designation} a été transmise à la DA.`
        });
        this.displayDialog = false;
        // Optionally refresh page content to show updated values or state
        this.loadItems();
      },
      error: (err) => {
        console.error(err);
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur',
          detail: 'Impossible de soumettre la demande d\'ajout.'
        });
      }
    });
  }
}
