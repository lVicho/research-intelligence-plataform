import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PageResponse,
  ValidationCommentRequest,
  ValidationEntityType,
  ValidationItem,
  ValidationItemDetail,
  ValidationStatus
} from './api-models';

export interface ValidationInboxFilters {
  status?: ValidationStatus;
  entityType?: ValidationEntityType;
  researcherId?: number;
  researchUnitId?: number;
  submittedFrom?: string;
  submittedTo?: string;
  text?: string;
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class ValidationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/validation`;

  inbox(filters: ValidationInboxFilters = {}): Observable<PageResponse<ValidationItem>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20))
      .set('sort', filters.sort ?? 'submittedAt,desc');

    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.entityType) {
      params = params.set('entityType', filters.entityType);
    }
    if (filters.researcherId !== undefined) {
      params = params.set('researcherId', String(filters.researcherId));
    }
    if (filters.researchUnitId !== undefined) {
      params = params.set('researchUnitId', String(filters.researchUnitId));
    }
    if (filters.submittedFrom) {
      params = params.set('submittedFrom', filters.submittedFrom);
    }
    if (filters.submittedTo) {
      params = params.set('submittedTo', filters.submittedTo);
    }
    if (filters.text) {
      params = params.set('text', filters.text);
    }

    return this.http.get<PageResponse<ValidationItem>>(`${this.baseUrl}/inbox`, { params });
  }

  get(entityType: ValidationEntityType, entityId: number): Observable<ValidationItemDetail> {
    return this.http.get<ValidationItemDetail>(`${this.baseUrl}/items/${entityType}/${entityId}`);
  }

  validate(entityType: ValidationEntityType, entityId: number, request: ValidationCommentRequest): Observable<ValidationItemDetail> {
    return this.http.post<ValidationItemDetail>(`${this.baseUrl}/items/${entityType}/${entityId}/validate`, request);
  }

  reject(entityType: ValidationEntityType, entityId: number, request: ValidationCommentRequest): Observable<ValidationItemDetail> {
    return this.http.post<ValidationItemDetail>(`${this.baseUrl}/items/${entityType}/${entityId}/reject`, request);
  }

  requestChanges(entityType: ValidationEntityType, entityId: number, request: ValidationCommentRequest): Observable<ValidationItemDetail> {
    return this.http.post<ValidationItemDetail>(`${this.baseUrl}/items/${entityType}/${entityId}/request-changes`, request);
  }
}
