import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ResearchUnit, ResearchUnitRequest, ResearchUnitTreeNode } from './api-models';

@Injectable({ providedIn: 'root' })
export class ResearchUnitsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/research-units`;

  list(): Observable<ResearchUnit[]> {
    return this.http.get<ResearchUnit[]>(this.baseUrl);
  }

  tree(): Observable<ResearchUnitTreeNode[]> {
    return this.http.get<ResearchUnitTreeNode[]>(`${this.baseUrl}/tree`);
  }

  get(id: number): Observable<ResearchUnit> {
    return this.http.get<ResearchUnit>(`${this.baseUrl}/${id}`);
  }

  create(request: ResearchUnitRequest): Observable<ResearchUnit> {
    return this.http.post<ResearchUnit>(this.baseUrl, request);
  }

  update(id: number, request: ResearchUnitRequest): Observable<ResearchUnit> {
    return this.http.put<ResearchUnit>(`${this.baseUrl}/${id}`, request);
  }
}
