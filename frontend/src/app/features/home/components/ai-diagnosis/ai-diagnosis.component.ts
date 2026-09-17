import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { SugerenciaFerreteria } from '../../../../core/models/asistente.models';

@Component({
  selector: 'app-ai-diagnosis',
  standalone: true,
  imports: [CommonModule, ButtonModule],
  templateUrl: './ai-diagnosis.component.html',
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

  onAddKitClick(): void {
    if (this.productCount() > 0) {
      this.addKit.emit();
    }
  }
}
