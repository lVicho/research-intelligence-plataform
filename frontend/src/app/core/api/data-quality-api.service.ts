import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';

import {
  DataQualityAffectedRecord,
  DataQualityEntityType,
  DataQualityIssue,
  DataQualityOverview,
  DataQualitySeverity,
  PageResponse
} from './api-models';
import { environment } from '../../../environments/environment';

type BackendDataQualitySeverity = 'INFO' | 'WARNING' | 'ERROR';

type BackendDataQualityEntityType =
  | 'PUBLICATION'
  | 'RESEARCHER'
  | 'PUBLICATION_AUTHOR'
  | 'EVENT_PARTICIPATION'
  | 'RESEARCH_UNIT'
  | 'VENUE'
  | 'SCIENTIFIC_EVENT'
  | 'TOPIC';

type BackendDataQualityIssueType =
  | 'PUBLICATIONS_WITHOUT_DOI'
  | 'PUBLICATIONS_WITHOUT_ABSTRACT'
  | 'PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY'
  | 'PUBLICATIONS_WITHOUT_TOPICS'
  | 'PUBLICATION_TITLE_CASING_ISSUES'
  | 'RESEARCHERS_WITHOUT_ORCID'
  | 'PUBLICATIONS_WITH_EXTERNAL_AUTHORS'
  | 'UNRESOLVED_EXTERNAL_AUTHORS'
  | 'ACTIVITIES_PENDING_VALIDATION'
  | 'VENUES_WITHOUT_IDENTIFIER'
  | 'EVENTS_WITHOUT_DATES'
  | 'EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES'
  | 'DUPLICATE_TOPIC_CANDIDATES'
  | 'DUPLICATE_PUBLICATION_CANDIDATES';

interface BackendDataQualitySummary {
  publicationsWithoutDoi: number;
  publicationsWithoutAbstract: number;
  publicationsWithoutPublicSummary: number;
  publicationsWithoutTopics: number;
  publicationTitleCasingIssues: number;
  researchersWithoutOrcid: number;
  publicationsWithExternalAuthors: number;
  unresolvedExternalAuthors: number;
  activitiesPendingValidation: number;
  venuesWithoutIdentifier: number;
  eventsWithoutDates: number;
  externalOrganizationDuplicateCandidates: number;
  duplicateTopicCandidates: number;
  duplicatePublicationCandidates: number;
}

interface BackendDataQualityIssue {
  issueType: BackendDataQualityIssueType;
  severity: BackendDataQualitySeverity;
  entityType: BackendDataQualityEntityType;
  entityId: number;
  title: string;
  description: string;
  suggestedAction: string;
}

interface IssueDefinition {
  id: string;
  label: string;
  description: string;
  count: (summary: BackendDataQualitySummary) => number;
}

const ISSUE_DEFINITIONS: Record<BackendDataQualityIssueType, IssueDefinition> = {
  PUBLICATIONS_WITHOUT_DOI: {
    id: 'missing-doi',
    label: 'Sin DOI',
    description: 'Publicaciones sin identificador DOI, pendientes de trazabilidad o justificacion editorial.',
    count: (summary) => summary.publicationsWithoutDoi
  },
  PUBLICATIONS_WITHOUT_ABSTRACT: {
    id: 'missing-abstract',
    label: 'Sin resumen',
    description: 'Registros sin resumen que limitan la busqueda, la revision y la recuperacion semantica.',
    count: (summary) => summary.publicationsWithoutAbstract
  },
  PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY: {
    id: 'missing-public-summary',
    label: 'Sin resumen publico',
    description: 'Publicaciones sin sintesis publica para mostrar contexto claro en el portal.',
    count: (summary) => summary.publicationsWithoutPublicSummary
  },
  PUBLICATIONS_WITHOUT_TOPICS: {
    id: 'missing-topics',
    label: 'Sin temas',
    description: 'Publicaciones sin temas asociados, pendientes de clasificacion o normalizacion.',
    count: (summary) => summary.publicationsWithoutTopics
  },
  PUBLICATION_TITLE_CASING_ISSUES: {
    id: 'title-casing-issues',
    label: 'Titulos por revisar',
    description: 'Titulos con capitalizacion anomala que conviene revisar antes de publicar o validar.',
    count: (summary) => summary.publicationTitleCasingIssues
  },
  RESEARCHERS_WITHOUT_ORCID: {
    id: 'researchers-without-orcid',
    label: 'Investigadores sin ORCID',
    description: 'Perfiles internos sin ORCID confirmado, con posible ambiguedad de identidad.',
    count: (summary) => summary.researchersWithoutOrcid
  },
  PUBLICATIONS_WITH_EXTERNAL_AUTHORS: {
    id: 'external-authors',
    label: 'Autores externos',
    description: 'Publicaciones con autores externos que conviene revisar y enriquecer.',
    count: (summary) => summary.publicationsWithExternalAuthors
  },
  UNRESOLVED_EXTERNAL_AUTHORS: {
    id: 'unresolved-external-authors',
    label: 'Autores externos sin resolver',
    description: 'Autores externos sin afiliacion o contexto suficiente para una revision fiable.',
    count: (summary) => summary.unresolvedExternalAuthors
  },
  ACTIVITIES_PENDING_VALIDATION: {
    id: 'activities-pending-validation',
    label: 'Actividades pendientes',
    description: 'Participaciones en eventos que siguen pendientes de decision institucional.',
    count: (summary) => summary.activitiesPendingValidation
  },
  VENUES_WITHOUT_IDENTIFIER: {
    id: 'venues-without-identifier',
    label: 'Canales sin identificador',
    description: 'Canales editoriales sin ISSN, eISSN o ISBN para trazabilidad bibliografica.',
    count: (summary) => summary.venuesWithoutIdentifier
  },
  EVENTS_WITHOUT_DATES: {
    id: 'events-without-dates',
    label: 'Eventos sin fechas',
    description: 'Eventos cientificos sin fechas completas para analisis temporal.',
    count: (summary) => summary.eventsWithoutDates
  },
  EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES: {
    id: 'external-organization-duplicates',
    label: 'Organizaciones externas duplicadas',
    description: 'Organizaciones externas con nombres coincidentes que requieren normalizacion.',
    count: (summary) => summary.externalOrganizationDuplicateCandidates
  },
  DUPLICATE_TOPIC_CANDIDATES: {
    id: 'possible-duplicate-topics',
    label: 'Posibles temas duplicados',
    description: 'Temas con nombres repetidos o variantes cercanas pendientes de consolidacion.',
    count: (summary) => summary.duplicateTopicCandidates
  },
  DUPLICATE_PUBLICATION_CANDIDATES: {
    id: 'possible-duplicate-publications',
    label: 'Posibles publicaciones duplicadas',
    description: 'Publicaciones con coincidencias por titulo y ano que requieren revision.',
    count: (summary) => summary.duplicatePublicationCandidates
  }
};

@Injectable({ providedIn: 'root' })
export class DataQualityApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/data-quality`;

  overview(): Observable<DataQualityOverview> {
    return forkJoin({
      summary: this.http.get<BackendDataQualitySummary>(`${this.baseUrl}/summary`),
      issues: this.http.get<PageResponse<BackendDataQualityIssue>>(`${this.baseUrl}/issues`, {
        params: new HttpParams().set('size', 100)
      })
    }).pipe(
      map(({ summary, issues }) => this.toOverview(summary, issues.content))
    );
  }

  private toOverview(summary: BackendDataQualitySummary, issueRows: BackendDataQualityIssue[]): DataQualityOverview {
    const groupedRows = this.groupByIssueType(issueRows);
    const issues = (Object.keys(ISSUE_DEFINITIONS) as BackendDataQualityIssueType[])
      .map((issueType) => this.toIssue(issueType, summary, groupedRows.get(issueType) ?? []))
      .filter((issue): issue is DataQualityIssue => issue !== null);
    const affectedRecords = issues.reduce((total, issue) => total + issue.count, 0);
    const criticalIssues = issues.filter((issue) => issue.severity === 'CRITICAL').length;

    return {
      summary: {
        totalOpenIssues: issues.length,
        criticalIssues,
        categoriesWithFindings: issues.length,
        affectedRecords,
        lastReviewAt: new Date().toISOString()
      },
      issues
    };
  }

  private toIssue(
    issueType: BackendDataQualityIssueType,
    summary: BackendDataQualitySummary,
    rows: BackendDataQualityIssue[]
  ): DataQualityIssue | null {
    const definition = ISSUE_DEFINITIONS[issueType];
    const count = definition.count(summary);
    if (count <= 0 && rows.length === 0) {
      return null;
    }
    const primaryRow = rows[0];
    return {
      id: definition.id,
      label: definition.label,
      description: definition.description,
      severity: this.toSeverity(primaryRow?.severity, issueType),
      entityType: this.toEntityType(primaryRow?.entityType, issueType),
      count,
      affectedRecords: rows.map((row) => this.toAffectedRecord(row)),
      updatedAt: new Date().toISOString()
    };
  }

  private groupByIssueType(rows: BackendDataQualityIssue[]): Map<BackendDataQualityIssueType, BackendDataQualityIssue[]> {
    const grouped = new Map<BackendDataQualityIssueType, BackendDataQualityIssue[]>();
    for (const row of rows) {
      grouped.set(row.issueType, [...(grouped.get(row.issueType) ?? []), row]);
    }
    return grouped;
  }

  private toAffectedRecord(row: BackendDataQualityIssue): DataQualityAffectedRecord {
    return {
      label: this.affectedRecordLabel(row),
      path: this.affectedRecordPath(row),
      helper: this.suggestedAction(row.issueType)
    };
  }

  private affectedRecordLabel(row: BackendDataQualityIssue): string {
    const prefix = this.entityLabel(this.toEntityType(row.entityType, row.issueType));
    return `${prefix} #${row.entityId}: ${row.title}`;
  }

  private affectedRecordPath(row: BackendDataQualityIssue): string {
    switch (row.entityType) {
      case 'PUBLICATION':
        return `/admin/publicaciones/${row.entityId}`;
      case 'RESEARCHER':
        return `/admin/investigadores/${row.entityId}`;
      case 'EVENT_PARTICIPATION':
        return `/admin/participaciones/${row.entityId}`;
      case 'RESEARCH_UNIT':
        return `/admin/unidades/${row.entityId}`;
      case 'VENUE':
        return `/admin/canales/${row.entityId}`;
      case 'SCIENTIFIC_EVENT':
        return `/admin/eventos/${row.entityId}`;
      case 'TOPIC':
        return '/admin/normalizacion-temas';
      default:
        return '/admin/calidad-datos';
    }
  }

  private toSeverity(
    severity: BackendDataQualitySeverity | undefined,
    issueType: BackendDataQualityIssueType
  ): DataQualitySeverity {
    if (severity === 'ERROR') {
      return 'CRITICAL';
    }
    if (issueType === 'DUPLICATE_PUBLICATION_CANDIDATES' || issueType === 'EVENTS_WITHOUT_DATES') {
      return 'CRITICAL';
    }
    if (severity === 'WARNING') {
      return 'HIGH';
    }
    if (severity === 'INFO') {
      return 'LOW';
    }
    return 'MEDIUM';
  }

  private toEntityType(
    entityType: BackendDataQualityEntityType | undefined,
    issueType: BackendDataQualityIssueType
  ): DataQualityEntityType {
    if (entityType === 'PUBLICATION_AUTHOR' || issueType === 'UNRESOLVED_EXTERNAL_AUTHORS') {
      return 'EXTERNAL_AUTHOR';
    }
    if (entityType === 'SCIENTIFIC_EVENT') {
      return 'EVENT';
    }
    if (entityType === 'RESEARCH_UNIT') {
      return 'RESEARCH_UNIT';
    }
    if (entityType === 'PUBLICATION' || entityType === 'RESEARCHER' || entityType === 'EVENT_PARTICIPATION' || entityType === 'VENUE' || entityType === 'TOPIC') {
      return entityType;
    }
    return 'PUBLICATION';
  }

  private entityLabel(entityType: DataQualityEntityType): string {
    switch (entityType) {
      case 'PUBLICATION':
        return 'Publicacion';
      case 'RESEARCHER':
        return 'Investigador';
      case 'TOPIC':
        return 'Tema';
      case 'EVENT_PARTICIPATION':
        return 'Participacion';
      case 'VENUE':
        return 'Canal';
      case 'EVENT':
        return 'Evento';
      case 'RESEARCH_UNIT':
        return 'Unidad';
      default:
        return 'Autor externo';
    }
  }

  private suggestedAction(issueType: BackendDataQualityIssueType): string {
    switch (issueType) {
      case 'PUBLICATIONS_WITHOUT_DOI':
        return 'Anadir DOI si esta disponible o documentar por que no aplica.';
      case 'PUBLICATIONS_WITHOUT_ABSTRACT':
        return 'Completar un resumen breve para mejorar revision y busqueda.';
      case 'PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY':
        return 'Preparar una sintesis publica clara para el portal.';
      case 'PUBLICATIONS_WITHOUT_TOPICS':
        return 'Vincular temas normalizados antes de usarlo en navegacion o informes.';
      case 'PUBLICATION_TITLE_CASING_ISSUES':
        return 'Revisar la capitalizacion del titulo.';
      case 'RESEARCHERS_WITHOUT_ORCID':
        return 'Anadir ORCID cuando este confirmado.';
      case 'PUBLICATIONS_WITH_EXTERNAL_AUTHORS':
        return 'Revisar autores externos y enriquecer afiliaciones cuando sea posible.';
      case 'UNRESOLVED_EXTERNAL_AUTHORS':
        return 'Completar afiliacion o resolver el autor externo manualmente.';
      case 'ACTIVITIES_PENDING_VALIDATION':
        return 'Validar, rechazar o solicitar cambios desde la bandeja de validacion.';
      case 'VENUES_WITHOUT_IDENTIFIER':
        return 'Anadir ISSN, eISSN o ISBN si existe una fuente fiable.';
      case 'EVENTS_WITHOUT_DATES':
        return 'Completar fechas para mejorar trazabilidad temporal.';
      case 'EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES':
        return 'Revisar si las organizaciones representan la misma entidad externa.';
      case 'DUPLICATE_TOPIC_CANDIDATES':
        return 'Revisar variantes antes de fusionar o normalizar temas.';
      case 'DUPLICATE_PUBLICATION_CANDIDATES':
        return 'Comparar metadatos antes de consolidar publicaciones.';
    }
  }
}
