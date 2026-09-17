import { Producto } from './producto.models';

export interface SugerenciaFerreteria {
  palabrasClave: string[];
  herramientas: string[];
  repuestos: string[];
}

export interface BusquedaInteligenteResponse {
  sugerencia: SugerenciaFerreteria;
  productos: Producto[];
}

export interface DiagnoseRequest {
  problema: string;
}

export interface ReindexacionResponse {
  procesados: number;
  pendientes: number;
}

