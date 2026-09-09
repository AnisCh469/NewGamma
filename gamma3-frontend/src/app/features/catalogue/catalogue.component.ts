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
import { DropdownModule } from 'primeng/dropdown';
import { FileUploadModule } from 'primeng/fileupload';
import { MessageService } from 'primeng/api';
import { ItemService, Item } from './item.service';
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
    TooltipModule,
    DropdownModule,
    FileUploadModule
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

  // ─── Dialog de création d'article (Admin / DA Manager) ────────────────────
  // RÔLE : État du formulaire modal "Nouvel Article" (bouton visible pour tout
  //        rôle autre que UNIT_USER, voir isClient()).
  displayNewItemDialog: boolean = false;
  isSavingNewItem: boolean = false;
  newItem: Partial<Item> = {};

  // Classification en cascade (Classe > Sous-classe > Catégorie > Série > Code Article) :
  // chaque article doit appartenir à une classification bien définie (règle CCF), ces 5
  // segments de 2 (ou 4 pour l'item) caractères sont concaténés pour former la nomenclature
  // à 12 caractères envoyée au backend (voir saveNewItem()).
  newItemClasse: string = '';
  newItemSousClasse: string = '';
  newItemCategorie: string = '';
  newItemSerie: string = '';
  newItemItemCode: string = '';

  sousClasseOptions: { label: string; value: string }[] = [];
  categorieOptions: { label: string; value: string }[] = [];
  serieOptions: { label: string; value: string }[] = [];
  isLoadingSousClasse = false;
  isLoadingCategorie = false;
  isLoadingSerie = false;
  isLoadingItemSuggestion = false;

  // Fichiers sélectionnés dans le formulaire de création, envoyés APRÈS la création de
  // l'article (les endpoints d'upload existants exigent un UUID d'article déjà persistant).
  newItemPhotoFile: File | null = null;
  newItemDocFile: File | null = null;

  /**
   * Les 8 classes officielles de matériel (CCF, doc "Instructions de Création/Modification
   * FT", §3.1.1) — liste fermée et stable, contrairement aux niveaux inférieurs qui évoluent
   * au gré des nouveaux équipements catalogués. Code stocké sur 2 caractères (zéro-préfixé).
   */
  readonly classeOptions = [
    { label: '01 — Matériel Mécanique', value: '01' },
    { label: '02 — Matériel Électronique', value: '02' },
    { label: "03 — Matériel d'Armes et Système d'Armes", value: '03' },
    { label: '04 — Matériel Électrotechnique', value: '04' },
    { label: "05 — Matière d'Œuvres", value: '05' },
    { label: '06 — Matériel de Protection Générale et de Plongée', value: '06' },
    { label: '07 — Matériel de Quincaillerie Générale', value: '07' },
    { label: "08 — Matériel d'Équipement Général de Vie à Bord", value: '08' }
  ];

  /** Sous-classes officiellement documentées (CCF) pour la Classe 01 — Matériel Mécanique. */
  private readonly SOUS_CLASSE_LABELS: Record<string, Record<string, string>> = {
    '01': {
      '00': 'Moteurs Diesels', '05': 'Réducteurs Inverseurs', '10': 'Lignes de Propulsion',
      '15': 'Compresseurs', '20': 'Pompes', '25': 'Installations Frigorifiques',
      '30': 'Groupes Hydrophores', '35': "Unités de Production d'Eau",
      '40': 'Chaudières', '45': 'Turbines à Gaz', '50': "Système d'Évacuation des Eaux Usées"
    }
  };

  /** Catégories (constructeurs) officiellement documentées (CCF) pour 01/00 — Moteurs Diesels. */
  private readonly CATEGORIE_LABELS: Record<string, Record<string, string>> = {
    '0100': {
      'MT': 'MTU', 'MW': 'MWM', 'GM': 'GM', 'CT': 'CATERPILLAR', 'DZ': 'DEUTZ',
      'VP': 'VOLVO PENTA', 'MN': 'MAN', 'IV': 'IVECO AIFO', 'BD': 'BAUDOIN',
      'DM': 'DM GUSK SVES', 'LM': 'LAMBARDINI', 'NR': 'NORDBERG', 'PG': 'PEGASO GUASCOR',
      'ON': 'ONAN', 'WB': 'WESTER BEKE'
    }
  };

  /** Séries officiellement documentées (CCF) pour 01/00/MT — Moteurs Diesels MTU. */
  private readonly SERIE_LABELS: Record<string, Record<string, string>> = {
    '0100MT': {
      '01': '20 V 538', '02': '16 V 956', '03': '12 V 493',
      '04': '8/12 V 331 TC 92', '05': '12/16 V 652', '06': '8 V 4000M70'
    }
  };

  readonly typeConsommabiliteOptions = [
    { label: 'Consommable', value: 'CONSOMMABLE' },
    { label: 'Non Consommable', value: 'NON_CONSOMMABLE' }
  ];

  readonly dangerClassOptions = [
    { label: 'Aucun', value: 'NONE' },
    { label: 'Explosif', value: 'EXPLOSIVE' },
    { label: 'Inflammable', value: 'FLAMMABLE' },
    { label: 'Toxique', value: 'TOXIC' },
    { label: 'Corrosif', value: 'CORROSIVE' },
    { label: 'Radioactif', value: 'RADIOACTIVE' },
    { label: 'Danger Environnemental', value: 'ENVIRONMENTAL_HAZARD' },
    { label: 'Comburant', value: 'OXIDIZING' },
    { label: 'Gaz Comprimé', value: 'COMPRESSED_GAS' },
    { label: 'Nocif', value: 'HARMFUL' }
  ];

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
    // CORRECTIF (2026-09-09) : ne plus supposer « matricule = code unité » — faux pour le
    // Commandant (RPS_CDT ≠ RPS), d'où un panier / catalogue vide. On utilise le
    // rattachement explicite (uniteCode) renvoyé au login, avec repli sur le matricule.
    const uniteCode = user?.uniteCode || user?.matricule;
    if (!uniteCode) return;

    this.planArmementService.getPlansByUniteCode(uniteCode).subscribe({
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

  // ===== CRÉATION D'ARTICLE (ADMIN / DA MANAGER) =====

  /**
   * RÔLE :
   * Ouvre le formulaire modal de création d'un nouvel article avec des valeurs par défaut,
   * et réinitialise la classification en cascade et les fichiers d'un précédent essai.
   *
   * PARAMÈTRES : Aucun
   * RETOUR : void
   */
  openNewItemDialog(): void {
    this.newItem = {
      designation: '',
      prixUnitaire: 0,
      stockSecurite: 0,
      uniteGestionCode: '',
      typeConsommabilite: 'CONSOMMABLE',
      dangerClass: 'NONE'
    };
    this.newItemClasse = '';
    this.newItemSousClasse = '';
    this.newItemCategorie = '';
    this.newItemSerie = '';
    this.newItemItemCode = '';
    this.sousClasseOptions = [];
    this.categorieOptions = [];
    this.serieOptions = [];
    this.newItemPhotoFile = null;
    this.newItemDocFile = null;
    this.displayNewItemDialog = true;
  }

  /**
   * RÔLE :
   * Construit le libellé affiché dans une liste déroulante de classification à partir d'un
   * code retourné par l'API (code, désignation d'exemple, nombre d'articles), enrichi par le
   * libellé officiel CCF quand il est connu (voir SOUS_CLASSE_LABELS / CATEGORIE_LABELS /
   * SERIE_LABELS) — sinon un aperçu basé sur un article existant, sinon "(nouveau)".
   */
  private formatSegmentLabel(code: string, exempleDesignation: string | null, nombreArticles: number, knownLabels?: Record<string, string>): string {
    const known = knownLabels?.[code];
    if (known) return `${code} — ${known}`;
    if (exempleDesignation) return `${code} — ex: ${exempleDesignation} (${nombreArticles} article${nombreArticles > 1 ? 's' : ''})`;
    return `${code} (nouveau)`;
  }

  /**
   * RÔLE :
   * Appelée quand la Classe change : réinitialise les niveaux inférieurs puis charge les
   * codes Sous-classe déjà utilisés dans le catalogue pour cette classe.
   */
  onClasseChange(): void {
    this.newItemSousClasse = '';
    this.newItemCategorie = '';
    this.newItemSerie = '';
    this.newItemItemCode = '';
    this.categorieOptions = [];
    this.serieOptions = [];
    this.sousClasseOptions = [];
    if (this.newItemClasse.length !== 2) return;

    this.isLoadingSousClasse = true;
    this.itemService.getNomenclatureNextLevel(this.newItemClasse).subscribe({
      next: (res) => {
        const labels = this.SOUS_CLASSE_LABELS[this.newItemClasse];
        this.sousClasseOptions = (res.options || []).map((o: any) =>
          ({ label: this.formatSegmentLabel(o.code, o.exempleDesignation, o.nombreArticles, labels), value: o.code }));
        this.isLoadingSousClasse = false;
      },
      error: () => { this.isLoadingSousClasse = false; }
    });
  }

  /**
   * RÔLE :
   * Appelée quand la Sous-classe change (sélection OU saisie libre d'un nouveau code) :
   * réinitialise les niveaux inférieurs puis charge les codes Catégorie correspondants dès
   * que 2 caractères valides sont saisis.
   */
  onSousClasseChange(): void {
    this.newItemSousClasse = (this.newItemSousClasse || '').toUpperCase().slice(0, 2);
    this.newItemCategorie = '';
    this.newItemSerie = '';
    this.newItemItemCode = '';
    this.serieOptions = [];
    this.categorieOptions = [];
    if (this.newItemSousClasse.length !== 2) return;

    this.isLoadingCategorie = true;
    this.itemService.getNomenclatureNextLevel(this.newItemClasse, this.newItemSousClasse).subscribe({
      next: (res) => {
        const labels = this.CATEGORIE_LABELS[this.newItemClasse + this.newItemSousClasse];
        this.categorieOptions = (res.options || []).map((o: any) =>
          ({ label: this.formatSegmentLabel(o.code, o.exempleDesignation, o.nombreArticles, labels), value: o.code }));
        this.isLoadingCategorie = false;
      },
      error: () => { this.isLoadingCategorie = false; }
    });
  }

  /**
   * RÔLE :
   * Appelée quand la Catégorie change : réinitialise Série/Code Article puis charge les
   * codes Série correspondants dès que 2 caractères valides sont saisis.
   */
  onCategorieChange(): void {
    this.newItemCategorie = (this.newItemCategorie || '').toUpperCase().slice(0, 2);
    this.newItemSerie = '';
    this.newItemItemCode = '';
    this.serieOptions = [];
    if (this.newItemCategorie.length !== 2) return;

    this.isLoadingSerie = true;
    this.itemService.getNomenclatureNextLevel(this.newItemClasse, this.newItemSousClasse, this.newItemCategorie).subscribe({
      next: (res) => {
        const labels = this.SERIE_LABELS[this.newItemClasse + this.newItemSousClasse + this.newItemCategorie];
        this.serieOptions = (res.options || []).map((o: any) =>
          ({ label: this.formatSegmentLabel(o.code, o.exempleDesignation, o.nombreArticles, labels), value: o.code }));
        this.isLoadingSerie = false;
      },
      error: () => { this.isLoadingSerie = false; }
    });
  }

  /**
   * RÔLE :
   * Appelée quand la Série change : réinitialise le Code Article puis déclenche
   * automatiquement la suggestion du prochain code libre dans cette série.
   */
  onSerieChange(): void {
    this.newItemSerie = (this.newItemSerie || '').toUpperCase().slice(0, 2);
    this.newItemItemCode = '';
    if (this.newItemSerie.length !== 2) return;
    this.suggestItemCode();
  }

  /**
   * RÔLE :
   * Demande au backend le prochain code Item (4 chiffres) libre dans la série sélectionnée
   * et pré-remplit le champ — l'utilisateur reste libre de le modifier avant d'enregistrer.
   */
  suggestItemCode(): void {
    if (this.newItemSerie.length !== 2) return;
    this.isLoadingItemSuggestion = true;
    this.itemService.getNomenclatureNextLevel(this.newItemClasse, this.newItemSousClasse, this.newItemCategorie, this.newItemSerie).subscribe({
      next: (res) => {
        this.newItemItemCode = res.suggestedCode || '';
        this.isLoadingItemSuggestion = false;
      },
      error: () => { this.isLoadingItemSuggestion = false; }
    });
  }

  /** Capture le fichier photo choisi dans le formulaire (upload différé, voir saveNewItem()). */
  onNewPhotoSelected(event: any): void {
    if (event.files && event.files.length > 0) {
      this.newItemPhotoFile = event.files[0];
    }
  }

  /** Capture le fichier PDF choisi dans le formulaire (upload différé, voir saveNewItem()). */
  onNewDocSelected(event: any): void {
    if (event.files && event.files.length > 0) {
      this.newItemDocFile = event.files[0];
    }
  }

  /**
   * RÔLE :
   * Vérifie que le formulaire de création d'article contient les champs obligatoires
   * avant d'activer le bouton "Enregistrer" (garde-fou côté client, en complément de
   * la validation serveur dans ItemController.createItem) : les 4 segments de
   * classification (2 caractères chacun), le code Item (4 chiffres) et la désignation.
   *
   * RETOUR : true si le formulaire peut être soumis.
   */
  isNewItemValid(): boolean {
    return this.newItemClasse.length === 2
      && this.newItemSousClasse.length === 2
      && this.newItemCategorie.length === 2
      && this.newItemSerie.length === 2
      && /^\d{4}$/.test(this.newItemItemCode)
      && !!this.newItem.designation
      && this.newItem.designation.trim().length > 0;
  }

  /**
   * RÔLE :
   * Soumet le nouvel article au backend (nomenclature reconstituée à partir des 5 segments
   * de classification), puis enchaîne l'upload de la photo et/ou du document PDF si
   * l'utilisateur en a choisi, avant de rafraîchir la liste du catalogue.
   *
   * POURQUOI CETTE LOGIQUE :
   * - Le backend est la source de vérité pour la validation (nomenclature à 12
   *   caractères, unicité) : les erreurs qu'il renvoie (400/409) sont affichées
   *   telles quelles à l'utilisateur plutôt que dupliquées côté client.
   * - Les endpoints d'upload existants (uploadPhoto/uploadDoc) exigent l'UUID de
   *   l'article : ils ne peuvent donc être appelés qu'APRÈS la création réussie,
   *   d'où un enchaînement séquentiel de souscriptions plutôt qu'un envoi parallèle.
   * - Le catalogue est rechargé sur la page courante pour que le nouvel article
   *   apparaisse immédiatement si le tri/la recherche active le fait remonter.
   *
   * PARAMÈTRES : Aucun
   * RETOUR : void
   */
  saveNewItem(): void {
    if (!this.isNewItemValid()) return;

    const nomenclature = this.newItemClasse + this.newItemSousClasse + this.newItemCategorie + this.newItemSerie + this.newItemItemCode;
    const payload: Partial<Item> = { ...this.newItem, nomenclature };

    this.isSavingNewItem = true;
    this.itemService.createItem(payload).subscribe({
      next: (createdItem) => {
        // ÉTAPE : Upload différé de la photo puis du document, seulement maintenant que
        // l'article dispose d'un UUID persistant (requis par les endpoints d'upload).
        const finishUp = () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Article Créé ✓',
            detail: `L'article « ${payload.designation} » (${nomenclature}) a été ajouté au catalogue.`,
            life: 4000
          });
          this.displayNewItemDialog = false;
          this.isSavingNewItem = false;
          this.itemService.fetchItems(this.currentPage, this.pageSize, this.searchTerm || this.nomenclatureSearchTerm || undefined,
            this.sortField, this.sortOrder, false, this.filterType || undefined, this.selectedMagasinId ?? undefined);
        };

        const uploadDocIfAny = () => {
          if (this.newItemDocFile) {
            this.itemService.uploadDoc(createdItem.id, this.newItemDocFile).subscribe({
              next: finishUp,
              error: () => {
                this.messageService.add({ severity: 'warn', summary: 'Article créé', detail: `Article créé, mais le document n'a pas pu être ajouté (à réessayer depuis sa fiche).`, life: 6000 });
                finishUp();
              }
            });
          } else {
            finishUp();
          }
        };

        if (this.newItemPhotoFile) {
          this.itemService.uploadPhoto(createdItem.id, this.newItemPhotoFile).subscribe({
            next: uploadDocIfAny,
            error: () => {
              this.messageService.add({ severity: 'warn', summary: 'Article créé', detail: `Article créé, mais la photo n'a pas pu être ajoutée (à réessayer depuis sa fiche).`, life: 6000 });
              uploadDocIfAny();
            }
          });
        } else {
          uploadDocIfAny();
        }
      },
      error: (err: any) => {
        const errorMsg = err?.error?.error || `Impossible de créer l'article.`;
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: errorMsg, life: 5000 });
        this.isSavingNewItem = false;
      }
    });
  }
}
