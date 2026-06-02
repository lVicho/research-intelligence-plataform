import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { catchError, of } from 'rxjs';

import {
  ExpertFinderApiService,
  ExpertFinderResult,
  ExpertFinderSearchResponse
} from '../../core/api/expert-finder-api.service';
import { PortalApiService } from '../../core/api/portal-api.service';
import { PortalDemoQuerySuggestionsService } from '../../core/api/portal-demo-query-suggestions.service';
import { PortalContextAssistantSearchRequest, PortalResearchUnitSummary, RetrievalMode } from '../../core/api/api-models';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { DemoQueryChipsComponent } from '../../shared/components/demo-query-chips.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { PortalContextAssistantComponent } from '../portal/portal-context-assistant.component';
import {
  ExpertFinderEvidenceDialogComponent,
  ExpertFinderEvidenceDialogData
} from './expert-finder-evidence-dialog.component';

type ExpertFinderMode = RetrievalMode;

interface ModeConfiguration {
  label: string;
  helper: string;
  limit: number;
}

const MODE_CONFIGURATION: Record<ExpertFinderMode, ModeConfiguration> = {
  STRICT: {
    label: 'Estricto',
    helper: 'Prioriza coincidencias muy directas.',
    limit: 8
  },
  BALANCED: {
    label: 'Equilibrado',
    helper: 'Combina coincidencia temática y evidencias relacionadas.',
    limit: 12
  },
  BROAD: {
    label: 'Amplio',
    helper: 'Incluye perfiles relacionados de forma más exploratoria.',
    limit: 16
  }
};

const FALLBACK_EXPERT_QUERY_EXAMPLES = [
  'IA local en hospitales',
  'panteras y conservación',
  'salud pública y clima urbano',
  'grafos de conocimiento en genómica'
];

@Component({
  selector: 'rip-expert-finder-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    DemoQueryChipsComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    PortalContextAssistantComponent,
    TagChipComponent
  ],
  template: `
    <section class="page expert-finder-page">
      <rip-page-header
        title="Guía de expertos"
        subtitle="Encuentra investigadores por temas, líneas de trabajo o experiencia."
        eyebrow="Portal público"
      />

      <section class="portal-list-intro">
        <p class="section-kicker">Búsqueda experta</p>
        <h2>Perfiles públicos con evidencias validadas y afinidad temática.</h2>
        <p>
          Describe una necesidad de investigación y usa los filtros como apoyo lateral. La guía siempre trabaja con
          investigadores activos y evidencia pública revisada por la institución.
        </p>
      </section>

      <form class="portal-search-strip" [formGroup]="searchForm" (ngSubmit)="submitSearch()">
        <mat-form-field appearance="outline" class="search-field">
          <mat-label>Buscar expertos</mat-label>
          <input
            matInput
            formControlName="query"
            placeholder="Busca por tema, problema, técnica o área de conocimiento..."
          >
        </mat-form-field>

        <div class="search-actions">
          <button mat-flat-button color="primary" type="submit" [disabled]="searchForm.invalid || loading()">
            Buscar
          </button>
          <button mat-stroked-button type="button" class="filter-toggle" (click)="toggleFilters()">
            Filtros
          </button>
        </div>
      </form>

      <section class="suggestions-strip">
        <rip-demo-query-chips
          title="Consultas sugeridas"
          [caption]="exampleQueryCaption()"
          [queries]="exampleQueries()"
          [disabled]="loading()"
          (querySelected)="useExample($event)"
        />
      </section>

      <section class="portal-search-layout">
        <aside class="filter-panel" [class.open]="filtersOpen()" [formGroup]="searchForm">
          <div class="filter-panel-heading">
            <div>
              <p class="section-kicker">Filtros</p>
              <h3>Acotar búsqueda</h3>
            </div>
            <button mat-button type="button" class="mobile-close" (click)="toggleFilters()">Cerrar</button>
          </div>

          <mat-form-field appearance="outline">
            <mat-label>Unidad</mat-label>
            <mat-select formControlName="researchUnitId">
              <mat-option value="all">Todas las unidades</mat-option>
              @for (unit of researchUnits(); track unit.id) {
                <mat-option [value]="unit.id.toString()">{{ unit.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <details class="advanced-options">
            <summary>Opciones avanzadas</summary>
            <div class="mode-choice-group" role="group" aria-label="Modo de búsqueda">
              @for (mode of modeEntries; track mode.value) {
                <button
                  type="button"
                  class="mode-choice"
                  [class.selected]="selectedMode() === mode.value"
                  (click)="selectMode(mode.value)"
                >
                  {{ mode.config.label }}
                </button>
              }
            </div>

            <div class="mode-explainer">
              @for (mode of modeEntries; track mode.value) {
                <p>
                  <strong>{{ mode.config.label }}:</strong>
                  {{ mode.config.helper }}
                </p>
              }
            </div>
          </details>

          <button mat-button type="button" class="clear-button" (click)="clearSearch()">Limpiar búsqueda</button>
        </aside>

        <main class="results-panel">
          @if (publicWarnings().length > 0) {
            <section class="info-panel">
              @for (warning of publicWarnings(); track warning) {
                <p>{{ warning }}</p>
              }
            </section>
          }

          @if (loading()) {
            <rip-loading-state message="Buscando expertos..." />
          } @else if (errorMessage()) {
            <rip-error-state message="No se ha podido cargar la guía de expertos." />
          } @else if (!hasSearched()) {
            <rip-empty-state
              title="Empieza con una consulta"
              message="Usa una búsqueda temática o una sugerencia para encontrar perfiles con evidencia pública."
            />
          } @else if (results().length === 0) {
            <rip-empty-state
              title="Sin resultados"
              message="No se han encontrado expertos con esos criterios. Prueba a limpiar filtros o usar una búsqueda más amplia."
            />
          } @else {
            <section class="results-summary">
              <div>
                <p class="section-kicker">Resultados</p>
                <h3>{{ results().length }} expertos encontrados</h3>
              </div>
              <p>Ordenados por afinidad con la búsqueda.</p>
            </section>

            <rip-portal-context-assistant
              contextScope="EXPERT_FINDER_RESULTS"
              [searchRequest]="expertAssistantSearchRequest()"
              triggerLabel="Preguntar a los candidatos"
              contextTitle="Preguntas sobre los candidatos de esta búsqueda"
              helperText="Pregunta por comparaciones, fortalezas o evidencias de los candidatos calculados por la guía de expertos."
              [maxEvidence]="16"
            />

            <div class="results-list">
              @for (expert of results(); track expert.researcher.id) {
                <article class="expert-card">
                  <div class="expert-card-header">
                    <div class="identity-block">
                      <strong>{{ expert.researcher.displayName || expert.researcher.fullName }}</strong>
                      <p>{{ affiliationLabel(expert) }}</p>
                    </div>
                    <div class="affinity-badge">
                      <span>Afinidad</span>
                      <strong>{{ affinityLabel(expert.score) }}</strong>
                    </div>
                  </div>

                  <p class="explanation">{{ publicExplanation(expert) }}</p>

                  <div class="meta-row">
                    @if (expert.researcher.orcid) {
                      <span>ORCID</span>
                    }
                    <span>{{ evidenceLabel(expert) }}</span>
                    @if (expert.confidence) {
                      <span>{{ confidenceLabel(expert.confidence) }}</span>
                    }
                  </div>

                  <div class="topic-row">
                    @for (topic of visibleTopics(expert); track topic) {
                      <rip-tag-chip [label]="topic" />
                    }
                    @if (hiddenTopicCount(expert) > 0) {
                      <span class="more-chip">+{{ hiddenTopicCount(expert) }}</span>
                    }
                  </div>

                  <div class="card-actions">
                    <a
                      mat-stroked-button
                      [routerLink]="['/portal/investigadores', expert.researcher.id]"
                      [queryParams]="navigationContext.returnQueryParams('Volver a la guía de expertos')"
                    >
                      Ver perfil
                    </a>
                    <button mat-flat-button color="primary" type="button" (click)="openEvidence(expert)">
                      Ver evidencias
                    </button>
                  </div>
                </article>
              }
            </div>
          }
        </main>
      </section>
    </section>
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
      max-width: 100%;
    }

    :host * {
      box-sizing: border-box;
    }

    :host ::ng-deep .mat-mdc-form-field,
    :host ::ng-deep .mat-mdc-form-field-infix {
      min-width: 0;
      max-width: 100%;
    }

    .expert-finder-page {
      display: grid;
      gap: 26px;
      width: 100%;
      max-width: 100%;
      min-width: 0;
      overflow-x: hidden;
      overflow-wrap: anywhere;
    }

    .portal-list-intro {
      display: grid;
      gap: 10px;
      width: 100%;
      max-width: 900px;
      min-width: 0;
    }

    .portal-list-intro h2 {
      margin: 0;
      color: #102033;
      font-size: clamp(1.5rem, 2.3vw, 2.05rem);
      line-height: 1.16;
    }

    .portal-list-intro p:not(.section-kicker) {
      margin: 0;
      color: #5f7182;
      line-height: 1.65;
    }

    .portal-search-strip {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 14px;
      align-items: center;
      width: 100%;
      max-width: 100%;
      min-width: 0;
      padding: 18px;
      border: 1px solid #dce7ed;
      border-radius: 18px;
      background: #ffffff;
    }

    .search-field {
      width: 100%;
    }

    :host ::ng-deep .portal-search-strip .mat-mdc-form-field-subscript-wrapper {
      display: none;
    }

    .search-actions,
    .card-actions,
    .meta-row,
    .topic-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .search-actions {
      justify-content: flex-end;
      min-width: 0;
    }

    .search-actions button {
      min-width: 0;
    }

    .filter-toggle,
    .mobile-close {
      display: none;
    }

    .suggestions-strip {
      margin-top: -12px;
      max-width: 100%;
      min-width: 0;
      overflow: hidden;
    }

    :host ::ng-deep rip-demo-query-chips,
    :host ::ng-deep rip-demo-query-chips .demo-query-block,
    :host ::ng-deep rip-demo-query-chips .chip-row {
      max-width: 100%;
      min-width: 0;
    }

    :host ::ng-deep rip-demo-query-chips .query-chip {
      min-width: 0;
      max-width: 100%;
    }

    .portal-search-layout {
      display: grid;
      grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
      gap: 22px;
      align-items: start;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .filter-panel,
    .results-summary,
    .expert-card,
    .info-panel {
      border: 1px solid #dce7ed;
      border-radius: 18px;
      background: #ffffff;
    }

    .filter-panel {
      position: sticky;
      top: 92px;
      display: grid;
      gap: 16px;
      min-width: 0;
      padding: 18px;
    }

    .filter-panel-heading {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      align-items: start;
    }

    .filter-panel h3,
    .results-summary h3,
    .identity-block strong,
    .advanced-options summary,
    .mode-explainer p,
    .explanation,
    .info-panel p {
      margin: 0;
    }

    .filter-panel h3,
    .results-summary h3 {
      color: #132133;
      line-height: 1.2;
    }

    .section-kicker {
      margin: 0 0 4px;
      color: var(--portal-accent-700, #245b73);
      font-size: 0.76rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .advanced-options {
      display: grid;
      gap: 14px;
      padding-top: 4px;
    }

    .advanced-options summary {
      cursor: pointer;
      color: #526879;
      font-size: 0.9rem;
      font-weight: 800;
    }

    .advanced-options[open] {
      display: grid;
      padding: 14px;
      border: 1px solid #dce7ed;
      border-radius: 14px;
      background: #f8fafb;
    }

    .advanced-options[open] summary {
      margin-bottom: 12px;
    }

    .mode-choice-group {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 6px;
      padding: 4px;
      border: 1px solid #d8e4eb;
      border-radius: 999px;
      background: #ffffff;
    }

    .mode-choice {
      min-width: 0;
      padding: 8px 10px;
      border: 0;
      border-radius: 999px;
      background: transparent;
      color: #536776;
      cursor: pointer;
      font: inherit;
      font-size: 0.82rem;
      font-weight: 780;
      line-height: 1.2;
      text-align: center;
      transition: background-color 140ms ease, color 140ms ease, box-shadow 140ms ease;
    }

    .mode-choice.selected {
      background: var(--portal-accent-700, #245b73);
      color: #ffffff;
      box-shadow: 0 8px 16px rgba(20, 32, 51, 0.12);
    }

    .mode-explainer {
      display: grid;
      gap: 8px;
      color: #607286;
      font-size: 0.88rem;
      line-height: 1.45;
    }

    .mode-explainer strong {
      color: #26394c;
    }

    .clear-button {
      justify-self: start;
    }

    .results-panel {
      display: grid;
      gap: 16px;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .info-panel {
      display: grid;
      gap: 6px;
      padding: 14px 16px;
      background: #f6fbfd;
    }

    .info-panel p,
    .results-summary p,
    .identity-block p,
    .explanation {
      color: #607286;
      line-height: 1.55;
    }

    .results-summary {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      padding: 18px;
    }

    .results-summary p {
      margin: 0;
    }

    .results-list {
      display: grid;
      gap: 14px;
      min-width: 0;
    }

    .expert-card {
      display: grid;
      gap: 14px;
      min-width: 0;
      padding: 20px;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .expert-card:hover {
      border-color: #9dc1d1;
      box-shadow: 0 16px 32px rgba(20, 32, 51, 0.08);
      transform: translateY(-1px);
    }

    .expert-card-header {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 14px;
      align-items: start;
    }

    .identity-block {
      display: grid;
      gap: 6px;
      min-width: 0;
    }

    .identity-block strong {
      color: #132133;
      font-size: 1.12rem;
      line-height: 1.28;
      overflow-wrap: anywhere;
    }

    .identity-block p {
      margin: 0;
    }

    .affinity-badge {
      display: grid;
      gap: 3px;
      min-width: 96px;
      padding: 10px 12px;
      border: 1px solid #d8e4eb;
      border-radius: 8px;
      background: #f8fafb;
      text-align: right;
    }

    .affinity-badge span {
      color: #607286;
      font-size: 0.76rem;
      font-weight: 800;
      text-transform: uppercase;
    }

    .affinity-badge strong {
      color: var(--portal-accent-700, #245b73);
      font-size: 1.05rem;
      line-height: 1;
    }

    .explanation {
      max-width: 76ch;
    }

    .meta-row span,
    .more-chip {
      padding: 6px 10px;
      border: 1px solid #d8e4eb;
      border-radius: 999px;
      background: #f8fafb;
      color: #365369;
      font-size: 0.78rem;
      font-weight: 760;
      line-height: 1.2;
    }

    .card-actions {
      justify-content: flex-start;
      padding-top: 2px;
    }

    @media (max-width: 900px) {
      .portal-search-strip,
      .portal-search-layout,
      .expert-card-header {
        grid-template-columns: 1fr;
      }

      .search-actions {
        justify-content: stretch;
      }

      .search-actions button,
      .card-actions a,
      .card-actions button {
        flex: 1 1 auto;
      }

      .filter-toggle,
      .mobile-close {
        display: inline-flex;
      }

      .filter-panel {
        position: static;
        display: none;
      }

      .filter-panel.open {
        display: grid;
      }

      .affinity-badge {
        width: fit-content;
        text-align: left;
      }
    }

    @media (max-width: 520px) {
      :host,
      .expert-finder-page,
      .portal-list-intro,
      .portal-search-strip,
      .suggestions-strip,
      .portal-search-layout,
      .results-panel,
      .results-summary,
      .expert-card {
        width: calc(100vw - 96px);
        max-width: calc(100vw - 96px);
      }

      .portal-search-strip,
      .filter-panel,
      .results-summary,
      .expert-card {
        padding: 16px;
      }

      .filter-panel {
        width: 100%;
        max-width: 100%;
      }

      .search-actions {
        display: grid;
        grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
        width: 100%;
      }

      .search-actions button {
        width: 100%;
      }

      .mode-choice-group {
        grid-template-columns: 1fr;
        border-radius: 14px;
      }

      .card-actions {
        align-items: stretch;
        flex-direction: column;
      }
    }
  `]
})
export class ExpertFinderPageComponent implements OnInit {
  private readonly expertFinderApi = inject(ExpertFinderApiService);
  private readonly portalApi = inject(PortalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly demoQuerySuggestions = inject(PortalDemoQuerySuggestionsService);
  readonly navigationContext = inject(NavigationContextService);

  readonly results = signal<ExpertFinderResult[]>([]);
  readonly researchUnits = signal<PortalResearchUnitSummary[]>([]);
  readonly exampleQueries = signal<string[]>(FALLBACK_EXPERT_QUERY_EXAMPLES);
  readonly exampleQueriesDynamic = signal(false);
  readonly loading = signal(false);
  readonly hasSearched = signal(false);
  readonly errorMessage = signal('');
  readonly responseWarnings = signal<string[]>([]);
  readonly filtersOpen = signal(false);
  readonly selectedMode = signal<ExpertFinderMode>('BALANCED');
  readonly publicWarnings = computed(() =>
    this.responseWarnings()
      .map((warning) => this.publicWarning(warning))
      .filter((warning): warning is string => !!warning)
  );
  readonly exampleQueryCaption = computed(() => this.exampleQueriesDynamic() ? 'Inspiradas en los datos del portal' : '');
  readonly modeEntries = Object.entries(MODE_CONFIGURATION).map(([value, config]) => ({
    value: value as ExpertFinderMode,
    config
  }));

  readonly searchForm = new FormGroup({
    query: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3)] }),
    mode: new FormControl<ExpertFinderMode>('BALANCED', { nonNullable: true }),
    researchUnitId: new FormControl('all', { nonNullable: true })
  });

  ngOnInit(): void {
    this.loadDemoQueries();
    this.loadResearchUnits();

    this.searchForm.controls.mode.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((mode) => this.selectedMode.set(mode));

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const query = params.get('q') ?? '';
        const mode = this.toMode(params.get('mode'));
        const researchUnitId = params.get('researchUnitId') ?? 'all';

        this.selectedMode.set(mode);
        this.searchForm.patchValue({ query, mode, researchUnitId }, { emitEvent: false });

        if (query.trim().length < 3) {
          this.resetResults();
          return;
        }

        this.runSearch(query.trim(), mode, this.toNumber(researchUnitId));
      });
  }

  submitSearch(): void {
    if (this.searchForm.invalid || this.loading()) {
      return;
    }

    const value = this.searchForm.getRawValue();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: value.query.trim() || null,
        mode: value.mode === 'BALANCED' ? null : value.mode,
        researchUnitId: value.researchUnitId === 'all' ? null : value.researchUnitId
      },
      queryParamsHandling: 'merge'
    });
  }

  clearSearch(): void {
    this.searchForm.reset({
      query: '',
      mode: 'BALANCED',
      researchUnitId: 'all'
    });
    this.selectedMode.set('BALANCED');
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: null,
        mode: null,
        researchUnitId: null
      },
      queryParamsHandling: 'merge'
    });
  }

  useExample(example: string): void {
    this.searchForm.controls.query.setValue(example);
    this.submitSearch();
  }

  toggleFilters(): void {
    this.filtersOpen.update((open) => !open);
  }

  selectMode(mode: ExpertFinderMode): void {
    this.searchForm.controls.mode.setValue(mode);
    this.selectedMode.set(mode);
  }

  expertAssistantSearchRequest(): PortalContextAssistantSearchRequest {
    const value = this.searchForm.getRawValue();
    return {
      query: value.query.trim() || null,
      mode: value.mode,
      yearFrom: null,
      yearTo: null,
      type: null,
      status: null,
      researchUnitId: this.toNumber(value.researchUnitId),
      researcherId: null,
      topic: null
    };
  }

  openEvidence(expert: ExpertFinderResult): void {
    this.dialog.open<ExpertFinderEvidenceDialogComponent, ExpertFinderEvidenceDialogData>(
      ExpertFinderEvidenceDialogComponent,
      {
        width: 'min(760px, calc(100vw - 32px))',
        maxWidth: 'calc(100vw - 32px)',
        data: {
          expert,
          returnQueryParams: this.navigationContext.returnQueryParams('Volver a la guía de expertos')
        }
      }
    );
  }

  affiliationLabel(expert: ExpertFinderResult): string {
    return expert.researcher.primaryResearchUnitName || 'Afiliación institucional pública';
  }

  affinityLabel(score: number): string {
    const normalized = this.normalizeScore(score);
    if (normalized >= 0.65) {
      return 'Alta';
    }
    if (normalized >= 0.35) {
      return 'Media';
    }
    return `${Math.round(normalized * 100)}%`;
  }

  confidenceLabel(value: string): string {
    if (value === 'HIGH') {
      return 'Confianza alta';
    }
    if (value === 'MEDIUM') {
      return 'Confianza media';
    }
    return 'Evidencia limitada';
  }

  evidenceLabel(expert: ExpertFinderResult): string {
    const publicationCount = expert.representativePublications.length;
    const eventCount = expert.relevantEventParticipations.length;
    const parts = [];
    if (publicationCount > 0) {
      parts.push(publicationCount === 1 ? '1 publicación' : `${publicationCount} publicaciones`);
    }
    if (eventCount > 0) {
      parts.push(eventCount === 1 ? '1 evento' : `${eventCount} eventos`);
    }
    return parts.length > 0 ? parts.join(' · ') : 'Evidencia limitada';
  }

  visibleTopics(expert: ExpertFinderResult): string[] {
    return expert.matchedTopics.slice(0, 3);
  }

  hiddenTopicCount(expert: ExpertFinderResult): number {
    return Math.max(expert.matchedTopics.length - 3, 0);
  }

  publicExplanation(expert: ExpertFinderResult): string {
    const name = expert.researcher.displayName || expert.researcher.fullName;
    if (expert.explanation.toLowerCase().includes('baja confianza')) {
      return `${name} aparece con evidencia pública limitada para esta búsqueda. Revisa las evidencias antes de contactar.`;
    }
    return expert.explanation
      .replace('destaca por evidencia trazable:', 'aparece por evidencia pública relacionada:')
      .replace('Solo se ha usado evidencia validada.', 'Solo se ha usado evidencia pública validada.');
  }

  private loadDemoQueries(): void {
    this.demoQuerySuggestions.loadSuggestions({
      context: 'EXPERT_FINDER',
      fallbackQueries: FALLBACK_EXPERT_QUERY_EXAMPLES
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.exampleQueries.set(result.queries);
        this.exampleQueriesDynamic.set(result.dynamic);
      });
  }

  private loadResearchUnits(): void {
    this.portalApi.researchUnits({ page: 0, size: 100 })
      .pipe(
        catchError(() => of({
          content: [] as PortalResearchUnitSummary[],
          page: 0,
          size: 100,
          totalElements: 0,
          totalPages: 0,
          last: true,
          visibilityScope: 'PUBLIC_VALIDATED',
          validationFilterApplied: true
        })),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((response) => this.researchUnits.set(response.content.filter((unit) => unit.active)));
  }

  private runSearch(query: string, mode: ExpertFinderMode, researchUnitId: number | null): void {
    const modeConfig = MODE_CONFIGURATION[mode];
    this.loading.set(true);
    this.hasSearched.set(true);
    this.errorMessage.set('');
    this.results.set([]);
    this.responseWarnings.set([]);

    this.expertFinderApi.search({
      query,
      limit: modeConfig.limit,
      mode,
      filters: {
        researchUnitId,
        topic: null,
        onlyValidated: true
      }
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.applyResponse(response),
        error: () => {
          this.results.set([]);
          this.responseWarnings.set([]);
          this.loading.set(false);
          this.errorMessage.set('No se ha podido cargar la guía de expertos.');
        }
      });
  }

  private applyResponse(response: ExpertFinderSearchResponse): void {
    this.results.set(response.results);
    this.responseWarnings.set(response.warnings);
    this.loading.set(false);
  }

  private resetResults(): void {
    this.loading.set(false);
    this.hasSearched.set(false);
    this.errorMessage.set('');
    this.results.set([]);
    this.responseWarnings.set([]);
  }

  private publicWarning(warning: string): string | null {
    const normalized = warning.toLowerCase();
    if (normalized.includes('evidencia validada')) {
      return null;
    }
    if (normalized.includes('embeddings') || normalized.includes('proveedor') || normalized.includes('dimension')) {
      return 'La búsqueda se ha completado con coincidencia textual cuando no había señales semánticas disponibles.';
    }
    if (normalized.includes('debil')) {
      return 'Algunas coincidencias tienen evidencia limitada para esta consulta.';
    }
    return warning;
  }

  private normalizeScore(score: number): number {
    return score > 1 ? Math.min(score / 100, 1) : Math.max(score, 0);
  }

  private toMode(value: string | null): ExpertFinderMode {
    return value === 'STRICT' || value === 'BROAD' ? value : 'BALANCED';
  }

  private toNumber(value: string): number | null {
    const parsed = Number(value);
    return Number.isFinite(parsed) && value !== '' && value !== 'all' ? parsed : null;
  }
}
