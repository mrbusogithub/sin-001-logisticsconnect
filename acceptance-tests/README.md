# LogisticsConnect Acceptance Tests

## Purpose

This independent Maven module tests the live integration of LogisticsConnect
services over HTTP and ActiveMQ.

The service applications are independent Maven projects; these acceptance
tests exercise the running processes rather than importing their internal
classes.

## Prerequisites

- Java 21.
- Maven 3.8 or later.
- Python 3.
- Docker with Docker Compose.
- The sample dataset containing hub `H-500`.

The automatic runner starts ActiveMQ and the required services if they are
not already running.

## Run acceptance tests

From the repository root:

```bash
python scripts/run-iteration-3-4-tests.py --mode acceptance
```

Or use Make:

```bash
make acceptance-test
```

For the full event-flow smoke test:

```bash
python scripts/run-iteration-3-4-tests.py --mode smoke
```

Or:

```bash
make smoke-test
```

## Coverage

The existing acceptance-test class verifies the REST integration and the
asynchronous messaging flow, including:

1. Service health endpoints.
2. Hub data returned from the cleaned dataset.
3. Case-insensitive hub lookup.
4. Delay-stage validation and storage.
5. Transit's ETA calculation using event-derived stage state.
6. Invalid delay stages and unknown hubs.
7. A real delay-stage event reaching Transit through ActiveMQ.
8. A threshold event reaching AlertBot and producing a simulated alert.

The unit tests separately cover event serialization, invalid event fields,
publisher behaviour, duplicate processing, timestamp ordering and AlertBot
threshold boundaries.

## Manual execution

To run the JUnit tests without the automatic runner, first start ActiveMQ and
all five application services. Then execute:

```bash
mvn -f acceptance-tests/pom.xml test
```

Use the automatic runner for the normal development workflow because it also
verifies broker connectivity and service startup.

## Logs

On local integration-test failures, inspect the logs under:

```text
target/iteration-3-4-logs/
```