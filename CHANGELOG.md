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
### Changed
- Documentacion de dominio y secuencia debido a decisoones tomadas acerca de la sincronziacion completa.