import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { CheckoutComponent } from './checkout.component';
import { CarritoService } from '../../core/services/carrito.service';
import { AuthService } from '../../core/auth/auth.service';
import { AuthModalService } from '../../core/auth/auth-modal.service';
import { PedidoService } from '../../core/services/pedido.service';
import { Carrito } from '../../core/models/carrito.models';
import { Pedido, CheckoutResponse } from '../../core/models/pedido.models';

describe('CheckoutComponent', () => {
  let component: CheckoutComponent;
  let fixture: ComponentFixture<CheckoutComponent>;
  let mockCarritoService: jasmine.SpyObj<CarritoService>;
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let mockAuthModalService: jasmine.SpyObj<AuthModalService>;
  let mockPedidoService: jasmine.SpyObj<PedidoService>;
  let mockRouter: jasmine.SpyObj<Router>;

  const mockCart: Carrito = {
    items: [
      {
        id: 1,
        productoId: 101,
        nombre: 'Taladro Percutor 750W',
        precioUnitario: 45000,
        cantidad: 1,
        subtotal: 45000
      }
    ],
    total: 45000
  };

  const mockPedido: Pedido = {
    id: 123,
    estado: 'PENDIENTE',
    total: 45000,
    fechaCreacion: '2026-09-18T10:00:00Z',
    items: []
  };

  const mockCheckoutResponse: CheckoutResponse = {
    token: 'tbk-token-mock-123',
    url: 'https://webpay3gint.transbank.cl/webpayserver/initTransaction'
  };

  beforeEach(async () => {
    mockCarritoService = jasmine.createSpyObj(
      'CarritoService',
      ['actualizarCantidad', 'eliminarItem', 'vaciarCarrito', 'sincronizarCarritoInvitado'],
      {
        carrito: signal<Carrito | null>(mockCart),
        items: signal(mockCart.items),
        itemCount: signal(1),
        total: signal(45000),
        isEmpty: signal(false)
      }
    );
    mockCarritoService.actualizarCantidad.and.returnValue(of(null));
    mockCarritoService.eliminarItem.and.returnValue(of(void 0));
    mockCarritoService.sincronizarCarritoInvitado.and.returnValue(of(void 0));

    mockAuthService = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'currentUser']);
    mockAuthService.isAuthenticated.and.returnValue(true);
    mockAuthService.currentUser.and.returnValue({
      id: 1,
      username: 'alexis',
      rol: 'CLIENTE',
      email: 'alexis@example.com'
    });

    mockAuthModalService = jasmine.createSpyObj('AuthModalService', ['openLogin', 'setPendingAction']);

    mockPedidoService = jasmine.createSpyObj('PedidoService', ['crearDesdeCarrito', 'iniciarPago', 'redirigirAWebpay']);
    mockPedidoService.crearDesdeCarrito.and.returnValue(of(mockPedido));
    mockPedidoService.iniciarPago.and.returnValue(of(mockCheckoutResponse));

    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideNoopAnimations(),
        { provide: CarritoService, useValue: mockCarritoService },
        { provide: AuthService, useValue: mockAuthService },
        { provide: AuthModalService, useValue: mockAuthModalService },
        { provide: PedidoService, useValue: mockPedidoService },
        { provide: Router, useValue: mockRouter }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and start on step "resumen"', () => {
    expect(component).toBeTruthy();
    expect(component.currentStep()).toBe('resumen');
  });

  it('should advance to "datos" from "resumen"', () => {
    component.avanzarADatos();
    expect(component.currentStep()).toBe('datos');
  });

  it('should not advance to "entrega" if datosForm is invalid', () => {
    component.currentStep.set('datos');
    component.datosForm.reset();
    component.avanzarAEntrega();
    expect(component.currentStep()).toBe('datos');
    expect(component.datosForm.invalid).toBeTrue();
  });

  it('should advance to "entrega" when datosForm is valid with valid Chilean RUT', () => {
    component.currentStep.set('datos');
    component.datosForm.setValue({
      nombres: 'Alexis',
      apellidos: 'Jacob',
      rut: '11.111.111-1',
      email: 'alexis@example.cl',
      telefono: '+56912345678'
    });
    expect(component.datosForm.valid).toBeTrue();

    component.avanzarAEntrega();
    expect(component.currentStep()).toBe('entrega');
  });

  it('should calculate shipping fee as 0 for "retiro" and 3990 for "despacho" when under 50k', () => {
    component.metodoEntrega.set('retiro');
    expect(component.costoDespacho()).toBe(0);
    expect(component.total()).toBe(45000);

    component.metodoEntrega.set('despacho');
    expect(component.costoDespacho()).toBe(3990);
    expect(component.total()).toBe(48990);
  });

  it('should advance to "pago" when delivery method is retiro', () => {
    component.currentStep.set('entrega');
    component.metodoEntrega.set('retiro');
    component.avanzarAPago();
    expect(component.currentStep()).toBe('pago');
  });

  it('should not process payment if terms are not accepted', () => {
    component.currentStep.set('pago');
    component.terminosAceptados.set(false);
    component.procesarPago();

    expect(mockPedidoService.crearDesdeCarrito).not.toHaveBeenCalled();
  });

  it('should orchestrate Webpay Plus payment when authenticated and terms accepted', () => {
    component.currentStep.set('pago');
    component.terminosAceptados.set(true);
    component.procesarPago();

    expect(mockPedidoService.crearDesdeCarrito).toHaveBeenCalled();
    expect(mockPedidoService.iniciarPago).toHaveBeenCalledWith(123);
    expect(mockPedidoService.redirigirAWebpay).toHaveBeenCalledWith(
      'tbk-token-mock-123',
      mockCheckoutResponse.url
    );
  });

  it('should open login modal if guest attempts payment on step 4', () => {
    mockAuthService.isAuthenticated.and.returnValue(false);
    component.currentStep.set('pago');
    component.terminosAceptados.set(true);
    component.procesarPago();

    expect(mockAuthModalService.setPendingAction).toHaveBeenCalled();
    expect(mockAuthModalService.openLogin).toHaveBeenCalled();
    expect(mockPedidoService.crearDesdeCarrito).not.toHaveBeenCalled();
  });
});
