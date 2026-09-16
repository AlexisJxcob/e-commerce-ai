import { Injectable, computed, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of } from 'rxjs';
import { LoginRequest, LoginResponse, RegisterRequest, Usuario, Rol } from '../models/auth.models';
import { JwtHelper } from './jwt.helper';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private static readonly TOKEN_STORAGE_KEY = 'repara_token';

  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);

  readonly currentUser = signal<Usuario | null>(null);
  readonly isAuthenticated = computed(() => !!this.currentUser());
  readonly isAdmin = computed(() => {
    const user = this.currentUser();
    if (!user) {
      return false;
    }
    return user.rol === 'ADMIN' || user.rol === 'ROLE_ADMIN';
  });

  constructor() {
    this.restoreSessionFromStorage();
  }

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', credentials).pipe(
      tap((response) => {
        this.saveToken(response.token);
        this.setUserFromToken(response.token);
        this.fetchProfile().subscribe();
      })
    );
  }

  register(payload: RegisterRequest): Observable<Usuario> {
    return this.http.post<Usuario>('/api/auth/register', payload);
  }

  logout(): void {
    this.removeToken();
    this.currentUser.set(null);
  }

  getToken(): string | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }

    const token = localStorage.getItem(AuthService.TOKEN_STORAGE_KEY);
    if (!token) {
      return null;
    }

    if (JwtHelper.isTokenExpired(token)) {
      this.logout();
      return null;
    }

    return token;
  }

  fetchProfile(): Observable<Usuario | null> {
    const token = this.getToken();
    if (!token) {
      return of(null);
    }

    return this.http.get<Usuario>('/api/v1/usuarios/me').pipe(
      tap((usuario) => {
        this.currentUser.set(usuario);
      }),
      catchError(() => {
        return of(null);
      })
    );
  }

  private saveToken(token: string): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(AuthService.TOKEN_STORAGE_KEY, token);
    }
  }

  private removeToken(): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem(AuthService.TOKEN_STORAGE_KEY);
    }
  }

  private restoreSessionFromStorage(): void {
    const token = this.getToken();
    if (!token) {
      return;
    }

    this.setUserFromToken(token);
    this.fetchProfile().subscribe();
  }

  private setUserFromToken(token: string): void {
    const username = JwtHelper.extractUsername(token);
    const roles = JwtHelper.extractRoles(token);

    if (!username) {
      this.logout();
      return;
    }

    const hasAdmin = roles.includes('ROLE_ADMIN') || roles.includes('ADMIN');
    const rol: Rol = hasAdmin ? 'ADMIN' : 'CLIENTE';

    // Seed temporary user until fetchProfile completes
    this.currentUser.set({
      id: 0,
      username,
      rol,
      email: null
    });
  }
}
