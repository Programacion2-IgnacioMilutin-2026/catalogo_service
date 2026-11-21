# SPEC: C04 — Listar categorías

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
| Caso de uso C04 (`docs/casos_de_uso/catalogo_y_sincronizacion_usecases.md`) | Todo: precondición, pasos 1 a 3, alternativos 1.1, 2.1, 2.2 y 2.3, y postcondición. En 2.3 se sigue el contrato, que fija `500` (ver "Decisiones tomadas") |
| Diagrama de secuencia (`docs/secuencias/catalogo_y_sincronizacion_secuencia.md`) | §3, la rama `public query`, solo lo de C04 (sin el `opt only C05`) |
| Contrato de catálogo (`docs/contratos/catalogo_y_sincronizacion_contrato.md`) | §1 (fila de C04: `GET /api/professional-categories`, público, lo consume la app) y §2.1 |
| Contrato común (`docs/contratos/contratos_comunes.md`) | §1.4 (C04 es público; los endpoints públicos ignoran `Authorization`), §2 (formato de errores, `INTERNAL_ERROR`) y §3 (propiedades en `camelCase`) |
| Modelo de dominio (`docs/catalogo_y_sincronizacion_dominio.md`) | §1 y §3: `ProfessionalCategory` (`description` opcional) y `CatalogSyncState.localVersion` (qué significa "inicializado"); reglas 2 y 4 |
| Caso de uso A04 de la app (`documentacion/uml/casos_de_uso/app_kmp_usecases.md`) | Solo como quien lo usa: paso 2 (arma el filtro por categoría) y alternativos 2.1 (sin categorías por error) y 2.2 (lista vacía) |
| Enunciado (`documentacion/enunciado/PROJECT_STATEMENT-v1.md`) | §9: "proteger los endpoints que no sean expresamente públicos" (D1) |
| Anexo de la cátedra | No aplica: C04 no usa la cátedra. El formato de errores sigue el de la cátedra a través del contrato común §2 (anexo §13) |
| Turnos (`turnos_service`: `SecurityConfig` de T01 y T02, `docs/sdd/validacion-jwt/`, decisiones 003, 006, 007 y 008) | Solo como antecedente de cómo expuso sus endpoints públicos antes y después de validar el JWT. No es una fuente de verdad para catálogo |

## Qué construimos y para quién

<!-- Qué necesidad cubre, qué actor la inicia (la app KMP, otro servicio, el
tiempo o la cátedra) y qué podrá hacer o recibir. Describe el objetivo en
lenguaje de negocio. Resume y referencia: no copies la descripción del caso de uso. -->

La app pide las categorías de profesionales para armar el filtro de la búsqueda (A04, paso 2; C05). El servicio de catálogo le devuelve todas las de su copia local, habilitadas y deshabilitadas, con su estado, y la app decide cómo mostrarlas (C04, descripción). Es público: no hace falta haber iniciado sesión. Nunca consulta a la cátedra.

Es el primer endpoint REST del servicio. Por eso también trae lo mínimo que necesita para atender un pedido público y responder sus errores con el formato común, sin adelantar la validación del JWT, que es una funcionalidad aparte, después de C05 (D1).

## Situación actual

<!-- Qué existe hoy y es relevante (código, datos, otras funcionalidades ya
implementadas), la limitación que queremos resolver y los comportamientos
existentes que deben conservarse. Si todavía no hay código para esto, indícalo.
No describas la arquitectura. -->

Verificado el 2026-11-20, en la rama `listar-categorias` (creada por el usuario desde `consultas-catalogo`, las dos en `dcf0b91`, el merge de `develop` con C02 completo):

- X01, C01, C03 y C02 están implementados. La copia local tiene categorías, profesionales, horarios y el estado de sincronización. Una sincronización completa reemplaza la copia en una sola unidad de trabajo, y una incremental aplica cada versión en la suya.
- La versión local está vacía hasta que la primera sincronización completa termina bien, y un fracaso no la borra: una copia inicializada no vuelve a estar sin inicializar.
- Una copia inicializada siempre tiene categorías por los flujos del servicio: C01 rechaza un snapshot sin categorías (`INVALID_DATA`) y la sincronización incremental nunca borra. C04 2.2 solo se da si la base se modifica por fuera.
- El nombre de una categoría siempre tiene texto, porque la sincronización rechaza uno vacío; la descripción puede faltar. Nada impide dos categorías con el mismo nombre. Los datos de ejemplo de los tests (con el formato real) llevan tildes, como "Kinesiología".
- No hay ningún endpoint, ni configuración de seguridad, ni manejo de errores HTTP. Con las dependencias de seguridad ya incluidas y sin configuración, Spring Boot exige autenticación en todo pedido y no tiene con qué validar un JWT: hoy ningún pedido HTTP pasa. Esto se dedujo del código de Spring Boot 4.1.2, sin ejecutarlo; se comprueba en el PLAN.
- No hay un límite de espera para las consultas a la base.
- Turnos no consume C04: solo lo usa la app.
- Las copias de `contratos_comunes.md` y de X01 (caso de uso y secuencia) son idénticas en `documentacion`, `turnos_service` y `catalog_service`; las de catálogo (casos de uso, contrato, dominio y secuencia) coinciden con `documentacion` (comparadas el 2026-11-20).
- Lo que debe conservarse: el comportamiento de X01, C01, C02 y C03. C04 no escribe nada.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. Cada requisito indica el paso o flujo
alternativo del que proviene. No agregues requisitos que los documentos de
referencia no tengan sin consultarlo. -->

- **RF-01** · C04 pasos 1 a 3; contrato §1 y §2.1 (D2, D3): `GET /api/professional-categories`, sin parámetros, responde `200` con todas las categorías de la copia local, habilitadas y deshabilitadas, cada una con `id`, `name`, `description` y `enabled`, en el orden de D2. Si llegan parámetros de consulta, no cambian la respuesta. Nunca consulta a la cátedra ni modifica datos.
- **RF-02** · C04 precondición y alternativo 1.1; contrato común §1.4 (D1): es público. Un pedido sin `Authorization`, o con uno válido, inválido, vencido o mal formado, recibe la misma respuesta.
- **RF-03** · C04 alternativo 2.1; contrato §2.1: si la copia no está inicializada, responde `503` `CATALOG_NOT_INITIALIZED`, sin consultar a la cátedra ni pedir una sincronización.
- **RF-04** · C04 alternativo 2.2; contrato §2.1: si la copia está inicializada pero no tiene categorías, responde `200` con `[]`.
- **RF-05** · C04 alternativo 2.3; contrato §2.1 (D4): si falla la base, al leer el estado o las categorías, deja un error en el log y responde `500` `INTERNAL_ERROR`, sin detalles internos. Una base que no da una conexión también es una falla: el servicio deja de esperarla al vencer el tiempo del pool de conexiones. No hay un límite propio (D4).
- **RF-06** · C04 postcondición: la respuesta muestra una copia entera, la anterior o la posterior a una sincronización en curso, nunca una mezcla. C04 no espera a que la sincronización termine.
- **RF-07** · Enunciado §9 (D1): lo que no es C04 no queda abierto. Hasta que exista la validación del JWT, ningún otro pedido obtiene datos sin autenticación. Cómo se rechazan esos pedidos (estado y cuerpo) se define en la funcionalidad de la validación del JWT.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Incluye las partes de los
casos de uso que se dejan para otra funcionalidad. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Confirmadas por el usuario el 2026-11-20, al aprobar la SPEC:

- La validación del JWT y lo que trae: la clave pública de turnos, los rechazos `401` y `403` y que las rutas públicas sigan ignorando el token cuando exista esa validación. Van en su funcionalidad, después de C05 (ver "Para la validación del JWT").
- CORS: va con la validación del JWT (D5).
- Un límite de espera propio para la base (D4).
- Los endpoints de C05, C06 y C07.
- Paginación: el contrato §2.1 no la tiene para C04, y son pocas categorías.
- Cambios en la sincronización (C01, C02 y C03).
- Pruebas contra la cátedra real: C04 no la usa.
- El Docker del servicio: etapa propia.

## Flujo de la operación

<!-- Cómo se inicia (request, evento o tarea programada), qué verifica y qué
resultado produce, incluidas las alternativas relevantes. Si el caso de uso ya
lo describe, referencia sus pasos en lugar de repetirlos y describe solo lo que
cubre esta funcionalidad. -->

1. La app pide `GET /api/professional-categories`, con o sin `Authorization`; el pedido se trata como anónimo (C04 paso 1 y 1.1; RF-02).
2. El servicio mira si la copia está inicializada; si no, responde `503` (C04 2.1; RF-03).
3. Lee todas las categorías de la copia (C04 paso 2). Si falla la base, responde `500` (C04 2.3; RF-05).
4. Responde `200` con la lista en el orden de D2, o `[]` si no hay categorías (C04 paso 3 y 2.2; RF-01 y RF-04).

## Datos y reglas de negocio

<!-- Información que necesita la operación, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- **La respuesta** (contrato §2.1; contrato común §3): un array JSON. Cada elemento tiene exactamente `id` (número), `name` (texto), `description` (texto, o `null` si la categoría no tiene descripción: el campo siempre está, D3) y `enabled` (booleano). No lleva otros datos. Las deshabilitadas van con `enabled` en `false`; cómo mostrarlas lo decide la app (A04 paso 3).
- **Orden** (contrato §2.1; D2): alfabético en español, de la A a la Z, por `name`. No distingue mayúsculas ni tildes, y la ñ va después de la n. Si dos nombres quedan iguales con esa regla, va primero el de `id` menor. Habilitadas y deshabilitadas van mezcladas. Por ejemplo: "Nutrición", "Odontología", "Óptica", "pediatría", "Psicología". El orden es el mismo en cualquier entorno, sin depender de la configuración de la base.
- **Inicializada** (dominio §3): la versión local tiene valor. Que haya o no categorías no cambia eso.
- **Errores** (contrato común §2): `Content-Type: application/problem+json` con `status`, `title`, `code` y `detail`. `detail` es un texto fijo, para el desarrollador, y nunca incluye detalles internos: ni SQL, ni nombres de tablas o clases, ni trazas. El contrato no define encabezados para el `503`, y no se agregan.
- **Qué se registra:** la falla de la base, como error en el log. Un pedido que termina bien no deja nada en el log.

## Casos alternativos y de error

<!-- Adapta la tabla usando BACKEND_GUIDELINES.md. Cuando el caso de uso ya define
el flujo alternativo, referencia su ID en lugar de repetirlo. Añade escenarios
relevantes. Marca No aplica con su motivo cuando corresponda. Expresa resultados
observables (estado HTTP, código de error, estado final del recurso, evento
publicado), no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Entrada inválida | No tiene parámetros ni cuerpo; los parámetros de consulta que lleguen no cambian la respuesta (RF-01). Un `Authorization` inválido se ignora (RF-02) |
| Sin sesión o sin permiso | Público: sin sesión responde igual que con una (C04 1.1; RF-02; D1) |
| Recurso inexistente o ajeno | No aplica: es la lista completa, sin un recurso por id ni un dueño |
| Dependencia no disponible (cátedra, Redis, Kafka) | No usa ninguna: responde aunque la integración con la cátedra no esté disponible, porque solo lee la copia local. Base no disponible: `500` (RF-05) |
| Timeout o espera excesiva | Sin límite propio (D4). Una base caída, o colgada al pedir una conexión, responde `500` al vencer el tiempo del pool de conexiones (30 s por defecto; RF-05). Limitación aceptada: una consulta que ya estaba en curso cuando la base se colgó espera hasta que la base vuelva; la app corta por su lado y ofrece reintentar (A04 2.1) |
| Operación o mensaje repetido | Solo lee: repetir el pedido da la misma respuesta mientras la copia no cambie |
| Evento tardío o fuera de orden | No aplica: no recibe eventos |
| Falla de persistencia | `500` `INTERNAL_ERROR` y error en el log (C04 2.3; RF-05). No hay escrituras |
| Solicitudes simultáneas | Los pedidos son independientes entre sí. Con una sincronización en curso, la respuesta muestra la copia anterior o la posterior, nunca una mezcla, y no espera a que termine (RF-06) |
| Reinicio con operaciones pendientes | No aplica: no deja nada pendiente. Recién arrancado con la base vacía, responde `503` hasta que termine la primera sincronización completa (RF-03) |
| Otros puntos aplicables de la guía | Autorización: público, y lo demás queda cerrado (RF-02, RF-07; D1). Privacidad: la respuesta solo lleva datos públicos del catálogo y los errores no muestran detalles internos (RF-05). Rendimiento: lista chica (8 categorías en la cátedra real) y sin paginación (contrato §2.1). Arranque: no depende de la cátedra (RF-03) |

**Puntos de la guía no aplicables y motivo:** idempotencia de escrituras, consistencia entre sistemas, estados y transiciones, tareas programadas y Kafka (C04 solo lee la copia local); fechas, horas y paginación (la respuesta no las tiene).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de seguridad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Respetar el contrato de Documentacion y el anexo de la cátedra puede ser
una restricción; el diseño de los adaptadores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Respetar el contrato §1 y §2.1 y el contrato común §1.4 y §2.
- C04 no consulta a la cátedra (C04, descripción).
- La validación del JWT va después de C05, en su propia funcionalidad (orden acordado); C04 no adelanta nada de ella (D1).
- X01, C01, C02 y C03 siguen cumpliendo sus SPEC, sin cambios de comportamiento.
- Los tests automáticos no llaman a la cátedra real ni leen el `.env`.
- Aplicar la skill `hexagonal-vertical-slicing`. Cada excepción se analiza en el contexto de C04 (indicación del usuario del 2026-11-15).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados: cada flujo alternativo cubierto debería
tener un criterio. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dada una copia inicializada con categorías habilitadas y deshabilitadas, cuando se pide la lista sin `Authorization`, entonces responde `200` con todas, cada una con sus cuatro campos y los valores de la copia, sin otros campos, y no se hace ningún pedido a la cátedra.
- **CA-02 · RF-01:** Dada una copia con nombres en minúscula y en mayúscula, con y sin tildes, uno que empieza con ñ, habilitadas y deshabilitadas, y dos categorías con nombres que solo difieren en mayúsculas o tildes, cuando se pide la lista, entonces vienen en el orden de D2: alfabético sin distinguir mayúsculas ni tildes, la ñ después de la n, y las dos iguales por `id` menor primero.
- **CA-03 · RF-01:** Dada una categoría sin descripción, cuando se pide la lista, entonces su elemento trae `"description": null` y los otros tres campos con sus valores.
- **CA-04 · RF-01:** Dada la misma copia, cuando se pide con parámetros de consulta, entonces la respuesta es la misma que sin ellos.
- **CA-05 · RF-02:** Dada la misma copia, cuando se pide con un JWT bien firmado, con uno vencido, con uno alterado, con un texto que no es un JWT y con un `Authorization` que no es `Bearer`, entonces cada respuesta es igual a la del pedido sin encabezado.
- **CA-06 · RF-03:** Dada una copia sin versión local, con categorías cargadas o sin ellas, cuando se pide la lista, entonces responde `503` en `application/problem+json` con `code` `CATALOG_NOT_INITIALIZED`, no se hace ningún pedido a la cátedra y la base no cambia.
- **CA-07 · RF-04:** Dada una copia inicializada sin categorías, cuando se pide la lista, entonces responde `200` con `[]`.
- **CA-08 · RF-05:** Dada una falla de la base al leer el estado, y otra al leer las categorías, cuando se pide la lista, entonces responde `500` en `application/problem+json` con `code` `INTERNAL_ERROR`, el cuerpo no contiene SQL, nombres de tablas o clases ni trazas, y queda un error en el log.
- **CA-09 · RF-05:** Dada la base detenida, cuando se pide la lista, entonces responde `500` en `application/problem+json` con `code` `INTERNAL_ERROR`, sin detalles internos, y queda un error en el log.
- **CA-10 · RF-06:** Dada una sincronización que está reemplazando la copia y todavía no terminó, cuando se pide la lista, entonces responde enseguida con la copia anterior; y cuando la sincronización terminó, con la nueva.
- **CA-11 · RF-01, RF-03:** Dada la integración con la cátedra no disponible y una copia inicializada, cuando se pide la lista, entonces responde `200` igual.
- **CA-12 · RF-07:** Dado el servicio en marcha, cuando se pide sin autenticación cualquier otra ruta, entonces no responde con datos.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Copia inicializada con categorías habilitadas y deshabilitadas; pedido sin encabezado; registro de los pedidos a la cátedra simulada | `200` con todas y sus cuatro campos; ningún pedido a la cátedra durante la consulta |
| CA-02 | Copia con los nombres del criterio, cargados en un orden distinto del esperado | La lista en el orden de D2 |
| CA-03 | Copia con una categoría sin descripción | El campo presente, con `null` |
| CA-04 | La misma copia; pedido con parámetros de consulta | Igual al pedido sin parámetros |
| CA-05 | La misma copia; un pedido por cada variante del encabezado | Cada respuesta igual a la del pedido sin encabezado |
| CA-06 | Base sin versión local, con y sin categorías | `503` `CATALOG_NOT_INITIALIZED` en `problem+json`; sin pedidos a la cátedra; base sin cambios |
| CA-07 | Copia inicializada y tabla de categorías vacía | `200` `[]` |
| CA-08 | Falla de la base provocada al leer el estado, y al leer las categorías | `500` `INTERNAL_ERROR` en `problem+json`, sin detalles internos; error en el log |
| CA-09 | Base de la aplicación detenida antes del pedido | `500` `INTERNAL_ERROR` en `problem+json`, sin detalles internos; error en el log |
| CA-10 | Un reemplazo de la copia que queda abierto mientras llega el pedido, y después se completa | Antes: la copia anterior, sin esperar; después: la nueva |
| CA-11 | Cátedra simulada sin integración disponible; copia inicializada | `200` |
| CA-12 | Pedido sin autenticación a otra ruta | Sin datos |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

Para el PLAN, ya anotado:

- La configuración de seguridad mínima (D1): dónde va (en `catalogo.config`, si entra en la excepción de X01), si se abre solo `GET` o todos los métodos de la ruta, cómo queda cerrado el resto y qué pasa con CSRF.
- Comprobar en ejecución qué hace hoy la seguridad por defecto, que se dedujo del código.
- El formato `problem+json`: manejador global, traducción en el controller o la propiedad de Spring Boot; y si vuelve la excepción base del slice (`CatalogException`, quitada en C03).
- Dónde se ordena según D2 (en la base o en Java), comprobando que la técnica elegida deje la ñ después de la n; y cómo se garantiza RF-06 al leer el estado y las categorías.
- Los nombres del service, el controller y la respuesta, y la excepción a la skill por la ruta `/api/...` del contrato.
- Si el caso de uso lee el estado por el puerto de salida o por `GetCatalogSyncStateUseCase`.
- Cómo se provoca una falla de la base en los tests y cómo se prueba CA-10.

Para la validación del JWT (anotado por indicación del usuario, D1):

- C04 y C05 tienen que seguir ignorando `Authorization`. El filtro que lee el token rechaza uno inválido aunque la ruta esté abierta, así que hará falta que no lo busque en las rutas públicas, como hizo turnos. CA-05 lo detecta si se olvida.
- Definir ahí los rechazos `401` y `403` (formato y cuerpo), la clave pública de turnos y CORS (D5).

Decisiones tomadas:

- **Aprobación (2026-11-20):** el usuario aprobó la SPEC y confirmó tres puntos: los parámetros de consulta que lleguen se ignoran, en lugar de rechazarse, porque el contrato dice "sin parámetros" y no define un error (RF-01); la lista de "Fuera de alcance"; y la corrección del tipeo "Aplicación KMP1" en el actor de C04, que se aplicó en `documentacion` y en la copia del servicio, que siguen idénticas.
- **2026-11-20 · Carpeta y rama:** `docs/sdd/c04-listar-categorias/`; se trabaja en `listar-categorias`, que el usuario creó desde `consultas-catalogo` (el milestone de las consultas).
- **2026-11-20 · `500` o `503` en C04 2.3:** se sigue el contrato §2.1, `500` `INTERNAL_ERROR`. El contrato elige una de las dos respuestas que permite el caso de uso, así que no es una contradicción. Se propuso en el análisis y el usuario no lo objetó.
- **D1 · Seguridad hasta la validación del JWT (2026-11-20): opción a), lo mínimo que necesita C04, sin JWT.** `GET /api/professional-categories` queda abierto y todo lo demás cerrado. El servicio todavía no lee el encabezado `Authorization`, así que C04 1.1 se cumple sin hacer nada más; CA-05 queda escrito desde ahora para cuando eso cambie. El usuario lo indicó así: como los endpoints públicos lo son, que usen lo que necesitan, y lo del JWT se trabaja en su funcionalidad (anotado arriba). Es lo mismo que hizo turnos en T01 y T02, que acá es solo un antecedente. Alternativas descartadas: b), adelantar la validación del JWT a C04, que construía algo que C04 no usa, cambiaba el orden acordado y traía a C04 el riesgo abierto de la clave pública de turnos; c), dejar todo abierto hasta la validación, que dejaba el servicio abierto por defecto: si C06 o C07 llegaran antes que la validación, quedarían públicos sin que ningún test fallara, contra el enunciado §9.
- **D2 · Orden de la lista (2026-11-20): opción a), alfabético en español.** No distingue mayúsculas ni tildes, la ñ va después de la n, y con dos nombres iguales va primero el `id` menor ("Datos y reglas de negocio"). El contrato dice "ordenadas por `name`" sin más detalle; los nombres de la cátedra llevan tildes y pueden repetirse. Es el mismo orden en cualquier entorno y es coherente con C05, que busca por nombre sin distinguir mayúsculas ni tildes. Al decidir, el usuario preguntó si se muestra el estado: sí, cada categoría trae `enabled`, y habilitadas y deshabilitadas van mezcladas por nombre, como dice el contrato; la app las marca (A04 paso 3). Alternativas descartadas: b), el orden que dé la base, que depende del idioma con que se creó y no desempata, así que un test podía pasar en un entorno y fallar en otro, y dos nombres iguales podían cambiar de lugar entre pedidos; c), por el código de cada carácter, que pone las mayúsculas antes que las minúsculas y las letras con tilde al final ("Óptica" después de "Traumatología").
- **D3 · Categoría sin descripción (2026-11-20): opción a), `"description": null`.** El campo está siempre, como en el ejemplo del contrato, y todos los elementos tienen la misma forma. Alternativas descartadas: b), omitir el campo, con la que una app que lo declare sin valor por defecto falla solo con las categorías sin descripción, un error que depende de los datos; c), texto vacío, que inventa un valor que la cátedra no mandó y no distingue "sin descripción" de "descripción vacía".
- **D4 · Límite de espera de la base (2026-11-20): opción c), sin límite propio.** Con una base caída, o colgada al pedir una conexión, el pool de conexiones deja de esperar a los 30 s (su valor por defecto), la lectura falla y C04 responde `500` (RF-05, CA-09). El `500` lo responde el servicio, no la base. Limitación aceptada: una consulta que ya estaba en curso cuando la base se colgó espera hasta que la base vuelva; la app corta por su lado y ofrece reintentar (A04 2.1). Es una falla rara con consecuencia menor, y para esos casos el usuario prefiere lo simple. Se dedujo de los valores por defecto y de lo que observó turnos con la base en pausa (D5 de T09), sin probarlo en catálogo: CA-09 lo comprueba. Hallazgo fuera de C04: la sincronización tiene la misma espera con la base colgada, y la hace con el candado tomado; si el usuario quiere tratarlo, va aparte. Alternativas descartadas: a), un límite general para la base, como turnos, que también cortaba la sincronización y cambiaba el comportamiento de C01 a C03 en ese caso extremo, dentro de un caso que no los toca; b), un límite solo para la consulta de C04, que depende de que la base atienda la cancelación y probablemente no cubre la base colgada.
- **D5 · CORS (2026-11-20): opción a), fuera de C04.** Va con la validación del JWT, donde se decide toda la configuración de seguridad junta, como hizo turnos (decisión 007). Hasta entonces no hay configuración, así que ningún navegador de otro origen puede leer las respuestas; la app Android no lo necesita. Alternativa descartada: b), una lista de orígenes ya en C04, vacía por defecto, que hoy no cambiaba nada y obligaba a tocar lo mismo dos veces.
