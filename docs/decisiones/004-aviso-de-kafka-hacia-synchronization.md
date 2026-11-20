# 004 — El aviso de Kafka: dónde se recibe y cómo llega a `synchronization`

- **Fecha:** 2026-11-19
- **Funcionalidad:** C02 — Sincronizar catálogo incrementalmente (`docs/sdd/c02-sincronizacion-incremental/`, P1 del PLAN)
- **Estado:** aceptada

## Situación

En C02, la cátedra avisa por Kafka que publicó una versión nueva del catálogo (`CatalogUpdated`), y el servicio tiene que ponerse al día con Redis. Hay dos partes:

- **Recibir el aviso es asunto de la cátedra.** Los servidores, el topic y el consumer group llegan con el login técnico, y el formato del aviso es el de su contrato (contrato §4.4, anexo §15.3). Hasta ahora todo lo de la cátedra vive en el slice `catedra`, la única puerta hacia ella (decisión 001): el login, el snapshot y Redis.
- **Lo que se hace con el aviso es asunto de `synchronization`:** esperar a otra sincronización en curso, ponerse al día con el procedimiento que comparte con la verificación periódica (C03), y registrar el aviso como procesado.

Las dependencias entre slices van en un solo sentido: `synchronization` usa a `catedra` y a `catalog`, y ninguno de los dos usa a nadie (decisión 002).

## Problema

El aviso nace en `catedra`, porque es ahí donde llega el mensaje de Kafka, y tiene que llegar a `synchronization`. Va contra la flecha de las dependencias: `catedra` no puede llamar a `synchronization`.

Si lo llamara directamente, habría dependencias en los dos sentidos, un ciclo. Cada slice dependería del otro, un cambio en uno podría romper al otro, y `catedra`, que solo sabe hablar con la cátedra, conocería el proceso de sincronización.

Hay solo cuatro formas de que algo nacido en `catedra` llegue a `synchronization`:

| Forma | Cómo |
| --- | --- |
| Llamada directa | `catedra` importa el service de `synchronization`: el ciclo |
| Una interfaz | `catedra` llama a un puerto de salida propio; `synchronization` lo implementa |
| Eventos de Spring | `catedra` publica un evento; `synchronization` lo escucha |
| Que `synchronization` lo pida | `synchronization` le pregunta a `catedra` cada tanto si hay avisos |

## Decisión

**Kafka vive en `catedra/infrastructure/kafka`, y el aviso llega a `synchronization` por un puerto de salida de `catedra` que implementa `synchronization`.**

```
Kafka (topic del catálogo)
 └─ catedra/infrastructure/kafka            recibe el mensaje, lo lee y lo valida, y lo convierte al modelo del aviso
    └─ service de catedra
       └─ caso de uso de catedra "recibir un aviso"
          └─ puerto de salida de catedra: "quien procesa los avisos" (una interfaz)
             ▲ implementado por
             synchronization/infrastructure/gateway   un adaptador chico
             └─ SynchronizationService → caso de uso de C02
```

- **`catedra` declara la interfaz y no sabe quién la cumple.** Spring le inyecta el adaptador de `synchronization` al arrancar. La dependencia sigue siendo `synchronization` → `catedra`: es el adaptador el que importa de `catedra`, nunca al revés.
- **Es la misma idea que cualquier repositorio.** Un caso de uso de `catalog` llama a `CatalogSyncStateRepository`, una interfaz de su dominio, y la cumple un adaptador JPA de su infraestructura. Acá, quien cumple la interfaz vive en la infraestructura de otro slice.
- **Todo lo de la cátedra queda en `catedra`:** los datos de conexión, el formato del aviso, su validación y el aviso de que la sesión técnica se habilitó, que hace falta para arrancar el consumo. Nada de eso cruza a otro slice.
- **Lo que `synchronization` importa de `catedra`** es solo de su dominio: la interfaz del puerto y el modelo del aviso, ya validado. Es el mismo nivel que la fachada ya usa: modelos y puertos de entrada del dominio de `catedra`, como `CatalogMetadata` y `GetCatalogMetadataUseCase`.
- **Si algo queda mal conectado, se nota enseguida.** Si nadie implementa el puerto, la aplicación no arranca; si alguien cambia la interfaz, el código no compila.
- **Es lo que hizo turnos** (su decisión 014, §3.7), ya probado contra la cátedra real: `reservation/infrastructure/gateway/CatedraEventAdapter`, de 69 líneas, implementa `CatedraEventHandler`, un puerto de salida de `catedra` de 14 líneas, e importa de `catedra` solo cuatro tipos de su dominio.

Los nombres de las piezas, el resultado que devuelve el puerto y lo que pasa con el offset se definen en el PLAN de C02.

### Excepción a la skill

La skill `hexagonal-vertical-slicing` (§9) tiene una opción parecida, "puerto de salida hacia el otro slice", pero al revés: ahí el puerto es del slice que llama, y su adaptador consume el puerto de entrada del otro. Esta es su espejo: el puerto es de `catedra` y el adaptador vive en `synchronization`. Por eso es una excepción, autorizada por el usuario para C02 (P1 y P10 del PLAN):

- un adaptador de `synchronization` que implementa un puerto de salida de `catedra`;
- el paquete `synchronization/infrastructure/gateway` para ese adaptador.

## Alternativas descartadas

- **Kafka en `synchronization/infrastructure/kafka`, como un disparador más, igual que el de C03.** El aviso llegaría directo al service de `synchronization`, pero `synchronization` necesitaría de `catedra` los datos de conexión de Kafka, con un puerto nuevo, porque hoy la integración entera trae la contraseña de Redis. También necesitaría enterarse de que la sesión se habilitó, que es el mismo cruce al revés que se quería evitar. Además, el formato del aviso de la cátedra quedaría fuera de `catedra`. Eran dos cruces entre slices en lugar de uno.
- **Eventos de Spring.** `catedra` publica un evento con el aviso y `synchronization` lo escucha con `@EventListener`; Spring los une al arrancar y llama al que escucha en el mismo hilo. No hay interfaz que implementar, y `synchronization` igual importa la clase del evento, del dominio de `catedra`. Se descartó porque el vínculo es invisible: leyendo `catedra` no se ve quién recibe el aviso, y si nadie lo escucha (un método borrado o renombrado), Spring publica a la nada, el consumidor confirma el offset y C02 deja de funcionar sin ningún error. Tampoco está en la skill, así que también era una excepción, solo que menos visible. Turnos la descartó por lo mismo.
- **Que `synchronization` pida los avisos.** `catedra` guarda los avisos que llegan y `synchronization` le pregunta cada tanto, con un puerto de entrada. Es la única forma sin excepción a la skill, porque la flecha queda en su sentido. Se descartó por el offset: la SPEC lo confirma después de procesar (C02 paso 9, RF-06), así que el consumidor de `catedra` tendría que esperar a que `synchronization` le avise que terminó, con una cola entre dos hilos y un canal de vuelta. La otra salida era confirmar al guardar el aviso, antes de procesarlo, lo que cambiaba la SPEC: un corte del servicio perdería el aviso, aunque C03 lo recuperaría. Evitaba la excepción a cambio de lo más delicado de Kafka.
- **Llamada directa de `catedra` a `synchronization`.** Creaba un ciclo entre los dos slices.

## Qué no cambia

- La dirección de las dependencias entre slices: `synchronization` usa a `catedra` y a `catalog`; ninguno de los dos usa a nadie.
- `catedra` sigue siendo la única puerta hacia la cátedra, ahora también por Kafka.
- El procedimiento para ponerse al día con Redis, que `synchronization` comparte entre C02 y C03 (P2 del PLAN de C02).
