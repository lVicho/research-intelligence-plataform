import { Component, input, output } from '@angular/core';

@Component({
  selector: 'rip-demo-query-chips',
  standalone: true,
  template: `
    @if (queries().length > 0) {
      <section class="demo-query-block">
        <div class="demo-query-copy">
          <span class="label">{{ title() }}</span>
          @if (caption()) {
            <span class="caption">{{ caption() }}</span>
          }
        </div>

        <div class="chip-row">
          @for (query of queries(); track query) {
            <button
              type="button"
              class="query-chip"
              [disabled]="disabled()"
              (click)="querySelected.emit(query)"
            >
              {{ query }}
            </button>
          }
        </div>
      </section>
    }
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
    }

    .demo-query-block {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .demo-query-copy {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 12px;
      align-items: baseline;
      min-width: 0;
    }

    .label {
      color: #4f6174;
      font-size: 0.8rem;
      font-weight: 780;
      letter-spacing: 0.03em;
    }

    .caption {
      color: #6b7a8b;
      font-size: 0.8rem;
      line-height: 1.4;
    }

    .chip-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: flex-start;
      min-width: 0;
    }

    .query-chip {
      max-width: min(100%, 32rem);
      padding: 7px 12px;
      border: 1px solid #d8e2ea;
      border-radius: 999px;
      background: #ffffff;
      color: #32465a;
      cursor: pointer;
      font: inherit;
      font-size: 0.84rem;
      line-height: 1.25;
      text-align: left;
      transition: border-color 140ms ease, background-color 140ms ease, transform 140ms ease;
      overflow-wrap: anywhere;
    }

    .query-chip:hover {
      border-color: #9bb8c8;
      background: #f5fafc;
      transform: translateY(-1px);
    }

    .query-chip:disabled {
      cursor: default;
      opacity: 0.72;
      transform: none;
    }

    .query-chip:focus-visible {
      outline: 2px solid rgba(47, 111, 139, 0.3);
      outline-offset: 2px;
    }
  `]
})
export class DemoQueryChipsComponent {
  readonly title = input('Consultas sugeridas');
  readonly caption = input('');
  readonly queries = input<string[]>([]);
  readonly disabled = input(false);

  readonly querySelected = output<string>();
}
