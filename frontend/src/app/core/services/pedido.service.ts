import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CheckoutResponse, Pedido } from '../models/pedido.models';

@Injectable({
  providedIn: 'root'
})
export class PedidoService {
  private readonly http = inject(HttpClient);

  crearDesdeCarrito(): Observable<Pedido> {
    return this.http.post<Pedido>('/api/v1/pedidos/desde-carrito', {});
  }

  iniciarPago(pedidoId: number): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`/api/v1/pedidos/${pedidoId}/pagar`, {});
  }

  obtenerPedido(id: number): Observable<Pedido> {
    return this.http.get<Pedido>(`/api/v1/pedidos/${id}`);
  }

  listar(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>('/api/v1/pedidos');
  }

  redirigirAWebpay(token: string, url: string): void {
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = url;

    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = 'token_ws';
    input.value = token;

    form.appendChild(input);
    document.body.appendChild(form);
    form.submit();
  }
}
