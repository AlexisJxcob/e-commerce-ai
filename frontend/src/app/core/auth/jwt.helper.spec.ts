import { JwtHelper } from './jwt.helper';

describe('JwtHelper', () => {
  // Sample token with payload: {"sub":"alexis","roles":["ROLE_ADMIN"],"iat":1700000000,"exp":4000000000}
  const validToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhbGV4aXMiLCJyb2xlcyI6WyJST0xFX0FETUlOIl0sImlhdCI6MTcwMDAwMDAwMCwiZXhwIjo0MDAwMDAwMDAwfQ.signature';
  // Expired token with payload: {"sub":"juan","roles":["ROLE_CLIENTE"],"iat":1500000000,"exp":1500000001}
  const expiredToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqdWFuIiwicm9sZXMiOlsicm9sZV9DTElFTlRFIl0sImlhdCI6MTUwMDAwMDAwMCwiZXhwIjoxNTAwMDAwMDAxfQ.signature';

  it('should decode payload correctly', () => {
    const payload = JwtHelper.decodeToken(validToken);
    expect(payload).toBeTruthy();
    expect(payload?.sub).toBe('alexis');
    expect(payload?.roles).toEqual(['ROLE_ADMIN']);
  });

  it('should return null for invalid token string', () => {
    expect(JwtHelper.decodeToken('invalid-token')).toBeNull();
    expect(JwtHelper.decodeToken(null)).toBeNull();
  });

  it('should detect unexpired token', () => {
    expect(JwtHelper.isTokenExpired(validToken)).toBeFalse();
  });

  it('should detect expired token', () => {
    expect(JwtHelper.isTokenExpired(expiredToken)).toBeTrue();
  });

  it('should extract roles properly', () => {
    expect(JwtHelper.extractRoles(validToken)).toEqual(['ROLE_ADMIN']);
  });

  it('should extract username properly', () => {
    expect(JwtHelper.extractUsername(validToken)).toBe('alexis');
  });
});
