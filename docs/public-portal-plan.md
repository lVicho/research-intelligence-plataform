# Public Portal Plan

## Purpose

The public portal is a visual, discovery-oriented experience for visitors who are not working in the researcher or administration areas. It exposes validated institutional knowledge and makes it easy to explore research units, researchers, publications, topics, semantic relationships, expert guidance, and assisted answers.

The portal is not the same as the internal application without a login. Internal workflows such as drafts, validation, audit events, ingestion, master-data maintenance, and data-quality operations belong in `/app` or `/admin`.

## Current Routes

- `/portal`: landing page with institutional positioning, featured validated activity, and entry points to units, researchers, publications, expert guide, and news.
- `/portal/unidades`: public institutional unit directory for portal-visible internal units only.
- `/portal/unidades/:id`: public research unit detail.
- `/portal/investigadores`: public researcher directory.
- `/portal/investigadores/:id`: public researcher detail.
- `/portal/publicaciones`: validated publications and activities with one shared search surface, a mode selector (`Por campos` / `Semántica`), and one unified result list.
- `/portal/guia-expertos`: public expert guide / expert finder.
- Contextual assistant: embedded in publication detail, researcher detail, unit detail, publication search results, and expert finder results. It is not promoted as a standalone public destination.

Legacy public portal redirects:

- `/portal/busqueda` redirects to `/portal/publicaciones`.
- `/portal/asistente` redirects to `/portal`.
- `/portal/copiloto` redirects to `/portal`.

The public navigation shows only portal discovery destinations such as `Inicio`, `Unidades`, `Investigadores`, `Publicaciones`, `Guía de expertos`, and public news where enabled. It does not promote a standalone assistant route.

## Data Visibility Rules

- Public portal pages must return only `VALIDATED` records by default.
- Public publication, semantic search, related publications, graph, and assistant responses must not include drafts, pending records, rejected records, or records requiring changes.
- Public researcher pages should only show validated researchers, validated affiliations, validated research units, and validated authored publications.
- Public researcher directories and detail pages should only expose researchers whose current primary affiliation is a validated portal-visible institutional unit.
- Public research unit pages should only show validated units and validated relationships.
- `/portal/unidades` must exclude external organizations as first-class directory items. External universities, hospitals, companies, or foundations may still appear as secondary references inside collaborations, affiliations, coauthors, or publication metadata.
- Assistant and expert-guide behavior must clearly state when it is working in validated-only mode.
- Public contextual assistant entry points stay embedded in the relevant pages, but the conversation opens in a modal so the structured answer, cited evidence, and optional context/support details have enough room. Evidence used to build the answer stays collapsed by default; evidence explicitly cited in the answer is shown separately.
- Contextual assistant answers use controlled Markdown only, not model-provided HTML. Publication markers such as `[pub:ID]` and grouped marker variants are normalized to numbered cited-evidence references in the UI.
- Contextual assistant publication context includes linked public researchers, internal units inferred from visible researcher affiliations, external affiliations declared by authors, topics, DOI/URL/source, and retrieval reasons so unit, researcher, publication, search, and expert-guide questions can answer collaboration-oriented prompts.
- `POST /api/portal/context-assistant/ask` resolves context server-side from trusted scopes (`PUBLICATION_DETAIL`, `RESEARCHER_PROFILE`, `UNIT_PROFILE`, `PUBLICATION_SEARCH_RESULTS`, `EXPERT_FINDER_RESULTS`) and must not trust frontend-provided evidence lists.
- Non-validated data may be visible only in authenticated private/admin contexts with explicit role checks.

## Unit Detail Content

Public unit detail pages should show:

- Name, short name, unit type, city/country, website, and active status.
- Parent/child hierarchy using validated units only.
- Current validated affiliated researchers.
- Validated publications or activities connected to the unit.
- Main validated topics represented in the unit.
- External collaborating organizations only as secondary references when available, not as peer institutional units in the main directory.
- Clear empty states when no validated content is available.

Private/admin-only fields such as audit users, validation comments, lifecycle history, and edit controls should remain hidden outside the admin area.

## Researcher Detail Content

Public researcher detail pages should show:

- Display name, full name where appropriate, ORCID, and active status.
- Current validated affiliations and primary affiliation.
- Validated authored publications or activities.
- Validated topics and coauthor relationships derived from validated publications.
- Contextual assistant entry points with validated-only context for the current researcher profile.

Private researcher data, validation comments, drafts, pending changes, and audit history must stay in `/app` or `/admin` experiences.

## Search Behavior

- Field-based search should default to validated publications and validated people/units.
- `/portal/publicaciones` should present one main search bar and one result component, with the user choosing between `Por campos` and `Semántica` instead of navigating to separate search experiences.
- Semantic search is integrated into `/portal/publicaciones` and should retrieve only validated publications for public users.
- The public note can be subtle and appear once in the intro or footer, for example: `El portal muestra actividad pública revisada por la institución.`
- Authenticated admin workflows may provide explicit controls for broader visibility in non-portal routes, but public pages must not infer or expose non-validated data.

## Difference From Private Areas

- `Portal público`: visual discovery, validated-only data, public unit/researcher/publication exploration, expert guide, news, and contextual assistant entry points with validated context.
- `Área investigador`: logged-in researcher workspace under `/app` for `Mi panel`, `Mis actividades`, owned edits, validation submission, and own lifecycle history.
- `Administración`: admin/validator workspace under `/admin` for institutional dashboard, validation inbox, audit, ingestion, reports, master data, data quality, and operational checks.
