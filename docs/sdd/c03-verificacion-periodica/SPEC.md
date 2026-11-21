# SPEC: C03 — Verificar la versión del catálogo periódicamente

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
| Caso de uso C03 (`docs/casos_de_uso/catalogo_y_sincronizacion_usecases.md`) | Pasos 1 a 6, alternativos 2.1, 3.1, 3.2, 4.1, 4.2, 5.1 y 5.2, precondición y postcondición. Incluye el alternativo 2.2, nuevo por D1 (sin versión local, la sincronización completa va antes de leer Redis). "Registrar la verificación" (paso 6 y 4.1) según D4 |
| Caso de uso C02 (mismo archivo) | Solo los pasos 6 a 8 y los alternativos 6.1, 6.2, 6.3 y 7.1, que C03 usa como procedimiento para aplicar las versiones pendientes (C03 paso 5). Esta SPEC lo especifica para C03; la de C02 especifica el suyo (D10) |
| Caso de uso C01 (mismo archivo) | Solo como lo que C03 pide en 4.2 y 5.1, y su resultado (`docs/sdd/c01-sincronizacion-completa/`, RF-09 y RF-10) |
| Caso de uso C07 (mismo archivo) | Solo lo que lee de lo que deja C03: el resultado registrado (paso 3) |
| Diagrama de secuencia (`docs/secuencias/catalogo_y_sincronizacion_secuencia.md`) | §2, la rama de la verificación periódica (C03), con el `opt no local version (C03)` sumado por D1, y el tramo común, con el resultado registrado dentro de la unidad de trabajo de cada versión (D5), el catálogo al día (D4) y los fracasos sin cambiar el tipo (D11). §1, solo sus líneas de fracaso, ajustadas por D11 |
| Contrato de catálogo (`docs/contratos/catalogo_y_sincronizacion_contrato.md`) | §4.3 (Redis) y, para el rechazo de Redis, la idea de la tabla de §4.1 |
| Contrato común (`docs/contratos/contratos_comunes.md`) | §1.1 (la sesión técnica vive en memoria y no viaja) y §3 (instantes en UTC, horas locales) |
| Modelo de dominio (`docs/catalogo_y_sincronizacion_dominio.md`) | §1, §2 (`CatalogSyncState`, `SyncType`, `SyncResult`, `SyncErrorType`; no `ProcessedEvent`), §3 y reglas 1 a 9. Sin datos nuevos para la verificación (D4); `lastSyncType` redefinido por D11 |
| X01 (`docs/sdd/x01-cuenta-tecnica/`, `docs/decisiones/001-sesion-tecnica-con-la-catedra.md`) | Uso de la sesión técnica: obtenerla, pedir su renovación ante un rechazo de Redis, y la verificación periódica como quien vuelve a pedir la sesión en cada ciclo |
| Anexo de la cátedra (`documentacion/enunciado/INTEGRATION_REFERENCE-v2.md`) | §4 (convenciones), §14.1 a §14.5 (Redis), §16 (diagrama de sincronización), §18.2 (versión incremental perdida), §18.4 (reintentos limitados y observables) y §19 (lo que define el alumno) |
| Enunciado (`documentacion/enunciado/PROJECT_STATEMENT-v1.md`) | §4.1, §6.1, §6.2, §8 ("pérdida de notificaciones de catálogo", "interrupción temporal de la conexión", "reinicio"), §10 y §11 (evidencias "actualización incremental del catálogo" y "recuperación mediante snapshot luego de una discontinuidad") |

## Qué construimos y para quién

<!-- Qué necesidad cubre, qué actor la inicia (la app KMP, otro servicio, el
tiempo o la cátedra) y qué podrá hacer o recibir. Describe el objetivo en
lenguaje de negocio. Resume y referencia: no copies la descripción del caso de uso. -->

El servicio de catálogo comprueba, al arrancar y cada cierto intervalo, si su copia local está al día con la versión que la cátedra publica en Redis, y si no lo está la pone al día: aplicando en orden las versiones que faltan, o reconstruyéndola con la sincronización completa (C01) cuando no puede seguir de forma incremental (C03, descripción). Es la red de seguridad de la sincronización incremental (C02) ante un aviso de Kafka perdido.

Lo inicia el tiempo; no hay endpoint ni persona. Es también quien carga el catálogo por primera vez con la base vacía (decisión D1 de la SPEC de C01) y, hasta que exista C02, lo único que mantiene la copia al día. El resultado queda registrado para que C07 lo informe.

## Situación actual

<!-- Qué existe hoy y es relevante (código, datos, otras funcionalidades ya
implementadas), la limitación que queremos resolver y los comportamientos
existentes que deben conservarse. Si todavía no hay código para esto, indícalo.
No describas la arquitectura. -->

Verificado el 2026-11-18, en la rama `verificacion-periodica` (sale de `sincronizacion-catalogo`, en `bb650c9`):

- X01 está implementado. El login técnico inicial corre en un hilo aparte cuando el servicio terminó de arrancar; mientras corre, la sesión está "conectando" y no se puede pedir una renovación. La sesión trae los datos de Redis (`redisHost`, `redisPort`, `redisUsername`, `redisPassword`) y puede renovarse entregando la sesión rechazada (decisión 001).
- C01 está implementado: la sincronización completa se pide desde adentro del servicio y devuelve aplicada (con la versión), fallida o descartada; hay una sola sincronización a la vez, y quien ya la tiene puede pedir C01 desde adentro (`CLAUDE.md`, "Cómo pedir la sincronización completa").
- El estado de sincronización guarda la versión local, el último intento (instante, tipo, resultado y tipo de error) y el instante del último éxito. Hoy solo lo escribe C01 (`FULL`); nada lo lee todavía.
- No hay nada de Redis ni de tareas programadas. Hoy nadie pide la sincronización completa: con la aplicación levantada a mano, la base queda vacía (limitación aceptada en C01, D1).
- **Redis real, leído el 2026-11-18** por el usuario con un script de solo lectura (un login técnico, un `GET` del snapshot y lecturas de Redis; la salida se revisó sin secretos):
  - Conexión TCP sin TLS, Redis 7.4.11; autenticación con usuario y contraseña aceptada. **`PING` responde `NOPERM`**: el ACL no lo permite.
  - Unos 246 ms por cada ida y vuelta; conectar y autenticarse, unos 740 ms.
  - Metadata: `currentVersion` 333 y `oldestAvailableVersion` 234, sin TTL, iguales a las claves sueltas del anexo §14.2.
  - Existen `changes:234` a `changes:333` (las 100, sin huecos, incluida la de `oldestAvailableVersion`), con la forma del contrato §4.3. Las 100 cambiaban un solo profesional, siempre el mismo; ninguna categoría ni horario. Se publica más o menos una versión por hora: la ventana retenida cubre unos 4 días.
  - Los tres Hash tienen 8, 24 y 44 entradas, idénticas al snapshot de la misma versión, con el mismo formato y sin referencias rotas.
- Las copias de `contratos_comunes.md` y de X01 (caso de uso y secuencia) son idénticas en `documentacion`, `turnos_service` y `catalog_service`; las de catálogo (casos de uso, contrato, dominio y secuencia) coinciden con `documentacion` (comparadas el 2026-11-18).

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. Cada requisito indica el paso o flujo
alternativo del que proviene. No agregues requisitos que los documentos de
referencia no tengan sin consultarlo. -->

- **RF-01** · C03 paso 1: la verificación corre al arrancar el servicio y luego a intervalos configurables. La del arranque espera a que termine el login técnico inicial, como máximo un tope configurable (2 minutos por defecto); al terminar el login, o al cumplirse el tope, sigue con la sesión que haya: si no hay, como un ciclo sin sesión (D3). El servicio arranca y atiende sin esperar a la verificación (D2). El intervalo es de 5 minutos por defecto, configurable, y se cuenta desde que termina una verificación hasta que empieza la siguiente (D6).
- **RF-11** · C03 paso 2 y 4.2 (D1): si no hay versión local, pide la sincronización completa (C01) sin leer Redis. Si termina bien, sigue una sola vez desde el paso 3 para aplicar las versiones publicadas después del snapshot; si en esa pasada encuentra una discontinuidad, registra el error y termina (RF-06). Si C01 falla, termina sin registrar otro error (C01 ya lo registró) y se reintenta en el siguiente ciclo.
- **RF-02** · C03 paso 2 y alternativo 2.1; RF-09 de C01: si hay otra sincronización en curso, de cualquier tipo, la verificación se omite y termina; la siguiente ejecución la repite. Una verificación nunca se solapa con otra.
- **RF-03** · C03 paso 3, alternativos 3.1 y 3.2; contrato §4.3: lee la metadata de Redis con los datos de la sesión técnica vigente. Si Redis no responde, reintenta con espera creciente y un máximo de intentos; si rechaza las credenciales, pide la renovación de la sesión y repite la lectura una sola vez. Por defecto, 3 intentos con esperas de 1 y 2 s, 5 s para conectar y 2 s por comando, configurables aparte de los del snapshot y del login (D7). Las respuestas que los documentos no clasifican siguen RF-14 (D8).
- **RF-14** · contrato §4.3; C03 3.1, 3.2 y 5.1 (D8):
  - `WRONGPASS`, `NOAUTH` y `NOPERM`: como credenciales rechazadas (C03 3.2): una renovación y una repetición; si persiste, `INTEGRATION_REJECTED`.
  - `LOADING`, `BUSY`, `TRYAGAIN`, `MASTERDOWN`, timeout, corte de conexión y cualquier otro error de Redis: no disponible (C03 3.1): reintentos y, agotados, `SOURCE_UNAVAILABLE`.
  - Metadata ausente, sin `currentVersion` o `oldestAvailableVersion`, con un valor que no es un número entero no negativo, o con `currentVersion` menor que `oldestAvailableVersion`: estado no verificable, se trata como discontinuidad (C03 4.2; RF-06); si persiste en la segunda pasada, `INVALID_DATA`.
  - `changes:{v}` con `version` distinta de `v`, sin alguna de las tres colecciones, con ids que no son números, o guardado con otro tipo (`WRONGTYPE`): no interpretable, discontinuidad (C03 5.1).
  - Colecciones o campos desconocidos: se ignoran.
- **RF-12** · decisión 001; C03 precondición (D3): si no hay sesión técnica habilitada, pide una renovación sin sesión rechazada (X01 decide si intenta un login, según su intervalo mínimo y nunca con error de configuración). Si sigue sin sesión, no lee Redis, registra el fracaso (`SOURCE_UNAVAILABLE`) y termina; el siguiente ciclo lo vuelve a intentar.
- **RF-04** · C03 paso 4 y alternativos 4.1 y 4.2; anexo §18.2: compara la versión local con la metadata: igual a `currentVersion`, al día; menor que `currentVersion` y no menor que `oldestAvailableVersion`, atrasada con historial continuo; menor que `oldestAvailableVersion` o mayor que `currentVersion`, discontinuidad. Sin versión local no se llega a este paso (RF-11).
- **RF-05** · C03 paso 5; C02 pasos 6 a 8: con historial continuo, aplica las versiones pendientes de a una, en orden ascendente; cada una en su propia unidad de trabajo, que aplica sus cambios, avanza la versión local a esa versión y registra el resultado `INCREMENTAL` `SUCCESS`, con el instante como último intento y último éxito (dominio, regla 9; D5). Si una versión falla después de aplicar otras, lo aplicado queda con su registro y el fracaso queda como último intento (RF-07, RF-08). Antes de aplicar, se validan los datos de cada entidad (RF-15) y las referencias (RF-10).
- **RF-06** · C03 4.2 y 5.1; C02 6.1 y 6.2: ante una discontinuidad pide la sincronización completa (C01) dentro de su propia pasada. Si termina bien, repite una sola vez la lectura y la comparación (desde el paso 3); si en esa segunda pasada vuelve a encontrar una discontinuidad, registra el error y termina. Si C01 falla, termina sin registrar otro error (C01 ya lo registró).
- **RF-07** · C03 5.2; C02 6.3 y 7.1: si Redis falla al leer una versión o falla la persistencia, se deshace solo esa versión (la versión local queda en la última aplicada), se reintenta con la política de RF-03 y, agotados los intentos, se registra el error y termina.
- **RF-08** · C03 paso 6 y 4.1 (D4): cada verificación deja en el log la versión local, la `currentVersion` leída, el instante y el resultado. El estado de sincronización cambia solo cuando hubo trabajo o corrige uno anterior:
  - Versiones aplicadas: cada una en su unidad de trabajo (RF-05; D5). La sincronización completa la registra C01 (`FULL`).
  - Fracaso de la propia verificación (Redis caído o rechazando, sin sesión, persistencia, discontinuidad repetida): como último intento, `ERROR`, su tipo de error y el instante; no cambian la versión local, el último éxito ni el tipo (D11).
  - Catálogo al día, sin nada que aplicar: no toca el estado, salvo que el último intento registrado haya fallado; entonces lo registra como intento `SUCCESS` con su instante, sin cambiar la versión local, el último éxito ni el tipo (D11).
- **RF-13** · dominio §3, `lastSyncType` (D11): el tipo registrado es siempre el de la última sincronización que aplicó cambios. Un fracaso no lo cambia, tampoco el de la sincronización completa: cambia lo que hoy hace C01 al registrar un fracaso (SPEC de C01, RF-14 y CA-15, con su nota del 2026-11-18).
- **RF-09** · anexo §18.4; contrato común §1.1: cada verificación, cada intento fallido con su motivo y el resultado final (versiones local y remota, versiones aplicadas o motivo del fracaso) quedan en el log, sin el JWT técnico ni la contraseña de Redis.
- **RF-10** · dominio, reglas 3 y 4 (D9.2): antes de aplicar una versión, cada profesional afectado tiene que referenciar una categoría que exista en la copia local o entre las categorías de esa misma versión, y cada horario afectado, un profesional que exista en la copia local o entre los profesionales de esa versión; una referencia a una entidad deshabilitada es válida. Si falta, es estado no verificable: discontinuidad (RF-06). La clave foránea de la base queda como segunda protección.
- **RF-15** · C03 5.1; SPEC de C01, RF-02 (D9.1): cada entidad leída de su Hash se valida con las mismas reglas que el snapshot: categoría con `id`, `name` y `enabled` (`description` opcional); profesional con `id`, `categoryId`, `firstName`, `lastName` y `enabled`; horario con `id`, `professionalId`, `dayOfWeek` entre `MONDAY` y `SUNDAY`, `startTime` y `endTime` en `HH:mm` o `HH:mm:ss`, `slotDurationMinutes` y `enabled`; todos presentes y no vacíos. Además, el `id` del JSON coincide con el field del Hash. Se ignoran los campos desconocidos y no se leen `createdAt` ni `updatedAt`. Un JSON ilegible o con un defecto es no interpretable: discontinuidad (RF-06) y, si persiste en la segunda pasada, `INVALID_DATA`.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Incluye las partes de los
casos de uso que se dejan para otra funcionalidad. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Confirmadas por el usuario el 2026-11-18:

- El consumo de Kafka, el `eventId`, los eventos procesados y la espera del candado: C02 (pasos 1 a 4, 9 y alternativos 1.1, 2.1, 3.1 y 5.1).
- El endpoint de C07 y su lectura de Redis sin reintentos.
- Los endpoints C04 a C06 y la seguridad de los endpoints: C03 no tiene endpoints.
- Cambios a la sincronización completa (C01): C03 solo la pide. La única excepción es que un fracaso no cambia el tipo registrado (RF-13, D11).
- El Docker del servicio: etapa propia.

## Flujo de la operación

<!-- Cómo se inicia (request, evento o tarea programada), qué verifica y qué
resultado produce, incluidas las alternativas relevantes. Si el caso de uso ya
lo describe, referencia sus pasos en lugar de repetirlos y describe solo lo que
cubre esta funcionalidad. -->

1. El tiempo dispara la verificación: al arrancar, esperando el login inicial con un tope, y a intervalos (RF-01).
2. Si hay otra sincronización en curso, se omite (RF-02).
3. Si no hay versión local, sincronización completa sin leer Redis y, si termina bien, una sola pasada desde el paso 4 de este flujo (RF-11).
4. Se ejecutan los pasos 3 a 6 de C03 y la rama de la verificación periódica de la secuencia §2 (RF-03 a RF-08), con los alternativos 3.1, 3.2, 4.1, 4.2, 5.1 y 5.2.

## Datos y reglas de negocio

<!-- Información que necesita la operación, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Datos de Redis: los del contrato §4.3. De la metadata se usan `currentVersion` y `oldestAvailableVersion`; `publishedAt` no se lee. No se usan las claves sueltas del anexo §14.2 ni el namespace `alumnos:*`.
- Las versiones pendientes son de la versión local + 1 a `currentVersion`, y se aplican en orden ascendente. La versión local avanza solo cuando todos los cambios de esa versión quedaron aplicados (anexo §14.5; dominio, regla 9).
- Historial continuo: la versión local no es menor que `oldestAvailableVersion` (C03 paso 4; anexo §18.2). En la lectura real también existía `changes:{oldestAvailableVersion}`, pero se sigue la regla del anexo: con la versión local menor que `oldestAvailableVersion` se reconstruye con el snapshot.
- Los arrays de `changes:{v}` traen ids; el estado vigente de cada uno se lee de su Hash, con la misma forma que el elemento del snapshot. Lo deshabilitado sigue en los Hash y se aplica con `enabled` en falso (dominio, regla 2). La aplicación incremental no quita entidades de la copia: un id ausente de su Hash es una discontinuidad (contrato §4.3).
- Los `id` son los de la cátedra; no se guardan `createdAt` ni `updatedAt`; las horas son locales (dominio, reglas 1, 7 y 8).
- Tipos de error (dominio §3): `SOURCE_UNAVAILABLE` (Redis no responde tras los reintentos), `INTEGRATION_REJECTED` (Redis rechaza aun después de renovar), `INVALID_DATA` (estado no verificable que persiste en la segunda pasada) y `PERSISTENCE_FAILURE` (falla al guardar tras los reintentos). Sin sesión técnica, `SOURCE_UNAVAILABLE` (D3). Un fracaso no cambia el tipo registrado, que es el de la última sincronización que aplicó cambios (D11). Los casos que los documentos no clasifican, según RF-14 (D8).

## Casos alternativos y de error

<!-- Adapta la tabla usando BACKEND_GUIDELINES.md. Cuando el caso de uso ya define
el flujo alternativo, referencia su ID en lugar de repetirlo. Añade escenarios
relevantes. Marca No aplica con su motivo cuando corresponda. Expresa resultados
observables (estado HTTP, código de error, estado final del recurso, evento
publicado), no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Entrada inválida | `changes:{v}` inexistente o ilegible, o un id ausente de su Hash: discontinuidad (C03 5.1; RF-06). Metadata inválida, `version` distinta de `v` y demás casos de RF-14 (D8). Una referencia rota o una entidad con datos inválidos: discontinuidad (RF-10, RF-15) |
| Sin sesión o sin permiso | Redis rechaza las credenciales: una renovación y una repetición (C03 3.2; RF-03). Sin sesión técnica: una renovación y, si no hay, `SOURCE_UNAVAILABLE` (RF-12). `NOPERM`, como credenciales rechazadas (RF-14) |
| Recurso inexistente o ajeno | Ver "Entrada inválida": una clave o un id que no está es una discontinuidad |
| Dependencia no disponible (Redis) | C03 3.1 y 5.2 (RF-03, RF-07): 3 intentos con esperas de 1 y 2 s (D7). Errores pasajeros de Redis, igual (RF-14) |
| Dependencia no disponible (cátedra REST) | Solo a través de C01, que la maneja y registra su error (RF-06) |
| Timeout o espera excesiva | Como Redis no disponible (RF-03): 5 s para conectar y 2 s por comando (unas 8 veces la ida y vuelta medida). El peor caso de una lectura que nunca responde ronda los 20 s (D7). Con espera fija entre ciclos, una verificación larga nunca se solapa con la siguiente |
| Operación o mensaje repetido | Repetir la verificación con el catálogo al día no cambia nada. Aplicar de nuevo una versión deja la copia igual (los cambios son el estado vigente por id) |
| Evento tardío o fuera de orden | Versión local mayor que `currentVersion`: discontinuidad, sincronización completa (C03 4.2) |
| Falla de persistencia | Se deshace solo esa versión y se reintenta con la política de D7 (C03 5.2; RF-07); agotados, `PERSISTENCE_FAILURE`. Si la base tampoco acepta el registro del error, queda solo en el log, como en C01 (su D7) |
| Solicitudes simultáneas | Una sola sincronización a la vez: la verificación se omite si hay otra (RF-02). Las consultas ven la copia anterior o la nueva de cada versión, nunca una mezcla |
| Reinicio con operaciones pendientes | Si el servicio se detiene a mitad de una versión, esa versión no se confirma; al arrancar, la verificación sigue desde la última aplicada (RF-01) |
| Otros puntos aplicables de la guía | Tareas programadas: intervalo configurable y sin solaparse (RF-01, RF-02; D6). Arranque: el servicio arranca y atiende sin esperar a la verificación; la del arranque espera el login inicial con un tope (RF-01). Base vacía con Redis caído: se carga igual con el snapshot (RF-11) |

**Puntos de la guía no aplicables y motivo:** autenticación de usuarios, autorización, validación de parámetros y paginación (C03 no tiene endpoint); idempotencia de mensajes y offsets de Kafka (C02).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de seguridad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Respetar el contrato de Documentacion y el anexo de la cátedra puede ser
una restricción; el diseño de los adaptadores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Respetar el contrato §4.3 y el anexo §14: solo lectura sobre `catedra:sync:*`, con los datos de conexión de la sesión técnica.
- Los datos de Redis se obtienen solo de X01: C03 nunca hace el login técnico por su cuenta, y ni el JWT ni la contraseña de Redis aparecen en logs (contrato común §1.1; decisión 001).
- Los tests automáticos no llaman a la cátedra real, ni a su Redis, ni leen el `.env`. Toda prueba real la corre el usuario, después de confirmar la lista exacta de pedidos.
- Aplicar la skill `hexagonal-vertical-slicing`. Cada excepción se analiza en el contexto de C03 (indicación del usuario del 2026-11-15).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados: cada flujo alternativo cubierto debería
tener un criterio. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-04, RF-08:** Dada una copia con la versión local igual a `currentVersion`, cuando corre la verificación, entonces no se lee ningún `changes:{v}`, la copia no cambia y la verificación queda en el log; el estado no cambia si el último intento fue un éxito, y si fue un fracaso queda como intento `SUCCESS`, con la versión local, el último éxito y el tipo sin cambios.
- **CA-02 · RF-04, RF-05, RF-08:** Dada una copia atrasada con historial continuo (varias versiones pendientes, con cambios de categorías, profesionales y horarios, incluidos uno nuevo, uno modificado y uno deshabilitado), cuando corre la verificación, entonces se aplican en orden ascendente, la copia queda igual al estado de los Hash para los ids afectados, nada se quita de la copia, la versión local queda en `currentVersion`, y cada versión queda registrada como `INCREMENTAL` `SUCCESS` con el instante como último intento y último éxito.
- **CA-03 · RF-04, RF-06:** Dada una versión local menor que `oldestAvailableVersion`, o mayor que `currentVersion`, cuando corre la verificación, entonces se pide la sincronización completa y, si termina bien, se repite una vez la lectura y se aplican las versiones publicadas después del snapshot. Con la versión local igual a `oldestAvailableVersion`, en cambio, se sigue de forma incremental.
- **CA-04 · RF-02:** Dada otra sincronización en curso, cuando corre la verificación, entonces se omite: no lee Redis, no toca la copia ni registra un resultado, y queda en el log.
- **CA-05 · RF-11:** Dada una base sin versión local, cuando corre la verificación, entonces se pide la sincronización completa sin leer Redis antes; si termina bien, la pasada siguiente lee Redis y aplica las versiones posteriores al snapshot, o lo encuentra al día. Con Redis caído, la copia queda cargada igual con la versión del snapshot, y la pasada siguiente registra `ERROR` y `SOURCE_UNAVAILABLE` como último intento, con la versión del snapshot, su último éxito y el tipo `FULL` intactos.
- **CA-06 · RF-01:** Dado un login inicial que todavía no terminó, cuando corre la verificación del arranque, entonces espera a que termine y sigue con esa sesión; si el login no termina antes del tope, sigue como un ciclo sin sesión (RF-12). El servicio responde a otros pedidos mientras tanto.
- **CA-07 · RF-12:** Dada una sesión técnica no habilitada, cuando corre la verificación, entonces pide una renovación; si da una sesión, lee Redis con ella; si no, no lee Redis y registra `ERROR` y `SOURCE_UNAVAILABLE`, con la versión local, el último éxito y el tipo sin cambios.
- **CA-08 · RF-13:** Dado un último éxito de un tipo (`FULL` o `INCREMENTAL`), cuando falla una sincronización de cualquier tipo, completa o de la verificación, entonces el tipo registrado sigue siendo el del último éxito; y antes del primer éxito, un fracaso deja el tipo vacío.
- **CA-09 · RF-03, RF-07, RF-14:** Dado un Redis que no responde (timeout, conexión rechazada o cortada, o un error pasajero como `LOADING`), al leer la metadata o al leer una versión, cuando corre la verificación, entonces se reintenta con esperas crecientes hasta 3 intentos; si un intento posterior responde, sigue normalmente; si se agotan, se registra `ERROR` y `SOURCE_UNAVAILABLE`, y la versión local queda en la última aplicada, con las versiones anteriores aplicadas y registradas.
- **CA-10 · RF-03, RF-14:** Dado un Redis que responde `WRONGPASS`, `NOAUTH` o `NOPERM`, cuando corre la verificación, entonces se pide una sola renovación y se repite con los datos de la sesión nueva; si vuelve el rechazo, no hay un tercer intento y se registra `ERROR` y `INTEGRATION_REJECTED`; si la renovación no da una sesión, se registra `ERROR` y `SOURCE_UNAVAILABLE`.
- **CA-11 · RF-06, RF-10, RF-14, RF-15:** Dado cualquiera de estos casos: metadata ausente o inválida; `changes:{v}` inexistente, ilegible, con `version` distinta de `v`, sin una colección o con otro tipo; un id ausente de su Hash; una entidad con JSON ilegible, un dato obligatorio ausente o vacío, un `dayOfWeek` o una hora inválidos, o un `id` distinto del field; o una referencia a una entidad que no está en la copia ni en la versión, cuando corre la verificación, entonces esa versión no se aplica (la copia y la versión local no cambian por ella) y se pide la sincronización completa.
- **CA-12 · RF-06, RF-11:** Dada una discontinuidad que persiste después de una sincronización completa exitosa, cuando la segunda pasada la vuelve a encontrar, entonces se registra `ERROR` y `INVALID_DATA` y termina, sin pedir otra sincronización completa en ese ciclo. Y dada una sincronización completa que falla, entonces la verificación termina sin registrar otro error que el de C01.
- **CA-13 · RF-07, RF-05:** Dada una falla de la base al aplicar una versión, cuando corre la verificación, entonces esa versión se deshace entera, las anteriores quedan aplicadas y registradas, y se reintenta con la política de RF-03; si un intento posterior funciona, sigue; si se agotan, se registra `ERROR` y `PERSISTENCE_FAILURE`; si la base tampoco acepta ese registro, el error queda en el log y el servicio sigue funcionando.
- **CA-14 · RF-15:** Dada una entidad válida sin `description`, con horas `HH:mm`, con campos desconocidos o con `createdAt` y `updatedAt` en cualquier formato, cuando se aplica su versión, entonces se aplica.
- **CA-15 · RF-01, RF-02:** Dado un intervalo configurado, cuando pasa el tiempo, entonces la verificación se repite con ese intervalo, contado desde que termina la anterior; una verificación que tarda más que el intervalo no se solapa con la siguiente.
- **CA-16 · RF-09:** En todos los escenarios automáticos, el log muestra cada verificación, cada intento fallido con su motivo y el resultado final, y no contiene el JWT técnico ni la contraseña de Redis de test.
- **CA-17 · RF-01 a RF-11:** Con la cátedra real (API y Redis), corrido por el usuario: con la base vacía, la verificación del arranque carga el snapshot y termina al día; con una copia cuya versión local está atrasada dentro de la ventana, aplica de forma incremental hasta `currentVersion` (evidencia "actualización incremental del catálogo" del enunciado §11); con una versión local menor que `oldestAvailableVersion`, reconstruye con el snapshot (evidencia "recuperación mediante snapshot luego de una discontinuidad"). En la cátedra real los cambios publicados son solo de profesionales: categorías y horarios se comprueban con la cátedra simulada.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Redis simulado con `currentVersion` igual a la local; último intento exitoso, y después uno fallido | Ningún `changes:{v}` leído; estado igual en lo primero; en lo segundo, intento `SUCCESS` con versión, último éxito y tipo sin cambios |
| CA-02 | Copia en una versión; Redis simulado con varias versiones pendientes que cambian las tres colecciones | Copia igual a los Hash para los ids afectados; nada quitado; versión en `currentVersion`; un registro `INCREMENTAL` `SUCCESS` por versión |
| CA-03 | Versión local menor que `oldestAvailableVersion`, mayor que `currentVersion` e igual a `oldestAvailableVersion` | Sincronización completa y una segunda pasada en los dos primeros; incremental en el tercero |
| CA-04 | Otra sincronización en curso al correr la verificación | Sin lecturas de Redis ni cambios; omitida en el log |
| CA-05 | Base vacía, con Redis disponible y con Redis caído | Snapshot pedido sin leer Redis antes; copia cargada; segunda pasada al día, o fracaso registrado con la versión y el tipo `FULL` intactos |
| CA-06 | Login inicial demorado, menos y más que el tope | Espera y sigue con la sesión; o sigue sin sesión al cumplirse el tope; el servicio atiende mientras tanto |
| CA-07 | Sesión no habilitada; renovación que da sesión, y que no la da | Lectura con la sesión nueva; o sin lectura y `SOURCE_UNAVAILABLE` |
| CA-08 | Un éxito `FULL` y uno `INCREMENTAL`, cada uno seguido de fracasos de cada clase; y un fracaso antes del primer éxito | Tipo igual al del último éxito; vacío antes del primero |
| CA-09 | Redis simulado con timeout, conexión rechazada, corte y `LOADING`, en la metadata y a mitad de las versiones; luego una respuesta | Hasta 3 intentos con esperas crecientes; sigue si responde; si no, `SOURCE_UNAVAILABLE` con lo aplicado intacto |
| CA-10 | Redis simulado con `WRONGPASS`, `NOAUTH` y `NOPERM`, una vez y dos veces; renovación sin sesión | Una renovación; repetición con las credenciales nuevas; `INTEGRATION_REJECTED` o `SOURCE_UNAVAILABLE` |
| CA-11 | Un escenario por cada defecto de CA-11 | La versión no se aplica; se pide la sincronización completa |
| CA-12 | Defecto que persiste después de una completa exitosa; y una completa que falla | Una sola completa e `INVALID_DATA`; o solo el error de C01 |
| CA-13 | Falla de la base a mitad de la segunda de tres versiones; luego éxito, y luego fallas hasta agotar; y falla también al registrar | Primera versión intacta y registrada; segunda deshecha; sigue o `PERSISTENCE_FAILURE`; en lo último, el error en el log |
| CA-14 | Una entidad por cada caso de CA-14 | Se aplica |
| CA-15 | Intervalo corto configurado; una verificación que tarda más que el intervalo | Repeticiones con el intervalo; nunca dos a la vez |
| CA-16 | Revisar la salida de los escenarios automáticos | Verificaciones, intentos y resultados en el log; sin el JWT ni la contraseña de Redis de test |
| CA-17 | E2E con la cátedra real, corrido por el usuario después de confirmar la lista de pedidos: base vacía, copia atrasada dentro de la ventana y copia anterior a `oldestAvailableVersion` | Copia al día en `currentVersion` en los tres; incremental en el segundo y snapshot en el tercero |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

Decisiones tomadas:

- **Aprobación (2026-11-18):** el usuario aprobó la SPEC junto con la lista de "Fuera de alcance". Los cambios de documentos de D1, D4, D5 y D11 ya estaban aplicados con su OK.

- **2026-11-18 · Carpeta y rama:** `docs/sdd/c03-verificacion-periodica/`; se trabaja en `verificacion-periodica`, que el usuario creó desde `sincronizacion-catalogo`.
- **2026-11-18 · Lectura real de Redis antes de la SPEC:** la corrió el usuario con un script de solo lectura; sus resultados están en "Situación actual" y en el `CLAUDE.md` ("Lo comprobado con la cátedra real"). El script y su salida se borraron.
- **D1 · Sin versión local (2026-11-18): opción a).** Se pide la sincronización completa sin leer Redis, y si termina bien se sigue una sola vez desde la lectura de Redis (RF-11). Sin versión local, la metadata no cambia la decisión: cualquier valor lleva a C01 (C03 4.2). Así la base vacía se carga aunque Redis esté caído, porque el snapshot solo usa la API REST (enunciado §6.1), y C04 a C06 no quedan en `503` por una dependencia que el snapshot no necesita. El usuario aprobó el texto el 2026-11-18 y se aplicó en `documentacion` y en las copias del servicio, que siguen idénticas: C03 suma el alternativo 2.2 y 4.2 ya no nombra "no hay versión local"; la secuencia §2 suma `opt no local version (C03)` en la rama de la verificación, y su rama de discontinuidad pasa a decir `no local version (C02)`. C02 5.2 no cambia (lo revisa su SPEC). Alternativas descartadas: b), respetar el orden actual, que con la base vacía y Redis caído dejaba el servicio sin catálogo y gastaba los reintentos de Redis en el arranque; c), Redis primero y C01 si se agotan los reintentos, que llegaba al mismo resultado que a) después de esperar todos los reintentos y con una rama más. **Nota del 2026-11-19:** D3 de la SPEC de C02 movió el `opt no local version` al tramo común de la secuencia §2, porque C02 también pide C01 sin leer Redis; la rama de discontinuidad dice ahora `too old or ahead of Redis (not verifiable)`. C03 no cambia de comportamiento.
- **D2 · La primera verificación y el login inicial (2026-11-18): opción a).** La verificación del arranque espera a que termine el login técnico inicial, como máximo 2 minutos por defecto (configurable; el peor caso del login ronda los 90 s: 5 intentos de hasta 15 s y 15 s de esperas), y después sigue con la sesión que haya (RF-01). Cumple "al arrancar, después de X01" (C03 paso 1) y carga el catálogo en cuanto hay sesión. Costo aceptado: una capacidad nueva en `catedra` para esperar el login inicial (dónde y cómo, en el PLAN), y el hilo de la tarea ocupado mientras espera. Alternativas descartadas: b), disparar la verificación cuando el login termina, que necesitaba un aviso entre slices y coordinar aparte el ciclo periódico; c), una demora inicial fija, que adivina cuánto tarda el login y demora cada arranque; d), omitir el ciclo si la sesión sigue "conectando", que dejaba el primer arranque sin catálogo durante un intervalo entero.
- **D3 · Ciclo sin sesión técnica (2026-11-18): opción a).** Pide una renovación sin sesión rechazada y, si sigue sin sesión, registra `SOURCE_UNAVAILABLE` sin leer Redis (RF-12), como C01 (su D5). Así una sesión degradada por un problema pasajero se recupera sola en un ciclo siguiente, como cuenta la decisión 001, y C07 ve que la integración falla. Limitación aceptada: con error de configuración, cada ciclo registra el mismo fracaso y deja una línea en el log; la causa está en el log de X01. Alternativas descartadas: b), renovar sin registrar el fracaso, que dejaba a C07 mostrando el último éxito mientras el catálogo no puede actualizarse; c), no pedir la renovación, que contradice la decisión 001 y dejaba una sesión degradada en el arranque así hasta reiniciar.
- **D4 · Qué es "registrar la verificación" (2026-11-18): opción c).** Siempre queda en el log; el estado cambia solo con trabajo real (versiones aplicadas, según D5, o un fracaso) o para corregir un fracaso anterior: una verificación al día registra un intento `INCREMENTAL` `SUCCESS` solo si el último intento registrado había fallado, sin tocar la versión local ni el último éxito (RF-08). Alternativas descartadas: a), solo el log, que dejaba a la vista un error ya superado hasta la próxima versión aplicada; b), registrar cada verificación como intento, que escribía en cada ciclo y tapaba con un `INCREMENTAL` sin cambios el `FULL` recién aplicado; d), atributos nuevos en el dominio, que cambiaban el dominio y la base por datos que nadie lee (el contrato de C07 no tiene dónde mostrarlos). El usuario aprobó el texto el 2026-11-18 y se aplicó en `documentacion` y en las copias del servicio: C03 paso 6 dice qué va al log y qué al estado, y 4.1 remite al paso 6. La línea de la secuencia §2 para el catálogo al día se ajustó junto con D11. **El tipo registrado ante un fracaso se reabrió y se resolvió en D11**: la opción propuesta acá, `INCREMENTAL`, quedó reemplazada.
- **D5 · Registro de la aplicación incremental (2026-11-18): opción a).** Cada versión registra su resultado (`INCREMENTAL`, `SUCCESS`, instante como último intento y último éxito) en la misma unidad de trabajo que sus datos y la versión local (RF-05), como C01 (su D7). Así los datos, la versión y el resultado nunca quedan en desacuerdo: si se aplica una versión y la siguiente falla, el último éxito es el de la aplicada y el último intento, el fracaso; si el servicio se corta a mitad, lo confirmado queda registrado. La secuencia §2 se ajustó (con el OK del usuario al elegir esta opción): el registro pasó dentro del bucle, en la línea de la unidad de trabajo, en `documentacion` y en la copia del servicio. Alternativa descartada: b), registrar una vez al terminar, como dibujaba la secuencia, que dejaba la versión avanzada sin su registro entre versiones o tras un corte, y necesitaba una regla extra para el fracaso a mitad del recorrido.
- **D6 · Intervalo por defecto (2026-11-18): opción b), 5 minutos, configurable** (RF-01). Para una demo o una prueba se baja por configuración. Una verificación al día cuesta una lectura de Redis (unos 250 ms); se publica más o menos una versión por hora y la ventana retenida es de unos 4 días. Hasta que exista C02 el catálogo se atrasa como mucho 5 minutos, y una sesión degradada se recupera en ese plazo (D3). Alternativas descartadas: a), 1 minuto, unas 1440 verificaciones y líneas de log por día sobre un Redis compartido, sin ganancia real con C02 en camino; c), 15 minutos, demasiado atraso y recuperación lenta mientras C03 sea lo único que actualiza el catálogo.
- **D11 · El tipo registrado ante un fracaso (2026-11-18): opción a).** El tipo registrado es siempre el de la última sincronización que terminó bien y aplicó cambios; un fracaso no lo cambia, sea de la verificación o de la sincronización completa, y una verificación que corrige un fracaso tampoco (RF-08, RF-13). Lo pidió el usuario, y coincide con lo que describe C07 ("el tipo, el resultado y la fecha de la última sincronización que aplicó cambios"). Limitación aceptada: el estado ya no dice de qué clase fue el intento fallido (un `SOURCE_UNAVAILABLE` puede venir de la API REST en C01 o de Redis en C03); eso queda en el log. Cuidado de lectura: un tipo `FULL` junto a un resultado `ERROR` no significa que falló una completa; lo aclaran el dominio y, después, C07. El usuario aprobó el texto y se aplicó en `documentacion` y en las copias del servicio, que siguen idénticas: dominio §3 (`lastSyncType`), secuencia §1 (cuatro líneas de fracaso: `(ERROR, …), type unchanged`) y secuencia §2 (el error de Redis y el catálogo al día). En la SPEC de C01, RF-14, CA-15 y "Datos y reglas de negocio" llevan una nota del cambio; el código de C01 (`CatalogSyncState`) y sus tests se ajustan en el TASKS de C03. Alternativas descartadas: b), guardar los dos tipos, con un atributo nuevo, una migración y un dato que el contrato de C07 no tiene dónde mostrar; c), dejar el tipo del intento (`INCREMENTAL` en la verificación, `FULL` en C01), que no era lo que pedía el usuario y dejaba a C07 sin el tipo de la última sincronización que aplicó cambios después de un fracaso.
- **D7 · Reintentos y timeouts de Redis (2026-11-18): opción b).** 3 intentos con esperas de 1 y 2 s, 5 s para conectar y 2 s por comando, configurables, en una política propia, aparte de la del snapshot y de la del login (RF-03). Una falla de persistencia al aplicar una versión sigue la misma política (C03 5.2); si la base tampoco acepta el registro del error, queda solo en el log, como en C01. Se eligió porque acá, a diferencia del snapshot, el ciclo de 5 minutos ya es un reintento barato: tres intentos cubren un corte breve sin ocupar el candado casi un minuto, lo que importará cuando C02 espere a la verificación en curso. Los 2 s por comando son unas 8 veces la ida y vuelta medida (246 ms). Limitación aceptada: un corte de más de unos 3 s registra un error, que el ciclo siguiente corrige (D4). Alternativas descartadas: a), los valores del snapshot (5 intentos, 1, 2, 4 y 8 s), con un peor caso de casi un minuto; c), un solo intento, que contradice C03 3.1. Al preguntar el usuario, se comprobó que la política del snapshot ya es configurable (`catalogo.catedra.snapshot.*` en `CatedraProperties`), aunque no está documentada; se documenta al cerrar C03, con las propiedades nuevas.
- **D8 · Respuestas que los documentos no clasifican (2026-11-18):** el usuario aprobó las reglas directas (RF-14) y eligió a) en las dos preguntas abiertas:
  - **D8.1 · `NOPERM`, opción a):** como credenciales rechazadas, con una renovación y una repetición. Si la cátedra rehízo la integración con otro usuario o con otro ACL, el login trae la configuración nueva y se recupera sin reiniciar; el login de más está acotado por X01 (como mucho uno por minuto). Alternativa descartada: b), `INTEGRATION_REJECTED` sin renovar, que nunca se recuperaba de una integración rehecha.
  - **D8.2 · Metadata inválida, opción a):** estado no verificable, así que sincronización completa, como dicen C03 4.2 y el enunciado §6.2; si persiste en la segunda pasada, `INVALID_DATA`. El catálogo se mantiene al día por el snapshot aunque Redis publique mal la metadata. Limitación aceptada: mientras dure el problema, un snapshot por ciclo (14,5 KB y menos de 1 s, cada 5 minutos). Alternativas descartadas: b), `INVALID_DATA` sin snapshot, que dejaba de actualizar el catálogo con la API REST funcionando; c), tratarla como no disponible, con un tipo de error engañoso (Redis sí respondió) y reintentos que no arreglan un dato mal publicado.
  - Reglas directas: lo mismo que hace C01 con lo no previsto (su D6). Un error pasajero se reintenta; un contenido que no se puede interpretar es una discontinuidad; lo desconocido se ignora.
- **D9.1 · Datos de cada entidad leída de Redis (2026-11-18): opción a).** Se validan con las reglas del snapshot de C01 (su RF-02), más que el `id` del JSON coincida con el field del Hash (RF-15). Un defecto hace la versión no interpretable: discontinuidad y sincronización completa; si persiste en la segunda pasada, `INVALID_DATA`. Es la misma regla que D8 para un `changes:{v}` mal formado (C03 5.1; enunciado §6.2). Si solo Redis tiene el dato mal, el snapshot por la API REST lo corrige; si también lo tiene mal, C01 lo rechaza y la copia queda intacta, igual que con b). En la lectura real los 76 valores eran idénticos al snapshot, así que probablemente salgan del mismo origen: el caso en que a) recupera puede ser raro, pero a) nunca termina peor que b). Limitación aceptada: mientras el dato siga mal, un snapshot por ciclo. Alternativa descartada: b), `INVALID_DATA` sin snapshot, que dejaba el catálogo trabado en la versión anterior aunque la API REST tuviera el dato bien, con una regla distinta de la de un `changes:{v}` mal formado.
- **D9.2 · Referencias de una versión (2026-11-18): opción a).** Se comprueban contra lo que quedaría después de aplicar la versión: la copia local más las entidades de esa misma versión (RF-10). Detecta el problema antes de escribir y con el tipo correcto (discontinuidad y, si persiste, `INVALID_DATA`). La aplicación incremental no borra, así que las referencias que ya estaban bien siguen bien. Alternativas descartadas: b), dejarlo a la clave foránea, que lo registraba como falla de persistencia y lo reintentaba sin sentido hasta `PERSISTENCE_FAILURE`; c), comprobar contra el Hash de Redis, que sumaba lecturas para llegar a la misma conclusión (si la entidad no está en la copia ni en la versión, la copia está incompleta igual).
- **D10 · El procedimiento incremental y C02 (2026-11-18): opción b).** Esta SPEC especifica el procedimiento solo para C03 (RF-05, RF-07, RF-10, RF-14 y D9). La SPEC de C02 especificará el suyo y podrá referenciar este. El usuario aceptó escribir dos veces la misma definición. Es una decisión sobre la documentación: si el código se comparte, se decide en el PLAN. Cuidado anotado: si en el PLAN C02 y C03 comparten el código del procedimiento, un cambio que pida la SPEC de C02 cambiaría también a C03; al escribir la SPEC de C02 hay que comparar las dos definiciones. Alternativa descartada: a), especificarlo acá como común y que C02 lo referencie.
- **2026-11-18 · Texto de decisión:** se escribe `docs/decisiones/003` sobre qué guarda el estado de sincronización (D7 de C01, y D4, D5 y D11 de C03), al cerrar esta SPEC.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, las referencias apuntan a pasos y flujos
que existen, los flujos son coherentes, los puntos de backend relevantes están
cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
