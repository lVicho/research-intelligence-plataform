# Research Intelligence Platform

## Scope

This root file contains global product and architecture rules only. For area-specific work, also read the nearest scoped instructions:

- `backend/AGENTS.md` for Java, Spring Boot, persistence, security, validation, AI, and backend tests.
- `frontend/AGENTS.md` for Angular, Spanish UI, routing, components, styling, and frontend builds.
- `docs/AGENTS.md` for documentation style and accuracy.
- `sample-data/AGENTS.md` for demo data and CSV seed data.

For stable task context, start with `docs/codex-task-guidelines.md`.

## Product Vision

Research Intelligence Platform is a local-first, AI-ready research analytics platform for universities, research centers, hospitals, laboratories, and research institutions.

The platform helps users explore researchers, research units, publications, topics, affiliations, collaborations, institutional activity, semantic relationships, and emerging research trends.

## Technical Philosophy

- Build a modular monolith, not microservices.
- Keep the system clean, explicit, testable, extensible, maintainable, and boring where possible.
- Prefer advanced techniques only where they create real product value.
- Avoid unnecessary abstractions, premature optimization, magic, and overengineered frameworks.
- Do not expose JPA entities through REST.
- Do not hardcode AI providers, API keys, or secrets.
- Do not invent bibliometric metrics. Citation counts and similar metrics must later include explicit source and measurement date.

## Architecture Baseline

- Repository areas are `backend`, `frontend`, `infra`, `docs`, and `sample-data`.
- Backend package root is `com.researchintelligence.platform`.
- Backend modules use package-by-feature modular monolith boundaries.
- Frontend uses Angular standalone components and feature-based folders.
- Database is PostgreSQL with Flyway migrations.
- AI is local-first: mock providers by default, Ollama for local real AI, and OpenAI only as a future optional provider unless explicitly requested.

## Workflow

- Before changing files, inspect the relevant area and explain the plan briefly.
- Make small coherent changes.
- Keep internal code and identifiers in English.
- Keep user-facing UI text in Spanish.
- Update README or docs when behavior or task context changes.
- After changes, list changed files, verification performed, assumptions, and limitations.
