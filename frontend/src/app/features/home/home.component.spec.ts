import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HomeComponent } from './home.component';
import { AsistenteService } from '../../core/services/asistente.service';

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let asistenteService: AsistenteService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        AsistenteService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    asistenteService = TestBed.inject(AsistenteService);
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
});
