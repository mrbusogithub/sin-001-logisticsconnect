# Hub Service

## Overview

Hub Service provides cleaned hub information to other LogisticsConnect
services. It loads data from Ingestion Service and caches it in memory.

Part of the [LogisticsConnect](../README.md) project.

## Build

From the project root:

```bash
mvn -f hub-service/pom.xml package

## Run

From the project root:

```bash
java -jar hub-service/target/hub-service.jar
```

Hub Service listens on port 7051 by default.

## Endpoints

| Method | Endpoint | Purpose |
| -- | -- | -- |
| GET | `/health` | Reports service health and cached hub count |
| GET | `/hubs` | Returns all cached hubs as a JSON array |
| GET | `/hubs/{hubId}` | Returns one hub by ID |
| POST | `/hubs/reload` | Reloads the cached directory from Ingestion Service |

## Refreshing hub data

Hub Service caches its directory in memory. It does not automatically detect changes to the CSV file.

After editing `ingestion-service/src/main/resources/hubs-global.csv`:

1. Rebuild the Ingestion Service JAR.
2. Restart Ingestion Service so it loads the new packaged CSV.
3. Call `POST /hubs/reload` so Hub Service fetches the updated data.

Rebuild Ingestion Service from the project root:

```bash
mvn -f ingestion-service/pom.xml package -DskipTests
```

After restarting Ingestion Service, trigger the Hub Service reload:

```bash
curl -i -X POST http://localhost:7051/hubs/reload
```

A successful reload returns HTTP 200 with a JSON response containing `status: RELOADED` and the new `hubCount`.

If the reload fails, Hub Service returns HTTP 503 and continues serving its previous cached directory.

## Tests

Run the Hub Service unit tests from the project root:

```bash
mvn -f hub-service/pom.xml test
```

Run the project's integrated checks using the root Makefile:

```bash
make test
make smoke-test
```