import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { IngestionApiService } from '../../core/api/ingestion-api.service';
import { PublicationCsvIngestionReport } from '../../core/api/api-models';
import { ErrorStateComponent } from '../../shared/components/error-state.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state.component';
import { MetricCardComponent } from '../../shared/components/metric-card.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';

interface ReportStat {
  label: string;
  value: number;
}

@Component({
  selector: 'rip-ingestion-page',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule, ErrorStateComponent, EmptyStateComponent, LoadingStateComponent, MetricCardComponent, PageHeaderComponent],
  template: `
    <section class="page">
      <rip-page-header
        title="Ingesta"
        subtitle="Importa publicaciones desde CSV preservando autores, temas y errores de fila."
        eyebrow="Carga de datos"
      />

      <mat-card appearance="outlined" class="upload-card">
        <mat-card-header>
          <mat-card-title>Subida de CSV</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="upload-panel">
            <div>
              <h2>CSV de publicaciones</h2>
              <p class="muted">{{ selectedFileName() || 'Ningún archivo seleccionado' }}</p>
            </div>
            <div class="actions">
              <input #fileInput class="file-input" type="file" accept=".csv,text/csv" (change)="selectFile($event)">
              <button mat-button type="button" (click)="fileInput.click()">Elegir archivo</button>
              <button mat-flat-button color="primary" type="button" [disabled]="!selectedFile() || uploading()" (click)="upload()">
                Subir CSV
              </button>
            </div>
          </div>

          @if (uploading()) {
            <rip-loading-state message="Importando publicaciones" />
          }

          @if (errorMessage()) {
            <rip-error-state [message]="errorMessage()" />
          }
        </mat-card-content>
      </mat-card>

      @if (report(); as ingestionReport) {
        <div class="metric-grid">
          @for (stat of reportStats(); track stat.label) {
            <rip-metric-card [label]="stat.label" [value]="stat.value" />
          }
        </div>

        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-card-title>Errores por fila</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (ingestionReport.rowErrors.length === 0) {
              <rip-empty-state title="Sin errores por fila" message="La ingesta terminó sin incidencias de validación." />
            } @else {
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Fila</th>
                      <th>Mensaje</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (rowError of ingestionReport.rowErrors; track rowError.rowNumber + rowError.message) {
                      <tr>
                        <td>{{ rowError.rowNumber }}</td>
                        <td>{{ rowError.message }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </mat-card-content>
        </mat-card>
      }
    </section>
  `,
  styles: [`
    .upload-panel {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 20px;
    }

    h2 {
      margin: 0;
      font-size: 1.05rem;
    }

    .file-input {
      display: none;
    }

    .progress-row {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 16px;
      color: #5a6677;
    }

    .error-banner {
      margin-top: 16px;
      padding: 12px 14px;
      border: 1px solid #f0b4b4;
      border-radius: 8px;
      background: #fff5f5;
      color: #a61b1b;
    }

    th,
    td {
      padding: 12px 14px;
      border-bottom: 1px solid #e4e9ef;
      text-align: left;
      vertical-align: top;
    }

    th:first-child,
    td:first-child {
      width: 96px;
      white-space: nowrap;
    }

    @media (max-width: 720px) {
      .upload-panel {
        display: grid;
      }
    }
  `]
})
export class IngestionPageComponent {
  private readonly api = inject(IngestionApiService);

  readonly selectedFile = signal<File | null>(null);
  readonly selectedFileName = computed(() => this.selectedFile()?.name ?? '');
  readonly uploading = signal(false);
  readonly report = signal<PublicationCsvIngestionReport | null>(null);
  readonly errorMessage = signal('');
  readonly reportStats = computed<ReportStat[]>(() => {
    const report = this.report();
    if (!report) {
      return [];
    }
    return [
      { label: 'Filas totales', value: report.totalRows },
      { label: 'Publicaciones insertadas', value: report.insertedPublications },
      { label: 'Publicaciones actualizadas', value: report.updatedPublications },
      { label: 'Autores internos encontrados', value: report.matchedInternalAuthors },
      { label: 'Autores externos guardados', value: report.externalAuthorsStored },
      { label: 'Temas creados', value: report.createdTopics },
      { label: 'Filas omitidas', value: report.skippedRows }
    ];
  });

  selectFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.item(0) ?? null);
    this.errorMessage.set('');
  }

  upload(): void {
    const file = this.selectedFile();
    if (!file) {
      return;
    }
    this.uploading.set(true);
    this.errorMessage.set('');
    this.report.set(null);
    this.api.uploadPublicationsCsv(file).subscribe({
      next: (report) => {
        this.report.set(report);
        this.uploading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(this.toErrorMessage(error));
        this.uploading.set(false);
      }
    });
  }

  private toErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as { message?: string } | null;
      return body?.message ?? 'La subida del CSV ha fallado.';
    }
    return 'La subida del CSV ha fallado.';
  }
}
