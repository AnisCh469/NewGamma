import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../../core/auth/auth.service';
import { DistributionService, DemandeMateriel } from '../../../core/services/distribution.service';

/**
 * Écran "Valider les Demandes" (Commandant d'unité uniquement).
 *
 * Contexte (règle métier du 2026-09-04) : chaque unité dispose de deux comptes — un
 * Gestionnaire qui compose et soumet les demandes de matériel, et un Commandant (chef
 * d'unité) qui les valide ou les refuse AVANT qu'elles n'atteignent GAMMA3 (DA_MANAGER/
 * ADMIN) — une double vérification. Cet écran est la vue du Commandant : il ne voit que les
 * demandes de SA PROPRE unité en attente de sa décision (voir DemandeController.
 * validerCommandant côté backend, qui applique la même restriction serveur).
 */
@Component({
  selector: 'app-validation-commandant',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DialogModule, InputTextareaModule, TagModule],
  templateUrl: './validation-commandant.component.html',
  styleUrl: './validation-commandant.component.css'
})
export class ValidationCommandantComponent implements OnInit {
  demandesEnAttente: DemandeMateriel[] = [];
  historique: DemandeMateriel[] = [];
  isLoading = false;
  errorMessage = '';

  displayRefusDialog = false;
  demandeAValiderOuRefuser: DemandeMateriel | null = null;
  motifRefus = '';
  isSubmitting = false;

  constructor(
    private authService: AuthService,
    private distributionService: DistributionService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const currentUser = this.authService.currentUser();
    if (!currentUser || currentUser.role !== 'UNIT_USER' || currentUser.fonctionUnite !== 'COMMANDANT') {
      this.router.navigate(['/catalogue']);
      return;
    }
    if (!currentUser.uniteId) {
      this.errorMessage = "Votre compte n'est rattaché à aucune unité. Contactez un administrateur.";
      return;
    }
    this.loadDemandes(currentUser.uniteId);
  }

  loadDemandes(uniteId: number): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.distributionService.getDemandesByUnite(uniteId).subscribe({
      next: (demandes) => {
        this.isLoading = false;
        this.demandesEnAttente = demandes.filter(d => d.statut === 'EN_ATTENTE_VALIDATION_COMMANDANT');
        this.historique = demandes.filter(d => d.statut !== 'EN_ATTENTE_VALIDATION_COMMANDANT')
          .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''));
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || err?.error || "Erreur lors du chargement des demandes.";
      }
    });
  }

  accepter(demande: DemandeMateriel): void {
    if (!confirm(`Valider la demande ${demande.numeroDemande} et la transmettre à la DA ?`)) return;
    this.isSubmitting = true;
    this.distributionService.validerCommandant(demande.id!, { accepter: true }).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.reloadForCurrentUnite();
      },
      error: (err) => {
        this.isSubmitting = false;
        alert(err?.error?.message || err?.error || "Erreur lors de la validation.");
      }
    });
  }

  openRefus(demande: DemandeMateriel): void {
    this.demandeAValiderOuRefuser = demande;
    this.motifRefus = '';
    this.displayRefusDialog = true;
  }

  confirmerRefus(): void {
    if (!this.demandeAValiderOuRefuser?.id || !this.motifRefus.trim()) return;
    this.isSubmitting = true;
    this.distributionService.validerCommandant(this.demandeAValiderOuRefuser.id, {
      accepter: false,
      motifRefus: this.motifRefus.trim()
    }).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.displayRefusDialog = false;
        this.reloadForCurrentUnite();
      },
      error: (err) => {
        this.isSubmitting = false;
        alert(err?.error?.message || err?.error || "Erreur lors du refus.");
      }
    });
  }

  private reloadForCurrentUnite(): void {
    const uniteId = this.authService.currentUser()?.uniteId;
    if (uniteId) this.loadDemandes(uniteId);
  }

  getStatutSeverity(statut: string | undefined): 'success' | 'secondary' | 'info' | 'warning' | 'danger' | 'contrast' | undefined {
    switch (statut) {
      case 'REFUSE_COMMANDANT':
      case 'REFUSE': return 'danger';
      case 'LIVRE':
      case 'APPROUVE_TOTAL': return 'success';
      case 'APPROUVE_PARTIEL':
      case 'ESCALADE_SGS': return 'warning';
      default: return 'info';
    }
  }

  getStatutLabel(statut: string | undefined): string {
    switch (statut) {
      case 'REFUSE_COMMANDANT': return 'Refusée (vous)';
      case 'SOUMIS': return 'Transmise à la DA';
      case 'EN_COURS_ARBITRAGE': return "En cours d'arbitrage";
      case 'ESCALADE_SGS': return 'Escaladée SGS';
      case 'APPROUVE_PARTIEL': return 'Approuvée partiellement';
      case 'APPROUVE_TOTAL': return 'Approuvée';
      case 'REFUSE': return 'Refusée (DA)';
      case 'LIVRE': return 'Livrée';
      default: return statut || '';
    }
  }
}
