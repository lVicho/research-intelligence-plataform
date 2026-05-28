import { Component, input } from '@angular/core';

@Component({
  selector: 'rip-status-chip',
  standalone: true,
  template: `
    <span
      class="status-chip"
      [class.success]="tone() === 'success'"
      [class.warning]="tone() === 'warning'"
      [class.danger]="tone() === 'danger'"
      [class.info]="tone() === 'info'"
    >
      {{ label() }}
    </span>
  `,
  styles: [`
    .status-chip {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: fit-content;
      min-height: 28px;
      max-width: 100%;
      min-width: 0;
      padding: 6px 12px;
      border: 1px solid #d8e2ed;
      border-radius: 999px;
      background: #f8fbfd;
      color: #334155;
      font-size: 0.79rem;
      font-weight: 720;
      line-height: 1.3;
      text-align: center;
      white-space: normal;
      overflow-wrap: anywhere;
    }

    .success {
      border-color: #b9d9ca;
      background: #f0faf4;
      color: #17634f;
    }

    .warning {
      border-color: #efd18b;
      background: #fff9e9;
      color: #72510d;
    }

    .danger {
      border-color: #f0b4b4;
      background: #fff6f6;
      color: #9b1c1c;
    }

    .info {
      border-color: #c9d8f2;
      background: #f1f6ff;
      color: #274d8c;
    }
  `]
})
export class StatusChipComponent {
  readonly label = input.required<string>();
  readonly tone = input<'neutral' | 'success' | 'warning' | 'danger' | 'info'>('neutral');
}
