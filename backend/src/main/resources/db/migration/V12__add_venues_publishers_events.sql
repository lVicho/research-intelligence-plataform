create table venues (
    id bigserial primary key,
    name varchar(255) not null,
    short_name varchar(100),
    type_code varchar(80) not null,
    issn varchar(32),
    eissn varchar(32),
    isbn varchar(32),
    country varchar(120),
    website varchar(500),
    active boolean not null default true,
    validation_status varchar(50) not null default 'PENDING_VALIDATION',
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by_user_id bigint references users(id),
    updated_by_user_id bigint references users(id),
    constraint ck_venues_name_not_blank check (length(trim(name)) > 0),
    constraint ck_venues_type_code_not_blank check (length(trim(type_code)) > 0)
);

create index idx_venues_type_code on venues(type_code);
create index idx_venues_active on venues(active);
create index idx_venues_validation_status on venues(validation_status, created_at desc);
create index idx_venues_created_by_user_id on venues(created_by_user_id);
create index idx_venues_updated_by_user_id on venues(updated_by_user_id);

create table publishers (
    id bigserial primary key,
    name varchar(255) not null,
    country varchar(120),
    website varchar(500),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_publishers_name_not_blank check (length(trim(name)) > 0)
);

create index idx_publishers_active on publishers(active);
create index idx_publishers_name on publishers(name);

alter table publications
    add column venue_id bigint references venues(id),
    add column publisher_id bigint references publishers(id),
    add column isbn varchar(32),
    add column issn varchar(32),
    add column language_code varchar(16);

create index idx_publications_venue_id on publications(venue_id);
create index idx_publications_publisher_id on publications(publisher_id);
create index idx_publications_language_code on publications(language_code);

create table scientific_events (
    id bigserial primary key,
    name varchar(255) not null,
    edition varchar(120),
    event_type_code varchar(80) not null,
    start_date date,
    end_date date,
    city varchar(120),
    country varchar(120),
    organizer varchar(255),
    website varchar(500),
    venue_id bigint references venues(id),
    validation_status varchar(50) not null default 'PENDING_VALIDATION',
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_scientific_events_name_not_blank check (length(trim(name)) > 0),
    constraint ck_scientific_events_type_code_not_blank check (length(trim(event_type_code)) > 0),
    constraint ck_scientific_events_dates check (end_date is null or start_date is null or end_date >= start_date)
);

create index idx_scientific_events_type_code on scientific_events(event_type_code);
create index idx_scientific_events_dates on scientific_events(start_date, end_date);
create index idx_scientific_events_venue_id on scientific_events(venue_id);
create index idx_scientific_events_validation_status on scientific_events(validation_status, created_at desc);

create table event_participations (
    id bigserial primary key,
    event_id bigint not null references scientific_events(id),
    researcher_id bigint not null references researchers(id),
    research_unit_id bigint references research_units(id),
    participation_type_code varchar(80) not null,
    title varchar(500) not null,
    description text,
    participation_date date,
    related_publication_id bigint references publications(id),
    validation_status varchar(50) not null default 'DRAFT',
    submitted_at timestamptz,
    validated_at timestamptz,
    validation_comment text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_event_participations_type_code_not_blank check (length(trim(participation_type_code)) > 0),
    constraint ck_event_participations_title_not_blank check (length(trim(title)) > 0)
);

create index idx_event_participations_event_id on event_participations(event_id);
create index idx_event_participations_researcher_id on event_participations(researcher_id);
create index idx_event_participations_research_unit_id on event_participations(research_unit_id);
create index idx_event_participations_related_publication_id on event_participations(related_publication_id);
create index idx_event_participations_validation_status on event_participations(validation_status, submitted_at desc, created_at desc);
create index idx_event_participations_participation_date on event_participations(participation_date);

insert into venues (
    id, name, short_name, type_code, issn, eissn, isbn, country, website, active, validation_status, created_at, updated_at
) values
    (1, 'Revista de IA Clínica Local', 'RIACL', 'JOURNAL', '2950-1001', '2950-1002', null, 'España', 'https://demo.example.es/revista-ia-clinica-local', true, 'VALIDATED', now(), now()),
    (2, 'Congreso Ibérico de Datos Biomédicos', 'CIDB', 'CONFERENCE', null, null, null, 'España', 'https://demo.example.es/cidb', true, 'VALIDATED', now(), now()),
    (3, 'Jornadas de Conservación de Grandes Felinos', 'JCGF', 'CONFERENCE', null, null, null, 'España', 'https://felinos.example.es/jornadas', true, 'VALIDATED', now(), now()),
    (4, 'Repositorio Abierto de Salud Digital', 'RASD', 'REPOSITORY', null, null, null, 'España', 'https://datos-salud.example.es', true, 'VALIDATED', now(), now());

insert into publishers (id, name, country, website, active, created_at, updated_at) values
    (1, 'Editorial Ciencia Local', 'España', 'https://editorial-ciencia-local.example.es', true, now(), now()),
    (2, 'Red Ibérica de Datos Biomédicos', 'España', 'https://datos-biomedicos.example.es', true, now(), now()),
    (3, 'Fundación Conservación Felina', 'España', 'https://felinos.example.es/fundacion', true, now(), now());

update publications
set venue_id = 1,
    publisher_id = 1,
    issn = '2950-1001',
    language_code = 'es',
    updated_at = now()
where source = 'Revista de IA Clinica Local'
   or source = 'Revista de Sistemas de IA Clinica'
   or source = 'Revista de Salud Digital'
   or source = 'IA Clinica Responsable';

update publications
set venue_id = 2,
    publisher_id = 2,
    language_code = 'es',
    updated_at = now()
where source like 'Congreso%'
   or source like 'Simposio%'
   or source = 'Datos Biomedicos Explicables';

update publications
set venue_id = 3,
    publisher_id = 3,
    language_code = 'es',
    updated_at = now()
where source in (
    'Conservacion de Grandes Felinos',
    'Ecologia de Carnivoros',
    'Informes de Biodiversidad Iberica',
    'Revista de Comportamiento Animal',
    'Congreso de Biodiversidad y Paisaje',
    'Cuadernos de Conservacion Social'
);

update publications
set venue_id = 4,
    publisher_id = 1,
    language_code = 'es',
    updated_at = now()
where source in (
    'Herramientas Abiertas de Investigacion',
    'Herramientas de Salud Urbana',
    'Gestion de Datos en Salud',
    'Red Hospitalaria de Datos Abiertos'
);

insert into scientific_events (
    id, name, edition, event_type_code, start_date, end_date, city, country, organizer, website, venue_id, validation_status, created_at, updated_at
) values
    (1, 'Workshop de IA Local en Hospitales 2025', '2025', 'WORKSHOP', '2025-10-15', '2025-10-16', 'Madrid', 'España', 'Hospital Universitario Central y Universidad Central Iberica', 'https://demo.example.es/workshop-ia-local-hospitales-2025', null, 'VALIDATED', now(), now()),
    (2, 'Congreso Ibérico de Datos Biomédicos 2026', '2026', 'CONFERENCE', '2026-04-21', '2026-04-23', 'Madrid', 'España', 'Red Ibérica de Datos Biomédicos', 'https://demo.example.es/cidb-2026', 2, 'VALIDATED', now(), now()),
    (3, 'Jornadas de Conservación de Grandes Felinos 2026', '2026', 'CONFERENCE', '2026-06-12', '2026-06-13', 'Sevilla', 'España', 'Centro de Conservacion de Grandes Felinos', 'https://felinos.example.es/jornadas-2026', 3, 'VALIDATED', now(), now());

insert into event_participations (
    id, event_id, researcher_id, research_unit_id, participation_type_code, title, description, participation_date,
    related_publication_id, validation_status, submitted_at, validated_at, validation_comment, created_at, updated_at
) values
    (1, 1, 1, 3, 'KEYNOTE', 'IA local para comites clinicos con evidencia recuperada', 'Conferencia invitada sobre modelos locales, privacidad y citas trazables en hospitales.', '2025-10-15', 201, 'VALIDATED', now(), now(), 'Demo seed data validated for public exploration.', now(), now()),
    (2, 2, 106, 6, 'ORAL_PRESENTATION', 'Grafos explicables para cohortes genomicas', 'Presentacion oral sobre cohortes explicables y grafos de conocimiento clinico-genomico.', '2026-04-22', 214, 'VALIDATED', now(), now(), 'Demo seed data validated for public exploration.', now(), now()),
    (3, 3, 110, 103, 'ORAL_PRESENTATION', 'Corredores ecologicos para panteras en paisajes fragmentados', 'Comunicacion sobre conectividad de habitat y conservacion de grandes felinos.', '2026-06-12', 222, 'VALIDATED', now(), now(), 'Demo seed data validated for public exploration.', now(), now()),
    (4, 3, 118, 103, 'POSTER', 'Camaras trampa para estimar actividad nocturna de felinos salvajes', 'Poster metodologico sobre seguimiento con camaras trampa y patrones de actividad.', '2026-06-13', 223, 'VALIDATED', now(), now(), 'Demo seed data validated for public exploration.', now(), now());

select setval('venues_id_seq', (select max(id) from venues));
select setval('publishers_id_seq', (select max(id) from publishers));
select setval('scientific_events_id_seq', (select max(id) from scientific_events));
select setval('event_participations_id_seq', (select max(id) from event_participations));
