import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { GraphDensity, ResearchGraph } from './api-models';

export interface ResearcherGraphRequestOptions {
  density: GraphDensity;
  includePublications: boolean;
  includeTopics: boolean;
  includeCoauthors: boolean;
  includeResearchUnits: boolean;
  includeExternalAuthors: boolean;
}

@Injectable({ providedIn: 'root' })
export class GraphApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/graph`;

  researcherGraph(researcherId: number, options: ResearcherGraphRequestOptions): Observable<ResearchGraph> {
    return this.http.get<ResearchGraph>(`${this.baseUrl}/researcher/${researcherId}`, {
      params: {
        density: options.density,
        includePublications: options.includePublications,
        includeTopics: options.includeTopics,
        includeCoauthors: options.includeCoauthors,
        includeResearchUnits: options.includeResearchUnits,
        includeExternalAuthors: options.includeExternalAuthors
      }
    });
  }
}
