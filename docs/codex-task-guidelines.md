# Codex Task Guidelines

Use these guidelines to keep future Codex work small, safe, and low-context.

## Start Here

- Read root `AGENTS.md`.
- Read only the scoped `AGENTS.md` for the area being changed.
- Use these summaries before scanning code broadly:
  - `docs/architecture-summary.md`
  - `docs/domain-model-summary.md`
  - `docs/ai-search-summary.md`
  - `docs/workflow-current-state.md`

## Scope Tasks Narrowly

- Backend-only tasks should stay in `backend/**` and should not inspect or change `frontend/**` unless the API contract or user request requires it.
- Frontend-only tasks should stay in `frontend/**` and should not change backend code unless a backend contract bug blocks the UI task.
- Docs-only tasks should stay in `docs/**`, root `README.md`, or scoped `AGENTS.md` files.
- Sample-data tasks should stay in `sample-data/**` and the relevant Flyway seed migration only when seed data must change.
- Cross-cutting tasks should explicitly name the backend endpoints, frontend pages, docs, and tests in scope.

## Verification

- Backend tasks: run `.\mvnw.cmd clean verify` from `backend` on Windows, or `mvn clean verify` where Maven is used directly.
- Frontend tasks: run `npm run build` from `frontend`.
- Docs-only tasks: proofread changed docs and check links/paths.
- Sample-data tasks: validate CSV headers and keep Flyway changes deterministic.

## Guardrails

- Do not add product features during cleanup or documentation tasks.
- Do not redesign business logic unless requested.
- Do not change APIs unless the task explicitly requires a contract change.
- Do not add secrets, API keys, generated large files, or real private data.
- Do not globally reformat existing code.
- Keep internal code English and user-facing UI Spanish.

## Prompt Templates

Backend-only:

```text
Backend-only task. Read /AGENTS.md, /backend/AGENTS.md, and relevant docs summaries only. Do not inspect or change frontend unless a backend API contract issue requires it. Implement [specific backend change] in [module/package]. Preserve current API behavior unless stated. Run .\mvnw.cmd clean verify on Windows, or mvn clean verify where Maven is used directly.
```

Frontend-only:

```text
Frontend-only task. Read /AGENTS.md and /frontend/AGENTS.md. Do not change backend unless the UI is blocked by a confirmed API contract issue. Implement [specific UI change] in [feature/page]. Keep user-facing text Spanish and TypeScript strict. Run npm run build.
```

Docs-only:

```text
Docs-only task. Read /AGENTS.md and /docs/AGENTS.md. Do not change application code. Update [specific doc/file] to reflect current repository behavior. Do not invent claims or completed features. Proofread changed docs and list changed files.
```
