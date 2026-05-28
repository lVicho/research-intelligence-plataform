import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { StrategicResearchMapResponse } from './api-models';

export interface StrategicMapFilters {
  yearFrom?: number;
  yearTo?: number;
  researchUnitId?: number;
  onlyValidated?: boolean;
}

@Injectable({ providedIn: 'root' })
export class StrategicMapApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/strategic-map`;

  researchLines(filters: StrategicMapFilters = {}): Observable<StrategicResearchMapResponse> {
    let params = new HttpParams();
    if (filters.yearFrom !== undefined) {
      params = params.set('yearFrom', String(filters.yearFrom));
    }
    if (filters.yearTo !== undefined) {
      params = params.set('yearTo', String(filters.yearTo));
    }
    if (filters.researchUnitId !== undefined) {
      params = params.set('researchUnitId', String(filters.researchUnitId));
    }
    if (filters.onlyValidated !== undefined) {
      params = params.set('onlyValidated', String(filters.onlyValidated));
    }
    return this.http.get<StrategicResearchMapResponse>(`${this.baseUrl}/research-lines`, { params });
  }
}
