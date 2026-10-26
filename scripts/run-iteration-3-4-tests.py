"""
Build and run LogisticsConnect Iteration 3 and 4 tests.

Examples:
    python scripts/run-iteration-3-4-tests.py --mode acceptance
    python scripts/run-iteration-3-4-tests.py --mode smoke

Pass --skip-build when the modules have already been built.
Only processes started by this script are stopped afterwards.
"""

import argparse
import json
import os
import socket
import subprocess
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import urlopen


ROOT = Path(__file__).resolve().parents[1]
LOG_DIR = ROOT / "target" / "iteration-3-4-logs"
COMPOSE_FILE = "common/docker-compose.yml"

MAVEN = os.getenv("MAVEN_COMMAND", "mvn")
JAVA = os.getenv("JAVA_COMMAND", "java")

STARTUP_TIMEOUT = int(
    os.getenv("SERVICE_STARTUP_TIMEOUT_SECONDS", "45")
)

INGESTION_URL = "http://localhost:7050"
HUB_URL = "http://localhost:7051"
DELAY_URL = "http://localhost:7052"
TRANSIT_URL = "http://localhost:7053"
ALERTBOT_URL = "http://localhost:7054"


# Start in dependency order. Hub loads data from Ingestion during startup.
SERVICES = [
    {
        "name": "Ingestion",
        "pom": "ingestion-service/pom.xml",
        "jar": "ingestion-service/target/ingestion-service.jar",
        "url": INGESTION_URL,
        "environment": {},
        "consumer_health_field": None,
    },
    {
        "name": "Hub",
        "pom": "hub-service/pom.xml",
        "jar": "hub-service/target/hub-service.jar",
        "url": HUB_URL,
        "environment": {
            "INGESTION_SERVICE_URL": INGESTION_URL,
        },
        "consumer_health_field": None,
    },
    {
        "name": "Delay Stage",
        "pom": "delay-stage-service/pom.xml",
        "jar": "delay-stage-service/target/delay-stage-service.jar",
        "url": DELAY_URL,
        "environment": {
            "HUB_SERVICE_URL": HUB_URL,
        },
        "consumer_health_field": None,
    },
    {
        "name": "Transit",
        "pom": "transit-service/pom.xml",
        "jar": "transit-service/target/transit-service.jar",
        "url": TRANSIT_URL,
        "environment": {
            "HUB_SERVICE_URL": HUB_URL,
        },
        "consumer_health_field": "messageConsumerConnected",
    },
    {
        "name": "AlertBot",
        "pom": "alertbot/pom.xml",
        "jar": "alertbot/target/alertbot.jar",
        "url": ALERTBOT_URL,
        "environment": {
            "ALERT_STAGE_THRESHOLD": os.getenv(
                "ALERT_STAGE_THRESHOLD", "5"
            ),
        },
        "consumer_health_field": "consumerConnected",
    },
]


def run_command(command):
    """Run a command in the repository root and fail on errors."""
    print("\n$", " ".join(str(part) for part in command), flush=True)

    subprocess.run(
        command,
        cwd=ROOT,
        check=True,
    )


def build_services():
    """Build all five independent runnable service JARs."""
    print("\nBuilding the five service modules...", flush=True)

    for service in SERVICES:
        run_command([
            MAVEN,
            "-f",
            service["pom"],
            "package",
            "-DskipTests",
        ])

    print("\nAll five service JARs have been built.", flush=True)


def port_is_open(host, port):
    """Check whether a TCP service is listening."""
    try:
        with socket.create_connection((host, port), timeout=0.5):
            return True
    except OSError:
        return False


def start_broker_if_needed():
    """
    Start ActiveMQ only when the local OpenWire port is not already open.
    Return True only when this script launched the compose service.
    """
    if port_is_open("127.0.0.1", 61616):
        print("ActiveMQ is already listening; reusing it.", flush=True)
        return False

    run_command([
        "docker",
        "compose",
        "-f",
        COMPOSE_FILE,
        "up",
        "-d",
    ])

    deadline = time.monotonic() + 45

    while time.monotonic() < deadline:
        if port_is_open("127.0.0.1", 61616):
            print("ActiveMQ OpenWire port is ready.", flush=True)
            return True

        time.sleep(0.5)

    raise RuntimeError(
        "ActiveMQ did not become ready on localhost:61616. "
        "Check Docker and common/docker-compose.yml."
    )


def request_json(url):
    """Read a JSON HTTP response; return None when the service is not ready."""
    try:
        with urlopen(url, timeout=1) as response:
            text = response.read().decode("utf-8")
            return json.loads(text) if text.strip() else None
    except (OSError, URLError, HTTPError, TimeoutError, ValueError):
        return None


def is_healthy(base_url):
    """Return True when the health endpoint returns HTTP 2xx."""
    try:
        with urlopen(f"{base_url}/health", timeout=1) as response:
            return 200 <= response.status < 300
    except (OSError, URLError, HTTPError, TimeoutError):
        return False


def read_log_tail(log_path, max_lines=40):
    """Read the final lines of a service log for troubleshooting."""
    try:
        lines = log_path.read_text(
            encoding="utf-8",
            errors="replace",
        ).splitlines()

        return "\n".join(lines[-max_lines:])

    except OSError:
        return "The service log could not be read."


def wait_until_healthy(service, process, log_path):
    """Wait for a service to become healthy or report its startup failure."""
    deadline = time.monotonic() + STARTUP_TIMEOUT

    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(
                f"{service['name']} exited during startup.\n"
                f"{read_log_tail(log_path)}"
            )

        if is_healthy(service["url"]):
            print(
                f"{service['name']} is healthy at {service['url']}",
                flush=True,
            )
            return

        time.sleep(0.5)

    raise RuntimeError(
        f"{service['name']} did not become healthy.\n"
        f"{read_log_tail(log_path)}"
    )


def start_services(started):
    """Start or reuse the five HTTP services."""
    LOG_DIR.mkdir(parents=True, exist_ok=True)

    for service in SERVICES:
        if is_healthy(service["url"]):
            print(
                f"{service['name']} is already healthy; reusing it.",
                flush=True,
            )
            continue

        jar_path = ROOT / service["jar"]

        if not jar_path.is_file():
            raise RuntimeError(
                f"Missing JAR: {jar_path}. Build the project first."
            )

        log_path = LOG_DIR / (
                service["name"].lower().replace(" ", "-") + ".log"
        )

        environment = os.environ.copy()
        environment.update(service["environment"])

        log_file = log_path.open("w", encoding="utf-8")

        try:
            process = subprocess.Popen(
                [JAVA, "-jar", str(jar_path)],
                cwd=ROOT,
                env=environment,
                stdout=log_file,
                stderr=subprocess.STDOUT,
            )
        except Exception:
            log_file.close()
            raise

        started.append({
            "name": service["name"],
            "process": process,
            "log_file": log_file,
            "log_path": log_path,
        })

        print(
            f"Starting {service['name']}; "
            f"log: {log_path.relative_to(ROOT)}",
            flush=True,
        )

        wait_until_healthy(service, process, log_path)


def wait_for_message_consumers():
    """Wait for Transit and AlertBot to establish their JMS subscriptions."""
    deadline = time.monotonic() + STARTUP_TIMEOUT

    while time.monotonic() < deadline:
        all_connected = True

        for service in SERVICES:
            field = service["consumer_health_field"]

            if field is None:
                continue

            health = request_json(f"{service['url']}/health")

            if not isinstance(health, dict) or not health.get(field):
                all_connected = False
                break

        if all_connected:
            print(
                "Transit and AlertBot are connected to ActiveMQ.",
                flush=True,
            )
            return

        time.sleep(0.5)

    raise RuntimeError(
        "Transit or AlertBot did not connect to ActiveMQ. "
        "Check their startup logs and durable subscription settings."
    )


def stop_started_services(started):
    """Stop only Java processes created by this runner."""
    for item in reversed(started):
        process = item["process"]

        if process.poll() is not None:
            item["log_file"].close()
            continue

        print(f"Stopping {item['name']}...", flush=True)
        process.terminate()

        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
        finally:
            item["log_file"].close()


def save_broker_logs():
    """Preserve ActiveMQ logs before a broker container is stopped."""
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    log_path = LOG_DIR / "activemq.log"

    try:
        with log_path.open("w", encoding="utf-8") as output:
            subprocess.run(
                [
                    "docker",
                    "compose",
                    "-f",
                    COMPOSE_FILE,
                    "logs",
                    "--no-color",
                    "activemq",
                ],
                cwd=ROOT,
                stdout=output,
                stderr=subprocess.STDOUT,
                check=False,
            )
    except OSError as exception:
        print(
            f"WARNING: Could not save ActiveMQ logs: {exception}",
            file=sys.stderr,
        )


def main():
    parser = argparse.ArgumentParser(
        description="Run the LogisticsConnect Iteration 3 and 4 tests."
    )

    parser.add_argument(
        "--mode",
        choices=("acceptance", "smoke"),
        default="acceptance",
        help="Run JUnit acceptance tests or the full Python smoke test.",
    )

    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Use existing JARs instead of packaging the modules again.",
    )

    args = parser.parse_args()

    started = []
    broker_started = False
    exit_code = 0

    try:
        if not args.skip_build:
            build_services()

        broker_started = start_broker_if_needed()
        start_services(started)
        wait_for_message_consumers()

        if args.mode == "acceptance":
            run_command([
                MAVEN,
                "-f",
                "acceptance-tests/pom.xml",
                "-DfailIfNoTests=true",
                "test",
            ])
        else:
            run_command([
                sys.executable,
                "scripts/iteration-3-4-smoke-test.py",
            ])

        print(
            "\nPASS: Iteration 3 and 4 test run completed.",
            flush=True,
        )

    except subprocess.CalledProcessError as error:
        print(
            f"\nFAIL: Command exited with status {error.returncode}.",
            file=sys.stderr,
        )
        exit_code = error.returncode or 1

    except Exception as error:
        print(f"\nFAIL: {error}", file=sys.stderr)
        exit_code = 1

    finally:
        stop_started_services(started)

        if broker_started:
            # Preserve broker diagnostics before removing its container.
            save_broker_logs()

            subprocess.run(
                [
                    "docker",
                    "compose",
                    "-f",
                    COMPOSE_FILE,
                    "down",
                ],
                cwd=ROOT,
                check=False,
            )

    return exit_code


if __name__ == "__main__":
    sys.exit(main())