import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { AuthApiService } from '../api/auth-api.service';
import { AuthUser, RoleCode } from '../api/api-models';

interface StoredSession {
  token: string;
  user: AuthUser;
}

@Injectable({ providedIn: 'root' })
export class AuthStateService {
  private readonly storageKey = 'rip.auth.session';
  private readonly api = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly currentUserSignal = signal<AuthUser | null>(this.loadStoredUser());

  readonly currentUser = computed(() => this.currentUserSignal());
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);
  readonly roleLabel = computed(() => this.primaryRoleLabel(this.currentUserSignal()?.roles ?? []));

  login(email: string, password: string): Observable<AuthUser> {
    return this.api.login({ email, password }).pipe(
      tap((user) => this.storeSession({
        token: this.encodeBasicToken(email, password),
        user
      }))
    );
  }

  logout(): void {
    this.api.logout().subscribe({ error: () => undefined });
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  authorizationHeader(): string | null {
    const session = this.loadStoredSession();
    return session ? `Basic ${session.token}` : null;
  }

  hasAnyRole(requiredRoles: RoleCode[]): boolean {
    if (requiredRoles.length === 0) {
      return true;
    }
    const user = this.currentUserSignal();
    return user !== null && requiredRoles.some((role) => user.roles.includes(role));
  }

  refreshCurrentUser(): void {
    if (!this.authorizationHeader()) {
      return;
    }
    this.api.me().subscribe({
      next: (user) => {
        const session = this.loadStoredSession();
        if (session) {
          this.storeSession({ token: session.token, user });
        }
      },
      error: () => this.clearSession()
    });
  }

  private storeSession(session: StoredSession): void {
    localStorage.setItem(this.storageKey, JSON.stringify(session));
    this.currentUserSignal.set(session.user);
  }

  private clearSession(): void {
    localStorage.removeItem(this.storageKey);
    this.currentUserSignal.set(null);
  }

  private loadStoredUser(): AuthUser | null {
    return this.loadStoredSession()?.user ?? null;
  }

  private loadStoredSession(): StoredSession | null {
    const rawSession = localStorage.getItem(this.storageKey);
    if (!rawSession) {
      return null;
    }
    try {
      return JSON.parse(rawSession) as StoredSession;
    } catch {
      localStorage.removeItem(this.storageKey);
      return null;
    }
  }

  private encodeBasicToken(email: string, password: string): string {
    return btoa(`${email}:${password}`);
  }

  private primaryRoleLabel(roles: RoleCode[]): string {
    if (roles.includes('ADMIN')) {
      return 'Administrador';
    }
    if (roles.includes('VALIDATOR')) {
      return 'Validador';
    }
    if (roles.includes('RESEARCHER')) {
      return 'Investigador';
    }
    return 'Usuario público';
  }
}
