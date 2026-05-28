# Phase 1 Architecture

Research Intelligence Platform is implemented as a modular monolith.

The backend uses package-by-feature modules under `com.researchintelligence.platform`:

- `shared`: common API response types, error handling, CORS, and persistence base support.
- `researchunits`: institutional units and hierarchy navigation.
- `researchers`: researcher profiles and affiliations.
- `publications`: publications, authors, topics, and filters.
- `analytics`: cross-module summary counts.
- `graph`: read-only research knowledge graph projections built from existing relational data.

REST controllers only exchange DTO records. JPA entities remain inside persistence packages and are not returned directly from controllers.

The frontend is a strict Angular standalone application with feature folders for dashboard, research units, researchers, publications, and graph exploration. API calls live in `src/app/core/api`.

## Research Graph MVP

The first graph endpoint is `GET /api/graph/researcher/{researcherId}`. It returns DTO-only nodes and edges for a researcher neighborhood:

- researcher, research unit, publication, topic, and external author nodes
- affiliation, authorship, topic, coauthor, and research-unit hierarchy edges
- coauthor edge weights based on shared publication count

This version deliberately does not introduce a graph database or persisted graph projection. The backend assembles the graph from PostgreSQL-backed repositories at request time, and the frontend renders the result with a client-side Cytoscape layout.

Future improvements can add graph paging/depth controls, cached projections, richer edge provenance, semantic similarity edges, collaboration recommendations, and eventually graph-specific storage if relational queries stop being enough.

Out of scope for Phase 1:

- AI providers and copilot
- ingestion workflows
- semantic search
- recommendations
- bibliometric metrics
