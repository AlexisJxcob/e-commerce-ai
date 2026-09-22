import { Component, computed, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';

import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { HeroSearchComponent } from './components/hero-search';
import { SearchSkeletonComponent } from './components/search-skeleton';
import { AiDiagnosisComponent } from './components/ai-diagnosis';
import { ProductGridComponent } from './components/product-grid';
import { CategoryCarouselComponent } from './components/category-carousel';
import { AsistenteService } from '../../core/services/asistente.service';
import { CarritoService } from '../../core/services/carrito.service';
import { CatalogoService } from '../../core/services/catalogo.service';
import { CategoriaConProductos } from '../../core/models/categoria.models';
import { Producto } from '../../core/models/producto.models';

@Component({
    selector: 'app-home',
    imports: [
    ButtonModule,
    SkeletonModule,
    HeroSearchComponent,
    SearchSkeletonComponent,
    AiDiagnosisComponent,
    ProductGridComponent,
    CategoryCarouselComponent
],
    templateUrl: './home.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  readonly asistente = inject(AsistenteService);
  readonly carritoService = inject(CarritoService);
  readonly catalogoService = inject(CatalogoService);

  readonly catalogo = signal<CategoriaConProductos[]>([]);
  readonly isCatalogoLoading = signal<boolean>(true);
  readonly catalogoError = signal<string | null>(null);

  readonly isCompact = computed(() => {
    return this.asistente.isLoading() || this.asistente.hasResult() || !!this.asistente.error();
  });

  ngOnInit(): void {
    this.cargarCatalogo();
  }

  cargarCatalogo(): void {
    this.isCatalogoLoading.set(true);
    this.catalogoError.set(null);

    this.catalogoService.getCatalogoAgrupado().subscribe({
      next: (grupos) => {
        this.catalogo.set(grupos);
        this.isCatalogoLoading.set(false);
      },
      error: (err) => {
        console.error('Error al cargar catálogo:', err);
        this.catalogoError.set('No fue posible cargar el catálogo de productos.');
        this.isCatalogoLoading.set(false);
      }
    });
  }

  onSearch(query: string): void {
    this.asistente.buscar(query).subscribe({
      error: () => {
        // Handled in service error signal
      }
    });
  }

  retry(): void {
    const query = this.asistente.currentQuery();
    if (query) {
      this.onSearch(query);
    }
  }

  reset(): void {
    this.asistente.limpiar();
  }

  onAddKit(): void {
    const prods = this.asistente.productos();
    const available = prods.filter((p) => p.stock > 0);
    if (available.length > 0) {
      available.forEach((prod) => {
        this.carritoService.agregarProducto(prod, 1);
      });
      this.carritoService.abrirCarrito();
    }
  }

  onAddToCart(producto: Producto): void {
    this.carritoService.agregarProducto(producto, 1);
  }
}
