"""
LogisticsConnect Iteration 3 and 4 smoke test.

Verifies the real HTTP -> ActiveMQ -> Transit and AlertBot flow.
Uses Python's standard library only.
"""

import json
import os
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


INGESTION_URL = os.getenv(
    "INGESTION_SERVICE_URL", "http://localhost:7050"
).rstrip("/")

HUB_URL = os.getenv(
    "HUB_SERVICE_URL", "http://localhost:7051"
).rstrip("/")

DELAY_URL = os.getenv(
    "DELAY_STAGE_SERVICE_URL", "http://localhost:7052"
).rstrip("/")

TRANSIT_URL = os.getenv(
    "TRANSIT_SERVICE_URL", "http://localhost:7053"
).rstrip("/")

ALERTBOT_URL = os.getenv(
    "ALERTBOT_SERVICE_URL", "http://localhost:7054"
).rstrip("/")

HUB_ID = "H-500"
TIMEOUT_SECONDS = 15


class SmokeTestError(RuntimeError):
    """Raised when a live end-to-end check fails."""


def request_json(url, method="GET", payload=None):
    """Send an HTTP request and return its decoded JSON response."""
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

    try:
        with urlopen(request, timeout=5) as response:
            text = response.read().decode("utf-8")
            return json.loads(text) if text.strip() else None

    except HTTPError as error:
        response_body = error.read().decode(
            "utf-8", errors="replace"
        )

        raise SmokeTestError(
            f"{method} {url} returned HTTP {error.code}: "
            f"{response_body}"
        ) from error

    except (URLError, OSError, TimeoutError) as error:
        raise SmokeTestError(
            f"Could not reach {url}: {error}"
        ) from error


def wait_for_transit_stage(expected_stage):
    """Wait for the asynchronous event to update Transit."""
    deadline = time.monotonic() + TIMEOUT_SECONDS
    last_response = None

    while time.monotonic() < deadline:
        last_response = request_json(
            f"{TRANSIT_URL}/transit/{HUB_ID}"
        )

        if last_response.get("delayStage") == expected_stage:
            return last_response

        time.sleep(0.2)

    raise SmokeTestError(
        f"Transit did not reach stage {expected_stage}. "
        f"Last response: {last_response}"
    )


def wait_for_alert(expected_stage, expected_timestamp):
    """Wait until AlertBot exposes the alert for the target event."""
    deadline = time.monotonic() + TIMEOUT_SECONDS

    while time.monotonic() < deadline:
        alerts = request_json(f"{ALERTBOT_URL}/alerts")

        for alert in alerts:
            if (
                    alert.get("hubId") == HUB_ID
                    and alert.get("stage") == expected_stage
                    and alert.get("timestamp") == expected_timestamp
            ):
                return alert

        time.sleep(0.2)

    raise SmokeTestError(
        "AlertBot did not expose the expected simulated alert "
        f"for event timestamp {expected_timestamp}."
    )


def run_smoke_test():
    """Verify both consumers react to an actual delay-stage change."""
    services = [
        ("Ingestion", INGESTION_URL),
        ("Hub", HUB_URL),
        ("Delay Stage", DELAY_URL),
        ("Transit", TRANSIT_URL),
        ("AlertBot", ALERTBOT_URL),
    ]

    print("1. Checking all five service health endpoints...")

    for name, base_url in services:
        health = request_json(f"{base_url}/health")

        if not isinstance(health, dict):
            raise SmokeTestError(
                f"{name} did not return a JSON health response."
            )

        print(f"   PASS: {name}")

    # Confirm the configured sample hub exists before mutating its state.
    request_json(f"{HUB_URL}/hubs/{HUB_ID}")

    alert_health = request_json(f"{ALERTBOT_URL}/health")
    threshold = alert_health.get("threshold", 5)

    if not isinstance(threshold, int) or not 0 <= threshold <= 8:
        raise SmokeTestError(
            f"Invalid AlertBot threshold: {threshold}"
        )

    original = request_json(
        f"{DELAY_URL}/delay-stage/{HUB_ID}"
    )

    previous_stage = original.get("stage")

    if not isinstance(previous_stage, int) or not 0 <= previous_stage <= 8:
        raise SmokeTestError(
            f"Unexpected delay-stage response: {original}"
        )

    # Select a valid intermediate value that differs from both the original
    # stage and the alert threshold. This guarantees two real state changes.
    candidates = [
        stage for stage in range(9)
        if stage != previous_stage and stage != threshold
    ]

    if not candidates:
        raise SmokeTestError(
            "Could not choose an intermediate stage for the test."
        )

    intermediate_stage = candidates[0]

    try:
        print("2. Publishing an ordinary delay-stage change...")

        request_json(
            f"{DELAY_URL}/delay-stage/{HUB_ID}",
            method="POST",
            payload={"stage": intermediate_stage},
        )

        ordinary_eta = wait_for_transit_stage(intermediate_stage)

        expected_eta = 120 + (intermediate_stage * 15)

        if ordinary_eta.get("etaMinutes") != expected_eta:
            raise SmokeTestError(
                f"Expected ETA {expected_eta}, got "
                f"{ordinary_eta.get('etaMinutes')}."
            )

        print(
            "   PASS: Transit applied the event and calculated ETA "
            f"{expected_eta} minutes."
        )

        print(
            f"3. Publishing the configured alert threshold "
            f"(stage {threshold})..."
        )

        update = request_json(
            f"{DELAY_URL}/delay-stage/{HUB_ID}",
            method="POST",
            payload={"stage": threshold},
        )

        event_timestamp = update.get("eventTimestamp")

        if not event_timestamp:
            raise SmokeTestError(
                "The changed stage did not return eventTimestamp."
            )

        threshold_eta = wait_for_transit_stage(threshold)
        alert = wait_for_alert(threshold, event_timestamp)

        if threshold_eta.get("etaMinutes") != 120 + threshold * 15:
            raise SmokeTestError(
                "Transit returned an unexpected ETA at the threshold."
            )

        if alert.get("simulated") is not True:
            raise SmokeTestError(
                "AlertBot did not mark the notification as simulated."
            )

        print("   PASS: Transit received the threshold event.")
        print("   PASS: AlertBot generated a simulated alert.")
        print()
        print("PASS: Iterations 3 and 4 event flow works.")
        print(f"Hub: {HUB_ID}")
        print(f"Threshold: {threshold}")
        print(
            f"ETA at threshold: "
            f"{threshold_eta['etaMinutes']} minutes"
        )
        print(f"Alert event timestamp: {event_timestamp}")

    finally:
        # Keep the shared local state as it was before the test.
        try:
            request_json(
                f"{DELAY_URL}/delay-stage/{HUB_ID}",
                method="POST",
                payload={"stage": previous_stage},
            )
        except Exception as error:
            print(
                f"WARNING: Could not restore stage "
                f"{previous_stage}: {error}",
                file=sys.stderr,
            )


if __name__ == "__main__":
    try:
        run_smoke_test()
    except SmokeTestError as error:
        print(f"\nFAIL: {error}", file=sys.stderr)
        sys.exit(1)
    except Exception as error:
        print(f"\nFAIL: Unexpected error: {error}", file=sys.stderr)
        sys.exit(1)