import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { PublicationFilterMetadata } from '../../core/api/api-models';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { MetricCardComponent } from '../../shared/components/metric-card.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { VisibilityNoteComponent } from '../../shared/components/visibility-note.component';
import {
  StrategicResearchLine,
  StrategicResearchMapData,
  StrategicResearchMapFilters,
  StrategicResearchMapPageContext
} from './strategic-research-map.models';
import { StrategicResearchMapService } from './strategic-research-map.service';

@Component({
  selector: 'rip-strategic-research-map-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    MetricCardComponent,
    PageHeaderComponent,
    TagChipComponent,
    VisibilityNoteComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Mapa estratégico"
        subtitle="Lectura temática interna del conocimiento validado a partir de publicaciones, investigadores y unidades."
        eyebrow="Inteligencia institucional"
      />

      <rip-visibility-note message="Mostrando solo datos validados" emphasis="strong" />

      <mat-card appearance="outlined" class="filter-card">
        <mat-card-content>
          <div class="filter-header">
            <div>
              <p class="section-kicker">Filtros del mapa</p>
              <p class="filter-copy">Refina la lectura estratégica por rango temporal y por unidad visible.</p>
            </div>
            <mat-checkbox [checked]="true" disabled>Solo datos validados</mat-checkbox>
          </div>

          <form class="filter-grid" [formGroup]="filterForm" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline">
              <mat-label>Año desde</mat-label>
              <input matInput type="number" formControlName="yearFrom" [placeholder]="yearPlaceholder('min')">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Año hasta</mat-label>
              <input matInput type="number" formControlName="yearTo" [placeholder]="yearPlaceholder('max')">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Unidad de investigación</mat-label>
              <mat-select formControlName="researchUnitId">
                <mat-option value="all">Todas las unidades</mat-option>
                @for (unit of metadata().researchUnits; track unit.label) {
                  <mat-option [value]="unit.id?.toString() || 'all'">{{ unit.label }} ({{ unit.count }})</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <div class="filter-actions">
              <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
              <button mat-flat-button color="primary" type="submit" [disabled]="loading()">Aplicar</button>
            </div>
          </form>

          @if (filterMessage()) {
            <div class="filter-message">{{ filterMessage() }}</div>
          }
        </mat-card-content>
      </mat-card>

      @if (loading() && !hasLoadedOnce()) {
        <rip-loading-state message="Construyendo el mapa estratégico visible" />
      } @else if (errorMessage()) {
        <rip-error-state [message]="errorMessage()" />
      } @else {
        <section class="metric-grid">
          <rip-metric-card label="Líneas detectadas" [value]="overview().lineCount" hint="agrupaciones temáticas visibles" />
          <rip-metric-card label="Publicaciones base" [value]="overview().publicationCount" hint="producción usada en el mapa" />
          <rip-metric-card label="Investigadores conectados" [value]="overview().researcherCount" hint="perfiles vinculados a las líneas" />
          <rip-metric-card label="Unidades implicadas" [value]="overview().unitCount" hint="estructura visible en la evidencia" />
        </section>

        @if (loading() && hasLoadedOnce()) {
          <rip-loading-state message="Actualizando líneas con los filtros seleccionados" />
        }

        @if (lines().length === 0 && !loading()) {
          <rip-empty-state
            title="Sin líneas estratégicas visibles"
            message="Prueba con un rango temporal más amplio o quita el filtro de unidad para recuperar evidencia validada."
          />
        } @else {
          <section class="line-list">
            @for (line of lines(); track line.id) {
              <mat-card appearance="outlined" class="line-card">
                <mat-card-content>
                  <div class="line-top">
                    <div class="line-copy">
                      <div class="line-heading">
                        <p class="section-kicker">Línea {{ $index + 1 }}</p>
                        <div class="line-badges">
                          <span class="pill" [class.strong]="line.confidenceTone === 'strong'" [class.medium]="line.confidenceTone === 'medium'" [class.low]="line.confidenceTone === 'low'">
                            {{ line.confidenceLabel }}
                          </span>
                          <span class="pill trend" [class.up]="line.trend.direction === 'up'" [class.down]="line.trend.direction === 'down'" [class.limited]="line.trend.direction === 'limited'">
                            {{ line.trend.label }}
                          </span>
                        </div>
                      </div>
                      <h2>{{ line.title }}</h2>
                      <p class="line-description">{{ line.description }}</p>
                    </div>

                    <button mat-stroked-button type="button" (click)="toggleLine(line.id)">
                      {{ expandedLineId() === line.id ? 'Ocultar detalle' : 'Ver detalle' }}
                    </button>
                  </div>

                  <div class="line-metrics">
                    <div>
                      <strong>{{ line.publicationCount }}</strong>
                      <span>publicaciones</span>
                    </div>
                    <div>
                      <strong>{{ line.researchers.length }}</strong>
                      <span>investigadores</span>
                    </div>
                    <div>
                      <strong>{{ line.units.length }}</strong>
                      <span>unidades</span>
                    </div>
                  </div>

                  @if (line.warnings.length > 0) {
                    <div class="warning-list">
                      @for (warning of line.warnings; track warning) {
                        <p>{{ warning }}</p>
                      }
                    </div>
                  }

                  <div class="summary-grid">
                    <div class="summary-block">
                      <h3>Temas</h3>
                      <div class="chip-list">
                        @for (topic of line.topics; track topic.label) {
                          <a class="topic-link" [routerLink]="['/admin/publicaciones']" [queryParams]="{ topic: topic.label }">
                            <rip-tag-chip [label]="topic.label + ' (' + topic.count + ')'" />
                          </a>
                        }
                      </div>
                    </div>

                    <div class="summary-block">
                      <h3>Publicaciones representativas</h3>
                      <div class="reference-list compact">
                        @for (publication of line.representativePublications; track publication.id) {
                          <a class="reference-link" [routerLink]="['/admin/publicaciones', publication.id]" [queryParams]="navigationContext.returnQueryParams('Volver al mapa estratégico')">
                            <strong>{{ publication.title }}</strong>
                            <span>{{ publication.year || 's. f.' }} · {{ publication.source || 'Repositorio institucional' }}</span>
                          </a>
                        }
                      </div>
                    </div>
                  </div>

                  @if (expandedLineId() === line.id) {
                    <section class="detail-grid">
                      <div class="detail-panel">
                        <h3>Publicaciones</h3>
                        <div class="reference-list">
                          @for (publication of line.publications; track publication.id) {
                            <a class="reference-link" [routerLink]="['/admin/publicaciones', publication.id]" [queryParams]="navigationContext.returnQueryParams('Volver al mapa estratégico')">
                              <strong>{{ publication.title }}</strong>
                              <span>{{ publication.year || 's. f.' }} · {{ publication.source || 'Repositorio institucional' }}</span>
                              <div class="chip-list">
                                @for (topic of publication.topics; track topic) {
                                  <rip-tag-chip [label]="topic" />
                                }
                              </div>
                            </a>
                          }
                        </div>
                      </div>

                      <div class="detail-panel">
                        <h3>Investigadores</h3>
                        <div class="person-list">
                          @for (researcher of line.researchers; track researcher.id) {
                            <a class="person-row" [routerLink]="['/admin/investigadores', researcher.id]" [queryParams]="navigationContext.returnQueryParams('Volver al mapa estratégico')">
                              <div>
                                <strong>{{ researcher.name }}</strong>
                                <span>{{ researcher.primaryAffiliation || 'Afiliación visible pendiente' }}</span>
                              </div>
                              <small>{{ researcher.publicationCount }} pubs.</small>
                            </a>
                          }
                        </div>
                      </div>

                      <div class="detail-panel">
                        <h3>Unidades</h3>
                        <div class="unit-list">
                          @for (unit of line.units; track unit.name) {
                            <div class="unit-row">
                              <strong>{{ unit.name }}</strong>
                              <span>{{ unit.publicationCount }} publicaciones · {{ unit.researcherCount }} investigadores</span>
                            </div>
                          }
                        </div>
                      </div>

                      <div class="detail-panel">
                        <h3>Líneas relacionadas</h3>
                        @if (line.relatedLines.length === 0) {
                          <p class="detail-empty">No se detectan líneas cercanas con suficiente solapamiento visible.</p>
                        } @else {
                          <div class="related-list">
                            @for (relatedLine of line.relatedLines; track relatedLine.id) {
                              <button mat-button type="button" class="related-button" (click)="toggleLine(relatedLine.id)">
                                {{ relatedLine.title }} · {{ relatedLine.sharedTopics }} temas · {{ relatedLine.sharedPublications }} pubs.
                              </button>
                            }
                          </div>
                        }
                      </div>

                      <div class="detail-panel detail-panel-wide">
                        <h3>Evidencia y citas</h3>
                        <p class="trend-copy">{{ line.trend.detail }}</p>
                        <div class="evidence-list">
                          @for (evidence of line.evidence; track evidence.publicationId) {
                            <a class="evidence-item" [routerLink]="['/admin/publicaciones', evidence.publicationId]" [queryParams]="navigationContext.returnQueryParams('Volver al mapa estratégico')">
                              <strong>{{ evidence.title }}</strong>
                              <span>{{ evidence.note }}</span>
                            </a>
                          }
                        </div>
                      </div>
                    </section>
                  }
                </mat-card-content>
              </mat-card>
            }
          </section>
        }
      }
    </section>
  `,
  styles: [`
    .filter-card mat-card-content,
    .line-card mat-card-content {
      display: grid;
      gap: 18px;
    }

    .filter-header,
    .line-top {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 16px;
    }

    .filter-copy,
    .line-description,
    .trend-copy,
    .detail-empty {
      margin: 0;
      color: #627487;
      line-height: 1.6;
    }

    .filter-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
      gap: 12px;
      align-items: start;
    }

    .filter-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
      padding-top: 6px;
    }

    .filter-message {
      padding: 12px 14px;
      border: 1px solid #efd18b;
      border-radius: 14px;
      background: #fff9e9;
      color: #72510d;
      font-size: 0.9rem;
      line-height: 1.45;
    }

    .metric-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 18px;
    }

    .line-list {
      display: grid;
      gap: 18px;
    }

    .line-card {
      border-radius: 24px;
    }

    .line-copy,
    .summary-block,
    .detail-panel {
      display: grid;
      gap: 12px;
      min-width: 0;
    }

    .line-heading {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }

    .line-badges,
    .chip-list,
    .related-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    h2,
    h3 {
      margin: 0;
      color: #142033;
    }

    h2 {
      font-size: 1.4rem;
      line-height: 1.15;
    }

    h3 {
      font-size: 1rem;
      line-height: 1.3;
    }

    .pill {
      display: inline-flex;
      align-items: center;
      min-height: 30px;
      padding: 0 12px;
      border-radius: 999px;
      border: 1px solid #dbe4eb;
      background: #f7fbff;
      color: #355064;
      font-size: 0.82rem;
      font-weight: 760;
      white-space: nowrap;
    }

    .pill.strong {
      border-color: #c9e6d9;
      background: #eefaf5;
      color: #17634f;
    }

    .pill.medium {
      border-color: #dbe4eb;
      background: #f7fbff;
      color: #355064;
    }

    .pill.low,
    .pill.limited,
    .pill.down {
      border-color: #efd18b;
      background: #fff8e6;
      color: #8a5d0a;
    }

    .pill.up {
      border-color: #c9e6d9;
      background: #eefaf5;
      color: #17634f;
    }

    .line-metrics {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px;
    }

    .line-metrics div {
      display: grid;
      gap: 4px;
      padding: 14px 16px;
      border: 1px solid #e0e8ee;
      border-radius: 18px;
      background: #fbfdff;
    }

    .line-metrics strong {
      color: #102033;
      font-size: 1.3rem;
      line-height: 1;
    }

    .line-metrics span,
    .reference-link span,
    .person-row span,
    .unit-row span,
    .evidence-item span {
      color: #667487;
      font-size: 0.88rem;
      line-height: 1.45;
    }

    .warning-list {
      display: grid;
      gap: 8px;
      padding: 14px 16px;
      border: 1px solid #efd18b;
      border-radius: 18px;
      background: #fffaf0;
    }

    .warning-list p {
      margin: 0;
      color: #7a5812;
      line-height: 1.45;
    }

    .summary-grid,
    .detail-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 18px;
    }

    .detail-panel {
      padding: 18px;
      border: 1px solid #e0e8ee;
      border-radius: 20px;
      background: #fbfcfe;
    }

    .detail-panel-wide {
      grid-column: 1 / -1;
    }

    .reference-list,
    .person-list,
    .unit-list,
    .evidence-list {
      display: grid;
      gap: 12px;
    }

    .reference-list.compact {
      gap: 10px;
    }

    .reference-link,
    .person-row,
    .evidence-item,
    .topic-link {
      color: inherit;
      text-decoration: none;
    }

    .reference-link,
    .person-row,
    .evidence-item,
    .unit-row {
      display: grid;
      gap: 6px;
      padding: 14px;
      border: 1px solid #e0e8ee;
      border-radius: 16px;
      background: #ffffff;
    }

    .person-row {
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: start;
      gap: 12px;
    }

    .reference-link strong,
    .person-row strong,
    .unit-row strong,
    .evidence-item strong {
      color: #142033;
      line-height: 1.35;
    }

    .person-row small {
      color: #355064;
      font-size: 0.82rem;
      font-weight: 760;
      white-space: nowrap;
    }

    .related-button {
      border-radius: 999px;
    }

    @media (max-width: 980px) {
      .metric-grid,
      .filter-grid,
      .summary-grid,
      .detail-grid {
        grid-template-columns: 1fr;
      }

      .filter-actions {
        justify-content: flex-start;
      }
    }

    @media (max-width: 720px) {
      .line-top,
      .filter-header,
      .line-heading,
      .person-row {
        display: grid;
      }

      .line-metrics {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class StrategicResearchMapPageComponent implements OnInit {
  private readonly service = inject(StrategicResearchMapService);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly context = signal<StrategicResearchMapPageContext | null>(null);
  readonly mapData = signal<StrategicResearchMapData>({ overview: { lineCount: 0, publicationCount: 0, researcherCount: 0, unitCount: 0 }, lines: [] });
  readonly loading = signal(true);
  readonly hasLoadedOnce = signal(false);
  readonly errorMessage = signal('');
  readonly filterMessage = signal('');
  readonly expandedLineId = signal<string | null>(null);
  readonly metadata = computed<PublicationFilterMetadata>(() => this.context()?.metadata ?? this.emptyMetadata());
  readonly overview = computed(() => this.mapData().overview);
  readonly lines = computed(() => this.mapData().lines);

  readonly filterForm = new FormGroup({
    yearFrom: new FormControl('', { nonNullable: true }),
    yearTo: new FormControl('', { nonNullable: true }),
    researchUnitId: new FormControl('all', { nonNullable: true })
  });

  ngOnInit(): void {
    this.service.loadPageContext()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (context) => {
          this.context.set(context);
          this.reloadMap();
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No se pudo cargar el contexto base del mapa estratégico.');
        }
      });
  }

  applyFilters(): void {
    const filters = this.currentFilters();
    if (filters.yearFrom !== null && filters.yearTo !== null && filters.yearFrom > filters.yearTo) {
      this.filterMessage.set('El año inicial no puede ser mayor que el año final.');
      return;
    }
    this.filterMessage.set('');
    this.reloadMap();
  }

  clearFilters(): void {
    this.filterForm.reset({ yearFrom: '', yearTo: '', researchUnitId: 'all' });
    this.filterMessage.set('');
    this.reloadMap();
  }

  toggleLine(lineId: string): void {
    this.expandedLineId.set(this.expandedLineId() === lineId ? null : lineId);
  }

  yearPlaceholder(bound: 'min' | 'max'): string {
    const metadata = this.metadata();
    const year = bound === 'min' ? metadata.minYear : metadata.maxYear;
    return year?.toString() ?? '';
  }

  private reloadMap(): void {
    const context = this.context();
    if (!context) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');

    this.service.loadMapData(this.currentFilters())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (mapData) => {
          this.mapData.set(mapData);
          if (!this.lines().some((line) => line.id === this.expandedLineId())) {
            this.expandedLineId.set(this.lines()[0]?.id ?? null);
          }
          this.loading.set(false);
          this.hasLoadedOnce.set(true);
        },
        error: () => {
          this.loading.set(false);
          this.hasLoadedOnce.set(true);
          this.errorMessage.set('No se pudo construir el mapa estratégico con los filtros seleccionados.');
        }
      });
  }

  private currentFilters(): StrategicResearchMapFilters {
    return {
      yearFrom: this.toNumber(this.filterForm.controls.yearFrom.value),
      yearTo: this.toNumber(this.filterForm.controls.yearTo.value),
      researchUnitId: this.toNumber(this.filterForm.controls.researchUnitId.value),
      onlyValidated: true
    };
  }

  private toNumber(value: string): number | null {
    if (!value || value === 'all') {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private emptyMetadata(): PublicationFilterMetadata {
    return {
      minYear: null,
      maxYear: null,
      availableTypes: [],
      availableStatuses: [],
      researchUnits: [],
      topics: []
    };
  }
}
