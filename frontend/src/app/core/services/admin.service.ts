import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse, Producto, ProductoRequest } from '../models/producto.models';
import { Categoria, CategoriaRequest } from '../models/categoria.models';
import { ReindexacionResponse } from '../models/asistente.models';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private readonly http = inject(HttpClient);

  // --- Productos ---

  getProductos(page: number = 0, size: number = 50): Observable<PageResponse<Producto>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<Producto>>('/api/v1/productos', { params });
  }

  crearProducto(data: ProductoRequest): Observable<Producto> {
    return this.http.post<Producto>('/api/v1/productos', data);
  }

  actualizarProducto(id: number, data: ProductoRequest): Observable<Producto> {
    return this.http.put<Producto>(`/api/v1/productos/${id}`, data);
  }

  actualizarStock(id: number, stock: number): Observable<Producto> {
    const params = new HttpParams().set('stock', stock.toString());
    return this.http.patch<Producto>(`/api/v1/productos/${id}/stock`, {}, { params });
  }

  eliminarProducto(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/productos/${id}`);
  }

  // --- Categorías ---

  getCategorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>('/api/v1/categorias');
  }

  crearCategoria(data: CategoriaRequest): Observable<Categoria> {
    return this.http.post<Categoria>('/api/v1/categorias', data);
  }

  actualizarCategoria(id: number, data: CategoriaRequest): Observable<Categoria> {
    return this.http.put<Categoria>(`/api/v1/categorias/${id}`, data);
  }

  eliminarCategoria(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/categorias/${id}`);
  }

  // --- Mantenimiento & IA ---

  reindexarEmbeddings(): Observable<ReindexacionResponse> {
    return this.http.post<ReindexacionResponse>('/api/v1/productos/reindexar', {});
  }
}
