# Backend Instructions

## Scope

Applies to `backend/**`. Also follow root `AGENTS.md`.

## Stack

- Java 21, Maven, Spring Boot, Spring Web, Spring Data JPA, Hibernate, Spring Security, Flyway, Bean Validation, PostgreSQL, JUnit 5.
- Current project uses Spring Boot 4.0.6.
- Do not use Lombok.

## Architecture

- Package root: `com.researchintelligence.platform`.
- Keep modular monolith modules package-by-feature: `shared`, `researchunits`, `researchers`, `publications`, `analytics`, `auth`, `validation`, `graph`, `ingestion`, `ai`.
- Inside modules, keep `api`, `application`, `domain`, and `persistence` roles clear.
- Controllers stay thin. Application services hold use-case logic.
- REST APIs must use DTOs/records, never JPA entities.
- Keep mapping simple and manual unless duplication becomes painful.

## Persistence And Migrations

- Use PostgreSQL and Flyway for every schema change.
- Keep migrations deterministic and append-only. Do not edit existing migrations after they are shared unless explicitly requested.
- Preserve existing constraints for ORCID, DOI, author order, primary affiliation, validation, and audit fields.
- Do not add bibliometric metrics unless the task explicitly models source and measurement date.
- pgvector-backed embeddings live in `publication_embeddings`; match provider, model, and dimension when querying.

## Security, Visibility, Audit, Validation

- Spring Security is stateless HTTP Basic for this MVP.
- Public read endpoints exist for publications, researchers, research units, graph, and copilot; admin/validator/researcher routes are role-gated.
- Preserve role semantics: `PUBLIC_USER`, `RESEARCHER`, `ADMIN`, `VALIDATOR`.
- Preserve researcher workspace visibility under `/api/me/**`; researchers must only access their own linked data.
- Core editable records carry audit user fields where implemented.
- Validation statuses are `DRAFT`, `PENDING_VALIDATION`, `VALIDATED`, `REJECTED`, `CHANGES_REQUESTED`.
- Use Bean Validation on API requests and keep API errors consistent through shared handlers.

## AI

- Keep AI isolated behind `EmbeddingService` and `LlmService`.
- Default providers must be mock. Ollama providers call local Ollama HTTP APIs.
- Config keys include `ai.provider`, `ai.embedding-provider`, `ai.embedding-dimension`, retrieval thresholds, and Ollama base/model settings.
- Do not hardcode API keys or paid providers.
- Copilot must answer from retrieved context, return cited publications, and clearly warn when context is weak or missing.

## Testing

- For backend tasks, run `.\mvnw.cmd clean verify` from `backend` on Windows, or `mvn clean verify` where Maven is used directly.
- Add focused tests for non-trivial service logic, retrieval/ranking behavior, security visibility, validation, ingestion matching, and important repository queries.
