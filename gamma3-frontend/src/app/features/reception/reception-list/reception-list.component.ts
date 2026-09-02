import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ReceptionService, BonProvisoireReception } from '../../../core/services/reception.service';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-reception-list',
  standalone: true,
  imports: [CommonModule, RouterModule, TableModule, ButtonModule, InputTextModule, TagModule],
  templateUrl: './reception-list.component.html'
})
export class ReceptionListComponent implements OnInit {
  private receptionService = inject(ReceptionService);

  bprs: BonProvisoireReception[] = [];
  loading: boolean = true;

  ngOnInit(): void {
    this.loadReceptions();
  }

  loadReceptions(): void {
    this.loading = true;
    this.receptionService.getAllBpr().subscribe({
      next: (data: BonProvisoireReception[]) => {
        this.bprs = data;
        this.loading = false;
      },
      error: (err: unknown) => {
        console.error('Erreur de chargement des réceptions', err);
        this.loading = false;
      }
    });
  }

  getStatutSeverity(statut: string | undefined): 'success' | 'secondary' | 'info' | 'warning' | 'danger' | 'contrast' | undefined {
    switch(statut) {
      case 'ATTENTE_PV': return 'warning';
      case 'VALIDE': return 'success';
      case 'REJETE': return 'danger';
      default: return 'info';
    }
  }
}
