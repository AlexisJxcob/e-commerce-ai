import {
  Directive,
  ElementRef,
  HostListener,
  PLATFORM_ID,
  Renderer2,
  DestroyRef,
  OnInit,
  inject,
  input
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export const DEFAULT_TYPEWRITER_PHRASES: string[] = [
  'Tengo una fuga en una cañería de PVC bajo el lavaplatos...',
  'Quiero colgar un mueble pesado en pared de tabique...',
  'La cerradura de la puerta se traba al girar la llave...',
  'Necesito cambiar un interruptor de luz en el baño...'
];

@Directive({
  selector: '[appTypewriter]',
  standalone: true
})
export class TypewriterDirective implements OnInit {
  private readonly el = inject(ElementRef<HTMLInputElement | HTMLTextAreaElement>);
  private readonly renderer = inject(Renderer2);
  private readonly destroyRef = inject(DestroyRef);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  readonly phrases = input<string[]>(DEFAULT_TYPEWRITER_PHRASES);
  readonly typingSpeed = input<number>(55);
  readonly deletingSpeed = input<number>(30);
  readonly pauseDuration = input<number>(2200);
  readonly deletePauseDuration = input<number>(450);

  private timeoutId: ReturnType<typeof setTimeout> | null = null;
  private currentPhraseIndex = 0;
  private currentCharIndex = 0;
  private isDeleting = false;
  private isPaused = false;

  ngOnInit(): void {
    if (!this.isBrowser) {
      const initial = this.phrases()[0] ?? '';
      this.renderer.setAttribute(this.el.nativeElement, 'placeholder', initial);
      return;
    }

    this.destroyRef.onDestroy(() => this.clearTimer());
    this.startTypingLoop();
  }

  @HostListener('focus')
  onFocus(): void {
    this.isPaused = true;
    this.clearTimer();
  }

  @HostListener('blur')
  onBlur(): void {
    const value = this.el.nativeElement.value;
    if (!value || value.trim().length === 0) {
      this.isPaused = false;
      this.scheduleNextStep(200);
    }
  }

  @HostListener('input')
  onInput(): void {
    const value = this.el.nativeElement.value;
    if (value && value.length > 0) {
      this.isPaused = true;
      this.clearTimer();
    }
  }

  private startTypingLoop(): void {
    this.clearTimer();
    this.step();
  }

  private step(): void {
    if (this.isPaused) {
      return;
    }

    const currentPhrases = this.phrases();
    if (!currentPhrases || currentPhrases.length === 0) {
      return;
    }

    const fullPhrase = currentPhrases[this.currentPhraseIndex % currentPhrases.length];

    if (!this.isDeleting) {
      this.currentCharIndex++;
      const textToDisplay = fullPhrase.slice(0, this.currentCharIndex);
      this.renderer.setAttribute(this.el.nativeElement, 'placeholder', textToDisplay);

      if (this.currentCharIndex >= fullPhrase.length) {
        this.isDeleting = true;
        this.scheduleNextStep(this.pauseDuration());
      } else {
        const jitter = Math.floor(Math.random() * 30) - 15;
        const delay = Math.max(20, this.typingSpeed() + jitter);
        this.scheduleNextStep(delay);
      }
    } else {
      this.currentCharIndex--;
      const textToDisplay = fullPhrase.slice(0, Math.max(0, this.currentCharIndex));
      this.renderer.setAttribute(this.el.nativeElement, 'placeholder', textToDisplay);

      if (this.currentCharIndex <= 0) {
        this.isDeleting = false;
        this.currentPhraseIndex = (this.currentPhraseIndex + 1) % currentPhrases.length;
        this.scheduleNextStep(this.deletePauseDuration());
      } else {
        this.scheduleNextStep(this.deletingSpeed());
      }
    }
  }

  private scheduleNextStep(delay: number): void {
    this.clearTimer();
    this.timeoutId = setTimeout(() => this.step(), delay);
  }

  private clearTimer(): void {
    if (this.timeoutId !== null) {
      clearTimeout(this.timeoutId);
      this.timeoutId = null;
    }
  }
}
