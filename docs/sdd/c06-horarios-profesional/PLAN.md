# PLAN: C06 — Consultar horarios semanales vigentes de un profesional

**SPEC de referencia:** `docs/sdd/c06-horarios-profesional/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-22, con el ajuste S1 que salió de este PLAN, aprobado por el usuario ese mismo día (rama `horarios-profesional`, sin commitear).
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

Las rutas de código son relativas a `src/main/java/catalogo/`; las de test, a `src/test/java/catalogo/`.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| Controller de profesionales | `catalog/infrastructure/web/controller/ProfessionalController.java` | `@RequestMapping("/api/professionals")` con el `GET` de C05. Arma el `503` `CATALOG_NOT_INITIALIZED` dentro del método. C06 suma su método aquí (P6 de C05) |
| Service de profesionales | `catalog/application/service/ProfessionalService.java` | Un método, el de C05. C06 suma el suyo |
| Caso de uso de C05 | `catalog/application/usecases/SearchProfessionalsUseCaseImpl.java` | `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`; lanza `CatalogNotInitializedException`; ordena los horarios con un `Comparator` privado (`SCHEDULE_ORDER`: día, inicio, `id`) |
| Tipo del dominio | `catalog/domain/model/ProfessionalWithSchedules.java` | `Professional` más su lista de `WeeklySchedule`. Lo devuelve C06 (P4.1 de C05) |
| Puertos de salida | `catalog/domain/ports/out/ProfessionalRepository.java`, `WeeklyScheduleRepository.java` | `ProfessionalRepository` tiene `findAllByIds` (C03), `countMatching` y `findMatching`, sin `findById`. `WeeklyScheduleRepository.findAllByProfessionalIds` (C05) sirve tal cual |
| Persistencia | `catalog/infrastructure/persistence/entity/ProfessionalEntity.java`, `WeeklyScheduleEntity.java` | `@ManyToOne(optional = false)` sin `fetch`, o sea `EAGER`: leer un profesional trae su categoría, y cada horario trae su profesional |
| Mapper web | `catalog/infrastructure/web/mapper/ProfessionalDtoMapper.java` | `toResponse(ProfessionalWithSchedules)` es privado; arma el objeto que C06 devuelve |
| Respuestas | `catalog/infrastructure/web/dto/ProfessionalResponse.java`, `ProfessionalCategorySummaryResponse.java`, `WeeklyScheduleResponse.java` | El objeto de C05, que C06 devuelve tal cual. Las horas salen `HH:mm:ss` (lo fijan los tests de C05) |
| Validación propia | `catalog/infrastructure/web/dto/WholeNumber.java` | Entero escrito con `-?\d+` (solo dígitos ASCII) dentro de un rango. `@Target(FIELD)`. Es la regla de D1 |
| Excepciones del slice | `catalog/application/exception/CatalogException.java`, `CatalogNotInitializedException.java` | La base del slice y el "sin inicializar". El "no encontrado" de C06 sería la segunda subclase |
| Manejador de errores | `config/ApiExceptionHandler.java` | Sobrescribe `handleMethodArgumentNotValid` (el `400` de C05) y atrapa lo no previsto (`500`). No sobrescribe `handleHandlerMethodValidationException` |
| Seguridad | `config/SecurityConfig.java` | La ruta de C06 ya exige un token (`anyRequest().authenticated()`); no se toca |
| Tests de seguridad que cambian | `config/ProtectedRoutesSecurityTest.java` | `aValidTokenReachesTheRouteWhateverItsRoles` (4 casos con `/api/professionals/15` esperan `404`) y `aNonNumericProfessionalIdWithAValidTokenReachesTheRoute` (espera `404`). `ProfessionalService` es un `@MockitoBean` |
| Tests de seguridad que no cambian | `config/CorsSecurityTest.java`, `config/SecurityIntegrationTest.java`, `config/SecurityWithoutKeyIntegrationTest.java` | Usan la ruta de C06 solo con consultas previas de CORS o sin un token aceptado (`401`); nada llega al controller. El `CLAUDE.md` dice que `CorsSecurityTest` cambia: es inexacto |
| Tests de C05, modelos de los nuevos | `catalog/infrastructure/web/controller/ProfessionalControllerTest.java`, `catalog/ProfessionalsIntegrationTest.java`, `catalog/ProfessionalsConsistencyIntegrationTest.java`, `catalog/application/usecases/SearchProfessionalsUseCaseImplTest.java`, `catalog/infrastructure/persistence/adapter/ProfessionalSearchRepositoryAdaptersTest.java`, `catalog/infrastructure/web/mapper/ProfessionalDtoMapperTest.java` | La falla de la base se provoca renombrando una tabla (`assertInternalErrorWhileTheTableIsRenamed`); la foto única, con `@MockitoSpyBean` y un cambio confirmado desde otra conexión, en el mismo hilo |
| Apoyo de tests | `TestTokens.java` (`withSubject`, `withAuth`), `catalog/CatalogTables.java` (`insertCategory`, `insertProfessional`, `insertWeeklySchedule`, `initializeAt`), `config/TestSecurityConfiguration.java` | Se usan sin cambios |
| Configuración | `src/main/resources/application.yaml` | Sin propiedades de Hikari ni de Tomcat: el pool espera una conexión 30 s y Tomcat tiene 200 hilos, los valores por defecto (D6.3) |

Hechos comprobados el 2026-11-22 para este PLAN, con una prueba descartable: un `@WebMvcTest` con la seguridad real (`TestSecurityConfiguration`), el manejador real y un controller de prueba con cuatro formas de recibir el id. Cada pedido llevaba un token válido con `sub` `42`. Se corrió sola dos veces, se leyó la salida y se borró, junto con su clase compilada y su reporte; `git status` quedó solo con `docs/sdd/c06-horarios-profesional/`.

| Forma de recibir el id | Qué pasó |
| --- | --- |
| `@PathVariable Long` | `15`, `015`, `-1` bien. **`%2015` (" 15"), `1%205` ("1 5"), `15%20` ("15 "), `%2B15` ("+15"), `0xF`, `%23F` ("#F"), `%0915` (tabulación) y `%D9%A4%D9%A2` (dígitos árabes, "٤٢") se leen como 15 o 42.** `abc`, `1.0`, `9223372036854775808` y `%0115`: `400` sin `code` ni `fieldErrors`, con el valor recibido en el `detail` (`MethodArgumentTypeMismatchException`); con `%0115`, el `detail` trae el carácter de control |
| `@Valid @ModelAttribute` con un `String` y `@Pattern` | Toma la variable de la ruta, y lo inválido da exactamente el `400` de D2: `VALIDATION_ERROR` con `fieldErrors` `professionalId` (`MethodArgumentNotValidException`, el manejador de C05). **Pero un parámetro de consulta con el mismo nombre le gana a la ruta:** `/15?professionalId=99` leyó 99, y `/15?professionalId=abc` dio `400` |
| `@PathVariable String` con `@Pattern` en el parámetro | Spring valida el parámetro solo (`HandlerMethodValidationException`): `400` con `"detail": "Validation failure"`, sin `code` ni `fieldErrors`, porque el manejador no lo sobrescribe. El parámetro de consulta no interviene. El número de 20 dígitos pasa `@Pattern` (el rango lo controlaría `WholeNumber`) |
| `Principal` en el método | Es un `JwtAuthenticationToken`, y `getName()` devuelve el `sub` (`42`). No hace falta importar Spring Security en el controller |

Además, en la misma prueba:

- **Sin el id, con barra final** (`/probe/long/`, `/probe/model/`): `404` "No static resource", sin `code`. Una variable de ruta no toma un valor vacío. Confirma lo deducido en D3.1 de la SPEC.
- **La seguridad rechaza ciertas URL antes de llegar a cualquier ruta**: `400` **sin cuerpo**, también en una ruta pública (`/api/professionals/%0A`). Pasó con `%0A` y `%0D` (saltos de línea), `%00` (nulo), `%2F` (barra), `%5C` (barra invertida), `%25` (`%`), `%3B` (`;`) y `%2E` (punto). Son los caracteres que rechaza `StrictHttpFirewall`, el filtro de Spring Security que viene activo por defecto (deducido de la lista; lo registra en `DEBUG`, así que no se vio en el log). `%09`, `%01` y `%7F` no los rechaza: llegan al controller. Con el servidor real, Tomcat podría rechazar alguno antes (sin comprobar). Ver S1.

**Convenciones y slice de referencia:** el slice `catalog` y su parte web de C05 (controller, service, request validado, mapper y respuestas), con las excepciones ya autorizadas en el `CLAUDE.md`. La skill pide, para "Get by id", `@GetMapping("/{id}")`, un caso de uso que devuelve `Optional`, y un service que convierte el vacío en la excepción del slice (§5.2 y §5.3). Turnos es solo un antecedente.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de la solicitud, el evento o la tarea desde que entra hasta la
respuesta, la persistencia o el mensaje publicado. Referencia el diagrama de
secuencia del proyecto si ya lo describe; usa un diagrama nuevo solo si aporta
claridad. -->

C06 es un "Get by id" con el molde de la skill, dentro del slice `catalog`, que reutiliza el objeto, el mapper y la foto única de C05. La seguridad no cambia: el token se valida antes de todo (RF-03). Lo nuevo fuera del slice es el `400` de un parámetro validado en `ApiExceptionHandler` (P1).

Recorrido de un pedido (las piezas salen de P1 a P5):

```
GET /api/professionals/{professionalId}, Authorization: Bearer <jwt>
└─ Seguridad (config, sin cambios)
   ├─ URL con caracteres que no acepta → 400 sin cuerpo, sin línea en el log             (RF-04, S1)
   └─ sin token o inválido → 401, sin línea en el log                                       (RF-03)
└─ Spring MVC valida el parámetro (@WholeNumber sobre el @PathVariable String)            (P1)
   └─ inválido → ApiExceptionHandler: 400 VALIDATION_ERROR con fieldErrors
                 y la línea "Invalid parameters … requested by user {sub}: professionalId"  (RF-04, RF-09, P4)
└─ ProfessionalController.getProfessional(professionalId, principal)                       web/controller
   ├─ Long.valueOf(professionalId)
   ├─ ProfessionalService.getProfessional(id)                                              application/service
   │  └─ GetProfessionalUseCase = GetProfessionalUseCaseImpl                               application/usecases
   │     │  @Transactional(readOnly, REPEATABLE_READ): una sola foto                       (RF-08, P5)
   │     ├─ CatalogSyncStateRepository.get(): sin versión → CatalogNotInitializedException (RF-05)
   │     ├─ ProfessionalRepository.findById(id): vacío → Optional.empty()                  (P2)
   │     └─ WeeklyScheduleRepository.findAllByProfessionalIds(List.of(id)),
   │        ordenados con WeeklySchedule.BY_DAY_START_AND_ID                               (P3)
   │     → Optional<ProfessionalWithSchedules>
   │  vacío → ProfessionalNotFoundException                                                (RF-06, P2)
   ├─ ProfessionalDtoMapper.toResponse(item) → 200 con el objeto de C05                    (RF-01, RF-02)
   ├─ ProfessionalNotFoundException → 404 PROFESSIONAL_NOT_FOUND
   ├─ CatalogNotInitializedException → 503 CATALOG_NOT_INITIALIZED
   ├─ error no previsto (la base) → sale sin atrapar → ApiExceptionHandler: ERROR con la traza y 500 (RF-07)
   └─ finally: "Professional {id} requested by user {principal.getName()}: {resultado}"   (RF-09, P4)
```

- Solo objetos del dominio viajan entre el controller, el service, el caso de uso y los adaptadores; el `sub` no entra a `application`.
- El orden de los chequeos es el de la SPEC: `401`, `400`, `503`, `404`.

## Slices y componentes afectados

<!-- Aplica la skill hexagonal-vertical-slicing: un paquete por slice, con sus
capas domain, application e infrastructure. Identifica los slices afectados, sus
responsabilidades y la dirección de sus dependencias. Una funcionalidad puede
afectar más de un slice. Justifica cualquier slice nuevo o dependencia nueva
entre slices. -->

| Slice | Existe / nuevo | Responsabilidad y cambios | Dependencias con otros slices |
| --- | --- | --- | --- |
| `catalog` (`catalogo/catalog/`) | Existente | El endpoint de C06, su caso de uso, sus lecturas, la excepción del "no encontrado" y el orden compartido de los horarios (P1 a P5) | Ninguna nueva: no usa otros slices. `synchronization` sigue usando el dominio de `catalog` por la fachada, sin cambios |
| `config` (`catalogo/config/`) | Existente | El `400` de un parámetro validado y su línea del log en `ApiExceptionHandler` (P1, P4). La seguridad no cambia | Ninguna: no importa los slices |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son
propuestas. Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Slice y capa | Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- | --- |
| catalog · web/controller | `ProfessionalController` | Modificar | Suma el método de `GET /{professionalId}`, que recibe el id como `@PathVariable @WholeNumber String` (P1), lo convierte a `Long` y llama al service. Atrapa `ProfessionalNotFoundException` (`404` `PROFESSIONAL_NOT_FOUND`) y `CatalogNotInitializedException` (`503`); el armado del `503` pasa a un método privado que comparte con C05 (P2). Recibe el `sub` por `Principal` y escribe al terminar, en un `finally`, una línea `INFO` con el id, el `sub` y el resultado: encontrado con N horarios, no existe, catálogo sin inicializar o falla (P4) | RF-01, RF-04, RF-05, RF-06, RF-09 |
| catalog · web/mapper | `ProfessionalDtoMapper` | Modificar | `toResponse(ProfessionalWithSchedules)` pasa a público, para que C06 arme su respuesta con el mismo método que cada elemento de C05 (P2) | RF-01 |
| catalog · web/dto | `ProfessionalResponse`, `ProfessionalCategorySummaryResponse`, `WeeklyScheduleResponse` | Reutilizar | El objeto de C05, sin cambios | RF-01 |
| catalog · domain/ports/in | `GetProfessionalUseCase` | Crear | `Optional<ProfessionalWithSchedules> getProfessional(Long professionalId)` (P2) | RF-01, RF-02, RF-05, RF-06 |
| catalog · application/usecases | `GetProfessionalUseCaseImpl` | Crear | Sin versión local, lanza `CatalogNotInitializedException`; si el profesional no existe, devuelve vacío; si existe, el profesional con sus horarios (P2), ordenados con `WeeklySchedule.BY_DAY_START_AND_ID` (P3). Todo en una transacción de solo lectura con `REPEATABLE READ` (P5) | RF-01, RF-02, RF-05, RF-06, RF-08 |
| catalog · domain/model | `WeeklySchedule` | Modificar | Suma `BY_DAY_START_AND_ID` (nombre propuesto), un `Comparator` público y constante por día de la semana, hora de inicio e `id` (P3). Lombok no lo toca, porque es estático | RF-01 |
| catalog · application/usecases | `SearchProfessionalsUseCaseImpl` | Modificar | Reemplaza su `SCHEDULE_ORDER` privado por `WeeklySchedule.BY_DAY_START_AND_ID`, sin cambio de comportamiento (P3) | — (RF-01 de C05) |
| catalog · application/exception | `ProfessionalNotFoundException` | Crear | Subclase de `CatalogException`, con el id en el mensaje (P2) | RF-06 |
| catalog · application/service | `ProfessionalService` | Modificar | Suma `getProfessional(Long)`, que convierte el vacío en `ProfessionalNotFoundException` con `orElseThrow` (P2) | RF-06 |
| catalog · domain/ports/out | `ProfessionalRepository` | Modificar | Suma `Optional<Professional> findById(Long id)` (P2) | RF-01, RF-06 |
| catalog · persistence/adapter | `JpaProfessionalRepositoryAdapter` | Modificar | Implementa `findById` con `findById(id).map(professionalMapper::toDomainModel)`, el molde de la skill (P2) | RF-01, RF-06 |
| catalog · domain/ports/out | `WeeklyScheduleRepository.findAllByProfessionalIds` | Reutilizar | Con `List.of(id)` (P2) | RF-01, RF-02 |
| catalog · web/dto | `WholeNumber` | Modificar | Su `@Target` suma `PARAMETER`, para usarla sobre el parámetro (P1). La regla y el validador no cambian | RF-04 |
| config | `ApiExceptionHandler` | Modificar | Sobrescribe `handleHandlerMethodValidationException`: `400` `VALIDATION_ERROR` con el `detail` fijo de C05 y un `fieldErrors` por parámetro, con su nombre y el mensaje de la regla (P1). Escribe una línea `INFO` con el método, la ruta como plantilla, el `sub` del pedido y los nombres de los campos, nunca sus valores (P4) | RF-04, RF-09 |
| tests · catalog/application/usecases | `GetProfessionalUseCaseImplTest` | Crear | Unitario con los puertos simulados con Mockito, como el de C05 (P6) | CA-01, CA-03, CA-06, CA-07 |
| tests · catalog/infrastructure/persistence/adapter | `CatalogRepositoryAdaptersTest` | Modificar | Suma `findById` contra PostgreSQL (P6) | CA-01, CA-07 |
| tests · catalog/infrastructure/web/mapper | `ProfessionalDtoMapperTest` | Modificar | Suma el `toResponse` público, con datos sin nulos (P6) | CA-01 |
| tests · catalog/infrastructure/web/controller | `ProfessionalControllerTest` | Modificar | Suma los casos web de C06 y sus líneas del log (P6) | CA-01, CA-05, CA-06, CA-07, CA-10 |
| tests · config | `ProtectedRoutesSecurityTest` | Modificar | Cambian a propósito dos tests: con un token válido, `/api/professionals/15` da `200` (con el service simulado) y `/api/professionals/abc`, `400` `VALIDATION_ERROR` (P6) | CA-04, CA-05 |
| tests · catalog | `ProfessionalsIntegrationTest` | Modificar | Suma los escenarios de C06 con el servidor real, reutilizando sus cuatro profesionales de prueba, la ayuda que renombra tablas y la cátedra simulada como testigo (P6, P6.1) | CA-01 a CA-08, CA-10 |
| tests · catalog | `ProfessionalsConsistencyIntegrationTest` | Modificar | Suma un método para la foto única de C06, con el espía que ya tiene (P6) | CA-09 |
| tests · config | `SecurityIntegrationTest` | Modificar | `anyOtherRouteWithoutATokenIsRejectedWithoutData` inserta el profesional 15, así "sin datos" prueba algo (P6) | CA-04 |
| — | `CLAUDE.md` | Modificar | La fila de `catalog`; las excepciones de C06; "Para un endpoint protegido nuevo", corrigiendo lo de `CorsSecurityTest`; en "Cómo responde un error", el `400` de un parámetro validado y el aviso de no poner `@Validated` en un controller; lo comprobado con turnos real; los tests; la carpeta de C06 (P8.2) | — |
| docs | `docs/decisiones/005-manejador-global-de-errores.md` | Modificar | El punto 4 suma el `400` de un parámetro validado y su línea del log (P8.2) | RF-04, RF-09 |
| docs | `docs/decisiones/006-resultados-sellados-o-excepciones.md` | Modificar | La tabla "Dónde se usa cada uno hoy" suma `ProfessionalNotFoundException`, una excepción del slice que solo lee su controller (P8.2) | RF-06 |
| docs | `docs/decisiones/007-seguridad-del-servicio.md` | Modificar | La trazabilidad pasa de "lo va a escribir en C06" a cómo lo escribe (P8.2) | RF-09 |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Operaciones, eventos y DTO de entrada y salida:** `GET /api/professionals/{professionalId}` (contrato §3.1). La respuesta es la de C05, sin cambios. El contrato no cambia.
- **Identificadores, relaciones y restricciones:** el `professionalId` con la regla de D1; el `sub` ya validado por la seguridad.
- **Origen de los datos devueltos o publicados y transformaciones:** la copia local (C01 a C03). El orden de los horarios, como en C05 (D6.1); dónde vive: P3.
- **Persistencia, consultas y migraciones:** sin migraciones. Las lecturas son `ProfessionalRepository.findById`, nuevo, y `WeeklyScheduleRepository.findAllByProfessionalIds`, que ya existe (P2).
- **Convivencia entre datos locales y remotos:** no aplica: C06 no consulta a la cátedra.
- **Compatibilidad y migraciones de datos existentes:** el cliente de turnos lee la respuesta sin cambios (restricción de la SPEC; CA-11, P7).

## Transacciones, concurrencia y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de BACKEND_GUIDELINES.md. -->

- **Estados y transiciones:** no aplica.
- **Transacciones y consistencia:** `GetProfessionalUseCaseImpl` con `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`, como C05 (P5). PostgreSQL toma la foto en la primera consulta, así que el estado, el profesional con su categoría y sus horarios salen de la misma versión, aunque una sincronización confirme en el medio (RF-08). No escribe nada.
- **Idempotencia y mensajes duplicados:** no aplica: solo lee.
- **Concurrencia y tareas programadas:** cada consulta es independiente; una sincronización en curso no la bloquea (RF-08).
- **Timeouts, reintentos y errores de dependencias:** sin dependencias externas. La base, sin límite propio (D6.3). El `500` por `ApiExceptionHandler`, como C04 y C05 (D5).
- **Seguridad y autorización:** sin cambios en la seguridad. El `sub` llega al controller por `Principal` (comprobado) y solo se usa para el log (P4); `application` no lo recibe.
- **Otras consideraciones de backend aplicables y su solución:** el log del `sub` (RF-09): una línea `INFO` del controller o del manejador por cada pedido que pasó la seguridad, nunca con el token ni con el valor de un id inválido (P4).

## Dependencias y configuración

<!-- Librerías, servicios externos, propiedades de configuración y secretos
afectados. Verifica compatibilidad con el proyecto y justifica las incorporaciones.
No agregues dependencias por defecto. Los valores sensibles y los parámetros
ajustables se externalizan. -->

- Ninguna dependencia ni propiedad nueva.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, de integración (base de datos, mensajería), de API o prueba manual.
No todos requieren todos los métodos. Identifica tests existentes y separa los
nuevos propuestos. Incluye regresiones relevantes. Una prueba con mocks no
demuestra persistencia, transacciones ni idempotencia. -->

Los nombres de los tests describen el comportamiento, sin citar documentos. Nada depende de la velocidad de la máquina: ningún test detiene ni pausa la base (la falla se provoca renombrando una tabla, como en C05), la foto única se fuerza en el mismo hilo con un espía, y los tokens de las integraciones vencen en una hora (P6).

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Unitario del caso de uso (horarios cargados fuera de orden); adaptador (`findById` con su categoría); mapper; web (JSON exacto y `?x=1`); integración (igual al elemento de C05) | PostgreSQL de Testcontainers; `CatalogTables` | `200` con todos los campos y los horarios ordenados, idéntico a C05 |
| CA-02 | Integración | Un profesional deshabilitado y uno de una categoría deshabilitada | `200` con sus `enabled` |
| CA-03 | Unitario e integración | Un profesional sin horarios | `weeklySchedules: []` |
| CA-04 | `ProtectedRoutesSecurityTest` (cambia) e integración; `SecurityIntegrationTest` (ajuste) | `TestTokens` con distintos `auth`; el profesional 15 en la base | `401` sin datos; `200` con cualquier `auth` |
| CA-05 | Web (los ids de CA-05; el service no se llama; `015` llega como 15) e integración (`abc`, `%0A15`, `/api/professionals/` con el servidor real) | Copia inicializada y sin inicializar | `400` con la forma de D2 y sin el valor; `400` sin cuerpo; la respuesta de una ruta inexistente |
| CA-06 | Unitario (no lee al profesional) e integración (la cátedra simulada como testigo) | Sin versión local | `503` `CATALOG_NOT_INITIALIZED`; ningún pedido a la cátedra |
| CA-07 | Unitario, adaptador, web e integración (`0`, `-1` y un id inexistente) | — | `404` `PROFESSIONAL_NOT_FOUND` |
| CA-08 | Integración, renombrando la tabla de profesionales y la de horarios, y restaurándolas en un `finally` | — | `500` `INTERNAL_ERROR` sin detalles; el error con la traza en el log |
| CA-09 | `ProfessionalsConsistencyIntegrationTest`: después de `findById`, el espía confirma desde otra conexión un cambio del profesional y de sus horarios | Mismo hilo | La versión anterior entera |
| CA-10 | Web (cada línea) e integración (las cinco juntas, con el servidor real), con `OutputCaptureExtension` | Token con `sub` `42` | Una línea `INFO` por pedido con `42`; el inválido sin su valor; el error de la falla; nada en los rechazos; nunca el token |
| CA-11 | Prueba manual que corre el usuario (P7) | Turnos y catálogo en local, sin la cátedra | Lo que dice CA-11, en un archivo de resultado sin el token |

**Pruebas en negativo previstas:** quitar `REPEATABLE READ` (falla CA-09); no ordenar en C06 (falla el orden de CA-01); quitar `@WholeNumber` del parámetro y, aparte, el método nuevo del manejador (fallan los `400` de CA-05); quitar el `sub` de la línea o el `finally` (falla CA-10); que `findById` devuelva siempre vacío (falla CA-01). Cada una se restaura y se comprueba el hash.

**Comprobaciones de regresión:** la suite completa (728 casos al cerrar la validación del JWT), en especial los tests de C05 (el orden de sus horarios con el comparador compartido, su `400` y su `503`), los de seguridad y los tres E2E, que no llaman a C06; los cinco chequeos de la skill.

**Pruebas con infraestructura real o simulada:** PostgreSQL de Testcontainers en el adaptador y las integraciones; la cátedra simulada como testigo de que C06 no la llama; los tokens de `TestTokens`, firmados como los de turnos. La integración real con turnos es la prueba manual de P7.

**Limitaciones del entorno:** con el servidor real, `%0A15` podría rechazarlo Tomcat antes que la seguridad, con otro cuerpo (sin comprobar): si pasa, se trae como hallazgo.

**Comandos verificados para compilar y ejecutar tests:** `./mvnw test`, `./mvnw test -Dtest='Clase1,Clase2'` y `./mvnw clean verify`, documentados en el `CLAUDE.md`. `./mvnw -q test -Dtest=…` se usó en la prueba descartable de este PLAN.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **El caso de uso y sus lecturas.** `WeeklySchedule.BY_DAY_START_AND_ID` y su uso en `SearchProfessionalsUseCaseImpl`; `ProfessionalRepository.findById` y su adaptador; `GetProfessionalUseCase` y su implementación; `ProfessionalNotFoundException`; `ProfessionalService.getProfessional`. Tests: `GetProfessionalUseCaseImplTest` y los casos de `findById` en `CatalogRepositoryAdaptersTest`. Comprobación: esos tests, los de C05 que miran el orden, y sus pruebas en negativo. Todavía nadie llama al caso de uso desde la web.
2. **La entrada web.** `WholeNumber` sobre un parámetro; el método nuevo de `ApiExceptionHandler` con su línea del log; `ProfessionalDtoMapper.toResponse` público; el método de C06 en `ProfessionalController`, con el `503` compartido y la línea del log. Tests: `ProfessionalDtoMapperTest`, `ProfessionalControllerTest` y los dos de `ProtectedRoutesSecurityTest`. Comprobación: los tests web y la suite completa, porque el manejador y el controller también los usa C05. Aquí se comprueba que el manejador recibe el nombre del parámetro, el `sub` y la plantilla de la ruta; si no, se vuelve a P1 o P4.
3. **La aplicación completa.** Los escenarios de C06 en `ProfessionalsIntegrationTest`, el método nuevo de `ProfessionalsConsistencyIntegrationTest` y el ajuste de `SecurityIntegrationTest`. Comprobación: esas clases y sus pruebas en negativo, y `%0A15` con el servidor real.
4. **La prueba con turnos real** (P7): el script, la lista de pedidos y los comandos para el usuario, y el registro de lo que observe.
5. **Cierre.** `CLAUDE.md`, `005`, `006` y `007`; `./mvnw clean verify`; los cinco chequeos de la skill; y la revisión de los criterios contra lo observado.

Ningún archivo se toca en dos etapas. Si dos tareas de una misma tanda tocan el mismo archivo, se frena entre una y otra para que el usuario commitee.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - **El manejador puede no recibir todo lo que P1 y P4 necesitan:** el nombre del parámetro, el `sub` y la plantilla de la ruta. El nombre ya apareció en un mensaje de conversión de la prueba descartable; lo demás no se vio. Se comprueba en la etapa 2; si falla, se vuelve a P1 o P4 con el usuario.
  - **`@WholeNumber` sobre el parámetro tiene que disparar la validación de Spring**, como lo hizo `@Pattern` en la prueba descartable. Es una validación propia (`@Constraint`), así que se espera lo mismo; se comprueba en la etapa 2.
  - **Con el servidor real, `%0A15` lo podría rechazar Tomcat antes que la seguridad**, con otro cuerpo. Se mira en la etapa 3; si pasa, se trae como hallazgo para la SPEC (S1).
  - **El comparador compartido podría cambiar el orden de C05:** lo detecta su test del orden, que tiene que seguir en verde sin cambios.
  - **Las líneas del log salen de dos lugares (P4):** el test de integración de CA-10 las mira juntas. La salida se captura por método, así que cada test revisa solo su propio pedido.
  - **`ProtectedRoutesSecurityTest` cambia a propósito:** solo sus dos tests con la ruta de C06 y un token válido; el resto tiene que seguir en verde sin cambios.
  - **Un `@Validated` en el controller** cambiaría el mecanismo de validación por otro que termina en `500` (deducido): queda anotado en el `CLAUDE.md`.
- **Ajuste a la SPEC aprobada:**
  - **S1 · Las URL que rechaza la seguridad (2026-11-22): opción a), se aclaró la SPEC**, con el texto aprobado por el usuario (RF-04, RF-09, la fila "Entrada inválida", CA-05 y su fila en "Cómo se comprueba"). El motivo y las alternativas están en las decisiones de la SPEC.
- **Decisiones pendientes:**
  - Ninguna.
- **Decisiones tomadas:**
  - **Aprobación (2026-11-22):** el usuario aprobó el PLAN con P1 a P8, la estrategia de validación, los riesgos y el orden de implementación.
  - **P1 · Cómo se recibe y se valida el id (2026-11-22): opción a), `@PathVariable String` con `WholeNumber` sobre el parámetro, y el `400` lo arma `ApiExceptionHandler`** (RF-04, D1 y D2). `WholeNumber` suma `PARAMETER` a su `@Target`. Spring valida el parámetro antes de entrar al método (`HandlerMethodValidationException`, comprobado con `@Pattern`), y el manejador sobrescribe `handleHandlerMethodValidationException` para responder `VALIDATION_ERROR` con el mismo `detail` fijo que el `400` de C05 y un `fieldErrors` por parámetro, con su nombre y el mensaje de la regla. El id sale solo de la ruta, con la misma regla y el mismo mensaje que `categoryId`, y el `400` se arma en el mismo lugar que el de C05. Hoy ningún otro controller valida parámetros así, así que C04 y C05 no cambian. Cuidado: el controller no lleva `@Validated`, porque con esa anotación Spring valida por otro mecanismo, que lanza `ConstraintViolationException` y terminaría en `500` (deducido, sin ejecutar). Que el nombre del parámetro llegue al manejador se comprueba en la tarea: la prueba descartable ya mostró `professionalId` en el mensaje de una conversión fallida. Probablemente sea una excepción a la skill, que solo describe la validación en un request de `web/dto`: se lista en P8. Alternativas descartadas: b), un request `@Valid @ModelAttribute` como en C05, que da el `400` sin código nuevo pero deja que `?professionalId=99` cambie el profesional pedido (comprobado), y su arreglo depende del mecanismo interno de Spring; c), `@PathVariable Long` ajustando el `400` de la conversión, que acepta `" 15"`, `"+15"`, `0xF`, `#F` y `"٤٢"` como ids, contra D1; d), validar a mano en el controller, que escribe dos veces la forma del `400` y la regla de `WholeNumber`.
  - **P2 · El caso de uso, el "no encontrado" y las lecturas (2026-11-22): opción a), el molde de la skill para "Get by id"** (§5.2 y §5.3; RF-01, RF-05, RF-06). `GetProfessionalUseCase` devuelve `Optional<ProfessionalWithSchedules>`: lanza `CatalogNotInitializedException` sin versión local, como C05, y devuelve vacío si el profesional no existe. `ProfessionalService` convierte el vacío en `ProfessionalNotFoundException`, subclase de `CatalogException`. El controller convierte el texto ya validado a `Long`, como recibe el id el controller de referencia de la skill, y traduce las dos excepciones a `404` y `503`; el armado del `503` pasa a un método privado que comparte con C05. Las lecturas: `ProfessionalRepository.findById(Long)` nuevo, el método estándar de la skill para buscar por id, y `WeeklyScheduleRepository.findAllByProfessionalIds(List.of(id))`, que ya existe. Es lo que pide la decisión 006: una excepción del slice que solo lee su controller. Alternativas descartadas: b), que el caso de uso lance también el "no encontrado", contra la regla de la skill de que el `Optional` llega al service, que traduce la ausencia, sin ganar nada y con el service como pasamanos; c), un resultado sellado (`Found`, `NotFound`, `NotInitialized`), contra la decisión 006 (nadie fuera del slice lo lee) e inconsistente con el "sin inicializar" de C05. Para las lecturas: reutilizar `findAllByIds(List.of(id))` y tomar el primero, que ahorra un método pero convierte a mano una lista en un `Optional`; y sumar `findAllByProfessionalId(Long)`, que duplica el que ya existe.
  - **P4 · Dónde se escriben las líneas del `sub` (2026-11-22): opción a), el controller escribe la de lo que pasa dentro de su método, y `ApiExceptionHandler` la del id inválido** (RF-09, D4). Lo condiciona P1: un id inválido nunca entra al método del controller, porque Spring lo rechaza antes y responde el manejador; y una falla de la base sale del controller hasta el manejador, que escribe el error con la traza.
    - El controller recibe el `sub` por `Principal` (comprobado: `getName()` devuelve el `sub`, sin importar Spring Security) y escribe una sola línea al terminar, en un `finally`, con el resultado que quedó. Si salió un error no previsto, el resultado es "failed", y la línea sale justo antes del error con la traza, en el mismo hilo, que el formato del log muestra.
    - El método nuevo del manejador (P1) escribe la línea del `400` con el `sub` del pedido, la ruta como plantilla (sin el valor) y los nombres de los campos. Es genérica: serviría para cualquier ruta futura que valide así; hoy solo la usa C06. Que el pedido le dé al manejador el `sub` y la plantilla de la ruta se comprueba en la tarea.
    - El texto, en inglés como el resto del log:
      ```
      Professional 15 requested by user 42: found with 3 weekly schedules
      Professional 999 requested by user 42: not found
      Professional 15 requested by user 42: catalog not initialized
      Professional 15 requested by user 42: failed
      Invalid parameters for GET /api/professionals/{professionalId} requested by user 42: professionalId
      ```
    - `application` no cambia: el `sub` es un dato del pedido, no del negocio. La única desventaja, que las líneas salen de dos lugares, se cubre con el test de CA-10, que mira las cinco juntas.
    - Alternativas descartadas: b), un interceptor de Spring MVC para la ruta de C06, que junta todo en un lugar a costa de dos piezas nuevas (el interceptor y su registro), una regla que traduce estados HTTP a resultados, volver a validar el id para escribirlo, y la cantidad de horarios pasada por un atributo del pedido; c), el `sub` en el contexto del log (MDC) de todo pedido autenticado, que también lo pondría en el error con la traza, pero cambia el formato del log de todo el servicio (también la sincronización, con el campo vacío), necesita una pieza que corra en cada pedido después de la seguridad, e igual requiere las dos líneas de a).
  - **P3 · Dónde vive el orden de los horarios (2026-11-22): opción a), un `Comparator` público en `WeeklySchedule`** (`BY_DAY_START_AND_ID`, nombre propuesto; RF-01, D6.1). C05 reemplaza su comparador privado por este, y C06 usa el mismo. Es una comparación pura, sin dependencias, que el modelo puede tener (skill §5.1: las reglas del concepto van en el modelo), y no suma archivos. El usuario preguntó si la recomendación cambiaba con d) sin el costo de la excepción: seguía siendo a), por poco. Lo que hoy vive en `application/support` (`CatalogCatchUp`, `SynchronizationLock`, `CatalogRedisReader`) son procedimientos con dependencias o estado, que no podían ir al dominio; una comparación pura sí puede. Riesgo aceptado: cada caso de uso tiene que acordarse de ordenar; los tests de los dos cargan los horarios fuera de orden. Alternativas descartadas: b), un método de fábrica en `ProfessionalWithSchedules` que guarda los horarios ordenados, con una garantía que el `@Builder` deja saltear y dos formas de construir el mismo objeto; c), que la persistencia los devuelva ordenados, con un `CASE` en la consulta porque el día se guarda como texto, y una regla de presentación en la base; d), una pieza en `catalog/application/support`, que la deja junto a sus únicos usuarios y fuera del modelo que comparte la sincronización, a cambio de un archivo y un paquete nuevos (y una excepción a la skill) para una constante.
  - **P5 · La foto única (2026-11-22): opción a), `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` en `GetProfessionalUseCaseImpl`, como C05 (P3 de C05)** (RF-08). Las tres lecturas ven la misma foto. Se prueba como `ProfessionalsConsistencyIntegrationTest`, confirmando un cambio desde otra conexión entre la lectura del profesional y la de sus horarios, en el mismo hilo (P6); la prueba en negativo quita el aislamiento. Alternativas descartadas: b), una transacción común (`READ COMMITTED`), donde cada consulta ve su propio momento y el cambio confirmado en el medio se mezcla (lo mostró la prueba en negativo de C05); c), una sola consulta con el profesional, su categoría y sus horarios, que suma un método de lectura con `join fetch` para lo que ya dan `findById` y `findAllByProfessionalIds`, y que igual necesita la transacción para el estado de la copia.
  - **P6 · Los tests (2026-11-22): opción a), por niveles, cada uno con lo que puede demostrar** (tabla de componentes y estrategia de validación). Unitario del caso de uso con Mockito; `findById` contra PostgreSQL; el mapper; el controller con la seguridad y el manejador reales; dos tests de `ProtectedRoutesSecurityTest` que cambian a propósito; la aplicación completa para lo que necesita el servidor real; la foto única en `ProfessionalsConsistencyIntegrationTest`; y un ajuste a `SecurityIntegrationTest` para que "sin datos" pruebe algo. Ninguno detiene ni pausa la base ni compara tiempos (lección de C04). Al proponerla se dijo que la clase nueva de integración compartía el contexto de la de C05: era inexacto, ver P6.1. Alternativas descartadas: b), todo con el servidor real, más lento, con la base a preparar en cada caso, y sin poder ver que el service no se llama en un `400`; c), solo unitarios y web, sin evidencia real de la persistencia, la transacción ni el servidor (un mock no demuestra transacciones, guía de backend).
  - **P7 · La prueba con turnos real (2026-11-22): opción a), un script en el scratchpad, con `curl` y `jq`, que corre el usuario** (CA-11, D7).
    - Arranque: catálogo con el comando de TASK-08 de la validación del JWT (sin la cátedra, sin la verificación ni Kafka, con la clave de turnos); turnos sin la cátedra ni Kafka, sin el barrido ni la reconciliación, con `TURNOS_CATALOG_BASE_URL=http://localhost:8081`. Los nombres exactos de las variables de turnos se confirman en su `application.yaml` al preparar la tarea.
    - El script registra un usuario nuevo con una contraseña generada, inicia sesión y guarda el token solo en memoria. Elige los datos desde C05, que es público: un profesional con horarios, una fecha desde mañana en un día que no atiende, una hora fuera de sus horarios en un día que atiende, un id inexistente y, si hay, un profesional deshabilitado.
    - A catálogo, con el token: C06 del profesional (`200`, comparado con su elemento de C05 con `jq`), un id inexistente (`404` `PROFESSIONAL_NOT_FOUND`), `abc` (`400`) y sin token (`401`). A turnos: T03 en la fecha sin atención (`200 []`), T04 a la hora fuera de horario (`400` `INVALID_SLOT`), T03 con el id inexistente (`404`) y, opcional, T03 del deshabilitado (`409`). Ninguno llega a la cátedra.
    - Del log de catálogo se cuentan las líneas con el `sub` del usuario (una por cada C06 con token) y las apariciones del token (tienen que ser 0).
    - Deja un archivo de resultado sin el token, que se revisa antes de leerlo; después se borran el script, el resultado y el log. Queda un usuario de prueba en la base de desarrollo de turnos; T04 no crea nada, porque falla antes de la cátedra. Antes de correrlo, el usuario recibe la lista exacta de pedidos y los comandos.
    - Alternativas descartadas (P7): b), los pedidos como comandos sueltos, que hacen pasar el token a mano por la terminal y dejan ids y fechas a elegir a mano; c), solo los pedidos directos a catálogo, que no prueban que el cliente de turnos lea la respuesta, que es lo que pide CA-11.
  - **P6.1 · Dónde van los escenarios de integración de C06 (2026-11-22): opción b), en `ProfessionalsIntegrationTest`.** Corrige lo dicho al proponer P6: una clase nueva no comparte el contexto de esa clase, porque conecta su propia cátedra simulada con `@DynamicPropertySource`. En la misma clase no se suma un arranque, y se reutilizan tal cual sus cuatro profesionales de prueba (uno deshabilitado, uno de una categoría deshabilitada y uno sin horarios), la ayuda que renombra tablas y la cátedra simulada como testigo; comparar C06 con el elemento de C05 queda en la misma clase. Es la regla del `CLAUDE.md`: varios escenarios en una clase si comparten la configuración. La clase pasa de unos 15 tests a unos 25 y mezcla dos casos de uso. Alternativas descartadas: a), una clase nueva con su propia cátedra, que suma un arranque y repite o extrae los datos y las ayudas; c), una clase nueva sin cátedra simulada, que no puede probar CA-06 y tampoco comparte contexto (la de la foto única tiene un espía, que arma otro).
  - **P8 · Excepciones a la skill y documentos (2026-11-22):**
    - **P8.1 · Una excepción nueva, autorizada por el usuario:** la validación sobre el parámetro del controller (`@PathVariable @WholeNumber String professionalId`), y no en un request de `web/dto`; el controller convierte después el texto a `Long` (P1). Quedan dentro de excepciones ya autorizadas: la ruta `/api/professionals/{professionalId}` (la de C05, que fija el contrato, con el nombre de la variable del contrato), `WholeNumber` aplicable a un parámetro (validación propia de C05) y el método nuevo con su línea del log en `ApiExceptionHandler` (manejador global de C04). No son excepciones: `Principal` como parámetro del controller (no es una inyección: el controller sigue inyectando solo el service y el mapper), la línea del log en el controller, `findById` (el método estándar de la skill), `ProfessionalNotFoundException` (la jerarquía de la skill), el `Comparator` en el modelo (una regla del concepto), el `toResponse` público (el nombre de la skill) y `@Transactional` en el caso de uso (como desde C01).
    - **P8.2 · Los documentos: opción a), `CLAUDE.md`, `005`, `006` y `007`, sin un texto de decisión nuevo** (ver la tabla de componentes). El usuario respondió "autorizo", tomado como la opción recomendada. Alternativas descartadas: b), además un texto nuevo sobre la trazabilidad del `sub`, para algo que cabe en la `007`, que ya tiene esa limitación; c), solo el `CLAUDE.md`, que deja en la `006` y la `007` afirmaciones sobre C06 que dejan de ser ciertas.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados, respeta la skill hexagonal-vertical-slicing y permite
demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
