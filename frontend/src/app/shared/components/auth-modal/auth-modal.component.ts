import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';

import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalService, AuthModalMode } from '../../../core/auth/auth-modal.service';
import { ErrorResponse } from '../../../core/models/error.models';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'app-auth-modal',
    imports: [
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule
],
    templateUrl: './auth-modal.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './auth-modal.component.scss'
})
export class AuthModalComponent {
  private readonly fb = inject(FormBuilder);
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
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(this.extractErrorMessage(err, 'Credenciales inválidas. Revisa usuario y contraseña.'));
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
          },
          error: () => {
            this.isLoading.set(false);
            this.successMessage.set('Cuenta creada con éxito. Ahora inicia sesión.');
            this.setMode('login');
            this.loginForm.patchValue({ username });
          }
        });
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(this.extractErrorMessage(err, 'Error al registrar la cuenta. Intenta de nuevo.'));
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
