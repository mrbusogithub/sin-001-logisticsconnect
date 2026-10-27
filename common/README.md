# ActiveMQ Messaging

## Purpose

ActiveMQ Classic provides asynchronous package-status messaging for
LogisticsConnect.

- **Producer:** Delay Stage Service.
- **Consumer:** Transit Service.
- **Additional consumer:** AlertBot.

Both consumers independently subscribe to the topic `package-status-topic`.

## Start the broker

From the repository root:

```bash
docker compose -f common/docker-compose.yml up -d
```

Verify the broker:

```bash
docker compose -f common/docker-compose.yml ps
docker compose -f common/docker-compose.yml logs activemq
```

Stop it when no longer needed:

```bash
docker compose -f common/docker-compose.yml down
```

## Connection details

| Purpose | Default |
|---|---|
| Broker address | `tcp://localhost:61616` |
| Topic | `package-status-topic` |
| Web console | `http://localhost:8161` |

The web console is an optional development tool for inspecting the broker.
Normal API requests do not require it.

## Event contract

Example JSON message:

```json
{
  "hubId": "H-501",
  "stage": 5,
  "timestamp": "2026-07-18T10:15:00Z"
}
```

The contract requires:

- `hubId`: a non-blank string; hub IDs are normalized to uppercase.
- `stage`: an integer from 0 through 8.
- `timestamp`: a valid ISO-8601 instant.

All three fields are required. Invalid JSON, missing fields, invalid stages and
invalid timestamps are rejected by the consumers without updating their state.

## Event responsibilities

### Delay Stage Service

When a valid stage changes, the producer publishes a JSON text message with
persistent delivery. Repeating the existing stage does not publish an event.

If the event cannot be published, the REST endpoint returns HTTP 503 and
does not commit the requested stage to its in-memory stage map.

### Transit Service

Transit updates its local stage state as events arrive. It uses hub details
from Hub Service and the latest valid event to calculate the ETA.

It does not poll Delay Stage Service for the latest stage.

### AlertBot

AlertBot uses the same topic and applies the configured inclusive rule:

```text
stage >= ALERT_STAGE_THRESHOLD
```

The default threshold is 5. Alerts are simulated through the application
logs and `GET /alerts`.

## Configuration

| Environment variable | Default |
|---|---|
| `ACTIVEMQ_BROKER_URL` | `tcp://localhost:61616` |
| `PACKAGE_STATUS_TOPIC` | `package-status-topic` |
| `ACTIVEMQ_RECONNECT_DELAY_MS` | `5000` |
| `ACTIVEMQ_USERNAME` | Unset |
| `ACTIVEMQ_PASSWORD` | Unset |
| `TRANSIT_JMS_CLIENT_ID` | `logisticsconnect-transit` |
| `TRANSIT_JMS_SUBSCRIPTION` | `transit-package-status` |
| `ALERTBOT_JMS_CLIENT_ID` | `logisticsconnect-alertbot` |
| `ALERTBOT_JMS_SUBSCRIPTION` | `alertbot-package-status` |
| `ALERT_STAGE_THRESHOLD` | `5` |

The client ID and durable subscription name must remain stable for each
consumer. Each concurrently running consumer application should use its own
client identity.

## Reliability and limitations

The consumers retry their connection when the broker is unavailable during
startup or a connection failure.

The producer sends events persistently, and the consumers use durable topic
subscriptions. These features help retain events for an existing durable
subscription while a consumer is offline, subject to broker persistence and
retention configuration.

Transit ignores exact duplicate events and events that are older than its
latest accepted event for a hub. AlertBot suppresses duplicate event keys.

The deduplication sets and application states are held in memory. They reset
when the application restarts. The design does not implement a persistent
database or transactional outbox, so it does not promise an atomic transaction
between business-state updates and broker publication.

## Integration testing

From the repository root, run:

```bash
python scripts/run-iteration-3-4-tests.py --mode acceptance
python scripts/run-iteration-3-4-tests.py --mode smoke
```

The runner starts ActiveMQ if it is not already listening, starts or reuses the
application services, waits for Transit and AlertBot to connect, and performs
the relevant integration checks.