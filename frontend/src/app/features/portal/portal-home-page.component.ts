import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { catchError, forkJoin, map, of } from 'rxjs';

import {
  PortalCount,
  PortalNewsArticleSummary,
  PortalResearchUnitSummary,
  PortalSummary,
  PortalResearcherSummary,
  PublicationSummary
} from '../../core/api/api-models';
import { NewsApiService } from '../../core/api/news-api.service';
import { PortalApiService } from '../../core/api/portal-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { SectionCardComponent } from '../../shared/components/section-card.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { publicationTypeLabel, researchUnitTypeLabel } from '../../shared/utils/display-labels';

@Component({
  selector: 'rip-portal-home-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    EmptyStateComponent,
    SectionCardComponent,
    TagChipComponent
  ],
  template: `
    <section class="portal-home">
      <section class="portal-hero">
        <div class="hero-copy">
          <p class="eyebrow">Portal institucional</p>
          <h1>Explora la investigación de la universidad</h1>
          <p class="hero-text">
            Descubre publicaciones, unidades, investigadores y temas con una visión pública, validada y
            claramente institucional.
          </p>
          <div class="hero-actions">
            <a mat-flat-button color="primary" routerLink="/portal/publicaciones">Buscar publicaciones</a>
            <a mat-stroked-button routerLink="/portal/unidades">Explorar unidades</a>
            <a mat-button routerLink="/portal/guia-expertos">Encontrar expertos</a>
          </div>
        </div>

        <div class="hero-panel" aria-label="Resumen del portal">
          <span>Actividad conectada</span>
          <strong>{{ activityCount() }}</strong>
          <p>registros públicos enlazados entre personas, publicaciones, unidades y temas institucionales.</p>
          <a routerLink="/portal/asistente">Preguntar al asistente</a>
        </div>
      </section>

      <section class="metric-strip" aria-label="Resumen de actividad pública">
        <div>
          <strong>{{ publicationCount() }}</strong>
          <span>Publicaciones</span>
        </div>
        <div>
          <strong>{{ activityCount() }}</strong>
          <span>Actividad visible</span>
        </div>
        <div>
          <strong>{{ researcherCount() }}</strong>
          <span>Investigadores</span>
        </div>
        <div>
          <strong>{{ unitCount() }}</strong>
          <span>Unidades</span>
        </div>
      </section>

      <section class="featured-grid primary">
        <rip-section-card
          title="Unidades destacadas"
          subtitle="Puertas de entrada a facultades, institutos, departamentos, laboratorios y centros de la institución."
          eyebrow="Organización"
        >
          <div class="unit-grid">
            @for (unit of featuredUnits(); track unit.id) {
              <a class="unit-card" [routerLink]="['/portal/unidades', unit.id]" [queryParams]="navigationContext.returnQueryParams('Volver al inicio')">
                <span class="unit-type">{{ typeLabel(unit.type) }}</span>
                <strong>{{ unit.name }}</strong>
                <p>{{ locationLabel(unit) }}</p>
                @if (unit.shortName) {
                  <span class="unit-short">{{ unit.shortName }}</span>
                }
              </a>
            } @empty {
              <rip-empty-state title="Sin unidades destacadas" message="Aún no hay unidades activas disponibles en el portal." />
            }
          </div>
        </rip-section-card>

        <rip-section-card
          title="Publicaciones recientes"
          subtitle="Una selección inicial para entrar en la producción científica validada de la institución."
          eyebrow="Actividad reciente"
        >
          <div class="publication-list">
            @for (publication of recentPublications(); track publication.id) {
              <a class="publication-card" [routerLink]="['/publications', publication.id]" [queryParams]="navigationContext.returnQueryParams('Volver al inicio')">
                <span class="publication-year">{{ publication.year || 's. f.' }}</span>
                <div class="publication-main">
                  <strong>{{ publication.title }}</strong>
                  <p>{{ publication.source || publication.doi || 'Repositorio institucional' }}</p>
                  <div class="chip-list">
                    <rip-tag-chip [label]="publicationTypeLabel(publication.type)" tone="type" />
                    @for (topic of publication.topics.slice(0, 3); track topic) {
                      <rip-tag-chip [label]="topic" />
                    }
                  </div>
                </div>
              </a>
            } @empty {
              <rip-empty-state title="Sin publicaciones visibles" message="Todavía no hay publicaciones públicas para destacar." />
            }
          </div>
        </rip-section-card>
      </section>

      <section class="news-feature">
        <div class="news-feature-head">
          <div>
            <p class="section-kicker">Noticias destacadas</p>
            <h2>Lecturas cortas para seguir la actualidad cientifica del portal.</h2>
          </div>
          <a class="subtle-link" routerLink="/portal/noticias">Ver todas las noticias</a>
        </div>

        <div class="news-grid">
          @for (article of featuredNews(); track article.id) {
            <a
              class="news-card"
              [routerLink]="['/portal/noticias', article.id]"
              [queryParams]="navigationContext.returnQueryParams('Volver al inicio')"
            >
              <div class="news-image" [class.news-image-empty]="!article.imageUrl">
                @if (article.imageUrl) {
                  <img [src]="article.imageUrl" [alt]="article.imageAlt || article.title">
                } @else {
                  <span>{{ newsBadge(article) }}</span>
                }
              </div>

              <div class="news-copy">
                <p class="news-date">{{ newsDateLabel(article.publishedAt) }}</p>
                <strong>{{ article.title }}</strong>
                <p>{{ article.summary }}</p>
                @if (newsContextLabel(article); as label) {
                  <span class="news-context">{{ label }}</span>
                }
              </div>
            </a>
          } @empty {
            <rip-empty-state title="Sin noticias destacadas" message="Cuando existan noticias publicadas apareceran aqui." />
          }
        </div>
      </section>

      <section class="featured-grid secondary">
        <rip-section-card
          title="Temas principales"
          subtitle="Áreas de conocimiento que ayudan a recorrer la actividad científica del portal."
          eyebrow="Temas"
        >
          <div class="topic-cloud">
            @for (topic of topTopics(); track topic.label) {
              <button type="button" class="topic-pill" [routerLink]="['/portal/publicaciones']" [queryParams]="{ topic: topic.label }">
                <strong>{{ topic.label }}</strong>
                <span>{{ topic.count }}</span>
              </button>
            } @empty {
              <rip-empty-state title="Sin temas destacados" message="Los temas aparecerán cuando haya suficiente contenido clasificado." />
            }
          </div>
        </rip-section-card>

        <rip-section-card
          title="Guía de expertos"
          subtitle="Encuentra perfiles institucionales a partir de una pregunta, una línea temática o una necesidad concreta."
          eyebrow="Personas y evidencia"
        >
          <div class="expert-teaser">
            <div class="researcher-list">
              @for (researcher of visibleResearchers(); track researcher.id) {
                <a class="researcher-row" [routerLink]="['/portal/investigadores', researcher.id]" [queryParams]="navigationContext.returnQueryParams('Volver al inicio')">
                  <div>
                    <strong>{{ researcher.displayName || researcher.fullName }}</strong>
                    <p>{{ researcher.primaryAffiliationName || 'Afiliación pública pendiente de completar' }}</p>
                  </div>
                  @if (researcher.orcid) {
                    <span class="orcid-chip">ORCID</span>
                  }
                </a>
              } @empty {
                <rip-empty-state title="Sin investigadores visibles" message="Cuando existan perfiles públicos aparecerán aquí." />
              }
            </div>
            <a mat-stroked-button routerLink="/portal/guia-expertos">Abrir guía de expertos</a>
          </div>
        </rip-section-card>
      </section>

      <p class="trust-note">El portal muestra actividad pública revisada por la institución.</p>
    </section>
  `,
  styles: [`
    .portal-home {
      display: grid;
      gap: 28px;
    }

    .portal-hero {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(280px, 390px);
      gap: clamp(24px, 4vw, 54px);
      align-items: end;
      padding: clamp(34px, 5vw, 64px) clamp(26px, 5vw, 68px);
      border: 1px solid #d9dee6;
      border-radius: 16px;
      background: linear-gradient(135deg, #ffffff 0%, #f7f9fb 62%, #eef2f5 100%);
      box-shadow: 0 20px 48px rgba(20, 32, 51, 0.08);
    }

    .hero-copy,
    .hero-panel {
      display: grid;
      gap: 16px;
      min-width: 0;
      align-content: start;
    }

    .eyebrow,
    .hero-panel span {
      margin: 0;
      color: #58697c;
      font-size: 0.78rem;
      font-weight: 780;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    h1 {
      margin: 0;
      color: #102033;
      font-size: clamp(2.35rem, 4.8vw, 4rem);
      line-height: 1.04;
      font-weight: 760;
      max-width: 14ch;
    }

    .hero-text {
      max-width: 58ch;
      margin: 0;
      color: #4f5f72;
      font-size: 1.08rem;
      line-height: 1.65;
    }

    .hero-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }

    .hero-panel {
      padding: 24px;
      border: 1px solid #dfe5ec;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.82);
    }

    .hero-panel strong {
      color: #142033;
      font-size: clamp(2.2rem, 5vw, 3.25rem);
      font-weight: 760;
      line-height: 1;
    }

    .hero-panel p {
      margin: 0;
      color: #5f6c7d;
      line-height: 1.55;
    }

    .hero-panel a {
      width: fit-content;
      color: #15364a;
      font-weight: 760;
      text-decoration: none;
    }

    .hero-panel a:hover {
      text-decoration: underline;
    }

    .metric-strip {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      border: 1px solid #dfe5ec;
      border-radius: 12px;
      background: #ffffff;
      box-shadow: 0 12px 28px rgba(20, 32, 51, 0.05);
      overflow: hidden;
    }

    .metric-strip div {
      display: grid;
      gap: 4px;
      padding: 20px 24px;
      border-right: 1px solid #e6ebf0;
    }

    .metric-strip div:last-child {
      border-right: 0;
    }

    .metric-strip strong {
      color: #132133;
      font-size: 1.7rem;
      line-height: 1;
      font-weight: 780;
    }

    .metric-strip span {
      color: #5f6c7d;
      font-size: 0.86rem;
      font-weight: 720;
    }

    .featured-grid {
      display: grid;
      gap: 24px;
    }

    .news-feature {
      display: grid;
      gap: 18px;
    }

    .news-feature-head {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }

    .section-kicker {
      margin: 0 0 8px;
      color: #2f6f8f;
      font-size: 0.76rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .news-feature-head h2 {
      margin: 0;
      color: #132235;
      font-size: clamp(1.2rem, 1.9vw, 1.7rem);
      line-height: 1.15;
    }

    .subtle-link {
      color: #174d67;
      font-size: 0.92rem;
      font-weight: 760;
      text-decoration: none;
    }

    .subtle-link:hover {
      text-decoration: underline;
    }

    .featured-grid.primary {
      grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
    }

    .featured-grid.secondary {
      grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
    }

    .news-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 18px;
    }

    .publication-list,
    .researcher-list {
      display: grid;
      gap: 14px;
    }

    .publication-card,
    .researcher-row,
    .unit-card,
    .news-card {
      color: inherit;
      text-decoration: none;
    }

    .publication-card {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      gap: 14px;
      padding: 18px 0;
      border-bottom: 1px solid #e5e9ee;
      background: #ffffff;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .publication-card:last-child {
      border-bottom: 0;
    }

    .news-card {
      display: grid;
      grid-template-rows: auto 1fr;
      min-height: 100%;
      border: 1px solid #dbe4eb;
      border-radius: 18px;
      overflow: hidden;
      background: #ffffff;
      box-shadow: 0 14px 28px rgba(20, 32, 51, 0.05);
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .publication-card:hover,
    .researcher-row:hover,
    .unit-card:hover,
    .news-card:hover,
    .topic-pill:hover {
      border-color: #9db8c7;
      box-shadow: 0 14px 28px rgba(20, 32, 51, 0.07);
      transform: translateY(-2px);
    }

    .publication-card:focus-visible,
    .researcher-row:focus-visible,
    .unit-card:focus-visible,
    .news-card:focus-visible,
    .topic-pill:focus-visible,
    .hero-panel a:focus-visible {
      outline: 3px solid rgba(41, 91, 128, 0.18);
      outline-offset: 3px;
    }

    .news-image {
      aspect-ratio: 16 / 9;
      background: #ebf1f5;
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
      background: linear-gradient(135deg, #17384c 0%, #56768a 100%);
      color: #ffffff;
    }

    .news-image-empty span {
      padding: 8px 12px;
      border: 1px solid rgba(255, 255, 255, 0.24);
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.12);
      font-size: 0.8rem;
      font-weight: 780;
    }

    .news-copy {
      display: grid;
      gap: 10px;
      padding: 18px;
      align-content: start;
    }

    .news-date,
    .news-context {
      margin: 0;
      font-size: 0.8rem;
      font-weight: 760;
    }

    .news-date {
      color: #5f7485;
      text-transform: uppercase;
    }

    .news-copy strong {
      color: #132235;
      font-size: 1.02rem;
      line-height: 1.35;
    }

    .news-copy p:last-of-type {
      margin: 0;
      color: #5f7182;
      line-height: 1.65;
    }

    .news-context {
      color: #174d67;
    }

    .publication-year {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 60px;
      height: 34px;
      border-radius: 999px;
      background: #eaf2f7;
      color: #31566a;
      font-size: 0.8rem;
      font-weight: 780;
    }

    .publication-main {
      display: grid;
      gap: 8px;
      min-width: 0;
    }

    .publication-main strong,
    .unit-card strong,
    .researcher-row strong {
      color: #142033;
      font-size: 1rem;
      line-height: 1.35;
    }

    .publication-main p,
    .unit-card p,
    .researcher-row p {
      margin: 0;
      color: #657587;
      line-height: 1.5;
    }

    .chip-list,
    .topic-cloud {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .topic-pill {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      padding: 10px 14px;
      border: 1px solid #dbe4ea;
      border-radius: 999px;
      background: #ffffff;
      color: #233044;
      cursor: pointer;
      font: inherit;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .topic-pill strong {
      font-size: 0.88rem;
      font-weight: 720;
    }

    .topic-pill span {
      color: #6f7f8e;
      font-size: 0.82rem;
      font-weight: 760;
    }

    .unit-grid,
    .expert-teaser {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 14px;
    }

    .expert-teaser {
      grid-template-columns: 1fr;
    }

    .unit-card,
    .researcher-row {
      display: grid;
      gap: 10px;
      padding: 20px;
      border: 1px solid #e0e8ee;
      border-radius: 12px;
      background: #ffffff;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    .unit-type,
    .unit-short,
    .orcid-chip {
      width: fit-content;
      padding: 4px 9px;
      border-radius: 999px;
      font-size: 0.76rem;
      font-weight: 760;
    }

    .unit-type {
      background: #eef6f3;
      color: #28624a;
    }

    .unit-short {
      background: #eef4f7;
      color: #31566a;
    }

    .orcid-chip {
      background: #f5f7fa;
      color: #5b6b7a;
    }

    .trust-note {
      margin: 0;
      color: #6d7785;
      font-size: 0.9rem;
      line-height: 1.5;
      text-align: center;
    }

    @media (max-width: 1080px) {
      .portal-hero,
      .featured-grid.primary,
      .featured-grid.secondary,
      .news-grid {
        grid-template-columns: 1fr;
      }

      .metric-strip {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .metric-strip div:nth-child(2) {
        border-right: 0;
      }
    }

    @media (max-width: 720px) {
      .portal-hero {
        min-width: 0;
        padding: 24px;
        border-radius: 14px;
      }

      .hero-text,
      .hero-panel p {
        max-width: 28ch;
        overflow-wrap: anywhere;
      }

      .hero-actions {
        display: grid;
        justify-items: start;
      }

      .hero-actions a {
        max-width: 100%;
      }

      .unit-grid,
      .publication-card,
      .news-grid,
      .metric-strip {
        grid-template-columns: 1fr;
      }

      .metric-strip div {
        border-right: 0;
        border-bottom: 1px solid #e6ebf0;
      }

      .metric-strip div:last-child {
        border-bottom: 0;
      }

      h1 {
        max-width: none;
      }
    }
  `]
})
export class PortalHomePageComponent implements OnInit {
  private readonly portalApi = inject(PortalApiService);
  private readonly newsApi = inject(NewsApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly summary = signal<PortalSummary>(this.emptySummary());
  readonly visibleResearchers = signal<PortalResearcherSummary[]>([]);
  readonly featuredNews = signal<PortalNewsArticleSummary[]>([]);
  readonly newsResearcherLabels = signal<Record<number, string>>({});
  readonly newsUnitLabels = signal<Record<number, string>>({});

  readonly publicationCount = computed(() => this.summary().totalValidatedPublications);
  readonly activityCount = computed(() => this.summary().totalValidatedActivities);
  readonly researcherCount = computed(() => this.summary().totalPublicResearchers);
  readonly unitCount = computed(() => this.summary().totalPublicResearchUnits);
  readonly recentPublications = computed(() => this.summary().recentValidatedPublications.slice(0, 4));
  readonly featuredUnits = computed(() => this.summary().featuredResearchUnits.slice(0, 4));
  readonly topTopics = computed(() =>
    this.summary().topTopics
      .slice(0, 8)
      .map((topic) => ({
        label: topic.name,
        count: topic.count
      }))
  );

  ngOnInit(): void {
    this.portalApi.summary()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => of(this.emptySummary()))
      )
      .subscribe((summary) => this.summary.set(summary));

    this.portalApi.researchers({ size: 5 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => this.visibleResearchers.set(result.content),
        error: () => this.visibleResearchers.set([])
      });

    this.newsApi.searchPortal({ size: 3 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const articles = result.content.slice(0, 3);
          this.featuredNews.set(articles);
          this.loadNewsContextLabels(articles);
        },
        error: () => {
          this.featuredNews.set([]);
          this.newsResearcherLabels.set({});
          this.newsUnitLabels.set({});
        }
      });
  }

  publicationTypeLabel(type: PublicationSummary['type']): string {
    return publicationTypeLabel(type);
  }

  typeLabel(type: PortalResearchUnitSummary['type']): string {
    return researchUnitTypeLabel(type);
  }

  locationLabel(unit: PortalResearchUnitSummary): string {
    return [unit.city, unit.country].filter(Boolean).join(', ') || 'Ubicación pública no especificada';
  }

  newsDateLabel(value: string | null): string {
    if (!value) {
      return 'Sin fecha de publicacion';
    }
    return new Intl.DateTimeFormat('es-ES', { dateStyle: 'long' }).format(new Date(value));
  }

  newsBadge(article: PortalNewsArticleSummary): string {
    if (!article.publishedAt) {
      return 'Portal';
    }
    return new Intl.DateTimeFormat('es-ES', { month: 'short', day: 'numeric' }).format(new Date(article.publishedAt));
  }

  newsContextLabel(article: PortalNewsArticleSummary): string {
    const unitLabel = article.relatedUnitIds
      .map((id) => this.newsUnitLabels()[id])
      .find((label): label is string => !!label);
    if (unitLabel) {
      return unitLabel;
    }
    return article.relatedResearcherIds
      .map((id) => this.newsResearcherLabels()[id])
      .find((label): label is string => !!label)
      ?? '';
  }

  private emptySummary(): PortalSummary {
    return {
      totalValidatedPublications: 0,
      totalValidatedActivities: 0,
      totalPublicResearchers: 0,
      totalPublicResearchUnits: 0,
      topTopics: [] as PortalCount[],
      recentValidatedPublications: [],
      featuredResearchUnits: [],
      visibilityScope: 'PUBLIC_VALIDATED',
      validationFilterApplied: true
    };
  }

  private loadNewsContextLabels(articles: PortalNewsArticleSummary[]): void {
    const unitIds = Array.from(new Set(articles.flatMap((article) => article.relatedUnitIds.slice(0, 1))));
    const researcherIds = Array.from(new Set(articles.flatMap((article) => article.relatedResearcherIds.slice(0, 1))));

    forkJoin({
      units: unitIds.length === 0
        ? of([] as Array<readonly [number, string] | null>)
        : forkJoin(unitIds.map((id) =>
          this.portalApi.researchUnit(id).pipe(
            map((detail) => [id, detail.unit.name] as const),
            catchError(() => of(null))
          )
        )),
      researchers: researcherIds.length === 0
        ? of([] as Array<readonly [number, string] | null>)
        : forkJoin(researcherIds.map((id) =>
          this.portalApi.researcher(id).pipe(
            map((researcher) => [id, researcher.displayName || researcher.fullName] as const),
            catchError(() => of(null))
          )
        ))
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ units, researchers }) => {
        this.newsUnitLabels.set(this.toLabelMap(units));
        this.newsResearcherLabels.set(this.toLabelMap(researchers));
      });
  }

  private toLabelMap(entries: Array<readonly [number, string] | null>): Record<number, string> {
    return entries.reduce<Record<number, string>>((accumulator, entry) => {
      if (entry) {
        accumulator[entry[0]] = entry[1];
      }
      return accumulator;
    }, {});
  }
}
