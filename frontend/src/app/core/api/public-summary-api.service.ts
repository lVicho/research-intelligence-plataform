import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AiSuggestion,
  PersistedAiSuggestion,
  PersistedAiSuggestionStatus,
  PublicSummaryEvidence,
  PublicSummaryGenerateRequest,
  PublicSummaryGenerateResponse,
  PublicSummaryStyle,
  PublicSummaryTargetType
} from './api-models';
import { PageResponse } from './api-models';

interface PublicSummaryPayload {
  targetType?: string;
  targetId?: number;
  field?: string;
  summary?: string;
  style?: string;
  audience?: string;
  requiresHumanReview?: boolean;
}

interface PublicSummaryEvidenceEnvelope {
  evidence?: PublicSummaryEvidence[];
  warnings?: string[];
}

export interface PublicSummarySuggestionContext {
  currentSummary: string | null;
  targetTitle: string;
  targetSubtitle?: string | null;
  ownerResearcherName?: string | null;
  entityLabel?: string | null;
}

export interface PublicSummarySuggestionState {
  suggestionId: number;
  rawStatus: PersistedAiSuggestionStatus;
  rawPayload: PublicSummaryPayload;
  warnings: string[];
  summaryText: string;
  style: PublicSummaryStyle;
  suggestion: AiSuggestion;
}

@Injectable({ providedIn: 'root' })
export class PublicSummaryApiService {
  private readonly http = inject(HttpClient);
  private readonly publicSummaryBaseUrl = `${environment.apiBaseUrl}/ai/public-summary`;
  private readonly aiSuggestionsBaseUrl = `${environment.apiBaseUrl}/ai-suggestions`;

  generate(
    request: PublicSummaryGenerateRequest,
    context: PublicSummarySuggestionContext
  ): Observable<PublicSummarySuggestionState> {
    return this.http.post<PublicSummaryGenerateResponse>(`${this.publicSummaryBaseUrl}/generate`, request).pipe(
      map((response) => this.fromGeneratedResponse(response, request, context))
    );
  }

  latestForTarget(
    targetType: PublicSummaryTargetType,
    targetId: number,
    context: PublicSummarySuggestionContext
  ): Observable<PublicSummarySuggestionState | null> {
    const params = new HttpParams()
      .set('targetType', targetType)
      .set('targetId', String(targetId))
      .set('suggestionType', 'PUBLIC_SUMMARY')
      .set('page', '0')
      .set('size', '20');

    return this.http.get<PageResponse<PersistedAiSuggestion>>(this.aiSuggestionsBaseUrl, { params }).pipe(
      map((response) => {
        const latest = [...response.content]
          .sort((left, right) => this.rankSuggestion(right) - this.rankSuggestion(left))[0] ?? null;
        return latest ? this.mapPersistedSuggestion(latest, context) : null;
      })
    );
  }

  accept(state: PublicSummarySuggestionState): Observable<PublicSummarySuggestionState> {
    return this.http.post<PersistedAiSuggestion>(`${this.aiSuggestionsBaseUrl}/${state.suggestionId}/accept`, {}).pipe(
      map((response) => this.mapPersistedSuggestion(response, this.contextFromState(state)))
    );
  }

  reject(state: PublicSummarySuggestionState): Observable<PublicSummarySuggestionState> {
    return this.http.post<PersistedAiSuggestion>(`${this.aiSuggestionsBaseUrl}/${state.suggestionId}/reject`, {}).pipe(
      map((response) => this.mapPersistedSuggestion(response, this.contextFromState(state)))
    );
  }

  editAndAccept(state: PublicSummarySuggestionState, summary: string): Observable<PublicSummarySuggestionState> {
    const payload: PublicSummaryPayload = {
      ...state.rawPayload,
      summary
    };

    return this.http.post<PersistedAiSuggestion>(`${this.aiSuggestionsBaseUrl}/${state.suggestionId}/edit-and-accept`, {
      proposedDataJson: JSON.stringify(payload)
    }).pipe(
      map((response) => this.mapPersistedSuggestion(response, this.contextFromState(state, summary)))
    );
  }

  private fromGeneratedResponse(
    response: PublicSummaryGenerateResponse,
    request: PublicSummaryGenerateRequest,
    context: PublicSummarySuggestionContext
  ): PublicSummarySuggestionState {
    const payload: PublicSummaryPayload = {
      targetType: request.targetType,
      targetId: request.targetId,
      field: this.reviewedField(request.targetType),
      summary: response.summary,
      style: request.style,
      audience: request.audience ?? 'PUBLIC',
      requiresHumanReview: true
    };

    return {
      suggestionId: response.createdSuggestionId,
      rawStatus: 'GENERATED',
      rawPayload: payload,
      warnings: response.warnings,
      summaryText: response.summary,
      style: request.style,
      suggestion: this.buildUiSuggestion(
        response.createdSuggestionId,
        'GENERATED',
        payload,
        response.evidence,
        response.warnings,
        context,
        response.provider,
        response.model,
        new Date().toISOString()
      )
    };
  }

  private mapPersistedSuggestion(
    response: PersistedAiSuggestion,
    context: PublicSummarySuggestionContext
  ): PublicSummarySuggestionState {
    const payload = this.parsePayload(response.proposedDataJson);
    const evidenceEnvelope = this.parseEvidenceEnvelope(response.evidenceJson);
    const summaryText = (payload.summary ?? '').trim();
    const style = this.parseStyle(payload.style);

    return {
      suggestionId: response.id,
      rawStatus: response.status,
      rawPayload: payload,
      warnings: evidenceEnvelope.warnings ?? [],
      summaryText,
      style,
      suggestion: this.buildUiSuggestion(
        response.id,
        response.status,
        payload,
        evidenceEnvelope.evidence ?? [],
        evidenceEnvelope.warnings ?? [],
        context,
        response.modelProvider,
        response.modelName,
        response.createdAt,
        response.reviewedAt,
        response.reviewComment
      )
    };
  }

  private buildUiSuggestion(
    suggestionId: number,
    rawStatus: PersistedAiSuggestionStatus,
    payload: PublicSummaryPayload,
    evidence: PublicSummaryEvidence[],
    warnings: string[],
    context: PublicSummarySuggestionContext,
    provider: string,
    model: string,
    createdAt: string,
    reviewedAt?: string | null,
    reviewComment?: string | null
  ): AiSuggestion {
    const entityType = this.mapEntityType(payload.targetType);
    const createdOrReviewedAt = reviewedAt ?? createdAt;

    return {
      id: String(suggestionId),
      title: `Resumen público sugerido para ${context.targetTitle}`,
      type: 'PUBLIC_SUMMARY',
      status: this.mapStatus(rawStatus),
      explanation: 'La sugerencia se ha preparado a partir de evidencia pública o validada disponible para este registro.',
      reviewerNote: 'Revísala con criterio editorial antes de aceptarla. La IA no publica nada de forma automática.',
      target: {
        entityType,
        entityId: payload.targetId ?? 0,
        title: context.targetTitle,
        subtitle: context.targetSubtitle ?? null,
        ownerResearcherId: null,
        ownerResearcherName: context.ownerResearcherName ?? null,
        path: null
      },
      proposedFields: [
        {
          key: 'public_summary',
          label: context.entityLabel === 'Organización externa' ? 'Descripción pública' : 'Resumen público',
          currentValue: context.currentSummary,
          proposedValue: payload.summary ?? '',
          helper: 'Borrador interno pendiente de revisión humana.',
          multiline: true,
          editable: true
        }
      ],
      evidence: evidence.map((item, index) => ({
        id: `${suggestionId}-evidence-${index + 1}`,
        label: item.label,
        sourceLabel: item.label,
        sourceType: this.evidenceSourceType(item.reference),
        excerpt: item.value,
        helper: null,
        path: null
      })),
      providerInfo: {
        provider,
        model,
        generatedAt: createdAt,
        promptProfile: this.promptProfileLabel(payload.style),
        confidenceSummary: warnings.length > 0
          ? warnings.join(' ')
          : 'La propuesta se apoya en evidencia pública o validada disponible en el sistema.',
        caution: 'La sugerencia sigue siendo privada hasta que una persona la revise y acepte expresamente.'
      },
      visibilityNote: 'Solo la versión aceptada pasa al campo público correspondiente.',
      technicalData: {
        suggestionId,
        rawStatus,
        targetType: payload.targetType ?? null,
        entityLabel: context.entityLabel ?? null,
        style: payload.style ?? null,
        audience: payload.audience ?? null,
        warnings,
        reviewComment: reviewComment ?? null,
        reviewedAt: reviewedAt ?? null
      },
      createdAt,
      updatedAt: createdOrReviewedAt
    };
  }

  private contextFromState(
    state: PublicSummarySuggestionState,
    nextCurrentSummary?: string
  ): PublicSummarySuggestionContext {
    return {
      currentSummary: nextCurrentSummary ?? state.suggestion.proposedFields[0]?.currentValue ?? null,
      targetTitle: state.suggestion.target.title,
      targetSubtitle: state.suggestion.target.subtitle,
      ownerResearcherName: state.suggestion.target.ownerResearcherName,
      entityLabel: this.entityLabelFromSuggestion(state.suggestion)
    };
  }

  private entityLabelFromSuggestion(suggestion: AiSuggestion): string | null {
    const technicalEntityLabel = suggestion.technicalData['entityLabel'];
    return typeof technicalEntityLabel === 'string' ? technicalEntityLabel : null;
  }

  private reviewedField(targetType?: string): string {
    switch (targetType) {
      case 'RESEARCHER':
        return 'publicProfileSummary';
      case 'RESEARCH_UNIT':
      case 'EXTERNAL_ORGANIZATION':
        return 'publicDescription';
      default:
        return 'publicSummary';
    }
  }

  private mapEntityType(targetType?: string) {
    switch (targetType) {
      case 'RESEARCHER':
        return 'RESEARCHER';
      case 'RESEARCH_UNIT':
      case 'EXTERNAL_ORGANIZATION':
        return 'RESEARCH_UNIT';
      default:
        return 'PUBLICATION';
    }
  }

  private mapStatus(status: PersistedAiSuggestionStatus) {
    switch (status) {
      case 'ACCEPTED':
        return 'ACCEPTED';
      case 'EDITED':
        return 'ACCEPTED_WITH_EDITS';
      case 'REJECTED':
      case 'EXPIRED':
        return 'REJECTED';
      default:
        return 'PENDING_REVIEW';
    }
  }

  private promptProfileLabel(style?: string): string {
    switch (style) {
      case 'SHORT':
        return 'resumen-publico-corto';
      case 'EXTENDED':
        return 'resumen-publico-extendido';
      default:
        return 'resumen-publico-estandar';
    }
  }

  private evidenceSourceType(reference: string): string {
    if (reference.startsWith('publication:')) {
      return 'PUBLICATION';
    }
    if (reference.startsWith('researchUnit:')) {
      return 'RESEARCH_UNIT';
    }
    if (reference.startsWith('researcher:')) {
      return 'RESEARCHER';
    }
    if (reference.startsWith('affiliation:')) {
      return 'RESEARCHER_AFFILIATION';
    }
    if (reference.startsWith('topic:')) {
      return 'TOPIC';
    }
    return 'SYSTEM';
  }

  private parseStyle(value?: string): PublicSummaryStyle {
    return value === 'SHORT' || value === 'EXTENDED' ? value : 'STANDARD';
  }

  private parsePayload(value: string): PublicSummaryPayload {
    try {
      const parsed = JSON.parse(value) as PublicSummaryPayload;
      return parsed ?? {};
    } catch {
      return {};
    }
  }

  private parseEvidenceEnvelope(value: string | null): PublicSummaryEvidenceEnvelope {
    if (!value) {
      return {};
    }

    try {
      return (JSON.parse(value) as PublicSummaryEvidenceEnvelope) ?? {};
    } catch {
      return {};
    }
  }

  private rankSuggestion(suggestion: PersistedAiSuggestion): number {
    const timestamp = suggestion.reviewedAt ?? suggestion.createdAt;
    return Number(new Date(timestamp));
  }
}
