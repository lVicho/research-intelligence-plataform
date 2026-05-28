alter table scientific_events
    add column description text,
    add column evidence_url varchar(500),
    add column active boolean not null default true;

create index idx_scientific_events_active on scientific_events(active);
create index idx_scientific_events_active_validation on scientific_events(active, validation_status, start_date desc);

alter table venues
    add column description text,
    add column publisher_id bigint references publishers(id);

create index idx_venues_publisher_id on venues(publisher_id);

alter table event_participations
    add column evidence_url varchar(500);
