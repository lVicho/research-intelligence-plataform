import { Injectable, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

export interface NavigationReturn {
  path: string;
  label: string;
}

export type NavigationContextQueryParams = {
  returnTo: string;
  returnLabel: string;
};

@Injectable({ providedIn: 'root' })
export class NavigationContextService {
  private readonly router = inject(Router);

  returnQueryParams(returnLabel: string, returnTo = this.currentContextUrl()): NavigationContextQueryParams {
    return {
      returnTo: this.safeReturnTo(returnTo, '/portal'),
      returnLabel
    };
  }

  resolve(route: ActivatedRoute, fallbackPath: string, fallbackLabel: string): NavigationReturn {
    const returnTo = route.snapshot.queryParamMap.get('returnTo');
    const returnLabel = route.snapshot.queryParamMap.get('returnLabel');
    return {
      path: this.safeReturnTo(returnTo, fallbackPath),
      label: returnLabel?.trim() || fallbackLabel
    };
  }

  navigateBack(route: ActivatedRoute, fallbackPath: string, fallbackLabel: string): void {
    const target = this.resolve(route, fallbackPath, fallbackLabel);
    void this.router.navigateByUrl(target.path);
  }

  currentContextUrl(): string {
    const tree = this.router.parseUrl(this.router.url);
    delete tree.queryParams['returnTo'];
    delete tree.queryParams['returnLabel'];
    return this.safeReturnTo(this.router.serializeUrl(tree), '/portal');
  }

  isCurrentPath(prefix: string): boolean {
    const path = this.router.url.split(/[?#]/)[0];
    return path === prefix || path.startsWith(`${prefix}/`);
  }

  private safeReturnTo(value: string | null, fallbackPath: string): string {
    if (!value) {
      return fallbackPath;
    }

    const trimmed = value.trim();
    if (
      !trimmed.startsWith('/')
      || trimmed.startsWith('//')
      || trimmed.includes('\\')
      || /[\u0000-\u001f]/.test(trimmed)
      || /^[a-z][a-z0-9+.-]*:/i.test(trimmed)
    ) {
      return fallbackPath;
    }

    return trimmed;
  }
}
