import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AuditApiService } from '../../core/api/audit-api.service';
import { ActivityAuditAction, ActivityAuditEvent, PageResponse, ValidationEntityType } from '../../core/api/api-models';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import {
  activityAuditActionLabel,
  activityAuditActionTone,
  validationEntityTypeLabel,
  validationStatusLabel
} from '../../shared/utils/display-labels';

type EntityTypeFilter = ValidationEntityType | 'all';
type ActionFilter = ActivityAuditAction | 'all';

@Component({
  selector: 'rip-audit-page',
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
    TagChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Auditoría"
        subtitle="Consulta cambios operativos, validaciones y actividad institucional reciente."
        eyebrow="Gestión institucional"
      >
        <a mat-button routerLink="/admin/panel">Panel institucional</a>
      </rip-page-header>

      <div class="toolbar-meta">
        <p>Consulta la trazabilidad operativa sin exponer detalles de mantenimiento en el portal público.</p>
        <div class="summary-strip">
          <span class="summary-chip">
            <strong>{{ result()?.totalElements || 0 }}</strong>
            <span>eventos cargados</span>
          </span>
          <span class="summary-chip">
            <strong>{{ filteredEvents().length }}</strong>
            <span>visibles tras filtros</span>
          </span>
        </div>
      </div>

      <mat-card appearance="outlined">
        <mat-card-content>
          <p class="section-kicker">Filtros</p>
          <form class="form-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline">
              <mat-label>Tipo de actividad</mat-label>
              <mat-select formControlName="entityType">
                <mat-option value="all">Todos los tipos</mat-option>
                @for (type of entityTypes; track type) {
                  <mat-option [value]="type">{{ entityTypeLabel(type) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Acción</mat-label>
              <mat-select formControlName="action">
                <mat-option value="all">Todas las acciones</mat-option>
                @for (action of actions; track action) {
                  <mat-option [value]="action">{{ actionLabel(action) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Usuario</mat-label>
              <input matInput formControlName="user">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Fecha desde</mat-label>
              <input matInput type="date" formControlName="dateFrom">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Fecha hasta</mat-label>
              <input matInput type="date" formControlName="dateTo">
            </mat-form-field>

            <div class="actions filter-actions">
              <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
              <button mat-flat-button color="primary" type="submit" [disabled]="loading()">Aplicar</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-content class="table-card">
          <div class="table-header">
            <div>
              <p class="section-kicker">Eventos</p>
              <h2>{{ filteredEvents().length }} registros visibles</h2>
            </div>
            <div class="pagination">
              <button mat-button type="button" [disabled]="currentPage() === 0 || loading()" (click)="goToPage(currentPage() - 1)">Anterior</button>
              <span>Página {{ currentPage() + 1 }} de {{ result()?.totalPages || 1 }}</span>
              <button mat-button type="button" [disabled]="(result()?.last ?? true) || loading()" (click)="goToPage(currentPage() + 1)">Siguiente</button>
            </div>
          </div>

          @if (loading()) {
            <rip-loading-state message="Cargando eventos de auditoría..." />
          } @else if (errorMessage()) {
            <div class="state-card">
              <rip-error-state [message]="errorMessage()" />
              <div class="actions">
                <button mat-flat-button color="primary" type="button" (click)="reload()">Reintentar</button>
              </div>
            </div>
          } @else if (filteredEvents().length === 0) {
            <rip-empty-state title="Sin eventos" message="No hay eventos que coincidan con los filtros actuales." />
          } @else {
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Actor</th>
                    <th>Acción</th>
                    <th>Entidad</th>
                    <th>Fecha y hora</th>
                    <th>Estado anterior</th>
                    <th>Estado nuevo</th>
                    <th>Comentario</th>
                  </tr>
                </thead>
                <tbody>
                  @for (event of filteredEvents(); track event.id) {
                    <tr>
                      <td>
                        <div class="cell-stack">
                          <strong>{{ actorLabel(event) }}</strong>
                          <span>{{ actorRoleLabel(event) }}</span>
                        </div>
                      </td>
                      <td>
                        <rip-status-chip [label]="actionLabel(event.action)" [tone]="actionTone(event.action)" />
                      </td>
                      <td>
                        @if (entityDetailLink(event); as link) {
                          <a [routerLink]="link" [queryParams]="navigationContext.returnQueryParams('Volver a auditoría')">{{ entityLabel(event) }}</a>
                        } @else {
                          {{ entityLabel(event) }}
                        }
                      </td>
                      <td>{{ formatDate(event.occurredAt) }}</td>
                      <td>
                        @if (event.previousStatus) {
                          <rip-status-chip [label]="statusValue(event.previousStatus)" [tone]="statusTone(event.previousStatus)" />
                        } @else {
                          <rip-tag-chip label="Sin cambio" />
                        }
                      </td>
                      <td>
                        @if (event.newStatus) {
                          <rip-status-chip [label]="statusValue(event.newStatus)" [tone]="statusTone(event.newStatus)" />
                        } @else {
                          <rip-tag-chip label="Sin cambio" />
                        }
                      </td>
                      <td>{{ event.comment || 'Sin comentario' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </mat-card-content>
      </mat-card>
    </section>
  `,
  styles: [`
    .table-card {
      display: grid;
      gap: 18px;
    }

    .filter-actions {
      align-self: center;
    }

    .table-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
    }

    .table-header h2 {
      margin: 0;
      color: #142033;
      font-size: 1.05rem;
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      color: #5a6677;
    }

    .state-copy {
      margin: 0;
      color: #667487;
    }

    .cell-stack {
      display: grid;
      gap: 4px;
    }

    .cell-stack strong {
      color: #142033;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .cell-stack span {
      color: #667487;
      font-size: 0.84rem;
    }

    td {
      vertical-align: top;
    }

    @media (max-width: 720px) {
      .table-header {
        display: grid;
      }

      .pagination {
        justify-content: flex-start;
      }
    }
  `]
})
export class AuditPageComponent implements OnInit {
  private readonly api = inject(AuditApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly entityTypes: ValidationEntityType[] = ['PUBLICATION', 'EVENT_PARTICIPATION', 'RESEARCHER', 'RESEARCHER_AFFILIATION', 'RESEARCH_UNIT'];
  readonly actions: ActivityAuditAction[] = [
    'CREATED',
    'UPDATED',
    'SUBMITTED',
    'VALIDATED',
    'REJECTED',
    'CHANGES_REQUESTED',
    'ARCHIVED',
    'DELETED',
    'RESTORED'
  ];
  readonly result = signal<PageResponse<ActivityAuditEvent> | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly currentPage = signal(0);

  readonly filterForm = new FormGroup({
    entityType: new FormControl<EntityTypeFilter>('all', { nonNullable: true }),
    action: new FormControl<ActionFilter>('all', { nonNullable: true }),
    user: new FormControl('', { nonNullable: true }),
    dateFrom: new FormControl('', { nonNullable: true }),
    dateTo: new FormControl('', { nonNullable: true })
  });

  readonly filteredEvents = computed(() => {
    const filters = this.filterForm.getRawValue();
    const userFilter = filters.user.trim().toLocaleLowerCase('es-ES');
    const dateFrom = filters.dateFrom ? new Date(`${filters.dateFrom}T00:00:00`) : null;
    const dateTo = filters.dateTo ? new Date(`${filters.dateTo}T23:59:59`) : null;

    return (this.result()?.content || []).filter((event) => {
      const actorMatch = userFilter.length === 0
        || this.actorLabel(event).toLocaleLowerCase('es-ES').includes(userFilter)
        || (event.actorRole || '').toLocaleLowerCase('es-ES').includes(userFilter);
      const occurredAt = new Date(event.occurredAt);
      const fromMatch = dateFrom === null || occurredAt >= dateFrom;
      const toMatch = dateTo === null || occurredAt <= dateTo;
      return actorMatch && fromMatch && toMatch;
    });
  });

  ngOnInit(): void {
    this.reload();
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.loadEvents();
  }

  clearFilters(): void {
    this.filterForm.reset({
      entityType: 'all',
      action: 'all',
      user: '',
      dateFrom: '',
      dateTo: ''
    });
    this.currentPage.set(0);
    this.loadEvents();
  }

  goToPage(page: number): void {
    this.currentPage.set(Math.max(page, 0));
    this.loadEvents();
  }

  reload(): void {
    this.loadEvents();
  }

  actorLabel(event: ActivityAuditEvent): string {
    if (event.actorDisplayName) {
      return event.actorDisplayName;
    }
    if (event.actorUserId !== null) {
      return `Usuario #${event.actorUserId}`;
    }
    return 'Sistema';
  }

  actorRoleLabel(event: ActivityAuditEvent): string {
    return event.actorRole || 'Sin rol';
  }

  actionLabel(action: ActivityAuditAction): string {
    return activityAuditActionLabel(action);
  }

  actionTone(action: ActivityAuditAction): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return activityAuditActionTone(action);
  }

  entityTypeLabel(type: ValidationEntityType): string {
    return validationEntityTypeLabel(type);
  }

  entityLabel(event: ActivityAuditEvent): string {
    return `${validationEntityTypeLabel(event.entityType)} #${event.entityId}`;
  }

  entityDetailLink(event: ActivityAuditEvent): string[] | null {
    switch (event.entityType) {
      case 'PUBLICATION':
        return ['/admin/publicaciones', String(event.entityId)];
      case 'EVENT_PARTICIPATION':
        return ['/admin/participaciones', String(event.entityId)];
      case 'RESEARCHER':
        return ['/admin/investigadores', String(event.entityId)];
      case 'RESEARCH_UNIT':
        return ['/admin/unidades', String(event.entityId)];
      default:
        return null;
    }
  }

  statusValue(status: ActivityAuditEvent['previousStatus']): string {
    return status ? validationStatusLabel(status) : 'Sin cambio';
  }

  statusTone(status: NonNullable<ActivityAuditEvent['previousStatus']>): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    if (status === 'VALIDATED') {
      return 'success';
    }
    if (status === 'PENDING_VALIDATION') {
      return 'info';
    }
    if (status === 'CHANGES_REQUESTED') {
      return 'warning';
    }
    if (status === 'REJECTED') {
      return 'danger';
    }
    return 'neutral';
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  private loadEvents(): void {
    const filters = this.filterForm.getRawValue();
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.events({
      entityType: filters.entityType === 'all' ? undefined : filters.entityType,
      action: filters.action === 'all' ? undefined : filters.action,
      page: this.currentPage(),
      size: 50
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
        },
        error: () => {
          this.result.set(null);
          this.errorMessage.set('No se pudieron cargar los eventos de auditoría.');
          this.loading.set(false);
        }
      });
  }
}
