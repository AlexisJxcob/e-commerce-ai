import { Component, computed, inject, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DrawerModule } from 'primeng/drawer';
import { ButtonModule } from 'primeng/button';
import { CarritoService } from '../../../core/services/carrito.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalService } from '../../../core/auth/auth-modal.service';
import { ClpPipe } from '../../pipes/clp.pipe';

@Component({
  selector: 'app-cart-drawer',
  standalone: true,
  imports: [CommonModule, DrawerModule, ButtonModule, ClpPipe],
  templateUrl: './cart-drawer.component.html',
  styleUrl: './cart-drawer.component.scss'
})
export class CartDrawerComponent {
  readonly carritoService = inject(CarritoService);
  readonly authService = inject(AuthService);
  readonly authModalService = inject(AuthModalService);

  readonly isProcessingCheckout = signal<boolean>(false);
  readonly checkoutTriggered = output<void>();

  readonly isOpen = computed(() => this.carritoService.isOpen());
  readonly items = computed(() => this.carritoService.items());
  readonly total = computed(() => this.carritoService.total());
  readonly itemCount = computed(() => this.carritoService.itemCount());
  readonly isEmpty = computed(() => this.carritoService.isEmpty());

  onVisibleChange(visible: boolean): void {
    if (!visible) {
      this.carritoService.cerrarCarrito();
    }
  }

  incrementar(itemId: number, cantidadActual: number): void {
    this.carritoService.actualizarCantidad(itemId, cantidadActual + 1).subscribe();
  }

  decrementar(itemId: number, cantidadActual: number): void {
    this.carritoService.actualizarCantidad(itemId, cantidadActual - 1).subscribe();
  }

  eliminar(itemId: number): void {
    this.carritoService.eliminarItem(itemId).subscribe();
  }

  vaciar(): void {
    this.carritoService.vaciarCarrito().subscribe();
  }

  seguirComprando(): void {
    this.carritoService.cerrarCarrito();
  }

  onCheckout(): void {
    if (this.isEmpty() || this.isProcessingCheckout()) {
      return;
    }

    this.checkoutTriggered.emit();
  }
}
