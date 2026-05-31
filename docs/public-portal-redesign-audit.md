# Public Portal Redesign Audit

Date: 2026-05-31

Scope: preparation and P1 stabilization for the public portal redesign. The original audit was inspection-only; this file now also records the stabilization changes made before visual redesign.

## Confirmed Direction

- The public portal must feel like a real institutional public research portal, not like an internal admin application.
- Public, intranet/researcher, and admin areas must remain separate.
- The public header should be clean and should not show the full main navigation menu.
- Public sections should be discovered mainly from the home page.
- Public header target content: university/product logo area, portal name, language selector placeholder, and `Intranet` access.
- A public footer must exist and visually match the header.
- `/portal/asistente` should not remain a main public portal page or menu item.
- The assistant should become contextual and embedded in publication detail, researcher detail, unit detail, publication search results, and expert finder results.
- Contextual assistant scopes must use the full relevant context, not only the current UI page or paginator slice.
- Public publication detail must be a real `/portal/publicaciones/:id` page, not the legacy `/publications/:id` detail route.
- Mojibake/garbled Spanish text is a bug.

## Stabilization Update

P1 stabilization has been applied before the visual redesign:

- `GET /api/portal/publications/{id}` now exists as a portal-specific public publication detail contract.
- The endpoint resolves through validated/public visibility only and returns 404 when the publication is not public in the existing service convention.
- `/portal/publicaciones/:id` now has a minimal public-safe route/component that uses the portal endpoint.
- Portal publication links in home, unit detail, researcher detail, news detail, publication search, expert finder evidence, and public explanation references now target `/portal/publicaciones/:id` where safe.
- Legacy `/publications/:id` remains available for legacy/internal compatibility.
- `/portal/asistente` and `/portal/copiloto` now redirect to `/portal`; `/copilot` and `/admin/asistente` remain available.
- The public header no longer promotes `Asistente`, but full public shell/footer redesign remains P2 work.
- Mojibake and missing-accent Spanish labels were corrected in the public portal, public publications mode, expert finder, copilot public copy, and shared labels visible publicly.

## Current Public Route State

| Public path | Current component | Current state |
| --- | --- | --- |
| `/portal` | `PortalHomePageComponent` | Dedicated public landing page. Includes hero, metrics, featured units, recent publications, news, topics, expert guide teaser, and publication discovery links. Standalone assistant promotion was removed. |
| `/portal/unidades` | `PortalResearchUnitsPageComponent` | Dedicated public unit directory. Uses portal-specific API and portal-specific cards. |
| `/portal/unidades/:id` | `PortalResearchUnitDetailPageComponent` | Dedicated public unit detail. Publication links now target `/portal/publicaciones/:id`. |
| `/portal/investigadores` | `PortalResearchersPageComponent` | Dedicated public researcher directory. Uses portal-specific API and portal-specific cards. |
| `/portal/investigadores/:id` | `PortalResearcherDetailPageComponent` | Dedicated public researcher profile. Publication links now target `/portal/publicaciones/:id`; embeds the researcher graph when small enough. |
| `/portal/publicaciones` | `PublicationsPageComponent` with `data: { portalView: true }` | Shared publication search/list page. Public mode hides some internal controls, still uses shared component and `/api/publications`, and now links results to `/portal/publicaciones/:id`. |
| `/portal/publicaciones/:id` | `PortalPublicationDetailPageComponent` | Minimal public-safe publication detail route implemented for the P4 contract. Uses `/api/portal/publications/{id}` and avoids shared CRUD/workflow UI. |
| `/portal/guia-expertos` | `ExpertFinderPageComponent` with `data: { portalView: true }` | Public expert finder. Builds results in the frontend from semantic publication results and researcher detail calls. Evidence links now target `/portal/publicaciones/:id`. |
| `/portal/asistente` | Redirect to `/portal` | Compatibility redirect. It is no longer a promoted standalone public assistant route. |
| `/publications/:id` | `PublicationDetailPageComponent` | Legacy/shared publication detail route preserved for legacy/internal compatibility and non-portal flows. |

Additional portal routes exist beyond the requested list:

- `/portal/noticias`
- `/portal/noticias/:id`
- `/portal/busqueda` redirects to `/portal/publicaciones`.
- `/portal/copiloto` redirects to `/portal`.
- `/portal/mapa-estrategico` redirects to `/admin/mapa-estrategico`.

## Public Shell

Current shell: `frontend/src/app/core/layout/shell.component.ts`.

- Header component: `ShellComponent`, inline template/styles.
- Public header currently shows a brand mark `RI`, product name/tagline, a full top navigation menu, and a session action.
- Public top menu entries currently are `Inicio`, `Unidades`, `Investigadores`, `Publicaciones`, and `Guía de expertos`.
- This still conflicts with the confirmed decision that the public header must not show the main navigation menu, but `Asistente` is no longer a main menu item.
- Anonymous users see `Iniciar sesión`; logged-in users see `Entrar al área interna`.
- There is no language selector placeholder in the public header.
- There is no footer in the public shell.
- Portal main width is `min(1480px, calc(100% - 48px))`; public topbar inner width is `min(1480px, 100%)`.
- Internal shell uses a separate wider layout and side navigation; this separation should be preserved.
- Shared UI primitives in use include `PageHeaderComponent`, `SectionCardComponent`, `TagChipComponent`, `StatusChipComponent`, `VisibilityNoteComponent`, `LoadingStateComponent`, `ErrorStateComponent`, and `EmptyStateComponent`.
- Shared styles still lean heavily on Angular Material cards/buttons/form fields and page-level cards, so public pages can still feel close to the internal app.
- Previously visible encoding bugs in public-facing portal, publications, expert finder, and copilot strings were corrected for `público`, `Guía`, `búsqueda`, `publicación`, `Página`, `Título`, `sesión`, `área`, `investigación`, `semántica`, `validación`, and sort-arrow labels.

## Search And List Pages

### Units List

- Route/component: `/portal/unidades`, `PortalResearchUnitsPageComponent`.
- Search implementation: local `FormControl` for text search plus type pills.
- Filters: search card at top; type filters are collapsed behind `Filtrar por tipo`.
- Result card: custom `.unit-card` in the page component.
- Pagination: previous/next controls; `page` query param; page size 12.
- Query params: `page`, `text`, `type`.
- API calls:
  - `/api/portal/research-units?page&size&text&type`
  - `/api/portal/research-units/{id}` per visible card for metrics/topics
  - all unit pages loaded with size 100 for hierarchy/tree context
- Issues/gaps:
  - N+1 detail calls per result page.
  - Tree/all-unit loading is independent from the visible result page.
  - Public-facing mojibake strings were corrected in the stabilization pass.

### Researchers List

- Route/component: `/portal/investigadores`, `PortalResearchersPageComponent`.
- Search implementation: text `FormControl`, unit select, topic input.
- Filters: main search row; advanced filters collapsed behind `Mas filtros`.
- Result card: custom `.researcher-card`.
- Pagination: previous/next controls; `page` query param; page size 12.
- Query params: `page`, `text`, `topic`, `researchUnitId`.
- API calls:
  - `/api/portal/researchers?page&size&text&researchUnitId&topic`
  - `/api/portal/researchers/{id}` per visible card for topics/counts
  - `/api/portal/research-units` all pages for unit filter options
- Issues/gaps:
  - N+1 detail calls per result page.
  - Cards show publication/activity counts only after detail calls complete.
  - Public-facing mojibake strings were corrected in the stabilization pass.

### Publications List

- Route/component: `/portal/publicaciones`, shared `PublicationsPageComponent` in `portalView`.
- Search implementation: one form with mode selector `fields` / `semantic`.
- Filters:
  - Main search field is inline.
  - Field-mode advanced filters are collapsed.
  - Public mode hides the status select, but the shared form still carries status state.
  - Semantic mode shows demo query chips and uses fixed `limit: 20`, `minSimilarity: 0.35`.
- Result card: custom `.publication-card` inside the shared publications component.
- Pagination:
  - Field search uses previous/next and `page`.
  - Semantic search has no pagination and is capped at 20 results in the frontend.
- Query params: `mode`, `q`, `yearFrom`, `yearTo`, `type`, `status`, `researchUnitId`, `researcherId`, `topic`, `includeNonValidated`, `sortBy`, `sortDirection`, `page`.
- API calls:
  - `/api/publications`
  - `/api/publications/filter-metadata`
  - `/api/publications/semantic-search`
  - `/api/researchers` for researcher filter options
  - `/api/portal/demo-queries`
- Issues/gaps:
  - The frontend does not use existing `/api/portal/publications` for public publication search.
  - Result links now go to `/portal/publicaciones/:id`.
  - Semantic result context is only the returned limit, not all possible matching results.
  - Public-facing mojibake strings were corrected in the stabilization pass.

### Expert Finder

- Route/component: `/portal/guia-expertos`, `ExpertFinderPageComponent`.
- Search implementation: form with `query`, `mode`, `researchUnitId`, and active/all filter.
- Filters: inline top form; no paginator.
- Result card: custom `.expert-card` with confidence, topics, evidence publications, explanation, and profile link.
- Query params: `q`, `mode`, `researchUnitId`, `active`.
- API calls currently used by frontend:
  - `/api/publications/semantic-search`
  - `/api/publications/{id}` for each semantic result
  - `/api/researchers/{id}` for authors in retrieved publications
  - `/api/research-units`
- Backend endpoint exists but is unused by the current frontend:
  - `/api/expert-finder/search`
- Issues/gaps:
  - Expert ranking is currently assembled client-side from a limited semantic result set.
  - Backend expert finder has broader deterministic evidence ranking, event evidence, visibility metadata, and warnings, but the UI bypasses it.
  - Evidence links now go to `/portal/publicaciones/:id`.
  - Public-facing mojibake strings were corrected in the stabilization pass.

## Detail Pages

### Unit Detail

- Route/component: `/portal/unidades/:id`, `PortalResearchUnitDetailPageComponent`.
- Public-specific: yes.
- API calls:
  - `/api/portal/research-units/{id}`
  - `/api/portal/research-units` all pages for institutional trail.
- Sections/cards:
  - Public page header with return button.
  - Hero card with type, institutional trail, location, website, metrics.
  - Topics strip.
  - Researchers linked to `/portal/investigadores/:id`.
  - Institutional frame and child units.
  - Validated publications.
  - Related activities.
- Tabs: none.
- Graph/network: none.
- Related activities/publications: yes.
- Contextual return: `NavigationContextService` with `returnTo` and `returnLabel`.
- Issues/gaps:
  - Backend detail caps researchers at 100 and publications/activities at 25, so it is not a full context source for assistant scope.
  - No embedded contextual assistant yet.

### Researcher Detail

- Route/component: `/portal/investigadores/:id`, `PortalResearcherDetailPageComponent`.
- Public-specific: yes.
- API calls:
  - `/api/portal/researchers/{id}`
  - Embedded graph may call `/api/graph/researcher/{id}` through `ResearcherGraphComponent`.
- Sections/cards:
  - Public page header with return button.
  - Hero card with ORCID, primary affiliation, metrics, topics.
  - Affiliations and trajectory.
  - Visible collaboration/coauthors.
  - Publications and works.
  - Participation/activity.
  - Graph card or graph summary depending on graph density.
- Tabs: none.
- Graph/network: yes, embedded only when graph summary is small enough.
- Related activities/publications: yes.
- Contextual return: `NavigationContextService`.
- Issues/gaps:
  - No embedded contextual assistant yet.
  - Need confirm backend detail returns all validated authored publications at expected dataset sizes before using it as assistant context.

### Publication Detail

- Public route/component today: `/portal/publicaciones/:id`, `PortalPublicationDetailPageComponent`.
- Public-specific: yes. It is a minimal P4 contract component, not the final visual redesign.
- Legacy route/component remains: `/publications/:id`, shared `PublicationDetailPageComponent`.
- API calls:
  - `/api/portal/publications/{id}`
  - `/api/portal/publications/{id}/explain` for public explanation dialog behavior
- Sections/cards:
  - Minimal summary/metadata card.
  - Abstract or public summary.
  - Authors, public internal researcher links, public unit links, external organizations, topics, warnings, and related-publication preview.
- Tabs: none.
- Graph/network: none.
- Related publications: yes, through a safe preview returned by `/api/portal/publications/{id}`.
- Contextual return: `NavigationContextService`; portal context is inferred partly from `returnTo` starting with `/portal`.
- Issues/gaps:
  - This is not the final redesigned public detail page.
  - Related-publication preview is intentionally small and not a full contextual assistant scope.
  - No embedded contextual assistant yet.

## Assistant Current State

- `/portal/asistente` now redirects to `/portal`.
- `/portal/copiloto` now redirects to `/portal`.
- `CopilotPageComponent` is still used by `/copilot` and `/admin/asistente`.
- Current legacy/global behavior:
  - User enters a general question.
  - Optional controls include `limit`, retrieval mode, and min similarity.
  - Public mode always uses validated-only context; admin-only non-validated toggle is hidden in portal mode.
  - The component first retrieves publications, then sends those retrieved publications back to the answer endpoint.
  - Answer layout is answer-first, with cited publications below and retrieved context in an optional panel.
- Current backend endpoints:
  - `POST /api/copilot/ask`
  - `POST /api/copilot/retrieve`
  - `POST /api/copilot/answer`
  - `POST /api/copilot/evaluate-answer`
- Current context selection:
  - Context is retrieved from the question text across public validated publications by default.
  - Retrieval uses semantic search if embeddings exist, otherwise text search.
  - Scope is visibility-only: public validated or admin all. It is not scoped to an entity, route, search result set, or expert-finder result set.
- Current request shape:
  - `CopilotRetrieveRequest`: `question`, `limit`, `minSimilarity`, `retrievalMode`, `includeNonValidated`.
  - `CopilotAnswerRequest`: `question`, `retrievedPublications`, `includeNonValidated`.
- Contextual assistant gaps:
  - No target entity scope such as publication, researcher, unit, search, or expert finder result.
  - No server-side scope token or filter snapshot.
  - No way to say "all publications for this researcher/unit/search" independently from the UI paginator.
  - Public UI caps retrieval at 20; backend validation allows larger but still limit-based retrieval.
  - Answer endpoint accepts client-provided retrieved publications and filters visibility, but does not reconstruct a trusted full scope.

## Backend/API State And Gaps

### Public Publication Detail

Current:

- `GET /api/publications/{id}` is public and visibility-filtered through `PublicationService.findById`.
- `PublicationService.findPublicValidatedById` exists internally.
- `GET /api/portal/publications` exists for public validated publication lists.
- `GET /api/portal/publications/{id}` exists and returns a portal-specific DTO for validated/public publications only.

Contract:

- Public visibility is resolved server-side with `PublicationService.findPublicValidatedById`.
- Non-public, draft, pending, rejected, or otherwise non-visible publications use the existing not-found convention.
- The DTO includes id, title, abstract, public summary, year/date, type/status, source/venue, publisher, DOI/URL, ISBN/ISSN/language, authors, public internal researchers, public units, external organizations, topics, related-publication preview, warnings, explanation availability, visibility scope, and validation filter metadata.

Remaining gap:

- The current frontend detail page is a minimal contract implementation; P4 should still produce the final public editorial layout and contextual assistant placement.

### Public Researcher Full Context

Current:

- `GET /api/portal/researchers/{id}` returns affiliations, authored publications, activities, topics, coauthors, and graph summary in public validated scope.

Gap:

- The endpoint is a display DTO, not an assistant context contract.
- Need a context endpoint or assistant scope resolver that can gather all relevant validated researcher context deterministically and with explicit limits/warnings.

### Public Unit Full Context

Current:

- `GET /api/portal/research-units/{id}` returns a display DTO.
- Backend caps related researchers at 100 and publications/activities at 25.

Gap:

- This cannot satisfy full-context assistant behavior for units with more than the display caps.
- Need server-side unit scope resolution for all relevant validated publications/researchers/activities, with controlled maximums and warnings.

### Publication Search Full Result Context

Current:

- Field search: `/api/publications` and `/api/portal/publications` are paginated.
- Semantic search: `/api/publications/semantic-search` has max 50 and the current UI uses 20.

Gap:

- No endpoint turns a publication search/filter snapshot into an assistant context over the full result set.
- Contextual assistant must not use only the current paginator page.

### Expert Finder Full Result Context

Current:

- Backend `POST /api/expert-finder/search` exists and supports deterministic evidence ranking, visibility metadata, warnings, semantic publication scores, topic matches, publication evidence, and event participation evidence.
- Frontend `ExpertFinderPageComponent` does not call this endpoint.

Gap:

- Use or adapt the backend expert-finder endpoint for the redesigned expert finder and its contextual assistant.
- Add a context/scope representation for selected expert results and evidence sets.

### Assistant Contextual Ask

Current:

- `/api/copilot/*` is global question-based retrieval.

Gap:

- Need an assistant request model that carries a trusted server-resolved context scope, for example:
  - `PUBLICATION_DETAIL` with publication id.
  - `RESEARCHER_DETAIL` with researcher id.
  - `UNIT_DETAIL` with unit id.
  - `PUBLICATION_SEARCH` with validated search/filter snapshot.
  - `EXPERT_FINDER_RESULTS` with expert-finder request/result scope.
- The server should resolve context from ids/filters, not rely on the frontend sending the current page of results.

## Components And Routes Likely Affected

Routes:

- `/portal`
- `/portal/unidades`
- `/portal/unidades/:id`
- `/portal/investigadores`
- `/portal/investigadores/:id`
- `/portal/publicaciones`
- `/portal/publicaciones/:id` (new)
- `/portal/guia-expertos`
- `/portal/asistente` (demote/remove from main discovery; likely keep redirect or compatibility route)
- `/portal/copiloto`
- `/copilot`
- `/publications/:id`
- `/admin/asistente`
- `/admin/publicaciones/:id`

Frontend files/components:

- `frontend/src/app/app.routes.ts`
- `frontend/src/app/core/layout/shell.component.ts`
- `frontend/src/app/features/portal/portal-home-page.component.ts`
- `frontend/src/app/features/portal/portal-publication-detail-page.component.ts`
- `frontend/src/app/features/portal/portal-research-units-page.component.ts`
- `frontend/src/app/features/portal/portal-research-unit-detail-page.component.ts`
- `frontend/src/app/features/portal/portal-researchers-page.component.ts`
- `frontend/src/app/features/portal/portal-researcher-detail-page.component.ts`
- `frontend/src/app/features/publications/publications-page.component.ts`
- `frontend/src/app/features/publications/publication-detail-page.component.ts`
- `frontend/src/app/features/recommendations/expert-finder-page.component.ts`
- `frontend/src/app/features/copilot/copilot-page.component.ts`
- `frontend/src/app/core/api/portal-api.service.ts`
- `frontend/src/app/core/api/publications-api.service.ts`
- `frontend/src/app/core/api/copilot-api.service.ts`
- `frontend/src/app/core/navigation/navigation-context.service.ts`
- Shared components under `frontend/src/app/shared/components/**`

Backend files/modules:

- `backend/src/main/java/com/researchintelligence/platform/portal/api/PortalController.java`
- `backend/src/main/java/com/researchintelligence/platform/portal/api/PortalPublicationDetailResponse.java`
- `backend/src/main/java/com/researchintelligence/platform/portal/api/PortalPublicationAuthorResponse.java`
- `backend/src/main/java/com/researchintelligence/platform/portal/api/PortalPublicationLinkedResearcherResponse.java`
- `backend/src/main/java/com/researchintelligence/platform/portal/api/PortalPublicationLinkedUnitResponse.java`
- `backend/src/main/java/com/researchintelligence/platform/portal/api/PortalPublicationTopicResponse.java`
- `backend/src/main/java/com/researchintelligence/platform/portal/application/PortalService.java`
- `backend/src/main/java/com/researchintelligence/platform/publications/api/PublicationController.java`
- `backend/src/main/java/com/researchintelligence/platform/publications/application/PublicationService.java`
- `backend/src/main/java/com/researchintelligence/platform/ai/api/CopilotController.java`
- `backend/src/main/java/com/researchintelligence/platform/ai/application/CopilotService.java`
- `backend/src/main/java/com/researchintelligence/platform/ai/application/PublicationRetrievalService.java`
- `backend/src/main/java/com/researchintelligence/platform/expertfinder/api/ExpertFinderController.java`
- `backend/src/main/java/com/researchintelligence/platform/expertfinder/application/ExpertFinderService.java`
- Security config only if new endpoints do not fit existing public/role rules.

## Risk Areas

- Public/internal route mixing: publication detail is the largest current leak between public and legacy/internal UX.
- Shared component behavior: changes to `PublicationsPageComponent`, `PublicationDetailPageComponent`, or `CopilotPageComponent` can affect public, legacy, and admin routes at once.
- Visibility rules: public redesign must preserve validated-only behavior and avoid exposing drafts/pending/rejected data.
- Contextual assistant scope: biggest product risk is accidentally using only displayed page results instead of full relevant context.
- Query param behavior: list pages rely on bookmarkable query params and return context.
- Encoding: mojibake is widespread and should be fixed deliberately, preferably before visual QA.
- Backend/frontend duplication: expert finder has a backend endpoint but frontend currently implements its own aggregation.
- Performance: unit/researcher portal lists perform per-card detail calls; contextual assistant full scopes can amplify this if not resolved server-side.
- Accessibility/responsiveness: current pages use large cards plus forms; redesign should reduce public chrome and avoid public pages becoming form-heavy.

## Proposed Implementation Sequence

### P1 - Stabilize Text, Route Inventory, And Public Contracts

- Status: completed for the initial stabilization pass.
- Mojibake was fixed in public-facing frontend strings and shared public labels touched by the portal.
- `/portal/asistente` and `/portal/copiloto` now redirect to `/portal`.
- Public publication detail contract and route were added before visual work.
- Tests were added for the portal publication detail endpoint and service visibility behavior.

### P2 - Public Shell Separation

- Update `ShellComponent` so public header removes the main nav.
- Add logo/product area, portal name, language selector placeholder, and `Intranet` access.
- Add a matching public footer.
- Keep internal `/app` and `/admin` side-navigation behavior separate.

### P3 - Home Page As Portal Directory

- Make `/portal` the primary discovery surface for sections.
- Remove main assistant-page emphasis from home; replace with contextual assistant entry points where relevant.
- Keep links to units, researchers, publications, expert guide, and news clear and editorial.

### P4 - Public Publication Detail

- Expand the minimal `/portal/publicaciones/:id` implementation into the final public detail page.
- Keep public publication links on `/portal/publicaciones/:id`.
- Preserve the separation from shared CRUD/workflow-heavy structure.
- Preserve `/publications/:id` for legacy/internal compatibility.

### P5 - Simplify Public Search/List Pages

- Keep public filters secondary and less form-heavy.
- Consider moving public publication search to `/api/portal/publications` or make the visibility behavior explicit in the frontend service.
- Replace frontend expert-finder aggregation with backend `/api/expert-finder/search` or align both paths.
- Preserve query-param bookmarkability and return context.

### P6 - Contextual Assistant Scopes

- Add server-resolved assistant scope support for publication detail, researcher detail, unit detail, publication search, and expert finder results.
- Ensure scopes resolve all relevant validated context, not only current UI page results.
- Add warning/limit metadata when a full scope is intentionally bounded.
- Embed contextual assistant panels/cards into relevant public pages.

### P7 - Public QA And Regression Pass

- Verify routes, headers, footer, return behavior, and public/internal separation.
- Verify public assistant scopes on cases with more records than visible paginator size.
- Verify semantic search, expert finder, citations, related publication links, and graph sizing.
- Run frontend build and backend tests for any code/API changes introduced in implementation phases.

### P8 - Accessibility And Performance Hardening

- Audit keyboard flow, focus states, landmarks, heading hierarchy, and contrast after the visual redesign.
- Reduce or batch N+1 public list detail calls for units and researchers.
- Add explicit loading, empty, error, and truncated-scope messaging for large public datasets.

### P9 - Launch Readiness And Regression Suite

- Add route-level regression coverage for the public/internal/admin separation.
- Add fixtures for validated and non-validated public portal visibility checks.
- Finalize a smoke-test checklist covering public home, lists, detail pages, assistant entry points, expert finder, news, and legacy compatibility routes.

## Summary

The current portal already has dedicated public unit and researcher pages, a broad public home page, a unified publication search, an expert finder, a minimal public publication detail route, and compatibility redirects away from the standalone public assistant route. The main blockers before redesign are now narrower: the public shell still behaves like a menu-driven app, publication detail still needs its final public editorial layout, expert finder still bypasses the backend endpoint, and backend assistant context remains query/limit-based rather than route/entity/search-scope based.
