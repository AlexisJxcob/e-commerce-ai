import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CartDrawerComponent } from './cart-drawer.component';
import { CarritoService } from '../../../core/services/carrito.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalService } from '../../../core/auth/auth-modal.service';
import { PedidoService } from '../../../core/services/pedido.service';
import { Carrito } from '../../../core/models/carrito.models';
import { Pedido, CheckoutResponse } from '../../../core/models/pedido.models';
import { of } from 'rxjs';

describe('CartDrawerComponent', () => {
  let component: CartDrawerComponent;
  let fixture: ComponentFixture<CartDrawerComponent>;
  let mockCarritoService: jasmine.SpyObj<CarritoService>;
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let mockAuthModalService: jasmine.SpyObj<AuthModalService>;
  let mockPedidoService: jasmine.SpyObj<PedidoService>;

  const mockCart: Carrito = {
    items: [
      {
        id: 1,
        productoId: 10,
        nombre: 'Cinta Teflón 3/4',
        precioUnitario: 1200,
        cantidad: 2,
        subtotal: 2400
      }
    ],
    total: 2400
  };

  const mockPedido: Pedido = {
    id: 99,
    estado: 'PENDIENTE',
    total: 2400,
    fechaCreacion: '2026-09-16T21:00:00Z',
    items: []
  };

  const mockCheckout: CheckoutResponse = {
    token: 'token-abc',
    url: 'https://webpay3gint.transbank.cl/webpayserver/initTransaction'
  };

  beforeEach(async () => {
    mockCarritoService = jasmine.createSpyObj(
      'CarritoService',
      ['cerrarCarrito', 'abrirCarrito', 'actualizarCantidad', 'eliminarItem', 'vaciarCarrito', 'sincronizarCarritoInvitado'],
      {
        carrito: signal<Carrito | null>(mockCart),
        isOpen: signal<boolean>(true),
        items: signal(mockCart.items),
        itemCount: signal(2),
        total: signal(2400),
        isEmpty: signal(false)
      }
    );

    mockCarritoService.actualizarCantidad.and.returnValue(of(null));
    mockCarritoService.eliminarItem.and.returnValue(of(void 0));
    mockCarritoService.vaciarCarrito.and.returnValue(of(void 0));
    mockCarritoService.sincronizarCarritoInvitado.and.returnValue(of(void 0));

    mockAuthService = jasmine.createSpyObj('AuthService', ['isAuthenticated']);
    mockAuthService.isAuthenticated.and.returnValue(true);

    mockAuthModalService = jasmine.createSpyObj('AuthModalService', ['openLogin', 'setPendingAction']);

    mockPedidoService = jasmine.createSpyObj('PedidoService', ['crearDesdeCarrito', 'iniciarPago', 'redirigirAWebpay']);
    mockPedidoService.crearDesdeCarrito.and.returnValue(of(mockPedido));
    mockPedidoService.iniciarPago.and.returnValue(of(mockCheckout));

    await TestBed.configureTestingModule({
      imports: [CartDrawerComponent],
      providers: [
        provideNoopAnimations(),
        { provide: CarritoService, useValue: mockCarritoService },
        { provide: AuthService, useValue: mockAuthService },
        { provide: AuthModalService, useValue: mockAuthModalService },
        { provide: PedidoService, useValue: mockPedidoService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CartDrawerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call cerrarCarrito when visible changes to false', () => {
    component.onVisibleChange(false);
    expect(mockCarritoService.cerrarCarrito).toHaveBeenCalled();
  });

  it('should delegate increment to carritoService', () => {
    component.incrementar(1, 2);
    expect(mockCarritoService.actualizarCantidad).toHaveBeenCalledWith(1, 3);
  });

  it('should delegate decrement to carritoService', () => {
    component.decrementar(1, 2);
    expect(mockCarritoService.actualizarCantidad).toHaveBeenCalledWith(1, 1);
  });

  it('should delegate delete item to carritoService', () => {
    component.eliminar(1);
    expect(mockCarritoService.eliminarItem).toHaveBeenCalledWith(1);
  });

  it('should delegate vaciar to carritoService', () => {
    component.vaciar();
    expect(mockCarritoService.vaciarCarrito).toHaveBeenCalled();
  });

  it('should open login and set pending action if guest clicks checkout', () => {
    mockAuthService.isAuthenticated.and.returnValue(false);
    component.onCheckout();
    expect(mockAuthModalService.setPendingAction).toHaveBeenCalled();
    expect(mockAuthModalService.openLogin).toHaveBeenCalled();
  });

  it('should orchestrate webpay checkout if authenticated', () => {
    mockAuthService.isAuthenticated.and.returnValue(true);
    component.onCheckout();

    expect(mockPedidoService.crearDesdeCarrito).toHaveBeenCalled();
    expect(mockPedidoService.iniciarPago).toHaveBeenCalledWith(99);
    expect(mockPedidoService.redirigirAWebpay).toHaveBeenCalledWith('token-abc', mockCheckout.url);
  });
});
