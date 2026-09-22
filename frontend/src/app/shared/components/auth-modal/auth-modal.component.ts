import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { TabsModule } from 'primeng/tabs';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalService, AuthModalMode } from '../../../core/auth/auth-modal.service';

@Component({
  selector: 'app-auth-modal',
  imports: [
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    TabsModule,
    MessageModule
  ],
  templateUrl: './auth-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './auth-modal.component.scss'
})
export class AuthModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly messageService = inject(MessageService);
  readonly authService = inject(AuthService);
  readonly authModalService = inject(AuthModalService);

  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly isVisible = computed(() => this.authModalService.isOpen());
  readonly currentMode = computed(() => this.authModalService.mode());

  readonly loginForm: FormGroup = this.fb.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  readonly registerForm: FormGroup = this.fb.group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    email: ['', [Validators.email, Validators.maxLength(150)]]
  });

  setMode(mode: AuthModalMode): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.authModalService.mode.set(mode);
  }

  onTabChange(tabValue: unknown): void {
    if (tabValue === 'login' || tabValue === 'register') {
      this.setMode(tabValue);
    }
  }

  onVisibleChange(visible: boolean): void {
    if (!visible) {
      this.close();
    }
  }

  close(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.isLoading.set(false);
    this.loginForm.reset();
    this.registerForm.reset();
    this.authModalService.clearPendingAction();
    this.authModalService.close();
  }

  onLoginSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.login(this.loginForm.value).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.authModalService.close();
        this.authModalService.executePendingAction();
        this.messageService.add({
          severity: 'success',
          summary: 'Sesión iniciada',
          detail: 'Has ingresado correctamente a Repara.ai',
          life: 3000
        });
      },
      error: (err) => {
        this.isLoading.set(false);
        const msg = this.extractErrorMessage(err, 'Credenciales inválidas. Revisa usuario y contraseña.');
        this.errorMessage.set(msg);
        this.messageService.add({
          severity: 'error',
          summary: 'Error de autenticación',
          detail: msg,
          life: 4000
        });
      }
    });
  }

  onRegisterSubmit(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { username, password, email } = this.registerForm.value;
    const payload = {
      username,
      password,
      email: email ? email.trim() : null
    };

    this.authService.register(payload).subscribe({
      next: () => {
        // Auto-login upon successful registration
        this.authService.login({ username, password }).subscribe({
          next: () => {
            this.isLoading.set(false);
            this.authModalService.close();
            this.authModalService.executePendingAction();
            this.messageService.add({
              severity: 'success',
              summary: 'Cuenta creada',
              detail: '¡Bienvenido a Repara.ai!',
              life: 3000
            });
          },
          error: () => {
            this.isLoading.set(false);
            const msg = 'Cuenta creada con éxito. Ahora inicia sesión.';
            this.successMessage.set(msg);
            this.setMode('login');
            this.loginForm.patchValue({ username });
            this.messageService.add({
              severity: 'success',
              summary: 'Registro exitoso',
              detail: msg,
              life: 3000
            });
          }
        });
      },
      error: (err) => {
        this.isLoading.set(false);
        const msg = this.extractErrorMessage(err, 'Error al registrar la cuenta. Intenta de nuevo.');
        this.errorMessage.set(msg);
        this.messageService.add({
          severity: 'error',
          summary: 'Error de registro',
          detail: msg,
          life: 4000
        });
      }
    });
  }

  private extractErrorMessage(err: unknown, fallbackMessage: string): string {
    const errorObj = err as any;
    const errorBody = errorObj?.error;

    // 1. Backend structured ErrorResponse (plain object, not JS Error or SyntaxError)
    if (errorBody && typeof errorBody === 'object' && !(errorBody instanceof Error)) {
      if (errorBody.fieldErrors && typeof errorBody.fieldErrors === 'object') {
        const firstError = Object.values(errorBody.fieldErrors)[0];
        if (typeof firstError === 'string' && firstError.trim().length > 0) {
          return firstError;
        }
      }
      if (typeof errorBody.message === 'string' && errorBody.message.trim().length > 0) {
        return errorBody.message;
      }
    }

    // 2. HTTP status-specific fallback messages
    const status = errorObj?.status;
    if (typeof status === 'number') {
      if (status === 0) {
        return 'No se pudo conectar con el servidor. Verifica que el backend esté en ejecución.';
      }
      if (status === 409) {
        return 'Ya existe una cuenta con este nombre de usuario o correo electrónico.';
      }
      if (status === 401) {
        return 'Credenciales inválidas. Revisa usuario y contraseña.';
      }
      if (status === 403) {
        return 'No tienes permisos para realizar esta acción.';
      }
      if (status >= 500) {
        return 'El servicio no está disponible en este momento. Intenta de nuevo más tarde.';
      }
    }

    return fallbackMessage;
  }
}
