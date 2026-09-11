import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { SauvegardeService, Sauvegarde } from '../../../core/services/sauvegarde.service';
import { ParametreService } from '../../../core/services/parametre.service';

/**
 * Écran « Administration > Sauvegardes » — Phase 2 de la gouvernance des rôles (2026-09-11,
 * demande explicite de l'utilisateur, voir docs/PROPOSITION_GOUVERNANCE_ROLES.md §4.1).
 * Réservé SUPER_ADMIN (voir SauvegardeController). Déclenche un dump côté serveur et affiche
 * la liste des sauvegardes déjà produites — AUCUN bouton de téléchargement : le fichier reste
 * strictement sur le serveur (voir la javadoc de SauvegardeService pour le raisonnement).
 */
@Component({
  selector: 'app-sauvegardes',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, InputNumberModule, ToastModule],
  providers: [MessageService],
  templateUrl: './sauvegardes.component.html'
})
export class SauvegardesComponent implements OnInit {
  private service = inject(SauvegardeService);
  private parametreService = inject(ParametreService);
  private messageService = inject(MessageService);

  sauvegardes: Sauvegarde[] = [];
  isLoading = false;
  isDeclenchement = false;
  errorMessage = '';

  retention: number | null = null;
  retentionLoading = false;

  ngOnInit(): void {
    this.charger();
    this.chargerRetention();
  }

  charger(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.service.lister().subscribe({
      next: (liste) => { this.sauvegardes = liste; this.isLoading = false; },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.status === 403
          ? 'Réservé à l\'administrateur système.'
          : 'Impossible de charger la liste des sauvegardes.';
      }
    });
  }

  chargerRetention(): void {
    this.parametreService.getRetentionSauvegardes().subscribe({
      next: (r) => { this.retention = r.retentionSauvegardes; },
      error: () => { /* champ laissé vide, non bloquant */ }
    });
  }

  enregistrerRetention(): void {
    if (this.retention === null) return;
    this.retentionLoading = true;
    this.parametreService.setRetentionSauvegardes(this.retention).subscribe({
      next: (r) => {
        this.retentionLoading = false;
        this.retention = r.retentionSauvegardes;
        this.messageService.add({ severity: 'success', summary: 'Rétention mise à jour', detail: `${r.retentionSauvegardes} sauvegarde(s) conservée(s).` });
      },
      error: (err) => {
        this.retentionLoading = false;
        this.messageService.add({ severity: 'error', summary: 'Échec', detail: typeof err?.error === 'string' ? err.error : 'Réglage impossible.' });
      }
    });
  }

  declencher(): void {
    this.isDeclenchement = true;
    this.service.declencher().subscribe({
      next: (s) => {
        this.isDeclenchement = false;
        this.messageService.add({ severity: 'success', summary: 'Sauvegarde terminée', detail: `${s.nomFichier} (${this.formatTaille(s.tailleOctets)})` });
        this.charger();
      },
      error: (err) => {
        this.isDeclenchement = false;
        this.messageService.add({
          severity: 'error', summary: 'Échec de la sauvegarde',
          detail: typeof err?.error === 'string' ? err.error : 'La sauvegarde a échoué — voir les logs serveur.',
          life: 8000
        });
      }
    });
  }

  formatTaille(octets: number): string {
    if (octets < 1024) return `${octets} o`;
    if (octets < 1024 * 1024) return `${(octets / 1024).toFixed(1)} Ko`;
    return `${(octets / (1024 * 1024)).toFixed(1)} Mo`;
  }
}
