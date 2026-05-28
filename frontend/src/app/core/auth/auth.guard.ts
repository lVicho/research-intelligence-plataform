import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';

import { RoleCode } from '../api/api-models';
import { AuthStateService } from './auth-state.service';

export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthStateService);
  const router = inject(Router);
  const requiredRoles = (route.data['roles'] as RoleCode[] | undefined) ?? [];

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  if (!auth.hasAnyRole(requiredRoles)) {
    return router.createUrlTree(['/portal']);
  }

  return true;
};
