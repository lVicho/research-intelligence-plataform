import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AcademicMasterDataService } from '../../core/api/academic-master-data.service';
import { PageResponse, Venue } from '../../core/api/api-models';
import { VenuesApiService } from '../../core/api/venues-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { validationStatusLabel, validationStatusTone } from '../../shared/utils/display-labels';

type ActiveFilter = 'all' | 'true' | 'false';

@Component({
  selector: 'rip-venues-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    EmptyStateComponent,
    PageHeaderComponent,
    StatusChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Canales"
        [subtitle]="(result()?.totalElements || 0) + ' canales de publicación y difusión científica.'"
        eyebrow="Difusión académica"
      >
        @if (canManageMasterData()) {
          <a
            mat-flat-button
            color="primary"
            [routerLink]="newVenueLink()"
            [queryParams]="navigationContext.returnQueryParams('Volver a canales')"
          >
            Añadir canal
          </a>
        }
      </rip-page-header>

      <mat-card appearance="outlined">
        <mat-card-content>
          <p class="section-kicker">Filtros</p>
          <form class="form-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline">
              <mat-label>Buscar</mat-label>
              <input matInput formControlName="text">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Tipo</mat-label>
              <mat-select formControlName="typeCode">
                <mat-option value="all">Todos los tipos</mat-option>
                @for (typeCode of venueTypeCodes(); track typeCode) {
                  <mat-option [value]="typeCode">{{ venueTypeLabel(typeCode) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Estado</mat-label>
              <mat-select formControlName="active">
                <mat-option value="all">Todos</mat-option>
                <mat-option value="true">Activos</mat-option>
                <mat-option value="false">Inactivos</mat-option>
              </mat-select>
            </mat-form-field>
            <div class="actions filter-actions">
              <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
              <button mat-flat-button color="primary" type="submit">Aplicar</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-content>
          @if ((result()?.content || []).length === 0) {
            <rip-empty-state title="Sin canales" message="No hay canales que coincidan con los filtros actuales." />
          } @else {
            <div class="item-list">
              @for (venue of result()?.content || []; track venue.id) {
                <a class="item-row interactive" [routerLink]="venueLink(venue.id)" [queryParams]="navigationContext.returnQueryParams('Volver a canales')">
                  <div class="item-main">
                    <strong class="item-title">{{ venue.name }}</strong>
                    <span>{{ venueTypeLabel(venue.typeCode) }}</span>
                    <span>{{ identifierLabel(venue) }}</span>
                  </div>
                  <div class="item-meta">
                    @if (venue.country) {
                      <span class="meta-text">{{ venue.country }}</span>
                    }
                    <rip-status-chip [label]="statusLabel(venue.validationStatus)" [tone]="statusTone(venue.validationStatus)" />
                  </div>
                </a>
              }
            </div>
          }
        </mat-card-content>
      </mat-card>

      <div class="pagination">
        <button mat-button type="button" [disabled]="currentPage() === 0" (click)="goToPage(currentPage() - 1)">Anterior</button>
        <span>Página {{ currentPage() + 1 }} de {{ result()?.totalPages || 1 }}</span>
        <button mat-button type="button" [disabled]="result()?.last ?? true" (click)="goToPage(currentPage() + 1)">Siguiente</button>
      </div>
    </section>
  `,
  styles: [`
    .filter-actions {
      align-self: center;
    }

    .item-main,
    .item-meta {
      display: grid;
      gap: 6px;
    }

    .item-main span,
    .meta-text {
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.4;
    }

    .item-meta {
      justify-items: end;
      align-items: start;
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      color: #5a6677;
    }

    @media (max-width: 720px) {
      .item-meta {
        justify-items: start;
      }
    }
  `]
})
export class VenuesPageComponent implements OnInit {
  private readonly api = inject(VenuesApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly masterData = inject(AcademicMasterDataService);
  readonly navigationContext = inject(NavigationContextService);

  readonly result = signal<PageResponse<Venue> | null>(null);
  readonly currentPage = signal(0);
  readonly venueTypeCodes = computed(() => this.masterData.venueTypeCodes());
  readonly canManageMasterData = () => this.auth.hasAnyRole(['ADMIN']);

  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    typeCode: new FormControl('all', { nonNullable: true }),
    active: new FormControl<ActiveFilter>('all', { nonNullable: true })
  });

  ngOnInit(): void {
    this.masterData.ensureLoaded();

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const page = Math.max(this.toNumber(params.get('page')) ?? 0, 0);
        const text = params.get('text') ?? '';
        const typeCode = params.get('typeCode') ?? 'all';
        const active = this.toActiveFilter(params.get('active'));

        this.currentPage.set(page);
        this.filterForm.patchValue({ text, typeCode, active }, { emitEvent: false });

        this.api.search({
          page,
          text: text || undefined,
          typeCode: typeCode === 'all' ? undefined : typeCode,
          active: active === 'all' ? undefined : active === 'true'
        })
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((result) => this.result.set(result));
      });
  }

  applyFilters(): void {
    const value = this.filterForm.getRawValue();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: null,
        text: value.text || null,
        typeCode: value.typeCode === 'all' ? null : value.typeCode,
        active: value.active === 'all' ? null : value.active
      },
      queryParamsHandling: 'merge'
    });
  }

  clearFilters(): void {
    this.filterForm.reset({ text: '', typeCode: 'all', active: 'all' });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: null, text: null, typeCode: null, active: null },
      queryParamsHandling: 'merge'
    });
  }

  goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page === 0 ? null : page },
      queryParamsHandling: 'merge'
    });
  }

  venueTypeLabel(code: string): string {
    return this.masterData.venueTypeLabel(code);
  }

  statusLabel(status: Venue['validationStatus']): string {
    return validationStatusLabel(status);
  }

  statusTone(status: Venue['validationStatus']): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    return validationStatusTone(status);
  }

  identifierLabel(venue: Venue): string {
    return [venue.issn, venue.eissn, venue.isbn].filter(Boolean).join(' · ') || 'Sin ISSN/ISBN';
  }

  venueLink(venueId: number): string[] {
    return this.navigationContext.isCurrentPath('/admin/canales') ? ['/admin/canales', String(venueId)] : ['/venues', String(venueId)];
  }

  newVenueLink(): string[] {
    return this.navigationContext.isCurrentPath('/admin/canales') ? ['/admin/canales/new'] : ['/venues/new'];
  }

  private toNumber(value: string | null): number | undefined {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }

  private toActiveFilter(value: string | null): ActiveFilter {
    return value === 'true' || value === 'false' ? value : 'all';
  }
}
