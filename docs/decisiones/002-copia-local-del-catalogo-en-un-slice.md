# 002 — Estructura de slices: la copia local del catálogo en un solo slice

- **Fecha:** 2026-11-16
- **Funcionalidad:** C01 — Sincronizar catálogo completo (`docs/sdd/c01-sincronizacion-completa/`). Se registra como P1 de su PLAN y vale para C02 a C07.
- **Estado:** aceptada

## Situación

El servicio guarda una copia local de tres entidades de la cátedra: categorías, profesionales y horarios semanales. Antes de C01 solo existía el slice `catedra` (la sesión técnica de X01) y se había acordado, como punto de partida, un slice por entidad (`professionalcategory`, `professional` y `weeklyschedule`) más `synchronization`, para confirmarlo en cada PLAN.

Al especificar C01 aparecieron rasgos de este catálogo que pesan en esa elección:

- **El servicio no es el dueño de los datos.** Es una réplica: la cátedra los publica y la copia cambia entera, con el snapshot (C01) o con cada versión incremental (C02, C03). Una versión puede cambiar a la vez una categoría, un profesional y sus horarios, y se aplica entera o no se aplica.
- **Las entidades se referencian entre sí** (dominio, regla 3) y esas referencias se validan juntas.
- **Casi todas las consultas las leen juntas:** C05 y C06 devuelven el profesional con su categoría y sus horarios, y C05 filtra por horarios y pagina por apellido. Solo C04 lee las categorías solas.
- **La versión local cambia en la misma unidad de trabajo que los datos** (dominio, regla 9), y C04 a C06 la necesitan para responder "catálogo no inicializado".

## Problema

La skill `hexagonal-vertical-slicing` da un paquete por concepto de negocio y toma como modelo un slice con una sola entidad (`product`). También permite unir slices cuando el usuario lo decide, y lo sugiere cuando las entidades "comparten su ciclo de vida y se leen o escriben siempre juntas" (§9).

Con un slice por entidad, casi cada caso de uso cruza slices:

- C01 escribe en tres slices desde `synchronization`, con una transacción que abre otro, y `synchronization` tiene que conocer el orden en que se relacionan las tres.
- C05 tiene que pedir a otros slices los profesionales que atienden en un día y una franja, juntar listas de ids antes de paginar y pedir las categorías y los horarios de la página.
- La versión local vive en `synchronization`, y las consultas tienen que preguntarle si el catálogo está inicializado, mientras `synchronization` depende de ellas para escribir: una dependencia circular.

Hasta C07 eran unas 13 conexiones entre slices, cada una con su puerto y su adaptador, y dos de ellas en sentido contrario.

## Decisión

La copia local va en un solo slice, `catalog`, y el proceso que la mantiene al día, en otro:

| Slice | Qué contiene | De quién depende |
| --- | --- | --- |
| `catalog` | La copia local: `ProfessionalCategory`, `Professional`, `WeeklySchedule` y `CatalogSyncState` (la versión local y el último resultado). Cambia la copia y su versión en una transacción propia. Más adelante, las consultas de C04 a C06 | De ninguno |
| `synchronization` | El proceso: la sincronización completa (C01) y, más adelante, la incremental y la verificación periódica (C02, C03); la regla de una sola sincronización a la vez (D2 de la SPEC de C01) | De `catalog` y de `catedra` |
| `catedra` | La única puerta hacia la cátedra. Suma el pedido del snapshot; más adelante, la lectura de Redis y el consumo de Kafka | De ninguno |

- **`CatalogSyncState` va en `catalog`, no en `synchronization`,** aunque el dominio lo dibuje en la sección "Sincronización". La versión local es la versión de los datos: tiene que cambiar en la misma transacción que ellos (regla 9) y las consultas la necesitan. Así las dependencias van en un solo sentido y no hay ciclo.
- **Dentro de `catalog`, cada entidad tiene sus propios archivos** (modelo, entidad JPA, mapper, repositorio). Un cambio en las categorías toca los archivos de categoría, no los del profesional.
- **El modelo del código sigue el diagrama del dominio:** el profesional tiene su categoría y el horario su profesional, como objetos, porque están en el mismo slice.
- **Nombre `catalog`:** corto, coincide con "Datos del catálogo" del dominio y con `CatalogSyncState`, y produce nombres de clases claros (`CatalogService`, `CatalogException`). La palabra se repite con el paquete base (`catalogo.catalog`), y turnos también tiene un slice `catalog` (su pasarela hacia este servicio): al hablar del slice conviene decir de qué repositorio.
- Dónde van las piezas de C02, C03 y C07 (eventos procesados, estado de sincronización) se confirma en sus PLAN.

Cómo quedan los cambios más probables, comparados con un slice por entidad:

| Cambio | Más fácil con |
| --- | --- |
| Un filtro de C05 sobre otra entidad (el enunciado permite sumar filtros) | `catalog`: una condición más en una consulta del mismo slice |
| Una regla nueva entre entidades | `catalog`: las tres están en el mismo lugar |
| La cátedra agrega un dato a una entidad | Casi igual; con `catalog`, un slice menos |
| La cátedra cambia un formato o un nombre de campo | Igual: lo absorbe `catedra` en sus DTO y mapper |
| Cambiar cómo se guarda una sola entidad | Un slice por entidad, si los demás la usan por puertos |
| La cátedra agrega una entidad nueva | Depende: un slice propio la separa mejor; si se lee siempre con otra, `catalog` |

Alternativas descartadas:

- **Un slice por entidad, más `synchronization`:** lo más fiel al molde de la skill y a las tres colecciones de la cátedra, con slices chicos y fáciles de comparar con `product`. Se descartó por las conexiones, la transacción repartida y el ciclo descritos en "Problema": separaba en paquetes entidades que se comportan como una sola.
- **Intermedia: `professional` con sus horarios y `professionalcategory` aparte:** reducía cruces, pero mantenía la escritura repartida y el ciclo.
- **Un único slice con la copia y los procesos:** el menor número de dependencias, pero mezclaba las consultas con Kafka, Redis y los eventos procesados en un slice que crecería con cada caso de uso.
- **Nombres descartados para el slice:** `localcatalog` (claro pero largo), `replica` (no dice de qué) y `catalogreplica` (largo y difícil de leer).

## Qué no cambia

- Los casos de uso, los contratos y el anexo: la estructura interna no cambia ninguna operación, ruta ni dato expuesto.
- El slice `catedra` sigue siendo la única puerta hacia la cátedra; el JWT técnico no sale de él.
- La skill se sigue aplicando en cada slice. Unir slices es una opción de su §9 que decide el usuario; lo que trae para el resto de la skill (cómo se nombran el service y la excepción base de un slice con varias entidades, los `id` asignados por la cátedra) se decide en el PLAN de C01.
- El servicio de turnos no se entera: solo ve la API de catálogo.
