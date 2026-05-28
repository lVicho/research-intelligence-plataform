import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { filter, map, startWith } from 'rxjs';

import { RoleCode } from '../api/api-models';
import { AuthStateService } from '../auth/auth-state.service';

interface NavigationItem {
  label: string;
  path: string;
  exact?: boolean;
  roles?: RoleCode[];
}

interface NavigationGroup {
  label: string;
  items: NavigationItem[];
}

type AreaKey = 'portal' | 'researcher' | 'admin';

@Component({
  selector: 'rip-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatMenuModule,
    MatToolbarModule
  ],
  template: `
    <mat-toolbar class="topbar" [class.internal-topbar]="currentArea() !== 'portal'">
      <div class="topbar-inner">
        <a class="brand" routerLink="/portal" aria-label="Portal público de Inteligencia de Investigación">
          <span class="brand-mark">RI</span>
          <span class="brand-copy">
            <strong>Inteligencia de Investigación</strong>
            <small>{{ currentAreaTagline() }}</small>
          </span>
        </a>

        @if (currentArea() === 'portal') {
          <nav class="public-nav" aria-label="Portal público">
            @for (item of publicNavigation; track item.label) {
              <a
                mat-button
                [routerLink]="item.path"
                routerLinkActive="active-link"
                [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
              >
                {{ item.label }}
              </a>
            }
          </nav>

          <div class="public-session-actions">
            @if (currentUser()) {
              <a mat-stroked-button class="login-button" [routerLink]="defaultPrivateAreaPath()">Entrar al área interna</a>
            } @else {
              <a mat-flat-button color="primary" class="login-button" routerLink="/login">Iniciar sesión</a>
            }
          </div>
        } @else {
          <div class="internal-context">
            <span>{{ currentAreaLabel() }}</span>
            <strong>{{ currentAreaSummaryTitle() }}</strong>
          </div>

          <div class="session-actions">
            <button mat-stroked-button type="button" class="nav-toggle" (click)="toggleSideNav()">
              {{ sideNavCollapsed() ? 'Mostrar menú' : 'Ocultar menú' }}
            </button>

            <a mat-button class="portal-link" routerLink="/portal">Ver portal</a>

            @if (currentUser(); as user) {
              <button mat-stroked-button type="button" class="user-menu-button" [matMenuTriggerFor]="areasMenu">
                <span class="session-user">
                  <strong>{{ user.displayName }}</strong>
                  <small>{{ roleLabel() }}</small>
                </span>
              </button>

              <mat-menu #areasMenu="matMenu">
                @for (item of areaNavigationItems(); track item.label) {
                  <a
                    mat-menu-item
                    [routerLink]="item.path"
                    routerLinkActive="active-menu-item"
                    [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
                  >
                    {{ item.label }}
                  </a>
                }
                <button mat-menu-item type="button" (click)="logout()">Cerrar sesión</button>
              </mat-menu>
            }
          </div>
        }
      </div>
    </mat-toolbar>

    @if (currentArea() === 'portal') {
      <main class="shell-main portal-main">
        <router-outlet />
      </main>
    } @else {
      <div
        class="internal-shell"
        [class.admin-shell]="currentArea() === 'admin'"
        [class.researcher-shell]="currentArea() === 'researcher'"
      >
        <aside class="side-nav-panel" [attr.aria-label]="currentAreaLabel()">
          <div class="side-nav-heading">
            <div class="side-nav-heading-copy">
              <span>{{ currentAreaLabel() }}</span>
              <strong>{{ currentAreaSummaryTitle() }}</strong>
              <p>{{ currentAreaSummaryText() }}</p>
            </div>
            <button mat-button type="button" class="side-nav-toggle" (click)="toggleSideNav()">
              {{ sideNavCollapsed() ? 'Mostrar navegación' : 'Ocultar navegación' }}
            </button>
          </div>

          <div class="side-nav-content" [class.side-nav-content-collapsed]="sideNavCollapsed()">
            @if (currentArea() === 'admin') {
              <nav class="side-nav-groups">
                @for (group of adminNavigationGroups(); track group.label) {
                  @if (group.items.length > 0) {
                    <section class="side-nav-group">
                      <h2>{{ group.label }}</h2>
                      @for (item of group.items; track item.label) {
                        <a
                          [routerLink]="item.path"
                          routerLinkActive="side-link-active"
                          [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
                          class="side-link"
                        >
                          {{ item.label }}
                        </a>
                      }
                    </section>
                  }
                }
              </nav>
            } @else {
              <nav class="side-nav-links">
                @for (item of researcherNavigation; track item.label) {
                  <a
                    [routerLink]="item.path"
                    routerLinkActive="side-link-active"
                    [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
                    class="side-link"
                  >
                    {{ item.label }}
                  </a>
                }
              </nav>
            }
          </div>
        </aside>

        <main class="internal-main">
          <router-outlet />
        </main>
      </div>
    }
  `,
  styles: [`
    .topbar {
      position: sticky;
      top: 0;
      z-index: 20;
      height: auto;
      min-height: 82px;
      padding: 0 20px;
      border-bottom: 1px solid rgba(214, 220, 227, 0.96);
      background: rgba(255, 255, 255, 0.96);
      backdrop-filter: blur(16px);
      color: #183041;
    }

    .internal-topbar {
      min-height: 64px;
      border-bottom-color: rgba(191, 203, 215, 0.96);
      background: rgba(247, 250, 252, 0.98);
    }

    .topbar-inner {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 24px;
      width: min(1480px, 100%);
      margin: 0 auto;
    }

    .internal-topbar .topbar-inner {
      width: min(1920px, 100%);
      gap: 18px;
    }

    .brand {
      display: inline-flex;
      align-items: center;
      gap: 12px;
      min-width: 0;
      color: #102033;
      text-decoration: none;
    }

    .brand-mark {
      display: inline-grid;
      width: 42px;
      height: 42px;
      flex: 0 0 auto;
      place-items: center;
      border-radius: 10px;
      background: #143449;
      color: #ffffff;
      font-size: 0.92rem;
      font-weight: 800;
      letter-spacing: 0.04em;
    }

    .internal-topbar .brand-mark {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      box-shadow: none;
    }

    .brand-copy {
      display: grid;
      gap: 2px;
      min-width: 0;
    }

    .brand-copy strong {
      color: #102033;
      font-size: 1rem;
      font-weight: 780;
      line-height: 1.1;
      white-space: nowrap;
    }

    .brand-copy small {
      color: #5c7283;
      font-size: 0.77rem;
      line-height: 1.25;
    }

    .internal-topbar .brand-copy small {
      display: none;
    }

    .public-nav {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      min-width: 0;
      flex-wrap: wrap;
    }

    .public-nav a {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-height: 42px;
      padding: 0 12px;
      border-radius: 8px;
      color: #44546a;
      font-size: 0.9rem;
      font-weight: 700;
      text-decoration: none;
      white-space: nowrap;
    }

    .public-nav a:hover,
    .active-link,
    .active-menu-item {
      background: #eef3f6;
      color: #15364a !important;
    }

    .public-session-actions,
    .session-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 10px;
      min-width: 0;
    }

    .login-button,
    .portal-link,
    .user-menu-button,
    .nav-toggle {
      border-radius: 12px;
      white-space: nowrap;
    }

    .internal-context {
      display: grid;
      gap: 2px;
      min-width: 0;
      padding: 6px 0;
    }

    .internal-context span,
    .side-nav-heading span {
      color: #2f6f8f;
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .internal-context strong {
      color: #132235;
      font-size: 0.94rem;
      line-height: 1.18;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .portal-link,
    .user-menu-button {
      min-height: 40px;
    }

    .nav-toggle {
      display: none;
    }

    .session-user {
      display: grid;
      gap: 2px;
      color: #142033;
      font-size: 0.84rem;
      line-height: 1.2;
      text-align: left;
    }

    .session-user small {
      color: #5f7384;
      font-size: 0.74rem;
    }

    .shell-main {
      margin: 0 auto;
      padding: 30px 0 64px;
    }

    .portal-main {
      width: min(1480px, calc(100% - 48px));
    }

    .internal-shell {
      display: grid;
      grid-template-columns: 292px minmax(0, 1fr);
      gap: 24px;
      width: min(2100px, calc(100% - 24px));
      margin: 0 auto;
      padding: 20px 0 56px;
      align-items: start;
    }

    .side-nav-panel {
      position: sticky;
      top: 82px;
      align-self: start;
      display: grid;
      gap: 18px;
      max-height: calc(100vh - 100px);
      overflow: auto;
      padding: 18px;
      border: 1px solid #d3dde7;
      border-radius: 22px;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 250, 252, 0.97)),
        linear-gradient(90deg, rgba(20, 52, 73, 0.05), rgba(47, 111, 139, 0.03));
      box-shadow: 0 22px 44px rgba(20, 32, 51, 0.06);
    }

    .side-nav-heading {
      display: grid;
      gap: 12px;
      padding-bottom: 14px;
      border-bottom: 1px solid #dce5ee;
    }

    .side-nav-heading-copy {
      display: grid;
      gap: 6px;
    }

    .side-nav-heading strong {
      color: #132235;
      font-size: 0.98rem;
      line-height: 1.24;
    }

    .side-nav-heading p {
      margin: 0;
      color: #647688;
      font-size: 0.85rem;
      line-height: 1.5;
    }

    .side-nav-toggle {
      display: none;
      align-self: start;
      padding: 0;
      color: inherit;
    }

    .side-nav-content,
    .side-nav-groups,
    .side-nav-links,
    .side-nav-group {
      display: grid;
      gap: 10px;
    }

    .side-nav-groups {
      gap: 18px;
    }

    .side-nav-group h2 {
      margin: 0 0 2px;
      color: #647386;
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      line-height: 1.2;
      text-transform: uppercase;
    }

    .side-link {
      display: flex;
      align-items: center;
      min-height: 44px;
      padding: 10px 14px;
      border: 1px solid transparent;
      border-radius: 16px;
      color: #334155;
      font-size: 0.92rem;
      font-weight: 700;
      line-height: 1.28;
      text-decoration: none;
      transition: border-color 140ms ease, background-color 140ms ease, color 140ms ease, transform 140ms ease;
    }

    .side-link:hover,
    .side-link-active {
      border-color: #bad2de;
      background: #eef7fb;
      color: #144d67;
      transform: translateX(1px);
    }

    .internal-main {
      min-width: 0;
    }

    @media (max-width: 1180px) {
      .topbar {
        min-height: 96px;
        padding-block: 12px;
      }

      .internal-topbar {
        min-height: 84px;
      }

      .topbar-inner {
        grid-template-columns: 1fr;
        align-items: start;
        gap: 14px;
      }

      .public-nav {
        justify-content: flex-start;
      }

      .public-session-actions,
      .session-actions {
        justify-content: flex-start;
        flex-wrap: wrap;
      }

      .nav-toggle,
      .side-nav-toggle {
        display: inline-flex;
      }

      .internal-shell {
        grid-template-columns: 1fr;
      }

      .side-nav-panel {
        position: static;
        max-height: none;
      }

      .side-nav-content.side-nav-content-collapsed {
        display: none;
      }

      .side-nav-groups {
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      }
    }

    @media (max-width: 640px) {
      .topbar {
        padding-inline: 12px;
      }

      .brand {
        align-items: flex-start;
      }

      .brand-copy strong {
        white-space: normal;
      }

      .public-nav {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 8px;
        width: 100%;
      }

      .public-nav a {
        width: 100%;
        min-height: 38px;
        padding-inline: 12px;
        font-size: 0.86rem;
        white-space: normal;
        text-align: center;
      }

      .public-session-actions,
      .session-actions {
        width: 100%;
      }

      .session-actions {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .public-session-actions .login-button,
      .session-actions .nav-toggle,
      .session-actions .portal-link,
      .session-actions .user-menu-button {
        width: 100%;
        min-width: 0;
      }

      .session-actions .user-menu-button {
        grid-column: 1 / -1;
      }

      .portal-main,
      .internal-shell {
        width: min(100% - 20px, 1920px);
      }

      .shell-main,
      .internal-shell {
        padding-top: 20px;
      }

      .side-nav-panel {
        padding: 16px;
        border-radius: 20px;
      }

      .side-nav-groups {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class ShellComponent {
  private readonly auth = inject(AuthStateService);
  private readonly router = inject(Router);
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url)
    ),
    { initialValue: this.router.url }
  );

  readonly currentUser = this.auth.currentUser;
  readonly roleLabel = this.auth.roleLabel;
  readonly sideNavCollapsed = signal(false);
  readonly currentArea = computed<AreaKey>(() => {
    const url = this.currentUrl();
    if (this.hasPathPrefix(url, '/admin') || this.isLegacyAdminRoute(url)) {
      return 'admin';
    }
    if (this.hasPathPrefix(url, '/app') || this.isLegacyResearcherRoute(url)) {
      return 'researcher';
    }
    return 'portal';
  });
  readonly currentAreaLabel = computed(() => {
    switch (this.currentArea()) {
      case 'admin':
        return 'Administración';
      case 'researcher':
        return 'Área investigador';
      default:
        return 'Portal público';
    }
  });
  readonly currentAreaTagline = computed(() => {
    switch (this.currentArea()) {
      case 'admin':
        return 'Operaciones, validación y control institucional';
      case 'researcher':
        return 'Seguimiento personal de actividad y validación';
      default:
        return 'Portal público institucional';
    }
  });
  readonly currentAreaSummaryTitle = computed(() => {
    switch (this.currentArea()) {
      case 'admin':
        return 'Consola institucional';
      case 'researcher':
        return 'Trabajo personal de investigación';
      default:
        return 'Exploración pública de conocimiento institucional';
    }
  });
  readonly currentAreaSummaryText = computed(() => {
    switch (this.currentArea()) {
      case 'admin':
        return 'Panel, validación, catálogo, inteligencia, calidad y auditoría.';
      case 'researcher':
        return 'Panel personal, asistente privado, actividades propias y recordatorios para mantener tus datos al día.';
      default:
        return 'Consulta unidades, investigadores, publicaciones y herramientas públicas.';
    }
  });
  readonly defaultPrivateAreaPath = computed(() => {
    const user = this.currentUser();
    if (!user) {
      return '/login';
    }
    if (this.canAccessAdminArea(user.roles)) {
      return '/admin/panel';
    }
    if (this.canAccessResearcherArea(user.roles)) {
      return '/app/mi-panel';
    }
    return '/portal';
  });
  readonly areaNavigationItems = computed(() => {
    const user = this.currentUser();
    const items: NavigationItem[] = [];

    if (user && this.canAccessResearcherArea(user.roles)) {
      items.push({ label: 'Área investigador', path: '/app/mi-panel', exact: true });
    }

    if (user && this.canAccessAdminArea(user.roles)) {
      items.push({ label: 'Administración', path: '/admin/panel', exact: true });
    }

    return items;
  });
  readonly adminNavigationGroups = computed<NavigationGroup[]>(() => {
    return this.adminNavigationGroupsConfig.map((group) => ({
      label: group.label,
      items: group.items.filter((item) => !item.roles || this.auth.hasAnyRole(item.roles))
    }));
  });

  readonly publicNavigation: NavigationItem[] = [
    { label: 'Inicio', path: '/portal', exact: true },
    { label: 'Unidades', path: '/portal/unidades' },
    { label: 'Investigadores', path: '/portal/investigadores' },
    { label: 'Publicaciones', path: '/portal/publicaciones' },
    { label: 'Guía de expertos', path: '/portal/guia-expertos' },
    { label: 'Asistente', path: '/portal/asistente' }
  ];

  readonly researcherNavigation: NavigationItem[] = [
    { label: 'Mi panel', path: '/app/mi-panel', exact: true },
    { label: 'Asistente personal', path: '/app/asistente' },
    { label: 'Mis actividades', path: '/app/mis-actividades' },
    { label: 'Añadir participación', path: '/app/actividades/nueva', exact: true }
  ];

  private readonly adminNavigationGroupsConfig: NavigationGroup[] = [
    {
      label: 'Panel',
      items: [
        { label: 'Panel institucional', path: '/admin/panel', exact: true }
      ]
    },
    {
      label: 'Validación',
      items: [
        { label: 'Bandeja de validación', path: '/admin/validacion' }
      ]
    },
    {
      label: 'Catálogo',
      items: [
        { label: 'Publicaciones', path: '/admin/publicaciones' },
        { label: 'Investigadores', path: '/admin/investigadores' },
        { label: 'Unidades internas', path: '/admin/unidades' },
        { label: 'Organizaciones externas', path: '/admin/organizaciones-externas' },
        { label: 'Participaciones', path: '/admin/participaciones' },
        { label: 'Eventos', path: '/admin/eventos' },
        { label: 'Canales', path: '/admin/canales' },
        { label: 'Editoriales', path: '/admin/editoriales' },
        { label: 'Datos maestros', path: '/admin/datos-maestros', roles: ['ADMIN'] },
        { label: 'Ingesta CSV', path: '/admin/ingesta' }
      ]
    },
    {
      label: 'Inteligencia',
      items: [
        { label: 'Noticias', path: '/admin/noticias', roles: ['ADMIN'] },
        { label: 'Mapa estratégico', path: '/admin/mapa-estrategico' },
        { label: 'Asistente interno', path: '/admin/asistente' },
        { label: 'Sugerencias IA', path: '/admin/sugerencias-ia' },
        { label: 'Oportunidades de colaboración', path: '/admin/oportunidades-colaboracion' },
        { label: 'Informes', path: '/admin/informes', roles: ['ADMIN'] },
        { label: 'Normalización de temas', path: '/admin/normalizacion-temas', roles: ['ADMIN'] }
      ]
    },
    {
      label: 'Calidad y auditoría',
      items: [
        { label: 'Calidad de datos', path: '/admin/calidad-datos', roles: ['ADMIN'] },
        { label: 'Auditoría', path: '/admin/auditoria' }
      ]
    }
  ];

  constructor() {
    this.auth.refreshCurrentUser();
  }

  logout(): void {
    this.auth.logout();
  }

  toggleSideNav(): void {
    this.sideNavCollapsed.update((value) => !value);
  }

  private canAccessResearcherArea(roles: RoleCode[]): boolean {
    return roles.includes('RESEARCHER') || roles.includes('ADMIN');
  }

  private canAccessAdminArea(roles: RoleCode[]): boolean {
    return roles.includes('ADMIN') || roles.includes('VALIDATOR');
  }

  private isLegacyAdminRoute(url: string): boolean {
    return [
      '/dashboard',
      '/validation',
      '/audit',
      '/data-quality',
      '/reports',
      '/collaboration-opportunities',
      '/topic-normalization',
      '/ingestion',
      '/master-data'
    ].some((prefix) => this.hasPathPrefix(url, prefix));
  }

  private isLegacyResearcherRoute(url: string): boolean {
    return ['/my-dashboard', '/my-activities'].some((prefix) => this.hasPathPrefix(url, prefix));
  }

  private hasPathPrefix(url: string, prefix: string): boolean {
    const path = url.split(/[?#]/)[0];
    return path === prefix || path.startsWith(`${prefix}/`);
  }
}
