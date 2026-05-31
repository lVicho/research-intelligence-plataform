import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { PortalPublicationDetail, PublicationSummary } from '../../core/api/api-models';
import { PortalApiService } from '../../core/api/portal-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { publicationTypeLabel, researchUnitTypeLabel } from '../../shared/utils/display-labels';

@Component({
  selector: 'rip-portal-publication-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    TagChipComponent
  ],
  template: `
    <section class="page portal-publication-detail">
      <rip-page-header
        [title]="detail()?.title || 'Publicación'"
        eyebrow="Portal público"
        [subtitle]="subtitle()"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
      </rip-page-header>

      @if (loading()) {
        <rip-loading-state message="Cargando ficha pública de la publicación" />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else {
        @if (detail(); as publication) {
          <mat-card appearance="outlined" class="summary-card">
            <mat-card-content>
              <div class="meta-row">
                <rip-tag-chip [label]="publicationTypeLabel(publication.type)" tone="type" />
                <span>{{ publication.year || 's. f.' }}</span>
                @if (publication.venueName || publication.source) {
                  <span>{{ publication.venueName || publication.source }}</span>
                }
              </div>

              @if (publication.publicSummary || publication.abstractText) {
                <section class="text-block">
                  <h2>{{ publication.publicSummary ? 'Resumen público' : 'Resumen' }}</h2>
                  <p>{{ publication.publicSummary || publication.abstractText }}</p>
                </section>
              } @else {
                <rip-empty-state
                  title="Sin resumen público"
                  message="Esta publicación todavía no tiene un resumen visible para el portal."
                />
              }

              <div class="metadata-grid">
                <div>
                  <span>DOI</span>
                  <strong>{{ publication.doi || 'No disponible' }}</strong>
                </div>
                <div>
                  <span>Fuente</span>
                  <strong>{{ sourceLabel(publication) }}</strong>
                </div>
                <div>
                  <span>Editorial</span>
                  <strong>{{ publication.publisherName || 'No disponible' }}</strong>
                </div>
                <div>
                  <span>Fecha</span>
                  <strong>{{ dateLabel(publication.publicationDate) }}</strong>
                </div>
              </div>

              @if (publication.url) {
                <a mat-stroked-button [href]="publication.url" target="_blank" rel="noreferrer">Abrir fuente externa</a>
              }
            </mat-card-content>
          </mat-card>

          <section class="content-grid">
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-card-title>Autores</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="stack-list">
                  @for (author of publication.authors; track author.id) {
                    @if (author.researcherId && author.publicProfileAvailable) {
                      <a
                        class="list-card"
                        [routerLink]="['/portal/investigadores', author.researcherId]"
                        [queryParams]="navigationContext.returnQueryParams('Volver a la publicación')"
                      >
                        <strong>{{ author.name || 'Investigador interno' }}</strong>
                        <p>Perfil público disponible</p>
                      </a>
                    } @else {
                      <div class="list-card passive-card">
                        <strong>{{ author.name || 'Autor sin nombre público' }}</strong>
                        <p>{{ author.externalAffiliation || (author.internal ? 'Autor interno sin perfil público' : 'Afiliación externa no disponible') }}</p>
                      </div>
                    }
                  } @empty {
                    <rip-empty-state title="Sin autores" message="Esta publicación no tiene autores visibles." />
                  }
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-card-title>Contexto institucional</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="stack-list">
                  @for (researcher of publication.internalResearchers; track researcher.id) {
                    <a
                      class="list-card"
                      [routerLink]="['/portal/investigadores', researcher.id]"
                      [queryParams]="navigationContext.returnQueryParams('Volver a la publicación')"
                    >
                      <strong>{{ researcher.displayName || researcher.fullName }}</strong>
                      <p>{{ researcher.primaryAffiliationName || 'Afiliación pública' }}</p>
                    </a>
                  }

                  @for (unit of publication.researchUnits; track unit.id) {
                    <a
                      class="list-card"
                      [routerLink]="['/portal/unidades', unit.id]"
                      [queryParams]="navigationContext.returnQueryParams('Volver a la publicación')"
                    >
                      <strong>{{ unit.name }}</strong>
                      <p>{{ researchUnitTypeLabel(unit.type) }}</p>
                    </a>
                  }

                  @for (organization of publication.externalOrganizations; track organization) {
                    <div class="list-card passive-card">
                      <strong>{{ organization }}</strong>
                      <p>Organización externa citada en la autoría</p>
                    </div>
                  } @empty {
                    @if (publication.internalResearchers.length === 0 && publication.researchUnits.length === 0) {
                      <rip-empty-state
                        title="Sin enlaces institucionales"
                        message="No hay perfiles o unidades públicas enlazables para esta publicación."
                      />
                    }
                  }
                </div>
              </mat-card-content>
            </mat-card>
          </section>

          <section class="content-grid">
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-card-title>Temas</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="chip-list">
                  @for (topic of publication.topics; track topic.id) {
                    <rip-tag-chip [label]="topic.name" />
                  } @empty {
                    <rip-empty-state title="Sin temas" message="Todavía no hay temas públicos asociados." />
                  }
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-card-title>Publicaciones relacionadas</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="stack-list">
                  @for (related of publication.relatedPublications; track related.id) {
                    <a
                      class="list-card"
                      [routerLink]="['/portal/publicaciones', related.id]"
                      [queryParams]="navigationContext.returnQueryParams('Volver a la publicación')"
                    >
                      <strong>{{ related.title }}</strong>
                      <p>{{ related.year || 's. f.' }} · {{ related.source || related.doi || 'Repositorio institucional' }}</p>
                      <div class="chip-list">
                        <rip-tag-chip [label]="publicationTypeLabel(related.type)" tone="type" />
                        @for (topic of related.topics.slice(0, 3); track topic) {
                          <rip-tag-chip [label]="topic" />
                        }
                      </div>
                    </a>
                  } @empty {
                    <rip-empty-state
                      title="Sin publicaciones relacionadas"
                      message="Todavía no hay relaciones públicas suficientes para mostrar una vista previa."
                    />
                  }
                </div>
              </mat-card-content>
            </mat-card>
          </section>

          @if (publication.warnings.length > 0) {
            <mat-card appearance="outlined" class="warning-card">
              <mat-card-content>
                @for (warning of publication.warnings; track warning) {
                  <p>{{ warning }}</p>
                }
              </mat-card-content>
            </mat-card>
          }
        } @else {
          <rip-empty-state
            title="Publicación no disponible"
            message="Esta ficha no está visible en el portal público."
          />
        }
      }
    </section>
  `,
  styles: [`
    .portal-publication-detail {
      gap: 24px;
    }

    .summary-card mat-card-content,
    .stack-list {
      display: grid;
      gap: 16px;
    }

    .summary-card {
      border-radius: 18px !important;
    }

    .meta-row,
    .chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .meta-row span {
      color: #5f7182;
      font-weight: 720;
    }

    .text-block {
      display: grid;
      gap: 10px;
      max-width: 92ch;
    }

    .text-block h2 {
      margin: 0;
      color: #132235;
      font-size: 1.3rem;
      line-height: 1.2;
    }

    .text-block p {
      margin: 0;
      color: #405466;
      line-height: 1.75;
      white-space: pre-line;
    }

    .metadata-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
      gap: 12px;
    }

    .metadata-grid div {
      display: grid;
      gap: 5px;
      padding: 14px 16px;
      border: 1px solid #e0e8ee;
      border-radius: 12px;
      background: #fbfdfe;
    }

    .metadata-grid span {
      color: #617283;
      font-size: 0.74rem;
      font-weight: 780;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    .metadata-grid strong,
    .list-card strong {
      color: #102033;
      line-height: 1.35;
    }

    .list-card {
      display: grid;
      gap: 8px;
      padding: 16px;
      border: 1px solid #e0e8ee;
      border-radius: 12px;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
    }

    a.list-card {
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }

    a.list-card:hover {
      border-color: #aac8d7;
      box-shadow: 0 14px 26px rgba(20, 32, 51, 0.08);
      transform: translateY(-1px);
    }

    .passive-card {
      background: #fbfdfe;
    }

    .list-card p,
    .warning-card p {
      margin: 0;
      color: #617283;
      line-height: 1.55;
    }

    .warning-card {
      border-color: #e7d7b8 !important;
      background: #fffaf0;
    }
  `]
})
export class PortalPublicationDetailPageComponent implements OnInit {
  private readonly portalApi = inject(PortalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly detail = signal<PortalPublicationDetail | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly publicationId = Number(this.route.snapshot.paramMap.get('id'));
  readonly subtitle = computed(() => {
    const publication = this.detail();
    if (!publication) {
      return 'Ficha pública de publicación';
    }
    return [
      publication.year,
      publication.venueName || publication.source || publication.publisherName
    ].filter(Boolean).join(' · ') || 'Actividad pública validada';
  });

  ngOnInit(): void {
    if (!Number.isFinite(this.publicationId)) {
      this.loading.set(false);
      this.errorMessage.set('La publicación solicitada no es válida.');
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
          this.errorMessage.set('No se pudo cargar la ficha pública de la publicación.');
        }
      });
  }

  publicationTypeLabel(type: PortalPublicationDetail['type'] | PublicationSummary['type']): string {
    return publicationTypeLabel(type);
  }

  researchUnitTypeLabel(type: string): string {
    return researchUnitTypeLabel(type);
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
    return new Intl.DateTimeFormat('es-ES', { dateStyle: 'long' }).format(new Date(value));
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, '/portal/publicaciones', 'Volver a publicaciones').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, '/portal/publicaciones', 'Volver a publicaciones');
  }
}
