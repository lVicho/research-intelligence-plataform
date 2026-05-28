import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { PortalDemoQueryContext } from './api-models';
import { PortalApiService } from './portal-api.service';

export interface DemoQuerySuggestionsResult {
  queries: string[];
  dynamic: boolean;
}

export interface DemoQuerySuggestionsRequest {
  context: PortalDemoQueryContext;
  fallbackQueries: string[];
  limit?: number;
}

@Injectable({ providedIn: 'root' })
export class PortalDemoQuerySuggestionsService {
  private readonly portalApi = inject(PortalApiService);

  loadSuggestions(request: DemoQuerySuggestionsRequest): Observable<DemoQuerySuggestionsResult> {
    const fallbackQueries = this.sanitizeQueries(request.fallbackQueries, 5);

    return this.portalApi.demoQueries({
      context: request.context,
      limit: request.limit ?? 6,
      onlyValidated: true
    }).pipe(
      map((queries) => {
        const sanitizedQueries = this.sanitizeQueries(
          queries.map((query) => query.query),
          5
        );

        if (sanitizedQueries.length >= 4) {
          return {
            queries: sanitizedQueries,
            dynamic: true
          };
        }

        return {
          queries: fallbackQueries,
          dynamic: false
        };
      }),
      catchError(() => of({
        queries: fallbackQueries,
        dynamic: false
      }))
    );
  }

  private sanitizeQueries(queries: string[], limit: number): string[] {
    const deduplicated = new Map<string, string>();

    for (const query of queries) {
      const trimmed = query.trim().replace(/\s+/g, ' ');
      const normalized = this.normalize(trimmed);
      if (!trimmed || deduplicated.has(normalized)) {
        continue;
      }
      deduplicated.set(normalized, trimmed);
    }

    return Array.from(deduplicated.values()).slice(0, limit);
  }

  private normalize(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
  }
}
