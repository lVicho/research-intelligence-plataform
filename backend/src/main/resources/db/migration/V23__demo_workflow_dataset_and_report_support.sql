-- Rich Spanish demo dataset for validation, data quality, AI search, and report generation.
-- This migration is append-only and keeps existing demo records intact.

update research_units
set organization_scope = 'EXTERNAL',
    visible_in_portal = false,
    updated_at = now()
where name in (
    'Hospital Universitario Central',
    'Hospital General del Sur',
    'Instituto Nacional de Oncologia',
    'Observatorio de Salud Publica de Lisboa',
    'Fundacion Panthera Iberia',
    'Reserva Natural Sierra Verde',
    'Universidad de Nueva York',
    'Clinica Privada San Gabriel'
)
or type in ('HOSPITAL', 'COMPANY', 'FOUNDATION', 'GOVERNMENT_AGENCY');

update research_units
set organization_scope = 'INTERNAL',
    visible_in_portal = true,
    validation_status = 'VALIDATED',
    validation_comment = coalesce(validation_comment, 'Unidad demo validada para portal publico.'),
    validated_at = coalesce(validated_at, now()),
    public_description = coalesce(public_description, 'Facultad interna demo para salud, informatica biomedica e IA clinica.'),
    internal_description = coalesce(internal_description, 'Unidad raiz para demos del cluster hospitalario y de salud digital.'),
    updated_at = now()
where name = 'Facultad de Ciencias de la Salud';

insert into research_units (
    id, name, short_name, type, parent_id, country, city, website, active,
    created_at, updated_at, validation_status, validation_comment, validated_at,
    visible_in_portal, organization_scope, public_description, internal_description,
    featured, sort_order
) values
    (9001, 'Departamento de Informatica Biomedica', 'DIB', 'DEPARTMENT', 2, 'Espana', 'Madrid', 'https://uci.example.edu/dib', true, now(), now(), 'VALIDATED', 'Unidad demo validada para portal publico.', now(), true, 'INTERNAL', 'Departamento universitario dedicado a informatica biomedica, datos clinicos y modelos locales.', 'Unidad interna para demos de IA clinica y hospitales.', true, 10),
    (9002, 'Instituto de Salud Digital', 'ISD', 'INSTITUTE', 2, 'Espana', 'Madrid', 'https://uci.example.edu/salud-digital', true, now(), now(), 'VALIDATED', 'Unidad demo validada para portal publico.', now(), true, 'INTERNAL', 'Instituto orientado a salud digital, privacidad y evaluacion de evidencia clinica.', 'Unidad interna con produccion suficiente para informes.', true, 20),
    (9003, 'Grupo de IA Clinica', 'GIAC', 'RESEARCH_GROUP', 9001, 'Espana', 'Madrid', 'https://uci.example.edu/giac', true, now(), now(), 'VALIDATED', 'Unidad demo validada para portal publico.', now(), true, 'INTERNAL', 'Grupo de investigacion en IA local, busqueda semantica clinica y comites de evidencia.', 'Objetivo principal para informes y busqueda semantica.', true, 30),
    (9004, 'Centro de Estudios Ambientales', 'CEA', 'CENTER', 1, 'Espana', 'Valencia', 'https://uci.example.edu/ambientales', true, now(), now(), 'VALIDATED', 'Unidad demo validada para portal publico.', now(), true, 'INTERNAL', 'Centro universitario sobre salud publica, clima urbano y vulnerabilidad ambiental.', 'Unidad interna para cluster clima/salud publica.', true, 40),
    (9005, 'Grupo de Conservacion de Biodiversidad', 'GCB', 'RESEARCH_GROUP', 9004, 'Espana', 'Sevilla', 'https://uci.example.edu/biodiversidad', true, now(), now(), 'VALIDATED', 'Unidad demo validada para portal publico.', now(), true, 'INTERNAL', 'Grupo centrado en conservacion de grandes felinos, biodiversidad y seguimiento de habitats.', 'Unidad interna para cluster panteras/conservacion.', true, 50),
    (9006, 'Departamento de Humanidades Digitales', 'DHD', 'DEPARTMENT', 1, 'Espana', 'Granada', 'https://uci.example.edu/humanidades-digitales', true, now(), now(), 'VALIDATED', 'Unidad demo validada para portal publico.', now(), true, 'INTERNAL', 'Departamento dedicado a ciencia abierta, colaboracion cientifica y humanidades digitales.', 'Unidad interna para gestion de investigacion.', false, 60),
    (9011, 'Hospital General del Sur', 'HGS', 'HOSPITAL', null, 'Espana', 'Sevilla', 'https://hgs.example.org', true, now(), now(), 'VALIDATED', 'Organizacion externa validada para colaboraciones.', now(), false, 'EXTERNAL', 'Hospital colaborador externo para estudios de IA clinica.', 'No debe aparecer en el directorio publico de unidades internas.', false, null),
    (9012, 'Instituto Nacional de Oncologia', 'INO', 'GOVERNMENT_AGENCY', null, 'Espana', 'Madrid', 'https://ino.example.es', true, now(), now(), 'VALIDATED', 'Organizacion externa validada para colaboraciones.', now(), false, 'EXTERNAL', 'Entidad externa de investigacion oncologica.', 'No debe aparecer en portal/unidades.', false, null),
    (9013, 'Observatorio de Salud Publica de Lisboa', 'OSPL', 'GOVERNMENT_AGENCY', null, 'Portugal', 'Lisboa', 'https://ospl.example.pt', true, now(), now(), 'VALIDATED', 'Organizacion externa validada para colaboraciones.', now(), false, 'EXTERNAL', 'Observatorio externo sobre clima urbano y salud publica.', 'No debe aparecer en portal/unidades.', false, null),
    (9014, 'Fundacion Panthera Iberia', 'FPI', 'FOUNDATION', null, 'Espana', 'Sevilla', 'https://panthera-iberia.example.org', true, now(), now(), 'VALIDATED', 'Organizacion externa validada para colaboraciones.', now(), false, 'EXTERNAL', 'Fundacion externa dedicada a grandes felinos y conservacion.', 'No debe aparecer en portal/unidades.', false, null),
    (9015, 'Reserva Natural Sierra Verde', 'RNSV', 'OTHER', null, 'Espana', 'Jaen', 'https://sierraverde.example.org', true, now(), now(), 'VALIDATED', 'Organizacion externa validada para colaboraciones.', now(), false, 'EXTERNAL', 'Reserva natural externa para seguimiento de habitats.', 'No debe aparecer en portal/unidades.', false, null),
    (9016, 'Universidad de Nueva York', 'UNY', 'UNIVERSITY', null, 'Estados Unidos', 'Nueva York', 'https://uny.example.edu', true, now(), now(), 'VALIDATED', 'Organizacion externa validada para colaboraciones.', now(), false, 'EXTERNAL', 'Universidad externa colaboradora en grafos de conocimiento.', 'No debe aparecer en portal/unidades.', false, null),
    (9017, 'Clinica Privada San Gabriel', 'CPSG', 'HOSPITAL', null, 'Espana', 'Madrid', 'https://sangabriel.example.es', true, now(), now(), 'VALIDATED', 'Organizacion externa validada para colaboraciones.', now(), false, 'EXTERNAL', 'Clinica externa usada en pruebas de normalizacion.', 'No debe aparecer en portal/unidades.', false, null),
    (9018, 'Clinica Privada San Gabriel', 'CPSG-ALT', 'HOSPITAL', null, 'Espana', 'Madrid', 'https://sangabriel-alt.example.es', true, now(), now(), 'PENDING_VALIDATION', 'Posible duplicado externo pendiente de normalizacion.', null, false, 'EXTERNAL', null, 'Duplicado intencional para calidad de datos.', false, null);

insert into researchers (
    id, full_name, display_name, email, orcid, active,
    created_at, updated_at, validation_status, validation_comment, validated_at
) values
    (9001, 'Maya Chen', 'Maya Chen', 'maya.chen@uci.example.edu', '0000-0002-9001-0001', true, now(), now(), 'VALIDATED', 'Perfil demo validado para IA clinica.', now()),
    (9002, 'Laura Gomez', 'Laura Gomez', 'laura.gomez@uci.example.edu', '0000-0002-9001-0002', true, now(), now(), 'VALIDATED', 'Perfil demo validado para evidencia clinica.', now()),
    (9003, 'Ines Carvalho', 'Ines Carvalho', 'ines.carvalho@uci.example.edu', '0000-0002-9001-0003', true, now(), now(), 'VALIDATED', 'Perfil demo validado para colaboracion cientifica.', now()),
    (9004, 'Rosa Marin', 'Rosa Marin', 'rosa.marin@uci.example.edu', '0000-0002-9001-0004', true, now(), now(), 'VALIDATED', 'Perfil demo validado para IA local.', now()),
    (9005, 'Ana Ribeiro', 'Ana Ribeiro', 'ana.ribeiro@uci.example.edu', '0000-0002-9001-0005', true, now(), now(), 'VALIDATED', 'Perfil demo validado para privacidad.', now()),
    (9006, 'Carla Serra', 'Carla Serra', 'carla.serra@uci.example.edu', '0000-0002-9001-0006', true, now(), now(), 'VALIDATED', 'Perfil demo validado para biodiversidad.', now()),
    (9007, 'Diego Fuentes', 'Diego Fuentes', 'diego.fuentes@uci.example.edu', null, true, now(), now(), 'VALIDATED', 'Perfil demo validado; falta ORCID para calidad de datos.', now()),
    (9008, 'Irene Campos', 'Irene Campos', 'irene.campos@uci.example.edu', '0000-0002-9001-0008', false, now(), now(), 'VALIDATED', 'Perfil demo inactivo para pruebas de visibilidad.', now()),
    (9009, 'Ana Ribeiro', 'Ana Ribeiro externo', 'ana.ribeiro.extern@demo.example', null, true, now(), now(), 'PENDING_VALIDATION', 'Posible duplicado de identidad pendiente.', null);

update researchers
set validation_status = 'VALIDATED',
    validation_comment = coalesce(validation_comment, 'Perfil demo validado.'),
    validated_at = coalesce(validated_at, now()),
    active = true,
    updated_at = now()
where full_name in ('Priya Raman', 'Omar Alvarez', 'Jonas Weber', 'Elena Kovacs');

insert into researcher_affiliations (
    id, researcher_id, research_unit_id, role, affiliation_type, start_date, end_date, primary_affiliation,
    created_at, updated_at, validation_status, validation_comment, validated_at
) values
    (9001, 9001, 9003, 'Investigadora principal en IA clinica local', 'LEADER', '2022-01-10', null, true, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9002, 3, 9002, 'Investigadora en salud digital y modelos predictivos', 'MEMBER', '2021-09-01', null, false, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9003, 2, 9003, 'Responsable de gobernanza de datos hospitalarios', 'COLLABORATOR', '2020-03-15', null, false, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9004, 5, 9001, 'Investigador en grafos de conocimiento y genomica', 'MEMBER', '2021-02-01', null, false, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9005, 4, 9004, 'Investigadora en salud publica y clima urbano', 'MEMBER', '2020-09-01', null, false, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9006, 9002, 9002, 'Coordinadora de evidencia clinica', 'LEADER', '2023-01-01', null, true, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9007, 9003, 9006, 'Responsable de colaboracion cientifica', 'LEADER', '2022-05-01', null, true, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9008, 9004, 9003, 'Especialista en modelos locales para comites de evidencia', 'MEMBER', '2022-09-01', null, true, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9009, 9005, 9002, 'Investigadora en privacidad y modelos locales', 'MEMBER', '2021-11-01', null, true, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9010, 9006, 9005, 'Investigadora principal en conservacion de grandes felinos', 'LEADER', '2020-01-01', null, true, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9011, 9007, 9005, 'Investigador en sensores ambientales y habitats', 'MEMBER', '2022-04-01', null, true, now(), now(), 'VALIDATED', 'Afiliacion demo validada.', now()),
    (9012, 9008, 9006, 'Investigadora en ciencia abierta', 'FORMER_MEMBER', '2019-09-01', '2024-12-31', false, now(), now(), 'VALIDATED', 'Afiliacion cerrada para perfil inactivo.', now()),
    (9013, 9009, 9002, 'Colaboradora pendiente de verificacion', 'VISITING', '2026-01-01', null, true, now(), now(), 'PENDING_VALIDATION', 'Pendiente de resolver posible duplicado de identidad.', null);

insert into topics (id, name, normalized_name) values
    (9001, 'IA local', 'ia local'),
    (9002, 'Modelos predictivos', 'modelos predictivos'),
    (9003, 'Privacidad', 'privacidad'),
    (9004, 'Salud digital', 'salud digital'),
    (9005, 'Busqueda semantica', 'busqueda semantica'),
    (9006, 'Evidencia clinica', 'evidencia clinica'),
    (9007, 'Vulnerabilidad', 'vulnerabilidad'),
    (9008, 'Sensores ambientales', 'sensores ambientales'),
    (9009, 'Conservacion de grandes felinos', 'conservacion de grandes felinos'),
    (9010, 'Panteras', 'panteras'),
    (9011, 'Biodiversidad', 'biodiversidad'),
    (9012, 'Seguimiento de habitats', 'seguimiento de habitats'),
    (9013, 'Camaras trampa', 'camaras trampa'),
    (9014, 'Ecologia aplicada', 'ecologia aplicada'),
    (9015, 'Colaboracion cientifica', 'colaboracion cientifica'),
    (9016, 'Calidad de datos', 'calidad de datos'),
    (9017, 'IA clinica', 'ia clinica variante demo'),
    (9018, 'Inteligencia artificial clinica', 'inteligencia artificial clinica'),
    (9019, 'Clinical AI', 'clinical ai'),
    (9020, 'Conservacion de grandes felinos', 'conservacion grandes felinos variante demo'),
    (9021, 'Grandes felinos', 'grandes felinos'),
    (9022, 'Clinical AI', 'clinical-ai-duplicado-demo'),
    (9023, 'Informes automaticos', 'informes automaticos')
on conflict (normalized_name) do nothing;

insert into publishers (id, name, country, website, active, description, created_at, updated_at) values
    (9001, 'Editorial Salud Digital', 'Espana', 'https://editorial-salud-digital.example.es', true, 'Editorial demo de revistas y cuadernos de salud digital.', now(), now()),
    (9002, 'Open Research Tools Press', 'Espana', 'https://ortp.example.org', true, 'Editorial demo para herramientas abiertas de investigacion.', now(), now()),
    (9003, 'Biodiversidad Aplicada Editorial', 'Espana', 'https://biodiversidad-editorial.example.es', true, 'Editorial demo para conservacion aplicada.', now(), now()),
    (9004, 'Universidad Demo Press', 'Espana', 'https://udp.example.edu', true, 'Sello editorial universitario demo.', now(), now()),
    (9005, 'Editorial Incompleta Demo', null, null, true, null, now(), now());

insert into venues (
    id, name, short_name, type_code, issn, eissn, isbn, country, website, active,
    validation_status, description, publisher_id, created_at, updated_at
) values
    (9001, 'Revista de Salud Digital', 'RSD', 'JOURNAL', '2950-2101', null, null, 'Espana', 'https://demo.example.es/revista-salud-digital', true, 'VALIDATED', 'Revista demo de salud digital y modelos predictivos.', 9001, now(), now()),
    (9002, 'Journal of Biomedical Graphs', 'JBG', 'JOURNAL', '2950-2201', null, null, 'Estados Unidos', 'https://demo.example.org/jbg', true, 'VALIDATED', 'Revista demo de grafos biomedicos.', 9004, now(), now()),
    (9003, 'Observatorio Urbano de Salud', 'OUS', 'INSTITUTIONAL_SERIES', null, null, null, 'Portugal', 'https://demo.example.pt/ous', true, 'VALIDATED', 'Serie institucional sin identificador para pruebas de calidad.', 9004, now(), now()),
    (9004, 'Revista de Conservacion Aplicada', 'RCA', 'JOURNAL', '2950-2301', null, null, 'Espana', 'https://demo.example.es/conservacion-aplicada', true, 'VALIDATED', 'Revista demo de conservacion y biodiversidad.', 9003, now(), now()),
    (9005, 'Congreso de Biodiversidad Mediterranea', 'CBM', 'CONFERENCE', null, null, null, 'Espana', 'https://demo.example.es/cbm', true, 'PENDING_VALIDATION', 'Canal pendiente para pruebas de catalogo.', 9003, now(), now()),
    (9006, 'Herramientas Abiertas de Investigacion', 'HAI', 'REPOSITORY', null, null, null, 'Espana', 'https://demo.example.org/hai', true, 'VALIDATED', 'Repositorio demo de herramientas abiertas.', 9002, now(), now()),
    (9007, 'Cuadernos de IA Local', 'CIAL', 'INSTITUTIONAL_SERIES', null, null, null, 'Espana', 'https://demo.example.es/cuadernos-ia-local', false, 'VALIDATED', 'Serie inactiva para pruebas de catalogo.', 9001, now(), now());

insert into scientific_events (
    id, name, edition, event_type_code, start_date, end_date, city, country, organizer, website,
    venue_id, validation_status, description, evidence_url, active, created_at, updated_at
) values
    (9001, 'Seminario de Salud Publica y Clima Urbano 2025', '2025', 'SEMINAR', '2025-11-06', '2025-11-06', 'Lisboa', 'Portugal', 'Observatorio de Salud Publica de Lisboa', 'https://demo.example.pt/seminario-clima-2025', 9003, 'VALIDATED', 'Seminario demo sobre vulnerabilidad, clima urbano y salud publica.', 'https://demo.example.pt/evidencia/seminario-clima-2025', true, now(), now()),
    (9002, 'Simposio de Grafos Biomedicos 2024', '2024', 'SYMPOSIUM', '2024-09-19', '2024-09-20', 'Madrid', 'Espana', 'Universidad Central Iberica y Universidad de Nueva York', 'https://demo.example.es/grafos-biomedicos-2024', 9002, 'VALIDATED', 'Simposio demo de grafos de conocimiento y genomica.', 'https://demo.example.es/evidencia/grafos-2024', true, now(), now()),
    (9003, 'Encuentro de Comites de Evidencia Clinica 2026', '2026', 'WORKSHOP', '2026-03-12', '2026-03-12', 'Madrid', 'Espana', 'Hospital Universitario Central', 'https://demo.example.es/comites-evidencia-2026', 9007, 'PENDING_VALIDATION', null, null, true, now(), now()),
    (9004, 'Jornada sin fechas de Calidad de Datos', '2026', 'SEMINAR', null, null, 'Madrid', 'Espana', 'Universidad Central Iberica', 'https://demo.example.es/calidad-datos-sin-fecha', 9006, 'PENDING_VALIDATION', null, null, true, now(), now());

insert into publications (
    id, title, abstract_text, public_summary, year, publication_date, type, status, doi, source, source_detail, url,
    venue_id, publisher_id, issn, language_code,
    created_at, updated_at, validation_status, validation_comment, validated_at
) values
    (9001, 'IA local para triaje hospitalario con evidencia recuperada', 'Evaluamos modelos locales para triaje hospitalario usando contexto recuperado, privacidad y revision clinica.', 'Resumen publico aceptado sobre IA local y triaje hospitalario.', 2026, '2026-01-12', 'ARTICLE', 'PUBLISHED', '10.5555/rip.demo.2026.001', 'Revista de IA Clinica Local', 'Demo con Hospital Universitario Central', 'https://demo.example.es/pub/9001', 1, 9001, '2950-1001', 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9002, 'Modelos predictivos locales para estancias hospitalarias', 'Se comparan modelos predictivos entrenados localmente para estimar estancias y riesgos operativos en hospitales.', 'Sintesis para portal sobre modelos predictivos locales.', 2026, '2026-02-03', 'ARTICLE', 'PUBLISHED', '10.5555/rip.demo.2026.002', 'Revista de Salud Digital', 'Colaboracion con Hospital General del Sur', 'https://demo.example.es/pub/9002', 9001, 9001, '2950-2101', 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9003, 'Privacidad hospitalaria en busqueda semantica clinica', 'Propuesta de busqueda semantica con embeddings locales, minimizacion de datos y auditoria de consultas clinicas.', 'Resumen publico sobre privacidad y busqueda semantica.', 2025, '2025-09-20', 'ARTICLE', 'PUBLISHED', null, 'Cuadernos de IA Local', 'Registro sin DOI para calidad de datos', 'https://demo.example.es/pub/9003', 9007, 9001, null, 'es', now(), now(), 'VALIDATED', 'Validada con DOI pendiente.', now()),
    (9004, 'Datos multimodales para comites de evidencia clinica', 'Integra texto clinico, imagen y variables estructuradas para preparar sesiones de comites de evidencia.', null, 2025, '2025-06-08', 'REPORT', 'PUBLISHED', '10.5555/rip.demo.2025.004', 'Informes de Salud Digital', 'Falta resumen publico para calidad de datos', 'https://demo.example.es/pub/9004', 9001, 9001, '2950-2101', 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9005, 'Gobernanza de datos en redes hospitalarias locales', 'Analiza roles, trazabilidad y decisiones de gobierno para redes hospitalarias que comparten evidencia sin centralizar datos.', 'Resumen publico sobre gobernanza hospitalaria.', 2024, '2024-11-02', 'REPORT', 'PUBLISHED', '10.5555/rip.demo.2024.005', 'Herramientas Abiertas de Investigacion', 'Incluye organizaciones externas', 'https://demo.example.es/pub/9005', 9006, 9002, null, 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9006, 'Grafos de conocimiento para cohortes genomicas hospitalarias', 'Presenta grafos de conocimiento que conectan variantes genomicas, fenotipos y unidades clinicas.', 'Resumen publico sobre grafos y genomica.', 2024, '2024-05-17', 'ARTICLE', 'PUBLISHED', '10.5555/rip.demo.2024.006', 'Journal of Biomedical Graphs', 'Colaboracion con Universidad de Nueva York', 'https://demo.example.es/pub/9006', 9002, 9004, '2950-2201', 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9007, 'Evaluacion de IA clinica local en hospitales pequenos', 'Estudio completo pendiente de validacion sobre despliegue local de modelos clinicos en hospitales con recursos limitados.', 'Resumen publico preliminar para validacion.', 2026, '2026-04-01', 'ARTICLE', 'ACCEPTED', '10.5555/rip.demo.2026.007', 'Revista de IA Clinica Local', 'Pendiente completa para IA asistida', 'https://demo.example.es/pub/9007', 1, 9001, '2950-1001', 'es', now(), now(), 'PENDING_VALIDATION', 'Pendiente; registro completo para asistencia de validacion.', null),
    (9008, 'Modelo local sin temas para priorizacion de pacientes', 'Registro pendiente con resumen suficiente pero sin temas asociados para probar recomendaciones tematicas.', 'Resumen publico preliminar sin temas.', 2026, '2026-04-10', 'ARTICLE', 'ACCEPTED', '10.5555/rip.demo.2026.008', 'Revista de Salud Digital', 'Pendiente sin temas', 'https://demo.example.es/pub/9008', 9001, 9001, '2950-2101', 'es', now(), now(), 'PENDING_VALIDATION', 'Pendiente; falta normalizar temas antes de validar.', null),
    (9009, 'Resumen incompleto de IA hospitalaria local', null, null, 2026, '2026-04-11', 'ARTICLE', 'DRAFT', null, 'Cuadernos de IA Local', 'Borrador sin resumen ni DOI', 'https://demo.example.es/pub/9009', 9007, 9001, null, 'es', now(), now(), 'DRAFT', 'Borrador del investigador; requiere resumen y DOI si existe.', null),
    (9010, 'IA local para triaje hospitalario con evidencia recuperada', 'Posible duplicado del registro 9001, creado para pruebas de deteccion de duplicados.', null, 2026, '2026-01-12', 'ARTICLE', 'DRAFT', null, 'Revista de IA Clinica Local', 'Duplicado intencional', 'https://demo.example.es/pub/9010', 1, 9001, '2950-1001', 'es', now(), now(), 'PENDING_VALIDATION', 'Posible duplicado; revisar antes de validar.', null),
    (9011, 'Comites de evidencia clinica con modelos locales', 'Manuscrito devuelto por no aportar evidencias suficientes sobre el protocolo de evaluacion.', null, 2025, '2025-10-13', 'CONFERENCE_PAPER', 'ACCEPTED', '10.5555/rip.demo.2025.011', 'Congreso Iberico de Datos Biomedicos', 'Cambios solicitados por el validador', 'https://demo.example.es/pub/9011', 2, 2, null, 'es', now(), now(), 'CHANGES_REQUESTED', 'Anadir enlace de evidencia y aclarar criterios de inclusion.', null),
    (9012, 'Prediccion automatica de decisiones clinicas sin auditoria', 'Registro rechazado por no documentar trazabilidad ni supervision clinica.', null, 2025, '2025-08-04', 'ARTICLE', 'UNKNOWN', null, 'Revista de Salud Digital', 'Rechazada para pruebas de workflow', 'https://demo.example.es/pub/9012', 9001, 9001, '2950-2101', 'es', now(), now(), 'REJECTED', 'Rechazada: falta evidencia verificable y supervision clinica.', now()),
    (9013, 'Panteras ibericas y corredores de biodiversidad', 'Analisis de corredores ecologicos para grandes felinos con datos de reservas y observaciones comunitarias.', 'Resumen publico sobre panteras y corredores de biodiversidad.', 2026, '2026-02-22', 'ARTICLE', 'PUBLISHED', '10.5555/rip.demo.2026.013', 'Revista de Conservacion Aplicada', 'Colaboracion con Reserva Natural Sierra Verde', 'https://demo.example.es/pub/9013', 9004, 9003, '2950-2301', 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9014, 'Camaras trampa para seguimiento de habitats de panteras', 'Metodologia de camaras trampa para estimar presencia de panteras y cambios de habitat.', 'Resumen publico sobre camaras trampa y habitats.', 2026, '2026-03-01', 'ARTICLE', 'PUBLISHED', '10.5555/rip.demo.2026.014', 'Revista de Conservacion Aplicada', 'Datos de Fundacion Panthera Iberia', 'https://demo.example.es/pub/9014', 9004, 9003, '2950-2301', 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9015, 'Biodiversidad aplicada en paisajes de grandes felinos', 'Estudio de biodiversidad aplicada en mosaicos mediterraneos con especial atencion a grandes felinos.', 'Resumen publico sobre biodiversidad aplicada.', 2025, '2025-07-05', 'ARTICLE', 'PUBLISHED', null, 'Congreso de Biodiversidad Mediterranea', 'Sin DOI para calidad de datos', 'https://demo.example.es/pub/9015', 9005, 9003, null, 'es', now(), now(), 'VALIDATED', 'Validada con DOI no disponible.', now()),
    (9016, 'Ecologia aplicada para conectividad de panteras', 'Modelo espacial de conectividad funcional para panteras en areas protegidas y fincas privadas.', 'Resumen publico sobre conectividad ecologica.', 2025, '2025-05-19', 'REPORT', 'PUBLISHED', '10.5555/rip.demo.2025.016', 'Informes de Biodiversidad Iberica', 'Colaboracion externa', 'https://demo.example.es/pub/9016', 9004, 9003, '2950-2301', 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9017, 'Sensores ambientales para habitats de grandes felinos', 'Combina sensores ambientales y observacion de campo para detectar estres de habitat en grandes felinos.', null, 2024, '2024-10-02', 'ARTICLE', 'PUBLISHED', '10.5555/rip.demo.2024.017', 'Revista de Conservacion Aplicada', 'Falta resumen publico', 'https://demo.example.es/pub/9017', 9004, 9003, '2950-2301', 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9018, 'Panteras y camaras trampa en reservas mediterraneas', 'Registro pendiente completo para probar validacion asistida en el cluster de conservacion.', 'Resumen publico preliminar sobre panteras y camaras trampa.', 2026, '2026-06-01', 'CONFERENCE_PAPER', 'ACCEPTED', '10.5555/rip.demo.2026.018', 'Congreso de Biodiversidad Mediterranea', 'Pendiente completa para IA asistida', 'https://demo.example.es/pub/9018', 9005, 9003, null, 'es', now(), now(), 'PENDING_VALIDATION', 'Pendiente; revisar evidencia de campo.', null),
    (9019, 'Panteras sin resumen metodologico', null, null, 2026, '2026-06-02', 'CONFERENCE_PAPER', 'DRAFT', null, 'Congreso de Biodiversidad Mediterranea', 'Borrador sin resumen', 'https://demo.example.es/pub/9019', 9005, 9003, null, 'es', now(), now(), 'DRAFT', 'Borrador: falta resumen metodologico.', null),
    (9020, 'Mapa de biodiversidad sin temas normalizados', 'Registro pendiente sin temas asociados para validar sugerencias de normalizacion en conservacion.', null, 2026, '2026-06-03', 'DATASET', 'DRAFT', '10.5555/rip.demo.2026.020', 'Herramientas Abiertas de Investigacion', 'Pendiente sin temas', 'https://demo.example.es/pub/9020', 9006, 9002, null, 'es', now(), now(), 'PENDING_VALIDATION', 'Pendiente; faltan temas normalizados.', null),
    (9021, 'Avistamientos de panteras sin evidencia verificable', 'Registro rechazado por ausencia de fuentes verificables y descripcion insuficiente.', null, 2025, '2025-04-04', 'REPORT', 'UNKNOWN', null, 'Informes de Biodiversidad Iberica', 'Rechazada para workflow', 'https://demo.example.es/pub/9021', 9004, 9003, '2950-2301', 'es', now(), now(), 'REJECTED', 'Rechazada: evidencia de campo no verificable.', now()),
    (9022, 'Conectividad de habitats con cambios solicitados', 'Analisis prometedor de conectividad, devuelto para precisar ventanas temporales y origen de sensores.', null, 2025, '2025-09-14', 'ARTICLE', 'ACCEPTED', '10.5555/rip.demo.2025.022', 'Revista de Conservacion Aplicada', 'Cambios solicitados', 'https://demo.example.es/pub/9022', 9004, 9003, '2950-2301', 'es', now(), now(), 'CHANGES_REQUESTED', 'Aclarar origen de sensores y permisos de la reserva.', null),
    (9023, 'Salud publica y clima urbano en barrios vulnerables', 'Estudio de vulnerabilidad al calor urbano y sus efectos en indicadores de salud publica.', 'Resumen publico sobre clima urbano y vulnerabilidad.', 2026, '2026-01-28', 'ARTICLE', 'PUBLISHED', '10.5555/rip.demo.2026.023', 'Observatorio Urbano de Salud', 'Colaboracion con Lisboa', 'https://demo.example.es/pub/9023', 9003, 9004, null, 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9024, 'Sensores ambientales para alerta temprana de calor urbano', 'Red de sensores ambientales para detectar calor urbano y orientar acciones de salud publica.', 'Resumen publico sobre sensores y calor urbano.', 2025, '2025-07-11', 'DATASET', 'PUBLISHED', '10.5555/rip.demo.2025.024', 'Herramientas Abiertas de Investigacion', 'Dataset demo', 'https://demo.example.es/pub/9024', 9006, 9002, null, 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9025, 'Vulnerabilidad social ante olas de calor urbanas', 'Analiza vulnerabilidad social, acceso a zonas verdes y exposicion termica en ciudades mediterraneas.', null, 2025, '2025-03-16', 'ARTICLE', 'PUBLISHED', null, 'Observatorio Urbano de Salud', 'Sin DOI ni resumen publico', 'https://demo.example.es/pub/9025', 9003, 9004, null, 'es', now(), now(), 'VALIDATED', 'Validada con DOI no disponible.', now()),
    (9026, 'Clima urbano y salud respiratoria en episodios extremos', 'Relaciona islas de calor, contaminacion y consultas respiratorias con apoyo de sensores ambientales.', 'Resumen publico sobre clima y salud respiratoria.', 2024, '2024-12-02', 'REPORT', 'PUBLISHED', '10.5555/rip.demo.2024.026', 'Observatorio Urbano de Salud', 'Informe externo', 'https://demo.example.es/pub/9026', 9003, 9004, null, 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9027, 'Mapa de riesgo climatico urbano pendiente', 'Registro pendiente con evidencia suficiente para validar mapas de riesgo climatico urbano.', 'Resumen publico preliminar de mapa de riesgo.', 2026, '2026-04-22', 'REPORT', 'ACCEPTED', '10.5555/rip.demo.2026.027', 'Observatorio Urbano de Salud', 'Pendiente completa', 'https://demo.example.es/pub/9027', 9003, 9004, null, 'es', now(), now(), 'PENDING_VALIDATION', 'Pendiente; validar fuente del observatorio externo.', null),
    (9028, 'Registro de clima urbano sin resumen', null, null, 2026, '2026-04-23', 'REPORT', 'DRAFT', null, 'Observatorio Urbano de Salud', 'Borrador sin resumen', 'https://demo.example.es/pub/9028', 9003, 9004, null, 'es', now(), now(), 'DRAFT', 'Borrador: falta resumen y metodo.', null),
    (9029, 'Sensores ambientales sin cobertura verificable', 'Registro rechazado por no aportar cobertura espacial ni evidencia de calibracion.', null, 2025, '2025-08-18', 'DATASET', 'UNKNOWN', null, 'Herramientas Abiertas de Investigacion', 'Rechazada para workflow', 'https://demo.example.es/pub/9029', 9006, 9002, null, 'es', now(), now(), 'REJECTED', 'Rechazada: falta evidencia de calibracion.', now()),
    (9030, 'Vulnerabilidad climatica con cambios solicitados', 'Estudio devuelto para aclarar definicion de vulnerabilidad y cobertura temporal de sensores.', null, 2025, '2025-09-30', 'ARTICLE', 'ACCEPTED', '10.5555/rip.demo.2025.030', 'Observatorio Urbano de Salud', 'Cambios solicitados', 'https://demo.example.es/pub/9030', 9003, 9004, null, 'es', now(), now(), 'CHANGES_REQUESTED', 'Precisar indicador de vulnerabilidad y evidencia de sensores.', null),
    (9031, 'Analitica de investigacion para comites de direccion', 'Describe tableros de analitica de investigacion para comites de direccion con evidencia trazable.', 'Resumen publico sobre analitica institucional.', 2026, '2026-02-15', 'REPORT', 'PUBLISHED', '10.5555/rip.demo.2026.031', 'Herramientas Abiertas de Investigacion', 'Informe institucional', 'https://demo.example.es/pub/9031', 9006, 9002, null, 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9032, 'Colaboracion cientifica y mapas tematicos institucionales', 'Propone mapas tematicos para detectar colaboracion cientifica y areas emergentes.', 'Resumen publico sobre colaboracion y mapas tematicos.', 2025, '2025-06-21', 'ARTICLE', 'PUBLISHED', '10.5555/rip.demo.2025.032', 'Herramientas Abiertas de Investigacion', 'Ciencia abierta', 'https://demo.example.es/pub/9032', 9006, 9002, null, 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9033, 'Calidad de datos para informes automaticos', 'Metodologia para detectar registros sin DOI, resumen, temas o autores resueltos antes de generar informes automaticos.', 'Resumen publico sobre calidad de datos e informes.', 2025, '2025-10-01', 'ARTICLE', 'PUBLISHED', null, 'Cuadernos de IA Local', 'Sin DOI para calidad', 'https://demo.example.es/pub/9033', 9007, 9001, null, 'es', now(), now(), 'VALIDATED', 'Validada con DOI no disponible.', now()),
    (9034, 'Informes automaticos con evidencia citada', 'Describe generacion de informes Markdown con citas a publicaciones y advertencias por contexto insuficiente.', 'Resumen publico sobre informes automaticos.', 2026, '2026-03-08', 'SOFTWARE', 'PUBLISHED', '10.5555/rip.demo.2026.034', 'Herramientas Abiertas de Investigacion', 'Software demo', 'https://demo.example.es/pub/9034', 9006, 9002, null, 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9035, 'Gestion de temas normalizados en repositorios institucionales', 'Analiza variantes de temas y decisiones de fusion en catalogos institucionales.', null, 2024, '2024-09-09', 'ARTICLE', 'PUBLISHED', '10.5555/rip.demo.2024.035', 'Herramientas Abiertas de Investigacion', 'Falta resumen publico', 'https://demo.example.es/pub/9035', 9006, 9002, null, 'es', now(), now(), 'VALIDATED', 'Validada para demo publica.', now()),
    (9036, 'Analitica de investigacion pendiente para panel institucional', 'Registro pendiente completo para validar un informe de actividad institucional.', 'Resumen publico preliminar de panel institucional.', 2026, '2026-05-06', 'REPORT', 'ACCEPTED', '10.5555/rip.demo.2026.036', 'Herramientas Abiertas de Investigacion', 'Pendiente completa', 'https://demo.example.es/pub/9036', 9006, 9002, null, 'es', now(), now(), 'PENDING_VALIDATION', 'Pendiente; validar alcance institucional.', null),
    (9037, 'Mapa tematico sin temas asociados', 'Registro pendiente con resumen pero sin temas enlazados para probar calidad y recomendaciones.', null, 2026, '2026-05-07', 'REPORT', 'ACCEPTED', '10.5555/rip.demo.2026.037', 'Herramientas Abiertas de Investigacion', 'Pendiente sin temas', 'https://demo.example.es/pub/9037', 9006, 9002, null, 'es', now(), now(), 'PENDING_VALIDATION', 'Pendiente; faltan temas normalizados.', null),
    (9038, 'Borrador de informe automatico sin resumen', null, null, 2026, '2026-05-08', 'REPORT', 'DRAFT', null, 'Herramientas Abiertas de Investigacion', 'Borrador sin resumen', 'https://demo.example.es/pub/9038', 9006, 9002, null, 'es', now(), now(), 'DRAFT', 'Borrador: falta resumen y evidencia.', null),
    (9039, 'Indicadores de colaboracion sin fuente verificable', 'Registro rechazado porque las colaboraciones no estaban respaldadas por publicaciones trazables.', null, 2025, '2025-11-11', 'REPORT', 'UNKNOWN', null, 'Herramientas Abiertas de Investigacion', 'Rechazada para workflow', 'https://demo.example.es/pub/9039', 9006, 9002, null, 'es', now(), now(), 'REJECTED', 'Rechazada: no hay evidencia trazable para las relaciones.', now()),
    (9040, 'Calidad de datos con cambios solicitados', 'Informe devuelto para separar incidencias automaticas de decisiones editoriales.', null, 2025, '2025-12-04', 'REPORT', 'ACCEPTED', '10.5555/rip.demo.2025.040', 'Herramientas Abiertas de Investigacion', 'Cambios solicitados', 'https://demo.example.es/pub/9040', 9006, 9002, null, 'es', now(), now(), 'CHANGES_REQUESTED', 'Separar incidencias de calidad de recomendaciones editoriales.', null);

insert into publication_authors (publication_id, researcher_id, external_author_name, external_affiliation, author_order, corresponding_author) values
    (9001, 9001, null, null, 1, true), (9001, 2, null, null, 2, false), (9001, null, 'Lucia Ferrer', 'Hospital Universitario Central', 3, false),
    (9002, 3, null, null, 1, true), (9002, 9002, null, null, 2, false), (9002, null, 'Miguel Arroyo', 'Hospital General del Sur', 3, false),
    (9003, 9005, null, null, 1, true), (9003, 9001, null, null, 2, false),
    (9004, 9002, null, null, 1, true), (9004, 9004, null, null, 2, false),
    (9005, 2, null, null, 1, true), (9005, null, 'Helena Costa', 'Observatorio de Salud Publica de Lisboa', 2, false),
    (9006, 5, null, null, 1, true), (9006, null, 'Nora Klein', 'Universidad de Nueva York', 2, false),
    (9007, 9001, null, null, 1, true), (9007, 3, null, null, 2, false),
    (9008, 9004, null, null, 1, true), (9008, null, 'Autor externo sin resolver', null, 2, false),
    (9009, 9001, null, null, 1, true),
    (9010, 9001, null, null, 1, true), (9010, 2, null, null, 2, false),
    (9011, 9002, null, null, 1, true), (9011, 9005, null, null, 2, false),
    (9012, 3, null, null, 1, true), (9012, null, 'Carlos Moreno', null, 2, false),
    (9013, 9006, null, null, 1, true), (9013, 9007, null, null, 2, false), (9013, null, 'Marta Saez', 'Reserva Natural Sierra Verde', 3, false),
    (9014, 9007, null, null, 1, true), (9014, 9006, null, null, 2, false),
    (9015, 9006, null, null, 1, true), (9015, null, 'Pablo Nunes', 'Fundacion Panthera Iberia', 2, false),
    (9016, 9006, null, null, 1, true), (9016, 9007, null, null, 2, false),
    (9017, 9007, null, null, 1, true), (9017, null, 'Equipo Sierra Verde', 'Reserva Natural Sierra Verde', 2, false),
    (9018, 9006, null, null, 1, true), (9018, 9007, null, null, 2, false),
    (9019, 9006, null, null, 1, true),
    (9020, 9007, null, null, 1, true),
    (9021, 9006, null, null, 1, true), (9021, null, 'Observador anonimo', null, 2, false),
    (9022, 9007, null, null, 1, true), (9022, 9006, null, null, 2, false),
    (9023, 4, null, null, 1, true), (9023, null, 'Joao Pereira', 'Observatorio de Salud Publica de Lisboa', 2, false),
    (9024, 9007, null, null, 1, true), (9024, 4, null, null, 2, false),
    (9025, 4, null, null, 1, true), (9025, null, 'Sofia Almeida', 'Observatorio de Salud Publica de Lisboa', 2, false),
    (9026, 4, null, null, 1, true), (9026, 9007, null, null, 2, false),
    (9027, 4, null, null, 1, true),
    (9028, 4, null, null, 1, true),
    (9029, 9007, null, null, 1, true), (9029, null, 'Tecnico externo', null, 2, false),
    (9030, 4, null, null, 1, true), (9030, 9007, null, null, 2, false),
    (9031, 9003, null, null, 1, true), (9031, 9008, null, null, 2, false),
    (9032, 9003, null, null, 1, true), (9032, 9001, null, null, 2, false),
    (9033, 9003, null, null, 1, true), (9033, 2, null, null, 2, false),
    (9034, 9004, null, null, 1, true), (9034, 9003, null, null, 2, false),
    (9035, 9003, null, null, 1, true),
    (9036, 9003, null, null, 1, true), (9036, 9008, null, null, 2, false),
    (9037, 9003, null, null, 1, true),
    (9038, 9003, null, null, 1, true),
    (9039, 9003, null, null, 1, true), (9039, null, 'Consultor externo', null, 2, false),
    (9040, 9003, null, null, 1, true), (9040, 9004, null, null, 2, false);

insert into publication_topics (publication_id, topic_id)
select link.publication_id, topic.id
from (values
    (9001, 'ia local'), (9001, 'ia clinica'), (9001, 'evidencia clinica'),
    (9002, 'modelos predictivos'), (9002, 'salud digital'), (9002, 'ia local'),
    (9003, 'privacidad'), (9003, 'busqueda semantica'), (9003, 'ia local'),
    (9004, 'datos multimodales'), (9004, 'evidencia clinica'), (9004, 'ia clinica'),
    (9005, 'gobernanza de datos'), (9005, 'privacidad'), (9005, 'salud digital'),
    (9006, 'grafos de conocimiento'), (9006, 'genomica'), (9006, 'busqueda semantica'),
    (9007, 'ia clinica'), (9007, 'modelos predictivos'), (9007, 'salud digital'),
    (9009, 'ia local'),
    (9010, 'ia local'), (9010, 'ia clinica'), (9010, 'evidencia clinica'),
    (9011, 'evidencia clinica'), (9011, 'ia local'),
    (9012, 'modelos predictivos'),
    (9013, 'panteras'), (9013, 'biodiversidad'), (9013, 'conservacion de grandes felinos'),
    (9014, 'camaras trampa'), (9014, 'seguimiento de habitats'), (9014, 'panteras'),
    (9015, 'biodiversidad'), (9015, 'grandes felinos'),
    (9016, 'ecologia aplicada'), (9016, 'panteras'), (9016, 'seguimiento de habitats'),
    (9017, 'sensores ambientales'), (9017, 'seguimiento de habitats'), (9017, 'biodiversidad'),
    (9018, 'panteras'), (9018, 'camaras trampa'), (9018, 'conservacion de grandes felinos'),
    (9019, 'panteras'),
    (9021, 'panteras'),
    (9022, 'seguimiento de habitats'), (9022, 'ecologia aplicada'),
    (9023, 'salud publica'), (9023, 'clima urbano'), (9023, 'vulnerabilidad'),
    (9024, 'sensores ambientales'), (9024, 'clima urbano'), (9024, 'salud publica'),
    (9025, 'vulnerabilidad'), (9025, 'clima urbano'),
    (9026, 'salud publica'), (9026, 'clima urbano'), (9026, 'sensores ambientales'),
    (9027, 'vulnerabilidad'), (9027, 'clima urbano'),
    (9028, 'clima urbano'),
    (9029, 'sensores ambientales'),
    (9030, 'vulnerabilidad'), (9030, 'sensores ambientales'),
    (9031, 'analitica de investigacion'), (9031, 'calidad de datos'),
    (9032, 'colaboracion cientifica'), (9032, 'mapas tematicos'), (9032, 'analitica de investigacion'),
    (9033, 'calidad de datos'), (9033, 'informes automaticos'),
    (9034, 'informes automaticos'), (9034, 'busqueda semantica'), (9034, 'calidad de datos'),
    (9035, 'mapas tematicos'), (9035, 'calidad de datos'),
    (9036, 'analitica de investigacion'), (9036, 'informes automaticos'),
    (9038, 'informes automaticos'),
    (9039, 'colaboracion cientifica'),
    (9040, 'calidad de datos'), (9040, 'informes automaticos')
) as link(publication_id, normalized_name)
join topics topic on topic.normalized_name = link.normalized_name
on conflict do nothing;

insert into event_participations (
    id, event_id, researcher_id, research_unit_id, participation_type_code, title, description, evidence_url,
    participation_date, related_publication_id, validation_status, submitted_at, validated_at, validation_comment,
    created_at, updated_at
) values
    (9001, 2, 9001, 9003, 'ORAL_PRESENTATION', 'IA local en hospitales con evidencia citada', 'Ponencia oral sobre recuperacion de evidencia para modelos locales.', 'https://demo.example.es/evidencia/participacion-9001', '2026-04-22', 9001, 'VALIDATED', now(), now(), 'Participacion validada para demo publica.', now(), now()),
    (9002, 2, 5, 9001, 'POSTER', 'Grafos de conocimiento en genomica clinica', 'Poster sobre grafos biomedicos y cohortes genomicas.', 'https://demo.example.es/evidencia/participacion-9002', '2026-04-23', 9006, 'VALIDATED', now(), now(), 'Participacion validada para demo publica.', now(), now()),
    (9003, 3, 9002, 9002, 'KEYNOTE', 'Comites de evidencia clinica y modelos locales', 'Conferencia invitada pendiente por relacion con evento aun no validado.', null, '2026-03-12', 9011, 'PENDING_VALIDATION', now(), null, null, now(), now()),
    (9004, 3, 9004, 9003, 'SCIENTIFIC_COMMITTEE', 'Revision cientifica de IA local hospitalaria', 'Participacion en comite cientifico con evidencia incompleta.', null, '2026-03-12', null, 'CHANGES_REQUESTED', now(), null, 'Anadir evidenceUrl y aclarar relacion con el evento.', now(), now()),
    (9005, 9001, 4, 9004, 'ORGANIZING_COMMITTEE', 'Organizacion del seminario de salud publica y clima urbano', 'Comite organizador del seminario.', 'https://demo.example.pt/evidencia/comite-9005', '2025-11-06', 9023, 'VALIDATED', now(), now(), 'Participacion validada para demo publica.', now(), now()),
    (9006, 3, 9005, 9002, 'WORKSHOP_SPEAKER', 'Privacidad de datos en modelos locales', 'Taller pendiente con evento ambiguo y evidencia parcial.', null, '2026-03-12', 9003, 'PENDING_VALIDATION', now(), null, null, now(), now()),
    (9007, 3, 9001, 9003, 'ATTENDANCE', 'Asistencia al encuentro de comites de evidencia', 'Registro en borrador creado por investigador.', null, '2026-03-12', null, 'DRAFT', null, null, null, now(), now()),
    (9008, 3, 9009, 9002, 'POSTER', 'Poster de privacidad sin afiliacion resuelta', 'Registro rechazado por identidad duplicada no resuelta.', null, '2026-03-12', 9008, 'REJECTED', now(), now(), 'Rechazada: resolver identidad y evidencia antes de reenviar.', now(), now());

update report_templates
set active = true,
    updated_at = now()
where name in (
    'Informe anual de unidad',
    'Informe de investigador',
    'Informe de linea tematica',
    'Informe de actividad institucional',
    'Informe de calidad de datos',
    'Informe para comite de direccion'
);

select setval('research_units_id_seq', greatest((select max(id) from research_units), 9018));
select setval('researchers_id_seq', greatest((select max(id) from researchers), 9009));
select setval('researcher_affiliations_id_seq', greatest((select max(id) from researcher_affiliations), 9013));
select setval('topics_id_seq', greatest((select max(id) from topics), 9023));
select setval('publishers_id_seq', greatest((select max(id) from publishers), 9005));
select setval('venues_id_seq', greatest((select max(id) from venues), 9007));
select setval('scientific_events_id_seq', greatest((select max(id) from scientific_events), 9004));
select setval('publications_id_seq', greatest((select max(id) from publications), 9040));
select setval('event_participations_id_seq', greatest((select max(id) from event_participations), 9008));
