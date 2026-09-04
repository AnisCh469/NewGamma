import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

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
  imports: [CommonModule, ButtonModule, TableModule, TagModule],
  templateUrl: './audit-migration.component.html',
  styleUrl: './audit-migration.component.css'
})
export class AuditMigrationComponent implements OnInit {
  report: AuditReport | null = null;
  isLoading = false;
  errorMessage = '';

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
}
