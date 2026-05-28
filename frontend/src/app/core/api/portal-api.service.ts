import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PortalDemoQuery,
  PortalDemoQueryContext,
  PortalPageResponse,
  PortalResearchUnitDetail,
  PortalResearchUnitSummary,
  PortalResearcherDetail,
  PortalResearcherSummary,
  PortalSummary,
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

  demoQueries(filters: PortalDemoQueryFilters = {}): Observable<PortalDemoQuery[]> {
    const params = new HttpParams()
      .set('context', filters.context ?? 'GENERAL')
      .set('limit', String(filters.limit ?? 6))
      .set('onlyValidated', String(filters.onlyValidated ?? true));
    return this.http.get<PortalDemoQuery[]>(`${this.baseUrl}/demo-queries`, { params });
  }
}
