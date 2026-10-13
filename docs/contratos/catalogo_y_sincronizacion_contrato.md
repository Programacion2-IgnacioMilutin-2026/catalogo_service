# Contrato del Servicio de Catálogo y Sincronización

Nota: Los valores de las requests y respuestas son ejemplos.

## 1. Operaciones

| Caso | Método y ruta | Acceso | Lo consume |
| --- | --- | --- | --- |
| C04 | `GET /api/professional-categories` | Público | App |
| C05 | `GET /api/professionals` | Público | App |
| C06 | `GET /api/professionals/{professionalId}` | JWT de usuario válido, cualquier rol | Servicio de turnos |
| C07 | `GET /api/synchronization/status` | JWT con `ROLE_ADMIN` | App (administrador) o herramienta de administración |

El catálogo solo valida tokens; nunca los emite. Formato, clave y reglas de validación: `contratos_comunes.md`, sección 1. Formato de errores, fechas y paginación: secciones 2 y 3 del mismo archivo.

## 2. Operaciones que expone a la app

### 2.1 C04 — Listar categorías

Sin parámetros. Devuelve todas las categorías, habilitadas y deshabilitadas, ordenadas por `name`.

`200 OK`:

```json
[
  {
    "id": 1,
    "name": "Clinica medica",
    "description": "Atencion clinica general",
    "enabled": true
  }
]
```

Sin categorías devuelve `[]`.

| Estado | `code` | Cuándo |
| --- | --- | --- |
| `503` | `CATALOG_NOT_INITIALIZED` | Todavía no hay versión local aplicada |
| `500` | `INTERNAL_ERROR` | Falla el acceso a la base local |

### 2.2 C05 — Buscar profesionales

Todos los parámetros son opcionales y se combinan.

| Parámetro | Tipo | Regla |
| --- | --- | --- |
| `categoryId` | número | Profesionales de esa categoría. Un id inexistente devuelve página vacía |
| `name` | texto | Coincidencia parcial, sin distinguir mayúsculas ni tildes, con el nombre, el apellido o ambos |
| `enabled` | booleano | Estado del profesional |
| `dayOfWeek` | `MONDAY` a `SUNDAY` | Repetible: `dayOfWeek=MONDAY&dayOfWeek=FRIDAY` |
| `from` | hora (`HH:mm` o `HH:mm:ss`) | Inicio de la franja. Si viene `to`, debe ser anterior |
| `to` | hora (`HH:mm` o `HH:mm:ss`) | Fin de la franja |
| `page`, `size` | número | Ver `contratos_comunes.md`, sección 3.1 |

Disponibilidad: un profesional cumple si tiene al menos un horario semanal habilitado en alguno de los `dayOfWeek` indicados que se solapa con la franja. Si falta `dayOfWeek` o falta la franja, ese criterio no se aplica. Sin filtros se devuelven todos los profesionales, habilitados o no, y también los de una categoría deshabilitada.

Orden: `lastName`, `firstName`, `id`.

`200 OK`:

```json
{
  "items": [
    {
      "id": 15,
      "firstName": "Ana",
      "lastName": "Gomez",
      "enabled": true,
      "category": { "id": 1, "name": "Clinica medica", "enabled": true },
      "weeklySchedules": [
        {
          "id": 21,
          "dayOfWeek": "MONDAY",
          "startTime": "09:00:00",
          "endTime": "12:00:00",
          "slotDurationMinutes": 30,
          "enabled": true
        }
      ]
    }
  ],
  "page": 0,
  "size": 10,
  "totalItems": 1
}
```

Sin coincidencias, o con una página más allá de la última, devuelve `items` vacío con el `totalItems` real.

| Estado | `code` | Cuándo |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | `dayOfWeek` fuera de `MONDAY` a `SUNDAY`, hora inválida, `from` no anterior a `to`, `categoryId` no numérico, `page` negativa o `size` fuera de rango. Incluye `fieldErrors` |
| `503` | `CATALOG_NOT_INITIALIZED` | Todavía no hay versión local aplicada |
| `500` | `INTERNAL_ERROR` | Falla el acceso a la base local |

### 2.3 C07 — Consultar estado de sincronización

Sin parámetros. Requiere `ROLE_ADMIN`.

`200 OK`:

```json
{
  "initialized": true,
  "localVersion": 7,
  "lastSyncAt": "2026-09-10T13:45:02Z",
  "lastSyncType": "INCREMENTAL",
  "lastSyncResult": "SUCCESS",
  "lastSyncErrorType": null,
  "remoteVerified": true,
  "remoteVersion": 7,
  "behind": false
}
```

| Campo | Valores y regla |
| --- | --- |
| `initialized` | `false` si todavía no se aplicó ninguna sincronización. En ese caso todos los demás campos son `null` |
| `lastSyncType` | `FULL` o `INCREMENTAL` |
| `lastSyncResult` | `SUCCESS` o `ERROR` |
| `lastSyncErrorType` | Solo con `ERROR`: `SOURCE_UNAVAILABLE`, `INTEGRATION_REJECTED`, `INVALID_DATA` o `PERSISTENCE_FAILURE` |
| `remoteVerified` | `false` si no se pudo leer Redis. Entonces `remoteVersion` y `behind` son `null` |
| `behind` | `true` si `localVersion` es menor que `remoteVersion` |

La lectura de Redis para este caso no se reintenta ni ejecuta X01.

| Estado | `code` | Cuándo |
| --- | --- | --- |
| `401` | — | Token ausente, inválido o vencido |
| `403` | — | Token válido sin `ROLE_ADMIN` |
| `500` | `INTERNAL_ERROR` | Falla el acceso a la base local |

## 3. Contrato entre servicios

### 3.1 C06 — Consultar profesional y horarios semanales

Operación que turnos le pide al catálogo en T03 y T04. Turnos no guarda copia del catálogo y consulta en cada operación.

```http
GET /api/professionals/{professionalId}
Authorization: Bearer <jwt-del-usuario>
```

- `professionalId`: número.
- El JWT es el mismo que la app envió a turnos. Turnos lo reenvía sin modificarlo.
- Devuelve también lo deshabilitado, con `enabled`. Turnos decide cómo usarlo.

`200 OK`: mismo objeto que cada elemento de `items` en C05.

```json
{
  "id": 15,
  "firstName": "Ana",
  "lastName": "Gomez",
  "enabled": true,
  "category": { "id": 1, "name": "Clinica medica", "enabled": true },
  "weeklySchedules": [
    {
      "id": 21,
      "dayOfWeek": "MONDAY",
      "startTime": "09:00:00",
      "endTime": "12:00:00",
      "slotDurationMinutes": 30,
      "enabled": true
    }
  ]
}
```

Un profesional sin horarios devuelve `weeklySchedules` vacío con `200`.

| Estado | `code` | Cuándo |
| --- | --- | --- |
| `401` | — | Token ausente, inválido o vencido |
| `403` | — | Token válido sin permisos |
| `400` | `VALIDATION_ERROR` | `professionalId` no numérico |
| `404` | `PROFESSIONAL_NOT_FOUND` | El profesional no existe en la copia local |
| `503` | `CATALOG_NOT_INITIALIZED` | Todavía no hay versión local aplicada |
| `500` | `INTERNAL_ERROR` | Falla el acceso a la base local |

## 4. Contratos que consume de la cátedra

Extracto del anexo técnico de integración v2. Si hay diferencia, manda el anexo.

### 4.1 Autenticación técnica (X01, anexo §5.2)

```http
POST /api/authenticate
Content-Type: application/json
```

```json
{
  "username": "juan.perez",
  "password": "una-clave-segura",
  "rememberMe": true
}
```

`rememberMe` debe ser `true` para obtener el JWT de vigencia larga. `username` y `password` salen de la configuración externa.

`200 OK`:

```json
{
  "id_token": "<jwt-tecnico>",
  "integration": {
    "groupId": "proyecto-juan-perez",
    "redisHost": "redis.catedra.internal",
    "redisPort": 6379,
    "redisUsername": "grp_proyecto-juan-perez",
    "redisPassword": "<secreto-redis>",
    "redisReadNamespace": "catedra:sync:*",
    "redisWriteNamespace": "alumnos:proyecto-juan-perez:*",
    "kafkaBootstrapServers": "kafka.catedra.internal:9092",
    "kafkaConsumerGroupId": "alumnos-proyecto-juan-perez",
    "kafkaCatalogTopic": "catedra.catalog.proyecto-juan-perez",
    "kafkaAppointmentActionsTopic": "alumnos.turnos.acciones.proyecto-juan-perez",
    "kafkaAppointmentPhoneTopic": "catedra.turnos.telefono.proyecto-juan-perez",
    "provisioningStatus": "PROVISIONED"
  }
}
```

- El catálogo usa de `integration`: `provisioningStatus`, `redisHost`, `redisPort`, `redisUsername`, `redisPassword`, `kafkaBootstrapServers`, `kafkaConsumerGroupId` y `kafkaCatalogTopic`.
- Solo sigue si `provisioningStatus` es `PROVISIONED`.
- El JWT y la configuración se guardan solo en memoria.
- Credenciales incorrectas responden `401`.

Respuestas de la cátedra a cualquier llamada REST del catálogo:

| Respuesta | Qué hace el catálogo |
| --- | --- |
| `401` | Ejecuta X01 y repite la llamada, una sola vez |
| `403` | Error de integración. No reintenta |
| Timeout, corte, `503` o `500` | Reintenta con espera creciente y un máximo de intentos |

### 4.2 Snapshot (C01, anexo §7)

```http
GET /api/synchronization/snapshot
Authorization: Bearer <jwt-tecnico>
```

`200 OK`:

```json
{
  "snapshotVersion": 7,
  "generatedAt": "2026-09-10T13:44:59Z",
  "professionalCategories": [
    {
      "id": 1,
      "name": "Clinica medica",
      "description": "Atencion clinica general",
      "enabled": true,
      "createdAt": "2026-07-01T12:00:00Z",
      "updatedAt": "2026-09-10T13:40:00Z"
    }
  ],
  "professionals": [
    {
      "id": 15,
      "categoryId": 1,
      "firstName": "Ana",
      "lastName": "Gomez",
      "enabled": true,
      "createdAt": "2026-07-01T12:00:00Z",
      "updatedAt": "2026-09-10T13:40:00Z"
    }
  ],
  "weeklySchedules": [
    {
      "id": 21,
      "professionalId": 15,
      "dayOfWeek": "MONDAY",
      "startTime": "09:00:00",
      "endTime": "12:00:00",
      "slotDurationMinutes": 30,
      "enabled": true,
      "createdAt": "2026-07-01T12:00:00Z",
      "updatedAt": "2026-09-10T13:40:00Z"
    }
  ]
}
```

- Incluye entidades habilitadas y deshabilitadas. No incluye historial incremental.
- `snapshotVersion` se guarda como versión local solo cuando las tres colecciones quedaron aplicadas.

### 4.3 Redis (C02, C03, C07, anexo §14)

- Conexión con `redisHost`, `redisPort`, `redisUsername` y `redisPassword` de `integration`.
- Solo lectura sobre `catedra:sync:*`. Sin TTL.

| Clave | Tipo | Contenido |
| --- | --- | --- |
| `catedra:sync:metadata` | Hash | `currentVersion`, `oldestAvailableVersion`, `publishedAt` (texto) |
| `catedra:sync:professional-categories` | Hash | field = `id` decimal, value = JSON de la categoría |
| `catedra:sync:professionals` | Hash | field = `id` decimal, value = JSON del profesional |
| `catedra:sync:weekly-schedules` | Hash | field = `id` decimal, value = JSON del horario |
| `catedra:sync:changes:{version}` | String | JSON con los IDs afectados por esa versión |

Cada value tiene la misma forma que el elemento correspondiente del snapshot. Las entidades deshabilitadas permanecen en los Hashes.

`catedra:sync:metadata`:

```text
currentVersion = 7
oldestAvailableVersion = 4
publishedAt = 2026-09-10T13:44:59Z
```

`catedra:sync:changes:7`:

```json
{
  "version": 7,
  "changes": {
    "professionalCategories": [],
    "professionals": [15],
    "weeklySchedules": []
  }
}
```

- Los arrays traen IDs, no entidades. El estado vigente de cada ID se lee del Hash correspondiente.
- Las versiones se aplican en orden ascendente. La versión local avanza solo cuando todos los cambios de esa versión quedaron aplicados.
- Versión local menor que `oldestAvailableVersion`, `changes:{version}` inexistente o ID ausente en su Hash: discontinuidad, se reconstruye con el snapshot (C01).
- El catálogo no usa `catedra:sync:current-version`, `catedra:sync:oldest-available-version` ni el namespace `alumnos:{groupId}:*`.

### 4.4 Kafka (C02, anexo §15.3)

| Dato | Valor |
| --- | --- |
| Topic | `kafkaCatalogTopic` (`catedra.catalog.{groupId}`) |
| Consumer group | `kafkaConsumerGroupId` |
| Dirección | Cátedra al catálogo. El catálogo no publica |
| Message key | `newVersion` como texto |

```json
{
  "eventId": "catalog-event-7-4f3b",
  "eventType": "CatalogUpdated",
  "newVersion": 7,
  "occurredAt": "2026-09-10T13:44:59Z",
  "source": "MANUAL",
  "schemaVersion": 1
}
```

- `schemaVersion` debe ser `1`. Los campos desconocidos se ignoran.
- La entrega es al menos una vez. `eventId` es la clave de idempotencia.
- El evento solo avisa: los datos se obtienen de Redis o del snapshot.
