import { Component, computed, inject, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
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
  private readonly router = inject(Router);

  readonly producto = input.required<Producto>();
  readonly addToCart = output<Producto>();

  readonly hasStock = computed(() => this.producto().stock > 0);

  onCardClick(): void {
    this.router.navigate(['/productos', this.producto().id]);
  }

  onCardKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.onCardClick();
    }
  }

  onQuickBuy(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    if (this.hasStock()) {
      this.addToCart.emit(this.producto());
    }
  }
}

