# Sample Data

Demo data is loaded by Flyway migrations:

- `backend/src/main/resources/db/migration/V2__insert_sample_data.sql`
- `backend/src/main/resources/db/migration/V4__spanish_publication_sample_data.sql`
- `backend/src/main/resources/db/migration/V5__rich_spanish_demo_dataset.sql`
- `backend/src/main/resources/db/migration/V23__demo_workflow_dataset_and_report_support.sql`

The dataset includes:

- multiple root research units
- nested faculties, departments, centers, labs, and research groups
- researchers with multiple simultaneous affiliations
- current and former affiliations
- Spanish publications with internal and external authors
- Spanish normalized topics designed for semantic-search demos
- separated clusters for clinical AI, public health, genomics, institutional analytics, panther conservation, and unrelated domains
- expanded domain coverage for energia y movilidad sostenible, ciencias marinas, robotica submarina, educacion digital, gobierno digital, ciberseguridad, linguistica computacional, patrimonio digital, neurociencia cognitiva, economia circular, arquitectura resiliente y recursos hidricos
- additional large-volume demo coverage for bioinformatica, agricultura de precision, turismo sostenible, derecho digital, manufactura aditiva, ciencias del deporte, psicologia social, musica digital, logistica portuaria, quimica verde, meteorologia extrema, vivienda inclusiva e historia urbana
- additional physics-heavy coverage for astrofisica computacional, cosmologia observacional, ondas gravitacionales, exoplanetas, fisica cuantica, informacion cuantica, simulacion cuantica, fisica nuclear, detectores, fisica de particulas, fotonica, materiales cuanticos, plasmas y fusion
- workflow-focused coverage for validation statuses, data-quality issues, report generation, external organization handling, clinical AI/hospitals, public health/climate, panther conservation, and research-management reports

The CSV ingestion sample is also in Spanish:

- `sample-data/publications.csv`

The MVP intentionally excludes citation counts and all other bibliometric metrics.

See `docs/demo-data.md` for demo users, embedding rebuild instructions, semantic-search queries, and report-generation test cases.
