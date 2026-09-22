import { Component, inject, ChangeDetectionStrategy, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { BadgeModule } from 'primeng/badge';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalService } from '../../../core/auth/auth-modal.service';
import { CarritoService } from '../../../core/services/carrito.service';

@Component({
  selector: 'app-header',
  imports: [RouterModule, ButtonModule, BadgeModule],
  templateUrl: './header.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './header.component.scss'
})
export class HeaderComponent {
  readonly authService = inject(AuthService);
  readonly authModalService = inject(AuthModalService);
  readonly carritoService = inject(CarritoService);

  readonly cartBadgeValue = computed(() => {
    const count = this.carritoService.itemCount();
    return count > 0 ? count.toString() : undefined;
  });

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
