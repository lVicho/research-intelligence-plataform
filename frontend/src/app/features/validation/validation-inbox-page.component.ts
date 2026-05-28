import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import {
  AcademicMasterDataService
} from '../../core/api/academic-master-data.service';
import {
  PageResponse,
  ValidationEntityType,
  ValidationItem,
  ValidationItemDetail,
  ValidationStatus
} from '../../core/api/api-models';
import { AiSuggestionsApiService } from '../../core/api/ai-suggestions-api.service';
import { ValidationApiService } from '../../core/api/validation-api.service';
import {
  ValidationAiReview,
  ValidationAiReviewApiService
} from '../../core/api/validation-ai-review-api.service';
import { AuditHistoryPanelComponent } from '../../shared/components/audit-history-panel.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import {
  validationEntityTypeLabel,
  validationStatusLabel,
  validationStatusTone
} from '../../shared/utils/display-labels';
import { ValidationAiReviewPanelComponent } from './validation-ai-review-panel.component';
import { ValidationActionDialogComponent, ValidationDialogAction } from './validation-action-dialog.component';

type EntityFilter = ValidationEntityType | 'all';
type ValidationSort = 'submittedAt,desc' | 'submittedAt,asc' | 'title,asc' | 'entityType,asc';
type ValidationAction = 'validate' | 'reject' | 'requestChanges';

interface DisplayField {
  key: string;
  value: string;
}

@Component({
  selector: 'rip-validation-inbox-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    AuditHistoryPanelComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent,
    ValidationAiReviewPanelComponent
  ],
  template: `
    <section class="page validation-console-page">
      <rip-page-header
        title="Bandeja de validación"
        [subtitle]="(result()?.totalElements || 0) + ' actividades en revisión institucional.'"
        eyebrow="Control editorial"
        [compact]="true"
      />

      <div class="toolbar-meta">
        <p>Revisión operativa para validar, rechazar o solicitar cambios sin duplicar navegación ni mezclar esta bandeja con el portal público.</p>
        <div class="summary-strip">
          <span class="summary-chip">
            <strong>{{ result()?.totalElements || 0 }}</strong>
            <span>elementos encontrados</span>
          </span>
          <span class="summary-chip">
            <strong>{{ pendingOnPage() }}</strong>
            <span>pendientes en esta página</span>
          </span>
        </div>
      </div>

      <mat-card appearance="outlined" class="filter-card">
        <mat-card-content>
          <div class="section-header compact-header">
            <div>
              <p class="section-kicker">Filtros</p>
              <h2>Acota la cola de revisión</h2>
              <p>Combina estado, tipo, texto y contexto institucional para centrar el trabajo de validación.</p>
            </div>
          </div>

          <form class="form-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline">
              <mat-label>Buscar</mat-label>
              <input matInput formControlName="text">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Estado</mat-label>
              <mat-select formControlName="status">
                @for (status of validationStatuses; track status) {
                  <mat-option [value]="status">{{ statusLabel(status) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Tipo</mat-label>
              <mat-select formControlName="entityType">
                <mat-option value="all">Todos los tipos</mat-option>
                @for (type of entityTypes; track type) {
                  <mat-option [value]="type">{{ entityTypeLabel(type) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>ID investigador</mat-label>
              <input matInput type="number" min="1" formControlName="researcherId">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>ID unidad</mat-label>
              <input matInput type="number" min="1" formControlName="researchUnitId">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Desde</mat-label>
              <input matInput type="date" formControlName="submittedFrom">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Hasta</mat-label>
              <input matInput type="date" formControlName="submittedTo">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Orden</mat-label>
              <mat-select formControlName="sort">
                <mat-option value="submittedAt,desc">Más recientes</mat-option>
                <mat-option value="submittedAt,asc">Más antiguas</mat-option>
                <mat-option value="title,asc">Título</mat-option>
                <mat-option value="entityType,asc">Tipo</mat-option>
              </mat-select>
            </mat-form-field>

            <div class="actions filter-actions">
              <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
              <button mat-flat-button color="primary" type="submit" [disabled]="loading()">Aplicar</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <div class="inbox-layout">
        <mat-card appearance="outlined" class="list-card">
          <mat-card-content>
            <div class="list-toolbar">
              <div>
                <p class="section-kicker">Actividades</p>
                <h2>{{ (result()?.content || []).length }} elementos en pantalla</h2>
                <p class="list-copy">La lista prioriza contexto, trazabilidad y acceso rápido al detalle de revisión.</p>
              </div>
              @if (loading()) {
                <span class="muted">Actualizando bandeja...</span>
              }
            </div>

            @if (loading() && !result()) {
              <rip-loading-state message="Cargando actividades pendientes de validación..." />
            } @else if (loadError()) {
              <rip-error-state [message]="loadError()" />
            } @else if (!loading() && (result()?.content || []).length === 0) {
              <rip-empty-state
                title="No hay actividades pendientes de validación."
                message="Cambia los filtros o revisa estados ya resueltos."
              />
            } @else {
              <div class="table-wrap validation-table">
                <table>
                  <thead>
                    <tr>
                      <th>Elemento</th>
                      <th>Contexto</th>
                      <th>Enviado</th>
                      <th>Estado</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (item of result()?.content || []; track item.entityType + '-' + item.entityId) {
                      <tr [class.selected]="isSelected(item)">
                        <td>
                          <div class="item-cell">
                            <strong>{{ item.title }}</strong>
                            <span>{{ entityTypeLabel(item.entityType) }} #{{ item.entityId }}</span>
                            @if (item.subtitle) {
                              <span>{{ item.subtitle }}</span>
                            }
                          </div>
                        </td>
                        <td>
                          <div class="item-cell">
                            <span>{{ item.researcherName || 'Sin investigador asociado' }}</span>
                            <span>{{ item.researchUnitName || 'Sin unidad asociada' }}</span>
                          </div>
                        </td>
                        <td>
                          <div class="item-cell compact">
                            <strong>{{ item.submittedBy }}</strong>
                            <span>{{ dateLabel(item.submittedAt) }}</span>
                          </div>
                        </td>
                        <td class="status-cell">
                          <rip-status-chip [label]="statusLabel(item.validationStatus)" [tone]="statusTone(item.validationStatus)" />
                        </td>
                        <td class="row-action">
                          <button mat-button type="button" (click)="selectItem(item)">Revisar</button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }

            <div class="pagination">
              <button mat-button type="button" [disabled]="currentPage() === 0 || loading()" (click)="goToPage(currentPage() - 1)">Anterior</button>
              <span>Página {{ currentPage() + 1 }} de {{ result()?.totalPages || 1 }}</span>
              <button mat-button type="button" [disabled]="(result()?.last ?? true) || loading()" (click)="goToPage(currentPage() + 1)">Siguiente</button>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card appearance="outlined" class="detail-card">
          <mat-card-content>
            @if (detailLoading()) {
              <rip-loading-state message="Cargando detalle de la actividad..." />
            } @else if (detailError()) {
              <rip-error-state [message]="detailError()" />
            } @else {
              @if (selectedDetail(); as detail) {
                <div class="detail-shell">
                <div class="detail-overview">
                  <div class="detail-title-block">
                    <p class="section-kicker">Revisión</p>
                    <h2>{{ detail.item.title }}</h2>
                    <p class="detail-subtitle">{{ entityTypeLabel(detail.item.entityType) }} #{{ detail.item.entityId }}</p>
                    <div class="detail-meta-line">
                      <span>{{ detail.item.researcherName || 'Sin investigador asociado' }}</span>
                      <span>{{ detail.item.researchUnitName || 'Sin unidad asociada' }}</span>
                      <span>{{ detail.item.submittedBy }}</span>
                    </div>
                  </div>

                  <div class="detail-status-stack">
                    <rip-status-chip [label]="statusLabel(detail.item.validationStatus)" [tone]="statusTone(detail.item.validationStatus)" />
                    <span>Enviada {{ dateLabel(detail.item.submittedAt) }}</span>
                  </div>
                </div>

                <section class="detail-section">
                  <div class="section-header compact-header">
                    <div>
                      <p class="section-kicker">Datos principales</p>
                      <h3>Contenido del registro</h3>
                      <p>Campos visibles para revisar la actividad sin depender de tarjetas genéricas.</p>
                    </div>
                  </div>

                  @if (primaryFields().length === 0 && secondaryFields().length === 0) {
                    <rip-empty-state
                      title="Sin campos adicionales"
                      message="Este elemento no expone campos detallados en el snapshot actual."
                    />
                  } @else {
                    <div class="detail-data-layout">
                      @if (primaryFields().length > 0) {
                        <section class="detail-data-block">
                          <p class="detail-data-title">Resumen</p>
                          <div class="detail-field-list">
                            @for (field of primaryFields(); track field.key) {
                              <div class="detail-field-row">
                                <span>{{ field.key }}</span>
                                <strong>{{ field.value }}</strong>
                              </div>
                            }
                          </div>
                        </section>
                      }

                      @if (secondaryFields().length > 0) {
                        <section class="detail-data-block">
                          <p class="detail-data-title">Detalle adicional</p>
                          <div class="detail-field-list">
                            @for (field of secondaryFields(); track field.key) {
                              <div class="detail-field-row">
                                <span>{{ field.key }}</span>
                                <strong>{{ field.value }}</strong>
                              </div>
                            }
                          </div>
                        </section>
                      }
                    </div>
                  }
                </section>

                <section class="detail-section">
                  <div class="section-header compact-header">
                    <div>
                      <p class="section-kicker">Revisión</p>
                      <h3>Estado y señales</h3>
                      <p>Resumen operativo del envío, del estado actual y de posibles avisos de calidad.</p>
                    </div>
                  </div>

                  <div class="review-grid">
                    <section class="review-panel">
                      <div class="review-summary-row">
                        <span>Estado actual</span>
                        <rip-status-chip [label]="statusLabel(detail.item.validationStatus)" [tone]="statusTone(detail.item.validationStatus)" />
                      </div>
                      <div class="review-summary-row">
                        <span>Enviado por</span>
                        <strong>{{ detail.item.submittedBy }}</strong>
                      </div>
                      <div class="review-summary-row">
                        <span>Fecha de envío</span>
                        <strong>{{ dateLabel(detail.item.submittedAt) }}</strong>
                      </div>
                      <div class="review-summary-row">
                        <span>Última resolución</span>
                        <strong>{{ resolutionLabel(detail) }}</strong>
                      </div>
                    </section>

                    <section class="review-panel">
                      <p class="detail-data-title">Avisos y señales</p>
                      @if (detail.warnings.length === 0 && detail.dataQualityFlags.length === 0) {
                        <p class="muted review-empty-copy">No hay avisos adicionales para esta actividad.</p>
                      } @else {
                        @if (detail.warnings.length > 0) {
                          <div class="signal-block">
                            <span>Avisos de revisión</span>
                            <div class="chip-list">
                              @for (warning of detail.warnings; track warning) {
                                <rip-tag-chip [label]="warning" tone="status" />
                              }
                            </div>
                          </div>
                        }

                        @if (detail.dataQualityFlags.length > 0) {
                          <div class="signal-block">
                            <span>Señales de calidad</span>
                            <div class="chip-list">
                              @for (flag of detail.dataQualityFlags; track flag) {
                                <rip-tag-chip [label]="flag" />
                              }
                            </div>
                          </div>
                        }
                      }
                    </section>
                  </div>
                </section>

                <section class="detail-section">
                  <div class="section-header compact-header">
                    <div>
                      <p class="section-kicker">Comentario</p>
                      <h3>Mensaje al investigador</h3>
                      <p>Comentario visible en el flujo posterior cuando se valida, rechaza o se solicitan cambios.</p>
                    </div>
                  </div>

                  @if (detail.validationComment) {
                    <div class="review-note">
                      <p>{{ detail.validationComment }}</p>
                    </div>
                  } @else {
                    <div class="review-note empty-note">
                      <p>No hay comentario registrado para esta actividad.</p>
                    </div>
                  }
                </section>

                <section class="detail-section">
                  <div class="section-header compact-header">
                    <div>
                      <p class="section-kicker">Revisión asistida</p>
                      <h3>Orientación para la decisión</h3>
                      <p>Apoyo operativo para revisar la actividad con IA sin desplazar la responsabilidad del validador.</p>
                    </div>
                  </div>

                  <rip-validation-ai-review-panel
                    [review]="aiReview()"
                    [loading]="aiReviewLoading()"
                    [actionBusy]="aiSuggestionActionLoading()"
                    [feedbackMessage]="aiSuggestionFeedback()"
                    (analyze)="analyzeWithAi()"
                    (acceptSuggestion)="acceptAiSuggestion()"
                    (rejectSuggestion)="rejectAiSuggestion()"
                  />
                </section>

                <section class="detail-section">
                  <div class="section-header compact-header">
                    <div>
                      <p class="section-kicker">Historial</p>
                      <h3>Trazabilidad</h3>
                      <p>Estados anteriores, comentarios y cambios registrados para esta actividad.</p>
                    </div>
                  </div>

                  <rip-audit-history-panel
                    [entityType]="detail.item.entityType"
                    [entityId]="detail.item.entityId"
                    title="Historial de validación"
                    subtitle="Consulta estados anteriores y comentarios registrados para esta actividad."
                  />
                </section>

                <section class="detail-section action-section">
                  <div class="section-header compact-header">
                    <div>
                      <p class="section-kicker">Acciones</p>
                      <h3>Resolver revisión</h3>
                      <p>Registra la decisión final o pide cambios sin salir del detalle actual.</p>
                    </div>
                  </div>

                  @if (actionMessage()) {
                    <div class="action-message">{{ actionMessage() }}</div>
                  }

                  <div class="action-grid">
                    <button mat-stroked-button type="button" [disabled]="actionLoading()" (click)="openActionDialog('requestChanges')">
                      Solicitar cambios
                    </button>
                    <button mat-stroked-button type="button" color="warn" [disabled]="actionLoading()" (click)="openActionDialog('reject')">
                      Rechazar
                    </button>
                    <button mat-flat-button color="primary" type="button" [disabled]="actionLoading()" (click)="openActionDialog('validate')">
                      Validar
                    </button>
                    <button mat-flat-button color="primary" type="button" [disabled]="actionLoading()" (click)="validateAndNext()">
                      Validar y siguiente
                    </button>
                  </div>

                  <p class="muted action-helper-copy">"Validar y siguiente" registra la decisión actual y abre la siguiente revisión disponible en esta página.</p>
                </section>
                </div>
              } @else {
                <rip-empty-state
                  title="Selecciona una actividad"
                  message="El detalle aparecerá aquí para revisar datos principales, comentario, historial y acciones."
                />
              }
            }
          </mat-card-content>
        </mat-card>
      </div>
    </section>
  `,
  styles: [`
    .validation-console-page {
      display: grid;
      gap: 20px;
    }

    .filter-card mat-card-content,
    .list-card mat-card-content,
    .detail-card mat-card-content {
      display: grid;
      gap: 18px;
    }

    .compact-header {
      margin-bottom: 0;
    }

    .compact-header h2,
    .compact-header h3 {
      margin: 0;
      color: #142033;
      line-height: 1.15;
    }

    .compact-header p {
      margin: 0;
      color: #667487;
      line-height: 1.55;
      max-width: 860px;
    }

    .filter-actions {
      align-self: center;
    }

    .inbox-layout {
      display: grid;
      grid-template-columns: minmax(420px, 0.9fr) minmax(500px, 1.1fr);
      align-items: start;
      gap: 24px;
    }

    .list-toolbar,
    .detail-overview {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 18px;
    }

    .list-toolbar h2 {
      margin: 0;
      color: #142033;
      font-size: 1.08rem;
      line-height: 1.2;
    }

    .list-copy {
      margin: 6px 0 0;
      color: #667487;
      font-size: 0.9rem;
      line-height: 1.55;
      max-width: 720px;
    }

    .detail-shell {
      display: grid;
      gap: 18px;
    }

    .detail-overview {
      padding: 20px 22px;
      border: 1px solid #dce7ef;
      border-radius: 20px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(248, 251, 253, 0.96)),
        linear-gradient(90deg, rgba(20, 52, 73, 0.06), rgba(47, 111, 139, 0.03));
    }

    .detail-title-block {
      display: grid;
      gap: 8px;
      min-width: 0;
    }

    .detail-title-block h2 {
      margin: 0;
      color: #142033;
      font-size: clamp(1.28rem, 1.8vw, 1.6rem);
      line-height: 1.15;
      overflow-wrap: anywhere;
    }

    .detail-subtitle {
      margin: 0;
      color: #496173;
      font-size: 0.92rem;
      font-weight: 760;
    }

    .detail-meta-line {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 14px;
      color: #667487;
      font-size: 0.9rem;
      line-height: 1.45;
    }

    .detail-status-stack {
      display: grid;
      justify-items: end;
      gap: 8px;
      min-width: 168px;
      text-align: right;
    }

    .detail-status-stack span {
      color: #667487;
      font-size: 0.86rem;
      line-height: 1.4;
    }

    .detail-section {
      display: grid;
      gap: 14px;
      padding: 18px 20px;
      border: 1px solid #dce5ee;
      border-radius: 18px;
      background: #ffffff;
    }

    .detail-data-layout,
    .review-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }

    .detail-data-block,
    .review-panel {
      display: grid;
      gap: 14px;
      min-width: 0;
      padding: 16px 18px;
      border: 1px solid #dce5ee;
      border-radius: 16px;
      background: linear-gradient(180deg, #ffffff, #f9fbfd);
    }

    .detail-data-title,
    .signal-block span {
      margin: 0;
      color: #4a6375;
      font-size: 0.78rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .detail-field-list {
      display: grid;
      gap: 12px;
    }

    .detail-field-row {
      display: grid;
      grid-template-columns: minmax(120px, 156px) minmax(0, 1fr);
      gap: 12px;
      align-items: start;
      padding-bottom: 12px;
      border-bottom: 1px solid #e5ebf1;
    }

    .detail-field-row:last-child {
      padding-bottom: 0;
      border-bottom: none;
    }

    .detail-field-row span,
    .review-summary-row span {
      color: #667487;
      font-size: 0.82rem;
      font-weight: 760;
      line-height: 1.4;
      text-transform: uppercase;
    }

    .detail-field-row strong,
    .review-summary-row strong {
      color: #223145;
      font-size: 0.95rem;
      line-height: 1.5;
      overflow-wrap: anywhere;
    }

    .review-summary-row {
      display: grid;
      gap: 8px;
      align-items: start;
      padding-bottom: 12px;
      border-bottom: 1px solid #e5ebf1;
    }

    .review-summary-row:last-child {
      padding-bottom: 0;
      border-bottom: none;
    }

    .review-empty-copy {
      margin: 0;
      line-height: 1.55;
    }

    .signal-block {
      display: grid;
      gap: 10px;
    }

    .review-note,
    .action-message {
      padding: 16px 18px;
      border: 1px solid #d9e6f2;
      border-radius: 16px;
      background: #f7fbff;
    }

    .review-note p {
      margin: 0;
      color: #243044;
      line-height: 1.6;
      overflow-wrap: anywhere;
    }

    .empty-note {
      background: #fbfdff;
    }

    .action-section {
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(247, 250, 253, 0.98)),
        linear-gradient(90deg, rgba(20, 52, 73, 0.04), rgba(47, 111, 139, 0.02));
    }

    .action-message {
      color: #17634f;
      font-size: 0.92rem;
      font-weight: 700;
      line-height: 1.5;
    }

    .action-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }

    .action-grid button {
      width: 100%;
      min-height: 42px;
    }

    .action-helper-copy {
      margin: 0;
      line-height: 1.5;
    }

    .validation-table {
      box-shadow: none;
    }

    th,
    td {
      padding: 12px;
      border-bottom: 1px solid #e4e9ef;
      vertical-align: top;
    }

    tbody tr.selected {
      background: #eef7fb;
    }

    .item-cell {
      display: grid;
      gap: 5px;
      min-width: 0;
    }

    .item-cell strong {
      color: #142033;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .item-cell span {
      color: #667487;
      font-size: 0.86rem;
      line-height: 1.4;
    }

    .item-cell.compact {
      min-width: 120px;
    }

    .status-cell {
      min-width: 164px;
    }

    .row-action {
      text-align: right;
      white-space: nowrap;
    }

    .pagination {
      display: flex;
      justify-content: flex-end;
      align-items: center;
      gap: 12px;
      color: #5a6677;
    }

    @media (max-width: 1240px) {
      .inbox-layout {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 860px) {
      .detail-data-layout,
      .review-grid,
      .action-grid {
        grid-template-columns: 1fr;
      }

      .list-toolbar,
      .detail-overview {
        display: grid;
      }

      .detail-status-stack {
        justify-items: start;
        text-align: left;
      }
    }

    @media (max-width: 640px) {
      .detail-overview,
      .detail-section,
      .detail-data-block,
      .review-panel {
        padding: 16px;
      }

      .detail-field-row {
        grid-template-columns: 1fr;
        gap: 6px;
      }

      .pagination {
        justify-content: flex-start;
        flex-wrap: wrap;
      }
    }
  `]
})
export class ValidationInboxPageComponent implements OnInit {
  private readonly api = inject(ValidationApiService);
  private readonly aiReviewApi = inject(ValidationAiReviewApiService);
  private readonly aiSuggestionsApi = inject(AiSuggestionsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly masterData = inject(AcademicMasterDataService);

  readonly validationStatuses: ValidationStatus[] = ['PENDING_VALIDATION', 'DRAFT', 'CHANGES_REQUESTED', 'REJECTED', 'VALIDATED'];
  readonly entityTypes: ValidationEntityType[] = ['PUBLICATION', 'EVENT_PARTICIPATION', 'RESEARCHER', 'RESEARCHER_AFFILIATION', 'RESEARCH_UNIT'];
  readonly result = signal<PageResponse<ValidationItem> | null>(null);
  readonly selectedDetail = signal<ValidationItemDetail | null>(null);
  readonly loading = signal(false);
  readonly loadError = signal('');
  readonly detailLoading = signal(false);
  readonly detailError = signal('');
  readonly actionLoading = signal(false);
  readonly actionMessage = signal('');
  readonly currentPage = signal(0);
  readonly aiReview = signal<ValidationAiReview | null>(null);
  readonly aiReviewLoading = signal(false);
  readonly aiSuggestionActionLoading = signal(false);
  readonly aiSuggestionFeedback = signal('');
  readonly pendingOnPage = computed(() => {
    return (this.result()?.content ?? []).filter((item) => item.validationStatus === 'PENDING_VALIDATION').length;
  });
  readonly primaryFields = computed<DisplayField[]>(() => {
    const detail = this.selectedDetail();
    if (!detail) {
      return [];
    }

    const summaryEntries = Object.entries(detail.item.summaryFields);
    if (summaryEntries.length > 0) {
      return summaryEntries.map(([key, value]) => this.toDisplayField(detail, key, value));
    }

    return Object.entries(detail.fields).map(([key, value]) => this.toDisplayField(detail, key, value));
  });
  readonly secondaryFields = computed<DisplayField[]>(() => {
    const detail = this.selectedDetail();
    if (!detail) {
      return [];
    }

    const summaryKeys = new Set(Object.keys(detail.item.summaryFields));
    if (summaryKeys.size === 0) {
      return [];
    }

    return Object.entries(detail.fields)
      .filter(([key]) => !summaryKeys.has(key))
      .map(([key, value]) => this.toDisplayField(detail, key, value));
  });

  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    status: new FormControl<ValidationStatus>('PENDING_VALIDATION', { nonNullable: true }),
    entityType: new FormControl<EntityFilter>('all', { nonNullable: true }),
    researcherId: new FormControl('', { nonNullable: true }),
    researchUnitId: new FormControl('', { nonNullable: true }),
    submittedFrom: new FormControl('', { nonNullable: true }),
    submittedTo: new FormControl('', { nonNullable: true }),
    sort: new FormControl<ValidationSort>('submittedAt,desc', { nonNullable: true })
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();
    this.loadInbox(true);
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.loadInbox(true);
  }

  clearFilters(): void {
    this.filterForm.reset({
      text: '',
      status: 'PENDING_VALIDATION',
      entityType: 'all',
      researcherId: '',
      researchUnitId: '',
      submittedFrom: '',
      submittedTo: '',
      sort: 'submittedAt,desc'
    });
    this.currentPage.set(0);
    this.loadInbox(true);
  }

  goToPage(page: number): void {
    this.currentPage.set(Math.max(page, 0));
    this.loadInbox(true);
  }

  selectItem(item: ValidationItem): void {
    this.actionMessage.set('');
    this.detailLoading.set(true);
    this.detailError.set('');
    this.resetAiReviewState();
    this.api.get(item.entityType, item.entityId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.selectedDetail.set(detail);
          this.detailLoading.set(false);
        },
        error: () => {
          this.selectedDetail.set(null);
          this.detailLoading.set(false);
          this.detailError.set('No se pudo cargar el detalle de la actividad.');
          this.resetAiReviewState();
        }
      });
  }

  validateAndNext(): void {
    this.openActionDialog('validate', true);
  }

  openActionDialog(action: ValidationDialogAction, selectNext = false): void {
    if (!this.selectedDetail() || this.actionLoading()) {
      return;
    }

    const suggestedComment = action === 'validate'
      ? null
      : this.aiReview()?.suggestedComment ?? null;

    const dialogRef = this.dialog.open(ValidationActionDialogComponent, {
      width: '560px',
      maxWidth: '95vw',
      data: { action, suggestedComment }
    });

    dialogRef.afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result: { comment: string | null } | undefined) => {
        if (!result) {
          return;
        }

        this.runAction(action, result.comment, selectNext);
      });
  }

  runAction(action: ValidationAction, comment: string | null, selectNext = false): void {
    const detail = this.selectedDetail();
    if (!detail || this.actionLoading()) {
      return;
    }

    const currentItems = this.result()?.content ?? [];
    const currentIndex = currentItems.findIndex((item) => this.sameItem(item, detail.item));
    const nextItem = currentItems[currentIndex + 1] ?? currentItems[currentIndex - 1] ?? null;
    const request = { comment };
    const actionRequest = action === 'validate'
      ? this.api.validate(detail.item.entityType, detail.item.entityId, request)
      : action === 'reject'
        ? this.api.reject(detail.item.entityType, detail.item.entityId, request)
        : this.api.requestChanges(detail.item.entityType, detail.item.entityId, request);

    this.actionLoading.set(true);
    this.actionMessage.set('');
    actionRequest
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.actionLoading.set(false);
          this.actionMessage.set(this.successMessage(action, selectNext));
          this.loadInbox(false);
          if (selectNext && nextItem) {
            this.selectItem(nextItem);
          } else if (selectNext) {
            this.selectedDetail.set(null);
          } else {
            this.selectedDetail.set(updated);
          }
        },
        error: () => {
          this.actionLoading.set(false);
          this.actionMessage.set('No se pudo registrar la acción.');
        }
      });
  }

  isSelected(item: ValidationItem): boolean {
    const detail = this.selectedDetail();
    return detail !== null && this.sameItem(item, detail.item);
  }

  dateLabel(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  resolutionLabel(detail: ValidationItemDetail): string {
    if (detail.validatedBy && detail.validatedAt) {
      return `${detail.validatedBy} · ${this.dateLabel(detail.validatedAt)}`;
    }
    if (detail.validatedBy) {
      return detail.validatedBy;
    }
    if (detail.validatedAt) {
      return this.dateLabel(detail.validatedAt);
    }
    return 'Sin resolución previa';
  }

  statusLabel(status: string | ValidationStatus): string {
    return validationStatusLabel(status);
  }

  statusTone(status: string | ValidationStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  entityTypeLabel(type: string | ValidationEntityType): string {
    return validationEntityTypeLabel(type);
  }

  analyzeWithAi(): void {
    const detail = this.selectedDetail();
    if (!detail) {
      return;
    }

    this.aiReviewLoading.set(true);
    this.aiSuggestionFeedback.set('');
    this.aiReviewApi.analyze(detail)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (review) => {
          this.aiReview.set(review);
          this.aiReviewLoading.set(false);
        },
        error: () => {
          this.aiReview.set(null);
          this.aiReviewLoading.set(false);
          this.aiSuggestionFeedback.set('No se pudo generar la revisión asistida para esta actividad.');
        }
      });
  }

  acceptAiSuggestion(): void {
    this.runAiSuggestionAction('accept');
  }

  rejectAiSuggestion(): void {
    this.runAiSuggestionAction('reject');
  }

  displayFieldValue(detail: ValidationItemDetail, fieldKey: string, fieldValue: string): string {
    if (!fieldValue) {
      return 'Sin dato';
    }
    if (fieldKey === 'Tipo') {
      return detail.item.entityType === 'EVENT_PARTICIPATION'
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

  private loadInbox(selectFirst: boolean): void {
    const value = this.filterForm.getRawValue();
    this.loading.set(true);
    this.loadError.set('');
    this.api.inbox({
      page: this.currentPage(),
      status: value.status,
      entityType: value.entityType === 'all' ? undefined : value.entityType,
      researcherId: this.toNumber(value.researcherId),
      researchUnitId: this.toNumber(value.researchUnitId),
      submittedFrom: this.toInstant(value.submittedFrom, false),
      submittedTo: this.toInstant(value.submittedTo, true),
      text: value.text || undefined,
      sort: value.sort
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
          if (selectFirst) {
            const first = result.content[0] ?? null;
            if (first) {
              this.selectItem(first);
            } else {
              this.detailLoading.set(false);
              this.detailError.set('');
              this.selectedDetail.set(null);
            }
          }
        },
        error: () => {
          this.result.set(null);
          this.loading.set(false);
          this.loadError.set('No se pudo cargar la bandeja de validación.');
          this.detailLoading.set(false);
          this.detailError.set('');
          this.selectedDetail.set(null);
        }
      });
  }

  private successMessage(action: ValidationAction, selectNext: boolean): string {
    if (action === 'requestChanges') {
      return 'Se solicitó al investigador que realice cambios.';
    }
    if (action === 'reject') {
      return 'La actividad quedó rechazada.';
    }
    return selectNext ? 'Actividad validada. Se abrió la siguiente revisión.' : 'Actividad validada correctamente.';
  }

  private sameItem(left: ValidationItem, right: ValidationItem): boolean {
    return left.entityType === right.entityType && left.entityId === right.entityId;
  }

  private toDisplayField(detail: ValidationItemDetail, key: string, value: string): DisplayField {
    return {
      key,
      value: this.displayFieldValue(detail, key, value)
    };
  }

  private runAiSuggestionAction(action: 'accept' | 'reject'): void {
    const review = this.aiReview();
    if (!review || this.aiSuggestionActionLoading()) {
      return;
    }

    const request = action === 'accept'
      ? this.aiSuggestionsApi.accept(review.aiSuggestion.id)
      : this.aiSuggestionsApi.reject(review.aiSuggestion.id);

    this.aiSuggestionActionLoading.set(true);
    this.aiSuggestionFeedback.set('');
    request
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updatedSuggestion) => {
          this.aiReview.update((current) => current ? { ...current, aiSuggestion: updatedSuggestion } : current);
          this.aiSuggestionActionLoading.set(false);
          this.aiSuggestionFeedback.set(
            action === 'accept'
              ? 'La sugerencia de IA quedó aceptada para seguimiento interno. La validación del registro sigue pendiente de tu decisión.'
              : 'La sugerencia de IA quedó rechazada. El registro no cambió y la revisión humana sigue abierta.'
          );
        },
        error: () => {
          this.aiSuggestionActionLoading.set(false);
          this.aiSuggestionFeedback.set('No se pudo registrar la decisión sobre esta sugerencia.');
        }
      });
  }

  private resetAiReviewState(): void {
    this.aiReview.set(null);
    this.aiReviewLoading.set(false);
    this.aiSuggestionActionLoading.set(false);
    this.aiSuggestionFeedback.set('');
  }

  private toNumber(value: string): number | undefined {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 && value !== '' ? parsed : undefined;
  }

  private toInstant(value: string, endOfDay: boolean): string | undefined {
    if (!value) {
      return undefined;
    }
    return `${value}T${endOfDay ? '23:59:59' : '00:00:00'}Z`;
  }
}
