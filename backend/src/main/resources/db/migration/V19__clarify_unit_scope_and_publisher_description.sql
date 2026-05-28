alter table research_units
    add column organization_scope varchar(20) not null default 'INTERNAL',
    add column public_description text,
    add column internal_description text,
    add column responsible_researcher_id bigint references researchers(id),
    add column featured boolean default false,
    add column sort_order integer;

update research_units
set organization_scope = 'EXTERNAL',
    visible_in_portal = false
where visible_in_portal = false;

create index idx_research_units_organization_scope on research_units(organization_scope);
create index idx_research_units_portal_scope on research_units(organization_scope, visible_in_portal, active, validation_status);
create index idx_research_units_responsible_researcher_id on research_units(responsible_researcher_id);
create index idx_research_units_sort_order on research_units(sort_order);

alter table publishers
    add column description text;
