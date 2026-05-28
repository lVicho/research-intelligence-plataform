import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PageResponse,
  Publication,
  PublicationFilterMetadata,
  PublicationRequest,
  RelatedPublicationsResponse,
  PublicationSemanticSearchResult,
  PublicationStatus,
  PublicationSummary,
  PublicationType
} from './api-models';

export interface PublicationFilters {
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

export interface SemanticSearchOptions {
  query: string;
  limit?: number;
  minSimilarity?: number | null;
  includeNonValidated?: boolean | null;
}

export interface RelatedPublicationOptions {
  limit?: number;
  minScore?: number | null;
}

@Injectable({ providedIn: 'root' })
export class PublicationsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/publications`;

  search(filters: PublicationFilters = {}): Observable<PageResponse<PublicationSummary>> {
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
    return this.http.get<PageResponse<PublicationSummary>>(this.baseUrl, { params });
  }

  filterMetadata(): Observable<PublicationFilterMetadata> {
    return this.http.get<PublicationFilterMetadata>(`${this.baseUrl}/filter-metadata`);
  }

  semanticSearch(options: SemanticSearchOptions): Observable<PublicationSemanticSearchResult[]> {
    let params = new HttpParams()
      .set('query', options.query)
      .set('limit', String(options.limit ?? 10));
    if (options.minSimilarity !== null && options.minSimilarity !== undefined) {
      params = params.set('minSimilarity', String(options.minSimilarity));
    }
    if (options.includeNonValidated !== null && options.includeNonValidated !== undefined) {
      params = params.set('includeNonValidated', String(options.includeNonValidated));
    }
    return this.http.get<PublicationSemanticSearchResult[]>(`${this.baseUrl}/semantic-search`, { params });
  }

  related(id: number, options: RelatedPublicationOptions = {}): Observable<RelatedPublicationsResponse> {
    let params = new HttpParams().set('limit', String(options.limit ?? 15));
    if (options.minScore !== null && options.minScore !== undefined) {
      params = params.set('minScore', String(options.minScore));
    }
    return this.http.get<RelatedPublicationsResponse>(`${this.baseUrl}/${id}/related`, { params });
  }

  get(id: number): Observable<Publication> {
    return this.http.get<Publication>(`${this.baseUrl}/${id}`);
  }

  create(request: PublicationRequest): Observable<Publication> {
    return this.http.post<Publication>(this.baseUrl, request);
  }

  update(id: number, request: PublicationRequest): Observable<Publication> {
    return this.http.put<Publication>(`${this.baseUrl}/${id}`, request);
  }

  submit(id: number): Observable<Publication> {
    return this.http.post<Publication>(`${this.baseUrl}/${id}/submit`, {});
  }
}
