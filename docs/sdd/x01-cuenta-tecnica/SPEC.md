# SPEC: X01 — Autenticar cuenta técnica y obtener configuración

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
| Caso de uso X01 (`docs/casos_de_uso/cuenta_tecnica_configuracion_usecases.md`) | Precondición, pasos 1 a 5, alternativos 2.1, 2.2 y 4.1, postcondición. La parte de la descripción "cada vez que la cátedra rechaza el JWT (`401`) en otro caso de uso", según D1 |
| Casos de uso de catálogo (`docs/casos_de_uso/catalogo_y_sincronizacion_usecases.md`) | Solo como origen de los pedidos de un login nuevo: C01 2.1 (`401` en el snapshot) y, según D2, C02 4.2 y C03 3.2 (Redis rechaza las credenciales) |
| Diagrama de secuencia (`docs/secuencias/cuenta_tecnica_configuracion_secuencia.md`) | §1 completo |
| Contrato de catálogo (`docs/contratos/catalogo_y_sincronizacion_contrato.md`) | §4.1 completo: login técnico, datos de `integration` que usa catálogo y la tabla de respuestas de la cátedra (la repetición de la llamada queda fuera, ver "Fuera de alcance") |
| Contrato común (`docs/contratos/contratos_comunes.md`) | §1.1: el JWT técnico es distinto del JWT del usuario, vive en memoria y no viaja a la app ni entre servicios |
| Anexo de la cátedra (`documentacion/enunciado/INTEGRATION_REFERENCE-v2.md`) | §2 (identidades), §3 (valores de acceso externalizados), §4 (convenciones), §5.1 (estados de `provisioningStatus`, `redisPassword` solo con `PROVISIONED`, guarda segura del JWT), §5.2 (login técnico), §13 (errores), §14.1 (los datos de Redis salen de `integration`) y §18.4 (reintentos limitados y observables) |

## Qué construimos y para quién

<!-- Qué necesidad cubre, qué actor la inicia (la app KMP, otro servicio, el
tiempo o la cátedra) y qué podrá hacer o recibir. Describe el objetivo en
lenguaje de negocio. Resume y referencia: no copies la descripción del caso de uso. -->

Al arrancar, el servicio de catálogo inicia sesión con la cuenta técnica del proyecto ante la cátedra y obtiene el JWT técnico y la configuración de la integración: los datos de Redis y de Kafka. Lo inicia el tiempo (el arranque) y lo usan los casos de uso que hablan con la cátedra (C01, C02 y C03): sin X01 completado no pueden sincronizar. Si la cátedra no responde o la cuenta está mal configurada, el servicio queda degradado y sigue atendiendo las consultas sobre la copia local (X01, descripción y postcondición).

## Situación actual

<!-- Qué existe hoy y es relevante (código, datos, otras funcionalidades ya
implementadas), la limitación que queremos resolver y los comportamientos
existentes que deben conservarse. Si todavía no hay código para esto, indícalo.
No describas la arquitectura. -->

Verificado el 2026-11-15, en la rama `cuenta-tecnica`:

- El servicio solo tiene el proyecto generado con Spring Initializr (`CatalogoApplication` y el test que levanta el contexto). No hay ningún slice, ningún caso de uso implementado ni cliente HTTP hacia la cátedra.
- `application.yaml` solo tiene el nombre de la aplicación: no hay configuración de la cátedra, ni lectura de un `.env`, ni conexión a la base. Tampoco hay `compose.yaml` ni `.env.example`. La estructura inicial se hizo sin SDD por indicación del usuario: lo que haga falta para arrancar el servicio y probar X01 lo crea esta funcionalidad, y se detalla en el PLAN.
- La cuenta técnica ya existe (el usuario la creó a mano) y el servicio de turnos la usa. Es la misma cuenta para los dos servicios (anexo §2 y §4).
- X01 ya está implementado en turnos (`turnos_service/docs/sdd/x01-cuenta-tecnica/` y sus decisiones 005 y 011). Sirve de modelo; no se copia código entre repositorios.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. Cada requisito indica el paso o flujo
alternativo del que proviene. No agregues requisitos que los documentos de
referencia no tengan sin consultarlo. -->

- **RF-01** · X01 paso 1, precondición; anexo §3: la URL base de la API de la cátedra y el `login` y la `password` de la cuenta técnica se leen de la configuración externa; no están en el repositorio. Si falta alguno, o si la URL no es una URL `http` o `https` válida, el servicio arranca igual, registra un warning que nombra la propiedad y no intenta el login: la integración queda con error de configuración (D1).
- **RF-02** · X01 paso 2; contrato §4.1: al arrancar, el servicio hace el login técnico (`POST /api/authenticate` con `username`, `password` y `rememberMe` en verdadero).
- **RF-03** · X01 pasos 3 a 5; contrato §4.1: con `200` y `provisioningStatus` `PROVISIONED`, guarda en memoria el `id_token` y los datos de la integración que usa catálogo (`redisHost`, `redisPort`, `redisUsername`, `redisPassword`, `kafkaBootstrapServers`, `kafkaConsumerGroupId` y `kafkaCatalogTopic`), además del `groupId` (D3), y queda con la integración habilitada. Ignora `redisReadNamespace`, `redisWriteNamespace`, `kafkaAppointmentActionsTopic` y `kafkaAppointmentPhoneTopic`.
- **RF-04** · X01 alternativo 4.1: con `200` y otro estado (`PENDING`, `FAILED`, `REVOKED` o desconocido), no habilita la integración y reintenta con límite.
- **RF-05** · X01 alternativo 2.1; contrato §4.1: con `401` en el login, o `403`/`404` `STUDENT_INTEGRATION_NOT_FOUND`, registra un error de configuración, no reintenta y queda sin integración habilitada.
- **RF-06** · X01 alternativo 2.2; contrato §4.1; anexo §18.4: ante timeout, corte de conexión, `503` o `500`, reintenta un número limitado de veces con espera creciente; cada intento queda registrado; mientras tanto el servicio queda degradado.
- **RF-07** · X01 postcondición y alternativo 2.2: mientras la integración no está habilitada, el servicio sigue en ejecución, atiende lo que no depende de la cátedra (las consultas C04 a C07, cuando existan) y ofrece a los demás casos de uso el estado de la integración para que no la usen. El servicio arranca aunque la cátedra no responda o la cuenta esté mal configurada, y el login técnico no demora el arranque (D1).
- **RF-08** · X01 descripción; contrato §4.1: otro caso de uso puede pedir un login nuevo cuando la cátedra rechaza el JWT guardado con un `401` en una operación (C01 2.1), cuando Redis rechaza las credenciales guardadas (C02 4.2, C03 3.2; D2) y cuando necesita la integración y está degradada (D1). Es un único pedido de renovar la sesión, con los mismos límites para los tres motivos (RF-10 a RF-12). Con la integración habilitada, los casos de uso usan la sesión guardada y no hay logins extra.
- **RF-09** · contrato común §1.1, contrato §4.1, anexo §2 y §5.1: el JWT técnico, la `password` de la cuenta técnica y la `redisPassword` nunca aparecen en logs, en respuestas ni en el repositorio; el JWT técnico y la configuración solo viven en memoria.
- **RF-10** · X01 alternativo 2.1 (D1): con error de configuración (RF-05 o configuración faltante, RF-01), no se envía ningún login más hasta reiniciar el servicio; los pedidos de RF-08 se responden al instante como integración no habilitada, sin llamar a la cátedra.
- **RF-11** · X01 alternativos 2.2 y 4.1 (D1): todo pedido de RF-08, con la integración habilitada o degradada, dispara un intento de login solo si pasó el intervalo mínimo configurable (60 segundos por defecto) desde el último; antes de eso se responde al instante con la sesión vigente, o como integración no habilitada si no la hay, sin llamar a la cátedra.
- **RF-12** · guía de backend (concurrencia) (D1): nunca hay dos logins técnicos a la vez; los pedidos que llegan mientras hay uno en curso esperan su resultado. Si la sesión que se rechazó ya no es la vigente, porque otro pedido la renovó, se devuelve la vigente sin un login nuevo.
- **RF-13** · X01 alternativo 2.2 (D1): un error inesperado durante un intento de login cuenta como un intento fallido por cátedra no disponible; la sesión nunca queda detenida en el estado de conexión inicial.
- **RF-14** · X01 paso 3 y alternativo 2.2; anexo §5.1 (D4): un `200` con `PROVISIONED` al que le falta el `id_token` o alguno de los siete datos de Redis y de Kafka de RF-03 se trata como cátedra no disponible: no habilita la integración, cuenta como intento fallido y se reintenta con límite (RF-06). El log nombra el dato que falta, sin valores. El `groupId` no es obligatorio para habilitar: solo se usa en el log.
- **RF-15** · X01 alcance ("ejecutado por cada servicio por su cuenta"); anexo §4 y §5.2; contrato común §1.1 (D5): catálogo obtiene su propio JWT técnico con la misma cuenta que turnos, sin compartirlo con él. Se comprueba contra la cátedra real que un login nuevo no invalida un JWT anterior de la misma cuenta. Si no conviven, se detiene el trabajo y se revisan X01 y los contratos antes de seguir.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Incluye las partes de los
casos de uso que se dejan para otra funcionalidad. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Confirmadas por el usuario el 2026-11-15:

- Repetir una operación contra la cátedra después de renovar el login (contrato §4.1, "repite la llamada, una sola vez"): corresponde a cada caso de uso que llama a la cátedra, desde C01.
- Conectarse a Redis y a Kafka con la configuración obtenida: corresponde a C03 y a C02.
- Que la verificación del arranque espere al login inicial o se entere de que la integración quedó habilitada (C03 paso 1): se define en C03.
- Consultar la integración con `GET /api/student/integration` (anexo §5.3): el login ya la devuelve.
- Registrar la cuenta técnica (anexo §5.1): ya se hizo, a mano, una sola vez.
- X01 en el servicio de turnos: ya está implementado allí.
- La seguridad de los endpoints del servicio: X01 no tiene endpoints; se define con el primero que aparezca.

## Flujo de la operación

<!-- Cómo se inicia (request, evento o tarea programada), qué verifica y qué
resultado produce, incluidas las alternativas relevantes. Si el caso de uso ya
lo describe, referencia sus pasos en lugar de repetirlos y describe solo lo que
cubre esta funcionalidad. -->

1. El servicio arranca y lee la configuración de la cátedra (RF-01).
2. Ejecuta los pasos 2 a 5 de X01 y el diagrama de secuencia §1 (RF-02 y RF-03).
3. Según la respuesta, aplica 2.1, 2.2 o 4.1 de X01 (RF-04 a RF-06). Hasta completar X01, el servicio está degradado (RF-07).
4. Más tarde, otro caso de uso puede pedir un login nuevo, y se repite este flujo desde el paso 2 (RF-08).

## Datos y reglas de negocio

<!-- Información que necesita la operación, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Request del login técnico: `username` (1 a 254 caracteres), `password` (4 a 100) y `rememberMe` en verdadero (anexo §5.2). Las credenciales configuradas se envían tal cual.
- Datos que guarda catálogo de la respuesta: `id_token` y los campos de la integración de RF-03. Todo en memoria: al reiniciar el servicio, se vuelve a hacer el login.
- La integración está habilitada solo si el último login respondió `200` con `PROVISIONED`. Cualquier otro resultado la deja no habilitada.
- Un login nuevo exitoso reemplaza juntos el JWT y la integración anteriores.
- Reintentos (RF-04 y RF-06): número máximo de intentos y espera creciente configurables, con valores por defecto definidos en el PLAN. Al agotarlos, el servicio queda degradado y vuelve a intentar el login recién cuando un caso de uso lo necesita, sin reintentos periódicos propios (D1). En catálogo, quien lo va a pedir en cada ciclo es la verificación periódica (C03).
- El `groupId` no se usa para conectarse a nada: el topic llega con su nombre completo, las claves de Redis que lee catálogo no lo llevan y la API lo deduce del JWT. Se guarda para identificar la integración en los logs (D3).
- Los logs registran el resultado de cada intento (éxito con el `groupId` y el estado, o el motivo del fallo) sin el JWT, la `password` ni la `redisPassword` (RF-09; anexo §18.4).

## Casos alternativos y de error

<!-- Adapta la tabla usando BACKEND_GUIDELINES.md. Cuando el caso de uso ya define
el flujo alternativo, referencia su ID en lugar de repetirlo. Añade escenarios
relevantes. Marca No aplica con su motivo cuando corresponda. Expresa resultados
observables (estado HTTP, código de error, estado final del recurso, evento
publicado), no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Entrada inválida | No aplica como request: X01 no tiene endpoint. Configuración faltante o URL inválida: arranca, warning con las propiedades, sin login (RF-01) |
| Sin sesión o sin permiso | Credenciales rechazadas (`401`) o integración inexistente (`403`/`404` `STUDENT_INTEGRATION_NOT_FOUND`): X01 2.1, error de configuración registrado, sin reintentos, integración no habilitada (RF-05) |
| Recurso inexistente o ajeno | Ver la fila anterior (`404` `STUDENT_INTEGRATION_NOT_FOUND`) |
| Dependencia no disponible (cátedra) | `503`, `500` o sin conexión: X01 2.2, reintentos limitados con espera creciente; servicio degradado (RF-06, RF-07) |
| Dependencia no disponible (Redis, Kafka) | No aplica: X01 no se conecta a Redis ni a Kafka; solo guarda sus datos |
| Timeout o espera excesiva | Igual que la cátedra no disponible: cuenta como intento fallido y se reintenta con límite (RF-06) |
| Operación o mensaje repetido | Pedidos simultáneos de login producen uno solo (RF-12). Con error de configuración no se repite el login (RF-10); degradada, como máximo un intento por intervalo (RF-11) |
| Evento tardío o fuera de orden | No aplica: no hay eventos |
| Falla de persistencia | No aplica: X01 no persiste nada (todo en memoria) |
| Estado de provisión distinto de `PROVISIONED` | X01 4.1: integración no habilitada, reintento con límite (RF-04) |
| Respuesta `PROVISIONED` incompleta | Como la cátedra no disponible: integración no habilitada, reintento con límite, log con el dato que falta (RF-14) |
| Solicitudes simultáneas | Ver "Operación o mensaje repetido" |
| Reinicio con operaciones pendientes | Al reiniciar, el JWT y la configuración se pierden y X01 se ejecuta de nuevo |
| Reintentos agotados | Degradado; nuevo intento solo cuando un caso de uso lo necesita (D1) |

**Puntos de la guía no aplicables y motivo:** idempotencia de mensajes, estados de procesos, transacciones, persistencia y paginación: X01 no persiste datos ni consume mensajes.

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de seguridad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Respetar el contrato de Documentacion y el anexo de la cátedra puede ser
una restricción; el diseño de los adaptadores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Respetar el contrato §4.1 y el anexo §5.2: ruta, request, `rememberMe` en verdadero y lectura de `id_token` e `integration`.
- La URL de la cátedra, el `login` y la `password` se externalizan (anexo §3); nada de eso se versiona.
- El JWT técnico solo vive en memoria y nunca llega a la app ni a turnos (contrato común §1.1).
- Los tests automáticos no llaman a la cátedra real ni usan las credenciales reales: simulan la cátedra según el anexo. La integración real se comprueba con una prueba manual que corre el usuario, con las credenciales de su `.env` local, después de confirmar la lista de pedidos.
- Estructura parecida a la de turnos: un slice propio que maneja la comunicación con la cátedra (indicación del usuario del 2026-11-13). Aplicar la skill `hexagonal-vertical-slicing`.
- Esta funcionalidad crea los paquetes y la configuración que necesita; no hay una etapa previa de estructura (indicación del usuario del 2026-11-14).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados: cada flujo alternativo cubierto debería
tener un criterio. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-02, RF-03:** Dada una cátedra que responde `200` con `PROVISIONED`, cuando el servicio arranca, entonces envía `POST /api/authenticate` con las credenciales configuradas y `rememberMe` en verdadero, y queda con la integración habilitada, con el `id_token` y los datos de la integración de RF-03 disponibles en memoria.
- **CA-02 · RF-04:** Dada una cátedra que responde `200` con `PENDING`, `FAILED` o `REVOKED`, cuando el servicio arranca, entonces la integración no queda habilitada y el login se reintenta hasta el límite configurado.
- **CA-03 · RF-05:** Dada una cátedra que responde `401` al login, o `403`/`404` con `STUDENT_INTEGRATION_NOT_FOUND`, cuando el servicio arranca, entonces registra un error de configuración, hace un solo intento y la integración no queda habilitada.
- **CA-04 · RF-06:** Dada una cátedra que responde `503`, `500`, demora más que el timeout o rechaza la conexión, cuando el servicio arranca, entonces reintenta hasta el límite con esperas crecientes, registra cada intento, y si en un intento posterior responde `200` `PROVISIONED`, la integración queda habilitada.
- **CA-05 · RF-07:** Dada una cátedra caída o una cuenta mal configurada, cuando el servicio arranca, entonces termina de arrancar sin esperar al login y sigue en ejecución con la integración no habilitada.
- **CA-06 · RF-08, RF-12:** Dada una integración habilitada, cuando no hay rechazos, entonces los casos de uso obtienen la sesión guardada sin ningún login nuevo; y cuando varios piden el login a la vez por un rechazo, entonces se envía un solo login y todos obtienen la sesión nueva.
- **CA-07 · RF-09:** En todos los escenarios de este documento, los logs no contienen el JWT técnico, la `password` ni la `redisPassword`.
- **CA-08 · RF-01:** Dado que falta la URL, el `login` o la `password`, o que la URL no es `http`/`https` válida (entre comillas, sin protocolo o con otro protocolo), cuando el servicio arranca, entonces arranca, registra un warning que nombra la propiedad, no envía ningún login y la integración queda con error de configuración.
- **CA-09 · RF-02, RF-03:** Con la cátedra real y las credenciales del `.env` local, cuando el servicio arranca, entonces el log muestra el login exitoso con el `groupId` y `PROVISIONED`, sin el JWT ni la `redisPassword`.
- **CA-10 · RF-11:** Dada una integración degradada tras agotar los reintentos, cuando un caso de uso pide el login antes del intervalo mínimo, entonces la cátedra no recibe ningún request; y cuando lo pide después del intervalo, entonces se envía un único login y, si responde `PROVISIONED`, la integración queda habilitada.
- **CA-11 · RF-10:** Dada una integración con error de configuración, cuando los casos de uso piden el login varias veces, entonces la cátedra no recibe ningún request y cada pedido responde al instante que la integración no está habilitada.
- **CA-12 · RF-12:** Dado que un pedido ya renovó la sesión, cuando otro pedido informa el rechazo de la sesión anterior, entonces recibe la vigente y la cátedra no recibe otro login.
- **CA-13 · RF-11:** Dada una integración habilitada cuya sesión se renovó hace menos del intervalo mínimo, cuando llega otro pedido por un rechazo de la sesión vigente, entonces no se envía un login y se responde al instante.
- **CA-14 · RF-13:** Dado un intento de login que falla con un error inesperado, cuando corre el login inicial o una renovación, entonces cuenta como intento fallido y la sesión termina habilitada o degradada, nunca en el estado de conexión inicial.
- **CA-15 · RF-14:** Dada una cátedra que responde `200` con `PROVISIONED` pero sin el `id_token`, o sin alguno de los siete datos de Redis y de Kafka, cuando el servicio arranca, entonces la integración no queda habilitada, el login se reintenta hasta el límite y el log nombra el dato que falta; y si un intento posterior llega completo, la integración queda habilitada.
- **CA-16 · RF-15:** Dado un JWT técnico obtenido con un login, cuando se hace un segundo login con la misma cuenta, entonces la cátedra real sigue aceptando el primer JWT en una consulta de solo lectura, y también acepta el segundo.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Cátedra simulada que responde `200` `PROVISIONED`; arrancar | Request con las credenciales de test y `rememberMe: true`; integración habilitada con los datos de la respuesta |
| CA-02 | Cátedra simulada con cada estado distinto de `PROVISIONED` | Integración no habilitada; cantidad de intentos igual al límite |
| CA-03 | Cátedra simulada con `401`, con `403` y con `404` `STUDENT_INTEGRATION_NOT_FOUND` | Un solo intento; error de configuración en el log; integración no habilitada |
| CA-04 | Cátedra simulada con `503`, `500`, demora y conexión rechazada; luego `200` `PROVISIONED` | Intentos con espera creciente hasta el límite; habilitada si un intento posterior tiene éxito |
| CA-05 | Cátedra simulada caída y cuenta mal configurada; arrancar | El servicio arranca sin esperar y sigue en ejecución |
| CA-06 | Integración habilitada; pedir la sesión varias veces; luego pedidos simultáneos de login por un rechazo | Sin requests a la cátedra en lo primero; un único login en lo segundo, y todos reciben la sesión nueva |
| CA-07 | Revisar la salida de todos los escenarios automáticos | Sin el `id_token`, la `password` ni la `redisPassword` |
| CA-08 | Arrancar sin cada una de las tres propiedades, y con URLs inválidas | Arranca; warning con la propiedad; cátedra simulada sin requests |
| CA-09 | Prueba manual con la cátedra real, corrida por el usuario: un único `POST /api/authenticate` | Log con el `groupId` y `PROVISIONED`, sin el JWT ni la `redisPassword` |
| CA-10 | Integración degradada; pedidos antes y después del intervalo, con un reloj controlado en el test | Ningún request antes; un único login después |
| CA-11 | Integración con error de configuración; varios pedidos de login | La cátedra simulada no recibe requests; respuestas inmediatas |
| CA-12 | Renovar con la sesión A (pasa a B); luego pedir con la sesión A | Devuelve B; un solo login en total |
| CA-13 | Renovar; antes del intervalo, pedir con la sesión nueva, con un reloj controlado | Ningún login nuevo; respuesta inmediata |
| CA-14 | Cliente que lanza un error inesperado, en el login inicial y en una renovación | Intentos contados como fallidos; estado final degradado o habilitado |
| CA-15 | Cátedra simulada que responde `PROVISIONED` sin el `id_token` y sin cada dato de Redis y de Kafka; luego una respuesta completa | Integración no habilitada; intentos hasta el límite; log con el dato que falta; habilitada con la respuesta completa |
| CA-16 | Prueba con la cátedra real, corrida por el usuario, que solo muestra códigos HTTP: login A, snapshot con A, login B, snapshot con A y snapshot con B | Los cinco pedidos responden `200` |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

CA-16 se comprobó antes de aprobar (ver D5).

Decisiones tomadas:

- **Aprobación:** el usuario aprobó la SPEC el 2026-11-15, junto con la lista de "Fuera de alcance".
- **2026-11-15 · SDD:** X01 es la primera funcionalidad de catálogo con SDD. La estructura inicial se hizo sin SDD (2026-11-14).
- **D1 · Reglas del login (2026-11-15):** las mismas que en turnos (D1 a D4, P7 y D8 de su SPEC de X01; decisión 005 de turnos). Con la integración habilitada se usa la sesión guardada; hay un login nuevo solo ante un rechazo o cuando la integración está degradada y un caso de uso la necesita. Credenciales rechazadas, integración inexistente o configuración faltante son un error de configuración, sin más logins hasta reiniciar. Como máximo un login por intervalo mínimo (60 segundos, configurable), también con la integración habilitada, y nunca dos a la vez; si la sesión rechazada ya no es la vigente, se devuelve la vigente. El servicio arranca aunque la cátedra esté caída o falte configuración, con un warning que nombra lo que falta, y el login no demora el arranque. Agotados los reintentos, queda degradado sin reintentos periódicos propios. Una URL inválida cuenta como configuración faltante y un error inesperado como intento fallido. Alternativas descartadas: revisar cada regla por separado; sumar reintentos periódicos propios, que en catálogo no aportan porque C03 pide la sesión en cada ciclo.
- **D2 · Rechazo de credenciales de Redis (2026-11-15):** se define en esta SPEC. X01 ofrece un único pedido de renovar la sesión, que sirve para el `401` de la API y para el rechazo de Redis, con los mismos límites: el JWT y los datos de Redis llegan juntos en el mismo login. Alternativa descartada: dejarlo para C03, que obligaba a reabrir X01 y podía dejar límites distintos para cada motivo.
- **D3 · `groupId` (2026-11-15):** se guarda, como dice la postcondición de X01, y se usa para identificar la integración en los logs. El contrato §4.1 no cambia: su lista es de los datos que catálogo usa para conectarse. Alternativa descartada: no guardarlo, que dejaba el log del login sin identificar la integración.
- **D4 · Respuesta `PROVISIONED` incompleta (2026-11-15):** se trata como cátedra no disponible (RF-14): nunca queda habilitada una sesión que no sirve para sincronizar, y un problema pasajero de la cátedra se recupera solo. Alternativas descartadas: error de configuración, que obliga a reiniciar por un problema pasajero; habilitar igual, que hace aparecer el error en C02 o C03, lejos de su causa.
- **D5 · Convivencia con el JWT de turnos (2026-11-15):** se mantiene X01 como está (cada servicio hace su propio login) y se comprueba contra la cátedra real, sin suponerlo (RF-15, CA-16). El anexo §4 dice que los dos servicios usan "la misma cuenta técnica y el mismo JWT"; X01 dice que cada servicio hace el login por separado, el anexo §5.2 que el login "permite obtener otro JWT" y el contrato común §1.1 que el JWT técnico no viaja entre los servicios. El usuario entiende "la misma identidad". **Comprobado el 2026-11-15** con la cátedra real, en una corrida del usuario que solo mostró códigos HTTP: login A `200`; snapshot con A `200`; login B `200`, con un token distinto de A; snapshot con A después del segundo login `200`; snapshot con B `200`. Los dos JWT conviven: un login nuevo no invalida el anterior, así que X01 y los contratos no cambian. Alternativas descartadas: no comprobarlo; un único JWT compartido por configuración, que contradice X01 y lo ya implementado en turnos.
- **X01 2.1, "se informa al profesor":** el servicio registra un error de configuración claro; avisar al profesor es una acción manual del alumno, como en turnos.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, las referencias apuntan a pasos y flujos
que existen, los flujos son coherentes, los puntos de backend relevantes están
cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
