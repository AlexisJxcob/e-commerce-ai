import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { Producto } from '../../../core/models/producto.models';
import { ClpPipe } from '../../pipes/clp.pipe';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, ButtonModule, ClpPipe],
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.scss'
})
export class ProductCardComponent {
  readonly producto = input.required<Producto>();
  readonly addToCart = output<Producto>();

  readonly hasStock = computed(() => this.producto().stock > 0);

  onQuickBuy(): void {
    if (this.hasStock()) {
      this.addToCart.emit(this.producto());
    }
  }
}
