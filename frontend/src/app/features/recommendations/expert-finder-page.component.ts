import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

import { Publication, PublicationSemanticSearchResult, ResearchUnit, Researcher } from '../../core/api/api-models';
import { PortalDemoQuerySuggestionsService } from '../../core/api/portal-demo-query-suggestions.service';
import { PublicationsApiService } from '../../core/api/publications-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { ResearchUnitsApiService } from '../../core/api/research-units-api.service';
import { ResearchersApiService } from '../../core/api/researchers-api.service';
import { DemoQueryChipsComponent } from '../../shared/components/demo-query-chips.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';

type ExpertFinderMode = 'STRICT' | 'BALANCED' | 'BROAD';
type ActiveResearcherFilter = 'active' | 'all';

interface ExpertEvidencePublication {
  id: number;
  title: string;
  year: number | null;
  similarityScore: number;
  retrievalReason: string;
  topics: string[];
}

interface ExpertMatchCard {
  researcher: Researcher;
  score: number;
  scorePercent: number;
  confidenceLabel: string;
  explanation: string;
  topics: string[];
  evidencePublications: ExpertEvidencePublication[];
}

interface ExpertAggregation {
  researcher: Researcher;
  evidencePublications: ExpertEvidencePublication[];
}

interface ModeConfiguration {
  label: string;
  helper: string;
  minSimilarity: number;
  limit: number;
}

const MODE_CONFIGURATION: Record<ExpertFinderMode, ModeConfiguration> = {
  STRICT: {
    label: 'Estricto',
    helper: 'Prioriza coincidencias directas y publicaciones muy cercanas a la consulta.',
    minSimilarity: 0.55,
    limit: 8
  },
  BALANCED: {
    label: 'Equilibrado',
    helper: 'Combina precisiÃ³n y cobertura para devolver perfiles pÃºblicos consistentes.',
    minSimilarity: 0.4,
    limit: 12
  },
  BROAD: {
    label: 'Amplio',
    helper: 'Abre la bÃºsqueda para explorar afinidades temÃ¡ticas mÃ¡s abiertas.',
    minSimilarity: 0.25,
    limit: 16
  }
};

const FALLBACK_EXPERT_QUERY_EXAMPLES = [
  'IA local en hospitales',
  'salud pÃºblica y clima urbano',
  'grafos de conocimiento y genÃ³mica',
  'biodiversidad y corredores ecolÃ³gicos',
  'colaboraciÃ³n cientÃ­fica en salud digital'
];

@Component({
  selector: 'rip-expert-finder-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    DemoQueryChipsComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    TagChipComponent
  ],
  template: `
    <section class="page expert-finder-page">
      <rip-page-header
        title="GuÃ­a de expertos"
        [subtitle]="headerSubtitle()"
        eyebrow="Portal pÃºblico"
      />

      <section class="surface-intro">
        <p class="section-kicker">BÃºsqueda guiada</p>
        <div class="intro-grid">
          <div>
            <h2>Encuentra perfiles pÃºblicos a partir de una pregunta, una lÃ­nea temÃ¡tica o un problema concreto.</h2>
            <p>
              La guÃ­a combina bÃºsqueda semÃ¡ntica y seÃ±ales pÃºblicas del perfil del investigador para sugerir
              expertos con evidencias concretas.
            </p>
          </div>
          <div class="intro-pills">
            <span>{{ results().length }} expertos</span>
            <span>{{ searchedPublicationsCount() }} publicaciones de evidencia</span>
          </div>
        </div>
      </section>

      <mat-card appearance="outlined" class="search-card">
        <mat-card-content>
          <form class="search-form" [formGroup]="searchForm" (ngSubmit)="submitSearch()">
            <mat-form-field appearance="outline" class="query-field">
              <mat-label>Buscar expertos</mat-label>
              <input
                matInput
                formControlName="query"
                placeholder="Ej. salud pÃºblica y clima urbano"
              >
              <mat-hint>Describe un tema, reto o contexto de investigaciÃ³n.</mat-hint>
            </mat-form-field>

            <div class="mode-block">
              <span class="control-label">Modo</span>
              <mat-button-toggle-group formControlName="mode" aria-label="Modo de bÃºsqueda">
                <mat-button-toggle value="STRICT">Estricto</mat-button-toggle>
                <mat-button-toggle value="BALANCED">Equilibrado</mat-button-toggle>
                <mat-button-toggle value="BROAD">Amplio</mat-button-toggle>
              </mat-button-toggle-group>
              <p class="mode-helper">{{ selectedModeConfig().helper }}</p>
            </div>

            <div class="filter-grid">
              @if (researchUnits().length > 0) {
                <mat-form-field appearance="outline">
                  <mat-label>Unidad</mat-label>
                  <mat-select formControlName="researchUnitId">
                    <mat-option value="all">Todas las unidades</mat-option>
                    @for (unit of researchUnits(); track unit.id) {
                      <mat-option [value]="unit.id.toString()">{{ unit.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              }

              <mat-form-field appearance="outline">
                <mat-label>Perfiles</mat-label>
                <mat-select formControlName="active">
                  <mat-option value="active">Solo activos</mat-option>
                  <mat-option value="all">Todos</mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <div class="example-section">
              <rip-demo-query-chips
                title="Consultas sugeridas"
                [caption]="exampleQueryCaption()"
                [queries]="exampleQueries()"
                [disabled]="loading()"
                (querySelected)="useExample($event)"
              />
            </div>

            <div class="actions">
              <button mat-button type="button" (click)="clearSearch()">Limpiar</button>
              <button mat-flat-button color="primary" type="submit" [disabled]="searchForm.invalid || loading()">
                Buscar
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      @if (loading()) {
        <rip-loading-state message="Buscando expertos" />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else if (!hasSearched()) {
        <rip-empty-state
          title="Empieza con una consulta"
          message="Prueba una pregunta temÃ¡tica o usa uno de los ejemplos para encontrar expertos con evidencias pÃºblicas."
        />
      } @else if (results().length === 0) {
        <rip-empty-state
          title="Sin coincidencias visibles"
          message="No se encontraron expertos pÃºblicos con esta combinaciÃ³n de consulta, modo y filtros."
        />
      } @else {
        <section class="results-summary">
          <div>
            <p class="section-kicker">Resultados</p>
            <h3>{{ results().length }} expertos sugeridos en modo {{ selectedModeConfig().label.toLowerCase() }}</h3>
          </div>
          <p>
            Las tarjetas muestran afinidad estimada a partir de publicaciones, temas pÃºblicos y afiliaciÃ³n
            visible.
          </p>
        </section>

        <div class="results-grid">
          @for (expert of results(); track expert.researcher.id) {
            <article class="expert-card">
              <div class="card-header">
                <div class="identity-block">
                  <strong>{{ expert.researcher.displayName || expert.researcher.fullName }}</strong>
                  <p>{{ affiliationLabel(expert.researcher) }}</p>
                </div>
                <div class="score-badge">
                  <strong>{{ expert.scorePercent }}%</strong>
                  <span>Confianza {{ expert.confidenceLabel.toLowerCase() }}</span>
                </div>
              </div>

              <div class="meta-row">
                <span>{{ selectedModeConfig().label }}</span>
                <span>{{ evidenceLabel(expert.evidencePublications.length) }}</span>
                @if (expert.researcher.orcid) {
                  <span>ORCID</span>
                }
              </div>

              <section class="detail-block">
                <h4>Temas pÃºblicos</h4>
                <div class="chip-list">
                  @for (topic of expert.topics; track topic) {
                    <rip-tag-chip [label]="topic" />
                  } @empty {
                    <span class="muted">Sin temas destacados</span>
                  }
                </div>
              </section>

              <section class="detail-block">
                <h4>Publicaciones de evidencia</h4>
                <div class="evidence-list">
                  @for (publication of expert.evidencePublications; track publication.id) {
                    <a class="evidence-item" [routerLink]="['/publications', publication.id]" [queryParams]="navigationContext.returnQueryParams('Volver a la guÃ­a de expertos')">
                      <strong>{{ publication.title }}</strong>
                      <span>{{ publication.year || 's. f.' }} Â· {{ similarityLabel(publication.similarityScore) }}</span>
                    </a>
                  }
                </div>
              </section>

              <section class="detail-block">
                <h4>ExplicaciÃ³n</h4>
                <p class="explanation">{{ expert.explanation }}</p>
              </section>

              <div class="card-actions">
                <a mat-stroked-button [routerLink]="['/portal/investigadores', expert.researcher.id]" [queryParams]="navigationContext.returnQueryParams('Volver a la guÃ­a de expertos')">
                  Ver perfil pÃºblico
                </a>
              </div>
            </article>
          }
        </div>
      }
    </section>
  `,
  styles: [`
    .expert-finder-page {
      display: grid;
      gap: 28px;
    }

    .surface-intro,
    .search-card,
    .results-summary,
    .expert-card {
      border-radius: 14px !important;
    }

    .intro-grid {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 18px;
      align-items: start;
    }

    .intro-grid h2,
    .results-summary h3 {
      margin: 0;
      color: #132133;
      line-height: 1.18;
    }

    .intro-grid h2 {
      font-size: clamp(1.45rem, 2.4vw, 1.9rem);
    }

    .intro-grid p,
    .results-summary p {
      margin: 0;
      color: #637486;
      line-height: 1.6;
    }

    .intro-pills {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      justify-content: flex-end;
    }

    .intro-pills span,
    .meta-row span {
      padding: 8px 12px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.84);
      border: 1px solid #d8e4eb;
      color: #365369;
      font-size: 0.84rem;
      font-weight: 760;
      white-space: nowrap;
    }

    .search-card mat-card-content {
      display: grid;
      gap: 20px;
    }

    .search-form {
      display: grid;
      gap: 18px;
    }

    .query-field {
      width: 100%;
    }

    .mode-block,
    .example-section {
      display: grid;
      gap: 10px;
    }

    .control-label {
      color: #627587;
      font-size: 0.8rem;
      font-weight: 780;
      text-transform: uppercase;
    }

    .mode-helper {
      margin: 0;
      color: #667487;
      font-size: 0.9rem;
      line-height: 1.5;
    }

    .filter-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 12px;
    }

    .prompt-chips,
    .chip-list,
    .meta-row {
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
    .evidence-item:hover,
    .expert-card:hover {
      border-color: #9dc1d1;
      background: #f6fbfd;
      transform: translateY(-1px);
    }

    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
      flex-wrap: wrap;
    }

    .results-summary {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(280px, 0.8fr);
      gap: 18px;
      align-items: end;
      padding: 20px 22px;
      border: 1px solid #dce6ed;
      border-radius: 14px;
      background: #ffffff;
    }

    .results-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 18px;
    }

    .expert-card {
      display: grid;
      gap: 16px;
      padding: 22px;
      border: 1px solid #dfe7ed;
      border-radius: 12px;
      background: #ffffff;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .expert-card:hover {
      box-shadow: 0 16px 32px rgba(20, 32, 51, 0.08);
    }

    .card-header {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 12px;
      align-items: start;
    }

    .identity-block {
      display: grid;
      gap: 6px;
      min-width: 0;
    }

    .identity-block strong {
      color: #142033;
      font-size: 1.08rem;
      line-height: 1.3;
      overflow-wrap: anywhere;
    }

    .identity-block p {
      margin: 0;
      color: #667487;
      line-height: 1.5;
    }

    .score-badge {
      display: grid;
      gap: 4px;
      min-width: 118px;
      padding: 12px 14px;
      border: 1px solid #dfe7ed;
      border-radius: 10px;
      background: #f8fafb;
      text-align: right;
    }

    .score-badge strong {
      color: #174d67;
      font-size: 1.2rem;
      line-height: 1;
    }

    .score-badge span {
      color: #466174;
      font-size: 0.8rem;
      font-weight: 700;
    }

    .detail-block {
      display: grid;
      gap: 10px;
    }

    .detail-block h4 {
      margin: 0;
      color: #233447;
      font-size: 0.9rem;
      font-weight: 780;
    }

    .evidence-list {
      display: grid;
      gap: 10px;
    }

    .evidence-item {
      display: grid;
      gap: 4px;
      padding: 12px 14px;
      border: 1px solid #e3eaf0;
      border-radius: 10px;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      transition: border-color 140ms ease, background-color 140ms ease, transform 140ms ease;
    }

    .evidence-item strong {
      color: #142033;
      font-size: 0.94rem;
      line-height: 1.35;
    }

    .evidence-item span,
    .explanation,
    .muted {
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.55;
    }

    .explanation {
      margin: 0;
    }

    .card-actions {
      display: flex;
      justify-content: flex-start;
    }

    @media (max-width: 760px) {
      .intro-grid,
      .results-summary,
      .card-header {
        grid-template-columns: 1fr;
      }

      .intro-pills {
        justify-content: flex-start;
      }

      .score-badge {
        text-align: left;
      }

      .actions {
        justify-content: stretch;
      }
    }
  `]
})
export class ExpertFinderPageComponent implements OnInit {
  private readonly publicationsApi = inject(PublicationsApiService);
  private readonly researchersApi = inject(ResearchersApiService);
  private readonly researchUnitsApi = inject(ResearchUnitsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly demoQuerySuggestions = inject(PortalDemoQuerySuggestionsService);
  readonly navigationContext = inject(NavigationContextService);

  readonly results = signal<ExpertMatchCard[]>([]);
  readonly researchUnits = signal<ResearchUnit[]>([]);
  readonly exampleQueries = signal<string[]>(FALLBACK_EXPERT_QUERY_EXAMPLES);
  readonly exampleQueriesDynamic = signal(false);
  readonly loading = signal(false);
  readonly hasSearched = signal(false);
  readonly errorMessage = signal('');
  readonly searchedPublicationsCount = signal(0);
  readonly selectedMode = signal<ExpertFinderMode>('BALANCED');
  readonly selectedModeConfig = computed(() => MODE_CONFIGURATION[this.selectedMode()]);
  readonly headerSubtitle = computed(() => this.hasSearched()
    ? `${this.results().length} perfiles sugeridos con evidencia pÃºblica`
    : 'Localiza expertos a partir de publicaciones, temas y afinidad semÃ¡ntica visibles en el portal.');
  readonly exampleQueryCaption = computed(() => this.exampleQueriesDynamic() ? 'Inspiradas en los datos del portal' : '');

  readonly searchForm = new FormGroup({
    query: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3)] }),
    mode: new FormControl<ExpertFinderMode>('BALANCED', { nonNullable: true }),
    researchUnitId: new FormControl('all', { nonNullable: true }),
    active: new FormControl<ActiveResearcherFilter>('active', { nonNullable: true })
  });

  ngOnInit(): void {
    this.loadDemoQueries();

    this.researchUnitsApi.list()
      .pipe(
        catchError(() => of([] as ResearchUnit[])),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((units) => this.researchUnits.set(units.filter((unit) => unit.active)));

    this.searchForm.controls.mode.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((mode) => this.selectedMode.set(mode));

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const query = params.get('q') ?? '';
        const mode = this.toMode(params.get('mode'));
        const researchUnitId = params.get('researchUnitId') ?? 'all';
        const active = this.toActiveFilter(params.get('active'));

        this.selectedMode.set(mode);
        this.searchForm.patchValue({ query, mode, researchUnitId, active }, { emitEvent: false });

        if (query.trim().length < 3) {
          this.loading.set(false);
          this.hasSearched.set(false);
          this.errorMessage.set('');
          this.results.set([]);
          this.searchedPublicationsCount.set(0);
          return;
        }

        this.runSearch(query.trim(), mode, this.toNumber(researchUnitId), active);
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
        researchUnitId: value.researchUnitId === 'all' ? null : value.researchUnitId,
        active: value.active === 'active' ? null : value.active
      },
      queryParamsHandling: 'merge'
    });
  }

  clearSearch(): void {
    this.searchForm.reset({
      query: '',
      mode: 'BALANCED',
      researchUnitId: 'all',
      active: 'active'
    });
    this.selectedMode.set('BALANCED');
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: null,
        mode: null,
        researchUnitId: null,
        active: null
      },
      queryParamsHandling: 'merge'
    });
  }

  useExample(example: string): void {
    this.searchForm.controls.query.setValue(example);
    this.submitSearch();
  }

  affiliationLabel(researcher: Researcher): string {
    return researcher.primaryAffiliation?.researchUnitName
      ?? researcher.currentAffiliations[0]?.researchUnitName
      ?? 'AfiliaciÃ³n pÃºblica no disponible';
  }

  evidenceLabel(count: number): string {
    return count === 1 ? '1 publicaciÃ³n de evidencia' : `${count} publicaciones de evidencia`;
  }

  similarityLabel(score: number): string {
    return `${Math.round(score * 100)}% de afinidad`;
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

  private runSearch(
    query: string,
    mode: ExpertFinderMode,
    researchUnitId: number | undefined,
    activeFilter: ActiveResearcherFilter
  ): void {
    const modeConfig = MODE_CONFIGURATION[mode];
    this.loading.set(true);
    this.hasSearched.set(true);
    this.errorMessage.set('');
    this.results.set([]);
    this.searchedPublicationsCount.set(0);

    this.publicationsApi.semanticSearch({
      query,
      limit: modeConfig.limit,
      minSimilarity: modeConfig.minSimilarity,
      includeNonValidated: false
    })
      .pipe(
        switchMap((semanticResults) => this.loadSemanticContext(semanticResults)),
        map(({ semanticResults, publications, researchers }) => {
          this.searchedPublicationsCount.set(semanticResults.length);
          return this.buildExpertCards(query, mode, researchUnitId, activeFilter, semanticResults, publications, researchers);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (results) => {
          this.results.set(results);
          this.loading.set(false);
        },
        error: () => {
          this.results.set([]);
          this.loading.set(false);
          this.errorMessage.set('No se pudo cargar la guÃ­a de expertos en este momento.');
        }
      });
  }

  private loadSemanticContext(semanticResults: PublicationSemanticSearchResult[]) {
    if (semanticResults.length === 0) {
      return of({
        semanticResults,
        publications: [] as Publication[],
        researchers: [] as Researcher[]
      });
    }

    return forkJoin(
      semanticResults.map((publication) =>
        this.publicationsApi.get(publication.id).pipe(
          catchError(() => of(null))
        )
      )
    ).pipe(
      switchMap((publicationDetails) => {
        const publications = publicationDetails.filter((detail): detail is Publication => detail !== null);
        const researcherIds = Array.from(new Set(
          publications.flatMap((publication) =>
            publication.authors
              .map((author) => author.researcherId)
              .filter((researcherId): researcherId is number => researcherId !== null)
          )
        ));

        if (researcherIds.length === 0) {
          return of({
            semanticResults,
            publications,
            researchers: [] as Researcher[]
          });
        }

        return forkJoin(
          researcherIds.map((researcherId) =>
            this.researchersApi.get(researcherId).pipe(
              catchError(() => of(null))
            )
          )
        ).pipe(
          map((researcherDetails) => ({
            semanticResults,
            publications,
            researchers: researcherDetails.filter((detail): detail is Researcher => detail !== null)
          }))
        );
      })
    );
  }

  private buildExpertCards(
    query: string,
    mode: ExpertFinderMode,
    researchUnitId: number | undefined,
    activeFilter: ActiveResearcherFilter,
    semanticResults: PublicationSemanticSearchResult[],
    publications: Publication[],
    researchers: Researcher[]
  ): ExpertMatchCard[] {
    if (semanticResults.length === 0 || publications.length === 0 || researchers.length === 0) {
      return [];
    }

    const publicationMap = new Map<number, Publication>(
      publications.map((publication) => [publication.id, publication])
    );
    const researcherMap = new Map<number, Researcher>(
      researchers.map((researcher) => [researcher.id, researcher])
    );
    const aggregation = new Map<number, ExpertAggregation>();

    for (const result of semanticResults) {
      const publication = publicationMap.get(result.id);
      if (!publication) {
        continue;
      }

      const evidenceTopics = publication.topics.map((topic) => topic.name);
      const evidencePublication: ExpertEvidencePublication = {
        id: publication.id,
        title: publication.title,
        year: publication.year,
        similarityScore: result.similarityScore,
        retrievalReason: result.retrievalReason,
        topics: evidenceTopics
      };

      for (const author of publication.authors) {
        if (author.researcherId === null) {
          continue;
        }
        const researcher = researcherMap.get(author.researcherId);
        if (!researcher) {
          continue;
        }

        const existing = aggregation.get(researcher.id);
        if (existing) {
          existing.evidencePublications.push(evidencePublication);
        } else {
          aggregation.set(researcher.id, {
            researcher,
            evidencePublications: [evidencePublication]
          });
        }
      }
    }

    const queryTokens = this.tokenize(query);

    return Array.from(aggregation.values())
      .filter(({ researcher }) => this.matchesFilters(researcher, researchUnitId, activeFilter))
      .map(({ researcher, evidencePublications }) => {
        const sortedEvidence = [...evidencePublications]
          .sort((left, right) => right.similarityScore - left.similarityScore)
          .slice(0, 3);
        const topics = this.collectTopics(researcher, evidencePublications, queryTokens);
        const score = this.calculateScore(researcher, evidencePublications, queryTokens);

        return {
          researcher,
          score,
          scorePercent: Math.round(score * 100),
          confidenceLabel: this.confidenceLabel(score, mode),
          explanation: this.buildExplanation(researcher, mode, evidencePublications, topics),
          topics,
          evidencePublications: sortedEvidence
        };
      })
      .sort((left, right) => {
        if (right.score !== left.score) {
          return right.score - left.score;
        }
        return right.evidencePublications.length - left.evidencePublications.length;
      });
  }

  private matchesFilters(
    researcher: Researcher,
    researchUnitId: number | undefined,
    activeFilter: ActiveResearcherFilter
  ): boolean {
    if (activeFilter === 'active' && !researcher.active) {
      return false;
    }

    if (researchUnitId === undefined) {
      return true;
    }

    return researcher.currentAffiliations.some((affiliation) => affiliation.researchUnitId === researchUnitId)
      || researcher.primaryAffiliation?.researchUnitId === researchUnitId;
  }

  private collectTopics(
    researcher: Researcher,
    evidencePublications: ExpertEvidencePublication[],
    queryTokens: string[]
  ): string[] {
    const topicCounts = new Map<string, number>();
    const profileTopics = researcher.topics.map((topic) => topic.name);

    for (const topic of profileTopics) {
      topicCounts.set(topic, (topicCounts.get(topic) ?? 0) + 2);
    }

    for (const publication of evidencePublications) {
      for (const topic of publication.topics) {
        topicCounts.set(topic, (topicCounts.get(topic) ?? 0) + 1);
      }
    }

    return Array.from(topicCounts.entries())
      .sort((left, right) => {
        const leftMatches = this.topicMatchScore(left[0], queryTokens);
        const rightMatches = this.topicMatchScore(right[0], queryTokens);
        if (rightMatches !== leftMatches) {
          return rightMatches - leftMatches;
        }
        if (right[1] !== left[1]) {
          return right[1] - left[1];
        }
        return left[0].localeCompare(right[0], 'es');
      })
      .map(([topic]) => topic)
      .slice(0, 5);
  }

  private calculateScore(
    researcher: Researcher,
    evidencePublications: ExpertEvidencePublication[],
    queryTokens: string[]
  ): number {
    const averageSimilarity = evidencePublications.reduce((total, publication) => total + publication.similarityScore, 0)
      / evidencePublications.length;
    const bestSimilarity = Math.max(...evidencePublications.map((publication) => publication.similarityScore));
    const evidenceStrength = Math.min(evidencePublications.length, 4) / 4;
    const topicStrength = this.topicStrength(researcher, evidencePublications, queryTokens);

    return Math.min(0.99, averageSimilarity * 0.62 + bestSimilarity * 0.18 + evidenceStrength * 0.12 + topicStrength * 0.08);
  }

  private topicStrength(
    researcher: Researcher,
    evidencePublications: ExpertEvidencePublication[],
    queryTokens: string[]
  ): number {
    const topics = [
      ...researcher.topics.map((topic) => topic.name),
      ...evidencePublications.flatMap((publication) => publication.topics)
    ];
    const matches = topics.filter((topic) => this.topicMatchScore(topic, queryTokens) > 0).length;
    return Math.min(matches, 4) / 4;
  }

  private topicMatchScore(topic: string, queryTokens: string[]): number {
    const topicTokens = this.tokenize(topic);
    if (topicTokens.length === 0 || queryTokens.length === 0) {
      return 0;
    }
    return queryTokens.filter((token) => topicTokens.includes(token)).length;
  }

  private buildExplanation(
    researcher: Researcher,
    mode: ExpertFinderMode,
    evidencePublications: ExpertEvidencePublication[],
    topics: string[]
  ): string {
    const affiliation = this.affiliationLabel(researcher);
    const topicText = topics.length > 0
      ? ` Temas pÃºblicos mÃ¡s cercanos: ${topics.slice(0, 3).join(', ')}.`
      : '';

    return `Modo ${MODE_CONFIGURATION[mode].label}: ${evidencePublications.length} ${evidencePublications.length === 1 ? 'publicaciÃ³n conecta' : 'publicaciones conectan'} la consulta con ${affiliation}.${topicText}`;
  }

  private confidenceLabel(score: number, mode: ExpertFinderMode): string {
    if (score >= 0.8) {
      return 'alta';
    }
    if (score >= 0.65) {
      return 'media';
    }
    return mode === 'BROAD' ? 'exploratoria' : 'inicial';
  }

  private tokenize(value: string): string[] {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .split(/[^a-z0-9]+/)
      .filter((token) => token.length >= 3);
  }

  private toMode(value: string | null): ExpertFinderMode {
    return value === 'STRICT' || value === 'BROAD' ? value : 'BALANCED';
  }

  private toActiveFilter(value: string | null): ActiveResearcherFilter {
    return value === 'all' ? 'all' : 'active';
  }

  private toNumber(value: string): number | undefined {
    const parsed = Number(value);
    return Number.isFinite(parsed) && value !== '' && value !== 'all' ? parsed : undefined;
  }
}

