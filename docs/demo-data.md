# Datos Demo

La base demo se carga con Flyway. El refuerzo principal esta en:

- `backend/src/main/resources/db/migration/V23__demo_workflow_dataset_and_report_support.sql`

La migracion es aditiva: no reemplaza todo el dataset anterior. En una base limpia, el corpus queda con unas 489 publicaciones demo, de las cuales 40 pertenecen al nuevo bloque de workflows.

## Usuarios Demo

Todos los usuarios son locales y de demo. La contrasena compartida es `demo123`.

| Usuario | Rol | Vinculo |
| --- | --- | --- |
| `admin@demo.local` | `ADMIN` | Administracion completa |
| `validator@demo.local` | `VALIDATOR` | Bandeja de validacion |
| `researcher@demo.local` | `RESEARCHER` | Investigador demo historico |
| `researcher1@demo.local` | `RESEARCHER` | Maya Chen |
| `researcher2@demo.local` | `RESEARCHER` | Carla Serra |
| `researcher3@demo.local` | `RESEARCHER` | Ines Carvalho |

## Cobertura Del Dataset

El dataset incluye unidades internas de la Universidad Central Iberica para salud, informatica biomedica, IA clinica, salud digital, estudios ambientales, conservacion de biodiversidad y humanidades digitales.

Tambien incluye organizaciones externas que no deben aparecer en `/portal/unidades` porque tienen `organization_scope='EXTERNAL'` y `visible_in_portal=false`: Hospital Universitario Central, Hospital General del Sur, Instituto Nacional de Oncologia, Observatorio de Salud Publica de Lisboa, Fundacion Panthera Iberia, Reserva Natural Sierra Verde, Universidad de Nueva York y Clinica Privada San Gabriel.

Clusters semanticos principales:

- Hospital/IA: IA clinica, IA local, modelos predictivos, privacidad, datos multimodales, busqueda semantica, evidencia clinica, grafos de conocimiento y genomica.
- Salud publica/clima: salud publica, clima urbano, vulnerabilidad y sensores ambientales.
- Panteras/conservacion: conservacion de grandes felinos, panteras, biodiversidad, seguimiento de habitats, camaras trampa y ecologia aplicada.
- Gestion de investigacion: analitica de investigacion, colaboracion cientifica, mapas tematicos, calidad de datos e informes automaticos.

## Estados De Validacion

Hay registros en todos los estados principales:

- `VALIDATED`
- `PENDING_VALIDATION`
- `REJECTED`
- `DRAFT`
- `CHANGES_REQUESTED`

La cobertura aplica a publicaciones y participaciones en eventos. Tambien hay una organizacion externa duplicada pendiente y una afiliacion pendiente para probar workflows.

## Casos De Calidad

Casos intencionales para `/admin/calidad-datos` y asistentes:

- Publicaciones sin DOI.
- Publicaciones sin resumen.
- Publicaciones sin temas.
- Publicaciones con resumen publico incompleto.
- Investigadores sin ORCID.
- Autores externos sin afiliacion.
- Organizaciones externas duplicadas.
- Venues sin ISSN/eISSN/ISBN.
- Eventos sin fechas, descripcion o URL de evidencia.
- Publicaciones duplicadas por titulo y ano.
- Temas candidatos duplicados o variantes cercanas.

## Consultas Semanticas

Consultas utiles para probar separacion semantica:

- `IA local en hospitales`
- `panteras y conservacion`
- `salud publica y clima urbano`
- `grafos de conocimiento en genomica`
- `calidad de datos en investigacion`

## Embeddings

El arranque no reconstruye embeddings para evitar trabajo costoso. Para reconstruirlos, inicia el backend con el proveedor deseado y ejecuta:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/ai/embeddings/publications/rebuild -Headers @{ Authorization = "Basic <token-admin-o-validator>" }
```

Con una base limpia tras V23, el endpoint debe informar `totalPublications` cercano a `489`. Si la base ya tenia datos, usa el `totalPublications` devuelto por el endpoint como conteo esperado.

Con proveedores mock, los vectores son deterministas y sirven para pruebas funcionales. Con Ollama, configura `AI_EMBEDDING_PROVIDER=ollama`, `AI_OLLAMA_EMBEDDING_MODEL=bge-m3` y `AI_EMBEDDING_DIMENSION=1024`.

## Informes

Plantillas verificadas:

- Informe anual de unidad.
- Informe de investigador.
- Informe de linea tematica.
- Informe de actividad institucional.
- Informe de calidad de datos.
- Informe para comite de direccion.

Casos de prueba recomendados:

- Unidad: `Grupo de IA Clinica`.
- Investigador: `Maya Chen`.
- Tema: `IA local`.
- Tema: `Panteras`.
- Linea estrategica: una linea generada desde el mapa estrategico con evidencia suficiente.
- Calidad de datos: unidad `Grupo de IA Clinica` o `Departamento de Humanidades Digitales`.

Si el proveedor es `mock`, el backend genera un dossier determinista en Markdown con citas `[pub:ID]` usando la evidencia recuperada. Si Ollama falla o no devuelve contenido, la API responde con un error claro en espanol en lugar de devolver un informe vacio.
