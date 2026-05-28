import { Injectable } from '@angular/core';
import { Observable, delay, of, throwError } from 'rxjs';

import {
  CollaborationOpportunity,
  CollaborationOpportunityMode,
  CollaborationOpportunityQuery,
  CollaborationOpportunityResponse
} from './api-models';

export type CollaborationOpportunityDemoState = 'default' | 'error';

@Injectable({ providedIn: 'root' })
export class CollaborationOpportunitiesApiService {
  getOpportunities(
    query: CollaborationOpportunityQuery,
    demoState: CollaborationOpportunityDemoState = 'default'
  ): Observable<CollaborationOpportunityResponse> {
    if (demoState === 'error') {
      return throwError(() => new Error('demo-error')).pipe(delay(350));
    }

    const opportunities = this.filterOpportunities(query);
    return of({
      generatedAt: '2026-05-19T08:45:00Z',
      visibilityScope: 'Uso interno administrativo. Si esta vista se publica, debe limitarse a datos validados.',
      minYear: 2019,
      maxYear: 2026,
      total: opportunities.length,
      opportunities
    }).pipe(delay(450));
  }

  private filterOpportunities(query: CollaborationOpportunityQuery): CollaborationOpportunity[] {
    const fromYear = query.fromYear ?? 2019;
    const toYear = query.toYear ?? 2026;
    const minimumConfidence = this.minimumConfidence(query.mode);

    return MOCK_COLLABORATION_OPPORTUNITIES
      .filter((item) => item.toYear >= fromYear && item.fromYear <= toYear)
      .filter((item) => item.confidence >= minimumConfidence)
      .slice(0, query.limit);
  }

  private minimumConfidence(mode: CollaborationOpportunityMode): number {
    switch (mode) {
      case 'STRICT':
        return 0.84;
      case 'BALANCED':
        return 0.72;
      default:
        return 0.58;
    }
  }
}

const MOCK_COLLABORATION_OPPORTUNITIES: CollaborationOpportunity[] = [
  {
    id: 'bioinfo-clinical-oncology',
    unitA: { id: 12, name: 'Instituto de Bioinformatica Traslacional', shortName: 'IBT', type: 'INSTITUTE' },
    unitB: { id: 34, name: 'Unidad de Oncologia Clinica', shortName: 'UOC', type: 'HOSPITAL' },
    score: 93,
    confidence: 0.91,
    sharedTopics: ['oncologia de precision', 'biomarcadores', 'modelos predictivos'],
    complementaryTopics: ['genomica clinica', 'ensayos multicentricos', 'estratificacion de pacientes'],
    representativePublications: [
      { id: 182, title: 'Modelos multimodales para estratificacion tumoral', year: 2025, source: 'Journal of Precision Medicine', path: '/publications/182' },
      { id: 167, title: 'Biomarcadores transcriptomicos en cohortes clinicas', year: 2024, source: 'Clinical Data Science Review', path: '/publications/167' }
    ],
    existingCollaborationCount: 3,
    explanation: 'Las dos unidades ya coinciden en biomarcadores y diagnostico de precision, pero aun publican con comunidades y metodos complementarios que podrian converger en proyectos traslacionales.',
    fromYear: 2022,
    toYear: 2025
  },
  {
    id: 'ai-education-public-health',
    unitA: { id: 27, name: 'Laboratorio de IA Aplicada', shortName: 'LIA', type: 'LAB' },
    unitB: { id: 51, name: 'Centro de Salud Publica y Aprendizaje', shortName: 'CSPA', type: 'CENTER' },
    score: 88,
    confidence: 0.86,
    sharedTopics: ['analitica predictiva', 'evaluacion de impacto', 'salud digital'],
    complementaryTopics: ['intervenciones poblacionales', 'aprendizaje adaptativo', 'seguimiento longitudinal'],
    representativePublications: [
      { id: 201, title: 'Prediccion temprana de adherencia en programas preventivos', year: 2025, source: 'Digital Health Systems', path: '/publications/201' },
      { id: 154, title: 'Diseno de rutas formativas con senales clinicas y educativas', year: 2023, source: 'Learning Analytics in Health', path: '/publications/154' }
    ],
    existingCollaborationCount: 1,
    explanation: 'Comparten foco en evaluacion y datos longitudinales. La combinacion sugiere oportunidad para programas preventivos con apoyo de IA y seguimiento formativo del personal clinico.',
    fromYear: 2021,
    toYear: 2025
  },
  {
    id: 'robotics-rehab-neuroscience',
    unitA: { id: 43, name: 'Grupo de Robotica Medica', shortName: 'GRM', type: 'RESEARCH_GROUP' },
    unitB: { id: 44, name: 'Instituto de Neurorehabilitacion', shortName: 'INR', type: 'INSTITUTE' },
    score: 84,
    confidence: 0.79,
    sharedTopics: ['rehabilitacion asistida', 'captura de movimiento'],
    complementaryTopics: ['interfaces hapticas', 'neuroplasticidad', 'protocolos de terapia intensiva'],
    representativePublications: [
      { id: 138, title: 'Sensores portables para terapia motora intensiva', year: 2023, source: 'Rehabilitation Engineering Notes', path: '/publications/138' },
      { id: 129, title: 'Evaluacion funcional con robotica blanda en ictus', year: 2022, source: 'NeuroRecovery Journal', path: '/publications/129' }
    ],
    existingCollaborationCount: 0,
    explanation: 'Hay poca colaboracion previa, pero los temas encajan muy bien entre instrumentacion y protocolos clinicos, con margen para pilotos de alta visibilidad.',
    fromYear: 2022,
    toYear: 2024
  },
  {
    id: 'climate-data-rural-health',
    unitA: { id: 61, name: 'Centro de Modelizacion Climatica', shortName: 'CMC', type: 'CENTER' },
    unitB: { id: 73, name: 'Observatorio de Salud Rural', shortName: 'OSR', type: 'CENTER' },
    score: 79,
    confidence: 0.68,
    sharedTopics: ['vulnerabilidad territorial', 'series temporales'],
    complementaryTopics: ['eventos extremos', 'salud comunitaria', 'riesgo ambiental'],
    representativePublications: [
      { id: 222, title: 'Series temporales para vigilancia de riesgo climatico y sanitario', year: 2025, source: 'Environmental Health Data', path: '/publications/222' },
      { id: 149, title: 'Patrones territoriales de acceso y exposicion ambiental', year: 2023, source: 'Rural Health Insights', path: '/publications/149' }
    ],
    existingCollaborationCount: 0,
    explanation: 'La coincidencia tematica es emergente y mas exploratoria. En modo amplio resulta util para detectar alianzas tempranas entre ciencia de datos climaticos y salud comunitaria.',
    fromYear: 2023,
    toYear: 2025
  },
  {
    id: 'materials-devices-cardiology',
    unitA: { id: 19, name: 'Departamento de Materiales Inteligentes', shortName: 'DMI', type: 'DEPARTMENT' },
    unitB: { id: 58, name: 'Unidad de Cardiologia Intervencionista', shortName: 'UCI', type: 'HOSPITAL' },
    score: 75,
    confidence: 0.61,
    sharedTopics: ['dispositivos biomedicos'],
    complementaryTopics: ['superficies bioactivas', 'monitorizacion hemodinamica', 'validacion preclinica'],
    representativePublications: [
      { id: 118, title: 'Recubrimientos bioactivos para dispositivos implantables', year: 2022, source: 'Biomedical Materials Today', path: '/publications/118' },
      { id: 231, title: 'Monitorizacion continua en procedimientos intervencionistas', year: 2026, source: 'CardioTech Reports', path: '/publications/231' }
    ],
    existingCollaborationCount: 2,
    explanation: 'Existe una base limitada de colaboracion, pero la complementariedad es fuerte entre materiales, validacion clinica y necesidades de dispositivo en entorno hospitalario.',
    fromYear: 2022,
    toYear: 2026
  }
];
