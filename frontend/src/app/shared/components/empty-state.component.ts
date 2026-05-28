import { Component, input } from '@angular/core';

@Component({
  selector: 'rip-empty-state',
  standalone: true,
  template: `
    <div class="empty-state">
      <strong>{{ title() }}</strong>
      @if (message()) {
        <span>{{ message() }}</span>
      }
    </div>
  `,
  styles: [`
    .empty-state {
      display: grid;
      gap: 8px;
      padding: 26px 24px;
      border: 1px dashed #cbd8e6;
      border-radius: 18px;
      background:
        linear-gradient(180deg, rgba(248, 251, 253, 0.96), rgba(255, 255, 255, 0.98)),
        linear-gradient(90deg, rgba(49, 111, 141, 0.05), rgba(45, 140, 120, 0.04));
      color: #5e6b7c;
      text-align: center;
      line-height: 1.55;
    }

    strong {
      color: #253044;
      font-size: 1rem;
    }
  `]
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly message = input('');
}
