update research_units
set validation_status = 'VALIDATED',
    validated_at = coalesce(updated_at, created_at),
    validation_comment = coalesce(validation_comment, 'Demo seed data validated for public exploration.')
where validation_status = 'PENDING_VALIDATION'
  and created_by_user_id is null;

update researchers
set validation_status = 'VALIDATED',
    validated_at = coalesce(updated_at, created_at),
    validation_comment = coalesce(validation_comment, 'Demo seed data validated for public exploration.')
where validation_status = 'PENDING_VALIDATION'
  and created_by_user_id is null;

update researcher_affiliations
set validation_status = 'VALIDATED',
    validated_at = coalesce(updated_at, created_at),
    validation_comment = coalesce(validation_comment, 'Demo seed data validated for public exploration.')
where validation_status = 'PENDING_VALIDATION'
  and created_by_user_id is null;

update publications
set validation_status = 'VALIDATED',
    validated_at = coalesce(updated_at, created_at),
    validation_comment = coalesce(validation_comment, 'Demo seed data validated for public exploration.')
where validation_status = 'PENDING_VALIDATION'
  and created_by_user_id is null;
