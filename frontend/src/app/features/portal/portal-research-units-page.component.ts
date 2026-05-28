import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
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

interface UnitTreeRow {
  id: number;
  name: string;
  type: ResearchUnitType;
  level: number;
  childCount: number;
}

@Component({
  selector: 'rip-portal-research-units-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    TagChipComponent
  ],
  template: `
    <section class="page portal-directory">
      <rip-page-header
        title="Unidades"
        eyebrow="Portal público"
        [subtitle]="headerSubtitle()"
      />

      <section class="intro-grid">
        <mat-card appearance="outlined" class="hero-card">
          <mat-card-content>
            <p class="section-kicker">Directorio institucional</p>
            <h2>Explora la estructura investigadora propia de la universidad.</h2>
            <p class="hero-copy">
              Este directorio reúne solo facultades, departamentos, institutos, laboratorios, grupos, centros y
              demás unidades institucionales con información pública validada.
            </p>

            <div class="summary-strip">
              <div class="summary-chip">
                <strong>{{ unitCount() }}</strong>
                <span>unidades visibles</span>
              </div>
              <div class="summary-chip">
                <strong>{{ rootCount() }}</strong>
                <span>raíces institucionales</span>
              </div>
              <div class="summary-chip">
                <strong>{{ typeOptions().length }}</strong>
                <span>tipologías</span>
              </div>
            </div>

            <p class="trust-note">No se muestran organizaciones externas como unidades principales del portal.</p>
          </mat-card-content>
        </mat-card>

        <mat-card appearance="outlined" class="tree-card">
          <mat-card-header>
            <mat-card-title>Jerarquía visible</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (treeLoading()) {
              <rip-loading-state message="Cargando jerarquía institucional" />
            } @else if (treeRows().length === 0) {
              <rip-empty-state title="Sin jerarquía disponible" message="Todavía no hay estructura institucional visible." />
            } @else {
              <div class="tree-list">
                @for (row of treeRows().slice(0, 18); track row.id) {
                  <a class="tree-row" [routerLink]="['/portal/unidades', row.id]" [queryParams]="navigationContext.returnQueryParams('Volver a unidades')" [style.padding-left.px]="16 + row.level * 18">
                    <div>
                      <strong>{{ row.name }}</strong>
                      <p>{{ typeLabel(row.type) }}</p>
                    </div>
                    @if (row.childCount > 0) {
                      <span>{{ row.childCount }} subunidades</span>
                    }
                  </a>
                }
              </div>
            }
          </mat-card-content>
        </mat-card>
      </section>

      <mat-card appearance="outlined" class="search-card">
        <mat-card-content>
          <form class="search-shell" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline" class="search-field">
              <mat-label>Buscar unidad</mat-label>
              <input matInput [formControl]="searchControl" placeholder="Nombre, sigla, ciudad o país">
            </mat-form-field>

            <div class="search-actions">
              <button mat-stroked-button type="button" (click)="toggleTypeFilters()">
                {{ showTypeFilters() ? 'Ocultar tipos' : 'Filtrar por tipo' }}
              </button>
              @if (hasActiveFilters()) {
                <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
              }
              <button mat-flat-button color="primary" type="submit">Buscar</button>
            </div>
          </form>

          @if (showTypeFilters()) {
            <div class="type-pills">
              <button
                type="button"
                class="type-pill"
                [class.active]="selectedType() === 'all'"
                (click)="selectType('all')"
              >
                Todas
              </button>
              @for (type of typeOptions(); track type) {
                <button
                  type="button"
                  class="type-pill"
                  [class.active]="selectedType() === type"
                  (click)="selectType(type)"
                >
                  {{ typeLabel(type) }}
                </button>
              }
            </div>
          }
        </mat-card-content>
      </mat-card>

      @if (loading()) {
        <rip-loading-state message="Cargando directorio de unidades" />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else if (result().content.length === 0) {
        <rip-empty-state title="Sin resultados" message="Prueba otra búsqueda o cambia el tipo de unidad." />
      } @else {
        <div class="unit-grid">
          @for (unit of result().content; track unit.id) {
            <a class="unit-card" [routerLink]="['/portal/unidades', unit.id]" [queryParams]="navigationContext.returnQueryParams('Volver a unidades')">
              <div class="card-top">
                <span class="unit-type">{{ typeLabel(unit.type) }}</span>
                @if (hierarchyLabel(unit)) {
                  <span class="unit-hierarchy">{{ hierarchyLabel(unit) }}</span>
                }
              </div>

              <div class="card-headline">
                <strong>{{ unit.name }}</strong>
                @if (unit.shortName) {
                  <p>{{ unit.shortName }}</p>
                }
              </div>

              <p class="unit-copy">
                {{ locationLabel(unit) }} · {{ activityLabel(unit.id) }}
              </p>

              <div class="metric-row">
                <span>{{ researcherLabel(unit.id) }}</span>
                <span>{{ publicationLabel(unit.id) }}</span>
                <span>{{ childUnitLabel(unit.id) }}</span>
              </div>

              <div class="chip-list">
                @for (topic of visibleTopics(unit.id); track topic) {
                  <rip-tag-chip [label]="topic" />
                } @empty {
                  <span class="muted">Temas disponibles al abrir la ficha</span>
                }
              </div>
            </a>
          }
        </div>
      }

      <div class="pagination">
        <button mat-button type="button" [disabled]="currentPage() === 0" (click)="goToPage(currentPage() - 1)">Anterior</button>
        <span>Página {{ currentPage() + 1 }} de {{ pageCount() }}</span>
        <button mat-button type="button" [disabled]="result().last" (click)="goToPage(currentPage() + 1)">Siguiente</button>
      </div>
    </section>
  `,
  styles: [`
    .portal-directory {
      gap: 28px;
      min-width: 0;
    }

    .intro-grid {
      display: grid;
      grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
      gap: 22px;
      align-items: start;
      min-width: 0;
    }

    .hero-card,
    .tree-card,
    .search-card {
      border-radius: 24px !important;
      min-width: 0;
    }

    .hero-card {
      background:
        radial-gradient(circle at top right, rgba(73, 138, 171, 0.14), transparent 32%),
        linear-gradient(160deg, #ffffff, #f6fafc 62%, #f1f7f8 100%);
    }

    .hero-card mat-card-content {
      display: grid;
      gap: 18px;
    }

    h2 {
      margin: 0;
      color: #102033;
      font-size: clamp(1.6rem, 2.3vw, 2.3rem);
      line-height: 1.12;
      overflow-wrap: anywhere;
    }

    .hero-copy,
    .trust-note {
      margin: 0;
      color: #5f7182;
      line-height: 1.7;
      overflow-wrap: anywhere;
    }

    .tree-list {
      display: grid;
      gap: 10px;
    }

    .tree-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
      padding: 14px 16px;
      border: 1px solid #dfebf0;
      border-radius: 16px;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .tree-row:hover,
    .unit-card:hover {
      border-color: #aac8d7;
      box-shadow: 0 16px 32px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
    }

    .tree-row strong {
      color: #102033;
      line-height: 1.3;
    }

    .tree-row p,
    .tree-row span {
      margin: 4px 0 0;
      color: #617283;
      font-size: 0.88rem;
    }

    .search-shell {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 18px;
      align-items: center;
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

    .type-pills {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 16px;
    }

    .type-pill {
      padding: 9px 14px;
      border: 1px solid #d9e6ec;
      border-radius: 999px;
      background: #ffffff;
      color: #365369;
      font: inherit;
      cursor: pointer;
      transition: border-color 140ms ease, background 140ms ease, transform 140ms ease;
    }

    .type-pill.active,
    .type-pill:hover {
      border-color: #8eb4c8;
      background: #eef7fb;
      transform: translateY(-1px);
    }

    .unit-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 20px;
      min-width: 0;
    }

    .unit-card {
      display: grid;
      gap: 16px;
      padding: 24px;
      border: 1px solid #dfe8ef;
      border-radius: 24px;
      background: linear-gradient(180deg, #ffffff, #fbfdfe);
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .card-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }

    .unit-type,
    .unit-hierarchy {
      display: inline-flex;
      align-items: center;
      padding: 5px 10px;
      border-radius: 999px;
      font-size: 0.77rem;
      font-weight: 780;
    }

    .unit-type {
      background: #eef6f3;
      color: #28624a;
    }

    .unit-hierarchy {
      background: #eef4f8;
      color: #305a72;
    }

    .card-headline strong {
      display: block;
      color: #102033;
      font-size: 1.18rem;
      line-height: 1.26;
    }

    .card-headline p,
    .unit-copy {
      margin: 6px 0 0;
      color: #617283;
      line-height: 1.55;
    }

    .unit-copy {
      margin: 0;
    }

    .metric-row {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }

    .metric-row span {
      padding: 7px 11px;
      border-radius: 10px;
      background: #f5f8fa;
      color: #506373;
      font-size: 0.84rem;
      font-weight: 720;
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      color: #5a6677;
    }

    @media (max-width: 980px) {
      .intro-grid,
      .search-shell {
        grid-template-columns: 1fr;
      }

      .search-actions {
        justify-content: flex-start;
      }
    }

      @media (max-width: 720px) {
      .unit-grid {
        grid-template-columns: 1fr;
      }

      .tree-row {
        display: grid;
      }

      .pagination {
        justify-content: space-between;
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
  readonly treeLoading = signal(true);
  readonly errorMessage = signal('');
  readonly currentPage = signal(0);
  readonly selectedType = signal<ResearchUnitType | 'all'>('all');
  readonly showTypeFilters = signal(false);

  readonly typeOptions = computed(() =>
    Array.from(new Set(this.allUnits().map((unit) => unit.type)))
  );
  readonly unitCount = computed(() => this.allUnits().length);
  readonly rootCount = computed(() => this.allUnits().filter((unit) => unit.parentId === null).length);
  readonly pageCount = computed(() => Math.max(this.result().totalPages, 1));
  readonly headerSubtitle = computed(() => {
    const total = this.result().totalElements;
    return `${total} unidades institucionales visibles para explorar la estructura investigadora de la universidad`;
  });
  readonly treeRows = computed(() => this.flattenUnits(this.rootUnits(), 0));

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

  toggleTypeFilters(): void {
    this.showTypeFilters.update((value) => !value);
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

  locationLabel(unit: PortalResearchUnitSummary): string {
    return [unit.city, unit.country].filter(Boolean).join(', ') || 'Ubicación pública pendiente';
  }

  hierarchyLabel(unit: PortalResearchUnitSummary): string {
    const trail = this.unitTrail(unit.id);
    if (trail.length <= 1) {
      return '';
    }
    return trail.slice(0, -1).join(' · ');
  }

  visibleTopics(unitId: number): string[] {
    return this.unitDetails()[unitId]?.topics.slice(0, 4).map((topic) => topic.name) ?? [];
  }

  researcherLabel(unitId: number): string {
    const detail = this.unitDetails()[unitId];
    if (!detail) {
      return 'Investigadores: cargando';
    }
    const count = detail.collaborationSummary.researcherCount;
    return count === 1 ? '1 investigador' : `${count} investigadores`;
  }

  publicationLabel(unitId: number): string {
    const detail = this.unitDetails()[unitId];
    if (!detail) {
      return 'Publicaciones: cargando';
    }
    const count = detail.collaborationSummary.publicationCount;
    return count === 1 ? '1 publicación' : `${count} publicaciones`;
  }

  childUnitLabel(unitId: number): string {
    const detail = this.unitDetails()[unitId];
    if (!detail) {
      return 'Subunidades: cargando';
    }
    const count = detail.childUnits.length;
    return count === 0 ? 'Sin subunidades' : count === 1 ? '1 subunidad' : `${count} subunidades`;
  }

  activityLabel(unitId: number): string {
    const detail = this.unitDetails()[unitId];
    if (!detail) {
      return 'Actividad pública validada en carga';
    }
    const activities = detail.activities.length;
    if (activities === 0) {
      return 'Sin actividades públicas registradas';
    }
    return activities === 1 ? '1 actividad pública visible' : `${activities} actividades públicas visibles`;
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
          this.errorMessage.set('No se pudo cargar el directorio de unidades del portal.');
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
    this.treeLoading.set(true);
    this.portalApi.researchUnits({ page: 0, size: 100 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (firstPage) => {
          if (firstPage.totalPages <= 1) {
            this.allUnits.set(firstPage.content);
            this.treeLoading.set(false);
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
              this.treeLoading.set(false);
            });
        },
        error: () => {
          this.allUnits.set([]);
          this.treeLoading.set(false);
        }
      });
  }

  private rootUnits(): PortalResearchUnitSummary[] {
    return this.allUnits()
      .filter((unit) => unit.parentId === null || !this.unitMap().has(unit.parentId))
      .sort((left, right) => left.name.localeCompare(right.name, 'es'));
  }

  private childrenByParent(): Map<number, PortalResearchUnitSummary[]> {
    const grouped = new Map<number, PortalResearchUnitSummary[]>();
    for (const unit of this.allUnits()) {
      if (unit.parentId === null) {
        continue;
      }
      const current = grouped.get(unit.parentId) ?? [];
      current.push(unit);
      grouped.set(unit.parentId, current);
    }
    for (const entry of grouped.values()) {
      entry.sort((left, right) => left.name.localeCompare(right.name, 'es'));
    }
    return grouped;
  }

  private flattenUnits(units: PortalResearchUnitSummary[], level: number): UnitTreeRow[] {
    const childrenByParent = this.childrenByParent();
    return units.flatMap((unit) => {
      const children = childrenByParent.get(unit.id) ?? [];
      return [
        {
          id: unit.id,
          name: unit.name,
          type: unit.type,
          level,
          childCount: children.length
        },
        ...this.flattenUnits(children, level + 1)
      ];
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
