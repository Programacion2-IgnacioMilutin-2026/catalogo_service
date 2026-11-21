# TASKS: C04 — Listar categorías

SPEC: `docs/sdd/c04-listar-categorias/SPEC.md` (aprobada el 2026-11-20). PLAN: `docs/sdd/c04-listar-categorias/PLAN.md` (aprobado el 2026-11-20). Decisiones: `docs/decisiones/005-manejador-global-de-errores.md` y `docs/decisiones/006-resultados-sellados-o-excepciones.md`. Tareas aprobadas por el usuario el 2026-11-20, junto con el ajuste que mueve la etapa 1 del PLAN (el test en rojo) al comienzo de TASK-04; la implementación sigue su autorización, tanda por tanda (tandas 1 a 7: TASK-01 a TASK-12, autorizadas el 2026-11-20).

Las tareas siguen el "Orden de implementación" del PLAN y cada una deja la suite en verde. Toda tarea con tests incluye, después de que pasan, una prueba en negativo: romper el código a propósito, ver fallar el test, restaurar el archivo y comprobar el hash. Los tests se cuentan con los reportes XML (`target/surefire-reports/TEST-*.xml`) después de `clean`; al cerrar C02 eran 492, y TASK-01 toma la línea base. Ninguna tarea llama a la cátedra real ni lee el `.env`, salvo la aplicación en la prueba manual de TASK-11, que se corre con la integración apagada.

Los comandos de git los ejecuta el usuario. Al terminar cada tanda se le da, por cada tarea, un mensaje de commit recomendado, la lista de archivos a agregar y la explicación de cada archivo creado o modificado.

Tandas propuestas (cada una espera la orden del usuario): 1 = TASK-01; 2 = TASK-02 y TASK-03; 3 = TASK-04 y TASK-05; 4 = TASK-06 y TASK-07; 5 = TASK-08 y TASK-09; 6 = TASK-10 y TASK-11; 7 = TASK-12.

## Etapa 1 · El orden en la base

- [x] **TASK-01 · El orden de D2 en la base**
  - Alcance: la migración `V3__spanish_order_for_category_names.sql`, que crea la colación `spanish_insensitive` (ICU, `es-u-ks-level1`, no determinística) y se la asigna a `professional_categories.name` (P3). `ProfessionalCategoryRepository` suma `findAllOrderedByName()`; `JpaProfessionalCategoryRepository`, `findAllByOrderByNameAscIdAsc()`; y `JpaProfessionalCategoryRepositoryAdapter` lo implementa con el mapper existente. `CatalogRepositoryAdaptersTest` suma el orden. No se tocan el modelo, la entidad ni los mappers de persistencia.
  - Depende de: ninguna
  - Resuelve: base de RF-01 y RF-04; CA-02 (en la base)
  - Validación: línea base antes de tocar nada (`./mvnw -q clean test`, conteo en los XML). Después, `./mvnw test -Dtest=CatalogRepositoryAdaptersTest`: nombres en minúscula y mayúscula, con y sin tildes, uno con ñ, habilitadas y deshabilitadas, y dos iguales salvo mayúsculas o tildes, insertados en otro orden, vuelven en el orden de D2 con desempate por `id`; una tabla vacía devuelve una lista vacía. Los tests existentes de `catalog` siguen en verde (`./mvnw test -Dtest='catalogo/catalog/**/*Test'`). Prueba en negativo: `V3` sin asignar la colación a la columna, y el test del orden falla.
  - Resultado: 2026-11-20 (tanda 1). Creada `V3__spanish_order_for_category_names.sql`, con la colación `spanish_insensitive` asignada a `professional_categories.name`. `findAllOrderedByName()` sumado al puerto y al adaptador, y `findAllByOrderByNameAscIdAsc()` al repositorio de Spring Data. `CatalogRepositoryAdaptersTest` suma `allCategoriesComeInSpanishAlphabeticalOrderWithTiesById` (diez categorías cargadas desordenadas; comprueba el orden de los `id` y todos los campos) y `withoutCategoriesTheOrderedListIsEmpty`.
    - Línea base, antes de tocar nada: `./mvnw -q clean test`, 492 tests en 64 clases, 0 fallos ni errores.
    - `./mvnw test -Dtest=CatalogRepositoryAdaptersTest`: 9 tests, 0 fallos. En el log, Flyway aplica `V1`, `V2` y `V3` ("now at version v3").
    - `./mvnw test -Dtest='catalogo/catalog/**/*Test'`: 35 tests en 8 clases del slice, 0 fallos.
    - Orden obtenido: Nunez (9), Nuñez (8), Nutrición (4), Ñandú (2), odontología (5), Óptica (3), optica (6), Óptica (10), pediatría (1), Psicología (7).
    - Prueba en negativo: sin la línea `ALTER TABLE … COLLATE` en `V3`, falló el test del orden, porque la base devolvió otro orden en seis posiciones. Restaurada, `sha256sum -c` coincide. Como el mensaje del fallo mostraba objetos sin nombre, el test suma antes una comprobación por `id`, para que un fallo muestre el orden real.
    - `./mvnw -q clean test`: 494 tests en 64 clases (492 + 2), 0 fallos ni errores, 130 s.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los imports entre slices son solo los de la fachada y el gateway de `synchronization`, ya autorizados: TASK-01 no suma ninguno.

## Etapa 2 · El caso de uso y sus piezas

- [x] **TASK-02 · Caso de uso, service y excepciones del slice**
  - Alcance: `catalog/application/exception/CatalogException` (la base del slice, con su constructor `protected` con mensaje) y `CatalogNotInitializedException`; el puerto de entrada `GetAllProfessionalCategoriesUseCase` y `GetAllProfessionalCategoriesUseCaseImpl` (lee el estado por `CatalogSyncStateRepository`; sin inicializar, lanza la excepción; si no, devuelve `findAllOrderedByName()`; sin transacción, P5); `ProfessionalCategoryService` con `getAllProfessionalCategories()`. Sin controller ni seguridad.
  - Depende de: TASK-01
  - Resuelve: RF-01, RF-03, RF-04 y RF-06 en la aplicación; P4.2, P4.3, P5
  - Validación: `./mvnw test` en verde. Los chequeos de la skill (§12, del 1 al 5) sin resultados nuevos. El comportamiento lo prueban la entrada web (TASK-04) y la integración (TASK-06 y TASK-07): sin un test aparte del caso de uso, como dice el PLAN (P6).
  - Resultado: 2026-11-20 (tanda 2, autorizada ese día). Creados `CatalogException` (base del slice, solo con el constructor `protected` con mensaje, igual que la que se quitó en C03), `CatalogNotInitializedException`, el puerto `GetAllProfessionalCategoriesUseCase`, `GetAllProfessionalCategoriesUseCaseImpl` (lee el estado por `CatalogSyncStateRepository`, lanza la excepción si no está inicializado, y si no devuelve `findAllOrderedByName()`, sin `@Transactional`) y `ProfessionalCategoryService` (un método que delega en el puerto).
    - Compila junto con TASK-03 (`./mvnw test -Dtest=ProfessionalCategoryDtoMapperTest`, `BUILD SUCCESS`).
    - `./mvnw -q clean test`: 497 tests en 65 clases, 0 fallos ni errores, 124 s (incluye los 3 de TASK-03).
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices de antes (fachada y gateway de `synchronization`). El service depende solo del puerto de entrada, y el caso de uso solo de puertos de salida.
    - El comportamiento del caso de uso todavía no tiene test: lo prueban TASK-04 y TASK-06, como estaba previsto.

- [x] **TASK-03 · La respuesta y su mapper**
  - Alcance: `catalog/infrastructure/web/dto/ProfessionalCategoryResponse` (clase Lombok con `id`, `name`, `description` y `enabled`) y `catalog/infrastructure/web/mapper/ProfessionalCategoryDtoMapper` (solo `toResponse`, con el Builder; `null` devuelve `null`). `ProfessionalCategoryDtoMapperTest` (nuevo, skill §10).
  - Depende de: ninguna
  - Resuelve: RF-01; CA-03 (el mapper)
  - Validación: `./mvnw test -Dtest=ProfessionalCategoryDtoMapperTest`: ida y vuelta con todos los campos completos (`hasNoNullFieldsOrProperties`), `description` en `null` se conserva, y `null` da `null`. Prueba en negativo: quitar una línea del Builder, y el test falla.
  - Resultado: 2026-11-20 (tanda 2). Creados `ProfessionalCategoryResponse` (clase Lombok con los cuatro campos), `ProfessionalCategoryDtoMapper` (`toResponse` con el Builder; `null` da `null`) y `ProfessionalCategoryDtoMapperTest` con `domainToResponseKeepsEveryExposedField`, `aCategoryWithoutDescriptionKeepsItEmpty` y `nullMapsToNull`.
    - `./mvnw test -Dtest=ProfessionalCategoryDtoMapperTest`: 3 tests, 0 fallos.
    - Prueba en negativo: sin la línea `.description(...)` del Builder, falló `domainToResponseKeepsEveryExposedField` ("field/property 'description' differ"); el test con la descripción en `null` siguió pasando, como corresponde. Restaurado, `sha256sum -c` coincide.
    - Suite completa: la de TASK-02, 497 tests en 65 clases, 0 fallos.

## Etapa 3 · La entrada web

- [x] **TASK-04 · Controller, seguridad mínima y formato de errores**
  - Alcance:
    1. Primero, sin código de producción nuevo: `ProfessionalCategoriesIntegrationTest` (nuevo) con su primer escenario (copia inicializada con categorías y pedido sin encabezado: `200` con la lista), y `CatalogTables` con lo que haga falta para cargar la copia con SQL directo. Se corre en rojo y se registra qué responde hoy la seguridad por defecto, que la SPEC dejó para comprobar.
    2. Después: `config/SecurityConfig` (P1), `config/ApiExceptionHandler` (P2, decisión 005), `catalog/infrastructure/web/controller/ProfessionalCategoryController` (traduce `CatalogNotInitializedException` a `ResponseStatusException` `503` con `code` `CATALOG_NOT_INITIALIZED`), y `ProfessionalCategoryControllerTest` (nuevo, `@WebMvcTest` con `SecurityConfig` y el mapper importados y el service simulado).
  - Depende de: TASK-02, TASK-03
  - Resuelve: RF-01 a RF-05 en la web; CA-01 (forma del JSON), CA-03, CA-04, CA-06 y CA-08 (forma del `503` y del `500`); el pendiente de la SPEC sobre la seguridad por defecto
  - Validación: el escenario del paso 1 falla por el estado HTTP, no por compilación ni por el contexto, y queda registrado el estado y el cuerpo observados. Después, `./mvnw test -Dtest='ProfessionalCategoryControllerTest,ProfessionalCategoriesIntegrationTest'` en verde: el JSON con los cuatro campos y ningún otro, `"description": null` presente, los parámetros de consulta no cambian la respuesta, `503` y `500` en `application/problem+json` con su `code` y sin detalles internos, y el error no previsto en el log. Pruebas en negativo: el controller traduce a `500` en lugar de `503`; y el manejador sin la captura de lo no previsto.
  - Resultado: 2026-11-20 (tanda 3, autorizada ese día).
    - Paso 1: `CatalogTables` suma `insertCategory` e `initializeAt(versión)`, y `ProfessionalCategoriesIntegrationTest` (nuevo) su primer escenario, `anInitializedCopyListsEveryCategoryWithItsFields`. Corrido antes de crear la seguridad y el controller, falló por el estado: **`401 Unauthorized`, sin cuerpo, con `WWW-Authenticate: Basic realm="Realm"` y una cookie `JSESSIONID`**. En el log no hay una contraseña generada. Queda comprobado lo que la SPEC dejó deducido: la cadena por defecto de Spring Boot exige autenticación básica o por formulario y no crea un usuario.
    - Paso 2: creados `config/SecurityConfig` (sin sesión; `PUBLIC_ENDPOINTS` con `GET /api/professional-categories`; el resto exige autenticación; CSRF como viene), `config/ApiExceptionHandler` (hereda de `ResponseEntityExceptionHandler`; `@ExceptionHandler(Exception.class)` deja `Unexpected error` en el log con la traza y responde `500` `INTERNAL_ERROR` con un texto fijo) y `ProfessionalCategoryController` (atrapa `CatalogNotInitializedException` y lanza `ResponseStatusException` `503` con `code` `CATALOG_NOT_INITIALIZED`). `ProfessionalCategoryControllerTest` (nuevo, `@WebMvcTest` con `SecurityConfig` y el mapper importados y el service simulado con `@MockitoBean`): `theListHasTheFourFieldsOfEachCategoryAndNothingElse` (JSON comparado en modo estricto), `aCategoryWithoutDescriptionKeepsTheFieldWithNull` (`"description":null` en el cuerpo), `queryParametersDoNotChangeTheResponse`, `aCatalogNotInitializedAnswers503AsAProblem` y `aDatabaseFailureAnswers500WithoutInternalDetailsAndIsLogged`.
    - `./mvnw test -Dtest='ProfessionalCategoryControllerTest,ProfessionalCategoriesIntegrationTest'`: 6 tests, 0 fallos; el escenario del paso 1 ahora responde `200` con la lista en el orden de D2.
    - Prueba en negativo 1: el controller con `INTERNAL_SERVER_ERROR` en lugar de `SERVICE_UNAVAILABLE`: falló `aCatalogNotInitializedAnswers503AsAProblem` ("Status expected:<503> but was:<500>").
    - Prueba en negativo 2: el manejador sin `@ExceptionHandler(Exception.class)`: falló con error `aDatabaseFailureAnswers500WithoutInternalDetailsAndIsLogged`, porque la excepción se escapó con su mensaje interno (host y tabla). El caso del `503` siguió pasando, porque lo resuelve la parte heredada del manejador. Restaurados, `sha256sum -c` coincide en los dos archivos.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports de `synchronization`; `catalogo.config` no importa nada de los slices; el controller no importa puertos ni casos de uso.
    - Suite completa: la de TASK-05.

- [x] **TASK-05 · El encabezado `Authorization` se ignora**
  - Alcance: `src/test/java/catalogo/TestTokens` (nuevo): una clave RSA generada en cada corrida y tokens bien firmados, vencidos, alterados o que no son un JWT, armados con la librería de JWT que ya trae el resource server. `ProfessionalCategoryControllerTest` suma un pedido por variante del encabezado, también `Basic …`. Sin cambios en el código de producción.
  - Depende de: TASK-04
  - Resuelve: RF-02; CA-05; deja `TestTokens` para la validación del JWT
  - Validación: `./mvnw test -Dtest=ProfessionalCategoryControllerTest`: cada respuesta es igual a la del pedido sin encabezado. Prueba en negativo: agregar a la cadena de seguridad la lectura del token con un decodificador que rechaza todo, que es justo el riesgo anotado para la validación del JWT, y los tests de los tokens inválidos fallan.
  - Resultado: 2026-11-20 (tanda 3). Creado `src/test/java/catalogo/TestTokens` (clave RSA de 2048 bits generada en cada corrida; `valid()`, `expired()` y `altered()`, la firma de un token con los datos de otro; con nimbus-jose-jwt, que ya trae el resource server). `ProfessionalCategoryControllerTest` suma `anyAuthorizationHeaderGetsTheSameResponseAsNoHeader`, parametrizado con un token bien firmado, uno vencido, uno alterado, `Bearer not-a-token` y `Basic …`.
    - `./mvnw test -Dtest=ProfessionalCategoryControllerTest`: 10 tests, 0 fallos (los 5 de TASK-04 y las 5 variantes).
    - Prueba en negativo: `SecurityConfig` con `.oauth2ResourceServer(...)` y un decodificador que rechaza todo: fallaron las 4 variantes `Bearer` con `401` en la ruta pública, y `Basic` siguió pasando, porque ese filtro solo lee tokens `Bearer`. Confirma con el código real el riesgo anotado en la SPEC para la validación del JWT, y que CA-05 lo detecta. Restaurado, `sha256sum -c` coincide.
    - `./mvnw -q clean test`: 508 tests en 67 clases (497 + 1 de integración + 10 del controller), 0 fallos ni errores, 126 s.

## Etapa 4 · La integración con la aplicación

- [x] **TASK-06 · Con la aplicación levantada y la base real**
  - Alcance: `ProfessionalCategoriesIntegrationTest` suma escenarios: los valores salen de la copia y la cátedra simulada no recibe pedidos durante la consulta (CA-01); la respuesta en el orden de D2 (CA-02); sin versión local, con y sin categorías, `503` y las tablas sin cambios (CA-06); inicializada y sin categorías, `[]` (CA-07); y otras rutas sin autenticación, rechazadas sin datos (CA-12).
  - Depende de: TASK-04
  - Resuelve: CA-01, CA-02, CA-06, CA-07, CA-12; RF-01, RF-03, RF-04, RF-07
  - Validación: `./mvnw test -Dtest=ProfessionalCategoriesIntegrationTest` en verde, con el estado observado de CA-12 registrado (lo esperable, `403`). Prueba en negativo: el caso de uso mira si hay categorías en lugar de la versión local, y falla el escenario de CA-06 con categorías cargadas.
  - Resultado: 2026-11-20 (tanda 4, autorizada ese día). `ProfessionalCategoriesIntegrationTest` pasa de 1 a 10 casos. Antes de cada uno espera el login del arranque, que corre en otro hilo, para que no se cuente como un pedido de la consulta.
    - Casos nuevos: `listingReadsOnlyTheLocalCopyWithoutCallingTheCatedra` (CA-01: la cátedra simulada no recibe ningún pedido durante la consulta), `theResponseComesInSpanishAlphabeticalOrderWithTiesById` (CA-02: `id` 4, 2, 3, 5, 1, es decir Nutrición, Ñandú, Óptica, optica, pediatría), `withoutALocalVersionButWithCategoriesItAnswers503AndChangesNothing` y `withoutALocalVersionAndWithoutCategoriesItAnswers503AndChangesNothing` (CA-06: `503` `problem+json` `CATALOG_NOT_INITIALIZED`; categorías, estado y pedidos a la cátedra iguales antes y después), `anInitializedCopyWithoutCategoriesAnswersAnEmptyList` (CA-07: `[]`) y `anyOtherRouteWithoutAuthenticationIsRejectedWithoutData` (CA-12, parametrizado).
    - `./mvnw test -Dtest=ProfessionalCategoriesIntegrationTest`: 10 tests, 0 fallos.
    - CA-12, estado observado con el servidor real: `GET /api/professionals`, `GET /api/professionals/15`, `GET /api/synchronization/status` y `POST /api/professional-categories` sin autenticación respondieron **`403`**, sin datos de la copia.
    - Prueba en negativo: el caso de uso con `findAllOrderedByName().isEmpty()` en lugar de `!isInitialized()`. Fallaron los dos casos que distinguen "inicializada" de "tiene categorías": CA-06 con categorías respondió `200`, y CA-07 respondió `503`. Restaurado, `sha256sum -c` coincide.
    - Suite completa: la de TASK-07.

- [x] **TASK-07 · Fallas de la base y una sincronización abierta**
  - Alcance (ajustado el 2026-11-20, aprobado por el usuario al empezar la tanda 4: los escenarios van a una clase propia, para que cada tarea tenga sus archivos y su commit, y para aislar los que tocan el esquema de la base): `ProfessionalCategoriesDatabaseIntegrationTest` (nueva, sin la cátedra configurada) con CA-08 (el test renombra `catalog_sync_state`, y en otro escenario `professional_categories`, y las restaura siempre al terminar) y CA-10 (el test abre su propia conexión sin confirmación automática, cambia nombres y suma una categoría, no confirma, pide la lista, confirma y la pide otra vez).
  - Depende de: TASK-04
  - Resuelve: CA-08, CA-10; RF-05, RF-06
  - Validación: `./mvnw test -Dtest=ProfessionalCategoriesDatabaseIntegrationTest` en verde: `500` `INTERNAL_ERROR` sin SQL, nombres de tablas o clases ni trazas, y un error en el log; con la transacción abierta, la copia anterior dentro de un tiempo máximo, y después la nueva. Una corrida completa de la clase deja las tablas con sus nombres. Prueba en negativo: la consulta de categorías con un bloqueo de lectura (`@Lock(PESSIMISTIC_READ)` en el repositorio de Spring Data, dentro de una transacción), que espera a la transacción abierta, y CA-10 falla por tiempo.
  - Resultado: 2026-11-20 (tanda 4). `ProfessionalCategoriesDatabaseIntegrationTest` (nueva) arranca con `catalogo.catedra.base-url` vacía, porque no usa la cátedra. Prepara una copia con una categoría, y después de cada caso comprueba que `catalog_sync_state` y `professional_categories` siguen con su nombre.
    - Casos: `aFailureReadingTheSyncStateAnswers500WithoutInternalDetails` y `aFailureReadingTheCategoriesAnswers500WithoutInternalDetails` (CA-08: renombran la tabla, piden la lista y la restauran en un `finally`; `500` `problem+json` `INTERNAL_ERROR`; el cuerpo no tiene el nombre de la tabla, "relation", "does not exist", "SQL", "select" ni "Exception"; el log tiene `Unexpected error` con `InvalidDataAccessResourceUsageException` y `relation "…" does not exist`). Y `aSynchronizationNotYetConfirmedIsNotSeenAndDoesNotMakeTheListWait` (CA-10: una conexión propia sin confirmación automática cambia "Pediatría" a "Zoología" y suma "Cardiología" sin confirmar; la lista responde dentro de 5 s con solo "Pediatría"; después de confirmar, "Cardiología" y "Zoología").
    - `./mvnw test -Dtest=ProfessionalCategoriesDatabaseIntegrationTest`: 3 tests, 0 fallos.
    - Prueba en negativo 1: `findAllByOrderByNameAscIdAsc` con `@Lock(PESSIMISTIC_WRITE)` y `@Transactional` (un `SELECT … FOR UPDATE`). CA-10 falló con "execution timed out after 5000 ms": la lectura esperó a la sincronización abierta. Restaurado, `sha256sum -c` coincide.
    - Prueba en negativo 2, no prevista en TASKS: `ApiExceptionHandler` sin `@ExceptionHandler(Exception.class)`, con el servidor real. Los dos casos de CA-08 recibieron **`403 Forbidden`** en lugar de `500`: el error se reenvió a `/error` y la seguridad rechazó el reenvío. Confirma el riesgo anotado en el PLAN, que `@WebMvcTest` no podía mostrar. Restaurado, `sha256sum -c` coincide.
    - `./mvnw -q clean test`: 520 tests en 68 clases (508 + 9 de TASK-06 + 3 de TASK-07), 0 fallos ni errores, 129 s.

- [x] **TASK-08 · Sin integración con la cátedra**
  - Alcance: `ProfessionalCategoriesWithoutCatedraIntegrationTest` (nuevo): la aplicación sin la URL de la cátedra, que arranca sin integración, con la copia cargada con SQL directo.
  - Depende de: TASK-04
  - Resuelve: CA-11
  - Validación: `./mvnw test -Dtest=ProfessionalCategoriesWithoutCatedraIntegrationTest`: `200` con la lista, y el warning de "sin integración" en el log del arranque. Prueba en negativo: romper el código haría que `catalog` dependa de `catedra` solo para la prueba, así que se comprueba que el test distingue el resultado: con la copia sin inicializar tiene que fallar (`503` en lugar de `200`).
  - Resultado: 2026-11-20 (tanda 5, autorizada ese día). Creado `ProfessionalCategoriesWithoutCatedraIntegrationTest`, con `catalogo.catedra.base-url` vacía y un solo método, `theListIsServedFromTheLocalCopyWithoutTheCatedra`. Lleva `@DirtiesContext(classMode = BEFORE_CLASS)` porque `ProfessionalCategoriesDatabaseIntegrationTest` tiene la misma configuración: sin eso, Spring reutilizaría ese contexto y el warning del arranque no se vería.
    - Corrido junto con la clase de TASK-07, que va antes: dos arranques, con el warning `Technical login disabled: missing configuration properties [catalogo.catedra.base-url, …]` en cada uno; 4 tests, 0 fallos. Responde `200` con "Kinesiología" y "Pediatría".
    - Prueba en negativo (del test): sin `initializeAt(57)`, falló con "Status expected:<200 OK> but was:<503 SERVICE_UNAVAILABLE>". Restaurado, `sha256sum -c` coincide.
    - Suite completa: la de TASK-09.

- [x] **TASK-09 · La base detenida**
  - Alcance: `ProfessionalCategoriesDatabaseDownIntegrationTest` (nuevo): su propio contenedor de PostgreSQL, no el compartido; el tiempo de espera del pool bajado solo en ese test; `@DirtiesContext`. El contenedor se detiene antes del pedido.
  - Depende de: TASK-04
  - Resuelve: CA-09; RF-05 (D4)
  - Validación: `./mvnw test -Dtest=ProfessionalCategoriesDatabaseDownIntegrationTest`: `500` `INTERNAL_ERROR` sin detalles internos y un error en el log, dentro del tiempo del pool. Se corre con `timeout`. Prueba en negativo: el manejador sin la captura de lo no previsto, y el test falla.
  - Resultado: 2026-11-20 (tanda 5). Creado `ProfessionalCategoriesDatabaseDownIntegrationTest`: su propio `PostgreSQLContainer` (`postgres:18`), conectado con `@DynamicPropertySource`, sin la configuración compartida; `spring.datasource.hikari.connection-timeout=2000`; `@DirtiesContext`; `@Timeout(120)`. El caso confirma `200` con la base arriba, la detiene y pide la lista dos veces.
    - La primera versión pedía una sola vez, y el log mostró que la falla no pasaba por el pool: la conexión recién usada se entregó sin comprobar y la consulta falló en el acto (`EOFException`, "An I/O error occurred while sending to the backend"), unos 25 ms después de detener la base. Se sumó un segundo pedido, para recorrer el camino que D4 había dejado deducido, y se corrigió el comentario de la clase.
    - `./mvnw test -Dtest=ProfessionalCategoriesDatabaseDownIntegrationTest`: 1 test, 0 fallos, 13 s. Primer pedido: `500` en el acto. Segundo: `500` después de "HikariPool-1 - Connection is not available, request timed out after 2000ms" (`SQLTransientConnectionException`). Los dos en `problem+json` con `INTERNAL_ERROR`, sin detalles internos y en menos de 10 s.
    - Prueba en negativo: el manejador sin `@ExceptionHandler(Exception.class)`. Falló con "Status expected:<500> but was:<403 FORBIDDEN>", el mismo reenvío a `/error` de TASK-07. Restaurado, `sha256sum -c` coincide.
    - Al terminar, ningún contenedor de PostgreSQL queda corriendo (`docker ps`).
    - `./mvnw -q clean test`: 522 tests en 70 clases (520 + 1 + 1), 0 fallos ni errores, 138 s.
    - **Corrección del 2026-11-20, después del cierre (autorizada por el usuario):** en GitHub Actions el test falló: los dos pedidos dieron `500`, pero el log no tenía "Connection is not available, request timed out". La causa: Hikari entrega sin comprobar una conexión usada hace menos de 500 ms. En la máquina de desarrollo detener la base tardaba unos 0,8 s, más que ese margen, y el segundo pedido comprobaba las conexiones; en GitHub, más rápido, los dos pedidos recibieron conexiones recién usadas y fallaron en el acto. Turnos ya lo tenía anotado en su `CLAUDE.md`, y no se aplicó.
      - Reproducción: con `com.zaxxer.hikari.aliveBypassWindowMs` en 10 s, puesta solo para la prueba, el test sin cambios falló con el mismo mensaje que en GitHub, y en el log los dos pedidos tenían "An I/O error occurred while sending to the backend". Restaurado, `sha256sum -c` coincide.
      - Arreglo: el test espera 1 s entre los dos pedidos, con Awaitility (`pollDelay`, sin `Thread.sleep`). El segundo pedido queda obligado a comprobar las conexiones y a esperar al pool.
      - Cinco corridas seguidas: las cinco pasaron, y en todas apareció el mensaje del pool. En tres, el primer pedido falló por la conexión cortada, y en dos también fue al pool: el camino del primero depende de la máquina; el del segundo, ya no.

## Etapa 5 · Regresión y prueba manual

- [x] **TASK-10 · Regresión completa**
  - Alcance: ninguno en el código.
  - Depende de: TASK-01 a TASK-09
  - Resuelve: todos los CA en conjunto; la regresión de X01, C01, C02 y C03
  - Validación: `./mvnw clean verify` en verde; el conteo de tests en los XML, igual a la línea base más los nuevos; la cobertura de JaCoCo de las clases nuevas; los chequeos de la skill (§12, del 1 al 5), con los imports entre slices sin cambios.
  - Resultado: 2026-11-20 (tanda 6, autorizada ese día).
    - `./mvnw -q clean verify`: 522 tests en 70 clases, 0 fallos ni errores, 137 s. Es la línea base de 492 más 30 nuevos: 2 de adaptadores, 3 del mapper web, 10 del controller, 10 de `ProfessionalCategoriesIntegrationTest`, 3 de `ProfessionalCategoriesDatabaseIntegrationTest`, 1 de `ProfessionalCategoriesWithoutCatedraIntegrationTest` y 1 de `ProfessionalCategoriesDatabaseDownIntegrationTest`.
    - Cobertura de JaCoCo (`target/site/jacoco/jacoco.xml`): total, 97,0 % de líneas (1441/1486) y 93,4 % de ramas (481/515); al cerrar C02, 96,9 % y 93,3 %. Las clases nuevas o tocadas, al 100 % de líneas y de ramas: `SecurityConfig`, `ApiExceptionHandler`, `ProfessionalCategoryController`, `ProfessionalCategoryDtoMapper`, `ProfessionalCategoryService`, `GetAllProfessionalCategoriesUseCaseImpl`, `CatalogException`, `CatalogNotInitializedException` y `JpaProfessionalCategoryRepositoryAdapter`. `ProfessionalCategoryResponse` no cuenta líneas, porque todo su código lo genera Lombok.
    - Chequeos de la skill (§12): del 1 al 4 sin resultados; en el 5, los mismos 27 imports entre slices, todos de la fachada y el gateway de `synchronization`, ya autorizados; ningún paquete vacío.

- [x] **TASK-11 · Prueba manual con la aplicación**
  - Alcance: con el OK del usuario, que ve el comando antes. Anotar cómo estaba el compose; levantar la base de desarrollo si hacía falta; correr la aplicación con `CATALOGO_CATEDRA_BASE_URL` vacía y `CATALOGO_VERIFICATION_ENABLED=false` en el entorno del comando, para no llamar a la cátedra real; pedir la lista con `curl -i`, sin encabezado y con un `Authorization` inventado. Después, la aplicación detenida y el compose como estaba.
  - Depende de: TASK-10
  - Resuelve: comprobación real de RF-01 a RF-03 y de `V3` sobre una base con datos
  - Validación: el log del arranque con el warning de "sin integración" y Flyway aplicando `V3` sobre una base con `V1` y `V2`; la respuesta real (`200` con las categorías ya sincronizadas, en el orden de D2, o `503` si esa base no tiene versión local), igual con y sin el encabezado. Se registran los comandos y lo observado, sin valores del `.env`.
  - Resultado: 2026-11-20 (tanda 6). La corrió el usuario en su terminal, con los comandos que se le pasaron. Claude leyó el log y las respuestas, que quedaron en `target/`, revisando antes que no tuvieran valores sensibles (la palabra "password" aparece solo como nombre de propiedad), y después los borró.
    - Estado previo: el compose de catálogo sin contenedores, ni corriendo ni detenidos; el volumen `catalog_service_catalog-db-data` existía.
    - Comandos: `docker compose up -d --wait catalog-db`; `CATALOGO_CATEDRA_BASE_URL= CATALOGO_CATEDRA_USERNAME= CATALOGO_CATEDRA_PASSWORD= CATALOGO_VERIFICATION_ENABLED=false CATALOGO_CATEDRA_KAFKA_ENABLED=false ./mvnw spring-boot:run` en segundo plano; tres `curl -i`; `kill` del proceso que escuchaba en el puerto; `docker compose down`, sin `-v`.
    - Arranque: warning `Technical login disabled: missing configuration properties [catalogo.catedra.base-url, catalogo.catedra.username, catalogo.catedra.password]`, y el `ERROR` de X01 "configuration error, no retries until restart": ningún pedido a la cátedra real. `Tomcat started on port 8081`; arrancó en 3,0 s.
    - **La base de desarrollo estaba vacía** (`Current version of schema "public": << Empty Schema >>`), así que Flyway aplicó `V1`, `V2` y `V3` desde cero. Desde esta prueba, esa base tiene el esquema en `v3`, sin datos.
    - `GET /api/professional-categories`, sin encabezado y con `Authorization: Bearer inventado`: las dos respuestas idénticas, `503` con `Content-Type: application/problem+json` y `{"detail":"The catalog has not been initialized yet","instance":"/api/professional-categories","status":503,"title":"Service Unavailable","code":"CATALOG_NOT_INITIALIZED"}`. Ninguna respuesta trae `Set-Cookie`: sin sesión, a diferencia de la seguridad por defecto observada en TASK-04.
    - `GET /api/professionals` sin autenticación: `403` sin cuerpo (`Content-Length: 0`).
    - Al detenerla: "Graceful shutdown complete"; Maven terminó con `exit code 143`, que corresponde a `SIGTERM`, el `kill` esperado, no a una falla.
    - No se pudo observar en esta prueba: el `200` con datos reales, porque la base estaba vacía (lo cubren CA-01, CA-02 y CA-07 en integración), ni `V3` sobre una base existente. Esto último se comprobó aparte, en un PostgreSQL 18 descartable: con `V1`, `V2` y datos cargados (cinco categorías con tildes, ñ y un empate, y un profesional que referencia una), aplicar `V3` dejó las 5 filas iguales, la referencia intacta, el orden Nutrición, Ñandú, Óptica (3), optica (5), pediatría, y la columna con la colación `spanish_insensitive`. El contenedor se detuvo.

## Etapa 6 · Cierre

- [x] **TASK-12 · Documentación del servicio**
  - Alcance: el `CLAUDE.md`: el slice `catalog` con C04; `catalogo.config` con `SecurityConfig` y `ApiExceptionHandler`; las excepciones de C04 (P7); cómo se agrega una ruta pública y cómo responde un error; la colación de `name` y su efecto al comparar; las clases de test nuevas y `TestTokens`; las decisiones 005 y 006 en la lista; la carpeta `c04-listar-categorias` entre las hechas. Este `TASKS.md` con lo observado.
  - Depende de: TASK-11
  - Resuelve: cierre de C04
  - Validación: las rutas y los nombres citados existen (comprobado con `ls` o `grep`); las copias de `contratos_comunes.md` y de X01 siguen idénticas en los tres repositorios.
  - Resultado: 2026-11-20 (tanda 7, autorizada ese día). El `CLAUDE.md` suma:
    - en "Comandos", C04 en la lista de lo comprobado y la forma de correr la aplicación sin la integración con la cátedra (TASK-11);
    - en "Datos del proyecto", qué dependencias se usan desde C04;
    - en la tabla de slices, la entrada web de `catalog` y las dos piezas nuevas de `catalogo.config`;
    - las excepciones a la skill de C04 (P7) y lo que no es excepción;
    - tres secciones nuevas: "Seguridad (desde C04)", con lo que hereda la validación del JWT; "Cómo responde un error (desde C04)", con el reenvío a `/error` comprobado; y "El orden de las categorías (desde C04)", con el efecto de la colación al comparar y que `V3` ya no se toca;
    - en "Tests", las técnicas nuevas: integración por HTTP, `@WebMvcTest`, `CatalogTables`, `TestTokens`, falla de la base a pedido, `@DirtiesContext(BEFORE_CLASS)` y la base detenida;
    - en la lista de documentos, la carpeta de C04 y las decisiones 005 y 006.
    - Comprobación: los 26 nombres de clases, métodos, constantes y propiedades citados existen en `src` (`grep`), y existen las decisiones 005 y 006, la carpeta de C04 y `V3`. Las copias de `contratos_comunes.md`, del caso de uso X01 y de su secuencia son idénticas en los tres repositorios (`md5sum`), y las de catálogo (casos de uso, contrato, dominio y secuencia) coinciden con `documentacion` (`cmp`).

## Cierre

C04 terminado el 2026-11-20: TASK-01 a TASK-12 hechas y validadas. `./mvnw clean verify`: 522 tests en 70 clases, 0 fallos; cobertura del 97,0 % de líneas y 93,4 % de ramas. Quedan para la validación del JWT (después de C05) los puntos anotados en la SPEC ("Para la validación del JWT") y en la sección "Seguridad" del `CLAUDE.md`. Ajustes aprobados durante la implementación: la etapa 1 del PLAN pasó a TASK-04, y los escenarios de TASK-07 pasaron a su propia clase.
