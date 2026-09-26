import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductGridComponent } from './product-grid.component';
import { Producto } from '../../../../core/models/producto.models';

import { provideRouter } from '@angular/router';

describe('ProductGridComponent', () => {
  let component: ProductGridComponent;
  let fixture: ComponentFixture<ProductGridComponent>;

  const mockProducts: Producto[] = [
    {
      id: 1,
      sku: 'PVC-001',
      nombre: 'Tubo PVC 1/2 pulgada',
      descripcionTecnica: 'Tubo hidráulico',
      descripcionColoquial: 'Cañería plástica',
      precio: 4990,
      stock: 10,
      categoriaId: 1
    },
    {
      id: 2,
      sku: 'ADH-002',
      nombre: 'Pegamento PVC 125ml',
      descripcionTecnica: 'Adhesivo solvente',
      descripcionColoquial: 'Pegamento para tubos',
      precio: 3490,
      stock: 5,
      categoriaId: 1
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductGridComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductGridComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should render empty state when products array is empty', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-state')).toBeTruthy();
    expect(el.querySelector('.products-grid')).toBeNull();
  });

  it('should render products grid when products are provided', () => {
    fixture.componentRef.setInput('productos', mockProducts);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-state')).toBeNull();
    expect(el.querySelector('.products-grid')).toBeTruthy();
    expect(el.querySelectorAll('app-product-card').length).toBe(2);
    expect(el.textContent).toContain('2 disponibles');
  });

  it('should bubble up addToCart event from child product card', () => {
    let capturedProduct: Producto | null = null;
    component.addToCart.subscribe((prod) => {
      capturedProduct = prod;
    });

    component.onAddToCart(mockProducts[0]);
    expect(capturedProduct as unknown as Producto).toEqual(mockProducts[0]);
  });
});
