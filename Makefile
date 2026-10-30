# Commands can be overridden, for example:
# make PYTHON=python3 acceptance-test
# make JAVA=/opt/java21/bin/java run-hub

ifeq ($(OS),Windows_NT)
PYTHON ?= python
else
PYTHON ?= python3
endif

MAVEN ?= mvn
JAVA  ?= java

MAVEN ?= mvn

# Default demo hub; override per call, e.g.  make set-stage HUB=H-501 STAGE=3
HUB   ?= H-500
STAGE ?= 5

.PHONY: help build unit-test acceptance-test smoke-test test clean \
        run-broker stop-broker \
        run-ingestion run-hub run-delay-stage run-transit run-alertbot \
        status stop demo set-stage show-transit show-alerts

# ---------------------------------------------------------------- help ------

help:
	@echo "LogisticsConnect development commands:"
	@echo
	@echo "Setup:"
	@echo "  make build            Build all five services"
	@echo "  make run-broker       Start ActiveMQ (docker compose up -d)"
	@echo "  make stop-broker      Stop ActiveMQ (docker compose down)"
	@echo
	@echo "Run services (one per terminal):"
	@echo "  make run-ingestion    Start ingestion-service on :7050"
	@echo "  make run-hub          Start hub-service on :7051"
	@echo "  make run-delay-stage  Start delay-stage-service on :7052"
	@echo "  make run-transit      Start transit-service on :7053"
	@echo "  make run-alertbot     Start alertbot on :7054"
	@echo
	@echo "Stop / inspect:"
	@echo "  make stop             Kill all services and the broker"
	@echo "  make status           Show which services are up"
	@echo
	@echo "Testing:"
	@echo "  make unit-test        Run unit tests in every service"
	@echo "  make acceptance-test  Run JUnit integration tests"
	@echo "  make smoke-test       Run the live end-to-end event flow"
	@echo "  make test             Run unit tests and acceptance tests"
	@echo "  make clean            Clean the independent Maven projects"
	@echo
	@echo "Typical demo sequence (after 'make build'):"
	@echo "  Terminal 1: make run-broker"
	@echo "  Terminal 2: make run-ingestion"
	@echo "  Terminal 3: make run-hub"
	@echo "  Terminal 4: make run-delay-stage"
	@echo "  Terminal 5: make run-transit"
	@echo "  Terminal 6: make run-alertbot"
	@echo "  Anywhere:   make status"
	@echo "  Anywhere:   make stop"
	@echo
	@echo "Endpoints (demo shorthands):"
	@echo "  make set-stage  [HUB=H-500] [STAGE=5]   POST a delay-stage change"
	@echo "  make show-transit [HUB=H-500]           GET Transit's ETA"
	@echo "  make show-alerts                        GET AlertBot's alerts"
	@echo
	@echo "Raw endpoint examples:"
	@echo "  curl -s http://localhost:7050/hubs | head -c 600; echo"
	@echo "  curl -s http://localhost:7050/stats | python3 -m json.tool"
	@echo "  curl -s http://localhost:7051/hubs/H-500"
	@echo "  curl -s http://localhost:7052/delay-stage/H-500"
	@echo "  curl -i -X POST http://localhost:7051/hubs/reload"

# ---------------------------------------------------------------- build -----
# Build executable JARs for all five application services.
build:
	$(MAVEN) -f ingestion-service/pom.xml package -DskipTests
	$(MAVEN) -f hub-service/pom.xml package -DskipTests
	$(MAVEN) -f delay-stage-service/pom.xml package -DskipTests
	$(MAVEN) -f transit-service/pom.xml package -DskipTests
	$(MAVEN) -f alertbot/pom.xml package -DskipTests

# --------------------------------------------------------------- broker -----

# Idempotent — safe to run twice. Wakes the broker and waits a moment so
# the JMS consumers can connect on the first try.
run-broker:
	@docker compose -f common/docker-compose.yml up -d
	@echo "ActiveMQ is starting on tcp://localhost:61616 (web console :8161)."
	@sleep 3
	@docker compose -f common/docker-compose.yml ps

stop-broker:
	-@docker compose -f common/docker-compose.yml down 2>/dev/null || true
	@echo "ActiveMQ stopped."

# ------------------------------------------------------------- services -----

# Each run-* target:
#   1. Verifies the JAR exists (clear error if 'make build' was never run).
#   2. Checks that any hard dependencies are already running.
#   3. Starts the JAR in the foreground, so you see its log lines live.
# Ctrl-C in that terminal stops the service.

run-ingestion:
	@test -f ingestion-service/target/ingestion-service.jar \
	    || { echo "Jar missing. Run 'make build' first."; exit 1; }
	$(JAVA) -jar ingestion-service/target/ingestion-service.jar

run-hub:
	@test -f hub-service/target/hub-service.jar \
	    || { echo "Jar missing. Run 'make build' first."; exit 1; }
	@curl -sf -m 2 http://localhost:7050/health >/dev/null \
	    || { echo "Ingestion is not running. Start it first: make run-ingestion"; exit 1; }
	$(JAVA) -jar hub-service/target/hub-service.jar

run-delay-stage:
	@test -f delay-stage-service/target/delay-stage-service.jar \
	    || { echo "Jar missing. Run 'make build' first."; exit 1; }
	@curl -sf -m 2 http://localhost:7051/health >/dev/null \
	    || { echo "Hub is not running. Start it first: make run-hub"; exit 1; }
	$(JAVA) -jar delay-stage-service/target/delay-stage-service.jar

run-transit:
	@test -f transit-service/target/transit-service.jar \
	    || { echo "Jar missing. Run 'make build' first."; exit 1; }
	@curl -sf -m 2 http://localhost:7051/health >/dev/null \
	    || { echo "Hub is not running. Start it first: make run-hub"; exit 1; }
	$(JAVA) -jar transit-service/target/transit-service.jar

run-alertbot:
	@test -f alertbot/target/alertbot.jar \
	    || { echo "Jar missing. Run 'make build' first."; exit 1; }
	$(JAVA) -jar alertbot/target/alertbot.jar

# --------------------------------------------------------- status / stop ----

# Health-check every port in one shot.
status:
	@echo "Application services:"
	@for entry in \
	    "ingestion:7050" \
	    "hub:7051" \
	    "delay-stage:7052" \
	    "transit:7053" \
	    "alertbot:7054"; do \
	  name=$${entry%%:*}; \
	  port=$${entry##*:}; \
	  if curl -sf -m 1 "http://localhost:$$port/health" >/dev/null 2>&1; then \
	    printf "  %-12s :%-5s UP\n" "$$name" "$$port"; \
	  else \
	    printf "  %-12s :%-5s DOWN\n" "$$name" "$$port"; \
	  fi; \
	done
	@echo
	@echo "ActiveMQ broker:"
	@docker compose -f common/docker-compose.yml ps 2>/dev/null \
	    || echo "  (docker not available or compose file missing)"

# Kill every service and take the broker down. Safe to run when nothing
# is up — pkill and docker compose down are both idempotent.
stop:
ifeq ($(OS),Windows_NT)
	-@powershell -NoProfile -Command "Get-CimInstance Win32_Process | \
	    Where-Object { $$_.CommandLine -match 'ingestion-service.jar|hub-service.jar|delay-stage-service.jar|transit-service.jar|alertbot.jar' } | \
	    ForEach-Object { Stop-Process -Id $$_.ProcessId -Force }" 2>NUL || true
else
	-@pkill -f 'ingestion-service.jar'    2>/dev/null || true
	-@pkill -f 'hub-service.jar'          2>/dev/null || true
	-@pkill -f 'delay-stage-service.jar'  2>/dev/null || true
	-@pkill -f 'transit-service.jar'      2>/dev/null || true
	-@pkill -f 'alertbot.jar'             2>/dev/null || true
endif
	-@docker compose -f common/docker-compose.yml down 2>/dev/null || true
	@echo "All services and broker stopped."

# -------------------------------------------------------------- demo -------

# One-shot setup: start the broker and print the terminal sequence.
# Doesn't start the five services — you run each in its own terminal,
# so 'make stop' can find every one of them via pkill.
demo:
	@$(MAKE) --no-print-directory run-broker
	@echo
	@echo "================================================================"
	@echo "Broker is up. Now open 5 more terminals and run, in order:"
	@echo
	@echo "  Terminal 2: make run-ingestion"
	@echo "  Terminal 3: make run-hub"
	@echo "  Terminal 4: make run-delay-stage"
	@echo "  Terminal 5: make run-transit"
	@echo "  Terminal 6: make run-alertbot"
	@echo
	@echo "Verify:   make status"
	@echo "Shutdown: make stop"
	@echo "================================================================"

# POST a delay stage change. This is the mutating call that fans out over
# ActiveMQ to Transit and AlertBot.
set-stage:
	@test -n "$(STAGE)" || { echo "Usage: make set-stage STAGE=<0-8> [HUB=H-500]"; exit 1; }
	@curl -sf -m 2 http://localhost:7052/health >/dev/null \
	    || { echo "Delay Stage is not running. Start it: make run-delay-stage"; exit 1; }
	@echo "POST http://localhost:7052/delay-stage/$(HUB)  body={\"stage\":$(STAGE)}"
	@curl -s -X POST "http://localhost:7052/delay-stage/$(HUB)" \
	    -H "Content-Type: application/json" \
	    -d '{"stage": $(STAGE)}' \
	    -w "\nHTTP %{http_code}\n"

# GET Transit's ETA for a hub. Re-run after `make set-stage` to see the
# asynchronous update land.
show-transit:
	@curl -sf -m 2 http://localhost:7053/health >/dev/null \
	    || { echo "Transit is not running. Start it: make run-transit"; exit 1; }
	@echo "GET http://localhost:7053/transit/$(HUB)"
	@curl -s "http://localhost:7053/transit/$(HUB)" | python3 -m json.tool

# GET AlertBot's retained simulated alerts.
show-alerts:
	@curl -sf -m 2 http://localhost:7054/health >/dev/null \
	    || { echo "AlertBot is not running. Start it: make run-alertbot"; exit 1; }
	@echo "GET http://localhost:7054/alerts"
	@curl -s "http://localhost:7054/alerts" | python3 -m json.tool

# --------------------------------------------------------------- tests -----

# Unit tests run independently inside each service module.
unit-test:
	$(MAVEN) -f ingestion-service/pom.xml test
	$(MAVEN) -f hub-service/pom.xml test
	$(MAVEN) -f delay-stage-service/pom.xml test
	$(MAVEN) -f transit-service/pom.xml test
	$(MAVEN) -f alertbot/pom.xml test

# Automatically starts ActiveMQ and any services that are not already running.
acceptance-test:
	$(PYTHON) scripts/run-iteration-3-4-tests.py --mode acceptance

# Run the HTTP smoke test using the same automatic service startup.
smoke-test:
	$(PYTHON) scripts/run-iteration-3-4-tests.py --mode smoke

# Run unit tests followed by the live integration acceptance tests.
test: unit-test
	$(PYTHON) scripts/run-iteration-3-4-tests.py --mode acceptance

# Clean the independent Maven projects.
clean:
	$(MAVEN) -f ingestion-service/pom.xml clean
	$(MAVEN) -f hub-service/pom.xml clean
	$(MAVEN) -f delay-stage-service/pom.xml clean
	$(MAVEN) -f transit-service/pom.xml clean
	$(MAVEN) -f alertbot/pom.xml clean
	$(MAVEN) -f acceptance-tests/pom.xml clean
