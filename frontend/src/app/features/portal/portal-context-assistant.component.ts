import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, HostListener, Input, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import {
  PortalContextAssistantPublicationEvidence,
  PortalContextAssistantResearcherEvidence,
  PortalContextAssistantResponse,
  PortalContextAssistantScope,
  PortalContextAssistantSearchRequest,
  PortalContextAssistantUnitEvidence
} from '../../core/api/api-models';
import { PortalApiService } from '../../core/api/portal-api.service';

type EvidenceKind = 'publication' | 'researcher' | 'unit';

interface EvidenceReference {
  key: string;
  id: number;
  kind: EvidenceKind;
  eyebrow: string;
  label: string;
  citationLabel: string | null;
  description: string | null;
  topics: string[];
  route: string;
}

interface AnswerSegment {
  index: number;
  text: string;
  evidenceKey: string | null;
  route: string | null;
  strong: boolean;
}

interface AnswerLine {
  index: number;
  blank: boolean;
  heading: boolean;
  listItem: boolean;
  segments: AnswerSegment[];
}

interface AnswerMatch {
  start: number;
  end: number;
  text: string;
  reference: EvidenceReference;
}

interface InlinePart {
  text: string;
  strong: boolean;
}

@Component({
  selector: 'rip-portal-context-assistant',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule
  ],
  template: `
    <section class="context-assistant">
      <div class="assistant-callout">
        <div>
          <p class="assistant-kicker">Asistente contextual</p>
          <p>{{ helperText }}</p>
        </div>
        <button mat-stroked-button type="button" [disabled]="disabled" (click)="openDialog()">
          {{ triggerLabel }}
        </button>
      </div>

      @if (dialogOpen()) {
        <div class="assistant-backdrop" (click)="closeDialog()">
          <div
            class="assistant-modal"
            role="dialog"
            aria-modal="true"
            [attr.aria-labelledby]="titleId"
            (click)="$event.stopPropagation()"
          >
            <header class="assistant-header">
              <div>
                <p class="assistant-kicker">Asistente contextual</p>
                <h3 [id]="titleId">{{ contextTitle }}</h3>
                <p>Pregunta usando solo la información pública validada de este contexto.</p>
              </div>
              <button mat-button type="button" (click)="closeDialog()">Cerrar</button>
            </header>

            <form class="question-form" (submit)="ask($event)">
              <mat-form-field appearance="outline">
                <mat-label>Haz una pregunta</mat-label>
                <textarea
                  matInput
                  rows="4"
                  [formControl]="questionControl"
                  placeholder="Ej. ¿Qué evidencias justifican estos resultados?"
                ></textarea>
              </mat-form-field>
              <div class="question-actions">
                <span>El asistente reconstruye el contexto en servidor y muestra evidencias citadas cuando están disponibles.</span>
                <button mat-flat-button color="primary" type="submit" [disabled]="loading() || questionControl.invalid">
                  {{ loading() ? 'Preguntando...' : 'Preguntar' }}
                </button>
              </div>
            </form>

            @if (loading()) {
              <section class="loading-block" aria-live="polite">
                <span class="spinner" aria-hidden="true"></span>
                <div>
                  <strong>Generando respuesta contextual...</strong>
                  <p>Estoy recuperando el contexto validado y preparando una respuesta con citas.</p>
                </div>
              </section>
            }

            @if (errorMessage()) {
              <p class="assistant-error">{{ errorMessage() }}</p>
            }

            @if (response(); as assistantResponse) {
              <div class="assistant-results">
                <article class="answer-block">
                  <p class="assistant-kicker">Respuesta</p>
                  <div class="answer-content">
                    @for (line of answerLines(); track line.index) {
                      @if (line.blank) {
                        <div class="answer-gap" aria-hidden="true"></div>
                      } @else if (line.heading) {
                        <h5 class="answer-heading">
                          @for (segment of line.segments; track segment.index) {
                            <ng-container
                              [ngTemplateOutlet]="answerSegmentTemplate"
                              [ngTemplateOutletContext]="{ segment: segment }"
                            ></ng-container>
                          }
                        </h5>
                      } @else {
                        <p class="answer-line" [class.list-item]="line.listItem">
                          @for (segment of line.segments; track segment.index) {
                            <ng-container
                              [ngTemplateOutlet]="answerSegmentTemplate"
                              [ngTemplateOutletContext]="{ segment: segment }"
                            ></ng-container>
                          }
                        </p>
                      }
                    }
                  </div>
                </article>

                @if (displayWarnings().length > 0) {
                  <div class="warning-list" aria-label="Avisos del asistente">
                    @for (warning of displayWarnings(); track warning) {
                      <p>{{ warning }}</p>
                    }
                  </div>
                }

                <section class="evidence-block" aria-labelledby="assistant-cited-evidence-title">
                  <div class="assistant-section-title">
                    <p class="assistant-kicker">Evidencias citadas</p>
                    <h4 id="assistant-cited-evidence-title">Evidencias citadas en la respuesta</h4>
                  </div>

                  @if (hasCitedEvidence()) {
                    <div class="evidence-grid">
                      @for (evidence of citedEvidence(); track evidence.key) {
                        <a
                          class="evidence-card"
                          [class.active]="isEvidenceActive(evidence.key)"
                          [routerLink]="evidence.route"
                          (mouseenter)="setActiveEvidence(evidence.key)"
                          (mouseleave)="clearActiveEvidence()"
                        >
                          <span class="evidence-kind">{{ evidence.eyebrow }}</span>
                          <strong>
                            @if (evidence.citationLabel) {
                              <span class="citation-chip">{{ evidence.citationLabel }}</span>
                            }
                            {{ evidence.label }}
                          </strong>
                          @if (evidence.description) {
                            <p>{{ evidence.description }}</p>
                          }
                          @if (evidence.topics.length > 0) {
                            <p>{{ evidence.topics.slice(0, 3).join(', ') }}</p>
                          }
                        </a>
                      }
                    </div>
                  } @else {
                    <p class="assistant-muted">La respuesta no ha citado publicaciones, investigadores o unidades concretas.</p>
                  }
                </section>

                <section class="context-block">
                  <button class="link-button" type="button" (click)="toggleEvidenceUsed()">
                    {{ evidenceUsedVisible() ? 'Ocultar evidencias utilizadas para responder' : 'Ver evidencias utilizadas para responder' }}
                  </button>
                  @if (evidenceUsedVisible()) {
                    <div class="context-summary">
                      <p class="assistant-kicker">Evidencias utilizadas para responder</p>
                      <p>Resumen del contexto completo que el backend reconstruyó antes de generar la respuesta.</p>
                      @for (item of assistantResponse.evidenceSummary; track item) {
                        <p>{{ item }}</p>
                      }
                    </div>
                  }
                </section>

                <section class="technical-block">
                  <button class="link-button" type="button" (click)="toggleTechnicalInfo()">
                    Información técnica
                  </button>
                  @if (technicalVisible()) {
                    <p>Proveedor: {{ assistantResponse.provider }} · Modelo: {{ assistantResponse.model }}</p>
                  }
                </section>
              </div>
            }
          </div>
        </div>
      }

      <ng-template #answerSegmentTemplate let-segment="segment">
        @if (segment.evidenceKey) {
          <a
            class="answer-reference"
            [class.strong]="segment.strong"
            [routerLink]="segment.route"
            (mouseenter)="setActiveEvidence(segment.evidenceKey)"
            (mouseleave)="clearActiveEvidence()"
          >{{ segment.text }}</a>
        } @else if (segment.strong) {
          <strong class="answer-strong">{{ segment.text }}</strong>
        } @else {
          <span>{{ segment.text }}</span>
        }
      </ng-template>
    </section>
  `,
  styles: [`
    .context-assistant,
    .assistant-results,
    .answer-content {
      display: grid;
      gap: 16px;
    }

    .assistant-callout,
    .assistant-modal,
    .answer-block,
    .warning-list,
    .context-summary,
    .loading-block {
      border: 1px solid var(--portal-border, #c9dadd);
      border-radius: 8px;
      background: var(--portal-surface, #fff);
    }

    .assistant-callout {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 16px;
      padding: 16px 18px;
      box-shadow: 0 12px 28px rgba(16, 37, 48, 0.06);
    }

    .assistant-backdrop {
      position: fixed;
      inset: 0;
      z-index: 1200;
      display: grid;
      align-items: start;
      justify-items: center;
      padding: min(6vh, 48px) 18px 28px;
      background: rgba(7, 20, 27, 0.54);
      overflow-y: auto;
    }

    .assistant-modal {
      display: grid;
      gap: 18px;
      width: min(980px, 100%);
      max-height: calc(100vh - 56px);
      padding: clamp(18px, 3vw, 28px);
      background:
        linear-gradient(180deg, color-mix(in srgb, var(--portal-accent-soft, #dff0f1) 52%, transparent), transparent 230px),
        var(--portal-surface, #fff);
      box-shadow: 0 28px 72px rgba(7, 20, 27, 0.32);
      overflow-y: auto;
    }

    .assistant-callout p,
    .assistant-header p,
    .question-actions span,
    .assistant-muted,
    .technical-block p,
    .context-summary p,
    .loading-block p {
      margin: 0;
      color: var(--portal-muted, #587078);
      font-size: 0.92rem;
      line-height: 1.55;
    }

    .assistant-kicker {
      margin: 0 0 4px;
      color: var(--portal-accent, #0f6479);
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .assistant-header,
    .question-actions,
    .assistant-section-title,
    .loading-block {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
    }

    .assistant-header h3,
    .assistant-section-title h4 {
      margin: 0;
      color: var(--portal-text, #102530);
      font-size: 1.15rem;
      line-height: 1.25;
    }

    .question-form {
      display: grid;
      gap: 12px;
    }

    .question-form mat-form-field {
      width: 100%;
    }

    .loading-block {
      justify-content: flex-start;
      padding: 14px 16px;
      border-color: color-mix(in srgb, var(--portal-accent, #0f6479) 30%, var(--portal-border, #c9dadd));
      background: color-mix(in srgb, var(--portal-accent-soft, #dff0f1) 48%, #fff);
    }

    .loading-block strong {
      display: block;
      margin-bottom: 4px;
      color: var(--portal-text, #102530);
    }

    .spinner {
      width: 22px;
      height: 22px;
      flex: 0 0 auto;
      border: 3px solid color-mix(in srgb, var(--portal-accent, #0f6479) 18%, #fff);
      border-top-color: var(--portal-accent, #0f6479);
      border-radius: 999px;
      animation: spin 800ms linear infinite;
    }

    .answer-block {
      padding: 16px;
      border-color: color-mix(in srgb, var(--portal-accent, #0f6479) 38%, var(--portal-border, #c9dadd));
      background: color-mix(in srgb, var(--portal-accent-soft, #dff0f1) 58%, #fff);
    }

    .answer-content {
      gap: 8px;
      color: var(--portal-text, #102530);
      line-height: 1.65;
    }

    .answer-heading,
    .answer-line {
      margin: 0;
    }

    .answer-heading {
      color: var(--portal-text, #102530);
      font-size: 1rem;
      font-weight: 800;
      line-height: 1.35;
    }

    .answer-strong,
    .answer-reference.strong {
      font-weight: 800;
    }

    .answer-line.list-item {
      position: relative;
      padding-left: 18px;
    }

    .answer-line.list-item::before {
      position: absolute;
      left: 2px;
      color: var(--portal-accent, #0f6479);
      content: "•";
    }

    .answer-gap {
      height: 4px;
    }

    .answer-reference {
      border-radius: 4px;
      color: var(--portal-link, #07586b);
      font-weight: 760;
      text-decoration: none;
      transition: background-color 140ms ease, box-shadow 140ms ease;
    }

    .answer-reference:hover,
    .answer-reference:focus-visible {
      background: color-mix(in srgb, var(--portal-accent-soft, #dff0f1) 86%, #fff);
      box-shadow: 0 0 0 2px color-mix(in srgb, var(--portal-accent, #0f6479) 22%, transparent);
      outline: none;
    }

    .warning-list {
      display: grid;
      gap: 8px;
      padding: 12px 14px;
      border-color: #e0c46f;
      background: #fff8df;
    }

    .warning-list p {
      margin: 0;
      color: #695018;
      font-size: 0.9rem;
    }

    .evidence-block,
    .context-block,
    .technical-block {
      display: grid;
      gap: 12px;
    }

    .evidence-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 12px;
    }

    .evidence-card {
      display: grid;
      gap: 7px;
      padding: 12px;
      border: 1px solid var(--portal-border, #c9dadd);
      border-radius: 8px;
      color: inherit;
      text-decoration: none;
      background: var(--portal-surface, #fff);
      transition: border-color 140ms ease, background-color 140ms ease, box-shadow 140ms ease;
    }

    .evidence-card:hover,
    .evidence-card:focus-visible,
    .evidence-card.active {
      border-color: var(--portal-accent, #0f6479);
      background: var(--portal-accent-soft, #dff0f1);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--portal-accent, #0f6479) 16%, transparent);
      outline: none;
    }

    .evidence-kind,
    .citation-chip {
      width: fit-content;
      border-radius: 999px;
      font-size: 0.76rem;
      font-weight: 800;
      line-height: 1.1;
    }

    .evidence-kind {
      color: var(--portal-accent, #0f6479);
      text-transform: uppercase;
    }

    .citation-chip {
      display: inline-flex;
      margin-right: 6px;
      padding: 3px 7px;
      background: var(--portal-accent, #0f6479);
      color: #fff;
      vertical-align: 1px;
    }

    .evidence-card strong {
      color: var(--portal-text, #102530);
      line-height: 1.35;
    }

    .evidence-card p {
      margin: 0;
      color: var(--portal-muted, #587078);
      font-size: 0.86rem;
      line-height: 1.45;
    }

    .context-summary {
      display: grid;
      gap: 8px;
      padding: 14px;
    }

    .assistant-error {
      margin: 0;
      color: #8d2a22;
      font-weight: 700;
    }

    .link-button {
      justify-self: start;
      border: 0;
      padding: 0;
      background: transparent;
      color: var(--portal-link, #07586b);
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }

    .link-button:hover,
    .link-button:focus-visible {
      text-decoration: underline;
      outline: none;
    }

    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }

    @media (max-width: 760px) {
      .assistant-callout,
      .assistant-header,
      .question-actions,
      .assistant-section-title,
      .loading-block {
        grid-template-columns: 1fr;
      }

      .assistant-callout,
      .assistant-header,
      .question-actions,
      .loading-block {
        display: grid;
      }

      .assistant-callout button,
      .question-actions button {
        width: 100%;
      }

      .assistant-backdrop {
        align-items: stretch;
        padding: 12px;
      }

      .assistant-modal {
        max-height: calc(100vh - 24px);
      }
    }
  `]
})
export class PortalContextAssistantComponent {
  private readonly portalApi = inject(PortalApiService);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) contextScope!: PortalContextAssistantScope;
  @Input() targetId: number | null = null;
  @Input() searchRequest: PortalContextAssistantSearchRequest | null = null;
  @Input() triggerLabel = 'Preguntar';
  @Input() contextTitle = 'Asistente contextual';
  @Input() helperText = 'El asistente responde sobre este contexto con información pública validada.';
  @Input() disabled = false;
  @Input() maxEvidence = 12;

  readonly titleId = `portal-context-assistant-title-${Math.random().toString(36).slice(2)}`;
  readonly questionControl = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly dialogOpen = signal(false);
  readonly loading = signal(false);
  readonly response = signal<PortalContextAssistantResponse | null>(null);
  readonly errorMessage = signal('');
  readonly evidenceUsedVisible = signal(false);
  readonly technicalVisible = signal(false);
  readonly activeEvidenceKey = signal<string | null>(null);

  readonly citedEvidence = computed(() => {
    const response = this.response();
    if (!response) {
      return [] as EvidenceReference[];
    }
    const answer = response.answer;
    return [
      ...this.publicationEvidence(response.citedPublications),
      ...this.researcherEvidence(response.citedResearchers, answer),
      ...this.unitEvidence(response.citedUnits, answer)
    ];
  });

  readonly hasCitedEvidence = computed(() => this.citedEvidence().length > 0);

  readonly displayWarnings = computed(() => {
    const response = this.response();
    if (!response) {
      return [] as string[];
    }
    return response.warnings.filter((warning) => !this.isValidationScopeNotice(warning));
  });

  readonly answerLines = computed(() => {
    const response = this.response();
    return response ? this.toAnswerLines(response.answer, this.citedEvidence()) : [];
  });

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.closeDialog();
  }

  openDialog(): void {
    if (this.disabled) {
      return;
    }
    this.dialogOpen.set(true);
  }

  closeDialog(): void {
    this.dialogOpen.set(false);
  }

  toggleEvidenceUsed(): void {
    this.evidenceUsedVisible.update((visible) => !visible);
  }

  toggleTechnicalInfo(): void {
    this.technicalVisible.update((visible) => !visible);
  }

  setActiveEvidence(key: string | null): void {
    this.activeEvidenceKey.set(key);
  }

  clearActiveEvidence(): void {
    this.activeEvidenceKey.set(null);
  }

  isEvidenceActive(key: string): boolean {
    return this.activeEvidenceKey() === key;
  }

  ask(event?: Event): void {
    event?.preventDefault();
    const question = this.questionControl.value.trim();
    if (!question || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.response.set(null);
    this.errorMessage.set('');
    this.evidenceUsedVisible.set(false);
    this.technicalVisible.set(false);
    this.activeEvidenceKey.set(null);
    this.portalApi.askContextAssistant({
      contextScope: this.contextScope,
      targetId: this.targetId,
      question,
      searchRequest: this.searchRequest,
      maxEvidence: this.maxEvidence
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.response.set(response);
          this.loading.set(false);
        },
        error: () => {
          this.errorMessage.set('No se ha podido generar la respuesta.');
          this.loading.set(false);
        }
      });
  }

  private publicationEvidence(publications: PortalContextAssistantPublicationEvidence[]): EvidenceReference[] {
    return [...publications]
      .sort((left, right) => left.citationIndex - right.citationIndex)
      .map((publication) => ({
        key: `publication-${publication.id}`,
        id: publication.id,
        kind: 'publication' as const,
        eyebrow: 'Publicación',
        label: publication.title,
        citationLabel: `[${publication.citationIndex}]`,
        description: [publication.year || 's. f.', publication.source].filter(Boolean).join(' · ') || null,
        topics: publication.topics,
        route: publication.path
      }));
  }

  private researcherEvidence(
    researchers: PortalContextAssistantResearcherEvidence[],
    answer: string
  ): EvidenceReference[] {
    return researchers
      .filter((researcher) => this.answerMentions(answer, researcher.name))
      .map((researcher) => ({
        key: `researcher-${researcher.id}`,
        id: researcher.id,
        kind: 'researcher' as const,
        eyebrow: 'Investigador',
        label: researcher.name,
        citationLabel: null,
        description: researcher.affiliationName,
        topics: [],
        route: researcher.path
      }));
  }

  private unitEvidence(units: PortalContextAssistantUnitEvidence[], answer: string): EvidenceReference[] {
    return units
      .filter((unit) => this.answerMentions(answer, unit.name))
      .map((unit) => ({
        key: `unit-${unit.id}`,
        id: unit.id,
        kind: 'unit' as const,
        eyebrow: 'Unidad',
        label: unit.name,
        citationLabel: null,
        description: unit.type,
        topics: [],
        route: unit.path
      }));
  }

  private toAnswerLines(answer: string, evidence: EvidenceReference[]): AnswerLine[] {
    return answer.split(/\r?\n/).map((rawLine, index) => {
      const trimmed = rawLine.trim();
      if (!trimmed) {
        return { index, blank: true, heading: false, listItem: false, segments: [] };
      }
      const withoutHeadingPrefix = trimmed.replace(/^#{1,4}\s+/, '');
      const listItem = /^[-*•]\s+/.test(withoutHeadingPrefix);
      const text = withoutHeadingPrefix.replace(/^[-*•]\s+/, '');
      const plainText = this.stripMarkdownEmphasis(text);
      const heading = !listItem && (
        /^#{1,4}\s+/.test(trimmed)
        || this.isWrappedInStrongMarkdown(text)
        || (plainText.endsWith(':') && plainText.length <= 90)
      );
      return {
        index,
        blank: false,
        heading,
        listItem,
        segments: this.toAnswerSegments(heading ? this.stripWrappingStrongMarkdown(text) : text, evidence)
      };
    });
  }

  private toAnswerSegments(line: string, evidence: EvidenceReference[]): AnswerSegment[] {
    const normalizedLine = this.normalizeCitationMarkers(line, evidence);
    const segments: AnswerSegment[] = [];
    for (const part of this.inlineParts(normalizedLine)) {
      const matches = this.answerMatches(part.text, evidence);
      let cursor = 0;
      for (const match of matches) {
        if (match.start > cursor) {
          segments.push(this.textSegment(segments.length, part.text.slice(cursor, match.start), part.strong));
        }
        segments.push({
          index: segments.length,
          text: match.text,
          evidenceKey: match.reference.key,
          route: match.reference.route,
          strong: part.strong
        });
        cursor = match.end;
      }
      if (cursor < part.text.length) {
        segments.push(this.textSegment(segments.length, part.text.slice(cursor), part.strong));
      }
    }
    return segments.length > 0 ? segments : [this.textSegment(0, normalizedLine, false)];
  }

  private answerMatches(line: string, evidence: EvidenceReference[]): AnswerMatch[] {
    const rawMatches: AnswerMatch[] = [];
    for (const reference of evidence) {
      if (reference.kind === 'publication') {
        for (const marker of this.publicationMarkers(reference)) {
          const markerRegex = new RegExp(this.escapeRegex(marker), 'gi');
          for (const match of line.matchAll(markerRegex)) {
            if (match.index !== undefined) {
              rawMatches.push({
                start: match.index,
                end: match.index + match[0].length,
                text: reference.citationLabel ?? match[0],
                reference
              });
            }
          }
        }
      }

      if (reference.label.length >= 3) {
        const labelRegex = new RegExp(this.escapeRegex(reference.label), 'gi');
        for (const match of line.matchAll(labelRegex)) {
          if (match.index !== undefined) {
            const renderedText = reference.kind === 'publication' && reference.citationLabel
              && !this.hasNearbyPublicationMarker(line, match.index + match[0].length, reference)
              ? `${match[0]} ${reference.citationLabel}`
              : match[0];
            rawMatches.push({
              start: match.index,
              end: match.index + match[0].length,
              text: renderedText,
              reference
            });
          }
        }
      }
    }
    return this.removeOverlappingMatches(rawMatches);
  }

  private normalizeCitationMarkers(line: string, evidence: EvidenceReference[]): string {
    const publicationsById = new Map(
      evidence
        .filter((reference) => reference.kind === 'publication')
        .map((reference) => [reference.id, reference])
    );
    return line.replace(/\[(?:pub|publication):([^\]]+)\]/gi, (_marker, rawIds: string) => {
      const labels = this.publicationIdsFromMarker(rawIds)
        .map((id) => publicationsById.get(id)?.citationLabel)
        .filter((label): label is string => Boolean(label));
      return labels.length > 0 ? labels.join(', ') : '';
    });
  }

  private publicationIdsFromMarker(value: string): number[] {
    const ids: number[] = [];
    for (const match of value.matchAll(/\d+/g)) {
      const id = Number(match[0]);
      if (Number.isSafeInteger(id) && !ids.includes(id)) {
        ids.push(id);
      }
    }
    return ids;
  }

  private publicationMarkers(reference: EvidenceReference): string[] {
    const markers = [`[pub:${reference.id}]`, `[publication:${reference.id}]`];
    if (reference.citationLabel) {
      markers.push(reference.citationLabel);
    }
    return markers;
  }

  private removeOverlappingMatches(matches: AnswerMatch[]): AnswerMatch[] {
    const sorted = [...matches].sort((left, right) =>
      left.start - right.start || (right.end - right.start) - (left.end - left.start)
    );
    const accepted: AnswerMatch[] = [];
    let cursor = -1;
    for (const match of sorted) {
      if (match.start < cursor) {
        continue;
      }
      accepted.push(match);
      cursor = match.end;
    }
    return accepted;
  }

  private hasNearbyPublicationMarker(line: string, end: number, reference: EvidenceReference): boolean {
    const nearby = line.slice(Math.max(0, end - 64), end + 32).toLowerCase();
    return nearby.includes(`[pub:${reference.id}]`)
      || nearby.includes(`[publication:${reference.id}]`)
      || (reference.citationLabel !== null && nearby.includes(reference.citationLabel.toLowerCase()));
  }

  private inlineParts(line: string): InlinePart[] {
    const parts: InlinePart[] = [];
    const strongRegex = /\*\*([^*]+)\*\*/g;
    let cursor = 0;
    for (const match of line.matchAll(strongRegex)) {
      const start = match.index ?? 0;
      if (start > cursor) {
        parts.push({ text: line.slice(cursor, start), strong: false });
      }
      parts.push({ text: match[1], strong: true });
      cursor = start + match[0].length;
    }
    if (cursor < line.length) {
      parts.push({ text: line.slice(cursor), strong: false });
    }
    return parts.length > 0 ? parts : [{ text: line, strong: false }];
  }

  private textSegment(index: number, text: string, strong: boolean): AnswerSegment {
    return { index, text, evidenceKey: null, route: null, strong };
  }

  private answerMentions(answer: string, label: string): boolean {
    const normalizedLabel = this.normalize(label);
    return normalizedLabel.length >= 3 && this.normalize(answer).includes(normalizedLabel);
  }

  private normalize(value: string | null): string {
    return (value ?? '')
      .normalize('NFD')
      .replace(/\p{M}+/gu, '')
      .toLowerCase();
  }

  private stripMarkdownEmphasis(value: string): string {
    return value.replace(/\*\*/g, '').trim();
  }

  private stripWrappingStrongMarkdown(value: string): string {
    return value.trim().replace(/^\*\*(.+)\*\*$/s, '$1').trim();
  }

  private isWrappedInStrongMarkdown(value: string): boolean {
    return /^\*\*[^*].*[^*]\*\*$/.test(value.trim());
  }

  private isValidationScopeNotice(value: string): boolean {
    const normalized = this.normalize(value);
    return normalized.includes('solo evidencia') && normalized.includes('validada');
  }

  private escapeRegex(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }
}
