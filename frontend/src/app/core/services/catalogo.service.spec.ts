import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { CatalogoService } from './catalogo.service';
import { Producto, PageResponse } from '../models/producto.models';
import { Categoria } from '../models/categoria.models';

describe('CatalogoService', () => {
  let service: CatalogoService;
  let httpMock: HttpTestingController;

  const mockCategories: Categoria[] = [
    { id: 1, nombre: 'Herramientas Manuales', descripcion: 'Martillos y llaves', padreId: null },
    { id: 2, nombre: 'Plomería', descripcion: 'Cañerías y uniones', padreId: null },
    { id: 3, nombre: 'Sin categoría', descripcion: 'Default', padreId: null },
    { id: 4, nombre: 'Electricidad Vacía', descripcion: 'Sin productos', padreId: null }
  ];

  const mockProducts: Producto[] = [
    {
      id: 101,
      sku: 'HERR-01',
      nombre: 'Martillo',
      precio: 5000,
      stock: 10,
      categoriaId: 1,
      descripcionTecnica: 'Acero',
      descripcionColoquial: 'Martillo'
    },
    {
      id: 102,
      sku: 'PLOM-01',
      nombre: 'Tubo PVC 1/2',
      precio: 3000,
      stock: 20,
      categoriaId: 2,
      descripcionTecnica: 'PVC',
      descripcionColoquial: 'Tubo para agua'
    },
    {
      id: 103,
      sku: 'SINC-01',
      nombre: 'Cinta Aisladora',
      precio: 1200,
      stock: 15,
      categoriaId: 3, // categoría "Sin categoría"
      descripcionTecnica: 'Aislante',
      descripcionColoquial: 'Cinta'
    },
    {
      id: 104,
      sku: 'NULL-01',
      nombre: 'Pegamento Rápido',
      precio: 2500,
      stock: 5,
      categoriaId: null, // Sin categoría asignada
      descripcionTecnica: 'Cianoacrilato',
      descripcionColoquial: 'La Gotita'
    }
  ];

  const mockPage: PageResponse<Producto> = {
    content: mockProducts,
    totalElements: 4,
    totalPages: 1,
    size: 100,
    number: 0,
    first: true,
    last: true,
    empty: false
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CatalogoService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(CatalogoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch categories from /api/v1/categorias', () => {
    service.getCategorias().subscribe((res) => {
      expect(res.length).toBe(4);
    });

    const req = httpMock.expectOne('/api/v1/categorias');
    expect(req.request.method).toBe('GET');
    req.flush(mockCategories);
  });

  it('should fetch products from /api/v1/productos with pagination params', () => {
    service.getProductos(0, 50).subscribe((res) => {
      expect(res.content.length).toBe(4);
    });

    const req = httpMock.expectOne('/api/v1/productos?page=0&size=50');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should group products by category and omit empty categories', () => {
    service.getCatalogoAgrupado().subscribe((grupos) => {
      // Should have: Herramientas Manuales, Plomería, and "Otros productos"
      // Category 4 (Electricidad Vacía) must be omitted because it has 0 products
      expect(grupos.length).toBe(3);

      const herr = grupos.find((g) => g.categoria.id === 1);
      expect(herr).toBeTruthy();
      expect(herr?.productos.length).toBe(1);
      expect(herr?.productos[0].sku).toBe('HERR-01');

      const plom = grupos.find((g) => g.categoria.id === 2);
      expect(plom).toBeTruthy();
      expect(plom?.productos.length).toBe(1);

      // "Otros productos" should group id:3 ("Sin categoría") and null categoryId products
      const otros = grupos.find((g) => g.categoria.id === -1);
      expect(otros).toBeTruthy();
      expect(otros?.categoria.nombre).toBe('Otros productos');
      expect(otros?.productos.length).toBe(2);
      expect(otros?.productos.map((p) => p.sku)).toEqual(['SINC-01', 'NULL-01']);
    });

    const reqCat = httpMock.expectOne('/api/v1/categorias');
    reqCat.flush(mockCategories);

    const reqProd = httpMock.expectOne('/api/v1/productos?page=0&size=100');
    reqProd.flush(mockPage);
  });
});
