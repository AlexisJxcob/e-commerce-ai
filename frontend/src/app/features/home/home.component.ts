import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { HeroSearchComponent } from './components/hero-search';
import { SearchSkeletonComponent } from './components/search-skeleton';
import { AiDiagnosisComponent } from './components/ai-diagnosis';
import { ProductGridComponent } from './components/product-grid';
import { AsistenteService } from '../../core/services/asistente.service';
import { CarritoService } from '../../core/services/carrito.service';
import { Producto } from '../../core/models/producto.models';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    HeroSearchComponent,
    SearchSkeletonComponent,
    AiDiagnosisComponent,
    ProductGridComponent
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent {
  readonly asistente = inject(AsistenteService);
  readonly carritoService = inject(CarritoService);

  readonly isCompact = computed(() => {
    return this.asistente.isLoading() || this.asistente.hasResult() || !!this.asistente.error();
  });

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
