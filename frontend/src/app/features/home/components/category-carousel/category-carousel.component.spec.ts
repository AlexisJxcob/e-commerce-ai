import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CategoryCarouselComponent } from './category-carousel.component';
import { Categoria } from '../../../../core/models/categoria.models';
import { Producto } from '../../../../core/models/producto.models';

import { provideRouter } from '@angular/router';

describe('CategoryCarouselComponent', () => {
  let component: CategoryCarouselComponent;
  let fixture: ComponentFixture<CategoryCarouselComponent>;

  const mockCategory: Categoria = {
    id: 1,
    nombre: 'Herramientas Manuales',
    descripcion: 'Herramientas de uso profesional y doméstico',
    padreId: null
  };

  const mockProducts: Producto[] = [
    {
      id: 1,
      sku: 'HERR-001',
      nombre: 'Martillo de Uña 16oz',
      precio: 8990,
      stock: 12,
      categoriaId: 1,
      descripcionTecnica: 'Mango fibra de vidrio',
      descripcionColoquial: 'Martillo carpintero'
    },
    {
      id: 2,
      sku: 'HERR-002',
      nombre: 'Destornillador Phillips',
      precio: 3500,
      stock: 5,
      categoriaId: 1,
      descripcionTecnica: 'Punta imantada PH2',
      descripcionColoquial: 'Destornillador cruz'
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CategoryCarouselComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(CategoryCarouselComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('categoria', mockCategory);
    fixture.componentRef.setInput('productos', mockProducts);
    fixture.detectChanges();
  });

  it('should create the component with provided inputs', () => {
    expect(component).toBeTruthy();
    expect(component.categoria().nombre).toBe('Herramientas Manuales');
    expect(component.productos().length).toBe(2);
  });

  it('should render the category name and product count in header', () => {
    const el: HTMLElement = fixture.nativeElement;
    const titleEl = el.querySelector('.category-title');
    const badgeEl = el.querySelector('.title-row .p-tag');
    const descEl = el.querySelector('.category-desc');

    expect(titleEl?.textContent?.trim()).toBe('Herramientas Manuales');
    expect(badgeEl?.textContent?.trim()).toContain('2 productos');
    expect(descEl?.textContent?.trim()).toBe('Herramientas de uso profesional y doméstico');
  });

  it('should emit addToCart when a child card emits addToCart', () => {
    let addedProduct: Producto | undefined;
    component.addToCart.subscribe((prod) => {
      addedProduct = prod;
    });

    component.addToCart.emit(mockProducts[0]);
    expect(addedProduct).toEqual(mockProducts[0]);
  });
});
