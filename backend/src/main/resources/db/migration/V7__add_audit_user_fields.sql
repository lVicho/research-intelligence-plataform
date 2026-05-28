alter table research_units
    add column created_by_user_id bigint references users(id),
    add column updated_by_user_id bigint references users(id);

alter table researchers
    add column created_by_user_id bigint references users(id),
    add column updated_by_user_id bigint references users(id);

alter table researcher_affiliations
    add column created_by_user_id bigint references users(id),
    add column updated_by_user_id bigint references users(id);

alter table publications
    add column created_by_user_id bigint references users(id),
    add column updated_by_user_id bigint references users(id);

alter table users
    add column created_by_user_id bigint references users(id),
    add column updated_by_user_id bigint references users(id);

create index idx_research_units_created_by_user_id on research_units(created_by_user_id);
create index idx_research_units_updated_by_user_id on research_units(updated_by_user_id);
create index idx_researchers_created_by_user_id on researchers(created_by_user_id);
create index idx_researchers_updated_by_user_id on researchers(updated_by_user_id);
create index idx_researcher_affiliations_created_by_user_id on researcher_affiliations(created_by_user_id);
create index idx_researcher_affiliations_updated_by_user_id on researcher_affiliations(updated_by_user_id);
create index idx_publications_created_by_user_id on publications(created_by_user_id);
create index idx_publications_updated_by_user_id on publications(updated_by_user_id);
create index idx_users_created_by_user_id on users(created_by_user_id);
create index idx_users_updated_by_user_id on users(updated_by_user_id);
