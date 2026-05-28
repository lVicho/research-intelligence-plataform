import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PublicationCsvIngestionReport } from './api-models';

@Injectable({ providedIn: 'root' })
export class IngestionApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/ingestion`;

  uploadPublicationsCsv(file: File): Observable<PublicationCsvIngestionReport> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<PublicationCsvIngestionReport>(`${this.baseUrl}/publications/csv`, formData);
  }
}
