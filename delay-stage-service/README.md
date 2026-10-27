# Delay Stage Service

## Purpose

Delay Stage validates and stores the current delay stage for each known hub.
A valid stage change is published as a JMS event to ActiveMQ.

Supported stages are integers from 0 through 8, inclusive.

## Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Returns service health and the stored hub count. |
| GET | `/delay-stage/{hubId}` | Returns the current stage for a known hub. |
| POST | `/delay-stage/{hubId}` | Validates and updates the stage. |

Default port: `7052`.

Example request:

```json
{
  "stage": 5
}
```

A successful real state change publishes an event and returns the event
timestamp. Sending the same stage again does not publish a new event.

Invalid JSON or a stage outside 0–8 returns HTTP 400. Unknown hubs return
HTTP 404. If a changed stage cannot be published, the service returns HTTP
503 and does not update its in-memory stage map.

## Messaging

- Broker address: `ACTIVEMQ_BROKER_URL`
- Topic: `PACKAGE_STATUS_TOPIC`
- Default topic: `package-status-topic`

The event has the fields `hubId`, `stage` and `timestamp`. See
[`../common/README.md`](../common/README.md) and
[`../docs/REST-CONTRACTS.md`](../docs/REST-CONTRACTS.md).

## Build and test

From the repository root:

```bash
mvn -f delay-stage-service/pom.xml package
mvn -f delay-stage-service/pom.xml test
```

Start ActiveMQ first when running the service manually:

```bash
docker compose -f common/docker-compose.yml up -d
java -jar delay-stage-service/target/delay-stage-service.jar
```

## Limitations

Stage state is stored in memory and is lost when Delay Stage Service restarts.
The service does not use a transactional outbox.