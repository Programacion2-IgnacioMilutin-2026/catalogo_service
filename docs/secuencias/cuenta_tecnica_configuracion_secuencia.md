# Secuencias — cuenta técnica y configuración

## 1. Autenticación técnica y configuración (X01)

Caso de uso: X01. Lo ejecuta cada servicio por su cuenta, al arrancar y cada vez que la cátedra responde `401` en otro caso de uso.

```mermaid
sequenceDiagram
participant S as Service (catalog or reservations)
participant CFG as External configuration
participant API as Central API
Note over S: Startup, or any 401 from the central service in another use case
S->>CFG: read technical account login and password
CFG-->>S: login, password
S->>API: POST /api/authenticate (login, password, rememberMe=true)
alt valid credentials
API-->>S: 200 id_token and integration
alt provisioningStatus is PROVISIONED
S->>S: keep groupId, Redis, Kafka, topics and JWT in memory
else any other status
S->>S: integration not enabled, limited retries, no operations that depend on the central service
end
else wrong credentials or missing integration (401, 403, 404)
S->>S: configuration error, no retries, report to the professor
else Central API unavailable (timeout, 503)
S->>S: limited retries with growing wait, service stays degraded
end
```
