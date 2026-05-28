create table activity_audit_events (
    id bigserial primary key,
    entity_type varchar(50) not null,
    entity_id bigint not null,
    action varchar(50) not null,
    actor_user_id bigint references users(id),
    actor_display_name varchar(255),
    actor_role varchar(50),
    occurred_at timestamptz not null,
    previous_status varchar(50),
    new_status varchar(50),
    comment text,
    changes_json text
);

create index idx_activity_audit_events_entity on activity_audit_events(entity_type, entity_id, occurred_at desc);
create index idx_activity_audit_events_occurred_at on activity_audit_events(occurred_at desc);
create index idx_activity_audit_events_actor_user_id on activity_audit_events(actor_user_id);
create index idx_activity_audit_events_action on activity_audit_events(action);
