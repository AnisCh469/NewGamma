import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DropdownModule } from 'primeng/dropdown';
import { TagModule } from 'primeng/tag';
import { JournalAuditService, EntreeAudit, TypeActionAudit } from '../../../core/services/journal-audit.service';

/**
 * Écran « Administration > Journal d'Audit » — traçabilité de chaque intervenant sur GAMMA 3
 * (connexions/déconnexions, création/modification/blocage de comptes, changements de
 * paramètres système…). Lecture seule, réservé ADMIN (voir JournalAuditController).
 */
@Component({
  selector: 'app-journal-audit',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, InputTextModule, DropdownModule, TagModule],
  templateUrl: './journal-audit.component.html'
})
export class JournalAuditComponent implements OnInit {
  private service = inject(JournalAuditService);

  entrees: EntreeAudit[] = [];
  totalElements = 0;
  page = 0;
  size = 25;
  isLoading = false;
  errorMessage = '';

  filtreMatricule = '';
  filtreAction: TypeActionAudit | null = null;

  actionOptions: { label: string; value: TypeActionAudit }[] = [
    { label: 'Connexion', value: 'CONNEXION' },
    { label: 'Connexion échouée', value: 'CONNEXION_ECHEC' },
    { label: 'Déconnexion manuelle', value: 'DECONNEXION_MANUELLE' },
    { label: 'Déconnexion (inactivité)', value: 'DECONNEXION_INACTIVITE' },
    { label: 'Déconnexion (fermeture)', value: 'DECONNEXION_FERMETURE' },
    { label: 'Paramètre système modifié', value: 'PARAMETRE_MODIFIE' },
    { label: 'Compte créé', value: 'COMPTE_CREE' },
    { label: 'Compte modifié', value: 'COMPTE_MODIFIE' },
    { label: 'Compte bloqué', value: 'COMPTE_BLOQUE' },
    { label: 'Compte débloqué', value: 'COMPTE_DEBLOQUE' },
    { label: 'Mot de passe réinitialisé', value: 'MOT_DE_PASSE_REINITIALISE' },
    { label: 'Sauvegarde déclenchée', value: 'SAUVEGARDE_DECLENCHEE' },
    { label: 'Sauvegarde échouée', value: 'SAUVEGARDE_ECHEC' },
  ];

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.service.lister(this.page, this.size, this.filtreMatricule || undefined, this.filtreAction || undefined)
      .subscribe({
        next: (r) => {
          this.entrees = r.content;
          this.totalElements = r.totalElements;
          this.isLoading = false;
        },
        error: (err) => {
          this.isLoading = false;
          this.errorMessage = err?.status === 403
            ? 'Réservé à un administrateur.'
            : "Impossible de charger le journal d'audit.";
        }
      });
  }

  appliquerFiltres(): void {
    this.page = 0;
    this.charger();
  }

  reinitialiserFiltres(): void {
    this.filtreMatricule = '';
    this.filtreAction = null;
    this.appliquerFiltres();
  }

  onPage(event: { first: number; rows: number }): void {
    this.page = Math.floor(event.first / event.rows);
    this.size = event.rows;
    this.charger();
  }

  severite(action: TypeActionAudit): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    if (action === 'CONNEXION') return 'success';
    if (action === 'CONNEXION_ECHEC') return 'danger';
    if (action.startsWith('DECONNEXION')) return 'secondary';
    if (action === 'COMPTE_BLOQUE') return 'danger';
    if (action === 'COMPTE_DEBLOQUE') return 'success';
    if (action === 'COMPTE_CREE') return 'success';
    if (action === 'PARAMETRE_MODIFIE' || action === 'MOT_DE_PASSE_REINITIALISE') return 'warning';
    if (action === 'SAUVEGARDE_DECLENCHEE') return 'success';
    if (action === 'SAUVEGARDE_ECHEC') return 'danger';
    return 'info';
  }

  libelle(action: TypeActionAudit): string {
    return this.actionOptions.find(o => o.value === action)?.label || action;
  }
}
