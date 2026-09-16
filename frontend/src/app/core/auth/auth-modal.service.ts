import { Injectable, signal } from '@angular/core';

export type AuthModalMode = 'login' | 'register';

@Injectable({
  providedIn: 'root'
})
export class AuthModalService {
  readonly isOpen = signal<boolean>(false);
  readonly mode = signal<AuthModalMode>('login');

  open(mode: AuthModalMode = 'login'): void {
    this.mode.set(mode);
    this.isOpen.set(true);
  }

  openLogin(): void {
    this.open('login');
  }

  openRegister(): void {
    this.open('register');
  }

  close(): void {
    this.isOpen.set(false);
  }
}
