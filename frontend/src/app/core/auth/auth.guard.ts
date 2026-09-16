import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthModalService } from './auth-modal.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const authModalService = inject(AuthModalService);

  if (authService.isAuthenticated()) {
    return true;
  }

  authModalService.openLogin();
  return false;
};
