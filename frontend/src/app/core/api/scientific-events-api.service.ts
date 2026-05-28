import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PageResponse,
  ScientificEvent,
  ScientificEventRequest,
  ValidationStatus
} from './api-models';

export interface ScientificEventFilters {
  page?: number;
  size?: number;
  text?: string;
  eventTypeCode?: string;
  venueId?: number;
  validationStatus?: ValidationStatus;
}

@Injectable({ providedIn: 'root' })
export class ScientificEventsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/scientific-events`;

  search(filters: ScientificEventFilters = {}): Observable<PageResponse<ScientificEvent>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));

    if (filters.text) {
      params = params.set('text', filters.text);
    }
    if (filters.eventTypeCode) {
      params = params.set('eventTypeCode', filters.eventTypeCode);
    }
    if (filters.venueId !== undefined) {
      params = params.set('venueId', String(filters.venueId));
    }
    if (filters.validationStatus) {
      params = params.set('validationStatus', filters.validationStatus);
    }

    return this.http.get<PageResponse<ScientificEvent>>(this.baseUrl, { params });
  }

  get(id: number): Observable<ScientificEvent> {
    return this.http.get<ScientificEvent>(`${this.baseUrl}/${id}`);
  }

  create(request: ScientificEventRequest): Observable<ScientificEvent> {
    return this.http.post<ScientificEvent>(this.baseUrl, request);
  }

  update(id: number, request: ScientificEventRequest): Observable<ScientificEvent> {
    return this.http.put<ScientificEvent>(`${this.baseUrl}/${id}`, request);
  }
}
