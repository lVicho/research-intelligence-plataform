import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { PageResponse, PortalNewsArticleSummary } from '../../core/api/api-models';
import { NewsApiService } from '../../core/api/news-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';

interface NewsFilters {
  text: string;
}

@Component({
  selector: 'rip-portal-news-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent
  ],
  template: `
    <section class="page portal-news-page">
      <rip-page-header
        title="Noticias"
        subtitle="Una seleccion editorial de actividad institucional validada, conectada con publicaciones, personas y unidades del portal."
        eyebrow="Portal publico"
      />

      <section class="surface-intro intro-band">
        <div class="intro-copy">
          <p class="section-kicker">Actualidad institucional</p>
          <h2>Historias breves para recorrer la investigacion desde un angulo mas editorial.</h2>
          <p>
            Cada noticia enlaza con evidencia visible en el portal para que puedas seguir la pista hacia personas,
            unidades y produccion cientifica relacionada.
          </p>
        </div>

        <form class="search-panel" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
          <mat-form-field appearance="outline">
            <mat-label>Buscar noticias</mat-label>
            <input matInput formControlName="text" placeholder="Titulo, resumen o tema institucional">
          </mat-form-field>
          <div class="search-actions">
            <button mat-flat-button color="primary" type="submit">Buscar</button>
            <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
          </div>
        </form>
      </section>

      @if (loading()) {
        <rip-loading-state message="Cargando noticias del portal..." />
      } @else if (errorMessage()) {
        <mat-card appearance="outlined">
          <mat-card-content class="state-card">
            <rip-error-state [message]="errorMessage()" />
            <div class="actions">
              <button mat-flat-button color="primary" type="button" (click)="reload()">Reintentar</button>
            </div>
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="results-meta">
          <p>{{ result().totalElements }} noticias visibles</p>
          @if (appliedFilters().text) {
            <span>Filtro actual: "{{ appliedFilters().text }}"</span>
          }
        </div>

        @if (result().content.length === 0) {
          <mat-card appearance="outlined">
            <mat-card-content>
              <rip-empty-state
                title="Sin noticias visibles"
                message="Todavia no hay noticias publicadas para esta busqueda en el portal."
              />
            </mat-card-content>
          </mat-card>
        } @else {
          <div class="news-grid">
            @for (article of result().content; track article.id) {
              <article class="news-card">
                <a
                  class="news-card-link"
                  [routerLink]="['/portal/noticias', article.id]"
                  [queryParams]="navigationContext.returnQueryParams('Volver a noticias')"
                >
                  <div class="news-image" [class.news-image-empty]="!article.imageUrl">
                    @if (article.imageUrl) {
                      <img [src]="article.imageUrl" [alt]="article.imageAlt || article.title">
                    } @else {
                      <span>{{ dateBadge(article) }}</span>
                    }
                  </div>

                  <div class="news-copy">
                    <p class="news-date">{{ dateLabel(article.publishedAt) }}</p>
                    <h3>{{ article.title }}</h3>
                    <p>{{ article.summary }}</p>
                    <span class="news-link-label">Leer noticia</span>
                  </div>
                </a>
              </article>
            }
          </div>

          <div class="pager">
            <button mat-button type="button" (click)="goToPage(result().page - 1)" [disabled]="result().page === 0">
              Anterior
            </button>
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
      }
    </section>
  `,
  styles: [`
    .portal-news-page {
      gap: 22px;
    }

    .intro-band {
      display: grid;
      grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
      gap: 24px;
      align-items: start;
    }

    .intro-copy,
    .search-panel {
      display: grid;
      gap: 12px;
      min-width: 0;
    }

    .intro-copy h2 {
      margin: 0;
      color: #142033;
      font-size: clamp(1.3rem, 1.8vw, 1.85rem);
      line-height: 1.15;
    }

    .intro-copy p:not(.section-kicker) {
      margin: 0;
      color: #5f7182;
      line-height: 1.65;
    }

    .search-panel {
      padding: 20px;
      border: 1px solid #dce5ee;
      border-radius: 18px;
      background: #ffffff;
    }

    .search-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }

    .results-meta {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 12px;
      color: #607181;
      font-size: 0.92rem;
    }

    .results-meta p,
    .results-meta span {
      margin: 0;
    }

    .news-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 20px;
    }

    .news-card {
      min-width: 0;
    }

    .news-card-link {
      display: grid;
      grid-template-rows: auto 1fr;
      min-height: 100%;
      border: 1px solid #dbe4eb;
      border-radius: 18px;
      overflow: hidden;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      box-shadow: 0 14px 30px rgba(20, 32, 51, 0.05);
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .news-card-link:hover {
      border-color: #a9c6d8;
      box-shadow: 0 18px 38px rgba(20, 32, 51, 0.1);
      transform: translateY(-2px);
    }

    .news-card-link:focus-visible {
      outline: 3px solid rgba(41, 91, 128, 0.18);
      outline-offset: 3px;
    }

    .news-image {
      aspect-ratio: 16 / 9;
      background: #edf3f7;
    }

    .news-image img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .news-image-empty {
      display: grid;
      place-items: center;
      background: linear-gradient(135deg, #12354c 0%, #44697e 100%);
      color: #ffffff;
    }

    .news-image-empty span {
      padding: 8px 14px;
      border: 1px solid rgba(255, 255, 255, 0.28);
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.12);
      font-size: 0.82rem;
      font-weight: 760;
    }

    .news-copy {
      display: grid;
      gap: 12px;
      padding: 18px;
      align-content: start;
    }

    .news-date,
    .news-link-label {
      margin: 0;
      font-size: 0.82rem;
      font-weight: 760;
    }

    .news-date {
      color: #5d7384;
      text-transform: uppercase;
    }

    .news-copy h3 {
      margin: 0;
      color: #132235;
      font-size: 1.08rem;
      line-height: 1.35;
    }

    .news-copy p:last-of-type {
      margin: 0;
      color: #5f7182;
      line-height: 1.65;
    }

    .news-link-label {
      color: #174d67;
    }

    .pager {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 16px;
      flex-wrap: wrap;
      color: #5f7182;
    }

    @media (max-width: 1080px) {
      .intro-band,
      .news-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class PortalNewsPageComponent implements OnInit {
  private readonly newsApi = inject(NewsApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly result = signal<PageResponse<PortalNewsArticleSummary>>(this.emptyPage());
  readonly appliedFilters = signal<NewsFilters>({ text: '' });
  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true })
  });
  readonly maxPageLabel = computed(() => Math.max(this.result().totalPages, 1));

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    const filters = this.appliedFilters();
    this.newsApi.searchPortal({
      text: filters.text || undefined,
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
          this.errorMessage.set('No se pudieron cargar las noticias publicas.');
        }
      });
  }

  applyFilters(): void {
    this.appliedFilters.set({
      text: this.filterForm.controls.text.value.trim()
    });
    this.result.update((result) => ({ ...result, page: 0 }));
    this.reload();
  }

  clearFilters(): void {
    this.filterForm.reset({ text: '' });
    this.appliedFilters.set({ text: '' });
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

  dateLabel(value: string | null): string {
    if (!value) {
      return 'Sin fecha de publicacion';
    }
    return new Intl.DateTimeFormat('es-ES', { dateStyle: 'long' }).format(new Date(value));
  }

  dateBadge(article: PortalNewsArticleSummary): string {
    if (!article.publishedAt) {
      return 'Portal';
    }
    return new Intl.DateTimeFormat('es-ES', { month: 'short', day: 'numeric' }).format(new Date(article.publishedAt));
  }

  private emptyPage(): PageResponse<PortalNewsArticleSummary> {
    return {
      content: [],
      page: 0,
      size: 9,
      totalElements: 0,
      totalPages: 0,
      last: true
    };
  }
}
