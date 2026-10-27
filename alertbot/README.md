# AlertBot

## Overview

AlertBot consumes package-status events from ActiveMQ and creates simulated
notifications for critical delay stages.

Alert threshold rule:

    stage >= ALERT_STAGE_THRESHOLD

The default threshold is 5. Stage 4 does not trigger an alert; stage 5 does.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /health` | Service and ActiveMQ consumer status |
| `GET /alerts` | Recent simulated notifications |

AlertBot listens on port 7054 by default.

## Configuration

- `ACTIVEMQ_BROKER_URL`: defaults to `tcp://localhost:61616`.
- `PACKAGE_STATUS_TOPIC`: defaults to `package-status-topic`.
- `ALERT_STAGE_THRESHOLD`: defaults to `5`, valid range 0–8.
- `ALERTBOT_JMS_CLIENT_ID`: defaults to `logisticsconnect-alertbot`.
- `ALERTBOT_JMS_SUBSCRIPTION`: defaults to `alertbot-package-status`.
- `ALERTBOT_SERVICE_PORT`: defaults to `7054`.

## Build and run

From the repository root:

```bash
mvn -f alertbot/pom.xml package
java -jar alertbot/target/alertbot.jar

ActiveMQ should be available for incoming notifications. AlertBot's HTTP health endpoint can start while the consumer is reconnecting.

## Tests

Run the evaluator and boundary tests:

```bash
mvn -f alertbot/pom.xml test
```

Run the complete system through the root runner:

```bash
python scripts/run-iteration-3-4-tests.py --mode acceptance
python scripts/run-iteration-3-4-tests.py --mode smoke
```

## Limitations

Notifications are simulated through application logs and the `/alerts` endpoint. There is no external social-media integration. Alert history and duplicate detection are in memory and are reset when AlertBot restarts.