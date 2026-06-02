import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle
} from '@angular/material/dialog';

import {
  PortalPublicationExplanation,
  PortalPublicationExplanationStyle
} from '../../core/api/api-models';
import { PortalApiService } from '../../core/api/portal-api.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';

interface PortalPublicationExplanationDialogData {
  publicationId: number;
  title: string;
  initialStyle: PortalPublicationExplanationStyle;
}

@Component({
  selector: 'rip-portal-publication-explanation-dialog',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    TagChipComponent
  ],
  template: `
    <section class="portal-explanation-dialog">
      <header class="dialog-header">
        <p class="section-kicker">Asistente contextual</p>
        <h2 mat-dialog-title>Explicar esta publicación</h2>
        <p>{{ data.title }}</p>
      </header>

      <mat-dialog-content class="dialog-content">
        <div class="mode-switch" role="radiogroup" aria-label="Modo de explicación">
          @for (option of styleOptions; track option.value) {
            <button
              type="button"
              class="mode-option"
              role="radio"
              [class.active]="style() === option.value"
              [attr.aria-checked]="style() === option.value"
              (click)="selectStyle(option.value)"
            >
              {{ option.label }}
            </button>
          }
        </div>

        @if (loading()) {
          <rip-loading-state [message]="loadingMessage()" />
        } @else if (errorMessage()) {
          <rip-error-state [message]="errorMessage()" />
          <button mat-stroked-button type="button" (click)="retry()">Reintentar</button>
        } @else {
          @if (explanation(); as content) {
            <section class="explanation-sections" aria-label="Explicación de la publicación">
            <article>
              <span>Resumen</span>
              <p>{{ content.plainSummary }}</p>
            </article>
            <article>
              <span>Qué problema aborda</span>
              <p>{{ content.problemAddressed }}</p>
            </article>
            <article>
              <span>Por qué es relevante</span>
              <p>{{ content.whyItMatters }}</p>
            </article>
            <article>
              <span>Enfoque</span>
              <p>{{ content.approach }}</p>
            </article>
          </section>

            @if (hasReferences(content)) {
              <section class="reference-section" aria-label="Referencias relacionadas">
              <h3>Contexto visible en el portal</h3>
              <div class="reference-grid">
                <article>
                  <span>Temas</span>
                  <div class="chip-list">
                    @for (topic of content.relatedTopics; track topic.id) {
                      <rip-tag-chip [label]="topic.label" />
                    } @empty {
                      <p>No hay temas públicos suficientes.</p>
                    }
                  </div>
                </article>

                <article>
                  <span>Investigadores</span>
                  <div class="reference-list">
                    @for (researcher of content.relatedResearchers; track researcher.id) {
                      <a [routerLink]="['/portal/investigadores', researcher.id]" [queryParams]="referenceQueryParams()">
                        {{ researcher.label }}
                      </a>
                    } @empty {
                      <p>No hay perfiles públicos enlazables.</p>
                    }
                  </div>
                </article>

                <article>
                  <span>Unidades</span>
                  <div class="reference-list">
                    @for (unit of content.relatedUnits; track unit.id) {
                      <a [routerLink]="['/portal/unidades', unit.id]" [queryParams]="referenceQueryParams()">
                        {{ unit.label }}
                      </a>
                    } @empty {
                      <p>No hay unidades públicas enlazables.</p>
                    }
                  </div>
                </article>

                <article>
                  <span>Publicaciones relacionadas</span>
                  <div class="reference-list">
                    @for (publication of content.relatedPublications; track publication.id) {
                      <a [routerLink]="['/portal/publicaciones', publication.id]" [queryParams]="referenceQueryParams()">
                        {{ publication.label }}
                      </a>
                    } @empty {
                      <p>No hay publicaciones relacionadas visibles.</p>
                    }
                  </div>
                </article>
              </div>
              </section>
            }

            @if (displayWarnings(content).length > 0) {
              <section class="context-note" aria-label="Notas de contexto">
                @for (warning of displayWarnings(content); track warning) {
                  <p>{{ warning }}</p>
                }
              </section>
            }
          } @else {
            <rip-empty-state
              title="Sin explicación"
              message="No se ha podido preparar una explicación con la información pública disponible."
            />
          }
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cerrar</button>
      </mat-dialog-actions>
    </section>
  `,
  styles: [`
    :host {
      --portal-text: #102530;
      --portal-muted: #587078;
      --portal-border: #c9dadd;
      --portal-accent: #0f6479;
      --portal-accent-soft: #dff0f1;
      --portal-surface: #ffffff;
      --portal-surface-muted: #e8f2f3;
      display: block;
      color: var(--portal-text);
    }

    .portal-explanation-dialog {
      display: grid;
      gap: 18px;
      min-width: min(760px, 86vw);
    }

    .dialog-header {
      display: grid;
      gap: 8px;
      padding: 24px 24px 0;
    }

    .section-kicker,
    .explanation-sections span,
    .reference-grid span {
      margin: 0;
      color: var(--portal-accent);
      font-size: 0.78rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    h2[mat-dialog-title] {
      margin: 0;
      padding: 0;
      color: var(--portal-text);
      font-size: clamp(1.45rem, 2vw, 2rem);
      line-height: 1.15;
    }

    .dialog-header p:not(.section-kicker) {
      margin: 0;
      color: var(--portal-muted);
      line-height: 1.55;
    }

    .dialog-content {
      display: grid;
      gap: 18px;
      padding: 0 24px 8px;
    }

    .mode-switch {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 6px;
      padding: 5px;
      border: 1px solid var(--portal-border);
      border-radius: 8px;
      background: color-mix(in srgb, var(--portal-surface-muted) 58%, #ffffff);
    }

    .mode-option {
      min-height: 40px;
      border: 0;
      border-radius: 6px;
      background: transparent;
      color: var(--portal-muted);
      cursor: pointer;
      font: inherit;
      font-weight: 740;
      text-align: center;
    }

    .mode-option.active {
      background: var(--portal-surface);
      color: var(--portal-accent);
      box-shadow: 0 8px 18px rgba(16, 37, 48, 0.1);
    }

    .explanation-sections {
      display: grid;
      gap: 14px;
    }

    .explanation-sections article,
    .reference-grid article {
      display: grid;
      gap: 8px;
      padding: 16px;
      border: 1px solid var(--portal-border);
      border-radius: 8px;
      background: var(--portal-surface);
    }

    .explanation-sections p,
    .reference-grid p,
    .context-note p {
      margin: 0;
      color: var(--portal-muted);
      line-height: 1.65;
    }

    .reference-section {
      display: grid;
      gap: 12px;
    }

    .reference-section h3 {
      margin: 0;
      color: var(--portal-text);
      font-size: 1rem;
      line-height: 1.3;
    }

    .reference-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
    }

    .chip-list,
    .reference-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      min-width: 0;
    }

    .reference-list {
      display: grid;
    }

    .reference-list a {
      color: var(--portal-link, #07586b);
      font-weight: 720;
      line-height: 1.45;
      text-decoration: none;
    }

    .reference-list a:hover,
    .reference-list a:focus-visible {
      text-decoration: underline;
    }

    .context-note {
      display: grid;
      gap: 8px;
      padding: 14px 16px;
      border: 1px solid color-mix(in srgb, var(--portal-accent) 24%, var(--portal-border));
      border-radius: 8px;
      background: var(--portal-accent-soft);
    }

    mat-dialog-actions {
      padding: 0 24px 20px;
    }

    @media (max-width: 720px) {
      .portal-explanation-dialog {
        min-width: 0;
      }

      .dialog-header,
      .dialog-content,
      mat-dialog-actions {
        padding-inline: 18px;
      }

      .reference-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class PortalPublicationExplanationDialogComponent implements OnInit {
  private readonly portalApi = inject(PortalApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly data = inject<PortalPublicationExplanationDialogData>(MAT_DIALOG_DATA);

  readonly styleOptions: { value: PortalPublicationExplanationStyle; label: string }[] = [
    { value: 'PLAIN', label: 'Divulgativo' },
    { value: 'TECHNICAL', label: 'Técnico' }
  ];

  readonly style = signal<PortalPublicationExplanationStyle>(this.data.initialStyle);
  readonly explanation = signal<PortalPublicationExplanation | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal('');

  private readonly cache = new Map<PortalPublicationExplanationStyle, PortalPublicationExplanation>();
  private requestNonce = 0;

  ngOnInit(): void {
    this.load(this.style());
  }

  selectStyle(style: PortalPublicationExplanationStyle): void {
    if (style === this.style()) {
      return;
    }
    this.style.set(style);
    this.load(style);
  }

  retry(): void {
    this.cache.delete(this.style());
    this.load(this.style());
  }

  loadingMessage(): string {
    return this.style() === 'TECHNICAL'
      ? 'Preparando una explicación técnica...'
      : 'Preparando una explicación divulgativa...';
  }

  referenceQueryParams(): Record<string, string> {
    return {
      returnTo: `/portal/publicaciones/${this.data.publicationId}`,
      returnLabel: 'Volver a la publicación'
    };
  }

  hasReferences(content: PortalPublicationExplanation): boolean {
    return content.relatedTopics.length > 0
      || content.relatedResearchers.length > 0
      || content.relatedUnits.length > 0
      || content.relatedPublications.length > 0;
  }

  displayWarnings(content: PortalPublicationExplanation): string[] {
    const warnings = content.warnings.map((warning) => this.publicWarning(warning));
    return Array.from(new Set(warnings.filter((warning) => warning.length > 0))).slice(0, 3);
  }

  private load(style: PortalPublicationExplanationStyle): void {
    const cached = this.cache.get(style);
    if (cached) {
      this.explanation.set(cached);
      this.errorMessage.set('');
      this.loading.set(false);
      return;
    }

    const requestId = ++this.requestNonce;
    this.loading.set(true);
    this.errorMessage.set('');
    this.explanation.set(null);

    this.portalApi.explainPublication(this.data.publicationId, { style, language: 'español' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          if (requestId !== this.requestNonce) {
            return;
          }
          this.cache.set(style, response);
          this.explanation.set(response);
          this.loading.set(false);
        },
        error: () => {
          if (requestId !== this.requestNonce) {
            return;
          }
          this.loading.set(false);
          this.errorMessage.set('No se ha podido generar la explicación. Inténtalo de nuevo en unos minutos.');
        }
      });
  }

  private publicWarning(warning: string): string {
    const normalized = warning.toLowerCase();
    if (
      normalized.includes('ollama')
      || normalized.includes('provider')
      || normalized.includes('proveedor')
      || normalized.includes('modelo')
      || normalized.includes('embedding')
    ) {
      return 'La explicación se ha preparado con una versión de apoyo basada en la evidencia visible.';
    }
    return warning.trim();
  }
}
