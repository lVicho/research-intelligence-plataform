import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AcademicMasterDataService } from '../../core/api/academic-master-data.service';
import {
  EventParticipation,
  EventParticipationRequest,
  PublicationSummary,
  ResearchUnit,
  ResearcherSummary,
  ScientificEvent,
  ValidationStatus
} from '../../core/api/api-models';
import { EventParticipationsApiService } from '../../core/api/event-participations-api.service';
import { PublicationsApiService } from '../../core/api/publications-api.service';
import { ResearchUnitsApiService } from '../../core/api/research-units-api.service';
import { ResearchersApiService } from '../../core/api/researchers-api.service';
import { ScientificEventsApiService } from '../../core/api/scientific-events-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import {
  NavigationContextQueryParams,
  NavigationContextService
} from '../../core/navigation/navigation-context.service';
import { AuditHistoryPanelComponent } from '../../shared/components/audit-history-panel.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { contentAccessErrorMessage } from '../../shared/utils/api-error-messages';
import { validationStatusLabel, validationStatusTone } from '../../shared/utils/display-labels';

@Component({
  selector: 'rip-participation-detail-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    AuditHistoryPanelComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        [title]="participationId() === null ? 'Nueva participación' : (currentParticipation()?.title || 'Detalle de participación')"
        [subtitle]="participationSubtitle()"
        eyebrow="Participaciones"
        [compact]="true"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
        @if (canSave()) {
          <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="participationForm.invalid || saving()">
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
            <div class="workflow-banner">
              <div class="workflow-copy">
                <p class="section-kicker">Flujo de validación</p>
                <h2>Registro sometido a revisión</h2>
                <p>
                  Las participaciones sí forman parte del buzón de validación. Guarda primero tus cambios y, cuando el
                  registro esté listo, envíalo o reenvíalo a validación desde esta pantalla.
                </p>
              </div>
              <div class="workflow-chips">
                <rip-status-chip [label]="currentStatusLabel()" [tone]="currentStatusTone()" />
                @if (currentParticipation(); as participation) {
                  @if (participation.submittedAt) {
                    <rip-tag-chip [label]="'Enviado ' + formatShortDate(participation.submittedAt)" />
                  }
                } @else {
                  <rip-tag-chip label="Borrador inicial" tone="type" />
                }
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      }

      @if (currentParticipation(); as participation) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <div class="summary-header">
              <div class="summary-copy">
                <h2>{{ participation.title }}</h2>
                <p>{{ participation.eventName }}</p>
              </div>
              <rip-status-chip [label]="statusLabel(participation.validationStatus)" [tone]="statusTone(participation.validationStatus)" />
            </div>

            <div class="metadata-grid">
              <div class="metadata-item">
                <span>Estado actual</span>
                <strong>{{ statusLabel(participation.validationStatus) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Tipo de participación</span>
                <strong>{{ participationTypeLabel(participation.participationTypeCode) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Investigador</span>
                <strong>{{ participation.researcherName || 'Sin investigador' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Unidad</span>
                <strong>{{ participation.researchUnitName || 'Sin unidad' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Fecha</span>
                <strong>{{ participation.participationDate || 'Sin fecha' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Último envío</span>
                <strong>{{ participation.submittedAt ? formatShortDate(participation.submittedAt) : 'Pendiente' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Enviado por</span>
                <strong>{{ participation.submittedBy || 'Sin envío registrado' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Última revisión</span>
                <strong>{{ participation.validatedAt ? formatShortDate(participation.validatedAt) : 'Pendiente' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Revisado por</span>
                <strong>{{ participation.validatedBy || 'Sin revisión cerrada' }}</strong>
              </div>
            </div>

            @if (participation.relatedPublicationTitle) {
              <div class="signal-list">
                <rip-tag-chip [label]="'Publicación vinculada: ' + participation.relatedPublicationTitle" />
              </div>
            }

            @if (participation.description) {
              <section class="detail-block">
                <h3>Descripción</h3>
                <p>{{ participation.description }}</p>
              </section>
            }

            @if (participation.evidenceUrl) {
              <section class="link-grid">
                <div class="link-card">
                  <span>Fuente o evidencia</span>
                  <a [href]="participation.evidenceUrl" target="_blank" rel="noopener noreferrer">{{ participation.evidenceUrl }}</a>
                </div>
              </section>
            }

            @if (showValidationWarning(participation)) {
              <section class="validation-warning" [class.warning-strong]="participation.validationStatus === 'CHANGES_REQUESTED'">
                <div class="warning-copy">
                  <span class="banner-label">{{ validationWarningLabel(participation) }}</span>
                  <p class="warning-title">{{ validationWarningTitle(participation) }}</p>
                  <p class="warning-comment">{{ validationWarningComment(participation) }}</p>
                  <p class="warning-support">{{ validationSupportCopy(participation.validationStatus) }}</p>
                </div>
                @if (canSubmitCurrent()) {
                  <div class="actions warning-actions">
                    <button mat-flat-button color="primary" type="button" [disabled]="submitting()" (click)="submit()">
                      {{ submitActionLabel(participation) }}
                    </button>
                  </div>
                }
              </section>
            } @else if (canSubmitCurrent()) {
              <div class="actions detail-actions">
                <button mat-flat-button color="primary" type="button" [disabled]="submitting()" (click)="submit()">
                  {{ submitActionLabel(participation) }}
                </button>
              </div>
            } @else if (!participation.canEdit && !participation.canSubmit) {
              <section class="readonly-note">
                <span>Edición bloqueada</span>
                <p>Este registro ya no admite cambios desde este perfil en su estado actual.</p>
              </section>
            }
          </mat-card-content>
        </mat-card>

        <rip-audit-history-panel
          [entityType]="'EVENT_PARTICIPATION'"
          [entityId]="participation.id"
          title="Historial"
          subtitle="Seguimiento de cambios y transiciones del flujo de validación."
        />
      }

      @if (canSave() && !loadError()) {
        <div class="section-stack" [formGroup]="participationForm">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Datos principales</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p class="section-description">Indica quién participa, en qué evento y con qué tipo de contribución se registrará.</p>
              <div class="form-grid">
                @if (isAdmin()) {
                  <mat-form-field appearance="outline">
                    <mat-label>Investigador</mat-label>
                    <mat-select formControlName="researcherId">
                      @for (researcher of researchers(); track researcher.id) {
                        <mat-option [value]="researcher.id">{{ researcher.displayName || researcher.fullName }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                } @else {
                  <div class="metadata-item form-note">
                    <span>Investigador</span>
                    <strong>{{ currentUser()?.displayName || currentUser()?.email || 'Investigador actual' }}</strong>
                  </div>
                }

                <mat-form-field appearance="outline">
                  <mat-label>Evento</mat-label>
                  <mat-select formControlName="eventId">
                    @for (event of events(); track event.id) {
                      <mat-option [value]="event.id">{{ event.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Tipo de participación</mat-label>
                  <mat-select formControlName="participationTypeCode">
                    @for (typeCode of participationTypeCodes(); track typeCode) {
                      <mat-option [value]="typeCode">{{ participationTypeLabel(typeCode) }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline" class="wide-field">
                  <mat-label>Título</mat-label>
                  <input matInput formControlName="title">
                </mat-form-field>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Contexto institucional</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p class="section-description">Relaciona la participación con la unidad y la publicación asociada cuando aplique.</p>
              <div class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Unidad asociada</mat-label>
                  <mat-select formControlName="researchUnitId">
                    <mat-option [value]="null">Sin unidad asociada</mat-option>
                    @for (unit of units(); track unit.id) {
                      <mat-option [value]="unit.id">{{ unit.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Fecha</mat-label>
                  <input matInput type="date" formControlName="participationDate">
                </mat-form-field>
                <mat-form-field appearance="outline" class="wide-field">
                  <mat-label>Publicación vinculada</mat-label>
                  <mat-select formControlName="relatedPublicationId">
                    <mat-option [value]="null">Sin publicación vinculada</mat-option>
                    @for (publication of publications(); track publication.id) {
                      <mat-option [value]="publication.id">{{ publication.title }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Descripción y evidencia</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p class="section-description">Aporta el detalle narrativo y la URL de soporte que facilitarán la validación posterior.</p>
              <div class="form-grid">
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
      } @else if (participationId() === null && !loadError()) {
        <rip-empty-state
          title="No puedes crear participaciones desde este perfil"
          message="Necesitas un rol de investigador o administración para registrar una participación."
        />
      }
    </section>
  `,
  styles: [`
    .workflow-banner,
    .summary-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
    }

    .workflow-copy,
    .summary-copy {
      min-width: 0;
    }

    .workflow-copy h2,
    .summary-copy h2 {
      margin: 0;
      color: #142033;
      font-size: 1.2rem;
      font-weight: 760;
    }

    .workflow-copy p,
    .summary-copy p {
      margin: 6px 0 0;
      color: #667487;
      line-height: 1.55;
    }

    .workflow-chips {
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

    .link-card span,
    .readonly-note span {
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

    .signal-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 20px;
    }

    .validation-warning,
    .readonly-note {
      display: grid;
      gap: 12px;
      margin-top: 20px;
      padding: 16px;
      border: 1px solid #d9e6f2;
      border-radius: 14px;
      background: #f7fbff;
    }

    .warning-strong {
      border-color: #efd18b;
      background: #fff9e9;
    }

    .banner-label {
      color: #72510d;
      font-size: 0.76rem;
      font-weight: 780;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .warning-title {
      margin: 6px 0 0;
      color: #172235;
      font-size: 1rem;
      font-weight: 720;
    }

    .warning-comment,
    .warning-support,
    .readonly-note p {
      margin: 0;
      color: #475569;
      line-height: 1.55;
    }

    .warning-actions,
    .detail-actions {
      margin-top: 8px;
    }

    .wide-field {
      grid-column: 1 / -1;
    }

    .form-note {
      align-self: center;
    }

    @media (max-width: 720px) {
      .workflow-banner,
      .summary-header {
        display: grid;
      }

      .workflow-chips {
        justify-content: flex-start;
      }
    }
  `]
})
export class ParticipationDetailPageComponent implements OnInit {
  private readonly api = inject(EventParticipationsApiService);
  private readonly eventsApi = inject(ScientificEventsApiService);
  private readonly publicationsApi = inject(PublicationsApiService);
  private readonly researchersApi = inject(ResearchersApiService);
  private readonly unitsApi = inject(ResearchUnitsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly masterData = inject(AcademicMasterDataService);
  private readonly navigationContext = inject(NavigationContextService);

  readonly currentUser = this.auth.currentUser;
  readonly participationId = signal<number | null>(null);
  readonly currentParticipation = signal<EventParticipation | null>(null);
  readonly events = signal<ScientificEvent[]>([]);
  readonly publications = signal<PublicationSummary[]>([]);
  readonly researchers = signal<ResearcherSummary[]>([]);
  readonly units = signal<ResearchUnit[]>([]);
  readonly loadError = signal('');
  readonly saving = signal(false);
  readonly submitting = signal(false);
  readonly participationTypeCodes = computed(() => this.masterData.eventParticipationTypeCodes());

  readonly participationForm = new FormGroup({
    eventId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    researcherId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    researchUnitId: new FormControl<number | null>(null),
    participationTypeCode: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    title: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    description: new FormControl<string | null>(null),
    evidenceUrl: new FormControl<string | null>(null),
    participationDate: new FormControl<string | null>(null),
    relatedPublicationId: new FormControl<number | null>(null)
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();

    this.eventsApi.search({ size: 100 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.events.set(result.content));

    this.unitsApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((units) => this.units.set(units));

    if (this.isAdmin()) {
      this.researchersApi.search({ size: 100 })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((result) => this.researchers.set(result.content));
    }

    this.participationForm.controls.researcherId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((researcherId) => this.loadRelatedPublications(researcherId));

    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.loadParticipation(this.parseId(params.get('id'))));
  }

  save(): void {
    if (!this.canSave() || this.participationForm.invalid || this.saving()) {
      return;
    }
    const request = this.toRequest();
    if (request === null) {
      return;
    }
    const participationId = this.participationId();
    this.saving.set(true);
    const operation = participationId === null ? this.api.create(request) : this.api.update(participationId, request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (participation) => {
        this.saving.set(false);
        void this.router.navigate(this.afterSaveLink(participation.id), { queryParams: this.detailQueryParams() });
      },
      error: () => this.saving.set(false)
    });
  }

  submit(): void {
    const participation = this.currentParticipation();
    if (!participation || !this.canSubmitCurrent() || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.api.submit(participation.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.submitting.set(false);
          this.currentParticipation.set(updated);
        },
        error: () => this.submitting.set(false)
      });
  }

  canSave(): boolean {
    if (!this.auth.hasAnyRole(['RESEARCHER', 'ADMIN'])) {
      return false;
    }
    const participation = this.currentParticipation();
    if (!participation) {
      return true;
    }
    return participation.canEdit;
  }

  canSubmitCurrent(): boolean {
    return this.currentParticipation()?.canSubmit ?? false;
  }

  isAdmin(): boolean {
    return this.auth.hasAnyRole(['ADMIN']);
  }

  participationSubtitle(): string {
    const participation = this.currentParticipation();
    if (!participation) {
      return 'Registrar contribuciones, asistencias y presentaciones en eventos antes de enviarlas a validación.';
    }
    return [
      participation.eventName,
      participation.participationDate,
      this.statusLabel(participation.validationStatus)
    ].filter(Boolean).join(' · ') || 'Detalle de participación';
  }

  participationTypeLabel(code: string): string {
    return this.masterData.eventParticipationTypeLabel(code);
  }

  currentStatusLabel(): string {
    return this.statusLabel(this.currentParticipation()?.validationStatus ?? 'DRAFT');
  }

  currentStatusTone(): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return this.statusTone(this.currentParticipation()?.validationStatus ?? 'DRAFT');
  }

  statusLabel(status: ValidationStatus): string {
    return validationStatusLabel(status);
  }

  statusTone(status: ValidationStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  submitActionLabel(participation: EventParticipation): string {
    return participation.validationStatus === 'CHANGES_REQUESTED' ? 'Reenviar a validación' : 'Enviar a validación';
  }

  showValidationWarning(participation: EventParticipation): boolean {
    return participation.validationStatus === 'CHANGES_REQUESTED' || !!participation.validationComment;
  }

  validationWarningLabel(participation: EventParticipation): string {
    return participation.validationStatus === 'CHANGES_REQUESTED' ? 'Requiere cambios' : 'Comentario de validación';
  }

  validationWarningTitle(participation: EventParticipation): string {
    return participation.validationStatus === 'CHANGES_REQUESTED'
      ? 'El equipo validador ha solicitado una revisión antes de aprobar esta participación.'
      : 'Hay observaciones registradas sobre esta participación.';
  }

  validationWarningComment(participation: EventParticipation): string {
    if (participation.validationComment) {
      return participation.validationComment;
    }
    return 'Revisa el contenido y completa la información necesaria antes del siguiente envío.';
  }

  validationSupportCopy(status: ValidationStatus): string {
    return status === 'CHANGES_REQUESTED'
      ? 'Actualiza los campos señalados y usa el botón de reenvío cuando el registro vuelva a estar completo.'
      : 'Mantén esta observación visible mientras revisas el registro.';
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, this.fallbackBackPath(), this.fallbackBackLabel()).label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, this.fallbackBackPath(), this.fallbackBackLabel());
  }

  formatShortDate(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  private loadParticipation(participationId: number | null): void {
    this.participationId.set(participationId);
    this.currentParticipation.set(null);
    this.loadError.set('');

    if (participationId === null) {
      this.initializeNewForm();
      return;
    }

    this.api.get(participationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (participation) => {
          this.currentParticipation.set(participation);
          this.participationForm.patchValue({
            eventId: participation.eventId,
            researcherId: participation.researcherId,
            researchUnitId: participation.researchUnitId,
            participationTypeCode: participation.participationTypeCode,
            title: participation.title,
            description: participation.description,
            evidenceUrl: participation.evidenceUrl,
            participationDate: participation.participationDate,
            relatedPublicationId: participation.relatedPublicationId
          });
          this.loadRelatedPublications(participation.researcherId);
        },
        error: (error: unknown) => {
          this.currentParticipation.set(null);
          this.loadError.set(contentAccessErrorMessage(error, 'No se pudo cargar la participación.'));
        }
      });
  }

  private afterSaveLink(participationId: number): string[] {
    if (this.isResearcherArea()) {
      return ['/app/actividades', String(participationId)];
    }
    return this.navigationContext.isCurrentPath('/admin/participaciones')
      ? ['/admin/participaciones', String(participationId)]
      : ['/participations', String(participationId)];
  }

  private isResearcherArea(): boolean {
    return this.router.url.startsWith('/app/');
  }

  private fallbackBackPath(): string {
    if (this.isResearcherArea()) {
      return '/app/mis-actividades';
    }
    return this.navigationContext.isCurrentPath('/admin/participaciones') ? '/admin/participaciones' : '/participations';
  }

  private fallbackBackLabel(): string {
    return this.isResearcherArea() ? 'Volver a mis actividades' : 'Volver a participaciones';
  }

  private initializeNewForm(): void {
    const defaultTypeCode = this.participationTypeCodes()[0] ?? '';
    const researcherId = this.currentUser()?.researcherId ?? null;
    this.participationForm.reset({
      eventId: null,
      researcherId,
      researchUnitId: null,
      participationTypeCode: defaultTypeCode,
      title: '',
      description: null,
      evidenceUrl: null,
      participationDate: null,
      relatedPublicationId: null
    });
    this.loadRelatedPublications(researcherId);
  }

  private loadRelatedPublications(researcherId: number | null): void {
    if (researcherId === null) {
      this.publications.set([]);
      return;
    }
    this.publicationsApi.search({ size: 100, researcherId })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.publications.set(result.content));
  }

  private toRequest(): EventParticipationRequest | null {
    const value = this.participationForm.getRawValue();
    if (value.eventId === null || value.researcherId === null) {
      return null;
    }
    return {
      eventId: value.eventId,
      researcherId: value.researcherId,
      researchUnitId: value.researchUnitId,
      participationTypeCode: value.participationTypeCode,
      title: value.title,
      description: this.emptyToNull(value.description),
      evidenceUrl: this.emptyToNull(value.evidenceUrl),
      participationDate: this.emptyToNull(value.participationDate),
      relatedPublicationId: value.relatedPublicationId,
      validationStatus: this.currentParticipation()?.validationStatus ?? null
    };
  }

  private detailQueryParams(): NavigationContextQueryParams {
    const resolved = this.navigationContext.resolve(this.route, this.fallbackBackPath(), this.fallbackBackLabel());
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
