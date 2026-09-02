import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Stocke l'URL demandée pour y revenir après le login (optionnel)
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url }});
};
