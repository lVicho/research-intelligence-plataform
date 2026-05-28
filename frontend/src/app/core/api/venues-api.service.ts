import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse, ValidationStatus, Venue, VenueRequest } from './api-models';

export interface VenueFilters {
  page?: number;
  size?: number;
  text?: string;
  typeCode?: string;
  active?: boolean;
  validationStatus?: ValidationStatus;
}

@Injectable({ providedIn: 'root' })
export class VenuesApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/venues`;

  search(filters: VenueFilters = {}): Observable<PageResponse<Venue>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));

    if (filters.text) {
      params = params.set('text', filters.text);
    }
    if (filters.typeCode) {
      params = params.set('typeCode', filters.typeCode);
    }
    if (filters.active !== undefined) {
      params = params.set('active', String(filters.active));
    }
    if (filters.validationStatus) {
      params = params.set('validationStatus', filters.validationStatus);
    }

    return this.http.get<PageResponse<Venue>>(this.baseUrl, { params });
  }

  get(id: number): Observable<Venue> {
    return this.http.get<Venue>(`${this.baseUrl}/${id}`);
  }

  create(request: VenueRequest): Observable<Venue> {
    return this.http.post<Venue>(this.baseUrl, request);
  }

  update(id: number, request: VenueRequest): Observable<Venue> {
    return this.http.put<Venue>(`${this.baseUrl}/${id}`, request);
  }
}
