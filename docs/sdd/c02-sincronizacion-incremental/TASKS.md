# TASKS: C02 — Sincronizar catálogo incrementalmente

SPEC: `docs/sdd/c02-sincronizacion-incremental/SPEC.md` (aprobada el 2026-11-19, con el ajuste de D6 del mismo día). PLAN: `docs/sdd/c02-sincronizacion-incremental/PLAN.md` (aprobado el 2026-11-19). Decisión: `docs/decisiones/004-aviso-de-kafka-hacia-synchronization.md`. Tareas aprobadas por el usuario el 2026-11-19; la implementación sigue su autorización, tanda por tanda (tanda 1: TASK-01; tanda 2: TASK-02 y TASK-03; tanda 3: TASK-04, autorizadas el 2026-11-19; tanda 4: TASK-05, autorizada el 2026-11-20).

Las tareas siguen el "Orden de implementación" del PLAN y cada una deja la suite en verde. Toda tarea con tests incluye, después de que pasan, una prueba en negativo: romper el código a propósito, ver fallar el test, restaurar el archivo y comprobar el hash. Los tests se cuentan con los reportes XML (`target/surefire-reports/TEST-*.xml`); al empezar C02 hay 394. Ninguna tarea llama a la cátedra real, salvo TASK-14, que corre el usuario después de ver la lista de pedidos.

Tandas propuestas (cada una espera la orden del usuario): 1 = TASK-01; 2 = TASK-02 y TASK-03; 3 = TASK-04; 4 = TASK-05; 5 = TASK-06 y TASK-07; 6 = TASK-08; 7 = TASK-09 y TASK-10; 8 = TASK-11 y TASK-12; 9 = TASK-13; 10 = TASK-14 (usuario); 11 = TASK-15.

## Etapa 1 · La pieza común (sin cambio de comportamiento)

- [x] **TASK-01 · Sacar `CatalogCatchUp` de la verificación**
  - Alcance: `synchronization/domain/model/CatchUpResult` (nuevo: al día, actualizado y fallido); `synchronization/application/support/CatalogCatchUp` (nuevo), con el procedimiento movido de los métodos privados de `VerifyCatalogVersionUseCaseImpl` sin cambiar su comportamiento, y un nombre que da quien llama al frente de cada línea del log (ajuste aprobado por el usuario al empezar la tarea); una completa "descartada" dentro de la pieza se trata como fallida con un error en el log (P2.1). `VerifyCatalogVersionUseCaseImpl` queda con esperar el login, `tryAcquire` u omitirse, la pieza y la traducción de `CatchUpResult` a `VerificationResult`, sin cambiar su puerto. Tests: los 17 del procedimiento pasan de `VerifyCatalogVersionUseCaseImplTest` a `CatalogCatchUpTest`; quedan los 2 propios de C03 y uno nuevo que muestra que usa la pieza; los textos del log de C03 no cambian, así que ningún otro test se toca. No se toca nada de `catedra`, `catalog` ni el disparador.
  - Depende de: ninguna
  - Resuelve: base de RF-05; CA-08 (parte de C03); P2, P2.1
  - Validación: línea base antes de tocar nada (`./mvnw -q clean test`); después, `./mvnw test` en verde, con los tests de integración de C03 (`CatalogVerificationIntegrationTest`, `CatalogVerificationWithoutRedisIntegrationTest`, `VerificationAtStartupIntegrationTest`, `VerificationSchedulingIntegrationTest`) sin cambios en lo que comprueban; el conteo de tests sube solo por los nuevos. Prueba en negativo en la pieza y otra en la traducción de C03.
  - Resultado: 2026-11-19 (tanda 1, autorizada ese día). Al empezar, el usuario aprobó un ajuste menor a P2: en lugar de textos neutros, la pieza recibe un nombre que encabeza cada línea del log (C03 pasa "Catalog verification"), así que los textos de C03 quedaron idénticos y no se tocó ningún test de integración ni el E2E de C03. Creados `CatchUpResult` y `CatalogCatchUp` (el procedimiento movido sin cambiar su lógica, en una clase interna por recorrido que guarda el nombre; una completa "descartada" queda como `Failed` con un error en el log). `VerifyCatalogVersionUseCaseImpl` quedó con la espera del login, `tryAcquire` u omitirse, la pieza y la traducción a `VerificationResult`; su constructor bajó de 10 dependencias a 3 y su puerto no cambió. Tests: `CatalogCatchUpTest` con los 17 del procedimiento (mismo armado de dobles, nombre de prueba "Test catch-up") más `aDiscardedFullSynchronizationIsReportedAsAFailureWithoutRecordingAnother`; `VerifyCatalogVersionUseCaseImplTest` con los 2 propios de C03 más `theVerificationUsesTheCatchUpWithItsNameAndTranslatesEachResult`. Cambiaron dos textos del log que solo miraba un test movido: "The failed catalog verification could not be recorded" pasa a "{nombre}: the failure could not be recorded", y lo mismo con el registro del catálogo al día.
    - Línea base: 394 tests en 46 clases, 0 fallos. Una primera corrida sin `clean` contó 401, porque en `target/` quedaba el reporte de un test que el usuario había quitado; por eso los conteos se hacen después de `clean`.
    - Después: tests de la pieza, de la verificación y las cuatro clases de integración de C03 en verde (18, 3, 6, 1, 1 y 2). `./mvnw -q clean test`: 396 tests en 47 clases (394 − 19 + 18 + 3), 0 fallos ni errores.
    - Prueba en negativo 1: las versiones pendientes empezando en `localVersion + 2` en `CatalogCatchUp`: fallaron 9 de los 18 tests de la pieza. Restaurado, `sha256sum` coincide.
    - Prueba en negativo 2: `Updated` traducido como `UpToDate` en la verificación: fallaron 7 tests, el nuevo de `VerifyCatalogVersionUseCaseImplTest` y 6 de `CatalogVerificationIntegrationTest` (la red de C03 detecta el cambio). Restaurado, `sha256sum` coincide.
    - Chequeos de la skill (§12): 1 a 4 sin resultados; en el 5, los imports de otros slices son solo de `catalog.domain` y `catedra.domain` (modelos y puertos de entrada), desde las tres clases de la fachada.

## Etapa 2 · El candado que espera

- [x] **TASK-02 · Esperar el candado, interrumpible**
  - Alcance: `SynchronizationLock` suma una espera hasta tomarlo con `lockInterruptibly`, sin tope (P6). Tests unitarios con dos hilos.
  - Depende de: ninguna
  - Resuelve: base de RF-04; CA-07 (parte del candado)
  - Validación: un hilo espera mientras otro tiene el candado y lo toma al liberarse; un hilo esperando se interrumpe y la espera termina sin tomarlo, con la marca de interrupción restaurada; quien ya lo tiene vuelve a entrar sin esperar; `tryAcquire` sigue igual. `./mvnw test` en verde.
  - Resultado: 2026-11-19 (tanda 2). `SynchronizationLock.acquire()`: espera con `lockInterruptibly`; si el hilo se interrumpe, restaura la marca y devuelve `false` sin tomar el candado. `SynchronizationLockTest` (nuevo, 4 tests): `tryAcquire` no espera mientras otro hilo lo tiene; un hilo que espera no lo toma en 300 ms y lo toma cuando el otro lo suelta; una espera interrumpida termina en `false`, con la marca de interrupción y sin el candado (después otro hilo lo toma); quien lo tiene vuelve a entrar con `acquire`.
    - Tests de la tarea en verde (4). Suite completa al cerrar la tanda: ver TASK-03.
    - Prueba en negativo: un primer intento con `lock()` no compiló (el `catch` quedaba inalcanzable), así que no probaba nada. Se repitió con `lock()` y una excepción alcanzable que nunca se lanza: falló `anInterruptedWaitEndsWithoutTheLockAndKeepsTheInterruption`. Restaurado, `sha256sum` coincide.

## Etapa 3 · Avisos procesados

- [x] **TASK-03 · Persistencia de los avisos procesados en `synchronization`**
  - Alcance: `db/migration/V2__create_processed_events.sql` (`event_id` `VARCHAR(255)` clave, `processed_at`); `synchronization/domain/model/ProcessedEvent` (clase Lombok); `synchronization/domain/ports/out/ProcessedEventRepository` (saber si existe un `eventId` y registrarlo); `synchronization/infrastructure/persistence/` con la entidad, el repositorio de Spring Data (`String` como clave), el mapper y el adaptador (P3, excepción 5 de P10).
  - Depende de: ninguna
  - Resuelve: base de RF-03 y RF-06; CA-09 y CA-10 (parte de la base)
  - Validación: la migración corre en la base de Testcontainers y no toca las tablas de C01; test de ida y vuelta del mapper (skill §10); integración del adaptador contra la base real: un `eventId` no registrado no existe, después de registrarlo existe, el instante llega en UTC (leído con SQL directo), y uno de 255 caracteres se guarda. `./mvnw test` en verde.
  - Resultado: 2026-11-19 (tanda 2). Creados `V2__create_processed_events.sql` (`event_id` `VARCHAR(255)` clave, `processed_at` `TIMESTAMPTZ NOT NULL`, como las fechas de `catalog_sync_state`); `ProcessedEvent` (clase Lombok); `ProcessedEventRepository` con `create` y `findById`, nombres del molde de la skill (C02 preguntará si existe con `findById(...).isPresent()`); y en `synchronization/infrastructure/persistence/` la entidad (sin `@GeneratedValue`, clave `String`), `JpaProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String>`, el mapper y el adaptador. Tests: `ProcessedEventMapperTest` (ida y vuelta con todos los campos, y `null`) y `JpaProcessedEventRepositoryAdapterTest` contra el PostgreSQL de Testcontainers (4 tests): no existe hasta registrarlo y después se lee igual; el instante llega en UTC (leído con SQL directo); un `eventId` de 255 caracteres se guarda; y la base tiene exactamente las cuatro tablas de C01 más `processed_events`, con la fila de `catalog_sync_state` intacta.
    - Tests de las dos tareas y `CatalogRepositoryAdaptersTest` (regresión de la base) en verde: 4, 2, 4 y 7.
    - `./mvnw -q clean test` al cerrar la tanda: 406 tests en 50 clases (396 + 4 + 2 + 4), 0 fallos ni errores.
    - Prueba en negativo: el mapper sin copiar `processedAt` a la entidad: falló la ida y vuelta, y tres tests del adaptador con `DataIntegrityViolation` (la columna es `NOT NULL`). Restaurado, `sha256sum` coincide.
    - Chequeos de la skill (§12): sin resultados; el dominio y la infraestructura nuevos de `synchronization` no importan nada de otros slices.

## Etapa 4 · C02 en `synchronization`

- [x] **TASK-04 · El caso de uso de C02**
  - Alcance: `synchronization/domain/model/IncrementalSyncResult` (los tres casos de la pieza más "ya procesado"); `synchronization/domain/ports/in/SynchronizeCatalogIncrementallyUseCase`, que recibe el `eventId` y la `newVersion`; `synchronization/application/usecases/SynchronizeCatalogIncrementallyUseCaseImpl`: consultar el `eventId`, esperar el candado, la pieza común, registrar el `eventId` con un solo intento, las fallas de la base fuera de una versión (D9) y el log (RF-08); `SynchronizationService` expone C02. Tests unitarios con dobles.
  - Depende de: TASK-01, TASK-02, TASK-03
  - Resuelve: RF-03, RF-04, RF-05, RF-06, RF-07, RF-08 (parte de `synchronization`); CA-06, CA-07, CA-08, CA-09, CA-11 y CA-12 (partes unitarias); CA-13 (log del aviso)
  - Validación: unitarios: un `eventId` registrado no toma el candado ni lee Redis; con el candado tomado por otro hilo, C02 espera y después lee de nuevo; terminó bien (aplicó, completa o al día) → `eventId` registrado; la base rechaza el registro → error en el log y el resultado sigue siendo el de la sincronización; cada fracaso de la pieza → `eventId` sin registrar; falla al consultar el `eventId` o al leer el estado → error en el log y sin escribir nada; interrupción durante la espera → termina sin procesar. El log tiene el `eventId`, la `newVersion` y el resultado. `./mvnw test` en verde.
  - Resultado: 2026-11-20 (tanda 3, autorizada el 2026-11-19). Creados `IncrementalSyncResult` (`AlreadyProcessed`, `UpToDate`, `Updated`, `Failed`), `SynchronizeCatalogIncrementallyUseCase` (recibe el `eventId` y la `newVersion`) y `SynchronizeCatalogIncrementallyUseCaseImpl`; `SynchronizationService` suma `synchronizeCatalogIncrementally`. El caso de uso consulta el `eventId` antes del candado, espera el candado con `acquire`, llama a `CatalogCatchUp` con el nombre "Catalog update notice {eventId}" (así cada línea del log del procedimiento dice qué aviso la disparó), y si terminó bien registra el aviso con el instante del reloj, en un solo intento. Una falla de la base al consultar los avisos, o cualquier error que salga de la pieza (por ejemplo, al leer la versión local), deja un error en el log y devuelve `Failed` sin escribir nada (D9.3); el candado se suelta siempre. Una interrupción mientras espera devuelve `Failed` sin procesar ni registrar; qué hace el oyente con el offset en ese caso se resuelve en TASK-08 (P6). El log tiene una línea al recibir el aviso (con la `newVersion`) y otra al terminar (con el resultado).
    - `SynchronizeCatalogIncrementallyUseCaseImplTest` (nuevo, 11 tests), con dobles de los puertos y la pieza común de verdad: aplica versiones y registra el aviso con su instante (y el log); al día, registrado; base vacía, completa y registrado; ya procesado, sin esperar el candado (que tiene otro hilo) ni leer Redis; un fracaso de la pieza y una completa fallida, sin registrar; la base rechaza el registro, error en el log y el resultado se conserva; falla al consultar los avisos y al leer la versión local, sin escribir nada; espera a otra sincronización y lee de nuevo después (aplica solo la versión publicada mientras esperaba); y la interrupción mientras espera.
    - **Corregido de paso:** los tests comprobaban que el candado quedó libre con `tryAcquire` desde el mismo hilo que corrió el caso de uso, y como el candado es reentrante eso no detectaba nada. Ahora se comprueba desde otro hilo, también en el test de la verificación de TASK-01, que tenía la misma debilidad.
    - Tests de C02, de la pieza, de la verificación y `CatalogoApplicationTests` (Spring arma el caso de uso nuevo) en verde: 11, 18, 3 y 1.
    - `./mvnw -q clean test`: 417 tests en 51 clases (406 + 11), 0 fallos ni errores.
    - Pruebas en negativo, cada una restaurada con `sha256sum` comprobado: C02 sin soltar el candado (fallaron 3 tests de C02); la verificación sin soltar el candado (falló el test de la verificación, lo que valida la corrección); el catálogo al día sin registrar el aviso (falló 1); una falla al consultar los avisos tratada como "no procesado" (falló 1).
    - Chequeos de la skill (§12): sin resultados; C02, su puerto y su resultado no importan nada de otros slices.

## Etapa 5 · El aviso en `catedra`

- [x] **TASK-05 · Leer y validar el aviso**
  - Alcance: `catedra/domain/model/CatalogUpdateNotice`; `catedra/infrastructure/kafka/dto/CatalogUpdatedMessage` (los seis campos como texto); `catedra/infrastructure/kafka/CatalogUpdateReader`, con su resultado (válido o inválido con el motivo) y el recorte de lo que va al log: 100 caracteres y `?` por cada carácter de control (D6, P7).
  - Depende de: ninguna
  - Resuelve: RF-02 (lectura y validación); CA-04, CA-05 y CA-13 (partes unitarias)
  - Validación: unitarios con un mensaje por cada defecto de CA-04 (valor nulo, JSON ilegible o que no es un objeto, cada campo ausente o vacío, `eventId` de 256 caracteres, con un salto de línea y con el carácter nulo, `eventType` distinto, `newVersion` negativa o no entera, `occurredAt` que no es un instante, `source` de 65 caracteres o con un carácter de control, `schemaVersion` distinto de 1) y por cada variante de CA-05 (`occurredAt` con nanosegundos y sin fracción, campos desconocidos, `source` fuera de los ejemplos); los mensajes reales de la lectura del topic, con el formato limpio, se aceptan; un inválido con `eventId` legible lo conserva para el log; el recorte deja 100 caracteres y reemplaza los de control. `./mvnw test` en verde.
  - Resultado: 2026-11-20 (tanda 4). Creados `CatalogUpdateNotice` (`record` del dominio de `catedra`: `eventId`, `newVersion`, `occurredAt`, `source`); `CatalogUpdatedMessage` (DTO con los seis campos como `JsonNode`, sin convertir, e `ignoreUnknown`), lo que permite distinguir el número `1` del texto `"1"` y leer el `eventId` aunque otro campo esté mal; `CatalogUpdateReading` (`Valid` con el aviso, o `Invalid` con el `eventId` legible y el motivo, ya recortados); y `CatalogUpdateReader`, que junta todos los problemas del mensaje en el motivo y tiene `forLog` (100 caracteres, cada carácter de control como `?`). Un valor con caracteres de control nunca entra al motivo: se rechaza con "with control characters"; el único valor del mensaje que entra al motivo es un `eventType` distinto de `CatalogUpdated`, recortado. `schemaVersion` se muestra con su forma JSON, que ya escapa los caracteres de control.
    - `CatalogUpdateReaderTest` (nuevo, 41 tests): el mensaje real de la lectura del topic, con sus campos; 6 variantes aceptadas (instante sin fracción, campos desconocidos, `source` fuera de los ejemplos, `eventId` de 255, `source` de 64, `newVersion` 0); 29 defectos, uno por caso de CA-04 (valor nulo, vacío, JSON `null`, ilegible, un arreglo, un texto; cada campo ausente, nulo, vacío o de otro tipo; `eventId` de 256, con salto de línea y con el carácter nulo; otro `eventType`; `newVersion` negativa, decimal o como texto; `occurredAt` que no es un instante o sin zona; `source` de 65 o con un carácter de control; `schemaVersion` 2 o como texto); el `eventId` de un inválido se conserva para el log, recortado y sin caracteres de control; un `eventType` largo llega recortado al motivo; un valor con caracteres de control no llega al motivo; y `forLog`.
    - Un primer intento de un test esperaba que un `eventType` con caracteres de control llegara al motivo con `?`; el código lo rechaza antes por la regla general, así que nunca llega al log. El test estaba mal planteado y se separó en los dos casos que sí ocurren.
    - `./mvnw -q clean test`: 458 tests en 52 clases (417 + 41), 0 fallos ni errores.
    - Pruebas en negativo, cada una restaurada con `sha256sum` comprobado: sin la regla de los caracteres de control (4 fallos y 1 error: avisos con salto de línea y carácter nulo aceptados); `forLog` sin reemplazar (2 fallos); `schemaVersion` aceptado como texto (1 fallo). Nota: en las dos primeras, el filtro del log no mostró nada porque el log tenía el carácter nulo y `grep` lo trató como binario; se repitieron leyendo el log como texto.
    - Chequeos de la skill (§12): sin resultados; `catedra` no importa nada de otros slices.

## Etapa 6 · El cruce entre slices y el aviso de sesión

- [ ] **TASK-06 · Aviso de sesión habilitada en X01**
  - Alcance: `catedra/domain/ports/out/TechnicalIntegrationListener`; `StartTechnicalSessionUseCaseImpl` y `RenewTechnicalSessionUseCaseImpl` lo llaman después de guardar una sesión habilitada (P5). Sin otro cambio de X01. Tests de los dos casos de uso con un doble del puerto.
  - Depende de: ninguna
  - Resuelve: base de RF-01 (D12.1); CA-02 (parte unitaria)
  - Validación: unitarios de los dos casos de uso: avisan una vez con la integración cuando la sesión queda habilitada, y no avisan si queda degradada, con error de configuración o sin integración completa; los tests existentes de X01 siguen en verde sin cambiar lo que comprueban. `./mvnw test` en verde.
  - Resultado: pendiente

- [ ] **TASK-07 · Del aviso de `catedra` a C02**
  - Alcance: `catedra/domain/ports/out/CatalogUpdateHandler`; `catedra/domain/ports/in/ReceiveCatalogUpdateUseCase` y su implementación; `catedra/application/service/CatalogUpdateService`; `synchronization/infrastructure/gateway/CatalogUpdateHandlerAdapter`, que implementa el puerto y llama a `SynchronizationService` con el `eventId` y la `newVersion` (P1, decisión 004, excepción 1 de P10).
  - Depende de: TASK-04, TASK-05
  - Resuelve: RF-01 a RF-07 (el cruce); P1
  - Validación: unitario del caso de uso de `catedra` (entrega el aviso al puerto) y del adaptador (llama al service con los valores del aviso); chequeo 5 de la skill (§12): los únicos imports entre slices nuevos son el puerto y el modelo del aviso, del dominio de `catedra`, desde el adaptador. `./mvnw test` en verde.
  - Resultado: pendiente

## Etapa 7 · El consumo

- [ ] **TASK-08 · Consumir el topic del catálogo**
  - Alcance: `catedra/infrastructure/kafka/CatedraKafkaProperties` (`enabled`, `max-poll-interval`, y `consumer-group` solo para los E2E, sin entrada en el yaml); `catedra/infrastructure/kafka/KafkaCatalogConsumer`, que implementa `TechnicalIntegrationListener`: con la primera integración arma el contenedor de spring-kafka (grupo `{kafkaConsumerGroupId}-catalog`, `latest`, sin confirmación automática, un aviso por lectura, `max-poll-interval`, 30 s como máximo entre reconexiones), pide el oyente con `ObjectProvider`, deja el warning de D12.2 si cambian los datos, no hace nada si `enabled` está apagado, y detiene el contenedor al apagar el servicio; `catedra/infrastructure/kafka/CatalogUpdateListener`: lector, service y todo error atrapado y registrado (P4, P7, P8). `application.yaml` y `.env.example` con `CATALOGO_CATEDRA_KAFKA_ENABLED` y `CATALOGO_CATEDRA_KAFKA_MAX_POLL_INTERVAL`, y en la tabla del `CLAUDE.md`; Surefire apaga el consumo en todos los tests, también con `-Pe2e`.
  - Depende de: TASK-06, TASK-07
  - Resuelve: RF-01, RF-07 (oyente que nunca deja un aviso sin confirmar), RF-08 (log del aviso inválido); CA-02 (parte del consumidor)
  - Validación: unitario de `KafkaCatalogConsumer` con dos integraciones distintas (la segunda deja el warning y no rearma) y con `enabled` apagado; unitario del oyente (un error en el service queda en el log y no sale); la aplicación completa arranca en un test (riesgo del ciclo de beans) sin intentar conectarse a `localhost:9092` (riesgo del starter), comprobado en su log. `./mvnw test` en verde.
  - Resultado: pendiente

## Etapa 8 · Integración con Kafka en contenedor

- [ ] **TASK-09 · Kafka en contenedor para los tests**
  - Alcance: en `src/test/java/catalogo/`: Kafka de Testcontainers (`apache/kafka:4.2.1`), compartido por la suite, que se puede pausar y reanudar; una ayuda para publicar avisos como la cátedra y saber hasta dónde confirmó un grupo; un login de `FakeCatedraServer` cuya integración apunta a ese Kafka (y al Redis de test) (P9.1).
  - Depende de: ninguna
  - Resuelve: base de CA-01 a CA-03, CA-06, CA-07 y CA-09 a CA-13
  - Validación: un test de la ayuda: publica un aviso, un consumidor de prueba lo lee, y la ayuda informa la posición confirmada; pausar y reanudar funcionan. `./mvnw test` en verde.
  - Resultado: pendiente

- [ ] **TASK-10 · Integración del consumo**
  - Alcance: tests `*IntegrationTest` con la aplicación completa, Kafka y Redis en contenedor y la cátedra simulada.
  - Depende de: TASK-08, TASK-09
  - Resuelve: CA-01, CA-02, CA-03
  - Validación: CA-01: el grupo `{kafkaConsumerGroupId}-catalog` aparece en el Kafka de test, y se procesa el aviso publicado después de empezar a consumir y no el de antes. CA-02: con un login del arranque degradado, el consumo arranca con una renovación exitosa. CA-03: con Kafka pausado al arrancar, el servicio atiende y el estado no cambia; al reanudar, se procesan los avisos publicados mientras tanto, y los warnings de reconexión quedan acotados. `./mvnw test` en verde.
  - Resultado: pendiente

- [ ] **TASK-11 · Integración de un aviso: recorrido y repetidos**
  - Alcance: tests de integración de punta a punta, del aviso publicado a la base.
  - Depende de: TASK-10
  - Resuelve: CA-04, CA-05, CA-06, CA-08, CA-09, CA-10, CA-13
  - Validación: un aviso con versiones pendientes aplica de forma incremental y registra el `eventId`; un inválido seguido de uno válido: el primero descartado con su motivo y sin cambios, el segundo procesado; el mismo aviso dos veces: la segunda no lee Redis ni cambia nada; el `eventId` borrado y el aviso publicado de nuevo: al día, registrado, sin cambios repetidos; con la base vacía, sincronización completa sin leer Redis; el log tiene los avisos y resultados, recortes con `?` para un aviso con saltos de línea, y no tiene el JWT ni la contraseña de Redis de test. `./mvnw test` en verde.
  - Resultado: pendiente

- [ ] **TASK-12 · Integración de un aviso: fallas y concurrencia**
  - Alcance: tests de integración con dependencias que fallan y con otra sincronización en curso.
  - Depende de: TASK-10
  - Resuelve: CA-07, CA-09 (falla del registro), CA-11
  - Validación: con una verificación en curso (demorada), el aviso espera, después aplica lo publicado mientras tanto, y nunca hay dos sincronizaciones a la vez; la base rechaza el registro del `eventId` (disparador de PostgreSQL, como en C01): error en el log, offset confirmado y estado sin cambios; Redis caído: el fracaso queda registrado como en C03, el `eventId` no, el offset se confirma y el aviso siguiente se procesa. `./mvnw test` en verde.
  - Resultado: pendiente

## Etapa 9 · E2E

- [ ] **TASK-13 · E2E de C02 contra la cátedra real**
  - Alcance: `src/test/java/e2e/`: un E2E con `@Tag("e2e")` que exige `CATALOGO_E2E_KAFKA_CONSUMER_GROUP` terminada en `-e2e` (la aplicación no la lee; el test la pasa a `catalogo.catedra.kafka.consumer-group`) y enciende el consumo solo para él. Recorrido (P9.2): carga el snapshot, baja la versión local con SQL, mueve la posición de su grupo al último aviso retenido, espera que el aviso se procese (versión en `currentVersion`, tipo `INCREMENTAL`, `eventId` registrado), vuelve a mover la posición al mismo aviso y comprueba que la segunda entrega no cambia nada. Lista exacta de pedidos para el usuario.
  - Depende de: TASK-11
  - Resuelve: CA-14 (preparación)
  - Validación: compila; `./mvnw test -Pe2e -Dtests.env-file=<archivo inexistente>` lo saltea sin credenciales; la suite normal no lo corre. No se ejecuta contra la cátedra real en esta tarea.
  - Resultado: pendiente

- [ ] **TASK-14 · Correr el E2E (lo hace el usuario)**
  - Alcance: el usuario corre `./mvnw test -Pe2e` en su terminal, después de confirmar la lista de pedidos de TASK-13; se revisan los reportes sin mostrar secretos.
  - Depende de: TASK-13
  - Resuelve: CA-14
  - Validación: el reporte del E2E en verde, con la versión, el tipo y el `eventId` del aviso real, y la segunda entrega sin cambios; el grupo del servicio sigue sin posiciones.
  - Resultado: pendiente

## Etapa 10 · Cierre

- [ ] **TASK-15 · Cierre de C02**
  - Alcance: `CLAUDE.md` (slices, excepciones de C02, propiedades nuevas, cómo usar Kafka de la cátedra, cómo pedir C02, tests de Kafka y lo comprobado del topic real); `docs/decisiones/003` con las filas de C02 (aviso al día, repetido, inválido, registro del `eventId`); marcar las tareas. Con el usuario: el "por verificar" de la decisión 014 de turnos (§4) sobre el consumer group, que es de otro repositorio, y si catálogo tiene su propio documento vivo de Kafka.
  - Depende de: TASK-01 a TASK-14
  - Resuelve: `AGENTS.md`, "Código"; cierre de la SPEC y el PLAN
  - Validación: `./mvnw clean verify` en verde con el conteo final; los cinco chequeos de la skill (§12) sin resultados fuera de las excepciones autorizadas; las copias de los documentos siguen idénticas a `documentacion`.
  - Resultado: pendiente
