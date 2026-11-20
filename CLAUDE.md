# catalog_service

Servicio de catálogo y sincronización. Los comandos se ejecutan desde la raíz de este repositorio.

Las instrucciones generales del proyecto están en `documentacion/AGENTS.md`. El servicio de turnos (`turnos_service`, con su `CLAUDE.md`) ya está terminado y sirve de modelo; no se copia código entre repositorios.

## Requisitos

- Docker en ejecución: lo usan el compose y los tests.
- JDK 25.
- Un archivo `.env` creado a partir de `.env.example`. No se versiona. Lo leen docker compose y la aplicación. Los valores van sin comillas: la aplicación toma las comillas como parte del valor.
  - Obligatoria: `CATALOGO_DB_PASSWORD`. Sin ella el compose se niega a levantar y la aplicación no arranca (PostgreSQL rechaza la conexión).
  - Base: `CATALOGO_DB_NAME`, `CATALOGO_DB_USER` y `CATALOGO_DB_PORT` (por defecto `catalogo`, `catalogo` y `5433`).
  - Cuenta técnica de la cátedra: `CATALOGO_CATEDRA_BASE_URL`, `CATALOGO_CATEDRA_USERNAME` y `CATALOGO_CATEDRA_PASSWORD`. Es la misma cuenta que usa turnos. Si falta alguna, o la URL no es `http` o `https` válida, el servicio arranca con un warning y sin integración con la cátedra.
  - Opcional: `CATALOGO_PORT` (8081 por defecto; el 8080 lo usa turnos).
  - Opcionales: los tiempos, los reintentos y la verificación periódica (ver "Propiedades ajustables").
- La suite normal de tests nunca lee el `.env` ni llama a la cátedra real.

## Comandos

Todos se ejecutaron y su resultado se observó (TASKS de X01, C01 y C03).

| Para | Comando |
| --- | --- |
| Levantar la base de desarrollo y esperar a que esté lista | `docker compose up -d --wait catalog-db` |
| Ver el estado de los contenedores | `docker compose ps` |
| Correr la aplicación (necesita la base levantada) | `./mvnw spring-boot:run` |
| Correr todos los tests (Testcontainers crea su propio PostgreSQL; no usan el compose) | `./mvnw test` |
| Correr una o varias clases de test | `./mvnw test -Dtest='Clase1,Clase2'` |
| Compilar desde cero y correr los tests | `./mvnw clean test` |
| Tests más reporte de cobertura de JaCoCo (`target/site/jacoco/jacoco.xml`), lo mismo que corre GitHub Actions | `./mvnw clean verify` |
| Apagar los contenedores conservando los datos | `docker compose down` |
| Solo los tests E2E, contra la cátedra real con las credenciales del `.env` (los corre el usuario, después de ver la lista de pedidos; sin credenciales se saltean) | `./mvnw test -Pe2e` |

`docker compose down -v` borra el volumen de la base (`catalog_service_catalog-db-data`): no usarlo para limpiar una prueba. Para una prueba descartable, otro proyecto de compose con otro volumen y otro puerto, y después `down` sin `-v` y `docker volume rm` del volumen de prueba.

## Datos del proyecto

- Spring Boot 4.1.2, Java 25, Maven con wrapper.
- `{basePackage}` de la skill `hexagonal-vertical-slicing`: `catalogo`. Clase principal: `CatalogoApplication`.
- Variables de entorno con prefijo `CATALOGO_` y propiedades propias bajo `catalogo.`.
- La aplicación escucha en el 8081 y la base se publica en el 5433 de la máquina; los dos puertos son configurables.
- `compose.yaml` tiene por ahora solo la base (`catalog-db`, PostgreSQL 18). El `Dockerfile`, la aplicación en el compose y `compose.full-system.yaml` son de la etapa de Docker.
- Dependencias incluidas desde el inicio, aunque algunas todavía no se usan: web, JPA, Flyway, PostgreSQL, validation, Lombok, security, OAuth2 resource server, Kafka y Testcontainers.
- Redis se lee con `io.lettuce:lettuce-core` directo, con la versión que maneja Spring Boot (7.5.2). En C03 reemplazó a `spring-boot-starter-data-redis`, que armaba una conexión automática a `localhost:6379` que nadie usaba (P3.2 del PLAN de C03).
- SonarQube Cloud: organización `programacion2-ignaciomilutin-2026`, proyecto `Programacion2-IgnacioMilutin-2026_catalogo_service`. Lo dispara `.github/workflows/build.yml` en cada pull request y en cada push a `main` y `develop`. `CatalogoApplication` está excluida del cálculo de cobertura. El usuario revisa SonarQube por su cuenta: no va en los TASKS.
- El repositorio remoto se llama `catalogo_service` y la carpeta local, `catalog_service`. El compose del sistema completo de turnos busca `../catalog_service`. El usuario decidió resolverlo en la etapa de Docker.

## Arquitectura

Se aplica la skill `hexagonal-vertical-slicing` (`documentacion/.claude/skills/hexagonal-vertical-slicing/SKILL.md`). La estructura inicial del proyecto se hizo sin SDD: cada paquete lo crea el SDD del caso de uso que lo usa.

Slices actuales:

| Paquete | Qué contiene |
| --- | --- |
| `catalogo.catedra` | Pasarela hacia la cátedra, la única puerta. La sesión técnica (X01): login técnico al arrancar, estado en memoria y renovación a pedido. El pedido del snapshot (C01): sesión, renovación ante un `401`, reintentos y validación de la respuesta (`GetCatalogSnapshotUseCase`). C03: la lectura de Redis (`GetCatalogMetadataUseCase` y `GetCatalogVersionChangesUseCase`, con lo común en `CatalogRedisReader`) y la espera del login inicial (`AwaitInitialTechnicalSessionUseCase`). Las reglas de cada entidad están en `CatedraEntityMapper`, que comparten el snapshot y Redis. Más adelante, el consumo de Kafka |
| `catalogo.catalog` | La copia local del catálogo: categorías, profesionales, horarios semanales y el estado de sincronización (`CatalogSyncState`, una sola fila). C01: reemplazar la copia con su versión en una transacción (`ReplaceCatalogUseCase`) y registrar un fracaso (`RecordSyncFailureUseCase`). C03: aplicar una versión incremental en una transacción, comprobando las referencias (`ApplyCatalogVersionUseCase`, que devuelve `ApplyCatalogVersionResult`), leer el estado (`GetCatalogSyncStateUseCase`) y corregir un fracaso cuando el catálogo está al día (`RecordUpToDateCheckUseCase`). Más adelante, las consultas de C04 a C06 |
| `catalogo.synchronization` | El proceso de sincronización. C01: la sincronización completa (`SynchronizeFullCatalogUseCase`, `SynchronizationService`) y la regla de una sola sincronización a la vez (`SynchronizationLock`). C03: la verificación periódica (`VerifyCatalogVersionUseCase`, que devuelve `VerificationResult`), con el procedimiento incremental adentro, y su disparador (`infrastructure/scheduling/CatalogVerificationScheduler`). Más adelante, C02 y C07 |
| `catalogo.config` | Configuración transversal, sin lógica de negocio: `ClockConfig` y `SchedulingConfig` (`@EnableScheduling`) |

Estructura decidida en C01 (decisión 002): la copia local va en un solo slice, `catalog`, porque las tres entidades se escriben siempre juntas por versión, se referencian entre sí y se leen juntas; el proceso va en `synchronization`. Dependencias en un solo sentido: `synchronization` usa `catedra` y `catalog`; `catalog` y `catedra` no usan a nadie. `synchronization` es una fachada (skill §9): su caso de uso recibe directamente los puertos de entrada de los otros dos y trabaja con sus modelos de dominio (P2 del PLAN de C01). No hay slice `turnos`: catálogo nunca llama a turnos; el endpoint que consume turnos (C06) va en `catalog`.

### Criterios de diseño acordados con el usuario

- Cada slice tiene solo las piezas que alguien usa; no se crean piezas por simetría con otro slice.
- Lo de turnos se adoptó en bloque solo para X01, porque es el mismo caso de uso en los dos servicios. Desde C01, cada excepción a la skill y cada decisión de diseño se analiza en el contexto de catálogo, con sus opciones; turnos es una opción más, no la respuesta por defecto.
- En X01, la decisión sobre el resultado de un login (habilitar, reintentar o quedar degradada, o error de configuración) está en cada caso de uso, `Start` y `Renew`, aunque quede escrita dos veces (P3 del PLAN de X01). Una regla nueva sobre ese resultado va en los dos. El usuario aclaró que no es un criterio general.

### Excepciones a la skill autorizadas por el usuario

Autorizadas para X01 (P1 de su PLAN). No llevan comentario en el código: el usuario los quitó; quedan listadas acá.

- Paquete `catalogo.config` fuera de los slices.
- Paquetes de infraestructura además de `persistence` y `web`: `infrastructure/client` (cliente HTTP hacia la cátedra) e `infrastructure/startup` (disparador al arrancar).
- Repositorio en memoria en `infrastructure/persistence/adapter`, sin base de datos: `InMemoryTechnicalSessionRepositoryAdapter`.
- `record` inmutables en lugar de clases Lombok (`TechnicalSession`, `TechnicalIntegration`, `TechnicalLoginPolicy`) y un resultado sellado (`TechnicalLoginResult`): la sesión se lee en paralelo mientras un login la reemplaza. Sus `toString` ocultan el JWT y la contraseña de Redis.
- Puerto de salida para una capacidad técnica, no un repositorio: `TechnicalLoginClient`.
- `@Configuration` dentro de la infraestructura de un slice: `CatedraClientConfiguration`.

Autorizadas para C01 (P9 de su PLAN), cada una analizada en su contexto:

- Los `id` los asigna la cátedra: las entidades JPA de `catalog` no llevan `@GeneratedValue`.
- `CatalogSyncState` sin `id` en el modelo; su entidad es una sola fila con `id` fijo en 1, que crea la migración.
- `record` inmutables y resultados sellados: `CatalogSnapshot`, `SnapshotCategory`, `SnapshotProfessional`, `SnapshotWeeklySchedule` (renombrados a `Catedra…` en C03), `CatalogSnapshotResult`, `SnapshotFetchResult`, `CatalogSnapshotPolicy` y `FullSyncResult`. Los modelos de `catalog` siguen la skill (clases Lombok).
- `infrastructure/client` en `catedra` también para el cliente del snapshot (`CatedraSnapshotClientAdapter`).
- Puerto de salida para una capacidad técnica: `CatalogSnapshotClient`.
- `application/support` en `synchronization` para `SynchronizationLock`.

Autorizadas para C03 (P13 de su PLAN):

- `infrastructure/client` en `catedra` también para el cliente de Redis (`CatedraRedisClientAdapter`).
- Puerto de salida para una capacidad técnica: `CatalogRedisClient`.
- `record` y resultados sellados en `catedra`: `CatedraCategory`, `CatedraProfessional` y `CatedraWeeklySchedule` (antes `Snapshot…`), `CatalogMetadata`, `CatalogVersionChanges`, `CatalogMetadataResult`, `CatalogVersionChangesResult`, `RedisReadResult`, `CatalogRedisPolicy` e `InitialLoginWait`.
- `record` y resultados sellados en `synchronization`: `VerificationResult` y `VerificationPolicy`.
- Paquete `synchronization/infrastructure/scheduling`, para el disparador y sus propiedades.
- `@Configuration` dentro de la infraestructura de un slice: `VerificationConfiguration`.
- `SchedulingConfig` en `catalogo.config`, dentro de la excepción de X01 para ese paquete.
- Paquete `catedra/application/support`, para `CatalogRedisReader`.
- Resultado sellado en el dominio de `catalog`: `ApplyCatalogVersionResult` (`Applied` o `NotVerifiable`). Es el primer `record` del dominio de `catalog`; sus modelos siguen siendo clases Lombok. Reemplazó a una excepción de `catalog/application/exception`, que la fachada no podía importar (skill §9; P13.1 revisada).

No son excepciones: la fachada de `synchronization` (opción de la skill §9), `@Transactional` en los casos de uso de `catalog` (la skill no lo prohíbe en `application`) y las tres entidades en `catalog` (opción "unir slices", decisión 002). Los repositorios de `catalog` suman métodos fuera del CRUD (`saveAll` actualiza o inserta por `id`; `deleteAllExcept` quita lo que no está en una lista; `findAllByIds` lee por ids, desde C03).

Un resultado que otro slice necesita leer va como resultado sellado en el dominio del slice que lo produce, no como excepción: la fachada solo puede usar el dominio y los puertos de entrada de los otros slices (lo que se aprendió en C03).

### Comentarios y nombres en el código

- El código, sus comentarios y los nombres de los tests no citan documentos: ni el contrato, ni el anexo de la cátedra, ni el enunciado, ni IDs de requisitos. Un test se nombra por el comportamiento que comprueba.
- El usuario prefiere pocos comentarios: uno corto por clase que diga qué es, y alguno más solo donde avisa algo que no se ve en el código. Cambia o borra los que sobran; no se restauran.
- Las referencias a documentos van en `docs/sdd/` y en `docs/decisiones/`.

### Cómo usar la sesión técnica (desde C01)

- En cada operación, pedir la sesión con `GetTechnicalSessionUseCase` (JWT técnico e integración, con los datos de Redis y de Kafka). Devuelve vacío si la integración no está habilitada. Nunca guardar una copia en un campo: así siempre se usa la última.
- Si la cátedra responde `401`, o Redis rechaza las credenciales, llamar a `RenewTechnicalSessionUseCase.renewTechnicalSession(sesionRechazada)` y repetir la operación una sola vez con la sesión que devuelve. Si devuelve vacío, no hay una sesión distinta: la integración no está disponible. Repetir la operación es responsabilidad de cada caso de uso, no de X01.
- Si se necesita la integración y no está habilitada, se puede pedir con `renewTechnicalSession(null)`: intenta un login solo si pasó el intervalo mínimo.
- El control de los logins vive en `catedra` (decisión 001): uno a la vez, como máximo uno cada 60 segundos, y ninguno más hasta reiniciar si hubo un error de configuración. Los demás slices nunca llaman al login de la cátedra.
- Usar `catedra` desde otro slice es una dependencia entre slices: se decide con el usuario en el PLAN (skill §9).
- Las tareas que arrancan con el servicio encuentran la sesión en "conectando": el login inicial corre en un hilo aparte después de que el servicio quedó listo. Para esperarlo, llamar a `AwaitInitialTechnicalSessionUseCase.awaitInitialTechnicalSession()` (desde C03). Consulta la sesión cada 200 ms hasta que deja de estar "conectando", o hasta un tope de 2 minutos, y devuelve la sesión habilitada o vacío. La sesión está "conectando" desde que se crea el bean, así que también espera una tarea que corra antes del `ApplicationReadyEvent`. La verificación la llama antes de tomar el candado.
- Una operación nueva con la cátedra va en `catedra`, como el snapshot: un cliente en `infrastructure/client` que hace **un solo intento** y devuelve un resultado sellado (`SnapshotFetchResult`), y un caso de uso que pide la sesión, renueva una vez ante un `401` (el pedido repetido no cuenta como reintento) y reintenta lo no disponible con su propia política (`GetCatalogSnapshotUseCaseImpl`). Los demás slices reciben el resultado ya resuelto, nunca el JWT.

### Cómo pedir la sincronización completa (desde C02 y C03)

- Por `SynchronizeFullCatalogUseCase` (o `SynchronizationService` desde la infraestructura). Devuelve `Applied(versión)`, `Failed(motivo)` (el motivo empieza con el tipo de error registrado) o `Discarded` (había otra sincronización en curso; no se hizo nada).
- Hay una sola sincronización a la vez, de cualquier tipo: `SynchronizationLock`, en memoria, para una sola instancia. Deja volver a entrar a quien ya lo tiene: C02 y C03 toman el candado para toda su pasada y piden la completa desde adentro sin que se descarte. C01 solo usa `tryAcquire` (no espera); "esperar a tomarlo" se suma con C02.
- El resultado queda en `CatalogSyncState`: un éxito actualiza todo (versión, último intento, último éxito y tipo); un fracaso solo el último intento (instante, `ERROR` y tipo de error), **sin cambiar el tipo**, que es siempre el de la última sincronización que aplicó cambios (D11 de C03). Si la base tampoco acepta el registro del fracaso, queda en el log. Qué guarda la fila en cada caso, y por qué: `docs/decisiones/003`.
- Un snapshot sin categorías se rechaza como `INVALID_DATA` y la copia no se vacía (cambio de D4 de la SPEC de C01).

### Cómo pedir la verificación de la versión (desde C03)

- Por `VerifyCatalogVersionUseCase` (o `SynchronizationService.verifyCatalogVersion()` desde la infraestructura). Primero espera el login inicial y después toma el candado sin esperar. Devuelve:
  - `Skipped`, si había otra sincronización en curso;
  - `UpToDate(versión)`, si la versión local es la actual de Redis;
  - `Updated(versión)`, si aplicó versiones o hizo una completa;
  - `Failed(motivo)`, si falló, con el motivo empezando por el tipo de error registrado.
- Si no hay versión local, pide la completa sin leer Redis, y después lee Redis una vez más. Una discontinuidad (metadata inválida, una versión local fuera de la ventana, o un `changes:{v}` o una entidad inválidos) también pide la completa y otra pasada. Si la discontinuidad sigue después de la completa, queda `INVALID_DATA`, con una sola completa por verificación.
- Cada versión se aplica y se registra en su propia transacción (D5). Una falla de la base se reintenta con `VerificationPolicy`, y al agotarse queda `PERSISTENCE_FAILURE`.
- El disparador (`CatalogVerificationScheduler`) usa una espera fija: la primera verificación corre al arrancar, y cada una empieza recién cuando terminó la anterior, así que nunca se solapan.

### Cómo leer Redis de la cátedra (desde C03)

- Solo desde `catedra`, por `GetCatalogMetadataUseCase` y `GetCatalogVersionChangesUseCase`. Devuelven el resultado resuelto (obtenido, no disponible, rechazado o inválido) y ya validado con las reglas de `CatedraEntityMapper`. Los otros slices nunca ven la integración ni la contraseña de Redis.
- `CatalogRedisReader` hace lo común a las dos lecturas:
  - pide la sesión, o la renovación si no hay;
  - hace un intento con el cliente;
  - si las credenciales son rechazadas (`WRONGPASS`, `NOAUTH` o `NOPERM`), renueva la sesión una vez;
  - si Redis no está disponible (conexión, timeout o cualquier otro error del servidor, como `LOADING` o `BUSY`), reintenta con `CatalogRedisPolicy`.

  Un `WRONGTYPE` o un dato ilegible se informa como inválido, sin reintentar.
- `CatedraRedisClientAdapter` (Lettuce):
  - tiene una sola conexión, abierta entre ciclos, y la rearma si la sesión trae otros datos de Redis;
  - usa RESP2 fijo;
  - lee la metadata con `HGETALL catedra:sync:metadata`;
  - lee cada versión en dos idas y vueltas: `GET catedra:sync:changes:{v}`, y después los `HMGET` de los Hash afectados, en pipeline.
- Hallazgo sobre Lettuce, comprobado con `ACL LOG` en el Redis de test (TASK-08 de C03):
  - con credenciales y RESP2, al conectarse manda `AUTH`, no `PING`, así que `pingBeforeActivateConnection(false)` no cambia nada (queda como defensa);
  - manda `CLIENT SETINFO`, que el ACL rechaza y Lettuce tolera sin error.

### Lo comprobado con la cátedra real

- El login técnico de catálogo funciona con la cuenta del proyecto y responde `PROVISIONED` (2026-11-16).
- Dos JWT técnicos de la misma cuenta conviven: un login nuevo no invalida el anterior. Turnos y catálogo hacen cada uno su login (2026-11-15).
- El snapshot real (2026-11-16 y 2026-11-17): responde `200` en menos de un segundo, unos 14,5 KB; 8 categorías, 24 profesionales y 44 horarios (con algunos deshabilitados), sin datos faltantes, ids repetidos ni referencias rotas; horas `HH:mm:ss`; los instantes no tienen formato fijo (`generatedAt` con nanosegundos, algún `updatedAt` con microsegundos), por eso no se leen. Con el E2E, la sincronización completa de la versión 327 tardó 779 ms (2026-11-17).
- El snapshot real trae los campos en orden alfabético, en el objeto y en cada elemento (2026-11-18). A la lectura no le afecta.
- El Redis de la cátedra (lectura del usuario, 2026-11-18, solo comandos de lectura):
  - Conexión TCP sin TLS, Redis 7.4.11 standalone, `HELLO 2 AUTH usuario clave` aceptado. **`PING` responde `NOPERM`**: el ACL no lo permite. Sin probar: `HELLO 3`.
  - Unos 246 ms por ida y vuelta, para un comando suelto o un pipeline; conectar y autenticarse, unos 740 ms.
  - `catedra:sync:metadata`: Hash sin TTL; `currentVersion` 333 y `oldestAvailableVersion` 234, iguales a `catedra:sync:current-version` y `catedra:sync:oldest-available-version`.
  - `catedra:sync:changes:{v}`: existen de `oldest` a `current` (las 100, sin huecos, incluida `changes:{oldest}`); String sin TTL con `{version, changes: {professionalCategories, professionals, weeklySchedules}}`, ids numéricos y `version` igual a `v`. Las 100 cambiaban solo un profesional, siempre el mismo; ninguna, categorías ni horarios. Se publica más o menos una versión por hora: la ventana es de unos 4 días.
  - Los tres Hash (8, 24 y 44 entradas, sin TTL) eran idénticos al snapshot de la misma versión, con el mismo formato y sin referencias rotas.
- La verificación de C03 contra la cátedra real (E2E, 2026-11-19), con Redis en `currentVersion` 359 y `oldestAvailableVersion` 260:
  - **La conexión de Lettuce con el ACL real funciona**, sin errores ni warnings de Redis en el log.
  - Con la base vacía, la verificación del arranque esperó el login, cargó el snapshot 359 y encontró Redis al día.
  - Desde 356, aplicó tres versiones (un profesional cada una) en 1,9 s, con tipo `INCREMENTAL`.
  - Desde 259, fuera de la ventana, reconstruyó con el snapshot en 0,9 s, con tipo `FULL`.
  - El E2E de C01, en la misma corrida, aplicó el snapshot 359 en 807 ms.
- Las pruebas contra la cátedra real las corre el usuario en su terminal, después de ver la lista de pedidos; la salida se revisa sin mostrar secretos y los archivos temporales se borran.

## Propiedades ajustables

Todas tienen un valor por defecto y ninguna hace falta en el `.env`. Cada una se ajusta con una variable `CATALOGO_…` (en el `.env` o en el entorno), que `application.yaml` pasa a la propiedad con un placeholder `${VARIABLE:valor por defecto}`. Como en turnos, el `.env.example` las lista comentadas y con su valor por defecto (TASK-16 de C03).

El valor por defecto está escrito dos veces, en el yaml y en el `@DefaultValue` del código: si se cambia uno, hay que cambiar el otro. Los mismos números aparecen en más de una sección a propósito (P10 del PLAN de C03): cada una se ajusta por separado.

Con una variable presente pero vacía (comprobado en TASK-16 de C03):

- una duración toma el valor por defecto;
- un número (intentos o multiplicador) no se puede leer, y la aplicación no arranca;
- `CATALOGO_VERIFICATION_ENABLED` apaga la verificación;
- `CATALOGO_VERIFICATION_INTERVAL` impide el arranque.

| Variable | Propiedad | Por defecto | Para qué |
| --- | --- | --- | --- |
| `CATALOGO_CATEDRA_CONNECT_TIMEOUT` / `_READ_TIMEOUT` | `catalogo.catedra.connect-timeout` / `read-timeout` | `5s` / `10s` | Login técnico |
| `CATALOGO_CATEDRA_LOGIN_MAX_ATTEMPTS`, `_INITIAL_DELAY`, `_MULTIPLIER`, `_MAX_DELAY` | `catalogo.catedra.login.max-attempts`, `initial-delay`, `multiplier`, `max-delay` | `5`, `1s`, `2`, `30s` | Reintentos del login técnico |
| `CATALOGO_CATEDRA_LOGIN_MIN_INTERVAL` | `catalogo.catedra.login.min-interval` | `60s` | Mínimo entre dos logins |
| `CATALOGO_CATEDRA_INITIAL_LOGIN_MAX_WAIT` / `_POLL_INTERVAL` | `catalogo.catedra.initial-login.max-wait` / `poll-interval` | `2m` / `200ms` | Espera del login inicial (C03) |
| `CATALOGO_CATEDRA_SNAPSHOT_CONNECT_TIMEOUT` / `_READ_TIMEOUT` | `catalogo.catedra.snapshot.connect-timeout` / `read-timeout` | `5s` / `10s` | Pedido del snapshot |
| `CATALOGO_CATEDRA_SNAPSHOT_MAX_ATTEMPTS`, `_INITIAL_DELAY`, `_MULTIPLIER`, `_MAX_DELAY` | `catalogo.catedra.snapshot.max-attempts`, `initial-delay`, `multiplier`, `max-delay` | `5`, `1s`, `2`, `30s` | Reintentos del snapshot |
| `CATALOGO_CATEDRA_REDIS_CONNECT_TIMEOUT` / `_COMMAND_TIMEOUT` | `catalogo.catedra.redis.connect-timeout` / `command-timeout` | `5s` / `2s` | Conexión y cada comando de Redis |
| `CATALOGO_CATEDRA_REDIS_MAX_ATTEMPTS`, `_INITIAL_DELAY`, `_MULTIPLIER`, `_MAX_DELAY` | `catalogo.catedra.redis.max-attempts`, `initial-delay`, `multiplier`, `max-delay` | `3`, `1s`, `2`, `30s` | Reintentos de una lectura de Redis |
| `CATALOGO_VERIFICATION_ENABLED` | `catalogo.synchronization.verification.enabled` | `true` | Enciende el disparador de la verificación. Surefire la pone en `false` en todos los tests: una propiedad del sistema le gana al yaml |
| `CATALOGO_VERIFICATION_INTERVAL` | `catalogo.synchronization.verification.interval` | `5m` | Espera entre el fin de una verificación y el comienzo de la siguiente |
| `CATALOGO_VERIFICATION_PERSISTENCE_MAX_ATTEMPTS`, `_INITIAL_DELAY`, `_MULTIPLIER`, `_MAX_DELAY` | `catalogo.synchronization.verification.persistence-retry.max-attempts`, `initial-delay`, `multiplier`, `max-delay` | `3`, `1s`, `2`, `30s` | Reintentos cuando la base falla al aplicar una versión |

`enabled` e `interval` los lee el disparador directamente, no están en `VerificationProperties` (TASK-12 de C03). Su `@Scheduled` conserva `:5m` como valor por defecto por si falta el yaml.

## Tests

- La suite normal nunca lee el `.env` local: el `pom.xml` apunta `catalogo.env-file` a un archivo inexistente (propiedad `tests.env-file`).
- `TestcontainersConfiguration`: solo PostgreSQL 18. Lo importan los tests que levantan la aplicación. Kafka queda para los tests que lo usen.
- Surefire pone `catalogo.synchronization.verification.enabled=false` en todos los tests, también con `-Pe2e`, para que la verificación periódica no escriba en la base de cada test que levanta la aplicación. Los tests del disparador la encienden con `@SpringBootTest(properties = …)`.
- `CatedraRedis` (`src/test/java/catalogo/catedra/`): Redis 7.4 en Testcontainers, con dos usuarios. `grp_test` tiene un ACL como el de la cátedra: lectura sobre `catedra:sync:*`, `HELLO` permitido y `PING` prohibido. `grp_without_keys` no puede leer esas claves. Tiene además una conexión de administración (`admin()`) para cargar datos (`putMetadata`, `putChanges`, `putEntity`, `putRaw` y `putSnapshotEntities`, con las entidades del snapshot de ejemplo), y `pauseClients` (`CLIENT PAUSE`) para provocar un timeout. `CatedraRedisTest` comprueba que el ACL se comporta como el de la cátedra.
- `FakeCatedraServer.loginWithRedis(host, puerto, usuario, contraseña)`: un login cuya integración apunta al Redis de test. Para probar Redis caído, apuntarla a un puerto cerrado (`CatalogVerificationWithoutRedisIntegrationTest`).
- `ScriptedRedisClient` (junto a los tests de los casos de uso de `catedra`): cliente de Redis falso que responde en orden y guarda las integraciones y versiones recibidas. Los errores de Redis que no se pueden provocar a demanda (`LOADING`, `BUSY`) se prueban en la clasificación del adaptador (`CatedraRedisClientAdapter.classify`), con un test unitario.
- `FakeHttpServer` (`src/test/java/catalogo/`): servidor HTTP del JDK en un puerto libre, para simular sistemas externos. Respuestas en orden por ruta (la última se repite), demoras y registro de los pedidos.
- `FakeCatedraServer` (`src/test/java/catalogo/catedra/`): cátedra simulada sobre `FakeHttpServer`. Por defecto responde un login técnico exitoso y el snapshot de ejemplo; arma también respuestas con otro estado, sin un dato o con un rechazo, y copias del snapshot para modificar en un test (`snapshotTree`, `snapshotOf`). Las respuestas de ejemplo están en `src/test/resources/catedra/`: `login-200.json` y `snapshot-200.json` (recorte del snapshot real con datos ficticios y el mismo formato; el orden de los campos sigue el ejemplo del anexo; el real los trae en orden alfabético, sin efecto en la lectura), con valores ficticios.
- `CatalogSnapshots` (`src/test/java/catalogo/catedra/`): el snapshot de ejemplo ya leído, igual a `snapshot-200.json`. `ScriptedSnapshotClient` (junto a los tests de los casos de uso de `catedra`): cliente del snapshot falso que responde en orden y guarda los JWT recibidos.
- `CatalogSamples` y `CatalogTables` (`src/test/java/catalogo/catalog/`): modelos de la copia local con todos los datos completos, y lectura y limpieza de las tablas con SQL directo, para comprobar lo que llegó a la base sin pasar por JPA. Los tests de `catalog` no usan `@Transactional`: prueban transacciones reales. Para provocar una falla de la base a mitad de una escritura, el test crea un disparador de PostgreSQL y lo borra al terminar (`ReplaceCatalogUseCaseImplTest`).
- `TechnicalIntegrations` (`src/test/java/catalogo/catedra/`): integraciones de ejemplo, completas o sin un dato.
- `ScriptedLoginClient` (junto a los tests de los casos de uso de `catedra`): cliente de login falso que responde en orden y cuenta las llamadas. `MutableClock` (`src/test/java/catalogo/`): reloj que el test adelanta.
- Integración con la aplicación completa: clases `*IntegrationTest` con `@SpringBootTest` e `@Import(TestcontainersConfiguration.class)`, con la cátedra simulada configurada por `@DynamicPropertySource`. Un escenario por clase cuando cada uno necesita su propia configuración de la aplicación; varios en una clase si comparten la configuración y cada uno revisa su propio log (`FullSynchronizationIntegrationTest`).
- E2E: paquete `e2e` (`src/test/java/e2e/`), clases con `@Tag("e2e")` y `@EnabledIf("e2e.E2ECredentials#available")`: corren solo con `-Pe2e` y se saltean sin las tres credenciales de la cátedra en el `.env`. Usan una base descartable de Testcontainers y la cátedra real, y comprueban solo lo que hace este servicio. Para probar el perfil sin llamar a la cátedra: `./mvnw test -Pe2e -Dtests.env-file=<archivo inexistente>` (la propiedad de la línea de comandos le gana a la del perfil; comprobado con `help:evaluate`).
- La salida se captura por método (`OutputCaptureExtension`): un log que salió durante otro método de la misma clase no se ve. Si un test mira el log de algo que ocurre una sola vez, como el login del arranque, la clase tiene un solo método.
- Para contar los tests de una corrida, usar los reportes XML (`target/surefire-reports/TEST-*.xml`).
- Práctica acordada: después de que un test pasa, una prueba en negativo (romper el código a propósito, ver que el test falla, restaurar el archivo y comprobar el hash).
- No usar `pkill -f` ni `pgrep -f` con un texto que esté en el propio comando: se corta o se encuentra a sí mismo.

## Documentación del servicio

- `docs/`: copias de los casos de uso, los contratos, el dominio y las secuencias. La fuente de verdad está en el repositorio `documentacion`. `contratos_comunes.md` y el caso de uso X01 tienen que ser idénticos en los tres repositorios: compararlos al empezar cada SPEC.
- `docs/sdd/<id>-<nombre-corto>/`: SPEC, PLAN y TASKS de cada funcionalidad. Hechas: `x01-cuenta-tecnica`, `c01-sincronizacion-completa` y `c03-verificacion-periodica`.
- `docs/decisiones/NNN-titulo.md`: textos de las decisiones complejas. `001`: sesión técnica con la cátedra. `002`: la copia local del catálogo en un solo slice. `003`: qué guarda el estado de sincronización y cuándo cambia.
- El `README.md` lo escribe el usuario.
