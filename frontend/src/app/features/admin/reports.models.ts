import { CopilotCitation, PublicationFilterMetadata, ResearchUnit, Researcher } from '../../core/api/api-models';
import { StrategicResearchLine } from '../strategic-map/strategic-research-map.models';

export type ReportType = 'RESEARCH_UNIT' | 'RESEARCHER' | 'TOPIC' | 'STRATEGIC_LINE';

export type ReportSectionKey =
  | 'EXECUTIVE_SUMMARY'
  | 'PUBLICATION_OVERVIEW'
  | 'YEARLY_EVOLUTION'
  | 'TOP_TOPICS'
  | 'LINKED_RESEARCHERS'
  | 'LINKED_UNITS'
  | 'REPRESENTATIVE_PUBLICATIONS'
  | 'COLLABORATIONS'
  | 'DATA_QUALITY'
  | 'VALIDATION_STATUS'
  | 'OPPORTUNITIES'
  | 'LIMITATIONS'
  | 'CITED_EVIDENCE'
  | 'SUMMARY'
  | 'PROFILE'
  | 'OUTPUT'
  | 'TOPICS'
  | 'RESEARCHERS'
  | 'UNITS'
  | 'COLLABORATION'
  | 'TREND'
  | 'RELATED'
  | 'EVIDENCE';

export type ReportOutputFormat = 'MARKDOWN' | 'HTML_PREVIEW';

export interface ReportTypeOption {
  value: ReportType;
  label: string;
  description: string;
}

export interface ReportSectionOption {
  key: ReportSectionKey;
  label: string;
  description: string;
}

export interface ReportTargetOption {
  id: string;
  type: ReportType;
  label: string;
  helper: string;
  keywords: string[];
  count: number | null;
  payload: {
    id?: number;
    topic?: string;
    lineId?: string;
  };
}

export interface ReportsContext {
  metadata: PublicationFilterMetadata;
  units: ResearchUnit[];
  researchers: Researcher[];
}

export interface ReportGenerationRequest {
  type: ReportType;
  templateId: number | null;
  target: ReportTargetOption;
  yearFrom: number | null;
  yearTo: number | null;
  sections: ReportSectionKey[];
  additionalInstructions: string;
}

export interface ReportTemplate {
  id: number;
  name: string;
  description: string | null;
  targetType: ReportType;
  sections: ReportSectionKey[];
  defaultYearFrom: number | null;
  defaultYearTo: number | null;
  outputFormat: ReportOutputFormat;
  active: boolean;
  createdAt: string | null;
  updatedAt: string | null;
  createdByUserId: number | null;
  updatedByUserId: number | null;
}

export interface ReportTemplateRequest {
  name: string;
  description: string | null;
  targetType: ReportType;
  sections: ReportSectionKey[];
  defaultYearFrom: number | null;
  defaultYearTo: number | null;
  outputFormat: ReportOutputFormat;
  active: boolean;
}

export interface BackendReportGenerationRequest {
  reportType: ReportType;
  templateId: number | null;
  targetId: number | null;
  query: string | null;
  yearFrom: number | null;
  yearTo: number | null;
  includeSections: ReportSectionKey[];
  onlyValidated: boolean;
  additionalInstructions: string | null;
}

export interface BackendGeneratedReport {
  reportTitle: string;
  markdownContent: string;
  citedPublications: CopilotCitation[];
  warnings: string[];
  generatedAt: string;
  provider: string;
  model: string;
}

export interface ReportSummaryMetric {
  label: string;
  value: string;
}

export interface GeneratedReport {
  title: string;
  subtitle: string;
  generatedAt: string;
  yearFrom: number | null;
  yearTo: number | null;
  type: ReportType;
  target: ReportTargetOption;
  sections: ReportSectionKey[];
  sectionOptions: ReportSectionOption[];
  template: ReportTemplate | null;
  summaryMetrics: ReportSummaryMetric[];
  warnings: string[];
  markdown: string;
  exportMarkdown: string;
  citations: CopilotCitation[];
}

export interface StrategicLineTargetSnapshot {
  target: ReportTargetOption;
  line: StrategicResearchLine;
}
