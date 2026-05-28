# Frontend Current State

This document captures the current frontend state in the repository before any large visual polish phase.

Scope:

- Based on the current Angular code under `frontend/src/app/**`
- Focused on inventory and planning, not design proposals or implementation
- Includes current route structure, shared UI building blocks, and likely UX risks inferred from code structure

Status labels used in the route map:

- `complete`: implemented and clearly usable in its current scope
- `partial`: implemented, but still mixed, MVP-like, or likely to need follow-up polish/workflow clarification
- `placeholder`: intentionally not a full workspace yet
- `unknown`: purpose or maturity is not clear from the current code

## 1. Current Route Map

### Public Portal

| Route path | Page / component | Purpose | Role / visibility | Main API calls | Current status |
| --- | --- | --- | --- | --- | --- |
| `/portal` | `PortalHomePageComponent` | Public landing page with highlighted units, researchers, publications, topics, and portal entry points | Public, validated-only intent | `PortalApiService.summary`, `PortalApiService.researchers` | `complete` |
| `/portal/unidades` | `PortalResearchUnitsPageComponent` | Public institutional unit directory with hierarchy snapshot and portal-only filtering of internal units | Public, validated-only | `PortalApiService.researchUnits`, per-card `PortalApiService.researchUnit` for summary metrics/topics | `complete` |
| `/portal/unidades/:id` | `PortalResearchUnitDetailPageComponent` | Public unit detail with institutional hierarchy, validated researchers, publications, activities, and secondary references only | Public, validated-only | `PortalApiService.researchUnit`, `PortalApiService.researchUnits` | `complete` |
| `/portal/investigadores` | `PortalResearchersPageComponent` | Public researcher directory with lighter filters, improved cards, and only portal-visible institutional affiliations | Public, validated-only | `PortalApiService.researchers`, `PortalApiService.researcher`, `PortalApiService.researchUnits` | `complete` |
| `/portal/investigadores/:id` | `PortalResearcherDetailPageComponent` | Public researcher profile with affiliations, visible publications/activities, coauthors, and graph shown only when readable | Public, validated-only | `PortalApiService.researcher`, embedded `GraphApiService` via graph component | `complete` |
| `/portal/publicaciones` | `PublicationsPageComponent` | Public publications catalogue with one shared search bar, segmented field/semantic mode selection, secondary advanced filters, and one result list | Public via `portalView` route data | `PublicationsApiService.search`, `PublicationsApiService.semanticSearch`, `AcademicMasterDataService`, `ResearchersApiService.search` | `complete` |
| `/portal/guia-expertos` | `ExpertFinderPageComponent` | Public expert finder using semantic publication retrieval and researcher/profile aggregation | Public, validated-only intent | `PublicationsApiService.semanticSearch/get`, `ResearchersApiService.get`, `ResearchUnitsApiService.list` | `partial` |
| `/portal/asistente` | `CopilotPageComponent` | Public validated-only assistant flow with answer-first layout, cited publications below the response, collapsible context, and secondary support evaluation | Public via `portalView` route data | `CopilotApiService.retrieve`, `CopilotApiService.answer` | `complete` |

### Researcher Private Area

| Route path | Page / component | Purpose | Role / visibility | Main API calls | Current status |
| --- | --- | --- | --- | --- | --- |
| `/app/mi-panel` | `MyDashboardPageComponent` | Researcher workspace dashboard with profile summary, activity counts, topics, recent items, and quality reminders | Guarded for `RESEARCHER` or `ADMIN` | `MeApiService.dashboard` | `complete` |
| `/app/mis-actividades` | `MyActivitiesPageComponent` | Researcher workflow page for filtering own items, reading validation comments, checking audit trail, editing, and submitting/re-submitting | Guarded for `RESEARCHER` or `ADMIN` | `MeApiService.activities`, `MeApiService.activityDetail`, `MeApiService.submitActivity`, `AcademicMasterDataService` | `complete` |
| `/app/actividades/nueva` | `ParticipationDetailPageComponent` | Researcher/admin create flow for event participation activity records | Guarded for `RESEARCHER` or `ADMIN` | `EventParticipationsApiService`, `ScientificEventsApiService.search`, `ResearchUnitsApiService.list`, `PublicationsApiService.search` | `complete` |

### Admin / Validator Area

| Route path | Page / component | Purpose | Role / visibility | Main API calls | Current status |
| --- | --- | --- | --- | --- | --- |
| `/admin/panel` | `DashboardPageComponent` | Institutional dashboard for analytics, validation overview, collaboration and data-quality signals | Guarded for `ADMIN` or `VALIDATOR` | `AnalyticsApiService.summary`, `CollaborationOpportunitiesApiService`, `DataQualityApiService.overview`, `ValidationApiService.inbox` | `complete` |
| `/admin/validacion` | `ValidationInboxPageComponent` | Validation inbox with filters, detail pane, audit history, and action dialog for validate/reject/request changes | Guarded for `ADMIN` or `VALIDATOR` | `ValidationApiService.inbox/get/validate/reject/requestChanges`, `AcademicMasterDataService` | `complete` |
| `/admin/auditoria` | `AuditPageComponent` | Institutional audit event browser with filters and paginated event table | Guarded for `ADMIN` or `VALIDATOR` | `AuditApiService.events` | `partial` |
| `/admin/calidad-datos` | `DataQualityPageComponent` | Data-quality overview with issue summary, severity filters, and deep links to affected records | Guarded for `ADMIN` only | `DataQualityApiService.overview` | `partial` |
| `/admin/informes` | `ReportsPageComponent` | Admin-only dossier/report generator with target selection, rendered preview, citations, and Markdown export | Guarded for `ADMIN` only | `ReportsService.loadContext`, `ReportsService.generateReport`, `ReportsService.loadStrategicLineTargets` | `partial` |
| `/admin/oportunidades-colaboracion` | `CollaborationOpportunitiesPageComponent` | Admin/validator analytical page for potential unit collaboration pairs | Guarded for `ADMIN` or `VALIDATOR` | `CollaborationOpportunitiesApiService` | `partial` |
| `/admin/normalizacion-temas` | `TopicNormalizationPageComponent` | Topic curation page for candidate duplicate groups, canonical label editing, ignore/merge actions | Guarded for `ADMIN` only | `TopicNormalizationApiService` | `partial` |
| `/admin/ingesta` | `IngestionPageComponent` | CSV publication ingestion page with upload, report metrics, and per-row errors | Guarded for `ADMIN` or `VALIDATOR` | `IngestionApiService.uploadPublicationsCsv` | `complete` |
| `/admin/datos-maestros` | `AdminPlaceholderPageComponent` | Admin-only hub page linking to shared entity maintenance pages | Guarded for `ADMIN` only | No direct API calls; route-data title/subtitle only | `placeholder` |

### AI / Search / Reporting Area

| Route path | Page / component | Purpose | Role / visibility | Main API calls | Current status |
| --- | --- | --- | --- | --- | --- |
| `/semantic-search` | redirect | Legacy semantic search alias redirected to `/portal/publicaciones` | Public/shared legacy route | None | `complete` |
| `/copilot` | `CopilotPageComponent` | Legacy non-portal assistant route; admin users can broaden retrieval scope here | Public/shared legacy route with role-sensitive options | `CopilotApiService.retrieve`, `CopilotApiService.answer` | `complete` |

### Shared / Auth Pages

These routes are important because they are still part of the current app surface, even when they are not cleanly separated into `/portal`, `/app`, or `/admin`.

| Route path | Page / component | Purpose | Role / visibility | Main API calls | Current status |
| --- | --- | --- | --- | --- | --- |
| `/` | redirect | Redirect root entry to `/portal` | Public | None | `complete` |
| `/login` | `LoginPageComponent` | Session entry page with return URL handling | Public | `AuthStateService.login` | `complete` |
| `/research-units` | `ResearchUnitsPageComponent` | Legacy shared unit directory; same component as portal view, but admin actions appear when role allows | Public/shared; admin-enhanced when logged in | `ResearchUnitsApiService.list`, `ResearchersApiService.search`, `PublicationsApiService.search` | `partial` |
| `/research-units/new` | `ResearchUnitDetailPageComponent` | Admin create flow for research units | Guarded for `ADMIN` | `ResearchUnitsApiService` plus related researchers/publications calls where needed | `complete` |
| `/research-units/:id` | `ResearchUnitDetailPageComponent` | Shared unit detail route used outside `/portal`; shows admin controls when allowed | Public/shared; admin-enhanced when logged in | `ResearchUnitsApiService.get/list`, `ResearchersApiService.search`, `PublicationsApiService.search` | `partial` |
| `/researchers` | `ResearchersPageComponent` | Legacy shared researcher directory; admin sees extra controls and non-portal detail links | Public/shared; admin-enhanced when logged in | `ResearchersApiService.search/get`, `ResearchUnitsApiService.list` | `partial` |
| `/researchers/new` | `ResearcherDetailPageComponent` | Admin create flow for researchers | Guarded for `ADMIN` | `ResearchersApiService`, `ResearchUnitsApiService`, `EventParticipationsApiService` | `complete` |
| `/researchers/:id` | `ResearcherDetailPageComponent` | Shared researcher detail route with admin editing/audit and embedded graph | Public/shared; admin-enhanced when logged in | `ResearchersApiService.get`, `ResearchUnitsApiService.list`, `EventParticipationsApiService.search`, embedded `GraphApiService` | `partial` |
| `/publications` | `PublicationsPageComponent` | Legacy shared publications list; admin can create and optionally include non-validated semantic results | Public/shared; admin-enhanced when logged in | `PublicationsApiService.search`, `PublicationsApiService.semanticSearch`, `AcademicMasterDataService` | `partial` |
| `/publications/new` | `PublicationDetailPageComponent` | Admin create flow for publications | Guarded for `ADMIN` | `PublicationsApiService`, `ResearchersApiService.search`, `VenuesApiService`, `PublishersApiService`, `AcademicMasterDataService` | `complete` |
| `/publications/:id` | `PublicationDetailPageComponent` | Shared publication detail route for public and admin contexts; includes related content and admin edit/audit sections | Public/shared; admin-enhanced when logged in | `PublicationsApiService.get`, related publication retrieval, `VenuesApiService.get`, `PublishersApiService.get`, `ResearchersApiService.search` | `partial` |
| `/events` | `EventsPageComponent` | Event catalogue with filters and validation status; primarily internal/master-data oriented | Public/shared; create action is admin-only | `ScientificEventsApiService.search`, `VenuesApiService.search`, `AcademicMasterDataService` | `complete` |
| `/events/new` | `EventDetailPageComponent` | Admin create flow for scientific events | Guarded for `ADMIN` | `ScientificEventsApiService`, `VenuesApiService.search`, `AcademicMasterDataService` | `complete` |
| `/events/:id` | `EventDetailPageComponent` | Shared event detail route with admin edit form | Public/shared; admin-enhanced when logged in | `ScientificEventsApiService.get`, `VenuesApiService.search` | `partial` |
| `/participations` | `ParticipationsPageComponent` | Participation list with event/researcher/status filters | Public/shared route; create action only for `RESEARCHER` or `ADMIN` | `EventParticipationsApiService.search`, `ScientificEventsApiService.search`, `AcademicMasterDataService` | `complete` |
| `/participations/new` | redirect | Legacy create link redirected to `/app/actividades/nueva` | Guarded by target route for `RESEARCHER` or `ADMIN` | None | `complete` |
| `/participations/:id` | `ParticipationDetailPageComponent` | Participation detail, submission to validation, comments, and audit history | Public/shared route with role- and ownership-based actions | `EventParticipationsApiService.get/submit`, related event/publication/researcher/unit APIs | `complete` |
| `/venues` | `VenuesPageComponent` | Venue/channel list with filters and validation status | Public/shared; create action is admin-only | `VenuesApiService.search`, `AcademicMasterDataService` | `complete` |
| `/venues/new` | `VenueDetailPageComponent` | Admin create flow for venues/channels | Guarded for `ADMIN` | `VenuesApiService`, `AcademicMasterDataService` | `complete` |
| `/venues/:id` | `VenueDetailPageComponent` | Venue detail with admin edit form | Public/shared; admin-enhanced when logged in | `VenuesApiService.get`, `AcademicMasterDataService` | `partial` |
| `**` | redirect | Catch-all redirect to `/portal` | Public | None | `complete` |

Notes:

- There is no dedicated routed graph page. `ResearcherGraphComponent` is embedded inside researcher detail.
- Canonical private/admin route families now exist under `/app/**` and `/admin/**`.
- There is no dedicated system/status route at present.

## 2. Navigation Structure

### Top Navigation

Current shell behavior is implemented in `frontend/src/app/core/layout/shell.component.ts`.

- The shell uses a public top bar for `/portal/**` and a compact internal top bar for `/app/**` and `/admin/**`.
- In the public area (`currentArea === 'portal'`), the top bar shows only public portal navigation:
  - `Inicio`
  - `Unidades`
  - `Investigadores`
  - `Publicaciones`
  - `Guía de expertos`
  - `Asistente`
- Public navigation no longer includes a separate `Búsqueda` item, `Mapa estratégico`, or a `Más` menu.
- In internal areas, the top bar shows product identity, the current area, user/session controls, a `Cambiar área` menu, and logout.
- Internal top bars do not duplicate all workspace links and do not show the public portal menu.

### Workspace Navigation

- Researcher and admin areas now use side navigation instead of the former horizontal workspace chip bar.
- Researcher side navigation entries:
  - `Mi panel`
  - `Mis actividades`
  - `Nueva actividad`
- Admin side navigation is grouped:
  - `Panel`: `Panel institucional`
  - `Validación`: `Bandeja de validación`
  - `Catálogo`: `Datos maestros`, `Ingesta CSV`
  - `Inteligencia`: `Oportunidades de colaboración`, `Informes`, `Normalización de temas`
  - `Calidad y auditoría`: `Calidad de datos`, `Auditoría`

### User Menu

- Anonymous public users see `Iniciar sesión`.
- Logged-in users on the public portal see a single `Entrar al área interna` action instead of admin/researcher navigation menus.
- Logged-in users in internal areas see compact user identity, `Cambiar área`, and `Cerrar sesión`.
- The area menu includes `Ver portal público` plus role-allowed internal areas.

### Public / Private / Admin Separation

What is clearly separated now:

- Public portal routes under `/portal/**`
- Researcher routes under `/app/**`
- Admin/validator workspace routes under `/admin/**`
- Internal pages use a near-fluid content layout with side navigation and wider desktop gutters.

Canonical researcher workspace routes:

- `/app/mi-panel`
- `/app/mis-actividades`
- `/app/actividades/nueva`

Canonical admin workspace routes:

- `/admin/panel`
- `/admin/validacion`
- `/admin/auditoria`
- `/admin/calidad-datos`
- `/admin/informes`
- `/admin/oportunidades-colaboracion`
- `/admin/normalizacion-temas`
- `/admin/ingesta`
- `/admin/datos-maestros`

What is still mixed:

- Entity pages such as `/publications`, `/researchers`, `/research-units`, `/venues`, `/events`, and `/participations` are still shared legacy routes.
- Some routes still share components between portal and internal contexts using route data (`portalView`) and role checks, but units and researchers now have dedicated portal pages.
- Publication detail still uses `/publications/:id` even when entered from public portal pages.

### Navigation Issues / Duplicated Entries

- Route duplication still exists for portal vs legacy routes:
  - `/portal/publicaciones` and `/publications`
  - `/portal/asistente` and `/copilot`
  - `/portal/unidades/:id` vs `/research-units/:id`
  - `/portal/investigadores/:id` vs `/researchers/:id`
- Public legacy redirects preserve older `/portal/busqueda` -> `/portal/publicaciones`, `/semantic-search` -> `/portal/publicaciones`, and `/portal/copiloto` -> `/portal/asistente` links.
- The app has no dedicated publication detail alias such as `/portal/publicaciones/:id`, so portal flows jump into a shared legacy detail route.
- Admin master-data work is split between dedicated admin routes and shared legacy entity routes.

## 3. Layout / Component Inventory

### Shell and Global Layout

Main layout primitives:

- `ShellComponent`
- global utility classes in `frontend/src/styles.css`
- sticky public top bar
- side navigation for private/admin areas
- reusable utility layouts such as `page`, `content-grid`, `card-grid`, `metric-grid`, `form-grid`, `section-header`, `surface-intro`, `table-wrap`, `metadata-grid`, `item-list`, `item-row`

### Shared Components

Shared components under `frontend/src/app/shared/components`:

- `PageHeaderComponent`
  - Large title + eyebrow + subtitle + action slot
- `SectionCardComponent`
  - Reusable card wrapper with eyebrow/title/subtitle
- `MetricCardComponent`
  - Metric/stat presentation
- `StatusChipComponent`
  - Validation/action/status chip with tones `neutral`, `success`, `warning`, `danger`, `info`
- `TagChipComponent`
  - Neutral/type/status micro-label chip
- `VisibilityNoteComponent`
  - Shows validated-only or role-aware visibility note
- `LoadingStateComponent`
  - Spinner + text panel
- `ErrorStateComponent`
  - Shared error banner panel
- `EmptyStateComponent`
  - Shared dashed empty-state panel
- `AuditHistoryPanelComponent`
  - Reusable audit timeline card for entity history

### Tables / Lists

Current patterns used repeatedly:

- card lists of entities (`researchers`, `units`, `reports`, `expert finder`)
- generic `item-row` lists (`events`, `venues`, `recent activities`)
- table wrappers for dense administrative views (`validation`, `audit`, `ingestion`)
- split list/detail layouts (`/app/mis-actividades`, `validation`)
- metadata grids for summary/detail sections across most detail pages

### Filters

Filter patterns in use:

- `FormGroup` + Angular Material form fields
- route query param synchronization on many list pages
- top filter cards on most list/admin analytics pages
- special semantic/AI controls:
  - segmented field/semantic search mode and unified publication results in `PublicationsPageComponent`
  - advanced retrieval options in `CopilotPageComponent`
  - mode toggles in `ExpertFinderPageComponent`

### Empty / Loading / Error States

Shared components exist, but usage is mixed:

- strong shared usage on dashboard, portal pages, my area, validation, data quality, reports
- some pages still use inline loading/error text or custom warning banners
- graph and Copilot still mix custom state panels with shared error handling

### Dialogs

Dialogs currently identified:

- `ValidationActionDialogComponent`
  - Used in validation inbox for validate/reject/request changes
- `TopicNormalizationMergeDialogComponent`
  - Used in topic normalization merge flow

### Graph Components

- `ResearcherGraphComponent`
  - Embedded in researcher detail
  - Uses Cytoscape
  - Supports density toggles and graph layer toggles
  - No standalone route

### Assistant / Citation Components

- `CopilotPageComponent`
  - Retrieval, answer generation, answer rendering, citation jumping, support evaluation
- `copilot-citation-parser.ts`
  - Parses Markdown-like answers into renderable blocks and citation segments
- Citation chips are also reused in `ReportsPageComponent`

### Report Components

- `ReportsPageComponent`
  - Config form
  - target picker
  - rendered report preview
  - citations panel
  - sidebar summary panels
- `ReportsService`
  - context loading, target building, report generation, strategic line targets

## 4. Visual Consistency Review

This section intentionally does not propose code changes. It only identifies likely issues based on the current code structure.

### Crowded Headers

- Many page headers include large titles plus multiple action buttons.
- Admin pages often add summary strips or secondary chip rows immediately below the header.
- The shell now has:
  - top bar
  - optional private workspace sticky bar
  - page header inside each route
- On medium/small screens, this stack is likely to feel tall and busy.

### Inconsistent Spacing

- Global utility classes exist, but many pages still define custom spacing locally.
- Similar patterns use different card radii, header spacing, list density, or panel padding.
- Split-view pages (`/app/mis-actividades`, `validation`, `reports`) are denser than portal cards and likely need a common spacing system.

### Duplicated Layout Patterns

- Publications, researchers, units, venues, events, and participations all implement similar filter-card + results-list layouts separately.
- Summary cards, toolbar meta rows, chip summaries, and intro panels are repeated with slight variations.
- Detail pages share the same “summary + metadata grid + audit/admin form” structure, but each page restyles it locally.

### Too Many Menu Options

- Admin side navigation is grouped, but dense internal destinations still need responsive review.
- Role switching plus workspace links plus public portal links may be manageable on desktop but can get crowded on smaller widths.
- Legacy routes still exist in parallel, so future contributors may add links in more than one place.

### Raw / Debug-like Panels

- No obvious raw JSON or `<pre>` debug output is present in the inspected pages.
- However, some detail panes still render generic key/value metadata from `detail.fields | keyvalue`:
  - `MyActivitiesPageComponent`
  - `ValidationInboxPageComponent`
- These are functional, but they may feel closer to operational/debug data than to curated UX.

### Inconsistent Cards / Tables

- Portal pages are mostly card-first and discovery-oriented.
- Admin pages mix:
  - metric cards
  - list cards
  - dense tables
  - generic metadata grids
- The result is workable, but not yet a single coherent design language.

### Weak Empty / Loading / Error State Consistency

Strengths:

- Shared empty/loading/error components exist and are used often.

Weak spots:

- some pages still use custom warnings instead of shared components
- graph uses custom text states
- audit history panel still uses a simple inline loading copy instead of the shared loading component
- ingestion shows report/error panels in a simpler style than the richer admin analytics pages

### Mobile / Responsive Risks

- Internal side navigation now stacks above content on smaller screens, so mobile spacing still needs review.
- Tables in `validation`, `audit`, and `ingestion` rely on horizontal scroll.
- Split layouts appear in:
  - `/app/mis-actividades`
  - `validation`
  - `reports`
  - `strategic map`
  - several detail pages
- Long Spanish labels and action bars may wrap unevenly in dense admin pages.

### Public Portal Looking Too Much Like Admin App

- The portal has improved branding and introduction surfaces, but it still shares many Material cards and shared list patterns with internal pages.
- Public and internal entity pages are often the same component with route-data switches.
- That reuse is efficient, but it makes it harder to give the portal a truly distinct discovery-oriented visual identity.

## 5. Public Portal Review

### Landing

Current page:

- `/portal` via `PortalHomePageComponent`

What exists:

- strong hero area
- highlighted entry points
- public metrics
- recent publications
- featured units/researchers
- topic pills

Visual improvement opportunities later:

- make hierarchy and featured content feel more editorial
- tighten visual rhythm between hero, metrics, and card sections
- ensure portal landing does not inherit admin-style density

### Units List / Detail

Current pages:

- `/portal/unidades`
- `/portal/unidades/:id`

What exists:

- directory filters
- hierarchy snapshot/tree
- unit metrics, linked researchers/publications/topics
- public visibility notes

Visual improvement opportunities later:

- tree panel and card grid may need stronger visual relationship
- detail page uses a fairly operational summary layout that could feel more public-facing
- long metadata sections could be more digestible

### Researchers List / Detail

Current pages:

- `/portal/investigadores`
- `/portal/investigadores/:id`

What exists:

- card-based directory
- visible topics and activity hints
- detail tabs and embedded graph

Visual improvement opportunities later:

- researcher cards likely need more consistent card hierarchy and stronger emphasis on expertise
- embedded graph inside detail can visually compete with the rest of the profile
- public profile still shares structure with admin-capable detail view

### Publications / Search

Current pages:

- `/portal/publicaciones`
- publication detail still resolves through shared `/publications/:id`

What exists:

- one main search bar
- segmented field/semantic mode selector
- advanced filters kept secondary
- one shared publication result card grid
- subtle public visibility note
- shared publication detail with related content

Visual improvement opportunities later:

- publication detail lacks a portal-prefixed route and therefore feels like a shared legacy screen
- result-card density, card rhythm, and topic metadata may still need a later polish pass
- semantic scoring is now integrated into the same cards, but the subtle score treatment may still need tuning after responsive review

### Expert Guide

Current page:

- `/portal/guia-expertos`

What exists:

- guided search
- mode toggle
- expert cards with confidence, themes, and evidence publications

Visual improvement opportunities later:

- confidence scoring and evidence hierarchy could be made more legible
- results likely need stronger scanning patterns for visitors
- current implementation is functional but still reads as an analytical tool more than a polished public guide

### Public Assistant

Current page:

- `/portal/asistente`

What exists:

- question input with guided prompts
- answer-first main column
- cited publications directly below the response
- collapsible context panel
- secondary support evaluation and retrieval summary

Visual improvement opportunities later:

- long-answer readability still needs responsive review
- context and support panels may still need a later accessibility pass for very dense evidence sets
- citation-heavy responses should be checked against real institutional content once broader datasets are loaded

## 6. Researcher Area Review

### Mi Panel

Current page:

- `/app/mi-panel`

What exists:

- profile summary
- validation counts
- publications by year
- topics
- recent activities
- data-quality reminders

Observations:

- now framed as a wider personal workspace instead of a portal/admin-like screen
- prioritizes activity status, attention items, actionable data-quality reminders, and recent work in one place
- quality reminders are now record-linked and action-oriented, but they are still built from the current dashboard plus the first page of owned activities because the dashboard API does not yet expose fully structured reminder items

### Mis Actividades

Current page:

- `/app/mis-actividades`

What exists:

- filters
- list/detail split
- status legend
- validation comments
- warnings/reminders
- audit history
- edit / submit / re-submit actions

Observations:

- remains one of the strongest workflow pages structurally
- detail hierarchy is now less operational: clearer status summary, curated fact cards, integrated change-request banner, and secondary audit history below
- deep links from the dashboard can now open a specific owned record directly inside the detail pane through query params

### Validation Status UI

Current state:

- shared status chip patterns are used consistently
- statuses observed across researcher/private flows:
  - `DRAFT`
  - `PENDING_VALIDATION`
  - `CHANGES_REQUESTED`
  - `VALIDATED`
  - `REJECTED`

Observations:

- status meaning is understandable
- legend treatment still varies by page
- status chips remain good primitives, while the researcher detail view now gives them clearer surrounding context

### Comments / Change Request UI

Current state:

- comments and change-request banners exist in `MyActivitiesPageComponent`
- validation detail panels explicitly surface comments to the researcher

Observations:

- the main yellow `Requiere cambios` banner now includes the validator comment directly instead of pushing the important text into a separate neutral note below
- the surrounding workflow reads as more human-centered, though the underlying backend field model is still fairly generic

### Activity Forms

Relevant forms present today:

- `/app/actividades/nueva`
- `/app/actividades/:id`
- `/participations/:id`
- researcher-owned edits via shared entity detail routes depending role/ownership

Observations:

- forms are real, not placeholder
- form experience is still very CRUD-oriented and likely inconsistent across entity types

## 7. Admin Area Review

### Panel institucional

Current page:

- `/admin/panel`

What exists:

- executive summary
- yearly production
- top topics
- emerging trends
- collaboration highlights
- validation overview
- data-quality signals

Observations:

- broad and useful
- card-heavy analytical page with many sections
- likely needs a clearer visual hierarchy and priority order in a future polish

### Validación

Current page:

- `/admin/validacion`

What exists:

- filters
- paginated inbox table
- review detail pane
- comments
- audit trail
- validation dialogs

Observations:

- one of the clearest operational workflows
- desktop-oriented split layout
- mobile/tablet experience likely depends heavily on stacking and table overflow

### Auditoría

Current page:

- `/admin/auditoria`

What exists:

- filters
- paginated event table
- action/status chips

Observations:

- functional but still table-centric
- likely more operational than polished
- no advanced faceting or grouped timeline view yet

### Datos maestros

Current page:

- `/admin/datos-maestros`

What exists:

- admin-only hub with shortcuts to researchers, units, publications, events, venues, and topic normalization

Observations:

- explicitly not a full workspace yet
- current page is a curated hub, not a true dedicated admin console

### Calidad de datos

Current page:

- `/admin/calidad-datos`

What exists:

- summary metrics
- filters
- issue list
- severity chips
- deep links to affected records

Observations:

- useful operational inventory
- more polished than a placeholder, but still an initial admin tool

### Sistema / Estado

Current state:

- no dedicated system/status page or route is present

## 8. AI / Search / Reporting Review

### Assistant

Current pages:

- `/portal/asistente`
- `/copilot`

What exists:

- question form
- retrieval-only step
- answer generation
- Markdown-like answer rendering
- citation jump links
- cited-publication cards directly under the answer
- collapsible retrieved-context panel
- secondary support evaluation / warning signals

Notes:

- public portal mode uses validated-only framing
- the public layout now keeps the answer central and pushes context/technical detail into secondary panels
- non-portal route can enable broader scope for privileged users

### Semantic Search

Current pages:

- `/portal/publicaciones`
- `/semantic-search`

What exists:

- one shared public publication search page
- field and semantic modes in the same search surface
- one unified result list with subtle similarity score in semantic mode
- example prompts
- optional inclusion of non-validated data for admin in non-portal mode

### Expert Finder

Current page:

- `/portal/guia-expertos`

What exists:

- guided query input
- search modes
- unit/active profile filters
- aggregated evidence from semantic search

Notes:

- implemented in code, even though older planning docs still describe it as future/planned

### Strategic Map

Current page:

- `/portal/mapa-estrategico`

What exists:

- validated-only filtering
- line metrics
- expandable line detail
- representative publications
- linked researchers/units/topics
- evidence list and trend/warning markers

Notes:

- more than a placeholder, but still conceptually MVP analytics

### Reports / Dossiers

Current page:

- `/admin/informes`

What exists:

- configurable report template selection
- admin create/edit form for template name, description, target type, sections, default year range, output format, and active state
- type/range/section selection with template defaults
- target search and selection
- optional additional instructions as controlled preferences, not raw prompt editing
- rendered report preview
- citation list
- Markdown export
- sidebar summaries

Notes:

- explicitly framed in UI as an internal/admin-first reporting tool
- PDF and DOCX export are not implemented and remain a later phase

### Citations / Evidence UI

Present in:

- `CopilotPageComponent`
- `ReportsPageComponent`
- `ExpertFinderPageComponent`
- `StrategicResearchMapPageComponent`

Patterns used:

- inline citation chips
- evidence cards
- linked publication detail routes
- support/evidence summaries

### Retrieved Context UI

Present in:

- Copilot retrieval flow
- semantic publication search results
- expert finder evidence publication lists
- strategic map evidence blocks
- report target selection / citations

### Answer Evaluation

Present:

- yes, in `CopilotPageComponent`
- support evaluation includes:
  - detected citations
  - warnings
  - possible unsupported claims

Not identified elsewhere:

- no equivalent answer-evaluation pattern on reports or expert finder

## 9. Suggested Visual Polish Phases

Recommended sequence for future work:

### Phase 1: Design System / Layout Base

- consolidate spacing, card density, section rhythm, and typography hierarchy
- normalize list/table/card patterns
- define consistent admin vs portal surface language
- stabilize responsive behavior for header + side navigation

### Phase 2: Public Portal Polish

- portal home
- units list/detail
- researchers list/detail
- publications/search
- make portal feel clearly public/discovery-oriented

### Phase 3: Researcher Area Polish

- `Mi panel`
- `Mis actividades`
- validation comments / change request states
- participation forms and owned activity edit flows

### Phase 4: Admin / Validator Polish

- `Panel institucional`
- `Validación`
- `Auditoría`
- `Datos maestros`
- `Calidad de datos`
- related CRUD-style maintenance pages used by admins

### Phase 5: AI / Search / Reports Polish

- Copilot
- semantic search
- expert finder
- strategic map
- reports / dossiers
- citations / evidence / retrieved context presentation

### Phase 6: Responsive / Accessibility Pass

- compact nav behavior
- sticky header/workspace interactions
- table overflow treatment
- keyboard/focus states for dense interactive analytical views
- readability and scanning on mobile/tablet

## 10. Risks

Areas where a visual refactor could accidentally break behavior:

- Shared portal/internal components
  - `PublicationsPageComponent`
  - `ResearchersPageComponent`
  - `ResearchUnitsPageComponent`
  - several detail pages
  - Risk: styling or layout changes can unintentionally expose admin-only actions or break portal-mode assumptions.

- Route-data-driven behavior
  - `portalView`
  - Risk: moving templates or splitting components without preserving route-data behavior can change visibility and navigation links.

- Query-param-driven filters
  - list/search pages sync filters with the URL
  - Risk: redesigning filter shells can drop routing state, back/forward behavior, or bookmarkability.

- Split list/detail workflows
  - `/app/mis-actividades`
  - `validation`
  - Risk: visual refactors can break selection state, async detail loading, or action sequencing.

- AI citation rendering
  - `CopilotPageComponent`
  - `ReportsPageComponent`
  - Risk: changing answer markup or citation chip behavior can break citation jumps and evidence traceability.

- Embedded graph
  - `ResearcherGraphComponent`
  - Risk: container/responsive changes can break Cytoscape sizing or node interaction.

- Admin maintenance pages using public/shared routes
  - `/researchers`, `/publications`, `/research-units`, `/venues`, `/events`, `/participations`
  - Risk: visual cleanup can accidentally optimize too strongly for public viewing and weaken admin affordances, or the reverse.

- Sticky shell layout
  - top bar + side navigation
  - Risk: height, spacing, or breakpoint changes can cause overlap, excessive vertical chrome, or hidden content anchors.

## Summary

The frontend is already broad and functionally substantial. It has:

- a meaningful public portal layer
- a real researcher workflow
- a real validator/admin workflow
- multiple AI/search/reporting experiences
- a growing set of shared UI primitives

The main challenge is not missing functionality. The main challenge is structural and visual coherence:

- route families are still mixed between new portal paths and older shared paths
- portal and internal experiences still reuse many of the same components
- admin navigation is growing faster than its information architecture
- shared states/components exist, but page-level layout patterns are still only partially unified

This makes the codebase a strong candidate for a planned polish phase, but also a risky one unless route behavior, role visibility, citation flows, and query-param workflows are protected carefully during refactor.
