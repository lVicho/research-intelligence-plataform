alter table research_units
add column visible_in_portal boolean not null default true;

update research_units
set visible_in_portal = false
where id in (7, 8, 102, 103, 104, 106, 107, 108, 112, 113, 118, 119, 121, 122, 124);
