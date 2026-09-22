import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { AuthModalComponent } from './shared/components/auth-modal';
import { HeaderComponent } from './shared/components/header';
import { CartDrawerComponent } from './shared/components/cart-drawer';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    ToastModule,
    ConfirmDialogModule,
    AuthModalComponent,
    HeaderComponent,
    CartDrawerComponent
  ],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'Repara.ai';
}
