import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AcademicMasterDataService } from '../../core/api/academic-master-data.service';
import { AiSuggestion, MeActivity, MeActivityDetail, PageResponse, ValidationEntityType, ValidationStatus } from '../../core/api/api-models';
import { AiSuggestionsApiService } from '../../core/api/ai-suggestions-api.service';
import { MeApiService } from '../../core/api/me-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { AiSuggestionReviewPanelComponent } from '../../shared/components/ai-suggestion-review-panel.component';
import { AuditHistoryPanelComponent } from '../../shared/components/audit-history-panel.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import {
  qualityAssistantDefinitionsForActivity,
  QualityAssistantActionKind,
  QualityAssistantIssueCode,
  supportsAiSuggestionForQualityIssue
} from '../../shared/utils/quality-assistant';
import { validationEntityTypeLabel, validationStatusLabel, validationStatusTone } from '../../shared/utils/display-labels';

type StatusFilter = ValidationStatus | 'all';
type TypeFilter = ValidationEntityType | 'all';

interface ActivityTarget {
  entityType: ValidationEntityType;
  entityId: number;
}

interface AssistantTask {
  id: string;
  issueCode: QualityAssistantIssueCode;
  badge: string;
  issue: string;
  tone: 'neutral' | 'info' | 'warning';
  actionKind: QualityAssistantActionKind;
  actionLabel: string;
  supportsSuggestion: boolean;
}

@Component({
  selector: 'rip-my-activities-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    AiSuggestionReviewPanelComponent,
    AuditHistoryPanelComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Mis actividades"
        subtitle="Revisa tus registros, entiende el estado de validación y actúa sobre cada caso con menos ruido operativo."
        eyebrow="Actividad personal"
        [compact]="true"
      >
        <a mat-button routerLink="/app/mi-panel">Mi panel</a>
        <a
          mat-flat-button
          color="primary"
          routerLink="/app/actividades/nueva"
          [queryParams]="navigationContext.returnQueryParams('Volver a mis actividades')"
        >
          Añadir participación
        </a>
      </rip-page-header>

      <div class="toolbar-meta">
        <p>Selecciona un registro para ver su estado, los comentarios del equipo validador y las acciones disponibles.</p>
        <div class="summary-strip">
          <span class="summary-chip">
            <strong>{{ result()?.totalElements || 0 }}</strong>
            <span>actividades en la vista</span>
          </span>
          <span class="summary-chip">
            <strong>{{ actionableCount() }}</strong>
            <span>listas para acción</span>
          </span>
        </div>
      </div>

      <div class="status-legend">
        @for (status of statuses; track status) {
          <rip-status-chip [label]="statusLabel(status)" [tone]="statusTone(status)" />
        }
      </div>

      <mat-card appearance="outlined">
        <mat-card-content>
          <p class="section-kicker">Filtros</p>
          <form class="form-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline">
              <mat-label>Buscar</mat-label>
              <input matInput formControlName="text">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Estado</mat-label>
              <mat-select formControlName="status">
                <mat-option value="all">Todos los estados</mat-option>
                @for (status of statuses; track status) {
                  <mat-option [value]="status">{{ statusLabel(status) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Tipo</mat-label>
              <mat-select formControlName="type">
                <mat-option value="all">Todos los tipos</mat-option>
                @for (type of types; track type) {
                  <mat-option [value]="type">{{ typeLabel(type) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <div class="actions filter-actions">
              <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
              <button mat-flat-button color="primary" type="submit" [disabled]="loading()">Aplicar</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <div class="activities-layout">
        <mat-card appearance="outlined">
          <mat-card-content>
            <div class="list-toolbar">
              <div>
                <p class="section-kicker">Lista de actividades</p>
                <p class="list-copy">Accede al contexto de cada registro sin salir de esta vista.</p>
              </div>
              @if (loading()) {
                <span class="muted">Actualizando lista...</span>
              }
            </div>

            @if (loading() && !result()) {
              <rip-loading-state message="Cargando tus actividades..." />
            } @else if (loadError()) {
              <rip-error-state [message]="loadError()" />
            } @else if (!loading() && (result()?.content || []).length === 0) {
              <rip-empty-state title="Sin actividades" message="No hay actividades propias con los filtros actuales." />
            } @else {
              <div class="item-list">
                @for (activity of result()?.content || []; track activity.entityType + '-' + activity.entityId) {
                  <button
                    type="button"
                    class="activity-row"
                    [class.selected]="isSelected(activity)"
                    [class.requires-changes]="activity.validationStatus === 'CHANGES_REQUESTED'"
                    (click)="selectActivity(activity)"
                  >
                    <div class="activity-row-topline">
                      <div class="activity-main">
                        <span class="activity-type">{{ typeLabel(activity.entityType) }}</span>
                        <strong>{{ activity.title }}</strong>
                        @if (activity.subtitle) {
                          <p>{{ activity.subtitle }}</p>
                        }
                      </div>
                      <div class="activity-header-meta">
                        <rip-status-chip [label]="statusLabel(activity.validationStatus)" [tone]="statusTone(activity.validationStatus)" />
                        @if (activity.validationStatus === 'CHANGES_REQUESTED') {
                          <rip-tag-chip label="Requiere cambios" tone="status" />
                        }
                      </div>
                    </div>

                    @if (summaryEntries(activity).length > 0) {
                      <div class="activity-summary">
                        @for (entry of summaryEntries(activity); track entry[0]) {
                          <span><strong>{{ entry[0] }}:</strong> {{ entry[1] }}</span>
                        }
                      </div>
                    }

                    @if (activity.dataQualityReminders.length > 0) {
                      <div class="signal-list compact-signals">
                        @for (reminder of activity.dataQualityReminders.slice(0, 2); track reminder) {
                          <rip-tag-chip [label]="reminder" />
                        }
                      </div>
                    }
                  </button>
                }
              </div>
            }

            <div class="pagination">
              <button mat-button type="button" [disabled]="currentPage() === 0 || loading()" (click)="goToPage(currentPage() - 1)">Anterior</button>
              <span>Página {{ currentPage() + 1 }} de {{ result()?.totalPages || 1 }}</span>
              <button mat-button type="button" [disabled]="(result()?.last ?? true) || loading()" (click)="goToPage(currentPage() + 1)">Siguiente</button>
            </div>
          </mat-card-content>
        </mat-card>

        <div class="detail-column">
          <mat-card appearance="outlined" class="detail-card">
            <mat-card-content>
              @if (detailLoading()) {
                <rip-loading-state message="Cargando el detalle de la actividad..." />
              } @else if (detailError()) {
                <rip-error-state [message]="detailError()" />
              } @else {
                @if (selectedDetail(); as detail) {
                  <div class="detail-overview">
                    <div class="detail-heading">
                      <p class="section-kicker">Detalle seleccionado</p>
                      <h2>{{ detail.title }}</h2>
                      @if (detail.subtitle) {
                        <p class="detail-subtitle">{{ detail.subtitle }}</p>
                      }
                      <div class="chip-list">
                        <rip-tag-chip [label]="typeLabel(detail.entityType)" tone="type" />
                        @if (detail.researcherName && detail.entityType !== 'RESEARCHER') {
                          <rip-tag-chip [label]="detail.researcherName" />
                        }
                        @if (detail.researchUnitName) {
                          <rip-tag-chip [label]="detail.researchUnitName" />
                        }
                      </div>
                    </div>
                    <rip-status-chip [label]="statusLabel(detail.validationStatus)" [tone]="statusTone(detail.validationStatus)" />
                  </div>

                  <div class="detail-status-grid">
                    <div class="status-panel">
                      <span>Estado actual</span>
                      <strong>{{ statusLabel(detail.validationStatus) }}</strong>
                      <p>{{ detailStatusCopy(detail.validationStatus) }}</p>
                    </div>
                    <div class="status-panel">
                      <span>Último envío</span>
                      <strong>{{ formatDate(detail.submittedAt) }}</strong>
                      <p>{{ detail.submittedAt ? 'Última fecha registrada para el flujo de validación.' : 'Todavía no hay envío registrado.' }}</p>
                    </div>
                    <div class="status-panel">
                      <span>Última revisión</span>
                      <strong>{{ detail.validatedAt ? formatDate(detail.validatedAt) : 'Pendiente' }}</strong>
                      <p>{{ detail.validatedBy ? 'Por ' + detail.validatedBy : 'Aún sin revisión cerrada.' }}</p>
                    </div>
                  </div>

                  @if (detail.validationStatus === 'CHANGES_REQUESTED') {
                    <section class="changes-banner">
                      <div class="changes-copy">
                        <span class="banner-label">Requiere cambios</span>
                        <p class="banner-comment">{{ detail.validationComment || 'El equipo validador te ha devuelto este registro para revisión.' }}</p>
                        <p class="banner-support">Actualiza el registro y vuelve a enviarlo cuando esté completo.</p>
                      </div>
                      <div class="actions banner-actions">
                        @if (detail.editable && canOpenEdit(detail)) {
                          <a mat-button [routerLink]="editLink(detail)" [queryParams]="editQueryParams(detail)">{{ editActionLabel(detail) }}</a>
                        }
                        @if (detail.submittable) {
                          <button mat-flat-button color="primary" type="button" [disabled]="actionLoading()" (click)="submitSelected()">
                            Enviar a validación
                          </button>
                        }
                      </div>
                    </section>
                  } @else if (detail.validationComment) {
                    <section class="validator-note">
                      <span>Comentario de validación</span>
                      <p>{{ detail.validationComment }}</p>
                    </section>
                  }

                  <section class="detail-section">
                    <div class="detail-section-heading">
                      <h3>Resumen del registro</h3>
                      <p>Campos clave para revisar antes de editar o reenviar.</p>
                    </div>
                    <div class="detail-facts">
                      @for (entry of fieldEntries(detail); track entry.label) {
                        <div class="detail-fact">
                          <span>{{ entry.label }}</span>
                          <strong>{{ entry.value }}</strong>
                        </div>
                      }
                    </div>
                  </section>

                  @if (detail.warnings.length > 0 || detail.dataQualityReminders.length > 0) {
                    <section class="detail-section">
                      <div class="detail-section-heading">
                        <h3>Señales y calidad</h3>
                        <p>Recordatorios automáticos y advertencias detectadas para este registro.</p>
                      </div>
                      <div class="signal-list">
                        @for (warning of detail.warnings; track warning) {
                          <rip-tag-chip [label]="warning" tone="status" />
                        }
                        @for (reminder of detail.dataQualityReminders; track reminder) {
                          <rip-tag-chip [label]="reminder" />
                        }
                      </div>
                    </section>
                  }

                  @if (assistantTasks().length > 0) {
                    <section class="detail-section assistant-section">
                      <div class="detail-section-heading">
                        <h3>Asistente de calidad</h3>
                        <p>Trabaja con incidencias concretas de este registro sin aplicar cambios de forma automática.</p>
                      </div>

                      <div class="assistant-task-list">
                        @for (task of assistantTasks(); track task.id) {
                          <article class="assistant-task-card" [class.warning-task]="task.tone === 'warning'">
                            <div class="assistant-task-topline">
                              <div class="chip-list">
                                <rip-tag-chip [label]="task.badge" [tone]="task.tone === 'warning' ? 'status' : 'default'" />
                                <rip-tag-chip [label]="typeLabel(detail.entityType)" tone="type" />
                              </div>
                              <a mat-button [routerLink]="editLink(detail)" [queryParams]="editQueryParams(detail)">{{ viewRecordLabel(detail) }}</a>
                            </div>

                            <p>{{ task.issue }}</p>

                            <div class="actions assistant-actions">
                              @if (task.supportsSuggestion) {
                                <button mat-flat-button color="primary" type="button" [disabled]="assistantLoading()" (click)="requestAssistantSuggestion(task)">
                                  {{ suggestionActionLabel(task) }}
                                </button>
                              } @else {
                                <a mat-flat-button color="primary" [routerLink]="editLink(detail)" [queryParams]="editQueryParams(detail)">
                                  {{ task.actionLabel }}
                                </a>
                              }
                            </div>
                          </article>
                        }
                      </div>

                      @if (assistantLoading()) {
                        <p class="assistant-status">Preparando sugerencia con IA...</p>
                      }

                      @if (assistantError()) {
                        <div class="assistant-status assistant-error">{{ assistantError() }}</div>
                      }

                      @if (assistantSuggestion(); as suggestion) {
                        <rip-ai-suggestion-review-panel
                          [suggestion]="suggestion"
                          [problemStatement]="assistantProblem()"
                          [showDecisionActions]="false"
                        />
                      }
                    </section>
                  }

                  @if (actionMessage()) {
                    <div class="action-message" [class.error-message]="actionMessageTone() === 'error'">
                      {{ actionMessage() }}
                    </div>
                  }

                  @if (detail.validationStatus !== 'CHANGES_REQUESTED' && (detail.editable || detail.submittable)) {
                    <div class="actions detail-actions">
                      @if (detail.editable && canOpenEdit(detail)) {
                        <a mat-button [routerLink]="editLink(detail)" [queryParams]="editQueryParams(detail)">{{ editActionLabel(detail, false) }}</a>
                      }
                      @if (detail.submittable) {
                        <button mat-flat-button color="primary" type="button" [disabled]="actionLoading()" (click)="submitSelected()">
                          Enviar a validación
                        </button>
                      }
                    </div>
                  }
                } @else {
                  <rip-empty-state title="Selecciona una actividad" message="Verás aquí el detalle, los comentarios y el historial de validación." />
                }
              }
            </mat-card-content>
          </mat-card>

          @if (selectedDetail(); as detail) {
            <rip-audit-history-panel
              [entityType]="detail.entityType"
              [entityId]="detail.entityId"
              title="Historial de validación"
              subtitle="Consulta comentarios y cambios de estado registrados para este elemento."
            />
          }
        </div>
      </div>
    </section>
  `,
  styles: [`
    .status-legend {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .list-toolbar {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 16px;
    }

    .list-copy {
      margin: 0;
      color: #667487;
      font-size: 0.9rem;
      line-height: 1.5;
    }

    .filter-actions {
      align-self: center;
    }

    .activities-layout {
      display: grid;
      grid-template-columns: minmax(0, 0.92fr) minmax(360px, 1.08fr);
      gap: 24px;
      align-items: start;
    }

    .activity-row {
      display: grid;
      gap: 12px;
      width: 100%;
      padding: 18px;
      border: 1px solid #e2e8f0;
      border-radius: 18px;
      background: #ffffff;
      color: inherit;
      cursor: pointer;
      font: inherit;
      text-align: left;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease, background-color 140ms ease;
    }

    .activity-row:hover,
    .activity-row.selected {
      border-color: #a9c7d6;
      background: #eef7fb;
      box-shadow: 0 16px 32px rgba(20, 32, 51, 0.08);
      transform: translateY(-1px);
    }

    .activity-row.requires-changes {
      border-color: #efc27a;
      background: linear-gradient(180deg, #fffdf6, #fff7e8);
    }

    .activity-row-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 14px;
    }

    .activity-main {
      display: grid;
      gap: 6px;
      min-width: 0;
    }

    .activity-type {
      color: #2f6f8f;
      font-size: 0.76rem;
      font-weight: 780;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    .activity-main strong {
      color: #142033;
      font-size: 1rem;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .activity-main p {
      margin: 0;
      color: #667487;
      font-size: 0.9rem;
      line-height: 1.5;
    }

    .activity-header-meta {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      flex-wrap: wrap;
      max-width: 280px;
    }

    .activity-summary {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 16px;
      color: #5f7083;
      font-size: 0.88rem;
      line-height: 1.45;
    }

    .compact-signals {
      gap: 8px;
    }

    .pagination {
      display: flex;
      justify-content: flex-end;
      align-items: center;
      gap: 12px;
      margin-top: 16px;
      color: #5a6677;
    }

    .detail-column {
      display: grid;
      gap: 20px;
    }

    .detail-card mat-card-content {
      display: grid;
      gap: 18px;
    }

    .detail-overview {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
    }

    .detail-heading {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .detail-heading h2 {
      margin: 0;
      color: #142033;
      font-size: 1.32rem;
      line-height: 1.22;
      overflow-wrap: anywhere;
    }

    .detail-subtitle {
      margin: 0;
      color: #667487;
      line-height: 1.55;
    }

    .detail-status-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 14px;
    }

    .status-panel {
      display: grid;
      gap: 8px;
      padding: 16px;
      border: 1px solid #e1e9f0;
      border-radius: 16px;
      background: linear-gradient(180deg, #ffffff, #f8fbfd);
    }

    .status-panel span {
      color: #5f7182;
      font-size: 0.76rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .status-panel strong {
      color: #142033;
      font-size: 1rem;
      line-height: 1.25;
    }

    .status-panel p {
      margin: 0;
      color: #667487;
      line-height: 1.5;
    }

    .changes-banner {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 18px;
      padding: 18px;
      border: 1px solid #efc27a;
      border-radius: 16px;
      background: linear-gradient(180deg, #fffdf5, #fff7e6);
    }

    .changes-copy {
      display: grid;
      gap: 8px;
    }

    .banner-label {
      color: #8b5a10;
      font-size: 0.8rem;
      font-weight: 780;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    .banner-comment,
    .banner-support {
      margin: 0;
      line-height: 1.55;
    }

    .banner-comment {
      color: #4f3a14;
      font-size: 1rem;
      font-weight: 700;
    }

    .banner-support {
      color: #6f5528;
    }

    .banner-actions {
      align-items: flex-start;
      justify-content: flex-start;
    }

    .validator-note,
    .action-message {
      padding: 14px 16px;
      border: 1px solid #d9e6f2;
      border-radius: 14px;
      background: #f7fbff;
    }

    .validator-note span {
      color: #5e6b7c;
      font-size: 0.76rem;
      font-weight: 760;
      text-transform: uppercase;
    }

    .validator-note p {
      margin: 8px 0 0;
      color: #243044;
      line-height: 1.55;
    }

    .detail-section {
      display: grid;
      gap: 14px;
    }

    .detail-section-heading {
      display: grid;
      gap: 6px;
    }

    .detail-section-heading h3 {
      margin: 0;
      color: #162336;
      font-size: 1rem;
    }

    .detail-section-heading p {
      margin: 0;
      color: #667487;
      line-height: 1.5;
    }

    .detail-facts {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
    }

    .detail-fact {
      display: grid;
      gap: 8px;
      padding: 16px;
      border: 1px solid #e1e8ef;
      border-radius: 16px;
      background: #ffffff;
    }

    .detail-fact span {
      color: #67778a;
      font-size: 0.76rem;
      font-weight: 760;
      letter-spacing: 0.03em;
      text-transform: uppercase;
    }

    .detail-fact strong {
      color: #243044;
      font-size: 0.96rem;
      line-height: 1.45;
      overflow-wrap: anywhere;
    }

    .signal-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .assistant-task-list {
      display: grid;
      gap: 12px;
    }

    .assistant-task-card {
      display: grid;
      gap: 12px;
      padding: 16px;
      border: 1px solid #dde6ef;
      border-radius: 16px;
      background: #ffffff;
    }

    .warning-task {
      border-color: #efcf86;
      background: linear-gradient(180deg, #fffdf6, #fff8eb);
    }

    .assistant-task-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
    }

    .assistant-task-card p {
      margin: 0;
      color: #5a6c7d;
      line-height: 1.55;
    }

    .assistant-actions {
      justify-content: flex-start;
    }

    .assistant-status {
      margin: 0;
      color: #5f7182;
      line-height: 1.55;
    }

    .assistant-error {
      padding: 14px 16px;
      border: 1px solid #efc3c3;
      border-radius: 14px;
      background: #fff5f5;
      color: #a12b2b;
    }

    .action-message {
      color: #17634f;
      font-size: 0.92rem;
      font-weight: 700;
    }

    .error-message {
      border-color: #efc3c3;
      background: #fff5f5;
      color: #a12b2b;
    }

    .detail-actions {
      justify-content: flex-start;
    }

    @media (max-width: 1180px) {
      .activities-layout {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 760px) {
      .list-toolbar,
      .activity-row-topline,
      .detail-overview,
      .changes-banner,
      .assistant-task-topline {
        display: grid;
      }

      .activity-header-meta {
        justify-content: flex-start;
        max-width: none;
      }

      .pagination {
        justify-content: flex-start;
        flex-wrap: wrap;
      }
    }
  `]
})
export class MyActivitiesPageComponent implements OnInit {
  private readonly api = inject(MeApiService);
  private readonly aiSuggestionsApi = inject(AiSuggestionsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly masterData = inject(AcademicMasterDataService);
  private readonly route = inject(ActivatedRoute);
  readonly navigationContext = inject(NavigationContextService);

  readonly statuses: ValidationStatus[] = ['DRAFT', 'PENDING_VALIDATION', 'CHANGES_REQUESTED', 'VALIDATED', 'REJECTED'];
  readonly types: ValidationEntityType[] = ['PUBLICATION', 'EVENT_PARTICIPATION', 'RESEARCHER_AFFILIATION', 'RESEARCHER'];
  readonly result = signal<PageResponse<MeActivity> | null>(null);
  readonly selectedDetail = signal<MeActivityDetail | null>(null);
  readonly loading = signal(false);
  readonly loadError = signal('');
  readonly detailLoading = signal(false);
  readonly detailError = signal('');
  readonly actionLoading = signal(false);
  readonly actionMessage = signal('');
  readonly actionMessageTone = signal<'success' | 'error'>('success');
  readonly currentPage = signal(0);
  readonly focusTarget = signal<ActivityTarget | null>(null);
  readonly assistantIntent = signal<QualityAssistantActionKind | null>(null);
  readonly assistantSuggestion = signal<AiSuggestion | null>(null);
  readonly assistantProblem = signal('');
  readonly assistantLoading = signal(false);
  readonly assistantError = signal('');
  readonly actionableCount = computed(() => {
    return (this.result()?.content ?? []).filter((activity) => activity.submittable || activity.validationStatus === 'CHANGES_REQUESTED').length;
  });
  readonly assistantTasks = computed<AssistantTask[]>(() => {
    const detail = this.selectedDetail();
    if (!detail) {
      return [];
    }
    return this.buildAssistantTasks(detail);
  });

  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    status: new FormControl<StatusFilter>('all', { nonNullable: true }),
    type: new FormControl<TypeFilter>('all', { nonNullable: true })
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        this.focusTarget.set(this.parseTarget(params.get('entityType'), params.get('entityId')));
        this.assistantIntent.set(this.parseAssistantIntent(params.get('assistantAction')));
        this.currentPage.set(0);
        this.loadActivities(true);
      });
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.loadActivities(true);
  }

  clearFilters(): void {
    this.filterForm.reset({ text: '', status: 'all', type: 'all' });
    this.currentPage.set(0);
    this.loadActivities(true);
  }

  goToPage(page: number): void {
    this.currentPage.set(Math.max(page, 0));
    this.loadActivities(true);
  }

  selectActivity(activity: MeActivity): void {
    this.loadActivityDetail(activity.entityType, activity.entityId, true);
  }

  submitSelected(): void {
    const detail = this.selectedDetail();
    if (!detail || this.actionLoading()) {
      return;
    }

    this.actionLoading.set(true);
    this.actionMessage.set('');
    this.api.submitActivity(detail.entityType, detail.entityId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.selectedDetail.set(updated);
          this.actionLoading.set(false);
          this.actionMessageTone.set('success');
          this.actionMessage.set(
            detail.validationStatus === 'CHANGES_REQUESTED'
              ? 'Registro reenviado a validación.'
              : 'Actividad enviada a validación.'
          );
          this.loadActivities(false);
        },
        error: () => {
          this.actionLoading.set(false);
          this.actionMessageTone.set('error');
          this.actionMessage.set('No se pudo enviar la actividad.');
        }
      });
  }

  isSelected(activity: MeActivity): boolean {
    const detail = this.selectedDetail();
    return detail !== null && detail.entityType === activity.entityType && detail.entityId === activity.entityId;
  }

  editLink(detail: MeActivityDetail): string[] {
    if (detail.entityType === 'PUBLICATION') {
      return ['/publications', String(detail.entityId)];
    }
    if (detail.entityType === 'EVENT_PARTICIPATION') {
      return ['/app/actividades', String(detail.entityId)];
    }
    if (detail.entityType === 'RESEARCHER') {
      return ['/researchers', String(detail.entityId)];
    }
    return ['/researchers', String(detail.researcherId ?? detail.entityId)];
  }

  editQueryParams(detail: MeActivityDetail): Record<string, string | number> {
    return {
      ...this.navigationContext.returnQueryParams('Volver a mis actividades'),
      focusEntityType: detail.entityType,
      focusEntityId: detail.entityId
    };
  }

  editActionLabel(detail: MeActivityDetail, includeSendHint = true): string {
    if (detail.validationStatus === 'CHANGES_REQUESTED') {
      return 'Editar y reenviar';
    }
    return includeSendHint && detail.submittable ? 'Editar y preparar envio' : 'Editar';
  }

  canOpenEdit(detail: MeActivityDetail): boolean {
    return detail.entityType === 'PUBLICATION'
      || detail.entityType === 'EVENT_PARTICIPATION'
      || detail.entityType === 'RESEARCHER'
      || detail.entityType === 'RESEARCHER_AFFILIATION';
  }

  typeLabel(type: string): string {
    return validationEntityTypeLabel(type);
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

  fieldEntries(detail: MeActivityDetail): Array<{ label: string; value: string }> {
    return Object.entries(detail.fields).map(([label, value]) => ({
      label,
      value: this.displayFieldValue(label, value)
    }));
  }

  detailStatusCopy(status: ValidationStatus): string {
    switch (status) {
      case 'DRAFT':
        return 'Puedes seguir editando este registro antes de enviarlo.';
      case 'PENDING_VALIDATION':
        return 'Está en revisión institucional y no requiere cambios por ahora.';
      case 'CHANGES_REQUESTED':
        return 'Necesita ajustes antes de volver a validación.';
      case 'VALIDATED':
        return 'El registro ya ha sido validado.';
      case 'REJECTED':
        return 'Revisa el historial para entender el motivo del rechazo.';
      default:
        return 'Consulta el estado actual del registro.';
    }
  }

  formatDate(value: string | null): string {
    if (!value) {
      return 'Pendiente';
    }
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  displayFieldValue(fieldKey: string, fieldValue: string): string {
    if (fieldKey === 'Tipo') {
      return this.selectedDetail()?.entityType === 'EVENT_PARTICIPATION'
        ? this.masterData.eventParticipationTypeLabel(fieldValue)
        : this.masterData.publicationTypeLabel(fieldValue);
    }
    if (fieldKey === 'Estado') {
      return this.masterData.publicationStatusLabel(fieldValue);
    }
    if (fieldKey === 'Tipo de evento') {
      return this.masterData.eventTypeLabel(fieldValue);
    }
    return fieldValue;
  }

  requestAssistantSuggestion(task: AssistantTask): void {
    const detail = this.selectedDetail();
    if (!detail || !task.supportsSuggestion || this.assistantLoading()) {
      return;
    }
    const issueCode = task.issueCode as Exclude<QualityAssistantIssueCode, 'changes-requested'>;

    this.assistantLoading.set(true);
    this.assistantError.set('');
    this.assistantProblem.set(task.issue);
    this.aiSuggestionsApi.suggestDataQualityImprovement({
      issueId: issueCode,
      entityType: detail.entityType,
      entityId: detail.entityId,
      title: detail.title,
      subtitle: detail.subtitle,
      currentValue: this.currentValueForIssue(task.issueCode),
      path: this.editLink(detail).join('/'),
      context: task.issue
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (suggestion) => {
          this.assistantSuggestion.set(suggestion);
          this.assistantLoading.set(false);
        },
        error: () => {
          this.assistantSuggestion.set(null);
          this.assistantLoading.set(false);
          this.assistantError.set('No se pudo preparar la sugerencia con IA para este registro.');
        }
      });
  }

  viewRecordLabel(detail: MeActivityDetail): string {
    if (detail.entityType === 'PUBLICATION') {
      return 'Ver publicación';
    }
    if (detail.entityType === 'EVENT_PARTICIPATION') {
      return 'Ver actividad';
    }
    if (detail.entityType === 'RESEARCHER_AFFILIATION') {
      return 'Ver afiliación';
    }
    if (detail.entityType === 'RESEARCHER') {
      return 'Ver perfil';
    }
    return 'Ver registro';
  }

  suggestionActionLabel(task: AssistantTask): string {
    return task.issueCode === 'missing-doi' ? 'Sugerir mejora con IA' : task.actionLabel;
  }

  private loadActivityDetail(entityType: ValidationEntityType, entityId: number, clearActionMessage: boolean): void {
    if (clearActionMessage) {
      this.actionMessage.set('');
    }
    this.resetAssistantState();
    this.detailLoading.set(true);
    this.detailError.set('');
    this.api.activityDetail(entityType, entityId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.selectedDetail.set(detail);
          this.detailLoading.set(false);
          this.maybeRequestAssistantSuggestion(detail);
        },
        error: () => {
          this.selectedDetail.set(null);
          this.detailLoading.set(false);
          this.detailError.set('No se pudo cargar el detalle de la actividad.');
        }
      });
  }

  private loadActivities(selectPreferred: boolean): void {
    const value = this.filterForm.getRawValue();
    const currentDetail = this.selectedDetail();
    const preferredTarget = this.focusTarget();

    this.loading.set(true);
    this.loadError.set('');
    this.api.activities({
      page: this.currentPage(),
      text: value.text || undefined,
      status: value.status === 'all' ? undefined : value.status,
      type: value.type === 'all' ? undefined : value.type
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);

          if (!selectPreferred) {
            return;
          }

          if (preferredTarget) {
            this.focusTarget.set(null);
            const match = result.content.find((activity) => activity.entityType === preferredTarget.entityType && activity.entityId === preferredTarget.entityId);
            if (match) {
              this.selectActivity(match);
              return;
            }
            this.loadActivityDetail(preferredTarget.entityType, preferredTarget.entityId, true);
            return;
          }

          if (currentDetail) {
            const match = result.content.find((activity) => activity.entityType === currentDetail.entityType && activity.entityId === currentDetail.entityId);
            if (match) {
              this.loadActivityDetail(match.entityType, match.entityId, false);
              return;
            }
          }

          const first = result.content[0] ?? null;
          if (first) {
            this.selectActivity(first);
          } else {
            this.detailLoading.set(false);
            this.detailError.set('');
            this.selectedDetail.set(null);
          }
        },
        error: () => {
          this.result.set(null);
          this.selectedDetail.set(null);
          this.loading.set(false);
          this.loadError.set('No se pudo cargar la lista de actividades.');
          this.detailLoading.set(false);
          this.detailError.set('');
        }
      });
  }

  private parseTarget(entityType: string | null, entityId: string | null): ActivityTarget | null {
    if (!entityType || !entityId) {
      return null;
    }
    if (!this.types.includes(entityType as ValidationEntityType)) {
      return null;
    }
    const parsedId = Number(entityId);
    if (!Number.isInteger(parsedId) || parsedId <= 0) {
      return null;
    }
    return {
      entityType: entityType as ValidationEntityType,
      entityId: parsedId
    };
  }

  private parseAssistantIntent(value: string | null): QualityAssistantActionKind | null {
    if (value === 'summary') {
      return 'summary';
    }
    if (value === 'topics') {
      return 'topics';
    }
    return null;
  }

  private buildAssistantTasks(detail: MeActivityDetail): AssistantTask[] {
    return qualityAssistantDefinitionsForActivity(detail).map((definition) => ({
      id: `${detail.entityType}-${detail.entityId}-${definition.code}`,
      issueCode: definition.code,
      badge: definition.badge,
      issue: definition.code === 'changes-requested'
        ? (detail.validationComment || definition.issue)
        : definition.issue,
      tone: definition.tone,
      actionKind: definition.actionKind,
      actionLabel: definition.actionLabel,
      supportsSuggestion: supportsAiSuggestionForQualityIssue(definition.code)
    }));
  }

  private maybeRequestAssistantSuggestion(detail: MeActivityDetail): void {
    const intent = this.assistantIntent();
    if (!intent) {
      return;
    }

    const matchingTask = this.buildAssistantTasks(detail).find((task) => task.supportsSuggestion && task.actionKind === intent);
    if (!matchingTask) {
      return;
    }

    this.requestAssistantSuggestion(matchingTask);
  }

  private currentValueForIssue(issueCode: QualityAssistantIssueCode): string | null {
    switch (issueCode) {
      case 'missing-doi':
        return 'Sin DOI';
      case 'missing-abstract':
        return 'Sin resumen';
      case 'missing-topics':
        return 'Sin temas';
      case 'missing-public-summary':
        return 'Sin resumen público';
      default:
        return null;
    }
  }

  private resetAssistantState(): void {
    this.assistantSuggestion.set(null);
    this.assistantProblem.set('');
    this.assistantLoading.set(false);
    this.assistantError.set('');
  }
}
