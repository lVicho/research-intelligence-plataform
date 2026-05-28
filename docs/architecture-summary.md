# Architecture Summary

Research Intelligence Platform is a local-first modular monolith with a Spring Boot backend, Angular frontend, PostgreSQL database, and isolated AI providers.

## Backend

- Java 21, Maven, Spring Boot 4.0.6.
- Package root: `com.researchintelligence.platform`.
- Feature modules currently include `shared`, `researchunits`, `researchers`, `publications`, `analytics`, `auth`, `validation`, `graph`, `ingestion`, and `ai`.
- Modules use `api`, `application`, `domain`, and `persistence` packages where useful.
- REST controllers return DTOs/records and do not expose JPA entities.
- Spring Security uses stateless HTTP Basic credentials for the MVP.
- Roles are `PUBLIC_USER`, `RESEARCHER`, `ADMIN`, and `VALIDATOR`.

## Frontend

- Angular 18 standalone application with Angular Router, Angular Material, strict TypeScript, RxJS, and typed API services.
- Feature folders include public exploration, auth workspace pages, validation, ingestion, graph, copilot, and admin placeholders.
- User-facing labels are Spanish; internal TypeScript names are English.
- Route guards protect admin, validator, and researcher workspace pages.

## Database

- PostgreSQL runs through Docker Compose with the `pgvector/pgvector:pg16` image.
- Flyway owns schema and seed data.
- Core tables cover research units, researchers, affiliations, publications, authors, topics, users, roles, validation state, audit user fields, and publication embeddings.
- Bibliometric metrics are intentionally absent.

## AI

- AI is isolated behind `EmbeddingService` and `LlmService`.
- Mock providers are the default.
- Ollama providers support local embeddings and chat through configured HTTP endpoints.
- pgvector stores publication embeddings for semantic search and Copilot retrieval.
- Copilot retrieves records first, then answers from compact context and returns cited publications.
