import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HeroSearchComponent } from './hero-search.component';

describe('HeroSearchComponent', () => {
  let component: HeroSearchComponent;
  let fixture: ComponentFixture<HeroSearchComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeroSearchComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(HeroSearchComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create hero search component', () => {
    expect(component).toBeTruthy();
  });

  it('should emit search event when submitting a non-empty query', () => {
    spyOn(component.search, 'emit');
    component.queryText.set('fuga en pvc');
    component.onSearchSubmit();
    expect(component.search.emit).toHaveBeenCalledWith('fuga en pvc');
  });

  it('should not emit search event when query is empty', () => {
    spyOn(component.search, 'emit');
    component.queryText.set('   ');
    component.onSearchSubmit();
    expect(component.search.emit).not.toHaveBeenCalled();
  });

  it('should select quick suggestion and emit search', () => {
    spyOn(component.search, 'emit');
    component.selectSuggestion('Colgar repisa en tabique');
    expect(component.queryText()).toBe('Colgar repisa en tabique');
    expect(component.search.emit).toHaveBeenCalledWith('Colgar repisa en tabique');
  });
});
