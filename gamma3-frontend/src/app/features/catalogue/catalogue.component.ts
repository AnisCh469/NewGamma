/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROTOCOLE DE DOCUMENTATION ÉDUCATIVE — Composant Catalogue Frontend
 * Fichier : catalogue.component.ts
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * RÔLE DU MODULE :
 * Ce composant Angular gère l'affichage, le tri réactif et la recherche paginée
 * des articles du catalogue. Il offre une interface premium avec debouncing de
 * 300ms sur la recherche textuelle générale et une barre de recherche dédiée à la
 * nomenclature à 12 caractères.
 *
 * CONTEXTE TECHNIQUE & ARCHITECTURE :
 * 1. Reactive Signaly : Utilise l'architecture réactive standalone d'Angular 17
 *    avec des computed signals (`filteredData`) pour rafraîchir l'affichage sans
 *    surcharge de cycles de détection de changements.
 * 2. Debouncing anti-rebond : Protège le serveur API de requêtes excessives
 *    grâce à un délai d'attente de 300ms sur chaque frappe de clavier.
 * 3. Panier de dotation : Gère l'arbitrage automatique en limitant l'ajout au
 *    panier uniquement pour les articles faisant partie du Plan d'Armement officiel.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
import { Component, OnInit, OnDestroy, inject, computed, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { BadgeModule } from 'primeng/badge';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { ItemService } from './item.service';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/services/cart.service';
import { PlanArmementService, PlanArmement } from '../../core/services/plan-armement.service';
import { DistributionService } from '../../core/services/distribution.service';
import { ReceptionService } from '../../core/services/reception.service';
import { Magasin } from '../../core/models/stock.model';

@Component({
  selector: 'app-catalogue',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    InputTextModule,
    ButtonModule,
    ToastModule,
    DialogModule,
    BadgeModule,
    TooltipModule
  ],
  providers: [MessageService],
  templateUrl: './catalogue.component.html',
  styleUrls: ['./catalogue.component.css']
})
export class CatalogueComponent implements OnInit {
  
  itemService = inject(ItemService);
  authService = inject(AuthService);
  cartService = inject(CartService);
  private planArmementService = inject(PlanArmementService);
  private distributionService = inject(DistributionService);
  private receptionService = inject(ReceptionService);
  private messageService = inject(MessageService);

  isClient(): boolean {
    return this.authService.currentUser()?.role === 'UNIT_USER';
  }

  /**
   * Signal calculé (computed) qui représente les données filtrées affichées dans le tableau.
   */
  filteredData = computed(() => this.itemService.items());

  private router = inject(Router);

  // Pagination & Tri
  currentPage = 0;
  pageSize = 25;
  searchTerm = '';
  nomenclatureSearchTerm = '';
  sortField = 'nomenclature';
  sortOrder: 'asc' | 'desc' = 'asc';
  filterType: 'CONSOMMABLE' | 'NON_CONSOMMABLE' | null = null;
  private searchTimeout: any;

  // ─── Gestion Multi-Magasins ─────────────────────────────────────────────
  // RÔLE : Permettre au DA Manager de filtrer le catalogue par soute.
  //        La soute active est auto-détectée selon le matricule de l'utilisateur connecté.
  magasins: Magasin[] = [];
  selectedMagasinId: number | null = null;
  selectedMagasinLabel: string = 'Tous les Magasins';

  // Map matricule -> magasin ID par convention de nommage
  private readonly MAGASIN_MAP: Record<string, number> = {
    'magasin_centre': 2,  // SM1 — Centre
    'magasin_sud': 3      // SM2 — Sud
    // L'admin (SGS) voit tout par défaut (null)
  };

  // Cart/Panier dialog
  displayCartDialog: boolean = false;
  isSubmittingCart: boolean = false;

  ngOnInit(): void {
    // ÉTAPE 1 : Charger la liste des magasins pour le sélecteur
    this.receptionService.getMagasins().subscribe({
      next: (list) => {
        this.magasins = list;

        // ÉTAPE 2 : Auto-détecter le magasin de l'utilisateur DA Manager connecté
        const matricule = this.authService.currentUser()?.matricule || '';
        if (this.MAGASIN_MAP[matricule] !== undefined) {
          this.selectedMagasinId = this.MAGASIN_MAP[matricule];
          const found = list.find(m => m.id === this.selectedMagasinId);
          this.selectedMagasinLabel = found ? found.nom : 'Magasin';
        }

        // ÉTAPE 3 : Chargement initial du catalogue avec le filtre magasin actif
        this.itemService.fetchItems(0, this.pageSize, undefined, this.sortField, this.sortOrder, false,
          this.filterType || undefined, this.selectedMagasinId ?? undefined);
      },
      error: () => {
        // Fallback : charger sans filtre magasin
        this.itemService.fetchItems(0, this.pageSize, undefined, this.sortField, this.sortOrder, false, this.filterType || undefined);
      }
    });

    // Si l'utilisateur est un client, charger ses plans d'armement pour le panier
    if (this.isClient()) {
      this.loadClientPlans();
    }
  }

  /**
   * RÔLE :
   * Appelée quand l'utilisateur sélectionne une soute dans le sélecteur.
   * Rafraîchit le catalogue pour n'afficher que les articles disponibles dans cette soute.
   */
  onMagasinChange(magasinId: number | null): void {
    this.selectedMagasinId = magasinId;
    const found = this.magasins.find(m => m.id === magasinId);
    this.selectedMagasinLabel = magasinId == null ? 'Tous les Magasins' : (found?.nom || 'Magasin');
    this.currentPage = 0;
    const activeSearch = this.nomenclatureSearchTerm || this.searchTerm;
    this.itemService.fetchItems(0, this.pageSize, activeSearch || undefined, this.sortField, this.sortOrder, false,
      this.filterType || undefined, this.selectedMagasinId ?? undefined);
  }

  loadClientPlans(): void {
    const user = this.authService.currentUser();
    if (!user?.matricule) return;

    this.planArmementService.getPlansByUniteCode(user.matricule).subscribe({
      next: (plans) => {
        // Récupérer l'ID de l'unité à partir du premier plan
        const uniteId = plans[0]?.unite?.id;
        if (uniteId) {
          this.cartService.setPlans(plans, uniteId);
        }
      },
      error: (err) => console.error('Impossible de charger le plan d\'armement:', err)
    });
  }

  /**
   * RÔLE :
   * Gère les événements de changement de page, de tri et de redimensionnement de tableau PrimeNG.
   *
   * POURQUOI CETTE LOGIQUE :
   * - Permet une synchronisation transparente avec le serveur backend (Pagination SQL Server/Postgres).
   * - Maintient l'état de tri et de recherche actif à travers la pagination.
   *
   * PARAMÈTRES :
   * @param event L'événement généré par PrimeNG (contenant first, rows, sortField, sortOrder).
   *
   * RETOUR :
   * @return void
   */
  onPageChange(event: any) {
    // ÉTAPE 1 : Calcul de l'index de page (0-based)
    this.currentPage = Math.floor(event.first / event.rows);
    this.pageSize = event.rows;
    
    // ÉTAPE 2 : Capture de l'en-tête de tri cliqué par l'utilisateur
    if (event.sortField) {
      this.sortField = event.sortField;
      this.sortOrder = event.sortOrder === 1 ? 'asc' : 'desc';
    }

    // ÉTAPE 3 : Identification du filtre de recherche textuel prioritaire (Exclusion mutuelle)
    const activeSearch = this.nomenclatureSearchTerm || this.searchTerm;
    
    // ÉTAPE 4 : Émission de la requête HTTP asynchrone via le service d'état standalone
    this.itemService.fetchItems(this.currentPage, this.pageSize, activeSearch || undefined, this.sortField, this.sortOrder, false, this.filterType || undefined, this.selectedMagasinId ?? undefined);
  }

  /**
   * RÔLE :
   * Déclenche la recherche par désignation textuelle libre avec debouncing de 300ms.
   *
   * POURQUOI CETTE LOGIQUE :
   * - Évite le pilonnage de l'API REST à chaque frappe clavier.
   * - Assure l'exclusion mutuelle en vidant le champ de nomenclature.
   *
   * PARAMÈTRES : Aucun
   * RETOUR : void
   */
  onSearch() {
    // ÉTAPE 1 : Annuler la recherche nomenclature par souci de cohérence
    this.nomenclatureSearchTerm = '';
    
    // ÉTAPE 2 : Effacer le minuteur anti-rebond (debounce) précédent
    clearTimeout(this.searchTimeout);
    
    // ÉTAPE 3 : Lancer un nouveau minuteur pour temporiser la requête de 300ms
    this.searchTimeout = setTimeout(() => {
      this.currentPage = 0;
      this.itemService.fetchItems(0, this.pageSize, this.searchTerm || undefined, this.sortField, this.sortOrder, false, this.filterType || undefined, this.selectedMagasinId ?? undefined);
    }, 300);
  }

  /**
   * RÔLE :
   * Déclenche la recherche ciblée sur la nomenclature compacte (12 caractères) avec debouncing.
   *
   * POURQUOI CETTE LOGIQUE :
   * - Permet d'isoler instantanément un article officiel de la Marine.
   * - Assure l'exclusion mutuelle en réinitialisant la recherche par désignation.
   *
   * PARAMÈTRES : Aucun
   * RETOUR : void
   */
  onNomenclatureSearch() {
    // ÉTAPE 1 : Réinitialisation de la recherche textuelle
    this.searchTerm = '';
    
    // ÉTAPE 2 : Annulation du minuteur de debouncing actif
    clearTimeout(this.searchTimeout);
    
    // ÉTAPE 3 : Déclenchement de la requête différée après 300ms de silence clavier
    this.searchTimeout = setTimeout(() => {
      this.currentPage = 0;
      this.itemService.fetchItems(0, this.pageSize, this.nomenclatureSearchTerm || undefined, this.sortField, this.sortOrder, false, this.filterType || undefined, this.selectedMagasinId ?? undefined);
    }, 300);
  }

  /**
   * RÔLE :
   * Efface et réinitialise tous les filtres de recherche textuelle globale.
   */
  clearSearch() {
    this.searchTerm = '';
    this.nomenclatureSearchTerm = '';
    this.currentPage = 0;
    this.itemService.fetchItems(0, this.pageSize, undefined, this.sortField, this.sortOrder, false, this.filterType || undefined, this.selectedMagasinId ?? undefined);
  }

  /**
   * RÔLE :
   * Réinitialise spécifiquement le filtre de nomenclature à 12 caractères.
   */
  clearNomenclatureSearch() {
    this.nomenclatureSearchTerm = '';
    this.currentPage = 0;
    this.itemService.fetchItems(0, this.pageSize, undefined, this.sortField, this.sortOrder, false, this.filterType || undefined, this.selectedMagasinId ?? undefined);
  }

  filterByType(type: 'CONSOMMABLE' | 'NON_CONSOMMABLE' | null): void {
    this.filterType = type;
    this.currentPage = 0;
    const activeSearch = this.nomenclatureSearchTerm || this.searchTerm;
    this.itemService.fetchItems(0, this.pageSize, activeSearch || undefined, this.sortField, this.sortOrder, false, this.filterType || undefined, this.selectedMagasinId ?? undefined);
  }

  /** Ctrl+K : focus sur la barre de recherche */
  @HostListener('document:keydown', ['$event'])
  handleCtrlK(event: KeyboardEvent) {
    if ((event.ctrlKey || event.metaKey) && event.key === 'k') {
      event.preventDefault();
      const input = document.getElementById('search-input') as HTMLInputElement;
      if (input) input.focus();
    }
  }

  onRowSelect(event: any) {
    if (event.data && event.data.id) {
      this.router.navigate(['/catalogue', event.data.id]);
    }
  }

  // ===== GESTION DU PANIER (CLIENTS) =====

  /**
   * Récupère le plan correspondant à un item du catalogue.
   */
  getPlanForItem(itemId: string): PlanArmement | null {
    return this.cartService.getPlanForItem(itemId);
  }

  /**
   * Indique si un article est dans le panier de demande.
   */
  isInCart(itemId: string): boolean {
    return this.cartService.cart().some(c => c.plan.item.id === itemId);
  }

  /**
   * Ajoute au panier de demande. L'article doit être dans le plan d'armement de l'unité.
   */
  addToCart(item: any, event: Event): void {
    event.stopPropagation(); // ne pas naviguer vers la fiche article

    const plan = this.getPlanForItem(item.id);
    if (!plan) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Hors Dotation',
        detail: `L'article « ${item.designation} » n'est pas dans votre Plan d'Armement. Faites une Demande d'Ajout depuis votre fiche unité.`,
        life: 5000
      });
      return;
    }

    const result = this.cartService.addItem(plan);
    this.messageService.add({
      severity: result.success ? 'success' : 'error',
      summary: result.success ? 'Panier' : 'Limite Dépassée',
      detail: result.message,
      life: 3000
    });
  }

  openCartDialog(): void {
    if (this.cartService.totalLines() === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Panier Vide',
        detail: 'Ajoutez des articles de votre dotation avant de valider.',
        life: 3000
      });
      return;
    }
    this.displayCartDialog = true;
  }

  removeFromCart(index: number): void {
    this.cartService.removeItem(index);
  }

  clearCart(): void {
    this.cartService.clearCart();
    this.messageService.add({ severity: 'info', summary: 'Panier vidé', detail: 'Le panier a été vidé.', life: 2000 });
  }

  getRemainingForPlan(plan: PlanArmement): number {
    return this.cartService.getRemainingForPlan(plan);
  }

  onCartQuantityChange(index: number, value: number): void {
    this.cartService.setQuantity(index, value);
  }

  /**
   * RÔLE :
   * Soumet les lignes d'articles du panier sous forme de Demande Officielle de Ravitaillement au backend.
   *
   * POURQUOI CETTE LOGIQUE :
   * - Assure la validation complète côté client avant l'envoi HTTP pour économiser les ressources serveur.
   * - Construit le modèle d'agrégation requis (Demande + LigneDemandes).
   * - Met à jour l'UI en rafraîchissant les plans d'armement locaux après soumission réussie.
   *
   * PARAMÈTRES : Aucun
   * RETOUR : void
   */
  submitCartDemande(): void {
    // ÉTAPE 1 : Garde-fou d'absence de lignes
    if (this.cartService.totalLines() === 0) return;

    // ÉTAPE 2 : Validation des limites de quota et de dotation (Plan d'Armement)
    const validation = this.cartService.validate();
    if (!validation.valid) {
      validation.errors.forEach(err => {
        this.messageService.add({ severity: 'error', summary: 'Quantité Invalide', detail: err, life: 5000 });
      });
      return;
    }

    // ÉTAPE 3 : Identification technique de l'unité de l'utilisateur
    const uniteId = this.cartService.getUniteId();
    if (!uniteId) {
      this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible d\'identifier votre unité.', life: 4000 });
      return;
    }

    // ÉTAPE 4 : Activation de l'état de soumission (Spinner de chargement)
    this.isSubmittingCart = true;

    // ÉTAPE 5 : Construction du Payload JSON unifié conforme à l'API Rest de Distribution
    const payload = {
      demande: {
        numeroDemande: 'DEM-' + new Date().getFullYear() + '-' + Math.floor(1000 + Math.random() * 9000),
        dateDemande: new Date().toISOString().split('T')[0],
        unite: { id: uniteId } as any
      },
      lignes: this.cartService.cart().map(c => ({
        item: { id: c.plan.item.id } as any,
        quantiteDemandee: c.quantiteDemandee
      }))
    };

    // ÉTAPE 6 : Souscription à l'appel HTTP POST via RxJS Observable
    this.distributionService.createDemande(payload).subscribe({
      next: () => {
        // ÉTAPE 7a : En cas de succès -> notifications, effacement du panier local et rechargement des quotas
        this.messageService.add({
          severity: 'success',
          summary: 'Demande Soumise ✓',
          detail: `Votre demande de ${this.cartService.totalLines()} article(s) a été transmise à la DA.`,
          life: 6000
        });
        this.cartService.clearCart();
        this.displayCartDialog = false;
        this.isSubmittingCart = false;
        this.loadClientPlans(); // ÉTAPE vital : recalculer les dotations sur l'UI
      },
      error: (err: any) => {
        // ÉTAPE 7b : En cas d'erreur -> notification et désactivation du spinner de chargement
        const errorMsg = typeof err.error === 'string' ? err.error : 'Impossible de soumettre la demande.';
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: errorMsg, life: 5000 });
        this.isSubmittingCart = false;
      }
    });
  }
}
