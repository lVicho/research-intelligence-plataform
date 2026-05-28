import { Component } from '@angular/core';

import { ShellComponent } from './core/layout/shell.component';

@Component({
  selector: 'rip-root',
  standalone: true,
  imports: [ShellComponent],
  template: '<rip-shell />'
})
export class AppComponent {
}
