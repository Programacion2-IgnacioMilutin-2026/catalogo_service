# 003 — Qué guarda el estado de sincronización y cuándo cambia

- **Fecha:** 2026-11-19
- **Funcionalidad:** C01 — Sincronizar catálogo completo (D7 de su SPEC) y C03 — Verificar periódicamente la versión del catálogo (D4, D5 y D11 de su SPEC). Vale para C02 y para lo que muestre C07.
- **Estado:** aceptada

## Situación

`CatalogSyncState` es una sola fila en el slice `catalog` (decisión 002). Guarda la versión local y el resultado de la sincronización: `localVersion`, `lastSyncAt`, `lastSuccessfulSyncAt`, `lastSyncType`, `lastSyncResult` y `lastErrorType` (dominio §3). Lo escriben C01, C03 y más adelante C02, y lo lee C07, que muestra "el tipo, el resultado y la fecha de la última sincronización que aplicó cambios".

Cada caso de uso dejaba abiertas preguntas que, juntas, definen el significado de la fila:

- C01: si se guarda solo el último intento o también el último éxito, y si el registro va en la misma escritura que los datos.
- C03: qué se registra cuando la verificación encuentra el catálogo al día, que es lo que pasa casi siempre, una vez cada 5 minutos.
- C03: si una aplicación incremental de varias versiones se registra una vez al final o una vez por versión.
- C01 y C03: qué tipo queda registrado cuando una sincronización falla.

## Problema

Dejar que cada caso de uso decida por su cuenta llevaba a una fila que significa cosas distintas según quién la escribió último:

- Si cada verificación al día se registraba como intento, el estado cambiaba cada 5 minutos sin que cambiara nada, y un `INCREMENTAL` sin cambios tapaba el `FULL` recién aplicado.
- Si solo se registraba al final de un recorrido de versiones, un corte del servicio a mitad dejaba la versión avanzada sin su resultado.
- Si un fracaso guardaba su propio tipo, después de un error de Redis el estado ya no decía de qué tipo fue la última sincronización que aplicó cambios, que es lo que pide C07.

## Decisión

La fila responde a dos preguntas. La primera es **qué tiene la copia local**: `localVersion`, `lastSuccessfulSyncAt` y `lastSyncType`, que cambian solo cuando se aplican cambios. La segunda es **cómo salió el último intento**: `lastSyncAt`, `lastSyncResult` y `lastErrorType`.

| Lo que pasa | Qué cambia | Qué no cambia | Cómo se escribe |
| --- | --- | --- | --- |
| Una sincronización completa termina bien (C01) | Todo: la versión, los dos instantes, `FULL`, `SUCCESS` y el error vacío | — | En la misma transacción que los datos |
| Se aplica una versión incremental (C03, y C02 después) | Todo: la versión, los dos instantes, `INCREMENTAL`, `SUCCESS` y el error vacío | — | En la misma transacción que los datos de esa versión, una vez por versión (D5) |
| Un fracaso, de C01 o de C03 | `lastSyncAt`, `ERROR` y el tipo de error | La versión, el último éxito y **el tipo** (D11) | En una escritura propia. Si la base tampoco la acepta, queda solo en el log |
| Una verificación encuentra el catálogo al día, y el último resultado era `ERROR` | `lastSyncAt`, `SUCCESS` y el error vacío | La versión, el último éxito y el tipo (D4 y D11) | En una transacción propia |
| Una verificación encuentra el catálogo al día, y el último resultado era `SUCCESS` | Nada; queda en el log | Todo (D4) | — |
| Una verificación se descarta porque hay otra sincronización en curso | Nada; queda en el log | Todo | — |

Lo que sale de esta tabla:

- **Los datos, la versión y su resultado nunca quedan en desacuerdo.** Si se aplican las versiones 58 y 59 y la 60 falla, la copia está en 59, el último éxito es el de la 59 y el último intento es el fracaso. Si el servicio se corta a mitad, lo que se confirmó queda registrado con su resultado.
- **Un error ya superado no queda a la vista.** La próxima verificación que encuentra el catálogo al día lo corrige, aunque no se haya aplicado ninguna versión nueva.
- **Una verificación al día no escribe nada** si no hay un error que corregir. Es lo que pasa en casi todos los ciclos.
- **Todo error queda en el log**, se registre o no en la base.

Cuidado al leer la fila: un tipo `FULL` junto a un resultado `ERROR` no significa que falló una sincronización completa. Significa que la última sincronización que aplicó cambios fue completa y que el último intento falló. De qué clase fue el intento fallido queda en el log: un `SOURCE_UNAVAILABLE` puede venir de la API REST en C01 o de Redis en C03.

Dónde vive cada regla: en el modelo `CatalogSyncState` (`recordSuccess`, `recordFailure` y `recordUpToDateCheck`). Los casos de uso de `catalog` deciden la transacción, y `synchronization` decide cuándo llamar a cada uno.

Alternativas descartadas:

- **Guardar solo el último intento (C01).** Después de un error se perdía la fecha del último éxito, que es lo que muestra C07.
- **Guardar el último éxito y el último error por separado (C01).** Obligaba a rediseñar el estado y la respuesta de C07, y a decidir qué hacer con un error viejo.
- **Guardar solo los éxitos (C01).** Dejaba sin uso el resultado `ERROR` y su tipo, que están en el dominio y el contrato.
- **No registrar nunca la verificación al día (C03).** El error ya superado quedaba a la vista hasta la próxima versión aplicada, que puede tardar una hora.
- **Registrar cada verificación como intento (C03).** Escribía en cada ciclo y tapaba el tipo de la última sincronización que aplicó cambios.
- **Agregar atributos para la verificación (C03).** Cambiaban el dominio y la base por datos que nadie lee: el contrato de C07 no tiene dónde mostrarlos.
- **Registrar una vez al terminar el recorrido de versiones (C03).** Dejaba la versión avanzada sin su registro entre versiones o tras un corte, y necesitaba una regla aparte para el fracaso a mitad del recorrido.
- **Guardar el tipo del intento también en un fracaso (C01 y C03).** Dejaba a C07 sin el tipo de la última sincronización que aplicó cambios.
- **Guardar los dos tipos (C01 y C03).** Hacía falta un atributo nuevo y una migración, para un dato que el contrato de C07 no tiene dónde mostrar.

## Qué no cambia

- El contrato y los endpoints: C07 sigue mostrando lo que define su contrato. Si suma `lastSuccessfulSyncAt` a la respuesta, se decide en su SPEC, como un campo agregado y compatible.
- Los valores posibles de `lastSyncType`, `lastSyncResult` y `lastErrorType`, que siguen siendo los del dominio y el contrato.
- La regla de una sola sincronización a la vez (`SynchronizationLock`): la tabla supone que nunca hay dos escrituras de sincronización en paralelo.

## Documentos ajustados

Con la aprobación del usuario, en `documentacion` y en las copias del servicio, que siguen idénticas:

- **Dominio §3:** `lastSuccessfulSyncAt` (D7 de C01) y la nueva definición de `lastSyncType` (D11).
- **Secuencia §1:** el éxito se registra dentro de la unidad de trabajo (D7 de C01), y las líneas de fracaso dicen `(ERROR, …), type unchanged` (D11).
- **Secuencia §2:** el registro va dentro del bucle de versiones (D5), y la línea del catálogo al día quedó ajustada (D4 y D11).
- **C03:** el paso 6 dice qué va al log y qué al estado (D4).
