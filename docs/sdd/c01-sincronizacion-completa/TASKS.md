# TASKS: C01 — Sincronizar catálogo completo (snapshot)

SPEC: `docs/sdd/c01-sincronizacion-completa/SPEC.md` (aprobada el 2026-11-17). PLAN: `docs/sdd/c01-sincronizacion-completa/PLAN.md` (aprobado el 2026-11-17). Tareas aprobadas por el usuario el 2026-11-17; la implementación espera su autorización.

Las tareas siguen el "Orden de implementación" del PLAN. Toda tarea con tests incluye, después de que pasan, una prueba en negativo: romper el código a propósito, ver fallar el test, restaurar el archivo y comprobar el hash. Los tests se cuentan con los reportes XML (`target/surefire-reports/TEST-*.xml`). Ninguna tarea llama a la cátedra real, salvo TASK-09, que corre el usuario.

## Etapa 1 · La copia local (`catalog`)

- [x] **TASK-01 · Migración, modelos y entidades de la copia local**
  - Alcance: `src/main/resources/db/migration/V1__create_catalog.sql` (cuatro tablas y la fila vacía del estado, PLAN "Datos y contratos"); en `catalog/domain/model`, `ProfessionalCategory`, `Professional`, `WeeklySchedule`, `CatalogSyncState`, `SyncType`, `SyncResult` y `SyncErrorType`; en `catalog/infrastructure/persistence`, las cuatro entidades (`id` de la cátedra, `@ManyToOne`, P6 y P9), sus repositorios de Spring Data y sus mappers. Sin puertos ni casos de uso.
  - Depende de: ninguna
  - Resuelve: base de RF-03, RF-04 y RF-11 (la usan CA-01, CA-02 y CA-15)
  - Validación: `./mvnw test`: la aplicación arranca con Flyway aplicando `V1` y Hibernate validando las cuatro entidades (`CatalogoApplicationTests`); tests de ida y vuelta de los cuatro mappers con todos los datos completos (skill §10); los 135 tests de X01 siguen en verde.
  - Resultado: 2026-11-17. Creados `V1__create_catalog.sql`, los siete tipos de `catalog/domain/model`, las cuatro entidades, los cuatro repositorios de Spring Data y los cuatro mappers. Diferencias con el alcance escrito: `CatalogSyncState` suma sus reglas (`isInitialized`, `recordSuccess` y `recordFailure`: un éxito actualiza todo y un fracaso solo el último intento), porque son del modelo; los repositorios de Spring Data ya traen `deleteByIdNotIn`, que usa TASK-02; el `id` del estado es `INTEGER`. Ayudante de test nuevo: `catalog/CatalogSamples` (modelos completos de ejemplo).
    - `./mvnw test -Dtest='CatalogoApplicationTests,CatalogPersistenceMappersTest,CatalogSyncStateTest'`: 10 tests, 0 fallos (reportes XML). El log muestra `Migrating schema "public" to version "1 - create catalog"` y `Successfully applied 1 migration`, y el contexto arranca con `ddl-auto: validate`. El `ERROR` de configuración del login técnico es el esperado: la suite no configura la cátedra.
    - Pruebas en negativo: sin la línea de `description` en `ProfessionalCategoryMapper.toDomainModel`, fallan los tres tests de ida y vuelta que la incluyen; con `recordFailure` borrando `lastSuccessfulSyncAt`, fallan `aFailureOnlyChangesTheLastAttempt` y el test de `RecordSyncFailureUseCaseImplTest` de TASK-03. Archivos restaurados con el mismo hash (`9ad7399f91b99ea6` y `8f18b30e2d6273b7`).
    - Regresión: ver TASK-03.

- [x] **TASK-02 · Repositorios de `catalog`**
  - Alcance: puertos de salida `ProfessionalCategoryRepository`, `ProfessionalRepository`, `WeeklyScheduleRepository` y `CatalogSyncStateRepository` (P4), y sus adaptadores JPA: guardar actualizando o insertando por `id`, quitar los `id` que no están en una lista (P5), y leer y guardar el estado único.
  - Depende de: TASK-01
  - Resuelve: CA-02 (parte de base)
  - Validación: tests de los adaptadores contra el PostgreSQL de Testcontainers: guardar una entidad nueva y una existente, quitar las que no están en la lista, y leer y guardar el estado.
  - Resultado: 2026-11-17. Creados los cuatro puertos (`saveAll`, `deleteAllExcept`, y `get` y `save` del estado) y los cuatro adaptadores `Jpa…RepositoryAdapter`. Dos decisiones de implementación: con una lista vacía, `deleteAllExcept` borra todo con `deleteAll` en lugar de mandar un `NOT IN` vacío; y `deleteAllExcept` lleva `@Transactional` en el adaptador, porque un borrado derivado de Spring Data necesita una transacción de escritura cuando se lo llama solo (dentro del reemplazo se une a la del caso de uso). Si falta la fila del estado, `get` lanza `IllegalStateException`. Ayudante de test nuevo: `catalog/CatalogTables` (lee y limpia las tablas con SQL directo).
    - `./mvnw test -Dtest='CatalogRepositoryAdaptersTest'`: 6 tests, 0 fallos, contra PostgreSQL 18 de Testcontainers: insertar y actualizar por `id` (nombre, `description` en `null`, deshabilitada); un profesional que cambia de categoría; un horario con su día y sus horas locales; quitar los que no están en la lista; quitar todo con la lista vacía; el estado empieza vacío, guarda lo que se le da y sigue siendo una sola fila.
    - Prueba en negativo: cubierta por la segunda de TASK-03 (orden de los borrados), que pasa por estos adaptadores.

- [x] **TASK-03 · Reemplazar la copia y registrar un fracaso**
  - Alcance: puertos de entrada `ReplaceCatalogUseCase` y `RecordSyncFailureUseCase`, y sus implementaciones. `ReplaceCatalogUseCaseImpl` con `@Transactional`: guarda en orden categorías, profesionales y horarios, quita lo que falta, y registra versión, `FULL`, `SUCCESS` y los dos instantes (RF-03, RF-04). `RecordSyncFailureUseCaseImpl` en su propia transacción: último intento con `FULL`, `ERROR` y el tipo, sin tocar la versión ni el último éxito (RF-14). Sin `CatalogService` ni `CatalogException` (P4).
  - Depende de: TASK-02
  - Resuelve: CA-01 y CA-02 (parte de base), CA-07 (parte de base), CA-15 (parte de base)
  - Validación: tests contra la base real: reemplazo con la base vacía; reemplazo con una entidad modificada, una nueva, una deshabilitada, una que falta de cada colección y un profesional que cambia de categoría; un fracaso después de un éxito; un disparador de PostgreSQL creado por el test que hace fallar un `INSERT` a mitad del reemplazo, y la base sin cambios después.
  - Resultado: 2026-11-17. Creados `ReplaceCatalogUseCase`, `RecordSyncFailureUseCase` y sus implementaciones, sin `CatalogService` ni `CatalogException`.
    - `./mvnw test -Dtest='ReplaceCatalogUseCaseImplTest,RecordSyncFailureUseCaseImplTest'`: 6 tests, 0 fallos, contra PostgreSQL real. Reemplazo con la base vacía (con entidades deshabilitadas; versión 303, `FULL`, `SUCCESS`, sin tipo de error, el mismo instante como último intento y último éxito); reemplazo de una copia existente (categoría renombrada, una nueva y una que falta; un profesional movido a la categoría nueva y deshabilitado, uno igual, uno nuevo y uno que falta; un horario con otras horas y otra duración, uno nuevo y uno que falta): la base queda exactamente como el snapshot nuevo, versión 304; un snapshot vacío deja el catálogo inicializado y sin entidades; con un disparador de PostgreSQL que hace fallar el `INSERT` del horario 999, el reemplazo lanza la excepción y las cuatro tablas quedan idénticas a antes; un fracaso después de un éxito cambia solo el último intento (versión, último éxito y copia intactos); un fracaso sin éxito previo deja el catálogo sin inicializar.
    - Pruebas en negativo: sin `@Transactional` en `replaceCatalog`, falla el test del disparador (la base quedó a medias); con los borrados en orden inverso (categorías primero), dos tests fallan con `DataIntegrityViolationException` por la clave foránea. Archivo restaurado con el mismo hash (`8fd2ebbd1ee515d8`) después de cada prueba.
    - Regresión de la tanda: `./mvnw clean test`, 156 tests en 20 clases (los 135 de X01 más los 21 nuevos), 0 fallos, 0 errores, 0 salteados, contados en los reportes XML. Los cinco chequeos de la sección 12 de la skill, sin resultados.

## Etapa 2 · El snapshot en `catedra`

- [ ] **TASK-04 · Modelo y política del snapshot**
  - Alcance: en `catedra/domain/model`, los `record` `CatalogSnapshot`, `SnapshotCategory`, `SnapshotProfessional` y `SnapshotWeeklySchedule`, con la detección de ids repetidos y referencias rotas (P3); `CatalogSnapshotPolicy` (P8); los resultados sellados `CatalogSnapshotResult` y `SnapshotResponse`. Puertos `GetCatalogSnapshotUseCase` (entrada) y `CatalogSnapshotClient` (salida), sin implementar.
  - Depende de: ninguna
  - Resuelve: CA-03 (ids repetidos y referencias), base de RF-06
  - Validación: unitarios de `CatalogSnapshot` (válido; id repetido en cada colección; profesional con categoría inexistente; horario con profesional inexistente; referencias a entidades deshabilitadas, que son válidas) y de `CatalogSnapshotPolicy` (esperas de 1, 2, 4 y 8 s con tope).
  - Resultado: pendiente

- [ ] **TASK-05 · Cliente HTTP del snapshot**
  - Alcance: `src/test/resources/catedra/snapshot-200.json` (recorte del snapshot real con datos ficticios y el mismo formato, P10); DTO del snapshot en `catedra/infrastructure/client/dto`; `CatalogSnapshotMapper` (valida los datos y formatos de D4 y entrega `Invalid` con el motivo; ignora campos desconocidos, `createdAt`, `updatedAt` y `generatedAt`); `CatedraSnapshotClientAdapter` (un intento, clasificación por familia, RF-13); sección `snapshot` de `CatedraProperties` con los valores de D8; ruta del snapshot en `FakeCatedraServer`.
  - Depende de: TASK-04
  - Resuelve: CA-03 (datos y formatos), CA-09, CA-12, CA-14 (parte HTTP), parte HTTP de CA-04 a CA-06
  - Validación: unitarios del mapper (cada defecto de CA-03; cada caso aceptado de CA-12; horas `HH:mm` y `HH:mm:ss`; el recorte se lee completo); adaptador contra la cátedra simulada: `200`, `200` no JSON, `401`, `403`, `400`, `404`, `429`, `500`, `502`, `503`, `504`, demora mayor al timeout y puerto cerrado, cada uno con su resultado, y el JWT en el encabezado `Authorization`.
  - Resultado: pendiente

- [ ] **TASK-06 · Pedido del snapshot con sesión, renovación y reintentos**
  - Alcance: `GetCatalogSnapshotUseCaseImpl` (sesión con `GetTechnicalSessionUseCase` o, si no hay, renovación sin sesión rechazada; `401` con una renovación y un segundo pedido; reintentos con `CatalogSnapshotPolicy`; log de cada intento sin el JWT); `CatedraClientConfiguration` arma `CatalogSnapshotPolicy`; cliente del snapshot falso para los tests. `TechnicalLoginPolicy` y el resto de X01 no cambian.
  - Depende de: TASK-05
  - Resuelve: CA-04, CA-05, CA-06, CA-13, CA-14, CA-10 (parte de `catedra`)
  - Validación: unitarios con el cliente falso y un servicio de sesión falso: `401` y después `200`; `401` dos veces; renovación sin sesión; sesión no habilitada con y sin renovación exitosa; `503` hasta agotar los intentos y `503` seguido de `200`; `403` y otros `4xx` con un solo pedido; `Invalid` sin reintento; captura de la salida sin el JWT de test.
  - Resultado: pendiente

## Etapa 3 · El proceso (`synchronization`) e integración

- [ ] **TASK-07 · Sincronización completa**
  - Alcance: `SynchronizationLock` en `synchronization/application/support` (P7); `FullSyncResult`; puerto `SynchronizeFullCatalogUseCase` y `SynchronizeFullCatalogUseCaseImpl` (fachada sobre `catedra` y `catalog`, P2; traducción del snapshot a los modelos de `catalog` en un método aparte; tipo de error de cada fracaso; toda excepción del reemplazo como `PERSISTENCE_FAILURE`; si el registro del fracaso también falla, solo log); `SynchronizationService`.
  - Depende de: TASK-03, TASK-06
  - Resuelve: CA-07 (parte del proceso), CA-08, RF-10, RF-12
  - Validación: unitarios con puertos falsos: cada resultado de `catedra` lleva a su tipo de error y a `Failed`; el éxito lleva a `Applied` con la versión; un reemplazo que lanza una excepción lleva a `PERSISTENCE_FAILURE`, y con el registro también fallando, el error en el log; dos hilos a la vez con la cátedra demorada (un solo pedido del snapshot y un `Discarded`); un pedido hecho con el candado ya tomado por el mismo hilo corre; la traducción con todos los datos completos no pierde ninguno.
  - Resultado: pendiente

- [ ] **TASK-08 · Integración completa con la cátedra simulada**
  - Alcance: tests `*IntegrationTest` con la aplicación completa, `FakeCatedraServer` y el PostgreSQL de Testcontainers, un escenario por clase (`CLAUDE.md`, "Tests"). Sin cambios de código, salvo correcciones que surjan, que se anotan acá.
  - Depende de: TASK-07
  - Resuelve: CA-01, CA-10, CA-15
  - Validación: sincronización con la base vacía (tablas iguales al recorte, estado y resultado `Applied`); un éxito y después un fracaso (`503` hasta agotar), con la versión y el último éxito intactos; captura de la salida con los intentos y el resultado, sin el JWT de test.
  - Resultado: pendiente

## Etapa 4 · E2E y cierre

- [ ] **TASK-09 · Perfil `e2e` y E2E contra la cátedra real**
  - Alcance: `pom.xml` (propiedades `tests.groups` y `tests.excluded-groups`, filtro de surefire y perfil `e2e` con el `.env` local, como turnos); `FullSynchronizationE2ETest` con `@Tag("e2e")` (P10).
  - Depende de: TASK-08
  - Resuelve: CA-11
  - Validación: `./mvnw test` no corre el E2E (contado en los reportes XML); el usuario, después de ver la lista de pedidos (un login técnico y dos `GET` del snapshot), corre `./mvnw test -Pe2e` en su terminal; se revisa el reporte sin secretos: versión y cantidades de la base iguales a las del snapshot leído en la misma corrida. Si la cátedra no está disponible, queda como no verificado.
  - Resultado: pendiente

- [ ] **TASK-10 · Cierre: regresión, chequeos y documentación**
  - Alcance: corrida completa y chequeos de la skill; `CLAUDE.md` (slices `catalog` y `synchronization`, excepciones de C01, comando `-Pe2e`, ayudantes de test nuevos, decisión 002, texto de "Slices acordados"); borrar el `snapshot.json` del scratchpad. Sin cambios de código, salvo correcciones que surjan.
  - Depende de: TASK-01 a TASK-09
  - Resuelve: comprobación de todos los CA; `AGENTS.md`, "Código" (comandos documentados)
  - Validación: `./mvnw clean verify` en verde con el reporte de JaCoCo; los cinco chequeos de la sección 12 de la skill sin resultados fuera de las excepciones autorizadas (P9) y las dependencias de la fachada (P2); cada comando nuevo del `CLAUDE.md` ejecutado; revisión de que cada CA tiene su evidencia en este archivo.
  - Resultado: pendiente
