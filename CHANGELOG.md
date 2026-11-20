# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 2026-10-13
### Added
- Documentacion relacionada con el servicio: contratos con otros servicios, casos de uso, modelo de dominio y diagramas de secuencia.

## 2026-11-15
### Added
- Archivos de configuracion, dependencias, sonarqube y estrucutra inicial.

## 2026-11-15
### Added
- Documentacion de SDD para el caso de uso X01-CuentaTecnica.
- '.env-example' con puerto y configuracion de base de datos.
- Configuracion en TestContainers para la base de datos.
### Changed
- 'application.yaml' y 'compose.yaml' para que utilice el puerto y la configuracion de la base de datos.

## 2026-11-16
### Added
- En tests, servidor http para estos y servidor que simula a la catedra.
- Archivos de domain(modelos y puertos) en slice 'Catedra' para la integracion de la cuenta tecnica.
- Cliente http para la sesion tecnica en slice 'Catedra'.
- Variables del servicio de catedra para iniciar sesion y comunciarse añadidos a '.env-example' y 'application.yaml'.
- Servicio e implementacion de los casos de uso del inicio de sesion de la cuenta tecnica en el slice 'Catedra'.
- Reloj del servicio en nuevo slice 'Config'.
- Disparador del login tecnico inicial cuando termina de iniciar el servicio. 

## 2026-11-17
### Added
- Documentacion de SDD para el caso de uso C01-SincronizacionCompleta.
- Creacion de tablas y migracion de la base de datos local de catalogo. 
- Archivos de dominio del nuevo slice 'Catalog' (Tablas de la DB y el stado de sincronizacion).
- Entidades de la persistencia en slice 'Catalog' y mappers del dominio a entidad y viceversa.
- Repositorios JPA de las entidades del slice 'Catalog'.
- Modelos de ejemplo para los tests y tests para los modelos del dominio(CatalogSyncState), y los mappers de la persistencia del slice 'Catalog'.
- Puertos de salida de los repositorios en slice 'Catalog'.
- Adaptadores en la persistencia de slice 'Catalog'.
- Tests de los adaptadores y limpiador de la base de datos de los test en slice 'Catalog'.
- Puertos de entrada de los casos ReplaceCatalogUseCase y RecordSyncFailureUseCase de uso de slice 'Catalog'.
- Implementacion de los casos de uso en slice 'Catalog'.
- Modelo del snapshot en slice 'Catedra', con sus puertos de salida y de entrada.
- Test de los modelos del snapshot del slice 'Catedra' y un ejemplos del snapshot para utilizar en los tests.
- Ciente HTTP del snapshot en slice 'Catedra'. incluye DTOs, mappers, adaptadores, configuraciones y validaciones.
- Tests del cliente HTTP para el snapshot en slice 'Catedra'.
- Implementacion del caso de uso para obtener el snapshot de la catedra con sus tests en slice 'Catedra'.
- Nuevo slice 'Syncronization'. Archivos del dominio, incluye modelo y puerto de entrada para la sincronizacion completa.
- Archivos de aplicacion al slice 'Syncronization', incluye implementacion del caso de uso de la sincronizacion completa, el candado y el servicio.
- Tests de la sincronizacion completa del catalogo.
- Test E2E que testea la sincronizacion completa.
### Changed
- Documentacion de dominio y secuencia debido a decisoones tomadas acerca de la sincronziacion completa.
- El pom.xml se actualizo para que el test E2e.

## 2026-11-18
### Added 
- Documentacion de SDD del caso de uso C03-VerificacionPeriodica.
- CatedraEntityMapper, verificacion de formatos de la catedra conjunta unificada tanto para el snapshot como para redis. 
- Test para CatedraEntityMapper.
- Lectura y escritura que realiza la verificacion. Solo escribe si esta al dia y lo ultimo que registra es un fallo.
- Archivos para aplicar una sincronizacion incremental, verificando que no rompa la integridad de la base de datos. Tambien sus test. Todo esto en slice 'Catalog'.
- Excepciones del slice 'Catalog'.
- Dependencias en pom.xml incluy lettuce-core en vez del starter de redis.
- En tests un redis simulado para no utlizar el real en los tests.
- Modelos, politicas y mapper que valida para redis y sus tests en slice 'Catedra'.
- Cliente de Redis, su adaptador y sus tests en slice 'Catedra'.
### Changed 
- Documentacion actualizada debido decisiones tomadas para aplicar el casod e uso C03-VerificacionPeriodica.
- Se renombraron los modelos del snapshot de categoria, profesional y horario semanal del slice 'Catedra' a CatedraCategory y asi. Esto debido a que tambien lo utilzara Redis por lo que se necesita un nombre acorde a la utilizacio por parte de ambos.
- Se renombraron los dto de respuesta del cliente del snapshot de categoria, profesional y horario semanal del slice 'Catedra' con el prefijo 'Catedra'. Esto debido a que tambien lo utilzara Redis por lo que se necesita un nombre acorde a la utilizacion por parte de ambos.
- CatalogSnapshotResponse y CatalogSnapshot utilizan lo nuevos nombres. 
- Se adaptaron los tests al cambio de nombre de los archivos y clases.
- Ahora CatalogSnapshotMapper solo valida lo del snapshot no los formatos de las entidades, se actualizaron y borraron algunos de sus tests debido a estos cambios. 
- Modificacion de archivos para que un fracaso no cambie el ultimo tipo de sincronizacion exitosa. Actualizados y añadidos tests por esto.

## 2026-11-19
### Added 
- Lector de redis e implementacion de los casos de uso para leer redis en slice 'Catedra'.
- Configuracion para esperar a que se realice el login tecnico.
- Verificador de la version del catalogo y aplica los cambios si es necesario, en slice 'Syncronization'.
- Disparador periodico de la verificacion en slice 'Syncronization'.
- Tests de integracion para C03-VerificacionPerioca. 
- Nuevas variables confgurables en .en.example y application.yaml para que las utilice.
- Documentacion de SDD del caso de uso C02-SincronizacionIncremental.
- Resultados para catchUp, en slice 'Synchronization'.
- Funcion a synchrinizationLock para tomar el lock pero epserar a que se libere.
- Entidad de ProcessedEvent con repsoitorio y demas en slice 'Synchronization', para alamcenar los avisos de la catedra que ya se procesaron.
### Removed 
- Excepciones en slice 'Catalog' utilizadas para aplicar una version del catalogo, ahora devuelve un record con el error y resultado.
### Changed
- Ahora cuando falla la aplicar una version del catalogo (slice 'Catalog') devuelve un record de resultado. Esto para poder obtentener esoe resultado en la fachada 'Syncornization'.}
- Ahora la actualizacion de la version esta en un archivo catchUp para que la sincronizacion incremental tambien lo pueda utilizar. En slice 'Synchronization'.

## 2026-11-20
### Added 
- Caso de uso de la sincronizacion incremental en slice 'Synchronization', con sus tests.
- Lectura y validacion del aviso actualizacion del catalogo en slice 'Catedra'.
- Caso de uso en slice 'Catedra' para enviar el aviso al slice 'Syncornization' para que lo utilice la sincronizacion incremental.
- Listener para enterarse cuando el login tecnico ya termino. 
- Consumer del topic de kafka.
- Contenedro de kafka para los test.
- Tests de integracion de lo implementado en el caso de uso C02-SincronizacionIncrmental y Kafka.
- Test E2E de sincronizacion incremental.
- Documentacion de SDD y decisiones para caso de uso C04-ListarCategorias.
- Colacion de la base de datos, puerto y funcion en el repositorio para devolver las categorias en formato alfabetico en español.
- Caso de uso, service y excepciones para listar categorias en slice 'Catalog'.
- DTO y DTOMapper par la respuesta web a la app en slice 'Catalog'.