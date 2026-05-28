import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ActivityAuditAction, ActivityAuditEvent, PageResponse, ValidationEntityType } from './api-models';

export interface AuditEventFilters {
  entityType?: ValidationEntityType;
  entityId?: number;
  action?: ActivityAuditAction;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/audit`;

  events(filters: AuditEventFilters = {}): Observable<PageResponse<ActivityAuditEvent>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));

    if (filters.entityType) {
      params = params.set('entityType', filters.entityType);
    }
    if (filters.entityId !== undefined) {
      params = params.set('entityId', String(filters.entityId));
    }
    if (filters.action) {
      params = params.set('action', filters.action);
    }

    return this.http.get<PageResponse<ActivityAuditEvent>>(`${this.baseUrl}/events`, { params });
  }

  entityEvents(entityType: ValidationEntityType, entityId: number, page = 0, size = 10): Observable<PageResponse<ActivityAuditEvent>> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));

    return this.http.get<PageResponse<ActivityAuditEvent>>(`${this.baseUrl}/entities/${entityType}/${entityId}`, { params });
  }
}
