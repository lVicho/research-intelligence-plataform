import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

import {
  PortalResearchUnitDetail,
  PortalResearchUnitSummary,
  PublicationSummary
} from '../../core/api/api-models';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { PortalApiService } from '../../core/api/portal-api.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { publicationTypeLabel, researchUnitTypeLabel } from '../../shared/utils/display-labels';

@Component({
  selector: 'rip-portal-research-unit-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    TagChipComponent
  ],
  template: `
    <section class="page portal-unit-detail">
      <rip-page-header
        [title]="detail()?.unit?.name || 'Unidad'"
        eyebrow="Portal público"
        [subtitle]="pageSubtitle()"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
      </rip-page-header>

      @if (loading()) {
        <rip-loading-state message="Cargando ficha pública de la unidad" />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else {
        @if (detail(); as detail) {
        <mat-card appearance="outlined" class="hero-card">
          <mat-card-content>
            <div class="hero-top">
              <div class="hero-copy">
                <div class="pill-row">
                  <span class="type-pill">{{ typeLabel(detail.unit.type) }}</span>
                  @if (trailLabel()) {
                    <span class="trail-pill">{{ trailLabel() }}</span>
                  }
                </div>

                <h2>{{ detail.unit.name }}</h2>

                @if (detail.unit.shortName) {
                  <p class="lead">{{ detail.unit.shortName }}</p>
                }

                <p class="supporting">
                  {{ locationLabel(detail.unit) }}
                  @if (detail.unit.website) {
                    · <a [href]="detail.unit.website" target="_blank" rel="noreferrer">sitio web</a>
                  }
                </p>
              </div>

              <div class="hero-metrics">
                <div>
                  <span>Investigadores</span>
                  <strong>{{ detail.collaborationSummary.researcherCount }}</strong>
                </div>
                <div>
                  <span>Publicaciones</span>
                  <strong>{{ detail.collaborationSummary.publicationCount }}</strong>
                </div>
                <div>
                  <span>Actividades</span>
                  <strong>{{ detail.collaborationSummary.activityCount }}</strong>
                </div>
              </div>
            </div>

            <div class="topic-strip">
              @for (topic of detail.topics.slice(0, 8); track topic.name) {
                <rip-tag-chip [label]="topic.name + ' · ' + topic.count" />
              } @empty {
                <span class="muted">Los temas públicos aparecerán cuando haya suficiente actividad validada.</span>
              }
            </div>
          </mat-card-content>
        </mat-card>

        <section class="content-grid">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Investigadores vinculados</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="list-grid">
                @for (researcher of detail.researchers; track researcher.id) {
                  <a class="list-card" [routerLink]="['/portal/investigadores', researcher.id]" [queryParams]="navigationContext.returnQueryParams('Volver a la unidad')">
                    <strong>{{ researcher.displayName || researcher.fullName }}</strong>
                    <p>{{ researcher.primaryAffiliationName || 'Afiliación pública sin detalle adicional' }}</p>
                    @if (researcher.orcid) {
                      <span class="subtle-link">ORCID disponible</span>
                    }
                  </a>
                } @empty {
                  <rip-empty-state title="Sin investigadores visibles" message="No hay perfiles públicos vinculados a esta unidad." />
                }
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Marco institucional</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="meta-stack">
                <div>
                  <span class="label">Ruta institucional</span>
                  <strong>{{ fullTrailLabel() || detail.unit.name }}</strong>
                </div>
                <div>
                  <span class="label">Subunidades públicas</span>
                  <strong>{{ detail.childUnits.length }}</strong>
                </div>
              </div>

              <div class="list-grid compact-grid">
                @for (child of detail.childUnits; track child.id) {
                  <a class="list-card compact-card" [routerLink]="['/portal/unidades', child.id]" [queryParams]="navigationContext.returnQueryParams('Volver a la unidad')">
                    <strong>{{ child.name }}</strong>
                    <p>{{ typeLabel(child.type) }}</p>
                  </a>
                } @empty {
                  <rip-empty-state title="Sin subunidades visibles" message="No hay subunidades públicas asociadas a esta ficha." />
                }
              </div>
            </mat-card-content>
          </mat-card>
        </section>

        <section class="content-grid">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Publicaciones validadas</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="publication-list">
                @for (publication of detail.publications; track publication.id) {
                  <a class="publication-card" [routerLink]="['/portal/publicaciones', publication.id]" [queryParams]="navigationContext.returnQueryParams('Volver a la unidad')">
                    <div class="publication-head">
                      <strong>{{ publication.title }}</strong>
                      <span>{{ publication.year || 's. f.' }}</span>
                    </div>
                    <p>{{ publication.source || publication.doi || 'Repositorio institucional' }}</p>
                    <div class="chip-list">
                      <rip-tag-chip [label]="publicationTypeLabel(publication.type)" tone="type" />
                      @for (topic of publication.topics.slice(0, 4); track topic) {
                        <rip-tag-chip [label]="topic" />
                      }
                    </div>
                  </a>
                } @empty {
                  <rip-empty-state title="Sin publicaciones visibles" message="Esta unidad todavía no muestra publicaciones validadas." />
                }
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Actividades relacionadas</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="activity-list">
                @for (activity of detail.activities; track activity.id) {
                  <div class="activity-card">
                    <strong>{{ activity.title }}</strong>
                    <p>{{ activity.eventName || 'Actividad institucional' }}</p>
                    <span>{{ activity.participationDate || 'Fecha pendiente' }}</span>
                    @if (activity.relatedPublicationId) {
                      <a class="subtle-link" [routerLink]="['/portal/publicaciones', activity.relatedPublicationId]" [queryParams]="navigationContext.returnQueryParams('Volver a la unidad')">
                        {{ activity.relatedPublicationTitle || 'Ver publicación relacionada' }}
                      </a>
                    }
                  </div>
                } @empty {
                  <rip-empty-state title="Sin actividades visibles" message="No hay actividades públicas adicionales para esta unidad." />
                }
              </div>
            </mat-card-content>
          </mat-card>
        </section>
        } @else {
          <rip-empty-state title="Unidad no disponible" message="Esta ficha ya no está visible en el portal público." />
        }
      }
    </section>
  `,
  styles: [`
    .portal-unit-detail {
      gap: 24px;
    }

    .hero-card {
      border-radius: 28px !important;
      background:
        radial-gradient(circle at top right, rgba(70, 133, 162, 0.16), transparent 28%),
        linear-gradient(160deg, #ffffff, #f6fafc 68%, #f3f7f8 100%);
    }

    .hero-card mat-card-content {
      display: grid;
      gap: 20px;
    }

    .hero-top {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
      gap: 24px;
      align-items: start;
    }

    .pill-row {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-bottom: 12px;
    }

    .type-pill,
    .trail-pill {
      display: inline-flex;
      align-items: center;
      padding: 6px 11px;
      border-radius: 999px;
      font-size: 0.78rem;
      font-weight: 780;
    }

    .type-pill {
      background: #eef6f3;
      color: #28624a;
    }

    .trail-pill {
      background: #eef4f8;
      color: #305a72;
    }

    h2 {
      margin: 0;
      color: #102033;
      font-size: clamp(2rem, 3vw, 3rem);
      line-height: 1.02;
    }

    .lead,
    .supporting {
      margin: 10px 0 0;
      color: #5f7182;
      line-height: 1.6;
    }

    .supporting a,
    .subtle-link {
      color: #1d5a77;
      text-decoration: none;
      font-weight: 720;
    }

    .supporting a:hover,
    .subtle-link:hover {
      text-decoration: underline;
    }

    .hero-metrics {
      display: grid;
      gap: 12px;
    }

    .hero-metrics div {
      display: grid;
      gap: 4px;
      padding: 16px 18px;
      border: 1px solid #deeaef;
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.9);
    }

    .hero-metrics span,
    .label {
      color: #617283;
      font-size: 0.75rem;
      font-weight: 780;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .hero-metrics strong,
    .meta-stack strong {
      color: #102033;
      font-size: 1.55rem;
      line-height: 1.1;
    }

    .topic-strip {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .list-grid,
    .publication-list,
    .activity-list {
      display: grid;
      gap: 12px;
    }

    .compact-grid {
      margin-top: 18px;
    }

    .list-card,
    .publication-card,
    .activity-card {
      display: grid;
      gap: 8px;
      padding: 18px;
      border: 1px solid #e0e8ee;
      border-radius: 18px;
      background: #ffffff;
    }

    .list-card,
    .publication-card {
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .list-card:hover,
    .publication-card:hover {
      border-color: #aac8d7;
      box-shadow: 0 16px 32px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
    }

    .compact-card {
      padding: 16px;
    }

    .list-card strong,
    .publication-card strong,
    .activity-card strong {
      color: #102033;
      line-height: 1.35;
    }

    .list-card p,
    .publication-card p,
    .activity-card p,
    .activity-card span {
      margin: 0;
      color: #617283;
      line-height: 1.55;
    }

    .meta-stack {
      display: grid;
      gap: 16px;
      padding: 18px;
      border: 1px solid #e2ebf0;
      border-radius: 18px;
      background: #fbfdfe;
    }

    .publication-head {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 12px;
    }

    .publication-head span {
      color: #617283;
      font-size: 0.88rem;
      white-space: nowrap;
    }

    @media (max-width: 980px) {
      .hero-top {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class PortalResearchUnitDetailPageComponent implements OnInit {
  private readonly portalApi = inject(PortalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly detail = signal<PortalResearchUnitDetail | null>(null);
  readonly allUnits = signal<PortalResearchUnitSummary[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly unitId = Number(this.route.snapshot.paramMap.get('id'));

  readonly pageSubtitle = computed(() => {
    const unit = this.detail()?.unit;
    if (!unit) {
      return 'Ficha pública de unidad';
    }
    return this.locationLabel(unit);
  });
  readonly trailLabel = computed(() => {
    const trail = this.unitTrail();
    return trail.length > 1 ? trail.slice(0, -1).join(' · ') : '';
  });
  readonly fullTrailLabel = computed(() => this.unitTrail().join(' · '));

  ngOnInit(): void {
    this.loading.set(true);
    forkJoin({
      detail: this.portalApi.researchUnit(this.unitId),
      units: this.loadAllUnits()
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ detail, units }) => {
          this.detail.set(detail);
          this.allUnits.set(units);
          this.loading.set(false);
        },
        error: () => {
          this.errorMessage.set('No se pudo cargar la ficha pública de la unidad.');
          this.loading.set(false);
        }
      });
  }

  typeLabel(type: PortalResearchUnitSummary['type']): string {
    return researchUnitTypeLabel(type);
  }

  publicationTypeLabel(type: PublicationSummary['type']): string {
    return publicationTypeLabel(type);
  }

  locationLabel(unit: PortalResearchUnitSummary): string {
    return [unit.city, unit.country].filter(Boolean).join(', ') || 'Ubicación pública pendiente';
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, '/portal/unidades', 'Volver a unidades').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, '/portal/unidades', 'Volver a unidades');
  }

  private unitTrail(): string[] {
    const unit = this.detail()?.unit;
    if (!unit) {
      return [];
    }

    const trail: string[] = [];
    const map = new Map(this.allUnits().map((entry) => [entry.id, entry]));
    let current: PortalResearchUnitSummary | null = unit;
    while (current) {
      trail.unshift(current.name);
      current = current.parentId === null ? null : map.get(current.parentId) ?? null;
    }
    return trail;
  }

  private loadAllUnits() {
    return this.portalApi.researchUnits({ page: 0, size: 100 }).pipe(
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
    );
  }
}
