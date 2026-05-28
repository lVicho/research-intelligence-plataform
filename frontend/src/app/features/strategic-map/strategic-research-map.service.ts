import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap, catchError } from 'rxjs';

import {
  PageResponse,
  PublicationSummary,
  ResearchUnit,
  Researcher,
  ResearcherAffiliation,
  StrategicResearchMapLineResponse,
  StrategicResearchMapPublicationReference,
  StrategicResearchMapResponse
} from '../../core/api/api-models';
import { PublicationFilters, PublicationsApiService } from '../../core/api/publications-api.service';
import { ResearchersApiService } from '../../core/api/researchers-api.service';
import { ResearchUnitsApiService } from '../../core/api/research-units-api.service';
import { StrategicMapApiService } from '../../core/api/strategic-map-api.service';
import {
  StrategicResearchEvidenceItem,
  StrategicResearchLine,
  StrategicResearchMapContext,
  StrategicResearchMapData,
  StrategicResearchMapFilters,
  StrategicResearchMapPageContext,
  StrategicResearchPublicationReference,
  StrategicResearchRelatedLine,
  StrategicResearchResearcher,
  StrategicResearchTrend,
  StrategicResearchTopic,
  StrategicResearchUnitSummary
} from './strategic-research-map.models';

interface TopicAggregate {
  normalizedLabel: string;
  displayLabel: string;
  publicationIds: Set<number>;
}

interface TopicVariantAggregate {
  displayLabel: string;
  count: number;
}

@Injectable({ providedIn: 'root' })
export class StrategicResearchMapService {
  private readonly publicationsApi = inject(PublicationsApiService);
  private readonly researchersApi = inject(ResearchersApiService);
  private readonly unitsApi = inject(ResearchUnitsApiService);
  private readonly strategicMapApi = inject(StrategicMapApiService);

  loadPageContext(): Observable<StrategicResearchMapPageContext> {
    return forkJoin({
      metadata: this.publicationsApi.filterMetadata(),
      units: this.unitsApi.list()
    });
  }

  loadContext(): Observable<StrategicResearchMapContext> {
    return forkJoin({
      metadata: this.publicationsApi.filterMetadata(),
      units: this.unitsApi.list(),
      researchers: this.loadAllResearchers()
    });
  }

  loadMapData(filters: StrategicResearchMapFilters): Observable<StrategicResearchMapData> {
    return this.strategicMapApi.researchLines({
      yearFrom: filters.yearFrom ?? undefined,
      yearTo: filters.yearTo ?? undefined,
      researchUnitId: filters.researchUnitId ?? undefined,
      onlyValidated: filters.onlyValidated
    }).pipe(
      map((response) => this.toMapData(response))
    );
  }

  loadPublications(filters: StrategicResearchMapFilters): Observable<PublicationSummary[]> {
    const publicationFilters: PublicationFilters = {
      size: 100,
      sortBy: 'year',
      sortDirection: 'desc',
      yearFrom: filters.yearFrom ?? undefined,
      yearTo: filters.yearTo ?? undefined,
      researchUnitId: filters.researchUnitId ?? undefined
    };

    return this.loadAllPages((page) => this.publicationsApi.search({ ...publicationFilters, page }));
  }

  buildMapData(
    publications: PublicationSummary[],
    researchers: Researcher[],
    units: ResearchUnit[]
  ): StrategicResearchMapData {
    const lines = this.buildLines(publications, researchers, units);
    const researcherIds = new Set<number>();
    const unitKeys = new Set<string>();

    for (const line of lines) {
      line.researchers.forEach((researcher) => researcherIds.add(researcher.id));
      line.units.forEach((unit) => unitKeys.add(this.unitKey(unit.id, unit.name)));
    }

    return {
      overview: {
        lineCount: lines.length,
        publicationCount: publications.length,
        researcherCount: researcherIds.size,
        unitCount: unitKeys.size
      },
      lines
    };
  }

  private buildLines(
    publications: PublicationSummary[],
    researchers: Researcher[],
    units: ResearchUnit[]
  ): StrategicResearchLine[] {
    if (publications.length === 0) {
      return [];
    }

    const publicationsById = new Map(publications.map((publication) => [publication.id, publication]));
    const topicAggregates = this.buildTopicAggregates(publications);
    const selectedTopics = this.selectAnchorTopics(topicAggregates);

    const lines = selectedTopics.map((topic) => {
      const linePublications = [...topic.publicationIds]
        .map((publicationId) => publicationsById.get(publicationId))
        .filter((publication): publication is PublicationSummary => publication !== undefined)
        .sort((left, right) => this.comparePublications(left, right));

      const lineTopics = this.collectLineTopics(linePublications, topic.normalizedLabel);
      const researchersForLine = this.collectResearchersForLine(linePublications, researchers, lineTopics);
      const unitsForLine = this.collectUnitsForLine(researchersForLine, researchers, units);
      const representativePublications = linePublications.slice(0, 3).map((publication) => this.toPublicationReference(publication));
      const detailPublications = linePublications.slice(0, 8).map((publication) => this.toPublicationReference(publication));
      const trend = this.buildTrend(linePublications);
      const confidence = this.buildConfidence(linePublications.length, researchersForLine.length, unitsForLine.length, lineTopics.length, trend.available);
      const warnings = this.buildWarnings(confidence.score, linePublications.length, researchersForLine.length, trend.available);

      return {
        id: `line-${topic.normalizedLabel}`,
        title: this.titleCase(topic.displayLabel),
        description: this.buildDescription(topic.displayLabel, lineTopics, unitsForLine, linePublications),
        publicationCount: linePublications.length,
        confidenceScore: confidence.score,
        confidenceLabel: confidence.label,
        confidenceTone: confidence.tone,
        warnings,
        topics: lineTopics,
        researchers: researchersForLine,
        units: unitsForLine,
        representativePublications,
        publications: detailPublications,
        relatedLines: [],
        evidence: this.buildEvidence(linePublications, lineTopics),
        trend
      };
    });

    const relatedLinesById = this.buildRelatedLines(lines);
    return lines
      .map((line) => ({
        ...line,
        relatedLines: relatedLinesById.get(line.id) ?? []
      }))
      .sort((left, right) => right.publicationCount - left.publicationCount || left.title.localeCompare(right.title, 'es'));
  }

  private toMapData(response: StrategicResearchMapResponse): StrategicResearchMapData {
    const baseLines = response.researchLines.map((line) => this.toLine(line));
    const relatedLinesById = this.buildRelatedLines(baseLines);
    const lines = baseLines.map((line) => ({
      ...line,
      relatedLines: relatedLinesById.get(line.id) ?? []
    }));
    const researcherIds = new Set<number>();
    const unitKeys = new Set<string>();
    const publicationCount = lines.reduce((total, line) => total + line.publicationCount, 0);

    for (const line of lines) {
      line.researchers.forEach((researcher) => researcherIds.add(researcher.id));
      line.units.forEach((unit) => unitKeys.add(this.unitKey(unit.id, unit.name)));
    }

    return {
      overview: {
        lineCount: lines.length,
        publicationCount,
        researcherCount: researcherIds.size,
        unitCount: unitKeys.size
      },
      lines
    };
  }

  private toLine(line: StrategicResearchMapLineResponse): StrategicResearchLine {
    const publications = line.representativePublications.map((publication) => this.toBackendPublicationReference(publication));
    const topics = line.topics.map((topic) => ({
      label: topic.name,
      count: topic.publicationCount
    }));
    const trend = this.toTrend(line.trendSummary);
    const confidence = this.toConfidence(line.confidence);

    return {
      id: line.lineId,
      title: line.title,
      description: line.description,
      publicationCount: line.publicationCount,
      confidenceScore: line.confidence ?? 0,
      confidenceLabel: confidence.label,
      confidenceTone: confidence.tone,
      warnings: line.warnings,
      topics,
      researchers: line.researchers
        .filter((researcher): researcher is StrategicResearchResearcher & { id: number } => researcher.id !== null)
        .map((researcher) => ({
          id: researcher.id,
          name: researcher.name,
          primaryAffiliation: null,
          publicationCount: researcher.publicationCount,
          topicMatchCount: 0
        })),
      units: line.researchUnits.map((unit) => ({
        id: unit.id,
        name: unit.name,
        researcherCount: line.researchers.length,
        publicationCount: unit.publicationCount
      })),
      representativePublications: publications,
      publications,
      relatedLines: [],
      evidence: this.buildEvidenceFromReferences(publications, topics),
      trend
    };
  }

  private toBackendPublicationReference(
    publication: StrategicResearchMapPublicationReference
  ): StrategicResearchPublicationReference {
    return {
      id: publication.id,
      title: publication.title,
      year: publication.year,
      source: publication.source,
      topics: publication.topics.slice(0, 4)
    };
  }

  private buildEvidenceFromReferences(
    publications: StrategicResearchPublicationReference[],
    lineTopics: StrategicResearchTopic[]
  ): StrategicResearchEvidenceItem[] {
    const focusTopics = lineTopics.slice(0, 3).map((topic) => topic.label).join(', ');
    return publications.slice(0, 4).map((publication) => ({
      publicationId: publication.id,
      title: publication.title,
      note: `${publication.year ?? 's. f.'} · ${publication.source ?? 'Repositorio institucional'} · Temas: ${focusTopics || 'sin clasificar'}`
    }));
  }

  private toTrend(summary: string): StrategicResearchTrend {
    const normalized = this.normalizeLabel(summary);
    if (normalized.includes('sin anos') || normalized.includes('no estimable')) {
      return {
        label: 'Base temporal limitada',
        detail: summary,
        direction: 'limited',
        available: false
      };
    }
    if (normalized.includes('creciente')) {
      return {
        label: 'En crecimiento',
        detail: summary,
        direction: 'up',
        available: true
      };
    }
    if (normalized.includes('descendente')) {
      return {
        label: 'Menor presencia',
        detail: summary,
        direction: 'down',
        available: true
      };
    }
    return {
      label: 'Estable',
      detail: summary,
      direction: 'stable',
      available: true
    };
  }

  private toConfidence(confidence: number | null): { label: string; tone: 'strong' | 'medium' | 'low' } {
    if (confidence !== null && confidence >= 0.78) {
      return { label: 'Confianza alta', tone: 'strong' };
    }
    if (confidence !== null && confidence >= 0.55) {
      return { label: 'Confianza media', tone: 'medium' };
    }
    return { label: 'Confianza baja', tone: 'low' };
  }

  private buildTopicAggregates(publications: PublicationSummary[]): TopicAggregate[] {
    const topicToPublicationIds = new Map<string, Set<number>>();
    const topicVariants = new Map<string, Map<string, number>>();

    for (const publication of publications) {
      const normalizedTopics = new Set<string>();
      for (const topic of publication.topics) {
        const normalized = this.normalizeLabel(topic);
        if (!normalized) {
          continue;
        }
        normalizedTopics.add(normalized);
        if (!topicToPublicationIds.has(normalized)) {
          topicToPublicationIds.set(normalized, new Set<number>());
        }
        topicToPublicationIds.get(normalized)?.add(publication.id);

        if (!topicVariants.has(normalized)) {
          topicVariants.set(normalized, new Map<string, number>());
        }
        const variants = topicVariants.get(normalized);
        variants?.set(topic.trim(), (variants.get(topic.trim()) ?? 0) + 1);
      }
    }

    return [...topicToPublicationIds.entries()]
      .map(([normalizedLabel, publicationIds]) => ({
        normalizedLabel,
        displayLabel: this.bestDisplayLabel(topicVariants.get(normalizedLabel)),
        publicationIds
      }))
      .sort((left, right) => {
        const countDiff = right.publicationIds.size - left.publicationIds.size;
        return countDiff !== 0 ? countDiff : left.displayLabel.localeCompare(right.displayLabel, 'es');
      });
  }

  private selectAnchorTopics(topicAggregates: TopicAggregate[]): TopicAggregate[] {
    const selected: TopicAggregate[] = [];

    for (const candidate of topicAggregates) {
      if (candidate.publicationIds.size === 0) {
        continue;
      }

      const duplicatesExistingLine = selected.some((selectedTopic) => {
        const overlapRatio = this.overlapRatio(candidate.publicationIds, selectedTopic.publicationIds);
        return overlapRatio >= 0.72;
      });

      if (duplicatesExistingLine) {
        continue;
      }

      selected.push(candidate);

      if (selected.length >= 8) {
        break;
      }
    }

    return selected;
  }

  private collectLineTopics(publications: PublicationSummary[], anchorNormalizedLabel: string): StrategicResearchTopic[] {
    const counts = new Map<string, number>();
    const variants = new Map<string, Map<string, number>>();

    for (const publication of publications) {
      for (const topic of publication.topics) {
        const normalized = this.normalizeLabel(topic);
        if (!normalized) {
          continue;
        }
        counts.set(normalized, (counts.get(normalized) ?? 0) + 1);
        if (!variants.has(normalized)) {
          variants.set(normalized, new Map<string, number>());
        }
        const labels = variants.get(normalized);
        labels?.set(topic.trim(), (labels.get(topic.trim()) ?? 0) + 1);
      }
    }

    return [...counts.entries()]
      .map(([normalizedLabel, count]) => ({
        normalizedLabel,
        label: this.bestDisplayLabel(variants.get(normalizedLabel)),
        count
      }))
      .sort((left, right) => {
        if (left.normalizedLabel === anchorNormalizedLabel) {
          return -1;
        }
        if (right.normalizedLabel === anchorNormalizedLabel) {
          return 1;
        }
        return right.count - left.count || left.label.localeCompare(right.label, 'es');
      })
      .slice(0, 5)
      .map(({ label, count }) => ({ label, count }));
  }

  private collectResearchersForLine(
    publications: PublicationSummary[],
    researchers: Researcher[],
    lineTopics: StrategicResearchTopic[]
  ): StrategicResearchResearcher[] {
    const publicationIds = new Set(publications.map((publication) => publication.id));
    const topicNames = new Set(lineTopics.map((topic) => this.normalizeLabel(topic.label)));

    return researchers
      .map((researcher) => {
        const authoredPublications = researcher.authoredPublications.filter((publication) => publicationIds.has(publication.id));
        const topicMatchCount = researcher.topics.filter((topic) => topicNames.has(this.normalizeLabel(topic.normalizedName || topic.name))).length;
        if (authoredPublications.length === 0 && topicMatchCount === 0) {
          return null;
        }
        return {
          id: researcher.id,
          name: researcher.displayName || researcher.fullName,
          primaryAffiliation: researcher.primaryAffiliation?.researchUnitName ?? null,
          publicationCount: authoredPublications.length,
          topicMatchCount
        } satisfies StrategicResearchResearcher;
      })
      .filter((researcher): researcher is StrategicResearchResearcher => researcher !== null)
      .sort((left, right) => {
        const publicationDiff = right.publicationCount - left.publicationCount;
        if (publicationDiff !== 0) {
          return publicationDiff;
        }
        const topicDiff = right.topicMatchCount - left.topicMatchCount;
        return topicDiff !== 0 ? topicDiff : left.name.localeCompare(right.name, 'es');
      })
      .slice(0, 6);
  }

  private collectUnitsForLine(
    lineResearchers: StrategicResearchResearcher[],
    researchers: Researcher[],
    units: ResearchUnit[]
  ): StrategicResearchUnitSummary[] {
    const researchersById = new Map(researchers.map((researcher) => [researcher.id, researcher]));
    const unitsById = new Map(units.map((unit) => [unit.id, unit]));
    const unitSummaries = new Map<string, StrategicResearchUnitSummary>();

    for (const lineResearcher of lineResearchers) {
      const researcher = researchersById.get(lineResearcher.id);
      if (!researcher) {
        continue;
      }

      const affiliations = researcher.currentAffiliations.length > 0
        ? researcher.currentAffiliations
        : researcher.primaryAffiliation
          ? [researcher.primaryAffiliation]
          : [];

      const uniqueAffiliations = new Map<string, ResearcherAffiliation>();
      affiliations.forEach((affiliation) => {
        uniqueAffiliations.set(this.unitKey(affiliation.researchUnitId, affiliation.researchUnitName ?? 'Sin unidad'), affiliation);
      });

      uniqueAffiliations.forEach((affiliation) => {
        const unitId = affiliation.researchUnitId;
        const unitName = affiliation.researchUnitName ?? unitsById.get(unitId)?.name ?? 'Unidad no especificada';
        const key = this.unitKey(unitId, unitName);
        const existing = unitSummaries.get(key);
        if (existing) {
          existing.researcherCount += 1;
          existing.publicationCount += lineResearcher.publicationCount;
          return;
        }
        unitSummaries.set(key, {
          id: unitId,
          name: unitName,
          researcherCount: 1,
          publicationCount: lineResearcher.publicationCount
        });
      });
    }

    return [...unitSummaries.values()]
      .sort((left, right) => right.publicationCount - left.publicationCount || left.name.localeCompare(right.name, 'es'))
      .slice(0, 5);
  }

  private buildRelatedLines(lines: StrategicResearchLine[]): Map<string, StrategicResearchRelatedLine[]> {
    const related = new Map<string, StrategicResearchRelatedLine[]>();

    for (const line of lines) {
      const lineTopics = new Set(line.topics.map((topic) => this.normalizeLabel(topic.label)));
      const linePublicationIds = new Set(line.publications.map((publication) => publication.id));
      const relatedLines = lines
        .filter((candidate) => candidate.id !== line.id)
        .map((candidate) => {
          const candidateTopics = new Set(candidate.topics.map((topic) => this.normalizeLabel(topic.label)));
          const candidatePublicationIds = new Set(candidate.publications.map((publication) => publication.id));
          const sharedTopics = [...lineTopics].filter((topic) => candidateTopics.has(topic)).length;
          const sharedPublications = [...linePublicationIds].filter((publicationId) => candidatePublicationIds.has(publicationId)).length;
          if (sharedTopics === 0 && sharedPublications === 0) {
            return null;
          }
          return {
            id: candidate.id,
            title: candidate.title,
            sharedTopics,
            sharedPublications
          } satisfies StrategicResearchRelatedLine;
        })
        .filter((candidate): candidate is StrategicResearchRelatedLine => candidate !== null)
        .sort((left, right) => {
          const publicationDiff = right.sharedPublications - left.sharedPublications;
          return publicationDiff !== 0 ? publicationDiff : right.sharedTopics - left.sharedTopics;
        })
        .slice(0, 4);

      related.set(line.id, relatedLines);
    }

    return related;
  }

  private buildEvidence(publications: PublicationSummary[], lineTopics: StrategicResearchTopic[]): StrategicResearchEvidenceItem[] {
    const focusTopics = lineTopics.slice(0, 3).map((topic) => topic.label).join(', ');
    return publications
      .slice(0, 4)
      .map((publication) => ({
        publicationId: publication.id,
        title: publication.title,
        note: `${publication.year ?? 's. f.'} · ${publication.source ?? 'Repositorio institucional'} · Temas: ${focusTopics || 'sin clasificar'}`
      }));
  }

  private buildDescription(
    anchorLabel: string,
    lineTopics: StrategicResearchTopic[],
    units: StrategicResearchUnitSummary[],
    publications: PublicationSummary[]
  ): string {
    const relatedTopics = lineTopics
      .map((topic) => topic.label)
      .filter((topic) => this.normalizeLabel(topic) !== this.normalizeLabel(anchorLabel))
      .slice(0, 2);
    const unitNames = units.slice(0, 2).map((unit) => unit.name);
    const years = publications.map((publication) => publication.year).filter((year): year is number => year !== null);
    const latestYear = years.length > 0 ? Math.max(...years) : null;

    const topicText = relatedTopics.length > 0 ? `, conectada con ${relatedTopics.join(' y ')}` : '';
    const unitText = unitNames.length > 0 ? ` y actividad visible en ${unitNames.join(' y ')}` : '';
    const yearText = latestYear ? ` con evidencia reciente hasta ${latestYear}` : '';
    return `Línea derivada de publicaciones validadas sobre ${anchorLabel}${topicText}${unitText}${yearText}.`;
  }

  private buildTrend(publications: PublicationSummary[]): StrategicResearchTrend {
    const years = publications
      .map((publication) => publication.year)
      .filter((year): year is number => year !== null)
      .sort((left, right) => left - right);

    if (years.length < 3) {
      return {
        label: 'Base temporal limitada',
        detail: 'Todavía no hay suficiente recorrido temporal para inferir una tendencia sólida.',
        direction: 'limited',
        available: false
      };
    }

    const maxYear = years[years.length - 1];
    const recentWindowStart = maxYear - 2;
    const previousWindowStart = maxYear - 5;
    const previousWindowEnd = maxYear - 3;
    const recentCount = years.filter((year) => year >= recentWindowStart && year <= maxYear).length;
    const previousCount = years.filter((year) => year >= previousWindowStart && year <= previousWindowEnd).length;

    if (previousCount === 0 && recentCount > 0) {
      return {
        label: 'En crecimiento',
        detail: `${recentCount} publicaciones entre ${recentWindowStart} y ${maxYear}; no hay base comparable en el trienio anterior.`,
        direction: 'up',
        available: true
      };
    }

    if (recentCount >= previousCount + 2) {
      return {
        label: 'En crecimiento',
        detail: `${recentCount} publicaciones entre ${recentWindowStart} y ${maxYear} frente a ${previousCount} entre ${previousWindowStart} y ${previousWindowEnd}.`,
        direction: 'up',
        available: true
      };
    }

    if (recentCount + 1 < previousCount) {
      return {
        label: 'Menor presencia',
        detail: `${recentCount} publicaciones entre ${recentWindowStart} y ${maxYear} frente a ${previousCount} en el trienio anterior.`,
        direction: 'down',
        available: true
      };
    }

    return {
      label: 'Estable',
      detail: `${recentCount} publicaciones entre ${recentWindowStart} y ${maxYear}; el volumen se mantiene parecido al trienio anterior.`,
      direction: 'stable',
      available: true
    };
  }

  private buildConfidence(
    publicationCount: number,
    researcherCount: number,
    unitCount: number,
    topicCount: number,
    trendAvailable: boolean
  ): { score: number; label: string; tone: 'strong' | 'medium' | 'low' } {
    const score = Math.min(publicationCount / 6, 1) * 0.45
      + Math.min(researcherCount / 4, 1) * 0.25
      + Math.min(unitCount / 3, 1) * 0.15
      + Math.min(topicCount / 4, 1) * 0.1
      + (trendAvailable ? 0.05 : 0);

    if (score >= 0.78) {
      return { score, label: 'Confianza alta', tone: 'strong' };
    }
    if (score >= 0.55) {
      return { score, label: 'Confianza media', tone: 'medium' };
    }
    return { score, label: 'Confianza baja', tone: 'low' };
  }

  private buildWarnings(
    confidenceScore: number,
    publicationCount: number,
    researcherCount: number,
    trendAvailable: boolean
  ): string[] {
    const warnings: string[] = [];
    if (confidenceScore < 0.55) {
      warnings.push('La línea se apoya en una base visible limitada y puede cambiar al incorporarse nuevas validaciones.');
    }
    if (publicationCount < 3) {
      warnings.push('La producción visible todavía es reducida para consolidar esta línea.');
    }
    if (researcherCount < 2) {
      warnings.push('La línea muestra poca diversidad de investigadores visibles.');
    }
    if (!trendAvailable) {
      warnings.push('La tendencia aún no es concluyente por falta de suficiente serie temporal.');
    }
    return warnings;
  }

  private toPublicationReference(publication: PublicationSummary): StrategicResearchPublicationReference {
    return {
      id: publication.id,
      title: publication.title,
      year: publication.year,
      source: publication.source,
      topics: publication.topics.slice(0, 4)
    };
  }

  private loadAllResearchers(): Observable<Researcher[]> {
    return this.loadAllPages((page) => this.researchersApi.search({ page, size: 100 })).pipe(
      switchMap((summaries) => {
        if (summaries.length === 0) {
          return of([] as Researcher[]);
        }
        return forkJoin(
          summaries.map((summary) =>
            this.researchersApi.get(summary.id).pipe(catchError(() => of(null)))
          )
        ).pipe(
          map((researchers) => researchers.filter((researcher): researcher is Researcher => researcher !== null))
        );
      })
    );
  }

  private loadAllPages<T>(loader: (page: number) => Observable<PageResponse<T>>): Observable<T[]> {
    return loader(0).pipe(
      switchMap((firstPage) => {
        if (firstPage.totalPages <= 1) {
          return of(firstPage.content);
        }

        const remainingPages = Array.from(
          { length: Math.max(firstPage.totalPages - 1, 0) },
          (_, index) => loader(index + 1)
        );

        return forkJoin(remainingPages).pipe(
          map((pages) => [firstPage, ...pages].flatMap((page) => page.content))
        );
      })
    );
  }

  private bestDisplayLabel(variants: Map<string, number> | undefined): string {
    if (!variants || variants.size === 0) {
      return 'Sin tema';
    }

    return [...variants.entries()]
      .map(([displayLabel, count]) => ({ displayLabel, count }) satisfies TopicVariantAggregate)
      .sort((left, right) => right.count - left.count || left.displayLabel.localeCompare(right.displayLabel, 'es'))[0]
      .displayLabel;
  }

  private normalizeLabel(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
  }

  private overlapRatio(left: Set<number>, right: Set<number>): number {
    const intersection = [...left].filter((value) => right.has(value)).length;
    return intersection / Math.min(left.size, right.size);
  }

  private comparePublications(left: PublicationSummary, right: PublicationSummary): number {
    const leftYear = left.year ?? -1;
    const rightYear = right.year ?? -1;
    return rightYear - leftYear || left.title.localeCompare(right.title, 'es');
  }

  private unitKey(id: number | null, name: string): string {
    return `${id ?? 'none'}::${name}`;
  }

  private titleCase(value: string): string {
    if (!value) {
      return value;
    }
    return value.charAt(0).toUpperCase() + value.slice(1);
  }
}
