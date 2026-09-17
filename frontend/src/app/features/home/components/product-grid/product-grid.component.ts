import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Producto } from '../../../../core/models/producto.models';
import { ProductCardComponent } from '../../../../shared/components/product-card';

@Component({
  selector: 'app-product-grid',
  standalone: true,
  imports: [CommonModule, ProductCardComponent],
  templateUrl: './product-grid.component.html',
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
