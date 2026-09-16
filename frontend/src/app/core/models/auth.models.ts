export type Rol = 'ADMIN' | 'CLIENTE';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  expiresIn: number;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email?: string | null;
  rol?: string | null;
}

export interface Usuario {
  id: number;
  username: string;
  rol: Rol | string;
  email?: string | null;
}

export interface JwtPayload {
  sub: string;
  roles: string[];
  iat?: number;
  exp?: number;
}
