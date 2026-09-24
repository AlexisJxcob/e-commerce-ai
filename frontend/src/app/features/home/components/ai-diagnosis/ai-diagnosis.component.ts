import { Component, computed, input, output, ChangeDetectionStrategy } from '@angular/core';

import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { SugerenciaFerreteria } from '../../../../core/models/asistente.models';

@Component({
    selector: 'app-ai-diagnosis',
    imports: [ButtonModule, CardModule, TagModule],
    templateUrl: './ai-diagnosis.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './ai-diagnosis.component.scss'
})
export class AiDiagnosisComponent {
  readonly query = input<string>('');
  readonly sugerencia = input<SugerenciaFerreteria | null>(null);
  readonly productCount = input<number>(0);

  readonly addKit = output<void>();

  readonly palabrasClave = computed(() => this.sugerencia()?.palabrasClave ?? []);
  readonly herramientas = computed(() => this.sugerencia()?.herramientas ?? []);
  readonly repuestos = computed(() => this.sugerencia()?.repuestos ?? []);

  readonly hasItems = computed(() => {
    return (
      this.herramientas().length > 0 ||
      this.repuestos().length > 0 ||
      this.palabrasClave().length > 0
    );
  });

  /** Etiqueta estable del CTA: evita recomputar la expresión en cada ciclo. */
  readonly kitLabel = computed(() =>
    this.productCount() > 0
      ? `Agregar kit completo al carrito (${this.productCount()})`
      : 'Agregar kit completo al carrito'
  );

  onAddKitClick(): void {
    if (this.productCount() > 0) {
      this.addKit.emit();
    }
  }
}
