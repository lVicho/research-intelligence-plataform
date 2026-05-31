import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { PortalResearcherDetail } from '../../core/api/api-models';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { PortalApiService } from '../../core/api/portal-api.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { affiliationTypeLabel, publicationTypeLabel } from '../../shared/utils/display-labels';
import { ResearcherGraphComponent } from '../graph/researcher-graph.component';

@Component({
  selector: 'rip-portal-researcher-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    TagChipComponent,
    ResearcherGraphComponent
  ],
  template: `
    <section class="page portal-researcher-detail">
      <rip-page-header
        [title]="displayName()"
        eyebrow="Portal público"
        [subtitle]="profileSubtitle()"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
      </rip-page-header>

      @if (loading()) {
        <rip-loading-state message="Cargando perfil público" />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else {
        @if (detail(); as detail) {
        <mat-card appearance="outlined" class="hero-card">
          <mat-card-content>
            <div class="hero-top">
              <div>
                @if (detail.orcid) {
                  <a class="orcid-link" [href]="'https://orcid.org/' + detail.orcid" target="_blank" rel="noreferrer">
                    ORCID · {{ detail.orcid }}
                  </a>
                }
                <h2>{{ detail.displayName || detail.fullName }}</h2>
                <p class="lead">{{ primaryAffiliationLabel() }}</p>
              </div>

              <div class="hero-metrics">
                <div>
                  <span>Temas</span>
                  <strong>{{ detail.topics.length }}</strong>
                </div>
                <div>
                  <span>Publicaciones</span>
                  <strong>{{ detail.publications.length }}</strong>
                </div>
                <div>
                  <span>Actividades</span>
                  <strong>{{ detail.activities.length }}</strong>
                </div>
                <div>
                  <span>Coautores</span>
                  <strong>{{ detail.coauthors.length }}</strong>
                </div>
              </div>
            </div>

            <div class="topic-strip">
              @for (topic of detail.topics.slice(0, 8); track topic.name) {
                <rip-tag-chip [label]="topic.name + ' · ' + topic.count" />
              } @empty {
                <span class="muted">El perfil aún no muestra temas públicos destacados.</span>
              }
            </div>
          </mat-card-content>
        </mat-card>

        <section class="content-grid">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Afiliaciones y trayectoria</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="affiliation-list">
                @for (affiliation of detail.affiliations; track affiliation.id) {
                  <div class="affiliation-card">
                    <div class="affiliation-top">
                      <div>
                        <strong>{{ affiliation.researchUnitName || 'Unidad institucional' }}</strong>
                        <p>{{ affiliation.role || affiliationLabel(affiliation.affiliationType) }}</p>
                      </div>
                      <div class="chip-row">
                        @if (affiliation.primaryAffiliation) {
                          <span class="role-chip primary">Principal</span>
                        }
                        @if (affiliation.current) {
                          <span class="role-chip">Actual</span>
                        }
                      </div>
                    </div>
                    <span class="muted">{{ dateRange(affiliation.startDate, affiliation.endDate) }}</span>
                  </div>
                } @empty {
                  <rip-empty-state title="Sin afiliaciones visibles" message="No hay afiliaciones públicas disponibles en este perfil." />
                }
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Colaboración visible</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="coauthor-list">
                @for (coauthor of detail.coauthors.slice(0, 6); track coauthor.name) {
                  <div class="coauthor-card">
                    <div>
                      <strong>{{ coauthor.name }}</strong>
                      <p>{{ coauthor.sharedPublicationCount }} publicaciones compartidas</p>
                    </div>
                    <span class="role-chip" [class.external]="!coauthor.internal">
                      {{ coauthor.internal ? 'Interno' : 'Externo' }}
                    </span>
                  </div>
                } @empty {
                  <rip-empty-state title="Sin coautorías visibles" message="Todavía no hay colaboración pública suficiente para resumirla aquí." />
                }
              </div>
            </mat-card-content>
          </mat-card>
        </section>

        <section class="content-grid">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Publicaciones y trabajos</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="publication-list">
                @for (publication of detail.publications; track publication.id) {
                  <a class="publication-card" [routerLink]="['/portal/publicaciones', publication.id]" [queryParams]="navigationContext.returnQueryParams('Volver al investigador')">
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
                  <rip-empty-state title="Sin publicaciones visibles" message="Este perfil todavía no muestra publicaciones públicas validadas." />
                }
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Participación y actividad</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="activity-list">
                @for (activity of detail.activities; track activity.id) {
                  <div class="activity-card">
                    <strong>{{ activity.title }}</strong>
                    <p>{{ activity.eventName || 'Actividad institucional' }}</p>
                    <span>{{ activity.participationDate || 'Fecha pendiente' }}</span>
                    @if (activity.relatedPublicationId) {
                      <a class="subtle-link" [routerLink]="['/portal/publicaciones', activity.relatedPublicationId]" [queryParams]="navigationContext.returnQueryParams('Volver al investigador')">
                        {{ activity.relatedPublicationTitle || 'Ver publicación relacionada' }}
                      </a>
                    }
                  </div>
                } @empty {
                  <rip-empty-state title="Sin actividad visible" message="No hay actividad pública adicional disponible para este perfil." />
                }
              </div>
            </mat-card-content>
          </mat-card>
        </section>

        @if (showGraph()) {
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Red legible de relaciones</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <rip-researcher-graph [researcherId]="detail.id" [portalView]="true" />
            </mat-card-content>
          </mat-card>
        } @else {
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Relaciones y red</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="graph-summary">
                <div>
                  <span>Nodos visibles</span>
                  <strong>{{ detail.graphSummary.displayedNodes }}</strong>
                </div>
                <div>
                  <span>Relaciones visibles</span>
                  <strong>{{ detail.graphSummary.displayedEdges }}</strong>
                </div>
                <div>
                  <span>Lectura pública</span>
                  <strong>{{ detail.graphSummary.truncated ? 'Demasiado densa para portal' : 'Resumen disponible' }}</strong>
                </div>
              </div>
              <p class="muted">
                La red completa no se muestra aquí para mantener una lectura pública clara del perfil.
              </p>
            </mat-card-content>
          </mat-card>
        }
        } @else {
          <rip-empty-state title="Perfil no disponible" message="Este perfil ya no está visible en el portal público." />
        }
      }
    </section>
  `,
  styles: [`
    .portal-researcher-detail {
      gap: 24px;
    }

    .hero-card {
      border-radius: 28px !important;
      background:
        radial-gradient(circle at top right, rgba(58, 122, 154, 0.16), transparent 30%),
        linear-gradient(165deg, #ffffff, #f7fafc 66%, #f3f8fa 100%);
    }

    .hero-card mat-card-content {
      display: grid;
      gap: 20px;
    }

    .hero-top {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(260px, 340px);
      gap: 24px;
      align-items: start;
    }

    .orcid-link {
      display: inline-flex;
      align-items: center;
      padding: 7px 12px;
      margin-bottom: 12px;
      border-radius: 999px;
      background: #edf5f9;
      color: #1f5e7b;
      font-size: 0.82rem;
      font-weight: 760;
      text-decoration: none;
    }

    .orcid-link:hover,
    .subtle-link:hover {
      text-decoration: underline;
    }

    h2 {
      margin: 0;
      color: #102033;
      font-size: clamp(2rem, 3vw, 3rem);
      line-height: 1.02;
    }

    .lead,
    .subtle-link {
      margin: 10px 0 0;
      color: #5f7182;
      line-height: 1.6;
    }

    .subtle-link {
      color: #1d5a77;
      text-decoration: none;
      font-weight: 720;
    }

    .hero-metrics {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
    }

    .hero-metrics div,
    .graph-summary div {
      display: grid;
      gap: 4px;
      padding: 16px 18px;
      border: 1px solid #deeaef;
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.9);
    }

    .hero-metrics span,
    .graph-summary span {
      color: #617283;
      font-size: 0.75rem;
      font-weight: 780;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .hero-metrics strong,
    .graph-summary strong {
      color: #102033;
      font-size: 1.24rem;
      line-height: 1.1;
    }

    .topic-strip,
    .chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .affiliation-list,
    .coauthor-list,
    .publication-list,
    .activity-list {
      display: grid;
      gap: 12px;
    }

    .affiliation-card,
    .coauthor-card,
    .publication-card,
    .activity-card {
      display: grid;
      gap: 8px;
      padding: 18px;
      border: 1px solid #e0e8ee;
      border-radius: 18px;
      background: #ffffff;
    }

    .publication-card {
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .publication-card:hover {
      border-color: #aac8d7;
      box-shadow: 0 16px 32px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
    }

    .affiliation-top,
    .coauthor-card,
    .publication-head {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 12px;
    }

    .affiliation-card strong,
    .coauthor-card strong,
    .publication-card strong,
    .activity-card strong {
      color: #102033;
      line-height: 1.35;
    }

    .affiliation-card p,
    .coauthor-card p,
    .publication-card p,
    .activity-card p,
    .activity-card span,
    .publication-head span {
      margin: 0;
      color: #617283;
      line-height: 1.55;
    }

    .chip-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      justify-content: flex-end;
    }

    .role-chip {
      display: inline-flex;
      align-items: center;
      padding: 6px 10px;
      border-radius: 999px;
      background: #f2f6f9;
      color: #516676;
      font-size: 0.76rem;
      font-weight: 780;
      white-space: nowrap;
    }

    .role-chip.primary {
      background: #eef6f3;
      color: #28624a;
    }

    .role-chip.external {
      background: #f8f4ef;
      color: #7b5a21;
    }

    .graph-summary {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
      margin-bottom: 16px;
    }

    @media (max-width: 980px) {
      .hero-top {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 720px) {
      .affiliation-top,
      .coauthor-card,
      .publication-head {
        display: grid;
      }

      .chip-row {
        justify-content: flex-start;
      }
    }
  `]
})
export class PortalResearcherDetailPageComponent implements OnInit {
  private readonly portalApi = inject(PortalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly detail = signal<PortalResearcherDetail | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly researcherId = Number(this.route.snapshot.paramMap.get('id'));

  readonly displayName = computed(() => this.detail()?.displayName || this.detail()?.fullName || 'Perfil investigador');
  readonly profileSubtitle = computed(() => this.primaryAffiliationLabel());
  readonly showGraph = computed(() => {
    const graph = this.detail()?.graphSummary;
    return !!graph && !graph.truncated && graph.displayedNodes <= 12 && graph.displayedEdges <= 18;
  });

  ngOnInit(): void {
    this.portalApi.researcher(this.researcherId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.detail.set(detail);
          this.loading.set(false);
        },
        error: () => {
          this.errorMessage.set('No se pudo cargar el perfil público del investigador.');
          this.loading.set(false);
        }
      });
  }

  primaryAffiliationLabel(): string {
    const detail = this.detail();
    const primary = detail?.affiliations.find((affiliation) => affiliation.primaryAffiliation && affiliation.current);
    return primary?.researchUnitName || 'Afiliación institucional pública';
  }

  affiliationLabel(type: string): string {
    return affiliationTypeLabel(type);
  }

  publicationTypeLabel(type: string): string {
    return publicationTypeLabel(type);
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, '/portal/investigadores', 'Volver a investigadores').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, '/portal/investigadores', 'Volver a investigadores');
  }

  dateRange(startDate: string | null, endDate: string | null): string {
    return `${startDate || 'Inicio sin fecha'} · ${endDate || 'Actualidad'}`;
  }
}
