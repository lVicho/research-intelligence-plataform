import { Component, computed, effect, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { AiSuggestion, AiSuggestionField, AiSuggestionFieldUpdate } from '../../core/api/api-models';
import { StatusChipComponent } from './status-chip.component';
import { TagChipComponent } from './tag-chip.component';
import {
  aiSuggestionStatusLabel,
  aiSuggestionStatusTone,
  aiSuggestionTypeLabel,
  validationEntityTypeLabel
} from '../utils/display-labels';

@Component({
  selector: 'rip-ai-suggestion-review-panel',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <article class="review-panel">
      <header class="panel-header">
        <div class="header-copy">
          <p class="section-kicker">Revision de sugerencia</p>
          <h2>{{ suggestion().title }}</h2>
          <p class="header-description">{{ suggestion().explanation }}</p>
          <div class="chip-list">
            <rip-tag-chip [label]="typeLabel(suggestion().type)" tone="type" />
            <rip-tag-chip [label]="entityTypeLabel(suggestion().target.entityType)" />
            <rip-status-chip [label]="statusLabel(suggestion().status)" [tone]="statusTone(suggestion().status)" />
          </div>
        </div>

        <div class="header-actions">
          @if (suggestion().target.path) {
            <a mat-stroked-button [routerLink]="suggestion().target.path">Abrir registro</a>
          }
        </div>
      </header>

      <section class="notice-block">
        <p>{{ noticeHeadline() }}</p>
        <span>{{ noticeDetail() || suggestion().visibilityNote }}</span>
      </section>

      @if (problemStatement()) {
        <section class="panel-section problem-section">
          <div class="section-header compact-header">
            <div>
              <p class="section-kicker">Problema detectado</p>
              <h3>Motivo de la sugerencia</h3>
            </div>
          </div>
          <p class="problem-copy">{{ problemStatement() }}</p>
        </section>
      }

      <section class="panel-section">
        <div class="section-header compact-header">
          <div>
            <p class="section-kicker">Contexto</p>
            <h3>Entidad objetivo</h3>
            <p>{{ suggestion().reviewerNote }}</p>
          </div>
        </div>

        <div class="metadata-grid">
          <div class="metadata-item">
            <span>Entidad</span>
            <strong>{{ suggestion().target.title }}</strong>
          </div>
          <div class="metadata-item">
            <span>Tipo de registro</span>
            <strong>{{ entityTypeLabel(suggestion().target.entityType) }} #{{ suggestion().target.entityId }}</strong>
          </div>
          <div class="metadata-item">
            <span>Estado</span>
            <strong>{{ statusLabel(suggestion().status) }}</strong>
          </div>
          <div class="metadata-item">
            <span>Propietario potencial</span>
            <strong>{{ suggestion().target.ownerResearcherName || 'Revision institucional' }}</strong>
          </div>
          @if (suggestion().target.subtitle) {
            <div class="metadata-item metadata-item-wide">
              <span>Detalle</span>
              <strong>{{ suggestion().target.subtitle }}</strong>
            </div>
          }
        </div>
      </section>

      <section class="panel-section">
        <div class="section-header compact-header">
          <div>
            <p class="section-kicker">{{ previewSectionKicker() }}</p>
            <h3>{{ previewSectionTitle() }}</h3>
            <p>{{ previewDescription() }}</p>
          </div>
        </div>

        <div class="field-list">
          @for (field of suggestion().proposedFields; track field.key) {
            <div class="field-item">
              <div class="field-labels">
                <strong>{{ field.label }}</strong>
                @if (field.helper) {
                  <span>{{ field.helper }}</span>
                }
              </div>

              <div class="field-preview-grid">
                <div class="value-box current-box">
                  <span class="value-label">Actual</span>
                  <p>{{ displayValue(field.currentValue) }}</p>
                </div>

                <div class="value-box proposed-box">
                  <span class="value-label">Propuesta</span>

                  @if (editMode() && field.editable) {
                    <mat-form-field appearance="outline" class="edit-field">
                      <mat-label>{{ field.label }}</mat-label>
                      @if (field.multiline) {
                        <textarea
                          matInput
                          [rows]="4"
                          [value]="draftValue(field)"
                          (input)="updateDraft(field, $event)"
                        ></textarea>
                      } @else {
                        <input
                          matInput
                          [value]="draftValue(field)"
                          (input)="updateDraft(field, $event)"
                        >
                      }
                    </mat-form-field>
                  } @else {
                    <p>{{ displayValue(effectiveProposedValue(field)) }}</p>
                  }
                </div>
              </div>
            </div>
          }
        </div>
      </section>

      <section class="panel-section">
        <mat-expansion-panel class="section-expansion" [expanded]="!collapseEvidence()">
          <mat-expansion-panel-header>
            <mat-panel-title>{{ evidencePanelTitle() }}</mat-panel-title>
            <mat-panel-description>{{ suggestion().evidence.length }} referencias</mat-panel-description>
          </mat-expansion-panel-header>

          <div class="section-header compact-header expansion-copy">
            <div>
              <p class="section-kicker">Evidencia</p>
              <h3>Senales de apoyo</h3>
              <p>Estas pistas ayudan a revisar la propuesta. No sustituyen una decision editorial o de catalogacion.</p>
            </div>
          </div>

          <div class="evidence-grid">
            @for (evidence of suggestion().evidence; track evidence.id) {
              <article class="evidence-card">
                <div class="evidence-topline">
                  <div class="chip-list">
                    <rip-tag-chip [label]="evidence.label" tone="status" />
                    <rip-tag-chip [label]="evidence.sourceType" />
                  </div>
                  <strong>{{ evidence.sourceLabel }}</strong>
                </div>

                <p class="evidence-excerpt">{{ evidence.excerpt }}</p>

                @if (evidence.helper) {
                  <p class="evidence-helper">{{ evidence.helper }}</p>
                }

                @if (evidence.path) {
                  <div class="actions evidence-actions">
                    <a mat-button [routerLink]="evidence.path">Ver evidencia relacionada</a>
                  </div>
                }
              </article>
            }
          </div>
        </mat-expansion-panel>
      </section>

      <section class="panel-section">
        <div class="section-header compact-header">
          <div>
            <p class="section-kicker">Modelo y proveedor</p>
            <h3>Trazabilidad de generacion</h3>
            <p>Se muestra quien produjo la sugerencia y con que cautelas debe revisarse.</p>
          </div>
        </div>

        <div class="metadata-grid">
          <div class="metadata-item">
            <span>Proveedor</span>
            <strong>{{ suggestion().providerInfo.provider }}</strong>
          </div>
          <div class="metadata-item">
            <span>Modelo</span>
            <strong>{{ suggestion().providerInfo.model }}</strong>
          </div>
          <div class="metadata-item">
            <span>Generada</span>
            <strong>{{ dateLabel(suggestion().providerInfo.generatedAt) }}</strong>
          </div>
          <div class="metadata-item">
            <span>Perfil de prompt</span>
            <strong>{{ suggestion().providerInfo.promptProfile }}</strong>
          </div>
          @if (confidenceLabel()) {
            <div class="metadata-item">
              <span>Confianza estimada</span>
              <strong>{{ confidenceLabel() }}</strong>
            </div>
          }
          <div class="metadata-item metadata-item-wide">
            <span>Lectura orientativa</span>
            <strong>{{ suggestion().providerInfo.confidenceSummary }}</strong>
          </div>
        </div>

        <div class="caution-note">
          <p>{{ suggestion().providerInfo.caution }}</p>
        </div>
      </section>

      <section class="panel-section">
        <mat-expansion-panel>
          <mat-expansion-panel-header>
            <mat-panel-title>Ver datos tecnicos</mat-panel-title>
            <mat-panel-description>Solo para revision interna</mat-panel-description>
          </mat-expansion-panel-header>

          <pre>{{ technicalDataJson() }}</pre>
        </mat-expansion-panel>
      </section>

      @if (showDecisionActions()) {
        <section class="panel-section action-section">
          <div class="section-header compact-header">
            <div>
              <p class="section-kicker">Decision</p>
              <h3>Resolver sugerencia</h3>
              <p>La sugerencia sigue siendo privada hasta que alguien la revise y aplique el cambio en el flujo correspondiente.</p>
            </div>
          </div>

          @if (feedbackMessage()) {
            <div class="feedback-banner">{{ feedbackMessage() }}</div>
          }

          <div class="actions action-bar">
            <button mat-stroked-button type="button" [disabled]="actionBusy()" (click)="accept.emit()">Aceptar</button>
            <button mat-flat-button color="primary" type="button" [disabled]="actionBusy()" (click)="handleEditAndAccept()">
              {{ editMode() ? 'Aplicar edicion y aceptar' : 'Editar y aceptar' }}
            </button>
            <button mat-stroked-button color="warn" type="button" [disabled]="actionBusy()" (click)="reject.emit()">Rechazar</button>
            @if (editMode()) {
              <button mat-button type="button" [disabled]="actionBusy()" (click)="cancelEdit()">Cancelar edicion</button>
            }
          </div>
        </section>
      }
    </article>
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
    }

    .review-panel {
      display: grid;
      gap: 18px;
      min-width: 0;
      padding: 24px;
      border: 1px solid #dce5ee;
      border-radius: 24px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(248, 251, 253, 0.96)),
        linear-gradient(90deg, rgba(20, 52, 73, 0.05), rgba(47, 111, 139, 0.03));
      box-shadow: 0 18px 38px rgba(20, 32, 51, 0.06);
    }

    .panel-header,
    .evidence-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 18px;
    }

    .header-copy {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .header-copy h2 {
      margin: 0;
      color: #142033;
      font-size: clamp(1.3rem, 1.8vw, 1.75rem);
      line-height: 1.15;
      overflow-wrap: anywhere;
    }

    .header-description {
      margin: 0;
      color: #5f7182;
      line-height: 1.6;
      max-width: 920px;
    }

    .header-actions {
      flex: 0 0 auto;
    }

    .notice-block,
    .feedback-banner,
    .caution-note {
      display: grid;
      gap: 8px;
      padding: 16px 18px;
      border: 1px solid #dbe7ef;
      border-radius: 18px;
      background: #f7fbff;
    }

    .notice-block p,
    .notice-block span,
    .feedback-banner,
    .caution-note p {
      margin: 0;
      line-height: 1.6;
    }

    .notice-block p {
      color: #173247;
      font-weight: 700;
    }

    .notice-block span,
    .caution-note p {
      color: #5e7183;
    }

    .feedback-banner {
      color: #17634f;
      font-weight: 700;
    }

    .problem-section {
      background: linear-gradient(180deg, #fffdf7, #fffaf0);
      border-color: #ead7a6;
    }

    .problem-copy {
      margin: 0;
      color: #4e3c1f;
      line-height: 1.6;
    }

    .panel-section {
      display: grid;
      gap: 14px;
      padding: 18px 20px;
      border: 1px solid #dce5ee;
      border-radius: 20px;
      background: #ffffff;
    }

    .compact-header {
      margin-bottom: 0;
    }

    .compact-header h3 {
      margin: 0;
      color: #142033;
      font-size: 1.08rem;
      line-height: 1.2;
    }

    .compact-header p:not(.section-kicker) {
      margin: 6px 0 0;
      color: #667487;
      line-height: 1.55;
      max-width: 860px;
    }

    .metadata-item-wide {
      grid-column: span 2;
    }

    .field-list,
    .evidence-grid {
      display: grid;
      gap: 14px;
    }

    .field-item,
    .evidence-card {
      display: grid;
      gap: 14px;
      padding: 18px;
      border: 1px solid #dce5ee;
      border-radius: 18px;
      background: linear-gradient(180deg, #ffffff, #f9fbfd);
    }

    .field-labels {
      display: grid;
      gap: 6px;
    }

    .field-labels strong,
    .evidence-topline strong {
      color: #163247;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .field-labels span,
    .evidence-helper {
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.5;
    }

    .field-preview-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 14px;
    }

    .value-box {
      display: grid;
      gap: 10px;
      min-width: 0;
      padding: 14px 16px;
      border: 1px solid #dce5ee;
      border-radius: 16px;
      background: #ffffff;
    }

    .current-box {
      background: #fbfcfd;
    }

    .proposed-box {
      background: #f7fbff;
      border-color: #cfe0ed;
    }

    .value-label {
      color: #4a6375;
      font-size: 0.78rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .value-box p,
    .evidence-excerpt {
      margin: 0;
      color: #243044;
      line-height: 1.6;
      overflow-wrap: anywhere;
      white-space: pre-wrap;
    }

    .edit-field {
      width: 100%;
    }

    .section-expansion {
      box-shadow: none;
      background: transparent;
    }

    .expansion-copy {
      margin-bottom: 14px;
    }

    .evidence-grid {
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    }

    .evidence-card {
      align-content: start;
    }

    .evidence-topline {
      display: grid;
      gap: 10px;
    }

    .evidence-actions {
      justify-content: flex-start;
    }

    pre {
      margin: 0;
      padding: 16px;
      border-radius: 14px;
      background: #0f1d2b;
      color: #edf4fb;
      font-size: 0.82rem;
      line-height: 1.55;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-word;
    }

    .action-section {
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(247, 250, 253, 0.98)),
        linear-gradient(90deg, rgba(20, 52, 73, 0.04), rgba(47, 111, 139, 0.02));
    }

    .action-bar {
      justify-content: flex-start;
    }

    .action-bar button,
    .action-bar a {
      min-height: 42px;
    }

    @media (max-width: 900px) {
      .panel-header {
        display: grid;
      }

      .metadata-item-wide {
        grid-column: auto;
      }

      .field-preview-grid {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 640px) {
      .review-panel,
      .panel-section,
      .field-item,
      .evidence-card {
        padding: 16px;
      }
    }
  `]
})
export class AiSuggestionReviewPanelComponent {
  readonly suggestion = input.required<AiSuggestion>();
  readonly problemStatement = input('');
  readonly actionBusy = input(false);
  readonly feedbackMessage = input('');
  readonly showDecisionActions = input(true);
  readonly noticeHeadline = input('La IA propone cambios, pero una persona debe revisarlos antes de aplicarlos.');
  readonly noticeDetail = input('La sugerencia sigue siendo privada hasta que alguien la revise y la aplique.');
  readonly previewSectionKicker = input('Vista previa');
  readonly previewSectionTitle = input('Datos propuestos');
  readonly previewDescription = input('Compara el valor actual con la propuesta antes de decidir. La edicion es opcional y sigue siendo interna hasta aceptar.');
  readonly evidencePanelTitle = input('Evidencia utilizada');
  readonly collapseEvidence = input(false);
  readonly entityTypeLabelOverride = input('');
  readonly accept = output<void>();
  readonly acceptWithEdits = output<AiSuggestionFieldUpdate[]>();
  readonly reject = output<void>();

  readonly editMode = signal(false);
  readonly draftValues = signal<Record<string, string>>({});
  readonly technicalDataJson = computed(() => JSON.stringify(this.suggestion().technicalData, null, 2));
  readonly confidenceLabel = computed(() => {
    const technicalConfidence = this.suggestion().technicalData['confidenceScore'];
    if (typeof technicalConfidence === 'number' && Number.isFinite(technicalConfidence)) {
      return `${Math.round(technicalConfidence * 100)}%`;
    }
    return '';
  });

  constructor() {
    effect(() => {
      const nextValues: Record<string, string> = {};
      for (const field of this.suggestion().proposedFields) {
        nextValues[field.key] = field.proposedValue ?? '';
      }
      this.draftValues.set(nextValues);
      this.editMode.set(false);
    }, { allowSignalWrites: true });
  }

  handleEditAndAccept(): void {
    if (!this.editMode()) {
      this.editMode.set(true);
      return;
    }

    this.acceptWithEdits.emit(this.collectUpdates());
  }

  cancelEdit(): void {
    const nextValues: Record<string, string> = {};
    for (const field of this.suggestion().proposedFields) {
      nextValues[field.key] = field.proposedValue ?? '';
    }
    this.draftValues.set(nextValues);
    this.editMode.set(false);
  }

  updateDraft(field: AiSuggestionField, event: Event): void {
    const value = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.draftValues.update((drafts) => ({
      ...drafts,
      [field.key]: value
    }));
  }

  draftValue(field: AiSuggestionField): string {
    return this.draftValues()[field.key] ?? field.proposedValue ?? '';
  }

  effectiveProposedValue(field: AiSuggestionField): string | null {
    return this.editMode() ? this.draftValue(field) : field.proposedValue;
  }

  displayValue(value: string | null): string {
    return value && value.trim() ? value : 'Sin dato';
  }

  dateLabel(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  typeLabel(value: string): string {
    return aiSuggestionTypeLabel(value);
  }

  statusLabel(value: string): string {
    return aiSuggestionStatusLabel(value);
  }

  statusTone(value: string): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return aiSuggestionStatusTone(value);
  }

  entityTypeLabel(value: string): string {
    return this.entityTypeLabelOverride() || validationEntityTypeLabel(value);
  }

  private collectUpdates(): AiSuggestionFieldUpdate[] {
    return this.suggestion().proposedFields.map((field) => ({
      key: field.key,
      proposedValue: this.normalizeValue(this.draftValue(field))
    }));
  }

  private normalizeValue(value: string): string | null {
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
  }
}
