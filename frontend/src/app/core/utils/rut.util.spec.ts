import { cleanRut, formatRut, validateRut } from './rut.util';

describe('RutUtil', () => {
  describe('cleanRut', () => {
    it('should clean dots, dashes and whitespace and uppercase K', () => {
      expect(cleanRut(' 12.345.678-k ')).toBe('12345678K');
      expect(cleanRut(null)).toBe('');
      expect(cleanRut('')).toBe('');
    });
  });

  describe('formatRut', () => {
    it('should format clean rut into standard format with dots and dash', () => {
      expect(formatRut('111111111')).toBe('11.111.111-1');
      expect(formatRut('9876543K')).toBe('9.876.543-K');
    });

    it('should return empty string if input is falsy', () => {
      expect(formatRut('')).toBe('');
      expect(formatRut(null)).toBe('');
    });
  });

  describe('validateRut', () => {
    it('should return true for valid RUTs', () => {
      expect(validateRut('11.111.111-1')).toBeTrue();
      expect(validateRut('11111111-1')).toBeTrue();
      expect(validateRut('111111111')).toBeTrue();
    });

    it('should return false for invalid RUTs', () => {
      expect(validateRut('11.111.111-2')).toBeFalse();
      expect(validateRut('123')).toBeFalse();
      expect(validateRut('')).toBeFalse();
      expect(validateRut(null)).toBeFalse();
    });
  });
});
