# PLAN: C03 — Verificar la versión del catálogo periódicamente

**SPEC de referencia:** `docs/sdd/c03-verificacion-periodica/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-18 (rama `verificacion-periodica`, sin commitear)
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

Verificado el 2026-11-18 en la rama `verificacion-periodica` (`bb650c9`). Todavía no hay código de C03.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| Sesión técnica (X01) | `catalogo/catedra/domain/ports/in/GetTechnicalSessionUseCase.java`, `RenewTechnicalSessionUseCase.java`; `catedra/domain/model/TechnicalSession.java`, `TechnicalIntegration.java` | Dan la sesión, con `redisHost`, `redisPort`, `redisUsername` y `redisPassword`, y la renuevan entregando la sesión rechazada. Mientras el login inicial corre, el estado es `CONNECTING` y `renew` devuelve vacío. Los usa la lectura de Redis (RF-03, RF-12) |
| Login inicial | `catedra/infrastructure/startup/TechnicalSessionStartup.java`, `catedra/application/usecases/StartTechnicalSessionUseCaseImpl.java` | Corre en un hilo virtual al `ApplicationReadyEvent`. No avisa cuando termina: la espera de D2 es nueva (P6) |
| Pedido del snapshot (C01) | `catedra/application/usecases/GetCatalogSnapshotUseCaseImpl.java`, `catedra/infrastructure/client/CatedraSnapshotClientAdapter.java` | Modelo de una operación con la cátedra: cliente de un intento con resultado sellado, y caso de uso con sesión, renovación una vez y reintentos con su política |
| Validación del snapshot | `catedra/infrastructure/client/mapper/CatalogSnapshotMapper.java`, `catedra/domain/model/CatalogSnapshot.java` | Revisa cada elemento con reglas que coinciden con RF-15. Se puede reusar para las entidades de Redis (P5) |
| Propiedades y políticas | `catedra/infrastructure/client/CatedraProperties.java`, `CatedraClientConfiguration.java`, `catedra/domain/model/CatalogSnapshotPolicy.java` | Secciones `login` y `snapshot` con valores por defecto; la configuración arma las políticas. Suma la sección de Redis (D7) |
| Copia local y estado | `catalog/domain/model/CatalogSyncState.java`, `catalog/domain/ports/in/ReplaceCatalogUseCase.java`, `RecordSyncFailureUseCase.java`, `catalog/domain/ports/out/*Repository.java` | Hoy solo escribe: no hay puerto para leer el estado ni para buscar entidades por id. `recordFailure` asigna el tipo: cambia por D11 (RF-13). Los repositorios tienen `saveAll` (actualiza o inserta por `id`) y `deleteAllExcept` |
| Entidades JPA | `catalog/infrastructure/persistence/entity/ProfessionalEntity.java`, `WeeklyScheduleEntity.java` | `@ManyToOne` sin cascada hacia la categoría y el profesional |
| Sincronización completa y candado | `synchronization/application/usecases/SynchronizeFullCatalogUseCaseImpl.java`, `synchronization/application/support/SynchronizationLock.java`, `synchronization/application/service/SynchronizationService.java` | C03 toma el candado con `tryAcquire` para toda su pasada y pide C01 desde adentro (es reentrante) |
| Reloj | `catalogo/config/ClockConfig.java` | Instantes del registro |
| Dependencias de Redis | `pom.xml`: `spring-boot-starter-data-redis` y `spring-boot-starter-data-redis-test` | En el repositorio local de Maven: Lettuce 7.5.2 y Spring Data Redis 4.1.2. Sin configuración de Redis en `application.yaml`: Spring Boot arma igual una conexión por defecto a `localhost:6379` que nadie usa (P3) |
| Tests | `src/test/java/catalogo/TestcontainersConfiguration.java`, `FakeHttpServer`, `catedra/FakeCatedraServer`, `catalog/CatalogSamples`, `CatalogTables`, `MutableClock`, `e2e/E2ECredentials`, `e2e/FullSynchronizationE2ETest` | Base real con Testcontainers, cátedra simulada, muestras y E2E. No hay nada de Redis: el módulo base de Testcontainers (`GenericContainer`) ya está y permite un Redis real en contenedor (P12) |
| Turnos, como referencia | `turnos_service/src/main/java/turnos/reservation/infrastructure/scheduling/`, `turnos/config/SchedulingConfig.java`, `turnos_service/CLAUDE.md` ("Tareas programadas") | `@Scheduled` con espera fija en `infrastructure/scheduling` (una excepción en turnos), una propiedad para apagarlo en los tests y la espera del login inicial con tope (`runAfterInitialLogin`). Turnos no usa Redis. Es una opción más, no la respuesta por defecto |

**Convenciones y slice de referencia:** `catedra` (X01 y C01) para lo que toca a la cátedra; `catalog` y `synchronization` (C01) para la copia local y el proceso. Fachada en `synchronization` (P2 del PLAN de C01). La skill `hexagonal-vertical-slicing` se aplica tal como está; cada excepción se analiza en el contexto de C03.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de la solicitud, el evento o la tarea desde que entra hasta la
respuesta, la persistencia o el mensaje publicado. Referencia el diagrama de
secuencia del proyecto si ya lo describe; usa un diagrama nuevo solo si aporta
claridad. -->

Propuesta en líneas generales; lo marcado con P está pendiente. Sigue la secuencia §2 y la separación de C01: `catedra` habla con Redis y entrega datos ya validados, `catalog` guarda, y `synchronization` decide.

```
disparador periódico (espera fija; primera corrida al arrancar)          (P11)
└─ SynchronizationService.verifyCatalogVersion()
   └─ VerifyCatalogVersionUseCaseImpl                                     synchronization (P1, P9)
      ├─ candado: tryAcquire; si no, omitida                              (RF-02)
      ├─ espera del login inicial, con tope (solo en la primera)          catedra (P6; RF-01)
      ├─ estado local (versión, último intento)                           catalog (P8)
      ├─ sin versión local → SynchronizeFullCatalog y una sola pasada más (RF-11)
      ├─ metadata de Redis: sesión, rechazo con una renovación, reintentos catedra (P2, P3, P10; RF-03, RF-12, RF-14)
      ├─ comparar: al día | atrasada | discontinuidad                      (RF-04)
      ├─ por cada versión pendiente:
      │    ├─ changes:{v} y sus entidades, validados                       catedra (P2, P5; RF-14, RF-15)
      │    └─ aplicar la versión en una transacción, con sus referencias  catalog (P7; RF-05, RF-10)
      │       y su registro; si falla la base, reintentos                  (P10; RF-07)
      ├─ discontinuidad → SynchronizeFullCatalog y una sola pasada más     (RF-06)
      └─ registrar: fracaso, o la verificación que corrige uno             catalog (P8; RF-08, RF-13)
```

## Slices y componentes afectados

<!-- Aplica la skill hexagonal-vertical-slicing: un paquete por slice, con sus
capas domain, application e infrastructure. Identifica los slices afectados, sus
responsabilidades y la dirección de sus dependencias. Una funcionalidad puede
afectar más de un slice. Justifica cualquier slice nuevo o dependencia nueva
entre slices. -->

| Slice | Existe / nuevo | Responsabilidad y cambios | Dependencias con otros slices |
| --- | --- | --- | --- |
| `catedra` | Existente | Suma la lectura de Redis (metadata y una versión con sus entidades) y la espera del login inicial | Ninguna. `synchronization` usa sus puertos de entrada nuevos |
| `catalog` | Existente | Suma la lectura del estado, aplicar una versión y el registro de la verificación; cambia el registro de un fracaso (D11) | Ninguna. `synchronization` usa sus puertos de entrada |
| `synchronization` | Existente | Suma la verificación periódica y su disparador | Usa `catedra` y `catalog` (P1) |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son
propuestas. Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Slice y capa | Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- | --- |
| catedra / domain | `model/SnapshotCategory`, `SnapshotProfessional`, `SnapshotWeeklySchedule` → `CatedraCategory`, `CatedraProfessional`, `CatedraWeeklySchedule`; `model/CatalogSnapshot` | Modificar (renombre) | Sin cambio de comportamiento (P5.2) | RF-15 |
| catedra / domain | `model/CatalogMetadata`, `model/CatalogVersionChanges` | Crear | La metadata (`currentVersion`, `oldestAvailableVersion`) y una versión con las entidades vigentes de sus ids | RF-03, RF-05 |
| catedra / domain | `model/CatalogMetadataResult`, `model/CatalogVersionChangesResult`, `model/RedisReadResult` | Crear | Resultados para quien pide (obtenido, no disponible, rechazado, inválido) y de un intento del cliente (P2) | RF-03, RF-14 |
| catedra / domain | `model/CatalogRedisPolicy`, `model/InitialLoginWait` | Crear | Reintentos y timeouts de Redis (D7, P10); tope y consulta de la espera del login inicial (P6) | RF-01, RF-03 |
| catedra / domain | `ports/in/GetCatalogMetadataUseCase`, `GetCatalogVersionChangesUseCase`, `AwaitInitialTechnicalSessionUseCase` | Crear | Las dos lecturas de Redis (P2) y la espera del login inicial (P6) | RF-01, RF-03, RF-05 |
| catedra / domain | `ports/out/CatalogRedisClient` | Crear | Un intento de cada lectura con los datos de una integración | RF-03, RF-14 |
| catedra / application | `usecases/GetCatalogMetadataUseCaseImpl`, `GetCatalogVersionChangesUseCaseImpl`, `AwaitInitialTechnicalSessionUseCaseImpl` | Crear | Sesión, renovación una vez ante un rechazo, reintentos y log de cada intento; espera del login con tope. Las dos lecturas usan `CatalogRedisReader` (P14) | RF-01, RF-03, RF-09, RF-12, RF-14 |
| catedra / application | `support/CatalogRedisReader` | Crear | La secuencia común de las lecturas de Redis (P14) | RF-03, RF-09, RF-12, RF-14 |
| catedra / infrastructure | `client/CatedraRedisClientAdapter` | Crear | Lettuce: conexión con la sesión, sin `PING`, RESP2, guardada y rearmada si cambian los datos, cerrada al apagar; lectura en lote; clasificación de errores (P3.1, P4) | RF-03, RF-14 |
| catedra / infrastructure | `client/mapper/CatedraEntityMapper`, `client/mapper/CatalogVersionChangesMapper`, `client/dto/CatalogVersionChangesResponse` | Crear | Reglas comunes de cada entidad; sobre de `changes:{v}` y coincidencia de ids (P5.1) | RF-14, RF-15 |
| catedra / infrastructure | `client/mapper/CatalogSnapshotMapper`, `client/dto/Snapshot…Response` → `Catedra…Response`, `client/dto/CatalogSnapshotResponse` | Modificar | El mapper del snapshot delega cada entidad en `CatedraEntityMapper`; renombre de los DTO (P5.1, P5.2) | RF-15 |
| catedra / infrastructure | `client/CatedraProperties`, `client/CatedraClientConfiguration` | Modificar | Secciones `redis` e `initial-login`; beans de `CatalogRedisPolicy` e `InitialLoginWait` (P10) | RF-01, RF-03 |
| catalog / domain | `model/CatalogSyncState` | Modificar | `recordFailure` sin tipo (D11); `recordUpToDateCheck` (D4) | RF-08, RF-13 |
| catalog / domain | `ports/in/ApplyCatalogVersionUseCase`, `GetCatalogSyncStateUseCase`, `RecordUpToDateCheckUseCase`; `ports/in/RecordSyncFailureUseCase` | Crear / modificar | Aplicar una versión (P7), leer el estado y registrar la verificación al día (P8); el registro de un fracaso pierde el tipo | RF-05, RF-08, RF-10, RF-13 |
| catalog / domain | `ports/out/ProfessionalCategoryRepository`, `ProfessionalRepository` | Modificar | Lectura por ids para resolver las referencias (P7) | RF-10 |
| catalog / application | `usecases/ApplyCatalogVersionUseCaseImpl`, `GetCatalogSyncStateUseCaseImpl`, `RecordUpToDateCheckUseCaseImpl`; `usecases/RecordSyncFailureUseCaseImpl` | Crear / modificar | `@Transactional` en las que escriben; la referencia rota se informa con `CatalogVersionNotVerifiableException` (P13.1) | RF-05, RF-08, RF-10, RF-13 |
| catalog / application | `exception/CatalogException`, `exception/CatalogVersionNotVerifiableException` | Crear | Base de las excepciones del slice y la de una versión no verificable (skill §2.9; P13.1). La base la reusan C04 a C06 | RF-10 |
| catalog / infrastructure | `persistence/adapter/JpaProfessionalCategoryRepositoryAdapter`, `JpaProfessionalRepositoryAdapter` | Modificar | La lectura por ids | RF-10 |
| synchronization / domain | `ports/in/VerifyCatalogVersionUseCase`, `model/VerificationResult`, `model/VerificationPolicy` | Crear | La verificación, su resultado y los reintentos de persistencia (P10) | RF-01 a RF-15 |
| synchronization / application | `usecases/VerifyCatalogVersionUseCaseImpl` | Crear | El proceso del diagrama, con el procedimiento incremental en métodos propios (P9) | RF-01 a RF-15 |
| synchronization / application | `usecases/SynchronizeFullCatalogUseCaseImpl`, `service/SynchronizationService` | Modificar | El registro del fracaso sin tipo (D11); el service expone la verificación | RF-13 |
| synchronization / infrastructure | `scheduling/CatalogVerificationScheduler`, `scheduling/VerificationProperties`, `scheduling/VerificationConfiguration` | Crear | Disparador con espera fija, apagable; propiedades y bean de `VerificationPolicy` (P10, P11) | RF-01, RF-02 |
| config | `SchedulingConfig` | Crear | `@EnableScheduling` (P11) | RF-01 |
| Proyecto | `pom.xml` | Modificar | `lettuce-core` en lugar del starter de Redis (P3.2); Surefire apaga la verificación en todos los tests (P11) | RF-01 |
| Tests | Redis en contenedor con el ACL de la cátedra, datos de ejemplo de Redis, cliente de Redis falso; tests de C01 por el renombre y por D11 (incluido CA-15 de C01) | Crear / modificar | P12.1 | CA-01 a CA-16 |
| Tests | `src/test/java/e2e/` (verificación contra la cátedra real) | Crear | P12.2 | CA-17 |
| Proyecto | `CLAUDE.md`, `docs/decisiones/003-…` | Modificar / crear | Slices, excepciones de C03, propiedades ajustables (también las del snapshot y del login), cómo usar Redis; texto del estado de sincronización | `AGENTS.md`, "Código" |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Operaciones, eventos y DTO de entrada y salida:** Redis según el contrato §4.3 y el anexo §14, solo lectura. Los contratos no cambian. C03 no expone operaciones.
- **Identificadores, relaciones y restricciones:** los `id` son los de la cátedra. Las referencias se validan antes de escribir (RF-10, P7); la clave foránea queda como segunda protección.
- **Origen de los datos devueltos o publicados y transformaciones:** la metadata y `changes:{v}` de Redis; el estado vigente de cada id, de su Hash, con la forma del elemento del snapshot (P5).
- **Persistencia, consultas y migraciones:** sin tablas ni migraciones nuevas: D4 y D11 no suman atributos. Los repositorios de categorías y profesionales suman una lectura por ids (P7).
- **Convivencia entre datos locales y remotos:** la aplicación incremental actualiza o inserta por `id` y nunca quita (SPEC, "Datos y reglas de negocio").
- **Compatibilidad y migraciones de datos existentes:** no aplica.

## Transacciones, concurrencia y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de BACKEND_GUIDELINES.md. -->

- **Estados y transiciones:** los de `CatalogSyncState` (C01), con el tipo redefinido por D11.
- **Transacciones y consistencia:** una transacción por versión, con sus datos, la versión local y su registro (D5; P7). Las lecturas de Redis y sus reintentos ocurren fuera, como el snapshot en C01.
- **Idempotencia y mensajes duplicados:** aplicar de nuevo una versión deja la copia igual. No hay mensajes (C02).
- **Concurrencia y tareas programadas:** `SynchronizationLock` con `tryAcquire`; espera fija entre ciclos (P11).
- **Timeouts, reintentos y errores de dependencias:** D7 y RF-14, con las políticas de P10. Un rechazo de credenciales renueva la sesión una vez y no cuenta como reintento, como el `401` del snapshot.
- **Seguridad y autorización:** sin endpoints. La contraseña de Redis y el JWT no salen de `catedra` ni aparecen en logs.
- **Otras consideraciones de backend aplicables y su solución:**
  - Arranque: el servicio arranca y atiende sin esperar la verificación; la primera corrida espera el login inicial en el hilo de la tarea (P6, P11).
  - Apagado: la conexión a Redis se cierra al detener el servicio. Si el servicio se detiene durante una versión, la transacción no se confirma.
  - Interrupción del hilo durante una espera: se corta la verificación como no disponible y se restaura la marca de interrupción, como en X01 y C01.
  - Logs: cada lectura fallida y su motivo en `catedra`; el resultado de la verificación (versiones local y remota, versiones aplicadas o motivo) en `synchronization`. La contraseña de Redis nunca: `TechnicalIntegration.toString` ya la oculta, y los errores de Redis se registran por su código.

## Dependencias y configuración

<!-- Librerías, servicios externos, propiedades de configuración y secretos
afectados. Verifica compatibilidad con el proyecto y justifica las incorporaciones.
No agregues dependencias por defecto. Los valores sensibles y los parámetros
ajustables se externalizan. -->

- **Dependencias:** `spring-boot-starter-data-redis` se reemplaza por `io.lettuce:lettuce-core` (7.5.2, la versión que maneja Spring Boot 4.1.2), y se quita `spring-boot-starter-data-redis-test` (P3.2). Ninguna otra.
- **Propiedades nuevas (propuesta):** intervalo de la verificación (5 min), tope de espera del login inicial (2 min), y la política y los timeouts de Redis (D7). Valores por defecto en las propiedades, como `login` y `snapshot`.
- **Documentación:** al cerrar, documentar en el `CLAUDE.md` todas las propiedades ajustables, también las del snapshot y del login, que hoy no figuran (D7 de la SPEC).
- **Secretos:** ninguno nuevo. Los datos de Redis llegan con la sesión técnica.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, de integración (base de datos, mensajería), de API o prueba manual.
No todos requieren todos los métodos. Identifica tests existentes y separa los
nuevos propuestos. Incluye regresiones relevantes. Una prueba con mocks no
demuestra persistencia, transacciones ni idempotencia. -->

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01, CA-02, CA-03, CA-05, CA-11, CA-12, CA-13 | Unitarios de `VerifyCatalogVersionUseCaseImpl` con puertos falsos que responden en orden; integración con la aplicación completa, Redis en contenedor, cátedra simulada y base real | Redis con metadata, `changes:{v}` y Hash armados por test; snapshot de ejemplo | Lecturas hechas, versión local, tablas y estado registrado en cada caso |
| CA-02, CA-11 (referencias), CA-13 (parte de la base) | Integración de `ApplyCatalogVersionUseCaseImpl` contra la base real; falla provocada con un disparador de PostgreSQL, como en C01 | Copia previa; versiones con entidades nuevas, modificadas, deshabilitadas y referencias a la copia o a la misma versión | Copia y versión tras cada versión; nada quitado; referencia rota sin escribir; versión deshecha entera |
| CA-01, CA-08 | Unitarios de `CatalogSyncState` (`recordFailure` sin tipo, `recordUpToDateCheck`) y de los casos de uso de registro; CA-15 de C01 ajustado | Estados de ejemplo | Tipo, versión y último éxito según D4 y D11 |
| CA-09, CA-10, CA-14 | Adaptador `CatedraRedisClientAdapter` contra Redis en contenedor con el ACL; unitario de la clasificación de errores; unitarios de los casos de uso de `catedra` con un cliente falso | Contenedor detenido, puerto cerrado, `CLIENT PAUSE`, usuario con otra contraseña y sin permiso | Conexión sin `PING`; lectura en lote; intentos, renovaciones y resultado de cada caso |
| CA-11, CA-14 | Unitarios de `CatedraEntityMapper` y `CatalogVersionChangesMapper` | Un JSON por cada defecto y por cada caso aceptado | Inválido con el motivo, o aceptado |
| CA-04 | Unitario de `synchronization` con el candado tomado por otro hilo | Cliente falso | Sin lecturas de Redis ni cambios; omitida en el log |
| CA-06, CA-07 | Unitario de `AwaitInitialTechnicalSessionUseCaseImpl` con reloj y repositorio controlados; integración con la cátedra simulada demorando el login | Login demorado menos y más que el tope; sesión no habilitada | Espera y sigue, o sigue sin sesión; `SOURCE_UNAVAILABLE` sin lectura |
| CA-15 | Integración con el disparador encendido y un intervalo corto; verificación que tarda más que el intervalo | Redis en contenedor demorado con `CLIENT PAUSE` | Repeticiones con el intervalo, nunca dos a la vez |
| CA-16 | Integración con captura de la salida | Los escenarios de integración | Verificaciones, intentos y resultados en el log; sin el JWT ni la contraseña de Redis de test |
| CA-17 | E2E (`@Tag("e2e")`), corrido por el usuario con `-Pe2e` (P12.2) | Cátedra real (API y Redis), `.env` local, base descartable de Testcontainers | Versión local y tipo en cada uno de los tres escenarios |

**Comprobaciones de regresión:** la suite completa (248 tests al cerrar C01) y los cinco chequeos de la skill (§12). CA-15 de C01 cambia por D11.

**Comandos verificados para compilar y ejecutar tests:** los del `CLAUDE.md`, ejecutados en C01.

**Pruebas con infraestructura real o simulada:** PostgreSQL y Redis reales en contenedores de Testcontainers (Redis 7.4 con un ACL como el de la cátedra, P12.1); la API de la cátedra, simulada con `FakeCatedraServer`; la cátedra real (API y Redis) solo en el E2E, que corre el usuario después de ver la lista de pedidos.

**Limitaciones del entorno:** la cátedra real solo publica cambios de profesionales (SPEC, CA-17).

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

Cada etapa deja la suite en verde.

1. **Renombres y mapper común (P5):** `Catedra…`, `CatedraEntityMapper` y `CatalogSnapshotMapper` delegando. Sin cambio de comportamiento: la suite de C01 en verde.
2. **El estado (D11, P8):** `recordFailure` sin tipo, `RecordSyncFailureUseCase` sin el parámetro, C01 ajustado, `recordUpToDateCheck`, `GetCatalogSyncStateUseCase` y `RecordUpToDateCheckUseCase` (CA-08 y CA-15 de C01).
3. **Aplicar una versión en `catalog` (P7):** lectura por ids, `ApplyCatalogVersionUseCase` y la referencia rota, contra la base real (CA-02 y CA-13 en su parte de base).
4. **Redis en `catedra`, el cliente (P3, P4, P5):** dependencia, Redis en contenedor con el ACL, adaptador, `CatalogVersionChangesMapper`, política y propiedades (CA-09, CA-10 y CA-14 en su parte de adaptador; CA-11 en su parte de contenido). Aquí se verifica el handshake sin `PING`.
5. **Redis en `catedra`, los casos de uso (P2, P6):** las dos lecturas con sesión, renovación y reintentos, y la espera del login inicial (CA-06, CA-07, CA-09, CA-10).
6. **La verificación en `synchronization` (P9, P10):** caso de uso, resultado, política de persistencia y service (CA-01 a CA-05, CA-11 a CA-13).
7. **El disparador y la integración completa (P11):** `SchedulingConfig`, el disparador, sus propiedades, el apagado en Surefire y los tests con la aplicación completa (CA-06, CA-15, CA-16).
8. **E2E (P12.2):** lo corre el usuario (CA-17).
9. **Cierre:** regresión, chequeos de la skill, `CLAUDE.md` y `docs/decisiones/003`.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - `PING` está prohibido por el ACL de la cátedra. Lettuce, Spring Data Redis o una validación de la conexión podrían mandarlo, o mandar `CLIENT SETINFO` al conectarse, y no se sabe cómo responde el ACL a eso. Se comprueba con un Redis de test que tenga el mismo ACL y, al final, con la cátedra real (P3, P12).
  - Cada ida y vuelta tarda unos 246 ms: leer las entidades de una versión una por una lo multiplicaría. Conviene leer en lote y mantener la conexión abierta entre ciclos (P3).
  - La conexión depende de la sesión: si una renovación trae otras credenciales, hay que rearmarla, y nunca usar una vieja (P3).
- **Decisiones pendientes:**
  - Ninguna.

- **Decisiones tomadas:**
  - **Aprobación:** el usuario aprobó el PLAN el 2026-11-18, con P1 a P14 y la lista de excepciones de P13.
  - **P1 · Estructura y dependencias entre slices (2026-11-18): opción a), fachada como en C01.** `catedra` habla con Redis y entrega la metadata y cada versión ya validadas (RF-14, RF-15), y ofrece la espera del login inicial; `catalog` aplica una versión en una transacción, comprobando las referencias contra la copia local (RF-10), y lee y registra el estado; `synchronization` decide (compara versiones, recorre las pendientes, pide C01, traduce cada resultado a su tipo de error) y tiene el disparador. El caso de uso de `synchronization` recibe directamente los puertos de entrada de los otros dos y trabaja con sus modelos (skill §9, opción fachada; P2 del PLAN de C01). La comprobación de referencias en `catalog` ya estaba anotada en la P3 del PLAN de C01. Costo aceptado: el caso de uso de `synchronization` recibe unos 7 u 8 puertos. Alternativas descartadas: b), puertos de salida propios con adaptadores en `infrastructure/gateway`, como turnos, descartada en C01 por sumar archivos y otra excepción sin ahorrar trabajo, y acá con más puertos; c), la comprobación de referencias en `synchronization`, que repartía la regla 3 del dominio entre dos slices.
  - **P2 · Puertos de entrada de `catedra` para Redis (2026-11-18): opción a), dos puertos.** `GetCatalogMetadataUseCase` (la metadata) y `GetCatalogVersionChangesUseCase` (los cambios de una versión con el estado vigente de cada id, leídos en lote y validados). Cada uno maneja la sesión, la renovación una vez ante un rechazo y los reintentos, como el snapshot, y devuelve un resultado sellado: obtenido, no disponible, rechazado o inválido. `synchronization` trata "inválido" como discontinuidad (RF-14, RF-15). Así `synchronization` lee la metadata una vez y una versión por vez, y conserva el avance parcial (D5). C07, que lee la metadata en un solo intento y sin renovar, suma su variante en su PLAN. Alternativas descartadas: b), un solo puerto con todas las versiones pendientes, que llevaba a `catedra` la comparación de versiones y leía todo antes de aplicar nada; c), tres puertos (metadata, `changes:{v}` y entidades), que llevaba a `synchronization` la coordinación de dos lecturas por versión y la validación de los ids ausentes.
  - **P3.1 · Cliente de Redis (2026-11-18): opción a), Lettuce directo.** El adaptador de `catedra` arma la conexión con los datos de la sesión, con `pingBeforeActivateConnection(false)` (el ACL de la cátedra rechaza `PING`), el protocolo fijo en RESP2 (el que funcionó en la lectura real, `HELLO 2 AUTH`) y los timeouts de D7. La conexión queda abierta entre ciclos, guardada junto con los datos con que se armó; si la sesión trae otros, se cierra y se arma una nueva, y se cierra al apagar el servicio. Cada versión se lee en dos idas y vueltas: el `GET` de `changes:{v}`, y los `HMGET` de los tres Hash enviados juntos (unos 0,5 s por versión con la latencia medida). Verificado en el jar de Lettuce 7.5.2: existen `ClientOptions.Builder.pingBeforeActivateConnection(boolean)` y `protocolVersion(...)`. Sin verificar: si Lettuce manda `CLIENT SETINFO` al conectarse y si tolera que el ACL lo rechace; se comprueba con un Redis de test que tenga el mismo ACL (P12) y en el E2E. Turnos no usa Redis (ni tiene la dependencia); el problema parecido que resolvió es Kafka, cuyo consumidor arma por programa con los datos del primer login (`KafkaConsumerManager`). Alternativas descartadas: b), Spring Data Redis con un `LettuceConnectionFactory` armado a mano, que agregaba una capa con opciones que usan `PING` (`validateConnection`), un ciclo de vida pensado para un bean fijo y un segundo factory; c), la conexión de Spring Boot por propiedades, imposible porque los datos llegan con la sesión, después de arrancar.
  - **P3.2 · La conexión que arma Spring Boot (2026-11-18): opción a).** Se reemplaza `spring-boot-starter-data-redis` por `io.lettuce:lettuce-core` (versión manejada por Spring Boot, 7.5.2) y se quita `spring-boot-starter-data-redis-test`. Desaparecen la conexión automática a `localhost:6379` y Spring Data Redis, que no se usa. Alternativas descartadas: b), excluir `DataRedisAutoConfiguration` y sus parientes por propiedad, que dejaba una biblioteca sin usar y una lista de nombres internos de Spring Boot que mantener; c), dejarla, con un bean engañoso disponible para inyectar.
  - **P4 · Paquete del adaptador de Redis (2026-11-18): opción a), `catedra/infrastructure/client/`.** Extiende la excepción que ya existe para los clientes hacia la cátedra (X01 P1, C01 P9): Redis es parte de la cátedra, como la API REST. El adaptador reusa los DTO de `client/dto/` y las reglas de `client/mapper/` sin salir del paquete (P5); el nombre de la clase lo distingue del cliente HTTP. Alternativa descartada: b), `infrastructure/redis/`, otra excepción, y el reuso cruzaba paquetes dentro del slice.
  - **P5.1 · Dónde viven las reglas de cada entidad (2026-11-18): opción a), un mapper de entidades común.** El valor de cada Hash de Redis tiene la misma forma que el elemento del snapshot (contrato §4.3, anexo §14.4, comprobado en la lectura real), y las reglas son lo que la copia local necesita para guardar una entidad, venga de donde venga (RF-15). Por eso se separan los dos trabajos que hoy hace `CatalogSnapshotMapper`:
    - `CatedraEntityMapper` (nuevo, `catedra/infrastructure/client/mapper/`): la revisión y la conversión de cada categoría, profesional y horario. Sale de `CatalogSnapshotMapper`, sin cambiar sus reglas.
    - `CatalogSnapshotMapper` (mismo nombre): revisa solo el sobre del snapshot (`snapshotVersion` y las tres colecciones) y usa el mapper de entidades para cada elemento.
    - `CatalogVersionChangesMapper` (nuevo): revisa `changes:{v}` (RF-14) y que el `id` de cada JSON coincida con el field del Hash (RF-15), y usa el mapper de entidades.

    Sus tests actuales siguen valiendo; se suman los del mapper de entidades y el de Redis. Alternativas descartadas: b), un solo mapper renombrado a algo neutro, que juntaba los sobres del snapshot y de Redis en una clase que crecería con cada fuente; c), un mapper propio para Redis con las reglas copiadas, que dejaba dos copias de las mismas reglas.
  - **P5.2 · Los nombres de los modelos (2026-11-18): opción b), renombrar a `Catedra…`.** Records del dominio de `catedra`: `SnapshotCategory`, `SnapshotProfessional` y `SnapshotWeeklySchedule` pasan a `CatedraCategory`, `CatedraProfessional` y `CatedraWeeklySchedule`. DTO: `Snapshot…Response` pasa a `Catedra…Response`. El prefijo dice "la entidad tal como la publica la cátedra, por el snapshot o por Redis", y la distingue de los modelos de `catalog` (la copia local). `CatalogSnapshot`, `CatalogSnapshotResponse` y `CatalogSnapshotMapper` conservan su nombre: son de verdad el snapshot. Es un renombre mecánico, sin cambio de comportamiento, que toca unos 10 archivos de C01 (los records y los DTO, `CatalogSnapshot`, `CatalogSnapshotResponse`, `CatalogSnapshotMapper`, `CatalogSnapshots` y `CatalogSnapshotTest`); se valida con la suite de C01 en verde. El usuario aceptó el prefijo `Catedra…` como el más acertado, aunque no lo convence del todo; el mapper común se llama `CatedraEntityMapper` (propuesto en lugar de `CatedraCatalogEntityMapper`, que al usuario le resultaba confuso). Alternativas descartadas: a), mantener los nombres, que dejaba un `SnapshotProfessional` dentro del mapper de Redis; c), tipos nuevos para Redis, idénticos a los existentes.
  - **P6 · Espera del login inicial (2026-11-18): opción a).** Un puerto de entrada nuevo en `catedra`, `AwaitInitialTechnicalSessionUseCase`, que consulta el estado de la sesión cada poco tiempo (200 ms por defecto) hasta que deja de estar "conectando" o se cumple el tope (2 minutos por defecto), y devuelve la sesión habilitada o vacío; los dos valores, configurables. No toca X01. Verificado: la sesión está "conectando" desde que se crea el bean (`InMemoryTechnicalSessionRepositoryAdapter`), así que una tarea que corra antes del `ApplicationReadyEvent` también espera. Es lo que hace turnos (`CatedraOperationRunner.runAfterInitialLogin`). Costo aceptado: hasta 200 ms de demora, una vez por arranque. Alternativas descartadas: b), que el login inicial avise al terminar, que modificaba X01, sumaba una pieza compartida y casos de carrera por una ganancia de 200 ms; c), que `synchronization` consulte `GetTechnicalSessionUseCase` en un bucle, que no distingue "conectando" de "degradada" (los dos dan vacío).
  - **P7 · Aplicar una versión en `catalog` (2026-11-18): opción a).** Un puerto nuevo, `ApplyCatalogVersionUseCase`, con `@Transactional` en su implementación (como `ReplaceCatalogUseCaseImpl`, P4 de C01): comprueba las referencias (D9.2), guarda las entidades de la versión con los `saveAll` existentes (actualiza o inserta por `id`, sin quitar nada), avanza la versión local y registra `INCREMENTAL` `SUCCESS` con `recordSuccess`, todo en una transacción (D5). Si una referencia no está, lanza `CatalogVersionNotVerifiableException` antes de escribir nada (cambiado por P13.1: la versión aprobada devolvía un resultado sellado); una falla de la base sale como otra excepción, como en el reemplazo. `synchronization` atrapa primero la de la referencia (discontinuidad) y después cualquier otra (falla de persistencia). Las referencias llegan solo con el id: un profesional trae una `ProfessionalCategory` con solo su `id`, y un horario un `Professional` con solo su `id`; `catalog` resuelve cada uno, primero entre las entidades de la versión y si no en la copia local (los repositorios suman una lectura por ids), y arma los objetos completos antes de guardar. Así la regla 3 del dominio queda entera en `catalog`, en la misma transacción que escribe (P1). Costo aceptado: el objeto "solo con id" existe solo en el paso de `synchronization` a `catalog`; lo cubre un test. Alternativas descartadas: b), que `synchronization` lea antes las entidades referenciadas, que partía la regla entre dos slices, leía fuera de la transacción y sumaba un puerto; c), tipos propios con ids en `catalog`, que repetían los modelos con otra forma (descartado por lo mismo en la P3 de C01).
  - **P8 · Registro y lectura del estado (2026-11-18): opción a).**
    - `GetCatalogSyncStateUseCase` (nuevo) devuelve el `CatalogSyncState`: lo usa `synchronization` para la versión local y lo reusará C07.
    - D11: `CatalogSyncState.recordFailure` deja de asignar el tipo, y `RecordSyncFailureUseCase.recordSyncFailure` pierde el parámetro del tipo, que ya no se usaría. Se ajustan C01 (`SynchronizeFullCatalogUseCaseImpl`) y sus tests (con el OK del usuario).
    - D4: `CatalogSyncState` suma `recordUpToDateCheck(instante)`, que solo cambia el estado si el último resultado fue `ERROR` (queda `SUCCESS`, el instante y el tipo de error vacío; no cambian la versión, el último éxito ni el tipo). Lo llama un puerto nuevo, `RecordUpToDateCheckUseCase`, en su propia transacción. La regla vive con el estado, como `recordSuccess` y `recordFailure`, y la decisión y la escritura van juntas.

    Alternativa descartada: b), que `synchronization` lea el estado, decida y después registre, que dejaba una regla del estado fuera de `catalog` y separaba la lectura de la escritura.
  - **P9 · El procedimiento incremental (2026-11-18): opción a), dentro de `VerifyCatalogVersionUseCaseImpl`.** El recorrido de las versiones, la aplicación de cada una, el reintento ante una falla de la base y el pedido de C01 van en métodos privados del caso de uso de C03, probados por su comportamiento. Si C02 lo comparte, se decide en su PLAN, después de su SPEC (D10). Sigue los criterios del usuario: cada slice tiene lo que necesita, y una pieza común se extrae cuando la necesita una segunda operación (como la lógica de sesión en turnos). Costo aceptado: un caso de uso largo, y C02 tendrá que mover código y tests si decide compartirlo. Alternativa descartada: b), un puerto `ApplyPendingVersionsUseCase` desde ahora, diseñado antes de saber qué pide C02 y con un solo usuario.
  - **P10 · Políticas de reintento (2026-11-18): opción a).**
    - Redis: `CatalogRedisPolicy` (record en `catedra/domain/model`, junto a `CatalogSnapshotPolicy`), armada por `CatedraClientConfiguration` desde `catalogo.catedra.redis` con los valores de D7 (3 intentos, 1 s de espera inicial, multiplicador 2, 5 s para conectar y 2 s por comando). El reintento lo hacen los casos de uso de `catedra`, como con el snapshot.
    - Espera del login inicial (P6): `catalogo.catedra.initial-login`, con el tope (2 min) y el intervalo de consulta (200 ms).
    - Falla de la base al aplicar una versión: una política propia en `synchronization`, con su sección `catalogo.synchronization.verification`, que incluye el intervalo (5 min, D6) y los reintentos de persistencia, con los mismos valores por defecto que Redis (D7). La reintenta `synchronization`, que es quien llama a `catalog`, fuera de la transacción que falló.

    Costo aceptado: los mismos números por defecto quedan escritos dos veces; se ven juntos en la tabla de propiedades del `CLAUDE.md`. Alternativas descartadas: b), reusar la política de Redis para la base, con un nombre que miente y un cambio pensado para Redis que cambiaría sin querer los reintentos de PostgreSQL; c), reintentar dentro de `ApplyCatalogVersionUseCase`, imposible dentro de la misma transacción sin partir `catalog` en dos piezas.
  - **P11 · El disparador periódico (2026-11-18): opción a), `synchronization/infrastructure/scheduling/`.** `CatalogVerificationScheduler`, con `@Scheduled` y espera fija (el intervalo de D6), primera corrida al arrancar; `@EnableScheduling` en `catalogo.config.SchedulingConfig` (excepción existente del paquete `config`). Con una sola tarea alcanza el hilo que Spring usa por defecto. Se apaga con `catalogo.synchronization.verification.enabled` (encendida por defecto): Surefire la apaga en todos los tests, también en `-Pe2e`, y la encienden solo los de C03; si no, cada test que levanta la aplicación dispararía C01 al arrancar con la base vacía y cambiaría la base de los tests de C01, y el E2E de C01 vería su pedido "descartado". Turnos hace lo mismo con sus tareas. El disparador es un adaptador de entrada, como un controller disparado por el tiempo: su lugar es la infraestructura. Alternativas descartadas: b), reusar el nombre `infrastructure/startup/`, que describe un disparador al arrancar y confundía con una tarea periódica; c), el `@Scheduled` en `SynchronizationService`, que mezclaba el "cuándo" con el "qué" y, al apagarlo en los tests, apagaba el service que usa el E2E.
  - **P12.1 · Redis en los tests automáticos (2026-11-18): opción a), Redis real en contenedor.** Testcontainers (`GenericContainer`, ya en el proyecto) con Redis 7.4, la versión de la cátedra, y un usuario con un ACL como el de la cátedra: lectura sobre `catedra:sync:*` y `PING` prohibido. Cada test carga sus datos. Timeouts con `CLIENT PAUSE` desde una conexión de administración; Redis caído, con el contenedor detenido o un puerto cerrado. Los errores que no se pueden provocar a demanda (`LOADING`, `BUSY`) se prueban en la clasificación del adaptador con un test unitario, y los casos de uso con un cliente falso que responde en orden, como `ScriptedSnapshotClient`. Prueba lo más riesgoso de C03: el handshake de Lettuce frente al ACL (`PING`, `CLIENT SETINFO`) y la lectura en lote. Costo aceptado: un contenedor más en la suite y la descarga de la imagen la primera vez. Alternativas descartadas: b), un servidor falso que hable el protocolo de Redis, que obliga a programarlo y no imita el ACL ni el handshake; c), solo tests unitarios, que dejaban la conexión, el lote y el ACL para el E2E.
  - **P12.2 · Los escenarios del E2E (2026-11-18): opción a), preparar la base desde el test.** Con la base vacía, la verificación del arranque carga el snapshot y la versión local llega a `currentVersion`; después el test baja con SQL la versión local a `currentVersion − 3` (dentro de la ventana) y pide una verificación, que aplica tres versiones de forma incremental y termina en `currentVersion` con tipo `INCREMENTAL` (los datos ya son los de la versión actual, y aplicar de nuevo esas versiones los deja iguales); por último la baja a `oldestAvailableVersion − 1` y pide otra, que reconstruye con el snapshot (tipo `FULL`). Pedidos por corrida: un login técnico, dos o tres `GET` del snapshot y lecturas de Redis; la lista exacta se le pasa al usuario antes de correrlo. Limitación aceptada: el atraso se fabrica bajando la versión, porque la cátedra no permite provocarlo de otra forma; queda explicado en el TASKS. Alternativas descartadas: b), esperar una versión nueva real, de una hora o más por corrida y sin el escenario de la discontinuidad; c), solo el escenario de la base vacía, que dejaba las dos evidencias del enunciado §11 sin la cátedra real.
  - **P13.1 · Cómo informa `catalog` una referencia rota (2026-11-18): opción a), con la jerarquía de excepciones de la skill.** `CatalogException` (base del slice, `application/exception`) y `CatalogVersionNotVerifiableException`, que la extiende. `ApplyCatalogVersionUseCase` no devuelve nada, como `ReplaceCatalogUseCase`, y lanza la excepción si una referencia no está; la comprobación va antes de escribir, así que la transacción no tiene nada que deshacer. Cambia ese detalle de P7. Es la forma que define la skill (§2.9), así que no hace falta otra excepción, y la base la necesitarán los endpoints de C04 a C06. Alternativa descartada: b), el resultado sellado de P7, que era una excepción a la skill en un slice que hoy no tiene ninguna de ese tipo.
  - **P13 · Excepciones a la skill (2026-11-18), autorizadas por el usuario para C03:**
    1. `catedra/infrastructure/client` también para el cliente de Redis (P4); extiende la de X01 y C01.
    2. Puerto de salida para una capacidad técnica: `CatalogRedisClient`, como `CatalogSnapshotClient`.
    3. `record` y resultados sellados en `catedra`: `CatedraCategory`, `CatedraProfessional` y `CatedraWeeklySchedule` (ya autorizados como `Snapshot…`), `CatalogMetadata`, `CatalogVersionChanges`, `CatalogMetadataResult`, `CatalogVersionChangesResult`, `RedisReadResult`, `CatalogRedisPolicy` e `InitialLoginWait`: datos inmutables leídos en paralelo y resultados con varios casos, como los del snapshot.
    4. `record` y resultados sellados en `synchronization`: `VerificationResult` y `VerificationPolicy`, como `FullSyncResult`.
    5. Paquete nuevo `synchronization/infrastructure/scheduling` (P11).
    6. `@Configuration` dentro de la infraestructura de un slice: `VerificationConfiguration`, como `CatedraClientConfiguration` en X01.
    7. `SchedulingConfig` en `catalogo.config`, dentro de la excepción de X01 para ese paquete.
    8. Paquete `catedra/application/support` para `CatalogRedisReader` (P14), como el `application/support` de `synchronization` para el candado (C01).

    No son excepciones: la fachada (P1), `@Transactional` en los casos de uso de `catalog`, la lectura por ids de los repositorios (método fuera del CRUD, como `saveAll`) y `CatalogException` con su subclase (P13.1). Como pide el usuario, el código no lleva comentarios que marquen excepciones: se listan en el `CLAUDE.md`.
  - **P14 · Lo común de las dos lecturas de Redis (2026-11-18): opción a), una pieza común.** `CatalogRedisReader`, en `catedra/application/support/`: pide la sesión (o la renovación sin sesión), hace el intento con el cliente, renueva una vez ante `WRONGPASS`, `NOAUTH` o `NOPERM`, reintenta lo no disponible con `CatalogRedisPolicy`, registra cada intento y devuelve el resultado resuelto. `GetCatalogMetadataUseCaseImpl` y `GetCatalogVersionChangesUseCaseImpl` le pasan qué lectura hacer y traducen a su propio resultado. Lo distinto de cada lectura (comandos, validación y lo que devuelve) vive en el adaptador y los mappers; sin la pieza común, cada caso de uso sería casi entero la misma secuencia (unas 50 líneas repetidas). Sigue el criterio del usuario de ampliar en lugar de duplicar. Quién la reusa: C02 usa directamente los dos casos de uso; una lectura nueva con las mismas reglas suma un método al cliente y un caso de uso corto; C07 (un intento, sin renovar ni reintentar) obliga a ampliarla o a una variante mínima, a decidir en su PLAN. Alternativas descartadas: b), duplicar la secuencia en los dos casos de uso; c), unificarla también con el snapshot, que reacciona distinto (un `401` y otra política) y obligaba a modificar y volver a probar `GetCatalogSnapshotUseCaseImpl` de C01.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados, respeta la skill hexagonal-vertical-slicing y permite
demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
