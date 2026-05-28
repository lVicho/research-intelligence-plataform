import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

import { AiSuggestionStatus } from '../../core/api/api-models';
import {
  ValidationAiReview,
  ValidationAiReviewCheck
} from '../../core/api/validation-ai-review-api.service';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import {
  aiSuggestionStatusLabel,
  aiSuggestionStatusTone
} from '../../shared/utils/display-labels';

type ReviewSeverity = 'HIGH' | 'MEDIUM' | 'LOW';

@Component({
  selector: 'rip-validation-ai-review-panel',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    LoadingStateComponent,
    StatusChipComponent
  ],
  template: `
    <div class="assistant-shell">
      <div class="assistant-toolbar">
        <div class="assistant-copy">
          <p class="detail-data-title">Apoyo a la revisión</p>
          <h4>La IA orienta, pero no decide</h4>
          <p>
            La sugerencia resume señales visibles del registro. No valida automáticamente y la responsabilidad final
            sigue siendo de la persona validadora.
          </p>
        </div>

        <button
          mat-stroked-button
          type="button"
          [disabled]="loading()"
          (click)="analyze.emit()"
        >
          Analizar con IA
        </button>
      </div>

      @if (loading()) {
        <rip-loading-state message="Revisando la actividad..." />
      } @else {
        @if (review(); as activeReview) {
          <div class="assistant-summary-grid">
            <section class="assistant-summary-card">
              <span class="assistant-label">Recomendación</span>
              <div class="assistant-summary-topline">
                <span class="recommendation-pill {{ recommendationClass(activeReview.recommendation) }}">
                  {{ recommendationLabel(activeReview.recommendation) }}
                </span>
                <span class="confidence-pill">{{ confidenceLabel(activeReview.confidenceScore) }}</span>
              </div>
              <p class="assistant-summary">{{ activeReview.summary }}</p>
            </section>

            <section class="assistant-summary-card">
              <span class="assistant-label">Trazabilidad</span>
              <div class="trace-list">
                <div class="trace-row">
                  <span>Proveedor</span>
                  <strong>{{ activeReview.provider }}</strong>
                </div>
                <div class="trace-row">
                  <span>Modelo</span>
                  <strong>{{ activeReview.model }}</strong>
                </div>
                <div class="trace-row">
                  <span>Analizada</span>
                  <strong>{{ dateLabel(activeReview.generatedAt) }}</strong>
                </div>
                <div class="trace-row trace-row-chip">
                  <span>Estado de la sugerencia</span>
                  <rip-status-chip
                    [label]="suggestionStatusLabel(activeReview.aiSuggestion.status)"
                    [tone]="suggestionStatusTone(activeReview.aiSuggestion.status)"
                  />
                </div>
              </div>
            </section>
          </div>

          <div class="assistant-note">
            <p>{{ activeReview.caution }}</p>
          </div>

          @if (feedbackMessage()) {
            <div class="assistant-feedback">{{ feedbackMessage() }}</div>
          }

          <section class="assistant-decision">
            <div class="assistant-decision-copy">
              <span class="assistant-label">Seguimiento de la sugerencia</span>
              <p>
                Puedes aceptar o rechazar la sugerencia de IA para dejar constancia interna. Esta decisión no cambia el
                estado de validación del registro.
              </p>
            </div>
            <div class="assistant-decision-actions">
              <button
                mat-stroked-button
                type="button"
                [disabled]="actionBusy() || activeReview.aiSuggestion.status === 'ACCEPTED'"
                (click)="acceptSuggestion.emit()"
              >
                Aceptar sugerencia
              </button>
              <button
                mat-stroked-button
                color="warn"
                type="button"
                [disabled]="actionBusy() || activeReview.aiSuggestion.status === 'REJECTED'"
                (click)="rejectSuggestion.emit()"
              >
                Rechazar sugerencia
              </button>
              <a mat-button routerLink="/admin/sugerencias-ia">Ver bandeja IA</a>
            </div>
          </section>

          <div class="assistant-body-grid">
            <section class="assistant-panel">
              <div class="assistant-section-header">
                <span class="assistant-label">Comprobaciones</span>
                <h4>Señales agrupadas por severidad</h4>
              </div>

              @for (severity of severityOrder; track severity) {
                @if (checksForSeverity(activeReview.checks, severity).length > 0) {
                  <div class="severity-block">
                    <div class="severity-header">
                      <span class="severity-pill {{ severityClass(severity) }}">{{ severityLabel(severity) }}</span>
                      <span>{{ checksForSeverity(activeReview.checks, severity).length }} elementos</span>
                    </div>

                    <div class="check-list">
                      @for (check of checksForSeverity(activeReview.checks, severity); track check.id) {
                        <article class="check-card {{ checkCardClass(check) }}">
                          <div class="check-topline">
                            <strong>{{ check.title }}</strong>
                            <span>{{ checkOutcomeLabel(check.outcome) }}</span>
                          </div>
                          <p>{{ check.detail }}</p>
                        </article>
                      }
                    </div>
                  </div>
                }
              }
            </section>

            <section class="assistant-panel">
              <div class="assistant-section-header">
                <span class="assistant-label">Comentario sugerido</span>
                <h4>Texto reutilizable para la revisión</h4>
              </div>

              @if (activeReview.suggestedComment) {
                <div class="comment-box">
                  <p>{{ activeReview.suggestedComment }}</p>
                </div>
              } @else {
                <p class="muted-copy">No se ha generado un comentario sugerido para esta actividad.</p>
              }

              <p class="helper-copy">
                Si decides solicitar cambios o rechazar la actividad, podrás usar este texto dentro del diálogo antes de
                confirmar la acción.
              </p>

              @if (activeReview.evidence.length > 0) {
                <div class="assistant-section-header evidence-header">
                  <span class="assistant-label">Evidencia</span>
                  <h4>Señales de apoyo visibles</h4>
                </div>

                <div class="evidence-list">
                  @for (evidence of activeReview.evidence; track evidence.label + evidence.excerpt) {
                    <article class="evidence-card">
                      <div class="evidence-topline">
                        <strong>{{ evidence.label }}</strong>
                        <span>{{ evidence.sourceLabel }}</span>
                      </div>
                      <p>{{ evidence.excerpt }}</p>
                      @if (evidence.helper) {
                        <span class="evidence-helper">{{ evidence.helper }}</span>
                      }
                      @if (evidence.path) {
                        <a mat-button [routerLink]="evidence.path">Abrir registro</a>
                      }
                    </article>
                  }
                </div>
              }
            </section>
          </div>
        } @else {
          <div class="assistant-empty">
            <p class="assistant-empty-title">Aún no hay revisión asistida para esta actividad.</p>
            <p>
              Ejecuta el análisis cuando quieras una orientación adicional sobre recomendación, señales de calidad y
              comentario sugerido.
            </p>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
    }

    .assistant-shell {
      display: grid;
      gap: 16px;
      min-width: 0;
    }

    .assistant-toolbar,
    .assistant-summary-grid,
    .assistant-decision,
    .severity-header,
    .check-topline,
    .evidence-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 14px;
    }

    .assistant-copy,
    .assistant-section-header,
    .assistant-decision-copy {
      display: grid;
      gap: 6px;
      min-width: 0;
    }

    .assistant-copy h4,
    .assistant-section-header h4 {
      margin: 0;
      color: #142033;
      font-size: 1rem;
      line-height: 1.2;
    }

    .assistant-copy p:not(.detail-data-title),
    .assistant-decision-copy p,
    .helper-copy,
    .muted-copy {
      margin: 0;
      color: #667487;
      line-height: 1.55;
    }

    .detail-data-title,
    .assistant-label {
      margin: 0;
      color: #4a6375;
      font-size: 0.78rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .assistant-summary-grid,
    .assistant-body-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }

    .assistant-summary-card,
    .assistant-panel,
    .assistant-decision,
    .assistant-note,
    .assistant-feedback,
    .assistant-empty {
      display: grid;
      gap: 12px;
      padding: 16px 18px;
      border: 1px solid #dce5ee;
      border-radius: 16px;
      background: linear-gradient(180deg, #ffffff, #f9fbfd);
    }

    .assistant-summary-card,
    .assistant-panel {
      min-width: 0;
    }

    .assistant-summary-topline {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
    }

    .recommendation-pill,
    .confidence-pill,
    .severity-pill {
      display: inline-flex;
      align-items: center;
      min-height: 30px;
      padding: 0 12px;
      border-radius: 999px;
      font-size: 0.84rem;
      font-weight: 760;
      line-height: 1.2;
      white-space: normal;
    }

    .recommendation-pill.recommendation-validate {
      background: #edf8f2;
      color: #1a6846;
      border: 1px solid #c9e7d6;
    }

    .recommendation-pill.recommendation-request-changes {
      background: #fff7e8;
      color: #8c5b07;
      border: 1px solid #efd6a4;
    }

    .recommendation-pill.recommendation-review-manually {
      background: #f4f7fb;
      color: #31455a;
      border: 1px solid #d6e1ea;
    }

    .confidence-pill {
      background: #f3f8fc;
      color: #284761;
      border: 1px solid #d4e2ee;
    }

    .assistant-summary {
      margin: 0;
      color: #243044;
      line-height: 1.6;
    }

    .trace-list,
    .check-list,
    .evidence-list {
      display: grid;
      gap: 12px;
    }

    .trace-row {
      display: grid;
      gap: 6px;
      padding-bottom: 10px;
      border-bottom: 1px solid #e4ebf2;
    }

    .trace-row:last-child {
      padding-bottom: 0;
      border-bottom: none;
    }

    .trace-row span,
    .severity-header span:last-child,
    .evidence-topline span,
    .evidence-helper {
      color: #667487;
      font-size: 0.84rem;
      line-height: 1.45;
    }

    .trace-row strong,
    .check-card strong,
    .evidence-card strong {
      color: #1d3043;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .trace-row-chip {
      gap: 10px;
    }

    .assistant-note {
      background: #f7fbff;
      border-color: #d8e6f2;
    }

    .assistant-note p,
    .assistant-feedback,
    .assistant-empty p {
      margin: 0;
      line-height: 1.6;
    }

    .assistant-feedback {
      color: #17634f;
      font-weight: 700;
    }

    .assistant-decision-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      justify-content: flex-end;
    }

    .severity-block {
      display: grid;
      gap: 12px;
    }

    .severity-pill.severity-high {
      background: #fff3f1;
      color: #a23526;
      border: 1px solid #f1c8c1;
    }

    .severity-pill.severity-medium {
      background: #fff8eb;
      color: #8d6113;
      border: 1px solid #ecd6a7;
    }

    .severity-pill.severity-low {
      background: #f2f6fa;
      color: #3d556c;
      border: 1px solid #d8e2eb;
    }

    .check-card,
    .comment-box,
    .evidence-card {
      display: grid;
      gap: 10px;
      padding: 14px 16px;
      border: 1px solid #dde6ee;
      border-radius: 14px;
      background: #ffffff;
    }

    .check-card.check-negative-high {
      border-color: #f0cbc3;
      background: #fffaf9;
    }

    .check-card.check-negative-medium {
      border-color: #eadbb7;
      background: #fffdf8;
    }

    .check-card.check-positive {
      border-color: #cfe2d7;
      background: #f8fcfa;
    }

    .check-card p,
    .comment-box p,
    .evidence-card p {
      margin: 0;
      color: #243044;
      line-height: 1.55;
      overflow-wrap: anywhere;
      white-space: pre-wrap;
    }

    .comment-box {
      background: #f7fbff;
      border-color: #d8e6f2;
    }

    .evidence-header {
      margin-top: 8px;
    }

    .evidence-card {
      background: linear-gradient(180deg, #ffffff, #f9fbfd);
    }

    .assistant-empty-title {
      color: #142033;
      font-weight: 760;
    }

    @media (max-width: 980px) {
      .assistant-summary-grid,
      .assistant-body-grid {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 760px) {
      .assistant-toolbar,
      .assistant-decision,
      .severity-header,
      .check-topline,
      .evidence-topline {
        display: grid;
      }

      .assistant-decision-actions {
        justify-content: flex-start;
      }
    }
  `]
})
export class ValidationAiReviewPanelComponent {
  readonly review = input<ValidationAiReview | null>(null);
  readonly loading = input(false);
  readonly actionBusy = input(false);
  readonly feedbackMessage = input('');
  readonly analyze = output<void>();
  readonly acceptSuggestion = output<void>();
  readonly rejectSuggestion = output<void>();

  readonly severityOrder: ReviewSeverity[] = ['HIGH', 'MEDIUM', 'LOW'];
  checksForSeverity(checks: ValidationAiReviewCheck[], severity: ReviewSeverity): ValidationAiReviewCheck[] {
    return checks.filter((check) => check.severity === severity);
  }

  recommendationLabel(value: string): string {
    switch (value) {
      case 'VALIDATE':
        return 'Parece validable';
      case 'REQUEST_CHANGES':
        return 'Conviene solicitar cambios';
      default:
        return 'Revisión manual recomendada';
    }
  }

  recommendationClass(value: string): string {
    switch (value) {
      case 'VALIDATE':
        return 'recommendation-validate';
      case 'REQUEST_CHANGES':
        return 'recommendation-request-changes';
      default:
        return 'recommendation-review-manually';
    }
  }

  confidenceLabel(score: number): string {
    return `Confianza ${Math.round(score * 100)}%`;
  }

  suggestionStatusLabel(status: AiSuggestionStatus): string {
    return aiSuggestionStatusLabel(status);
  }

  suggestionStatusTone(status: AiSuggestionStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return aiSuggestionStatusTone(status);
  }

  severityLabel(severity: ReviewSeverity): string {
    switch (severity) {
      case 'HIGH':
        return 'Alta';
      case 'MEDIUM':
        return 'Media';
      default:
        return 'Baja';
    }
  }

  severityClass(severity: ReviewSeverity): string {
    switch (severity) {
      case 'HIGH':
        return 'severity-high';
      case 'MEDIUM':
        return 'severity-medium';
      default:
        return 'severity-low';
    }
  }

  checkOutcomeLabel(outcome: string): string {
    switch (outcome) {
      case 'positive':
        return 'A favor';
      case 'negative':
        return 'A revisar';
      default:
        return 'Contexto';
    }
  }

  checkCardClass(check: ValidationAiReviewCheck): string {
    if (check.outcome === 'positive') {
      return 'check-positive';
    }
    if (check.outcome === 'negative' && check.severity === 'HIGH') {
      return 'check-negative-high';
    }
    if (check.outcome === 'negative' && check.severity === 'MEDIUM') {
      return 'check-negative-medium';
    }
    return '';
  }

  dateLabel(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }
}
