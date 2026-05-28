import { PublicationFilterMetadata, PublicationSummary, ResearchUnit, Researcher } from '../../core/api/api-models';

export interface StrategicResearchMapPageContext {
  metadata: PublicationFilterMetadata;
  units: ResearchUnit[];
}

export interface StrategicResearchMapContext {
  metadata: PublicationFilterMetadata;
  units: ResearchUnit[];
  researchers: Researcher[];
}

export interface StrategicResearchMapFilters {
  yearFrom: number | null;
  yearTo: number | null;
  researchUnitId: number | null;
  onlyValidated: boolean;
}

export interface StrategicResearchMapData {
  overview: StrategicResearchMapOverview;
  lines: StrategicResearchLine[];
}

export interface StrategicResearchMapOverview {
  lineCount: number;
  publicationCount: number;
  researcherCount: number;
  unitCount: number;
}

export interface StrategicResearchLine {
  id: string;
  title: string;
  description: string;
  publicationCount: number;
  confidenceScore: number;
  confidenceLabel: string;
  confidenceTone: 'strong' | 'medium' | 'low';
  warnings: string[];
  topics: StrategicResearchTopic[];
  researchers: StrategicResearchResearcher[];
  units: StrategicResearchUnitSummary[];
  representativePublications: StrategicResearchPublicationReference[];
  publications: StrategicResearchPublicationReference[];
  relatedLines: StrategicResearchRelatedLine[];
  evidence: StrategicResearchEvidenceItem[];
  trend: StrategicResearchTrend;
}

export interface StrategicResearchTrend {
  label: string;
  detail: string;
  direction: 'up' | 'stable' | 'down' | 'limited';
  available: boolean;
}

export interface StrategicResearchTopic {
  label: string;
  count: number;
}

export interface StrategicResearchResearcher {
  id: number;
  name: string;
  primaryAffiliation: string | null;
  publicationCount: number;
  topicMatchCount: number;
}

export interface StrategicResearchUnitSummary {
  id: number | null;
  name: string;
  researcherCount: number;
  publicationCount: number;
}

export interface StrategicResearchPublicationReference {
  id: number;
  title: string;
  year: number | null;
  source: string | null;
  topics: string[];
}

export interface StrategicResearchRelatedLine {
  id: string;
  title: string;
  sharedTopics: number;
  sharedPublications: number;
}

export interface StrategicResearchEvidenceItem {
  publicationId: number;
  title: string;
  note: string;
}
