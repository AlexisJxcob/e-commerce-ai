/**
 * Chilean RUT validation and formatting utilities.
 */

export function cleanRut(rut: string | null | undefined): string {
  if (!rut) {
    return '';
  }
  return rut.replace(/[^0-9kK]/g, '').toUpperCase();
}

export function formatRut(rut: string | null | undefined): string {
  const cleaned = cleanRut(rut);
  if (!cleaned) {
    return '';
  }

  if (cleaned.length === 1) {
    return cleaned;
  }

  const cuerpo = cleaned.slice(0, -1);
  const dv = cleaned.slice(-1);

  let formattedCuerpo = '';
  let count = 0;
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    formattedCuerpo = cuerpo[i] + formattedCuerpo;
    count++;
    if (count === 3 && i > 0) {
      formattedCuerpo = '.' + formattedCuerpo;
      count = 0;
    }
  }

  return `${formattedCuerpo}-${dv}`;
}

export function validateRut(rut: string | null | undefined): boolean {
  const cleaned = cleanRut(rut);
  if (cleaned.length < 8 || cleaned.length > 9) {
    return false;
  }

  const cuerpo = cleaned.slice(0, -1);
  const dv = cleaned.slice(-1);

  if (!/^\d+$/.test(cuerpo)) {
    return false;
  }

  let suma = 0;
  let factor = 2;

  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += parseInt(cuerpo[i], 10) * factor;
    factor = factor === 7 ? 2 : factor + 1;
  }

  const resto = 11 - (suma % 11);
  let expectedDv: string;

  if (resto === 11) {
    expectedDv = '0';
  } else if (resto === 10) {
    expectedDv = 'K';
  } else {
    expectedDv = resto.toString();
  }

  return dv === expectedDv;
}
