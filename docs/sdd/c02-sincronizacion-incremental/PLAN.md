# PLAN: C02 — Sincronizar catálogo incrementalmente

**SPEC de referencia:** `docs/sdd/c02-sincronizacion-incremental/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-19, con el ajuste de D6 del mismo día (rama `sync-incremental`, sin commitear)
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

Verificado el 2026-11-19 en la rama `sync-incremental` (`d455dbd`). Todavía no hay código de C02 ni nada de Kafka.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| Sesión técnica (X01) | `catalogo/catedra/domain/model/TechnicalIntegration.java`, `TechnicalSession.java`; `catedra/domain/ports/in/GetTechnicalSessionUseCase.java`, `RenewTechnicalSessionUseCase.java` | La integración ya trae `kafkaBootstrapServers`, `kafkaConsumerGroupId` y `kafkaCatalogTopic`, y la sesión no queda habilitada si falta alguno (`missingData`). Su `toString` oculta la contraseña de Redis |
| Login del arranque y renovación | `catedra/infrastructure/startup/TechnicalSessionStartup.java`, `catedra/application/usecases/StartTechnicalSessionUseCaseImpl.java`, `RenewTechnicalSessionUseCaseImpl.java` | Guardan una sesión habilitada con `TechnicalSessionRepository.save`. Hoy nadie se entera cuando eso pasa: el aviso para arrancar el consumo (RF-01, D12.1) es nuevo |
| Lecturas de Redis (C03) | `catedra/domain/ports/in/GetCatalogMetadataUseCase.java`, `GetCatalogVersionChangesUseCase.java`; `catedra/application/support/CatalogRedisReader.java` | Las usa el procedimiento común sin cambios (RF-05) |
| La verificación (C03) | `synchronization/application/usecases/VerifyCatalogVersionUseCaseImpl.java` (240 líneas) y su test (19 tests) | Espera el login inicial, toma el candado sin esperar y, adentro, el procedimiento para ponerse al día con Redis en métodos privados (P9 de C03). De los 19 tests, 2 son propios de C03 (omitirse y esperar el login) y 17 prueban el procedimiento (P2) |
| Sincronización completa y candado | `synchronization/application/usecases/SynchronizeFullCatalogUseCaseImpl.java`, `synchronization/application/support/SynchronizationLock.java` | El candado es reentrante y solo tiene `tryAcquire` (no espera). C02 necesita esperar (RF-04, D5) |
| Service y disparador de C03 | `synchronization/application/service/SynchronizationService.java`, `synchronization/infrastructure/scheduling/` | Entrada de la verificación; sin cambios previstos |
| Copia local y estado | `catalog/domain/model/CatalogSyncState.java`, `catalog/domain/ports/in/*`, `src/main/resources/db/migration/V1__create_catalog.sql` | Los puertos que usa el procedimiento. No hay tabla de avisos procesados (P3) |
| Dependencias de Kafka | `pom.xml`: `spring-boot-starter-kafka` (spring-kafka 4.1.2, kafka-clients 4.2.2), `spring-boot-starter-kafka-test` y `testcontainers-kafka` 2.0.5 | Incluidas desde el inicio, sin uso. Sin configuración de Kafka en `application.yaml` |
| Tests | `catedra/FakeCatedraServer.java`, `src/test/resources/catedra/login-200.json`, `catedra/TechnicalIntegrations.java`, `TestcontainersConfiguration.java`, `e2e/` | La cátedra simulada informa `kafka.catedra.internal:9092`, que no existe: los tests que levantan la aplicación tendrán que apagar el consumo. No hay Kafka en contenedor |
| Turnos, como referencia | `turnos_service/src/main/java/turnos/catedra/infrastructure/kafka/` (7 clases, unas 700 líneas), `catedra/domain/ports/out/TechnicalIntegrationListener.java`, `CatedraEventHandler.java`; `turnos_service/docs/decisiones/014-kafka-con-la-catedra.md` | Kafka en `catedra/infrastructure/kafka`, consumo armado por programa con el primer login exitoso, y entrega al otro slice por un puerto de salida de `catedra`. Es una opción más, no la respuesta por defecto |

**Convenciones y slice de referencia:** `catedra` (X01, C01 y C03) para lo que toca a la cátedra; `catalog` y `synchronization` (C01 y C03) para la copia local y el proceso. Fachada en `synchronization` (P1 de C03). La skill `hexagonal-vertical-slicing` se aplica tal como está; cada excepción se analiza en el contexto de C02.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de la solicitud, el evento o la tarea desde que entra hasta la
respuesta, la persistencia o el mensaje publicado. Referencia el diagrama de
secuencia del proyecto si ya lo describe; usa un diagrama nuevo solo si aporta
claridad. -->

Sigue la secuencia §2 y la separación de C01 y C03: `catedra` habla con la cátedra (ahora también por Kafka), `catalog` guarda la copia, y `synchronization` decide. El aviso cruza de `catedra` a `synchronization` por un puerto de salida de `catedra` (P1, decisión 004), y el procedimiento para ponerse al día sale de C03 a una pieza común (P2).

**Arranque del consumo:**

```
login del arranque o renovación (X01) guarda una sesión habilitada
└─ TechnicalIntegrationListener.integrationEnabled(integración)        catedra, puerto de salida (P5)
   = KafkaCatalogConsumer                                              catedra/infrastructure/kafka (P4, P8)
     ├─ primera vez: arma el contenedor con el topic, los servidores y el grupo {kafkaConsumerGroupId}-catalog (D4), y lo arranca
     └─ ya consume con otros datos: warning y sigue con los primeros (D12.2)
```

**Un aviso:**

```
Kafka (topic del catálogo), de a un aviso (P8)
└─ CatalogUpdateListener                                               catedra/infrastructure/kafka
   ├─ CatalogUpdateReader: DTO con los campos como texto, valida D6     (P7; RF-02)
   │    inválido → warning recortado (D8) y termina: el contenedor confirma el offset
   └─ CatalogUpdateService → ReceiveCatalogUpdateUseCase              catedra/application
      └─ CatalogUpdateHandler (puerto de salida de catedra)            (P1)
         = CatalogUpdateHandlerAdapter                                 synchronization/infrastructure/gateway
           └─ SynchronizationService → SynchronizeCatalogIncrementallyUseCaseImpl   synchronization/application
              ├─ ¿eventId registrado? → "ya procesado", termina         ProcessedEventRepository (P3; RF-03)
              ├─ espera el candado, interrumpible                       SynchronizationLock (P6; RF-04)
              ├─ CatalogCatchUp: el procedimiento común con C03         synchronization/application/support (P2; RF-05)
              ├─ terminó bien → registra el eventId (un intento)        (D9.1, D9.2; RF-06)
              └─ fracasó → solo el log; el fracaso ya lo registró la pieza (RF-07)
   El oyente atrapa cualquier error, lo deja en el log y termina: el contenedor confirma el offset (D9.3; RF-07)
```

**La verificación (C03) después del cambio:** esperar el login inicial, `tryAcquire` u omitirse, `CatalogCatchUp`, liberar, y traducir `CatchUpResult` a `VerificationResult`. Mismo comportamiento que hoy.

## Slices y componentes afectados

<!-- Aplica la skill hexagonal-vertical-slicing: un paquete por slice, con sus
capas domain, application e infrastructure. Identifica los slices afectados, sus
responsabilidades y la dirección de sus dependencias. Una funcionalidad puede
afectar más de un slice. Justifica cualquier slice nuevo o dependencia nueva
entre slices. -->

| Slice | Existe / nuevo | Responsabilidad y cambios | Dependencias con otros slices |
| --- | --- | --- | --- |
| `catedra` | Existente | Suma el consumo de Kafka, la lectura y validación del aviso, el aviso de sesión habilitada (X01) y el puerto por el que entrega el aviso | Ninguna. `synchronization` implementa su puerto `CatalogUpdateHandler` (P1, decisión 004) |
| `catalog` | Existente | Sin cambios | Ninguna |
| `synchronization` | Existente | Suma C02, la pieza común con C03, la espera del candado y su primera persistencia: los avisos procesados (P3) | Usa `catedra` y `catalog`; implementa el puerto `CatalogUpdateHandler` de `catedra` (P1) |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son
propuestas. Señala impacto sobre modelos, contratos o componentes compartidos. -->

Rutas bajo `src/main/java/catalogo/`; las nuevas son propuestas.

| Slice y capa | Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- | --- |
| catedra / domain | `model/CatalogUpdateNotice` | Crear | El aviso ya validado: `eventId`, `newVersion`, `occurredAt` y `source` | RF-02 |
| catedra / domain | `ports/out/TechnicalIntegrationListener` | Crear | "La integración se habilitó", con la integración (P5) | RF-01 |
| catedra / domain | `ports/out/CatalogUpdateHandler` | Crear | Quien procesa los avisos; no devuelve nada, porque el offset se confirma siempre (P1) | RF-01 a RF-07 |
| catedra / domain | `ports/in/ReceiveCatalogUpdateUseCase` | Crear | Recibir un aviso válido | RF-02 |
| catedra / application | `usecases/ReceiveCatalogUpdateUseCaseImpl`, `service/CatalogUpdateService` | Crear | Entregan el aviso al puerto de salida. Todo lo que entra a un slice desde afuera entra por su service | RF-02 |
| catedra / application | `usecases/StartTechnicalSessionUseCaseImpl`, `RenewTechnicalSessionUseCaseImpl` | Modificar | Después de guardar una sesión habilitada, avisan al puerto (P5). Sin otro cambio de X01 | RF-01 |
| catedra / infrastructure | `kafka/KafkaCatalogConsumer` | Crear | Implementa `TechnicalIntegrationListener`: arma y arranca el contenedor con la primera integración, conserva sus datos y deja el warning de D12.2; lo detiene al apagar (P4, P8) | RF-01 |
| catedra / infrastructure | `kafka/CatalogUpdateListener`, `kafka/CatalogUpdateReader`, `kafka/dto/CatalogUpdatedMessage` | Crear | Recibe cada mensaje, lo lee y valida (D6, P7), recorta lo que va al log, entra por el service y atrapa todo error | RF-02, RF-07, RF-08 |
| catedra / infrastructure | `kafka/CatedraKafkaProperties` | Crear | `catalogo.catedra.kafka`: `enabled`, `max-poll-interval` y el consumer group de los E2E, que no está en el yaml (P8, P9.2) | RF-01 |
| synchronization / domain | `model/CatchUpResult`, `model/IncrementalSyncResult` | Crear | Resultado de la pieza común (3 casos) y de C02 (los 3 más "ya procesado") (P2.1) | RF-05 |
| synchronization / domain | `model/ProcessedEvent`, `ports/out/ProcessedEventRepository` | Crear | El aviso procesado (clase Lombok) y su repositorio: saber si existe y registrarlo (P3) | RF-03, RF-06 |
| synchronization / domain | `ports/in/SynchronizeCatalogIncrementallyUseCase` | Crear | C02; recibe el `eventId` y la `newVersion` como valores simples, para que el puerto no dependa del modelo de `catedra` (P1) | RF-03 a RF-08 |
| synchronization / application | `support/CatalogCatchUp` | Crear | El procedimiento común, movido de `VerifyCatalogVersionUseCaseImpl` sin cambiar su comportamiento; quien llama da el nombre que encabeza el log (P2, ajuste de TASK-01) | RF-05 |
| synchronization / application | `support/SynchronizationLock` | Modificar | Suma la espera interrumpible (P6) | RF-04 |
| synchronization / application | `usecases/SynchronizeCatalogIncrementallyUseCaseImpl` | Crear | C02: `eventId`, candado, pieza común, registro del `eventId`, fallas de la base fuera de una versión (D9) y log | RF-03 a RF-08 |
| synchronization / application | `usecases/VerifyCatalogVersionUseCaseImpl` | Modificar | Usa `CatalogCatchUp` y traduce su resultado; conserva la espera del login y omitirse | RF-05 |
| synchronization / application | `service/SynchronizationService` | Modificar | Expone C02 | RF-01 |
| synchronization / infrastructure | `gateway/CatalogUpdateHandlerAdapter` | Crear | Implementa `CatalogUpdateHandler` de `catedra` y llama a `SynchronizationService` (P1) | RF-01 |
| synchronization / infrastructure | `persistence/entity/ProcessedEventEntity`, `repository/JpaProcessedEventRepository`, `mapper/ProcessedEventMapper`, `adapter/JpaProcessedEventRepositoryAdapter` | Crear | Persistencia de los avisos procesados (P3) | RF-03, RF-06 |
| Recursos | `db/migration/V2__create_processed_events.sql` | Crear | Tabla `processed_events`: `event_id` (`VARCHAR(255)`, clave) y `processed_at` (P3) | RF-06 |
| Recursos | `application.yaml`, `.env.example` | Modificar | `CATALOGO_CATEDRA_KAFKA_ENABLED` y `CATALOGO_CATEDRA_KAFKA_MAX_POLL_INTERVAL` (P8) | RF-01 |
| Proyecto | `pom.xml` | Modificar | Surefire apaga el consumo en todos los tests, también con `-Pe2e` (P8, P9) | — |
| Tests | Kafka en contenedor, login simulado con ese Kafka, publicación de avisos, tests movidos de C03 a la pieza, tests nuevos de C02, del lector, del candado y de X01 con el aviso | Crear / modificar | P9.1 | CA-01 a CA-13 |
| Tests | `src/test/java/e2e/` (C02 contra la cátedra real) | Crear | P9.2 | CA-14 |
| Proyecto | `CLAUDE.md`, `docs/decisiones/003-…` | Modificar | Slices, excepciones de C02, propiedades nuevas, cómo usar Kafka y lo comprobado del topic; en 003, las filas de C02 | `AGENTS.md`, "Código" |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Operaciones, eventos y DTO de entrada y salida:** `CatalogUpdated` según el contrato §4.4 y el anexo §15.3, solo lectura; el DTO del mensaje tiene los seis campos como texto (P7). Redis, como en C03. Los contratos ya se ajustaron en la SPEC (D4). C02 no expone operaciones.
- **Identificadores, relaciones y restricciones:** el `eventId` es la clave de `processed_events`, de 255 caracteres como máximo, sin caracteres de control (D6). Sin relación con otras tablas.
- **Origen de los datos devueltos o publicados y transformaciones:** el aviso solo dispara el proceso; los datos salen de Redis o del snapshot, como en C03.
- **Persistencia, consultas y migraciones:** `V2__create_processed_events.sql` (P3). La pieza común usa los mismos puertos de `catalog` que C03.
- **Convivencia entre datos locales y remotos:** como en C03.
- **Compatibilidad y migraciones de datos existentes:** la migración solo crea una tabla vacía; no toca las de C01.

## Transacciones, concurrencia y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de BACKEND_GUIDELINES.md. -->

- **Estados y transiciones:** los de `CatalogSyncState`, sin cambios (decisión 003). `ProcessedEvent` no tiene estados: existe o no.
- **Transacciones y consistencia:** cada versión en su transacción, dentro de la pieza común, como en C03. El `eventId` en una escritura propia, después de que la pieza terminó bien (D9.1). El offset lo confirma el contenedor cuando el oyente termina, siempre después de esas escrituras (RF-06).
- **Idempotencia y mensajes duplicados:** el `eventId` registrado corta un repetido antes del candado (RF-03); si no estaba registrado, la comparación de versiones deja la copia igual (RF-05).
- **Concurrencia y tareas programadas:** un solo consumidor y un aviso por lectura (P8), así que los avisos se procesan de a uno. C02 espera el candado (P6); C03 se sigue omitiendo con `tryAcquire`. El contenedor tiene su propio hilo: no comparte el de las tareas programadas.
- **Timeouts, reintentos y errores de dependencias:** Redis, el snapshot y la base, como en C03. Kafka no disponible: lo reintenta el cliente, con hasta 30 s entre reconexiones (P8; D11). El tiempo máximo entre lecturas, 10 minutos y configurable, cubre el peor caso de un aviso (P8).
- **Seguridad y autorización:** sin endpoints. Kafka sin credenciales (Fuera de alcance de la SPEC). La validación de D6 y el log recortado cubren un aviso falso (P7). Ni el JWT ni la contraseña de Redis salen de `catedra` ni aparecen en logs.
- **Otras consideraciones de backend aplicables y su solución:**
  - Arranque: el servicio arranca sin Kafka; el consumo empieza con el aviso de sesión habilitada (P5).
  - Apagado (ver P6): el contenedor se detiene con el servicio y deja terminar el aviso en curso. Si C02 está esperando el candado, sigue cuando la otra sincronización lo suelta (al apagar, Spring interrumpe la verificación), y termina o falla; el offset se confirma igual, y un aviso que quedó sin aplicar lo cubre la verificación del arranque siguiente (P6, ajuste de la tanda 6).
  - Logs: el aviso con su `eventId` y `newVersion` y el resultado, en `synchronization`; el aviso inválido, recortado, en `catedra` (RF-08, P7).

## Dependencias y configuración

<!-- Librerías, servicios externos, propiedades de configuración y secretos
afectados. Verifica compatibilidad con el proyecto y justifica las incorporaciones.
No agregues dependencias por defecto. Los valores sensibles y los parámetros
ajustables se externalizan. -->

- **Dependencias:** ninguna nueva. spring-kafka 4.1.2 y kafka-clients 4.2.2 (por `spring-boot-starter-kafka`), y `testcontainers-kafka` 2.0.5 para los tests, ya están en el `pom.xml`. La imagen `apache/kafka:4.2.1` ya está en el Docker local (la usa turnos).
- **Propiedades nuevas (P8):** `catalogo.catedra.kafka.enabled` (`CATALOGO_CATEDRA_KAFKA_ENABLED`, `true`) y `catalogo.catedra.kafka.max-poll-interval` (`CATALOGO_CATEDRA_KAFKA_MAX_POLL_INTERVAL`, `10m`), con su variable en el yaml, el `.env.example` y la tabla del `CLAUDE.md`. Fijos en el código: `latest`, sin confirmación automática, un aviso por lectura y 30 s como máximo entre reconexiones. `catalogo.catedra.kafka.consumer-group` existe solo para los E2E y no está en el yaml (P9.2).
- **Secretos:** ninguno nuevo. Los datos de Kafka llegan con la sesión técnica y no llevan credenciales.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, de integración (base de datos, mensajería), de API o prueba manual.
No todos requieren todos los métodos. Identifica tests existentes y separa los
nuevos propuestos. Incluye regresiones relevantes. Una prueba con mocks no
demuestra persistencia, transacciones ni idempotencia. -->

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Integración con la aplicación completa, Kafka en contenedor y la cátedra simulada informando ese Kafka | Un aviso publicado antes y otro después de que el consumo empieza | Grupo `{kafkaConsumerGroupId}-catalog` en el Kafka de test; solo el posterior procesado |
| CA-02 | Unitarios de `StartTechnicalSessionUseCaseImpl` y `RenewTechnicalSessionUseCaseImpl` con un doble del aviso; unitario de `KafkaCatalogConsumer` con dos integraciones distintas; integración con un login del arranque degradado y una renovación exitosa | `ScriptedLoginClient`; cátedra simulada que falla y después responde | Aviso solo con sesión habilitada; consumo que arranca con la renovación; warning con datos distintos |
| CA-03 | Integración con el contenedor de Kafka pausado al arrancar y reanudado después | Avisos publicados mientras estaba pausado | El servicio atiende; estado sin cambios; avisos procesados al reanudar; cantidad de warnings de reconexión acotada |
| CA-04, CA-05 | Unitarios de `CatalogUpdateReader`; integración que publica un inválido seguido de uno válido | Un mensaje por cada defecto y cada variante (instante con nanosegundos, campos desconocidos, `source` fuera de los ejemplos) | Inválido con su motivo, sin lecturas de Redis ni escrituras; el válido procesado |
| CA-06 | Unitario de C02 con dobles; integración que publica el mismo aviso dos veces | Kafka en contenedor y Redis en contenedor | La segunda entrega no lee Redis ni escribe; offset confirmado |
| CA-07 | Unitario del candado con dos hilos; unitario de C02 con el candado tomado por otro hilo; integración con una verificación en curso | Una versión publicada mientras C02 espera | C02 espera, después lee de nuevo y aplica; C03 omitida mientras C02 trabaja; espera interrumpible |
| CA-08 | Los 17 tests del procedimiento, movidos de `VerifyCatalogVersionUseCaseImplTest` al test de `CatalogCatchUp`; los tests de integración de C03 sin cambios, como regresión; un test de C02 que muestra que usa la pieza | Dobles como los de C03; Redis y base en contenedor | Mismos resultados que en C03 |
| CA-09 | Unitario de C02; integración contra la base real, con una falla provocada al registrar el `eventId` (disparador de PostgreSQL, como en C01) | Avisos que aplican versiones, que hacen una completa y que encuentran el catálogo al día | `eventId` en `processed_events`; con la falla, error en el log y estado sin cambios |
| CA-10 | Integración: se aplica la versión, se borra el `eventId` registrado para simular el corte, y se vuelve a publicar el aviso | Kafka, Redis y base en contenedor | Al día, `eventId` registrado, sin cambios repetidos |
| CA-11 | Unitario de C02 con la pieza devolviendo cada fracaso; integración con Redis caído | Dobles; puerto de Redis cerrado | `eventId` no registrado; el siguiente aviso procesado |
| CA-12 | Unitario de C02 con el repositorio de avisos y la lectura del estado fallando | Dobles | Error en el log; estado sin cambios |
| CA-13 | Captura de la salida en los tests de integración, con un aviso inválido que trae saltos de línea y un texto largo | Los escenarios de integración | Avisos y resultados en el log; recortes de 100 caracteres con `?`; sin el JWT ni la contraseña de Redis de test |
| CA-14 | E2E (`@Tag("e2e")`), corrido por el usuario con `-Pe2e` (P9.2) | Cátedra real (API, Redis y Kafka), `.env` local, `CATALOGO_E2E_KAFKA_CONSUMER_GROUP` terminada en `-e2e`, base descartable | Versión local en `currentVersion` y `eventId` registrado con el último aviso real; la segunda entrega, sin cambios |

**Comprobaciones de regresión:** la suite completa (394 tests al cerrar C03), con los tests de integración de C03 sin cambios como prueba de que la verificación se comporta igual, y los cinco chequeos de la skill (§12).

**Comandos verificados para compilar y ejecutar tests:** los del `CLAUDE.md`, ejecutados en C03.

**Pruebas con infraestructura real o simulada:** PostgreSQL, Redis y Kafka reales en contenedores de Testcontainers; la API de la cátedra, simulada con `FakeCatedraServer`; la cátedra real (API, Redis y Kafka) solo en el E2E, que corre el usuario después de ver la lista de pedidos.

**Limitaciones del entorno:** la cátedra publica solo cambios de profesionales y un aviso por hora; el E2E usa el último aviso retenido (P9.2).

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

Cada etapa deja la suite en verde.

1. **Sacar la pieza común (P2, P2.1):** `CatalogCatchUp` y `CatchUpResult`; C03 la usa; los 17 tests pasan a la pieza. Sin cambio de comportamiento: los tests de integración de C03 en verde (CA-08 en su parte de C03).
2. **El candado que espera (P6):** con su test de dos hilos y la interrupción (CA-07 en su parte de candado).
3. **Avisos procesados en `synchronization` (P3):** migración, modelo, puerto y persistencia, contra la base real (CA-09 en su parte de base).
4. **C02 en `synchronization`:** caso de uso, resultado y service, con dobles (CA-06 a CA-12 en sus partes unitarias).
5. **El aviso en `catedra` (P7):** modelo, lector y DTO, con el recorte del log (CA-04, CA-05, CA-13 en su parte unitaria).
6. **El cruce y el aviso de sesión (P1, P5):** `CatalogUpdateHandler`, `ReceiveCatalogUpdateUseCase` y su service, el adaptador de `synchronization`, y `TechnicalIntegrationListener` en los dos casos de uso del login (CA-02 en su parte unitaria).
7. **El consumo (P4, P8):** propiedades, `KafkaCatalogConsumer`, el oyente, el yaml, el `.env.example` y Surefire. Aquí se comprueba que el servicio arranca sin intentar conectarse a `localhost:9092`.
8. **Integración con Kafka en contenedor (P9.1):** CA-01 a CA-03, CA-06, CA-07, CA-09 a CA-11 y CA-13.
9. **E2E (P9.2):** lo corre el usuario (CA-14).
10. **Cierre:** regresión, chequeos de la skill, `CLAUDE.md`, filas de C02 en `docs/decisiones/003`, y con el usuario, el "por verificar" de la decisión 014 de turnos.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - **Dependencia circular entre beans al arrancar.** El login avisa a `KafkaCatalogConsumer`, que necesita el oyente; el oyente llega, por `catedra` y el adaptador, hasta C02; C02 lee Redis con `CatalogRedisReader`, que usa la renovación de la sesión, que es la que avisa. Spring no puede crear esos objetos en orden. Medida: `KafkaCatalogConsumer` pide el oyente a Spring recién al armar el contenedor (`ObjectProvider`), como turnos (decisión 014, §3.8). Es un ciclo entre objetos al arrancar, no entre slices ni capas. Se comprueba al levantar la aplicación en la etapa 7.
  - **Piezas por defecto del starter de Kafka.** Pueden intentar conectarse a `localhost:9092` al arrancar, como hacía el starter de Redis en C03. Se comprueba en la etapa 7 (P4).
  - **Mover el procedimiento de C03.** Un error al moverlo cambiaría C03 sin querer. Medida: la etapa 1 va sola, sin cambio de comportamiento, con los tests de integración de C03 sin tocar como red.
  - **El tiempo máximo entre lecturas.** Si alguien sube los reintentos del snapshot o de Redis, un aviso podría pasar los 10 minutos y Kafka sacaría al consumidor del grupo; el aviso se reentregaría sin daño (D9.1), y la variable permite subirlo (P8).
- **Decisiones pendientes:**
  - Ninguna.
- **Decisiones tomadas:**
  - **Aprobación:** el usuario aprobó el PLAN el 2026-11-19, con P1 a P10 y la lista de excepciones de P10.
  - **P10 · Excepciones a la skill (2026-11-19), autorizadas por el usuario para C02**, cada una analizada en el contexto de C02:
    1. Paquete `synchronization/infrastructure/gateway`, y en él un adaptador que implementa un puerto de salida de `catedra` (`CatalogUpdateHandlerAdapter`), importando de `catedra` solo su dominio (P1, decisión 004).
    2. Paquete `catedra/infrastructure/kafka` (y su `dto`), para todo lo de Kafka (P1, P4).
    3. Puertos de salida que no son repositorios: `TechnicalIntegrationListener` (P5) y `CatalogUpdateHandler` (P1), como `TechnicalLoginClient` en X01.
    4. `record` y resultados sellados: `CatalogUpdateNotice` en el dominio de `catedra`, como los demás modelos de lo que publica la cátedra; `CatchUpResult` e `IncrementalSyncResult` en el dominio de `synchronization`, como `FullSyncResult` y `VerificationResult`; y lo que devuelve el lector del aviso, dentro de `catedra/infrastructure/kafka`. `ProcessedEvent` sigue la skill (clase Lombok).
    5. La clave de `processed_events` es el `eventId` de la cátedra (`String`), no un `Long` generado: `ProcessedEventEntity` sin `@GeneratedValue` y `JpaProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String>` (P3), como las entidades de `catalog` con los `id` de la cátedra (C01).
    6. `CatalogCatchUp` en `synchronization/application/support`, dentro de la excepción de C01 para ese paquete (P2).

    No son excepciones: la persistencia propia de `synchronization` (molde de la skill), el segundo service de `catedra` (`CatalogUpdateService`, uno por concepto) y el `ObjectProvider` contra el ciclo de beans (una técnica de Spring, no una estructura). Como pide el usuario, el código no lleva comentarios que marquen excepciones: se listan en el `CLAUDE.md`.
  - **P1 · Dónde vive Kafka y cómo llega el aviso a `synchronization` (2026-11-19): opción a), Kafka en `catedra/infrastructure/kafka` y un puerto de salida de `catedra` que implementa `synchronization`.** Texto completo, con el porqué y las alternativas: `docs/decisiones/004-aviso-de-kafka-hacia-synchronization.md` (pedido por el usuario). En resumen: el aviso nace en `catedra` y tiene que llegar a `synchronization`, contra la flecha de las dependencias; `catedra` declara la interfaz "quien procesa los avisos" y un adaptador en `synchronization/infrastructure/gateway` la cumple llamando a `SynchronizationService`. Todo lo de la cátedra (datos de conexión, formato del aviso, validación y el aviso de sesión habilitada) queda en `catedra`; `synchronization` importa solo la interfaz y el modelo del aviso, del dominio de `catedra`. Si algo queda mal conectado, la aplicación no arranca o no compila. Es lo que hizo turnos (decisión 014, §3.7). Excepción a la skill autorizada por el usuario: el adaptador que implementa un puerto de otro slice y el paquete `infrastructure/gateway` (P10). El usuario no está del todo conforme con ninguna opción y eligió esta como la mejor. Alternativas descartadas: b), Kafka en `synchronization`, que sacaba de `catedra` el formato del aviso y los datos de conexión, y necesitaba igual el cruce al revés para arrancar el consumo; c), eventos de Spring, con un vínculo invisible que falla en silencio si nadie escucha; d), que `synchronization` pida los avisos, sin excepción pero con una cola y un canal de vuelta para confirmar el offset después de procesar.
  - **P2 · El procedimiento común con C03 (2026-11-19): opción a), una pieza de soporte en `synchronization/application/support/`.** Lo propuso el usuario al ver que C02 hace lo mismo que una parte de C03. El procedimiento para ponerse al día con Redis sale de los métodos privados de `VerifyCatalogVersionUseCaseImpl` (unas 170 de sus 240 líneas) y pasa a una pieza propia, que usan los dos casos de uso: base vacía con una sola completa, metadata y comparación, versiones con sus reintentos, discontinuidad, y registro del fracaso o del catálogo al día. C03 queda con esperar el login, tomar el candado u omitirse, la pieza y liberar; C02, con esperar el candado, la pieza y liberar. Es lo que se hizo en C03 con `CatalogRedisReader` (P14): lo común de dos casos de uso, en `support`, sin ofrecerlo a otros slices; y cumple P9 de C03, que dejaba la pieza común para cuando la necesitara una segunda operación. Los tests: los 17 del procedimiento pasan a la pieza; C03 conserva los 2 suyos y suma uno que muestra que la usa; los tests de integración de C03 quedan como prueba de que C03 no cambió. Costo aceptado: mover código y tests. **Ajuste del 2026-11-19, al implementar TASK-01 (aprobado por el usuario, sin cambio de comportamiento):** en lugar de textos del log neutros, quien llama le da a la pieza un nombre que encabeza cada línea ("Catalog verification" en C03; C02 pasará el suyo con el `eventId`). Así los textos de C03 quedan idénticos, sus tests de integración y su E2E no se tocan, y el log dice quién disparó cada recorrido. Lo planeado antes obligaba a cambiar el texto esperado en cuatro tests de integración y en el E2E de C03, y una línea ya no decía quién la disparó. Alternativas descartadas: b), un puerto de entrada nuevo en `synchronization`, que se aparta de la skill (un caso de uso depende de puertos de salida, §2.4; la fachada usa puertos de entrada de otros slices) y publicaba un contrato que solo funciona con el candado tomado; c), que C02 llame a la verificación entera, que ataba C02 a todo C03, con sus textos, su `Skipped` y su regla de omitirse.
  - **P2.1 · Los resultados y los nombres (2026-11-19): opción a), un resultado propio para cada uno.** Criterio del usuario: cada pieza con su resultado, salvo que sean exactamente iguales, y no lo son. La pieza común se llama `CatalogCatchUp` (elegido por el usuario, de "ponerse al día") y devuelve `CatchUpResult` con tres casos: al día, actualizado (versiones aplicadas o una completa) y fallido con su motivo. C03 conserva su `VerificationResult` sin cambios: traduce los tres casos y suma "omitida"; su puerto, sus tests y su disparador no cambian por esto. C02 tiene su propio resultado, `IncrementalSyncResult`, que traduce los tres casos y suma "ya procesado"; su puerto de entrada sería `SynchronizeCatalogIncrementallyUseCase`, en paralelo a `SynchronizeFullCatalogUseCase` y `FullSyncResult` de C01. Dentro de la pieza, una completa "descartada" no puede ocurrir, porque quien la llama ya tiene el candado y el candado es reentrante; como Java obliga a tratar ese caso, se trata como fallido con un error en el log, sin un cuarto caso. Alternativas descartadas: b), devolver `VerificationResult` desde la pieza y en C02, con un `Skipped` que nunca producen y un nombre de verificación para un aviso; c), un solo tipo neutro con los cinco casos, donde "omitida" y "ya procesado" serían imposibles en dos de sus tres usuarios. Nombres considerados para la pieza: `CatalogVersionAlignment` (segunda opción), `CatalogSynchronizer` (se confunde con `SynchronizationService` y con la completa), `IncrementalSynchronizer` (engaña, porque también hace la completa) y `CatalogReconciliation` (en turnos, "reconciliación" es T13).
  - **P3 · Dónde se guardan los avisos procesados (2026-11-19): opción b), en `synchronization`, en la base.** "Aviso procesado" significa "C02 terminó bien", un concepto del proceso y no de la copia; el dominio lo dibuja en la sección "Sincronización". `synchronization` suma su primera persistencia, con el molde de la skill: el modelo `ProcessedEvent` (clase Lombok), el puerto de salida `ProcessedEventRepository`, y en `infrastructure/persistence` la entidad, el repositorio de Spring Data, el mapper y el adaptador; la tabla `processed_events`, con el `eventId` como clave, en una migración nueva (`V2`). El caso de uso de C02 usa su propio puerto de salida, sin puertos de entrada extra ni excepciones. Se guarda en la base y no en memoria porque el repetido típico llega justo después de un reinicio (el servicio procesa el aviso y se corta antes de confirmar el offset), y la SPEC ya lo da por persistido (D9.2, D13). Costo aceptado: `synchronization` deja de ser solo una fachada y tiene una tabla propia. Las razones de la decisión 002 para poner el estado en `catalog` (la versión cambia en la misma transacción que los datos, y las consultas la leen) no valen para `ProcessedEvent`, que se escribe aparte (D9.1) y no lo lee ninguna consulta. Alternativas descartadas: a), en `catalog`, que sumaba dos puertos de entrada con sus implementaciones solo para cruzar desde la fachada; c), en `catedra`, que le daba una base y una regla del proceso, y dejaba los pasos 3 y 9 de C02 en otro slice.
  - **P4 · Librería y forma de consumir (2026-11-19): opción a), spring-kafka con un contenedor armado por programa.** `KafkaMessageListenerContainer` de spring-kafka 4.1.2 (ya en el proyecto por `spring-boot-starter-kafka`), creado cuando hay sesión, con los datos de la integración, porque llegan con el login y no al arrancar. La librería resuelve el bucle de lectura en su propio hilo, arrancar y detener, los reacomodos del grupo y la confirmación del offset de cada aviso al terminar. Más simple que en turnos: como siempre se confirma (D9.3), no hacen falta reintentos sin fin ni pausa entre entregas; el oyente atrapa todo error, lo deja en el log y termina, y el contenedor confirma. Turnos lo eligió por lo mismo (decisión 014, §3.8). Riesgo a comprobar en las tareas: que las piezas que crea el starter de Kafka no intenten conectarse a `localhost:9092` al arrancar (en C03, el starter de Redis sí lo hacía, P3.2 de C03). Alternativas descartadas: b), `KafkaConsumer` directo con un bucle propio, que obligaba a escribir y probar a mano el bucle, la confirmación, el apagado y la salida del grupo, lo más delicado de Kafka; c), `@KafkaListener` con propiedades de Spring Boot, imposible con datos que llegan después de arrancar.
  - **P5 · Cómo se entera el consumo de que la sesión se habilitó (2026-11-19): opción a), un puerto de salida en `catedra`.** Los dos casos de uso del login (`StartTechnicalSessionUseCaseImpl` y `RenewTechnicalSessionUseCaseImpl`) avisan "la integración se habilitó" después de guardar una sesión habilitada; lo implementa la pieza de Kafka, que arranca el consumo o, si ya consume, compara los datos y deja el warning de D12.2. Queda dentro de `catedra`, sin cruzar slices, como turnos (decisión 014, §3.9). Costo aceptado: se tocan los dos casos de uso de X01 y sus tests, con un doble del puerto; la regla va en los dos, como se acordó en X01 (su P3). El usuario preguntó si no lo resolvía ya `AwaitInitialTechnicalSessionUseCase` (C03): no, porque ese puerto espera solo el login del arranque y alguien tiene que llamarlo; si el servicio arranca degradado y una renovación posterior habilita la sesión, nadie se entera, y D12.1 pide consumir también en ese caso. Cubrirlo con esa espera obligaba a volver a preguntar cada tanto, que es la opción c). Alternativas descartadas: b), que el repositorio de la sesión avise al guardar, que escondía el arranque del consumo dentro de un `save`; c), una tarea que revise la sesión cada pocos segundos, con una demora, su configuración, y el único hilo de tareas compartido con la verificación.
  - **P6 · Esperar el candado (2026-11-19): opción a), sin tope e interrumpible.** `SynchronizationLock` suma una espera hasta tomarlo con `lockInterruptibly`, sin tope propio (D5). Si algo interrumpe el hilo mientras espera, la espera se corta y C02 termina sin procesar. **Ajuste del 2026-11-20, al implementar TASK-08 (aprobado por el usuario):** el texto original decía que, al apagar el servicio, el contenedor de Kafka interrumpe el hilo de C02, el offset no se confirma y Kafka vuelve a entregar el aviso al arrancar. Al revisar spring-kafka se vio que, al detenerse, no interrumpe ese hilo: lo deja terminar. Lo que pasa al apagar es que Spring interrumpe la verificación de C03, que suelta el candado, y C02 sigue y termina el aviso o falla; el offset se confirma igual. Si ese aviso quedó sin aplicar, lo cubre la verificación del arranque siguiente. El usuario eligió aceptarlo sin código extra; se descartó forzar la reentrega (interrumpir el hilo al apagar y un manejador de errores propio que no confirme), más código y tests de apagado para un aviso que igual cubre C03. La espera interrumpible se conserva: no cuesta nada y no traba un hilo que alguien interrumpa. Mientras C02 espera, una verificación de C03 encuentra el candado ocupado y se omite (RF-02 de C03); al liberarse, lo toma C02. Alternativas descartadas: b), sin tope y sin interrupción (`lock`), que trababa el apagado hasta que terminara la otra sincronización, hasta 2 o 3 minutos; c), con un tope técnico (`tryLock` con tiempo), que contradice D5.
  - **P7 · Lectura y validación del aviso (2026-11-19).** En `catedra/infrastructure/kafka`, un lector toma el texto del mensaje y lo pasa a un DTO donde los campos llegan sin convertir; recién después valida cada uno con las reglas de D6. Un campo mal escrito no hace fallar la lectura entera, y el log conserva el `eventId` si se pudo leer (lección de turnos, P3 del PLAN de T08). Si es válido, lo convierte al modelo del aviso, en el dominio de `catedra`, que recibe `synchronization` (decisión 004); si es inválido, warning con el motivo, la posición y el `eventId`, y se confirma el offset (D8). Valores que D6 dejó para el PLAN, aprobados por el usuario: `source` de 64 caracteres como máximo (los reales tienen 9 y el ejemplo del anexo, 6), y todo texto del mensaje que vaya al log se recorta a 100 caracteres y cada carácter de control se reemplaza por `?`. Al planificar esto se vio que el `eventId` validado también se escribe en el log desde `synchronization` y que D6 solo prohibía el carácter nulo: con el OK del usuario se ajustó la SPEC (D6, opción a): el `eventId` no admite ningún carácter de control, y CA-04 suma ese defecto. Así, una vez validado, el `eventId` se puede escribir en cualquier log sin cuidado extra.
  - **P8 · Configuración del consumo (2026-11-19): opción c), dos valores configurables.** Bajo `catalogo.catedra.kafka`: `enabled` (`CATALOGO_CATEDRA_KAFKA_ENABLED`, `true`; Surefire la apaga en todos los tests, porque la cátedra simulada informa servidores inventados, y la encienden los tests de Kafka) y `max-poll-interval` (`CATALOGO_CATEDRA_KAFKA_MAX_POLL_INTERVAL`, `10m`), el tiempo máximo que Kafka espera entre dos lecturas antes de sacar al consumidor del grupo. Ese valor es el único que depende de otros tiempos configurables: el peor caso de un aviso es esperar el candado (2 a 3 minutos, D5) más su propio procedimiento con una completa y reintentos (otros 2 a 3). Fijos en el código, con su porqué en un comentario: `latest` (D10), sin confirmación automática (RF-06, RF-07), un aviso por lectura (`max.poll.records` 1; por defecto llegan hasta 500 y el tiempo máximo vale para todo el lote) y 30 s como máximo entre reconexiones (`reconnect.backoff.max.ms`; por defecto 1 s, unos 3600 warnings por hora con Kafka caído, D11). Alternativas descartadas: a), solo `enabled` configurable, que obligaba a tocar el código si subían los reintentos de C01 o C03; b), todos configurables, con variables para valores que no dependen de nada configurable.
  - **P9.1 · La suite normal (2026-11-19), aprobada por el usuario.** Kafka real en Testcontainers (`apache/kafka:4.2.1`), como Redis en C03: la cátedra simulada suma un login cuya integración apunta a ese Kafka, el test publica los avisos como la cátedra, y el contenedor se pausa para Kafka caído (CA-03). Unitarios del lector, de C02, de la pieza común (los 17 tests de C03) y del candado. El contenedor es lo único que prueba de verdad el consumo, la confirmación y la reentrega, y la guía de backend no acepta un doble para demostrar idempotencia. Turnos hizo lo mismo.
  - **P9.2 · El E2E con la cátedra real (2026-11-19): opción a), el test mueve la posición de su propio grupo.** Con un consumer group propio que termina en `-e2e` (`CATALOGO_E2E_KAFKA_CONSUMER_GROUP`, que la aplicación no lee; el test la pasa a `catalogo.catedra.kafka.consumer-group`, que no está en el yaml, como turnos), el test carga el snapshot, baja la versión local con SQL (como el E2E de C03), mueve la posición de ese grupo al último aviso retenido y arranca el consumo: el aviso real se procesa enseguida y aplica las versiones de forma incremental. Después vuelve a mover la posición al mismo aviso, y la segunda entrega no cambia nada: un repetido real (CA-06 y CA-14). Lo único que escribe en Kafka es la posición de un grupo de pruebas propio, sin efecto sobre nadie (criterio de turnos, decisión 014 §3.6). La lista exacta de pedidos se le pasa al usuario antes de correrlo. Alternativas descartadas: b), que el grupo del E2E lea desde el más antiguo, unos 200 avisos y 50 s, sin resolver la segunda entrega; c), esperar el próximo aviso real, hasta una hora por corrida, tampoco resuelve la segunda entrega.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados, respeta la skill hexagonal-vertical-slicing y permite
demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
