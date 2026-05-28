create table ai_suggestions (
    id bigserial primary key,
    target_type varchar(100) not null,
    target_id bigint,
    suggestion_type varchar(80) not null,
    status varchar(30) not null,
    proposed_data_json text not null,
    explanation text not null,
    evidence_json text,
    model_provider varchar(100) not null,
    model_name varchar(255) not null,
    created_at timestamptz not null,
    created_by_user_id bigint references users(id),
    reviewed_at timestamptz,
    reviewed_by_user_id bigint references users(id),
    review_comment text
);

create index idx_ai_suggestions_target on ai_suggestions(target_type, target_id);
create index idx_ai_suggestions_type on ai_suggestions(suggestion_type);
create index idx_ai_suggestions_status on ai_suggestions(status);
create index idx_ai_suggestions_created_at on ai_suggestions(created_at desc);
create index idx_ai_suggestions_reviewed_by_user_id on ai_suggestions(reviewed_by_user_id);
