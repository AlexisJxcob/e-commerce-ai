export interface LineaCarrito {
  id: number;
  productoId: number;
  nombre: string;
  precioUnitario: number;
  cantidad: number;
  subtotal: number;
}

export interface Carrito {
  items: LineaCarrito[];
  total: number;
}

export interface AgregarItemCarrito {
  productoId: number;
  cantidad: number;
}

export interface ActualizarItemCarrito {
  cantidad: number;
}
