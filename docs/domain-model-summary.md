# Domain Model Summary

## Research Units

`ResearchUnit` represents institutional or organizational research units: universities, faculties, departments, institutes, research groups, labs, centers, hospitals, companies, foundations, government agencies, and other units.

Important fields include name, short name, type, optional parent, country, city, website, active flag, portal visibility flag, audit timestamps, audit users, and validation state.

`visibleInPortal` controls whether a validated unit may appear as a first-class item in the public institutional directory under `/portal/unidades`. This keeps external organizations available for affiliations, publications, and collaboration references without mixing them into the university's own unit catalogue.

Hierarchy is optional and supports multiple roots. Researcher membership is modeled through affiliations, not only through the unit tree.

## Researchers And Affiliations

`Researcher` stores identity data: full name, optional display name, email, ORCID, active flag, audit data, and validation state.

`ResearcherAffiliation` connects researchers to research units with role, affiliation type, start/end dates, primary-affiliation flag, audit data, and validation state. A researcher can have several simultaneous affiliations. Current affiliations are open-ended or have an end date in the future.

ORCID is nullable but unique when present. The current primary affiliation is derived from affiliation records. Public portal researcher discovery now depends on having a current validated primary affiliation to a validated `ResearchUnit` with `visibleInPortal = true`.

## Publications, Authors, Topics

`Publication` stores title, abstract, year, type, status, DOI, source, URL, audit data, and validation state. DOI is nullable but unique when present. Citation counts and other bibliometric metrics are not part of the current model.

`PublicationAuthor` preserves author order and supports both internal researchers and external textual authors. External authors must not be converted into fake internal researchers.

`Topic` is a simple normalized keyword. `PublicationTopic` links publications and topics.

## Embeddings And Search

`publication_embeddings` stores one embedding per publication/provider/model/dimension combination through the publication primary key. Searches must match the configured provider, model, and dimension.

Semantic search currently uses dense vector similarity and threshold policies. Related publications combine semantic and metadata signals when available.

## Validation, Roles, Visibility

Validation statuses are `DRAFT`, `PENDING_VALIDATION`, `VALIDATED`, `REJECTED`, and `CHANGES_REQUESTED`.

Roles are `PUBLIC_USER`, `RESEARCHER`, `ADMIN`, and `VALIDATOR`.

Public read endpoints expose exploration data. Admin and validator users manage analytics, ingestion, validation, and embedding rebuilds. Researcher workspace endpoints under `/api/me/**` are scoped to the authenticated linked researcher.
