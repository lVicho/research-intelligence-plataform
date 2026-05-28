import { Component, input } from '@angular/core';

@Component({
  selector: 'rip-page-header',
  standalone: true,
  template: `
    <header class="page-header-shell" [class.compact]="compact()">
      <div class="header-copy">
        <p class="eyebrow">{{ eyebrow() }}</p>
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p class="subtitle">{{ subtitle() }}</p>
        }
      </div>
      <div class="header-actions">
        <ng-content />
      </div>
    </header>
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
    }

    .page-header-shell {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: end;
      gap: 20px;
      padding: 10px 0 0;
      min-width: 0;
    }

    .page-header-shell.compact {
      align-items: start;
      gap: 16px;
      padding-top: 2px;
    }

    .header-copy {
      min-width: 0;
    }

    h1 {
      margin: 0;
      color: #142033;
      font-size: clamp(2rem, 3vw, 2.8rem);
      font-weight: 800;
      line-height: 1.02;
      letter-spacing: 0;
      overflow-wrap: anywhere;
    }

    .compact h1 {
      font-size: clamp(1.6rem, 2vw, 2.15rem);
      line-height: 1.08;
      letter-spacing: 0;
    }

    .eyebrow {
      margin: 0 0 8px;
      color: #2f6f8f;
      font-size: 0.76rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .subtitle {
      max-width: 820px;
      margin: 12px 0 0;
      color: #5e6b7c;
      font-size: 1rem;
      line-height: 1.65;
      overflow-wrap: anywhere;
    }

    .compact .subtitle {
      max-width: 1040px;
      margin-top: 10px;
      font-size: 0.96rem;
      line-height: 1.55;
    }

    .header-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 10px;
      flex-wrap: wrap;
      padding-bottom: 4px;
      min-width: 0;
    }

    .header-actions:empty {
      display: none;
    }

    @media (max-width: 760px) {
      .page-header-shell {
        grid-template-columns: 1fr;
        align-items: start;
      }

      .header-actions {
        justify-content: flex-start;
      }
    }
  `]
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly eyebrow = input('Research Intelligence');
  readonly compact = input(false);
}
