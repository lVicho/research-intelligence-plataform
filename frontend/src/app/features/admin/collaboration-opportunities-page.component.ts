import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import {
  CollaborationOpportunity,
  CollaborationOpportunityMode,
  CollaborationOpportunityResponse,
  ResearchUnitType
} from '../../core/api/api-models';
import {
  CollaborationOpportunityDemoState,
  CollaborationOpportunitiesApiService
} from '../../core/api/collaboration-opportunities-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { MetricCardComponent } from '../../shared/components/metric-card.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';

interface SummaryCard {
  label: string;
  value: number | string;
  hint: string;
}

@Component({
  selector: 'rip-collaboration-opportunities-page',
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
    ErrorStateComponent,
    LoadingStateComponent,
    MetricCardComponent,
    PageHeaderComponent,
    TagChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Oportunidades de colaboración"
        subtitle="Detecta pares de unidades con afinidad temática y complementariedad metodológica para priorizar conversaciones institucionales."
        eyebrow="Analítica institucional"
      >
        <a mat-button routerLink="/admin/panel">Panel institucional</a>
        <a mat-button routerLink="/admin/mapa-estrategico">Mapa estratégico</a>
      </rip-page-header>

      <div class="surface-intro intro-grid">
        <div class="intro-copy">
          <h2>Senales listas para revision administrativa</h2>
          <p>{{ visibilityNote() }}</p>
        </div>
        <div class="intro-meta">
          <span>Ultima generacion</span>
          <strong>{{ generatedAtLabel() }}</strong>
        </div>
      </div>

      <mat-card appearance="outlined">
        <mat-card-content>
          <div class="section-header compact-header">
            <div>
              <p class="section-kicker">Controles</p>
              <h2>Explora el rango y la amplitud de recomendacion</h2>
              <p>El modo estricto exige mayor confianza; el amplio deja pasar senales tempranas para revision exploratoria.</p>
            </div>
            <button mat-button type="button" (click)="resetFilters()">Restablecer</button>
          </div>

          <form class="form-grid" [formGroup]="filterForm">
            <mat-form-field appearance="outline">
              <mat-label>Año inicial</mat-label>
              <input matInput type="number" formControlName="fromYear" [min]="availableMinYear()" [max]="availableMaxYear()">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Año final</mat-label>
              <input matInput type="number" formControlName="toYear" [min]="availableMinYear()" [max]="availableMaxYear()">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Modo</mat-label>
              <mat-select formControlName="mode">
                @for (mode of modes; track mode.value) {
                  <mat-option [value]="mode.value">{{ mode.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Limite</mat-label>
              <mat-select formControlName="limit">
                @for (option of limitOptions; track option) {
                  <mat-option [value]="option">{{ option }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </form>

          @if (!yearRangeValid()) {
            <p class="validation-message">El año inicial no puede ser mayor que el año final.</p>
          }
        </mat-card-content>
      </mat-card>

      @if (loading() && !response()) {
        <rip-loading-state message="Cargando oportunidades de colaboración..." />
      } @else if (errorMessage()) {
        <mat-card appearance="outlined">
          <mat-card-content class="state-card">
            <rip-error-state
              title="No se pudieron cargar las oportunidades"
              [message]="errorMessage()"
            />
            <div class="actions">
              <button mat-flat-button color="primary" type="button" (click)="reload()">Reintentar</button>
            </div>
          </mat-card-content>
        </mat-card>
      } @else {
        <div class="metric-grid">
          @for (card of summaryCards(); track card.label) {
            <rip-metric-card [label]="card.label" [value]="card.value" [hint]="card.hint" />
          }
        </div>

        <mat-card appearance="outlined">
          <mat-card-content class="results-card">
            <div class="section-header compact-header">
              <div>
                <p class="section-kicker">Pares sugeridos</p>
                <h2>{{ opportunities().length }} oportunidades visibles</h2>
                <p>La explicacion combina afinidad tematica, complementariedad y colaboracion ya existente para orientar la conversacion.</p>
              </div>
              @if (loading()) {
                <span class="muted">Actualizando...</span>
              }
            </div>

            @if (opportunities().length === 0) {
              <rip-empty-state
                title="Sin oportunidades para estos filtros"
                message="Prueba ampliar el rango de años, cambiar a modo amplio o subir el límite para revisar más pares."
              />
            } @else {
              <div class="opportunity-list">
                @for (item of opportunities(); track item.id) {
                  <article class="opportunity-card">
                    <div class="opportunity-topline">
                      <div class="units-grid">
                        <section class="unit-panel">
                          <span>Unidad A</span>
                          <strong>{{ item.unitA.name }}</strong>
                          <small>{{ unitTypeLabel(item.unitA.type) }}{{ item.unitA.shortName ? ' · ' + item.unitA.shortName : '' }}</small>
                        </section>

                        <section class="unit-panel">
                          <span>Unidad B</span>
                          <strong>{{ item.unitB.name }}</strong>
                          <small>{{ unitTypeLabel(item.unitB.type) }}{{ item.unitB.shortName ? ' · ' + item.unitB.shortName : '' }}</small>
                        </section>
                      </div>

                      <div class="score-panel">
                        <span>Puntuacion</span>
                        <strong>{{ item.score }}</strong>
                        <small>{{ confidenceLabel(item.confidence) }}</small>
                      </div>
                    </div>

                    <div class="metadata-grid">
                      <div class="metadata-item">
                        <span>Confianza</span>
                        <strong>{{ percentLabel(item.confidence) }}</strong>
                      </div>
                      <div class="metadata-item">
                        <span>Colaboraciones existentes</span>
                        <strong>{{ item.existingCollaborationCount }}</strong>
                      </div>
                      <div class="metadata-item">
                        <span>Ventana temporal</span>
                        <strong>{{ item.fromYear }} - {{ item.toYear }}</strong>
                      </div>
                    </div>

                    <div class="topic-sections">
                      <section class="topic-block">
                        <p class="topic-label">Temas compartidos</p>
                        <div class="chip-list">
                          @for (topic of item.sharedTopics; track topic) {
                            <rip-tag-chip [label]="topic" />
                          }
                        </div>
                      </section>

                      <section class="topic-block">
                        <p class="topic-label">Temas complementarios</p>
                        <div class="chip-list">
                          @for (topic of item.complementaryTopics; track topic) {
                            <rip-tag-chip [label]="topic" tone="type" />
                          }
                        </div>
                      </section>
                    </div>

                    <section class="publication-block">
                      <p class="topic-label">Publicaciones representativas</p>
                      <div class="publication-list">
                        @for (publication of item.representativePublications; track publication.id) {
                          <a class="publication-item" [routerLink]="publication.path" [queryParams]="navigationContext.returnQueryParams('Volver a oportunidades de colaboración')">
                            <strong>{{ publication.title }}</strong>
                            <span>{{ publication.year || 'Sin año' }} · {{ publication.source || 'Repositorio institucional' }}</span>
                          </a>
                        }
                      </div>
                    </section>

                    <section class="explanation-block">
                      <p class="topic-label">Explicacion</p>
                      <p>{{ item.explanation }}</p>
                    </section>
                  </article>
                }
              </div>
            }
          </mat-card-content>
        </mat-card>
      }
    </section>
  `,
  styles: [`
    .intro-grid,
    .state-card,
    .results-card {
      display: grid;
      gap: 20px;
    }

    .intro-grid {
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
    }

    .intro-copy {
      display: grid;
      gap: 10px;
    }

    .intro-meta {
      display: grid;
      gap: 6px;
      min-width: 190px;
      padding: 16px 18px;
      border: 1px solid #d9e5ee;
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.86);
    }

    .intro-meta span,
    .topic-label,
    .unit-panel span,
    .score-panel span {
      color: #466274;
      font-size: 0.77rem;
      font-weight: 780;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .intro-meta strong {
      color: #142033;
      font-size: 1rem;
    }

    .compact-header {
      margin-bottom: 0;
    }

    .validation-message {
      margin: 4px 0 0;
      color: #9b1c1c;
      font-size: 0.9rem;
    }

    .opportunity-list {
      display: grid;
      gap: 16px;
    }

    .opportunity-card {
      display: grid;
      gap: 18px;
      padding: 20px;
      border: 1px solid #dce5ee;
      border-radius: 20px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(248, 251, 253, 0.96)),
        linear-gradient(90deg, rgba(31, 111, 139, 0.05), rgba(45, 140, 120, 0.04));
    }

    .opportunity-topline {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 20px;
    }

    .units-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
      flex: 1;
    }

    .unit-panel,
    .score-panel {
      display: grid;
      gap: 8px;
      min-width: 0;
      padding: 16px 18px;
      border: 1px solid #dbe6ef;
      border-radius: 18px;
      background: #ffffff;
    }

    .unit-panel strong,
    .score-panel strong {
      color: #132236;
      font-size: 1.08rem;
      line-height: 1.3;
    }

    .unit-panel small,
    .score-panel small {
      color: #667487;
      line-height: 1.4;
    }

    .score-panel {
      min-width: 156px;
      text-align: right;
    }

    .score-panel strong {
      font-size: 2rem;
      line-height: 1;
    }

    .topic-sections {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }

    .topic-block,
    .publication-block,
    .explanation-block {
      display: grid;
      gap: 12px;
    }

    .publication-list {
      display: grid;
      gap: 10px;
    }

    .publication-item {
      display: grid;
      gap: 5px;
      padding: 14px 16px;
      border: 1px solid #dce5ee;
      border-radius: 16px;
      background: #ffffff;
      color: inherit;
      text-decoration: none;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .publication-item:hover {
      border-color: #9fc0d1;
      box-shadow: 0 12px 24px rgba(20, 32, 51, 0.08);
      transform: translateY(-2px);
    }

    .publication-item strong {
      color: #142033;
      line-height: 1.35;
    }

    .publication-item span,
    .explanation-block p {
      margin: 0;
      color: #667487;
      line-height: 1.6;
    }

    @media (max-width: 960px) {
      .intro-grid,
      .topic-sections {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 760px) {
      .opportunity-topline,
      .units-grid {
        display: grid;
      }

      .score-panel {
        min-width: 0;
        text-align: left;
      }
    }
  `]
})
export class CollaborationOpportunitiesPageComponent implements OnInit {
  private readonly api = inject(CollaborationOpportunitiesApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly modes: Array<{ value: CollaborationOpportunityMode; label: string }> = [
    { value: 'STRICT', label: 'Estricto' },
    { value: 'BALANCED', label: 'Equilibrado' },
    { value: 'BROAD', label: 'Amplio' }
  ];
  readonly limitOptions = [3, 5, 8, 12];
  readonly availableMinYear = signal(2019);
  readonly availableMaxYear = signal(2026);
  readonly response = signal<CollaborationOpportunityResponse | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly demoState = signal<CollaborationOpportunityDemoState>('default');

  readonly filterForm = new FormGroup({
    fromYear: new FormControl<number | null>(2022),
    toYear: new FormControl<number | null>(2026),
    mode: new FormControl<CollaborationOpportunityMode>('BALANCED', { nonNullable: true }),
    limit: new FormControl<number>(5, { nonNullable: true })
  });

  readonly yearRangeValid = computed(() => {
    const { fromYear, toYear } = this.filterForm.getRawValue();
    return fromYear === null || toYear === null || fromYear <= toYear;
  });

  readonly opportunities = computed<CollaborationOpportunity[]>(() => this.response()?.opportunities ?? []);
  readonly visibilityNote = computed(() => this.response()?.visibilityScope || 'Uso interno administrativo.');
  readonly generatedAtLabel = computed(() => {
    const generatedAt = this.response()?.generatedAt;
    if (!generatedAt) {
      return 'Pendiente';
    }
    return new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(generatedAt));
  });
  readonly summaryCards = computed<SummaryCard[]>(() => {
    const items = this.opportunities();
    if (items.length === 0) {
      return [
        { label: 'Pares visibles', value: 0, hint: 'sin resultados para los filtros actuales' },
        { label: 'Confianza media', value: '0 %', hint: 'ajusta modo o rango temporal' },
        { label: 'Colaboraciones previas', value: 0, hint: 'sin pares en esta seleccion' },
        { label: 'Temas complementarios', value: 0, hint: 'potenciales cruces interdisciplinarios' }
      ];
    }

    const averageConfidence = Math.round(items.reduce((sum, item) => sum + item.confidence, 0) / items.length * 100);
    const collaborationCount = items.reduce((sum, item) => sum + item.existingCollaborationCount, 0);
    const complementaryTopics = new Set(items.flatMap((item) => item.complementaryTopics.map((topic) => topic.toLocaleLowerCase('es-ES'))));

    return [
      { label: 'Pares visibles', value: items.length, hint: 'oportunidades dentro del filtro actual' },
      { label: 'Confianza media', value: `${averageConfidence} %`, hint: 'grado medio de solidez de la señal' },
      { label: 'Colaboraciones previas', value: collaborationCount, hint: 'publicaciones compartidas acumuladas' },
      { label: 'Temas complementarios', value: complementaryTopics.size, hint: 'frentes posibles de cruce interdisciplinario' }
    ];
  });

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(
        map((params) => (params.get('demoState') === 'error' ? 'error' : 'default') as CollaborationOpportunityDemoState),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((state) => {
        this.demoState.set(state);
        this.reload();
      });

    this.filterForm.valueChanges
      .pipe(debounceTime(180), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.yearRangeValid()) {
          this.reload();
        }
      });
  }

  reload(): void {
    if (!this.yearRangeValid()) {
      return;
    }

    const filters = this.filterForm.getRawValue();
    this.loading.set(true);
    this.errorMessage.set('');

    this.api.getOpportunities(
      {
        fromYear: filters.fromYear,
        toYear: filters.toYear,
        mode: filters.mode,
        limit: filters.limit
      },
      this.demoState()
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.response.set(response);
          this.availableMinYear.set(response.minYear);
          this.availableMaxYear.set(response.maxYear);
          this.loading.set(false);
        },
        error: () => {
          this.response.set(null);
          this.errorMessage.set('Revisa el estado del origen analitico o vuelve a intentarlo en unos segundos.');
          this.loading.set(false);
        }
      });
  }

  resetFilters(): void {
    this.filterForm.reset({
      fromYear: 2022,
      toYear: 2026,
      mode: 'BALANCED',
      limit: 5
    });
  }

  unitTypeLabel(type: ResearchUnitType): string {
    switch (type) {
      case 'INSTITUTE':
        return 'Instituto';
      case 'HOSPITAL':
        return 'Hospital';
      case 'CENTER':
        return 'Centro';
      case 'DEPARTMENT':
        return 'Departamento';
      case 'LAB':
        return 'Laboratorio';
      case 'RESEARCH_GROUP':
        return 'Grupo';
      default:
        return 'Unidad';
    }
  }

  percentLabel(value: number): string {
    return `${Math.round(value * 100)} %`;
  }

  confidenceLabel(value: number): string {
    if (value >= 0.85) {
      return 'Confianza alta';
    }
    if (value >= 0.72) {
      return 'Confianza media';
    }
    return 'Senal exploratoria';
  }
}
