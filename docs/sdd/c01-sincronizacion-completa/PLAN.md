# PLAN: C01 — Sincronizar catálogo completo (snapshot)

**SPEC de referencia:** `docs/sdd/c01-sincronizacion-completa/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-17 (sin commitear todavía, en la rama `sync-completa`)
**Estado:** Aprobado <!-- Borrador | En revisión | Aprobado -->

<!-- PARA LA PERSONA
Copia esta plantilla como PLAN.md junto a la SPEC.md aprobada.
Este documento define la solución técnica. Una vez revisado, el agente puede
derivar TASKS.md con tareas, dependencias y comprobaciones.
-->

<!-- PARA EL AGENTE
- Lee la SPEC.md aprobada, las instrucciones del proyecto, BACKEND_GUIDELINES.md,
  los documentos citados en Referencias de la SPEC y la skill
  hexagonal-vertical-slicing. Si falta un documento necesario o la spec no está
  aprobada, indícalo antes de avanzar.
- Inspecciona el repositorio. Referencia rutas verificadas y distingue las nuevas propuestas.
- Propón una solución proporcional al alcance y coherente con el proyecto.
  Reutiliza lo existente y justifica nuevas dependencias o cambios de arquitectura.
- Aplica la skill hexagonal-vertical-slicing tal como está: no la reinterpretes.
  Si la funcionalidad necesita algo que la skill no contempla, regístralo como
  decisión pendiente en lugar de improvisar una estructura.
- Distingue hechos, decisiones confirmadas y propuestas. Consulta las decisiones
  no resueltas; haz pocas preguntas por vez y actualiza el plan con las respuestas.
- Referencia los requisitos y criterios por su ID, y los contratos y casos de uso
  por archivo e ID, sin copiar su contenido.
- Si una decisión cambia el comportamiento o alcance, vuelve a la spec y solicita
  confirmación. No resuelvas una duda de producto mediante una suposición técnica.
- Conserva estos comentarios. No implementes durante la planificación.
- Solicita aprobación antes de marcar el plan como Aprobado. La autorización
  para implementar debe ser explícita; no se deduce del estado de los documentos.
-->

## Contexto técnico verificado

<!-- Qué existe hoy y cómo participa en la funcionalidad: código, slices,
migraciones y configuración ya implementados. Si todavía no hay código para
esto, indícalo. -->

Verificado el 2026-11-17 en la rama `sync-completa`. Todavía no hay código de C01.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| Sesión técnica (X01) | `src/main/java/catalogo/catedra/domain/ports/in/GetTechnicalSessionUseCase.java`, `RenewTechnicalSessionUseCase.java` | Dan el JWT técnico y lo renuevan. Los usa el pedido del snapshot (RF-01, RF-05) |
| Cliente del login | `catedra/infrastructure/client/CatedraLoginClientAdapter.java` | `RestClient` con `JdkClientHttpRequestFactory` y timeouts; clasifica `4xx`, `5xx`, sin conexión y respuesta ilegible. Modelo para el cliente del snapshot (RF-13) |
| Propiedades de la cátedra | `catedra/infrastructure/client/CatedraProperties.java` | `record` con `base-url`, credenciales, timeouts y la sección `login`. Suma la sección `snapshot` (D8) |
| Política de reintentos del login | `catedra/domain/model/TechnicalLoginPolicy.java` | Máximo de intentos, espera inicial, multiplicador, tope e intervalo mínimo; calcula la espera de cada intento. No cambia: el snapshot tiene su propia política (P8) |
| Espera entre intentos | `catedra/application/usecases/StartTechnicalSessionUseCaseImpl.java` | `Thread.sleep` con la espera de la política; los tests usan milisegundos |
| Reloj | `catalogo/config/ClockConfig.java` | Bean `Clock`. Lo usa `catalog` para los instantes del resultado |
| Configuración | `src/main/resources/application.yaml` | Base PostgreSQL, Hibernate solo valida (`ddl-auto: validate`), `open-in-view: false`, sección `catalogo.catedra` |
| Migraciones | `src/main/resources/db/migration/` | Vacía. C01 crea la primera (`V1`). Flyway ya está en el `pom.xml` |
| Build | `pom.xml` | Spring Boot 4.1.2, Java 25, JaCoCo. Los tests apuntan `catalogo.env-file` a un archivo inexistente. **No hay perfil `e2e`** ni filtro por etiquetas: los suma C01 (P10) |
| Tests: base | `src/test/java/catalogo/TestcontainersConfiguration.java` | PostgreSQL 18 con `@ServiceConnection` |
| Tests: cátedra simulada | `src/test/java/catalogo/FakeHttpServer.java`, `catedra/FakeCatedraServer.java` | Respuestas en orden por ruta, demoras y registro de pedidos. `FakeCatedraServer` suma la ruta del snapshot |
| Tests: ayudantes de `catedra` | `catedra/application/usecases/ScriptedLoginClient.java`, `catalogo/MutableClock.java`, `catedra/TechnicalIntegrations.java` | Cliente de login falso, reloj controlable e integraciones de ejemplo |
| Snapshot real | Leído el 2026-11-16 (SPEC, "Situación actual"); copia en el scratchpad de esa sesión, sin versionar | Base de una grabación recortada para los tests (P10) |
| Turnos, como referencia | `turnos_service/pom.xml` (perfil `e2e`), `turnos_service/src/main/resources/db/migration/V1__create_users.sql` | Convenciones de migración (`V{n}__{accion}_{tabla}.sql`) y del perfil `e2e` (`tests.groups` y `tests.excluded-groups`). En turnos, `@Transactional` solo aparece en un adaptador de persistencia |

**Convenciones y slice de referencia:** `catedra` (X01) para lo que toca a la cátedra. `catalog` y `synchronization` son nuevos (decisión 002). La skill `hexagonal-vertical-slicing` se aplica tal como está; cada excepción se analiza en el contexto de C01 (P9).

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de la solicitud, el evento o la tarea desde que entra hasta la
respuesta, la persistencia o el mensaje publicado. Referencia el diagrama de
secuencia del proyecto si ya lo describe; usa un diagrama nuevo solo si aporta
claridad. -->

Tres slices, según la decisión 002: `catedra` pide el snapshot y lo valida, `synchronization` conduce el proceso y `catalog` guarda la copia. El recorrido sigue la secuencia §1. Lo marcado con P está pendiente.

```
(los tests y el E2E; después, C02 y C03)
└─ SynchronizationService.synchronizeFullCatalog()                        synchronization/application/service
   └─ SynchronizeFullCatalogUseCaseImpl                                    synchronization/application/usecases
      ├─ una sola sincronización a la vez: si hay otra, Discarded          (RF-09; P7)
      ├─ GetCatalogSnapshotUseCase (catedra)                               (P2)
      │    = GetCatalogSnapshotUseCaseImpl
      │      ├─ sesión: GetTechnicalSession; si no hay, RenewTechnicalSession(null)       (RF-01)
      │      ├─ CatalogSnapshotClient.fetchSnapshot(jwt): un solo intento                  (RF-13)
      │      │    = CatedraSnapshotClientAdapter: GET /api/synchronization/snapshot, DTO, mapper y validación (P3)
      │      ├─ 401 → RenewTechnicalSession(sesión) y un segundo pedido                    (RF-05)
      │      ├─ no disponible → reintentos con espera creciente                            (RF-06; P8)
      │      └─ devuelve Obtained(snapshot) | Unavailable | Rejected | Invalid
      ├─ Obtained → modelos de catalog → ReplaceCatalogUseCase (catalog)   (P2, P4)
      │    = ReplaceCatalogUseCaseImpl, en una transacción:
      │      tres colecciones, versión local y resultado SUCCESS           (RF-03, RF-04; P5)
      ├─ fracaso → RecordSyncFailureUseCase (catalog): FULL, ERROR, tipo   (RF-14)
      │    si la base tampoco acepta el registro, solo log                 (RF-08)
      └─ devuelve FullSyncResult: Applied(versión) | Failed(motivo) | Discarded   (RF-10)
```

Traducción de cada fracaso a su tipo de error (SPEC, "Datos y reglas de negocio"):

| Resultado de `catedra` o de `catalog` | Tipo de error |
| --- | --- |
| `Unavailable`: sin sesión después de pedirla, renovación sin sesión, reintentos agotados (`5xx`, timeout, corte) | `SOURCE_UNAVAILABLE` |
| `Rejected`: `403`, otro `4xx`, `401` con el JWT renovado | `INTEGRATION_REJECTED` |
| `Invalid`: `200` ilegible o snapshot incompleto o inconsistente | `INVALID_DATA` |
| Falla al reemplazar la copia | `PERSISTENCE_FAILURE` |

- **Qué decide cada pieza.** El cliente hace un solo intento y clasifica la respuesta HTTP sin conocer reintentos ni sesión. El caso de uso de `catedra` maneja la sesión, el `401` y los reintentos, como pide X01 a cada operación con la cátedra (`CLAUDE.md`, "Cómo usar la sesión técnica"). `synchronization` decide qué hacer con cada resultado y qué tipo de error registrar. `catalog` solo guarda.
- **El JWT técnico no sale de `catedra`:** `synchronization` recibe el snapshot ya leído, nunca el token.
- **Una sola sincronización a la vez (RF-09):** el control vive en `synchronization` y deja volver a entrar a quien ya lo tiene, para que C02 y C03 pidan C01 desde adentro (D2). Cómo se arma: P7.
- **Instantes:** `catalog` toma el instante del reloj al registrar el resultado, éxito o fracaso.
- **Logs (RF-12):** `catedra` registra cada intento y su motivo; `synchronization`, el resultado final con la versión y las cantidades aplicadas, o el tipo de error y el motivo; nunca el JWT.

## Slices y componentes afectados

<!-- Aplica la skill hexagonal-vertical-slicing: un paquete por slice, con sus
capas domain, application e infrastructure. Identifica los slices afectados, sus
responsabilidades y la dirección de sus dependencias. Una funcionalidad puede
afectar más de un slice. Justifica cualquier slice nuevo o dependencia nueva
entre slices. -->

| Slice | Existe / nuevo | Responsabilidad y cambios | Dependencias con otros slices |
| --- | --- | --- | --- |
| `catedra` (`catalogo/catedra/`) | Existente | Suma el pedido del snapshot: sesión, `401`, reintentos, cliente HTTP y validación de la respuesta | Ninguna. `synchronization` pasa a usar su puerto de entrada nuevo |
| `catalog` (`catalogo/catalog/`) | Nuevo (decisión 002) | La copia local y su estado de sincronización: reemplazarla y registrar un fracaso. Más adelante, las consultas de C04 a C06 | Ninguna. `synchronization` usa sus puertos de entrada |
| `synchronization` (`catalogo/synchronization/`) | Nuevo (decisión 002) | La sincronización completa y la regla de una sola sincronización a la vez | Usa `catedra` y `catalog` (P2) |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son
propuestas. Señala impacto sobre modelos, contratos o componentes compartidos. -->

Todo es nuevo, salvo lo marcado como "Modificar". Las rutas son propuestas y algunas dependen de las decisiones pendientes.

| Slice y capa | Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- | --- |
| catedra / domain | `model/CatalogSnapshot`, `SnapshotCategory`, `SnapshotProfessional`, `SnapshotWeeklySchedule` | Crear | El snapshot tal como lo entrega la cátedra, ya con `DayOfWeek` y `LocalTime`. Sabe si viene sin categorías, si tiene ids repetidos o si tiene referencias rotas, como `TechnicalIntegration` sabe qué datos le faltan (P3) | RF-02, RF-11 |
| catedra / domain | `model/CatalogSnapshotResult` | Crear | Resultado para quien pide el snapshot: `Obtained`, `Unavailable`, `Rejected`, `Invalid`, cada uno con su motivo | RF-01, RF-02, RF-05 a RF-07, RF-13 |
| catedra / domain | `model/SnapshotFetchResult` | Crear | Resultado de un intento (se llamaba `SnapshotResponse` en la versión aprobada; se renombró para no confundirlo con el DTO `CatalogSnapshotResponse`): `Received`, `TokenRejected`, `Rejected`, `Unavailable`, `Invalid` | RF-13 |
| catedra / domain | `model/CatalogSnapshotPolicy` | Crear | `record` con máximo de intentos, espera inicial, multiplicador y tope; calcula la espera antes de cada intento. Propia, separada de `TechnicalLoginPolicy` (P8) | RF-06 |
| catedra / domain | `ports/in/GetCatalogSnapshotUseCase` | Crear | Pide el snapshot con todo lo de la sesión resuelto | RF-01 |
| catedra / domain | `ports/out/CatalogSnapshotClient` | Crear | Un intento con un JWT dado | RF-13 |
| catedra / application | `usecases/GetCatalogSnapshotUseCaseImpl` | Crear | Sesión, `401` con una renovación, reintentos y log de cada intento | RF-01, RF-05, RF-06, RF-12 |
| catedra / application | `service/TechnicalSessionService` | Sin cambios | `synchronization` usa directamente el puerto de entrada del snapshot (P2); nadie lo pide por el service | — |
| catedra / infrastructure | `client/CatedraSnapshotClientAdapter`, `client/dto/` (snapshot y sus tres elementos), `client/mapper/CatalogSnapshotMapper` | Crear | `RestClient` con timeouts propios; clasifica la respuesta (RF-13); el mapper valida que estén los datos y sus formatos (P3), y entrega `Invalid` con el motivo si falta algo; ignora campos desconocidos, `createdAt`, `updatedAt` y `generatedAt` | RF-02, RF-11, RF-13 |
| catedra / infrastructure | `client/CatedraProperties`, `client/CatedraClientConfiguration` | Modificar | Sección `snapshot` con timeouts y reintentos (D8); `CatedraClientConfiguration` arma también `CatalogSnapshotPolicy`. `TechnicalLoginPolicy` no cambia | RF-06 |
| catalog / domain | `model/ProfessionalCategory`, `Professional`, `WeeklySchedule` | Crear | Modelos del dominio §1: el profesional tiene su categoría y el horario su profesional, como objetos | RF-03 |
| catalog / domain | `model/CatalogSyncState`, `SyncType`, `SyncResult`, `SyncErrorType` | Crear | Estado único de sincronización del dominio §2, con `lastSuccessfulSyncAt` | RF-04, RF-14 |
| catalog / domain | `ports/in/ReplaceCatalogUseCase`, `RecordSyncFailureUseCase` | Crear | Reemplazar la copia con su versión; registrar un fracaso | RF-03, RF-04, RF-14 |
| catalog / domain | `ports/out/ProfessionalCategoryRepository`, `ProfessionalRepository`, `WeeklyScheduleRepository`, `CatalogSyncStateRepository` | Crear | Uno por entidad, como la skill (P4). Métodos de C01: guardar, quitar los que no están en una lista de `id`, y leer y guardar el estado | RF-03, RF-04, RF-14 |
| catalog / application | `usecases/ReplaceCatalogUseCaseImpl`, `RecordSyncFailureUseCaseImpl` | Crear | `ReplaceCatalogUseCaseImpl` con `@Transactional`: escribe las tres colecciones en orden, la versión y el resultado (P4). `RecordSyncFailureUseCaseImpl`, en su propia transacción. Sin `CatalogService` ni `CatalogException` en C01: nadie entra por el service (`synchronization` usa los puertos de entrada, P2) y nada lanza una excepción propia; los suma C04 con su controller | RF-03, RF-04, RF-08, RF-14 |
| catalog / infrastructure | `persistence/entity`, `repository`, `mapper`, `adapter` de las tres entidades y del estado | Crear | JPA con los `id` de la cátedra (P9). `ProfessionalEntity` tiene su `ProfessionalCategoryEntity` y `WeeklyScheduleEntity` su `ProfessionalEntity`, con `@ManyToOne` (P6). Guardar actualiza si el `id` existe e inserta si no; quitar borra los `id` que no están en la lista dada (P5) | RF-03, RF-11 |
| synchronization / domain | `model/FullSyncResult`, `ports/in/SynchronizeFullCatalogUseCase` | Crear | Resultado para quien pide la sincronización: `Applied(versión)`, `Failed(motivo)`, `Discarded`. El motivo es un texto que empieza con el tipo de error (por ejemplo `SOURCE_UNAVAILABLE: …`): el dominio de `synchronization` no puede importar `SyncErrorType`, que es de `catalog` (skill §2), y el tipo exacto ya queda registrado en el estado | RF-10 |
| synchronization / application | `usecases/SynchronizeFullCatalogUseCaseImpl` | Crear | El proceso del diagrama. Recibe los puertos de entrada `GetCatalogSnapshotUseCase` (`catedra`), `ReplaceCatalogUseCase` y `RecordSyncFailureUseCase` (`catalog`) (P2). Traduce cada fracaso a su tipo y arma los modelos de `catalog` con el snapshot, en un método aparte. Toda excepción que salga del reemplazo cuenta como `PERSISTENCE_FAILURE`: `catalog` solo escribe | RF-01 a RF-14 |
| synchronization / application | `service/SynchronizationService` | Crear | Expone la sincronización completa. Hoy la usan el E2E y los tests de integración; después, los disparadores de C02 y C03, que entran por el service | RF-10 |
| synchronization / application | `support/SynchronizationLock` | Crear | Componente con un candado de Java que deja volver a entrar al mismo hilo: "intentar tomarlo" (C01 y, después, C03) y, más adelante, "esperar a tomarlo" (C02). En memoria, para una sola instancia (P7) | RF-09 |
| Proyecto | `src/main/resources/db/migration/V1__create_catalog.sql` | Crear | Tablas de la copia y del estado (ver "Datos y contratos") | RF-03, RF-04 |
| Proyecto | `src/main/resources/application.yaml` | Modificar | Valores de `catalogo.catedra.snapshot` si hace falta escribirlos (los por defecto van en las propiedades) | RF-06 |
| Proyecto | `pom.xml` | Modificar | Propiedades `tests.groups` y `tests.excluded-groups`, filtro de surefire por etiquetas y perfil `e2e` con el `.env` local, como turnos (P10) | CA-11 |
| Tests | `FakeCatedraServer`, `src/test/resources/catedra/snapshot-200.json` | Modificar / crear | Ruta del snapshot y respuestas armadas; recorte del snapshot real con datos ficticios y el mismo formato (P10) | CA-01 a CA-15 |
| Tests | `src/test/java/e2e/FullSynchronizationE2ETest` (o junto a los de `synchronization`) | Crear | E2E de P10 | CA-11 |
| Proyecto | `CLAUDE.md` | Modificar | Slices, excepciones de C01, comandos nuevos (`-Pe2e`) y la decisión 002 | `AGENTS.md`, "Código" |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Operaciones, eventos y DTO de entrada y salida:** snapshot según el contrato §4.2 y el anexo §7. El contrato no cambia. C01 no expone operaciones.
- **Identificadores, relaciones y restricciones:** los `id` son los de la cátedra (regla 1), sin generación local (P9). El profesional apunta a su categoría y el horario a su profesional: en JPA, relaciones muchos a uno; en la base, columnas con clave foránea, que quedan como segunda protección (P6). El estado de sincronización es una sola fila.
- **Origen de los datos devueltos o publicados y transformaciones:** todo sale del snapshot. El mapper de `catedra` convierte `dayOfWeek` a `java.time.DayOfWeek` y las horas `HH:mm` o `HH:mm:ss` a `LocalTime`; descarta lo que no se guarda (regla 7).
- **Persistencia, consultas y migraciones:** `V1__create_catalog.sql`, propuesta:
  - `professional_categories`: `id BIGINT` clave primaria, `name VARCHAR NOT NULL`, `description VARCHAR`, `enabled BOOLEAN NOT NULL`.
  - `professionals`: `id`, `category_id` con clave foránea, `first_name` y `last_name VARCHAR NOT NULL`, `enabled`.
  - `weekly_schedules`: `id`, `professional_id` con clave foránea, `day_of_week VARCHAR NOT NULL`, `start_time` y `end_time TIME NOT NULL`, `slot_duration_minutes INTEGER NOT NULL`, `enabled`.
  - `catalog_sync_state`: una sola fila (`id` fijo en 1, con una restricción que lo asegura), `local_version BIGINT`, `last_sync_at` y `last_successful_sync_at TIMESTAMPTZ`, `last_sync_type`, `last_sync_result` y `last_error_type VARCHAR`. La migración inserta la fila vacía: "no inicializado".
  - Textos sin largo máximo (`VARCHAR` sin límite, como `image_url` en turnos): la cátedra no publica límites, y un nombre más largo no debe frenar todo el snapshot. Los del snapshot real miden como mucho 63 caracteres.
  - Los índices para las búsquedas de C05 quedan para su PLAN.
- **Convivencia entre datos locales y remotos:** la copia queda exactamente igual al snapshot (D3). Dentro de la transacción, se guardan en orden las categorías, los profesionales y los horarios del snapshot (se actualiza lo que existe y se inserta lo nuevo), y después se quitan los horarios, profesionales y categorías cuyo `id` no vino (P5).
- **Compatibilidad y migraciones de datos existentes:** no aplica; es la primera migración.

## Transacciones, concurrencia y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de BACKEND_GUIDELINES.md. -->

- **Estados y transiciones:** el estado de sincronización pasa de vacío ("no inicializado") a inicializado con el primer éxito, y nunca vuelve a vacío. Un éxito actualiza todo; un fracaso, solo el último intento (RF-04, RF-14).
- **Transacciones y consistencia:** el reemplazo de las tres colecciones, la versión y el resultado `SUCCESS` van en una transacción, abierta por `ReplaceCatalogUseCaseImpl` (RF-03, RF-04; P4). El pedido del snapshot y sus reintentos ocurren antes, fuera de la transacción, para no tener una conexión a la base abierta mientras se espera a la cátedra. Si la transacción falla, se deshace entera y el fracaso se registra en otra, independiente (RF-08, RF-14). Si esa también falla, se deja en el log y C01 termina con `Failed("PERSISTENCE_FAILURE: …")`.
- **Idempotencia y mensajes duplicados:** repetir con el mismo snapshot deja la copia igual. No hay mensajes (C02).
- **Concurrencia y tareas programadas:** una sola sincronización a la vez, en memoria y para una sola instancia (RF-09; P7). Las lecturas de C04 a C06 ven la copia anterior o la nueva: PostgreSQL no muestra cambios sin confirmar.
- **Timeouts, reintentos y errores de dependencias:** propiedades `catalogo.catedra.snapshot.connect-timeout` (5 s), `read-timeout` (10 s), `max-attempts` (5), `initial-delay` (1 s), `multiplier` (2) y `max-delay` (30 s) (D8). La renovación por `401` no cuenta como reintento: es un segundo pedido, una sola vez (RF-05). Si durante los reintentos la cátedra responde `401`, se renueva igual, como máximo una vez por sincronización.
- **Seguridad y autorización:** sin endpoints nuevos. El JWT técnico solo vive en `catedra` (contrato común §1.1).
- **Otras consideraciones de backend aplicables y su solución:**
  - Arranque: Flyway corre `V1` al arrancar; Hibernate valida las entidades contra esas tablas.
  - Manejo global de errores HTTP (respuestas `problem+json` con `code`, como el `ApiExceptionHandler` de turnos, su decisión 003): no aplica, porque C01 no tiene endpoints. Se decide con C04, el primer endpoint (`503` `CATALOG_NOT_INITIALIZED`, `500` `INTERNAL_ERROR`).
  - Interrupción del hilo durante una espera: se corta la sincronización como cátedra no disponible y se restaura la marca de interrupción, como en X01.

## Dependencias y configuración

<!-- Librerías, servicios externos, propiedades de configuración y secretos
afectados. Verifica compatibilidad con el proyecto y justifica las incorporaciones.
No agregues dependencias por defecto. Los valores sensibles y los parámetros
ajustables se externalizan. -->

- **Dependencias:** ninguna nueva. JPA, Flyway y PostgreSQL ya están en el `pom.xml`; el cliente usa `RestClient` como el login.
- **Propiedades nuevas:** la sección `catalogo.catedra.snapshot` de "Transacciones, concurrencia y errores", con sus valores por defecto en `CatedraProperties`, como la sección `login`. Sin variables nuevas en el `.env.example`.
- **Secretos:** ninguno nuevo. El E2E usa las credenciales del `.env` local, como la prueba manual de X01.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, de integración (base de datos, mensajería), de API o prueba manual.
No todos requieren todos los métodos. Identifica tests existentes y separa los
nuevos propuestos. Incluye regresiones relevantes. Una prueba con mocks no
demuestra persistencia, transacciones ni idempotencia. -->

Todos los tests son nuevos. Los nombres son propuestas.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Integración `FullSynchronizationIntegrationTest`: aplicación completa, cátedra simulada y base real | `FakeCatedraServer` con la grabación del snapshot; PostgreSQL de Testcontainers | Tablas iguales al snapshot; estado con versión, `FULL`, `SUCCESS` y los dos instantes; resultado `Applied` |
| CA-02 | Integración del reemplazo contra la base real (`JpaCatalog…AdapterTest` o del caso de uso) | Copia previa; snapshot con una entidad modificada, una nueva, una deshabilitada, una que falta y un profesional que cambia de categoría | Copia igual al snapshot nuevo; la que faltaba ya no está |
| CA-03, CA-12 | Unitarios del mapper y del modelo del snapshot (`CatalogSnapshotMapperTest`, `CatalogSnapshotTest`) | Un snapshot por cada defecto y por cada caso aceptado | `Invalid` con el motivo; o un snapshot válido |
| CA-04, CA-05, CA-06, CA-13, CA-14 | Unitario `GetCatalogSnapshotUseCaseImplTest` con un cliente falso que responde en orden; adaptador `CatedraSnapshotClientAdapterTest` contra el servidor JDK | `FakeCatedraServer`: `401`, `403`, `400`, `404`, `429`, `500`, `502`, `503`, `504`, demora, puerto cerrado, `200` no JSON | Cantidad de pedidos y de renovaciones, esperas crecientes y resultado de cada caso |
| CA-07 | Unitario del caso de uso de `synchronization` con un reemplazo que falla; integración con un disparador de PostgreSQL creado por el test que hace fallar un `INSERT` a mitad del reemplazo | Base real | Datos y versión anteriores intactos en la base; `PERSISTENCE_FAILURE`; con el registro también fallando, el error en el log |
| CA-08 | Unitario de `synchronization`: dos hilos piden a la vez, con la cátedra demorada hasta que los dos pidieron; y un pedido hecho desde dentro de una sincronización en curso | Cliente falso con espera controlada | Un pedido del snapshot y un `Discarded`; el pedido desde adentro corre |
| CA-09 | Unitario del mapper | Horas en los dos formatos | Misma `LocalTime` |
| CA-10 | Integración con captura de la salida (`OutputCaptureExtension`) | Los escenarios de integración | Intentos y resultado en el log; sin el JWT de test |
| CA-11 | E2E `FullSynchronizationE2ETest` (`@Tag("e2e")`), corrido por el usuario con `-Pe2e` (P10) | Cátedra real, `.env` local, PostgreSQL de Testcontainers | Cantidades y versión de la base iguales a las del snapshot que el test leyó en la misma corrida |
| CA-15 | Integración: un éxito y después un fracaso de cada tipo | Cátedra simulada, base real | Último intento con el fracaso; versión y último éxito sin cambios |

Además, por la skill (§10): pruebas de ida y vuelta de cada mapper de persistencia y de los DTO del snapshot.

**Comprobaciones de regresión:** la suite completa de X01 (135 tests al cerrar X01) y los cinco chequeos de la skill (§12).

**Comandos verificados para compilar y ejecutar tests:** los del `CLAUDE.md` del repositorio, ejecutados en X01. El perfil `-Pe2e` es nuevo: se verifica en la implementación y se suma al `CLAUDE.md`.

**Pruebas con infraestructura real o simulada:** cátedra simulada con el servidor HTTP del JDK en todos los tests automáticos; PostgreSQL real con Testcontainers en los de persistencia e integración; cátedra real solo en el E2E, que corre el usuario después de ver la lista de pedidos (un login técnico y un `GET` del snapshot).

**Limitaciones del entorno:** CA-11 depende de que la cátedra esté disponible; si no, queda como no verificado.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **La copia local (`catalog`):** migración, modelos, persistencia y los dos casos de uso. Se comprueba contra la base real (CA-02, CA-07 en su parte de base, CA-15 en su parte de base).
2. **El snapshot en `catedra`:** modelo, validación, DTO, mapper y cliente. Se comprueba con unitarios y contra la cátedra simulada (CA-03, CA-09, CA-12, CA-14 en su parte HTTP).
3. **El pedido con sesión y reintentos:** `GetCatalogSnapshotUseCaseImpl` y sus propiedades (CA-04 a CA-06, CA-13, CA-14).
4. **El proceso (`synchronization`):** control de una sola a la vez, caso de uso, service y resultado (CA-07, CA-08).
5. **Integración completa:** cátedra simulada y base real (CA-01, CA-10, CA-15).
6. **E2E:** perfil `e2e` y el test; lo corre el usuario (CA-11).
7. **Cierre:** regresión, chequeos de la skill y `CLAUDE.md`.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - Hibernate ordena las escrituras de una transacción a su manera (primero altas, después cambios, al final bajas). Un reemplazo mal ordenado puede chocar con una clave foránea, por ejemplo al quitar una categoría a la que todavía apunta un profesional que se movió. CA-02 incluye ese caso.
  - Con `id` asignados por la cátedra, Spring Data hace una consulta por entidad antes de guardarla (no sabe si es nueva). Con 76 entidades no pesa; se mide en la integración.
  - El E2E hace un login técnico nuevo en cada corrida. Ya se comprobó que no invalida los JWT anteriores (X01, D5).
  - El catálogo de la cátedra cambia: el E2E compara contra el snapshot que leyó el propio servicio, no contra cantidades fijas.
- **Decisiones pendientes:**
  - Ninguna.

- **Decisiones tomadas:**
  - **Aprobación:** el usuario aprobó el PLAN el 2026-11-17. Ese mismo día, después de la primera tanda, aprobó el cambio de D4 de la SPEC (el snapshot sin categorías se rechaza): la regla va en `catedra` según P3, la escritura de `catalog` no cambia, y se quitó el test del reemplazo con un snapshot vacío, que probaba un caso que ya no puede llegar. Después de la segunda tanda aprobó renombrar `SnapshotResponse` a `SnapshotFetchResult`, sin cambios de comportamiento. Después de la tercera aprobó que `FullSyncResult.Failed` lleve un motivo de texto que empieza con el tipo de error, en lugar del tipo.
  - **P1 · Estructura de slices (2026-11-16):** `catalog` con la copia local y su estado, `synchronization` con el proceso y `catedra` como única puerta hacia la cátedra. Texto completo: `docs/decisiones/002-copia-local-del-catalogo-en-un-slice.md`.
  - **P3 · Dónde se valida el snapshot (2026-11-17):** todo en `catedra`. El mapper del cliente valida que estén los datos de D4 y sus formatos; el modelo `CatalogSnapshot` detecta ids repetidos y referencias rotas, y desde el cambio de D4 del 2026-11-17 también un snapshot sin categorías. `catedra` entrega solo snapshots válidos, o `Invalid` con el motivo, como X01 con una integración incompleta. `synchronization` y `catalog` reciben datos ya consistentes; la clave foránea de la base queda como segunda protección. La comprobación de referencias contra la copia local que necesitará C02 irá en `catalog`, en su PLAN. Alternativas descartadas: referencias e ids repetidos en `catalog`, que obligaba a sumar tipos "planos" con ids y repartía `INVALID_DATA` en dos lugares; todo en `synchronization`, cuyo dominio no puede usar los tipos de `catedra`.
  - **P2 · Cómo usa `synchronization` a `catedra` y a `catalog` (2026-11-17):** como slice fachada (skill §9): su caso de uso recibe directamente los puertos de entrada `GetCatalogSnapshotUseCase`, `ReplaceCatalogUseCase` y `RecordSyncFailureUseCase`, y trabaja con los modelos de dominio de los dos slices; nunca toca su aplicación ni su infraestructura. Es la opción que la skill da para un slice que compone a otros sin dominio propio, y así es `synchronization` en C01. La traducción del snapshot a los modelos de `catalog` va en un método aparte del caso de uso, y la cubre un test que arma el snapshot con todos los datos y comprueba que lleguen todos. Si C02 le da a `synchronization` dominio propio (los eventos procesados), se revisa en su PLAN. Alternativa descartada: puertos de salida propios con adaptadores en `infrastructure/gateway`, como turnos (su decisión 009). Sumaba unos seis archivos y otra excepción a la skill, sin ahorrar trabajo ante un cambio: la línea de traducción solo cambiaba de archivo, y los puertos necesitaban o copias de los modelos o los de los otros slices.
  - **P4 · Transacción del reemplazo y puertos de `catalog` (2026-11-17):** un repositorio por entidad (`ProfessionalCategoryRepository`, `ProfessionalRepository`, `WeeklyScheduleRepository`, `CatalogSyncStateRepository`) y la transacción en `ReplaceCatalogUseCaseImpl` con `@Transactional`. Se eligió porque sigue el molde de la skill (`{Entity}Repository`), deja escrita la "única unidad de trabajo" donde vive la regla, y los mismos repositorios sirven después para C02 (actualizar por `id`) y para las consultas de C04 a C06. La anotación está en `application`; la skill no la prohíbe (su chequeo §12 solo prohíbe web, HTTP y Spring Data), aunque turnos nunca la usó ahí. Alternativa descartada: un solo puerto `CatalogCopyRepository.replace(…)` con la transacción en el adaptador, como turnos. Dejaba un caso de uso que solo delega y obligaba a sumar después repositorios por entidad para las consultas: dos estilos de puerto en el mismo slice.
  - **P5 · Cómo se reemplaza la copia (2026-11-17):** actualizar por `id` y después quitar lo que falta. Se eligió porque C02 necesita la misma escritura (actualizar o insertar por `id`) y así hay una sola forma de escribir la copia. Hibernate confirma las bajas al final, así que un profesional que cambió de categoría ya apunta a la nueva cuando se borra la vieja (CA-02 lo comprueba). Alternativas descartadas: borrar todo e insertar, cuya sencillez es aparente (con `id` de la cátedra, Spring Data igual consulta cada entidad antes de guardarla) y dejaba dos formas de escribir; comparar en memoria y escribir solo lo cambiado, código de más para 76 entidades.
  - **P6 · Relaciones en las entidades JPA (2026-11-17):** objetos relacionados (`@ManyToOne`). Se eligió porque el dominio tiene objetos (decisión 002): el mapper traduce uno a uno y el test de ida y vuelta de la skill funciona sin trucos; además, C05 y C06 leerán el profesional con su categoría y sus horarios en una consulta. Alternativa descartada: columnas con el `id`, que obligaban al adaptador a buscar las categorías aparte para armar cada profesional y hacían perder datos en la ida y vuelta. Cuidado para C05: que listar profesionales no haga una consulta extra por cada uno para traer su categoría.
  - **P7 · Control de una sola sincronización a la vez (2026-11-17):** un componente `SynchronizationLock` en `synchronization/application/support/`, con un candado de Java que deja volver a entrar al mismo hilo; C01 lo recibe ahora, y C02 y C03 después. Se eligió porque el control existe para compartirse entre tres casos de uso y permite probar CA-08 completo, incluido el pedido hecho desde dentro de una sincronización en curso. El paquete `application/support` no está en la skill: es una excepción autorizada por el usuario para esta pieza (P9). Alternativas descartadas: un campo dentro del caso de uso de C01, que no dejaba probar el pedido desde adentro y obligaba a reorganizarlo con C03; un puerto de salida con un adaptador en memoria, que sumaba más piezas y otra excepción para la ventaja de poder pasar a un candado en la base con varias instancias, que D2 dejó fuera.
  - **P8 · Política de reintentos del snapshot (2026-11-17):** un `record` propio, `CatalogSnapshotPolicy`, en `catedra/domain/model`, al lado de `TechnicalLoginPolicy`. Está en `catedra` porque lo que se reintenta es el pedido HTTP a la cátedra, no la sincronización entera: `synchronization` no reintenta, registra el fracaso y C03 la vuelve a pedir. Se eligió porque son dos políticas distintas que pueden cambiar por separado (el login tiene además un intervalo mínimo), lo que se repite son unas seis líneas de cálculo con su propio test, y X01 no se toca. Alternativas descartadas: sacar el cálculo a una política común, que ahorraba esas seis líneas a cambio de unir dos conceptos y modificar cinco archivos probados de X01; reusar `TechnicalLoginPolicy`, confuso por el nombre y por el intervalo que el snapshot no usa.
  - **P9 · Excepciones a la skill (2026-11-17):** el usuario autorizó, para C01:
    1. Los `id` los asigna la cátedra: las entidades JPA no llevan `@GeneratedValue` (dominio, regla 1).
    2. `CatalogSyncState` sin `id` en el modelo; su entidad es una sola fila con `id` fijo en 1 (regla 9).
    3. `record` inmutables y resultados sellados: `CatalogSnapshot`, `SnapshotCategory`, `SnapshotProfessional`, `SnapshotWeeklySchedule`, `CatalogSnapshotResult`, `SnapshotFetchResult`, `CatalogSnapshotPolicy` y `FullSyncResult`. Los modelos de `catalog` siguen la skill (clases Lombok).
    4. `infrastructure/client` en `catedra` para el cliente del snapshot.
    5. Puerto de salida para una capacidad técnica: `CatalogSnapshotClient`.
    6. `application/support` en `synchronization` para `SynchronizationLock` (P7).

    Las excepciones de X01 siguen vigentes para X01 (`CLAUDE.md`). No son excepciones: la fachada (P2, opción de la skill §9), `@Transactional` en `application` (P4, la skill no lo prohíbe) y las tres entidades en `catalog` (decisión 002, opción "unir slices"). Variación menor dentro de la skill: los repositorios de `catalog` suman métodos fuera del CRUD (guardar actualizando o insertando, quitar los que no están en una lista), con tipos del dominio. Como pide el usuario, el código no lleva comentarios que marquen excepciones: se listan en el `CLAUDE.md`.
  - **P10 · Tests (2026-11-17):**
    - **Snapshot de los tests:** `src/test/resources/catedra/snapshot-200.json`, un recorte del snapshot real (unas 2 categorías, 3 profesionales con uno deshabilitado y 4 horarios con uno deshabilitado) con **los datos reemplazados por ficticios y el formato exactamente igual**: mismos campos, en el mismo orden y con los mismos tipos; horas `HH:mm:ss`; instantes con los mismos patrones (sin fracción, un `updatedAt` con microsegundos y el `generatedAt` con nanosegundos); textos ficticios de largo parecido, con tildes donde el original las tenía. Los casos con defectos se arman en cada test a partir de este recorte. Alternativas descartadas: uno inventado sin partir del real, que podía no tener las rarezas reales de formato; el snapshot completo, difícil de leer y sin probar más.
    - **Separación del E2E:** como en turnos. Propiedades `tests.groups` y `tests.excluded-groups` en el `pom.xml`, la suite normal excluye `@Tag("e2e")` y el perfil `-Pe2e` corre solo los E2E con el `.env` local. Alternativas descartadas: `maven-failsafe-plugin`, otro plugin y otra forma de correr los tests distinta de turnos; saltear el E2E solo por falta de credenciales, que nunca correría (la suite normal no lee el `.env`) o, si leyera el `.env`, llamaría a la cátedra real en cada `./mvnw test` y en GitHub Actions.
    - **Forma del E2E:** aplicación completa con una base descartable de Testcontainers (no toca la base de desarrollo) y la cátedra real del `.env`. Espera a que termine el login técnico inicial, con un tope. Lee el snapshot una vez por su cuenta, por el puerto de `catedra`, y lo conserva solo en memoria como valor esperado; pide la sincronización completa y compara la base con esa lectura (cantidades por colección y versión). No guarda ninguna copia previa: compara contra lo que la cátedra respondió en esa misma corrida, así que sirve aunque el catálogo cambie de un día a otro. Si la cátedra publicó otra versión entre las dos lecturas, comprueba la versión que aplicó el servicio y que la copia esté completa, y lo deja en el log. Pedidos a la cátedra real por corrida: un login técnico y dos `GET` del snapshot, sin efectos. Se saltea si faltan las credenciales en el `.env`.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados, respeta la skill hexagonal-vertical-slicing y permite
demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
