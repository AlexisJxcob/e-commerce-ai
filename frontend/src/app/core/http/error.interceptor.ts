import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { AuthModalService } from '../auth/auth-modal.service';
import { ErrorResponse } from '../models/error.models';

/**
 * Uniform error interceptor mapping Spring Boot 4 / GlobalExceptionHandler
 * error bodies (400, 401, 403, 404, 409) and triggering authentication workflow on 401.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const authModalService = inject(AuthModalService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const errorBody: ErrorResponse | undefined = error.error;

        switch (error.status) {
          case 401: {
            // If an authenticated request receives 401 (token expired or revoked),
            // clean session and prompt user with auth modal without reloading page.
            if (!req.url.includes('/api/auth/login')) {
              authService.logout();
              authModalService.openLogin();
            }
            break;
          }
          case 403: {
            console.warn('[Repara.ai Security] Access forbidden for resource:', req.url);
            break;
          }
          case 404: {
            console.warn('[Repara.ai API] Resource not found (404):', req.url, errorBody?.message);
            break;
          }
          case 409: {
            console.warn('[Repara.ai API] Business conflict (409):', errorBody?.message);
            break;
          }
          case 400: {
            console.warn('[Repara.ai API] Bad request (400):', errorBody?.message, errorBody?.fieldErrors);
            break;
          }
          default: {
            console.error('[Repara.ai API] Unexpected error:', error.status, error.message);
            break;
          }
        }
      }

      return throwError(() => error);
    })
  );
};
