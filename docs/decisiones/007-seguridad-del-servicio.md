# 007 — Seguridad del servicio: la validación del JWT, los rechazos, CORS y CSRF

- **Fecha:** 2026-11-22
- **Funcionalidad:** Validación del JWT en los endpoints protegidos (`docs/sdd/validacion-jwt/`: D1 a D7 de la SPEC y P1 a P8 del PLAN). Actualizada en C06 (`docs/sdd/c06-horarios-profesional/`, D4 y S3 de su SPEC), el 2026-11-22: la trazabilidad y dos limitaciones
- **Estado:** aceptada

## Situación

Catálogo tiene cuatro operaciones (contrato §1). C04 y C05 son públicas: las usa la app sin sesión. C06 la llama turnos en T03 y T04, reenviando el JWT que recibió de la app. C07 es del administrador. El JWT lo emite turnos (T02) y lo firma con su clave privada; catálogo solo lo valida, con la pública (contrato común §1.1 y §1.3).

El enunciado (§9 y §12) pide validar firma, vigencia y permisos del token recibido, proteger lo que no es expresamente público, proteger con JWT la comunicación entre los dos servicios, configurar CORS según los clientes permitidos y documentar las decisiones y limitaciones de seguridad. Este texto es esa descripción para catálogo. Los permisos por rol (el `403` de C07 y qué significa el `403` de C06) quedan para C07, por indicación del usuario.

Hasta esta funcionalidad, lo que no era público se rechazaba con un `403` sin cuerpo, porque no había forma de autenticarse (D1 de la SPEC de C04).

## Problema

Había que decidir:

- qué token se acepta y con qué clave;
- cómo siguen ignorando el token las rutas públicas, si el filtro de Spring lo lee en todas;
- qué responde un rechazo;
- qué pasa si la clave falta o no sirve;
- qué navegadores pueden usar la API, y qué hacer con CSRF;
- dónde vive todo esto, si ningún slice de catálogo es dueño de la identidad.

## Decisión

### 1. Qué es público y qué no

Una sola lista de rutas públicas, `PUBLIC_ENDPOINTS` en `config/SecurityConfig`: `GET /api/professional-categories` (C04) y `GET /api/professionals` (C05), exactas. Todo lo demás exige un JWT válido, también las rutas que todavía no existen: una ruta nueva nace protegida.

**Las rutas públicas ignoran `Authorization`, sea válido o no** (contrato común §1.4; C04 1.1 y C05 1.1). El filtro del resource server de Spring lee ese encabezado en todas las rutas y rechaza un token inválido aunque la ruta esté abierta. Por eso el lector del token no busca nada en las rutas de esa misma lista (P2): las dos cosas salen de un solo lugar y no pueden quedar desparejas. Alternativas descartadas: dos cadenas de seguridad, que repiten la configuración común; y sacar las rutas públicas de la seguridad (`web.ignoring()`), que las deja también fuera de CORS y sin los encabezados de seguridad.

### 2. Por qué C06 está protegido, y por qué viaja el JWT del usuario

C06 devuelve lo mismo que cada elemento de C05, que es público. Aun así está protegido, porque el enunciado pide que los dos servicios se comuniquen "mediante contratos explícitos y protegidos con JWT" (§3) y que se valide el JWT "en la comunicación protegida entre los dos servicios" (§9), y C06 es la única comunicación entre turnos y catálogo. El contrato común §1.6 ya reconoce que C06 no expone nada adicional. El usuario lo decidió al cerrar C05, sin cambiar documentos.

Entre los servicios viaja el JWT del usuario, no uno técnico (contrato común §1.5): catálogo recibe la misma identidad (`sub`) y los mismos roles que se decidieron al iniciar sesión, sin otra credencial que administrar.

### 3. Qué token se acepta

- Firmado con `RS256` con la clave pública de turnos. Uno `HS256`, sin firma o firmado con otra clave responde `401`.
- `exp` obligatorio y sin tolerancia de reloj (D2). Un token sin `exp` valdría para siempre, y la tolerancia que trae Spring (60 s) se aparta de "`exp` no vencido" (contrato común §1.4).
- `sub` obligatorio, solo dígitos del 0 al 9 y dentro del rango de un `Long`, el tipo del `id` en turnos (D1). Sin `sub` válido no hay identidad, y así el `sub` se puede escribir en un log tal como llega (lo hace C06: ver "La trazabilidad" en las limitaciones).
- `typ` como lo trae Spring: ausente o `JWT`. Los tokens de turnos traen `alg` y `kid`, sin `typ` (comprobado con un token real, TASK-08).
- No se leen `iat` ni `auth`.
- Sin estado: no consulta la base ni guarda tokens. Un token vale hasta su `exp`, también después de reiniciar, mientras no cambie el par de claves.

El decodificador es propio (`config/JwtDecoderConfig`, P3), no el que arma Spring Boot con su propiedad de clave pública, que no deja arrancar sin la clave ni con un archivo inválido (punto 5; visto en su código, sin ejecutarlo, en el PLAN).

### 4. Qué responde un rechazo

`config/SecurityProblemHandler` escribe el `401` y el `403` en `application/problem+json`, con los mismos campos que los demás errores del servicio (`title`, `status`, `detail` e `instance`), sin `code` y con un texto fijo por estado (D3). El `401` lleva `WWW-Authenticate: Bearer`, sin el motivo: todos los motivos dan el mismo cuerpo, así no se le cuenta a quien prueba tokens si el suyo venció, fue alterado o falta. El contrato común §1.4 permite omitir el `code`, y turnos y la app deciden por el estado.

Spring tiene dos lugares que responden un `401`: uno cuando falta el token y otro cuando es inválido. El manejador está en los dos, y en el del `403` (P4; comprobado con una prueba descartable). Un rechazo no escribe nada en el log: cada pedido sin token dejaría una línea, y no es un error del servicio.

### 5. La clave pública de turnos

- Llega como la ubicación de un archivo PEM, en `CATALOGO_JWT_PUBLIC_KEY_LOCATION` (D4). Catálogo lee el mismo `jwt-public.pem` que genera turnos, sin copiarlo, así la clave siempre corresponde. En seguridad no hay diferencia con poner la clave misma en el `.env`: es pública, lo que importa es que nadie la cambie, y en los dos casos la protegen los permisos del usuario en la máquina.
- Se lee al arrancar. Tiene que ser una clave pública RSA de 2048 bits o más.
- **Si falta o no sirve, el servicio arranca igual**, con un warning que dice qué pasó y la ubicación, y toda ruta protegida responde `401`. La sincronización, C04 y C05 no usan la clave y siguen funcionando: es el mismo criterio que ya usa catálogo con las credenciales de la cátedra. El warning nunca muestra el contenido del archivo, por si la variable apunta por error a la clave privada.
- En Docker se monta solo `jwt-public.pem`, en solo lectura, nunca la carpeta `secrets/` de turnos, que también tiene la privada (contrato común §1.3: la privada no se entrega al catálogo).

### 6. CORS

Solo los navegadores aplican CORS. Los clientes de catálogo (la app nativa, turnos y herramientas como Postman) no mandan `Origin`, y para ellos no cambia nada. Los orígenes permitidos van en `CATALOGO_CORS_ALLOWED_ORIGINS`, exactos y separados por comas, vacía por defecto: ningún navegador (D5 y P5). A un origen permitido se le permite `GET` con `Authorization`, sin cookies, y su consulta previa se responde sin pedir el token. La configuración se registra aunque la lista esté vacía, así un origen no permitido recibe siempre el rechazo estándar de CORS (`403`), también en C04 y C05. **No se usa `*`**, que habilitaría a cualquier página. Es la decisión 007 de turnos, ajustada a catálogo, que solo tiene `GET`. Alternativa descartada: no configurar nada, con lo que ningún navegador lee igual, pero como efecto de otras reglas y no de una decisión.

### 7. CSRF

Queda como viene (D6). En seguridad da lo mismo que desactivarlo: catálogo no tiene sesiones ni cookies, y la identidad viaja en `Authorization`, que el navegador nunca agrega solo. CSRF no se aplica a `GET`, así que no cambia nada de C04 a C07. Un `POST`, `PUT` o `DELETE` sin token, que el contrato no tiene en ninguna ruta, responde `403` con el formato del punto 4; con un token inválido, `401`. Turnos lo desactivó (su decisión 006) porque tiene `POST` sin JWT, el registro y el login; catálogo no. Alternativa descartada: desactivarlo, que daría la regla única "sin token, `401`", pero SonarQube lo marca como vulnerabilidad y hay que justificarlo como falso positivo.

### 8. Dónde vive

Todo en `catalogo.config` (P1), dentro de la excepción autorizada en X01 para la configuración transversal. En turnos la validación vive en el slice `user`, dueño de las claves y los roles. En catálogo ningún slice es dueño de la identidad, y las rutas protegidas son de dos slices (C06 en `catalog`, C07 en `synchronization`). Ningún slice importa la seguridad, y la seguridad no importa ningún slice. El controller de C06 lee el usuario con `java.security.Principal`, cuyo `getName()` es el `sub`, sin importar Spring Security.

## Limitaciones

- **Sin revocación:** el token es sin estado y vale hasta su `exp` (24 h en turnos). Cerrar sesión es descartarlo en la app (contrato común §1.6).
- **Cualquier portador de un token válido puede llamar a C06**, no solo turnos. Devuelve lo mismo que el C05 público (§1.6).
- **La ventana del vencimiento:** un token que vence entre que turnos lo valida y catálogo lo valida (milisegundos) hace fallar esa consulta de turnos, que la trata como error interno (T03 4.1); al reintentar, turnos responde `401` y la app pide iniciar sesión. Con relojes distintos en dos máquinas la ventana crece; en el despliegue previsto, los dos servicios comparten la máquina.
- **Sin clave, C06 responde `401` a todo**, y turnos lo ve como un error interno. La pista es el warning del arranque de catálogo.
- **El cambio de clave es manual:** se reemplaza el par en turnos y se reinician los dos servicios; los tokens emitidos con la clave anterior dejan de valer.
- **La trazabilidad queda a medias:** el contrato común §1.5 pide el mismo `sub` en los logs de los dos servicios. Catálogo lo escribe en C06: una línea `INFO` por cada consulta que pasó la seguridad, con el `sub`, el id y el resultado (`Professional 15 requested by user 42: found with 2 weekly schedules`), o, si el id es inválido, la del manejador de errores, sin el valor recibido (D4 de C06; comprobado con un token real de turnos en TASK-09 de C06). Los rechazos de la seguridad no dejan línea. Turnos no escribe el `sub` en los suyos; está terminado y no se cambió.
- **Una URL con caracteres que el servicio no acepta** (por ejemplo, un salto de línea o un `;` codificados) **termina en `401`**, con token o sin él y en cualquier ruta, también en C04 y C05: la seguridad la rechaza y pide un error `400`, el servidor lo reenvía a la página interna `/error`, que la seguridad también protege, y ese reenvío termina en el `401` del punto 4, con `instance` `/error`. Una barra codificada la rechaza antes Tomcat, con su `400` en HTML. No se exponen datos y ningún cliente manda esas URL, pero el mensaje ("A valid bearer token is required") confunde ante una URL mal formada (S3 de C06, comprobado con el servidor real; antes se había visto solo con `@WebMvcTest`, que no hace ese reenvío).
- **C06 no puede hacerse pública tal como está:** su controller necesita el usuario para la línea del log, y en una ruta pública no hay usuario, así que respondería `500` (lo mostró la prueba en negativo de TASK-08 de C06). Hacerla pública exigiría también cambiar esa línea.
- **Sin roles hasta C07:** hoy un token válido pasa con cualquier `auth`. En la ruta de C07, que todavía no existe, recibe lo mismo que en una ruta inexistente.
- **Sin TLS:** en el despliegue local todo va por HTTP, y el token viaja sin cifrar. En un despliegue real lo pondría un proxy delante de los servicios. Es una decisión de todo el sistema, que el usuario va a revisar al hacer la app KMP.

## Qué no cambia

- C04 y C05 responden igual que antes, con o sin `Authorization`. La única diferencia es para un navegador de otro origen: antes recibía `200`, que el navegador no le dejaba leer a la página, y ahora `403`.
- `ApiExceptionHandler` sigue sin ver los rechazos de la seguridad (decisión 005): los escribe `SecurityProblemHandler`, con la misma forma.
- La sincronización (C01 a C03) no pasa por la seguridad web.
- Turnos no cambió: sus tokens se validan tal como los emite (TASK-08).
