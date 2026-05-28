import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CopilotAnswerRequest,
  CopilotAnswerResponse,
  CopilotAskRequest,
  CopilotAskResponse,
  CopilotRetrieveRequest,
  CopilotRetrieveResponse,
  PublicationEmbeddingRebuildReport
} from './api-models';

@Injectable({ providedIn: 'root' })
export class CopilotApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  ask(request: CopilotAskRequest): Observable<CopilotAskResponse> {
    return this.http.post<CopilotAskResponse>(`${this.baseUrl}/copilot/ask`, request);
  }

  retrieve(request: CopilotRetrieveRequest): Observable<CopilotRetrieveResponse> {
    return this.http.post<CopilotRetrieveResponse>(`${this.baseUrl}/copilot/retrieve`, request);
  }

  answer(request: CopilotAnswerRequest): Observable<CopilotAnswerResponse> {
    return this.http.post<CopilotAnswerResponse>(`${this.baseUrl}/copilot/answer`, request);
  }

  rebuildPublicationEmbeddings(): Observable<PublicationEmbeddingRebuildReport> {
    return this.http.post<PublicationEmbeddingRebuildReport>(`${this.baseUrl}/ai/embeddings/publications/rebuild`, {});
  }
}
