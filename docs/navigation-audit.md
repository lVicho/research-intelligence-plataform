# Navigation Audit

Date: 2026-05-24

Scope: frontend navigation and contextual return behavior after the visual polish phase through E30.5I.

## Route Inventory

### Public Portal

Reachable from public navigation:

- `/portal`
- `/portal/unidades`
- `/portal/unidades/:id`
- `/portal/investigadores`
- `/portal/investigadores/:id`
- `/portal/publicaciones`
- `/portal/guia-expertos`

Public redirects kept:

- `/portal/busqueda` -> `/portal/publicaciones`
- `/portal/asistente` -> `/portal`
- `/portal/copiloto` -> `/portal`
- `/portal/mapa-estrategico` -> `/admin/mapa-estrategico`

The public assistant is contextual and embedded in relevant portal pages. It is not a public top-level navigation destination.

The public navigation intentionally does not expose internal tools such as Mapa estrategico, Informes, Validacion, Auditoria, Calidad de datos, Datos maestros, Normalizacion de temas, Ingesta, Oportunidades de colaboracion, or Panel institucional.

### Researcher Area

Reachable from the researcher side navigation:

- `/app/mi-panel`
- `/app/mis-actividades`
- `/app/actividades/nueva`

Other guarded researcher route:

- `/app/actividades/:id`

Researcher legacy redirects kept:

- `/my-dashboard` -> `/app/mi-panel`
- `/my-activities` -> `/app/mis-actividades`
- `/participations/new` -> `/app/actividades/nueva`

### Admin And Validator Area

Reachable from the admin/validator side navigation:

- `/admin/panel`
- `/admin/validacion`
- `/admin/publicaciones`
- `/admin/investigadores`
- `/admin/unidades`
- `/admin/participaciones`
- `/admin/eventos`
- `/admin/canales`
- `/admin/datos-maestros`
- `/admin/ingesta`
- `/admin/mapa-estrategico`
- `/admin/asistente`
- `/admin/oportunidades-colaboracion`
- `/admin/informes`
- `/admin/normalizacion-temas`
- `/admin/calidad-datos`
- `/admin/auditoria`

Internal aliases added for implemented catalogue/detail pages:

- `/admin/publicaciones`, `/admin/publicaciones/new`, `/admin/publicaciones/:id`
- `/admin/investigadores`, `/admin/investigadores/new`, `/admin/investigadores/:id`
- `/admin/unidades`, `/admin/unidades/new`, `/admin/unidades/:id`
- `/admin/eventos`, `/admin/eventos/new`, `/admin/eventos/:id`
- `/admin/participaciones`, `/admin/participaciones/:id`
- `/admin/canales`, `/admin/canales/new`, `/admin/canales/:id`
- `/admin/mapa-estrategico`
- `/admin/asistente`

Admin legacy redirects kept:

- `/dashboard` -> `/admin/panel`
- `/validation` -> `/admin/validacion`
- `/audit` -> `/admin/auditoria`
- `/data-quality` -> `/admin/calidad-datos`
- `/reports` -> `/admin/informes`
- `/collaboration-opportunities` -> `/admin/oportunidades-colaboracion`
- `/topic-normalization` -> `/admin/normalizacion-temas`
- `/ingestion` -> `/admin/ingesta`
- `/master-data` -> `/admin/datos-maestros`

### Shared Legacy Routes

Still implemented and kept for compatibility:

- `/research-units`, `/research-units/new`, `/research-units/:id`
- `/researchers`, `/researchers/new`, `/researchers/:id`
- `/publications`, `/publications/new`, `/publications/:id`
- `/events`, `/events/new`, `/events/:id`
- `/participations`, `/participations/:id`
- `/venues`, `/venues/new`, `/venues/:id`
- `/copilot`
- `/semantic-search` -> `/portal/publicaciones`

These are intentionally not listed in the public portal navigation. Admin users should now prefer the `/admin/**` catalogue aliases.

## Side Navigation

The admin, validator, and researcher areas now use the same side navigation shell and visual treatment:

- same base width
- same spacing
- same active link style
- same mobile collapse behavior
- different menu contents by area and role

The public portal keeps a simple top navigation only. The internal top bar remains compact and does not duplicate the full workspace navigation.

## Strategic Map

Strategic Map is implemented and is now internal-first:

- canonical route: `/admin/mapa-estrategico`
- side navigation group: `Inteligencia`
- old public path redirects to the guarded admin route
- public portal navigation does not show it

Strategic map evidence links now point to admin-safe catalogue/detail routes and carry contextual return metadata.

## Contextual Return Strategy

A shared `NavigationContextService` now standardizes return behavior:

- detail links pass `returnTo` and `returnLabel` as query params
- `returnTo` is sanitized to app-internal relative URLs only
- existing nested return params are removed from the generated origin URL
- detail pages fall back to a sensible local catalogue or portal list when no valid return context exists

Updated return contexts include:

- portal home, unit, researcher, publication, expert guide, and assistant evidence links
- public unit/researcher detail links to related publications and profiles
- admin catalogue list/detail links
- researcher My Activities edit links
- data quality affected-record links
- audit entity links where the entity type can be mapped safely
- report citation links
- topic normalization affected-publication links
- collaboration opportunity evidence links
- strategic map publication/researcher/evidence links
- graph detail links

Fallback labels include examples such as `Volver a publicaciones`, `Volver a investigadores`, `Volver a la unidad`, `Volver al investigador`, `Volver a mis actividades`, `Volver a calidad de datos`, `Volver a auditoria`, `Volver al informe`, `Volver al asistente`, `Volver a la guia de expertos`, and `Volver al mapa estrategico`.

## Intentionally Hidden Or Limited

- There is no dedicated system/status page implemented, so no `Estado del sistema` menu entry was added.
- Validation inbox review is still an inline split-view workflow rather than a separate routed detail page.
- Portal publication detail still uses the shared publication detail component under `/publications/:id`; contextual return now preserves the portal origin.
- Some backend-provided evidence paths still point to legacy shared routes. They now carry return context, but the backend path source was not changed in this frontend-only pass.
- No backend routes or contracts were modified.

## Verification

- `npm run build` passes.
- Build still reports pre-existing Angular budget warnings for initial bundle and several component CSS budgets.
- A local dev server was started, but it did not remain reachable on port 4200 despite Angular reporting watch mode; it was stopped to avoid leaving a stray process.
