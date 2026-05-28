import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse, Publisher, PublisherRequest } from './api-models';

export interface PublisherFilters {
  page?: number;
  size?: number;
  text?: string;
  active?: boolean;
}

@Injectable({ providedIn: 'root' })
export class PublishersApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/publishers`;

  search(filters: PublisherFilters = {}): Observable<PageResponse<Publisher>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));

    if (filters.text) {
      params = params.set('text', filters.text);
    }
    if (filters.active !== undefined) {
      params = params.set('active', String(filters.active));
    }

    return this.http.get<PageResponse<Publisher>>(this.baseUrl, { params });
  }

  get(id: number): Observable<Publisher> {
    return this.http.get<Publisher>(`${this.baseUrl}/${id}`);
  }

  create(request: PublisherRequest): Observable<Publisher> {
    return this.http.post<Publisher>(this.baseUrl, request);
  }

  update(id: number, request: PublisherRequest): Observable<Publisher> {
    return this.http.put<Publisher>(`${this.baseUrl}/${id}`, request);
  }
}
