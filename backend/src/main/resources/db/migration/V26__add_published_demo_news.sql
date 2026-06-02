-- Published demo news for the public portal home.
-- The inserts are idempotent by title and do not remove or overwrite user-created news.

insert into news_articles (
    title,
    summary,
    body,
    status,
    image_url,
    image_alt,
    image_suggestion,
    published_at,
    created_at,
    updated_at
)
select *
from (
    values
        (
            'IA local para apoyar comites de evidencia clinica',
            'Investigadores del Grupo de IA Clinica exploran modelos locales para revisar evidencia cientifica sin depender de servicios externos.',
            'El trabajo combina busqueda semantica, modelos locales y revision de publicaciones clinicas para apoyar procesos de analisis de evidencia en entornos hospitalarios. La linea prioriza la privacidad de los datos y el uso de infraestructura institucional.',
            'PUBLISHED',
            null,
            'Visual abstracto sobre inteligencia artificial clinica en hospitales',
            'Escena editorial sobre IA clinica local, revision de evidencia hospitalaria y privacidad institucional.',
            timestamp with time zone '2026-05-28 09:00:00+00',
            timestamp with time zone '2026-05-28 09:00:00+00',
            timestamp with time zone '2026-05-28 09:00:00+00'
        ),
        (
            'Seguimiento de habitats para la conservacion de grandes felinos',
            'El Grupo de Conservacion de Biodiversidad combina sensores, camaras trampa y analisis ambiental para estudiar la presencia de panteras en paisajes protegidos.',
            'La actividad reciente del portal muestra publicaciones sobre biodiversidad, seguimiento de habitats y conservacion aplicada. Estas investigaciones conectan datos ambientales, observacion de campo y analisis institucional.',
            'PUBLISHED',
            null,
            'Paisaje natural asociado a conservacion de grandes felinos',
            'Paisaje mediterraneo con sensores ambientales y camaras trampa para conservacion de grandes felinos.',
            timestamp with time zone '2026-05-27 09:00:00+00',
            timestamp with time zone '2026-05-27 09:00:00+00',
            timestamp with time zone '2026-05-27 09:00:00+00'
        ),
        (
            'Salud publica y clima urbano: nuevas lineas de analisis institucional',
            'Publicaciones recientes conectan sensores ambientales, vulnerabilidad urbana y episodios de salud publica para orientar nuevas colaboraciones.',
            'Las publicaciones vinculadas a salud publica y clima urbano permiten explorar relaciones entre temperatura, vulnerabilidad territorial e indicadores sanitarios. El portal facilita localizar investigadores, unidades y evidencias asociadas.',
            'PUBLISHED',
            null,
            'Vista urbana con indicadores ambientales y salud publica',
            'Vista urbana con capas de indicadores ambientales, vulnerabilidad territorial y salud publica.',
            timestamp with time zone '2026-05-26 09:00:00+00',
            timestamp with time zone '2026-05-26 09:00:00+00',
            timestamp with time zone '2026-05-26 09:00:00+00'
        ),
        (
            'Mapas tematicos para explorar la actividad investigadora',
            'El portal incorpora lineas tematicas y busqueda inteligente para descubrir conexiones entre publicaciones, unidades e investigadores.',
            'Las herramientas de exploracion permiten recorrer la produccion cientifica por temas, publicaciones recientes y perfiles expertos. El objetivo es facilitar una lectura publica y contextualizada de la actividad investigadora institucional.',
            'PUBLISHED',
            null,
            'Mapa tematico de actividad investigadora institucional',
            'Mapa editorial con lineas tematicas, publicaciones y conexiones entre unidades e investigadores.',
            timestamp with time zone '2026-05-25 09:00:00+00',
            timestamp with time zone '2026-05-25 09:00:00+00',
            timestamp with time zone '2026-05-25 09:00:00+00'
        )
) as demo_news(title, summary, body, status, image_url, image_alt, image_suggestion, published_at, created_at, updated_at)
where not exists (
    select 1
    from news_articles existing
    where existing.title = demo_news.title
);

insert into news_article_publications (news_article_id, publication_id)
select news.id, relation.publication_id
from news_articles news
join (
    values
        ('IA local para apoyar comites de evidencia clinica', 'El trabajo combina busqueda semantica, modelos locales y revision de publicaciones clinicas para apoyar procesos de analisis de evidencia en entornos hospitalarios. La linea prioriza la privacidad de los datos y el uso de infraestructura institucional.', 9001),
        ('IA local para apoyar comites de evidencia clinica', 'El trabajo combina busqueda semantica, modelos locales y revision de publicaciones clinicas para apoyar procesos de analisis de evidencia en entornos hospitalarios. La linea prioriza la privacidad de los datos y el uso de infraestructura institucional.', 9004),
        ('Seguimiento de habitats para la conservacion de grandes felinos', 'La actividad reciente del portal muestra publicaciones sobre biodiversidad, seguimiento de habitats y conservacion aplicada. Estas investigaciones conectan datos ambientales, observacion de campo y analisis institucional.', 9013),
        ('Seguimiento de habitats para la conservacion de grandes felinos', 'La actividad reciente del portal muestra publicaciones sobre biodiversidad, seguimiento de habitats y conservacion aplicada. Estas investigaciones conectan datos ambientales, observacion de campo y analisis institucional.', 9014),
        ('Seguimiento de habitats para la conservacion de grandes felinos', 'La actividad reciente del portal muestra publicaciones sobre biodiversidad, seguimiento de habitats y conservacion aplicada. Estas investigaciones conectan datos ambientales, observacion de campo y analisis institucional.', 9015),
        ('Salud publica y clima urbano: nuevas lineas de analisis institucional', 'Las publicaciones vinculadas a salud publica y clima urbano permiten explorar relaciones entre temperatura, vulnerabilidad territorial e indicadores sanitarios. El portal facilita localizar investigadores, unidades y evidencias asociadas.', 9023),
        ('Salud publica y clima urbano: nuevas lineas de analisis institucional', 'Las publicaciones vinculadas a salud publica y clima urbano permiten explorar relaciones entre temperatura, vulnerabilidad territorial e indicadores sanitarios. El portal facilita localizar investigadores, unidades y evidencias asociadas.', 9024),
        ('Salud publica y clima urbano: nuevas lineas de analisis institucional', 'Las publicaciones vinculadas a salud publica y clima urbano permiten explorar relaciones entre temperatura, vulnerabilidad territorial e indicadores sanitarios. El portal facilita localizar investigadores, unidades y evidencias asociadas.', 9026),
        ('Mapas tematicos para explorar la actividad investigadora', 'Las herramientas de exploracion permiten recorrer la produccion cientifica por temas, publicaciones recientes y perfiles expertos. El objetivo es facilitar una lectura publica y contextualizada de la actividad investigadora institucional.', 9031),
        ('Mapas tematicos para explorar la actividad investigadora', 'Las herramientas de exploracion permiten recorrer la produccion cientifica por temas, publicaciones recientes y perfiles expertos. El objetivo es facilitar una lectura publica y contextualizada de la actividad investigadora institucional.', 9032),
        ('Mapas tematicos para explorar la actividad investigadora', 'Las herramientas de exploracion permiten recorrer la produccion cientifica por temas, publicaciones recientes y perfiles expertos. El objetivo es facilitar una lectura publica y contextualizada de la actividad investigadora institucional.', 9034)
) as relation(title, body, publication_id) on news.title = relation.title and news.body = relation.body
join publications publication on publication.id = relation.publication_id and publication.validation_status = 'VALIDATED'
on conflict do nothing;

insert into news_article_researchers (news_article_id, researcher_id)
select news.id, relation.researcher_id
from news_articles news
join (
    values
        ('IA local para apoyar comites de evidencia clinica', 'El trabajo combina busqueda semantica, modelos locales y revision de publicaciones clinicas para apoyar procesos de analisis de evidencia en entornos hospitalarios. La linea prioriza la privacidad de los datos y el uso de infraestructura institucional.', 9001),
        ('IA local para apoyar comites de evidencia clinica', 'El trabajo combina busqueda semantica, modelos locales y revision de publicaciones clinicas para apoyar procesos de analisis de evidencia en entornos hospitalarios. La linea prioriza la privacidad de los datos y el uso de infraestructura institucional.', 9004),
        ('Seguimiento de habitats para la conservacion de grandes felinos', 'La actividad reciente del portal muestra publicaciones sobre biodiversidad, seguimiento de habitats y conservacion aplicada. Estas investigaciones conectan datos ambientales, observacion de campo y analisis institucional.', 9006),
        ('Seguimiento de habitats para la conservacion de grandes felinos', 'La actividad reciente del portal muestra publicaciones sobre biodiversidad, seguimiento de habitats y conservacion aplicada. Estas investigaciones conectan datos ambientales, observacion de campo y analisis institucional.', 9007),
        ('Salud publica y clima urbano: nuevas lineas de analisis institucional', 'Las publicaciones vinculadas a salud publica y clima urbano permiten explorar relaciones entre temperatura, vulnerabilidad territorial e indicadores sanitarios. El portal facilita localizar investigadores, unidades y evidencias asociadas.', 4),
        ('Salud publica y clima urbano: nuevas lineas de analisis institucional', 'Las publicaciones vinculadas a salud publica y clima urbano permiten explorar relaciones entre temperatura, vulnerabilidad territorial e indicadores sanitarios. El portal facilita localizar investigadores, unidades y evidencias asociadas.', 9007),
        ('Mapas tematicos para explorar la actividad investigadora', 'Las herramientas de exploracion permiten recorrer la produccion cientifica por temas, publicaciones recientes y perfiles expertos. El objetivo es facilitar una lectura publica y contextualizada de la actividad investigadora institucional.', 9003)
) as relation(title, body, researcher_id) on news.title = relation.title and news.body = relation.body
join researchers researcher on researcher.id = relation.researcher_id
    and researcher.validation_status = 'VALIDATED'
    and researcher.active = true
on conflict do nothing;

insert into news_article_research_units (news_article_id, research_unit_id)
select news.id, relation.research_unit_id
from news_articles news
join (
    values
        ('IA local para apoyar comites de evidencia clinica', 'El trabajo combina busqueda semantica, modelos locales y revision de publicaciones clinicas para apoyar procesos de analisis de evidencia en entornos hospitalarios. La linea prioriza la privacidad de los datos y el uso de infraestructura institucional.', 9003),
        ('Seguimiento de habitats para la conservacion de grandes felinos', 'La actividad reciente del portal muestra publicaciones sobre biodiversidad, seguimiento de habitats y conservacion aplicada. Estas investigaciones conectan datos ambientales, observacion de campo y analisis institucional.', 9005),
        ('Salud publica y clima urbano: nuevas lineas de analisis institucional', 'Las publicaciones vinculadas a salud publica y clima urbano permiten explorar relaciones entre temperatura, vulnerabilidad territorial e indicadores sanitarios. El portal facilita localizar investigadores, unidades y evidencias asociadas.', 9004),
        ('Mapas tematicos para explorar la actividad investigadora', 'Las herramientas de exploracion permiten recorrer la produccion cientifica por temas, publicaciones recientes y perfiles expertos. El objetivo es facilitar una lectura publica y contextualizada de la actividad investigadora institucional.', 9006)
) as relation(title, body, research_unit_id) on news.title = relation.title and news.body = relation.body
join research_units unit on unit.id = relation.research_unit_id
    and unit.validation_status = 'VALIDATED'
    and unit.visible_in_portal = true
    and unit.organization_scope = 'INTERNAL'
on conflict do nothing;
