// Routing configuration for GAMMA 3
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

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
    path: 'catalogue/:id', 
    loadComponent: () => import('./features/catalogue/item-detail/item-detail.component').then(m => m.ItemDetailComponent),
    canActivate: [authGuard]
  },
  { 
    path: 'receptions', 
    loadComponent: () => import('./features/reception/reception-list/reception-list.component').then(m => m.ReceptionListComponent),
    canActivate: [authGuard]
  },
  { 
    path: 'receptions/:id', 
    loadComponent: () => import('./features/reception/reception-detail/reception-detail.component').then(m => m.ReceptionDetailComponent),
    canActivate: [authGuard]
  },
  { 
    path: 'distribution', 
    loadComponent: () => import('./features/distribution/console-arbitrage/console-arbitrage.component').then(m => m.ConsoleArbitrageComponent),
    canActivate: [authGuard]
  },
  { 
    path: 'fournisseurs', 
    loadComponent: () => import('./features/tiers/fournisseurs/fournisseurs.component').then(m => m.FournisseursComponent),
    canActivate: [authGuard]
  },
  { 
    path: 'fournisseurs/:id', 
    loadComponent: () => import('./features/tiers/fournisseurs/fournisseur-detail/fournisseur-detail.component').then(m => m.FournisseurDetailComponent),
    canActivate: [authGuard]
  },
  { 
    path: 'unites', 
    loadComponent: () => import('./features/tiers/unites/unites.component').then(m => m.UnitesComponent),
    canActivate: [authGuard]
  },
  { 
    path: 'unites/:id', 
    loadComponent: () => import('./features/tiers/unites/unite-detail/unite-detail.component').then(m => m.UniteDetailComponent),
    canActivate: [authGuard]
  },
  {
    path: 'analytics',
    loadComponent: () => import('./features/analytics/dashboard-analytics/dashboard-analytics.component').then(m => m.DashboardAnalyticsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'reformes',
    loadComponent: () => import('./features/reforme/reforme-console.component').then(m => m.ReformeConsoleComponent),
    canActivate: [authGuard]
  }
];
