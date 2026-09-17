import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalService } from '../../../core/auth/auth-modal.service';
import { CarritoService } from '../../../core/services/carrito.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule, ButtonModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss'
})
export class HeaderComponent {
  readonly authService = inject(AuthService);
  readonly authModalService = inject(AuthModalService);
  readonly carritoService = inject(CarritoService);

  openCart(): void {
    this.carritoService.abrirCarrito();
  }

  openLogin(): void {
    this.authModalService.openLogin();
  }

  openRegister(): void {
    this.authModalService.openRegister();
  }

  logout(): void {
    this.authService.logout();
  }
}
