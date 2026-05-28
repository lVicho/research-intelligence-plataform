import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AiSuggestionsApiService } from '../../core/api/ai-suggestions-api.service';
import {
  AiSuggestion,
  AiSuggestionFieldUpdate,
  AiSuggestionStatus,
  AiSuggestionType,
  ValidationEntityType
} from '../../core/api/api-models';
import { AiSuggestionReviewPanelComponent } from '../../shared/components/ai-suggestion-review-panel.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import {
  aiSuggestionStatusLabel,
  aiSuggestionStatusTone,
  aiSuggestionTypeLabel,
  validationEntityTypeLabel
} from '../../shared/utils/display-labels';

type SuggestionStatusFilter = AiSuggestionStatus | 'all';
type SuggestionTypeFilter = AiSuggestionType | 'all';
type SuggestionEntityFilter = ValidationEntityType | 'all';

interface SuggestionFilters {
  query: string;
  status: SuggestionStatusFilter;
  type: SuggestionTypeFilter;
  entityType: SuggestionEntityFilter;
}

@Component({
  selector: 'rip-ai-suggestions-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    AiSuggestionReviewPanelComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="page ai-suggestions-page">
      <rip-page-header
        title="Sugerencias IA"
        subtitle="Revisa propuestas internas generadas a partir de señales bibliográficas, perfiles y contexto institucional antes de decidir si se aplican."
        eyebrow="Supervisión humana"
      />

      <div class="surface-intro intro-band">
        <div class="intro-copy">
          <p class="section-kicker">Uso interno</p>
          <h2>La IA propone cambios, pero una persona debe revisarlos antes de aplicarlos.</h2>
          <p>Estas sugerencias permanecen en un circuito privado hasta que alguien las acepte y complete el flujo editorial o de catálogo correspondiente.</p>
        </div>

        <div class="summary-strip">
          <span class="summary-chip">
            <strong>{{ suggestions().length }}</strong>
            <span>sugerencias cargadas</span>
          </span>
          <span class="summary-chip">
            <strong>{{ pendingCount() }}</strong>
            <span>pendientes de revisión</span>
          </span>
          <span class="summary-chip">
            <strong>{{ filteredSuggestions().length }}</strong>
            <span>visibles con filtros actuales</span>
          </span>
        </div>
      </div>

      <mat-card appearance="outlined">
        <mat-card-content>
          <div class="section-header compact-header">
            <div>
              <p class="section-kicker">Filtros</p>
              <h2>Acotar la revisión</h2>
              <p>Filtra por tipo de sugerencia, estado y entidad objetivo para centrar la revisión humana.</p>
            </div>
            <button mat-button type="button" (click)="clearFilters()">Limpiar filtros</button>
          </div>

          <form class="form-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline" class="search-field">
              <mat-label>Buscar sugerencia o entidad</mat-label>
              <input matInput formControlName="query" placeholder="Ej. investigador, publicación, tema o unidad">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Estado</mat-label>
              <mat-select formControlName="status">
                <mat-option value="all">Todos</mat-option>
                @for (status of statusOptions; track status) {
                  <mat-option [value]="status">{{ statusLabel(status) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Tipo</mat-label>
              <mat-select formControlName="type">
                <mat-option value="all">Todos</mat-option>
                @for (type of typeOptions; track type) {
                  <mat-option [value]="type">{{ typeLabel(type) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Entidad</mat-label>
              <mat-select formControlName="entityType">
                <mat-option value="all">Todas</mat-option>
                @for (entityType of entityTypeOptions(); track entityType) {
                  <mat-option [value]="entityType">{{ entityTypeLabel(entityType) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <div class="actions filter-actions">
              <button mat-flat-button color="primary" type="submit">Aplicar filtros</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      @if (loading()) {
        <rip-loading-state message="Cargando sugerencias internas de IA..." />
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
        <div class="review-layout">
          <mat-card appearance="outlined" class="list-card">
            <mat-card-content>
              <div class="section-header compact-header">
                <div>
                  <p class="section-kicker">Bandeja</p>
                  <h2>{{ filteredSuggestions().length }} sugerencias visibles</h2>
                  <p>Selecciona una propuesta para revisar su vista previa, evidencia, proveedor y decisión disponible.</p>
                </div>
              </div>

              @if (filteredSuggestions().length === 0) {
                <rip-empty-state
                  title="No hay sugerencias para estos filtros"
                  message="Prueba otra combinación de estado, tipo o entidad para continuar la revisión."
                />
              } @else {
                <div class="suggestion-list">
                  @for (suggestion of filteredSuggestions(); track suggestion.id) {
                    <button
                      type="button"
                      class="suggestion-item"
                      [class.active]="isSelected(suggestion.id)"
                      (click)="selectSuggestion(suggestion.id)"
                    >
                      <div class="suggestion-item-topline">
                        <div class="chip-list">
                          <rip-tag-chip [label]="typeLabel(suggestion.type)" tone="type" />
                          <rip-status-chip [label]="statusLabel(suggestion.status)" [tone]="statusTone(suggestion.status)" />
                        </div>
                        <span class="item-date">{{ dateLabel(suggestion.updatedAt) }}</span>
                      </div>

                      <div class="suggestion-item-copy">
                        <strong>{{ suggestion.title }}</strong>
                        <span>{{ suggestion.target.title }}</span>
                        <span>{{ entityTypeLabel(suggestion.target.entityType) }} #{{ suggestion.target.entityId }}</span>
                      </div>

                      <p class="suggestion-item-note">{{ suggestion.providerInfo.provider }} · {{ suggestion.providerInfo.model }}</p>
                    </button>
                  }
                </div>
              }
            </mat-card-content>
          </mat-card>

          <section class="detail-column">
            @if (activeSuggestion(); as suggestion) {
              <rip-ai-suggestion-review-panel
                [suggestion]="suggestion"
                [actionBusy]="actionBusy()"
                [feedbackMessage]="feedbackMessage()"
                (accept)="acceptSuggestion()"
                (acceptWithEdits)="acceptSuggestionWithEdits($event)"
                (reject)="rejectSuggestion()"
              />
            } @else {
              <mat-card appearance="outlined">
                <mat-card-content>
                  <rip-empty-state
                    title="Selecciona una sugerencia"
                    message="Aquí aparecerán la vista previa propuesta, la evidencia de apoyo y las acciones de revisión."
                  />
                </mat-card-content>
              </mat-card>
            }
          </section>
        </div>
      }
    </section>
  `,
  styles: [`
    .ai-suggestions-page {
      gap: 20px;
    }

    .intro-band {
      display: grid;
      grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
      align-items: center;
      gap: 22px;
    }

    .intro-copy {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .intro-copy h2 {
      margin: 0;
      color: #142033;
      font-size: clamp(1.25rem, 1.8vw, 1.75rem);
      line-height: 1.15;
    }

    .intro-copy p:not(.section-kicker) {
      margin: 0;
      color: #5f7182;
      line-height: 1.65;
      max-width: 920px;
    }

    .compact-header {
      margin-bottom: 0;
    }

    .compact-header h2 {
      margin: 0;
      color: #142033;
      line-height: 1.2;
    }

    .compact-header p:not(.section-kicker) {
      max-width: 860px;
    }

    .search-field {
      grid-column: span 2;
    }

    .filter-actions {
      align-self: center;
    }

    .review-layout {
      display: grid;
      grid-template-columns: minmax(360px, 0.8fr) minmax(0, 1.3fr);
      gap: 24px;
      align-items: start;
    }

    .list-card mat-card-content {
      display: grid;
      gap: 18px;
    }

    .suggestion-list {
      display: grid;
      gap: 14px;
    }

    .suggestion-item {
      display: grid;
      gap: 12px;
      width: 100%;
      padding: 18px;
      border: 1px solid #dce5ee;
      border-radius: 18px;
      background: #ffffff;
      color: inherit;
      text-align: left;
      cursor: pointer;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .suggestion-item:hover,
    .suggestion-item.active {
      border-color: #a9c7d6;
      box-shadow: 0 16px 30px rgba(20, 32, 51, 0.08);
      transform: translateY(-1px);
    }

    .suggestion-item.active {
      background: #f7fbff;
    }

    .suggestion-item-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
    }

    .item-date,
    .suggestion-item-note {
      color: #667487;
      font-size: 0.84rem;
      line-height: 1.45;
    }

    .suggestion-item-copy {
      display: grid;
      gap: 6px;
      min-width: 0;
    }

    .suggestion-item-copy strong {
      color: #142033;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .suggestion-item-copy span {
      color: #5f7182;
      font-size: 0.9rem;
      line-height: 1.45;
      overflow-wrap: anywhere;
    }

    .suggestion-item-note {
      margin: 0;
    }

    .detail-column {
      min-width: 0;
    }

    @media (max-width: 1180px) {
      .intro-band,
      .review-layout {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 820px) {
      .search-field {
        grid-column: auto;
      }
    }

    @media (max-width: 640px) {
      .suggestion-item {
        padding: 16px;
      }

      .suggestion-item-topline {
        display: grid;
      }
    }
  `]
})
export class AiSuggestionsPageComponent implements OnInit {
  private readonly api = inject(AiSuggestionsApiService);

  readonly suggestions = signal<AiSuggestion[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly actionBusy = signal(false);
  readonly feedbackMessage = signal('');
  readonly selectedSuggestionId = signal<string | null>(null);
  readonly statusOptions: AiSuggestionStatus[] = ['PENDING_REVIEW', 'ACCEPTED', 'ACCEPTED_WITH_EDITS', 'REJECTED'];
  readonly typeOptions: AiSuggestionType[] = [
    'RESEARCHER_SUMMARY',
    'RESEARCHER_TOPIC',
    'PUBLICATION_METADATA',
    'PUBLICATION_TOPIC',
    'RESEARCH_UNIT_SUMMARY',
    'VALIDATION_REVIEW'
  ];
  readonly appliedFilters = signal<SuggestionFilters>({
    query: '',
    status: 'PENDING_REVIEW',
    type: 'all',
    entityType: 'all'
  });

  readonly filterForm = new FormGroup({
    query: new FormControl('', { nonNullable: true }),
    status: new FormControl<SuggestionStatusFilter>('PENDING_REVIEW', { nonNullable: true }),
    type: new FormControl<SuggestionTypeFilter>('all', { nonNullable: true }),
    entityType: new FormControl<SuggestionEntityFilter>('all', { nonNullable: true })
  });

  readonly filteredSuggestions = computed(() => {
    const filters = this.appliedFilters();
    const query = filters.query.trim().toLocaleLowerCase('es-ES');

    return this.suggestions()
      .filter((suggestion) => filters.status === 'all' || suggestion.status === filters.status)
      .filter((suggestion) => filters.type === 'all' || suggestion.type === filters.type)
      .filter((suggestion) => filters.entityType === 'all' || suggestion.target.entityType === filters.entityType)
      .filter((suggestion) => {
        if (!query) {
          return true;
        }

        return [
          suggestion.title,
          suggestion.target.title,
          suggestion.target.subtitle || '',
          suggestion.explanation,
          suggestion.providerInfo.provider,
          suggestion.providerInfo.model,
          this.typeLabel(suggestion.type),
          this.entityTypeLabel(suggestion.target.entityType)
        ].some((value) => value.toLocaleLowerCase('es-ES').includes(query));
      })
      .sort((left, right) => this.statusRank(left.status) - this.statusRank(right.status) || right.updatedAt.localeCompare(left.updatedAt));
  });

  readonly activeSuggestion = computed(() => {
    const filtered = this.filteredSuggestions();
    if (filtered.length === 0) {
      return null;
    }

    const selectedId = this.selectedSuggestionId();
    return filtered.find((suggestion) => suggestion.id === selectedId) ?? filtered[0];
  });

  readonly entityTypeOptions = computed(() => {
    const entityTypes = new Set<ValidationEntityType>();
    for (const suggestion of this.suggestions()) {
      entityTypes.add(suggestion.target.entityType);
    }
    return Array.from(entityTypes.values()).sort((left, right) => this.entityTypeLabel(left).localeCompare(this.entityTypeLabel(right), 'es-ES'));
  });

  readonly pendingCount = computed(() => {
    return this.suggestions().filter((suggestion) => suggestion.status === 'PENDING_REVIEW').length;
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.list().subscribe({
      next: (suggestions) => {
        this.suggestions.set(suggestions);
        this.loading.set(false);
        if (!this.activeSuggestion() && suggestions.length > 0) {
          this.selectedSuggestionId.set(suggestions[0].id);
        }
      },
      error: () => {
        this.suggestions.set([]);
        this.loading.set(false);
        this.errorMessage.set('No se pudieron cargar las sugerencias internas de IA.');
      }
    });
  }

  applyFilters(): void {
    this.appliedFilters.set(this.filterForm.getRawValue());
    this.feedbackMessage.set('');
  }

  clearFilters(): void {
    const defaults: SuggestionFilters = {
      query: '',
      status: 'PENDING_REVIEW',
      type: 'all',
      entityType: 'all'
    };
    this.filterForm.reset(defaults);
    this.appliedFilters.set(defaults);
    this.feedbackMessage.set('');
  }

  selectSuggestion(id: string): void {
    this.selectedSuggestionId.set(id);
    this.feedbackMessage.set('');
  }

  isSelected(id: string): boolean {
    return this.activeSuggestion()?.id === id;
  }

  acceptSuggestion(): void {
    const suggestion = this.activeSuggestion();
    if (!suggestion || this.actionBusy()) {
      return;
    }

    this.runAction(
      () => this.api.accept(suggestion.id),
      'Sugerencia aceptada. El cambio sigue pendiente de aplicación o publicación según su flujo.'
    );
  }

  acceptSuggestionWithEdits(updates: AiSuggestionFieldUpdate[]): void {
    const suggestion = this.activeSuggestion();
    if (!suggestion || this.actionBusy()) {
      return;
    }

    this.runAction(
      () => this.api.acceptWithEdits(suggestion.id, updates),
      'Sugerencia aceptada con edición. La versión revisada queda lista para el siguiente paso interno.'
    );
  }

  rejectSuggestion(): void {
    const suggestion = this.activeSuggestion();
    if (!suggestion || this.actionBusy()) {
      return;
    }

    this.runAction(
      () => this.api.reject(suggestion.id),
      'Sugerencia rechazada. No se aplicará ningún cambio mientras no exista una nueva revisión.'
    );
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
    return validationEntityTypeLabel(value);
  }

  private runAction(action: () => ReturnType<AiSuggestionsApiService['accept']>, successMessage: string): void {
    this.actionBusy.set(true);
    this.feedbackMessage.set('');

    action().subscribe({
      next: (updatedSuggestion) => {
        this.suggestions.update((items) => items.map((item) => item.id === updatedSuggestion.id ? updatedSuggestion : item));
        this.selectedSuggestionId.set(updatedSuggestion.id);
        this.actionBusy.set(false);
        this.feedbackMessage.set(successMessage);
      },
      error: () => {
        this.actionBusy.set(false);
        this.feedbackMessage.set('No se pudo registrar la decisión sobre esta sugerencia.');
      }
    });
  }

  private statusRank(status: AiSuggestionStatus): number {
    switch (status) {
      case 'PENDING_REVIEW':
        return 0;
      case 'ACCEPTED_WITH_EDITS':
        return 1;
      case 'ACCEPTED':
        return 2;
      default:
        return 3;
    }
  }
}
