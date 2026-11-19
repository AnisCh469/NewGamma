// Routing configuration for GAMMA 3
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  { 
    path: 'catalogue', 
    loadComponent: () => import('./features/catalogue/catalogue.component').then(m => m.CatalogueComponent),
    canActivate: [authGuard]
  },
  { 
    path: 'demander-dotation', 
    loadComponent: () => import('./features/catalogue/demander-dotation/demander-dotation.component').then(m => m.DemanderDotationComponent),
    canActivate: [authGuard]
  },
  {
    path: 'valider-demandes',
    loadComponent: () => import('./features/distribution/validation-commandant/validation-commandant.component').then(m => m.ValidationCommandantComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['UNIT_USER'], fonctionsUnite: ['COMMANDANT'] }
  },
  { 
    path: 'catalogue/:id', 
    loadComponent: () => import('./features/catalogue/item-detail/item-detail.component').then(m => m.ItemDetailComponent),
    canActivate: [authGuard]
  },
  {
    path: 'receptions',
    loadComponent: () => import('./features/reception/reception-list/reception-list.component').then(m => m.ReceptionListComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN', 'DA_MANAGER'], sectionsDA: ['SRE'], daManagerAutorise: true }
  },
  {
    path: 'receptions/:id',
    loadComponent: () => import('./features/reception/reception-detail/reception-detail.component').then(m => m.ReceptionDetailComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN', 'DA_MANAGER'], sectionsDA: ['SRE'], daManagerAutorise: true }
  },
  {
    path: 'distribution',
    loadComponent: () => import('./features/distribution/console-arbitrage/console-arbitrage.component').then(m => m.ConsoleArbitrageComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN', 'DA_MANAGER'] }
  },
  { 
    path: 'fournisseurs', 
    loadComponent: () => import('./features/tiers/fournisseurs/fournisseurs.component').then(m => m.FournisseursComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN', 'DA_MANAGER'] }
  },
  { 
    path: 'fournisseurs/:id', 
    loadComponent: () => import('./features/tiers/fournisseurs/fournisseur-detail/fournisseur-detail.component').then(m => m.FournisseurDetailComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN', 'DA_MANAGER'] }
  },
  { 
    path: 'unites', 
    loadComponent: () => import('./features/tiers/unites/unites.component').then(m => m.UnitesComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN', 'DA_MANAGER'] }
  },
  { 
    path: 'unites/:id', 
    loadComponent: () => import('./features/tiers/unites/unite-detail/unite-detail.component').then(m => m.UniteDetailComponent),
    canActivate: [authGuard, roleGuard]
  },
  {
    path: 'analytics',
    loadComponent: () => import('./features/analytics/dashboard-analytics/dashboard-analytics.component').then(m => m.DashboardAnalyticsComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN', 'DA_MANAGER'] }
  },
  {
    path: 'reformes',
    loadComponent: () => import('./features/reforme/reforme-console.component').then(m => m.ReformeConsoleComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN', 'DA_MANAGER'], sectionsDA: ['SRR'], daManagerAutorise: true }
  },
  {
    path: 'administration/utilisateurs',
    loadComponent: () => import('./features/administration/gestion-utilisateurs/gestion-utilisateurs.component').then(m => m.GestionUtilisateursComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN'] }
  },
  {
    path: 'administration/audit-migration',
    loadComponent: () => import('./features/administration/audit-migration/audit-migration.component').then(m => m.AuditMigrationComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN'] }
  },
  {
    path: 'administration/journal-audit',
    loadComponent: () => import('./features/administration/journal-audit/journal-audit.component').then(m => m.JournalAuditComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN'] }
  },
  {
    path: 'administration/sauvegardes',
    loadComponent: () => import('./features/administration/sauvegardes/sauvegardes.component').then(m => m.SauvegardesComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN'] }
  },
  {
    path: 'administration/previsions',
    loadComponent: () => import('./features/administration/previsions/previsions.component').then(m => m.PrevisionsComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN'], sectionsDA: ['SEP'], daManagerAutorise: false }
  },
  {
    path: 'administration/collationnement',
    loadComponent: () => import('./features/administration/collationnement/collationnement-liste.component').then(m => m.CollationnementListeComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN'], sectionsDA: ['SC'], daManagerAutorise: false }
  },
  {
    path: 'administration/collationnement/:id',
    loadComponent: () => import('./features/administration/collationnement/collationnement-detail.component').then(m => m.CollationnementDetailComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN'], sectionsDA: ['SC'], daManagerAutorise: false }
  }
];
