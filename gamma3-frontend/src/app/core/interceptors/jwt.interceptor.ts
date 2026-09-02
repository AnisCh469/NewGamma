import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

import { environment } from '../../../environments/environment';
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  // On injecte le token si disponible et si la requête est destinée à notre API
  if (token && (req.url.startsWith('/api/v1') || (!!environment.apiUrl && req.url.startsWith(environment.apiUrl + '/api/v1')))) {
    const authReq = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${token}`)
    });
    return next(authReq);
  }

  return next(req);
};
