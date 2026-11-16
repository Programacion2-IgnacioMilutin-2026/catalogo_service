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