import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { catchError, forkJoin, map, of } from 'rxjs';

import {
  PortalNewsArticle,
  PortalPublicationDetail,
  PortalResearchUnitSummary,
  PortalResearcherSummary
} from '../../core/api/api-models';
import { NewsApiService } from '../../core/api/news-api.service';
import { PortalApiService } from '../../core/api/portal-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { publicationTypeLabel, researchUnitTypeLabel } from '../../shared/utils/display-labels';

interface RelatedPublicationCard {
  id: number;
  title: string;
  year: number | null;
  source: string | null;
  type: PortalPublicationDetail['type'];
}

interface RelatedResearcherCard {
  id: number;
  name: string;
  affiliation: string | null;
}

interface RelatedUnitCard {
  id: number;
  name: string;
  type: PortalResearchUnitSummary['type'];
  city: string | null;
  country: string | null;
}

@Component({
  selector: 'rip-portal-news-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="page portal-news-detail">
      <div class="detail-actions">
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
      </div>

      @if (loading()) {
        <rip-loading-state message="Cargando noticia del portal..." />
      } @else {
        @if (errorMessage()) {
          <mat-card appearance="outlined">
            <mat-card-content>
              <rip-error-state [message]="errorMessage()" />
            </mat-card-content>
          </mat-card>
        } @else {
          @if (article(); as currentArticle) {
            <section class="hero">
              <div class="hero-copy">
                <p class="eyebrow">Portal público</p>
                <p class="hero-date">{{ dateLabel(currentArticle.publishedAt) }}</p>
                <h1>{{ currentArticle.title }}</h1>
                <p class="summary">{{ currentArticle.summary }}</p>
                <div class="hero-tags">
                  @if (relatedUnits().length > 0) {
                    <rip-tag-chip [label]="relatedUnits()[0].name" tone="type" />
                  }
                  @if (relatedResearchers().length > 0) {
                    <rip-tag-chip [label]="relatedResearchers()[0].name" />
                  }
                </div>
              </div>

              <div class="hero-media" [class.hero-media-empty]="!currentArticle.imageUrl">
                @if (currentArticle.imageUrl) {
                  <img [src]="currentArticle.imageUrl" [alt]="currentArticle.imageAlt || currentArticle.title">
                } @else {
                  <span>Noticia institucional</span>
                }
              </div>
            </section>

            <div class="detail-layout">
              <mat-card appearance="outlined" class="story-card">
                <mat-card-content>
                  <div class="story-body">{{ currentArticle.body }}</div>
                </mat-card-content>
              </mat-card>

              <aside class="related-column">
                <mat-card appearance="outlined">
                  <mat-card-content>
                    <div class="section-head">
                      <p class="section-kicker">Publicaciones</p>
                      <h2>Relacionadas</h2>
                    </div>

                    @if (relatedPublications().length === 0) {
                      <rip-empty-state
                        title="Sin publicaciones relacionadas"
                        message="Esta noticia no enlaza todavía con publicaciones visibles."
                      />
                    } @else {
                      <div class="stack-list">
                        @for (publication of relatedPublications(); track publication.id) {
                          <a
                            class="related-card"
                            [routerLink]="['/portal/publicaciones', publication.id]"
                            [queryParams]="navigationContext.returnQueryParams('Volver a la noticia')"
                          >
                            <strong>{{ publication.title }}</strong>
                            <p>{{ publication.source || 'Repositorio institucional' }}</p>
                            <div class="chip-list">
                              <rip-tag-chip [label]="publicationTypeLabel(publication.type)" tone="type" />
                              <rip-tag-chip [label]="publication.year ? '' + publication.year : 's. f.'" />
                            </div>
                          </a>
                        }
                      </div>
                    }
                  </mat-card-content>
                </mat-card>

                <mat-card appearance="outlined">
                  <mat-card-content>
                    <div class="section-head">
                      <p class="section-kicker">Investigadores</p>
                      <h2>Perfiles citados</h2>
                    </div>

                    @if (relatedResearchers().length === 0) {
                      <rip-empty-state
                        title="Sin perfiles relacionados"
                        message="No hay investigadores públicos asociados a esta noticia."
                      />
                    } @else {
                      <div class="stack-list">
                        @for (researcher of relatedResearchers(); track researcher.id) {
                          <a
                            class="related-card"
                            [routerLink]="['/portal/investigadores', researcher.id]"
                            [queryParams]="navigationContext.returnQueryParams('Volver a la noticia')"
                          >
                            <strong>{{ researcher.name }}</strong>
                            <p>{{ researcher.affiliation || 'Afiliación pública pendiente de completar' }}</p>
                          </a>
                        }
                      </div>
                    }
                  </mat-card-content>
                </mat-card>

                <mat-card appearance="outlined">
                  <mat-card-content>
                    <div class="section-head">
                      <p class="section-kicker">Unidades</p>
                      <h2>Contexto institucional</h2>
                    </div>

                    @if (relatedUnits().length === 0) {
                      <rip-empty-state
                        title="Sin unidades relacionadas"
                        message="No hay unidades visibles asociadas a esta noticia."
                      />
                    } @else {
                      <div class="stack-list">
                        @for (unit of relatedUnits(); track unit.id) {
                          <a
                            class="related-card"
                            [routerLink]="['/portal/unidades', unit.id]"
                            [queryParams]="navigationContext.returnQueryParams('Volver a la noticia')"
                          >
                            <strong>{{ unit.name }}</strong>
                            <p>{{ unitLocation(unit) }}</p>
                            <rip-status-chip [label]="typeLabel(unit.type)" tone="info" />
                          </a>
                        }
                      </div>
                    }
                  </mat-card-content>
                </mat-card>
              </aside>
            </div>
          } @else {
            <mat-card appearance="outlined">
              <mat-card-content>
                <rip-empty-state
                  title="Noticia no disponible"
                  message="Esta noticia ya no está visible en el portal público."
                />
              </mat-card-content>
            </mat-card>
          }
        }
      }
    </section>
  `,
  styles: [`
    .portal-news-detail {
      gap: 22px;
    }

    .detail-actions {
      display: flex;
      justify-content: flex-start;
    }

    .hero {
      display: grid;
      grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
      gap: 28px;
      align-items: stretch;
      padding: clamp(24px, 4vw, 40px);
      border: 1px solid #dce4eb;
      border-radius: 22px;
      background: linear-gradient(180deg, #ffffff 0%, #f7fafc 100%);
      box-shadow: 0 20px 44px rgba(20, 32, 51, 0.07);
    }

    .hero-copy,
    .hero-media {
      min-width: 0;
    }

    .hero-copy {
      display: grid;
      gap: 14px;
      align-content: start;
    }

    .eyebrow,
    .hero-date,
    .section-kicker {
      margin: 0;
      color: #5f7485;
      font-size: 0.8rem;
      font-weight: 780;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    h1 {
      margin: 0;
      color: #132235;
      font-size: clamp(2rem, 4vw, 3.3rem);
      line-height: 1.05;
    }

    .summary {
      margin: 0;
      color: #4f6274;
      font-size: 1.06rem;
      line-height: 1.7;
      max-width: 58ch;
    }

    .hero-tags,
    .chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .hero-media {
      border-radius: 18px;
      overflow: hidden;
      background: #eaf1f5;
      min-height: 260px;
    }

    .hero-media img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .hero-media-empty {
      display: grid;
      place-items: center;
      background: linear-gradient(135deg, #17384c 0%, #54748a 100%);
      color: #ffffff;
      font-size: 0.92rem;
      font-weight: 760;
    }

    .detail-layout {
      display: grid;
      grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
      gap: 24px;
      align-items: start;
    }

    .story-card mat-card-content,
    .related-column {
      display: grid;
      gap: 20px;
    }

    .story-body {
      color: #203244;
      font-size: 1rem;
      line-height: 1.85;
      white-space: pre-line;
    }

    .section-head {
      display: grid;
      gap: 8px;
      margin-bottom: 14px;
    }

    .section-head h2 {
      margin: 0;
      color: #132235;
      font-size: 1.12rem;
      line-height: 1.2;
    }

    .stack-list {
      display: grid;
      gap: 12px;
    }

    .related-card {
      display: grid;
      gap: 8px;
      padding: 16px;
      border: 1px solid #dce5ee;
      border-radius: 16px;
      color: inherit;
      text-decoration: none;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .related-card:hover {
      border-color: #aac6d6;
      box-shadow: 0 14px 26px rgba(20, 32, 51, 0.08);
      transform: translateY(-1px);
    }

    .related-card strong {
      color: #132235;
      line-height: 1.35;
    }

    .related-card p {
      margin: 0;
      color: #5f7182;
      line-height: 1.55;
    }

    @media (max-width: 1120px) {
      .hero,
      .detail-layout {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class PortalNewsDetailPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly newsApi = inject(NewsApiService);
  private readonly portalApi = inject(PortalApiService);
  readonly navigationContext = inject(NavigationContextService);

  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly article = signal<PortalNewsArticle | null>(null);
  readonly relatedPublications = signal<RelatedPublicationCard[]>([]);
  readonly relatedResearchers = signal<RelatedResearcherCard[]>([]);
  readonly relatedUnits = signal<RelatedUnitCard[]>([]);
  readonly articleId = computed(() => {
    const value = Number(this.route.snapshot.paramMap.get('id'));
    return Number.isFinite(value) ? value : null;
  });

  ngOnInit(): void {
    const articleId = this.articleId();
    if (articleId === null) {
      this.loading.set(false);
      this.errorMessage.set('La noticia solicitada no es valida.');
      return;
    }

    this.newsApi.getPortal(articleId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (article) => {
          this.article.set(article);
          this.loading.set(false);
          this.loadRelated(article);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No se pudo cargar la noticia del portal.');
        }
      });
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, '/portal/noticias', 'Volver a noticias').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, '/portal/noticias', 'Volver a noticias');
  }

  dateLabel(value: string | null): string {
    if (!value) {
      return 'Sin fecha de publicación';
    }
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'long',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  publicationTypeLabel(type: PortalPublicationDetail['type']): string {
    return publicationTypeLabel(type);
  }

  typeLabel(type: PortalResearchUnitSummary['type']): string {
    return researchUnitTypeLabel(type);
  }

  unitLocation(unit: RelatedUnitCard): string {
    return [unit.city, unit.country].filter(Boolean).join(', ') || 'Ubicación pública no especificada';
  }

  private loadRelated(article: PortalNewsArticle): void {
    forkJoin({
      publications: this.loadPublicationCards(article.relatedPublicationIds),
      researchers: this.loadResearcherCards(article.relatedResearcherIds),
      units: this.loadUnitCards(article.relatedUnitIds)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ publications, researchers, units }) => {
        this.relatedPublications.set(publications);
        this.relatedResearchers.set(researchers);
        this.relatedUnits.set(units);
      });
  }

  private loadPublicationCards(ids: number[]) {
    if (ids.length === 0) {
      return of([] as RelatedPublicationCard[]);
    }
    return forkJoin(
      ids.map((id) =>
        this.portalApi.publication(id).pipe(
          map((publication) => ({
            id: publication.id,
            title: publication.title,
            year: publication.year,
            source: publication.source,
            type: publication.type
          })),
          catchError(() => of(null))
        )
      )
    ).pipe(map((items) => items.filter((item): item is RelatedPublicationCard => item !== null)));
  }

  private loadResearcherCards(ids: number[]) {
    if (ids.length === 0) {
      return of([] as RelatedResearcherCard[]);
    }
    return forkJoin(
      ids.map((id) =>
        this.portalApi.researcher(id).pipe(
          map((researcher) => ({
            id: researcher.id,
            name: researcher.displayName || researcher.fullName,
            affiliation: researcher.affiliations.find((item) => item.primaryAffiliation)?.researchUnitName
              ?? researcher.affiliations[0]?.researchUnitName
              ?? null
          })),
          catchError(() => of(null))
        )
      )
    ).pipe(map((items) => items.filter((item): item is RelatedResearcherCard => item !== null)));
  }

  private loadUnitCards(ids: number[]) {
    if (ids.length === 0) {
      return of([] as RelatedUnitCard[]);
    }
    return forkJoin(
      ids.map((id) =>
        this.portalApi.researchUnit(id).pipe(
          map((detail) => ({
            id: detail.unit.id,
            name: detail.unit.name,
            type: detail.unit.type,
            city: detail.unit.city,
            country: detail.unit.country
          })),
          catchError(() => of(null))
        )
      )
    ).pipe(map((items) => items.filter((item): item is RelatedUnitCard => item !== null)));
  }
}
