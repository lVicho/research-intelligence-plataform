import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { OrganizationScope, PageResponse, ResearchUnit, ResearchUnitTreeNode } from '../../core/api/api-models';
import { PublicationsApiService } from '../../core/api/publications-api.service';
import { ResearchUnitsApiService } from '../../core/api/research-units-api.service';
import { ResearchersApiService } from '../../core/api/researchers-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { VisibilityNoteComponent } from '../../shared/components/visibility-note.component';
import { organizationScopeLabel, researchUnitTypeLabel } from '../../shared/utils/display-labels';
import { publicVisibilityNote, visibilityNoteForUser } from '../../shared/utils/visibility-labels';

interface TreeRow {
  id: number;
  name: string;
  type: string;
  level: number;
  active: boolean;
  organizationScope: OrganizationScope;
}

interface UnitPortalStats {
  publicationCount: number;
  researcherCount: number;
  topics: string[];
}

const EXTERNAL_ORGANIZATIONS_HELPER =
  'Las organizaciones externas pueden aparecer como colaboradoras, pero no en el directorio público de unidades.';

@Component({
  selector: 'rip-research-units-page',
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
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent,
    VisibilityNoteComponent
  ],
  template: `
    <section class="page portal-page" [class.portal-view]="isPortalView()">
      <rip-page-header
        [title]="headerTitle()"
        [subtitle]="headerSubtitle()"
        eyebrow="Organización"
      >
        @if (showScopeSwitcher()) {
          <div class="scope-switcher">
            <a mat-button [class.scope-link-active]="!isExternalView()" [routerLink]="['/admin/unidades']">Unidades internas</a>
            <a mat-button [class.scope-link-active]="isExternalView()" [routerLink]="['/admin/organizaciones-externas']">Organizaciones externas</a>
          </div>
        }
        @if (canManageMasterData()) {
          <a mat-flat-button color="primary" [routerLink]="newUnitLink()">{{ createButtonLabel() }}</a>
        }
      </rip-page-header>

      @if (!isPortalView()) {
        <rip-visibility-note [message]="visibilityMessage()" />
      }

      @if (isExternalView()) {
        <mat-card appearance="outlined" class="hero-card hero-card-wide">
          <mat-card-content>
            <div class="hero-grid hero-grid-wide">
              <div>
                <p class="section-kicker">Colaboración institucional</p>
                <h2>Directorio administrativo para hospitales, empresas, fundaciones y otras entidades colaboradoras.</h2>
                <p class="hero-copy">
                  Mantén aquí las organizaciones externas vinculadas a publicaciones, afiliaciones y actividad científica sin
                  mezclarlas con la estructura interna de la institución.
                </p>
                <p class="trust-note">{{ externalOrganizationsHelper }}</p>
              </div>
              <div class="hero-metrics">
                <div>
                  <span>Organizaciones activas</span>
                  <strong>{{ activeUnitsCount() }}</strong>
                </div>
                <div>
                  <span>Países representados</span>
                  <strong>{{ representedCountriesCount() }}</strong>
                </div>
                <div>
                  <span>Coincidencias</span>
                  <strong>{{ filteredUnits().length }}</strong>
                </div>
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="layout-grid">
          <mat-card appearance="outlined" class="hero-card">
            <mat-card-content>
              <div class="hero-grid">
                <div>
                  <p class="section-kicker">{{ isPortalView() ? 'Directorio público' : 'Estructura interna' }}</p>
                  <h2>Explora institutos, grupos, departamentos y laboratorios de la universidad.</h2>
                  <p class="hero-copy">
                    Cada ficha destaca el tipo de unidad, su actividad pública, sus temas y su relación con investigadores y
                    publicaciones.
                  </p>
                </div>
                <div class="hero-metrics">
                  <div>
                    <span>Unidades activas</span>
                    <strong>{{ activeUnitsCount() }}</strong>
                  </div>
                  <div>
                    <span>Niveles jerárquicos</span>
                    <strong>{{ hierarchyDepth() }}</strong>
                  </div>
                  <div>
                    <span>Coincidencias</span>
                    <strong>{{ filteredUnits().length }}</strong>
                  </div>
                </div>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined" class="tree-card">
            <mat-card-header>
              <mat-card-title>Estructura institucional</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="tree-list">
                @for (row of treeRows(); track row.id) {
                  <a
                    class="tree-row"
                    [routerLink]="unitLink(row.id, row.organizationScope)"
                    [queryParams]="navigationContext.returnQueryParams(backNavigationLabel())"
                    [style.padding-left.px]="14 + row.level * 18"
                  >
                    <span class="tree-name">{{ row.name }}</span>
                    <span class="tree-meta">
                      <span class="muted">{{ typeLabel(row.type) }}</span>
                      @if (!isPortalView()) {
                        <rip-status-chip [label]="row.active ? 'Activa' : 'Inactiva'" [tone]="row.active ? 'success' : 'neutral'" />
                      }
                    </span>
                  </a>
                } @empty {
                  <rip-empty-state title="Árbol vacío" message="Aún no hay jerarquía institucional disponible." />
                }
              </div>
            </mat-card-content>
          </mat-card>
        </div>
      }

      <mat-card appearance="outlined" class="filter-card">
        <mat-card-content>
          <form class="form-grid" [formGroup]="filterForm">
            <mat-form-field appearance="outline">
              <mat-label>{{ isExternalView() ? 'Buscar organización' : 'Buscar unidad' }}</mat-label>
              <input
                matInput
                formControlName="text"
                [placeholder]="isExternalView() ? 'Nombre, tipo, país o web' : 'Nombre, sigla, ciudad o país'"
              >
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Estado</mat-label>
              <mat-select formControlName="active">
                <mat-option value="all">Todas</mat-option>
                <mat-option value="true">Activas</mat-option>
                <mat-option value="false">Inactivas</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Tipo</mat-label>
              <mat-select formControlName="type">
                <mat-option value="all">Todos los tipos</mat-option>
                @for (type of availableTypes(); track type) {
                  <mat-option [value]="type">{{ typeLabel(type) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </form>
        </mat-card-content>
      </mat-card>

      @if (loading()) {
        <rip-loading-state [message]="isExternalView() ? 'Cargando organizaciones externas' : 'Cargando directorio de unidades'" />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else {
        <div class="unit-grid">
          @for (unit of filteredUnits(); track unit.id) {
            <a class="unit-card" [routerLink]="unitLink(unit.id, unit.organizationScope)" [queryParams]="navigationContext.returnQueryParams(backNavigationLabel())">
              <div class="unit-card-header">
                <div>
                  <span class="unit-type">{{ typeLabel(unit.type) }}</span>
                  <strong>{{ unit.name }}</strong>
                  <p>{{ cardSubtitle(unit) }}</p>
                </div>
                @if (!isPortalView()) {
                  <rip-status-chip [label]="unit.active ? 'Activa' : 'Inactiva'" [tone]="unit.active ? 'success' : 'neutral'" />
                }
              </div>

              <div class="unit-stats">
                <span>{{ scopeLabel(unit.organizationScope) }}</span>
                <span>{{ statsLabel(unit.id, 'researchers') }}</span>
                <span>{{ statsLabel(unit.id, 'publications') }}</span>
                <span>{{ visibilityLabel(unit) }}</span>
              </div>

              <div class="chip-list">
                @for (topic of topicsForUnit(unit.id); track topic) {
                  <rip-tag-chip [label]="topic" />
                } @empty {
                  <span class="muted">{{ isExternalView() ? 'Temas asociados visibles al abrir el detalle' : 'Temas al abrir el detalle' }}</span>
                }
              </div>
            </a>
          } @empty {
            <rip-empty-state [title]="emptyTitle()" [message]="emptyMessage()" />
          }
        </div>
      }
    </section>
  `,
  styles: [`
    .portal-page {
      display: grid;
      gap: 24px;
    }

    .portal-view {
      gap: 28px;
    }

    .scope-switcher {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-right: 4px;
    }

    .scope-switcher a {
      border-radius: 999px;
    }

    .scope-link-active {
      background: #eef7fb;
      color: #144d67;
    }

    .layout-grid {
      display: grid;
      grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
      gap: 22px;
      align-items: start;
    }

    .hero-card,
    .tree-card,
    .unit-card,
    .filter-card {
      border-radius: 14px !important;
    }

    .hero-card {
      background: linear-gradient(135deg, #ffffff, #f8fafb);
    }

    .hero-card-wide {
      background: linear-gradient(135deg, #ffffff, #f7fbfc);
    }

    .hero-grid {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 20px;
      align-items: start;
    }

    .hero-grid-wide {
      grid-template-columns: minmax(0, 1.2fr) minmax(220px, 0.8fr);
    }

    h2 {
      margin: 0;
      color: #142033;
      font-size: clamp(1.45rem, 2.1vw, 2.05rem);
      line-height: 1.16;
      font-weight: 740;
    }

    .hero-copy,
    .trust-note {
      margin: 12px 0 0;
      color: #607081;
      line-height: 1.6;
      max-width: 64ch;
    }

    .trust-note {
      font-size: 0.92rem;
      color: #3d5b70;
    }

    .hero-metrics {
      display: grid;
      gap: 12px;
      min-width: 220px;
    }

    .hero-metrics div {
      display: grid;
      gap: 4px;
      padding: 14px 16px;
      border: 1px solid #e0e8ee;
      border-radius: 10px;
      background: #ffffff;
    }

    .hero-metrics span {
      color: #698091;
      font-size: 0.78rem;
      font-weight: 760;
      text-transform: uppercase;
    }

    .hero-metrics strong {
      color: #142033;
      font-size: 1.45rem;
      font-weight: 800;
    }

    .tree-list,
    .unit-grid {
      display: grid;
      gap: 12px;
    }

    .tree-card mat-card-content {
      max-height: 380px;
      overflow: auto;
    }

    .tree-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      min-height: 54px;
      padding: 12px 14px;
      border: 1px solid #e4e9ef;
      border-radius: 10px;
      background: #ffffff;
      color: #1c2635;
      text-decoration: none;
    }

    .tree-row:hover,
    .unit-card:hover {
      border-color: #b8d2df;
      box-shadow: 0 14px 30px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
    }

    .tree-name {
      font-weight: 700;
    }

    .tree-meta {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }

    .unit-grid {
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    }

    .unit-card {
      display: grid;
      gap: 16px;
      padding: 22px;
      border: 1px solid #dfe7ed;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .unit-card-header {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 12px;
    }

    .unit-card-header strong {
      display: block;
      color: #142033;
      font-size: 1.04rem;
      line-height: 1.32;
      margin-top: 8px;
    }

    .unit-card-header p {
      margin: 6px 0 0;
      color: #667487;
      line-height: 1.45;
    }

    .unit-type {
      display: inline-flex;
      padding: 4px 10px;
      border-radius: 999px;
      background: #eef6f3;
      color: #28624a;
      font-size: 0.76rem;
      font-weight: 780;
    }

    .unit-stats {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .unit-stats span,
    .muted {
      color: #667487;
      font-size: 0.88rem;
    }

    .unit-stats span {
      padding: 6px 10px;
      border-radius: 8px;
      background: #f6f7f9;
    }

    .chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .filter-card mat-card-content {
      padding-top: 20px !important;
    }

    .portal-view .filter-card {
      background: #ffffff;
      box-shadow: none;
    }

    .portal-view .form-grid {
      grid-template-columns: minmax(260px, 1.5fr) minmax(180px, 0.6fr) minmax(220px, 0.9fr);
    }

    @media (max-width: 980px) {
      .layout-grid,
      .hero-grid,
      .hero-grid-wide,
      .portal-view .form-grid {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 720px) {
      .tree-row,
      .unit-card-header {
        display: grid;
      }

      .tree-meta {
        justify-content: flex-start;
      }
    }
  `]
})
export class ResearchUnitsPageComponent implements OnInit {
  private readonly api = inject(ResearchUnitsApiService);
  private readonly researchersApi = inject(ResearchersApiService);
  private readonly publicationsApi = inject(PublicationsApiService);
  private readonly auth = inject(AuthStateService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly externalOrganizationsHelper = EXTERNAL_ORGANIZATIONS_HELPER;
  readonly isPortalView = signal(this.route.snapshot.data['portalView'] === true);
  readonly routeScope = signal((this.route.snapshot.data['organizationScope'] as OrganizationScope | undefined) ?? null);
  readonly units = signal<ResearchUnit[]>([]);
  readonly tree = signal<ResearchUnitTreeNode[]>([]);
  readonly unitStats = signal<Record<number, UnitPortalStats>>({});
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly filterValue = signal({ text: '', active: 'all' as 'all' | 'true' | 'false', type: 'all' });
  readonly scopeFilter = computed<OrganizationScope | null>(() => this.isPortalView() ? 'INTERNAL' : this.routeScope());
  readonly isExternalView = computed(() => this.scopeFilter() === 'EXTERNAL');
  readonly showScopeSwitcher = computed(() => this.navigationContext.isCurrentPath('/admin'));
  readonly scopedUnits = computed(() => {
    const scope = this.scopeFilter();
    return scope === null ? this.units() : this.units().filter((unit) => unit.organizationScope === scope);
  });
  readonly availableTypes = computed(() => Array.from(new Set(this.scopedUnits().map((unit) => unit.type))));
  readonly activeUnitsCount = computed(() => this.scopedUnits().filter((unit) => unit.active).length);
  readonly representedCountriesCount = computed(() =>
    new Set(this.scopedUnits().map((unit) => unit.country?.trim()).filter((value): value is string => !!value)).size
  );
  readonly treeRows = computed(() => this.isExternalView() ? [] : this.flattenTree(this.filterTree(this.tree()), 0));
  readonly hierarchyDepth = computed(() => this.treeRows().reduce((max, row) => Math.max(max, row.level + 1), 0));
  readonly filteredUnits = computed(() => {
    const { text, active, type } = this.filterValue();
    const normalized = text.trim().toLocaleLowerCase('es');

    return this.scopedUnits().filter((unit) => {
      const matchesText = normalized.length === 0 || [
        unit.name,
        unit.shortName,
        unit.city,
        unit.country,
        unit.website,
        unit.publicDescription
      ]
        .filter((value): value is string => !!value)
        .some((value) => value.toLocaleLowerCase('es').includes(normalized));
      const matchesActive = active === 'all' || String(unit.active) === active;
      const matchesType = type === 'all' || unit.type === type;
      return matchesText && matchesActive && matchesType;
    });
  });
  readonly canManageMasterData = computed(() => this.auth.hasAnyRole(['ADMIN']) && !this.isPortalView());
  readonly visibilityMessage = computed(() => {
    if (this.isPortalView()) {
      return publicVisibilityNote();
    }
    if (this.isExternalView()) {
      return EXTERNAL_ORGANIZATIONS_HELPER;
    }
    return visibilityNoteForUser(this.auth.currentUser());
  });
  readonly headerTitle = computed(() => {
    if (this.isPortalView()) {
      return 'Unidades de investigación';
    }
    return this.isExternalView() ? 'Organizaciones externas' : 'Unidades internas';
  });
  readonly headerSubtitle = computed(() => {
    const count = this.filteredUnits().length;
    if (this.isPortalView()) {
      return `${count} unidades internas visibles para explorar la estructura investigadora de la institución.`;
    }
    if (this.isExternalView()) {
      return `${count} organizaciones colaboradoras fuera del directorio público de unidades.`;
    }
    return `${count} unidades institucionales para gestión interna y visibilidad en portal.`;
  });
  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    active: new FormControl<'all' | 'true' | 'false'>('all', { nonNullable: true }),
    type: new FormControl<string>('all', { nonNullable: true })
  });

  ngOnInit(): void {
    this.filterForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.filterValue.set({
          text: value.text ?? '',
          active: value.active ?? 'all',
          type: value.type ?? 'all'
        });
      });

    this.api.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (units) => {
          this.units.set(units);
          this.loadUnitStats(units);
          this.loading.set(false);
        },
        error: () => {
          this.errorMessage.set(this.isExternalView() ? 'No se pudo cargar el directorio de organizaciones externas.' : 'No se pudo cargar el directorio de unidades.');
          this.loading.set(false);
        }
      });

    this.api.tree()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tree) => this.tree.set(tree),
        error: () => this.tree.set([])
      });
  }

  typeLabel(type: string): string {
    return researchUnitTypeLabel(type);
  }

  scopeLabel(scope: OrganizationScope): string {
    return organizationScopeLabel(scope);
  }

  cardSubtitle(unit: ResearchUnit): string {
    if (this.isExternalView()) {
      return [unit.country, unit.website].filter(Boolean).join(' · ') || unit.publicDescription || 'Sin contexto adicional';
    }
    return unit.shortName || this.locationLabel(unit);
  }

  locationLabel(unit: ResearchUnit): string {
    return [unit.city, unit.country].filter(Boolean).join(', ') || 'Ubicación pública pendiente';
  }

  visibilityLabel(unit: ResearchUnit): string {
    if (unit.organizationScope === 'EXTERNAL') {
      return 'Fuera del portal de unidades';
    }
    return unit.visibleInPortal ? 'Visible en portal' : 'Oculta en portal';
  }

  createButtonLabel(): string {
    return this.isExternalView() ? 'Nueva organización externa' : 'Nueva unidad interna';
  }

  backNavigationLabel(): string {
    return this.isExternalView() ? 'Volver a organizaciones externas' : 'Volver a unidades';
  }

  emptyTitle(): string {
    return this.isExternalView() ? 'Sin organizaciones' : 'Sin resultados';
  }

  emptyMessage(): string {
    return this.isExternalView()
      ? 'Ajusta los filtros para encontrar organizaciones externas colaboradoras.'
      : 'Ajusta los filtros para encontrar unidades internas.';
  }

  unitLink(unitId: number, scope: OrganizationScope): string[] {
    if (this.isPortalView()) {
      return ['/portal/unidades', String(unitId)];
    }
    if (this.navigationContext.isCurrentPath('/admin')) {
      const basePath = scope === 'EXTERNAL' ? '/admin/organizaciones-externas' : '/admin/unidades';
      return [basePath, String(unitId)];
    }
    return ['/research-units', String(unitId)];
  }

  newUnitLink(): string[] {
    if (this.navigationContext.isCurrentPath('/admin')) {
      const basePath = this.isExternalView() ? '/admin/organizaciones-externas' : '/admin/unidades';
      return [`${basePath}/new`];
    }
    return ['/research-units/new'];
  }

  statsLabel(unitId: number, kind: 'researchers' | 'publications'): string {
    const stats = this.unitStats()[unitId];
    if (!stats) {
      return kind === 'researchers' ? 'Investigadores: cargando' : 'Publicaciones: cargando';
    }
    if (kind === 'researchers') {
      return `Investigadores: ${stats.researcherCount}`;
    }
    return `Publicaciones: ${stats.publicationCount}`;
  }

  topicsForUnit(unitId: number): string[] {
    return this.unitStats()[unitId]?.topics ?? [];
  }

  private loadUnitStats(units: ResearchUnit[]): void {
    if (units.length === 0) {
      this.unitStats.set({});
      return;
    }

    forkJoin(
      units.map((unit) =>
        forkJoin({
          publications: this.publicationsApi.search({ researchUnitId: unit.id, size: 4 }).pipe(
            catchError(() => of(this.emptyPublicationPage()))
          ),
          researchers: this.researchersApi.search({ researchUnitId: unit.id, size: 1 }).pipe(
            catchError(() => of(this.emptyResearcherPage()))
          )
        }).pipe(
          map(({ publications, researchers }) => ({
            id: unit.id,
            stats: {
              publicationCount: publications.totalElements,
              researcherCount: researchers.totalElements,
              topics: this.uniqueTopics(publications.content)
            }
          }))
        )
      )
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((entries) => {
        this.unitStats.set(
          entries.reduce<Record<number, UnitPortalStats>>((accumulator, entry) => {
            accumulator[entry.id] = entry.stats;
            return accumulator;
          }, {})
        );
      });
  }

  private uniqueTopics(publications: Array<{ topics: string[] }>): string[] {
    return Array.from(new Set(publications.flatMap((publication) => publication.topics))).slice(0, 4);
  }

  private filterTree(nodes: ResearchUnitTreeNode[]): ResearchUnitTreeNode[] {
    const scope = this.scopeFilter();
    if (scope === null) {
      return nodes;
    }

    return nodes.flatMap((node) => {
      const children = this.filterTree(node.children);
      const unit = this.units().find((candidate) => candidate.id === node.id);
      if (!unit || unit.organizationScope !== scope) {
        return children;
      }
      return [{ ...node, children }];
    });
  }

  private flattenTree(nodes: ResearchUnitTreeNode[], level: number): TreeRow[] {
    return nodes.flatMap((node) => {
      const unit = this.units().find((candidate) => candidate.id === node.id);
      const organizationScope = unit?.organizationScope ?? 'INTERNAL';
      return [
        {
          id: node.id,
          name: node.name,
          type: node.type,
          level,
          active: node.active,
          organizationScope
        },
        ...this.flattenTree(node.children, level + 1)
      ];
    });
  }

  private emptyPublicationPage(): PageResponse<{ topics: string[] }> {
    return {
      content: [],
      page: 0,
      size: 4,
      totalElements: 0,
      totalPages: 0,
      last: true
    };
  }

  private emptyResearcherPage(): PageResponse<unknown> {
    return {
      content: [],
      page: 0,
      size: 1,
      totalElements: 0,
      totalPages: 0,
      last: true
    };
  }
}
