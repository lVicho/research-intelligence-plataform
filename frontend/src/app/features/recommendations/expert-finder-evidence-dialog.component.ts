import { Component, Inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle
} from '@angular/material/dialog';

import {
  ExpertFinderEventEvidence,
  ExpertFinderPublicationEvidence,
  ExpertFinderResult
} from '../../core/api/expert-finder-api.service';
import { NavigationContextQueryParams } from '../../core/navigation/navigation-context.service';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { publicationTypeLabel } from '../../shared/utils/display-labels';

export interface ExpertFinderEvidenceDialogData {
  expert: ExpertFinderResult;
  returnQueryParams: NavigationContextQueryParams;
}

@Component({
  selector: 'rip-expert-finder-evidence-dialog',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    TagChipComponent
  ],
  template: `
    <div class="evidence-dialog">
      <header class="dialog-header">
        <p class="section-kicker">Evidencias públicas</p>
        <h2 mat-dialog-title>{{ expertName() }}</h2>
        <p>{{ data.expert.explanation }}</p>
      </header>

      <mat-dialog-content class="dialog-content">
        @if (data.expert.warnings.length > 0) {
          <section class="warning-panel">
            @for (warning of data.expert.warnings; track warning) {
              <p>{{ publicWarning(warning) }}</p>
            }
          </section>
        }

        <section class="evidence-section">
          <h3>Publicaciones</h3>
          @if (data.expert.representativePublications.length > 0) {
            <div class="evidence-list">
              @for (publication of data.expert.representativePublications; track publication.id) {
                <article class="evidence-card">
                  <div class="evidence-main">
                    <a
                      [routerLink]="['/portal/publicaciones', publication.id]"
                      [queryParams]="data.returnQueryParams"
                    >
                      {{ publication.title }}
                    </a>
                    <p>
                      {{ publication.year || 's. f.' }}
                      @if (publication.source) {
                        <span>· {{ publication.source }}</span>
                      }
                    </p>
                  </div>

                  <div class="evidence-meta">
                    @if (publication.type) {
                      <rip-tag-chip [label]="publicationTypeLabel(publication.type)" tone="type" />
                    }
                    @if (publication.semanticSimilarity !== null) {
                      <span class="soft-badge">{{ affinityLabel(publication.semanticSimilarity) }}</span>
                    }
                  </div>

                  @if (publication.matchedTopics.length > 0) {
                    <div class="topic-row">
                      @for (topic of publication.matchedTopics.slice(0, 3); track topic) {
                        <rip-tag-chip [label]="topic" />
                      }
                    </div>
                  }
                </article>
              }
            </div>
          } @else {
            <p class="empty-copy">No hay publicaciones destacadas para esta consulta.</p>
          }
        </section>

        <section class="evidence-section">
          <h3>Participación en eventos</h3>
          @if (data.expert.relevantEventParticipations.length > 0) {
            <div class="evidence-list">
              @for (event of data.expert.relevantEventParticipations; track event.id) {
                <article class="evidence-card event-card">
                  <div class="evidence-main">
                    <strong>{{ eventTitle(event) }}</strong>
                    <p>{{ event.eventName || 'Evento institucional' }} · {{ eventDate(event) }}</p>
                  </div>
                  <div class="evidence-meta">
                    @if (event.participationTypeCode) {
                      <span class="soft-badge">{{ participationLabel(event.participationTypeCode) }}</span>
                    }
                    @if (event.relatedPublicationId) {
                      <a
                        class="evidence-link"
                        [routerLink]="['/portal/publicaciones', event.relatedPublicationId]"
                        [queryParams]="data.returnQueryParams"
                      >
                        Publicación relacionada
                      </a>
                    }
                  </div>
                </article>
              }
            </div>
          } @else {
            <p class="empty-copy">No hay participaciones destacadas para esta consulta.</p>
          }
        </section>

        @if (data.expert.reasons.length > 0) {
          <section class="reason-panel">
            <h3>Por qué aparece este perfil</h3>
            @for (reason of data.expert.reasons.slice(0, 3); track reason) {
              <p>{{ publicReason(reason) }}</p>
            }
          </section>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cerrar</button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .evidence-dialog {
      color: #162235;
    }

    .dialog-header {
      display: grid;
      gap: 8px;
      padding: 24px 24px 8px;
    }

    h2[mat-dialog-title],
    .dialog-header p,
    .evidence-section h3,
    .reason-panel h3,
    .warning-panel p,
    .reason-panel p,
    .empty-copy {
      margin: 0;
    }

    h2[mat-dialog-title] {
      padding: 0;
      color: #132133;
      font-size: clamp(1.35rem, 2vw, 1.8rem);
      line-height: 1.16;
    }

    .dialog-header > p:last-child,
    .empty-copy,
    .evidence-main p,
    .reason-panel p,
    .warning-panel p {
      color: #607286;
      line-height: 1.55;
    }

    .section-kicker {
      color: var(--portal-accent-700, #245b73);
      font-size: 0.76rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .dialog-content {
      display: grid;
      gap: 22px;
      padding: 16px 24px 8px;
    }

    .evidence-section,
    .reason-panel,
    .warning-panel {
      display: grid;
      gap: 12px;
    }

    .warning-panel,
    .reason-panel {
      padding: 14px 16px;
      border: 1px solid #d8e7ee;
      border-radius: 8px;
      background: #f6fbfd;
    }

    .evidence-list {
      display: grid;
      gap: 12px;
    }

    .evidence-card {
      display: grid;
      gap: 12px;
      padding: 16px;
      border: 1px solid #dfe8ee;
      border-radius: 8px;
      background: #ffffff;
    }

    .evidence-main {
      display: grid;
      gap: 5px;
      min-width: 0;
    }

    .evidence-main a,
    .evidence-main strong {
      color: #132133;
      font-weight: 800;
      line-height: 1.35;
      text-decoration: none;
    }

    .evidence-main a:hover,
    .evidence-link:hover {
      color: var(--portal-accent-700, #245b73);
      text-decoration: underline;
    }

    .evidence-meta,
    .topic-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .soft-badge {
      padding: 6px 10px;
      border: 1px solid #d8e4eb;
      border-radius: 999px;
      background: #f8fafb;
      color: #365369;
      font-size: 0.78rem;
      font-weight: 760;
    }

    .evidence-link {
      color: var(--portal-accent-700, #245b73);
      font-size: 0.88rem;
      font-weight: 760;
      text-decoration: none;
    }

    mat-dialog-actions {
      padding: 12px 24px 20px;
    }

    @media (max-width: 640px) {
      .dialog-header,
      .dialog-content,
      mat-dialog-actions {
        padding-left: 18px;
        padding-right: 18px;
      }
    }
  `]
})
export class ExpertFinderEvidenceDialogComponent {
  constructor(@Inject(MAT_DIALOG_DATA) readonly data: ExpertFinderEvidenceDialogData) {
  }

  expertName(): string {
    return this.data.expert.researcher.displayName || this.data.expert.researcher.fullName;
  }

  publicationTypeLabel(type: string): string {
    return publicationTypeLabel(type);
  }

  affinityLabel(score: number): string {
    const value = Math.round(this.normalizeScore(score) * 100);
    return `${value}% de afinidad`;
  }

  eventTitle(event: ExpertFinderEventEvidence): string {
    return event.title || event.eventName || 'Participación institucional';
  }

  eventDate(event: ExpertFinderEventEvidence): string {
    return event.participationDate ? event.participationDate.slice(0, 10) : 'fecha no indicada';
  }

  participationLabel(value: string): string {
    return value
      .toLowerCase()
      .replace(/_/g, ' ')
      .replace(/^\p{L}/u, (letter) => letter.toUpperCase());
  }

  publicWarning(warning: string): string {
    if (warning.toLowerCase().includes('debil')) {
      return 'La evidencia disponible es limitada para esta consulta.';
    }
    return warning;
  }

  publicReason(reason: string): string {
    return reason
      .replace('Similitud semantica maxima de publicaciones:', 'Afinidad temática destacada:')
      .replace('Actividad reciente usada como senal de apoyo.', 'La actividad reciente se ha usado como señal de apoyo.')
      .replace('Solo se ha usado evidencia validada.', 'Solo se ha usado evidencia pública validada.');
  }

  private normalizeScore(score: number): number {
    return score > 1 ? Math.min(score / 100, 1) : Math.max(score, 0);
  }
}
