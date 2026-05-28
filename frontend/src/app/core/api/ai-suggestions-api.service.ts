import { Injectable, inject } from '@angular/core';
import { Observable, delay, map, of, throwError } from 'rxjs';

import { AiSuggestion, AiSuggestionEvidence, AiSuggestionFieldUpdate, ValidationEntityType } from './api-models';
import { TopicRecommendationsApiService } from './topic-recommendations-api.service';

export interface DataQualitySuggestionRequest {
  issueId: 'missing-doi' | 'missing-abstract' | 'missing-topics' | 'missing-public-summary';
  entityType: ValidationEntityType;
  entityId: number;
  title: string;
  subtitle?: string | null;
  currentValue?: string | null;
  path?: string | null;
  context?: string | null;
}

export type ValidationReviewRecommendation = 'VALIDATE' | 'REQUEST_CHANGES' | 'REVIEW_MANUALLY';
export type ValidationReviewCheckSeverity = 'HIGH' | 'MEDIUM' | 'LOW';
export type ValidationReviewCheckOutcome = 'positive' | 'negative' | 'neutral';

export interface ValidationReviewSuggestionCheck {
  id: string;
  severity: ValidationReviewCheckSeverity;
  title: string;
  detail: string;
  outcome: ValidationReviewCheckOutcome;
}

export interface ValidationReviewSuggestionEvidence {
  label: string;
  sourceLabel: string;
  sourceType: string;
  excerpt: string;
  helper?: string | null;
  path?: string | null;
}

export interface ValidationReviewSuggestionRequest {
  entityType: ValidationEntityType;
  entityId: number;
  title: string;
  subtitle?: string | null;
  researcherName?: string | null;
  researchUnitName?: string | null;
  path?: string | null;
  recommendation: ValidationReviewRecommendation;
  confidenceScore: number;
  summary: string;
  suggestedComment?: string | null;
  checks: ValidationReviewSuggestionCheck[];
  evidence: ValidationReviewSuggestionEvidence[];
}

@Injectable({ providedIn: 'root' })
export class AiSuggestionsApiService {
  private readonly topicRecommendationsApi = inject(TopicRecommendationsApiService);

  list(): Observable<AiSuggestion[]> {
    return of(cloneSuggestions()).pipe(delay(260));
  }

  suggestDataQualityImprovement(request: DataQualitySuggestionRequest): Observable<AiSuggestion> {
    const existing = this.findMatchingSuggestion(request);
    if (existing) {
      return of(structuredClone(existing)).pipe(delay(180));
    }

    if (request.issueId === 'missing-topics') {
      return this.topicRecommendationsApi.suggestTopics({
        entityType: 'DATA_QUALITY',
        entityId: request.entityId,
        title: request.title,
        summary: request.context ?? request.subtitle ?? request.title,
        description: request.subtitle ?? null
      }).pipe(
        delay(120),
        map((recommendations) => {
          const suggestion = this.createTopicSuggestion(request, recommendations);
          this.upsertSuggestion(suggestion);
          return structuredClone(suggestion);
        })
      );
    }

    const suggestion = this.createMetadataSuggestion(request);
    this.upsertSuggestion(suggestion);
    return of(structuredClone(suggestion)).pipe(delay(240));
  }

  suggestValidationReview(request: ValidationReviewSuggestionRequest): Observable<AiSuggestion> {
    const existing = this.findMatchingValidationReviewSuggestion(request);
    if (existing) {
      return of(structuredClone(existing)).pipe(delay(180));
    }

    const suggestion = this.createValidationReviewSuggestion(request);
    this.upsertSuggestion(suggestion);
    return of(structuredClone(suggestion)).pipe(delay(260));
  }

  accept(id: string): Observable<AiSuggestion> {
    return this.updateSuggestion(id, (suggestion) => ({
      ...suggestion,
      status: 'ACCEPTED',
      updatedAt: '2026-05-25T10:05:00Z',
      technicalData: {
        ...suggestion.technicalData,
        lastDecision: 'accepted'
      }
    }), 180);
  }

  acceptWithEdits(id: string, updates: AiSuggestionFieldUpdate[]): Observable<AiSuggestion> {
    return this.updateSuggestion(id, (suggestion) => ({
      ...suggestion,
      status: 'ACCEPTED_WITH_EDITS',
      updatedAt: '2026-05-25T10:12:00Z',
      proposedFields: suggestion.proposedFields.map((field) => {
        const update = updates.find((item) => item.key === field.key);
        return update ? { ...field, proposedValue: update.proposedValue } : field;
      }),
      technicalData: {
        ...suggestion.technicalData,
        lastDecision: 'accepted_with_edits',
        editedFieldCount: updates.length
      }
    }), 220);
  }

  reject(id: string): Observable<AiSuggestion> {
    return this.updateSuggestion(id, (suggestion) => ({
      ...suggestion,
      status: 'REJECTED',
      updatedAt: '2026-05-25T10:18:00Z',
      technicalData: {
        ...suggestion.technicalData,
        lastDecision: 'rejected'
      }
    }), 180);
  }

  private updateSuggestion(
    id: string,
    updater: (suggestion: AiSuggestion) => AiSuggestion,
    waitMs: number
  ): Observable<AiSuggestion> {
    const index = aiSuggestionStore.findIndex((suggestion) => suggestion.id === id);
    if (index < 0) {
      return throwError(() => new Error('not-found')).pipe(delay(waitMs));
    }

    aiSuggestionStore[index] = updater(aiSuggestionStore[index]);
    return of(null).pipe(
      delay(waitMs),
      map(() => structuredClone(aiSuggestionStore[index]))
    );
  }

  private findMatchingSuggestion(request: DataQualitySuggestionRequest): AiSuggestion | null {
    const fieldKeys = this.expectedFieldKeysForIssue(request.issueId);
    return aiSuggestionStore.find((suggestion) =>
      suggestion.target.entityType === request.entityType
      && suggestion.target.entityId === request.entityId
      && suggestion.proposedFields.some((field) => fieldKeys.includes(field.key))
    ) ?? null;
  }

  private findMatchingValidationReviewSuggestion(request: ValidationReviewSuggestionRequest): AiSuggestion | null {
    return aiSuggestionStore.find((suggestion) =>
      suggestion.type === 'VALIDATION_REVIEW'
      && suggestion.target.entityType === request.entityType
      && suggestion.target.entityId === request.entityId
    ) ?? null;
  }

  private createMetadataSuggestion(request: DataQualitySuggestionRequest): AiSuggestion {
    const confidenceScore = request.issueId === 'missing-doi'
      ? 0.81
      : request.issueId === 'missing-public-summary'
        ? 0.72
        : 0.76;
    const generatedAt = '2026-05-25T11:20:00Z';
    const id = `quality-${request.issueId}-${request.entityType.toLowerCase()}-${request.entityId}`;

    return {
      id,
      title: this.suggestionTitleForIssue(request),
      type: 'PUBLICATION_METADATA',
      status: 'PENDING_REVIEW',
      explanation: this.explanationForIssue(request.issueId),
      reviewerNote: this.reviewerNoteForIssue(request.issueId),
      target: {
        entityType: request.entityType,
        entityId: request.entityId,
        title: request.title,
        subtitle: request.subtitle ?? null,
        ownerResearcherId: null,
        ownerResearcherName: null,
        path: request.path ?? null
      },
      proposedFields: this.proposedFieldsForIssue(request),
      evidence: this.buildEvidence(request, request.issueId === 'missing-doi'
        ? 'Conviene verificar el DOI y la referencia contra la fuente editorial antes de guardarlos.'
        : 'Úsalo como borrador de trabajo y ajusta el texto al tono editorial del registro.'),
      providerInfo: {
        provider: request.issueId === 'missing-doi' ? 'Mock AI' : 'Ollama',
        model: request.issueId === 'missing-doi' ? 'catalog-enricher-v2' : 'llama3.1:8b',
        generatedAt,
        promptProfile: this.promptProfileForIssue(request.issueId),
        confidenceSummary: this.confidenceSummaryForIssue(request.issueId, confidenceScore),
        caution: 'La sugerencia ayuda a acelerar la revisión, pero no modifica el registro hasta que una persona la compruebe y la guarde por el flujo habitual.'
      },
      visibilityNote: 'Uso interno. La propuesta es un borrador y requiere revisión humana antes de incorporarse al registro.',
      technicalData: {
        issueId: request.issueId,
        confidenceScore,
        generatedFrom: 'data-quality-assistant-ui',
        sourceSignals: [request.title, request.subtitle, request.context].filter((value) => !!value)
      },
      createdAt: generatedAt,
      updatedAt: generatedAt
    };
  }

  private createValidationReviewSuggestion(request: ValidationReviewSuggestionRequest): AiSuggestion {
    const generatedAt = new Date().toISOString();
    const id = `validation-review-${request.entityType.toLowerCase()}-${request.entityId}`;

    return {
      id,
      title: `Revisión asistida para ${request.title}`,
      type: 'VALIDATION_REVIEW',
      status: 'PENDING_REVIEW',
      explanation: request.summary,
      reviewerNote: 'La IA orienta la revisión con señales visibles en la bandeja. La decisión final sigue siendo responsabilidad de la persona validadora.',
      target: {
        entityType: request.entityType,
        entityId: request.entityId,
        title: request.title,
        subtitle: request.subtitle ?? null,
        ownerResearcherId: null,
        ownerResearcherName: request.researcherName ?? null,
        path: request.path ?? null
      },
      proposedFields: [
        {
          key: 'validation_recommendation',
          label: 'Recomendación',
          currentValue: 'Sin orientación previa',
          proposedValue: this.validationRecommendationLabel(request.recommendation),
          helper: 'Orientación interna para apoyar la decisión del validador.',
          multiline: false,
          editable: false
        },
        {
          key: 'validator_comment',
          label: 'Comentario sugerido',
          currentValue: 'Sin comentario',
          proposedValue: request.suggestedComment ?? null,
          helper: 'Puedes ajustarlo antes de usarlo en una solicitud de cambios o en un rechazo.',
          multiline: true,
          editable: true
        }
      ],
      evidence: request.evidence.map((evidence, index) => ({
        id: `${id}-e${index + 1}`,
        label: evidence.label,
        sourceLabel: evidence.sourceLabel,
        sourceType: evidence.sourceType,
        excerpt: evidence.excerpt,
        helper: evidence.helper ?? null,
        path: evidence.path ?? null
      })),
      providerInfo: {
        provider: 'Ollama',
        model: 'validator-review-local',
        generatedAt,
        promptProfile: 'validation-review-assistant-v1',
        confidenceSummary: this.validationConfidenceSummary(request.recommendation, request.confidenceScore),
        caution: 'La IA no valida automáticamente. La acción final debe revisarse y ejecutarse manualmente desde la bandeja.'
      },
      visibilityNote: 'Uso interno. La sugerencia acompaña la revisión humana y no cambia el estado del registro por sí sola.',
      technicalData: {
        recommendation: request.recommendation,
        confidenceScore: request.confidenceScore,
        checks: request.checks,
        suggestedComment: request.suggestedComment ?? null,
        summary: request.summary,
        generatedFrom: 'validation-inbox-assistant-ui',
        researchUnitName: request.researchUnitName ?? null
      },
      createdAt: generatedAt,
      updatedAt: generatedAt
    };
  }

  private createTopicSuggestion(
    request: DataQualitySuggestionRequest,
    recommendations: Array<{ topicLabel: string; confidence: number | null; evidence: Array<{ id: string; label: string; excerpt: string; helper: string | null; path: string | null }> }>
  ): AiSuggestion {
    const generatedAt = '2026-05-25T11:24:00Z';
    const id = `quality-${request.issueId}-${request.entityType.toLowerCase()}-${request.entityId}`;
    const bestConfidence = recommendations[0]?.confidence ?? 0.68;
    const proposedTopics = recommendations.slice(0, 4).map((recommendation) => recommendation.topicLabel);

    return {
      id,
      title: `Preparar temas sugeridos para ${request.title}`,
      type: 'PUBLICATION_TOPIC',
      status: 'PENDING_REVIEW',
      explanation: 'La IA propone temas a partir del título y del contexto disponible. Conviene contrastarlos con la taxonomía institucional antes de aplicarlos.',
      reviewerNote: 'Revisa que la etiqueta no duplique variantes ya normalizadas y que el nivel de especificidad encaje con el catálogo institucional.',
      target: {
        entityType: request.entityType,
        entityId: request.entityId,
        title: request.title,
        subtitle: request.subtitle ?? null,
        ownerResearcherId: null,
        ownerResearcherName: null,
        path: request.path ?? null
      },
      proposedFields: [
        {
          key: 'topic_labels',
          label: 'Temas sugeridos',
          currentValue: request.currentValue ?? 'Sin temas',
          proposedValue: proposedTopics.join(', '),
          helper: 'Puedes editar la lista antes de aceptarla.',
          multiline: true,
          editable: true
        }
      ],
      evidence: recommendations
        .flatMap((recommendation) => recommendation.evidence)
        .slice(0, 4)
        .map((evidence) => ({
          id: evidence.id,
          label: evidence.label,
          sourceLabel: evidence.label,
          sourceType: request.entityType,
          excerpt: evidence.excerpt,
          helper: evidence.helper,
          path: evidence.path
        })),
      providerInfo: {
        provider: 'Ollama',
        model: 'topic-tagger-local',
        generatedAt,
        promptProfile: 'data-quality-topic-repair-v1',
        confidenceSummary: this.confidenceSummaryForIssue('missing-topics', bestConfidence),
        caution: 'La propuesta no reemplaza la revisión taxonómica. Aplícala solo después de comprobar que los temas son pertinentes.'
      },
      visibilityNote: 'Uso interno. Los temas sugeridos no se incorporan al registro hasta que una persona los revise y los guarde.',
      technicalData: {
        issueId: request.issueId,
        confidenceScore: bestConfidence,
        generatedFrom: 'data-quality-assistant-ui',
        candidateTopics: proposedTopics
      },
      createdAt: generatedAt,
      updatedAt: generatedAt
    };
  }

  private suggestionTitleForIssue(request: DataQualitySuggestionRequest): string {
    switch (request.issueId) {
      case 'missing-doi':
        return `Completar DOI y referencia breve para ${request.title}`;
      case 'missing-public-summary':
        return `Preparar resumen público para ${request.title}`;
      default:
        return `Redactar resumen para ${request.title}`;
    }
  }

  private explanationForIssue(issueId: DataQualitySuggestionRequest['issueId']): string {
    switch (issueId) {
      case 'missing-doi':
        return 'La IA detectó un DOI y un detalle de fuente plausibles a partir del contexto disponible. Deben verificarse antes de guardarlos en el registro.';
      case 'missing-public-summary':
        return 'La propuesta resume el contenido en un tono más apto para visibilidad pública. Conviene revisar que no incluya afirmaciones no sustentadas.';
      default:
        return 'La IA propone un borrador de resumen para acelerar la revisión editorial. Debe ajustarse antes de usarse como texto final.';
    }
  }

  private reviewerNoteForIssue(issueId: DataQualitySuggestionRequest['issueId']): string {
    switch (issueId) {
      case 'missing-doi':
        return 'Comprueba DOI, fuente y edición exacta antes de aceptar la sugerencia.';
      case 'missing-public-summary':
        return 'Mantén un tono descriptivo, evita sobreafirmaciones y confirma que el texto sea adecuado para visibilidad pública.';
      default:
        return 'Revisa el tono, completa datos concretos si faltan y confirma que el resumen refleje con precisión el trabajo registrado.';
    }
  }

  private proposedFieldsForIssue(request: DataQualitySuggestionRequest) {
    if (request.issueId === 'missing-doi') {
      return [
        {
          key: 'doi',
          label: 'DOI',
          currentValue: request.currentValue ?? 'Sin dato',
          proposedValue: this.syntheticDoi(request.entityId),
          helper: 'Borrador generado a partir del contexto disponible.',
          multiline: false,
          editable: true
        },
        {
          key: 'source_detail',
          label: 'Detalle de fuente',
          currentValue: 'Sin dato',
          proposedValue: request.subtitle ?? 'Referencia institucional pendiente de verificación editorial',
          helper: 'Sirve como referencia corta mientras se contrasta con la fuente original.',
          multiline: false,
          editable: true
        }
      ];
    }

    if (request.issueId === 'missing-public-summary') {
      return [
        {
          key: 'public_summary',
          label: 'Resumen público',
          currentValue: request.currentValue ?? 'Sin dato',
          proposedValue: this.buildPublicSummary(request.title, request.subtitle, request.context),
          helper: 'Borrador breve pensado para visibilidad pública y revisión humana.',
          multiline: true,
          editable: true
        }
      ];
    }

    return [
      {
        key: 'abstract_text',
        label: 'Resumen',
        currentValue: request.currentValue ?? 'Sin dato',
        proposedValue: this.buildAbstractText(request.title, request.subtitle, request.context),
        helper: 'Borrador de trabajo para revisar y completar en la ficha de la publicación.',
        multiline: true,
        editable: true
      }
    ];
  }

  private buildEvidence(request: DataQualitySuggestionRequest, helper: string): AiSuggestionEvidence[] {
    const evidence: AiSuggestionEvidence[] = [
      {
        id: `${request.entityType}-${request.entityId}-title`,
        label: 'Título del registro',
        sourceLabel: request.title,
        sourceType: request.entityType,
        excerpt: request.title,
        helper,
        path: request.path ?? null
      }
    ];

    if (request.subtitle) {
      evidence.push({
        id: `${request.entityType}-${request.entityId}-subtitle`,
        label: 'Contexto del catálogo',
        sourceLabel: 'Detalle del registro',
        sourceType: 'CATALOG',
        excerpt: request.subtitle,
        helper: 'Úsalo como pista contextual, no como validación final.',
        path: request.path ?? null
      });
    }

    if (request.context) {
      evidence.push({
        id: `${request.entityType}-${request.entityId}-context`,
        label: 'Señal operativa',
        sourceLabel: 'Incidencia de calidad',
        sourceType: 'DATA_QUALITY',
        excerpt: request.context,
        helper: 'La sugerencia se construyó a partir de esta incidencia visible en la revisión actual.',
        path: null
      });
    }

    return evidence;
  }

  private promptProfileForIssue(issueId: DataQualitySuggestionRequest['issueId']): string {
    switch (issueId) {
      case 'missing-doi':
        return 'data-quality-doi-repair-v1';
      case 'missing-public-summary':
        return 'data-quality-public-summary-v1';
      case 'missing-topics':
        return 'data-quality-topic-repair-v1';
      default:
        return 'data-quality-abstract-repair-v1';
    }
  }

  private confidenceSummaryForIssue(issueId: DataQualitySuggestionRequest['issueId'], confidenceScore: number): string {
    const percentage = `${Math.round(confidenceScore * 100)}%`;
    switch (issueId) {
      case 'missing-doi':
        return `Coincidencia bibliográfica estimada en ${percentage}; requiere contraste manual con la referencia original.`;
      case 'missing-topics':
        return `Alineación temática estimada en ${percentage} a partir del título y el contexto disponible.`;
      case 'missing-public-summary':
        return `Cobertura narrativa estimada en ${percentage}; conviene ajustar el tono antes de publicarlo.`;
      default:
        return `Cobertura del contenido estimada en ${percentage}; úsalo como punto de partida para la edición final.`;
    }
  }

  private validationRecommendationLabel(recommendation: ValidationReviewRecommendation): string {
    switch (recommendation) {
      case 'VALIDATE':
        return 'Parece validable';
      case 'REQUEST_CHANGES':
        return 'Conviene solicitar cambios';
      default:
        return 'Revisión manual recomendada';
    }
  }

  private validationConfidenceSummary(
    recommendation: ValidationReviewRecommendation,
    confidenceScore: number
  ): string {
    const percentage = `${Math.round(confidenceScore * 100)}%`;
    switch (recommendation) {
      case 'VALIDATE':
        return `La IA estima en ${percentage} que el registro podría cerrarse, pero la validación final sigue siendo manual.`;
      case 'REQUEST_CHANGES':
        return `La IA estima en ${percentage} que conviene pedir ajustes antes de cerrar la revisión.`;
      default:
        return `La IA estima en ${percentage} que el caso necesita contraste manual adicional antes de decidir.`;
    }
  }

  private syntheticDoi(entityId: number): string {
    return `10.24873/rip.${new Date('2026-01-01T00:00:00Z').getUTCFullYear()}.${entityId}`;
  }

  private buildAbstractText(title: string, subtitle?: string | null, context?: string | null): string {
    const lead = `Este trabajo aborda ${this.lowercaseSentence(title)}.`;
    const support = subtitle
      ? ` El registro actual lo sitúa en ${this.lowercaseSentence(subtitle)}.`
      : '';
    const close = context
      ? ` La propuesta se ha preparado para cubrir la incidencia detectada: ${this.lowercaseSentence(context)}.`
      : ' La propuesta sirve como borrador inicial para completar el resumen pendiente y facilitar la revisión editorial.';
    return `${lead}${support}${close}`.trim();
  }

  private buildPublicSummary(title: string, subtitle?: string | null, context?: string | null): string {
    const base = `${title} presenta una contribución de investigación`;
    const detail = subtitle ? ` en ${this.lowercaseSentence(subtitle)}` : '';
    const support = context ? ` y ayuda a resolver la incidencia detectada en esta revisión de calidad.` : '.';
    return `${base}${detail}${support}`.replace(/\.\s*$/, '.');
  }

  private lowercaseSentence(value: string): string {
    const trimmed = value.trim();
    if (!trimmed) {
      return 'el contenido disponible';
    }
    return trimmed.charAt(0).toLocaleLowerCase('es-ES') + trimmed.slice(1);
  }

  private expectedFieldKeysForIssue(issueId: DataQualitySuggestionRequest['issueId']): string[] {
    switch (issueId) {
      case 'missing-doi':
        return ['doi'];
      case 'missing-public-summary':
        return ['public_summary'];
      case 'missing-topics':
        return ['topic_labels'];
      default:
        return ['abstract_text'];
    }
  }

  private upsertSuggestion(nextSuggestion: AiSuggestion): void {
    const index = aiSuggestionStore.findIndex((suggestion) => suggestion.id === nextSuggestion.id);
    if (index >= 0) {
      aiSuggestionStore[index] = nextSuggestion;
      return;
    }
    aiSuggestionStore = [nextSuggestion, ...aiSuggestionStore];
  }
}

let aiSuggestionStore: AiSuggestion[] = [
  {
    id: 'researcher-summary-27',
    title: 'Actualizar resumen público del investigador con evidencia reciente',
    type: 'RESEARCHER_SUMMARY',
    status: 'PENDING_REVIEW',
    explanation: 'La propuesta resume tres publicaciones recientes y una línea estable de trabajo en salud digital clínica. Puede ahorrar edición manual, pero conviene verificar el tono y eliminar cualquier afirmación demasiado concluyente.',
    reviewerNote: 'Mantener un tono descriptivo y confirmar que el nuevo resumen no publique información privada antes de aprobarlo.',
    target: {
      entityType: 'RESEARCHER',
      entityId: 27,
      title: 'Laura Martín Quesada',
      subtitle: 'Área principal: analítica clínica y salud digital',
      ownerResearcherId: 27,
      ownerResearcherName: 'Laura Martín Quesada',
      path: '/admin/investigadores/27'
    },
    proposedFields: [
      {
        key: 'public_summary',
        label: 'Resumen público',
        currentValue: 'Investiga aplicaciones de analítica de datos en entornos sanitarios.',
        proposedValue: 'Investiga modelos de analítica clínica y salud digital con foco en evaluación de impacto, seguimiento longitudinal y apoyo a la toma de decisiones en servicios hospitalarios.',
        helper: 'Texto visible si el perfil se publica tras validación.',
        multiline: true,
        editable: true
      },
      {
        key: 'featured_topics',
        label: 'Temas destacados',
        currentValue: 'salud digital',
        proposedValue: 'salud digital, analítica clínica, evaluación de impacto',
        helper: 'Lista breve para la ficha pública y la guía de expertos.',
        multiline: false,
        editable: true
      }
    ],
    evidence: [
      {
        id: 'researcher-summary-27-e1',
        label: 'Publicación institucional',
        sourceLabel: 'Publicación #201',
        sourceType: 'PUBLICATION',
        excerpt: 'Predicción temprana de adherencia en programas preventivos con señales clínicas y longitudinales.',
        helper: 'Coincide con evaluación de impacto y seguimiento longitudinal.',
        path: '/admin/publicaciones/201'
      },
      {
        id: 'researcher-summary-27-e2',
        label: 'Publicación institucional',
        sourceLabel: 'Publicación #154',
        sourceType: 'PUBLICATION',
        excerpt: 'Diseño de rutas formativas con señales clínicas y educativas en servicios sanitarios.',
        helper: 'Refuerza la línea de salud digital aplicada.',
        path: '/admin/publicaciones/154'
      },
      {
        id: 'researcher-summary-27-e3',
        label: 'Perfil ORCID',
        sourceLabel: 'ORCID confirmado',
        sourceType: 'ORCID',
        excerpt: 'Las palabras clave más repetidas en el perfil externo se alinean con analítica clínica y aprendizaje longitudinal.',
        helper: 'Úsalo como contraste, no como fuente automática final.',
        path: null
      }
    ],
    providerInfo: {
      provider: 'Ollama',
      model: 'llama3.1:8b',
      generatedAt: '2026-05-24T16:40:00Z',
      promptProfile: 'researcher-summary-draft-v1',
      confidenceSummary: 'Coincidencia temática media-alta basada en publicaciones recientes.',
      caution: 'La sugerencia sintetiza patrones del contenido disponible. No sustituye revisión editorial ni validación humana.'
    },
    visibilityNote: 'Uso interno. El borrador sigue siendo privado hasta que una persona lo acepte y se publique por el flujo habitual.',
    technicalData: {
      sourceWindow: '2023-2026',
      matchedPublicationIds: [201, 154, 222],
      supportingTopicMatches: ['salud digital', 'analítica clínica', 'evaluación de impacto'],
      promptProfile: 'researcher-summary-draft-v1',
      temperature: 0.2
    },
    createdAt: '2026-05-24T16:40:00Z',
    updatedAt: '2026-05-24T16:40:00Z'
  },
  {
    id: 'publication-metadata-182',
    title: 'Completar metadatos faltantes de la publicación 182',
    type: 'PUBLICATION_METADATA',
    status: 'PENDING_REVIEW',
    explanation: 'La IA detectó DOI y detalle de fuente en referencias consistentes, pero debe comprobarse que correspondan exactamente a esta edición antes de guardar cambios.',
    reviewerNote: 'Revisar DOI, fuente y año contra la referencia original. No aplicar de forma automática si hay varias ediciones del mismo trabajo.',
    target: {
      entityType: 'PUBLICATION',
      entityId: 182,
      title: 'Modelos multimodales para estratificación tumoral',
      subtitle: 'Publicación pendiente de enriquecimiento bibliográfico',
      ownerResearcherId: 12,
      ownerResearcherName: 'Lucía Serrano Vega',
      path: '/admin/publicaciones/182'
    },
    proposedFields: [
      {
        key: 'doi',
        label: 'DOI',
        currentValue: 'Sin dato',
        proposedValue: '10.24873/jpm.2025.18217',
        helper: 'Detectado en dos referencias convergentes.',
        multiline: false,
        editable: true
      },
      {
        key: 'source_detail',
        label: 'Detalle de fuente',
        currentValue: 'Sin dato',
        proposedValue: 'Journal of Precision Medicine, volumen 18, número 2',
        helper: 'Texto candidato para completar la referencia.',
        multiline: false,
        editable: true
      }
    ],
    evidence: [
      {
        id: 'publication-metadata-182-e1',
        label: 'Referencia cruzada',
        sourceLabel: 'Journal of Precision Medicine',
        sourceType: 'CATALOG',
        excerpt: 'La referencia muestra el mismo título, año 2025 y un DOI compatible con la versión institucional.',
        helper: 'Revisar si coincide también la autoría completa.',
        path: null
      },
      {
        id: 'publication-metadata-182-e2',
        label: 'Publicación relacionada',
        sourceLabel: 'Publicación #167',
        sourceType: 'PUBLICATION',
        excerpt: 'Se cita el mismo trabajo con DOI y volumen completos en una revisión del mismo grupo.',
        helper: 'Puede servir como pista, no como validación final.',
        path: '/admin/publicaciones/167'
      }
    ],
    providerInfo: {
      provider: 'Mock AI',
      model: 'catalog-enricher-v2',
      generatedAt: '2026-05-24T18:10:00Z',
      promptProfile: 'publication-metadata-suggestion-v1',
      confidenceSummary: 'Alta coincidencia bibliográfica, pendiente de verificación humana.',
      caution: 'La similitud bibliográfica no garantiza identidad exacta. La revisión humana decide si el metadato se aplica.'
    },
    visibilityNote: 'Uso interno. La sugerencia no modifica la publicación hasta que alguien confirme y guarde el cambio.',
    technicalData: {
      matchStrategy: 'title-year-reference-overlap',
      candidateSourceCount: 2,
      sourcePriority: ['catalog', 'institutional-citation'],
      confidenceScore: 0.84
    },
    createdAt: '2026-05-24T18:10:00Z',
    updatedAt: '2026-05-24T18:10:00Z'
  },
  {
    id: 'publication-topic-145',
    title: 'Añadir tema sugerido a la publicación sobre vigilancia ambiental',
    type: 'PUBLICATION_TOPIC',
    status: 'ACCEPTED_WITH_EDITS',
    explanation: 'El modelo propuso un tema alineado con el resumen, pero durante la revisión anterior se suavizó la etiqueta para que encaje mejor con la taxonomía interna.',
    reviewerNote: 'Ya revisada. Puede usarse como referencia para futuros flujos de aceptación con edición.',
    target: {
      entityType: 'PUBLICATION',
      entityId: 145,
      title: 'Series temporales para vigilancia de riesgo climático y sanitario',
      subtitle: 'Tema candidato en taxonomía institucional',
      ownerResearcherId: null,
      ownerResearcherName: null,
      path: '/admin/publicaciones/145'
    },
    proposedFields: [
      {
        key: 'topic_label',
        label: 'Tema sugerido',
        currentValue: 'riesgo ambiental',
        proposedValue: 'vigilancia climática y sanitaria',
        helper: 'Versión ajustada manualmente antes de aceptar.',
        multiline: false,
        editable: true
      }
    ],
    evidence: [
      {
        id: 'publication-topic-145-e1',
        label: 'Resumen de publicación',
        sourceLabel: 'Publicación #145',
        sourceType: 'PUBLICATION',
        excerpt: 'El resumen habla de vigilancia territorial, eventos extremos y señales sanitarias en series temporales.',
        helper: 'La revisión humana evitó una etiqueta demasiado amplia.',
        path: '/admin/publicaciones/145'
      }
    ],
    providerInfo: {
      provider: 'Ollama',
      model: 'mistral:7b',
      generatedAt: '2026-05-23T12:18:00Z',
      promptProfile: 'publication-topic-tagging-v1',
      confidenceSummary: 'Buena alineación semántica con el resumen del trabajo.',
      caution: 'Las etiquetas propuestas pueden simplificar demasiado la taxonomía y deben revisarse manualmente.'
    },
    visibilityNote: 'Uso interno. Solo la etiqueta aceptada y guardada pasa al catálogo habitual.',
    technicalData: {
      candidateTopics: ['salud ambiental', 'vigilancia climática y sanitaria', 'series temporales territoriales'],
      selectedByReviewer: 'vigilancia climática y sanitaria',
      confidenceScore: 0.79
    },
    createdAt: '2026-05-23T12:18:00Z',
    updatedAt: '2026-05-24T09:05:00Z'
  },
  {
    id: 'unit-summary-61',
    title: 'Borrador de resumen público para unidad de modelización climática',
    type: 'RESEARCH_UNIT_SUMMARY',
    status: 'REJECTED',
    explanation: 'La propuesta mezclaba actividad investigadora con una promesa institucional demasiado rotunda. Se conserva solo como ejemplo de revisión crítica.',
    reviewerNote: 'Mantener como rechazada: el texto original exageraba capacidades y no distinguía evidencia validada de orientación futura.',
    target: {
      entityType: 'RESEARCH_UNIT',
      entityId: 61,
      title: 'Centro de Modelización Climática',
      subtitle: 'Resumen público pendiente de nueva redacción',
      ownerResearcherId: null,
      ownerResearcherName: null,
      path: '/admin/unidades/61'
    },
    proposedFields: [
      {
        key: 'public_description',
        label: 'Descripción pública',
        currentValue: 'Centro dedicado al estudio de modelos climáticos y series temporales.',
        proposedValue: 'Centro líder que anticipa con precisión el impacto sanitario de cualquier evento climático extremo.',
        helper: 'Se rechazó por tono excesivamente concluyente.',
        multiline: true,
        editable: true
      }
    ],
    evidence: [
      {
        id: 'unit-summary-61-e1',
        label: 'Producción reciente',
        sourceLabel: 'Publicación #222',
        sourceType: 'PUBLICATION',
        excerpt: 'El trabajo más reciente muestra capacidad analítica, pero no justifica afirmaciones absolutas sobre predicción.',
        helper: 'La evidencia apoya una descripción prudente.',
        path: '/admin/publicaciones/222'
      }
    ],
    providerInfo: {
      provider: 'Mock AI',
      model: 'unit-summary-draft-v1',
      generatedAt: '2026-05-22T11:05:00Z',
      promptProfile: 'unit-summary-public-v1',
      confidenceSummary: 'Propuesta útil para arrancar una redacción, pero con riesgo de sobreafirmación.',
      caution: 'Los textos públicos sugeridos deben revisarse con especial cuidado para no atribuir a la IA autoridad editorial.'
    },
    visibilityNote: 'Uso interno. Esta sugerencia no se publicó y quedó rechazada tras revisión humana.',
    technicalData: {
      riskFlags: ['overclaiming', 'public-tone'],
      evidencePublicationIds: [222],
      confidenceScore: 0.58
    },
    createdAt: '2026-05-22T11:05:00Z',
    updatedAt: '2026-05-22T13:22:00Z'
  },
  {
    id: 'researcher-topic-12',
    title: 'Sugerir línea temática emergente para investigadora de bioinformática',
    type: 'RESEARCHER_TOPIC',
    status: 'PENDING_REVIEW',
    explanation: 'Se detectó un patrón repetido entre biomarcadores, estratificación tumoral y análisis multimodal. La sugerencia sirve como punto de partida y debe contrastarse con la taxonomía vigente.',
    reviewerNote: 'Validar contra normalización de temas y evitar duplicar etiquetas ya consolidadas con otra variante.',
    target: {
      entityType: 'RESEARCHER',
      entityId: 12,
      title: 'Lucía Serrano Vega',
      subtitle: 'Perfil interno con actividad reciente en oncología de precisión',
      ownerResearcherId: 12,
      ownerResearcherName: 'Lucía Serrano Vega',
      path: '/admin/investigadores/12'
    },
    proposedFields: [
      {
        key: 'emerging_topic',
        label: 'Tema emergente',
        currentValue: 'biomarcadores',
        proposedValue: 'estratificación tumoral multimodal',
        helper: 'Puede alimentar la guía de expertos tras revisión.',
        multiline: false,
        editable: true
      },
      {
        key: 'topic_rationale',
        label: 'Justificación editorial',
        currentValue: 'Sin dato',
        proposedValue: 'Aparece en publicaciones recientes junto con biomarcadores transcriptómicos y modelos predictivos de precisión.',
        helper: 'Texto interno para apoyar la revisión.',
        multiline: true,
        editable: true
      }
    ],
    evidence: [
      {
        id: 'researcher-topic-12-e1',
        label: 'Publicación institucional',
        sourceLabel: 'Publicación #182',
        sourceType: 'PUBLICATION',
        excerpt: 'Modelos multimodales para estratificación tumoral.',
        helper: 'Coincidencia directa con la etiqueta sugerida.',
        path: '/admin/publicaciones/182'
      },
      {
        id: 'researcher-topic-12-e2',
        label: 'Publicación institucional',
        sourceLabel: 'Publicación #167',
        sourceType: 'PUBLICATION',
        excerpt: 'Biomarcadores transcriptómicos en cohortes clínicas.',
        helper: 'Apoya la conexión con oncología de precisión.',
        path: '/admin/publicaciones/167'
      }
    ],
    providerInfo: {
      provider: 'Ollama',
      model: 'phi4-mini',
      generatedAt: '2026-05-25T08:32:00Z',
      promptProfile: 'researcher-topic-suggestion-v1',
      confidenceSummary: 'Patrón consistente en dos publicaciones recientes, con necesidad de normalización temática.',
      caution: 'La propuesta ayuda a explorar patrones, pero la taxonomía institucional sigue dependiendo de revisión humana.'
    },
    visibilityNote: 'Uso interno. La sugerencia puede revisarse también en futuros contextos de investigador si se habilita esa bandeja.',
    technicalData: {
      candidateTopics: ['oncología de precisión', 'estratificación tumoral multimodal', 'biomarcadores transcriptómicos'],
      evidencePublicationIds: [182, 167],
      confidenceScore: 0.76
    },
    createdAt: '2026-05-25T08:32:00Z',
    updatedAt: '2026-05-25T08:32:00Z'
  }
];

function cloneSuggestions(): AiSuggestion[] {
  return structuredClone(aiSuggestionStore);
}
