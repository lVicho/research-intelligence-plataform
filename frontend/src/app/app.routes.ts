import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'portal'
  },
  {
    path: 'portal',
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./features/portal/portal-home-page.component').then((m) => m.PortalHomePageComponent)
      },
      {
        path: 'noticias',
        loadComponent: () => import('./features/portal/portal-news-page.component').then((m) => m.PortalNewsPageComponent)
      },
      {
        path: 'noticias/:id',
        loadComponent: () => import('./features/portal/portal-news-detail-page.component').then((m) => m.PortalNewsDetailPageComponent)
      },
      {
        path: 'unidades',
        loadComponent: () => import('./features/portal/portal-research-units-page.component').then((m) => m.PortalResearchUnitsPageComponent)
      },
      {
        path: 'unidades/:id',
        loadComponent: () => import('./features/portal/portal-research-unit-detail-page.component').then((m) => m.PortalResearchUnitDetailPageComponent)
      },
      {
        path: 'investigadores',
        loadComponent: () => import('./features/portal/portal-researchers-page.component').then((m) => m.PortalResearchersPageComponent)
      },
      {
        path: 'investigadores/:id',
        loadComponent: () => import('./features/portal/portal-researcher-detail-page.component').then((m) => m.PortalResearcherDetailPageComponent)
      },
      {
        path: 'publicaciones/:id',
        loadComponent: () => import('./features/portal/portal-publication-detail-page.component').then((m) => m.PortalPublicationDetailPageComponent)
      },
      {
        path: 'publicaciones',
        data: { portalView: true },
        loadComponent: () => import('./features/publications/publications-page.component').then((m) => m.PublicationsPageComponent)
      },
      {
        path: 'guia-expertos',
        data: { portalView: true },
        loadComponent: () => import('./features/recommendations/expert-finder-page.component').then((m) => m.ExpertFinderPageComponent)
      },
      {
        path: 'asistente',
        pathMatch: 'full',
        redirectTo: '/portal'
      },
      {
        path: 'busqueda',
        pathMatch: 'full',
        redirectTo: 'publicaciones'
      },
      {
        path: 'copiloto',
        pathMatch: 'full',
        redirectTo: '/portal'
      },
      {
        path: 'mapa-estrategico',
        pathMatch: 'full',
        redirectTo: '/admin/mapa-estrategico'
      }
    ]
  },
  {
    path: 'app',
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'mi-panel'
      },
      {
        path: 'mi-panel',
        canActivate: [authGuard],
        data: { roles: ['RESEARCHER', 'ADMIN'] },
        loadComponent: () => import('./features/auth/my-dashboard-page.component').then((m) => m.MyDashboardPageComponent)
      },
      {
        path: 'mis-actividades',
        canActivate: [authGuard],
        data: { roles: ['RESEARCHER', 'ADMIN'] },
        loadComponent: () => import('./features/auth/my-activities-page.component').then((m) => m.MyActivitiesPageComponent)
      },
      {
        path: 'asistente',
        canActivate: [authGuard],
        data: { roles: ['RESEARCHER', 'ADMIN'] },
        loadComponent: () => import('./features/auth/researcher-assistant-page.component').then((m) => m.ResearcherAssistantPageComponent)
      },
      {
        path: 'actividades',
        children: [
          {
            path: 'nueva',
            canActivate: [authGuard],
            data: { roles: ['RESEARCHER', 'ADMIN'] },
            loadComponent: () => import('./features/participations/participation-detail-page.component').then((m) => m.ParticipationDetailPageComponent)
          },
          {
            path: ':id',
            canActivate: [authGuard],
            data: { roles: ['RESEARCHER', 'ADMIN'] },
            loadComponent: () => import('./features/participations/participation-detail-page.component').then((m) => m.ParticipationDetailPageComponent)
          }
        ]
      }
    ]
  },
  {
    path: 'admin',
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'panel'
      },
      {
        path: 'panel',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/dashboard/dashboard-page.component').then((m) => m.DashboardPageComponent)
      },
      {
        path: 'validacion',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/validation/validation-inbox-page.component').then((m) => m.ValidationInboxPageComponent)
      },
      {
        path: 'auditoria',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'], title: 'Auditoría', subtitle: 'Registro de cambios y actividad institucional.' },
        loadComponent: () => import('./features/admin/audit-page.component').then((m) => m.AuditPageComponent)
      },
      {
        path: 'calidad-datos',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/admin/data-quality-page.component').then((m) => m.DataQualityPageComponent)
      },
      {
        path: 'informes',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/admin/reports-page.component').then((m) => m.ReportsPageComponent)
      },
      {
        path: 'oportunidades-colaboracion',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/admin/collaboration-opportunities-page.component').then((m) => m.CollaborationOpportunitiesPageComponent)
      },
      {
        path: 'normalizacion-temas',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/admin/topic-normalization-page.component').then((m) => m.TopicNormalizationPageComponent)
      },
      {
        path: 'ingesta',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/ingestion/ingestion-page.component').then((m) => m.IngestionPageComponent)
      },
      {
        path: 'datos-maestros',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'], title: 'Datos maestros', subtitle: 'Gestión de unidades, investigadores y publicaciones maestras.' },
        loadComponent: () => import('./features/admin/admin-placeholder-page.component').then((m) => m.AdminPlaceholderPageComponent)
      },
      {
        path: 'mapa-estrategico',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/strategic-map/strategic-research-map-page.component').then((m) => m.StrategicResearchMapPageComponent)
      },
      {
        path: 'asistente',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/copilot/copilot-page.component').then((m) => m.CopilotPageComponent)
      },
      {
        path: 'sugerencias-ia',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/admin/ai-suggestions-page.component').then((m) => m.AiSuggestionsPageComponent)
      },
      {
        path: 'noticias',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/admin/admin-news-page.component').then((m) => m.AdminNewsPageComponent)
      },
      {
        path: 'noticias/new',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/admin/admin-news-detail-page.component').then((m) => m.AdminNewsDetailPageComponent)
      },
      {
        path: 'noticias/:id',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/admin/admin-news-detail-page.component').then((m) => m.AdminNewsDetailPageComponent)
      },
      {
        path: 'publicaciones',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/publications/publications-page.component').then((m) => m.PublicationsPageComponent)
      },
      {
        path: 'publicaciones/new',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/publications/publication-detail-page.component').then((m) => m.PublicationDetailPageComponent)
      },
      {
        path: 'publicaciones/:id',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/publications/publication-detail-page.component').then((m) => m.PublicationDetailPageComponent)
      },
      {
        path: 'investigadores',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/researchers/researchers-page.component').then((m) => m.ResearchersPageComponent)
      },
      {
        path: 'investigadores/new',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/researchers/researcher-detail-page.component').then((m) => m.ResearcherDetailPageComponent)
      },
      {
        path: 'investigadores/:id',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/researchers/researcher-detail-page.component').then((m) => m.ResearcherDetailPageComponent)
      },
      {
        path: 'unidades',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'], organizationScope: 'INTERNAL' },
        loadComponent: () => import('./features/research-units/research-units-page.component').then((m) => m.ResearchUnitsPageComponent)
      },
      {
        path: 'unidades/new',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'], organizationScope: 'INTERNAL' },
        loadComponent: () => import('./features/research-units/research-unit-detail-page.component').then((m) => m.ResearchUnitDetailPageComponent)
      },
      {
        path: 'unidades/:id',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'], organizationScope: 'INTERNAL' },
        loadComponent: () => import('./features/research-units/research-unit-detail-page.component').then((m) => m.ResearchUnitDetailPageComponent)
      },
      {
        path: 'organizaciones-externas',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'], organizationScope: 'EXTERNAL' },
        loadComponent: () => import('./features/research-units/research-units-page.component').then((m) => m.ResearchUnitsPageComponent)
      },
      {
        path: 'organizaciones-externas/new',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'], organizationScope: 'EXTERNAL' },
        loadComponent: () => import('./features/research-units/research-unit-detail-page.component').then((m) => m.ResearchUnitDetailPageComponent)
      },
      {
        path: 'organizaciones-externas/:id',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'], organizationScope: 'EXTERNAL' },
        loadComponent: () => import('./features/research-units/research-unit-detail-page.component').then((m) => m.ResearchUnitDetailPageComponent)
      },
      {
        path: 'eventos',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/events/events-page.component').then((m) => m.EventsPageComponent)
      },
      {
        path: 'eventos/new',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/events/event-detail-page.component').then((m) => m.EventDetailPageComponent)
      },
      {
        path: 'eventos/:id',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/events/event-detail-page.component').then((m) => m.EventDetailPageComponent)
      },
      {
        path: 'participaciones',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/participations/participations-page.component').then((m) => m.ParticipationsPageComponent)
      },
      {
        path: 'participaciones/:id',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/participations/participation-detail-page.component').then((m) => m.ParticipationDetailPageComponent)
      },
      {
        path: 'canales',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/venues/venues-page.component').then((m) => m.VenuesPageComponent)
      },
      {
        path: 'canales/new',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/venues/venue-detail-page.component').then((m) => m.VenueDetailPageComponent)
      },
      {
        path: 'canales/:id',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/venues/venue-detail-page.component').then((m) => m.VenueDetailPageComponent)
      },
      {
        path: 'editoriales',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/publishers/publishers-page.component').then((m) => m.PublishersPageComponent)
      },
      {
        path: 'editoriales/new',
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/publishers/publisher-detail-page.component').then((m) => m.PublisherDetailPageComponent)
      },
      {
        path: 'editoriales/:id',
        canActivate: [authGuard],
        data: { roles: ['ADMIN', 'VALIDATOR'] },
        loadComponent: () => import('./features/publishers/publisher-detail-page.component').then((m) => m.PublisherDetailPageComponent)
      }
    ]
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login-page.component').then((m) => m.LoginPageComponent)
  },
  {
    path: 'dashboard',
    pathMatch: 'full',
    redirectTo: 'admin/panel'
  },
  {
    path: 'validation',
    pathMatch: 'full',
    redirectTo: 'admin/validacion'
  },
  {
    path: 'audit',
    pathMatch: 'full',
    redirectTo: 'admin/auditoria'
  },
  {
    path: 'data-quality',
    pathMatch: 'full',
    redirectTo: 'admin/calidad-datos'
  },
  {
    path: 'reports',
    pathMatch: 'full',
    redirectTo: 'admin/informes'
  },
  {
    path: 'collaboration-opportunities',
    pathMatch: 'full',
    redirectTo: 'admin/oportunidades-colaboracion'
  },
  {
    path: 'topic-normalization',
    pathMatch: 'full',
    redirectTo: 'admin/normalizacion-temas'
  },
  {
    path: 'ingestion',
    pathMatch: 'full',
    redirectTo: 'admin/ingesta'
  },
  {
    path: 'master-data',
    pathMatch: 'full',
    redirectTo: 'admin/datos-maestros'
  },
  {
    path: 'my-dashboard',
    pathMatch: 'full',
    redirectTo: 'app/mi-panel'
  },
  {
    path: 'my-activities',
    pathMatch: 'full',
    redirectTo: 'app/mis-actividades'
  },
  {
    path: 'participations/new',
    pathMatch: 'full',
    redirectTo: 'app/actividades/nueva'
  },
  {
    path: 'research-units',
    data: { organizationScope: 'INTERNAL' },
    loadComponent: () => import('./features/research-units/research-units-page.component').then((m) => m.ResearchUnitsPageComponent)
  },
  {
    path: 'research-units/new',
    canActivate: [authGuard],
    data: { roles: ['ADMIN'], organizationScope: 'INTERNAL' },
    loadComponent: () => import('./features/research-units/research-unit-detail-page.component').then((m) => m.ResearchUnitDetailPageComponent)
  },
  {
    path: 'research-units/:id',
    data: { organizationScope: 'INTERNAL' },
    loadComponent: () => import('./features/research-units/research-unit-detail-page.component').then((m) => m.ResearchUnitDetailPageComponent)
  },
  {
    path: 'researchers',
    loadComponent: () => import('./features/researchers/researchers-page.component').then((m) => m.ResearchersPageComponent)
  },
  {
    path: 'researchers/new',
    canActivate: [authGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/researchers/researcher-detail-page.component').then((m) => m.ResearcherDetailPageComponent)
  },
  {
    path: 'researchers/:id',
    loadComponent: () => import('./features/researchers/researcher-detail-page.component').then((m) => m.ResearcherDetailPageComponent)
  },
  {
    path: 'venues',
    loadComponent: () => import('./features/venues/venues-page.component').then((m) => m.VenuesPageComponent)
  },
  {
    path: 'venues/new',
    canActivate: [authGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/venues/venue-detail-page.component').then((m) => m.VenueDetailPageComponent)
  },
  {
    path: 'venues/:id',
    loadComponent: () => import('./features/venues/venue-detail-page.component').then((m) => m.VenueDetailPageComponent)
  },
  {
    path: 'events',
    loadComponent: () => import('./features/events/events-page.component').then((m) => m.EventsPageComponent)
  },
  {
    path: 'events/new',
    canActivate: [authGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/events/event-detail-page.component').then((m) => m.EventDetailPageComponent)
  },
  {
    path: 'events/:id',
    loadComponent: () => import('./features/events/event-detail-page.component').then((m) => m.EventDetailPageComponent)
  },
  {
    path: 'participations',
    loadComponent: () => import('./features/participations/participations-page.component').then((m) => m.ParticipationsPageComponent)
  },
  {
    path: 'participations/:id',
    loadComponent: () => import('./features/participations/participation-detail-page.component').then((m) => m.ParticipationDetailPageComponent)
  },
  {
    path: 'publications',
    loadComponent: () => import('./features/publications/publications-page.component').then((m) => m.PublicationsPageComponent)
  },
  {
    path: 'semantic-search',
    pathMatch: 'full',
    redirectTo: 'portal/publicaciones'
  },
  {
    path: 'publications/new',
    canActivate: [authGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/publications/publication-detail-page.component').then((m) => m.PublicationDetailPageComponent)
  },
  {
    path: 'publications/:id',
    loadComponent: () => import('./features/publications/publication-detail-page.component').then((m) => m.PublicationDetailPageComponent)
  },
  {
    path: 'copilot',
    loadComponent: () => import('./features/copilot/copilot-page.component').then((m) => m.CopilotPageComponent)
  },
  {
    path: '**',
    redirectTo: 'portal'
  }
];
