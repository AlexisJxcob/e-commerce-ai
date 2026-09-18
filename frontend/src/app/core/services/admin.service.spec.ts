import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminService } from './admin.service';
import { Producto, ProductoRequest, PageResponse } from '../models/producto.models';
import { Categoria, CategoriaRequest } from '../models/categoria.models';
import { ReindexacionResponse } from '../models/asistente.models';

describe('AdminService', () => {
  let service: AdminService;
  let httpMock: HttpTestingController;

  const mockProduct: Producto = {
    id: 1,
    sku: 'SKU-001',
    nombre: 'Martillo Carpintero',
    precio: 9500,
    stock: 15,
    categoriaId: 1,
    descripcionTecnica: 'Acero forjado',
    descripcionColoquial: 'Martillo común'
  };

  const mockCategory: Categoria = {
    id: 1,
    nombre: 'Herramientas Manuales',
    descripcion: 'Martillos y destornilladores',
    padreId: null
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AdminService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch paginated products', () => {
    const mockPage: PageResponse<Producto> = {
      content: [mockProduct],
      totalElements: 1,
      totalPages: 1,
      size: 50,
      number: 0,
      first: true,
      last: true,
      empty: false
    };

    service.getProductos(0, 50).subscribe((page) => {
      expect(page.content.length).toBe(1);
      expect(page.content[0].sku).toBe('SKU-001');
    });

    const req = httpMock.expectOne('/api/v1/productos?page=0&size=50');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should update stock via PATCH', () => {
    service.actualizarStock(1, 20).subscribe((prod) => {
      expect(prod.stock).toBe(20);
    });

    const req = httpMock.expectOne('/api/v1/productos/1/stock?stock=20');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...mockProduct, stock: 20 });
  });

  it('should create product via POST', () => {
    const payload: ProductoRequest = {
      sku: 'SKU-002',
      nombre: 'Serrucho',
      precio: 8500,
      stock: 5,
      categoriaId: 1,
      descripcionTecnica: 'Hoja de acero templado',
      descripcionColoquial: 'Serrucho para madera'
    };

    service.crearProducto(payload).subscribe((prod) => {
      expect(prod.nombre).toBe('Serrucho');
    });

    const req = httpMock.expectOne('/api/v1/productos');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 2, ...payload });
  });

  it('should update product via PUT', () => {
    const payload: ProductoRequest = {
      sku: 'SKU-001',
      nombre: 'Martillo Reforzado',
      precio: 10500,
      stock: 12,
      categoriaId: 1,
      descripcionTecnica: 'Acero especial',
      descripcionColoquial: 'Martillo pesado'
    };

    service.actualizarProducto(1, payload).subscribe((prod) => {
      expect(prod.nombre).toBe('Martillo Reforzado');
    });

    const req = httpMock.expectOne('/api/v1/productos/1');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 1, ...payload });
  });

  it('should delete product via DELETE', () => {
    service.eliminarProducto(1).subscribe();

    const req = httpMock.expectOne('/api/v1/productos/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should list categories via GET', () => {
    service.getCategorias().subscribe((cats) => {
      expect(cats.length).toBe(1);
    });

    const req = httpMock.expectOne('/api/v1/categorias');
    expect(req.request.method).toBe('GET');
    req.flush([mockCategory]);
  });

  it('should create category via POST', () => {
    const payload: CategoriaRequest = {
      nombre: 'Plomería',
      descripcion: 'Tubos y conexiones',
      padreId: null
    };

    service.crearCategoria(payload).subscribe((cat) => {
      expect(cat.nombre).toBe('Plomería');
    });

    const req = httpMock.expectOne('/api/v1/categorias');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 2, ...payload });
  });

  it('should reindex embeddings via POST', () => {
    const mockReindex: ReindexacionResponse = { procesados: 10, pendientes: 0 };

    service.reindexarEmbeddings().subscribe((res) => {
      expect(res.procesados).toBe(10);
      expect(res.pendientes).toBe(0);
    });

    const req = httpMock.expectOne('/api/v1/productos/reindexar');
    expect(req.request.method).toBe('POST');
    req.flush(mockReindex);
  });
});
