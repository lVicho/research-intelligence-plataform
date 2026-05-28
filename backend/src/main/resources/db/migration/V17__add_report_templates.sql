create table report_templates (
    id bigserial primary key,
    name varchar(255) not null,
    description varchar(1000),
    target_type varchar(50) not null,
    sections_json text not null,
    default_year_from integer,
    default_year_to integer,
    output_format varchar(50) not null default 'MARKDOWN',
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by_user_id bigint references users(id),
    updated_by_user_id bigint references users(id),
    constraint ck_report_template_default_years check (
        (default_year_from is null or (default_year_from >= 1500 and default_year_from <= 2200))
        and (default_year_to is null or (default_year_to >= 1500 and default_year_to <= 2200))
        and (default_year_from is null or default_year_to is null or default_year_from <= default_year_to)
    )
);

create index idx_report_templates_target_type on report_templates(target_type);
create index idx_report_templates_active on report_templates(active);

insert into report_templates (
    name,
    description,
    target_type,
    sections_json,
    default_year_from,
    default_year_to,
    output_format,
    active,
    created_at,
    updated_at
) values
    (
        'Informe anual de unidad',
        'Sintesis anual de actividad de una unidad, con produccion, evolucion, temas, investigadores y evidencia citada.',
        'RESEARCH_UNIT',
        '["EXECUTIVE_SUMMARY","PUBLICATION_OVERVIEW","YEARLY_EVOLUTION","TOP_TOPICS","LINKED_RESEARCHERS","REPRESENTATIVE_PUBLICATIONS","COLLABORATIONS","LIMITATIONS","CITED_EVIDENCE"]',
        null,
        null,
        'MARKDOWN',
        true,
        now(),
        now()
    ),
    (
        'Informe de investigador',
        'Perfil interno de actividad investigadora con publicaciones representativas, temas, colaboraciones y limitaciones.',
        'RESEARCHER',
        '["EXECUTIVE_SUMMARY","PUBLICATION_OVERVIEW","TOP_TOPICS","COLLABORATIONS","REPRESENTATIVE_PUBLICATIONS","LIMITATIONS","CITED_EVIDENCE"]',
        null,
        null,
        'MARKDOWN',
        true,
        now(),
        now()
    ),
    (
        'Informe de linea tematica',
        'Panorama de un tema o linea de investigacion con unidades, investigadores, evolucion y oportunidades.',
        'TOPIC',
        '["EXECUTIVE_SUMMARY","PUBLICATION_OVERVIEW","YEARLY_EVOLUTION","TOP_TOPICS","LINKED_RESEARCHERS","LINKED_UNITS","OPPORTUNITIES","LIMITATIONS","CITED_EVIDENCE"]',
        null,
        null,
        'MARKDOWN',
        true,
        now(),
        now()
    ),
    (
        'Informe de actividad institucional',
        'Lectura estrategica de actividad institucional a partir de una linea agregada y su evidencia documental.',
        'STRATEGIC_LINE',
        '["EXECUTIVE_SUMMARY","PUBLICATION_OVERVIEW","YEARLY_EVOLUTION","TOP_TOPICS","LINKED_RESEARCHERS","LINKED_UNITS","REPRESENTATIVE_PUBLICATIONS","OPPORTUNITIES","LIMITATIONS","CITED_EVIDENCE"]',
        null,
        null,
        'MARKDOWN',
        true,
        now(),
        now()
    ),
    (
        'Informe de calidad de datos',
        'Revision interna orientada a calidad, validacion, limitaciones y trazabilidad de la evidencia disponible.',
        'RESEARCH_UNIT',
        '["EXECUTIVE_SUMMARY","DATA_QUALITY","VALIDATION_STATUS","PUBLICATION_OVERVIEW","LIMITATIONS","CITED_EVIDENCE"]',
        null,
        null,
        'MARKDOWN',
        true,
        now(),
        now()
    ),
    (
        'Informe para comite de direccion',
        'Resumen ejecutivo para decision institucional con foco en oportunidades, colaboraciones y evidencia citada.',
        'STRATEGIC_LINE',
        '["EXECUTIVE_SUMMARY","PUBLICATION_OVERVIEW","TOP_TOPICS","COLLABORATIONS","OPPORTUNITIES","LIMITATIONS","CITED_EVIDENCE"]',
        null,
        null,
        'MARKDOWN',
        true,
        now(),
        now()
    );
