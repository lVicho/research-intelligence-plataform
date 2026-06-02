import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';

import { PortalPublicationDetail, PublicationType } from '../../core/api/api-models';
import { PortalApiService } from '../../core/api/portal-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { publicationTypeLabel, researchUnitTypeLabel } from '../../shared/utils/display-labels';
import { PortalContextAssistantComponent } from './portal-context-assistant.component';
import { PortalPublicationExplanationDialogComponent } from './portal-publication-explanation-dialog.component';

type PortalPublicationTab = 'summary' | 'authors' | 'topics' | 'related';

interface PortalPublicationTabItem {
  id: PortalPublicationTab;
  label: string;
}

@Component({
  selector: 'rip-portal-publication-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PortalContextAssistantComponent,
    TagChipComponent
  ],
  template: `
    <section class="page portal-publication-detail">
      @if (loading()) {
        <rip-loading-state message="Cargando publicación..." />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else {
        @if (detail(); as publication) {
          <article class="publication-hero" aria-labelledby="publication-title">
          <button mat-button type="button" class="back-link" (click)="navigateBack()">{{ backLabel() }}</button>

          <div class="hero-topline">
            <rip-tag-chip [label]="publicationTypeLabel(publication.type)" tone="type" />
            <span>{{ publication.year || 's. f.' }}</span>
            @if (publication.publicationDate) {
              <span>{{ dateLabel(publication.publicationDate) }}</span>
            }
            @if (sourceLabel(publication) !== 'No disponible') {
              <span>{{ sourceLabel(publication) }}</span>
            }
          </div>

          <div class="hero-copy">
            <h1 id="publication-title">{{ publication.title }}</h1>
          </div>

          <div class="hero-actions" aria-label="Acciones de publicación">
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="openExplanation(publication)"
            >
              Explicar esta publicación
            </button>

            @if (doiUrl(publication.doi); as doiLink) {
              <a mat-stroked-button [href]="doiLink" target="_blank" rel="noreferrer">Abrir DOI</a>
            }

            @if (publication.url) {
              <a mat-stroked-button [href]="publication.url" target="_blank" rel="noreferrer">Fuente externa</a>
            }
          </div>
        </article>

          <rip-portal-context-assistant
            contextScope="PUBLICATION_DETAIL"
            [targetId]="publication.id"
            triggerLabel="Preguntar sobre esta publicación"
            contextTitle="Asistente contextual de la publicación"
            helperText="Haz preguntas libres sobre esta publicación y sus relaciones públicas validadas."
          />

          <nav class="portal-tabs" aria-label="Secciones de la publicación">
          @for (tab of tabs; track tab.id) {
            <button
              type="button"
              [class.active]="activeTab() === tab.id"
              [attr.aria-selected]="activeTab() === tab.id"
              (click)="selectTab(tab.id)"
            >
              {{ tab.label }}
            </button>
          }
        </nav>

          <section class="tab-panel" [attr.data-active-tab]="activeTab()">
          @switch (activeTab()) {
            @case ('summary') {
              <section class="detail-section summary-section" aria-labelledby="summary-title">
                <header class="section-header">
                  <p class="section-kicker">Resumen</p>
                  <h2 id="summary-title">Lectura pública de la publicación</h2>
                  <p>Información validada para comprender la aportación sin entrar en flujos internos.</p>
                </header>

                <div class="summary-layout">
                  <div class="text-stack">
                    @if (publication.publicSummary) {
                      <article class="editorial-block">
                        <h3>Resumen público</h3>
                        <p>{{ publication.publicSummary }}</p>
                      </article>
                    }

                    @if (showAbstract(publication)) {
                      <article class="editorial-block">
                        <h3>Resumen académico</h3>
                        <p>{{ publication.abstractText }}</p>
                      </article>
                    }

                    @if (!publication.publicSummary && !publication.abstractText) {
                      <rip-empty-state
                        title="Sin resumen público"
                        message="Esta publicación todavía no tiene un resumen visible para el portal."
                      />
                    }
                  </div>

                  <aside class="metadata-panel" aria-label="Metadatos principales">
                    <h3>Metadatos</h3>
                    <dl>
                      <div>
                        <dt>Año</dt>
                        <dd>{{ publication.year || 's. f.' }}</dd>
                      </div>
                      <div>
                        <dt>Fecha</dt>
                        <dd>{{ dateLabel(publication.publicationDate) }}</dd>
                      </div>
                      <div>
                        <dt>Fuente</dt>
                        <dd>{{ sourceLabel(publication) }}</dd>
                      </div>
                      <div>
                        <dt>Editorial</dt>
                        <dd>{{ publication.publisherName || 'No disponible' }}</dd>
                      </div>
                      <div>
                        <dt>DOI</dt>
                        <dd>
                          @if (doiUrl(publication.doi); as doiLink) {
                            <a [href]="doiLink" target="_blank" rel="noreferrer">{{ publication.doi }}</a>
                          } @else {
                            No disponible
                          }
                        </dd>
                      </div>
                      <div>
                        <dt>Idioma</dt>
                        <dd>{{ languageLabel(publication.languageCode) }}</dd>
                      </div>
                    </dl>
                  </aside>
                </div>
              </section>
            }

            @case ('authors') {
              <section class="detail-section" aria-labelledby="authors-title">
                <header class="section-header">
                  <p class="section-kicker">Autores y entidades</p>
                  <h2 id="authors-title">Personas y organizaciones vinculadas</h2>
                  <p>Autores, perfiles públicos internos, unidades institucionales y colaboradores externos citados en la ficha.</p>
                </header>

                <div class="entity-grid">
                  <article class="section-panel">
                    <h3>Autores</h3>
                    <div class="entity-list">
                      @for (author of publication.authors; track author.id) {
                        @if (author.researcherId && author.publicProfileAvailable) {
                          <a
                            class="entity-card interactive"
                            [routerLink]="['/portal/investigadores', author.researcherId]"
                            [queryParams]="navigationContext.returnQueryParams('Volver a la publicación')"
                          >
                            <strong>{{ author.name || 'Investigador interno' }}</strong>
                            <span>Perfil público disponible</span>
                          </a>
                        } @else {
                          <div class="entity-card">
                            <strong>{{ author.name || 'Autor sin nombre público' }}</strong>
                            <span>{{ author.externalAffiliation || (author.internal ? 'Autor interno sin perfil público' : 'Afiliación externa no disponible') }}</span>
                          </div>
                        }
                      } @empty {
                        <rip-empty-state title="Sin autores" message="Esta publicación no tiene autores visibles." />
                      }
                    </div>
                  </article>

                  <article class="section-panel">
                    <h3>Investigadores internos</h3>
                    <div class="entity-list">
                      @for (researcher of publication.internalResearchers; track researcher.id) {
                        <a
                          class="entity-card interactive"
                          [routerLink]="['/portal/investigadores', researcher.id]"
                          [queryParams]="navigationContext.returnQueryParams('Volver a la publicación')"
                        >
                          <strong>{{ researcher.displayName || researcher.fullName }}</strong>
                          <span>{{ researcher.primaryAffiliationName || 'Perfil público institucional' }}</span>
                        </a>
                      } @empty {
                        <rip-empty-state
                          title="Sin perfiles enlazables"
                          message="No hay perfiles internos públicos asociados a esta publicación."
                        />
                      }
                    </div>
                  </article>

                  <article class="section-panel">
                    <h3>Unidades relacionadas</h3>
                    <div class="entity-list">
                      @for (unit of publication.researchUnits; track unit.id) {
                        <a
                          class="entity-card interactive"
                          [routerLink]="['/portal/unidades', unit.id]"
                          [queryParams]="navigationContext.returnQueryParams('Volver a la publicación')"
                        >
                          <strong>{{ unit.name }}</strong>
                          <span>{{ researchUnitTypeLabel(unit.type) }}</span>
                        </a>
                      } @empty {
                        <rip-empty-state
                          title="Sin unidades públicas"
                          message="No hay unidades públicas enlazables para esta publicación."
                        />
                      }
                    </div>
                  </article>

                  <article class="section-panel">
                    <h3>Entidades externas</h3>
                    <div class="entity-list">
                      @for (organization of publication.externalOrganizations; track organization) {
                        <div class="entity-card">
                          <strong>{{ organization }}</strong>
                          <span>Organización externa citada en la autoría</span>
                        </div>
                      } @empty {
                        <rip-empty-state
                          title="Sin entidades externas"
                          message="No hay colaboradores externos visibles en esta ficha."
                        />
                      }
                    </div>
                  </article>
                </div>
              </section>
            }

            @case ('topics') {
              <section class="detail-section" aria-labelledby="topics-title">
                <header class="section-header">
                  <p class="section-kicker">Temas y relaciones</p>
                  <h2 id="topics-title">Cómo se sitúa en el mapa temático</h2>
                  <p>Tipo documental y temas normalizados que ayudan a explorar publicaciones relacionadas en el portal.</p>
                </header>

                <div class="topic-layout">
                  <article class="section-panel">
                    <h3>Tipo de publicación</h3>
                    <rip-tag-chip [label]="publicationTypeLabel(publication.type)" tone="type" />
                  </article>

                  <article class="section-panel topic-panel">
                    <h3>Temas</h3>
                    <div class="topic-list">
                      @for (topic of publication.topics; track topic.id) {
                        <a
                          class="topic-chip"
                          [routerLink]="['/portal/publicaciones']"
                          [queryParams]="{ topic: topic.name }"
                        >
                          {{ topic.name }}
                        </a>
                      } @empty {
                        <rip-empty-state title="Sin temas" message="Todavía no hay temas públicos asociados." />
                      }
                    </div>
                  </article>
                </div>
              </section>
            }

            @case ('related') {
              <section class="detail-section" aria-labelledby="related-title">
                <header class="section-header">
                  <p class="section-kicker">Publicaciones relacionadas</p>
                  <h2 id="related-title">Más actividad pública conectada</h2>
                  <p>Vista previa de publicaciones validadas que comparten señales temáticas o bibliográficas.</p>
                </header>

                <div class="related-grid">
                  @for (related of publication.relatedPublications; track related.id) {
                    <a
                      class="related-card"
                      [routerLink]="['/portal/publicaciones', related.id]"
                      [queryParams]="navigationContext.returnQueryParams('Volver a la publicación')"
                    >
                      <div class="related-topline">
                        <rip-tag-chip [label]="publicationTypeLabel(related.type)" tone="type" />
                        <span>{{ related.year || 's. f.' }}</span>
                      </div>
                      <strong>{{ related.title }}</strong>
                      <p>{{ related.source || related.doi || 'Repositorio institucional' }}</p>
                      @if (related.topics.length > 0) {
                        <div class="topic-list compact">
                          @for (topic of related.topics.slice(0, 3); track topic) {
                            <span class="topic-chip small">{{ topic }}</span>
                          }
                        </div>
                      }
                    </a>
                  } @empty {
                    <rip-empty-state
                      title="Sin publicaciones relacionadas"
                      message="Todavía no hay relaciones públicas suficientes para mostrar una vista previa."
                    />
                  }
                </div>
              </section>
            }
          }
          </section>
        } @else {
          <rip-empty-state
            title="Publicación no disponible"
            message="La publicación no está disponible públicamente."
          />
        }
      }
    </section>
  `,
  styles: [`
    .publication-hero,
    .detail-section,
    .section-panel,
    .metadata-panel,
    .explanation-callout,
    .related-card {
      border: 1px solid var(--portal-border);
      border-radius: 8px;
      background: var(--portal-surface);
    }

    .publication-hero {
      display: grid;
      gap: 20px;
      padding: clamp(22px, 4vw, 42px);
      background:
        linear-gradient(135deg, color-mix(in srgb, var(--portal-accent-soft) 76%, #ffffff) 0%, #ffffff 58%),
        var(--portal-surface);
    }

    .back-link {
      justify-self: start;
      color: var(--portal-link);
    }

    .hero-topline,
    .hero-actions,
    .topic-list,
    .related-topline {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .hero-copy {
      display: grid;
      gap: 14px;
    }

    .hero-copy h1 {
      margin: 0;
      color: var(--portal-text);
      font-size: clamp(2rem, 4vw, 3.35rem);
      line-height: 1.03;
    }

    .hero-copy p {
      margin: 0;
      max-width: 86ch;
      color: var(--portal-muted);
      line-height: 1.7;
      white-space: pre-line;
    }

    .portal-tabs {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding: 6px;
      border: 1px solid var(--portal-border);
      border-radius: 8px;
      background: color-mix(in srgb, var(--portal-surface-muted) 58%, #ffffff);
    }

    .portal-tabs button {
      flex: 1 1 180px;
      min-height: 42px;
      border: 0;
      border-radius: 6px;
      background: transparent;
      color: var(--portal-muted);
      cursor: pointer;
      font-weight: 760;
      text-align: center;
    }

    .portal-tabs button.active {
      background: var(--portal-surface);
      color: var(--portal-accent);
      box-shadow: 0 8px 18px rgba(16, 37, 48, 0.1);
    }

    .detail-section {
      display: grid;
      gap: 22px;
      padding: clamp(20px, 3vw, 30px);
    }

    .section-header {
      display: grid;
      gap: 8px;
    }

    .section-kicker,
    .explanation-callout span {
      margin: 0;
      color: var(--portal-accent);
      font-size: 0.78rem;
      font-weight: 780;
      text-transform: uppercase;
    }

    .section-header h2,
    .section-panel h3,
    .metadata-panel h3,
    .editorial-block h3 {
      margin: 0;
      color: var(--portal-text);
      line-height: 1.2;
    }

    .section-header p:not(.section-kicker),
    .editorial-block p,
    .explanation-callout p,
    .related-card p {
      margin: 0;
      color: var(--portal-muted);
      line-height: 1.65;
    }

    .summary-layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
      gap: 18px;
      align-items: start;
    }

    .text-stack,
    .entity-list,
    .metadata-panel dl,
    .topic-layout,
    .related-grid {
      display: grid;
      gap: 14px;
    }

    .editorial-block {
      display: grid;
      gap: 10px;
      max-width: 92ch;
    }

    .editorial-block p {
      white-space: pre-line;
    }

    .metadata-panel,
    .section-panel,
    .explanation-callout {
      display: grid;
      gap: 14px;
      padding: 18px;
    }

    .metadata-panel dl {
      margin: 0;
    }

    .metadata-panel dl div {
      display: grid;
      gap: 4px;
      padding-block: 10px;
      border-bottom: 1px solid color-mix(in srgb, var(--portal-border) 72%, transparent);
    }

    .metadata-panel dl div:last-child {
      border-bottom: 0;
    }

    .metadata-panel dt {
      color: var(--portal-accent);
      font-size: 0.76rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .metadata-panel dd {
      margin: 0;
      color: var(--portal-text);
      line-height: 1.45;
      overflow-wrap: anywhere;
    }

    .explanation-callout {
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      background: color-mix(in srgb, var(--portal-accent-soft) 70%, #ffffff);
    }

    .entity-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }

    .entity-card {
      display: grid;
      gap: 6px;
      min-width: 0;
      padding: 14px 16px;
      border: 1px solid color-mix(in srgb, var(--portal-border) 84%, transparent);
      border-radius: 8px;
      background: color-mix(in srgb, var(--portal-surface-muted) 32%, #ffffff);
      color: inherit;
      text-decoration: none;
    }

    .entity-card strong,
    .related-card strong {
      color: var(--portal-text);
      line-height: 1.35;
    }

    .entity-card span {
      color: var(--portal-muted);
      line-height: 1.45;
    }

    .entity-card.interactive:hover,
    .related-card:hover {
      border-color: color-mix(in srgb, var(--portal-accent) 42%, var(--portal-border));
    }

    .topic-layout {
      grid-template-columns: minmax(220px, 0.35fr) minmax(0, 1fr);
      align-items: start;
    }

    .topic-panel {
      align-content: start;
    }

    .topic-chip {
      display: inline-flex;
      align-items: center;
      min-height: 30px;
      max-width: 100%;
      padding: 5px 11px;
      border: 1px solid color-mix(in srgb, var(--portal-accent) 24%, var(--portal-border));
      border-radius: 999px;
      background: var(--portal-accent-soft);
      color: var(--portal-accent);
      font-size: 0.86rem;
      overflow-wrap: anywhere;
      text-decoration: none;
    }

    .related-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .related-card {
      display: grid;
      gap: 10px;
      min-width: 0;
      padding: 18px;
      color: inherit;
      text-decoration: none;
    }

    @media (max-width: 920px) {
      .summary-layout,
      .entity-grid,
      .topic-layout,
      .related-grid {
        grid-template-columns: 1fr;
      }

      .explanation-callout {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class PortalPublicationDetailPageComponent implements OnInit {
  private readonly portalApi = inject(PortalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly tabs: PortalPublicationTabItem[] = [
    { id: 'summary', label: 'Resumen' },
    { id: 'authors', label: 'Autores y entidades' },
    { id: 'topics', label: 'Temas y relaciones' },
    { id: 'related', label: 'Publicaciones relacionadas' }
  ];

  readonly detail = signal<PortalPublicationDetail | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly activeTab = signal<PortalPublicationTab>('summary');
  readonly publicationId = Number(this.route.snapshot.paramMap.get('id'));
  readonly backLabel = computed(() =>
    this.navigationContext.resolve(this.route, '/portal/publicaciones', 'Volver a publicaciones').label
  );

  ngOnInit(): void {
    if (!Number.isFinite(this.publicationId)) {
      this.loading.set(false);
      this.errorMessage.set('No se ha podido cargar la publicación.');
      return;
    }

    this.portalApi.publication(this.publicationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.detail.set(detail);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No se ha podido cargar la publicación.');
        }
      });
  }

  selectTab(tab: PortalPublicationTab): void {
    this.activeTab.set(tab);
  }

  openExplanation(publication: PortalPublicationDetail): void {
    this.dialog.open(PortalPublicationExplanationDialogComponent, {
      width: 'min(900px, 96vw)',
      maxWidth: '96vw',
      maxHeight: '92vh',
      autoFocus: false,
      data: {
        publicationId: publication.id,
        title: publication.title,
        initialStyle: 'PLAIN'
      }
    });
  }

  publicationTypeLabel(type: PublicationType): string {
    return publicationTypeLabel(type);
  }

  researchUnitTypeLabel(type: string): string {
    return researchUnitTypeLabel(type);
  }

  primarySummary(publication: PortalPublicationDetail): string {
    return (publication.publicSummary || publication.abstractText || '').trim();
  }

  showAbstract(publication: PortalPublicationDetail): boolean {
    const abstractText = publication.abstractText?.trim() ?? '';
    const publicSummary = publication.publicSummary?.trim() ?? '';
    return abstractText.length > 0 && abstractText !== publicSummary;
  }

  sourceLabel(publication: PortalPublicationDetail): string {
    return publication.venueName
      || publication.sourceDetail
      || publication.source
      || publication.issn
      || publication.isbn
      || 'No disponible';
  }

  dateLabel(value: string | null): string {
    if (!value) {
      return 'Sin fecha';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return 'Sin fecha';
    }
    return new Intl.DateTimeFormat('es-ES', { dateStyle: 'long' }).format(date);
  }

  doiUrl(doi: string | null): string | null {
    const value = doi?.trim();
    if (!value) {
      return null;
    }
    return value.startsWith('http://') || value.startsWith('https://')
      ? value
      : `https://doi.org/${value}`;
  }

  languageLabel(value: string | null): string {
    if (!value) {
      return 'No disponible';
    }
    const normalized = value.trim().toLowerCase();
    const labels: Record<string, string> = {
      es: 'Español',
      spa: 'Español',
      en: 'Inglés',
      eng: 'Inglés',
      pt: 'Portugués',
      por: 'Portugués',
      fr: 'Francés',
      fre: 'Francés',
      fra: 'Francés'
    };
    return labels[normalized] ?? value.toUpperCase();
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, '/portal/publicaciones', 'Volver a publicaciones');
  }
}
