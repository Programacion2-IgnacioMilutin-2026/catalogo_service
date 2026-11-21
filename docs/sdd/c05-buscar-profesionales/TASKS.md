# TASKS: C05 — Buscar profesionales

SPEC: `docs/sdd/c05-buscar-profesionales/SPEC.md` (aprobada el 2026-11-20, con el cambio de las comas aprobado el mismo día). PLAN: `docs/sdd/c05-buscar-profesionales/PLAN.md` (aprobado el 2026-11-21). Tareas aprobadas por el usuario el 2026-11-21. La implementación sigue su autorización, tanda por tanda (tanda 1, TASK-01: autorizada el 2026-11-21, después del commit `25e94b4` con la SPEC, el PLAN y estas tareas).

Las tareas siguen el "Orden de implementación" del PLAN y cada una deja la suite en verde. Toda tarea con tests incluye, después de que pasan, una prueba en negativo: romper el código a propósito, ver fallar el test, restaurar el archivo y comprobar el hash. Los tests se cuentan con los reportes XML (`target/surefire-reports/TEST-*.xml`) después de `clean`; al cerrar C04 eran 522 tests en 70 clases, y TASK-01 toma la línea base. Ninguna tarea llama a la cátedra real ni lee el `.env`, salvo la prueba manual de TASK-11, que corre el usuario.

Ningún test nuevo detiene o pausa la base, ni compara tiempos con márgenes ajustados (P7.3): la única espera es el tiempo máximo amplio de la primera mitad de CA-15, como en C04.

Los comandos de git los ejecuta el usuario. Al terminar cada tanda se le da, por cada tarea, un mensaje de commit recomendado, la lista de archivos a agregar y la explicación de cada archivo creado o modificado.

Tandas propuestas (cada una espera la orden del usuario): 1 = TASK-01; 2 = TASK-02 y TASK-03; 3 = TASK-04 y TASK-05; 4 = TASK-06 y TASK-07; 5 = TASK-08 y TASK-09; 6 = TASK-10 y TASK-11; 7 = TASK-12.

## Etapa 1 · La búsqueda en la base

- [x] **TASK-01 · Las colaciones de `V4`, los filtros del dominio y la búsqueda por nombre en orden**
  - Alcance:
    - `V4__spanish_order_and_search_for_professional_names.sql`: asigna `spanish_insensitive` a `professionals.first_name` y `last_name`, y crea la colación de búsqueda `search_insensitive` (ICU, `und-u-ks-level1`, no determinística) (P1).
    - `ProfessionalSearchCriteria` en `catalog/domain/model` (clase Lombok), con `nameWords()`.
    - `ProfessionalRepository` suma `countMatching(criteria)` y `findMatching(criteria, page, size)`. `JpaProfessionalRepository` suma `JpaSpecificationExecutor`. `JpaProfessionalRepositoryAdapter` los implementa, por ahora con el filtro por nombre (cada palabra en el nombre o el apellido, con `collate` y los comodines escapados) y el orden por apellido, nombre e `id`.
    - `CatalogTables` suma `insertProfessional` e `insertWeeklySchedule`.
    - `ProfessionalSearchRepositoryAdaptersTest` (nuevo, misma configuración que `CatalogRepositoryAdaptersTest`).
    - No se tocan los modelos existentes, las entidades, los mappers ni la sincronización.
  - Depende de: ninguna
  - Resuelve: CA-03 y CA-07 en la base; el riesgo del `collate` de Hibernate (P2)
  - Validación:
    - Línea base antes de tocar nada: `./mvnw -q clean test`, conteo en los XML.
    - **Primero, que el `collate` funcione:** si Hibernate no lo acepta o PostgreSQL no lo recibe como se espera, se detiene la tarea y se vuelve a P2 con el usuario.
    - `./mvnw test -Dtest=ProfessionalSearchRepositoryAdaptersTest`: los textos de CA-03 ("tomás ferreyra", "ferreyra tomás", "TOM ferr", "nunez", "ibanez", "ana", `%`, `_`) y el orden de CA-07 (Munro antes que Muñoz, desempate por `id`). En el log, Flyway llega a `v4`.
    - Los tests de `catalog` siguen en verde (`./mvnw test -Dtest='catalogo/catalog/**/*Test'`).
    - Pruebas en negativo: `V4` sin asignar la colación a las columnas (falla el orden); el nombre buscado con la colación de `V3` (falla "nunez"); y sin escapar los comodines (falla `%`).
  - Resultado: 2026-11-21 (tanda 1, autorizada ese día).
    - Creados `V4__spanish_order_and_search_for_professional_names.sql`, `ProfessionalSearchCriteria` (con todos los filtros de P6; esta tarea usa solo el nombre) y `ProfessionalSearchRepositoryAdaptersTest`.
    - Modificados `ProfessionalRepository` (`countMatching` y `findMatching`), `JpaProfessionalRepository` (`JpaSpecificationExecutor`), `JpaProfessionalRepositoryAdapter` (la condición por palabras con `collate` y los comodines escapados, y el orden por apellido, nombre e `id`) y `CatalogTables` (`insertProfessional` e `insertWeeklySchedule`; el segundo se usa desde TASK-03).
    - Línea base, antes de tocar nada: `./mvnw -q clean test`, 522 tests en 70 clases, 0 fallos ni errores, 141 s.
    - **El `collate` de Hibernate funciona:** `./mvnw test -Dtest=ProfessionalSearchRepositoryAdaptersTest`, 15 tests, 0 fallos. Pasaron "nunez" → Núñez e "ibanez" → Ibáñez, que solo encuentra la colación de búsqueda (la de la columna distingue la ñ). En el log, Flyway aplica `V1` a `V4` ("now at version v4").
    - Los 13 textos de CA-03 dan lo esperado, y `countMatching` coincide con la cantidad encontrada. El orden de CA-07 salió: Álvarez (8), de la Fuente (7), Gómez Ana (5), gomez ana (6), Gómez Bruno (3), Munro (4), Muñoz (2), Núñez (9), Zárate (1).
    - Prueba en negativo 1: `V4` sin las dos líneas `ALTER TABLE … COLLATE`, y falló solo el test del orden: la base puso Muñoz antes que Munro y no desempató 5 y 6 por `id`.
    - Prueba en negativo 2: la búsqueda con `spanish_insensitive`, y fallaron exactamente "nunez" e "ibanez".
    - Prueba en negativo 3: sin escapar los comodines, y fallaron `%` y `_`, que devolvieron a los cuatro. El caso `\` siguió pasando, como se esperaba: no distingue si se escapó, y solo comprueba que una barra no rompe la consulta.
    - Restaurados `V4` y el adaptador, `sha256sum -c` coincide en los dos.
    - `./mvnw test -Dtest='catalogo/catalog/**/*Test'`: 78 tests, 0 fallos.
    - `./mvnw -q clean test`: 537 tests en 71 clases (522 + 15), 0 fallos ni errores, 140 s.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices de antes (fachada y gateway de `synchronization`). TASK-01 no suma ninguno.

- [x] **TASK-02 · Categoría, estado, contar y la página**
  - Alcance: `JpaProfessionalRepositoryAdapter` suma los filtros por `categoryId` y `enabled`, y su combinación con el nombre. `ProfessionalSearchRepositoryAdaptersTest` suma esos casos, el conteo y la página (`findMatching` con `page` y `size`), y los resultados vacíos.
  - Depende de: TASK-01
  - Resuelve: CA-02, CA-08 y CA-13 en la base; parte de CA-06
  - Validación: `./mvnw test -Dtest=ProfessionalSearchRepositoryAdaptersTest`. Una categoría habilitada y una deshabilitada, profesionales en los dos estados; páginas de 2 sin repetidos ni faltantes; el total igual a la cantidad que cumple los filtros; un `categoryId` inexistente da 0. Prueba en negativo: el filtro por estado ignorado, y falla.
  - Resultado: 2026-11-21 (tanda 2, autorizada ese día).
    - `JpaProfessionalRepositoryAdapter` suma `inCategory` y `withEnabled`, y `matching` arma la lista de condiciones con los filtros recibidos.
    - `ProfessionalSearchRepositoryAdaptersTest` suma 9 tests: 6 casos de categoría y estado (CA-02; con una categoría deshabilitada y un horario deshabilitado que no cambian nada), el nombre combinado con categoría y estado (CA-06), las páginas de 2 (CA-08) y sin profesionales (CA-13).
    - `./mvnw test -Dtest=ProfessionalSearchRepositoryAdaptersTest`: 24 tests, 0 fallos.
    - Prueba en negativo: sin la condición de `enabled`, fallaron exactamente los tres casos con estado y el combinado. Restaurado, `sha256sum -c` coincide.
    - Suite completa: la de TASK-03.

- [x] **TASK-03 · La disponibilidad y los horarios de varios profesionales**
  - Alcance:
    - `JpaProfessionalRepositoryAdapter` suma la disponibilidad: "existe un horario habilitado de este profesional que…", con días opcionales, franja opcional (solo `from`, solo `to` o las dos), solapamiento estricto, y sin contar los horarios que no terminan después de empezar (RF-04).
    - `WeeklyScheduleRepository` suma `findAllByProfessionalIds(ids)`; `JpaWeeklyScheduleRepository`, `findAllByProfessionalIdIn(ids)`; y `JpaWeeklyScheduleRepositoryAdapter` lo implementa con el mapper existente.
    - Los casos nuevos van en `ProfessionalSearchRepositoryAdaptersTest`.
  - Depende de: TASK-02
  - Resuelve: CA-04, CA-05 y CA-06 en la base; la lectura de los horarios de RF-01
  - Validación: `./mvnw test -Dtest=ProfessionalSearchRepositoryAdaptersTest`. Los profesionales y pedidos de CA-04 y CA-05, uno por caso; los filtros combinados de CA-06; y los horarios de los ids pedidos, y solo esos. Pruebas en negativo: el solapamiento con `<=` en lugar de `<` (falla el borde de 08:00 a 12:00 con `from` 12:00); sin la condición de fin posterior al inicio (falla el horario de 22:00 a 02:00); y la disponibilidad sin exigir que el horario esté habilitado (falla CA-04).
  - Resultado: 2026-11-21 (tanda 2).
    - `JpaProfessionalRepositoryAdapter` suma `availableIn`, una subconsulta "existe un horario de este profesional que…": habilitado, que termina después de empezar, en alguno de los días si llegaron, que termina después de `from` si llegó y que empieza antes de `to` si llegó. Solo se agrega si llegó alguna parte.
    - `WeeklyScheduleRepository` suma `findAllByProfessionalIds`; `JpaWeeklyScheduleRepository`, `findAllByProfessionalIdIn`; y `JpaWeeklyScheduleRepositoryAdapter` lo implementa con el mapper existente. Con una lista vacía devuelve vacío sin consultar.
    - `ProfessionalSearchRepositoryAdaptersTest` suma 14 tests: CA-04; los 11 casos de CA-05, con días, franja completa, solo `from`, solo `to`, los dos bordes, el horario de 22:00 a 02:00 y sin disponibilidad; la disponibilidad combinada con el nombre (CA-06); y los horarios de los ids pedidos con todos sus campos.
    - `./mvnw test -Dtest=ProfessionalSearchRepositoryAdaptersTest`: 38 tests, 0 fallos.
    - Prueba en negativo 1: `greaterThanOrEqualTo` en lugar de `greaterThan`, y falló solo el caso de `from` 12:00.
    - Prueba en negativo 2: sin la condición de fin posterior al inicio, y fallaron los casos del lunes, del lunes y el viernes, y de `to` 23:00, donde entraba el horario de 22:00 a 02:00.
    - Prueba en negativo 3: sin exigir el horario habilitado, y falló el test de CA-04.
    - Restaurado el adaptador, `sha256sum -c` coincide.
    - `./mvnw -q clean test`: 560 tests en 71 clases (537 + 23), 0 fallos ni errores, 139 s.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices.

## Etapa 2 · El caso de uso

- [x] **TASK-04 · El caso de uso y el service**
  - Alcance:
    - `ProfessionalWithSchedules` y `ProfessionalPage` en `catalog/domain/model` (clases Lombok).
    - `SearchProfessionalsUseCase` y `SearchProfessionalsUseCaseImpl`: transacción de solo lectura con `REPEATABLE READ` (P3); sin inicializar, `CatalogNotInitializedException`; cuenta; si la página empieza en el total o más allá, devuelve la página vacía con el total sin leer más; si no, lee la página y los horarios, los junta y los ordena por D7.
    - `ProfessionalService` con `searchProfessionals(...)`.
    - `SearchProfessionalsUseCaseImplTest` (nuevo, puertos simulados).
    - Sin controller ni seguridad.
  - Depende de: TASK-03
  - Resuelve: CA-01 (los horarios juntos y en orden), CA-08 (más allá del total no lee) y CA-12 (sin inicializar no busca), en el caso de uso
  - Validación: `./mvnw test -Dtest=SearchProfessionalsUseCaseImplTest`. Chequeos de la skill (§12, del 1 al 5) sin resultados nuevos. Pruebas en negativo: sin cortar en el total (se pide la página igual, y falla); y los horarios sin ordenar por día (falla). La foto única no se prueba aquí: la prueba TASK-09.
  - Resultado: 2026-11-21 (tanda 3, autorizada ese día).
    - Creados `ProfessionalWithSchedules`, `ProfessionalPage`, `SearchProfessionalsUseCase`, `SearchProfessionalsUseCaseImpl` (con `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`), `ProfessionalService` y `SearchProfessionalsUseCaseImplTest`.
    - Los puertos se simulan con Mockito (`@Mock`), porque son tres y el test solo mira qué se lee y cómo se arma la página. Los otros tests unitarios del servicio usan clientes falsos escritos a mano; para repositorios, que nadie había simulado, Mockito evita tres clases de prueba.
    - `./mvnw test -Dtest=SearchProfessionalsUseCaseImplTest`: 6 tests, 0 fallos. Sin versión local, falla sin tocar los otros puertos (CA-12). Una página que empieza en el total o más allá viene vacía con el total, sin leer la página ni los horarios: cuatro casos, entre ellos el total 0 y la página 2000000000 (CA-08). Los profesionales vienen en el orden del puerto, cada uno con sus horarios por día, inicio e `id`, y uno sin horarios trae la lista vacía (CA-01).
    - Prueba en negativo 1: sin el corte en el total, fallaron los cuatro casos de la página más allá.
    - Prueba en negativo 2: los horarios sin ordenar, y falló el test del orden.
    - Restaurado, `sha256sum -c` coincide.
    - `./mvnw -q clean test`: 566 tests en 72 clases (560 + 6), 0 fallos ni errores, 141 s.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices.

## Etapa 3 · El request, las respuestas y el mapper

- [x] **TASK-05 · El request, las respuestas y el mapper web**
  - Alcance:
    - `ProfessionalSearchRequest`: todos los campos como texto, y los días como lista de textos. Las reglas de RF-07 y D8 van como Bean Validation con mensajes fijos en inglés: días sin distinguir mayúsculas, `enabled` solo `true` o `false`, horas `HH:mm` o `HH:mm:ss`, enteros con sus rangos, vacíos válidos, y "`from` anterior a `to`" informado en `from` y revisado solo si las dos horas se pudieron leer.
    - **Ajuste del 2026-11-21, aprobado por el usuario:** dos validaciones propias, `WholeNumber` (los enteros con su rango) y `TimeRange` (`from` anterior a `to`), en `web/dto`, cada una con su validador adentro (ver el PLAN, "Ajuste a P5 y P6").
    - `ProfessionalPageResponse`, `ProfessionalResponse`, `ProfessionalCategorySummaryResponse` y `WeeklyScheduleResponse`.
    - `ProfessionalDtoMapper`: `toDomain`, `toPage`, `toSize` y `toResponse`.
    - `ProfessionalDtoMapperTest` (nuevo).
  - Depende de: TASK-04
  - Resuelve: CA-03 (`nameWords()` y los espacios) y CA-18 (vacíos, mayúsculas y comas), en el mapper; base de CA-01
  - Validación: `./mvnw test -Dtest=ProfessionalDtoMapperTest`. Los vacíos llegan como ausentes; `monday`, `Monday` y `MONDAY` dan el mismo día; los días con comas y repetidos dan lo mismo; la página y el tamaño por defecto son 0 y 10; y `toResponse` conserva todos los campos (`hasNoNullFieldsOrProperties`). Las reglas de validación se prueban en TASK-07. Prueba en negativo: una línea menos en un Builder del mapper, y falla.
  - Resultado: 2026-11-21 (tanda 3).
    - Creados `ProfessionalSearchRequest`, `WholeNumber` y `TimeRange` (las dos con su validador adentro), `ProfessionalPageResponse`, `ProfessionalResponse`, `ProfessionalCategorySummaryResponse`, `WeeklyScheduleResponse`, `ProfessionalDtoMapper` y `ProfessionalDtoMapperTest`.
    - Las reglas del request:
      - `@Pattern` para `enabled`, las horas (`ProfessionalSearchRequest.TIME_FORMAT`, que también usa `TimeRange`) y cada día, sin distinguir mayúsculas; las tres aceptan el vacío.
      - `@WholeNumber` para `categoryId` (rango de `Long`), `page` (0 a `Integer.MAX_VALUE`) y `size` (1 a 100).
      - `@TimeRange` sobre el request.
    - `./mvnw test -Dtest=ProfessionalDtoMapperTest`: 7 tests, 0 fallos:
      - un request completo da los filtros, con `nameWords()` separando "  tomás   ferr " en dos palabras y `monday`, `FRIDAY` y `Monday` dando dos días;
      - los parámetros vacíos y los ausentes dan "sin filtros", con página 0 y tamaño 10;
      - un día vacío junto a otro no cuenta;
      - la respuesta conserva todos los campos del profesional, de su categoría y de su horario;
      - un profesional sin horarios trae la lista vacía;
      - `null` da `null`.
    - Prueba en negativo: sin `.slotDurationMinutes(...)` en el Builder del horario, falló el test de los campos ("expected: 30"). Restaurado, `sha256sum -c` coincide.
    - Las validaciones del request todavía no tienen test: las prueba TASK-07, con el controller, como estaba previsto.
    - `./mvnw -q clean test`: 573 tests en 73 clases (566 + 7), 0 fallos ni errores, 153 s.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices.

## Etapa 4 · La entrada web

- [x] **TASK-06 · El controller y la ruta pública**
  - Alcance:
    - `ProfessionalController` (`@RequestMapping("/api/professionals")`, un `@GetMapping` con `@Valid @ModelAttribute`), que traduce `CatalogNotInitializedException` a `503` `CATALOG_NOT_INITIALIZED`.
    - `SecurityConfig` suma `GET /api/professionals`, exacta.
    - `ProfessionalCategoriesIntegrationTest`: el test de las otras rutas pierde `GET /api/professionals` y suma `POST /api/professionals`.
    - `ProfessionalControllerTest` (nuevo, `@WebMvcTest` con `SecurityConfig`, el mapper y el service simulado): la forma del JSON, el JWT ignorado, el `503` y el `500`.
  - Depende de: TASK-05
  - Resuelve: CA-01 (la forma del JSON y las horas en `HH:mm:ss`), CA-11, CA-12 y CA-14 en la forma, y CA-17
  - Validación: `./mvnw test -Dtest='ProfessionalControllerTest,ProfessionalCategoriesIntegrationTest'`. Si las horas no salen en `HH:mm:ss`, se ajusta el mapper (riesgo del PLAN). Pruebas en negativo: la ruta con `/**` en `SecurityConfig` (`/api/professionals/15` deja de ser `403`, y falla); y el `503` traducido como `500` (falla).
  - Resultado: 2026-11-21 (tanda 4, autorizada ese día; el usuario commitea TASK-06 antes de TASK-07, porque las dos tocan `ProfessionalControllerTest`).
    - Creados `ProfessionalController` y `ProfessionalControllerTest`.
    - Modificados `SecurityConfig` (`GET /api/professionals`, exacta) y `ProfessionalCategoriesIntegrationTest` (el test de las otras rutas pierde `GET /api/professionals` y suma `POST /api/professionals`).
    - `./mvnw test -Dtest='ProfessionalControllerTest,ProfessionalCategoriesIntegrationTest'`: 19 tests, 0 fallos.
      - El JSON coincide en modo estricto con el del contrato, sin campos de más.
      - **Las horas salen como `"08:00:00"` y `"12:30:00"`: el riesgo de Jackson con las horas quedó descartado**, y el mapper no tuvo que cambiar.
      - Un profesional sin horarios trae `[]`.
      - Sin parámetros, el service recibe la página 0 de 10.
      - Las cinco variantes de `Authorization` dan la misma respuesta.
      - El `503` y el `500` salen en `problem+json` con su `code`; el `500` sin detalles internos y con el error en el log.
      - `GET /api/professionals/15` y `POST /api/professionals` siguen en `403`.
    - Un error del test, corregido antes de la prueba en negativo: el `500` se comprobaba con "professionals" entre las palabras prohibidas, y el cuerpo la trae en `instance` (`/api/professionals`, la ruta pública, no un dato interno). La falla simulada nombra ahora `weekly_schedules`, que no aparece en la ruta.
    - Prueba en negativo 1: la ruta con `/**`, y falló el caso `GET /api/professionals/15` (esperaba `403`, llegó `404`).
    - Prueba en negativo 2: "sin inicializar" traducido como `500`, y falló el test del `503`.
    - Restaurados, `sha256sum -c` coincide en los dos.
    - `./mvnw -q clean test`: 582 tests en 74 clases (573 + 9), 0 fallos ni errores, 139 s.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices.

- [x] **TASK-07 · El `400` con `fieldErrors`**
  - Alcance:
    - `ApiExceptionHandler` sobrescribe el manejo de `MethodArgumentNotValidException`: `400` `VALIDATION_ERROR`, un `fieldErrors` por parámetro (los errores de varios días se juntan en `dayOfWeek`) y los mensajes de las reglas. No importa nada de C05.
    - `ProfessionalControllerTest` suma todas las reglas de validación y que los vacíos, las mayúsculas, las comas y los parámetros desconocidos le lleguen al service igual que si no vinieran.
    - La decisión 005 ajusta su texto: los códigos generales los pone el manejador, y los de cada slice, su controller.
  - Depende de: TASK-06
  - Resuelve: CA-09, CA-10, CA-18 y CA-19 en la web
  - Validación: `./mvnw test -Dtest=ProfessionalControllerTest`. Cada valor de CA-09 y CA-10 responde `400` con su parámetro; CA-19 trae exactamente las entradas del criterio; ningún cuerpo contiene tipos de Java. Los tests de C04 siguen en verde (`./mvnw test -Dtest='ProfessionalCategory*Test,ProfessionalCategories*Test'`). Pruebas en negativo: sin juntar los errores de los días (aparecen dos entradas, y falla); y la regla de las horas revisada aunque una no se pudo leer (aparece una entrada de más, y falla).
  - Resultado: 2026-11-21 (tanda 4, después del commit `f5ecba0` de TASK-06).
    - `ApiExceptionHandler` sobrescribe `handleMethodArgumentNotValid`: `400` `VALIDATION_ERROR`, `detail` fijo, y `fieldErrors` con una entrada por parámetro. Quita el índice de los elementos de una lista (`dayOfWeek[1]` → `dayOfWeek`), y pone un texto neutro si Spring no pudo convertir un valor.
    - La decisión 005 suma el punto 4 y el paso en el recorrido, y precisa "qué no hace": pone solo los códigos generales del contrato común.
    - `ProfessionalControllerTest` suma 28 tests:
      - 20 valores inválidos de CA-09 y CA-10, cada uno con `400`, una sola entrada con su parámetro, sin tipos de Java en el cuerpo y sin llamar al service;
      - CA-19, con dos parámetros inválidos (dos entradas, cada una con su mensaje), "`from` antes que `to`" (una entrada en `from`) y una hora mal escrita (una entrada en `from`, por el formato);
      - CA-18, con vacíos, `sort`, desconocidos y un `name` de espacios, que le llegan al service como "sin filtros", página 0 de 10;
      - los días en cualquier mayúscula, repetidos, con comas o con un vacío, que dan lunes y viernes;
      - y los valores válidos en los bordes: `page=2000000000`, `size=100`, `categoryId=-5`, `09:00:30` y `23:59`.
    - `./mvnw test -Dtest=ProfessionalControllerTest`: 37 tests, 0 fallos. `WholeNumber` y `TimeRange` pasaron en la primera corrida.
    - Las pruebas en negativo de la tarea cambiaron en la implementación, para que cada una rompa algo que un test detecte:
      - "la regla de las horas revisada aunque una no se pudo leer" no se puede detectar, porque el manejador junta los errores de un mismo parámetro; se reemplazó por `TimeRange` informando en `to`;
      - se sumó una tercera, sobre `WholeNumber`.
    - Prueba en negativo 1: el manejador sin quitar el índice, y fallaron los dos casos de días inválidos (llegaban como `dayOfWeek[0]`) y el combinado (tres entradas).
    - Prueba en negativo 2: `TimeRange` con el error en `to`, y fallaron los dos casos de franja invertida o igual y el de "`from` antes que `to`".
    - Prueba en negativo 3: `WholeNumber` sin el rango, y fallaron seis casos (20 dígitos en `categoryId` y en `page`, `page=-1`, `size=0`, `size=101` y el combinado). En el log aparecieron 3 "Unexpected error": sin el rango, esos valores rompían la conversión o la paginación con un `500`.
    - Restaurados, `sha256sum -c` coincide en los tres.
    - `./mvnw -q clean test`: 610 tests en 74 clases (582 + 28), 0 fallos ni errores, 140 s. Las cinco clases de C04 siguen en verde.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices.

## Etapa 5 · La integración completa

- [x] **TASK-08 · La búsqueda de punta a punta**
  - Alcance:
    - `ProfessionalsIntegrationTest` (nuevo; aplicación en un puerto real, `RestTestClient`, cátedra simulada como testigo):
      - los valores desde la base y los horarios en orden, sin pedidos a la cátedra;
      - un caso por filtro y la paginación, con la página enorme;
      - el JWT ignorado y el `503`, con y sin profesionales;
      - la página vacía, y el `500` con `catalog_sync_state` y `professionals` renombradas;
      - un cambio sin confirmar durante el pedido, y después confirmado;
      - los vacíos y desconocidos, y algunos `400` con el servidor real.
    - `ProfessionalCategoriesWithoutCatedraIntegrationTest`: su método suma un pedido a C05.
  - Depende de: TASK-07
  - Resuelve: CA-01 a CA-03, CA-05, CA-07, CA-08, CA-11 a CA-14, la primera mitad de CA-15, CA-16, CA-18 y CA-19 de punta a punta
  - Validación: `./mvnw test -Dtest='ProfessionalsIntegrationTest,ProfessionalCategoriesWithoutCatedraIntegrationTest'`. Después de cada caso con tablas renombradas, las tablas siguen con su nombre. Pruebas en negativo: sin la captura de lo no previsto en el manejador (el `500` llega como otro estado, y falla); y el controller sin traducir "sin inicializar" (falla).
  - Resultado: 2026-11-21 (tanda 5, autorizada ese día; el usuario commitea juntas TASK-08 y TASK-09).
    - Creado `ProfessionalsIntegrationTest`, con 19 tests:
      - **CA-01:** la copia completa en modo estricto, con el orden de D6, los horarios cargados desordenados y devueltos por D7, las horas `HH:mm:ss`, la categoría sin descripción y sin pedidos a la cátedra simulada;
      - **un caso por filtro**, 9 en total: categoría, estado, nombre sin tildes y en mayúsculas, día con franja, horario deshabilitado, solo `to`, días con coma y filtros combinados;
      - **la paginación:** páginas de 2 y la 2000000000, que da vacía con el total real y no `500`;
      - **lo que no cambia la respuesta:** el JWT alterado, y los vacíos y desconocidos;
      - **el `503`** con y sin profesionales, sin pedidos a la cátedra y con la base igual;
      - **el `400` antes de mirar la copia**, con la copia sin inicializar;
      - **la página vacía**, sin profesionales y con filtros que nadie cumple;
      - **el `500`** con `catalog_sync_state` y con `professionals` renombradas, sin detalles internos y con el error en el log;
      - **un cambio sin confirmar** desde otra conexión, que no se ve y no hace esperar (tope de 5 s, el mismo de C04), y después confirmado, que sí se ve.
    - `ProfessionalCategoriesWithoutCatedraIntegrationTest`: su método suma un profesional y un pedido a C05, que responde `200` sin la integración (CA-16).
    - `./mvnw test -Dtest=ProfessionalsIntegrationTest`: 19 tests, 0 fallos. `./mvnw test -Dtest=ProfessionalCategoriesWithoutCatedraIntegrationTest`: 1 test, 0 fallos.
    - En el `500` no se revisa "professionals", porque el cuerpo trae la ruta en `instance`, igual que en TASK-06; se revisan `catalog_sync_state`, `_hidden` y los textos del error de PostgreSQL.
    - Prueba en negativo 1: sin `@ExceptionHandler(Exception.class)`, los dos casos del `500` llegaron como `403`. Es el reenvío a `/error` que documentó C04, y ahora se ve también con C05.
    - Prueba en negativo 2: el `catch` del controller sin atrapar "sin inicializar", y el `503` llegó como `500`.
    - Restaurados, `sha256sum -c` coincide en los dos.
    - Suite completa: la de TASK-09.

- [x] **TASK-09 · La foto única**
  - Alcance: `ProfessionalsConsistencyIntegrationTest` (nuevo). `@MockitoSpyBean` sobre el adaptador de profesionales: antes de traer la página, y después de contar, el test confirma desde una conexión propia del `DataSource` un profesional nuevo y un horario cambiado; después corre la lectura real (P7.2).
  - Depende de: TASK-08
  - Resuelve: CA-15 (la segunda mitad); RF-11
  - Validación: `./mvnw test -Dtest=ProfessionalsConsistencyIntegrationTest`. La respuesta trae el total, la página y los horarios de antes; un pedido siguiente trae los nuevos. **Prueba en negativo obligatoria:** sin `REPEATABLE READ` en el caso de uso, el test falla.
  - Resultado: 2026-11-21 (tanda 5).
    - Creado `ProfessionalsConsistencyIntegrationTest`, sin la cátedra (`catalogo.catedra.base-url=`) y con `@MockitoSpyBean` sobre `ProfessionalRepository`.
    - El primer `findMatching`, que llega después de contar, confirma antes desde otra conexión del `DataSource` un profesional nuevo (605, Acosta) y un horario cambiado (701, de 08:00 a 07:00), y recién después hace la lectura real; los siguientes son normales.
    - `./mvnw test -Dtest=ProfessionalsConsistencyIntegrationTest`: 1 test, 0 fallos. El primer pedido trae el total, la página y el horario de antes (2, `[602, 601]` y 08:00); el test comprueba que el cambio se hizo; y el segundo pedido trae los nuevos (3, `[605, 602, 601]` y 07:00).
    - **Prueba en negativo obligatoria:** con `@Transactional(readOnly = true)`, sin `REPEATABLE READ`, el test falló con `"$.items[*].id" expected:<[602, 601]> but was:<[605, 602, 601]>`. El total decía 2 y la página traía 3: la mezcla que prohíbe RF-11. Restaurado, `sha256sum -c` coincide.
    - `./mvnw -q clean test`: 630 tests en 76 clases (610 + 20), 0 fallos ni errores, 161 s. Unos 20 s más que antes, por los arranques de las dos clases nuevas, que necesitan cada una su propia configuración (P7.1).
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices.

## Etapa 6 · Regresión y prueba manual

- [x] **TASK-10 · La suite completa**
  - Alcance: sin código nuevo. Solo se corre y se registra.
  - Depende de: TASK-09
  - Resuelve: comprobaciones de regresión del PLAN
  - Validación: `./mvnw clean verify`; conteo en los XML comparado con la línea base de TASK-01; cobertura de JaCoCo; y los chequeos de la skill (§12, del 1 al 5). El resultado se registra después de leer la salida, nunca en el mismo comando.
  - Resultado: 2026-11-21 (tanda 6, autorizada ese día; sobre `dfdf56a`, con todo commiteado).
    - `./mvnw clean verify`: `BUILD SUCCESS`, 630 tests en 76 clases, 0 fallos, errores ni salteados, 149 s. Contra la línea base de TASK-01 (522 tests en 70 clases), son 108 tests y 6 clases más.
    - Cobertura de JaCoCo (`target/site/jacoco/jacoco.xml`): 97,3 % de líneas (1617/1662) y 94,0 % de ramas (565/601), contra 97,0 % y 93,4 % al cerrar C04.
    - Las clases de C05 tienen 100 % de líneas. Las ramas están al 100 % salvo dos, que quedan sin cubrir:
      - `ProfessionalSearchCriteria.nameWords()` con un nombre de solo espacios: el mapper ya lo convierte en `null`, así que no llega;
      - en `ApiExceptionHandler`, el texto neutro para un valor que Spring no pudo convertir: con el request todo en texto no ocurre, y queda como defensa para otros controllers.
    - Los modelos, el request y las respuestas no suman líneas, porque su código lo genera Lombok.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices (fachada y gateway de `synchronization`), ninguno de C05.
    - La lista de la skill, revisada en las piezas de C05:
      - el controller inyecta solo el service y el mapper web, y no importa puertos de entrada ni persistencia;
      - el service depende solo de `SearchProfessionalsUseCase`, y el caso de uso solo de puertos de salida;
      - los adaptadores devuelven modelos del dominio;
      - los mappers usan el Builder.

- [x] **TASK-11 · La prueba manual con la cátedra real (la corre el usuario)**
  - Alcance:
    - Se le da al usuario la lista exacta de pedidos a la cátedra que hace el arranque (login técnico, snapshot y lecturas de Redis de la verificación) y el comando para levantar la aplicación con `CATALOGO_CATEDRA_KAFKA_ENABLED=false`.
    - También, los `curl` a C05: sin filtros, por categoría, por nombre con y sin tildes, por disponibilidad, una página más allá y un `400`.
    - Él los corre y pasa la salida, que se revisa sin mostrar secretos.
  - Depende de: TASK-10 y el OK del usuario
  - Resuelve: la prueba manual de P7.4 (enunciado §11); `V4` sobre la base de desarrollo
  - Validación: el log del arranque (Flyway en `v4`, el login y la carga del snapshot) y las respuestas de los `curl`, con los datos reales. Después, la aplicación detenida y el compose como estaba. Si la cátedra no responde, se registra como limitación del entorno.
  - Resultado: 2026-11-21 (tanda 6). El usuario corrió en su terminal `docker compose up -d --wait catalog-db`, la aplicación con `CATALOGO_CATEDRA_KAFKA_ENABLED=false` (el log a un archivo del scratchpad) y un script con 14 `curl` a `localhost:8081`. Antes se le dio la lista de pedidos a la cátedra: el login técnico, el snapshot y las lecturas de Redis, sin Kafka. El log se leyó filtrado, sin hosts ni credenciales.
    - **Arranque:**
      - Flyway aplicó `V4` sobre la base de desarrollo ("now at version v4").
      - El login técnico respondió `PROVISIONED`.
      - La verificación no encontró versión local y pidió el snapshot 422: 8 categorías, 24 profesionales y 44 horarios. Después leyó Redis y quedó al día en la 422.
      - En el log no hay errores ni avisos de la aplicación (0 "Unexpected error"). Los únicos `[WARNING]` eran de Maven, que no pudo bajar metadatos de un repositorio, y no tienen relación con la prueba.
    - **Respuestas, con los datos reales:**
      - Las categorías (C04): `200`, 8, en orden alfabético.
      - Sin filtros: `200`, página 0 de 10, `totalItems` 24. Con `size=100` vienen los 24 por apellido (Acosta, Alvarez, Benitez … Torres, Vega), con habilitados y deshabilitados mezclados. Cada uno trae todos sus horarios en orden de la semana, con las horas en `HH:mm:ss`.
      - `categoryId=101` (Cardiologia): 3, entre ellos una profesional deshabilitada. `enabled=false`: 4.
      - `name=Acosta` y `name=acosta`: 1 cada uno.
      - `dayOfWeek=MONDAY`: 9. Incluye a una profesional deshabilitada con un horario habilitado los lunes, porque el estado es el del profesional (dominio, regla 4).
      - `dayOfWeek=monday,friday&from=14:00`: 12, con la primera página de 10. **El borde de D3.1 se vio con datos reales:** Castro (lunes de 11 a 14) y Costa (viernes de 11 a 14) quedaron afuera, y Quiroga entró por su viernes de 16 a 19.
      - `to=09:00`: 0, porque ningún horario empieza antes de las 11:00.
      - `page=1000`: `200`, vacía, con `totalItems` 24.
      - `size=0&dayOfWeek=FUNDAY`: `400` `VALIDATION_ERROR`, con dos `fieldErrors` (`dayOfWeek` y `size`).
      - Con `Authorization: Bearer not-a-token`: el mismo cuerpo que sin el encabezado (comparado como JSON).
      - `GET /api/professionals/1`: `403` sin cuerpo.
    - **Lo que la cátedra real no permite comprobar:** ninguno de los 24 profesionales ni de las 8 categorías tiene tildes ni ñ. La búsqueda sin tildes, la ñ igual a la n y el lugar de la ñ en el orden quedan probados solo por los tests (TASK-01 y TASK-08), con datos ficticios.
    - **Después:** la aplicación detenida, el compose apagado como estaba (sin contenedores, con el volumen de la base intacto) y el puerto 8081 libre. La base de desarrollo quedó con la copia 422 y el esquema en `v4`, como en cualquier arranque.
    - El script, las respuestas y el log se borraron del scratchpad.

## Etapa 7 · Cierre

- [x] **TASK-12 · Documentación de cierre**
  - Alcance:
    - El `CLAUDE.md`: el slice `catalog` con C05, las excepciones de C05 (P8), cómo se busca (las colaciones de `V4` y su efecto al comparar), la ruta pública nueva en "Seguridad", el `400` en "Cómo responde un error", los tests y helpers nuevos y la carpeta de la SDD.
    - `TASKS.md` con lo observado, la memoria del proyecto, y el recordatorio del commit de `documentacion` (los cambios de C05 en el caso de uso y el contrato, separados de los de turnos).
  - Depende de: TASK-11
  - Resuelve: el cierre del PLAN (etapa 7)
  - Validación: el `CLAUDE.md` revisado contra el código (rutas, clases y comandos que existen) y las copias de `documentacion` y del servicio comparadas.
  - Resultado: 2026-11-21 (tanda 7, autorizada ese día; el usuario commitea juntas las tandas 6 y 7).
    - **El `CLAUDE.md`:**
      - el comando de la aplicación con la cátedra real y sin Kafka;
      - validation entre las dependencias en uso;
      - el slice `catalog` con las piezas de C05 y el `config` con el `400`;
      - las excepciones de C05 (P8) y lo que no es excepción;
      - en "Seguridad", la ruta pública nueva, la regla de la ruta exacta y los casos del test de las otras rutas;
      - en "Cómo responde un error", el `400` `VALIDATION_ERROR`, las validaciones propias y cómo lee Spring los parámetros;
      - una sección nueva, "La búsqueda de profesionales": `V4`, la consulta, el caso de uso y el doble conteo sin medir;
      - lo comprobado con la cátedra real en C05, incluido que no hay tildes ni ñ;
      - los tests de C05 y tres lecciones: la palabra prohibida que aparece en `instance`, el controller anidado en un `@WebMvcTest` y el `%` en `MockMvc`;
      - la carpeta de la SDD, y la decisión 005 ampliada.
    - **Revisión contra el código:** las 19 clases que nombra existen en `src`. La tabla de slices sigue con sus 6 líneas de 4 columnas, después de corregir una fila que había quedado partida al editarla. `V1` a `V4` están en `db/migration`.
    - **Las copias:** `contratos_comunes.md`, el caso de uso de X01 y su secuencia son idénticos en `documentacion`, `turnos_service` y `catalog_service`; el contrato, los casos de uso, la secuencia y el dominio de catálogo son idénticos en `documentacion` y en la copia del servicio.
    - **Para el usuario:** en `documentacion` siguen sin commitear el caso de uso de C05, paso 5 (D1), y el contrato §2.2 (D2, D3 y las dos versiones de la fila de `dayOfWeek`), junto con los cambios de catálogo de C01 a C04 y los de turnos y la app.
