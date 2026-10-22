# LogisticsConnect — Iteration 2 REST Contracts

## Scope

Iteration 2 connects the Hub, Delay Stage, and Transit services using synchronous REST calls. The Ingestion Service remains the source of cleaned hub data.

ActiveMQ publishing and subscriptions are outside this iteration and remain part of Iteration 3.

## Service addresses

| Service | Default base URL |
|---|---|
| Ingestion | `http://localhost:7050` |
| Hub | `http://localhost:7051` |
| Delay Stage | `http://localhost:7052` |
| Transit | `http://localhost:7053` |

## Hub Service

### `GET /health`

Returns service health and the number of cached hubs.

### `GET /hubs`

Returns a JSON array of cleaned hub records fetched from Ingestion at startup.

### `GET /hubs/{hubId}`

Returns one hub record. The Hub Service trims surrounding whitespace and compares IDs without regard to case.

A missing hub returns HTTP `404`.

The Hub Service does not start if it cannot load a valid, non-empty hub list from Ingestion.

## Delay Stage Service

### `GET /delay-stage/{hubId}`

Returns the current stage for a known hub.

Example response:

    {
      "hubId": "H-501",
      "stage": 3
    }

A known hub with no previously recorded stage returns stage `0`.

### `POST /delay-stage/{hubId}`

Request:

    {
      "stage": 3
    }

Valid stages are integers from `0` through `8`, inclusive. Valid updates are stored in memory. State resets when the service restarts.

Invalid JSON, a missing stage, a non-integer stage, or a stage outside the permitted range returns HTTP `400`.

Unknown hubs return HTTP `404`.

## Transit Service

### `GET /transit/{hubId}`

Transit calls the Hub Service and Delay Stage Service before calculating an ETA.

Formula:

    etaMinutes = baseDurationMinutes + (delayStage * minutesPerStage)

Default configuration:

- Base duration: 120 minutes
- Additional duration per stage: 15 minutes
- Valid delay stages: 0–8

Example response for stage `3`:

    {
      "hubId": "H-501",
      "province": "Example Province",
      "sortingCenter": "Example Sorting Center",
      "delayStage": 3,
      "baseDurationMinutes": 120,
      "minutesPerStage": 15,
      "etaMinutes": 165,
      "etaDuration": "PT2H45M",
      "etaAt": "2026-10-21T20:00:00Z"
    }

The example `etaAt` is illustrative. The service calculates the timestamp from the actual request time in UTC.

`etaDuration` uses ISO-8601 duration notation. `etaAt` is an absolute UTC timestamp calculated from the current time plus the ETA duration.

## Error response shape

Errors use JSON:

    {
      "error": "INVALID_STAGE",
      "message": "Stage must be an integer between 0 and 8."
    }

Status code conventions:

- `400 Bad Request`: invalid request input.
- `404 Not Found`: unknown hub.
- `502 Bad Gateway`: invalid upstream response.
- `503 Service Unavailable`: upstream service cannot be reached or is unavailable.

## Environment configuration

| Variable | Default | Used by |
|---|---|---|
| `INGESTION_SERVICE_URL` | `http://localhost:7050` | Hub |
| `HUB_SERVICE_URL` | `http://localhost:7051` | Delay Stage and Transit |
| `DELAY_STAGE_SERVICE_URL` | `http://localhost:7052` | Transit |
| `HUB_SERVICE_PORT` | `7051` | Hub |
| `DELAY_STAGE_SERVICE_PORT` | `7052` | Delay Stage |
| `TRANSIT_SERVICE_PORT` | `7053` | Transit |
| `TRANSIT_BASE_DURATION_MINUTES` | `120` | Transit |
| `TRANSIT_MINUTES_PER_STAGE` | `15` | Transit |

## Startup order

Start services in dependency order:

1. Ingestion
2. Hub
3. Delay Stage
4. Transit

Each service is an independent Maven project. Run its Maven commands from that service's own directory.

## Iteration boundary

Iteration 2 uses REST calls for current delay-stage reads. Delay Stage state is in memory. The transition to publishing and subscribing to `package-status-topic` belongs to Iteration 3.