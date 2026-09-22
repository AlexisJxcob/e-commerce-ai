import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthModalComponent } from './shared/components/auth-modal';
import { HeaderComponent } from './shared/components/header';
import { CartDrawerComponent } from './shared/components/cart-drawer';

@Component({
    selector: 'app-root',
    imports: [RouterOutlet, AuthModalComponent, HeaderComponent, CartDrawerComponent],
    templateUrl: './app.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'Repara.ai';
}
