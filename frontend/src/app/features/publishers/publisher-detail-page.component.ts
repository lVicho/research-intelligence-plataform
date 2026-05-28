import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { Publisher, PublisherRequest } from '../../core/api/api-models';
import { PublishersApiService } from '../../core/api/publishers-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { contentAccessErrorMessage } from '../../shared/utils/api-error-messages';

@Component({
  selector: 'rip-publisher-detail-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    ErrorStateComponent,
    PageHeaderComponent,
    StatusChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        [title]="publisherId() === null ? 'Nueva editorial' : (currentPublisher()?.name || 'Detalle de editorial')"
        [subtitle]="publisherId() === null ? 'Crear una editorial reutilizable en publicaciones y canales.' : publisherSubtitle()"
        eyebrow="Editoriales"
      >
        <button mat-button type="button" (click)="navigateBack()">{{ backLabel() }}</button>
        @if (canManageMasterData()) {
          <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="publisherForm.invalid || saving()">Guardar</button>
        }
      </rip-page-header>

      @if (loadError()) {
        <rip-error-state [message]="loadError()" />
      }

      @if (currentPublisher(); as publisher) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <div class="summary-header">
              <div>
                <h2>{{ publisher.name }}</h2>
                <p>{{ publisher.country || 'País no indicado' }}</p>
              </div>
              <rip-status-chip [label]="publisher.active ? 'Activa' : 'Inactiva'" [tone]="publisher.active ? 'success' : 'neutral'" />
            </div>

            <div class="metadata-grid">
              <div class="metadata-item">
                <span>País</span>
                <strong>{{ publisher.country || 'Sin país' }}</strong>
              </div>
              <div class="metadata-item">
                <span>Sitio web</span>
                <strong>{{ publisher.website || 'Sin sitio web' }}</strong>
              </div>
            </div>

            @if (publisher.description) {
              <p class="description">{{ publisher.description }}</p>
            }
          </mat-card-content>
        </mat-card>
      }

      @if (canManageMasterData() && !loadError()) {
        <mat-card appearance="outlined">
          <mat-card-content>
            <form class="form-grid" [formGroup]="publisherForm">
              <mat-form-field appearance="outline">
                <mat-label>Nombre</mat-label>
                <input matInput formControlName="name">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>País</mat-label>
                <input matInput formControlName="country">
              </mat-form-field>
              <mat-form-field appearance="outline" class="wide-field">
                <mat-label>Sitio web</mat-label>
                <input matInput formControlName="website">
              </mat-form-field>
              <mat-form-field appearance="outline" class="wide-field">
                <mat-label>Descripción</mat-label>
                <textarea matInput rows="5" formControlName="description"></textarea>
              </mat-form-field>
              <mat-checkbox formControlName="active">Activa</mat-checkbox>
            </form>
          </mat-card-content>
        </mat-card>
      }
    </section>
  `,
  styles: [`
    .summary-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 20px;
    }

    .summary-header h2 {
      margin: 0;
      color: #142033;
      font-size: 1.2rem;
      font-weight: 760;
    }

    .summary-header p,
    .description {
      margin: 6px 0 0;
      color: #667487;
      line-height: 1.55;
    }

    .description {
      margin-top: 20px;
    }

    .wide-field {
      grid-column: 1 / -1;
    }

    @media (max-width: 720px) {
      .summary-header {
        display: grid;
      }
    }
  `]
})
export class PublisherDetailPageComponent implements OnInit {
  private readonly api = inject(PublishersApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthStateService);
  private readonly navigationContext = inject(NavigationContextService);

  readonly publisherId = signal<number | null>(null);
  readonly currentPublisher = signal<Publisher | null>(null);
  readonly loadError = signal('');
  readonly saving = signal(false);
  readonly canManageMasterData = () => this.auth.hasAnyRole(['ADMIN']);

  readonly publisherForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    country: new FormControl<string | null>(null),
    website: new FormControl<string | null>(null),
    description: new FormControl<string | null>(null),
    active: new FormControl(true, { nonNullable: true })
  });

  ngOnInit(): void {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.loadPublisher(this.parseId(params.get('id'))));
  }

  save(): void {
    if (!this.canManageMasterData() || this.publisherForm.invalid || this.saving()) {
      return;
    }
    const request = this.toRequest();
    const publisherId = this.publisherId();
    this.saving.set(true);
    const operation = publisherId === null ? this.api.create(request) : this.api.update(publisherId, request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (publisher) => {
        this.saving.set(false);
        void this.router.navigate(['/admin/editoriales', String(publisher.id)]);
      },
      error: () => this.saving.set(false)
    });
  }

  publisherSubtitle(): string {
    const publisher = this.currentPublisher();
    if (!publisher) {
      return 'Detalle de editorial';
    }
    return [publisher.country, publisher.website].filter(Boolean).join(' · ') || 'Detalle de editorial';
  }

  backLabel(): string {
    return this.navigationContext.resolve(this.route, '/admin/editoriales', 'Volver a editoriales').label;
  }

  navigateBack(): void {
    this.navigationContext.navigateBack(this.route, '/admin/editoriales', 'Volver a editoriales');
  }

  private loadPublisher(publisherId: number | null): void {
    this.publisherId.set(publisherId);
    this.loadError.set('');

    if (publisherId === null) {
      this.currentPublisher.set(null);
      this.publisherForm.reset({
        name: '',
        country: '',
        website: '',
        description: '',
        active: true
      });
      return;
    }

    this.api.get(publisherId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (publisher) => {
          this.currentPublisher.set(publisher);
          this.publisherForm.patchValue({
            name: publisher.name,
            country: publisher.country,
            website: publisher.website,
            description: publisher.description,
            active: publisher.active
          });
        },
        error: (error: unknown) => {
          this.currentPublisher.set(null);
          this.loadError.set(contentAccessErrorMessage(error, 'No se pudo cargar la editorial.'));
        }
      });
  }

  private toRequest(): PublisherRequest {
    const value = this.publisherForm.getRawValue();
    return {
      name: value.name,
      country: this.emptyToNull(value.country),
      website: this.emptyToNull(value.website),
      description: this.emptyToNull(value.description),
      active: value.active
    };
  }

  private parseId(value: string | null): number | null {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private emptyToNull(value: string | null): string | null {
    return value === null || value.trim() === '' ? null : value;
  }
}
