# Validation Scope

Date: 2026-05-25

This note clarifies which records are reviewed through the validation inbox and which records are admin-managed catalogue data.

## Full Validation Inbox

The validation inbox is for records that can be submitted, reviewed, returned for changes, rejected, or validated as workflow items:

- Research units
- Researchers
- Researcher affiliations
- Publications
- Event participations

These entities can appear in `/api/validation/**` when they are pending validation, and validation actions create activity audit events.

## Admin-Managed Catalogue Records

Scientific events and venues/channels are not validation inbox items. Their `validationStatus` is an admin-managed catalogue status used for visibility and curation, not a researcher-submitted validation workflow.

Admins can create or update scientific events and venues, including their catalogue status. Create, update, status edits, and archive-like active changes are recorded in activity audit events under `SCIENTIFIC_EVENT` and `VENUE`. These records do not support validation comments or validation actions through `/api/validation/**`.

Scientific events now carry description, evidence URL, and active fields. Venues now carry description and an optional publisher relation. Event participations remain full validation workflow items and can store an evidence URL.

## Catalogue Policy

- Use the validation inbox when a researcher or admin submits an activity that needs review.
- Use admin catalogue editing for events, venues, publishers, and other master data.
- Use `active=false` to archive catalogue events or venues without deleting referenced records.
