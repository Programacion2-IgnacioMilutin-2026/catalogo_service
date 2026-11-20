# PLAN: C04 — Listar categorías

**SPEC de referencia:** `docs/sdd/c04-listar-categorias/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-20 (rama `listar-categorias`, sin commitear)
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
| Modelo de la categoría | `catalog/domain/model/ProfessionalCategory.java` | `id`, `name`, `description` y `enabled`: los cuatro campos de la respuesta. Se reutiliza sin cambios |
| Estado de sincronización | `catalog/domain/model/CatalogSyncState.java` | `isInitialized()` (la versión local tiene valor) es la regla de RF-03. Se reutiliza sin cambios |
| Puerto y adaptador del estado | `catalog/domain/ports/out/CatalogSyncStateRepository.java`, `catalog/infrastructure/persistence/adapter/JpaCatalogSyncStateRepositoryAdapter.java` | `get()` lee la única fila del estado. Se reutiliza |
| Lectura del estado como caso de uso (C03) | `catalog/domain/ports/in/GetCatalogSyncStateUseCase.java` y su `Impl` | Lo usa la fachada de `synchronization`. Si C04 lo reutiliza o lee por el puerto de salida, en P4 |
| Puerto y persistencia de las categorías | `catalog/domain/ports/out/ProfessionalCategoryRepository.java`, `catalog/infrastructure/persistence/adapter/JpaProfessionalCategoryRepositoryAdapter.java`, `.../repository/JpaProfessionalCategoryRepository.java`, `.../mapper/ProfessionalCategoryMapper.java` | Hoy guardan, quitan y leen por ids (`findAllByIds`). Falta leer todas: se amplían, sin piezas paralelas |
| Tabla de categorías | `src/main/resources/db/migration/V1__create_catalog.sql` | `name` obligatorio y sin `UNIQUE`; `description` puede ser `null`. El estado es una sola fila (`id` 1) |
| Paquete de configuración | `config/ClockConfig.java`, `config/SchedulingConfig.java` | Configuración transversal fuera de los slices, excepción autorizada en X01. Candidato para la seguridad (P1) y el formato de errores (P2) |
| Configuración | `src/main/resources/application.yaml` | Sin nada de seguridad ni de formato de errores; `spring.jpa.open-in-view: false` |
| Dependencias | `pom.xml` | Ya están `spring-boot-starter-webmvc`, `-security`, `-security-oauth2-resource-server` y `-validation`; para tests, `-webmvc-test`, `-security-test` y Testcontainers. No hace falta ninguna nueva |
| Bases de test | `TestcontainersConfiguration.java`, `catalog/CatalogSamples.java`, `catalog/CatalogTables.java`, `catedra/FakeCatedraServer.java`, `FakeHttpServer.java` | PostgreSQL 18 de test; modelos y SQL directo para preparar la copia; cátedra simulada que registra los pedidos (CA-01, CA-06, CA-11) |

Hechos comprobados el 2026-11-20 para este PLAN:

- **Versiones:** Spring Boot 4.1.2, Spring MVC 7.0.10, Java 25.
- **Orden en Java:** `Collator` de Java 25 para español (`es`), con la fuerza primaria, ordena exactamente como D2: "Nunez", "Nuñez", "Nutrición", "Ñandú", "Odontología", "oftalmología", "Óptica"/"optica" (iguales: compara 0), "pediatría", "Psicología", "Traumatología". Las reglas del español están en el módulo `jdk.localedata` (`sun/text/resources/ext/CollationData_es`).
- **Orden en PostgreSQL 18** (contenedor descartable de la imagen de los tests, base en `en_US.utf8`): `ORDER BY name` pone "Ñandú" antes que "Nunez" y no desempata por `id`; la colación `es-x-icu` acierta con la ñ pero ordena "optica" antes que "Óptica" sin mirar el `id`; una colación propia de ICU de nivel 1 (`es-u-ks-level1`, no determinística) más `id` da el orden de D2, y hay que crearla en una migración. Asignada a la columna `name`, un `ORDER BY name, id` común ya devuelve el orden de D2 (probado en una copia de la tabla de `V1`).
- **Formato de errores en Spring:** Spring Boot 4.1.2 tiene `spring.mvc.problemdetails.enabled` (apagada por defecto), que registra su propio manejador. El manejador base de Spring MVC 7.0.10 (`ResponseEntityExceptionHandler`) cubre `ErrorResponseException` (y por eso `ResponseStatusException`), el `405` y el `404`, pero no un error cualquiera, como una falla de la base.
- **Seguridad por defecto:** se dedujo del código de Spring Boot 4.1.2 (SPEC, "Situación actual") y se comprueba en ejecución en la primera tarea, antes de cambiarla.

**Convenciones y slice de referencia:** la skill `hexagonal-vertical-slicing` y el slice `catalog`, con sus excepciones del `CLAUDE.md`. Turnos (`SecurityConfig`, `ApiExceptionHandler`, `SecurityProblemHandler`) es solo un antecedente: cada decisión se analiza para catálogo.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de la solicitud, el evento o la tarea desde que entra hasta la
respuesta, la persistencia o el mensaje publicado. Referencia el diagrama de
secuencia del proyecto si ya lo describe; usa un diagrama nuevo solo si aporta
claridad. -->

C04 sigue el molde de la skill dentro del slice `catalog` (P4), con dos piezas transversales en `catalogo.config`: la seguridad mínima (P1) y el manejador de errores (P2, decisión 005). El orden lo da la base (P3), y las dos lecturas van sin transacción (P5).

```
GET /api/professional-categories
└─ SecurityConfig (config): ruta pública, sin leer Authorization                     (P1; RF-02, RF-07)
   └─ ProfessionalCategoryController                                                infrastructure/web/controller
      └─ ProfessionalCategoryService                                                application/service
         └─ GetAllProfessionalCategoriesUseCase = ...UseCaseImpl                     application/usecases
            ├─ CatalogSyncStateRepository.get(): sin inicializar
            │  └─ CatalogNotInitializedException                                    (RF-03; P4.2, P4.3)
            └─ ProfessionalCategoryRepository.findAllOrderedByName()                (RF-01, RF-04, RF-06)
               └─ JPA: por name e id; la colación de V3 da el orden de D2            (P3)
      ├─ ProfessionalCategoryDtoMapper.toResponse → 200 con la lista
      └─ CatalogNotInitializedException → ResponseStatusException 503 con code CATALOG_NOT_INITIALIZED
└─ ApiExceptionHandler (config): problem+json; un error no previsto → log y 500 INTERNAL_ERROR   (P2; RF-05)
```

## Slices y componentes afectados

<!-- Aplica la skill hexagonal-vertical-slicing: un paquete por slice, con sus
capas domain, application e infrastructure. Identifica los slices afectados, sus
responsabilidades y la dirección de sus dependencias. Una funcionalidad puede
afectar más de un slice. Justifica cualquier slice nuevo o dependencia nueva
entre slices. -->

| Slice | Existe / nuevo | Responsabilidad y cambios | Dependencias con otros slices |
| --- | --- | --- | --- |
| `catalogo.catalog` | Existente | Suma la primera entrada web del servicio: controller, respuesta, mapper web, service, excepciones y el caso de uso de C04; amplía la persistencia de las categorías (P4) | Ninguna nueva: C04 no usa `catedra` ni `synchronization` |
| `catalogo.config` | Existente | Suma la configuración de seguridad mínima (P1) y, según P2, el formato de errores | No importa nada de los slices |
| `catalogo.catedra`, `catalogo.synchronization` | Existentes | Sin cambios | — |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son
propuestas. Señala impacto sobre modelos, contratos o componentes compartidos. -->

Rutas relativas a `src/main/java/catalogo/` salvo la migración. Todas son nuevas salvo las marcadas "Modificar". Los tests, en P6.

| Slice y capa | Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- | --- |
| config | `config/SecurityConfig.java` | Crear | Sin sesión; una lista de rutas públicas, hoy `GET /api/professional-categories`; el resto exige autenticación; CSRF como viene (P1) | RF-02, RF-07 |
| config | `config/ApiExceptionHandler.java` | Crear | Hereda de `ResponseEntityExceptionHandler`; `problem+json` para lo que lanzan los controllers y para los errores de Spring; un error no previsto va al log con su traza y responde `500` `INTERNAL_ERROR` (P2, decisión 005) | RF-03, RF-05 |
| catalog / infrastructure | `catalog/infrastructure/web/controller/ProfessionalCategoryController.java` | Crear | `@RequestMapping("/api/professional-categories")`, un `@GetMapping`; inyecta solo el service y el mapper web; atrapa `CatalogNotInitializedException` y relanza `ResponseStatusException` `503` con `code` `CATALOG_NOT_INITIALIZED` | RF-01 a RF-04 |
| catalog / infrastructure | `catalog/infrastructure/web/dto/ProfessionalCategoryResponse.java` | Crear | `id`, `name`, `description` (siempre presente, `null` si falta: D3) y `enabled`; clase Lombok, como la skill | RF-01 |
| catalog / infrastructure | `catalog/infrastructure/web/mapper/ProfessionalCategoryDtoMapper.java` | Crear | Solo `toResponse`, con el Builder: C04 no recibe datos | RF-01 |
| catalog / application | `catalog/application/service/ProfessionalCategoryService.java` | Crear | Un método, `getAllProfessionalCategories()`, que devuelve lo del caso de uso | RF-01 |
| catalog / domain | `catalog/domain/ports/in/GetAllProfessionalCategoriesUseCase.java` | Crear | Puerto de entrada de C04 | RF-01 |
| catalog / application | `catalog/application/usecases/GetAllProfessionalCategoriesUseCaseImpl.java` | Crear | Lee el estado por `CatalogSyncStateRepository`; sin inicializar, lanza `CatalogNotInitializedException`; si no, devuelve `findAllOrderedByName()`. Sin transacción (P5) | RF-01, RF-03, RF-04, RF-06 |
| catalog / application | `catalog/application/exception/CatalogException.java` y `CatalogNotInitializedException.java` | Crear | La base del slice, que vuelve (se quitó en C03), con su constructor `protected` con mensaje, y la subclase de C04 | RF-03 |
| catalog / domain | `catalog/domain/ports/out/ProfessionalCategoryRepository.java` | Modificar | Suma `findAllOrderedByName()` | RF-01, RF-04 |
| catalog / infrastructure | `catalog/infrastructure/persistence/repository/JpaProfessionalCategoryRepository.java` | Modificar | Suma `findAllByOrderByNameAscIdAsc()` | RF-01 |
| catalog / infrastructure | `catalog/infrastructure/persistence/adapter/JpaProfessionalCategoryRepositoryAdapter.java` | Modificar | Implementa `findAllOrderedByName()` con el mapper existente | RF-01 |
| recursos | `src/main/resources/db/migration/V3__spanish_order_for_category_names.sql` | Crear | Crea la colación `spanish_insensitive` (ICU, `es-u-ks-level1`, no determinística) y se la asigna a `professional_categories.name` (P3) | RF-01 |

Sin cambios: el modelo `ProfessionalCategory`, `CatalogSyncState`, la entidad JPA, los mappers de persistencia y todo `catedra` y `synchronization`. Los nombres de la migración y de la colación son propuestas; se pueden cambiar en TASKS sin cambiar el plan.

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Operaciones, eventos y DTO de entrada y salida:** contrato §2.1 y contrato común §2. La respuesta y el formato de errores, en P2 y P4.
- **Identificadores, relaciones y restricciones:** los `id` son los de la cátedra; sin cambios.
- **Origen de los datos devueltos o publicados y transformaciones:** la copia local, sin transformaciones: el orden lo da la consulta (P3).
- **Persistencia, consultas y migraciones:** una lectura del estado y una de todas las categorías, ordenada por `name` y por `id`. Una migración nueva, `V3`, crea la colación española y se la asigna a `professional_categories.name` (P3).
- **Compatibilidad con los datos existentes:** `V3` no cambia ningún dato, solo cómo se compara y ordena la columna. Flyway la aplica al arrancar, también sobre la base de desarrollo que ya tiene `V1` y `V2`.
- **Convivencia entre datos locales y remotos:** no aplica: C04 solo lee la copia local.

## Transacciones, concurrencia y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de BACKEND_GUIDELINES.md. -->

- **Estados y transiciones:** no aplica: C04 no cambia estados.
- **Transacciones y consistencia:** dos lecturas sin transacción (P5). La de las categorías es una sola consulta, que ve solo lo ya confirmado, y cada sincronización confirma su versión entera de una vez: la lista nunca es una mezcla (RF-06). Si una sincronización termina entre las dos lecturas, no cambia nada, porque una copia inicializada no vuelve atrás. Las lecturas no esperan a la sincronización (CA-10).
- **Idempotencia y mensajes duplicados:** no aplica: solo lee.
- **Concurrencia y tareas programadas:** C04 no toma el candado de la sincronización ni espera a una en curso (RF-06, P5). Los pedidos son independientes entre sí.
- **Timeouts, reintentos y errores de dependencias:** sin límite propio para la base (D4); la falla se traduce a `500` según P2.
- **Seguridad y autorización:** una lista de rutas públicas en la configuración de seguridad, hoy solo `GET /api/professional-categories`; todo lo demás exige autenticación, que todavía no tiene mecanismo (P1). El encabezado `Authorization` no se lee (RF-02).
- **Otras consideraciones de backend aplicables y su solución:** los errores sin detalles internos y la falla de la base en el log, según P2.

## Dependencias y configuración

<!-- Librerías, servicios externos, propiedades de configuración y secretos
afectados. Verifica compatibilidad con el proyecto y justifica las incorporaciones.
No agregues dependencias por defecto. Los valores sensibles y los parámetros
ajustables se externalizan. -->

- Sin dependencias nuevas.
- Sin variables ni propiedades nuevas: C04 no tiene valores ajustables (D4, D5), y el formato de errores lo da un manejador propio (P2).

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, de integración (base de datos, mensajería), de API o prueba manual.
No todos requieren todos los métodos. Identifica tests existentes y separa los
nuevos propuestos. Incluye regresiones relevantes. Una prueba con mocks no
demuestra persistencia, transacciones ni idempotencia. -->

Niveles de P6 (opción b), como turnos): un test del mapper web, un test del controller con `@WebMvcTest` (seguridad y manejador reales, service simulado, sin base), el test de adaptadores ampliado contra PostgreSQL, y tests de integración con la aplicación levantada en un puerto real (`*IntegrationTest`, PostgreSQL de Testcontainers). No hay E2E: C04 no usa la cátedra. Nombres de las clases nuevas como propuesta: `ProfessionalCategoryDtoMapperTest`, `ProfessionalCategoryControllerTest`, `ProfessionalCategoriesIntegrationTest`, `ProfessionalCategoriesWithoutCatedraIntegrationTest` y `ProfessionalCategoriesDatabaseDownIntegrationTest`.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Controller: el JSON tiene los cuatro campos y ningún otro. Integración: los valores salen de la copia, y la cátedra simulada no recibe pedidos después del login del arranque | Service simulado con categorías habilitadas y deshabilitadas; en integración, la copia cargada con SQL directo (`CatalogTables`) | `200`, cuerpo comparado campo por campo; lista de pedidos recibidos por la cátedra simulada sin cambios durante la consulta |
| CA-02 | `CatalogRepositoryAdaptersTest` ampliado: `findAllOrderedByName()` contra PostgreSQL con `V3`. Integración: un caso que confirma que la respuesta sale en ese orden | Nombres en minúscula y mayúscula, con y sin tildes, uno con ñ, habilitadas y deshabilitadas, y dos iguales salvo mayúsculas o tildes, insertados en otro orden | La lista en el orden de D2, desempate por `id` |
| CA-03 | Controller: una categoría sin descripción. `ProfessionalCategoryDtoMapperTest`: ida y vuelta con todos los campos (skill §10), y `description` en `null` | Ninguno | `"description": null` presente en el JSON; el mapper conserva todos los campos |
| CA-04 | Controller: el mismo pedido con parámetros de consulta inventados | Service simulado | Cuerpo y estado iguales a los del pedido sin parámetros |
| CA-05 | Controller, con la cadena de seguridad real: un pedido por variante del encabezado. Los tokens los arma `TestTokens` (`src/test/java/catalogo/`, nueva), con una clave RSA generada en cada corrida y la librería de JWT que ya trae el resource server | Bien firmado, vencido, alterado, un texto que no es un JWT y `Basic …` | Cada respuesta igual a la del pedido sin encabezado. `TestTokens` queda para la validación del JWT |
| CA-06 | Controller: el service lanza `CatalogNotInitializedException`. Integración: base sin versión local, con y sin categorías | En integración, `local_version` en `null` con SQL directo | `503`, `application/problem+json`, `code` `CATALOG_NOT_INITIALIZED`; sin pedidos a la cátedra; tablas iguales antes y después |
| CA-07 | Integración | Versión local con valor y la tabla de categorías vacía | `200` y `[]` |
| CA-08 | Controller: el service lanza una falla de acceso a datos de Spring. Integración: el test renombra `catalog_sync_state`, y en otro caso `professional_categories`, y las restaura al terminar | La base del test | `500`, `problem+json`, `code` `INTERNAL_ERROR`; el cuerpo no contiene SQL, nombres de tablas o clases ni trazas; un error en el log (salida capturada) |
| CA-09 | `ProfessionalCategoriesDatabaseDownIntegrationTest`: su propio contenedor de PostgreSQL, no el compartido, detenido antes del pedido; el tiempo de espera del pool se baja solo en ese test para no esperar 30 s | Contexto propio (`@DirtiesContext`) | `500` `INTERNAL_ERROR` sin detalles internos; error en el log |
| CA-10 | Integración: el test abre su propia conexión sin confirmación automática, cambia la copia (nombres y una categoría nueva) y no confirma; pide la lista; después confirma y la pide otra vez | La base del test; un tiempo máximo para la primera respuesta | Primero la copia anterior, sin esperar; después la nueva |
| CA-11 | `ProfessionalCategoriesWithoutCatedraIntegrationTest`: la aplicación sin la URL de la cátedra, que arranca sin integración | Copia inicializada con SQL directo | `200` con la lista |
| CA-12 | Integración, con el servidor real: `GET /api/professionals`, `GET /api/synchronization/status` y `POST /api/professional-categories`, sin autenticación | Ninguno | Rechazados, sin datos; se registra el estado observado (lo esperable, `403`) |

En todos, la práctica acordada: después de que el test pasa, una prueba en negativo (romper a propósito, ver que falla, restaurar y comprobar el hash).

**Comprobaciones de regresión:** la suite completa (`./mvnw clean verify`), sin cambios en los tests de X01, C01, C02 y C03.

**Comandos verificados para compilar y ejecutar tests:** los del `CLAUDE.md` del repositorio (`./mvnw test`, `./mvnw test -Dtest='Clase'`, `./mvnw clean verify`).

**Pruebas con infraestructura real o simulada:** PostgreSQL de Testcontainers (el compartido de `TestcontainersConfiguration`, y uno propio para CA-09) y la cátedra simulada, solo como testigo de que C04 no le pide nada. Sin Kafka ni Redis. No hace falta la cátedra real (SPEC, "Fuera de alcance").

**Prueba manual con la aplicación (última etapa, con el OK del usuario):** levantar la aplicación sobre la base de desarrollo con la integración apagada (`CATALOGO_CATEDRA_BASE_URL` vacía y `CATALOGO_VERIFICATION_ENABLED=false` en el entorno del comando, que le ganan al `.env`; el log del arranque tiene que mostrar el warning de "sin integración"), para no llamar a la cátedra real, y pedir la lista con `curl`. Comprueba que `V3` se aplica sobre una base con `V1`, `V2` y datos, y muestra la respuesta real: `200` con las categorías ya sincronizadas, o `503` si esa base no tiene versión local. Aplicar `V3` cambia el esquema de la base de desarrollo, como lo haría cualquier arranque del servicio. Después, la aplicación detenida y el compose como estaba.

**Limitaciones del entorno:**
- El caso "la base colgada al pedir una conexión" de D4 no se prueba: sigue como deducción, apoyada en lo que observó turnos (D5 de T09). CA-09 prueba la base detenida.
- `@WebMvcTest` no reproduce el reenvío a `/error` de un servidor real: por eso CA-06, CA-08 y CA-12 también se prueban con la aplicación levantada.
- Que la imagen de Docker del servicio use un PostgreSQL con ICU se revisa en la etapa de Docker.

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **El estado de hoy, en rojo.** Se escribe el primer escenario de `ProfessionalCategoriesIntegrationTest` (CA-01) y se corre antes de crear la seguridad y el controller: falla con lo que responde hoy la seguridad por defecto, y ese estado se registra (pendiente de la SPEC). Comprobación: el test falla por el estado, no por otra cosa. **Ajuste del 2026-11-20, aprobado por el usuario con las tareas:** esta etapa pasa al comienzo de la etapa 4 (TASK-04), para que la suite no quede en rojo durante las etapas 2 y 3 y el test no se commitee en rojo; como esas etapas no tocan la web ni la seguridad, lo observado es lo mismo.
2. **El orden en la base.** `V3`, `findAllOrderedByName()` en el puerto, el adaptador y el repositorio de Spring Data, y `CatalogRepositoryAdaptersTest` ampliado (CA-02). Comprobación: los tests de adaptadores, los nuevos y los que ya estaban.
3. **El caso de uso y sus piezas.** `CatalogException`, `CatalogNotInitializedException`, el puerto de entrada, su implementación, el service, `ProfessionalCategoryResponse`, `ProfessionalCategoryDtoMapper` y su test. Comprobación: el test del mapper y la suite compila.
4. **La entrada web.** `SecurityConfig`, `ApiExceptionHandler`, el controller, `TestTokens` y `ProfessionalCategoryControllerTest` (CA-01, CA-03 a CA-06 y CA-08). Comprobación: el test del controller, y el escenario de la etapa 1, ahora en verde.
5. **La integración completa.** El resto de `ProfessionalCategoriesIntegrationTest` (CA-01, CA-02, CA-06 a CA-08, CA-10 y CA-12), `ProfessionalCategoriesWithoutCatedraIntegrationTest` (CA-11) y `ProfessionalCategoriesDatabaseDownIntegrationTest` (CA-09). Comprobación: esas clases, con su prueba en negativo.
6. **Regresión y prueba manual.** `./mvnw clean verify` completo, con el conteo de tests en los reportes XML; después, con el OK del usuario, la prueba manual con la aplicación.
7. **Cierre.** El `CLAUDE.md`: el slice `catalog` con C04, las excepciones de C04, cómo se exponen las rutas públicas, cómo responde un error, la colación de `name`, las decisiones 005 y 006 en la lista, la carpeta de la SDD y las clases de test nuevas. `TASKS.md` con lo observado.

Las etapas 2 y 3 no dependen de la seguridad; la 4 necesita la 3, y la 5 necesita la 2 y la 4.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - **La página de error interna de Spring y la seguridad.** Un error que Spring no resuelve dentro del controller se reenvía a su página de error interna (`/error`), y la seguridad también revisa ese reenvío: con todo lo que no es C04 cerrado, un `503` o un `500` podría llegar como otro estado. Medida: el manejador de P2 responde antes de ese reenvío. Se comprueba con CA-06 y CA-08.
  - **El filtro del JWT en las rutas públicas.** Anotado en la SPEC para la validación del JWT; CA-05 lo detecta.
  - **El orden depende de ICU en PostgreSQL** (P3). Una base sin ICU no puede crear la colación, y `V3` falla al arrancar, de forma visible. La imagen `postgres:18` lo trae; si la etapa de Docker cambiara la imagen, se revisa ahí.
  - **La colación cambia cómo compara la columna `name`.** Con una colación no determinística, "optica" y "Óptica" son iguales al comparar. Hoy nadie compara categorías por nombre; una consulta futura que lo haga tiene que saberlo. Queda anotado en el `CLAUDE.md` al cerrar.
- **Decisiones pendientes:**
  - Ninguna.
- **Decisiones tomadas:**
  - **Aprobación:** el usuario aprobó el PLAN el 2026-11-20, con P1 a P7 y la lista de excepciones de P7. Indicó además que todos los comandos de git los ejecuta él: al terminar cada tarea se le da un mensaje de commit recomendado y la lista de archivos a agregar, y la explicación de cada archivo, separada por tarea.
  - **P1 · La seguridad mínima (2026-11-20): opción a).** Una configuración de seguridad en `catalogo.config`, sin sesión, con una sola lista de rutas públicas: hoy solo `GET /api/professional-categories`, con acceso libre. Todo lo demás exige autenticación, que todavía no tiene mecanismo, así que se rechaza; lo esperable es un `403` sin cuerpo, y se comprueba en las tareas (CA-12). CSRF queda como viene: solo controla los métodos que cambian datos, y todos los endpoints del contrato de catálogo son `GET` (contrato §1). Esa lista es el lugar donde se decide qué es público y qué no (pregunta del usuario al decidir): C05 suma su ruta, y la validación del JWT agrega el mecanismo y las reglas de C06 y C07. Alternativas descartadas: b), desactivar CSRF como turnos (decisión 006), un aviso de SonarQube que justificar por algo sin efecto en un servicio que solo tiene `GET`; c), abrir la ruta con todos sus métodos, que abre más de lo que define el contrato solo para que un pedido que nadie hace reciba `405` en lugar de `403`.
  - **P2 · El formato de los errores (2026-11-20): opción a), un manejador global en `catalogo.config`.** Qué hace y por qué existe, con su recorrido y las alternativas: `docs/decisiones/005-manejador-global-de-errores.md` (pedido por el usuario). En resumen: da formato `problem+json` (contrato común §2) a los errores de Spring, como el `404` y el `405`, y a los que lanza el controller con su `code`. Atrapa todo error no previsto, como una falla de la base: responde `500` `INTERNAL_ERROR` con un texto fijo y deja el error en el log con su detalle completo (RF-05). Es el único lugar donde se escribe ese error: el detalle va al log y nunca a la respuesta. No cambia el formato de las líneas del log, que sigue siendo el de Spring Boot, como en turnos (pregunta del usuario al decidir). El controller sigue traduciendo las excepciones del slice a su estado y su `code`, como pide la skill. Los rechazos de la seguridad ocurren antes y no pasan por este manejador: anotados en la SPEC para la validación del JWT. Es una excepción a la skill (un `@RestControllerAdvice`), autorizada por el usuario al elegir esta opción; va en la lista de P7. Alternativas descartadas: b), la propiedad de Spring Boot con el controller atrapando lo inesperado, que repetía esa captura en cada controller, dejaba sin formato lo que falla fuera de uno y no da el `code` ni los `fieldErrors` que necesitará C05; c), un manejador solo para los controllers de `catalog`, la misma excepción con menos cobertura.
  - **P3 · Dónde se ordena (2026-11-20): opción a), en la base, con la colación en la columna.** El usuario prefirió que la base devuelva las categorías ya ordenadas, sin código de orden propio. Como el orden por defecto de la base no es el de D2 ("Contexto técnico verificado"), una migración nueva, `V3`, crea una colación de ICU para español de nivel 1 (`es-u-ks-level1`, no determinística: no distingue mayúsculas ni tildes, y deja la ñ como letra propia después de la n) y se la asigna a `professional_categories.name`. La lectura es una consulta común de Spring Data, "todas, ordenadas por `name` y por `id`", y el resultado ya es el de D2; se probó en una copia de la tabla de `V1`. La SPEC no cambia: D2 dice qué orden, P3 dónde se calcula. Efectos aceptados: la regla vive en el esquema y la protege CA-02; la columna compara "optica" y "Óptica" como iguales, pero nadie compara categorías por nombre (la sincronización va por `id`; buscado en el código y en los tests); y necesita ICU en PostgreSQL, que la imagen `postgres:18` de los tests y del compose trae. Si C05 elige la misma regla para los profesionales, que se ordenan y paginan en la base, le sirve la misma técnica. Alternativas descartadas: b), la misma colación escrita en la consulta, que pedía la misma migración más una consulta escrita a mano para conservar en la columna una comparación que nadie usa; c), un comparador en Java con el `Collator` español, que funcionaba igual (comprobado) pero ponía en el código lo que la base hace con una migración, y dependía de `jdk.localedata` en el JRE; d), volver a D2 y aceptar el orden por defecto de la base, que deja la ñ como una n, no desempata por `id`, depende de la configuración de la base y cambiaba la SPEC aprobada.
  - **P4 · Las piezas del slice `catalog` (2026-11-20).**
    - **P4.1 · Piezas y nombres: el molde de la skill**, aprobado como se propuso (tabla de "Slices y componentes afectados"). Un service por entidad, `ProfessionalCategoryService`: C05 y C07 tendrán el suyo. Sin request ni `toDomain`, porque C04 no recibe datos (skill §8, partes que no se necesitan). La ruta `/api/professional-categories` la fija el contrato §1, en lugar del `/{entities}` de la skill: va en P7.
    - **P4.2 · "Sin inicializar": opción a), una excepción del slice.** `CatalogNotInitializedException extends CatalogException`, en `catalog/application/exception` (skill §2.9 y §5.2). La lanza el caso de uso y la traduce el controller (skill §5.3). Los resultados sellados de C01 a C03 se usan cuando el resultado lo lee otro slice o cuando el que llama hace algo distinto en cada caso; aquí lo lee solo el controller del mismo slice, para convertirlo en un estado HTTP, que es el caso exacto de la skill. Alternativa descartada: b), un resultado sellado ("lista" o "sin inicializar"), otra excepción a la skill sin nada que ganar. El criterio general, cuándo se usa cada uno y dónde se usa hoy: `docs/decisiones/006-resultados-sellados-o-excepciones.md` (pedido por el usuario para poder justificarlo).
    - **P4.3 · Cómo se lee el estado: opción a), por el puerto de salida `CatalogSyncStateRepository`.** La skill (§5.2) dice que un caso de uso depende de puertos de salida; el usuario prefirió la opción que sigue mejor a la skill. Alternativa descartada: b), por el puerto de entrada `GetCatalogSyncStateUseCase`, que haría que un caso de uso llame a otro; ese puerto existe para que `synchronization` entre al slice desde afuera.
  - **P5 · Lectura y consistencia (2026-11-20): opción a), dos lecturas sin transacción.** El porqué, en "Transacciones, concurrencia y errores". Alternativas descartadas: b), una transacción de solo lectura, que muestra exactamente lo mismo, porque cada consulta sigue viendo su propio momento; c), las dos lecturas en una misma foto de la base (aislamiento más estricto), que protege de un caso imposible, porque el estado no vuelve atrás, y suma una configuración que hay que explicar.
  - **P6 · Los tests (2026-11-20): opción b), como turnos.** Un test del controller con `@WebMvcTest`, con la seguridad y el manejador reales y el service simulado, más tests de integración con la aplicación levantada y la base de test, el test de adaptadores ampliado y el del mapper web (estrategia en "Estrategia de validación"). El usuario preguntó primero si la opción a) no era el E2E: no, porque el E2E corre contra la cátedra real y lo lanza el usuario, mientras que estos tests de integración corren en la suite normal con la cátedra simulada. C04 no tiene E2E porque no usa la cátedra. También preguntó para qué sirve la cátedra simulada: es un servidor HTTP del test que responde lo que respondería la cátedra, para que el cliente HTTP real se pruebe; en C04 solo hace de testigo de que no se le pide nada. Al presentar las opciones se dijo que con b) no se probaba que la seguridad y el manejador trabajaran juntos; era inexacto y se corrigió: `@WebMvcTest` usa los dos reales, y lo que no reproduce (el reenvío a `/error` de un servidor real y la base) lo cubren los tests de integración. Con eso, b) quedó recomendada: gana un test rápido del contrato HTTP y la misma estructura que turnos, a cambio de repetir algunos escenarios. Alternativas descartadas: a), solo tests de integración, con menos tests pero sin el test rápido del controller; c), un repositorio que falla a pedido en lugar de provocar la falla en la base real, una clase más, y la guía de backend no acepta un doble para demostrar lo que hace la base.
  - **P7 · Excepciones a la skill (2026-11-20), autorizadas por el usuario para C04:**
    1. Un `@RestControllerAdvice` global, `ApiExceptionHandler` (P2, decisión 005).
    2. La ruta `/api/professional-categories` en lugar del `/{entities}` de la skill, porque la fija el contrato §1 (P4.1). Al autorizarla, el usuario preguntó si una ruta lleva guiones: sí, es la forma habitual de separar palabras en una URL, y es la que fija el contrato, como en turnos (`/api/reservation-processes`, `/api/free-slots`).

    No son excepciones nuevas: `SecurityConfig` y `ApiExceptionHandler` viven en `catalogo.config`, dentro de la excepción de X01 para ese paquete; `CatalogException` vuelve como la pide la skill (§2.9); el mapper web sin `toDomain` lo permite la skill (§8, solo las partes que se necesitan); la migración con la colación es de la base, de la que la skill no habla. Como pide el usuario, el código no lleva comentarios que marquen excepciones: se listan en el `CLAUDE.md` al cerrar.
