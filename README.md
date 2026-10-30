# LogisticsConnect

LogisticsConnect is a Java-based logistics integration system that cleans a
legacy hub dataset, exposes hub and delivery information through REST APIs,
publishes delay-stage changes using Apache ActiveMQ, and generates simulated
alerts for critical delays.

The project was developed in four iterations, progressing from data cleaning
to REST integration, asynchronous messaging and alerting.

## 1. Project objectives and achievements

The original assignment required the following four stages:

| Iteration | Original requirement | Implementation |
|---|---|---|
| 1 — Data cleaning | Parse and clean the legacy `hubs-global.csv` dataset, normalize inconsistent values, handle duplicates and expose the cleaned records through REST. | Ingestion parses CSV rows into Java domain objects, normalizes hub IDs and province names, trims and collapses whitespace, normalizes boolean values, handles placeholders and deduplicates records using a deterministic identity rule. |
| 2 — REST integration | Introduce Hub, Delay Stage and Transit services with explicit HTTP contracts and an ETA calculation. | Hub loads and caches cleaned records from Ingestion. Delay Stage validates and stores delay-stage updates. Transit looks up hub details and calculates ETAs using its configured base duration and stage allowance. |
| 3 — Event-driven integration | Provision ActiveMQ and replace unnecessary synchronous runtime dependencies with asynchronous messaging. | Delay Stage publishes validated package-status events to `package-status-topic`. Transit subscribes to the topic and updates its local delay state without polling Delay Stage over REST. |
| 4 — AlertBot | Add another event consumer and demonstrate critical-delay notifications. | AlertBot consumes the same topic and generates a simulated alert when the event stage meets the configured threshold. The default rule is `stage >= 5`. |

### Data-cleaning scope

The supplied CSV currently contains four columns:

- `hub_id`
- `Province`
- `sorting_center`
- `active`

It contains casing inconsistencies, whitespace variations, duplicate hub/sorting-centre records, different boolean spellings and placeholder values.

The supplied file does not contain date or numeric fields, so date normalization and numeric-column validation are not applicable to its current schema.

The duplicate-resolution rule uses normalized province and sorting-centre names as the identity. When duplicate records conflict, the smallest normalized hub ID is selected as the canonical record. Records without sufficient identity information are not merged based on guesswork.

## 2. Architecture

```text
 Legacy CSV
     |
     v
 Ingestion Service (7050)
     |
     | GET /hubs
     v
 Hub Service (7051)
     ^
     |
     | REST hub lookup
     |
 Transit Service (7053) <------- ActiveMQ Topic -------- Delay Stage Service (7052)
     ^                                 ^                         |
     |                                 |                         |
     | ETA and event-derived stage     |                         |
     |                                 |                         |
     +---------------------------------+-------------------------+
                                       |
                                       |
                                 AlertBot (7054)
                                 Simulated alert
```

The runtime event flow is:

```text
Client
  |
  | POST /delay-stage/{hubId}
  v
Delay Stage Service
  |
  | JMS package-status event
  v
ActiveMQ: package-status-topic
  |                         |
  v                         v
Transit Service          AlertBot
  |                         |
  v                         v
Updated ETA/state       Simulated notification
```

Transit still uses REST to obtain hub details from Hub Service. The current
delay stage is sourced from the latest valid event received by Transit, rather
than a synchronous request to Delay Stage.

### Package-status event

The topic carries JSON text messages with this contract:

```json
{
  "hubId": "H-501",
  "stage": 5,
  "timestamp": "2026-07-18T10:15:00Z"
}
```

The required fields are:

- `hubId`: non-blank string, normalized to uppercase.
- `stage`: integer from 0 through 8, inclusive.
- `timestamp`: valid ISO-8601 instant.

Consumers reject malformed messages, missing required fields, invalid stages
and invalid timestamps.

## 3. Services and ports

| Service | Port | Responsibility |
|---|---:|---|
| Ingestion Service | 7050 | Parses, cleans and exposes the legacy hub dataset. |
| Hub Service | 7051 | Loads and caches cleaned hub records and serves hub lookups. |
| Delay Stage Service | 7052 | Validates delay stages and publishes events when a stage changes. |
| Transit Service | 7053 | Consumes delay events and calculates estimated arrival times. |
| AlertBot | 7054 | Consumes delay events and exposes simulated critical-delay alerts. |
| ActiveMQ Classic | 61616 | Broker connection port (OpenWire). |
| ActiveMQ web console | 8161 | Local broker administration and inspection. |

The five application services are separate Maven projects. There is no root
aggregator POM, so the Makefile and Python runner coordinate their builds and
test execution.

## 4. Repository navigation

| Location | Contents |
|---|---|
| [`ingestion-service/`](ingestion-service/) | CSV parsing, normalization, deduplication, dataset resource and tests. See [`ingestion-service/README.md`](ingestion-service/README.md). |
| [`hub-service/`](hub-service/) | Hub directory cache, hub REST endpoints and tests. See [`hub-service/README.md`](hub-service/README.md). |
| [`delay-stage-service/`](delay-stage-service/) | Delay-stage REST API, ActiveMQ producer, message contract and producer tests. See [`delay-stage-service/README.md`](delay-stage-service/README.md). |
| [`transit-service/`](transit-service/) | ETA API, event consumer, event-state processor and tests. See [`transit-service/README.md`](transit-service/README.md). |
| [`alertbot/`](alertbot/) | Alert consumer, threshold evaluator, simulated notifications and tests. See [`alertbot/README.md`](alertbot/README.md). |
| [`common/`](common/) | ActiveMQ Docker Compose configuration and messaging documentation. See [`common/README.md`](common/README.md). |
| [`acceptance-tests/`](acceptance-tests/) | JUnit tests against the live REST and messaging system. See [`acceptance-tests/README.md`](acceptance-tests/README.md). |
| [`docs/REST-CONTRACTS.md`](docs/REST-CONTRACTS.md) | REST endpoints, request/response shapes, JMS event contract, configuration and limitations. |
| [`scripts/`](scripts/) | Cross-platform Python smoke tests and automatic service/test runners. |
| [`.github/workflows/ci.yml`](.github/workflows/ci.yml) | GitHub Actions build, unit-test and integration pipeline. |
| [`Makefile`](Makefile) | Short commands for building, running, stopping, testing and cleaning the project. |

## 5. Prerequisites

Install the following tools:

- Java 21
- Maven 3.8 or later
- Python 3
- Docker with Docker Compose
- GNU Make (optional, for the `make` commands)

The Python scripts use the standard library, so no extra Python packages are
required.

Run the commands in this README from the repository root.

## 6. Quick start — recommended

The simplest way to demonstrate the system is to run the automated smoke test.
It builds the services, starts ActiveMQ if needed, starts or reuses the
services, waits for Transit and AlertBot to connect, and verifies the event
flow.

### Step 1: See the available commands

```bash
make help
```

That prints every supported target. The main ones:

| Target | Purpose |
| -- | -- |
| `make build` | Build all five service JARs |
| `make run-broker` | Start ActiveMQ (`docker compose up -d`) |
| `make run-ingestion` | Start Ingestion on `:7050` |
| `make run-hub` | Start Hub on `:7051` |
| `make run-delay-stage` | Start Delay Stage on `:7052` |
| `make run-transit` | Start Transit on `:7053` |
| `make run-alertbot` | Start AlertBot on `:7054` |
| `make set-stage [HUB=H-500] [STAGE=5]` | POST a delay-stage change |
| `make show-transit [HUB=H-500]` | GET Transit's ETA |
| `make show-alerts` | GET AlertBot's retained alerts |
| `make status` | Show which services are up |
| `make stop` | Kill all services and take the broker down |
| `make smoke-test` / `make acceptance-test` / `make test` | Run tests |

### Step 2: Run the complete event-flow smoke test

```bash
make smoke-test
```

The runner verifies the five HTTP health endpoints, changes the delay stage,
waits for Transit to apply the event and calculate an ETA, and checks that
AlertBot generates a simulated alert at the threshold.

The script restores the original stage when it finishes.

### Step 3: Run the automated acceptance tests

```bash
make acceptance-test
```

This runs the JUnit acceptance tests against the live application services.

### Step 4: Run the unit tests and acceptance tests together

```bash
make test
```

This runs the module unit tests followed by the live acceptance tests.
Run `make smoke-test` separately when you also want to verify the full
event-flow demonstration.

The test runner stops only the application processes and broker it started.
Already-running services are reused and are not terminated by its cleanup.

## 7. Understanding the Makefile commands

Run `make help` whenever you need a reminder of the available commands.

| Command | Purpose |
|---|---|
| `make help` | Displays the development commands. |
| `make build` | Packages all five application services into runnable JARs. |
| `make run-broker` | Starts ActiveMQ via `docker compose up -d`. |
| `make run-ingestion` / `run-hub` / `run-delay-stage` / `run-transit` / `run-alertbot` | Starts one service in the foreground. Ctrl-C stops it. |
| `make set-stage [HUB=...] [STAGE=...]` | POSTs a delay-stage change and echoes the raw curl call. |
| `make show-transit [HUB=...]` | GETs Transit's ETA for a hub. |
| `make show-alerts` | GETs AlertBot's retained simulated alerts. |
| `make status` | Shows which services and the broker are up. |
| `make stop` | Kills all application services and stops the broker. Idempotent. |
| `make unit-test` | Runs unit tests in each application service. |
| `make acceptance-test` | Builds/starts the required system components and runs the JUnit acceptance tests. |
| `make smoke-test` | Starts the required components and verifies the end-to-end event flow. |
| `make test` | Runs module unit tests followed by the live acceptance tests. |
| `make clean` | Cleans the independent Maven build outputs. |

GNU Make is optional. From the repository root, the equivalent Python
integration commands are:

```bash
python3 scripts/run-iteration-3-4-tests.py --mode acceptance
python3 scripts/run-iteration-3-4-tests.py --mode smoke
```

On Windows, use `python` instead of `python3` if that is the command installed
on your machine.

To reuse already-built JARs, pass `--skip-build`:

```bash
python3 scripts/run-iteration-3-4-tests.py --mode smoke --skip-build
```

Use that option only after the service JARs have been built successfully.

## 8. Running the application manually

The automated runner is recommended for testing. For a manual demonstration,
start the broker and services in separate terminals.

The commands below use the raw `java -jar` form. The same steps are
available as `make run-broker`, `make run-ingestion`, `make run-hub`,
`make run-delay-stage`, `make run-transit`, `make run-alertbot` — see
Section 7.

### Step 1: Start ActiveMQ

From the repository root:

```bash
docker compose -f common/docker-compose.yml up -d
```

Check the broker:

```bash
docker compose -f common/docker-compose.yml ps
docker compose -f common/docker-compose.yml logs activemq
```

The services use `tcp://localhost:61616` by default.

### Step 2: Build each service

From the repository root:

```bash
mvn -f ingestion-service/pom.xml package
mvn -f hub-service/pom.xml package
mvn -f delay-stage-service/pom.xml package
mvn -f transit-service/pom.xml package
mvn -f alertbot/pom.xml package
```

### Step 3: Start the services

Start each command in its own terminal, in this order:

```bash
java -jar ingestion-service/target/ingestion-service.jar
```

```bash
java -jar hub-service/target/hub-service.jar
```

```bash
java -jar delay-stage-service/target/delay-stage-service.jar
```

```bash
java -jar transit-service/target/transit-service.jar
```

```bash
java -jar alertbot/target/alertbot.jar
```

Wait for the Hub Service to load its records and for Transit and AlertBot to
report their ActiveMQ subscriptions as connected before demonstrating a stage
change.

## 9. See the event flow in action

The following example uses sample hub `H-500`.

> **Tip:** each step below has a `make` shorthand — `make show-transit`,
> `make set-stage STAGE=5`, `make show-alerts`. The raw `curl` commands
> are kept below so the HTTP contract stays visible. Run `make status`
> first to confirm all five services and the broker are up.

### Step 1: Check the services

The following endpoints should respond successfully:

- `GET http://localhost:7050/health`
- `GET http://localhost:7051/health`
- `GET http://localhost:7052/health`
- `GET http://localhost:7053/health`
- `GET http://localhost:7054/health`

All five services return JSON health responses (`{"status": "UP", ...}`).
Run `make status` for a one-line summary of all five plus the broker.

### Step 2: Check hub data

```bash
curl http://localhost:7051/hubs
```

Look up one hub:

```bash
curl http://localhost:7051/hubs/H-500
```

### Step 3: Publish a delay-stage change

Use PowerShell:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:7052/delay-stage/H-500" `
  -ContentType "application/json" `
  -Body '{"stage":5}'
```

Or use curl:

```bash
curl -X POST http://localhost:7052/delay-stage/H-500 \
  -H "Content-Type: application/json" \
  -d '{"stage":5}'
```

A successful real stage change returns its event timestamp. Delay Stage
publishes the corresponding event to `package-status-topic`.

Setting the same stage again does not create another event.

### Step 4: Check the Transit ETA

```bash
curl http://localhost:7053/transit/H-500
```

The demo configuration uses:

```text
ETA minutes = 120 + (stage * 15)
```

For stage 5, the expected ETA is 195 minutes, or `PT3H15M`.

Transit may need a short interval to receive and apply the event because
messaging is asynchronous.

### Step 5: Check AlertBot's simulated notification

```bash
curl http://localhost:7054/alerts
```

The default alert rule is:

```text
stage >= 5
```

Stage 4 does not trigger an alert; stage 5 does. The alert includes the hub
ID, stage, event timestamp, threshold, simulated flag and message.

AlertBot also prints a `[SIMULATED ALERT]` line in its application logs.
No real social-media notification is sent.

## 10. REST and messaging contracts

The full API and configuration reference is maintained in
[`docs/REST-CONTRACTS.md`](docs/REST-CONTRACTS.md).

Important REST endpoints:

| Service | Endpoint | Purpose |
|---|---|---|
| Ingestion | `GET /hubs` | Returns cleaned hub records. |
| Hub | `GET /hubs` | Returns cached hub records. |
| Hub | `GET /hubs/{hubId}` | Looks up a hub. |
| Delay Stage | `GET /delay-stage/{hubId}` | Reads the current delay stage. |
| Delay Stage | `POST /delay-stage/{hubId}` | Validates and changes a delay stage. |
| Transit | `GET /transit/{hubId}` | Returns hub details and ETA data. |
| AlertBot | `GET /alerts` | Returns the retained simulated alerts. |

The event contract is JSON with `hubId`, `stage` and `timestamp`. The topic
name is configurable but defaults to `package-status-topic`.

## 11. Configuration

The following environment variables have defaults suitable for local
development:

| Variable | Default | Purpose |
|---|---|---|
| `INGESTION_SERVICE_URL` | `http://localhost:7050` | Hub's source of cleaned data. |
| `HUB_SERVICE_URL` | `http://localhost:7051` | Hub lookup address for dependent services. |
| `ACTIVEMQ_BROKER_URL` | `tcp://localhost:61616` | ActiveMQ broker address. |
| `PACKAGE_STATUS_TOPIC` | `package-status-topic` | JMS topic. |
| `ACTIVEMQ_RECONNECT_DELAY_MS` | `5000` | Delay between consumer reconnect attempts. |
| `TRANSIT_BASE_DURATION_MINUTES` | `120` | Base ETA in minutes. |
| `TRANSIT_MINUTES_PER_STAGE` | `15` | Added minutes per delay stage. |
| `ALERT_STAGE_THRESHOLD` | `5` | Inclusive alert threshold, valid range 0–8. |

The producer and both consumers use configuration rather than hard-coded
service-to-service message addresses.

## 12. Testing and CI

Run all module unit tests:

```bash
make unit-test
```

Run live acceptance tests:

```bash
make acceptance-test
```

Run the event-flow smoke test:

```bash
make smoke-test
```

Run the GitHub Actions workflow by pushing a commit or opening a pull request.
The workflow builds the modules, runs their unit tests, starts ActiveMQ and
runs the acceptance and smoke tests.

On local test failure, inspect:

```text
target/iteration-3-4-logs/
```

The Python runner writes service logs there to help diagnose startup and
integration failures.

## 13. Known limitations and design decisions

- Delay Stage state is held in memory and resets when its service restarts.
- Transit's delay-stage state is derived from events and is held in memory.
  Before an event has been processed, its default stage is 0.
- Updates are eventually consistent; the HTTP response from Delay Stage does
  not guarantee that both consumers have processed the event already.
- Event deduplication is performed in memory and resets on consumer restart.
- The design uses persistent JMS messages and durable topic subscriptions,
  but it does not have a database-backed transactional outbox.
- AlertBot generates simulated notifications only. It does not connect to an
  external social-media or notification API.
- Alert history is limited to the most recent 100 alerts in the running
  AlertBot process.
- The current CSV has no date or numeric columns; those normalizations are
  outside the scope of its present four-column dataset.

These decisions keep the demonstration small and understandable while
showing the transition from synchronous REST integration to asynchronous
event-driven integration.

## 14. Final project flow

```text
Legacy CSV
    |
    v
Ingestion
    |
    v
Hub
    |
    +---------------------> Transit (hub lookup over REST)
    |
    v
Delay Stage
    |
    v
ActiveMQ: package-status-topic
    |                         |
    v                         v
Transit                    AlertBot
    |                         |
    v                         v
Updated ETA              Simulated alert
```

The core objective is to demonstrate a working integration system whose
services have clear responsibilities, explicit REST and JMS contracts,
automated tests, a reproducible broker setup and a simple end-to-end demo.