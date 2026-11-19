import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ItemService, Item } from '../item.service';
import { CardModule } from 'primeng/card';
import { PanelModule } from 'primeng/panel';
import { DividerModule } from 'primeng/divider';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { FileUploadModule } from 'primeng/fileupload';
import { DropdownModule } from 'primeng/dropdown';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { QRCodeModule } from 'angularx-qrcode';
import { DialogModule } from 'primeng/dialog';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { StockService } from '../../stock/stock.service';
import { Stock, MouvementDto } from '../../../core/models/stock.model';
import { AuthService } from '../../../core/auth/auth.service';
import { DocumentService } from '../../../core/services/document.service';
import * as JsBarcode from 'jsbarcode';

import { environment } from '../../../../environments/environment';
@Component({
  selector: 'app-item-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, CardModule, PanelModule, DividerModule, ButtonModule, TagModule, FileUploadModule, DropdownModule, ToastModule, QRCodeModule, DialogModule, TableModule, InputTextModule, InputTextareaModule, InputNumberModule, CheckboxModule],
  providers: [MessageService],
  templateUrl: './item-detail.component.html',
  styleUrls: ['./item-detail.component.css']
})
export class ItemDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private itemService = inject(ItemService);
  private stockService = inject(StockService);
  private messageService = inject(MessageService);
  authService = inject(AuthService);
  private documentService = inject(DocumentService);

  isClient(): boolean {
    return this.authService.currentUser()?.role === 'UNIT_USER';
  }
  
  item: Item | null = null;
  loading: boolean = true;
  error: string | null = null;
  today: Date = new Date();

  // Variables de Stock
  stocks: Stock[] = [];
  stockTotal: number = 0;
  displayMouvementModal: boolean = false;
  mouvementDto: Partial<MouvementDto> = { typeMouvement: 'ENTREE', quantite: 1 };

  // Historique des mouvements (bouton « Historique »)
  displayHistoriqueModal = false;
  loadingHistorique = false;
  historique: import('../../stock/stock.service').MouvementHistorique[] = [];

  // Variables de Réservations (Transit)
  reservations: any[] = [];
  loadingReservations: boolean = false;

  dangerClasses = [
    { label: 'Aucun', value: 'NONE', image: null },
    { label: 'Explosif', value: 'EXPLOSIVE', image: 'assets/danger/picto-danger-1.png' },
    { label: 'Inflammable', value: 'FLAMMABLE', image: 'assets/danger/picto-danger-2.png' },
    { label: 'Toxique', value: 'TOXIC', image: 'assets/danger/picto-danger-6.png' },
    { label: 'Corrosif', value: 'CORROSIVE', image: 'assets/danger/GHS-pictogram-acid.svg.png' },
    { label: 'Nocif / Irritant', value: 'HARMFUL', image: 'assets/danger/webpc-passthru.webp' },
    { label: 'Danger pour l\'environnement', value: 'ENVIRONMENTAL_HAZARD', image: 'assets/danger/SGH-09.png' }
  ];

  selectedDangerClass: string = 'NONE';
  editDangerMode: boolean = false;

  // ─── Fiche Signalétique d'Article (FSA) — ajouté le 2026-09-11, audit de conformité CCF
  //     (voir docs/AUDIT_CONFORMITE_CCF_2026-09-11.md §4). Réservé à l'administration
  //     (isClient() masque déjà le bouton), même patron que l'édition de la classe de danger.
  displayFsaModal = false;
  savingFsa = false;
  fsaForm: Partial<Item> = {};

  readonly criticiteOptions = [
    { label: 'Critique (Cr)', value: 'CRITIQUE' },
    { label: 'Non-critique (NCr)', value: 'NON_CRITIQUE' }
  ];
  readonly reparabiliteOptions = [
    { label: 'Réparable (Rep)', value: 'REPARABLE' },
    { label: 'Non réparable (NRep)', value: 'NON_REPARABLE' }
  ];
  readonly codeTypeMaterielOptions = [
    { label: 'MC — Matériel complet', value: 'MC' },
    { label: 'EQ — Équipement', value: 'EQ' },
    { label: 'BE — Banc d\'essai / mesure', value: 'BE' },
    { label: 'DT — Document technique', value: 'DT' },
    { label: 'PS — Sous-ensemble de rechange', value: 'PS' },
    { label: 'PR — Pièce de rechange spécifique', value: 'PR' },
    { label: 'PC — Pièce de rechange commune', value: 'PC' },
    { label: 'AC — Accessoire', value: 'AC' },
    { label: 'OD — Outillage standard', value: 'OD' },
    { label: 'OS — Outillage spécifique', value: 'OS' },
    { label: 'MP — Matière première', value: 'MP' },
    { label: 'MS — Matière première dangereuse', value: 'MS' }
  ];
  readonly codeGestionOptions = [
    { label: 'Clf — Commandes par lot fixe', value: 'CLF' },
    { label: 'Clu — Nombre limité d\'unités', value: 'CLU' },
    { label: 'Ciu — Nombre illimité d\'unités', value: 'CIU' },
    { label: 'Cei — Commandes externes interdites', value: 'CEI' },
    { label: 'Csm — Interdites sous stock minimum', value: 'CSM' },
    { label: 'Clm — Limitées au stock maximum', value: 'CLM' }
  ];
  readonly codeSoutienOptions = [
    { label: 'S10 — Soutien normé', value: 'S10' },
    { label: 'S20 — Soutien restreint', value: 'S20' },
    { label: 'S30 — Soutien spécial', value: 'S30' },
    { label: 'S40 — Soutien suspendu', value: 'S40' },
    { label: 'S50 — Soutien arrêté', value: 'S50' }
  ];
  readonly codePositionAdministrativeOptions = [
    { label: 'An — Allocation', value: 'AN' },
    { label: 'Dc — Dotation de configuration', value: 'DC' },
    { label: 'Da — Dotation d\'armement', value: 'DA' },
    { label: 'Sb — Stock bord', value: 'SB' },
    { label: 'Va — Volant d\'ateliers', value: 'VA' },
    { label: 'Ps — Spécifique', value: 'PS' },
    { label: 'Px — Non définie', value: 'PX' }
  ];
  readonly codeModeApprovisionnementOptions = [
    { label: 'GRC — Gré à gré / marché cadre', value: 'GRC' },
    { label: 'ADO — Appel d\'offre', value: 'ADO' },
    { label: 'BDC — Bon de commande', value: 'BDC' },
    { label: 'ADR — Achat direct par régie', value: 'ADR' },
    { label: 'MAX — Non défini', value: 'MAX' },
    { label: 'LCE — Lettre de commande à l\'étranger', value: 'LCE' }
  ];
  readonly codeOrigineMaterielOptions = [
    { label: 'AFn — Marché local', value: 'AFN' },
    { label: 'AFe — Fournisseur d\'origine', value: 'AFE' },
    { label: 'FMs — Armée américaine (FMS)', value: 'FMS' },
    { label: 'DOc — Don / convention', value: 'DOC' },
    { label: 'CL1 — Fabrication ateliers Armée de Mer', value: 'CL1' },
    { label: 'CL2 — Fabrication autre direction MDN', value: 'CL2' },
    { label: 'CL3 — Fabrication fournisseur local', value: 'CL3' },
    { label: 'CL4 — Fabrication fournisseur étranger', value: 'CL4' },
    { label: 'PR1 — Prêt provisoire', value: 'PR1' },
    { label: 'AD1 — Dislocation', value: 'AD1' },
    { label: 'AD2 — Montage / assemblage', value: 'AD2' },
    { label: 'AX1 — Origine indéfinie', value: 'AX1' }
  ];

  getFullUrl(url: string | undefined): string {
    if (!url) return '';
    if (url.startsWith('http')) return url;
    return `${environment.apiUrl}${url}`;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.itemService.getItemById(id).subscribe({
        next: (data) => {
          this.item = data;
          this.selectedDangerClass = data.dangerClass || 'NONE';
          this.loading = false;
          this.loadStock();
          this.loadReservations();
          // Render Barcodes
          setTimeout(() => {
            if (this.item) {
               try {
                 const renderBarcode = (JsBarcode as any).default || JsBarcode;
                 const svgElements = document.querySelectorAll('.barcode-svg');
                 svgElements.forEach(el => {
                   renderBarcode(el, this.item!.nomenclature, {
                      format: "CODE128",
                      lineColor: "#000",
                      width: 2,
                      height: 40,
                      displayValue: true
                   });
                 });
               } catch(e) { console.error('Erreur JsBarcode', e); }
            }
          }, 100);
        },
        error: (err) => {
          this.error = 'Impossible de charger l\'article.';
          this.loading = false;
          console.error(err);
        }
      });
    } else {
      this.error = 'Identifiant introuvable.';
      this.loading = false;
    }

    // Cleanup print classes after print dialog closes
    window.addEventListener('afterprint', () => {
      document.body.classList.remove('print-document-mode');
      document.body.classList.remove('print-label-mode');
    });
  }

  getStockSeverity(stock: number): 'success' | 'warning' | 'danger' {
    if (stock > 50) return 'success';
    if (stock > 10) return 'warning';
    return 'danger';
  }

  getDangerImage(value: string): string | null {
    const found = this.dangerClasses.find(d => d.value === value);
    return found ? found.image : null;
  }

  getDangerLabel(value: string): string {
    const found = this.dangerClasses.find(d => d.value === value);
    return found ? found.label : 'Aucun';
  }

  saveDangerClass() {
    if (this.item) {
      this.itemService.updateDangerClass(this.item.id, this.selectedDangerClass).subscribe({
        next: (res) => {
          this.item = res;
          this.editDangerMode = false;
          this.messageService.add({severity:'success', summary:'Succès', detail:'Classe de danger mise à jour'});
        },
        error: () => this.messageService.add({severity:'error', summary:'Erreur', detail:'Impossible de mettre à jour'})
      });
    }
  }

  /** Ouvre la boîte de dialogue d'édition de la Fiche Signalétique d'Article (FSA). */
  openFsaModal(): void {
    if (!this.item) return;
    const {
      poids, volume, normeIso, echelonMaintenance, codeConditionnement,
      criticite, codeTypeMateriel,
      stockMinimum, stockMaximum, codeGestion, codeSoutien, codePositionAdministrative,
      codeModeApprovisionnement, codeOrigineMateriel, reparabilite,
      delaiApprovisionnementAnnees, blocageApprovisionnement, cma, etatArticle,
      dureeConservation, nsn, oem
    } = this.item;
    this.fsaForm = {
      poids, volume, normeIso, echelonMaintenance, codeConditionnement,
      criticite, codeTypeMateriel,
      stockMinimum, stockMaximum, codeGestion, codeSoutien, codePositionAdministrative,
      codeModeApprovisionnement, codeOrigineMateriel, reparabilite,
      delaiApprovisionnementAnnees, blocageApprovisionnement: !!blocageApprovisionnement, cma, etatArticle,
      dureeConservation, nsn, oem
    };
    this.displayFsaModal = true;
  }

  saveFsa(): void {
    if (!this.item) return;
    this.savingFsa = true;
    this.itemService.updateItem(this.item.id, this.fsaForm).subscribe({
      next: (res) => {
        this.item = res;
        this.savingFsa = false;
        this.displayFsaModal = false;
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Fiche signalétique mise à jour.' });
      },
      error: (err) => {
        this.savingFsa = false;
        this.messageService.add({
          severity: 'error', summary: 'Erreur',
          detail: typeof err?.error?.error === 'string' ? err.error.error : 'Impossible de mettre à jour la fiche.'
        });
      }
    });
  }

  onPhotoUpload(event: any) {
    if (this.item && event.files && event.files.length > 0) {
      const file = event.files[0];
      console.log('Tentative d\'upload photo pour l\'article:', this.item.id);
      this.itemService.uploadPhoto(this.item.id, file).subscribe({
        next: (res) => {
          console.log('Réponse upload réussie. Nouvel objet Item:', res);
          // On force le rafraîchissement en ajoutant un paramètre de temps à l'URL si elle existe
          if (res.photoUrl) {
            res.photoUrl = res.photoUrl + '?t=' + new Date().getTime();
          }
          this.item = res;
          this.messageService.add({severity:'success', summary:'Succès', detail:'Photo uploadée avec succès'});
        },
        error: (err) => {
          console.error('Erreur lors de l\'upload photo:', err);
          this.messageService.add({severity:'error', summary:'Erreur', detail:'Impossible d\'uploader la photo'});
        }
      });
    }
  }

  onDocUpload(event: any) {
    if (this.item && event.files && event.files.length > 0) {
      const file = event.files[0];
      this.itemService.uploadDoc(this.item.id, file).subscribe({
        next: (res) => {
          this.item = res;
          this.messageService.add({severity:'success', summary:'Succès', detail:'Document ajouté avec succès'});
        },
        error: () => this.messageService.add({severity:'error', summary:'Erreur', detail:'Impossible d\'ajouter le document'})
      });
    }
  }

  /** Ouvre l'historique complet des mouvements de stock de l'article (in-app, pas de PDF). */
  openHistorique(): void {
    if (!this.item) return;
    this.displayHistoriqueModal = true;
    this.loadingHistorique = true;
    this.stockService.getItemHistorique(this.item.id).subscribe({
      next: (list) => { this.historique = list; this.loadingHistorique = false; },
      error: () => {
        this.loadingHistorique = false;
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: "Impossible de charger l'historique." });
      }
    });
  }

  /** Fiche article officielle en PDF (serveur). Remplace l'impression navigateur. */
  printPage() {
    if (!this.item) return;
    this.documentService.ouvrir('fiche-article/' + encodeURIComponent(this.item.nomenclature));
  }

  /** Étiquette autocollante (petit format, QR + code-barres) en PDF (serveur). */
  printLabel() {
    if (!this.item) return;
    this.documentService.ouvrir('etiquette/article/' + encodeURIComponent(this.item.nomenclature));
  }

  // Soutes disponibles pour les mouvements de stock
  readonly magasinOptions = [
    { label: '🔵 Nord (Bizerte - SGS)', value: 1 },
    { label: '🟢 Centre (SM1)', value: 2 },
    { label: '🟡 Sud (Sfax)', value: 3 }
  ];

  isStockMinAlert(): boolean {
    if (!this.item) return false;
    const minThreshold = (this.item.stockMinimum !== null && this.item.stockMinimum !== undefined)
      ? Number(this.item.stockMinimum)
      : (this.item.stockSecurite ? Number(this.item.stockSecurite) : 0);
    return minThreshold > 0 && this.stockTotal <= minThreshold;
  }

  isStockMaxAlert(): boolean {
    if (!this.item || !this.item.stockMaximum) return false;
    return this.stockTotal >= Number(this.item.stockMaximum);
  }

  loadStock() {
    if (this.item) {
      this.stockService.getStockByItemId(this.item.id).subscribe({
        next: (res) => {
          this.stocks = res;
          // Conversion numérique explicite pour éviter toute concaténation textuelle
          this.stockTotal = this.stocks.reduce((acc, curr) => acc + Number(curr.quantite || 0), 0);
        },
        error: () => console.error('Erreur chargement stock')
      });
    }
  }

  loadReservations() {
    if (this.item) {
      this.loadingReservations = true;
      this.itemService.getItemReservations(this.item.id).subscribe({
        next: (res) => {
          this.reservations = res;
          this.loadingReservations = false;
        },
        error: (err) => {
          console.error('Erreur chargement réservations', err);
          this.loadingReservations = false;
        }
      });
    }
  }

  getReservedTotal(): number {
    return this.reservations.reduce((acc, curr) => acc + Number(curr.quantite || 0), 0);
  }

  openMouvementModal() {
    this.mouvementDto = {
      typeMouvement: 'ENTREE',
      quantite: 1,
      magasinId: 1,
      itemId: this.item?.id,
      referenceBon: 'BE-' + new Date().getTime(),
      motif: '',
      rayon: '',
      emplacement: ''
    };
    this.displayMouvementModal = true;
  }

  effectuerMouvement() {
    const qte = Number(this.mouvementDto.quantite);
    if (!qte || qte <= 0) {
      this.messageService.add({severity:'error', summary:'Erreur', detail:'La quantité doit être supérieure à 0'});
      return;
    }
    if (!this.mouvementDto.magasinId) {
      this.messageService.add({severity:'error', summary:'Erreur', detail:'Veuillez sélectionner une soute / magasin valide'});
      return;
    }
    this.mouvementDto.quantite = qte;
    this.stockService.effectuerMouvement(this.mouvementDto as MouvementDto).subscribe({
      next: () => {
        this.messageService.add({severity:'success', summary:'Succès', detail:'Mouvement de stock enregistré avec succès'});
        this.displayMouvementModal = false;
        this.loadStock();
      },
      error: (err) => {
        const detailMsg = err.error?.message || err.message || 'Impossible d\'enregistrer le mouvement';
        this.messageService.add({severity:'error', summary:'Échec Mouvement', detail: detailMsg});
      }
    });
  }
}
