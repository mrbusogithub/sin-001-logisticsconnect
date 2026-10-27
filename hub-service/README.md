# Hub Service

## Purpose

Hub Service loads the cleaned hub data from Ingestion at startup and keeps a
local cache for efficient lookups. Transit and Delay Stage use it to confirm
hub IDs and retrieve hub information.

## Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Returns service health and the cached hub count. |
| GET | `/hubs` | Returns all cached hubs. |
| GET | `/hubs/{hubId}` | Returns one hub by ID. |

Default port: `7051`.

Hub ID lookup is case-insensitive. An unknown hub returns HTTP 404.

## Configuration

`INGESTION_SERVICE_URL` sets the Ingestion base URL. The default is
`http://localhost:7050`.

`HUB_SERVICE_PORT` sets the HTTP port. The default is `7051`.

The service loads its cache when it starts. If Ingestion is unavailable or
returns invalid hub data, Hub Service does not start successfully with an
empty or incomplete directory.

## Build and test

From the repository root:

```bash
mvn -f hub-service/pom.xml package
mvn -f hub-service/pom.xml test
```

Check the endpoints:

```bash
curl http://localhost:7051/health
curl http://localhost:7051/hubs
curl http://localhost:7051/hubs/H-500
```

## Limitations

Hub data is cached in memory and refreshed when the service restarts.