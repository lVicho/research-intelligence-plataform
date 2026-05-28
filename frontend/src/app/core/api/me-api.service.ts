import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  MeActivity,
  MeActivityDetail,
  MeDashboard,
  PageResponse,
  ValidationEntityType,
  ValidationStatus
} from './api-models';

export interface MeActivityFilters {
  status?: ValidationStatus;
  type?: ValidationEntityType;
  text?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class MeApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/me`;

  dashboard(): Observable<MeDashboard> {
    return this.http.get<MeDashboard>(`${this.baseUrl}/dashboard`);
  }

  activities(filters: MeActivityFilters = {}): Observable<PageResponse<MeActivity>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.type) {
      params = params.set('type', filters.type);
    }
    if (filters.text) {
      params = params.set('text', filters.text);
    }
    return this.http.get<PageResponse<MeActivity>>(`${this.baseUrl}/activities`, { params });
  }

  activityDetail(entityType: ValidationEntityType, entityId: number): Observable<MeActivityDetail> {
    return this.http.get<MeActivityDetail>(`${this.baseUrl}/activities/${entityType}/${entityId}`);
  }

  submitActivity(entityType: ValidationEntityType, entityId: number): Observable<MeActivityDetail> {
    return this.http.post<MeActivityDetail>(`${this.baseUrl}/activities/${entityType}/${entityId}/submit`, {});
  }
}
