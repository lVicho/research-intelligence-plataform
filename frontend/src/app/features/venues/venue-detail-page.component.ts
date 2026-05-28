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
import { Publisher, ValidationStatus, Venue, VenueRequest } from '../../core/api/api-models';
import { PublishersApiService } from '../../core/api/publishers-api.service';
import { VenuesApiService } from '../../core/api/venues-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import {
  NavigationContextQueryParams,
  NavigationContextService
} from '../../core/navigation/navigation-context.service';
import { AuditHistoryPanelComponent } from '../../shared/components/audit-history-panel.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { contentAccessErrorMessage } from '../../shared/utils/api-error-messages';
import { validationStatusLabel, validationStatusTone } from '../../shared/utils/display-labels';

@Component({
  selector: 'rip-venue-detail-page',
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
        [title]="venueId() === null ? 'Nuevo canal' : (currentVenue()?.name || 'Detalle de canal')"
        [subtitle]="venueId() === null ? 'Crear un canal académico o editorial y definir su estado de catálogo.' : venueSubtitle()"
        eyebrow="Canales"
        [compact]="true"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
        @if (canManageMasterData()) {
          <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="venueForm.invalid || saving()">
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
                <h2>Curación administrativa</h2>
                <p>
                  Los canales se tratan como catálogo administrado por administración. Su estado no pasa por el buzón
                  de validación: se gestiona aquí para curación, visibilidad y archivado lógico.
                </p>
              </div>
              <div class="policy-chips">
                <rip-status-chip
                  [label]="statusLabel(venueForm.controls.validationStatus.value)"
                  [tone]="statusTone(venueForm.controls.validationStatus.value)"
                />
                <rip-status-chip [label]="activeLabel(venueForm.controls.active.value)" [tone]="venueForm.controls.active.value ? 'success' : 'warning'" />
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      }

      @if (currentVenue(); as venue) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <div class="summary-header">
              <div class="summary-copy">
                <h2>{{ venue.name }}</h2>
                <p>{{ venue.shortName || venueTypeLabel(venue.typeCode) }}</p>
              </div>
              <rip-status-chip [label]="statusLabel(venue.validationStatus)" [tone]="statusTone(venue.validationStatus)" />
            </div>

            <div class="metadata-grid">
              <div class="metadata-item">
                <span>Estado de catálogo</span>
                <strong>{{ statusLabel(venue.validationStatus) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Situación</span>
                <strong>{{ activeLabel(venue.active) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Tipo</span>
                <strong>{{ venueTypeLabel(venue.typeCode) }}</strong>
              </div>
              <div class="metadata-item">
                <span>ISSN / eISSN</span>
                <strong>{{ venueIssnLabel(venue) }}</strong>
              </div>
              <div class="metadata-item">
                <span>ISBN</span>
                <strong>{{ venue.isbn || 'Sin ISBN' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Editorial</span>
                <strong>{{ selectedPublisherName() || 'Sin editorial' }}</strong>
              </div>
            </div>

            @if (venue.description) {
              <section class="detail-block">
                <h3>Descripción</h3>
                <p>{{ venue.description }}</p>
              </section>
            }

            @if (venue.website) {
              <section class="link-grid">
                <div class="link-card">
                  <span>Sitio web</span>
                  <a [href]="venue.website" target="_blank" rel="noopener noreferrer">{{ venue.website }}</a>
                </div>
              </section>
            }
          </mat-card-content>
        </mat-card>

        <rip-audit-history-panel
          [entityType]="'VENUE'"
          [entityId]="venue.id"
          title="Historial del canal"
          subtitle="Consulta cambios de catálogo, actualizaciones de metadatos y archivado lógico."
        />
      }

      @if (canManageMasterData() && !loadError()) {
        <div class="section-stack" [formGroup]="venueForm">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Identificación y catálogo</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p class="section-description">Recoge el nombre visible, el tipo de canal y el estado con el que quedará curado.</p>
              <div class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Nombre</mat-label>
                  <input matInput formControlName="name">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Nombre corto</mat-label>
                  <input matInput formControlName="shortName">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Tipo</mat-label>
                  <mat-select formControlName="typeCode">
                    @for (typeCode of venueTypeCodes(); track typeCode) {
                      <mat-option [value]="typeCode">{{ venueTypeLabel(typeCode) }}</mat-option>
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
                  <span>Desactívalo para archivar el canal sin perder referencias desde publicaciones o eventos.</span>
                </div>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Identificadores y editorial</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p class="section-description">Mantén visibles los identificadores del canal y la editorial relacionada cuando exista.</p>
              <div class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>ISSN</mat-label>
                  <input matInput formControlName="issn">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>eISSN</mat-label>
                  <input matInput formControlName="eissn">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>ISBN</mat-label>
                  <input matInput formControlName="isbn">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>País</mat-label>
                  <input matInput formControlName="country">
                </mat-form-field>
                <mat-form-field appearance="outline" class="wide-field">
                  <mat-label>Editorial</mat-label>
                  <mat-select formControlName="publisherId">
                    <mat-option [value]="null">Sin editorial</mat-option>
                    @for (publisher of publishers(); track publisher.id) {
                      <mat-option [value]="publisher.id">{{ publisherOptionLabel(publisher) }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Descripción y presencia</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p class="section-description">Añade el contexto descriptivo y la URL principal que ayude a identificar correctamente el canal.</p>
              <div class="form-grid">
                <mat-form-field appearance="outline" class="wide-field">
                  <mat-label>Sitio web</mat-label>
                  <input matInput formControlName="website">
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
export class VenueDetailPageComponent implements OnInit {
  private readonly api = inject(VenuesApiService);
  private readonly publishersApi = inject(PublishersApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly masterData = inject(AcademicMasterDataService);
  private readonly navigationContext = inject(NavigationContextService);

  readonly venueId = signal<number | null>(null);
  readonly currentVenue = signal<Venue | null>(null);
  readonly publishers = signal<Publisher[]>([]);
  readonly loadError = signal('');
  readonly saving = signal(false);
  readonly venueTypeCodes = computed(() => this.masterData.venueTypeCodes());
  readonly catalogStatuses: ValidationStatus[] = ['DRAFT', 'PENDING_VALIDATION', 'VALIDATED', 'CHANGES_REQUESTED', 'REJECTED'];
  readonly canManageMasterData = () => this.auth.hasAnyRole(['ADMIN']);

  readonly venueForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    shortName: new FormControl<string | null>(null),
    typeCode: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    issn: new FormControl<string | null>(null),
    eissn: new FormControl<string | null>(null),
    isbn: new FormControl<string | null>(null),
    country: new FormControl<string | null>(null),
    publisherId: new FormControl<number | null>(null),
    website: new FormControl<string | null>(null),
    description: new FormControl<string | null>(null),
    active: new FormControl(true, { nonNullable: true }),
    validationStatus: new FormControl<ValidationStatus>('PENDING_VALIDATION', { nonNullable: true })
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();
    this.publishersApi.search({ size: 200 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.publishers.set(this.sortByName(result.content)));
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.loadVenue(this.parseId(params.get('id'))));
  }

  save(): void {
    if (!this.canManageMasterData() || this.venueForm.invalid || this.saving()) {
      return;
    }
    const request = this.toRequest();
    const venueId = this.venueId();
    this.saving.set(true);
    const operation = venueId === null ? this.api.create(request) : this.api.update(venueId, request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (venue) => {
        this.saving.set(false);
        void this.router.navigate(this.venueDetailLink(venue.id), { queryParams: this.detailQueryParams() });
      },
      error: () => this.saving.set(false)
    });
  }

  venueSubtitle(): string {
    const venue = this.currentVenue();
    if (!venue) {
      return 'Detalle de canal';
    }
    return [
      this.venueTypeLabel(venue.typeCode),
      venue.country,
      this.statusLabel(venue.validationStatus)
    ].filter(Boolean).join(' · ') || 'Detalle de canal';
  }

  venueTypeLabel(code: string): string {
    return this.masterData.venueTypeLabel(code);
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

  venueIssnLabel(venue: Venue): string {
    return [venue.issn, venue.eissn].filter((value): value is string => !!value).join(' · ') || 'Sin ISSN';
  }

  selectedPublisherName(): string | null {
    const publisherId = this.currentVenue()?.publisherId ?? this.venueForm.controls.publisherId.value;
    return publisherId === null ? null : this.publishers().find((publisher) => publisher.id === publisherId)?.name ?? null;
  }

  publisherOptionLabel(publisher: Publisher): string {
    return publisher.active ? publisher.name : `${publisher.name} (inactiva)`;
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, this.fallbackBackPath(), 'Volver a canales').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, this.fallbackBackPath(), 'Volver a canales');
  }

  private venueDetailLink(venueId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/canales')
      ? ['/admin/canales', String(venueId)]
      : ['/venues', String(venueId)];
  }

  private fallbackBackPath(): string {
    return this.navigationContext.isCurrentPath('/admin/canales') ? '/admin/canales' : '/venues';
  }

  private loadVenue(venueId: number | null): void {
    this.venueId.set(venueId);
    this.loadError.set('');

    if (venueId === null) {
      this.currentVenue.set(null);
      this.venueForm.reset({
        name: '',
        shortName: null,
        typeCode: this.venueTypeCodes()[0] ?? '',
        issn: null,
        eissn: null,
        isbn: null,
        country: null,
        publisherId: null,
        website: null,
        description: null,
        active: true,
        validationStatus: 'PENDING_VALIDATION'
      });
      return;
    }

    this.api.get(venueId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (venue) => {
          this.currentVenue.set(venue);
          this.venueForm.patchValue({
            name: venue.name,
            shortName: venue.shortName,
            typeCode: venue.typeCode,
            issn: venue.issn,
            eissn: venue.eissn,
            isbn: venue.isbn,
            country: venue.country,
            publisherId: venue.publisherId,
            website: venue.website,
            description: venue.description,
            active: venue.active,
            validationStatus: venue.validationStatus
          });
        },
        error: (error: unknown) => {
          this.currentVenue.set(null);
          this.loadError.set(contentAccessErrorMessage(error, 'No se pudo cargar el canal.'));
        }
      });
  }

  private toRequest(): VenueRequest {
    const value = this.venueForm.getRawValue();
    return {
      name: value.name,
      shortName: this.emptyToNull(value.shortName),
      typeCode: value.typeCode,
      issn: this.emptyToNull(value.issn),
      eissn: this.emptyToNull(value.eissn),
      isbn: this.emptyToNull(value.isbn),
      country: this.emptyToNull(value.country),
      publisherId: value.publisherId,
      website: this.emptyToNull(value.website),
      description: this.emptyToNull(value.description),
      active: value.active,
      validationStatus: value.validationStatus
    };
  }

  private detailQueryParams(): NavigationContextQueryParams {
    const resolved = this.navigationContext.resolve(this.route, this.fallbackBackPath(), 'Volver a canales');
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

  private sortByName(items: Publisher[]): Publisher[] {
    return [...items].sort((left, right) => left.name.localeCompare(right.name, 'es'));
  }
}
