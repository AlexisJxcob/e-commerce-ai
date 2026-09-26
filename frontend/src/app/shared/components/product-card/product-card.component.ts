import { Component, computed, input, output, signal, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { Producto } from '../../../core/models/producto.models';
import { ClpPipe } from '../../pipes/clp.pipe';
import { obtenerCategoriaVisual, placaTecnica } from '../../../core/utils/product-visual.util';

@Component({
    selector: 'app-product-card',
    imports: [RouterLink, ButtonModule, ClpPipe],
    templateUrl: './product-card.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './product-card.component.scss'
})
export class ProductCardComponent {
  readonly producto = input.required<Producto>();
  readonly addToCart = output<Producto>();

  readonly hasStock = computed(() => this.producto().stock > 0);

  private readonly imageLoadFailed = signal(false);

  /**
   * Imagen real si el backend la entrega; si no, la placa técnica dibujada.
   * Nunca una foto de stock genérica que aparente ser el producto.
   */
  readonly imagen = computed(() => {
    if (this.imageLoadFailed()) {
      return placaTecnica(obtenerCategoriaVisual(this.producto()).tema);
    }
    return this.producto().imagenUrl?.trim() || placaTecnica(obtenerCategoriaVisual(this.producto()).tema);
  });

  onImageError(): void {
    this.imageLoadFailed.set(true);
  }

  onQuickBuy(event?: Event): void {
    event?.stopPropagation();
    if (this.hasStock()) {
      this.addToCart.emit(this.producto());
    }
  }
}
