import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { AuthStateService } from '../../core/auth/auth-state.service';
import { ErrorStateComponent } from '../../shared/components/error-state.component';

@Component({
  selector: 'rip-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, ErrorStateComponent],
  template: `
    <section class="login-page">
      <div class="login-panel">
        <a class="login-brand" routerLink="/portal">
          <span class="brand-mark">RI</span>
          <span>Inteligencia de Investigación</span>
        </a>

        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-card-title>Iniciar sesión</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="login()" class="login-form">
              <mat-form-field appearance="outline">
                <mat-label>Usuario</mat-label>
                <input matInput type="email" autocomplete="username" formControlName="email">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Contraseña</mat-label>
                <input matInput type="password" autocomplete="current-password" formControlName="password">
              </mat-form-field>

              @if (errorMessage()) {
                <rip-error-state [message]="errorMessage()" />
              }

              <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || loading()">
                Iniciar sesión
              </button>
            </form>
          </mat-card-content>
        </mat-card>
      </div>
    </section>
  `,
  styles: [`
    .login-page {
      display: grid;
      min-height: calc(100vh - 64px);
      place-items: center;
      padding: 32px 0;
    }

    .login-panel {
      display: grid;
      gap: 18px;
      width: min(100%, 420px);
    }

    .login-brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      color: #102033;
      font-weight: 760;
      text-decoration: none;
    }

    .brand-mark {
      display: inline-grid;
      width: 34px;
      height: 34px;
      place-items: center;
      border-radius: 10px;
      background: #1f6f8b;
      color: #ffffff;
      font-size: 0.82rem;
      font-weight: 800;
    }

    .login-form {
      display: grid;
      gap: 14px;
    }
  `]
})
export class LoginPageComponent {
  private readonly auth = inject(AuthStateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  login(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }
    const value = this.form.getRawValue();
    this.loading.set(true);
    this.errorMessage.set('');
    this.auth.login(value.email, value.password).subscribe({
      next: () => {
        this.loading.set(false);
        void this.router.navigateByUrl(this.returnUrl());
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.toErrorMessage(error));
      }
    });
  }

  private returnUrl(): string {
    const value = this.route.snapshot.queryParamMap.get('returnUrl');
    return value && value.startsWith('/') ? value : this.defaultWorkspaceUrl();
  }

  private defaultWorkspaceUrl(): string {
    const roles = this.auth.currentUser()?.roles ?? [];
    if (roles.includes('ADMIN') || roles.includes('VALIDATOR')) {
      return '/admin/panel';
    }
    if (roles.includes('RESEARCHER')) {
      return '/app/mi-panel';
    }
    return '/portal';
  }

  private toErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Usuario o contraseña no válidos.';
    }
    return 'No se ha podido iniciar sesión.';
  }
}
