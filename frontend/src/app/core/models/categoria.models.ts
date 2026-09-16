export interface Categoria {
  id: number;
  nombre: string;
  descripcion?: string | null;
  padreId?: number | null;
}

export interface CategoriaRequest {
  nombre: string;
  descripcion?: string | null;
  padreId?: number | null;
}
