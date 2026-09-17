import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';
import { AuthService } from '../auth/auth.service';
import { AuthModalService } from '../auth/auth-modal.service';

describe('HTTP Interceptors', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let authModalSpy: jasmine.SpyObj<AuthModalService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getToken', 'logout']);
    authModalSpy = jasmine.createSpyObj('AuthModalService', ['openLogin']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: AuthModalService, useValue: authModalSpy },
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting()
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should add Authorization Bearer header when token is present', () => {
    authServiceSpy.getToken.and.returnValue('valid-mock-token');

    httpClient.get('/api/v1/productos').subscribe();

    const req = httpMock.expectOne('/api/v1/productos');
    expect(req.request.headers.has('Authorization')).toBeTrue();
    expect(req.request.headers.get('Authorization')).toBe('Bearer valid-mock-token');
    req.flush([]);
  });

  it('should NOT add Authorization header for login endpoint', () => {
    authServiceSpy.getToken.and.returnValue('valid-mock-token');

    httpClient.post('/api/auth/login', {}).subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('should trigger logout and open login modal on 401 response', () => {
    httpClient.get('/api/v1/pedidos').subscribe({
      error: () => {}
    });

    const req = httpMock.expectOne('/api/v1/pedidos');
    req.flush({ message: 'No autenticado' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(authModalSpy.openLogin).toHaveBeenCalled();
  });
});
