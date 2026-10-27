# Transit Service

## Purpose

Transit obtains hub details from Hub Service and calculates the estimated
arrival time using its local delay state, which is updated asynchronously by
package-status events received from ActiveMQ.

Transit does not poll Delay Stage Service to obtain the current stage.

## Endpoint

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Returns health, ETA configuration, consumer connection status and tracked hub count. |
| GET | `/transit/{hubId}` | Returns hub details, delay stage and ETA information. |

Default port: `7053`.

## ETA calculation

The default demonstration formula is:

```text
etaMinutes = baseDurationMinutes + (delayStage * minutesPerStage)
```

The defaults are:

- Base duration: `120` minutes.
- Additional duration per stage: `15` minutes.
- Supported stage values: 0–8.

For example, stage 3 produces 165 minutes (`PT2H45M`). Stage 5 produces
195 minutes (`PT3H15M`).

An ETA response includes the hub ID, delay stage, ETA minutes, ISO-8601
duration, estimated arrival timestamp and latest delay-event timestamp.

If a hub has not received a valid event, Transit uses stage 0 until a valid
event is applied.

## Configuration

- `HUB_SERVICE_URL`: defaults to `http://localhost:7051`.
- `TRANSIT_SERVICE_PORT`: defaults to `7053`.
- `TRANSIT_BASE_DURATION_MINUTES`: defaults to `120`.
- `TRANSIT_MINUTES_PER_STAGE`: defaults to `15`.
- `ACTIVEMQ_BROKER_URL`: defaults to `tcp://localhost:61616`.
- `PACKAGE_STATUS_TOPIC`: defaults to `package-status-topic`.

## Build and test

From the repository root:

```bash
mvn -f transit-service/pom.xml package
mvn -f transit-service/pom.xml test
```

Run the full system through the integration runner:

```bash
python scripts/run-iteration-3-4-tests.py --mode acceptance
```

## Limitations

Transit's event-derived state and deduplication set are stored in memory.
Updates are eventually consistent, and the default stage is 0 before a hub's
first event is applied.