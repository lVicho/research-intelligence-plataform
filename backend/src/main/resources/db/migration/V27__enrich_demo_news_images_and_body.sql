-- Enrich public portal demo news with fuller editorial bodies and local frontend images.
-- The update is intentionally scoped to the four deterministic demo titles from V26.

update news_articles news
set
    summary = enriched.summary,
    body = enriched.body,
    image_url = enriched.image_url,
    image_alt = enriched.image_alt,
    image_suggestion = enriched.image_suggestion,
    updated_at = timestamp with time zone '2026-06-02 15:30:00+00'
from (
    values
        (
            $$IA local para apoyar comites de evidencia clinica$$,
            $$Investigadores del Grupo de IA Clinica exploran modelos locales para revisar evidencia cientifica con trazabilidad institucional y sin depender de servicios externos.$$,
            $$El Grupo de IA Clinica esta probando flujos de trabajo con modelos locales para ayudar a los comites de evidencia a revisar publicaciones, resumir hallazgos y localizar relaciones entre estudios validados. La iniciativa parte de una premisa simple: las herramientas de apoyo pueden ser utiles si el contexto, las citas y los limites de la respuesta quedan visibles para quienes toman decisiones.

El enfoque combina busqueda semantica, recuperacion de publicaciones institucionales y generacion controlada de respuestas. En lugar de enviar informacion sensible a servicios externos, la arquitectura prioriza ejecuciones locales y proveedores mock por defecto, con la posibilidad de usar modelos desplegados dentro de la infraestructura de la institucion.

Las pruebas iniciales se centran en preguntas frecuentes de revision clinica: que estudios sostienen una linea de trabajo, que temas aparecen de forma recurrente y que investigadores o unidades han producido evidencia relacionada. El objetivo no es sustituir la revision experta, sino reducir el tiempo de exploracion inicial y facilitar que cada respuesta pueda contrastarse con publicaciones concretas.$$,
            $$/news/demo-clinical-ai.png$$,
            $$Equipo clinico revisando paneles de evidencia e IA local en una sala hospitalaria.$$,
            $$Imagen editorial 16:9 sobre IA clinica local, revision de evidencia hospitalaria, privacidad institucional y paneles de apoyo a comites cientificos.$$
        ),
        (
            $$Seguimiento de habitats para la conservacion de grandes felinos$$,
            $$El Grupo de Conservacion de Biodiversidad combina sensores, camaras trampa y analisis ambiental para estudiar habitats de grandes felinos en paisajes protegidos.$$,
            $$El trabajo de conservacion descrito en el portal reune publicaciones sobre biodiversidad, seguimiento de habitats y analisis ambiental aplicado. Los equipos de campo combinan camaras trampa, sensores climaticos y observaciones territoriales para detectar cambios en zonas sensibles y construir series de evidencia comparables.

La linea no se limita a registrar presencia o ausencia de especies. Tambien cruza informacion sobre cobertura vegetal, fragmentacion del habitat, presion humana y variaciones estacionales. Esa lectura integrada ayuda a priorizar nuevas salidas de campo y a identificar que preguntas necesitan mas datos antes de orientar una decision de conservacion.

Desde el portal publico, estas noticias sirven como entrada editorial a investigadores, unidades y publicaciones relacionadas. La intencion es que una persona interesada pueda pasar de una noticia divulgativa a la evidencia institucional validada que la sostiene, sin mezclar este cluster ambiental con otras lineas del dataset como salud digital o analitica institucional.$$,
            $$/news/demo-habitat-monitoring.png$$,
            $$Paisaje protegido con camara trampa y sensor ambiental para seguimiento de habitats.$$,
            $$Imagen editorial 16:9 de paisaje mediterraneo protegido, camara trampa, sensor solar y seguimiento de biodiversidad para grandes felinos.$$
        ),
        (
            $$Salud publica y clima urbano: nuevas lineas de analisis institucional$$,
            $$Publicaciones recientes conectan sensores ambientales, vulnerabilidad urbana e indicadores sanitarios para orientar colaboraciones entre salud publica y clima urbano.$$,
            $$Las publicaciones vinculadas a salud publica y clima urbano permiten explorar relaciones entre temperatura, vulnerabilidad territorial e indicadores sanitarios. El portal agrupa estas evidencias para que la busqueda no empiece desde listas aisladas, sino desde conexiones entre temas, investigadores y unidades con actividad validada.

La linea combina datos de sensores ambientales, analisis espacial y preguntas aplicadas sobre exposicion al calor, episodios extremos y desigualdades urbanas. Esta mirada resulta especialmente util para localizar equipos que trabajan en la interseccion entre epidemiologia, planificacion urbana, ciencia de datos y evaluacion de riesgos.

La noticia destaca como el portal puede apoyar conversaciones tempranas de colaboracion: identificar quienes han publicado sobre el tema, que unidades aparecen asociadas y que piezas de evidencia conviene revisar antes de plantear un proyecto. Las imagenes y el resumen editorial abren la lectura, mientras que los enlaces relacionados llevan al contexto verificable.$$,
            $$/news/demo-urban-health-climate.png$$,
            $$Ciudad con sensores ambientales y capas visuales de clima urbano y salud publica.$$,
            $$Imagen editorial 16:9 de ciudad con sensores ambientales, capas de calor urbano, indicadores de salud publica y equipo investigador.$$
        ),
        (
            $$Mapas tematicos para explorar la actividad investigadora$$,
            $$El portal incorpora lineas tematicas, busqueda inteligente y vistas de relacion para descubrir conexiones entre publicaciones, unidades e investigadores.$$,
            $$Los mapas tematicos ayudan a recorrer la produccion cientifica como una red de evidencias, no solo como un listado cronologico. A partir de temas normalizados, publicaciones recientes y perfiles institucionales, el portal ofrece una lectura publica de como se conectan unidades, investigadores y areas de conocimiento.

Esta pieza demo resume una capacidad transversal del producto: convertir datos validados en recorridos comprensibles. Una persona puede empezar por un tema, saltar a una publicacion, revisar investigadores asociados y volver a una unidad para entender donde se concentra una linea de actividad.

El objetivo editorial es mostrar la investigacion con contexto suficiente para orientar exploracion, contacto y colaboracion. La plataforma evita inventar metricas bibliometricas y prioriza informacion trazable: datos validados, relaciones explicitas y avisos cuando el contexto es parcial o esta acotado por limites tecnicos.$$,
            $$/news/demo-thematic-map.png$$,
            $$Sala institucional con mapa visual de temas, publicaciones y conexiones de investigacion.$$,
            $$Imagen editorial 16:9 de mapa tematico institucional con nodos de publicaciones, unidades, investigadores y lineas de investigacion.$$
        )
) as enriched(title, summary, body, image_url, image_alt, image_suggestion)
where news.title = enriched.title;
