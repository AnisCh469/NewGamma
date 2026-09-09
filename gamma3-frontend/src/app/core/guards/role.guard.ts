import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/**
 * RÔLE : garde de route par rôle, en complément de `authGuard` (qui ne vérifie QUE
 * l'authentification). À utiliser sur les écrans réservés à certains rôles.
 *
 * POURQUOI (audit 2026-09-09) : les routes sensibles (console d'arbitrage, administration,
 * validation Commandant) n'étaient protégées que par `authGuard`. Un compte UNIT_USER
 * pouvait donc atteindre la console d'arbitrage en tapant l'URL `/distribution` — et,
 * comme le backend ne contrôlait pas non plus le rôle sur `POST /demandes/{id}/arbitrer`,
 * auto-approuver ses propres demandes. Le backend est désormais verrouillé ; cette garde
 * ferme la porte aussi côté navigation pour éviter d'afficher un écran non pertinent.
 *
 * USAGE dans app.routes.ts :
 *   { path: 'distribution', ...,
 *     canActivate: [authGuard, roleGuard],
 *     data: { roles: ['ADMIN', 'DA_MANAGER'] } }
 *
 * Un objet `data.fonctionsUnite` optionnel permet de restreindre en plus par fonction
 * d'unité (GESTIONNAIRE / COMMANDANT) pour les comptes UNIT_USER.
 */
export const roleGuard: CanActivateFn = (route, _state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.currentUser();
  if (!user) {
    return router.createUrlTree(['/login']);
  }

  const rolesAutorises: string[] | undefined = route.data?.['roles'];
  if (rolesAutorises && rolesAutorises.length > 0 && !rolesAutorises.includes(user.role)) {
    // Rôle non autorisé : on renvoie vers un écran neutre plutôt que d'afficher la page.
    return router.createUrlTree(['/catalogue']);
  }

  const fonctionsAutorisees: string[] | undefined = route.data?.['fonctionsUnite'];
  if (
    fonctionsAutorisees &&
    fonctionsAutorisees.length > 0 &&
    user.role === 'UNIT_USER' &&
    (!user.fonctionUnite || !fonctionsAutorisees.includes(user.fonctionUnite))
  ) {
    return router.createUrlTree(['/catalogue']);
  }

  return true;
};
