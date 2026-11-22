# PLAN: Validación del JWT en los endpoints protegidos

**SPEC de referencia:** `docs/sdd/validacion-jwt/SPEC.md`
**Versión de la spec revisada:** aprobada el 2026-11-22 (rama `validacion-jwt`, sin commitear)
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
| Seguridad | `config/SecurityConfig.java` | Una sola cadena de seguridad: sin sesión, `PUBLIC_ENDPOINTS` (los `GET` exactos de C04 y C05) con `permitAll`, y lo demás `authenticated()`, sin mecanismo para autenticarse. Se le suman la validación del token, el lector que ignora las rutas públicas, los rechazos y CORS |
| Formato de errores | `config/ApiExceptionHandler.java` | `problem+json` con `ProblemDetail` para los errores de los controllers y de Spring MVC. No ve los rechazos de la seguridad (decisión 005). Su forma es la que tiene que copiar el `401` (D3) |
| Reloj | `config/ClockConfig.java` | Bean `Clock` en UTC. Lo usa la validación del vencimiento, así un test puede fijar el reloj (D2) |
| Propiedades | `catedra/infrastructure/client/CatedraProperties.java`, `catedra/infrastructure/kafka/CatedraKafkaProperties.java`, `synchronization/infrastructure/scheduling/VerificationProperties.java`; `CatalogoApplication` con `@ConfigurationPropertiesScan` | `record` con `@ConfigurationProperties` bajo `catalogo.`, escaneados solos. El molde para las dos propiedades nuevas (clave y CORS) |
| Configuración | `src/main/resources/application.yaml`, `.env.example` | Variables `CATALOGO_…` con su valor por defecto en el yaml. Se suman las dos nuevas, sin test de variables |
| Tokens de prueba | `TestTokens.java` | Válido, vencido y alterado, todos con `sub` `42` y `ROLE_USER`, firmados con un par RSA generado en cada corrida. No expone la clave pública. Se amplía (P6) |
| Configuración de test | `TestcontainersConfiguration.java` | Solo PostgreSQL 18. Candidata para registrar la clave de test en los contextos que levantan la aplicación (P6) |
| Tests que se tocan | `catalog/ProfessionalCategoriesIntegrationTest.java`, `catalog/infrastructure/web/controller/ProfessionalCategoryControllerTest.java`, `catalog/infrastructure/web/controller/ProfessionalControllerTest.java`, `catalog/ProfessionalsIntegrationTest.java` | `anyOtherRouteWithoutAuthenticationIsRejectedWithoutData` espera `403` en cuatro rutas. Los dos `@WebMvcTest` importan `SecurityConfig` y prueban cinco variantes de `Authorization` sobre C04 y C05; el de integración de C05, un token alterado (CA-07) |
| Dependencias | `pom.xml` | Ya están `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server` y sus starters de test. No hace falta ninguna nueva |
| Claves de turnos | `turnos_service/secrets/jwt-private.pem` (`600`) y `jwt-public.pem` (`644`) | Existen en la máquina de desarrollo e ignoradas por git en turnos. Catálogo apunta a la pública sin copiarla (D4), así que su `.gitignore` no cambia |

Hechos comprobados el 2026-11-22 para este PLAN (leyendo el código de las librerías con `javap`, sin ejecutar):

- **Versiones:** Spring Security 7.1.2 (config, core, web, oauth2-jose y oauth2-resource-server), Nimbus JOSE 10.9.1 y Spring Boot 4.1.2 (en el jar del proyecto).
- **El decodificador:** `NimbusJwtDecoder.withPublicKey(RSAPublicKey)` con `signatureAlgorithm(…)`, `validateType(boolean)` y `build()`; `setJwtValidator(…)` en el decodificador.
- **El vencimiento:** `JwtTimestampValidator(Duration)` acepta la tolerancia, `setClock(Clock)` el reloj, y en esta versión `setAllowEmptyExpiryClaim(boolean)` decide si se acepta un token sin `exp` (D2).
- **Leer la clave:** `RsaKeyConverters.x509()` (en `spring-security-core`) convierte un PEM X.509 en `RSAPublicKey`.
- **El lector del token:** `DefaultBearerTokenResolver` implementa `BearerTokenResolver`, que la configuración del resource server permite reemplazar (P2).
- **CSRF:** al activar el resource server, Spring Security exime de CSRF a los pedidos que traen `Bearer` (`registerDefaultCsrfOverride` en `OAuth2ResourceServerConfigurer`). Qué manejador escribe el `403` de CSRF no se vio: se comprueba con una prueba descartable (P4).
- **El decodificador de Spring Boot** (`JwtDecoderConfiguration`, con la propiedad `spring.security.oauth2.resourceserver.jwt.public-key-location`): solo se crea si la propiedad está, lee la clave al arrancar y usa los validadores por defecto de Spring (`JwtValidators.createDefault` o `createDefaultWithValidators`). Se aparta con un `JwtDecoder` propio (`@ConditionalOnMissingBean`). Ver P3.
- **Corregido en TASK-01 (2026-11-22, leyendo `JwtValidators` con `javap`):** `createDefaultWithValidators(lista)` agrega por su cuenta el validador del vencimiento por defecto (60 s de tolerancia), el de `typ` (`JwtTypeValidator.jwt()`) y el de la huella del certificado **solo si la lista no trae uno de ese tipo**. Antes este PLAN decía que la tolerancia de 60 s no se podía sacar con la propiedad de Spring Boot: sí se puede, pasándole un validador de vencimiento propio. P3 no cambia, porque esa opción igual no cumple D4. El decodificador propio usa ese mismo método con sus dos validadores (vencimiento y `sub`), y así conserva la revisión de `typ` como la trae Spring (P3.4).
- **No hay ningún test que fije todos los campos de un `problem+json` del servicio:** por eso se comprobó con la prueba descartable de abajo.

Comprobado el 2026-11-22 con una prueba descartable: un `@WebMvcTest` de `ProfessionalController` con una cadena de seguridad de prueba, con el resource server, CSRF como viene y un manejador distinto en cada lugar posible, que marcaba con un encabezado quién respondía. Se corrió sola, se leyó la salida y se borró, junto con su clase compilada y su reporte; `git status` quedó solo con `docs/sdd/validacion-jwt/`.

| Pedido | Estado | Quién respondió |
| --- | --- | --- |
| `POST /api/professionals` sin token, y con `Basic abc` | `403` | El `accessDeniedHandler` de `exceptionHandling`, con `MissingCsrfTokenException`: **el `403` de CSRF pasa por el mismo manejador que se configure** (D6) |
| `POST` con un `Bearer` que el decodificador rechaza | `401` | El `authenticationEntryPoint` del resource server |
| `POST` con un `Bearer` aceptado | `405` | `ApiExceptionHandler`: `{"detail","instance","status","title"}` |
| `GET /api/professionals/15` sin token | `401` | El `authenticationEntryPoint` de `exceptionHandling` |
| `GET /api/professionals/15` con un `Bearer` rechazado | `401` | El del resource server |
| `GET /api/professionals/15` con un `Bearer` aceptado | `404` | `ApiExceptionHandler`, `"detail":"No static resource api/professionals/15."` |
| `GET /api/professionals` con el catálogo sin inicializar | `503` | El controller y `ApiExceptionHandler`, con `code` |

- **Hay dos lugares para el `401`:** sin token lo responde el de `exceptionHandling`, y con un token inválido, el del resource server. Para que el cuerpo sea idéntico (D3), los dos tienen que ser el mismo manejador.
- **Los errores del servicio llevan `detail`, `instance`, `status` y `title`**, más `code` cuando corresponde, **sin `type`**. Es la forma que copia el rechazo.

**Convenciones y slice de referencia:** la skill `hexagonal-vertical-slicing` no dice dónde vive la seguridad; para un concepto global sugiere un *shared kernel* (§9). El proyecto ya tiene `catalogo.config` para la configuración transversal (excepción de X01), donde están `SecurityConfig` y `ApiExceptionHandler`. Turnos (`docs/sdd/validacion-jwt/` y sus decisiones 006 a 008) es solo un antecedente: cada decisión se analiza para catálogo.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de la solicitud, el evento o la tarea desde que entra hasta la
respuesta, la persistencia o el mensaje publicado. Referencia el diagrama de
secuencia del proyecto si ya lo describe; usa un diagrama nuevo solo si aporta
claridad. -->

Se usa el soporte de *resource server* de Spring Security, que ya está en el proyecto. La validación no es un caso de uso: ocurre en la cadena de filtros, antes de que el pedido llegue a cualquier controller, y no toca `domain` ni `application` de ningún slice. Dónde vive cada pieza: P1.

Recorrido de un pedido (las piezas salen de P1 a P5):

```
Pedido
└─ Cadena de seguridad (SecurityConfig)
   ├─ CORS: origen no permitido → 403; consulta previa de uno permitido → respondida sin token     (RF-11, P5)
   ├─ CSRF: POST/PUT/DELETE sin Bearer → 403                                                        (RF-12, P4)
   ├─ Lector del token: en las rutas públicas no busca nada, así que nunca rechaza ahí              (RF-07, P2)
   ├─ Sin token u otro esquema → anónimo → la autorización lo rechaza → 401                         (RF-01, RF-02)
   ├─ Decodificador (P3)
   │  ├─ firma RS256 con la pública de turnos; sin clave utilizable, rechaza todo                   (RF-03, RF-08)
   │  ├─ exp obligatorio, sin tolerancia, con el Clock del servicio                                  (RF-04)
   │  └─ sub solo dígitos, dentro del límite de un id de turnos                                      (RF-05)
   │     → cualquier falla: 401
   └─ 401 (y 403) en problem+json con un cuerpo fijo, sin code ni motivo                              (RF-06, RF-10, P4)
└─ Spring MVC: hoy, sin rutas protegidas, 404 o 405 por ApiExceptionHandler; desde C06, su controller
```

- **Sin estado (RF-09):** el decodificador solo usa la clave pública, cargada al arrancar; no consulta la base ni guarda nada.
- **401 antes que 400 (RF-14):** al estar en la cadena de filtros, el token se valida antes de que Spring MVC lea los parámetros.
- **Sin roles:** no se lee `auth`; la regla de autorización es "autenticado" para todo lo que no es público.

## Slices y componentes afectados

<!-- Aplica la skill hexagonal-vertical-slicing: un paquete por slice, con sus
capas domain, application e infrastructure. Identifica los slices afectados, sus
responsabilidades y la dirección de sus dependencias. Una funcionalidad puede
afectar más de un slice. Justifica cualquier slice nuevo o dependencia nueva
entre slices. -->

| Slice | Existe / nuevo | Responsabilidad y cambios | Dependencias con otros slices |
| --- | --- | --- | --- |
| `config` (`catalogo/config/`) | Existente (excepción autorizada en X01) | Toda la seguridad: la cadena, el lector del token, el decodificador con la clave, los rechazos y CORS (P1) | Ninguna: no importa nada de los slices, y ningún slice la importa |
| `catalog`, `synchronization`, `catedra` | Existentes | Sin cambios en su código de producción. Cambian tests de `catalog` (P6) | Sin cambios |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son
propuestas. Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Slice y capa | Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- | --- |
| config | `SecurityConfig` | Modificar | Suma el resource server con el decodificador de P3, el lector del token de P2 (que usa la misma `PUBLIC_ENDPOINTS` que el `permitAll`), los rechazos de P4 y CORS de P5. Sigue sin sesión, con CSRF como viene (D6) y `authenticated()` para todo lo que no es público, sin roles | RF-01, RF-02, RF-07, RF-12, RF-14 |
| config | `JwtProperties` (nombre propuesto) | Crear | `record` con `catalogo.jwt.public-key-location`. Vacía, o presente sin valor, cuenta como no configurada (P3) | RF-08 |
| config | `JwtDecoderConfig` (nombre propuesto) | Crear | Lee la clave al arrancar con `RsaKeyConverters.x509()` y la revisa (RSA, 2048 bits o más). Con clave: decodificador `RS256` con el vencimiento sin tolerancia y con el `Clock` del servicio, `exp` obligatorio y `sub` solo dígitos dentro del rango de un `Long`. Sin clave utilizable: un warning al arrancar, sin el contenido del archivo, y un decodificador que rechaza todo token (P3) | RF-03, RF-04, RF-05, RF-08, RF-09, RF-10 |
| config | `CorsProperties` (nombre propuesto) | Crear | `record` con `catalogo.cors.allowed-origins`, lista vacía por defecto (P5) | RF-11 |
| config | `SecurityConfig` (CORS) | Modificar | La configuración CORS, registrada siempre: los orígenes de `CorsProperties`, `GET`, `Authorization`, sin cookies ni encabezados expuestos (P5) | RF-11 |
| config | `SecurityProblemHandler` (nombre propuesto) | Crear | Punto de entrada y manejador de acceso denegado a la vez. Escribe un `ProblemDetail` en `application/problem+json` con `title`, `status`, un `detail` fijo por estado e `instance`, sin `code` ni motivo; el `401` lleva `WWW-Authenticate: Bearer`. No escribe nada en el log. `SecurityConfig` lo conecta en los dos lugares del `401` (el general y el del resource server) y en el del `403` (P4) | RF-06, RF-10, RF-12 |
| — | `src/main/resources/application.yaml` | Modificar | `catalogo.jwt.public-key-location: ${CATALOGO_JWT_PUBLIC_KEY_LOCATION:}` y `catalogo.cors.allowed-origins: ${CATALOGO_CORS_ALLOWED_ORIGINS:}` | RF-08, RF-11 |
| — | `.env.example` | Modificar | `CATALOGO_JWT_PUBLIC_KEY_LOCATION=` vacía, con un aviso de que hay que poner la ubicación de la clave pública del JWT de turnos, sin la ruta (indicación del usuario). `CATALOGO_CORS_ALLOWED_ORIGINS` comentada, con el aviso de no usar `*` | RF-08, RF-11 |
| tests | `TestTokens` | Modificar | Suma variantes (sin `exp`, sin `sub` o con uno dado, firmado con otro par, `HS256`, sin firma, con un `auth` dado), tokens respecto de un instante fijo, la clave pública en un PEM temporal y PEM de prueba para CA-10 y CA-12 (otro par, 1024 bits y una privada). `valid`, `expired` y `altered` siguen con su nombre (P6.1) | CA-03 a CA-05, CA-08, CA-10, CA-12 |
| tests | `TestcontainersConfiguration` | Modificar | Registra `catalogo.jwt.public-key-location` con la clave de `TestTokens` en todos los contextos que la importan (P6.1) | CA-07 |
| tests | `TestSecurityConfiguration` (nombre propuesto) | Crear | La seguridad real (cadena, decodificador, manejador y propiedades) con la clave de test, para los `@WebMvcTest` (P6.1) | CA-01 a CA-09, CA-13, CA-14 |
| tests | `ProfessionalCategoryControllerTest`, `ProfessionalControllerTest` | Modificar | Importan `TestSecurityConfiguration` en lugar de `SecurityConfig`; sus cinco variantes de `Authorization` pasan a usar un token válido de verdad (P6.1) | CA-07 |
| tests | `config/JwtDecoderConfigTest` | Crear | Unitario, sin Spring y con un reloj fijo: las variantes del token y de la clave (P6.3) | CA-03, CA-04, CA-05, CA-10, CA-12 |
| tests | `config/ProtectedRoutesSecurityTest` (nombre propuesto) | Crear | `@WebMvcTest` con `TestSecurityConfiguration`, contra las rutas reales (P6.2, P6.3) | CA-01, CA-02, CA-06, CA-08, CA-09, CA-14 |
| tests | `config/CorsSecurityTest` (nombre propuesto) | Crear | `@WebMvcTest` sin orígenes y con uno de prueba (P6.3) | CA-13 |
| tests | `config/SecurityIntegrationTest` (nombre propuesto) | Crear | Aplicación completa con el servidor real: recibe el test de las otras rutas, con los `GET` en `401` y los `POST` en `403`; el reenvío a `/error` que `@WebMvcTest` no hace; los tokens en el log (P6.3) | CA-06, CA-07, CA-11, CA-14 |
| tests | `config/SecurityWithoutKeyIntegrationTest` (nombre propuesto) | Crear | Aplicación completa sin clave, con un solo método, porque mira el log del arranque (P6.3) | CA-12 |
| tests | `catalog/ProfessionalCategoriesIntegrationTest` | Modificar | Pierde `anyOtherRouteWithoutAuthenticationIsRejectedWithoutData`, que pasa a `SecurityIntegrationTest` (P6.3) | — |
| docs | `docs/decisiones/007-seguridad-del-servicio.md` | Crear | Las decisiones y limitaciones de RF-13, con por qué C06 está protegido (P7) | RF-13 |
| docs | `docs/decisiones/005-manejador-global-de-errores.md` | Modificar | En "Qué no hace", la línea de los rechazos de la seguridad pasa a decir que los escribe `SecurityProblemHandler`, con la misma forma (decisión 007) (P7) | RF-06 |
| — | `CLAUDE.md` | Modificar | La sección "Seguridad" reescrita; las dos variables en Requisitos y en la tabla de propiedades; lo nuevo de los tests; `007` en "Documentación del servicio" (P7) | — |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Operaciones, eventos y DTO de entrada y salida:** no hay operaciones nuevas. El `401` y el `403` siguen el contrato común §1.4 y §2, sin `code` (D3). El contrato no cambia.
- **Identificadores, relaciones y restricciones:** `sub` es el `id` del usuario de turnos como texto, solo dígitos (D1). Su límite exacto: P3.
- **Origen de los datos devueltos o publicados y transformaciones:** no aplica: la validación no devuelve datos.
- **Persistencia, consultas y migraciones:** no aplica: la validación no lee ni escribe la base (RF-09).
- **Convivencia entre datos locales y remotos:** no aplica.
- **Compatibilidad y migraciones de datos existentes:** los tokens que ya emite T02 se validan sin cambios en turnos (restricción de la SPEC). Se comprueba con un token emitido por turnos de verdad (P6).

## Transacciones, concurrencia y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de BACKEND_GUIDELINES.md. -->

- **Estados y transiciones:** no aplica.
- **Transacciones y consistencia:** no aplica.
- **Idempotencia y mensajes duplicados:** no aplica.
- **Concurrencia y tareas programadas:** el decodificador y los manejadores no tienen estado mutable: la clave se lee una vez al arrancar y se comparte entre pedidos sin bloqueos.
- **Timeouts, reintentos y errores de dependencias:** no hay dependencias externas.
- **Seguridad y autorización:**
  - Solo `RS256` con la pública de turnos: un token `HS256` o sin firma se rechaza (RF-03, P3).
  - Sin roles: lo que no es público exige estar autenticado, nada más (indicación del usuario).
  - Dos caminos al `401`: sin token u otro esquema, el pedido llega anónimo y lo rechaza la autorización; con un token inválido, lo rechaza el filtro del token. Los dos usan `SecurityProblemHandler`, así el cuerpo es idéntico (RF-06, P4; ver "Hechos comprobados").
  - El `403` de CSRF pasa por el mismo manejador (RF-12, comprobado).
  - Rutas públicas: el lector del token comparte `PUBLIC_ENDPOINTS` con el `permitAll` (P2).
  - CORS: ver P5.
- **Otras consideraciones de backend aplicables y su solución:** privacidad (RF-10): ni el token ni el contenido del archivo de la clave van al log ni a una respuesta. Un rechazo no escribe nada en el log; para depurar, se puede encender por un rato `logging.level.org.springframework.security=DEBUG`, que no muestra el token (P4).

## Dependencias y configuración

<!-- Librerías, servicios externos, propiedades de configuración y secretos
afectados. Verifica compatibilidad con el proyecto y justifica las incorporaciones.
No agregues dependencias por defecto. Los valores sensibles y los parámetros
ajustables se externalizan. -->

- **Dependencias:** ninguna nueva.
- **Propiedades nuevas:** van en `application.yaml`, en `.env.example` y en la tabla del `CLAUDE.md`, sin test de variables.
  - `catalogo.jwt.public-key-location` (`CATALOGO_JWT_PUBLIC_KEY_LOCATION`): ubicación del PEM de la clave pública de turnos, con `file:` (o `classpath:` en los tests). Vacía por defecto: el servicio arranca con el warning de D4. Para desarrollar con `./mvnw`, cada uno la pone en su `.env` (por ejemplo, apuntando al `secrets/jwt-public.pem` de turnos); el `.env.example` solo avisa, sin la ruta (P3).
  - `catalogo.cors.allowed-origins` (`CATALOGO_CORS_ALLOWED_ORIGINS`): orígenes exactos separados por comas, vacía por defecto: ningún navegador (P5).
- **Clave:** no es un secreto, pero no se versiona en catálogo: la variable apunta al archivo de turnos.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, de integración (base de datos, mensajería), de API o prueba manual.
No todos requieren todos los métodos. Identifica tests existentes y separa los
nuevos propuestos. Incluye regresiones relevantes. Una prueba con mocks no
demuestra persistencia, transacciones ni idempotencia. -->

Los nombres de los tests describen el comportamiento, sin citar documentos. Nada depende de la velocidad de la máquina: los bordes del vencimiento se prueban solo en el unitario, con un reloj fijo, y las integraciones usan tokens con una hora de margen; ninguna detiene la base.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Web `ProtectedRoutesSecurityTest`; en integración, el test de las otras rutas | Clave de test | `401` `problem+json`, sin datos, en `/api/professionals/15` y en una ruta inexistente |
| CA-02 | Web | `Basic abc`, `Bearer`, `Bearer ` y `Bearer xxx` | `401` en todos |
| CA-03 | Unitario `JwtDecoderConfigTest`; web para el `401` | Otro par, `HS256`, sin firma, contenido cambiado | Rechazados; `401` |
| CA-04 | Unitario con reloj fijo | Vence 1 s antes y 1 s después del reloj; sin `exp` | Rechazado, aceptado y rechazado. Prueba en negativo: con la tolerancia por defecto de Spring, el vencido hace 1 s pasa |
| CA-05 | Unitario; web para un caso | Sin `sub`; `abc`, `-1`, `4 2`, `٤٢`, `9223372036854775808` (uno más que el mayor `Long`), y `42` | Rechazados todos menos `42` |
| CA-06 | Web: comparar los `401` de CA-01, CA-02, CA-03, CA-05 y CA-09; integración con el servidor real | — | Cuerpos idénticos, sin `code`, `WWW-Authenticate: Bearer` sin motivo |
| CA-07 | Tests de los controllers de C04 y C05 (existentes) y `ProfessionalsIntegrationTest`, con el token válido de verdad; `SecurityIntegrationTest` con el servidor real | Clave de test en el contexto, copia inicializada | Cada respuesta igual a la del pedido sin encabezado |
| CA-08 | Web | Tokens válidos sin `auth`, con `auth` vacío, `ROLE_USER` y `ROLE_ADMIN`, a las tres rutas | `404` `problem+json` en todos |
| CA-09 | Web | `/api/professionals/abc` sin token y con un token válido | `401` y `404` |
| CA-10 | Unitario: dos decodificadores armados desde el mismo archivo, y uno con el PEM de otro par | — | Aceptado en los dos primeros; rechazado en el tercero |
| CA-11 | `SecurityIntegrationTest` con `OutputCaptureExtension`; los cuerpos en web; el warning en el unitario | Los tokens de los escenarios | Ni el token ni el contenido de la clave en la salida ni en los cuerpos |
| CA-12 | Unitario: sin propiedad, vacía, archivo inexistente, archivo que no es una clave, 1024 bits y una privada. Integración `SecurityWithoutKeyIntegrationTest` | PEM de prueba de `TestTokens` | El decodificador rechaza todo y el warning dice qué pasó, sin el contenido; con la aplicación: arranca, warning, `401` en una protegida y C04 responde |
| CA-13 | Web `CorsSecurityTest`, en dos grupos con propiedades distintas | `catalogo.cors.allowed-origins=http://localhost:3000` en uno | Lo que dice CA-13 |
| CA-14 | Web, y el test de las otras rutas con el servidor real | `POST` sin token, con uno inválido y con uno válido | `403` `problem+json`, `401` y `405` |
| CA-15 | Revisión del texto `007` al cerrar | — | Todas las decisiones y limitaciones de RF-13, sin contradecir lo observado |
| Restricción: tokens de T02 sin cambios | Prueba manual (P6.4), la corre el usuario | Turnos y catálogo en local, sin la cátedra | Con el token real: `404` en `/api/professionals/15` y C05 igual que sin token; sin token y con el token alterado, `401` |

**Pruebas en negativo previstas:** sacar el lector propio (CA-07 falla en las rutas públicas); la tolerancia por defecto (CA-04); `exp` opcional (CA-04); sin la validación del `sub` (CA-05); el manejador solo en uno de los dos lugares del `401` (CA-06); la configuración CORS sin registrar con la lista vacía (CA-13). Cada una se restaura y se comprueba el hash.

**Comprobaciones de regresión:** la suite completa (630 tests al cerrar C05), en especial CA-05 de C04 y CA-11 de C05, y los tres E2E, que reciben la clave de test por `TestcontainersConfiguration` sin cambios (no llaman rutas protegidas); los cinco chequeos de la skill.

**Comandos verificados para compilar y ejecutar tests:** `./mvnw test`, `./mvnw test -Dtest='Clase1,Clase2'` y `./mvnw clean verify`, documentados en el `CLAUDE.md`.

**Pruebas con infraestructura real o simulada:** PostgreSQL de Testcontainers en las integraciones, y claves RSA reales generadas en cada corrida. Turnos se "simula" como emisor: `TestTokens` firma como turnos y el servicio arranca con su clave pública; catálogo nunca llama a turnos, así que no hay otra llamada que simular. Ninguna prueba necesita la cátedra. La prueba manual de P6.4 levanta los dos servicios reales en local, sin la cátedra.

**E2E:** sin cambios. La validación no usa la cátedra, que es lo que prueban los E2E de catálogo. Cuando llegue C06, su E2E puede hacer de turnos: sincronizar el snapshot real y pedir C06 con un token de `TestTokens`, mirando solo lo que responde catálogo, como se acordó para los E2E: el otro servicio se simula.

**Limitaciones del entorno:** ninguna conocida: la validación no depende de la cátedra, de Redis ni de Kafka.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. **La clave y el decodificador.** `JwtProperties`, `JwtDecoderConfig`, la propiedad en `application.yaml` y `.env.example`, `TestTokens` ampliado y `JwtDecoderConfigTest` (CA-03, CA-04, CA-05, CA-10 y CA-12 en unitario). Comprobación: el unitario y sus pruebas en negativo. Todavía nadie usa el decodificador.
2. **La cadena.** `SecurityProblemHandler`, `SecurityConfig` con el resource server, el lector propio y los manejadores, `TestSecurityConfiguration`, la clave en `TestcontainersConfiguration`, los tests de los controllers de C04 y C05, y `ProtectedRoutesSecurityTest` (CA-01, CA-02, CA-06 a CA-09 y CA-14). El test de las otras rutas se mueve aquí a `SecurityIntegrationTest`, con lo que espera ahora: con la cadena nueva, el de C04 dejaría de pasar (ajuste al armar TASKS, 2026-11-22). Comprobación: los tests web y la suite completa, porque cambia la configuración de 36 clases.
3. **CORS.** `CorsProperties`, su parte en `SecurityConfig`, la propiedad en `application.yaml` y `.env.example`, y `CorsSecurityTest` (CA-13). Toca `SecurityConfig` y el yaml otra vez: se frena después de la etapa 2 para que el usuario commitee.
4. **La aplicación completa.** `SecurityIntegrationTest`, que ya tiene el test movido de C04, se amplía, y `SecurityWithoutKeyIntegrationTest` (CA-06, CA-07, CA-11, CA-12 y CA-14 con el servidor real).
5. **La prueba con turnos** (P6.4): la lista de pedidos y los comandos para el usuario, y el registro de lo que observe.
6. **Cierre.** `007`, la línea de la `005`, el `CLAUDE.md`, `./mvnw clean verify`, los chequeos de la skill y CA-15.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:**
  - **Los `@WebMvcTest` de C04 y C05 importan `SecurityConfig`**, que va a necesitar el decodificador y sus propiedades: sin ajuste, no arrancan. Se resuelve con `TestSecurityConfiguration` (P6.1). Sin comprobar: si en un `@WebMvcTest` el `@ConfigurationPropertiesScan` de `CatalogoApplication` registra las propiedades nuevas; si no, `TestSecurityConfiguration` las habilita de forma explícita.
  - **El cambio en `TestcontainersConfiguration` alcanza a 36 clases:** se corre la suite completa al terminar la etapa 2, antes de seguir.
  - **El lector del token desparejo de `PUBLIC_ENDPOINTS`:** si una ruta pública no estuviera en el lector, un token inválido daría `401` ahí. Lo evita P2, y lo detectan CA-05 de C04, CA-11 de C05 y CA-07.
  - **Lo deducido sin ejecutar** (el camino del `403` de CSRF y los campos del `problem+json`): comprobado con la prueba descartable (ver "Hechos comprobados"). Falta verlo con el servidor real, que hace el reenvío a `/error` que `@WebMvcTest` no hace: lo cubre la integración (P6).
- **Decisiones pendientes:**
  - Ninguna.
- **Decisiones tomadas:**
  - **Aprobación (2026-11-22):** el usuario aprobó el PLAN con P1 a P8, la estrategia de validación y el orden de implementación.
  - **P1 · Dónde vive la validación (2026-11-22): opción a), todo en `catalogo.config`**, junto a `SecurityConfig` y `ApiExceptionHandler`, dentro de la excepción de X01 para ese paquete (configuración transversal, sin lógica de negocio). En catálogo ningún slice es dueño de la identidad: solo valida los tokens de turnos, y las rutas protegidas son de dos slices (C06 en `catalog`, C07 en `synchronization`). Ningún slice importa la seguridad. Alternativas descartadas: b), un paquete `catalogo.security`, que separa sin ganar nada y suma otra excepción para lo mismo que ya contiene `config`; c), dentro de un slice, como turnos en `user`, donde aquí no hay un dueño (la ruta de C07 es de `synchronization`) y suma un paquete `infrastructure/security`; d), un *shared kernel* (skill §9), que sirve para compartir tipos del dominio, y aquí no hay ninguno: ningún slice lee hoy la identidad. Si C06 o C07 necesitan un tipo para ella, se revisa ahí.
  - **P2 · Cómo se ignora el token en las rutas públicas (2026-11-22): opción a), un lector del token propio en una sola cadena.** En una ruta pública no busca nada; en las demás delega en el lector de Spring (`DefaultBearerTokenResolver`). Usa la misma `PUBLIC_ENDPOINTS` que el `permitAll`, así las dos no pueden quedar desparejas (RF-07). Es lo que hizo turnos (su P2), por las mismas razones. Alternativas descartadas: b), dos cadenas (pública y protegida), que repiten lo común (sin sesión, CORS, rechazos) y dependen del orden entre ellas; c), sacar las rutas públicas de la seguridad (`web.ignoring()`), que las deja también fuera de CORS (no se cumpliría RF-11 en C04 y C05) y sin los encabezados de seguridad, y que Spring desaconseja.
  - **P3 · El decodificador y la clave (2026-11-22): opción a), un decodificador propio en `config`**, con los detalles confirmados por el usuario:
    1. La propiedad `catalogo.jwt.public-key-location` (`CATALOGO_JWT_PUBLIC_KEY_LOCATION`), vacía por defecto; acepta `file:` y `classpath:`. **En el `.env.example` va vacía, con un aviso de que hay que poner la clave pública de turnos, sin la ruta** (indicación del usuario).
    2. "No válida": no se puede leer, no es un PEM de clave pública o no es RSA, o tiene menos de 2048 bits. Una más larga se acepta: rechazarla no protege nada, y si turnos cambia a una de 4096, catálogo sigue funcionando.
    3. El `sub`: solo dígitos del 0 al 9 (no los de otras escrituras), dentro del rango de un `Long`, el tipo del `id` en turnos; hasta 19 dígitos, como turnos.
    4. `typ`: como lo trae Spring, ausente o `JWT`. Los tokens de turnos no lo traen (T02 de turnos); lo comprueba la prueba con un token real (P6).
    5. El warning: una sola vez, al arrancar, con qué pasó (no configurada, no se pudo leer, no es una clave pública RSA, menos de 2048 bits) y la ubicación configurada; nunca el contenido del archivo ni el mensaje de la excepción. Los rechazos por falta de clave no escriben nada por pedido.

    Alternativa descartada: b), la propiedad de Spring Boot (`spring.security.oauth2.resourceserver.jwt.public-key-location`), que no deja arrancar con un archivo inválido ni sin la propiedad (contra D4). Al decidir se dijo también que siempre incluía la tolerancia de 60 s (contra D2); en TASK-01 se vio que no: con un validador de vencimiento propio, Spring no agrega el suyo (ver "Hechos comprobados"). La razón de D4 alcanza para descartarla.
  - **P5 · CORS (2026-11-22): aprobado como se propuso.** `catalogo.cors.allowed-origins` (`CATALOGO_CORS_ALLOWED_ORIGINS`), orígenes exactos separados por comas, vacía por defecto y comentada en el `.env.example`. A un origen permitido: `GET` (la consulta previa la resuelve Spring) con `Authorization`, sin cookies ni encabezados expuestos; sin `Content-Type`, porque ningún pedido tiene cuerpo. La configuración se registra siempre, aunque esté vacía (D5). `*` no se usa: lo dicen el texto de seguridad (`007`) y el `CLAUDE.md`, sin validarlo en el código, como turnos. Al decidir decía "el `.env.example` y el texto de seguridad"; el usuario sacó el aviso del `.env.example` al commitear TASK-04, y en el cierre (2026-11-22) eligió dejarlo así y ajustar esta frase. Es la decisión 007 de turnos ajustada a catálogo: turnos permite además `POST`, `Content-Type` y expone `Retry-After`.
  - **P4 · Quién escribe los rechazos (2026-11-22): opción a), una pieza propia en `config`** (`SecurityProblemHandler`, nombre propuesto), conectada en los dos lugares del `401` y en el del `403` (ver "Hechos comprobados"). Escribe un `ProblemDetail`, el mismo tipo que usa `ApiExceptionHandler`, así los campos coinciden: `title`, `status`, un `detail` fijo por estado (por ejemplo, "A valid bearer token is required" en el `401`) e `instance`, sin `code`; el `401` con `WWW-Authenticate: Bearer`, sin el motivo. Un rechazo no escribe nada en el log: cada pedido sin token dejaría una línea, y no es un error del servicio. Alternativas descartadas: b), pasar los rechazos a `ApiExceptionHandler`, que pasaría a conocer los tipos de Spring Security, contra lo que dice la decisión 005, con un recorrido más indirecto; c), lo que trae Spring, un `401` sin cuerpo con el motivo en el encabezado, que no cumple D3.
  - **P7 · Los textos de decisión (2026-11-22): opción a), uno solo, `docs/decisiones/007-seguridad-del-servicio.md`**, con todo lo de RF-13: un solo lugar para la descripción que pide el enunciado §12, con las limitaciones juntas. Se suman una línea en "Qué no hace" de la 005 ("Los escribe `SecurityProblemHandler`, con la misma forma (decisión 007)") y los cambios del `CLAUDE.md` de la tabla de componentes. Alternativas descartadas: b), dos textos como turnos, que reparten las limitaciones y la descripción, cuando en catálogo CORS y CSRF son cortos; c), solo el `CLAUDE.md` y la SPEC, que rompe la convención de `docs/decisiones/`.
  - **P6.1 · Con qué clave arrancan los tests (2026-11-22): opción a), la clave de test en todos los contextos, y una `TestSecurityConfiguration` para los `@WebMvcTest`.** `TestcontainersConfiguration` registra la clave de `TestTokens` (36 clases la importan), así todos prueban la configuración real y el token "bien firmado" de CA-05 de C04 y CA-11 de C05 pasa a ser válido para la aplicación, que es lo que pide CA-07. No crea contextos nuevos. Los tests que necesitan otra clave, o ninguna (CA-10 y CA-12), la reemplazan con su propia propiedad. Los dos `@WebMvcTest` importan `TestSecurityConfiguration` en lugar de `SecurityConfig`, y C06 y C07 harán lo mismo: un cambio en la seguridad se ajusta en un solo lugar. Alternativas descartadas: b), la clave solo en las clases de seguridad, con la que los demás contextos arrancan con el warning y CA-07 no se prueba con un token válido en la aplicación completa; e importar en cada `@WebMvcTest` las piezas y la propiedad, que lo repite en cada uno.
  - **P6.2 · Contra qué rutas se prueba lo protegido (2026-11-22): opción a), las rutas reales** (`GET /api/professionals/15`, `GET /api/professionals/abc`, `GET /api/synchronization/status` y una inexistente), que con un token válido responden `404` en `problem+json`: el pedido pasó la seguridad. No hay código solo para los tests, y es lo que dice la SPEC en "Cómo se comprueba". Cuando lleguen C06 y C07, sus tests cambian a propósito: la ruta de C06 ya se conoce, y el usuario aclaró que cambiarlos no es un problema. Alternativa descartada: b), un controller que exista solo en los tests (`GET /api/probe`, como turnos), que no cambia con C06 pero es una pieza más para mantener, cuando CA-08 y CA-09 igual necesitan las rutas reales. Turnos lo necesitaba para probar que el `sub` llegaba a sus casos de uso; catálogo todavía no lo lee.
  - **P6.3 · Las clases de test (2026-11-22): opción a), las variantes en un unitario con reloj fijo, y la aplicación completa solo para lo que necesita el servidor real**, con un contexto con la clave y otro sin ella (tabla de componentes y estrategia de validación). Es rápido y no depende del tiempo de la máquina. El test de las otras rutas se mueve de `ProfessionalCategoriesIntegrationTest` a `SecurityIntegrationTest`, porque prueba la seguridad y no C04. Alternativas descartadas: b), todo con la aplicación completa, que suma cinco arranques y un reloj controlable dentro de la aplicación sin probar nada que el unitario no pruebe, porque la cadena es la misma; y dejar el test de las otras rutas en C04, cambiando lo que espera.
  - **P6.4 · La prueba con un token real de turnos (2026-11-22): opción a), una prueba manual con los dos servicios en local, sin la cátedra**, que corre el usuario con la lista de pedidos y los comandos que se le pasan (etapa 5). Es la única forma de comprobar la restricción de la SPEC (los tokens de T02 se validan sin cambios) y la primera integración real entre los dos servicios. Deja un usuario de prueba en la base de desarrollo de turnos, que no tiene cómo borrarlo. No es un E2E: esos verifican solo su propio servicio (ver "E2E" en la estrategia de validación, pregunta del usuario al decidir). Alternativas descartadas: b), un test que arme un token imitando el formato de turnos, que escribe una misma persona de los dos lados y no prueba que coincidan; c), no probarlo y confiar en el contrato.
  - **P8 · Excepciones a la skill (2026-11-22): ninguna nueva**, aceptado por el usuario. No son excepciones: `SecurityProblemHandler`, `JwtDecoderConfig`, `JwtProperties`, `CorsProperties` y los cambios de `SecurityConfig`, en `catalogo.config`, dentro de la excepción de X01 (configuración transversal, sin lógica de negocio), como `SecurityConfig` y `ApiExceptionHandler` en C04; los `record` de propiedades, con el molde de `CatedraProperties` y `VerificationProperties`, que no son modelos del dominio; el lector del token, una técnica de Spring; `TestSecurityConfiguration` y los tests nuevos, apoyo de tests (la skill solo pide un test por mapper, y aquí no hay mappers). Ningún slice cambia ni importa la seguridad: no hay dependencias nuevas entre slices.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados, respeta la skill hexagonal-vertical-slicing y permite
demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
