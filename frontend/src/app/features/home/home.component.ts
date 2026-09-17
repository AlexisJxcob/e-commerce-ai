import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { HeroSearchComponent } from './components/hero-search';
import { SearchSkeletonComponent } from './components/search-skeleton';
import { AiDiagnosisComponent } from './components/ai-diagnosis';
import { ProductGridComponent } from './components/product-grid';
import { AsistenteService } from '../../core/services/asistente.service';
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
    // Emitted from AiDiagnosisComponent; prepares kit products for cart in Block 5
    const prods = this.asistente.productos();
    if (prods.length > 0) {
      // Future integration with CarritoService in Block 5
    }
  }

  onAddToCart(producto: Producto): void {
    // Emitted from ProductGridComponent / ProductCardComponent
    // Future integration with CarritoService in Block 5
  }
}
