# common — Asynchronous Decoupling (MQ)

## Overview

Topic: `package-status-topic`

Package status updates move from latency-driven RPC to bandwidth-driven messaging.

Part of the [LogisticsConnect](../README.md) project. Holds the ActiveMQ broker shared
by the services below — not a service itself, so it has no port of its own.

- Producer: `delay-stage-service` (`../delay-stage-service`)
- Consumer(s): `transit-service` (required, stage 3); `alertbot` (stretch, stage 4 —
  reacts to stage changes to decide when to raise an alert)

Broker URL and topic name are shared via a common `co.wethinkcode.logisticsconnect.mq.MqConfig` class
(`BROKER_URL`, `TOPIC`). It's identical in every participating service's own source
tree — each service here is an independent Maven project with no shared parent pom,
so the common package is duplicated rather than imported from one place.

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

This folder holds the broker config and notes only — the actual publish/subscribe
code belongs in the producer/consumer services listed above (their poms already
depend on `activemq-client`, and each already has
`src/main/java/co/wethinkcode/logisticsconnect/mq/MqConfig.java`).

## Build

Nothing to build here directly — this folder just brings up the broker used by the
services listed above.

## Broker

The broker is provisioned by `docker-compose.yml`.

## Run

Start it from the repository root:

```
docker compose -f common/docker-compose.yml up -d
```

- Broker URL for clients: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default admin/admin)

Then start the producer/consumer services as usual (`mvn package && java -jar ...`
from their own directories at the project root).

## Test

```
docker compose ps          # confirm the broker container is healthy
```

Once the TODOs below are implemented, verify end-to-end by publishing a message from
`delay-stage-service` and confirming the consumer(s) receive it — e.g. via logs, or by
watching the topic in the web console.

## TODO

- Add `activemq-client` publish logic to `delay-stage-service` on its stage/state-change endpoint.
- Add `activemq-client` subscriber logic to consumer service(s) above, replacing any
  direct synchronous calls to `delay-stage-service`.
- `alertbot` (stretch goal) needs its own subscriber logic too — its `pom.xml`
  already has the `activemq-client` dependency alongside the other participating
  services.

## Overview on completing the 'TODO':

# ActiveMQ Integration

ActiveMQ Classic provides asynchronous package-status messaging for
LogisticsConnect.

The broker runs independently of the five application services.

Check the container:

```bash
docker compose -f common/docker-compose.yml ps
docker compose -f common/docker-compose.yml logs activemq
```

Stop the broker:

```bash
docker compose -f common/docker-compose.yml down
```

Default addresses:

| Purpose | Address |
| -- | -- |
| Java client connection (OpenWire) | tcp://localhost:61616 |
| Web console | http://localhost:8161 |

The web console is for development and inspection, not a required dependency for normal application requests.

## Topic contract

Topic: `package-status-topic`

Example event:

```json
{
  "hubId": "H-501",
  "stage": 5,
  "timestamp": "2026-07-18T10:15:00Z"
}
```

Contract rules:

- `hubId` is a non-blank string, normalized to uppercase.
- `stage` is an integer from 0 through 8.
- `timestamp` is a valid ISO-8601 instant.
- All three fields are required.
- Invalid messages are ignored by consumers and must not update application state or generate an alert.

The contract implementation is intentionally duplicated in the three independent Maven projects to prevent compile-time dependencies between the producer and consumers.

## Responsibilities

- **Delay Stage Service**: validates stage changes and publishes an event only when the stage actually changes.
- **Transit Service**: consumes events and updates its local stage state. It does not call Delay Stage Service to poll for the current stage.
- **AlertBot**: independently consumes the same topic and generates a simulated notification when `stage >= ALERT_STAGE_THRESHOLD`.

## Delivery and subscriptions

The producer uses persistent JMS delivery.

Transit and AlertBot use separate durable topic subscriptions. Persistent messages can be retained for an existing durable subscription while its consumer is offline, subject to broker persistence and message retention.

Each consumer must use a stable client ID and subscription name. A single durable subscription identity should not be used by multiple concurrently running application instances.

## Configuration

| Variable | Default | Purpose |
| -- | -- | -- |
| `ACTIVEMQ_BROKER_URL` | `tcp://localhost:61616` | Broker address |
| `PACKAGE_STATUS_TOPIC` | `package-status-topic` | Topic name |
| `ACTIVEMQ_USERNAME` | Unset | Optional broker username |
| `ACTIVEMQ_PASSWORD` | Unset | Optional broker password |
| `ACTIVEMQ_RECONNECT_DELAY_MS` | 5000 | Delay between consumer reconnect attempts |
| `TRANSIT_JMS_CLIENT_ID` | `logisticsconnect-transit` | Transit durable client ID |
| `TRANSIT_JMS_SUBSCRIPTION` | `transit-package-status` | Transit subscription name |
| `ALERTBOT_JMS_CLIENT_ID` | `logisticsconnect-alertbot` | AlertBot durable client ID |
| `ALERTBOT_JMS_SUBSCRIPTION` | `alertbot-package-status` | AlertBot subscription name |
| `ALERT_STAGE_THRESHOLD` | 5 | Alert stage threshold |

When broker credentials are not configured, the Java clients connect without explicit username and password arguments. Configure credentials to match the actual broker if authentication is enabled.

## Failure handling and duplicate events

Malformed JSON, missing required fields, invalid stages and invalid timestamps are logged and ignored.

Transit ignores exact duplicates and out-of-order events. AlertBot ignores an exact duplicate event so it does not generate the same simulated alert twice.

The consumers' deduplication sets, Transit state, Delay Stage state, and AlertBot alert history are in memory. They reset when their respective application processes restart.

The producer returns HTTP 503 when it cannot publish a requested state change. The implementation does not include a transactional outbox; stronger business-state and event-delivery guarantees would require additional persistent storage and recovery logic.

## End-to-end verification

Run the full integration test from the repository root:

```bash
python scripts/run-iteration-3-4-tests.py --mode acceptance
```

Run the full smoke test:

```bash
python scripts/run-iteration-3-4-tests.py --mode smoke
```

Both commands start ActiveMQ if the local broker port is not already open. The runner also starts required services and waits for Transit and AlertBot to connect to the broker.