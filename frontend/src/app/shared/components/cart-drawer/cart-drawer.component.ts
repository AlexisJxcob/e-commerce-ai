import { Component, computed, inject, output, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DrawerModule } from 'primeng/drawer';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { ConfirmationService, MessageService } from 'primeng/api';
import { CarritoService } from '../../../core/services/carrito.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalService } from '../../../core/auth/auth-modal.service';
import { PedidoService } from '../../../core/services/pedido.service';
import { ClpPipe } from '../../pipes/clp.pipe';

@Component({
  selector: 'app-cart-drawer',
  imports: [DrawerModule, ButtonModule, InputNumberModule, FormsModule, ClpPipe],
  templateUrl: './cart-drawer.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cart-drawer.component.scss'
})
export class CartDrawerComponent {
  private readonly router = inject(Router);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);

  readonly carritoService = inject(CarritoService);
  readonly authService = inject(AuthService);
  readonly authModalService = inject(AuthModalService);
  readonly pedidoService = inject(PedidoService);

  readonly isProcessingCheckout = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly checkoutTriggered = output<void>();

  readonly isOpen = computed(() => this.carritoService.isOpen());
  readonly items = computed(() => this.carritoService.items());
  readonly total = computed(() => this.carritoService.total());
  readonly itemCount = computed(() => this.carritoService.itemCount());
  readonly isEmpty = computed(() => this.carritoService.isEmpty());

  onVisibleChange(visible: boolean): void {
    if (!visible) {
      this.carritoService.cerrarCarrito();
      this.errorMessage.set(null);
    }
  }

  onCantidadChange(itemId: number, cantidad: number | null): void {
    if (cantidad !== null && cantidad > 0) {
      this.carritoService.actualizarCantidad(itemId, cantidad).subscribe();
    }
  }

  incrementar(itemId: number, cantidadActual: number): void {
    this.carritoService.actualizarCantidad(itemId, cantidadActual + 1).subscribe();
  }

  decrementar(itemId: number, cantidadActual: number): void {
    this.carritoService.actualizarCantidad(itemId, cantidadActual - 1).subscribe();
  }

  eliminar(itemId: number): void {
    this.carritoService.eliminarItem(itemId).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'info',
          summary: 'Producto eliminado',
          detail: 'El producto se quitó del carrito.',
          life: 2500
        });
      }
    });
  }

  confirmarVaciar(): void {
    this.confirmationService.confirm({
      message: '¿Estás seguro de que deseas vaciar todos los productos del carrito?',
      header: 'Vaciar Carrito',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Sí, vaciar',
      rejectLabel: 'Cancelar',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-sm',
      accept: () => {
        this.vaciar();
      }
    });
  }

  vaciar(): void {
    this.carritoService.vaciarCarrito().subscribe({
      next: () => {
        this.messageService.add({
          severity: 'info',
          summary: 'Carrito vacío',
          detail: 'Se eliminaron todos los productos de tu carrito.',
          life: 2500
        });
      }
    });
  }

  seguirComprando(): void {
    this.carritoService.cerrarCarrito();
  }

  onCheckout(): void {
    if (this.isEmpty() || this.isProcessingCheckout()) {
      return;
    }

    this.checkoutTriggered.emit();
    this.carritoService.cerrarCarrito();
    this.router.navigate(['/checkout']);
  }

  procederCheckout(): void {
    this.isProcessingCheckout.set(true);
    this.errorMessage.set(null);

    this.carritoService.sincronizarCarritoInvitado().subscribe({
      next: () => {
        this.pedidoService.crearDesdeCarrito().subscribe({
          next: (pedido) => {
            this.pedidoService.iniciarPago(pedido.id).subscribe({
              next: (checkout) => {
                this.pedidoService.redirigirAWebpay(checkout.token, checkout.url);
              },
              error: () => {
                this.isProcessingCheckout.set(false);
                this.errorMessage.set('Error al conectar con la pasarela Webpay. Intenta nuevamente.');
                this.messageService.add({
                  severity: 'error',
                  summary: 'Error de pasarela',
                  detail: 'No se pudo conectar con Webpay. Intenta nuevamente.'
                });
              }
            });
          },
          error: (err) => {
            this.isProcessingCheckout.set(false);
            const msg = err?.error?.message ?? 'No se pudo generar el pedido desde el carrito.';
            this.errorMessage.set(msg);
            this.messageService.add({
              severity: 'error',
              summary: 'Error al generar pedido',
              detail: msg
            });
          }
        });
      },
      error: () => {
        this.isProcessingCheckout.set(false);
        this.errorMessage.set('Error al sincronizar el carrito antes del pago.');
        this.messageService.add({
          severity: 'error',
          summary: 'Error de sincronización',
          detail: 'No se pudo sincronizar el carrito.'
        });
      }
    });
  }
}
