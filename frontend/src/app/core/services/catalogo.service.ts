import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { forkJoin, map, Observable } from 'rxjs';
import { Categoria, CategoriaConProductos } from '../models/categoria.models';
import { PageResponse, Producto } from '../models/producto.models';

@Injectable({
  providedIn: 'root'
})
export class CatalogoService {
  private readonly http = inject(HttpClient);

  getCategorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>('/api/v1/categorias');
  }

  getProductos(page: number = 0, size: number = 100): Observable<PageResponse<Producto>> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<PageResponse<Producto>>('/api/v1/productos', { params });
  }

  getProductoById(id: number): Observable<Producto> {
    return this.http.get<Producto>(`/api/v1/productos/${id}`);
  }

  getProductosPorCategoria(categoriaId: number, limite: number = 8): Observable<Producto[]> {
    return this.getProductos(0, 100).pipe(
      map((page) => (page.content ?? []).filter((p) => p.categoriaId === categoriaId).slice(0, limite))
    );
  }

  getCatalogoAgrupado(): Observable<CategoriaConProductos[]> {
    return forkJoin({
      categorias: this.getCategorias(),
      productosPage: this.getProductos(0, 100)
    }).pipe(
      map(({ categorias, productosPage }) => {
        const productos = productosPage.content ?? [];
        const resultado: CategoriaConProductos[] = [];
        const productosPorCategoria = new Map<number, Producto[]>();
        const productosSinCategoria: Producto[] = [];

        for (const p of productos) {
          if (p.categoriaId != null) {
            const list = productosPorCategoria.get(p.categoriaId) ?? [];
            list.push(p);
            productosPorCategoria.set(p.categoriaId, list);
          } else {
            productosSinCategoria.push(p);
          }
        }

        // Add categories that have products
        for (const cat of categorias) {
          const prods = productosPorCategoria.get(cat.id) ?? [];
          if (prods.length > 0) {
            // Check if it's the seed "Sin categoría"
            const normalizado = cat.nombre.trim().toLowerCase();
            if (normalizado === 'sin categoría' || normalizado === 'sin categoria') {
              productosSinCategoria.push(...prods);
            } else {
              resultado.push({
                categoria: cat,
                productos: prods
              });
            }
          }
        }

        // Add unassigned products as "Otros productos" at the end
        if (productosSinCategoria.length > 0) {
          resultado.push({
            categoria: {
              id: -1,
              nombre: 'Otros productos',
              descripcion: 'Herramientas y repuestos generales'
            },
            productos: productosSinCategoria
          });
        }

        return resultado;
      })
    );
  }
}
