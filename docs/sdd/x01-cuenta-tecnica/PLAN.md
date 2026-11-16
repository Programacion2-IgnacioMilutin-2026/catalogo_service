# PLAN: X01 — Autenticar cuenta técnica y obtener configuración

**SPEC de referencia:** `docs/sdd/x01-cuenta-tecnica/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-15
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

Verificado el 2026-11-15 en la rama `cuenta-tecnica`. Todavía no hay código para X01.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| Clase principal | `src/main/java/catalogo/CatalogoApplication.java` | No cambia. `{basePackage}` de la skill: `catalogo` |
| Configuración | `src/main/resources/application.yaml` | Solo tiene el nombre de la aplicación. Suma la lectura del `.env`, la conexión a la base y la sección de la cátedra |
| Build | `pom.xml` | Spring Boot 4.1.2, Java 25, JaCoCo y SonarQube ya configurados. Suma la propiedad que impide a los tests leer el `.env` local |
| Tests generados | `src/test/java/catalogo/` (`CatalogoApplicationTests`, `TestcontainersConfiguration`, `TestCatalogoApplication`) | `TestcontainersConfiguration` levanta Kafka, PostgreSQL y Redis con imágenes `latest`. Se deja solo PostgreSQL con versión fija |
| Archivos ignorados | `.gitignore` | No ignora el `.env`. Se agrega antes de crear ese archivo |
| Cliente HTTP | No existe todavía | `RestClient` con `JdkClientHttpRequestFactory` (`spring-web` 7.0.10, ya presente: comprobado en el jar) para fijar los timeouts |
| Servidor para simular la cátedra en los tests | JDK 25, módulo `jdk.httpserver` | Servidor HTTP real que responde según el anexo, sin dependencias nuevas |
| `compose.yaml`, `.env.example`, `CLAUDE.md` del repositorio | No existen | Los crea esta funcionalidad, porque la estructura inicial no tuvo SDD (SPEC, "Restricciones del pedido") |

**Convenciones y slice de referencia:** no hay ningún slice en catálogo. El modelo es el slice `catedra` de turnos (`turnos_service/src/main/java/turnos/catedra/`, sus decisiones 005 y 011 y su `CLAUDE.md`); no se copia código entre repositorios. X01 no tiene endpoint: no hay capa `web`.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de la solicitud, el evento o la tarea desde que entra hasta la
respuesta, la persistencia o el mensaje publicado. Referencia el diagrama de
secuencia del proyecto si ya lo describe; usa un diagrama nuevo solo si aporta
claridad. -->

Se crea el slice `catedra`, la única puerta del servicio hacia la cátedra. En esta funcionalidad solo tiene la sesión técnica: la guarda en memoria y ofrece tres casos de uso.

| Caso de uso | Lo usa | Hace |
| --- | --- | --- |
| `StartTechnicalSessionUseCase` | El arranque | Login inicial con reintentos y espera creciente (RF-02 a RF-06, RF-14) |
| `GetTechnicalSessionUseCase` | C01 en adelante | Devuelve la sesión (JWT e integración) si está habilitada; no llama a la cátedra (RF-07, CA-06) |
| `RenewTechnicalSessionUseCase` | C01 en adelante, tras un rechazo o con la integración degradada | Un login a pedido, con los límites de D1 (RF-08, RF-10 a RF-12) |

```
ApplicationReadyEvent
└─ TechnicalSessionStartup (hilo aparte: no demora el arranque)
   └─ TechnicalSessionService.startTechnicalSession()
      └─ StartTechnicalSessionUseCaseImpl
         ├─ TechnicalLoginClient.login()  → Success | ConfigurationError | Unavailable
         │    = CatedraLoginClientAdapter: POST {base-url}/api/authenticate con RestClient
         ├─ Success, PROVISIONED y completa → sesión habilitada                      (RF-03)
         ├─ Success con otro estado         → reintento                              (RF-04)
         ├─ Success incompleta              → reintento                              (RF-14)
         ├─ ConfigurationError              → error de configuración, sin reintentos (RF-05)
         ├─ Unavailable                     → reintento con espera creciente         (RF-06)
         └─ TechnicalSessionRepository.save(...)  = adaptador en memoria
```

- **Qué decide cada capa.** El adaptador traduce la respuesta HTTP a uno de tres resultados del dominio: `200` es `Success`; `401`, `403`, `404` y cualquier otro `4xx` son `ConfigurationError`; `5xx`, timeout, conexión rechazada y una respuesta ilegible son `Unavailable`. El caso de uso decide qué hacer con cada resultado, sin conocer HTTP.
- **Qué sesión resulta de cada intento (P3).** Lo deciden los casos de uso, como en turnos: el login inicial y la renovación tienen cada uno su propia decisión sobre el resultado del login (habilitar, reintentar o quedar degradada, o error de configuración). La regla de la respuesta incompleta (RF-14) se aplica en los dos.
- **Reglas en el dominio.** `TechnicalSession` sabe si está habilitada y si se puede intentar un login en un instante dado. `TechnicalIntegration` sabe si está provista y qué datos le faltan (RF-14). `TechnicalLoginPolicy` calcula la espera de cada reintento. Esas reglas se prueban sin Spring ni HTTP.
- **Renovación para los dos rechazos (D2).** Quien pide renovar entrega la sesión que le rechazaron, sea por el `401` de la API o por las credenciales de Redis. Si esa sesión ya no es la vigente, porque otro pedido la renovó, recibe la vigente sin otro login (RF-12). En turnos se entrega solo el JWT rechazado; acá se entrega la sesión entera porque el rechazo de Redis no tiene un JWT asociado.
- **Un login a la vez (RF-12).** Las renovaciones se hacen de a una, con un candado. El login inicial deja la sesión en "conectando" mientras dura, y en ese estado no se acepta ninguna renovación: nunca coinciden dos logins.
- **Una sola sesión vigente, siempre leída en el momento.** La sesión vive solo en memoria y cada login exitoso la reemplaza entera. Los casos de uso de C01 en adelante la piden en cada operación y nunca se guardan una copia.
- **Configuración faltante o inválida (RF-01).** Si falta `base-url`, `username` o `password`, o la URL no es `http`/`https` válida, el adaptador registra un warning al crearse, con las propiedades, y `login()` devuelve `ConfigurationError` sin enviar ningún request.
- **Errores inesperados (RF-13).** Cualquier excepción no prevista durante un intento cuenta como `Unavailable`, para que la sesión nunca quede en "conectando".
- **Secretos (RF-09).** Los tipos que llevan el JWT o la `redisPassword` tienen un `toString` que los oculta. Los logs dicen el `groupId`, el estado y el motivo.

## Slices y componentes afectados

<!-- Aplica la skill hexagonal-vertical-slicing: un paquete por slice, con sus
capas domain, application e infrastructure. Identifica los slices afectados, sus
responsabilidades y la dirección de sus dependencias. Una funcionalidad puede
afectar más de un slice. Justifica cualquier slice nuevo o dependencia nueva
entre slices. -->

| Slice | Existe / nuevo | Responsabilidad y cambios | Dependencias con otros slices |
| --- | --- | --- | --- |
| `catedra` (`catalogo/catedra/`) | Nuevo | Sesión técnica con la cátedra: login, estado y renovación. Más adelante suma el snapshot, la lectura de Redis y el consumo de Kafka | Ninguna. Desde C01, otros slices usarán sus puertos de entrada; esa dependencia se decide en cada PLAN (skill §9) |
| `config` (`catalogo/config/`) | Nuevo | Configuración transversal. En esta funcionalidad, solo el reloj | No importa nada de los slices |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son
propuestas. Señala impacto sobre modelos, contratos o componentes compartidos. -->

Todo es nuevo, salvo lo marcado como "Modificar". Las rutas son propuestas.

| Slice y capa | Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- | --- |
| catedra / domain | `model/TechnicalIntegration` | Crear | `record` inmutable: `groupId`, los cuatro datos de Redis, los tres de Kafka y `provisioningStatus`. Sabe si está provista y qué datos le faltan. Su `toString` oculta la `redisPassword` | RF-03, RF-09, RF-14 |
| catedra / domain | `model/ProvisioningStatus` | Crear | `PENDING`, `PROVISIONED`, `FAILED`, `REVOKED` y `UNKNOWN` para valores no previstos | RF-03, RF-04 |
| catedra / domain | `model/IntegrationState` | Crear | `CONNECTING`, `ENABLED`, `DEGRADED`, `CONFIGURATION_ERROR` | RF-05, RF-07, RF-10, RF-11 |
| catedra / domain | `model/TechnicalSession` | Crear | `record` inmutable: estado, JWT, integración e instante del último intento. Sabe si está habilitada y si se puede intentar un login; cada cambio crea una sesión nueva. Su `toString` oculta el JWT | RF-07, RF-09, RF-10, RF-11 |
| catedra / domain | `model/TechnicalLoginResult` | Crear | Interfaz sellada con `Success(token, integration)`, `ConfigurationError(reason)` y `Unavailable(reason)` | RF-03 a RF-06 |
| catedra / domain | `model/TechnicalLoginPolicy` | Crear | Máximo de intentos, espera inicial, multiplicador, espera máxima e intervalo mínimo; calcula la espera antes de cada intento | RF-04, RF-06, RF-11 |
| catedra / domain | `ports/in/StartTechnicalSessionUseCase`, `GetTechnicalSessionUseCase`, `RenewTechnicalSessionUseCase` | Crear | Ver "Solución propuesta". `Renew` recibe la sesión rechazada | RF-02 a RF-14 |
| catedra / domain | `ports/out/TechnicalLoginClient` | Crear | Un intento de login: devuelve un `TechnicalLoginResult` | RF-02 |
| catedra / domain | `ports/out/TechnicalSessionRepository` | Crear | Lee y reemplaza la sesión vigente | RF-03 |
| catedra / application | `usecases/*UseCaseImpl` (3) | Crear | Reintentos, estados, intervalo y candado. `Start` y `Renew` deciden cada uno qué sesión resulta del login, convierten un error inesperado en "no disponible" y dejan el resultado en el log (P3) | RF-02 a RF-14 |
| catedra / application | `service/TechnicalSessionService` | Crear | Expone los tres casos de uso | — |
| catedra / infrastructure | `client/CatedraLoginClientAdapter` | Crear | `RestClient` con timeouts; traduce la respuesta a `TechnicalLoginResult`; warning por configuración faltante o URL inválida; nunca registra secretos | RF-01, RF-02, RF-05, RF-06, RF-09 |
| catedra / infrastructure | `client/dto/` (request, respuesta, integración) y `client/mapper/TechnicalIntegrationMapper` | Crear | Formato del anexo §5.2; conserva solo los datos de RF-03 | RF-03 |
| catedra / infrastructure | `client/CatedraProperties` | Crear | Propiedades de la cátedra (ver "Dependencias y configuración") | RF-01 |
| catedra / infrastructure | `client/CatedraClientConfiguration` | Crear | Convierte las propiedades de reintento en `TechnicalLoginPolicy`, para que los casos de uso no conozcan la configuración | RF-04, RF-06, RF-11 |
| catedra / infrastructure | `persistence/adapter/InMemoryTechnicalSessionRepositoryAdapter` | Crear | Guarda la sesión en memoria | RF-03 |
| catedra / infrastructure | `startup/TechnicalSessionStartup` | Crear | Escucha `ApplicationReadyEvent` y corre el login inicial en un hilo virtual | RF-02, RF-07 |
| config | `ClockConfig` | Crear | Bean `Clock.systemUTC()`, para controlar el intervalo en los tests | RF-11 |
| Proyecto | `src/main/resources/application.yaml` | Modificar | Lectura del `.env`, conexión a la base, puerto y sección de la cátedra | RF-01, CA-09 |
| Proyecto | `pom.xml` | Modificar | Los tests apuntan la ruta del `.env` a un archivo inexistente | Restricción de la SPEC (tests sin credenciales reales) |
| Proyecto | `src/test/java/catalogo/TestcontainersConfiguration.java` | Modificar | Solo PostgreSQL, con versión fija | RF-07, CA-05 |
| Proyecto | `.gitignore` | Modificar | Suma `.env` | RF-09 |
| Proyecto | `.env.example`, `compose.yaml` | Crear | Variables sin valores; base de desarrollo | RF-01, CA-09 |
| Proyecto | `CLAUDE.md` | Crear | Requisitos, comandos verificados, datos del proyecto, slices y excepciones a la skill | `AGENTS.md`, "Código" |
| Proyecto | `docs/decisiones/001-sesion-tecnica-con-la-catedra.md` | Crear | Texto de las decisiones de X01 en catálogo: reglas del login, renovación por rechazo de Redis, respuesta incompleta y convivencia de los dos JWT (P4) | — |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Operaciones, eventos y DTO de entrada y salida:** login técnico según el contrato §4.1 y el anexo §5.2. El contrato no cambia. X01 no expone operaciones a la app ni a turnos.
- **Identificadores, relaciones y restricciones:** ninguno persistido. Un `provisioningStatus` desconocido se trata como `UNKNOWN`, que equivale a "distinto de `PROVISIONED`" (RF-04).
- **Origen de los datos devueltos o publicados y transformaciones:** el JWT y la integración salen de la respuesta del login; el mapper conserva solo los datos de RF-03 e ignora los demás campos, también los desconocidos.
- **Persistencia, consultas y migraciones:** no aplica. La sesión vive en memoria (RF-03); no hay migración. La conexión a la base se configura solo para que el servicio arranque.
- **Convivencia entre datos locales y remotos:** cada login exitoso reemplaza juntos el JWT y la integración anteriores.
- **Compatibilidad y migraciones de datos existentes:** no aplica.

## Transacciones, concurrencia y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de BACKEND_GUIDELINES.md. -->

- **Estados y transiciones:** `CONNECTING` al arrancar. Con `Success`, `PROVISIONED` y datos completos pasa a `ENABLED`. Con `ConfigurationError` pasa a `CONFIGURATION_ERROR`, que es final hasta reiniciar. Con reintentos agotados pasa a `DEGRADED`. Desde `ENABLED` o `DEGRADED`, una renovación puede llevar a cualquiera de los tres.
- **Transacciones y consistencia:** no aplica. Se reemplaza la sesión completa de una vez, nunca un campo suelto.
- **Idempotencia y mensajes duplicados:** varios pedidos de renovación simultáneos producen un login (RF-12). Si la sesión rechazada ya no es la vigente, se devuelve la vigente sin login (CA-12).
- **Concurrencia y tareas programadas:** sin tareas periódicas propias (D1). El login inicial corre en un hilo propio; mientras dura, las renovaciones se responden al instante como integración no habilitada.
- **Timeouts, reintentos y errores de dependencias:** timeouts de conexión y lectura configurables. Reintentos con espera creciente, con tope de espera y de intentos (P4). Cada intento se registra con su número y su motivo (anexo §18.4).
- **Seguridad y autorización:** no hay endpoints nuevos. El JWT técnico y la `redisPassword` solo existen en memoria y solo salen por `GetTechnicalSessionUseCase` y `RenewTechnicalSessionUseCase`, hacia otros casos de uso del servicio.
- **Otras consideraciones de backend aplicables y su solución:**
  - Privacidad (RF-09): los logs dicen el `groupId`, el estado y el motivo, nunca el JWT, la `password` ni la `redisPassword`. Los DTO no generan un `toString` con secretos.
  - El log de Spring de los requests HTTP en nivel `DEBUG` podría mostrar el cuerpo con la `password`; se deja en su nivel por defecto (ver "Riesgos").
  - Arranque sin Redis ni Kafka: las dependencias ya están en el proyecto y Spring las configura solas apuntando a `localhost`. Hay que comprobar que el servicio arranca sin intentar conectarse y, si hace falta, apagar esa configuración automática (ver "Riesgos").

## Dependencias y configuración

<!-- Librerías, servicios externos, propiedades de configuración y secretos
afectados. Verifica compatibilidad con el proyecto y justifica las incorporaciones.
No agregues dependencias por defecto. Los valores sensibles y los parámetros
ajustables se externalizan. -->

- **Dependencias:** ninguna nueva. `RestClient` y `JdkClientHttpRequestFactory` (`spring-web` 7.0.10); servidor de test con `com.sun.net.httpserver` del JDK.
- **Nombres de las variables y propiedades (P2):** variables de entorno `CATALOGO_*` y propiedades propias `catalogo.*`. El nombre de la base, su usuario y los dos puertos (el del servicio y el de la base) son configurables desde el `.env`, como en turnos.
- **Propiedades nuevas en `application.yaml`:**
  - Lectura opcional del `.env`: `spring.config.import: optional:file:${catalogo.env-file:.env}[.properties]`.
  - Base: `CATALOGO_DB_HOST` (`localhost`), `CATALOGO_DB_PORT` (5433), `CATALOGO_DB_NAME` (`catalogo`), `CATALOGO_DB_USER` (`catalogo`) y `CATALOGO_DB_PASSWORD`, sin valor por defecto.
  - Hibernate solo valida las tablas, y sin sesión abierta durante el request, como en turnos.
  - Puerto del servicio: `CATALOGO_PORT` (8081; el 8080 lo usa turnos).
  - Cátedra, con vacío por defecto para que la falta dé el warning de RF-01: `catalogo.catedra.base-url: ${CATALOGO_CATEDRA_BASE_URL:}`, `username: ${CATALOGO_CATEDRA_USERNAME:}` y `password: ${CATALOGO_CATEDRA_PASSWORD:}`.
  - `catalogo.catedra.connect-timeout` (5 s) y `read-timeout` (10 s).
  - `catalogo.catedra.login.max-attempts` (5), `initial-delay` (1 s), `multiplier` (2), `max-delay` (30 s) y `min-interval` (60 s): los mismos valores que turnos (P4). Como en turnos, estos tiempos del login tienen su valor por defecto en las propiedades y no llevan variable propia en el `.env.example`.
- **Entorno de desarrollo:** `compose.yaml` con un solo servicio, `catalog-db` (PostgreSQL 18, publicado en el puerto de `CATALOGO_DB_PORT`, 5433 por defecto, y volumen con nombre fijo). El compose lee el nombre de la base, el usuario, la contraseña y el puerto de las mismas variables que la aplicación. La aplicación dentro del compose, el `Dockerfile` y `compose.full-system.yaml` quedan para la etapa de Docker. `.env.example` sigue el formato del de turnos: una sección de variables obligatorias (`CATALOGO_DB_NAME`, `CATALOGO_DB_USER`, `CATALOGO_DB_PASSWORD`, `CATALOGO_DB_PORT` y las tres de la cuenta técnica) y otra de opcionales con su valor por defecto (`CATALOGO_PORT`), sin valores reales y con el aviso de que los valores van sin comillas. El usuario completa su `.env`; las credenciales nunca pasan por el chat ni por el repositorio.
- **Tests:** cátedra simulada en un puerto aleatorio y credenciales de test; los reintentos usan esperas de milisegundos y el intervalo se controla con un reloj de test. La suite nunca lee el `.env` local.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, de integración (base de datos, mensajería), de API o prueba manual.
No todos requieren todos los métodos. Identifica tests existentes y separa los
nuevos propuestos. Incluye regresiones relevantes. Una prueba con mocks no
demuestra persistencia, transacciones ni idempotencia. -->

Todos los tests son nuevos, salvo `CatalogoApplicationTests`.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Adaptador contra la cátedra simulada (`CatedraLoginClientAdapterTest`); integración `TechnicalSessionIntegrationTest` | Servidor JDK con la respuesta del anexo §5.2 | Request con `username`, `password` y `rememberMe: true`; sesión `ENABLED` con los datos de RF-03 |
| CA-02 | Unitario `StartTechnicalSessionUseCaseImplTest` con un cliente falso que devuelve cada estado | — | Intentos igual al máximo; sesión `DEGRADED` |
| CA-03 | Adaptador con `401`, `403` y `404`; unitario del caso de uso con `ConfigurationError` | Servidor JDK | `ConfigurationError`; un solo intento; `CONFIGURATION_ERROR` |
| CA-04 | Adaptador con `503`, `500`, demora mayor al timeout y puerto cerrado; unitario del caso de uso; unitario de `TechnicalLoginPolicy` | Servidor JDK; esperas de milisegundos | `Unavailable`; esperas crecientes con tope; éxito en un intento posterior → `ENABLED` |
| CA-05 | Integración: cátedra simulada caída y cátedra que responde `401` | App completa, PostgreSQL de Testcontainers | El contexto arranca sin esperar; sesión `DEGRADED` o `CONFIGURATION_ERROR` |
| CA-06 | Unitario de los casos de uso `Get` y `Renew`: sin pedidos no hay logins; 20 hilos piden renovar a la vez | Cliente falso que cuenta llamadas y demora la respuesta | 0 logins en lo primero; 1 login en lo segundo, y todos reciben la sesión nueva |
| CA-07 | Integración y adaptador con captura de la salida (`OutputCaptureExtension`); unitarios de los `toString` | — | La salida no contiene el JWT de test, la `password` de test ni la `redisPassword` de test |
| CA-08 | Integración sin `base-url`, sin `username`, sin `password` y con URLs inválidas | App completa | Arranca; warning con la propiedad; la cátedra simulada no recibe requests |
| CA-09 | Manual con la cátedra real y el `.env` local, corrida por el usuario: un único `POST /api/authenticate` | Credenciales reales, solo locales; base levantada con el compose | Log con `groupId` y `PROVISIONED`, sin el JWT ni la `redisPassword` |
| CA-10 | Unitario de `Renew` en `DEGRADED` con un reloj controlado | — | 0 logins antes de 60 s; 1 login después |
| CA-11 | Unitario de `Renew` en `CONFIGURATION_ERROR` | — | 0 logins con varios pedidos |
| CA-12 | Unitario de `Renew`: renovar con la sesión A (pasa a B) y volver a pedir con A | — | Devuelve B; un solo login |
| CA-13 | Unitario de `Renew`: renovar y, antes del intervalo, pedir con la sesión nueva | Reloj controlado | Ningún login nuevo |
| CA-14 | Unitario de `Start` y `Renew` con un cliente que lanza una excepción | — | Intento contado como fallido; estado final `DEGRADED` o `ENABLED`, nunca `CONNECTING` |
| CA-15 | Unitario de `TechnicalIntegration` y de los casos de uso `Start` y `Renew`: respuesta sin el `id_token` y sin cada uno de los siete datos; adaptador con una respuesta incompleta | Servidor JDK | Sesión no habilitada; reintentos hasta el límite; log con el dato que falta; `ENABLED` con la respuesta completa |
| CA-16 | Ya comprobado el 2026-11-15 (SPEC, D5) | Cátedra real | Registrado en la SPEC |

**Comprobaciones de regresión:** `CatalogoApplicationTests` (el contexto arranca) y los cinco chequeos de la skill (§12).

**Comandos verificados para compilar y ejecutar tests:** `./mvnw -B -DskipTests test-compile` y `./mvnw -B clean verify`, ejecutados el 2026-11-14 sobre el proyecto generado. Los demás (`./mvnw test`, `./mvnw test -Dtest=NombreDeLaClase`, `./mvnw spring-boot:run`, `docker compose up -d --wait catalog-db`) se verifican durante la implementación y se registran en el `CLAUDE.md` del repositorio.

**Pruebas con infraestructura real o simulada:** cátedra simulada con el servidor HTTP del JDK en todos los tests automáticos; PostgreSQL real con Testcontainers en los de integración; cátedra real solo en la prueba manual de CA-09, que corre el usuario.

**Limitaciones del entorno:** CA-09 depende de que la cátedra esté disponible y de las credenciales del usuario; si no se puede ejecutar, queda como no verificado.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **Base para arrancar:** `.gitignore`, `application.yaml` (lectura del `.env`, base y puerto), `compose.yaml`, `.env.example`, `pom.xml` y `TestcontainersConfiguration`. Se comprueba levantando la base con el compose y arrancando el servicio sin Redis ni Kafka, y con la suite en verde sin leer el `.env`.
2. **Dominio:** modelos, `TechnicalLoginPolicy` y puertos. Se comprueba con unitarios de `TechnicalSession`, `TechnicalIntegration` y `TechnicalLoginPolicy`.
3. **Login inicial:** `StartTechnicalSessionUseCaseImpl`, el repositorio en memoria, el reloj y el service. Se comprueba con CA-02, CA-03, CA-04, CA-14 y CA-15 en unitarios.
4. **Renovación:** `GetTechnicalSessionUseCaseImpl` y `RenewTechnicalSessionUseCaseImpl` con el candado. Se comprueba con CA-06 y CA-10 a CA-14.
5. **Cliente de la cátedra:** adaptador, DTO, mapper y propiedades. Se comprueba con los tests del adaptador contra el servidor JDK (CA-01, CA-03, CA-04, CA-07, CA-15).
6. **Arranque:** `TechnicalSessionStartup` y la sección de la cátedra en `application.yaml` y `.env.example`. Se comprueba con la integración (CA-01, CA-05, CA-07, CA-08).
7. **Cierre:** prueba manual con la cátedra real (CA-09), regresión, chequeos de la skill, `CLAUDE.md` del repositorio y texto de decisiones.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - Que la configuración automática de Redis o de Kafka intente conectarse al arrancar o ensucie el log. Se comprueba en la etapa 1, arrancando sin Redis ni Kafka; si molesta, se apaga lo necesario y se anota en el `CLAUDE.md`.
  - Que la seguridad por defecto de Spring genere una contraseña en el log en cada arranque. Es esperado: la seguridad quedó fuera de alcance hasta el primer endpoint.
  - Que un log `DEBUG` de los requests HTTP exponga la `password` del cuerpo. Se mantiene el nivel por defecto y CA-07 revisa la salida.
  - Que un test de concurrencia pase por casualidad. El cliente falso demora la respuesta hasta que todos los hilos pidieron, así el solapamiento está garantizado.
  - Que los tests que levantan la aplicación sin cátedra muestren warnings. Es esperado (RF-01) y no afecta los resultados.
  - Limitación conocida: si la cátedra cambia la integración por su lado, catálogo la toma recién en el próximo login (un rechazo, el intervalo en estado degradado o un reinicio). `GET /api/student/integration` quedó fuera de alcance.
- **Decisiones pendientes:** Ninguna.

- **Decisiones tomadas:**
  - **Aprobación:** el usuario aprobó el PLAN el 2026-11-15. Pidió que los tests sigan, sin obligación, la estructura de los de turnos, adaptada a este servicio.
  - **P3 · Qué sesión resulta de un intento de login (2026-11-15):** lo decide cada caso de uso, como en turnos, aunque la decisión quede escrita en el login inicial y en la renovación. El usuario prefiere que, en este caso, lo que es funcional se lea en el caso de uso; aclaró que no es un criterio general y que en otras situaciones se pregunta. Cuidado a tener: una regla nueva, como RF-14, va en los dos. Alternativas descartadas: un componente común en `application/support`, que sumaba un archivo y una excepción a la skill; la regla en el modelo `TechnicalSession`, que la dejaba en un solo lugar pero apartaba el código del de turnos.
  - **P4 · Valores por defecto y texto de decisiones (2026-11-15):** los mismos valores que turnos: 5 intentos en el login inicial con esperas de 1, 2, 4 y 8 s (tope de 30 s), intervalo mínimo de 60 s entre logins a pedido, y timeouts de 5 s para conectar y 10 s para leer; todos configurables por propiedad. Se escribe `docs/decisiones/001-sesion-tecnica-con-la-catedra.md`, para que las decisiones se entiendan leyendo solo este repositorio. Alternativa descartada: dejar la explicación solo en la SPEC, el PLAN y el repositorio de turnos.
  - **P1 · Excepciones a la skill (2026-11-15):** para X01 se adoptan las mismas que en turnos, cada una con su comentario corto en el código: paquetes `infrastructure/client` (cliente HTTP hacia la cátedra) e `infrastructure/startup` (disparador del arranque); repositorio en memoria en `infrastructure/persistence/adapter`; `record` inmutables (`TechnicalSession`, `TechnicalIntegration`, `TechnicalLoginPolicy`) y resultado sellado (`TechnicalLoginResult`) en lugar de clases Lombok con setters, porque la sesión se lee en paralelo mientras un login la reemplaza; puerto de salida para una capacidad técnica (`TechnicalLoginClient`); `@Configuration` dentro del slice (`CatedraClientConfiguration`); y paquete `catalogo.config` fuera de los slices. **Alcance:** el usuario las autoriza en bloque solo para la autenticación de la cuenta técnica, porque quiere que sea igual que en turnos. En los demás casos de uso, cada excepción se analiza en su contexto. Alternativa descartada: revisarlas una por una.
  - **P2 · Nombres y puertos (2026-11-15):** variables `CATALOGO_*` y propiedades `catalogo.*`, la misma palabra que el paquete base. El servicio escucha en el 8081 (`CATALOGO_PORT`) y la base se publica en el 5433 (`CATALOGO_DB_PORT`); los dos puertos son configurables, por pedido del usuario. La base y su usuario se llaman `catalogo`, y el servicio del compose, `catalog-db`. Alternativa descartada: `CATALOG_*`, que coincide con los nombres de Docker pero no con el paquete.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados, respeta la skill hexagonal-vertical-slicing y permite
demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
