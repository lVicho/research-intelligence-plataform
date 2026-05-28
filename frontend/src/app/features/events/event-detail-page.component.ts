import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AcademicMasterDataService } from '../../core/api/academic-master-data.service';
import { ScientificEvent, ScientificEventRequest, ValidationStatus, Venue } from '../../core/api/api-models';
import { ScientificEventsApiService } from '../../core/api/scientific-events-api.service';
import {
  NavigationContextQueryParams,
  NavigationContextService
} from '../../core/navigation/navigation-context.service';
import { VenuesApiService } from '../../core/api/venues-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { AuditHistoryPanelComponent } from '../../shared/components/audit-history-panel.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { contentAccessErrorMessage } from '../../shared/utils/api-error-messages';
import { validationStatusLabel, validationStatusTone } from '../../shared/utils/display-labels';

@Component({
  selector: 'rip-event-detail-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    AuditHistoryPanelComponent,
    ErrorStateComponent,
    PageHeaderComponent,
    StatusChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        [title]="eventId() === null ? 'Nuevo evento' : (currentEvent()?.name || 'Detalle de evento')"
        [subtitle]="eventId() === null ? 'Crear un evento científico y definir su estado de catálogo.' : eventSubtitle()"
        eyebrow="Eventos"
        [compact]="true"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
        @if (canManageMasterData()) {
          <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="eventForm.invalid || saving()">
            Guardar
          </button>
        }
      </rip-page-header>

      @if (loadError()) {
        <rip-error-state [message]="loadError()" />
      }

      @if (!loadError()) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <div class="policy-banner">
              <div class="policy-copy">
                <p class="section-kicker">Política de catálogo</p>
                <h2>Gestión administrativa</h2>
                <p>
                  Los eventos científicos se gestionan como registros de catálogo. No se envían al buzón de validación:
                  administración edita aquí el estado, la visibilidad y el archivo lógico.
                </p>
              </div>
              <div class="policy-chips">
                <rip-status-chip
                  [label]="statusLabel(eventForm.controls.validationStatus.value)"
                  [tone]="statusTone(eventForm.controls.validationStatus.value)"
                />
                <rip-status-chip [label]="activeLabel(eventForm.controls.active.value)" [tone]="eventForm.controls.active.value ? 'success' : 'warning'" />
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      }

      @if (currentEvent(); as event) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <div class="summary-header">
              <div class="summary-copy">
                <h2>{{ event.name }}</h2>
                <p>{{ eventTypeLabel(event.eventTypeCode) }}</p>
              </div>
              <rip-status-chip [label]="statusLabel(event.validationStatus)" [tone]="statusTone(event.validationStatus)" />
            </div>

            <div class="metadata-grid">
              <div class="metadata-item">
                <span>Estado de catálogo</span>
                <strong>{{ statusLabel(event.validationStatus) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Situación</span>
                <strong>{{ activeLabel(event.active) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Fechas</span>
                <strong>{{ eventDateLabel(event) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Canal</span>
                <strong>{{ venueName(event.venueId) || 'Sin canal asociado' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Ubicación</span>
                <strong>{{ locationLabel(event) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Organización</span>
                <strong>{{ event.organizer || 'Sin organización' }}</strong>
              </div>
            </div>

            @if (event.description) {
              <section class="detail-block">
                <h3>Descripción</h3>
                <p>{{ event.description }}</p>
              </section>
            }

            @if (event.website || event.evidenceUrl) {
              <section class="link-grid">
                @if (event.website) {
                  <div class="link-card">
                    <span>Sitio web</span>
                    <a [href]="event.website" target="_blank" rel="noopener noreferrer">{{ event.website }}</a>
                  </div>
                }
                @if (event.evidenceUrl) {
                  <div class="link-card">
                    <span>Fuente o evidencia</span>
                    <a [href]="event.evidenceUrl" target="_blank" rel="noopener noreferrer">{{ event.evidenceUrl }}</a>
                  </div>
                }
              </section>
            }
          </mat-card-content>
        </mat-card>

        <rip-audit-history-panel
          [entityType]="'SCIENTIFIC_EVENT'"
          [entityId]="event.id"
          title="Historial del evento"
          subtitle="Consulta altas, cambios de estado de catálogo y archivado lógico."
        />
      }

      @if (canManageMasterData() && !loadError()) {
        <div class="section-stack" [formGroup]="eventForm">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Identificación y catálogo</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p class="section-description">Define el nombre del evento y el estado interno con el que se gestionará en catálogo.</p>
              <div class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Nombre</mat-label>
                  <input matInput formControlName="name">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Edición</mat-label>
                  <input matInput formControlName="edition">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Tipo</mat-label>
                  <mat-select formControlName="eventTypeCode">
                    @for (typeCode of eventTypeCodes(); track typeCode) {
                      <mat-option [value]="typeCode">{{ eventTypeLabel(typeCode) }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Estado de catálogo</mat-label>
                  <mat-select formControlName="validationStatus">
                    @for (status of catalogStatuses; track status) {
                      <mat-option [value]="status">{{ statusLabel(status) }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <div class="checkbox-row wide-field">
                  <mat-checkbox formControlName="active">Registro activo</mat-checkbox>
                  <span>Desactívalo para archivar el evento sin eliminarlo ni perder referencias.</span>
                </div>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Ubicación y calendario</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p class="section-description">Relaciona el evento con su canal, organiza las fechas y deja clara la sede registrada.</p>
              <div class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Canal asociado</mat-label>
                  <mat-select formControlName="venueId">
                    <mat-option [value]="null">Sin canal asociado</mat-option>
                    @for (venue of venues(); track venue.id) {
                      <mat-option [value]="venue.id">{{ venue.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Fecha de inicio</mat-label>
                  <input matInput type="date" formControlName="startDate">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Fecha de fin</mat-label>
                  <input matInput type="date" formControlName="endDate">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Ciudad</mat-label>
                  <input matInput formControlName="city">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>País</mat-label>
                  <input matInput formControlName="country">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Organizador</mat-label>
                  <input matInput formControlName="organizer">
                </mat-form-field>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Descripción y evidencias</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p class="section-description">Añade contexto editorial y la URL de referencia que justifica la información registrada.</p>
              <div class="form-grid">
                <mat-form-field appearance="outline" class="wide-field">
                  <mat-label>Sitio web</mat-label>
                  <input matInput formControlName="website">
                </mat-form-field>
                <mat-form-field appearance="outline" class="wide-field">
                  <mat-label>Fuente o evidencia</mat-label>
                  <input matInput formControlName="evidenceUrl">
                </mat-form-field>
                <mat-form-field appearance="outline" class="wide-field">
                  <mat-label>Descripción</mat-label>
                  <textarea matInput rows="5" formControlName="description"></textarea>
                </mat-form-field>
              </div>
            </mat-card-content>
          </mat-card>
        </div>
      }
    </section>
  `,
  styles: [`
    .policy-banner,
    .summary-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
    }

    .policy-copy,
    .summary-copy {
      min-width: 0;
    }

    .policy-copy h2,
    .summary-copy h2 {
      margin: 0;
      color: #142033;
      font-size: 1.2rem;
      font-weight: 760;
    }

    .policy-copy p,
    .summary-copy p {
      margin: 6px 0 0;
      color: #667487;
      line-height: 1.55;
    }

    .policy-chips {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 8px;
    }

    .detail-block {
      display: grid;
      gap: 8px;
      margin-top: 20px;
    }

    .detail-block h3 {
      margin: 0;
      color: #172235;
      font-size: 1rem;
    }

    .detail-block p {
      margin: 0;
      color: #475569;
      line-height: 1.6;
    }

    .link-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 12px;
      margin-top: 20px;
    }

    .link-card {
      display: grid;
      gap: 8px;
      padding: 14px 15px;
      border: 1px solid #e0e8ef;
      border-radius: 14px;
      background: linear-gradient(180deg, #ffffff, #f8fbfd);
    }

    .link-card span {
      color: #667487;
      font-size: 0.75rem;
      font-weight: 760;
      letter-spacing: 0.03em;
      text-transform: uppercase;
    }

    .link-card a {
      color: #1f5e7a;
      overflow-wrap: anywhere;
      text-decoration: none;
    }

    .link-card a:hover {
      text-decoration: underline;
    }

    .checkbox-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 14px 15px;
      border: 1px dashed #d7e3ec;
      border-radius: 14px;
      background: #fbfdff;
      color: #5e6b7c;
      line-height: 1.5;
      flex-wrap: wrap;
    }

    .wide-field {
      grid-column: 1 / -1;
    }

    @media (max-width: 720px) {
      .policy-banner,
      .summary-header {
        display: grid;
      }

      .policy-chips {
        justify-content: flex-start;
      }
    }
  `]
})
export class EventDetailPageComponent implements OnInit {
  private readonly api = inject(ScientificEventsApiService);
  private readonly venuesApi = inject(VenuesApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly masterData = inject(AcademicMasterDataService);
  private readonly navigationContext = inject(NavigationContextService);

  readonly eventId = signal<number | null>(null);
  readonly currentEvent = signal<ScientificEvent | null>(null);
  readonly venues = signal<Venue[]>([]);
  readonly loadError = signal('');
  readonly saving = signal(false);
  readonly eventTypeCodes = computed(() => this.masterData.eventTypeCodes());
  readonly catalogStatuses: ValidationStatus[] = ['DRAFT', 'PENDING_VALIDATION', 'VALIDATED', 'CHANGES_REQUESTED', 'REJECTED'];
  readonly canManageMasterData = () => this.auth.hasAnyRole(['ADMIN']);

  readonly eventForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    edition: new FormControl<string | null>(null),
    eventTypeCode: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    startDate: new FormControl<string | null>(null),
    endDate: new FormControl<string | null>(null),
    city: new FormControl<string | null>(null),
    country: new FormControl<string | null>(null),
    organizer: new FormControl<string | null>(null),
    website: new FormControl<string | null>(null),
    description: new FormControl<string | null>(null),
    evidenceUrl: new FormControl<string | null>(null),
    venueId: new FormControl<number | null>(null),
    active: new FormControl(true, { nonNullable: true }),
    validationStatus: new FormControl<ValidationStatus>('PENDING_VALIDATION', { nonNullable: true })
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();
    this.venuesApi.search({ size: 100 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.venues.set(result.content));

    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.loadEvent(this.parseId(params.get('id'))));
  }

  save(): void {
    if (!this.canManageMasterData() || this.eventForm.invalid || this.saving()) {
      return;
    }
    const request = this.toRequest();
    const eventId = this.eventId();
    this.saving.set(true);
    const operation = eventId === null ? this.api.create(request) : this.api.update(eventId, request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (event) => {
        this.saving.set(false);
        void this.router.navigate(this.eventDetailLink(event.id), { queryParams: this.detailQueryParams() });
      },
      error: () => this.saving.set(false)
    });
  }

  eventTypeLabel(code: string): string {
    return this.masterData.eventTypeLabel(code);
  }

  eventSubtitle(): string {
    const event = this.currentEvent();
    if (!event) {
      return 'Detalle de evento';
    }
    return [
      this.eventTypeLabel(event.eventTypeCode),
      this.eventDateLabel(event),
      this.statusLabel(event.validationStatus)
    ].filter(Boolean).join(' · ');
  }

  venueName(venueId: number | null): string | null {
    return venueId === null ? null : this.venues().find((venue) => venue.id === venueId)?.name ?? null;
  }

  locationLabel(event: ScientificEvent): string {
    return [event.city, event.country].filter((value): value is string => !!value).join(', ') || 'Sin ubicación';
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

  activeLabel(active: boolean): string {
    return active ? 'Activo' : 'Archivado';
  }

  statusLabel(status: ValidationStatus): string {
    return validationStatusLabel(status);
  }

  statusTone(status: ValidationStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, this.fallbackBackPath(), 'Volver a eventos').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, this.fallbackBackPath(), 'Volver a eventos');
  }

  private eventDetailLink(eventId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/eventos')
      ? ['/admin/eventos', String(eventId)]
      : ['/events', String(eventId)];
  }

  private fallbackBackPath(): string {
    return this.navigationContext.isCurrentPath('/admin/eventos') ? '/admin/eventos' : '/events';
  }

  private loadEvent(eventId: number | null): void {
    this.eventId.set(eventId);
    this.loadError.set('');

    if (eventId === null) {
      this.currentEvent.set(null);
      this.eventForm.reset({
        name: '',
        edition: null,
        eventTypeCode: this.eventTypeCodes()[0] ?? '',
        startDate: null,
        endDate: null,
        city: null,
        country: null,
        organizer: null,
        website: null,
        description: null,
        evidenceUrl: null,
        venueId: null,
        active: true,
        validationStatus: 'PENDING_VALIDATION'
      });
      return;
    }

    this.api.get(eventId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          this.currentEvent.set(event);
          this.eventForm.patchValue({
            name: event.name,
            edition: event.edition,
            eventTypeCode: event.eventTypeCode,
            startDate: event.startDate,
            endDate: event.endDate,
            city: event.city,
            country: event.country,
            organizer: event.organizer,
            website: event.website,
            description: event.description,
            evidenceUrl: event.evidenceUrl,
            venueId: event.venueId,
            active: event.active,
            validationStatus: event.validationStatus
          });
        },
        error: (error: unknown) => {
          this.currentEvent.set(null);
          this.loadError.set(contentAccessErrorMessage(error, 'No se pudo cargar el evento.'));
        }
      });
  }

  private toRequest(): ScientificEventRequest {
    const value = this.eventForm.getRawValue();
    return {
      name: value.name,
      edition: this.emptyToNull(value.edition),
      eventTypeCode: value.eventTypeCode,
      startDate: this.emptyToNull(value.startDate),
      endDate: this.emptyToNull(value.endDate),
      city: this.emptyToNull(value.city),
      country: this.emptyToNull(value.country),
      organizer: this.emptyToNull(value.organizer),
      website: this.emptyToNull(value.website),
      description: this.emptyToNull(value.description),
      evidenceUrl: this.emptyToNull(value.evidenceUrl),
      venueId: value.venueId,
      active: value.active,
      validationStatus: value.validationStatus
    };
  }

  private detailQueryParams(): NavigationContextQueryParams {
    const resolved = this.navigationContext.resolve(this.route, this.fallbackBackPath(), 'Volver a eventos');
    return {
      returnTo: resolved.path,
      returnLabel: resolved.label
    };
  }

  private parseId(value: string | null): number | null {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private emptyToNull(value: string | null): string | null {
    return value === null || value.trim() === '' ? null : value;
  }
}
