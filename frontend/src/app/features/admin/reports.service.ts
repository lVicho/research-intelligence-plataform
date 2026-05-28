import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, shareReplay, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CopilotCitation,
  PageResponse,
  Publication,
  PublicationSummary,
  ResearchUnit,
  Researcher,
  ResearcherCoauthor
} from '../../core/api/api-models';
import { PublicationFilters, PublicationsApiService } from '../../core/api/publications-api.service';
import { StrategicResearchMapService } from '../strategic-map/strategic-research-map.service';
import {
  BackendGeneratedReport,
  BackendReportGenerationRequest,
  GeneratedReport,
  ReportGenerationRequest,
  ReportsContext,
  ReportSectionKey,
  ReportSectionOption,
  ReportTemplate,
  ReportTemplateRequest,
  ReportTargetOption,
  ReportType,
  ReportTypeOption,
  StrategicLineTargetSnapshot
} from './reports.models';

interface TopicCount {
  label: string;
  count: number;
}

interface CountedResearcher {
  researcher: Researcher;
  publicationCount: number;
}

interface CountedUnit {
  unit: ResearchUnit | null;
  name: string;
  publicationCount: number;
  researcherCount: number;
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly publicationsApi = inject(PublicationsApiService);
  private readonly strategicMapService = inject(StrategicResearchMapService);
  private readonly reportTemplatesUrl = `${environment.apiBaseUrl}/report-templates`;
  private readonly reportsUrl = `${environment.apiBaseUrl}/reports`;

  private contextCache$: Observable<ReportsContext> | null = null;

  readonly reportTypes: ReportTypeOption[] = [
    {
      value: 'RESEARCH_UNIT',
      label: 'Unidad de investigación',
      description: 'Dossier institucional por unidad, producción visible y perfiles vinculados.'
    },
    {
      value: 'RESEARCHER',
      label: 'Investigador',
      description: 'Perfil sintético con producción, temas, afiliación y colaboradores visibles.'
    },
    {
      value: 'TOPIC',
      label: 'Tema',
      description: 'Panorama temático con publicaciones, investigadores y unidades activas.'
    },
    {
      value: 'STRATEGIC_LINE',
      label: 'Línea estratégica',
      description: 'Lectura estratégica derivada de la evidencia temática y sus conexiones.'
    }
  ];

  loadContext(): Observable<ReportsContext> {
    if (!this.contextCache$) {
      this.contextCache$ = this.strategicMapService.loadContext().pipe(
        map((context) => ({
          metadata: context.metadata,
          units: context.units,
          researchers: context.researchers
        })),
        shareReplay(1)
      );
    }
    return this.contextCache$;
  }

  loadTemplates(): Observable<ReportTemplate[]> {
    return this.http.get<ReportTemplate[]>(this.reportTemplatesUrl);
  }

  createTemplate(request: ReportTemplateRequest): Observable<ReportTemplate> {
    return this.http.post<ReportTemplate>(this.reportTemplatesUrl, request);
  }

  updateTemplate(id: number, request: ReportTemplateRequest): Observable<ReportTemplate> {
    return this.http.put<ReportTemplate>(`${this.reportTemplatesUrl}/${id}`, request);
  }

  sectionOptionsFor(type: ReportType): ReportSectionOption[] {
    return this.templateSectionOptionsFor(type);
    switch (type) {
      case 'RESEARCH_UNIT':
        return [
          { key: 'SUMMARY', label: 'Resumen ejecutivo', description: 'Lectura breve del alcance visible.' },
          { key: 'OUTPUT', label: 'Producción visible', description: 'Volumen, periodo y publicaciones representativas.' },
          { key: 'TOPICS', label: 'Temas destacados', description: 'Temas con mayor presencia en la muestra.' },
          { key: 'RESEARCHERS', label: 'Investigadores vinculados', description: 'Perfiles con más producción asociada.' },
          { key: 'TREND', label: 'Evolución temporal', description: 'Ritmo visible por años.' },
          { key: 'EVIDENCE', label: 'Evidencia citada', description: 'Listado de publicaciones usadas como soporte.' }
        ];
      case 'RESEARCHER':
        return [
          { key: 'SUMMARY', label: 'Resumen ejecutivo', description: 'Síntesis del perfil y su foco visible.' },
          { key: 'PROFILE', label: 'Perfil y afiliación', description: 'Datos básicos y afiliaciones actuales.' },
          { key: 'OUTPUT', label: 'Producción visible', description: 'Publicaciones representativas y periodo.' },
          { key: 'TOPICS', label: 'Temas destacados', description: 'Temas y señales de especialización.' },
          { key: 'COLLABORATION', label: 'Colaboradores visibles', description: 'Red de coautoría y proximidad.' },
          { key: 'TREND', label: 'Evolución temporal', description: 'Trayectoria de la producción visible.' },
          { key: 'EVIDENCE', label: 'Evidencia citada', description: 'Publicaciones incluidas como referencia.' }
        ];
      case 'TOPIC':
        return [
          { key: 'SUMMARY', label: 'Resumen ejecutivo', description: 'Panorama general del tema.' },
          { key: 'OUTPUT', label: 'Producción visible', description: 'Base documental visible en el tema.' },
          { key: 'RESEARCHERS', label: 'Investigadores activos', description: 'Perfiles que más aparecen en el tema.' },
          { key: 'UNITS', label: 'Unidades activas', description: 'Estructuras más vinculadas al tema.' },
          { key: 'TREND', label: 'Evolución temporal', description: 'Señal de crecimiento o estabilidad.' },
          { key: 'RELATED', label: 'Temas relacionados', description: 'Conexiones temáticas cercanas.' },
          { key: 'EVIDENCE', label: 'Evidencia citada', description: 'Publicaciones seleccionadas para sostener el dossier.' }
        ];
      case 'STRATEGIC_LINE':
        return [
          { key: 'SUMMARY', label: 'Resumen ejecutivo', description: 'Síntesis de la línea y su grado de consolidación.' },
          { key: 'OUTPUT', label: 'Alcance visible', description: 'Volumen, cobertura y publicaciones representativas.' },
          { key: 'TOPICS', label: 'Temas y señales', description: 'Temas asociados y foco conceptual.' },
          { key: 'RESEARCHERS', label: 'Investigadores conectados', description: 'Perfiles visibles alrededor de la línea.' },
          { key: 'UNITS', label: 'Unidades implicadas', description: 'Unidades con presencia visible en la línea.' },
          { key: 'TREND', label: 'Tendencia', description: 'Lectura temporal de la línea.' },
          { key: 'RELATED', label: 'Líneas relacionadas', description: 'Solapamientos con otras líneas visibles.' },
          { key: 'EVIDENCE', label: 'Evidencia citada', description: 'Publicaciones que soportan la lectura estratégica.' }
        ];
    }
  }

  private templateSectionOptionsFor(type: ReportType): ReportSectionOption[] {
    const common = {
      executive: { key: 'EXECUTIVE_SUMMARY', label: 'Resumen ejecutivo', description: 'Lectura breve del alcance visible.' },
      overview: { key: 'PUBLICATION_OVERVIEW', label: 'Panorama de publicaciones', description: 'Volumen, periodo y publicaciones representativas.' },
      evolution: { key: 'YEARLY_EVOLUTION', label: 'EvoluciÃ³n anual', description: 'Ritmo visible por aÃ±os.' },
      topics: { key: 'TOP_TOPICS', label: 'Temas principales', description: 'Temas con mayor presencia en la muestra.' },
      researchers: { key: 'LINKED_RESEARCHERS', label: 'Investigadores vinculados', description: 'Perfiles conectados con la evidencia.' },
      units: { key: 'LINKED_UNITS', label: 'Unidades vinculadas', description: 'Estructuras vinculadas con la evidencia.' },
      publications: { key: 'REPRESENTATIVE_PUBLICATIONS', label: 'Publicaciones representativas', description: 'Base documental principal.' },
      collaborations: { key: 'COLLABORATIONS', label: 'Colaboraciones', description: 'Relaciones visibles en la evidencia.' },
      quality: { key: 'DATA_QUALITY', label: 'Calidad de datos', description: 'SeÃ±ales de completitud y cautelas de calidad.' },
      validation: { key: 'VALIDATION_STATUS', label: 'Estado de validaciÃ³n', description: 'Lectura del filtro de validaciÃ³n aplicado.' },
      opportunities: { key: 'OPPORTUNITIES', label: 'Oportunidades', description: 'Posibles usos, conexiones o decisiones.' },
      limitations: { key: 'LIMITATIONS', label: 'Limitaciones', description: 'Cobertura y cautelas del contexto.' },
      evidence: { key: 'CITED_EVIDENCE', label: 'Evidencia citada', description: 'Listado de publicaciones usadas como soporte.' }
    } satisfies Record<string, ReportSectionOption>;
    switch (type) {
      case 'RESEARCH_UNIT':
        return [common.executive, common.overview, common.evolution, common.topics, common.researchers, common.publications, common.collaborations, common.limitations, common.evidence];
      case 'RESEARCHER':
        return [common.executive, common.overview, common.topics, common.collaborations, common.evolution, common.limitations, common.evidence];
      case 'TOPIC':
        return [common.executive, common.overview, common.evolution, common.topics, common.researchers, common.units, common.opportunities, common.limitations, common.evidence];
      case 'STRATEGIC_LINE':
        return [common.executive, common.overview, common.evolution, common.topics, common.researchers, common.units, common.publications, common.opportunities, common.limitations, common.evidence];
    }
  }

  buildBaseTargets(context: ReportsContext, type: ReportType): ReportTargetOption[] {
    switch (type) {
      case 'RESEARCH_UNIT': {
        const metadataById = new Map(context.metadata.researchUnits.map((item) => [item.id ?? -1, item]));
        return context.units
          .map((unit) => {
            const metadata = metadataById.get(unit.id);
            return {
              id: `unit-${unit.id}`,
              type,
              label: unit.shortName ? `${unit.shortName} · ${unit.name}` : unit.name,
              helper: `${this.unitTypeLabel(unit.type)} · ${metadata?.count ?? 0} publicaciones visibles`,
              keywords: [unit.name, unit.shortName ?? '', unit.city ?? '', unit.country ?? ''],
              count: metadata?.count ?? 0,
              payload: { id: unit.id }
            } satisfies ReportTargetOption;
          })
          .sort((left, right) => (right.count ?? 0) - (left.count ?? 0) || left.label.localeCompare(right.label, 'es'));
      }
      case 'RESEARCHER':
        return context.researchers
          .map((researcher) => ({
            id: `researcher-${researcher.id}`,
            type,
            label: researcher.displayName || researcher.fullName,
            helper: [
              researcher.primaryAffiliation?.researchUnitName ?? 'Afiliación visible pendiente',
              `${researcher.authoredPublications.length} publicaciones visibles`
            ].join(' · '),
            keywords: [
              researcher.fullName,
              researcher.displayName ?? '',
              researcher.email ?? '',
              researcher.primaryAffiliation?.researchUnitName ?? ''
            ],
            count: researcher.authoredPublications.length,
            payload: { id: researcher.id }
          }))
          .sort((left, right) => (right.count ?? 0) - (left.count ?? 0) || left.label.localeCompare(right.label, 'es'));
      case 'TOPIC':
        return context.metadata.topics
          .map((topic) => ({
            id: `topic-${this.slugify(topic.label)}`,
            type,
            label: topic.label,
            helper: `${topic.count} publicaciones visibles`,
            keywords: [topic.label, topic.value],
            count: topic.count,
            payload: { topic: topic.label }
          }))
          .sort((left, right) => (right.count ?? 0) - (left.count ?? 0) || left.label.localeCompare(right.label, 'es'));
      case 'STRATEGIC_LINE':
        return [];
    }
  }

  loadStrategicLineTargets(yearFrom: number | null, yearTo: number | null): Observable<StrategicLineTargetSnapshot[]> {
    return this.loadContext().pipe(
      switchMap((context) =>
        this.strategicMapService.loadPublications({
          yearFrom,
          yearTo,
          researchUnitId: null,
          onlyValidated: true
        }).pipe(
          map((publications) => this.strategicMapService.buildMapData(publications, context.researchers, context.units).lines),
          map((lines) => lines.map((line) => ({
            target: {
              id: `line-${this.slugify(line.title)}`,
              type: 'STRATEGIC_LINE',
              label: line.title,
              helper: `${line.publicationCount} publicaciones · ${line.confidenceLabel.toLowerCase()} · ${line.trend.label.toLowerCase()}`,
              keywords: [
                line.title,
                line.description,
                ...line.topics.map((topic) => topic.label),
                ...line.units.map((unit) => unit.name)
              ],
              count: line.publicationCount,
              payload: { lineId: line.id }
            },
            line
          } satisfies StrategicLineTargetSnapshot)))
        )
      )
    );
  }

  generateReport(request: ReportGenerationRequest): Observable<GeneratedReport> {
    return this.http.post<BackendGeneratedReport>(`${this.reportsUrl}/generate`, this.toBackendRequest(request)).pipe(
      map((response) => this.fromBackendReport(request, response))
    );
  }

  private toBackendRequest(request: ReportGenerationRequest): BackendReportGenerationRequest {
    return {
      reportType: request.type,
      templateId: request.templateId,
      targetId: request.target.payload.id ?? null,
      query: request.target.payload.topic ?? request.target.label,
      yearFrom: request.yearFrom,
      yearTo: request.yearTo,
      includeSections: request.sections,
      onlyValidated: true,
      additionalInstructions: request.additionalInstructions.trim() || null
    };
  }

  private fromBackendReport(request: ReportGenerationRequest, response: BackendGeneratedReport): GeneratedReport {
    const subtitle = [
      this.typeLabel(request.type),
      request.templateId ? 'plantilla configurable' : 'sin plantilla',
      this.yearRangeLabel(request.yearFrom, request.yearTo)
    ].join(' · ');
    return {
      title: response.reportTitle,
      subtitle,
      generatedAt: response.generatedAt,
      yearFrom: request.yearFrom,
      yearTo: request.yearTo,
      type: request.type,
      target: request.target,
      sections: request.sections,
      sectionOptions: this.sectionOptionsFor(request.type),
      template: null,
      summaryMetrics: [
        { label: 'Publicaciones citadas', value: String(response.citedPublications.length) },
        { label: 'Proveedor IA', value: response.provider },
        { label: 'Modelo', value: response.model },
        { label: 'Periodo', value: this.yearRangeLabel(request.yearFrom, request.yearTo) }
      ],
      warnings: response.warnings,
      markdown: response.markdownContent,
      exportMarkdown: this.toExportMarkdown(response.reportTitle, subtitle, response.generatedAt, response.markdownContent, response.citedPublications),
      citations: response.citedPublications
    };
  }

  private buildResearchUnitReport(context: ReportsContext, request: ReportGenerationRequest): Observable<GeneratedReport> {
    const unitId = request.target.payload.id;
    const unit = context.units.find((candidate) => candidate.id === unitId);
    if (!unitId || !unit) {
      return this.reportError('No se pudo identificar la unidad seleccionada.');
    }

    return this.loadAllPublications({
      researchUnitId: unitId,
      yearFrom: request.yearFrom ?? undefined,
      yearTo: request.yearTo ?? undefined,
      sortBy: 'year',
      sortDirection: 'desc'
    }).pipe(
      switchMap((publications) => {
        const topicCounts = this.topicCounts(publications);
        const topResearchers = this.topResearchersForPublications(publications, context.researchers).slice(0, 5);
        const citationsBase = publications.slice(0, 6);
        return this.toCitations(citationsBase).pipe(
          map((citations) => {
            const citationMap = this.citationMap(citations);
            const summarySentences = [
              `${unit.name} concentra ${this.publicationCountLabel(publications.length)} en el periodo ${this.yearRangeLabel(request.yearFrom, request.yearTo)}${this.citationSentence(citationsBase[0], citationMap)}.`,
              topicCounts.length > 0
                ? `Los temas con mayor presencia visible son ${this.topicList(topicCounts.slice(0, 3))}${this.citationSentence(citationsBase[1] ?? citationsBase[0], citationMap)}.`
                : 'Todavía no se observan temas normalizados con suficiente señal en esta muestra.',
              topResearchers.length > 0
                ? `Los perfiles más conectados en la muestra son ${topResearchers.map((item) => item.researcher.displayName || item.researcher.fullName).slice(0, 3).join(', ')}.`
                : 'No se identifican investigadores con producción visible suficiente para priorización.'
            ];

            const markdown = this.composeMarkdown(
              request,
              this.sectionOptionsFor(request.type),
              {
                SUMMARY: [
                  '## Resumen ejecutivo',
                  summarySentences.join(' ')
                ],
                OUTPUT: [
                  '## Producción visible',
                  `- ${this.publicationCountLabel(publications.length)} en el rango analizado.`,
                  `- Ventana temporal visible: ${this.yearSpan(publications)}.`,
                  `- Publicaciones representativas: ${citationsBase.map((publication) => `${publication.title}${this.citationSentence(publication, citationMap)}`).join('; ')}.`
                ],
                TOPICS: [
                  '## Temas destacados',
                  ...this.topicBulletLines(topicCounts, 5)
                ],
                RESEARCHERS: [
                  '## Investigadores vinculados',
                  ...topResearchers.map((item) => `- ${item.researcher.displayName || item.researcher.fullName}: ${item.publicationCount} publicaciones visibles.`)
                ],
                TREND: [
                  '## Evolución temporal',
                  this.trendNarrative(publications)
                ],
                EVIDENCE: [
                  '## Evidencia citada',
                  ...citations.map((citation) => `- [${citation.citationIndex}] ${citation.title} (${citation.year ?? 's. f.'}).`)
                ]
              }
            );

            return this.createReport(request, {
              title: `Informe · ${unit.name}`,
              subtitle: `Unidad de investigación · ${this.yearRangeLabel(request.yearFrom, request.yearTo)}`,
              markdown,
              citations,
              warnings: publications.length === 0
                ? ['La unidad no tiene publicaciones visibles con los filtros actuales.']
                : publications.length < 3
                  ? ['La base visible es reducida; conviene revisar un rango temporal más amplio.']
                  : [],
              summaryMetrics: [
                { label: 'Publicaciones base', value: String(publications.length) },
                { label: 'Investigadores visibles', value: String(topResearchers.length) },
                { label: 'Temas detectados', value: String(topicCounts.length) },
                { label: 'Periodo', value: this.yearRangeLabel(request.yearFrom, request.yearTo) }
              ]
            });
          })
        );
      })
    );
  }

  private buildResearcherReport(context: ReportsContext, request: ReportGenerationRequest): Observable<GeneratedReport> {
    const researcherId = request.target.payload.id;
    const researcher = context.researchers.find((candidate) => candidate.id === researcherId);
    if (!researcherId || !researcher) {
      return this.reportError('No se pudo identificar el investigador seleccionado.');
    }

    return this.loadAllPublications({
      researcherId,
      yearFrom: request.yearFrom ?? undefined,
      yearTo: request.yearTo ?? undefined,
      sortBy: 'year',
      sortDirection: 'desc'
    }).pipe(
      switchMap((publications) => {
        const topicCounts = this.topicCounts(publications);
        const coauthors = this.topCoauthors(researcher.coauthors);
        const citationsBase = publications.slice(0, 6);
        return this.toCitations(citationsBase).pipe(
          map((citations) => {
            const citationMap = this.citationMap(citations);
            const displayName = researcher.displayName || researcher.fullName;
            const markdown = this.composeMarkdown(
              request,
              this.sectionOptionsFor(request.type),
              {
                SUMMARY: [
                  '## Resumen ejecutivo',
                  `${displayName} aporta ${this.publicationCountLabel(publications.length)} en el periodo ${this.yearRangeLabel(request.yearFrom, request.yearTo)}${this.citationSentence(citationsBase[0], citationMap)}.`,
                  topicCounts.length > 0
                    ? `Su foco visible gira alrededor de ${this.topicList(topicCounts.slice(0, 3))}${this.citationSentence(citationsBase[1] ?? citationsBase[0], citationMap)}.`
                    : 'No se detectan temas suficientemente repetidos para sintetizar un foco dominante.'
                ],
                PROFILE: [
                  '## Perfil y afiliación',
                  `- Nombre visible: ${displayName}.`,
                  `- Afiliación principal: ${researcher.primaryAffiliation?.researchUnitName ?? 'No visible'}.`,
                  `- ORCID: ${researcher.orcid || 'No visible'}.`,
                  `- Correo visible: ${researcher.email || 'No visible'}.`
                ],
                OUTPUT: [
                  '## Producción visible',
                  `- ${this.publicationCountLabel(publications.length)} en el rango analizado.`,
                  `- Periodo visible: ${this.yearSpan(publications)}.`,
                  `- Publicaciones representativas: ${citationsBase.map((publication) => `${publication.title}${this.citationSentence(publication, citationMap)}`).join('; ')}.`
                ],
                TOPICS: [
                  '## Temas destacados',
                  ...this.topicBulletLines(topicCounts, 5)
                ],
                COLLABORATION: [
                  '## Colaboradores visibles',
                  ...(coauthors.length > 0
                    ? coauthors.map((coauthor) => `- ${coauthor.name}: ${coauthor.sharedPublicationCount} publicaciones compartidas visibles.`)
                    : ['- No se detectan colaboradores repetidos en la visibilidad actual.'])
                ],
                TREND: [
                  '## Evolución temporal',
                  this.trendNarrative(publications)
                ],
                EVIDENCE: [
                  '## Evidencia citada',
                  ...citations.map((citation) => `- [${citation.citationIndex}] ${citation.title} (${citation.year ?? 's. f.'}).`)
                ]
              }
            );

            return this.createReport(request, {
              title: `Informe · ${displayName}`,
              subtitle: `Investigador · ${this.yearRangeLabel(request.yearFrom, request.yearTo)}`,
              markdown,
              citations,
              warnings: publications.length === 0
                ? ['No hay publicaciones visibles con los filtros actuales para este perfil.']
                : researcher.primaryAffiliation === null
                  ? ['La afiliación principal no está visible en el contexto actual.']
                  : [],
              summaryMetrics: [
                { label: 'Publicaciones base', value: String(publications.length) },
                { label: 'Temas detectados', value: String(topicCounts.length) },
                { label: 'Coautores visibles', value: String(coauthors.length) },
                { label: 'Periodo', value: this.yearRangeLabel(request.yearFrom, request.yearTo) }
              ]
            });
          })
        );
      })
    );
  }

  private buildTopicReport(context: ReportsContext, request: ReportGenerationRequest): Observable<GeneratedReport> {
    const topic = request.target.payload.topic;
    if (!topic) {
      return this.reportError('No se pudo identificar el tema seleccionado.');
    }

    return this.loadAllPublications({
      topic,
      yearFrom: request.yearFrom ?? undefined,
      yearTo: request.yearTo ?? undefined,
      sortBy: 'year',
      sortDirection: 'desc'
    }).pipe(
      switchMap((publications) => {
        const topResearchers = this.topResearchersForPublications(publications, context.researchers).slice(0, 6);
        const topUnits = this.topUnitsForResearchers(topResearchers, context.units).slice(0, 5);
        const relatedTopics = this.relatedTopics(publications, topic);
        const citationsBase = publications.slice(0, 6);
        return this.toCitations(citationsBase).pipe(
          map((citations) => {
            const citationMap = this.citationMap(citations);
            const markdown = this.composeMarkdown(
              request,
              this.sectionOptionsFor(request.type),
              {
                SUMMARY: [
                  '## Resumen ejecutivo',
                  `El tema ${topic} reúne ${this.publicationCountLabel(publications.length)} en el periodo ${this.yearRangeLabel(request.yearFrom, request.yearTo)}${this.citationSentence(citationsBase[0], citationMap)}.`,
                  topResearchers.length > 0
                    ? `Los perfiles más visibles son ${topResearchers.slice(0, 3).map((item) => item.researcher.displayName || item.researcher.fullName).join(', ')}.`
                    : 'Todavía no se distinguen perfiles con masa crítica suficiente en la muestra actual.'
                ],
                OUTPUT: [
                  '## Producción visible',
                  `- ${this.publicationCountLabel(publications.length)}.`,
                  `- Cobertura temporal visible: ${this.yearSpan(publications)}.`,
                  `- Publicaciones representativas: ${citationsBase.map((publication) => `${publication.title}${this.citationSentence(publication, citationMap)}`).join('; ')}.`
                ],
                RESEARCHERS: [
                  '## Investigadores activos',
                  ...topResearchers.map((item) => `- ${item.researcher.displayName || item.researcher.fullName}: ${item.publicationCount} publicaciones visibles en el tema.`)
                ],
                UNITS: [
                  '## Unidades activas',
                  ...(topUnits.length > 0
                    ? topUnits.map((unit) => `- ${unit.name}: ${unit.publicationCount} publicaciones y ${unit.researcherCount} investigadores visibles.`)
                    : ['- No se identifican unidades con señal suficiente en la muestra actual.'])
                ],
                TREND: [
                  '## Evolución temporal',
                  this.trendNarrative(publications)
                ],
                RELATED: [
                  '## Temas relacionados',
                  ...(relatedTopics.length > 0
                    ? relatedTopics.map((item) => `- ${item.label}: ${item.count} coapariciones visibles.`)
                    : ['- No se detectan relaciones temáticas consistentes con la visibilidad actual.'])
                ],
                EVIDENCE: [
                  '## Evidencia citada',
                  ...citations.map((citation) => `- [${citation.citationIndex}] ${citation.title} (${citation.year ?? 's. f.'}).`)
                ]
              }
            );

            return this.createReport(request, {
              title: `Informe · ${topic}`,
              subtitle: `Tema · ${this.yearRangeLabel(request.yearFrom, request.yearTo)}`,
              markdown,
              citations,
              warnings: publications.length < 3
                ? ['El tema tiene una base visible limitada con los filtros actuales.']
                : [],
              summaryMetrics: [
                { label: 'Publicaciones base', value: String(publications.length) },
                { label: 'Investigadores visibles', value: String(topResearchers.length) },
                { label: 'Unidades activas', value: String(topUnits.length) },
                { label: 'Periodo', value: this.yearRangeLabel(request.yearFrom, request.yearTo) }
              ]
            });
          })
        );
      })
    );
  }

  private buildStrategicLineReport(context: ReportsContext, request: ReportGenerationRequest): Observable<GeneratedReport> {
    return this.loadStrategicLineTargets(request.yearFrom, request.yearTo).pipe(
      switchMap((snapshots) => {
        const snapshot = snapshots.find((candidate) => candidate.line.id === request.target.payload.lineId);
        if (!snapshot) {
          return this.reportError('No se pudo reconstruir la línea estratégica seleccionada con los filtros actuales.');
        }
        const line = snapshot.line;
        return this.toCitations(line.publications.map((publication) => ({ ...publication, topics: publication.topics } as PublicationSummary)).slice(0, 6)).pipe(
          map((citations) => {
            const citationMap = this.citationMap(citations);
            const markdown = this.composeMarkdown(
              request,
              this.sectionOptionsFor(request.type),
              {
                SUMMARY: [
                  '## Resumen ejecutivo',
                  `${line.title} se apoya en ${line.publicationCount} publicaciones visibles y presenta ${line.confidenceLabel.toLowerCase()}${this.citationSentenceFromId(line.publications[0]?.id, citationMap)}.`,
                  `${line.description}`
                ],
                OUTPUT: [
                  '## Alcance visible',
                  `- ${line.publicationCount} publicaciones visibles.`,
                  `- ${line.researchers.length} investigadores conectados.`,
                  `- ${line.units.length} unidades implicadas.`,
                  `- Publicaciones representativas: ${line.representativePublications.map((publication) => `${publication.title}${this.citationSentenceFromId(publication.id, citationMap)}`).join('; ')}.`
                ],
                TOPICS: [
                  '## Temas y señales',
                  ...line.topics.map((topic) => `- ${topic.label}: ${topic.count} apariciones visibles.`)
                ],
                RESEARCHERS: [
                  '## Investigadores conectados',
                  ...line.researchers.map((researcher) => `- ${researcher.name}: ${researcher.publicationCount} publicaciones visibles y ${researcher.topicMatchCount} coincidencias temáticas.`)
                ],
                UNITS: [
                  '## Unidades implicadas',
                  ...line.units.map((unit) => `- ${unit.name}: ${unit.publicationCount} publicaciones y ${unit.researcherCount} investigadores visibles.`)
                ],
                TREND: [
                  '## Tendencia',
                  `${line.trend.label}. ${line.trend.detail}`
                ],
                RELATED: [
                  '## Líneas relacionadas',
                  ...(line.relatedLines.length > 0
                    ? line.relatedLines.map((relatedLine) => `- ${relatedLine.title}: ${relatedLine.sharedTopics} temas compartidos y ${relatedLine.sharedPublications} publicaciones comunes.`)
                    : ['- No se observan líneas próximas con suficiente solapamiento visible.'])
                ],
                EVIDENCE: [
                  '## Evidencia citada',
                  ...citations.map((citation) => `- [${citation.citationIndex}] ${citation.title} (${citation.year ?? 's. f.'}).`)
                ]
              }
            );

            return this.createReport(request, {
              title: `Informe · ${line.title}`,
              subtitle: `Línea estratégica · ${this.yearRangeLabel(request.yearFrom, request.yearTo)}`,
              markdown,
              citations,
              warnings: line.warnings,
              summaryMetrics: [
                { label: 'Publicaciones base', value: String(line.publicationCount) },
                { label: 'Investigadores conectados', value: String(line.researchers.length) },
                { label: 'Unidades implicadas', value: String(line.units.length) },
                { label: 'Periodo', value: this.yearRangeLabel(request.yearFrom, request.yearTo) }
              ]
            });
          })
        );
      })
    );
  }

  private createReport(
    request: ReportGenerationRequest,
    data: {
      title: string;
      subtitle: string;
      markdown: string;
      citations: CopilotCitation[];
      warnings: string[];
      summaryMetrics: { label: string; value: string }[];
    }
  ): GeneratedReport {
    const generatedAt = new Date().toISOString();
    return {
      title: data.title,
      subtitle: data.subtitle,
      generatedAt,
      yearFrom: request.yearFrom,
      yearTo: request.yearTo,
      type: request.type,
      target: request.target,
      sections: request.sections,
      sectionOptions: this.sectionOptionsFor(request.type),
      template: null,
      summaryMetrics: data.summaryMetrics,
      warnings: data.warnings,
      markdown: data.markdown,
      exportMarkdown: this.toExportMarkdown(data.title, data.subtitle, generatedAt, data.markdown, data.citations),
      citations: data.citations
    };
  }

  private reportError(message: string): Observable<GeneratedReport> {
    return new Observable<GeneratedReport>((subscriber) => subscriber.error(new Error(message)));
  }

  private loadAllPublications(filters: PublicationFilters): Observable<PublicationSummary[]> {
    return this.loadPublicationPage({ ...filters, page: 0, size: 100 }).pipe(
      switchMap((firstPage) => {
        if (firstPage.totalPages <= 1) {
          return of(firstPage.content);
        }
        const remainingPages = Array.from(
          { length: Math.max(firstPage.totalPages - 1, 0) },
          (_, index) => this.loadPublicationPage({ ...filters, page: index + 1, size: 100 })
        );
        return forkJoin(remainingPages).pipe(
          map((pages) => [firstPage, ...pages].flatMap((page) => page.content))
        );
      })
    );
  }

  private loadPublicationPage(filters: PublicationFilters): Observable<PageResponse<PublicationSummary>> {
    return this.publicationsApi.search(filters);
  }

  private toCitations(publications: PublicationSummary[]): Observable<CopilotCitation[]> {
    if (publications.length === 0) {
      return of([]);
    }
    return forkJoin(
      publications.map((publication) =>
        this.publicationsApi.get(publication.id).pipe(
          map((detail) => this.toCitation(detail, publication)),
          catchError(() => of(this.toFallbackCitation(publication)))
        )
      )
    ).pipe(
      map((citations) => citations.map((citation, index) => ({ ...citation, citationIndex: index + 1 })))
    );
  }

  private toCitation(detail: Publication, summary: PublicationSummary): CopilotCitation {
    return {
      id: detail.id,
      citationIndex: 0,
      title: detail.title,
      year: detail.year,
      authors: detail.authors
        .sort((left, right) => left.authorOrder - right.authorOrder)
        .map((author) => author.researcherName || author.externalAuthorName || 'Autor no visible'),
      topics: detail.topics.map((topic) => topic.name),
      doi: detail.doi,
      source: detail.source,
      url: detail.url,
      similarityScore: null
    };
  }

  private toFallbackCitation(summary: PublicationSummary): CopilotCitation {
    return {
      id: summary.id,
      citationIndex: 0,
      title: summary.title,
      year: summary.year,
      authors: [],
      topics: summary.topics,
      doi: summary.doi,
      source: summary.source,
      url: null,
      similarityScore: null
    };
  }

  private citationMap(citations: CopilotCitation[]): Map<number, CopilotCitation> {
    return new Map(citations.map((citation) => [citation.id, citation]));
  }

  private composeMarkdown(
    request: ReportGenerationRequest,
    sectionOptions: ReportSectionOption[],
    sections: Partial<Record<ReportSectionKey, string[]>>
  ): string {
    const selectedKeys = request.sections.length > 0
      ? request.sections
      : sectionOptions.map((option) => option.key);

    return [
      `# ${request.target.label}`,
      '',
      ...selectedKeys.flatMap((key) => {
        const block = sections[key] ?? [];
        return block.length > 0 ? [...block, ''] : [];
      })
    ].join('\n').trim();
  }

  private toExportMarkdown(title: string, subtitle: string, generatedAt: string, markdown: string, citations: CopilotCitation[]): string {
    const citationLabelById = new Map(citations.map((citation) => [citation.id, citation.citationIndex]));
    const body = markdown.replace(/\[(?:pub|publication):(\d+)]/gi, (_, rawId: string) => {
      const publicationId = Number(rawId);
      const index = citationLabelById.get(publicationId);
      return index ? `[${index}]` : '[?]';
    });

    const references = citations.length > 0
      ? [
        '',
        '## Publicaciones citadas',
        ...citations.map((citation) => `- [${citation.citationIndex}] ${this.citationExportLine(citation)}`)
      ]
      : [];

    return [
      `# ${title}`,
      '',
      subtitle,
      '',
      `Generado: ${this.formatDateTime(generatedAt)}`,
      '',
      body,
      ...references
    ].join('\n').trim();
  }

  private citationExportLine(citation: CopilotCitation): string {
    const authors = citation.authors.length > 0 ? `${citation.authors.join(', ')}. ` : '';
    const source = citation.source ? ` ${citation.source}.` : '';
    const doi = citation.doi ? ` DOI: ${citation.doi}.` : '';
    return `${authors}${citation.title} (${citation.year ?? 's. f.'}).${source}${doi}`.replace(/\s+/g, ' ').trim();
  }

  private topicCounts(publications: PublicationSummary[]): TopicCount[] {
    const counts = new Map<string, number>();
    for (const publication of publications) {
      for (const topic of publication.topics) {
        const label = topic.trim();
        if (!label) {
          continue;
        }
        counts.set(label, (counts.get(label) ?? 0) + 1);
      }
    }
    return [...counts.entries()]
      .map(([label, count]) => ({ label, count }))
      .sort((left, right) => right.count - left.count || left.label.localeCompare(right.label, 'es'));
  }

  private topResearchersForPublications(publications: PublicationSummary[], researchers: Researcher[]): CountedResearcher[] {
    const publicationIds = new Set(publications.map((publication) => publication.id));
    return researchers
      .map((researcher) => ({
        researcher,
        publicationCount: researcher.authoredPublications.filter((publication) => publicationIds.has(publication.id)).length
      }))
      .filter((item) => item.publicationCount > 0)
      .sort((left, right) => right.publicationCount - left.publicationCount || (left.researcher.displayName || left.researcher.fullName).localeCompare(right.researcher.displayName || right.researcher.fullName, 'es'));
  }

  private topUnitsForResearchers(countedResearchers: CountedResearcher[], units: ResearchUnit[]): CountedUnit[] {
    const unitsById = new Map(units.map((unit) => [unit.id, unit]));
    const aggregates = new Map<string, CountedUnit>();

    for (const item of countedResearchers) {
      const affiliations = item.researcher.currentAffiliations.length > 0
        ? item.researcher.currentAffiliations
        : item.researcher.primaryAffiliation
          ? [item.researcher.primaryAffiliation]
          : [];

      const seenKeys = new Set<string>();
      for (const affiliation of affiliations) {
        const unit = unitsById.get(affiliation.researchUnitId) ?? null;
        const name = affiliation.researchUnitName ?? unit?.name ?? 'Unidad no visible';
        const key = `${affiliation.researchUnitId}:${name}`;
        if (seenKeys.has(key)) {
          continue;
        }
        seenKeys.add(key);
        const existing = aggregates.get(key);
        if (existing) {
          existing.publicationCount += item.publicationCount;
          existing.researcherCount += 1;
          continue;
        }
        aggregates.set(key, {
          unit,
          name,
          publicationCount: item.publicationCount,
          researcherCount: 1
        });
      }
    }

    return [...aggregates.values()]
      .sort((left, right) => right.publicationCount - left.publicationCount || left.name.localeCompare(right.name, 'es'));
  }

  private relatedTopics(publications: PublicationSummary[], anchorTopic: string): TopicCount[] {
    return this.topicCounts(publications)
      .filter((topic) => this.normalize(topic.label) !== this.normalize(anchorTopic))
      .slice(0, 5);
  }

  private topCoauthors(coauthors: ResearcherCoauthor[]): ResearcherCoauthor[] {
    return [...coauthors]
      .sort((left, right) => right.sharedPublicationCount - left.sharedPublicationCount || left.name.localeCompare(right.name, 'es'))
      .slice(0, 5);
  }

  private trendNarrative(publications: PublicationSummary[]): string {
    const yearCounts = new Map<number, number>();
    for (const publication of publications) {
      if (publication.year !== null) {
        yearCounts.set(publication.year, (yearCounts.get(publication.year) ?? 0) + 1);
      }
    }
    const years = [...yearCounts.keys()].sort((left, right) => left - right);
    if (years.length === 0) {
      return 'La muestra no incluye fechas visibles suficientes para una lectura temporal.';
    }
    if (years.length === 1) {
      return `La producción visible se concentra en ${years[0]}, con ${yearCounts.get(years[0])} publicaciones.`;
    }

    const lastYear = years[years.length - 1];
    const previousYear = years[Math.max(years.length - 2, 0)];
    const lastCount = yearCounts.get(lastYear) ?? 0;
    const previousCount = yearCounts.get(previousYear) ?? 0;

    if (lastCount > previousCount) {
      return `La señal más reciente apunta a crecimiento: ${lastCount} publicaciones en ${lastYear} frente a ${previousCount} en ${previousYear}.`;
    }
    if (lastCount < previousCount) {
      return `La presencia más reciente es menor: ${lastCount} publicaciones en ${lastYear} frente a ${previousCount} en ${previousYear}.`;
    }
    return `La producción visible se mantiene estable entre ${previousYear} y ${lastYear}, con ${lastCount} publicaciones en ambos cortes.`;
  }

  private topicBulletLines(topicCounts: TopicCount[], limit: number): string[] {
    if (topicCounts.length === 0) {
      return ['- Sin temas suficientemente repetidos en la muestra actual.'];
    }
    return topicCounts.slice(0, limit).map((topic) => `- ${topic.label}: ${topic.count} apariciones visibles.`);
  }

  private publicationCountLabel(count: number): string {
    return count === 1 ? '1 publicación visible' : `${count} publicaciones visibles`;
  }

  private typeLabel(type: ReportType): string {
    return this.reportTypes.find((option) => option.value === type)?.label ?? type;
  }

  private yearRangeLabel(yearFrom: number | null, yearTo: number | null): string {
    if (yearFrom !== null && yearTo !== null) {
      return `${yearFrom}-${yearTo}`;
    }
    if (yearFrom !== null) {
      return `desde ${yearFrom}`;
    }
    if (yearTo !== null) {
      return `hasta ${yearTo}`;
    }
    return 'todos los años visibles';
  }

  private yearSpan(publications: PublicationSummary[]): string {
    const years = publications
      .map((publication) => publication.year)
      .filter((year): year is number => year !== null)
      .sort((left, right) => left - right);
    if (years.length === 0) {
      return 'sin años visibles';
    }
    return years[0] === years[years.length - 1]
      ? `${years[0]}`
      : `${years[0]}-${years[years.length - 1]}`;
  }

  private topicList(topics: TopicCount[]): string {
    return topics.map((topic) => `${topic.label} (${topic.count})`).join(', ');
  }

  private citationSentence(publication: PublicationSummary | undefined, citationMap: Map<number, CopilotCitation>): string {
    if (!publication) {
      return '';
    }
    return this.citationSentenceFromId(publication.id, citationMap);
  }

  private citationSentenceFromId(publicationId: number | undefined, citationMap: Map<number, CopilotCitation>): string {
    if (!publicationId) {
      return '';
    }
    return citationMap.has(publicationId) ? ` [publication:${publicationId}]` : '';
  }

  private formatDateTime(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  private unitTypeLabel(value: string): string {
    switch (value) {
      case 'UNIVERSITY':
        return 'Universidad';
      case 'FACULTY':
        return 'Facultad';
      case 'SCHOOL':
        return 'Escuela';
      case 'DEPARTMENT':
        return 'Departamento';
      case 'INSTITUTE':
        return 'Instituto';
      case 'RESEARCH_GROUP':
        return 'Grupo';
      case 'LAB':
        return 'Laboratorio';
      case 'CENTER':
        return 'Centro';
      case 'HOSPITAL':
        return 'Hospital';
      case 'COMPANY':
        return 'Empresa';
      case 'FOUNDATION':
        return 'Fundación';
      case 'GOVERNMENT_AGENCY':
        return 'Agencia';
      default:
        return 'Unidad';
    }
  }

  private slugify(value: string): string {
    return this.normalize(value).replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  }

  private normalize(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
  }
}
