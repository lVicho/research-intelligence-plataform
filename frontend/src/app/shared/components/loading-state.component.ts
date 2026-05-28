import { Component, input } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'rip-loading-state',
  standalone: true,
  imports: [MatProgressSpinnerModule],
  template: `
    <div class="loading-state">
      <mat-spinner diameter="28" />
      <span>{{ message() }}</span>
    </div>
  `,
  styles: [`
    .loading-state {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 18px 20px;
      border: 1px solid #d9e6f2;
      border-radius: 16px;
      background: #f7fbff;
      color: #4d5d70;
    }
  `]
})
export class LoadingStateComponent {
  readonly message = input('Cargando...');
}
