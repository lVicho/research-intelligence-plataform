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
            Consulta unidades, investigadores, publicaciones y líneas de conocimiento a partir de información
            pública revisada por la institución.
          </p>
          <div class="hero-actions">
            <a mat-flat-button color="primary" routerLink="/portal/publicaciones">Buscar publicaciones</a>
            <a mat-stroked-button routerLink="/portal/unidades">Explorar unidades</a>
            <a mat-stroked-button routerLink="/portal/guia-expertos">Encontrar expertos</a>
          </div>
        </div>

        <div class="hero-visual" aria-label="Entradas principales del portal">
          <span class="hero-visual-label">Descubrimiento público</span>
          <div class="hero-paths">
            <a routerLink="/portal/unidades">
              <strong>Unidades</strong>
              <span>Departamentos, institutos y grupos.</span>
            </a>
            <a routerLink="/portal/investigadores">
              <strong>Investigadores</strong>
              <span>Perfiles, afiliaciones y temas.</span>
            </a>
            <a routerLink="/portal/publicaciones">
              <strong>Publicaciones</strong>
              <span>Producción científica validada.</span>
            </a>
          </div>
          <p>El portal muestra actividad pública revisada por la institución.</p>
        </div>
      </section>

      <section class="discovery-section" aria-labelledby="portal-discovery-title">
        <div class="section-heading">
          <p class="section-kicker">Accesos principales</p>
          <h2 id="portal-discovery-title">Empieza por el tipo de información que quieres explorar.</h2>
          <p>La página inicial funciona como directorio público de las secciones principales del portal.</p>
        </div>

        <div class="discovery-grid">
          @for (card of discoveryCards; track card.title) {
            <a class="discovery-card" [routerLink]="card.route">
              <span class="discovery-marker">{{ card.marker }}</span>
              <strong>{{ card.title }}</strong>
              <p>{{ card.description }}</p>
              <span class="discovery-action">{{ card.action }}</span>
            </a>
          }
        </div>
      </section>

      <section class="metric-strip" aria-label="Resumen de actividad pública">
        <div>
          <strong>{{ publicationCount() }}</strong>
          <span>Publicaciones</span>
        </div>
        <div>
          <strong>{{ researcherCount() }}</strong>
          <span>Investigadores</span>
        </div>
        <div>
          <strong>{{ unitCount() }}</strong>
          <span>Unidades</span>
        </div>
        <div>
          <strong>{{ topicCount() }}</strong>
          <span>Temas</span>
        </div>
      </section>

      @if (featuredNews().length > 0) {
        <section class="news-feature">
          <div class="news-feature-head">
            <div>
              <p class="section-kicker">Noticias destacadas</p>
              <h2>Lecturas breves para seguir la actualidad científica del portal.</h2>
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
                  <span class="news-link-label">Leer noticia</span>
                </div>
              </a>
            }
          </div>
        </section>
      }

      <section class="featured-grid primary" aria-label="Unidades y publicaciones destacadas">
        <rip-section-card
          title="Unidades destacadas"
          subtitle="Puntos de entrada a la estructura investigadora de la institución."
          eyebrow="Organización"
        >
          <div class="unit-list">
            @for (unit of featuredUnits(); track unit.id) {
              <a class="unit-highlight" [routerLink]="['/portal/unidades', unit.id]" [queryParams]="navigationContext.returnQueryParams('Volver al inicio')">
                <div>
                  <strong>{{ unit.name }}</strong>
                  <p>{{ locationLabel(unit) }}</p>
                </div>
                <span>{{ unit.shortName || typeLabel(unit.type) }}</span>
              </a>
            } @empty {
              <rip-empty-state title="Sin unidades destacadas" message="Aún no hay unidades activas disponibles en el portal." />
            }
          </div>
          <a class="section-action" routerLink="/portal/unidades">Ver unidades</a>
        </rip-section-card>

        <rip-section-card
          title="Publicaciones recientes"
          subtitle="Una selección breve para entrar en la producción científica validada."
          eyebrow="Actividad reciente"
        >
          <div class="publication-list">
            @for (publication of recentPublications(); track publication.id) {
              <a class="publication-card" [routerLink]="['/portal/publicaciones', publication.id]" [queryParams]="navigationContext.returnQueryParams('Volver al inicio')">
                <span class="publication-year">{{ publication.year || 's. f.' }}</span>
                <div class="publication-main">
                  <strong>{{ publication.title }}</strong>
                  <p>{{ publication.source || publication.doi || 'Repositorio institucional' }}</p>
                  <div class="chip-list">
                    <rip-tag-chip [label]="publicationTypeLabel(publication.type)" tone="type" />
                    @for (topic of publication.topics.slice(0, 2); track topic) {
                      <rip-tag-chip [label]="topic" />
                    }
                  </div>
                </div>
              </a>
            } @empty {
              <rip-empty-state title="Sin publicaciones visibles" message="Todavía no hay publicaciones públicas para destacar." />
            }
          </div>
          <a class="section-action" routerLink="/portal/publicaciones">Buscar publicaciones</a>
        </rip-section-card>
      </section>

      <section class="featured-grid secondary">
        <rip-section-card
          title="Temas principales"
          subtitle="Áreas de conocimiento que ayudan a recorrer la actividad científica del portal."
          eyebrow="Temas"
        >
          <div class="topics-editorial">
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
            <a class="section-action" routerLink="/portal/publicaciones">Buscar publicaciones por tema</a>
          </div>
        </rip-section-card>

        <rip-section-card
          title="Guía de expertos"
          subtitle="Encuentra perfiles institucionales a partir de una pregunta, una línea temática o una necesidad concreta."
          eyebrow="Personas y evidencia"
        >
          <div class="expert-teaser">
            <p>
              Describe un tema, una necesidad de colaboración o una pregunta aplicada. La guía cruza perfiles
              públicos con evidencia institucional validada.
            </p>
            <div class="query-suggestions" aria-label="Consultas sugeridas">
              @for (suggestion of expertGuideSuggestions; track suggestion) {
                <a [routerLink]="['/portal/guia-expertos']" [queryParams]="{ q: suggestion }">{{ suggestion }}</a>
              }
            </div>
            <a class="section-action" routerLink="/portal/guia-expertos">Abrir guía de expertos</a>
          </div>
        </rip-section-card>
      </section>
    </section>
  `,
  styles: [`
    .portal-home,
    .discovery-section,
    .featured-grid,
    .unit-list,
    .publication-list,
    .topics-editorial,
    .expert-teaser {
      display: grid;
      gap: 20px;
    }

    .portal-home {
      gap: 30px;
    }

    .portal-hero {
      position: relative;
      display: grid;
      grid-template-columns: minmax(0, 1.1fr) minmax(300px, 430px);
      gap: clamp(24px, 4vw, 54px);
      align-items: center;
      min-height: 420px;
      padding: clamp(34px, 5vw, 64px) clamp(26px, 5vw, 68px);
      border: 1px solid color-mix(in srgb, var(--portal-accent) 18%, var(--portal-border));
      border-radius: 10px;
      background: linear-gradient(115deg, rgba(255, 255, 255, 0.98), rgba(247, 251, 251, 0.94) 47%, rgba(218, 236, 238, 0.82));
      box-shadow: 0 18px 42px rgba(16, 37, 48, 0.08);
      overflow: hidden;
    }

    .portal-hero::before {
      position: absolute;
      inset: 0 auto 0 0;
      width: 7px;
      background: linear-gradient(180deg, var(--portal-accent), rgba(15, 100, 121, 0.15));
      content: "";
    }

    .hero-copy,
    .hero-visual,
    .section-heading,
    .publication-main,
    .news-copy {
      display: grid;
      min-width: 0;
    }

    .hero-copy {
      position: relative;
      gap: 18px;
      max-width: 790px;
    }

    .eyebrow,
    .hero-visual-label {
      margin: 0;
      color: var(--portal-accent);
      font-size: 0.78rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    h1 {
      max-width: 15ch;
      margin: 0;
      color: var(--portal-text);
      font-size: clamp(2.2rem, 4.2vw, 3.8rem);
      font-weight: 780;
      line-height: 1.04;
    }

    .hero-text,
    .section-heading p:last-child,
    .hero-paths span,
    .hero-visual p,
    .unit-highlight p,
    .publication-main p,
    .expert-teaser p,
    .news-copy p:last-of-type {
      margin: 0;
      color: var(--portal-muted);
      line-height: 1.55;
    }

    .hero-text {
      max-width: 62ch;
      font-size: 1.08rem;
    }

    .hero-actions,
    .news-feature-head,
    .chip-list,
    .topic-cloud,
    .query-suggestions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }

    .hero-visual {
      position: relative;
      gap: 16px;
      padding: 22px;
      border: 1px solid rgba(15, 100, 121, 0.18);
      border-radius: 8px;
      background: rgba(255, 255, 255, 0.72);
      box-shadow: 0 14px 30px rgba(16, 37, 48, 0.07);
    }

    .hero-paths {
      display: grid;
      gap: 10px;
    }

    .hero-paths a {
      display: grid;
      gap: 4px;
      padding: 13px 0;
      border-bottom: 1px solid rgba(15, 100, 121, 0.12);
      color: inherit;
      text-decoration: none;
    }

    .hero-paths a:last-child {
      border-bottom: 0;
    }

    .section-heading {
      gap: 6px;
      max-width: 820px;
    }

    .section-heading h2,
    .news-feature-head h2 {
      margin: 0;
      color: var(--portal-text);
      font-size: clamp(1.24rem, 1.8vw, 1.7rem);
      line-height: 1.18;
    }

    .discovery-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 14px;
    }

    .discovery-card {
      display: grid;
      grid-template-rows: auto auto 1fr auto;
      gap: 10px;
      min-height: 218px;
      padding: 20px;
      border: 1px solid var(--portal-border);
      border-radius: 8px;
      background: var(--portal-surface);
      color: inherit;
      text-decoration: none;
    }

    .discovery-marker,
    .publication-year {
      width: fit-content;
      border-radius: 999px;
      background: var(--portal-accent-soft);
      color: var(--portal-accent);
      font-weight: 800;
    }

    .discovery-marker {
      padding: 4px 8px;
      font-size: 0.76rem;
    }

    .hero-paths strong,
    .discovery-card strong,
    .unit-highlight strong,
    .publication-main strong,
    .news-copy strong {
      color: var(--portal-text);
      font-size: 1rem;
      line-height: 1.32;
    }

    .discovery-card strong {
      font-size: 1.08rem;
    }

    .discovery-card p {
      margin: 0;
      color: var(--portal-muted);
      line-height: 1.56;
    }

    .discovery-action,
    .section-action,
    .subtle-link {
      color: var(--portal-link);
      font-size: 0.92rem;
      font-weight: 780;
      text-decoration: none;
    }

    .metric-strip {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      border: 1px solid var(--portal-border);
      border-radius: 8px;
      background: rgba(255, 255, 255, 0.66);
      overflow: hidden;
    }

    .metric-strip div {
      display: grid;
      gap: 4px;
      padding: 15px 18px;
      border-right: 1px solid color-mix(in srgb, var(--portal-border) 76%, transparent);
    }

    .metric-strip div:last-child {
      border-right: 0;
    }

    .metric-strip strong {
      color: var(--portal-text);
      font-size: 1.38rem;
      font-weight: 780;
      line-height: 1;
    }

    .metric-strip span,
    .topic-pill span {
      color: var(--portal-muted);
      font-size: 0.84rem;
      font-weight: 720;
    }

    .featured-grid.primary {
      align-items: start;
      grid-template-columns: 1fr;
    }

    .featured-grid.secondary {
      align-items: start;
      grid-template-columns: 1fr;
    }

    .unit-list {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .unit-highlight,
    .publication-card,
    .news-card {
      color: inherit;
      text-decoration: none;
    }

    .unit-highlight,
    .publication-card {
      display: grid;
      gap: 14px;
      padding: 16px 18px;
      border: 1px solid color-mix(in srgb, var(--portal-border) 78%, transparent);
      border-radius: 8px;
      background: var(--portal-surface);
    }

    .unit-highlight {
      grid-template-columns: minmax(0, 1fr);
      align-items: start;
    }

    .unit-highlight > span,
    .topic-pill,
    .query-suggestions a {
      border: 1px solid var(--portal-border);
      border-radius: 999px;
      color: var(--portal-link);
    }

    .unit-highlight > span {
      max-width: 120px;
      width: fit-content;
      padding: 5px 9px;
      font-size: 0.76rem;
      font-weight: 760;
      line-height: 1.1;
      overflow-wrap: anywhere;
    }

    .publication-card {
      grid-template-columns: auto minmax(0, 1fr);
      align-items: start;
    }

    .publication-year {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      align-self: start;
      min-width: 58px;
      height: 32px;
      border: 1px solid var(--portal-border);
      font-size: 0.8rem;
    }

    .publication-main,
    .news-copy {
      gap: 8px;
    }

    .chip-list,
    .topic-cloud,
    .query-suggestions {
      gap: 8px;
    }

    .news-feature {
      display: grid;
      gap: 18px;
      padding: 24px;
      border: 1px solid var(--portal-border);
      border-radius: 8px;
      background: color-mix(in srgb, var(--portal-surface-muted) 48%, #ffffff);
    }

    .news-feature-head {
      align-items: end;
      justify-content: space-between;
    }

    .news-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 16px;
    }

    .news-card {
      display: grid;
      grid-template-rows: auto 1fr;
      border: 1px solid var(--portal-border);
      border-radius: 8px;
      overflow: hidden;
      background: var(--portal-surface);
    }

    .news-image {
      display: grid;
      place-items: center;
      aspect-ratio: 16 / 9;
      background: linear-gradient(135deg, #164756, #4f8793);
      color: #ffffff;
    }

    .news-image img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .news-image-empty span {
      padding: 8px 12px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.12);
      font-size: 0.8rem;
      font-weight: 780;
    }

    .news-copy {
      padding: 16px;
    }

    .news-date,
    .news-context,
    .news-link-label {
      margin: 0;
      font-size: 0.8rem;
      font-weight: 760;
    }

    .news-date {
      color: var(--portal-muted);
      text-transform: uppercase;
    }

    .news-context,
    .news-link-label {
      color: var(--portal-link);
    }

    .topic-pill,
    .query-suggestions a {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      min-height: 38px;
      padding: 9px 13px;
      background: var(--portal-surface);
      font: inherit;
      text-decoration: none;
      cursor: pointer;
    }

    .topic-pill strong {
      color: var(--portal-text);
      font-size: 0.88rem;
      font-weight: 720;
    }

    .query-suggestions a {
      background: var(--portal-accent-soft);
      font-size: 0.9rem;
      font-weight: 740;
    }

    .expert-teaser {
      grid-template-columns: 1fr;
      align-items: start;
    }

    .expert-teaser p {
      max-width: 72ch;
    }

    .section-action {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: fit-content;
      min-height: 38px;
      margin-top: 8px;
      padding: 8px 13px;
      border: 1px solid var(--portal-border);
      border-radius: 999px;
      background: var(--portal-accent-soft);
      line-height: 1;
    }

    .discovery-card,
    .news-card,
    .topic-pill,
    .query-suggestions a,
    .unit-highlight,
    .publication-card {
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .discovery-card:hover,
    .news-card:hover,
    .topic-pill:hover,
    .query-suggestions a:hover {
      border-color: color-mix(in srgb, var(--portal-accent) 42%, var(--portal-border));
      box-shadow: 0 12px 26px rgba(16, 37, 48, 0.08);
      transform: translateY(-2px);
    }

    .unit-highlight:hover,
    .publication-card:hover {
      border-color: color-mix(in srgb, var(--portal-accent) 42%, var(--portal-border));
      transform: translateX(3px);
    }

    .hero-paths a:hover,
    .subtle-link:hover,
    .section-action:hover,
    .discovery-card:hover .discovery-action {
      text-decoration: underline;
      text-underline-offset: 3px;
    }

    a:focus-visible,
    button:focus-visible {
      outline: 3px solid rgba(15, 100, 121, 0.18);
      outline-offset: 3px;
    }

    @media (max-width: 1180px) {
      .discovery-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .portal-hero,
      .unit-list,
      .news-grid {
        grid-template-columns: 1fr;
      }

      .portal-hero {
        min-height: auto;
      }
    }

    @media (max-width: 760px) {
      .portal-home {
        gap: 24px;
      }

      .portal-hero,
      .news-feature {
        padding: 22px;
      }

      h1 {
        max-width: none;
        font-size: clamp(2rem, 12vw, 2.55rem);
      }

      .hero-actions,
      .discovery-grid,
      .metric-strip,
      .publication-card,
      .unit-highlight,
      .expert-teaser {
        grid-template-columns: 1fr;
      }

      .hero-actions {
        display: grid;
        justify-items: start;
      }

      .metric-strip div {
        border-right: 0;
        border-bottom: 1px solid color-mix(in srgb, var(--portal-border) 76%, transparent);
      }

      .metric-strip div:last-child {
        border-bottom: 0;
      }
    }
  `]
})
export class PortalHomePageComponent implements OnInit {
  private readonly portalApi = inject(PortalApiService);
  private readonly newsApi = inject(NewsApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly discoveryCards = [
    {
      marker: '01',
      title: 'Unidades',
      description: 'Explora departamentos, institutos, centros y grupos de investigación.',
      action: 'Ver unidades',
      route: '/portal/unidades'
    },
    {
      marker: '02',
      title: 'Investigadores',
      description: 'Consulta perfiles públicos, temas de trabajo y producción asociada.',
      action: 'Ver investigadores',
      route: '/portal/investigadores'
    },
    {
      marker: '03',
      title: 'Publicaciones',
      description: 'Busca publicaciones por texto, tema o búsqueda inteligente.',
      action: 'Buscar publicaciones',
      route: '/portal/publicaciones'
    },
    {
      marker: '04',
      title: 'Guía de expertos',
      description: 'Encuentra especialistas a partir de temas o necesidades concretas.',
      action: 'Abrir guía de expertos',
      route: '/portal/guia-expertos'
    }
  ] as const;

  readonly expertGuideSuggestions = [
    'IA local en hospitales',
    'Salud pública y clima urbano',
    'Calidad de datos en investigación'
  ] as const;

  readonly summary = signal<PortalSummary>(this.emptySummary());
  readonly featuredNews = signal<PortalNewsArticleSummary[]>([]);
  readonly newsResearcherLabels = signal<Record<number, string>>({});
  readonly newsUnitLabels = signal<Record<number, string>>({});

  readonly publicationCount = computed(() => this.summary().totalValidatedPublications);
  readonly researcherCount = computed(() => this.summary().totalPublicResearchers);
  readonly unitCount = computed(() => this.summary().totalPublicResearchUnits);
  readonly topicCount = computed(() => this.summary().topTopics.length);
  readonly recentPublications = computed(() => this.summary().recentValidatedPublications.slice(0, 5));
  readonly featuredUnits = computed(() => this.summary().featuredResearchUnits.slice(0, 3));
  readonly topTopics = computed(() =>
    this.summary().topTopics
      .slice(0, 10)
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
      return 'Sin fecha de publicación';
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
