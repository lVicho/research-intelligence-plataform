import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  NewsArticle,
  NewsArticleRequest,
  NewsArticleStatus,
  PageResponse,
  PortalNewsArticle,
  PortalNewsArticleSummary
} from './api-models';

export interface PortalNewsFilters {
  page?: number;
  size?: number;
  text?: string;
}

export interface AdminNewsFilters {
  page?: number;
  size?: number;
  text?: string;
  status?: NewsArticleStatus;
}

@Injectable({ providedIn: 'root' })
export class NewsApiService {
  private readonly http = inject(HttpClient);
  private readonly portalBaseUrl = `${environment.apiBaseUrl}/portal/news`;
  private readonly adminBaseUrl = `${environment.apiBaseUrl}/admin/news`;

  searchPortal(filters: PortalNewsFilters = {}): Observable<PageResponse<PortalNewsArticleSummary>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.text) {
      params = params.set('text', filters.text);
    }
    return this.http.get<PageResponse<PortalNewsArticleSummary>>(this.portalBaseUrl, { params });
  }

  getPortal(id: number): Observable<PortalNewsArticle> {
    return this.http.get<PortalNewsArticle>(`${this.portalBaseUrl}/${id}`);
  }

  searchAdmin(filters: AdminNewsFilters = {}): Observable<PageResponse<NewsArticle>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.text) {
      params = params.set('text', filters.text);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    return this.http.get<PageResponse<NewsArticle>>(this.adminBaseUrl, { params });
  }

  getAdmin(id: number): Observable<NewsArticle> {
    return this.http.get<NewsArticle>(`${this.adminBaseUrl}/${id}`);
  }

  create(request: NewsArticleRequest): Observable<NewsArticle> {
    return this.http.post<NewsArticle>(this.adminBaseUrl, request);
  }

  update(id: number, request: NewsArticleRequest): Observable<NewsArticle> {
    return this.http.put<NewsArticle>(`${this.adminBaseUrl}/${id}`, request);
  }

  publish(id: number): Observable<NewsArticle> {
    return this.http.post<NewsArticle>(`${this.adminBaseUrl}/${id}/publish`, {});
  }

  archive(id: number): Observable<NewsArticle> {
    return this.http.post<NewsArticle>(`${this.adminBaseUrl}/${id}/archive`, {});
  }
}
