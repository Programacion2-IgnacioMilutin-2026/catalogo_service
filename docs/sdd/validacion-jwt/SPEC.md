# SPEC: Validación del JWT en los endpoints protegidos

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

<!-- PARA LA PERSONA
Copia esta plantilla como SPEC.md en una carpeta de la funcionalidad.
Pide al agente que la complete contigo usando BACKEND_GUIDELINES.md.
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución.
Los casos de uso, diagramas, contratos y el anexo de la cátedra se referencian
por ID; esta SPEC no los reescribe.
-->

<!-- PARA EL AGENTE
- Lee las instrucciones del proyecto, BACKEND_GUIDELINES.md y los documentos
  citados en Referencias (casos de uso, diagramas, contratos, modelo de dominio
  y anexo de la cátedra). Inspecciona el repositorio para comprobar el
  comportamiento actual. Si falta un documento, pide su ubicación.
- Los casos de uso y los contratos son la fuente de verdad. No los reescribas ni
  los contradigas: referencia sus pasos y flujos alternativos por ID. Si esta
  spec y un documento de referencia difieren, o este es ambiguo, detente y consulta.
- Completa esta spec con la persona: investiga lo comprobable y consulta las
  decisiones pendientes. Haz pocas preguntas por vez y actualiza las respuestas.
- No inventes requisitos ni exclusiones. Distingue propuestas de decisiones
  confirmadas y marca como PENDIENTE lo que aún no esté resuelto.
- Aplica las consideraciones de backend relevantes sin ampliar el alcance automáticamente.
- No incluyas diseño de clases, tablas, componentes, archivos o algoritmos:
  esos detalles pertenecen a PLAN.md. Sí registra restricciones explícitas del pedido.
- Mantén el documento breve y proporcional a la funcionalidad. Conserva los comentarios.
- Un documento completo no está aprobado automáticamente. Solicita aprobación
  antes de marcarlo como Aprobada. No implementes durante esta etapa.
-->

## Referencias

<!-- Documentos de Documentacion en los que se apoya esta funcionalidad. Indica
qué partes cubre esta spec, con IDs exactos de archivo, caso de uso, paso y flujo
alternativo. Una funcionalidad puede cubrir solo una parte de un caso de uso: lo
que no se nombra aquí no forma parte de ella. -->

Esta funcionalidad no es un caso de uso: cubre la parte común de seguridad de C06 y de las rutas que no son públicas. Su carpeta, `docs/sdd/validacion-jwt/`, no lleva id de caso de uso: es una excepción a la convención `docs/sdd/<id>-<nombre-corto>/`, por indicación del usuario. Todo lo que depende de los roles queda para C07, también por indicación del usuario (ver "Fuera de alcance").

| Documento | Partes cubiertas |
| --- | --- |
| Contrato común (`docs/contratos/contratos_comunes.md`) | §1.1 (catálogo solo valida, nunca emite), §1.2 (`RS256`, `Authorization: Bearer`, claims `sub` y `exp`; `auth` no se lee aquí), §1.3 (la clave pública de turnos en la configuración externa), §1.4 (las tres primeras filas de la primera tabla; de la segunda, las filas de C04, C05 y C06 sin la parte de roles; los tres puntos finales), §1.5 y §1.6 (para la documentación, RF-13, y el token sin estado, RF-09) y §2 (formato de errores) |
| Contrato de catálogo (`docs/contratos/catalogo_y_sincronizacion_contrato.md`) | §1 (columna "Acceso" de C04, C05 y C06) y §3.1 (la fila del `401`; la del `403` queda fuera) |
| Casos de uso (`docs/casos_de_uso/catalogo_y_sincronizacion_usecases.md`) | C06: paso 2 y alternativo 2.1, solo firma y vigencia (el `401`), y el orden de los pasos 2 y 3 (RF-14). C04 y C05: alternativo 1.1 (siguen ignorando el JWT) |
| Diagrama de secuencia (`docs/secuencias/catalogo_y_sincronizacion_secuencia.md`) | §3, la nota de la rama `public query` ("any JWT sent is ignored"). C06 no se dibuja en catálogo: aparece en las secuencias de T03 y T04 de turnos, con el JWT del usuario |
| Enunciado (`documentacion/enunciado/PROJECT_STATEMENT-v1.md`) | §3 (contratos entre servicios protegidos con JWT), §9 (validar firma y vigencia, proteger lo que no es expresamente público, externalizar secretos, CORS y documentar las decisiones y limitaciones de seguridad) y §12 (descripción de las decisiones de seguridad) |
| SDD de C04 y C05, y decisión 005 | Lo que dejaron para esta funcionalidad: D1, D5 y "Para la validación del JWT" de la SPEC de C04; RF-12 y D10 de la SPEC de C05; los rechazos de la seguridad fuera de `ApiExceptionHandler` (decisión 005, "Qué no hace") |
| Turnos (`turnos_service`: `docs/sdd/validacion-jwt/`, decisiones 006, 007 y 008) | Solo como antecedente de cómo validó el token, ignoró el token en sus rutas públicas, dio formato a los rechazos y resolvió CSRF y CORS. No es una fuente de verdad para catálogo |
| Modelo de dominio / anexo de la cátedra | No aplica: la validación no usa la copia local ni la cátedra |

## Qué construimos y para quién

<!-- Qué necesidad cubre, qué actor la inicia (la app KMP, otro servicio, el
tiempo o la cátedra) y qué podrá hacer o recibir. Describe el objetivo en
lenguaje de negocio. Resume y referencia: no copies la descripción del caso de uso. -->

Catálogo solo debe atender sus rutas protegidas a quien presenta un JWT emitido por turnos (T02) y vigente. La primera es C06, que turnos llama reenviando el token del usuario (contrato §3.1). Esta funcionalidad agrega esa validación, con la clave pública de turnos, antes de que exista C06, y conserva públicas C04 y C05 sin mirar el token. Además define cómo se rechaza un pedido, cómo llega la clave por configuración, qué navegadores pueden usar la API (CORS) y deja documentadas las decisiones y limitaciones de seguridad que pide el enunciado.

## Situación actual

<!-- Qué existe hoy y es relevante (código, datos, otras funcionalidades ya
implementadas), la limitación que queremos resolver y los comportamientos
existentes que deben conservarse. Si todavía no hay código para esto, indícalo.
No describas la arquitectura. -->

- Están implementados X01 y C01 a C05. Al cerrar C05, la suite tenía 630 tests en 76 clases, en verde.
- La seguridad es la mínima de C04 (D1 de su SPEC): sin sesión, y una sola lista de rutas públicas, `GET /api/professional-categories` y `GET /api/professionals`, las dos exactas. Todo lo demás exige autenticación, pero no hay forma de autenticarse: responde `403` sin cuerpo (comprobado con el servidor real en TASK-06 y TASK-11 de C04). El contrato pide `401` cuando falta el token.
- Nadie lee el encabezado `Authorization`. CA-05 de C04 y CA-11 de C05 comprueban, con cinco variantes, que las rutas públicas lo ignoran. La variante "bien firmado" no está firmada con una clave que la aplicación conozca, porque la aplicación todavía no tiene ninguna.
- Un test revisa que otras cuatro rutas se rechacen con `403` sin datos: `GET /api/professionals/15`, `POST /api/professionals`, `GET /api/synchronization/status` y `POST /api/professional-categories`.
- Los errores de los controllers salen en `application/problem+json`. Los rechazos de la seguridad no pasan por ahí (decisión 005) y no tienen formato.
- No hay clave pública configurada ni configuración de CORS. CSRF queda como viene por defecto.
- Las dependencias del *resource server* de Spring Security ya están en el proyecto. Los tests tienen un generador de tokens de prueba con su propia clave, generada en cada corrida.
- **Deducido sin ejecutar** (código de Spring Security 7.1.2): con la validación del token activa, la protección CSRF exime solo a los pedidos que traen `Bearer`. Un `POST` sin token lo seguiría rechazando CSRF con `403`, antes de la autenticación (D6). Se comprueba en el PLAN.
- Debe conservarse el comportamiento de X01 y de C01 a C05, en especial que C04 y C05 ignoren `Authorization`.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. Cada requisito indica el paso o flujo
alternativo del que proviene. No agregues requisitos que los documentos de
referencia no tengan sin consultarlo. -->

- **RF-01** · Contrato común §1.4 (segunda tabla), contrato §1, enunciado §9: toda ruta distinta de `GET /api/professional-categories` y `GET /api/professionals` exige un JWT válido en `Authorization: Bearer <jwt>`, incluidas las que todavía no existen (C06, C07 y cualquier otra). Con un token válido, el pedido sigue hacia la ruta, tenga el `auth` que tenga.
- **RF-02** · Contrato común §1.4 (fila 1), C06 2.1: sin el encabezado, con otro esquema distinto de `Bearer`, o con un token que no es un JWT bien formado, responde `401`.
- **RF-03** · Contrato común §1.2 y §1.4 (fila 2), C06 paso 2: si la firma no se verifica con la clave pública de turnos, o el token no usa `RS256` (por ejemplo, `HS256` o sin firma), responde `401`.
- **RF-04** · Contrato común §1.4 (fila 3), C06 paso 2: si el `exp` venció, responde `401`, sin tolerancia de reloj: un token vencido hace un segundo ya se rechaza. Un token sin `exp` también responde `401`, porque valdría para siempre (D2).
- **RF-05** · Contrato común §1.2 y §1.4 ("la identidad sale siempre de `sub`"): un token bien firmado y vigente sin `sub`, o con un `sub` que no es un número escrito solo con dígitos (sin signo ni espacios, y no más largo que un `id` de turnos; el límite exacto va en el PLAN), responde `401`: sin `sub` válido no hay identidad. Así el `sub` se puede escribir en un log tal como llega (D1).
- **RF-06** · Contrato común §1.4 (último punto) y §2, C06 2.1: el `401` usa `application/problem+json`, con los mismos campos que los demás errores del servicio, sin `code` y sin datos del recurso ni del token. Todos los motivos dan el mismo cuerpo, así no se revela si el token venció, fue alterado o falta, y el encabezado `WWW-Authenticate: Bearer` no lleva el motivo. Si la seguridad responde un `403` (D6), cumple lo mismo con su estado (D3).
- **RF-07** · Contrato común §1.4 (primer punto), C04 1.1 y C05 1.1: las rutas públicas ignoran `Authorization`, sea válido, inválido o vencido, y responden lo mismo que sin él.
- **RF-08** · Contrato común §1.3: la clave pública de turnos llega por la configuración externa de catálogo, como la ruta a su archivo PEM (X.509) en una variable del `.env`. Catálogo lee el mismo archivo que genera turnos (`turnos_service/secrets/jwt-public.pem`), sin copiar la clave; la lee al arrancar, así que un cambio de clave exige reiniciar (contrato común §1.6). El nombre de la variable va en el PLAN (D4). Si falta o no es válida (no se puede leer, no es una clave pública RSA o tiene menos de 2048 bits), el servicio arranca igual, lo avisa con un warning, y toda ruta protegida responde `401`, porque sin clave no se puede verificar ninguna firma. La sincronización y las rutas públicas siguen funcionando (D4).
- **RF-09** · Contrato común §1.6: la validación no guarda estado: no registra tokens, no consulta revocaciones ni la base. Un token vale hasta su `exp`, también después de reiniciar el servicio, mientras no cambie el par de claves.
- **RF-10** · Guía de backend (privacidad), contrato común §1.3: el token recibido no aparece en logs ni en respuestas de error. Tampoco el contenido del archivo de la clave: si la variable apunta por error a la clave privada de turnos, se rechaza como inválida (RF-08) y el warning no la muestra (D4).
- **RF-11** · Enunciado §9 ("configurar CORS de acuerdo con los clientes permitidos"): los orígenes de navegador permitidos se leen de una variable del `.env` y, si no se configuran, no se permite ninguno, porque ningún cliente previsto es un navegador. A un origen permitido se le permite `GET` con el encabezado `Authorization`, sin cookies; su consulta previa (*preflight*) se responde sin pedir el JWT, y las respuestas, incluido el `401`, llevan los encabezados CORS para que el navegador pueda leerlas. Un origen no permitido recibe el rechazo estándar de CORS (`403`), también en C04 y C05. Los pedidos sin encabezado `Origin`, como los de la app, turnos o Postman, no cambian (D5).
- **RF-12** · Contrato común §1.4: la protección CSRF queda como viene. No se aplica a `GET`, `HEAD` ni `OPTIONS`, así que no cambia nada de C04 a C07. Un `POST`, `PUT` o `DELETE` sin token responde `403`, con el formato de RF-06; con un token inválido, `401`; con uno válido, lo que responde un método no permitido o una ruta inexistente. El contrato no tiene ninguna ruta con esos métodos (D6).
- **RF-13** · Enunciado §9 ("documentar las decisiones y limitaciones de seguridad") y §12: quedan documentadas las decisiones de seguridad del servicio y sus limitaciones (D7):
  - Decisiones: qué es público y qué protegido, y por qué en las rutas públicas se ignora el token; por qué C06 está protegido aunque devuelva lo mismo que C05 (enunciado §3 y §9), y por qué se propaga el JWT del usuario (contrato común §1.5); cómo se valida (D1, D2 y RF-09); la clave, de dónde sale, cómo se configura, qué pasa si falta y qué se monta en Docker (D4); los rechazos (D3); CORS y CSRF, y por qué CSRF se resolvió distinto que en turnos (D5 y D6).
  - Limitaciones: sin revocación, un token vale hasta su `exp`; cualquier portador de un token válido puede llamar a C06 (contrato común §1.6); la ventana de D2; sin clave, C06 responde `401` y turnos lo ve como un error interno (D4); el cambio de clave es manual y exige reiniciar los dos servicios; la trazabilidad queda a medias, porque turnos no escribe el `sub` en sus logs; sin roles hasta C07; y sin TLS, todo va por HTTP y el token viaja sin cifrar.
- **RF-14** · C06 pasos 2 y 3: la validación del token va antes que la de los parámetros. Un pedido a una ruta protegida sin token válido y con un parámetro inválido responde `401`, no `400`.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Incluye las partes de los
casos de uso que se dejan para otra funcionalidad. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Confirmado por el usuario (2026-11-21):

- **Todo lo que depende de los roles va con C07:** leer el claim `auth`, el `403` de C07 2.2 y qué significa el `403` "token válido sin permisos" de C06 (contrato §3.1). Hasta entonces, un token válido pasa la validación sea cual sea su `auth`, y C06 lo aceptaría igual. En la ruta de C07, que todavía no tiene endpoint, un token válido recibe lo mismo que en cualquier ruta inexistente.

Confirmadas por el usuario al aprobar la SPEC (2026-11-22):

- Los endpoints de C06 y C07 y el resto de sus pasos.
- Registrar el `sub` en el log de C06: el usuario quiere que quede en los logs (D1); se hace en la SPEC de C06, donde está la línea del log. Esta funcionalidad garantiza que el `sub` se pueda escribir tal como llega.
- Revocación, renovación y cierre de sesión en el backend (contrato común §1.6): no existen.
- Rotación de las claves: es manual (contrato común §1.6).
- TLS entre la app, los servicios y la cátedra: no se implementa; queda como limitación documentada (D7). El usuario lo va a revisar al hacer la app KMP.
- Cambios en turnos. Hallazgo: turnos no escribe el `sub` en sus logs, así que la trazabilidad del contrato común §1.5 ("el mismo `sub` en los logs de ambos servicios") no se cumple de su lado. Turnos está terminado: se documenta como limitación (D7) y no se toca sin el OK del usuario.
- Cómo llega la clave dentro de Docker: es de la etapa de Docker. Aquí solo la configuración externa (D4). Queda anotado para esa etapa: se monta solo `jwt-public.pem`, en solo lectura, nunca la carpeta `secrets/` de turnos, que también tiene la clave privada (contrato común §1.3: la privada no se entrega al catálogo).

## Flujo de la operación

<!-- Cómo se inicia (request, evento o tarea programada), qué verifica y qué
resultado produce, incluidas las alternativas relevantes. Si el caso de uso ya
lo describe, referencia sus pasos en lugar de repetirlos y describe solo lo que
cubre esta funcionalidad. -->

1. Llega un pedido a una ruta que no es pública. Turnos, en C06, lo manda con el JWT que recibió de la app (C06 paso 1).
2. Si viene de un navegador (trae `Origin`), antes se aplica CORS (RF-11): un origen no permitido se rechaza, y la consulta previa de uno permitido se responde sin pedir el JWT.
3. Un `POST`, `PUT` o `DELETE` sin token lo rechaza CSRF, venga de donde venga (RF-12).
4. Antes de que actúe cualquier controller, el servicio aplica las verificaciones del contrato común §1.4, en su orden: token presente y bien formado, firma y `exp` (C06 paso 2), y además un `sub` válido (D1).
5. Si alguna falla, responde `401` sin datos y la ruta no se ejecuta (C06 2.1).
6. Si todas pasan, el pedido sigue hacia la ruta. Hoy no hay ninguna ruta protegida implementada, así que termina como una ruta inexistente; desde C06, en su controller.
7. En `GET /api/professional-categories` y `GET /api/professionals` no se busca el token: el encabezado se ignora (RF-07).

## Datos y reglas de negocio

<!-- Información que necesita la operación, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- El token tiene el formato del contrato común §1.2. Esta funcionalidad usa la firma (`RS256`), `exp` (obligatorio, D2) y `sub` (obligatorio y solo dígitos, D1). `iat` y `auth` no se validan: `auth` va con C07.
- `sub` es el `id` del usuario de turnos como texto. Catálogo no lo usa para autorizar: no tiene datos de los usuarios.
- La clave es la pública del par RSA de turnos (contrato común §1.3). Catálogo nunca tiene la privada.
- La validación va antes que la de los parámetros (RF-14).
- No se guarda nada: ni tokens ni intentos rechazados (RF-09).

## Casos alternativos y de error

<!-- Adapta la tabla usando BACKEND_GUIDELINES.md. Cuando el caso de uso ya define
el flujo alternativo, referencia su ID en lugar de repetirlo. Añade escenarios
relevantes. Marca No aplica con su motivo cuando corresponda. Expresa resultados
observables (estado HTTP, código de error, estado final del recurso, evento
publicado), no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Entrada inválida | Sin encabezado, con otro esquema, con `Bearer` vacío o con algo que no es un JWT: `401` (RF-02). Un `sub` ausente o que no es solo dígitos: `401` (RF-05). Con un parámetro inválido y sin token válido: `401` (RF-14) |
| Sin sesión o sin permiso | Firma inválida, otro algoritmo, `exp` vencido, sin tolerancia, o sin `exp`: `401` (RF-03, RF-04). Los permisos por rol van con C07 |
| Recurso inexistente o ajeno | Ruta inexistente sin token válido: `401`; con token válido, lo que hoy responde una ruta inexistente. No hay recursos ajenos: catálogo no tiene datos de los usuarios |
| Ruta pública con `Authorization` | Se ignora, sea válido, inválido o vencido (RF-07) |
| Método que cambia datos sin token | `403` de CSRF, con el formato de RF-06; con un token inválido, `401` (RF-12). Ninguna operación del contrato usa esos métodos |
| Request desde un navegador | Origen no permitido, o ninguno configurado: rechazado con `403`, también en C04 y C05. Origen permitido: consulta previa sin JWT y respuestas con los encabezados CORS (RF-11) |
| Configuración faltante o inválida | Clave pública ausente o inválida: el servicio arranca con un warning, toda ruta protegida responde `401`, y la sincronización y las rutas públicas siguen como siempre (RF-08) |
| Dependencia no disponible (cátedra, turnos, Redis, Kafka) | No aplica: la validación usa solo la clave pública, sin llamadas externas. Tampoco depende de que turnos esté levantado |
| Timeout o espera excesiva | No aplica: no hay llamadas externas |
| Operación o mensaje repetido | No aplica: validar no modifica nada; el mismo token sirve en muchos pedidos hasta su `exp` |
| Evento tardío o fuera de orden | No aplica: no hay eventos |
| Falla de persistencia | No aplica: no se lee ni se escribe la base |
| Solicitudes simultáneas | No aplica: cada pedido se valida por separado |
| Reinicio con operaciones pendientes | Los tokens emitidos antes siguen valiendo hasta su `exp` si no cambió el par de claves; con un par nuevo, `401` (RF-09) |

**Puntos de la guía no aplicables y motivo:** dependencias externas, idempotencia, transacciones, estados, tareas programadas, persistencia y paginación: la validación no llama a otros sistemas, no guarda datos ni consume mensajes.

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de seguridad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Respetar el contrato de Documentacion y el anexo de la cátedra puede ser
una restricción; el diseño de los adaptadores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Respetar el contrato común §1 y §2, y el contrato de catálogo §1 y §3.1. Si una decisión exige cambiarlos, se avisa antes y se cambian las tres copias de `contratos_comunes.md` a la vez.
- Los tokens que ya emite turnos (T02) tienen que validarse sin cambios en turnos.
- Sin roles: nada que dependa de `auth` (indicación del usuario).
- No cambiar el comportamiento de X01 ni de C01 a C05, con una sola excepción, que sale de D5: un navegador de otro origen que pide C04 o C05 pasa de recibir `200` (que el navegador no le deja leer a la página) a recibir `403`. La app, turnos y Postman no mandan `Origin`, así que para ellos nada cambia.
- Los tests no leen el `.env` local, generan sus propias claves en cada corrida y no dependen de márgenes de tiempo reales: los bordes del vencimiento se prueban con un reloj controlado.
- Ninguna prueba se conecta a la cátedra real, a su Kafka ni a su Redis.
- Aplicar la skill `hexagonal-vertical-slicing` con las excepciones ya autorizadas; dónde vive la validación se decide en el PLAN.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados: cada flujo alternativo cubierto debería
tener un criterio. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01, RF-02:** Dada una ruta protegida, cuando llega un pedido sin `Authorization`, entonces responde `401` y no devuelve datos.
- **CA-02 · RF-02:** Cuando el encabezado es `Basic …`, `Bearer` sin token o `Bearer xxx`, entonces responde `401`.
- **CA-03 · RF-03:** Cuando el token está firmado con otra clave RSA, usa `HS256`, no tiene firma o fue alterado después de firmarse, entonces responde `401`.
- **CA-04 · RF-04:** Dado un reloj controlado, cuando el `exp` del token pasó hace un segundo, entonces responde `401`; cuando vence dentro de un segundo, el pedido sigue hacia la ruta. Un token bien firmado sin `exp` responde `401`.
- **CA-05 · RF-05:** Cuando el token está bien firmado y vigente pero no tiene `sub`, o su `sub` es `abc`, `-1`, `4 2`, dígitos de otro sistema de escritura (como `٤٢`) o un número más largo que un `id` de turnos, entonces responde `401`; con `sub` `42`, el pedido sigue hacia la ruta.
- **CA-06 · RF-06:** Los `401` de CA-01 a CA-05, CA-09, CA-10 y CA-12 tienen `Content-Type` `application/problem+json`, `status` 401, `title` y `detail`, no tienen `code` ni datos del recurso o del token, y su cuerpo es idéntico en todos los casos. Llevan `WWW-Authenticate: Bearer`, sin el motivo.
- **CA-07 · RF-07:** Dadas las rutas de C04 y C05, cuando se piden con un JWT firmado con la clave que el servicio tiene configurada, con uno vencido, con uno alterado, con un texto que no es un JWT y con un `Authorization` que no es `Bearer`, entonces cada respuesta es igual a la del pedido sin encabezado.
- **CA-08 · RF-01:** Dado un token válido sin `auth`, uno con `auth` vacío, uno con `ROLE_USER` y uno con `ROLE_ADMIN`, cuando piden `GET /api/professionals/15`, `GET /api/synchronization/status` y una ruta que no existe, entonces todos reciben la respuesta de una ruta inexistente, sin `401` ni `403`.
- **CA-09 · RF-14:** Cuando se pide `GET /api/professionals/abc` sin token, entonces responde `401`, no `400`.
- **CA-10 · RF-09:** Dado un token válido, cuando el servicio se reinicia con la misma clave pública, entonces el token sigue siendo aceptado; con la clave de otro par, responde `401`.
- **CA-11 · RF-10:** En todos los escenarios anteriores, ni los logs ni los cuerpos de las respuestas contienen el token recibido ni el contenido del archivo de la clave.
- **CA-12 · RF-08:** Dada la variable con la ruta de la clave pública de test, un token firmado con su par es aceptado. Dado el servicio arrancado sin la variable, con un archivo que no existe, con uno que no es una clave, con una clave RSA de 1024 bits y con una clave privada, entonces en cada caso el servicio arranca, el log muestra un warning sin el contenido del archivo, ese mismo token recibe `401` en una ruta protegida, y C04 y C05 responden como siempre.
- **CA-13 · RF-11:** Sin orígenes configurados, cuando llega una consulta previa o un `GET` con cualquier `Origin`, a C04, a C05 o a una ruta protegida, entonces se rechaza con `403` y la respuesta no tiene `Access-Control-Allow-Origin`. Con un origen configurado, la consulta previa desde ese origen a una ruta protegida, sin token, se acepta con `Access-Control-Allow-Origin` igual al origen y `Authorization` entre los encabezados permitidos; el `GET` sin token responde `401` con ese encabezado, y el `GET` a C05 responde como siempre, con ese encabezado. Una consulta previa que pide `POST`, o que viene de otro origen, se rechaza. Los pedidos sin `Origin` responden igual con y sin orígenes configurados.
- **CA-14 · RF-12:** Cuando `POST /api/professionals` y `POST /api/professional-categories` llegan sin token, entonces responden `403` en `application/problem+json`, sin datos; con un token inválido, `401`; con uno válido, `405`. Los `GET` de C04 y C05 siguen respondiendo sin token.
- **CA-15 · RF-13:** Al cerrar la funcionalidad, `docs/decisiones/` tiene el texto de seguridad con cada decisión y cada limitación de RF-13, y lo que dice coincide con el comportamiento comprobado en CA-01 a CA-14.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

Todavía no hay rutas protegidas implementadas: los criterios se comprueban con las rutas reales, que con un token válido responden como una ruta inexistente, o con lo que decida el PLAN.

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Pedido sin `Authorization` a `GET /api/professionals/15` y a una ruta inexistente | `401`, sin datos |
| CA-02 | `Basic abc`, `Bearer` vacío y `Bearer xxx` | `401` en los tres |
| CA-03 | Token firmado con otro par RSA, token `HS256`, token sin firma y token con el contenido cambiado | `401` en todos |
| CA-04 | Con un reloj fijo: token que venció 1 s antes, token que vence 1 s después y token sin `exp` | `401`, la respuesta de la ruta y `401` |
| CA-05 | Tokens firmados con la clave configurada: sin `sub`, y con `sub` `abc`, `-1`, `4 2`, `٤٢`, un número demasiado largo y `42` | `401` en todos menos el último, que sigue hacia la ruta |
| CA-06 | Comparar los cuerpos y los encabezados de los `401` anteriores | `problem+json` sin `code` ni datos, cuerpos idénticos y `WWW-Authenticate: Bearer` sin motivo |
| CA-07 | Las cinco variantes de CA-05 de C04 y CA-11 de C05, con el token válido firmado con la clave configurada | Igual que sin encabezado |
| CA-08 | Tokens válidos con distintos `auth` a las tres rutas | La respuesta de una ruta inexistente en todos |
| CA-09 | `GET /api/professionals/abc` sin token | `401` |
| CA-10 | Validar un mismo token con dos arranques: la misma clave y la de otro par | Aceptado y `401` |
| CA-11 | Revisar la salida y los cuerpos de los escenarios anteriores | Sin el token ni el contenido de la clave |
| CA-12 | Arrancar con la clave pública de test; después sin la variable, con un archivo inexistente, con uno que no es una clave, con una clave de 1024 bits y con una clave privada; en cada caso, un token firmado con el par de test a una ruta protegida, y C04 y C05 | Con la clave, aceptado. En los demás: arranca, warning sin el contenido del archivo, `401` en la protegida, C04 y C05 sin cambios |
| CA-13 | Sin orígenes configurados: consulta previa y `GET` con `Origin` a C04, C05 y una ruta protegida. Con un origen de prueba configurado: consulta previa sin token, `GET` sin token, `GET` a C05, consulta previa con `POST` y consulta previa desde otro origen. Pedidos sin `Origin` en las dos configuraciones | Rechazos sin encabezados CORS; con el origen configurado, aceptado, `401` y `200` con el encabezado; los demás, rechazados; sin `Origin`, sin cambios |
| CA-14 | `POST` a esas dos rutas sin token, con un token inválido y con uno válido; `GET` a C04 y C05 sin token | `403` `problem+json`, `401` y `405`; los `GET`, como siempre |
| CA-15 | Revisar el texto contra la lista de RF-13 y contra lo observado en CA-01 a CA-14 | Están todas, y ninguna afirmación contradice lo observado |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

Decisiones tomadas:

- **Aprobación (2026-11-22):** el usuario aprobó la SPEC, junto con la lista de "Fuera de alcance".
- **2026-11-21 · Carpeta y rama:** `docs/sdd/validacion-jwt/`, sin id de caso de uso, como excepción a la convención, por indicación del usuario. Se trabaja en `validacion-jwt`, que el usuario creó desde `consultas-protegidas`.
- **2026-11-21 · Sin roles:** por indicación del usuario, esta funcionalidad se hace como si C07 no existiera: no lee `auth` ni rechaza por rol. Todo lo de roles, incluido el `403` de C06 (contrato §3.1), va con C07.
- **2026-11-21 · C06 queda protegido** (decisión del usuario al cerrar C05): no se cambian documentos ni issues. El motivo, que el enunciado exige proteger con JWT la comunicación entre los servicios (§3 y §9), va en la documentación (RF-13).
- **D1 · Qué se exige del `sub` (2026-11-21): opción a), obligatorio y solo dígitos; si no, `401`** (RF-05). Catálogo acepta exactamente los tokens que turnos acepta en ese punto, y el `sub` se puede escribir en un log tal como llega, sin saltos de línea ni caracteres de control. El usuario lo eligió porque así queda grabado en los logs quién hizo cada consulta, lo que se hace en C06. Con los tokens reales no cambia nada: T02 siempre pone el `id`. Alternativas descartadas: b), cualquier texto no vacío, que cuesta lo mismo, obliga a limpiar el valor antes del log y acepta tokens que turnos rechaza; c), no revisarlo, que deja llegar a C06 un token sin identidad, contra "la identidad sale siempre de `sub`" (contrato común §1.4).
- **D2 · El vencimiento (2026-11-21): opción a), `exp` obligatorio y sin tolerancia de reloj** (RF-04). Sigue el contrato común §1.4 ("`exp` no vencido") sin cambiarlo, igual que turnos. Limitación aceptada: un token que vence entre que turnos lo valida y catálogo lo valida (milisegundos) hace fallar esa consulta de turnos, que la trata como error interno (T03 4.1); al reintentar, turnos responde `401` y la app pide iniciar sesión. Con relojes distintos en dos máquinas la ventana crecería; en el despliegue previsto, una sola máquina, comparten el reloj. Se documenta con las demás limitaciones (D7). Alternativas descartadas: b), una tolerancia fija chica, que se aparta de "`exp` no vencido", obliga a aclarar el contrato común en las tres copias y deja a catálogo distinto de turnos, por una ventana de milisegundos; c), lo que trae Spring (`exp` opcional y 60 s de tolerancia), con el que un token sin `exp` no vencería nunca, y no hay revocación que lo corte (contrato común §1.6).
- **D3 · El cuerpo del rechazo (2026-11-22): opción a), `problem+json` sin `code` y un único cuerpo para todos los motivos** (RF-06). Con los mismos campos que los demás errores del servicio, y `WWW-Authenticate: Bearer` sin el motivo. Cumple el contrato común §1.4 y §2 sin cambiarlo; turnos y la app deciden por el estado, y el cliente de C06 de turnos trata igual el `401` y el `403`. Vale también para un `403` de la seguridad, si D6 lo mantiene. Si hace falta el motivo para depurar, puede ir al log, sin el token: se decide en el PLAN. Alternativas descartadas: b), sumar un `code`, que exige definirlo en las tres copias del contrato común para un valor que ningún cliente lee; c), lo que trae Spring, un `401` sin cuerpo con el motivo en el encabezado (por ejemplo, "Jwt expired at …"), que no es `problem+json` y le cuenta el motivo a quien prueba tokens.
- **D4 · Si la clave falta o no es válida (2026-11-22): opción b), el servicio arranca con un warning y toda ruta protegida responde `401`** (RF-08). "No válida": no se puede leer, no es una clave pública RSA o tiene menos de 2048 bits. Catálogo sigue sincronizando y respondiendo C04 y C05, que no usan la clave; es el mismo criterio que ya usa con las credenciales de la cátedra, y nunca abre nada. Limitación aceptada: el error se nota cuando turnos recibe `401` en C06 y lo trata como error interno; la pista es el warning del arranque. Alternativas descartadas: a), no arrancar, que ata todo el servicio a una pieza que hoy solo usa C06 y obliga a tener la clave para levantar catálogo solo; c), no arrancar solo si la clave está pero no sirve, que suma una segunda regla cuando un archivo roto y uno ausente tienen la misma consecuencia; y responder `503` en lugar de `401`, que describe mejor el problema pero exige un código nuevo en el contrato.
- **D4 · Cómo llega la clave (2026-11-22): opción a), la ruta al archivo PEM que genera turnos, en una variable del `.env` de catálogo** (RF-08). Es lo habitual con una clave fija, y lo que pide el contrato común §1.3, sin tocar turnos. Catálogo lee siempre el archivo que turnos generó, así que la clave corresponde sin copiar nada; necesita los dos repositorios uno al lado del otro, como ya exige el sistema completo. En seguridad no hay diferencia con b): la clave es pública, lo que importa es que nadie la cambie, y en los dos casos la protegen los permisos del usuario en la máquina (pregunta del usuario al decidir). De ese análisis salen dos cuidados: el warning nunca muestra el contenido del archivo (RF-10), y en Docker se monta solo la pública ("Fuera de alcance"). Alternativas descartadas: b), la clave misma en el `.env`, en una sola línea, que hay que copiar a mano cada vez que turnos genera un par, y en Docker no puede estar antes del primer arranque; c), que turnos publique la clave en un endpoint (JWKS), lo más habitual en sistemas grandes, que exige cambiar turnos, suma una dependencia de red y se aparta del §1.3.
- **D5 · CORS (2026-11-22): opción a), configuración explícita, con los orígenes en una variable del `.env`, vacía por defecto** (RF-11). Cumple lo que pide el enunciado §9 con una decisión propia, y para habilitar una web en el futuro (por ejemplo, si la app KMP suma la plataforma web, que es opcional) alcanza con la variable y un reinicio. Es lo que hizo turnos (su decisión 007), ajustado a catálogo: solo `GET`, y sin encabezados expuestos. Alternativas descartadas: b), no configurar nada, con lo que ningún navegador lee igual, pero como efecto de otras reglas, y habilitar una web exigiría cambiar código; c), abrir C04 y C05 a cualquier origen porque sus datos son públicos, que ningún cliente necesita y se aparta de "los clientes permitidos".
- **D6 · CSRF (2026-11-22): opción a), queda como viene** (RF-12). En seguridad es igual que desactivarlo: catálogo no tiene sesiones ni cookies, y la identidad viaja en `Authorization`, que el navegador nunca agrega solo. El usuario lo aceptó con la condición de que ningún `GET` pida un token por esto: CSRF no se aplica a `GET`, `HEAD` ni `OPTIONS`, así que C04 y C05 siguen públicos y C06 y C07 piden solo el JWT. Turnos lo desactivó (su decisión 006) porque tiene `POST` sin JWT (`/api/register` y `/api/authenticate`), que CSRF rechazaba; catálogo solo tiene `GET`. Que el `403` salga con el formato de RF-06 se comprueba en el PLAN; si no, se vuelve a esta decisión. Deducido del código de Spring Security 7.1.2, sin ejecutar: con la validación del token activa, CSRF deja pasar solo los pedidos que traen `Bearer`. Alternativa descartada: b), desactivarlo, que da una sola regla ("sin token, `401`") pero SonarQube lo marca como vulnerabilidad y hay que justificarlo como falso positivo, por una diferencia que solo se ve en métodos que el contrato no usa.
- **D7 · Qué se documenta (2026-11-22): la lista de RF-13, confirmada por el usuario, y TLS como limitación (opción a)).** En local todo va por HTTP; en un despliegue real, TLS lo pondría un proxy delante de los servicios. Es una decisión de todo el sistema: el usuario la va a revisar al hacer la app KMP. En cuántos textos de `docs/decisiones/` va se decide en el PLAN. Alternativas descartadas para TLS: b), HTTPS en catálogo ahora, que exige un certificado y que turnos, la app y Docker confíen en él, tres proyectos para un despliegue local; c), no mencionarlo, que deja sin respuesta un punto que el enunciado §9 pide.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, las referencias apuntan a pasos y flujos
que existen, los flujos son coherentes, los puntos de backend relevantes están
cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
