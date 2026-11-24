# SPEC: C05 — Buscar profesionales

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
| Caso de uso C05 (`docs/casos_de_uso/catalogo_y_sincronizacion_usecases.md`) | Todo: precondición, pasos 1 a 5, alternativos 1.1, 2.1, 3.1, 3.2, 3.3 y 4.1, y postcondición. El paso 5 suma el `id` de cada horario desde el 2026-11-20 (D1). En 3.3 se sigue el contrato, que fija `500` (D10) |
| Diagrama de secuencia (`docs/secuencias/catalogo_y_sincronizacion_secuencia.md`) | §3, la rama `public query`, lo de C05 (incluido el `opt only C05`) |
| Contrato de catálogo (`docs/contratos/catalogo_y_sincronizacion_contrato.md`) | §1 (fila de C05; la de C06 solo para RF-12) y §2.2. §3.1 solo porque su respuesta es "el mismo objeto que cada elemento de `items` en C05". La frase de la disponibilidad del §2.2 se aclaró el 2026-11-20 (D2 y D3) |
| Contrato común (`docs/contratos/contratos_comunes.md`) | §1.4 (C05 es público; los endpoints públicos ignoran `Authorization`), §2 (formato de errores, `VALIDATION_ERROR` con `fieldErrors`, `INTERNAL_ERROR`), §3 (`camelCase`, horas, días) y §3.1 (paginación) |
| Modelo de dominio (`docs/catalogo_y_sincronizacion_dominio.md`) | §3: `Professional`, `ProfessionalCategory`, `WeeklySchedule` y `CatalogSyncState.localVersion` (qué significa "inicializado"); reglas 4, 5 y 6 |
| Caso de uso A04 de la app (`documentacion/uml/casos_de_uso/app_kmp_usecases.md`) | Solo como quien lo usa: pasos 3, 5, 6 y 8, y alternativos 7.1, 7.2, 8.1 y 8.2 |
| Enunciado (`documentacion/enunciado/PROJECT_STATEMENT-v1.md`) | §4.1 (filtros mínimos: categoría, nombre, estado habilitado y disponibilidad, sobre la copia local), §9 ("proteger los endpoints que no sean expresamente públicos", RF-12) y §11 (evidencia del funcionamiento de esos filtros) |
| Anexo de la cátedra | No aplica: C05 no usa la cátedra. El formato de errores sigue el de la cátedra a través del contrato común §2 (anexo §13) |
| SDD de C04 (`docs/sdd/c04-listar-categorias/`) y decisiones 005 y 006 | Lo que C05 reutiliza: la lista de rutas públicas, el manejador de errores, la excepción de "sin inicializar" y la regla de orden en español (D2 y P3 de C04) |
| Turnos (`turnos_service`: T10 y su `ApiExceptionHandler`; el cliente de C06 y `CatalogClientAdapterTest`) | Solo como antecedente: cómo paginó y validó parámetros T10, y qué lee turnos del objeto del profesional. No es una fuente de verdad para catálogo |

## Qué construimos y para quién

<!-- Qué necesidad cubre, qué actor la inicia (la app KMP, otro servicio, el
tiempo o la cátedra) y qué podrá hacer o recibir. Describe el objetivo en
lenguaje de negocio. Resume y referencia: no copies la descripción del caso de uso. -->

La app busca profesionales combinando filtros opcionales: categoría, nombre, estado y disponibilidad semanal (A04; C05, descripción). El servicio de catálogo responde con una página de profesionales de su copia local, cada uno con su estado, su categoría y sus horarios semanales, y la app decide cómo mostrarlos. Es público: no hace falta haber iniciado sesión. Nunca consulta a la cátedra ni a turnos. Cubre los filtros mínimos que pide el enunciado (§4.1).

## Situación actual

<!-- Qué existe hoy y es relevante (código, datos, otras funcionalidades ya
implementadas), la limitación que queremos resolver y los comportamientos
existentes que deben conservarse. Si todavía no hay código para esto, indícalo.
No describas la arquitectura. -->

Verificado el 2026-11-20, en la rama `buscar-profesionales` (creada por el usuario desde `listar-categorias`, en `58ab3b1`, con C04 completo):

- X01, C01, C02, C03 y C04 están implementados. La copia local tiene categorías, profesionales, horarios semanales y el estado de sincronización. Una sincronización completa reemplaza la copia en una sola unidad de trabajo, y una incremental aplica cada versión en la suya.
- La versión local está vacía hasta que la primera sincronización completa termina bien, y una copia inicializada no vuelve a estar sin inicializar.
- C04 dejó:
  - una sola lista de rutas públicas, hoy solo `GET /api/professional-categories`; todo lo demás se rechaza con `403` sin cuerpo, y nadie lee `Authorization`. Hoy `GET /api/professionals` responde `403` (comprobado por un test de C04);
  - un formato común de errores (`problem+json`), con `503` `CATALOG_NOT_INITIALIZED` desde el controller de categorías y `500` `INTERNAL_ERROR` para lo no previsto. No hay nada de `400` `VALIDATION_ERROR` ni de `fieldErrors`;
  - el orden en español de los nombres de las categorías (D2 de C04). Los nombres de los profesionales no lo tienen.
- Lo que garantiza la sincronización sobre los datos: el nombre y el apellido de un profesional siempre tienen texto; cada profesional tiene una categoría existente y cada horario un profesional existente; el día es de `MONDAY` a `SUNDAY` y las horas son horas válidas. **No comprueba que un horario termine después de empezar.** Nada impide un profesional sin horarios, ni dos horarios del mismo profesional en el mismo día.
- Los datos de ejemplo de los tests (con el formato real) llevan tildes y ñ: "Ibáñez", "Núñez", "Tomás", "Lucía". La cátedra real tenía 8 categorías, 24 profesionales y 44 horarios, con algunos deshabilitados (2026-11-16 y 2026-11-17).
- Turnos no consume C05: solo la app. Turnos consume C06, cuyo objeto es el mismo que cada elemento de C05; su cliente ya lee una respuesta con el `id` de cada horario (`CatalogClientAdapterTest` de turnos).
- Las copias de `contratos_comunes.md` y de X01 (caso de uso y secuencia) son idénticas en `documentacion`, `turnos_service` y `catalog_service`; las de catálogo (casos de uso, contrato, dominio y secuencia) coinciden con `documentacion` (comparadas el 2026-11-20).
- Lo que debe conservarse: el comportamiento de X01 y de C01 a C04. C05 no escribe nada.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. Cada requisito indica el paso o flujo
alternativo del que proviene. No agregues requisitos que los documentos de
referencia no tengan sin consultarlo. -->

- **RF-01** · C05 pasos 1, 3 y 5; contrato §1 y §2.2: `GET /api/professionals` responde `200` con una página de los profesionales de la copia local que cumplen todos los filtros recibidos (RF-03 y RF-04). Cada profesional lleva `id`, `firstName`, `lastName`, `enabled`, su categoría (`id`, `name`, `enabled`) y sus horarios semanales, cada uno con `id`, `dayOfWeek`, `startTime`, `endTime`, `slotDurationMinutes` y `enabled` (D1). Van todos sus horarios, habilitados y deshabilitados, hayan cumplido o no el filtro, ordenados por día de la semana (de lunes a domingo), hora de inicio e `id` (D7). Nunca consulta a la cátedra ni a turnos, ni modifica datos.
- **RF-02** · C05 precondición y alternativo 1.1; contrato común §1.4: es público. Un pedido sin `Authorization`, o con uno válido, inválido, vencido o mal formado, recibe la misma respuesta.
- **RF-03** · C05 paso 3; contrato §2.2: los filtros se combinan; un profesional tiene que cumplir todos los recibidos. Sin filtros vuelven todos, habilitados o no, también los de una categoría deshabilitada.
  - `categoryId`: los de esa categoría. Un id que no existe da una página vacía (RF-09).
  - `enabled`: los profesionales con ese estado. Es el del profesional, independiente del de su categoría y de sus horarios (dominio, regla 4).
  - `name`: el texto se separa en palabras, y cada una tiene que estar contenida en el nombre o en el apellido; tienen que aparecer todas, en cualquier orden (D4). Contener es aparecer en cualquier parte: "ana" encuentra también a "Mariana". No distingue mayúsculas ni tildes, y la n y la ñ son iguales (D5). Los espacios de los extremos no cuentan, y `%` y `_` son caracteres comunes, no comodines.
- **RF-04** · C05 paso 3; contrato §2.2; dominio, regla 5: la disponibilidad cumple si el profesional tiene al menos un horario semanal **habilitado** que está en alguno de los `dayOfWeek` recibidos y se solapa con la franja. Tiene que ser el mismo horario el que cumple las dos cosas. Los horarios deshabilitados nunca cuentan.
  - Los días y la franja son opcionales, y cada parte se aplica por su cuenta (D2): sin `dayOfWeek` cuenta cualquier día, y sin franja, cualquier hora. Con solo `from`, la franja va desde `from` hasta el final del día; con solo `to`, desde el comienzo del día hasta `to`.
  - Sin días ni franja no se filtra por disponibilidad: entran también los profesionales sin horarios.
  - Un horario se solapa con la franja si empieza antes de que ella termine y termina después de que empiece: tocarse en el borde no cuenta (D3). Por ejemplo, uno de 08:00 a 12:00 no cumple con `from` 12:00 ni con `to` 08:00.
  - Un horario que no termina después de empezar (por ejemplo, de 22:00 a 02:00) nunca cuenta para la disponibilidad, ni por días ni por franja (D3). Igual viaja con los demás horarios del profesional (D7).
- **RF-05** · C05 paso 4; contrato §2.2: los resultados se ordenan por `lastName`, `firstName` e `id`, y la página se toma de ese orden. Mientras la copia no cambie, recorrer las páginas no repite ni saltea profesionales. El orden es alfabético en español, con la misma regla que las categorías (D2 de C04): no distingue mayúsculas ni tildes, la ñ va después de la n, y si apellido y nombre quedan iguales con esa regla, va primero el `id` menor (D6). Es el mismo en cualquier entorno.
- **RF-06** · C05 pasos 1, 4 y 5, alternativo 4.1; contrato común §3.1: paginación con `page` (base 0, por defecto 0) y `size` (por defecto 10, de 1 a 100). La respuesta trae `items`, `page`, `size` y `totalItems`, que es el total de profesionales que cumplen los filtros. Una página más allá de la última trae `items` vacío y el `totalItems` real.
- **RF-07** · C05 paso 2 y alternativo 2.1; contrato §2.2; contrato común §2: los parámetros se validan antes de consultar la copia. Un parámetro inválido responde `400` `VALIDATION_ERROR` con `fieldErrors`, sin consultar la copia, también si no está inicializada. Son inválidos, como mínimo: un `dayOfWeek` fuera de `MONDAY` a `SUNDAY`, una hora que no sea `HH:mm` ni `HH:mm:ss`, un `from` que no sea anterior a `to`, un `categoryId` no numérico, una `page` negativa y un `size` fuera de 1 a 100. Además (D8):
  - Los formatos son exactos: los días de `MONDAY` a `SUNDAY`, sin distinguir mayúsculas (`monday`, `Monday` y `MONDAY` valen), y varios días repitiendo el parámetro o separados por coma; `enabled` solo `true` o `false`; las horas solo `HH:mm` o `HH:mm:ss`, con dos dígitos; `categoryId`, `page` y `size`, números enteros. Cualquier otro valor es inválido, también un número demasiado grande para leerse. Un `categoryId` negativo o 0 es válido y da una página vacía (RF-09).
  - Un parámetro presente pero vacío, o un `name` de solo espacios, cuenta como si no viniera. Un `dayOfWeek` vacío junto a otros no cuenta.
  - Los parámetros desconocidos, incluido `sort`, se ignoran.
  - `fieldErrors` trae una entrada por cada parámetro inválido, todas en la misma respuesta; un `dayOfWeek` con dos valores inválidos es una sola entrada. `field` es el nombre del parámetro y `message`, un texto fijo en inglés propio de cada regla, sin tipos de Java ni datos internos (D9). "`from` no es anterior a `to`" va en `from`, y solo se revisa si las dos horas se pudieron leer.
- **RF-08** · C05 alternativo 3.1; contrato §2.2: con parámetros válidos y la copia sin inicializar, responde `503` `CATALOG_NOT_INITIALIZED`, sin consultar a la cátedra ni pedir una sincronización.
- **RF-09** · C05 alternativo 3.2; contrato §2.2: si ningún profesional cumple los filtros, responde `200` con `items` vacío y `totalItems` 0.
- **RF-10** · C05 alternativo 3.3; contrato §2.2: si falla la base, deja un error en el log y responde `500` `INTERNAL_ERROR`, sin detalles internos. Una base que no da una conexión también es una falla: el servicio deja de esperarla al vencer el tiempo del pool de conexiones. No hay un límite propio (D10, como D4 de C04).
- **RF-11** · C05 postcondición: la respuesta muestra una copia entera: los profesionales, sus categorías, sus horarios y el `totalItems` salen de la misma versión de la copia, la anterior o la posterior a una sincronización en curso, nunca de una mezcla. C05 no espera a que la sincronización termine.
- **RF-12** · Enunciado §9; contrato §1: solo se abre `GET /api/professionals`. `GET /api/professionals/{professionalId}` (C06) y todo lo demás siguen sin obtener datos sin autenticación hasta la validación del JWT, como dejó C04.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Incluye las partes de los
casos de uso que se dejan para otra funcionalidad. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Confirmadas por el usuario el 2026-11-20:

- La validación del JWT, los rechazos `401` y `403`, la clave pública de turnos y CORS: van en su funcionalidad, después de C05, como quedó en C04 (D1 y D5 de C04).
- Los endpoints de C06 y C07.
- Los turnos libres de una fecha: son de turnos (T03; C05, descripción).
- Cambios en la sincronización (C01, C02 y C03), también en lo que acepta de la cátedra.
- Pruebas contra la cátedra real: C05 no interactúa con la cátedra. Solo lee la copia local, que cargan C01 a C03, ya probados contra la cátedra real.
- El Docker del servicio: etapa propia.

## Flujo de la operación

<!-- Cómo se inicia (request, evento o tarea programada), qué verifica y qué
resultado produce, incluidas las alternativas relevantes. Si el caso de uso ya
lo describe, referencia sus pasos en lugar de repetirlos y describe solo lo que
cubre esta funcionalidad. -->

1. La app pide `GET /api/professionals` con los filtros y la paginación que quiera, con o sin `Authorization`; el pedido se trata como anónimo (C05 paso 1 y 1.1; RF-02).
2. El servicio valida los parámetros; si alguno es inválido, responde `400` sin consultar la copia (C05 paso 2 y 2.1; RF-07).
3. Mira si la copia está inicializada; si no, responde `503` (C05 3.1; RF-08).
4. Selecciona los profesionales que cumplen todos los filtros, los ordena y toma la página pedida (C05 pasos 3 y 4; RF-03 a RF-06). Si falla la base, responde `500` (C05 3.3; RF-10).
5. Responde `200` con la página y el total; vacía si nada coincide o si la página está más allá de la última (C05 paso 5, 3.2 y 4.1; RF-01, RF-06 y RF-09).

## Datos y reglas de negocio

<!-- Información que necesita la operación, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- **La respuesta** (contrato §2.2; contrato común §3 y §3.1): un objeto con `items`, `page`, `size` y `totalItems`. `page` y `size` son los aplicados, con los valores por defecto si no llegaron. Cada elemento de `items` es un profesional con los campos de RF-01 y nada más. La categoría va con sus tres campos, sin `description`. Un profesional sin horarios lleva `weeklySchedules` vacío, igual que C06 (contrato §3.1). Las horas salen como `HH:mm:ss` y los días de `MONDAY` a `SUNDAY` (contrato común §3).
- **Los horarios de cada profesional:** cada uno con `id`, `dayOfWeek`, `startTime`, `endTime`, `slotDurationMinutes` y `enabled` (D1). Van todos, habilitados y deshabilitados, también los que no cumplieron el filtro de disponibilidad: el filtro elige profesionales, no recorta su agenda. Ordenados por día de la semana, de lunes a domingo (el orden del contrato común §3), hora de inicio e `id` (D7). La app muestra solo los habilitados (A04 paso 8).
- **Los filtros:** se combinan (RF-03). La disponibilidad solo mira horarios habilitados (RF-04).
- **El nombre** (D4 y D5): por palabras, como en RF-03. "tomás ferreyra", "ferreyra tomás" y "tom ferr" encuentran a Tomás Ferreyra, y "tomás gómez" no. "nunez" y "nuñez" encuentran a Núñez, e "ibanez" a Ibáñez. La regla de la búsqueda no cambia el orden (D6).
- **Orden** (D6): por `lastName`, `firstName` e `id`, alfabético en español como las categorías (RF-05). Por ejemplo, por apellido: Álvarez, de la Fuente, Munro, Muñoz, Zárate. Habilitados y deshabilitados van mezclados.
- **Inicializada** (dominio §3): la versión local tiene valor. Que haya o no profesionales no cambia eso.
- **Errores** (contrato común §2): `Content-Type: application/problem+json` con `status`, `title`, `code` y `detail`, y `fieldErrors` en el `400`. Los textos son fijos, para el desarrollador, y nunca incluyen detalles internos: ni SQL, ni nombres de tablas, clases o tipos, ni trazas.
- **Qué se registra:** la falla de la base, como error en el log. Un pedido que termina bien, o con `400`, no deja nada en el log (D10).

## Casos alternativos y de error

<!-- Adapta la tabla usando BACKEND_GUIDELINES.md. Cuando el caso de uso ya define
el flujo alternativo, referencia su ID en lugar de repetirlo. Añade escenarios
relevantes. Marca No aplica con su motivo cuando corresponda. Expresa resultados
observables (estado HTTP, código de error, estado final del recurso, evento
publicado), no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Entrada inválida | C05 2.1: `400` `VALIDATION_ERROR` con `fieldErrors`, antes de mirar la copia (RF-07). Formatos exactos; los parámetros vacíos y los desconocidos se ignoran (D8). Todos los errores juntos, uno por parámetro (D9). Un `Authorization` inválido se ignora (RF-02) |
| Sin sesión o sin permiso | Público: sin sesión responde igual que con una (C05 1.1; RF-02). C06 y lo demás siguen cerrados (RF-12) |
| Recurso inexistente o ajeno | Un `categoryId` inexistente da una página vacía, no un `404` (C05 3.2; RF-09). No hay dueño: no aplica "ajeno" |
| Dependencia no disponible (cátedra, Redis, Kafka) | No usa ninguna: responde aunque la integración con la cátedra no esté disponible. Base no disponible: `500` (RF-10) |
| Timeout o espera excesiva | Sin límite propio (D10, como D4 de C04). Una base caída, o colgada al pedir una conexión, responde `500` al vencer el tiempo del pool de conexiones (30 s por defecto; RF-10). Limitación aceptada: una consulta que ya estaba en curso cuando la base se colgó espera hasta que la base vuelva; la app corta por su lado y permite reintentar (A04 6.1) |
| Operación o mensaje repetido | Solo lee: el mismo pedido da la misma respuesta mientras la copia no cambie |
| Evento tardío o fuera de orden | No aplica: no recibe eventos |
| Falla de persistencia | `500` `INTERNAL_ERROR` y error en el log (C05 3.3; RF-10). No hay escrituras |
| Solicitudes simultáneas | Los pedidos son independientes. Con una sincronización en curso, la respuesta muestra la copia anterior o la posterior, también en el total, y no espera a que termine (RF-11) |
| Reinicio con operaciones pendientes | No aplica: no deja nada pendiente. Recién arrancado con la base vacía, responde `503` hasta que termine la primera sincronización completa (RF-08) |
| Otros puntos aplicables de la guía | Paginación y orden estable (RF-05, RF-06). Horas locales sin zona (contrato común §3). Privacidad: solo datos públicos del catálogo y errores sin detalles internos. Rendimiento: páginas de 100 como máximo |

**Puntos de la guía no aplicables y motivo:** idempotencia de escrituras, consistencia entre sistemas, estados y transiciones, tareas programadas y Kafka (C05 solo lee la copia local); instantes y zonas horarias (la respuesta no los tiene).

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de seguridad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Respetar el contrato de Documentacion y el anexo de la cátedra puede ser
una restricción; el diseño de los adaptadores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Respetar el contrato §1 y §2.2 y el contrato común §1.4, §2, §3 y §3.1.
- C05 no consulta a la cátedra ni a turnos (C05, descripción) y resuelve los filtros sobre la copia local (enunciado §4.1).
- La validación del JWT va después de C05, en su propia funcionalidad (orden acordado).
- X01 y C01 a C04 siguen cumpliendo sus SPEC, sin cambios de comportamiento.
- Los tests automáticos no llaman a la cátedra real ni leen el `.env`.
- Aplicar la skill `hexagonal-vertical-slicing`. Cada excepción se analiza en el contexto de C05 (indicación del usuario del 2026-11-15).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados: cada flujo alternativo cubierto debería
tener un criterio. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dada una copia inicializada con profesionales habilitados y deshabilitados, de categorías habilitadas y deshabilitadas, con y sin horarios, cuando se pide sin filtros y sin `Authorization`, entonces responde `200` con todos, cada uno con sus campos y los valores de la copia, sin otros campos, con todos sus horarios (habilitados y deshabilitados) en el orden de D7, las horas en `HH:mm:ss` y `weeklySchedules` vacío para el que no tiene horarios, y no se hace ningún pedido a la cátedra.
- **CA-02 · RF-03:** Dada la misma copia, cuando se pide con un `categoryId`, entonces vienen solo los de esa categoría, también si está deshabilitada; y con `enabled` en `true` o en `false`, solo los profesionales con ese estado, sin importar el de su categoría ni el de sus horarios.
- **CA-03 · RF-03:** Dados Tomás Ferreyra, Valentina Ibáñez, Lucía Núñez y Mariana Paz, cuando se busca por `name`:
  - "tomás ferreyra", "ferreyra tomás", "TOM ferr" o "  tomas  ", entonces viene solo Tomás Ferreyra;
  - "tomás gómez", ninguno;
  - "nunez", "nuñez" o "NÚÑEZ", solo Lucía Núñez; "ibanez", solo Valentina Ibáñez;
  - "ana", solo Mariana Paz;
  - "%" o "_", ninguno.
- **CA-04 · RF-04:** Dado un profesional con un horario habilitado el lunes a la mañana y otro el martes a la tarde, y otro profesional con un solo horario, deshabilitado, el lunes a la mañana, cuando se busca el lunes a la tarde, entonces no viene ninguno de los dos; y cuando se busca el lunes a la mañana, viene solo el primero, con sus dos horarios.
- **CA-05 · RF-04:** Dado un profesional que atiende solo el lunes de 08:00 a 12:00, otro solo el viernes de 10:00 a 14:00, otro cuyo único horario es el lunes de 22:00 a 02:00, y otro sin horarios, cuando se busca:
  - solo con `dayOfWeek` lunes, entonces viene solo el primero;
  - solo con la franja de 13:00 a 15:00, o solo con `from` 13:00, solo el segundo;
  - solo con `to` 09:00, solo el primero; con `to` 23:00, el primero y el segundo, y no el tercero;
  - con `from` 12:00 o con `to` 08:00, el primero no viene; con `from` 11:30, sí;
  - sin días ni franja, los cuatro.
- **CA-06 · RF-03, RF-04:** Dados varios filtros juntos, cuando se busca, entonces vienen solo los profesionales que cumplen todos.
- **CA-07 · RF-05:** Dados profesionales con apellidos en mayúscula y en minúscula, con y sin tildes, dos que solo difieren en la ñ (Munro y Muñoz), dos con el mismo apellido y distinto nombre, y dos con apellido y nombre iguales salvo mayúsculas o tildes, cargados en otro orden, cuando se buscan, entonces vienen por apellido, nombre e `id` con la regla de D6, también al recorrerlos página por página.
- **CA-08 · RF-06:** Dada una copia con más profesionales que el tamaño de página, cuando se piden sin `page` ni `size`, entonces vienen los primeros 10 con `page` 0, `size` 10 y el `totalItems` real; cuando se recorren con `size` 2, cada profesional aparece en una sola página y en el orden de RF-05; y cuando se pide una página más allá de la última, también una enorme como la 2000000000, `items` viene vacío con el `totalItems` real.
- **CA-09 · RF-07:** Dada una copia sin inicializar, cuando se pide con cada uno de los valores inválidos de RF-07, entonces cada respuesta es `400` en `application/problem+json` con `code` `VALIDATION_ERROR` y `fieldErrors` que nombra el parámetro, sin detalles internos (no un `503`: no se consultó la copia).
- **CA-10 · RF-07:** Dada una copia sin inicializar, cuando se pide con `dayOfWeek=lunes`, `enabled=TRUE`, `enabled=yes`, `enabled=1`, `enabled=quizas`, `from=9:00`, `from=09:00:00.5`, `page=abc`, `size=diez`, o un `page` o un `categoryId` de 20 dígitos, entonces cada respuesta es `400` `VALIDATION_ERROR` con ese parámetro en `fieldErrors`.
- **CA-11 · RF-02:** Dada la misma copia, cuando se pide con un JWT bien firmado, con uno vencido, con uno alterado, con un texto que no es un JWT y con un `Authorization` que no es `Bearer`, entonces cada respuesta es igual a la del pedido sin encabezado.
- **CA-12 · RF-08:** Dada una copia sin versión local, con profesionales cargados o sin ellos, cuando se pide con parámetros válidos, entonces responde `503` en `application/problem+json` con `code` `CATALOG_NOT_INITIALIZED`, no se hace ningún pedido a la cátedra y la base no cambia.
- **CA-13 · RF-09:** Dada una copia inicializada, cuando se busca con un `categoryId` inexistente, o con filtros que nadie cumple, o en una copia sin profesionales, entonces responde `200` con `items` vacío y `totalItems` 0.
- **CA-14 · RF-10:** Dada una falla de la base al leer el estado, y otra al buscar, cuando se pide, entonces responde `500` en `application/problem+json` con `code` `INTERNAL_ERROR`, el cuerpo no contiene SQL, nombres de tablas o clases ni trazas, y queda un error en el log.
- **CA-15 · RF-11:** Dada una sincronización que está cambiando la copia y todavía no terminó, cuando se pide, entonces responde enseguida con la copia anterior, también en el `totalItems` y en los horarios; y cuando la sincronización terminó, con la nueva.
- **CA-16 · RF-01, RF-08:** Dada la integración con la cátedra no disponible y una copia inicializada, cuando se pide, entonces responde `200` igual.
- **CA-17 · RF-12:** Dado el servicio en marcha, cuando se pide sin autenticación `GET /api/professionals/{professionalId}` u otra ruta no pública, entonces no responde con datos.
- **CA-18 · RF-07:** Dada una copia inicializada, cuando se pide con cada parámetro presente pero vacío (`name=`, `categoryId=`, `enabled=`, `dayOfWeek=`, `from=`, `to=`, `page=` y `size=`), con un `name` de solo espacios, o con `sort` y otros parámetros desconocidos, entonces la respuesta es la misma que sin esos parámetros; con `dayOfWeek=MONDAY&dayOfWeek=`, la misma que con solo el lunes; con `dayOfWeek=monday` o `dayOfWeek=Monday`, la misma que con `dayOfWeek=MONDAY`; y con `dayOfWeek=MONDAY,FRIDAY`, la misma que con `dayOfWeek=MONDAY&dayOfWeek=FRIDAY`.
- **CA-19 · RF-07:** Dada una copia sin inicializar, cuando se pide con `size=0&dayOfWeek=FUNDAY`, entonces `fieldErrors` trae exactamente dos entradas, una de `size` y otra de `dayOfWeek`, con textos fijos y sin tipos de Java; con `from=14:00&to=10:00`, una sola entrada, de `from`; y con `from=99:00&to=10:00`, una sola entrada, de `from`, por el formato.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Copia del criterio, con los horarios cargados en otro orden; pedido sin filtros ni encabezado; registro de los pedidos a la cátedra simulada | `200` con todos y sus campos, y los horarios en el orden de D7; ningún pedido a la cátedra durante la consulta |
| CA-02 | La misma copia; un pedido por `categoryId` (habilitada y deshabilitada) y uno por cada valor de `enabled` | Solo los que corresponden |
| CA-03 | Los cuatro profesionales del criterio; un pedido por cada texto | Los que indica el criterio en cada pedido |
| CA-04 | Los dos profesionales del criterio | El lunes a la tarde, ninguno; el lunes a la mañana, solo el primero |
| CA-05 | Los cuatro profesionales del criterio; un pedido por cada caso | Los que indica el criterio en cada pedido |
| CA-06 | Copia donde cada filtro solo descarta a un profesional distinto | Solo los que cumplen todos |
| CA-07 | Copia del criterio, cargada en otro orden; una búsqueda de todos y otra página por página | El orden de D6 en las dos |
| CA-08 | Copia con más de 10 profesionales; sin paginación, con `size` 2 página por página, y una página más allá de la última | Las páginas del criterio, sin repetidos ni faltantes |
| CA-09 | Copia sin inicializar; un pedido por valor inválido | `400` `VALIDATION_ERROR` con el campo en `fieldErrors` |
| CA-10 | Copia sin inicializar; un pedido por valor del criterio | `400` `VALIDATION_ERROR` con el campo en `fieldErrors` |
| CA-11 | La misma copia; un pedido por variante del encabezado | Cada respuesta igual a la del pedido sin encabezado |
| CA-12 | Base sin versión local, con y sin profesionales | `503` `CATALOG_NOT_INITIALIZED`; sin pedidos a la cátedra; base sin cambios |
| CA-13 | Copia inicializada; `categoryId` inexistente, filtros que nadie cumple, y sin profesionales | `200`, `items` vacío, `totalItems` 0 |
| CA-14 | Falla de la base provocada al leer el estado, y al buscar | `500` `INTERNAL_ERROR` sin detalles internos; error en el log |
| CA-15 | Un cambio de la copia que queda abierto mientras llega el pedido, y después se completa | Antes: la copia anterior, sin esperar; después: la nueva |
| CA-16 | Cátedra simulada sin integración disponible; copia inicializada | `200` |
| CA-17 | Pedido sin autenticación a `GET /api/professionals/15` y a otra ruta no pública | Sin datos |
| CA-18 | Copia inicializada; un pedido por caso del criterio, comparado con el mismo pedido sin esos parámetros | Respuestas iguales |
| CA-19 | Copia sin inicializar; los tres pedidos del criterio | Las entradas de `fieldErrors` que indica el criterio, ni una más |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna.

Para el PLAN, ya anotado:

- Dónde se ordena (D6): la colación de `V3` ya existe y `V3` no se toca. Puede asignarse a las columnas del nombre y el apellido con una migración nueva, o aplicarse en la consulta.
- Cómo se busca (D4 y D5): una comparación que no distinga mayúsculas ni tildes y con la ñ igual a la n, que la colación de C04 no da, porque distingue la ñ. `LIKE` con una colación no determinística, que PostgreSQL 18 admite según sus notas (por comprobar en un contenedor descartable, como en C04), contra otras técnicas. Y cómo se escapan `%`, `_` y `\`.
- La consulta con filtros variables, el total, la página y los horarios de la página, sin una consulta por profesional.
- RF-11: cómo salen de la misma foto el total, la página y los horarios. El argumento de P5 de C04 (dos lecturas sin transacción) no se traslada, porque aquí son varias lecturas que pueden caer a los dos lados de una sincronización. Y cómo se prueba sin depender del tiempo.
- El modelo: los horarios junto al profesional, y la página y los filtros en el dominio, que no puede usar Spring Data. C06 lo reutiliza.
- La validación: dónde se leen y validan los parámetros; el `400` en `ApiExceptionHandler`, con mensajes propios; las horas, sin depender del idioma de la máquina; y los días sin distinguir mayúsculas, convertidos al valor del día que usa el modelo. La base guarda el nombre del día en mayúsculas (`MONDAY`, por `@Enumerated(EnumType.STRING)`; comprobado en la entidad y en dos tests que la leen con SQL); el usuario pidió revisarlo al elegir a qué convertir lo que llega.
- El texto de la decisión 005: los códigos generales del contrato común (`INTERNAL_ERROR` y `VALIDATION_ERROR`) los pone el manejador, y los de cada slice, su controller.
- Las piezas y sus nombres, el `503` en el controller nuevo y las excepciones a la skill, entre ellas la ruta que fija el contrato.
- Los tests: reutilizar la configuración de contexto de C04 para no sumar arranques; antes de escribirlos, leer las lecciones de tests de los `CLAUDE.md` de turnos y de catálogo; si se repite la base detenida, forzar el camino del pool (la espera de 1 s de C04); y CA-15 sin márgenes de tiempo ajustados.
- Una prueba manual con la aplicación y la integración apagada, como evidencia de los filtros (enunciado §11), con el OK del usuario.

Decisiones tomadas:

- **Aprobación (2026-11-20):** el usuario aprobó la SPEC después de aplicar el texto de la fila de `dayOfWeek` del contrato. Ese mismo día aprobó un cambio posterior, que salió de P5 del PLAN: las comas como separador de días (D8.2, RF-07, CA-10 y CA-18).
- **2026-11-20 · Carpeta y rama:** `docs/sdd/c05-buscar-profesionales/`; se trabaja en `buscar-profesionales`, que el usuario creó desde `listar-categorias`.
- **D1 · El `id` de cada horario (2026-11-20): opción a), incluirlo y corregir el caso de uso.** C05 paso 5 no lo listaba; el contrato §2.2 lo incluye, y C06 también (caso de uso, paso 5; contrato §3.1, "el mismo objeto que cada elemento de `items` en C05"). Se agregó `id` a los campos del horario en C05 paso 5, en `documentacion` y en la copia del servicio, que siguen idénticas. El usuario autorizó aplicarlo sin ver antes el texto, solo esta vez. A turnos no le cambia nada: su cliente de C06 ya lee una respuesta con ese `id`. Alternativas descartadas: b), quitarlo del contrato, que obligaba a cambiar también el §3.1 o el caso de uso de C06 y dejaba de ser el mismo objeto en C05 y C06; c), incluirlo sin tocar los documentos, que dejaba la contradicción en la fuente de verdad.
- **D2 · La disponibilidad cuando falta una parte (2026-11-20): opción a), cada parte se aplica por su cuenta** (RF-04). La frase del contrato §2.2, "si falta `dayOfWeek` o falta la franja, ese criterio no se aplica", leída al pie de la letra, dejaba sin filtro una búsqueda con solo días; chocaba con C05 paso 1 ("`from` y/o `to`", filtros que se combinan) y con A04 paso 3 (días "y, si quiere, una franja"). Se aclaró en el contrato junto con D3 (ver "Texto del contrato §2.2"). El caso de uso no cambia: su paso 3 ya habla de "los `dayOfWeek` indicados" y "la franja indicada". Alternativas descartadas: b), al pie de la letra, que ignoraba en silencio un filtro elegido por el usuario y contradecía C05 y A04; c), exigir días y franja juntos con un `400`, que rechazaba lo que A04 permite y obligaba a cambiar dos casos de uso.
- **D3 · Los bordes de la disponibilidad (2026-11-20)** (RF-04):
  - **D3.1 · Tocarse no es solaparse: opción a).** Un horario se solapa con la franja si empieza antes de que ella termine y termina después de que empiece. Alternativas descartadas: b), que tocarse cuente, que mostraba a alguien que no atiende en ningún momento de la franja (de 08:00 a 12:00 con `from` 12:00); c), exigir que entre un turno entero, que repetía en catálogo el cálculo de los turnos, que es de turnos (T03), e iba más allá de "se solapa".
  - **D3.2 · Un horario que no termina después de empezar nunca cuenta: opción a).** La sincronización los acepta y, con la regla 6 del dominio, no generan turnos: así "disponible" coincide con "tiene turnos". Es una condición más para un caso que quizá no exista en la cátedra. Alternativas descartadas: b), que cuenten por día y no por franja, que listaba para un día a alguien sin turnos ese día; c), leerlos como que cruzan la medianoche, una lectura que ningún documento da y que turnos no comparte; d), sin regla propia, con la que el mismo horario cumplía o no según qué parte de la franja llegara.
- **Texto del contrato §2.2 (2026-11-20):** el usuario aprobó el texto propuesto y se aplicó en `documentacion` y en la copia del servicio, que siguen idénticas. La frase "si falta `dayOfWeek` o falta la franja, ese criterio no se aplica" se reemplazó por las reglas de D2 y D3: cada parte es opcional; con solo `from` la franja llega al final del día y con solo `to` empieza al comienzo; sin días ni franja no se filtra; tocarse en el borde no es solaparse; y un horario que no termina después de empezar no cuenta.
- **D4 · Qué coincide con "el nombre, el apellido o ambos" (2026-11-20): opción c), cada palabra en el nombre o en el apellido** (RF-03). Tienen que aparecer todas, en cualquier orden. Cubre las tres lecturas del caso de uso ("el nombre", "el apellido" y "ambos"). Alternativas descartadas: a), el texto entero dentro del nombre o del apellido, con la que quien escribe nombre y apellido no encuentra nada, justo el "ambos"; b), el texto entero dentro de "nombre apellido", que depende del orden y de los espacios exactos, cuando escribir "apellido nombre" es lo habitual en una lista ordenada por apellido.
- **D5 · La n y la ñ en la búsqueda (2026-11-20): opción a), son iguales** (RF-03). "nunez" encuentra a Núñez. Busca tolerar cómo escribe el usuario, también desde un teclado sin ñ; un resultado de más no molesta. El orden no cambia por esto (D6): ordenar decide el lugar en la lista, y buscar, a quién se encuentra. Alternativas descartadas: b), la ñ como letra aparte, como en el orden de C04, con la que "nunez" no encuentra a Núñez sin ganar nada; c), que la n encuentre la ñ pero no al revés, casi lo mismo que a) con una regla más difícil de explicar y de construir.
- **D6 · La regla del orden (2026-11-20): opción a), la misma que las categorías** (RF-05; D2 de C04). Alfabético en español por apellido, nombre e `id`: no distingue mayúsculas ni tildes, la ñ va después de la n, y el `id` desempata. La base ya tiene esa regla desde C04; cómo se aplica a los profesionales y cómo convive con D5, donde la ñ es igual a la n, va en el PLAN. Alternativas descartadas: b), el orden por defecto de la base, que depende de con qué idioma se creó cada base, deja la ñ como una n, y ordenaba categorías y profesionales con reglas distintas en la misma app; c), por el código de cada carácter, que pone "Álvarez" después de "Zárate" y "de la Fuente" después de todos los apellidos con mayúscula.
- **D7 · Los horarios en la respuesta (2026-11-20)** (RF-01):
  - **D7.1 · Van todos: opción a).** Habilitados y deshabilitados, hayan cumplido o no el filtro, también los que no terminan después de empezar. Es el mismo objeto que C06, y lo que dicen los documentos ("sus horarios semanales", cada uno con su `enabled`). Alternativas descartadas: b), solo los que cumplieron el filtro, que le mostraba a la app una agenda recortada como si fuera completa y cambiaba el objeto según el filtro; c), solo los habilitados, que dejaba sin sentido el `enabled` de cada horario y contradecía a C06.
  - **D7.2 · Por día de la semana, hora de inicio e `id`: opción a).** De lunes a domingo, como el contrato común §3. Se muestra tal como llega, y C06 lo reutiliza. Alternativas descartadas: b), por `id`, estable pero sin significado para la app, que tendría que reordenar; c), sin orden definido, con una respuesta que puede cambiar entre pedidos y que un test no puede fijar.
- **D8 · El resto de la validación (2026-11-20)** (RF-07). C05 2.1 y el contrato listan casos inválidos, pero no los formatos exactos, los valores vacíos ni los parámetros desconocidos; un `enabled` inválido y una `page` o un `size` no numéricos los cubre la regla general de `VALIDATION_ERROR` (contrato común §2, "formato inválido").
  - **D8.1 · Un parámetro presente pero vacío cuenta como si no viniera: opción a).** Un formulario puede mandar un campo que el usuario borró. Es lo que eligió turnos (D3 de T10), y la app trata la paginación igual en los dos servicios, que comparten el contrato común §3.1. Alternativas descartadas: b), un `400`, que responde "inválido" a algo que el usuario no completó; c), vacío como ausente solo en el texto, que obliga a la app a recordar una regla por parámetro.
  - **D8.2 · Formatos exactos: opción a).** Los del contrato: días en mayúsculas (contrato común §3), `enabled` solo `true` o `false`, horas `HH:mm` o `HH:mm:ss` con dos dígitos, varios días repitiendo el parámetro; lo demás es `400`. Un error de la app aparece enseguida, en lugar de interpretarse en silencio. **Cambio pedido por el usuario el 2026-11-20, al decidir D9 (D8.2 revisada):** los días se aceptan sin distinguir mayúsculas (`monday`, `Monday` y `MONDAY`, opción a); se descartó b), solo todo en mayúsculas o todo en minúsculas, una regla rara de explicar). Con eso, los días son tolerantes, mientras `enabled=TRUE` sigue siendo `400` y turnos distingue mayúsculas en el `status` de T10; se le avisó y queda así. El usuario aprobó el texto para el contrato, y la fila de `dayOfWeek` del §2.2 dice ahora "`MONDAY` a `SUNDAY`, sin distinguir mayúsculas", en `documentacion` y en la copia del servicio, que siguen idénticas. **Segundo cambio, con la SPEC ya aprobada (2026-11-20, P5 del PLAN):** las comas se aceptan como separador de días (`dayOfWeek=MONDAY,FRIDAY` es lo mismo que repetir el parámetro). Una prueba con Spring Boot 4.1.2 mostró que separa las comas antes de que el request se valide, así que con el request validado que eligió P5 no se puede distinguir una forma de la otra; rechazarlas obligaba a leer los parámetros a mano o a tocar la conversión de Spring, y la regla no protegía nada, porque ningún nombre de día tiene comas. El usuario aprobó el cambio y el texto del contrato: la fila dice ahora "Repetible: `dayOfWeek=MONDAY&dayOfWeek=FRIDAY`, o separados por coma: `dayOfWeek=MONDAY,FRIDAY`", en las dos copias, que siguen idénticas. Alternativas descartadas: b), lo que acepte Spring por defecto (por ejemplo `yes`, `on` o `1` como verdadero, o listas con comas), con reglas que decide la librería y pueden cambiar con su versión; c), tolerante a propósito, que acepta más de lo que dice el contrato y suma casos sin que nadie lo pida.
  - **D8.3 · Los parámetros desconocidos, incluido `sort`, se ignoran: opción a).** Como en C04 (aprobado en su SPEC). Riesgo aceptado: un parámetro mal escrito (por ejemplo, `dayofweek`) se ignora y la búsqueda devuelve más; la app es el único cliente y sus pruebas lo detectan. Alternativas descartadas: b), un `400`, un error que el contrato no define, que rechaza pedidos que el contrato no prohíbe y contradice C04; c), `400` solo para `sort`, un caso especial sin ganancia, porque ignorarlo ya deja el orden fijo.
- **D9 · La forma de `fieldErrors` (2026-11-20)** (RF-07). El contrato común §2 solo dice que es una lista de `{ "field", "message" }`.
  - **D9.1 · Todos los errores juntos, uno por parámetro: opción a).** La app corrige todo de una vez (A04 7.1). Alternativa descartada: b), solo el primero, que obliga a ir y volver una vez por error.
  - **D9.2 · "`from` no es anterior a `to`" va en `from`: opción a).** El contrato pone esa regla en la fila de `from`, y así cada problema es una sola entrada; la app puede marcar las dos horas si quiere. Si una de las dos horas está mal escrita, solo se informa su formato. Alternativas descartadas: b), en `to`; c), en los dos, que duplica el mensaje de un solo problema.
  - **D9.3 · Textos fijos propios, en inglés, uno por regla: opción a).** Siguen el estilo de los `detail` de C04 y de los `fieldErrors` de turnos; la app decide qué mostrar por el campo (contrato común §2: decide por `status` y `code`, no por los textos). Alternativas descartadas: b), en castellano, que mezcla idiomas en la API y ata la pantalla de la app a los textos del servicio; c), los de Spring, que pueden mostrar tipos de Java y cambian con el idioma de la máquina, como encontró turnos.
- **D10 · Lo que se hereda de C04 (2026-11-20): confirmado por el usuario.** No se lee `Authorization` (RF-02). Si falla la base, `500` y no `503`: el contrato §2.2 elige uno de los dos que permite C05 3.3. Sin límite de espera propio para la base, con la limitación aceptada en D4 de C04 (RF-10). CORS va con la validación del JWT (D5 de C04). Solo la falla de la base deja un error en el log. C05 no cambia ninguna de las razones de C04: sus consultas son algo más pesadas, sobre pocos profesionales (24 en la cátedra real).
- **Fuera de alcance (2026-11-20):** el usuario confirmó la lista. Las pruebas contra la cátedra real quedan afuera porque C05 no interactúa con ella.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, las referencias apuntan a pasos y flujos
que existen, los flujos son coherentes, los puntos de backend relevantes están
cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
