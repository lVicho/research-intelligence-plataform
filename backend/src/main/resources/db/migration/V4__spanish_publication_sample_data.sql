delete from publication_embeddings;

update publications set
    title = 'Modelos fundacionales clinicos para revisar riesgo multimodal del paciente',
    abstract_text = 'Evaluacion local de modelos clinicos multimodales para combinar datos estructurados, notas medicas e imagen en la revision de riesgo del paciente.',
    source = 'Revista de Sistemas de IA Clinica',
    updated_at = now()
where id = 1;

update publications set
    title = 'Descubrimiento de cohortes con grafos en genomica traslacional',
    abstract_text = 'Metodo basado en grafos para encontrar cohortes de investigacion conectando variantes genomicas, historiales clinicos y criterios de estudio.',
    source = 'Congreso Internacional de Datos Biomedicos',
    updated_at = now()
where id = 2;

update publications set
    title = 'Exposicion al calor urbano y patrones de ingreso hospitalario',
    abstract_text = 'Analisis de senales de clima urbano, islas de calor y tendencias de ingresos hospitalarios durante episodios de temperatura extrema.',
    source = 'Cuadernos del Instituto Atlantico',
    updated_at = now()
where id = 3;

update publications set
    title = 'Flujos reutilizables de gobernanza de datos para hospitales de investigacion',
    abstract_text = 'Modelo practico para catalogar, validar y compartir conjuntos de datos en estudios liderados por hospitales de investigacion.',
    source = 'Revision de Gobernanza de Datos',
    updated_at = now()
where id = 4;

update publications set
    title = 'Mapas tematicos federados para carteras institucionales de investigacion',
    abstract_text = 'Normalizacion de temas y exploracion de carteras cientificas en instituciones con varias unidades, hospitales y grupos de investigacion.',
    source = 'Herramientas Abiertas de Investigacion',
    updated_at = now()
where id = 5;

update topics set name = 'IA clinica', normalized_name = 'ia clinica' where id = 1;
update topics set name = 'Datos multimodales', normalized_name = 'datos multimodales' where id = 2;
update topics set name = 'Genomica', normalized_name = 'genomica' where id = 3;
update topics set name = 'Grafos de conocimiento', normalized_name = 'grafos de conocimiento' where id = 4;
update topics set name = 'Clima urbano', normalized_name = 'clima urbano' where id = 5;
update topics set name = 'Salud publica', normalized_name = 'salud publica' where id = 6;
update topics set name = 'Gobernanza de datos', normalized_name = 'gobernanza de datos' where id = 7;
update topics set name = 'Analitica de investigacion', normalized_name = 'analitica de investigacion' where id = 8;
update topics set name = 'Mapas tematicos', normalized_name = 'mapas tematicos' where id = 9;

insert into topics (id, name, normalized_name) values
    (101, 'Busqueda semantica', 'busqueda semantica'),
    (102, 'IA local', 'ia local'),
    (103, 'Privacidad', 'privacidad'),
    (104, 'Hospitales', 'hospitales'),
    (105, 'Cohortes', 'cohortes'),
    (106, 'Cambio climatico', 'cambio climatico'),
    (107, 'Salud digital', 'salud digital'),
    (108, 'Modelos predictivos', 'modelos predictivos'),
    (109, 'Gestion de datos', 'gestion de datos'),
    (110, 'Colaboracion cientifica', 'colaboracion cientifica'),
    (111, 'Deteccion de tendencias', 'deteccion de tendencias'),
    (112, 'Recomendacion de colaboraciones', 'recomendacion de colaboraciones');

insert into publications (id, title, abstract_text, year, type, status, doi, source, url, created_at, updated_at) values
    (101, 'Busqueda semantica local para publicaciones universitarias', 'Sistema de recuperacion con embeddings locales para encontrar publicaciones relacionadas sin enviar datos institucionales a servicios externos.', 2026, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2026.101', 'Revista Iberoamericana de Inteligencia de Investigacion', 'https://doi.org/10.1000/rip.2026.101', now(), now()),
    (102, 'Copilotos de investigacion con contexto recuperado y citas verificables', 'Diseno de un copiloto local que responde preguntas usando solo publicaciones recuperadas y devuelve citas de los trabajos utilizados.', 2026, 'CONFERENCE_PAPER', 'ACCEPTED', '10.1000/rip.2026.102', 'Congreso de Sistemas de Investigacion Responsable', 'https://doi.org/10.1000/rip.2026.102', now(), now()),
    (103, 'Privacidad en analitica institucional con modelos de lenguaje locales', 'Comparacion de estrategias para usar modelos locales en universidades y hospitales sin exponer resumenes, autores ni metadatos sensibles.', 2025, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2025.103', 'Cuadernos de IA Local', 'https://doi.org/10.1000/rip.2025.103', now(), now()),
    (104, 'Prediccion temprana de ingresos por calor extremo en ciudades costeras', 'Modelo predictivo que combina clima urbano, historiales hospitalarios y variables demograficas para anticipar picos de demanda asistencial.', 2025, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2025.104', 'Salud Publica y Clima', 'https://doi.org/10.1000/rip.2025.104', now(), now()),
    (105, 'Mapeo de colaboraciones entre hospitales, laboratorios y departamentos', 'Analisis de coautoria y afiliaciones multiples para descubrir colaboraciones activas entre unidades clinicas y grupos universitarios.', 2024, 'REPORT', 'PUBLISHED', null, 'Informes de Analitica Institucional', 'https://northbridge.example.edu/reports/colaboraciones', now(), now()),
    (106, 'Normalizacion de temas para carteras cientificas multilingues', 'Proceso de limpieza y agrupacion de palabras clave en espanol, ingles y portugues para mejorar la navegacion por temas de investigacion.', 2024, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2024.106', 'Revista de Gestion de Datos Cientificos', 'https://doi.org/10.1000/rip.2024.106', now(), now()),
    (107, 'Cohortes genomicas explicables mediante grafos de conocimiento clinico', 'Uso de grafos de conocimiento para explicar por que ciertos pacientes entran en cohortes de estudios genomicos traslacionales.', 2025, 'CONFERENCE_PAPER', 'PUBLISHED', '10.1000/rip.2025.107', 'Simposio de Genomica Traslacional', 'https://doi.org/10.1000/rip.2025.107', now(), now()),
    (108, 'Gobernanza de datos reutilizables en ensayos hospitalarios multicentro', 'Guia operativa para versionar diccionarios, controlar calidad y documentar procedencia en ensayos con datos de varios hospitales.', 2023, 'REPORT', 'PUBLISHED', null, 'Red Hospitalaria de Datos Abiertos', 'https://riverbend.example.org/reports/gobernanza-multicentro', now(), now()),
    (109, 'Deteccion de tendencias emergentes en investigacion sobre salud digital', 'Metodo para detectar temas crecientes en publicaciones recientes usando busqueda textual, temas compartidos y embeddings locales.', 2026, 'SOFTWARE', 'IN_PRESS', null, 'Herramientas Abiertas de Investigacion', 'https://northbridge.example.edu/tools/tendencias-salud-digital', now(), now()),
    (110, 'Evaluacion de modelos predictivos clinicos con datos multimodales', 'Marco de evaluacion para modelos que combinan tablas clinicas, notas, imagenes y senales temporales en tareas de riesgo hospitalario.', 2024, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2024.110', 'Revista de Salud Digital', 'https://doi.org/10.1000/rip.2024.110', now(), now()),
    (111, 'Arquitectura local-first para plataformas de inteligencia de investigacion', 'Propuesta de arquitectura modular con proveedores mock por defecto, Ollama local y extension futura para servicios externos opcionales.', 2026, 'REPORT', 'DRAFT', null, 'Documentos Tecnicos RIP', 'https://northbridge.example.edu/reports/local-first', now(), now()),
    (112, 'Recomendacion de colaboraciones cientificas con autores y temas compartidos', 'Primer ranking de colaboraciones potenciales combinando coautoria, afinidad tematica y proximidad entre unidades de investigacion.', 2025, 'ARTICLE', 'ACCEPTED', '10.1000/rip.2025.112', 'Analitica de Investigacion Aplicada', 'https://doi.org/10.1000/rip.2025.112', now(), now()),
    (113, 'Calidad de metadatos en repositorios de publicaciones hospitalarias', 'Estudio de errores frecuentes en DOI, autores, afiliaciones y temas durante la integracion de datos bibliograficos locales.', 2023, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2023.113', 'Gestion de Datos en Salud', 'https://doi.org/10.1000/rip.2023.113', now(), now()),
    (114, 'Paneles de actividad investigadora para centros clinicos universitarios', 'Diseno de indicadores descriptivos sin metricas bibliometricas ambiguas para explorar actividad reciente por unidades y temas.', 2024, 'SOFTWARE', 'PUBLISHED', null, 'Herramientas de Gestion Cientifica', 'https://riverbend.example.org/tools/paneles-investigacion', now(), now()),
    (115, 'Relacion entre contaminacion termica urbana y salud respiratoria', 'Analisis espacial de calor urbano, contaminacion y visitas respiratorias para apoyar decisiones de salud publica municipal.', 2023, 'REPORT', 'PUBLISHED', null, 'Observatorio de Salud Urbana', 'https://aiss.example.pt/reports/calor-respiratorio', now(), now()),
    (116, 'Busqueda hibrida de publicaciones con texto, autores y temas', 'Experimento de recuperacion que combina busqueda textual, autores compartidos y temas normalizados antes de incorporar similitud vectorial.', 2025, 'CONFERENCE_PAPER', 'PUBLISHED', '10.1000/rip.2025.116', 'Conferencia de Recuperacion de Informacion Cientifica', 'https://doi.org/10.1000/rip.2025.116', now(), now()),
    (117, 'Trazabilidad de datasets clinicos para investigacion reproducible', 'Modelo de trazabilidad que conecta publicaciones, datasets, responsables de curacion y reglas de acceso en hospitales de investigacion.', 2024, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2024.117', 'Revista de Datos Reproducibles', 'https://doi.org/10.1000/rip.2024.117', now(), now()),
    (118, 'Exploracion visual de redes de investigadores y temas institucionales', 'Interfaz para navegar relaciones entre investigadores, unidades, publicaciones y temas con filtros pensados para equipos de gestion cientifica.', 2026, 'SOFTWARE', 'IN_PRESS', null, 'Open Research Tools Iberia', 'https://northbridge.example.edu/tools/redes-investigacion', now(), now()),
    (119, 'Modelos locales para resumen de evidencia en comites clinicos', 'Uso de modelos de lenguaje ejecutados en local para resumir evidencia recuperada, manteniendo citas y advertencias sobre contexto insuficiente.', 2025, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2025.119', 'IA Clinica Responsable', 'https://doi.org/10.1000/rip.2025.119', now(), now()),
    (120, 'Integracion de datos climaticos y hospitalarios para vigilancia poblacional', 'Canal de datos que une sensores climaticos urbanos, episodios de urgencias y mapas de vulnerabilidad para vigilancia de salud publica.', 2024, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2024.120', 'Salud Publica Computacional', 'https://doi.org/10.1000/rip.2024.120', now(), now());

insert into publication_authors (publication_id, researcher_id, external_author_name, external_affiliation, author_order, corresponding_author) values
    (101, 1, null, null, 1, true),
    (101, 5, null, null, 2, false),
    (101, null, 'Lucia Moreno', 'Universidad del Mediterraneo', 3, false),
    (102, 3, null, null, 1, true),
    (102, 1, null, null, 2, false),
    (102, null, 'Mateo Serrano', 'Instituto de Sistemas Responsables', 3, false),
    (103, 1, null, null, 1, false),
    (103, 4, null, null, 2, true),
    (103, null, 'Ana Ribeiro', 'Centro de Privacidad Digital', 3, false),
    (104, 4, null, null, 1, true),
    (104, null, 'Rui Martins', 'Observatorio de Salud Publica de Lisboa', 2, false),
    (105, 2, null, null, 1, true),
    (105, 5, null, null, 2, false),
    (105, null, 'Ines Carvalho', 'Red Clinica Iberica', 3, false),
    (106, 5, null, null, 1, true),
    (106, 4, null, null, 2, false),
    (106, null, 'Clara Beltran', 'Archivo Europeo de Investigacion', 3, false),
    (107, 2, null, null, 1, true),
    (107, 6, null, null, 2, false),
    (107, null, 'Pablo Nunez', 'Centro Nacional de Genomica', 3, false),
    (108, 6, null, null, 1, true),
    (108, 2, null, null, 2, false),
    (108, null, 'Marta Soler', 'Consorcio de Datos Clinicos', 3, false),
    (109, 1, null, null, 1, true),
    (109, 3, null, null, 2, false),
    (109, 5, null, null, 3, false),
    (110, 3, null, null, 1, true),
    (110, 1, null, null, 2, false),
    (110, null, 'Diego Fuentes', 'Hospital General del Sur', 3, false),
    (111, 5, null, null, 1, true),
    (111, 1, null, null, 2, false),
    (112, 4, null, null, 1, true),
    (112, 5, null, null, 2, false),
    (112, 2, null, null, 3, false),
    (113, 6, null, null, 1, true),
    (113, null, 'Carla Reis', 'Open Data Commons Iberia', 2, false),
    (114, 2, null, null, 1, true),
    (114, 3, null, null, 2, false),
    (115, 4, null, null, 1, true),
    (115, null, 'Sofia Almeida', 'Ayuntamiento de Lisboa', 2, false),
    (116, 5, null, null, 1, true),
    (116, 1, null, null, 2, false),
    (117, 6, null, null, 1, true),
    (117, 2, null, null, 2, false),
    (118, 5, null, null, 1, true),
    (118, 4, null, null, 2, false),
    (118, null, 'Irene Campos', 'Laboratorio de Visualizacion Cientifica', 3, false),
    (119, 1, null, null, 1, true),
    (119, 3, null, null, 2, false),
    (119, null, 'Rosa Marin', 'Comite de Evidencia Clinica', 3, false),
    (120, 4, null, null, 1, true),
    (120, 2, null, null, 2, false),
    (120, null, 'Miguel Torres', 'Agencia de Salud Urbana', 3, false);

insert into publication_topics (publication_id, topic_id) values
    (101, 101), (101, 102), (101, 103), (101, 8),
    (102, 102), (102, 101), (102, 4),
    (103, 102), (103, 103), (103, 8),
    (104, 5), (104, 6), (104, 106), (104, 108),
    (105, 110), (105, 112), (105, 8), (105, 104),
    (106, 9), (106, 109), (106, 8),
    (107, 3), (107, 4), (107, 105),
    (108, 7), (108, 109), (108, 104),
    (109, 111), (109, 107), (109, 101), (109, 102),
    (110, 1), (110, 2), (110, 108), (110, 107),
    (111, 102), (111, 103), (111, 8),
    (112, 112), (112, 110), (112, 9),
    (113, 109), (113, 7), (113, 8),
    (114, 8), (114, 104), (114, 9),
    (115, 5), (115, 6), (115, 106),
    (116, 101), (116, 4), (116, 9),
    (117, 7), (117, 109), (117, 104),
    (118, 110), (118, 9), (118, 8),
    (119, 1), (119, 102), (119, 101),
    (120, 5), (120, 6), (120, 106), (120, 107);

select setval('publications_id_seq', (select max(id) from publications));
select setval('topics_id_seq', (select max(id) from topics));
