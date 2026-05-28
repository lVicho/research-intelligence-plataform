import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin } from 'rxjs';

import {
  AiSuggestion,
  AiSuggestionFieldUpdate,
  DataQualityEntityType,
  DataQualityIssue,
  DataQualityOverview,
  DataQualitySeverity,
  ValidationEntityType
} from '../../core/api/api-models';
import { AiSuggestionsApiService } from '../../core/api/ai-suggestions-api.service';
import { DataQualityApiService } from '../../core/api/data-quality-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { AiSuggestionReviewPanelComponent } from '../../shared/components/ai-suggestion-review-panel.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { MetricCardComponent } from '../../shared/components/metric-card.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { QualityAssistantIssueCode, supportsAiSuggestionForQualityIssue } from '../../shared/utils/quality-assistant';

type SeverityFilter = DataQualitySeverity | 'all';
type EntityTypeFilter = DataQualityEntityType | 'all';

@Component({
  selector: 'rip-data-quality-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    MetricCardComponent,
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent,
    AiSuggestionReviewPanelComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Calidad de datos"
        subtitle="Prioriza incidencias operativas en publicaciones, investigadores, eventos y canales antes de validar o exponer información institucional."
        eyebrow="Gestión institucional"
      >
        <a mat-button routerLink="/admin/panel">Panel institucional</a>
        <a mat-button routerLink="/admin/validacion">Bandeja de validación</a>
      </rip-page-header>

      @if (loading() && !overview()) {
        <rip-loading-state message="Cargando incidencias de calidad de datos..." />
      } @else if (errorMessage()) {
        <mat-card appearance="outlined">
          <mat-card-content class="state-card">
            <rip-error-state [message]="errorMessage()" />
            <div class="actions">
              <button mat-flat-button color="primary" type="button" (click)="reload()">Reintentar</button>
            </div>
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="metric-grid">
          @for (card of summaryCards(); track card.label) {
            <rip-metric-card [label]="card.label" [value]="card.value" [hint]="card.hint" />
          }
        </div>

        <mat-card appearance="outlined">
          <mat-card-content>
            <div class="section-header compact-header">
              <div>
                <p class="section-kicker">Filtros</p>
                <h2>Afina la revisión</h2>
                <p>Combina severidad, tipo de registro y texto libre para centrar la revisión administrativa.</p>
              </div>
              <button mat-button type="button" (click)="clearFilters()">Limpiar filtros</button>
            </div>

            <form class="form-grid" [formGroup]="filterForm">
              <mat-form-field appearance="outline">
                <mat-label>Severidad</mat-label>
                <mat-select formControlName="severity">
                  <mat-option value="all">Todas</mat-option>
                  @for (severity of severities; track severity) {
                    <mat-option [value]="severity">{{ severityLabel(severity) }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Tipo de registro</mat-label>
                <mat-select formControlName="entityType">
                  <mat-option value="all">Todos</mat-option>
                  @for (entityType of entityTypes; track entityType) {
                    <mat-option [value]="entityType">{{ entityTypeLabel(entityType) }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline" class="search-field">
                <mat-label>Buscar incidencia o registro</mat-label>
                <input matInput formControlName="query" placeholder="Ej. ORCID, DOI, evento o publicación">
              </mat-form-field>
            </form>
          </mat-card-content>
        </mat-card>

        <mat-card appearance="outlined">
          <mat-card-content class="issues-card">
            <div class="section-header compact-header">
              <div>
                <p class="section-kicker">Incidencias</p>
                <h2>{{ filteredIssues().length }} categorías visibles</h2>
                <p>Las incidencias con mayor severidad se muestran primero para facilitar la priorización.</p>
              </div>
              @if (loading()) {
                <span class="muted">Actualizando...</span>
              }
            </div>

            @if (filteredIssues().length === 0) {
              <rip-empty-state
                title="Sin incidencias para estos filtros"
                message="Prueba otra combinación de severidad, tipo o texto para encontrar registros afectados."
              />
            } @else {
              <div class="issue-list">
                @for (issue of filteredIssues(); track issue.id) {
                  <article class="issue-item">
                    <div class="issue-topline">
                      <div class="issue-heading">
                        <div class="chip-list">
                          <rip-status-chip [label]="severityLabel(issue.severity)" [tone]="severityTone(issue.severity)" />
                          <rip-tag-chip [label]="entityTypeLabel(issue.entityType)" tone="type" />
                        </div>
                        <h3>{{ issue.label }}</h3>
                        <p>{{ issue.description }}</p>
                      </div>

                      <div class="issue-count">
                        <strong>{{ issue.count }}</strong>
                        <span>registros</span>
                      </div>
                    </div>

                    <div class="issue-meta">
                      <span>Actualizado {{ formatDate(issue.updatedAt) }}</span>
                      <span>{{ issue.count === 1 ? '1 registro afectado' : issue.count + ' registros afectados' }}</span>
                    </div>

                    <div class="affected-block">
                      <p class="links-label">Registros afectados</p>
                      <div class="affected-links">
                        @for (record of issue.affectedRecords; track record.path + record.label) {
                          <article class="affected-link-card">
                            <a class="affected-link" [routerLink]="record.path" [queryParams]="navigationContext.returnQueryParams('Volver a calidad de datos')">
                              <strong>{{ record.label }}</strong>
                              <span>{{ record.helper || 'Abrir detalle del registro' }}</span>
                            </a>

                            @if (supportsAiSuggestion(issue, record)) {
                              <div class="actions record-actions">
                                <button
                                  mat-stroked-button
                                  type="button"
                                  [disabled]="suggestionLoading(suggestionRecordKey(issue, record))"
                                  (click)="requestAiSuggestion(issue, record)"
                                >
                                  {{ recordSuggestion(issue, record) ? 'Regenerar sugerencia IA' : 'Sugerir mejoras con IA' }}
                                </button>
                              </div>
                            }

                            @if (suggestionLoading(suggestionRecordKey(issue, record))) {
                              <p class="assistant-loading">Preparando sugerencia con IA para este registro...</p>
                            }

                            @if (recordSuggestion(issue, record); as suggestion) {
                              <rip-ai-suggestion-review-panel
                                [suggestion]="suggestion"
                                [problemStatement]="issue.label + ': ' + issue.description"
                                [actionBusy]="suggestionActionBusy(suggestionRecordKey(issue, record))"
                                [feedbackMessage]="suggestionFeedback(suggestionRecordKey(issue, record))"
                                (accept)="acceptSuggestion(suggestion.id, suggestionRecordKey(issue, record))"
                                (acceptWithEdits)="acceptSuggestionWithEdits(suggestion.id, suggestionRecordKey(issue, record), $event)"
                                (reject)="rejectSuggestion(suggestion.id, suggestionRecordKey(issue, record))"
                              />
                            }
                          </article>
                        }
                      </div>
                    </div>
                  </article>
                }
              </div>
            }
          </mat-card-content>
        </mat-card>
      }
    </section>
  `,
  styles: [`
    .state-card,
    .issues-card {
      display: grid;
      gap: 20px;
    }

    .compact-header {
      margin-bottom: 0;
    }

    .compact-header p {
      max-width: 760px;
    }

    .search-field {
      grid-column: span 2;
    }

    .issue-list {
      display: grid;
      gap: 16px;
    }

    .issue-item {
      display: grid;
      gap: 16px;
      padding: 20px;
      border: 1px solid #dce5ee;
      border-radius: 20px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 251, 253, 0.96)),
        linear-gradient(90deg, rgba(31, 111, 139, 0.04), rgba(45, 140, 120, 0.03));
    }

    .issue-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 20px;
    }

    .issue-heading {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .issue-heading h3 {
      margin: 0;
      color: #142033;
      font-size: 1.12rem;
      line-height: 1.2;
    }

    .issue-heading p {
      margin: 0;
      color: #617182;
      line-height: 1.6;
    }

    .issue-count {
      display: grid;
      min-width: 110px;
      padding: 14px 16px;
      border: 1px solid #d7e5ee;
      border-radius: 18px;
      background: #ffffff;
      text-align: right;
    }

    .issue-count strong {
      color: #102033;
      font-size: 1.9rem;
      line-height: 1;
    }

    .issue-count span {
      color: #667487;
      font-size: 0.82rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .issue-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 18px;
      color: #667487;
      font-size: 0.9rem;
    }

    .affected-block {
      display: grid;
      gap: 12px;
    }

    .links-label {
      margin: 0;
      color: #29495c;
      font-size: 0.84rem;
      font-weight: 780;
      letter-spacing: 0.03em;
      text-transform: uppercase;
    }

    .affected-links {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 12px;
    }

    .affected-link-card {
      display: grid;
      gap: 10px;
    }

    .affected-link {
      display: grid;
      gap: 6px;
      min-width: 0;
      padding: 14px 16px;
      border: 1px solid #dbe7ef;
      border-radius: 16px;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .affected-link:hover {
      border-color: #9fc0d1;
      box-shadow: 0 12px 24px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
    }

    .affected-link strong {
      color: #163247;
      line-height: 1.35;
    }

    .affected-link span {
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.45;
    }

    .record-actions {
      justify-content: flex-start;
    }

    .assistant-loading {
      margin: 0;
      color: #5f7182;
      font-size: 0.9rem;
      line-height: 1.5;
    }

    @media (max-width: 960px) {
      .search-field {
        grid-column: auto;
      }
    }

    @media (max-width: 720px) {
      .issue-topline {
        display: grid;
      }

      .issue-count {
        text-align: left;
      }
    }
  `]
})
export class DataQualityPageComponent implements OnInit {
  private readonly api = inject(DataQualityApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly aiSuggestionsApi = inject(AiSuggestionsApiService);
  readonly navigationContext = inject(NavigationContextService);

  readonly severities: DataQualitySeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
  readonly entityTypes: DataQualityEntityType[] = [
    'PUBLICATION',
    'RESEARCHER',
    'TOPIC',
    'EVENT_PARTICIPATION',
    'RESEARCH_UNIT',
    'VENUE',
    'EVENT',
    'EXTERNAL_AUTHOR'
  ];
  readonly overview = signal<DataQualityOverview | null>(null);
  readonly suggestions = signal<AiSuggestion[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly suggestionLoadingMap = signal<Record<string, boolean>>({});
  readonly suggestionActionBusyMap = signal<Record<string, boolean>>({});
  readonly suggestionFeedbackMap = signal<Record<string, string>>({});

  readonly filterForm = new FormGroup({
    severity: new FormControl<SeverityFilter>('all', { nonNullable: true }),
    entityType: new FormControl<EntityTypeFilter>('all', { nonNullable: true }),
    query: new FormControl('', { nonNullable: true })
  });

  readonly filteredIssues = computed(() => {
    const overview = this.overview();
    if (!overview) {
      return [] as DataQualityIssue[];
    }

    const filters = this.filterForm.getRawValue();
    const query = filters.query.trim().toLocaleLowerCase('es-ES');

    return overview.issues
      .filter((issue) => filters.severity === 'all' || issue.severity === filters.severity)
      .filter((issue) => filters.entityType === 'all' || issue.entityType === filters.entityType)
      .filter((issue) => {
        if (!query) {
          return true;
        }

        return [
          issue.label,
          issue.description,
          this.entityTypeLabel(issue.entityType),
          ...issue.affectedRecords.map((record) => `${record.label} ${record.helper || ''}`)
        ].some((value) => value.toLocaleLowerCase('es-ES').includes(query));
      })
      .sort((left, right) => this.severityRank(left.severity) - this.severityRank(right.severity) || right.count - left.count);
  });

  readonly summaryCards = computed(() => {
    const summary = this.overview()?.summary;
    if (!summary) {
      return [];
    }

    return [
      { label: 'Incidencias abiertas', value: summary.totalOpenIssues, hint: 'categorías activas en esta revisión' },
      { label: 'Críticas', value: summary.criticalIssues, hint: 'prioridad operativa inmediata' },
      { label: 'Registros afectados', value: summary.affectedRecords, hint: 'elementos que requieren revisión' },
      { label: 'Última revisión', value: this.formatDate(summary.lastReviewAt), hint: 'fecha del snapshot actual' }
    ];
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    forkJoin({
      overview: this.api.overview(),
      suggestions: this.aiSuggestionsApi.list()
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ overview, suggestions }) => {
          this.overview.set(overview);
          this.suggestions.set(suggestions);
          this.loading.set(false);
        },
        error: () => {
          this.overview.set(null);
          this.suggestions.set([]);
          this.errorMessage.set('No se pudieron cargar las incidencias de calidad de datos.');
          this.loading.set(false);
        }
      });
  }

  clearFilters(): void {
    this.filterForm.reset({
      severity: 'all',
      entityType: 'all',
      query: ''
    });
  }

  supportsAiSuggestion(issue: DataQualityIssue, record: DataQualityIssue['affectedRecords'][number]): boolean {
    return this.qualityTarget(issue, record) !== null
      && supportsAiSuggestionForQualityIssue(issue.id as QualityAssistantIssueCode);
  }

  requestAiSuggestion(issue: DataQualityIssue, record: DataQualityIssue['affectedRecords'][number]): void {
    const target = this.qualityTarget(issue, record);
    if (!target || !supportsAiSuggestionForQualityIssue(issue.id as QualityAssistantIssueCode)) {
      return;
    }
    const issueCode = issue.id as Exclude<QualityAssistantIssueCode, 'changes-requested'>;

    const recordKey = this.suggestionRecordKey(issue, record);
    this.setSuggestionLoading(recordKey, true);
    this.setSuggestionFeedback(recordKey, '');

    this.aiSuggestionsApi.suggestDataQualityImprovement({
      issueId: issueCode,
      entityType: target.entityType,
      entityId: target.entityId,
      title: record.helper || record.label,
      subtitle: issue.label,
      currentValue: this.currentValueForIssue(issue.id),
      path: record.path,
      context: issue.description
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (suggestion) => {
          this.suggestions.update((items) => this.upsertSuggestion(items, suggestion));
          this.setSuggestionLoading(recordKey, false);
        },
        error: () => {
          this.setSuggestionLoading(recordKey, false);
          this.setSuggestionFeedback(recordKey, 'No se pudo generar la sugerencia con IA para este registro.');
        }
      });
  }

  recordSuggestion(issue: DataQualityIssue, record: DataQualityIssue['affectedRecords'][number]): AiSuggestion | null {
    const target = this.qualityTarget(issue, record);
    if (!target || !supportsAiSuggestionForQualityIssue(issue.id as QualityAssistantIssueCode)) {
      return null;
    }

    const expectedFieldKey = this.expectedFieldKeyForIssue(issue.id as QualityAssistantIssueCode);
    return this.suggestions().find((suggestion) =>
      suggestion.target.entityType === target.entityType
      && suggestion.target.entityId === target.entityId
      && suggestion.proposedFields.some((field) => field.key === expectedFieldKey)
    ) ?? null;
  }

  suggestionLoading(recordKey: string): boolean {
    return this.suggestionLoadingMap()[recordKey] ?? false;
  }

  suggestionActionBusy(recordKey: string): boolean {
    return this.suggestionActionBusyMap()[recordKey] ?? false;
  }

  suggestionFeedback(recordKey: string): string {
    return this.suggestionFeedbackMap()[recordKey] ?? '';
  }

  acceptSuggestion(suggestionId: string, recordKey: string): void {
    this.runSuggestionAction(recordKey, () => this.aiSuggestionsApi.accept(suggestionId), 'Sugerencia aceptada. El registro no cambia hasta que alguien la aplique manualmente.');
  }

  acceptSuggestionWithEdits(suggestionId: string, recordKey: string, updates: AiSuggestionFieldUpdate[]): void {
    this.runSuggestionAction(
      recordKey,
      () => this.aiSuggestionsApi.acceptWithEdits(suggestionId, updates),
      'Sugerencia aceptada con edición. La versión revisada queda lista para aplicarse manualmente.'
    );
  }

  rejectSuggestion(suggestionId: string, recordKey: string): void {
    this.runSuggestionAction(recordKey, () => this.aiSuggestionsApi.reject(suggestionId), 'Sugerencia rechazada. No se aplicará ningún cambio automáticamente.');
  }

  severityLabel(severity: DataQualitySeverity): string {
    switch (severity) {
      case 'CRITICAL':
        return 'Crítica';
      case 'HIGH':
        return 'Alta';
      case 'MEDIUM':
        return 'Media';
      default:
        return 'Baja';
    }
  }

  severityTone(severity: DataQualitySeverity): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    switch (severity) {
      case 'CRITICAL':
        return 'danger';
      case 'HIGH':
        return 'warning';
      case 'MEDIUM':
        return 'info';
      default:
        return 'neutral';
    }
  }

  entityTypeLabel(entityType: DataQualityEntityType): string {
    switch (entityType) {
      case 'PUBLICATION':
        return 'Publicación';
      case 'RESEARCHER':
        return 'Investigador';
      case 'TOPIC':
        return 'Tema';
      case 'EVENT_PARTICIPATION':
        return 'Participación';
      case 'RESEARCH_UNIT':
        return 'Unidad';
      case 'VENUE':
        return 'Canal';
      case 'EVENT':
        return 'Evento';
      default:
        return 'Autor externo';
    }
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  suggestionRecordKey(issue: DataQualityIssue, record: DataQualityIssue['affectedRecords'][number]): string {
    return `${issue.id}::${record.path}::${record.label}`;
  }

  private severityRank(severity: DataQualitySeverity): number {
    switch (severity) {
      case 'CRITICAL':
        return 0;
      case 'HIGH':
        return 1;
      case 'MEDIUM':
        return 2;
      default:
        return 3;
    }
  }

  private qualityTarget(
    issue: DataQualityIssue,
    record: DataQualityIssue['affectedRecords'][number]
  ): { entityType: ValidationEntityType; entityId: number } | null {
    const entityType = this.validationEntityType(issue.entityType);
    const entityId = this.entityIdFromPath(record.path);
    if (!entityType || entityId === null) {
      return null;
    }
    return { entityType, entityId };
  }

  private validationEntityType(entityType: DataQualityEntityType): ValidationEntityType | null {
    switch (entityType) {
      case 'PUBLICATION':
        return 'PUBLICATION';
      case 'RESEARCHER':
        return 'RESEARCHER';
      case 'RESEARCH_UNIT':
        return 'RESEARCH_UNIT';
      case 'EVENT_PARTICIPATION':
        return 'EVENT_PARTICIPATION';
      default:
        return null;
    }
  }

  private entityIdFromPath(path: string): number | null {
    const match = path.match(/\/(\d+)(?:\?.*)?$/);
    if (!match) {
      return null;
    }
    const entityId = Number(match[1]);
    return Number.isInteger(entityId) && entityId > 0 ? entityId : null;
  }

  private currentValueForIssue(issueId: string): string | null {
    switch (issueId) {
      case 'missing-doi':
        return 'Sin DOI';
      case 'missing-abstract':
        return 'Sin resumen';
      case 'missing-topics':
        return 'Sin temas';
      case 'missing-public-summary':
        return 'Sin resumen público';
      default:
        return null;
    }
  }

  private expectedFieldKeyForIssue(issueId: QualityAssistantIssueCode): string {
    switch (issueId) {
      case 'missing-doi':
        return 'doi';
      case 'missing-topics':
        return 'topic_labels';
      case 'missing-public-summary':
        return 'public_summary';
      default:
        return 'abstract_text';
    }
  }

  private runSuggestionAction(
    recordKey: string,
    operation: () => ReturnType<AiSuggestionsApiService['accept']>,
    successMessage: string
  ): void {
    this.setSuggestionActionBusy(recordKey, true);
    this.setSuggestionFeedback(recordKey, '');
    operation()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (suggestion) => {
          this.suggestions.update((items) => this.upsertSuggestion(items, suggestion));
          this.setSuggestionActionBusy(recordKey, false);
          this.setSuggestionFeedback(recordKey, successMessage);
        },
        error: () => {
          this.setSuggestionActionBusy(recordKey, false);
          this.setSuggestionFeedback(recordKey, 'No se pudo registrar la decisión sobre esta sugerencia.');
        }
      });
  }

  private setSuggestionLoading(recordKey: string, loading: boolean): void {
    this.suggestionLoadingMap.update((state) => ({ ...state, [recordKey]: loading }));
  }

  private setSuggestionActionBusy(recordKey: string, busy: boolean): void {
    this.suggestionActionBusyMap.update((state) => ({ ...state, [recordKey]: busy }));
  }

  private setSuggestionFeedback(recordKey: string, message: string): void {
    this.suggestionFeedbackMap.update((state) => ({ ...state, [recordKey]: message }));
  }

  private upsertSuggestion(currentSuggestions: AiSuggestion[], nextSuggestion: AiSuggestion): AiSuggestion[] {
    const index = currentSuggestions.findIndex((suggestion) => suggestion.id === nextSuggestion.id);
    if (index < 0) {
      return [nextSuggestion, ...currentSuggestions];
    }

    const updated = [...currentSuggestions];
    updated[index] = nextSuggestion;
    return updated;
  }
}
