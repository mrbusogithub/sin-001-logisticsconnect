# Iteration 2 Acceptance Tests

This independent Maven module tests the real REST integration between
the four LogisticsConnect services.

## Prerequisites

- Java 17 or later
- Maven 3.8 or later
- All four services running
- The sample dataset containing hub H-500

## Run the acceptance tests

Start the services in separate terminals, following the root README.

From the repository root, run:

```powershell
mvn -f acceptance-tests/pom.xml test
```

The tests verify:

1. All four services respond to health requests.
2. Hub Service exposes the cleaned hub records.
3. Hub lookup is case-insensitive.
4. Delay Stage accepts and stores stage 3.
5. Transit returns 165 minutes (`PT2H45M`) for stage 3.
6. Invalid delay stages return HTTP 400.
7. Unknown hubs return HTTP 404.

The acceptance tests require running services and are intentionally
separate from the unit tests in each service module.
