import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TypewriterDirective } from '../../../../shared/directives/typewriter.directive';

@Component({
  selector: 'app-hero-search',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    InputTextModule,
    TypewriterDirective
  ],
  templateUrl: './hero-search.component.html',
  styleUrl: './hero-search.component.scss'
})
export class HeroSearchComponent {
  readonly loading = input<boolean>(false);
  readonly compact = input<boolean>(false);
  readonly initialQuery = input<string>('');

  readonly search = output<string>();

  readonly queryText = signal<string>('');

  readonly quickSuggestions: string[] = [
    'Fuga de agua en cañería PVC',
    'Colgar repisa en tabique',
    'Cerradura trabada de puerta',
    'Cambiar interruptor de luz'
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
