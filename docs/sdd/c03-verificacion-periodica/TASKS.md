# TASKS: C03 — Verificar la versión del catálogo periódicamente

SPEC: `docs/sdd/c03-verificacion-periodica/SPEC.md` (aprobada el 2026-11-18). PLAN: `docs/sdd/c03-verificacion-periodica/PLAN.md` (aprobado el 2026-11-18). Tareas aprobadas por el usuario el 2026-11-18; la implementación sigue su autorización, tanda por tanda (tanda 1: TASK-01 y TASK-02, autorizada el 2026-11-18).

Las tareas siguen el "Orden de implementación" del PLAN y cada una deja la suite en verde. Toda tarea con tests incluye, después de que pasan, una prueba en negativo: romper el código a propósito, ver fallar el test, restaurar el archivo y comprobar el hash. Los tests se cuentan con los reportes XML (`target/surefire-reports/TEST-*.xml`); al empezar C03 hay 248. Ninguna tarea llama a la cátedra real ni a su Redis, salvo TASK-14, que corre el usuario.

## Etapa 1 · Renombres y mapper común (sin cambio de comportamiento)

- [x] **TASK-01 · Renombrar los modelos y DTO de las entidades a `Catedra…`**
  - Alcance: `catedra/domain/model`: `SnapshotCategory`, `SnapshotProfessional` y `SnapshotWeeklySchedule` pasan a `CatedraCategory`, `CatedraProfessional` y `CatedraWeeklySchedule`; `catedra/infrastructure/client/dto`: `Snapshot…Response` pasa a `Catedra…Response`; sus usos en `CatalogSnapshot`, `CatalogSnapshotResponse`, `CatalogSnapshotMapper`, `CatalogSnapshots` y `CatalogSnapshotTest` (P5.2). No cambia ninguna regla ni ningún test de comportamiento. `CatalogSnapshot`, `CatalogSnapshotResponse` y `CatalogSnapshotMapper` conservan el nombre.
  - Depende de: ninguna
  - Resuelve: base de RF-15 (P5.2)
  - Validación: `./mvnw test` con los mismos 248 tests en verde; `grep` sin restos de `Snapshot(Category|Professional|WeeklySchedule)` en `src/`.
  - Resultado: 2026-11-18. Línea base antes de tocar nada: `./mvnw -q test`, 248 tests en 27 clases, 0 fallos ni errores (reportes XML). Los seis archivos se renombraron con `git mv` (git los registra como renombres) y se cambiaron con Edit el nombre de cada clase y su comentario ("tal como la publica la cátedra, en el snapshot y en Redis"); se ajustaron los usos en `CatalogSnapshot`, `CatalogSnapshotResponse`, `CatalogSnapshotMapper`, `CatalogSnapshots` y `CatalogSnapshotTest`. `grep -rnE 'Snapshot(Category|Professional|WeeklySchedule)' src/` sin resultados. `./mvnw -q test`: 248 tests en verde, los mismos de la base. Sin prueba en negativo: es un renombre sin comportamiento nuevo, y lo comprueban la compilación y la suite (corrida además desde cero en TASK-02, para descartar clases viejas compiladas).

- [x] **TASK-02 · Mapper común de las entidades de la cátedra**
  - Alcance: `catedra/infrastructure/client/mapper/CatedraEntityMapper` (nuevo), con la revisión y la conversión de cada categoría, profesional y horario, sacadas de `CatalogSnapshotMapper` sin cambiar sus reglas; `CatalogSnapshotMapper` revisa solo el sobre del snapshot y delega cada elemento (P5.1). Tests nuevos de `CatedraEntityMapper` por entidad.
  - Depende de: TASK-01
  - Resuelve: base de RF-15 y de CA-14 (P5.1)
  - Validación: `CatalogSnapshotMapperTest` y `CatedraSnapshotClientAdapterTest` en verde sin cambios en lo que comprueban; `CatedraEntityMapperTest` con un caso por cada defecto y cada caso aceptado de RF-15 (sin `description`, horas `HH:mm` y `HH:mm:ss`, campos desconocidos); `./mvnw test` en verde.
  - Resultado: 2026-11-18. Creado `CatedraEntityMapper` (`problemsOf` y `toDomain` de cada entidad, con las mismas reglas y mensajes que tenía `CatalogSnapshotMapper`; quien llama dice cómo nombrar la entidad en los mensajes, por ejemplo `professionals[0]`). `CatalogSnapshotMapper` revisa la versión y las tres colecciones, recibe el mapper común por constructor y delega cada elemento. Dos tests cambian solo la línea que crea el mapper (`new CatalogSnapshotMapper(new CatedraEntityMapper())`): `CatalogSnapshotMapperTest` y `CatedraSnapshotClientAdapterTest`; lo que comprueban no cambió. Creado `CatedraEntityMapperTest` (29 tests), que lee cada entidad desde un JSON suelto, como llegará de Redis.
    - `./mvnw -q clean test`: 277 tests en 28 clases (248 + 29), 0 fallos ni errores. `CatalogSnapshotMapperTest` 36 y `CatedraSnapshotClientAdapterTest` 16, en verde.
    - Prueba en negativo: se quitó la regla del apellido en `CatedraEntityMapper`; fallaron 4 tests, dos de `CatedraEntityMapperTest` y dos de `CatalogSnapshotMapperTest` (prueba que el del snapshot delega de verdad). Se restauró la línea, el `sha256sum` coincide con el original y los dos tests vuelven a pasar.

## Etapa 2 · El estado de sincronización

- [x] **TASK-03 · Un fracaso no cambia el tipo (D11)**
  - Alcance: `CatalogSyncState.recordFailure` deja de asignar el tipo; `RecordSyncFailureUseCase` y su implementación pierden el parámetro del tipo; `SynchronizeFullCatalogUseCaseImpl` lo llama sin tipo (P8). Tests: `CatalogSyncStateTest`, `RecordSyncFailureUseCaseImplTest`, `SynchronizeFullCatalogUseCaseImplTest` y el escenario de CA-15 de C01 en `FullSynchronizationIntegrationTest`.
  - Depende de: ninguna
  - Resuelve: RF-13; CA-08 (parte de la sincronización completa); CA-15 de C01 con su nota
  - Validación: tests del modelo y del caso de uso: después de un éxito `FULL`, cada fracaso deja el tipo `FULL`; antes del primer éxito, el tipo queda vacío; la versión y el último éxito no cambian. Integración de C01 con un éxito y después un fracaso: el tipo sigue siendo el del éxito. `./mvnw test` en verde.
  - Resultado: 2026-11-18 (tanda 2, autorizada ese día). `CatalogSyncState.recordFailure(errorType, instante)` ya no recibe ni asigna el tipo; `RecordSyncFailureUseCase.recordSyncFailure(errorType)` perdió el parámetro, y `SynchronizeFullCatalogUseCaseImpl` lo llama así. Tests ajustados: `CatalogSyncStateTest` (llamadas sin tipo; nuevo `aFailureKeepsTheTypeOfTheLastSyncThatAppliedChanges`, con un éxito `INCREMENTAL` antes del fracaso; el fracaso antes del primer éxito comprueba además el tipo vacío), `RecordSyncFailureUseCaseImplTest` (llamadas sin tipo; antes del primer éxito, `last_sync_type` vacío en la tabla), el registro falso de `SynchronizeFullCatalogUseCaseImplTest` (ya no comprueba un tipo), y el escenario de CA-15 de C01 en `FullSynchronizationIntegrationTest`: después del éxito, el test marca con SQL `last_sync_type = 'INCREMENTAL'` (como si la última sincronización con cambios hubiera sido incremental), y después del fracaso de la completa la tabla sigue diciendo `INCREMENTAL`. Sin ese paso el escenario no distinguía la regla vieja de la nueva, porque hoy solo hay sincronizaciones `FULL`.
    - `./mvnw -q test`: 278 tests en 28 clases (277 + 1), 0 fallos ni errores.
    - Prueba en negativo: se volvió a poner `lastSyncType = SyncType.FULL` en `recordFailure`; fallaron 4 tests, uno por nivel (`CatalogSyncStateTest` ×2, `RecordSyncFailureUseCaseImplTest` ×1 y `FullSynchronizationIntegrationTest` ×1). Restaurado, `sha256sum` coincide.

- [x] **TASK-04 · Leer el estado y registrar la verificación al día**
  - Alcance: `CatalogSyncState.recordUpToDateCheck(instante)`; puertos `GetCatalogSyncStateUseCase` y `RecordUpToDateCheckUseCase` con sus implementaciones (la que escribe, con `@Transactional`) (P8).
  - Depende de: TASK-03
  - Resuelve: RF-08 (catálogo al día); CA-01 (parte del estado); CA-08 (la verificación que corrige no toca el tipo)
  - Validación: unitarios del modelo: con el último resultado `SUCCESS` no cambia nada; con `ERROR` queda `SUCCESS`, el instante nuevo y el tipo de error vacío, sin tocar la versión, el último éxito ni el tipo. Integración de los dos casos de uso contra la base real (lo que llega a la tabla, leído con SQL directo).
  - Resultado: 2026-11-18. `CatalogSyncState.recordUpToDateCheck(instante)` devuelve si cambió algo: solo con el último resultado `ERROR` deja `SUCCESS`, el instante y el tipo de error vacío. Creados `GetCatalogSyncStateUseCase` e implementación (devuelve el estado guardado) y `RecordUpToDateCheckUseCase` e implementación (`@Transactional`; guarda solo si el modelo cambió, así una verificación al día normal no escribe). Tests nuevos: tres en `CatalogSyncStateTest` (después de un éxito no cambia; después de un fracaso registra el intento exitoso sin tocar versión, último éxito ni tipo; antes de toda sincronización no cambia), `RecordUpToDateCheckUseCaseImplTest` (2, base real: después de un éxito la fila queda idéntica; después de un fracaso, `SUCCESS` sin tocar `local_version`, `last_successful_sync_at` ni `last_sync_type`) y `GetCatalogSyncStateUseCaseImplTest` (2, base real: vacío sin inicializar; lo leído es lo guardado).
    - `./mvnw -q test`: 285 tests en 30 clases (278 + 7), 0 fallos ni errores.
    - Prueba en negativo: se quitó la condición "solo si el último resultado fue `ERROR`"; fallaron 3 tests (`CatalogSyncStateTest` ×2 y `RecordUpToDateCheckUseCaseImplTest.aCheckAfterASuccessDoesNotWriteAnything`). Restaurado, `sha256sum` coincide y vuelven a pasar.

## Etapa 3 · Aplicar una versión en `catalog`

- [ ] **TASK-05 · Aplicar una versión con sus referencias, en una transacción**
  - Alcance: lectura por ids en `ProfessionalCategoryRepository` y `ProfessionalRepository` y sus adaptadores; `catalog/application/exception/CatalogException` y `CatalogVersionNotVerifiableException`; `ApplyCatalogVersionUseCase` y su implementación con `@Transactional`: resuelve cada referencia "solo con id" (primero en la versión, después en la copia), lanza la excepción antes de escribir si falta, guarda con `saveAll`, avanza la versión y registra `INCREMENTAL` `SUCCESS` (P7, P13.1).
  - Depende de: TASK-03
  - Resuelve: RF-05, RF-10; CA-02, CA-11 (referencias) y CA-13 (parte de la base)
  - Validación: integración contra la base real, sin `@Transactional` en el test: una versión con una entidad nueva, una modificada y una deshabilitada de cada colección; un profesional que apunta a una categoría de la copia y otro a una de la misma versión; un horario que apunta a un profesional de la versión. La copia queda con los cambios, nada se quita, la versión avanza y el estado registra `INCREMENTAL` `SUCCESS` con los dos instantes. Referencia rota: excepción y ni un dato cambiado. Falla de la base a mitad (disparador de PostgreSQL, como en C01): datos y versión anteriores intactos. Una referencia "solo con id" llega a la base con la categoría completa, sin borrarle el nombre.
  - Resultado: pendiente

## Etapa 4 · Redis en `catedra`: el cliente

- [ ] **TASK-06 · Dependencia de Redis y Redis de test con el ACL de la cátedra**
  - Alcance: `pom.xml`: `io.lettuce:lettuce-core` en lugar de `spring-boot-starter-data-redis`, y sin `spring-boot-starter-data-redis-test` (P3.2). En `src/test/java`: un Redis 7.4 en contenedor (`GenericContainer`) con un usuario como el de la cátedra (lectura sobre `catedra:sync:*`, `PING` prohibido) y una conexión de administración para cargar datos y pausar clientes; datos de ejemplo de Redis (metadata, `changes:{v}` y los tres Hash, con la forma de la lectura real y valores ficticios) (P12.1).
  - Depende de: ninguna
  - Resuelve: base de CA-09, CA-10, CA-14 y de la integración (P12.1)
  - Validación: `./mvnw test` en verde (la aplicación arranca sin la conexión automática de Redis: ningún bean `RedisConnectionFactory` en el contexto); un test del contenedor comprueba que el usuario de test recibe `NOPERM` al mandar `PING` y puede leer `catedra:sync:*`, como en la cátedra.
  - Resultado: pendiente

- [ ] **TASK-07 · Modelos, política, propiedades y mapper de Redis**
  - Alcance: `catedra/domain/model`: `CatalogMetadata`, `CatalogVersionChanges`, `RedisReadResult` y `CatalogRedisPolicy`; `catedra/domain/ports/out/CatalogRedisClient`; `CatedraProperties` (sección `redis`: intentos, espera inicial, multiplicador, tope, conexión y comando) y `CatedraClientConfiguration` (bean de la política) (D7, P10); `client/mapper/CatalogVersionChangesMapper` y `client/dto/CatalogVersionChangesResponse`: el sobre de `changes:{v}` y la coincidencia del `id` con el field del Hash, usando `CatedraEntityMapper` (RF-14, RF-15).
  - Depende de: TASK-02
  - Resuelve: RF-14, RF-15; CA-11 (contenido), CA-14
  - Validación: unitarios del mapper con un JSON por cada defecto de CA-11 (metadata ausente, sin un campo, no numérica, `currentVersion` menor que `oldestAvailableVersion`; `changes:{v}` ilegible, con `version` distinta de `v`, sin una colección, con ids no numéricos; entidad con un defecto o con `id` distinto del field) y cada caso aceptado de CA-14; unitario de la política (esperas de 1 y 2 s con los valores por defecto).
  - Resultado: pendiente

- [ ] **TASK-08 · Cliente de Redis con Lettuce**
  - Alcance: `catedra/infrastructure/client/CatedraRedisClientAdapter`: conexión con los datos de la integración, `pingBeforeActivateConnection(false)`, RESP2, los timeouts de la política; la guarda abierta junto con los datos con que se armó, la rearma si cambian y la cierra al apagar; lee la metadata (`HGETALL`) y una versión (`GET` de `changes:{v}` y los `HMGET` de los tres Hash en un lote); clasifica cada respuesta en un `RedisReadResult` (P3.1, P4; RF-14).
  - Depende de: TASK-06, TASK-07
  - Resuelve: RF-03, RF-14; CA-09, CA-10 y CA-14 en su parte de adaptador; CA-16 (sin la contraseña en el log)
  - Validación: contra el Redis de test con el ACL: se conecta sin `PING` (comprueba también qué pasa con `CLIENT SETINFO`) y lee la metadata y una versión; con otra contraseña, `WRONGPASS`; con un usuario sin permiso sobre una clave, `NOPERM`; con `CLIENT PAUSE` más largo que el timeout, no disponible; con el contenedor detenido o un puerto cerrado, no disponible; con otros datos de integración, conexión nueva. Unitario de la clasificación de `LOADING`, `BUSY`, `TRYAGAIN`, `MASTERDOWN` y `WRONGTYPE`. La salida capturada no contiene la contraseña de test.
  - Resultado: pendiente

## Etapa 5 · Redis en `catedra`: los casos de uso

- [ ] **TASK-09 · Las dos lecturas de Redis con sesión, renovación y reintentos**
  - Alcance: `catedra/application/support/CatalogRedisReader` (P14); `CatalogMetadataResult` y `CatalogVersionChangesResult`; puertos `GetCatalogMetadataUseCase` y `GetCatalogVersionChangesUseCase` con sus implementaciones (P2); en los tests, un cliente de Redis falso que responde en orden y guarda las integraciones recibidas.
  - Depende de: TASK-07
  - Resuelve: RF-03, RF-12, RF-14; CA-07, CA-09 y CA-10 en su parte de caso de uso
  - Validación: unitarios con el cliente falso y una sesión controlada: sin sesión, una renovación sin sesión rechazada y, si no hay, no disponible sin ningún intento; `WRONGPASS`, `NOAUTH` y `NOPERM`, una renovación y un segundo intento con la integración nueva, y rechazado si vuelve; renovación sin sesión, no disponible; no disponible, hasta 3 intentos con esperas crecientes (en milisegundos en el test) y obtenido si uno responde; inválido sin reintentar. Log de cada intento.
  - Resultado: pendiente

- [ ] **TASK-10 · Espera del login técnico inicial**
  - Alcance: `catedra/domain/model/InitialLoginWait`; `AwaitInitialTechnicalSessionUseCase` y su implementación (consulta el estado cada cierto tiempo hasta que deja de estar "conectando" o se cumple el tope); sección `initial-login` de `CatedraProperties` y su bean (P6, P10).
  - Depende de: ninguna
  - Resuelve: RF-01; CA-06 (parte de `catedra`)
  - Validación: unitarios con un repositorio de sesión controlado y esperas en milisegundos: la sesión pasa a habilitada antes del tope, devuelve la sesión; pasa a degradada, devuelve vacío; sigue "conectando" al cumplirse el tope, devuelve vacío; ya habilitada al empezar, devuelve enseguida. Ningún cambio en X01.
  - Resultado: pendiente

## Etapa 6 · La verificación en `synchronization`

- [ ] **TASK-11 · Verificación de la versión del catálogo**
  - Alcance: `synchronization/domain/model`: `VerificationResult` y `VerificationPolicy`; `VerifyCatalogVersionUseCase` y `VerifyCatalogVersionUseCaseImpl` (candado, espera del login en la primera, estado local, base vacía con C01 directo, metadata, comparación, recorrido de las versiones con su aplicación y reintento ante una falla de la base, discontinuidad con C01 y una sola segunda pasada, registro del fracaso o de la verificación al día) (P9, P10); `SynchronizationService.verifyCatalogVersion`.
  - Depende de: TASK-04, TASK-05, TASK-09, TASK-10
  - Resuelve: RF-01 a RF-15; CA-01, CA-02, CA-03, CA-04, CA-05, CA-07, CA-11, CA-12 y CA-13 en su parte de proceso; CA-16 (log del resultado)
  - Validación: unitarios con puertos falsos que responden en orden: al día (con el último intento exitoso y fallido); atrasada con varias versiones; versión local menor que `oldestAvailableVersion`, mayor que `currentVersion` e igual a `oldestAvailableVersion`; base vacía con Redis disponible y caído; candado tomado por otro hilo; sin sesión; cada resultado inválido de Redis y la referencia rota (discontinuidad); discontinuidad que persiste (una sola C01 e `INVALID_DATA`); C01 que falla (sin otro registro); falla de la base en la segunda de tres versiones, con reintento exitoso y con reintentos agotados; registro del fracaso que también falla (solo log). En cada uno: qué se leyó, qué se pidió y qué se registró.
  - Resultado: pendiente

## Etapa 7 · El disparador y la integración completa

- [ ] **TASK-12 · Disparador periódico**
  - Alcance: `catalogo/config/SchedulingConfig` (`@EnableScheduling`); `synchronization/infrastructure/scheduling/CatalogVerificationScheduler` (`@Scheduled` con espera fija, primera corrida al arrancar, apagable con `catalogo.synchronization.verification.enabled`), `VerificationProperties` y `VerificationConfiguration` (intervalo y reintentos de persistencia) (P10, P11); `pom.xml`: Surefire apaga la verificación en todos los tests, también en `-Pe2e`.
  - Depende de: TASK-11
  - Resuelve: RF-01, RF-02; CA-06 y CA-15
  - Validación: `./mvnw test` en verde con la verificación apagada en los tests existentes (ningún test de C01 la ve correr); integración con la verificación encendida y un intervalo corto: se repite con ese intervalo y una verificación demorada (Redis pausado) no se solapa con la siguiente; integración con la cátedra simulada demorando el login inicial: la primera verificación espera y sigue, y el servicio responde mientras tanto.
  - Resultado: pendiente

- [ ] **TASK-13 · Integración completa con Redis de test y cátedra simulada**
  - Alcance: tests `*IntegrationTest` con la aplicación completa, Redis en contenedor con el ACL, la cátedra simulada y la base real.
  - Depende de: TASK-12
  - Resuelve: CA-01, CA-02, CA-03, CA-05, CA-11, CA-12 y CA-16 de punta a punta
  - Validación: base vacía, la verificación del arranque carga el snapshot y después lee Redis; copia atrasada, aplica las versiones de Redis y la copia queda igual a los Hash; versión anterior a `oldestAvailableVersion`, snapshot y segunda pasada; un `changes:{v}` mal formado, snapshot; discontinuidad que persiste, `INVALID_DATA`; Redis detenido con la base vacía, copia cargada y `SOURCE_UNAVAILABLE`. La salida no contiene el JWT ni la contraseña de Redis de test.
  - Resultado: pendiente

## Etapa 8 · E2E

- [ ] **TASK-14 · E2E de la verificación contra la cátedra real**
  - Alcance: `src/test/java/e2e/` (`@Tag("e2e")`): aplicación completa con la verificación encendida, base descartable de Testcontainers y la cátedra real del `.env`. Tres escenarios: base vacía (la verificación del arranque llega a `currentVersion`); versión local bajada con SQL a `currentVersion − 3` (incremental hasta `currentVersion`, tipo `INCREMENTAL`); versión local bajada a `oldestAvailableVersion − 1` (snapshot, tipo `FULL`) (P12.2). Antes de correrlo, se le pasa al usuario la lista exacta de pedidos a la cátedra y a Redis.
  - Depende de: TASK-13
  - Resuelve: CA-17
  - Validación: el usuario corre `./mvnw test -Pe2e` en su terminal; se leen los reportes y las líneas del resultado sin secretos, y se borra lo temporal. Si la cátedra no está disponible, queda como no verificado. Además: se saltea sin credenciales en el `.env` (comprobado con `-Dtests.env-file=<archivo inexistente>`).
  - Resultado: pendiente

## Etapa 9 · Cierre

- [ ] **TASK-15 · Regresión, chequeos y documentación**
  - Alcance: corrida completa y chequeos de la skill; `CLAUDE.md` (slices, excepciones de C03 de la P13, cómo usar Redis y la espera del login, tabla de propiedades ajustables incluidas las del snapshot y del login, el tipo ante un fracaso en "Cómo pedir la sincronización completa", ayudantes de test nuevos, lo comprobado con el E2E); `docs/decisiones/003` (qué guarda el estado de sincronización: D7 de C01, D4, D5 y D11 de C03). Sin cambios de código, salvo correcciones que surjan.
  - Depende de: TASK-01 a TASK-14
  - Resuelve: comprobación de todos los CA; `AGENTS.md`, "Código"
  - Validación: `./mvnw clean verify` en verde con el reporte de JaCoCo; los cinco chequeos de la sección 12 de la skill sin resultados fuera de las excepciones autorizadas (P13) y de la fachada (P1); copias de los documentos comparadas (`contratos_comunes.md` y X01 en los tres repositorios; los de catálogo entre `documentacion` y `docs/`); revisión de que cada CA tiene su evidencia en este archivo.
  - Resultado: pendiente
