import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { CopilotCitation, PublicationFilterMetadata } from '../../core/api/api-models';
import { PortalDemoQuerySuggestionsService } from '../../core/api/portal-demo-query-suggestions.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { DemoQueryChipsComponent } from '../../shared/components/demo-query-chips.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { VisibilityNoteComponent } from '../../shared/components/visibility-note.component';
import { AnswerSegment, parseAnswerWithCitations } from '../copilot/copilot-citation-parser';
import {
  GeneratedReport,
  ReportSectionKey,
  ReportOutputFormat,
  ReportsContext,
  ReportTargetOption,
  ReportTemplate,
  ReportTemplateRequest,
  ReportType,
  StrategicLineTargetSnapshot
} from './reports.models';
import { ReportsService } from './reports.service';

const FALLBACK_REPORT_QUERY_EXAMPLES = [
  'salud digital',
  'IA clínica',
  'clima urbano',
  'genómica',
  'colaboración científica'
];

@Component({
  selector: 'rip-reports-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    DemoQueryChipsComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent,
    VisibilityNoteComponent
  ],
  template: `
    <section class="page reports-page">
      <rip-page-header
        title="Informes"
        subtitle="Genera dossiers internos en español a partir de la visibilidad disponible en la plataforma y exporta el resultado en Markdown."
        eyebrow="Administración"
      >
        <a mat-button routerLink="/admin/panel">Panel institucional</a>
        <a mat-button routerLink="/admin/validacion">Bandeja de validación</a>
      </rip-page-header>

      <rip-visibility-note
        message="Versión inicial privada para administración. El informe refleja la visibilidad disponible para tu sesión y se exporta en Markdown."
        emphasis="strong"
      />

      @if (contextLoading() && !context()) {
        <rip-loading-state message="Cargando contexto base para los informes..." />
      } @else if (contextError()) {
        <mat-card appearance="outlined">
          <mat-card-content class="state-card">
            <rip-error-state [message]="contextError()" />
            <div class="actions">
              <button mat-flat-button color="primary" type="button" (click)="reloadContext()">Reintentar</button>
            </div>
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="layout-grid">
          <main class="main-column">
            <mat-card appearance="outlined">
              <mat-card-content class="config-card">
                <div class="section-header">
                  <div>
                    <p class="section-kicker">Configuración del dossier</p>
                    <h2>Define alcance y objetivo</h2>
                    <p>
                      Selecciona el tipo de informe, filtra por años, afina las secciones y elige el objetivo a partir de la búsqueda.
                    </p>
                  </div>
                </div>

                <form class="form-grid" [formGroup]="form">
                  <mat-form-field appearance="outline" class="full-span">
                    <mat-label>Plantilla de informe</mat-label>
                    <mat-select formControlName="templateId">
                      <mat-option [value]="null">Sin plantilla</mat-option>
                      @for (template of activeTemplatesForType(); track template.id) {
                        <mat-option [value]="template.id">{{ template.name }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Tipo de informe</mat-label>
                    <mat-select formControlName="type">
                      @for (type of reportTypes; track type.value) {
                        <mat-option [value]="type.value">{{ type.label }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Año desde</mat-label>
                    <input matInput type="number" formControlName="yearFrom" [placeholder]="yearPlaceholder('min')">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Año hasta</mat-label>
                    <input matInput type="number" formControlName="yearTo" [placeholder]="yearPlaceholder('max')">
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="full-span">
                    <mat-label>Objetivo o consulta</mat-label>
                    <input
                      matInput
                      formControlName="targetQuery"
                      [placeholder]="targetQueryPlaceholder()"
                    >
                  </mat-form-field>

                  <div class="full-span">
                    <rip-demo-query-chips
                      title="Consultas sugeridas"
                      [caption]="reportSuggestionCaption()"
                      [queries]="reportSuggestions()"
                      (querySelected)="useReportSuggestion($event)"
                    />
                  </div>

                  <mat-form-field appearance="outline" class="full-span">
                    <mat-label>Secciones incluidas</mat-label>
                    <mat-select formControlName="sections" multiple>
                      @for (section of sectionOptions(); track section.key) {
                        <mat-option [value]="section.key">{{ section.label }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="full-span">
                    <mat-label>Instrucciones adicionales opcionales</mat-label>
                    <textarea
                      matInput
                      rows="3"
                      formControlName="additionalInstructions"
                      placeholder="Ej.: priorizar oportunidades de colaboración. No sustituye las reglas de evidencia."
                    ></textarea>
                  </mat-form-field>
                </form>

                @if (templatesLoading()) {
                  <rip-loading-state message="Cargando plantillas de informes..." />
                } @else if (templatesError()) {
                  <rip-error-state [message]="templatesError()" />
                } @else {
                  <div class="template-strip">
                    @for (template of activeTemplatesForType().slice(0, 4); track template.id) {
                      <button
                        type="button"
                        class="template-card"
                        [class.selected]="selectedTemplate()?.id === template.id"
                        (click)="applyTemplate(template)"
                      >
                        <strong>{{ template.name }}</strong>
                        <span>{{ template.description || 'Plantilla interna configurable por administración.' }}</span>
                        <small>{{ targetTypeLabel(template.targetType) }} · {{ template.sections.length }} secciones · {{ templateRangeLabel(template) }}</small>
                      </button>
                    }
                  </div>
                }

                @if (rangeError()) {
                  <rip-error-state [message]="rangeError()" />
                }

                <div class="type-description">
                  <strong>{{ currentTypeLabel() }}</strong>
                  <span>{{ currentTypeDescription() }}</span>
                </div>

                @if (currentType() === 'STRATEGIC_LINE' && lineTargetsLoading()) {
                  <rip-loading-state message="Actualizando líneas estratégicas visibles con el rango temporal seleccionado..." />
                } @else if (currentType() === 'STRATEGIC_LINE' && lineTargetsError()) {
                  <rip-error-state [message]="lineTargetsError()" />
                } @else if (filteredTargetOptions().length === 0) {
                  <rip-empty-state
                    title="Sin objetivos para esta búsqueda"
                    message="Ajusta el texto, revisa el rango temporal o cambia el tipo de informe para recuperar candidatos."
                  />
                } @else {
                  <div class="target-results">
                    <div class="target-results-header">
                      <div>
                        <p class="section-kicker">Objetivos disponibles</p>
                        <h3>{{ filteredTargetOptions().length }} coincidencias</h3>
                      </div>
                      <span class="muted">Mostrando las primeras {{ visibleTargetOptions().length }}</span>
                    </div>

                    <div class="target-grid">
                      @for (target of visibleTargetOptions(); track target.id) {
                        <button
                          type="button"
                          class="target-option"
                          [class.selected]="selectedTargetId() === target.id"
                          (click)="selectTarget(target)"
                        >
                          <div class="target-option-copy">
                            <strong>{{ target.label }}</strong>
                            <span>{{ target.helper }}</span>
                          </div>
                          @if (target.count !== null) {
                            <span class="target-count">{{ target.count }}</span>
                          }
                        </button>
                      }
                    </div>
                  </div>
                }

                <div class="actions">
                  <button mat-button type="button" (click)="resetFilters()">Restablecer</button>
                  <button
                    mat-flat-button
                    color="primary"
                    type="button"
                    [disabled]="generateDisabled()"
                    (click)="generateReport()"
                  >
                    Generar informe
                  </button>
                </div>
              </mat-card-content>
            </mat-card>

            @if (reportError()) {
              <rip-error-state [message]="reportError()" />
            }

            @if (generating()) {
              <rip-loading-state message="Generando el dossier y preparando la exportación Markdown..." />
            } @else if (!report()) {
              <rip-empty-state
                title="Todavía no hay informe generado"
                message="Selecciona un objetivo, ajusta el rango si hace falta y genera el dossier para ver la vista previa y las citas."
              />
            } @else {
              <mat-card appearance="outlined">
                <mat-card-content class="report-card">
                  <div class="report-header">
                    <div>
                      <p class="section-kicker">Vista previa renderizada</p>
                      <h2>{{ report()!.title }}</h2>
                      <p>{{ report()!.subtitle }}</p>
                    </div>
                    <button mat-stroked-button type="button" (click)="exportMarkdown()">Exportar Markdown</button>
                  </div>

                  <div class="meta-chip-list">
                    <rip-tag-chip [label]="targetTypeLabel(report()!.type)" tone="type" />
                    <rip-tag-chip [label]="report()!.target.label" />
                    <rip-tag-chip [label]="'Generado ' + formatDateTime(report()!.generatedAt)" />
                  </div>

                  @if (report()!.warnings.length > 0) {
                    <div class="warning-list">
                      @for (warning of report()!.warnings; track warning) {
                        <p>{{ warning }}</p>
                      }
                    </div>
                  }

                  <article class="report-preview">
                    @for (block of reportBlocks(); track $index) {
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
                            } @else {
                              {{ segment.text }}
                            }
                          }
                        </p>
                      }
                    }
                  </article>
                </mat-card-content>
              </mat-card>

              <mat-card appearance="outlined">
                <mat-card-content class="citations-card">
                  <div class="section-header compact-header">
                    <div>
                      <p class="section-kicker">Publicaciones citadas</p>
                      <h2>{{ report()!.citations.length }} referencias</h2>
                      <p>Las citas inline del informe apuntan a estas publicaciones.</p>
                    </div>
                  </div>

                  @if (report()!.citations.length === 0) {
                    <rip-empty-state
                      title="Sin citas visibles"
                      message="Con los filtros actuales no se ha generado una base documental suficiente para citar publicaciones."
                    />
                  } @else {
                    <div class="citation-list">
                      @for (citation of report()!.citations; track citation.id) {
                        <article
                          class="citation-card"
                          [id]="citedPublicationCardId(citation.id)"
                          [class.highlighted]="highlightedCitationId() === citation.id"
                          tabindex="-1"
                        >
                          <div class="citation-card-top">
                            <span class="citation-index">[{{ citation.citationIndex }}]</span>
                            <div class="citation-copy">
                              <strong>{{ citation.title }}</strong>
                              <span>{{ citation.year || 's. f.' }} · {{ citation.source || 'Repositorio institucional' }}</span>
                            </div>
                          </div>
                          <p>{{ authorsLine(citation) }}</p>
                          <div class="meta-chip-list">
                            @for (topic of citation.topics.slice(0, 4); track topic) {
                              <rip-tag-chip [label]="topic" />
                            }
                            @if (citation.doi) {
                              <rip-tag-chip [label]="'DOI ' + citation.doi" tone="type" />
                            }
                          </div>
                          <div class="actions">
                            <a mat-stroked-button [routerLink]="['/admin/publicaciones', citation.id]" [queryParams]="navigationContext.returnQueryParams('Volver al informe')">Ver publicación</a>
                          </div>
                        </article>
                      }
                    </div>
                  }
                </mat-card-content>
              </mat-card>
            }
          </main>

          <aside class="side-column">
            <mat-card appearance="outlined">
              <mat-card-content class="side-card">
                <div class="section-header compact-header">
                  <div>
                    <p class="section-kicker">Selección actual</p>
                    <h2>Resumen de configuración</h2>
                  </div>
                </div>

                <div class="summary-grid">
                  <div>
                    <span>Tipo</span>
                    <strong>{{ currentTypeLabel() }}</strong>
                  </div>
                  <div>
                    <span>Objetivo</span>
                    <strong>{{ selectedTarget()?.label || 'Pendiente de selección' }}</strong>
                  </div>
                  <div>
                    <span>Periodo</span>
                    <strong>{{ currentRangeLabel() }}</strong>
                  </div>
                  <div>
                    <span>Secciones</span>
                    <strong>{{ selectedSectionLabels() }}</strong>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-content class="side-card">
                <div class="section-header compact-header">
                  <div>
                    <p class="section-kicker">Métricas del informe</p>
                    <h2>Snapshot de salida</h2>
                  </div>
                </div>

                @if (report(); as generatedReport) {
                  <div class="summary-grid">
                    @for (metric of generatedReport.summaryMetrics; track metric.label) {
                      <div>
                        <span>{{ metric.label }}</span>
                        <strong>{{ metric.value }}</strong>
                      </div>
                    }
                  </div>
                } @else {
                  <rip-empty-state
                    title="Sin snapshot"
                    message="Las métricas del dossier aparecerán aquí cuando generes el informe."
                  />
                }
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-content class="side-card">
                <div class="section-header compact-header">
                  <div>
                    <p class="section-kicker">Plantillas</p>
                    <h2>{{ templateEditId() === null ? 'Crear plantilla' : 'Editar plantilla' }}</h2>
                  </div>
                  <button mat-button type="button" (click)="startNewTemplate()">Nueva</button>
                </div>

                <form class="template-form" [formGroup]="templateForm">
                  <mat-form-field appearance="outline">
                    <mat-label>Nombre</mat-label>
                    <input matInput formControlName="name">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Descripción</mat-label>
                    <textarea matInput rows="2" formControlName="description"></textarea>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Tipo objetivo</mat-label>
                    <mat-select formControlName="targetType">
                      @for (type of reportTypes; track type.value) {
                        <mat-option [value]="type.value">{{ type.label }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Secciones</mat-label>
                    <mat-select formControlName="sections" multiple>
                      @for (section of templateFormSectionOptions(); track section.key) {
                        <mat-option [value]="section.key">{{ section.label }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <div class="template-year-grid">
                    <mat-form-field appearance="outline">
                      <mat-label>Año desde</mat-label>
                      <input matInput type="number" formControlName="defaultYearFrom">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Año hasta</mat-label>
                      <input matInput type="number" formControlName="defaultYearTo">
                    </mat-form-field>
                  </div>

                  <mat-form-field appearance="outline">
                    <mat-label>Formato</mat-label>
                    <mat-select formControlName="outputFormat">
                      <mat-option value="MARKDOWN">Markdown</mat-option>
                      <mat-option value="HTML_PREVIEW">Vista HTML</mat-option>
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Estado</mat-label>
                    <mat-select formControlName="active">
                      <mat-option [value]="true">Activa</mat-option>
                      <mat-option [value]="false">Inactiva</mat-option>
                    </mat-select>
                  </mat-form-field>
                </form>

                @if (templateMessage()) {
                  <p class="template-message">{{ templateMessage() }}</p>
                }

                <div class="actions">
                  <button mat-flat-button color="primary" type="button" [disabled]="templateSaving()" (click)="saveTemplate()">
                    Guardar plantilla
                  </button>
                </div>

                <div class="template-list">
                  @for (template of templatesForType().slice(0, 5); track template.id) {
                    <button type="button" class="template-row" (click)="editTemplate(template)">
                      <strong>{{ template.name }}</strong>
                      <span>{{ template.active ? 'Activa' : 'Inactiva' }} · {{ template.sections.length }} secciones</span>
                    </button>
                  }
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-content class="side-card">
                <div class="section-header compact-header">
                  <div>
                    <p class="section-kicker">Secciones disponibles</p>
                    <h2>Qué incluye cada tipo</h2>
                  </div>
                </div>

                <div class="section-chip-list">
                  @for (section of sectionOptions(); track section.key) {
                    <rip-status-chip
                      [label]="section.label"
                      [tone]="selectedSections().includes(section.key) ? 'info' : 'neutral'"
                    />
                  }
                </div>
              </mat-card-content>
            </mat-card>
          </aside>
        </div>
      }
    </section>
  `,
  styles: [`
    .reports-page {
      display: grid;
      gap: 18px;
    }

    .layout-grid {
      display: grid;
      grid-template-columns: minmax(0, 1.7fr) minmax(300px, 0.9fr);
      gap: 20px;
      align-items: start;
    }

    .main-column,
    .side-column {
      display: grid;
      gap: 18px;
    }

    .config-card,
    .report-card,
    .citations-card,
    .side-card,
    .state-card {
      display: grid;
      gap: 18px;
    }

    .section-header,
    .report-header,
    .target-results-header {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 16px;
    }

    .section-header h2,
    .report-header h2,
    .target-results-header h3 {
      margin: 0;
      color: #142033;
      line-height: 1.15;
    }

    .section-header p,
    .report-header p,
    .target-results-header p {
      margin: 0;
      color: #617182;
      line-height: 1.55;
    }

    .compact-header {
      margin-bottom: 0;
    }

    .form-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px;
    }

    .full-span {
      grid-column: 1 / -1;
    }

    .type-description {
      display: grid;
      gap: 4px;
      padding: 14px 16px;
      border: 1px solid #d8e4eb;
      border-radius: 16px;
      background: #f9fbfd;
    }

    .type-description strong {
      color: #163247;
      font-size: 0.98rem;
    }

    .type-description span,
    .muted {
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.5;
    }

    .target-results {
      display: grid;
      gap: 14px;
    }

    .template-strip,
    .template-list {
      display: grid;
      gap: 10px;
    }

    .template-strip {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .template-card,
    .template-row {
      display: grid;
      gap: 6px;
      width: 100%;
      padding: 14px;
      border: 1px solid #dbe5ed;
      border-radius: 16px;
      background: #ffffff;
      color: inherit;
      text-align: left;
      cursor: pointer;
    }

    .template-card.selected,
    .template-row:hover,
    .template-row:focus-visible {
      border-color: #297c9d;
      background: #f4fafc;
      outline: none;
    }

    .template-card span,
    .template-card small,
    .template-row span,
    .template-message {
      color: #667487;
      font-size: 0.84rem;
      line-height: 1.45;
    }

    .template-form {
      display: grid;
      gap: 10px;
    }

    .template-year-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }

    .target-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
    }

    .target-option {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: start;
      gap: 12px;
      width: 100%;
      padding: 16px;
      border: 1px solid #dbe5ed;
      border-radius: 16px;
      background: #ffffff;
      color: inherit;
      text-align: left;
      cursor: pointer;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .target-option:hover,
    .target-option:focus-visible {
      border-color: #9fc0d1;
      box-shadow: 0 10px 22px rgba(20, 32, 51, 0.08);
      transform: translateY(-1px);
      outline: none;
    }

    .target-option.selected {
      border-color: #297c9d;
      background: #f4fafc;
      box-shadow: 0 0 0 3px rgba(41, 124, 157, 0.14);
    }

    .target-option-copy {
      display: grid;
      gap: 6px;
      min-width: 0;
    }

    .target-option-copy strong {
      color: #142033;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .target-option-copy span {
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.45;
    }

    .target-count,
    .citation-index {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 40px;
      height: 30px;
      padding: 0 10px;
      border-radius: 999px;
      background: #eef5f8;
      color: #31566a;
      font-size: 0.82rem;
      font-weight: 800;
      white-space: nowrap;
    }

    .actions,
    .meta-chip-list,
    .section-chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .actions {
      justify-content: flex-start;
    }

    .warning-list {
      display: grid;
      gap: 8px;
      padding: 14px 16px;
      border: 1px solid #efd18b;
      border-radius: 16px;
      background: #fffaf0;
    }

    .warning-list p {
      margin: 0;
      color: #72510d;
      line-height: 1.45;
    }

    .report-preview {
      display: grid;
      gap: 12px;
      color: #1f2a3d;
      line-height: 1.68;
    }

    .report-preview h3 {
      margin: 10px 0 0;
      color: #142033;
      font-size: 1.04rem;
      font-weight: 760;
    }

    .report-preview p,
    .report-preview ul {
      margin: 0;
    }

    .report-preview ul {
      padding-left: 22px;
    }

    .citation-chip {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 28px;
      height: 24px;
      margin: 0 3px;
      padding: 0 7px;
      border: 1px solid #a9c7d6;
      border-radius: 999px;
      background: #f4fafc;
      color: #17536d;
      cursor: pointer;
      font: inherit;
      font-size: 0.8rem;
      font-weight: 760;
      line-height: 1;
      vertical-align: 0.08em;
    }

    .citation-chip:hover,
    .citation-chip:focus-visible {
      border-color: #297c9d;
      background: #e6f4f8;
      box-shadow: 0 0 0 3px rgba(41, 124, 157, 0.16);
      outline: none;
    }

    .citation-list {
      display: grid;
      gap: 14px;
    }

    .citation-card {
      display: grid;
      gap: 12px;
      padding: 18px;
      border: 1px solid #dde6ee;
      border-radius: 18px;
      background: #ffffff;
      transition: border-color 140ms ease, box-shadow 140ms ease;
    }

    .citation-card.highlighted {
      border-color: #2f7f61;
      background: #f3fbf6;
      box-shadow: 0 0 0 3px rgba(47, 127, 97, 0.14);
    }

    .citation-card-top {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      gap: 12px;
      align-items: start;
    }

    .citation-copy {
      display: grid;
      gap: 6px;
      min-width: 0;
    }

    .citation-copy strong {
      color: #142033;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .citation-copy span,
    .citation-card p {
      margin: 0;
      color: #667487;
      font-size: 0.9rem;
      line-height: 1.5;
    }

    .summary-grid {
      display: grid;
      gap: 12px;
    }

    .summary-grid div {
      display: grid;
      gap: 4px;
      padding: 12px 14px;
      border: 1px solid #dfe7ee;
      border-radius: 14px;
      background: #fbfdff;
    }

    .summary-grid span {
      color: #667487;
      font-size: 0.76rem;
      font-weight: 760;
      text-transform: uppercase;
    }

    .summary-grid strong {
      color: #233044;
      font-size: 0.92rem;
      overflow-wrap: anywhere;
    }

    @media (max-width: 1120px) {
      .layout-grid {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 860px) {
      .form-grid,
      .template-strip,
      .template-year-grid,
      .target-grid {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 640px) {
      .reports-page {
        gap: 14px;
      }

      .section-header,
      .report-header,
      .target-results-header,
      .citation-card-top {
        display: grid;
      }
    }
  `]
})
export class ReportsPageComponent implements OnInit {
  private readonly reportsService = inject(ReportsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly demoQuerySuggestions = inject(PortalDemoQuerySuggestionsService);
  readonly navigationContext = inject(NavigationContextService);
  private highlightTimer: ReturnType<typeof setTimeout> | null = null;

  readonly reportTypes = this.reportsService.reportTypes;
  readonly context = signal<ReportsContext | null>(null);
  readonly contextLoading = signal(true);
  readonly contextError = signal('');
  readonly lineTargets = signal<StrategicLineTargetSnapshot[]>([]);
  readonly lineTargetsLoading = signal(false);
  readonly lineTargetsError = signal('');
  readonly templates = signal<ReportTemplate[]>([]);
  readonly templatesLoading = signal(false);
  readonly templatesError = signal('');
  readonly templateSaving = signal(false);
  readonly templateMessage = signal('');
  readonly templateEditId = signal<number | null>(null);
  readonly generating = signal(false);
  readonly reportError = signal('');
  readonly report = signal<GeneratedReport | null>(null);
  readonly selectedTargetId = signal<string | null>(null);
  readonly highlightedCitationId = signal<number | null>(null);
  readonly reportSuggestions = signal<string[]>(FALLBACK_REPORT_QUERY_EXAMPLES);
  readonly reportSuggestionsDynamic = signal(false);

  readonly form = new FormGroup({
    type: new FormControl<ReportType>('RESEARCH_UNIT', { nonNullable: true }),
    templateId: new FormControl<number | null>(null),
    targetQuery: new FormControl('', { nonNullable: true }),
    yearFrom: new FormControl('', { nonNullable: true }),
    yearTo: new FormControl('', { nonNullable: true }),
    sections: new FormControl<ReportSectionKey[]>([], { nonNullable: true }),
    additionalInstructions: new FormControl('', { nonNullable: true })
  });

  readonly templateForm = new FormGroup({
    name: new FormControl('', { nonNullable: true }),
    description: new FormControl('', { nonNullable: true }),
    targetType: new FormControl<ReportType>('RESEARCH_UNIT', { nonNullable: true }),
    sections: new FormControl<ReportSectionKey[]>([], { nonNullable: true }),
    defaultYearFrom: new FormControl('', { nonNullable: true }),
    defaultYearTo: new FormControl('', { nonNullable: true }),
    outputFormat: new FormControl<ReportOutputFormat>('MARKDOWN', { nonNullable: true }),
    active: new FormControl(true, { nonNullable: true })
  });

  readonly currentType = computed(() => this.form.controls.type.value);
  readonly sectionOptions = computed(() => this.reportsService.sectionOptionsFor(this.currentType()));
  readonly currentTypeLabel = computed(() => this.reportTypes.find((item) => item.value === this.currentType())?.label ?? '');
  readonly currentTypeDescription = computed(() => this.reportTypes.find((item) => item.value === this.currentType())?.description ?? '');
  readonly templatesForType = computed(() => this.templates().filter((template) => template.targetType === this.currentType()));
  readonly activeTemplatesForType = computed(() => this.templatesForType().filter((template) => template.active));
  readonly selectedSections = computed(() => {
    const sections = this.form.controls.sections.value;
    return sections.length > 0 ? sections : this.sectionOptions().map((option) => option.key);
  });
  readonly baseTargetOptions = computed(() => {
    const context = this.context();
    if (!context || this.currentType() === 'STRATEGIC_LINE') {
      return [] as ReportTargetOption[];
    }
    return this.reportsService.buildBaseTargets(context, this.currentType());
  });
  readonly currentTargetOptions = computed(() => this.currentType() === 'STRATEGIC_LINE'
    ? this.lineTargets().map((item) => item.target)
    : this.baseTargetOptions());
  readonly filteredTargetOptions = computed(() => {
    const query = this.normalize(this.form.controls.targetQuery.value);
    const options = this.currentTargetOptions();
    if (!query) {
      return options;
    }
    return options.filter((option) => this.matchesTargetQuery(option, query));
  });
  readonly visibleTargetOptions = computed(() => this.filteredTargetOptions().slice(0, 10));
  readonly selectedTarget = computed(() => this.currentTargetOptions().find((option) => option.id === this.selectedTargetId()) ?? null);
  readonly reportSuggestionCaption = computed(() => this.reportSuggestionsDynamic() ? 'Inspiradas en los datos del portal' : '');
  readonly rangeError = computed(() => {
    const yearFrom = this.toNumber(this.form.controls.yearFrom.value);
    const yearTo = this.toNumber(this.form.controls.yearTo.value);
    if (yearFrom !== null && yearTo !== null && yearFrom > yearTo) {
      return 'El año inicial no puede ser mayor que el año final.';
    }
    return '';
  });
  readonly generateDisabled = computed(() => {
    return this.generating()
      || this.contextLoading()
      || !!this.rangeError()
      || this.selectedTarget() === null
      || this.selectedSections().length === 0;
  });
  readonly reportBlocks = computed(() => parseAnswerWithCitations(this.report()?.markdown ?? '', this.report()?.citations ?? []));
  readonly selectedSectionLabels = computed(() => {
    const selected = new Set(this.selectedSections());
    return this.sectionOptions()
      .filter((section) => selected.has(section.key))
      .map((section) => section.label)
      .join(', ') || 'Sin selección';
  });

  ngOnInit(): void {
    this.syncSectionsWithType();
    this.syncTemplateFormSections();
    this.loadDemoQueries();
    this.reloadContext();
    this.reloadTemplates();

    this.form.controls.type.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.selectedTargetId.set(null);
        this.form.controls.templateId.setValue(null, { emitEvent: false });
        this.form.controls.targetQuery.setValue('');
        this.syncSectionsWithType();
        this.refreshStrategicLineTargetsIfNeeded();
      });

    this.form.controls.templateId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((templateId) => {
        const template = this.templates().find((item) => item.id === templateId);
        if (template) {
          this.applyTemplate(template);
        }
      });

    this.templateForm.controls.targetType.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.syncTemplateFormSections());

    this.form.controls.yearFrom.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshStrategicLineTargetsIfNeeded());

    this.form.controls.yearTo.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshStrategicLineTargetsIfNeeded());
  }

  reloadContext(): void {
    this.contextLoading.set(true);
    this.contextError.set('');
    this.reportsService.loadContext()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (context) => {
          this.context.set(context);
          this.contextLoading.set(false);
          this.refreshStrategicLineTargetsIfNeeded();
        },
        error: () => {
          this.context.set(null);
          this.contextLoading.set(false);
          this.contextError.set('No se pudo cargar el contexto base para generar informes.');
        }
      });
  }

  reloadTemplates(): void {
    this.templatesLoading.set(true);
    this.templatesError.set('');
    this.reportsService.loadTemplates()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (templates) => {
          this.templates.set(templates);
          this.templatesLoading.set(false);
          if (!templates.some((template) => template.id === this.form.controls.templateId.value)) {
            this.form.controls.templateId.setValue(null, { emitEvent: false });
          }
        },
        error: () => {
          this.templates.set([]);
          this.templatesLoading.set(false);
          this.templatesError.set('No se pudieron cargar las plantillas de informe.');
        }
      });
  }

  useReportSuggestion(query: string): void {
    this.selectedTargetId.set(null);
    this.form.controls.targetQuery.setValue(query);
  }

  resetFilters(): void {
    this.form.reset({
      type: 'RESEARCH_UNIT',
      templateId: null,
      targetQuery: '',
      yearFrom: '',
      yearTo: '',
      sections: [],
      additionalInstructions: ''
    });
    this.report.set(null);
    this.reportError.set('');
    this.selectedTargetId.set(null);
    this.syncSectionsWithType();
    this.refreshStrategicLineTargetsIfNeeded();
  }

  applyTemplate(template: ReportTemplate): void {
    this.form.controls.type.setValue(template.targetType, { emitEvent: false });
    this.form.controls.templateId.setValue(template.id, { emitEvent: false });
    this.form.controls.sections.setValue(template.sections, { emitEvent: false });
    this.form.controls.yearFrom.setValue(template.defaultYearFrom?.toString() ?? '', { emitEvent: false });
    this.form.controls.yearTo.setValue(template.defaultYearTo?.toString() ?? '', { emitEvent: false });
    this.selectedTargetId.set(null);
    this.form.controls.targetQuery.setValue('');
    this.refreshStrategicLineTargetsIfNeeded();
  }

  startNewTemplate(): void {
    this.templateEditId.set(null);
    this.templateMessage.set('');
    this.templateForm.reset({
      name: '',
      description: '',
      targetType: this.currentType(),
      sections: this.sectionOptions().map((option) => option.key),
      defaultYearFrom: '',
      defaultYearTo: '',
      outputFormat: 'MARKDOWN',
      active: true
    });
  }

  editTemplate(template: ReportTemplate): void {
    this.templateEditId.set(template.id);
    this.templateMessage.set('');
    this.templateForm.reset({
      name: template.name,
      description: template.description ?? '',
      targetType: template.targetType,
      sections: template.sections,
      defaultYearFrom: template.defaultYearFrom?.toString() ?? '',
      defaultYearTo: template.defaultYearTo?.toString() ?? '',
      outputFormat: template.outputFormat,
      active: template.active
    });
  }

  private loadDemoQueries(): void {
    this.demoQuerySuggestions.loadSuggestions({
      context: 'REPORTS',
      fallbackQueries: FALLBACK_REPORT_QUERY_EXAMPLES
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.reportSuggestions.set(result.queries);
        this.reportSuggestionsDynamic.set(result.dynamic);
      });
  }

  saveTemplate(): void {
    const payload = this.templatePayload();
    if (!payload.name || payload.sections.length === 0 || this.templateSaving()) {
      this.templateMessage.set('Completa el nombre y al menos una sección.');
      return;
    }

    this.templateSaving.set(true);
    this.templateMessage.set('');
    const editId = this.templateEditId();
    const request$ = editId === null
      ? this.reportsService.createTemplate(payload)
      : this.reportsService.updateTemplate(editId, payload);
    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (template) => {
        this.templateSaving.set(false);
        this.templateMessage.set(editId === null ? 'Plantilla creada.' : 'Plantilla actualizada.');
        this.reloadTemplates();
        this.applyTemplate(template);
      },
      error: (error: unknown) => {
        this.templateSaving.set(false);
        this.templateMessage.set(this.toErrorMessage(error, 'No se pudo guardar la plantilla.'));
      }
    });
  }

  selectTarget(target: ReportTargetOption): void {
    this.selectedTargetId.set(target.id);
  }

  generateReport(): void {
    const target = this.selectedTarget();
    if (!target || this.generateDisabled()) {
      return;
    }

    this.generating.set(true);
    this.reportError.set('');
    this.reportsService.generateReport({
      type: this.currentType(),
      templateId: this.form.controls.templateId.value,
      target,
      yearFrom: this.toNumber(this.form.controls.yearFrom.value),
      yearTo: this.toNumber(this.form.controls.yearTo.value),
      sections: this.selectedSections(),
      additionalInstructions: this.form.controls.additionalInstructions.value
    }).pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (report) => {
          this.report.set(report);
          this.generating.set(false);
        },
        error: (error: unknown) => {
          this.report.set(null);
          this.generating.set(false);
          this.reportError.set(this.toErrorMessage(error, 'No se pudo generar el informe seleccionado.'));
        }
      });
  }

  exportMarkdown(): void {
    const report = this.report();
    if (!report) {
      return;
    }
    const blob = new Blob([report.exportMarkdown], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${this.sanitizeFilename(report.title)}.md`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  targetQueryPlaceholder(): string {
    switch (this.currentType()) {
      case 'RESEARCH_UNIT':
        return 'Busca por nombre de unidad, sigla, ciudad o país';
      case 'RESEARCHER':
        return 'Busca por nombre, afiliación o correo visible';
      case 'TOPIC':
        return 'Busca por tema o variante temática';
      case 'STRATEGIC_LINE':
        return 'Busca por línea, tema o unidad conectada';
    }
  }

  currentRangeLabel(): string {
    const yearFrom = this.toNumber(this.form.controls.yearFrom.value);
    const yearTo = this.toNumber(this.form.controls.yearTo.value);
    if (yearFrom !== null && yearTo !== null) {
      return `${yearFrom}-${yearTo}`;
    }
    if (yearFrom !== null) {
      return `Desde ${yearFrom}`;
    }
    if (yearTo !== null) {
      return `Hasta ${yearTo}`;
    }
    return 'Todos los años visibles';
  }

  yearPlaceholder(bound: 'min' | 'max'): string {
    const metadata = this.context()?.metadata ?? this.emptyMetadata();
    const value = bound === 'min' ? metadata.minYear : metadata.maxYear;
    return value?.toString() ?? '';
  }

  targetTypeLabel(type: ReportType): string {
    return this.reportTypes.find((item) => item.value === type)?.label ?? type;
  }

  selectedTemplate(): ReportTemplate | null {
    return this.templates().find((template) => template.id === this.form.controls.templateId.value) ?? null;
  }

  templateFormSectionOptions() {
    return this.reportsService.sectionOptionsFor(this.templateForm.controls.targetType.value);
  }

  sectionLabel(section: ReportSectionKey, type: ReportType = this.currentType()): string {
    return this.reportsService.sectionOptionsFor(type).find((option) => option.key === section)?.label ?? section;
  }

  templateRangeLabel(template: ReportTemplate): string {
    if (template.defaultYearFrom !== null && template.defaultYearTo !== null) {
      return `${template.defaultYearFrom}-${template.defaultYearTo}`;
    }
    if (template.defaultYearFrom !== null) {
      return `Desde ${template.defaultYearFrom}`;
    }
    if (template.defaultYearTo !== null) {
      return `Hasta ${template.defaultYearTo}`;
    }
    return 'Sin rango por defecto';
  }

  formatDateTime(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  authorsLine(citation: CopilotCitation): string {
    return citation.authors.length > 0 ? citation.authors.join(', ') : 'Autores visibles no disponibles';
  }

  citationAriaLabel(segment: AnswerSegment): string {
    return segment.citationIndex === null ? 'Cita no disponible' : `Ir a la publicación citada ${segment.citationIndex}`;
  }

  citedPublicationCardId(publicationId: number): string {
    return `report-citation-${publicationId}`;
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

  private syncSectionsWithType(): void {
    this.form.controls.sections.setValue(this.sectionOptions().map((option) => option.key), { emitEvent: false });
  }

  private syncTemplateFormSections(): void {
    const type = this.templateForm.controls.targetType.value;
    const current = this.templateForm.controls.sections.value;
    const allowed = this.reportsService.sectionOptionsFor(type).map((option) => option.key);
    const retained = current.filter((section) => allowed.includes(section));
    this.templateForm.controls.sections.setValue(retained.length > 0 ? retained : allowed, { emitEvent: false });
  }

  private templatePayload(): ReportTemplateRequest {
    return {
      name: this.templateForm.controls.name.value.trim(),
      description: this.templateForm.controls.description.value.trim() || null,
      targetType: this.templateForm.controls.targetType.value,
      sections: this.templateForm.controls.sections.value,
      defaultYearFrom: this.toNumber(this.templateForm.controls.defaultYearFrom.value),
      defaultYearTo: this.toNumber(this.templateForm.controls.defaultYearTo.value),
      outputFormat: this.templateForm.controls.outputFormat.value,
      active: this.templateForm.controls.active.value
    };
  }

  private refreshStrategicLineTargetsIfNeeded(): void {
    if (this.currentType() !== 'STRATEGIC_LINE') {
      this.lineTargets.set([]);
      this.lineTargetsLoading.set(false);
      this.lineTargetsError.set('');
      return;
    }
    if (this.rangeError()) {
      this.lineTargets.set([]);
      this.lineTargetsLoading.set(false);
      this.lineTargetsError.set('');
      return;
    }

    this.lineTargetsLoading.set(true);
    this.lineTargetsError.set('');
    this.reportsService.loadStrategicLineTargets(
      this.toNumber(this.form.controls.yearFrom.value),
      this.toNumber(this.form.controls.yearTo.value)
    ).pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (targets) => {
          this.lineTargets.set(targets);
          if (!targets.some((item) => item.target.id === this.selectedTargetId())) {
            this.selectedTargetId.set(null);
          }
          this.lineTargetsLoading.set(false);
        },
        error: () => {
          this.lineTargets.set([]);
          this.selectedTargetId.set(null);
          this.lineTargetsLoading.set(false);
          this.lineTargetsError.set('No se pudieron actualizar las líneas estratégicas para este rango temporal.');
        }
      });
  }

  private toErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as { message?: string } | null;
      return body?.message || fallback;
    }
    if (error instanceof Error) {
      return error.message || fallback;
    }
    return fallback;
  }

  private sanitizeFilename(value: string): string {
    return this.normalize(value).replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'informe';
  }

  private matchesTargetQuery(option: ReportTargetOption, query: string): boolean {
    const searchableText = [
      option.label,
      option.helper,
      ...option.keywords
    ].map((value) => this.normalize(value)).join(' ');

    if (searchableText.includes(query)) {
      return true;
    }

    const queryTokens = this.tokenizeNormalized(query);
    return queryTokens.length > 0 && queryTokens.every((token) => searchableText.includes(token));
  }

  private normalize(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
  }

  private tokenizeNormalized(value: string): string[] {
    return value
      .split(/\s+/)
      .map((token) => token.trim())
      .filter((token) => token.length >= 3);
  }

  private toNumber(value: string): number | null {
    if (!value) {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private emptyMetadata(): PublicationFilterMetadata {
    return {
      minYear: null,
      maxYear: null,
      availableTypes: [],
      availableStatuses: [],
      researchUnits: [],
      topics: []
    };
  }

  private clearHighlightTimer(): void {
    if (this.highlightTimer !== null) {
      clearTimeout(this.highlightTimer);
      this.highlightTimer = null;
    }
  }
}
