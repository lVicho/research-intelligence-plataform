import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AcademicMasterDataService } from '../../core/api/academic-master-data.service';
import {
  Publication,
  PublicationAuthor,
  PublicationAuthorRequest,
  PublicationRequest,
  PublicationStatus,
  PublicationType,
  Publisher,
  RelatedPublicationsResponse,
  ResearcherSummary,
  ValidationStatus,
  Venue
} from '../../core/api/api-models';
import { PublishersApiService } from '../../core/api/publishers-api.service';
import { PublicationsApiService } from '../../core/api/publications-api.service';
import { ResearchersApiService } from '../../core/api/researchers-api.service';
import {
  TopicRecommendation,
  TopicRecommendationRequest,
  TopicRecommendationsApiService
} from '../../core/api/topic-recommendations-api.service';
import { VenuesApiService } from '../../core/api/venues-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { AuditHistoryPanelComponent } from '../../shared/components/audit-history-panel.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { PublicSummaryWorkflowComponent } from '../../shared/components/public-summary-workflow.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { TopicRecommendationCardComponent } from '../../shared/components/topic-recommendation-card.component';
import { VisibilityNoteComponent } from '../../shared/components/visibility-note.component';
import { contentAccessErrorMessage } from '../../shared/utils/api-error-messages';
import {
  publicationStatusTone,
  validationStatusLabel,
  validationStatusTone
} from '../../shared/utils/display-labels';
import { visibilityNoteForUser } from '../../shared/utils/visibility-labels';
import {
  PublicationExplanationDialogComponent,
  PublicationExplanationMode
} from './publication-explanation-dialog.component';

type AuthorKind = 'internal' | 'external';
type RelatedMode = 'STRICT' | 'BALANCED' | 'BROAD';

type AuthorFormGroup = FormGroup<{
  authorKind: FormControl<AuthorKind>;
  researcherId: FormControl<number | null>;
  externalAuthorName: FormControl<string>;
  externalAffiliation: FormControl<string>;
  correspondingAuthor: FormControl<boolean>;
}>;

@Component({
  selector: 'rip-publication-detail-page',
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
    EmptyStateComponent,
    ErrorStateComponent,
    PageHeaderComponent,
    PublicSummaryWorkflowComponent,
    StatusChipComponent,
    TagChipComponent,
    TopicRecommendationCardComponent,
    VisibilityNoteComponent
  ],
  template: `
    <section class="page publication-page">
      <rip-page-header
        [title]="publicationId() === null ? 'Nueva publicación' : (currentPublication()?.title || 'Detalle de publicación')"
        [subtitle]="publicationId() === null ? 'Registra una publicación con metadatos, autoría y validación listos para trabajar.' : publicationSubtitle()"
        eyebrow="Producción científica"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
        @if (showPublicationExplanationAction()) {
          <button mat-flat-button color="primary" type="button" (click)="requestPublicationExplanation()">
            Explicar esta publicación
          </button>
        }
        @if (canSubmitCurrent()) {
          <button mat-button type="button" [disabled]="submitting()" (click)="submit()">
            {{ submitButtonLabel() }}
          </button>
        }
        @if (canSave()) {
          <button
            mat-flat-button
            color="primary"
            type="button"
            (click)="save()"
            [disabled]="publicationForm.invalid || authorArray.invalid || authorArray.length === 0 || saving()"
          >
            Guardar
          </button>
        }
      </rip-page-header>

      <rip-visibility-note [message]="pageVisibilityNote()" />

      @if (loadError()) {
        <rip-error-state [message]="loadError()" />
      }

      @if (currentPublication(); as publication) {
        <mat-card appearance="outlined" class="summary-card">
          <mat-card-content>
            <div class="summary-head">
              <div class="summary-copy">
                <span class="summary-kicker">{{ typeLabel(publication.type) }}</span>
                <h2>{{ publication.title }}</h2>
                <p>{{ publicationSubtitle() }}</p>
              </div>
              <div class="summary-chips">
                <rip-status-chip [label]="statusLabel(publication.status)" [tone]="statusTone(publication.status)" />
                <rip-status-chip
                  [label]="validationLabel(publication.validationStatus)"
                  [tone]="validationTone(publication.validationStatus)"
                />
              </div>
            </div>

            <div class="metadata-grid">
              <div class="metadata-item">
                <span>Estado academico</span>
                <strong>{{ statusLabel(publication.status) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Estado de validación</span>
                <strong>{{ validationLabel(publication.validationStatus) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Fecha de publicación</span>
                <strong>{{ publicationDateLabel(publication) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Canal</span>
                <strong>{{ selectedVenueName() || publication.source || 'Sin canal' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Editorial</span>
                <strong>{{ selectedPublisherName() || 'Sin editorial' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Idioma</span>
                <strong>{{ publication.languageCode || 'Sin idioma' }}</strong>
              </div>
            </div>

            @if (showValidationBanner()) {
              <section class="validation-banner" [class.warning-banner]="publication.validationStatus === 'CHANGES_REQUESTED'">
                <div>
                  <span class="banner-label">
                    {{ publication.validationStatus === 'CHANGES_REQUESTED' ? 'Requiere cambios' : 'Validación' }}
                  </span>
                  <p class="banner-title">{{ validationHeadline(publication.validationStatus) }}</p>
                  <p class="banner-copy">{{ publication.validationComment || validationHelpCopy(publication.validationStatus) }}</p>
                </div>
                @if (canSave() || canSubmitCurrent()) {
                  <div class="actions banner-actions">
                    @if (canSave()) {
                      <button mat-button type="button" (click)="scrollToForm()">Editar</button>
                    }
                    @if (canSubmitCurrent()) {
                      <button mat-flat-button color="primary" type="button" [disabled]="submitting()" (click)="submit()">
                        {{ submitButtonLabel() }}
                      </button>
                    }
                  </div>
                }
              </section>
            }

            @if (publication.abstractText) {
              <div class="text-block">
                <h3>Resumen</h3>
                <p>{{ publication.abstractText }}</p>
              </div>
            }

            @if (publication.publicSummary) {
              <div class="text-block">
                <h3>Resumen público</h3>
                <p>{{ publication.publicSummary }}</p>
              </div>
            }

            <div class="summary-columns">
              <div class="summary-panel">
                <h3>Autores</h3>
                <div class="stack-list">
                  @for (author of publication.authors; track author.id) {
                    <div class="stack-row">
                      <div>
                        <strong>{{ authorName(author) }}</strong>
                        <p>{{ author.externalAffiliation || (author.researcherId ? 'Investigador interno' : 'Autor externo') }}</p>
                      </div>
                      <div class="chip-list compact">
                        <rip-tag-chip [label]="'Orden ' + author.authorOrder" />
                        @if (author.correspondingAuthor) {
                          <rip-status-chip label="Correspondencia" tone="info" />
                        }
                      </div>
                    </div>
                  } @empty {
                    <rip-empty-state title="Sin autores" message="Esta publicación no tiene autores registrados." />
                  }
                </div>
              </div>

              <div class="summary-panel">
                <h3>Temas</h3>
                <div class="chip-list">
                  @for (topic of publication.topics; track topic.id) {
                    <rip-tag-chip [label]="topic.name" />
                  } @empty {
                    <span class="muted">Sin temas asociados</span>
                  }
                </div>
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      }

      @if (showPublicSummaryWorkflow()) {
        <rip-public-summary-workflow
          [targetType]="'PUBLICATION'"
          [targetId]="currentPublication()!.id"
          [targetTitle]="currentPublication()!.title"
          [targetSubtitle]="publicationSubtitle()"
          [currentSummary]="currentPublication()!.publicSummary"
          [ownerResearcherName]="ownerResearcherName()"
          entityLabel="Publicación"
          currentSummaryLabel="Resumen público actual"
          [allowGeneration]="showPublicSummaryWorkflow()"
          [allowReviewExisting]="showPublicSummaryWorkflow()"
          (summaryApplied)="applyAcceptedPublicSummary($event)"
        />
      }

      @if (showEditBlockedMessage()) {
        <mat-card appearance="outlined" class="info-card">
          <mat-card-content>
            <p class="info-title">Edicion no disponible</p>
            <p>{{ editBlockedMessage() }}</p>
          </mat-card-content>
        </mat-card>
      }

      @if (canSave() && !loadError()) {
        <mat-card appearance="outlined" class="form-card" id="publication-form">
          <mat-card-content>
            <form class="form-shell" [formGroup]="publicationForm">
              <section class="form-section">
                <div class="section-heading">
                  <h3>Datos principales</h3>
                  <p>Informacion descriptiva y academica del registro.</p>
                </div>
                <div class="section-grid">
                  <mat-form-field appearance="outline" class="wide-field">
                    <mat-label>Título</mat-label>
                    <input matInput formControlName="title">
                    <mat-hint>Obligatorio</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Tipo</mat-label>
                    <mat-select formControlName="type">
                      @for (type of publicationTypes(); track type) {
                        <mat-option [value]="type">{{ typeLabel(type) }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Estado academico</mat-label>
                    <mat-select formControlName="status">
                      @for (status of publicationStatuses(); track status) {
                        <mat-option [value]="status">{{ statusLabel(status) }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Ano</mat-label>
                    <input matInput type="number" formControlName="year">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Fecha de publicación</mat-label>
                    <input matInput type="date" formControlName="publicationDate">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Idioma</mat-label>
                    <input matInput formControlName="languageCode" placeholder="es, en, fr...">
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="wide-field">
                    <mat-label>Resumen</mat-label>
                    <textarea matInput rows="5" formControlName="abstractText"></textarea>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="wide-field">
                    <mat-label>Resumen público</mat-label>
                    <textarea matInput rows="4" formControlName="publicSummary"></textarea>
                  </mat-form-field>
                </div>
              </section>

              <section class="form-section">
                <div class="section-heading">
                  <h3>Identificadores y canal</h3>
                  <p>DOI, enlaces y metadatos del canal editorial.</p>
                </div>
                <div class="section-grid">
                  <mat-form-field appearance="outline">
                    <mat-label>DOI</mat-label>
                    <input matInput formControlName="doi">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>URL</mat-label>
                    <input matInput formControlName="url">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Fuente</mat-label>
                    <input matInput formControlName="source">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Canal</mat-label>
                    <mat-select formControlName="venueId">
                      <mat-option [value]="null">Sin canal</mat-option>
                      @for (venue of venues(); track venue.id) {
                        <mat-option [value]="venue.id">{{ venue.name }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Editorial</mat-label>
                    <mat-select formControlName="publisherId">
                      <mat-option [value]="null">Sin editorial</mat-option>
                      @for (publisher of publishers(); track publisher.id) {
                        <mat-option [value]="publisher.id">{{ publisherOptionLabel(publisher) }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>ISSN</mat-label>
                    <input matInput formControlName="issn">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>ISBN</mat-label>
                    <input matInput formControlName="isbn">
                  </mat-form-field>
                </div>
              </section>

              <section class="form-section">
                <div class="section-heading">
                  <h3>Autores y entidades</h3>
                  <p>Gestiona autoría interna y externa con orden y correspondencia.</p>
                </div>

                <div class="author-toolbar">
                  <p class="support-copy">Debe existir al menos un autor.</p>
                  <div class="actions">
                    <button mat-button type="button" (click)="addAuthor('internal')">Autor interno</button>
                    <button mat-button type="button" (click)="addAuthor('external')">Autor externo</button>
                  </div>
                </div>

                <div class="author-list" formArrayName="authors">
                  @for (authorGroup of authorControls(); track $index) {
                    <div class="author-card" [formGroupName]="$index">
                      <div class="author-card-head">
                        <div>
                          <h4>Autor {{ $index + 1 }}</h4>
                          <p>Orden {{ $index + 1 }}</p>
                        </div>
                        <div class="author-card-actions">
                          <button mat-button type="button" [disabled]="$index === 0" (click)="moveAuthor($index, -1)">Subir</button>
                          <button mat-button type="button" [disabled]="$index === authorArray.length - 1" (click)="moveAuthor($index, 1)">Bajar</button>
                          <button mat-button type="button" [disabled]="authorArray.length === 1" (click)="removeAuthor($index)">Eliminar</button>
                        </div>
                      </div>

                      <div class="section-grid">
                        <mat-form-field appearance="outline">
                          <mat-label>Tipo de autor</mat-label>
                          <mat-select formControlName="authorKind">
                            <mat-option value="internal">Interno</mat-option>
                            <mat-option value="external">Externo</mat-option>
                          </mat-select>
                        </mat-form-field>

                        @if (authorGroup.controls.authorKind.value === 'internal') {
                          <mat-form-field appearance="outline" class="wide-field">
                            <mat-label>Investigador</mat-label>
                            <mat-select formControlName="researcherId">
                              <mat-option [value]="null">Selecciona un investigador</mat-option>
                              @for (researcher of researchers(); track researcher.id) {
                                <mat-option [value]="researcher.id">{{ researcher.displayName || researcher.fullName }}</mat-option>
                              }
                            </mat-select>
                          </mat-form-field>
                        } @else {
                          <mat-form-field appearance="outline">
                            <mat-label>Autor externo</mat-label>
                            <input matInput formControlName="externalAuthorName">
                          </mat-form-field>

                          <mat-form-field appearance="outline">
                            <mat-label>Afiliación externa</mat-label>
                            <input matInput formControlName="externalAffiliation">
                          </mat-form-field>
                        }

                        <mat-checkbox formControlName="correspondingAuthor">Autor de correspondencia</mat-checkbox>
                      </div>
                    </div>
                  }
                </div>
              </section>

              <section class="form-section">
                <div class="section-heading">
                  <h3>Temas</h3>
                  <p>Introduce los temas separados por coma para que el backend normalice el registro.</p>
                </div>
                <div class="section-grid">
                  <mat-form-field appearance="outline" class="wide-field">
                    <mat-label>Temas</mat-label>
                    <textarea matInput rows="3" formControlName="topicsCsv"></textarea>
                  </mat-form-field>
                </div>

                <rip-topic-recommendation-card
                  title="Sugerencias de temas"
                  subtitle="Revisa propuestas basadas en el titulo y el resumen. Solo se anaden al borrador si las aceptas y el guardado sigue el flujo habitual."
                  [loading]="topicSuggestionLoading()"
                  [requested]="topicSuggestionRequested()"
                  [suggestions]="topicSuggestions()"
                  [appliedTopics]="acceptedTopicSuggestions()"
                  advisoryNote="Estas sugerencias no cambian la publicación por sí solas. Guárdalas cuando quieras conservarlas y después sigue la validación normal."
                  appliedHelper="Los temas aceptados ya están en el borrador actual."
                  (requestSuggestions)="requestTopicSuggestions()"
                  (applySelectedTopics)="acceptTopicSuggestions($event)"
                />
              </section>

              <section class="form-section">
                <div class="section-heading">
                  <h3>Visibilidad y validación</h3>
                  <p>Campos de flujo visibles en solo lectura para decidir si toca editar o reenviar.</p>
                </div>
                <div class="status-grid">
                  <div class="status-panel">
                    <span>Estado de validación</span>
                    <strong>{{ currentPublication() ? validationLabel(currentPublication()!.validationStatus) : 'Borrador' }}</strong>
                    <p>{{ currentPublication() ? validationHelpCopy(currentPublication()!.validationStatus) : 'Se asignará cuando el registro exista.' }}</p>
                  </div>
                  <div class="status-panel">
                    <span>Ultimo envío</span>
                    <strong>{{ formatOptionalDateTime(currentPublication()?.submittedAt, 'Sin envío') }}</strong>
                    <p>{{ currentPublication()?.submittedBy || 'Aún no se ha registrado un envío.' }}</p>
                  </div>
                  <div class="status-panel">
                    <span>Última revisión</span>
                    <strong>{{ formatOptionalDateTime(currentPublication()?.validatedAt, 'Pendiente') }}</strong>
                    <p>{{ currentPublication()?.validatedBy || 'Todavía no hay validación cerrada.' }}</p>
                  </div>
                </div>

                @if (currentPublication()?.validationComment) {
                  <div class="readonly-note" [class.warning-note]="currentPublication()?.validationStatus === 'CHANGES_REQUESTED'">
                    <span>Comentario de validación</span>
                    <p>{{ currentPublication()?.validationComment }}</p>
                  </div>
                }
              </section>
            </form>
          </mat-card-content>
        </mat-card>
      }

      @if (currentPublication() && isAdmin()) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-card-title>Auditoria tecnica</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="metadata-grid">
              <div class="metadata-item">
                <span>Creado por</span>
                <strong>{{ auditUserLabel(currentPublication()!.createdByUserId) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Creado el</span>
                <strong>{{ formatDateTime(currentPublication()!.createdAt) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Modificado por</span>
                <strong>{{ auditUserLabel(currentPublication()!.updatedByUserId) }}</strong>
              </div>
              <div class="metadata-item">
                <span>Modificado el</span>
                <strong>{{ formatDateTime(currentPublication()!.updatedAt) }}</strong>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <rip-audit-history-panel
          [entityType]="'PUBLICATION'"
          [entityId]="currentPublication()!.id"
          title="Historial"
          subtitle="Seguimiento de cambios y transiciones de estado de la publicación."
        />
      }

      @if (publicationId() !== null && !loadError()) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-card-title>Publicaciones relacionadas</mat-card-title>
          </mat-card-header>
          <mat-card-content class="related-section">
            <div class="related-intro">
              <div class="related-intro-copy">
                <p>Se calculan combinando similitud semántica, temas compartidos, autoría y cercanía temporal.</p>
                <rip-visibility-note [message]="relatedVisibilityNote()" />
              </div>

              <form class="related-controls" [formGroup]="relatedControls">
                <mat-form-field appearance="outline">
                  <mat-label>Resultados</mat-label>
                  <input matInput type="number" min="1" max="50" formControlName="limit">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Modo</mat-label>
                  <mat-select formControlName="mode">
                    <mat-option value="STRICT">Estricto</mat-option>
                    <mat-option value="BALANCED">Equilibrado</mat-option>
                    <mat-option value="BROAD">Amplio</mat-option>
                  </mat-select>
                </mat-form-field>
              </form>
            </div>

            @if (relatedLoading()) {
              <p class="muted">Calculando relaciones...</p>
            }

            @if (relatedResponse()?.warnings?.length) {
              <div class="warning-list">
                @for (warning of relatedResponse()?.warnings || []; track warning) {
                  <p>{{ warning }}</p>
                }
              </div>
            }

            <div class="related-list">
              @for (related of relatedResponse()?.relatedPublications || []; track related.publication.id) {
                <article class="related-card">
                  <div class="related-card-header">
                    <div>
                      <a [routerLink]="publicationDetailLink(related.publication.id)" [queryParams]="detailReturnQueryParams()">
                        {{ related.publication.title }}
                      </a>
                      <p class="muted">
                        {{ related.publication.year || 'Sin ano' }} · {{ typeLabel(related.publication.type) }} · {{ statusLabel(related.publication.status) }}
                      </p>
                    </div>
                    <div class="score-stack">
                      <strong>{{ scoreLabel(related.finalScore) }}</strong>
                      <span>score final</span>
                    </div>
                  </div>

                  <div class="score-line">
                    @if (related.semanticScore !== null) {
                      <span>Semántica {{ scoreLabel(related.semanticScore) }}</span>
                    }
                    <span>Metadatos {{ scoreLabel(related.metadataScore) }}</span>
                  </div>

                  @if (related.sharedTopicNames.length) {
                    <div class="chip-list compact">
                      @for (topic of related.sharedTopicNames; track topic) {
                        <rip-tag-chip [label]="topic" />
                      }
                    </div>
                  }

                  @if (related.sharedAuthorNames.length) {
                    <p class="related-meta"><strong>Autores:</strong> {{ related.sharedAuthorNames.join(', ') }}</p>
                  }

                  @if (related.relatedResearchUnitNames.length) {
                    <p class="related-meta"><strong>Unidades:</strong> {{ related.relatedResearchUnitNames.join(', ') }}</p>
                  }

                  <ul class="reason-list">
                    @for (reason of related.explanationReasons; track reason) {
                      <li>{{ reason }}</li>
                    }
                  </ul>

                  @if (related.warning) {
                    <p class="item-warning">{{ related.warning }}</p>
                  }
                </article>
              } @empty {
                @if (!relatedLoading()) {
                  <rip-empty-state
                    title="No se han encontrado publicaciones suficientemente relacionadas."
                    message="Prueba el modo amplio para revisar relaciones mas debiles."
                  />
                }
              }
            </div>
          </mat-card-content>
        </mat-card>
      }
    </section>
  `,
  styles: [`
    .publication-page {
      display: grid;
      gap: 24px;
    }

    .summary-card mat-card-content,
    .form-card mat-card-content,
    .related-section {
      display: grid;
      gap: 22px;
    }

    .summary-head,
    .related-intro,
    .related-card-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
    }

    .summary-copy {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .summary-kicker {
      color: #31566a;
      font-size: 0.76rem;
      font-weight: 780;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .summary-copy h2 {
      margin: 0;
      color: #142033;
      font-size: clamp(1.7rem, 2.8vw, 2.4rem);
      line-height: 1.08;
      overflow-wrap: anywhere;
    }

    .summary-copy p,
    .text-block p,
    .stack-row p,
    .info-card p,
    .readonly-note p {
      margin: 0;
      color: #5f7083;
      line-height: 1.6;
    }

    .summary-chips {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 8px;
    }

    .metadata-grid,
    .status-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
    }

    .metadata-item,
    .status-panel {
      display: grid;
      gap: 8px;
      padding: 16px;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      background: #ffffff;
    }

    .metadata-item span,
    .status-panel span,
    .readonly-note span,
    .banner-label,
    .info-title {
      color: #5f7083;
      font-size: 0.76rem;
      font-weight: 780;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .metadata-item strong,
    .status-panel strong,
    .stack-row strong {
      color: #1f2d3d;
      line-height: 1.45;
      overflow-wrap: anywhere;
    }

    .text-block,
    .summary-panel,
    .form-section {
      display: grid;
      gap: 12px;
    }

    .text-block h3,
    .summary-panel h3,
    .section-heading h3 {
      margin: 0;
      color: #162336;
      font-size: 1rem;
    }

    .summary-columns {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 18px;
    }

    .summary-panel {
      padding: 18px;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      background: #f8fbfd;
    }

    .stack-list,
    .author-list,
    .related-list {
      display: grid;
      gap: 12px;
    }

    .stack-row,
    .author-card,
    .related-card {
      display: grid;
      gap: 12px;
      padding: 16px;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      background: #ffffff;
    }

    .stack-row {
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: start;
    }

    .chip-list,
    .summary-head .chip-list,
    .author-card-actions,
    .summary-chips,
    .score-line {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .compact {
      gap: 6px;
    }

    .validation-banner,
    .readonly-note,
    .info-card mat-card-content,
    .warning-list {
      padding: 16px 18px;
      border: 1px solid #d9e6f2;
      border-radius: 14px;
      background: #f7fbff;
    }

    .validation-banner {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 16px;
      align-items: start;
    }

    .warning-banner {
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

    .banner-copy {
      margin: 0;
      color: #5f7083;
      line-height: 1.55;
    }

    .banner-actions {
      align-items: flex-start;
    }

    .info-title {
      display: block;
      margin-bottom: 8px;
    }

    .form-shell {
      display: grid;
      gap: 28px;
    }

    .form-section {
      padding-top: 4px;
    }

    .form-section + .form-section {
      border-top: 1px solid #e2e8f0;
      padding-top: 24px;
    }

    .section-heading {
      display: grid;
      gap: 6px;
    }

    .section-heading p,
    .support-copy,
    .related-meta,
    .item-warning,
    .score-stack span,
    .score-line {
      margin: 0;
      color: #667487;
      font-size: 0.9rem;
      line-height: 1.5;
    }

    .section-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 14px;
      align-items: start;
    }

    .wide-field {
      grid-column: 1 / -1;
    }

    .author-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }

    .author-card-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
    }

    .author-card-head h4 {
      margin: 0;
      color: #142033;
      font-size: 0.98rem;
    }

    .author-card-head p {
      margin: 4px 0 0;
      color: #667487;
      font-size: 0.86rem;
    }

    .readonly-note.warning-note {
      border-color: #efc27a;
      background: #fff8ea;
    }

    .related-intro-copy {
      display: grid;
      gap: 10px;
      max-width: 760px;
    }

    .related-controls {
      display: grid;
      grid-template-columns: 120px 170px;
      gap: 12px;
      min-width: 302px;
    }

    .related-card-header a {
      color: #172235;
      font-size: 1rem;
      font-weight: 760;
      line-height: 1.35;
      text-decoration: none;
    }

    .related-card-header a:hover {
      color: #2457a6;
      text-decoration: underline;
    }

    .score-stack {
      display: grid;
      justify-items: end;
      min-width: 92px;
      color: #172235;
    }

    .score-stack strong {
      font-size: 1.1rem;
    }

    .reason-list {
      display: grid;
      gap: 4px;
      margin: 0;
      padding-left: 18px;
      color: #475569;
      line-height: 1.45;
    }

    .item-warning {
      color: #72510d;
      font-weight: 700;
    }

    @media (max-width: 960px) {
      .summary-columns {
        grid-template-columns: 1fr;
      }

      .validation-banner,
      .summary-head,
      .related-intro,
      .related-card-header,
      .stack-row,
      .author-card-head {
        display: grid;
      }

      .summary-chips {
        justify-content: flex-start;
      }

      .score-stack {
        justify-items: start;
      }
    }

    @media (max-width: 720px) {
      .related-controls {
        grid-template-columns: 1fr;
        min-width: 0;
      }
    }
  `]
})
export class PublicationDetailPageComponent implements OnInit {
  private readonly publicationsApi = inject(PublicationsApiService);
  private readonly venuesApi = inject(VenuesApiService);
  private readonly publishersApi = inject(PublishersApiService);
  private readonly researchersApi = inject(ResearchersApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly masterData = inject(AcademicMasterDataService);
  private readonly topicRecommendationsApi = inject(TopicRecommendationsApiService);
  private readonly navigationContext = inject(NavigationContextService);
  private readonly dialog = inject(MatDialog);

  readonly publicationTypes = this.masterData.publicationTypeCodes;
  readonly publicationStatuses = this.masterData.publicationStatusCodes;
  readonly publicationId = signal<number | null>(null);
  readonly currentPublication = signal<Publication | null>(null);
  readonly venues = signal<Venue[]>([]);
  readonly publishers = signal<Publisher[]>([]);
  readonly researchers = signal<ResearcherSummary[]>([]);
  readonly relatedResponse = signal<RelatedPublicationsResponse | null>(null);
  readonly saving = signal(false);
  readonly submitting = signal(false);
  readonly loadError = signal('');
  readonly relatedLoading = signal(false);
  readonly topicSuggestions = signal<TopicRecommendation[]>([]);
  readonly topicSuggestionLoading = signal(false);
  readonly topicSuggestionRequested = signal(false);
  readonly acceptedTopicSuggestions = signal<string[]>([]);
  readonly isPortalContext = signal(this.detectPortalContext());
  readonly pageVisibilityNote = computed(() => visibilityNoteForUser(this.auth.currentUser()));
  readonly relatedVisibilityNote = computed(() => visibilityNoteForUser(this.auth.currentUser()));
  readonly masterDataLoading = this.masterData.loading;
  readonly masterDataError = this.masterData.error;

  readonly publicationForm = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(500)] }),
    abstractText: new FormControl(''),
    publicSummary: new FormControl(''),
    year: new FormControl('', { nonNullable: true, validators: [Validators.min(1500), Validators.max(2200)] }),
    publicationDate: new FormControl(''),
    type: new FormControl<PublicationType>('ARTICLE', { nonNullable: true, validators: [Validators.required] }),
    status: new FormControl<PublicationStatus>('PUBLISHED', { nonNullable: true, validators: [Validators.required] }),
    languageCode: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(16)] }),
    doi: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(255)] }),
    url: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
    source: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(255)] }),
    venueId: new FormControl<number | null>(null),
    publisherId: new FormControl<number | null>(null),
    issn: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(32)] }),
    isbn: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(32)] }),
    topicsCsv: new FormControl('', { nonNullable: true }),
    authors: new FormArray<AuthorFormGroup>([])
  });

  readonly relatedControls = new FormGroup({
    limit: new FormControl(6, { nonNullable: true }),
    mode: new FormControl<RelatedMode>('BALANCED', { nonNullable: true })
  });

  readonly authorArray = this.publicationForm.controls.authors;
  private explanationDialogRef: MatDialogRef<PublicationExplanationDialogComponent> | null = null;

  ngOnInit(): void {
    this.masterData.ensureLoaded();
    this.loadLookups();

    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.loadPublication(this.parsePublicationId(params.get('id'))));

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        this.isPortalContext.set(this.detectPortalContext(params));
        if (this.shouldOpenPublicationExplanation(params)) {
          this.openPublicationExplanation(this.readExplanationMode(params));
          return;
        }
        this.closePublicationExplanation();
      });

    this.relatedControls.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadRelated());
  }

  isAdmin(): boolean {
    return this.auth.hasAnyRole(['ADMIN']);
  }

  canSave(): boolean {
    if (this.publicationId() === null) {
      return this.isAdmin();
    }
    return this.currentPublication()?.canEdit ?? false;
  }

  showPublicationExplanationAction(): boolean {
    return this.isPortalContext() && this.publicationId() !== null && !!this.currentPublication() && !this.loadError();
  }

  showPublicSummaryWorkflow(): boolean {
    const publication = this.currentPublication();
    if (!publication) {
      return false;
    }

    return this.isAdmin() || this.isCurrentResearcherAuthor(publication);
  }

  canSubmitCurrent(): boolean {
    return this.currentPublication()?.canSubmit ?? false;
  }

  showEditBlockedMessage(): boolean {
    return this.auth.isAuthenticated()
      && !!this.currentPublication()
      && !this.canSave()
      && !this.loadError()
      && !this.auth.hasAnyRole(['PUBLIC_USER']);
  }

  editBlockedMessage(): string {
    const publication = this.currentPublication();
    if (!publication) {
      return '';
    }
    if (publication.validationStatus === 'PENDING_VALIDATION') {
      return 'La publicación está en revisión institucional. Mientras siga pendiente no admite cambios.';
    }
    if (publication.validationStatus === 'VALIDATED') {
      return 'La publicación ya fue validada. Solo los borradores o registros con cambios solicitados pueden editarse.';
    }
    if (publication.validationStatus === 'REJECTED') {
      return 'La publicación fue rechazada. Revisa el historial para conocer el motivo antes de continuar.';
    }
    return 'No tienes permiso para editar esta publicación en su estado actual.';
  }

  publicationSubtitle(): string {
    const publication = this.currentPublication();
    if (!publication) {
      return 'Registro de publicación';
    }
    return [
      publication.publicationDate,
      publication.year ? String(publication.year) : null,
      this.selectedVenueName() || publication.source
    ].filter(Boolean).join(' · ') || 'Registro de publicación';
  }

  selectedVenueName(): string | null {
    const publication = this.currentPublication();
    const venueId = publication?.venueId ?? this.publicationForm.controls.venueId.value;
    return venueId === null ? null : this.venues().find((venue) => venue.id === venueId)?.name ?? null;
  }

  selectedPublisherName(): string | null {
    const publication = this.currentPublication();
    const publisherId = publication?.publisherId ?? this.publicationForm.controls.publisherId.value;
    return publisherId === null ? null : this.publishers().find((publisher) => publisher.id === publisherId)?.name ?? null;
  }

  validationLabel(status: ValidationStatus): string {
    return validationStatusLabel(status);
  }

  validationTone(status: ValidationStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  validationHeadline(status: ValidationStatus): string {
    switch (status) {
      case 'CHANGES_REQUESTED':
        return 'Hay cambios pendientes antes de reenviar la publicación.';
      case 'PENDING_VALIDATION':
        return 'El registro está en revisión.';
      case 'VALIDATED':
        return 'La publicación ya está validada.';
      case 'REJECTED':
        return 'La publicación fue rechazada.';
      default:
        return 'El registro sigue en borrador.';
    }
  }

  validationHelpCopy(status: ValidationStatus): string {
    switch (status) {
      case 'CHANGES_REQUESTED':
        return 'Actualiza los campos observados y reenvia el registro cuando este completo.';
      case 'PENDING_VALIDATION':
        return 'Puedes revisar la información, pero no editarla mientras dura la revisión.';
      case 'VALIDATED':
        return 'Puedes consultar el historial para entender la validación aplicada.';
      case 'REJECTED':
        return 'Consulta el historial y coordina el siguiente paso con el equipo validador.';
      default:
        return 'Completa la información pendiente antes de enviar a validación.';
    }
  }

  showValidationBanner(): boolean {
    const publication = this.currentPublication();
    return !!publication && (
      publication.validationStatus === 'CHANGES_REQUESTED'
      || !!publication.validationComment
      || publication.validationStatus === 'PENDING_VALIDATION'
    );
  }

  submitButtonLabel(): string {
    return this.currentPublication()?.validationStatus === 'CHANGES_REQUESTED'
      ? 'Reenviar a validación'
      : 'Enviar a validación';
  }

  requestPublicationExplanation(): void {
    if (!this.showPublicationExplanationAction()) {
      return;
    }

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        portalContext: 'true',
        explainPublication: 'true',
        explanationMode: this.modeQueryValue(this.readExplanationMode(this.route.snapshot.queryParamMap))
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  retryMasterData(): void {
    this.masterData.retry();
  }

  authorControls(): AuthorFormGroup[] {
    return this.authorArray.controls;
  }

  addAuthor(kind: AuthorKind): void {
    this.authorArray.push(this.createAuthorGroup(undefined, kind));
  }

  moveAuthor(index: number, direction: -1 | 1): void {
    const target = index + direction;
    if (target < 0 || target >= this.authorArray.length) {
      return;
    }
    const current = this.authorArray.at(index);
    const next = this.authorArray.at(target);
    this.authorArray.setControl(index, next);
    this.authorArray.setControl(target, current);
  }

  removeAuthor(index: number): void {
    if (this.authorArray.length <= 1) {
      return;
    }
    this.authorArray.removeAt(index);
  }

  authorName(author: PublicationAuthor): string {
    return author.researcherName || author.externalAuthorName || 'Autor sin nombre';
  }

  requestTopicSuggestions(): void {
    const request = this.buildTopicSuggestionRequest();
    this.topicSuggestionLoading.set(true);
    this.topicSuggestionRequested.set(true);
    this.acceptedTopicSuggestions.set([]);
    this.topicRecommendationsApi.suggestTopics(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (suggestions) => {
          this.topicSuggestions.set(suggestions);
          this.topicSuggestionLoading.set(false);
        },
        error: () => {
          this.topicSuggestions.set([]);
          this.topicSuggestionLoading.set(false);
        }
      });
  }

  acceptTopicSuggestions(suggestions: TopicRecommendation[]): void {
    const existingTopics = this.parseTopics(this.publicationForm.controls.topicsCsv.value);
    const mergedTopics = this.mergeTopics(existingTopics, suggestions.map((suggestion) => suggestion.topicLabel));
    this.publicationForm.controls.topicsCsv.setValue(mergedTopics.join(', '));
    this.publicationForm.controls.topicsCsv.markAsDirty();
    this.publicationForm.controls.topicsCsv.markAsTouched();
    this.acceptedTopicSuggestions.set(suggestions.map((suggestion) => suggestion.topicLabel));
  }

  save(): void {
    if (!this.canSave() || this.publicationForm.invalid || this.authorArray.invalid || this.authorArray.length === 0 || this.saving()) {
      return;
    }
    const request = this.toPublicationRequest();
    if (request === null) {
      return;
    }
    this.saving.set(true);
    const publicationId = this.publicationId();
    const operation = publicationId === null ? this.publicationsApi.create(request) : this.publicationsApi.update(publicationId, request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (publication) => {
        this.saving.set(false);
        void this.router.navigate(this.publicationDetailLink(publication.id), {
          queryParams: this.preservedQueryParams()
        });
      },
      error: () => this.saving.set(false)
    });
  }

  submit(): void {
    const publication = this.currentPublication();
    if (!publication || !this.canSubmitCurrent() || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.publicationsApi.submit(publication.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.submitting.set(false);
          this.applyPublication(updated);
        },
        error: () => this.submitting.set(false)
      });
  }

  scrollToForm(): void {
    if (typeof document === 'undefined') {
      return;
    }
    document.getElementById('publication-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  typeLabel(type: string | PublicationType): string {
    return this.masterData.publicationTypeLabel(type);
  }

  statusLabel(status: string | PublicationStatus): string {
    return this.masterData.publicationStatusLabel(status);
  }

  statusTone(status: string | PublicationStatus): 'neutral' | 'success' | 'warning' | 'info' {
    return publicationStatusTone(status);
  }

  scoreLabel(score: number): string {
    return `${Math.round(score * 100)}%`;
  }

  publicationDateLabel(publication: Publication): string {
    return publication.publicationDate || (publication.year ? `${publication.year}` : 'Sin fecha');
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

  formatOptionalDateTime(value: string | null | undefined, fallback: string): string {
    return value ? this.formatDateTime(value) : fallback;
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, this.fallbackBackPath(), 'Volver a publicaciones').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, this.fallbackBackPath(), 'Volver a publicaciones');
  }

  detailReturnQueryParams(): Record<string, string> {
    return this.navigationContext.returnQueryParams(this.backLabel());
  }

  publicationDetailLink(publicationId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/publicaciones')
      ? ['/admin/publicaciones', String(publicationId)]
      : this.isPortalContext()
        ? ['/portal/publicaciones', String(publicationId)]
      : ['/publications', String(publicationId)];
  }

  private fallbackBackPath(): string {
    if (this.navigationContext.isCurrentPath('/admin/publicaciones')) {
      return '/admin/publicaciones';
    }
    return this.isPortalContext() ? '/portal/publicaciones' : '/publications';
  }

  private loadLookups(): void {
    this.researchersApi.search({ size: 100, active: true })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        const researchers = [...result.content].sort((left, right) =>
          (left.displayName || left.fullName).localeCompare(right.displayName || right.fullName, 'es')
        );
        this.researchers.set(researchers);
      });

    this.venuesApi.search({ size: 100, active: true })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.venues.set(this.sortByName(result.content)));

    this.publishersApi.search({ size: 200 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => this.publishers.set(this.sortByName(result.content)));
  }

  private loadPublication(publicationId: number | null): void {
    this.publicationId.set(publicationId);
    this.relatedResponse.set(null);
    this.loadError.set('');
    this.resetTopicSuggestionState();

    if (publicationId === null) {
      this.currentPublication.set(null);
      this.initializeNewForm();
      return;
    }

    this.publicationsApi.get(publicationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (publication) => {
          this.applyPublication(publication);
          this.loadRelated();
          this.openPublicationExplanation(this.readExplanationMode(this.route.snapshot.queryParamMap));
        },
        error: (error: unknown) => {
          this.currentPublication.set(null);
          this.relatedResponse.set(null);
          this.loadError.set(contentAccessErrorMessage(error, 'No se pudo cargar la publicación.'));
          this.closePublicationExplanation();
        }
      });
  }

  private applyPublication(publication: Publication): void {
    this.currentPublication.set(publication);
    this.resetTopicSuggestionState();
    this.publicationForm.patchValue({
      title: publication.title,
      abstractText: publication.abstractText ?? '',
      publicSummary: publication.publicSummary ?? '',
      year: publication.year === null ? '' : String(publication.year),
      publicationDate: publication.publicationDate ?? '',
      type: publication.type,
      status: publication.status,
      languageCode: publication.languageCode ?? '',
      doi: publication.doi ?? '',
      url: publication.url ?? '',
      source: publication.source ?? '',
      venueId: publication.venueId,
      publisherId: publication.publisherId,
      issn: publication.issn ?? '',
      isbn: publication.isbn ?? '',
      topicsCsv: publication.topics.map((topic) => topic.name).join(', ')
    });
    this.resetAuthors(publication.authors);
  }

  private initializeNewForm(): void {
    this.resetTopicSuggestionState();
    this.publicationForm.reset({
      title: '',
      abstractText: '',
      publicSummary: '',
      year: '',
      publicationDate: '',
      type: 'ARTICLE',
      status: 'PUBLISHED',
      languageCode: '',
      doi: '',
      url: '',
      source: '',
      venueId: null,
      publisherId: null,
      issn: '',
      isbn: '',
      topicsCsv: ''
    });
    this.resetAuthors([]);
    this.addAuthor('internal');
  }

  private loadRelated(): void {
    const publicationId = this.publicationId();
    if (publicationId === null) {
      return;
    }
    const controls = this.relatedControls.getRawValue();
    this.relatedLoading.set(true);
    this.publicationsApi.related(publicationId, {
      limit: this.clampRelatedLimit(controls.limit),
      minScore: this.minScoreForMode(controls.mode)
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.relatedResponse.set(response);
        this.relatedLoading.set(false);
      },
      error: () => {
        this.relatedResponse.set({
          publicationId,
          limit: controls.limit,
          minScore: this.minScoreForMode(controls.mode),
          metadataOnly: false,
          warnings: ['No se han podido cargar las publicaciones relacionadas.'],
          relatedPublications: []
        });
        this.relatedLoading.set(false);
      }
    });
  }

  private minScoreForMode(mode: RelatedMode): number {
    switch (mode) {
      case 'STRICT':
        return 0.55;
      case 'BROAD':
        return 0.15;
      default:
        return 0.35;
    }
  }

  private clampRelatedLimit(value: number): number {
    return Math.min(Math.max(Number(value) || 6, 1), 50);
  }

  publisherOptionLabel(publisher: Publisher): string {
    return publisher.active ? publisher.name : `${publisher.name} (inactiva)`;
  }

  ownerResearcherName(): string | null {
    const currentResearcherId = this.auth.currentUser()?.researcherId;
    if (currentResearcherId === null) {
      return null;
    }

    const author = this.currentPublication()?.authors.find((item) => item.researcherId === currentResearcherId);
    return author?.researcherName ?? null;
  }

  applyAcceptedPublicSummary(summary: string): void {
    const publication = this.currentPublication();
    if (publication) {
      this.currentPublication.set({
        ...publication,
        publicSummary: summary
      });
    }
    this.publicationForm.controls.publicSummary.setValue(summary);
  }

  private createAuthorGroup(author?: PublicationAuthor, fallbackKind: AuthorKind = 'internal'): AuthorFormGroup {
    const authorKind: AuthorKind = author
      ? (author.researcherId !== null ? 'internal' : 'external')
      : fallbackKind;
    const group: AuthorFormGroup = new FormGroup({
      authorKind: new FormControl<AuthorKind>(authorKind, { nonNullable: true }),
      researcherId: new FormControl<number | null>(author?.researcherId ?? null),
      externalAuthorName: new FormControl(author?.externalAuthorName ?? '', { nonNullable: true }),
      externalAffiliation: new FormControl(author?.externalAffiliation ?? '', { nonNullable: true }),
      correspondingAuthor: new FormControl(author?.correspondingAuthor ?? false, { nonNullable: true })
    });
    this.syncAuthorValidators(group);
    group.controls.authorKind.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.syncAuthorValidators(group));
    return group;
  }

  private syncAuthorValidators(group: AuthorFormGroup): void {
    if (group.controls.authorKind.value === 'internal') {
      group.controls.researcherId.setValidators([Validators.required]);
      group.controls.externalAuthorName.clearValidators();
    } else {
      group.controls.researcherId.clearValidators();
      group.controls.externalAuthorName.setValidators([Validators.required, Validators.maxLength(255)]);
    }
    group.controls.researcherId.updateValueAndValidity({ emitEvent: false });
    group.controls.externalAuthorName.updateValueAndValidity({ emitEvent: false });
  }

  private resetAuthors(authors: PublicationAuthor[]): void {
    while (this.authorArray.length > 0) {
      this.authorArray.removeAt(0);
    }
    authors
      .slice()
      .sort((left, right) => left.authorOrder - right.authorOrder)
      .forEach((author) => this.authorArray.push(this.createAuthorGroup(author)));
  }

  private toPublicationRequest(): PublicationRequest | null {
    const value = this.publicationForm.getRawValue();
    const authors = this.toAuthorRequests();
    if (authors === null || authors.length === 0) {
      return null;
    }
    return {
      title: value.title,
      abstractText: this.emptyToNull(value.abstractText),
      publicSummary: this.emptyToNull(value.publicSummary),
      year: this.toNumberOrNull(value.year),
      publicationDate: this.emptyToNull(value.publicationDate),
      type: value.type,
      status: value.status,
      doi: this.emptyToNull(value.doi),
      source: this.emptyToNull(value.source),
      sourceDetail: this.currentPublication()?.sourceDetail ?? null,
      url: this.emptyToNull(value.url),
      venueId: value.venueId,
      publisherId: value.publisherId,
      isbn: this.emptyToNull(value.isbn),
      issn: this.emptyToNull(value.issn),
      languageCode: this.emptyToNull(value.languageCode),
      authors,
      topics: value.topicsCsv
        .split(',')
        .map((topic) => topic.trim())
        .filter((topic) => topic.length > 0)
    };
  }

  private buildTopicSuggestionRequest(): TopicRecommendationRequest {
    const value = this.publicationForm.getRawValue();
    return {
      entityType: 'PUBLICATION',
      entityId: this.publicationId(),
      title: value.title,
      summary: [value.abstractText, value.publicSummary].filter((item) => !!item).join(' '),
      description: value.source,
      existingTopics: this.parseTopics(value.topicsCsv)
    };
  }

  private resetTopicSuggestionState(): void {
    this.topicSuggestions.set([]);
    this.topicSuggestionLoading.set(false);
    this.topicSuggestionRequested.set(false);
    this.acceptedTopicSuggestions.set([]);
  }

  private mergeTopics(existingTopics: string[], suggestedTopics: string[]): string[] {
    const topics = [...existingTopics];
    const normalizedTopics = new Set(existingTopics.map((topic) => this.normalizeTopic(topic)));
    for (const topic of suggestedTopics) {
      const normalized = this.normalizeTopic(topic);
      if (!normalized || normalizedTopics.has(normalized)) {
        continue;
      }
      normalizedTopics.add(normalized);
      topics.push(topic);
    }
    return topics;
  }

  private parseTopics(value: string): string[] {
    return value
      .split(',')
      .map((topic) => topic.trim())
      .filter((topic) => topic.length > 0);
  }

  private normalizeTopic(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLocaleLowerCase('es-ES')
      .trim();
  }

  private toAuthorRequests(): PublicationAuthorRequest[] | null {
    const requests: PublicationAuthorRequest[] = [];
    for (const [index, group] of this.authorArray.controls.entries()) {
      const value = group.getRawValue();
      if (value.authorKind === 'internal') {
        if (value.researcherId === null) {
          return null;
        }
        requests.push({
          researcherId: value.researcherId,
          externalAuthorName: null,
          externalAffiliation: null,
          authorOrder: index + 1,
          correspondingAuthor: value.correspondingAuthor
        });
        continue;
      }

      const externalAuthorName = this.emptyToNull(value.externalAuthorName);
      if (externalAuthorName === null) {
        return null;
      }
      requests.push({
        researcherId: null,
        externalAuthorName,
        externalAffiliation: this.emptyToNull(value.externalAffiliation),
        authorOrder: index + 1,
        correspondingAuthor: value.correspondingAuthor
      });
    }

    return requests;
  }

  private parsePublicationId(value: string | null): number | null {
    const parsed = value === null ? Number.NaN : Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private preservedQueryParams(): Record<string, string | number> {
    const params = this.route.snapshot.queryParamMap;
    const preserved: Record<string, string | number> = {};
    const returnTo = params.get('returnTo');
    const returnLabel = params.get('returnLabel');
    const focusEntityType = params.get('focusEntityType');
    const focusEntityId = params.get('focusEntityId');
    const portalContext = params.get('portalContext');

    if (returnTo) {
      preserved['returnTo'] = returnTo;
    }
    if (returnLabel) {
      preserved['returnLabel'] = returnLabel;
    }
    if (focusEntityType) {
      preserved['focusEntityType'] = focusEntityType;
    }
    if (focusEntityId) {
      preserved['focusEntityId'] = focusEntityId;
    }
    if (portalContext === 'true') {
      preserved['portalContext'] = 'true';
    }
    return preserved;
  }

  private shouldOpenPublicationExplanation(params = this.route.snapshot.queryParamMap): boolean {
    return this.isPortalContext() && params.get('explainPublication') === 'true';
  }

  private readExplanationMode(params = this.route.snapshot.queryParamMap): PublicationExplanationMode {
    return params.get('explanationMode') === 'tecnico' ? 'TECHNICAL' : 'DIVULGATIVE';
  }

  private openPublicationExplanation(mode: PublicationExplanationMode): void {
    if (!this.showPublicationExplanationAction() || this.explanationDialogRef) {
      return;
    }

    const publication = this.currentPublication();
    if (!publication) {
      return;
    }

    this.explanationDialogRef = this.dialog.open(PublicationExplanationDialogComponent, {
      width: 'min(1024px, 96vw)',
      maxWidth: '96vw',
      maxHeight: '92vh',
      autoFocus: false,
      data: {
        publication,
        publicationLink: this.publicationDetailLink(publication.id),
        preservedQueryParams: this.preservedQueryParams(),
        initialMode: mode
      }
    });

    this.explanationDialogRef.afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.explanationDialogRef = null;
        const activePublication = this.currentPublication();
        if (!activePublication || this.route.snapshot.queryParamMap.get('explainPublication') !== 'true') {
          return;
        }
        const currentPath = this.router.url.split(/[?#]/)[0];
        const expectedPath = this.router.createUrlTree(this.publicationDetailLink(activePublication.id)).toString();
        if (currentPath !== expectedPath) {
          return;
        }
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: {
            explainPublication: null,
            explanationMode: null
          },
          queryParamsHandling: 'merge',
          replaceUrl: true
        });
      });
  }

  private closePublicationExplanation(): void {
    this.explanationDialogRef?.close();
  }

  private modeQueryValue(mode: PublicationExplanationMode): string {
    return mode === 'TECHNICAL' ? 'tecnico' : 'divulgativo';
  }

  private detectPortalContext(params = this.route.snapshot.queryParamMap): boolean {
    const returnTo = params.get('returnTo')?.trim() ?? '';
    return this.route.snapshot.data['portalView'] === true
      || params.get('portalContext') === 'true'
      || this.navigationContext.isCurrentPath('/portal/publicaciones')
      || returnTo.startsWith('/portal');
  }

  private toNumberOrNull(value: string): number | null {
    const parsed = Number(value);
    return Number.isFinite(parsed) && value !== '' ? parsed : null;
  }

  private emptyToNull(value: string | null): string | null {
    const trimmed = (value ?? '').trim();
    return trimmed === '' ? null : trimmed;
  }

  private sortByName<T extends { name: string }>(items: T[]): T[] {
    return [...items].sort((left, right) => left.name.localeCompare(right.name, 'es'));
  }

  private isCurrentResearcherAuthor(publication: Publication): boolean {
    const currentResearcherId = this.auth.currentUser()?.researcherId;
    return currentResearcherId !== null && publication.authors.some((author) => author.researcherId === currentResearcherId);
  }
}
