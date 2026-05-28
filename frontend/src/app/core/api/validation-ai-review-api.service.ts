import { Injectable, inject } from '@angular/core';
import { map, Observable } from 'rxjs';

import { AiSuggestion, ValidationEntityType, ValidationItemDetail } from './api-models';
import {
  AiSuggestionsApiService,
  ValidationReviewCheckSeverity,
  ValidationReviewRecommendation,
  ValidationReviewSuggestionCheck,
  ValidationReviewSuggestionEvidence
} from './ai-suggestions-api.service';

export interface ValidationAiReviewCheck extends ValidationReviewSuggestionCheck {}

export interface ValidationAiReview {
  recommendation: ValidationReviewRecommendation;
  confidenceScore: number;
  summary: string;
  suggestedComment: string | null;
  checks: ValidationAiReviewCheck[];
  evidence: ValidationReviewSuggestionEvidence[];
  aiSuggestion: AiSuggestion;
  generatedAt: string;
  provider: string;
  model: string;
  caution: string;
}

interface CompletenessSignal {
  ratio: number;
  missingLabels: string[];
}

@Injectable({ providedIn: 'root' })
export class ValidationAiReviewApiService {
  private readonly aiSuggestionsApi = inject(AiSuggestionsApiService);

  analyze(detail: ValidationItemDetail): Observable<ValidationAiReview> {
    const checks = this.buildChecks(detail);
    const recommendation = this.recommendationFor(detail, checks);
    const confidenceScore = this.confidenceFor(recommendation, checks);
    const summary = this.summaryFor(recommendation, checks);
    const suggestedComment = this.suggestedCommentFor(recommendation, checks);
    const evidence = this.buildEvidence(detail);
    const path = this.targetPath(detail.item.entityType, detail.item.entityId, detail.item.researcherId);

    return this.aiSuggestionsApi.suggestValidationReview({
      entityType: detail.item.entityType,
      entityId: detail.item.entityId,
      title: detail.item.title,
      subtitle: detail.item.subtitle,
      researcherName: detail.item.researcherName,
      researchUnitName: detail.item.researchUnitName,
      path,
      recommendation,
      confidenceScore,
      summary,
      suggestedComment,
      checks,
      evidence
    }).pipe(
      map((aiSuggestion) => ({
        recommendation,
        confidenceScore,
        summary,
        suggestedComment,
        checks,
        evidence,
        aiSuggestion,
        generatedAt: aiSuggestion.providerInfo.generatedAt,
        provider: aiSuggestion.providerInfo.provider,
        model: aiSuggestion.providerInfo.model,
        caution: aiSuggestion.providerInfo.caution
      }))
    );
  }

  private buildChecks(detail: ValidationItemDetail): ValidationAiReviewCheck[] {
    const checks: ValidationAiReviewCheck[] = [];
    const completeness = this.completenessSignal(detail);
    const normalizedWarnings = detail.warnings.map((warning) => warning.trim()).filter((warning) => warning.length > 0);
    const normalizedFlags = detail.dataQualityFlags.map((flag) => flag.trim()).filter((flag) => flag.length > 0);

    normalizedWarnings.forEach((warning, index) => {
      checks.push({
        id: `warning-${index + 1}`,
        severity: this.severityForText(warning, 'MEDIUM'),
        title: 'Aviso de revisión visible',
        detail: warning,
        outcome: 'negative'
      });
    });

    normalizedFlags.forEach((flag, index) => {
      checks.push({
        id: `quality-${index + 1}`,
        severity: this.severityForText(flag, 'HIGH'),
        title: 'Señal de calidad abierta',
        detail: flag,
        outcome: 'negative'
      });
    });

    if (completeness.missingLabels.length > 0) {
      checks.push({
        id: 'completeness-gap',
        severity: completeness.ratio < 0.5 ? 'HIGH' : 'MEDIUM',
        title: 'Faltan datos para cerrar la revisión',
        detail: `Conviene completar o confirmar: ${completeness.missingLabels.join(', ')}.`,
        outcome: 'negative'
      });
    } else {
      checks.push({
        id: 'completeness-ok',
        severity: 'LOW',
        title: 'Cobertura mínima visible',
        detail: 'Los campos principales del tipo de registro aparecen cubiertos en el detalle actual.',
        outcome: 'positive'
      });
    }

    const narrativeField = this.longNarrativeValue(detail);
    if (narrativeField) {
      checks.push({
        id: 'narrative-context',
        severity: 'LOW',
        title: 'Contexto descriptivo disponible',
        detail: `Hay contenido narrativo suficiente para contrastar la actividad: ${narrativeField.label}.`,
        outcome: 'positive'
      });
    } else {
      checks.push({
        id: 'narrative-gap',
        severity: 'MEDIUM',
        title: 'Contexto descriptivo limitado',
        detail: 'La revisión no muestra un resumen o descripción amplia; puede hacer falta contraste manual adicional.',
        outcome: 'neutral'
      });
    }

    if (detail.validationComment?.trim()) {
      checks.push({
        id: 'existing-comment',
        severity: 'MEDIUM',
        title: 'Existe comentario previo',
        detail: 'Hay un comentario registrado en el historial visible; conviene revisarlo antes de cerrar la decisión.',
        outcome: 'neutral'
      });
    }

    if (normalizedWarnings.length === 0 && normalizedFlags.length === 0) {
      checks.push({
        id: 'no-visible-alerts',
        severity: 'LOW',
        title: 'Sin alertas visibles en la bandeja',
        detail: 'No aparecen avisos ni señales de calidad adicionales en esta revisión.',
        outcome: 'positive'
      });
    }

    checks.push({
      id: 'human-responsibility',
      severity: 'LOW',
      title: 'La validación sigue siendo manual',
      detail: 'La recomendación ayuda a priorizar la revisión, pero no sustituye el criterio de la persona validadora.',
      outcome: 'neutral'
    });

    return checks;
  }

  private recommendationFor(
    detail: ValidationItemDetail,
    checks: ValidationAiReviewCheck[]
  ): ValidationReviewRecommendation {
    const negativeHigh = checks.filter((check) => check.severity === 'HIGH' && check.outcome === 'negative').length;
    const negativeMedium = checks.filter((check) => check.severity === 'MEDIUM' && check.outcome === 'negative').length;
    const positiveLow = checks.filter((check) => check.outcome === 'positive').length;

    if (negativeHigh === 0
      && negativeMedium === 0
      && detail.warnings.length === 0
      && detail.dataQualityFlags.length === 0
      && positiveLow >= 2) {
      return 'VALIDATE';
    }

    if (negativeHigh > 0 || negativeMedium >= 2) {
      return 'REQUEST_CHANGES';
    }

    return 'REVIEW_MANUALLY';
  }

  private confidenceFor(
    recommendation: ValidationReviewRecommendation,
    checks: ValidationAiReviewCheck[]
  ): number {
    const negativeHigh = checks.filter((check) => check.severity === 'HIGH' && check.outcome === 'negative').length;
    const negativeMedium = checks.filter((check) => check.severity === 'MEDIUM' && check.outcome === 'negative').length;
    const positives = checks.filter((check) => check.outcome === 'positive').length;
    const boundedPositives = Math.min(positives, 3);

    const value = recommendation === 'VALIDATE'
      ? 0.64 + (positives * 0.06) - (negativeMedium * 0.03)
      : recommendation === 'REQUEST_CHANGES'
        ? 0.68 + (negativeHigh * 0.09) + (negativeMedium * 0.04)
        : 0.54 + (boundedPositives * 0.03) + (negativeMedium * 0.02);

    return Math.max(0.46, Math.min(0.91, Number(value.toFixed(2))));
  }

  private summaryFor(
    recommendation: ValidationReviewRecommendation,
    checks: ValidationAiReviewCheck[]
  ): string {
    const highSignals = checks.filter((check) => check.severity === 'HIGH' && check.outcome === 'negative').length;
    const mediumSignals = checks.filter((check) => check.severity === 'MEDIUM' && check.outcome !== 'positive').length;

    switch (recommendation) {
      case 'VALIDATE':
        return 'Las señales visibles en la bandeja son consistentes y no aparecen incidencias relevantes que impidan cerrar la revisión.';
      case 'REQUEST_CHANGES':
        return highSignals > 0
          ? 'La actividad muestra incidencias relevantes o huecos de información que conviene corregir antes de reenviar.'
          : 'La actividad necesita ajustes visibles antes de poder cerrarse con seguridad.';
      default:
        return mediumSignals > 0
          ? 'La revisión necesita contraste manual adicional porque hay señales mixtas y no conviene resolverla solo con la vista actual.'
          : 'La actividad parece razonable, pero conviene una comprobación manual antes de decidir.';
    }
  }

  private suggestedCommentFor(
    recommendation: ValidationReviewRecommendation,
    checks: ValidationAiReviewCheck[]
  ): string | null {
    const relevantChecks = checks
      .filter((check) => check.outcome !== 'positive' && check.id !== 'human-responsibility')
      .slice(0, 2)
      .map((check) => check.detail.replace(/\.$/, ''));

    switch (recommendation) {
      case 'VALIDATE':
        return 'La información visible en esta revisión resulta consistente y suficiente para cerrar la validación.';
      case 'REQUEST_CHANGES':
        return relevantChecks.length > 0
          ? `Antes de reenviar, conviene revisar lo siguiente: ${relevantChecks.join(' ')}.`
          : 'Antes de reenviar, conviene completar o corregir la información señalada en esta revisión.';
      default:
        return relevantChecks.length > 0
          ? `Necesito una revisión manual adicional antes de decidir. Puntos a contrastar: ${relevantChecks.join(' ')}.`
          : 'Necesito una revisión manual adicional antes de cerrar esta validación.';
    }
  }

  private buildEvidence(detail: ValidationItemDetail): ValidationReviewSuggestionEvidence[] {
    const evidence: ValidationReviewSuggestionEvidence[] = [];
    const path = this.targetPath(detail.item.entityType, detail.item.entityId, detail.item.researcherId);

    if (detail.item.subtitle?.trim()) {
      evidence.push({
        label: 'Contexto del registro',
        sourceLabel: detail.item.title,
        sourceType: detail.item.entityType,
        excerpt: detail.item.subtitle,
        helper: 'Sirve como contexto operativo inicial para la revisión.',
        path
      });
    }

    const narrativeField = this.longNarrativeValue(detail);
    if (narrativeField) {
      evidence.push({
        label: narrativeField.label,
        sourceLabel: 'Detalle del registro',
        sourceType: 'DETAIL',
        excerpt: narrativeField.value,
        helper: 'Fragmento textual visible en el detalle actual.',
        path
      });
    }

    detail.warnings.slice(0, 2).forEach((warning, index) => {
      evidence.push({
        label: `Aviso ${index + 1}`,
        sourceLabel: 'Bandeja de validación',
        sourceType: 'WARNING',
        excerpt: warning,
        helper: 'Señal visible en la revisión operativa.',
        path: null
      });
    });

    detail.dataQualityFlags.slice(0, 2).forEach((flag, index) => {
      evidence.push({
        label: `Calidad ${index + 1}`,
        sourceLabel: 'Control de calidad',
        sourceType: 'DATA_QUALITY',
        excerpt: flag,
        helper: 'Incidencia visible para la decisión actual.',
        path: null
      });
    });

    if (detail.validationComment?.trim()) {
      evidence.push({
        label: 'Comentario registrado',
        sourceLabel: 'Historial de validación',
        sourceType: 'COMMENT',
        excerpt: detail.validationComment,
        helper: 'Conviene revisarlo si todavía condiciona la decisión actual.',
        path: null
      });
    }

    return evidence.slice(0, 5);
  }

  private completenessSignal(detail: ValidationItemDetail): CompletenessSignal {
    const requiredGroups = this.requiredFieldGroups(detail.item.entityType);
    if (requiredGroups.length === 0) {
      return { ratio: 1, missingLabels: [] };
    }

    const availableEntries = Object.entries(detail.fields)
      .filter(([, value]) => !this.isEmptyValue(value))
      .map(([key]) => this.normalizeText(key));
    const missingLabels = requiredGroups
      .filter((group) => !group.patterns.some((pattern) => availableEntries.some((key) => key.includes(pattern))))
      .map((group) => group.label);

    return {
      ratio: (requiredGroups.length - missingLabels.length) / requiredGroups.length,
      missingLabels
    };
  }

  private requiredFieldGroups(entityType: ValidationEntityType): Array<{ label: string; patterns: string[] }> {
    switch (entityType) {
      case 'PUBLICATION':
        return [
          { label: 'tipo', patterns: ['tipo'] },
          { label: 'año o fecha', patterns: ['ano', 'año', 'fecha'] },
          { label: 'estado editorial', patterns: ['estado'] },
          { label: 'autoría o autores', patterns: ['autor', 'autoria', 'autoría'] }
        ];
      case 'EVENT_PARTICIPATION':
        return [
          { label: 'tipo de participación', patterns: ['tipo'] },
          { label: 'evento', patterns: ['evento'] },
          { label: 'fecha', patterns: ['fecha'] }
        ];
      case 'RESEARCHER':
        return [
          { label: 'identificación principal', patterns: ['nombre', 'name'] },
          { label: 'contacto u orcid', patterns: ['email', 'orcid'] }
        ];
      case 'RESEARCHER_AFFILIATION':
        return [
          { label: 'unidad', patterns: ['unidad', 'unit'] },
          { label: 'tipo de afiliación', patterns: ['tipo', 'afili'] },
          { label: 'periodo', patterns: ['inicio', 'start', 'fin', 'end'] }
        ];
      case 'RESEARCH_UNIT':
        return [
          { label: 'tipo de unidad', patterns: ['tipo'] },
          { label: 'alcance geográfico', patterns: ['pais', 'país', 'city', 'ciudad', 'country'] }
        ];
      default:
        return [];
    }
  }

  private longNarrativeValue(detail: ValidationItemDetail): { label: string; value: string } | null {
    const entry = Object.entries(detail.fields).find(([key, value]) => {
      const normalizedKey = this.normalizeText(key);
      return !this.isEmptyValue(value)
        && value.trim().length >= 72
        && (
          normalizedKey.includes('resumen')
          || normalizedKey.includes('descripcion')
          || normalizedKey.includes('descripción')
          || normalizedKey.includes('comentario')
          || normalizedKey.includes('detalle')
        );
    });

    return entry ? { label: entry[0], value: entry[1].trim() } : null;
  }

  private severityForText(
    value: string,
    fallback: ValidationReviewCheckSeverity
  ): ValidationReviewCheckSeverity {
    const normalized = this.normalizeText(value);
    if (normalized.includes('falta')
      || normalized.includes('sin ')
      || normalized.includes('inconsisten')
      || normalized.includes('obligatori')
      || normalized.includes('evidencia')
      || normalized.includes('duplic')) {
      return 'HIGH';
    }
    if (normalized.includes('revis')
      || normalized.includes('confirm')
      || normalized.includes('comple')
      || normalized.includes('ajust')
      || normalized.includes('pendiente')) {
      return 'MEDIUM';
    }
    return fallback;
  }

  private targetPath(
    entityType: ValidationEntityType,
    entityId: number,
    researcherId: number | null
  ): string | null {
    switch (entityType) {
      case 'PUBLICATION':
        return `/admin/publicaciones/${entityId}`;
      case 'EVENT_PARTICIPATION':
        return `/admin/participaciones/${entityId}`;
      case 'RESEARCHER':
        return `/admin/investigadores/${entityId}`;
      case 'RESEARCH_UNIT':
        return `/admin/unidades/${entityId}`;
      case 'RESEARCHER_AFFILIATION':
        return researcherId ? `/admin/investigadores/${researcherId}` : null;
      default:
        return null;
    }
  }

  private isEmptyValue(value: string | null | undefined): boolean {
    if (!value) {
      return true;
    }

    const normalized = this.normalizeText(value);
    return normalized.length === 0 || normalized === 'sin dato' || normalized === 'n/a';
  }

  private normalizeText(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLocaleLowerCase('es-ES')
      .trim();
  }
}
