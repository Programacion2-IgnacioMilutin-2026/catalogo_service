# SPEC: C02 — Sincronizar catálogo incrementalmente

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
| Caso de uso C02 (`docs/casos_de_uso/catalogo_y_sincronizacion_usecases.md`) | Pasos 1 a 9, alternativos 1.1, 2.1, 3.1, 4.1, 4.2, 4.3, 5.1, 5.2, 6.1, 6.2, 6.3 y 7.1, precondición y postcondición. Pasos 7 y 9 y alternativo 5.1 ajustados por D1 y D2; alternativo 4.3, nuevo, y 5.2 ajustado por D3; paso 1 (suma `eventType`) por D6; 2.1 ("en el log") por D8 |
| Caso de uso C03 (mismo archivo) | Su procedimiento para ponerse al día con Redis (`docs/sdd/c03-verificacion-periodica/`, RF-03 a RF-08 y RF-10 a RF-15), que C02 referencia en lugar de repetir (D14). C03 como red de seguridad de un aviso perdido o fallido (C02 2.1, 4.1 y 5.2) |
| Caso de uso C01 (mismo archivo) | Solo como lo que C02 pide en 4.3, 5.2, 6.1 y 6.2, y su resultado (`docs/sdd/c01-sincronizacion-completa/`) |
| Diagrama de secuencia (`docs/secuencias/catalogo_y_sincronizacion_secuencia.md`) | §2: la rama del aviso de Kafka (C02), el tramo común (con el `opt no local version` movido por D3) y el `opt notice from Kafka` del final |
| Contrato de catálogo (`docs/contratos/catalogo_y_sincronizacion_contrato.md`) | §4.4 (Kafka), con el consumer group ajustado por D4, y §4.3 (Redis, como en C03) |
| Contrato común (`docs/contratos/contratos_comunes.md`) | §1.1 (la sesión técnica vive en memoria y no viaja) y §3 (instantes en UTC) |
| Modelo de dominio (`docs/catalogo_y_sincronizacion_dominio.md`) | §2 y §3: `CatalogSyncState` (como en C03) y `ProcessedEvent`; reglas 9 y 10 |
| Decisión 003 (`docs/decisiones/003-que-guarda-el-estado-de-sincronizacion.md`) | Qué guarda el estado de sincronización y cuándo cambia |
| X01 (`docs/sdd/x01-cuenta-tecnica/`, `docs/decisiones/001-sesion-tecnica-con-la-catedra.md`) | Los datos de Kafka llegan con la sesión técnica (`kafkaBootstrapServers`, `kafkaConsumerGroupId`, `kafkaCatalogTopic`); el aviso de que la sesión quedó habilitada (D12) |
| Anexo de la cátedra (`documentacion/enunciado/INTEGRATION_REFERENCE-v2.md`) | §3 (datos externalizados), §4 (convenciones de Kafka), §15.1 a §15.3 (`CatalogUpdated`), §16 (diagrama de sincronización), §18.1 (mensaje duplicado), §18.2, §18.4 (reintentos limitados y observables) y §19 (lo que define el alumno) |
| Enunciado (`documentacion/enunciado/PROJECT_STATEMENT-v1.md`) | §4.1, §5 (punto 5, "mantener el catálogo actualizado mediante Kafka y Redis"), §6.2, §8 ("recepción duplicada de mensajes Kafka", "pérdida de notificaciones de catálogo", "reinicio", "datos de entrada inválidos"), §10.1 y §11 (evidencias "actualización incremental del catálogo" y "procesamiento idempotente de un mensaje duplicado") |
| Turnos, decisión 014 (`turnos_service/docs/decisiones/014-kafka-con-la-catedra.md`) | Solo como antecedente: lo comprobado del Kafka de la cátedra (§3.1 y §4) y las decisiones que tomó turnos. No es una fuente de verdad para catálogo |

## Qué construimos y para quién

<!-- Qué necesidad cubre, qué actor la inicia (la app KMP, otro servicio, el
tiempo o la cátedra) y qué podrá hacer o recibir. Describe el objetivo en
lenguaje de negocio. Resume y referencia: no copies la descripción del caso de uso. -->

Cuando la cátedra publica una versión nueva del catálogo, avisa por Kafka con un `CatalogUpdated`. El servicio de catálogo recibe ese aviso y pone su copia local al día enseguida, leyendo de Redis lo que cambió, o reconstruyéndola con la sincronización completa (C01) si no puede seguir de forma incremental (C02, descripción). El aviso no trae datos: solo dice que hay algo nuevo (contrato §4.4).

Lo inicia la cátedra; no hay endpoint ni persona. Ponerse al día con Redis es lo mismo que hace la verificación periódica (C03). Lo que agrega C02 es reaccionar al aviso en segundos, en lugar de esperar hasta el próximo ciclo de C03, y tratarlo como un mensaje: validarlo, no procesar dos veces el mismo y confirmarlo en Kafka. C03 sigue siendo la red de seguridad: recupera un aviso perdido o uno que no se pudo procesar.

## Situación actual

<!-- Qué existe hoy y es relevante (código, datos, otras funcionalidades ya
implementadas), la limitación que queremos resolver y los comportamientos
existentes que deben conservarse. Si todavía no hay código para esto, indícalo.
No describas la arquitectura. -->

Verificado el 2026-11-19, en la rama `sync-incremental` (creada por el usuario en `d455dbd`, el mismo commit de `sincronizacion-catalogo`, con C03 completo):

- X01, C01 y C03 están implementados. La sesión técnica ya trae los tres datos de Kafka y no queda habilitada si falta alguno. Hoy nadie se entera de que la sesión quedó habilitada: los demás la piden cuando la necesitan.
- C03 ya pone la copia al día con Redis: lee la metadata, compara con la versión local, aplica las versiones pendientes de a una (cada una con su resultado, en su propia unidad de trabajo) y, ante una discontinuidad, pide C01 y repite una sola pasada. Hoy ese procedimiento solo lo usa la verificación periódica.
- Hay una sola sincronización a la vez. Hoy, quien encuentra otra en curso no espera: la verificación se omite y C01 se descarta. "Esperar a que termine", como pide C02 1.1, no existe todavía.
- No hay nada de Kafka: el servicio no consume ningún topic y no guarda avisos procesados.
- Lo que debe conservarse: el comportamiento de C03 y de C01, y lo que guarda el estado de sincronización según la decisión 003.
- **Topic del catálogo, leído el 2026-11-19** por el usuario con un programa de solo lectura (un login técnico, la descripción del topic, la lectura de la historia sin consumer group y la consulta del consumer group sugerido, sin unirse; la salida se revisó sin secretos y se borró):
  - Una partición; retención de 7 días, con borrado.
  - 201 mensajes retenidos, del 2026-11-11 al 2026-11-19. Uno por hora (de 3596 a 3602 s entre mensajes), todos `CatalogUpdated`, con `source` `SCHEDULED` y `schemaVersion` 1.
  - Siempre los mismos seis campos del anexo §15.3, sin headers. La clave es igual a `newVersion` en todos. Versiones de 172 a 372, consecutivas, sin repetidos ni retrocesos.
  - El `eventId` es un UUID de 36 caracteres, no como el ejemplo del anexo. `occurredAt` trae nanosegundos.
  - El consumer group sugerido no existe: no tiene miembros ni posiciones guardadas. Hoy nadie consume con él, tampoco turnos.
- El Kafka de la cátedra no exige autenticación ni tiene control de acceso (comprobado por turnos, decisión 014 §3.1): cualquiera con acceso a esa red podría publicar en el topic.
- Las copias de `contratos_comunes.md` y de X01 (caso de uso y secuencia) son idénticas en `documentacion`, `turnos_service` y `catalog_service`; las de catálogo (casos de uso, contrato, dominio y secuencia) coinciden con `documentacion` (comparadas el 2026-11-19, y de nuevo después de cada cambio de esta SPEC).

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. Cada requisito indica el paso o flujo
alternativo del que proviene. No agregues requisitos que los documentos de
referencia no tengan sin consultarlo. -->

- **RF-01** · C02 precondición y paso 1; contrato §4.4; anexo §15.2 y §15.3 (D4, D10, D11, D12): el servicio consume el topic `kafkaCatalogTopic` de la sesión técnica, con el consumer group `kafkaConsumerGroupId` seguido de `-catalog`.
  - Empieza apenas la sesión queda habilitada, con el login del arranque o con uno posterior (D12.1).
  - Conserva los datos del primer login exitoso hasta reiniciar; si un login posterior trae otros datos de Kafka, deja un warning y sigue con los primeros (D12.2).
  - Sin una posición guardada, lee solo los avisos nuevos (D10). Con una posición guardada, sigue desde ella, incluidos los avisos publicados mientras el servicio estuvo apagado.
  - El servicio arranca y atiende aunque Kafka no responda. Si Kafka no responde, queda en el log y el consumo se reconecta solo, sin tocar el estado de sincronización (D11).
  - Los avisos se procesan de a uno, en el orden del topic.
- **RF-02** · C02 paso 2 y alternativo 2.1 (D6, D8): un aviso es válido solo si cumple las reglas de "Datos y reglas de negocio". Uno inválido se descarta sin leer Redis ni tocar la copia, el estado ni los avisos procesados; queda un warning en el log con el motivo, la posición y el `eventId` si se pudo leer, y se confirma el offset.
- **RF-03** · C02 paso 3 y alternativo 3.1; anexo §18.1; enunciado §6.2 (D9): si el `eventId` ya está registrado como procesado, no se repite ningún efecto (ni lectura de Redis ni escritura); queda en el log y se confirma el offset.
- **RF-04** · C02 alternativo 1.1 (D5): si hay otra sincronización en curso, de cualquier tipo, C02 espera a que termine, sin un tope propio, y después sigue desde el paso 4 leyendo otra vez la versión local y la metadata. Mientras C02 tiene la sincronización, una verificación de C03 se omite (RF-02 de C03), y la sincronización completa que pida C02 no se descarta.
- **RF-05** · C02 pasos 4 a 8 y alternativos 4.1, 4.2, 4.3, 5.1, 5.2, 6.1, 6.2, 6.3 y 7.1 (D1, D2, D3, D14): para ponerse al día con Redis, C02 hace exactamente lo que definen RF-03 a RF-08 y RF-10 a RF-15 de la SPEC de C03, leyendo "verificación" como "aviso": la sesión y su renovación, los reintentos, la comparación, la aplicación de cada versión con su resultado en su propia unidad de trabajo, la discontinuidad con una sola sincronización completa, la base vacía sin leer Redis, el catálogo al día, los fracasos sin cambiar el tipo, y la clasificación de las respuestas de Redis.
- **RF-06** · C02 paso 9 y alternativo 5.1; dominio, regla 10 (D9.1, D9.2, D13): cuando C02 termina bien (aplicó versiones, hizo una sincronización completa o encontró el catálogo al día), registra el `eventId` como procesado, con el instante, en una escritura propia, y confirma el offset. Si la base no acepta ese registro, un solo intento: error en el log y se confirma el offset igual, sin tocar el estado de sincronización. Los avisos procesados no se borran.
- **RF-07** · C02 alternativos 4.1, 4.2, 4.3, 5.2 y 7.1 (D9.3): cuando C02 termina en un fracaso, el fracaso queda registrado como en C03 (RF-05), el `eventId` no se registra y se confirma el offset. Una falla de la base fuera de la aplicación de una versión (al consultar el `eventId` o al leer la versión local) deja un error en el log, no toca el estado y confirma el offset. C02 nunca deja un aviso sin confirmar para reintentarlo: lo que no pudo hacer lo recupera C03.
- **RF-08** · anexo §18.4; contrato común §1.1 (D6): cada aviso deja en el log su `eventId`, su `newVersion` y el resultado (versiones aplicadas, al día, repetido, inválido o el motivo del fracaso). Lo que viene del mensaje se escribe recortado y sin caracteres de control. El log nunca contiene el JWT técnico ni la contraseña de Redis.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Incluye las partes de los
casos de uso que se dejan para otra funcionalidad. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Confirmadas por el usuario el 2026-11-19:

- Publicar en Kafka: el catálogo no publica (contrato §4.4).
- Kafka con autenticación: el de la cátedra no la exige (decisión 014 de turnos, §3.1) y `integration` no trae credenciales de Kafka. Si la cátedra empezara a pedirlas, C02 dejaría de recibir avisos y C03 seguiría cubriendo.
- Reconectarse con otros datos de Kafka sin reiniciar (D12.2).
- Limpiar los avisos procesados (D13).
- Cambios de comportamiento en C01 y C03. Compartir con C03 el código para ponerse al día con Redis es un cambio interno, que se decide en el PLAN, y C03 tiene que seguir cumpliendo su SPEC.
- El endpoint de C07, los endpoints de C04 a C06 y su seguridad.
- El Docker del servicio: etapa propia.

## Flujo de la operación

<!-- Cómo se inicia (request, evento o tarea programada), qué verifica y qué
resultado produce, incluidas las alternativas relevantes. Si el caso de uso ya
lo describe, referencia sus pasos en lugar de repetirlos y describe solo lo que
cubre esta funcionalidad. -->

1. Con la primera sesión técnica habilitada, el servicio empieza a consumir el topic del catálogo (RF-01).
2. Llega un aviso: se valida (C02 paso 2; RF-02). Si es inválido, log, offset y termina.
3. Se consulta su `eventId` (C02 paso 3; RF-03). Si ya se procesó, log, offset y termina.
4. Si hay otra sincronización en curso, se espera a que termine (C02 1.1; RF-04).
5. Se pone al día con Redis con el procedimiento de C03: sin versión local, sincronización completa sin leer Redis y una pasada; con versión local, metadata, comparación y versiones pendientes, o sincronización completa ante una discontinuidad (C02 pasos 4 a 8 y sus alternativos; RF-05).
6. Si terminó bien, se registra el `eventId` y se confirma el offset (C02 paso 9; RF-06). Si fracasó, se confirma el offset sin registrar el `eventId` (RF-07).

## Datos y reglas de negocio

<!-- Información que necesita la operación, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- **El aviso** (contrato §4.4; anexo §15.3; D6). Son obligatorios los seis campos del paso 1 de C02:
  - `eventId`: texto no vacío, de 255 caracteres como máximo, sin caracteres de control (el nulo incluido).
  - `eventType`: exactamente `CatalogUpdated`.
  - `newVersion`: entero no negativo. Se valida, pero no cambia el recorrido: el destino es siempre la `currentVersion` de Redis (D7).
  - `occurredAt`: un instante válido en UTC, con o sin fracción de segundo.
  - `source`: texto no vacío, con un largo máximo y sin caracteres de control; cualquier valor, no solo los ejemplos del anexo.
  - `schemaVersion`: el número 1.

  Un valor nulo, un JSON ilegible o que no es un objeto, o cualquiera de esos defectos, hacen inválido el aviso. Los campos desconocidos se ignoran. La clave del mensaje no se usa.
- **De dónde salen los datos de conexión:** el topic, los servidores y el nombre base del consumer group, de la sesión técnica (anexo §3; D4). Ninguno se fija en el código ni en la configuración.
- **Avisos procesados** (dominio §3, `ProcessedEvent`; regla 10): se identifican por el `eventId` y guardan el instante en UTC en que se terminó de procesar. Se registran solo cuando C02 termina bien, y no se borran (D13).
- **Idempotencia** (enunciado §6.2; anexo §18.1): un aviso repetido no repite efectos, por dos protecciones independientes: el `eventId` registrado (RF-03) y la comparación de versiones, que hace que aplicar de nuevo deje la copia igual (RF-05).
- **Versiones, Redis, tipos de error y estado de sincronización:** como en la SPEC de C03 ("Datos y reglas de negocio") y la decisión 003.

## Casos alternativos y de error

<!-- Adapta la tabla usando BACKEND_GUIDELINES.md. Cuando el caso de uso ya define
el flujo alternativo, referencia su ID en lugar de repetirlo. Añade escenarios
relevantes. Marca No aplica con su motivo cuando corresponda. Expresa resultados
observables (estado HTTP, código de error, estado final del recurso, evento
publicado), no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Entrada inválida | Aviso inválido: se descarta con un warning y el offset confirmado (C02 2.1; RF-02; D6, D8). Datos de Redis inválidos: como en C03 (RF-05) |
| Sin sesión o sin permiso | Sin sesión al llegar un aviso: como un ciclo de C03 sin sesión, una renovación y, si no hay, `SOURCE_UNAVAILABLE` (RF-12 de C03, por RF-05). Redis rechaza las credenciales: C02 4.2, como C03 (RF-05). Kafka no pide credenciales (Fuera de alcance) |
| Recurso inexistente o ajeno | Un `changes:{v}` o un id que no está: discontinuidad, como en C03 (RF-05). El catálogo solo lee el topic de su integración |
| Dependencia no disponible (Redis, API REST, Kafka) | Redis: C02 4.1 y 6.3, como C03 (RF-05). API REST: solo a través de C01. Kafka: el servicio arranca y atiende, log y reconexión sola; C03 mantiene el catálogo al día (RF-01; D11) |
| Timeout o espera excesiva | Los tiempos de Redis y del snapshot, como en C03 y C01. La espera a otra sincronización no tiene un tope propio, porque esa sincronización ya está acotada por sus propios tiempos (RF-04; D5) |
| Operación o mensaje repetido | Mismo `eventId`: sin efectos (RF-03). Mismo aviso con otro `eventId`, o uno ya cubierto: lee Redis y encuentra el catálogo al día (RF-05; D7). Un aviso reentregado por un corte antes de registrar su `eventId`: igual, sin repetir cambios (D9.1) |
| Evento tardío o fuera de orden | El aviso no decide qué se aplica: siempre hasta la `currentVersion` de Redis (D7). Un aviso viejo encuentra el catálogo al día |
| Falla de persistencia | Al aplicar una versión: C02 7.1, como C03 (RF-05). Al consultar el `eventId` o leer la versión local: log y offset (RF-07; D9.3). Al registrar el `eventId`: log y offset (RF-06; D9.2) |
| Solicitudes simultáneas | Una sola sincronización a la vez: C02 espera (RF-04); C03 se omite mientras C02 trabaja. Los avisos se procesan de a uno (RF-01). Las consultas ven la copia anterior o la nueva de cada versión, nunca una mezcla |
| Reinicio con operaciones pendientes | Un aviso no confirmado al cortarse el servicio se vuelve a entregar al arrancar y se procesa sin repetir efectos (D9.1). La verificación del arranque (C03) cubre lo publicado mientras el servicio estuvo apagado |
| Otros puntos aplicables de la guía | Seguridad: un aviso falso no puede meter datos en el catálogo, porque los datos salen de Redis o del snapshot; la validación y el log recortado cubren la base y el log (D6). Arranque: sin Kafka el servicio arranca (D11); sin sesión habilitada no consume, y empieza con el primer login exitoso (D12.1) |

**Puntos de la guía no aplicables y motivo:** autenticación de usuarios, autorización, validación de parámetros HTTP y paginación (C02 no tiene endpoint); publicación de eventos (el catálogo no publica).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de seguridad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Respetar el contrato de Documentacion y el anexo de la cátedra puede ser
una restricción; el diseño de los adaptadores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Respetar el contrato §4.4 y el anexo §15: el catálogo solo lee el topic de su integración y no publica nada.
- Los datos de Kafka se obtienen solo de X01: no se fijan en el código ni en la configuración (anexo §3).
- C03 tiene que seguir cumpliendo su SPEC sin cambios de comportamiento.
- Los tests automáticos no llaman a la cátedra real, ni a su Kafka, ni a su Redis, ni leen el `.env`. Toda prueba real la corre el usuario, después de confirmar la lista exacta de pedidos, y nunca con el consumer group del servicio.
- Aplicar la skill `hexagonal-vertical-slicing`. Cada excepción se analiza en el contexto de C02 (indicación del usuario del 2026-11-15).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados: cada flujo alternativo cubierto debería
tener un criterio. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dada una sesión habilitada con los datos de Kafka, cuando arranca el servicio, entonces consume el topic de la sesión con el consumer group `{kafkaConsumerGroupId}-catalog`; un aviso publicado después de que empezó a consumir se procesa, y uno publicado antes, sin una posición guardada, no.
- **CA-02 · RF-01:** Dado un login del arranque que deja la sesión degradada, cuando un login posterior la habilita, entonces el servicio empieza a consumir; y dado un login posterior con otros datos de Kafka, el consumo sigue con los primeros y queda un warning.
- **CA-03 · RF-01:** Dado un Kafka que no responde al arrancar, entonces el servicio arranca y atiende, y el estado de sincronización no cambia por eso; cuando Kafka vuelve, el consumo sigue desde la última posición confirmada y procesa los avisos pendientes.
- **CA-04 · RF-02:** Dado un aviso con cualquiera de estos defectos: valor nulo, JSON ilegible o que no es un objeto, un campo obligatorio ausente o vacío, un `eventId` de más de 255 caracteres o con un carácter de control, `eventType` distinto de `CatalogUpdated`, `newVersion` que no es un entero no negativo, `occurredAt` que no es un instante, `source` con caracteres de control o más largo que el máximo, o `schemaVersion` distinto de 1, cuando llega, entonces se descarta sin leer Redis ni cambiar la copia, el estado ni los avisos procesados, queda un warning con el motivo, se confirma el offset y el aviso siguiente se procesa.
- **CA-05 · RF-02:** Dado un aviso válido con `occurredAt` con nanosegundos o sin fracción de segundo, con campos desconocidos, o con un `source` que no está entre los ejemplos del anexo, cuando llega, entonces se procesa.
- **CA-06 · RF-03:** Dado un aviso cuyo `eventId` ya está registrado, cuando llega otra vez, entonces no se lee Redis, no cambia nada y se confirma el offset (evidencia "procesamiento idempotente de un mensaje duplicado" del enunciado §11).
- **CA-07 · RF-04:** Dada otra sincronización en curso, cuando llega un aviso, entonces espera a que termine; después vuelve a leer la versión local y la metadata, y aplica lo que se haya publicado mientras esperaba. Nunca hay dos sincronizaciones a la vez, y una verificación de C03 que toca mientras C02 trabaja se omite.
- **CA-08 · RF-05:** Dados los escenarios de los criterios CA-01 a CA-03, CA-05 y CA-07 a CA-14 de la SPEC de C03, cuando los dispara un aviso en lugar de la verificación, entonces el resultado en la copia, la versión local y el estado de sincronización es el mismo. En particular: atrasada con historial continuo, aplica en orden con un registro `INCREMENTAL` por versión; al día, no cambia el estado salvo para corregir un fracaso; discontinuidad, una sola sincronización completa y una segunda pasada; base vacía, sincronización completa sin leer Redis.
- **CA-09 · RF-06:** Dado un aviso que termina bien (aplicando versiones, con una sincronización completa o con el catálogo al día), entonces su `eventId` queda registrado con su instante y se confirma el offset; y si la base rechaza ese registro, queda un error en el log, se confirma el offset y el estado de sincronización no cambia por eso.
- **CA-10 · RF-06, RF-03:** Dado un corte del servicio después de aplicar la última versión de un aviso y antes de registrar su `eventId`, cuando el aviso se vuelve a entregar, entonces encuentra el catálogo al día, registra el `eventId` y no repite ningún cambio.
- **CA-11 · RF-07:** Dado un aviso que termina en fracaso (Redis no disponible o rechazando aun después de renovar, sin sesión, sincronización completa fallida, discontinuidad que persiste o falla de persistencia agotada), entonces el fracaso queda registrado como en C03, el `eventId` no se registra, se confirma el offset y el aviso siguiente se procesa.
- **CA-12 · RF-07:** Dada una falla de la base al consultar el `eventId` o al leer la versión local, cuando llega un aviso, entonces queda un error en el log, el estado de sincronización no cambia, se confirma el offset y el aviso siguiente se procesa.
- **CA-13 · RF-08:** En todos los escenarios automáticos, el log muestra cada aviso con su `eventId` y su `newVersion` y su resultado; un campo del aviso con saltos de línea o muy largo aparece recortado y sin caracteres de control; y el log no contiene el JWT técnico ni la contraseña de Redis de test.
- **CA-14 · RF-01 a RF-06:** Con la cátedra real (Kafka, Redis y API), corrido por el usuario con un consumer group de pruebas: al recibir un aviso real, la copia queda en la `currentVersion` de Redis y el `eventId` queda registrado (evidencia "actualización incremental del catálogo"); y el mismo aviso entregado otra vez no cambia nada. Cómo se provoca la segunda entrega y cómo se evita esperar hasta una hora el próximo aviso, en el PLAN.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Kafka de pruebas y cátedra simulada que informa ese Kafka; un aviso antes y otro después de que el servicio empieza a consumir | Consume con el grupo `-catalog`; procesa solo el posterior |
| CA-02 | Login del arranque degradado y uno posterior exitoso; después, un login con otros datos de Kafka | Empieza con el exitoso; sigue con los primeros datos y hay un warning |
| CA-03 | Kafka de pruebas detenido al arrancar y después disponible, con avisos publicados mientras tanto | El servicio atiende; estado sin cambios; los avisos se procesan al volver |
| CA-04 | Un aviso por cada defecto, seguido de uno válido | Cada uno descartado con su motivo; nada cambia; el válido se procesa |
| CA-05 | Un aviso por cada variante válida | Se procesan |
| CA-06 | El mismo aviso publicado dos veces | La segunda no lee Redis ni cambia nada; offset confirmado |
| CA-07 | Una sincronización en curso al llegar el aviso, con una versión publicada mientras tanto; y el ciclo de C03 durante un aviso | Espera; aplica la versión nueva; C03 omitido |
| CA-08 | Los escenarios de C03 indicados, disparados por un aviso | Mismo resultado que con la verificación |
| CA-09 | Un aviso que aplica versiones, uno con sincronización completa y uno al día; y una falla de la base al registrar el `eventId` | `eventId` registrado y offset confirmado; con la falla, error en el log, offset confirmado y estado sin cambios |
| CA-10 | Corte provocado entre la última versión y el registro del `eventId`; reentrega del aviso | Al día, `eventId` registrado, sin cambios repetidos |
| CA-11 | Un aviso por cada fracaso de la lista | Fracaso registrado; `eventId` no registrado; offset confirmado; el siguiente se procesa |
| CA-12 | Falla de la base al consultar el `eventId` y al leer la versión local | Error en el log; estado sin cambios; offset confirmado |
| CA-13 | Revisar la salida de los escenarios automáticos, con un aviso que trae saltos de línea y un texto largo | Avisos y resultados en el log; campos recortados y sin caracteres de control; sin el JWT ni la contraseña de Redis de test |
| CA-14 | E2E con la cátedra real, corrido por el usuario después de confirmar la lista de pedidos, con un consumer group de pruebas | Copia en `currentVersion`, `eventId` registrado; la segunda entrega no cambia nada |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

Para el PLAN, ya anotado:

- Compartir con C03 el código para ponerse al día con Redis (P9 de C03, D10 de C03): dónde vive, cómo se llama, qué devuelve y cómo se reparten los tests de C03.
- El consumer group de las pruebas E2E, con una variable que la aplicación no lea (D4), y cómo evitar esperar hasta una hora el próximo aviso real (D10).
- El tiempo máximo que Kafka espera entre dos lecturas del consumidor, con margen sobre el peor caso de la espera de D5.
- Que los warnings de reconexión del cliente de Kafka no llenen el log (D11).
- Apagar el consumo en los tests que no son de Kafka: la cátedra simulada informa servidores inventados.
- Cada propiedad ajustable nueva lleva su variable en el yaml, en el `.env.example`, en la tabla del `CLAUDE.md` y en `ApplicationYamlVariablesTest`.

Para el cierre:

- Actualizar el "por verificar" de la decisión 014 de turnos (§4) sobre el consumer group de catálogo, que es de otro repositorio: verlo con el usuario.
- Pasar lo comprobado del topic al `CLAUDE.md` ("Lo comprobado con la cátedra real").
- Preguntar al usuario si catálogo tiene su propio documento vivo de Kafka, como la decisión 014 de turnos.

Decisiones tomadas:

- **Aprobación (2026-11-19):** el usuario aprobó la SPEC junto con la lista de "Fuera de alcance". Los cambios de documentos de D1, D2, D3, D4, D6 y D8 ya estaban aplicados con su OK. El mismo día, durante el PLAN, aprobó el ajuste de D6 sobre los caracteres de control del `eventId` (ver D6).
- **2026-11-19 · Carpeta y rama:** `docs/sdd/c02-sincronizacion-incremental/`; se trabaja en `sync-incremental`, que el usuario creó desde `sincronizacion-catalogo`.
- **2026-11-19 · Lectura real del topic antes de la SPEC:** la corrió el usuario; sus resultados están en "Situación actual".
- **2026-11-19 · Compartir el código con C03:** el usuario propuso sacar de la verificación el procedimiento para ponerse al día con Redis, para que lo usen C02 y C03. Se decide en el PLAN (P9 de C03, D10 de C03). Esta SPEC define el comportamiento, y busca que sea el mismo en los dos casos (D1 a D3).
- **D1 · Dónde se registra el resultado (2026-11-19): opción a), como C03.** Cada versión registra su resultado (`INCREMENTAL`, `SUCCESS`, instante como último intento y último éxito) en la misma unidad de trabajo que sus datos y la versión local, como C03 (su D5) y la decisión 003. Al final, C02 solo registra el `eventId` y confirma el offset. Así la copia y su resultado nunca quedan en desacuerdo: si se aplican la 371 y la 372 y la 373 falla, el estado dice 372 con su éxito y el fracaso como último intento. El texto anterior del paso 9 era previo a D5 de C03. El usuario aprobó el texto y se aplicó en `documentacion` y en la copia del servicio, que siguen idénticas: el paso 7 suma el registro del resultado y el paso 9 queda con el `eventId` y el offset. La secuencia §2 y la decisión 003 ya lo decían. Alternativas descartadas: b), un registro al final, que dejaba la versión avanzada sin su resultado y obligaba a cambiar la secuencia §2, la decisión 003 y el procedimiento común; c), registrar por versión y además al final, una escritura de más que no agrega nada.
- **D2 · Catálogo al día (2026-11-19): opción a), como C03.** Con la versión local igual a `currentVersion`, C02 hace lo mismo que C03 (su D4): queda en el log, y en el estado se registra un intento `SUCCESS` solo si el último intento registrado había fallado, sin cambiar la versión local, el último éxito ni el tipo (decisión 003). El procedimiento queda igual para los dos. El usuario aprobó el texto y se aplicó en `documentacion` y en la copia del servicio: C02 5.1 usa las palabras del paso 6 de C03. La secuencia §2 ya lo decía. Lo de registrar el `eventId` en 5.1 se confirmó en D9. Alternativa descartada: b), no tocar el estado, que cambiaba la secuencia §2 (que es común) y sumaba una rama al procedimiento común, para dejar un error ya superado a la vista hasta el próximo ciclo de C03.
- **D3 · Sin versión local (2026-11-19): opción a), como C03.** Sin versión local, C02 pide la sincronización completa sin leer Redis y, si termina bien, sigue una sola vez desde el paso 4, como C03 (su D1). Sin versión local la metadata no cambia la decisión, y el snapshot solo usa la API REST: con la base vacía y Redis caído, la copia se carga igual. El procedimiento queda igual para los dos. Casi nunca le pasa a C02, porque la base vacía la carga C03 al arrancar. El usuario aprobó el texto y se aplicó en `documentacion` y en las copias del servicio, que siguen idénticas: C02 suma el alternativo 4.3 (con las palabras de C03 2.2) y 5.2 ya no nombra "no hay versión local"; en la secuencia §2, el `opt no local version` sale de la rama de C03 y pasa al tramo común, antes de leer Redis, y la rama de discontinuidad pasa a decir `too old or ahead of Redis (not verifiable)`. Con el OK del usuario, la SPEC de C03 lleva una nota de este cambio al final de su D1. Alternativa descartada: b), dejar C02 como estaba, que no cambiaba documentos pero dejaba un comienzo distinto en el procedimiento común y, con la base vacía y Redis caído, gastaba los reintentos de Redis sin cargar nada.
- **D4 · Consumer group (2026-11-19): opción a), un grupo propio con nombre fijo.** Catálogo consume con `kafkaConsumerGroupId` seguido de `-catalog`, sin configuración. Un consumer group lleva la cuenta de todos sus miembros aunque lean topics distintos: con el protocolo clásico, que es el que usa el cliente por defecto, compartirlo con turnos hacía que cada reinicio de un servicio, o un aviso que tarda demasiado en procesarse, frenara también los eventos del otro. La cátedra acepta cualquier nombre (decisión 014 de turnos, §4) y el de `integration` es una sugerencia (anexo §15.2); hoy ese grupo no existe. El nombre sigue saliendo del login (anexo §3). El usuario eligió primero c), un sufijo configurable, y volvió a a) para no complicarlo, al saber que turnos no hizo configurable su consumer group en la aplicación: la aplicación lee el mismo `.env` que la suite E2E, que no debe usar el grupo del servicio. El grupo de las pruebas E2E se define en el PLAN, con una variable que la aplicación no lea. El usuario aprobó el texto y se aplicó en el contrato §4.4, en `documentacion` y en la copia del servicio, que siguen idénticas. Queda por actualizar el "por verificar" de la decisión 014 de turnos (§4), que es de otro repositorio: se ve con el usuario al cerrar. Alternativas descartadas: b), el sugerido, configurable, que por defecto dejaba el acople; c), un sufijo configurable, que agregaba una variable sin uso; d), compartirlo como decía el contrato, que aceptaba que un servicio frene al otro.
- **D5 · Espera a otra sincronización en curso (2026-11-19): opción a), sin un tope propio, como dice C02 1.1.** Si al llegar un aviso hay otra sincronización en curso, C02 espera a que termine y recién entonces lee la versión local y la metadata (desde el paso 4), porque la otra pudo haber cambiado la versión local, o haber leído Redis justo antes de que se publicara la versión del aviso. Mientras espera, los avisos siguientes esperan en Kafka, en orden. La espera tiene un tope natural, porque todo lo que hace una sincronización tiene un tiempo máximo: lo normal es menos de un segundo, y el peor caso es una verificación que pide una sincronización completa con la cátedra fallando, unos 2 a 3 minutos (lectura de Redis con reintentos, snapshot con 5 intentos y sus esperas, y la segunda pasada). Kafka saca del grupo a un consumidor que tarda demasiado entre dos lecturas (5 minutos por defecto): si pasara, el aviso se volvería a entregar sin daño (D9.1). Ese tiempo se configura en el PLAN, con margen sobre el peor caso. Alternativas descartadas: b), un tope configurable, que cambiaba 1.1 y sumaba una variable para protegerse de una espera que ya está acotada; c), omitir como C03, que contradice 1.1 y dejaba una versión recién publicada hasta 5 minutos sin aplicar.
- **D6 · Qué se exige del mensaje (2026-11-19): opción c), todo validado, con tolerancia en lo que no importa.** Son obligatorios los seis campos del paso 1:
  - `eventId`: texto no vacío, de 255 caracteres como máximo y sin caracteres de control (el nulo incluido), para que la base nunca lo rechace (lección de turnos, decisión 014 §3.14) y para que se pueda escribir en el log desde cualquier slice sin inyectar líneas.
  - `eventType`: exactamente `CatalogUpdated`.
  - `newVersion`: entero no negativo.
  - `occurredAt`: un instante válido en UTC, con o sin fracción de segundo (los reales traen nanosegundos).
  - `source`: texto no vacío, con un largo máximo y sin caracteres de control, sin lista cerrada (el anexo §15.3 da solo ejemplos).
  - `schemaVersion`: el número 1.

  Un valor nulo, un JSON ilegible o que no es un objeto, o cualquiera de esos defectos, hacen inválido el mensaje (C02 2.1). Los campos desconocidos se ignoran. Además, en cualquier caso, todo lo que venga del mensaje y vaya al log se escribe recortado y sin caracteres de control. Motivo: el Kafka de la cátedra no tiene autenticación ni control de acceso (decisión 014 de turnos, §3.1), así que cualquiera con acceso a esa red podría publicar en el topic. Un aviso falso no puede meter datos en el catálogo, porque los datos siempre salen de Redis o del snapshot; lo que sí podría es inyectar líneas en el log o hacer fallar a la base, y eso es lo que cubren estas reglas. El usuario pidió validar los formatos por seguridad; acá, a diferencia de turnos, descartar un aviso bueno cuesta poco, porque C03 lo recupera en como mucho 5 minutos, y el formato real ya se conoce por la lectura del topic. Limitación aceptada: si la cátedra cambiara el formato de un campo, C02 descartaría todos los avisos hasta corregirlo, con el motivo en el log, y el catálogo se mantendría al día por C03. El largo máximo de `source` y el recorte del log se fijaron en el PLAN (P7): 64 y 100 caracteres. **Ajuste del 2026-11-19, con la SPEC ya aprobada (opción a, elegida y aprobada por el usuario):** al planificar la lectura (P7 del PLAN) se vio que el `eventId` validado se escribe en el log también desde `synchronization`, y que la regla original solo prohibía el carácter nulo: un salto de línea en un `eventId` válido podía inyectar una línea falsa. Ahora se prohíben todos los caracteres de control, y CA-04 suma ese defecto. Los `eventId` reales son UUID, así que no cambia nada para los avisos de la cátedra. Descartada: b), mantener la regla y recortar el `eventId` en cada log de cada slice, que obligaba a acordarse en cada línea nueva. Con el OK del usuario se sumó `eventType` a la lista del paso 1 de C02, en `documentacion` y en la copia del servicio, que siguen idénticas: estaba en el ejemplo del contrato §4.4 y del anexo, pero no en el caso de uso. Alternativas descartadas: a), exigir solo lo que se usa, que se apartaba del paso 1 sin una razón práctica; b), exigir los cinco campos y leer el formato solo de los que se usan, que el usuario descartó por seguridad (la protección del log se aplica igual en c).
- **D7 · `newVersion` (2026-11-19): opción a), solo se valida.** No cambia el recorrido: todo aviso válido y no procesado lee la metadata de Redis y compara con `currentVersion`, como dice C02. Un aviso que ya está cubierto (repetido con otro `eventId`, uno que C03 aplicó antes, uno viejo o uno falso con una versión baja) cuesta una lectura de Redis de más, unos 250 ms. Con D10 b) no hay ráfaga de avisos viejos al arrancar, así que esos casos son raros, y el usuario prefiere lo simple para fallas poco comunes con consecuencia menor. Alternativas descartadas: b), terminar sin leer Redis si la versión local ya es mayor o igual que `newVersion`, que sumaba un alternativo y una rama para ahorrar lecturas que casi no ocurren y no frenaba un aviso falso con una versión alta; c), aplicar solo hasta `newVersion`, que contradice C02 paso 5 y dejaba el catálogo atrasado a propósito.
- **D8 · Dónde queda el error de un mensaje inválido (2026-11-19): opción a), solo en el log.** Un warning con el motivo, la posición del mensaje y su `eventId`, si se pudo leer. El estado de sincronización describe los intentos de sincronizar (decisión 003), y un mensaje mal armado no lo es: el catálogo puede estar al día. El usuario aprobó el texto y se aplicó en `documentacion` y en la copia del servicio: C02 2.1 dice "se registra el error en el log". Alternativas descartadas: b), `ERROR` `INVALID_DATA` en el estado, que mostraba en C07 un error en un catálogo sano, con un tipo de error que significa otra cosa, y que la verificación siguiente corregía en minutos; c), un dato nuevo en el dominio, que cambiaba el dominio y la base por algo que nadie lee.
- **D9 · El `eventId` y las fallas de la base fuera de una versión (2026-11-19): opción a) en las tres partes.** Sigue lo que ya dicen los documentos: el `eventId` se consulta en el paso 3 y se registra solo cuando C02 terminó bien (paso 9 y 5.1); en los fracasos (4.1, 4.2, 4.3, 5.2 y 7.1) se confirma el offset sin registrarlo.
  - **D9.1 · En una escritura propia, al terminar.** Si el servicio se corta entre la última versión y esa escritura, Kafka vuelve a entregar el aviso: se lee Redis, se encuentra el catálogo al día y se registra, sin repetir ningún efecto, porque la comparación de versiones ya protege. El procedimiento común con C03 no se entera del `eventId`. Descartada: b), en la transacción de la última versión, que metía el aviso en el procedimiento común y necesitaba igual una escritura propia cuando no se aplica ninguna versión o se aplica una completa.
  - **D9.2 · Si la base no acepta el registro del `eventId`:** un solo intento; si falla, error en el log y se confirma el offset igual. No se toca el estado de sincronización, porque la sincronización ya terminó bien. Si el aviso llegara de nuevo, se procesaría como nuevo, sin daño. Descartadas: b), reintentar con la política de la base, más espera para algo sin consecuencia; c), no confirmar el offset, que con la base caída repetía el aviso sin fin y frenaba el topic.
  - **D9.3 · Toda otra falla de la base fuera de una versión** (al consultar el `eventId` o al leer la versión local): error en el log, no se toca el estado, se confirma el offset y termina; C03 recupera el atraso. Es la misma regla que el caso de uso aplica a Redis no disponible (4.1): C02 nunca frena el topic. La falla al aplicar una versión sigue con sus reintentos (7.1). Descartadas: b), reintentar unas veces antes, sin beneficio porque C03 ya reintenta; c), no confirmar y dejar que Kafka lo reentregue, como turnos (decisión 014, §3.3), que lo necesitaba porque no podía perder el mensaje.
- **D10 · Consumer group sin posiciones guardadas (2026-11-19): opción b), solo los avisos nuevos (`latest`).** Aplica solo en el primer arranque (el grupo `-catalog` todavía no existe) o si Kafka borró las posiciones de un grupo inactivo (7 días por defecto; el valor de la cátedra no está verificado). En cualquier otro arranque, el grupo sigue desde la última posición confirmada, incluidos los avisos publicados mientras el servicio estuvo apagado. Lo publicado antes de la primera lectura no lo procesa C02, pero lo cubre la verificación del arranque (C03). Turnos eligió lo contrario (decisión 014, §3.4), porque perder un pedido de teléfono era grave; acá no se pierde nada. Efecto en las pruebas: un E2E con su propio grupo esperaría hasta una hora el próximo aviso; se resuelve en el PLAN con una configuración solo de tests. Alternativa descartada: a), desde el más antiguo (`earliest`), que procesaba unos 200 avisos que no cambian nada en cada arranque sin posición guardada (unos 50 s de lecturas de Redis con D7 a).
- **D11 · Kafka no disponible (2026-11-19): opción a), solo el log.** El servicio arranca y atiende aunque Kafka no responda: no es un requisito para arrancar. El cliente de Kafka se reconecta solo; cuando vuelve, el consumo sigue desde la última posición confirmada, sin perder avisos. No se toca el estado de sincronización, porque no es un intento de sincronizar (como en D8). Mientras tanto, C03 mantiene el catálogo al día, con hasta 5 minutos de atraso. En el PLAN se cuida que los warnings de reconexión del cliente no llenen el log. Alternativas descartadas: b), registrarlo en el estado, que no es un intento de sincronizar y C07 no tiene dónde mostrarlo; c), dejar de intentar después de N fallas y volver más tarde, lógica propia para algo que el cliente ya resuelve.
- **D12 · Cuándo empieza a consumir (2026-11-19): opción a) en las dos partes.**
  - **D12.1 · Apenas la sesión queda habilitada,** con el login del arranque o con uno posterior. Si el servicio arrancó degradado, empieza con el primer login exitoso, por ejemplo uno que pide un ciclo de C03. Es lo que hace turnos (decisión 014, §3.5). Descartadas: b), solo con el login del arranque, que dejaba el servicio sin C02 hasta un reinicio por un corte pasajero al arrancar; c), una tarea que revise la sesión cada tanto, una tarea más y una demora para lo que a) hace con el aviso del login.
  - **D12.2 · Si un login posterior trae otros datos de Kafka,** se conservan los del primero hasta reiniciar, con un warning. Limitación aceptada: si la cátedra mudara Kafka, C02 dejaría de recibir avisos hasta reiniciar, y C03 seguiría cubriendo. Descartada: b), reconectarse con los datos nuevos, que exige detener el consumo sin cortar un aviso a medias y volver a armarlo, para un caso que nunca se vio.
  - Consecuencia, sin decisión aparte: si la sesión se degrada con el consumo andando, el consumo sigue, porque no necesita el JWT. Cada aviso pide la sesión al leer Redis, como C03 (su RF-12).
- **D13 · Avisos procesados (2026-11-19): opción a), no se limpian.** Se registra uno por hora: unos 8.800 por año, menos de 1 MB. Con D10 b) y el offset siempre confirmado, un aviso repetido llega a los pocos minutos del original, nunca meses después. Limitación aceptada: la tabla crece sin límite, en una medida insignificante para la vida del proyecto. Descartadas: b), borrar los de más de N días, y c), conservar solo los últimos N, una pieza más con su configuración y sus tests para un problema que no existe a esta escala.
- **D14 · El procedimiento común con C03 (2026-11-19): opción a), se referencia.** RF-05 remite a RF-03 a RF-08 y RF-10 a RF-15 de la SPEC de C03, leyendo "verificación" como "aviso", y esta SPEC escribe solo lo propio de C02: la entrada (RF-01 a RF-03), la espera (RF-04) y la salida (RF-06 y RF-07). D10 de C03 lo previó ("podrá referenciar este"), y con el código compartido es lo coherente: un cambio en el procedimiento cambia a los dos. Las dos definiciones se compararon al escribir esta SPEC, como pedía D10 de C03; sus diferencias (base vacía, catálogo al día y registro del resultado) se resolvieron en D1 a D3 a favor de lo que ya hacía C03, así que C03 no cambia. Alternativas descartadas: b), copiar los textos de los RF, que arriesgaba dos definiciones distintas del mismo código; c), sacar el procedimiento a un texto común, que tocaba las referencias de la SPEC de C03, ya aprobada, sin ganar nada.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, las referencias apuntan a pasos y flujos
que existen, los flujos son coherentes, los puntos de backend relevantes están
cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
