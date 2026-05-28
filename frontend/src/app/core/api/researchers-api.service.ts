import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PageResponse,
  Researcher,
  ResearcherAffiliation,
  ResearcherAffiliationRequest,
  ResearcherRequest,
  ResearcherSummary
} from './api-models';

export interface ResearcherFilters {
  page?: number;
  size?: number;
  text?: string;
  researchUnitId?: number;
  active?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ResearchersApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/researchers`;

  search(filters: ResearcherFilters = {}): Observable<PageResponse<ResearcherSummary>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.text) {
      params = params.set('text', filters.text);
    }
    if (filters.researchUnitId !== undefined) {
      params = params.set('researchUnitId', String(filters.researchUnitId));
    }
    if (filters.active !== undefined) {
      params = params.set('active', String(filters.active));
    }
    return this.http.get<PageResponse<ResearcherSummary>>(this.baseUrl, { params });
  }

  get(id: number): Observable<Researcher> {
    return this.http.get<Researcher>(`${this.baseUrl}/${id}`);
  }

  create(request: ResearcherRequest): Observable<Researcher> {
    return this.http.post<Researcher>(this.baseUrl, request);
  }

  update(id: number, request: ResearcherRequest): Observable<Researcher> {
    return this.http.put<Researcher>(`${this.baseUrl}/${id}`, request);
  }

  affiliations(id: number): Observable<ResearcherAffiliation[]> {
    return this.http.get<ResearcherAffiliation[]>(`${this.baseUrl}/${id}/affiliations`);
  }

  addAffiliation(id: number, request: ResearcherAffiliationRequest): Observable<ResearcherAffiliation> {
    return this.http.post<ResearcherAffiliation>(`${this.baseUrl}/${id}/affiliations`, request);
  }

  updateAffiliation(id: number, affiliationId: number, request: ResearcherAffiliationRequest): Observable<ResearcherAffiliation> {
    return this.http.put<ResearcherAffiliation>(`${this.baseUrl}/${id}/affiliations/${affiliationId}`, request);
  }

  deleteAffiliation(id: number, affiliationId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/affiliations/${affiliationId}`);
  }
}
