import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MasterDataItem } from './api-models';

@Injectable({ providedIn: 'root' })
export class MasterDataApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/master-data`;

  publicationTypes(): Observable<MasterDataItem[]> {
    return this.http.get<MasterDataItem[]>(`${this.baseUrl}/publication-types`);
  }

  publicationStatuses(): Observable<MasterDataItem[]> {
    return this.http.get<MasterDataItem[]>(`${this.baseUrl}/publication-statuses`);
  }

  venueTypes(): Observable<MasterDataItem[]> {
    return this.http.get<MasterDataItem[]>(`${this.baseUrl}/venue-types`);
  }

  eventTypes(): Observable<MasterDataItem[]> {
    return this.http.get<MasterDataItem[]>(`${this.baseUrl}/event-types`);
  }

  eventParticipationTypes(): Observable<MasterDataItem[]> {
    return this.http.get<MasterDataItem[]>(`${this.baseUrl}/event-participation-types`);
  }
}
