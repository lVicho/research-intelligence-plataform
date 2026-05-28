import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { TopicNormalizationCandidateGroup } from '../../core/api/api-models';
import { TopicNormalizationApiService } from '../../core/api/topic-normalization-api.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { MetricCardComponent } from '../../shared/components/metric-card.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';
import { TopicNormalizationMergeDialogComponent } from './topic-normalization-merge-dialog.component';

type PendingAction = 'save' | 'merge' | 'ignore';

@Component({
  selector: 'rip-topic-normalization-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    MetricCardComponent,
    PageHeaderComponent,
    StatusChipComponent,
    TagChipComponent
  ],
  template: `
    <section class="page">
      <rip-page-header
        title="Normalización de temas"
        subtitle="Consolida candidatos de taxonomía duplicada antes de validar o exponer las publicaciones en vistas institucionales."
        eyebrow="Gestión institucional"
      >
        <a mat-button routerLink="/admin/calidad-datos">Calidad de datos</a>
        <a mat-button routerLink="/admin/panel">Panel institucional</a>
      </rip-page-header>

      @if (loading() && candidateGroups().length === 0) {
        <rip-loading-state message="Cargando grupos de normalización..." />
      } @else if (errorMessage()) {
        <mat-card appearance="outlined">
          <mat-card-content class="state-card">
            <rip-error-state [message]="errorMessage()" />
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

        @if (feedbackMessage(); as feedback) {
          <mat-card appearance="outlined">
            <mat-card-content>
              <div class="feedback-banner" [class.feedback-banner-error]="feedback.tone === 'danger'">
                <strong>{{ feedback.title }}</strong>
                <span>{{ feedback.message }}</span>
              </div>
            </mat-card-content>
          </mat-card>
        }

        <mat-card appearance="outlined">
          <mat-card-content class="section-stack">
            <div class="section-header compact-header">
              <div>
                <p class="section-kicker">Posibles duplicados</p>
                <h2>{{ candidateGroups().length }} grupos pendientes</h2>
                <p>Revisa confianza, etiqueta sugerida y publicaciones afectadas antes de fusionar o ignorar cada grupo.</p>
              </div>
              @if (loading()) {
                <span class="muted">Actualizando...</span>
              }
            </div>

            @if (candidateGroups().length === 0) {
              <rip-empty-state
                title="Sin grupos pendientes"
                message="No hay candidatos activos de normalización para revisar ahora mismo."
              />
            } @else {
              <div class="group-list">
                @for (group of candidateGroups(); track group.id) {
                  <article class="group-item">
                    <div class="group-header">
                      <div class="group-heading">
                        <div class="chip-list">
                          <rip-status-chip [label]="confidenceLabel(group.confidence)" [tone]="confidenceTone(group.confidence)" />
                          <rip-tag-chip label="Tema" tone="type" />
                        </div>
                        <h3>{{ group.suggestedCanonicalLabel }}</h3>
                        <p>{{ group.topicsToMerge.length }} variantes detectadas para consolidar en una sola etiqueta canónica.</p>
                      </div>
                      <div class="group-actions">
                        <button
                          mat-button
                          type="button"
                          [disabled]="isPending(group.id)"
                          (click)="toggleReview(group.id)"
                        >
                          {{ isReviewOpen(group.id) ? 'Ocultar revisión' : 'Revisar' }}
                        </button>
                        <button
                          mat-button
                          type="button"
                          [disabled]="isPending(group.id)"
                          (click)="toggleEdit(group)"
                        >
                          {{ isEditing(group.id) ? 'Cancelar edición' : 'Editar etiqueta canónica' }}
                        </button>
                      </div>
                    </div>

                    <div class="metadata-grid">
                      <div class="metadata-item">
                        <span>Confianza</span>
                        <strong>{{ toPercent(group.confidence) }}</strong>
                      </div>
                      <div class="metadata-item">
                        <span>Publicaciones afectadas</span>
                        <strong>{{ group.affectedPublicationsCount }}</strong>
                      </div>
                      <div class="metadata-item">
                        <span>Tema canónico</span>
                        <strong>{{ group.canonicalLabel }}</strong>
                      </div>
                    </div>

                    <section class="canonical-panel">
                      <div>
                        <p class="section-kicker">Tema canónico</p>
                        @if (isEditing(group.id)) {
                          <mat-form-field appearance="outline" class="canonical-field">
                            <mat-label>Etiqueta canónica</mat-label>
                            <input
                              matInput
                              [value]="draftCanonicalLabel(group)"
                              [disabled]="pendingActionFor(group.id) === 'save'"
                              (input)="onDraftInput(group.id, $event)"
                            >
                          </mat-form-field>
                          <p class="muted helper-copy">Sugerencia inicial: {{ group.suggestedCanonicalLabel }}</p>
                        } @else {
                          <h4>{{ group.canonicalLabel }}</h4>
                          <p class="helper-copy">
                            Sugerencia del sistema: {{ group.suggestedCanonicalLabel }}
                          </p>
                        }
                      </div>

                      @if (isEditing(group.id)) {
                        <div class="actions">
                          <button
                            mat-stroked-button
                            type="button"
                            [disabled]="pendingActionFor(group.id) === 'save'"
                            (click)="cancelEdit(group)"
                          >
                            Cancelar
                          </button>
                          <button
                            mat-flat-button
                            color="primary"
                            type="button"
                            [disabled]="pendingActionFor(group.id) === 'save'"
                            (click)="saveCanonicalLabel(group)"
                          >
                            {{ pendingActionFor(group.id) === 'save' ? 'Guardando...' : 'Guardar etiqueta' }}
                          </button>
                        </div>
                      }
                    </section>

                    <section class="topics-panel">
                      <p class="section-kicker">Posibles duplicados</p>
                      <div class="topic-list">
                        @for (topic of group.topicsToMerge; track topic.id) {
                          <div class="topic-item">
                            <strong>{{ topic.label }}</strong>
                            <span>{{ topic.publicationCount }} {{ topic.publicationCount === 1 ? 'publicación' : 'publicaciones' }}</span>
                          </div>
                        }
                      </div>
                    </section>

                    <div class="actions group-footer">
                      <button
                        mat-flat-button
                        color="primary"
                        type="button"
                        [disabled]="isPending(group.id)"
                        (click)="confirmMerge(group)"
                      >
                        {{ pendingActionFor(group.id) === 'merge' ? 'Fusionando...' : 'Fusionar temas' }}
                      </button>
                      <button
                        mat-stroked-button
                        type="button"
                        [disabled]="isPending(group.id)"
                        (click)="ignoreGroup(group)"
                      >
                        {{ pendingActionFor(group.id) === 'ignore' ? 'Ignorando...' : 'Ignorar' }}
                      </button>
                    </div>

                    @if (isReviewOpen(group.id)) {
                      <section class="review-panel">
                        <div class="section-header compact-header">
                          <div>
                            <p class="section-kicker">Publicaciones afectadas</p>
                            <h2>Revisar</h2>
                            <p>Abre una muestra de publicaciones relacionadas antes de confirmar la fusión.</p>
                          </div>
                        </div>

                        @if (group.affectedPublications.length === 0) {
                          <rip-empty-state
                            title="Sin publicaciones enlazadas"
                            message="Este grupo no incluye accesos directos de revisión en el snapshot actual."
                          />
                        } @else {
                          <div class="item-list">
                            @for (publication of group.affectedPublications; track publication.id) {
                              <a class="item-row interactive" [routerLink]="publication.path" [queryParams]="navigationContext.returnQueryParams('Volver a normalización de temas')">
                                <span class="item-title">{{ publication.title }}</span>
                                <div class="item-meta">
                                  <span>Publicación #{{ publication.id }}</span>
                                  <span>Revisar detalle</span>
                                </div>
                              </a>
                            }
                          </div>
                        }
                      </section>
                    }
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
    .state-card {
      display: grid;
      gap: 20px;
    }

    .compact-header {
      margin-bottom: 0;
    }

    .compact-header p {
      max-width: 760px;
    }

    .feedback-banner {
      display: grid;
      gap: 6px;
      padding: 16px 18px;
      border: 1px solid #b9d9ca;
      border-radius: 14px;
      background: #f0faf4;
      color: #17634f;
    }

    .feedback-banner-error {
      border-color: #f0b4b4;
      background: #fff6f6;
      color: #9b1c1c;
    }

    .group-list {
      display: grid;
      gap: 18px;
    }

    .group-item {
      display: grid;
      gap: 20px;
      padding: 22px;
      border: 1px solid #dce5ee;
      border-radius: 20px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 251, 253, 0.96)),
        linear-gradient(90deg, rgba(31, 111, 139, 0.04), rgba(45, 140, 120, 0.03));
    }

    .group-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 20px;
    }

    .group-heading {
      display: grid;
      gap: 10px;
      min-width: 0;
    }

    .group-heading h3,
    .canonical-panel h4 {
      margin: 0;
      color: #142033;
      line-height: 1.2;
    }

    .group-heading h3 {
      font-size: 1.18rem;
    }

    .group-heading p,
    .helper-copy {
      margin: 0;
      color: #617182;
      line-height: 1.55;
    }

    .group-actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 8px;
    }

    .canonical-panel,
    .topics-panel,
    .review-panel {
      display: grid;
      gap: 14px;
      padding: 18px;
      border: 1px solid #dce5ee;
      border-radius: 16px;
      background: rgba(255, 255, 255, 0.88);
    }

    .canonical-panel {
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: end;
      gap: 20px;
    }

    .canonical-field {
      width: min(420px, 100%);
    }

    .topic-list {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 12px;
    }

    .topic-item {
      display: grid;
      gap: 6px;
      min-width: 0;
      padding: 14px 16px;
      border: 1px solid #dbe7ef;
      border-radius: 14px;
      background: #ffffff;
    }

    .topic-item strong {
      color: #163247;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .topic-item span {
      color: #667487;
      font-size: 0.88rem;
    }

    .group-footer {
      justify-content: flex-start;
    }

    @media (max-width: 860px) {
      .group-header,
      .canonical-panel {
        display: grid;
      }

      .group-actions {
        justify-content: flex-start;
      }
    }
  `]
})
export class TopicNormalizationPageComponent implements OnInit {
  private readonly api = inject(TopicNormalizationApiService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);

  readonly candidateGroups = signal<TopicNormalizationCandidateGroup[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly feedbackMessage = signal<{ tone: 'success' | 'danger'; title: string; message: string } | null>(null);
  readonly draftCanonicalLabels = signal<Record<string, string>>({});
  readonly editingGroupIds = signal<string[]>([]);
  readonly reviewOpenGroupIds = signal<string[]>([]);
  readonly pendingActions = signal<Record<string, PendingAction | undefined>>({});

  readonly summaryCards = computed(() => {
    const groups = this.candidateGroups();
    const affectedPublications = groups.reduce((sum, group) => sum + group.affectedPublicationsCount, 0);
    const highestConfidence = groups.length === 0 ? 0 : Math.max(...groups.map((group) => group.confidence));

    return [
      { label: 'Grupos pendientes', value: groups.length, hint: 'candidatos activos para revisar' },
      { label: 'Publicaciones afectadas', value: affectedPublications, hint: 'alcance estimado de la normalización' },
      { label: 'Mayor confianza', value: this.toPercent(highestConfidence), hint: 'grupo con mayor similitud detectada' }
    ];
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.api.candidateGroups()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (groups) => {
          const orderedGroups = [...groups].sort((left, right) => right.confidence - left.confidence);
          this.candidateGroups.set(orderedGroups);
          this.draftCanonicalLabels.set(
            Object.fromEntries(orderedGroups.map((group) => [group.id, group.canonicalLabel]))
          );
          this.loading.set(false);
        },
        error: () => {
          this.candidateGroups.set([]);
          this.errorMessage.set('No se pudieron cargar los grupos candidatos de normalización.');
          this.loading.set(false);
        }
      });
  }

  updateDraft(groupId: string, value: string): void {
    this.draftCanonicalLabels.update((drafts) => ({ ...drafts, [groupId]: value }));
  }

  onDraftInput(groupId: string, event: Event): void {
    this.updateDraft(groupId, (event.target as HTMLInputElement | null)?.value ?? '');
  }

  toggleEdit(group: TopicNormalizationCandidateGroup): void {
    if (this.isEditing(group.id)) {
      this.cancelEdit(group);
      return;
    }

    this.draftCanonicalLabels.update((drafts) => ({ ...drafts, [group.id]: group.canonicalLabel }));
    this.editingGroupIds.update((groupIds) => [...groupIds, group.id]);
  }

  cancelEdit(group: TopicNormalizationCandidateGroup): void {
    this.draftCanonicalLabels.update((drafts) => ({ ...drafts, [group.id]: group.canonicalLabel }));
    this.editingGroupIds.update((groupIds) => groupIds.filter((groupId) => groupId !== group.id));
  }

  saveCanonicalLabel(group: TopicNormalizationCandidateGroup): void {
    const canonicalLabel = this.draftCanonicalLabel(group).trim();
    if (!canonicalLabel) {
      this.feedbackMessage.set({
        tone: 'danger',
        title: 'Etiqueta no válida',
        message: 'La etiqueta canónica no puede estar vacía.'
      });
      return;
    }

    this.setPending(group.id, 'save');
    this.feedbackMessage.set(null);

    this.api.updateCanonicalLabel(group.id, canonicalLabel)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updatedGroup) => {
          this.replaceGroup(updatedGroup);
          this.editingGroupIds.update((groupIds) => groupIds.filter((groupId) => groupId !== group.id));
          this.draftCanonicalLabels.update((drafts) => ({ ...drafts, [group.id]: updatedGroup.canonicalLabel }));
          this.clearPending(group.id);
          this.feedbackMessage.set({
            tone: 'success',
            title: 'Etiqueta actualizada',
            message: `El tema canónico del grupo se guardó como "${updatedGroup.canonicalLabel}".`
          });
        },
        error: () => {
          this.clearPending(group.id);
          this.feedbackMessage.set({
            tone: 'danger',
            title: 'No se pudo guardar',
            message: 'Revisa la etiqueta canónica y vuelve a intentarlo.'
          });
        }
      });
  }

  confirmMerge(group: TopicNormalizationCandidateGroup): void {
    const canonicalLabel = this.draftCanonicalLabel(group).trim();
    if (!canonicalLabel) {
      this.feedbackMessage.set({
        tone: 'danger',
        title: 'Etiqueta no válida',
        message: 'Define primero una etiqueta canónica antes de fusionar.'
      });
      return;
    }

    const dialogRef = this.dialog.open(TopicNormalizationMergeDialogComponent, {
      width: '520px',
      data: {
        canonicalLabel,
        topicLabels: group.topicsToMerge.map((topic) => topic.label),
        affectedPublicationsCount: group.affectedPublicationsCount
      }
    });

    dialogRef.afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }

        this.setPending(group.id, 'merge');
        this.feedbackMessage.set(null);

        this.api.mergeGroup(group.id, canonicalLabel)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.removeGroup(group.id);
              this.clearPending(group.id);
              this.feedbackMessage.set({
                tone: 'success',
                title: 'Fusión completada',
                message: `El grupo se fusionó en "${canonicalLabel}".`
              });
            },
            error: () => {
              this.clearPending(group.id);
              this.feedbackMessage.set({
                tone: 'danger',
                title: 'No se pudo fusionar',
                message: 'La fusión no se completó. Vuelve a intentarlo.'
              });
            }
          });
      });
  }

  ignoreGroup(group: TopicNormalizationCandidateGroup): void {
    this.setPending(group.id, 'ignore');
    this.feedbackMessage.set(null);

    this.api.ignoreGroup(group.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.removeGroup(group.id);
          this.clearPending(group.id);
          this.feedbackMessage.set({
            tone: 'success',
            title: 'Grupo ignorado',
            message: 'El candidato se retiró de la cola de revisión actual.'
          });
        },
        error: () => {
          this.clearPending(group.id);
          this.feedbackMessage.set({
            tone: 'danger',
            title: 'No se pudo ignorar',
            message: 'El grupo sigue pendiente. Reintenta la acción.'
          });
        }
      });
  }

  toggleReview(groupId: string): void {
    this.reviewOpenGroupIds.update((groupIds) =>
      groupIds.includes(groupId) ? groupIds.filter((id) => id !== groupId) : [...groupIds, groupId]
    );
  }

  isEditing(groupId: string): boolean {
    return this.editingGroupIds().includes(groupId);
  }

  isReviewOpen(groupId: string): boolean {
    return this.reviewOpenGroupIds().includes(groupId);
  }

  draftCanonicalLabel(group: TopicNormalizationCandidateGroup): string {
    return this.draftCanonicalLabels()[group.id] ?? group.canonicalLabel;
  }

  pendingActionFor(groupId: string): PendingAction | undefined {
    return this.pendingActions()[groupId];
  }

  isPending(groupId: string): boolean {
    return this.pendingActionFor(groupId) !== undefined;
  }

  confidenceLabel(confidence: number): string {
    if (confidence >= 0.9) {
      return 'Confianza alta';
    }
    if (confidence >= 0.75) {
      return 'Confianza media';
    }
    return 'Confianza inicial';
  }

  confidenceTone(confidence: number): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
    if (confidence >= 0.9) {
      return 'success';
    }
    if (confidence >= 0.75) {
      return 'info';
    }
    return 'warning';
  }

  toPercent(value: number): string {
    return new Intl.NumberFormat('es-ES', {
      style: 'percent',
      maximumFractionDigits: 0
    }).format(value);
  }

  private setPending(groupId: string, action: PendingAction): void {
    this.pendingActions.update((actions) => ({ ...actions, [groupId]: action }));
  }

  private clearPending(groupId: string): void {
    this.pendingActions.update((actions) => {
      const nextActions = { ...actions };
      delete nextActions[groupId];
      return nextActions;
    });
  }

  private replaceGroup(updatedGroup: TopicNormalizationCandidateGroup): void {
    this.candidateGroups.update((groups) =>
      groups
        .map((group) => group.id === updatedGroup.id ? updatedGroup : group)
        .sort((left, right) => right.confidence - left.confidence)
    );
  }

  private removeGroup(groupId: string): void {
    this.candidateGroups.update((groups) => groups.filter((group) => group.id !== groupId));
    this.editingGroupIds.update((groupIds) => groupIds.filter((id) => id !== groupId));
    this.reviewOpenGroupIds.update((groupIds) => groupIds.filter((id) => id !== groupId));
    this.draftCanonicalLabels.update((drafts) => {
      const nextDrafts = { ...drafts };
      delete nextDrafts[groupId];
      return nextDrafts;
    });
  }
}
