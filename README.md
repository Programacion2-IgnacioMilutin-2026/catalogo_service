<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-72%2F100-green) ![Aprobar](https://img.shields.io/badge/Aprobar-no-red) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey) ![Build](https://img.shields.io/badge/Build-OK-green) ![Duplicación](https://img.shields.io/badge/Duplicaci%C3%B3n-3.0%25-orange) ![%POO](https://img.shields.io/badge/%25POO-74%25-yellow) ![Sueltas](https://img.shields.io/badge/Sueltas-0-green) ![D1](https://img.shields.io/badge/D1-5-red) ![D2](https://img.shields.io/badge/D2-100-green) ![D4](https://img.shields.io/badge/D4-40-orange) ![D5](https://img.shields.io/badge/D5-100-green) ![D6](https://img.shields.io/badge/D6-100-green) ![D7](https://img.shields.io/badge/D7-100-green)

**Calidad (heurístico, 7 dimensiones):** índice **72/100** · build **OK** · aprobar **no (7/11)** · destacar **no**
Detalle completo en [`docs/EVALUACION.md`](docs/EVALUACION.md).
<!-- calidad:fin -->

# Servicio de Catalogo y Sincronizacion

>**Materia:** Programación2 - TrabajoFinal\
>**Alumno:** Ignacio Milutin ; Legajo: 62144

En este repositorio se encuentra el servicio encargado de almacenar el catalogo de profesionales, categorias y horarios semanales. A continuacion se muestra la estructura del proyecto:

```
 ├── 📦 docs
 │   │
 │   ├── 📁 casos_de_uso  
 │   ├── 📁 contratos
 │   ├── 📁 secuencias
 │   ├── 📄 catalogo_y_sincronizacion_dominio.md
 │   │
 │   ├── 📁 decisiones
 │   └── 📁 sdd
 │
 ├── 📦 src/
 │   │
 │   ├── 📦 main
 │   │    │  
 │   │    ├── 📦 java/catalogo   
 │   │    │   │
 │   │    │   ├── 📁 catalog
 │   │    │   ├── 📁 catedra
 │   │    │   ├── 📁 config
 │   │    │   ├── 📁 syncronization
 │   │    │   └── 📄 CatalogoApplication
 │   │    │
 │   │    └── 📦 resources
 │   │        │
 │   │        ├── 📁 db/migration
 │   │        └── 📄 application.yaml
 │   │
 │   └── 📦 test/java
 │       │
 │       ├── 📁 e2e
 │       └── 📁 catalogo
 │
 │
 ├── 📦 .github
 │
 ├── 📦 .mvn/wrapper
 │
 ├── 📄 .env.example
 │
 ├── 📄 Dockerfile 
 ├── 📄 .dockerignore
 ├── 📄 compose.yaml
 ├── 📄 compose.full-system.yaml
 │
 ├── 📄 mvnw
 ├── 📄 mvnw.cmd
 ├── 📄 pom.xml
 │
 ├── 📄 .gitignore
 ├── 📄 .gittatributes
 ├── 📄 CLAUDE.md
 ├── 📄 CHANGELOG.md
 └── 📄 README.md
```
---

##  Instalación y Ejecucion

Para ejecutar el servicio sigue los siguientes pasos:

### Requisitos:
- Correr el servicio: **Docker con Compose V2**. Si se corre en conjunto con el servicio de turnos es necesario **Compose 2.23.3**.
- Desarrollar y correr tests: **JDK 25**, sumado a los requisitos para correr el servicio.

### Instalación:

1- Clonar el repositorio:

SSH:
```
git clone git@github.com:Programacion2-IgnacioMilutin-2026/catalogo_service.git
```
HTTPS:
```
git clone https://github.com/Programacion2-IgnacioMilutin-2026/catalogo_service.git
```

2- Copiar [.env.example](.env.example) como **.env** y completar las variables de entorno obligatorias. Completar las opcionales si es necesario. Las opcionales no completadas se toman con los valores por defecto que figuran en el .env.example.

### Ejecución:

Hay dos formas de ejecución

- Para ejecutar solo el servicio de catalogo individualmente:

```
docker compose up -d --build
```

- Para ejecutar junto con el servicio de turnos:

```
docker compose -f compose.full-system.yaml up -d --build
```
Es el mismo comando en ambos servicios. Deben estar ambos servicios clonados en el mismo directorio raiz. El directorio clonado de los servicios debe tener el nombre originario del repositorio: **turnos_service** y **catalogo_service**.

### Detener Ejecución:

Para apagar si esta ejecutando solo el servicio de catalogo ejecute el siguiente comando:
```
docker compose down
```

Para apagar si esta ejecutando ambos servicios:

```
docker compose -f compose.full-system.yaml down
```

### Tests:

Para ejecutar los tests:

```
./mvnw test
```

Para ejecutar los tests End to End (E2E):

```
./mvnw test -Pe2e
```
Asegurarse de completar las variables de entorno necesarias para el test E2E.

---

---

## Documentación

En la carpeta de [Documentacion](./docs) se ecuentra toda la documentacion del servicio. Se tiene su modelo de dominio, casos de uso, contratos con los otros servicios y diagramas de secuencia. 

Luego se tiene una carpeta relacionada al [Spec-Driven Development](./docs/sdd). Esta contiene una carpeta para cada funcionalidad, donde se encuentran los archivos relacionados a la implementacion de este proceso de desarrollo de esa funcionalidad especifica. 

Tambien se tiene una carpeta [Decisiones](./docs/decisiones), donde se documentan y se da información sobre distintas decisones tomadas acerca del funcionamiento y configuracion del servicio.

---

## Paquete de Catalogo

A continuacion se tiene una breve explicación acerca del paquete [Catalogo](./src/main/java/catalogo). Se aplica la arquitectura hexagonal con vertical slicing. Aqui tenemos dentro de cada slice un paquete Domain, un paquete Application y un paquete Infrastructure.
- **Catalog:** La copia local del catálogo de la cátedra, con categorías, profesionales y sus horarios semanales, junto con el estado de la sincronización, es decir, qué versión se tiene y cuándo se sincronizó. Aplica cada versión nueva en una sola transacción y atiende las consultas.
- **Catedra:** Maneja la comunicación con la cátedra, por su API REST, por Redis y por Kafka. Mantiene la sesión técnica y pide el catálogo completo. En Redis lee la versión actual y los cambios de cada versión y por Kafka recibe los avisos de versión nueva. Es el único paquete que conoce la cátedra, Redis y Kafka.
- **Config:** Configuración del servicio, sin lógica de negocio. Contiene la seguridad (la validación del JWT que emite turnos, las rutas públicas y CORS), el formato común de los errores de la API, el reloj y las tareas programadas.
- **Syncronization:** El proceso que mantiene la copia local al día con la cátedra. Hace la sincronización completa cuando no hay copia local o cuando no puede ponerse al día versión por versión. Hace la incremental cuando llega un aviso de versión nueva, y una verificación periódica por si se perdió alguno. Asegura que haya una sola sincronización a la vez y que un aviso repetido no se aplique dos veces.

---

## Tests

A continuacion una breve explicacion acerca de los tests y el test E2E

- Test Normales: Prueban el servicio sin depender de nada externo real. Necesitan Docker en ejecución, porque crean sus propios contenedores, y nunca leen el .env ni usan las credenciales de la cátedra. Se simulan los servicios de catedra y de catalogo. Levanta una base de datos de Testcontianers y crea su propio Kafka. Se tienen tanto tests unitarios como de integración.
- E2E: Levantan la aplicación completa y la hacen trabajar contra el servicio de cátedra real, con la cuenta técnica del .env. Sin esas credenciales, se saltean. Comprueban lo que hace el catalogo, la cátedra es el entorno, no lo que se prueba.