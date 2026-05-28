import { Component, input } from '@angular/core';

@Component({
  selector: 'rip-tag-chip',
  standalone: true,
  template: `
    <span class="tag-chip" [class.status-chip]="tone() === 'status'" [class.type-chip]="tone() === 'type'">
      {{ label() }}
    </span>
  `,
  styles: [`
    .tag-chip {
      display: inline-flex;
      align-items: center;
      min-height: 24px;
      max-width: 100%;
      padding: 4px 10px;
      border: 1px solid #d8e2ed;
      border-radius: 999px;
      background: #f8fbfd;
      color: #334155;
      font-size: 0.78rem;
      font-weight: 650;
      line-height: 1.2;
      overflow-wrap: anywhere;
    }

    .status-chip {
      border-color: #bddfd4;
      background: #eefaf5;
      color: #17634f;
    }

    .type-chip {
      border-color: #c9d8f2;
      background: #f1f6ff;
      color: #274d8c;
    }
  `]
})
export class TagChipComponent {
  readonly label = input.required<string>();
  readonly tone = input<'default' | 'status' | 'type'>('default');
}
