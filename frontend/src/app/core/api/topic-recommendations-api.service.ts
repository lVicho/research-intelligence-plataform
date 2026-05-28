import { Injectable } from '@angular/core';
import { Observable, delay, of } from 'rxjs';

export interface TopicRecommendationRequest {
  entityType: 'PUBLICATION' | 'EVENT_PARTICIPATION' | 'RESEARCHER' | 'VALIDATION' | 'DATA_QUALITY';
  entityId: number | string | null;
  title: string;
  summary?: string | null;
  description?: string | null;
  existingTopics?: string[];
}

export interface TopicRecommendationEvidence {
  id: string;
  label: string;
  excerpt: string;
  helper: string | null;
  path: string | null;
}

export interface TopicRecommendation {
  id: string;
  topicLabel: string;
  confidence: number | null;
  reason: string | null;
  evidence: TopicRecommendationEvidence[];
}

interface TopicProfile {
  keywords: string[];
  suggestions: Array<{
    label: string;
    confidence: number;
    reason: string;
    helper: string;
  }>;
}

@Injectable({ providedIn: 'root' })
export class TopicRecommendationsApiService {
  suggestTopics(request: TopicRecommendationRequest): Observable<TopicRecommendation[]> {
    return of(this.buildSuggestions(request)).pipe(delay(320));
  }

  private buildSuggestions(request: TopicRecommendationRequest): TopicRecommendation[] {
    const searchableText = [request.title, request.summary ?? '', request.description ?? '']
      .join(' ')
      .toLocaleLowerCase('es-ES');
    const existingTopics = new Set((request.existingTopics ?? []).map((topic) => this.normalizeLabel(topic)));
    const suggestions: TopicRecommendation[] = [];

    for (const profile of TOPIC_PROFILES) {
      if (!profile.keywords.some((keyword) => searchableText.includes(keyword))) {
        continue;
      }

      for (const candidate of profile.suggestions) {
        const normalized = this.normalizeLabel(candidate.label);
        if (existingTopics.has(normalized) || suggestions.some((item) => this.normalizeLabel(item.topicLabel) === normalized)) {
          continue;
        }

        suggestions.push({
          id: `${request.entityType}-${request.entityId ?? 'draft'}-${normalized}`,
          topicLabel: candidate.label,
          confidence: candidate.confidence,
          reason: candidate.reason,
          evidence: this.buildEvidence(request, candidate.helper)
        });
      }
    }

    if (suggestions.length === 0 && searchableText.trim().length > 0) {
      for (const fallback of FALLBACK_SUGGESTIONS) {
        const normalized = this.normalizeLabel(fallback.label);
        if (existingTopics.has(normalized)) {
          continue;
        }

        suggestions.push({
          id: `${request.entityType}-${request.entityId ?? 'draft'}-${normalized}`,
          topicLabel: fallback.label,
          confidence: fallback.confidence,
          reason: fallback.reason,
          evidence: this.buildEvidence(request, fallback.helper)
        });
      }
    }

    return suggestions.slice(0, 5);
  }

  private buildEvidence(
    request: TopicRecommendationRequest,
    helper: string
  ): TopicRecommendationEvidence[] {
    const evidence: TopicRecommendationEvidence[] = [];

    if (request.title.trim()) {
      evidence.push({
        id: `${request.entityType}-${request.entityId ?? 'draft'}-title`,
        label: 'Titulo',
        excerpt: request.title.trim(),
        helper,
        path: null
      });
    }

    const summary = (request.summary ?? '').trim();
    if (summary) {
      evidence.push({
        id: `${request.entityType}-${request.entityId ?? 'draft'}-summary`,
        label: 'Resumen',
        excerpt: this.trimExcerpt(summary),
        helper: 'El resumen contiene las pistas mas utiles para revisar la propuesta.',
        path: null
      });
    }

    const description = (request.description ?? '').trim();
    if (description) {
      evidence.push({
        id: `${request.entityType}-${request.entityId ?? 'draft'}-description`,
        label: 'Contexto',
        excerpt: this.trimExcerpt(description),
        helper: 'Sirve como apoyo adicional cuando el titulo y el resumen son breves.',
        path: null
      });
    }

    return evidence;
  }

  private trimExcerpt(value: string): string {
    return value.length > 220 ? `${value.slice(0, 217).trimEnd()}...` : value;
  }

  private normalizeLabel(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLocaleLowerCase('es-ES')
      .trim();
  }
}

const TOPIC_PROFILES: TopicProfile[] = [
  {
    keywords: ['salud digital', 'clinica', 'hospital', 'paciente', 'sanitario', 'sanitaria'],
    suggestions: [
      {
        label: 'salud digital',
        confidence: 0.89,
        reason: 'Coincide con el lenguaje clinico y con aplicaciones digitales descritas en el texto.',
        helper: 'La combinacion de terminos clinicos y digitales suele justificar esta etiqueta.'
      },
      {
        label: 'analitica clinica',
        confidence: 0.8,
        reason: 'El contenido apunta a analisis de datos aplicados a procesos asistenciales o de seguimiento.',
        helper: 'La propuesta se apoya en expresiones relacionadas con analisis clinico y decisiones asistenciales.'
      }
    ]
  },
  {
    keywords: ['tumor', 'tumoral', 'biomarcador', 'oncologia', 'multimodal', 'precision'],
    suggestions: [
      {
        label: 'oncologia de precision',
        confidence: 0.87,
        reason: 'Aparecen terminos propios de perfiles tumorales, biomarcadores y medicina personalizada.',
        helper: 'La evidencia conecta el registro con biomarcadores y estratificacion de precision.'
      },
      {
        label: 'estratificacion tumoral multimodal',
        confidence: 0.79,
        reason: 'El texto sugiere modelos o analisis multimodales sobre cohortes oncologicas.',
        helper: 'La formulacion multimodal queda respaldada por el vocabulario de estratificacion tumoral.'
      }
    ]
  },
  {
    keywords: ['clima', 'climatic', 'ambiental', 'vigilancia', 'territorial', 'riesgo'],
    suggestions: [
      {
        label: 'vigilancia climatica y sanitaria',
        confidence: 0.85,
        reason: 'Se detectan senales de vigilancia, series temporales y riesgo con impacto sanitario o territorial.',
        helper: 'El titulo y el resumen relacionan clima, vigilancia y efectos sobre salud o territorio.'
      },
      {
        label: 'salud ambiental',
        confidence: 0.74,
        reason: 'El contenido se mueve en la interseccion entre exposicion ambiental y consecuencias en salud.',
        helper: 'La relacion entre clima, riesgo y salud respalda una lectura de salud ambiental.'
      }
    ]
  },
  {
    keywords: ['laboratorio', 'protocolo', 'instrumentacion', 'experimental', 'ensayo'],
    suggestions: [
      {
        label: 'protocolos de laboratorio',
        confidence: 0.83,
        reason: 'El registro describe optimizacion, validacion o aplicacion de protocolos experimentales.',
        helper: 'Se observan referencias directas a protocolos, procedimientos o flujos de laboratorio.'
      },
      {
        label: 'instrumentacion cientifica',
        confidence: 0.71,
        reason: 'Hay vocabulario asociado a equipos, medicion o soporte instrumental.',
        helper: 'El tono tecnico apunta a una capa de instrumentacion o soporte experimental.'
      }
    ]
  },
  {
    keywords: ['salud publica', 'comunitaria', 'observacional', 'cohorte', 'poblacion'],
    suggestions: [
      {
        label: 'salud publica comunitaria',
        confidence: 0.81,
        reason: 'El texto habla de cohortes, poblacion o intervenciones con foco comunitario.',
        helper: 'Las expresiones poblacionales y comunitarias sostienen esta recomendacion.'
      },
      {
        label: 'epidemiologia aplicada',
        confidence: 0.73,
        reason: 'La redaccion apunta a observacion, seguimiento o medicion de resultados en poblaciones.',
        helper: 'El enfoque observacional y de cohorte encaja con epidemiologia aplicada.'
      }
    ]
  },
  {
    keywords: ['docente', 'educativa', 'aprendizaje', 'formacion', 'curricular'],
    suggestions: [
      {
        label: 'aprendizaje longitudinal',
        confidence: 0.77,
        reason: 'Se describen procesos formativos, seguimiento o evaluacion a lo largo del tiempo.',
        helper: 'Las referencias a formacion y trayectoria apoyan una lectura de aprendizaje longitudinal.'
      },
      {
        label: 'evaluacion de impacto',
        confidence: 0.69,
        reason: 'El texto enfatiza medicion de resultados, cambios o evaluaciones comparativas.',
        helper: 'La propuesta nace de senales de evaluacion y comparacion de resultados.'
      }
    ]
  }
];

const FALLBACK_SUGGESTIONS = [
  {
    label: 'metodos computacionales',
    confidence: 0.58,
    reason: 'No hay una familia tematica dominante, pero el texto sugiere un componente metodologico o analitico.',
    helper: 'Sirve como punto de partida cuando la evidencia es util pero no concluyente.'
  },
  {
    label: 'analisis de datos',
    confidence: 0.54,
    reason: 'La propuesta recoge un perfil transversal cuando todavia falta contexto para una taxonomia mas fina.',
    helper: 'Usala solo como borrador inicial si la etiqueta especifica aun no es evidente.'
  }
];
