Contratos comunes — catálogo y turnos

## 1. Seguridad: JWT del usuario

### 1.1 Emisión y validación

| Servicio | Rol respecto del token |
| --- | --- |
| Turnos | Único emisor (T02). Firma con la clave privada. También valida el token en sus endpoints protegidos |
| Catálogo | Solo valida, con la clave pública. Nunca emite |

Este token es distinto del JWT técnico de la cátedra. El JWT técnico vive en memoria en cada servicio (X01): no llega a la app ni viaja entre los servicios.

### 1.2 Formato

- Algoritmo: `RS256`.
- Transporte: encabezado `Authorization: Bearer <jwt>`.

| Claim | Tipo | Valor |
| --- | --- | --- |
| `sub` | string | `id` del usuario (identificador estable), como texto |
| `auth` | string | Roles separados por un espacio: `ROLE_USER`, `ROLE_ADMIN` |
| `iat` | número | Instante de emisión, en segundos desde epoch |
| `exp` | número | Instante de vencimiento. La vigencia la define la configuración de turnos |

```json
{
  "sub": "42",
  "auth": "ROLE_USER",
  "iat": 1790000000,
  "exp": 1790086400
}
```

El token no lleva más datos (ni `login`, ni email, ni nombres).

"ADMINISTRADOR" en los casos de uso equivale a `ROLE_ADMIN`. Un usuario común tiene solo `ROLE_USER`. T01 nunca asigna `ROLE_ADMIN`.

### 1.3 Claves

- Par RSA de 2048 bits.
- Turnos: clave privada (PEM, PKCS#8) en su configuración externa.
- Catálogo: solo la clave pública (PEM, X.509) en su configuración externa.
- La clave privada no se versiona en el repositorio, no se entrega al catálogo y no aparece en logs.
- Generación:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
```

### 1.4 Validación

| Verificación | Si falla |
| --- | --- |
| Token presente y bien formado | `401` |
| Firma válida con la clave pública | `401` |
| `exp` no vencido | `401` |
| El endpoint exige un rol y `auth` no lo incluye | `403` |

| Servicio | Endpoints | Requisito |
| --- | --- | --- |
| Turnos | T03, T04, T05, T06, T09, T10, T11 | Token válido, cualquier rol. El dueño es `sub`: cada usuario ve y modifica solo lo suyo |
| Catálogo | C06 | Token válido, cualquier rol |
| Catálogo | C07 | Token válido con `ROLE_ADMIN` |
| Turnos | T01, T02 | Públicos |
| Catálogo | C04, C05 | Públicos |

- Los endpoints públicos ignoran el encabezado `Authorization`, sea válido o no.
- La identidad del usuario sale siempre de `sub`. Ningún endpoint recibe un identificador de usuario por parámetro o cuerpo, ni confía en uno enviado por el cliente.
- Los `401` y `403` los produce la capa de seguridad: no llevan datos del recurso y pueden no incluir `code`. La app decide por el `status`.

### 1.5 Por qué se propaga el JWT del usuario

- **Identidad y autorización:** catálogo recibe el mismo token, con el mismo `sub` y los mismos roles que se decidieron al iniciar sesión. No hay una segunda identidad que mapear.
- **Trazabilidad:** una misma operación del usuario deja el mismo `sub` en los logs de ambos servicios.
- **Sin credencial extra:** no hay un token técnico entre los servicios que administrar. La cuenta técnica de la cátedra queda fuera de este canal.

### 1.6 Limitaciones

- El token es sin estado: no se puede revocar, vale hasta su `exp`. Cerrar sesión es descartarlo en la app.
- C06 puede ser llamado por cualquier portador de un token válido, no solo por turnos. Devuelve los mismos datos que el C05 público, así que no expone nada adicional.
- El cambio de clave es manual: hay que reemplazar el par en ambos servicios y reiniciarlos. Los tokens emitidos con la clave anterior dejan de valer.

## 2. Formato de errores

Los errores usan `Content-Type: application/problem+json`, igual que la cátedra (anexo §13).

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "The user already has an open reservation process",
  "code": "OPEN_PROCESS_EXISTS"
}
```

| Campo | Descripción |
| --- | --- |
| `status` | Código HTTP |
| `title` | Texto HTTP estándar |
| `code` | Código funcional estable |
| `detail` | Texto para el desarrollador. Nunca para decidir lógica |
| `fieldErrors` | Solo en `400` por validación. Lista de `{ "field": "...", "message": "..." }` |

- La app decide por `status` y `code`, nunca por `detail`.
- Los clientes ignoran los campos que no conocen.
- Los `code` van en `UPPER_SNAKE_CASE`, en inglés, y no cambian de significado. Los específicos de cada operación se definen en el contrato de cada servicio.
- Códigos generales de este contrato:

| Código | Estado | Cuándo |
| --- | --- | --- |
| `VALIDATION_ERROR` | `400` | Falta un campo, formato inválido o parámetro fuera de rango. Incluye `fieldErrors` cuando corresponde |
| `INTERNAL_ERROR` | `500` | Error del servidor. Nunca expone detalles internos |

- Un error puede incluir campos adicionales, siempre definidos en el contrato de la operación.

## 3. Convenciones de datos

| Tipo | Formato | Ejemplo |
| --- | --- | --- |
| Propiedades JSON | `camelCase` | `startTime` |
| Fecha | `yyyy-MM-dd` | `2026-09-10` |
| Instante | ISO-8601 en UTC | `2026-09-10T14:30:00Z` |
| Hora local de atención | Se emite `HH:mm:ss` y se acepta `HH:mm` o `HH:mm:ss` | `08:30:00` |
| Día de la semana | `MONDAY` a `SUNDAY` | `MONDAY` |
| Enumeraciones | Texto en mayúsculas | `WAITING_FOR_PHONE` |

- Las horas son locales de atención y no llevan zona. Las comparaciones con "hoy" y con turnos ya comenzados usan la zona horaria de atención, definida en la configuración.
- Son instantes: `expiresAt`, `cancelledAt`, `confirmedAt` y `dataAsOf`.

### 3.1 Paginación

Parámetros: `page` (base 0, por defecto 0) y `size` (por defecto 10, entre 1 y 100). Fuera de rango responde `400` `VALIDATION_ERROR`. No hay parámetro `sort`: cada operación fija su orden.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0
}
```

Una página más allá de la última devuelve `items` vacío con el `totalItems` real.
