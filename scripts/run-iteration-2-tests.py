"""
Run LogisticsConnect Iteration 2 tests without four manual terminals.

Examples:
    python scripts/run-iteration-2-tests.py --mode acceptance
    python scripts/run-iteration-2-tests.py --mode smoke

Only services started by this script are stopped when testing finishes.
Services that were already running are left alone.
"""

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


# Find the repository root, regardless of the current terminal directory.
ROOT = Path(__file__).resolve().parents[1]

# Keep startup logs out of source folders and away from Git commits.
LOG_DIR = ROOT / "target" / "iteration-2-logs"

MAVEN = os.getenv("MAVEN_COMMAND", "mvn")
JAVA = os.getenv("JAVA_COMMAND", "java")

STARTUP_TIMEOUT = int(
    os.getenv("SERVICE_STARTUP_TIMEOUT_SECONDS", "45")
)

HUB_ID = "H-500"

INGESTION_URL = "http://localhost:7050"
HUB_URL = "http://localhost:7051"
DELAY_URL = "http://localhost:7052"
TRANSIT_URL = "http://localhost:7053"


# Start order matters: Hub loads data from Ingestion during startup.
SERVICES = [
    {
        "name": "Ingestion",
        "pom": "ingestion-service/pom.xml",
        "jar": "ingestion-service/target/ingestion-service.jar",
        "url": INGESTION_URL,
        "environment": {},
    },
    {
        "name": "Hub",
        "pom": "hub-service/pom.xml",
        "jar": "hub-service/target/hub-service.jar",
        "url": HUB_URL,
        "environment": {
            "INGESTION_SERVICE_URL": INGESTION_URL,
        },
    },
    {
        "name": "Delay Stage",
        "pom": "delay-stage-service/pom.xml",
        "jar": "delay-stage-service/target/delay-stage-service.jar",
        "url": DELAY_URL,
        "environment": {
            "HUB_SERVICE_URL": HUB_URL,
        },
    },
    {
        "name": "Transit",
        "pom": "transit-service/pom.xml",
        "jar": "transit-service/target/transit-service.jar",
        "url": TRANSIT_URL,
        "environment": {
            "HUB_SERVICE_URL": HUB_URL,
            "DELAY_STAGE_SERVICE_URL": DELAY_URL,
        },
    },
]


def run_command(command):
    """Run a command from the repository root and stop on failure."""
    print("\n$", " ".join(str(part) for part in command), flush=True)

    subprocess.run(
        command,
        cwd=ROOT,
        check=True,
    )


def build_services():
    """Package each independent Maven service into a runnable JAR."""
    print("\nBuilding the four REST services...", flush=True)

    for service in SERVICES:
        run_command([
            MAVEN,
            "-f",
            service["pom"],
            "package",
            "-DskipTests",
        ])

    print("\nAll four service JARs have been built.", flush=True)


def is_healthy(base_url):
    """Return True if the service health endpoint returns HTTP 2xx."""
    try:
        with urlopen(
                f"{base_url}/health",
                timeout=1,
        ) as response:
            return 200 <= response.status < 300

    except (OSError, URLError, HTTPError, TimeoutError):
        # A connection failure means the service is not ready yet.
        return False


def read_log_tail(log_path, max_lines=30):
    """Return the final log lines to explain why startup failed."""
    try:
        lines = log_path.read_text(
            encoding="utf-8",
            errors="replace",
        ).splitlines()

        return "\n".join(lines[-max_lines:])

    except OSError:
        return "The service log could not be read."


def wait_until_healthy(service, process, log_path):
    """Wait for a newly started service or fail with its log output."""
    deadline = time.monotonic() + STARTUP_TIMEOUT
    health_url = f"{service['url']}/health"

    while time.monotonic() < deadline:

        # Report a startup failure immediately if Java has exited.
        if process.poll() is not None:
            raise RuntimeError(
                f"{service['name']} exited during startup.\n"
                f"Log file: {log_path}\n"
                f"{read_log_tail(log_path)}"
            )

        if is_healthy(service["url"]):
            print(
                f"{service['name']} is ready at {health_url}",
                flush=True,
            )
            return

        time.sleep(0.5)

    raise RuntimeError(
        f"{service['name']} did not become healthy within "
        f"{STARTUP_TIMEOUT} seconds.\n"
        f"Log file: {log_path}\n"
        f"{read_log_tail(log_path)}"
    )


def start_services(started):
    """
    Start services in dependency order.

    The 'started' list contains only processes owned by this script,
    so existing developer processes are never terminated by cleanup.
    """
    LOG_DIR.mkdir(parents=True, exist_ok=True)

    for service in SERVICES:

        # Reuse a healthy service instead of starting a duplicate.
        if is_healthy(service["url"]):
            print(
                f"{service['name']} is already healthy; reusing it.",
                flush=True,
            )
            continue

        jar_path = ROOT / service["jar"]

        if not jar_path.is_file():
            raise RuntimeError(
                f"Missing service JAR: {jar_path}\n"
                "Check that the Maven package step completed successfully."
            )

        safe_name = service["name"].lower().replace(" ", "-")
        log_path = LOG_DIR / f"{safe_name}.log"

        # Pass the downstream REST URLs to the service being started.
        # Existing environment variables are retained for other settings.
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

        # Register it immediately so cleanup also works after a startup error.
        started.append({
            "name": service["name"],
            "process": process,
            "log_file": log_file,
            "log_path": log_path,
        })

        print(
            f"Starting {service['name']}... "
            f"Log: {log_path.relative_to(ROOT)}",
            flush=True,
        )

        wait_until_healthy(service, process, log_path)


def request_json(url, method="GET", payload=None):
    """Send one JSON HTTP request and return the decoded response."""
    body = None
    headers = {}

    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = Request(
        url,
        data=body,
        headers=headers,
        method=method,
    )

    with urlopen(request, timeout=5) as response:
        text = response.read().decode("utf-8")

        if not text.strip():
            return None

        return json.loads(text)


def run_smoke_test_with_restored_state():
    """
    Ensure the smoke test sees stage 0, then restore the previous stage.

    This matters when reusing a Delay Stage service that already has state.
    """
    stage_url = f"{DELAY_URL}/delay-stage/{HUB_ID}"

    original = request_json(stage_url)
    original_stage = original.get("stage")

    if not isinstance(original_stage, int) or not 0 <= original_stage <= 8:
        raise RuntimeError(
            f"Unexpected delay-stage response before smoke test: {original}"
        )

    try:
        # The existing smoke script checks that the initial stage is zero.
        request_json(
            stage_url,
            method="POST",
            payload={"stage": 0},
        )

        run_command([
            sys.executable,
            "scripts/iteration-2-smoke-test.py",
        ])

    finally:
        # Return the reused service to its original state.
        try:
            request_json(
                stage_url,
                method="POST",
                payload={"stage": original_stage},
            )
        except Exception as error:
            print(
                f"WARNING: Could not restore the original stage "
                f"({original_stage}): {error}",
                file=sys.stderr,
                flush=True,
            )


def stop_started_services(started):
    """Stop our services in reverse dependency order."""
    for item in reversed(started):
        process = item["process"]

        # Do not try to terminate a process that has already exited.
        if process.poll() is not None:
            item["log_file"].close()
            continue

        print(f"Stopping {item['name']}...", flush=True)
        process.terminate()

        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            # Force termination only if a normal shutdown does not work.
            process.kill()
            process.wait(timeout=5)
        finally:
            item["log_file"].close()


def main():
    parser = argparse.ArgumentParser(
        description="Build services and run Iteration 2 tests."
    )

    parser.add_argument(
        "--mode",
        choices=("acceptance", "smoke"),
        default="acceptance",
        help="Choose JUnit acceptance tests or the Python smoke test.",
    )

    args = parser.parse_args()
    started = []

    try:
        # Build fresh JARs before starting any services.
        build_services()

        try:
            # Start or reuse each service and wait for it to become healthy.
            start_services(started)

            if args.mode == "acceptance":
                # Require Surefire to discover at least one test.
                run_command([
                    MAVEN,
                    "-f",
                    "acceptance-tests/pom.xml",
                    "-DfailIfNoTests=true",
                    "test",
                ])

            else:
                run_smoke_test_with_restored_state()

            print("\nPASS: Iteration 2 test run completed.", flush=True)

        finally:
            # Runs on test failure as well as test success.
            stop_started_services(started)

    except subprocess.CalledProcessError as error:
        print(
            f"\nFAIL: Command exited with status {error.returncode}.",
            file=sys.stderr,
        )
        return error.returncode or 1

    except Exception as error:
        print(f"\nFAIL: {error}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
