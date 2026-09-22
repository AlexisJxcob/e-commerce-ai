import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeng/themes/aura';
import { definePreset } from '@primeng/themes';
import { MessageService, ConfirmationService } from 'primeng/api';

import { routes } from './app.routes';
import { authInterceptor, errorInterceptor } from './core/http';

const FerreteriaPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#eef2ff',
      100: '#e0e7ff',
      200: '#c7d2fe',
      300: '#a5b4fc',
      400: '#818cf8',
      500: '#2454ff',
      600: '#1b44dc',
      700: '#1637b8',
      800: '#132e96',
      900: '#102575',
      950: '#0a1647'
    },
    colorScheme: {
      light: {
        surface: {
          0: '#ffffff',
          50: '#fafaf8',
          100: '#f4f3ef',
          200: '#e5e3dd',
          300: '#d5d2c8',
          400: '#a8a497',
          500: '#737064',
          600: '#524f46',
          700: '#383630',
          800: '#23221e',
          900: '#141311',
          950: '#0a0a08'
        }
      }
    }
  }
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorInterceptor])),
    provideAnimationsAsync(),
    MessageService,
    ConfirmationService,
    providePrimeNG({
      theme: {
        preset: FerreteriaPreset,
        options: {
          darkModeSelector: 'none'
        }
      }
    })
  ]
};
