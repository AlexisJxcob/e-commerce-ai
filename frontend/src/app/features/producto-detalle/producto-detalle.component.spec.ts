import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ProductoDetalleComponent } from './producto-detalle.component';
import { CatalogoService } from '../../core/services/catalogo.service';
import { CarritoService } from '../../core/services/carrito.service';
import { Producto } from '../../core/models/producto.models';

describe('ProductoDetalleComponent', () => {
  let component: ProductoDetalleComponent;
  let fixture: ComponentFixture<ProductoDetalleComponent>;
  let catalogoServiceSpy: jasmine.SpyObj<CatalogoService>;
  let carritoServiceSpy: jasmine.SpyObj<CarritoService>;
  let router: Router;

  const mockProduct: Producto = {
    id: 10,
    sku: 'PLOM-001',
    nombre: 'Llave de Paso 1/2 Bronce',
    descripcionTecnica: 'Válvula de corte esférica paso total',
    descripcionColoquial: 'Llave de corte para cañería principal de agua',
    precio: 8990,
    stock: 5,
    categoriaId: 2
  };

  const mockRelated: Producto[] = [
    {
      id: 11,
      sku: 'PLOM-002',
      nombre: 'Teflón Sellador',
      descripcionTecnica: 'Cinta sellante',
      descripcionColoquial: 'Cinta para rosca',
      precio: 990,
      stock: 20,
      categoriaId: 2
    },
    {
      id: 10, // Same ID should be filtered out
      sku: 'PLOM-001',
      nombre: 'Llave de Paso 1/2 Bronce',
      descripcionTecnica: 'Válvula de corte esférica paso total',
      descripcionColoquial: 'Llave de corte para cañería principal de agua',
      precio: 8990,
      stock: 5,
      categoriaId: 2
    }
  ];

  beforeEach(async () => {
    catalogoServiceSpy = jasmine.createSpyObj('CatalogoService', [
      'getProductoById',
      'getProductosPorCategoria'
    ]);
    carritoServiceSpy = jasmine.createSpyObj('CarritoService', ['agregarProducto']);

    catalogoServiceSpy.getProductoById.and.returnValue(of(mockProduct));
    catalogoServiceSpy.getProductosPorCategoria.and.returnValue(of(mockRelated));
    carritoServiceSpy.agregarProducto.and.returnValue(of(null));

    await TestBed.configureTestingModule({
      imports: [ProductoDetalleComponent],
      providers: [
        provideRouter([]),
        { provide: CatalogoService, useValue: catalogoServiceSpy },
        { provide: CarritoService, useValue: carritoServiceSpy }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(ProductoDetalleComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('id', '10');
    fixture.detectChanges();
  });

  it('should load product and related category products', () => {
    expect(component).toBeTruthy();
    expect(catalogoServiceSpy.getProductoById).toHaveBeenCalledWith(10);
    expect(catalogoServiceSpy.getProductosPorCategoria).toHaveBeenCalledWith(2, 8);
    expect(component.producto()).toEqual(mockProduct);
    expect(component.productosRelacionados().length).toBe(1);
    expect(component.productosRelacionados()[0].id).toBe(11);
  });

  it('should render product title, price and stock status', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Llave de Paso 1/2 Bronce');
    expect(el.textContent).toContain('SKU: PLOM-001');
    expect(el.textContent).toContain('$ 8.990');
    expect(el.textContent).toContain('En stock (5 unidades)');
  });

  it('should bound quantity selection between 1 and available stock', () => {
    expect(component.cantidad()).toBe(1);

    component.decrementQuantity();
    expect(component.cantidad()).toBe(1); // cannot go below 1

    component.incrementQuantity();
    component.incrementQuantity();
    expect(component.cantidad()).toBe(3);

    // Increment up to stock = 5
    component.incrementQuantity();
    component.incrementQuantity();
    expect(component.cantidad()).toBe(5);

    component.incrementQuantity();
    expect(component.cantidad()).toBe(5); // cannot exceed stock
  });

  it('should add to cart with the selected quantity and open feedback', () => {
    component.incrementQuantity(); // quantity = 2
    component.addToCart();

    expect(carritoServiceSpy.agregarProducto).toHaveBeenCalledWith(mockProduct, 2);
  });

  it('should navigate back to home on volverAlCatalogo', () => {
    component.volverAlCatalogo();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should display error message if product is not found', () => {
    catalogoServiceSpy.getProductoById.and.returnValue(throwError(() => ({ status: 404 })));
    component.cargarProducto(999);
    fixture.detectChanges();

    expect(component.error()).toContain('El producto solicitado no fue encontrado');
  });
});
