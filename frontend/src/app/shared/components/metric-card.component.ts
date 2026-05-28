import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'rip-metric-card',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined" class="metric-card">
      <mat-card-content>
        <span class="metric-value">{{ value() }}</span>
        <span class="metric-label">{{ label() }}</span>
        @if (hint()) {
          <span class="metric-hint">{{ hint() }}</span>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .metric-card {
      height: 100%;
      border-radius: 20px;
    }

    mat-card-content {
      display: grid;
      gap: 10px;
      min-height: 132px;
      align-content: center;
    }

    .metric-value {
      color: #142033;
      font-size: 2.2rem;
      font-weight: 820;
      line-height: 1;
    }

    .metric-label {
      color: #334155;
      font-size: 0.98rem;
      font-weight: 720;
    }

    .metric-hint {
      color: #718096;
      font-size: 0.86rem;
      line-height: 1.45;
    }
  `]
})
export class MetricCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<number | string>();
  readonly hint = input('');
}
