import { Component, OnDestroy, computed, effect, inject, input, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { InputNumberModule } from 'primeng/inputnumber';
import { Producto } from '../../core/models/producto.models';
import { CatalogoService } from '../../core/services/catalogo.service';
import { CarritoService } from '../../core/services/carrito.service';
import { ClpPipe } from '../../shared/pipes/clp.pipe';
import { ProductCardComponent } from '../../shared/components/product-card/product-card.component';
import {
  CategoriaVisual,
  obtenerCategoriaVisual,
  placaTecnica,
  PlateTheme
} from '../../core/utils/product-visual.util';

@Component({
    selector: 'app-producto-detalle',
    imports: [
      CommonModule,
      FormsModule,
      ButtonModule,
      SkeletonModule,
      InputNumberModule,
      ClpPipe,
      ProductCardComponent
    ],
    templateUrl: './producto-detalle.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './producto-detalle.component.scss'
})
export class ProductoDetalleComponent implements OnDestroy {
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
  readonly addError = signal<string | null>(null);

  readonly productosRelacionados = signal<Producto[]>([]);

  readonly hasStock = computed(() => (this.producto()?.stock ?? 0) > 0);
  readonly stockDisponible = computed(() => this.producto()?.stock ?? 0);

  /** Texto que anuncia el estado real a lectores de pantalla. */
  readonly estadoAnunciado = computed(() => {
    if (this.isLoading()) return 'Cargando ficha del producto';
    if (this.error()) return `Error: ${this.error()}`;
    const prod = this.producto();
    return prod ? `Ficha de ${prod.nombre} cargada` : '';
  });

  readonly visualInfo = computed<CategoriaVisual>(() => {
    const prod = this.producto();
    return prod
      ? obtenerCategoriaVisual(prod)
      : { icono: 'pi pi-box', etiqueta: 'Ferretería y repuestos', tema: 'general' as PlateTheme };
  });

  private readonly imageLoadFailed = signal<boolean>(false);

  readonly imagen = computed<string>(() => {
    const prod = this.producto();
    if (!prod) return '';
    if (this.imageLoadFailed()) {
      return placaTecnica(obtenerCategoriaVisual(prod).tema);
    }
    return prod.imagenUrl?.trim() || placaTecnica(obtenerCategoriaVisual(prod).tema);
  });

  private feedbackTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      const numId = Number(this.id());
      if (!Number.isNaN(numId) && numId > 0) {
        this.cargarProducto(numId);
      } else {
        this.error.set('El identificador del producto no es válido.');
        this.isLoading.set(false);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.feedbackTimer) {
      clearTimeout(this.feedbackTimer);
    }
  }

  onHeroImageError(): void {
    // Sólo afecta a la imagen actual: se resetea al cargar otro producto.
    this.imageLoadFailed.set(true);
  }

  cargarProducto(id: number): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.cantidad.set(1);
    this.addedSuccess.set(false);
    this.addError.set(null);
    // Sin esto, una foto fallida contaminaba el siguiente producto.
    this.imageLoadFailed.set(false);
    this.productosRelacionados.set([]);

    this.catalogoService.getProductoById(id).subscribe({
      next: (prod) => {
        this.producto.set(prod);
        this.isLoading.set(false);
        if (prod.categoriaId != null) {
          this.cargarProductosRelacionados(prod.categoriaId, prod.id);
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        this.error.set(
          err?.status === 404
            ? 'El producto solicitado no existe en el catálogo.'
            : 'Ocurrió un error al cargar la información del producto.'
        );
      }
    });
  }

  cargarProductosRelacionados(categoriaId: number, currentProductId: number): void {
    this.catalogoService.getProductosPorCategoria(categoriaId, 8).subscribe({
      next: (prods) => {
        this.productosRelacionados.set(prods.filter((p) => p.id !== currentProductId).slice(0, 4));
      },
      error: () => {
        // Los relacionados son un extra: su fallo no rompe la ficha.
        this.productosRelacionados.set([]);
      }
    });
  }

  addToCart(): void {
    const prod = this.producto();
    if (!prod || !this.hasStock() || this.isAddingToCart()) {
      return;
    }

    this.isAddingToCart.set(true);
    this.addError.set(null);
    this.carritoService.agregarProducto(prod, this.cantidad()).subscribe({
      next: () => {
        this.isAddingToCart.set(false);
        this.addedSuccess.set(true);
        this.feedbackTimer = setTimeout(() => this.addedSuccess.set(false), 3000);
      },
      error: () => {
        this.isAddingToCart.set(false);
        this.addError.set('No se pudo agregar el producto al carro. Intenta nuevamente.');
      }
    });
  }

  onAddToCartRelacionado(prod: Producto): void {
    this.carritoService.agregarProducto(prod, 1).subscribe({
      error: () => {
        this.addError.set('No se pudo agregar el producto relacionado al carro.');
      }
    });
  }

  volverAlCatalogo(): void {
    this.router.navigate(['/']);
  }
}
