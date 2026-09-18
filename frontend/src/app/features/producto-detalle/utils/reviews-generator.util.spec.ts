import { Producto } from '../../../core/models/producto.models';
import {
  generarResenas,
  generarResumenCalificaciones,
  obtenerIconoVisual
} from './reviews-generator.util';

describe('ReviewsGeneratorUtil', () => {
  const mockProduct: Producto = {
    id: 42,
    sku: 'TEST-01',
    nombre: 'Tubo PVC 1/2 pulgada',
    descripcionTecnica: 'Tubo para agua fría',
    descripcionColoquial: 'Cañería plástica',
    precio: 3500,
    stock: 10,
    categoriaId: 2
  };

  it('should generate deterministic reviews for the same product ID', () => {
    const resenas1 = generarResenas(mockProduct);
    const resenas2 = generarResenas(mockProduct);

    expect(resenas1.length).toBeGreaterThanOrEqual(3);
    expect(resenas1).toEqual(resenas2);
    expect(resenas1[0].autor).toBeTruthy();
    expect(resenas1[0].calificacion).toBeGreaterThanOrEqual(4);
    expect(resenas1[0].compraVerificada).toBeTrue();
  });

  it('should generate deterministic summary with valid distribution', () => {
    const resumen1 = generarResumenCalificaciones(mockProduct);
    const resumen2 = generarResumenCalificaciones(mockProduct);

    expect(resumen1).toEqual(resumen2);
    expect(resumen1.promedio).toBeGreaterThanOrEqual(4.0);
    expect(resumen1.promedio).toBeLessThanOrEqual(5.0);
    expect(resumen1.totalResenas).toBeGreaterThanOrEqual(15);
    expect(resumen1.distribucion.length).toBe(5);
  });

  it('should assign contextual icon and label based on product text', () => {
    const iconPlom = obtenerIconoVisual(mockProduct);
    expect(iconPlom.icono).toBe('pi pi-cloud');

    const iconHerr = obtenerIconoVisual({
      ...mockProduct,
      nombre: 'Martillo de uña 16oz',
      descripcionTecnica: 'Herramienta de impacto'
    });
    expect(iconHerr.icono).toBe('pi pi-wrench');

    const iconElec = obtenerIconoVisual({
      ...mockProduct,
      nombre: 'Cinta Aisladora Eléctrica',
      descripcionTecnica: 'Aislamiento eléctrico 600V'
    });
    expect(iconElec.icono).toBe('pi pi-bolt');
  });
});
