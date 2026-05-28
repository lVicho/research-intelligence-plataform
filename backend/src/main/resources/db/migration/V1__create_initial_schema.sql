create table research_units (
    id bigserial primary key,
    name varchar(255) not null,
    short_name varchar(100),
    type varchar(50) not null,
    parent_id bigint references research_units(id),
    country varchar(120),
    city varchar(120),
    website varchar(500),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_research_units_parent_id on research_units(parent_id);
create index idx_research_units_type on research_units(type);
create index idx_research_units_active on research_units(active);

create table researchers (
    id bigserial primary key,
    full_name varchar(255) not null,
    display_name varchar(255),
    email varchar(320),
    orcid varchar(32),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_researchers_orcid on researchers(orcid) where orcid is not null;
create index idx_researchers_active on researchers(active);
create index idx_researchers_full_name on researchers(full_name);

create table researcher_affiliations (
    id bigserial primary key,
    researcher_id bigint not null references researchers(id) on delete cascade,
    research_unit_id bigint not null references research_units(id),
    role varchar(255),
    affiliation_type varchar(50) not null,
    start_date date,
    end_date date,
    primary_affiliation boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_affiliation_dates check (end_date is null or start_date is null or end_date >= start_date)
);

create index idx_affiliations_researcher_id on researcher_affiliations(researcher_id);
create index idx_affiliations_research_unit_id on researcher_affiliations(research_unit_id);
create index idx_affiliations_current on researcher_affiliations(researcher_id, research_unit_id)
    where end_date is null;
create unique index ux_affiliations_open_primary on researcher_affiliations(researcher_id)
    where primary_affiliation = true and end_date is null;

create table publications (
    id bigserial primary key,
    title varchar(500) not null,
    abstract_text text,
    year integer,
    type varchar(50) not null,
    status varchar(50) not null,
    doi varchar(255),
    source varchar(255),
    url varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_publications_year check (year is null or (year >= 1500 and year <= 2200))
);

create unique index ux_publications_doi on publications(doi) where doi is not null;
create index idx_publications_year on publications(year);
create index idx_publications_type on publications(type);
create index idx_publications_status on publications(status);

create table publication_authors (
    id bigserial primary key,
    publication_id bigint not null references publications(id) on delete cascade,
    researcher_id bigint references researchers(id),
    external_author_name varchar(255),
    external_affiliation varchar(255),
    author_order integer not null,
    corresponding_author boolean not null default false,
    constraint ck_publication_author_identity check (
        (researcher_id is not null and external_author_name is null)
        or (researcher_id is null and external_author_name is not null and length(trim(external_author_name)) > 0)
    ),
    constraint ck_publication_author_order check (author_order > 0)
);

create unique index ux_publication_authors_order on publication_authors(publication_id, author_order);
create index idx_publication_authors_publication_id on publication_authors(publication_id);
create index idx_publication_authors_researcher_id on publication_authors(researcher_id);

create table topics (
    id bigserial primary key,
    name varchar(255) not null,
    normalized_name varchar(255) not null
);

create unique index ux_topics_normalized_name on topics(normalized_name);

create table publication_topics (
    publication_id bigint not null references publications(id) on delete cascade,
    topic_id bigint not null references topics(id) on delete cascade,
    primary key (publication_id, topic_id)
);

create index idx_publication_topics_topic_id on publication_topics(topic_id);
