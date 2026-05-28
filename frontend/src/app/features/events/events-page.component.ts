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
import { PageResponse, ScientificEvent, Venue } from '../../core/api/api-models';
import { ScientificEventsApiService } from '../../core/api/scientific-events-api.service';
import { VenuesApiService } from '../../core/api/venues-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { validationStatusLabel, validationStatusTone } from '../../shared/utils/display-labels';

@Component({
  selector: 'rip-events-page',
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
    StatusChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Eventos"
        [subtitle]="(result()?.totalElements || 0) + ' eventos científicos registrados.'"
        eyebrow="Encuentros académicos"
      >
        @if (canManageMasterData()) {
          <a
            mat-flat-button
            color="primary"
            [routerLink]="newEventLink()"
            [queryParams]="navigationContext.returnQueryParams('Volver a eventos')"
          >
            Añadir evento
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
              <mat-label>Tipo</mat-label>
              <mat-select formControlName="eventTypeCode">
                <mat-option value="all">Todos los tipos</mat-option>
                @for (typeCode of eventTypeCodes(); track typeCode) {
                  <mat-option [value]="typeCode">{{ eventTypeLabel(typeCode) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Canal</mat-label>
              <mat-select formControlName="venueId">
                <mat-option value="all">Todos los canales</mat-option>
                @for (venue of venues(); track venue.id) {
                  <mat-option [value]="venue.id">{{ venue.name }}</mat-option>
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
            <rip-empty-state title="Sin eventos" message="No hay eventos que coincidan con los filtros actuales." />
          } @else {
            <div class="item-list">
              @for (event of result()?.content || []; track event.id) {
                <a class="item-row interactive" [routerLink]="eventLink(event.id)" [queryParams]="navigationContext.returnQueryParams('Volver a eventos')">
                  <div class="item-main">
                    <strong class="item-title">{{ event.name }}</strong>
                    <span>{{ eventTypeLabel(event.eventTypeCode) }}</span>
                    <span>{{ eventDateLabel(event) }}</span>
                  </div>
                  <div class="item-meta">
                    <span class="meta-text">{{ venueName(event.venueId) || event.city || event.country || 'Sin sede' }}</span>
                    <rip-status-chip [label]="statusLabel(event.validationStatus)" [tone]="statusTone(event.validationStatus)" />
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
      .item-meta {
        justify-items: start;
      }
    }
  `]
})
export class EventsPageComponent implements OnInit {
  private readonly api = inject(ScientificEventsApiService);
  private readonly venuesApi = inject(VenuesApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly masterData = inject(AcademicMasterDataService);
  readonly navigationContext = inject(NavigationContextService);

  readonly result = signal<PageResponse<ScientificEvent> | null>(null);
  readonly venues = signal<Venue[]>([]);
  readonly currentPage = signal(0);
  readonly eventTypeCodes = computed(() => this.masterData.eventTypeCodes());
  readonly canManageMasterData = () => this.auth.hasAnyRole(['ADMIN']);

  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    eventTypeCode: new FormControl('all', { nonNullable: true }),
    venueId: new FormControl<number | 'all'>('all', { nonNullable: true })
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();

    this.venuesApi.search({ size: 100 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.venues.set(result.content));

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const page = Math.max(this.toNumber(params.get('page')) ?? 0, 0);
        const text = params.get('text') ?? '';
        const eventTypeCode = params.get('eventTypeCode') ?? 'all';
        const venueId = this.toNumber(params.get('venueId')) ?? 'all';

        this.currentPage.set(page);
        this.filterForm.patchValue({ text, eventTypeCode, venueId }, { emitEvent: false });

        this.api.search({
          page,
          text: text || undefined,
          eventTypeCode: eventTypeCode === 'all' ? undefined : eventTypeCode,
          venueId: typeof venueId === 'number' ? venueId : undefined
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
        eventTypeCode: value.eventTypeCode === 'all' ? null : value.eventTypeCode,
        venueId: value.venueId === 'all' ? null : value.venueId
      },
      queryParamsHandling: 'merge'
    });
  }

  clearFilters(): void {
    this.filterForm.reset({ text: '', eventTypeCode: 'all', venueId: 'all' });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: null, text: null, eventTypeCode: null, venueId: null },
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

  eventTypeLabel(code: string): string {
    return this.masterData.eventTypeLabel(code);
  }

  venueName(venueId: number | null): string | null {
    return venueId === null ? null : this.venues().find((venue) => venue.id === venueId)?.name ?? null;
  }

  eventDateLabel(event: ScientificEvent): string {
    if (!event.startDate && !event.endDate) {
      return 'Sin fecha';
    }
    if (event.startDate && event.endDate && event.startDate !== event.endDate) {
      return `${event.startDate} - ${event.endDate}`;
    }
    return event.startDate || event.endDate || 'Sin fecha';
  }

  statusLabel(status: ScientificEvent['validationStatus']): string {
    return validationStatusLabel(status);
  }

  statusTone(status: ScientificEvent['validationStatus']): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  eventLink(eventId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/eventos') ? ['/admin/eventos', String(eventId)] : ['/events', String(eventId)];
  }

  newEventLink(): string[] {
    return this.navigationContext.isCurrentPath('/admin/eventos') ? ['/admin/eventos/new'] : ['/events/new'];
  }

  private toNumber(value: string | null): number | undefined {
    const parsed = Number(value);
    return Number.isFinite(parsed) && value !== 'all' ? parsed : undefined;
  }
}
