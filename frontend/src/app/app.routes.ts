import { Routes } from '@angular/router';
import { HomeComponent } from './features/home';
import { adminGuard } from './core/auth';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent
  },
  {
    path: 'productos/:id',
    loadComponent: () =>
      import('./features/producto-detalle').then((m) => m.ProductoDetalleComponent)
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
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/admin').then((m) => m.AdminComponent)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
