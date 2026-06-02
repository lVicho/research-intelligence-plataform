import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

import {
  PortalPageResponse,
  PortalResearchUnitSummary,
  PortalResearcherDetail,
  PortalResearcherSummary
} from '../../core/api/api-models';
import { PortalApiService } from '../../core/api/portal-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';

@Component({
  selector: 'rip-portal-researchers-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    TagChipComponent
  ],
  template: `
    <section class="page portal-list-page">
      <rip-page-header
        title="Investigadores"
        eyebrow="Portal público"
        subtitle="Busca perfiles públicos por nombre, unidad, tema o área de experiencia."
      />

      <section class="portal-list-intro">
        <p class="section-kicker">Perfiles institucionales</p>
        <h2>Investigadores con afiliación pública, temas de trabajo y actividad validada.</h2>
        <p>
          Un directorio claro para localizar experiencia investigadora sin exponer estados internos ni controles
          administrativos.
        </p>
      </section>

      <form class="portal-search-strip" (ngSubmit)="applyFilters()">
        <mat-form-field appearance="outline" class="search-field">
          <mat-label>Buscar investigadores</mat-label>
          <input matInput [formControl]="searchControl" placeholder="Buscar investigadores, temas o áreas de experiencia...">
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

          <mat-form-field appearance="outline">
            <mat-label>Unidad</mat-label>
            <mat-select [formControl]="unitControl">
              <mat-option value="all">Todas las unidades</mat-option>
              @for (unit of unitOptions(); track unit.id) {
                <mat-option [value]="unit.id.toString()">{{ unit.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Tema o experiencia</mat-label>
            <input matInput [formControl]="topicControl" placeholder="Ej.: genómica, IA clínica, materiales">
          </mat-form-field>

          <button mat-stroked-button type="button" (click)="applyFilters()">Aplicar filtros</button>
        </aside>

        <section class="results-panel">
          <div class="results-summary">
            <div>
              <p class="section-kicker">Resultados</p>
              <strong>{{ result().totalElements }} {{ result().totalElements === 1 ? 'perfil encontrado' : 'perfiles encontrados' }}</strong>
            </div>
            @if (hasActiveFilters()) {
              <span>Filtros aplicados</span>
            }
          </div>

          @if (loading()) {
            <rip-loading-state message="Cargando investigadores..." />
          } @else if (errorMessage()) {
            <rip-error-state [message]="errorMessage()" />
          } @else if (result().content.length === 0) {
            <rip-empty-state title="Sin resultados" message="No se han encontrado resultados con esos filtros." />
          } @else {
            <div class="result-grid">
              @for (researcher of result().content; track researcher.id) {
                <a class="portal-result-card" [routerLink]="['/portal/investigadores', researcher.id]" [queryParams]="navigationContext.returnQueryParams('Volver a investigadores')">
                  <div class="card-top">
                    <span class="profile-badge">Perfil público</span>
                    @if (researcher.orcid) {
                      <span class="subtle-meta">ORCID {{ researcher.orcid }}</span>
                    }
                  </div>

                  <div class="card-main">
                    <strong>{{ researcher.displayName || researcher.fullName }}</strong>
                    <p>{{ researcher.primaryAffiliationName || 'Afiliación pública pendiente' }}</p>
                  </div>

                  <p class="expertise-copy">{{ expertiseSummary(researcher.id) }}</p>

                  <div class="topic-row">
                    @for (topic of visibleTopics(researcher.id); track topic) {
                      <rip-tag-chip [label]="topic" />
                    }
                    @if (extraTopics(researcher.id) > 0) {
                      <span class="extra-chip">+{{ extraTopics(researcher.id) }}</span>
                    }
                    @if (visibleTopics(researcher.id).length === 0) {
                      <span class="muted">Temas visibles al abrir el perfil</span>
                    }
                  </div>

                  <div class="card-footer">
                    <span>Actividad pública validada</span>
                    <strong>Ver perfil</strong>
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
      gap: 16px;
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
      gap: 15px;
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

    .profile-badge {
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

    .card-main p,
    .expertise-copy {
      margin: 8px 0 0;
      color: #617283;
      line-height: 1.55;
    }

    .expertise-copy {
      margin: 0;
      min-height: 2.8em;
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
export class PortalResearchersPageComponent implements OnInit {
  private readonly portalApi = inject(PortalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly unitControl = new FormControl('all', { nonNullable: true });
  readonly topicControl = new FormControl('', { nonNullable: true });

  readonly result = signal<PortalPageResponse<PortalResearcherSummary>>(this.emptyPage());
  readonly unitOptions = signal<PortalResearchUnitSummary[]>([]);
  readonly researcherDetails = signal<Record<number, PortalResearcherDetail>>({});
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly currentPage = signal(0);
  readonly filtersOpen = signal(false);
  readonly pageCount = computed(() => Math.max(this.result().totalPages, 1));

  ngOnInit(): void {
    this.loadUnitOptions();

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const page = Math.max(this.parseNumber(params.get('page')) ?? 0, 0);
        const text = params.get('text') ?? '';
        const topic = params.get('topic') ?? '';
        const researchUnitId = params.get('researchUnitId') ?? 'all';

        this.currentPage.set(page);
        this.searchControl.setValue(text, { emitEvent: false });
        this.topicControl.setValue(topic, { emitEvent: false });
        this.unitControl.setValue(researchUnitId, { emitEvent: false });

        this.loadPage(page, text, topic, researchUnitId);
      });
  }

  applyFilters(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: null,
        text: this.searchControl.value.trim() || null,
        topic: this.topicControl.value.trim() || null,
        researchUnitId: this.unitControl.value === 'all' ? null : this.unitControl.value
      },
      queryParamsHandling: 'merge'
    });
  }

  clearFilters(): void {
    this.searchControl.setValue('');
    this.topicControl.setValue('');
    this.unitControl.setValue('all');
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: null,
        text: null,
        topic: null,
        researchUnitId: null
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

  hasActiveFilters(): boolean {
    return this.searchControl.value.trim().length > 0
      || this.topicControl.value.trim().length > 0
      || this.unitControl.value !== 'all';
  }

  visibleTopics(researcherId: number): string[] {
    return this.researcherDetails()[researcherId]?.topics.slice(0, 3).map((topic) => topic.name) ?? [];
  }

  extraTopics(researcherId: number): number {
    const total = this.researcherDetails()[researcherId]?.topics.length ?? 0;
    return Math.max(total - 3, 0);
  }

  expertiseSummary(researcherId: number): string {
    const topics = this.visibleTopics(researcherId);
    if (topics.length === 0) {
      return 'Perfil público con experiencia y producción validada por la institución.';
    }
    return `Áreas visibles: ${topics.join(', ')}.`;
  }

  private loadPage(page: number, text: string, topic: string, researchUnitId: string): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.portalApi.researchers({
      page,
      size: 12,
      text: text || undefined,
      topic: topic || undefined,
      researchUnitId: this.parseNumber(researchUnitId)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
          this.loadResearcherDetails(result.content);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No se ha podido cargar la información.');
          this.result.set(this.emptyPage());
          this.researcherDetails.set({});
        }
      });
  }

  private loadResearcherDetails(researchers: PortalResearcherSummary[]): void {
    if (researchers.length === 0) {
      this.researcherDetails.set({});
      return;
    }

    forkJoin(
      researchers.map((researcher) =>
        this.portalApi.researcher(researcher.id).pipe(
          catchError(() => of(null))
        )
      )
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((details) => {
        this.researcherDetails.set(
          details.reduce<Record<number, PortalResearcherDetail>>((accumulator, detail) => {
            if (detail) {
              accumulator[detail.id] = detail;
            }
            return accumulator;
          }, {})
        );
      });
  }

  private loadUnitOptions(): void {
    this.portalApi.researchUnits({ page: 0, size: 100 })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap((firstPage) => {
          const remaining = Math.max(firstPage.totalPages - 1, 0);
          if (remaining === 0) {
            return of(firstPage.content);
          }
          return forkJoin(
            Array.from({ length: remaining }, (_, index) =>
              this.portalApi.researchUnits({ page: index + 1, size: 100 }).pipe(
                map((pageResult) => pageResult.content),
                catchError(() => of([] as PortalResearchUnitSummary[]))
              )
            )
          ).pipe(
            map((remainingPages) => [firstPage.content, ...remainingPages].flat())
          );
        })
      )
      .subscribe({
        next: (units) => this.unitOptions.set([...units].sort((left, right) => left.name.localeCompare(right.name, 'es'))),
        error: () => this.unitOptions.set([])
      });
  }

  private parseNumber(value: string | null): number | undefined {
    if (!value || value === 'all') {
      return undefined;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }

  private emptyPage(): PortalPageResponse<PortalResearcherSummary> {
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
