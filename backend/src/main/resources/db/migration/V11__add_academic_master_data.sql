create table publication_types (
    id bigserial primary key,
    code varchar(80) not null,
    label_es varchar(255) not null,
    description_es text,
    active boolean not null default true,
    sort_order integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ux_publication_types_code unique (code),
    constraint ck_publication_types_code_not_blank check (length(trim(code)) > 0),
    constraint ck_publication_types_label_es_not_blank check (length(trim(label_es)) > 0)
);

create index idx_publication_types_active_sort_order on publication_types(active, sort_order);

create table publication_statuses (
    id bigserial primary key,
    code varchar(80) not null,
    label_es varchar(255) not null,
    description_es text,
    active boolean not null default true,
    sort_order integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ux_publication_statuses_code unique (code),
    constraint ck_publication_statuses_code_not_blank check (length(trim(code)) > 0),
    constraint ck_publication_statuses_label_es_not_blank check (length(trim(label_es)) > 0)
);

create index idx_publication_statuses_active_sort_order on publication_statuses(active, sort_order);

create table venue_types (
    id bigserial primary key,
    code varchar(80) not null,
    label_es varchar(255) not null,
    description_es text,
    active boolean not null default true,
    sort_order integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ux_venue_types_code unique (code),
    constraint ck_venue_types_code_not_blank check (length(trim(code)) > 0),
    constraint ck_venue_types_label_es_not_blank check (length(trim(label_es)) > 0)
);

create index idx_venue_types_active_sort_order on venue_types(active, sort_order);

create table event_types (
    id bigserial primary key,
    code varchar(80) not null,
    label_es varchar(255) not null,
    description_es text,
    active boolean not null default true,
    sort_order integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ux_event_types_code unique (code),
    constraint ck_event_types_code_not_blank check (length(trim(code)) > 0),
    constraint ck_event_types_label_es_not_blank check (length(trim(label_es)) > 0)
);

create index idx_event_types_active_sort_order on event_types(active, sort_order);

create table event_participation_types (
    id bigserial primary key,
    code varchar(80) not null,
    label_es varchar(255) not null,
    description_es text,
    active boolean not null default true,
    sort_order integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ux_event_participation_types_code unique (code),
    constraint ck_event_participation_types_code_not_blank check (length(trim(code)) > 0),
    constraint ck_event_participation_types_label_es_not_blank check (length(trim(label_es)) > 0)
);

create index idx_event_participation_types_active_sort_order on event_participation_types(active, sort_order);

insert into publication_types (code, label_es, description_es, sort_order) values
    ('JOURNAL_ARTICLE', 'Artículo', 'Artículo publicado o previsto en una revista académica.', 10),
    ('BOOK_CHAPTER', 'Capítulo de libro', 'Capítulo incluido en un libro académico o monografía.', 20),
    ('BOOK', 'Libro', 'Libro o monografía completa.', 30),
    ('CONFERENCE_PROCEEDING', 'Acta de congreso', 'Acta o volumen de contribuciones de un congreso.', 40),
    ('CONFERENCE_PAPER', 'Comunicación en congreso', 'Comunicación presentada en un congreso.', 50),
    ('MANUAL', 'Manual', 'Manual académico, docente o técnico.', 60),
    ('DATASET', 'Dataset', 'Dataset asociado a actividad investigadora.', 70),
    ('SOFTWARE', 'Software', 'Software asociado a actividad investigadora.', 80),
    ('WORKING_PAPER', 'Working paper', 'Trabajo preliminar difundido como working paper.', 90),
    ('THESIS', 'Tesis', 'Tesis académica.', 100),
    ('TECHNICAL_REPORT', 'Informe técnico', 'Informe técnico o institucional.', 110),
    ('PREPRINT', 'Preprint', 'Preprint difundido antes de revisión o publicación formal.', 120),
    ('OTHER', 'Otro', 'Tipo de publicación no clasificado en las categorías anteriores.', 130);

insert into publication_statuses (code, label_es, description_es, sort_order) values
    ('DRAFT', 'Borrador', 'Registro o manuscrito académico en borrador.', 10),
    ('SUBMITTED', 'Enviado', 'Trabajo enviado a evaluación o consideración editorial.', 20),
    ('UNDER_REVIEW', 'En revisión', 'Trabajo en proceso de revisión académica o editorial.', 30),
    ('ACCEPTED', 'Aceptado', 'Trabajo aceptado para publicación o presentación.', 40),
    ('IN_PRESS', 'En prensa', 'Trabajo aceptado pendiente de publicación final.', 50),
    ('PUBLISHED', 'Publicado', 'Trabajo publicado o difundido formalmente.', 60),
    ('REJECTED', 'Rechazado', 'Trabajo rechazado en el proceso académico o editorial.', 70),
    ('WITHDRAWN', 'Retirado', 'Trabajo retirado por sus autores o por la entidad responsable.', 80),
    ('UNKNOWN', 'Desconocido', 'Estado académico no determinado.', 90);

insert into venue_types (code, label_es, description_es, sort_order) values
    ('JOURNAL', 'Revista', 'Revista académica o científica.', 10),
    ('CONFERENCE', 'Congreso', 'Congreso, conferencia o reunión académica.', 20),
    ('BOOK_SERIES', 'Serie de libros', 'Serie editorial de libros o monografías.', 30),
    ('REPOSITORY', 'Repositorio', 'Repositorio institucional, temático o de datos.', 40),
    ('PUBLISHER_PLATFORM', 'Plataforma editorial', 'Plataforma editorial o portal de publicación.', 50),
    ('INSTITUTIONAL_SERIES', 'Serie institucional', 'Serie documental o editorial de una institución.', 60),
    ('OTHER', 'Otro', 'Tipo de venue no clasificado en las categorías anteriores.', 70);

insert into event_types (code, label_es, description_es, sort_order) values
    ('CONFERENCE', 'Congreso', 'Congreso o conferencia académica.', 10),
    ('WORKSHOP', 'Workshop', 'Workshop o taller académico.', 20),
    ('SYMPOSIUM', 'Simposio', 'Simposio académico o científico.', 30),
    ('SEMINAR', 'Seminario', 'Seminario de investigación o formación.', 40),
    ('SUMMER_SCHOOL', 'Escuela de verano', 'Escuela de verano u otro programa intensivo.', 50),
    ('JOURNAL_CLUB', 'Club de lectura', 'Club de lectura o discusión de literatura científica.', 60),
    ('OTHER', 'Otro', 'Tipo de evento no clasificado en las categorías anteriores.', 70);

insert into event_participation_types (code, label_es, description_es, sort_order) values
    ('ORAL_PRESENTATION', 'Ponencia oral', 'Presentación oral en un evento académico.', 10),
    ('POSTER', 'Póster', 'Presentación en formato póster.', 20),
    ('KEYNOTE', 'Conferencia invitada', 'Conferencia invitada o plenaria.', 30),
    ('ROUND_TABLE', 'Mesa redonda', 'Participación en mesa redonda.', 40),
    ('SESSION_CHAIR', 'Moderación de sesión', 'Moderación o presidencia de una sesión.', 50),
    ('SCIENTIFIC_COMMITTEE', 'Comité científico', 'Participación en el comité científico.', 60),
    ('ORGANIZING_COMMITTEE', 'Comité organizador', 'Participación en el comité organizador.', 70),
    ('WORKSHOP_ORGANIZER', 'Organización de workshop', 'Organización de un workshop o taller.', 80),
    ('WORKSHOP_SPEAKER', 'Impartición de workshop', 'Impartición de un workshop o taller.', 90),
    ('ATTENDANCE', 'Asistencia', 'Asistencia al evento sin contribución registrada.', 100),
    ('AWARD', 'Premio', 'Premio o reconocimiento recibido en el evento.', 110),
    ('OTHER', 'Otro', 'Tipo de participación no clasificado en las categorías anteriores.', 120);
