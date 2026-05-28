import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle
} from '@angular/material/dialog';
import { forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

import {
  CopilotAnswerResponse,
  CopilotRetrievedPublication,
  PortalResearcherDetail,
  Publication,
  RelatedPublication,
  RelatedPublicationsResponse
} from '../../core/api/api-models';
import { CopilotApiService } from '../../core/api/copilot-api.service';
import { PortalApiService } from '../../core/api/portal-api.service';
import { PublicationsApiService } from '../../core/api/publications-api.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';

export type PublicationExplanationMode = 'DIVULGATIVE' | 'TECHNICAL';

interface PublicationExplanationDialogData {
  publication: Publication;
  publicationLink: string[];
  preservedQueryParams: Record<string, string | number>;
  initialMode: PublicationExplanationMode;
}

interface PublicationExplanationSections {
  summary: string;
  problem: string;
  relevance: string;
  approach: string;
}

interface PublicationExplanationResearcher {
  id: number;
  name: string;
  primaryAffiliationName: string | null;
  topicPreview: string[];
  visiblePublicationCount: number;
}

interface PublicationExplanationUnit {
  id: number;
  name: string;
  linkedResearcherCount: number;
  linkedResearcherNames: string[];
}

interface PublicationExplanationRelatedPublication {
  id: number;
  title: string;
  year: number | null;
  source: string | null;
  topics: string[];
  score: number | null;
  reasons: string[];
}

interface PublicationExplanationTechnicalInfo {
  provider: string;
  model: string;
  visibilityScope: string;
  validationFilterApplied: boolean;
  contextPublicationCount: number;
  warnings: string[];
}

interface PublicationExplanationViewModel {
  sections: PublicationExplanationSections;
  topics: string[];
  researchers: PublicationExplanationResearcher[];
  units: PublicationExplanationUnit[];
  publications: PublicationExplanationRelatedPublication[];
  warnings: string[];
  technicalInfo: PublicationExplanationTechnicalInfo;
}

const SECTION_LABELS = {
  summary: 'RESUMEN_SENCILLO',
  problem: 'PROBLEMA',
  relevance: 'RELEVANCIA',
  approach: 'ENFOQUE'
} as const;

@Component({
  selector: 'rip-publication-explanation-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    TagChipComponent
  ],
  template: `
    <h2 mat-dialog-title>Explicar esta publicación</h2>

    <mat-dialog-content class="dialog-content">
      <section class="hero-panel">
        <div class="hero-copy">
          <span class="eyebrow">Lectura guiada</span>
          <h3>{{ data.publication.title }}</h3>
          <p>
            Genera una explicación clara de la publicación con contexto público validado, temas cercanos y perfiles
            relacionados visibles en el portal.
          </p>
        </div>

        <mat-button-toggle-group [formControl]="modeControl" aria-label="Modo de explicación">
          <mat-button-toggle value="DIVULGATIVE">Divulgativo</mat-button-toggle>
          <mat-button-toggle value="TECHNICAL">Técnico</mat-button-toggle>
        </mat-button-toggle-group>
      </section>

      <div class="context-note">
        <strong>Contexto usado:</strong> solo información pública validada y relaciones disponibles para esta ficha.
      </div>

      @if (loading()) {
        <rip-loading-state [message]="loadingMessage()" />
      } @else {
        @if (errorMessage()) {
          <section class="error-panel">
            <rip-error-state [message]="errorMessage()" />
            <div class="error-actions">
              <button mat-stroked-button type="button" (click)="retry()">Reintentar</button>
            </div>
          </section>
        } @else {
          @if (explanation(); as explanation) {
            @if (explanation.warnings.length > 0) {
              <section class="warning-panel">
                <span class="panel-label">Contexto limitado</span>
                <ul>
                  @for (warning of explanation.warnings; track warning) {
                    <li>{{ warning }}</li>
                  }
                </ul>
              </section>
            }

            <section class="section-grid">
              <article class="feature-card">
                <span class="card-label">Resumen sencillo</span>
                <p>{{ explanation.sections.summary }}</p>
              </article>

              <article class="feature-card">
                <span class="card-label">Qué problema aborda</span>
                <p>{{ explanation.sections.problem }}</p>
              </article>

              <article class="feature-card">
                <span class="card-label">Por qué es relevante</span>
                <p>{{ explanation.sections.relevance }}</p>
              </article>

              <article class="feature-card">
                <span class="card-label">Enfoque</span>
                <p>{{ explanation.sections.approach }}</p>
              </article>
            </section>

            <section class="support-grid">
              <article class="support-card">
                <div class="card-head">
                  <h4>Temas relacionados</h4>
                  <p>Conceptos que ayudan a situar la publicación.</p>
                </div>

                <div class="chip-list">
                  @for (topic of explanation.topics; track topic) {
                    <rip-tag-chip [label]="topic" />
                  } @empty {
                    <rip-empty-state
                      title="Sin temas destacados"
                      message="Esta explicación no dispone de suficientes temas visibles para resumir conexiones."
                    />
                  }
                </div>
              </article>

              <article class="support-card">
                <div class="card-head">
                  <h4>Investigadores relacionados</h4>
                  <p>Perfiles públicos vinculados directamente con esta publicación.</p>
                </div>

                <div class="link-list">
                  @for (researcher of explanation.researchers; track researcher.id) {
                    <a
                      class="link-card"
                      [routerLink]="['/portal/investigadores', researcher.id]"
                      [queryParams]="portalDetailQueryParams()"
                    >
                      <strong>{{ researcher.name }}</strong>
                      <p>{{ researcher.primaryAffiliationName || 'Perfil público sin afiliación principal visible' }}</p>
                      <span>
                        {{ researcher.visiblePublicationCount }} publicaciones visibles
                        @if (researcher.topicPreview.length > 0) {
                          · {{ researcher.topicPreview.join(', ') }}
                        }
                      </span>
                    </a>
                  } @empty {
                    <rip-empty-state
                      title="Sin investigadores enlazables"
                      message="No hay perfiles internos públicos suficientes para enlazar esta publicación con investigadores."
                    />
                  }
                </div>
              </article>

              <article class="support-card">
                <div class="card-head">
                  <h4>Unidades relacionadas</h4>
                  <p>Afiliaciones públicas de los investigadores vinculados.</p>
                </div>

                <div class="link-list">
                  @for (unit of explanation.units; track unit.id) {
                    <a
                      class="link-card"
                      [routerLink]="['/portal/unidades', unit.id]"
                      [queryParams]="portalDetailQueryParams()"
                    >
                      <strong>{{ unit.name }}</strong>
                      <p>{{ unit.linkedResearcherCount }} investigadores conectados a esta ficha</p>
                      <span>{{ unit.linkedResearcherNames.join(', ') }}</span>
                    </a>
                  } @empty {
                    <rip-empty-state
                      title="Sin unidades visibles"
                      message="No hay afiliaciones públicas suficientes para resumir unidades relacionadas."
                    />
                  }
                </div>
              </article>

              <article class="support-card wide-card">
                <div class="card-head">
                  <h4>Publicaciones relacionadas</h4>
                  <p>Referencias cercanas por similitud temática, autores o proximidad del contexto.</p>
                </div>

                <div class="link-list">
                  @for (publication of explanation.publications; track publication.id) {
                    <a
                      class="link-card publication-link-card"
                      [routerLink]="['/publications', publication.id]"
                      [queryParams]="publicationDetailQueryParams()"
                    >
                      <div class="link-card-top">
                        <strong>{{ publication.title }}</strong>
                        <span>{{ publication.year || 's. f.' }}</span>
                      </div>
                      <p>{{ publication.source || 'Repositorio institucional' }}</p>
                      @if (publication.topics.length > 0) {
                        <div class="chip-list compact-chip-list">
                          @for (topic of publication.topics.slice(0, 4); track topic) {
                            <rip-tag-chip [label]="topic" />
                          }
                        </div>
                      }
                      @if (publication.reasons.length > 0) {
                        <span>{{ publication.reasons[0] }}</span>
                      }
                    </a>
                  } @empty {
                    <rip-empty-state
                      title="Sin publicaciones relacionadas"
                      message="Todavía no hay suficiente evidencia visible para proponer publicaciones cercanas."
                    />
                  }
                </div>
              </article>
            </section>

            <details class="technical-details">
              <summary>Información técnica</summary>

              <div class="technical-grid">
                <div>
                  <span>Modo</span>
                  <strong>{{ modeLabel(modeControl.value) }}</strong>
                </div>
                <div>
                  <span>Modelo</span>
                  <strong>{{ explanation.technicalInfo.provider }} · {{ explanation.technicalInfo.model }}</strong>
                </div>
                <div>
                  <span>Visibilidad</span>
                  <strong>{{ explanation.technicalInfo.visibilityScope }}</strong>
                </div>
                <div>
                  <span>Filtro validado</span>
                  <strong>{{ explanation.technicalInfo.validationFilterApplied ? 'Aplicado' : 'No indicado' }}</strong>
                </div>
                <div>
                  <span>Publicaciones usadas</span>
                  <strong>{{ explanation.technicalInfo.contextPublicationCount }}</strong>
                </div>
              </div>

              @if (explanation.technicalInfo.warnings.length > 0) {
                <div class="technical-warnings">
                  @for (warning of explanation.technicalInfo.warnings; track warning) {
                    <p>{{ warning }}</p>
                  }
                </div>
              }
            </details>
          }
        }
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" matDialogClose>Cerrar</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-content {
      display: grid;
      gap: 20px;
      min-width: min(920px, 92vw);
      max-width: 100%;
      padding-top: 4px;
    }

    .hero-panel {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 18px;
      padding: 22px 24px;
      border: 1px solid #dbe8ef;
      border-radius: 24px;
      background:
        radial-gradient(circle at top right, rgba(71, 131, 159, 0.16), transparent 30%),
        linear-gradient(155deg, #ffffff, #f7fbfd 70%, #f1f7f8 100%);
    }

    .hero-copy {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .eyebrow,
    .panel-label,
    .card-label {
      color: #2f6078;
      font-size: 0.76rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .hero-copy h3,
    .card-head h4 {
      margin: 0;
      color: #112235;
    }

    .hero-copy h3 {
      font-size: clamp(1.35rem, 2vw, 1.8rem);
      line-height: 1.18;
      overflow-wrap: anywhere;
    }

    .hero-copy p,
    .feature-card p,
    .card-head p,
    .link-card p,
    .link-card span,
    .technical-warnings p {
      margin: 0;
      color: #5d6f80;
      line-height: 1.6;
    }

    .context-note,
    .warning-panel,
    .feature-card,
    .support-card,
    .technical-details {
      border: 1px solid #e1e8ee;
      border-radius: 20px;
      background: #ffffff;
    }

    .context-note {
      padding: 14px 16px;
      color: #476072;
      background: #f8fbfc;
    }

    .warning-panel {
      padding: 16px 18px;
      background: #fffaf0;
      border-color: #efd8a3;
    }

    .warning-panel ul {
      margin: 10px 0 0;
      padding-left: 18px;
      color: #6d5a2b;
    }

    .warning-panel li + li {
      margin-top: 6px;
    }

    .section-grid,
    .support-grid {
      display: grid;
      gap: 16px;
    }

    .section-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .support-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .feature-card,
    .support-card {
      display: grid;
      gap: 14px;
      padding: 20px;
    }

    .feature-card {
      background: linear-gradient(180deg, #ffffff, #fbfdfe);
    }

    .wide-card {
      grid-column: 1 / -1;
    }

    .card-head {
      display: grid;
      gap: 6px;
    }

    .chip-list,
    .compact-chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .link-list {
      display: grid;
      gap: 12px;
    }

    .link-card {
      display: grid;
      gap: 8px;
      padding: 16px 18px;
      border: 1px solid #dfe7ed;
      border-radius: 18px;
      background: #fbfdfe;
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, border-color 140ms ease, box-shadow 140ms ease;
    }

    .link-card:hover {
      transform: translateY(-2px);
      border-color: #a5c2d1;
      box-shadow: 0 16px 32px rgba(17, 34, 53, 0.08);
    }

    .link-card strong,
    .technical-grid strong {
      color: #112235;
      line-height: 1.35;
    }

    .link-card-top {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 12px;
    }

    .link-card-top span {
      white-space: nowrap;
    }

    .publication-link-card {
      background: linear-gradient(180deg, #ffffff, #f9fcfd);
    }

    .technical-details {
      padding: 18px 20px;
      background: #f8fbfc;
    }

    .technical-details summary {
      cursor: pointer;
      color: #244d63;
      font-weight: 760;
    }

    .technical-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
      margin-top: 16px;
    }

    .technical-grid div {
      display: grid;
      gap: 4px;
      padding: 14px 16px;
      border: 1px solid #dbe7ed;
      border-radius: 16px;
      background: #ffffff;
    }

    .technical-grid span {
      color: #617385;
      font-size: 0.76rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .technical-warnings {
      display: grid;
      gap: 8px;
      margin-top: 16px;
    }

    .error-panel,
    .error-actions {
      display: grid;
      gap: 12px;
    }

    @media (max-width: 900px) {
      .dialog-content {
        min-width: 0;
      }

      .hero-panel,
      .section-grid,
      .support-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class PublicationExplanationDialogComponent implements OnInit {
  private readonly publicationsApi = inject(PublicationsApiService);
  private readonly portalApi = inject(PortalApiService);
  private readonly copilotApi = inject(CopilotApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly data = inject<PublicationExplanationDialogData>(MAT_DIALOG_DATA);

  readonly modeControl = new FormControl<PublicationExplanationMode>(this.data.initialMode, { nonNullable: true });
  readonly explanation = signal<PublicationExplanationViewModel | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');

  private readonly explanationCache = new Map<PublicationExplanationMode, PublicationExplanationViewModel>();
  private requestNonce = 0;

  ngOnInit(): void {
    this.modeControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((mode) => {
        this.syncRouteMode(mode);
        this.loadExplanation(mode);
      });

    this.syncRouteMode(this.modeControl.value);
    this.loadExplanation(this.modeControl.value);
  }

  loadingMessage(): string {
    return this.modeControl.value === 'TECHNICAL'
      ? 'Preparando una explicación técnica...'
      : 'Preparando una explicación divulgativa...';
  }

  modeLabel(mode: PublicationExplanationMode): string {
    return mode === 'TECHNICAL' ? 'Técnico' : 'Divulgativo';
  }

  retry(): void {
    const mode = this.modeControl.value;
    this.explanationCache.delete(mode);
    this.loadExplanation(mode);
  }

  publicationDetailQueryParams(): Record<string, string | boolean> {
    return {
      returnTo: this.buildExplanationContextUrl(),
      returnLabel: 'Volver a la explicación',
      portalContext: true
    };
  }

  portalDetailQueryParams(): Record<string, string> {
    return {
      returnTo: this.buildExplanationContextUrl(),
      returnLabel: 'Volver a la explicación'
    };
  }

  private loadExplanation(mode: PublicationExplanationMode): void {
    const cached = this.explanationCache.get(mode);
    if (cached) {
      this.explanation.set(cached);
      this.errorMessage.set('');
      this.loading.set(false);
      return;
    }

    const requestId = ++this.requestNonce;
    this.loading.set(true);
    this.errorMessage.set('');

    this.buildExplanation(mode)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (explanation) => {
          if (requestId !== this.requestNonce) {
            return;
          }
          this.explanationCache.set(mode, explanation);
          this.explanation.set(explanation);
          this.loading.set(false);
        },
        error: () => {
          if (requestId !== this.requestNonce) {
            return;
          }
          this.explanation.set(null);
          this.loading.set(false);
          this.errorMessage.set('No se pudo generar la explicación de esta publicación en este momento.');
        }
      });
  }

  private buildExplanation(mode: PublicationExplanationMode) {
    const publication = this.data.publication;
    const internalAuthorIds = Array.from(new Set(
      publication.authors
        .map((author) => author.researcherId)
        .filter((researcherId): researcherId is number => researcherId !== null)
    ));

    const researchers$ = internalAuthorIds.length > 0
      ? forkJoin(
          internalAuthorIds.map((researcherId) =>
            this.portalApi.researcher(researcherId).pipe(catchError(() => of(null)))
          )
        ).pipe(
          map((researchers) => researchers.filter((researcher): researcher is PortalResearcherDetail => researcher !== null))
        )
      : of([] as PortalResearcherDetail[]);

    const related$ = this.publicationsApi.related(publication.id, { limit: 6, minScore: 0.35 }).pipe(
      catchError(() => of<RelatedPublicationsResponse>({
        publicationId: publication.id,
        limit: 6,
        minScore: 0.35,
        metadataOnly: false,
        warnings: ['No se pudieron cargar publicaciones relacionadas adicionales para enriquecer la explicación.'],
        relatedPublications: []
      }))
    );

    return forkJoin({
      researchers: researchers$,
      relatedResponse: related$
    }).pipe(
      switchMap(({ researchers, relatedResponse }) => {
        const relatedCandidates = relatedResponse.relatedPublications.slice(0, 4);
        const relatedDetails$ = relatedCandidates.length > 0
          ? forkJoin(
              relatedCandidates.map((item) =>
                this.publicationsApi.get(item.publication.id).pipe(catchError(() => of(null)))
              )
            ).pipe(
              map((publications) => publications.filter((entry): entry is Publication => entry !== null))
            )
          : of([] as Publication[]);

        return relatedDetails$.pipe(
          switchMap((relatedDetails) => {
            const copilotRequest = this.buildCopilotRequest(mode, publication, relatedCandidates, relatedDetails);
            return this.copilotApi.answer(copilotRequest).pipe(
              map((response) => this.toExplanationViewModel(mode, response, publication, relatedResponse.relatedPublications, researchers, relatedDetails))
            );
          })
        );
      })
    );
  }

  private buildCopilotRequest(
    mode: PublicationExplanationMode,
    publication: Publication,
    relatedCandidates: RelatedPublication[],
    relatedDetails: Publication[]
  ) {
    const relatedMap = new Map(relatedCandidates.map((item) => [item.publication.id, item]));
    const retrievedPublications: CopilotRetrievedPublication[] = [
      this.toRetrievedPublication(publication, null, true),
      ...relatedDetails.map((item) => this.toRetrievedPublication(item, relatedMap.get(item.id) ?? null, false))
    ];

    return {
      question: this.buildExplanationPrompt(mode, publication),
      retrievedPublications,
      includeNonValidated: false
    };
  }

  private toExplanationViewModel(
    mode: PublicationExplanationMode,
    response: CopilotAnswerResponse,
    publication: Publication,
    related: RelatedPublication[],
    researchers: PortalResearcherDetail[],
    relatedDetails: Publication[]
  ): PublicationExplanationViewModel {
    const sections = this.parseSections(response.answer);
    const topics = this.collectTopics(publication, related, researchers);
    const warnings = this.collectWarnings(publication, related, researchers, response.warnings);
    const publications = this.buildRelatedPublicationCards(related, relatedDetails);

    return {
      sections: {
        summary: sections.summary || this.fallbackSummary(publication, mode),
        problem: sections.problem || this.fallbackProblem(publication),
        relevance: sections.relevance || this.fallbackRelevance(publication, related, researchers),
        approach: sections.approach || this.fallbackApproach(publication)
      },
      topics,
      researchers: this.buildResearcherCards(researchers),
      units: this.buildUnitCards(researchers),
      publications,
      warnings,
      technicalInfo: {
        provider: response.provider,
        model: response.model,
        visibilityScope: response.visibilityScope,
        validationFilterApplied: response.validationFilterApplied,
        contextPublicationCount: response.retrievedPublications.length,
        warnings: response.warnings
      }
    };
  }

  private buildExplanationPrompt(mode: PublicationExplanationMode, publication: Publication): string {
    const tone = mode === 'TECHNICAL'
      ? 'Escribe con tono técnico, preciso y claro para una persona con cultura investigadora.'
      : 'Escribe con tono divulgativo, cercano y fácil de entender para público general sin perder rigor.';

    return [
      'Explica una publicación para el portal público institucional.',
      tone,
      'Usa solo el contexto proporcionado.',
      'No inventes datos, resultados, métodos, unidades ni investigadores.',
      'Si el contexto es limitado, dilo de forma breve dentro de la sección correspondiente.',
      `Publicación principal: ${publication.title}.`,
      'Devuelve exactamente estas etiquetas, en este orden, sin viñetas ni texto adicional:',
      'RESUMEN_SENCILLO:',
      'PROBLEMA:',
      'RELEVANCIA:',
      'ENFOQUE:',
      'Cada sección debe tener entre dos y cuatro frases.'
    ].join('\n');
  }

  private parseSections(answer: string): PublicationExplanationSections {
    const normalized = answer.replace(/\r/g, '');
    return {
      summary: this.extractSection(normalized, SECTION_LABELS.summary, [SECTION_LABELS.problem, SECTION_LABELS.relevance, SECTION_LABELS.approach]),
      problem: this.extractSection(normalized, SECTION_LABELS.problem, [SECTION_LABELS.relevance, SECTION_LABELS.approach]),
      relevance: this.extractSection(normalized, SECTION_LABELS.relevance, [SECTION_LABELS.approach]),
      approach: this.extractSection(normalized, SECTION_LABELS.approach, [])
    };
  }

  private extractSection(answer: string, label: string, nextLabels: string[]): string {
    const endPattern = nextLabels.length > 0 ? `(?=\\n(?:${nextLabels.join('|')}):|$)` : '$';
    const match = answer.match(new RegExp(`${label}:\\s*([\\s\\S]*?)${endPattern}`, 'm'));
    return (match?.[1] ?? '').trim();
  }

  private collectTopics(
    publication: Publication,
    related: RelatedPublication[],
    researchers: PortalResearcherDetail[]
  ): string[] {
    const scoreMap = new Map<string, number>();
    for (const topic of publication.topics.map((item) => item.name)) {
      scoreMap.set(topic, (scoreMap.get(topic) ?? 0) + 5);
    }
    for (const item of related) {
      for (const topic of item.sharedTopicNames) {
        scoreMap.set(topic, (scoreMap.get(topic) ?? 0) + 3);
      }
      for (const topic of item.publication.topics) {
        scoreMap.set(topic, (scoreMap.get(topic) ?? 0) + 1);
      }
    }
    for (const researcher of researchers) {
      for (const topic of researcher.topics.slice(0, 4)) {
        scoreMap.set(topic.name, (scoreMap.get(topic.name) ?? 0) + 1);
      }
    }
    return [...scoreMap.entries()]
      .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0], 'es'))
      .map(([topic]) => topic)
      .slice(0, 10);
  }

  private buildResearcherCards(researchers: PortalResearcherDetail[]): PublicationExplanationResearcher[] {
    return researchers
      .map((researcher) => {
        const primaryAffiliation = researcher.affiliations.find((item) => item.primaryAffiliation && item.current)
          ?? researcher.affiliations.find((item) => item.current)
          ?? researcher.affiliations[0]
          ?? null;
        return {
          id: researcher.id,
          name: researcher.displayName || researcher.fullName,
          primaryAffiliationName: primaryAffiliation?.researchUnitName ?? null,
          topicPreview: researcher.topics.slice(0, 3).map((topic) => topic.name),
          visiblePublicationCount: researcher.publications.length
        };
      })
      .sort((left, right) => left.name.localeCompare(right.name, 'es'));
  }

  private buildUnitCards(researchers: PortalResearcherDetail[]): PublicationExplanationUnit[] {
    const units = new Map<number, PublicationExplanationUnit>();

    for (const researcher of researchers) {
      const preferredAffiliations = researcher.affiliations.filter((item) => item.current);
      const sourceAffiliations = preferredAffiliations.length > 0 ? preferredAffiliations : researcher.affiliations;

      for (const affiliation of sourceAffiliations) {
        const current = units.get(affiliation.researchUnitId) ?? {
          id: affiliation.researchUnitId,
          name: affiliation.researchUnitName || 'Unidad institucional',
          linkedResearcherCount: 0,
          linkedResearcherNames: []
        };

        if (!current.linkedResearcherNames.includes(researcher.displayName || researcher.fullName)) {
          current.linkedResearcherNames.push(researcher.displayName || researcher.fullName);
          current.linkedResearcherCount += 1;
        }

        units.set(affiliation.researchUnitId, current);
      }
    }

    return [...units.values()]
      .sort((left, right) =>
        right.linkedResearcherCount - left.linkedResearcherCount || left.name.localeCompare(right.name, 'es')
      )
      .slice(0, 6);
  }

  private buildRelatedPublicationCards(
    related: RelatedPublication[],
    relatedDetails: Publication[]
  ): PublicationExplanationRelatedPublication[] {
    const detailsById = new Map(relatedDetails.map((item) => [item.id, item]));
    return related.map((item) => {
      const detail = detailsById.get(item.publication.id);
      return {
        id: item.publication.id,
        title: item.publication.title,
        year: item.publication.year,
        source: item.publication.source,
        topics: detail?.topics.map((topic) => topic.name) ?? item.publication.topics,
        score: item.finalScore,
        reasons: item.explanationReasons
      };
    });
  }

  private collectWarnings(
    publication: Publication,
    related: RelatedPublication[],
    researchers: PortalResearcherDetail[],
    assistantWarnings: string[]
  ): string[] {
    const warnings = new Set<string>();

    if (!publication.abstractText?.trim() && !publication.publicSummary?.trim()) {
      warnings.add('La ficha no incluye resumen ni resumen público, así que la explicación se apoya sobre todo en metadatos y relaciones visibles.');
    }
    if (researchers.length === 0) {
      warnings.add('No hay suficientes perfiles internos públicos enlazables para resumir investigadores o unidades con detalle.');
    }
    if (related.length === 0) {
      warnings.add('No se encontraron publicaciones relacionadas visibles para ampliar el contexto de la explicación.');
    }
    for (const warning of assistantWarnings) {
      warnings.add(warning);
    }

    return [...warnings];
  }

  private fallbackSummary(publication: Publication, mode: PublicationExplanationMode): string {
    if (publication.publicSummary?.trim()) {
      return publication.publicSummary.trim();
    }
    if (publication.abstractText?.trim()) {
      return publication.abstractText.trim();
    }
    return mode === 'TECHNICAL'
      ? 'La ficha disponible permite identificar el foco general de la publicación, pero no ofrece suficiente texto para resumir con precisión sus hallazgos o resultados.'
      : 'La ficha disponible deja ver el tema general de la publicación, pero no aporta suficiente texto para contarla con más detalle.';
  }

  private fallbackProblem(publication: Publication): string {
    if (publication.abstractText?.trim()) {
      return 'El problema se infiere a partir del resumen disponible y del contexto temático asociado a la ficha, aunque no siempre queda formulado de manera explícita.';
    }
    return 'La ficha pública no describe con suficiente detalle el problema concreto que aborda, así que solo puede inferirse a partir del título y de los temas relacionados.';
  }

  private fallbackRelevance(
    publication: Publication,
    related: RelatedPublication[],
    researchers: PortalResearcherDetail[]
  ): string {
    const topicCount = publication.topics.length;
    const relationCount = related.length;
    if (topicCount === 0 && relationCount === 0 && researchers.length === 0) {
      return 'Su relevancia no puede concretarse demasiado porque la ficha pública disponible es limitada y apenas muestra conexiones adicionales.';
    }
    return `Su relevancia se aprecia en las conexiones visibles con ${Math.max(topicCount, 1)} tema(s), ${relationCount} publicación(es) relacionada(s) y ${researchers.length} perfil(es) investigador(es) enlazables.`;
  }

  private fallbackApproach(publication: Publication): string {
    const descriptors = [
      publication.publicationDate || (publication.year ? String(publication.year) : null),
      publication.source,
      publication.type
    ].filter(Boolean).join(', ');

    return descriptors
      ? `El enfoque se resume a partir de la descripción disponible en la ficha y de sus metadatos editoriales: ${descriptors}.`
      : 'El enfoque solo puede resumirse de forma general porque la ficha pública no muestra suficiente detalle metodológico.';
  }

  private toRetrievedPublication(
    publication: Publication,
    related: RelatedPublication | null,
    isPrimary: boolean
  ): CopilotRetrievedPublication {
    return {
      id: publication.id,
      title: publication.title,
      abstractText: publication.abstractText,
      year: publication.year,
      doi: publication.doi,
      source: publication.source,
      url: publication.url,
      authors: publication.authors.map((author) => author.researcherName || author.externalAuthorName || 'Autor sin nombre'),
      topics: publication.topics.map((topic) => topic.name),
      similarityScore: isPrimary ? 1 : related?.semanticScore ?? related?.finalScore ?? null,
      passedThreshold: true,
      lowSimilarity: !isPrimary && (related?.finalScore ?? 1) < 0.35,
      retrievalReason: isPrimary
        ? 'Publicación principal seleccionada para generar la explicación.'
        : (related?.explanationReasons.join(' ') || 'Publicación relacionada por similitud temática o de contexto.')
    };
  }

  private buildExplanationContextUrl(mode = this.modeControl.value): string {
    const tree = this.router.createUrlTree(this.data.publicationLink, {
      queryParams: {
        ...this.data.preservedQueryParams,
        portalContext: 'true',
        explainPublication: 'true',
        explanationMode: this.modeQueryValue(mode)
      }
    });
    return this.router.serializeUrl(tree);
  }

  private syncRouteMode(mode: PublicationExplanationMode): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        portalContext: 'true',
        explainPublication: 'true',
        explanationMode: this.modeQueryValue(mode)
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  private modeQueryValue(mode: PublicationExplanationMode): string {
    return mode === 'TECHNICAL' ? 'tecnico' : 'divulgativo';
  }
}
