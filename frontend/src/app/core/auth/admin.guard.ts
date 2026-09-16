import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthModalService } from './auth-modal.service';

export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const authModalService = inject(AuthModalService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    authModalService.openLogin();
    return false;
  }

  if (authService.isAdmin()) {
    return true;
  }

  router.navigate(['/']);
  return false;
};
