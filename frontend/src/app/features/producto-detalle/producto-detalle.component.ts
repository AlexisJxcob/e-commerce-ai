import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { Producto } from '../../core/models/producto.models';
import { CatalogoService } from '../../core/services/catalogo.service';
import { CarritoService } from '../../core/services/carrito.service';
import { ClpPipe } from '../../shared/pipes/clp.pipe';
import { ProductCardComponent } from '../../shared/components/product-card/product-card.component';
import {
  generarResenas,
  generarResumenCalificaciones,
  obtenerIconoVisual
} from './utils/reviews-generator.util';
import { getProductPresentation, ProductPresentation } from '../../core/utils/product-presentation.util';

@Component({
  selector: 'app-producto-detalle',
  standalone: true,
  imports: [CommonModule, RouterLink, ButtonModule, ClpPipe, ProductCardComponent],
  templateUrl: './producto-detalle.component.html',
  styleUrl: './producto-detalle.component.scss'
})
export class ProductoDetalleComponent {
  protected readonly Math = Math;
  private readonly catalogoService = inject(CatalogoService);
  private readonly carritoService = inject(CarritoService);
  private readonly router = inject(Router);

  readonly id = input.required<string>();

  readonly producto = signal<Producto | null>(null);
  readonly isLoading = signal<boolean>(true);
  readonly error = signal<string | null>(null);

  readonly cantidad = signal<number>(1);
  readonly isAddingToCart = signal<boolean>(false);
  readonly addedSuccess = signal<boolean>(false);

  readonly productosRelacionados = signal<Producto[]>([]);
  readonly isLoadingRelacionados = signal<boolean>(false);

  readonly hasStock = computed(() => (this.producto()?.stock ?? 0) > 0);
  readonly stockDisponible = computed(() => this.producto()?.stock ?? 0);

  readonly resenas = computed(() => {
    const prod = this.producto();
    return prod ? generarResenas(prod) : [];
  });

  readonly resumenCalificaciones = computed(() => {
    const prod = this.producto();
    return prod ? generarResumenCalificaciones(prod) : null;
  });

  readonly visualInfo = computed(() => {
    const prod = this.producto();
    return prod ? obtenerIconoVisual(prod) : { icono: 'pi pi-box', etiqueta: 'Ferretería & Repuestos' };
  });

  readonly presentation = computed<ProductPresentation | null>(() => {
    const prod = this.producto();
    return prod ? getProductPresentation(prod) : null;
  });

  readonly imageLoadFailed = signal<boolean>(false);

  readonly currentHeroImage = computed<string>(() => {
    const pres = this.presentation();
    if (!pres) return '';
    return this.imageLoadFailed() ? pres.fallbackSvg : pres.imagenUrl;
  });

  onHeroImageError(): void {
    this.imageLoadFailed.set(true);
  }

  constructor() {
    effect(
      () => {
        const rawId = this.id();
        const numId = Number(rawId);
        if (!isNaN(numId) && numId > 0) {
          this.cargarProducto(numId);
        } else {
          this.error.set('El identificador del producto no es válido.');
          this.isLoading.set(false);
        }
      },
      { allowSignalWrites: true }
    );
  }

  cargarProducto(id: number): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.cantidad.set(1);
    this.addedSuccess.set(false);

    this.catalogoService.getProductoById(id).subscribe({
      next: (prod) => {
        this.producto.set(prod);
        this.isLoading.set(false);
        if (prod.categoriaId != null) {
          this.cargarProductosRelacionados(prod.categoriaId, prod.id);
        } else {
          this.productosRelacionados.set([]);
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        if (err.status === 404) {
          this.error.set('El producto solicitado no fue encontrado en el catálogo.');
        } else {
          this.error.set('Ocurrió un error al cargar la información del producto.');
        }
      }
    });
  }

  cargarProductosRelacionados(categoriaId: number, currentProductId: number): void {
    this.isLoadingRelacionados.set(true);
    this.catalogoService.getProductosPorCategoria(categoriaId, 8).subscribe({
      next: (prods) => {
        this.productosRelacionados.set(
          prods.filter((p) => p.id !== currentProductId).slice(0, 4)
        );
        this.isLoadingRelacionados.set(false);
      },
      error: () => {
        this.productosRelacionados.set([]);
        this.isLoadingRelacionados.set(false);
      }
    });
  }

  incrementQuantity(): void {
    const max = this.stockDisponible();
    if (this.cantidad() < max) {
      this.cantidad.update((q) => q + 1);
    }
  }

  decrementQuantity(): void {
    if (this.cantidad() > 1) {
      this.cantidad.update((q) => q - 1);
    }
  }

  addToCart(): void {
    const prod = this.producto();
    if (!prod || !this.hasStock()) {
      return;
    }

    this.isAddingToCart.set(true);
    this.carritoService.agregarProducto(prod, this.cantidad()).subscribe({
      next: () => {
        this.isAddingToCart.set(false);
        this.addedSuccess.set(true);
        setTimeout(() => this.addedSuccess.set(false), 3000);
      },
      error: () => {
        this.isAddingToCart.set(false);
      }
    });
  }

  onAddToCartRelacionado(prod: Producto): void {
    this.carritoService.agregarProducto(prod, 1).subscribe();
  }

  volverAlCatalogo(): void {
    this.router.navigate(['/']);
  }
}
