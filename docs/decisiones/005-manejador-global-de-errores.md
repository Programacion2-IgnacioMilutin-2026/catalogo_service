# 005 — Manejador global de errores: un solo formato para las respuestas de error

- **Fecha:** 2026-11-20
- **Funcionalidad:** C04 — Listar categorías (`docs/sdd/c04-listar-categorias/`, P2 del PLAN). Ampliada en C05 — Buscar profesionales (`docs/sdd/c05-buscar-profesionales/`, P5 del PLAN), el 2026-11-21
- **Estado:** aceptada

## Situación

C04 es el primer endpoint REST del servicio. El contrato común (§2) define un solo formato para los errores de los dos servicios: `Content-Type: application/problem+json`, con `status`, `title`, `code` y `detail`. Un `INTERNAL_ERROR` nunca muestra detalles internos. C04 responde dos errores con ese formato: `503` `CATALOG_NOT_INITIALIZED` y `500` `INTERNAL_ERROR` cuando falla la base (contrato §2.1). Los siguientes casos suman los suyos: C05, `400` `VALIDATION_ERROR` con `fieldErrors`; C06, `404` `PROFESSIONAL_NOT_FOUND`.

La skill `hexagonal-vertical-slicing` (§5.3) dice que el controller atrapa las excepciones de su slice y las relanza como `ResponseStatusException`, con el estado que corresponde, y que no se agrega un `@ControllerAdvice` salvo que el usuario lo pida.

## Problema

Con solo lo que dice la skill, el servicio no cumple el formato del contrato:

- **Spring no responde `problem+json` por su cuenta.** Una `ResponseStatusException` que lanza un controller, y un error que nadie atrapa, como una falla de la base, terminan en la página de error interna de Spring Boot (`/error`). Esa página responde su propio JSON, sin `code`.
- **Ese reenvío interno vuelve a pasar por la seguridad.** Con todo lo que no es C04 cerrado (P1 del PLAN de C04), un `503` o un `500` podría llegar al cliente como otro estado.
- **Un error no previsto puede salir de cualquier controller**, de C04 o de los que vengan. Si cada uno tuviera que atraparlo, el código se repetiría, y el que lo olvide respondería sin formato o mostraría detalles internos.

## Decisión

**Un manejador global de errores, una clase `@RestControllerAdvice` en `catalogo.config`.** Existe para que todas las respuestas de error del servicio tengan el formato del contrato, en un solo lugar, y para que un error no previsto se registre en el log y nunca llegue con su detalle al cliente.

Qué hace:

1. **Da formato `problem+json` a los errores que lanza un controller** con su estado y su `code`, como el `503` de C04, y a los que Spring ya reconoce, como una ruta inexistente (`404`) o un método no permitido (`405`). Hereda de `ResponseEntityExceptionHandler`, la clase de Spring MVC que ya resuelve esos casos (comprobado en Spring MVC 7.0.10, la versión del proyecto).
2. **Atrapa cualquier otro error**, como una falla de la base: deja en el log un error con su detalle completo, incluida la traza, y responde `500` `INTERNAL_ERROR` con un texto fijo. Es el único lugar del servicio donde se escribe ese error, y así se cumple el "se registra el error" de C04 2.3 (RF-05 de su SPEC): el detalle va al log y nunca a la respuesta.
3. **Responde antes del reenvío a `/error`**, así que la seguridad no vuelve a intervenir.
4. **Responde los errores de validación de los parámetros** (desde C05): `400` `VALIDATION_ERROR` con `fieldErrors`, uno por parámetro, con el mensaje fijo de la regla que no se cumplió. Los errores de los elementos de una lista (por ejemplo, dos días inválidos) se juntan en uno, con el nombre del parámetro. Si Spring no pudo convertir un valor, el mensaje es neutro ("invalid value"), porque el suyo muestra tipos de Java.

Cómo fluye un error:

```
pedido
└─ seguridad (config)
   └─ parámetros inválidos (@Valid) → MethodArgumentNotValidException, antes de entrar al controller
   └─ controller del slice
      ├─ excepción del slice → ResponseStatusException con su estado y su code   lo decide el controller
      └─ error no previsto (la base) → sale del controller sin atrapar
└─ manejador global (config)
   ├─ MethodArgumentNotValidException → 400 VALIDATION_ERROR con fieldErrors
   ├─ ResponseStatusException → problem+json con su code
   └─ cualquier otro → error en el log, con la traza, y 500 INTERNAL_ERROR con un texto fijo
```

Qué no hace:

- **No decide los estados ni los códigos de los slices.** Eso sigue en el controller de cada slice, como pide la skill. Los únicos códigos que pone son los generales del contrato común (§2), que no son de ningún slice: `INTERNAL_ERROR` y, desde C05, `VALIDATION_ERROR`. El manejador no importa nada de ningún slice.
- **No responde los rechazos de la seguridad** (`401` y `403`): ocurren antes de llegar a un controller. Los escribe `SecurityProblemHandler`, con la misma forma (decisión 007).
- **No cambia el formato de las líneas del log**, que sigue siendo el de Spring Boot.

Empezó con lo que C04 necesitaba. C05 le sumó el `400` `VALIDATION_ERROR` con `fieldErrors` (punto 4). En C04 este texto decía que el manejador "no decide estados ni códigos", aunque ya ponía `INTERNAL_ERROR`; se precisó al sumar `VALIDATION_ERROR` (P5 del PLAN de C05).

### Excepción a la skill

- Un `@RestControllerAdvice` global. La skill lo admite solo si el usuario lo pide, y el usuario lo autorizó para C04 (P2 del PLAN).
- Vive en `catalogo.config`, dentro de la excepción autorizada en X01 para ese paquete: configuración transversal, sin lógica de negocio.

## Alternativas descartadas

- **La propiedad de Spring Boot `spring.mvc.problemdetails.enabled`, con el controller atrapando lo inesperado.** Spring Boot registra su propio manejador, que da formato a sus errores y a las `ResponseStatusException`, sin una clase propia. Pero no atrapa un error cualquiera: cada controller tendría que convertir lo inesperado en un `500`, y lo que fallara fuera de un controller quedaría sin formato. Además, no da el `code` ni los `fieldErrors` que C05 necesita, así que se terminaría en un manejador propio igual.
- **Un manejador solo para los controllers de `catalog`.** Es la misma excepción a la skill, pero cubre menos: una ruta inexistente, un método no permitido y los endpoints de otro slice quedarían sin formato. El formato es del servicio entero (contrato común §2), no de un slice.
- **Solo lo que dice la skill, sin manejador.** Las respuestas saldrían con el formato de Spring Boot, sin `code`, y con el riesgo del reenvío a `/error` que se describe en "Problema".

Turnos tiene una pieza con el mismo papel (su decisión 003). Aquí se analizó para catálogo y no se copia: empieza con lo que C04 necesita y crece con cada caso.

## Qué no cambia

- Cada slice sigue decidiendo sus errores en su controller, y los slices no conocen al manejador.
- La sincronización (C01, C02 y C03) no tiene endpoints: no pasa por aquí.
- El log mantiene su formato.
