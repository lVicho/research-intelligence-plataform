import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

import { MeActivity, MeDashboard } from '../../core/api/api-models';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import {
  assistantActionQueryValue,
  qualityAssistantDefinitionsForActivity
} from '../../shared/utils/quality-assistant';
import { validationEntityTypeLabel, validationStatusLabel, validationStatusTone } from '../../shared/utils/display-labels';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';

export type ResearcherAssistantPromptId =
  | 'pending'
  | 'changes'
  | 'complete-data'
  | 'profile-summary'
  | 'publication-topics';

interface AssistantPromptOption {
  id: ResearcherAssistantPromptId;
  label: string;
}

interface RelatedActivityItem {
  id: string;
  title: string;
  entityLabel: string;
  helper: string;
  route: string[];
  queryParams: Record<string, string | number>;
  statusLabel: string;
  statusTone: 'neutral' | 'success' | 'warning' | 'danger' | 'info';
}

interface AssistantActionButton {
  id: string;
  label: 'Abrir actividad' | 'Sugerir temas' | 'Generar resumen' | 'Ver calidad de datos';
  route: string[];
  queryParams: Record<string, string | number>;
  appearance: 'flat' | 'stroked';
}

interface AssistantResponse {
  answer: string;
  actionItems: string[];
  relatedActivities: RelatedActivityItem[];
  warnings: string[];
  buttons: AssistantActionButton[];
}

const PROMPT_OPTIONS: AssistantPromptOption[] = [
  { id: 'pending', label: '¿Qué tengo pendiente?' },
  { id: 'changes', label: '¿Qué actividades requieren cambios?' },
  { id: 'complete-data', label: '¿Qué datos debería completar?' },
  { id: 'profile-summary', label: 'Generar resumen de mi perfil' },
  { id: 'publication-topics', label: 'Sugerir temas para mis publicaciones' }
];

@Component({
  selector: 'rip-researcher-assistant-panel',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="assistant-shell" [class.compact-shell]="compact()">
      <div class="assistant-header">
        <div class="assistant-header-copy">
          <div class="assistant-kickers">
            <span class="section-kicker">Asistente personal</span>
            <rip-tag-chip label="Privado" tone="status" />
          </div>
          <h3>¿En qué te ayudo?</h3>
          <p>
            Usa solo tu perfil, tus actividades propias y tus avisos internos. No consulta datos privados de otros investigadores.
          </p>
        </div>

        @if (!compact()) {
          <a
            mat-stroked-button
            routerLink="/app/mi-panel"
            [queryParams]="navigationContext.returnQueryParams('Volver al asistente personal')"
          >
            Mi panel
          </a>
        }
      </div>

      <div class="prompt-chip-list">
        @for (option of promptOptions; track option.id) {
          <button
            mat-stroked-button
            type="button"
            class="prompt-chip"
            [class.active-prompt]="selectedPromptId() === option.id"
            (click)="selectPrompt(option.id)"
          >
            {{ option.label }}
          </button>
        }
      </div>

      @if (response(); as response) {
        <div class="assistant-response">
          <section class="answer-panel">
            <span class="panel-label">Respuesta</span>
            <p>{{ response.answer }}</p>
          </section>

          <div class="assistant-sections" [class.compact-sections]="compact()">
            <section class="response-panel">
              <div class="response-panel-heading">
                <span class="panel-label">Acciones sugeridas</span>
                <strong>{{ response.actionItems.length }}</strong>
              </div>
              <ul class="response-list">
                @for (item of visibleActionItems(response); track item) {
                  <li>{{ item }}</li>
                }
              </ul>
            </section>

            <section class="response-panel">
              <div class="response-panel-heading">
                <span class="panel-label">Actividades relacionadas</span>
                <strong>{{ response.relatedActivities.length }}</strong>
              </div>

              @if (response.relatedActivities.length === 0) {
                <p class="empty-copy">No hay actividades relacionadas en esta vista privada.</p>
              } @else {
                <div class="related-activity-list">
                  @for (activity of visibleRelatedActivities(response); track activity.id) {
                    <a class="related-activity" [routerLink]="activity.route" [queryParams]="activity.queryParams">
                      <div class="related-activity-topline">
                        <div class="related-activity-copy">
                          <span>{{ activity.entityLabel }}</span>
                          <strong>{{ activity.title }}</strong>
                        </div>
                        <rip-status-chip [label]="activity.statusLabel" [tone]="activity.statusTone" />
                      </div>
                      <p>{{ activity.helper }}</p>
                    </a>
                  }
                </div>
              }
            </section>
          </div>

          @if (response.warnings.length > 0) {
            <section class="warning-panel">
              <span class="panel-label">Advertencias</span>
              <ul class="response-list warning-list">
                @for (warning of response.warnings; track warning) {
                  <li>{{ warning }}</li>
                }
              </ul>
            </section>
          }

          <div class="button-row">
            @for (button of visibleButtons(response); track button.id) {
              @if (button.appearance === 'flat') {
                <a mat-flat-button color="primary" [routerLink]="button.route" [queryParams]="button.queryParams">
                  {{ button.label }}
                </a>
              } @else {
                <a mat-stroked-button [routerLink]="button.route" [queryParams]="button.queryParams">
                  {{ button.label }}
                </a>
              }
            }

            @if (compact()) {
              <a
                mat-button
                routerLink="/app/asistente"
                [queryParams]="assistantPageQuery(selectedPromptId())"
              >
                Abrir asistente completo
              </a>
            }
          </div>
        </div>
      }
    </section>
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
    }

    .assistant-shell {
      display: grid;
      gap: 18px;
    }

    .compact-shell {
      gap: 16px;
    }

    .assistant-header,
    .answer-panel,
    .response-panel,
    .warning-panel {
      border: 1px solid #dce5ee;
      border-radius: 18px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(248, 251, 253, 0.96)),
        linear-gradient(90deg, rgba(20, 52, 73, 0.04), rgba(47, 111, 139, 0.02));
    }

    .assistant-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      padding: 18px 20px;
    }

    .assistant-header-copy {
      display: grid;
      gap: 8px;
      min-width: 0;
    }

    .assistant-kickers {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .assistant-header h3 {
      margin: 0;
      color: #142033;
      font-size: 1.18rem;
      line-height: 1.2;
    }

    .assistant-header p,
    .answer-panel p,
    .empty-copy,
    .related-activity p {
      margin: 0;
      color: #607183;
      line-height: 1.58;
    }

    .prompt-chip-list,
    .button-row {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
    }

    .prompt-chip {
      min-height: 38px;
      border-radius: 999px;
      text-align: left;
      white-space: normal;
    }

    .active-prompt {
      border-color: #73a7c1;
      background: #eef7fb;
      color: #144d67;
    }

    .assistant-response {
      display: grid;
      gap: 16px;
    }

    .answer-panel,
    .warning-panel {
      display: grid;
      gap: 10px;
      padding: 18px 20px;
    }

    .assistant-sections {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }

    .compact-sections {
      grid-template-columns: 1fr;
    }

    .response-panel {
      display: grid;
      gap: 14px;
      padding: 18px 20px;
      min-width: 0;
    }

    .response-panel-heading,
    .related-activity-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
    }

    .response-panel-heading strong {
      color: #142033;
      font-size: 1rem;
      line-height: 1.2;
    }

    .panel-label,
    .related-activity-copy span {
      color: #2f6f8f;
      font-size: 0.76rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .response-list {
      display: grid;
      gap: 10px;
      margin: 0;
      padding-left: 18px;
      color: #334155;
      line-height: 1.55;
    }

    .warning-list {
      color: #5a4a2d;
    }

    .related-activity-list {
      display: grid;
      gap: 12px;
    }

    .related-activity {
      display: grid;
      gap: 10px;
      padding: 14px 16px;
      border: 1px solid #dbe5ed;
      border-radius: 16px;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .related-activity:hover {
      border-color: #bad2de;
      box-shadow: 0 14px 28px rgba(20, 32, 51, 0.08);
      transform: translateY(-1px);
    }

    .related-activity-copy {
      display: grid;
      gap: 6px;
      min-width: 0;
    }

    .related-activity-copy strong {
      color: #152437;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    @media (max-width: 980px) {
      .assistant-sections {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 720px) {
      .assistant-header,
      .response-panel-heading,
      .related-activity-topline {
        display: grid;
      }
    }
  `]
})
export class ResearcherAssistantPanelComponent {
  readonly dashboard = input<MeDashboard | null>(null);
  readonly activityInventory = input<MeActivity[]>([]);
  readonly activityInventoryTotal = input(0);
  readonly compact = input(false);
  readonly initialPrompt = input<ResearcherAssistantPromptId | null>(null);

  readonly navigationContext = inject(NavigationContextService);
  readonly promptOptions = PROMPT_OPTIONS;
  readonly selectedPromptId = signal<ResearcherAssistantPromptId>('pending');

  readonly pendingActivities = computed(() =>
    this.activityInventory().filter((activity) => activity.validationStatus === 'PENDING_VALIDATION')
  );
  readonly changesRequestedActivities = computed(() =>
    this.activityInventory().filter((activity) => activity.validationStatus === 'CHANGES_REQUESTED')
  );
  readonly qualityActivities = computed(() =>
    this.activityInventory().filter((activity) => this.hasQualitySignals(activity))
  );
  readonly summaryCandidates = computed(() =>
    this.activityInventory().filter((activity) => this.supportsAssistantAction(activity, 'summary'))
  );
  readonly topicCandidates = computed(() =>
    this.activityInventory().filter((activity) => this.supportsAssistantAction(activity, 'topics'))
  );
  readonly profileActivity = computed(() =>
    this.activityInventory().find((activity) => activity.entityType === 'RESEARCHER') ?? null
  );
  readonly profileGaps = computed(() => {
    const profile = this.dashboard()?.profile;
    if (!profile) {
      return [];
    }
    const gaps: string[] = [];
    if (!profile.email) {
      gaps.push('Completa tu email institucional para que el perfil interno quede bien identificado.');
    }
    if (!profile.orcid) {
      gaps.push('Añade tu ORCID para mejorar trazabilidad, importación y revisión de autoría.');
    }
    if (!profile.primaryAffiliationName) {
      gaps.push('Revisa tu afiliación principal vigente para evitar ambigüedad en el perfil.');
    }
    return gaps;
  });
  readonly inventoryWarning = computed(() => {
    const loaded = this.activityInventory().length;
    const total = this.activityInventoryTotal();
    if (loaded === 0 || total <= loaded) {
      return '';
    }
    return `La respuesta usa ${loaded} registros propios recientes de un total de ${total}.`;
  });
  readonly response = computed<AssistantResponse>(() => {
    switch (this.selectedPromptId()) {
      case 'changes':
        return this.buildChangesResponse();
      case 'complete-data':
        return this.buildDataCompletionResponse();
      case 'profile-summary':
        return this.buildProfileSummaryResponse();
      case 'publication-topics':
        return this.buildPublicationTopicsResponse();
      case 'pending':
      default:
        return this.buildPendingResponse();
    }
  });

  constructor() {
    effect(() => {
      const requestedPrompt = this.initialPrompt();
      this.selectedPromptId.set(this.isPromptId(requestedPrompt) ? requestedPrompt : 'pending');
    }, { allowSignalWrites: true });
  }

  selectPrompt(promptId: ResearcherAssistantPromptId): void {
    this.selectedPromptId.set(promptId);
  }

  visibleActionItems(response: AssistantResponse): string[] {
    return this.compact() ? response.actionItems.slice(0, 3) : response.actionItems;
  }

  visibleRelatedActivities(response: AssistantResponse): RelatedActivityItem[] {
    return this.compact() ? response.relatedActivities.slice(0, 2) : response.relatedActivities;
  }

  visibleButtons(response: AssistantResponse): AssistantActionButton[] {
    return this.compact() ? response.buttons.slice(0, 2) : response.buttons;
  }

  assistantPageQuery(promptId: ResearcherAssistantPromptId): Record<string, string> {
    return {
      prompt: promptId,
      ...this.navigationContext.returnQueryParams('Volver a mi panel')
    };
  }

  private buildPendingResponse(): AssistantResponse {
    const pending = this.pendingActivities();
    const nextEditable = this.activityInventory().find((activity) => activity.submittable) ?? null;
    const warnings = this.baseWarnings();

    return {
      answer: pending.length > 0
        ? `Ahora mismo tienes ${pending.length} actividad${pending.length === 1 ? '' : 'es'} propia${pending.length === 1 ? '' : 's'} en revisión. Tu siguiente paso útil es vigilar observaciones nuevas y dejar listos los registros editables para no frenar el flujo.`
        : 'No tienes actividades pendientes de validación en esta vista privada. El mejor siguiente paso es revisar borradores editables y avisos de calidad antes del próximo envío.',
      actionItems: [
        pending.length > 0
          ? 'Consulta primero las actividades en revisión para comprobar si ya traen comentarios o cambios de estado.'
          : 'Revisa tus borradores y reenvíos para preparar el siguiente envío a validación.',
        nextEditable
          ? `Abre "${nextEditable.title}" y confirma que el contenido editable esté completo antes de enviarlo.`
          : 'No hay un borrador editable prioritario en la vista actual.',
        this.qualityActivities().length > 0
          ? 'Atiende al menos un aviso de calidad hoy para reducir trabajo acumulado en próximos envíos.'
          : 'No se detectan avisos de calidad prioritarios en esta vista.'
      ],
      relatedActivities: this.mapActivities(pending.length > 0 ? pending : this.activityInventory().filter((activity) => activity.submittable)),
      warnings,
      buttons: this.uniqueButtons([
        pending[0] ? this.openActivityButton(pending[0]) : null,
        this.qualityActivities()[0] ? this.qualityButton(this.qualityActivities()[0]) : null,
        this.topicCandidates()[0] ? this.topicsButton(this.topicCandidates()[0]) : null
      ])
    };
  }

  private buildChangesResponse(): AssistantResponse {
    const changes = this.changesRequestedActivities();
    const warnings = this.baseWarnings();
    if (changes.length === 0) {
      warnings.push('No hay actividades con cambios solicitados en la vista actual.');
    }

    return {
      answer: changes.length > 0
        ? `Tienes ${changes.length} actividad${changes.length === 1 ? '' : 'es'} con cambios solicitados. Conviene empezar por la más reciente o por la que tenga comentarios de validación más concretos para reenviarla cuanto antes.`
        : 'No aparecen actividades con cambios solicitados en esta vista privada. Puedes usar el tiempo para mejorar calidad o dejar listo el siguiente envío.',
      actionItems: [
        changes[0]?.validationComment
          ? `Revisa primero el comentario de validación de "${changes[0].title}".`
          : 'Ordena tus revisiones por el comentario más claro o el registro más cercano a reenvío.',
        changes.length > 0
          ? 'Abre cada registro devuelto, corrige lo señalado y vuelve a enviarlo solo cuando el cambio esté completo.'
          : 'No hay reenvíos urgentes activos en este momento.',
        this.profileGaps().length > 0
          ? 'Aprovecha también para cerrar huecos básicos del perfil que pueden provocar nuevas observaciones.'
          : 'Tu perfil básico no muestra huecos críticos en esta sesión.'
      ],
      relatedActivities: this.mapActivities(changes),
      warnings,
      buttons: this.uniqueButtons([
        changes[0] ? this.openActivityButton(changes[0]) : null,
        this.qualityActivities()[0] ? this.qualityButton(this.qualityActivities()[0]) : null,
        this.summaryCandidates()[0] ? this.summaryButton(this.summaryCandidates()[0]) : null
      ])
    };
  }

  private buildDataCompletionResponse(): AssistantResponse {
    const quality = this.qualityActivities();
    const warnings = this.baseWarnings();
    if (quality.length === 0 && this.profileGaps().length === 0) {
      warnings.push('No se detectan huecos prioritarios de calidad con la información cargada en esta sesión.');
    }

    return {
      answer: quality.length > 0 || this.profileGaps().length > 0
        ? `He detectado ${quality.length} registro${quality.length === 1 ? '' : 's'} con avisos de calidad y ${this.profileGaps().length} hueco${this.profileGaps().length === 1 ? '' : 's'} básico${this.profileGaps().length === 1 ? '' : 's'} de perfil. Prioriza primero lo que bloquea revisión o visibilidad.`
        : 'No veo pendientes claros de calidad en esta carga. La mejor estrategia ahora es mantener los nuevos registros completos desde el primer envío.',
      actionItems: [
        ...this.profileGaps().slice(0, 2),
        quality[0]
          ? `Empieza por "${quality[0].title}" porque ya muestra señales accionables en tus recordatorios privados.`
          : 'No hay un registro de calidad claramente prioritario en la vista actual.',
        this.topicCandidates()[0]
          ? 'Añade temas en publicaciones sin clasificar para mejorar descubrimiento y futuras sugerencias.'
          : 'No hay una publicación sin temas marcada como prioritaria en esta sesión.'
      ].slice(0, 4),
      relatedActivities: this.mapActivities(quality),
      warnings,
      buttons: this.uniqueButtons([
        quality[0] ? this.qualityButton(quality[0]) : null,
        quality[0] ? this.openActivityButton(quality[0]) : null,
        this.topicCandidates()[0] ? this.topicsButton(this.topicCandidates()[0]) : null,
        this.summaryCandidates()[0] ? this.summaryButton(this.summaryCandidates()[0]) : null
      ])
    };
  }

  private buildProfileSummaryResponse(): AssistantResponse {
    const summaryCandidate = this.summaryCandidates()[0] ?? this.profileActivity();
    const mainTopics = this.dashboard()?.mainTopics.slice(0, 3).map((topic) => topic.name) ?? [];
    const recentPublicationYears = this.dashboard()?.publicationsByYear
      .filter((item) => item.count > 0)
      .slice(0, 2)
      .map((item) => item.year)
      .filter((year): year is number => year !== null) ?? [];
    const warnings = this.baseWarnings();
    if (!this.summaryCandidates()[0]) {
      warnings.push('No hay un recordatorio activo de resumen pendiente; el botón sirve como acceso al contexto más cercano disponible.');
    }

    const topicsText = mainTopics.length > 0 ? mainTopics.join(', ') : 'tus líneas más recientes registradas';
    const yearsText = recentPublicationYears.length > 0 ? recentPublicationYears.join(' y ') : 'los últimos registros visibles';

    return {
      answer: `Tu resumen debería apoyarse en ${topicsText} y en la producción que aparece en ${yearsText}. Conviene mantenerlo descriptivo, privado hasta revisión y alineado solo con tus propios registros cargados en el sistema.`,
      actionItems: [
        'Usa un tono breve y verificable; evita promesas o afirmaciones que no estén respaldadas por tus registros propios.',
        this.profileGaps().length > 0
          ? 'Completa antes los datos básicos del perfil para que el borrador tenga mejor contexto institucional.'
          : 'Tu perfil básico ya tiene el mínimo necesario para revisar un borrador con menos fricción.',
        summaryCandidate
          ? `Abre "${summaryCandidate.title}" para revisar el contexto del resumen antes de aceptarlo o editarlo.`
          : 'No se encontró un registro claro para lanzar la revisión del resumen en esta sesión.'
      ],
      relatedActivities: this.mapActivities(summaryCandidate ? [summaryCandidate, ...this.summaryCandidates().slice(1)] : this.activityInventory().slice(0, 3)),
      warnings,
      buttons: this.uniqueButtons([
        summaryCandidate ? this.summaryButton(summaryCandidate) : null,
        summaryCandidate ? this.openActivityButton(summaryCandidate) : null,
        this.qualityActivities()[0] ? this.qualityButton(this.qualityActivities()[0]) : null
      ])
    };
  }

  private buildPublicationTopicsResponse(): AssistantResponse {
    const topicCandidate = this.topicCandidates()[0];
    const warnings = this.baseWarnings();
    if (!topicCandidate) {
      warnings.push('No hay una publicación con recordatorio explícito de temas en la carga actual.');
    }

    return {
      answer: topicCandidate
        ? `La prioridad está en las publicaciones sin temas asociados. Añadir una clasificación útil en "${topicCandidate.title}" mejorará descubrimiento, contexto y futuras sugerencias internas.`
        : 'No veo publicaciones propias con un hueco claro de temas en esta sesión. Puedes revisar igualmente tus temas principales para detectar etiquetas redundantes o demasiado amplias.',
      actionItems: [
        topicCandidate
          ? `Abre "${topicCandidate.title}" y revisa la propuesta temática antes de guardarla.`
          : 'Contrasta tus publicaciones recientes con los temas principales visibles en el panel.',
        'Evita duplicar variantes casi iguales; prioriza etiquetas que ya aparezcan de forma consistente en tu producción.',
        this.summaryCandidates()[0]
          ? 'Si una publicación tampoco tiene resumen claro, genera primero el resumen y luego revisa temas.'
          : 'Si detectas títulos demasiado ambiguos, completa el resumen antes de ajustar temas.'
      ],
      relatedActivities: this.mapActivities(topicCandidate ? [topicCandidate, ...this.topicCandidates().slice(1)] : this.activityInventory().filter((activity) => activity.entityType === 'PUBLICATION')),
      warnings,
      buttons: this.uniqueButtons([
        topicCandidate ? this.topicsButton(topicCandidate) : null,
        topicCandidate ? this.openActivityButton(topicCandidate) : null,
        this.qualityActivities()[0] ? this.qualityButton(this.qualityActivities()[0]) : null
      ])
    };
  }

  private mapActivities(activities: MeActivity[]): RelatedActivityItem[] {
    return activities.slice(0, 4).map((activity) => ({
      id: `${activity.entityType}-${activity.entityId}`,
      title: activity.entityType === 'RESEARCHER' ? 'Mi perfil investigador' : activity.title,
      entityLabel: validationEntityTypeLabel(activity.entityType),
      helper: activity.subtitle
        || Object.entries(activity.summaryFields).slice(0, 2).map(([label, value]) => `${label}: ${value}`).join(' · ')
        || 'Abre el registro para revisar su contexto completo.',
      route: this.recordRoute(activity),
      queryParams: this.recordQueryParams(activity),
      statusLabel: validationStatusLabel(activity.validationStatus),
      statusTone: validationStatusTone(activity.validationStatus)
    }));
  }

  private openActivityButton(activity: MeActivity): AssistantActionButton {
    return {
      id: `open-${activity.entityType}-${activity.entityId}`,
      label: 'Abrir actividad',
      route: this.recordRoute(activity),
      queryParams: this.recordQueryParams(activity),
      appearance: 'flat'
    };
  }

  private topicsButton(activity: MeActivity): AssistantActionButton {
    return {
      id: `topics-${activity.entityType}-${activity.entityId}`,
      label: 'Sugerir temas',
      route: ['/app/mis-actividades'],
      queryParams: this.activityQueryParams(activity, 'topics'),
      appearance: 'stroked'
    };
  }

  private summaryButton(activity: MeActivity): AssistantActionButton {
    return {
      id: `summary-${activity.entityType}-${activity.entityId}`,
      label: 'Generar resumen',
      route: ['/app/mis-actividades'],
      queryParams: this.activityQueryParams(activity, 'summary'),
      appearance: 'stroked'
    };
  }

  private qualityButton(activity: MeActivity): AssistantActionButton {
    return {
      id: `quality-${activity.entityType}-${activity.entityId}`,
      label: 'Ver calidad de datos',
      route: ['/app/mis-actividades'],
      queryParams: this.activityQueryParams(activity),
      appearance: 'stroked'
    };
  }

  private activityQueryParams(
    activity: Pick<MeActivity, 'entityType' | 'entityId'>,
    assistantAction?: 'summary' | 'topics'
  ): Record<string, string | number> {
    const params: Record<string, string | number> = {
      entityType: activity.entityType,
      entityId: activity.entityId,
      ...this.navigationContext.returnQueryParams('Volver al asistente personal')
    };
    const actionValue = assistantAction ? assistantActionQueryValue(assistantAction) : null;
    if (actionValue) {
      params['assistantAction'] = actionValue;
    }
    return params;
  }

  private recordRoute(activity: Pick<MeActivity, 'entityType' | 'entityId' | 'researcherId'>): string[] {
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

  private recordQueryParams(activity: Pick<MeActivity, 'entityType' | 'entityId'>): Record<string, string | number> {
    return {
      ...this.navigationContext.returnQueryParams('Volver al asistente personal'),
      focusEntityType: activity.entityType,
      focusEntityId: activity.entityId
    };
  }

  private hasQualitySignals(activity: MeActivity): boolean {
    return qualityAssistantDefinitionsForActivity(activity).length > 0;
  }

  private supportsAssistantAction(activity: MeActivity, actionKind: 'summary' | 'topics'): boolean {
    return qualityAssistantDefinitionsForActivity(activity).some((definition) => definition.actionKind === actionKind);
  }

  private baseWarnings(): string[] {
    const warnings: string[] = [];
    const inventoryWarning = this.inventoryWarning();
    if (inventoryWarning) {
      warnings.push(inventoryWarning);
    }
    if (!this.dashboard()) {
      warnings.push('La respuesta depende del panel privado cargado para esta sesión.');
    }
    return warnings;
  }

  private uniqueButtons(buttons: Array<AssistantActionButton | null>): AssistantActionButton[] {
    const seen = new Set<string>();
    const collected: AssistantActionButton[] = [];
    for (const button of buttons) {
      if (!button || seen.has(button.label)) {
        continue;
      }
      seen.add(button.label);
      collected.push(button);
    }
    return collected;
  }

  private isPromptId(value: ResearcherAssistantPromptId | null): value is ResearcherAssistantPromptId {
    return value !== null && this.promptOptions.some((option) => option.id === value);
  }
}
