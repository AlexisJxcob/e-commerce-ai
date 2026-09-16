import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TypewriterDirective } from './typewriter.directive';

@Component({
  standalone: true,
  imports: [TypewriterDirective],
  template: `
    <input
      type="text"
      appTypewriter
      [phrases]="testPhrases"
      [typingSpeed]="10"
      [deletingSpeed]="10"
      [pauseDuration]="50"
      [deletePauseDuration]="50"
    />
  `
})
class TestHostComponent {
  testPhrases = ['Fuga de agua...', 'Cerradura trabada...'];
}

describe('TypewriterDirective', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let inputEl: HTMLInputElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    inputEl = fixture.nativeElement.querySelector('input');
  });

  it('should initialize and type characters into the placeholder', fakeAsync(() => {
    tick(40);
    fixture.detectChanges();
    expect(inputEl.placeholder.length).toBeGreaterThan(0);
  }));

  it('should pause typing on focus', fakeAsync(() => {
    tick(20);
    const placeholderBefore = inputEl.placeholder;

    inputEl.dispatchEvent(new Event('focus'));
    tick(200);
    fixture.detectChanges();

    expect(inputEl.placeholder).toBe(placeholderBefore);
  }));

  it('should resume typing on blur if input is empty', fakeAsync(() => {
    inputEl.dispatchEvent(new Event('focus'));
    tick(100);

    inputEl.value = '';
    inputEl.dispatchEvent(new Event('blur'));
    tick(250);
    fixture.detectChanges();

    expect(inputEl.placeholder.length).toBeGreaterThan(0);
  }));
});
