import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { PedidoService } from '../../core/services/pedido.service';
import { CarritoService } from '../../core/services/carrito.service';
import { Pedido } from '../../core/models/pedido.models';
import { ClpPipe } from '../../shared/pipes/clp.pipe';

export type PaymentStatus = 'exito' | 'rechazado' | 'cancelado' | 'error' | 'invalido';

@Component({
    selector: 'app-checkout-resultado',
    imports: [CommonModule, RouterModule, ButtonModule, CardModule, TagModule, ClpPipe],
    templateUrl: './checkout-resultado.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './checkout-resultado.component.scss'
})
export class CheckoutResultadoComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly pedidoService = inject(PedidoService);
  private readonly carritoService = inject(CarritoService);

  readonly status = signal<PaymentStatus>('invalido');
  readonly token = signal<string | null>(null);
  readonly buyOrder = signal<string | null>(null);
  readonly pedido = signal<Pedido | null>(null);
  readonly isLoading = signal<boolean>(false);
  readonly transactionDate = new Date();

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      const statusParam = (params['status'] as PaymentStatus) || 'invalido';
      const tokenParam = params['token'] || null;
      const buyOrderParam = params['buy_order'] || null;

      this.status.set(statusParam);
      this.token.set(tokenParam);
      this.buyOrder.set(buyOrderParam);

      if (statusParam === 'exito') {
        // Clear cart now that payment succeeded
        this.carritoService.vaciarCarrito().subscribe();

        if (buyOrderParam) {
          const orderId = Number(buyOrderParam);
          if (!Number.isNaN(orderId) && orderId > 0) {
            this.cargarDetallePedido(orderId);
          }
        }
      }
    });
  }

  reintentar(): void {
    this.carritoService.abrirCarrito();
  }

  private cargarDetallePedido(orderId: number): void {
    this.isLoading.set(true);
    this.pedidoService.obtenerPedido(orderId).subscribe({
      next: (order) => {
        this.pedido.set(order);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      }
    });
  }
}
