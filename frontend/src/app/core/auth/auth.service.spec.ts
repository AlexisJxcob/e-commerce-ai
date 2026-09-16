import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { LoginRequest, LoginResponse, RegisterRequest, Usuario } from '../models/auth.models';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  // Sample token: sub: "admin", roles: ["ROLE_ADMIN"], exp in year 2096
  const sampleAdminToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIlJPTEVfQURNSU4iXSwiZXhwIjo0MDAwMDAwMDAwfQ.dummy';

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created and start unauthenticated when storage is empty', () => {
    expect(service).toBeTruthy();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.isAdmin()).toBeFalse();
  });

  it('should authenticate user and store token on login', () => {
    const credentials: LoginRequest = { username: 'admin', password: 'password123' };
    const mockResponse: LoginResponse = {
      token: sampleAdminToken,
      username: 'admin',
      expiresIn: 3600
    };
    const mockProfile: Usuario = {
      id: 1,
      username: 'admin',
      rol: 'ADMIN',
      email: 'admin@repara.ai'
    };

    service.login(credentials).subscribe((res) => {
      expect(res.token).toBe(sampleAdminToken);
    });

    const loginReq = httpMock.expectOne('/api/auth/login');
    expect(loginReq.request.method).toBe('POST');
    loginReq.flush(mockResponse);

    const profileReq = httpMock.expectOne('/api/v1/usuarios/me');
    expect(profileReq.request.method).toBe('GET');
    profileReq.flush(mockProfile);

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.isAdmin()).toBeTrue();
    expect(service.currentUser()?.username).toBe('admin');
    expect(service.getToken()).toBe(sampleAdminToken);
  });

  it('should clear authentication state on logout', () => {
    localStorage.setItem('repara_token', sampleAdminToken);
    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.isAdmin()).toBeFalse();
    expect(service.currentUser()).toBeNull();
    expect(service.getToken()).toBeNull();
  });

  it('should call register endpoint with expected payload', () => {
    const registerPayload: RegisterRequest = {
      username: 'newuser',
      password: 'password123',
      email: 'newuser@example.com'
    };
    const mockCreatedUser: Usuario = {
      id: 2,
      username: 'newuser',
      rol: 'CLIENTE',
      email: 'newuser@example.com'
    };

    service.register(registerPayload).subscribe((user) => {
      expect(user.username).toBe('newuser');
      expect(user.rol).toBe('CLIENTE');
    });

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(registerPayload);
    req.flush(mockCreatedUser);
  });
});
