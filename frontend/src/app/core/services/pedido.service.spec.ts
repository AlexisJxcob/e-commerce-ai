import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PedidoService } from './pedido.service';
import { Pedido, CheckoutResponse } from '../models/pedido.models';

describe('PedidoService', () => {
  let service: PedidoService;
  let httpMock: HttpTestingController;

  const mockPedido: Pedido = {
    id: 42,
    estado: 'PENDIENTE',
    total: 15990,
    fechaCreacion: '2026-09-16T21:00:00Z',
    items: [
      {
        productoId: 10,
        cantidad: 1,
        precioUnitario: 15990,
        subtotal: 15990
      }
    ]
  };

  const mockCheckout: CheckoutResponse = {
    token: 'test_token_123',
    url: 'https://webpay3gint.transbank.cl/webpayserver/initTransaction'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PedidoService,
        provideHttpClient(withXhr()),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(PedidoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should call POST /api/v1/pedidos/desde-carrito', () => {
    service.crearDesdeCarrito().subscribe((pedido) => {
      expect(pedido).toEqual(mockPedido);
    });

    const req = httpMock.expectOne('/api/v1/pedidos/desde-carrito');
    expect(req.request.method).toBe('POST');
    req.flush(mockPedido);
  });

  it('should call POST /api/v1/pedidos/:id/pagar', () => {
    service.iniciarPago(42).subscribe((res) => {
      expect(res).toEqual(mockCheckout);
    });

    const req = httpMock.expectOne('/api/v1/pedidos/42/pagar');
    expect(req.request.method).toBe('POST');
    req.flush(mockCheckout);
  });

  it('should call GET /api/v1/pedidos/:id', () => {
    service.obtenerPedido(42).subscribe((pedido) => {
      expect(pedido).toEqual(mockPedido);
    });

    const req = httpMock.expectOne('/api/v1/pedidos/42');
    expect(req.request.method).toBe('GET');
    req.flush(mockPedido);
  });
});
