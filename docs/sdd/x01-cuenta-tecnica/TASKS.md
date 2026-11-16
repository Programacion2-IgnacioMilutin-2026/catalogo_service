# TASKS: X01 — Autenticar cuenta técnica y obtener configuración

**SPEC:** `docs/sdd/x01-cuenta-tecnica/SPEC.md` (aprobada el 2026-11-15)
**PLAN:** `docs/sdd/x01-cuenta-tecnica/PLAN.md` (aprobado el 2026-11-15)

Las tareas siguen el "Orden de implementación" del PLAN, con un cambio de orden: el cliente de la cátedra (TASK-03) va antes que el login inicial (TASK-04). El caso de uso del login inicial necesita un cliente para que la aplicación arranque; con el orden del PLAN, la suite quedaría en rojo entre una tarea y la otra.

Rutas de código relativas a `src/main/java/catalogo/`; rutas de tests, a `src/test/java/catalogo/`. Los tests siguen la estructura de los de turnos, adaptada a este servicio (pedido del usuario al aprobar el PLAN).

CA-16 (convivencia de dos JWT de la misma cuenta) ya se comprobó contra la cátedra real el 2026-11-15, antes de aprobar la SPEC; la evidencia está en la SPEC, D5. No tiene tarea.

- [x] **TASK-01 · Base para arrancar el servicio**
  - Alcance: suma `.env` a `.gitignore`; en `src/main/resources/application.yaml`, la lectura opcional del `.env`, la conexión a la base con las variables `CATALOGO_DB_*`, Hibernate en modo de solo validar y el puerto del servicio (`CATALOGO_PORT`); crea `compose.yaml` con el servicio `catalog-db` y `.env.example` con las variables de la base y el puerto; en `pom.xml`, los tests apuntan la ruta del `.env` a un archivo inexistente; deja `TestcontainersConfiguration` solo con PostgreSQL 18. No toca la seguridad ni crea ningún slice. Si Redis o Kafka intentan conectarse al arrancar, apaga lo necesario y lo anota acá.
  - Depende de: ninguna
  - Resuelve: base de CA-05 y CA-09; restricción de la SPEC (los tests no usan el `.env` local)
  - Validación:
    - Con una base descartable (otro volumen y otro puerto, definidos en un archivo fuera del repositorio) y las variables pasadas por la línea de comandos, `./mvnw spring-boot:run` arranca, escucha en el puerto configurado y el log no muestra intentos de conexión a Redis ni a Kafka. Al terminar se detiene la aplicación y se borra solo el volumen de prueba.
    - Sin la contraseña de la base, el servicio no arranca. (Corregido el 2026-11-15: decía además "y el error nombra la variable", que no es lo que hace Spring; ver el resultado.)
    - `./mvnw test` en verde con un `.env` presente que tiene un valor inválido a propósito (solo si no existe ya un `.env` del usuario; no se pisa ni se lee el suyo), levantando solo el contenedor de PostgreSQL.
    - `git status` no ofrece versionar el `.env`.
  - Resultado: 2026-11-15. Archivos: `.gitignore`, `application.yaml`, `pom.xml` y `TestcontainersConfiguration` modificados; `compose.yaml` y `.env.example` creados.
    - Base descartable con el `compose.yaml` nuevo (proyecto `catalogo-x01-prueba`, volumen propio y puerto 55433 definidos en un archivo temporal): `docker compose up -d --wait catalog-db` la dejó `healthy`. Sin `CATALOGO_DB_PASSWORD`, el compose se niega con el mensaje `Definí CATALOGO_DB_PASSWORD en el archivo .env`.
    - `./mvnw -B -q spring-boot:run` con `CATALOGO_DB_PASSWORD`, `CATALOGO_DB_PORT=55433` y `CATALOGO_PORT=18081` por la línea de comandos: `Started CatalogoApplication in 2.515 seconds`, `Tomcat started on port 18081`, conectado a `jdbc:postgresql://localhost:55433/catalogo` (PostgreSQL 18.6). Flyway avisa `No migrations found` y deja solo su tabla `flyway_schema_history`: ninguna tabla de negocio.
    - Redis y Kafka: el log solo muestra `Bootstrapping Spring Data Redis repositories` con 0 repositorios; ninguna línea de conexión y ninguna conexión abierta a los puertos 6379 o 9092. No hizo falta apagar nada.
    - Seguridad: `GET /` respondió `401` y el log no mostró ninguna contraseña generada (el PLAN preveía que apareciera; no apareció).
    - Sin `CATALOGO_DB_PASSWORD`: el servicio no arranca (`exit=1`), con `FATAL: password authentication failed for user "catalogo"`. El error no nombra la variable: Spring deja el texto sin resolver como contraseña y quien rechaza es PostgreSQL. Queda así, como en turnos; el compose sí la nombra.
    - `./mvnw -B test` con un `.env` temporal que contenía `CATALOGO_PORT=no-es-un-numero`: `BUILD SUCCESS`, 1 test, y solo se crearon los contenedores `postgres:18` y el auxiliar de Testcontainers. Prueba en negativo: la misma corrida con `-Dtests.env-file=.env` falló con `Failed to bind properties under 'server.port'`, lo que demuestra que ese `.env` rompe el arranque si se lee. El `.env` temporal se borró.
    - `git check-ignore` confirma que `.env` está ignorado por `.gitignore`.
    - Limpieza: aplicación detenida, `docker compose down` sin `-v` y `docker volume rm catalogo-x01-prueba-db-data`; no quedan contenedores, volúmenes ni puertos de la prueba.

- [x] **TASK-02 · Dominio del slice `catedra`**
  - Alcance: crea en `catedra/domain/model/` los `record` `TechnicalIntegration`, `TechnicalSession` y `TechnicalLoginPolicy`, los enum `ProvisioningStatus` e `IntegrationState`, y la interfaz sellada `TechnicalLoginResult` con `Success`, `ConfigurationError` y `Unavailable`; en `catedra/domain/ports/`, los tres puertos de entrada (`Start`, `Get` y `Renew`, que recibe la sesión rechazada) y los dos de salida (`TechnicalLoginClient` y `TechnicalSessionRepository`). Sin implementaciones.
  - Depende de: ninguna
  - Resuelve: base de CA-02, CA-04, CA-07, CA-10, CA-11, CA-13 y CA-15
  - Validación: `./mvnw test -Dtest='TechnicalSessionTest,TechnicalLoginPolicyTest,TechnicalIntegrationTest'` (en `catedra/domain/model/`):
    - Una sesión con error de configuración o conectando nunca permite otro login; habilitada o degradada, solo si pasó el intervalo mínimo. Solo `ENABLED` está habilitada.
    - Las esperas son 1, 2, 4 y 8 s y nunca superan 30 s; el primer intento no espera.
    - La integración dice si está provista y nombra cada dato de Redis o de Kafka que le falta; sin `groupId` no le falta nada.
    - El `toString` de la sesión y del resultado no muestra el JWT, y el de la integración no muestra la `redisPassword`.
    - Chequeo 1 de la skill (el dominio no importa Spring, JPA ni otras capas).
  - Resultado: 2026-11-15. Creados los seis modelos y los cinco puertos. Además, el ayudante de test `catedra/TechnicalIntegrations` (integraciones de ejemplo, completas o sin un dato), que no estaba en el alcance y usan también los tests de las tareas siguientes.
    - `./mvnw -B test -Dtest='TechnicalSessionTest,TechnicalLoginPolicyTest,TechnicalIntegrationTest'`: `BUILD SUCCESS`, 28 tests en verde.
    - `TechnicalSessionTest` (8): con error de configuración o conectando nunca permite otro login, aunque pasen 30 días; degradada o habilitada, no a los 59 s y sí a los 60 s; solo `ENABLED` está habilitada; una sesión no habilitada no conserva JWT ni integración; el `toString` de la sesión y del login exitoso no muestra el JWT ni la `redisPassword`.
    - `TechnicalLoginPolicyTest` (3): el primer intento no espera; esperas de 1, 2, 4 y 8 s; nunca más de 30 s.
    - `TechnicalIntegrationTest` (17): completa, no le falta nada; nombra cada uno de los siete datos cuando falta, también si viene en blanco, y todos juntos si faltan todos; sin `groupId` no le falta nada; solo `PROVISIONED` está provista; el `toString` no muestra la `redisPassword`.
    - Chequeo 1 de la skill: sin resultados.
    - Prueba en negativo (junto con TASK-03): con el `toString` de la integración mostrando la `redisPassword`, fallaron `theRedisPasswordIsNotShownWhenTheIntegrationIsPrinted` y los dos tests del `toString` de `TechnicalSessionTest`. Archivo restaurado y hash comprobado.

- [x] **TASK-03 · Cliente HTTP de la cátedra**
  - Alcance: crea en `catedra/infrastructure/client/` `CatedraLoginClientAdapter`, `CatedraProperties`, los DTO del login y de la integración (`dto/`) y `mapper/TechnicalIntegrationMapper`; suma la sección `catalogo.catedra` a `application.yaml` y las tres variables de la cuenta técnica a `.env.example`. Traduce `200` a `Success`, `4xx` a `ConfigurationError` y `5xx`, timeout, conexión rechazada o respuesta ilegible a `Unavailable`; con configuración faltante o URL inválida, warning con las propiedades y ningún request. Para los tests, crea `FakeHttpServer` (servidor HTTP del JDK, en `src/test/java/catalogo/`), `catedra/FakeCatedraServer` y la respuesta de ejemplo `src/test/resources/catedra/login-200.json`, armada desde el anexo §5.1 con valores ficticios.
  - Depende de: TASK-02
  - Resuelve: CA-01, CA-03, CA-04, CA-07, CA-08 y CA-15 (adaptador)
  - Validación: `./mvnw test -Dtest='CatedraLoginClientAdapterTest,TechnicalIntegrationMapperTest,FakeHttpServerTest'`:
    - El request a `/api/authenticate` lleva las credenciales de test y `rememberMe: true`.
    - La respuesta del anexo se traduce a `Success` con el `groupId`, los cuatro datos de Redis y los tres de Kafka; los demás campos y los desconocidos se descartan.
    - `401`, `403` y `404` con `STUDENT_INTEGRATION_NOT_FOUND` → `ConfigurationError`.
    - `503`, `500`, una demora mayor al timeout de lectura, un puerto cerrado y un cuerpo ilegible → `Unavailable`.
    - Una respuesta `200` sin `id_token` o sin `integration` → `Unavailable`; con `integration` pero sin un dato de Redis, `Success` con ese dato faltante.
    - Sin cada una de las tres propiedades, y con URLs inválidas (entre comillas, sin protocolo, con otro protocolo): warning que nombra la propiedad y ningún request.
    - Con captura de la salida: no contiene el JWT, la `password` ni la `redisPassword` de test.
    - El mapper conserva cada dato (ida completa, con todos los campos cargados) y devuelve nulo con entrada nula.
  - Resultado: 2026-11-15. Creados el adaptador, `CatedraProperties`, los tres DTO y el mapper; sumadas la sección `catalogo.catedra` a `application.yaml` y las tres variables a `.env.example`. Dos diferencias con el alcance escrito: `CatalogoApplication` suma `@ConfigurationPropertiesScan`, necesaria para que Spring cargue `CatedraProperties`; y `CatedraProperties` todavía no tiene los tiempos de reintento del login, que entran en TASK-04 con quien los usa. El adaptador además quita una barra final de la URL base.
    - `./mvnw -B test -Dtest='CatedraLoginClientAdapterTest,TechnicalIntegrationMapperTest,FakeHttpServerTest'`: `BUILD SUCCESS`, 52 tests en verde.
    - `CatedraLoginClientAdapterTest` (37), contra la cátedra simulada (`FakeCatedraServer` sobre `FakeHttpServer`): `POST /api/authenticate` con `test-login`, `test-password` y `rememberMe: true`; la respuesta de ejemplo → `Success` con el JWT, el `groupId`, los cuatro datos de Redis y los tres de Kafka; campos desconocidos ignorados; `PENDING`, `FAILED`, `REVOKED` y un estado no previsto (→ `UNKNOWN`) siguen siendo `Success`; `401` sin cuerpo, `403` y `404` con `STUDENT_INTEGRATION_NOT_FOUND` → `ConfigurationError` con el estado y el código, un solo request; `500` y `503` → `Unavailable`; respuesta más lenta que el timeout de lectura, puerto cerrado, cuerpo ilegible y cuerpo vacío → `Unavailable`; `200` sin `id_token` o sin `integration` → `Unavailable` que lo nombra; `200` sin cada uno de los siete datos → `Success` cuya integración nombra el dato; sin `base-url`, `username` o `password` → warning que nombra la propiedad, `ConfigurationError` y ningún request; seis URLs inválidas (entre comillas dobles y simples, sin protocolo, con `ftp`, sin host, texto suelto) → warning y `ConfigurationError`; URL con barra final → llega a `/api/authenticate`; un error inesperado → `Unavailable`; la salida no contiene el JWT, la `password` ni la `redisPassword` de test.
    - `TechnicalIntegrationMapperTest` (10): cada dato llega al dominio, con todos los campos cargados; nulo con entrada nula; un estado desconocido, en minúsculas o ausente queda `UNKNOWN`.
    - `FakeHttpServerTest` (5): respuestas en orden y la última repetida; recuerda método, ruta, encabezados y cuerpo; `404` sin respuesta configurada; respuesta vacía sin cuerpo ni `Content-Type`; respeta la demora.
    - Prueba en negativo, en una sola corrida con tres roturas: con `rememberMe(false)` falló `sendsTheConfiguredCredentialsWithRememberMe`; con el mapper sin pasar la `redisPassword` fallaron `everyValueOfTheResponseReachesTheDomain` y los tests de `Success` del adaptador; la tercera rotura es la de TASK-02. 17 fallos de 72. Los tres archivos se restauraron y su hash coincide con el anterior.
    - Regresión: `./mvnw -B clean test`: `BUILD SUCCESS`, 81 tests (contados en los reportes XML), 0 fallos. `CatalogoApplicationTests` arranca con los beans nuevos y muestra el warning de configuración faltante, que es lo esperado sin datos de la cátedra. Los cinco chequeos de la skill, sin resultados.

- [x] **TASK-04 · Login inicial con reintentos**
  - Alcance: crea `catedra/application/usecases/StartTechnicalSessionUseCaseImpl`, `catedra/application/service/TechnicalSessionService` (con el método de arranque), `catedra/infrastructure/persistence/adapter/InMemoryTechnicalSessionRepositoryAdapter`, `catedra/infrastructure/client/CatedraClientConfiguration` (arma `TechnicalLoginPolicy` desde las propiedades) y `config/ClockConfig`. Para los tests, `ScriptedLoginClient` en `catedra/application/usecases/`.
  - Depende de: TASK-02, TASK-03
  - Resuelve: CA-02, CA-03, CA-04, CA-14 y CA-15 (lógica)
  - Validación: `./mvnw test -Dtest='StartTechnicalSessionUseCaseImplTest,InMemoryTechnicalSessionRepositoryAdapterTest'`, con un cliente falso que devuelve una secuencia de resultados y esperas de milisegundos:
    - `Success` con `PROVISIONED` y datos completos → `ENABLED` con el JWT y la integración, guardada en el repositorio.
    - Cada estado distinto de `PROVISIONED` → tantos intentos como el máximo y `DEGRADED`.
    - `Success` con `PROVISIONED` sin cada uno de los siete datos → tantos intentos como el máximo, `DEGRADED` y el dato nombrado en el log; si un intento posterior llega completo, `ENABLED`.
    - `ConfigurationError` → un solo intento y `CONFIGURATION_ERROR`.
    - `Unavailable` dos veces y después `Success` → tres intentos y `ENABLED`.
    - Un cliente que lanza una excepción → intento fallido; el estado final nunca es `CONNECTING`.
    - Suite completa en verde (el contexto arranca con los beans nuevos). Chequeos 1 y 2 de la skill.
  - Resultado: 2026-11-16. Creados `StartTechnicalSessionUseCaseImpl`, `TechnicalSessionService`, `InMemoryTechnicalSessionRepositoryAdapter`, `CatedraClientConfiguration` y `config/ClockConfig`. Modificados: `CatedraProperties` suma la sección `login` con los valores por defecto de P4 (y su test del adaptador se ajustó al constructor nuevo); `TechnicalLoginResult.Success` exige que la integración no sea nula, para que una respuesta sin integración nunca llegue a un caso de uso (no estaba en el alcance; protege RF-13).
    - `./mvnw -B test -Dtest='StartTechnicalSessionUseCaseImplTest,InMemoryTechnicalSessionRepositoryAdapterTest'`: `BUILD SUCCESS`, 24 tests en verde.
    - `StartTechnicalSessionUseCaseImplTest` (22), con `ScriptedLoginClient`, esperas de 1 a 5 ms y un reloj fijo: `PROVISIONED` y completa → `ENABLED` con el JWT, la integración y el instante, guardada en el repositorio, un solo intento; `PENDING`, `FAILED`, `REVOKED` y `UNKNOWN` → 5 intentos y `DEGRADED`; `PROVISIONED` sin cada uno de los siete datos → 5 intentos, `DEGRADED` y el dato en el log; incompleta y después completa → `ENABLED` al segundo intento; sin `groupId` → `ENABLED`; `ConfigurationError` → un solo intento, `CONFIGURATION_ERROR` y el motivo en el log; `Unavailable` dos veces y después éxito → 3 intentos y `ENABLED`, con cada intento en el log; siempre `Unavailable` → 5 intentos y `DEGRADED`; una excepción del cliente cuenta como intento fallido, y si falla siempre el estado final es `DEGRADED`, no `CONNECTING`; durante el login la sesión está en `CONNECTING`; una espera interrumpida corta los reintentos y deja `DEGRADED`; el log del éxito muestra el `groupId` y `PROVISIONED`, sin el JWT ni la `redisPassword`.
    - `InMemoryTechnicalSessionRepositoryAdapterTest` (2): arranca en `CONNECTING`; una sesión guardada reemplaza a la anterior.
    - Prueba en negativo (junto con TASK-05): sin la regla de la respuesta incompleta fallaron los 7 casos de datos faltantes y el de "incompleta y después completa". Archivo restaurado y hash comprobado.
    - Regresión y chequeos: ver TASK-05.

- [x] **TASK-05 · Consultar y renovar la sesión a pedido**
  - Alcance: crea `GetTechnicalSessionUseCaseImpl` y `RenewTechnicalSessionUseCaseImpl` (candado, intervalo mínimo y sesión vigente) y suma sus métodos al service. Para los tests, `MutableClock` en `src/test/java/catalogo/`.
  - Depende de: TASK-04
  - Resuelve: CA-06, CA-10, CA-11, CA-12, CA-13, CA-14 y CA-15 (renovación)
  - Validación: `./mvnw test -Dtest='GetTechnicalSessionUseCaseImplTest,RenewTechnicalSessionUseCaseImplTest'`, con un reloj controlado y un cliente falso que cuenta llamadas:
    - Habilitada: pedir la sesión no hace logins; en los otros tres estados, devuelve vacío.
    - 20 hilos piden renovar a la vez, con el cliente demorando hasta que todos pidieron: un solo login, y todos reciben la sesión nueva.
    - Degradada: ningún login antes de 60 s, uno después.
    - Error de configuración: ningún login.
    - Sesión rechazada que ya no es la vigente: devuelve la vigente sin login.
    - Habilitada y renovada hace menos del intervalo: ningún login.
    - Renovación con una respuesta `PROVISIONED` incompleta: la sesión queda degradada y el log nombra el dato.
    - Un cliente que lanza una excepción: la sesión queda degradada, no trabada.
    - Prueba en negativo de la concurrencia: sin el candado, el test de los 20 hilos falla; se restaura el archivo y se comprueba su hash.
  - Resultado: 2026-11-16. Creados `GetTechnicalSessionUseCaseImpl`, `RenewTechnicalSessionUseCaseImpl` y, para los tests, `MutableClock`; el service suma sus dos métodos.
    - `./mvnw -B test -Dtest='GetTechnicalSessionUseCaseImplTest,RenewTechnicalSessionUseCaseImplTest'`: `BUILD SUCCESS`, 26 tests en verde.
    - `GetTechnicalSessionUseCaseImplTest` (3): devuelve la sesión habilitada tal cual; lee la vigente en cada llamada; vacío en `CONNECTING`, `DEGRADED` y `CONFIGURATION_ERROR`.
    - `RenewTechnicalSessionUseCaseImplTest` (23), con `MutableClock` y `ScriptedLoginClient`: degradada, ningún login a los 59 s y uno a los 60 s, que la deja `ENABLED`; con error de configuración, ningún login en 5 pedidos después de 2 días; en `CONNECTING`, ningún login; habilitada y sin sesión rechazada, devuelve la vigente sin login; la vigente rechazada se renueva pasado el intervalo; una sesión rechazada que ya no es la vigente recibe la vigente sin login; renovada hace 59 s y rechazada de nuevo, ningún login, respuesta vacía y la sesión queda como estaba; 20 pedidos simultáneos con la sesión rechazada, con el cliente demorado hasta que todos pidieron: un solo login y los 20 reciben la sesión nueva; renovación con `PENDING`, `FAILED`, `REVOKED` o `UNKNOWN` → `DEGRADED`; renovación `PROVISIONED` sin cada uno de los siete datos → `DEGRADED` y el dato en el log; renovación rechazada por la cátedra → `CONFIGURATION_ERROR` y ningún login más; cátedra no disponible → `DEGRADED`, ningún login a los 30 s y uno a los 60 s; una excepción del cliente → `DEGRADED`; el log de la renovación muestra el `groupId` y `PROVISIONED`, sin el JWT ni la `redisPassword`.
    - Prueba en negativo, en una corrida con dos roturas: sin el candado, `manySimultaneousRenewalsProduceASingleLoginAndEveryoneGetsTheNewSession` falló con 20 logins en lugar de 1; sin la regla de la respuesta incompleta fallaron los 7 casos de la renovación (y los de TASK-04). 16 fallos de 45. Los dos archivos se restauraron y su hash coincide con el anterior.
    - Regresión: `./mvnw -B clean test`: `BUILD SUCCESS`, 131 tests (contados en los reportes XML), 0 fallos. Los cinco chequeos de la skill, sin resultados.
    - Nota sobre RF-11: cuando la sesión rechazada es la vigente y todavía no pasó el intervalo mínimo, la respuesta es vacía ("no hay una sesión distinta para darte"), no la misma sesión rechazada. Es lo que hace turnos; se le informó al usuario.

- [x] **TASK-06 · Arranque e integración**
  - Alcance: crea `catedra/infrastructure/startup/TechnicalSessionStartup` (escucha `ApplicationReadyEvent` y corre el login inicial en un hilo virtual). Sin otros cambios de código.
  - Depende de: TASK-03, TASK-04, TASK-05
  - Resuelve: CA-01, CA-05, CA-07 y CA-08 (aplicación completa)
  - Validación: `./mvnw test -Dtest='TechnicalSessionIntegrationTest,TechnicalSessionCatedraDownIntegrationTest,TechnicalSessionWithoutConfigurationIntegrationTest'` (en `catedra/`), con la aplicación completa, PostgreSQL de Testcontainers y la cátedra simulada:
    - Al arrancar, la sesión queda `ENABLED` con los datos de la respuesta, con un solo request.
    - Con la cátedra caída y con una cátedra que responde `401`, la aplicación termina de arrancar sin esperar al login y la sesión queda `DEGRADED` o `CONFIGURATION_ERROR`.
    - Sin configuración de la cátedra: arranca, warning con las propiedades, ningún request a la cátedra simulada.
    - La salida no contiene el JWT, la `password` ni la `redisPassword` de test.
    - Regresión: `./mvnw test` completo en verde.
  - Resultado: 2026-11-16. Creado `TechnicalSessionStartup`. Los tests de integración son cuatro clases y no tres: el caso de la cátedra que responde `401` quedó en una clase propia, `TechnicalSessionRejectedIntegrationTest`, porque cada escenario necesita su propia aplicación arrancada.
    - `./mvnw -B test -Dtest='TechnicalSession*IntegrationTest'`: 4 tests en verde, en tres corridas seguidas.
    - `TechnicalSessionIntegrationTest`: al arrancar, la sesión queda habilitada con el JWT de test y la integración de la respuesta; la cátedra simulada recibe un solo request, con `test-login`, `test-password` y `rememberMe: true`; el log dice `Technical login succeeded: groupId=proyecto-de-prueba, provisioningStatus=PROVISIONED` y la salida no contiene el JWT, la `password` ni la `redisPassword` de test.
    - `TechnicalSessionCatedraDownIntegrationTest` (nadie escucha en la dirección configurada; 3 intentos con esperas de 500 ms y 1 s): cuando el test empieza, la aplicación ya arrancó y la sesión sigue en `CONNECTING`; después queda `DEGRADED`, con los tres intentos en el log.
    - `TechnicalSessionRejectedIntegrationTest` (la cátedra responde `401`): la aplicación arranca, la sesión queda `CONFIGURATION_ERROR` con un solo request, y un pedido de renovación no envía otro.
    - `TechnicalSessionWithoutConfigurationIntegrationTest` (URL y usuario configurados, sin contraseña): arranca, warning `missing configuration properties [catalogo.catedra.password]`, `CONFIGURATION_ERROR` y ningún request a la cátedra simulada.
    - Un fallo durante la tarea, corregido: la primera versión de `TechnicalSessionIntegrationTest` tenía tres métodos y uno falló, porque la salida se captura por método y el log del login había salido durante otro. Quedó en un solo método. Era un defecto del test, no del código.
    - Prueba en negativo: con el login corriendo dentro del arranque (sin hilo aparte), `TechnicalSessionCatedraDownIntegrationTest` falló con `expected: CONNECTING but was: DEGRADED`. Archivo restaurado y hash comprobado.
    - Regresión: `./mvnw -B clean verify`: `BUILD SUCCESS`, 135 tests (contados en los reportes XML), 0 fallos; solo se crearon contenedores de `postgres:18`. JaCoCo: líneas 225/229 (98,3 %), ramas 100/106 (94,3 %). Los cinco chequeos de la skill, sin resultados.

- [ ] **TASK-07 · Prueba manual con la cátedra real**
  - Alcance: el usuario crea su `.env` a partir de `.env.example`, con la contraseña de la base y las tres variables de la cuenta técnica, sin pasarlas por el chat; levanta la base con el compose y la aplicación con `./mvnw spring-boot:run`, guardando la salida en un archivo temporal. Un único pedido a la cátedra: `POST /api/authenticate`. Sin cambios de código.
  - Depende de: TASK-06
  - Resuelve: CA-09
  - Validación: el log muestra el login técnico exitoso con el `groupId` y `PROVISIONED`; se busca en la salida que no aparezcan un JWT ni el nombre de ningún campo secreto con su valor. Después se borra el archivo temporal. Si la cátedra no está disponible, queda como no verificado.
  - Resultado: pendiente

- [ ] **TASK-08 · Cierre: regresión, chequeos y documentación**
  - Alcance: corrida completa y chequeos de la skill; crea el `CLAUDE.md` del repositorio (requisitos, comandos verificados, datos del proyecto, slices, excepciones a la skill autorizadas para X01 y cómo usar la sesión técnica) y `docs/decisiones/001-sesion-tecnica-con-la-catedra.md`. Sin cambios de código, salvo correcciones que surjan, que se anotan acá. El `README.md` lo escribe el usuario.
  - Depende de: TASK-01 a TASK-07
  - Resuelve: comprobación de todos los CA; `AGENTS.md`, "Código" (comandos documentados)
  - Validación: `./mvnw clean verify` en verde y con el reporte de JaCoCo; los cinco chequeos de la sección 12 de la skill sin resultados, salvo las excepciones autorizadas; cada comando del `CLAUDE.md` ejecutado y su resultado observado; `.env` fuera de git; revisión de que cada CA tiene su evidencia en este archivo.
  - Resultado: pendiente
