# Secuencias — catálogo y sincronización

## 1. Sincronización completa del catálogo (C01)

Caso de uso: C01. No arranca por sí sola: la dispara C03 (al arrancar el servicio y en cada verificación periódica) o C02 (ante un aviso de Kafka) cuando no hay versión local (por ejemplo, en el primer arranque con la base vacía), la versión local es menor que `oldestAvailableVersion`, o el estado no se puede verificar. En un arranque normal, con la copia al día, no se ejecuta. Cuando termina bien, quien la disparó repite una vez su pasada incremental (sección 2 de este archivo).

```mermaid
sequenceDiagram
    participant T as Trigger (C03 at startup or periodic, or C02)
    participant CS as Catalog Service
    participant API as Central API
    participant DB as Catalog DB

    T->>CS: full sync needed (no local version, for example on first startup, version too old or state not verifiable)
    alt another sync in progress
        CS->>CS: discard the request
    else no other sync in progress
        CS->>API: GET /api/synchronization/snapshot (Bearer technical JWT)
        alt 200
            API-->>CS: snapshotVersion, generatedAt, categories, professionals, weekly schedules
            CS->>CS: validate completeness and references between entities
            alt snapshot valid
                CS->>DB: begin unit of work
                CS->>DB: replace categories, professionals and weekly schedules, set local version = snapshotVersion
                alt commit succeeds
                    DB-->>CS: committed
                    CS->>DB: record sync result (FULL, SUCCESS)
                    CS-->>T: done
                    Note over T,CS: the caller repeats its incremental pass once
                else persistence failure
                    DB-->>CS: rollback, previous copy and version intact
                    CS->>DB: record sync result (FULL, ERROR, PERSISTENCE_FAILURE)
                end
            else snapshot incomplete or inconsistent
                CS->>DB: record sync result (FULL, ERROR, INVALID_DATA), copy unchanged
            end
        else 401 (JWT expired or rejected)
            Note over CS,API: X01 renews the JWT, then the request is repeated once
        else timeout, 500 or 503
            CS->>CS: limited retries with growing wait
            CS->>DB: if exhausted, record sync result (FULL, ERROR, SOURCE_UNAVAILABLE), copy unchanged
        else 403 (no technical client role)
            CS->>DB: no retries, record sync result (FULL, ERROR, INTEGRATION_REJECTED)
        end
    end
```

## 2. Sincronización incremental y verificación periódica (C02, C03)

Casos de uso: C02 y C03. Comparten el procedimiento de aplicar las versiones pendientes y solo cambia quién lo dispara. C02 reacciona a un aviso `CatalogUpdated` de Kafka. C03 corre al arrancar (después de X01) y a intervalos, y es la red de seguridad de C02 si un aviso se pierde. Si no se puede seguir de forma incremental, delegan en la sincronización completa (sección 1).

```mermaid
sequenceDiagram
    participant K as Kafka (catalog topic)
    participant SCH as Scheduler
    participant CS as Catalog Service
    participant R as Redis
    participant DB as Catalog DB

    alt notice from Kafka (C02)
        K-->>CS: CatalogUpdated (eventId, newVersion)
        CS->>CS: validate message (required fields, schemaVersion 1)
        CS->>DB: was this eventId processed before?
        break already processed
            CS-->>K: commit offset, no effects
        end
        CS->>CS: if another sync is in progress, wait for it to finish
    else periodic check (C03, at startup and every interval)
        SCH->>CS: run verification
        CS->>CS: if another sync is in progress, skip this run
    end
    CS->>R: read metadata (currentVersion, oldestAvailableVersion)
    alt Redis unavailable
        CS->>CS: limited retries with growing wait
        CS->>DB: if exhausted, record error (INCREMENTAL, ERROR, SOURCE_UNAVAILABLE), version unchanged
        Note over CS,K: for a notice the offset is committed, C03 recovers the delay later
    else Redis rejects credentials
        Note over CS,R: X01 renews the configuration, the read is repeated once
    else metadata received
        R-->>CS: currentVersion, oldestAvailableVersion
        alt local version equals currentVersion
            CS->>DB: record the check, nothing to apply
        else behind, with continuous history
            loop each pending version v, in ascending order
                CS->>R: read catedra:sync:changes:v and the current state of each affected ID
                R-->>CS: affected IDs and entities
                CS->>DB: begin unit of work, apply changes, advance local version to v
                DB-->>CS: commit
            end
            CS->>DB: record sync result (INCREMENTAL, SUCCESS)
            Note over CS,DB: persistence failure rolls back only that version and follows the same retry policy
            Note over CS,R: changes:v missing or an affected ID not found is treated as a discontinuity, full sync
        else no local version, too old or ahead of Redis (not verifiable)
            Note over CS: full sync (section 1), then this pass is repeated once
        end
        opt notice from Kafka
            CS->>DB: mark eventId as processed
            CS-->>K: commit offset
        end
    end
```

## 3. Consultas del catálogo desde la app (C04, C05, C07)

Casos de uso: C04, C05 y C07. C06 (horarios de un profesional) no se dibuja acá porque lo llama el servicio de turnos, y aparece en las secuencias de T03 y T04. C04 y C05 son públicos y C07 es solo para el rol ADMINISTRADOR. Ninguno llama a la API de la cátedra.

```mermaid
sequenceDiagram
    participant App as KMP App
    participant CS as Catalog Service
    participant DB as Catalog DB
    participant R as Redis

    alt public query (C04 categories, C05 professionals)
        App->>CS: GET categories, or GET professionals with filters and pagination
        Note over CS: any JWT sent is ignored, the request is treated as anonymous
        opt only C05
            CS->>CS: validate filters and pagination
            break invalid parameter
                CS-->>App: 400 with functional code and the invalid fields
            end
        end
        CS->>DB: is the catalog initialized (local version applied)?
        break not initialized
            CS-->>App: 503 with functional code
        end
        CS->>DB: read categories, or search professionals with their category and weekly schedules
        DB-->>CS: consistent copy, never partial
        CS-->>App: 200 ordered list (empty if nothing matches)
    else sync status (C07, administrator only)
        App->>CS: GET sync status (Bearer user JWT)
        CS->>CS: validate JWT signature and expiry
        break JWT missing, invalid or expired
            CS-->>App: 401
        end
        CS->>CS: check role ADMINISTRATOR
        break valid JWT without the role
            CS-->>App: 403
        end
        CS->>DB: read last sync result and local version
        DB-->>CS: state (initialized false if no sync was applied yet)
        CS->>R: read currentVersion
        Note over CS,R: no retries and no X01 here, a read-only query must not delay the response
        alt Redis available
            R-->>CS: currentVersion
            CS-->>App: 200 state with remoteVersion and delay indicator
        else Redis unavailable
            CS-->>App: 200 local state only, not verified against the central service
        end
    end
```

