import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';

import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { CarritoService } from '../../core/services/carrito.service';
import { AuthService } from '../../core/auth/auth.service';
import { AuthModalService } from '../../core/auth/auth-modal.service';
import { PedidoService } from '../../core/services/pedido.service';
import { ClpPipe } from '../../shared/pipes/clp.pipe';
import { formatRut, validateRut } from '../../core/utils/rut.util';

export type CheckoutStep = 'resumen' | 'datos' | 'entrega' | 'pago';
export type MetodoEntrega = 'retiro' | 'despacho';

@Component({
    selector: 'app-checkout',
    imports: [
    ReactiveFormsModule,
    RouterModule,
    ButtonModule,
    InputTextModule,
    ClpPipe
],
    templateUrl: './checkout.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './checkout.component.scss'
})
export class CheckoutComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  readonly carritoService = inject(CarritoService);
  readonly authService = inject(AuthService);
  readonly authModalService = inject(AuthModalService);
  readonly pedidoService = inject(PedidoService);

  readonly currentStep = signal<CheckoutStep>('resumen');
  readonly metodoEntrega = signal<MetodoEntrega>('retiro');
  readonly retiraTercero = signal<boolean>(false);
  readonly terminosAceptados = signal<boolean>(false);
  readonly isProcessing = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly items = computed(() => this.carritoService.items());
  readonly itemCount = computed(() => this.carritoService.itemCount());
  readonly isEmpty = computed(() => this.carritoService.isEmpty());
  readonly subtotal = computed(() => this.carritoService.total());

  readonly costoDespacho = computed(() => {
    if (this.metodoEntrega() === 'retiro') {
      return 0;
    }
    // Despacho gratis sobre $50.000, estándar $3.990
    return this.subtotal() >= 50000 ? 0 : 3990;
  });

  readonly total = computed(() => this.subtotal() + this.costoDespacho());

  readonly steps = [
    { id: 'resumen' as CheckoutStep, label: 'Resumen', number: 1, icon: 'pi pi-shopping-bag' },
    { id: 'datos' as CheckoutStep, label: 'Tus Datos', number: 2, icon: 'pi pi-user' },
    { id: 'entrega' as CheckoutStep, label: 'Entrega', number: 3, icon: 'pi pi-map-marker' },
    { id: 'pago' as CheckoutStep, label: 'Pago', number: 4, icon: 'pi pi-credit-card' }
  ];

  readonly datosForm: FormGroup = this.fb.group({
    nombres: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(70)]],
    apellidos: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(70)]],
    rut: ['', [Validators.required, (control: { value: string }) => validateRut(control.value) ? null : { rutInvalido: true }]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(120)]],
    telefono: ['', [Validators.required, Validators.pattern(/^[0-9+ ]{8,15}$/)]]
  });

  readonly despachoForm: FormGroup = this.fb.group({
    region: ['Región Metropolitana', [Validators.required]],
    comuna: ['', [Validators.required, Validators.minLength(2)]],
    direccion: ['', [Validators.required, Validators.minLength(5)]],
    depto: [''],
    referencias: ['']
  });

  readonly terceroForm: FormGroup = this.fb.group({
    nombreTercero: [''],
    rutTercero: ['']
  });

  readonly regionesChile = [
    'Región Metropolitana',
    'Arica y Parinacota',
    'Tarapacá',
    'Antofagasta',
    'Atacama',
    'Coquimbo',
    'Valparaíso',
    "O'Higgins",
    'Maule',
    'Ñuble',
    'Biobío',
    'La Araucanía',
    'Los Ríos',
    'Los Lagos',
    'Aysén',
    'Magallanes y Antártica Chilena'
  ];

  readonly sucursalLocal = {
    nombre: 'Sucursal Central Ferretería IA — Casa Matriz',
    direccion: 'Av. Providencia 1234, Local 45 (Metro Manuel Montt)',
    comuna: 'Providencia, Santiago',
    horario: 'Lunes a Viernes 08:30 a 18:30 hrs | Sábados 09:00 a 14:00 hrs',
    tiempoRetiro: 'Listo para retiro en 2 horas hábiles tras la confirmación del pago',
    telefono: '+56 2 2987 6543'
  };

  ngOnInit(): void {
    const user = this.authService.currentUser();
    if (user) {
      if (user.username && !this.datosForm.get('nombres')?.value) {
        this.datosForm.patchValue({
          nombres: user.username,
          email: user.email || ''
        });
      }
    }
  }

  setStep(step: CheckoutStep): void {
    if (step === 'resumen') {
      this.currentStep.set('resumen');
      return;
    }

    if (step === 'datos') {
      if (this.isEmpty()) return;
      this.currentStep.set('datos');
      return;
    }

    if (step === 'entrega') {
      if (this.isEmpty()) return;
      if (this.datosForm.invalid) {
        this.datosForm.markAllAsTouched();
        return;
      }
      this.currentStep.set('entrega');
      return;
    }

    if (step === 'pago') {
      if (this.isEmpty()) return;
      if (this.datosForm.invalid) {
        this.currentStep.set('datos');
        this.datosForm.markAllAsTouched();
        return;
      }
      if (this.metodoEntrega() === 'despacho' && this.despachoForm.invalid) {
        this.currentStep.set('entrega');
        this.despachoForm.markAllAsTouched();
        return;
      }
      this.currentStep.set('pago');
    }
  }

  isStepCompleted(stepId: CheckoutStep): boolean {
    const stepOrder: Record<CheckoutStep, number> = {
      resumen: 1,
      datos: 2,
      entrega: 3,
      pago: 4
    };
    const currentOrder = stepOrder[this.currentStep()];

    if (stepId === 'resumen') {
      return !this.isEmpty() && currentOrder > 1;
    }
    if (stepId === 'datos') {
      return this.datosForm.valid && currentOrder > 2;
    }
    if (stepId === 'entrega') {
      const entregaValida = this.metodoEntrega() === 'retiro' || this.despachoForm.valid;
      return entregaValida && currentOrder > 3;
    }
    return false;
  }

  avanzarADatos(): void {
    if (this.isEmpty()) {
      return;
    }
    this.currentStep.set('datos');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  avanzarAEntrega(): void {
    if (this.datosForm.invalid) {
      this.datosForm.markAllAsTouched();
      return;
    }
    this.currentStep.set('entrega');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  avanzarAPago(): void {
    if (this.metodoEntrega() === 'despacho' && this.despachoForm.invalid) {
      this.despachoForm.markAllAsTouched();
      return;
    }
    this.currentStep.set('pago');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  onRutBlur(): void {
    const rutControl = this.datosForm.get('rut');
    if (rutControl && rutControl.value) {
      const formatted = formatRut(rutControl.value);
      rutControl.setValue(formatted, { emitEvent: false });
    }
  }

  onTerceroRutBlur(): void {
    const rutControl = this.terceroForm.get('rutTercero');
    if (rutControl && rutControl.value) {
      const formatted = formatRut(rutControl.value);
      rutControl.setValue(formatted, { emitEvent: false });
    }
  }

  incrementar(itemId: number, cantidad: number): void {
    this.carritoService.actualizarCantidad(itemId, cantidad + 1).subscribe();
  }

  decrementar(itemId: number, cantidad: number): void {
    this.carritoService.actualizarCantidad(itemId, cantidad - 1).subscribe();
  }

  eliminar(itemId: number): void {
    this.carritoService.eliminarItem(itemId).subscribe();
  }

  seguirComprando(): void {
    this.router.navigate(['/']);
  }

  toggleTerminos(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.terminosAceptados.set(input.checked);
  }

  toggleRetiraTercero(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.retiraTercero.set(input.checked);
  }

  procesarPago(): void {
    if (!this.terminosAceptados() || this.isProcessing()) {
      return;
    }

    if (!this.authService.isAuthenticated()) {
      this.authModalService.setPendingAction(() => {
        this.ejecutarPago();
      });
      this.authModalService.openLogin();
      return;
    }

    this.ejecutarPago();
  }

  ejecutarPago(): void {
    this.isProcessing.set(true);
    this.errorMessage.set(null);

    this.carritoService.sincronizarCarritoInvitado().subscribe({
      next: () => {
        this.pedidoService.crearDesdeCarrito().subscribe({
          next: (pedido) => {
            this.pedidoService.iniciarPago(pedido.id).subscribe({
              next: (checkout) => {
                this.pedidoService.redirigirAWebpay(checkout.token, checkout.url);
              },
              error: () => {
                this.isProcessing.set(false);
                this.errorMessage.set('Error al conectar con la pasarela Transbank Webpay Plus. Intenta nuevamente.');
              }
            });
          },
          error: (err) => {
            this.isProcessing.set(false);
            const msg = err?.error?.message ?? 'No se pudo generar el pedido desde el carrito.';
            this.errorMessage.set(msg);
          }
        });
      },
      error: () => {
        this.isProcessing.set(false);
        this.errorMessage.set('Error al sincronizar el carrito antes del pago.');
      }
    });
  }
}
