import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { catchError, forkJoin, map, Observable, of, startWith } from 'rxjs';

import { AnalyticsApiService } from '../../core/api/analytics-api.service';
import {
  AnalyticsSummary,
  CollaborationOpportunity,
  CollaborationOpportunityResponse,
  DataQualityOverview,
  NamedCount,
  ValidationItem,
  ValidationStatus,
  YearCount
} from '../../core/api/api-models';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { CollaborationOpportunitiesApiService } from '../../core/api/collaboration-opportunities-api.service';
import { DataQualityApiService } from '../../core/api/data-quality-api.service';
import { ValidationApiService } from '../../core/api/validation-api.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { MetricCardComponent } from '../../shared/components/metric-card.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import {
  validationEntityTypeLabel,
  validationStatusLabel,
  validationStatusTone
} from '../../shared/utils/display-labels';

interface DashboardStat {
  label: string;
  value: number | string;
  hint: string;
}

interface SectionState<T> {
  data: T;
  loading: boolean;
  error: string;
}

interface SummaryMetaItem {
  label: string;
  value: number | string;
  helper: string;
}

interface EmergingTopic {
  name: string;
  recentCount: number;
  totalCount: number;
  momentum: 'high' | 'medium' | 'watch';
  detail: string;
}

interface ValidationStatusCount {
  status: ValidationStatus;
  count: number;
}

interface ValidationOverview {
  counts: ValidationStatusCount[];
  pendingItems: ValidationItem[];
  totalTracked: number;
}

@Component({
  selector: 'rip-dashboard-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    MetricCardComponent,
    PageHeaderComponent,
    SectionCardComponent,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Panel institucional"
        subtitle="Supervisa producción, señales temáticas, colaboración, calidad del dato y validación desde el área administrativa."
        eyebrow="Área institucional"
      >
        <a mat-button routerLink="/admin/validacion">Bandeja de validación</a>
        <a mat-button routerLink="/admin/oportunidades-colaboracion">Colaboraciones</a>
        @if (canAccessDataQuality()) {
          <a mat-stroked-button routerLink="/admin/calidad-datos">Calidad del dato</a>
        }
      </rip-page-header>

      <rip-section-card
        title="Resumen institucional"
        subtitle="Lectura ejecutiva del catálogo, la actividad científica y el alcance operativo visible para administración y validación."
        eyebrow="Vista general"
      >
        @if (summaryState().loading) {
          <rip-loading-state message="Cargando resumen institucional..." />
        } @else if (summaryState().error) {
          <rip-error-state [message]="summaryState().error" />
        } @else {
          <div class="surface-intro summary-hero">
            <div class="summary-copy">
              <h2>Panorama administrativo de la investigación institucional</h2>
              <p>
                Este panel consolida producción, estructura investigadora y señales operativas para ayudar a priorizar
                validación, calidad de datos y coordinación entre unidades.
              </p>
            </div>

            <div class="metadata-grid summary-meta-grid">
              @for (item of summaryMeta(); track item.label) {
                <div class="metadata-item">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                  <small>{{ item.helper }}</small>
                </div>
              }
            </div>
          </div>

          <div class="metric-grid">
            @for (stat of stats(); track stat.label) {
              <rip-metric-card [label]="stat.label" [value]="stat.value" [hint]="stat.hint" />
            }
          </div>
        }
      </rip-section-card>

      <div class="dashboard-grid two-up">
        <rip-section-card
          title="Producción por año"
          subtitle="Serie anual de publicaciones para seguir el ritmo de producción institucional."
          eyebrow="Actividad científica"
        >
          @if (summaryState().loading) {
            <rip-loading-state message="Preparando serie temporal..." />
          } @else if (summaryState().error) {
            <rip-error-state [message]="summaryState().error" />
          } @else if (publicationsByYear().length === 0) {
            <rip-empty-state
              title="Sin serie anual disponible"
              message="Todavía no hay publicaciones suficientes para construir la vista temporal."
            />
          } @else {
            <div class="section-intro">
              <p>{{ productionCallout() }}</p>
            </div>

            <div class="bars">
              @for (item of publicationsByYear(); track item.year) {
                <div class="bar-row">
                  <span>{{ item.year }}</span>
                  <div class="bar-track">
                    <div class="bar-fill" [style.width.%]="barWidth(item.count, maxYearCount())"></div>
                  </div>
                  <strong>{{ item.count }}</strong>
                </div>
              }
            </div>
          }
        </rip-section-card>

        <rip-section-card
          title="Temas principales"
          subtitle="Temas con mayor presencia en la producción registrada del resumen analítico actual."
          eyebrow="Cobertura temática"
        >
          @if (summaryState().loading) {
            <rip-loading-state message="Cargando temas principales..." />
          } @else if (summaryState().error) {
            <rip-error-state [message]="summaryState().error" />
          } @else if (topTopics().length === 0) {
            <rip-empty-state
              title="Sin temas clasificados"
              message="Añade o normaliza temas para enriquecer la lectura temática institucional."
            />
          } @else {
            <div class="ranking-list">
              @for (topic of topTopics(); track topic.name) {
                <div class="ranking-row">
                  <div class="ranking-copy">
                    <strong>{{ topic.name }}</strong>
                    <span>{{ topic.count }} publicaciones</span>
                  </div>
                  <div class="ranking-track">
                    <div class="ranking-fill accent" [style.width.%]="barWidth(topic.count, maxTopicCount())"></div>
                  </div>
                  <span class="ranking-share">{{ topicShare(topic.count) }}</span>
                </div>
              }
            </div>
          }
        </rip-section-card>
      </div>

      <div class="dashboard-grid two-up">
        <rip-section-card
          title="Tendencias emergentes"
          subtitle="Señales derivadas de publicaciones recientes del resumen para detectar focos con tracción reciente."
          eyebrow="Lectura exploratoria"
        >
          @if (summaryState().loading) {
            <rip-loading-state message="Analizando señales recientes..." />
          } @else if (summaryState().error) {
            <rip-error-state [message]="summaryState().error" />
          } @else if (emergingTopics().length === 0) {
            <rip-empty-state
              title="Sin señales recientes suficientes"
              message="Cuando haya publicaciones recientes con temas asociados aparecerán aquí posibles líneas emergentes."
            />
          } @else {
            <div class="trend-grid">
              @for (topic of emergingTopics(); track topic.name) {
                <article class="trend-card">
                  <div class="trend-topline">
                    <strong>{{ topic.name }}</strong>
                    <rip-status-chip [label]="momentumLabel(topic.momentum)" [tone]="momentumTone(topic.momentum)" />
                  </div>
                  <p>{{ topic.detail }}</p>
                  <div class="trend-metrics">
                    <div class="metadata-item compact-meta">
                      <span>Recientes</span>
                      <strong>{{ topic.recentCount }}</strong>
                    </div>
                    <div class="metadata-item compact-meta">
                      <span>Total resumen</span>
                      <strong>{{ topic.totalCount || 'Base corta' }}</strong>
                    </div>
                  </div>
                </article>
              }
            </div>
          }
        </rip-section-card>

        <rip-section-card
          title="Colaboraciones destacadas"
          subtitle="Pares de unidades con afinidad temática o complementariedad útiles para conversaciones institucionales."
          eyebrow="Coordinación entre unidades"
        >
          @if (collaborationState().loading) {
            <rip-loading-state message="Cargando colaboraciones destacadas..." />
          } @else if (collaborationState().error) {
            <rip-error-state [message]="collaborationState().error" />
          } @else if (collaborationHighlights().length === 0) {
            <rip-empty-state
              title="Sin oportunidades visibles"
              message="Amplía el horizonte temporal en la vista completa para detectar más pares potenciales."
            />
          } @else {
            <div class="item-list">
              @for (item of collaborationHighlights(); track item.id) {
                <article class="item-row collaboration-card">
                  <div class="collaboration-header">
                    <div>
                      <strong class="item-title">{{ item.unitA.name }} · {{ item.unitB.name }}</strong>
                      <p class="item-description">{{ item.explanation }}</p>
                    </div>
                    <div class="score-badge">
                      <span>Puntuación</span>
                      <strong>{{ item.score }}</strong>
                    </div>
                  </div>

                  <div class="chip-list">
                    @for (topic of item.sharedTopics.slice(0, 3); track topic) {
                      <rip-tag-chip [label]="topic" tone="status" />
                    }
                    @for (topic of item.complementaryTopics.slice(0, 2); track topic) {
                      <rip-tag-chip [label]="topic" tone="type" />
                    }
                  </div>

                  <div class="item-meta">
                    <span>{{ item.existingCollaborationCount }} colaboraciones previas</span>
                    <span>{{ percentLabel(item.confidence) }} de confianza</span>
                    <span>{{ item.fromYear }} - {{ item.toYear }}</span>
                  </div>
                </article>
              }
            </div>

            <div class="card-actions">
              <a mat-button routerLink="/admin/oportunidades-colaboracion">Abrir vista completa</a>
            </div>
          }
        </rip-section-card>
      </div>

      <div class="dashboard-grid two-up">
        <rip-section-card
          title="Actividad por unidad"
          subtitle="Distribución de publicaciones por unidad para detectar concentración y capacidad activa."
          eyebrow="Desglose organizativo"
        >
          @if (summaryState().loading) {
            <rip-loading-state message="Cargando actividad por unidad..." />
          } @else if (summaryState().error) {
            <rip-error-state [message]="summaryState().error" />
          } @else if (topResearchUnits().length === 0) {
            <rip-empty-state
              title="Sin actividad por unidad"
              message="Asocia publicaciones a unidades para mostrar su peso relativo en el panel."
            />
          } @else {
            <div class="bars">
              @for (item of topResearchUnits(); track item.id ?? item.name) {
                <div class="bar-row wide">
                  <span>{{ item.name }}</span>
                  <div class="bar-track">
                    <div class="bar-fill muted-fill" [style.width.%]="barWidth(item.count, maxResearchUnitCount())"></div>
                  </div>
                  <strong>{{ item.count }}</strong>
                </div>
              }
            </div>
          }
        </rip-section-card>

        <rip-section-card
          title="Estado de validación"
          subtitle="Seguimiento de la carga operativa y de los estados que atraviesan las actividades institucionales."
          eyebrow="Control editorial"
        >
          @if (validationState().loading) {
            <rip-loading-state message="Cargando estado de validación..." />
          } @else if (validationState().error) {
            <rip-error-state [message]="validationState().error" />
          } @else {
            <div class="section-intro">
              <p>Se monitorizan {{ validationState().data.totalTracked }} actividades con estado registrado en el flujo actual.</p>
            </div>

            <div class="validation-overview">
              @for (item of validationCounts(); track item.status) {
                <div class="validation-pill">
                  <rip-status-chip [label]="validationLabel(item.status)" [tone]="validationTone(item.status)" />
                  <strong>{{ item.count }}</strong>
                </div>
              }
            </div>

            @if (validationState().data.pendingItems.length === 0) {
              <rip-empty-state
                title="Sin elementos pendientes"
                message="La bandeja de validación no muestra actividades pendientes en este momento."
              />
            } @else {
              <div class="item-list">
                @for (item of validationState().data.pendingItems; track item.entityType + '-' + item.entityId) {
                  <a class="item-row interactive" routerLink="/admin/validacion">
                    <strong class="item-title">{{ item.title }}</strong>
                    <div class="item-meta">
                      <span>{{ validationEntityLabel(item.entityType) }} #{{ item.entityId }}</span>
                      <span>{{ item.researchUnitName || 'Sin unidad asociada' }}</span>
                      <span>{{ shortDate(item.submittedAt) }}</span>
                    </div>
                  </a>
                }
              </div>
            }

            <div class="card-actions">
              <a mat-button routerLink="/admin/validacion">Gestionar bandeja</a>
            </div>
          }
        </rip-section-card>
      </div>

      <rip-section-card
        title="Calidad del dato"
        subtitle="Incidencias activas que pueden afectar trazabilidad, normalización, publicación o exposición de la información institucional."
        eyebrow="Salud del catálogo"
      >
        @if (dataQualityState().loading) {
          <rip-loading-state message="Cargando indicadores de calidad del dato..." />
        } @else if (dataQualityState().error) {
          <rip-error-state [message]="dataQualityState().error" />
        } @else {
          <div class="metric-grid">
            @for (card of dataQualityCards(); track card.label) {
              <rip-metric-card [label]="card.label" [value]="card.value" [hint]="card.hint" />
            }
          </div>

          @if (priorityIssues().length === 0) {
            <rip-empty-state
              title="Sin incidencias abiertas"
              message="No hay señales de calidad del dato activas en el snapshot actual."
            />
          } @else {
            <div class="issue-grid">
              @for (issue of priorityIssues(); track issue.id) {
                <article class="issue-card">
                  <div class="issue-header">
                    <div>
                      <div class="chip-list">
                        <rip-status-chip [label]="severityLabel(issue.severity)" [tone]="severityTone(issue.severity)" />
                        <rip-tag-chip [label]="dataQualityEntityLabel(issue.entityType)" tone="type" />
                      </div>
                      <strong>{{ issue.label }}</strong>
                    </div>
                    <span class="issue-count">{{ issue.count }}</span>
                  </div>
                  <p>{{ issue.description }}</p>
                  <div class="issue-links">
                    @for (record of issue.affectedRecords.slice(0, 2); track record.path + record.label) {
                      <a [routerLink]="record.path">{{ record.label }}</a>
                    }
                  </div>
                </article>
              }
            </div>
          }

          <div class="card-actions">
            @if (canAccessDataQuality()) {
              <a mat-button routerLink="/admin/calidad-datos">Abrir detalle de calidad</a>
            } @else {
              <span class="muted-note">El detalle completo de calidad del dato permanece restringido a perfiles de administración.</span>
            }
          </div>
        }
      </rip-section-card>
    </section>
  `,
  styles: [`
    .dashboard-grid {
      display: grid;
      gap: 24px;
    }

    .two-up {
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    }

    .summary-hero,
    .summary-copy,
    .section-intro,
    .trend-grid,
    .trend-card,
    .trend-metrics,
    .collaboration-card,
    .validation-overview,
    .issue-grid,
    .issue-card {
      display: grid;
      gap: 16px;
    }

    .summary-meta-grid {
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    }

    .metadata-item small {
      color: #667487;
      line-height: 1.45;
    }

    .section-intro p,
    .trend-card p,
    .item-description,
    .issue-card p {
      margin: 0;
      color: #667487;
      line-height: 1.6;
    }

    .bars,
    .ranking-list {
      display: grid;
      gap: 12px;
    }

    .bar-row {
      display: grid;
      grid-template-columns: minmax(92px, 160px) 1fr 44px;
      gap: 12px;
      align-items: center;
      font-size: 0.9rem;
    }

    .bar-row.wide {
      grid-template-columns: minmax(180px, 280px) 1fr 44px;
    }

    .bar-track,
    .ranking-track {
      height: 10px;
      overflow: hidden;
      border-radius: 999px;
      background: #e8eef5;
    }

    .bar-fill,
    .ranking-fill {
      height: 100%;
      border-radius: 999px;
      background: #1f6f8b;
    }

    .ranking-fill.accent {
      background: #2f7d64;
    }

    .bar-fill.muted-fill {
      background: #5a7fc7;
    }

    .ranking-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(120px, 1fr) auto;
      gap: 14px;
      align-items: center;
    }

    .ranking-copy {
      display: grid;
      gap: 4px;
      min-width: 0;
    }

    .ranking-copy strong,
    .trend-topline strong,
    .issue-card strong {
      color: #142033;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .ranking-copy span,
    .ranking-share {
      color: #667487;
      font-size: 0.88rem;
    }

    .trend-grid {
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    }

    .trend-card {
      padding: 18px;
      border: 1px solid #dbe6ef;
      border-radius: 18px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(248, 251, 253, 0.96)),
        linear-gradient(90deg, rgba(31, 111, 139, 0.05), rgba(45, 140, 120, 0.04));
    }

    .trend-topline,
    .collaboration-header,
    .issue-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 14px;
    }

    .compact-meta {
      gap: 4px;
      min-width: 0;
      padding: 12px 14px;
    }

    .collaboration-card {
      border-radius: 18px;
    }

    .score-badge {
      display: grid;
      min-width: 100px;
      padding: 12px 14px;
      border: 1px solid #d8e4ed;
      border-radius: 16px;
      background: #ffffff;
      text-align: right;
    }

    .score-badge span,
    .issue-count {
      color: #5f7384;
      font-size: 0.78rem;
      font-weight: 760;
      letter-spacing: 0.03em;
      text-transform: uppercase;
    }

    .score-badge strong {
      color: #102033;
      font-size: 1.65rem;
      line-height: 1;
    }

    .validation-overview {
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    }

    .validation-pill {
      display: grid;
      gap: 10px;
      padding: 16px;
      border: 1px solid #dce5ee;
      border-radius: 16px;
      background: linear-gradient(180deg, #ffffff, #f8fbfd);
    }

    .validation-pill strong,
    .issue-count {
      color: #102033;
      font-size: 1.4rem;
      line-height: 1;
    }

    .issue-grid {
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    }

    .issue-card {
      padding: 18px;
      border: 1px solid #dce5ee;
      border-radius: 18px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(248, 251, 253, 0.96)),
        linear-gradient(90deg, rgba(31, 111, 139, 0.05), rgba(45, 140, 120, 0.04));
    }

    .issue-links {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 14px;
    }

    .issue-links a {
      color: #1d5773;
      font-size: 0.88rem;
      font-weight: 700;
      text-decoration: none;
    }

    .issue-links a:hover {
      text-decoration: underline;
    }

    .card-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 10px;
      flex-wrap: wrap;
    }

    .muted-note {
      color: #667487;
      font-size: 0.9rem;
      line-height: 1.5;
    }

    @media (max-width: 720px) {
      .bar-row,
      .bar-row.wide,
      .ranking-row {
        grid-template-columns: 1fr 44px;
      }

      .bar-track,
      .ranking-track {
        grid-column: 1 / -1;
        order: 3;
      }

      .trend-topline,
      .collaboration-header,
      .issue-header {
        display: grid;
      }

      .score-badge {
        min-width: 0;
        text-align: left;
      }

      .card-actions {
        justify-content: flex-start;
      }
    }
  `]
})
export class DashboardPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly collaborationApi = inject(CollaborationOpportunitiesApiService);
  private readonly dataQualityApi = inject(DataQualityApiService);
  private readonly validationApi = inject(ValidationApiService);
  private readonly auth = inject(AuthStateService);

  readonly summaryState = this.createSectionState(
    this.analyticsApi.getSummary(),
    this.emptySummary(),
    'No se pudo cargar el resumen institucional.'
  );
  readonly collaborationState = this.createSectionState(
    this.collaborationApi.getOpportunities({
      fromYear: new Date().getFullYear() - 4,
      toYear: new Date().getFullYear(),
      mode: 'BALANCED',
      limit: 3
    }),
    this.emptyCollaborationResponse(),
    'No se pudieron cargar las colaboraciones destacadas.'
  );
  readonly dataQualityState = this.createSectionState(
    this.dataQualityApi.overview(),
    this.emptyDataQualityOverview(),
    'No se pudo cargar el estado de calidad del dato.'
  );
  readonly validationState = this.createSectionState(
    this.buildValidationOverviewRequest(),
    this.emptyValidationOverview(),
    'No se pudo cargar el estado de validación.'
  );

  readonly canAccessDataQuality = computed(() => this.auth.hasAnyRole(['ADMIN']));
  readonly summary = computed(() => this.summaryState().data);
  readonly publicationsByYear = computed<YearCount[]>(() => this.summary().publicationsByYear);
  readonly topTopics = computed<NamedCount[]>(() => this.summary().topTopicsByPublicationCount.slice(0, 8));
  readonly topResearchUnits = computed<NamedCount[]>(() => this.summary().publicationsByResearchUnit.slice(0, 8));
  readonly collaborationHighlights = computed<CollaborationOpportunity[]>(() => this.collaborationState().data.opportunities);
  readonly validationCounts = computed<ValidationStatusCount[]>(() => this.validationState().data.counts);
  readonly priorityIssues = computed(() =>
    [...this.dataQualityState().data.issues]
      .sort((left, right) => this.severityRank(left.severity) - this.severityRank(right.severity) || right.count - left.count)
      .slice(0, 4)
  );

  readonly stats = computed<DashboardStat[]>(() => {
    const summary = this.summary();
    return [
      { label: 'Unidades', value: summary.totalResearchUnits, hint: 'estructura institucional visible' },
      { label: 'Investigadores', value: summary.totalResearchers, hint: 'perfiles registrados en catálogo' },
      { label: 'En activo', value: summary.activeResearchers, hint: 'investigadores con actividad vigente' },
      { label: 'Publicaciones', value: summary.totalPublications, hint: 'producción registrada en el resumen' }
    ];
  });

  readonly summaryMeta = computed<SummaryMetaItem[]>(() => {
    const years = this.publicationsByYear();
    const latestYear = years[years.length - 1];

    return [
      {
        label: 'Último año visible',
        value: latestYear?.year ?? 'Sin dato',
        helper: latestYear ? `${latestYear.count} publicaciones registradas en ese año.` : 'La serie anual todavía no tiene datos.'
      },
      {
        label: 'Temas destacados',
        value: this.topTopics().length,
        helper: this.topTopics().length > 0 ? 'Temas principales ya clasificados en el resumen.' : 'Faltan temas para una lectura temática más rica.'
      },
      {
        label: 'Unidades con producción',
        value: this.topResearchUnits().length,
        helper: this.topResearchUnits().length > 0 ? 'Unidades con actividad visible en este corte analítico.' : 'No hay actividad agrupada por unidad.'
      }
    ];
  });

  readonly productionCallout = computed(() => {
    const years = this.publicationsByYear();
    if (years.length < 2) {
      return 'La serie temporal todavía es corta; se ampliará conforme entren más publicaciones al catálogo.';
    }

    const latest = years[years.length - 1];
    const previous = years[years.length - 2];
    const delta = latest.count - previous.count;

    if (delta > 0) {
      return `${latest.year} registra ${delta} publicaciones más que ${previous.year}.`;
    }
    if (delta < 0) {
      return `${latest.year} registra ${Math.abs(delta)} publicaciones menos que ${previous.year}.`;
    }
    return `${latest.year} mantiene el mismo volumen que ${previous.year}.`;
  });

  readonly emergingTopics = computed<EmergingTopic[]>(() => {
    const recentCounts = new Map<string, { name: string; count: number }>();
    const totalByTopic = new Map(
      this.summary().topTopicsByPublicationCount.map((topic) => [topic.name.toLocaleLowerCase('es-ES'), topic.count] as const)
    );

    for (const publication of this.summary().recentPublications) {
      for (const topic of publication.topics) {
        const key = topic.toLocaleLowerCase('es-ES');
        const current = recentCounts.get(key);
        recentCounts.set(key, {
          name: topic,
          count: (current?.count ?? 0) + 1
        });
      }
    }

    const derivedTopics = Array.from(recentCounts.values())
      .map((item) => {
        const totalCount = totalByTopic.get(item.name.toLocaleLowerCase('es-ES')) ?? 0;
        const ratio = totalCount === 0 ? 1 : item.count / totalCount;
        const momentum = item.count >= 3 || ratio >= 0.5
          ? 'high'
          : item.count >= 2 || ratio >= 0.3
            ? 'medium'
            : 'watch';

        return {
          name: item.name,
          recentCount: item.count,
          totalCount,
          momentum,
          detail: totalCount > 0
            ? `${item.count} apariciones en publicaciones recientes sobre ${totalCount} referencias del resumen actual.`
            : `${item.count} apariciones recientes; conviene validar si se consolida como línea propia.`
        } satisfies EmergingTopic;
      })
      .sort((left, right) => right.recentCount - left.recentCount || right.totalCount - left.totalCount)
      .slice(0, 3);

    if (derivedTopics.length > 0) {
      return derivedTopics;
    }

    return this.topTopics()
      .slice(0, 3)
      .map((topic) => ({
        name: topic.name,
        recentCount: 0,
        totalCount: topic.count,
        momentum: 'watch',
        detail: 'Tema relevante en el resumen, pendiente de una serie reciente más rica para estimar tendencia.'
      }));
  });

  readonly dataQualityCards = computed<DashboardStat[]>(() => {
    const summary = this.dataQualityState().data.summary;
    return [
      { label: 'Incidencias abiertas', value: summary.totalOpenIssues, hint: 'categorías activas en el snapshot' },
      { label: 'Críticas', value: summary.criticalIssues, hint: 'prioridad operativa inmediata' },
      { label: 'Registros afectados', value: summary.affectedRecords, hint: 'elementos con revisión pendiente' },
      { label: 'Última revisión', value: this.shortDate(summary.lastReviewAt), hint: 'fecha del corte de calidad' }
    ];
  });

  readonly maxYearCount = computed(() => this.maxCount(this.publicationsByYear()));
  readonly maxTopicCount = computed(() => this.maxCount(this.topTopics()));
  readonly maxResearchUnitCount = computed(() => this.maxCount(this.topResearchUnits()));

  barWidth(count: number, max: number): number {
    return max === 0 ? 0 : Math.max((count / max) * 100, 4);
  }

  topicShare(count: number): string {
    const total = this.topTopics().reduce((sum, topic) => sum + topic.count, 0);
    if (total === 0) {
      return '0 %';
    }
    return `${Math.round((count / total) * 100)} %`;
  }

  momentumLabel(momentum: EmergingTopic['momentum']): string {
    switch (momentum) {
      case 'high':
        return 'Tracción alta';
      case 'medium':
        return 'En consolidación';
      default:
        return 'A vigilar';
    }
  }

  momentumTone(momentum: EmergingTopic['momentum']): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    switch (momentum) {
      case 'high':
        return 'success';
      case 'medium':
        return 'info';
      default:
        return 'warning';
    }
  }

  validationLabel(status: ValidationStatus): string {
    return validationStatusLabel(status);
  }

  validationTone(status: ValidationStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  validationEntityLabel(entityType: ValidationItem['entityType']): string {
    return validationEntityTypeLabel(entityType);
  }

  severityLabel(severity: DataQualityOverview['issues'][number]['severity']): string {
    switch (severity) {
      case 'CRITICAL':
        return 'Crítica';
      case 'HIGH':
        return 'Alta';
      case 'MEDIUM':
        return 'Media';
      default:
        return 'Baja';
    }
  }

  severityTone(severity: DataQualityOverview['issues'][number]['severity']): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    switch (severity) {
      case 'CRITICAL':
        return 'danger';
      case 'HIGH':
        return 'warning';
      case 'MEDIUM':
        return 'info';
      default:
        return 'neutral';
    }
  }

  dataQualityEntityLabel(entityType: DataQualityOverview['issues'][number]['entityType']): string {
    switch (entityType) {
      case 'PUBLICATION':
        return 'Publicación';
      case 'RESEARCHER':
        return 'Investigador';
      case 'TOPIC':
        return 'Tema';
      case 'EVENT_PARTICIPATION':
        return 'Participación';
      case 'VENUE':
        return 'Canal';
      case 'EVENT':
        return 'Evento';
      default:
        return 'Autor externo';
    }
  }

  percentLabel(value: number): string {
    return `${Math.round(value * 100)} %`;
  }

  shortDate(value: string | null): string {
    if (!value) {
      return 'Sin fecha';
    }

    return new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium' }).format(new Date(value));
  }

  private createSectionState<T>(request$: Observable<T>, initialData: T, errorMessage: string) {
    return toSignal(
      request$.pipe(
        map((data) => ({ data, loading: false, error: '' } satisfies SectionState<T>)),
        startWith({ data: initialData, loading: true, error: '' } satisfies SectionState<T>),
        catchError(() => of({ data: initialData, loading: false, error: errorMessage } satisfies SectionState<T>))
      ),
      { initialValue: { data: initialData, loading: true, error: '' } satisfies SectionState<T> }
    );
  }

  private buildValidationOverviewRequest(): Observable<ValidationOverview> {
    return forkJoin({
      pending: this.validationApi.inbox({ status: 'PENDING_VALIDATION', size: 5, sort: 'submittedAt,desc' }),
      draft: this.validationApi.inbox({ status: 'DRAFT', size: 1 }),
      changesRequested: this.validationApi.inbox({ status: 'CHANGES_REQUESTED', size: 1 }),
      rejected: this.validationApi.inbox({ status: 'REJECTED', size: 1 }),
      validated: this.validationApi.inbox({ status: 'VALIDATED', size: 1 })
    }).pipe(
      map(({ pending, draft, changesRequested, rejected, validated }) => {
        const counts: ValidationStatusCount[] = [
          { status: 'PENDING_VALIDATION', count: pending.totalElements },
          { status: 'DRAFT', count: draft.totalElements },
          { status: 'CHANGES_REQUESTED', count: changesRequested.totalElements },
          { status: 'REJECTED', count: rejected.totalElements },
          { status: 'VALIDATED', count: validated.totalElements }
        ];

        return {
          counts,
          pendingItems: pending.content,
          totalTracked: counts.reduce((sum, item) => sum + item.count, 0)
        } satisfies ValidationOverview;
      })
    );
  }

  private maxCount(items: Array<YearCount | NamedCount>): number {
    return items.reduce((max, item) => Math.max(max, item.count), 0);
  }

  private severityRank(severity: DataQualityOverview['issues'][number]['severity']): number {
    switch (severity) {
      case 'CRITICAL':
        return 0;
      case 'HIGH':
        return 1;
      case 'MEDIUM':
        return 2;
      default:
        return 3;
    }
  }

  private emptySummary(): AnalyticsSummary {
    return {
      totalResearchUnits: 0,
      totalResearchers: 0,
      activeResearchers: 0,
      totalPublications: 0,
      publicationsByYear: [],
      publicationsByType: [],
      publicationsByStatus: [],
      publicationsByResearchUnit: [],
      topResearchersByPublicationCount: [],
      topTopicsByPublicationCount: [],
      recentPublications: [],
      researchersByResearchUnitType: []
    };
  }

  private emptyCollaborationResponse(): CollaborationOpportunityResponse {
    return {
      generatedAt: '',
      visibilityScope: 'Uso interno administrativo.',
      minYear: 0,
      maxYear: 0,
      total: 0,
      opportunities: []
    };
  }

  private emptyDataQualityOverview(): DataQualityOverview {
    return {
      summary: {
        totalOpenIssues: 0,
        criticalIssues: 0,
        categoriesWithFindings: 0,
        affectedRecords: 0,
        lastReviewAt: ''
      },
      issues: []
    };
  }

  private emptyValidationOverview(): ValidationOverview {
    return {
      counts: [
        { status: 'PENDING_VALIDATION', count: 0 },
        { status: 'DRAFT', count: 0 },
        { status: 'CHANGES_REQUESTED', count: 0 },
        { status: 'REJECTED', count: 0 },
        { status: 'VALIDATED', count: 0 }
      ],
      pendingItems: [],
      totalTracked: 0
    };
  }
}
