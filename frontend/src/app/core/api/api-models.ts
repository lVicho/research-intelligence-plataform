export type ResearchUnitType =
  | 'UNIVERSITY'
  | 'FACULTY'
  | 'SCHOOL'
  | 'DEPARTMENT'
  | 'INSTITUTE'
  | 'RESEARCH_GROUP'
  | 'LAB'
  | 'CENTER'
  | 'HOSPITAL'
  | 'COMPANY'
  | 'FOUNDATION'
  | 'GOVERNMENT_AGENCY'
  | 'OTHER';

export type OrganizationScope = 'INTERNAL' | 'EXTERNAL';

export type AffiliationType =
  | 'MEMBER'
  | 'LEADER'
  | 'VISITING'
  | 'COLLABORATOR'
  | 'FORMER_MEMBER'
  | 'OTHER';

export type PublicationType =
  | 'ARTICLE'
  | 'BOOK'
  | 'BOOK_CHAPTER'
  | 'CONFERENCE_PAPER'
  | 'THESIS'
  | 'REPORT'
  | 'DATASET'
  | 'SOFTWARE'
  | 'OTHER';

export type PublicationStatus =
  | 'PUBLISHED'
  | 'ACCEPTED'
  | 'IN_PRESS'
  | 'DRAFT'
  | 'UNKNOWN';

export type NewsArticleStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'ARCHIVED';

export type NewsDraftSourceType =
  | 'PUBLICATION'
  | 'RESEARCH_UNIT'
  | 'RESEARCHER'
  | 'TOPIC'
  | 'CUSTOM_QUERY';

export type NewsDraftTone = 'INSTITUTIONAL' | 'OUTREACH';

export type RetrievalMode = 'STRICT' | 'BALANCED' | 'BROAD';
export type CollaborationOpportunityMode = 'STRICT' | 'BALANCED' | 'BROAD';
export type PublicSummaryStyle = 'SHORT' | 'STANDARD' | 'EXTENDED';
export type PublicSummaryTargetType = 'RESEARCHER' | 'RESEARCH_UNIT' | 'PUBLICATION' | 'EXTERNAL_ORGANIZATION';

export type RoleCode = 'PUBLIC_USER' | 'RESEARCHER' | 'ADMIN' | 'VALIDATOR';
export type VisibilityScopeCode = 'PUBLIC_VALIDATED' | 'MY_DATA' | 'ADMIN_ALL';

export type ValidationStatus = 'DRAFT' | 'PENDING_VALIDATION' | 'VALIDATED' | 'REJECTED' | 'CHANGES_REQUESTED';

export type ValidationEntityType =
  | 'RESEARCH_UNIT'
  | 'RESEARCHER'
  | 'RESEARCHER_AFFILIATION'
  | 'PUBLICATION'
  | 'EVENT_PARTICIPATION'
  | 'SCIENTIFIC_EVENT'
  | 'VENUE'
  | 'PUBLISHER'
  | 'TOPIC';

export type ActivityAuditAction =
  | 'CREATED'
  | 'UPDATED'
  | 'SUBMITTED'
  | 'VALIDATED'
  | 'REJECTED'
  | 'CHANGES_REQUESTED'
  | 'ARCHIVED'
  | 'DELETED'
  | 'RESTORED';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthUser {
  id: number;
  email: string;
  displayName: string;
  roles: RoleCode[];
  researcherId: number | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface PortalPageResponse<T> extends PageResponse<T> {
  visibilityScope: VisibilityScopeCode;
  validationFilterApplied: boolean;
}

export interface PortalCount {
  id: number | null;
  name: string;
  count: number;
}

export type PortalDemoQueryContext =
  | 'PUBLICATIONS'
  | 'EXPERT_FINDER'
  | 'ASSISTANT'
  | 'REPORTS'
  | 'STRATEGIC_MAP'
  | 'GENERAL';

export interface PortalDemoQueryFreshness {
  newestEvidenceAt: string;
  oldestEvidenceAt: string;
  evidenceCount: number;
}

export interface PortalDemoQuery {
  query: string;
  context: PortalDemoQueryContext;
  reason: string;
  expectedEntityTypes: string[];
  evidenceIds: string[];
  freshness: PortalDemoQueryFreshness;
  generatedAt: string;
}

export interface PortalCollaborationSummary {
  researcherCount: number;
  publicationCount: number;
  activityCount: number;
}

export interface PortalActivitySummary {
  id: number;
  eventId: number | null;
  eventName: string | null;
  researcherId: number | null;
  researcherName: string | null;
  researchUnitId: number | null;
  researchUnitName: string | null;
  participationTypeCode: string;
  title: string;
  description: string | null;
  participationDate: string | null;
  relatedPublicationId: number | null;
  relatedPublicationTitle: string | null;
}

export interface PortalResearchUnitSummary {
  id: number;
  name: string;
  shortName: string | null;
  type: ResearchUnitType;
  parentId: number | null;
  country: string | null;
  city: string | null;
  website: string | null;
  active: boolean;
}

export interface PortalResearchUnitDetail {
  unit: PortalResearchUnitSummary;
  researchers: PortalResearcherSummary[];
  publications: PublicationSummary[];
  activities: PortalActivitySummary[];
  topics: PortalCount[];
  childUnits: PortalResearchUnitSummary[];
  collaborationSummary: PortalCollaborationSummary;
  visibilityScope: VisibilityScopeCode;
  validationFilterApplied: boolean;
}

export interface PortalResearcherSummary {
  id: number;
  fullName: string;
  displayName: string | null;
  email: string | null;
  orcid: string | null;
  active: boolean;
  primaryAffiliationName: string | null;
}

export interface PortalAffiliation {
  id: number;
  researcherId: number;
  researchUnitId: number;
  researchUnitName: string | null;
  role: string | null;
  affiliationType: AffiliationType;
  startDate: string | null;
  endDate: string | null;
  primaryAffiliation: boolean;
  current: boolean;
}

export interface PortalCoauthor {
  researcherId: number | null;
  name: string;
  internal: boolean;
  sharedPublicationCount: number;
}

export interface PortalGraphSummary {
  totalNodes: number;
  totalEdges: number;
  displayedNodes: number;
  displayedEdges: number;
  truncated: boolean;
  visibilityScope: VisibilityScopeCode;
  validationFilterApplied: boolean;
}

export interface PortalResearcherDetail {
  id: number;
  fullName: string;
  displayName: string | null;
  email: string | null;
  orcid: string | null;
  active: boolean;
  affiliations: PortalAffiliation[];
  publications: PublicationSummary[];
  activities: PortalActivitySummary[];
  topics: PortalCount[];
  coauthors: PortalCoauthor[];
  graphSummary: PortalGraphSummary;
  visibilityScope: VisibilityScopeCode;
  validationFilterApplied: boolean;
}

export interface PortalSummary {
  totalValidatedPublications: number;
  totalValidatedActivities: number;
  totalPublicResearchers: number;
  totalPublicResearchUnits: number;
  topTopics: PortalCount[];
  recentValidatedPublications: PublicationSummary[];
  featuredResearchUnits: PortalResearchUnitSummary[];
  visibilityScope: VisibilityScopeCode;
  validationFilterApplied: boolean;
}

export interface PortalNewsArticleSummary {
  id: number;
  title: string;
  summary: string;
  imageUrl: string | null;
  imageAlt: string | null;
  publishedAt: string | null;
  relatedPublicationIds: number[];
  relatedResearcherIds: number[];
  relatedUnitIds: number[];
}

export interface PortalNewsArticle {
  id: number;
  title: string;
  summary: string;
  body: string;
  imageUrl: string | null;
  imageAlt: string | null;
  publishedAt: string | null;
  relatedPublicationIds: number[];
  relatedResearcherIds: number[];
  relatedUnitIds: number[];
}

export interface NewsArticle {
  id: number;
  title: string;
  summary: string;
  body: string;
  status: NewsArticleStatus;
  imageUrl: string | null;
  imageAlt: string | null;
  imageSuggestion: string | null;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
  createdByUserId: number | null;
  updatedByUserId: number | null;
  relatedPublicationIds: number[];
  relatedResearcherIds: number[];
  relatedUnitIds: number[];
}

export interface NewsArticleRequest {
  title: string;
  summary: string;
  body: string;
  status: NewsArticleStatus | null;
  imageUrl: string | null;
  imageAlt: string | null;
  imageSuggestion: string | null;
  relatedPublicationIds: number[];
  relatedResearcherIds: number[];
  relatedUnitIds: number[];
}

export interface NewsDraftRelatedIdsRequest {
  publicationIds: number[];
  researcherIds: number[];
  unitIds: number[];
}

export interface NewsDraftGenerateRequest {
  sourceType: NewsDraftSourceType;
  sourceId: number | null;
  query: string | null;
  tone: NewsDraftTone;
  relatedIds: NewsDraftRelatedIdsRequest;
}

export interface NewsDraftEvidence {
  reference: string;
  entityType: string;
  entityId: number | null;
  label: string;
  value: string | null;
}

export interface NewsDraftGenerateResponse {
  suggestedTitle: string;
  suggestedSummary: string;
  suggestedBody: string;
  imageSuggestion: string;
  imageAltSuggestion: string;
  evidence: NewsDraftEvidence[];
  createdSuggestionId: number;
  createdSuggestionType: AiSuggestionType;
}

export interface ValidationItem {
  entityType: ValidationEntityType;
  entityId: number;
  title: string;
  subtitle: string | null;
  researcherId: number | null;
  researcherName: string | null;
  researchUnitId: number | null;
  researchUnitName: string | null;
  submittedBy: string;
  submittedAt: string;
  validationStatus: ValidationStatus;
  summaryFields: Record<string, string>;
  warnings: string[];
  dataQualityFlags: string[];
}

export interface ValidationItemDetail {
  item: ValidationItem;
  fields: Record<string, string>;
  validationComment: string | null;
  validatedBy: string | null;
  validatedAt: string | null;
  warnings: string[];
  dataQualityFlags: string[];
}

export interface ValidationCommentRequest {
  comment: string | null;
}

export interface ActivityAuditEvent {
  id: number;
  entityType: ValidationEntityType;
  entityId: number;
  action: ActivityAuditAction;
  actorUserId: number | null;
  actorDisplayName: string | null;
  actorRole: string | null;
  occurredAt: string;
  previousStatus: ValidationStatus | null;
  newStatus: ValidationStatus | null;
  comment: string | null;
  changesJson: string | null;
}

export interface MeResearcherProfile {
  id: number;
  fullName: string;
  displayName: string | null;
  email: string | null;
  orcid: string | null;
  active: boolean;
  primaryAffiliationName: string | null;
}

export interface MePublicationYearCount {
  year: number | null;
  count: number;
}

export interface MeTopicCount {
  id: number;
  name: string;
  count: number;
}

export interface MeActivity {
  entityType: ValidationEntityType;
  entityId: number;
  title: string;
  subtitle: string | null;
  researcherId: number | null;
  researcherName: string | null;
  researchUnitId: number | null;
  researchUnitName: string | null;
  submittedAt: string;
  validationStatus: ValidationStatus;
  validationComment: string | null;
  summaryFields: Record<string, string>;
  dataQualityReminders: string[];
  editable: boolean;
  submittable: boolean;
}

export interface MeActivityDetail {
  entityType: ValidationEntityType;
  entityId: number;
  title: string;
  subtitle: string | null;
  researcherId: number | null;
  researcherName: string | null;
  researchUnitId: number | null;
  researchUnitName: string | null;
  submittedAt: string;
  validationStatus: ValidationStatus;
  validationComment: string | null;
  validatedBy: string | null;
  validatedAt: string | null;
  fields: Record<string, string>;
  warnings: string[];
  dataQualityReminders: string[];
  editable: boolean;
  submittable: boolean;
}

export interface MeDashboard {
  profile: MeResearcherProfile;
  validatedActivitiesCount: number;
  draftActivitiesCount: number;
  pendingValidationCount: number;
  changesRequestedCount: number;
  rejectedCount: number;
  publicationsByYear: MePublicationYearCount[];
  mainTopics: MeTopicCount[];
  recentActivities: MeActivity[];
  dataQualityReminders: string[];
}

export interface AnalyticsSummary {
  totalResearchUnits: number;
  totalResearchers: number;
  activeResearchers: number;
  totalPublications: number;
  publicationsByYear: YearCount[];
  publicationsByType: NamedCount[];
  publicationsByStatus: NamedCount[];
  publicationsByResearchUnit: NamedCount[];
  topResearchersByPublicationCount: ResearcherPublicationCount[];
  topTopicsByPublicationCount: NamedCount[];
  recentPublications: PublicationSummary[];
  researchersByResearchUnitType: NamedCount[];
}

export interface CollaborationOpportunityUnit {
  id: number;
  name: string;
  shortName: string | null;
  type: ResearchUnitType;
}

export interface CollaborationOpportunityPublication {
  id: number;
  title: string;
  year: number | null;
  source: string | null;
  path: string;
}

export interface CollaborationOpportunity {
  id: string;
  unitA: CollaborationOpportunityUnit;
  unitB: CollaborationOpportunityUnit;
  score: number;
  confidence: number;
  sharedTopics: string[];
  complementaryTopics: string[];
  representativePublications: CollaborationOpportunityPublication[];
  existingCollaborationCount: number;
  explanation: string;
  fromYear: number;
  toYear: number;
}

export interface CollaborationOpportunityQuery {
  fromYear: number | null;
  toYear: number | null;
  mode: CollaborationOpportunityMode;
  limit: number;
}

export interface CollaborationOpportunityResponse {
  generatedAt: string;
  visibilityScope: string;
  minYear: number;
  maxYear: number;
  total: number;
  opportunities: CollaborationOpportunity[];
}

export type DataQualitySeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type DataQualityEntityType =
  | 'PUBLICATION'
  | 'RESEARCHER'
  | 'TOPIC'
  | 'EVENT_PARTICIPATION'
  | 'RESEARCH_UNIT'
  | 'VENUE'
  | 'EVENT'
  | 'EXTERNAL_AUTHOR';

export interface DataQualityAffectedRecord {
  label: string;
  path: string;
  helper: string | null;
}

export interface DataQualityIssue {
  id: string;
  label: string;
  description: string;
  severity: DataQualitySeverity;
  entityType: DataQualityEntityType;
  count: number;
  affectedRecords: DataQualityAffectedRecord[];
  updatedAt: string;
}

export interface DataQualitySummary {
  totalOpenIssues: number;
  criticalIssues: number;
  categoriesWithFindings: number;
  affectedRecords: number;
  lastReviewAt: string;
}

export interface DataQualityOverview {
  summary: DataQualitySummary;
  issues: DataQualityIssue[];
}

export type AiSuggestionType =
  | 'PUBLIC_SUMMARY'
  | 'RESEARCHER_SUMMARY'
  | 'RESEARCHER_TOPIC'
  | 'PUBLICATION_METADATA'
  | 'PUBLICATION_TOPIC'
  | 'RESEARCH_UNIT_SUMMARY'
  | 'VALIDATION_REVIEW'
  | 'NEWS_DRAFT';

export type AiSuggestionStatus =
  | 'PENDING_REVIEW'
  | 'ACCEPTED'
  | 'ACCEPTED_WITH_EDITS'
  | 'REJECTED';

export interface AiSuggestionTarget {
  entityType: ValidationEntityType;
  entityId: number;
  title: string;
  subtitle: string | null;
  ownerResearcherId: number | null;
  ownerResearcherName: string | null;
  path: string | null;
}

export interface AiSuggestionField {
  key: string;
  label: string;
  currentValue: string | null;
  proposedValue: string | null;
  helper: string | null;
  multiline: boolean;
  editable: boolean;
}

export interface AiSuggestionEvidence {
  id: string;
  label: string;
  sourceLabel: string;
  sourceType: string;
  excerpt: string;
  helper: string | null;
  path: string | null;
}

export interface AiSuggestionProviderInfo {
  provider: string;
  model: string;
  generatedAt: string;
  promptProfile: string;
  confidenceSummary: string;
  caution: string;
}

export interface AiSuggestionFieldUpdate {
  key: string;
  proposedValue: string | null;
}

export interface AiSuggestion {
  id: string;
  title: string;
  type: AiSuggestionType;
  status: AiSuggestionStatus;
  explanation: string;
  reviewerNote: string;
  target: AiSuggestionTarget;
  proposedFields: AiSuggestionField[];
  evidence: AiSuggestionEvidence[];
  providerInfo: AiSuggestionProviderInfo;
  visibilityNote: string;
  technicalData: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface PublicSummaryEvidence {
  reference: string;
  label: string;
  value: string;
}

export interface PublicSummaryGenerateRequest {
  targetType: PublicSummaryTargetType;
  targetId: number;
  style: PublicSummaryStyle;
  audience?: 'PUBLIC' | null;
}

export interface PublicSummaryGenerateResponse {
  summary: string;
  evidence: PublicSummaryEvidence[];
  createdSuggestionId: number;
  warnings: string[];
  provider: string;
  model: string;
}

export type PersistedAiSuggestionStatus = 'GENERATED' | 'ACCEPTED' | 'REJECTED' | 'EDITED' | 'EXPIRED';

export interface PersistedAiSuggestion {
  id: number;
  targetType: string;
  targetId: number | null;
  suggestionType: string;
  status: PersistedAiSuggestionStatus;
  proposedDataJson: string;
  explanation: string;
  evidenceJson: string | null;
  modelProvider: string;
  modelName: string;
  createdAt: string;
  createdByUserId: number | null;
  reviewedAt: string | null;
  reviewedByUserId: number | null;
  reviewComment: string | null;
}

export interface TopicNormalizationPublicationLink {
  id: number;
  title: string;
  path: string;
}

export interface TopicNormalizationCandidateTopic {
  id: number;
  label: string;
  publicationCount: number;
}

export interface TopicNormalizationCandidateGroup {
  id: string;
  confidence: number;
  affectedPublicationsCount: number;
  suggestedCanonicalLabel: string;
  canonicalLabel: string;
  topicsToMerge: TopicNormalizationCandidateTopic[];
  affectedPublications: TopicNormalizationPublicationLink[];
  updatedAt: string;
}

export interface YearCount {
  year: number;
  count: number;
}

export interface NamedCount {
  id: number | null;
  name: string;
  count: number;
}

export interface ResearcherPublicationCount {
  researcherId: number;
  researcherName: string;
  publicationCount: number;
}

export interface ResearchUnit {
  id: number;
  name: string;
  shortName: string | null;
  type: ResearchUnitType;
  parentId: number | null;
  country: string | null;
  city: string | null;
  website: string | null;
  active: boolean;
  visibleInPortal: boolean;
  organizationScope: OrganizationScope;
  publicDescription: string | null;
  internalDescription: string | null;
  responsibleResearcherId: number | null;
  featured: boolean | null;
  sortOrder: number | null;
  validationStatus: ValidationStatus;
  validationComment: string | null;
  submittedAt: string | null;
  submittedBy: string | null;
  validatedAt: string | null;
  validatedBy: string | null;
  canEdit: boolean;
  canSubmit: boolean;
  canValidate: boolean;
  createdAt: string;
  updatedAt: string;
  createdByUserId?: number | null;
  updatedByUserId?: number | null;
}

export interface ResearchUnitTreeNode {
  id: number;
  name: string;
  shortName: string | null;
  type: ResearchUnitType;
  parentId: number | null;
  active: boolean;
  children: ResearchUnitTreeNode[];
}

export interface ResearchUnitRequest {
  name: string;
  shortName: string | null;
  type: ResearchUnitType;
  parentId: number | null;
  country: string | null;
  city: string | null;
  website: string | null;
  active: boolean;
  visibleInPortal: boolean;
  organizationScope: OrganizationScope;
  publicDescription: string | null;
  internalDescription: string | null;
  responsibleResearcherId: number | null;
  featured: boolean | null;
  sortOrder: number | null;
}

export interface ResearcherSummary {
  id: number;
  fullName: string;
  displayName: string | null;
  email: string | null;
  orcid: string | null;
  active: boolean;
  primaryAffiliationName: string | null;
  validationStatus: ValidationStatus;
  validationComment: string | null;
  submittedAt: string | null;
  submittedBy: string | null;
  validatedAt: string | null;
  validatedBy: string | null;
  canEdit: boolean;
  canSubmit: boolean;
  canValidate: boolean;
}

export interface Researcher {
  id: number;
  fullName: string;
  displayName: string | null;
  email: string | null;
  orcid: string | null;
  active: boolean;
  validationStatus: ValidationStatus;
  validationComment: string | null;
  submittedAt: string | null;
  submittedBy: string | null;
  validatedAt: string | null;
  validatedBy: string | null;
  canEdit: boolean;
  canSubmit: boolean;
  canValidate: boolean;
  affiliations: ResearcherAffiliation[];
  currentAffiliations: ResearcherAffiliation[];
  pastAffiliations: ResearcherAffiliation[];
  primaryAffiliation: ResearcherAffiliation | null;
  authoredPublications: PublicationSummary[];
  topics: Topic[];
  coauthors: ResearcherCoauthor[];
  createdAt: string;
  updatedAt: string;
  createdByUserId: number | null;
  updatedByUserId: number | null;
}

export interface ResearcherRequest {
  fullName: string;
  displayName: string | null;
  email: string | null;
  orcid: string | null;
  active: boolean;
}

export interface ResearcherAffiliation {
  id: number;
  researcherId: number;
  researchUnitId: number;
  researchUnitName: string | null;
  role: string | null;
  affiliationType: AffiliationType;
  startDate: string | null;
  endDate: string | null;
  primaryAffiliation: boolean;
  current: boolean;
  validationStatus: ValidationStatus;
  validationComment: string | null;
  submittedAt: string | null;
  submittedBy: string | null;
  validatedAt: string | null;
  validatedBy: string | null;
  canEdit: boolean;
  canSubmit: boolean;
  canValidate: boolean;
  createdAt: string;
  updatedAt: string;
  createdByUserId: number | null;
  updatedByUserId: number | null;
}

export interface ResearcherAffiliationRequest {
  researchUnitId: number;
  role: string | null;
  affiliationType: AffiliationType;
  startDate: string | null;
  endDate: string | null;
  primaryAffiliation: boolean;
}

export interface ResearcherCoauthor {
  researcherId: number | null;
  name: string;
  internal: boolean;
  sharedPublicationCount: number;
}

export type GraphNodeType = 'researcher' | 'research_unit' | 'publication' | 'topic' | 'external_author';

export type GraphEdgeType = 'affiliated_with' | 'authored' | 'has_topic' | 'coauthored_with' | 'belongs_to';

export type GraphDensity = 'SIMPLE' | 'NORMAL' | 'COMPLETE';

export interface ResearchGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
  metadata: GraphMetadata;
  warnings: string[];
}

export interface GraphMetadata {
  totalNodes: number;
  totalEdges: number;
  displayedNodes: number;
  displayedEdges: number;
  truncated: boolean;
}

export interface GraphNode {
  id: string;
  type: GraphNodeType;
  label: string;
  metadata: Record<string, unknown>;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  type: GraphEdgeType;
  weight: number;
  metadata: Record<string, unknown>;
}

export interface PublicationSummary {
  id: number;
  title: string;
  year: number | null;
  type: PublicationType;
  status: PublicationStatus;
  doi: string | null;
  source: string | null;
  venueId: number | null;
  publisherId: number | null;
  isbn: string | null;
  issn: string | null;
  languageCode: string | null;
  validationStatus: ValidationStatus;
  validationComment: string | null;
  submittedAt: string | null;
  submittedBy: string | null;
  validatedAt: string | null;
  validatedBy: string | null;
  canEdit: boolean;
  canSubmit: boolean;
  canValidate: boolean;
  createdAt: string;
  topics: string[];
}

export interface PublicationFilterMetadata {
  minYear: number | null;
  maxYear: number | null;
  availableTypes: FilterCount[];
  availableStatuses: FilterCount[];
  researchUnits: FilterCount[];
  topics: FilterCount[];
}

export interface FilterCount {
  id: number | null;
  value: string;
  label: string;
  count: number;
}

export interface MasterDataItem {
  id: number;
  code: string;
  labelEs: string;
  descriptionEs: string | null;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface Publication {
  id: number;
  title: string;
  abstractText: string | null;
  publicSummary: string | null;
  year: number | null;
  publicationDate: string | null;
  type: PublicationType;
  status: PublicationStatus;
  doi: string | null;
  source: string | null;
  sourceDetail: string | null;
  url: string | null;
  venueId: number | null;
  publisherId: number | null;
  isbn: string | null;
  issn: string | null;
  languageCode: string | null;
  validationStatus: ValidationStatus;
  validationComment: string | null;
  submittedAt: string | null;
  submittedBy: string | null;
  validatedAt: string | null;
  validatedBy: string | null;
  canEdit: boolean;
  canSubmit: boolean;
  canValidate: boolean;
  authors: PublicationAuthor[];
  topics: Topic[];
  createdAt: string;
  updatedAt: string;
  createdByUserId: number | null;
  updatedByUserId: number | null;
}

export interface PublicationAuthor {
  id: number;
  researcherId: number | null;
  researcherName: string | null;
  externalAuthorName: string | null;
  externalAffiliation: string | null;
  authorOrder: number;
  correspondingAuthor: boolean;
}

export interface Topic {
  id: number;
  name: string;
  normalizedName: string;
}

export interface PublicationRequest {
  title: string;
  abstractText: string | null;
  publicSummary: string | null;
  year: number | null;
  publicationDate: string | null;
  type: PublicationType;
  status: PublicationStatus;
  doi: string | null;
  source: string | null;
  sourceDetail: string | null;
  url: string | null;
  venueId: number | null;
  publisherId: number | null;
  isbn: string | null;
  issn: string | null;
  languageCode: string | null;
  authors: PublicationAuthorRequest[];
  topics: string[];
}

export interface PublicationAuthorRequest {
  researcherId: number | null;
  externalAuthorName: string | null;
  externalAffiliation: string | null;
  authorOrder: number;
  correspondingAuthor: boolean;
}

export interface PublicationCsvIngestionReport {
  totalRows: number;
  insertedPublications: number;
  updatedPublications: number;
  matchedInternalAuthors: number;
  externalAuthorsStored: number;
  createdTopics: number;
  skippedRows: number;
  rowErrors: IngestionRowError[];
}

export interface IngestionRowError {
  rowNumber: number;
  message: string;
}

export interface CopilotAskRequest {
  question: string;
  limit: number | null;
  includeNonValidated?: boolean | null;
}

export interface CopilotAskResponse {
  answerRaw: string;
  answer: string;
  retrievedPublications: CopilotRetrievedPublication[];
  citedPublications: CopilotCitation[];
  provider: string;
  model: string;
  embeddingProvider: string;
  embeddingModel: string;
  retrievalMethod: string;
  retrievalMode: RetrievalMode;
  minSimilarity: number;
  detectedTopics: CopilotSignal[];
  bridgingAuthors: CopilotSignal[];
  warnings: string[];
  visibilityScope: string;
  validationFilterApplied: boolean;
}

export interface CopilotRetrieveRequest {
  question: string;
  limit: number | null;
  minSimilarity?: number | null;
  retrievalMode?: RetrievalMode | null;
  includeNonValidated?: boolean | null;
}

export interface CopilotRetrieveResponse {
  retrievalMethod: string;
  retrievalMode: RetrievalMode;
  minSimilarity: number;
  embeddingProvider: string;
  embeddingModel: string;
  provider: string;
  model: string;
  retrievedPublications: CopilotRetrievedPublication[];
  detectedTopics: CopilotSignal[];
  bridgingAuthors: CopilotSignal[];
  warnings: string[];
  visibilityScope: string;
  validationFilterApplied: boolean;
}

export interface CopilotAnswerRequest {
  question: string;
  retrievedPublications: CopilotRetrievedPublication[];
  includeNonValidated?: boolean | null;
}

export interface CopilotAnswerResponse {
  answerRaw: string;
  answer: string;
  retrievedPublications: CopilotRetrievedPublication[];
  citedPublications: CopilotCitation[];
  provider: string;
  model: string;
  warnings: string[];
  visibilityScope: string;
  validationFilterApplied: boolean;
}

export interface CopilotRetrievedPublication {
  id: number;
  title: string;
  abstractText: string | null;
  year: number | null;
  doi: string | null;
  source: string | null;
  url: string | null;
  authors: string[];
  topics: string[];
  similarityScore: number | null;
  passedThreshold: boolean;
  lowSimilarity: boolean;
  retrievalReason: string;
}

export interface CopilotSignal {
  name: string;
  count: number;
}

export interface CopilotCitation {
  id: number;
  citationIndex: number;
  title: string;
  year: number | null;
  authors: string[];
  topics: string[];
  doi: string | null;
  source: string | null;
  url: string | null;
  similarityScore: number | null;
}

export interface PublicationEmbeddingRebuildReport {
  totalPublications: number;
  processedPublications: number;
  storedPublicationEmbeddings: number;
  storedEmbeddings: boolean;
  provider: string;
  model: string;
  dimension: number;
  message: string;
  warnings: string[];
}

export interface PublicationSemanticSearchResult {
  id: number;
  title: string;
  year: number | null;
  type: PublicationType;
  status: PublicationStatus;
  doi: string | null;
  source: string | null;
  createdAt: string;
  authors: string[];
  topics: string[];
  similarityScore: number;
  passedThreshold: boolean;
  lowSimilarity: boolean;
  retrievalReason: string;
  visibilityScope: string;
  validationFilterApplied: boolean;
}

export interface RelatedPublicationsResponse {
  publicationId: number;
  limit: number;
  minScore: number;
  metadataOnly: boolean;
  warnings: string[];
  relatedPublications: RelatedPublication[];
}

export interface StrategicResearchMapResponse {
  yearFrom: number | null;
  yearTo: number | null;
  researchUnitId: number | null;
  onlyValidated: boolean;
  visibilityScope: string;
  validationFilterApplied: boolean;
  groupingApproach: string;
  warnings: string[];
  researchLines: StrategicResearchMapLineResponse[];
}

export interface StrategicResearchMapLineResponse {
  lineId: string;
  title: string;
  description: string;
  publicationCount: number;
  researchers: StrategicResearchMapNamedCount[];
  researchUnits: StrategicResearchMapNamedCount[];
  topics: StrategicResearchMapNamedCount[];
  representativePublications: StrategicResearchMapPublicationReference[];
  trendSummary: string;
  confidence: number | null;
  warnings: string[];
}

export interface StrategicResearchMapNamedCount {
  id: number | null;
  name: string;
  publicationCount: number;
}

export interface StrategicResearchMapPublicationReference {
  id: number;
  citationKey: string;
  title: string;
  year: number | null;
  doi: string | null;
  source: string | null;
  topics: string[];
  relevanceScore: number | null;
  semanticCentrality: number | null;
}

export interface RelatedPublication {
  publication: PublicationSummary;
  finalScore: number;
  semanticScore: number | null;
  metadataScore: number;
  sharedTopicNames: string[];
  sharedAuthorNames: string[];
  relatedResearchUnitNames: string[];
  yearDistance: number | null;
  explanationReasons: string[];
  warning: string | null;
}

export interface Venue {
  id: number;
  name: string;
  shortName: string | null;
  typeCode: string;
  issn: string | null;
  eissn: string | null;
  isbn: string | null;
  country: string | null;
  website: string | null;
  description: string | null;
  publisherId: number | null;
  active: boolean;
  validationStatus: ValidationStatus;
  createdAt: string;
  updatedAt: string;
  createdByUserId: number | null;
  updatedByUserId: number | null;
}

export interface VenueRequest {
  name: string;
  shortName: string | null;
  typeCode: string;
  issn: string | null;
  eissn: string | null;
  isbn: string | null;
  country: string | null;
  website: string | null;
  description: string | null;
  publisherId: number | null;
  active: boolean;
  validationStatus: ValidationStatus | null;
}

export interface Publisher {
  id: number;
  name: string;
  country: string | null;
  website: string | null;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PublisherRequest {
  name: string;
  country: string | null;
  website: string | null;
  description: string | null;
  active: boolean;
}

export interface ScientificEvent {
  id: number;
  name: string;
  edition: string | null;
  eventTypeCode: string;
  startDate: string | null;
  endDate: string | null;
  city: string | null;
  country: string | null;
  organizer: string | null;
  website: string | null;
  description: string | null;
  evidenceUrl: string | null;
  venueId: number | null;
  active: boolean;
  validationStatus: ValidationStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ScientificEventRequest {
  name: string;
  edition: string | null;
  eventTypeCode: string;
  startDate: string | null;
  endDate: string | null;
  city: string | null;
  country: string | null;
  organizer: string | null;
  website: string | null;
  description: string | null;
  evidenceUrl: string | null;
  venueId: number | null;
  active: boolean;
  validationStatus: ValidationStatus | null;
}

export interface EventParticipation {
  id: number;
  eventId: number;
  eventName: string;
  researcherId: number;
  researcherName: string | null;
  researchUnitId: number | null;
  researchUnitName: string | null;
  participationTypeCode: string;
  title: string;
  description: string | null;
  evidenceUrl: string | null;
  participationDate: string | null;
  relatedPublicationId: number | null;
  relatedPublicationTitle: string | null;
  validationStatus: ValidationStatus;
  submittedAt: string | null;
  submittedBy: string | null;
  validatedAt: string | null;
  validatedBy: string | null;
  validationComment: string | null;
  canEdit: boolean;
  canSubmit: boolean;
  canValidate: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface EventParticipationRequest {
  eventId: number;
  researcherId: number;
  researchUnitId: number | null;
  participationTypeCode: string;
  title: string;
  description: string | null;
  evidenceUrl: string | null;
  participationDate: string | null;
  relatedPublicationId: number | null;
  validationStatus: ValidationStatus | null;
}
