# TASKS: C06 — Consultar horarios semanales vigentes de un profesional

SPEC: `docs/sdd/c06-horarios-profesional/SPEC.md` (aprobada el 2026-11-22, con el ajuste S1 aprobado ese día). PLAN: `docs/sdd/c06-horarios-profesional/PLAN.md` (aprobado el 2026-11-22). Tareas aprobadas por el usuario el 2026-11-22. La implementación sigue su autorización, tanda por tanda (tanda 1, TASK-01 a TASK-03: autorizada el 2026-11-22, al aprobar las tareas; tanda 2, TASK-04: autorizada el 2026-11-22, después de los commits `892d869`, `dfb2db3` y `acf235f`; tanda 3, TASK-05: autorizada el 2026-11-22, después del commit `7a39118`).

Las tareas siguen el "Orden de implementación" del PLAN y cada una deja la suite en verde. Toda tarea con tests incluye, después de que pasan, una prueba en negativo: romper el código a propósito, ver fallar el test, restaurar el archivo y comprobar el hash. Los tests se cuentan con los reportes XML (`target/surefire-reports/TEST-*.xml`) después de `clean`, por `testcase` (Surefire suma mal las `@Nested`); al cerrar la validación del JWT eran 728 casos en 81 clases.

Nada depende de la velocidad de la máquina: ningún test detiene ni pausa la base (la falla se provoca renombrando una tabla), la foto única se fuerza en el mismo hilo con un espía, y los tokens vencen en una hora. Ninguna tarea se conecta a la cátedra real, a su Kafka ni a su Redis.

Los comandos de git los ejecuta el usuario. Al terminar cada tanda se le da, por cada tarea, su explicación de archivos, su mensaje de commit y su lista de archivos. Si dos tareas de una tanda tocan el mismo archivo, se frena entre una y otra para que commitee. `TASKS.md` va con la última tarea de cada tanda.

Tandas propuestas (cada una espera la orden del usuario): 1 = TASK-01, TASK-02 y TASK-03; 2 = TASK-04; 3 = TASK-05; 4 = TASK-06, TASK-07 y TASK-08; 5 = TASK-09; 6 = TASK-10. TASK-04 y TASK-05 tocan `ProfessionalController`, `ApiExceptionHandler` y `ProfessionalControllerTest`: van en tandas distintas, así que la primera se commitea antes de la segunda. Dentro de las tandas 1 y 4, las tareas no comparten archivos.

## Etapa 1 · El caso de uso y sus lecturas

- [x] **TASK-01 · El orden compartido de los horarios**
  - Alcance:
    - `catalog/domain/model/WeeklySchedule`: suma `BY_DAY_START_AND_ID`, un `Comparator` público y constante por día de la semana, hora de inicio e `id` (P3).
    - `catalog/application/usecases/SearchProfessionalsUseCaseImpl`: reemplaza su `SCHEDULE_ORDER` privado por el nuevo. Sin cambio de comportamiento.
    - No se toca ningún test: el de C05 tiene que seguir en verde tal como está.
  - Depende de: ninguna
  - Resuelve: la base del orden de CA-01; la regresión del orden de C05
  - Validación:
    - Línea base antes de tocar nada: `./mvnw -q clean test`, conteo en los XML.
    - `./mvnw test -Dtest='SearchProfessionalsUseCaseImplTest,ProfessionalsIntegrationTest,CatalogPersistenceMappersTest'` en verde, sin cambios en los tests (los de los mappers de persistencia, para ver que el campo estático no cambia lo que mapean).
    - Prueba en negativo: el comparador sin el día; falla `eachProfessionalOfThePageComesWithItsSchedulesByDayStartAndId`. Restaurar y comprobar el hash.
  - Resultado: 2026-11-22 (tanda 1, autorizada ese día; los documentos de C06 ya estaban en el commit `0f2f423`).
    - Modificados `WeeklySchedule` (suma `BY_DAY_START_AND_ID`) y `SearchProfessionalsUseCaseImpl` (usa el comparador del modelo; se quitaron su `SCHEDULE_ORDER` y el import de `Comparator`).
    - Línea base, antes de tocar nada: `./mvnw -q clean test`, 728 casos en 81 clases, 0 fallos ni errores, 151 s.
    - `./mvnw test -Dtest='SearchProfessionalsUseCaseImplTest,ProfessionalsIntegrationTest,CatalogPersistenceMappersTest'`: 6, 19 y 5 casos, 0 fallos ni errores, sin cambiar ningún test.
    - Prueba en negativo: el comparador sin el día (solo inicio e `id`); falló únicamente `eachProfessionalOfThePageComesWithItsSchedulesByDayStartAndId` (1 de 6). Restaurado `WeeklySchedule`: `sha256sum -c` coincide.

- [x] **TASK-02 · Leer un profesional por id**
  - Alcance:
    - `catalog/domain/ports/out/ProfessionalRepository`: suma `Optional<Professional> findById(Long id)` (P2).
    - `catalog/infrastructure/persistence/adapter/JpaProfessionalRepositoryAdapter`: lo implementa con `findById(id).map(professionalMapper::toDomainModel)`.
    - `CatalogRepositoryAdaptersTest`: casos nuevos contra PostgreSQL.
  - Depende de: ninguna
  - Resuelve: CA-01 y CA-07 en la persistencia
  - Validación:
    - `./mvnw test -Dtest=CatalogRepositoryAdaptersTest`: un profesional que existe vuelve con todos sus datos y su categoría completa; uno que no existe da vacío; los casos que ya tenía la clase siguen en verde.
    - Prueba en negativo: el adaptador devolviendo siempre vacío; falla el caso del que existe. Restaurar y comprobar el hash.
  - Resultado: 2026-11-22 (tanda 1).
    - Modificados `ProfessionalRepository` (suma `findById`), `JpaProfessionalRepositoryAdapter` (lo implementa con `findById(id).map(professionalMapper::toDomainModel)`) y `CatalogRepositoryAdaptersTest`. Ningún test implementa el puerto a mano (buscado en `src`).
    - `./mvnw test -Dtest=CatalogRepositoryAdaptersTest`: 11 casos (los 9 que tenía y 2 nuevos), 0 fallos ni errores:
      - `findingOneProfessionalByIdReturnsEveryFieldWithItsWholeCategory`: con dos profesionales de categorías distintas, el 15 vuelve igual al guardado, con su categoría completa (comparación recursiva);
      - `findingOneProfessionalThatIsNotInTheCopyReturnsEmpty`: `999`, `0` y `-1` dan vacío.
    - Prueba en negativo: `findById` devolviendo siempre vacío; falló únicamente el caso del que existe (1 de 11). Restaurado el adaptador: `sha256sum -c` coincide.

- [x] **TASK-03 · El caso de uso de C06 y el "no encontrado"**
  - Alcance:
    - `catalog/domain/ports/in/GetProfessionalUseCase` (nuevo): `Optional<ProfessionalWithSchedules> getProfessional(Long professionalId)`.
    - `catalog/application/usecases/GetProfessionalUseCaseImpl` (nuevo): `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` (P5); sin versión local lanza `CatalogNotInitializedException` sin leer al profesional; si no existe, devuelve vacío sin leer horarios; si existe, lee sus horarios con `findAllByProfessionalIds(List.of(id))` y los ordena con `WeeklySchedule.BY_DAY_START_AND_ID` (P2, P3).
    - `catalog/application/exception/ProfessionalNotFoundException` (nueva): subclase de `CatalogException`, con el id en el mensaje.
    - `catalog/application/service/ProfessionalService`: suma `getProfessional(Long)`, que convierte el vacío en `ProfessionalNotFoundException` con `orElseThrow`.
    - `GetProfessionalUseCaseImplTest` (nuevo): unitario, con los puertos simulados con Mockito, como el de C05.
    - Todavía nadie llama al caso de uso desde la web.
  - Depende de: TASK-01, TASK-02
  - Resuelve: CA-01, CA-03, CA-06 y CA-07 en el caso de uso
  - Validación:
    - `./mvnw test -Dtest=GetProfessionalUseCaseImplTest`:
      - sin versión local: lanza `CatalogNotInitializedException` y no consulta al profesional ni sus horarios;
      - un id que no existe: vacío, y no consulta horarios;
      - uno que existe, con horarios cargados fuera de orden: vuelve con todos, ordenados por día, inicio e `id`, y los horarios se piden con su id;
      - uno sin horarios: vuelve con la lista vacía.
    - La suite completa sigue en verde.
    - Pruebas en negativo, por separado: sin ordenar los horarios (falla el del orden); sin revisar el estado (falla el de sin versión local). Restaurar y comprobar el hash.
  - Resultado: 2026-11-22 (tanda 1).
    - Creados `GetProfessionalUseCase`, `GetProfessionalUseCaseImpl`, `ProfessionalNotFoundException` (mensaje "Could not find professional with id: {id}", como el de la skill) y `GetProfessionalUseCaseImplTest`. Modificado `ProfessionalService` (suma `getProfessional` con `orElseThrow`). Nadie construye el service a mano (buscado), así que el constructor nuevo no rompe nada.
    - `./mvnw test -Dtest='GetProfessionalUseCaseImplTest,SearchProfessionalsUseCaseImplTest'`: 4 y 6 casos, 0 fallos ni errores:
      - sin versión local: `CatalogNotInitializedException`, sin ninguna llamada a los puertos del profesional ni de los horarios;
      - el 999 que no existe: vacío, sin llamar al de horarios;
      - el 15, con horarios cargados como 3 (viernes 10:00), 4 (domingo 9:00, deshabilitado), 2 (lunes 14:00), 5 y 1 (lunes 8:00): vuelve el mismo profesional con los horarios 1, 5, 2, 3 y 4, también el deshabilitado; los horarios se pidieron con `List.of(15L)` (Mockito en modo estricto rechaza otra llamada);
      - un profesional sin horarios: la lista vacía.
    - Prueba en negativo 1: sin el `.sorted`; falló únicamente el del orden (1 de 4).
    - Prueba en negativo 2: sin revisar el estado; falló el de sin versión local, y los otros 3 dieron error porque Mockito en modo estricto avisa que el estado preparado ya no se usa. Restaurado el caso de uso las dos veces: `sha256sum -c` coincide.
    - La suite completa: `./mvnw -q clean test`, 734 casos en 82 clases (728 + 2 del adaptador + 4 del caso de uso), 0 fallos ni errores, 152 s.
    - Los cuatro primeros chequeos de la skill (§12): sin salida. El quinto queda para el cierre.

## Etapa 2 · La entrada web

- [x] **TASK-04 · El endpoint de C06**
  - Alcance:
    - `catalog/infrastructure/web/dto/WholeNumber`: su `@Target` suma `PARAMETER` (P1). La regla no cambia.
    - `config/ApiExceptionHandler`: sobrescribe `handleHandlerMethodValidationException` y responde `400` `VALIDATION_ERROR` con el mismo `detail` fijo que el `400` de C05 y un `fieldErrors` por parámetro, con su nombre y el mensaje de la regla, sin el valor (P1). Todavía sin la línea del log (TASK-05).
    - `catalog/infrastructure/web/mapper/ProfessionalDtoMapper`: `toResponse(ProfessionalWithSchedules)` pasa a público.
    - `catalog/infrastructure/web/controller/ProfessionalController`: `GET /{professionalId}` con `@PathVariable @WholeNumber(min = Long.MIN_VALUE, max = Long.MAX_VALUE, message = "must be a whole number") String`, convertido a `Long`; llama a `ProfessionalService.getProfessional`; `ProfessionalNotFoundException` da `404` `PROFESSIONAL_NOT_FOUND` y `CatalogNotInitializedException` da `503` `CATALOG_NOT_INITIALIZED`, con el armado del `503` en un método privado que comparte con C05 (P2). Sin `@Validated` en la clase. Todavía sin la línea del log (TASK-05).
    - `ProfessionalDtoMapperTest`: suma el `toResponse` público, con datos sin nulos.
    - `ProfessionalControllerTest`: suma los casos web de C06.
    - `config/ProtectedRoutesSecurityTest`: cambian a propósito `aValidTokenReachesTheRouteWhateverItsRoles` (los cuatro casos de `/api/professionals/15` pasan a `200`, con el service simulado devolviendo un profesional) y `aNonNumericProfessionalIdWithAValidTokenReachesTheRoute` (pasa a `400` `VALIDATION_ERROR`). El resto de la clase no cambia.
  - Depende de: TASK-03
  - Resuelve: CA-01, CA-04, CA-05, CA-06 y CA-07 en la capa web
  - Validación:
    - `./mvnw test -Dtest='ProfessionalControllerTest,ProfessionalDtoMapperTest,ProtectedRoutesSecurityTest'`:
      - `200` con el JSON exacto del objeto de C05 (`JsonCompareMode.STRICT`) y la misma respuesta con `?x=1`;
      - `404` `PROFESSIONAL_NOT_FOUND` y `503` `CATALOG_NOT_INITIALIZED` en `problem+json`;
      - `abc`, `1.0`, `1e3`, `+15`, `0xF`, `#F`, `" 15"`, `"1 5"` y `9223372036854775808`, codificados en la URL: `400` con `fieldErrors` igual a `[{"field": "professionalId", "message": "must be a whole number"}]`, sin el valor en el cuerpo, y el service no se llama;
      - `015` llega al service como 15, y `0` y `-1` llegan como números;
      - `%0A15` da `400` sin cuerpo y el service no se llama;
      - los dos tests cambiados de `ProtectedRoutesSecurityTest` en verde, y los demás de la clase, sin cambios.
    - Comprobación del PLAN (riesgos): `@WholeNumber` sobre el parámetro dispara la validación de Spring, y el manejador recibe el nombre `professionalId`. Si no, se frena y se vuelve a P1 con el usuario.
    - La suite completa en verde, porque el manejador y el controller también los usa C05.
    - Pruebas en negativo, por separado: quitar `@WholeNumber` del parámetro (fallan los `400`); quitar la sobrescritura del manejador (el `400` sale sin `code` ni `fieldErrors`). Restaurar y comprobar el hash.
  - Resultado: 2026-11-22 (tanda 2). Marcada después de que el usuario decidiera S2 (abajo).
    - Modificados `WholeNumber` (`@Target` con `PARAMETER`), `ApiExceptionHandler` (sobrescribe `handleHandlerMethodValidationException`; arma `fieldErrors` desde `getParameterValidationResults()`, con el nombre del parámetro y el mensaje de cada error, la API vista con `javap` en Spring 7.0.10), `ProfessionalDtoMapper` (`toResponse(ProfessionalWithSchedules)` público y, como pide la skill para un mapper, `null` si recibe `null`) y `ProfessionalController` (el método de C06; el `503` en `catalogNotInitialized()`, que usa también C05; un comentario que avisa que la clase no lleva `@Validated`). Tests: `ProfessionalControllerTest`, `ProfessionalDtoMapperTest` y `ProtectedRoutesSecurityTest`.
    - `./mvnw test -Dtest='ProfessionalControllerTest,ProfessionalDtoMapperTest,ProtectedRoutesSecurityTest'`: 62, 8 y 34 casos, 0 fallos ni errores.
      - `200` con el JSON exacto (`STRICT`) del objeto de C05, con las horas `HH:mm:ss`; con `?x=1&professionalId=99`, la misma respuesta y el service recibe 15.
      - `404` `PROFESSIONAL_NOT_FOUND` y `503` `CATALOG_NOT_INITIALIZED`, en `problem+json`.
      - `abc`, `1.0`, `1e3`, `%2B15`, `0xF`, `%23F`, `%2015`, `1%205`, `15%20`, `%0915`, `%0115`, `%D9%A4%D9%A2`, `9223372036854775808` y `-9223372036854775809`: `400` con `title`, `status`, el `detail` fijo de C05, `code` `VALIDATION_ERROR` y `fieldErrors` igual a `[{"field": "professionalId", "message": "must be a whole number"}]`, y el service no se llamó. El `instance` es la ruta tal como llegó, codificada (por ejemplo, `/api/professionals/%0115`): ver S2.
      - `015`, `0`, `-1`, `-0`, el mayor y el menor `Long` llegan al service como 15, 0, -1, 0 y los dos extremos.
      - `%0A15`: `400` con el cuerpo vacío, y el service no se llamó.
      - `ProtectedRoutesSecurityTest`: `aValidTokenGetsTheProfessionalWhateverItsRoles` (sin `auth`, vacío, `ROLE_USER` y `ROLE_ADMIN`: `200`) reemplaza los cuatro casos de `/api/professionals/15` de `aValidTokenReachesTheRouteWhateverItsRoles`, que sigue con las otras dos rutas (`404`); `aNonNumericProfessionalIdWithAValidTokenReachesTheRoute` pasó a `…ReachesItsValidation` (`400` `VALIDATION_ERROR` en `professionalId`). El resto de la clase, sin cambios.
    - Comprobación del PLAN (riesgos): `@WholeNumber` sobre el parámetro dispara la validación de Spring, y el manejador recibe el nombre `professionalId`. Se cumple.
    - Prueba en negativo 1: sin `@WholeNumber` en el parámetro; fallaron los 14 ids inválidos y nada más (14 de 62).
    - Prueba en negativo 2: sin la sobrescritura del manejador (el método renombrado, sin `@Override`); fallaron los mismos 14, porque el `400` sale con el `detail` "Validation failure", sin `code` ni `fieldErrors`. Restaurados los dos archivos: `sha256sum -c` coincide.
    - La suite completa: `./mvnw -q clean test`, 760 casos en 82 clases (734 + 25 del controller + 1 del mapper), 0 fallos ni errores, 166 s.
    - Los cuatro primeros chequeos de la skill: sin salida.
    - **S2 · El `instance` del `400` (hallazgo):** CA-05 decía "el cuerpo no contiene el valor recibido". El `detail` y los `fieldErrors` no lo contienen, pero el `instance`, que Spring pone en todo error del servicio (también en el `401` de la seguridad), es la ruta del pedido tal como llegó, codificada: con `abc`, `"instance": "/api/professionals/abc"`; con `%0115`, `/api/professionals/%0115`, sin el carácter de control. El usuario eligió a) el 2026-11-22: se aclaró la SPEC (CA-05, su fila en "Cómo se comprueba" y D2) con el texto que aprobó, y el código no cambió.

## Etapa 3 · Las líneas del `sub`

- [x] **TASK-05 · El `sub` en el log**
  - Alcance:
    - `ProfessionalController`: el método de C06 recibe `Principal` y escribe al terminar, en un `finally`, `Professional {id} requested by user {sub}: {resultado}`, con `found with N weekly schedules`, `not found`, `catalog not initialized` o `failed` (P4).
    - `ApiExceptionHandler`: el método de TASK-04 escribe `Invalid parameters for {método} {plantilla de la ruta} requested by user {sub}: {campos}`, con los nombres de los campos y nunca sus valores (P4).
    - `ProfessionalControllerTest`: casos del log con `OutputCaptureExtension`.
  - Depende de: TASK-04 (y su commit)
  - Resuelve: CA-10 en la capa web
  - Validación:
    - `./mvnw test -Dtest='ProfessionalControllerTest,ProtectedRoutesSecurityTest'`, con un token de `sub` `42`:
      - encontrado, no existe, sin inicializar y una falla del service: una línea `INFO` cada uno, con `42`, el id y su resultado;
      - un id inválido con un carácter de control que sí llega al controller (`%0115`): la línea del manejador, con `42`, `GET /api/professionals/{professionalId}` y `professionalId`, sin el valor;
      - sin token, con un token inválido y con `%0A15`: ninguna línea;
      - en ningún caso aparece el token.
    - Comprobación del PLAN (riesgos): el manejador obtiene del pedido el `sub` y la plantilla de la ruta. Si no, se frena y se vuelve a P4 con el usuario.
    - La suite completa en verde.
    - Pruebas en negativo, por separado: la línea sin el `sub` (fallan los del log); la línea fuera del `finally`, solo al final del camino feliz (falla el de la falla del service). Restaurar y comprobar el hash.
  - Resultado: 2026-11-22 (tanda 3, autorizada ese día después del commit `7a39118`).
    - Modificados `ProfessionalController` (`@Slf4j`; el método de C06 recibe `Principal`, guarda el resultado en una variable y escribe la línea en el `finally`; el "found" se anota recién después de armar la respuesta), `ApiExceptionHandler` (el método del `400` escribe la línea con el método, la plantilla de la ruta, que toma de `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`, el `sub` del pedido, o `anonymous` si no lo hubiera, y los nombres de los campos) y `ProfessionalControllerTest` (6 casos nuevos; el token en su propia constante).
    - `./mvnw test -Dtest='ProfessionalControllerTest,ProtectedRoutesSecurityTest'`: 68 y 34 casos, 0 fallos ni errores. Con el token de `sub` `42`, cada caso de log encontró exactamente una línea `requested by user` y nunca el token:
      - `Professional 15 requested by user 42: found with 2 weekly schedules`;
      - `Professional 999 requested by user 42: not found`;
      - `Professional 15 requested by user 42: catalog not initialized`;
      - `Professional 15 requested by user 42: failed`, antes del `Unexpected error` con la traza;
      - `%0115`: `Invalid parameters for GET /api/professionals/{professionalId} requested by user 42: professionalId`, y el carácter `\u0001` no aparece en la salida;
      - sin token, con un token alterado y con `%0A15`: ninguna línea, ni el token, y el service no se llamó.
    - Comprobación del PLAN (riesgos): el manejador obtiene del pedido el `sub` y la plantilla de la ruta. Se cumple.
    - Prueba en negativo 1: la línea del controller con `?` en lugar del `sub`; fallaron los 4 tests del log del controller (4 de 68).
    - Prueba en negativo 2: la línea solo al final del camino feliz, sin el `finally`; fallaron los 3 que no terminan en `200` (no existe, sin inicializar y la falla). Restaurado el controller las dos veces: `sha256sum -c` coincide.
    - La suite completa: `./mvnw -q clean test`, 766 casos en 82 clases (760 + 6), 0 fallos ni errores, 168 s.
    - Los cuatro primeros chequeos de la skill: sin salida.

## Etapa 4 · La aplicación completa

- [ ] **TASK-06 · C06 con el servidor real**
  - Alcance: `catalog/ProfessionalsIntegrationTest` suma los escenarios de C06, con sus cuatro profesionales de prueba, la ayuda que renombra tablas y la cátedra simulada como testigo (P6.1).
  - Depende de: TASK-05
  - Resuelve: CA-01, CA-02, CA-03, CA-04, CA-05, CA-06, CA-07, CA-08 y CA-10 con el servidor real
  - Validación, `./mvnw test -Dtest=ProfessionalsIntegrationTest`:
    - CA-01: el 601 por C06 es idéntico a su elemento en C05, con los horarios en orden; con `?x=1`, igual.
    - CA-02: el 602 (deshabilitado) y el 603 (categoría deshabilitada), `200` con sus `enabled`.
    - CA-03: el 604, `weeklySchedules: []`.
    - CA-04: sin token y con uno alterado, `401` sin sus datos; con tokens sin `auth`, `ROLE_USER` y `ROLE_ADMIN`, `200`.
    - CA-05: `abc`, `400` con la forma de D2; `/api/professionals/`, la respuesta de una ruta inexistente; `%0A15`, `400` sin cuerpo (si Tomcat responde otra cosa, se frena y se trae como hallazgo para la SPEC).
    - CA-06: sin versión local, `503` `CATALOG_NOT_INITIALIZED` con y sin el profesional en la base, y la cátedra simulada no recibe pedidos después del login del arranque.
    - CA-07: `0`, `-1` y un id inexistente, `404` `PROFESSIONAL_NOT_FOUND`.
    - CA-08: con la tabla de profesionales y, aparte, la de horarios renombradas, `500` `INTERNAL_ERROR` sin el nombre de la tabla ni de la excepción, y el error con la traza en el log; las tablas vuelven a su nombre.
    - CA-10: un método por resultado, cada uno con su línea y el `sub`, y el de la falla con la línea `failed` y el error; nunca el token.
    - La clase entera en verde, también sus tests de C05.
    - Prueba en negativo: `findById` devolviendo siempre vacío; fallan los de CA-01 a CA-03. Restaurar y comprobar el hash.
  - Resultado: pendiente

- [ ] **TASK-07 · La foto única de C06**
  - Alcance: `catalog/ProfessionalsConsistencyIntegrationTest` suma un método: con el espía que ya tiene sobre `ProfessionalRepository`, después de `findById` confirma desde otra conexión un cambio del nombre del profesional y de sus horarios, y la respuesta de C06 tiene que mostrar entera la versión anterior (P5, P6).
  - Depende de: TASK-04
  - Resuelve: CA-09
  - Validación:
    - `./mvnw test -Dtest=ProfessionalsConsistencyIntegrationTest`: los dos métodos en verde.
    - Prueba en negativo: `GetProfessionalUseCaseImpl` sin `REPEATABLE READ`; la respuesta muestra los horarios nuevos y el test falla. Restaurar y comprobar el hash.
  - Resultado: pendiente

- [ ] **TASK-08 · La ruta protegida no deja ver datos**
  - Alcance: `config/SecurityIntegrationTest`: `anyOtherRouteWithoutATokenIsRejectedWithoutData` inserta el profesional 15 con un nombre propio, y el cuerpo del `401` no lo contiene (P6).
  - Depende de: TASK-04
  - Resuelve: CA-04 con datos reales en la ruta
  - Validación:
    - `./mvnw test -Dtest=SecurityIntegrationTest`: en verde.
    - Prueba en negativo: sumar `GET /api/professionals/{professionalId}` a `PUBLIC_ENDPOINTS`; el caso de esa ruta falla, porque responde `200` con el nombre. Restaurar y comprobar el hash.
  - Resultado: pendiente

## Etapa 5 · La prueba con turnos real

- [ ] **TASK-09 · C06 con turnos real (la corre el usuario)**
  - Alcance: prueba manual con los dos servicios en local, sin la cátedra (P7). Se le pasa al usuario la lista exacta de pedidos y los comandos; los nombres de las variables de turnos se confirman en su `application.yaml` antes. El script vive en el scratchpad, guarda el token solo en memoria y deja un resultado sin él. Deja un usuario de prueba en la base de desarrollo de turnos. Al terminar, todo queda como estaba (aplicaciones detenidas, compose en su estado previo), y se borran el script, el resultado y el log.
  - Depende de: TASK-06, TASK-07 y TASK-08
  - Resuelve: CA-11 (la restricción de que turnos lee la respuesta sin cambios)
  - Validación, con el token real de un usuario nuevo de turnos:
    - a catálogo: C06 de un profesional con horarios, `200` e idéntico a su elemento en C05 (`jq`); un id inexistente, `404` `PROFESSIONAL_NOT_FOUND`; `abc`, `400` `VALIDATION_ERROR`; sin token, `401`;
    - a turnos: T03 en una fecha sin atención, `200 []`; T04 a una hora fuera de sus horarios, `400` `INVALID_SLOT`; T03 con el id inexistente, `404` `PROFESSIONAL_NOT_FOUND`; opcional, T03 de un profesional deshabilitado, `409` `PROFESSIONAL_DISABLED`;
    - el log de catálogo: una línea con el `sub` del usuario por cada C06 con token, y el token, 0 veces.
  - Resultado: pendiente

## Etapa 6 · Cierre

- [ ] **TASK-10 · Documentos y verificación final**
  - Alcance:
    - `CLAUDE.md`: la fila de `catalog`; las excepciones de C06 (P8.1); "Para un endpoint protegido nuevo", corrigiendo lo de `CorsSecurityTest`; en "Cómo responde un error", el `400` de un parámetro validado y el aviso de no poner `@Validated` en un controller; lo comprobado con turnos real; los tests y su conteo; `c06-horarios-profesional` en "Documentación del servicio".
    - `docs/decisiones/005`: el punto 4 suma el `400` de un parámetro validado y su línea del log.
    - `docs/decisiones/006`: la tabla suma `ProfessionalNotFoundException`.
    - `docs/decisiones/007`: la trazabilidad describe cómo escribe C06 el `sub`.
  - Depende de: TASK-09
  - Resuelve: P8.2; la revisión de CA-01 a CA-11
  - Validación:
    - `./mvnw clean verify`: la suite completa en verde, con el conteo por `testcase`, y el reporte de JaCoCo.
    - Los cinco chequeos de la skill (§12), con `catalogo` como paquete base: los cuatro primeros sin salida, y el quinto solo con las dependencias ya autorizadas.
    - Cada criterio, de CA-01 a CA-11, con su evidencia registrada en este documento; ninguna afirmación de los documentos sin evidencia.
  - Resultado: pendiente
