import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import {
  ReceptionService,
  BonProvisoireReception,
  PvCommission,
  BonEntree,
  LigneReception,
  BprRequestDto
} from '../../../core/services/reception.service';
import { FournisseurService, Fournisseur } from '../../../core/services/fournisseur.service';
import { MarcheService } from '../../../core/services/marche.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { StepsModule } from 'primeng/steps';
import { MenuItem, MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DropdownModule } from 'primeng/dropdown';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { InputTextModule } from 'primeng/inputtext';

import { environment } from '../../../../environments/environment';
@Component({
  selector: 'app-reception-detail',
  standalone: true,
  imports: [
    CommonModule, 
    RouterModule, 
    FormsModule, 
    CardModule, 
    ButtonModule, 
    StepsModule, 
    ToastModule, 
    TableModule, 
    TagModule,
    DropdownModule,
    AutoCompleteModule,
    InputTextModule
  ],
  providers: [MessageService],
  templateUrl: './reception-detail.component.html',
  styleUrls: ['./reception-detail.component.css']
})
export class ReceptionDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private receptionService = inject(ReceptionService);
  private fournisseurService = inject(FournisseurService);
  private marcheService = inject(MarcheService);
  private authService = inject(AuthService);
  private messageService = inject(MessageService);
  private http = inject(HttpClient);

  bpr: BonProvisoireReception | null = null;
  lignes: LigneReception[] = [];
  pv: PvCommission | null = null;
  be: BonEntree | null = null;

  items: MenuItem[] | undefined;
  activeIndex: number = 0;
  loading: boolean = true;
  validatingPv: boolean = false;
  generatingBe: boolean = false;

  // Form states for creation
  isNew: boolean = false;
  newBpr: any = {
    numeroBpr: '',
    dateReception: new Date().toISOString().split('T')[0],
    fournisseur: null,
    magasin: null,
    commande: null
  };
  newLignes: any[] = [];
  selectedItem: any = null;
  tempQuantite: number = 1;

  fournisseurs: Fournisseur[] = [];
  magasins: any[] = [];
  commandes: any[] = [];
  filteredItems: any[] = [];

  // Form states for PV validation
  newPv: any = {
    numeroPv: '',
    dateCommission: new Date().toISOString().split('T')[0],
    membres: '',
    decision: 'ACCEPTE',
    observations: ''
  };

  // Form states for BE generation
  newBe: any = {
    numeroBe: '',
    dateEntree: new Date().toISOString().split('T')[0]
  };

  decisionOptions = [
    { label: 'Accepté sans réserves', value: 'ACCEPTE' },
    { label: 'Accepté avec réserves', value: 'ACCEPTE_AVEC_RESERVE' },
    { label: 'Refusé', value: 'REFUSE' }
  ];

  ngOnInit() {
    this.items = [
      { label: 'Bon Provisoire (BPR)' },
      { label: 'Commission (PV)' },
      { label: "Bon d'Entrée (BE)" }
    ];

    const idStr = this.route.snapshot.paramMap.get('id');
    if (idStr && idStr === 'new') {
      this.isNew = true;
      this.loading = false;
      this.loadDropdowns();
      // Auto-generate temporary BPR code
      this.newBpr.numeroBpr = 'BPR-' + new Date().getFullYear() + '-' + Math.floor(100 + Math.random() * 900);
    } else if (idStr) {
      this.isNew = false;
      this.loadDossier(parseInt(idStr, 10));
    } else {
      this.loading = false;
    }
  }

  loadDropdowns(): void {
    this.fournisseurService.getFournisseurs().subscribe({
      next: (data) => {
        this.fournisseurs = data;
      }
    });
    this.receptionService.getMagasins().subscribe({
      next: (data) => {
        this.magasins = data;
      }
    });
  }

  onFournisseurChange(event: any): void {
    if (event.value && event.value.id) {
      this.marcheService.getCommandesByFournisseur(event.value.id).subscribe({
        next: (cmds) => {
          this.commandes = cmds.filter(c => c.statut === 'EN_ATTENTE');
        }
      });
    } else {
      this.commandes = [];
      this.newBpr.commande = null;
    }
  }

  searchItems(event: any): void {
    const query = event.query;
    this.http.get<any>(`${environment.apiUrl}/api/v1/items?size=15&search=${query}`).subscribe({
      next: (res) => {
        this.filteredItems = res.content || [];
      }
    });
  }

  addLine(): void {
    if (!this.selectedItem) {
      this.messageService.add({severity: 'error', summary: 'Erreur', detail: 'Veuillez sélectionner un article du catalogue.'});
      return;
    }
    if (this.tempQuantite <= 0) {
      this.messageService.add({severity: 'error', summary: 'Erreur', detail: 'La quantité livrée doit être supérieure à 0.'});
      return;
    }

    // Check duplicate
    const exists = this.newLignes.find(l => l.item.id === this.selectedItem.id);
    if (exists) {
      exists.quantiteLivree += this.tempQuantite;
    } else {
      this.newLignes.push({
        item: this.selectedItem,
        quantiteLivree: this.tempQuantite,
        prixUnitaire: this.selectedItem.prixUnitaire
      });
    }

    this.selectedItem = null;
    this.tempQuantite = 1;
    this.messageService.add({severity: 'success', summary: 'Article Ajouté', detail: 'Article ajouté à la liste temporaire.'});
  }

  removeLine(index: number): void {
    this.newLignes.splice(index, 1);
  }

  saveBpr(): void {
    if (!this.newBpr.numeroBpr || !this.newBpr.fournisseur || !this.newBpr.magasin || this.newLignes.length === 0) {
      this.messageService.add({
        severity: 'error',
        summary: 'Formulaire Incomplet',
        detail: 'Veuillez renseigner tous les champs obligatoires (*) et ajouter au moins un article.'
      });
      return;
    }

    const payload: BprRequestDto = {
      bpr: {
        numeroBpr: this.newBpr.numeroBpr,
        dateReception: this.newBpr.dateReception,
        fournisseur: this.newBpr.fournisseur,
        magasin: this.newBpr.magasin,
        commande: this.newBpr.commande || undefined
      },
      lignes: this.newLignes.map(l => ({
        item: { id: l.item.id } as any,
        quantiteLivree: l.quantiteLivree,
        prixUnitaire: l.prixUnitaire
      }))
    };

    this.receptionService.createBpr(payload).subscribe({
      next: (savedBpr) => {
        this.messageService.add({
          severity: 'success',
          summary: 'BPR Enregistré',
          detail: 'Le bon provisoire de réception a été créé avec succès.'
        });
        this.router.navigate(['/receptions', savedBpr.id]);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur',
          detail: 'Impossible de créer le BPR. La référence existe déjà.'
        });
      }
    });
  }

  loadDossier(id: number): void {
    this.loading = true;
    this.receptionService.getBpr(id).subscribe({
      next: (bpr: BonProvisoireReception) => {
        this.bpr = bpr;
        
        // Load lines
        this.receptionService.getLignesByBpr(id).subscribe({
          next: (lignes: LigneReception[]) => {
            this.lignes = lignes;
            
            // Check status and load PV if needed
            if (bpr.statut === 'VALIDE' || bpr.statut === 'REJETE') {
              this.receptionService.getPvByBpr(id).subscribe({
                next: (pv: PvCommission) => {
                  this.pv = pv;
                  this.activeIndex = 1;
                  
                  // Pre-populate BE fields if not exists
                  this.newBe.numeroBe = 'BE-' + bpr.numeroBpr;
                  
                  // Now load BE if we have a PV
                  this.receptionService.getBeByPv(pv.id!).subscribe({
                    next: (be: BonEntree) => {
                      this.be = be;
                      this.activeIndex = 2;
                      this.loading = false;
                    },
                    error: () => {
                      this.loading = false; // No BE generated yet
                    }
                  });
                },
                error: () => { this.loading = false; }
              });
            } else {
              this.activeIndex = 0;
              this.loading = false;
              
              // Pre-populate PV fields
              this.newPv.numeroPv = 'PV-' + bpr.numeroBpr;
              this.newPv.membres = 'CEN. Ben Ali (Président), EV1. Haddad, ASP. Mabrouk';
            }
          },
          error: () => { this.loading = false; }
        });
      },
      error: () => { this.loading = false; }
    });
  }

  validerPv(): void {
    if (!this.bpr) return;
    if (!this.newPv.numeroPv || !this.newPv.membres) {
      this.messageService.add({severity: 'error', summary: 'Formulaire Incomplet', detail: 'Veuillez saisir le numéro de PV et les membres.'});
      return;
    }
    
    this.validatingPv = true;
    const newPvObj: PvCommission = {
      numeroPv: this.newPv.numeroPv,
      dateCommission: this.newPv.dateCommission,
      membres: this.newPv.membres,
      decision: this.newPv.decision,
      observations: this.newPv.observations,
      bpr: this.bpr
    };

    this.receptionService.createPv(newPvObj).subscribe({
      next: (pv: PvCommission) => {
        this.pv = pv;
        this.bpr!.statut = pv.decision === 'REFUSE' ? 'REJETE' : 'VALIDE';
        this.activeIndex = 1;
        this.validatingPv = false;
        
        const severity = pv.decision === 'REFUSE' ? 'error' : 'success';
        const summary = pv.decision === 'REFUSE' ? 'Réception Rejetée' : 'PV Validé';
        const detail = pv.decision === 'REFUSE' 
          ? 'Le PV technique a été enregistré avec un avis défavorable.'
          : 'Le PV technique a été validé avec succès. Prêt pour la mise en stock.';
        
        this.messageService.add({ severity, summary, detail, life: 5000 });
        
        if (pv.decision === 'REFUSE') {
          // If rejected, remain at step 1 as it cannot go to step 2 (BE)
        } else {
          // Default pre-population of BE
          this.newBe.numeroBe = 'BE-' + this.bpr!.numeroBpr;
        }
      },
      error: () => { 
        this.validatingPv = false;
        this.messageService.add({severity: 'error', summary: 'Erreur', detail: 'Impossible d\'enregistrer le PV. Référence existante.'});
      }
    });
  }

  genererBe(): void {
    if (!this.pv) return;
    if (!this.newBe.numeroBe) {
      this.messageService.add({severity: 'error', summary: 'Formulaire Incomplet', detail: 'Veuillez saisir un numéro de Bon d\'Entrée.'});
      return;
    }

    this.generatingBe = true;
    const newBeObj: BonEntree = {
      numeroBe: this.newBe.numeroBe,
      dateEntree: this.newBe.dateEntree,
      pvCommission: this.pv
    };

    this.receptionService.createBe(newBeObj).subscribe({
      next: (be: BonEntree) => {
        this.be = be;
        this.activeIndex = 2;
        this.generatingBe = false;
        this.messageService.add({
          severity: 'success', 
          summary: 'BE Généré !', 
          detail: 'Le matériel a été enregistré en soute et les stocks physiques ont été incrémentés.', 
          life: 6000
        });
      },
      error: () => { 
        this.generatingBe = false;
        this.messageService.add({severity: 'error', summary: 'Erreur', detail: 'Impossible de générer le BE. Numéro déjà attribué.'});
      }
    });
  }

  isClient(): boolean {
    const user = this.authService.currentUser();
    return user ? user.role === 'UNIT_USER' : false;
  }

  printBE(): void {
    window.print();
  }

  getLigneTotal(ligne: any): number {
    return (ligne.quantiteLivree || 0) * (ligne.prixUnitaire || 0);
  }

  getGlobalTotal(): number {
    return this.lignes.reduce((sum, l) => sum + this.getLigneTotal(l), 0);
  }

  getNewLignesTotal(): number {
    return this.newLignes.reduce((sum, l) => sum + this.getLigneTotal(l), 0);
  }
}
