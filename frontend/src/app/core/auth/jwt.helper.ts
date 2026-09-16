import { JwtPayload } from '../models/auth.models';

/**
 * Utility functions for client-side JWT inspection.
 * Token signature verification is strictly enforced by the backend on every API call.
 */
export class JwtHelper {
  /**
   * Decodes the payload portion of a JWT token safely.
   */
  static decodeToken(token: string | null | undefined): JwtPayload | null {
    if (!token) {
      return null;
    }

    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        return null;
      }

      // Base64Url decode with padding and UTF-8 safety
      let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      while (base64.length % 4) {
        base64 += '=';
      }

      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((char) => '%' + ('00' + char.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );

      return JSON.parse(jsonPayload) as JwtPayload;
    } catch {
      return null;
    }
  }

  /**
   * Checks if the JWT is expired or malformed.
   */
  static isTokenExpired(token: string | null | undefined, offsetSeconds: number = 0): boolean {
    if (!token) {
      return true;
    }

    const payload = this.decodeToken(token);
    if (!payload || !payload.exp) {
      return true;
    }

    const expirationDate = new Date(0);
    expirationDate.setUTCSeconds(payload.exp);

    const offsetMs = offsetSeconds * 1000;
    return expirationDate.valueOf() <= (new Date().valueOf() + offsetMs);
  }

  /**
   * Returns token expiration Date or null if invalid.
   */
  static getTokenExpirationDate(token: string | null | undefined): Date | null {
    const payload = this.decodeToken(token);
    if (!payload || !payload.exp) {
      return null;
    }

    const expirationDate = new Date(0);
    expirationDate.setUTCSeconds(payload.exp);
    return expirationDate;
  }

  /**
   * Extracts the roles claim (e.g. ['ROLE_ADMIN', 'ROLE_CLIENTE']).
   */
  static extractRoles(token: string | null | undefined): string[] {
    const payload = this.decodeToken(token);
    if (!payload || !Array.isArray(payload.roles)) {
      return [];
    }
    return payload.roles;
  }

  /**
   * Extracts username from subject claim.
   */
  static extractUsername(token: string | null | undefined): string | null {
    const payload = this.decodeToken(token);
    return payload?.sub ?? null;
  }
}
