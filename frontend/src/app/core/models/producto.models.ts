export interface Producto {
  id: number;
  sku: string;
  nombre: string;
  descripcionTecnica: string;
  descripcionColoquial: string;
  precio: number;
  stock: number;
  categoriaId: number | null;
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
