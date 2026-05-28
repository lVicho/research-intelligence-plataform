alter table research_units
    add column validation_status varchar(50) not null default 'PENDING_VALIDATION',
    add column validation_comment text,
    add column validated_by_user_id bigint references users(id),
    add column validated_at timestamptz;

alter table researchers
    add column validation_status varchar(50) not null default 'PENDING_VALIDATION',
    add column validation_comment text,
    add column validated_by_user_id bigint references users(id),
    add column validated_at timestamptz;

alter table researcher_affiliations
    add column validation_status varchar(50) not null default 'PENDING_VALIDATION',
    add column validation_comment text,
    add column validated_by_user_id bigint references users(id),
    add column validated_at timestamptz;

alter table publications
    add column validation_status varchar(50) not null default 'PENDING_VALIDATION',
    add column validation_comment text,
    add column validated_by_user_id bigint references users(id),
    add column validated_at timestamptz;

create index idx_research_units_validation_status on research_units(validation_status, created_at desc);
create index idx_researchers_validation_status on researchers(validation_status, created_at desc);
create index idx_researcher_affiliations_validation_status on researcher_affiliations(validation_status, created_at desc);
create index idx_publications_validation_status on publications(validation_status, created_at desc);
