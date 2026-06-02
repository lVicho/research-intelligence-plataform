import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PortalContextAssistantRequest,
  PortalContextAssistantResponse,
  PortalDemoQuery,
  PortalDemoQueryContext,
  PortalPageResponse,
  PortalPublicationDetail,
  PortalPublicationExplanation,
  PortalPublicationExplanationRequest,
  PortalPublicationSummary,
  PortalResearchUnitDetail,
  PortalResearchUnitSummary,
  PortalResearcherDetail,
  PortalResearcherSummary,
  PortalSummary,
  PublicationStatus,
  PublicationType,
  ResearchUnitType
} from './api-models';

export interface PortalResearchUnitFilters {
  page?: number;
  size?: number;
  text?: string;
  type?: ResearchUnitType;
}

export interface PortalResearcherFilters {
  page?: number;
  size?: number;
  text?: string;
  researchUnitId?: number;
  topic?: string;
}

export interface PortalDemoQueryFilters {
  context?: PortalDemoQueryContext;
  limit?: number;
  onlyValidated?: boolean;
}

export interface PortalPublicationFilters {
  page?: number;
  size?: number;
  text?: string;
  yearFrom?: number;
  yearTo?: number;
  type?: PublicationType;
  status?: PublicationStatus;
  researchUnitId?: number;
  researcherId?: number;
  topic?: string;
  sortBy?: 'year' | 'title' | 'type' | 'status' | 'createdAt';
  sortDirection?: 'asc' | 'desc';
}

@Injectable({ providedIn: 'root' })
export class PortalApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/portal`;

  summary(): Observable<PortalSummary> {
    return this.http.get<PortalSummary>(`${this.baseUrl}/summary`);
  }

  researchUnits(filters: PortalResearchUnitFilters = {}): Observable<PortalPageResponse<PortalResearchUnitSummary>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.text) {
      params = params.set('text', filters.text);
    }
    if (filters.type) {
      params = params.set('type', filters.type);
    }
    return this.http.get<PortalPageResponse<PortalResearchUnitSummary>>(`${this.baseUrl}/research-units`, { params });
  }

  researchUnit(id: number): Observable<PortalResearchUnitDetail> {
    return this.http.get<PortalResearchUnitDetail>(`${this.baseUrl}/research-units/${id}`);
  }

  researchers(filters: PortalResearcherFilters = {}): Observable<PortalPageResponse<PortalResearcherSummary>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.text) {
      params = params.set('text', filters.text);
    }
    if (filters.researchUnitId !== undefined) {
      params = params.set('researchUnitId', String(filters.researchUnitId));
    }
    if (filters.topic) {
      params = params.set('topic', filters.topic);
    }
    return this.http.get<PortalPageResponse<PortalResearcherSummary>>(`${this.baseUrl}/researchers`, { params });
  }

  researcher(id: number): Observable<PortalResearcherDetail> {
    return this.http.get<PortalResearcherDetail>(`${this.baseUrl}/researchers/${id}`);
  }

  publications(filters: PortalPublicationFilters = {}): Observable<PortalPageResponse<PortalPublicationSummary>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.text) {
      params = params.set('text', filters.text);
    }
    if (filters.yearFrom !== undefined) {
      params = params.set('yearFrom', String(filters.yearFrom));
    }
    if (filters.yearTo !== undefined) {
      params = params.set('yearTo', String(filters.yearTo));
    }
    if (filters.type) {
      params = params.set('type', filters.type);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.researchUnitId !== undefined) {
      params = params.set('researchUnitId', String(filters.researchUnitId));
    }
    if (filters.researcherId !== undefined) {
      params = params.set('researcherId', String(filters.researcherId));
    }
    if (filters.topic) {
      params = params.set('topic', filters.topic);
    }
    if (filters.sortBy) {
      params = params.set('sortBy', filters.sortBy);
    }
    if (filters.sortDirection) {
      params = params.set('sortDirection', filters.sortDirection);
    }
    return this.http.get<PortalPageResponse<PortalPublicationSummary>>(`${this.baseUrl}/publications`, { params });
  }

  publication(id: number): Observable<PortalPublicationDetail> {
    return this.http.get<PortalPublicationDetail>(`${this.baseUrl}/publications/${id}`);
  }

  explainPublication(id: number, request: PortalPublicationExplanationRequest): Observable<PortalPublicationExplanation> {
    return this.http.post<PortalPublicationExplanation>(`${this.baseUrl}/publications/${id}/explain`, request);
  }

  askContextAssistant(request: PortalContextAssistantRequest): Observable<PortalContextAssistantResponse> {
    return this.http.post<PortalContextAssistantResponse>(`${this.baseUrl}/context-assistant/ask`, request);
  }

  demoQueries(filters: PortalDemoQueryFilters = {}): Observable<PortalDemoQuery[]> {
    const params = new HttpParams()
      .set('context', filters.context ?? 'GENERAL')
      .set('limit', String(filters.limit ?? 6))
      .set('onlyValidated', String(filters.onlyValidated ?? true));
    return this.http.get<PortalDemoQuery[]>(`${this.baseUrl}/demo-queries`, { params });
  }
}
