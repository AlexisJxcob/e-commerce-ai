import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalService, AuthModalMode } from '../../../core/auth/auth-modal.service';
import { ErrorResponse } from '../../../core/models/error.models';

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule
  ],
  templateUrl: './auth-modal.component.html',
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
        this.close();
      },
      error: (err) => {
        this.isLoading.set(false);
        const errorBody: ErrorResponse | undefined = err.error;
        this.errorMessage.set(errorBody?.message ?? 'Credenciales inválidas. Revisa usuario y contraseña.');
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
            this.close();
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
        const errorBody: ErrorResponse | undefined = err.error;
        if (errorBody?.fieldErrors) {
          const firstError = Object.values(errorBody.fieldErrors)[0];
          this.errorMessage.set(firstError ?? errorBody.message);
        } else {
          this.errorMessage.set(errorBody?.message ?? 'Error al registrar la cuenta. Intenta de nuevo.');
        }
      }
    });
  }
}
