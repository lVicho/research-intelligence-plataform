import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';

import { AiSuggestionFieldUpdate, PublicSummaryStyle, PublicSummaryTargetType } from '../../core/api/api-models';
import {
  PublicSummaryApiService,
  PublicSummarySuggestionState
} from '../../core/api/public-summary-api.service';
import { AiSuggestionReviewPanelComponent } from './ai-suggestion-review-panel.component';
import { EmptyStateComponent } from './empty-state.component';

interface SummaryStyleOption {
  value: PublicSummaryStyle;
  label: string;
}

const STYLE_OPTIONS: SummaryStyleOption[] = [
  { value: 'SHORT', label: 'Corto' },
  { value: 'STANDARD', label: 'Estándar' },
  { value: 'EXTENDED', label: 'Extendido' }
];

@Component({
  selector: 'rip-public-summary-workflow',
  standalone: true,
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    AiSuggestionReviewPanelComponent,
    EmptyStateComponent
  ],
  template: `
    <section class="public-summary-workflow">
      <mat-card appearance="outlined" class="summary-shell">
        <mat-card-content>
          <div class="summary-header">
            <div class="summary-copy">
              <p class="section-kicker">Resumen público</p>
              <h3>Generación y revisión asistida</h3>
              <p>
                Genera un borrador revisable para este registro. La sugerencia sigue siendo interna hasta que alguien la
                acepte de forma explícita.
              </p>
            </div>

            <div class="summary-actions">
              <mat-button-toggle-group
                [value]="selectedStyle()"
                (change)="selectStyle($event.value)"
                aria-label="Estilo de resumen público"
              >
                @for (option of styleOptions; track option.value) {
                  <mat-button-toggle [value]="option.value">{{ option.label }}</mat-button-toggle>
                }
              </mat-button-toggle-group>

              <button
                mat-flat-button
                color="primary"
                type="button"
                [disabled]="generateDisabled()"
                (click)="generate()"
              >
                Generar resumen público
              </button>
            </div>
          </div>

          @if (displayedCurrentSummary()) {
            <div class="current-summary">
              <span>{{ currentSummaryHeading() }}</span>
              <p>{{ displayedCurrentSummary() }}</p>
            </div>
          } @else {
            <div class="current-summary empty-current-summary">
              <span>{{ currentSummaryHeading() }}</span>
              <p>Todavía no hay un texto público aceptado para este registro.</p>
            </div>
          }

          @if (statusMessage()) {
            <div class="status-banner" [class.error-banner]="statusTone() === 'error'">
              {{ statusMessage() }}
            </div>
          }
        </mat-card-content>
      </mat-card>

      @if (activeSuggestion(); as suggestionState) {
        <rip-ai-suggestion-review-panel
          [suggestion]="suggestionState.suggestion"
          [problemStatement]="warningMessage(suggestionState)"
          [actionBusy]="actionBusy()"
          [feedbackMessage]="reviewFeedback()"
          [showDecisionActions]="showDecisionActions(suggestionState)"
          [noticeHeadline]="'Este texto debe revisarse antes de publicarse.'"
          [noticeDetail]="'Solo la versión aceptada se aplica al campo público correspondiente.'"
          [previewSectionKicker]="'Vista previa'"
          [previewSectionTitle]="'Resumen sugerido'"
          [previewDescription]="'Compara la versión actual con la propuesta. Puedes editarla antes de aceptarla.'"
          [evidencePanelTitle]="'Ver evidencias utilizadas'"
          [collapseEvidence]="true"
          [entityTypeLabelOverride]="entityLabel()"
          (accept)="acceptSuggestion(suggestionState)"
          (acceptWithEdits)="acceptSuggestionWithEdits(suggestionState, $event)"
          (reject)="rejectSuggestion(suggestionState)"
        />
      } @else {
        <mat-card appearance="outlined">
          <mat-card-content>
            <rip-empty-state
              title="Sin sugerencias activas"
              message="Puedes generar un borrador nuevo cuando necesites revisar o actualizar el resumen público."
            />
          </mat-card-content>
        </mat-card>
      }
    </section>
  `,
  styles: [`
    .public-summary-workflow {
      display: grid;
      gap: 18px;
    }

    .summary-shell {
      border-radius: 14px !important;
    }

    .summary-shell mat-card-content {
      display: grid;
      gap: 18px;
    }

    .summary-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 18px;
    }

    .summary-copy {
      display: grid;
      gap: 8px;
      min-width: 0;
    }

    .summary-copy h3 {
      margin: 0;
      color: #132133;
      font-size: 1.14rem;
      line-height: 1.25;
    }

    .summary-copy p:not(.section-kicker) {
      margin: 0;
      color: #607182;
      line-height: 1.6;
      max-width: 72ch;
    }

    .summary-actions {
      display: grid;
      gap: 12px;
      justify-items: end;
      flex: 0 0 auto;
    }

    .current-summary,
    .status-banner {
      display: grid;
      gap: 8px;
      padding: 16px 18px;
      border: 1px solid #dde7ef;
      border-radius: 14px;
      background: #fbfdfe;
    }

    .current-summary span {
      color: #5f7082;
      font-size: 0.76rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .current-summary p,
    .status-banner {
      margin: 0;
      color: #243044;
      line-height: 1.6;
      white-space: pre-wrap;
    }

    .empty-current-summary p {
      color: #68798b;
    }

    .status-banner {
      background: #f7fbff;
      color: #1f5b75;
      font-weight: 700;
    }

    .error-banner {
      border-color: #efc3c3;
      background: #fff5f5;
      color: #a12b2b;
    }

    @media (max-width: 860px) {
      .summary-header {
        display: grid;
      }

      .summary-actions {
        justify-items: start;
      }
    }
  `]
})
export class PublicSummaryWorkflowComponent {
  private readonly api = inject(PublicSummaryApiService);

  readonly targetType = input.required<PublicSummaryTargetType>();
  readonly targetId = input<number | null>(null);
  readonly targetTitle = input.required<string>();
  readonly targetSubtitle = input<string | null>(null);
  readonly currentSummary = input<string | null>(null);
  readonly ownerResearcherName = input<string | null>(null);
  readonly entityLabel = input('Registro');
  readonly currentSummaryLabel = input('Resumen público actual');
  readonly allowGeneration = input(false);
  readonly allowReviewExisting = input(false);
  readonly summaryApplied = output<string>();

  readonly selectedStyle = signal<PublicSummaryStyle>('STANDARD');
  readonly loadingExisting = signal(false);
  readonly actionBusy = signal(false);
  readonly statusMessage = signal('');
  readonly statusTone = signal<'success' | 'error'>('success');
  readonly reviewFeedback = signal('');
  readonly activeSuggestion = signal<PublicSummarySuggestionState | null>(null);
  readonly loadedTargetKey = signal('');

  readonly styleOptions = STYLE_OPTIONS;
  readonly displayedCurrentSummary = computed(() => {
    const current = this.currentSummary()?.trim();
    if (current) {
      return current;
    }

    const suggestionState = this.activeSuggestion();
    if (!suggestionState) {
      return '';
    }

    return suggestionState.rawStatus === 'ACCEPTED' || suggestionState.rawStatus === 'EDITED'
      ? suggestionState.summaryText
      : '';
  });
  readonly generateDisabled = computed(() => !this.allowGeneration() || this.targetId() === null || this.actionBusy());

  constructor() {
    effect(() => {
      const targetId = this.targetId();
      const targetType = this.targetType();
      const allowReview = this.allowReviewExisting();
      const targetKey = targetId === null ? '' : `${targetType}:${targetId}`;

      if (!allowReview || !targetKey || this.loadedTargetKey() === targetKey) {
        return;
      }

      this.loadedTargetKey.set(targetKey);
      if (targetId !== null) {
        this.loadLatestSuggestion();
      }
    }, { allowSignalWrites: true });
  }

  currentSummaryHeading(): string {
    const current = this.currentSummary()?.trim();
    if (current) {
      return this.currentSummaryLabel();
    }

    const suggestionState = this.activeSuggestion();
    return suggestionState && (suggestionState.rawStatus === 'ACCEPTED' || suggestionState.rawStatus === 'EDITED')
      ? 'Última versión aceptada'
      : this.currentSummaryLabel();
  }

  selectStyle(value: PublicSummaryStyle): void {
    if (!value) {
      return;
    }
    this.selectedStyle.set(value);
  }

  generate(): void {
    const targetId = this.targetId();
    if (!this.allowGeneration() || targetId === null || this.actionBusy()) {
      return;
    }

    this.actionBusy.set(true);
    this.statusMessage.set('');
    this.reviewFeedback.set('');

    this.api.generate({
      targetType: this.targetType(),
      targetId,
      style: this.selectedStyle(),
      audience: 'PUBLIC'
    }, this.context()).subscribe({
      next: (suggestionState) => {
        this.activeSuggestion.set(suggestionState);
        this.selectedStyle.set(suggestionState.style);
        this.actionBusy.set(false);
        this.statusTone.set('success');
        this.statusMessage.set('Borrador generado. Revisa el texto antes de decidir si se acepta o se descarta.');
      },
      error: () => {
        this.actionBusy.set(false);
        this.statusTone.set('error');
        this.statusMessage.set('No se pudo generar el resumen público sugerido.');
      }
    });
  }

  acceptSuggestion(suggestionState: PublicSummarySuggestionState): void {
    this.runReviewAction(
      () => this.api.accept(suggestionState),
      'Resumen sugerido aceptado. La versión aceptada queda aplicada como texto público.'
    );
  }

  acceptSuggestionWithEdits(suggestionState: PublicSummarySuggestionState, updates: AiSuggestionFieldUpdate[]): void {
    const summary = updates.find((item) => item.key === 'public_summary')?.proposedValue?.trim() ?? '';
    if (!summary) {
      this.reviewFeedback.set('No se pudo aplicar la edición porque el resumen quedó vacío.');
      return;
    }

    this.runReviewAction(
      () => this.api.editAndAccept(suggestionState, summary),
      'Resumen sugerido editado y aceptado. La versión revisada queda aplicada como texto público.'
    );
  }

  rejectSuggestion(suggestionState: PublicSummarySuggestionState): void {
    this.runReviewAction(
      () => this.api.reject(suggestionState),
      'Sugerencia rechazada. No se ha aplicado ningún cambio al texto público.'
    );
  }

  warningMessage(suggestionState: PublicSummarySuggestionState): string {
    return suggestionState.warnings[0] ?? '';
  }

  showDecisionActions(suggestionState: PublicSummarySuggestionState): boolean {
    return suggestionState.rawStatus === 'GENERATED';
  }

  private loadLatestSuggestion(): void {
    const targetId = this.targetId();
    if (targetId === null) {
      return;
    }

    this.loadingExisting.set(true);
    this.api.latestForTarget(this.targetType(), targetId, this.context()).subscribe({
      next: (suggestionState) => {
        this.activeSuggestion.set(suggestionState);
        if (suggestionState) {
          this.selectedStyle.set(suggestionState.style);
        }
        this.loadingExisting.set(false);
      },
      error: () => {
        this.loadingExisting.set(false);
      }
    });
  }

  private runReviewAction(
    action: () => ReturnType<PublicSummaryApiService['accept']>,
    successMessage: string
  ): void {
    this.actionBusy.set(true);
    this.reviewFeedback.set('');
    this.statusMessage.set('');

    action().subscribe({
      next: (updatedSuggestion) => {
        this.activeSuggestion.set(updatedSuggestion);
        this.selectedStyle.set(updatedSuggestion.style);
        if (updatedSuggestion.rawStatus === 'ACCEPTED' || updatedSuggestion.rawStatus === 'EDITED') {
          this.summaryApplied.emit(updatedSuggestion.summaryText);
        }
        this.actionBusy.set(false);
        this.reviewFeedback.set(successMessage);
      },
      error: () => {
        this.actionBusy.set(false);
        this.reviewFeedback.set('No se pudo registrar la decisión sobre este resumen sugerido.');
      }
    });
  }

  private context() {
    return {
      currentSummary: this.currentSummary(),
      targetTitle: this.targetTitle(),
      targetSubtitle: this.targetSubtitle(),
      ownerResearcherName: this.ownerResearcherName(),
      entityLabel: this.entityLabel()
    };
  }
}
