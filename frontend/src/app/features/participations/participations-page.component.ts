import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AcademicMasterDataService } from '../../core/api/academic-master-data.service';
import { EventParticipation, PageResponse, ScientificEvent, ValidationStatus } from '../../core/api/api-models';
import { EventParticipationsApiService } from '../../core/api/event-participations-api.service';
import { ScientificEventsApiService } from '../../core/api/scientific-events-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { validationStatusLabel, validationStatusTone } from '../../shared/utils/display-labels';

type StatusFilter = ValidationStatus | 'all';

@Component({
  selector: 'rip-participations-page',
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
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Participaciones"
        [subtitle]="(result()?.totalElements || 0) + ' participaciones en eventos registradas.'"
        eyebrow="Actividad científica"
      >
        @if (canCreateParticipation()) {
          <a
            mat-flat-button
            color="primary"
            routerLink="/app/actividades/nueva"
            [queryParams]="navigationContext.returnQueryParams('Volver a participaciones')"
          >
            Añadir participación
          </a>
        }
      </rip-page-header>

      <mat-card appearance="outlined">
        <mat-card-content>
          <p class="section-kicker">Filtros</p>
          <form class="form-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline">
              <mat-label>Buscar</mat-label>
              <input matInput formControlName="text">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Evento</mat-label>
              <mat-select formControlName="eventId">
                <mat-option value="all">Todos los eventos</mat-option>
                @for (event of events(); track event.id) {
                  <mat-option [value]="event.id">{{ event.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>ID investigador</mat-label>
              <input matInput formControlName="researcherId" type="number" min="1">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Estado</mat-label>
              <mat-select formControlName="validationStatus">
                <mat-option value="all">Todos los estados</mat-option>
                @for (status of statuses; track status) {
                  <mat-option [value]="status">{{ statusLabel(status) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <div class="actions filter-actions">
              <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
              <button mat-flat-button color="primary" type="submit">Aplicar</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-content>
          @if ((result()?.content || []).length === 0) {
            <rip-empty-state title="Sin participaciones" message="No hay participaciones que coincidan con los filtros actuales." />
          } @else {
            <div class="item-list">
              @for (participation of result()?.content || []; track participation.id) {
                <a class="item-row interactive participation-row" [routerLink]="participationLink(participation.id)" [queryParams]="navigationContext.returnQueryParams('Volver a participaciones')">
                  <div class="item-main">
                    <strong class="item-title">{{ participation.title }}</strong>
                    <span>{{ participation.eventName }}</span>
                    <span>{{ participationTypeLabel(participation.participationTypeCode) }} · {{ participation.researcherName || 'Sin investigador' }}</span>
                    @if (participation.relatedPublicationTitle) {
                      <div class="chip-list">
                        <rip-tag-chip [label]="participation.relatedPublicationTitle" />
                      </div>
                    }
                  </div>
                  <div class="item-meta">
                    <span class="meta-text">{{ participation.participationDate || 'Sin fecha' }}</span>
                    <rip-status-chip [label]="statusLabel(participation.validationStatus)" [tone]="statusTone(participation.validationStatus)" />
                  </div>
                </a>
              }
            </div>
          }
        </mat-card-content>
      </mat-card>

      <div class="pagination">
        <button mat-button type="button" [disabled]="currentPage() === 0" (click)="goToPage(currentPage() - 1)">Anterior</button>
        <span>Página {{ currentPage() + 1 }} de {{ result()?.totalPages || 1 }}</span>
        <button mat-button type="button" [disabled]="result()?.last ?? true" (click)="goToPage(currentPage() + 1)">Siguiente</button>
      </div>
    </section>
  `,
  styles: [`
    .filter-actions {
      align-self: center;
    }

    .participation-row {
      grid-template-columns: minmax(0, 1fr) auto;
    }

    .item-main,
    .item-meta {
      display: grid;
      gap: 6px;
    }

    .item-main span,
    .meta-text {
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.4;
    }

    .item-meta {
      justify-items: end;
      align-items: start;
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      color: #5a6677;
    }

    @media (max-width: 720px) {
      .participation-row {
        grid-template-columns: 1fr;
      }

      .item-meta {
        justify-items: start;
      }
    }
  `]
})
export class ParticipationsPageComponent implements OnInit {
  private readonly api = inject(EventParticipationsApiService);
  private readonly eventsApi = inject(ScientificEventsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly masterData = inject(AcademicMasterDataService);
  readonly navigationContext = inject(NavigationContextService);

  readonly statuses: ValidationStatus[] = ['DRAFT', 'PENDING_VALIDATION', 'CHANGES_REQUESTED', 'VALIDATED', 'REJECTED'];
  readonly result = signal<PageResponse<EventParticipation> | null>(null);
  readonly events = signal<ScientificEvent[]>([]);
  readonly currentPage = signal(0);
  readonly canCreateParticipation = computed(() => this.auth.hasAnyRole(['RESEARCHER', 'ADMIN']));

  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    eventId: new FormControl<number | 'all'>('all', { nonNullable: true }),
    researcherId: new FormControl('', { nonNullable: true }),
    validationStatus: new FormControl<StatusFilter>('all', { nonNullable: true })
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();

    this.eventsApi.search({ size: 100 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.events.set(result.content));

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const page = Math.max(this.toNumber(params.get('page')) ?? 0, 0);
        const text = params.get('text') ?? '';
        const eventId = this.toNumber(params.get('eventId')) ?? 'all';
        const researcherId = params.get('researcherId') ?? '';
        const validationStatus = this.toStatusFilter(params.get('validationStatus'));

        this.currentPage.set(page);
        this.filterForm.patchValue({ text, eventId, researcherId, validationStatus }, { emitEvent: false });

        this.api.search({
          page,
          text: text || undefined,
          eventId: typeof eventId === 'number' ? eventId : undefined,
          researcherId: this.toNumber(researcherId),
          validationStatus: validationStatus === 'all' ? undefined : validationStatus
        })
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((result) => this.result.set(result));
      });
  }

  applyFilters(): void {
    const value = this.filterForm.getRawValue();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: null,
        text: value.text || null,
        eventId: value.eventId === 'all' ? null : value.eventId,
        researcherId: value.researcherId || null,
        validationStatus: value.validationStatus === 'all' ? null : value.validationStatus
      },
      queryParamsHandling: 'merge'
    });
  }

  clearFilters(): void {
    this.filterForm.reset({ text: '', eventId: 'all', researcherId: '', validationStatus: 'all' });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: null, text: null, eventId: null, researcherId: null, validationStatus: null },
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

  participationTypeLabel(code: string): string {
    return this.masterData.eventParticipationTypeLabel(code);
  }

  statusLabel(status: ValidationStatus): string {
    return validationStatusLabel(status);
  }

  statusTone(status: ValidationStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  participationLink(participationId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/participaciones')
      ? ['/admin/participaciones', String(participationId)]
      : ['/participations', String(participationId)];
  }

  private toNumber(value: string | null): number | undefined {
    const parsed = Number(value);
    return Number.isFinite(parsed) && value !== '' && value !== 'all' ? parsed : undefined;
  }

  private toStatusFilter(value: string | null): StatusFilter {
    return this.statuses.includes(value as ValidationStatus) ? value as ValidationStatus : 'all';
  }
}
