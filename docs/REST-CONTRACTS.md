# LogisticsConnect — REST and Event Contracts

## Scope

Ingestion exposes cleaned hub data. Hub Service provides hub lookup data.
Delay Stage accepts stage updates over REST and publishes package-status events.
Transit consumes those events and calculates ETAs from its local event-derived
state. AlertBot consumes the same events and produces simulated alerts.

All services are independent Maven projects.

## Service addresses

| Service | Default URL |
|---|---|
| Ingestion | `http://localhost:7050` |
| Hub | `http://localhost:7051` |
| Delay Stage | `http://localhost:7052` |
| Transit | `http://localhost:7053` |
| AlertBot | `http://localhost:7054` |

ActiveMQ defaults to `tcp://localhost:61616`.

## Hub Service

### `GET /health`

Returns service health and cached hub information.

### `GET /hubs`

Returns the cleaned hub collection.

### `GET /hubs/{hubId}`

Returns one hub record. Hub IDs are trimmed and compared without regard to
case. An unknown hub returns HTTP 404.

The Hub Service loads its cleaned data from Ingestion at startup.

## Delay Stage Service

### `GET /delay-stage/{hubId}`

Returns the current delay stage for a known hub.

Example response:

    {
      "hubId": "H-501",
      "stage": 3
    }

A hub without a saved stage returns stage 0.

### `POST /delay-stage/{hubId}`

Request:

    {
      "stage": 3
    }

Valid stages are integers from 0 through 8, inclusive.

A valid change is published to `package-status-topic`. A request that sets the
current stage again is accepted without publishing a new event.

Invalid request JSON or an invalid stage returns HTTP 400.
An unknown hub returns HTTP 404.
A changed stage that cannot be published returns HTTP 503 and is not committed
to the current in-memory stage map.

Stage state is not persisted across Delay Stage Service restarts.

## Package-status event

Topic: `package-status-topic`

Contract:

    {
      "hubId": "H-501",
      "stage": 5,
      "timestamp": "2026-07-18T10:15:00Z"
    }

All three fields are required.

- `hubId`: non-blank string, normalized to uppercase.
- `stage`: integer in the range 0–8.
- `timestamp`: valid ISO-8601 instant.

The producer sends JSON text messages with persistent delivery.

## Transit Service

### `GET /transit/{hubId}`

Transit retrieves hub details from Hub Service, but it does not make a runtime
REST request to Delay Stage Service. The delay stage comes from the latest
valid package-status event applied by Transit.

Formula:

    etaMinutes = baseDurationMinutes + (delayStage * minutesPerStage)

Default configuration:

- Base duration: 120 minutes.
- Additional duration per stage: 15 minutes.
- Valid stages: 0–8.

For stage 3, the expected ETA is 165 minutes (`PT2H45M`).

The response includes:

- `hubId`
- `province`
- `sortingCenter`
- `delayStage`
- `baseDurationMinutes`
- `minutesPerStage`
- `etaMinutes`
- `etaDuration`
- `etaAt`
- `delayEventTimestamp`

If Transit has not yet received an event for a hub, it reports default stage
0 until a valid event is applied. Updates are asynchronous, so the response
may briefly reflect the previous state after a Delay Stage update.

## AlertBot

### `GET /health`

Returns service health, current consumer connectivity, configured threshold
and the current in-memory alert count.

### `GET /alerts`

Returns the retained simulated alerts from this AlertBot process.

The default alert rule is:

    stage >= 5

Therefore:

- Stage 4 does not trigger an alert.
- Stage 5 triggers an alert.
- Stages 6, 7 and 8 trigger alerts.

The threshold is configurable through `ALERT_STAGE_THRESHOLD`, which must be
between 0 and 8.

The alert history is in memory and retains at most 100 recent alerts.

## Error response shape

    {
      "error": "INVALID_STAGE",
      "message": "Stage must be an integer between 0 and 8."
    }

Common HTTP status conventions:

- `400 Bad Request`: invalid request data.
- `404 Not Found`: unknown hub.
- `502 Bad Gateway`: an invalid upstream REST response.
- `503 Service Unavailable`: an upstream service or required message
  publication is unavailable.

Invalid asynchronous events are logged and ignored by the relevant consumer.

## Configuration

| Variable | Default | Used by |
|---|---|---|
| `INGESTION_SERVICE_URL` | `http://localhost:7050` | Hub |
| `HUB_SERVICE_URL` | `http://localhost:7051` | Delay Stage and Transit |
| `HUB_SERVICE_PORT` | `7051` | Hub |
| `DELAY_STAGE_SERVICE_PORT` | `7052` | Delay Stage |
| `TRANSIT_SERVICE_PORT` | `7053` | Transit |
| `ALERTBOT_SERVICE_PORT` | `7054` | AlertBot |
| `TRANSIT_BASE_DURATION_MINUTES` | `120` | Transit |
| `TRANSIT_MINUTES_PER_STAGE` | `15` | Transit |
| `ACTIVEMQ_BROKER_URL` | `tcp://localhost:61616` | Messaging services |
| `PACKAGE_STATUS_TOPIC` | `package-status-topic` | Messaging services |
| `ALERT_STAGE_THRESHOLD` | `5` | AlertBot |

Transit no longer uses `DELAY_STAGE_SERVICE_URL`.

## Failure handling and known limitations

Consumers validate JSON, required fields, stage range and timestamp format.
Malformed events are ignored rather than being allowed to terminate a consumer.

Transit ignores exact duplicate event keys and out-of-order events.
AlertBot ignores duplicate events to prevent duplicate alerts.

Deduplication and stage state are in memory. They reset on application restart.
The design has no transactional outbox or database-backed business state.
Persistent JMS messages and durable subscriptions help retain messages for
existing offline subscriptions, but they do not make producer state updates
and message delivery one atomic database transaction.

## Startup and tests

Start ActiveMQ:

    docker compose -f common/docker-compose.yml up -d

Build the services:

    mvn -f ingestion-service/pom.xml package
    mvn -f hub-service/pom.xml package
    mvn -f delay-stage-service/pom.xml package
    mvn -f transit-service/pom.xml package
    mvn -f alertbot/pom.xml package

Run the complete integration acceptance tests:

    python scripts/run-iteration-3-4-tests.py --mode acceptance

Run the event-flow smoke test:

    python scripts/run-iteration-3-4-tests.py --mode smoke