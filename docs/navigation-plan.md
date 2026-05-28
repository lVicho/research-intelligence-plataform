# Navigation Plan

## Current Stabilized Structure

The frontend now separates the product into three route families and three distinct shell experiences:

- `Portal público`: discovery-oriented public pages under `/portal/**`, with a public header and no admin/researcher menu options.
- `Área investigador`: private researcher workspace under `/app/**`, with compact top bar and side navigation.
- `Administración`: admin/validator operations under `/admin/**`, with compact top bar and grouped side navigation.

The public header links only to `Inicio`, `Unidades`, `Investigadores`, `Publicaciones`, `Guía de expertos`, and `Asistente`. Internal areas do not duplicate public navigation in the top bar; they expose a `Cambiar área` menu with `Ver portal público`.

## Canonical Routes

Public portal:

- `/portal`
- `/portal/unidades`
- `/portal/unidades/:id`
- `/portal/investigadores`
- `/portal/investigadores/:id`
- `/portal/publicaciones`
- `/portal/guia-expertos`
- `/portal/asistente`

Researcher area:

- `/app/mi-panel`
- `/app/mis-actividades`
- `/app/actividades/nueva`
- `/app/actividades/:id` as an internal alias for editing event participation activity records from the researcher workflow

Admin/validator area:

- `/admin/panel`
- `/admin/validacion`
- `/admin/auditoria`
- `/admin/calidad-datos`
- `/admin/informes`
- `/admin/oportunidades-colaboracion`
- `/admin/normalizacion-temas`
- `/admin/ingesta`
- `/admin/datos-maestros`

## Legacy Redirects And Aliases

Legacy researcher links redirect to the canonical researcher area:

- `/my-dashboard` -> `/app/mi-panel`
- `/my-activities` -> `/app/mis-actividades`
- `/participations/new` -> `/app/actividades/nueva`

Legacy admin links redirect to the canonical admin area:

- `/dashboard` -> `/admin/panel`
- `/validation` -> `/admin/validacion`
- `/audit` -> `/admin/auditoria`
- `/data-quality` -> `/admin/calidad-datos`
- `/reports` -> `/admin/informes`
- `/collaboration-opportunities` -> `/admin/oportunidades-colaboracion`
- `/topic-normalization` -> `/admin/normalizacion-temas`
- `/ingestion` -> `/admin/ingesta`
- `/master-data` -> `/admin/datos-maestros`

Legacy public AI/search routes remain available where they preserve existing role-sensitive behavior, while public portal links use the canonical portal routes:

- `/portal/busqueda` -> `/portal/publicaciones`
- `/portal/copiloto` -> `/portal/asistente`
- `/semantic-search` remains as a legacy non-portal search alias.
- `/copilot` remains as a legacy non-portal assistant alias.

## Guard And Visibility Rules

- Public navigation must link only to pages backed by validated-only public behavior.
- Researcher navigation remains protected by `RESEARCHER` or explicitly supported admin access.
- Admin navigation remains protected by `ADMIN` and/or `VALIDATOR` according to the operation.
- Edit/create controls remain hidden unless the role can perform the action.
- Shared legacy entity routes still exist for maintenance/detail flows and should not be treated as polished public portal surfaces.

## Next Navigation Work

- Decide whether publication detail needs a `/portal/publicaciones/:id` alias.
- Decide whether master-data maintenance pages should move from shared legacy routes into dedicated `/admin/**` aliases.
- Continue visual polish separately for public portal pages and internal operational pages.
