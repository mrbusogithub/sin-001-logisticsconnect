# AlertBot

## Purpose

AlertBot subscribes to `package-status-topic` and evaluates package-status
events against a configurable delay-stage threshold.

When the event stage meets or exceeds the threshold, AlertBot generates a
simulated notification. No external social-media API is required.

## Alert rule

```text
stage >= ALERT_STAGE_THRESHOLD
```

The default threshold is `5`.

- Stage 4 does not trigger an alert.
- Stage 5 triggers an alert.
- Stages 6–8 trigger an alert.

## Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Returns health, topic, threshold and consumer connection status. |
| GET | `/alerts` | Returns retained simulated alerts. |

Default port: `7054`.

AlertBot keeps at most 100 recent alerts in memory. A qualifying event is
also printed to the application log with a `[SIMULATED ALERT]` prefix.

## Configuration

- `ALERTBOT_SERVICE_PORT`: defaults to `7054`.
- `ALERT_STAGE_THRESHOLD`: defaults to `5`; valid range 0–8.
- `ACTIVEMQ_BROKER_URL`: defaults to `tcp://localhost:61616`.
- `PACKAGE_STATUS_TOPIC`: defaults to `package-status-topic`.
- `ALERTBOT_JMS_CLIENT_ID`: defaults to `logisticsconnect-alertbot`.
- `ALERTBOT_JMS_SUBSCRIPTION`: defaults to `alertbot-package-status`.

## Build and test

From the repository root:

```bash
mvn -f alertbot/pom.xml package
mvn -f alertbot/pom.xml test
```

Start the complete application through the integration runner:

```bash
python scripts/run-iteration-3-4-tests.py --mode acceptance
python scripts/run-iteration-3-4-tests.py --mode smoke
```

## Limitations

Notifications are simulated through logs and the `/alerts` endpoint. Alert
history and duplicate detection are held in memory and reset when AlertBot
restarts. There is no external notification integration.