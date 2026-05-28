import { Component, input } from '@angular/core';

@Component({
  selector: 'rip-error-state',
  standalone: true,
  template: `
    <div class="error-state">
      <strong>{{ title() }}</strong>
      <span>{{ message() }}</span>
    </div>
  `,
  styles: [`
    .error-state {
      display: grid;
      gap: 8px;
      padding: 18px 20px;
      border: 1px solid #f0b4b4;
      border-radius: 16px;
      background: #fff6f6;
      color: #9b1c1c;
      line-height: 1.5;
    }
  `]
})
export class ErrorStateComponent {
  readonly title = input('No se pudo completar la acción');
  readonly message = input.required<string>();
}
