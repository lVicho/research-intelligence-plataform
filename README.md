# Research Intelligence Platform

Research Intelligence Platform is a local-first, AI-ready research analytics platform for universities, research centers, hospitals, laboratories, and research institutions.

Phase 1 is a clean full-stack MVP foundation. It supports institutional research units, researchers, affiliations, publications, topics, richer analytics, advanced filters, the researcher graph, CSV publication ingestion, semantic search, and local-first AI architecture. Recommendations and bibliometric metrics are intentionally not implemented yet.

## Project Structure

```text
research-intelligence-platform/
  backend/       Spring Boot modular monolith
  frontend/      Angular standalone application
  infra/         Infrastructure examples
  docs/          Architecture and implementation notes
  sample-data/   Demo data notes
  AGENTS.md
  README.md
  docker-compose.yml
```

## Requirements

- Java 21
- Maven 3.6.3 or newer
- Node.js `^20.19.0`, `^22.12.0`, or `^24.0.0`
- npm
- Docker with Docker Compose

The backend uses Spring Boot `4.0.6`. The frontend uses Angular `18.x`.

For Codex task context, see `docs/codex-task-guidelines.md` and the scoped `AGENTS.md` files.

## Run PostgreSQL

```powershell
docker compose up -d postgres
```

PostgreSQL runs on `localhost:5432` with pgvector enabled through the `pgvector/pgvector:pg16` image.

- database: `research_intelligence`
- username: `research`
- password: `research`

Flyway creates the schema, enables the `vector` extension, and loads demo data when the backend starts. If you already created the database with the old plain PostgreSQL image, recreate the local volume after changing images:

```powershell
docker compose down -v
docker compose up -d postgres
```

## Run Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The API runs at:

```text
http://localhost:8080/api
```

To run backend tests:

```powershell
cd backend
.\mvnw.cmd clean test
```

## Run Frontend

```powershell
cd frontend
npm install
npm start
```

The Angular app runs at:

```text
http://localhost:4200
```

The frontend development server proxies `/api` requests through `frontend/proxy.conf.json` to the local backend on `http://localhost:8081`. If you expose the frontend publicly through a tunnel such as ngrok, tunnel the Angular dev server on `4200` so those proxied API requests keep reaching the backend.

The backend development CORS defaults also allow `http://127.0.0.1:4200` for local browser testing.

To build the frontend:

```powershell
cd frontend
npm run build
```

## Authentication And Roles

The MVP uses Spring Security with stateless HTTP Basic credentials. The Angular login page posts to `/api/auth/login`, stores a Basic token locally, and sends it on protected API calls. Passwords are hashed with BCrypt.

Demo users are created on backend startup if they do not already exist. All use the password `demo123`:

- `admin@demo.local` with role `ADMIN`
- `validator@demo.local` with role `VALIDATOR`
- `researcher@demo.local` with role `RESEARCHER`, linked to researcher id `1` when present
- `researcher1@demo.local` with role `RESEARCHER`, linked to Maya Chen when demo data is present
- `researcher2@demo.local` with role `RESEARCHER`, linked to Carla Serra when demo data is present
- `researcher3@demo.local` with role `RESEARCHER`, linked to Ines Carvalho when demo data is present

Seeded roles:

- `PUBLIC_USER`: Usuario público
- `RESEARCHER`: Investigador
- `ADMIN`: Administrador
- `VALIDATOR`: Validador

Public read endpoints remain available without login. Analytics, validation/ingestion and embedding rebuild routes require `ADMIN` or `VALIDATOR`. Master-data writes for research units, researchers and publications require `ADMIN`.

Bundled demo seed records include validated, pending, rejected, draft, and changes-requested examples so public exploration, validation, data-quality, AI assistance, semantic search, and report generation can be tested. Public portal pages only show validated/published records according to backend visibility rules. More detail is available in `docs/demo-data.md`.

## Technical Auditing

Spring Data JPA auditing is enabled for core editable records. Research units, researchers, researcher affiliations, publications and demo users store:

- `created_at` / `updated_at`
- `created_by_user_id` / `updated_by_user_id`

The auditor uses the authenticated Spring Security principal and stores the internal user id. Seeded data, Flyway data and startup jobs that run without an authenticated principal keep the user fields `null`; the UI shows those as `Sistema / sin usuario`. Topics are generated from publication edits and are not manually editable yet, so they do not carry audit user fields. Detail/admin views show audit metadata, while public list DTOs do not include updater ids.

## Local AI With Ollama

AI defaults to mock providers, so the backend runs without Ollama or paid external APIs.

Default configuration:

```yaml
ai.provider: mock
ai.embedding-provider: mock
ai.embedding-dimension: 1024
ai.retrieval.default-limit: 10
ai.retrieval.max-limit: 20
ai.retrieval.min-similarity: 0.35
ai.retrieval.strict-min-similarity: 0.45
ai.ollama.base-url: http://localhost:11434
ai.ollama.chat-model: qwen2.5:14b
ai.ollama.embedding-model: bge-m3
```

The embedding dimension is configurable with `AI_EMBEDDING_DIMENSION`. The default is `1024`, which matches the published dense embedding dimension for `BAAI/bge-m3` in the BAAI Hugging Face model card: https://huggingface.co/BAAI/bge-m3. Do not reuse this value blindly for other embedding models.
The Ollama embedding provider calls `/api/embed`; the Ollama chat provider calls `/api/generate`.

To use local Ollama providers:

```powershell
winget install Ollama.Ollama
ollama pull qwen2.5:14b
ollama pull bge-m3
```

Then start the backend with:

```powershell
$env:AI_PROVIDER='ollama'
$env:AI_EMBEDDING_PROVIDER='ollama'
cd backend
.\mvnw.cmd spring-boot:run
```

Optional overrides:

```powershell
$env:AI_OLLAMA_BASE_URL='http://localhost:11434'
$env:AI_OLLAMA_CHAT_MODEL='qwen2.5:14b'
$env:AI_OLLAMA_EMBEDDING_MODEL='bge-m3'
$env:AI_EMBEDDING_DIMENSION='1024'
$env:AI_RETRIEVAL_MIN_SIMILARITY='0.35'
```

OpenAI is intentionally left as an extension point and has no implementation in the MVP.

To build publication embeddings:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/ai/embeddings/publications/rebuild
```

To query semantic search:

```powershell
Invoke-RestMethod "http://localhost:8080/api/publications/semantic-search?query=busqueda%20semantica%20clinica&limit=5&minSimilarity=0.35"
```

Retrieval modes for Copilot:

- `STRICT`: uses `ai.retrieval.strict-min-similarity` for a smaller, higher-confidence context.
- `BALANCED`: uses `ai.retrieval.min-similarity` and the default limit.
- `BROAD`: uses `ai.retrieval.broad-min-similarity`, the max limit by default, and returns a caution warning for exploratory context.

## Main Endpoints

Auth:

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`

Private researcher workspace:

- `GET /api/me/dashboard`
- `GET /api/me/activities?status=&type=&text=&page=&size=`
- `GET /api/me/activities/{entityType}/{entityId}`
- `POST /api/me/activities/{entityType}/{entityId}/submit`

The private workspace uses the authenticated user linked through `researcher_id`. Researchers see only their own profile, affiliations and authored publications. Draft activities and activities with requested changes can be edited by the owner and submitted to validation; validator comments remain visible in `Mis actividades`.

Research units:

- `GET /api/research-units`
- `GET /api/research-units/tree`
- `GET /api/research-units/{id}`
- `POST /api/research-units`
- `PUT /api/research-units/{id}`

Researchers:

- `GET /api/researchers?page=&size=&text=&researchUnitId=&active=`
- `GET /api/researchers/{id}`
- `POST /api/researchers`
- `PUT /api/researchers/{id}`
- `GET /api/researchers/{id}/affiliations`
- `POST /api/researchers/{id}/affiliations`
- `PUT /api/researchers/{id}/affiliations/{affiliationId}`
- `DELETE /api/researchers/{id}/affiliations/{affiliationId}`

Publications:

- `GET /api/publications?page=&size=&text=&yearFrom=&yearTo=&type=&status=&researchUnitId=&researcherId=&topic=&sortBy=&sortDirection=`
- `GET /api/publications/filter-metadata`
- `GET /api/publications/semantic-search?query=&limit=&minSimilarity=`
- `GET /api/publications/{id}`
- `POST /api/publications`
- `PUT /api/publications/{id}`

Ingestion:

- `POST /api/ingestion/publications/csv`

The CSV ingestion endpoint accepts a multipart field named `file`. Required columns are:

```text
title, abstractText, year, type, status, doi, source, url, authors, topics
```

Optional columns are:

```text
internalAuthorOrcids, internalAuthorNames, externalAffiliations
```

Use semicolons inside a CSV field for multi-value author, topic, ORCID, internal-name, and affiliation lists. The importer matches existing publications by DOI first, then by normalized title and year. Internal authors are matched by ORCID when available, otherwise by normalized display name. Unknown authors are stored as external authors, and missing topics are created. A Spanish sample file is available at `sample-data/publications.csv`.

Analytics:

- `GET /api/analytics/summary`

Validation:

- `GET /api/validation/inbox?status=&entityType=&researcherId=&researchUnitId=&submittedFrom=&submittedTo=&text=&page=&size=&sort=`
- `GET /api/validation/items/{entityType}/{entityId}`
- `POST /api/validation/items/{entityType}/{entityId}/validate`
- `POST /api/validation/items/{entityType}/{entityId}/reject`
- `POST /api/validation/items/{entityType}/{entityId}/request-changes`

The validation inbox is available in the Angular admin navigation as `Bandeja de validación`. It is restricted to `ADMIN` and `VALIDATOR`, defaults to `PENDING_VALIDATION`, and reviews research units, researchers, researcher affiliations, and publications. Review actions accept an optional JSON body:

```json
{ "comment": "Comentario de revisión" }
```

Graph:

- `GET /api/graph/researcher/{researcherId}`

AI and copilot:

- `POST /api/copilot/ask`
- `POST /api/copilot/retrieve`
- `POST /api/copilot/answer`
- `POST /api/ai/embeddings/publications/rebuild`

The embedding rebuild endpoint generates missing publication embeddings with the configured `EmbeddingService` and stores them in PostgreSQL pgvector. Semantic search generates a query embedding and searches the nearest stored publication embeddings, then filters by the configured or requested similarity threshold. The copilot uses semantic retrieval when embeddings exist for the configured provider, model, and dimension; otherwise it falls back to text search. Responses include answer text, retrieved context, cited publications, provider, model, retrieval method, retrieval mode, similarity scores, threshold status, retrieval reasons, and warnings.

The bundled demo publication dataset is in Spanish so semantic search and copilot citations are easier to inspect manually with Spanish queries. Demo queries are available in `docs/demo-queries.md`.

## Architecture

The backend is a modular monolith with feature modules:

- `shared`
- `researchunits`
- `researchers`
- `publications`
- `analytics`
- `validation`
- `graph`
- `ingestion`
- `ai`

Each feature keeps REST DTOs in `api`, use-case logic in `application`, domain enums in `domain`, and JPA persistence in `persistence`. Controllers are thin, services hold use-case behavior, and REST never exposes JPA entities.

The frontend uses Angular standalone components, Angular Router, Angular Material, strict TypeScript, and typed API services under `src/app/core/api`.

## Current Limitations

- Semantic search currently uses dense vector similarity only; hybrid ranking belongs to the next phase.
- OpenAI providers are not implemented yet.
- No collaboration recommendations.
- No citation counts or bibliometric metrics.
- The publication form supports simple creation and preserves existing authors on update; richer author editing belongs in a later phase.
- The research graph is an MVP subgraph built from PostgreSQL queries at request time. It is not a separate graph database, does not persist graph projections, and uses a simple client-side layout for exploration.
- CSV ingestion updates imported publication authors and topics as the source snapshot for that publication; it does not create new researcher records for unmatched authors.
- The mock AI provider is deterministic placeholder behavior; use Ollama for local real model responses.
