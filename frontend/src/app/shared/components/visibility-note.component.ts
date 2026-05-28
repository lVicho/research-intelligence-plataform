import { Component, input } from '@angular/core';

@Component({
  selector: 'rip-visibility-note',
  standalone: true,
  template: `
    <div class="visibility-note" [class.strong]="emphasis() === 'strong'">
      {{ message() }}
    </div>
  `,
  styles: [`
    .visibility-note {
      width: fit-content;
      max-width: 100%;
      padding: 10px 14px;
      border: 1px solid #d9e6f2;
      border-radius: 999px;
      background: #f7fbff;
      color: #526276;
      font-size: 0.85rem;
      font-weight: 700;
      line-height: 1.4;
    }

    .visibility-note.strong {
      border-color: #cfe3d8;
      background: #f3fbf6;
      color: #24513a;
    }
  `]
})
export class VisibilityNoteComponent {
  readonly message = input.required<string>();
  readonly emphasis = input<'subtle' | 'strong'>('subtle');
}
