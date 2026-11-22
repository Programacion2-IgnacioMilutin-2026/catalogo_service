# TASKS: Validación del JWT en los endpoints protegidos

SPEC: `docs/sdd/validacion-jwt/SPEC.md` (aprobada el 2026-11-22). PLAN: `docs/sdd/validacion-jwt/PLAN.md` (aprobado el 2026-11-22, con el ajuste del orden que trajo este documento: el test de las otras rutas se mueve en la etapa 2). Tareas aprobadas por el usuario el 2026-11-22. La implementación sigue su autorización, tanda por tanda (tanda 1, TASK-01: autorizada el 2026-11-22, al aprobar las tareas).

Las tareas siguen el "Orden de implementación" del PLAN y cada una deja la suite en verde. Toda tarea con tests incluye, después de que pasan, una prueba en negativo: romper el código a propósito, ver fallar el test, restaurar el archivo y comprobar el hash. Los tests se cuentan con los reportes XML (`target/surefire-reports/TEST-*.xml`) después de `clean`; al cerrar C05 eran 630 tests en 76 clases.

Nada depende de la velocidad de la máquina: los bordes del vencimiento se prueban solo en el unitario, con un reloj fijo; las integraciones usan tokens con una hora de margen, y ningún test detiene ni pausa la base. Ninguna tarea se conecta a la cátedra real, a su Kafka ni a su Redis.

Los comandos de git los ejecuta el usuario. Al terminar cada tanda se le da, por cada tarea, su explicación de archivos, su mensaje de commit y su lista de archivos. Si dos tareas de una tanda tocan el mismo archivo, se frena entre una y otra para que commitee. `TASKS.md` va con la última tarea de cada tanda.

Tandas propuestas (cada una espera la orden del usuario): 1 = TASK-01; 2 = TASK-02 y TASK-03; 3 = TASK-04; 4 = TASK-05 y TASK-06; 5 = TASK-07 y TASK-08; 6 = TASK-09. TASK-01 y TASK-04 tocan `application.yaml` y `.env.example`, y TASK-02 y TASK-04 tocan `SecurityConfig`: están en tandas distintas, así que cada una se commitea antes de la siguiente.

## Etapa 1 · La clave y el decodificador

- [x] **TASK-01 · La clave pública, el decodificador y los tokens de prueba**
  - Alcance:
    - `config/JwtProperties` (`record`, `catalogo.jwt.public-key-location`; vacía o presente sin valor cuenta como no configurada).
    - `config/JwtDecoderConfig`: lee la clave al arrancar con `RsaKeyConverters.x509()` y la revisa (RSA, 2048 bits o más). Con clave, un decodificador `RS256` con el vencimiento sin tolerancia y el `Clock` del servicio, `exp` obligatorio y `sub` solo dígitos del 0 al 9 dentro del rango de un `Long`. Sin clave utilizable, un warning al arrancar (qué pasó y la ubicación, sin el contenido del archivo ni el mensaje de la excepción) y un decodificador que rechaza todo token (P3).
    - `application.yaml`: `catalogo.jwt.public-key-location: ${CATALOGO_JWT_PUBLIC_KEY_LOCATION:}`. `.env.example`: la variable vacía, con el aviso de que hay que poner la clave pública de turnos, sin la ruta.
    - `TestTokens` ampliado: variantes sin `exp`, sin `sub` o con uno dado, firmado con otro par, `HS256`, sin firma y con un `auth` dado; tokens respecto de un instante fijo; la clave pública en un PEM temporal; PEM de prueba de otro par, de 1024 bits y de una clave privada. `valid`, `expired` y `altered` siguen con su nombre (P6.1).
    - `config/JwtDecoderConfigTest` (nuevo, unitario, sin Spring y con un reloj fijo).
    - No se toca `SecurityConfig`: todavía nadie usa el decodificador.
  - Depende de: ninguna
  - Resuelve: CA-03, CA-04, CA-05 y CA-10 en el decodificador; CA-12 en sus variantes; CA-11 en el warning
  - Validación:
    - Línea base antes de tocar nada: `./mvnw -q clean test`, conteo en los XML.
    - `./mvnw test -Dtest=JwtDecoderConfigTest`:
      - CA-03: otro par, `HS256`, sin firma y contenido cambiado, rechazados;
      - CA-04: con el reloj fijo, el que venció 1 s antes, rechazado; el que vence 1 s después, aceptado; sin `exp`, rechazado;
      - CA-05: sin `sub`, `abc`, `-1`, `4 2`, `٤٢` y `9223372036854775808`, rechazados; `42`, aceptado;
      - CA-10: dos decodificadores armados desde el mismo archivo aceptan el token; uno con el PEM de otro par lo rechaza;
      - CA-12: sin propiedad, vacía, archivo inexistente, archivo que no es una clave, clave de 1024 bits y clave privada: el decodificador rechaza un token válido y el warning dice qué pasó, sin el contenido del PEM.
    - La suite completa sigue en verde (el bean existe en todos los contextos; sin clave, solo deja el warning).
    - Pruebas en negativo: la tolerancia por defecto de Spring (pasa el vencido hace 1 s); `exp` opcional (pasa el token sin `exp`); sin la validación del `sub`; el warning con el mensaje de la excepción en lugar del texto propio, si ese mensaje trae parte del contenido (si no lo trae, se anota).
  - Resultado: 2026-11-22 (tanda 1, autorizada ese día).
    - Creados `config/JwtProperties`, `config/JwtDecoderConfig` y `config/JwtDecoderConfigTest`. Modificados `application.yaml` (`catalogo.jwt.public-key-location`), `.env.example` (la variable vacía, con el aviso, sin la ruta) y `TestTokens`.
    - **Ajuste al hacerla:** `TestTokens` suma también un PEM de una clave pública de curva elíptica y un archivo que no es una clave, para los casos "no es RSA" y "no es un PEM" de CA-12. El decodificador arma sus validadores con `JwtValidators.createDefaultWithValidators`, que conserva la revisión de `typ` de Spring; eso corrigió una afirmación del PLAN sobre la opción de Spring Boot (ver "Hechos comprobados" del PLAN), sin cambiar P3.
    - Línea base, antes de tocar nada: `./mvnw -q clean test`, 630 tests en 76 clases, 0 fallos ni errores, 146 s.
    - `./mvnw test -Dtest=JwtDecoderConfigTest`: 31 tests, 0 fallos ni errores.
      - CA-03: otro par RSA, `HS256`, sin firma y el contenido cambiado, rechazados.
      - CA-04, con el reloj fijo en 2026-11-22T12:00:00Z: el que venció 1 s antes, rechazado; el que vence 1 s después, aceptado. Sin `exp`, rechazado.
      - CA-05: sin `sub`, y con `""`, `abc`, `-1`, `+42`, `4 2`, ` 42`, `٤٢`, `9223372036854775808` y `00000000000000000042` (20 dígitos), rechazados; `42`, `0` y `9223372036854775807`, aceptados.
      - CA-10: dos decodificadores armados desde el mismo archivo aceptan el mismo token; uno con el PEM de otro par lo rechaza.
      - CA-12 y CA-11: sin propiedad, vacía, en blanco, archivo inexistente, archivo que no es una clave, clave privada, clave de curva elíptica y RSA de 1024 bits: el decodificador rechaza un token válido. El log muestra un warning por caso, con la ubicación y lo que pasó ("is not configured", "could not be read", "is not an RSA public key in PEM format", "is shorter than 2048 bits"), sin `BEGIN` ni ninguna línea del archivo. Con la clave válida no hay warning.
    - Prueba en negativo 1: la tolerancia por defecto de Spring (`new JwtTimestampValidator()`), y falló solo el del vencido hace 1 s.
    - Prueba en negativo 2: sin `setAllowEmptyExpiryClaim(false)`, y falló solo el del token sin `exp`: Spring lo acepta por defecto.
    - Prueba en negativo 3: sin el validador del `sub`, y fallaron los 10 casos del `sub` (los 9 inválidos y el que no lo trae).
    - Prueba en negativo 4: el warning con el mensaje de la excepción, y fallaron 2 casos: el archivo que no es una clave y la clave privada. **El mensaje de Spring no trae el contenido del archivo**, pero sí la palabra `BEGIN` en su sugerencia ("check that the header begins with -----BEGIN PUBLIC KEY"), y eso lo detecta. En la clave de curva elíptica el mensaje ("Invalid RSA public key") no tiene ninguna de las dos cosas, así que ese caso no lo detecta.
    - Restaurado `JwtDecoderConfig`: `sha256sum -c` coincide, y `JwtDecoderConfigTest` vuelve a 31 en verde.
    - `./mvnw -q clean test`: 661 tests en 77 clases, 0 fallos ni errores, 149 s. El warning "is not configured" aparece 26 veces en la salida: uno por cada contexto que arranca la aplicación sin clave, más los del unitario. Es lo esperado hasta TASK-03.

## Etapa 2 · La cadena

- [ ] **TASK-02 · La cadena valida el token, ignora las rutas públicas y escribe los rechazos**
  - Alcance:
    - `config/SecurityProblemHandler` (nuevo): punto de entrada y manejador de acceso denegado; `ProblemDetail` en `application/problem+json` con `title`, `status`, un `detail` fijo por estado e `instance`, sin `code`; el `401` con `WWW-Authenticate: Bearer`; nada al log (P4).
    - `config/SecurityConfig`: el resource server con el decodificador de TASK-01; el lector del token que no busca nada en `PUBLIC_ENDPOINTS` y delega en `DefaultBearerTokenResolver` en las demás rutas (P2); `SecurityProblemHandler` en los dos lugares del `401` y en el del `403`; `authenticated()` para lo demás, sin roles; CSRF como viene.
    - `TestSecurityConfiguration` (nuevo, en `src/test/java/catalogo/config/`): la seguridad real con la clave de test, para los `@WebMvcTest`. Si el `@ConfigurationPropertiesScan` no registra `JwtProperties` en un `@WebMvcTest`, la habilita de forma explícita (riesgo del PLAN).
    - `ProfessionalCategoryControllerTest` y `ProfessionalControllerTest`: importan `TestSecurityConfiguration` en lugar de `SecurityConfig`.
    - `config/ProtectedRoutesSecurityTest` (nuevo, `@WebMvcTest` contra las rutas reales).
    - `anyOtherRouteWithoutAuthenticationIsRejectedWithoutData` sale de `ProfessionalCategoriesIntegrationTest` y pasa a `config/SecurityIntegrationTest` (nuevo), con los `GET` en `401` y los `POST` en `403`, los dos en `problem+json` sin datos (ajuste del orden del PLAN).
    - No se toca CORS ni `TestcontainersConfiguration`.
  - Depende de: TASK-01
  - Resuelve: CA-01, CA-02, CA-06 (web), CA-07 (en los `@WebMvcTest`), CA-08, CA-09 y CA-14 (web y servidor real sin token)
  - Validación:
    - `./mvnw test -Dtest='ProtectedRoutesSecurityTest,ProfessionalCategoryControllerTest,ProfessionalControllerTest,SecurityIntegrationTest,ProfessionalCategoriesIntegrationTest'`:
      - CA-01: sin `Authorization`, `401` `problem+json` sin datos en `/api/professionals/15` y en una ruta inexistente;
      - CA-02: `Basic abc`, `Bearer`, `Bearer ` y `Bearer xxx`, `401`;
      - CA-03 y CA-05 en la web: un token de otro par y uno con `sub` `abc`, `401`;
      - CA-06: los cuerpos de esos `401` son idénticos, sin `code`, y llevan `WWW-Authenticate: Bearer` sin motivo;
      - CA-07: las cinco variantes de C04 y C05, con el token válido de verdad, responden igual que sin encabezado;
      - CA-08: tokens válidos sin `auth`, con `auth` vacío, `ROLE_USER` y `ROLE_ADMIN`, en las tres rutas: `404` `problem+json`;
      - CA-09: `/api/professionals/abc` sin token, `401`; con token, `404`;
      - CA-14: `POST` sin token, `403` `problem+json`; con uno inválido, `401`; con uno válido, `405`;
      - el test movido, con el servidor real: los `GET` en `401` y los `POST` en `403`.
    - `./mvnw clean test` (se movió un test): la suite completa en verde y el conteo en los XML.
    - Pruebas en negativo: el resource server sin el lector propio (fallan las variantes de CA-07: un token inválido da `401` en una ruta pública); el manejador solo en el lugar general (falla CA-06: el `401` de un token inválido sale sin el cuerpo).
  - Resultado: pendiente

- [ ] **TASK-03 · La clave de test en todos los contextos**
  - Alcance:
    - `TestcontainersConfiguration` registra `catalogo.jwt.public-key-location` con la clave de `TestTokens` (P6.1).
    - No se toca código de producción.
  - Depende de: TASK-02
  - Resuelve: CA-07 en la aplicación completa (el token alterado de `ProfessionalsIntegrationTest` frente a una clave real)
  - Validación:
    - `./mvnw clean test`: la suite completa en verde, porque cambia la configuración de 36 clases, y el conteo en los XML.
    - En el log de la suite, el warning de la clave aparece solo en los contextos que no importan `TestcontainersConfiguration` (por ejemplo, el de la base detenida de C04).
    - Prueba en negativo: sin registrar la propiedad, el warning aparece en todos los contextos que importan `TestcontainersConfiguration`.
  - Resultado: pendiente

## Etapa 3 · CORS

- [ ] **TASK-04 · Los navegadores permitidos**
  - Alcance:
    - `config/CorsProperties` (`record`, `catalogo.cors.allowed-origins`, lista vacía por defecto).
    - `config/SecurityConfig`: la configuración CORS, registrada siempre: los orígenes de `CorsProperties`, `GET`, `Authorization`, sin cookies ni encabezados expuestos (P5).
    - `application.yaml`: `catalogo.cors.allowed-origins: ${CATALOGO_CORS_ALLOWED_ORIGINS:}`. `.env.example`: la variable comentada, con el aviso de no usar `*`.
    - `TestSecurityConfiguration`, si hace falta habilitar `CorsProperties`.
    - `config/CorsSecurityTest` (nuevo, `@WebMvcTest`, sin orígenes y con uno de prueba).
  - Depende de: TASK-02
  - Resuelve: CA-13
  - Validación:
    - `./mvnw test -Dtest=CorsSecurityTest`:
      - sin orígenes: la consulta previa y un `GET` con `Origin` a C04, a C05 y a una ruta protegida, `403` sin `Access-Control-Allow-Origin`;
      - con `http://localhost:3000`: la consulta previa sin token a una ruta protegida, aceptada con `Access-Control-Allow-Origin` igual al origen y `Authorization` permitido; el `GET` sin token, `401` con ese encabezado; el `GET` a C05, como siempre, con el encabezado; la consulta previa que pide `POST`, y la de otro origen, rechazadas;
      - sin `Origin`, las mismas respuestas en las dos configuraciones.
    - Los tests web de TASK-02 siguen en verde.
    - Prueba en negativo: la configuración CORS sin registrar con la lista vacía (la consulta previa deja de dar el `403` de CORS).
  - Resultado: pendiente

## Etapa 4 · La aplicación completa

- [ ] **TASK-05 · La seguridad con el servidor real**
  - Alcance:
    - `config/SecurityIntegrationTest` se amplía: los `401` con el servidor real (incluido el reenvío interno a `/error`, que `@WebMvcTest` no hace); C04 y C05 con el token válido de verdad y las demás variantes; un `POST` con un token inválido y con uno válido; y la salida capturada.
    - No se toca código de producción.
  - Depende de: TASK-03
  - Resuelve: CA-06, CA-07, CA-11 y CA-14 con el servidor real
  - Validación:
    - `./mvnw test -Dtest=SecurityIntegrationTest`:
      - CA-06: los `401` (sin token, `Bearer xxx`, vencido y alterado) tienen cuerpos idénticos y `WWW-Authenticate: Bearer`;
      - CA-07: C04 y C05 con la copia inicializada responden igual con cada variante que sin encabezado;
      - CA-14: `POST` con un token inválido, `401`; con uno válido, `405`;
      - CA-11: ni la salida ni los cuerpos contienen los tokens usados.
    - Prueba en negativo: el manejador solo en el lugar general (falla el cuerpo del `401` de un token inválido también con el servidor real).
  - Resultado: pendiente

- [ ] **TASK-06 · El servicio sin clave**
  - Alcance:
    - `config/SecurityWithoutKeyIntegrationTest` (nuevo, un solo método, con la propiedad vacía).
    - No se toca código de producción.
  - Depende de: TASK-03
  - Resuelve: CA-12 de punta a punta
  - Validación:
    - `./mvnw test -Dtest=SecurityWithoutKeyIntegrationTest`: el servicio arranca; el log del arranque tiene el warning de la clave, sin contenido; un token firmado con el par de test recibe `401` en `/api/professionals/15`; C04 responde como siempre.
    - Prueba en negativo: con la propiedad apuntando a la clave de test, el mismo token pasa y el warning no aparece.
  - Resultado: pendiente

## Etapa 5 · Regresión y prueba con turnos

- [ ] **TASK-07 · La suite completa**
  - Alcance: sin cambios de código; regresión y cobertura.
  - Depende de: TASK-05, TASK-06
  - Resuelve: la regresión del PLAN
  - Validación:
    - `./mvnw clean verify`: conteo en los XML y cobertura de JaCoCo de las clases nuevas.
    - Los tres E2E siguen compilando y se saltean sin credenciales: `./mvnw test -Pe2e -Dtests.env-file=<archivo inexistente>`.
    - Los cinco chequeos de la skill (`SKILL.md` §12), con la salida esperada.
  - Resultado: pendiente

- [ ] **TASK-08 · La prueba con un token real de turnos (la corre el usuario)**
  - Alcance: prueba manual con turnos y catálogo en local, sin la cátedra (P6.4). Se le pasa al usuario la lista de pedidos y los comandos, con las variables de la cátedra vacías en los dos servicios y sin Kafka ni tareas programadas; los nombres exactos de las variables de turnos se confirman en su `application.yaml` antes de pasarlos. Deja un usuario de prueba en la base de desarrollo de turnos. Al terminar, todo queda como estaba (aplicaciones detenidas, compose en su estado previo).
  - Depende de: TASK-07
  - Resuelve: la restricción de la SPEC (los tokens de T02 se validan sin cambios en turnos)
  - Validación, con `curl` y sin mostrar el token:
    - en turnos, registrar el usuario de prueba e iniciar sesión;
    - con ese token, `GET /api/professionals/15` de catálogo: `404` `problem+json` (pasó la seguridad);
    - sin token y con el token alterado: `401`;
    - `GET /api/professionals` con y sin token: la misma respuesta;
    - el log de catálogo, sin el token y sin el warning de la clave.
  - Resultado: pendiente

## Etapa 6 · Cierre

- [ ] **TASK-09 · Documentación de cierre**
  - Alcance:
    - `docs/decisiones/007-seguridad-del-servicio.md` (nuevo): las decisiones y limitaciones de RF-13, con por qué C06 está protegido.
    - `docs/decisiones/005-manejador-global-de-errores.md`: la línea de "Qué no hace" sobre los rechazos de la seguridad (P7).
    - `CLAUDE.md`: la sección "Seguridad" reescrita, las dos variables en Requisitos y en la tabla de propiedades, lo nuevo de los tests (`TestTokens`, `TestSecurityConfiguration`, las clases de seguridad, la clave en `TestcontainersConfiguration`) y la `007` en "Documentación del servicio".
    - `TASKS.md` con los resultados.
  - Depende de: TASK-08
  - Resuelve: CA-15
  - Validación: revisar el texto `007` contra la lista de RF-13 y contra lo observado en CA-01 a CA-14; ninguna afirmación sin evidencia en este documento.
  - Resultado: pendiente
