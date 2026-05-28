import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { forkJoin } from 'rxjs';

import { MeActivity, MeDashboard } from '../../core/api/api-models';
import { MeApiService } from '../../core/api/me-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { MetricCardComponent } from '../../shared/components/metric-card.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { ResearcherAssistantPanelComponent } from './researcher-assistant-panel.component';
import {
  assistantActionQueryValue,
  qualityAssistantDefinitionsForActivity,
  QualityAssistantIssueCode
} from '../../shared/utils/quality-assistant';
import { validationEntityTypeLabel, validationStatusLabel, validationStatusTone } from '../../shared/utils/display-labels';

interface StatusStat {
  label: string;
  value: number;
  hint: string;
}

interface AttentionCard {
  title: string;
  value: number;
  description: string;
  actionLabel: string;
  tone: 'neutral' | 'info' | 'warning';
  route: string[];
  queryParams?: Record<string, string | number>;
}

interface QualityTask {
  id: string;
  issueCode: QualityAssistantIssueCode;
  title: string;
  entityLabel: string;
  issue: string;
  badge: string;
  detailLabel: string;
  detailLink: string[];
  detailQueryParams?: Record<string, string | number>;
  actionLabel: string;
  actionLink: string[];
  actionQueryParams?: Record<string, string | number>;
  priority: number;
  tone: 'neutral' | 'info' | 'warning';
  contextSummary: string;
}

interface ReminderRule {
  includes: string;
  issue: string;
  badge: string;
  actionLabel: string;
  priority: number;
  tone: 'neutral' | 'info' | 'warning';
}

const QUALITY_REMINDER_RULES: ReminderRule[] = [
  {
    includes: 'Añade DOI',
    issue: 'Falta registrar el DOI de esta publicación.',
    badge: 'Sin DOI',
    actionLabel: 'Añadir DOI',
    priority: 10,
    tone: 'warning'
  },
  {
    includes: 'Añade un resumen',
    issue: 'Falta completar el resumen de esta publicación.',
    badge: 'Sin resumen',
    actionLabel: 'Completar resumen',
    priority: 12,
    tone: 'warning'
  },
  {
    includes: 'Añade al menos un tema',
    issue: 'Este registro todavía no tiene temas asociados.',
    badge: 'Sin temas',
    actionLabel: 'Añadir temas',
    priority: 14,
    tone: 'warning'
  },
  {
    includes: 'Completa tu email institucional',
    issue: 'Tu perfil no tiene email institucional.',
    badge: 'Perfil incompleto',
    actionLabel: 'Completar perfil',
    priority: 18,
    tone: 'info'
  },
  {
    includes: 'Añade tu ORCID',
    issue: 'Tu perfil todavía no muestra ORCID.',
    badge: 'ORCID pendiente',
    actionLabel: 'Añadir ORCID',
    priority: 20,
    tone: 'info'
  },
  {
    includes: 'Revisa tu afiliación principal vigente',
    issue: 'Conviene confirmar tu afiliación principal vigente.',
    badge: 'Afiliación',
    actionLabel: 'Actualizar perfil',
    priority: 24,
    tone: 'info'
  },
  {
    includes: 'Completa la fuente de la publicación',
    issue: 'La publicación no tiene fuente o revista asociada.',
    badge: 'Fuente pendiente',
    actionLabel: 'Completar fuente',
    priority: 26,
    tone: 'warning'
  },
  {
    includes: 'Completa el rol de tu afiliación',
    issue: 'Esta afiliación no tiene rol indicado.',
    badge: 'Afiliación',
    actionLabel: 'Completar afiliación',
    priority: 28,
    tone: 'warning'
  },
  {
    includes: 'Confirma si esta afiliación debe ser principal',
    issue: 'Revisa si esta afiliación debe marcarse como principal.',
    badge: 'Afiliación',
    actionLabel: 'Revisar afiliación',
    priority: 30,
    tone: 'warning'
  },
  {
    includes: 'Vincula la participación con un evento',
    issue: 'La actividad no está vinculada a un evento.',
    badge: 'Evento pendiente',
    actionLabel: 'Vincular evento',
    priority: 32,
    tone: 'warning'
  },
  {
    includes: 'Indica la unidad asociada a la participación',
    issue: 'La actividad no tiene unidad asociada.',
    badge: 'Unidad pendiente',
    actionLabel: 'Actualizar actividad',
    priority: 34,
    tone: 'warning'
  },
  {
    includes: 'Añade una descripción de la participación',
    issue: 'Falta una descripción clara de la actividad.',
    badge: 'Sin descripción',
    actionLabel: 'Completar descripción',
    priority: 36,
    tone: 'warning'
  }
];

@Component({
  selector: 'rip-my-dashboard-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    MetricCardComponent,
    PageHeaderComponent,
    ResearcherAssistantPanelComponent,
    SectionCardComponent,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="page researcher-dashboard">
      <rip-page-header
        title="Mi panel"
        subtitle="Un espacio personal para priorizar cambios, calidad y seguimiento de tus registros."
        eyebrow="Espacio investigador"
        [compact]="true"
      >
        <a mat-button routerLink="/portal">Ver portal</a>
        <a mat-flat-button color="primary" routerLink="/app/mis-actividades">Mis actividades</a>
      </rip-page-header>

      @if (loading()) {
        <rip-loading-state message="Cargando tu espacio personal..." />
      } @else if (errorMessage()) {
        <mat-card appearance="outlined">
          <mat-card-content class="state-card">
            <rip-error-state [message]="errorMessage()" />
            <div class="actions">
              <button mat-flat-button color="primary" type="button" (click)="reload()">Reintentar</button>
            </div>
          </mat-card-content>
        </mat-card>
      } @else {
        @if (dashboard(); as data) {
          <section class="workspace-hero surface-intro">
            <div class="hero-copy">
              <p class="section-kicker">Workspace personal</p>
              <h2>{{ workspaceTitle() }}</h2>
              <p>
                Mantén tus actividades al día, responde a observaciones de validación y mejora la calidad de tus
                registros desde un espacio privado más enfocado.
              </p>
              <div class="hero-actions">
                <a mat-flat-button color="primary" routerLink="/app/mis-actividades">Revisar mis actividades</a>
                <a
                  mat-button
                  routerLink="/app/actividades/nueva"
                  [queryParams]="navigationContext.returnQueryParams('Volver a mi panel')"
                >
                  Añadir participación
                </a>
              </div>
            </div>

            <div class="hero-summary">
              <div class="summary-strip">
                <span class="summary-chip">
                  <strong>{{ activityInventoryTotal() }}</strong>
                  <span>registros propios</span>
                </span>
                <span class="summary-chip">
                  <strong>{{ qualityTasks().length }}</strong>
                  <span>avisos priorizados</span>
                </span>
                <span class="summary-chip">
                  <strong>{{ profileCompletionText() }}</strong>
                  <span>perfil básico</span>
                </span>
              </div>

              <div class="metadata-grid hero-metadata">
                <div class="metadata-item">
                  <span>Email</span>
                  <strong>{{ data.profile.email || 'Pendiente' }}</strong>
                </div>
                <div class="metadata-item">
                  <span>ORCID</span>
                  <strong>{{ data.profile.orcid || 'Pendiente' }}</strong>
                </div>
                <div class="metadata-item">
                  <span>Afiliación principal</span>
                  <strong>{{ data.profile.primaryAffiliationName || 'Pendiente' }}</strong>
                </div>
              </div>
            </div>
          </section>

          <rip-section-card
            title="Asistente personal"
            subtitle="Consulta rápida y privada para decidir tu siguiente paso sin mezclar este espacio con el asistente público."
          >
            <rip-researcher-assistant-panel
              [dashboard]="data"
              [activityInventory]="activityInventory()"
              [activityInventoryTotal]="activityInventoryTotal()"
              [compact]="true"
            />
          </rip-section-card>

          <rip-section-card
            title="Estado de mis actividades"
            subtitle="Vista rápida del estado de tus registros para saber qué puedes editar, reenviar o seguir esperando."
          >
            <div class="metric-grid workspace-metrics">
              @for (stat of statusStats(); track stat.label) {
                <rip-metric-card [label]="stat.label" [value]="stat.value" [hint]="stat.hint" />
              }
            </div>
          </rip-section-card>

          <div class="dashboard-columns">
            <rip-section-card
              title="Requieren mi atención"
              subtitle="Tareas rápidas para avanzar hoy sin perder tiempo en navegación operativa."
            >
              <div class="attention-grid">
                @for (card of attentionCards(); track card.title) {
                  <article class="attention-card" [class.warning-card]="card.tone === 'warning'" [class.info-card]="card.tone === 'info'">
                    <div class="attention-topline">
                      <span>{{ card.title }}</span>
                      <strong>{{ card.value }}</strong>
                    </div>
                    <p>{{ card.description }}</p>
                    <a mat-button [routerLink]="card.route" [queryParams]="card.queryParams">{{ card.actionLabel }}</a>
                  </article>
                }
              </div>
            </rip-section-card>

            <rip-section-card
              title="Calidad de mis datos"
              subtitle="Recordatorios accionables construidos a partir de tus registros propios para que sepas qué corregir y dónde hacerlo."
            >
              @if (qualityLimitNote()) {
                <p class="quality-limit">{{ qualityLimitNote() }}</p>
              }

              @if (qualityTasks().length === 0) {
                <rip-empty-state
                  title="Sin recordatorios accionables"
                  message="No hay avisos prioritarios de calidad en la vista actual de tus registros."
                />
              } @else {
                <div class="quality-list">
                  @for (task of qualityTasks(); track task.id) {
                    <article class="quality-item" [class.priority-item]="task.tone === 'warning'">
                      <div class="quality-topline">
                        <div class="quality-heading">
                          <div class="chip-list">
                            <rip-tag-chip [label]="task.entityLabel" tone="type" />
                            <rip-tag-chip [label]="task.badge" [tone]="task.tone === 'warning' ? 'status' : 'default'" />
                          </div>
                          <h3>
                            <a [routerLink]="task.detailLink" [queryParams]="task.detailQueryParams">{{ task.title }}</a>
                          </h3>
                        </div>
                        <a mat-button [routerLink]="task.detailLink" [queryParams]="task.detailQueryParams">{{ task.detailLabel }}</a>
                      </div>
                      <p>{{ task.issue }}</p>
                      <p class="quality-context">{{ task.contextSummary }}</p>
                      <div class="actions quality-actions">
                        <a
                          mat-flat-button
                          color="primary"
                          [routerLink]="task.actionLink"
                          [queryParams]="task.actionQueryParams"
                        >
                          {{ task.actionLabel }}
                        </a>
                      </div>
                    </article>
                  }
                </div>
              }
            </rip-section-card>
          </div>

          <div class="dashboard-columns secondary-columns">
            <rip-section-card
              title="Publicaciones por año"
              subtitle="Evolución reciente de tu producción vinculada al perfil."
            >
              @if (data.publicationsByYear.length === 0) {
                <rip-empty-state title="Sin publicaciones" message="Aún no hay publicaciones vinculadas a tu perfil." />
              } @else {
                <div class="bars">
                  @for (item of data.publicationsByYear; track item.year) {
                    <div class="bar-row">
                      <span>{{ item.year || 'Sin año' }}</span>
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
              subtitle="Áreas que hoy representan mejor tu producción registrada en el sistema."
            >
              @if (data.mainTopics.length === 0) {
                <rip-empty-state title="Sin temas" message="Añade temas a tus publicaciones para mejorar su descubrimiento." />
              } @else {
                <div class="topic-list">
                  @for (topic of data.mainTopics; track topic.id) {
                    <div class="topic-row">
                      <rip-tag-chip [label]="topic.name" />
                      <strong>{{ topic.count }}</strong>
                    </div>
                  }
                </div>
              }
            </rip-section-card>
          </div>

          <rip-section-card
            title="Actividades recientes"
            subtitle="Tus últimos registros y movimientos relevantes, con acceso directo al contexto de validación."
          >
            @if (data.recentActivities.length === 0) {
              <rip-empty-state title="Sin actividad reciente" message="Tus publicaciones, afiliaciones y perfil aparecerán aquí." />
            } @else {
              <div class="recent-activity-list">
                @for (activity of data.recentActivities; track activity.entityType + '-' + activity.entityId) {
                  <a class="recent-activity-item" [routerLink]="['/app/mis-actividades']" [queryParams]="activityFocusQuery(activity)">
                    <div class="recent-activity-topline">
                      <div class="recent-activity-copy">
                        <span class="recent-activity-type">{{ entityTypeLabel(activity) }}</span>
                        <strong class="item-title">{{ activity.title }}</strong>
                        @if (activity.subtitle) {
                          <p>{{ activity.subtitle }}</p>
                        }
                      </div>
                      <rip-status-chip
                        [label]="statusLabel(activity.validationStatus)"
                        [tone]="statusTone(activity.validationStatus)"
                      />
                    </div>

                    @if (summaryEntries(activity).length > 0) {
                      <div class="recent-activity-meta">
                        @for (entry of summaryEntries(activity); track entry[0]) {
                          <span><strong>{{ entry[0] }}:</strong> {{ entry[1] }}</span>
                        }
                      </div>
                    }
                  </a>
                }
              </div>
            }
          </rip-section-card>
        } @else {
          <rip-empty-state title="Sin panel disponible" message="No se pudo preparar el espacio personal para esta sesión." />
        }
      }
    </section>
  `,
  styles: [`
    .researcher-dashboard {
      gap: 20px;
    }

    .workspace-hero {
      display: grid;
      grid-template-columns: minmax(0, 1.2fr) minmax(340px, 0.8fr);
      gap: 24px;
      align-items: start;
    }

    .hero-copy {
      display: grid;
      gap: 14px;
      min-width: 0;
    }

    .hero-copy h2 {
      margin: 0;
      color: #102033;
      font-size: clamp(1.85rem, 2.6vw, 2.55rem);
      line-height: 1.04;
      letter-spacing: -0.02em;
    }

    .hero-copy p {
      max-width: 760px;
      margin: 0;
    }

    .hero-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }

    .hero-summary {
      display: grid;
      gap: 16px;
    }

    .hero-metadata {
      grid-template-columns: 1fr;
    }

    .workspace-metrics {
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
    }

    .dashboard-columns {
      display: grid;
      grid-template-columns: minmax(320px, 0.86fr) minmax(0, 1.14fr);
      gap: 24px;
      align-items: start;
    }

    .secondary-columns {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .attention-grid {
      display: grid;
      gap: 14px;
    }

    .attention-card {
      display: grid;
      gap: 12px;
      padding: 18px;
      border: 1px solid #dde7ef;
      border-radius: 18px;
      background: linear-gradient(180deg, #ffffff, #f8fbfd);
    }

    .warning-card {
      border-color: #efd18b;
      background: linear-gradient(180deg, #fffdf5, #fff8e8);
    }

    .info-card {
      border-color: #c6d8f0;
      background: linear-gradient(180deg, #f8fbff, #eef5ff);
    }

    .attention-topline {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 12px;
    }

    .attention-topline span {
      color: #4c6074;
      font-size: 0.84rem;
      font-weight: 760;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .attention-topline strong {
      color: #142033;
      font-size: 1.85rem;
      line-height: 1;
    }

    .attention-card p {
      margin: 0;
      color: #526275;
      line-height: 1.55;
    }

    .quality-limit {
      margin: 0;
      color: #68798c;
      font-size: 0.88rem;
      line-height: 1.55;
    }

    .quality-list,
    .bars,
    .topic-list,
    .recent-activity-list {
      display: grid;
      gap: 14px;
    }

    .quality-item,
    .recent-activity-item {
      display: grid;
      gap: 12px;
      padding: 18px;
      border: 1px solid #dde7ef;
      border-radius: 18px;
      background: #ffffff;
      text-decoration: none;
      color: inherit;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .quality-item {
      cursor: default;
    }

    .priority-item {
      border-color: #efcf86;
      background: linear-gradient(180deg, #fffdf6, #fff8eb);
    }

    .recent-activity-item:hover {
      border-color: #bad2de;
      box-shadow: 0 16px 34px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
    }

    .quality-topline,
    .recent-activity-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 14px;
    }

    .quality-heading,
    .recent-activity-copy {
      display: grid;
      gap: 8px;
      min-width: 0;
    }

    .quality-heading h3 {
      margin: 0;
      color: #142033;
      font-size: 1rem;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .quality-heading h3 a {
      text-decoration: none;
    }

    .quality-item p,
    .recent-activity-copy p {
      margin: 0;
      color: #5a6c7d;
      line-height: 1.55;
    }

    .quality-context {
      color: #6a7b8d;
      font-size: 0.9rem;
    }

    .quality-actions {
      justify-content: flex-start;
    }

    .recent-activity-type {
      color: #2f6f8f;
      font-size: 0.78rem;
      font-weight: 780;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    .recent-activity-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 16px;
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.5;
    }

    .bar-row,
    .topic-row {
      display: grid;
      grid-template-columns: minmax(92px, 150px) 1fr 42px;
      gap: 12px;
      align-items: center;
      color: #445369;
      font-size: 0.9rem;
    }

    .topic-row {
      grid-template-columns: 1fr 42px;
    }

    .bar-track {
      height: 10px;
      overflow: hidden;
      border-radius: 999px;
      background: #e8eef5;
    }

    .bar-fill {
      height: 100%;
      min-width: 4px;
      border-radius: 999px;
      background: #1f6f8b;
    }

    @media (max-width: 1180px) {
      .workspace-hero,
      .dashboard-columns,
      .secondary-columns {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 720px) {
      .quality-topline,
      .recent-activity-topline,
      .attention-topline {
        display: grid;
      }

      .bar-row {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class MyDashboardPageComponent implements OnInit {
  private readonly api = inject(MeApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly dashboard = signal<MeDashboard | null>(null);
  readonly activityInventory = signal<MeActivity[]>([]);
  readonly activityInventoryTotal = signal(0);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly statusStats = computed<StatusStat[]>(() => {
    const data = this.dashboard();
    return [
      { label: 'Borradores', value: data?.draftActivitiesCount ?? 0, hint: 'editables antes de enviar' },
      { label: 'Pendientes de validación', value: data?.pendingValidationCount ?? 0, hint: 'en revisión institucional' },
      { label: 'Requieren cambios', value: data?.changesRequestedCount ?? 0, hint: 'necesitan revisión y reenvío' },
      { label: 'Validadas', value: data?.validatedActivitiesCount ?? 0, hint: 'aprobadas y visibles según su contexto' },
      { label: 'Rechazadas', value: data?.rejectedCount ?? 0, hint: 'conviene revisar su historial' }
    ];
  });
  readonly maxYearCount = computed(() => this.dashboard()?.publicationsByYear.reduce((max, item) => Math.max(max, item.count), 0) ?? 0);
  readonly workspaceTitle = computed(() => this.dashboard()?.profile.displayName || this.dashboard()?.profile.fullName || 'Tu espacio investigador');
  readonly profileCompletionText = computed(() => {
    const profile = this.dashboard()?.profile;
    if (!profile) {
      return 'Pendiente';
    }
    const totalFields = 3;
    const completedFields = [profile.email, profile.orcid, profile.primaryAffiliationName].filter((value) => !!value).length;
    return `${completedFields}/${totalFields}`;
  });
  readonly qualityLimitNote = computed(() => {
    const inventorySize = this.activityInventory().length;
    const total = this.activityInventoryTotal();
    if (inventorySize === 0 || total <= inventorySize) {
      return '';
    }
    return `La API actual permite construir esta vista con los ${inventorySize} registros propios más recientes de un total de ${total}.`;
  });
  readonly qualityTasks = computed<QualityTask[]>(() => {
    const tasks: QualityTask[] = [];
    const seen = new Map<string, QualityTask>();

    for (const activity of this.activityInventory()) {
      for (const definition of qualityAssistantDefinitionsForActivity(activity)) {
        const task = this.qualityTask(activity, definition);
        seen.set(task.id, task);
      }
    }

    tasks.push(...seen.values());
    return tasks
      .sort((left, right) => left.priority - right.priority || left.title.localeCompare(right.title, 'es'))
      .slice(0, 8);
  });
  readonly attentionCards = computed<AttentionCard[]>(() => {
    const data = this.dashboard();
    const firstChange = this.qualityTasks().find((task) => task.badge === 'Requiere cambios');
    const editableCount = this.activityInventory().filter((activity) => activity.submittable).length;
    const firstQualityTask = this.qualityTasks()[0];

    return [
      {
        title: 'Cambios solicitados',
        value: data?.changesRequestedCount ?? 0,
        description: firstChange?.issue || 'No tienes registros bloqueados por comentarios de validación en esta vista.',
        actionLabel: (data?.changesRequestedCount ?? 0) > 0 ? 'Revisar cambios' : 'Ver actividades',
        tone: 'warning',
        route: ['/app/mis-actividades'],
        queryParams: firstChange?.actionQueryParams
      },
      {
        title: 'Borradores y reenvíos',
        value: editableCount,
        description: editableCount > 0
          ? 'Tienes registros editables listos para completar o reenviar a validación.'
          : 'No hay borradores ni reenvíos pendientes en la vista actual.',
        actionLabel: 'Abrir mis actividades',
        tone: 'info',
        route: ['/app/mis-actividades']
      },
      {
        title: 'Calidad priorizada',
        value: this.qualityTasks().length,
        description: firstQualityTask?.issue || 'No se detectan recordatorios prioritarios de calidad en este momento.',
        actionLabel: firstQualityTask?.actionLabel || 'Ir a mis actividades',
        tone: firstQualityTask ? firstQualityTask.tone : 'neutral',
        route: ['/app/mis-actividades'],
        queryParams: firstQualityTask?.actionQueryParams ?? firstQualityTask?.detailQueryParams
      }
    ];
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    forkJoin({
      dashboard: this.api.dashboard(),
      activities: this.api.activities({ size: 100 })
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ dashboard, activities }) => {
          this.dashboard.set(dashboard);
          this.activityInventory.set(activities.content);
          this.activityInventoryTotal.set(activities.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.dashboard.set(null);
          this.activityInventory.set([]);
          this.activityInventoryTotal.set(0);
          this.loading.set(false);
          this.errorMessage.set('No se pudo cargar tu panel personal.');
        }
      });
  }

  barWidth(count: number, max: number): number {
    return max === 0 ? 0 : Math.max((count / max) * 100, 4);
  }

  entityTypeLabel(activity: MeActivity): string {
    return validationEntityTypeLabel(activity.entityType);
  }

  statusLabel(status: string): string {
    return validationStatusLabel(status);
  }

  statusTone(status: string): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  summaryEntries(activity: MeActivity): Array<[string, string]> {
    return Object.entries(activity.summaryFields).slice(0, 3);
  }

  activityFocusQuery(activity: Pick<MeActivity, 'entityType' | 'entityId'>): Record<string, string | number> {
    return {
      entityType: activity.entityType,
      entityId: activity.entityId
    };
  }

  private qualityTask(
    activity: MeActivity,
    definition: ReturnType<typeof qualityAssistantDefinitionsForActivity>[number]
  ): QualityTask {
    const focusQuery = this.activityFocusQuery(activity);
    const assistantAction = assistantActionQueryValue(definition.actionKind);
    return {
      id: `quality-${activity.entityType}-${activity.entityId}-${definition.code}`,
      issueCode: definition.code,
      title: this.taskTitle(activity),
      entityLabel: this.entityTypeLabel(activity),
      issue: definition.code === 'changes-requested'
        ? (activity.validationComment || definition.issue)
        : definition.issue,
      badge: definition.badge,
      detailLabel: this.recordViewLabel(activity),
      detailLink: this.editLink(activity),
      detailQueryParams: this.navigationContext.returnQueryParams('Volver a mi panel'),
      actionLabel: definition.actionLabel,
      actionLink: ['/app/mis-actividades'],
      actionQueryParams: assistantAction ? { ...focusQuery, assistantAction } : focusQuery,
      priority: definition.priority,
      tone: definition.tone,
      contextSummary: activity.subtitle || this.summaryEntries(activity).map(([label, value]) => `${label}: ${value}`).join(' · ') || 'Abre el registro para revisar todos los detalles disponibles.'
    };
  }

  private taskTitle(activity: MeActivity): string {
    if (activity.entityType === 'RESEARCHER') {
      return 'Mi perfil investigador';
    }
    return activity.title;
  }

  private viewLabel(activity: MeActivity): string {
    switch (activity.entityType) {
      case 'PUBLICATION':
        return 'Revisar publicación';
      case 'EVENT_PARTICIPATION':
        return 'Revisar actividad';
      case 'RESEARCHER_AFFILIATION':
        return 'Revisar afiliación';
      case 'RESEARCHER':
        return 'Completar perfil';
      default:
        return 'Revisar registro';
    }
  }

  private recordViewLabel(activity: MeActivity): string {
    const label = this.viewLabel(activity);
    if (label.startsWith('Revisar ')) {
      return `Ver ${label.slice('Revisar '.length)}`;
    }
    if (label.startsWith('Completar ')) {
      return `Ver ${label.slice('Completar '.length)}`;
    }
    return label;
  }

  private editLink(activity: Pick<MeActivity, 'entityType' | 'entityId' | 'researcherId'>): string[] {
    if (activity.entityType === 'PUBLICATION') {
      return ['/publications', String(activity.entityId)];
    }
    if (activity.entityType === 'EVENT_PARTICIPATION') {
      return ['/app/actividades', String(activity.entityId)];
    }
    if (activity.entityType === 'RESEARCHER') {
      return ['/researchers', String(activity.entityId)];
    }
    return ['/researchers', String(activity.researcherId ?? activity.entityId)];
  }
}
