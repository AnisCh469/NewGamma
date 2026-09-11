import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DistributionService, DemandeMateriel, LigneDemande, ArbitrageRequestDto } from '../../../core/services/distribution.service';
import { ReceptionService } from '../../../core/services/reception.service';
import { Magasin } from '../../../core/models/stock.model';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { TabViewModule } from 'primeng/tabview';
import { QRCodeModule } from 'angularx-qrcode';
import { PlanArmementService, DemandeDotation } from '../../../core/services/plan-armement.service';
import { AuthService } from '../../../core/auth/auth.service';
import { DocumentService } from '../../../core/services/document.service';
import { DocumentsSignesComponent } from '../../../shared/documents-signes/documents-signes.component';

@Component({
  selector: 'app-console-arbitrage',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    CardModule,
    ButtonModule,
    TableModule,
    TagModule,
    DialogModule,
    DropdownModule,
    InputTextModule,
    ToastModule,
    TabViewModule,
    QRCodeModule,
    DocumentsSignesComponent
  ],
  providers: [MessageService],
  templateUrl: './console-arbitrage.component.html',
  styleUrls: ['./console-arbitrage.component.css']
})
export class ConsoleArbitrageComponent implements OnInit {
  private distributionService = inject(DistributionService);
  private receptionService = inject(ReceptionService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  private planArmementService = inject(PlanArmementService);
  public authService = inject(AuthService);
  private documentService = inject(DocumentService);

  demandes: DemandeMateriel[] = [];
  allDemandes: DemandeMateriel[] = [];
  demandesDotation: DemandeDotation[] = [];
  allDemandesDotation: DemandeDotation[] = [];
  isFiltered: boolean = false;
  loading: boolean = true;
  loadingDotation: boolean = false;
  activeTabIndex: number = 0;
  today: Date = new Date();

  // Arbitrage state
  displayArbitrageDialog: boolean = false;
  selectedDemande: DemandeMateriel | null = null;
  lignes: any[] = [];
  magasins: Magasin[] = [];
  selectedMagasin: Magasin | null = null;
  decision: 'APPROUVE_TOTAL' | 'APPROUVE_PARTIEL' | 'REFUSE' = 'APPROUVE_TOTAL';
  motifRefus: string = '';

  // Smart recommendation state
  globalRecommendation: 'APPROVE' | 'ADJUST' | 'REFUSE' | null = null;
  recommendationLabel: string = '';
  recommendationDetail: string = '';
  recommendationApplied: boolean = false;
  autoSelectedMagasinReason: string = '';

  // Refus Dotation state
  displayRefusDotationDialog: boolean = false;
  selectedDotation: DemandeDotation | null = null;
  motifRefusDotation: string = '';

  // Printing state
  printDemande: DemandeMateriel | null = null;
  printLignes: LigneDemande[] = [];

  decisionOptions = [
    { label: 'Approuver Totalement', value: 'APPROUVE_TOTAL' },
    { label: 'Approuver Partiellement (Ajustement)', value: 'APPROUVE_PARTIEL' },
    { label: 'Refuser la demande', value: 'REFUSE' }
  ];

  ngOnInit(): void {
    this.loadDemandes();
    this.loadDemandesDotation();
    this.loadMagasins();

    this.route.queryParams.subscribe(params => {
      if (params['tab']) {
        const tab = params['tab'];
        if (tab === 'dotation' || tab === 'dotations') {
          this.activeTabIndex = 1;
        } else {
          this.activeTabIndex = 0;
        }
      }
      if (this.allDemandes.length > 0) {
        this.applyFilter();
      }
      if (this.allDemandesDotation.length > 0) {
        this.applyDotationFilter();
      }
    });
  }

  loadDemandes(): void {
    this.loading = true;
    this.distributionService.getDemandes().subscribe({
      next: (list) => {
        this.allDemandes = list;
        this.applyFilter();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  loadDemandesDotation(): void {
    this.loadingDotation = true;
    this.planArmementService.getDemandesDotation().subscribe({
      next: (list) => {
        this.allDemandesDotation = list;
        this.applyDotationFilter();
        this.loadingDotation = false;
      },
      error: () => {
        this.loadingDotation = false;
      }
    });
  }

  applyFilter(): void {
    const params = this.route.snapshot.queryParams;
    if (params['uniteId']) {
      const uId = parseInt(params['uniteId'], 10);
      this.demandes = this.allDemandes.filter(d => d.unite?.id === uId);
      this.isFiltered = true;
    } else {
      this.demandes = this.allDemandes;
      this.isFiltered = false;
    }
  }

  applyDotationFilter(): void {
    const params = this.route.snapshot.queryParams;
    if (params['uniteId']) {
      const uId = parseInt(params['uniteId'], 10);
      this.demandesDotation = this.allDemandesDotation.filter(d => d.unite?.id === uId);
      this.isFiltered = true;
    } else {
      this.demandesDotation = this.allDemandesDotation;
    }
  }

  clearFilter(): void {
    this.router.navigate([], { queryParams: { uniteId: null }, queryParamsHandling: 'merge' }).then(() => {
      this.applyFilter();
      this.applyDotationFilter();
    });
  }

  approuverDotation(dot: DemandeDotation): void {
    this.planArmementService.approuverDemandeDotation(dot.id!).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Dotation Approuvée',
          detail: `La demande pour l'article ${dot.item.designation} a été approuvée.`
        });
        this.loadDemandesDotation();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur',
          detail: 'Impossible d\'approuver la demande.'
        });
      }
    });
  }

  openRefusDotationDialog(dot: DemandeDotation): void {
    this.selectedDotation = dot;
    this.motifRefusDotation = '';
    this.displayRefusDotationDialog = true;
  }

  saveRefusDotation(): void {
    if (!this.selectedDotation || !this.motifRefusDotation) return;

    this.planArmementService.refuserDemandeDotation(this.selectedDotation.id!, this.motifRefusDotation).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'warn',
          summary: 'Dotation Rejetée',
          detail: `La demande pour l'article ${this.selectedDotation!.item.designation} a été rejetée.`
        });
        this.displayRefusDotationDialog = false;
        this.loadDemandesDotation();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur',
          detail: 'Impossible de rejeter la demande.'
        });
      }
    });
  }

  loadMagasins(): void {
    this.receptionService.getMagasins().subscribe({
      next: (list) => {
        this.magasins = list;
      }
    });
  }

  openArbitrageModal(dem: DemandeMateriel): void {
    console.log('Arbitrage modal requested for demand:', dem);
    this.selectedDemande = dem;
    this.decision = dem.statut === 'SOUMIS' ? 'APPROUVE_TOTAL' : (dem.statut as any);
    this.motifRefus = dem.motifRefus || '';
    this.selectedMagasin = null;
    this.lignes = [];

    this.distributionService.getLignesDetailleesByDemande(dem.id!).subscribe({
      next: (list) => {
        console.log('Successfully loaded detailed lines:', list);
        this.lignes = list.map(l => ({
          ...l,
          quantiteAccordee: dem.statut === 'SOUMIS' ? l.quantiteDemandee : l.quantiteAccordee
        }));
        // Calcul de la recommandation intelligente
        if (dem.statut === 'SOUMIS') {
          this.computeGlobalRecommendation();
          this.selectOptimalMagasin();
        } else {
          this.globalRecommendation = null;
          this.autoSelectedMagasinReason = '';
        }
        this.displayArbitrageDialog = true;
      },
      error: (err) => {
        console.error('Failed to load detailed lines for demand:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur de chargement',
          detail: 'Impossible de charger les stocks et prévisions pour cette demande.'
        });
      }
    });
  }

  onDecisionChange(): void {
    if (this.decision === 'APPROUVE_TOTAL' && this.lignes.length > 0) {
      for (let l of this.lignes) {
        l.quantiteAccordee = l.quantiteDemandee;
      }
    }
  }

  saveArbitrage(): void {
    if (!this.selectedDemande) return;

    if (this.decision === 'REFUSE' && !this.motifRefus) {
      this.messageService.add({severity: 'error', summary: 'Erreur', detail: 'Veuillez renseigner le motif de refus.'});
      return;
    }

    if (this.decision !== 'REFUSE' && !this.selectedMagasin) {
      this.messageService.add({severity: 'error', summary: 'Erreur', detail: 'Veuillez sélectionner la soute de prélèvement DA.'});
      return;
    }

    // Validation quantite accordee for partial
    if (this.decision === 'APPROUVE_PARTIEL') {
      for (let l of this.lignes) {
        if (l.quantiteAccordee == null || l.quantiteAccordee < 0 || l.quantiteAccordee > l.quantiteDemandee) {
          this.messageService.add({
            severity: 'error', 
            summary: 'Erreur', 
            detail: `La quantité accordée pour ${l.item.designation} doit être comprise entre 0 et la quantité demandée (${l.quantiteDemandee}).`
          });
          return;
        }
      }
    }

    const payload: ArbitrageRequestDto = {
      decision: this.decision,
      motifRefus: this.decision === 'REFUSE' ? this.motifRefus : undefined,
      magasinId: this.decision !== 'REFUSE' ? this.selectedMagasin!.id : undefined,
      lignes: this.lignes.map(l => ({
        id: l.id,
        item: { id: l.item.id } as any,
        quantiteDemandee: l.quantiteDemandee,
        quantiteAccordee: this.decision === 'APPROUVE_TOTAL' ? l.quantiteDemandee : l.quantiteAccordee
      }))
    };

    this.distributionService.arbitrerDemande(this.selectedDemande.id!, payload).subscribe({
      next: (res) => {
        const severity = this.decision === 'REFUSE' ? 'warn' : 'success';
        const summary = this.decision === 'REFUSE' ? 'Demande Refusée' : 'Demande Approuvée';
        const detail = this.decision === 'REFUSE' 
          ? `La demande ${this.selectedDemande!.numeroDemande} a été rejetée.`
          : `Le Bon de Sortie a été créé et les quantités ont été réservées. Le retrait physique doit être confirmé pour effectuer la sortie de stock.`;

        this.messageService.add({ severity, summary, detail, life: 5000 });
        this.displayArbitrageDialog = false;
        this.loadDemandes();
      },
      error: (err) => {
        const errorMsg = typeof err.error === 'string' ? err.error : 'Impossible de soumettre l\'arbitrage.';
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: errorMsg });
      }
    });
  }

  /**
   * Ouvre le Bon de Sortie officiel en PDF (généré par le serveur, gabarit institutionnel).
   * Remplace l'ancienne impression navigateur (window.print) — voir docs/AUDIT_IMPRESSION.md.
   */
  declencherImpression(dem: DemandeMateriel): void {
    this.documentService.ouvrir('bon-sortie/' + dem.id);
  }

  getStatutSeverity(statut: string | undefined): 'success' | 'secondary' | 'info' | 'warning' | 'danger' | 'contrast' | undefined {
    switch (statut) {
      case 'SOUMIS': return 'warning';
      case 'EN_COURS_ARBITRAGE': return 'warning';
      case 'ESCALADE_SGS': return 'danger';
      case 'APPROUVE_TOTAL': return 'success';
      case 'APPROUVE_PARTIEL': return 'info';
      case 'REFUSE': return 'danger';
      case 'LIVRE': return 'success';
      default: return 'info';
    }
  }

  getStatutLabel(statut: string | undefined): string {
    switch (statut) {
      case 'SOUMIS': return 'EN ATTENTE D\'ARBITRAGE';
      case 'EN_COURS_ARBITRAGE': return 'ARBITRAGE EN COURS';
      case 'ESCALADE_SGS': return 'ESCALADÉE — TRANSFERT SGS';
      case 'APPROUVE_TOTAL': return 'APPROUVÉ TOTALEMENT';
      case 'APPROUVE_PARTIEL': return 'APPROUVÉ PARTIELLEMENT';
      case 'REFUSE': return 'REFUSÉ';
      case 'LIVRE': return 'MATÉRIEL REMIS';
      default: return 'STATUT INCONNU';
    }
  }

  getLigneTotal(l: LigneDemande): number {
    const qty = l.quantiteAccordee != null ? l.quantiteAccordee : l.quantiteDemandee;
    return qty * (l.item.prixUnitaire || 0);
  }

  getGlobalTotalPrint(): number {
    return this.printLignes.reduce((sum, l) => sum + this.getLigneTotal(l), 0);
  }

  // --- SPRINT 13 ADVANCED DECISION METRICS ---
  getItemStockInSelectedMagasin(l: any): number {
    if (!this.selectedMagasin || !l.stocksParMagasin) return 0;
    const stock = l.stocksParMagasin.find((s: any) => s.magasinId === this.selectedMagasin!.id);
    return stock ? stock.quantite : 0;
  }

  isStockCriticalInSelectedMagasin(l: any): boolean {
    const stock = this.getItemStockInSelectedMagasin(l);
    return stock < (l.stockSecurite || 0);
  }

  getLogisticsRecommendation(l: any): string {
    const stock = this.getItemStockInSelectedMagasin(l);
    const critical = l.stockSecurite || 0;
    
    if (stock <= 0) {
      return `❌ RUPTURE : Stock épuisé dans cette soute.`;
    }
    if (stock < l.quantiteDemandee) {
      return `⚠️ RATIONNEMENT : Stock insuffisant (${stock} u).`;
    }
    if (stock - l.quantiteDemandee < critical) {
      return `⚠️ ALERTE SEUIL : Distribution critique (Seuil sécu : ${critical} u).`;
    }
    return `✅ APPROBATION RECOMMANDÉE : Stock nominal.`;
  }

  // ===== RECOMMANDATIONS INTELLIGENTES =====

  /**
   * Calcule la recommandation globale basée sur le stock total de tous les magasins.
   * La recommandation est préliminaire — l'opérateur DOIT confirmer.
   */
  computeGlobalRecommendation(): void {
    this.recommendationApplied = false;

    if (!this.lignes || this.lignes.length === 0) {
      this.globalRecommendation = null;
      return;
    }

    let hasRupture = false;
    let hasInsuffisant = false;

    for (const l of this.lignes) {
      // Stock total dans tous les magasins
      const stockTotal = (l.stocksParMagasin || []).reduce((sum: number, s: any) => sum + s.quantite, 0);

      if (stockTotal <= 0) {
        hasRupture = true;
      } else if (stockTotal < l.quantiteDemandee) {
        hasInsuffisant = true;
        // Pré-remplir la quantité accordée avec ce qui est disponible
        l.quantiteAccordee = stockTotal;
      }
    }

    if (hasRupture) {
      this.globalRecommendation = 'REFUSE';
      this.recommendationLabel = '❌ Refus Suggéré';
      this.recommendationDetail = 'Une ou plusieurs lignes sont en rupture totale de stock dans toutes les soutes DA. Le refus est la décision logistiquement correcte. Vous pouvez néanmoins modifier cette décision.';
    } else if (hasInsuffisant) {
      this.globalRecommendation = 'ADJUST';
      this.recommendationLabel = '⚠️ Ajustement Partiel Suggéré';
      this.recommendationDetail = 'Stock insuffisant pour honorer certaines lignes en totalité. Les quantités accordées ont été pré-calculées selon le stock disponible. Vérifiez et ajustez avant de valider.';
    } else {
      this.globalRecommendation = 'APPROVE';
      this.recommendationLabel = '✅ Approbation Totale Recommandée';
      this.recommendationDetail = 'Le stock disponible est suffisant pour honorer l\'intégralité de la demande. Sélectionnez une soute et approuvez.';
    }
  }

  /**
   * Applique la recommandation intelligente à la décision courante.
   * L'opérateur doit toujours confirmer manuellement.
   */
  applyRecommendation(): void {
    if (!this.globalRecommendation) return;

    switch (this.globalRecommendation) {
      case 'REFUSE':
        this.decision = 'REFUSE';
        this.motifRefus = 'Rupture de stock dans les soutes DA — Demande à renouveler après réapprovisionnement.';
        break;
      case 'ADJUST':
        this.decision = 'APPROUVE_PARTIEL';
        // Les quantités accordées ont déjà été pré-remplies dans computeGlobalRecommendation
        break;
      case 'APPROVE':
        this.decision = 'APPROUVE_TOTAL';
        this.onDecisionChange();
        break;
    }
    this.recommendationApplied = true;
    this.messageService.add({
      severity: 'info',
      summary: 'Recommandation Appliquée',
      detail: 'La décision a été pré-remplie selon l\'analyse du stock. Vérifiez et confirmez manuellement.',
      life: 4000
    });
  }

  getRecommendationClass(): string {
    switch (this.globalRecommendation) {
      case 'REFUSE': return 'bg-red-50 border-red-200 text-red-800';
      case 'ADJUST': return 'bg-amber-50 border-amber-200 text-amber-800';
      case 'APPROVE': return 'bg-emerald-50 border-emerald-200 text-emerald-800';
      default: return 'bg-slate-50 border-slate-200 text-slate-700';
    }
  }

  getRecommendationIconClass(): string {
    switch (this.globalRecommendation) {
      case 'REFUSE': return 'pi pi-times-circle text-red-500';
      case 'ADJUST': return 'pi pi-exclamation-triangle text-amber-500';
      case 'APPROVE': return 'pi pi-check-circle text-emerald-500';
      default: return 'pi pi-info-circle text-slate-400';
    }
  }

  getRecommendationButtonClass(): string {
    switch (this.globalRecommendation) {
      case 'REFUSE': return 'p-button-danger p-button-sm font-bold';
      case 'ADJUST': return 'p-button-warning p-button-sm font-bold';
      case 'APPROVE': return 'p-button-success p-button-sm font-bold';
      default: return 'p-button-secondary p-button-sm';
    }
  }

  confirmerLivraisonPhysique(dem: DemandeMateriel): void {
    if (!dem.bonSortie || !dem.bonSortie.id) {
      this.messageService.add({
        severity: 'error',
        summary: 'Erreur',
        detail: 'Aucun Bon de Sortie associé à cette demande.'
      });
      return;
    }

    if (confirm(`Confirmer la récupération physique des matériels pour la demande ${dem.numeroDemande} ? Cette action effectuera le prélèvement réel du stock des soutes DA.`)) {
      this.distributionService.confirmerLivraison(dem.bonSortie.id).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Sortie Confirmée',
            detail: `La livraison physique pour le Bon de Sortie ${dem.bonSortie?.numeroBs} a été confirmée.`
          });
          this.loadDemandes();
        },
        error: (err) => {
          const errorMsg = typeof err.error === 'string' ? err.error : 'Impossible de confirmer la livraison physique.';
          this.messageService.add({
            severity: 'error',
            summary: 'Erreur',
            detail: errorMsg
          });
        }
      });
    }
  }

  // ─── Escalade vers SGS (stock local insuffisant) ────────────────────────────
  // Le DA_MANAGER dont la soute ne peut pas couvrir la demande la remonte à l'ADMIN (SGS),
  // qui décidera d'un transfert inter-magasin. Voir DistributionService.escalerDemande.
  displayEscaladeDialog = false;
  displayTransfertDialog = false;
  demandeEscalade: DemandeMateriel | null = null;
  motifEscalade = '';
  motifRefusTransfert = '';

  /** Vrai si l'utilisateur courant peut jouer le rôle de magasinier pour ce bon de sortie :
   *  le DA_MANAGER pour sa soute, ou l'ADMIN pour la soute SGS (aucun DA_MANAGER n'y étant
   *  rattaché — aligné sur le contrôle backend de confirmerSortiePhysique). */
  peutConfirmerRetrait(dem: DemandeMateriel): boolean {
    const role = this.authService.currentUser()?.role;
    if (role === 'DA_MANAGER') return true;
    if (role === 'ADMIN' || role === 'SUPER_ADMIN') return dem.magasinDesigne?.code === 'SGS';
    return false;
  }

  estDaManager(): boolean {
    return this.authService.currentUser()?.role === 'DA_MANAGER';
  }
  /** ADMIN (administrateur logistique SGS) ou SUPER_ADMIN (superset — voir gouvernance des rôles). */
  estAdmin(): boolean {
    const role = this.authService.currentUser()?.role;
    return role === 'ADMIN' || role === 'SUPER_ADMIN';
  }

  openEscaladeDialog(dem: DemandeMateriel): void {
    this.demandeEscalade = dem;
    this.motifEscalade = '';
    this.displayEscaladeDialog = true;
  }

  confirmerEscalade(): void {
    if (!this.demandeEscalade?.id || !this.motifEscalade.trim()) {
      this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Motif d\'escalade obligatoire.' });
      return;
    }
    this.distributionService.escaladeDemande(this.demandeEscalade.id, this.motifEscalade.trim()).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Demande escaladée', detail: 'La demande a été remontée à SGS pour validation d\'un transfert inter-magasin.' });
        this.displayEscaladeDialog = false;
        this.loadDemandes();
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: typeof err.error === 'string' ? err.error : 'Échec de l\'escalade.' });
      }
    });
  }

  openTransfertDialog(dem: DemandeMateriel): void {
    this.demandeEscalade = dem;
    this.motifRefusTransfert = '';
    this.displayTransfertDialog = true;
  }

  validerTransfert(accepter: boolean): void {
    if (!this.demandeEscalade?.id) return;
    if (!accepter && !this.motifRefusTransfert.trim()) {
      this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Motif de refus obligatoire.' });
      return;
    }
    this.distributionService.validerTransfert(this.demandeEscalade.id, accepter, accepter ? undefined : this.motifRefusTransfert.trim()).subscribe({
      next: () => {
        this.messageService.add({
          severity: accepter ? 'success' : 'warn',
          summary: accepter ? 'Transfert validé' : 'Transfert refusé',
          detail: accepter
            ? 'Le stock a été transféré depuis SGS vers la soute locale. Bon de sortie généré.'
            : 'Le transfert a été refusé, la demande est rejetée avec motif.'
        });
        this.displayTransfertDialog = false;
        this.loadDemandes();
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: typeof err.error === 'string' ? err.error : 'Échec de la validation du transfert.' });
      }
    });
  }

  selectOptimalMagasin(): void {
    if (!this.selectedDemande || !this.magasins || this.magasins.length === 0 || !this.lignes || this.lignes.length === 0) {
      this.autoSelectedMagasinReason = '';
      return;
    }

    const base = (this.selectedDemande.unite?.baseNavale || '').toLowerCase();
    let bestMagasin: Magasin | null = null;
    let bestScore = -999999;
    let bestReason = '';

    for (const m of this.magasins) {
      // Skip soutes that are specialized like Magasin Mécanique unless necessary
      if (m.code === 'M_MEC') continue;

      let score = 0;
      let proximityScore = 5;
      let proximityReason = 'Magasin par défaut';

      // 1. Proximity score
      const isSudClient = base.includes('sfax') || base.includes('sud');
      const isNordClient = base.includes('bizerte') || base.includes('kélibia') || base.includes('centre') || base.includes('nord') || base.includes('un');

      const isSudMagasin = m.code === 'SM2' || m.nom.toLowerCase().includes('sud');
      const isCentreMagasin = m.code === 'SM1' || m.nom.toLowerCase().includes('centre');
      const isCentralMagasin = m.code === 'SGS' || m.nom.toLowerCase().includes('central') || m.nom.toLowerCase().includes('gestion') || m.nom.toLowerCase().includes('stocks');

      if (isSudClient) {
        if (isSudMagasin) {
          proximityScore = 15;
          proximityReason = 'Zone Sud (Sfax) - optimal pour le retrait client';
        } else if (isCentreMagasin) {
          proximityScore = 8;
          proximityReason = 'Zone Centre - retrait secondaire possible';
        } else {
          proximityScore = 3;
          proximityReason = 'Bâtiment Central (Nord) - éloignement géographique important';
        }
      } else if (isNordClient) {
        if (isCentreMagasin) {
          proximityScore = 15;
          proximityReason = 'Zone Centre - optimal pour le retrait client';
        } else if (isCentralMagasin) {
          proximityScore = 12;
          proximityReason = 'Bâtiment Central (DA Bizerte) - retrait central direct';
        } else if (isSudMagasin) {
          proximityScore = 2;
          proximityReason = 'Zone Sud (Sfax) - éloignement géographique important';
        }
      }

      // 2. Stock load balancing and availability score
      let stockScore = 0;
      let stockReason = 'Stock suffisant';
      let hasZeroStock = false;
      let hasInsufficientStock = false;
      let underSafetyStockCount = 0;

      for (const l of this.lignes) {
        const stock = (l.stocksParMagasin || []).find((s: any) => s.magasinId === m.id);
        const qtyInStock = stock ? stock.quantite : 0;

        if (qtyInStock <= 0) {
          hasZeroStock = true;
        } else if (qtyInStock < l.quantiteDemandee) {
          hasInsufficientStock = true;
        } else {
          const remaining = qtyInStock - l.quantiteDemandee;
          const safety = l.stockSecurite || 0;
          if (remaining < safety) {
            underSafetyStockCount++;
          }
          // Add load balancing factor (ratio of remaining stock to safety stock or base factor)
          const balanceFactor = remaining / (safety || 1);
          stockScore += balanceFactor * 2; // Favor warehouses with large relative surplus to prevent draining
        }
      }

      if (hasZeroStock) {
        stockScore -= 500; // Heavy penalty if any item is out of stock in this soute
        stockReason = 'Rupture totale sur certains matériels';
      } else if (hasInsufficientStock) {
        stockScore -= 200; // Penalty if stock is insufficient
        stockReason = 'Stock insuffisant pour couvrir la demande entière';
      } else if (underSafetyStockCount > 0) {
        stockScore -= 30 * underSafetyStockCount; // Penalty if it drops under safety stock
        stockReason = 'Alerte : Fait chuter le stock sous le seuil de sécurité';
      } else {
        stockReason = 'Niveaux de stock nominaux & équilibrés (Anti-drainage)';
      }

      // Combine scores (Proximity is weighted highly, but stock availability is critical)
      const totalScore = proximityScore * 8 + stockScore;

      if (totalScore > bestScore) {
        bestScore = totalScore;
        bestMagasin = m;
        bestReason = `${proximityReason} et ${stockReason}`;
      }
    }

    if (bestMagasin && bestScore > -200) { // Don't pre-select if all options are in total rupture
      this.selectedMagasin = bestMagasin;
      this.autoSelectedMagasinReason = `${bestMagasin.nom} (${bestMagasin.code}) — Choisi pour : ${bestReason}.`;
    } else {
      this.selectedMagasin = null;
      this.autoSelectedMagasinReason = 'Impossible d\'auto-sélectionner : Rupture de stock généralisée sur les soutes DA.';
    }
  }
}
