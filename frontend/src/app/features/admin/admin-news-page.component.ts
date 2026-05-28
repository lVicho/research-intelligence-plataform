import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { NewsArticle, NewsArticleStatus, PageResponse } from '../../core/api/api-models';
import { NewsApiService } from '../../core/api/news-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { newsArticleStatusLabel, newsArticleStatusTone } from '../../shared/utils/display-labels';

interface AdminNewsFilters {
  text: string;
  status: NewsArticleStatus | 'all';
}

@Component({
  selector: 'rip-admin-news-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
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
    <section class="page admin-news-page">
      <rip-page-header
        title="Noticias"
        subtitle="Gestiona borradores editoriales del portal, revisa su relacion con evidencia institucional y controla su publicacion."
        eyebrow="Portal institucional"
      >
        <a
          mat-flat-button
          color="primary"
          [routerLink]="['/admin/noticias/new']"
          [queryParams]="navigationContext.returnQueryParams('Volver a noticias')"
        >
          Nueva noticia
        </a>
      </rip-page-header>

      <section class="surface-intro intro-band">
        <div class="intro-copy">
          <p class="section-kicker">Operacion editorial</p>
          <h2>Una bandeja para redactar, revisar y publicar historias conectadas con el conocimiento validado.</h2>
          <p>
            Las noticias publicadas alimentan el portal publico. Los borradores, las sugerencias de IA y los cambios
            de imagen permanecen aqui hasta que una persona decida publicarlos o archivarlos.
          </p>
        </div>

        <div class="summary-strip">
          <span class="summary-chip">
            <strong>{{ result().totalElements }}</strong>
            <span>noticias cargadas</span>
          </span>
          <span class="summary-chip">
            <strong>{{ publishedCount() }}</strong>
            <span>publicadas</span>
          </span>
          <span class="summary-chip">
            <strong>{{ draftCount() }}</strong>
            <span>en trabajo</span>
          </span>
        </div>
      </section>

      <mat-card appearance="outlined">
        <mat-card-content>
          <div class="section-header compact-header">
            <div>
              <p class="section-kicker">Filtros</p>
              <h2>Acotar la bandeja editorial</h2>
              <p>Filtra por estado o texto para entrar rapido a la noticia que necesitas editar.</p>
            </div>
            <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
          </div>

          <form class="form-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline" class="search-field">
              <mat-label>Buscar noticia</mat-label>
              <input matInput formControlName="text" placeholder="Titulo, resumen o enfoque editorial">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Estado</mat-label>
              <mat-select formControlName="status">
                <mat-option value="all">Todos</mat-option>
                @for (status of statusOptions; track status) {
                  <mat-option [value]="status">{{ statusLabel(status) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <div class="actions filter-actions">
              <button mat-flat-button color="primary" type="submit">Aplicar filtros</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      @if (feedbackMessage()) {
        <div class="feedback-banner">{{ feedbackMessage() }}</div>
      }

      @if (loading()) {
        <rip-loading-state message="Cargando noticias internas..." />
      } @else if (errorMessage()) {
        <mat-card appearance="outlined">
          <mat-card-content class="state-card">
            <rip-error-state [message]="errorMessage()" />
            <div class="actions">
              <button mat-flat-button color="primary" type="button" (click)="reload()">Reintentar</button>
            </div>
          </mat-card-content>
        </mat-card>
      } @else if (result().content.length === 0) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <rip-empty-state
              title="Sin noticias para estos filtros"
              message="Prueba otra combinacion de estado o texto, o crea una nueva noticia para iniciar el flujo editorial."
            />
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="news-list">
          @for (article of result().content; track article.id) {
            <mat-card appearance="outlined" class="news-row">
              <mat-card-content>
                <div class="news-row-topline">
                  <div class="chip-list">
                    <rip-status-chip [label]="statusLabel(article.status)" [tone]="statusTone(article.status)" />
                    @if (article.imageSuggestion) {
                      <rip-tag-chip label="Imagen sugerida" tone="type" />
                    }
                  </div>
                  <span class="row-date">{{ article.publishedAt ? dateLabel(article.publishedAt) : 'Sin publicar' }}</span>
                </div>

                <div class="news-row-main">
                  <div class="news-copy">
                    <strong>{{ article.title }}</strong>
                    <p>{{ article.summary }}</p>
                    <div class="news-meta">
                      <span>{{ relationCountLabel(article) }}</span>
                      <span>Actualizada {{ dateLabel(article.updatedAt) }}</span>
                    </div>
                  </div>

                  <div class="row-actions">
                    <a
                      mat-button
                      [routerLink]="['/admin/noticias', article.id]"
                      [queryParams]="navigationContext.returnQueryParams('Volver a noticias')"
                    >
                      Editar
                    </a>
                    @if (canPublish(article)) {
                      <button
                        mat-flat-button
                        color="primary"
                        type="button"
                        [disabled]="busyArticleId() === article.id"
                        (click)="publish(article)"
                      >
                        Publicar
                      </button>
                    }
                    @if (canArchive(article)) {
                      <button
                        mat-button
                        type="button"
                        [disabled]="busyArticleId() === article.id"
                        (click)="archive(article)"
                      >
                        Archivar
                      </button>
                    }
                  </div>
                </div>
              </mat-card-content>
            </mat-card>
          }
        </div>

        <div class="pager">
          <button mat-button type="button" (click)="goToPage(result().page - 1)" [disabled]="result().page === 0">Anterior</button>
          <span>Pagina {{ result().page + 1 }} de {{ maxPageLabel() }}</span>
          <button
            mat-button
            type="button"
            (click)="goToPage(result().page + 1)"
            [disabled]="result().last || result().totalPages === 0"
          >
            Siguiente
          </button>
        </div>
      }
    </section>
  `,
  styles: [`
    .admin-news-page {
      gap: 20px;
    }

    .intro-band {
      display: grid;
      grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
      gap: 22px;
      align-items: center;
    }

    .intro-copy {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .intro-copy h2,
    .compact-header h2 {
      margin: 0;
      color: #142033;
      line-height: 1.18;
    }

    .intro-copy p:not(.section-kicker),
    .compact-header p:not(.section-kicker) {
      margin: 0;
      color: #5f7182;
      line-height: 1.65;
    }

    .summary-strip {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px;
    }

    .summary-chip {
      display: grid;
      gap: 6px;
      padding: 18px;
      border: 1px solid #dce5ee;
      border-radius: 18px;
      background: #ffffff;
    }

    .summary-chip strong {
      color: #132235;
      font-size: 1.55rem;
      line-height: 1;
    }

    .summary-chip span:last-child {
      color: #607181;
      font-size: 0.84rem;
      font-weight: 720;
    }

    .search-field {
      grid-column: span 2;
    }

    .filter-actions {
      align-self: center;
    }

    .feedback-banner {
      padding: 14px 18px;
      border: 1px solid #c8dfcf;
      border-radius: 16px;
      background: #f2faf5;
      color: #1b6148;
      line-height: 1.55;
    }

    .news-list {
      display: grid;
      gap: 16px;
    }

    .news-row mat-card-content {
      display: grid;
      gap: 16px;
    }

    .chip-list,
    .news-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 12px;
    }

    .news-row-topline,
    .news-row-main {
      display: flex;
      justify-content: space-between;
      gap: 18px;
      align-items: start;
    }

    .news-copy {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .news-copy strong {
      color: #132235;
      font-size: 1.08rem;
      line-height: 1.35;
    }

    .news-copy p,
    .news-meta,
    .row-date {
      margin: 0;
      color: #607181;
      line-height: 1.55;
    }

    .row-actions {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }

    .pager {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 16px;
      flex-wrap: wrap;
      color: #607181;
    }

    @media (max-width: 1180px) {
      .intro-band,
      .summary-strip {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 820px) {
      .search-field {
        grid-column: auto;
      }

      .news-row-topline,
      .news-row-main {
        display: grid;
      }

      .row-actions {
        justify-content: flex-start;
      }
    }
  `]
})
export class AdminNewsPageComponent implements OnInit {
  private readonly newsApi = inject(NewsApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly statusOptions: NewsArticleStatus[] = ['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'ARCHIVED'];
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly feedbackMessage = signal('');
  readonly busyArticleId = signal<number | null>(null);
  readonly result = signal<PageResponse<NewsArticle>>(this.emptyPage());
  readonly appliedFilters = signal<AdminNewsFilters>({
    text: '',
    status: 'all'
  });
  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    status: new FormControl<NewsArticleStatus | 'all'>('all', { nonNullable: true })
  });
  readonly maxPageLabel = computed(() => Math.max(this.result().totalPages, 1));
  readonly publishedCount = computed(() => this.result().content.filter((article) => article.status === 'PUBLISHED').length);
  readonly draftCount = computed(() => this.result().content.filter((article) => article.status !== 'PUBLISHED').length);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.feedbackMessage.set('');
    const filters = this.appliedFilters();
    this.newsApi.searchAdmin({
      text: filters.text || undefined,
      status: filters.status === 'all' ? undefined : filters.status,
      page: this.result().page,
      size: this.result().size
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
        },
        error: () => {
          this.result.set(this.emptyPage());
          this.loading.set(false);
          this.errorMessage.set('No se pudo cargar la bandeja de noticias.');
        }
      });
  }

  applyFilters(): void {
    this.appliedFilters.set(this.filterForm.getRawValue());
    this.result.update((result) => ({ ...result, page: 0 }));
    this.reload();
  }

  clearFilters(): void {
    const defaults: AdminNewsFilters = { text: '', status: 'all' };
    this.filterForm.reset(defaults);
    this.appliedFilters.set(defaults);
    this.result.update((result) => ({ ...result, page: 0 }));
    this.reload();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.result().totalPages) {
      return;
    }
    this.result.update((result) => ({ ...result, page }));
    this.reload();
  }

  statusLabel(status: NewsArticleStatus): string {
    return newsArticleStatusLabel(status);
  }

  statusTone(status: NewsArticleStatus): 'neutral' | 'success' | 'warning' | 'info' {
    return newsArticleStatusTone(status);
  }

  dateLabel(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  relationCountLabel(article: NewsArticle): string {
    const publicationCount = article.relatedPublicationIds.length;
    const researcherCount = article.relatedResearcherIds.length;
    const unitCount = article.relatedUnitIds.length;
    return `${publicationCount} publicaciones, ${researcherCount} investigadores, ${unitCount} unidades`;
  }

  canPublish(article: NewsArticle): boolean {
    return article.status !== 'PUBLISHED' && article.status !== 'ARCHIVED';
  }

  canArchive(article: NewsArticle): boolean {
    return article.status !== 'ARCHIVED';
  }

  publish(article: NewsArticle): void {
    if (!this.canPublish(article) || this.busyArticleId() !== null) {
      return;
    }
    this.busyArticleId.set(article.id);
    this.newsApi.publish(article.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busyArticleId.set(null);
          this.feedbackMessage.set('La noticia se ha publicado y ya puede mostrarse en el portal.');
          this.reload();
        },
        error: () => {
          this.busyArticleId.set(null);
          this.feedbackMessage.set('No se pudo publicar la noticia.');
        }
      });
  }

  archive(article: NewsArticle): void {
    if (!this.canArchive(article) || this.busyArticleId() !== null) {
      return;
    }
    this.busyArticleId.set(article.id);
    this.newsApi.archive(article.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busyArticleId.set(null);
          this.feedbackMessage.set('La noticia se ha archivado.');
          this.reload();
        },
        error: () => {
          this.busyArticleId.set(null);
          this.feedbackMessage.set('No se pudo archivar la noticia.');
        }
      });
  }

  private emptyPage(): PageResponse<NewsArticle> {
    return {
      content: [],
      page: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
      last: true
    };
  }
}
