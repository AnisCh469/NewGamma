import { Component, OnInit, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { UniteService, UniteUtilisatrice } from '../../../../core/services/unite.service';
import { CardModule } from 'primeng/card';
import { PanelModule } from 'primeng/panel';
import { DividerModule } from 'primeng/divider';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { FileUploadModule } from 'primeng/fileupload';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { QRCodeModule } from 'angularx-qrcode';
import { DialogModule } from 'primeng/dialog';
import { TabViewModule } from 'primeng/tabview';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import * as JsBarcode from 'jsbarcode';
import { HttpClient } from '@angular/common/http';
import { DropdownModule } from 'primeng/dropdown';
import { PlanArmementService, PlanArmement, DemandeDotation } from '../../../../core/services/plan-armement.service';
import { AuthService } from '../../../../core/auth/auth.service';
import { DistributionService, DemandeMateriel, LigneDemande } from '../../../../core/services/distribution.service';
import { AnalyticsService } from '../../../../core/services/analytics.service';
import { ReformeService, DossierReforme, LigneReforme } from '../../../../core/services/reforme.service';

import { environment } from '../../../../../environments/environment';
@Component({
  selector: 'app-unite-detail',
  standalone: true,
  imports: [
    CommonModule, 
    RouterModule, 
    FormsModule, 
    CardModule, 
    PanelModule, 
    DividerModule, 
    ButtonModule, 
    TagModule, 
    FileUploadModule, 
    ToastModule, 
    QRCodeModule, 
    DialogModule, 
    TabViewModule,
    InputTextModule,
    TableModule,
    DropdownModule
  ],
  providers: [MessageService],
  templateUrl: './unite-detail.component.html',
  styleUrls: ['./unite-detail.component.css']
})
export class UniteDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private uniteService = inject(UniteService);
  private messageService = inject(MessageService);
  private planArmementService = inject(PlanArmementService);
  private http = inject(HttpClient);
  authService = inject(AuthService);
  private distributionService = inject(DistributionService);
  private analyticsService = inject(AnalyticsService);
  private reformeService = inject(ReformeService);

  activeTabIndex: number = 0;

  dossiersReforme: DossierReforme[] = [];
  loadingReforme: boolean = true;
  displayReformeDialog: boolean = false;
  selectedPlanForReforme: PlanArmement | null = null;
  quantiteAReformer: number = 1;
  motifReforme: string = '';

  // Impression Certificat
  printDossierReforme: DossierReforme | null = null;
  printLignesReforme: LigneReforme[] = [];

  // Configuration Double Facteur (2FA)
  setup2FaMode: boolean = false;
  secret2fa: string = '';
  otpauthUrl: string = '';
  verificationCode2fa: string = '';

  @ViewChild('dtPlans') dtPlansTable: any;

  onPlansSearch(event: any): void {
    const value = event.target.value;
    if (this.dtPlansTable) {
      this.dtPlansTable.filterGlobal(value, 'contains');
    }
  }

  isClient(): boolean {
    return this.authService.currentUser()?.role === 'UNIT_USER';
  }

  unite: UniteUtilisatrice | null = null;
  loading: boolean = true;
  error: string | null = null;
  today: Date = new Date();

  displayDialog: boolean = false;
  selectedUnite: UniteUtilisatrice = { code: '', nom: '' };

  plans: PlanArmement[] = [];
  loadingPlans: boolean = true;

  displayPlanDialog: boolean = false;
  selectedPlan: PlanArmement = {
    unite: { code: '', nom: '' },
    item: { id: '', nomenclature: '', designation: '', prixUnitaire: 0, stockSecurite: 0, quantiteTotale: 0, classeCode: '', sousClasseCode: '', categorieCode: '', serieCode: '', itemCode: '' },
    quantiteType: 0,
    quantiteReelle: 0,
    quantiteVirtuelle: 0
  };
  isNewPlan: boolean = false;

  allItems: any[] = [];
  selectedItemForPlan: any = null;

  getFullUrl(url: string | undefined): string {
    if (!url) return '';
    if (url.startsWith('http')) return url;
    return `${environment.apiUrl}${url}`;
  }

  ngOnInit(): void {
    this.loadUnite();

    this.route.queryParams.subscribe(params => {
      if (params['tab']) {
        const tab = params['tab'];
        if (tab === 'dotations' || tab === 'dotation') {
          this.activeTabIndex = 0;
        } else if (tab === 'materiels' || tab === 'materiel') {
          this.activeTabIndex = 1;
        } else if (tab === 'consommations' || tab === 'consommation' || tab === 'bilan') {
          this.activeTabIndex = 2;
        } else if (tab === 'documents' || tab === 'document') {
          this.activeTabIndex = 3;
        } else if (tab === 'reformes' || tab === 'reforme') {
          this.activeTabIndex = 4;
        } else {
          const tabIndex = parseInt(tab, 10);
          if (!isNaN(tabIndex) && tabIndex >= 0 && tabIndex <= 4) {
            this.activeTabIndex = tabIndex;
          }
        }
      }
    });

    // Cleanup print classes after print dialog closes
    window.addEventListener('afterprint', () => {
      document.body.classList.remove('print-document-mode');
      document.body.classList.remove('print-label-mode');
    });
  }

  loadUnite(): void {
    const idStr = this.route.snapshot.paramMap.get('id');
    if (idStr) {
      const id = parseInt(idStr, 10);
      this.uniteService.getUnite(id).subscribe({
        next: (data) => {
          this.unite = data;
          this.loading = false;
          this.renderBarcode();
          this.loadPlanArmement();
          this.loadDemandes();
          this.loadDemandesDotation();
          this.loadDossiersReforme();
        },
        error: (err) => {
          this.error = 'Impossible de charger les détails de l\'unité.';
          this.loading = false;
          console.error(err);
        }
      });
    } else {
      this.error = 'Identifiant introuvable.';
      this.loading = false;
    }
  }

  renderBarcode(): void {
    setTimeout(() => {
      if (this.unite) {
        try {
          const renderBarcode = (JsBarcode as any).default || JsBarcode;
          const svgElements = document.querySelectorAll('.barcode-svg');
          svgElements.forEach(el => {
            renderBarcode(el, this.unite!.code, {
              format: "CODE128",
              lineColor: "#000",
              width: 2,
              height: 45,
              displayValue: true,
              fontSize: 12
            });
          });
        } catch (e) {
          console.error('Erreur JsBarcode', e);
        }
      }
    }, 150);
  }

  onLogoUpload(event: any): void {
    if (this.unite && event.files && event.files.length > 0) {
      const file = event.files[0];
      this.uniteService.uploadLogo(this.unite.id!, file).subscribe({
        next: (res) => {
          if (res.logoUrl) {
            res.logoUrl = res.logoUrl + '?t=' + new Date().getTime();
          }
          this.unite = res;
          this.messageService.add({
            severity: 'success', 
            summary: 'Succès', 
            detail: 'Insigne mis à jour avec succès'
          });
        },
        error: (err) => {
          console.error('Erreur lors de l\'upload du logo:', err);
          this.messageService.add({
            severity: 'error', 
            summary: 'Erreur', 
            detail: 'Impossible de mettre à jour l\'insigne'
          });
        }
      });
    }
  }

  editUnite(): void {
    if (this.unite) {
      this.selectedUnite = { ...this.unite };
      this.displayDialog = true;
    }
  }

  saveUnite(): void {
    if (this.selectedUnite.id) {
      this.uniteService.updateUnite(this.selectedUnite.id, this.selectedUnite).subscribe({
        next: (updated) => {
          this.unite = updated;
          this.displayDialog = false;
          this.messageService.add({
            severity: 'success',
            summary: 'Succès',
            detail: 'Unité mise à jour'
          });
          this.renderBarcode();
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Erreur',
            detail: 'Impossible d\'enregistrer les modifications'
          });
        }
      });
    }
  }

  deleteUnite(): void {
    if (this.unite && confirm('Voulez-vous vraiment supprimer cette unité ?')) {
      this.uniteService.deleteUnite(this.unite.id!).subscribe({
        next: () => {
          this.router.navigate(['/unites']);
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Erreur',
            detail: 'Impossible de supprimer l\'unité'
          });
        }
      });
    }
  }

  printPage(): void {
    document.body.classList.add('print-document-mode');
    setTimeout(() => {
      window.print();
    }, 100);
  }

  printLabel(): void {
    document.body.classList.add('print-label-mode');
    setTimeout(() => {
      window.print();
    }, 100);
  }

  loadPlanArmement(): void {
    if (!this.unite || !this.unite.code) return;
    this.loadingPlans = true;
    this.planArmementService.getPlansByUniteCode(this.unite.code).subscribe({
      next: (list) => {
        this.plans = list;
        this.loadingPlans = false;
      },
      error: () => {
        this.loadingPlans = false;
      }
    });
  }

  openNewPlanDialog(): void {
    this.isNewPlan = true;
    this.selectedPlan = {
      unite: this.unite!,
      item: { id: '', nomenclature: '', designation: '', prixUnitaire: 0, stockSecurite: 0, quantiteTotale: 0, classeCode: '', sousClasseCode: '', categorieCode: '', serieCode: '', itemCode: '' },
      quantiteType: 0,
      quantiteReelle: 0,
      quantiteVirtuelle: 0
    };
    this.selectedItemForPlan = null;
    
    this.http.get<any>(`${environment.apiUrl}/api/v1/items?size=100`).subscribe({
      next: (res: any) => {
        this.allItems = res.content || [];
        this.displayPlanDialog = true;
      }
    });
  }

  editPlan(p: PlanArmement): void {
    this.isNewPlan = false;
    this.selectedPlan = { ...p };
    this.displayPlanDialog = true;
  }

  savePlan(): void {
    if (this.isNewPlan) {
      if (!this.selectedItemForPlan) return;
      this.selectedPlan.item = this.selectedItemForPlan;
      this.planArmementService.createPlan(this.selectedPlan).subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Dotation ajoutée' });
          this.displayPlanDialog = false;
          this.loadPlanArmement();
        },
        error: () => {
          this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible d\'ajouter la dotation' });
        }
      });
    } else {
      if (!this.selectedPlan.id) return;
      this.planArmementService.updatePlan(this.selectedPlan.id, this.selectedPlan).subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Dotation mise à jour' });
          this.displayPlanDialog = false;
          this.loadPlanArmement();
        },
        error: () => {
          this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de mettre à jour' });
        }
      });
    }
  }

  deletePlan(p: PlanArmement): void {
    if (!p.id || !confirm('Voulez-vous supprimer cet article de la dotation ?')) return;
    this.planArmementService.deletePlan(p.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Dotation retirée' });
        this.loadPlanArmement();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de supprimer la dotation' });
      }
    });
  }

  // --- SPRINT 12: DISTRIBUTION & DEMANDES ---
  demandes: DemandeMateriel[] = [];
  loadingDemandes: boolean = true;
  displayDemandeDialog: boolean = false;
  newDemande: any = {
    numeroDemande: '',
    dateDemande: new Date().toISOString().split('T')[0],
    unite: null
  };
  selectedPlanForDemande: PlanArmement | null = null;
  quantiteDemandee: number = 1;

  // Cart / Panier logic
  cart: { plan: PlanArmement; quantiteDemandee: number }[] = [];
  displayCartDialog: boolean = false;

  addToCart(plan: PlanArmement): void {
    const existing = this.cart.find(c => c.plan.id === plan.id);
    const remaining = this.getRemainingDotation(plan);
    
    if (remaining <= 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Dotation Atteinte',
        detail: `La dotation réglementaire maximale est déjà allouée pour ${plan.item.designation}.`
      });
      return;
    }

    if (existing) {
      if (existing.quantiteDemandee < remaining) {
        existing.quantiteDemandee += 1;
        this.messageService.add({
          severity: 'success',
          summary: 'Panier Mis à Jour',
          detail: `Quantité demandée passée à ${existing.quantiteDemandee} pour ${plan.item.designation}.`
        });
      } else {
        this.messageService.add({
          severity: 'error',
          summary: 'Limite Dépassée',
          detail: `Impossible de dépasser la dotation restante de ${remaining} unité(s).`
        });
      }
    } else {
      this.cart.push({ plan, quantiteDemandee: 1 });
      this.messageService.add({
        severity: 'success',
        summary: 'Ajouté au Panier',
        detail: `L'article ${plan.item.designation} a été ajouté au panier.`
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
    return this.cart.reduce((sum, item) => sum + item.quantiteDemandee, 0);
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
    if (this.cart.length === 0) return;

    // Validation des limites
    for (const c of this.cart) {
      const remaining = this.getRemainingDotation(c.plan);
      if (c.quantiteDemandee <= 0 || c.quantiteDemandee > remaining) {
        this.messageService.add({
          severity: 'error',
          summary: 'Limite Dépassée',
          detail: `La quantité pour ${c.plan.item.designation} doit être comprise entre 1 et ${remaining}.`
        });
        return;
      }
    }

    const payload = {
      demande: {
        numeroDemande: 'DEM-' + new Date().getFullYear() + '-' + Math.floor(1000 + Math.random() * 9000),
        dateDemande: new Date().toISOString().split('T')[0],
        unite: { id: this.unite!.id } as any
      },
      lignes: this.cart.map(c => ({
        item: { id: c.plan.item.id } as any,
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
        this.loadDemandes();
        this.loadPlanArmement();
      },
      error: (err: any) => {
        const errorMsg = typeof err.error === 'string' ? err.error : 'Impossible de soumettre la demande.';
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: errorMsg });
      }
    });
  }

  loadDemandes(): void {
    if (!this.unite || !this.unite.id) return;
    this.loadingDemandes = true;
    this.distributionService.getDemandesByUnite(this.unite.id).subscribe({
      next: (list) => {
        this.demandes = list;
        this.loadingDemandes = false;
      },
      error: () => {
        this.loadingDemandes = false;
      }
    });
  }

  openNewDemandeDialog(): void {
    this.newDemande = {
      numeroDemande: 'DEM-' + new Date().getFullYear() + '-' + Math.floor(1000 + Math.random() * 9000),
      dateDemande: new Date().toISOString().split('T')[0],
      unite: this.unite!
    };
    this.selectedPlanForDemande = null;
    this.quantiteDemandee = 1;
    this.displayDemandeDialog = true;
  }

  saveNewDemande(): void {
    if (!this.selectedPlanForDemande) return;
    const remaining = this.getRemainingDotation(this.selectedPlanForDemande);
    if (this.quantiteDemandee > remaining) {
      this.messageService.add({
        severity: 'error',
        summary: 'Limite Dépassée',
        detail: `La quantité demandée dépasse le maximum autorisé (${remaining}).`
      });
      return;
    }

    const payload = {
      demande: {
        numeroDemande: this.newDemande.numeroDemande,
        dateDemande: this.newDemande.dateDemande,
        unite: { id: this.unite!.id } as any
      },
      lignes: [{
        item: { id: this.selectedPlanForDemande.item.id } as any,
        quantiteDemandee: this.quantiteDemandee
      }]
    };

    this.distributionService.createDemande(payload).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Demande soumise à la DA' });
        this.displayDemandeDialog = false;
        this.loadDemandes();
        this.loadPlanArmement(); // Reload dotation numbers since virtual quantity increased!
      },
      error: (err: any) => {
        const errorMsg = typeof err.error === 'string' ? err.error : 'Impossible de créer la demande.';
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: errorMsg });
      }
    });
  }

  getStatutDemandeSeverity(statut: string | undefined): 'success' | 'secondary' | 'info' | 'warning' | 'danger' | 'contrast' | undefined {
    switch (statut) {
      case 'SOUMIS': return 'warning';
      case 'APPROUVE_TOTAL': return 'success';
      case 'APPROUVE_PARTIEL': return 'info';
      case 'REFUSE': return 'danger';
      default: return 'info';
    }
  }

  getRemainingDotation(plan: PlanArmement): number {
    return Math.max(0, plan.quantiteType - plan.quantiteVirtuelle);
  }

  downloadUnitReport(format: 'pdf' | 'xlsx'): void {
    if (!this.unite || !this.unite.id) return;
    this.messageService.add({ severity: 'info', summary: 'Téléchargement', detail: 'Préparation du rapport de dotations...' });
    this.analyticsService.downloadConsommationUnite(this.unite.id, format).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `dotations_consommation_${this.unite!.code}_${new Date().toISOString().split('T')[0]}.${format}`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Rapport téléchargé avec succès.' });
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de générer le rapport.' });
      }
    });
  }

  // --- SPRINT 13/14: DEMANDES D'AJOUT DE DOTATIONS (CLIENT/ADMIN) ---
  demandesDotation: DemandeDotation[] = [];
  loadingDemandesDotation: boolean = true;
  displayDemandeDotationDialog: boolean = false;
  displayRefusDotationDialog: boolean = false;
  
  newDemandeDotation: DemandeDotation = {
    item: { id: '', nomenclature: '', designation: '', prixUnitaire: 0, stockSecurite: 0, quantiteTotale: 0, classeCode: '', sousClasseCode: '', categorieCode: '', serieCode: '', itemCode: '' },
    quantiteType: 1
  };
  
  selectedItemForDotation: any = null;
  selectedDemandeDotationForRefus: DemandeDotation | null = null;
  motifRefusDotation: string = '';

  loadDemandesDotation(): void {
    if (!this.unite || !this.unite.id) return;
    this.loadingDemandesDotation = true;
    this.planArmementService.getDemandesDotationByUnite(this.unite.id).subscribe({
      next: (list) => {
        this.demandesDotation = list;
        this.loadingDemandesDotation = false;
      },
      error: () => {
        this.loadingDemandesDotation = false;
      }
    });
  }

  openDemandeDotationDialog(): void {
    this.newDemandeDotation = {
      unite: { id: this.unite!.id } as any,
      item: { id: '', nomenclature: '', designation: '', prixUnitaire: 0, stockSecurite: 0, quantiteTotale: 0, classeCode: '', sousClasseCode: '', categorieCode: '', serieCode: '', itemCode: '' },
      quantiteType: 1
    };
    this.selectedItemForDotation = null;
    
    // Charger tous les articles du catalogue
    this.http.get<any>(`${environment.apiUrl}/api/v1/items?size=100`).subscribe({
      next: (res: any) => {
        const items = res.content || [];
        // Exclure les articles qui sont déjà dans la dotation nominale de l'unité
        const existingItemIds = this.plans.map(p => p.item.id);
        this.allItems = items.filter((it: any) => !existingItemIds.includes(it.id));
        this.displayDemandeDotationDialog = true;
      }
    });
  }

  saveDemandeDotation(): void {
    if (!this.selectedItemForDotation) return;
    this.newDemandeDotation.item = this.selectedItemForDotation;
    this.newDemandeDotation.unite = { id: this.unite!.id } as any;

    this.planArmementService.creerDemandeDotation(this.newDemandeDotation).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Demande d\'ajout au Plan d\'Armement soumise' });
        this.displayDemandeDotationDialog = false;
        this.loadDemandesDotation();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de soumettre la demande' });
      }
    });
  }

  approuverDemandeDotation(id: number): void {
    this.planArmementService.approuverDemandeDotation(id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Demande approuvée. L\'article a été ajouté au Plan d\'Armement.' });
        this.loadDemandesDotation();
        this.loadPlanArmement();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible d\'approuver la demande' });
      }
    });
  }

  openRefusDotationDialog(dem: DemandeDotation): void {
    this.selectedDemandeDotationForRefus = dem;
    this.motifRefusDotation = '';
    this.displayRefusDotationDialog = true;
  }

  refuserDemandeDotation(): void {
    if (!this.selectedDemandeDotationForRefus || !this.selectedDemandeDotationForRefus.id || !this.motifRefusDotation) return;
    
    this.planArmementService.refuserDemandeDotation(this.selectedDemandeDotationForRefus.id, this.motifRefusDotation).subscribe({
      next: () => {
        this.messageService.add({ severity: 'warn', summary: 'Demande Refusée', detail: 'La demande d\'ajout a été rejetée' });
        this.displayRefusDotationDialog = false;
        this.loadDemandesDotation();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de refuser la demande' });
      }
    });
  }

  // --- SPRINT 15: RÉFORME & DÉCLASSEMENT ---
  loadDossiersReforme(): void {
    if (!this.unite || !this.unite.id) return;
    this.loadingReforme = true;
    this.reformeService.getDossiersByUnite(this.unite.id).subscribe({
      next: (list) => {
        this.dossiersReforme = list;
        this.loadingReforme = false;
      },
      error: () => {
        this.loadingReforme = false;
      }
    });
  }

  openNewReformeDialog(): void {
    this.selectedPlanForReforme = null;
    this.quantiteAReformer = 1;
    this.motifReforme = '';
    this.displayReformeDialog = true;
  }

  saveNewReforme(): void {
    if (!this.selectedPlanForReforme || !this.unite || !this.unite.id) return;
    
    if (this.quantiteAReformer > this.selectedPlanForReforme.quantiteReelle) {
      this.messageService.add({
        severity: 'error',
        summary: 'Quantité invalide',
        detail: `La quantité à réformer (${this.quantiteAReformer}) ne peut pas dépasser la quantité réelle à bord (${this.selectedPlanForReforme.quantiteReelle}).`
      });
      return;
    }

    if (!this.motifReforme) {
      this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Veuillez saisir un motif de réforme.' });
      return;
    }

    const lignes: LigneReforme[] = [{
      item: { id: this.selectedPlanForReforme.item.id },
      quantite: this.quantiteAReformer
    }];

    this.reformeService.creerDossier(this.unite.id, this.motifReforme, lignes).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Demande Soumise', detail: 'La demande de réforme a été transmise à la commission.' });
        this.displayReformeDialog = false;
        this.loadDossiersReforme();
        this.loadPlanArmement(); // Reload dotation numbers
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de soumettre le dossier de réforme.' });
      }
    });
  }

  printCertificatReforme(dossier: DossierReforme): void {
    this.printDossierReforme = dossier;
    this.reformeService.getLignesByDossier(dossier.id!).subscribe({
      next: (list) => {
        this.printLignesReforme = list;
        setTimeout(() => {
          window.print();
        }, 150);
      }
    });
  }

  // --- SPRINT 15 2FA USER CONTROL ---
  init2FaSetup(): void {
    if (!this.unite) return;
    this.authService.setup2Fa(this.unite.code).subscribe({
      next: (res) => {
        this.secret2fa = res.secret;
        this.otpauthUrl = `otpauth://totp/GAMMA3:${this.unite!.code}?secret=${res.secret}&issuer=GAMMA3`;
        this.verificationCode2fa = '';
        this.setup2FaMode = true;
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible d\'initialiser le double facteur.' });
      }
    });
  }

  confirm2FaEnable(): void {
    if (!this.unite) return;
    this.authService.enable2Fa(this.unite.code, this.verificationCode2fa).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sécurité activée', detail: 'Le double facteur (2FA) est désormais actif sur votre compte.' });
        this.setup2FaMode = false;
        
        const current = this.authService.currentUser();
        if (current) {
          current.requires2fa = false;
          localStorage.setItem('gamma3_user', JSON.stringify(current));
          this.authService.currentUser.set({ ...current });
        }
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Code incorrect', detail: 'Le code saisi est invalide ou expiré.' });
      }
    });
  }

  disable2Fa(): void {
    if (!this.unite || !confirm('Voulez-vous vraiment désactiver le double facteur ? Votre compte sera moins sécurisé.')) return;
    
    this.authService.disable2Fa(this.unite.code).subscribe({
      next: () => {
        this.messageService.add({ severity: 'warn', summary: 'Sécurité désactivée', detail: 'Le double facteur a été retiré de votre compte.' });
        
        const current = this.authService.currentUser();
        if (current) {
          current.requires2fa = undefined;
          localStorage.setItem('gamma3_user', JSON.stringify(current));
          this.authService.currentUser.set({ ...current });
        }
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de désactiver la protection.' });
      }
    });
  }

  // Printing state
  printDemande: DemandeMateriel | null = null;
  printLignes: LigneDemande[] = [];

  declencherImpression(dem: DemandeMateriel): void {
    this.printDemande = dem;
    this.distributionService.getLignesByDemande(dem.id!).subscribe({
      next: (list) => {
        this.printLignes = list;
        setTimeout(() => {
          window.print();
        }, 150);
      }
    });
  }

  getLigneTotal(l: LigneDemande): number {
    const qty = l.quantiteAccordee != null ? l.quantiteAccordee : l.quantiteDemandee;
    return qty * (l.item.prixUnitaire || 0);
  }

  getGlobalTotalPrint(): number {
    return this.printLignes.reduce((sum, l) => sum + this.getLigneTotal(l), 0);
  }

  getDecisionLabel(decision: string | undefined): string {
    switch (decision) {
      case 'APPROUVE_TOTAL': return 'Approuvée (Total)';
      case 'APPROUVE_PARTIEL': return 'Approuvée (Partiel)';
      case 'REFUSE': return 'Refusée';
      case 'REFORME_COMPLETE': return 'Réforme Complète';
      case 'DECLASSEMENT': return 'Déclassement';
      default: return decision || 'En attente';
    }
  }
}
