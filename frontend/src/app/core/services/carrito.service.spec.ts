import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { CarritoService } from './carrito.service';
import { AuthService } from '../auth/auth.service';
import { Producto } from '../models/producto.models';

describe('CarritoService', () => {
  let service: CarritoService;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockProduct: Producto = {
    id: 101,
    sku: 'SKU-101',
    nombre: 'Tubo PVC 1/2 pulgada',
    precio: 3500,
    stock: 12,
    categoriaId: 1,
    descripcionTecnica: 'Tubo sanitario PVC',
    descripcionColoquial: 'Cañería plástica'
  };

  beforeEach(() => {
    localStorage.clear();
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isAuthenticated']);
    authServiceSpy.isAuthenticated.and.returnValue(false);

    TestBed.configureTestingModule({
      providers: [
        CarritoService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy }
      ]
    });

    service = TestBed.inject(CarritoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should initialize with empty cart for guests', () => {
    expect(service.items()).toEqual([]);
    expect(service.itemCount()).toBe(0);
    expect(service.total()).toBe(0);
    expect(service.isEmpty()).toBeTrue();
  });

  it('should add product to guest cart and open drawer', () => {
    service.agregarProducto(mockProduct, 2);

    expect(service.items().length).toBe(1);
    expect(service.items()[0].productoId).toBe(101);
    expect(service.items()[0].cantidad).toBe(2);
    expect(service.items()[0].subtotal).toBe(7000);
    expect(service.total()).toBe(7000);
    expect(service.isOpen()).toBeTrue();
  });

  it('should increment quantity if same product is added to guest cart', () => {
    service.agregarProducto(mockProduct, 1);
    service.agregarProducto(mockProduct, 2);

    expect(service.items().length).toBe(1);
    expect(service.items()[0].cantidad).toBe(3);
    expect(service.total()).toBe(10500);
  });

  it('should update quantity and subtotal of an existing item', () => {
    const line = service['agregarItemInvitado'](mockProduct, 1);
    service.actualizarCantidad(line.id, 4);

    expect(service.items()[0].cantidad).toBe(4);
    expect(service.total()).toBe(14000);
  });

  it('should remove item when quantity is set to 0', () => {
    const line = service['agregarItemInvitado'](mockProduct, 1);
    service.actualizarCantidad(line.id, 0);

    expect(service.items().length).toBe(0);
    expect(service.total()).toBe(0);
  });

  it('should remove item directly by id', () => {
    const line = service['agregarItemInvitado'](mockProduct, 2);
    service.eliminarItem(line.id);

    expect(service.items().length).toBe(0);
  });

  it('should empty cart completely', () => {
    service.agregarProducto(mockProduct, 2);
    service.vaciarCarrito();

    expect(service.items().length).toBe(0);
    expect(service.total()).toBe(0);
    expect(service.isEmpty()).toBeTrue();
  });

  it('should toggle and manage drawer state', () => {
    expect(service.isOpen()).toBeFalse();
    service.abrirCarrito();
    expect(service.isOpen()).toBeTrue();
    service.cerrarCarrito();
    expect(service.isOpen()).toBeFalse();
    service.toggleCarrito();
    expect(service.isOpen()).toBeTrue();
  });
});
