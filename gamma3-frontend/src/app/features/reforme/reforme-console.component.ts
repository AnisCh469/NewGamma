import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReformeService, DossierReforme, LigneReforme } from '../../core/services/reforme.service';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';

@Component({
  selector: 'app-reforme-console',
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
    InputTextareaModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './reforme-console.component.html',
  styleUrls: []
})
export class ReformeConsoleComponent implements OnInit {
  private reformeService = inject(ReformeService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  dossiers: DossierReforme[] = [];
  allDossiers: DossierReforme[] = [];
  isFiltered: boolean = false;
  loading: boolean = true;

  // Dialog Commission state
  displayCommissionDialog: boolean = false;
  selectedDossier: DossierReforme | null = null;
  lignes: LigneReforme[] = [];
  
  decision: 'REFORME_COMPLETE' | 'DECLASSEMENT' | 'REPARATION' | 'REJETE' = 'REFORME_COMPLETE';
  membresCommission: string = 'Président : CF Mnasri, Membres : LV Nouri, LV Elhammi';
  observationsCommission: string = '';
  dateCommission: string = new Date().toISOString().split('T')[0];

  decisionOptions = [
    { label: 'Réforme Complète (Mise au rebut)', value: 'REFORME_COMPLETE' },
    { label: 'Déclassement (Transfert vers Magasin SRR)', value: 'DECLASSEMENT' },
    { label: 'Réparation requise', value: 'REPARATION' },
    { label: 'Rejeter la demande de réforme', value: 'REJETE' }
  ];

  // Printing state
  printDossier: DossierReforme | null = null;
  printLignes: LigneReforme[] = [];
  today: Date = new Date();

  ngOnInit(): void {
    this.loadDossiers();

    this.route.queryParams.subscribe(() => {
      if (this.allDossiers.length > 0) {
        this.applyFilter();
      }
    });
  }

  loadDossiers(): void {
    this.loading = true;
    this.reformeService.getAllDossiers().subscribe({
      next: (list) => {
        this.allDossiers = list;
        this.applyFilter();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur',
          detail: 'Impossible de charger la liste des dossiers de réforme.'
        });
      }
    });
  }

  applyFilter(): void {
    const params = this.route.snapshot.queryParams;
    if (params['uniteId']) {
      const uId = parseInt(params['uniteId'], 10);
      this.dossiers = this.allDossiers.filter(d => d.unite?.id === uId);
      this.isFiltered = true;
    } else {
      this.dossiers = this.allDossiers;
      this.isFiltered = false;
    }
  }

  clearFilter(): void {
    this.router.navigate([], { queryParams: { uniteId: null }, queryParamsHandling: 'merge' }).then(() => {
      this.applyFilter();
    });
  }

  openCommissionModal(dossier: DossierReforme): void {
    this.selectedDossier = dossier;
    this.decision = (dossier.decisionCommission as any) || 'REFORME_COMPLETE';
    this.membresCommission = dossier.membresCommission || 'Président : CF Mnasri, Membres : LV Nouri, LV Elhammi';
    this.observationsCommission = dossier.observationsCommission || '';
    if (dossier.dateCommission) {
      this.dateCommission = dossier.dateCommission;
    } else {
      this.dateCommission = new Date().toISOString().split('T')[0];
    }
    
    this.reformeService.getLignesByDossier(dossier.id!).subscribe({
      next: (list) => {
        this.lignes = list;
        this.displayCommissionDialog = true;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur',
          detail: 'Impossible de charger les lignes de cet examen de réforme.'
        });
      }
    });
  }

  saveCommissionDecision(): void {
    if (!this.selectedDossier) return;

    if (!this.membresCommission) {
      this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Veuillez saisir les membres de la commission.' });
      return;
    }

    this.reformeService.traiterCommission(
      this.selectedDossier.id!,
      this.decision,
      this.membresCommission,
      this.observationsCommission,
      this.dateCommission
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Décision Actée',
          detail: `Le dossier ${this.selectedDossier!.numeroDossier} a été traité avec succès (Décision : ${this.decision}).`
        });
        this.displayCommissionDialog = false;
        this.loadDossiers();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erreur',
          detail: 'Impossible de valider la décision de la commission.'
        });
      }
    });
  }

  declencherImpression(dossier: DossierReforme): void {
    this.printDossier = dossier;
    this.reformeService.getLignesByDossier(dossier.id!).subscribe({
      next: (list) => {
        this.printLignes = list;
        setTimeout(() => {
          window.print();
        }, 150);
      }
    });
  }

  getStatutSeverity(statut: string | undefined): 'success' | 'secondary' | 'info' | 'warning' | 'danger' | undefined {
    switch (statut) {
      case 'SOUMIS': return 'warning';
      case 'COMMISSION': return 'info';
      case 'TRAITE': return 'success';
      case 'REJETE': return 'danger';
      default: return 'secondary';
    }
  }

  getDecisionLabel(decision: string | undefined): string {
    switch (decision) {
      case 'REFORME_COMPLETE': return 'RÉFORME COMPLÈTE';
      case 'DECLASSEMENT': return 'DÉCLASSEMENT MAGASIN SRR';
      case 'REPARATION': return 'RÉPARATION REQUISE';
      case 'REJETE': return 'REJETÉ';
      default: return 'EN ATTENTE D\'EXAMEN';
    }
  }

  getDecisionSeverity(decision: string | undefined): 'success' | 'secondary' | 'info' | 'warning' | 'danger' | undefined {
    switch (decision) {
      case 'REFORME_COMPLETE': return 'danger';
      case 'DECLASSEMENT': return 'success';
      case 'REPARATION': return 'warning';
      case 'REJETE': return 'secondary';
      default: return 'info';
    }
  }

  getFirstMembreCommission(dossier: DossierReforme | null): string {
    if (!dossier || !dossier.membresCommission) return '';
    return dossier.membresCommission.split(',')[0];
  }
}
