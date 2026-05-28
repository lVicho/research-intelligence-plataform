with domain_seed (
    domain_index, unit_name, short_name, unit_type, city, website, domain_topic_name, source_prefix
) as (
    values
        (1,  'Instituto de Bioinformatica Aplicada',                 'IBA',  'INSTITUTE', 'Barcelona', 'https://bioinfo.example.es',      'Bioinformatica aplicada',         'Bioinformatica Aplicada'),
        (2,  'Centro de Agricultura de Precision',                   'CAP',  'CENTER',    'Lleida',    'https://agroprecision.example.es', 'Agricultura de precision',        'Agricultura de Precision'),
        (3,  'Observatorio de Turismo Sostenible',                   'OTS',  'INSTITUTE', 'Palma',     'https://turismo.example.es',      'Turismo sostenible',              'Turismo Sostenible'),
        (4,  'Instituto de Derecho y Tecnologia',                    'IDT',  'INSTITUTE', 'Madrid',    'https://derechotech.example.es',  'Derecho y tecnologia',            'Derecho y Tecnologia'),
        (5,  'Laboratorio de Manufactura Aditiva',                   'LMA',  'LAB',       'Bilbao',    'https://aditiva.example.es',      'Manufactura aditiva',             'Manufactura Aditiva'),
        (6,  'Centro de Ciencias del Deporte y Rendimiento',         'CCDR', 'CENTER',    'Valencia',  'https://deporte.example.es',      'Ciencias del deporte',            'Ciencias del Deporte'),
        (7,  'Instituto de Psicologia Social y Comunitaria',         'IPSC', 'INSTITUTE', 'Sevilla',   'https://psicologia.example.es',   'Psicologia social',               'Psicologia Social'),
        (8,  'Laboratorio de Musica Digital y Sonido',               'LMDS', 'LAB',       'Granada',   'https://musica.example.es',       'Musica digital',                  'Musica Digital'),
        (9,  'Centro de Logistica Portuaria Inteligente',            'CLPI', 'CENTER',    'Vigo',      'https://logistica.example.es',    'Logistica portuaria',             'Logistica Portuaria'),
        (10, 'Instituto de Quimica Verde',                           'IQV',  'INSTITUTE', 'Tarragona', 'https://quimicaverde.example.es', 'Quimica verde',                   'Quimica Verde'),
        (11, 'Observatorio de Meteorologia Extrema',                 'OME',  'INSTITUTE', 'Santander', 'https://meteo.example.es',        'Meteorologia extrema',            'Meteorologia Extrema'),
        (12, 'Centro de Vivienda y Ciudad Inclusiva',                'CVCI', 'CENTER',    'Malaga',    'https://vivienda.example.es',     'Vivienda y ciudad inclusiva',     'Vivienda y Ciudad'),
        (13, 'Instituto de Historia Urbana y Cartografia',           'IHUC', 'INSTITUTE', 'Zaragoza',  'https://historiaurbana.example.es','Historia urbana',                'Historia Urbana')
)
insert into research_units (
    id, name, short_name, type, parent_id, country, city, website, active,
    created_at, updated_at, validation_status, validation_comment, validated_at
)
select
    124 + domain_index,
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
        (151, 'Alicia Navarro',   'alicia.navarro@bioinfo.example.es',        '0000-0002-8101-0151', 1),
        (152, 'Bruno Ledesma',    'bruno.ledesma@bioinfo.example.es',         '0000-0002-8101-0152', 1),
        (153, 'Carla Pons',       'carla.pons@bioinfo.example.es',            '0000-0002-8101-0153', 1),
        (154, 'Diego Merino',     'diego.merino@bioinfo.example.es',          '0000-0002-8101-0154', 1),
        (155, 'Elena Pastor',     'elena.pastor@agroprecision.example.es',    '0000-0002-8101-0155', 2),
        (156, 'Fabian Romero',    'fabian.romero@agroprecision.example.es',   '0000-0002-8101-0156', 2),
        (157, 'Gemma Vidal',      'gemma.vidal@agroprecision.example.es',     '0000-0002-8101-0157', 2),
        (158, 'Hector Solis',     'hector.solis@agroprecision.example.es',    '0000-0002-8101-0158', 2),
        (159, 'Ines Lozano',      'ines.lozano@turismo.example.es',           '0000-0002-8101-0159', 3),
        (160, 'Javier Casas',     'javier.casas@turismo.example.es',          '0000-0002-8101-0160', 3),
        (161, 'Karla Medina',     'karla.medina@turismo.example.es',          '0000-0002-8101-0161', 3),
        (162, 'Luis Navas',       'luis.navas@turismo.example.es',            '0000-0002-8101-0162', 3),
        (163, 'Marta Pardo',      'marta.pardo@derechotech.example.es',       '0000-0002-8101-0163', 4),
        (164, 'Nicolas Ibanez',   'nicolas.ibanez@derechotech.example.es',    '0000-0002-8101-0164', 4),
        (165, 'Olga Rey',         'olga.rey@derechotech.example.es',          '0000-0002-8101-0165', 4),
        (166, 'Pablo Cuesta',     'pablo.cuesta@derechotech.example.es',      '0000-0002-8101-0166', 4),
        (167, 'Queralt Moya',     'queralt.moya@aditiva.example.es',          '0000-0002-8101-0167', 5),
        (168, 'Raul Nieto',       'raul.nieto@aditiva.example.es',            '0000-0002-8101-0168', 5),
        (169, 'Silvia Duran',     'silvia.duran@aditiva.example.es',          '0000-0002-8101-0169', 5),
        (170, 'Tomas Belmonte',   'tomas.belmonte@aditiva.example.es',        '0000-0002-8101-0170', 5),
        (171, 'Valeria Cano',     'valeria.cano@deporte.example.es',          '0000-0002-8101-0171', 6),
        (172, 'Wenceslao Prieto', 'wenceslao.prieto@deporte.example.es',      '0000-0002-8101-0172', 6),
        (173, 'Xenia Ramos',      'xenia.ramos@deporte.example.es',           '0000-0002-8101-0173', 6),
        (174, 'Yago Ferrer',      'yago.ferrer@deporte.example.es',           '0000-0002-8101-0174', 6),
        (175, 'Aitana Mendez',    'aitana.mendez@psicologia.example.es',      '0000-0002-8101-0175', 7),
        (176, 'Borja Pina',       'borja.pina@psicologia.example.es',         '0000-0002-8101-0176', 7),
        (177, 'Claudia Serra',    'claudia.serra@psicologia.example.es',      '0000-0002-8101-0177', 7),
        (178, 'Dario Molina',     'dario.molina@psicologia.example.es',       '0000-0002-8101-0178', 7),
        (179, 'Eva Robles',       'eva.robles@musica.example.es',             '0000-0002-8101-0179', 8),
        (180, 'Gonzalo Vera',     'gonzalo.vera@musica.example.es',           '0000-0002-8101-0180', 8),
        (181, 'Helena Cid',       'helena.cid@musica.example.es',             '0000-0002-8101-0181', 8),
        (182, 'Ivan Rico',        'ivan.rico@musica.example.es',              '0000-0002-8101-0182', 8),
        (183, 'Jimena Sanz',      'jimena.sanz@logistica.example.es',         '0000-0002-8101-0183', 9),
        (184, 'Kevin Lago',       'kevin.lago@logistica.example.es',          '0000-0002-8101-0184', 9),
        (185, 'Lara Taboada',     'lara.taboada@logistica.example.es',        '0000-0002-8101-0185', 9),
        (186, 'Manuel Souto',     'manuel.souto@logistica.example.es',        '0000-0002-8101-0186', 9),
        (187, 'Noelia Bravo',     'noelia.bravo@quimicaverde.example.es',     '0000-0002-8101-0187', 10),
        (188, 'Oscar Miro',       'oscar.miro@quimicaverde.example.es',       '0000-0002-8101-0188', 10),
        (189, 'Patricia Lerma',   'patricia.lerma@quimicaverde.example.es',   '0000-0002-8101-0189', 10),
        (190, 'Ruben Cervera',    'ruben.cervera@quimicaverde.example.es',    '0000-0002-8101-0190', 10),
        (191, 'Sara Noriega',     'sara.noriega@meteo.example.es',            '0000-0002-8101-0191', 11),
        (192, 'Teo Llorente',     'teo.llorente@meteo.example.es',            '0000-0002-8101-0192', 11),
        (193, 'Uxia Freire',      'uxia.freire@meteo.example.es',             '0000-0002-8101-0193', 11),
        (194, 'Victor Calvo',     'victor.calvo@meteo.example.es',            '0000-0002-8101-0194', 11),
        (195, 'Aroa Gil',         'aroa.gil@vivienda.example.es',             '0000-0002-8101-0195', 12),
        (196, 'Biel Crespo',      'biel.crespo@vivienda.example.es',          '0000-0002-8101-0196', 12),
        (197, 'Celia Otero',      'celia.otero@vivienda.example.es',          '0000-0002-8101-0197', 12),
        (198, 'David Prat',       'david.prat@vivienda.example.es',           '0000-0002-8101-0198', 12),
        (199, 'Erika Plaza',      'erika.plaza@historiaurbana.example.es',    '0000-0002-8101-0199', 13),
        (200, 'Fermin Alcazar',   'fermin.alcazar@historiaurbana.example.es', '0000-0002-8101-0200', 13),
        (201, 'Gloria Pueyo',     'gloria.pueyo@historiaurbana.example.es',   '0000-0002-8101-0201', 13),
        (202, 'Hugo Plaza',       'hugo.plaza@historiaurbana.example.es',     '0000-0002-8101-0202', 13)
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
    162 + row_number() over (order by id),
    id,
    case
        when id between 151 and 154 then 125
        when id between 155 and 158 then 126
        when id between 159 and 162 then 127
        when id between 163 and 166 then 128
        when id between 167 and 170 then 129
        when id between 171 and 174 then 130
        when id between 175 and 178 then 131
        when id between 179 and 182 then 132
        when id between 183 and 186 then 133
        when id between 187 and 190 then 134
        when id between 191 and 194 then 135
        when id between 195 and 198 then 136
        else 137
    end,
    'Investigador/a del programa',
    'MEMBER',
    make_date(
        (2018 + ((id - 151) % 6))::integer,
        (1 + ((id - 151) % 11))::integer,
        (1 + ((id - 151) % 20))::integer
    ),
    null,
    true,
    now(),
    now(),
    'VALIDATED',
    'Demo seed data validated for public exploration.',
    now()
from researchers
where id between 151 and 202;

with topic_seed (topic_id, topic_name) as (
    values
        (271, 'Bioinformatica aplicada'),
        (272, 'Bioinformatica clinica'),
        (273, 'Metagenomica ambiental'),
        (274, 'Transcriptomica espacial'),
        (275, 'Proteomica computacional'),
        (276, 'Agricultura de precision'),
        (277, 'Sensores de suelo'),
        (278, 'Riego inteligente'),
        (279, 'Vision artificial agraria'),
        (280, 'Fenotipado de cultivos'),
        (281, 'Turismo sostenible'),
        (282, 'Capacidad de carga turistica'),
        (283, 'Movilidad turistica'),
        (284, 'Patrimonio y turismo'),
        (285, 'Analitica de destinos'),
        (286, 'Derecho y tecnologia'),
        (287, 'Derecho digital'),
        (288, 'Identidad electronica'),
        (289, 'Regulacion algoritmica'),
        (290, 'Justicia digital'),
        (291, 'Manufactura aditiva'),
        (292, 'Impresion 3D metalica'),
        (293, 'Control de calidad aditivo'),
        (294, 'Materiales compuestos impresos'),
        (295, 'Gemelos de fabrica'),
        (296, 'Ciencias del deporte'),
        (297, 'Rendimiento deportivo'),
        (298, 'Biomecanica'),
        (299, 'Analitica del entrenamiento'),
        (300, 'Prevencion de lesiones'),
        (301, 'Psicologia social'),
        (302, 'Bienestar laboral'),
        (303, 'Cohesion comunitaria'),
        (304, 'Desinformacion social'),
        (305, 'Salud mental universitaria'),
        (306, 'Musica digital'),
        (307, 'Audio inmersivo'),
        (308, 'Analisis musical computacional'),
        (309, 'Preservacion sonora'),
        (310, 'Interfaces musicales'),
        (311, 'Logistica portuaria'),
        (312, 'Cadena de suministro maritima'),
        (313, 'Prediccion de demanda portuaria'),
        (314, 'Emisiones portuarias'),
        (315, 'Operaciones intermodales'),
        (316, 'Quimica verde'),
        (317, 'Catalisis sostenible'),
        (318, 'Valorizacion de residuos'),
        (319, 'Solventes biobasados'),
        (320, 'Sintesis de bajo consumo'),
        (321, 'Meteorologia extrema'),
        (322, 'Inundaciones repentinas'),
        (323, 'Prediccion convectiva'),
        (324, 'Calidad del aire urbano'),
        (325, 'Riesgo por tormentas'),
        (326, 'Vivienda y ciudad inclusiva'),
        (327, 'Vivienda asequible'),
        (328, 'Regeneracion de barrios'),
        (329, 'Accesibilidad urbana'),
        (330, 'Pobreza energetica'),
        (331, 'Historia urbana'),
        (332, 'Cartografia historica'),
        (333, 'Crecimiento metropolitano'),
        (334, 'Archivos municipales'),
        (335, 'Morfologia urbana')
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
        (1,  'Bioinformatica aplicada',     'Bioinformatica Aplicada'),
        (2,  'Agricultura de precision',    'Agricultura de Precision'),
        (3,  'Turismo sostenible',          'Turismo Sostenible'),
        (4,  'Derecho y tecnologia',        'Derecho y Tecnologia'),
        (5,  'Manufactura aditiva',         'Manufactura Aditiva'),
        (6,  'Ciencias del deporte',        'Ciencias del Deporte'),
        (7,  'Psicologia social',           'Psicologia Social'),
        (8,  'Musica digital',              'Musica Digital'),
        (9,  'Logistica portuaria',         'Logistica Portuaria'),
        (10, 'Quimica verde',               'Quimica Verde'),
        (11, 'Meteorologia extrema',        'Meteorologia Extrema'),
        (12, 'Vivienda y ciudad inclusiva', 'Vivienda y Ciudad'),
        (13, 'Historia urbana',             'Historia Urbana')
),
theme_seed (domain_index, theme_offset, theme_label) as (
    values
        (1, 1, 'Bioinformatica clinica'),
        (1, 2, 'Metagenomica ambiental'),
        (1, 3, 'Transcriptomica espacial'),
        (1, 4, 'Proteomica computacional'),
        (2, 1, 'Sensores de suelo'),
        (2, 2, 'Riego inteligente'),
        (2, 3, 'Vision artificial agraria'),
        (2, 4, 'Fenotipado de cultivos'),
        (3, 1, 'Capacidad de carga turistica'),
        (3, 2, 'Movilidad turistica'),
        (3, 3, 'Patrimonio y turismo'),
        (3, 4, 'Analitica de destinos'),
        (4, 1, 'Derecho digital'),
        (4, 2, 'Identidad electronica'),
        (4, 3, 'Regulacion algoritmica'),
        (4, 4, 'Justicia digital'),
        (5, 1, 'Impresion 3D metalica'),
        (5, 2, 'Control de calidad aditivo'),
        (5, 3, 'Materiales compuestos impresos'),
        (5, 4, 'Gemelos de fabrica'),
        (6, 1, 'Rendimiento deportivo'),
        (6, 2, 'Biomecanica'),
        (6, 3, 'Analitica del entrenamiento'),
        (6, 4, 'Prevencion de lesiones'),
        (7, 1, 'Bienestar laboral'),
        (7, 2, 'Cohesion comunitaria'),
        (7, 3, 'Desinformacion social'),
        (7, 4, 'Salud mental universitaria'),
        (8, 1, 'Audio inmersivo'),
        (8, 2, 'Analisis musical computacional'),
        (8, 3, 'Preservacion sonora'),
        (8, 4, 'Interfaces musicales'),
        (9, 1, 'Cadena de suministro maritima'),
        (9, 2, 'Prediccion de demanda portuaria'),
        (9, 3, 'Emisiones portuarias'),
        (9, 4, 'Operaciones intermodales'),
        (10, 1, 'Catalisis sostenible'),
        (10, 2, 'Valorizacion de residuos'),
        (10, 3, 'Solventes biobasados'),
        (10, 4, 'Sintesis de bajo consumo'),
        (11, 1, 'Inundaciones repentinas'),
        (11, 2, 'Prediccion convectiva'),
        (11, 3, 'Calidad del aire urbano'),
        (11, 4, 'Riesgo por tormentas'),
        (12, 1, 'Vivienda asequible'),
        (12, 2, 'Regeneracion de barrios'),
        (12, 3, 'Accesibilidad urbana'),
        (12, 4, 'Pobreza energetica'),
        (13, 1, 'Cartografia historica'),
        (13, 2, 'Crecimiento metropolitano'),
        (13, 3, 'Archivos municipales'),
        (13, 4, 'Morfologia urbana')
),
publication_seed as (
    select
        268 + ((theme_seed.domain_index - 1) * 16) + ((theme_seed.theme_offset - 1) * 4) + series.pub_offset as publication_id,
        theme_seed.domain_index,
        theme_seed.theme_offset,
        series.pub_offset,
        theme_seed.theme_label,
        domain_seed.domain_topic_name,
        domain_seed.source_prefix,
        271 + ((theme_seed.domain_index - 1) * 5) as domain_topic_id,
        271 + ((theme_seed.domain_index - 1) * 5) + theme_seed.theme_offset as theme_topic_id
    from theme_seed
    join domain_seed on domain_seed.domain_index = theme_seed.domain_index
    cross join generate_series(1, 4) as series(pub_offset)
)
insert into publications (
    id, title, abstract_text, year, type, status, doi, source, url,
    venue_id, publisher_id, isbn, issn, language_code,
    created_at, updated_at, validation_status, validation_comment, validated_at
)
select
    publication_id,
    case pub_offset
        when 1 then theme_label || ': estudio aplicado con datos comparables'
        when 2 then 'Modelos reproducibles para ' || lower(theme_label)
        when 3 then 'Guia metodologica sobre ' || lower(theme_label)
        else 'Evaluacion comparada de ' || lower(theme_label)
    end,
    'Publicacion demo sobre ' || lower(theme_label)
        || ' dentro del dominio '
        || lower(domain_topic_name)
        || '. Se incorpora para ampliar la variedad tematica del portal y mejorar las pruebas de busqueda, relaciones y mapas estrategicos.',
    2023 + mod(publication_id + theme_offset, 4),
    case
        when pub_offset = 3 and mod(domain_index, 3) = 0 then 'DATASET'
        when pub_offset = 3 then 'REPORT'
        when pub_offset = 4 and mod(domain_index, 4) = 0 then 'SOFTWARE'
        when pub_offset = 4 then 'CONFERENCE_PAPER'
        else 'ARTICLE'
    end,
    case
        when pub_offset = 2 then 'ACCEPTED'
        when pub_offset = 4 and mod(domain_index, 4) = 0 then 'IN_PRESS'
        else 'PUBLISHED'
    end,
    case
        when pub_offset = 3 and mod(domain_index, 3) = 0 then null
        else '10.8888/demo.' || publication_id
    end,
    case pub_offset
        when 1 then 'Revista de ' || source_prefix
        when 2 then 'Cuadernos de ' || source_prefix
        when 3 then 'Informes de ' || source_prefix
        else 'Congreso Iberico de ' || source_prefix
    end,
    case
        when pub_offset = 3 and mod(domain_index, 3) = 0 then 'https://demo.example.es/dataset-' || publication_id
        when pub_offset = 4 and mod(domain_index, 4) = 0 then 'https://demo.example.es/software-' || publication_id
        when pub_offset = 3 then 'https://demo.example.es/informe-' || publication_id
        else 'https://doi.org/10.8888/demo.' || publication_id
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

with theme_seed (domain_index, theme_offset, theme_label) as (
    values
        (1, 1, 'Bioinformatica clinica'),
        (1, 2, 'Metagenomica ambiental'),
        (1, 3, 'Transcriptomica espacial'),
        (1, 4, 'Proteomica computacional'),
        (2, 1, 'Sensores de suelo'),
        (2, 2, 'Riego inteligente'),
        (2, 3, 'Vision artificial agraria'),
        (2, 4, 'Fenotipado de cultivos'),
        (3, 1, 'Capacidad de carga turistica'),
        (3, 2, 'Movilidad turistica'),
        (3, 3, 'Patrimonio y turismo'),
        (3, 4, 'Analitica de destinos'),
        (4, 1, 'Derecho digital'),
        (4, 2, 'Identidad electronica'),
        (4, 3, 'Regulacion algoritmica'),
        (4, 4, 'Justicia digital'),
        (5, 1, 'Impresion 3D metalica'),
        (5, 2, 'Control de calidad aditivo'),
        (5, 3, 'Materiales compuestos impresos'),
        (5, 4, 'Gemelos de fabrica'),
        (6, 1, 'Rendimiento deportivo'),
        (6, 2, 'Biomecanica'),
        (6, 3, 'Analitica del entrenamiento'),
        (6, 4, 'Prevencion de lesiones'),
        (7, 1, 'Bienestar laboral'),
        (7, 2, 'Cohesion comunitaria'),
        (7, 3, 'Desinformacion social'),
        (7, 4, 'Salud mental universitaria'),
        (8, 1, 'Audio inmersivo'),
        (8, 2, 'Analisis musical computacional'),
        (8, 3, 'Preservacion sonora'),
        (8, 4, 'Interfaces musicales'),
        (9, 1, 'Cadena de suministro maritima'),
        (9, 2, 'Prediccion de demanda portuaria'),
        (9, 3, 'Emisiones portuarias'),
        (9, 4, 'Operaciones intermodales'),
        (10, 1, 'Catalisis sostenible'),
        (10, 2, 'Valorizacion de residuos'),
        (10, 3, 'Solventes biobasados'),
        (10, 4, 'Sintesis de bajo consumo'),
        (11, 1, 'Inundaciones repentinas'),
        (11, 2, 'Prediccion convectiva'),
        (11, 3, 'Calidad del aire urbano'),
        (11, 4, 'Riesgo por tormentas'),
        (12, 1, 'Vivienda asequible'),
        (12, 2, 'Regeneracion de barrios'),
        (12, 3, 'Accesibilidad urbana'),
        (12, 4, 'Pobreza energetica'),
        (13, 1, 'Cartografia historica'),
        (13, 2, 'Crecimiento metropolitano'),
        (13, 3, 'Archivos municipales'),
        (13, 4, 'Morfologia urbana')
),
publication_seed as (
    select
        268 + ((domain_index - 1) * 16) + ((theme_offset - 1) * 4) + pub_offset as publication_id,
        theme_label,
        pub_offset,
        151 + ((domain_index - 1) * 4) as researcher_base,
        271 + ((domain_index - 1) * 5) as domain_topic_id,
        271 + ((domain_index - 1) * 5) + theme_offset as theme_topic_id,
        case
            when domain_index = 1 then 'Red de Bioinformatica Clinica'
            when domain_index = 2 then 'Consorcio de Cultivos Inteligentes'
            when domain_index = 3 then 'Laboratorio de Destinos Sostenibles'
            when domain_index = 4 then 'Observatorio Juridico Digital'
            when domain_index = 5 then 'Plataforma de Fabricacion Avanzada'
            when domain_index = 6 then 'Foro Iberico de Rendimiento Deportivo'
            when domain_index = 7 then 'Red Comunitaria de Psicologia Aplicada'
            when domain_index = 8 then 'Archivo Sonoro Experimental'
            when domain_index = 9 then 'Alianza de Puertos Inteligentes'
            when domain_index = 10 then 'Red de Sintesis Sostenible'
            when domain_index = 11 then 'Centro Atlantico de Tormentas'
            when domain_index = 12 then 'Laboratorio de Ciudad Inclusiva'
            else 'Archivo Metropolitano Comparado'
        end as external_affiliation
    from theme_seed
    cross join generate_series(1, 4) as publication_series(pub_offset)
)
insert into publication_authors (
    publication_id, researcher_id, external_author_name, external_affiliation, author_order, corresponding_author
)
select publication_id, researcher_base + mod(pub_offset - 1, 4), null, null, 1, true
from publication_seed
union all
select publication_id, researcher_base + mod(pub_offset, 4), null, null, 2, false
from publication_seed
union all
select publication_id, null, 'Colaborador externo ' || publication_id, external_affiliation, 3, false
from publication_seed;

with theme_seed (domain_index, theme_offset) as (
    values
        (1, 1), (1, 2), (1, 3), (1, 4),
        (2, 1), (2, 2), (2, 3), (2, 4),
        (3, 1), (3, 2), (3, 3), (3, 4),
        (4, 1), (4, 2), (4, 3), (4, 4),
        (5, 1), (5, 2), (5, 3), (5, 4),
        (6, 1), (6, 2), (6, 3), (6, 4),
        (7, 1), (7, 2), (7, 3), (7, 4),
        (8, 1), (8, 2), (8, 3), (8, 4),
        (9, 1), (9, 2), (9, 3), (9, 4),
        (10, 1), (10, 2), (10, 3), (10, 4),
        (11, 1), (11, 2), (11, 3), (11, 4),
        (12, 1), (12, 2), (12, 3), (12, 4),
        (13, 1), (13, 2), (13, 3), (13, 4)
),
publication_seed as (
    select
        268 + ((domain_index - 1) * 16) + ((theme_offset - 1) * 4) + pub_offset as publication_id,
        271 + ((domain_index - 1) * 5) as domain_topic_id,
        271 + ((domain_index - 1) * 5) + theme_offset as theme_topic_id
    from theme_seed
    cross join generate_series(1, 4) as publication_series(pub_offset)
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
