# SPEC: C01 — Sincronizar catálogo completo (snapshot)

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
| Caso de uso C01 (`docs/casos_de_uso/catalogo_y_sincronizacion_usecases.md`) | Pasos 1 a 6, alternativos 1.1 (según D2), 2.1, 2.2, 2.3, 4.1 y 5.1, y postcondición. De la precondición, solo la sesión técnica (según D5): las condiciones (a) a (c) las detectan C02 y C03 |
| Casos de uso C02 y C03 (mismo archivo) | Solo como quienes piden la sincronización completa (C02 5.2, 6.1 y 6.2; C03 4.2 y 5.1) y lo que esperan de C01: saber si terminó bien, para repetir una vez su pasada incremental |
| Casos de uso C04 a C07 (mismo archivo) | Solo lo que leen de lo que deja C01: "catálogo inicializado" (C04 2.1, C05 3.1, C06 4.1) y el resultado registrado (C07 paso 3) |
| Diagrama de secuencia (`docs/secuencias/catalogo_y_sincronizacion_secuencia.md`) | §1 completo |
| Contrato de catálogo (`docs/contratos/catalogo_y_sincronizacion_contrato.md`) | §4.2 (snapshot) y la tabla de respuestas de la cátedra de §4.1 |
| Contrato común (`docs/contratos/contratos_comunes.md`) | §1.1 (el JWT técnico vive en memoria y no viaja) y §3 (instantes en UTC, horas locales) |
| Modelo de dominio (`docs/catalogo_y_sincronizacion_dominio.md`) | §1, §2 (`CatalogSyncState`, `SyncType`, `SyncResult`, `SyncErrorType`; no `ProcessedEvent`), §3 y reglas 1 a 4, 7, 8 y 9 |
| X01 (`docs/sdd/x01-cuenta-tecnica/`, `docs/decisiones/001-sesion-tecnica-con-la-catedra.md`) | Uso de la sesión técnica: obtenerla y pedir su renovación ante un `401` |
| Anexo de la cátedra (`documentacion/enunciado/INTEGRATION_REFERENCE-v2.md`) | §4 (convenciones: horas `HH:mm` y `HH:mm:ss`, instantes en UTC), §6 (rol `ROLE_STUDENT_CLIENT`), §7 (snapshot), §13 (errores), §18.2 (recuperación con snapshot), §18.4 (reintentos limitados y observables) y §19 (lo que define el alumno) |
| Enunciado (`documentacion/enunciado/PROJECT_STATEMENT-v1.md`) | §4.1, §6.1, §8, §10.1 y §11 (evidencias "inicialización desde snapshot" y "recuperación mediante snapshot luego de una discontinuidad") |

## Qué construimos y para quién

<!-- Qué necesidad cubre, qué actor la inicia (la app KMP, otro servicio, el
tiempo o la cátedra) y qué podrá hacer o recibir. Describe el objetivo en
lenguaje de negocio. Resume y referencia: no copies la descripción del caso de uso. -->

El servicio de catálogo obtiene de la cátedra el catálogo completo (categorías, profesionales y horarios semanales, habilitados y deshabilitados) y con él reemplaza de forma consistente su copia local, guardando como versión local la del snapshot (C01, descripción). Es la base de todo lo demás: C04 a C06 consultan esa copia, y C02 y C03 aplican versiones a partir de la del snapshot.

No lo inicia una persona ni un endpoint. Lo piden C03 (al arrancar y en cada verificación) y C02 (ante un aviso de Kafka) cuando no se puede seguir de forma incremental (secuencia §1). Hasta que existan, lo piden solo los tests y el E2E (D1). El resultado queda registrado para que C07 lo informe.

## Situación actual

<!-- Qué existe hoy y es relevante (código, datos, otras funcionalidades ya
implementadas), la limitación que queremos resolver y los comportamientos
existentes que deben conservarse. Si todavía no hay código para esto, indícalo.
No describas la arquitectura. -->

Verificado el 2026-11-16, en la rama `sync-completa` (sale de `sincronizacion-catalogo`, la rama de la milestone, que sale de `cuenta-tecnica`):

- X01 está implementado: la sesión técnica vive en memoria y los demás casos de uso la piden y piden su renovación (`CLAUDE.md`, "Cómo usar la sesión técnica"). Nada más del servicio habla con la cátedra.
- No hay copia local del catálogo: ninguna tabla, ninguna migración (`src/main/resources/db/migration` está vacía) y Hibernate solo valida el esquema. Tampoco hay versión local ni registro de resultados.
- No existen C02 ni C03, así que hoy nadie pide la sincronización completa: la base sigue vacía aunque el servicio arranque (D1).
- **Snapshot real, leído el 2026-11-16** por el usuario con un script de solo lectura (un login técnico y un `GET` del snapshot; la salida se revisó sin secretos): `200` en 0,5 s, 14,5 KB, `snapshotVersion` 303. 8 categorías (todas habilitadas), 24 profesionales (3 deshabilitados) y 44 horarios (3 deshabilitados), de lunes a viernes, con turnos de 20 o 30 minutos. Ningún campo falta ni viene vacío (tampoco `description`), no hay campos no previstos, ids repetidos ni referencias rotas, y todas las franjas son coherentes. Las horas llegan como `HH:mm:ss`. Los instantes no tienen un formato fijo: `generatedAt` trae nanosegundos y un `updatedAt` trae microsegundos, mientras los demás vienen sin fracción.
- Las copias de `contratos_comunes.md` y de X01 (caso de uso y secuencia) son idénticas en `documentacion`, `turnos_service` y `catalog_service`; las de catálogo (casos de uso, contrato, dominio y secuencia) coinciden con `documentacion` (comparadas el 2026-11-16).

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. Cada requisito indica el paso o flujo
alternativo del que proviene. No agregues requisitos que los documentos de
referencia no tengan sin consultarlo. -->

- **RF-01** · C01 paso 2; contrato §4.2; anexo §7: pide el snapshot (`GET /api/synchronization/snapshot`) con el JWT técnico de la sesión vigente (X01). Si no hay sesión habilitada, pide una renovación sin sesión rechazada (X01 decide si intenta un login, según su intervalo mínimo); si sigue sin sesión, no pide el snapshot, registra el error (`SOURCE_UNAVAILABLE`) y termina (D5).
- **RF-02** · C01 pasos 3 y 4, alternativo 4.1; dominio regla 3: valida que la respuesta esté completa y sea consistente (D4):
  - `snapshotVersion` y las tres colecciones presentes; una colección vacía es válida (C04 2.2).
  - Cada entidad con sus datos presentes y no vacíos: categoría, `id`, `name` y `enabled` (`description` es opcional); profesional, `id`, `categoryId`, `firstName`, `lastName` y `enabled`; horario, `id`, `professionalId`, `dayOfWeek` entre `MONDAY` y `SUNDAY`, `startTime` y `endTime` en `HH:mm` o `HH:mm:ss`, `slotDurationMinutes` y `enabled`.
  - Ningún `id` repetido dentro de una colección.
  - Cada profesional con una categoría del snapshot y cada horario con un profesional del snapshot, también los deshabilitados.
  - No se revisan reglas propias de la cátedra sobre los horarios (`startTime` anterior a `endTime`, duración positiva). Se ignoran los campos desconocidos y no se leen `createdAt`, `updatedAt` ni `generatedAt`.

  Si no la supera, la descarta, no modifica la copia local, registra el error (`INVALID_DATA`) y termina.
- **RF-03** · C01 paso 5; anexo §7; dominio regla 9: en una única unidad de trabajo, reemplaza las tres colecciones locales por las del snapshot y registra `snapshotVersion` como versión local; la versión cambia solo si las tres colecciones quedaron aplicadas. La copia queda exactamente igual al snapshot: lo local que no viene en él se quita (D3).
- **RF-04** · C01 paso 6; dominio §3 (D7): registra el resultado de la sincronización en la misma unidad de trabajo que los datos y la versión: tipo `FULL`, `SUCCESS`, sin tipo de error, y el instante en UTC como último intento y como último éxito. Así los datos, la versión y el resultado nunca quedan en desacuerdo.
- **RF-05** · C01 alternativo 2.1; contrato §4.1: ante un `401`, pide la renovación de la sesión técnica (X01) y repite el pedido una sola vez con la sesión nueva. Si la renovación no da una sesión, registra el error (`SOURCE_UNAVAILABLE`); si el `401` vuelve con el JWT nuevo, registra el error (`INTEGRATION_REJECTED`). En los dos casos la copia no cambia y termina (D5).
- **RF-06** · C01 alternativo 2.2; contrato §4.1; anexo §18.4: ante timeout, corte de conexión, `503` o `500`, reintenta con espera creciente y un máximo de intentos: por defecto, 5 intentos con esperas de 1, 2, 4 y 8 s, 5 s para conectar y 10 s para leer, configurables aparte de los del login técnico (D8). Agotados, registra el error (`SOURCE_UNAVAILABLE`), conserva la copia local y termina.
- **RF-07** · C01 alternativo 2.3; contrato §4.1: ante un `403`, no reintenta, registra el error (`INTEGRATION_REJECTED`) y termina.
- **RF-13** · contrato §4.1, por familia (D6): cualquier otro `5xx` (`502`, `504`) se trata como `500` y `503` (RF-06); cualquier otro `4xx` (`400`, `404`, `429`), como `403` (RF-07); un `200` que no es JSON legible, como respuesta incompleta (RF-02, `INVALID_DATA`, sin reintento); un corte mientras se lee la respuesta, como corte de conexión (RF-06).
- **RF-08** · C01 alternativo 5.1 y postcondición de fracaso: si falla la persistencia, se revierte toda la unidad de trabajo, la copia y la versión anteriores quedan intactas, se registra el error (`PERSISTENCE_FAILURE`) y termina. Nunca queda un estado parcial. Si tampoco se puede registrar el error, queda solo en el log (D7).
- **RF-14** · dominio §3; C01 alternativos (D7): todo fracaso registrado (RF-01, RF-02, RF-05 a RF-08, RF-13) se guarda como último intento, en una escritura propia después de deshacer los cambios: tipo `FULL`, `ERROR`, su tipo de error y el instante. No cambia la versión local ni el instante del último éxito. Cada error queda además en el log (RF-12).
- **RF-09** · C01 alternativo 1.1; C02 1.1; C03 2.1 (D2): nunca hay dos sincronizaciones a la vez, de ningún tipo, completa o incremental: hay una sola "sincronización en curso" para C01, C02 y C03. La sincronización completa corre si no hay ninguna en curso, o dentro de la pasada de C02 o C03 que la pidió. Si hay otra en curso, se descarta: no pide el snapshot ni toca la copia, devuelve "descartada" a quien la pidió y queda en el log, sin registrarse como resultado (no hubo intento). Vale para una sola instancia del servicio.
- **RF-10** · C02 5.2 y C03 4.2: quien pidió la sincronización completa recibe su resultado (aplicada, con la versión; fallida; o descartada por 1.1) para decidir si repite su pasada incremental.
- **RF-11** · dominio reglas 1, 7 y 8; anexo §4: se conservan los `id` de la cátedra; no se guardan `createdAt` ni `updatedAt`; las horas de atención se guardan como hora local sin zona y se aceptan `HH:mm` y `HH:mm:ss`.
- **RF-12** · anexo §18.4; contrato común §1.1: cada intento, su motivo de fallo y el resultado final quedan en el log (con la versión y las cantidades aplicadas), sin el JWT técnico.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Incluye las partes de los
casos de uso que se dejan para otra funcionalidad. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Confirmadas por el usuario el 2026-11-17:

- Detectar cuándo hace falta la sincronización completa (precondición (a) a (c) de C01): lo hacen C02 y C03.
- Pedir la sincronización completa al arrancar (también en el primer arranque, con la base vacía) y periódicamente: C03 (D1).
- La pasada incremental que se repite después del snapshot (postcondición de éxito): C02 y C03.
- Qué hacen C02 y C03 cuando ya hay una sincronización en curso (C02 1.1 espera, C03 2.1 omite): sus SPEC. C01 deja la "sincronización en curso" para que la usen (D2).
- Leer Redis y consumir Kafka: C02 y C03.
- Los endpoints C04 a C07, incluido informar el resultado registrado (C07) y el `503` de "catálogo no inicializado".
- La seguridad de los endpoints del servicio: C01 no tiene endpoints.
- El Docker del servicio: etapa propia.

## Flujo de la operación

<!-- Cómo se inicia (request, evento o tarea programada), qué verifica y qué
resultado produce, incluidas las alternativas relevantes. Si el caso de uso ya
lo describe, referencia sus pasos en lugar de repetirlos y describe solo lo que
cubre esta funcionalidad. -->

1. Otro caso de uso del servicio pide la sincronización completa: C02 o C03; hasta que existan, los tests y el E2E (D1).
2. Si hay otra sincronización en curso que no es la de quien la pide, se descarta (C01 1.1, RF-09).
3. Se ejecutan los pasos 2 a 6 de C01 y la secuencia §1 (RF-01 a RF-04), con los alternativos 2.1, 2.2, 2.3, 4.1 y 5.1 (RF-02, RF-05 a RF-08).
4. Quien lo pidió recibe el resultado (RF-10).

## Datos y reglas de negocio

<!-- Información que necesita la operación, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Datos del snapshot: `snapshotVersion` y las tres colecciones del contrato §4.2. De cada entidad se guardan los atributos del dominio §3; `createdAt` y `updatedAt` no se guardan ni se leen (regla 7), igual que `generatedAt` (D4): la cátedra los manda con formatos distintos y nada los usa.
- Los `id` los asigna la cátedra y nunca se generan localmente (regla 1).
- Lo que la cátedra deshabilita viene en el snapshot y se conserva con `enabled` en falso; lo que deja de venir en el snapshot se quita de la copia (D3; regla 2 del dominio, con la aclaración de D3).
- Cada profesional referencia una categoría existente y cada horario un profesional existente (regla 3). El `enabled` de cada entidad es independiente (regla 4): una referencia a una entidad deshabilitada es válida.
- Las horas de atención son locales y sin zona; los instantes, en UTC (regla 8; contrato común §3).
- Hay un único estado de sincronización, y la versión local cambia solo en la misma unidad de trabajo que aplica los datos (regla 9). "Catálogo inicializado" significa que la versión local tiene valor (dominio §3).
- Tipo de error de cada fracaso (dominio §3): `SOURCE_UNAVAILABLE` (2.2, otro `5xx` y sin sesión técnica), `INTEGRATION_REJECTED` (2.3, otro `4xx` y `401` después de renovar), `INVALID_DATA` (4.1 y `200` ilegible) y `PERSISTENCE_FAILURE` (5.1). 
- El estado de sincronización guarda el último intento (instante, tipo, resultado y tipo de error), la versión local y el instante del último éxito (D7). Un éxito actualiza todo; un fracaso, solo el último intento.
- Un snapshot con una versión menor que la local se aplica igual: la cátedra es la fuente, y C02 5.2 y C03 4.2 piden el snapshot justamente cuando la versión local es mayor que `currentVersion`.

## Casos alternativos y de error

<!-- Adapta la tabla usando BACKEND_GUIDELINES.md. Cuando el caso de uso ya define
el flujo alternativo, referencia su ID en lugar de repetirlo. Añade escenarios
relevantes. Marca No aplica con su motivo cuando corresponda. Expresa resultados
observables (estado HTTP, código de error, estado final del recurso, evento
publicado), no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Entrada inválida | Snapshot incompleto o inconsistente: C01 4.1 (RF-02). Un `200` ilegible, igual (RF-13). Otro `4xx`: como `403` (RF-13) |
| Sin sesión o sin permiso | `401`: C01 2.1 (RF-05). `403`: C01 2.3 (RF-07). Sin sesión habilitada: una renovación y, si no hay, `SOURCE_UNAVAILABLE` (RF-01). `401` después de renovar: `INTEGRATION_REJECTED` (RF-05) |
| Recurso inexistente o ajeno | No aplica como recurso: el snapshot no tiene parámetros. Un `404` se trata como `403` (RF-13) |
| Dependencia no disponible (cátedra) | C01 2.2 (RF-06), también con `502`, `504` y otros `5xx` (RF-13) |
| Dependencia no disponible (Redis, Kafka) | No aplica: C01 no los usa |
| Timeout o espera excesiva | Como la cátedra no disponible (RF-06): 5 s para conectar y 10 s para leer. El peor caso, con los cinco intentos, ronda el minuto (D8) |
| Operación o mensaje repetido | Repetir la sincronización con el mismo snapshot deja la copia y la versión iguales. Dos pedidos a la vez: uno corre y el otro se descarta (RF-09) |
| Evento tardío o fuera de orden | Un snapshot con versión menor que la local se aplica igual (ver "Datos y reglas de negocio") |
| Falla de persistencia | C01 5.1 (RF-08). Si la base tampoco acepta el registro del error, queda solo en el log (D7) |
| Solicitudes simultáneas | Ver "Operación o mensaje repetido". Las consultas sobre la copia local ven la anterior o la nueva, nunca una mezcla (postcondición de C04 a C06) |
| Reinicio con operaciones pendientes | Si el servicio se detiene durante la sincronización, la unidad de trabajo no se confirma: quedan la copia y la versión anteriores, y se vuelve a pedir en el próximo arranque (C03) |

**Puntos de la guía no aplicables y motivo:** autenticación de usuarios, autorización, validación de parámetros y paginación (C01 no tiene endpoint); idempotencia de mensajes y offsets de Kafka (C02).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de seguridad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Respetar el contrato de Documentacion y el anexo de la cátedra puede ser
una restricción; el diseño de los adaptadores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Respetar el contrato §4.2 y el anexo §7, y tratar las respuestas de la cátedra según la tabla del contrato §4.1.
- El JWT técnico se obtiene solo de X01: C01 nunca hace el login técnico por su cuenta, y el JWT no aparece en logs (contrato común §1.1; decisión 001).
- Los tests automáticos no llaman a la cátedra real ni leen el `.env`. Toda prueba con la cátedra real la corre el usuario, después de confirmar la lista exacta de pedidos.
- Aplicar la skill `hexagonal-vertical-slicing`. Cada excepción se analiza en el contexto de C01; las de X01 no se adoptan en bloque (indicación del usuario del 2026-11-15).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados: cada flujo alternativo cubierto debería
tener un criterio. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01, RF-03, RF-04, RF-10:** Dada una base sin versión local y una cátedra que responde `200` con un snapshot válido, cuando se pide la sincronización completa, entonces la copia local tiene exactamente las categorías, los profesionales y los horarios del snapshot, habilitados y deshabilitados y con sus `id`; la versión local es `snapshotVersion`; el resultado registrado es `FULL` y `SUCCESS`, con la versión y el mismo instante como último intento y último éxito; y quien la pidió recibe "aplicada" con esa versión.
- **CA-02 · RF-03:** Dada una copia local con una versión anterior, cuando se aplica un snapshot con datos distintos, entonces la copia queda igual al snapshot y la versión local es la nueva: lo que cambió se actualiza, lo nuevo aparece, lo deshabilitado queda con `enabled` en falso y lo que no viene en el snapshot ya no está.
- **CA-03 · RF-02:** Dado un snapshot sin `snapshotVersion` o sin una de las colecciones, con un dato obligatorio ausente o vacío, con un `dayOfWeek` o una hora inválidos, con un `id` repetido, con un profesional cuya categoría no está o con un horario cuyo profesional no está, cuando se pide la sincronización completa, entonces la copia y la versión no cambian y se registra `ERROR` con `INVALID_DATA`.
- **CA-12 · RF-02:** Dado un snapshot válido con colecciones vacías, con una categoría sin `description`, con un horario cuyo `startTime` no es anterior a su `endTime`, con campos desconocidos o con `createdAt`, `updatedAt` y `generatedAt` en cualquier formato, cuando se pide la sincronización completa, entonces se aplica.
- **CA-04 · RF-05:** Dada una cátedra que responde `401` y, con el JWT renovado, `200`, cuando se pide la sincronización completa, entonces se pide una sola renovación, el segundo pedido lleva el JWT nuevo y el snapshot se aplica. Si con el JWT nuevo vuelve el `401`, no hay un tercer pedido, la copia no cambia y se registra `ERROR` con `INTEGRATION_REJECTED`; si la renovación no da una sesión, no hay segundo pedido y se registra `ERROR` con `SOURCE_UNAVAILABLE`.
- **CA-13 · RF-01:** Dada una sesión técnica no habilitada, cuando se pide la sincronización completa, entonces se pide una renovación; si da una sesión, el snapshot se pide con ella; si no, la cátedra no recibe ningún pedido del snapshot, la copia no cambia y se registra `ERROR` con `SOURCE_UNAVAILABLE`.
- **CA-14 · RF-13:** Dada una cátedra que responde `502` o `504`, cuando se pide la sincronización completa, entonces se reintenta como con `503`; con `400`, `404` o `429`, un solo pedido y `INTEGRATION_REJECTED`; con un `200` que no es JSON, un solo pedido y `INVALID_DATA`.
- **CA-05 · RF-06:** Dada una cátedra que responde `503`, `500`, demora más que el timeout o corta la conexión, cuando se pide la sincronización completa, entonces se reintenta con esperas crecientes hasta el límite; si un intento posterior responde `200`, el snapshot se aplica; si se agotan, la copia no cambia y se registra `ERROR` con `SOURCE_UNAVAILABLE`.
- **CA-06 · RF-07:** Dada una cátedra que responde `403`, cuando se pide la sincronización completa, entonces se hace un solo pedido, sin renovación, la copia no cambia y se registra `ERROR` con `INTEGRATION_REJECTED`.
- **CA-07 · RF-08:** Dada una falla de la base durante la escritura, cuando se pide la sincronización completa, entonces ni los datos ni la versión cambian y se registra `ERROR` con `PERSISTENCE_FAILURE`; si la base tampoco acepta ese registro, el error queda en el log y el servicio sigue funcionando.
- **CA-15 · RF-14:** Dado un catálogo con un éxito anterior, cuando una sincronización completa falla por cualquiera de sus motivos, entonces el último intento muestra `FULL`, `ERROR`, su tipo de error y el instante del fracaso, y la versión local y el instante del último éxito siguen siendo los anteriores.
- **CA-08 · RF-09, RF-10:** Dada una sincronización completa en curso, cuando llega otro pedido de sincronización completa, entonces el segundo no pide el snapshot ni toca la copia, devuelve "descartada", queda en el log y no registra un resultado, y el primero termina normalmente. Y dada una sincronización en curso tomada por quien después pide la sincronización completa (como harán C02 y C03), cuando la pide, entonces corre dentro de ella y no se descarta.
- **CA-09 · RF-11:** Dado un snapshot con horas `HH:mm` y `HH:mm:ss`, cuando se aplica, entonces las dos se guardan como la misma hora local, y `createdAt` y `updatedAt` no se guardan.
- **CA-10 · RF-12:** En todos los escenarios automáticos, el log muestra cada intento y el resultado final, y no contiene el JWT técnico de test.
- **CA-11 · RF-01 a RF-04:** Con la cátedra real, cuando se pide la sincronización completa, entonces la copia local queda con las cantidades de entidades del snapshot y la versión local es su `snapshotVersion` (evidencia "inicialización desde snapshot" del enunciado §11). La pide el E2E, dentro de la aplicación levantada contra la cátedra real (D1).

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Cátedra simulada con un snapshot válido; base vacía; pedir la sincronización | Copia igual al snapshot; versión y resultado registrados; resultado devuelto |
| CA-02 | Base con una copia y una versión previas; snapshot con una entidad modificada, una nueva, una deshabilitada y sin una de las anteriores (de cada colección) | Copia igual al snapshot nuevo; la que faltaba ya no está; versión nueva |
| CA-03 | Un snapshot por cada defecto de CA-03 | Copia y versión sin cambios; `ERROR` `INVALID_DATA` |
| CA-04 | Cátedra simulada: `401` y después `200`; `401` dos veces; `401` con una renovación que no da sesión | Una renovación; segundo pedido con el JWT nuevo y snapshot aplicado; `INTEGRATION_REJECTED` sin tercer pedido; `SOURCE_UNAVAILABLE` sin segundo pedido |
| CA-13 | Sesión no habilitada; renovación que da sesión, y renovación que no la da | Snapshot pedido con la sesión nueva; o ningún pedido del snapshot y `SOURCE_UNAVAILABLE` |
| CA-14 | Cátedra simulada con `502`, `504`, `400`, `404`, `429` y un `200` que no es JSON | Reintentos con los `5xx`; un solo pedido con los demás; tipo de error de RF-13 |
| CA-05 | Cátedra simulada con `503`, `500`, demora y conexión rechazada; luego `200` | Esperas crecientes hasta el límite; aplicado con el `200`; `SOURCE_UNAVAILABLE` si se agotan |
| CA-06 | Cátedra simulada con `403` | Un solo pedido; sin renovación; `INTEGRATION_REJECTED` |
| CA-07 | Falla de la base a mitad de la escritura; y falla también al registrar el error | Datos y versión anteriores; `PERSISTENCE_FAILURE`; en lo segundo, el error en el log |
| CA-15 | Un éxito y después un fracaso de cada tipo | Último intento con el fracaso; versión y último éxito sin cambios |
| CA-08 | Dos pedidos simultáneos con la cátedra simulada demorando el snapshot; y un pedido hecho desde dentro de una sincronización en curso | Un solo pedido del snapshot y un "descartada" en lo primero; en lo segundo, la sincronización corre |
| CA-09 | Snapshot con horas en los dos formatos | Misma hora local; sin `createdAt` ni `updatedAt` |
| CA-10 | Revisar la salida de los escenarios automáticos | Intentos y resultado en el log; sin el JWT de test |
| CA-12 | Un snapshot por cada caso de CA-12 | Se aplica; versión y resultado registrados |
| CA-11 | E2E con la cátedra real, corrido por el usuario después de confirmar la lista de pedidos: arranca la aplicación con la base vacía y pide la sincronización completa | Cantidades y versión iguales a las del snapshot |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

Decisiones tomadas:

- **Aprobación (2026-11-17):** el usuario aprobó la SPEC, junto con la lista de "Fuera de alcance", el atributo nuevo del dominio y el ajuste de la secuencia §1 (D7).

- **2026-11-16 · Carpeta y rama:** `docs/sdd/c01-sincronizacion-completa/`; se trabaja en `sync-completa`, que sale de `sincronizacion-catalogo` (la rama de la milestone, de la que saldrán también las de C02 y C03).
- **D1 · Quién pide la sincronización completa (2026-11-16):** C01 no tiene disparador propio: es una operación que piden C02 y C03, como dice la secuencia §1 (la tabla de C01 nombra al tiempo y a Kafka porque son los que disparan a C03 y a C02). Hasta que existan, la piden solo los tests y el E2E, que la llama dentro de la aplicación levantada contra la cátedra real (CA-11). La base vacía es la condición (a) de la precondición de C01, pero quien la detecta y pide el snapshot es C03 al arrancar (C03 4.2, "no hay versión local"). Limitación aceptada: hasta C03, la aplicación levantada a mano no carga el catálogo. Alternativas descartadas: un disparador provisorio al arrancar con la base vacía, que C03 reemplazaría enseguida y que tendría que esperar el login inicial; un endpoint de administración, que no está en el contrato y adelantaba la seguridad con `ROLE_ADMIN`.
- **D2 · Una sola sincronización a la vez (2026-11-16):** hay una sola "sincronización en curso" para C01, C02 y C03, de cualquier tipo (RF-09). El usuario lo considera necesario: con C02 y C03 funcionando, dos pedidos a la vez no son raros (C03 verifica al arrancar mientras Kafka entrega los avisos pendientes; un aviso puede llegar en medio de un snapshot), y dos sincronizaciones juntas pueden mezclar datos (por ejemplo, una versión incremental vieja que se confirma después de un snapshot más nuevo y deja la versión local hacia atrás). C01 corre si no hay ninguna en curso, o dentro de la pasada de C02 o C03 que la pidió; si hay otra, se descarta (C01 1.1). Qué hacen C02 y C03 en ese caso se define en sus SPEC. Limitación aceptada: el control vive en la memoria del servicio y vale para una sola instancia. Alternativas descartadas: que C01 no controle nada y la exclusión quede solo en C02 y C03, que dejaba 1.1 sin probar en C01; leer 1.1 al pie de la letra, que descartaba a C01 cuando la pide quien ya está sincronizando, o abría un hueco entre las dos pasadas.
- **D3 · Lo local que no viene en el snapshot (2026-11-16):** se quita de la copia, para que quede exactamente igual al snapshot, como piden el paso 5 y la postcondición de C01 y el enunciado §6.1 ("registrar como versión local exactamente la versión que corresponde a los datos almacenados"). En la práctica casi no ocurre: la cátedra conserva lo deshabilitado en el snapshot y en Redis (contrato §4.3); pasaría si la cátedra rehiciera su catálogo o borrara algo a mano. La regla 2 del dominio ("nada se borra físicamente") se aclaró con texto aprobado por el usuario el 2026-11-16: lo que la cátedra deshabilita se conserva; solo se quita lo que deja de publicar en el snapshot. Cambiada en `documentacion/uml/dominio/catalogo_y_sincronizacion_dominio.md` y en `docs/catalogo_y_sincronizacion_dominio.md`, que siguen idénticas. Efecto para turnos: C06 de un profesional quitado responde `404` `PROFESSIONAL_NOT_FOUND` (contrato §3.1). Alternativas descartadas: conservarlo deshabilitado, que dejaba en la copia entidades que la cátedra ya no tiene y una versión que no corresponde exactamente a los datos; conservarlo como estaba, que podía mostrar habilitado un profesional que la cátedra no tiene.
- **D4 · Snapshot completo y consistente (2026-11-16):** se valida lo que la copia necesita y lo que dicen los documentos (RF-02): versión y colecciones presentes, los datos que usa el dominio presentes y no vacíos, formatos de `dayOfWeek` y de las horas, ids sin repetir y referencias resueltas. Un solo defecto invalida todo el snapshot (C01 4.1). Se decidió después de leer el snapshot real, que cumple todo (ver "Situación actual"). Alternativas descartadas: rechazar además los horarios sin sentido (franja al revés, duración no positiva), que no está en ningún documento y, por ser todo o nada, frenaría todo el catálogo por un solo horario; validar solo colecciones y referencias, que dejaba un dato nulo como falla de persistencia en lugar de dato inválido.
- **D5 · Sin sesión técnica, o un `401` que vuelve (2026-11-16):** sin sesión habilitada, C01 pide una renovación sin sesión rechazada y X01 decide si intenta un login (intervalo mínimo, nunca con error de configuración); si sigue sin sesión, registra `SOURCE_UNAVAILABLE` sin pedir el snapshot. Si tras un `401` la renovación no da sesión, `SOURCE_UNAVAILABLE`; si el `401` vuelve con el JWT nuevo, `INTEGRATION_REJECTED`, como define el dominio ("rechazados aun después de renovar"). Limitación aceptada: con error de configuración se registra `SOURCE_UNAVAILABLE`, porque X01 no informa por qué no hay sesión; la causa queda en el log de X01. Alternativas descartadas: terminar sin pedir la renovación, que no recuperaba una sesión degradada por un problema pasajero; no registrar esos fracasos, que dejaba a C07 mostrando el último éxito mientras el catálogo no puede actualizarse.
- **D6 · Respuestas que los documentos no clasifican (2026-11-16):** se clasifican por familia (RF-13), con la misma lógica que el login técnico de X01. Alternativas descartadas: tratar todo lo no previsto como cátedra no disponible, que reintentaba un `404` sin sentido y registraba un tipo engañoso; tratarlo todo como error de integración, que no reintentaba un `502` pasajero.
- **D7 · Qué se registra y cuándo (2026-11-16):** opción a'). El estado guarda el último intento, como define el dominio para `lastSyncAt`, más un atributo nuevo con el instante del último éxito, que es "la última sincronización que aplicó cambios" de C07 (RF-04, RF-14): así se reconcilian el dominio y C07 con un solo campo. El éxito se registra en la misma unidad de trabajo que los datos; un fracaso, en una escritura propia, y si la base tampoco la acepta, solo en el log. Todo error queda en el log de todos modos. El atributo, `lastSuccessfulSyncAt`, se sumó al dominio con texto aprobado por el usuario el 2026-11-17, y la secuencia §1 ahora registra el éxito dentro de la unidad de trabajo; los dos cambios están en `documentacion` y en las copias del servicio, que siguen idénticas. Si C07 lo muestra en su respuesta se decide en su SPEC, como un campo agregado y compatible. Alternativas descartadas: a), solo el último intento, que perdía la fecha del último éxito después de un error; b), último éxito y último error por separado, que obligaba a rediseñar el estado y la respuesta de C07 y a decidir qué hacer con un error viejo; c), registrar solo los éxitos, que dejaba sin uso el resultado `ERROR` y su tipo, definidos en el dominio y el contrato.
- **D8 · Reintentos y timeouts del snapshot (2026-11-16):** los mismos valores que el login técnico (RF-06), configurables aparte. El snapshot real pesa 14,5 KB y tardó 0,5 s, así que 10 s de lectura dan un margen amplio, y el peor caso (alrededor de un minuto) entra en el margen de 5 minutos por mensaje del consumidor de Kafka de C02. Alternativas descartadas: 3 intentos, que liberaban antes la sincronización en curso pero recuperaban menos un corte breve; un timeout de lectura de 30 s, innecesario con este tamaño y más lento para detectar un cuelgue.
- **Estructura de slices (2026-11-16, adelantada del PLAN):** la copia local va en un slice `catalog` (las tres entidades y `CatalogSyncState`) y el proceso en `synchronization`; `catedra` suma el pedido del snapshot. Texto: `docs/decisiones/002-copia-local-del-catalogo-en-un-slice.md`. Se registra como P1 del PLAN.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, las referencias apuntan a pasos y flujos
que existen, los flujos son coherentes, los puntos de backend relevantes están
cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
