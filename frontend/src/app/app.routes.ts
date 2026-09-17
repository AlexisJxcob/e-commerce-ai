import { Routes } from '@angular/router';
import { HomeComponent } from './features/home';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent
  },
  {
    path: 'checkout/resultado',
    loadComponent: () =>
      import('./features/checkout').then((m) => m.CheckoutResultadoComponent)
  },
  {
    path: 'pedidos',
    loadComponent: () =>
      import('./features/checkout').then((m) => m.CheckoutResultadoComponent)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
