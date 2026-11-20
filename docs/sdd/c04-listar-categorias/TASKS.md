# TASKS: C04 — Listar categorías

SPEC: `docs/sdd/c04-listar-categorias/SPEC.md` (aprobada el 2026-11-20). PLAN: `docs/sdd/c04-listar-categorias/PLAN.md` (aprobado el 2026-11-20). Decisiones: `docs/decisiones/005-manejador-global-de-errores.md` y `docs/decisiones/006-resultados-sellados-o-excepciones.md`. Tareas aprobadas por el usuario el 2026-11-20, junto con el ajuste que mueve la etapa 1 del PLAN (el test en rojo) al comienzo de TASK-04; la implementación sigue su autorización, tanda por tanda (tanda 1: TASK-01, y tanda 2: TASK-02 y TASK-03, autorizadas el 2026-11-20).

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

- [ ] **TASK-04 · Controller, seguridad mínima y formato de errores**
  - Alcance:
    1. Primero, sin código de producción nuevo: `ProfessionalCategoriesIntegrationTest` (nuevo) con su primer escenario (copia inicializada con categorías y pedido sin encabezado: `200` con la lista), y `CatalogTables` con lo que haga falta para cargar la copia con SQL directo. Se corre en rojo y se registra qué responde hoy la seguridad por defecto, que la SPEC dejó para comprobar.
    2. Después: `config/SecurityConfig` (P1), `config/ApiExceptionHandler` (P2, decisión 005), `catalog/infrastructure/web/controller/ProfessionalCategoryController` (traduce `CatalogNotInitializedException` a `ResponseStatusException` `503` con `code` `CATALOG_NOT_INITIALIZED`), y `ProfessionalCategoryControllerTest` (nuevo, `@WebMvcTest` con `SecurityConfig` y el mapper importados y el service simulado).
  - Depende de: TASK-02, TASK-03
  - Resuelve: RF-01 a RF-05 en la web; CA-01 (forma del JSON), CA-03, CA-04, CA-06 y CA-08 (forma del `503` y del `500`); el pendiente de la SPEC sobre la seguridad por defecto
  - Validación: el escenario del paso 1 falla por el estado HTTP, no por compilación ni por el contexto, y queda registrado el estado y el cuerpo observados. Después, `./mvnw test -Dtest='ProfessionalCategoryControllerTest,ProfessionalCategoriesIntegrationTest'` en verde: el JSON con los cuatro campos y ningún otro, `"description": null` presente, los parámetros de consulta no cambian la respuesta, `503` y `500` en `application/problem+json` con su `code` y sin detalles internos, y el error no previsto en el log. Pruebas en negativo: el controller traduce a `500` en lugar de `503`; y el manejador sin la captura de lo no previsto.
  - Resultado: pendiente

- [ ] **TASK-05 · El encabezado `Authorization` se ignora**
  - Alcance: `src/test/java/catalogo/TestTokens` (nuevo): una clave RSA generada en cada corrida y tokens bien firmados, vencidos, alterados o que no son un JWT, armados con la librería de JWT que ya trae el resource server. `ProfessionalCategoryControllerTest` suma un pedido por variante del encabezado, también `Basic …`. Sin cambios en el código de producción.
  - Depende de: TASK-04
  - Resuelve: RF-02; CA-05; deja `TestTokens` para la validación del JWT
  - Validación: `./mvnw test -Dtest=ProfessionalCategoryControllerTest`: cada respuesta es igual a la del pedido sin encabezado. Prueba en negativo: agregar a la cadena de seguridad la lectura del token con un decodificador que rechaza todo, que es justo el riesgo anotado para la validación del JWT, y los tests de los tokens inválidos fallan.
  - Resultado: pendiente

## Etapa 4 · La integración con la aplicación

- [ ] **TASK-06 · Con la aplicación levantada y la base real**
  - Alcance: `ProfessionalCategoriesIntegrationTest` suma escenarios: los valores salen de la copia y la cátedra simulada no recibe pedidos durante la consulta (CA-01); la respuesta en el orden de D2 (CA-02); sin versión local, con y sin categorías, `503` y las tablas sin cambios (CA-06); inicializada y sin categorías, `[]` (CA-07); y otras rutas sin autenticación, rechazadas sin datos (CA-12).
  - Depende de: TASK-04
  - Resuelve: CA-01, CA-02, CA-06, CA-07, CA-12; RF-01, RF-03, RF-04, RF-07
  - Validación: `./mvnw test -Dtest=ProfessionalCategoriesIntegrationTest` en verde, con el estado observado de CA-12 registrado (lo esperable, `403`). Prueba en negativo: el caso de uso mira si hay categorías en lugar de la versión local, y falla el escenario de CA-06 con categorías cargadas.
  - Resultado: pendiente

- [ ] **TASK-07 · Fallas de la base y una sincronización abierta**
  - Alcance: `ProfessionalCategoriesIntegrationTest` suma CA-08 (el test renombra `catalog_sync_state`, y en otro escenario `professional_categories`, y las restaura siempre al terminar) y CA-10 (el test abre su propia conexión sin confirmación automática, cambia nombres y suma una categoría, no confirma, pide la lista, confirma y la pide otra vez).
  - Depende de: TASK-06
  - Resuelve: CA-08, CA-10; RF-05, RF-06
  - Validación: `./mvnw test -Dtest=ProfessionalCategoriesIntegrationTest` en verde: `500` `INTERNAL_ERROR` sin SQL, nombres de tablas o clases ni trazas, y un error en el log; con la transacción abierta, la copia anterior dentro de un tiempo máximo, y después la nueva. Una corrida completa de la clase deja las tablas con sus nombres. Prueba en negativo: la consulta de categorías con un bloqueo de lectura (`@Lock(PESSIMISTIC_READ)` en el repositorio de Spring Data, dentro de una transacción), que espera a la transacción abierta, y CA-10 falla por tiempo.
  - Resultado: pendiente

- [ ] **TASK-08 · Sin integración con la cátedra**
  - Alcance: `ProfessionalCategoriesWithoutCatedraIntegrationTest` (nuevo): la aplicación sin la URL de la cátedra, que arranca sin integración, con la copia cargada con SQL directo.
  - Depende de: TASK-04
  - Resuelve: CA-11
  - Validación: `./mvnw test -Dtest=ProfessionalCategoriesWithoutCatedraIntegrationTest`: `200` con la lista, y el warning de "sin integración" en el log del arranque. Prueba en negativo: romper el código haría que `catalog` dependa de `catedra` solo para la prueba, así que se comprueba que el test distingue el resultado: con la copia sin inicializar tiene que fallar (`503` en lugar de `200`).
  - Resultado: pendiente

- [ ] **TASK-09 · La base detenida**
  - Alcance: `ProfessionalCategoriesDatabaseDownIntegrationTest` (nuevo): su propio contenedor de PostgreSQL, no el compartido; el tiempo de espera del pool bajado solo en ese test; `@DirtiesContext`. El contenedor se detiene antes del pedido.
  - Depende de: TASK-04
  - Resuelve: CA-09; RF-05 (D4)
  - Validación: `./mvnw test -Dtest=ProfessionalCategoriesDatabaseDownIntegrationTest`: `500` `INTERNAL_ERROR` sin detalles internos y un error en el log, dentro del tiempo del pool. Se corre con `timeout`. Prueba en negativo: el manejador sin la captura de lo no previsto, y el test falla.
  - Resultado: pendiente

## Etapa 5 · Regresión y prueba manual

- [ ] **TASK-10 · Regresión completa**
  - Alcance: ninguno en el código.
  - Depende de: TASK-01 a TASK-09
  - Resuelve: todos los CA en conjunto; la regresión de X01, C01, C02 y C03
  - Validación: `./mvnw clean verify` en verde; el conteo de tests en los XML, igual a la línea base más los nuevos; la cobertura de JaCoCo de las clases nuevas; los chequeos de la skill (§12, del 1 al 5), con los imports entre slices sin cambios.
  - Resultado: pendiente

- [ ] **TASK-11 · Prueba manual con la aplicación**
  - Alcance: con el OK del usuario, que ve el comando antes. Anotar cómo estaba el compose; levantar la base de desarrollo si hacía falta; correr la aplicación con `CATALOGO_CATEDRA_BASE_URL` vacía y `CATALOGO_VERIFICATION_ENABLED=false` en el entorno del comando, para no llamar a la cátedra real; pedir la lista con `curl -i`, sin encabezado y con un `Authorization` inventado. Después, la aplicación detenida y el compose como estaba.
  - Depende de: TASK-10
  - Resuelve: comprobación real de RF-01 a RF-03 y de `V3` sobre una base con datos
  - Validación: el log del arranque con el warning de "sin integración" y Flyway aplicando `V3` sobre una base con `V1` y `V2`; la respuesta real (`200` con las categorías ya sincronizadas, en el orden de D2, o `503` si esa base no tiene versión local), igual con y sin el encabezado. Se registran los comandos y lo observado, sin valores del `.env`.
  - Resultado: pendiente

## Etapa 6 · Cierre

- [ ] **TASK-12 · Documentación del servicio**
  - Alcance: el `CLAUDE.md`: el slice `catalog` con C04; `catalogo.config` con `SecurityConfig` y `ApiExceptionHandler`; las excepciones de C04 (P7); cómo se agrega una ruta pública y cómo responde un error; la colación de `name` y su efecto al comparar; las clases de test nuevas y `TestTokens`; las decisiones 005 y 006 en la lista; la carpeta `c04-listar-categorias` entre las hechas. Este `TASKS.md` con lo observado.
  - Depende de: TASK-11
  - Resuelve: cierre de C04
  - Validación: las rutas y los nombres citados existen (comprobado con `ls` o `grep`); las copias de `contratos_comunes.md` y de X01 siguen idénticas en los tres repositorios.
  - Resultado: pendiente
