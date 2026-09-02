import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { ChartModule } from 'primeng/chart';
import { TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { AnalyticsService, Kpis, MagasinStat, MensuelleStat, ItemStat } from '../../../core/services/analytics.service';

@Component({
  selector: 'app-dashboard-analytics',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    CardModule,
    ButtonModule,
    ChartModule,
    TableModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './dashboard-analytics.component.html',
  styleUrls: ['./dashboard-analytics.component.css']
})
export class DashboardAnalyticsComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);
  private messageService = inject(MessageService);

  kpis: Kpis | null = null;
  loadingKpis: boolean = true;

  // Repartition Stocks charts
  stockChartData: any;
  stockChartOptions: any;
  loadingStockChart: boolean = true;

  // Monthly trends charts
  consoChartData: any;
  consoChartOptions: any;
  loadingConsoChart: boolean = true;
  selectedYear: number = new Date().getFullYear();

  // Top 5 Items list
  topItems: ItemStat[] = [];
  loadingTopItems: boolean = true;

  // Reporting parameters
  invFormat: 'pdf' | 'xlsx' = 'pdf';
  mvtFormat: 'pdf' | 'xlsx' = 'pdf';
  
  startDate: string = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().split('T')[0];
  endDate: string = new Date().toISOString().split('T')[0];

  ngOnInit(): void {
    this.loadKpis();
    this.loadStockChart();
    this.loadConsoChart();
    this.loadTopItems();
  }

  loadKpis(): void {
    this.loadingKpis = true;
    this.analyticsService.getKpis().subscribe({
      next: (data) => {
        this.kpis = data;
        this.loadingKpis = false;
      },
      error: () => {
        this.loadingKpis = false;
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de charger les indicateurs de synthèse.' });
      }
    });
  }

  loadStockChart(): void {
    this.loadingStockChart = true;
    this.analyticsService.getStockByMagasin().subscribe({
      next: (list) => {
        const labels = list.map(m => m.nom);
        const values = list.map(m => m.totalValue);

        this.stockChartData = {
          labels: labels,
          datasets: [
            {
              data: values,
              backgroundColor: [
                '#1d4ed8', // Blue
                '#0d9488', // Teal
                '#f59e0b', // Amber
                '#b91c1c'  // Red
              ],
              hoverBackgroundColor: [
                '#2563eb',
                '#0f766e',
                '#d97706',
                '#991b1b'
              ]
            }
          ]
        };

        this.stockChartOptions = {
          plugins: {
            legend: {
              labels: {
                color: '#475569',
                font: {
                  size: 11,
                  weight: 'bold'
                }
              },
              position: 'bottom'
            }
          },
          cutout: '65%'
        };

        this.loadingStockChart = false;
      },
      error: () => {
        this.loadingStockChart = false;
      }
    });
  }

  loadConsoChart(): void {
    this.loadingConsoChart = true;
    this.analyticsService.getConsommationMensuelle(this.selectedYear).subscribe({
      next: (list) => {
        const months = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Jun', 'Jul', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc'];
        const values = list.map(m => m.volume);

        this.consoChartData = {
          labels: months,
          datasets: [
            {
              label: 'Volume de Sorties de Soute',
              data: values,
              fill: true,
              borderColor: '#0284c7', // Sky Blue
              tension: 0.4,
              backgroundColor: 'rgba(2, 132, 199, 0.15)',
              borderWidth: 3
            }
          ]
        };

        this.consoChartOptions = {
          plugins: {
            legend: {
              display: false
            }
          },
          scales: {
            y: {
              beginAtZero: true,
              ticks: {
                color: '#94a3b8',
                font: {
                  size: 10
                }
              },
              grid: {
                color: '#f1f5f9'
              }
            },
            x: {
              ticks: {
                color: '#64748b',
                font: {
                  size: 10,
                  weight: 'bold'
                }
              },
              grid: {
                display: false
              }
            }
          }
        };

        this.loadingConsoChart = false;
      },
      error: () => {
        this.loadingConsoChart = false;
      }
    });
  }

  loadTopItems(): void {
    this.loadingTopItems = true;
    this.analyticsService.getTopItems().subscribe({
      next: (list) => {
        this.topItems = list;
        this.loadingTopItems = false;
      },
      error: () => {
        this.loadingTopItems = false;
      }
    });
  }

  downloadInventaire(): void {
    this.messageService.add({ severity: 'info', summary: 'Téléchargement', detail: 'Préparation du registre d\'inventaire...' });
    this.analyticsService.downloadInventaire(this.invFormat).subscribe({
      next: (blob) => {
        this.saveBlob(blob, `registre_inventaire_${new Date().toISOString().split('T')[0]}.${this.invFormat}`);
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Téléchargement du registre terminé.' });
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de générer le rapport.' });
      }
    });
  }

  downloadMouvements(): void {
    if (!this.startDate || !this.endDate) {
      this.messageService.add({ severity: 'error', summary: 'Champs Manquants', detail: 'Veuillez saisir les dates de début et de fin.' });
      return;
    }

    this.messageService.add({ severity: 'info', summary: 'Téléchargement', detail: 'Génération du rapport de mouvements...' });
    this.analyticsService.downloadMouvements(this.startDate, this.endDate, this.mvtFormat).subscribe({
      next: (blob) => {
        this.saveBlob(blob, `mouvements_${this.startDate}_to_${this.endDate}.${this.mvtFormat}`);
        this.messageService.add({ severity: 'success', summary: 'Succès', detail: 'Téléchargement du rapport terminé.' });
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible de générer le rapport de mouvements.' });
      }
    });
  }

  private saveBlob(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }
}
