import { ClpPipe } from './clp.pipe';

describe('ClpPipe', () => {
  let pipe: ClpPipe;

  beforeEach(() => {
    pipe = new ClpPipe();
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  it('should format numbers with dot as thousand separator', () => {
    expect(pipe.transform(12990)).toBe('$ 12.990');
    expect(pipe.transform(1500)).toBe('$ 1.500');
    expect(pipe.transform(350000)).toBe('$ 350.000');
    expect(pipe.transform(990)).toBe('$ 990');
  });

  it('should round decimal prices to nearest integer', () => {
    expect(pipe.transform(12990.45)).toBe('$ 12.990');
    expect(pipe.transform(12990.8)).toBe('$ 12.991');
  });

  it('should handle strings representing numbers', () => {
    expect(pipe.transform('8450')).toBe('$ 8.450');
  });

  it('should return $ 0 for null, undefined, empty or NaN values', () => {
    expect(pipe.transform(null)).toBe('$ 0');
    expect(pipe.transform(undefined)).toBe('$ 0');
    expect(pipe.transform('')).toBe('$ 0');
    expect(pipe.transform('not-a-number')).toBe('$ 0');
  });
});
