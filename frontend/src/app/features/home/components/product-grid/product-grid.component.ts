import { Component, computed, input, output, ChangeDetectionStrategy } from '@angular/core';

import { Producto } from '../../../../core/models/producto.models';
import { ProductCardComponent } from '../../../../shared/components/product-card';

@Component({
    selector: 'app-product-grid',
    imports: [ProductCardComponent],
    templateUrl: './product-grid.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './product-grid.component.scss'
})
export class ProductGridComponent {
  readonly productos = input<Producto[]>([]);
  readonly query = input<string>('');

  readonly addToCart = output<Producto>();

  readonly count = computed(() => this.productos().length);
  readonly hasProducts = computed(() => this.count() > 0);

  onAddToCart(producto: Producto): void {
    this.addToCart.emit(producto);
  }
}
