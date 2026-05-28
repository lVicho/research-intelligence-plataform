import { Component, effect, inject, input, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import { AuditApiService } from '../../core/api/audit-api.service';
import { ActivityAuditEvent, PageResponse, ValidationEntityType } from '../../core/api/api-models';
import { activityAuditActionLabel, activityAuditActionTone, validationStatusLabel } from '../utils/display-labels';
import { EmptyStateComponent } from './empty-state.component';
import { ErrorStateComponent } from './error-state.component';
import { LoadingStateComponent } from './loading-state.component';
import { StatusChipComponent } from './status-chip.component';

@Component({
  selector: 'rip-audit-history-panel',
  standalone: true,
  imports: [MatCardModule, EmptyStateComponent, ErrorStateComponent, LoadingStateComponent, StatusChipComponent],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>{{ title() }}</mat-card-title>
      </mat-card-header>
      <mat-card-content class="history-content">
        @if (subtitle()) {
          <p class="section-kicker">{{ subtitle() }}</p>
        }

        @if (loading()) {
          <rip-loading-state message="Cargando historial de validación..." />
        } @else if (errorMessage()) {
          <rip-error-state [message]="errorMessage()" />
        } @else if ((result()?.content || []).length === 0) {
          <rip-empty-state title="Sin historial" message="Todavía no hay eventos de auditoría para este registro." />
        } @else {
          <div class="history-list">
            @for (event of result()?.content || []; track event.id) {
              <article class="history-item">
                <div class="history-topline">
                  <div class="history-heading">
                    <strong>{{ actorLabel(event) }}</strong>
                    <span>{{ formatDate(event.occurredAt) }}</span>
                  </div>
                  <rip-status-chip [label]="actionLabel(event.action)" [tone]="actionTone(event.action)" />
                </div>

                <div class="history-statuses">
                  <span><strong>Estado anterior:</strong> {{ statusValue(event.previousStatus) }}</span>
                  <span><strong>Estado nuevo:</strong> {{ statusValue(event.newStatus) }}</span>
                </div>

                @if (event.comment) {
                  <p class="history-comment">{{ event.comment }}</p>
                }
              </article>
            }
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .history-content {
      display: grid;
      gap: 16px;
    }
    .history-list {
      display: grid;
      gap: 12px;
    }

    .history-item {
      display: grid;
      gap: 10px;
      padding: 14px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      background: #fbfcfe;
    }

    .history-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
    }

    .history-heading {
      display: grid;
      gap: 4px;
      min-width: 0;
    }

    .history-heading strong {
      color: #142033;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .history-heading span,
    .history-statuses {
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.45;
    }

    .history-statuses {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 16px;
    }

    .history-comment {
      margin: 0;
      padding: 12px;
      border-radius: 8px;
      background: #f7fbff;
      color: #243044;
      line-height: 1.5;
    }

    @media (max-width: 720px) {
      .history-topline {
        display: grid;
      }
    }
  `]
})
export class AuditHistoryPanelComponent {
  private readonly api = inject(AuditApiService);

  readonly entityType = input.required<ValidationEntityType>();
  readonly entityId = input.required<number>();
  readonly title = input('Historial');
  readonly subtitle = input('');
  readonly result = signal<PageResponse<ActivityAuditEvent> | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');

  constructor() {
    effect((onCleanup) => {
      const entityType = this.entityType();
      const entityId = this.entityId();

      this.loading.set(true);
      this.errorMessage.set('');

      const subscription = this.api.entityEvents(entityType, entityId, 0, 10)
        .subscribe({
          next: (result) => {
            this.result.set(result);
            this.loading.set(false);
          },
          error: () => {
            this.result.set(null);
            this.errorMessage.set('No se pudo cargar el historial de auditoría.');
            this.loading.set(false);
          }
        });

      onCleanup(() => subscription.unsubscribe());
    });
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

  actionLabel(action: ActivityAuditEvent['action']): string {
    return activityAuditActionLabel(action);
  }

  actionTone(action: ActivityAuditEvent['action']): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return activityAuditActionTone(action);
  }

  statusValue(status: ActivityAuditEvent['previousStatus']): string {
    return status ? validationStatusLabel(status) : 'Sin cambio';
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }
}
