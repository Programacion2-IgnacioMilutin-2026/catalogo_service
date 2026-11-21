# 006 — Resultados sellados o excepciones: cuándo se usa cada uno

- **Fecha:** 2026-11-20
- **Funcionalidad:** C04 — Listar categorías (`docs/sdd/c04-listar-categorias/`, P4.2 del PLAN). Deja por escrito el criterio que se aplicó desde X01.
- **Estado:** aceptada

## Situación

La skill `hexagonal-vertical-slicing` resuelve los errores con excepciones. Cada slice tiene su jerarquía en `application/exception`, con una base `{Entity}Exception` (§2.9); el caso de uso o el service la lanzan, y el controller la traduce a un estado HTTP (§5.3).

En este servicio conviven dos formas de decir que una operación no salió como se esperaba:

- **Resultados sellados**, en la mayoría de los casos: X01, C01, C02 y C03.
- **Una excepción del slice**, en C04: `CatalogNotInitializedException`.

Un resultado sellado es un valor que la operación devuelve y que es uno de un conjunto cerrado de casos. En Java se escribe como una `sealed interface` con un `record` por caso. Por ejemplo, `FullSyncResult` es `Applied(snapshotVersion)`, `Failed(reason)` o `Discarded()`. Quien lo recibe lo recorre con un `switch`, y el compilador exige que cubra todos los casos.

## Problema

Tener dos formas para lo mismo necesita un criterio. Sin él, el código queda inconsistente y la elección es difícil de justificar: por qué no todo con excepciones, como dice la skill, o por qué no todo con resultados.

## Decisión

**Excepción** cuando el error es excepcional, o cuando el único que lo lee es el controller del mismo slice, para traducirlo a HTTP. **Resultado sellado** cuando el fracaso es un desenlace esperado de la operación, cuando quien llama tiene que reaccionar distinto en cada caso, o cuando el resultado tiene que cruzar a otro slice.

### Por qué resultados sellados en la mayoría de los casos

1. **El fracaso es parte del funcionamiento normal.** Que la cátedra o Redis no respondan, que rechacen el token o que manden datos inválidos no es raro: el anexo pide reintentar, renovar la sesión y degradarse sin caerse. Son desenlaces con reglas propias, no errores inesperados. Por ejemplo, con `TechnicalLoginResult`, ante `Unavailable` se reintenta y ante `ConfigurationError` no se reintenta hasta reiniciar.
2. **Cada caso pide una reacción distinta, y el compilador obliga a contemplarlos todos.** En `CatalogCatchUp`, el procedimiento que comparten C02 y C03:

   ```java
   return switch (getCatalogMetadataUseCase.getCatalogMetadata()) {
       case CatalogMetadataResult.Unavailable unavailable ->
               fail(SyncErrorType.SOURCE_UNAVAILABLE, unavailable.reason());
       case CatalogMetadataResult.Rejected rejected ->
               fail(SyncErrorType.INTEGRATION_REJECTED, rejected.reason());
       case CatalogMetadataResult.Invalid invalid -> discontinuity(afterFull, invalid.reason());
       case CatalogMetadataResult.Obtained obtained -> compare(localVersion, obtained.metadata(), afterFull);
   };
   ```

   Son cuatro desenlaces con cuatro reacciones: registrar un error, registrar otro, pedir la sincronización completa o seguir. Si se agregara un quinto caso, el proyecto no compilaría hasta tratarlo. Con excepciones serían tres `catch`, y un caso nuevo que nadie atrapa compilaría igual y aparecería recién en ejecución.
3. **Pueden cruzar de un slice a otro.** La skill pone las excepciones en `application/exception` (§2.9), y una fachada solo puede usar del otro slice su dominio y sus puertos de entrada (§9). Un resultado sellado vive en el dominio, así que puede cruzar. Se aprendió en C03 (P13.1 de su PLAN): la primera versión usaba una excepción de `catalog` que `synchronization` no podía importar sin romper la skill, y se reemplazó por `ApplyCatalogVersionResult`.
4. **En los procesos de fondo no hay nadie que traduzca una excepción.** C01 a C03 corren desde el disparador periódico o desde un aviso de Kafka, sin un controller ni un manejador de errores. El oyente de Kafka nunca deja salir un error: si saliera, Kafka reintentaría o descartaría el aviso por su cuenta. Con un resultado, el recorrido queda a la vista: se registra el fracaso, se confirma el offset y se sigue.
5. **Llevan datos.** `Applied(versión)`, `Failed(motivo)` o `NotVerifiable(problemas)`: el desenlace viaja con la información que necesita quien lo recibe.

### Por qué una excepción en C04

- **"Sin inicializar"** lo lee solo el controller del mismo slice, y lo único que hace es convertirlo en un `503`. No hay varias reacciones ni un cruce a otro slice: es exactamente el caso que describe la skill (§2.9 y §5.3). Por eso `CatalogNotInitializedException` extiende la base del slice, `CatalogException`.
- **La falla de la base** sí es excepcional: no la atrapa nadie en el slice, llega al manejador global, que la deja en el log y responde `500` (decisión 005).

### Dónde se usa cada uno hoy

| Pieza | Slice | Forma | Por qué |
| --- | --- | --- | --- |
| `TechnicalLoginResult` | `catedra` | Sellado | Reintentar o no, según el caso (X01) |
| `SnapshotFetchResult`, `CatalogSnapshotResult` | `catedra` | Sellado | Un intento con la cátedra (recibido, token rechazado, rechazado, no disponible o inválido): con el token rechazado se renueva la sesión y con no disponible se reintenta. El segundo es el resultado ya resuelto, que recibe `synchronization` (C01) |
| `RedisReadResult`, `CatalogMetadataResult`, `CatalogVersionChangesResult` | `catedra` | Sellado | Lo mismo con Redis: con las credenciales rechazadas se renueva y con no disponible se reintenta; los dos últimos cruzan a `synchronization` (C03) |
| `CatalogUpdateReading` | `catedra` (Kafka) | Sellado | Un aviso válido o inválido con su motivo; uno inválido se descarta sin cortar el consumo (C02) |
| `ApplyCatalogVersionResult` | `catalog` | Sellado | Cruza a `synchronization`, que no puede importar una excepción de `catalog` (C03) |
| `FullSyncResult`, `VerificationResult`, `CatchUpResult`, `IncrementalSyncResult` | `synchronization` | Sellado | Procesos de fondo: quien llama decide qué registrar y si sigue (C01 a C03) |
| `CatalogNotInitializedException` | `catalog` | Excepción del slice | Solo la lee su controller, para responder `503` (C04) |
| Una falla de la base en C04 | — | Excepción no prevista | Excepcional: la atrapa el manejador global (decisión 005) |

### Excepción a la skill

La skill solo prevé excepciones, así que los resultados sellados, y los `record` con que se escriben, son una excepción a la skill. Se autorizó en cada PLAN, con su motivo (X01 P1, C01 P9, C03 P13 y C02 P10), y está listada en el `CLAUDE.md` del servicio.

## Alternativas descartadas

- **Todo con excepciones, como la skill.** Sin un `switch` que el compilador revise, un desenlace nuevo podría quedar sin tratar. Las excepciones no pueden cruzar a otro slice sin romper la skill, como mostró C03. Y en los procesos de fondo el recorrido quedaría escondido en `catch` repartidos.
- **Todo con resultados sellados, también en C04.** Sería otra excepción a la skill sin nada que ganar: en un endpoint, que el controller traduzca la excepción de su slice a un estado HTTP es justo el molde de la skill.
- **Excepciones que se declaran en la firma (`checked`).** Obligan a quien llama a tratarlas, como un resultado sellado, pero siguen viviendo en `application`, así que tampoco cruzan a otro slice. Además, la skill pide excepciones no declaradas (§5.2, "unchecked exceptions don't force try/catch everywhere"), y Spring trabaja con ellas.

## Qué no cambia

- La jerarquía de excepciones de la skill se usa donde corresponde: `CatalogException` vuelve con C04.
- Los resultados sellados que ya existen no cambian.
- Cada caso nuevo elige en su PLAN con esta regla, y si se aparta de ella, se anota el porqué.
