import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AsistenteService } from './asistente.service';
import { BusquedaInteligenteResponse } from '../models/asistente.models';

describe('AsistenteService', () => {
  let service: AsistenteService;
  let httpMock: HttpTestingController;

  const mockResponse: BusquedaInteligenteResponse = {
    sugerencia: {
      palabrasClave: ['pvc', 'fuga'],
      herramientas: ['llave inglesa', 'teflón'],
      repuestos: ['codo pvc 1/2']
    },
    productos: [
      {
        id: 1,
        sku: 'PVC-001',
        nombre: 'Codo PVC 1/2 pulgada',
        descripcionTecnica: 'Codo 90 grados PVC sanitario',
        descripcionColoquial: 'codo pvc para tubería de desagüe',
        precio: 1200,
        stock: 25,
        categoriaId: null
      }
    ]
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AsistenteService,
        provideHttpClient(withXhr()),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(AsistenteService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
    expect(service.isLoading()).toBeFalse();
    expect(service.hasResult()).toBeFalse();
  });

  it('should fetch recommendations and update signals on success', () => {
    service.buscar('fuga en cañería').subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });

    expect(service.isLoading()).toBeTrue();
    expect(service.currentQuery()).toBe('fuga en cañería');

    const req = httpMock.expectOne('/api/v1/productos/asistente?q=fuga%20en%20ca%C3%B1er%C3%ADa');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(service.isLoading()).toBeFalse();
    expect(service.hasResult()).toBeTrue();
    expect(service.sugerencia()?.palabrasClave).toEqual(['pvc', 'fuga']);
    expect(service.productos().length).toBe(1);
    expect(service.error()).toBeNull();
  });

  it('should handle errors and update error signal', () => {
    service.buscar('problema desconocido').subscribe({
      next: () => fail('Should have failed'),
      error: (err) => {
        expect(err).toBeTruthy();
      }
    });

    const req = httpMock.expectOne('/api/v1/productos/asistente?q=problema%20desconocido');
    req.flush({ message: 'Error en servicio de IA' }, { status: 500, statusText: 'Server Error' });

    expect(service.isLoading()).toBeFalse();
    expect(service.hasResult()).toBeFalse();
    expect(service.error()).toBe('Error en servicio de IA');
  });

  it('should reset state on limpiar()', () => {
    service.buscar('algo').subscribe();
    const req = httpMock.expectOne('/api/v1/productos/asistente?q=algo');
    req.flush(mockResponse);

    expect(service.hasResult()).toBeTrue();

    service.limpiar();
    expect(service.currentQuery()).toBe('');
    expect(service.response()).toBeNull();
    expect(service.hasResult()).toBeFalse();
    expect(service.error()).toBeNull();
  });
});
