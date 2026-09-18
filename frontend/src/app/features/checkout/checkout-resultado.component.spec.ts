import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { CheckoutResultadoComponent } from './checkout-resultado.component';
import { PedidoService } from '../../core/services/pedido.service';
import { CarritoService } from '../../core/services/carrito.service';
import { Pedido } from '../../core/models/pedido.models';

describe('CheckoutResultadoComponent', () => {
  let component: CheckoutResultadoComponent;
  let fixture: ComponentFixture<CheckoutResultadoComponent>;
  let mockPedidoService: jasmine.SpyObj<PedidoService>;
  let mockCarritoService: jasmine.SpyObj<CarritoService>;

  const mockPedido: Pedido = {
    id: 55,
    estado: 'CONFIRMADO',
    total: 12500,
    fechaCreacion: '2026-09-16T21:00:00Z',
    items: [
      {
        productoId: 1,
        cantidad: 1,
        precioUnitario: 12500,
        subtotal: 12500
      }
    ]
  };

  beforeEach(async () => {
    mockPedidoService = jasmine.createSpyObj('PedidoService', ['obtenerPedido']);
    mockPedidoService.obtenerPedido.and.returnValue(of(mockPedido));

    mockCarritoService = jasmine.createSpyObj('CarritoService', ['vaciarCarrito', 'abrirCarrito']);
    mockCarritoService.vaciarCarrito.and.returnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [CheckoutResultadoComponent],
      providers: [
        { provide: PedidoService, useValue: mockPedidoService },
        { provide: CarritoService, useValue: mockCarritoService },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: of({
              status: 'exito',
              token: 'token_mock_123',
              buy_order: '55'
            })
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CheckoutResultadoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should parse success status and buy_order from route', () => {
    expect(component.status()).toBe('exito');
    expect(component.buyOrder()).toBe('55');
    expect(mockCarritoService.vaciarCarrito).toHaveBeenCalled();
    expect(mockPedidoService.obtenerPedido).toHaveBeenCalledWith(55);
  });

  it('should call abrirCarrito on reintentar', () => {
    component.reintentar();
    expect(mockCarritoService.abrirCarrito).toHaveBeenCalled();
  });
});
