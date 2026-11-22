# SPEC: C06 — Consultar horarios semanales vigentes de un profesional

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

| Documento | Partes cubiertas |
| --- | --- |
| Caso de uso C06 (`docs/casos_de_uso/catalogo_y_sincronizacion_usecases.md`) | Precondición, pasos 1 a 5, alternativos 2.1 (solo el `401`, que ya resuelve la validación del JWT), 3.1, 4.1, 4.2, 4.3 y 4.4, y postcondición. El `403` "sin permisos" de 2.1 queda para C07 (ver "Fuera de alcance"). El "falta" de 3.1 (y el "presente" del paso 3) no tiene un caso propio, porque sin el id el pedido es otra ruta (D3.1); "vigentes" en el título son los datos actuales de la copia local, habilitados o no (D3.2). El caso de uso no cambia. En 4.4 se sigue el contrato, que fija `500` (D5) |
| Contrato de catálogo (`docs/contratos/catalogo_y_sincronizacion_contrato.md`) | §1 (fila de C06) y §3.1 completo, salvo la fila del `403`. §2.2 solo por "el mismo objeto que cada elemento de `items` en C05" |
| Contrato común (`docs/contratos/contratos_comunes.md`) | §1.4 (C06: token válido, cualquier rol; los `401` y `403` los produce la seguridad), §1.5 (trazabilidad: el `sub` en los logs, D4), §1.6 (cualquier portador de un token válido puede llamar a C06), §2 (formato de errores, `VALIDATION_ERROR`, `INTERNAL_ERROR`) y §3 (`camelCase`, horas `HH:mm:ss`, días `MONDAY` a `SUNDAY`) |
| Diagrama de secuencia | Catálogo (`docs/secuencias/catalogo_y_sincronizacion_secuencia.md`, §3) no dibuja C06: aparece en las secuencias de T03 (§2) y T04 (§3) de turnos (`turnos_service/docs/secuencias/turnos_y_reservas_secuencia.md`), con el JWT del usuario y las respuestas `200`, `401`/`403`, `404` y no disponible |
| Modelo de dominio (`docs/catalogo_y_sincronizacion_dominio.md`) | §3: `Professional`, `ProfessionalCategory`, `WeeklySchedule` y `CatalogSyncState.localVersion` (qué significa "inicializado"); reglas 2 (lo deshabilitado se conserva y se devuelve con `enabled`) y 4 (los `enabled` son independientes) |
| Enunciado (`documentacion/enunciado/PROJECT_STATEMENT-v1.md`) | §4.1 (catálogo es la única fuente local de los profesionales y sus reglas de agenda), §9 (JWT en la comunicación entre los dos servicios; validar los datos recibidos en cada límite; trazabilidad de la identidad entre los servicios) y §11 (evidencia) |
| Anexo de la cátedra | No aplica: C06 no usa la cátedra. El formato de errores sigue el de la cátedra a través del contrato común §2 (anexo §13) |
| SDD de C05 (`docs/sdd/c05-buscar-profesionales/`), de la validación del JWT (`docs/sdd/validacion-jwt/`) y decisiones 005, 006 y 007 | Lo que C06 reutiliza o hereda: el objeto del profesional y el orden de sus horarios (D1 y D7 de C05), la foto única (RF-11 de C05), el `400` (D8 y D9 de C05), la falla de la base (D10 de C05), la seguridad ya implementada y el `sub` validado (D1 de la validación del JWT) |
| Turnos (`turnos_service`: `catalog/infrastructure/client/CatalogClientAdapter`, sus DTO y `CatalogClientAdapterTest`; T03 y T04) | Solo como antecedente: qué envía turnos y cómo lee cada respuesta de C06. No es una fuente de verdad para catálogo |

## Qué construimos y para quién

<!-- Qué necesidad cubre, qué actor la inicia (la app KMP, otro servicio, el
tiempo o la cátedra) y qué podrá hacer o recibir. Describe el objetivo en
lenguaje de negocio. Resume y referencia: no copies la descripción del caso de uso. -->

Turnos necesita, en cada T03 y T04, los datos vigentes de un profesional y todos sus horarios semanales para calcular los turnos libres y validar el turno elegido, porque no guarda copia del catálogo (C06, descripción). Esta funcionalidad agrega el endpoint protegido que se los devuelve desde la copia local, con el mismo objeto que cada profesional de C05, y deja en el log de catálogo qué usuario hizo cada consulta (contrato común §1.5).

## Situación actual

<!-- Qué existe hoy y es relevante (código, datos, otras funcionalidades ya
implementadas), la limitación que queremos resolver y los comportamientos
existentes que deben conservarse. Si todavía no hay código para esto, indícalo.
No describas la arquitectura. -->

- Están implementados X01, C01 a C05 y la validación del JWT. Al cerrar la validación del JWT, la suite tenía 728 casos en 81 clases, en verde.
- C05 ya devuelve, por cada profesional, el objeto que pide C06, con los horarios ordenados por día, hora de inicio e `id`, y leído en una sola foto de la base.
- La ruta de C06 ya está protegida sin endpoint: sin token o con uno inválido responde `401` en `problem+json`; con un token válido, hoy responde `404` "No static resource" sin `code` (comprobado con un token real de turnos en TASK-08 de la validación del JWT). Un token válido pasa con cualquier `auth`: no hay roles.
- El cliente de C06 de turnos (terminado) ya lee una respuesta con este objeto. Turnos envía siempre un `Long` positivo, porque lo valida antes en T03 y T04.
- La copia de desarrollo tiene el snapshot 422 de la cátedra (24 profesionales y 44 horarios).
- El servicio no escribe hoy ninguna línea de log por pedido: solo el error de una falla no prevista (decisión 005). El `sub` de un token aceptado ya llega validado: solo dígitos, dentro del rango de un `Long` (D1 de la validación del JWT).
- **Deducido del código de Spring, sin ejecutar:** si el id se convirtiera a número con lo que trae Spring, un id no numérico respondería `400` sin `code`, sin `fieldErrors` y con el valor recibido en el `detail`; y `" 15"`, `"1 5"`, `"+15"`, `"0xF"` y `"#F"` se leerían como el profesional 15 (D1 y D2). Se comprueba en el PLAN.
- Debe conservarse el comportamiento de X01, de C01 a C05 y de la validación del JWT. Cambian a propósito los tests de seguridad que hoy esperan `404` con un token válido en la ruta de C06.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. Cada requisito indica el paso o flujo
alternativo del que proviene. No agregues requisitos que los documentos de
referencia no tengan sin consultarlo. -->

- **RF-01** · C06 pasos 1, 4 y 5; contrato §1 y §3.1: `GET /api/professionals/{professionalId}` responde `200` con el profesional (`id`, `firstName`, `lastName`, `enabled`), su categoría (`id`, `name`, `enabled`) y todos sus horarios semanales (`id`, `dayOfWeek`, `startTime`, `endTime`, `slotDurationMinutes`, `enabled`), habilitados y deshabilitados: el mismo objeto, con los mismos formatos, que el elemento de ese profesional en C05. Un profesional o una categoría deshabilitados también se devuelven, con su `enabled` (dominio, reglas 2 y 4). Los horarios van ordenados por día de la semana (de lunes a domingo), hora de inicio e `id`, como en C05 (D6.1). Nunca consulta a la cátedra ni a turnos, ni modifica datos.
- **RF-02** · C06 alternativo 4.3; contrato §3.1: un profesional sin horarios responde `200` con `weeklySchedules` vacío.
- **RF-03** · C06 paso 2 y alternativo 2.1; contrato común §1.4: exige un JWT válido. Lo resuelve la validación del JWT ya implementada, sin cambios: sin token o con uno inválido, `401` sin datos y antes de validar el id; un token válido pasa con cualquier `auth`.
- **RF-04** · C06 paso 3 y alternativo 3.1; contrato §3.1; enunciado §9 ("validar los datos recibidos"): el `professionalId` se valida antes de consultar la copia, también si no está inicializada.
  - Es válido un entero escrito solo con dígitos del 0 al 9, con un signo menos opcional, dentro del rango de un `Long`: la misma regla que `categoryId` en C05 (D1). `0`, los negativos y los ceros a la izquierda son válidos: `0` y `-1` son un profesional que no existe (`404`, RF-06), y `015` es el 15.
  - Cualquier otra escritura es inválida, por ejemplo `abc`, `1.0`, `1e3`, `+15`, `0xF`, `#F`, un id con espacios o un número fuera del rango de un `Long`.
  - Uno inválido responde `400` `VALIDATION_ERROR` con la forma de C05: el mismo `detail` fijo y `fieldErrors` con una sola entrada, `{"field": "professionalId", "message": "must be a whole number"}`, sin el valor recibido (D2). No consulta la copia.
  - Un id con caracteres que la seguridad del servicio no acepta en ninguna URL (por ejemplo, un salto de línea, un nulo, una barra, `%`, `;` o un punto codificados) se rechaza antes de llegar a C06, con un `400` sin cuerpo, como en cualquier otra ruta del servicio. No deja la línea del log.
  - Los parámetros de consulta desconocidos se ignoran: la respuesta es la misma que sin ellos, como en C04 y C05 (D6.2).
  - No hay un caso "falta" (D3.1): sin el id, el pedido es otra ruta. `GET /api/professionals` es C05, y `GET /api/professionals/` es una ruta inexistente: `401` sin token, y con un token válido lo que responde cualquier ruta inexistente, no `400`. Deducido sin ejecutar; se comprueba en el PLAN.
- **RF-05** · C06 alternativo 4.1; contrato §3.1: con un id válido y la copia sin inicializar, responde `503` `CATALOG_NOT_INITIALIZED`, sin consultar a la cátedra ni pedir una sincronización. Va antes que el `404`: sin copia no se puede decir que el profesional no existe.
- **RF-06** · C06 alternativo 4.2; contrato §3.1: si el profesional no existe en la copia, responde `404` `PROFESSIONAL_NOT_FOUND` en `problem+json`, sin datos.
- **RF-07** · C06 alternativo 4.4; contrato §3.1: si falla la base, deja un error en el log y responde `500` `INTERNAL_ERROR` con un texto fijo, sin detalles internos, como C04 y C05 (D5). Una base que no da una conexión también es una falla: el servicio deja de esperarla al vencer el tiempo del pool de conexiones, sin un límite propio (D6.3). Consecuencia aceptada en turnos: con una falla rápida, turnos no reintenta y la app recibe `500`; si la base deja esperando al pool, turnos corta por su propio timeout, reintenta y la app recibe `503` `CATALOG_UNAVAILABLE`.
- **RF-08** · C06 postcondición: la respuesta muestra una copia entera. El profesional, su categoría y sus horarios salen de la misma versión de la copia, la anterior o la posterior a una sincronización en curso, nunca de una mezcla. C06 no espera a que la sincronización termine.
- **RF-09** · Contrato común §1.5; enunciado §9; D1 de la validación del JWT: cada pedido que pasó la seguridad deja en el log una línea `INFO`, cuando ya se sabe la respuesta, con el `sub` del usuario, el `professionalId` y el resultado: encontrado (con cuántos horarios), no existe, catálogo sin inicializar, id inválido o falla de la base (D4).
  - La línea nunca lleva el token. De un id inválido no lleva el valor recibido, porque podría traer caracteres de control; el `sub` se escribe tal como llega, porque ya viene validado.
  - Los `401` y `403`, y los pedidos que la seguridad rechaza por su URL, no escriben nada (decisión 007).
  - Una falla de la base deja esta línea y, además, el error con su detalle, como en C04 y C05 (RF-07).

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Incluye las partes de los
casos de uso que se dejan para otra funcionalidad. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Confirmado por el usuario (2026-11-22, al pedir el análisis):

- **Los roles:** qué significa el `403` "token válido sin permisos" de C06 (C06 2.1 y contrato §3.1) va con C07. Hasta entonces, un token válido recibe C06 con cualquier `auth`.

Confirmadas por el usuario al aprobar la SPEC (2026-11-22):

- El endpoint de C07 y el resto de su caso de uso.
- Cambios en la seguridad: la validación del token, los rechazos y CORS quedan como los dejó la validación del JWT.
- Cambios en turnos. Turnos no escribe el `sub` en sus logs, así que la trazabilidad del contrato común §1.5 queda a medias de su lado (limitación ya documentada en la decisión 007).
- Cambios en la sincronización (C01 a C03).
- Pruebas contra la cátedra real: C06 solo lee la copia local.
- El Docker del servicio: etapa propia.

## Flujo de la operación

<!-- Cómo se inicia (request, evento o tarea programada), qué verifica y qué
resultado produce, incluidas las alternativas relevantes. Si el caso de uso ya
lo describe, referencia sus pasos en lugar de repetirlos y describe solo lo que
cubre esta funcionalidad. -->

1. Turnos (o cualquier portador de un token válido, contrato común §1.6) pide `GET /api/professionals/{professionalId}` con el JWT del usuario (C06 paso 1).
2. La seguridad valida el token (paso 2). Si falla, `401` sin datos y termina (2.1, RF-03).
3. Se valida el `professionalId` (paso 3). Si no es válido, `400` y termina (3.1, RF-04).
4. En una sola foto de la copia (RF-08): si no está inicializada, `503` (4.1, RF-05); si el profesional no existe, `404` (4.2, RF-06); si no, se leen su categoría y todos sus horarios (paso 4). Si la base falla, error del servidor (4.4, RF-07).
5. Responde `200` con el profesional, su categoría y sus horarios, o la lista vacía (paso 5 y 4.3, RF-01 y RF-02).
6. En cualquiera de los resultados de los pasos 3 a 5, queda en el log una línea `INFO` con el `sub`, el id y el resultado (RF-09, D4). Un rechazo del paso 2 no deja línea.

## Datos y reglas de negocio

<!-- Información que necesita la operación, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- El objeto es el de C05 (contrato §2.2 y §3.1): `camelCase`, horas `HH:mm:ss`, días `MONDAY` a `SUNDAY` (contrato común §3).
- Los `enabled` del profesional, de su categoría y de cada horario son independientes (dominio, regla 4). C06 no filtra ninguno: turnos decide cómo usarlos (C06, descripción).
- "Inicializada" es que hay una versión local aplicada (`CatalogSyncState.localVersion`), como en C04 y C05.
- El `professionalId` es un entero escrito con dígitos, con un signo menos opcional, dentro del rango de un `Long` (contrato §3.1, D1).
- El `sub` es el `id` del usuario de turnos, como texto y solo con dígitos (validado por la seguridad). Catálogo no lo usa para autorizar: no tiene datos de los usuarios.
- No se guarda nada.

## Casos alternativos y de error

<!-- Adapta la tabla usando BACKEND_GUIDELINES.md. Cuando el caso de uso ya define
el flujo alternativo, referencia su ID en lugar de repetirlo. Añade escenarios
relevantes. Marca No aplica con su motivo cuando corresponda. Expresa resultados
observables (estado HTTP, código de error, estado final del recurso, evento
publicado), no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Entrada inválida | Un id que no es un entero escrito con dígitos dentro del rango de un `Long`: `400` `VALIDATION_ERROR` con `fieldErrors` en `professionalId`, sin el valor y sin consultar la copia, también sin inicializar (RF-04, D1 y D2). `0` y negativos: `404` (RF-06). Sin el id, el pedido es otra ruta: C05 o una ruta inexistente (D3.1). Una URL con caracteres que la seguridad no acepta: `400` sin cuerpo, antes de llegar a C06 |
| Sin sesión o sin permiso | Sin token o con uno inválido, vencido o mal firmado: `401` sin datos, antes de validar el id (RF-03, ya implementado). Los permisos por rol van con C07 |
| Recurso inexistente o ajeno | Profesional inexistente: `404` `PROFESSIONAL_NOT_FOUND` (RF-06). No hay recursos ajenos: los datos del catálogo no son de ningún usuario |
| Copia sin inicializar | `503` `CATALOG_NOT_INITIALIZED`, sin consultar a la cátedra (RF-05) |
| Dependencia no disponible (cátedra, turnos, Redis, Kafka) | No aplica: C06 solo lee la copia local, también si la cátedra no está disponible |
| Falla de persistencia | Error en el log y `500` `INTERNAL_ERROR` sin detalles (RF-07, D5). Turnos lo trata como error interno, sin reintentar; si la base deja esperando al pool, ver la fila siguiente |
| Timeout o espera excesiva | Una base que no da una conexión es una falla de la base: el servicio deja de esperarla al vencer el tiempo del pool, sin un límite propio (RF-07, D6.3). Turnos corta antes por su propio timeout, reintenta y responde `503` a la app; mientras tanto, cada intento sigue ocupando un hilo de catálogo hasta que vence el pool (limitación aceptada) |
| Sincronización en curso | La respuesta muestra la versión anterior o la posterior, nunca una mezcla, y no la espera (RF-08) |
| Solicitudes simultáneas | Cada consulta es una lectura independiente; no hay escrituras que se pisen |
| Operación o mensaje repetido | Repetir la consulta da la misma respuesta mientras la copia no cambie: no modifica nada |
| Evento tardío o fuera de orden | No aplica: no hay eventos |
| Reinicio con operaciones pendientes | No aplica: no deja nada pendiente |
| Privacidad y observabilidad | Una línea `INFO` por consulta con el `sub`, el id y el resultado (RF-09, D4). Nunca el token, ni en el log ni en una respuesta, ni el valor de un id inválido en el log |

**Puntos de la guía no aplicables y motivo:** idempotencia, transacciones de escritura, estados, tareas programadas, persistencia nueva y paginación: C06 lee un solo profesional de la copia, sin escribir ni llamar a otros sistemas.

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de seguridad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Respetar el contrato de Documentacion y el anexo de la cátedra puede ser
una restricción; el diseño de los adaptadores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Respetar el contrato de catálogo §1 y §3.1, y el contrato común §1.4, §1.5 y §2. Si una decisión exige cambiarlos, se avisa antes y se aprueba el texto exacto, en `documentacion` y en la copia del servicio.
- El cliente de C06 de turnos, que está terminado, tiene que leer la respuesta sin cambios en turnos.
- No cambiar el comportamiento de X01, de C01 a C05 ni de la validación del JWT. Solo cambian, a propósito, los tests de seguridad que hoy esperan `404` con un token válido en esta ruta.
- Sin roles (indicación del usuario).
- Los tests no leen el `.env` local, no llaman a la cátedra real y no dependen de márgenes de tiempo de la máquina (lección de C04: lo que pasa en local por un margen puede fallar en GitHub Actions).
- Ninguna prueba se conecta a la cátedra real, a su Kafka ni a su Redis.
- Aplicar la skill `hexagonal-vertical-slicing` con las excepciones ya autorizadas; lo nuevo se decide en el PLAN.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados: cada flujo alternativo cubierto debería
tener un criterio. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dada una copia inicializada con un profesional con su categoría y horarios, uno de ellos deshabilitado, cuando se pide con un token válido, entonces responde `200` con todos sus campos y todos sus horarios, y el cuerpo es idéntico al elemento de ese profesional en la respuesta de C05, con los horarios por día de la semana, hora de inicio e `id`. Con un parámetro de consulta desconocido (`?x=1`), la respuesta es la misma.
- **CA-02 · RF-01:** Dado un profesional deshabilitado, y otro habilitado de una categoría deshabilitada, cuando se piden, entonces cada uno responde `200` con sus `enabled` tal como están en la copia.
- **CA-03 · RF-02:** Dado un profesional sin horarios, cuando se pide, entonces responde `200` con `weeklySchedules` vacío.
- **CA-04 · RF-03:** Dado un profesional que existe, cuando se pide sin token o con uno inválido, entonces responde `401` y el cuerpo no tiene sus datos; con tokens válidos sin `auth`, con `ROLE_USER` y con `ROLE_ADMIN`, entonces responde `200`.
- **CA-05 · RF-04:** Cuando el id de la ruta es (con los caracteres especiales codificados en la URL) `abc`, `1.0`, `1e3`, `+15`, `0xF`, `#F`, `" 15"` (con un espacio), `"1 5"` y `9223372036854775808` (uno más que el mayor `Long`), entonces cada uno responde `400` `VALIDATION_ERROR` en `problem+json`, con el `detail` fijo de C05 y `fieldErrors` igual a `[{"field": "professionalId", "message": "must be a whole number"}]`, y el cuerpo no contiene el valor recibido. Responde lo mismo con la copia sin inicializar. Con `015`, responde el profesional 15. Sin token, `abc` responde `401`. `GET /api/professionals/`, con un token válido, responde lo mismo que una ruta inexistente, no `400` (D3.1). Con `%0A15`, responde `400` sin cuerpo y no queda línea en el log.
- **CA-06 · RF-05:** Dada la copia sin inicializar, cuando se pide un id válido, exista o no en la base, entonces responde `503` `CATALOG_NOT_INITIALIZED` y la cátedra simulada no recibe ningún pedido.
- **CA-07 · RF-06:** Dada una copia inicializada, cuando se pide un id válido que no está en la copia, también `0` y `-1`, entonces responde `404` `PROFESSIONAL_NOT_FOUND` en `problem+json`, sin datos.
- **CA-08 · RF-07:** Dada la copia inicializada y la base fallando al leer, cuando se pide un id válido, entonces responde `500` `INTERNAL_ERROR` en `problem+json` con un texto fijo, sin nombres de tablas ni de la excepción, y el log tiene el error con su detalle.
- **CA-09 · RF-08:** Dada una sincronización que confirma un cambio del profesional y de sus horarios entre las lecturas de C06, entonces la respuesta muestra entera la versión anterior.
- **CA-10 · RF-09:** Dado un token válido con `sub` `42`, cuando se pide un profesional con tres horarios, uno que no existe, un id inválido, un id válido con la copia sin inicializar y un id válido con la base fallando, entonces cada pedido deja exactamente una línea `INFO` con `42` y su resultado, con el id en todos salvo en el inválido, cuyo valor no aparece en el log. La falla de la base deja además el error. Sin token y con un token inválido no queda ninguna línea. Ninguna línea contiene el token.
- **CA-11 · Restricción (turnos lee la respuesta sin cambios):** Dados turnos y catálogo en local, sin la cátedra, catálogo con la clave pública de turnos y la copia de desarrollo, y un usuario de turnos con sesión iniciada (D7):
  - con su token, C06 de un profesional real responde `200`, idéntico a su elemento en C05; un id inexistente, `404`; `abc`, `400`; y sin token, `401`;
  - por turnos, T03 en una fecha en que el profesional no atiende responde `200` con la lista vacía; T04 con una hora fuera de sus horarios, `400` con el código propio de turnos; y T03 con un id inexistente, `404`;
  - el log de catálogo tiene una línea por cada consulta, con el `sub` de ese usuario, y nunca el token.

  Opcionales, si los datos o la configuración lo permiten: un profesional deshabilitado, `409` en T03; catálogo sin la clave, `500` en T03.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Copia con un profesional, su categoría y tres horarios cargados fuera de orden (uno deshabilitado); pedir C06, C06 con `?x=1` y C05 | `200`; el objeto de C06 es idéntico a su elemento en C05, con los horarios ordenados; con `?x=1`, la misma respuesta |
| CA-02 | Un profesional deshabilitado y uno de una categoría deshabilitada | `200` con sus `enabled` |
| CA-03 | Un profesional sin horarios | `200` con `weeklySchedules: []` |
| CA-04 | Sin token, con un token inválido y con tokens válidos con distintos `auth` | `401` sin datos; `200` con cualquier `auth` |
| CA-05 | Los ids inválidos de CA-05, con la copia inicializada y sin inicializar; `015`; `abc` sin token; `/api/professionals/` con token; `%0A15` con token | `400` con la forma de D2 y sin el valor; el profesional 15; `401`; la respuesta de una ruta inexistente; `400` sin cuerpo y sin línea en el log |
| CA-06 | Copia sin versión local, con y sin el profesional en la base | `503` `CATALOG_NOT_INITIALIZED`; ningún pedido a la cátedra simulada |
| CA-07 | Un id válido que no está en la copia, `0` y `-1` | `404` `PROFESSIONAL_NOT_FOUND` |
| CA-08 | Copia inicializada y una falla de la base provocada al leer | `500` `INTERNAL_ERROR` sin detalles; el error en el log |
| CA-09 | Confirmar un cambio desde otra conexión entre dos lecturas de C06 | La versión anterior entera |
| CA-10 | Con un token de `sub` `42`: encontrado, no existe, id inválido, sin inicializar y falla de la base; después sin token y con un token inválido. Revisar la salida | Una línea `INFO` por pedido con `42` y su resultado; el inválido sin su valor; el error de la falla; nada en los rechazos; nunca el token |
| CA-11 | Prueba manual que corre el usuario: los dos servicios en local, sin la cátedra; pedidos directos a C06 con el token real y pedidos a T03 y T04 que no llegan a la cátedra; revisar el log de catálogo | Lo que dice CA-11 |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

Decisiones tomadas:

- **Aprobación (2026-11-22):** el usuario aprobó la SPEC, junto con la lista de "Fuera de alcance".
- **S1 · Las URL que rechaza la seguridad (2026-11-22, cambio con la SPEC ya aprobada, aprobado por el usuario): opción a), se aclara la SPEC** (RF-04, RF-09, la fila "Entrada inválida", CA-05 y su fila en "Cómo se comprueba"). Salió de la prueba descartable del PLAN: la seguridad de Spring rechaza antes de cualquier ruta, con un `400` sin cuerpo, una URL con ciertos caracteres codificados (saltos de línea, nulo, barras, `%`, `;` y el punto), también en C04 y C05; esos pedidos no llegan a C06 ni dejan la línea del log. La aclaración describe lo que el servicio ya hace, sin decidir nada nuevo. Alternativas descartadas: b), darle `problem+json` a ese rechazo, que cambia la seguridad (fuera de alcance), cambia la respuesta de C04 y C05 y no sabe qué campo informar; c), aflojar ese filtro para que esos caracteres lleguen al controller, que debilita una defensa contra rutas armadas con barras y puntos codificados y también cambia la seguridad.
- **2026-11-22 · Carpeta y rama:** `docs/sdd/c06-horarios-profesional/`, con el nombre de la rama, propuesto en el análisis previo; el usuario pidió crear la SPEC sin objetarlo. Se trabaja en `horarios-profesional`, que el usuario creó desde `validacion-jwt`.
- **2026-11-22 · Sin roles:** por indicación del usuario, el `403` de C06 va con C07 (ver "Fuera de alcance").
- **D1 · Qué `professionalId` es válido (2026-11-22): opción a), un entero escrito solo con dígitos, con un signo menos opcional, dentro del rango de un `Long`** (RF-04). Es la misma regla que `categoryId` en C05 (D8.2 de C05), así el servicio tiene una sola regla para los ids, y sigue el contrato §3.1 al pie de la letra: `0` y `-1` son números y dan `404` porque no existen. Turnos nunca los manda, porque valida antes que el id sea positivo. Alternativas descartadas: b), solo positivos (`0` y `-1` con `400`), que mejora el mensaje para dos casos que turnos no envía, a cambio de una segunda regla de ids en el servicio y un `400` que el contrato no lista (y ningún documento dice que los ids de la cátedra sean siempre positivos, dominio regla 1); c), lo que acepte la conversión de Spring, que según su código (sin ejecutar) lee `" 15"`, `"1 5"`, `"+15"`, `"0xF"` y `"#F"` como el 15: reglas que decide la librería y escrituras que ningún cliente manda, como descartó C05 en D8.2.
- **D2 · La forma del `400` (2026-11-22): opción a), la de C05** (RF-04): `VALIDATION_ERROR`, el mismo `detail` fijo y `fieldErrors` con `{"field": "professionalId", "message": "must be a whole number"}`, el mismo mensaje que `categoryId` en C05, que tiene la misma regla. No muestra el valor recibido. Una sola forma para todos los `400` del servicio. Alternativas descartadas: b), `VALIDATION_ERROR` sin `fieldErrors`, que el contrato §3.1 no exige y que con un solo parámetro pierde poca información, pero deja dos formas de `400` en el servicio; c), lo que trae Spring, un `400` sin `code` con el texto de la conversión y el valor recibido en el `detail` (deducido, sin ejecutar), que contradice el contrato §3.1.
- **D3 · La redacción de C06 (2026-11-22):**
  - **D3.1 · "`professionalId` falta": opción a), se anota en la SPEC y el caso de uso no cambia** (RF-04). C06 3.1 dice "falta o no es numérico" y el paso 3 "presente y numérico", pero el id es parte de la ruta: sin él, el pedido es C05 o una ruta inexistente. El contrato §3.1, que es lo que leen los clientes, ya dice solo "no numérico". La diferencia no cambia ningún comportamiento, así que no justifica tocar las dos copias (a diferencia de D1 de C05, donde faltaba un campo de la respuesta). Alternativas descartadas: b), ajustar el texto del paso 3 y de 3.1 en `documentacion` y en la copia, que deja exacta la fuente de verdad a cambio de un cambio en dos copias por una frase sin efecto; c), que `/api/professionals/` responda `400`, que suma una regla para una ruta que no es C06, con un `400` que el contrato no lista.
  - **D3.2 · "vigentes" en el título: opción a), queda como está.** Se lee como "los datos actuales de la copia local"; la descripción del caso de uso ya aclara que devuelve también lo deshabilitado. Alternativa descartada: b), el título del contrato ("Consultar profesional y horarios semanales"), con el mismo costo que b) de D3.1.
- **D5 · El error de una falla de la base (2026-11-22): opción a), `500` `INTERNAL_ERROR`, como fija el contrato §3.1** (RF-07). C06 4.4 permite `500` o `503`; el contrato ya eligió, con el criterio de C04 y C05 (D10 de C05), y el manejador global ya lo responde. Consecuencia aceptada en turnos, que no cambia (sus casos de uso T03 y T04 no listan un `500` de catálogo, pero su contrato sí: §4.1, "Cualquier otra respuesta (por ejemplo `400` o `500`)", que se registra y responde `500` `INTERNAL_ERROR`): con una falla rápida, turnos la lee como respuesta no prevista, no reintenta, y la app recibe `500` `INTERNAL_ERROR`; si la base deja esperando al pool (hasta 30 s, el valor por defecto de Hikari), turnos corta a los 3 s, reintenta y la app recibe `503` `CATALOG_UNAVAILABLE` a los ~10 s. Alternativas descartadas: b), `503` con un código nuevo solo en C06, que mejora el mensaje de la app solo en la falla rápida, a cambio de cambiar el contrato §3.1 en las dos copias, una regla nueva para decidir qué error es "de la base", C06 distinto de C04 y C05, y reintentos también de fallas que no son pasajeras; c), `503` en C04, C05 y C06, que reabre dos casos de uso cerrados y tres secciones del contrato.
- **D4 · El `sub` en el log (2026-11-22): opción a), una línea `INFO` por cada pedido que pasó la seguridad, cuando ya se sabe la respuesta** (RF-09). Lleva el `sub`, el `professionalId` y el resultado; nunca el token ni el valor de un id inválido. Los rechazos de la seguridad no escriben nada (decisión 007), y la falla de la base suma su error con el detalle. Cada consulta normal deja el `sub`, que es la trazabilidad que pide el contrato común §1.5 y la que el usuario quiso al decidir D1 de la validación del JWT; con el resultado, el log responde quién pidió qué y qué recibió. El volumen es una línea por cada T03 o T04. El texto exacto de la línea y cómo se junta con el error de la falla van en el PLAN. Alternativas descartadas: b), solo los resultados que no son `200`, en `WARN`, que deja sin rastro la operación normal y trata como problema un `404` o un `503`, que son respuestas esperables; c), una línea `DEBUG`, apagada por defecto, que en la práctica no deja nada.
- **D6 · Lo que se hereda de C05 (2026-11-22):**
  - **D6.1 · El orden de los horarios: opción a), por día de la semana, hora de inicio e `id`, como C05 (D7.2 de C05)** (RF-01). Es la única forma de que C06 sea "el mismo objeto" que el elemento de C05 (contrato §3.1). A turnos no le importa el orden. Alternativas descartadas: b), por `id`, estable pero sin significado, y distinto de C05 para el mismo profesional; c), sin orden definido, con una respuesta que puede cambiar entre pedidos y que ningún test puede fijar.
  - **D6.2 · Los parámetros de consulta desconocidos: opción a), se ignoran, como en C04 y C05 (D8.3 de C05)** (RF-04). C06 no tiene parámetros de consulta, así que ni siquiera está el riesgo que aceptó C05 (uno mal escrito que cambia el resultado). Alternativa descartada: b), `400`, un error que el contrato no define y que deja a C06 distinto de los otros dos.
  - **D6.3 · Sin un límite de espera propio para la base: opción a), el tiempo del pool, como D4 de C04 y D10 de C05** (RF-07). Lo nuevo en C06: turnos corta a los 3 s y reintenta, así que con la base colgada cada T03 deja ocupados en catálogo tres hilos de los 200 de Tomcat, hasta 30 s cada uno, aunque turnos ya no espere. Limitación aceptada, por el criterio del usuario para las fallas poco comunes: los hilos se liberan solos y en el despliegue local no habría tantas operaciones a la vez con la base caída. Alternativas descartadas: b), un límite propio y más corto para C06, que suma una propiedad y una regla distinta de C04 y C05 para una falla poco común; c), bajar el tiempo del pool en todo el servicio, que cambia también la espera de la sincronización (C01 a C03) por un caso de C06 y no resuelve el hallazgo de D4 de C04 (una consulta colgada no depende del pool).
- **D7 · La prueba con turnos real (2026-11-22): opción a), una prueba manual con los dos servicios en local, sin la cátedra, que corre el usuario**, como TASK-08 de la validación del JWT (CA-11). Es la única forma de comprobar con el cliente real de turnos la restricción de que lee la respuesta sin cambios, y es la primera consulta completa de C06 entre los dos servicios. Deja un usuario de prueba en la base de desarrollo de turnos, salvo que se reúse el de TASK-08. Cómo se arman los pedidos (ids y fechas elegidos desde C05 al correrla) va en el PLAN. Alternativas descartadas: b), solo los tests automáticos, donde la misma persona escribe el ejemplo de los dos lados y nada prueba que coincidan (como P6.4 de la validación del JWT); c), dejarla para la etapa de Docker y E2E, que cierra C06 sin probar la integración y mezcla un posible defecto con los de Docker.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, las referencias apuntan a pasos y flujos
que existen, los flujos son coherentes, los puntos de backend relevantes están
cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
