import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { TagChipComponent } from '../../shared/components/tag-chip.component';

interface AdminShortcut {
  title: string;
  description: string;
  path: string;
  cta: string;
  chip: string;
}

@Component({
  selector: 'rip-admin-placeholder-page',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatCardModule, EmptyStateComponent, PageHeaderComponent, TagChipComponent],
  template: `
    <section class="page">
      <rip-page-header [title]="title" [subtitle]="subtitle" eyebrow="Gestión institucional">
        <a mat-button routerLink="/admin/panel">Panel institucional</a>
        <a mat-button routerLink="/admin/validacion">Bandeja de validación</a>
      </rip-page-header>

      <div class="toolbar-meta">
        <p>Esta vista consolida accesos administrativos relacionados con mantenimiento y curación del catálogo interno.</p>
        <div class="summary-strip">
          <span class="summary-chip">
            <strong>{{ shortcuts.length }}</strong>
            <span>accesos operativos</span>
          </span>
        </div>
      </div>

      <mat-card appearance="outlined">
        <mat-card-content class="intro-card">
          <div>
            <p class="section-kicker">Datos maestros</p>
            <h2>Área privada para mantenimiento del catálogo</h2>
            <p class="bounded-text">
              Todavía no existe un workspace único de administración de datos maestros, pero esta página ya sirve como
              punto de entrada privado para las entidades que requieren revisión, normalización y edición controlada.
            </p>
          </div>
          <rip-tag-chip label="Solo administración" tone="type" />
        </mat-card-content>
      </mat-card>

      <div class="shortcut-grid">
        @for (shortcut of shortcuts; track shortcut.path) {
          <mat-card appearance="outlined">
            <mat-card-content class="shortcut-card">
              <div class="shortcut-topline">
                <rip-tag-chip [label]="shortcut.chip" tone="type" />
              </div>
              <div class="shortcut-copy">
                <h3>{{ shortcut.title }}</h3>
                <p>{{ shortcut.description }}</p>
              </div>
              <div class="actions">
                <a mat-stroked-button [routerLink]="shortcut.path">{{ shortcut.cta }}</a>
              </div>
            </mat-card-content>
          </mat-card>
        }
      </div>

      <rip-empty-state
        title="Workspace dedicado pendiente"
        message="Cuando exista una consola unificada de datos maestros, aparecerá aquí sin mezclarla con la navegación del portal público."
      />
    </section>
  `,
  styles: [`
    .intro-card,
    .shortcut-card {
      display: grid;
      gap: 18px;
    }

    .intro-card {
      align-items: start;
      grid-template-columns: minmax(0, 1fr) auto;
    }

    .intro-card h2,
    .shortcut-copy h3 {
      margin: 0;
      color: #142033;
    }

    .shortcut-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 20px;
    }

    .shortcut-topline {
      display: flex;
      justify-content: flex-start;
    }

    .shortcut-copy {
      display: grid;
      gap: 8px;
    }

    .shortcut-copy p {
      margin: 0;
      color: #667487;
      line-height: 1.55;
    }

    @media (max-width: 720px) {
      .intro-card {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class AdminPlaceholderPageComponent {
  private readonly route = inject(ActivatedRoute);

  readonly title = this.route.snapshot.data['title'] as string;
  readonly subtitle = this.route.snapshot.data['subtitle'] as string;
  readonly shortcuts: AdminShortcut[] = [
    {
      title: 'Investigadores',
      description: 'Revisa perfiles, afiliaciones y estados de validación desde la vista de mantenimiento.',
      path: '/admin/investigadores',
      cta: 'Abrir investigadores',
      chip: 'Perfiles'
    },
    {
      title: 'Unidades internas',
      description: 'Gestiona estructura, jerarquías y visibilidad pública de las unidades que sí forman parte de la institución.',
      path: '/admin/unidades',
      cta: 'Abrir unidades internas',
      chip: 'Estructura'
    },
    {
      title: 'Organizaciones externas',
      description: 'Mantén hospitales, empresas, fundaciones y otras entidades colaboradoras fuera del directorio público de unidades.',
      path: '/admin/organizaciones-externas',
      cta: 'Abrir organizaciones externas',
      chip: 'Colaboración'
    },
    {
      title: 'Publicaciones',
      description: 'Consulta registros maestros de producción científica y su enriquecimiento temático.',
      path: '/admin/publicaciones',
      cta: 'Abrir publicaciones',
      chip: 'Producción'
    },
    {
      title: 'Eventos y participaciones',
      description: 'Mantén eventos científicos y la trazabilidad de participaciones asociadas.',
      path: '/admin/eventos',
      cta: 'Abrir eventos',
      chip: 'Actividad'
    },
    {
      title: 'Canales',
      description: 'Revisa venues y metadatos de publicación para mejorar consistencia.',
      path: '/admin/canales',
      cta: 'Abrir canales',
      chip: 'Metadatos'
    },
    {
      title: 'Editoriales',
      description: 'Administra editoriales reutilizables para publicaciones y canales de difusión.',
      path: '/admin/editoriales',
      cta: 'Abrir editoriales',
      chip: 'Catálogo'
    },
    {
      title: 'Normalización temática',
      description: 'Avanza en la curación y consolidación de temas antes de ampliar el workspace maestro.',
      path: '/admin/normalizacion-temas',
      cta: 'Abrir normalización',
      chip: 'Curación'
    }
  ];
}
