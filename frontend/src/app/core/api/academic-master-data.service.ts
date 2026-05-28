import { Injectable, computed, inject, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { MasterDataItem } from './api-models';
import { MasterDataApiService } from './master-data-api.service';
import {
  FALLBACK_PUBLICATION_STATUS_LABELS,
  FALLBACK_PUBLICATION_TYPE_LABELS,
  publicationStatusLabel,
  publicationTypeLabel
} from '../../shared/utils/display-labels';

interface LoadCategoryResult {
  items: MasterDataItem[];
  failed: boolean;
}

@Injectable({ providedIn: 'root' })
export class AcademicMasterDataService {
  private readonly api = inject(MasterDataApiService);
  private readonly loadStarted = signal(false);
  private readonly publicationTypeItems = signal<MasterDataItem[]>([]);
  private readonly publicationStatusItems = signal<MasterDataItem[]>([]);
  private readonly venueTypeItems = signal<MasterDataItem[]>([]);
  private readonly eventTypeItems = signal<MasterDataItem[]>([]);
  private readonly eventParticipationTypeItems = signal<MasterDataItem[]>([]);

  readonly loading = signal(false);
  readonly error = signal('');
  readonly hasLoaded = signal(false);
  readonly publicationTypeCodes = computed(() => this.codesOrFallback(this.publicationTypeItems(), FALLBACK_PUBLICATION_TYPE_LABELS));
  readonly publicationStatusCodes = computed(() => this.codesOrFallback(this.publicationStatusItems(), FALLBACK_PUBLICATION_STATUS_LABELS));
  readonly venueTypeCodes = computed(() => this.venueTypeItems().map((item) => item.code));
  readonly eventTypeCodes = computed(() => this.eventTypeItems().map((item) => item.code));
  readonly eventParticipationTypeCodes = computed(() => this.eventParticipationTypeItems().map((item) => item.code));

  ensureLoaded(): void {
    if (this.loadStarted()) {
      return;
    }

    this.loadStarted.set(true);
    this.loading.set(true);
    this.error.set('');

    forkJoin({
      publicationTypes: this.loadCategory(this.api.publicationTypes()),
      publicationStatuses: this.loadCategory(this.api.publicationStatuses()),
      venueTypes: this.loadCategory(this.api.venueTypes()),
      eventTypes: this.loadCategory(this.api.eventTypes()),
      eventParticipationTypes: this.loadCategory(this.api.eventParticipationTypes())
    }).subscribe({
      next: (result) => {
        this.publicationTypeItems.set(result.publicationTypes.items);
        this.publicationStatusItems.set(result.publicationStatuses.items);
        this.venueTypeItems.set(result.venueTypes.items);
        this.eventTypeItems.set(result.eventTypes.items);
        this.eventParticipationTypeItems.set(result.eventParticipationTypes.items);

        const hasFailures = [
          result.publicationTypes,
          result.publicationStatuses,
          result.venueTypes,
          result.eventTypes,
          result.eventParticipationTypes
        ].some((category) => category.failed);

        this.error.set(hasFailures ? 'No se pudieron cargar algunos datos maestros acad\u00e9micos. Se muestran etiquetas de respaldo.' : '');
        this.loading.set(false);
        this.hasLoaded.set(true);
      },
      error: () => {
        this.error.set('No se pudieron cargar los datos maestros acad\u00e9micos. Se muestran etiquetas de respaldo.');
        this.loading.set(false);
        this.hasLoaded.set(true);
      }
    });
  }

  retry(): void {
    this.loadStarted.set(false);
    this.ensureLoaded();
  }

  publicationTypeLabel(code: string): string {
    return this.lookupLabel(this.publicationTypeItems(), code, publicationTypeLabel(code));
  }

  publicationStatusLabel(code: string): string {
    return this.lookupLabel(this.publicationStatusItems(), code, publicationStatusLabel(code));
  }

  venueTypeLabel(code: string): string {
    return this.lookupLabel(this.venueTypeItems(), code, code);
  }

  eventTypeLabel(code: string): string {
    return this.lookupLabel(this.eventTypeItems(), code, code);
  }

  eventParticipationTypeLabel(code: string): string {
    return this.lookupLabel(this.eventParticipationTypeItems(), code, code);
  }

  private loadCategory(source$: ReturnType<MasterDataApiService['publicationTypes']>) {
    return source$.pipe(
      map((items) => ({ items, failed: false })),
      catchError(() => of({ items: [], failed: true }))
    );
  }

  private lookupLabel(items: MasterDataItem[], code: string, fallbackLabel: string): string {
    return items.find((item) => item.code === code)?.labelEs ?? fallbackLabel;
  }

  private codesOrFallback(items: MasterDataItem[], fallbackLabels: Record<string, string>): string[] {
    return items.length > 0 ? items.map((item) => item.code) : Object.keys(fallbackLabels);
  }
}
