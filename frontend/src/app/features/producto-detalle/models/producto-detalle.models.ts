export interface ResenaProducto {
  id: number;
  autor: string;
  calificacion: number;
  fecha: string;
  comentario: string;
  compraVerificada: boolean;
}

export interface DistribucionEstrellas {
  estrellas: number;
  porcentaje: number;
  cantidad: number;
}

export interface ResumenCalificaciones {
  promedio: number;
  totalResenas: number;
  distribucion: DistribucionEstrellas[];
}

export interface CategoriaVisualInfo {
  icono: string;
  etiqueta: string;
}
