import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { RetrievalMode, VisibilityScopeCode } from './api-models';

export interface ExpertFinderFiltersRequest {
  researchUnitId: number | null;
  topic: string | null;
  onlyValidated: boolean | null;
}

export interface ExpertFinderSearchRequest {
  query: string;
  limit: number | null;
  mode: RetrievalMode | null;
  filters: ExpertFinderFiltersRequest | null;
}

export interface ExpertFinderResearcherSummary {
  id: number;
  fullName: string;
  displayName: string | null;
  orcid: string | null;
  primaryResearchUnitId: number | null;
  primaryResearchUnitName: string | null;
}

export interface ExpertFinderPublicationEvidence {
  id: number;
  title: string;
  year: number | null;
  type: string | null;
  doi: string | null;
  source: string | null;
  url: string | null;
  semanticSimilarity: number | null;
  matchedTopics: string[];
}

export interface ExpertFinderEventEvidence {
  id: number;
  eventId: number;
  eventName: string | null;
  participationTypeCode: string | null;
  title: string | null;
  participationDate: string | null;
  relatedPublicationId: number | null;
}

export interface ExpertFinderResult {
  researcher: ExpertFinderResearcherSummary;
  score: number;
  confidence: 'HIGH' | 'MEDIUM' | 'LOW' | string;
  matchedTopics: string[];
  representativePublications: ExpertFinderPublicationEvidence[];
  relevantEventParticipations: ExpertFinderEventEvidence[];
  reasons: string[];
  explanation: string;
  warnings: string[];
}

export interface ExpertFinderSearchResponse {
  results: ExpertFinderResult[];
  warnings: string[];
  rankingMethod: string;
  visibilityScope: VisibilityScopeCode | string;
  validationFilterApplied: boolean;
}

@Injectable({ providedIn: 'root' })
export class ExpertFinderApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/expert-finder`;

  search(request: ExpertFinderSearchRequest): Observable<ExpertFinderSearchResponse> {
    return this.http.post<ExpertFinderSearchResponse>(`${this.baseUrl}/search`, request);
  }
}
