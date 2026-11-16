# catalog_service

Servicio de catálogo y sincronización. Los comandos se ejecutan desde la raíz de este repositorio.

Las instrucciones generales del proyecto están en `documentacion/AGENTS.md`. El servicio de turnos (`turnos_service`, con su `CLAUDE.md`) ya está terminado y sirve de modelo; no se copia código entre repositorios.

## Requisitos

- Docker en ejecución: lo usan el compose y los tests.
- JDK 25.
- Un archivo `.env` creado a partir de `.env.example`. No se versiona. Lo leen docker compose y la aplicación. Los valores van sin comillas: la aplicación toma las comillas como parte del valor.
  - Obligatoria: `CATALOGO_DB_PASSWORD`. Sin ella el compose se niega a levantar y la aplicación no arranca (PostgreSQL rechaza la conexión).
  - Base: `CATALOGO_DB_NAME`, `CATALOGO_DB_USER` y `CATALOGO_DB_PORT` (por defecto `catalogo`, `catalogo` y `5433`).
  - Cuenta técnica de la cátedra: `CATALOGO_CATEDRA_BASE_URL`, `CATALOGO_CATEDRA_USERNAME` y `CATALOGO_CATEDRA_PASSWORD`. Es la misma cuenta que usa turnos. Si falta alguna, o la URL no es `http` o `https` válida, el servicio arranca con un warning y sin integración con la cátedra.
  - Opcional: `CATALOGO_PORT` (8081 por defecto; el 8080 lo usa turnos).
- La suite normal de tests nunca lee el `.env` ni llama a la cátedra real.

## Comandos

Todos se ejecutaron y su resultado se observó (TASKS de X01).

| Para | Comando |
| --- | --- |
| Levantar la base de desarrollo y esperar a que esté lista | `docker compose up -d --wait catalog-db` |
| Ver el estado de los contenedores | `docker compose ps` |
| Correr la aplicación (necesita la base levantada) | `./mvnw spring-boot:run` |
| Correr todos los tests (Testcontainers crea su propio PostgreSQL; no usan el compose) | `./mvnw test` |
| Correr una o varias clases de test | `./mvnw test -Dtest='Clase1,Clase2'` |
| Compilar desde cero y correr los tests | `./mvnw clean test` |
| Tests más reporte de cobertura de JaCoCo (`target/site/jacoco/jacoco.xml`), lo mismo que corre GitHub Actions | `./mvnw clean verify` |
| Apagar los contenedores conservando los datos | `docker compose down` |

`docker compose down -v` borra el volumen de la base (`catalog_service_catalog-db-data`): no usarlo para limpiar una prueba. Para una prueba descartable, otro proyecto de compose con otro volumen y otro puerto, y después `down` sin `-v` y `docker volume rm` del volumen de prueba.

## Datos del proyecto

- Spring Boot 4.1.2, Java 25, Maven con wrapper.
- `{basePackage}` de la skill `hexagonal-vertical-slicing`: `catalogo`. Clase principal: `CatalogoApplication`.
- Variables de entorno con prefijo `CATALOGO_` y propiedades propias bajo `catalogo.`.
- La aplicación escucha en el 8081 y la base se publica en el 5433 de la máquina; los dos puertos son configurables.
- `compose.yaml` tiene por ahora solo la base (`catalog-db`, PostgreSQL 18). El `Dockerfile`, la aplicación en el compose y `compose.full-system.yaml` son de la etapa de Docker.
- Dependencias incluidas desde el inicio, aunque algunas todavía no se usan: web, JPA, Flyway, PostgreSQL, validation, Lombok, security, OAuth2 resource server, Kafka, Redis y Testcontainers.
- SonarQube Cloud: organización `programacion2-ignaciomilutin-2026`. Lo dispara `.github/workflows/build.yml` en cada pull request y en cada push a `main` y `develop`. `CatalogoApplication` está excluida del cálculo de cobertura. El usuario revisa SonarQube por su cuenta: no va en los TASKS.
- **Pendiente de revisar por el usuario (detectado el 2026-11-16):** el proyecto que creó en SonarQube Cloud tiene la clave `Programacion2-IgnacioMilutin-2026_catalogo_service`, pero el workflow, desde su primer commit, envía `-Dsonar.projectKey=Programacion2-IgnacioMilutin-2026_catalogo_serviceilutin-2026_catalogo_service`, que parece un error de pegado. No se comprobó a qué proyecto están yendo los análisis.
- El repositorio remoto se llama `catalogo_service` y la carpeta local, `catalog_service`. El compose del sistema completo de turnos busca `../catalog_service`. El usuario decidió resolverlo en la etapa de Docker.

## Arquitectura

Se aplica la skill `hexagonal-vertical-slicing` (`documentacion/.claude/skills/hexagonal-vertical-slicing/SKILL.md`). La estructura inicial del proyecto se hizo sin SDD: cada paquete lo crea el SDD del caso de uso que lo usa.

Slices actuales:

| Paquete | Qué contiene |
| --- | --- |
| `catalogo.catedra` | Pasarela hacia la cátedra, la única puerta. Por ahora, solo la sesión técnica (X01): login técnico al arrancar, estado en memoria y renovación a pedido. Más adelante suma el snapshot, la lectura de Redis y el consumo de Kafka |
| `catalogo.config` | Configuración transversal, sin lógica de negocio. Por ahora, solo `ClockConfig` |

Slices acordados como punto de partida para lo que sigue, sin crear todavía (se confirman en cada PLAN): uno por entidad del catálogo (categoría, profesional y horario semanal, como las tres colecciones de la cátedra) y `synchronization`. Los nombres de los paquetes están sin confirmar. No hay slice `turnos`: catálogo nunca llama a turnos; el endpoint que consume turnos (C06) pertenece al slice del profesional.

### Criterios de diseño acordados con el usuario

- Cada slice tiene solo las piezas que alguien usa; no se crean piezas por simetría con otro slice.
- Lo de turnos se adoptó en bloque solo para X01, porque es el mismo caso de uso en los dos servicios. Desde C01, cada excepción a la skill y cada decisión de diseño se analiza en el contexto de catálogo, con sus opciones; turnos es una opción más, no la respuesta por defecto.
- En X01, la decisión sobre el resultado de un login (habilitar, reintentar o quedar degradada, o error de configuración) está en cada caso de uso, `Start` y `Renew`, aunque quede escrita dos veces (P3 del PLAN de X01). Una regla nueva sobre ese resultado va en los dos. El usuario aclaró que no es un criterio general.

### Excepciones a la skill autorizadas por el usuario

Autorizadas para X01 (P1 de su PLAN). No llevan comentario en el código: el usuario los quitó; quedan listadas acá.

- Paquete `catalogo.config` fuera de los slices.
- Paquetes de infraestructura además de `persistence` y `web`: `infrastructure/client` (cliente HTTP hacia la cátedra) e `infrastructure/startup` (disparador al arrancar).
- Repositorio en memoria en `infrastructure/persistence/adapter`, sin base de datos: `InMemoryTechnicalSessionRepositoryAdapter`.
- `record` inmutables en lugar de clases Lombok (`TechnicalSession`, `TechnicalIntegration`, `TechnicalLoginPolicy`) y un resultado sellado (`TechnicalLoginResult`): la sesión se lee en paralelo mientras un login la reemplaza. Sus `toString` ocultan el JWT y la contraseña de Redis.
- Puerto de salida para una capacidad técnica, no un repositorio: `TechnicalLoginClient`.
- `@Configuration` dentro de la infraestructura de un slice: `CatedraClientConfiguration`.

### Comentarios y nombres en el código

- El código, sus comentarios y los nombres de los tests no citan documentos: ni el contrato, ni el anexo de la cátedra, ni el enunciado, ni IDs de requisitos. Un test se nombra por el comportamiento que comprueba.
- El usuario prefiere pocos comentarios: uno corto por clase que diga qué es, y alguno más solo donde avisa algo que no se ve en el código. Cambia o borra los que sobran; no se restauran.
- Las referencias a documentos van en `docs/sdd/` y en `docs/decisiones/`.

### Cómo usar la sesión técnica (desde C01)

- En cada operación, pedir la sesión con `GetTechnicalSessionUseCase` (JWT técnico e integración, con los datos de Redis y de Kafka). Devuelve vacío si la integración no está habilitada. Nunca guardar una copia en un campo: así siempre se usa la última.
- Si la cátedra responde `401`, o Redis rechaza las credenciales, llamar a `RenewTechnicalSessionUseCase.renewTechnicalSession(sesionRechazada)` y repetir la operación una sola vez con la sesión que devuelve. Si devuelve vacío, no hay una sesión distinta: la integración no está disponible. Repetir la operación es responsabilidad de cada caso de uso, no de X01.
- Si se necesita la integración y no está habilitada, se puede pedir con `renewTechnicalSession(null)`: intenta un login solo si pasó el intervalo mínimo.
- El control de los logins vive en `catedra` (decisión 001): uno a la vez, como máximo uno cada 60 segundos, y ninguno más hasta reiniciar si hubo un error de configuración. Los demás slices nunca llaman al login de la cátedra.
- Usar `catedra` desde otro slice es una dependencia entre slices: se decide con el usuario en el PLAN (skill §9).
- Las tareas que arrancan con el servicio encuentran la sesión en "conectando": el login inicial corre en un hilo aparte después de que el servicio quedó listo. Cómo espera la verificación del arranque a ese login se define en C03.

### Lo comprobado con la cátedra real

- El login técnico de catálogo funciona con la cuenta del proyecto y responde `PROVISIONED` (2026-11-16).
- Dos JWT técnicos de la misma cuenta conviven: un login nuevo no invalida el anterior. Turnos y catálogo hacen cada uno su login (2026-11-15).
- El Redis de la cátedra todavía no se probó desde ningún servicio. Antes de usarlo hay que comprobar, con lecturas que corre el usuario, cómo se conecta y la forma real de los datos.
- Las pruebas contra la cátedra real las corre el usuario en su terminal, después de ver la lista de pedidos; la salida se revisa sin mostrar secretos y los archivos temporales se borran.

## Tests

- La suite normal nunca lee el `.env` local: el `pom.xml` apunta `catalogo.env-file` a un archivo inexistente (propiedad `tests.env-file`).
- `TestcontainersConfiguration`: solo PostgreSQL 18. Lo importan los tests que levantan la aplicación. Redis y Kafka quedan para los tests que los usen.
- `FakeHttpServer` (`src/test/java/catalogo/`): servidor HTTP del JDK en un puerto libre, para simular sistemas externos. Respuestas en orden por ruta (la última se repite), demoras y registro de los pedidos.
- `FakeCatedraServer` (`src/test/java/catalogo/catedra/`): cátedra simulada sobre `FakeHttpServer`. Por defecto responde un login técnico exitoso; arma también respuestas con otro estado, sin un dato o con un rechazo. La respuesta de ejemplo está en `src/test/resources/catedra/login-200.json`, con valores ficticios.
- `TechnicalIntegrations` (`src/test/java/catalogo/catedra/`): integraciones de ejemplo, completas o sin un dato.
- `ScriptedLoginClient` (junto a los tests de los casos de uso de `catedra`): cliente de login falso que responde en orden y cuenta las llamadas. `MutableClock` (`src/test/java/catalogo/`): reloj que el test adelanta.
- Integración con la aplicación completa: clases `*IntegrationTest` con `@SpringBootTest` e `@Import(TestcontainersConfiguration.class)`, con la cátedra simulada configurada por `@DynamicPropertySource`. Un escenario por clase, porque cada uno necesita su propia aplicación arrancada.
- La salida se captura por método (`OutputCaptureExtension`): un log que salió durante otro método de la misma clase no se ve. Si un test mira el log de algo que ocurre una sola vez, como el login del arranque, la clase tiene un solo método.
- Para contar los tests de una corrida, usar los reportes XML (`target/surefire-reports/TEST-*.xml`).
- Práctica acordada: después de que un test pasa, una prueba en negativo (romper el código a propósito, ver que el test falla, restaurar el archivo y comprobar el hash).
- No usar `pkill -f` ni `pgrep -f` con un texto que esté en el propio comando: se corta o se encuentra a sí mismo.

## Documentación del servicio

- `docs/`: copias de los casos de uso, los contratos, el dominio y las secuencias. La fuente de verdad está en el repositorio `documentacion`. `contratos_comunes.md` y el caso de uso X01 tienen que ser idénticos en los tres repositorios: compararlos al empezar cada SPEC.
- `docs/sdd/<id>-<nombre-corto>/`: SPEC, PLAN y TASKS de cada funcionalidad. Hecha: `x01-cuenta-tecnica`.
- `docs/decisiones/NNN-titulo.md`: textos de las decisiones complejas. `001`: sesión técnica con la cátedra.
- El `README.md` lo escribe el usuario.
