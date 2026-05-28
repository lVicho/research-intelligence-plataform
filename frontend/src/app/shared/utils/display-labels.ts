import {
  AiSuggestionStatus,
  AiSuggestionType,
  ActivityAuditAction,
  AffiliationType,
  GraphNodeType,
  NewsArticleStatus,
  OrganizationScope,
  PublicationStatus,
  PublicationType,
  ResearchUnitType,
  ValidationEntityType,
  ValidationStatus
} from '../../core/api/api-models';

export const FALLBACK_PUBLICATION_TYPE_LABELS: Record<string, string> = {
  ARTICLE: 'Art\u00edculo',
  BOOK: 'Libro',
  BOOK_CHAPTER: 'Cap\u00edtulo de libro',
  CONFERENCE_PAPER: 'Comunicaci\u00f3n',
  THESIS: 'Tesis',
  REPORT: 'Informe',
  DATASET: 'Dataset',
  SOFTWARE: 'Software',
  OTHER: 'Otro'
};

export const FALLBACK_PUBLICATION_STATUS_LABELS: Record<string, string> = {
  PUBLISHED: 'Publicado',
  ACCEPTED: 'Aceptado',
  IN_PRESS: 'En prensa',
  DRAFT: 'Borrador',
  UNKNOWN: 'Sin estado'
};

export function publicationTypeLabel(type: string | PublicationType): string {
  return FALLBACK_PUBLICATION_TYPE_LABELS[type] ?? type;
}

export function publicationStatusLabel(status: string | PublicationStatus): string {
  return FALLBACK_PUBLICATION_STATUS_LABELS[status] ?? status;
}

export function publicationStatusTone(status: string | PublicationStatus): 'neutral' | 'success' | 'warning' | 'info' {
  switch (status) {
    case 'PUBLISHED':
      return 'success';
    case 'ACCEPTED':
    case 'IN_PRESS':
      return 'info';
    case 'DRAFT':
      return 'warning';
    default:
      return 'neutral';
  }
}

export function newsArticleStatusLabel(status: string | NewsArticleStatus): string {
  const labels: Record<string, string> = {
    DRAFT: 'Borrador',
    PENDING_REVIEW: 'Pendiente de revision',
    PUBLISHED: 'Publicada',
    ARCHIVED: 'Archivada'
  };
  return labels[status] ?? status;
}

export function newsArticleStatusTone(status: string | NewsArticleStatus): 'neutral' | 'success' | 'warning' | 'info' {
  switch (status) {
    case 'PUBLISHED':
      return 'success';
    case 'PENDING_REVIEW':
      return 'info';
    case 'ARCHIVED':
      return 'warning';
    default:
      return 'neutral';
  }
}

export function researchUnitTypeLabel(type: string | ResearchUnitType): string {
  const labels: Record<string, string> = {
    UNIVERSITY: 'Universidad',
    FACULTY: 'Facultad',
    SCHOOL: 'Escuela',
    DEPARTMENT: 'Departamento',
    INSTITUTE: 'Instituto',
    RESEARCH_GROUP: 'Grupo de investigaci\u00f3n',
    LAB: 'Laboratorio',
    CENTER: 'Centro',
    HOSPITAL: 'Hospital',
    COMPANY: 'Empresa',
    FOUNDATION: 'Fundaci\u00f3n',
    GOVERNMENT_AGENCY: 'Agencia gubernamental',
    OTHER: 'Otro'
  };
  return labels[type] ?? type;
}

export function organizationScopeLabel(scope: string | OrganizationScope): string {
  const labels: Record<string, string> = {
    INTERNAL: 'Unidad interna',
    EXTERNAL: 'Organización externa'
  };
  return labels[scope] ?? scope;
}

export function affiliationTypeLabel(type: string | AffiliationType): string {
  const labels: Record<string, string> = {
    MEMBER: 'Miembro',
    LEADER: 'Responsable',
    VISITING: 'Visitante',
    COLLABORATOR: 'Colaborador',
    FORMER_MEMBER: 'Miembro anterior',
    OTHER: 'Otro'
  };
  return labels[type] ?? type;
}

export function graphNodeTypeLabel(type: string | GraphNodeType): string {
  const labels: Record<string, string> = {
    researcher: 'Investigador',
    research_unit: 'Unidad',
    publication: 'Publicaci\u00f3n',
    topic: 'Tema',
    external_author: 'Autor externo'
  };
  return labels[type] ?? type;
}

export function validationStatusLabel(status: string | ValidationStatus): string {
  const labels: Record<string, string> = {
    DRAFT: 'Borrador',
    PENDING_VALIDATION: 'Pendiente de validaci\u00f3n',
    VALIDATED: 'Validada',
    REJECTED: 'Rechazada',
    CHANGES_REQUESTED: 'Requiere cambios'
  };
  return labels[status] ?? status;
}

export function validationStatusTone(status: string | ValidationStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'DRAFT':
      return 'neutral';
    case 'VALIDATED':
      return 'success';
    case 'REJECTED':
      return 'danger';
    case 'CHANGES_REQUESTED':
      return 'warning';
    case 'PENDING_VALIDATION':
      return 'info';
    default:
      return 'neutral';
  }
}

export function validationEntityTypeLabel(type: string | ValidationEntityType): string {
  const labels: Record<string, string> = {
    RESEARCH_UNIT: 'Unidad',
    EXTERNAL_ORGANIZATION: 'Organizacion externa',
    RESEARCHER: 'Investigador',
    RESEARCHER_AFFILIATION: 'Afiliaci\u00f3n',
    PUBLICATION: 'Publicaci\u00f3n',
    EVENT_PARTICIPATION: 'Participaci\u00f3n',
    SCIENTIFIC_EVENT: 'Evento cient\u00edfico',
    VENUE: 'Canal',
    PUBLISHER: 'Editorial',
    TOPIC: 'Tema'
  };
  return labels[type] ?? type;
}

export function aiSuggestionTypeLabel(type: string | AiSuggestionType): string {
  if (type === 'NEWS_DRAFT') {
    return 'Borrador de noticia';
  }
  const labels: Record<string, string> = {
    PUBLIC_SUMMARY: 'Resumen publico',
    RESEARCHER_SUMMARY: 'Resumen de investigador',
    RESEARCHER_TOPIC: 'Tema sugerido para investigador',
    PUBLICATION_METADATA: 'Metadatos de publicación',
    PUBLICATION_TOPIC: 'Tema sugerido para publicación',
    RESEARCH_UNIT_SUMMARY: 'Resumen público de unidad',
    VALIDATION_REVIEW: 'Revisión asistida de validación'
  };
  return labels[type] ?? type;
}

export function aiSuggestionStatusLabel(status: string | AiSuggestionStatus): string {
  const labels: Record<string, string> = {
    PENDING_REVIEW: 'Pendiente de revisión',
    ACCEPTED: 'Aceptada',
    ACCEPTED_WITH_EDITS: 'Aceptada con edición',
    REJECTED: 'Rechazada'
  };
  return labels[status] ?? status;
}

export function aiSuggestionStatusTone(status: string | AiSuggestionStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'PENDING_REVIEW':
      return 'warning';
    case 'ACCEPTED':
    case 'ACCEPTED_WITH_EDITS':
      return 'success';
    case 'REJECTED':
      return 'danger';
    default:
      return 'neutral';
  }
}

export function activityAuditActionLabel(action: string | ActivityAuditAction): string {
  const labels: Record<string, string> = {
    CREATED: 'Creado',
    UPDATED: 'Modificado',
    SUBMITTED: 'Enviado a validaci\u00f3n',
    VALIDATED: 'Validado',
    REJECTED: 'Rechazado',
    CHANGES_REQUESTED: 'Cambios solicitados',
    ARCHIVED: 'Archivado',
    DELETED: 'Eliminado',
    RESTORED: 'Restaurado'
  };
  return labels[action] ?? action;
}

export function activityAuditActionTone(action: string | ActivityAuditAction): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  switch (action) {
    case 'CREATED':
    case 'VALIDATED':
    case 'RESTORED':
      return 'success';
    case 'SUBMITTED':
      return 'info';
    case 'UPDATED':
    case 'CHANGES_REQUESTED':
    case 'ARCHIVED':
      return 'warning';
    case 'REJECTED':
    case 'DELETED':
      return 'danger';
    default:
      return 'neutral';
  }
}
