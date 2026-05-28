import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'rip-section-card',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card appearance="outlined" class="section-card">
      @if (title() || subtitle() || eyebrow()) {
        <mat-card-header>
          <div class="section-card-header">
            @if (eyebrow()) {
              <p class="section-kicker">{{ eyebrow() }}</p>
            }
            @if (title()) {
              <mat-card-title>{{ title() }}</mat-card-title>
            }
            @if (subtitle()) {
              <p class="section-card-subtitle">{{ subtitle() }}</p>
            }
          </div>
        </mat-card-header>
      }
      <mat-card-content>
        <ng-content />
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .section-card {
      height: 100%;
      border-radius: 24px;
    }

    .section-card-header {
      display: grid;
      gap: 8px;
    }

    .section-card-subtitle {
      max-width: 720px;
      margin: 0;
      color: #637083;
      font-size: 0.94rem;
      line-height: 1.6;
    }
  `]
})
export class SectionCardComponent {
  readonly title = input('');
  readonly subtitle = input('');
  readonly eyebrow = input('');
}
