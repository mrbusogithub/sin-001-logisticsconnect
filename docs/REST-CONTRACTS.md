# LogisticsConnect — Iteration 2 REST Contracts

## 1. Scope

Iteration 2 uses synchronous HTTP REST calls between independently deployable services. Each service owns its implementation and exposes only its documented HTTP contract.

The ingestion service remains unchanged. ActiveMQ publishing and consuming are deferred to Iteration 3.

## 2. Service URLs

| Service | Default base URL |
|---|---|
| Ingestion Service | `http://localhost:7050` |
| Hub Service | `http://localhost:7051` |
| Delay Stage Service | `http://localhost:7052` |
| Transit Service | `http://localhost:7053` |

Base URLs should be configurable using environment variables.

## 3. Hub Service

### `GET /health`

Returns `200 OK` when the Hub Service is running.

### `GET /hubs`

Returns the cached collection of cleaned hub records loaded from Ingestion Service.

Example response:

```json
[
  {
    "hubId": "H-501",
    "province": "Western Cape",
    "sortingCenter": "Cape Town Port",
    "active": true
  }
]
```

Success: `200 OK`.

If the upstream ingestion service is unavailable during startup, the Hub Service must fail clearly rather than start with an empty or misleading dataset.

### `GET /hubs/{hubId}`

Returns one hub, using a trimmed, case-insensitive hub identifier.

Success: `200 OK`.

Errors:
- `400 Bad Request`: missing or invalid identifier.
- `404 Not Found`: no matching hub.
- `503 Service Unavailable`: the service cannot provide its required data.

## 4. Delay Stage Service

A delay stage is an integer from 0 through 8 inclusive.

### `GET /health`

Returns `200 OK` when the service is running.

### `GET /delay-stage/{hubId}`

Returns the current stage. If no stage has been submitted for a known hub, the default stage is `0`.

Example:

```json
{
  "hubId": "H-501",
  "stage": 3
}
```

### `POST /delay-stage/{hubId}`

Content-Type: `application/json`

Request:

```json
{
  "stage": 3
}
```

The service validates the JSON body, stage range, and existence of the hub through Hub Service. Valid stages are held in memory during Iteration 2 and reset when the service restarts.

Success: `200 OK`, returning the stored stage.

Errors:
- `400 Bad Request`: missing, malformed, non-integer, or out-of-range stage.
- `404 Not Found`: unknown hub.
- `502 Bad Gateway`: Hub Service returns an invalid response.
- `503 Service Unavailable`: Hub Service cannot be reached.

## 5. Transit Service

### `GET /health`

Returns `200 OK` when the service is running.

### `GET /transit/{hubId}`

Transit Service calls Hub Service and Delay Stage Service using their REST contracts.

ETA calculation:

`etaMinutes = baseDurationMinutes + (delayStage × minutesPerStage)`

Demonstration defaults:
- Base duration: 120 minutes.
- Additional delay per stage: 15 minutes.
- Supported stages: 0 through 8.

Example response:

```json
{
  "hubId": "H-501",
  "delayStage": 3,
  "baseDurationMinutes": 120,
  "minutesPerStage": 15,
  "etaMinutes": 165,
  "etaDuration": "PT2H45M"
}
```

Both ETA fields represent a duration, not a guaranteed real-world delivery time. The baseline and stage multiplier are illustrative assumptions, not operational predictions.

Errors:
- `400 Bad Request`: invalid hub identifier.
- `404 Not Found`: unknown hub.
- `502 Bad Gateway`: an upstream service returns malformed or invalid data.
- `503 Service Unavailable`: an upstream service cannot be reached.

## 6. Error response format

Errors use JSON consistently:

```json
{
  "error": "INVALID_STAGE",
  "message": "Stage must be an integer between 0 and 8."
}
```

The `error` value is a stable machine-readable code. The `message` explains the problem to a human.

## 7. Normalisation and validation

- Hub identifiers are trimmed and uppercased using locale-independent rules.
- Empty identifiers are rejected.
- Stage values must be integers in the inclusive range 0–8.
- Upstream HTTP status codes and JSON payloads must both be validated.
- No service imports another service's internal Java model; data crosses service boundaries as JSON.

## 8. Configuration

| Variable | Default | Used by |
|---|---|---|
| `INGESTION_SERVICE_URL` | `http://localhost:7050` | Hub Service |
| `HUB_SERVICE_URL` | `http://localhost:7051` | Delay Stage and Transit |
| `DELAY_STAGE_SERVICE_URL` | `http://localhost:7052` | Transit |
| `TRANSIT_BASE_DURATION_MINUTES` | `120` | Transit |
| `TRANSIT_MINUTES_PER_STAGE` | `15` | Transit |

## 9. Iteration boundary

Iteration 2 uses synchronous REST calls and in-memory delay-stage storage. Iteration 3 will introduce ActiveMQ events, including a delay-stage event published when a stage changes. Transit will then consume those events rather than rely on a synchronous delay-stage lookup for its state.
