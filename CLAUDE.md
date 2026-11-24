# catalogo_service

Servicio de catálogo y sincronización. Los comandos se ejecutan desde la raíz de este repositorio.

Las instrucciones generales del proyecto están en `documentacion/AGENTS.md`. El servicio de turnos (`turnos_service`, con su `CLAUDE.md`) ya está terminado y sirve de modelo; no se copia código entre repositorios.

## Requisitos

- Docker en ejecución: lo usan el compose y los tests.
- JDK 25.
- Un archivo `.env` creado a partir de `.env.example`. No se versiona. Lo leen docker compose y la aplicación. Los valores van sin comillas: la aplicación toma las comillas como parte del valor.
  - Obligatoria: `CATALOGO_DB_PASSWORD`. Sin ella el compose se niega a levantar y la aplicación no arranca (PostgreSQL rechaza la conexión).
  - Base: `CATALOGO_DB_NAME`, `CATALOGO_DB_USER` y `CATALOGO_DB_PORT` (por defecto `catalogo`, `catalogo` y `5433`).
  - Cuenta técnica de la cátedra: `CATALOGO_CATEDRA_BASE_URL`, `CATALOGO_CATEDRA_USERNAME` y `CATALOGO_CATEDRA_PASSWORD`. Es la misma cuenta que usa turnos. Si falta alguna, o la URL no es `http` o `https` válida, el servicio arranca con un warning y sin integración con la cátedra.
  - Clave pública del JWT de los usuarios: `CATALOGO_JWT_PUBLIC_KEY_LOCATION`, la ubicación del PEM que genera turnos, con `file:` (por ejemplo, `file:../turnos_service/secrets/jwt-public.pem`; el `.env.example` solo avisa, sin la ruta). Sin ella, o si no es una clave pública RSA de 2048 bits o más, el servicio arranca con un warning y toda ruta protegida responde `401` (decisión 007).
  - Opcional: `CATALOGO_PORT` (8081 por defecto; el 8080 lo usa turnos).
  - Opcional: `CATALOGO_CORS_ALLOWED_ORIGINS`, orígenes de navegador exactos separados por comas. Vacía, ningún navegador. Nunca `*` (decisión 007).
  - Opcionales: los tiempos, los reintentos, la verificación periódica y el consumo de Kafka (ver "Propiedades ajustables"). Kafka no lleva variables obligatorias: los servidores, el topic y el consumer group llegan con el login técnico.
  - Solo para el E2E de Kafka: `CATALOGO_E2E_KAFKA_CONSUMER_GROUP`, que tiene que terminar en `-e2e`. La aplicación no la lee; sin ella, ese E2E se saltea.
- La suite normal de tests nunca lee el `.env` ni llama a la cátedra real.

## Comandos

Todos se ejecutaron y su resultado se observó (TASKS de X01, C01, C03, C02, C04, C05, la validación del JWT y C06).

| Para | Comando |
| --- | --- |
| Levantar la base de desarrollo y esperar a que esté lista | `docker compose up -d --wait catalog-db` |
| Ver el estado de los contenedores | `docker compose ps` |
| Correr la aplicación (necesita la base levantada) | `./mvnw spring-boot:run` |
| Correr la aplicación sin la integración con la cátedra, para probarla sin llamar a la cátedra real. Las variables del comando le ganan al `.env`, y el log muestra `Technical login disabled` (TASK-11 de C04) | `CATALOGO_CATEDRA_BASE_URL= CATALOGO_CATEDRA_USERNAME= CATALOGO_CATEDRA_PASSWORD= CATALOGO_VERIFICATION_ENABLED=false CATALOGO_CATEDRA_KAFKA_ENABLED=false ./mvnw spring-boot:run` |
| Correr la aplicación sin la cátedra y con la clave pública de turnos, para probar las rutas protegidas con un token real de turnos (lo corrió el usuario en TASK-08 de la validación del JWT, con turnos también sin la cátedra) | `CATALOGO_CATEDRA_BASE_URL= CATALOGO_CATEDRA_USERNAME= CATALOGO_CATEDRA_PASSWORD= CATALOGO_VERIFICATION_ENABLED=false CATALOGO_CATEDRA_KAFKA_ENABLED=false CATALOGO_JWT_PUBLIC_KEY_LOCATION=file:../turnos_service/secrets/jwt-public.pem ./mvnw spring-boot:run` |
| Correr la aplicación con la cátedra real pero sin consumir Kafka, para no usar el consumer group del servicio (lo corre el usuario, después de ver la lista de pedidos; TASK-11 de C05) | `CATALOGO_CATEDRA_KAFKA_ENABLED=false ./mvnw spring-boot:run` |
| Correr todos los tests (Testcontainers crea su propio PostgreSQL; no usan el compose) | `./mvnw test` |
| Correr una o varias clases de test | `./mvnw test -Dtest='Clase1,Clase2'` |
| Compilar desde cero y correr los tests | `./mvnw clean test` |
| Tests más reporte de cobertura de JaCoCo (`target/site/jacoco/jacoco.xml`), lo mismo que corre GitHub Actions | `./mvnw clean verify` |
| Construir la imagen y levantar el servicio en contenedores, la base y la app (comprobado con un archivo de prueba y volúmenes descartables; ver "Docker") | `docker compose up -d --build` |
| Levantar el sistema completo, con turnos (necesita `../turnos_service`, con su `compose.yaml` y su `.env`; comprobado igual que la fila anterior) | `docker compose -f compose.full-system.yaml up -d --build` |
| Apagar el sistema completo conservando los datos | `docker compose -f compose.full-system.yaml down` |
| Apagar los contenedores conservando los datos | `docker compose down` |
| Solo los tests E2E, contra la cátedra real con las credenciales del `.env` (los corre el usuario, después de ver la lista de pedidos; sin credenciales se saltean) | `./mvnw test -Pe2e` |
| Un solo E2E, por ejemplo el de C02 | `./mvnw test -Pe2e -Dtest=IncrementalSynchronizationE2ETest` |

`docker compose down -v` borra el volumen de la base (`catalog_service_catalog-db-data`) y el de la clave pública de turnos (`turnos-jwt-public-key`): no usarlo para limpiar una prueba. Para una prueba descartable, otro proyecto de compose con otro volumen y otro puerto, y después `down` sin `-v` y `docker volume rm` del volumen de prueba.

## Datos del proyecto

- Spring Boot 4.1.2, Java 25, Maven con wrapper.
- `{basePackage}` de la skill `hexagonal-vertical-slicing`: `catalogo`. Clase principal: `CatalogoApplication`.
- Variables de entorno con prefijo `CATALOGO_` y propiedades propias bajo `catalogo.`.
- La aplicación escucha en el 8081 y la base se publica en el 5433 de la máquina; los dos puertos son configurables.
- Docker: `Dockerfile`, `compose.yaml` (la base `catalog-db`, PostgreSQL 18, y la app `catalog-service`) y `compose.full-system.yaml` (los dos servicios). Ver "Docker".
- Dependencias incluidas desde el inicio, aunque algunas todavía no se usan: web, JPA, Flyway, PostgreSQL, validation, Lombok, security, OAuth2 resource server, Kafka y Testcontainers. Desde C04 se usan web y security, desde C05 validation (el request de la búsqueda) y desde la validación del JWT el resource server (Spring Security 7.1.2, Nimbus JOSE 10.9.1).
- Kafka se consume con spring-kafka 4.1.2 (kafka-clients 4.2.2), por `spring-boot-starter-kafka`, con un contenedor armado por programa (C02). Las piezas que el starter crea por defecto no se conectan a nada: en el log de toda la suite no aparece `localhost:9092` (TASK-08 de C02).
- Redis se lee con `io.lettuce:lettuce-core` directo, con la versión que maneja Spring Boot (7.5.2). En C03 reemplazó a `spring-boot-starter-data-redis`, que armaba una conexión automática a `localhost:6379` que nadie usaba (P3.2 del PLAN de C03).
- SonarQube Cloud: organización `programacion2-ignaciomilutin-2026`, proyecto `Programacion2-IgnacioMilutin-2026_catalogo_service`. Lo dispara `.github/workflows/build.yml` en cada pull request y en cada push a `main` y `develop`. `CatalogoApplication` está excluida del cálculo de cobertura. El usuario revisa SonarQube por su cuenta: no va en los TASKS.
- La carpeta local se llama `catalogo_service`, como el repositorio remoto y como queda con `git clone`. Hasta el 2026-11-23 se llamaba `catalog_service`; se renombró en la etapa de Docker, por decisión del usuario, para que el sistema completo encuentre la carpeta en cualquier clon. Los documentos de SDD ya cerrados conservan el nombre viejo, y el volumen de la base también (a propósito: son los datos de desarrollo).

## Docker

- `Dockerfile` en dos etapas: compila con el `./mvnw` del repositorio dentro de `eclipse-temurin:25-jdk`, sin tests (necesitan Docker), y ejecuta solo el jar en `eclipse-temurin:25-jre`, con el usuario sin privilegios `catalogo` (uid 999). `.dockerignore` deja afuera `target/`, `.env`, `secrets/`, `*.pem`, `.git/`, `.idea/`, `.vscode/` y `docs/`. La imagen pesa unos 427 MB; el primer build tarda unos 2 minutos, y las dependencias de Maven quedan en caché.
- `compose.yaml`: `catalog-db` y `catalog-service`, que espera a que la base esté sana. La app recibe el `.env` con `env_file`, y el compose cambia lo que es distinto dentro de Docker: `CATALOGO_DB_HOST=catalog-db`, `CATALOGO_DB_PORT=5432` y `CATALOGO_PORT=8080`, los puertos de adentro de la red de Docker (los del `.env` siguen siendo los de la máquina: el 5433 de la base y el 8081 de la app, en los mapeos), y `CATALOGO_JWT_PUBLIC_KEY_LOCATION=file:/app/turnos-public-key/jwt-public.pem`. Turnos busca a catálogo en `http://catalog-service:8080`. Sin `user:`: la clave pública es `644` y la lee el usuario de la imagen (comprobado).
- **La clave pública de turnos llega por un volumen con nombre fijo, `turnos-jwt-public-key`,** que catálogo monta de solo lectura en `/app/turnos-public-key`. Lo llena el paso `turnos-public-key` del compose de turnos, que copia solo `jwt-public.pem` después de `turnos-keys`, así catálogo nunca ve `secrets/` de turnos. Sin turnos, el volumen se crea vacío: catálogo arranca con el warning de la clave y solo las rutas protegidas responden `401`. Si turnos genera un par nuevo, el volumen se actualiza en el próximo arranque de turnos, y catálogo lo lee al reiniciarse. Con `./mvnw`, catálogo sigue usando la ruta del `.env`.
- **Por qué un volumen y no montar el archivo de turnos** (comprobado el 2026-11-23 con escenarios descartables, Compose v5.7.0 y Docker en Linux):
  - con la sintaxis corta (`origen:destino`), si el origen no existe al arrancar, el daemon de Docker, que corre como root, lo crea como carpeta a nombre de root: en `turnos_service/secrets/` rompería la generación de claves de turnos, y arreglarlo exigiría `sudo`;
  - con la sintaxis larga y `create_host_path: false`, falla, y se revisa al crear los contenedores, antes de que corran sus dependencias: el sistema completo fallaba la primera vez, y catálogo solo no arrancaba sin la clave de turnos;
  - el `compose.yaml` de catálogo no puede depender de `turnos-keys`: un `depends_on` de un servicio no definido invalida el proyecto, también con `required: false`;
  - la clave en el `.env` se había descartado en D4 de la validación del JWT: hay que copiarla a mano con cada par nuevo, y en Docker no existe antes del primer arranque.
- `compose.full-system.yaml`: igual, letra por letra, al de turnos. `name: full-system`, `include` de `../turnos_service/compose.yaml` y `../catalogo_service/compose.yaml` (desde cada repositorio, uno de los dos se apunta a sí mismo por la carpeta de al lado), y la espera de `catalog-service` a `turnos-public-key`, que Compose suma a la que ya tiene con la base. Requiere los dos repositorios uno al lado del otro con los nombres que deja `git clone`, cada uno con su `.env`. Si se cambia, se cambia en los dos.
- El volumen de la base tiene nombre fijo, `catalog_service_catalog-db-data`, así que es la misma base sola, en el sistema completo o con `./mvnw`.
- **Para verificar sin la cátedra:** un archivo más con `-f`, fuera del repositorio, con las variables de la cátedra vacías (`CATALOGO_CATEDRA_BASE_URL`, `_USERNAME` y `_PASSWORD`, y `CATALOGO_VERIFICATION_ENABLED` y `CATALOGO_CATEDRA_KAFKA_ENABLED` en `false`; en turnos, también Kafka, el barrido y la reconciliación) y `name:` descartables para los volúmenes, con `-p probe-…`. Para esperar a la app, `curl --retry … --retry-all-errors`: con `--retry-connrefused` no espera, porque Docker acepta la conexión antes de que la app escuche. `docker compose config` muestra los valores del `.env`, también los secretos: usar `--quiet`, o `--format json | jq` de lo necesario. Al terminar, `down` sin `-v` y `docker volume rm` de los volúmenes de prueba.
- **Comprobado el 2026-11-23**, sin la cátedra y con volúmenes descartables:
  - catálogo solo: arranca en unos 5 s como `catalogo`, con el volumen vacío y el warning de la clave; C04 `503` (base vacía) y C06 `401`;
  - el sistema completo, desde los dos repositorios: `turnos-keys` no regenera las claves que ya existen, `turnos-public-key` publica la pública y catálogo la lee sin warning; con la copia vacía, C06 `503` y T03 `503` `CATALOG_UNAVAILABLE`; con datos de ejemplo cargados con SQL, C06 `200`, T03 `PROFESSIONAL_NOT_WORKING` y un id inexistente `404`; el log de catálogo tiene la línea con el `sub` de cada pedido de turnos, hecho dentro de la red de Docker, y el token no aparece en ningún log;
  - las claves de `turnos_service/secrets/` no cambiaron.
- **Sin comprobar:** una corrida con la cátedra real dentro de Docker (la sincronización, Redis y Kafka). Turnos anotó que la red del Kafka de la cátedra se superpone con la red por defecto de Docker.

## Arquitectura

Se aplica la skill `hexagonal-vertical-slicing` (`documentacion/.claude/skills/hexagonal-vertical-slicing/SKILL.md`). La estructura inicial del proyecto se hizo sin SDD: cada paquete lo crea el SDD del caso de uso que lo usa.

Slices actuales:

| Paquete | Qué contiene |
| --- | --- |
| `catalogo.catedra` | Pasarela hacia la cátedra, la única puerta. La sesión técnica (X01): login técnico al arrancar, estado en memoria y renovación a pedido. El pedido del snapshot (C01): sesión, renovación ante un `401`, reintentos y validación de la respuesta (`GetCatalogSnapshotUseCase`). C03: la lectura de Redis (`GetCatalogMetadataUseCase` y `GetCatalogVersionChangesUseCase`, con lo común en `CatalogRedisReader`) y la espera del login inicial (`AwaitInitialTechnicalSessionUseCase`). Las reglas de cada entidad están en `CatedraEntityMapper`, que comparten el snapshot y Redis. C02: el consumo del topic del catálogo (`infrastructure/kafka`: `KafkaCatalogConsumer`, `CatalogUpdateListener` y `CatalogUpdateReader`, el único lugar del servicio que conoce Kafka), el aviso de sesión habilitada que dan los dos casos de uso del login (`TechnicalIntegrationListener`), y la entrega del aviso ya validado (`CatalogUpdateNotice`) por `CatalogUpdateService` y `ReceiveCatalogUpdateUseCase` al puerto `CatalogUpdateHandler`, que implementa `synchronization` |
| `catalogo.catalog` | La copia local del catálogo: categorías, profesionales, horarios semanales y el estado de sincronización (`CatalogSyncState`, una sola fila). C01: reemplazar la copia con su versión en una transacción (`ReplaceCatalogUseCase`) y registrar un fracaso (`RecordSyncFailureUseCase`). C03: aplicar una versión incremental en una transacción, comprobando las referencias (`ApplyCatalogVersionUseCase`, que devuelve `ApplyCatalogVersionResult`), leer el estado (`GetCatalogSyncStateUseCase`) y corregir un fracaso cuando el catálogo está al día (`RecordUpToDateCheckUseCase`). C04, la primera entrada web del servicio (`GET /api/professional-categories`): `ProfessionalCategoryController`, `ProfessionalCategoryService`, `GetAllProfessionalCategoriesUseCase` (sin versión local lanza `CatalogNotInitializedException`, que extiende la base del slice, `CatalogException`), `ProfessionalCategoryResponse` con su `ProfessionalCategoryDtoMapper`, y `ProfessionalCategoryRepository.findAllOrderedByName()`. C05 (`GET /api/professionals`): `ProfessionalController`, `ProfessionalService`, `SearchProfessionalsUseCase` (lee todo en una sola foto y arma cada profesional con sus horarios), los tipos del dominio `ProfessionalSearchCriteria`, `ProfessionalWithSchedules` y `ProfessionalPage`, el request `ProfessionalSearchRequest` con sus validaciones propias `WholeNumber` y `TimeRange`, cuatro respuestas, `ProfessionalDtoMapper`, y la búsqueda en la persistencia (`ProfessionalRepository.countMatching` y `findMatching` con Specifications, y `WeeklyScheduleRepository.findAllByProfessionalIds`). C06 (`GET /api/professionals/{professionalId}`, protegido, lo llama turnos): el método `getProfessional` de `ProfessionalController` (el id como texto validado, el `sub` por `Principal` y una línea del log por consulta), `ProfessionalService.getProfessional` (convierte el vacío en `ProfessionalNotFoundException`), `GetProfessionalUseCase` (devuelve `Optional<ProfessionalWithSchedules>`, en la misma foto que C05), `ProfessionalRepository.findById`, el orden de los horarios que comparte con C05 (`WeeklySchedule.BY_DAY_START_AND_ID`) y `ProfessionalDtoMapper.toResponse(ProfessionalWithSchedules)`, ahora público. Ver "La consulta de un profesional" |
| `catalogo.synchronization` | El proceso de sincronización. C01: la sincronización completa (`SynchronizeFullCatalogUseCase`, `SynchronizationService`) y la regla de una sola sincronización a la vez (`SynchronizationLock`). C03: la verificación periódica (`VerifyCatalogVersionUseCase`, que devuelve `VerificationResult`) y su disparador (`infrastructure/scheduling/CatalogVerificationScheduler`). C02: la sincronización incremental por un aviso (`SynchronizeCatalogIncrementallyUseCase`, que devuelve `IncrementalSyncResult`), los avisos procesados (`ProcessedEvent`, con su persistencia propia, tabla `processed_events`), la espera del candado (`SynchronizationLock.acquire`) y el adaptador `infrastructure/gateway/CatalogUpdateHandlerAdapter`, que recibe los avisos de `catedra`. El procedimiento para ponerse al día con Redis lo comparten C03 y C02: `application/support/CatalogCatchUp`, que devuelve `CatchUpResult`. Más adelante, C07 |
| `catalogo.config` | Configuración transversal, sin lógica de negocio: `ClockConfig`, `SchedulingConfig` (`@EnableScheduling`) y, desde C04, `SecurityConfig` (las rutas públicas; desde la validación del JWT, también la validación del token, el lector que ignora las rutas públicas y CORS) y `ApiExceptionHandler` (el formato de los errores y los códigos generales del contrato común, decisión 005; desde C05, también el `400`; desde C06, el `400` de un parámetro validado en el método del controller, con su línea del log). Desde la validación del JWT (decisión 007): `JwtProperties` y `JwtDecoderConfig` (la clave pública de turnos y el decodificador), `SecurityProblemHandler` (el `401` y el `403` en `problem+json`) y `CorsProperties` |

Estructura decidida en C01 (decisión 002): la copia local va en un solo slice, `catalog`, porque las tres entidades se escriben siempre juntas por versión, se referencian entre sí y se leen juntas; el proceso va en `synchronization`. Dependencias en un solo sentido: `synchronization` usa `catedra` y `catalog`; `catalog` y `catedra` no usan a nadie. `synchronization` es una fachada (skill §9): su caso de uso recibe directamente los puertos de entrada de los otros dos y trabaja con sus modelos de dominio (P2 del PLAN de C01). Desde C02, además, tiene su propia persistencia (los avisos procesados, decisión de P3 del PLAN de C02), y recibe los avisos de Kafka por un puerto de salida de `catedra` que implementa su adaptador `infrastructure/gateway`: el aviso nace en `catedra` y va contra la flecha de las dependencias, así que `catedra` declara la interfaz y no sabe quién la cumple (decisión 004). La dependencia sigue siendo `synchronization` → `catedra`. No hay slice `turnos`: catálogo nunca llama a turnos; el endpoint que consume turnos (C06) va en `catalog`.

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

Autorizadas para C02 (P10 de su PLAN):

- Paquete `synchronization/infrastructure/gateway`, con un adaptador que implementa un puerto de salida de `catedra` (`CatalogUpdateHandlerAdapter`) e importa de `catedra` solo su dominio: el puerto y el modelo del aviso (decisión 004).
- Paquete `catedra/infrastructure/kafka` (y su `dto`), para todo lo de Kafka.
- Puertos de salida que no son repositorios: `TechnicalIntegrationListener` y `CatalogUpdateHandler`.
- `record` y resultados sellados: `CatalogUpdateNotice` (dominio de `catedra`), `CatchUpResult` e `IncrementalSyncResult` (dominio de `synchronization`) y `CatalogUpdateReading` (lo que devuelve el lector, en `catedra/infrastructure/kafka`). `ProcessedEvent` sigue la skill (clase Lombok).
- La clave de `processed_events` es el `eventId` de la cátedra (`String`), no un `Long` generado: `ProcessedEventEntity` sin `@GeneratedValue` y `JpaProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String>`.
- `CatalogCatchUp` en `synchronization/application/support`, dentro de la excepción de C01 para ese paquete.

No son excepciones: la persistencia propia de `synchronization` (molde de la skill), el segundo service de `catedra` (`CatalogUpdateService`, uno por concepto) y el `ObjectProvider` con que `KafkaCatalogConsumer` pide el oyente (una técnica de Spring).

No son excepciones: la fachada de `synchronization` (opción de la skill §9), `@Transactional` en los casos de uso de `catalog` (la skill no lo prohíbe en `application`) y las tres entidades en `catalog` (opción "unir slices", decisión 002). Los repositorios de `catalog` suman métodos fuera del CRUD (`saveAll` actualiza o inserta por `id`; `deleteAllExcept` quita lo que no está en una lista; `findAllByIds` lee por ids, desde C03).

Autorizadas para C04 (P7 de su PLAN):

- Un `@RestControllerAdvice` global, `ApiExceptionHandler` (decisión 005).
- La ruta `/api/professional-categories` en lugar del `/{entities}` de la skill, porque la fija el contrato §1.

No son excepciones: `SecurityConfig` y `ApiExceptionHandler` en `catalogo.config` (dentro de la excepción de X01), `CatalogException` (vuelve como la pide la skill), el mapper web sin `toDomain` (la skill, §8, deja afuera lo que no se usa) y la colación de `V3` (es de la base; la skill no habla de migraciones).

Autorizadas para C05 (P8 de su PLAN):

- La ruta `/api/professionals` en lugar del `/{entities}` de la skill, porque la fija el contrato §1 (como en C04).
- El request se recibe con `@Valid @ModelAttribute`, desde los parámetros de la URL: la skill solo describe `@Valid @RequestBody` donde hay un cuerpo.
- `ProfessionalDtoMapper` suma `toPage` y `toSize` además de `toDomain` y `toResponse`, porque el número y el tamaño de página van fuera de los filtros.
- Dos validaciones propias de Bean Validation en `web/dto`, `WholeNumber` y `TimeRange`, cada una con su validador en el mismo archivo. La skill no habla de validaciones propias (ajuste a P5 y P6 del PLAN de C05).

No son excepciones: los tres tipos del dominio de C05 (clases Lombok, como pide la skill); `JpaSpecificationExecutor` y el `collate` de Hibernate (infraestructura); `@Transactional` en el caso de uso (como desde C01); las respuestas anidadas (una clase por forma); el `503` en el controller (el molde de la skill); `V4` (es de la base); y el `400` en `ApiExceptionHandler` (dentro de la excepción de C04).

Validación del JWT (P8 de su PLAN): ninguna excepción nueva. No son excepciones: `JwtProperties`, `JwtDecoderConfig`, `SecurityProblemHandler`, `CorsProperties` y los cambios de `SecurityConfig`, en `catalogo.config` (dentro de la excepción de X01); los `record` de propiedades (como `CatedraProperties`); el lector del token (una técnica de Spring); y `TestSecurityConfiguration` (apoyo de tests).

Autorizada para C06 (P8.1 de su PLAN):

- La validación sobre un parámetro del controller (`@PathVariable @WholeNumber String professionalId`), y no en un request de `web/dto`; el controller convierte después el texto a `Long`. La skill solo describe la validación en un request.

Quedan dentro de excepciones ya autorizadas: la ruta `/api/professionals/{professionalId}` (la de C05, que fija el contrato), `WholeNumber` aplicable a un parámetro (la validación propia de C05) y el método nuevo de `ApiExceptionHandler` con su línea del log (el manejador global de C04). No son excepciones: `Principal` como parámetro del controller (no es una inyección), la línea del log en el controller, `findById` (el método estándar de la skill), `ProfessionalNotFoundException` (la jerarquía de la skill), el `Comparator` en `WeeklySchedule` (una regla del concepto), el `toResponse` público y `@Transactional` en el caso de uso.

Un resultado que otro slice necesita leer va como resultado sellado en el dominio del slice que lo produce, no como excepción: la fachada solo puede usar el dominio y los puertos de entrada de los otros slices (lo que se aprendió en C03). Cuándo un resultado sellado y cuándo una excepción, con la tabla de dónde se usa cada uno: decisión 006.

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
- Hay una sola sincronización a la vez, de cualquier tipo: `SynchronizationLock`, en memoria, para una sola instancia. Deja volver a entrar a quien ya lo tiene: C02 y C03 toman el candado para toda su pasada y piden la completa desde adentro sin que se descarte. C01 y C03 usan `tryAcquire` (no esperan); C02 usa `acquire`, que espera sin tope y se corta si el hilo se interrumpe (P6 del PLAN de C02).
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
- Todo lo anterior, desde la base vacía hasta el registro, lo hace `CatalogCatchUp` (desde C02), que comparte con C02. La verificación le pasa el nombre "Catalog verification", que encabeza cada línea del log del procedimiento, y traduce su `CatchUpResult` a `VerificationResult`.
- El disparador (`CatalogVerificationScheduler`) usa una espera fija: la primera verificación corre al arrancar, y cada una empieza recién cuando terminó la anterior, así que nunca se solapan.

### Cómo pedir la sincronización incremental (desde C02)

- Por `SynchronizeCatalogIncrementallyUseCase` (o `SynchronizationService.synchronizeCatalogIncrementally(eventId, newVersion)` desde la infraestructura). Recibe valores simples, sin tipos de `catedra`. Lo llama el adaptador del gateway con cada aviso válido.
- Recorrido: si el `eventId` ya está en `processed_events`, devuelve `AlreadyProcessed` sin esperar el candado ni leer Redis; si no, espera el candado (`acquire`), llama a `CatalogCatchUp` con el nombre "Catalog update notice {eventId}", y si terminó bien registra el aviso con el instante, en una escritura propia y un solo intento.
- Devuelve `AlreadyProcessed`, `UpToDate(versión)`, `Updated(versión)` o `Failed(motivo)`.
- Nunca deja salir un error: una falla de la base fuera de una versión (al consultar el aviso o al leer la versión local) queda en el log y devuelve `Failed`, sin escribir nada; si la base rechaza el registro del aviso, queda en el log y el resultado se conserva. Lo que no se pudo hacer lo recupera la verificación (D9 de C02).
- El aviso no trae datos: `newVersion` solo se valida y va al log; el destino es siempre la `currentVersion` de Redis (D7 de C02).

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

### Cómo usar Kafka de la cátedra (desde C02)

- **Nada de Kafka fuera de `catedra/infrastructure/kafka`**: ni tipos, ni nombres. El resto del código habla de "un aviso de versión nueva" (`CatalogUpdateNotice`) y de "la integración se habilitó".
- El consumo no se configura al arrancar: los dos casos de uso del login avisan por `TechnicalIntegrationListener` cuando guardan una sesión habilitada, y `KafkaCatalogConsumer` arma el contenedor de spring-kafka con esos datos, en un hilo aparte. Conserva los datos del primer login hasta reiniciar; un login posterior con otros datos solo deja un warning (D12 de C02). Si no pudo arrancar, un login posterior lo vuelve a intentar.
- **Pide el oyente con `ObjectProvider`**, recién al armar el contenedor. Recibirlo al crearse forma un ciclo entre objetos al arrancar (login → consumidor → oyente → C02 → Redis → renovación del login) y Spring no arranca: lo mostró una prueba en negativo (TASK-08 de C02).
- Cómo consume: grupo `{kafkaConsumerGroupId}-catalog` (D4), `latest` sin posición guardada (D10), un aviso por lectura, sin confirmación automática y con `AckMode.RECORD` (cada aviso se confirma cuando el oyente terminó con él), `max.poll.interval.ms` configurable (10 minutos) y como mucho 30 s entre reconexiones (D11). Fijos en el código salvo el intervalo.
- Un mensaje: `CatalogUpdateListener` → `CatalogUpdateReader` (DTO con los campos como `JsonNode`; las reglas de D6; lo inválido se descarta con un warning con partición, posición, `eventId` y motivo) → `CatalogUpdateService` → `ReceiveCatalogUpdateUseCase` → `CatalogUpdateHandler` → `CatalogUpdateHandlerAdapter` (`synchronization`) → C02.
- **El oyente nunca deja salir un error**: todo aviso se confirma, procesado o no, porque la verificación periódica recupera lo que no se pudo hacer. A diferencia de turnos, no hace falta frenar el topic.
- Lo que viene del mensaje va al log recortado a 100 caracteres y con `?` en lugar de cada carácter de control (`CatalogUpdateReader.forLog`). El `eventId` validado no tiene caracteres de control, así que se puede escribir en cualquier log.
- Al apagar, el contenedor deja terminar el aviso en curso: no interrumpe su hilo. Si quedó sin aplicar, lo cubre la verificación del arranque siguiente (P6 de C02, ajustado en la tanda 6).
- Las pruebas contra el Kafka real nunca usan el consumer group del servicio y las corre el usuario.

### Seguridad (desde C04; la validación del JWT, desde su funcionalidad)

Las decisiones, sus porqués y las limitaciones están en `docs/decisiones/007-seguridad-del-servicio.md`. Todo vive en `catalogo.config`, y ningún slice lo importa.

- `config/SecurityConfig`, una sola cadena:
  - sin sesión (`STATELESS`), así que ninguna respuesta lleva cookie;
  - una sola lista de rutas públicas, `PUBLIC_ENDPOINTS`: `GET /api/professional-categories` (C04) y `GET /api/professionals` (C05), las dos exactas. Todo lo demás exige un JWT válido, también lo que todavía no existe;
  - el resource server con el decodificador de `JwtDecoderConfig`, y un lector del token que no busca nada en `PUBLIC_ENDPOINTS` y delega en `DefaultBearerTokenResolver` en lo demás: así las rutas públicas ignoran `Authorization`, sea válido o no;
  - **sin roles**: lo que no es público exige estar autenticado, nada más. `auth` no se lee; los roles llegan con C07;
  - CORS (abajo) y CSRF como viene.
- **El token** (`config/JwtDecoderConfig`): solo `RS256` con la clave pública de turnos; `exp` obligatorio y sin tolerancia de reloj, con el `Clock` del servicio; `sub` solo dígitos del 0 al 9 dentro del rango de un `Long`; `typ` como lo trae Spring. Usa `JwtValidators.createDefaultWithValidators`, que agrega los validadores por defecto (el vencimiento de 60 s, el de `typ`) solo si la lista no trae uno de ese tipo. Sin estado. Los tokens reales de turnos traen `alg` y `kid`, sin `typ`, y se validan (TASK-08).
- **La clave** (`catalogo.jwt.public-key-location`): se lee al arrancar con `RsaKeyConverters.x509()`. Si falta, está vacía, no se puede leer, no es una pública RSA o tiene menos de 2048 bits, el servicio arranca con un warning (`The JWT public key …: every request to a protected route will be rejected`), que nunca muestra el contenido del archivo, y un decodificador que rechaza todo: las rutas protegidas responden `401`. En Docker, la clave llega por el volumen `turnos-jwt-public-key`, que turnos llena con solo la pública: catálogo nunca ve la carpeta `secrets/` de turnos (ver "Docker").
- **Los rechazos** (`config/SecurityProblemHandler`): `401` y `403` en `problem+json` con `title`, `status`, un `detail` fijo e `instance`, sin `code` y sin el motivo; el `401` con `WWW-Authenticate: Bearer`. Escribe un `ProblemDetail` con `JacksonJsonHttpMessageConverter`, el mismo conversor de Spring MVC, así sale sin `type`, como los demás errores. **Tiene que estar en los tres lugares:** el punto de entrada general (sin token), el del resource server (token inválido) y el de acceso denegado (el `403` de CSRF). Si falta en el del resource server, Spring responde un `401` sin cuerpo y con el motivo en el encabezado (prueba en negativo de TASK-02). Un rechazo no escribe nada en el log.
- **CSRF** queda como viene: no se aplica a `GET`. Un `POST`, `PUT` o `DELETE` sin token responde `403` (con el formato de arriba); con un token inválido, `401`; con uno válido, `405`. El resource server exime de CSRF a los pedidos con `Bearer` (comprobado).
- **CORS** (`catalogo.cors.allowed-origins`): orígenes exactos, vacía por defecto, nunca `*`. A un origen permitido, solo `GET` con `Authorization`, sin cookies; su consulta previa se responde sin token y el `401` lleva `Access-Control-Allow-Origin`. La configuración se registra siempre: un origen no permitido recibe el `403` estándar de Spring, también en C04 y C05. Sin `Origin` (la app, turnos, Postman), CORS no interviene.
- **Para hacer pública una ruta:** sumarla a `PUBLIC_ENDPOINTS` con su método y la ruta exacta, sin `/**` (con `/**`, `GET /api/professionals/{professionalId}` quedaba abierta: prueba en negativo de TASK-06 de C05), y quitar su caso de `SecurityIntegrationTest.anyOtherRouteWithoutATokenIsRejectedWithoutData`, que hoy revisa `GET /api/professionals/15` y `GET /api/synchronization/status` (`401`) y `POST /api/professionals` y `POST /api/professional-categories` (`403`). Desde C06, ese test inserta el profesional 15 y revisa que el rechazo no traiga su nombre. **C06 no se puede hacer pública tal como está:** su controller necesita el usuario para la línea del log, y en una ruta pública `Principal` llega vacío y responde `500` (prueba en negativo de TASK-08 de C06).
- **Para un endpoint protegido nuevo (C07):** no hace falta tocar la seguridad para que exija el token. Su `@WebMvcTest` importa `TestSecurityConfiguration`. En `ProtectedRoutesSecurityTest`, `aValidTokenReachesTheRouteWhateverItsRoles` espera hoy `404` con un token válido en `/api/synchronization/status`: cambia a propósito con el endpoint, como cambiaron en C06 los casos de `/api/professionals/15` (pasaron a `aValidTokenGetsTheProfessionalWhateverItsRoles`, con el service simulado). `CorsSecurityTest` no usa un token válido en las rutas protegidas (solo consultas previas y pedidos sin token), así que no cambia: lo confirmó C06. Para saber quién pide, el controller recibe `java.security.Principal`, cuyo `getName()` es el `sub`, sin importar Spring Security (C06).
- **Una URL con caracteres que el servicio no acepta** (un salto de línea, un nulo, `%`, `;` o un punto codificados) la rechaza la seguridad de Spring antes de cualquier ruta, también las públicas. Con `@WebMvcTest` responde `400` sin cuerpo; **con el servidor real termina en `401`**, con `instance` `/error`, con token o sin él: la seguridad pide un error `400`, el servidor lo reenvía a `/error` y ese reenvío no lleva la autenticación. Una barra codificada (`%2F`) la rechaza antes Tomcat, con su `400` en HTML; los demás caracteres no se probaron uno por uno con el servidor real. Limitación aceptada (S3 de C06, decisión 007).
- Antes de C04, sin configuración, Spring Boot respondía `401` con `WWW-Authenticate: Basic` y una cookie `JSESSIONID`, sin un usuario generado: el resource server en el classpath lo impide (TASK-04 de C04).

### Cómo responde un error (desde C04)

- El controller atrapa las excepciones de su slice y lanza `ResponseStatusException` con el estado, y el código en `getBody().setProperty("code", …)`. Por ejemplo, `ProfessionalCategoryController` convierte `CatalogNotInitializedException` en `503` `CATALOG_NOT_INITIALIZED`.
- `config/ApiExceptionHandler` (decisión 005) hereda de `ResponseEntityExceptionHandler`, que da `problem+json` a esas excepciones y a los errores de Spring. Además atrapa cualquier otro error: escribe `Unexpected error` en el log con la traza y responde `500` `INTERNAL_ERROR` con un texto fijo. No importa nada de los slices.
- Los códigos generales del contrato común (`INTERNAL_ERROR` y `VALIDATION_ERROR`) los pone el manejador; los de cada slice, su controller.
- **El `400` `VALIDATION_ERROR` (desde C05):**
  - Los parámetros llegan en un request con Bean Validation (`@Valid @ModelAttribute`), con todos los campos como texto, para que las reglas las pongan sus validaciones y no la conversión de Spring.
  - Si alguno no cumple, Spring lanza `MethodArgumentNotValidException` antes de entrar al controller, y el manejador responde `400` con `fieldErrors`: uno por parámetro, con el mensaje fijo de la regla. Los errores de los elementos de una lista se juntan, sin el índice (`dayOfWeek[1]` → `dayOfWeek`), y un valor que Spring no pudo convertir lleva un texto neutro.
  - Para lo que las validaciones estándar no expresan, hay dos propias en `catalog/infrastructure/web/dto`: `WholeNumber` (un entero dentro de un rango, escrito como texto) y `TimeRange` (`from` antes que `to`, informado en `from` y revisado solo si las dos horas cumplen el formato).
- **Un parámetro validado en el método del controller** (desde C06, como el id de la ruta: `@PathVariable @WholeNumber String`): Spring lo rechaza antes de entrar con `HandlerMethodValidationException`, y `ApiExceptionHandler.handleHandlerMethodValidationException` responde el mismo `400` `VALIDATION_ERROR`, con el nombre del parámetro en `fieldErrors`, y escribe una línea `INFO` con el método, la plantilla de la ruta (`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`), el `sub` del pedido y los nombres de los campos, nunca sus valores. **El controller no lleva `@Validated`:** con esa anotación Spring valida por otro mecanismo, que lanza `ConstraintViolationException` y terminaría en `500` (deducido, sin ejecutar). Sin la sobrescritura del manejador, el `400` sale con `"detail": "Validation failure"`, sin `code` (prueba en negativo de TASK-04 de C06).
- **El `instance` de un error es la ruta del pedido tal como llegó, codificada**, también con un id inválido (`/api/professionals/abc`, `/api/professionals/%0115`): el `detail` y `fieldErrors` nunca traen el valor recibido (S2 de C06).
- **Cómo lee Spring Boot 4.1.2 los parámetros de un `@ModelAttribute`** (comprobado con un test temporal, PLAN de C05): una lista toma `MONDAY,FRIDAY` como dos valores; un enum distingue mayúsculas; un `Boolean` acepta `yes`, `TRUE` y `1`; un `LocalTime` acepta `09:00:00.5`; un valor vacío llega como `null`, salvo en un `String`, donde llega `""`. Los mensajes de error de conversión muestran tipos de Java. Por eso el request de C05 es todo texto.
- **Ningún error puede llegar a la página interna `/error`:** la seguridad también revisa ese reenvío, y con lo no público cerrado la app recibiría un rechazo en lugar de `500`. Se comprobó con el servidor real, quitando la captura del manejador (TASK-07 y TASK-09 de C04), cuando el rechazo era un `403`; desde la validación del JWT es el `401` de un pedido sin token, con `instance` `/error` (comprobado en C06 con las URL que rechaza la seguridad, que sí pasan por `/error`: ver "Seguridad"). Los rechazos de la seguridad no pasan por `/error`: `SecurityProblemHandler` escribe la respuesta él mismo (TASK-05 de la validación del JWT). `@WebMvcTest` no hace ese reenvío: los estados de error se prueban también con la aplicación levantada.
- Cuándo un resultado sellado y cuándo una excepción: decisión 006.

### El orden de las categorías (desde C04)

- La migración `V3` crea la colación `spanish_insensitive` (ICU, `es-u-ks-level1`, no determinística) y se la asigna a `professional_categories.name`. Un `ORDER BY name` ordena en español: sin mayúsculas ni tildes, y con la ñ después de la n; el `id` desempata (D2 y P3 de C04). `findAllOrderedByName()` usa `findAllByOrderByNameAscIdAsc()`.
- **Efecto al comparar:** la columna considera iguales "optica" y "Óptica". Hoy nadie compara categorías por nombre; una consulta nueva que lo haga tiene que tenerlo en cuenta.
- Necesita ICU en PostgreSQL. La imagen `postgres:18` lo trae.
- `V3` ya está aplicada en la base de desarrollo (2026-11-20): no se modifica, ni siquiera un comentario, porque Flyway compara la suma de control de cada migración aplicada al arrancar.

### La búsqueda de profesionales (desde C05)

- **El orden.** `V4` asigna la colación de `V3` a `professionals.first_name` y `last_name`, así que `ORDER BY last_name, first_name, id` ordena en español, igual que las categorías (D6 de C05).
- **La comparación por nombre.** `V4` crea también `search_insensitive` (ICU, `und-u-ks-level1`, no determinística). No distingue mayúsculas ni tildes, y la ñ es igual a la n.
  - La consulta la escribe sobre cada columna con el `collate` de Hibernate (`HibernateCriteriaBuilder.collate`) y `LIKE … ESCAPE '\'`.
  - `%`, `_` y `\` del texto se escapan.
  - `LIKE` funciona con colaciones no determinísticas en PostgreSQL 18; `ILIKE` no (comprobado).
- **Efecto al comparar:** con la colación de `V4`, el nombre y el apellido toman "Gomez" igual a "Gómez". Nadie compara profesionales por nombre.
- **`V4` ya está aplicada en la base de desarrollo** (2026-11-21, prueba manual de C05): no se modifica.
- **La consulta.** Son Specifications de Spring Data, armadas en `JpaProfessionalRepositoryAdapter`, con una condición por cada filtro recibido. La disponibilidad es una subconsulta: "existe un horario habilitado de este profesional, que termina después de empezar, en alguno de los días, que termina después de `from` y empieza antes de `to`". Las partes que no llegaron no se agregan, y tocarse en el borde no cuenta.
- **El caso de uso** (`SearchProfessionalsUseCaseImpl`):
  - cuenta primero, y pide la página solo si empieza antes del total, así una página enorme no llega a la base;
  - lee el estado, el total, la página y los horarios en una transacción de solo lectura con `REPEATABLE READ`, una sola foto de la base, aunque una sincronización confirme en el medio (lo prueba `ProfessionalsConsistencyIntegrationTest`);
  - ordena los horarios en Java por día, inicio e `id`, porque la base guarda el día como texto.
- **Sin medir:** cuando la página viene llena, `findAll(spec, pageable)` de Spring Data hace su propio conteo, que se suma al del caso de uso. Los dos dan lo mismo, porque ven la misma foto, y con pocos profesionales el costo no importa.

### La consulta de un profesional (desde C06)

- **El molde "Get by id" de la skill:** `GetProfessionalUseCase` devuelve `Optional<ProfessionalWithSchedules>` (sin versión local lanza `CatalogNotInitializedException`; si no existe, vacío), `ProfessionalService` convierte el vacío en `ProfessionalNotFoundException`, y el controller traduce las dos a `404` `PROFESSIONAL_NOT_FOUND` y `503` `CATALOG_NOT_INITIALIZED` (el `503` lo arma `catalogNotInitialized()`, que comparte con C05).
- **La misma foto que C05:** el estado, el profesional (su categoría viene con él, `@ManyToOne` sin `fetch` es `EAGER`) y sus horarios, en una transacción de solo lectura con `REPEATABLE READ`. Lo prueba `ProfessionalsConsistencyIntegrationTest`; sin el aislamiento, la respuesta mezcló el apellido viejo con los horarios nuevos.
- **El orden de los horarios** (día, inicio e `id`) es `WeeklySchedule.BY_DAY_START_AND_ID`, que usan C05 y C06. El objeto de C06 es idéntico al elemento de C05 para el mismo profesional.
- **El id:** un entero escrito solo con dígitos, con un signo menos opcional, dentro del rango de un `Long` (la regla de `categoryId`); `0` y `-1` dan `404`, `015` es el 15. Se recibe como texto y no como `Long` porque la conversión de Spring lee `" 15"`, `"1 5"`, `"15 "`, `"+15"`, `0xF`, `#F` y `"٤٢"` como 15 o 42, y no con un `@ModelAttribute` porque un parámetro de consulta con el mismo nombre le gana a la ruta (`/15?professionalId=99` leía el 99). Comprobado con una prueba descartable (PLAN de C06).
- **La línea del log** (D4 de C06), en inglés y en `INFO`, una por pedido que pasó la seguridad:
  - la escribe el controller en un `finally`, con el resultado que quedó: `Professional 15 requested by user 42: found with 2 weekly schedules`, `not found`, `catalog not initialized` o `failed` (en una falla de la base sale justo antes del `Unexpected error` del manejador, en el mismo hilo);
  - para un id inválido, que no llega al controller, la escribe `ApiExceptionHandler`: `Invalid parameters for GET /api/professionals/{professionalId} requested by user 42: professionalId`;
  - nunca el token, ni el valor de un id inválido (podría traer caracteres de control: `%0115` llega a la validación como `\u0001` seguido de 15). Los rechazos de la seguridad no dejan línea.
- **Sin un límite propio de espera para la base** (D6.3): con la base colgada, turnos corta a los 3 s y reintenta, y cada intento deja un hilo de catálogo esperando hasta que vence el pool (30 s). Limitación aceptada.

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
- El topic del catálogo (lectura del usuario sin consumer group, 2026-11-19):
  - Kafka `PLAINTEXT` sin credenciales (como lo vio turnos). Una partición, retención de 7 días con borrado.
  - 201 avisos retenidos, del 2026-11-11 al 2026-11-19: uno por hora exacta, todos `CatalogUpdated`, `SCHEDULED`, `schemaVersion` 1, con los seis campos del anexo y sin headers; la clave es igual a `newVersion`; versiones consecutivas, sin repetidos.
  - El `eventId` es un UUID de 36 caracteres (no como el ejemplo del anexo) y `occurredAt` trae nanosegundos.
  - El consumer group sugerido no existía: nadie consumía con él.
- C02 contra la cátedra real (E2E, 2026-11-20), con un consumer group de pruebas:
  - Snapshot 382; versión local bajada a 379; el último aviso retenido aplicó las versiones 380 a 382 de forma incremental en 1,8 s, con tipo `INCREMENTAL`.
  - El mismo aviso entregado otra vez: `AlreadyProcessed` en 4 ms, sin leer Redis.
  - El grupo del servicio no se tocó.
- C05 con la cátedra real (prueba manual del usuario, 2026-11-21, con Kafka apagado):
  - La base de desarrollo, sin versión local, cargó el snapshot 422 (8 categorías, 24 profesionales y 44 horarios), y `V4` se aplicó sobre ella.
  - La búsqueda respondió como en los tests: el orden por apellido, los filtros, la página más allá de la última, el `400`, el `Authorization` ignorado y C06 cerrado.
  - El borde de la franja se vio con datos reales: un horario de 11 a 14 no cumple con `from` 14:00.
  - **Ninguno de los 24 profesionales ni de las 8 categorías tiene tildes ni ñ.** La búsqueda sin tildes y el lugar de la ñ en el orden solo los prueban los tests, con datos ficticios.
- Las pruebas contra la cátedra real las corre el usuario en su terminal, después de ver la lista de pedidos; la salida se revisa sin mostrar secretos y los archivos temporales se borran.
- **Con turnos real** (TASK-08 de la validación del JWT, 2026-11-22; los dos servicios en local, sin la cátedra, corrido por el usuario): un token emitido por T02 pasa la seguridad de catálogo sin cambios en turnos (`404` en `/api/professionals/15`, que todavía no tiene endpoint); sin token y con el token alterado, `401`; C04 y C05 responden lo mismo con y sin el token; el token no aparece en el log. El token de turnos trae en el encabezado `alg` (`RS256`) y `kid`, sin `typ`, y los claims `auth`, `exp`, `iat` y `sub`, con 24 h de vigencia. Es la primera comunicación real entre los dos servicios; el resto de C06 se prueba con su endpoint.
- **C06 con turnos real** (TASK-09 de C06, 2026-11-22; los dos servicios en local, sin la cátedra, corrido por el usuario con un script que guardaba el token solo en memoria):
  - con un token real de turnos, C06 respondió `200` idéntico al elemento de C05, `404` `PROFESSIONAL_NOT_FOUND` con un id inexistente, `400` `VALIDATION_ERROR` con `abc`, y `401` sin token;
  - el cliente de C06 de turnos leyó las respuestas sin cambios: T03 en un día sin atención, `200` con `slots: []` y `PROFESSIONAL_NOT_WORKING`; T04 fuera de horario, `400` `INVALID_SLOT`; T03 de un inexistente, `404`; T03 de un deshabilitado, `409` `PROFESSIONAL_DISABLED`;
  - el log de catálogo tuvo una línea con el `sub` por cada pedido con token (7 de 7) y el token 0 veces.
  - Para variables y comandos: los de turnos son `TURNOS_CATEDRA_BASE_URL`, `_USERNAME` y `_PASSWORD`, `TURNOS_CATEDRA_KAFKA_ENABLED`, `TURNOS_RESERVATION_SWEEP_ENABLED`, `TURNOS_RESERVATION_RECONCILIATION_ENABLED` y `TURNOS_CATALOG_BASE_URL=http://localhost:8081`; catálogo, con el comando de la fila "con la clave pública de turnos" de "Comandos". Quedó el usuario de prueba `catalogo.c06.1795389142` en la base de desarrollo de turnos.

## Propiedades ajustables

Todas tienen un valor por defecto y ninguna hace falta en el `.env`. Cada una se ajusta con una variable `CATALOGO_…` (en el `.env` o en el entorno), que `application.yaml` pasa a la propiedad con un placeholder `${VARIABLE:valor por defecto}`. Como en turnos, el `.env.example` las lista comentadas y con su valor por defecto (TASK-16 de C03).

El valor por defecto está escrito dos veces, en el yaml y en el `@DefaultValue` del código: si se cambia uno, hay que cambiar el otro. Los mismos números aparecen en más de una sección a propósito (P10 del PLAN de C03): cada una se ajusta por separado.

Con una variable presente pero vacía (comprobado en TASK-16 de C03):

- una duración toma el valor por defecto;
- un número (intentos o multiplicador) no se puede leer, y la aplicación no arranca;
- `CATALOGO_VERIFICATION_ENABLED` apaga la verificación;
- `CATALOGO_VERIFICATION_INTERVAL` impide el arranque.
- `CATALOGO_CATEDRA_KAFKA_ENABLED` deja el consumo encendido, y `CATALOGO_CATEDRA_KAFKA_MAX_POLL_INTERVAL` toma su valor por defecto (C02: lo resuelve el constructor de `CatedraKafkaProperties`, probado en `KafkaCatalogConsumerTest` sobre el `record`, no leyendo el yaml).

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
| `CATALOGO_CATEDRA_KAFKA_ENABLED` | `catalogo.catedra.kafka.enabled` | `true` | Enciende el consumo del topic del catálogo (C02). Vacía, queda encendido. Surefire la pone en `false` en todos los tests |
| `CATALOGO_CATEDRA_KAFKA_MAX_POLL_INTERVAL` | `catalogo.catedra.kafka.max-poll-interval` | `10m` | Cuánto espera Kafka entre dos lecturas antes de sacar al consumidor del grupo; cubre el peor caso de un aviso (P8 del PLAN de C02) |
| `CATALOGO_VERIFICATION_ENABLED` | `catalogo.synchronization.verification.enabled` | `true` | Enciende el disparador de la verificación. Surefire la pone en `false` en todos los tests: una propiedad del sistema le gana al yaml |
| `CATALOGO_VERIFICATION_INTERVAL` | `catalogo.synchronization.verification.interval` | `5m` | Espera entre el fin de una verificación y el comienzo de la siguiente |
| `CATALOGO_JWT_PUBLIC_KEY_LOCATION` | `catalogo.jwt.public-key-location` | vacía | La clave pública de turnos (PEM, con `file:`). Vacía o inválida: warning al arrancar y `401` en toda ruta protegida. En el `.env.example` va sin comentar y vacía, con un aviso |
| `CATALOGO_CORS_ALLOWED_ORIGINS` | `catalogo.cors.allowed-origins` | vacía | Orígenes de navegador permitidos, exactos y separados por comas; vacía, ninguno. Nunca `*` |
| `CATALOGO_VERIFICATION_PERSISTENCE_MAX_ATTEMPTS`, `_INITIAL_DELAY`, `_MULTIPLIER`, `_MAX_DELAY` | `catalogo.synchronization.verification.persistence-retry.max-attempts`, `initial-delay`, `multiplier`, `max-delay` | `3`, `1s`, `2`, `30s` | Reintentos cuando la base falla al aplicar una versión |

`enabled` e `interval` los lee el disparador directamente, no están en `VerificationProperties` (TASK-12 de C03). Su `@Scheduled` conserva `:5m` como valor por defecto por si falta el yaml.

## Tests

- La suite normal nunca lee el `.env` local: el `pom.xml` apunta `catalogo.env-file` a un archivo inexistente (propiedad `tests.env-file`).
- `TestcontainersConfiguration`: PostgreSQL 18 y, desde la validación del JWT, la clave pública de `TestTokens` (un `DynamicPropertyRegistrar`), así la aplicación de los tests valida los tokens de prueba como los de turnos. Lo importan los tests que levantan la aplicación. Kafka queda para los tests que lo usen (`CatedraKafka`). **Lo que registra le gana a las propiedades del test:** un test que necesita la aplicación sin clave no lo importa y usa su propia base (`SecurityWithoutKeyIntegrationTest`).
- Surefire pone `catalogo.synchronization.verification.enabled=false` en todos los tests, también con `-Pe2e`, para que la verificación periódica no escriba en la base de cada test que levanta la aplicación. Los tests del disparador la encienden con `@SpringBootTest(properties = …)`.
- Surefire pone también `catalogo.catedra.kafka.enabled=false` en todos los tests, también con `-Pe2e`: la cátedra simulada informa servidores de Kafka inventados. Los tests de Kafka lo encienden con `@DynamicPropertySource` o `@SpringBootTest(properties = …)`.
- `CatedraKafka` (`src/test/java/catalogo/catedra/`): Kafka `apache/kafka:4.2.1` en Testcontainers, compartido por la suite; `pause` y `unpause`. `CatalogTopic`: un topic del catálogo propio de cada test, con el consumer group sugerido y `serviceConsumerGroup()` (el sugerido más `-catalog`); publica avisos con el formato real y dice qué posición confirmó un grupo y si un grupo tiene asignada la partición. Un grupo nuevo lee con `latest`, así que un test espera `isAssignedTo` antes de publicar. `FakeCatedraServer.loginWithRedisAndKafka`: un login cuya integración apunta al Redis y a un topic de test.
- Los tests que encienden el consumo llevan `@DirtiesContext`: si su contexto quedara en el caché, su consumidor seguiría leyendo durante el resto de la suite.
- **Un Kafka pausado no provoca warnings de reconexión:** el contenedor congelado acepta la conexión y no responde, y el cliente espera. Para probar los warnings de reconexión hace falta un puerto que rechace la conexión (`KafkaCatalogConsumerTest`). Tampoco se puede publicar con Kafka pausado.
- Para retomar desde la posición confirmada sin reiniciar la aplicación, un test detiene el consumidor del servicio y arranca un `KafkaCatalogConsumer` nuevo con el mismo grupo (`CatalogConsumptionWithKafkaDownIntegrationTest`, y el E2E de C02). Kafka solo deja mover la posición de un grupo sin miembros.
- Para que una sincronización dure en un test, la cátedra simulada demora el snapshot (`Response.after`), como en `CatalogNoticeConcurrencyIntegrationTest`.
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
- Integración por HTTP (desde C04): `@SpringBootTest(webEnvironment = RANDOM_PORT)` con `@AutoConfigureRestTestClient` y `RestTestClient`, que vienen con `spring-boot-starter-webmvc-test` (`ProfessionalCategoriesIntegrationTest`). Si un test cuenta los pedidos a la cátedra simulada, espera antes el login del arranque, que corre en otro hilo.
- Tests web: `@WebMvcTest(Controller.class)` con `@Import({TestSecurityConfiguration.class, …DtoMapper.class})` y el service con `@MockitoBean` (`ProfessionalCategoryControllerTest`). `@WebMvcTest` incluye los `@ControllerAdvice`, como `ApiExceptionHandler`, pero no las `@Configuration`: por eso se importa la seguridad. No hace el reenvío a `/error` de un servidor real.
- `TestSecurityConfiguration` (`src/test/java/catalogo/config/`): la seguridad real (`SecurityConfig`, `JwtDecoderConfig`, `SecurityProblemHandler`, `ClockConfig`, y `JwtProperties` y `CorsProperties` habilitadas) con la clave de `TestTokens`, para los `@WebMvcTest`.
- Tests de la validación del JWT, en `src/test/java/catalogo/config/`:
  - `JwtDecoderConfigTest`: unitario, sin Spring. Las variantes del token y de la clave, y los bordes del vencimiento con un reloj fijo (nunca con márgenes de tiempo reales).
  - `ProtectedRoutesSecurityTest` y `CorsSecurityTest`: `@WebMvcTest` contra las rutas reales. El de CORS tiene dos grupos `@Nested` con propiedades distintas.
  - `SecurityIntegrationTest`: con el servidor real; tiene el test de las otras rutas (`anyOtherRouteWithoutATokenIsRejectedWithoutData`). En el log solo busca los tokens con forma de JWT: un texto corto como `xxx` podría coincidir con cualquier otra línea.
  - `SecurityWithoutKeyIntegrationTest`: la aplicación sin clave, con su propia base y un solo método (mira el log del arranque).
- `CatalogTables.insertCategory`, `insertProfessional`, `insertWeeklySchedule` (desde C05) e `initializeAt(versión)`: preparan la copia con SQL directo.
- Tests de C05:
  - `ProfessionalSearchRepositoryAdaptersTest`: la búsqueda contra PostgreSQL con `V4`. Usa la misma configuración que `CatalogRepositoryAdaptersTest` (`@SpringBootTest` y `TestcontainersConfiguration`, sin propiedades), así que comparten el contexto.
  - `SearchProfessionalsUseCaseImplTest`: el caso de uso con los puertos simulados con Mockito (`@Mock`).
  - `ProfessionalDtoMapperTest` y `ProfessionalControllerTest`.
  - `ProfessionalsIntegrationTest`: de punta a punta, con la cátedra simulada como testigo.
  - `ProfessionalsConsistencyIntegrationTest`: la foto única. `@MockitoSpyBean` sobre `ProfessionalRepository` hace que, antes de traer la página, el test confirme un cambio desde una conexión propia del `DataSource`. Es el mismo hilo, así que no depende del tiempo. Su prueba en negativo, sin `REPEATABLE READ`, mostró la mezcla: el total en 2 y la página con 3.
- Tests de C06:
  - `GetProfessionalUseCaseImplTest`: el caso de uso con los puertos simulados con Mockito.
  - `CatalogRepositoryAdaptersTest`: también `findById`, contra PostgreSQL.
  - `ProfessionalDtoMapperTest`: el `toResponse` público da lo mismo que el elemento de una página.
  - `ProfessionalControllerTest`: los casos web de C06 (los ids inválidos, los que llegan como número, el `404`, el `503`) y las líneas del log. Los ids con caracteres especiales se piden con `get(URI.create(...))`, para que `MockMvc` no vuelva a codificar el `%`.
  - `ProtectedRoutesSecurityTest`: `aValidTokenGetsTheProfessionalWhateverItsRoles` y `aNonNumericProfessionalIdWithAValidTokenReachesItsValidation`, que reemplazaron a los que esperaban `404` en la ruta de C06.
  - `ProfessionalsIntegrationTest`: los escenarios de C06 con el servidor real, en la misma clase que C05 porque comparten la configuración (P6.1 del PLAN de C06), con sus cuatro profesionales de prueba y la ayuda que renombra tablas, que recibe el pedido. Compara C06 con el elemento de C05 como árbol JSON (`JsonMapper`). Para mandar una URL con un `%` codificado usa una URI absoluta con `@LocalServerPort` (`org.springframework.boot.test.web.server.LocalServerPort`), porque `uri(String)` lo vuelve a codificar.
  - `ProfessionalsConsistencyIntegrationTest`: también la foto única de C06, con el mismo espía, que confirma el cambio después de `findById`.
  - `SecurityIntegrationTest`: el test de las otras rutas inserta el profesional 15.
- **`@WebMvcTest` no reenvía a `/error`**: lo que con el servidor real pasa por ahí (el rechazo de una URL por sus caracteres) responde distinto en los dos (`400` sin cuerpo con `MockMvc`, `401` con el servidor). Esos casos se prueban también con el servidor real (S3 de C06).
- En un `500`, no usar como palabra prohibida algo que aparece en la ruta: el cuerpo trae `instance` con la ruta del pedido (por ejemplo `/api/professionals`). Pasó en C05.
- En un `@WebMvcTest`, un controller anidado en la clase del test no se registra solo, porque Spring Boot excluye del escaneo las clases internas de los tests: hace falta `@Import` (C05).
- `MockMvc` con `get(String)` vuelve a codificar un `%` del texto: para un valor con espacios u otros caracteres especiales, usar `.queryParam(...)`.
- Al cerrar C06, la suite tenía 783 casos en 82 clases y `./mvnw clean verify` tardaba unos 171 s, con 97,5 % de líneas y 94,0 % de ramas (al cerrar la validación del JWT, 728 en 81; al cerrar C05, 630 en 76).
- `TestTokens` (`src/test/java/catalogo/`): tokens de usuario firmados con una clave RSA que se genera en cada corrida, con nimbus-jose-jwt, que trae el resource server: válido, vencido y alterado; sin `exp`, sin `sub` o con uno dado, con un `auth` dado, de otro par, `HS256` y sin firma; y con un vencimiento exacto, para usar con un reloj fijo. También escribe archivos PEM temporales: la pública de prueba (`publicKeyLocation()`, que usan `TestcontainersConfiguration` y `TestSecurityConfiguration`), la de otro par, una de 1024 bits, una de curva elíptica, la privada y uno que no es una clave.
- Falla de la base a pedido: renombrar una tabla y restaurarla en un `finally`, y comprobar después de cada caso que las tablas siguen con su nombre. Para una sincronización a medias, una conexión propia del `DataSource` sin confirmación automática (`ProfessionalCategoriesDatabaseIntegrationTest`).
- Para ver un log del arranque en una clase cuya configuración comparte otra: `@DirtiesContext(classMode = BEFORE_CLASS)`. Sin eso, Spring reutiliza el contexto ya arrancado y el log del arranque no se ve (`ProfessionalCategoriesWithoutCatedraIntegrationTest`).
- Base detenida (`ProfessionalCategoriesDatabaseDownIntegrationTest`): un `PostgreSQLContainer` propio, conectado por `@DynamicPropertySource` y sin `TestcontainersConfiguration`; `spring.datasource.hikari.connection-timeout` bajado, en milisegundos (`2000`); y `@DirtiesContext`. Con la base detenida hay dos caminos, y los dos dan `500`: la conexión recién usada se entrega sin comprobar y falla en el acto, o el pool comprueba las conexiones, las encuentra muertas y espera hasta su tiempo. **Hikari entrega sin comprobar una conexión usada hace menos de 500 ms**, así que el camino del primer pedido depende de la velocidad de la máquina. Para forzar el del pool, el test espera 1 s antes del segundo pedido (con Awaitility, no con `Thread.sleep`, que SonarQube marca). Sin esa espera, el test pasaba en la máquina de desarrollo y falló en GitHub Actions (2026-11-20). Turnos ya lo tenía anotado.
- E2E: paquete `e2e` (`src/test/java/e2e/`), clases con `@Tag("e2e")` y `@EnabledIf("e2e.E2ECredentials#available")`: corren solo con `-Pe2e` y se saltean sin las tres credenciales de la cátedra en el `.env`. Usan una base descartable de Testcontainers y la cátedra real, y comprueban solo lo que hace este servicio. Para probar el perfil sin llamar a la cátedra: `./mvnw test -Pe2e -Dtests.env-file=<archivo inexistente>` (la propiedad de la línea de comandos le gana a la del perfil; comprobado con `help:evaluate`). El E2E de C02 (`IncrementalSynchronizationE2ETest`) exige además `CATALOGO_E2E_KAFKA_CONSUMER_GROUP` terminada en `-e2e` (`E2ECredentials.kafkaAvailable`) y nunca usa el grupo del servicio: mueve la posición de su grupo de pruebas al último aviso retenido para no esperar el próximo, y lo entrega dos veces.
- La salida se captura por método (`OutputCaptureExtension`): un log que salió durante otro método de la misma clase no se ve. Si un test mira el log de algo que ocurre una sola vez, como el login del arranque, la clase tiene un solo método.
- Para contar los tests de una corrida, usar los reportes XML (`target/surefire-reports/TEST-*.xml`), siempre después de `clean`: sin él quedan reportes de tests que ya no existen. **Contar los `testcase`, no el atributo `tests`:** con clases `@Nested`, Surefire lo suma mal (`CorsSecurityTest` dice 12 con 13 casos). Un caso falló si tiene un `failure` o un `error` adentro; un `system-out` es solo la salida capturada.
- Algunos tests imprimen el carácter nulo (avisos con un `eventId` inválido): para filtrar su log con `grep`, quitarlo antes (`tr -d '\000'`), porque si no `grep` lo trata como binario y no muestra nada.
- Práctica acordada: después de que un test pasa, una prueba en negativo (romper el código a propósito, ver que el test falla, restaurar el archivo y comprobar el hash).
- No usar `pkill -f` ni `pgrep -f` con un texto que esté en el propio comando: se corta o se encuentra a sí mismo.

## Documentación del servicio

- `docs/`: copias de los casos de uso, los contratos, el dominio y las secuencias. La fuente de verdad está en el repositorio `documentacion`. `contratos_comunes.md` y el caso de uso X01 tienen que ser idénticos en los tres repositorios: compararlos al empezar cada SPEC.
- `docs/sdd/<id>-<nombre-corto>/`: SPEC, PLAN y TASKS de cada funcionalidad. Hechas: `x01-cuenta-tecnica`, `c01-sincronizacion-completa`, `c03-verificacion-periodica`, `c02-sincronizacion-incremental`, `c04-listar-categorias`, `c05-buscar-profesionales`, `validacion-jwt` y `c06-horarios-profesional`. `validacion-jwt` no es un caso de uso: su carpeta no lleva id, como excepción indicada por el usuario.
- `docs/decisiones/NNN-titulo.md`: textos de las decisiones complejas. `001`: sesión técnica con la cátedra. `002`: la copia local del catálogo en un solo slice. `003`: qué guarda el estado de sincronización y cuándo cambia. `004`: el aviso de Kafka, dónde se recibe y cómo llega a `synchronization`. `005`: el manejador global de errores, qué hace y por qué existe (ampliada en C05 con el `400`, y en C06 con el `400` de un parámetro validado en el método y su línea del log). `006`: cuándo se usa un resultado sellado y cuándo una excepción. `007`: la seguridad del servicio (la validación del JWT, los rechazos, la clave, CORS y CSRF, por qué C06 está protegido, y las limitaciones, con la trazabilidad del `sub` de C06 y las URL que terminan en `401`); es la descripción de seguridad que pide el enunciado §12.
- El `README.md` lo escribe el usuario.
