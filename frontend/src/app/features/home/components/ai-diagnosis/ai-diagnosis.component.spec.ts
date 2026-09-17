import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AiDiagnosisComponent } from './ai-diagnosis.component';
import { SugerenciaFerreteria } from '../../../../core/models/asistente.models';

describe('AiDiagnosisComponent', () => {
  let component: AiDiagnosisComponent;
  let fixture: ComponentFixture<AiDiagnosisComponent>;

  const mockSugerencia: SugerenciaFerreteria = {
    palabrasClave: ['pvc', 'adhesivo', 'fuga'],
    herramientas: ['Sierra para metales', 'Lija fina'],
    repuestos: ['Coplón PVC 1/2', 'Adhesivo PVC']
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AiDiagnosisComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(AiDiagnosisComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should compute empty arrays when sugerencia is null', () => {
    expect(component.herramientas()).toEqual([]);
    expect(component.repuestos()).toEqual([]);
    expect(component.palabrasClave()).toEqual([]);
    expect(component.hasItems()).toBeFalse();
  });

  it('should reflect sugerencia input values in computed signals', () => {
    fixture.componentRef.setInput('sugerencia', mockSugerencia);
    fixture.detectChanges();

    expect(component.herramientas()).toEqual(['Sierra para metales', 'Lija fina']);
    expect(component.repuestos()).toEqual(['Coplón PVC 1/2', 'Adhesivo PVC']);
    expect(component.palabrasClave()).toEqual(['pvc', 'adhesivo', 'fuga']);
    expect(component.hasItems()).toBeTrue();
  });

  it('should render query when provided', () => {
    fixture.componentRef.setInput('query', 'Fuga de agua en lavaplatos');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Fuga de agua en lavaplatos');
  });

  it('should emit addKit event when kit button is clicked and productCount > 0', () => {
    let emitted = false;
    component.addKit.subscribe(() => {
      emitted = true;
    });

    fixture.componentRef.setInput('productCount', 3);
    fixture.detectChanges();

    component.onAddKitClick();
    expect(emitted).toBeTrue();
  });

  it('should not emit addKit event when productCount is 0', () => {
    let emitted = false;
    component.addKit.subscribe(() => {
      emitted = true;
    });

    fixture.componentRef.setInput('productCount', 0);
    fixture.detectChanges();

    component.onAddKitClick();
    expect(emitted).toBeFalse();
  });
});
