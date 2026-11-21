# TASKS: C05 — Buscar profesionales

SPEC: `docs/sdd/c05-buscar-profesionales/SPEC.md` (aprobada el 2026-11-20, con el cambio de las comas aprobado el mismo día). PLAN: `docs/sdd/c05-buscar-profesionales/PLAN.md` (aprobado el 2026-11-21). Tareas propuestas el 2026-11-21, pendientes de la aprobación del usuario.

Las tareas siguen el "Orden de implementación" del PLAN y cada una deja la suite en verde. Toda tarea con tests incluye, después de que pasan, una prueba en negativo: romper el código a propósito, ver fallar el test, restaurar el archivo y comprobar el hash. Los tests se cuentan con los reportes XML (`target/surefire-reports/TEST-*.xml`) después de `clean`; al cerrar C04 eran 522 tests en 70 clases, y TASK-01 toma la línea base. Ninguna tarea llama a la cátedra real ni lee el `.env`, salvo la prueba manual de TASK-11, que corre el usuario.

Ningún test nuevo detiene o pausa la base, ni compara tiempos con márgenes ajustados (P7.3): la única espera es el tiempo máximo amplio de la primera mitad de CA-15, como en C04.

Los comandos de git los ejecuta el usuario. Al terminar cada tanda se le da, por cada tarea, un mensaje de commit recomendado, la lista de archivos a agregar y la explicación de cada archivo creado o modificado.

Tandas propuestas (cada una espera la orden del usuario): 1 = TASK-01; 2 = TASK-02 y TASK-03; 3 = TASK-04 y TASK-05; 4 = TASK-06 y TASK-07; 5 = TASK-08 y TASK-09; 6 = TASK-10 y TASK-11; 7 = TASK-12.

## Etapa 1 · La búsqueda en la base

- [ ] **TASK-01 · Las colaciones de `V4`, los filtros del dominio y la búsqueda por nombre en orden**
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
  - Resultado: pendiente

- [ ] **TASK-02 · Categoría, estado, contar y la página**
  - Alcance: `JpaProfessionalRepositoryAdapter` suma los filtros por `categoryId` y `enabled`, y su combinación con el nombre. `ProfessionalSearchRepositoryAdaptersTest` suma esos casos, el conteo y la página (`findMatching` con `page` y `size`), y los resultados vacíos.
  - Depende de: TASK-01
  - Resuelve: CA-02, CA-08 y CA-13 en la base; parte de CA-06
  - Validación: `./mvnw test -Dtest=ProfessionalSearchRepositoryAdaptersTest`. Una categoría habilitada y una deshabilitada, profesionales en los dos estados; páginas de 2 sin repetidos ni faltantes; el total igual a la cantidad que cumple los filtros; un `categoryId` inexistente da 0. Prueba en negativo: el filtro por estado ignorado, y falla.
  - Resultado: pendiente

- [ ] **TASK-03 · La disponibilidad y los horarios de varios profesionales**
  - Alcance:
    - `JpaProfessionalRepositoryAdapter` suma la disponibilidad: "existe un horario habilitado de este profesional que…", con días opcionales, franja opcional (solo `from`, solo `to` o las dos), solapamiento estricto, y sin contar los horarios que no terminan después de empezar (RF-04).
    - `WeeklyScheduleRepository` suma `findAllByProfessionalIds(ids)`; `JpaWeeklyScheduleRepository`, `findAllByProfessionalIdIn(ids)`; y `JpaWeeklyScheduleRepositoryAdapter` lo implementa con el mapper existente.
    - Los casos nuevos van en `ProfessionalSearchRepositoryAdaptersTest`.
  - Depende de: TASK-02
  - Resuelve: CA-04, CA-05 y CA-06 en la base; la lectura de los horarios de RF-01
  - Validación: `./mvnw test -Dtest=ProfessionalSearchRepositoryAdaptersTest`. Los profesionales y pedidos de CA-04 y CA-05, uno por caso; los filtros combinados de CA-06; y los horarios de los ids pedidos, y solo esos. Pruebas en negativo: el solapamiento con `<=` en lugar de `<` (falla el borde de 08:00 a 12:00 con `from` 12:00); sin la condición de fin posterior al inicio (falla el horario de 22:00 a 02:00); y la disponibilidad sin exigir que el horario esté habilitado (falla CA-04).
  - Resultado: pendiente

## Etapa 2 · El caso de uso

- [ ] **TASK-04 · El caso de uso y el service**
  - Alcance:
    - `ProfessionalWithSchedules` y `ProfessionalPage` en `catalog/domain/model` (clases Lombok).
    - `SearchProfessionalsUseCase` y `SearchProfessionalsUseCaseImpl`: transacción de solo lectura con `REPEATABLE READ` (P3); sin inicializar, `CatalogNotInitializedException`; cuenta; si la página empieza en el total o más allá, devuelve la página vacía con el total sin leer más; si no, lee la página y los horarios, los junta y los ordena por D7.
    - `ProfessionalService` con `searchProfessionals(...)`.
    - `SearchProfessionalsUseCaseImplTest` (nuevo, puertos simulados).
    - Sin controller ni seguridad.
  - Depende de: TASK-03
  - Resuelve: CA-01 (los horarios juntos y en orden), CA-08 (más allá del total no lee) y CA-12 (sin inicializar no busca), en el caso de uso
  - Validación: `./mvnw test -Dtest=SearchProfessionalsUseCaseImplTest`. Chequeos de la skill (§12, del 1 al 5) sin resultados nuevos. Pruebas en negativo: sin cortar en el total (se pide la página igual, y falla); y los horarios sin ordenar por día (falla). La foto única no se prueba aquí: la prueba TASK-09.
  - Resultado: pendiente

## Etapa 3 · El request, las respuestas y el mapper

- [ ] **TASK-05 · El request, las respuestas y el mapper web**
  - Alcance:
    - `ProfessionalSearchRequest`: todos los campos como texto, y los días como lista de textos. Las reglas de RF-07 y D8 van como Bean Validation con mensajes fijos en inglés: días sin distinguir mayúsculas, `enabled` solo `true` o `false`, horas `HH:mm` o `HH:mm:ss`, enteros con sus rangos, vacíos válidos, y "`from` anterior a `to`" informado en `from` y revisado solo si las dos horas se pudieron leer.
    - `ProfessionalPageResponse`, `ProfessionalResponse`, `ProfessionalCategorySummaryResponse` y `WeeklyScheduleResponse`.
    - `ProfessionalDtoMapper`: `toDomain`, `toPage`, `toSize` y `toResponse`.
    - `ProfessionalDtoMapperTest` (nuevo).
  - Depende de: TASK-04
  - Resuelve: CA-03 (`nameWords()` y los espacios) y CA-18 (vacíos, mayúsculas y comas), en el mapper; base de CA-01
  - Validación: `./mvnw test -Dtest=ProfessionalDtoMapperTest`. Los vacíos llegan como ausentes; `monday`, `Monday` y `MONDAY` dan el mismo día; los días con comas y repetidos dan lo mismo; la página y el tamaño por defecto son 0 y 10; y `toResponse` conserva todos los campos (`hasNoNullFieldsOrProperties`). Las reglas de validación se prueban en TASK-07. Prueba en negativo: una línea menos en un Builder del mapper, y falla.
  - Resultado: pendiente

## Etapa 4 · La entrada web

- [ ] **TASK-06 · El controller y la ruta pública**
  - Alcance:
    - `ProfessionalController` (`@RequestMapping("/api/professionals")`, un `@GetMapping` con `@Valid @ModelAttribute`), que traduce `CatalogNotInitializedException` a `503` `CATALOG_NOT_INITIALIZED`.
    - `SecurityConfig` suma `GET /api/professionals`, exacta.
    - `ProfessionalCategoriesIntegrationTest`: el test de las otras rutas pierde `GET /api/professionals` y suma `POST /api/professionals`.
    - `ProfessionalControllerTest` (nuevo, `@WebMvcTest` con `SecurityConfig`, el mapper y el service simulado): la forma del JSON, el JWT ignorado, el `503` y el `500`.
  - Depende de: TASK-05
  - Resuelve: CA-01 (la forma del JSON y las horas en `HH:mm:ss`), CA-11, CA-12 y CA-14 en la forma, y CA-17
  - Validación: `./mvnw test -Dtest='ProfessionalControllerTest,ProfessionalCategoriesIntegrationTest'`. Si las horas no salen en `HH:mm:ss`, se ajusta el mapper (riesgo del PLAN). Pruebas en negativo: la ruta con `/**` en `SecurityConfig` (`/api/professionals/15` deja de ser `403`, y falla); y el `503` traducido como `500` (falla).
  - Resultado: pendiente

- [ ] **TASK-07 · El `400` con `fieldErrors`**
  - Alcance:
    - `ApiExceptionHandler` sobrescribe el manejo de `MethodArgumentNotValidException`: `400` `VALIDATION_ERROR`, un `fieldErrors` por parámetro (los errores de varios días se juntan en `dayOfWeek`) y los mensajes de las reglas. No importa nada de C05.
    - `ProfessionalControllerTest` suma todas las reglas de validación y que los vacíos, las mayúsculas, las comas y los parámetros desconocidos le lleguen al service igual que si no vinieran.
    - La decisión 005 ajusta su texto: los códigos generales los pone el manejador, y los de cada slice, su controller.
  - Depende de: TASK-06
  - Resuelve: CA-09, CA-10, CA-18 y CA-19 en la web
  - Validación: `./mvnw test -Dtest=ProfessionalControllerTest`. Cada valor de CA-09 y CA-10 responde `400` con su parámetro; CA-19 trae exactamente las entradas del criterio; ningún cuerpo contiene tipos de Java. Los tests de C04 siguen en verde (`./mvnw test -Dtest='ProfessionalCategory*Test,ProfessionalCategories*Test'`). Pruebas en negativo: sin juntar los errores de los días (aparecen dos entradas, y falla); y la regla de las horas revisada aunque una no se pudo leer (aparece una entrada de más, y falla).
  - Resultado: pendiente

## Etapa 5 · La integración completa

- [ ] **TASK-08 · La búsqueda de punta a punta**
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
  - Resultado: pendiente

- [ ] **TASK-09 · La foto única**
  - Alcance: `ProfessionalsConsistencyIntegrationTest` (nuevo). `@MockitoSpyBean` sobre el adaptador de profesionales: antes de traer la página, y después de contar, el test confirma desde una conexión propia del `DataSource` un profesional nuevo y un horario cambiado; después corre la lectura real (P7.2).
  - Depende de: TASK-08
  - Resuelve: CA-15 (la segunda mitad); RF-11
  - Validación: `./mvnw test -Dtest=ProfessionalsConsistencyIntegrationTest`. La respuesta trae el total, la página y los horarios de antes; un pedido siguiente trae los nuevos. **Prueba en negativo obligatoria:** sin `REPEATABLE READ` en el caso de uso, el test falla.
  - Resultado: pendiente

## Etapa 6 · Regresión y prueba manual

- [ ] **TASK-10 · La suite completa**
  - Alcance: sin código nuevo. Solo se corre y se registra.
  - Depende de: TASK-09
  - Resuelve: comprobaciones de regresión del PLAN
  - Validación: `./mvnw clean verify`; conteo en los XML comparado con la línea base de TASK-01; cobertura de JaCoCo; y los chequeos de la skill (§12, del 1 al 5). El resultado se registra después de leer la salida, nunca en el mismo comando.
  - Resultado: pendiente

- [ ] **TASK-11 · La prueba manual con la cátedra real (la corre el usuario)**
  - Alcance:
    - Se le da al usuario la lista exacta de pedidos a la cátedra que hace el arranque (login técnico, snapshot y lecturas de Redis de la verificación) y el comando para levantar la aplicación con `CATALOGO_CATEDRA_KAFKA_ENABLED=false`.
    - También, los `curl` a C05: sin filtros, por categoría, por nombre con y sin tildes, por disponibilidad, una página más allá y un `400`.
    - Él los corre y pasa la salida, que se revisa sin mostrar secretos.
  - Depende de: TASK-10 y el OK del usuario
  - Resuelve: la prueba manual de P7.4 (enunciado §11); `V4` sobre la base de desarrollo
  - Validación: el log del arranque (Flyway en `v4`, el login y la carga del snapshot) y las respuestas de los `curl`, con los datos reales. Después, la aplicación detenida y el compose como estaba. Si la cátedra no responde, se registra como limitación del entorno.
  - Resultado: pendiente

## Etapa 7 · Cierre

- [ ] **TASK-12 · Documentación de cierre**
  - Alcance:
    - El `CLAUDE.md`: el slice `catalog` con C05, las excepciones de C05 (P8), cómo se busca (las colaciones de `V4` y su efecto al comparar), la ruta pública nueva en "Seguridad", el `400` en "Cómo responde un error", los tests y helpers nuevos y la carpeta de la SDD.
    - `TASKS.md` con lo observado, la memoria del proyecto, y el recordatorio del commit de `documentacion` (los cambios de C05 en el caso de uso y el contrato, separados de los de turnos).
  - Depende de: TASK-11
  - Resuelve: el cierre del PLAN (etapa 7)
  - Validación: el `CLAUDE.md` revisado contra el código (rutas, clases y comandos que existen) y las copias de `documentacion` y del servicio comparadas.
  - Resultado: pendiente
