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
	@echo "  make build            Build all five services"
	@echo "  make unit-test        Run unit tests in every service"
	@echo "  make acceptance-test  Run JUnit integration tests"
	@echo "  make smoke-test       Run the live end-to-end event flow"
	@echo "  make test             Run unit tests and acceptance tests"
	@echo "  make clean            Clean the independent Maven projects"

# Build executable JARs for all five application services.
build:
	$(MAVEN) -f ingestion-service/pom.xml package -DskipTests
	$(MAVEN) -f hub-service/pom.xml package -DskipTests
	$(MAVEN) -f delay-stage-service/pom.xml package -DskipTests
	$(MAVEN) -f transit-service/pom.xml package -DskipTests
	$(MAVEN) -f alertbot/pom.xml package -DskipTests

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
