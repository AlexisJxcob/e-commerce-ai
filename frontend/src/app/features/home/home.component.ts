import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { HeroSearchComponent } from './components/hero-search';
import { SearchSkeletonComponent } from './components/search-skeleton';
import { AsistenteService } from '../../core/services/asistente.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    HeroSearchComponent,
    SearchSkeletonComponent
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
}
