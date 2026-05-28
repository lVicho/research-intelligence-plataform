import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
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
    MatCardModule,
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
    <section class="page portal-researchers">
      <rip-page-header
        title="Investigadores"
        eyebrow="Portal público"
        [subtitle]="headerSubtitle()"
      />

      <section class="surface-intro portal-intro">
        <p class="section-kicker">Perfiles institucionales</p>
        <div class="intro-grid">
          <div>
            <h2>Perfiles públicos con afiliación, experiencia y actividad visible.</h2>
            <p>
              Encuentra investigadores por nombre, unidad o tema sin entrar en una vista administrativa ni en un
              formulario recargado.
            </p>
          </div>
          <div class="intro-pills">
            <span>{{ result().totalElements }} perfiles</span>
            <span>{{ unitOptions().length }} unidades</span>
          </div>
        </div>
      </section>

      <mat-card appearance="outlined" class="search-card">
        <mat-card-content>
          <form class="search-shell" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline" class="search-field">
              <mat-label>Buscar investigador</mat-label>
              <input matInput [formControl]="searchControl" placeholder="Nombre, ORCID o afiliación">
            </mat-form-field>

            <div class="search-actions">
              <button mat-stroked-button type="button" (click)="toggleAdvanced()">
                {{ showAdvanced() ? 'Ocultar filtros' : 'Más filtros' }}
              </button>
              @if (hasActiveFilters()) {
                <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
              }
              <button mat-flat-button color="primary" type="submit">Buscar</button>
            </div>
          </form>

          @if (showAdvanced()) {
            <div class="advanced-grid">
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
                <mat-label>Tema</mat-label>
                <input matInput [formControl]="topicControl" placeholder="Ej.: genómica, IA clínica, materiales">
              </mat-form-field>
            </div>
          }
        </mat-card-content>
      </mat-card>

      @if (loading()) {
        <rip-loading-state message="Cargando investigadores" />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else if (result().content.length === 0) {
        <rip-empty-state title="Sin resultados" message="Prueba otra búsqueda o ajusta los filtros del directorio." />
      } @else {
        <div class="researcher-grid">
          @for (researcher of result().content; track researcher.id) {
            <a class="researcher-card" [routerLink]="['/portal/investigadores', researcher.id]" [queryParams]="navigationContext.returnQueryParams('Volver a investigadores')">
              <div class="card-top">
                <div class="identity">
                  <strong>{{ researcher.displayName || researcher.fullName }}</strong>
                  <p>{{ researcher.primaryAffiliationName || 'Afiliación pública pendiente' }}</p>
                </div>
                @if (researcher.orcid) {
                  <span class="orcid-link">ORCID</span>
                }
              </div>

              <div class="metric-row">
                <span>{{ publicationLabel(researcher.id) }}</span>
                <span>{{ activityLabel(researcher.id) }}</span>
              </div>

              <div class="topic-block">
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
    .portal-researchers {
      gap: 28px;
    }

    .portal-intro {
      border-radius: 28px;
    }

    .intro-grid {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 18px;
      align-items: start;
    }

    .intro-grid h2 {
      margin: 0;
      color: #102033;
      font-size: clamp(1.55rem, 2.4vw, 2.1rem);
      line-height: 1.14;
    }

    .intro-grid p {
      margin-top: 12px;
      max-width: 64ch;
    }

    .intro-pills {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      justify-content: flex-end;
    }

    .intro-pills span {
      padding: 10px 14px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.88);
      border: 1px solid #d8e4eb;
      color: #365369;
      font-size: 0.86rem;
      font-weight: 760;
      white-space: nowrap;
    }

    .search-card {
      border-radius: 24px !important;
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

    .advanced-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 18px;
      margin-top: 16px;
    }

    .researcher-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 20px;
    }

    .researcher-card {
      display: grid;
      gap: 16px;
      padding: 24px;
      border: 1px solid #dde7ee;
      border-radius: 24px;
      background: linear-gradient(180deg, #ffffff, #fbfdfe);
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .researcher-card:hover {
      border-color: #a6c3d1;
      box-shadow: 0 18px 34px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
    }

    .card-top {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 16px;
    }

    .identity strong {
      display: block;
      color: #102033;
      font-size: 1.16rem;
      line-height: 1.28;
    }

    .identity p {
      margin: 8px 0 0;
      color: #617283;
      line-height: 1.55;
    }

    .orcid-link {
      display: inline-flex;
      align-items: center;
      padding: 6px 10px;
      border-radius: 999px;
      background: #f3f7fa;
      color: #4f6575;
      font-size: 0.76rem;
      font-weight: 780;
      white-space: nowrap;
    }

    .metric-row {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }

    .metric-row span,
    .extra-chip {
      padding: 7px 11px;
      border-radius: 10px;
      background: #f5f8fa;
      color: #536776;
      font-size: 0.84rem;
      font-weight: 720;
    }

    .topic-block {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      min-height: 36px;
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

      .search-actions,
      .intro-pills {
        justify-content: flex-start;
      }
    }

    @media (max-width: 720px) {
      .card-top {
        display: grid;
      }

      .pagination {
        justify-content: space-between;
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
  readonly showAdvanced = signal(false);
  readonly headerSubtitle = computed(() => {
    const total = this.result().totalElements;
    return `${total} perfiles institucionales para explorar experiencia, afiliación y actividad pública`;
  });
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

  toggleAdvanced(): void {
    this.showAdvanced.update((value) => !value);
  }

  hasActiveFilters(): boolean {
    return this.searchControl.value.trim().length > 0
      || this.topicControl.value.trim().length > 0
      || this.unitControl.value !== 'all';
  }

  visibleTopics(researcherId: number): string[] {
    return this.researcherDetails()[researcherId]?.topics.slice(0, 4).map((topic) => topic.name) ?? [];
  }

  extraTopics(researcherId: number): number {
    const total = this.researcherDetails()[researcherId]?.topics.length ?? 0;
    return Math.max(total - 4, 0);
  }

  publicationLabel(researcherId: number): string {
    const detail = this.researcherDetails()[researcherId];
    if (!detail) {
      return 'Publicaciones: cargando';
    }
    const count = detail.publications.length;
    return count === 1 ? '1 publicación' : `${count} publicaciones`;
  }

  activityLabel(researcherId: number): string {
    const detail = this.researcherDetails()[researcherId];
    if (!detail) {
      return 'Actividad: cargando';
    }
    const count = detail.activities.length;
    return count === 1 ? '1 actividad visible' : `${count} actividades visibles`;
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
          this.errorMessage.set('No se pudo cargar el directorio público de investigadores.');
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
        next: (units) => this.unitOptions.set(units),
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
