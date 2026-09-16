export type EstadoPedido = 'PENDIENTE' | 'CONFIRMADO' | 'ENVIADO' | 'ENTREGADO' | 'CANCELADO';

export interface ItemPedido {
  productoId: number;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

export interface Pedido {
  id: number;
  estado: EstadoPedido;
  total: number;
  fechaCreacion: string;
  items: ItemPedido[];
  webpayToken?: string | null;
  estadoPago?: string | null;
}

export interface LineaPedido {
  productoId: number;
  cantidad: number;
}

export interface PedidoRequest {
  items: LineaPedido[];
}

export interface EstadoPedidoRequest {
  estado: EstadoPedido;
}

export interface CheckoutResponse {
  token: string;
  url: string;
}
