delete from publication_embeddings;

update research_units set name = 'Universidad Central Iberica', short_name = 'UCI', type = 'UNIVERSITY', parent_id = null, country = 'Espana', city = 'Madrid', website = 'https://uci.example.edu', updated_at = now() where id = 1;
update research_units set name = 'Facultad de Ciencias de la Salud', short_name = 'FCS', type = 'FACULTY', parent_id = 1, country = 'Espana', city = 'Madrid', website = 'https://uci.example.edu/salud', updated_at = now() where id = 2;
update research_units set name = 'Instituto de IA Clinica', short_name = 'IIAC', type = 'INSTITUTE', parent_id = 2, country = 'Espana', city = 'Madrid', website = 'https://uci.example.edu/ia-clinica', updated_at = now() where id = 3;
update research_units set name = 'Grupo de Datos Multimodales en Salud', short_name = 'GDMS', type = 'RESEARCH_GROUP', parent_id = 3, country = 'Espana', city = 'Madrid', website = 'https://uci.example.edu/datos-multimodales', updated_at = now() where id = 4;
update research_units set name = 'Hospital Universitario Central', short_name = 'HUC', type = 'HOSPITAL', parent_id = null, country = 'Espana', city = 'Madrid', website = 'https://huc.example.org', updated_at = now() where id = 5;
update research_units set name = 'Instituto de Genomica Traslacional', short_name = 'IGT', type = 'INSTITUTE', parent_id = 5, country = 'Espana', city = 'Madrid', website = 'https://huc.example.org/genomica', updated_at = now() where id = 6;
update research_units set name = 'Observatorio de Clima Urbano y Salud', short_name = 'OCUS', type = 'INSTITUTE', parent_id = null, country = 'Espana', city = 'Valencia', website = 'https://ocus.example.es', updated_at = now() where id = 7;
update research_units set name = 'Departamento de Salud Publica', short_name = 'DSP', type = 'DEPARTMENT', parent_id = 7, country = 'Espana', city = 'Valencia', website = 'https://ocus.example.es/salud-publica', updated_at = now() where id = 8;

insert into research_units (id, name, short_name, type, parent_id, country, city, website, active, created_at, updated_at) values
    (101, 'Unidad de Analitica Institucional', 'UAI', 'CENTER', 1, 'Espana', 'Madrid', 'https://uci.example.edu/analitica-institucional', true, now(), now()),
    (102, 'Centro de Conservacion de Grandes Felinos', 'CCGF', 'CENTER', null, 'Espana', 'Sevilla', 'https://felinos.example.es', true, now(), now()),
    (103, 'Laboratorio de Ecologia de Panteras', 'LEP', 'LAB', 102, 'Espana', 'Sevilla', 'https://felinos.example.es/panteras', true, now(), now()),
    (104, 'Instituto de Astronomia Observacional', 'IAO', 'INSTITUTE', null, 'Espana', 'Granada', 'https://astronomia.example.es', true, now(), now()),
    (105, 'Departamento de Literatura Medieval', 'DLM', 'DEPARTMENT', 1, 'Espana', 'Salamanca', 'https://uci.example.edu/literatura-medieval', true, now(), now()),
    (106, 'Centro de Agricultura Sostenible', 'CAS', 'CENTER', null, 'Espana', 'Cordoba', 'https://agro.example.es', true, now(), now()),
    (107, 'Laboratorio de Materiales Ceramicos', 'LMC', 'LAB', null, 'Espana', 'Castellon', 'https://materiales.example.es/ceramicos', true, now(), now()),
    (108, 'Instituto de Arqueologia Mediterranea', 'IAM', 'INSTITUTE', null, 'Espana', 'Tarragona', 'https://arqueologia.example.es', true, now(), now());

update researchers set full_name = 'Lucia Herrera', display_name = 'Lucia Herrera', email = 'lucia.herrera@uci.example.edu', updated_at = now() where id = 1;
update researchers set full_name = 'Omar Alvarez', display_name = 'Omar Alvarez', email = 'omar.alvarez@huc.example.org', updated_at = now() where id = 2;
update researchers set full_name = 'Priya Raman', display_name = 'Priya Raman', email = 'priya.raman@uci.example.edu', updated_at = now() where id = 3;
update researchers set full_name = 'Elena Kovacs', display_name = 'Elena Kovacs', email = 'elena.kovacs@ocus.example.es', updated_at = now() where id = 4;
update researchers set full_name = 'Jonas Weber', display_name = 'Jonas Weber', email = 'jonas.weber@uci.example.edu', updated_at = now() where id = 5;
update researchers set full_name = 'Sara Williams', display_name = 'Sara Williams', email = 'sara.williams@huc.example.org', updated_at = now() where id = 6;

insert into researchers (id, full_name, display_name, email, orcid, active, created_at, updated_at) values
    (101, 'Carmen Rios', 'Carmen Rios', 'carmen.rios@huc.example.org', '0000-0002-7001-0001', true, now(), now()),
    (102, 'Diego Fuentes', 'Diego Fuentes', 'diego.fuentes@uci.example.edu', '0000-0002-7001-0002', true, now(), now()),
    (103, 'Marta Soler', 'Marta Soler', 'marta.soler@huc.example.org', '0000-0002-7001-0003', true, now(), now()),
    (104, 'Ines Carvalho', 'Ines Carvalho', 'ines.carvalho@ocus.example.es', '0000-0002-7001-0004', true, now(), now()),
    (105, 'Rui Martins', 'Rui Martins', 'rui.martins@ocus.example.es', '0000-0002-7001-0005', true, now(), now()),
    (106, 'Pablo Nunez', 'Pablo Nunez', 'pablo.nunez@huc.example.org', '0000-0002-7001-0006', true, now(), now()),
    (107, 'Ana Beltran', 'Ana Beltran', 'ana.beltran@huc.example.org', '0000-0002-7001-0007', true, now(), now()),
    (108, 'Clara Vidal', 'Clara Vidal', 'clara.vidal@uci.example.edu', '0000-0002-7001-0008', true, now(), now()),
    (109, 'Mateo Serrano', 'Mateo Serrano', 'mateo.serrano@uci.example.edu', '0000-0002-7001-0009', true, now(), now()),
    (110, 'Valeria Campos', 'Valeria Campos', 'valeria.campos@felinos.example.es', '0000-0002-7001-0010', true, now(), now()),
    (111, 'Nicolas Duarte', 'Nicolas Duarte', 'nicolas.duarte@felinos.example.es', '0000-0002-7001-0011', true, now(), now()),
    (112, 'Sofia Almeida', 'Sofia Almeida', 'sofia.almeida@felinos.example.es', '0000-0002-7001-0012', true, now(), now()),
    (113, 'Hugo Torres', 'Hugo Torres', 'hugo.torres@astronomia.example.es', '0000-0002-7001-0013', true, now(), now()),
    (114, 'Laura Benitez', 'Laura Benitez', 'laura.benitez@uci.example.edu', '0000-0002-7001-0014', true, now(), now()),
    (115, 'Sergio Molina', 'Sergio Molina', 'sergio.molina@agro.example.es', '0000-0002-7001-0015', true, now(), now()),
    (116, 'Nadia Karim', 'Nadia Karim', 'nadia.karim@materiales.example.es', '0000-0002-7001-0016', true, now(), now()),
    (117, 'Alba Ferrer', 'Alba Ferrer', 'alba.ferrer@arqueologia.example.es', '0000-0002-7001-0017', true, now(), now()),
    (118, 'Mei Chen', 'Mei Chen', 'mei.chen@felinos.example.es', '0000-0002-7001-0018', true, now(), now());

insert into researcher_affiliations (id, researcher_id, research_unit_id, role, affiliation_type, start_date, end_date, primary_affiliation, created_at, updated_at) values
    (101, 101, 5, 'Responsable de evidencia clinica', 'MEMBER', '2020-02-01', null, true, now(), now()),
    (102, 102, 4, 'Investigador de datos multimodales', 'MEMBER', '2021-04-01', null, true, now(), now()),
    (103, 103, 5, 'Coordinadora de gobernanza de datos', 'MEMBER', '2019-06-01', null, true, now(), now()),
    (104, 104, 8, 'Epidemiologa ambiental', 'MEMBER', '2018-09-01', null, true, now(), now()),
    (105, 105, 7, 'Analista de sensores urbanos', 'MEMBER', '2020-01-01', null, true, now(), now()),
    (106, 106, 6, 'Investigador principal en grafos genomicos', 'LEADER', '2019-03-01', null, true, now(), now()),
    (107, 107, 6, 'Genetista traslacional', 'MEMBER', '2021-05-01', null, true, now(), now()),
    (108, 108, 101, 'Responsable de analitica institucional', 'LEADER', '2017-11-01', null, true, now(), now()),
    (109, 109, 101, 'Analista de colaboracion cientifica', 'MEMBER', '2022-01-01', null, true, now(), now()),
    (110, 110, 103, 'Ecologa de panteras', 'LEADER', '2016-04-01', null, true, now(), now()),
    (111, 111, 102, 'Especialista en corredores ecologicos', 'MEMBER', '2018-02-01', null, true, now(), now()),
    (112, 112, 102, 'Biologa de conservacion', 'MEMBER', '2020-09-01', null, true, now(), now()),
    (113, 113, 104, 'Astronomo observacional', 'MEMBER', '2015-01-01', null, true, now(), now()),
    (114, 114, 105, 'Medievalista', 'MEMBER', '2014-10-01', null, true, now(), now()),
    (115, 115, 106, 'Investigador en agroecologia', 'MEMBER', '2019-02-01', null, true, now(), now()),
    (116, 116, 107, 'Investigadora en ceramicas avanzadas', 'MEMBER', '2021-03-01', null, true, now(), now()),
    (117, 117, 108, 'Arqueologa mediterranea', 'MEMBER', '2017-07-01', null, true, now(), now()),
    (118, 118, 103, 'Analista de camaras trampa', 'MEMBER', '2022-05-01', null, true, now(), now());

insert into topics (id, name, normalized_name) values
    (201, 'Comites de evidencia clinica', 'comites de evidencia clinica'),
    (202, 'Riesgo hospitalario', 'riesgo hospitalario'),
    (203, 'Sensores urbanos', 'sensores urbanos'),
    (204, 'Olas de calor', 'olas de calor'),
    (205, 'Vulnerabilidad climatica', 'vulnerabilidad climatica'),
    (206, 'Epidemiologia ambiental', 'epidemiologia ambiental'),
    (207, 'Urgencias hospitalarias', 'urgencias hospitalarias'),
    (208, 'Variantes genomicas', 'variantes genomicas'),
    (209, 'Medicina personalizada', 'medicina personalizada'),
    (210, 'Portfolios de investigacion', 'portfolios de investigacion'),
    (211, 'Unidades de investigacion', 'unidades de investigacion'),
    (212, 'Panteras', 'panteras'),
    (213, 'Felinos salvajes', 'felinos salvajes'),
    (214, 'Corredores ecologicos', 'corredores ecologicos'),
    (215, 'Conservacion', 'conservacion'),
    (216, 'Conservacion de habitats', 'conservacion de habitats'),
    (217, 'Camaras trampa', 'camaras trampa'),
    (218, 'Biodiversidad', 'biodiversidad'),
    (219, 'Comportamiento animal', 'comportamiento animal'),
    (220, 'Astronomia observacional', 'astronomia observacional'),
    (221, 'Literatura medieval', 'literatura medieval'),
    (222, 'Agricultura sostenible', 'agricultura sostenible'),
    (223, 'Materiales ceramicos', 'materiales ceramicos'),
    (224, 'Arqueologia mediterranea', 'arqueologia mediterranea');

insert into publications (id, title, abstract_text, year, type, status, doi, source, url, created_at, updated_at) values
    (201, 'Analisis semantico de publicaciones clinicas en hospitales universitarios', 'Evaluacion de busqueda semantica local para recuperar estudios clinicos relacionados por riesgo, datos multimodales y autores compartidos.', 2026, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2026.201', 'Revista de IA Clinica Local', 'https://doi.org/10.7777/rip.2026.201', now(), now()),
    (202, 'Copilotos locales para comites de evidencia clinica', 'Prototipo de copiloto que responde preguntas con contexto recuperado, citas verificables y advertencias cuando el contexto es insuficiente.', 2026, 'SOFTWARE', 'IN_PRESS', null, 'Herramientas Abiertas de Investigacion', 'https://demo.example.es/copilotos-locales', now(), now()),
    (203, 'Privacidad y despliegue de modelos locales en hospitales', 'Analisis operativo para ejecutar modelos de lenguaje y embeddings dentro de infraestructura hospitalaria sin APIs externas ni salida de datos sensibles.', 2024, 'REPORT', 'PUBLISHED', '10.7777/rip.2024.203', 'Cuadernos de IA Local', 'https://doi.org/10.7777/rip.2024.203', now(), now()),
    (204, 'Modelos predictivos multimodales para riesgo hospitalario', 'Evaluacion de modelos que combinan notas clinicas, tablas, imagenes y series temporales para anticipar complicaciones durante el ingreso.', 2025, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2025.204', 'Revista de Salud Digital', 'https://doi.org/10.7777/rip.2025.204', now(), now()),
    (205, 'Resumen de evidencia clinica con modelos locales y citas trazables', 'Metodo para sintetizar evidencia recuperada desde publicaciones internas manteniendo citas verificables para comites hospitalarios.', 2025, 'CONFERENCE_PAPER', 'PUBLISHED', '10.7777/rip.2025.205', 'Congreso de IA Clinica Responsable', 'https://doi.org/10.7777/rip.2025.205', now(), now()),
    (206, 'Gobernanza de datos clinicos para investigacion hospitalaria', 'Marco de roles, calidad, versionado y trazabilidad para reutilizar datasets clinicos en proyectos de investigacion multicentro.', 2023, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2023.206', 'Gestion de Datos en Salud', 'https://doi.org/10.7777/rip.2023.206', now(), now()),
    (207, 'Sensores urbanos para detectar olas de calor y riesgo sanitario', 'Integracion de sensores de temperatura, humedad y movilidad para identificar zonas con riesgo durante episodios de calor extremo.', 2025, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2025.207', 'Salud Publica y Clima Urbano', 'https://doi.org/10.7777/rip.2025.207', now(), now()),
    (208, 'Olas de calor y demanda de urgencias hospitalarias', 'Analisis temporal de episodios de calor, llamadas de emergencia y llegadas a urgencias en barrios con diferente vulnerabilidad social.', 2024, 'REPORT', 'PUBLISHED', null, 'Observatorio de Salud Urbana', 'https://demo.example.es/olas-calor-urgencias', now(), now()),
    (209, 'Mapas de vulnerabilidad climatica para salud publica municipal', 'Metodologia para combinar edad, renta, vegetacion urbana y exposicion termica en mapas de priorizacion sanitaria.', 2026, 'ARTICLE', 'ACCEPTED', '10.7777/rip.2026.209', 'Epidemiologia Ambiental Aplicada', 'https://doi.org/10.7777/rip.2026.209', now(), now()),
    (210, 'Epidemiologia ambiental de calor urbano y salud respiratoria', 'Estudio de asociaciones entre contaminacion, temperatura nocturna y consultas respiratorias en poblaciones vulnerables.', 2023, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2023.210', 'Revista de Salud Publica Computacional', 'https://doi.org/10.7777/rip.2023.210', now(), now()),
    (211, 'Vigilancia poblacional con datos climaticos y hospitalarios', 'Canal de datos que une sensores urbanos, registros de urgencias y capas de vulnerabilidad para vigilancia temprana de salud publica.', 2024, 'SOFTWARE', 'PUBLISHED', null, 'Herramientas de Salud Urbana', 'https://demo.example.es/vigilancia-clima-salud', now(), now()),
    (212, 'Grafos de conocimiento para cohortes de investigacion genomica', 'Construccion de grafos que conectan pacientes, fenotipos, variantes genomicas y criterios de inclusion para cohortes traslacionales.', 2025, 'CONFERENCE_PAPER', 'PUBLISHED', '10.7777/rip.2025.212', 'Simposio de Genomica Traslacional', 'https://doi.org/10.7777/rip.2025.212', now(), now()),
    (213, 'Variantes genomicas y estratificacion de riesgo en medicina personalizada', 'Modelo de interpretacion que relaciona variantes genomicas con fenotipos clinicos para orientar decisiones de medicina personalizada.', 2024, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2024.213', 'Medicina Genomica Aplicada', 'https://doi.org/10.7777/rip.2024.213', now(), now()),
    (214, 'Cohortes explicables para estudios de genomica traslacional', 'Procedimiento para documentar por que un paciente pertenece a una cohorte usando reglas, grafos y evidencia clinica.', 2026, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2026.214', 'Datos Biomedicos Explicables', 'https://doi.org/10.7777/rip.2026.214', now(), now()),
    (215, 'Integracion de historias clinicas y variantes genomicas en grafos', 'Arquitectura de datos que vincula eventos clinicos, laboratorios y variantes para facilitar busqueda de cohortes y revision de casos.', 2025, 'REPORT', 'PUBLISHED', null, 'Informes de Genomica Clinica', 'https://demo.example.es/grafos-historias-variantes', now(), now()),
    (216, 'Medicina personalizada basada en conocimiento clinico-genomico', 'Analisis de patrones compartidos entre tratamientos, mutaciones y respuesta clinica usando grafos de conocimiento revisables.', 2023, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2023.216', 'Revista de Medicina Personalizada', 'https://doi.org/10.7777/rip.2023.216', now(), now()),
    (217, 'Mapas tematicos para portfolios de investigacion universitarios', 'Sistema de normalizacion de temas para explorar portfolios, unidades de investigacion y publicaciones recientes de una institucion.', 2025, 'SOFTWARE', 'PUBLISHED', null, 'Herramientas de Gestion Cientifica', 'https://demo.example.es/mapas-tematicos-portfolios', now(), now()),
    (218, 'Paneles institucionales de actividad investigadora por unidades', 'Diseno de paneles descriptivos para comparar actividad por departamentos, hospitales, grupos y centros sin usar metricas bibliometricas ambiguas.', 2024, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2024.218', 'Analitica de Investigacion Aplicada', 'https://doi.org/10.7777/rip.2024.218', now(), now()),
    (219, 'Deteccion de tendencias emergentes en portfolios cientificos', 'Metodo para identificar temas crecientes combinando fechas, palabras clave normalizadas y busqueda semantica local.', 2026, 'ARTICLE', 'PUBLISHED', '10.7777/rip.2026.219', 'Revista de Inteligencia Institucional', 'https://doi.org/10.7777/rip.2026.219', now(), now()),
    (220, 'Colaboracion cientifica entre unidades de investigacion', 'Analisis de coautoria y afiliaciones para detectar conexiones entre grupos, hospitales, laboratorios y departamentos universitarios.', 2025, 'REPORT', 'PUBLISHED', null, 'Informes de Analitica Institucional', 'https://demo.example.es/colaboracion-unidades', now(), now()),
    (221, 'Recomendacion de colaboraciones por afinidad tematica institucional', 'Ranking de colaboraciones potenciales usando temas compartidos, autores puente y proximidad organizativa entre unidades de investigacion.', 2026, 'ARTICLE', 'ACCEPTED', '10.7777/rip.2026.221', 'Gestion Cientifica Computacional', 'https://doi.org/10.7777/rip.2026.221', now(), now()),
    (222, 'Corredores ecologicos para panteras en paisajes fragmentados', 'Evaluacion de rutas de movimiento para panteras usando conectividad de habitat, carreteras y presion humana en reservas mediterraneas.', 2025, 'ARTICLE', 'PUBLISHED', '10.7777/fauna.2025.222', 'Conservacion de Grandes Felinos', 'https://doi.org/10.7777/fauna.2025.222', now(), now()),
    (223, 'Camaras trampa para estimar actividad nocturna de felinos salvajes', 'Protocolo de muestreo con camaras trampa para medir patrones de actividad de panteras y otros felinos salvajes.', 2024, 'ARTICLE', 'PUBLISHED', '10.7777/fauna.2024.223', 'Ecologia de Carnivoros', 'https://doi.org/10.7777/fauna.2024.223', now(), now()),
    (224, 'Conservacion de habitats para poblaciones aisladas de panteras', 'Analisis de perdida de habitat, disponibilidad de presas y medidas de restauracion para poblaciones pequenas de panteras.', 2023, 'REPORT', 'PUBLISHED', null, 'Informes de Biodiversidad Iberica', 'https://demo.example.es/habitats-panteras', now(), now()),
    (225, 'Comportamiento animal y uso del territorio en panteras jovenes', 'Seguimiento de individuos juveniles para estudiar dispersion, uso de refugios y aprendizaje de rutas seguras.', 2026, 'ARTICLE', 'ACCEPTED', '10.7777/fauna.2026.225', 'Revista de Comportamiento Animal', 'https://doi.org/10.7777/fauna.2026.225', now(), now()),
    (226, 'Biodiversidad asociada a corredores de grandes felinos', 'Medicion de especies indicadoras en corredores ecologicos usados por panteras, linces y otros carnivoros medianos.', 2025, 'CONFERENCE_PAPER', 'PUBLISHED', '10.7777/fauna.2025.226', 'Congreso de Biodiversidad y Paisaje', 'https://doi.org/10.7777/fauna.2025.226', now(), now()),
    (227, 'Conflictos entre comunidades rurales y conservacion de panteras', 'Estudio social de percepciones, ganaderia extensiva y medidas de coexistencia en areas cercanas a reservas.', 2024, 'REPORT', 'PUBLISHED', null, 'Cuadernos de Conservacion Social', 'https://demo.example.es/conflictos-panteras', now(), now()),
    (228, 'Fotometria de transitos en astronomia observacional', 'Pipeline para detectar variaciones de brillo en estrellas candidatas a sistemas con exoplanetas mediante telescopios de pequeno diametro.', 2025, 'ARTICLE', 'PUBLISHED', '10.7777/astro.2025.228', 'Astronomia Observacional Iberica', 'https://doi.org/10.7777/astro.2025.228', now(), now()),
    (229, 'Calibracion de telescopios roboticos para vigilancia del cielo profundo', 'Procedimiento de calibracion fotometrica y astrometrica para redes de telescopios roboticos de observacion nocturna.', 2024, 'SOFTWARE', 'PUBLISHED', null, 'Instrumentacion Astronomica', 'https://demo.example.es/telescopios-roboticos', now(), now()),
    (230, 'Redes de manuscritos en literatura medieval castellana', 'Analisis de transmision textual y variantes en manuscritos medievales mediante grafos de copias, talleres y familias textuales.', 2023, 'ARTICLE', 'PUBLISHED', '10.7777/humanidades.2023.230', 'Estudios de Literatura Medieval', 'https://doi.org/10.7777/humanidades.2023.230', now(), now()),
    (231, 'Rotaciones de cultivo y agricultura sostenible en secano mediterraneo', 'Evaluacion de rotaciones, cobertura vegetal y eficiencia hidrica para mejorar productividad y resiliencia en agricultura de secano.', 2025, 'ARTICLE', 'PUBLISHED', '10.7777/agro.2025.231', 'Agricultura Sostenible Mediterranea', 'https://doi.org/10.7777/agro.2025.231', now(), now()),
    (232, 'Materiales ceramicos porosos para filtracion de agua', 'Sintesis y caracterizacion de ceramicas porosas de bajo coste para filtracion en comunidades rurales.', 2024, 'ARTICLE', 'PUBLISHED', '10.7777/materiales.2024.232', 'Revista de Materiales Ceramicos', 'https://doi.org/10.7777/materiales.2024.232', now(), now()),
    (233, 'Arqueologia mediterranea de puertos romanos secundarios', 'Prospeccion y analisis ceramico para reconstruir redes comerciales en puertos romanos de escala regional.', 2023, 'REPORT', 'PUBLISHED', null, 'Cuadernos de Arqueologia Mediterranea', 'https://demo.example.es/puertos-romanos', now(), now());

insert into publication_authors (publication_id, researcher_id, external_author_name, external_affiliation, author_order, corresponding_author) values
    (201, 1, null, null, 1, true), (201, 3, null, null, 2, false), (201, 102, null, null, 3, false),
    (202, 101, null, null, 1, true), (202, 1, null, null, 2, false), (202, 5, null, null, 3, false),
    (203, 2, null, null, 1, true), (203, 103, null, null, 2, false), (203, null, 'Ana Ribeiro', 'Centro de Privacidad Digital', 3, false),
    (204, 3, null, null, 1, true), (204, 102, null, null, 2, false), (204, 101, null, null, 3, false),
    (205, 101, null, null, 1, true), (205, 1, null, null, 2, false), (205, null, 'Rosa Marin', 'Comite de Evidencia Clinica', 3, false),
    (206, 103, null, null, 1, true), (206, 6, null, null, 2, false), (206, 2, null, null, 3, false),
    (207, 104, null, null, 1, true), (207, 105, null, null, 2, false), (207, 4, null, null, 3, false),
    (208, 4, null, null, 1, true), (208, 104, null, null, 2, false), (208, null, 'Marina Costa', 'Servicio Municipal de Urgencias', 3, false),
    (209, 104, null, null, 1, true), (209, 105, null, null, 2, false), (209, null, 'Pau Navarro', 'Ayuntamiento de Valencia', 3, false),
    (210, 4, null, null, 1, true), (210, 104, null, null, 2, false), (210, null, 'Rui Esteves', 'Instituto de Epidemiologia Ambiental', 3, false),
    (211, 105, null, null, 1, true), (211, 4, null, null, 2, false), (211, 2, null, null, 3, false),
    (212, 106, null, null, 1, true), (212, 107, null, null, 2, false), (212, 2, null, null, 3, false),
    (213, 107, null, null, 1, true), (213, 106, null, null, 2, false), (213, null, 'Elena Ruiz', 'Centro Nacional de Genomica', 3, false),
    (214, 106, null, null, 1, true), (214, 3, null, null, 2, false), (214, 107, null, null, 3, false),
    (215, 2, null, null, 1, true), (215, 106, null, null, 2, false), (215, 107, null, null, 3, false),
    (216, 107, null, null, 1, true), (216, 106, null, null, 2, false), (216, null, 'Carlos Varela', 'Unidad de Medicina Personalizada', 3, false),
    (217, 108, null, null, 1, true), (217, 109, null, null, 2, false), (217, 5, null, null, 3, false),
    (218, 108, null, null, 1, true), (218, 5, null, null, 2, false), (218, null, 'Irene Campos', 'Laboratorio de Visualizacion Cientifica', 3, false),
    (219, 109, null, null, 1, true), (219, 108, null, null, 2, false), (219, 1, null, null, 3, false),
    (220, 5, null, null, 1, true), (220, 108, null, null, 2, false), (220, 109, null, null, 3, false),
    (221, 109, null, null, 1, true), (221, 108, null, null, 2, false), (221, null, 'Ines Carvalho', 'Red Clinica Iberica', 3, false),
    (222, 110, null, null, 1, true), (222, 111, null, null, 2, false), (222, 118, null, null, 3, false),
    (223, 118, null, null, 1, true), (223, 110, null, null, 2, false), (223, null, 'Miguel Torres', 'Reserva Sierra Clara', 3, false),
    (224, 112, null, null, 1, true), (224, 111, null, null, 2, false), (224, 110, null, null, 3, false),
    (225, 110, null, null, 1, true), (225, 118, null, null, 2, false), (225, null, 'Beatriz Leon', 'Programa de Seguimiento de Felinos', 3, false),
    (226, 111, null, null, 1, true), (226, 112, null, null, 2, false), (226, null, 'Paula Estevez', 'Observatorio de Biodiversidad', 3, false),
    (227, 112, null, null, 1, true), (227, 110, null, null, 2, false), (227, null, 'Manuel Ortega', 'Cooperativa Ganadera del Sur', 3, false),
    (228, 113, null, null, 1, true), (228, null, 'Noelia Vega', 'Red de Telescopios del Sur', 2, false),
    (229, 113, null, null, 1, true), (229, null, 'Adam Novak', 'Observatorio Alpino', 2, false),
    (230, 114, null, null, 1, true), (230, null, 'Beatriz Montero', 'Archivo Historico de Salamanca', 2, false),
    (231, 115, null, null, 1, true), (231, null, 'Leila Haddad', 'Cooperativa Agroecologica Mediterranea', 2, false),
    (232, 116, null, null, 1, true), (232, null, 'Tomas Ibarra', 'Instituto de Quimica Aplicada', 2, false),
    (233, 117, null, null, 1, true), (233, null, 'Giulia Romano', 'Museo Arqueologico del Mediterraneo', 2, false);

insert into publication_topics (publication_id, topic_id) values
    (201, 1), (201, 101), (201, 2), (201, 104),
    (202, 102), (202, 101), (202, 201), (202, 1),
    (203, 102), (203, 103), (203, 104), (203, 7),
    (204, 1), (204, 2), (204, 202), (204, 108),
    (205, 102), (205, 201), (205, 101), (205, 1),
    (206, 7), (206, 109), (206, 104),
    (207, 203), (207, 204), (207, 6), (207, 5),
    (208, 204), (208, 207), (208, 205), (208, 6),
    (209, 205), (209, 106), (209, 6), (209, 206),
    (210, 206), (210, 5), (210, 6), (210, 106),
    (211, 203), (211, 207), (211, 5), (211, 6),
    (212, 4), (212, 105), (212, 3), (212, 208),
    (213, 208), (213, 209), (213, 3),
    (214, 105), (214, 4), (214, 3),
    (215, 4), (215, 3), (215, 208), (215, 104),
    (216, 209), (216, 4), (216, 3),
    (217, 9), (217, 210), (217, 8), (217, 211),
    (218, 8), (218, 211), (218, 104),
    (219, 111), (219, 210), (219, 101), (219, 8),
    (220, 110), (220, 211), (220, 8),
    (221, 112), (221, 110), (221, 9),
    (222, 212), (222, 214), (222, 216), (222, 215),
    (223, 217), (223, 213), (223, 212), (223, 219),
    (224, 216), (224, 212), (224, 215), (224, 218),
    (225, 219), (225, 212), (225, 213),
    (226, 218), (226, 214), (226, 213), (226, 215),
    (227, 212), (227, 215), (227, 216),
    (228, 220),
    (229, 220),
    (230, 221),
    (231, 222),
    (232, 223),
    (233, 224);

select setval('research_units_id_seq', (select max(id) from research_units));
select setval('researchers_id_seq', (select max(id) from researchers));
select setval('researcher_affiliations_id_seq', (select max(id) from researcher_affiliations));
select setval('publications_id_seq', (select max(id) from publications));
select setval('topics_id_seq', (select max(id) from topics));
