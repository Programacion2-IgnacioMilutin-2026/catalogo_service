# PLAN: C05 — Buscar profesionales

**SPEC de referencia:** `docs/sdd/c05-buscar-profesionales/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-20, con el cambio de las comas como separador de días que salió de P5 y el usuario aprobó el mismo día (rama `buscar-profesionales`, sin commitear)
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
| Modelos de la copia | `catalog/domain/model/Professional.java`, `WeeklySchedule.java`, `ProfessionalCategory.java` | Clases Lombok. `Professional` tiene su categoría, pero no sus horarios; cada `WeeklySchedule` apunta a su profesional. Cómo viajan juntos en la respuesta: P4 |
| Persistencia de profesionales y horarios | `catalog/domain/ports/out/ProfessionalRepository.java` y `WeeklyScheduleRepository.java`, con sus adaptadores (`infrastructure/persistence/adapter/`), repositorios de Spring Data y mappers | Hoy guardan, quitan y (los profesionales) leen por ids, para la sincronización. Se amplían con la búsqueda, sin piezas paralelas (P2, P4) |
| Entidades JPA | `catalog/infrastructure/persistence/entity/ProfessionalEntity.java` y `WeeklyScheduleEntity.java` | `ProfessionalEntity` tiene su categoría (`@ManyToOne`); `WeeklyScheduleEntity`, su profesional. El día se guarda con `@Enumerated(EnumType.STRING)`, es decir, el nombre en mayúsculas (`MONDAY`) |
| Tablas | `src/main/resources/db/migration/V1__create_catalog.sql` | `professionals` (`first_name` y `last_name` obligatorios, sin colación propia) y `weekly_schedules` (`day_of_week VARCHAR(9)`, `start_time` y `end_time` `TIME`). Sin índices además de las claves |
| Colación española | `src/main/resources/db/migration/V3__spanish_order_for_category_names.sql` | Crea `spanish_insensitive` (ICU, `es-u-ks-level1`, no determinística) y se la asigna a `professional_categories.name`. Ya está aplicada en la base de desarrollo: no se modifica |
| Estado de sincronización | `catalog/domain/ports/out/CatalogSyncStateRepository.java` | `get().isInitialized()`, como en C04 (RF-08) |
| Excepción de "sin inicializar" | `catalog/application/exception/CatalogNotInitializedException.java` (y su base `CatalogException`) | Se reutiliza tal cual (RF-08) |
| Entrada web de C04 | `catalog/infrastructure/web/controller/ProfessionalCategoryController.java` | Modelo de cómo un controller traduce `CatalogNotInitializedException` a `503` `CATALOG_NOT_INITIALIZED` |
| Seguridad | `config/SecurityConfig.java` | `PUBLIC_ENDPOINTS`, hoy solo `GET /api/professional-categories`. Suma `GET /api/professionals`, exacta (RF-02, RF-12) |
| Formato de errores | `config/ApiExceptionHandler.java` | Hereda de `ResponseEntityExceptionHandler`; hoy solo atrapa lo no previsto (`500` `INTERNAL_ERROR`). Suma el `400` `VALIDATION_ERROR` con `fieldErrors` (P5; decisión 005) |
| Dependencias | `pom.xml` | Ya están web, JPA, validation y security. No hace falta ninguna nueva para lo decidido hasta ahora |
| Bases de test | `TestcontainersConfiguration.java` (PostgreSQL 18), `catalog/CatalogTables.java`, `catalog/CatalogSamples.java`, `TestTokens.java`, `catedra/FakeCatedraServer.java`, `FakeHttpServer.java` | `CatalogTables` suma la carga de profesionales y horarios; lo demás se reutiliza como en C04 (P7) |
| Tests de C04 que se tocan | `catalog/ProfessionalCategoriesIntegrationTest.java` | `anyOtherRouteWithoutAuthenticationIsRejectedWithoutData` pierde el caso `GET, /api/professionals`; conserva `GET, /api/professionals/15` (RF-12) |

Hechos comprobados el 2026-11-20 para este PLAN:

- **Versiones:** Spring Boot 4.1.2, Spring MVC y Spring JDBC 7.0.10, Spring Data JPA 4.1.2, Hibernate 7.4.13, Java 25.
- **Colaciones y `LIKE` en PostgreSQL 18.6** (contenedor descartable de `postgres:18`, la imagen de los tests y del compose, con la colación de `V3` y otra de ICU `und-u-ks-level1`, no determinística):
  - `LIKE` funciona con una colación no determinística. `ILIKE` no: "nondeterministic collations are not supported for ILIKE".
  - Con la colación de `V3` (`es`), "%nunez%" no encuentra "Núñez" (la ñ es otra letra) y "%nuñez%" sí; "%gomez%" encuentra "Gomez" y "Gómez".
  - Con `und-u-ks-level1`, escrita en la consulta (`COLLATE`): "%nunez%" encuentra "Núñez", "%ibanez%" "Ibáñez", "%TOM%" "Tomás", "%guemes%" "Güemes", y "%ana%" a "Mariana" y a todas las "Ana". Es lo que piden D4 y D5.
  - Con `ESCAPE '\'`, "%\%%" encuentra solo un apellido con `%` y "%\_%" solo uno con `_`; sin escapar, "%%%" encontró las 14 filas: los comodines del texto se tienen que escapar.
  - `ORDER BY last_name, first_name, id` con la colación de `V3` en las dos columnas dio el orden de D6: Álvarez, de la Fuente, Ferreyra, Gomez (#10), Gómez (#11), Güemes, Ibáñez, Munro, Muñoz, Núñez, Paz, Pérez…, Zárate.
  - Con la colación de `V3`, "Gomez" = "Gómez" es verdadero y "Nunez" = "Núñez" falso.
  - `unaccent` (extensión incluida en la imagen) convierte "Núñez Ibáñez Güemes TOMÁS" en "Nunez Ibanez Guemes TOMAS".
- **Escribir una colación desde Hibernate 7.4.13:** `HibernateCriteriaBuilder` tiene `collate(Expression<String>, String)` y `like(…, char escape)`, y la gramática de HQL tiene una función `collate` (visto en el jar con `javap` y en sus clases). Sin ejecutar todavía. Spring Data JPA 4.1.2 trae `Specification` y `JpaSpecificationExecutor`.
- **Cómo lee Spring Boot 4.1.2 los parámetros de la URL en un `@ModelAttribute`** (test temporal `@WebMvcTest` con un controller de prueba, borrado después de leer la salida):
  - **Una lista acepta comas:** con `List<String>` o `List<DayOfWeek>`, `days=MONDAY,FRIDAY` llega igual que `days=MONDAY&days=FRIDAY`, como dos valores. Con `days=MONDAY&days=` llega `[MONDAY, ""]`, y con `days=` una lista vacía.
  - **Un enum distingue mayúsculas:** `monday` y `Monday` dan error de conversión en `List<DayOfWeek>`.
  - **Un `Boolean` es tolerante:** `yes`, `TRUE` y `1` llegan como `true`; `quizas` da error.
  - **Un `LocalTime`** acepta `09:00` y `09:00:00`, con `Accept-Language` `es-AR` o `en-US`; rechaza `9:00` y `9:00 AM`; y **acepta `09:00:00.5`** como `09:00:00.500`.
  - **Un valor vacío** llega como `null` en `Boolean`, `LocalTime`, `Integer` y `Long`, y como `""` en un `String`.
  - Un número que no entra en `Integer` o en `Long` da error de conversión.
  - Los errores de conversión de varios campos llegan juntos, y su mensaje por defecto muestra tipos de Java ("required type 'java.lang.Integer'", "[java.time.DayOfWeek]").
- **Nadie compara profesionales por nombre** en el código: los nombres solo aparecen en los campos y los mappers (buscado en `src/main/java`). La sincronización va por `id`.
- **El día de la semana se guarda en mayúsculas** (`@Enumerated(EnumType.STRING)`; `CatalogRepositoryAdaptersTest` y `FullSynchronizationIntegrationTest` lo leen con SQL y esperan `"MONDAY"` y `"TUESDAY"`).

**Convenciones y slice de referencia:** la skill `hexagonal-vertical-slicing` y el slice `catalog`, con sus excepciones del `CLAUDE.md`, y C04 como modelo de una entrada web pública. Turnos (T10: paginación, `@ModelAttribute` y `fieldErrors`) es solo un antecedente: cada decisión se analiza para catálogo.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de la solicitud, el evento o la tarea desde que entra hasta la
respuesta, la persistencia o el mensaje publicado. Referencia el diagrama de
secuencia del proyecto si ya lo describe; usa un diagrama nuevo solo si aporta
claridad. -->

C05 sigue el molde de la skill dentro del slice `catalog` (P6), con la consulta armada por Specifications (P2) sobre dos colaciones nuevas (P1), todas las lecturas en una misma foto (P3) y el request validado por Bean Validation (P5). En `catalogo.config`, la seguridad suma la ruta y el manejador suma el `400`.

```
GET /api/professionals?…
└─ SecurityConfig (config): ruta pública, sin leer Authorization                         (RF-02, RF-12)
   └─ ProfessionalController                                                             infrastructure/web/controller
      ├─ @Valid @ModelAttribute ProfessionalSearchRequest: si algo es inválido, 400      (P5; RF-07)
      ├─ ProfessionalDtoMapper.toDomain / toPage / toSize → filtros, página y tamaño
      └─ ProfessionalService.searchProfessionals                                        application/service
         └─ SearchProfessionalsUseCase = …UseCaseImpl (solo lectura, REPEATABLE READ)    application/usecases (P3)
            ├─ CatalogSyncStateRepository.get(): sin inicializar → CatalogNotInitializedException   (RF-08)
            ├─ ProfessionalRepository.countMatching(filtros)                             (RF-06)
            ├─ si la página empieza en el total o más allá → página vacía con el total
            ├─ ProfessionalRepository.findMatching(filtros, página, tamaño)             (RF-03 a RF-05)
            │  └─ Specification: categoría, estado, palabras con la colación de búsqueda,
            │     disponibilidad; orden por apellido, nombre e id (colación de V3 en V4)
            ├─ WeeklyScheduleRepository.findAllByProfessionalIds(ids de la página)      (RF-01)
            └─ junta cada profesional con sus horarios, ordenados por D7 → ProfessionalPage
      ├─ ProfessionalDtoMapper.toResponse → 200 ProfessionalPageResponse
      └─ CatalogNotInitializedException → ResponseStatusException 503 CATALOG_NOT_INITIALIZED
└─ ApiExceptionHandler (config): 400 VALIDATION_ERROR con fieldErrors; lo no previsto → log y 500
```

## Slices y componentes afectados

<!-- Aplica la skill hexagonal-vertical-slicing: un paquete por slice, con sus
capas domain, application e infrastructure. Identifica los slices afectados, sus
responsabilidades y la dirección de sus dependencias. Una funcionalidad puede
afectar más de un slice. Justifica cualquier slice nuevo o dependencia nueva
entre slices. -->

| Slice | Existe / nuevo | Responsabilidad y cambios | Dependencias con otros slices |
| --- | --- | --- | --- |
| `catalogo.catalog` | Existente | Suma la búsqueda de C05: entrada web, caso de uso, tres tipos del dominio (el profesional con sus horarios, los filtros y la página; P4) y la consulta en la persistencia de profesionales y horarios (P2). Las piezas, en la tabla siguiente (P6) | Ninguna nueva: C05 no usa `catedra` ni `synchronization` |
| `catalogo.config` | Existente | `SecurityConfig` suma la ruta pública; `ApiExceptionHandler`, el `400` (P5) | No importa nada de los slices |
| `catalogo.catedra`, `catalogo.synchronization` | Existentes | Sin cambios | — |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son
propuestas. Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Slice y capa | Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- | --- |
| catalog / domain | `catalog/domain/model/ProfessionalSearchCriteria.java` | Crear | Los filtros ya validados: `categoryId`, `enabled`, `name` (sin los espacios de los extremos, o vacío), los días (`Set<DayOfWeek>`), `from` y `to`. `nameWords()` separa el nombre en palabras (D4): una regla del concepto, en el modelo | RF-03, RF-04 |
| catalog / domain | `catalog/domain/model/ProfessionalWithSchedules.java` | Crear | Un `Professional` y su lista de `WeeklySchedule` (P4.1) | RF-01 |
| catalog / domain | `catalog/domain/model/ProfessionalPage.java` | Crear | La lista de `ProfessionalWithSchedules` y `totalItems` (P4.2) | RF-06 |
| catalog / domain | `catalog/domain/ports/in/SearchProfessionalsUseCase.java` | Crear | `searchProfessionals(criteria, page, size)` | RF-01 |
| catalog / domain | `catalog/domain/ports/out/ProfessionalRepository.java` | Modificar | Suma `countMatching(criteria)` y `findMatching(criteria, page, size)`, este en el orden de D6 | RF-03 a RF-06 |
| catalog / domain | `catalog/domain/ports/out/WeeklyScheduleRepository.java` | Modificar | Suma `findAllByProfessionalIds(ids)` | RF-01 |
| catalog / application | `catalog/application/usecases/SearchProfessionalsUseCaseImpl.java` | Crear | Solo lectura con `REPEATABLE READ` (P3). Sin inicializar, lanza `CatalogNotInitializedException`. Cuenta; si la página empieza en el total o más allá, devuelve la página vacía con el total. Si no, lee la página y los horarios de esos profesionales, los junta y ordena los horarios por D7 | RF-01, RF-06, RF-08, RF-11 |
| catalog / application | `catalog/application/service/ProfessionalService.java` | Crear | `searchProfessionals(...)`; C06 le sumará el suyo | RF-01 |
| catalog / infrastructure | `catalog/infrastructure/persistence/repository/JpaProfessionalRepository.java` | Modificar | Suma `JpaSpecificationExecutor<ProfessionalEntity>` | RF-03 a RF-05 |
| catalog / infrastructure | `catalog/infrastructure/persistence/adapter/JpaProfessionalRepositoryAdapter.java` | Modificar | `countMatching` y `findMatching`, con la condición armada en métodos privados (P2) y el orden `last_name`, `first_name`, `id` | RF-03 a RF-06 |
| catalog / infrastructure | `catalog/infrastructure/persistence/repository/JpaWeeklyScheduleRepository.java` | Modificar | Suma `findAllByProfessionalIdIn(ids)` | RF-01 |
| catalog / infrastructure | `catalog/infrastructure/persistence/adapter/JpaWeeklyScheduleRepositoryAdapter.java` | Modificar | Implementa `findAllByProfessionalIds` con el mapper existente, que ya tiene `toDomainModel` | RF-01 |
| recursos | `src/main/resources/db/migration/V4__spanish_order_and_search_for_professional_names.sql` | Crear | Las dos colaciones de P1 | RF-03, RF-05 |
| catalog / infrastructure | `catalog/infrastructure/web/controller/ProfessionalController.java` | Crear | `@RequestMapping("/api/professionals")` y un `@GetMapping` con `@Valid @ModelAttribute`; traduce `CatalogNotInitializedException` a `503` `CATALOG_NOT_INITIALIZED`, como el de categorías. C06 le sumará su `/{professionalId}` | RF-01, RF-07, RF-08 |
| catalog / infrastructure | `catalog/infrastructure/web/dto/ProfessionalSearchRequest.java` | Crear | Los parámetros como texto, con sus reglas y mensajes fijos (P5) | RF-07 |
| catalog / infrastructure | `catalog/infrastructure/web/dto/WholeNumber.java` | Crear | Validación propia: un entero dentro de un rango, escrito como texto, y el vacío vale. Para `categoryId`, `page` y `size`. Lleva su validador adentro (ajuste del 2026-11-21) | RF-07 |
| catalog / infrastructure | `catalog/infrastructure/web/dto/TimeRange.java` | Crear | Validación propia sobre todo el request: `from` anterior a `to`, informada en `from` y revisada solo si las dos horas se pudieron leer. Lleva su validador adentro (ajuste del 2026-11-21) | RF-07 |
| catalog / infrastructure | `catalog/infrastructure/web/dto/ProfessionalPageResponse.java` | Crear | `items`, `page`, `size` y `totalItems` | RF-06 |
| catalog / infrastructure | `catalog/infrastructure/web/dto/ProfessionalResponse.java` | Crear | El profesional, con `category` y `weeklySchedules`; C06 lo reutiliza | RF-01 |
| catalog / infrastructure | `catalog/infrastructure/web/dto/ProfessionalCategorySummaryResponse.java` | Crear | La categoría dentro del profesional: `id`, `name` y `enabled` (`ProfessionalCategoryResponse` lleva `description`) | RF-01 |
| catalog / infrastructure | `catalog/infrastructure/web/dto/WeeklyScheduleResponse.java` | Crear | Los seis campos del horario | RF-01 |
| catalog / infrastructure | `catalog/infrastructure/web/mapper/ProfessionalDtoMapper.java` | Crear | `toDomain(request)` arma los filtros; `toPage` y `toSize`, el número y el tamaño de página con sus valores por defecto; `toResponse(...)`, la respuesta con la categoría y los horarios | RF-01, RF-06, RF-07 |
| config | `config/SecurityConfig.java` | Modificar | Suma `GET /api/professionals`, exacta | RF-02, RF-12 |
| config | `config/ApiExceptionHandler.java` | Modificar | El `400` `VALIDATION_ERROR` con `fieldErrors` (P5) | RF-07 |
| docs | `docs/decisiones/005-manejador-global-de-errores.md` | Modificar | Los códigos generales los pone el manejador; los de cada slice, su controller (P5) | RF-07 |

Sin cambios: los modelos `Professional`, `WeeklySchedule` y `ProfessionalCategory`, las entidades JPA, los mappers de persistencia y todo `catedra` y `synchronization`. Los nombres son los aprobados en P6; se pueden ajustar en TASKS sin cambiar el plan.

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Operaciones, eventos y DTO de entrada y salida:** contrato §2.2 y contrato común §2 y §3.1, con lo que fijó la SPEC (RF-01, RF-06, RF-07). Los parámetros llegan en un request validado (`@Valid @ModelAttribute`) con todos los campos como texto, y los días como lista de textos (P5). El `400` lo arma `ApiExceptionHandler` con `fieldErrors`. El mapper web convierte el request ya validado en los filtros del dominio. Las respuestas, en P6.
- **Identificadores, relaciones y restricciones:** los `id` son los de la cátedra; sin cambios.
- **Origen de los datos devueltos o publicados y transformaciones:** la copia local. El caso de uso combina dos lecturas, los profesionales de la página y los horarios de esos profesionales, y junta cada profesional con los suyos (P4.1). Los horarios se ordenan en Java por D7, porque la base guarda el día como texto y lo ordenaría alfabéticamente, mientras que el `DayOfWeek` de Java va de lunes a domingo (P2).
- **Persistencia, consultas y migraciones:** una migración nueva, `V4` (P1), que:
  - asigna la colación de `V3` (`spanish_insensitive`) a `professionals.first_name` y `last_name`, para que `ORDER BY last_name, first_name, id` dé el orden de D6;
  - crea la colación de búsqueda (nombre propuesto `search_insensitive`: ICU, `und-u-ks-level1`, no determinística), que la consulta escribe sobre cada columna con `COLLATE`, con `LIKE … ESCAPE '\'` y con `%`, `_` y `\` del texto escapados (D4 y D5).
  La consulta (P2) se arma con Specifications de Spring Data: `JpaProfessionalRepository` suma `JpaSpecificationExecutor`, y el adaptador arma con métodos privados una condición por cada filtro recibido: la categoría, el estado, cada palabra del nombre (con la colación de búsqueda) y la disponibilidad ("existe un horario de este profesional que…", con las reglas de RF-04). El adaptador ofrece contar y traer una página; el caso de uso cuenta primero y pide la página solo si su comienzo está antes del total, así una página enorme nunca llega a la base (ajuste de P2). Los horarios de toda la página salen de una sola consulta, con un método derivado en `JpaWeeklyScheduleRepository`. Los valores viajan siempre como parámetros.
- **Convivencia entre datos locales y remotos:** no aplica: C05 solo lee la copia local.
- **Compatibilidad y migraciones de datos existentes:** `V4` no cambia ningún dato: solo cómo se comparan y ordenan las dos columnas. Flyway la aplica al arrancar, también sobre la base de desarrollo, que está en `V3`. `V3` no se toca.

## Transacciones, concurrencia y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de BACKEND_GUIDELINES.md. -->

- **Estados y transiciones:** no aplica: C05 no cambia estados.
- **Transacciones y consistencia:** todas las lecturas de una búsqueda (el estado, el total, la página y los horarios) van en una sola transacción de solo lectura con aislamiento `REPEATABLE READ`, en el caso de uso (P3). En PostgreSQL, todas sus consultas ven la foto de la primera: si una sincronización confirma en el medio, la respuesta sigue mostrando la versión anterior entera (RF-11, CA-15). Una transacción común (`READ COMMITTED`) no alcanzaría, porque cada consulta vería su propio momento.
- **Idempotencia y mensajes duplicados:** no aplica: solo lee.
- **Concurrencia y tareas programadas:** C05 no toma el candado de la sincronización ni espera a una en curso (RF-11). Su transacción no frena a la sincronización (en PostgreSQL leer no bloquea a quien escribe) y, como solo lee, no puede fallar por conflicto con otra.
- **Timeouts, reintentos y errores de dependencias:** sin límite propio para la base (D10 de la SPEC); la falla llega al manejador global, que responde `500` (decisión 005).
- **Seguridad y autorización:** `GET /api/professionals` se suma a `PUBLIC_ENDPOINTS`, con la ruta exacta, para que `GET /api/professionals/{professionalId}` (C06) siga cerrado (RF-12). Nadie lee `Authorization` (RF-02).
- **Otras consideraciones de backend aplicables y su solución:** los comodines del texto de búsqueda se escapan (P1); los errores no muestran detalles internos (P5).

## Dependencias y configuración

<!-- Librerías, servicios externos, propiedades de configuración y secretos
afectados. Verifica compatibilidad con el proyecto y justifica las incorporaciones.
No agregues dependencias por defecto. Los valores sensibles y los parámetros
ajustables se externalizan. -->

- Sin dependencias nuevas: Specifications vienen con Spring Data JPA, `collate` con Hibernate, y Bean Validation con `spring-boot-starter-validation`, que ya están en el `pom.xml`.
- Sin variables ni propiedades nuevas: C05 no tiene valores ajustables (D10 de la SPEC), y los límites de la paginación los fija el contrato común §3.1.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, de integración (base de datos, mensajería), de API o prueba manual.
No todos requieren todos los métodos. Identifica tests existentes y separa los
nuevos propuestos. Incluye regresiones relevantes. Una prueba con mocks no
demuestra persistencia, transacciones ni idempotencia. -->

Niveles de P7.1, como en C04, con un test del caso de uso de más porque aquí tiene lógica propia. Clases nuevas: `ProfessionalDtoMapperTest`, `SearchProfessionalsUseCaseImplTest` (puertos simulados), `ProfessionalControllerTest` (`@WebMvcTest` con `SecurityConfig`, `ApiExceptionHandler` y el mapper reales, y el service simulado), `ProfessionalSearchRepositoryAdaptersTest` (PostgreSQL con `V4`, misma configuración que `CatalogRepositoryAdaptersTest`, así comparte su contexto), `ProfessionalsIntegrationTest` (aplicación en un puerto real con `RestTestClient`) y `ProfessionalsConsistencyIntegrationTest` (P7.2). Se modifican `ProfessionalCategoriesIntegrationTest`, `ProfessionalCategoriesWithoutCatedraIntegrationTest` y `CatalogTables` (suma la carga de profesionales y horarios con SQL directo). No hay E2E: C05 no usa la cátedra.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Controller: los campos exactos y las horas en `HH:mm:ss`. Caso de uso: los horarios juntos con su profesional y en el orden de D7. Integración: los valores salen de la base, y la cátedra simulada no recibe pedidos después del login del arranque | En integración, la copia cargada con SQL directo, con los horarios en otro orden | `200`, cuerpo comparado campo por campo; pedidos a la cátedra simulada sin cambios |
| CA-02 | Adaptadores; un caso en integración | Profesionales de categorías habilitadas y deshabilitadas, en los dos estados | Solo los que corresponden |
| CA-03 | Adaptadores: cada texto del criterio. Mapper: espacios y `nameWords()`. Un caso en integración | Los cuatro profesionales del criterio | Los del criterio; con `%` o `_`, ninguno |
| CA-04, CA-05 | Adaptadores: cada combinación y cada borde. Un caso en integración | Los profesionales de los dos criterios | Los del criterio en cada pedido |
| CA-06 | Adaptadores | Cada filtro descarta a un profesional distinto | Solo los que cumplen todos |
| CA-07 | Adaptadores: el orden completo y página por página. Un caso en integración | Los nombres del criterio, cargados en otro orden | El orden de D6, desempate por `id` |
| CA-08 | Adaptadores: contar y traer una página. Caso de uso: más allá del total no lee la página ni los horarios. Integración: sin `page` ni `size`, con `size` 2 y con la página 2000000000 | Más de 10 profesionales | Páginas sin repetidos ni faltantes; vacía con el total real; nunca `500` |
| CA-09, CA-10, CA-19 | Controller: cada valor inválido, la cantidad de entradas de `fieldErrors` y sus textos. Integración: algunos `400` con el servidor real, con la copia sin inicializar | Ninguno; en integración, sin versión local | `400`, `problem+json`, `VALIDATION_ERROR`, el parámetro en `fieldErrors`, sin tipos de Java; nunca `503` |
| CA-11 | Controller, con los tokens de `TestTokens`; una variante en integración | La misma copia | Cada respuesta igual a la del pedido sin encabezado |
| CA-12 | Controller: la forma del `503`. Caso de uso: sin inicializar no busca. Integración: sin versión local, con y sin profesionales | `local_version` en `null` con SQL directo | `503` `CATALOG_NOT_INITIALIZED`; sin pedidos a la cátedra; tablas iguales antes y después |
| CA-13 | Adaptadores; integración | `categoryId` inexistente, filtros que nadie cumple, sin profesionales | `200`, `items` vacío, `totalItems` 0 |
| CA-14 | Controller: una falla de acceso a datos simulada. Integración: se renombra `catalog_sync_state`, y en otro caso `professionals`, y se restauran en un `finally` (P7.3) | La base del test | `500` `INTERNAL_ERROR` sin detalles internos; un error en el log (salida capturada) |
| CA-15 | Integración: un cambio sin confirmar, desde una conexión propia, mientras llega el pedido, y después confirmado. `ProfessionalsConsistencyIntegrationTest`: un cambio confirmado entre contar y traer la página (P7.2) | Para el primero, un tiempo máximo amplio; el segundo no usa tiempos | Primero la copia anterior sin esperar y después la nueva; en el segundo, el total, la página y los horarios de antes. Prueba en negativo: sin `REPEATABLE READ`, falla |
| CA-16 | `ProfessionalCategoriesWithoutCatedraIntegrationTest`: su método suma un pedido a C05 | La aplicación sin la URL de la cátedra; copia inicializada | `200` |
| CA-17 | `ProfessionalCategoriesIntegrationTest`: se quita `GET /api/professionals` y se suma `POST /api/professionals`; `GET /api/professionals/15` sigue | Ninguno | Rechazados sin datos (`403`) |
| CA-18 | Controller: el service recibe los mismos filtros que sin esos parámetros. Mapper: vacíos, mayúsculas y comas. Un caso en integración | La misma copia | Respuestas iguales |

En todos, la práctica acordada: después de que el test pasa, una prueba en negativo (romper a propósito, ver que falla, restaurar y comprobar el hash).

**Comprobaciones de regresión:** la suite completa (`./mvnw clean verify`), sin cambios en los tests de X01 y de C01 a C04, salvo el caso que se quita del test de las otras rutas.

**Comandos verificados para compilar y ejecutar tests:** los del `CLAUDE.md` del repositorio (`./mvnw test`, `./mvnw test -Dtest='Clase'`, `./mvnw clean verify`).

**Pruebas con infraestructura real o simulada:** PostgreSQL 18 de Testcontainers, el compartido de `TestcontainersConfiguration`, y la cátedra simulada solo como testigo de que C05 no le pide nada. Sin Kafka ni Redis. Ningún test nuevo detiene o pausa la base (P7.3).

**Prueba manual con la aplicación (P7.4, última etapa, con el OK del usuario):** el usuario levanta la aplicación con la cátedra real y Kafka apagado (`CATALOGO_CATEDRA_KAFKA_ENABLED=false`). Al arrancar hace el login técnico, y la verificación carga el snapshot real y lee Redis. Antes se le da la lista exacta de pedidos a la cátedra y los `curl` a C05; él corre los comandos y pasa la salida, que se revisa sin mostrar secretos. Comprueba que `V4` se aplica sobre la base de desarrollo (hoy en `V3`) y deja la evidencia de los filtros con los datos reales (enunciado §11). Con Kafka apagado no se usa el consumer group del servicio. Al terminar, la aplicación detenida y el compose como estaba; la base de desarrollo conserva la copia sincronizada, como en cualquier arranque.

**Limitaciones del entorno:**
- La base colgada con una consulta ya en curso (D10) no se prueba, como en C04: sigue como limitación aceptada.
- `@WebMvcTest` no reproduce el reenvío a `/error` de un servidor real: por eso los `400`, el `503` y el `500` también se prueban con la aplicación levantada.
- Si la cátedra no responde durante la prueba manual, se registra como limitación del entorno y la evidencia queda en los tests de integración.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **La búsqueda en la base.** `V4`; `ProfessionalSearchCriteria`; `countMatching` y `findMatching` en el puerto, el adaptador y el repositorio de Spring Data; `findAllByProfessionalIds` en los de horarios; `CatalogTables` ampliado; y `ProfessionalSearchRepositoryAdaptersTest` (CA-02 a CA-08 y CA-13 en la base). Antes de tocar nada, la línea base de la suite. **Primera comprobación: que el `collate` de Hibernate funcione** (riesgo de P2); si no, se vuelve a P2 con el usuario antes de seguir.
2. **El caso de uso.** `ProfessionalWithSchedules`, `ProfessionalPage`, `SearchProfessionalsUseCase` y su implementación, `ProfessionalService`, y `SearchProfessionalsUseCaseImplTest`. Comprobación: ese test y la suite compila.
3. **El request, las respuestas y el mapper.** `ProfessionalSearchRequest`, las cuatro respuestas, `ProfessionalDtoMapper` y su test. Comprobación: el test del mapper.
4. **La entrada web.** `SecurityConfig` y el cambio de `ProfessionalCategoriesIntegrationTest` en la misma tarea (si no, ese test falla); `ApiExceptionHandler` con el `400` y el ajuste del texto de la decisión 005; `ProfessionalController` y `ProfessionalControllerTest`. Comprobación: los dos tests web y el de las otras rutas.
5. **La integración completa.** `ProfessionalsIntegrationTest`, el pedido a C05 en `ProfessionalCategoriesWithoutCatedraIntegrationTest` y `ProfessionalsConsistencyIntegrationTest`, con su prueba en negativo sin `REPEATABLE READ`. Comprobación: esas clases.
6. **Regresión y prueba manual.** `./mvnw clean verify` completo, con el conteo de tests en los reportes XML; después, con el OK del usuario, la prueba manual de P7.4.
7. **Cierre.** El `CLAUDE.md` (el slice `catalog` con C05, las excepciones de C05, cómo se busca, las colaciones de `V4` y su efecto al comparar, la ruta pública nueva, los tests y la carpeta de la SDD), `TASKS.md` con lo observado y la memoria.

La etapa 1 no depende de nada; la 2 necesita la 1; la 3 puede ir con la 2; la 4 necesita la 2 y la 3; la 5, todas las anteriores.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - **Las colaciones dependen de ICU en PostgreSQL**, como en C04: una base sin ICU no puede crearlas y `V4` falla al arrancar, de forma visible. La imagen `postgres:18` lo trae.
  - **El nombre y el apellido pasan a compararse sin mayúsculas ni tildes:** con la colación de `V3`, "Gomez" = "Gómez" (comprobado). Nadie compara profesionales por nombre; queda anotado en el `CLAUDE.md` al cerrar, como en C04. Y `ILIKE` no se puede usar sobre esas columnas.
  - **El aislamiento tiene que aplicarse de verdad:** si quedara la transacción común, todo seguiría funcionando salvo RF-11. Lo detecta el test de CA-15 con su prueba en negativo (P7).
  - **El `collate` de Hibernate no se ejecutó todavía** (P2): es lo primero que se comprueba en la etapa 1, y si falla se vuelve a P2 con el usuario.
  - **Las horas en `HH:mm:ss`:** por lo que sé, Jackson escribe un `LocalTime` con segundos (`09:00:00`), pero no lo comprobé en este proyecto. CA-01 lo comprueba; si no fuera así, el mapper las escribe como texto con ese formato.
  - **La categoría de cada profesional** se carga con su relación de JPA: en una página de 10 son como mucho unas pocas lecturas más, por las 8 categorías. Si el log de los tests mostrara una lectura por profesional, se ajusta en la consulta.
- **Decisiones pendientes:** Ninguna.
- **Decisiones tomadas:**
  - **Aprobación (2026-11-21):** el usuario aprobó el PLAN con P1 a P8, el ajuste a P2 y la estrategia de validación, que suma `POST /api/professionals` a las rutas rechazadas de CA-17.
  - **P1 · El orden y la búsqueda por nombre (2026-11-20): opción a), dos colaciones de ICU en una migración nueva.** `V4` asigna la colación de C04 al nombre y al apellido (orden de D6) y crea una colación de búsqueda (`und-u-ks-level1`), que la consulta escribe con `COLLATE … LIKE … ESCAPE`, con los comodines escapados (D4 y D5). Todo comprobado en PostgreSQL 18.6 ("Hechos comprobados"). Consecuencia: la consulta tiene que poder escribir una colación, y eso condiciona P2. Alternativas descartadas: b), la colación de C04 para el orden y `unaccent` para la búsqueda, que suma una extensión con sus permisos (sin comprobar para un usuario que no administra la base) y dos mecanismos para la misma idea; c), todo en Java (filtrar, ordenar con el `Collator` y paginar en memoria), la consulta más simple, pero que lee la copia entera en cada búsqueda, contra el motivo de paginar, y deja la regla del orden de D2 escrita dos veces, en la base para las categorías y en Java para los profesionales; d), columnas sin tildes llenadas por la sincronización, que cambia C01 a C03, fuera del alcance de la SPEC.
  - **P3 · La misma foto para todas las lecturas (2026-11-20): opción a), una transacción de solo lectura con `REPEATABLE READ`** en el caso de uso (ver "Transacciones, concurrencia y errores"). `@Transactional` en los casos de uso de `catalog` no es una excepción a la skill (C01). Se prueba sin depender del tiempo: el test confirma un cambio desde otra conexión entre la lectura de la página y la de los horarios, y la respuesta tiene que mostrar la versión anterior; la prueba en negativo quita el aislamiento (P7). Alternativas descartadas: b), una transacción común (`READ COMMITTED`), que parece resolverlo pero no, porque cada consulta ve su propio momento; c), una sola sentencia SQL con el total por función de ventana y los horarios en JSON, la misma garantía con mucho más SQL y una conversión de JSON en el adaptador; d), sin transacción, que contradice RF-11 aprobado.
  - **P2 · Cómo se escribe la consulta (2026-11-20): opción a), Specifications de Spring Data** (ver "Persistencia, consultas y migraciones"). Sigue el molde del adaptador de la skill (repositorio de Spring Data, entidades y el mapper existente), y cada filtro es una pieza chica que se prueba sola. Contar primero evita que una página enorme llegue a la base, donde el desplazamiento, por lo que sé, se limita a un `int` (CA-08 lo comprueba), como hizo turnos en T10. Riesgo: la colación se escribe con `collate` de Hibernate, que existe en el jar pero no se ejecutó; lo confirma el primer test del adaptador, y si fallara se vuelve a esta decisión con el usuario. Alternativas descartadas: b), SQL a mano con `NamedParameterJdbcTemplate`, una excepción a la skill que convierte cada fila a mano habiendo un mapper; c), una consulta nativa fija con todos los filtros opcionales y arreglos, que hay que entender entera para tocar un filtro y suma las conversiones de tipo que pide PostgreSQL para los parámetros nulos; d), HQL armado como texto con el `EntityManager`, con los problemas de b) y concatenando texto.
  - **P4 · El modelo y los puertos (2026-11-20):**
    - **P4.1 · Un tipo nuevo del dominio junta cada profesional con sus horarios: opción a).** Contiene al `Professional` y su lista de `WeeklySchedule`, sin repetir campos (nombre propuesto `ProfessionalWithSchedules`). Lo arma el caso de uso combinando las dos lecturas (skill §5.2), y C06 devolverá lo mismo. No contradice la regla de ampliar, no duplicar: no copia ningún campo del modelo, lo contiene. Alternativas descartadas: b), sumarle a `Professional` sus horarios, con un campo que quedaría vacío y engañoso en toda la sincronización y una referencia circular con `WeeklySchedule.professional`; c), dos resultados que junta el controller, con lógica que no le corresponde.
    - **P4.2 · La página y los filtros, clases Lombok del dominio: opción a).** Los filtros ya leídos y validados (nombre propuesto `ProfessionalSearchCriteria`) y la página con sus profesionales y el total (`ProfessionalPage`). El número y el tamaño de la página van como parámetros del método del puerto; el controller los tiene para armar la respuesta. Alternativas descartadas: b), `record`, otra excepción a la skill sin motivo (en `catalog` se usaron solo para resultados sellados o para datos que se leen mientras otro hilo los reemplaza); c), `Page` y `Pageable` de Spring en el puerto, que rompe la regla de dependencias de la skill (§2, regla 2).
  - **P5 · Cómo se leen y validan los parámetros (2026-11-20): opción a), un request con Bean Validation, con todos los campos como texto.** Es el molde de la skill para un request (§5.3, `web/dto`) y lo que hizo turnos en T10. Se recibe con `@Valid @ModelAttribute`, porque C05 no tiene cuerpo.
    - Como todos los campos son texto, Spring no convierte nada antes de validar: no hay errores de conversión con tipos de Java en los mensajes, y las reglas de D8.2 las ponen las validaciones del request. Son el booleano estricto, las horas `HH:mm` o `HH:mm:ss` sin fracciones, los días sin distinguir mayúsculas, y los números enteros con sus rangos.
    - "`from` anterior a `to`" es una regla que mira las dos horas, se informa en `from` y solo se revisa si las dos se pudieron leer (D9.2).
    - `ApiExceptionHandler` sobrescribe el manejo de `MethodArgumentNotValidException` y responde `400` `VALIDATION_ERROR` con un `fieldErrors` por parámetro: los errores de varios días se juntan en uno solo, `dayOfWeek` (D9.1). No importa nada de C05.
    - El mapper web convierte el request ya validado en los filtros del dominio: los vacíos como ausentes, los días a `DayOfWeek` y las horas a `LocalTime` (D8.1).
    - **Cambio en la SPEC que trajo esta decisión, aprobado por el usuario:** Spring separa las comas antes de validar (ver "Hechos comprobados"), así que `dayOfWeek=MONDAY,FRIDAY` se acepta como dos días. Cambiaron D8.2, RF-07, CA-10 y CA-18, y la fila de `dayOfWeek` del contrato §2.2, en las dos copias. La recomendación de D8.2 de rechazar las comas se había dado sin saber eso.
    - El texto de la decisión 005 se ajusta en la tarea del manejador: los códigos generales del contrato común (`INTERNAL_ERROR` y `VALIDATION_ERROR`) los pone el manejador, y los de cada slice, su controller.
    - Alternativas descartadas: b), leer los valores crudos (`@RequestParam MultiValueMap`) y validarlos con código propio, que cumplía la SPEC sin cambios, pero salía de la skill (otra excepción) y escribía a mano la lectura de todos los parámetros para conservar una regla que no protegía nada; c), a) más un `@InitBinder` con un conversor que no separe las comas, que dependía del mecanismo interno de conversión de Spring para un solo parámetro.
  - **Ajuste a P2 (2026-11-21, aprobado por el usuario con P6):** contar primero lo hace el caso de uso, no el adaptador. El puerto ofrece dos métodos simples (contar y traer una página), sin un tipo de página propio, y decidir qué se lee y en qué orden es combinar llamadas a puertos, que la skill pone en el caso de uso (§5.2). El comportamiento no cambia.
  - **Ajuste a P5 y P6 (2026-11-21, al empezar TASK-05, aprobado por el usuario): dos validaciones propias, opción a).** Las validaciones estándar sobre texto (`@Pattern`) cubren los días, `enabled`, las horas y los vacíos, pero no dos reglas de la SPEC:
    1. Un entero dentro de un rango para `categoryId`, `page` y `size`. `@Pattern` no controla el rango, y CA-08 acepta `page=2000000000` mientras CA-10 rechaza 20 dígitos. `@Min` y `@Max` sobre texto aceptan `1.5` y `1e3`, que después harían fallar la conversión con un `500`. Combinados, dan dos errores en un mismo parámetro en orden variable, y el mensaje dejaría de ser fijo (D9.3).
    2. "`from` anterior a `to`" informado en `from` (D9.2): la opción estándar (`@AssertTrue`) lo informa con un nombre inventado.

    Se suman `WholeNumber` y `TimeRange` en `web/dto`, cada una con su validador adentro; el resto sigue con las validaciones estándar. Sin cambio de comportamiento. Alternativas descartadas: b), solo validaciones estándar, que cambiaba la SPEC dos veces (`page` limitada por cantidad de dígitos, contra CA-08, y "`from` antes que `to`" informado aparte, contra D9.1); c), una sola validación propia de toda la búsqueda, que escribía a mano lo que `@Pattern` ya da, casi la opción b) de P5.
  - **P6 · Las piezas y sus nombres (2026-11-21): aprobadas como se propusieron** (tabla de "Slices y componentes afectados"). Cada pieza tiene a alguien que la usa; los tres tipos del dominio responden a P4, y las tres respuestas anidadas, a que la skill pide una clase por forma de respuesta. Un service por entidad, `ProfessionalService`, al que C06 sumará su método; el controller recibirá también la ruta de C06.
  - **P8 · Excepciones a la skill (2026-11-21), autorizadas por el usuario para C05:**
    1. La ruta `/api/professionals` en lugar del `/{entities}` de la skill, porque la fija el contrato §1 (como en C04).
    2. El request se recibe con `@Valid @ModelAttribute` desde los parámetros de la URL: la skill solo describe `@Valid @RequestBody` "donde hay un cuerpo", y para una búsqueda sin cuerpo no dice nada (P5).
    3. El mapper web suma `toPage` y `toSize` además de `toDomain` y `toResponse`, porque P4.2 deja el número y el tamaño de página fuera de los filtros.
    4. Dos validaciones propias de Bean Validation en `web/dto`, `WholeNumber` y `TimeRange`, cada una con su validador en el mismo archivo. La skill no habla de validaciones propias (ajuste del 2026-11-21, más abajo).

    No son excepciones: los tres tipos del dominio (clases Lombok, como pide la skill); `JpaSpecificationExecutor` y el `collate` de Hibernate (infraestructura); `@Transactional` en el caso de uso (como desde C01); las respuestas anidadas (una clase por forma); el `503` en el controller (el molde de la skill); `V4` (es de la base); y el cambio en `ApiExceptionHandler` (dentro de la excepción de C04). El código no lleva comentarios que marquen excepciones: se listan en el `CLAUDE.md` al cerrar.
  - **P7 · Los tests (2026-11-21):**
    - **P7.1 · Niveles y clases: aprobados como se propusieron** (ver "Estrategia de validación").
    - **P7.2 · La foto única se prueba forzando el cambio entre dos lecturas: opción a), `@MockitoSpyBean` sobre el adaptador de profesionales.** Antes de traer la página, y después de contar, el test confirma un cambio desde una conexión propia del `DataSource`; la respuesta tiene que mostrar la versión anterior entera. Todo corre en el mismo hilo y en ese orden, así que no depende del tiempo. La prueba en negativo quita `REPEATABLE READ`. El usuario pidió una explicación de la foto única antes de seguir, y se le dio con un ejemplo de cuatro lecturas y una sincronización en el medio. Alternativas descartadas: b), una clase de prueba que envuelve el adaptador, como el `ControllableProcessRepository` de turnos, lo mismo con una clase entera para interceptar un método; c), solo un cambio sin confirmar, como en C04, que pasaría también sin el aislamiento y no prueba RF-11 (queda igual como la primera mitad de CA-15).
    - **P7.3 · Sin un test nuevo con la base detenida: opción a).** CA-14 se prueba renombrando tablas, que es determinista. El camino de una falla de la base hasta el `500` es el mismo que en C04, cuyo test con la base detenida ya lo cubre. Alternativa descartada: b), repetirlo con un contenedor propio y la espera de 1 s, que suma un contenedor y un arranque para un camino ya probado, y es el tipo de test que falló en GitHub.
    - **P7.4 · La prueba manual la corre el usuario con la cátedra real y Kafka apagado: opción a).** Es la única con datos reales, que es lo que pide el enunciado §11. Alternativas descartadas: b), la aplicación con la integración apagada sobre una base descartable con datos de ejemplo, que prueba lo mismo que los tests de integración; c), sin prueba manual.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados, respeta la skill hexagonal-vertical-slicing y permite
demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
