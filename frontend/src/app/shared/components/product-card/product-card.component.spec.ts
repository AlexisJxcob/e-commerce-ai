import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ProductCardComponent } from './product-card.component';
import { Producto } from '../../../core/models/producto.models';

describe('ProductCardComponent', () => {
  let component: ProductCardComponent;
  let fixture: ComponentFixture<ProductCardComponent>;
  let router: Router;

  const mockProduct: Producto = {
    id: 1,
    sku: 'PVC-001',
    nombre: 'Tubo PVC Hidráulico 1/2 pulgada',
    descripcionTecnica: 'Tubo clase 10 para agua fría norma NCh396',
    descripcionColoquial: 'Cañería plástica para reparar filtraciones',
    precio: 4990,
    stock: 12,
    categoriaId: 2
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductCardComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(ProductCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('producto', mockProduct);
    fixture.detectChanges();
  });

  it('should create the product card', () => {
    expect(component).toBeTruthy();
  });

  it('should render product name, sku and formatted price', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Tubo PVC Hidráulico 1/2 pulgada');
    expect(el.textContent).toContain('PVC-001');
    expect(el.textContent).toContain('$ 4.990');
  });

  it('should display green stock badge when stock > 0', () => {
    const el = fixture.nativeElement as HTMLElement;
    const stockBadge = el.querySelector('.badge-stock');
    expect(stockBadge).toBeTruthy();
    expect(stockBadge?.textContent).toContain('Stock: 12');
  });

  it('should display out of stock badge when stock is 0 and disable quick-buy button', () => {
    fixture.componentRef.setInput('producto', { ...mockProduct, stock: 0 });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const stockBadge = el.querySelector('.badge-stock');
    const outOfStockBadge = el.querySelector('.badge-out-of-stock');
    const button = el.querySelector('.quick-buy-btn') as HTMLButtonElement;

    expect(stockBadge).toBeNull();
    expect(outOfStockBadge).toBeTruthy();
    expect(outOfStockBadge?.textContent).toContain('Sin stock');
    expect(button.disabled).toBeTrue();
  });

  it('should emit addToCart event with product on quick-buy click', () => {
    let emittedProduct: Producto | null = null;
    component.addToCart.subscribe((prod) => {
      emittedProduct = prod;
    });

    component.onQuickBuy();
    expect(emittedProduct as unknown as Producto).toEqual(mockProduct);
  });

  it('should navigate to product detail on card click', () => {
    const card = fixture.nativeElement.querySelector('.product-card') as HTMLElement;
    card.click();
    expect(router.navigate).toHaveBeenCalledWith(['/productos', mockProduct.id]);
  });

  it('should navigate to product detail on Enter keydown', () => {
    const card = fixture.nativeElement.querySelector('.product-card') as HTMLElement;
    const event = new KeyboardEvent('keydown', { key: 'Enter' });
    card.dispatchEvent(event);
    expect(router.navigate).toHaveBeenCalledWith(['/productos', mockProduct.id]);
  });

  it('should not navigate when clicking quick-buy button', () => {
    const button = fixture.nativeElement.querySelector('.quick-buy-btn') as HTMLButtonElement;
    button.click();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});

