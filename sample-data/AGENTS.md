# Sample Data Instructions

## Scope

Applies to `sample-data/**` and seed/demo data changes in Flyway migrations. Also follow root `AGENTS.md`.

## Demo Data Rules

- Demo and CSV sample data should be Spanish.
- Keep data realistic enough for demos, but fictional unless a task explicitly uses public real data with sources.
- Validate CSV headers and required fields before changing samples.
- Preserve internal/external author separation; do not create fake internal researchers for every external author.
- Preserve semantic clusters used for demos: clinical AI/hospitals, public health/climate, genomics/knowledge graphs, institutional analytics, panther conservation, and unrelated domains.
- Keep hospital/clinical AI content semantically separate from panther/conservation content so retrieval quality can be tested.
- Do not add citation counts or bibliometric metrics to sample data.
