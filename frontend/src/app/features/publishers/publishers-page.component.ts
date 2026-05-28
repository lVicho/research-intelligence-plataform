import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { PageResponse, Publisher } from '../../core/api/api-models';
import { PublishersApiService } from '../../core/api/publishers-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';

type ActiveFilter = 'all' | 'true' | 'false';

@Component({
  selector: 'rip-publishers-page',
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
        title="Editoriales"
        [subtitle]="(result()?.totalElements || 0) + ' editoriales registradas para publicaciones y canales.'"
        eyebrow="Difusión académica"
      >
        @if (canManageMasterData()) {
          <a mat-flat-button color="primary" [routerLink]="newPublisherLink()">Nueva editorial</a>
        }
      </rip-page-header>

      <mat-card appearance="outlined">
        <mat-card-content>
          <p class="section-kicker">Filtros</p>
          <form class="form-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline">
              <mat-label>Buscar</mat-label>
              <input matInput formControlName="text" placeholder="Nombre, país, web o descripción">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Estado</mat-label>
              <mat-select formControlName="active">
                <mat-option value="all">Todas</mat-option>
                <mat-option value="true">Activas</mat-option>
                <mat-option value="false">Inactivas</mat-option>
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
            <rip-empty-state title="Sin editoriales" message="No hay editoriales que coincidan con los filtros actuales." />
          } @else {
            <div class="item-list">
              @for (publisher of result()?.content || []; track publisher.id) {
                <a class="item-row interactive" [routerLink]="publisherLink(publisher.id)" [queryParams]="navigationContext.returnQueryParams('Volver a editoriales')">
                  <div class="item-main">
                    <strong class="item-title">{{ publisher.name }}</strong>
                    <span>{{ publisher.country || 'País no indicado' }}</span>
                    <span>{{ publisher.website || descriptionPreview(publisher.description) }}</span>
                  </div>
                  <div class="item-meta">
                    <rip-status-chip [label]="publisher.active ? 'Activa' : 'Inactiva'" [tone]="publisher.active ? 'success' : 'neutral'" />
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

    .item-main span {
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
export class PublishersPageComponent implements OnInit {
  private readonly api = inject(PublishersApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  readonly navigationContext = inject(NavigationContextService);

  readonly result = signal<PageResponse<Publisher> | null>(null);
  readonly currentPage = signal(0);
  readonly canManageMasterData = () => this.auth.hasAnyRole(['ADMIN']);

  readonly filterForm = new FormGroup({
    text: new FormControl('', { nonNullable: true }),
    active: new FormControl<ActiveFilter>('all', { nonNullable: true })
  });

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const page = Math.max(this.toNumber(params.get('page')) ?? 0, 0);
        const text = params.get('text') ?? '';
        const active = this.toActiveFilter(params.get('active'));

        this.currentPage.set(page);
        this.filterForm.patchValue({ text, active }, { emitEvent: false });

        this.api.search({
          page,
          text: text || undefined,
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
        active: value.active === 'all' ? null : value.active
      },
      queryParamsHandling: 'merge'
    });
  }

  clearFilters(): void {
    this.filterForm.reset({ text: '', active: 'all' });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: null, text: null, active: null },
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

  descriptionPreview(value: string | null): string {
    if (!value) {
      return 'Sin descripción';
    }
    return value.length > 96 ? `${value.slice(0, 93)}...` : value;
  }

  publisherLink(publisherId: number): string[] {
    return ['/admin/editoriales', String(publisherId)];
  }

  newPublisherLink(): string[] {
    return ['/admin/editoriales/new'];
  }

  private toNumber(value: string | null): number | undefined {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }

  private toActiveFilter(value: string | null): ActiveFilter {
    return value === 'true' || value === 'false' ? value : 'all';
  }
}
