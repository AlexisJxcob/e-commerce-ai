import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of, throwError } from 'rxjs';
import { AuthModalComponent } from './auth-modal.component';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalService } from '../../../core/auth/auth-modal.service';
import { MessageService } from 'primeng/api';

describe('AuthModalComponent', () => {
  let component: AuthModalComponent;
  let fixture: ComponentFixture<AuthModalComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let authModalService: AuthModalService;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login', 'register']);

    await TestBed.configureTestingModule({
      imports: [AuthModalComponent],
      providers: [
        provideAnimationsAsync(),
        { provide: AuthService, useValue: authServiceSpy },
        MessageService
      ]
    }).compileComponents();

    authModalService = TestBed.inject(AuthModalService);
    fixture = TestBed.createComponent(AuthModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should reflect modal visibility from AuthModalService', () => {
    expect(component.isVisible()).toBeFalse();
    authModalService.openLogin();
    expect(component.isVisible()).toBeTrue();
    expect(component.currentMode()).toBe('login');
  });

  it('should not submit login if form is invalid', () => {
    component.onLoginSubmit();
    expect(component.loginForm.invalid).toBeTrue();
    expect(authServiceSpy.login).not.toHaveBeenCalled();
  });

  it('should call authService.login when form is valid', () => {
    authServiceSpy.login.and.returnValue(of({ token: 'mock-token', username: 'alexis', expiresIn: 3600 }));

    component.loginForm.setValue({
      username: 'alexis',
      password: 'secretpassword'
    });

    component.onLoginSubmit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      username: 'alexis',
      password: 'secretpassword'
    });
    expect(component.isLoading()).toBeFalse();
    expect(authModalService.isOpen()).toBeFalse();
  });

  it('should display error message when login fails', () => {
    authServiceSpy.login.and.returnValue(
      throwError(() => ({ error: { message: 'Credenciales inválidas' } }))
    );

    component.loginForm.setValue({
      username: 'alexis',
      password: 'wrongpassword'
    });

    component.onLoginSubmit();

    expect(component.errorMessage()).toBe('Credenciales inválidas');
    expect(component.isLoading()).toBeFalse();
  });

  it('should call authService.register and then auto-login on valid register submission', () => {
    authServiceSpy.register.and.returnValue(
      of({ id: 10, username: 'newuser', rol: 'CLIENTE', email: 'user@test.com' })
    );
    authServiceSpy.login.and.returnValue(
      of({ token: 'new-token', username: 'newuser', expiresIn: 3600 })
    );

    component.setMode('register');
    component.registerForm.setValue({
      username: 'newuser',
      password: 'password123',
      email: 'user@test.com'
    });

    component.onRegisterSubmit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      username: 'newuser',
      password: 'password123',
      email: 'user@test.com'
    });
    expect(authServiceSpy.login).toHaveBeenCalledWith({
      username: 'newuser',
      password: 'password123'
    });
    expect(authModalService.isOpen()).toBeFalse();
  });

  it('should display friendly message instead of raw SyntaxError when backend returns non-JSON', () => {
    const syntaxError = new SyntaxError('JSON.parse: unexpected character at line 1 column 1 of the JSON data');
    authServiceSpy.register.and.returnValue(
      throwError(() => ({ error: syntaxError, status: 500 }))
    );

    component.setMode('register');
    component.registerForm.setValue({
      username: 'newuser',
      password: 'password123',
      email: 'user@test.com'
    });

    component.onRegisterSubmit();

    expect(component.errorMessage()).not.toContain('JSON.parse');
    expect(component.errorMessage()).toBe('El servicio no está disponible en este momento. Intenta de nuevo más tarde.');
  });

  it('should display connection message when backend is unreachable (status 0)', () => {
    authServiceSpy.register.and.returnValue(
      throwError(() => ({ status: 0 }))
    );

    component.setMode('register');
    component.registerForm.setValue({
      username: 'newuser',
      password: 'password123',
      email: 'user@test.com'
    });

    component.onRegisterSubmit();

    expect(component.errorMessage()).toBe('No se pudo conectar con el servidor. Verifica que el backend esté en ejecución.');
  });

  it('should display field error when backend returns validation errors', () => {
    authServiceSpy.register.and.returnValue(
      throwError(() => ({
        error: {
          status: 400,
          message: 'Error de validación',
          fieldErrors: { password: 'La contraseña debe tener al menos 8 caracteres' }
        }
      }))
    );

    component.setMode('register');
    component.registerForm.setValue({
      username: 'newuser',
      password: 'password123',
      email: 'user@test.com'
    });

    component.onRegisterSubmit();

    expect(component.errorMessage()).toBe('La contraseña debe tener al menos 8 caracteres');
  });
});
