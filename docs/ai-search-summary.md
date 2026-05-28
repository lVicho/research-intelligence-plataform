# AI And Search Summary

## Providers

The application is local-first. Mock AI providers are enabled by default, so the backend can run without external services.

Ollama is the local real-AI option:

- Chat model default: `qwen2.5:14b`.
- Embedding model default: `bge-m3`.
- Base URL default: `http://localhost:11434`.
- Embedding endpoint: Ollama `/api/embed`.
- Chat endpoint: Ollama `/api/generate`.

OpenAI is intentionally future-only unless a task explicitly requests it.

## Configuration

Important backend keys:

- `ai.provider`
- `ai.embedding-provider`
- `ai.embedding-dimension`
- `ai.retrieval.default-limit`
- `ai.retrieval.max-limit`
- `ai.retrieval.min-similarity`
- `ai.retrieval.strict-min-similarity`
- `ai.retrieval.broad-min-similarity`
- `ai.retrieval.allow-no-context-answers`
- `ai.ollama.base-url`
- `ai.ollama.chat-model`
- `ai.ollama.embedding-model`

The default embedding dimension is `1024`, matching the configured `bge-m3` expectation. Do not reuse it for another embedding model without checking that model.

## pgvector And Semantic Search

Flyway enables the `vector` extension and creates `publication_embeddings`.

Embedding rebuild stores publication vectors for the configured provider, model, and dimension. Semantic search embeds the query, searches nearest publication vectors, and applies the configured or requested similarity threshold.

If matching embeddings are missing, retrieval can fall back to text search where implemented.

## Copilot

Copilot follows a retrieval-first pattern:

1. Retrieve relevant publications.
2. Build compact context.
3. Ask the LLM to answer only from that context.
4. Return answer text, retrieved publications, cited publications, provider/model metadata, retrieval method, scores, threshold status, reasons, and warnings.

When context is weak or missing, Copilot should warn clearly and avoid unsupported claims. Citations must refer to publications used as context.

## Reports And Dossiers

Admin report generation is template-aware. Report templates are structured records with target type, ordered section codes, optional default year range, output format, and active state. The backend resolves the template into section headings and builds the LLM prompt from retrieved publication evidence; the normal UI does not expose raw system prompt editing.

Reports must use retrieved evidence, cite publication-backed claims with `[pub:ID]`, and avoid invented publications, authors, relationships, or metrics. Optional additional instructions are treated as bounded user preferences and do not override evidence and citation rules. Markdown export is implemented; PDF and DOCX export remain a later phase.

## Visibility

Public Copilot and graph endpoints are currently readable without login. Embedding rebuild and operational AI endpoints under `/api/ai/**` require `ADMIN` or `VALIDATOR`.
