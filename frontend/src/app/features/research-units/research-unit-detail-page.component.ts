import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  OrganizationScope,
  PageResponse,
  PublicationSummary,
  ResearchUnit,
  ResearchUnitRequest,
  ResearchUnitType,
  ResearcherSummary
} from '../../core/api/api-models';
import { PublicationsApiService } from '../../core/api/publications-api.service';
import { ResearchUnitsApiService } from '../../core/api/research-units-api.service';
import { ResearchersApiService } from '../../core/api/researchers-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { AuditHistoryPanelComponent } from '../../shared/components/audit-history-panel.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { PublicSummaryWorkflowComponent } from '../../shared/components/public-summary-workflow.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { VisibilityNoteComponent } from '../../shared/components/visibility-note.component';
import { organizationScopeLabel, researchUnitTypeLabel } from '../../shared/utils/display-labels';
import { publicVisibilityNote, visibilityNoteForUser } from '../../shared/utils/visibility-labels';

const EXTERNAL_ORGANIZATIONS_HELPER =
  'Las organizaciones externas pueden aparecer como colaboradoras, pero no en el directorio público de unidades.';

const INTERNAL_RESEARCH_UNIT_TYPES: ResearchUnitType[] = [
  'UNIVERSITY',
  'FACULTY',
  'SCHOOL',
  'DEPARTMENT',
  'INSTITUTE',
  'RESEARCH_GROUP',
  'LAB',
  'CENTER',
  'OTHER'
];

const EXTERNAL_ORGANIZATION_TYPES: ResearchUnitType[] = [
  'HOSPITAL',
  'COMPANY',
  'FOUNDATION',
  'GOVERNMENT_AGENCY',
  'OTHER'
];

@Component({
  selector: 'rip-research-unit-detail-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
    AuditHistoryPanelComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    PublicSummaryWorkflowComponent,
    StatusChipComponent,
    TagChipComponent,
    VisibilityNoteComponent
  ],
  template: `
    <section class="page detail-page">
      <rip-page-header
        [title]="pageTitle()"
        [subtitle]="pageSubtitle()"
        eyebrow="Organización"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
        @if (canManageMasterData()) {
          <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="form.invalid || saving()">Guardar</button>
        }
      </rip-page-header>

      <rip-visibility-note [message]="visibilityNote()" />

      @if (currentUnit(); as unit) {
        <mat-card appearance="outlined" class="summary-card">
          <mat-card-content>
            <div class="summary-header">
              <div>
                <span class="unit-type">{{ scopeLabel(unit.organizationScope) }}</span>
                <h2>{{ unit.name }}</h2>
                <p>{{ summarySubtitle(unit) }}</p>
              </div>
              <div class="summary-chips">
                <rip-status-chip [label]="unit.active ? 'Activa' : 'Inactiva'" [tone]="unit.active ? 'success' : 'neutral'" />
                @if (unit.organizationScope === 'INTERNAL') {
                  <rip-status-chip
                    [label]="unit.visibleInPortal ? 'Visible en portal' : 'Oculta en portal'"
                    [tone]="unit.visibleInPortal ? 'info' : 'neutral'"
                  />
                }
              </div>
            </div>

            @if (summaryDescription(unit)) {
              <p class="summary-description">{{ summaryDescription(unit) }}</p>
            }

            <div class="metadata-grid">
              <div class="metadata-item">
                <span>Tipo</span>
                <strong>{{ typeLabel(unit.type) }}</strong>
              </div>
              <div class="metadata-item">
                <span>{{ unit.organizationScope === 'INTERNAL' ? 'Unidad superior' : 'País' }}</span>
                <strong>{{ unit.organizationScope === 'INTERNAL' ? (parentUnit()?.name || 'Sin unidad superior') : (unit.country || 'Sin país') }}</strong>
              </div>
              <div class="metadata-item">
                <span>Responsable</span>
                <strong>{{ responsibleResearcherName() || (unit.organizationScope === 'INTERNAL' ? 'Sin responsable asignado' : 'No aplica') }}</strong>
              </div>
              <div class="metadata-item">
                <span>{{ unit.organizationScope === 'INTERNAL' ? 'Visibilidad pública' : 'Directorio público' }}</span>
                <strong>{{ unit.organizationScope === 'INTERNAL' ? (unit.visibleInPortal ? 'Visible' : 'Oculta') : 'No se muestra' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Investigadores visibles</span>
                <strong>{{ relatedResearchers().totalElements }}</strong>
              </div>
              <div class="metadata-item">
                <span>Publicaciones visibles</span>
                <strong>{{ relatedPublications().totalElements }}</strong>
              </div>
              @if (unit.organizationScope === 'INTERNAL') {
                <div class="metadata-item">
                  <span>Destacada</span>
                  <strong>{{ unit.featured ? 'Sí' : 'No' }}</strong>
                </div>
                <div class="metadata-item">
                  <span>Orden</span>
                  <strong>{{ unit.sortOrder ?? 'Sin orden definido' }}</strong>
                </div>
              }
            </div>

            <div class="summary-actions">
              @if (unit.website) {
                <a mat-stroked-button [href]="unit.website" target="_blank" rel="noreferrer">Sitio web</a>
              }
              <a mat-stroked-button [routerLink]="searchLink()" [queryParams]="{ researchUnitId: unit.id }">Buscar actividad relacionada</a>
            </div>

            <div class="chip-list">
              @for (topic of relatedTopics(); track topic) {
                <rip-tag-chip [label]="topic" />
              } @empty {
                <span class="muted">Los temas públicos se mostrarán cuando haya actividad validada suficiente.</span>
              }
            </div>
          </mat-card-content>
        </mat-card>

        @if (showPublicSummaryWorkflow()) {
          <rip-public-summary-workflow
            [targetType]="publicSummaryTargetType()"
            [targetId]="unit.id"
            [targetTitle]="unit.name"
            [targetSubtitle]="summarySubtitle(unit)"
            [currentSummary]="unit.publicDescription"
            [entityLabel]="isExternalMode() ? 'Organizacion externa' : 'Unidad'"
            [currentSummaryLabel]="isExternalMode() ? 'Descripcion publica actual' : 'Resumen publico actual'"
            [allowGeneration]="showPublicSummaryWorkflow()"
            [allowReviewExisting]="showPublicSummaryWorkflow()"
            (summaryApplied)="applyAcceptedPublicSummary($event)"
          />
        }

        <section class="content-grid">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Investigadores vinculados</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="list-grid">
                @for (researcher of relatedResearchers().content; track researcher.id) {
                  <a class="list-card" [routerLink]="researcherLink(researcher.id)" [queryParams]="detailReturnQueryParams('Volver al registro')">
                    <strong>{{ researcher.displayName || researcher.fullName }}</strong>
                    <p>{{ researcher.primaryAffiliationName || 'Afiliación visible sin detalle adicional' }}</p>
                  </a>
                } @empty {
                  <rip-empty-state title="Sin investigadores visibles" message="No hay investigadores validados asociados a este registro." />
                }
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Temas principales</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="chip-list">
                @for (topic of relatedTopics(); track topic) {
                  <rip-tag-chip [label]="topic" />
                } @empty {
                  <rip-empty-state title="Sin temas visibles" message="Todavía no hay temas validados suficientes para este registro." />
                }
              </div>
            </mat-card-content>
          </mat-card>
        </section>

        <section class="content-grid">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Publicaciones y actividad validada</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="publication-list">
                @for (publication of relatedPublications().content; track publication.id) {
                  <a class="publication-row" [routerLink]="publicationLink(publication.id)" [queryParams]="detailReturnQueryParams('Volver al registro')">
                    <strong>{{ publication.title }}</strong>
                    <p>{{ publication.year || 'Sin año' }} · {{ publication.source || publication.doi || 'Repositorio institucional' }}</p>
                    <div class="chip-list">
                      @for (topic of publication.topics.slice(0, 4); track topic) {
                        <rip-tag-chip [label]="topic" />
                      }
                    </div>
                  </a>
                } @empty {
                  <rip-empty-state title="Sin publicaciones visibles" message="Este registro aún no muestra publicaciones validadas en el portal." />
                }
              </div>
            </mat-card-content>
          </mat-card>

          @if (!isExternalMode()) {
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-card-title>Unidades hijas</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="list-grid">
                  @for (child of childUnits(); track child.id) {
                    <a class="list-card" [routerLink]="unitLink(child.id, child.organizationScope)" [queryParams]="detailReturnQueryParams('Volver a la unidad')">
                      <strong>{{ child.name }}</strong>
                      <p>{{ typeLabel(child.type) }}</p>
                    </a>
                  } @empty {
                    <rip-empty-state title="Sin unidades hijas" message="No hay subunidades públicas asociadas a esta unidad." />
                  }
                </div>
              </mat-card-content>
            </mat-card>
          }
        </section>

        @if (canManageMasterData()) {
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-card-title>Auditoría técnica</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="metadata-grid">
                <div class="metadata-item">
                  <span>Creado por</span>
                    <strong>{{ auditUserLabel(unit.createdByUserId ?? null) }}</strong>
                </div>
                <div class="metadata-item">
                  <span>Creado el</span>
                  <strong>{{ formatAuditDate(unit.createdAt) }}</strong>
                </div>
                <div class="metadata-item">
                  <span>Modificado por</span>
                    <strong>{{ auditUserLabel(unit.updatedByUserId ?? null) }}</strong>
                </div>
                <div class="metadata-item">
                  <span>Modificado el</span>
                  <strong>{{ formatAuditDate(unit.updatedAt) }}</strong>
                </div>
              </div>
            </mat-card-content>
          </mat-card>

          <rip-audit-history-panel
            [entityType]="'RESEARCH_UNIT'"
            [entityId]="unit.id"
            title="Historial"
            subtitle="Trazabilidad de cambios y estados del registro."
          />
        }
      }

      @if (canManageMasterData()) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <form class="detail-form" [formGroup]="form">
              <section class="form-section classification-section">
                <div class="section-heading">
                  <h3>Clasificación</h3>
                  <p>Define si el registro forma parte de la estructura interna o si representa una entidad colaboradora externa.</p>
                </div>
                <mat-radio-group class="classification-options" formControlName="organizationScope">
                  <mat-radio-button value="INTERNAL">Unidad interna</mat-radio-button>
                  <mat-radio-button value="EXTERNAL">Organización externa</mat-radio-button>
                </mat-radio-group>
                @if (isExternalMode()) {
                  <p class="helper-copy">{{ externalOrganizationsHelper }}</p>
                }
              </section>

              <section class="form-section">
                <div class="section-heading">
                  <h3>{{ isExternalMode() ? 'Ficha de organización externa' : 'Ficha de unidad interna' }}</h3>
                  <p>{{ isExternalMode() ? 'Datos básicos para colaboradores externos vinculados a actividad científica.' : 'Información estructural y visibilidad pública de la unidad.' }}</p>
                </div>

                <div class="section-grid">
                  <mat-form-field appearance="outline">
                    <mat-label>Nombre</mat-label>
                    <input matInput formControlName="name">
                  </mat-form-field>

                  @if (!isExternalMode()) {
                    <mat-form-field appearance="outline">
                      <mat-label>Acrónimo</mat-label>
                      <input matInput formControlName="shortName">
                    </mat-form-field>
                  }

                  <mat-form-field appearance="outline">
                    <mat-label>Tipo</mat-label>
                    <mat-select formControlName="type">
                      @for (type of availableTypes(); track type) {
                        <mat-option [value]="type">{{ typeLabel(type) }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  @if (!isExternalMode()) {
                    <mat-form-field appearance="outline">
                      <mat-label>Unidad superior</mat-label>
                      <mat-select formControlName="parentId">
                        <mat-option [value]="null">Ninguna</mat-option>
                        @for (unit of parentOptions(); track unit.id) {
                          <mat-option [value]="unit.id">{{ unit.name }}</mat-option>
                        }
                      </mat-select>
                    </mat-form-field>
                  } @else {
                    <mat-form-field appearance="outline">
                      <mat-label>País</mat-label>
                      <input matInput formControlName="country">
                    </mat-form-field>
                  }

                  <mat-form-field appearance="outline" class="wide-field">
                    <mat-label>Sitio web</mat-label>
                    <input matInput formControlName="website">
                  </mat-form-field>
                </div>
              </section>

              @if (isExternalMode()) {
                <section class="form-section">
                  <div class="section-heading">
                    <h3>Descripción</h3>
                    <p>Resume brevemente el papel de la entidad y su relación con la institución.</p>
                  </div>

                  <div class="section-grid">
                    <mat-form-field appearance="outline" class="wide-field">
                      <mat-label>Descripción</mat-label>
                      <textarea matInput rows="5" formControlName="publicDescription"></textarea>
                    </mat-form-field>
                  </div>

                  <div class="checkbox-grid">
                    <mat-checkbox formControlName="active">Activa</mat-checkbox>
                    <mat-checkbox formControlName="visibleInPortal" [disabled]="true">No visible en el directorio público de unidades</mat-checkbox>
                  </div>
                </section>
              } @else {
                <section class="form-section">
                  <div class="section-heading">
                    <h3>Descripciones y contexto</h3>
                    <p>Distingue lo que se mostrará en el portal de la información operativa de uso interno.</p>
                  </div>

                  <div class="section-grid">
                    <mat-form-field appearance="outline" class="wide-field">
                      <mat-label>Descripción pública</mat-label>
                      <textarea matInput rows="5" formControlName="publicDescription"></textarea>
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="wide-field">
                      <mat-label>Descripción interna</mat-label>
                      <textarea matInput rows="4" formControlName="internalDescription"></textarea>
                    </mat-form-field>
                  </div>
                </section>

                <section class="form-section">
                  <div class="section-heading">
                    <h3>Visibilidad y responsable</h3>
                    <p>Controla la presencia en portal y la persona de referencia de la unidad.</p>
                  </div>

                  <div class="section-grid">
                    <mat-form-field appearance="outline">
                      <mat-label>Investigador responsable</mat-label>
                      <mat-select formControlName="responsibleResearcherId">
                        <mat-option [value]="null">Sin asignar</mat-option>
                        @for (researcher of researcherOptions(); track researcher.id) {
                          <mat-option [value]="researcher.id">{{ researcher.displayName || researcher.fullName }}</mat-option>
                        }
                      </mat-select>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Orden</mat-label>
                      <input matInput type="number" min="0" formControlName="sortOrder">
                    </mat-form-field>
                  </div>

                  <div class="checkbox-grid">
                    <mat-checkbox formControlName="active">Activa</mat-checkbox>
                    <mat-checkbox formControlName="visibleInPortal">Visible en el directorio público</mat-checkbox>
                    <mat-checkbox formControlName="featured">Destacada</mat-checkbox>
                  </div>
                </section>
              }
            </form>
          </mat-card-content>
        </mat-card>
      }
    </section>
  `,
  styles: [`
    .detail-page {
      display: grid;
      gap: 22px;
    }

    .summary-card,
    .list-card,
    .publication-row {
      border-radius: 24px;
    }

    .summary-card mat-card-content,
    .detail-form,
    .form-section {
      display: grid;
      gap: 22px;
    }

    .summary-header {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 16px;
    }

    .summary-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      justify-content: flex-end;
    }

    .unit-type {
      display: inline-flex;
      padding: 4px 10px;
      border-radius: 999px;
      background: #eef6f3;
      color: #28624a;
      font-size: 0.76rem;
      font-weight: 780;
    }

    h2 {
      margin: 10px 0 0;
      color: #142033;
      font-size: clamp(2rem, 3vw, 2.8rem);
      line-height: 1.02;
    }

    .summary-header p,
    .summary-description,
    .helper-copy {
      margin: 8px 0 0;
      color: #667487;
      line-height: 1.55;
    }

    .summary-description {
      margin-top: 0;
      max-width: 75ch;
    }

    .summary-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 8px;
    }

    .content-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 24px;
    }

    .list-grid,
    .publication-list {
      display: grid;
      gap: 12px;
    }

    .list-card,
    .publication-row {
      display: grid;
      gap: 8px;
      padding: 18px;
      border: 1px solid #e0e7ee;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .list-card:hover,
    .publication-row:hover {
      border-color: #b8d2df;
      box-shadow: 0 14px 28px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
    }

    .list-card strong,
    .publication-row strong {
      color: #142033;
      line-height: 1.35;
    }

    .list-card p,
    .publication-row p {
      margin: 0;
      color: #667487;
      line-height: 1.45;
    }

    .chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .classification-section {
      padding: 18px;
      border: 1px solid #dbe5ee;
      border-radius: 18px;
      background: linear-gradient(180deg, #fbfdff, #f7fafc);
    }

    .classification-options {
      display: flex;
      flex-wrap: wrap;
      gap: 18px;
    }

    .section-heading {
      display: grid;
      gap: 6px;
    }

    .section-heading h3 {
      margin: 0;
      color: #142033;
      font-size: 1.05rem;
    }

    .section-heading p,
    .muted {
      margin: 0;
      color: #667487;
    }

    .checkbox-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 18px;
      align-items: center;
    }

    .wide-field {
      grid-column: 1 / -1;
    }

    @media (max-width: 720px) {
      .summary-header,
      .classification-options {
        display: grid;
      }

      .summary-chips {
        justify-content: flex-start;
      }
    }
  `]
})
export class ResearchUnitDetailPageComponent implements OnInit {
  private readonly api = inject(ResearchUnitsApiService);
  private readonly researchersApi = inject(ResearchersApiService);
  private readonly publicationsApi = inject(PublicationsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly navigationContext = inject(NavigationContextService);

  readonly externalOrganizationsHelper = EXTERNAL_ORGANIZATIONS_HELPER;
  readonly unitId = this.readUnitId();
  readonly routeScope = signal((this.route.snapshot.data['organizationScope'] as OrganizationScope | undefined) ?? 'INTERNAL');
  readonly isPortalView = signal(this.route.snapshot.data['portalView'] === true);
  readonly saving = signal(false);
  readonly currentUnit = signal<ResearchUnit | null>(null);
  readonly allUnits = signal<ResearchUnit[]>([]);
  readonly allResearchers = signal<ResearcherSummary[]>([]);
  readonly selectedScope = signal<OrganizationScope>(this.routeScope());
  readonly relatedResearchers = signal<PageResponse<ResearcherSummary>>(this.emptyResearcherPage());
  readonly relatedPublications = signal<PageResponse<PublicationSummary>>(this.emptyPublicationPage());
  readonly parentOptions = computed(() =>
    this.allUnits().filter((unit) => unit.id !== this.unitId && unit.organizationScope === 'INTERNAL')
  );
  readonly researcherOptions = computed(() => [...this.allResearchers()].sort((left, right) =>
    (left.displayName || left.fullName).localeCompare(right.displayName || right.fullName, 'es')
  ));
  readonly availableTypes = computed(() => this.selectedScope() === 'EXTERNAL' ? EXTERNAL_ORGANIZATION_TYPES : INTERNAL_RESEARCH_UNIT_TYPES);
  readonly isExternalMode = computed(() => this.selectedScope() === 'EXTERNAL');
  readonly parentUnit = computed(() => {
    const unit = this.currentUnit();
    if (!unit?.parentId) {
      return null;
    }
    return this.allUnits().find((candidate) => candidate.id === unit.parentId) ?? null;
  });
  readonly childUnits = computed(() => {
    if (this.unitId === null || this.isExternalMode()) {
      return [];
    }
    return this.allUnits().filter((unit) => unit.parentId === this.unitId && unit.organizationScope === 'INTERNAL');
  });
  readonly responsibleResearcherName = computed(() => {
    const responsibleResearcherId = this.currentUnit()?.responsibleResearcherId ?? this.form.controls.responsibleResearcherId.value;
    if (responsibleResearcherId === null) {
      return null;
    }
    const researcher = this.researcherOptions().find((candidate) => candidate.id === responsibleResearcherId);
    return researcher ? (researcher.displayName || researcher.fullName) : null;
  });
  readonly relatedTopics = computed(() =>
    Array.from(new Set(this.relatedPublications().content.flatMap((publication) => publication.topics))).slice(0, 8)
  );
  readonly canManageMasterData = computed(() => this.auth.hasAnyRole(['ADMIN']) && !this.isPortalView());
  readonly visibilityNote = computed(() => {
    if (this.isPortalView()) {
      return publicVisibilityNote();
    }
    if (this.isExternalMode()) {
      return EXTERNAL_ORGANIZATIONS_HELPER;
    }
    return visibilityNoteForUser(this.auth.currentUser());
  });
  readonly pageTitle = computed(() => {
    if (this.unitId !== null && this.currentUnit()) {
      return this.currentUnit()!.name;
    }
    return this.isExternalMode() ? 'Nueva organización externa' : 'Nueva unidad interna';
  });
  readonly pageSubtitle = computed(() => {
    if (this.unitId === null) {
      return this.isExternalMode()
        ? 'Registro de entidades colaboradoras que no deben mostrarse en el directorio público de unidades.'
        : 'Registro institucional para facultades, centros, grupos y demás estructura interna visible.';
    }
    const unit = this.currentUnit();
    return unit?.publicDescription || unit?.website || this.summarySubtitle(unit) || 'Detalle de organización';
  });

  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    shortName: new FormControl<string | null>(null),
    type: new FormControl<ResearchUnitType>('OTHER', { nonNullable: true, validators: [Validators.required] }),
    parentId: new FormControl<number | null>(null),
    country: new FormControl<string | null>(null),
    city: new FormControl<string | null>(null),
    website: new FormControl<string | null>(null),
    active: new FormControl(true, { nonNullable: true }),
    visibleInPortal: new FormControl(true, { nonNullable: true }),
    organizationScope: new FormControl<OrganizationScope>('INTERNAL', { nonNullable: true, validators: [Validators.required] }),
    publicDescription: new FormControl<string | null>(null),
    internalDescription: new FormControl<string | null>(null),
    responsibleResearcherId: new FormControl<number | null>(null),
    featured: new FormControl(false, { nonNullable: true }),
    sortOrder: new FormControl<number | null>(null)
  });

  ngOnInit(): void {
    this.form.controls.organizationScope.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((scope) => this.applyScopeRules(scope, false));

    this.api.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((units) => this.allUnits.set(units));

    this.researchersApi.search({ size: 200, active: true })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.allResearchers.set(result.content));

    if (this.unitId === null) {
      this.initializeNewForm(this.routeScope());
      return;
    }

    this.api.get(this.unitId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((unit) => this.applyUnit(unit));

    forkJoin({
      researchers: this.researchersApi.search({ researchUnitId: this.unitId, size: 12 }).pipe(catchError(() => of(this.emptyResearcherPage()))),
      publications: this.publicationsApi.search({ researchUnitId: this.unitId, size: 8, sortBy: 'year', sortDirection: 'desc' }).pipe(
        catchError(() => of(this.emptyPublicationPage()))
      )
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.relatedResearchers.set(result.researchers);
        this.relatedPublications.set(result.publications);
      });
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    const request = this.toRequest();
    const operation = this.unitId === null ? this.api.create(request) : this.api.update(this.unitId, request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (unit) => {
        this.saving.set(false);
        void this.router.navigate(this.unitLink(unit.id, unit.organizationScope));
      },
      error: () => this.saving.set(false)
    });
  }

  showPublicSummaryWorkflow(): boolean {
    return this.canManageMasterData() && !!this.currentUnit();
  }

  publicSummaryTargetType(): 'RESEARCH_UNIT' | 'EXTERNAL_ORGANIZATION' {
    return this.isExternalMode() ? 'EXTERNAL_ORGANIZATION' : 'RESEARCH_UNIT';
  }

  scopeLabel(scope: OrganizationScope): string {
    return organizationScopeLabel(scope);
  }

  typeLabel(type: string): string {
    return researchUnitTypeLabel(type);
  }

  summarySubtitle(unit: ResearchUnit | null): string {
    if (!unit) {
      return '';
    }
    if (unit.organizationScope === 'EXTERNAL') {
      return [this.typeLabel(unit.type), unit.country, unit.website].filter(Boolean).join(' · ');
    }
    return [unit.shortName, unit.website, [unit.city, unit.country].filter(Boolean).join(', ')].filter(Boolean).join(' · ');
  }

  summaryDescription(unit: ResearchUnit): string | null {
    return unit.publicDescription;
  }

  applyAcceptedPublicSummary(summary: string): void {
    const unit = this.currentUnit();
    if (unit) {
      this.currentUnit.set({
        ...unit,
        publicDescription: summary
      });
    }
    this.form.controls.publicDescription.setValue(summary);
  }

  auditUserLabel(userId: number | null): string {
    return userId === null ? 'Sistema / sin usuario' : `Usuario #${userId}`;
  }

  formatAuditDate(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, this.fallbackBackPath(), this.isExternalMode() ? 'Volver a organizaciones externas' : 'Volver a unidades').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(
      this.route,
      this.fallbackBackPath(),
      this.isExternalMode() ? 'Volver a organizaciones externas' : 'Volver a unidades'
    );
  }

  searchLink(): string {
    if (this.isPortalView()) {
      return '/portal/publicaciones';
    }
    return this.navigationContext.isCurrentPath('/admin') ? '/admin/publicaciones' : '/publications';
  }

  unitLink(unitId: number, scope: OrganizationScope): string[] {
    if (this.isPortalView()) {
      return ['/portal/unidades', String(unitId)];
    }
    if (this.navigationContext.isCurrentPath('/admin')) {
      return [scope === 'EXTERNAL' ? '/admin/organizaciones-externas' : '/admin/unidades', String(unitId)];
    }
    return ['/research-units', String(unitId)];
  }

  researcherLink(researcherId: number): string[] {
    if (this.isPortalView()) {
      return ['/portal/investigadores', String(researcherId)];
    }
    return this.navigationContext.isCurrentPath('/admin')
      ? ['/admin/investigadores', String(researcherId)]
      : ['/researchers', String(researcherId)];
  }

  publicationLink(publicationId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin')
      ? ['/admin/publicaciones', String(publicationId)]
      : ['/publications', String(publicationId)];
  }

  detailReturnQueryParams(label: string) {
    return this.navigationContext.returnQueryParams(label);
  }

  private fallbackBackPath(): string {
    if (this.isPortalView()) {
      return '/portal/unidades';
    }
    if (this.navigationContext.isCurrentPath('/admin')) {
      return this.selectedScope() === 'EXTERNAL' ? '/admin/organizaciones-externas' : '/admin/unidades';
    }
    return '/research-units';
  }

  private initializeNewForm(scope: OrganizationScope): void {
    this.form.reset({
      name: '',
      shortName: '',
      type: 'OTHER',
      parentId: null,
      country: null,
      city: null,
      website: '',
      active: true,
      visibleInPortal: scope === 'INTERNAL',
      organizationScope: scope,
      publicDescription: '',
      internalDescription: '',
      responsibleResearcherId: null,
      featured: false,
      sortOrder: null
    });
    this.applyScopeRules(scope, true);
    this.currentUnit.set(null);
  }

  private applyUnit(unit: ResearchUnit): void {
    this.currentUnit.set(unit);
    this.form.patchValue({
      name: unit.name,
      shortName: unit.shortName,
      type: unit.type,
      parentId: unit.parentId,
      country: unit.country,
      city: unit.city,
      website: unit.website,
      active: unit.active,
      visibleInPortal: unit.visibleInPortal,
      organizationScope: unit.organizationScope,
      publicDescription: unit.publicDescription,
      internalDescription: unit.internalDescription,
      responsibleResearcherId: unit.responsibleResearcherId,
      featured: unit.featured ?? false,
      sortOrder: unit.sortOrder
    }, { emitEvent: false });
    this.applyScopeRules(unit.organizationScope, true);
  }

  private applyScopeRules(scope: OrganizationScope, preserveValues: boolean): void {
    this.selectedScope.set(scope);

    if (scope === 'EXTERNAL') {
      this.form.controls.visibleInPortal.disable({ emitEvent: false });
      this.form.controls.visibleInPortal.setValue(false, { emitEvent: false });
      this.form.controls.parentId.setValue(null, { emitEvent: false });
      this.form.controls.featured.setValue(false, { emitEvent: false });
      this.form.controls.responsibleResearcherId.setValue(null, { emitEvent: false });
      this.form.controls.sortOrder.setValue(null, { emitEvent: false });
      if (!EXTERNAL_ORGANIZATION_TYPES.includes(this.form.controls.type.value)) {
        this.form.controls.type.setValue('OTHER', { emitEvent: false });
      }
      return;
    }

    this.form.controls.visibleInPortal.enable({ emitEvent: false });
    if (!preserveValues && this.currentUnit()?.organizationScope !== 'INTERNAL' && !this.form.controls.visibleInPortal.value) {
      this.form.controls.visibleInPortal.setValue(true, { emitEvent: false });
    }
    if (!INTERNAL_RESEARCH_UNIT_TYPES.includes(this.form.controls.type.value)) {
      this.form.controls.type.setValue('OTHER', { emitEvent: false });
    }
  }

  private toRequest(): ResearchUnitRequest {
    const value = this.form.getRawValue();
    return {
      name: value.name,
      shortName: this.emptyToNull(value.shortName),
      type: value.type,
      parentId: value.organizationScope === 'EXTERNAL' ? null : value.parentId,
      country: this.emptyToNull(value.country),
      city: this.emptyToNull(value.city),
      website: this.emptyToNull(value.website),
      active: value.active,
      visibleInPortal: value.organizationScope === 'EXTERNAL' ? false : value.visibleInPortal,
      organizationScope: value.organizationScope,
      publicDescription: this.emptyToNull(value.publicDescription),
      internalDescription: value.organizationScope === 'EXTERNAL' ? null : this.emptyToNull(value.internalDescription),
      responsibleResearcherId: value.organizationScope === 'EXTERNAL' ? null : value.responsibleResearcherId,
      featured: value.organizationScope === 'EXTERNAL' ? false : value.featured,
      sortOrder: value.organizationScope === 'EXTERNAL' ? null : value.sortOrder
    };
  }

  private readUnitId(): number | null {
    const value = this.route.snapshot.paramMap.get('id');
    return value === null ? null : Number(value);
  }

  private emptyToNull(value: string | null): string | null {
    return value === null || value.trim() === '' ? null : value;
  }

  private emptyResearcherPage(): PageResponse<ResearcherSummary> {
    return {
      content: [],
      page: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
      last: true
    };
  }

  private emptyPublicationPage(): PageResponse<PublicationSummary> {
    return {
      content: [],
      page: 0,
      size: 8,
      totalElements: 0,
      totalPages: 0,
      last: true
    };
  }
}
