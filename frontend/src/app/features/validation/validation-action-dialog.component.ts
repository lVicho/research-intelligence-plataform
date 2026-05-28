import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export type ValidationDialogAction = 'validate' | 'reject' | 'requestChanges';

interface ValidationActionDialogData {
  action: ValidationDialogAction;
  initialComment?: string | null;
  suggestedComment?: string | null;
}

interface ValidationActionDialogResult {
  comment: string | null;
}

@Component({
  selector: 'rip-validation-action-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormFieldModule,
    MatInputModule
  ],
  template: `
    <h2 mat-dialog-title>{{ title }}</h2>

    <mat-dialog-content>
      <p class="dialog-copy">{{ description }}</p>

      @if (showSuggestedCommentAction) {
        <div class="assistant-comment-block">
          <p>La revisión asistida propone un comentario para este caso. Puedes insertarlo y ajustarlo antes de confirmar.</p>
          <button mat-stroked-button type="button" (click)="useSuggestedComment()">Usar comentario</button>
        </div>
      }

      <mat-form-field appearance="outline" class="comment-field">
        <mat-label>{{ label }}</mat-label>
        <textarea matInput rows="5" [formControl]="commentControl"></textarea>
        @if (commentControl.hasError('required') && commentControl.touched) {
          <mat-error>Este comentario es obligatorio.</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" matDialogClose>Cancelar</button>
      <button mat-flat-button color="primary" type="button" [disabled]="commentControl.invalid" (click)="confirm()">
        {{ confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-copy {
      margin: 0 0 16px;
      color: #4d5d70;
      line-height: 1.45;
    }

    .comment-field {
      width: 100%;
    }

    .assistant-comment-block {
      display: grid;
      gap: 10px;
      margin: 0 0 16px;
      padding: 14px 16px;
      border: 1px solid #d9e6f2;
      border-radius: 14px;
      background: #f7fbff;
    }

    .assistant-comment-block p {
      margin: 0;
      color: #4d5d70;
      line-height: 1.5;
    }
  `]
})
export class ValidationActionDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ValidationActionDialogComponent, ValidationActionDialogResult>);
  private readonly data = inject<ValidationActionDialogData>(MAT_DIALOG_DATA);

  readonly commentControl = new FormControl(this.data.initialComment ?? '', {
    nonNullable: true,
    validators: this.data.action === 'validate' ? [] : [Validators.required]
  });
  readonly showSuggestedCommentAction = this.data.action !== 'validate' && !!this.data.suggestedComment?.trim();

  get title(): string {
    switch (this.data.action) {
      case 'requestChanges':
        return 'Solicitar cambios';
      case 'reject':
        return 'Rechazar actividad';
      default:
        return 'Validar actividad';
    }
  }

  get description(): string {
    switch (this.data.action) {
      case 'requestChanges':
        return 'Indica al investigador qué debe corregir antes de reenviar esta actividad a validación.';
      case 'reject':
        return 'Explica el motivo del rechazo para que el investigador entienda por qué no puede continuar.';
      default:
        return 'Puedes añadir un comentario opcional para dejar contexto sobre la validación.';
    }
  }

  get label(): string {
    return this.data.action === 'validate' ? 'Comentario para el investigador' : 'Motivo';
  }

  get confirmLabel(): string {
    switch (this.data.action) {
      case 'requestChanges':
        return 'Solicitar cambios';
      case 'reject':
        return 'Rechazar';
      default:
        return 'Validar';
    }
  }

  useSuggestedComment(): void {
    if (!this.data.suggestedComment) {
      return;
    }

    this.commentControl.setValue(this.data.suggestedComment);
    this.commentControl.markAsDirty();
    this.commentControl.markAsTouched();
  }

  confirm(): void {
    if (this.commentControl.invalid) {
      this.commentControl.markAsTouched();
      return;
    }

    const comment = this.commentControl.value.trim();
    this.dialogRef.close({ comment: comment || null });
  }
}
