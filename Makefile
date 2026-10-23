# Commands can be overridden, for example:
# make PYTHON=python3 acceptance-test

ifeq ($(OS),Windows_NT)
PYTHON ?= python
else
PYTHON ?= python3
endif

MAVEN ?= mvn

.PHONY: help build unit-test acceptance-test smoke-test test clean

help:
	@echo "LogisticsConnect development commands:"
	@echo "  make build            Build the four REST services"
	@echo "  make unit-test        Run unit tests for each REST service"
	@echo "  make acceptance-test  Start services and run JUnit acceptance tests"
	@echo "  make smoke-test       Start services and run the Python smoke test"
	@echo "  make test             Run unit tests and acceptance tests"
	@echo "  make clean            Clean Maven build outputs"

# Build executable JARs for the four services.
build:
	$(MAVEN) -f ingestion-service/pom.xml package -DskipTests
	$(MAVEN) -f hub-service/pom.xml package -DskipTests
	$(MAVEN) -f delay-stage-service/pom.xml package -DskipTests
	$(MAVEN) -f transit-service/pom.xml package -DskipTests

# Unit tests run independently inside each service module.
unit-test:
	$(MAVEN) -f ingestion-service/pom.xml test
	$(MAVEN) -f hub-service/pom.xml test
	$(MAVEN) -f delay-stage-service/pom.xml test
	$(MAVEN) -f transit-service/pom.xml test

# The Python runner builds and starts any services that are not already running.
acceptance-test:
	$(PYTHON) scripts/run-iteration-2-tests.py --mode acceptance

# Run the HTTP smoke test using the same automatic service startup.
smoke-test:
	$(PYTHON) scripts/run-iteration-2-tests.py --mode smoke

# Run unit tests first, followed by the live end-to-end acceptance tests.
test: unit-test
	$(PYTHON) scripts/run-iteration-2-tests.py --mode acceptance

# Clean the independent Maven projects.
clean:
	$(MAVEN) -f ingestion-service/pom.xml clean
	$(MAVEN) -f hub-service/pom.xml clean
	$(MAVEN) -f delay-stage-service/pom.xml clean
	$(MAVEN) -f transit-service/pom.xml clean
	$(MAVEN) -f acceptance-tests/pom.xml clean
