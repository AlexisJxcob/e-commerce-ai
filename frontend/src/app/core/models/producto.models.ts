/**
 * Producto tal como lo entrega el backend.
 *
 * Sólo campos reales del contrato (`ProductoResponseDTO`). Antes este modelo
 * declaraba `marca`, `precioAnterior`, `descuentoPorcentaje`, `rating`,
 * `reviewCount`, `cuotasSinInteres` y `patrocinado`, que el servidor nunca
 * envió: se rellenaban con un PRNG en el navegador. Si el backend llega a
 * exponerlos, se agregan aquí con su origen real.
 */
export interface Producto {
  id: number;
  sku: string;
  nombre: string;
  descripcionTecnica: string;
  descripcionColoquial: string;
  precio: number;
  stock: number;
  categoriaId: number | null;
  imagenUrl?: string | null;
}

export interface ProductoRequest {
  sku: string;
  nombre: string;
  precio: number;
  stock: number;
  descripcionTecnica: string;
  descripcionColoquial: string;
  categoriaId: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
