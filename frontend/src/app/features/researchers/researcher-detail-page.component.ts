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
import { MatTabsModule } from '@angular/material/tabs';

import {
  AffiliationType,
  EventParticipation,
  PublicationStatus,
  PublicationType,
  ResearchUnit,
  Researcher,
  ResearcherAffiliation,
  ResearcherAffiliationRequest,
  ResearcherRequest,
  ValidationStatus
} from '../../core/api/api-models';
import { AcademicMasterDataService } from '../../core/api/academic-master-data.service';
import { EventParticipationsApiService } from '../../core/api/event-participations-api.service';
import { MeApiService } from '../../core/api/me-api.service';
import { ResearchUnitsApiService } from '../../core/api/research-units-api.service';
import { ResearchersApiService } from '../../core/api/researchers-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { AuditHistoryPanelComponent } from '../../shared/components/audit-history-panel.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { PublicSummaryWorkflowComponent } from '../../shared/components/public-summary-workflow.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { VisibilityNoteComponent } from '../../shared/components/visibility-note.component';
import { contentAccessErrorMessage } from '../../shared/utils/api-error-messages';
import {
  affiliationTypeLabel,
  publicationStatusLabel,
  publicationStatusTone,
  publicationTypeLabel,
  validationStatusLabel,
  validationStatusTone
} from '../../shared/utils/display-labels';
import { publicVisibilityNote, visibilityNoteForUser } from '../../shared/utils/visibility-labels';
import { ResearcherGraphComponent } from '../graph/researcher-graph.component';

@Component({
  selector: 'rip-researcher-detail-page',
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
    MatTabsModule,
    AuditHistoryPanelComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    PageHeaderComponent,
    PublicSummaryWorkflowComponent,
    StatusChipComponent,
    TagChipComponent,
    VisibilityNoteComponent,
    ResearcherGraphComponent
  ],
  template: `
    <section class="page researcher-page">
      <rip-page-header
        [title]="researcherId === null ? 'Nuevo investigador' : displayName()"
        [subtitle]="profileSubtitle()"
        eyebrow="Perfil investigador"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
        @if (canSubmitProfile()) {
          <button mat-button type="button" [disabled]="submittingProfile()" (click)="submitProfile()">
            {{ currentResearcher()?.validationStatus === 'CHANGES_REQUESTED' ? 'Reenviar a validacion' : 'Enviar a validacion' }}
          </button>
        }
        @if (canSaveProfile()) {
          <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="researcherForm.invalid || saving()">
            Guardar perfil
          </button>
        }
      </rip-page-header>

      <rip-visibility-note [message]="visibilityNote()" />

      @if (loadError()) {
        <rip-error-state [message]="loadError()" />
      }

      @if (currentResearcher(); as researcher) {
        <mat-card appearance="outlined" class="profile-summary">
          <mat-card-content>
            <div class="profile-header">
              <div class="profile-copy">
                @if (researcher.orcid) {
                  <span class="profile-kicker">ORCID disponible</span>
                }
                <h2>{{ researcher.displayName || researcher.fullName }}</h2>
                <p>{{ researcher.primaryAffiliation?.researchUnitName || 'Sin afiliacion principal actual' }}</p>
              </div>
              <div class="profile-chips">
                <rip-status-chip [label]="researcher.active ? 'Activo' : 'Inactivo'" [tone]="researcher.active ? 'success' : 'neutral'" />
                <rip-status-chip [label]="validationLabel(researcher.validationStatus)" [tone]="validationTone(researcher.validationStatus)" />
              </div>
            </div>

            <div class="metadata-grid">
              <div class="metadata-item">
                <span>Email</span>
                <strong>{{ researcher.email || 'Sin email' }}</strong>
              </div>
              <div class="metadata-item">
                <span>ORCID</span>
                <strong>{{ researcher.orcid || 'Sin ORCID' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Afiliaciones actuales</span>
                <strong>{{ researcher.currentAffiliations.length }}</strong>
              </div>
              <div class="metadata-item">
                <span>Publicaciones</span>
                <strong>{{ researcher.authoredPublications.length }}</strong>
              </div>
            </div>

            @if (showProfileValidationBanner()) {
              <section class="validation-banner" [class.warning-banner]="researcher.validationStatus === 'CHANGES_REQUESTED'">
                <div>
                  <span class="banner-label">{{ researcher.validationStatus === 'CHANGES_REQUESTED' ? 'Requiere cambios' : 'Validacion' }}</span>
                  <p class="banner-title">{{ profileValidationTitle() }}</p>
                  <p class="banner-copy">{{ researcher.validationComment || profileValidationHelp() }}</p>
                </div>
                @if (canSaveProfile() || canSubmitProfile()) {
                  <div class="actions banner-actions">
                    @if (canSaveProfile()) {
                      <button mat-button type="button" (click)="scrollToProfileForm()">Editar</button>
                    }
                    @if (canSubmitProfile()) {
                      <button mat-flat-button color="primary" type="button" [disabled]="submittingProfile()" (click)="submitProfile()">
                        {{ researcher.validationStatus === 'CHANGES_REQUESTED' ? 'Reenviar a validacion' : 'Enviar a validacion' }}
                      </button>
                    }
                  </div>
                }
              </section>
            }

            <div class="tag-list">
              @for (topic of researcher.topics; track topic.id) {
                <rip-tag-chip [label]="topic.name" />
              } @empty {
                <span class="muted">Sin temas publicos destacados</span>
              }
            </div>
          </mat-card-content>
        </mat-card>
      }

      @if (showPublicSummaryWorkflow()) {
        <rip-public-summary-workflow
          [targetType]="'RESEARCHER'"
          [targetId]="currentResearcher()!.id"
          [targetTitle]="displayName()"
          [targetSubtitle]="profileSubtitle()"
          [ownerResearcherName]="displayName()"
          entityLabel="Investigador"
          currentSummaryLabel="Resumen publico actual"
          [allowGeneration]="showPublicSummaryWorkflow()"
          [allowReviewExisting]="showPublicSummaryWorkflow()"
        />
      }

      @if (showProfileBlockedMessage()) {
        <mat-card appearance="outlined" class="info-card">
          <mat-card-content>
            <p class="info-title">Edicion no disponible</p>
            <p>{{ profileBlockedMessage() }}</p>
          </mat-card-content>
        </mat-card>
      }

      @if (canSaveProfile() && !loadError()) {
        <mat-card appearance="outlined" id="researcher-profile-form">
          <mat-card-content>
            <form class="form-grid" [formGroup]="researcherForm">
              <mat-form-field appearance="outline">
                <mat-label>Nombre completo</mat-label>
                <input matInput formControlName="fullName">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Nombre visible</mat-label>
                <input matInput formControlName="displayName">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>ORCID</mat-label>
                <input matInput formControlName="orcid">
              </mat-form-field>
              @if (isAdmin()) {
                <mat-checkbox formControlName="active">Activo</mat-checkbox>
              }
            </form>
          </mat-card-content>
        </mat-card>
      }

      @if (actionMessage()) {
        <div class="action-message" [class.error-message]="actionMessageTone() === 'error'">
          {{ actionMessage() }}
        </div>
      }

      @if (researcherId !== null && !loadError()) {
        <mat-tab-group>
          <mat-tab label="Perfil">
            <section class="tab-content">
              <section class="detail-grid">
                <mat-card appearance="outlined">
                  <mat-card-header>
                    <mat-card-title>Afiliaciones actuales</mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    <div class="list">
                      @for (affiliation of currentResearcher()?.currentAffiliations || []; track affiliation.id) {
                        <div class="list-row" [class.focused-row]="isFocusedAffiliation(affiliation)">
                          <div class="row-copy">
                            <strong>{{ affiliation.researchUnitName || 'Unidad de investigacion' }}</strong>
                            <div class="muted">{{ affiliation.role || 'Sin rol' }} · {{ affiliationLabel(affiliation.affiliationType) }}</div>
                            <div class="muted">{{ dateRange(affiliation) }}</div>
                            <div class="chip-list">
                              <rip-status-chip [label]="validationLabel(affiliation.validationStatus)" [tone]="validationTone(affiliation.validationStatus)" />
                              @if (affiliation.primaryAffiliation) {
                                <rip-status-chip label="Principal" tone="info" />
                              }
                            </div>
                            @if (affiliation.validationComment) {
                              <div class="inline-note" [class.warning-note]="affiliation.validationStatus === 'CHANGES_REQUESTED'">
                                <span>Comentario de validacion</span>
                                <p>{{ affiliation.validationComment }}</p>
                              </div>
                            }
                            @if (isAdmin()) {
                              <div class="muted">Creado por {{ auditUserLabel(affiliation.createdByUserId) }} · Modificado el {{ formatDateTime(affiliation.updatedAt) }}</div>
                            }
                          </div>
                          <div class="row-actions">
                            @if (affiliation.canEdit) {
                              <button mat-button type="button" (click)="editAffiliation(affiliation)">Editar</button>
                            }
                            @if (canSubmitAffiliation(affiliation)) {
                              <button mat-button type="button" [disabled]="submittingAffiliationId() === affiliation.id" (click)="submitAffiliation(affiliation)">
                                {{ affiliation.validationStatus === 'CHANGES_REQUESTED' ? 'Reenviar' : 'Enviar a validacion' }}
                              </button>
                            }
                            @if (isAdmin()) {
                              <button mat-button type="button" (click)="deleteAffiliation(affiliation)">Eliminar</button>
                            }
                          </div>
                        </div>
                      } @empty {
                        <rip-empty-state title="Sin afiliaciones actuales" message="No hay afiliaciones vigentes registradas." />
                      }
                    </div>
                  </mat-card-content>
                </mat-card>

                <mat-card appearance="outlined">
                  <mat-card-header>
                    <mat-card-title>Afiliaciones anteriores</mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    <div class="list">
                      @for (affiliation of currentResearcher()?.pastAffiliations || []; track affiliation.id) {
                        <div class="list-row" [class.focused-row]="isFocusedAffiliation(affiliation)">
                          <div class="row-copy">
                            <strong>{{ affiliation.researchUnitName || 'Unidad de investigacion' }}</strong>
                            <div class="muted">{{ affiliation.role || 'Sin rol' }} · {{ affiliationLabel(affiliation.affiliationType) }}</div>
                            <div class="muted">{{ dateRange(affiliation) }}</div>
                            <div class="chip-list">
                              <rip-status-chip [label]="validationLabel(affiliation.validationStatus)" [tone]="validationTone(affiliation.validationStatus)" />
                            </div>
                            @if (affiliation.validationComment) {
                              <div class="inline-note" [class.warning-note]="affiliation.validationStatus === 'CHANGES_REQUESTED'">
                                <span>Comentario de validacion</span>
                                <p>{{ affiliation.validationComment }}</p>
                              </div>
                            }
                          </div>
                          <div class="row-actions">
                            @if (affiliation.canEdit) {
                              <button mat-button type="button" (click)="editAffiliation(affiliation)">Editar</button>
                            }
                            @if (canSubmitAffiliation(affiliation)) {
                              <button mat-button type="button" [disabled]="submittingAffiliationId() === affiliation.id" (click)="submitAffiliation(affiliation)">
                                {{ affiliation.validationStatus === 'CHANGES_REQUESTED' ? 'Reenviar' : 'Enviar a validacion' }}
                              </button>
                            }
                            @if (isAdmin()) {
                              <button mat-button type="button" (click)="deleteAffiliation(affiliation)">Eliminar</button>
                            }
                          </div>
                        </div>
                      } @empty {
                        <rip-empty-state title="Sin afiliaciones anteriores" message="No hay historial previo registrado." />
                      }
                    </div>
                  </mat-card-content>
                </mat-card>
              </section>

              @if (showAffiliationBlockedMessage()) {
                <mat-card appearance="outlined" class="info-card">
                  <mat-card-content>
                    <p class="info-title">No puedes editar esta afiliacion</p>
                    <p>{{ affiliationBlockedMessage() }}</p>
                  </mat-card-content>
                </mat-card>
              }

              @if (canShowAffiliationForm()) {
                <mat-card appearance="outlined">
                  <mat-card-header>
                    <mat-card-title>{{ affiliationFormTitle() }}</mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    @if (editingAffiliation(); as focusedAffiliation) {
                      <div class="inline-note" [class.warning-note]="focusedAffiliation.validationStatus === 'CHANGES_REQUESTED'">
                        <span>Estado de validacion</span>
                        <p>
                          {{ validationLabel(focusedAffiliation.validationStatus) }}
                          @if (focusedAffiliation.validationComment) {
                            · {{ focusedAffiliation.validationComment }}
                          }
                        </p>
                      </div>
                    }

                    <form class="form-grid" [formGroup]="affiliationForm" (ngSubmit)="saveAffiliation()">
                      <mat-form-field appearance="outline">
                        <mat-label>Unidad de investigacion</mat-label>
                        <mat-select formControlName="researchUnitId">
                          @for (unit of units(); track unit.id) {
                            <mat-option [value]="unit.id">{{ unit.name }}</mat-option>
                          }
                        </mat-select>
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>Rol</mat-label>
                        <input matInput formControlName="role">
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>Tipo</mat-label>
                        <mat-select formControlName="affiliationType">
                          @for (type of affiliationTypes; track type) {
                            <mat-option [value]="type">{{ affiliationLabel(type) }}</mat-option>
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
                      <mat-checkbox formControlName="primaryAffiliation">Afiliacion principal actual</mat-checkbox>
                      <div class="actions">
                        @if (editingAffiliationId() !== null) {
                          <button mat-button type="button" (click)="cancelAffiliationEdit()">Cancelar</button>
                        }
                        <button mat-flat-button color="primary" type="submit" [disabled]="affiliationForm.invalid || savingAffiliation()">
                          {{ editingAffiliationId() === null ? 'Anadir' : 'Actualizar' }}
                        </button>
                      </div>
                    </form>
                  </mat-card-content>
                </mat-card>
              }

              <section class="detail-grid">
                <mat-card appearance="outlined">
                  <mat-card-header>
                    <mat-card-title>Temas</mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    <div class="tag-list">
                      @for (topic of currentResearcher()?.topics || []; track topic.id) {
                        <rip-tag-chip [label]="topic.name" />
                      } @empty {
                        <rip-empty-state title="Sin temas" message="Aun no hay temas inferidos desde publicaciones." />
                      }
                    </div>
                  </mat-card-content>
                </mat-card>

                <mat-card appearance="outlined">
                  <mat-card-header>
                    <mat-card-title>Coautores</mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    <table>
                      <thead>
                        <tr><th>Nombre</th><th>Compartidas</th></tr>
                      </thead>
                      <tbody>
                        @for (coauthor of currentResearcher()?.coauthors || []; track coauthor.name) {
                          <tr>
                            <td>
                              @if (coauthor.researcherId !== null) {
                                <a [routerLink]="researcherLink(coauthor.researcherId)" [queryParams]="detailReturnQueryParams('Volver al investigador')">{{ coauthor.name }}</a>
                              } @else {
                                {{ coauthor.name }}
                              }
                              <div class="muted">{{ coauthor.internal ? 'Interno' : 'Externo' }}</div>
                            </td>
                            <td>{{ coauthor.sharedPublicationCount }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </mat-card-content>
                </mat-card>
              </section>

              <mat-card appearance="outlined">
                <mat-card-header>
                  <mat-card-title>Participaciones en eventos</mat-card-title>
                </mat-card-header>
                <mat-card-content>
                  <div class="item-list">
                    @for (participation of eventParticipations(); track participation.id) {
                      <a class="item-row interactive publication-item" [routerLink]="participationLink(participation.id)" [queryParams]="detailReturnQueryParams('Volver al investigador')">
                        <strong class="item-title">{{ participation.eventName }}</strong>
                        <div class="item-meta">
                          <span>{{ participationTypeLabel(participation.participationTypeCode) }}</span>
                          <span>{{ participation.participationDate || 'Sin fecha' }}</span>
                          <rip-status-chip [label]="validationLabel(participation.validationStatus)" [tone]="validationTone(participation.validationStatus)" />
                        </div>
                        <span>{{ participation.title }}</span>
                      </a>
                    } @empty {
                      <rip-empty-state title="Sin participaciones" message="Aun no hay participaciones registradas para este investigador." />
                    }
                  </div>
                </mat-card-content>
              </mat-card>

              <mat-card appearance="outlined">
                <mat-card-header>
                  <mat-card-title>Publicaciones como autor</mat-card-title>
                </mat-card-header>
                <mat-card-content>
                  <div class="item-list">
                    @for (publication of currentResearcher()?.authoredPublications || []; track publication.id) {
                      <a class="item-row interactive publication-item" [routerLink]="publicationLink(publication.id)" [queryParams]="detailReturnQueryParams('Volver al investigador')">
                        <strong class="item-title">{{ publication.title }}</strong>
                        <div class="item-meta">
                          <span>{{ publication.year || 'Sin ano' }}</span>
                          <rip-tag-chip [label]="typeLabel(publication.type)" tone="type" />
                          <rip-status-chip [label]="statusLabel(publication.status)" [tone]="statusTone(publication.status)" />
                          <rip-status-chip [label]="validationLabel(publication.validationStatus)" [tone]="validationTone(publication.validationStatus)" />
                        </div>
                        <div class="chip-list">
                          @for (topic of publication.topics; track topic) {
                            <rip-tag-chip [label]="topic" />
                          } @empty {
                            <span class="muted">Sin temas</span>
                          }
                        </div>
                      </a>
                    } @empty {
                      <rip-empty-state title="Sin publicaciones" message="Aun no hay publicaciones asociadas a este investigador." />
                    }
                  </div>
                </mat-card-content>
              </mat-card>

              @if (isAdmin()) {
                <rip-audit-history-panel
                  [entityType]="'RESEARCHER'"
                  [entityId]="researcherId"
                  title="Historial"
                  subtitle="Cambios sobre el perfil y sus transiciones dentro del flujo institucional."
                />
              }
            </section>
          </mat-tab>

          <mat-tab label="Grafo">
            <ng-template matTabContent>
              <div class="tab-content">
                <rip-researcher-graph [researcherId]="resolvedResearcherId()" [portalView]="isPortalView()"></rip-researcher-graph>
              </div>
            </ng-template>
          </mat-tab>
        </mat-tab-group>
      }
    </section>
  `,
  styles: [`
    .researcher-page,
    .tab-content {
      display: grid;
      gap: 24px;
    }

    .profile-summary mat-card-content {
      display: grid;
      gap: 22px;
    }

    .profile-header,
    .validation-banner {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
    }

    .profile-copy {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .profile-header h2 {
      margin: 0;
      color: #142033;
      font-size: clamp(1.9rem, 3vw, 2.5rem);
      line-height: 1.05;
      overflow-wrap: anywhere;
    }

    .profile-header p,
    .banner-copy,
    .inline-note p,
    .info-card p {
      margin: 0;
      color: #667487;
      line-height: 1.55;
    }

    .profile-kicker,
    .banner-label,
    .metadata-item span,
    .inline-note span,
    .info-title {
      color: #5f7083;
      font-size: 0.76rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .profile-kicker {
      display: inline-flex;
    }

    .profile-chips,
    .chip-list,
    .tag-list,
    .row-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .metadata-grid,
    .detail-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 24px;
    }

    .metadata-grid {
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
    }

    .metadata-item,
    .list-row,
    .inline-note,
    .action-message,
    .info-card mat-card-content {
      padding: 16px;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      background: #ffffff;
    }

    .metadata-item {
      display: grid;
      gap: 8px;
    }

    .metadata-item strong,
    .row-copy strong {
      color: #243044;
      line-height: 1.45;
      overflow-wrap: anywhere;
    }

    .validation-banner {
      padding: 18px;
      border: 1px solid #d9e6f2;
      border-radius: 14px;
      background: #f7fbff;
    }

    .warning-banner,
    .inline-note.warning-note {
      border-color: #efc27a;
      background: #fff8ea;
    }

    .banner-title {
      margin: 6px 0;
      color: #243044;
      font-size: 1rem;
      font-weight: 760;
      line-height: 1.4;
    }

    .banner-actions {
      align-items: flex-start;
    }

    .info-title {
      display: block;
      margin-bottom: 8px;
    }

    .list {
      display: grid;
      gap: 12px;
    }

    .list-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 14px;
    }

    .focused-row {
      border-color: #7db3cb;
      box-shadow: inset 0 0 0 1px rgba(125, 179, 203, 0.16);
    }

    .row-copy {
      display: grid;
      gap: 8px;
    }

    .inline-note {
      display: grid;
      gap: 8px;
      background: #f7fbff;
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

    th,
    td {
      padding: 12px 14px;
      border-bottom: 1px solid #e4e9ef;
      text-align: left;
      vertical-align: top;
    }

    th {
      color: #5a6677;
      font-size: 0.82rem;
      font-weight: 650;
      text-transform: uppercase;
    }

    .publication-item {
      grid-template-columns: 1fr;
    }

    rip-status-chip {
      display: inline-flex;
    }

    @media (max-width: 720px) {
      .profile-header,
      .validation-banner,
      .list-row {
        display: grid;
      }

      .profile-chips,
      .row-actions {
        justify-content: flex-start;
      }
    }
  `]
})
export class ResearcherDetailPageComponent implements OnInit {
  private readonly researchersApi = inject(ResearchersApiService);
  private readonly participationsApi = inject(EventParticipationsApiService);
  private readonly unitsApi = inject(ResearchUnitsApiService);
  private readonly meApi = inject(MeApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly masterData = inject(AcademicMasterDataService);
  private readonly navigationContext = inject(NavigationContextService);

  readonly affiliationTypes: AffiliationType[] = ['MEMBER', 'LEADER', 'VISITING', 'COLLABORATOR', 'FORMER_MEMBER', 'OTHER'];
  readonly researcherId = this.readResearcherId();
  readonly isPortalView = signal(this.route.snapshot.data['portalView'] === true);
  readonly currentResearcher = signal<Researcher | null>(null);
  readonly eventParticipations = signal<EventParticipation[]>([]);
  readonly units = signal<ResearchUnit[]>([]);
  readonly saving = signal(false);
  readonly savingAffiliation = signal(false);
  readonly submittingProfile = signal(false);
  readonly submittingAffiliationId = signal<number | null>(null);
  readonly loadError = signal('');
  readonly actionMessage = signal('');
  readonly actionMessageTone = signal<'success' | 'error'>('success');
  readonly editingAffiliationId = signal<number | null>(null);
  readonly focusEntityType = signal<string | null>(null);
  readonly focusEntityId = signal<number | null>(null);
  readonly visibilityNote = computed(() => this.isPortalView() ? publicVisibilityNote() : visibilityNoteForUser(this.auth.currentUser()));

  readonly displayName = computed(() => {
    const researcher = this.currentResearcher();
    return researcher?.displayName || researcher?.fullName || 'Detalle de investigador';
  });

  readonly profileSubtitle = computed(() => {
    const researcher = this.currentResearcher();
    if (this.researcherId === null) {
      return 'Crear perfil de investigador';
    }
    if (this.isPortalView()) {
      return researcher?.primaryAffiliation?.researchUnitName || researcher?.orcid || 'Perfil publico de investigador';
    }
    return researcher?.primaryAffiliation?.researchUnitName || researcher?.email || 'Perfil de investigador';
  });

  readonly editingAffiliation = computed(() => {
    const affiliationId = this.editingAffiliationId();
    return this.currentResearcher()?.affiliations.find((affiliation) => affiliation.id === affiliationId) ?? null;
  });

  readonly focusedAffiliation = computed(() => {
    if (this.focusEntityType() !== 'RESEARCHER_AFFILIATION') {
      return null;
    }
    const focusId = this.focusEntityId();
    return this.currentResearcher()?.affiliations.find((affiliation) => affiliation.id === focusId) ?? null;
  });

  readonly researcherForm = new FormGroup({
    fullName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    displayName: new FormControl<string | null>(null),
    email: new FormControl<string | null>(null),
    orcid: new FormControl<string | null>(null),
    active: new FormControl(true, { nonNullable: true })
  });

  readonly affiliationForm = new FormGroup({
    researchUnitId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    role: new FormControl<string | null>(null),
    affiliationType: new FormControl<AffiliationType>('MEMBER', { nonNullable: true, validators: [Validators.required] }),
    startDate: new FormControl<string | null>(null),
    endDate: new FormControl<string | null>(null),
    primaryAffiliation: new FormControl(false, { nonNullable: true })
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();

    this.unitsApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((units) => this.units.set(units));

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        this.focusEntityType.set(params.get('focusEntityType'));
        const focusId = params.get('focusEntityId');
        this.focusEntityId.set(focusId === null ? null : Number(focusId));
        this.applyFocus();
      });

    if (this.researcherId !== null) {
      this.loadResearcher();
    }
  }

  isAdmin(): boolean {
    return this.auth.hasAnyRole(['ADMIN']);
  }

  showPublicSummaryWorkflow(): boolean {
    const researcher = this.currentResearcher();
    if (!researcher || this.isPortalView()) {
      return false;
    }

    return this.isAdmin() || researcher.id === this.auth.currentUser()?.researcherId;
  }

  canSaveProfile(): boolean {
    if (this.isPortalView()) {
      return false;
    }
    if (this.researcherId === null) {
      return this.isAdmin();
    }
    return this.currentResearcher()?.canEdit ?? false;
  }

  canSubmitProfile(): boolean {
    const researcher = this.currentResearcher();
    return !this.isPortalView()
      && !!researcher
      && !this.isAdmin()
      && researcher.id === this.auth.currentUser()?.researcherId
      && researcher.canSubmit;
  }

  showProfileBlockedMessage(): boolean {
    return !this.isPortalView()
      && this.auth.isAuthenticated()
      && !!this.currentResearcher()
      && !this.canSaveProfile();
  }

  profileBlockedMessage(): string {
    const researcher = this.currentResearcher();
    if (!researcher) {
      return '';
    }
    switch (researcher.validationStatus) {
      case 'PENDING_VALIDATION':
        return 'El perfil esta en revision institucional y no admite cambios hasta que termine esa revision.';
      case 'VALIDATED':
        return 'El perfil ya fue validado. Solo los borradores o registros con cambios solicitados pueden editarse.';
      case 'REJECTED':
        return 'El perfil fue rechazado. Revisa el historial para conocer el motivo antes de continuar.';
      default:
        return 'No puedes editar este perfil en su estado actual.';
    }
  }

  showProfileValidationBanner(): boolean {
    const researcher = this.currentResearcher();
    return !!researcher && (
      researcher.validationStatus === 'CHANGES_REQUESTED'
      || !!researcher.validationComment
      || researcher.validationStatus === 'PENDING_VALIDATION'
    );
  }

  profileValidationTitle(): string {
    const status = this.currentResearcher()?.validationStatus;
    switch (status) {
      case 'CHANGES_REQUESTED':
        return 'Hay ajustes pendientes antes de reenviar tu perfil.';
      case 'PENDING_VALIDATION':
        return 'Tu perfil esta en revision.';
      case 'VALIDATED':
        return 'El perfil ya esta validado.';
      case 'REJECTED':
        return 'El perfil fue rechazado.';
      default:
        return 'El perfil sigue en borrador.';
    }
  }

  profileValidationHelp(): string {
    const status = this.currentResearcher()?.validationStatus;
    switch (status) {
      case 'CHANGES_REQUESTED':
        return 'Corrige los campos observados y reenvia el perfil cuando este completo.';
      case 'PENDING_VALIDATION':
        return 'Puedes revisar la informacion, pero no modificarla mientras dura la revision.';
      case 'VALIDATED':
        return 'Consulta el historial para entender la ultima validacion.';
      case 'REJECTED':
        return 'Consulta el historial para revisar el motivo del rechazo.';
      default:
        return 'Completa la informacion pendiente antes de enviarla a validacion.';
    }
  }

  canSubmitAffiliation(affiliation: ResearcherAffiliation): boolean {
    return !this.isPortalView()
      && !this.isAdmin()
      && affiliation.researcherId === this.auth.currentUser()?.researcherId
      && affiliation.canSubmit;
  }

  canShowAffiliationForm(): boolean {
    if (this.isPortalView()) {
      return false;
    }
    if (this.isAdmin()) {
      return true;
    }
    return this.editingAffiliation()?.canEdit ?? false;
  }

  showAffiliationBlockedMessage(): boolean {
    const focused = this.focusedAffiliation();
    return !this.isPortalView()
      && !!focused
      && !focused.canEdit
      && this.focusEntityType() === 'RESEARCHER_AFFILIATION';
  }

  affiliationBlockedMessage(): string {
    const affiliation = this.focusedAffiliation();
    if (!affiliation) {
      return '';
    }
    switch (affiliation.validationStatus) {
      case 'PENDING_VALIDATION':
        return 'La afiliacion esta en revision institucional y no puede editarse mientras siga pendiente.';
      case 'VALIDATED':
        return 'La afiliacion ya fue validada. Solo los borradores o registros con cambios solicitados pueden modificarse.';
      case 'REJECTED':
        return 'La afiliacion fue rechazada. Revisa el historial y el comentario de validacion.';
      default:
        return 'No puedes editar esta afiliacion en su estado actual.';
    }
  }

  affiliationFormTitle(): string {
    if (this.editingAffiliationId() !== null) {
      return 'Editar afiliacion';
    }
    return 'Anadir afiliacion';
  }

  save(): void {
    if (!this.canSaveProfile() || this.researcherForm.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    const request = this.toResearcherRequest();
    const operation = this.researcherId === null ? this.researchersApi.create(request) : this.researchersApi.update(this.researcherId, request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (researcher) => {
        this.saving.set(false);
        this.actionMessageTone.set('success');
        this.actionMessage.set('Perfil guardado.');
        void this.router.navigate(this.researcherLink(researcher.id), {
          queryParams: this.preservedQueryParams('RESEARCHER', researcher.id)
        });
      },
      error: () => {
        this.saving.set(false);
        this.actionMessageTone.set('error');
        this.actionMessage.set('No se pudo guardar el perfil.');
      }
    });
  }

  submitProfile(): void {
    const researcher = this.currentResearcher();
    if (!researcher || !this.canSubmitProfile() || this.submittingProfile()) {
      return;
    }
    this.submittingProfile.set(true);
    this.meApi.submitActivity('RESEARCHER', researcher.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submittingProfile.set(false);
          this.actionMessageTone.set('success');
          this.actionMessage.set(
            researcher.validationStatus === 'CHANGES_REQUESTED'
              ? 'Perfil reenviado a validacion.'
              : 'Perfil enviado a validacion.'
          );
          this.loadResearcher();
        },
        error: () => {
          this.submittingProfile.set(false);
          this.actionMessageTone.set('error');
          this.actionMessage.set('No se pudo enviar el perfil a validacion.');
        }
      });
  }

  saveAffiliation(): void {
    if (this.researcherId === null || this.affiliationForm.invalid || this.savingAffiliation()) {
      return;
    }
    const request = this.toAffiliationRequest();
    if (request === null) {
      return;
    }
    const affiliationId = this.editingAffiliationId();
    if (!this.isAdmin() && affiliationId === null) {
      return;
    }
    this.savingAffiliation.set(true);
    const operation = affiliationId === null
      ? this.researchersApi.addAffiliation(this.researcherId, request)
      : this.researchersApi.updateAffiliation(this.researcherId, affiliationId, request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.savingAffiliation.set(false);
        this.actionMessageTone.set('success');
        this.actionMessage.set('Afiliacion guardada.');
        this.resetAffiliationForm();
        this.loadResearcher();
      },
      error: () => {
        this.savingAffiliation.set(false);
        this.actionMessageTone.set('error');
        this.actionMessage.set('No se pudo guardar la afiliacion.');
      }
    });
  }

  submitAffiliation(affiliation: ResearcherAffiliation): void {
    if (!this.canSubmitAffiliation(affiliation) || this.submittingAffiliationId() !== null) {
      return;
    }
    this.submittingAffiliationId.set(affiliation.id);
    this.meApi.submitActivity('RESEARCHER_AFFILIATION', affiliation.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submittingAffiliationId.set(null);
          this.actionMessageTone.set('success');
          this.actionMessage.set(
            affiliation.validationStatus === 'CHANGES_REQUESTED'
              ? 'Afiliacion reenviada a validacion.'
              : 'Afiliacion enviada a validacion.'
          );
          this.loadResearcher();
        },
        error: () => {
          this.submittingAffiliationId.set(null);
          this.actionMessageTone.set('error');
          this.actionMessage.set('No se pudo enviar la afiliacion a validacion.');
        }
      });
  }

  editAffiliation(affiliation: ResearcherAffiliation): void {
    if (!affiliation.canEdit) {
      return;
    }
    this.editingAffiliationId.set(affiliation.id);
    this.affiliationForm.reset({
      researchUnitId: affiliation.researchUnitId,
      role: affiliation.role,
      affiliationType: affiliation.affiliationType,
      startDate: affiliation.startDate,
      endDate: affiliation.endDate,
      primaryAffiliation: affiliation.primaryAffiliation
    });
  }

  cancelAffiliationEdit(): void {
    this.resetAffiliationForm();
  }

  deleteAffiliation(affiliation: ResearcherAffiliation): void {
    if (this.researcherId === null || !this.isAdmin()) {
      return;
    }
    this.researchersApi.deleteAffiliation(this.researcherId, affiliation.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.editingAffiliationId() === affiliation.id) {
          this.resetAffiliationForm();
        }
        this.actionMessageTone.set('success');
        this.actionMessage.set('Afiliacion eliminada.');
        this.loadResearcher();
      });
  }

  isFocusedAffiliation(affiliation: ResearcherAffiliation): boolean {
    return this.focusEntityType() === 'RESEARCHER_AFFILIATION' && this.focusEntityId() === affiliation.id;
  }

  scrollToProfileForm(): void {
    if (typeof document === 'undefined') {
      return;
    }
    document.getElementById('researcher-profile-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  dateRange(affiliation: ResearcherAffiliation): string {
    return `${affiliation.startDate || 'Sin inicio'} - ${affiliation.endDate || 'Presente'}`;
  }

  resolvedResearcherId(): number {
    return this.researcherId ?? 0;
  }

  affiliationLabel(type: string | AffiliationType): string {
    return affiliationTypeLabel(type);
  }

  typeLabel(type: string | PublicationType): string {
    return publicationTypeLabel(type);
  }

  statusLabel(status: string | PublicationStatus): string {
    return publicationStatusLabel(status);
  }

  statusTone(status: string | PublicationStatus): 'neutral' | 'success' | 'warning' | 'info' {
    return publicationStatusTone(status);
  }

  validationLabel(status: ValidationStatus): string {
    return validationStatusLabel(status);
  }

  validationTone(status: ValidationStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  participationTypeLabel(code: string): string {
    return this.masterData.eventParticipationTypeLabel(code);
  }

  auditUserLabel(userId: number | null): string {
    return userId === null ? 'Sistema / sin usuario' : `Usuario #${userId}`;
  }

  formatDateTime(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, this.fallbackBackPath(), 'Volver a investigadores').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, this.fallbackBackPath(), 'Volver a investigadores');
  }

  researcherLink(researcherId: number): string[] {
    if (this.isPortalView()) {
      return ['/portal/investigadores', String(researcherId)];
    }
    return this.navigationContext.isCurrentPath('/admin/investigadores')
      ? ['/admin/investigadores', String(researcherId)]
      : ['/researchers', String(researcherId)];
  }

  publicationLink(publicationId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/investigadores')
      ? ['/admin/publicaciones', String(publicationId)]
      : ['/publications', String(publicationId)];
  }

  participationLink(participationId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/investigadores')
      ? ['/admin/participaciones', String(participationId)]
      : ['/participations', String(participationId)];
  }

  detailReturnQueryParams(label: string): Record<string, string> {
    return this.navigationContext.returnQueryParams(label);
  }

  private fallbackBackPath(): string {
    if (this.isPortalView()) {
      return '/portal/investigadores';
    }
    return this.navigationContext.isCurrentPath('/admin/investigadores') ? '/admin/investigadores' : '/researchers';
  }

  private loadResearcher(): void {
    if (this.researcherId === null) {
      return;
    }
    this.loadError.set('');
    this.researchersApi.get(this.researcherId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (researcher) => {
          this.currentResearcher.set(researcher);
          this.researcherForm.patchValue({
            fullName: researcher.fullName,
            displayName: researcher.displayName,
            email: researcher.email,
            orcid: researcher.orcid,
            active: researcher.active
          });
          this.loadParticipations(researcher.id);
          this.applyFocus();
        },
        error: (error: unknown) => {
          this.currentResearcher.set(null);
          this.eventParticipations.set([]);
          this.loadError.set(contentAccessErrorMessage(error, 'No se pudo cargar el investigador.'));
        }
      });
  }

  private loadParticipations(researcherId: number): void {
    this.participationsApi.search({ researcherId, size: 50 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => this.eventParticipations.set(result.content),
        error: () => this.eventParticipations.set([])
      });
  }

  private applyFocus(): void {
    if (this.focusEntityType() !== 'RESEARCHER_AFFILIATION') {
      return;
    }
    const affiliation = this.focusedAffiliation();
    if (affiliation?.canEdit) {
      this.editAffiliation(affiliation);
    }
  }

  private toResearcherRequest(): ResearcherRequest {
    const value = this.researcherForm.getRawValue();
    return {
      fullName: value.fullName,
      displayName: this.emptyToNull(value.displayName),
      email: this.emptyToNull(value.email),
      orcid: this.emptyToNull(value.orcid),
      active: this.isAdmin() ? value.active : this.currentResearcher()?.active ?? value.active
    };
  }

  private toAffiliationRequest(): ResearcherAffiliationRequest | null {
    const value = this.affiliationForm.getRawValue();
    if (value.researchUnitId === null) {
      return null;
    }
    return {
      researchUnitId: value.researchUnitId,
      role: this.emptyToNull(value.role),
      affiliationType: value.affiliationType,
      startDate: this.emptyToNull(value.startDate),
      endDate: this.emptyToNull(value.endDate),
      primaryAffiliation: value.primaryAffiliation
    };
  }

  private resetAffiliationForm(): void {
    this.editingAffiliationId.set(null);
    this.affiliationForm.reset({
      researchUnitId: null,
      role: null,
      affiliationType: 'MEMBER',
      startDate: null,
      endDate: null,
      primaryAffiliation: false
    });
  }

  private readResearcherId(): number | null {
    const value = this.route.snapshot.paramMap.get('id');
    return value === null ? null : Number(value);
  }

  private preservedQueryParams(focusEntityType: string, focusEntityId: number): Record<string, string | number> {
    const params = this.route.snapshot.queryParamMap;
    const preserved: Record<string, string | number> = {
      focusEntityType,
      focusEntityId
    };
    const returnTo = params.get('returnTo');
    const returnLabel = params.get('returnLabel');
    if (returnTo) {
      preserved['returnTo'] = returnTo;
    }
    if (returnLabel) {
      preserved['returnLabel'] = returnLabel;
    }
    return preserved;
  }

  private emptyToNull(value: string | null): string | null {
    return value === null || value.trim() === '' ? null : value;
  }
}
