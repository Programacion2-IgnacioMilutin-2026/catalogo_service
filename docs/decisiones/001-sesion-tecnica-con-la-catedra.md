# 001 — Sesión técnica con la cátedra: cuándo se hace el login y qué se guarda

- **Fecha:** 2026-11-16
- **Funcionalidad:** X01 — Autenticar cuenta técnica y obtener configuración (`docs/sdd/x01-cuenta-tecnica/`)
- **Estado:** aceptada

## Situación

El servicio de catálogo necesita dos cosas de la cátedra para sincronizar: el JWT técnico, para pedir el snapshot por la API, y la configuración de la integración, que trae los datos de conexión a Redis y a Kafka. Las dos llegan juntas en la respuesta del login técnico (anexo §5.2). X01 dice que el login se hace al arrancar y cada vez que la cátedra rechaza el JWT con un `401` en otro caso de uso.

La cuenta técnica es la misma que usa el servicio de turnos, que ya tiene implementado X01.

## Problema

Hay cuatro puntos que X01 no resuelve por sí solo:

- **Cuándo repetir el login.** Si las credenciales están mal, ningún login nuevo lo arregla. Y si la cátedra tiene problemas, un login por cada pedido la carga justo cuando menos puede.
- **Redis también puede rechazar.** Los casos de uso de sincronización piden un login nuevo cuando Redis rechaza las credenciales (C02 4.2, C03 3.2), y ese rechazo no tiene un `401` ni un JWT asociado.
- **Una respuesta incompleta.** La cátedra puede responder que la integración está provista y no traer un dato que catálogo necesita. El anexo no dice qué hacer.
- **Dos servicios, una cuenta.** El anexo §4 dice que los dos servicios usan "la misma cuenta técnica y el mismo JWT", y X01 dice que cada servicio hace el login por separado. Si un login invalidara el JWT anterior, los dos servicios se pisarían.

## Decisión

**Estados de la sesión.** La sesión tiene un estado, y cada uno define si se puede llamar a la cátedra. Son las mismas reglas que en turnos (su decisión 005), porque es el mismo caso de uso:

| Estado | Cómo se llega | Qué pasa con los pedidos de login |
| --- | --- | --- |
| Conectando | Mientras corre el login inicial, con hasta 5 intentos y esperas de 1, 2, 4 y 8 s | No se acepta ninguna renovación |
| Habilitada | Login con `PROVISIONED` y todos los datos | Se usa la sesión guardada, sin logins extra. Solo un rechazo pide un login nuevo |
| Degradada | Reintentos agotados, un estado distinto de `PROVISIONED` o una respuesta incompleta | Un pedido dispara un login solo si pasó el intervalo mínimo (60 s) desde el último intento |
| Error de configuración | `401` en el login, `403` o `404` `STUDENT_INTEGRATION_NOT_FOUND`, propiedades faltantes o una URL inválida | Ningún login más hasta reiniciar el servicio |

Nunca hay dos logins a la vez, y el intervalo mínimo vale también con la sesión habilitada: la cátedra recibe como mucho un login por minuto de este servicio. El servicio arranca aunque la cátedra esté caída o falte configuración, y el login no demora el arranque. No hay reintentos periódicos propios: en catálogo, quien vuelve a pedir la sesión en cada ciclo es la verificación periódica (C03).

**Un solo pedido de renovación para los dos rechazos.** Quien pide renovar entrega la sesión entera que le rechazaron, venga el rechazo de la API o de Redis. Si esa sesión ya no es la vigente, porque otro pedido la renovó, recibe la vigente sin otro login. En turnos se entrega solo el JWT rechazado; acá no alcanza, porque el rechazo de Redis no tiene un JWT asociado. Una sesión que no está habilitada no conserva ni el JWT ni la integración: así nadie usa datos que ya se sabe que no sirven.

**Respuesta incompleta.** Si falta el `id_token` o alguno de los siete datos de Redis y de Kafka, se trata como cátedra no disponible: no se habilita, se reintenta con límite y el log nombra el dato. El `groupId` no es obligatorio: catálogo no lo usa para conectarse a nada y se guarda solo para identificar la integración en los logs.

**Cada servicio hace su propio login.** Se comprobó contra la cátedra real el 2026-11-15: después de un segundo login, que entregó un token distinto, la cátedra siguió aceptando el primero. Los dos JWT conviven, así que "el mismo JWT" del anexo se lee como "la misma identidad", y el JWT técnico nunca viaja entre los servicios (contrato común §1.1).

**Dónde está la decisión sobre el resultado de un login.** En cada caso de uso, el login inicial y la renovación, como en turnos, aunque quede escrita dos veces. El usuario prefirió que, en este caso, lo funcional se lea en el caso de uso.

Alternativas descartadas:

- Reintentar el login en cada pedido: carga a la cátedra y no resuelve los errores de configuración.
- Reintentos periódicos propios en segundo plano: no aportan, porque C03 ya pide la sesión en cada ciclo.
- Dejar el rechazo de Redis para C03: obligaba a reabrir X01 y podía dejar límites distintos para cada motivo.
- Tratar la respuesta incompleta como error de configuración, que obliga a reiniciar por un problema pasajero; o habilitar igual, que hace aparecer el error lejos de su causa.
- Un único JWT compartido por configuración: contradice X01 y deja un token de larga vigencia en un archivo.
- Un componente común para el intento de login, o la regla en el modelo de la sesión: dejaban la decisión en un solo lugar, pero apartaban el código del de turnos.

## Qué no cambia

- Repetir la operación que falló, una sola vez, sigue siendo responsabilidad de cada caso de uso que llama a la cátedra, desde C01 (contrato §4.1).
- El JWT técnico, la contraseña de la cuenta y la contraseña de Redis no aparecen en logs ni en respuestas, y la sesión vive solo en memoria: al reiniciar se vuelve a hacer el login.
- Las consultas sobre la copia local (C04 a C07) no dependen de la sesión: funcionan en cualquier estado.
- X01, los contratos y el anexo no se modificaron.
