import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FournisseurService, Fournisseur } from '../../../../core/services/fournisseur.service';
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
import { DropdownModule } from 'primeng/dropdown';
import { TableModule } from 'primeng/table';
import * as JsBarcode from 'jsbarcode';
import { MarcheService, Marche, CommandeFournisseur } from '../../../../core/services/marche.service';

import { environment } from '../../../../../environments/environment';
@Component({
  selector: 'app-fournisseur-detail',
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
    DropdownModule,
    TableModule
  ],
  providers: [MessageService],
  templateUrl: './fournisseur-detail.component.html',
  styleUrls: ['./fournisseur-detail.component.css']
})
export class FournisseurDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private fournisseurService = inject(FournisseurService);
  private messageService = inject(MessageService);
  private marcheService = inject(MarcheService);

  fournisseur: Fournisseur | null = null;
  loading: boolean = true;
  error: string | null = null;
  today: Date = new Date();

  displayDialog: boolean = false;
  selectedFournisseur: Fournisseur = { code: '', nom: '' };

  marches: Marche[] = [];
  commandes: CommandeFournisseur[] = [];
  loadingMarches: boolean = true;

  displayMarcheDialog: boolean = false;
  newMarche: Marche = {
    numeroMarche: '',
    designation: '',
    montantTotalHt: 0,
    montantTotalTtc: 0,
    fournisseur: { code: '', nom: '' }
  };

  displayCommandeDialog: boolean = false;
  newCommande: CommandeFournisseur = {
    numeroCommande: '',
    dateCommande: '',
    montantTotal: 0,
    fournisseur: { code: '', nom: '' }
  };

  statutOptions = [
    { label: 'Prospect', value: 'PROSPECT' },
    { label: 'En Évaluation', value: 'EN_EVALUATION' },
    { label: 'Homologué', value: 'HOMOLOGUE' },
    { label: 'Suspendu', value: 'SUSPENDU' }
  ];

  getFullUrl(url: string | undefined): string {
    if (!url) return '';
    if (url.startsWith('http')) return url;
    return `${environment.apiUrl}${url}`;
  }

  ngOnInit(): void {
    this.loadFournisseur();

    // Cleanup print classes after print dialog closes
    window.addEventListener('afterprint', () => {
      document.body.classList.remove('print-document-mode');
      document.body.classList.remove('print-label-mode');
    });
  }

  loadFournisseur(): void {
    const idStr = this.route.snapshot.paramMap.get('id');
    if (idStr) {
      const id = parseInt(idStr, 10);
      this.fournisseurService.getFournisseur(id).subscribe({
        next: (data) => {
          this.fournisseur = data;
          this.loading = false;
          this.renderBarcode();
          this.loadMarchesAndCommandes();
        },
        error: (err) => {
          this.error = 'Impossible de charger les détails du fournisseur.';
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
      if (this.fournisseur) {
        try {
          const renderBarcode = (JsBarcode as any).default || JsBarcode;
          const svgElements = document.querySelectorAll('.barcode-svg');
          svgElements.forEach(el => {
            renderBarcode(el, this.fournisseur!.code, {
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
    if (this.fournisseur && event.files && event.files.length > 0) {
      const file = event.files[0];
      this.fournisseurService.uploadLogo(this.fournisseur.id!, file).subscribe({
        next: (res) => {
          if (res.logoUrl) {
            res.logoUrl = res.logoUrl + '?t=' + new Date().getTime();
          }
          this.fournisseur = res;
          this.messageService.add({
            severity: 'success', 
            summary: 'Succès', 
            detail: 'Logo mis à jour avec succès'
          });
        },
        error: (err) => {
          console.error('Erreur lors de l\'upload du logo:', err);
          this.messageService.add({
            severity: 'error', 
            summary: 'Erreur', 
            detail: 'Impossible de mettre à jour le logo'
          });
        }
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

  editFournisseur(): void {
    if (this.fournisseur) {
      this.selectedFournisseur = { ...this.fournisseur };
      this.displayDialog = true;
    }
  }

  saveFournisseur(): void {
    if (this.selectedFournisseur.id) {
      this.fournisseurService.updateFournisseur(this.selectedFournisseur.id, this.selectedFournisseur).subscribe({
        next: (updated) => {
          this.fournisseur = updated;
          this.displayDialog = false;
          this.messageService.add({
            severity: 'success',
            summary: 'Succès',
            detail: 'Fournisseur mis à jour'
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

  deleteFournisseur(): void {
    if (this.fournisseur && confirm('Voulez-vous vraiment supprimer ce fournisseur ?')) {
      this.fournisseurService.deleteFournisseur(this.fournisseur.id!).subscribe({
        next: () => {
          this.router.navigate(['/fournisseurs']);
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Erreur',
            detail: 'Impossible de supprimer le fournisseur'
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

  loadMarchesAndCommandes(): void {
    if (!this.fournisseur || !this.fournisseur.id) return;
    this.loadingMarches = true;
    this.marcheService.getMarchesByFournisseur(this.fournisseur.id).subscribe({
      next: (mList) => {
        this.marches = mList;
        this.marcheService.getCommandesByFournisseur(this.fournisseur!.id!).subscribe({
          next: (cList) => {
            this.commandes = cList;
            this.loadingMarches = false;
          },
          error: () => {
            this.loadingMarches = false;
          }
        });
      },
      error: () => {
        this.loadingMarches = false;
      }
    });
  }

  getMarcheBudgetConsomme(marche: Marche): number {
    return this.commandes
      .filter(c => c.marche && c.marche.id === marche.id && c.statut !== 'ANNULE')
      .reduce((sum, c) => sum + c.montantTotal, 0);
  }

  getMarcheBudgetConsommePercent(marche: Marche): number {
    if (!marche.montantTotalHt || marche.montantTotalHt === 0) return 0;
    const consumed = this.getMarcheBudgetConsomme(marche);
    return Math.min(Math.round((consumed / marche.montantTotalHt) * 100), 100);
  }

  openNewMarcheDialog(): void {
    this.newMarche = {
      numeroMarche: '',
      designation: '',
      montantTotalHt: 0,
      montantTotalTtc: 0,
      fournisseur: this.fournisseur!,
      statut: 'ACTIF',
      dateDebut: new Date().toISOString().substring(0, 10),
      dateFin: new Date(new Date().setFullYear(new Date().getFullYear() + 1)).toISOString().substring(0, 10)
    };
    this.displayMarcheDialog = true;
  }

  saveNewMarche(): void {
    this.marcheService.createMarche(this.newMarche).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Marché enregistré avec succès' });
        this.displayMarcheDialog = false;
        this.loadMarchesAndCommandes();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de créer le marché' });
      }
    });
  }

  openNewCommandeDialog(marche?: Marche): void {
    this.newCommande = {
      numeroCommande: '',
      dateCommande: new Date().toISOString().substring(0, 10),
      montantTotal: 0,
      fournisseur: this.fournisseur!,
      marche: marche || undefined,
      statut: 'EN_ATTENTE'
    };
    this.displayCommandeDialog = true;
  }

  saveNewCommande(): void {
    this.marcheService.createCommande(this.newCommande).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Bon de commande créé' });
        this.displayCommandeDialog = false;
        this.loadMarchesAndCommandes();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de créer le bon de commande' });
      }
    });
  }
}
