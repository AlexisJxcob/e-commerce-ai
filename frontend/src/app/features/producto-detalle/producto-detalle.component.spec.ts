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
      id: 10,
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

  it('should render the real product data', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Llave de Paso 1/2 Bronce');
    expect(el.textContent).toContain('PLOM-001');
    expect(el.textContent).toContain('$ 8.990');
    expect(el.textContent).toContain('5 unidades');
  });

  it('should not render fabricated commercial data', () => {
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    // Sin reseñas, ratings, cuotas, marcas ni sellos de compra verificada
    expect(text).not.toContain('CMR');
    expect(text).not.toContain('cuotas sin interés');
    expect(text).not.toContain('Compra verificada');
    expect(text).not.toContain('Opiniones');
    expect(text).not.toContain('garantía de compatibilidad');
    expect(text).not.toContain('6 meses legal');
    expect(text).not.toContain('24-48');
  });

  it('should add to cart with the selected quantity', () => {
    component.cantidad.set(2);
    component.addToCart();

    expect(carritoServiceSpy.agregarProducto).toHaveBeenCalledWith(mockProduct, 2);
  });

  it('should not add to cart twice while a request is in flight', () => {
    component.cantidad.set(1);
    component.isAddingToCart.set(true);
    component.addToCart();

    expect(carritoServiceSpy.agregarProducto).not.toHaveBeenCalled();
  });

  it('should navigate back to home on volverAlCatalogo', () => {
    component.volverAlCatalogo();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should display error message if product is not found', () => {
    catalogoServiceSpy.getProductoById.and.returnValue(throwError(() => ({ status: 404 })));
    component.cargarProducto(999);
    fixture.detectChanges();

    expect(component.error()).toContain('no existe en el catálogo');
  });

  it('should reset the failed-image flag when loading another product', () => {
    component.onHeroImageError();
    expect(component.imagen()).toContain('data:image/svg+xml');

    component.cargarProducto(10);
    // Vuelve a intentar la imagen real (placa técnica por defecto, sin flag pegado)
    expect(component.imagen()).toContain('data:image/svg+xml');
  });
});
