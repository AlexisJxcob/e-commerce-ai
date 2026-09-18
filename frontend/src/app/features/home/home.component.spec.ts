import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { HomeComponent } from './home.component';
import { AsistenteService } from '../../core/services/asistente.service';
import { CatalogoService } from '../../core/services/catalogo.service';
import { BusquedaInteligenteResponse } from '../../core/models/asistente.models';
import { Producto } from '../../core/models/producto.models';
import { CategoriaConProductos } from '../../core/models/categoria.models';

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let asistenteService: AsistenteService;
  let catalogoService: jasmine.SpyObj<CatalogoService>;

  const mockProduct: Producto = {
    id: 10,
    sku: 'SKU-010',
    nombre: 'Llave Francesa 10 pulgadas',
    descripcionTecnica: 'Cromo vanadio',
    descripcionColoquial: 'Llave ajustable',
    precio: 8990,
    stock: 7,
    categoriaId: 1
  };

  const mockResponse: BusquedaInteligenteResponse = {
    sugerencia: {
      palabrasClave: ['fuga', 'pvc'],
      herramientas: ['Llave francesa'],
      repuestos: ['Coplón PVC']
    },
    productos: [mockProduct]
  };

  const mockCatalogoGrupos: CategoriaConProductos[] = [
    {
      categoria: { id: 1, nombre: 'Herramientas', descripcion: 'Manuales', padreId: null },
      productos: [mockProduct]
    }
  ];

  beforeEach(async () => {
    const catalogoSpy = jasmine.createSpyObj('CatalogoService', ['getCatalogoAgrupado']);
    catalogoSpy.getCatalogoAgrupado.and.returnValue(of(mockCatalogoGrupos));

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        AsistenteService,
        { provide: CatalogoService, useValue: catalogoSpy },
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    asistenteService = TestBed.inject(AsistenteService);
    catalogoService = TestBed.inject(CatalogoService) as jasmine.SpyObj<CatalogoService>;
    fixture.detectChanges();
  });

  it('should create home component', () => {
    expect(component).toBeTruthy();
  });

  it('should compute isCompact correctly based on assistant state', () => {
    expect(component.isCompact()).toBeFalse();

    asistenteService.isLoading.set(true);
    expect(component.isCompact()).toBeTrue();

    asistenteService.isLoading.set(false);
    asistenteService.error.set('Error test');
    expect(component.isCompact()).toBeTrue();

    asistenteService.error.set(null);
    asistenteService.response.set(mockResponse);
    expect(component.isCompact()).toBeTrue();
  });

  it('should call asistente.buscar on search', () => {
    spyOn(asistenteService, 'buscar').and.callThrough();
    component.onSearch('fuga de agua');
    expect(asistenteService.buscar).toHaveBeenCalledWith('fuga de agua');
  });

  it('should reset search on reset()', () => {
    spyOn(asistenteService, 'limpiar');
    component.reset();
    expect(asistenteService.limpiar).toHaveBeenCalled();
  });

  it('should render asymmetric layout with diagnosis and product grid when result is present', () => {
    asistenteService.response.set(mockResponse);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.results-asymmetric-layout')).toBeTruthy();
    expect(el.querySelector('app-ai-diagnosis')).toBeTruthy();
    expect(el.querySelector('app-product-grid')).toBeTruthy();
  });

  it('should handle onAddKit and onAddToCart without error', () => {
    asistenteService.response.set(mockResponse);
    fixture.detectChanges();

    expect(() => component.onAddKit()).not.toThrow();
    expect(() => component.onAddToCart(mockProduct)).not.toThrow();
  });

  it('should render catalog section with category carousels when not searching', () => {
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.catalog-section')).toBeTruthy();
    expect(el.querySelector('app-category-carousel')).toBeTruthy();
  });

  it('should hide catalog section when search has result', () => {
    asistenteService.response.set(mockResponse);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.catalog-section')).toBeFalsy();
  });
});
