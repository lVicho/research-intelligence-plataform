import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { PageResponse, ResearchUnit, Researcher, ResearcherSummary } from '../../core/api/api-models';
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
import { publicVisibilityNote, visibilityNoteForUser } from '../../shared/utils/visibility-labels';

type ActiveFilter = 'all' | 'true' | 'false';

@Component({
  selector: 'rip-researchers-page',
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
        title="Investigadores"
        [subtitle]="headerSubtitle()"
        eyebrow="Personas"
      >
        @if (canManageMasterData()) {
          <a mat-flat-button color="primary" [routerLink]="newResearcherLink()">Nuevo investigador</a>
        }
      </rip-page-header>

      @if (!isPortalView()) {
        <rip-visibility-note [message]="visibilityNote()" />
      }

      <section class="surface-intro">
        <p class="section-kicker">Perfiles públicos</p>
        <div class="intro-grid">
          <div>
            <h2>Localiza investigadores por nombre, afiliación y líneas de actividad.</h2>
            <p>
              El directorio destaca afiliaciones, temas principales y una referencia rápida al volumen de
              publicaciones asociadas.
            </p>
          </div>
          <div class="intro-pills">
            <span>{{ result()?.totalElements || 0 }} perfiles</span>
            <span>{{ researchUnits().length }} unidades disponibles</span>
          </div>
        </div>
      </section>

      <mat-card appearance="outlined" class="filter-card">
        <mat-card-content>
          <form class="form-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline">
              <mat-label>Buscar investigador</mat-label>
              <input matInput formControlName="text">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Estado</mat-label>
              <mat-select formControlName="active">
                <mat-option value="all">Todos</mat-option>
                <mat-option value="true">Activo</mat-option>
                <mat-option value="false">Inactivo</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Unidad de investigación</mat-label>
              <mat-select formControlName="researchUnitId">
                <mat-option value="all">Todas las unidades</mat-option>
                @for (unit of researchUnits(); track unit.id) {
                  <mat-option [value]="unit.id.toString()">{{ unit.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <div class="actions">
              <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
              <button mat-flat-button color="primary" type="submit">Aplicar</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      @if (loading()) {
        <rip-loading-state message="Cargando investigadores" />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else if ((result()?.content || []).length === 0) {
        <rip-empty-state title="No hay investigadores" message="Ajusta los filtros para explorar perfiles del portal." />
      } @else {
        <div class="card-grid">
          @for (researcher of result()?.content || []; track researcher.id) {
            <a class="researcher-card" [routerLink]="researcherLink(researcher.id)" [queryParams]="navigationContext.returnQueryParams('Volver a investigadores')">
              <div class="card-header">
                <div>
                  <strong>{{ researcher.displayName || researcher.fullName }}</strong>
                  <p>{{ researcher.primaryAffiliationName || 'Afiliación pública no disponible' }}</p>
                </div>
                @if (!isPortalView()) {
                  <rip-status-chip
                    [label]="researcher.active ? 'Activo' : 'Inactivo'"
                    [tone]="researcher.active ? 'success' : 'neutral'"
                  />
                }
              </div>

              @if (!isPortalView() && researcher.email) {
                <p class="supporting-text">{{ researcher.email }}</p>
              }

              <div class="meta-pills">
                @if (researcher.orcid) {
                  <span>ORCID disponible</span>
                }
                <span>{{ activityLabel(researcher.id) }}</span>
              </div>

              <div class="chip-list">
                @for (topic of researcherTopics(researcher.id); track topic) {
                  <rip-tag-chip [label]="topic" />
                } @empty {
                  <span class="muted">Sin temas públicos destacados</span>
                }
              </div>
            </a>
          }
        </div>
      }

      <div class="pagination">
        <button mat-button type="button" [disabled]="currentPage() === 0" (click)="goToPage(currentPage() - 1)">Anterior</button>
        <span>Página {{ currentPage() + 1 }} de {{ result()?.totalPages || 1 }}</span>
        <button mat-button type="button" [disabled]="result()?.last ?? true" (click)="goToPage(currentPage() + 1)">Siguiente</button>
      </div>
    </section>
  `,
  styles: [`
    .portal-page {
      display: grid;
      gap: 28px;
    }

    .filter-card {
      border-radius: 14px !important;
      box-shadow: none;
    }

    .intro-grid {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 18px;
      align-items: start;
    }

    .intro-grid h2 {
      margin: 0;
      color: #132133;
      font-size: clamp(1.45rem, 2.5vw, 1.9rem);
      line-height: 1.15;
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
      background: rgba(255, 255, 255, 0.84);
      border: 1px solid #d8e4eb;
      color: #365369;
      font-size: 0.86rem;
      font-weight: 760;
      white-space: nowrap;
    }

    .card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 18px;
    }

    .researcher-card {
      display: grid;
      gap: 14px;
      padding: 22px;
      border: 1px solid #dfe7ed;
      border-radius: 12px;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .researcher-card:hover {
      border-color: #9db8c7;
      box-shadow: 0 14px 28px rgba(20, 32, 51, 0.07);
      transform: translateY(-2px);
    }

    .researcher-card:focus-visible {
      outline: 3px solid rgba(41, 91, 128, 0.18);
      outline-offset: 3px;
    }

    .card-header {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 12px;
    }

    .card-header strong {
      color: #142033;
      font-size: 1.02rem;
      line-height: 1.32;
    }

    .card-header p,
    .supporting-text {
      margin: 6px 0 0;
      color: #667487;
      line-height: 1.45;
    }

    .meta-pills,
    .chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .meta-pills span {
      padding: 6px 10px;
      border-radius: 8px;
      background: #f6f7f9;
      color: #56697a;
      font-size: 0.82rem;
      font-weight: 700;
    }

    .portal-view .form-grid {
      grid-template-columns: minmax(260px, 1.4fr) minmax(160px, 0.55fr) minmax(260px, 1fr) auto;
    }

    .muted {
      color: #7a8897;
      font-size: 0.88rem;
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      color: #5a6677;
    }

    @media (max-width: 720px) {
      .card-header {
        display: grid;
      }

      .intro-grid {
        grid-template-columns: 1fr;
      }

      .portal-view .form-grid {
        grid-template-columns: 1fr;
      }

      .intro-pills {
        justify-content: flex-start;
      }

      .pagination {
        justify-content: space-between;
      }
    }
  `]
})
export class ResearchersPageComponent implements OnInit {
  private readonly api = inject(ResearchersApiService);
  private readonly researchUnitsApi = inject(ResearchUnitsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  readonly navigationContext = inject(NavigationContextService);

  readonly isPortalView = signal(this.route.snapshot.data['portalView'] === true);
  readonly result = signal<PageResponse<ResearcherSummary> | null>(null);
  readonly researchUnits = signal<ResearchUnit[]>([]);
  readonly researcherDetails = signal<Record<number, Researcher>>({});
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly currentPage = signal(0);
  readonly canManageMasterData = computed(() => this.auth.hasAnyRole(['ADMIN']) && !this.isPortalView());
  readonly visibilityNote = computed(() => this.isPortalView() ? publicVisibilityNote() : visibilityNoteForUser(this.auth.currentUser()));
  readonly headerSubtitle = computed(() => {
    const total = this.result()?.totalElements || 0;
    return this.isPortalView()
      ? `${total} perfiles para explorar experiencia, afiliación y actividad académica`
      : `${total} investigadores encontrados`;
  });

  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    active: new FormControl<ActiveFilter>('all', { nonNullable: true }),
    researchUnitId: new FormControl('all', { nonNullable: true })
  });

  ngOnInit(): void {
    this.researchUnitsApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((researchUnits) => this.researchUnits.set(researchUnits));

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const page = Math.max(this.toNumber(params.get('page') ?? '') ?? 0, 0);
        const text = params.get('text') ?? '';
        const active = this.toActiveFilter(params.get('active'));
        const researchUnitId = params.get('researchUnitId') ?? 'all';
        this.currentPage.set(page);
        this.filterForm.patchValue({ text, active, researchUnitId }, { emitEvent: false });
        this.loading.set(true);
        this.api.search({
          page,
          text: text || undefined,
          researchUnitId: this.toNumber(researchUnitId),
          active: active === 'all' ? undefined : active === 'true'
        })
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (result) => {
              this.result.set(result);
              this.loadResearcherDetails(result.content);
              this.loading.set(false);
              this.errorMessage.set('');
            },
            error: () => {
              this.result.set(null);
              this.loading.set(false);
              this.errorMessage.set('No se pudo cargar el directorio de investigadores.');
            }
          });
      });
  }

  applyFilters(): void {
    const value = this.filterForm.getRawValue();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: null,
        text: value.text || null,
        active: value.active === 'all' ? null : value.active,
        researchUnitId: value.researchUnitId === 'all' ? null : value.researchUnitId
      },
      queryParamsHandling: 'merge'
    });
  }

  clearFilters(): void {
    this.filterForm.reset({ text: '', active: 'all', researchUnitId: 'all' });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: null,
        text: null,
        active: null,
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

  researcherLink(researcherId: number): string[] {
    if (this.isPortalView()) {
      return ['/portal/investigadores', String(researcherId)];
    }
    return this.navigationContext.isCurrentPath('/admin/investigadores')
      ? ['/admin/investigadores', String(researcherId)]
      : ['/researchers', String(researcherId)];
  }

  newResearcherLink(): string[] {
    return this.navigationContext.isCurrentPath('/admin/investigadores') ? ['/admin/investigadores/new'] : ['/researchers/new'];
  }

  researcherTopics(researcherId: number): string[] {
    return this.researcherDetails()[researcherId]?.topics.slice(0, 4).map((topic) => topic.name) ?? [];
  }

  activityLabel(researcherId: number): string {
    const detail = this.researcherDetails()[researcherId];
    if (!detail) {
      return 'Actividad: cargando';
    }
    const count = detail.authoredPublications.length;
    return count === 1 ? '1 publicación' : `${count} publicaciones`;
  }

  private loadResearcherDetails(researchers: ResearcherSummary[]): void {
    if (researchers.length === 0) {
      this.researcherDetails.set({});
      return;
    }

    forkJoin(
      researchers.map((researcher) =>
        this.api.get(researcher.id).pipe(
          catchError(() => of(null))
        )
      )
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((details) => {
        this.researcherDetails.set(
          details.reduce<Record<number, Researcher>>((accumulator, detail) => {
            if (detail) {
              accumulator[detail.id] = detail;
            }
            return accumulator;
          }, {})
        );
      });
  }

  private toActiveFilter(value: string | null): ActiveFilter {
    return value === 'true' || value === 'false' ? value : 'all';
  }

  private toNumber(value: string): number | undefined {
    const parsed = Number(value);
    return Number.isFinite(parsed) && value !== '' && value !== 'all' ? parsed : undefined;
  }
}
