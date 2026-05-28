# CRUD And Form Coverage Audit

Date: 2026-05-24

Scope: full-stack CRUD, validation, audit, form, and navigation coverage for the main catalog/workflow entities. This is an audit and planning document only; it does not mark missing features as implemented.

Legend:

- `Yes`: implemented and reachable in the current codebase.
- `Partial`: implemented only for some workflow, role, route, or field subset.
- `No`: not implemented as a first-class capability.
- `N/A`: not applicable for the current entity.

## Entity Audit

| Entity | Backend coverage | Frontend coverage | Main gaps |
| --- | --- | --- | --- |
| Publications | Table/entity/repository/service/controller exist. List, detail, create, update, related, semantic search, validation inbox, and audit events exist. No delete/archive endpoint. Role rules: public GET, ADMIN create, ADMIN/RESEARCHER update with ownership check. | Public/admin list exists; shared detail exists; ADMIN create/edit form exists; audit history shown to admins; related publications shown. | Detail/list DTOs expose academic `status` but not `validationStatus`/comments. Researcher-owned edit links exist from My Activities, but detail form is ADMIN-only, so researchers cannot actually edit own publication records. Create/edit form does not expose venue, publisher, ISBN/ISSN, language, multiple author editing, publication date, public summary, visibility flag, external organizations, source/origin detail, or related-publication curation. No submit-to-validation action on publication detail. |
| Researchers | Table/entity/repository/service/controller exist. List, detail, create, update, affiliation CRUD, validation inbox, and audit events exist. No delete endpoint; active=false records archived in audit. Role rules: public GET, ADMIN create, ADMIN/RESEARCHER update with ownership check. | Public portal list/detail exists. Admin list/detail/create/edit exists. Affiliation add/edit/delete exists on admin detail. Audit history shown to admins. | Researcher-owned edit links exist from My Activities, but researcher detail form is ADMIN-only. Detail/list DTOs do not expose validation status/comments directly. No explicit submit-to-validation button on researcher detail. Missing public profile summary, visibility-in-portal flag, public/private email separation, role/responsibility field, and direct expertise editor beyond derived topics. |
| Internal research units | Table/entity/repository/service/controller exist. List, tree, detail, create, update, portal-visible public endpoints, validation inbox, and audit events exist. No delete endpoint; active=false records archived in audit. Role rules: public GET, ADMIN create/update. | Public portal unit directory/detail exists. Admin list/detail/create/edit exists. Audit history shown to admins. Admin menu links exist. | Same `research_units` model also represents external entities, so internal vs external intent depends on `type` and `visibleInPortal`. Detail/list DTOs do not expose validation status/comments directly. Missing public/internal descriptions, responsible person/researcher, topic editor, featured/order flag, and explicit internal-only classification. No submit-to-validation action on unit detail. |
| External organizations/entities | No separate table/entity. External organizations are modeled as `ResearchUnit` values with types such as `HOSPITAL`, `COMPANY`, `FOUNDATION`, `GOVERNMENT_AGENCY`, or `OTHER`; `visibleInPortal=false` keeps them out of the public internal unit directory. | No dedicated external-organization maintenance area. Admins can create/edit them through the unit form by choosing an external type and disabling portal visibility. | Needs a separate admin-facing conceptual page or clearer unit form mode to avoid mixing external collaborators with institutional units. Missing organization description, relation type, external directory safeguards, and links to publications/events beyond existing affiliation/publication/unit references. No dedicated menu link. |
| Researcher affiliations | Table/entity/repository exist under researchers. Endpoints exist for list/create/update/delete by researcher. Validation status, validation inbox, and audit events exist. | Admin researcher detail shows current/past affiliations and add/edit/delete form. My Activities lists affiliation workflow status/comments/audit for researchers. | No standalone list/detail route. Researcher-owned affiliation edit links from My Activities route to researcher detail, but the affiliation form is ADMIN-only. No explicit submit-to-validation from the affiliation form. Required fields are mostly clear, but role is optional and dates need stronger guidance. |
| Scientific events | Table/entity/repository/service/controller exist. List, detail, create, update exist. `validationStatus` is an admin-managed catalogue status, not a validation inbox workflow. Audit events exist for create/update/status edits and archive-like `active=false` changes. Role rules: public GET, ADMIN create/update. | List/detail/create/edit exist with admin menu link under Eventos. Form has name, edition, type, venue, dates, city/country, organizer, website. Status chip is shown. | Frontend form still needs to expose backend description, evidence URL, and active fields. No audit/history panel. Missing richer organizer relationship and portal visibility policy beyond validated/active visibility. |
| Event participations | Table/entity/repository/service/controller exist. List, detail, create, update, submit-to-validation exist. Validation inbox and audit events exist. `evidenceUrl` exists. No delete/archive endpoint. Role rules: public GET, ADMIN/RESEARCHER create/update; service enforces researcher ownership for non-admins. | List/detail/create/edit exist. Researcher route `/app/actividades/nueva` exists. Detail shows validation comments, submit button, and audit history. Admin/researcher menu links exist. | Frontend form still needs to expose `evidenceUrl`. Delete/archive is missing. Detail is public/shared, so long-term route ownership should be clarified. |
| Venues/channels | Table/entity/repository/service/controller exist. List, detail, create, update exist. `validationStatus` is an admin-managed catalogue status, not a validation inbox workflow. Audit events exist for create/update/status edits and archive-like `active=false` changes. Role rules: public GET, ADMIN create/update. | List/detail/create/edit exist with admin menu link under Canales. Form covers name, short name, type, ISSN/EISSN/ISBN, country, website, active. Status chip is shown. | Frontend form still needs publisher selector and description field. No portal visibility flag or audit/history panel. Validation workflow is intentionally not used; see `docs/validation-scope.md`. |
| Publishers | Table/entity/repository/service/controller exist. List, detail, create, update exist. No validation status, no audit events, no delete/archive endpoint. Role rules: public GET, ADMIN create/update. | API service only supports search/get. No list page, detail page, create form, edit form, or menu link. Used only as publication metadata lookup. | Need admin maintenance UI, service create/update methods, route/menu placement, publication form selector, and optional archive via active flag. Missing description and links to managed venues/publications in UI. |
| Topics | Table/repository exist. Topics are created/updated indirectly through publications. Topic normalization endpoints exist for candidates, merge, and canonical-name suggestion; topic merge is audited. No basic list/detail/create/update/delete CRUD controller. | Topic normalization admin page exists. Topics appear in publication/researcher/unit/portal views. No first-class topic list/detail/create/edit form. | Need decide whether topics remain publication-derived or become master data. If master data, add list/detail/create/edit, aliases/description, audit, and maybe merge history. Current "AI suggestion" for canonical names is local LLM-assisted only, not a persisted suggestion entity. |
| Report templates | Table/entity/repository/service/controller exist. List, detail, create, update exist. Active flag works as archive-like state. No validation workflow, audit events, or delete endpoint. Role rules: ADMIN-only. | Admin reports page includes template list, create/edit form, save action, and active toggle. Report-generation form includes additional instructions, but templates do not store them. Menu link exists as Informes. | No dedicated template detail route, no cancel/reset beyond starting a new template, no audit/history panel, and no stored "additional instructions" field despite generator form support. |
| Portal news/articles | No table/entity/controller found. | No route/page/form found. | Not implemented. Needs product decision before CRUD planning. |
| Public summaries | No separate entity found. Publication `abstractText` exists; generated report/copilot summaries exist only as responses. | No public-summary form or workflow found. | Not implemented as durable data. Publication `publicSummary` is missing from backend and frontend. |
| AI suggestions | No persisted suggestion table/entity/controller found. Local AI exists for embeddings, copilot, reports, expert finder, semantic search, and topic canonical-name suggestion. | No AI suggestion inbox or CRUD UI. Topic normalization can request a canonical-name suggestion. | Not implemented as an entity. Do not add until product workflow defines suggestion source, target entity, review status, audit, and permissions. |

## Missing Create/Edit Forms

| Area | Missing or incomplete form |
| --- | --- |
| Publishers | No frontend list/detail/create/edit routes, no admin menu link, and no create/update client methods. |
| Topics | No first-class list/detail/create/edit form; only normalization/merge workflow exists. |
| External organizations/entities | No dedicated form mode or page; admins must use the unit form and manually manage `type` plus `visibleInPortal`. |
| Publications | Form exists but is incomplete for metadata and authors: no venue/publisher selection, ISBN/ISSN/language editing, multi-author add/edit/reorder, publication date, public summary, visibility flag, external organization links, or related-publication curation. |
| Researcher-owned records | My Activities links to researcher/publication/affiliation edit targets, but those forms are guarded in the template for ADMIN-only editing, so the researcher workflow is incomplete outside event participations. |
| Report templates | Template form exists on reports page, but no dedicated route/detail and no stored additional-instructions field. |

## Missing Fields

### Publications

Current backend/form covers title, abstract text, year, type, academic status, DOI, source, URL, authors, and topics. Backend also has venue, publisher, ISBN, ISSN, and language, but the form preserves existing values instead of editing them.

Missing or incomplete:

- `publicSummary`
- `publicationDate`
- explicit `validationStatus` and validation comment in publication DTO/detail UI
- editable venue and publisher selectors
- editable ISBN/ISSN/language
- multiple internal/external authors with ordering and corresponding-author editing after creation
- external organization links beyond textual external affiliation
- keywords separate from normalized topics, if desired
- visibility/public flag, if publications need per-record portal visibility beyond validation
- richer source/origin provenance
- curated related-publication links

### Researchers

Current backend/form covers full name, display name, email, ORCID, active, affiliations, and derived topics/publications.

Missing or incomplete:

- public profile summary
- portal visibility flag
- public/private email separation
- main affiliation override, if derived affiliation is not enough
- editable expertise/topics separate from publication-derived topics
- role/responsibility field
- explicit validation status/comment in researcher DTO/detail UI

### Internal Research Units

Current backend/form covers name, short name/acronym, type, parent, country, city, website, active, and `visibleInPortal`.

Missing or incomplete:

- public description
- internal description
- responsible researcher/person
- editable topics
- featured/order flag
- explicit internal-vs-external classification beyond type/visibility
- validation status/comment in unit DTO/detail UI

### External Organizations

Current model uses research units for external organizations.

Missing or incomplete:

- dedicated organization model or dedicated admin mode
- description
- relation type with institution/publications/events
- public directory exclusion guardrails beyond `visibleInPortal`
- richer country/identifier metadata if required later

### Scientific Events

Current backend/form covers name, edition, type, dates, city/country, organizer, website, venue, and validation status.

Missing or incomplete:

- description
- evidence/source URL
- frontend exposure for description and evidence/source URL
- audit/history panel
- portal visibility, if events need a distinct public-facing flag beyond validated/active visibility

### Event Participations

Current backend/form covers event, researcher, research unit, participation type, title, description, date, related publication, validation status/comments, submit action, and audit.

Missing or incomplete:

- frontend exposure for `evidenceUrl`
- archive/delete behavior
- clearer ownership-specific routes for researcher vs admin contexts

### Venues/Channels

Current backend/form covers name, short name, type, ISSN/EISSN/ISBN, country, website, active, and validation status.

Missing or incomplete:

- frontend exposure for publisher relationship
- frontend exposure for description
- portal visibility, if venues should appear publicly
- audit/history panel

### Report Templates

Current backend/form covers name, description, target type, sections, default year range, output format, and active.

Missing or incomplete:

- stored additional safe instructions, if templates should carry them
- audit/history
- delete/archive endpoint beyond active toggle

## Missing Menu Links

| Missing link | Recommended area |
| --- | --- |
| Publisher maintenance | `/admin/datos-maestros` hub or `Catálogo` side navigation after routes/forms exist. |
| Topic master-data CRUD | Keep current `Normalización de temas` for curation; add a topic catalogue only if topics become explicit master data. |
| External organizations | Either a dedicated `/admin/organizaciones-externas` entry or a filtered unit catalogue tab once the route exists. |
| Portal news/articles | No link until the entity exists. |
| AI suggestions | No link until there is a persisted suggestion workflow. |

Existing admin side navigation already links publications, researchers, units, participations, events, channels, reports, validation, audit, ingestion, quality, strategic map, assistant, opportunities, and topic normalization.

## Missing Backend Endpoints

| Entity | Missing endpoint or contract |
| --- | --- |
| Publications | Delete/archive endpoint; submit endpoint for direct detail workflow if not only via `/api/me`; DTO fields for validation status/comment; fields for publication date, public summary, visibility, external organizations, and related links if adopted. |
| Researchers | Delete/archive endpoint or explicit archive endpoint; DTO fields for validation status/comment and public profile fields. |
| Research units | Delete/archive endpoint or explicit archive endpoint; DTO fields for validation status/comment and description/responsible/topic/featured fields if adopted. |
| External organizations | Dedicated endpoints if separated from research units. |
| Scientific events | Optional dedicated archive endpoint if active-toggle semantics are not enough. |
| Event participations | Delete/archive endpoint. |
| Venues | Optional dedicated archive endpoint if active-toggle semantics are not enough. |
| Publishers | Delete/archive endpoint or active-toggle semantics; audit events if important. |
| Topics | First-class list/detail/create/update/delete if topics become master data. |
| Report templates | Delete/archive endpoint if active toggle is not enough; audit events; optional stored additional instructions. |

## Recommended Implementation Phases

1. Fix researcher-owned workflow completeness.
   - Allow researchers to edit own publication/profile/affiliation records from My Activities, or change My Activities links/actions so they do not promise unavailable edits.
   - Add submit actions in the detail forms that are meant to participate in validation.

2. Complete publication metadata forms.
   - Add editable venue, publisher, ISBN/ISSN, language, multi-author management, and better required-field messaging.
   - Decide whether `publicationDate`, `publicSummary`, and per-publication portal visibility belong in the data model now.

3. Clarify validation scope.
   - Backend policy is now explicit: scientific events and venues/channels are admin-managed catalogue records, not validation inbox items.
   - See `docs/validation-scope.md` for the current scope.

4. Add publisher maintenance.
   - Extend the frontend API service with create/update.
   - Add admin list/detail/create/edit routes and menu/hub links.
   - Link publisher selection from publication and venue workflows as appropriate.

5. Separate external organizations from internal units at the UI level.
   - Start with a filtered admin page or tabs over the existing `research_units` model.
   - Later decide whether a dedicated table is warranted.

6. Decide topic strategy.
   - If topics remain publication-derived, keep normalization as the only admin workflow.
   - If topics become managed master data, add CRUD, audit, descriptions, aliases, and merge history.

7. Harden archive/delete policy.
   - Prefer explicit archive endpoints for entities already using `active`.
   - Avoid hard delete for records referenced by publications, affiliations, events, reports, or audit history.

8. Plan optional content/suggestion entities later.
   - Portal news/articles, public summaries, and AI suggestions should wait for a product workflow defining ownership, review, visibility, and retention.

## Risk Areas

- Validation status is intentionally split: core validation entities use the inbox, while venues and scientific events use admin-managed catalogue status. UI labels and help should make this distinction clear.
- Researcher-owned API permissions exist for some updates, but the frontend forms are mostly admin-only, creating broken workflow expectations from My Activities.
- External organizations are stored as research units; a single wrong `visibleInPortal` value can leak collaborators into the internal public units directory.
- Publication form saves existing venue/publisher/identifier/language values without letting users edit them, so future backend fields can appear supported while remaining unreachable.
- Shared legacy and admin routes reuse components. CRUD improvements must preserve portal/public visibility and role-specific actions.
- Delete/archive needs careful referential rules because publications, authors, affiliations, events, topics, embeddings, validation, and audit records are linked.
- AI-related suggestion behavior is currently response-only or helper-only; adding persistent suggestions later will require validation/audit/permissions from the start.

## Verification

No build or backend verification was run because this task changed documentation only.
