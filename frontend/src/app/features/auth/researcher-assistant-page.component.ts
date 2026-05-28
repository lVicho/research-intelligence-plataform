import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { forkJoin } from 'rxjs';

import { MeActivity, MeDashboard } from '../../core/api/api-models';
import { MeApiService } from '../../core/api/me-api.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import {
  ResearcherAssistantPanelComponent,
  ResearcherAssistantPromptId
} from './researcher-assistant-panel.component';

@Component({
  selector: 'rip-researcher-assistant-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    ResearcherAssistantPanelComponent
  ],
  template: `
    <section class="page assistant-page">
      <rip-page-header
        title="Asistente personal"
        subtitle="Orienta tus próximos pasos con tu perfil, tus actividades propias y tus avisos internos, sin salir del área privada."
        eyebrow="Privado"
        [compact]="true"
      >
        <a mat-button routerLink="/app/mi-panel">Mi panel</a>
        <a mat-flat-button color="primary" routerLink="/app/mis-actividades">Mis actividades</a>
      </rip-page-header>

      @if (loading()) {
        <rip-loading-state message="Preparando el asistente personal..." />
      } @else if (errorMessage()) {
        <mat-card appearance="outlined">
          <mat-card-content class="state-card">
            <rip-error-state [message]="errorMessage()" />
            <div class="actions">
              <button mat-flat-button color="primary" type="button" (click)="reload()">Reintentar</button>
            </div>
          </mat-card-content>
        </mat-card>
      } @else if (dashboard()) {
        <rip-researcher-assistant-panel
          [dashboard]="dashboard()"
          [activityInventory]="activityInventory()"
          [activityInventoryTotal]="activityInventoryTotal()"
          [initialPrompt]="selectedPrompt()"
        />
      } @else {
        <rip-empty-state
          title="Sin asistente disponible"
          message="No se pudo preparar el contexto privado del asistente para esta sesión."
        />
      }
    </section>
  `,
  styles: [`
    .assistant-page {
      gap: 20px;
    }

    .state-card {
      display: grid;
      gap: 16px;
    }
  `]
})
export class ResearcherAssistantPageComponent implements OnInit {
  private readonly api = inject(MeApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  readonly dashboard = signal<MeDashboard | null>(null);
  readonly activityInventory = signal<MeActivity[]>([]);
  readonly activityInventoryTotal = signal(0);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly selectedPrompt = signal<ResearcherAssistantPromptId | null>(null);

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        this.selectedPrompt.set(this.parsePrompt(params.get('prompt')));
        this.reload();
      });
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    forkJoin({
      dashboard: this.api.dashboard(),
      activities: this.api.activities({ size: 100 })
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ dashboard, activities }) => {
          this.dashboard.set(dashboard);
          this.activityInventory.set(activities.content);
          this.activityInventoryTotal.set(activities.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.dashboard.set(null);
          this.activityInventory.set([]);
          this.activityInventoryTotal.set(0);
          this.loading.set(false);
          this.errorMessage.set('No se pudo cargar el asistente personal.');
        }
      });
  }

  private parsePrompt(value: string | null): ResearcherAssistantPromptId | null {
    switch (value) {
      case 'pending':
      case 'changes':
      case 'complete-data':
      case 'profile-summary':
      case 'publication-topics':
        return value;
      default:
        return null;
    }
  }
}
