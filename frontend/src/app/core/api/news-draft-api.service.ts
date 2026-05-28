import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { NewsDraftGenerateRequest, NewsDraftGenerateResponse } from './api-models';

@Injectable({ providedIn: 'root' })
export class NewsDraftApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/ai/news`;

  generateDraft(request: NewsDraftGenerateRequest): Observable<NewsDraftGenerateResponse> {
    return this.http.post<NewsDraftGenerateResponse>(`${this.baseUrl}/generate-draft`, request);
  }
}
