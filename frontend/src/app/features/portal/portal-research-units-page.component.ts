import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import {
  PortalPageResponse,
  PortalResearchUnitDetail,
  PortalResearchUnitSummary,
  ResearchUnitType
} from '../../core/api/api-models';
import { PortalApiService } from '../../core/api/portal-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { researchUnitTypeLabel } from '../../shared/utils/display-labels';

@Component({
  selector: 'rip-portal-research-units-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    TagChipComponent
  ],
  template: `
    <section class="page portal-list-page">
      <rip-page-header
        title="Unidades"
        eyebrow="Portal público"
        subtitle="Explora unidades, institutos, departamentos y grupos con información pública validada."
      />

      <section class="portal-list-intro">
        <p class="section-kicker">Directorio institucional</p>
        <h2>Unidades propias de la institución, organizadas para una exploración pública sencilla.</h2>
        <p>
          El portal muestra unidades internas visibles y evita mezclar organizaciones externas con el directorio
          institucional principal.
        </p>
      </section>

      <form class="portal-search-strip" (ngSubmit)="applyFilters()">
        <mat-form-field appearance="outline" class="search-field">
          <mat-label>Buscar unidades</mat-label>
          <input matInput [formControl]="searchControl" placeholder="Buscar unidades, institutos, departamentos o grupos...">
        </mat-form-field>

        <div class="search-actions">
          <button class="filters-toggle" mat-stroked-button type="button" (click)="toggleFilters()">Filtros</button>
          <button mat-flat-button color="primary" type="submit">Buscar</button>
        </div>
      </form>

      <section class="portal-search-layout">
        <aside class="filter-panel" [class.open]="filtersOpen()">
          <div class="filter-panel-heading">
            <h3>Filtros</h3>
            @if (hasActiveFilters()) {
              <button mat-button type="button" (click)="clearFilters()">Limpiar filtros</button>
            }
          </div>

          <div class="filter-group">
            <span>Tipo de unidad</span>
            <button
              type="button"
              class="filter-chip"
              [class.active]="selectedType() === 'all'"
              (click)="selectType('all')"
            >
              Todas
            </button>
            @for (type of typeOptions(); track type) {
              <button
                type="button"
                class="filter-chip"
                [class.active]="selectedType() === type"
                (click)="selectType(type)"
              >
                {{ typeLabel(type) }}
              </button>
            }
          </div>
        </aside>

        <section class="results-panel">
          <div class="results-summary">
            <div>
              <p class="section-kicker">Resultados</p>
              <strong>{{ result().totalElements }} {{ result().totalElements === 1 ? 'unidad encontrada' : 'unidades encontradas' }}</strong>
            </div>
            @if (hasActiveFilters()) {
              <span>Filtros aplicados</span>
            }
          </div>

          @if (loading()) {
            <rip-loading-state message="Cargando unidades..." />
          } @else if (errorMessage()) {
            <rip-error-state [message]="errorMessage()" />
          } @else if (result().content.length === 0) {
            <rip-empty-state title="Sin resultados" message="No se han encontrado resultados con esos filtros." />
          } @else {
            <div class="result-grid">
              @for (unit of result().content; track unit.id) {
                <a class="portal-result-card" [routerLink]="['/portal/unidades', unit.id]" [queryParams]="navigationContext.returnQueryParams('Volver a unidades')">
                  <div class="card-top">
                    <span class="type-badge">{{ typeLabel(unit.type) }}</span>
                    @if (hierarchyLabel(unit)) {
                      <span class="subtle-meta">{{ hierarchyLabel(unit) }}</span>
                    }
                  </div>

                  <div class="card-main">
                    <strong>{{ unit.name }}</strong>
                    <p>{{ unitDescription(unit) }}</p>
                  </div>

                  <div class="topic-row">
                    @for (topic of visibleTopics(unit.id); track topic) {
                      <rip-tag-chip [label]="topic" />
                    }
                    @if (extraTopics(unit.id) > 0) {
                      <span class="extra-chip">+{{ extraTopics(unit.id) }}</span>
                    }
                    @if (visibleTopics(unit.id).length === 0) {
                      <span class="muted">Temas disponibles al abrir la ficha</span>
                    }
                  </div>

                  <div class="card-footer">
                    <span>{{ compactCounts(unit.id) }}</span>
                    <strong>Ver unidad</strong>
                  </div>
                </a>
              }
            </div>
          }
        </section>
      </section>

      <div class="pagination">
        <button mat-button type="button" [disabled]="currentPage() === 0" (click)="goToPage(currentPage() - 1)">Anterior</button>
        <span>Página {{ currentPage() + 1 }} de {{ pageCount() }}</span>
        <button mat-button type="button" [disabled]="result().last" (click)="goToPage(currentPage() + 1)">Siguiente</button>
      </div>
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

    .portal-list-page {
      gap: 26px;
      min-width: 0;
      overflow-wrap: anywhere;
    }

    .portal-list-intro {
      display: grid;
      gap: 10px;
      max-width: 860px;
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
      padding: 18px;
      border: 1px solid #dce7ed;
      border-radius: 18px;
      background: #ffffff;
    }

    .search-field {
      width: 100%;
    }

    .search-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      flex-wrap: wrap;
      gap: 10px;
    }

    :host ::ng-deep .portal-search-strip .mat-mdc-form-field-subscript-wrapper {
      display: none;
    }

    .filters-toggle {
      display: none;
    }

    .portal-search-layout {
      display: grid;
      grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
      gap: 22px;
      align-items: start;
      min-width: 0;
    }

    .filter-panel,
    .portal-result-card,
    .results-summary {
      border: 1px solid #dce7ed;
      border-radius: 18px;
      background: #ffffff;
    }

    .filter-panel {
      position: sticky;
      top: 92px;
      display: grid;
      gap: 18px;
      padding: 18px;
    }

    .filter-panel-heading {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
    }

    .filter-panel h3 {
      margin: 0;
      color: #102033;
      font-size: 1rem;
    }

    .filter-group {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .filter-group > span {
      flex: 1 0 100%;
      color: #526879;
      font-size: 0.84rem;
      font-weight: 760;
    }

    .filter-chip {
      padding: 8px 11px;
      border: 1px solid #d9e6ec;
      border-radius: 999px;
      background: #ffffff;
      color: #365369;
      font: inherit;
      font-size: 0.88rem;
      cursor: pointer;
      transition: background 140ms ease, border-color 140ms ease, color 140ms ease;
    }

    .filter-chip.active,
    .filter-chip:hover {
      border-color: #8eb4c8;
      background: #eef7fb;
      color: #173d55;
    }

    .results-panel {
      display: grid;
      gap: 18px;
      min-width: 0;
    }

    .results-summary {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
      padding: 16px 18px;
    }

    .results-summary strong {
      display: block;
      margin-top: 2px;
      color: #102033;
      font-size: 1.05rem;
    }

    .results-summary span {
      color: #617283;
      font-size: 0.88rem;
      font-weight: 720;
    }

    .result-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 18px;
      min-width: 0;
    }

    .portal-result-card {
      display: grid;
      gap: 16px;
      padding: 22px;
      color: inherit;
      text-decoration: none;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .portal-result-card:hover,
    .portal-result-card:focus-visible {
      border-color: #a6c3d1;
      box-shadow: 0 16px 30px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
      outline: none;
    }

    .card-top,
    .card-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }

    .type-badge {
      display: inline-flex;
      align-items: center;
      padding: 5px 10px;
      border-radius: 999px;
      background: #eef6f3;
      color: #28624a;
      font-size: 0.77rem;
      font-weight: 780;
    }

    .subtle-meta {
      color: #617283;
      font-size: 0.82rem;
    }

    .card-main strong {
      display: block;
      color: #102033;
      font-size: 1.12rem;
      line-height: 1.3;
    }

    .card-main p {
      margin: 8px 0 0;
      color: #617283;
      line-height: 1.55;
    }

    .topic-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      min-height: 34px;
    }

    .extra-chip,
    .card-footer span {
      color: #536776;
      font-size: 0.84rem;
      font-weight: 720;
    }

    .extra-chip {
      padding: 6px 10px;
      border-radius: 999px;
      background: #f3f7fa;
    }

    .card-footer {
      padding-top: 2px;
      color: #526879;
    }

    .card-footer strong {
      color: #23617f;
      font-size: 0.92rem;
    }

    .muted {
      color: #7a8997;
      font-size: 0.88rem;
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      color: #5a6677;
    }

    @media (max-width: 980px) {
      .portal-search-strip,
      .portal-search-layout {
        grid-template-columns: 1fr;
      }

      .search-actions {
        justify-content: flex-start;
        padding-top: 0;
      }

      .filters-toggle {
        display: inline-flex;
      }

      .filter-panel {
        position: static;
        display: none;
      }

      .filter-panel.open {
        display: grid;
      }
    }

    @media (max-width: 640px) {
      .portal-search-strip {
        padding: 14px;
      }

      .pagination,
      .results-summary {
        justify-content: space-between;
      }

      .search-actions {
        justify-content: flex-start;
      }

      .result-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class PortalResearchUnitsPageComponent implements OnInit {
  private readonly portalApi = inject(PortalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly result = signal<PortalPageResponse<PortalResearchUnitSummary>>(this.emptyPage());
  readonly allUnits = signal<PortalResearchUnitSummary[]>([]);
  readonly unitDetails = signal<Record<number, PortalResearchUnitDetail>>({});
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly currentPage = signal(0);
  readonly selectedType = signal<ResearchUnitType | 'all'>('all');
  readonly filtersOpen = signal(false);

  readonly typeOptions = computed(() =>
    Array.from(new Set(this.allUnits().map((unit) => unit.type)))
      .sort((left, right) => this.typeLabel(left).localeCompare(this.typeLabel(right), 'es'))
  );
  readonly pageCount = computed(() => Math.max(this.result().totalPages, 1));

  ngOnInit(): void {
    this.loadAllUnits();

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const page = Math.max(this.parseNumber(params.get('page')) ?? 0, 0);
        const text = params.get('text') ?? '';
        const type = this.parseType(params.get('type'));
        this.currentPage.set(page);
        this.selectedType.set(type);
        this.searchControl.setValue(text, { emitEvent: false });
        this.loadPage(page, text, type);
      });
  }

  applyFilters(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: null,
        text: this.searchControl.value.trim() || null,
        type: this.selectedType() === 'all' ? null : this.selectedType()
      },
      queryParamsHandling: 'merge'
    });
  }

  clearFilters(): void {
    this.searchControl.setValue('');
    this.selectedType.set('all');
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: null,
        text: null,
        type: null
      },
      queryParamsHandling: 'merge'
    });
  }

  goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page === 0 ? null : page },
      queryParamsHandling: 'merge'
    });
  }

  toggleFilters(): void {
    this.filtersOpen.update((value) => !value);
  }

  selectType(type: ResearchUnitType | 'all'): void {
    this.selectedType.set(type);
    this.applyFilters();
  }

  hasActiveFilters(): boolean {
    return this.searchControl.value.trim().length > 0 || this.selectedType() !== 'all';
  }

  typeLabel(type: ResearchUnitType): string {
    return researchUnitTypeLabel(type);
  }

  hierarchyLabel(unit: PortalResearchUnitSummary): string {
    const trail = this.unitTrail(unit.id);
    return trail.length > 1 ? trail.slice(0, -1).join(' · ') : '';
  }

  unitDescription(unit: PortalResearchUnitSummary): string {
    const name = unit.shortName ? `${unit.shortName}. ` : '';
    const location = [unit.city, unit.country].filter(Boolean).join(', ');
    return `${name}${location || 'Información institucional pública disponible en la ficha.'}`;
  }

  visibleTopics(unitId: number): string[] {
    return this.unitDetails()[unitId]?.topics.slice(0, 3).map((topic) => topic.name) ?? [];
  }

  extraTopics(unitId: number): number {
    const total = this.unitDetails()[unitId]?.topics.length ?? 0;
    return Math.max(total - 3, 0);
  }

  compactCounts(unitId: number): string {
    const detail = this.unitDetails()[unitId];
    if (!detail) {
      return 'Actividad pública en carga';
    }
    const researchers = detail.collaborationSummary.researcherCount;
    const publications = detail.collaborationSummary.publicationCount;
    const researcherText = researchers === 1 ? '1 investigador' : `${researchers} investigadores`;
    const publicationText = publications === 1 ? '1 publicación' : `${publications} publicaciones`;
    return `${researcherText} · ${publicationText}`;
  }

  private loadPage(page: number, text: string, type: ResearchUnitType | 'all'): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.portalApi.researchUnits({
      page,
      size: 12,
      text: text || undefined,
      type: type === 'all' ? undefined : type
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
          this.loadUnitDetails(result.content);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No se ha podido cargar la información.');
          this.result.set(this.emptyPage());
          this.unitDetails.set({});
        }
      });
  }

  private loadUnitDetails(units: PortalResearchUnitSummary[]): void {
    if (units.length === 0) {
      this.unitDetails.set({});
      return;
    }

    forkJoin(
      units.map((unit) =>
        this.portalApi.researchUnit(unit.id).pipe(
          catchError(() => of(null))
        )
      )
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((details) => {
        this.unitDetails.set(
          details.reduce<Record<number, PortalResearchUnitDetail>>((accumulator, detail) => {
            if (detail) {
              accumulator[detail.unit.id] = detail;
            }
            return accumulator;
          }, {})
        );
      });
  }

  private loadAllUnits(): void {
    this.portalApi.researchUnits({ page: 0, size: 100 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (firstPage) => {
          if (firstPage.totalPages <= 1) {
            this.allUnits.set(firstPage.content);
            return;
          }

          forkJoin(
            Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
              this.portalApi.researchUnits({ page: index + 1, size: 100 }).pipe(
                map((pageResult) => pageResult.content),
                catchError(() => of([] as PortalResearchUnitSummary[]))
              )
            )
          )
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((remainingPages) => {
              this.allUnits.set([firstPage.content, ...remainingPages].flat());
            });
        },
        error: () => this.allUnits.set([])
      });
  }

  private unitTrail(unitId: number): string[] {
    const trail: string[] = [];
    const map = this.unitMap();
    let current = map.get(unitId) ?? null;
    while (current) {
      trail.unshift(current.name);
      current = current.parentId === null ? null : map.get(current.parentId) ?? null;
    }
    return trail;
  }

  private unitMap(): Map<number, PortalResearchUnitSummary> {
    return new Map(this.allUnits().map((unit) => [unit.id, unit]));
  }

  private parseNumber(value: string | null): number | undefined {
    if (!value) {
      return undefined;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }

  private parseType(value: string | null): ResearchUnitType | 'all' {
    if (!value) {
      return 'all';
    }
    return value as ResearchUnitType;
  }

  private emptyPage(): PortalPageResponse<PortalResearchUnitSummary> {
    return {
      content: [],
      page: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
      last: true,
      visibilityScope: 'PUBLIC_VALIDATED',
      validationFilterApplied: true
    };
  }
}
