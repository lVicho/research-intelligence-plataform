import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AcademicMasterDataService } from '../../core/api/academic-master-data.service';
import {
  FilterCount,
  PageResponse,
  PublicationFilterMetadata,
  PublicationSemanticSearchResult,
  PublicationStatus,
  PublicationSummary,
  PublicationType,
  ResearcherSummary
} from '../../core/api/api-models';
import { PortalDemoQuerySuggestionsService } from '../../core/api/portal-demo-query-suggestions.service';
import { PublicationsApiService } from '../../core/api/publications-api.service';
import { ResearchersApiService } from '../../core/api/researchers-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { DemoQueryChipsComponent } from '../../shared/components/demo-query-chips.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { VisibilityNoteComponent } from '../../shared/components/visibility-note.component';
import { publicationStatusTone } from '../../shared/utils/display-labels';
import { publicVisibilityNote, visibilityNoteForUser } from '../../shared/utils/visibility-labels';

type SearchMode = 'fields' | 'semantic';
type TypeFilter = PublicationType | 'all';
type StatusFilter = PublicationStatus | 'all';
type PublicationSortBy = 'year' | 'title' | 'type' | 'status' | 'createdAt';
type SortDirection = 'asc' | 'desc';

interface UnifiedPublicationResult {
  id: number;
  title: string;
  year: number | null;
  type: PublicationType;
  status: PublicationStatus;
  doi: string | null;
  source: string | null;
  createdAt: string;
  topics: string[];
  authors: string[];
  similarityScore: number | null;
  lowSimilarity: boolean;
  retrievalReason: string | null;
}

const PUBLICATION_TYPES: PublicationType[] = [
  'ARTICLE',
  'BOOK',
  'BOOK_CHAPTER',
  'CONFERENCE_PAPER',
  'THESIS',
  'REPORT',
  'DATASET',
  'SOFTWARE',
  'OTHER'
];

const PUBLICATION_STATUSES: PublicationStatus[] = [
  'PUBLISHED',
  'ACCEPTED',
  'IN_PRESS',
  'DRAFT',
  'UNKNOWN'
];

const FALLBACK_SEMANTIC_EXAMPLES = [
  'IA clínica en hospitales',
  'colaboración interdisciplinar en salud digital',
  'sostenibilidad urbana y salud pública',
  'biodiversidad y corredores ecológicos',
  'grafos de conocimiento y genómica'
];

@Component({
  selector: 'rip-publications-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    DemoQueryChipsComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent,
    VisibilityNoteComponent
  ],
  template: `
    <section class="page publications-page" [class.portal-view]="isPortalView()">
      <rip-page-header
        title="Publicaciones"
        [subtitle]="pageSubtitle()"
        [eyebrow]="isPortalView() ? 'Portal público' : 'Producción científica'"
      >
        @if (canManageMasterData()) {
          <a mat-flat-button color="primary" [routerLink]="newPublicationLink()">Nueva publicación</a>
        }
      </rip-page-header>

      @if (!isPortalView()) {
        <rip-visibility-note [message]="pageVisibilityNote()" emphasis="strong" />
      }

      <section class="portal-intro" [class.internal-intro]="!isPortalView()">
        <div class="intro-copy">
          <p class="section-kicker">Descubrimiento público</p>
          <h2>Una sola búsqueda para explorar publicaciones por campos o por significado.</h2>
          <p>
            Alterna entre coincidencia por campos y búsqueda semántica sin cambiar de página ni de componente de
            resultados.
          </p>
          @if (isPortalView()) {
            <p class="portal-note">El portal muestra actividad pública revisada por la institución.</p>
          }
        </div>

        <div class="intro-pills">
          <span>{{ resultsCount() }} {{ resultsCountLabel() }}</span>
          <span>{{ metadata()?.topics?.length || 0 }} temas visibles</span>
        </div>
      </section>

      <mat-card appearance="outlined" class="discovery-card">
        <mat-card-content>
          @if (masterDataLoading()) {
            <p class="support-copy">Cargando filtros académicos...</p>
          }

          @if (masterDataError()) {
            <div class="inline-warning">
              <span>{{ masterDataError() }}</span>
              <button mat-button type="button" (click)="retryMasterData()">Reintentar</button>
            </div>
          }

          <form class="search-form" [formGroup]="searchForm" (ngSubmit)="submitSearch()">
            <div class="mode-row">
              <div class="mode-copy">
                <p class="section-kicker">Modo de búsqueda</p>
                <p class="support-copy">{{ modeHelperText() }}</p>
              </div>

              <mat-button-toggle-group
                formControlName="mode"
                aria-label="Modo de búsqueda"
                (change)="onModeChange()"
              >
                <mat-button-toggle value="fields">Por campos</mat-button-toggle>
                <mat-button-toggle value="semantic">Semántica</mat-button-toggle>
              </mat-button-toggle-group>
            </div>

            <div class="query-row">
              <mat-form-field appearance="outline" class="query-field">
                <mat-label>Búsqueda principal</mat-label>
                <input
                  matInput
                  formControlName="query"
                  placeholder="Buscar publicaciones, autores, temas o conceptos..."
                >
                <mat-hint>{{ queryFieldHint() }}</mat-hint>
              </mat-form-field>

              <div class="query-actions">
                <button mat-button type="button" (click)="clearSearch()">Limpiar</button>
                <button mat-flat-button color="primary" type="submit" [disabled]="submitDisabled()">
                  Buscar
                </button>
              </div>
            </div>

            @if (searchMode() === 'semantic') {
              <div class="semantic-support">
                <rip-demo-query-chips
                  [title]="semanticSuggestionTitle()"
                  [caption]="semanticSuggestionCaption()"
                  [queries]="semanticSuggestions()"
                  [disabled]="semanticLoading()"
                  (querySelected)="useSemanticExample($event)"
                />

                @if (canManageMasterData()) {
                  <mat-checkbox formControlName="includeNonValidated">
                    Incluir datos no validados
                  </mat-checkbox>
                }
              </div>
            }

            @if (searchMode() === 'fields') {
              <div class="advanced-actions">
                <button mat-button type="button" class="advanced-toggle" (click)="toggleAdvancedFilters()">
                  {{ showAdvancedFilters() ? 'Ocultar filtros avanzados' : 'Mostrar filtros avanzados' }}
                </button>

                @if (activeFieldFiltersCount() > 0) {
                  <span class="active-filter-count">{{ activeFieldFiltersCount() }} filtros activos</span>
                }
              </div>

              @if (showAdvancedFilters() || hasAdvancedFieldFilters()) {
                <div class="advanced-filters">
                  <mat-form-field appearance="outline">
                    <mat-label>Año desde</mat-label>
                    <input matInput type="number" formControlName="yearFrom" [placeholder]="metadata()?.minYear?.toString() || ''">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Año hasta</mat-label>
                    <input matInput type="number" formControlName="yearTo" [placeholder]="metadata()?.maxYear?.toString() || ''">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Tipo</mat-label>
                    <mat-select formControlName="type">
                      <mat-option value="all">Todos los tipos</mat-option>
                      @for (type of typeOptions(); track type.value) {
                        <mat-option [value]="type.value">{{ publicationTypeLabel(type.value) }} ({{ type.count }})</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Investigador</mat-label>
                    <mat-select formControlName="researcherId">
                      <mat-option value="all">Todos los investigadores</mat-option>
                      @for (researcher of researchers(); track researcher.id) {
                        <mat-option [value]="researcher.id.toString()">
                          {{ researcher.displayName || researcher.fullName }}
                        </mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Unidad</mat-label>
                    <mat-select formControlName="researchUnitId">
                      <mat-option value="all">Todas las unidades</mat-option>
                      @for (unit of metadata()?.researchUnits || []; track unit.id) {
                        <mat-option [value]="unit.id?.toString() || 'all'">{{ unit.label }} ({{ unit.count }})</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Tema</mat-label>
                    <mat-select formControlName="topic">
                      <mat-option value="">Todos los temas</mat-option>
                      @for (topic of metadata()?.topics || []; track topic.id) {
                        <mat-option [value]="topic.value">{{ topic.label }} ({{ topic.count }})</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  @if (!isPortalView()) {
                    <mat-form-field appearance="outline">
                      <mat-label>Estado</mat-label>
                      <mat-select formControlName="status">
                        <mat-option value="all">Todos los estados</mat-option>
                        @for (status of statusOptions(); track status.value) {
                          <mat-option [value]="status.value">{{ publicationStatusLabel(status.value) }} ({{ status.count }})</mat-option>
                        }
                      </mat-select>
                    </mat-form-field>
                  }
                </div>
              }
            }
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined" class="results-card">
        <mat-card-content>
          <div class="results-toolbar">
            <div>
              <p class="section-kicker">{{ searchMode() === 'semantic' ? 'Resultados semánticos' : 'Resultados' }}</p>
              <div class="result-count">{{ resultsCount() }} {{ resultsCountLabel() }}</div>
              <p class="support-copy">{{ resultsHelperText() }}</p>
            </div>

            @if (searchMode() === 'fields') {
              <div class="sort-actions">
                <button mat-button type="button" (click)="toggleSort('year')">Año {{ sortLabel('year') }}</button>
                <button mat-button type="button" (click)="toggleSort('title')">Título {{ sortLabel('title') }}</button>
              </div>
            }
          </div>

          @if (activeLoading()) {
            <rip-loading-state [message]="loadingMessage()" />
          } @else if (activeErrorMessage()) {
            <rip-error-state [message]="activeErrorMessage()" />
          } @else if (showSemanticPromptState()) {
            <rip-empty-state
              title="Empieza con una idea"
              message="Escribe una consulta en lenguaje natural para buscar publicaciones por significado."
            />
          } @else if (displayedResults().length === 0) {
            <rip-empty-state [title]="emptyStateTitle()" [message]="emptyStateMessage()" />
          } @else {
            <div class="results-grid">
              @for (publication of displayedResults(); track publication.id) {
                <a class="publication-card" [routerLink]="publicationLink(publication.id)" [queryParams]="navigationContext.returnQueryParams('Volver a publicaciones')">
                  <div class="card-top">
                    <span class="year-pill">{{ publication.year || 's. f.' }}</span>

                    @if (publication.similarityScore !== null) {
                      <span class="similarity-pill" [class.weak]="publication.lowSimilarity">
                        {{ similarityPercent(publication.similarityScore) }}
                      </span>
                    }
                  </div>

                  <div class="card-body">
                    <strong>{{ publication.title }}</strong>
                    <p>{{ sourceLabel(publication) }}</p>

                    @if (publication.authors.length > 0) {
                      <p class="authors">{{ publication.authors.join(', ') }}</p>
                    }

                    <div class="chip-list">
                      <rip-tag-chip [label]="publicationTypeLabel(publication.type)" tone="type" />

                      @if (!isPortalView()) {
                        <rip-status-chip
                          [label]="publicationStatusLabel(publication.status)"
                          [tone]="publicationStatusTone(publication.status)"
                        />
                      }

                      @for (topic of publication.topics.slice(0, 4); track topic) {
                        <rip-tag-chip [label]="topic" />
                      }
                    </div>

                    @if (publication.retrievalReason) {
                      <p class="semantic-note">{{ publication.retrievalReason }}</p>
                    }
                  </div>

                  @if (!isPortalView() && publication.similarityScore === null) {
                    <span class="created-date">Alta {{ publication.createdAt.slice(0, 10) }}</span>
                  }
                </a>
              }
            </div>
          }
        </mat-card-content>
      </mat-card>

      @if (searchMode() === 'fields') {
        <div class="pagination">
          <button mat-button type="button" [disabled]="currentPage() === 0" (click)="goToPage(currentPage() - 1)">Anterior</button>
          <span>Página {{ currentPage() + 1 }} de {{ result()?.totalPages || 1 }}</span>
          <button mat-button type="button" [disabled]="result()?.last ?? true" (click)="goToPage(currentPage() + 1)">Siguiente</button>
        </div>
      }
    </section>
  `,
  styles: [`
    .publications-page {
      display: grid;
      gap: 28px;
    }

    .portal-intro,
    .discovery-card,
    .results-card {
      border-radius: 14px !important;
    }

    .portal-intro {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 20px;
      align-items: start;
      padding: 28px 30px;
      border: 1px solid #dce6ed;
      border-radius: 14px;
      background: linear-gradient(135deg, #ffffff 0%, #f7fafc 100%);
    }

    .portal-intro.internal-intro {
      background: #ffffff;
    }

    .intro-copy {
      display: grid;
      gap: 10px;
    }

    .intro-copy h2 {
      margin: 0;
      color: #132133;
      font-size: clamp(1.5rem, 2.5vw, 2rem);
      line-height: 1.14;
    }

    .intro-copy p:not(.section-kicker) {
      margin: 0;
      color: #5f6c7d;
      line-height: 1.6;
      max-width: 72ch;
    }

    .portal-note {
      color: #697888;
      font-size: 0.92rem;
    }

    .intro-pills {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      justify-content: flex-end;
      align-self: end;
    }

    .intro-pills span {
      padding: 10px 14px;
      border: 1px solid #d8e4eb;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.9);
      color: #365369;
      font-size: 0.86rem;
      font-weight: 760;
      white-space: nowrap;
    }

    .discovery-card,
    .results-card {
      box-shadow: none;
    }

    .discovery-card mat-card-content,
    .results-card mat-card-content {
      display: grid;
      gap: 20px;
    }

    .search-form {
      display: grid;
      gap: 20px;
    }

    .mode-row,
    .query-row,
    .results-toolbar {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
    }

    .mode-copy {
      display: grid;
      gap: 6px;
      max-width: 64ch;
    }

    .query-row {
      align-items: stretch;
    }

    .query-field {
      flex: 1 1 auto;
    }

    .query-actions {
      display: flex;
      align-items: flex-end;
      justify-content: flex-end;
      gap: 10px;
      flex-wrap: wrap;
      min-width: 0;
    }

    .semantic-support {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 14px;
      flex-wrap: wrap;
      padding: 14px 16px;
      border: 1px solid #e0e8ee;
      border-radius: 12px;
      background: #fbfcfd;
    }

    .advanced-actions {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }

    .advanced-toggle {
      padding-left: 0;
    }

    .active-filter-count {
      color: #5f6f80;
      font-size: 0.84rem;
      font-weight: 720;
    }

    .advanced-filters {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 14px;
      padding: 18px;
      border: 1px solid #e2e9ef;
      border-radius: 12px;
      background: #fbfcfd;
    }

    .prompt-chips,
    .chip-list,
    .sort-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .prompt-chip {
      max-width: 100%;
      padding: 8px 12px;
      border: 1px solid #d7e0ea;
      border-radius: 999px;
      background: #ffffff;
      color: #324155;
      cursor: pointer;
      font: inherit;
      font-size: 0.86rem;
      line-height: 1.25;
      text-align: left;
      transition: border-color 140ms ease, background-color 140ms ease, transform 140ms ease;
    }

    .prompt-chip:hover,
    .publication-card:hover {
      border-color: #9db8c7;
      background: #f5fafc;
      transform: translateY(-2px);
    }

    .prompt-chip:disabled {
      cursor: default;
      transform: none;
      opacity: 0.7;
    }

    .support-copy {
      margin: 0;
      color: #667487;
      font-size: 0.92rem;
      line-height: 1.55;
    }

    .inline-warning {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 12px 14px;
      border: 1px solid #efd18b;
      border-radius: 12px;
      background: #fff9e9;
      color: #72510d;
      font-size: 0.9rem;
      line-height: 1.45;
    }

    .result-count {
      color: #132133;
      font-size: 1.1rem;
      font-weight: 760;
      line-height: 1.3;
    }

    .results-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 18px;
    }

    .publication-card {
      display: grid;
      gap: 16px;
      padding: 20px;
      border: 1px solid #dfe7ed;
      border-radius: 12px;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease, background-color 140ms ease;
    }

    .publication-card:hover {
      box-shadow: 0 14px 28px rgba(20, 32, 51, 0.07);
    }

    .publication-card:focus-visible,
    .prompt-chip:focus-visible {
      outline: 3px solid rgba(41, 91, 128, 0.18);
      outline-offset: 3px;
    }

    .card-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      flex-wrap: wrap;
    }

    .year-pill,
    .similarity-pill {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-height: 32px;
      padding: 0 12px;
      border-radius: 999px;
      font-size: 0.8rem;
      font-weight: 780;
      white-space: nowrap;
    }

    .year-pill {
      background: #eaf2f7;
      color: #31566a;
    }

    .similarity-pill {
      border: 1px solid #d5e5ec;
      background: #f5fafc;
      color: #174d67;
    }

    .similarity-pill.weak {
      border-color: #efd18b;
      background: #fff9e9;
      color: #8a5d0a;
    }

    .card-body {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .card-body strong {
      color: #142033;
      font-size: 1.05rem;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .card-body p,
    .authors,
    .semantic-note,
    .created-date {
      margin: 0;
      color: #667487;
      line-height: 1.55;
    }

    .authors {
      font-size: 0.9rem;
    }

    .semantic-note {
      font-size: 0.9rem;
    }

    .created-date {
      font-size: 0.82rem;
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      color: #5a6677;
    }

    @media (max-width: 860px) {
      .portal-intro,
      .mode-row,
      .query-row,
      .results-toolbar {
        flex-direction: column;
      }

      .intro-pills,
      .query-actions {
        justify-content: flex-start;
      }

      .query-actions {
        align-items: center;
      }

      .inline-warning {
        align-items: flex-start;
      }
    }

    @media (max-width: 640px) {
      .portal-intro {
        padding: 22px;
      }

      .results-grid {
        grid-template-columns: 1fr;
      }

      .pagination {
        justify-content: space-between;
      }
    }
  `]
})
export class PublicationsPageComponent implements OnInit {
  private readonly api = inject(PublicationsApiService);
  private readonly researchersApi = inject(ResearchersApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly masterData = inject(AcademicMasterDataService);
  private readonly demoQuerySuggestions = inject(PortalDemoQuerySuggestionsService);
  readonly navigationContext = inject(NavigationContextService);

  readonly result = signal<PageResponse<PublicationSummary> | null>(null);
  readonly metadata = signal<PublicationFilterMetadata | null>(null);
  readonly researchers = signal<ResearcherSummary[]>([]);
  readonly semanticResults = signal<PublicationSemanticSearchResult[]>([]);
  readonly semanticSuggestions = signal<string[]>(FALLBACK_SEMANTIC_EXAMPLES);
  readonly semanticSuggestionsDynamic = signal(false);
  readonly semanticLoading = signal(false);
  readonly semanticError = signal('');
  readonly semanticSearchRequested = signal(false);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly currentPage = signal(0);
  readonly sortBy = signal<PublicationSortBy>('year');
  readonly sortDirection = signal<SortDirection>('desc');
  readonly searchMode = signal<SearchMode>('fields');
  readonly showAdvancedFilters = signal(false);
  readonly isPortalView = signal(this.route.snapshot.data['portalView'] === true);
  readonly canManageMasterData = computed(() => this.auth.hasAnyRole(['ADMIN']) && !this.isPortalView());
  readonly masterDataLoading = this.masterData.loading;
  readonly masterDataError = this.masterData.error;

  readonly typeOptions = computed<FilterCount[]>(() => this.metadata()?.availableTypes ?? []);
  readonly statusOptions = computed<FilterCount[]>(() => this.metadata()?.availableStatuses ?? []);
  readonly displayedResults = computed<UnifiedPublicationResult[]>(() => {
    if (this.searchMode() === 'semantic') {
      return this.semanticResults().map((publication) => ({
        id: publication.id,
        title: publication.title,
        year: publication.year,
        type: publication.type,
        status: publication.status,
        doi: publication.doi,
        source: publication.source,
        createdAt: publication.createdAt,
        topics: publication.topics,
        authors: publication.authors,
        similarityScore: publication.similarityScore,
        lowSimilarity: publication.lowSimilarity,
        retrievalReason: publication.retrievalReason
      }));
    }

    return (this.result()?.content ?? []).map((publication) => ({
      id: publication.id,
      title: publication.title,
      year: publication.year,
      type: publication.type,
      status: publication.status,
      doi: publication.doi,
      source: publication.source,
      createdAt: publication.createdAt,
      topics: publication.topics,
      authors: [],
      similarityScore: null,
      lowSimilarity: false,
      retrievalReason: null
    }));
  });
  readonly pageSubtitle = computed(() => this.isPortalView()
    ? 'Explora publicaciones con una experiencia pública unificada de búsqueda y resultados.'
    : 'Busca por campos o por afinidad semántica dentro del catálogo institucional.');
  readonly pageVisibilityNote = computed(() => this.isPortalView() ? publicVisibilityNote() : visibilityNoteForUser(this.auth.currentUser()));
  readonly resultsCount = computed(() => this.searchMode() === 'semantic'
    ? this.semanticResults().length
    : this.result()?.totalElements ?? 0);
  readonly semanticSuggestionTitle = computed(() => this.semanticSuggestionsDynamic() ? 'Prueba con:' : 'Consultas sugeridas');
  readonly semanticSuggestionCaption = computed(() => this.semanticSuggestionsDynamic() ? 'Inspiradas en los datos del portal' : '');

  readonly searchForm = new FormGroup({
    mode: new FormControl<SearchMode>('fields', { nonNullable: true }),
    query: new FormControl('', { nonNullable: true }),
    yearFrom: new FormControl('', { nonNullable: true }),
    yearTo: new FormControl('', { nonNullable: true }),
    type: new FormControl<TypeFilter>('all', { nonNullable: true }),
    status: new FormControl<StatusFilter>('all', { nonNullable: true }),
    researchUnitId: new FormControl('all', { nonNullable: true }),
    researcherId: new FormControl('all', { nonNullable: true }),
    topic: new FormControl('', { nonNullable: true }),
    includeNonValidated: new FormControl(false, { nonNullable: true })
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();
    this.loadFilterMetadata();
    this.loadResearcherOptions();
    this.loadDemoQueries();

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const mode = this.toSearchMode(params.get('mode'));
        const query = params.get('q') ?? params.get('text') ?? '';
        const yearFrom = params.get('yearFrom') ?? '';
        const yearTo = params.get('yearTo') ?? '';
        const type = this.toTypeFilter(params.get('type'));
        const status = this.toStatusFilter(params.get('status'));
        const researchUnitId = params.get('researchUnitId') ?? 'all';
        const researcherId = params.get('researcherId') ?? 'all';
        const topic = params.get('topic') ?? '';
        const includeNonValidated = this.toBoolean(params.get('includeNonValidated')) && this.canManageMasterData();
        const page = Math.max(this.toNumber(params.get('page') ?? '') ?? 0, 0);
        const sortBy = this.toSortBy(params.get('sortBy'));
        const sortDirection = this.toSortDirection(params.get('sortDirection'));

        this.searchMode.set(mode);
        this.currentPage.set(page);
        this.sortBy.set(sortBy);
        this.sortDirection.set(sortDirection);
        this.showAdvancedFilters.set(mode === 'fields' && this.hasAdvancedFieldFilters({
          yearFrom,
          yearTo,
          type,
          status,
          researchUnitId,
          researcherId,
          topic
        }));
        this.searchForm.patchValue(
          { mode, query, yearFrom, yearTo, type, status, researchUnitId, researcherId, topic, includeNonValidated },
          { emitEvent: false }
        );

        if (mode === 'semantic') {
          this.loadSemanticResults(query, includeNonValidated);
          return;
        }

        this.semanticResults.set([]);
        this.semanticLoading.set(false);
        this.semanticError.set('');
        this.semanticSearchRequested.set(false);
        this.loadFieldResults({
          page,
          text: query || undefined,
          yearFrom: this.toNumber(yearFrom),
          yearTo: this.toNumber(yearTo),
          type: type === 'all' ? undefined : type,
          status: status === 'all' ? undefined : status,
          researchUnitId: this.toNumber(researchUnitId),
          researcherId: this.toNumber(researcherId),
          topic: topic || undefined,
          sortBy,
          sortDirection
        });
      });
  }

  submitSearch(): void {
    if (this.submitDisabled()) {
      return;
    }

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.buildQueryParams({ page: null }),
      queryParamsHandling: 'merge'
    });
  }

  clearSearch(): void {
    this.searchForm.reset({
      mode: 'fields',
      query: '',
      yearFrom: '',
      yearTo: '',
      type: 'all',
      status: 'all',
      researchUnitId: 'all',
      researcherId: 'all',
      topic: '',
      includeNonValidated: false
    });
    this.showAdvancedFilters.set(false);

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        mode: null,
        q: null,
        text: null,
        page: null,
        yearFrom: null,
        yearTo: null,
        type: null,
        status: null,
        researchUnitId: null,
        researcherId: null,
        topic: null,
        includeNonValidated: null,
        sortBy: null,
        sortDirection: null
      },
      queryParamsHandling: 'merge'
    });
  }

  onModeChange(): void {
    this.showAdvancedFilters.set(this.searchForm.controls.mode.value === 'fields' && this.hasAdvancedFieldFilters());

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.buildQueryParams({ page: null }),
      queryParamsHandling: 'merge'
    });
  }

  toggleAdvancedFilters(): void {
    this.showAdvancedFilters.update((visible) => !visible);
  }

  toggleSort(sortBy: PublicationSortBy): void {
    const nextDirection: SortDirection = this.sortBy() === sortBy && this.sortDirection() === 'asc' ? 'desc' : 'asc';
    this.sortBy.set(sortBy);
    this.sortDirection.set(nextDirection);

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.buildQueryParams({
        page: null,
        sortBy: sortBy === 'year' ? null : sortBy,
        sortDirection: nextDirection === 'desc' ? null : nextDirection
      }),
      queryParamsHandling: 'merge'
    });
  }

  sortLabel(sortBy: PublicationSortBy): string {
    if (this.sortBy() !== sortBy) {
      return '';
    }
    return this.sortDirection() === 'asc' ? '↑' : '↓';
  }

  goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.buildQueryParams({ page: page === 0 ? null : page }),
      queryParamsHandling: 'merge'
    });
  }

  retryMasterData(): void {
    this.masterData.retry();
  }

  useSemanticExample(example: string): void {
    this.searchForm.controls.query.setValue(example);
    this.submitSearch();
  }

  submitDisabled(): boolean {
    return this.loading() || this.semanticLoading();
  }

  activeLoading(): boolean {
    return this.searchMode() === 'semantic' ? this.semanticLoading() : this.loading();
  }

  activeErrorMessage(): string {
    return this.searchMode() === 'semantic' ? this.semanticError() : this.errorMessage();
  }

  loadingMessage(): string {
    return this.searchMode() === 'semantic'
      ? 'Buscando publicaciones relacionadas'
      : 'Cargando publicaciones';
  }

  showSemanticPromptState(): boolean {
    const trimmedQuery = this.searchForm.controls.query.value.trim();
    return this.searchMode() === 'semantic'
      && !this.semanticLoading()
      && !this.semanticError()
      && this.displayedResults().length === 0
      && !this.semanticSearchRequested()
      && trimmedQuery.length === 0;
  }

  emptyStateTitle(): string {
    return 'Sin resultados';
  }

  emptyStateMessage(): string {
    return this.searchMode() === 'semantic'
      ? 'No se han encontrado publicaciones semánticamente cercanas.'
      : 'No se han encontrado publicaciones con esos filtros.';
  }

  resultsCountLabel(): string {
    if (this.searchMode() === 'semantic') {
      return this.resultsCount() === 1 ? 'resultado semántico' : 'resultados semánticos';
    }
    return this.resultsCount() === 1 ? 'publicación encontrada' : 'publicaciones encontradas';
  }

  resultsHelperText(): string {
    return this.searchMode() === 'semantic'
      ? 'Los resultados se muestran en las mismas tarjetas y se ordenan por afinidad semántica.'
      : 'La lista reúne coincidencias por campos y mantiene los filtros avanzados como apoyo secundario.';
  }

  modeHelperText(): string {
    return this.searchMode() === 'semantic'
      ? 'Describe una idea, una pregunta o un concepto para recuperar publicaciones cercanas por significado.'
      : 'Usa coincidencia por campos cuando quieras combinar texto libre con filtros bibliográficos más precisos.';
  }

  queryFieldHint(): string {
    return this.searchMode() === 'semantic'
      ? 'La búsqueda semántica interpreta intención y contexto en la misma lista de resultados.'
      : 'Busca por texto y apóyate en filtros avanzados si necesitas acotar por año, tipo, unidad o tema.';
  }

  hasAdvancedFieldFilters(value?: {
    yearFrom: string;
    yearTo: string;
    type: TypeFilter;
    status: StatusFilter;
    researchUnitId: string;
    researcherId: string;
    topic: string;
  }): boolean {
    const currentValue = value ?? this.searchForm.getRawValue();
    return Boolean(
      currentValue.yearFrom
      || currentValue.yearTo
      || currentValue.type !== 'all'
      || currentValue.status !== 'all'
      || currentValue.researchUnitId !== 'all'
      || currentValue.researcherId !== 'all'
      || currentValue.topic
    );
  }

  activeFieldFiltersCount(): number {
    const value = this.searchForm.getRawValue();
    return [
      value.yearFrom,
      value.yearTo,
      value.type !== 'all' ? value.type : '',
      !this.isPortalView() && value.status !== 'all' ? value.status : '',
      value.researchUnitId !== 'all' ? value.researchUnitId : '',
      value.researcherId !== 'all' ? value.researcherId : '',
      value.topic
    ].filter(Boolean).length;
  }

  similarityPercent(score: number): string {
    return `${Math.round(score * 100)}%`;
  }

  sourceLabel(publication: UnifiedPublicationResult): string {
    return publication.source || publication.doi || 'Fuente no disponible';
  }

  publicationLink(publicationId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/publicaciones')
      ? ['/admin/publicaciones', String(publicationId)]
      : this.isPortalView()
        ? ['/portal/publicaciones', String(publicationId)]
      : ['/publications', String(publicationId)];
  }

  newPublicationLink(): string[] {
    return this.navigationContext.isCurrentPath('/admin/publicaciones') ? ['/admin/publicaciones/new'] : ['/publications/new'];
  }

  publicationTypeLabel(type: string | PublicationType): string {
    return this.masterData.publicationTypeLabel(type);
  }

  publicationStatusLabel(status: string | PublicationStatus): string {
    return this.masterData.publicationStatusLabel(status);
  }

  publicationStatusTone(status: string | PublicationStatus): 'neutral' | 'success' | 'warning' | 'info' {
    return publicationStatusTone(status);
  }

  private loadFilterMetadata(): void {
    this.api.filterMetadata()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (metadata) => this.metadata.set(metadata),
        error: () => this.metadata.set(null)
      });
  }

  private loadResearcherOptions(): void {
    this.researchersApi.search({
      size: 100,
      active: this.isPortalView() ? true : undefined
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const researchers = [...result.content].sort((left, right) =>
            (left.displayName || left.fullName).localeCompare(right.displayName || right.fullName, 'es')
          );
          this.researchers.set(researchers);
        },
        error: () => this.researchers.set([])
      });
  }

  private loadDemoQueries(): void {
    this.demoQuerySuggestions.loadSuggestions({
      context: 'PUBLICATIONS',
      fallbackQueries: FALLBACK_SEMANTIC_EXAMPLES
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.semanticSuggestions.set(result.queries);
        this.semanticSuggestionsDynamic.set(result.dynamic);
      });
  }

  private loadFieldResults(filters: {
    page: number;
    text?: string;
    yearFrom?: number;
    yearTo?: number;
    type?: PublicationType;
    status?: PublicationStatus;
    researchUnitId?: number;
    researcherId?: number;
    topic?: string;
    sortBy: PublicationSortBy;
    sortDirection: SortDirection;
  }): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.api.search(filters)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
        },
        error: () => {
          this.result.set(null);
          this.loading.set(false);
          this.errorMessage.set('No se pudo cargar la lista de publicaciones.');
        }
      });
  }

  private loadSemanticResults(query: string, includeNonValidated: boolean): void {
    const trimmedQuery = query.trim();
    this.loading.set(false);
    this.errorMessage.set('');
    this.semanticResults.set([]);
    this.semanticError.set('');

    if (trimmedQuery.length === 0) {
      this.semanticLoading.set(false);
      this.semanticSearchRequested.set(false);
      return;
    }

    this.semanticLoading.set(true);
    this.semanticSearchRequested.set(true);

    this.api.semanticSearch({
      query: trimmedQuery,
      limit: 20,
      minSimilarity: 0.35,
      includeNonValidated
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => {
          this.semanticResults.set(results);
          this.semanticLoading.set(false);
        },
        error: () => {
          this.semanticResults.set([]);
          this.semanticLoading.set(false);
          this.semanticError.set('No se pudo ejecutar la búsqueda semántica.');
        }
      });
  }

  private buildQueryParams(overrides: Record<string, string | number | boolean | null>) {
    const value = this.searchForm.getRawValue();
    return {
      mode: value.mode === 'fields' ? null : value.mode,
      q: value.query.trim() || null,
      text: null,
      yearFrom: value.yearFrom || null,
      yearTo: value.yearTo || null,
      type: value.type === 'all' ? null : value.type,
      status: value.status === 'all' ? null : value.status,
      researchUnitId: value.researchUnitId === 'all' ? null : value.researchUnitId,
      researcherId: value.researcherId === 'all' ? null : value.researcherId,
      topic: value.topic || null,
      includeNonValidated: this.canManageMasterData() && value.includeNonValidated ? true : null,
      sortBy: this.sortBy() === 'year' ? null : this.sortBy(),
      sortDirection: this.sortDirection() === 'desc' ? null : this.sortDirection(),
      ...overrides
    };
  }

  private toNumber(value: string): number | undefined {
    const parsed = Number(value);
    return Number.isFinite(parsed) && value !== '' && value !== 'all' ? parsed : undefined;
  }

  private toBoolean(value: string | null): boolean {
    return value === 'true';
  }

  private toSearchMode(value: string | null): SearchMode {
    return value === 'semantic' ? 'semantic' : 'fields';
  }

  private toTypeFilter(value: string | null): TypeFilter {
    return PUBLICATION_TYPES.includes(value as PublicationType) ? value as PublicationType : 'all';
  }

  private toStatusFilter(value: string | null): StatusFilter {
    return PUBLICATION_STATUSES.includes(value as PublicationStatus) ? value as PublicationStatus : 'all';
  }

  private toSortBy(value: string | null): PublicationSortBy {
    return value === 'title' || value === 'type' || value === 'status' || value === 'createdAt' ? value : 'year';
  }

  private toSortDirection(value: string | null): SortDirection {
    return value === 'asc' ? 'asc' : 'desc';
  }
}


