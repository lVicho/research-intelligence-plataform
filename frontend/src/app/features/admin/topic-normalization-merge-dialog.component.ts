import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

interface TopicNormalizationMergeDialogData {
  canonicalLabel: string;
  topicLabels: string[];
  affectedPublicationsCount: number;
}

@Component({
  selector: 'rip-topic-normalization-merge-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle],
  template: `
    <h2 mat-dialog-title>Fusionar temas</h2>

    <mat-dialog-content>
      <p class="dialog-copy">
        Vas a fusionar este grupo en <strong>{{ data.canonicalLabel }}</strong>.
      </p>
      <p class="dialog-copy">
        Se verán afectadas {{ data.affectedPublicationsCount }}
        {{ data.affectedPublicationsCount === 1 ? 'publicación' : 'publicaciones' }}.
      </p>

      <div class="topic-list">
        @for (topicLabel of data.topicLabels; track topicLabel) {
          <span class="topic-chip">{{ topicLabel }}</span>
        }
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" matDialogClose>Cancelar</button>
      <button mat-flat-button color="primary" type="button" [matDialogClose]="true">Fusionar temas</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-copy {
      margin: 0 0 12px;
      color: #4d5d70;
      line-height: 1.5;
    }

    .topic-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 16px;
    }

    .topic-chip {
      display: inline-flex;
      align-items: center;
      min-height: 30px;
      padding: 4px 10px;
      border: 1px solid #d8e2ed;
      border-radius: 999px;
      background: #f8fbfd;
      color: #334155;
      font-size: 0.85rem;
      font-weight: 650;
    }
  `]
})
export class TopicNormalizationMergeDialogComponent {
  protected readonly data = inject<TopicNormalizationMergeDialogData>(MAT_DIALOG_DATA);
}
