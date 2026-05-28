import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { catchError, forkJoin, map, of } from 'rxjs';

import {
  NewsArticle,
  NewsArticleRequest,
  NewsArticleStatus,
  NewsDraftGenerateResponse,
  NewsDraftSourceType,
  NewsDraftTone,
  PublicationSummary,
  ResearchUnit,
  ResearcherSummary
} from '../../core/api/api-models';
import { NewsDraftApiService } from '../../core/api/news-draft-api.service';
import { NewsApiService } from '../../core/api/news-api.service';
import { PublicationsApiService } from '../../core/api/publications-api.service';
import { ResearchUnitsApiService } from '../../core/api/research-units-api.service';
import { ResearchersApiService } from '../../core/api/researchers-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import {
  newsArticleStatusLabel,
  newsArticleStatusTone,
  publicationTypeLabel,
  researchUnitTypeLabel,
  validationStatusLabel
} from '../../shared/utils/display-labels';

@Component({
  selector: 'rip-admin-news-detail-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="page admin-news-detail-page">
      <rip-page-header
        [title]="articleId() === null ? 'Nueva noticia' : (currentArticle()?.title || 'Detalle de noticia')"
        [subtitle]="articleId() === null ? 'Prepara una pieza editorial para el portal y conectala con evidencia institucional.' : headerSubtitle()"
        eyebrow="Portal institucional"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
        @if (canArchive()) {
          <button mat-button type="button" [disabled]="workflowBusy()" (click)="archive()">Archivar</button>
        }
        @if (canPublish()) {
          <button mat-button type="button" [disabled]="workflowBusy()" (click)="publish()">Publicar</button>
        }
        <button mat-flat-button color="primary" type="button" [disabled]="newsForm.invalid || saving()" (click)="save()">
          Guardar
        </button>
      </rip-page-header>

      @if (feedbackMessage()) {
        <div class="feedback-banner" [class.feedback-error]="feedbackTone() === 'error'">{{ feedbackMessage() }}</div>
      }

      @if (loading()) {
        <rip-loading-state message="Cargando editor de noticias..." />
      } @else if (errorMessage()) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <rip-error-state [message]="errorMessage()" />
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="editor-layout">
          <div class="main-column">
            <mat-card appearance="outlined" class="summary-card">
              <mat-card-content>
                <div class="summary-head">
                  <div>
                    <p class="section-kicker">Estado editorial</p>
                    <h2>{{ newsForm.controls.title.value || 'Noticia sin titulo' }}</h2>
                    <p>{{ newsForm.controls.summary.value || 'Añade un resumen breve para situar la pieza en el portal.' }}</p>
                  </div>
                  <rip-status-chip [label]="statusLabel(newsForm.controls.status.value)" [tone]="statusTone(newsForm.controls.status.value)" />
                </div>

                <div class="summary-meta">
                  <span>{{ currentArticle()?.publishedAt ? ('Publicada ' + dateLabel(currentArticle()!.publishedAt!)) : 'Todavia no publicada' }}</span>
                  <span>{{ currentArticle() ? ('Actualizada ' + dateLabel(currentArticle()!.updatedAt)) : 'Sin guardar todavia' }}</span>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-content>
                <form class="form-shell" [formGroup]="newsForm">
                  <section class="form-section">
                    <div class="section-heading">
                      <h3>Contenido</h3>
                      <p>Texto principal para la portada del portal y la vista de detalle.</p>
                    </div>
                    <div class="section-grid">
                      <mat-form-field appearance="outline" class="wide-field">
                        <mat-label>Titulo</mat-label>
                        <input matInput formControlName="title">
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="wide-field">
                        <mat-label>Resumen</mat-label>
                        <textarea matInput rows="4" formControlName="summary"></textarea>
                      </mat-form-field>

                      <mat-form-field appearance="outline">
                        <mat-label>Estado</mat-label>
                        <mat-select formControlName="status">
                          @for (status of statusOptions; track status) {
                            <mat-option [value]="status" [disabled]="status === 'PUBLISHED' || status === 'ARCHIVED'">
                              {{ statusLabel(status) }}
                            </mat-option>
                          }
                        </mat-select>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="wide-field">
                        <mat-label>Cuerpo</mat-label>
                        <textarea matInput rows="14" formControlName="body"></textarea>
                      </mat-form-field>
                    </div>
                  </section>

                  <section class="form-section">
                    <div class="section-heading">
                      <h3>Imagen y accesibilidad</h3>
                      <p>Adjunta una imagen publica si existe y deja clara su descripcion alternativa.</p>
                    </div>
                    <div class="section-grid">
                      <mat-form-field appearance="outline" class="wide-field">
                        <mat-label>URL de imagen</mat-label>
                        <input matInput formControlName="imageUrl" placeholder="https://...">
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="wide-field">
                        <mat-label>Texto alternativo</mat-label>
                        <input matInput formControlName="imageAlt">
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="wide-field">
                        <mat-label>Imagen sugerida</mat-label>
                        <textarea matInput rows="4" formControlName="imageSuggestion"></textarea>
                      </mat-form-field>
                    </div>
                  </section>

                  <section class="form-section">
                    <div class="section-heading">
                      <h3>Relaciones</h3>
                      <p>Conecta la noticia con publicaciones, investigadores y unidades para enriquecer la navegacion.</p>
                    </div>
                    <div class="section-grid">
                      <mat-form-field appearance="outline" class="wide-field">
                        <mat-label>Publicaciones relacionadas</mat-label>
                        <mat-select formControlName="relatedPublicationIds" multiple>
                          @for (publication of publicationOptions(); track publication.id) {
                            <mat-option [value]="publication.id">{{ publicationOptionLabel(publication) }}</mat-option>
                          }
                        </mat-select>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="wide-field">
                        <mat-label>Investigadores relacionados</mat-label>
                        <mat-select formControlName="relatedResearcherIds" multiple>
                          @for (researcher of researcherOptions(); track researcher.id) {
                            <mat-option [value]="researcher.id">{{ researcherOptionLabel(researcher) }}</mat-option>
                          }
                        </mat-select>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="wide-field">
                        <mat-label>Unidades relacionadas</mat-label>
                        <mat-select formControlName="relatedUnitIds" multiple>
                          @for (unit of unitOptions(); track unit.id) {
                            <mat-option [value]="unit.id">{{ unitOptionLabel(unit) }}</mat-option>
                          }
                        </mat-select>
                      </mat-form-field>
                    </div>
                  </section>
                </form>
              </mat-card-content>
            </mat-card>
          </div>

          <aside class="side-column">
            <mat-card appearance="outlined">
              <mat-card-content>
                <div class="section-heading compact">
                  <h3>Generar borrador con IA</h3>
                  <p>Usa evidencia validada para proponer un primer texto editorial y una sugerencia visual.</p>
                </div>

                <form class="draft-form" [formGroup]="draftForm">
                  <mat-form-field appearance="outline">
                    <mat-label>Fuente</mat-label>
                    <mat-select formControlName="sourceType">
                      @for (sourceType of sourceTypeOptions; track sourceType) {
                        <mat-option [value]="sourceType">{{ sourceTypeLabel(sourceType) }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  @if (usesEntitySource()) {
                    <mat-form-field appearance="outline">
                      <mat-label>Registro base</mat-label>
                      <mat-select formControlName="sourceId">
                        <mat-option [value]="null">Selecciona un registro</mat-option>
                        @for (option of currentSourceOptions(); track option.id) {
                          <mat-option [value]="option.id">{{ option.label }}</mat-option>
                        }
                      </mat-select>
                    </mat-form-field>
                  } @else {
                    <mat-form-field appearance="outline">
                      <mat-label>{{ queryLabel() }}</mat-label>
                      <input matInput formControlName="query" [placeholder]="queryPlaceholder()">
                    </mat-form-field>
                  }

                  <mat-form-field appearance="outline">
                    <mat-label>Tono</mat-label>
                    <mat-select formControlName="tone">
                      @for (tone of toneOptions; track tone) {
                        <mat-option [value]="tone">{{ toneLabel(tone) }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <button mat-flat-button color="primary" type="button" [disabled]="draftBusy()" (click)="generateDraft()">
                    Generar borrador con IA
                  </button>
                </form>
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-content>
                <div class="section-heading compact">
                  <h3>Imagen sugerida</h3>
                  <p>Revisa la orientacion visual propuesta y decide si merece una imagen concreta para el portal.</p>
                </div>

                @if (draftResponse(); as draft) {
                  <div class="suggestion-block">
                    <p class="suggestion-copy">{{ draft.imageSuggestion || 'Sin sugerencia visual adicional.' }}</p>
                    <div class="suggestion-actions">
                      <button mat-button type="button" (click)="applySuggestedAlt(draft.imageAltSuggestion)">Usar texto alternativo sugerido</button>
                    </div>
                    <div class="support-list">
                      <span class="support-label">Texto alternativo sugerido</span>
                      <p>{{ draft.imageAltSuggestion || 'Sin texto alternativo sugerido.' }}</p>
                    </div>
                  </div>
                } @else if (newsForm.controls.imageSuggestion.value) {
                  <p class="suggestion-copy">{{ newsForm.controls.imageSuggestion.value }}</p>
                } @else {
                  <rip-empty-state
                    title="Sin sugerencia visual"
                    message="Genera un borrador o escribe una orientacion manual para la imagen del portal."
                  />
                }

                @if (newsForm.controls.imageUrl.value) {
                  <div class="image-preview">
                    <img [src]="newsForm.controls.imageUrl.value" [alt]="newsForm.controls.imageAlt.value || 'Vista previa de imagen'">
                  </div>
                }
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-content>
                <div class="section-heading compact">
                  <h3>Evidencias utilizadas</h3>
                  <p>Contexto empleado por la IA para proponer el borrador actual.</p>
                </div>

                @if (draftResponse()?.evidence?.length) {
                  <div class="evidence-list">
                    @for (item of draftResponse()!.evidence; track item.reference) {
                      <div class="evidence-card">
                        <span class="evidence-type">{{ item.entityType }}</span>
                        <strong>{{ item.label }}</strong>
                        <p>{{ item.value || 'Sin detalle adicional.' }}</p>
                      </div>
                    }
                  </div>

                  <details class="technical-details">
                    <summary>Ver datos tecnicos</summary>
                    <p>Sugerencia creada: #{{ draftResponse()!.createdSuggestionId }}</p>
                    <p>Tipo: {{ draftResponse()!.createdSuggestionType }}</p>
                  </details>
                } @else {
                  <rip-empty-state
                    title="Sin evidencias cargadas"
                    message="Las evidencias apareceran despues de generar un borrador con IA."
                  />
                }
              </mat-card-content>
            </mat-card>
          </aside>
        </div>
      }
    </section>
  `,
  styles: [`
    .admin-news-detail-page {
      gap: 20px;
    }

    .feedback-banner {
      padding: 14px 18px;
      border: 1px solid #c8dfcf;
      border-radius: 16px;
      background: #f2faf5;
      color: #1b6148;
      line-height: 1.55;
    }

    .feedback-error {
      border-color: #efc5c5;
      background: #fff7f7;
      color: #8e1f1f;
    }

    .editor-layout {
      display: grid;
      grid-template-columns: minmax(0, 1.35fr) minmax(360px, 0.85fr);
      gap: 24px;
      align-items: start;
    }

    .main-column,
    .side-column {
      display: grid;
      gap: 20px;
      min-width: 0;
    }

    .summary-head {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      align-items: start;
    }

    .summary-head h2,
    .section-heading h3 {
      margin: 0;
      color: #132235;
      line-height: 1.18;
    }

    .summary-head p,
    .section-heading p,
    .summary-meta {
      margin: 0;
      color: #607181;
      line-height: 1.6;
    }

    .summary-meta,
    .suggestion-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 14px;
    }

    .form-shell,
    .draft-form,
    .evidence-list {
      display: grid;
      gap: 20px;
    }

    .form-section {
      display: grid;
      gap: 16px;
      padding-top: 4px;
    }

    .form-section + .form-section {
      padding-top: 24px;
      border-top: 1px solid #e2e8ee;
    }

    .section-heading {
      display: grid;
      gap: 8px;
    }

    .section-heading.compact {
      margin-bottom: 16px;
    }

    .section-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }

    .wide-field {
      grid-column: 1 / -1;
    }

    .suggestion-block,
    .support-list {
      display: grid;
      gap: 12px;
    }

    .suggestion-copy,
    .support-list p,
    .evidence-card p {
      margin: 0;
      color: #607181;
      line-height: 1.65;
    }

    .support-label,
    .evidence-type {
      color: #31566a;
      font-size: 0.78rem;
      font-weight: 780;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .image-preview {
      margin-top: 18px;
      border-radius: 16px;
      overflow: hidden;
      background: #edf3f7;
    }

    .image-preview img {
      display: block;
      width: 100%;
      height: auto;
      object-fit: cover;
    }

    .evidence-card {
      display: grid;
      gap: 6px;
      padding: 14px;
      border: 1px solid #dce5ee;
      border-radius: 14px;
      background: #ffffff;
    }

    .evidence-card strong {
      color: #132235;
      line-height: 1.35;
    }

    .technical-details {
      margin-top: 16px;
      color: #607181;
    }

    .technical-details summary {
      cursor: pointer;
      font-weight: 720;
    }

    @media (max-width: 1240px) {
      .editor-layout {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 820px) {
      .section-grid {
        grid-template-columns: 1fr;
      }

      .summary-head {
        display: grid;
      }
    }
  `]
})
export class AdminNewsDetailPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly newsApi = inject(NewsApiService);
  private readonly newsDraftApi = inject(NewsDraftApiService);
  private readonly publicationsApi = inject(PublicationsApiService);
  private readonly researchersApi = inject(ResearchersApiService);
  private readonly researchUnitsApi = inject(ResearchUnitsApiService);
  readonly navigationContext = inject(NavigationContextService);

  readonly statusOptions: NewsArticleStatus[] = ['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'ARCHIVED'];
  readonly sourceTypeOptions: NewsDraftSourceType[] = ['PUBLICATION', 'RESEARCH_UNIT', 'RESEARCHER', 'TOPIC', 'CUSTOM_QUERY'];
  readonly toneOptions: NewsDraftTone[] = ['INSTITUTIONAL', 'OUTREACH'];

  readonly articleId = signal<number | null>(null);
  readonly currentArticle = signal<NewsArticle | null>(null);
  readonly publicationOptions = signal<PublicationSummary[]>([]);
  readonly researcherOptions = signal<ResearcherSummary[]>([]);
  readonly unitOptions = signal<ResearchUnit[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly workflowBusy = signal(false);
  readonly draftBusy = signal(false);
  readonly errorMessage = signal('');
  readonly feedbackMessage = signal('');
  readonly feedbackTone = signal<'success' | 'error'>('success');
  readonly draftResponse = signal<NewsDraftGenerateResponse | null>(null);
  readonly currentSourceOptions = computed(() => {
    switch (this.draftForm.controls.sourceType.value) {
      case 'PUBLICATION':
        return this.publicationOptions().map((publication) => ({
          id: publication.id,
          label: this.publicationOptionLabel(publication)
        }));
      case 'RESEARCHER':
        return this.researcherOptions().map((researcher) => ({
          id: researcher.id,
          label: this.researcherOptionLabel(researcher)
        }));
      case 'RESEARCH_UNIT':
        return this.unitOptions().map((unit) => ({
          id: unit.id,
          label: this.unitOptionLabel(unit)
        }));
      default:
        return [];
    }
  });

  readonly newsForm = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(255)] }),
    summary: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    body: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    status: new FormControl<NewsArticleStatus>('DRAFT', { nonNullable: true }),
    imageUrl: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(1000)] }),
    imageAlt: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
    imageSuggestion: new FormControl('', { nonNullable: true }),
    relatedPublicationIds: new FormControl<number[]>([], { nonNullable: true }),
    relatedResearcherIds: new FormControl<number[]>([], { nonNullable: true }),
    relatedUnitIds: new FormControl<number[]>([], { nonNullable: true })
  });

  readonly draftForm = new FormGroup({
    sourceType: new FormControl<NewsDraftSourceType>('PUBLICATION', { nonNullable: true }),
    sourceId: new FormControl<number | null>(null),
    query: new FormControl('', { nonNullable: true }),
    tone: new FormControl<NewsDraftTone>('INSTITUTIONAL', { nonNullable: true })
  });

  ngOnInit(): void {
    this.loadLookups();
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.loadArticle(this.parseArticleId(params.get('id')));
    });
    this.draftForm.controls.sourceType.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.draftForm.controls.sourceId.setValue(null);
        this.draftForm.controls.query.setValue('');
      });
  }

  headerSubtitle(): string {
    const article = this.currentArticle();
    if (!article) {
      return 'Prepara una pieza editorial para el portal y conectala con evidencia institucional.';
    }
    return article.publishedAt
      ? `Publicada el ${this.dateLabel(article.publishedAt)} y lista para seguir evolucionando con control editorial.`
      : 'Todavia no publicada. Revisa el texto, la imagen y las relaciones antes de decidir su salida al portal.';
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, '/admin/noticias', 'Volver a noticias').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, '/admin/noticias', 'Volver a noticias');
  }

  usesEntitySource(): boolean {
    return this.draftForm.controls.sourceType.value === 'PUBLICATION'
      || this.draftForm.controls.sourceType.value === 'RESEARCH_UNIT'
      || this.draftForm.controls.sourceType.value === 'RESEARCHER';
  }

  queryLabel(): string {
    return this.draftForm.controls.sourceType.value === 'TOPIC' ? 'Tema base' : 'Consulta base';
  }

  queryPlaceholder(): string {
    return this.draftForm.controls.sourceType.value === 'TOPIC'
      ? 'Ej. biomedicina, energia, salud digital'
      : 'Describe el enfoque editorial que quieres cubrir';
  }

  statusLabel(status: NewsArticleStatus): string {
    return newsArticleStatusLabel(status);
  }

  statusTone(status: NewsArticleStatus): 'neutral' | 'success' | 'warning' | 'info' {
    return newsArticleStatusTone(status);
  }

  sourceTypeLabel(sourceType: NewsDraftSourceType): string {
    const labels: Record<NewsDraftSourceType, string> = {
      PUBLICATION: 'Publicacion',
      RESEARCH_UNIT: 'Unidad',
      RESEARCHER: 'Investigador',
      TOPIC: 'Tema',
      CUSTOM_QUERY: 'Consulta libre'
    };
    return labels[sourceType];
  }

  toneLabel(tone: NewsDraftTone): string {
    return tone === 'OUTREACH' ? 'Divulgativo' : 'Institucional';
  }

  publicationOptionLabel(publication: PublicationSummary): string {
    const validation = validationStatusLabel(publication.validationStatus);
    return `${publication.title} (${publication.year || 's. f.'}) - ${publicationTypeLabel(publication.type)} - ${validation}`;
  }

  researcherOptionLabel(researcher: ResearcherSummary): string {
    const name = researcher.displayName || researcher.fullName;
    const affiliation = researcher.primaryAffiliationName || 'Sin afiliacion publica';
    return `${name} - ${affiliation} - ${validationStatusLabel(researcher.validationStatus)}`;
  }

  unitOptionLabel(unit: ResearchUnit): string {
    return `${unit.name} - ${researchUnitTypeLabel(unit.type)} - ${validationStatusLabel(unit.validationStatus)}`;
  }

  dateLabel(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  canPublish(): boolean {
    const article = this.currentArticle();
    return !!article && article.status !== 'PUBLISHED' && article.status !== 'ARCHIVED';
  }

  canArchive(): boolean {
    const article = this.currentArticle();
    return !!article && article.status !== 'ARCHIVED';
  }

  save(): void {
    if (this.newsForm.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.setFeedback('', 'success');
    const request = this.toRequest();
    const articleId = this.articleId();
    const operation = articleId === null
      ? this.newsApi.create(request)
      : this.newsApi.update(articleId, request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (article) => {
        this.saving.set(false);
        this.applyArticle(article);
        this.setFeedback('Los cambios se han guardado.', 'success');
        if (articleId === null) {
          void this.router.navigate(['/admin/noticias', article.id], {
            queryParams: this.preservedQueryParams()
          });
        }
      },
      error: () => {
        this.saving.set(false);
        this.setFeedback('No se pudo guardar la noticia.', 'error');
      }
    });
  }

  publish(): void {
    const article = this.currentArticle();
    if (!article || this.workflowBusy()) {
      return;
    }
    this.workflowBusy.set(true);
    this.newsApi.publish(article.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.workflowBusy.set(false);
          this.applyArticle(updated);
          this.setFeedback('La noticia se ha publicado.', 'success');
        },
        error: () => {
          this.workflowBusy.set(false);
          this.setFeedback('No se pudo publicar la noticia.', 'error');
        }
      });
  }

  archive(): void {
    const article = this.currentArticle();
    if (!article || this.workflowBusy()) {
      return;
    }
    this.workflowBusy.set(true);
    this.newsApi.archive(article.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.workflowBusy.set(false);
          this.applyArticle(updated);
          this.setFeedback('La noticia se ha archivado.', 'success');
        },
        error: () => {
          this.workflowBusy.set(false);
          this.setFeedback('No se pudo archivar la noticia.', 'error');
        }
      });
  }

  generateDraft(): void {
    if (this.draftBusy()) {
      return;
    }
    const sourceType = this.draftForm.controls.sourceType.value;
    const sourceId = this.draftForm.controls.sourceId.value;
    const query = this.draftForm.controls.query.value.trim();
    if (this.usesEntitySource() && sourceId === null) {
      this.setFeedback('Selecciona un registro base antes de generar el borrador.', 'error');
      return;
    }
    if (!this.usesEntitySource() && query.length === 0) {
      this.setFeedback('Escribe un tema o una consulta antes de generar el borrador.', 'error');
      return;
    }

    this.draftBusy.set(true);
    this.setFeedback('', 'success');
    this.newsDraftApi.generateDraft({
      sourceType,
      sourceId,
      query: query || null,
      tone: this.draftForm.controls.tone.value,
      relatedIds: {
        publicationIds: this.newsForm.controls.relatedPublicationIds.value,
        researcherIds: this.newsForm.controls.relatedResearcherIds.value,
        unitIds: this.newsForm.controls.relatedUnitIds.value
      }
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.draftBusy.set(false);
          this.draftResponse.set(response);
          this.newsForm.patchValue({
            title: response.suggestedTitle,
            summary: response.suggestedSummary,
            body: response.suggestedBody,
            imageSuggestion: response.imageSuggestion,
            imageAlt: this.newsForm.controls.imageAlt.value || response.imageAltSuggestion
          });
          this.setFeedback('Se ha generado un borrador con IA a partir de evidencia validada.', 'success');
        },
        error: () => {
          this.draftBusy.set(false);
          this.setFeedback('No se pudo generar el borrador con IA.', 'error');
        }
      });
  }

  applySuggestedAlt(value: string): void {
    if (!value) {
      return;
    }
    this.newsForm.controls.imageAlt.setValue(value);
    this.setFeedback('Se ha aplicado el texto alternativo sugerido.', 'success');
  }

  private loadLookups(): void {
    forkJoin({
      publications: this.publicationsApi.search({ size: 100, sortBy: 'createdAt', sortDirection: 'desc' }).pipe(
        map((result) => result.content),
        catchError(() => of([] as PublicationSummary[]))
      ),
      researchers: this.researchersApi.search({ size: 100, active: true }).pipe(
        map((result) => result.content),
        catchError(() => of([] as ResearcherSummary[]))
      ),
      units: this.researchUnitsApi.list().pipe(catchError(() => of([] as ResearchUnit[])))
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ publications, researchers, units }) => {
        this.publicationOptions.set(publications);
        this.researcherOptions.set(
          [...researchers].sort((left, right) =>
            (left.displayName || left.fullName).localeCompare(right.displayName || right.fullName, 'es')
          )
        );
        this.unitOptions.set([...units].sort((left, right) => left.name.localeCompare(right.name, 'es')));
      });
  }

  private loadArticle(articleId: number | null): void {
    this.articleId.set(articleId);
    this.loading.set(true);
    this.errorMessage.set('');
    this.setFeedback('', 'success');
    this.draftResponse.set(null);

    if (articleId === null) {
      this.currentArticle.set(null);
      this.resetForm();
      this.loading.set(false);
      return;
    }

    this.newsApi.getAdmin(articleId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (article) => {
          this.applyArticle(article);
          this.loading.set(false);
        },
        error: () => {
          this.currentArticle.set(null);
          this.loading.set(false);
          this.errorMessage.set('No se pudo cargar la noticia solicitada.');
        }
      });
  }

  private applyArticle(article: NewsArticle): void {
    this.currentArticle.set(article);
    this.newsForm.reset({
      title: article.title,
      summary: article.summary,
      body: article.body,
      status: article.status,
      imageUrl: article.imageUrl ?? '',
      imageAlt: article.imageAlt ?? '',
      imageSuggestion: article.imageSuggestion ?? '',
      relatedPublicationIds: article.relatedPublicationIds,
      relatedResearcherIds: article.relatedResearcherIds,
      relatedUnitIds: article.relatedUnitIds
    });
  }

  private resetForm(): void {
    this.newsForm.reset({
      title: '',
      summary: '',
      body: '',
      status: 'DRAFT',
      imageUrl: '',
      imageAlt: '',
      imageSuggestion: '',
      relatedPublicationIds: [],
      relatedResearcherIds: [],
      relatedUnitIds: []
    });
  }

  private toRequest(): NewsArticleRequest {
    const value = this.newsForm.getRawValue();
    return {
      title: value.title.trim(),
      summary: value.summary.trim(),
      body: value.body.trim(),
      status: value.status === 'PUBLISHED' || value.status === 'ARCHIVED' ? null : value.status,
      imageUrl: this.emptyToNull(value.imageUrl),
      imageAlt: this.emptyToNull(value.imageAlt),
      imageSuggestion: this.emptyToNull(value.imageSuggestion),
      relatedPublicationIds: value.relatedPublicationIds,
      relatedResearcherIds: value.relatedResearcherIds,
      relatedUnitIds: value.relatedUnitIds
    };
  }

  private parseArticleId(value: string | null): number | null {
    if (!value || value === 'new') {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private emptyToNull(value: string): string | null {
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
  }

  private preservedQueryParams(): Record<string, string> {
    const params = this.route.snapshot.queryParamMap;
    return {
      returnTo: params.get('returnTo') || '/admin/noticias',
      returnLabel: params.get('returnLabel') || 'Volver a noticias'
    };
  }

  private setFeedback(message: string, tone: 'success' | 'error'): void {
    this.feedbackMessage.set(message);
    this.feedbackTone.set(tone);
  }
}
