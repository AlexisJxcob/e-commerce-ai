import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { AdminComponent } from './admin.component';
import { AdminService } from '../../core/services/admin.service';
import { Producto, PageResponse } from '../../core/models/producto.models';
import { Categoria } from '../../core/models/categoria.models';
import { ReindexacionResponse } from '../../core/models/asistente.models';

describe('AdminComponent', () => {
  let component: AdminComponent;
  let fixture: ComponentFixture<AdminComponent>;
  let mockAdminService: jasmine.SpyObj<AdminService>;

  const mockProduct: Producto = {
    id: 1,
    sku: 'SKU-TEST',
    nombre: 'Taladro Percutor',
    precio: 45000,
    stock: 8,
    categoriaId: 1,
    descripcionTecnica: '750W velocidad variable',
    descripcionColoquial: 'Taladro para concreto'
  };

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

  const mockCategory: Categoria = {
    id: 1,
    nombre: 'Herramientas Eléctricas',
    descripcion: 'Taladros y sierras',
    padreId: null
  };

  const mockReindex: ReindexacionResponse = {
    procesados: 5,
    pendientes: 0
  };

  beforeEach(async () => {
    mockAdminService = jasmine.createSpyObj('AdminService', [
      'getProductos',
      'getCategorias',
      'actualizarStock',
      'crearProducto',
      'actualizarProducto',
      'eliminarProducto',
      'crearCategoria',
      'eliminarCategoria',
      'reindexarEmbeddings'
    ]);

    mockAdminService.getProductos.and.returnValue(of(mockPage));
    mockAdminService.getCategorias.and.returnValue(of([mockCategory]));
    mockAdminService.actualizarStock.and.returnValue(of({ ...mockProduct, stock: 15 }));
    mockAdminService.reindexarEmbeddings.and.returnValue(of(mockReindex));

    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AdminService, useValue: mockAdminService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create AdminComponent and load initial data', () => {
    expect(component).toBeTruthy();
    expect(mockAdminService.getProductos).toHaveBeenCalled();
    expect(mockAdminService.getCategorias).toHaveBeenCalled();
    expect(component.productos().length).toBe(1);
    expect(component.categorias().length).toBe(1);
  });

  it('should switch tabs', () => {
    expect(component.activeTab()).toBe('productos');
    component.setTab('categorias');
    expect(component.activeTab()).toBe('categorias');
    component.setTab('ia');
    expect(component.activeTab()).toBe('ia');
  });

  it('should save inline stock update', () => {
    component.stockEditingMap.set({ 1: 15 });
    component.guardarStock(mockProduct);

    expect(mockAdminService.actualizarStock).toHaveBeenCalledWith(1, 15);
    expect(component.productos()[0].stock).toBe(15);
    expect(component.successMessage()).toContain('Stock de "Taladro Percutor" actualizado a 15');
  });

  it('should trigger vector reindexing', () => {
    component.reindexarEmbeddings();

    expect(mockAdminService.reindexarEmbeddings).toHaveBeenCalled();
    expect(component.reindexResult()).toEqual(mockReindex);
    expect(component.isReindexing()).toBeFalse();
    expect(component.successMessage()).toContain('5 productos procesados');
  });
});
