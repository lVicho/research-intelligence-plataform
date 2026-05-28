import { Component, computed, effect, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

import {
  TopicRecommendation,
  TopicRecommendationEvidence
} from '../../core/api/topic-recommendations-api.service';
import { TagChipComponent } from './tag-chip.component';

interface TopicRecommendationEvidenceView {
  topicLabel: string;
  evidence: TopicRecommendationEvidence;
}

@Component({
  selector: 'rip-topic-recommendation-card',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    TagChipComponent
  ],
  template: `
    <article class="topic-suggestion-card">
      <header class="card-header">
        <div class="header-copy">
          <p class="section-kicker">{{ eyebrow() }}</p>
          <h3>{{ title() }}</h3>
          <p>{{ subtitle() }}</p>
        </div>

        <button mat-stroked-button type="button" [disabled]="loading()" (click)="requestSuggestions.emit()">
          Sugerir temas
        </button>
      </header>

      @if (loading()) {
        <div class="loading-box">
          <span class="loading-dot" aria-hidden="true"></span>
          <p>{{ loadingLabel() }}</p>
        </div>
      } @else if (visibleSuggestions().length > 0) {
        <div class="suggestion-grid">
          @for (suggestion of visibleSuggestions(); track suggestion.id) {
            <button
              type="button"
              class="suggestion-chip"
              [class.selected]="isSelected(suggestion.id)"
              (click)="toggleSuggestion(suggestion.id)"
            >
              <span class="suggestion-label">{{ suggestion.topicLabel }}</span>
              <span class="suggestion-meta">
                @if (suggestion.confidence !== null) {
                  <span>{{ confidenceLabel(suggestion.confidence) }}</span>
                }
                @if (suggestion.reason) {
                  <span>{{ suggestion.reason }}</span>
                }
              </span>
            </button>
          }
        </div>

        <div class="actions action-row">
          <button mat-flat-button color="primary" type="button" [disabled]="selectedSuggestions().length === 0" (click)="applySelected()">
            Anadir seleccionados
          </button>
          <button mat-button type="button" (click)="ignore()">Ignorar</button>
          @if (evidenceItems().length > 0) {
            <button mat-button type="button" (click)="toggleEvidence()">
              {{ showEvidence() ? 'Ocultar evidencia' : 'Ver evidencia' }}
            </button>
          }
        </div>

        @if (advisoryNote()) {
          <p class="support-note">{{ advisoryNote() }}</p>
        }

        @if (showEvidence() && evidenceItems().length > 0) {
          <div class="evidence-grid">
            @for (item of evidenceItems(); track item.topicLabel + '-' + item.evidence.id) {
              <article class="evidence-card">
                <div class="evidence-topline">
                  <rip-tag-chip [label]="item.topicLabel" tone="type" />
                  <span>{{ item.evidence.label }}</span>
                </div>
                <p>{{ item.evidence.excerpt }}</p>
                @if (item.evidence.helper) {
                  <small>{{ item.evidence.helper }}</small>
                }
                @if (item.evidence.path) {
                  <a mat-button [routerLink]="item.evidence.path">Ver evidencia relacionada</a>
                }
              </article>
            }
          </div>
        }
      } @else if (requested()) {
        <div class="empty-box">
          <p>{{ emptyMessage() }}</p>
        </div>
      }

      @if (appliedTopics().length > 0) {
        <div class="applied-box">
          <span>{{ appliedLabel() }}</span>
          <div class="chip-list">
            @for (topic of appliedTopics(); track topic) {
              <rip-tag-chip [label]="topic" tone="status" />
            }
          </div>
          @if (appliedHelper()) {
            <p>{{ appliedHelper() }}</p>
          }
        </div>
      }
    </article>
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
    }

    .topic-suggestion-card {
      display: grid;
      gap: 14px;
      padding: 16px 18px;
      border: 1px solid #dce5ee;
      border-radius: 18px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(248, 251, 253, 0.96)),
        linear-gradient(90deg, rgba(20, 52, 73, 0.04), rgba(47, 111, 139, 0.02));
    }

    .card-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
    }

    .header-copy {
      display: grid;
      gap: 6px;
      min-width: 0;
    }

    .header-copy h3 {
      margin: 0;
      color: #142033;
      font-size: 1.02rem;
      line-height: 1.2;
    }

    .header-copy p:not(.section-kicker) {
      margin: 0;
      color: #667487;
      line-height: 1.55;
    }

    .loading-box,
    .empty-box,
    .applied-box {
      display: grid;
      gap: 10px;
      padding: 14px 16px;
      border: 1px solid #dbe7ef;
      border-radius: 16px;
      background: #ffffff;
    }

    .loading-box {
      grid-template-columns: auto minmax(0, 1fr);
      align-items: center;
    }

    .loading-box p,
    .empty-box p,
    .applied-box p,
    .support-note {
      margin: 0;
      color: #5f7083;
      line-height: 1.55;
    }

    .loading-dot {
      width: 10px;
      height: 10px;
      border-radius: 999px;
      background: #2f6f8b;
      box-shadow: 0 0 0 6px rgba(47, 111, 139, 0.14);
    }

    .suggestion-grid,
    .evidence-grid {
      display: grid;
      gap: 12px;
    }

    .suggestion-chip,
    .evidence-card {
      display: grid;
      gap: 8px;
      min-width: 0;
      padding: 14px 16px;
      border: 1px solid #dce5ee;
      border-radius: 16px;
      background: #ffffff;
    }

    .suggestion-chip {
      cursor: pointer;
      text-align: left;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .suggestion-chip:hover {
      border-color: #9fc0d1;
      box-shadow: 0 10px 22px rgba(20, 32, 51, 0.08);
      transform: translateY(-1px);
    }

    .suggestion-chip.selected {
      border-color: #6aa0bc;
      background: #f2f8fc;
      box-shadow: inset 0 0 0 1px rgba(47, 111, 139, 0.18);
    }

    .suggestion-label {
      color: #153247;
      font-weight: 760;
      line-height: 1.3;
    }

    .suggestion-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 12px;
      color: #667487;
      font-size: 0.84rem;
      line-height: 1.45;
    }

    .action-row,
    .chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .support-note {
      font-size: 0.9rem;
    }

    .evidence-grid {
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    }

    .evidence-card p,
    .evidence-card small {
      margin: 0;
      color: #5f7083;
      line-height: 1.55;
    }

    .evidence-topline {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      color: #4a6375;
      font-size: 0.82rem;
      font-weight: 700;
    }

    .applied-box span {
      color: #234a3d;
      font-size: 0.8rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    @media (max-width: 720px) {
      .card-header {
        display: grid;
      }
    }
  `]
})
export class TopicRecommendationCardComponent {
  readonly eyebrow = input('Revision asistida');
  readonly title = input('Sugerencias de temas');
  readonly subtitle = input('Analiza el contenido disponible y te deja decidir que etiquetas quieres revisar.');
  readonly loading = input(false);
  readonly requested = input(false);
  readonly suggestions = input<TopicRecommendation[]>([]);
  readonly emptyMessage = input('No hay sugerencias claras con el contenido disponible.');
  readonly loadingLabel = input('Analizando titulo y resumen...');
  readonly advisoryNote = input('');
  readonly appliedTopics = input<string[]>([]);
  readonly appliedLabel = input('Temas preparados');
  readonly appliedHelper = input('');

  readonly requestSuggestions = output<void>();
  readonly applySelectedTopics = output<TopicRecommendation[]>();

  readonly dismissed = signal(false);
  readonly showEvidence = signal(false);
  readonly selectedSuggestionIds = signal<string[]>([]);
  readonly visibleSuggestions = computed(() => this.dismissed() ? [] : this.suggestions());
  readonly selectedSuggestions = computed(() => {
    const selectedIds = new Set(this.selectedSuggestionIds());
    return this.visibleSuggestions().filter((suggestion) => selectedIds.has(suggestion.id));
  });
  readonly evidenceItems = computed<TopicRecommendationEvidenceView[]>(() => {
    const suggestions = this.selectedSuggestions().length > 0 ? this.selectedSuggestions() : this.visibleSuggestions();
    return suggestions.flatMap((suggestion) => suggestion.evidence.map((evidence) => ({
      topicLabel: suggestion.topicLabel,
      evidence
    })));
  });

  constructor() {
    effect(() => {
      const keys = this.suggestions().map((suggestion) => suggestion.id).join('|');
      const loading = this.loading();
      this.resetState(keys, loading);
    }, { allowSignalWrites: true });
  }

  isSelected(suggestionId: string): boolean {
    return this.selectedSuggestionIds().includes(suggestionId);
  }

  toggleSuggestion(suggestionId: string): void {
    this.selectedSuggestionIds.update((ids) =>
      ids.includes(suggestionId)
        ? ids.filter((id) => id !== suggestionId)
        : [...ids, suggestionId]
    );
  }

  toggleEvidence(): void {
    this.showEvidence.update((value) => !value);
  }

  applySelected(): void {
    const selected = this.selectedSuggestions();
    if (selected.length === 0) {
      return;
    }

    this.applySelectedTopics.emit(selected);
  }

  ignore(): void {
    this.dismissed.set(true);
    this.showEvidence.set(false);
    this.selectedSuggestionIds.set([]);
  }

  confidenceLabel(confidence: number): string {
    return `${Math.round(confidence * 100)}% de confianza`;
  }

  private resetState(_keys: string, loading: boolean): void {
    this.dismissed.set(false);
    this.showEvidence.set(false);
    this.selectedSuggestionIds.set([]);
    if (loading) {
      return;
    }
  }
}
