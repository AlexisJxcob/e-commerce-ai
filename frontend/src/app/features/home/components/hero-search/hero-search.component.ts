import { Component, input, output, signal, ChangeDetectionStrategy } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ChipModule } from 'primeng/chip';
import { TypewriterDirective } from '../../../../shared/directives/typewriter.directive';

export interface CasoFrecuente {
  label: string;
  icon: string;
  gremio: string;
}

@Component({
    selector: 'app-hero-search',
    imports: [
    FormsModule,
    ButtonModule,
    InputTextModule,
    ChipModule,
    TypewriterDirective
],
    templateUrl: './hero-search.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './hero-search.component.scss'
})
export class HeroSearchComponent {
  readonly loading = input<boolean>(false);
  readonly compact = input<boolean>(false);
  readonly initialQuery = input<string>('');

  readonly search = output<string>();

  readonly queryText = signal<string>('');

  readonly quickSuggestions: CasoFrecuente[] = [
    { label: 'Fuga de agua en cañería PVC', icon: 'pi-wrench', gremio: 'Plomería' },
    { label: 'Colgar repisa en tabique', icon: 'pi-hammer', gremio: 'Fijación' },
    { label: 'Cerradura trabada de puerta', icon: 'pi-lock', gremio: 'Cerrajería' },
    { label: 'Cambiar interruptor de luz', icon: 'pi-bolt', gremio: 'Electricidad' }
  ];

  onSearchSubmit(): void {
    const trimmed = this.queryText().trim();
    if (!trimmed || this.loading()) {
      return;
    }
    this.search.emit(trimmed);
  }

  selectSuggestion(suggestion: string): void {
    this.queryText.set(suggestion);
    this.search.emit(suggestion);
  }
}
