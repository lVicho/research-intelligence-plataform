# Workflow Current State

## Existing Features

- Backend modular monolith with Spring Boot, PostgreSQL, Flyway, JPA, validation, security, and audit support.
- Angular frontend with public exploration pages and protected workspace/admin pages.
- Research units list, tree, detail, create, and update.
- Researchers list, detail, create, update, affiliations, coauthor summaries, and researcher workspace views.
- Publications list, filters, filter metadata, detail, create, update, topics, authors, and related publications.
- Analytics summary for admin/validator users.
- Researcher graph endpoint and frontend graph view.
- CSV publication ingestion with internal/external author matching and row errors.
- Authentication, role-based route/API visibility, and demo users.
- Validation inbox and researcher activity submission workflow.
- Local-first AI configuration with mock and Ollama providers.
- pgvector publication embeddings, semantic search, and Copilot retrieval/answer endpoints with citations.
- Spanish demo dataset and CSV sample.

## Last Completed Phase

The codebase appears complete through the original Phase 9 goal: local AI research copilot with retrieval and citations. It also contains some Phase 12 demo-polish foundations, plus validation workflow work that was not listed as a separate original phase.

## Pending Or Limited

- OpenAI providers are not implemented.
- Collaboration recommendations are not implemented.
- Bibliometric metrics are not implemented.
- Related publications and search can be improved further with deeper hybrid ranking and provenance.
- Graph exploration is an MVP relational projection, not a graph database or persisted graph projection.
- CSV ingestion is publication-focused and does not create new internal researchers for unmatched authors.
- Admin audit and master-data placeholders exist in the frontend but are not full dedicated admin workspaces.
- Mock AI is deterministic placeholder behavior; use Ollama for local real model responses.

## Recommended Next Work

Prefer small tasks by area: backend-only, frontend-only, docs-only, or sample-data-only. Cross-cutting tasks should name the exact API contract and affected pages before coding.
