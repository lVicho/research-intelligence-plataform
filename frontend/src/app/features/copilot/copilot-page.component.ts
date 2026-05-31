import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import {
  CopilotAnswerResponse,
  CopilotCitation,
  CopilotRetrieveResponse,
  CopilotRetrievedPublication,
  RetrievalMode
} from '../../core/api/api-models';
import { CopilotApiService } from '../../core/api/copilot-api.service';
import { PortalDemoQuerySuggestionsService } from '../../core/api/portal-demo-query-suggestions.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { DemoQueryChipsComponent } from '../../shared/components/demo-query-chips.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { VisibilityNoteComponent } from '../../shared/components/visibility-note.component';
import { contentAccessErrorMessage } from '../../shared/utils/api-error-messages';
import { publicVisibilityNote, visibilityNoteFromMetadata } from '../../shared/utils/visibility-labels';
import { AnswerSegment, parseAnswerWithCitations } from './copilot-citation-parser';

type CopilotPhase = 'idle' | 'preparing' | 'retrieving' | 'contextReady' | 'answering' | 'ready' | 'error';
type SupportLevel = 'Alto' | 'Medio' | 'Bajo';

interface SupportEvaluation {
  level: SupportLevel;
  citationsDetected: number;
  warnings: string[];
  unsupportedClaimCount: number;
  unsupportedClaimExamples: string[];
}

const FALLBACK_ASSISTANT_PROMPTS = [
  '¿Qué líneas de investigación aparecen sobre IA clínica?',
  '¿Qué publicaciones conectan salud pública e IA?',
  '¿Qué investigadores conectan varias líneas de investigación?',
  'Compara IA clínica con salud pública',
  '¿Qué publicaciones tratan sobre biodiversidad y corredores ecológicos?'
];

@Component({
  selector: 'rip-copilot-page',
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
    MatProgressSpinnerModule,
    DemoQueryChipsComponent,
    ErrorStateComponent,
    PageHeaderComponent,
    VisibilityNoteComponent
  ],
  template: `
    <section class="page copilot-page">
      <rip-page-header
        title="Asistente de investigación"
        subtitle="Haz preguntas sobre publicaciones, temas y conexiones de investigación. La respuesta enlaza las publicaciones que la respaldan."
        eyebrow="Asistente"
      />

      @if (isPortalView()) {
        <section class="surface-intro assistant-intro">
          <div>
            <p class="section-kicker">Consulta guiada</p>
            <h2>Pregunta primero. Revisa la respuesta y sus publicaciones citadas después.</h2>
            <p>Si quieres profundizar, también podrás abrir el contexto utilizado por el asistente en un panel secundario.</p>
          </div>
          <p class="assistant-note">El asistente responde con información pública revisada por la institución.</p>
        </section>
      } @else {
        <rip-visibility-note [message]="copilotVisibilityNote()" />
      }

      <div class="copilot-grid">
        <main class="main-column">
          <mat-card appearance="outlined" class="question-card">
            <mat-card-header>
              <mat-card-title>Haz tu pregunta</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <form class="ask-form" [formGroup]="form" (ngSubmit)="ask()">
                <mat-form-field appearance="outline">
                  <mat-label>Pregunta</mat-label>
                  <textarea
                    matInput
                    rows="5"
                    formControlName="question"
                    placeholder="Ej. ¿Qué publicaciones conectan salud pública e IA clínica?"
                  ></textarea>
                  <mat-hint>El asistente responderá usando solo la información pública encontrada para esta consulta.</mat-hint>
                </mat-form-field>

                <rip-demo-query-chips
                  title="Consultas sugeridas"
                  [caption]="examplePromptCaption()"
                  [queries]="examplePrompts()"
                  [disabled]="busy()"
                  (querySelected)="usePrompt($event)"
                />

                <details class="advanced-options">
                  <summary>Más opciones de búsqueda</summary>
                  <div class="advanced-grid">
                    <mat-form-field appearance="outline">
                      <mat-label>Número máximo de publicaciones</mat-label>
                      <input matInput type="number" min="1" max="20" formControlName="limit">
                    </mat-form-field>
                    <mat-form-field appearance="outline">
                      <mat-label>Modo de búsqueda</mat-label>
                      <mat-select formControlName="retrievalMode">
                        <mat-option value="STRICT">Más precisa</mat-option>
                        <mat-option value="BALANCED">Equilibrada</mat-option>
                        <mat-option value="BROAD">Más amplia</mat-option>
                      </mat-select>
                    </mat-form-field>
                    <mat-form-field appearance="outline">
                      <mat-label>Coincidencia mínima</mat-label>
                      <input matInput type="number" min="0" max="1" step="0.01" formControlName="minSimilarity">
                    </mat-form-field>
                  </div>
                  @if (canIncludeNonValidated()) {
                    <div class="advanced-toggle-row">
                      <span class="visibility-filter-label">Filtro de validación</span>
                      <mat-checkbox formControlName="includeNonValidated">Incluir datos no validados</mat-checkbox>
                    </div>
                  }
                </details>

                <div class="actions action-bar">
                  <button mat-button type="button" [disabled]="busy()" (click)="clear()">Limpiar</button>
                  <button mat-stroked-button type="button" [disabled]="form.invalid || busy()" (click)="retrieveContext()">
                    Ver fuentes primero
                  </button>
                  <button
                    mat-stroked-button
                    type="button"
                    [disabled]="form.invalid || busy() || !retrieval() || retrieval()!.retrievedPublications.length === 0"
                    (click)="generateAnswer()"
                  >
                    Generar respuesta
                  </button>
                  <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || busy()">Preguntar</button>
                </div>
              </form>
            </mat-card-content>
          </mat-card>

          @if (errorMessage()) {
            <rip-error-state [message]="errorMessage()" />
          }

          <mat-card appearance="outlined" class="answer-card">
            <mat-card-header>
              <mat-card-title>Respuesta</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              @if (answerLoading()) {
                <div class="loading-panel">
                  <mat-spinner diameter="30" />
                  <div>
                    <strong>Preparando la respuesta</strong>
                    <span>El asistente está redactando una respuesta con las publicaciones públicas encontradas.</span>
                  </div>
                </div>
              } @else if (answer()) {
                <div class="answer-content">
                  @for (block of answerBlocks(); track $index) {
                    @if (block.kind === 'heading') {
                      <h3>
                        @for (segment of block.segments ?? []; track $index) {
                          @if (segment.kind === 'citation') {
                            <button
                              type="button"
                              class="citation-chip"
                              [attr.aria-label]="citationAriaLabel(segment)"
                              (mouseenter)="setHighlightedCitation(segment.publicationId)"
                              (mouseleave)="clearHighlightedCitation()"
                              (focus)="setHighlightedCitation(segment.publicationId)"
                              (blur)="clearHighlightedCitation()"
                              (click)="scrollToCitation(segment.publicationId)"
                            >
                              {{ segment.text }}
                            </button>
                          } @else if (segment.kind === 'unknownCitation') {
                            <span class="unknown-citation" title="Cita no disponible">{{ segment.text }}</span>
                          } @else {
                            {{ segment.text }}
                          }
                        }
                      </h3>
                    } @else if (block.kind === 'list') {
                      <ul>
                        @for (item of block.items ?? []; track $index) {
                          <li>
                            @for (segment of item; track $index) {
                              @if (segment.kind === 'citation') {
                                <button
                                  type="button"
                                  class="citation-chip"
                                  [attr.aria-label]="citationAriaLabel(segment)"
                                  (mouseenter)="setHighlightedCitation(segment.publicationId)"
                                  (mouseleave)="clearHighlightedCitation()"
                                  (focus)="setHighlightedCitation(segment.publicationId)"
                                  (blur)="clearHighlightedCitation()"
                                  (click)="scrollToCitation(segment.publicationId)"
                                >
                                  {{ segment.text }}
                                </button>
                              } @else if (segment.kind === 'unknownCitation') {
                                <span class="unknown-citation" title="Cita no disponible">{{ segment.text }}</span>
                              } @else {
                                {{ segment.text }}
                              }
                            }
                          </li>
                        }
                      </ul>
                    } @else {
                      <p>
                        @for (segment of block.segments ?? []; track $index) {
                          @if (segment.kind === 'citation') {
                            <button
                              type="button"
                              class="citation-chip"
                              [attr.aria-label]="citationAriaLabel(segment)"
                              (mouseenter)="setHighlightedCitation(segment.publicationId)"
                              (mouseleave)="clearHighlightedCitation()"
                              (focus)="setHighlightedCitation(segment.publicationId)"
                              (blur)="clearHighlightedCitation()"
                              (click)="scrollToCitation(segment.publicationId)"
                            >
                              {{ segment.text }}
                            </button>
                          } @else if (segment.kind === 'unknownCitation') {
                            <span class="unknown-citation" title="Cita no disponible">{{ segment.text }}</span>
                          } @else {
                            {{ segment.text }}
                          }
                        }
                      </p>
                    }
                  }
                </div>
              } @else {
                <div class="empty-state">{{ answerPlaceholderMessage() }}</div>
              }
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined" class="evidence-card">
            <mat-card-header>
              <mat-card-title>Publicaciones citadas</mat-card-title>
              <mat-card-subtitle>Solo aparecen las publicaciones mencionadas explícitamente en la respuesta.</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              @if (answer()?.citedPublications?.length) {
                <div class="publication-list cited-publication-list">
                  @for (publication of answer()!.citedPublications; track publication.id) {
                    <article
                      class="publication-card evidence"
                      [id]="citedPublicationCardId(publication.id)"
                      [class.highlighted]="highlightedCitationId() === publication.id"
                      tabindex="-1"
                    >
                      <div class="publication-main citation-publication-main">
                        <div class="publication-title-block">
                          <div class="citation-meta">
                            <span class="citation-card-index">[{{ publication.citationIndex }}]</span>
                            <span class="publication-year">{{ publication.year || 's. f.' }}</span>
                          </div>
                          <strong>{{ publication.title }}</strong>
                        </div>
                      </div>
                      <p>{{ authorLine(publication) }}</p>
                      <div class="meta-row">
                        @if (publication.doi) {
                          <span>DOI {{ publication.doi }}</span>
                        }
                        @if (publication.source) {
                          <span>{{ publication.source }}</span>
                        }
                      </div>
                      @if (publication.topics.length > 0) {
                        <div class="chip-list">
                          @for (topic of publication.topics.slice(0, 3); track topic) {
                            <span class="topic-chip">{{ topic }}</span>
                          }
                        </div>
                      }
                      <div class="citation-card-actions">
                        <a mat-stroked-button [routerLink]="publicationLink(publication.id)" [queryParams]="navigationContext.returnQueryParams('Volver al asistente')">Ver publicación</a>
                      </div>
                    </article>
                  }
                </div>
              } @else {
                <div class="empty-state">Cuando la respuesta cite publicaciones, aparecerán aquí para que puedas revisarlas.</div>
              }
            </mat-card-content>
          </mat-card>
        </main>

        <aside class="side-column">
          <mat-card appearance="outlined" class="status-card">
            <mat-card-header>
              <mat-card-title>Estado de la consulta</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="status-overview">
                <div class="status-pill-row">
                  @if (busy()) {
                    <mat-spinner diameter="18" />
                  }
                  <span
                    class="status-pill"
                    [class.ready]="phase() === 'ready'"
                    [class.active]="busy()"
                    [class.error]="phase() === 'error'"
                  >
                    {{ phaseLabel() }}
                  </span>
                </div>
                <p>{{ phaseDescription() }}</p>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined" class="context-card">
            <mat-card-header>
              <mat-card-title>Resumen de la búsqueda</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              @if (retrievalLoading()) {
                <div class="loading-panel compact-panel">
                  <mat-spinner diameter="24" />
                  <div>
                    <strong>Buscando publicaciones relacionadas</strong>
                    <span>Estamos reuniendo las fuentes públicas más útiles para responder.</span>
                  </div>
                </div>
              } @else {
                @if (retrieval(); as context) {
                  <div class="summary-grid">
                    <div>
                      <span>Fuentes encontradas</span>
                      <strong>{{ contextPublications().length }}</strong>
                    </div>
                    <div>
                      <span>Publicaciones citadas</span>
                      <strong>{{ answer()?.citedPublications?.length ?? 0 }}</strong>
                    </div>
                    <div>
                      <span>Tipo de búsqueda</span>
                      <strong>{{ retrievalLabel(context.retrievalMethod) }}</strong>
                    </div>
                    <div>
                      <span>Modo</span>
                      <strong>{{ retrievalModeLabel(context.retrievalMode) }}</strong>
                    </div>
                  </div>

                  @if (context.detectedTopics.length > 0) {
                    <div class="context-signals">
                      <p class="signal-title">Temas relacionados</p>
                      <div class="chip-list">
                        @for (topic of context.detectedTopics.slice(0, 6); track topic.name) {
                          <span class="topic-chip strong">{{ topic.name }} <small>{{ topic.count }}</small></span>
                        }
                      </div>
                    </div>
                  }

                  @if (context.bridgingAuthors.length > 0) {
                    <div class="context-signals">
                      <p class="signal-title">Autores recurrentes</p>
                      <div class="chip-list">
                        @for (author of context.bridgingAuthors.slice(0, 6); track author.name) {
                          <span class="author-chip">{{ author.name }} <small>{{ author.count }}</small></span>
                        }
                      </div>
                    </div>
                  }

                  @if (allWarnings().length > 0) {
                    <div class="warning-list note-list">
                      @for (warning of allWarnings(); track warning) {
                        <span>{{ warning }}</span>
                      }
                    </div>
                  }
                } @else {
                  <div class="empty-state compact">Aquí verás cuántas fuentes se han encontrado y qué temas aparecen alrededor de tu pregunta.</div>
                }
              }
            </mat-card-content>
          </mat-card>

          <mat-card appearance="outlined" class="context-card full-context-card">
            <mat-card-header>
              <div class="full-context-header">
                <div>
                  <mat-card-title>Contexto utilizado</mat-card-title>
                  <mat-card-subtitle>{{ retrievedContextCountLabel() }}</mat-card-subtitle>
                </div>
                <button
                  mat-stroked-button
                  type="button"
                  [disabled]="contextPublications().length === 0"
                  (click)="toggleFullContext()"
                >
                  {{ fullContextOpen() ? 'Ocultar contexto' : 'Ver contexto utilizado' }}
                </button>
              </div>
            </mat-card-header>
            <mat-card-content>
              @if (fullContextOpen()) {
                <p class="context-note">Aquí aparecen las publicaciones que ayudaron a construir la respuesta, aunque no todas queden citadas de forma explícita.</p>
                @if (retrievalLoading()) {
                  <div class="skeleton-stack publications">
                    <span></span>
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                } @else if (contextPublications().length > 0) {
                  <div class="context-publication-list">
                    @for (publication of contextPublications(); track publication.id) {
                      <a class="context-publication-row" [routerLink]="publicationLink(publication.id)" [queryParams]="navigationContext.returnQueryParams('Volver al asistente')">
                        <div class="context-publication-main">
                          <strong>{{ publication.title }}</strong>
                          @if (isCitedPublication(publication.id)) {
                            <span class="cited-badge">Citada [{{ citedCitationIndex(publication.id) }}]</span>
                          }
                        </div>
                        <p>{{ authorLine(publication) }}</p>
                        <div class="meta-row">
                          <span>{{ publication.year || 's. f.' }}</span>
                          @if (publication.source) {
                            <span>{{ publication.source }}</span>
                          }
                          @if (publication.similarityScore !== null) {
                            <span [class.low-score]="publication.lowSimilarity">Coincidencia {{ similarityPercent(publication.similarityScore) }}</span>
                          }
                        </div>
                      </a>
                    }
                  </div>
                } @else {
                  <div class="empty-state compact">No se encontraron publicaciones suficientemente relacionadas.</div>
                }
              } @else {
                <p class="context-note">El detalle del contexto queda en segundo plano para mantener la respuesta y sus citas en el centro.</p>
              }
            </mat-card-content>
          </mat-card>

          @if (supportEvaluation(); as evaluation) {
            <mat-card appearance="outlined" class="support-card" [class.low-support]="evaluation.level === 'Bajo'">
              <mat-card-header>
                <mat-card-title>Soporte de la respuesta</mat-card-title>
                <mat-card-subtitle>Estimación automática basada en las publicaciones citadas y en el texto generado.</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <div class="support-evaluation-header">
                  <p>Esta ayuda orienta la revisión, pero no sustituye una lectura humana de las fuentes.</p>
                  <span class="support-level" [class.high]="evaluation.level === 'Alto'" [class.medium]="evaluation.level === 'Medio'" [class.low]="evaluation.level === 'Bajo'">
                    {{ evaluation.level }}
                  </span>
                </div>

                <div class="summary-grid support-summary-grid">
                  <div>
                    <span>Citas detectadas</span>
                    <strong>{{ evaluation.citationsDetected }}</strong>
                  </div>
                  <div>
                    <span>Observaciones</span>
                    <strong>{{ evaluation.warnings.length }}</strong>
                  </div>
                  <div>
                    <span>Frases a revisar</span>
                    <strong>{{ evaluation.unsupportedClaimCount }}</strong>
                  </div>
                </div>

                @if (evaluation.warnings.length > 0) {
                  <div class="warning-list note-list">
                    @for (warning of evaluation.warnings; track warning) {
                      <span>{{ warning }}</span>
                    }
                  </div>
                }

                @if (evaluation.unsupportedClaimExamples.length > 0) {
                  <div class="warning-list note-list">
                    @for (claim of evaluation.unsupportedClaimExamples; track claim) {
                      <span>{{ claim }}</span>
                    }
                  </div>
                }
              </mat-card-content>
            </mat-card>
          }

          @if (!isPortalView() && retrieval(); as context) {
            <mat-card appearance="outlined" class="technical-card">
              <mat-card-content>
                <details class="technical-details">
                  <summary>Detalles técnicos</summary>
                  <div class="summary-grid technical-grid">
                    <div>
                      <span>Visibilidad</span>
                      <strong>{{ visibilityNoteFromMetadata(context.visibilityScope, context.validationFilterApplied) }}</strong>
                    </div>
                    <div>
                      <span>Embeddings</span>
                      <strong>{{ context.embeddingProvider }} / {{ context.embeddingModel }}</strong>
                    </div>
                    <div>
                      <span>Modelo LLM</span>
                      <strong>{{ context.provider }} / {{ context.model }}</strong>
                    </div>
                    <div>
                      <span>Coincidencia mínima</span>
                      <strong>{{ context.minSimilarity }}</strong>
                    </div>
                  </div>
                </details>
              </mat-card-content>
            </mat-card>
          }
        </aside>
      </div>
    </section>
  `,
  styles: [`
    .assistant-intro,
    .copilot-grid,
    .main-column,
    .side-column,
    .ask-form,
    .publication-list,
    .context-signals,
    .warning-list,
    .context-publication-list,
    .status-overview,
    .technical-details {
      display: grid;
    }

    .assistant-intro {
      grid-template-columns: minmax(0, 1fr) minmax(260px, 320px);
      gap: 20px;
    }

    .assistant-intro h2 {
      margin: 0 0 10px;
      color: #15263a;
      font-size: clamp(1.45rem, 2.4vw, 1.9rem);
      line-height: 1.2;
    }

    .assistant-intro p,
    .assistant-note,
    .context-note,
    .signal-title,
    .status-overview p,
    .support-evaluation-header p {
      margin: 0;
      color: #566679;
      line-height: 1.6;
    }

    .assistant-note {
      padding: 16px 18px;
      border: 1px solid #dbe6ec;
      border-radius: 12px;
      background: #f8fbfc;
      font-size: 0.95rem;
    }

    .copilot-grid {
      grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
      gap: 28px;
    }

    .main-column,
    .side-column {
      gap: 20px;
    }

    mat-card.mat-mdc-card {
      border-radius: 14px !important;
      overflow: hidden;
    }

    mat-card-content {
      padding: 0 20px 20px;
    }

    .ask-form,
    .status-overview {
      gap: 18px;
    }

    textarea {
      resize: vertical;
      line-height: 1.5;
    }

    .prompt-chips,
    .chip-list,
    .meta-row,
    .action-bar,
    .citation-meta,
    .status-pill-row,
    .context-publication-main,
    .support-evaluation-header,
    .full-context-header {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }

    .prompt-chips,
    .chip-list {
      gap: 7px;
    }

    .action-bar {
      justify-content: flex-start;
    }

    .prompt-chip,
    .status-pill,
    .support-level,
    .topic-chip,
    .author-chip,
    .cited-badge {
      border: 1px solid #d7e0ea;
      border-radius: 999px;
      background: #ffffff;
    }

    .prompt-chip {
      max-width: 100%;
      padding: 7px 11px;
      color: #324155;
      cursor: pointer;
      font: inherit;
      font-size: 0.86rem;
      line-height: 1.25;
      text-align: left;
    }

    .advanced-options {
      border: 1px solid #e0e7ee;
      border-radius: 12px;
      background: #fbfcfe;
    }

    .advanced-options summary {
      padding: 12px 14px;
      color: #324155;
      cursor: pointer;
      font-weight: 700;
    }

    .advanced-grid,
    .summary-grid {
      display: grid;
      gap: 10px;
    }

    .advanced-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
      padding: 0 14px 14px;
    }

    .advanced-toggle-row {
      display: grid;
      gap: 6px;
      padding: 0 14px 14px;
    }

    .visibility-filter-label {
      color: #667487;
      font-size: 0.8rem;
      font-weight: 760;
      text-transform: uppercase;
    }

    .loading-panel,
    .empty-state {
      display: flex;
      align-items: center;
      gap: 12px;
      min-height: 88px;
      padding: 18px;
      border: 1px dashed #cfd9e4;
      border-radius: 12px;
      background: #f8fafc;
      color: #5d6b7c;
      line-height: 1.5;
    }

    .loading-panel > div {
      display: grid;
      gap: 4px;
    }

    .loading-panel strong {
      color: #233044;
      font-size: 0.96rem;
    }

    .compact-panel,
    .empty-state.compact {
      min-height: auto;
      padding: 12px;
      font-size: 0.9rem;
    }

    .answer-content {
      display: grid;
      gap: 12px;
      color: #1f2a3d;
      line-height: 1.7;
    }

    .answer-content h3 {
      margin: 10px 0 0;
      color: #172235;
      font-size: 1.02rem;
      font-weight: 760;
    }

    .answer-content p,
    .answer-content ul {
      margin: 0;
    }

    .answer-content ul {
      padding-left: 22px;
    }

    .citation-chip,
    .citation-card-index,
    .publication-year,
    .unknown-citation {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 999px;
    }

    .citation-chip {
      min-width: 28px;
      height: 24px;
      margin: 0 3px;
      padding: 0 7px;
      border: 1px solid #a9c7d6;
      background: #f4fafc;
      color: #17536d;
      cursor: pointer;
      font: inherit;
      font-size: 0.8rem;
      font-weight: 760;
      line-height: 1;
    }

    .unknown-citation {
      margin: 0 3px;
      padding: 2px 7px;
      border: 1px dashed #d8c79d;
      background: #fffaf0;
      color: #80621b;
      font-size: 0.76rem;
      font-weight: 700;
    }

    .publication-list,
    .warning-list,
    .context-publication-list {
      gap: 12px;
    }

    .context-signals,
    .technical-details {
      gap: 10px;
    }

    .publication-card,
    .context-publication-row {
      display: grid;
      gap: 10px;
      padding: 14px 16px;
      border: 1px solid #e0e7ee;
      border-radius: 12px;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
    }

    .publication-card.evidence {
      border-left: 4px solid #2f7f61;
    }

    .publication-card.evidence.highlighted {
      border-color: #2f7f61;
      background: #f3fbf6;
    }

    .context-publication-row {
      padding: 12px 14px;
      background: #fbfcfe;
    }

    .publication-main,
    .publication-title-block {
      display: grid;
      gap: 8px;
    }

    .citation-publication-main {
      grid-template-columns: 1fr;
    }

    .publication-title-block strong,
    .context-publication-main strong {
      color: #172235;
      font-size: 0.96rem;
      line-height: 1.4;
      overflow-wrap: anywhere;
    }

    .context-publication-main,
    .support-evaluation-header,
    .full-context-header {
      justify-content: space-between;
      align-items: flex-start;
    }

    .publication-year {
      padding: 3px 8px;
      background: #eef5f8;
      color: #31566a;
      font-size: 0.78rem;
      font-weight: 760;
    }

    .citation-card-index {
      min-width: 42px;
      height: 30px;
      background: #e8f5ee;
      color: #256348;
      font-size: 0.86rem;
      font-weight: 800;
    }

    .citation-card-actions {
      display: flex;
    }

    .publication-card p,
    .context-publication-row p {
      margin: 0;
      color: #5f6c7d;
      font-size: 0.88rem;
      line-height: 1.45;
    }

    .meta-row {
      color: #6a7686;
      font-size: 0.8rem;
      line-height: 1.35;
    }

    .status-pill,
    .support-level {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-height: 30px;
      padding: 0 12px;
      font-size: 0.82rem;
      font-weight: 780;
      white-space: nowrap;
    }

    .status-pill {
      color: #4f5f73;
    }

    .status-pill.active {
      border-color: #c9ddec;
      background: #eef7fb;
      color: #17536d;
    }

    .status-pill.ready,
    .support-level.high,
    .topic-chip.strong,
    .cited-badge {
      border-color: #b9d9ca;
      background: #f3fbf6;
      color: #256348;
    }

    .status-pill.error {
      border-color: #e4c5c5;
      background: #fff5f5;
      color: #9b3d3d;
    }

    .summary-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .summary-grid > div {
      display: grid;
      gap: 4px;
      padding: 11px 12px;
      border: 1px solid #e0e7ee;
      border-radius: 10px;
      background: #fbfcfe;
    }

    .summary-grid span {
      color: #667487;
      font-size: 0.75rem;
      font-weight: 720;
      text-transform: uppercase;
    }

    .summary-grid strong {
      color: #233044;
      font-size: 0.92rem;
      overflow-wrap: anywhere;
    }

    .signal-title,
    .context-note {
      font-size: 0.88rem;
    }

    .signal-title {
      font-weight: 720;
    }

    .warning-list span {
      padding: 9px 10px;
      border: 1px solid #e1e8ef;
      border-radius: 8px;
      background: #fbfcfe;
      color: #47596c;
      font-size: 0.88rem;
      line-height: 1.4;
    }

    .topic-chip,
    .author-chip,
    .cited-badge {
      gap: 6px;
      max-width: 100%;
      padding: 5px 9px;
      color: #29364a;
      font-size: 0.82rem;
      line-height: 1.25;
    }

    .author-chip {
      border-color: #dccfb9;
      background: #fffaf0;
      color: #5c451d;
    }

    .support-card.low-support {
      border-color: #ecd5a0;
      background: #fffdfa;
    }

    .support-level.medium {
      background: #f8fafc;
      color: #4f5f73;
    }

    .support-level.low,
    .support-card.low-support .warning-list span {
      border-color: #efd18b;
      background: #fff9e9;
      color: #72510d;
    }

    .support-summary-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .technical-details summary {
      cursor: pointer;
      color: #324155;
      font-weight: 700;
    }

    .technical-grid {
      margin-top: 14px;
    }

    .low-score {
      color: #8a5d0a;
      font-weight: 700;
    }

    .skeleton-stack {
      display: grid;
      gap: 10px;
    }

    .skeleton-stack span {
      display: block;
      height: 16px;
      border-radius: 999px;
      background: linear-gradient(90deg, #edf2f6, #f8fafc, #edf2f6);
      background-size: 220% 100%;
      animation: pulse 1.2s ease-in-out infinite;
    }

    .skeleton-stack.publications span {
      height: 52px;
      border-radius: 8px;
    }

    @keyframes pulse {
      0% {
        background-position: 0 0;
      }
      100% {
        background-position: -220% 0;
      }
    }

    @media (max-width: 1100px) {
      .assistant-intro,
      .copilot-grid {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 720px) {
      .advanced-grid,
      .summary-grid,
      .support-summary-grid {
        grid-template-columns: 1fr;
      }

      .context-publication-main,
      .full-context-header,
      .support-evaluation-header {
        flex-direction: column;
      }
    }

    @media (max-width: 620px) {
      .copilot-page {
        gap: 22px;
      }

      .action-bar {
        align-items: stretch;
      }

      .action-bar button,
      .full-context-header button {
        width: 100%;
      }
    }
  `]
})
export class CopilotPageComponent implements OnInit {
  private readonly api = inject(CopilotApiService);
  private readonly auth = inject(AuthStateService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly demoQuerySuggestions = inject(PortalDemoQuerySuggestionsService);
  readonly navigationContext = inject(NavigationContextService);
  private highlightTimer: ReturnType<typeof setTimeout> | null = null;
  readonly visibilityNoteFromMetadata = visibilityNoteFromMetadata;

  readonly isPortalView = signal(this.route.snapshot.data['portalView'] === true);
  readonly phase = signal<CopilotPhase>('idle');
  readonly retrieval = signal<CopilotRetrieveResponse | null>(null);
  readonly answer = signal<CopilotAnswerResponse | null>(null);
  readonly errorMessage = signal('');
  readonly highlightedCitationId = signal<number | null>(null);
  readonly fullContextOpen = signal(false);

  readonly examplePrompts = signal<string[]>(FALLBACK_ASSISTANT_PROMPTS);
  readonly examplePromptsDynamic = signal(false);
  readonly examplePromptCaption = computed(() => this.examplePromptsDynamic() ? 'Inspiradas en los datos del portal' : '');

  readonly form = new FormGroup({
    question: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3)] }),
    limit: new FormControl(10, { nonNullable: true, validators: [Validators.min(1), Validators.max(20)] }),
    retrievalMode: new FormControl<RetrievalMode>('BALANCED', { nonNullable: true }),
    minSimilarity: new FormControl<number | null>(null, { validators: [Validators.min(0), Validators.max(1)] }),
    includeNonValidated: new FormControl(false, { nonNullable: true })
  });

  readonly retrievalLoading = computed(() => this.phase() === 'preparing' || this.phase() === 'retrieving');
  readonly answerLoading = computed(() => this.phase() === 'answering');
  readonly busy = computed(() => this.retrievalLoading() || this.answerLoading());
  readonly canIncludeNonValidated = computed(() => this.auth.hasAnyRole(['ADMIN']) && !this.isPortalView());
  readonly contextPublications = computed(() => this.answer()?.retrievedPublications ?? this.retrieval()?.retrievedPublications ?? []);
  readonly copilotVisibilityNote = computed(() => {
    if (this.isPortalView()) {
      return publicVisibilityNote();
    }
    const metadata = this.answer() ?? this.retrieval();
    return metadata
      ? visibilityNoteFromMetadata(metadata.visibilityScope, metadata.validationFilterApplied)
      : this.canIncludeNonValidated() && this.form.controls.includeNonValidated.value
        ? 'Incluye datos no validados'
        : publicVisibilityNote();
  });
  readonly allWarnings = computed(() => [
    ...(this.retrieval()?.warnings ?? []),
    ...(this.answer()?.warnings ?? [])
  ].filter((warning, index, warnings) => warnings.indexOf(warning) === index));
  readonly answerBlocks = computed(() => parseAnswerWithCitations(
    this.answer()?.answer ?? '',
    this.answer()?.citedPublications ?? []
  ));

  ngOnInit(): void {
    this.loadDemoQueries();
  }

  publicationLink(publicationId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/asistente')
      ? ['/admin/publicaciones', String(publicationId)]
      : ['/publications', String(publicationId)];
  }

  readonly supportEvaluation = computed<SupportEvaluation | null>(() => {
    const answer = this.answer();
    if (!answer) {
      return null;
    }

    const blocks = this.answerBlocks();
    const citationsDetected = answer.citedPublications.length;
    const unknownCitationCount = this.countUnknownCitations(blocks);
    const unsupportedClaimExamples = this.collectUnsupportedClaimExamples(blocks);
    const unsupportedClaimCount = unsupportedClaimExamples.length;
    const warnings = [
      ...this.allWarnings(),
      ...(unknownCitationCount > 0 ? ['Se detectaron referencias en el texto que no se pudieron resolver como citas visibles.'] : [])
    ].filter((warning, index, items) => items.indexOf(warning) === index);

    let level: SupportLevel = 'Bajo';
    if (citationsDetected >= 3 && warnings.length === 0 && unsupportedClaimCount === 0) {
      level = 'Alto';
    } else if (citationsDetected >= 1 && warnings.length <= 1 && unsupportedClaimCount <= 2) {
      level = 'Medio';
    }

    return {
      level,
      citationsDetected,
      warnings,
      unsupportedClaimCount,
      unsupportedClaimExamples
    };
  });

  private loadDemoQueries(): void {
    this.demoQuerySuggestions.loadSuggestions({
      context: 'ASSISTANT',
      fallbackQueries: FALLBACK_ASSISTANT_PROMPTS
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.examplePrompts.set(result.queries);
        this.examplePromptsDynamic.set(result.dynamic);
      });
  }

  ask(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.resetRun();
    this.retrieveContext(true);
  }

  retrieveContext(generateAfterRetrieval = false): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.errorMessage.set('');
    this.answer.set(null);
    this.phase.set('preparing');
    this.phase.set('retrieving');
    const value = this.form.getRawValue();
    this.api.retrieve({
      question: value.question,
      limit: value.limit,
      retrievalMode: value.retrievalMode,
      minSimilarity: value.minSimilarity,
      includeNonValidated: this.canIncludeNonValidated() ? value.includeNonValidated : false
    }).subscribe({
      next: (response) => {
        this.retrieval.set(response);
        this.fullContextOpen.set(false);
        this.phase.set('contextReady');
        if (response.retrievedPublications.length === 0) {
          this.errorMessage.set('No se encontraron publicaciones suficientemente relacionadas.');
          return;
        }
        if (generateAfterRetrieval) {
          this.generateAnswer();
        }
      },
      error: (error: unknown) => {
        this.phase.set('error');
        this.errorMessage.set(contentAccessErrorMessage(error, this.toErrorMessage(error, 'No se pudo recuperar el contexto.')));
      }
    });
  }

  generateAnswer(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    const context = this.retrieval();
    if (!context || context.retrievedPublications.length === 0) {
      this.errorMessage.set('No se encontraron publicaciones suficientemente relacionadas.');
      return;
    }
    this.errorMessage.set('');
    this.answer.set(null);
    this.phase.set('answering');
    this.api.answer({
      question: this.form.controls.question.value,
      retrievedPublications: context.retrievedPublications,
      includeNonValidated: this.canIncludeNonValidated() ? this.form.controls.includeNonValidated.value : false
    }).subscribe({
      next: (response) => {
        this.answer.set(response);
        this.fullContextOpen.set(false);
        this.phase.set('ready');
      },
      error: (error: unknown) => {
        this.phase.set('contextReady');
        this.errorMessage.set(contentAccessErrorMessage(error, this.toErrorMessage(error, 'No se pudo generar la respuesta.')));
      }
    });
  }

  clear(): void {
    this.form.reset({ question: '', limit: 10, retrievalMode: 'BALANCED', minSimilarity: null, includeNonValidated: false });
    this.retrieval.set(null);
    this.answer.set(null);
    this.errorMessage.set('');
    this.clearHighlightTimer();
    this.highlightedCitationId.set(null);
    this.fullContextOpen.set(false);
    this.phase.set('idle');
  }

  usePrompt(prompt: string): void {
    this.form.controls.question.setValue(prompt);
    this.form.controls.question.markAsDirty();
  }

  answerPlaceholderMessage(): string {
    if (this.phase() === 'contextReady' && this.contextPublications().length > 0) {
      return 'Ya hemos reunido publicaciones relacionadas. Cuando quieras, genera la respuesta.';
    }
    if (this.phase() === 'error') {
      return 'Revisa la consulta o inténtalo de nuevo.';
    }
    return 'Escribe una pregunta y el asistente preparará una respuesta con publicaciones públicas revisadas.';
  }

  phaseLabel(): string {
    switch (this.phase()) {
      case 'preparing':
        return 'Preparando';
      case 'retrieving':
        return 'Buscando fuentes';
      case 'contextReady':
        return 'Fuentes listas';
      case 'answering':
        return 'Redactando';
      case 'ready':
        return 'Respuesta lista';
      case 'error':
        return 'Revisar consulta';
      default:
        return 'Esperando pregunta';
    }
  }

  phaseDescription(): string {
    switch (this.phase()) {
      case 'preparing':
        return 'Estamos preparando la consulta para buscar la información más relevante.';
      case 'retrieving':
        return 'El asistente está reuniendo publicaciones públicas relacionadas con tu pregunta.';
      case 'contextReady':
        return 'Ya hay fuentes preparadas. Puedes revisar el contexto o pedir la respuesta.';
      case 'answering':
        return 'Ahora se está redactando una respuesta a partir de las publicaciones encontradas.';
      case 'ready':
        return 'La respuesta ya está lista y las publicaciones citadas aparecen justo debajo.';
      case 'error':
        return 'No pudimos completar la consulta. Ajusta la pregunta o inténtalo otra vez.';
      default:
        return 'Cuando envíes una pregunta, aquí verás cómo avanza la consulta.';
    }
  }

  retrievalLabel(method: string): string {
    switch (method) {
      case 'SEMANTIC':
        return 'Búsqueda semántica';
      case 'MOCK':
        return 'Modo de demostración';
      case 'TEXT':
        return 'Búsqueda por texto';
      default:
        return method;
    }
  }

  retrievalModeLabel(mode: RetrievalMode): string {
    switch (mode) {
      case 'STRICT':
        return 'Más precisa';
      case 'BROAD':
        return 'Más amplia';
      case 'BALANCED':
        return 'Equilibrada';
    }
  }

  similarityPercent(score: number): string {
    return `${Math.round(score * 100)}%`;
  }

  authorLine(publication: CopilotRetrievedPublication | CopilotCitation): string {
    return publication.authors.length > 0 ? publication.authors.join(', ') : 'Autores desconocidos';
  }

  retrievedContextCountLabel(): string {
    const count = this.contextPublications().length;
    return count === 1 ? '1 publicación recuperada' : `${count} publicaciones recuperadas`;
  }

  toggleFullContext(): void {
    this.fullContextOpen.update((open) => !open);
  }

  isCitedPublication(publicationId: number): boolean {
    return this.answer()?.citedPublications.some((publication) => publication.id === publicationId) ?? false;
  }

  citedCitationIndex(publicationId: number): number | null {
    return this.answer()?.citedPublications.find((publication) => publication.id === publicationId)?.citationIndex ?? null;
  }

  citationAriaLabel(segment: AnswerSegment): string {
    return segment.citationIndex === null
      ? 'Cita no disponible'
      : `Ir a la publicación citada ${segment.citationIndex}`;
  }

  citedPublicationCardId(publicationId: number): string {
    return `cited-publication-${publicationId}`;
  }

  setHighlightedCitation(publicationId: number | null): void {
    this.clearHighlightTimer();
    this.highlightedCitationId.set(publicationId);
  }

  clearHighlightedCitation(): void {
    if (this.highlightTimer === null) {
      this.highlightedCitationId.set(null);
    }
  }

  scrollToCitation(publicationId: number | null): void {
    if (publicationId === null) {
      return;
    }
    this.setHighlightedCitation(publicationId);
    document.getElementById(this.citedPublicationCardId(publicationId))?.scrollIntoView({
      behavior: 'smooth',
      block: 'center'
    });
    this.highlightTimer = setTimeout(() => this.highlightedCitationId.set(null), 1800);
  }

  private countUnknownCitations(blocks: ReturnType<typeof parseAnswerWithCitations>): number {
    return blocks.reduce((total, block) => total + (block.segments ?? []).filter((segment) => segment.kind === 'unknownCitation').length, 0)
      + blocks.reduce((total, block) => total + (block.items ?? []).reduce(
        (itemTotal, item) => itemTotal + item.filter((segment) => segment.kind === 'unknownCitation').length,
        0
      ), 0);
  }

  private collectUnsupportedClaimExamples(blocks: ReturnType<typeof parseAnswerWithCitations>): string[] {
    const examples: string[] = [];

    for (const block of blocks) {
      if (block.kind === 'paragraph' && this.hasVisibleText(block.segments ?? []) && !this.hasCitation(block.segments ?? [])) {
        examples.push(this.toClaimPreview(block.segments ?? []));
      }

      if (block.kind === 'list') {
        for (const item of block.items ?? []) {
          if (this.hasVisibleText(item) && !this.hasCitation(item)) {
            examples.push(this.toClaimPreview(item));
          }
        }
      }
    }

    return examples.filter((example, index, items) => example && items.indexOf(example) === index).slice(0, 3);
  }

  private hasCitation(segments: AnswerSegment[]): boolean {
    return segments.some((segment) => segment.kind === 'citation');
  }

  private hasVisibleText(segments: AnswerSegment[]): boolean {
    return this.segmentText(segments).length > 0;
  }

  private toClaimPreview(segments: AnswerSegment[]): string {
    const text = this.segmentText(segments);
    if (text.length <= 120) {
      return text;
    }
    return `${text.slice(0, 117).trimEnd()}...`;
  }

  private segmentText(segments: AnswerSegment[]): string {
    return segments
      .filter((segment) => segment.kind === 'text')
      .map((segment) => segment.text.trim())
      .join(' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private resetRun(): void {
    this.retrieval.set(null);
    this.answer.set(null);
    this.errorMessage.set('');
    this.clearHighlightTimer();
    this.highlightedCitationId.set(null);
    this.fullContextOpen.set(false);
  }

  private clearHighlightTimer(): void {
    if (this.highlightTimer !== null) {
      clearTimeout(this.highlightTimer);
      this.highlightTimer = null;
    }
  }

  private toErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as { message?: string } | null;
      const message = body?.message ?? '';
      if (message.toLowerCase().includes('ollama')) {
        return 'Ollama no está disponible.';
      }
      return message || fallback;
    }
    return fallback;
  }
}

