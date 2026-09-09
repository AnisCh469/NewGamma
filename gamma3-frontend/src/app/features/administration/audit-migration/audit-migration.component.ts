import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';

import { MigrationAuditService, AuditReport } from '../../../core/services/migration-audit.service';

/**
 * Écran d'audit "Gamma2 → Gamma3" (Administration > Audit Migration).
 *
 * Contexte : la reprise de données depuis l'ancien système Gamma2 (SQL Server) n'a jamais
 * été un processus unique et documenté — voir LegacyAuditController (backend) pour le
 * détail des deux seuls mécanismes connus (Item via pgloader, Plan d'Armement via un
 * endpoint rejouable). Cet écran interroge en direct la base SQL Server Gamma2 (via la
 * connexion déjà configurée du backend) pour lister TOUTES ses tables et leur nombre de
 * lignes, à comparer avec les compteurs actuels de Gamma3 ci-dessous, afin d'identifier
 * les données jamais reprises (mouvements historiques, anciens bons, etc.).
 */
@Component({
  selector: 'app-audit-migration',
  standalone: true,
  imports: [CommonModule, ButtonModule, TableModule, TagModule, ToastModule],
  providers: [MessageService],
  templateUrl: './audit-migration.component.html',
  styleUrl: './audit-migration.component.css'
})
export class AuditMigrationComponent implements OnInit {
  report: AuditReport | null = null;
  isLoading = false;
  errorMessage = '';
  migratingMouvements = false;
  migratingPlan = false;

  private messageService = inject(MessageService);

  constructor(private auditService: MigrationAuditService) {}

  ngOnInit(): void {
    this.runAudit();
  }

  runAudit(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.auditService.getAudit().subscribe({
      next: (data) => {
        this.report = data;
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || err?.message || "Erreur lors de l'audit.";
      }
    });
  }

  get gamma3CountsEntries(): { key: string; value: number }[] {
    if (!this.report) return [];
    return Object.entries(this.report.gamma3Counts).map(([key, value]) => ({ key, value }));
  }

  /**
   * Rapatrie l'HISTORIQUE des mouvements de stock de Gamma 2 dans la vue « Historique » de
   * la fiche article. Opération d'ARCHIVE : ne modifie pas les niveaux de stock actuels.
   * Idempotente : ré-exécutable sans dupliquer (les lignes G2- sont purgées puis réinjectées).
   */
  migrerMouvements(): void {
    if (!confirm(
      "Rapatrier l'historique des mouvements de Gamma 2 ?\n\n" +
      "• ~160 000 lignes, environ 30 secondes.\n" +
      "• N'affecte PAS les stocks actuels (archives uniquement).\n" +
      "• Ré-exécutable sans risque.")) return;

    this.migratingMouvements = true;
    this.auditService.migrerMouvements().subscribe({
      next: (r) => {
        this.migratingMouvements = false;
        this.messageService.add({
          severity: 'success', life: 8000,
          summary: 'Historique importé',
          detail: `${r.mouvementsInseres.toLocaleString('fr')} mouvements insérés ` +
                  `(${r.ignoresArticleNonMigre} lignes ignorées : article non migré) en ${(r.dureeMs / 1000).toFixed(0)} s.`
        });
        this.runAudit();
      },
      error: (err) => {
        this.migratingMouvements = false;
        this.messageService.add({ severity: 'error', summary: 'Échec', detail: err?.error || err?.message || 'Erreur inconnue.' });
      }
    });
  }

  migrerPlanArmement(): void {
    if (!confirm("Recharger le Plan d'Armement depuis Gamma 2 ?\n\nCela REMPLACE l'intégralité des plans d'armement actuels.")) return;
    this.migratingPlan = true;
    this.auditService.migrerPlanArmement().subscribe({
      next: (msg) => {
        this.migratingPlan = false;
        this.messageService.add({ severity: 'success', summary: 'Plan d\'Armement rechargé', detail: msg, life: 8000 });
        this.runAudit();
      },
      error: (err) => {
        this.migratingPlan = false;
        this.messageService.add({ severity: 'error', summary: 'Échec', detail: err?.error || err?.message || 'Erreur inconnue.' });
      }
    });
  }
}
