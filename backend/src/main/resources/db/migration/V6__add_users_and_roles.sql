create table roles (
    id bigserial primary key,
    code varchar(50) not null,
    label_es varchar(120) not null,
    description_es varchar(500) not null
);

create unique index ux_roles_code on roles(code);

create table users (
    id bigserial primary key,
    email varchar(320) not null,
    display_name varchar(255) not null,
    password_hash varchar(255) not null,
    enabled boolean not null default true,
    researcher_id bigint references researchers(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_users_email on users(lower(email));
create index idx_users_researcher_id on users(researcher_id);
create index idx_users_enabled on users(enabled);

create table user_roles (
    user_id bigint not null references users(id) on delete cascade,
    role_id bigint not null references roles(id) on delete cascade,
    primary key (user_id, role_id)
);

create index idx_user_roles_role_id on user_roles(role_id);

insert into roles (id, code, label_es, description_es) values
    (1, 'PUBLIC_USER', 'Usuario público', 'Acceso de consulta a datos publicados y validados.'),
    (2, 'RESEARCHER', 'Investigador', 'Acceso a funciones privadas vinculadas a un investigador.'),
    (3, 'ADMIN', 'Administrador', 'Administración completa de datos maestros y configuración institucional.'),
    (4, 'VALIDATOR', 'Validador', 'Revisión, validación e ingesta controlada de información institucional.');

select setval('roles_id_seq', (select max(id) from roles));
