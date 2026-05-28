with domain_seed (
    domain_index, unit_name, short_name, unit_type, city, website, domain_topic_name, source_prefix, external_affiliation
) as (
    values
        (1, 'Instituto de Astrofisica Computacional',         'IAC',   'INSTITUTE', 'La Laguna', 'https://astrocomp.example.es',   'Astrofisica computacional',          'Astrofisica Computacional',          'Consorcio Iberico de Observacion Profunda'),
        (2, 'Centro de Fisica Cuantica e Informacion',        'CFCI',  'CENTER',    'Madrid',    'https://cuantica.example.es',    'Fisica cuantica',                    'Fisica Cuantica',                    'Quantum Systems Collaboration Network'),
        (3, 'Laboratorio de Fisica Nuclear y Detectores',     'LFND',  'LAB',       'Granada',   'https://nuclear.example.es',     'Fisica nuclear',                     'Fisica Nuclear',                     'Red Euroiberica de Deteccion Nuclear'),
        (4, 'Instituto de Fisica de Particulas y Aceleradores','IFPA', 'INSTITUTE', 'Valencia',  'https://particulas.example.es',  'Fisica de particulas',               'Fisica de Particulas',               'Alianza Mediterranea de Altas Energias'),
        (5, 'Centro de Fotonica y Materiales Cuanticos',      'CFMC',  'CENTER',    'Barcelona', 'https://fotonica.example.es',    'Fotonica y materiales cuanticos',    'Fotonica y Materiales Cuanticos',    'Photonics and Quantum Materials Hub'),
        (6, 'Laboratorio de Plasmas, Fusion y Propulsion',    'LPFP',  'LAB',       'Sevilla',   'https://plasmas.example.es',     'Plasmas y fusion',                   'Plasmas y Fusion',                   'Fusion and Electric Propulsion Forum')
)
insert into research_units (
    id, name, short_name, type, parent_id, country, city, website, active,
    created_at, updated_at, validation_status, validation_comment, validated_at
)
select
    137 + domain_index,
    unit_name,
    short_name,
    unit_type,
    1,
    'Espana',
    city,
    website,
    true,
    now(),
    now(),
    'VALIDATED',
    'Demo seed data validated for public exploration.',
    now()
from domain_seed;

with researcher_seed (
    researcher_id, full_name, email, orcid, domain_index
) as (
    values
        (203, 'Aina Corbera',      'aina.corbera@astrocomp.example.es',    '0000-0002-9201-0203', 1),
        (204, 'Bruno Salvat',      'bruno.salvat@astrocomp.example.es',    '0000-0002-9201-0204', 1),
        (205, 'Clara Niebla',      'clara.niebla@astrocomp.example.es',    '0000-0002-9201-0205', 1),
        (206, 'Dario Valcarcel',   'dario.valcarcel@astrocomp.example.es', '0000-0002-9201-0206', 1),
        (207, 'Elsa Torrens',      'elsa.torrens@astrocomp.example.es',    '0000-0002-9201-0207', 1),
        (208, 'Fabio Aranda',      'fabio.aranda@cuantica.example.es',     '0000-0002-9201-0208', 2),
        (209, 'Gala Montero',      'gala.montero@cuantica.example.es',     '0000-0002-9201-0209', 2),
        (210, 'Hector Muntaner',   'hector.muntaner@cuantica.example.es',  '0000-0002-9201-0210', 2),
        (211, 'Iria Valdes',       'iria.valdes@cuantica.example.es',      '0000-0002-9201-0211', 2),
        (212, 'Jaime Corral',      'jaime.corral@cuantica.example.es',     '0000-0002-9201-0212', 2),
        (213, 'Katia Pizarro',     'katia.pizarro@nuclear.example.es',     '0000-0002-9201-0213', 3),
        (214, 'Leo Campillo',      'leo.campillo@nuclear.example.es',      '0000-0002-9201-0214', 3),
        (215, 'Marta Delicado',    'marta.delicado@nuclear.example.es',    '0000-0002-9201-0215', 3),
        (216, 'Nestor Brea',       'nestor.brea@nuclear.example.es',       '0000-0002-9201-0216', 3),
        (217, 'Olalla Prieto',     'olalla.prieto@nuclear.example.es',     '0000-0002-9201-0217', 3),
        (218, 'Pau Cebrian',       'pau.cebrian@particulas.example.es',    '0000-0002-9201-0218', 4),
        (219, 'Queralt Burgos',    'queralt.burgos@particulas.example.es', '0000-0002-9201-0219', 4),
        (220, 'Ruben Bastida',     'ruben.bastida@particulas.example.es',  '0000-0002-9201-0220', 4),
        (221, 'Sara Valiente',     'sara.valiente@particulas.example.es',  '0000-0002-9201-0221', 4),
        (222, 'Tomas Albor',       'tomas.albor@particulas.example.es',    '0000-0002-9201-0222', 4),
        (223, 'Uxia Figueroa',     'uxia.figueroa@fotonica.example.es',    '0000-0002-9201-0223', 5),
        (224, 'Victor Mencia',     'victor.mencia@fotonica.example.es',    '0000-0002-9201-0224', 5),
        (225, 'Wendy Requena',     'wendy.requena@fotonica.example.es',    '0000-0002-9201-0225', 5),
        (226, 'Xavier Vela',       'xavier.vela@fotonica.example.es',      '0000-0002-9201-0226', 5),
        (227, 'Yaiza Gadea',       'yaiza.gadea@fotonica.example.es',      '0000-0002-9201-0227', 5),
        (228, 'Adrian Lobato',     'adrian.lobato@plasmas.example.es',     '0000-0002-9201-0228', 6),
        (229, 'Blanca Cedres',     'blanca.cedres@plasmas.example.es',     '0000-0002-9201-0229', 6),
        (230, 'Ciro Candela',      'ciro.candela@plasmas.example.es',      '0000-0002-9201-0230', 6),
        (231, 'Dunia Ferreiro',    'dunia.ferreiro@plasmas.example.es',    '0000-0002-9201-0231', 6),
        (232, 'Esteban Bargas',    'esteban.bargas@plasmas.example.es',    '0000-0002-9201-0232', 6)
)
insert into researchers (
    id, full_name, display_name, email, orcid, active,
    created_at, updated_at, validation_status, validation_comment, validated_at
)
select
    researcher_id,
    full_name,
    full_name,
    email,
    orcid,
    true,
    now(),
    now(),
    'VALIDATED',
    'Demo seed data validated for public exploration.',
    now()
from researcher_seed;

insert into researcher_affiliations (
    id, researcher_id, research_unit_id, role, affiliation_type, start_date, end_date, primary_affiliation,
    created_at, updated_at, validation_status, validation_comment, validated_at
)
select
    214 + row_number() over (order by id),
    id,
    case
        when id between 203 and 207 then 138
        when id between 208 and 212 then 139
        when id between 213 and 217 then 140
        when id between 218 and 222 then 141
        when id between 223 and 227 then 142
        else 143
    end,
    'Investigador/a del programa',
    'MEMBER',
    make_date(
        (2017 + ((id - 203) % 7))::integer,
        (1 + ((id - 203) % 11))::integer,
        (1 + ((id - 203) % 20))::integer
    ),
    null,
    true,
    now(),
    now(),
    'VALIDATED',
    'Demo seed data validated for public exploration.',
    now()
from researchers
where id between 203 and 232;

with topic_seed (topic_id, topic_name) as (
    values
        (336, 'Astrofisica computacional'),
        (337, 'Cosmologia observacional'),
        (338, 'Ondas gravitacionales'),
        (339, 'Exoplanetas y habitabilidad'),
        (340, 'Astrofisica estelar'),
        (341, 'Radioastronomia'),
        (342, 'Fisica cuantica'),
        (343, 'Informacion cuantica'),
        (344, 'Simulacion cuantica'),
        (345, 'Optica cuantica'),
        (346, 'Criptografia cuantica'),
        (347, 'Sensores cuanticos'),
        (348, 'Fisica nuclear'),
        (349, 'Estructura nuclear'),
        (350, 'Deteccion de radiacion'),
        (351, 'Imagen nuclear'),
        (352, 'Astrofisica nuclear'),
        (353, 'Seguridad radiologica'),
        (354, 'Fisica de particulas'),
        (355, 'Fenomenologia del boson de Higgs'),
        (356, 'Detectores de altas energias'),
        (357, 'Analisis de colisiones hadronicas'),
        (358, 'Instrumentacion de aceleradores'),
        (359, 'Neutrinos'),
        (360, 'Fotonica y materiales cuanticos'),
        (361, 'Fotonica integrada'),
        (362, 'Materiales topologicos'),
        (363, 'Nanofotonica'),
        (364, 'Superconductividad'),
        (365, 'Espectroscopia ultrarrapida'),
        (366, 'Plasmas y fusion'),
        (367, 'Confinamiento magnetico'),
        (368, 'Diagnostico de plasmas'),
        (369, 'Materiales para fusion'),
        (370, 'Propulsion espacial electrica'),
        (371, 'Fisica de tokamaks')
)
insert into topics (id, name, normalized_name)
select
    topic_id,
    topic_name,
    lower(topic_name)
from topic_seed;

with domain_seed (
    domain_index, domain_topic_name, source_prefix
) as (
    values
        (1, 'Astrofisica computacional',       'Astrofisica Computacional'),
        (2, 'Fisica cuantica',                 'Fisica Cuantica'),
        (3, 'Fisica nuclear',                  'Fisica Nuclear'),
        (4, 'Fisica de particulas',            'Fisica de Particulas'),
        (5, 'Fotonica y materiales cuanticos', 'Fotonica y Materiales Cuanticos'),
        (6, 'Plasmas y fusion',                'Plasmas y Fusion')
),
theme_seed (domain_index, theme_offset, theme_label) as (
    values
        (1, 1, 'Cosmologia observacional'),
        (1, 2, 'Ondas gravitacionales'),
        (1, 3, 'Exoplanetas y habitabilidad'),
        (1, 4, 'Astrofisica estelar'),
        (1, 5, 'Radioastronomia'),
        (2, 1, 'Informacion cuantica'),
        (2, 2, 'Simulacion cuantica'),
        (2, 3, 'Optica cuantica'),
        (2, 4, 'Criptografia cuantica'),
        (2, 5, 'Sensores cuanticos'),
        (3, 1, 'Estructura nuclear'),
        (3, 2, 'Deteccion de radiacion'),
        (3, 3, 'Imagen nuclear'),
        (3, 4, 'Astrofisica nuclear'),
        (3, 5, 'Seguridad radiologica'),
        (4, 1, 'Fenomenologia del boson de Higgs'),
        (4, 2, 'Detectores de altas energias'),
        (4, 3, 'Analisis de colisiones hadronicas'),
        (4, 4, 'Instrumentacion de aceleradores'),
        (4, 5, 'Neutrinos'),
        (5, 1, 'Fotonica integrada'),
        (5, 2, 'Materiales topologicos'),
        (5, 3, 'Nanofotonica'),
        (5, 4, 'Superconductividad'),
        (5, 5, 'Espectroscopia ultrarrapida'),
        (6, 1, 'Confinamiento magnetico'),
        (6, 2, 'Diagnostico de plasmas'),
        (6, 3, 'Materiales para fusion'),
        (6, 4, 'Propulsion espacial electrica'),
        (6, 5, 'Fisica de tokamaks')
),
publication_seed as (
    select
        476 + ((theme_seed.domain_index - 1) * 25) + ((theme_seed.theme_offset - 1) * 5) + series.pub_offset as publication_id,
        theme_seed.domain_index,
        theme_seed.theme_offset,
        series.pub_offset,
        theme_seed.theme_label,
        domain_seed.domain_topic_name,
        domain_seed.source_prefix,
        336 + ((theme_seed.domain_index - 1) * 6) as domain_topic_id,
        336 + ((theme_seed.domain_index - 1) * 6) + theme_seed.theme_offset as theme_topic_id
    from theme_seed
    join domain_seed on domain_seed.domain_index = theme_seed.domain_index
    cross join generate_series(1, 5) as series(pub_offset)
)
insert into publications (
    id, title, abstract_text, year, type, status, doi, source, url,
    venue_id, publisher_id, isbn, issn, language_code,
    created_at, updated_at, validation_status, validation_comment, validated_at
)
select
    publication_id,
    case pub_offset
        when 1 then theme_label || ': analisis observacional multicentro'
        when 2 then 'Modelado reproducible para ' || lower(theme_label)
        when 3 then 'Benchmark instrumental sobre ' || lower(theme_label)
        when 4 then 'Atlas de datos y metodos para ' || lower(theme_label)
        else 'Evaluacion comparada de ' || lower(theme_label) || ' en redes ibericas'
    end,
    'Publicacion demo sobre ' || lower(theme_label)
        || ' dentro del dominio '
        || lower(domain_topic_name)
        || '. Se incorpora para ampliar la cobertura de fisica del portal y reforzar las pruebas de busqueda, filtrado, relaciones tematicas y mapa estrategico.',
    2021 + mod(publication_id + theme_offset, 5),
    case
        when pub_offset = 4 and mod(domain_index, 2) = 0 then 'DATASET'
        when pub_offset = 4 then 'REPORT'
        when pub_offset = 5 and mod(domain_index, 3) = 0 then 'SOFTWARE'
        when pub_offset = 5 then 'CONFERENCE_PAPER'
        else 'ARTICLE'
    end,
    case
        when pub_offset = 2 then 'ACCEPTED'
        when pub_offset = 5 and mod(domain_index, 2) = 1 then 'IN_PRESS'
        else 'PUBLISHED'
    end,
    case
        when pub_offset = 4 and mod(domain_index, 2) = 0 then null
        else '10.9898/physicsdemo.' || publication_id
    end,
    case pub_offset
        when 1 then 'Revista Iberica de ' || source_prefix
        when 2 then 'Cuadernos de ' || source_prefix
        when 3 then 'Instrumentacion en ' || source_prefix
        when 4 then 'Repositorios de ' || source_prefix
        else 'Simposio de ' || source_prefix
    end,
    case
        when pub_offset = 4 and mod(domain_index, 2) = 0 then 'https://demo.example.es/dataset-fisica-' || publication_id
        when pub_offset = 4 then 'https://demo.example.es/informe-fisica-' || publication_id
        when pub_offset = 5 and mod(domain_index, 3) = 0 then 'https://demo.example.es/software-fisica-' || publication_id
        else 'https://doi.org/10.9898/physicsdemo.' || publication_id
    end,
    null,
    null,
    null,
    null,
    'es',
    now(),
    now(),
    'VALIDATED',
    'Demo seed data validated for public exploration.',
    now()
from publication_seed;

with domain_seed (
    domain_index, external_affiliation
) as (
    values
        (1, 'Consorcio Iberico de Observacion Profunda'),
        (2, 'Quantum Systems Collaboration Network'),
        (3, 'Red Euroiberica de Deteccion Nuclear'),
        (4, 'Alianza Mediterranea de Altas Energias'),
        (5, 'Photonics and Quantum Materials Hub'),
        (6, 'Fusion and Electric Propulsion Forum')
),
theme_seed (domain_index, theme_offset) as (
    values
        (1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
        (2, 1), (2, 2), (2, 3), (2, 4), (2, 5),
        (3, 1), (3, 2), (3, 3), (3, 4), (3, 5),
        (4, 1), (4, 2), (4, 3), (4, 4), (4, 5),
        (5, 1), (5, 2), (5, 3), (5, 4), (5, 5),
        (6, 1), (6, 2), (6, 3), (6, 4), (6, 5)
),
publication_seed as (
    select
        476 + ((theme_seed.domain_index - 1) * 25) + ((theme_seed.theme_offset - 1) * 5) + pub_offset as publication_id,
        theme_seed.domain_index,
        theme_seed.theme_offset,
        pub_offset,
        203 + ((theme_seed.domain_index - 1) * 5) as researcher_base,
        domain_seed.external_affiliation
    from theme_seed
    join domain_seed on domain_seed.domain_index = theme_seed.domain_index
    cross join generate_series(1, 5) as publication_series(pub_offset)
)
insert into publication_authors (
    publication_id, researcher_id, external_author_name, external_affiliation, author_order, corresponding_author
)
select
    publication_id,
    researcher_base + mod(theme_offset + pub_offset - 2, 5),
    null,
    null,
    1,
    true
from publication_seed
union all
select
    publication_id,
    researcher_base + mod(theme_offset + pub_offset - 1, 5),
    null,
    null,
    2,
    false
from publication_seed
union all
select
    publication_id,
    null,
    'Colaborador externo fisica ' || publication_id,
    external_affiliation,
    3,
    false
from publication_seed;

with theme_seed (domain_index, theme_offset) as (
    values
        (1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
        (2, 1), (2, 2), (2, 3), (2, 4), (2, 5),
        (3, 1), (3, 2), (3, 3), (3, 4), (3, 5),
        (4, 1), (4, 2), (4, 3), (4, 4), (4, 5),
        (5, 1), (5, 2), (5, 3), (5, 4), (5, 5),
        (6, 1), (6, 2), (6, 3), (6, 4), (6, 5)
),
publication_seed as (
    select
        476 + ((domain_index - 1) * 25) + ((theme_offset - 1) * 5) + pub_offset as publication_id,
        336 + ((domain_index - 1) * 6) as domain_topic_id,
        336 + ((domain_index - 1) * 6) + theme_offset as theme_topic_id
    from theme_seed
    cross join generate_series(1, 5) as publication_series(pub_offset)
)
insert into publication_topics (publication_id, topic_id)
select publication_id, domain_topic_id from publication_seed
union all
select publication_id, theme_topic_id from publication_seed;

select setval('research_units_id_seq', (select max(id) from research_units));
select setval('researchers_id_seq', (select max(id) from researchers));
select setval('researcher_affiliations_id_seq', (select max(id) from researcher_affiliations));
select setval('publications_id_seq', (select max(id) from publications));
select setval('topics_id_seq', (select max(id) from topics));
