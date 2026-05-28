# Frontend Instructions

## Scope

Applies to `frontend/**`. Also follow root `AGENTS.md`.

## Stack

- Angular standalone components, Angular Router, Angular Material, strict TypeScript, RxJS, and signals where useful.
- Current project dependencies are Angular 18.x.
- API DTOs live under `src/app/core/api`.

## Structure

- Keep feature code under `src/app/features/**`.
- Keep shared presentational components under `src/app/shared/components`.
- Keep API calls in `src/app/core/api` services.
- Keep auth state, guards, and interceptors under `src/app/core/auth`.
- Route-level features should stay lazy-loaded where practical.

## UI And Language

- User-facing UI labels, validation text, navigation, empty states, and demo copy must remain Spanish.
- Internal TypeScript names, DTO interfaces, service names, and comments stay English.
- Use Angular Material for base UI controls.
- Reuse existing shared components before adding new UI patterns.
- Keep components focused; extract reusable components only after real duplication appears.

## Routing And Visibility

- Preserve route guards and role metadata.
- Public routes include publication/researcher exploration, semantic search, graph, and copilot where currently configured.
- Admin/validator/researcher pages must keep their existing role restrictions.
- Do not change backend visibility from a frontend-only task.

## Styling

- Keep styling consistent with existing layout and Material usage.
- Avoid broad redesigns, global CSS churn, or visual rewrites unless explicitly requested.
- Ensure Spanish text fits in responsive layouts.

## Typing And Build

- Do not use `any` unless unavoidable and justified.
- Keep API models typed and synchronized with backend DTOs when contracts change.
- For frontend tasks, run `npm run build` from `frontend`.
