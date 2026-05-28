import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  EventParticipation,
  EventParticipationRequest,
  PageResponse,
  ValidationStatus
} from './api-models';

export interface EventParticipationFilters {
  page?: number;
  size?: number;
  text?: string;
  eventId?: number;
  researcherId?: number;
  researchUnitId?: number;
  validationStatus?: ValidationStatus;
}

@Injectable({ providedIn: 'root' })
export class EventParticipationsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/event-participations`;

  search(filters: EventParticipationFilters = {}): Observable<PageResponse<EventParticipation>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));

    if (filters.text) {
      params = params.set('text', filters.text);
    }
    if (filters.eventId !== undefined) {
      params = params.set('eventId', String(filters.eventId));
    }
    if (filters.researcherId !== undefined) {
      params = params.set('researcherId', String(filters.researcherId));
    }
    if (filters.researchUnitId !== undefined) {
      params = params.set('researchUnitId', String(filters.researchUnitId));
    }
    if (filters.validationStatus) {
      params = params.set('validationStatus', filters.validationStatus);
    }

    return this.http.get<PageResponse<EventParticipation>>(this.baseUrl, { params });
  }

  get(id: number): Observable<EventParticipation> {
    return this.http.get<EventParticipation>(`${this.baseUrl}/${id}`);
  }

  create(request: EventParticipationRequest): Observable<EventParticipation> {
    return this.http.post<EventParticipation>(this.baseUrl, request);
  }

  update(id: number, request: EventParticipationRequest): Observable<EventParticipation> {
    return this.http.put<EventParticipation>(`${this.baseUrl}/${id}`, request);
  }

  submit(id: number): Observable<EventParticipation> {
    return this.http.post<EventParticipation>(`${this.baseUrl}/${id}/submit`, {});
  }
}
