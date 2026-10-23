
"""
LogisticsConnect Iteration 2 REST smoke test.

Checks the live REST flow through Ingestion, Hub, Delay Stage and Transit.
Uses only Python's standard library, so no pip installation is required.
"""

import json
import os
import sys
from datetime import datetime
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


# Read service URLs from environment variables or use local defaults.
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

# H-500 is the sample hub used by this smoke test.
HUB_ID = "H-500"


class SmokeTestError(RuntimeError):
    """Raised when a smoke-test check fails."""


def parse_response(body):
    """Parse JSON when possible; otherwise return the response as text."""
    if not body:
        return None

    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return body


def send_request(url, method="GET", payload=None):
    """
    Send an HTTP request and return its status code and response body.

    HTTP errors are returned as responses so the caller can inspect
    their status codes. Network errors are reported clearly.
    """
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
        # The timeout prevents a request from hanging indefinitely.
        with urlopen(request, timeout=5) as response:
            text = response.read().decode("utf-8")
            return response.status, parse_response(text)

    except HTTPError as error:
        # HTTPError includes useful responses such as 400 and 404.
        text = error.read().decode("utf-8", errors="replace")
        return error.code, parse_response(text)

    except (URLError, TimeoutError, OSError) as error:
        raise SmokeTestError(
            f"Could not reach {url}: {error}"
        ) from error


def require(condition, message):
    """Stop the smoke test when a required condition is not met."""
    if not condition:
        raise SmokeTestError(message)


def get_successful_json(url):
    """GET an endpoint and require a successful JSON response."""
    status, response = send_request(url)

    require(
        200 <= status < 300,
        f"GET {url} returned HTTP {status}: {response}",
        )

    require(
        isinstance(response, (dict, list)),
        f"GET {url} did not return a JSON object or array.",
    )

    return response


def check_health(base_url, service_name):
    """Check that a service's health endpoint responds successfully."""
    status, response = send_request(f"{base_url}/health")

    require(
        status == 200,
        f"{service_name} health check returned HTTP {status}.",
        )

    # Ingestion may return plain-text OK; the other services may return
    # a JSON object containing status: UP.
    if isinstance(response, dict) and "status" in response:
        require(
            response["status"] == "UP",
            f"{service_name} is not UP: {response}",
            )
    else:
        require(
            isinstance(response, str) and response.strip() == "OK",
            f"{service_name} returned an unexpected health response: "
            f"{response}",
            )


def update_stage(hub_id, stage):
    """POST a new delay stage and require the update to succeed."""
    status, response = send_request(
        f"{DELAY_URL}/delay-stage/{hub_id}",
        method="POST",
        payload={"stage": stage},
    )

    require(
        200 <= status < 300,
        f"Could not set stage {stage} for {hub_id}. "
        f"HTTP {status}: {response}",
        )

    require(
        isinstance(response, dict)
        and response.get("stage") == stage,
        f"Delay Stage returned an unexpected update response: {response}",
        )

    return response


def run_smoke_test():
    """Verify the main Iteration 2 REST integration scenario."""

    print("1. Checking Ingestion...")
    check_health(INGESTION_URL, "Ingestion")

    print("2. Checking Hub Service...")
    check_health(HUB_URL, "Hub Service")

    # Confirm that Hub Service has data to serve.
    hubs = get_successful_json(f"{HUB_URL}/hubs")

    require(
        isinstance(hubs, list) and len(hubs) > 0,
        "Hub Service returned no hubs.",
        )

    # A lowercase lookup should return the canonical ID.
    hub = get_successful_json(f"{HUB_URL}/hubs/h-500")

    require(
        hub.get("hubId") == HUB_ID,
        "Case-insensitive hub lookup failed.",
        )

    print("3. Checking Delay Stage Service...")
    check_health(DELAY_URL, "Delay Stage Service")

    # Check the initial state before changing anything.
    initial = get_successful_json(
        f"{DELAY_URL}/delay-stage/{HUB_ID}"
    )

    previous_stage = initial.get("stage")

    require(
        isinstance(previous_stage, int) and 0 <= previous_stage <= 8,
        f"Invalid initial delay-stage response: {initial}",
        )

    # For this scenario, a fresh service should start at stage 0.
    require(
        previous_stage == 0,
        f"Expected initial stage 0, received {previous_stage}. "
        "Restart Delay Stage Service to reset its in-memory state.",
        )

    try:
        # Set stage 3 and confirm the service accepts it.
        updated = update_stage(HUB_ID, 3)

        require(
            updated["stage"] == 3,
            "Delay stage update failed.",
            )

        # Verify that the change was stored, not just returned.
        stored = get_successful_json(
            f"{DELAY_URL}/delay-stage/{HUB_ID}"
        )

        require(
            stored.get("stage") == 3,
            f"Expected stored stage 3, received {stored.get('stage')}.",
            )

        print("4. Checking Transit Service...")
        check_health(TRANSIT_URL, "Transit Service")

        # Transit must retrieve the hub and current delay stage.
        eta = get_successful_json(
            f"{TRANSIT_URL}/transit/{HUB_ID}"
        )

        require(
            eta.get("hubId") == HUB_ID,
            f"Unexpected hub in Transit response: {eta}",
            )

        require(
            eta.get("delayStage") == 3,
            f"Expected delay stage 3, received {eta.get('delayStage')}.",
            )

        # Default settings: 120 base minutes + (3 stages x 15 minutes).
        require(
            eta.get("etaMinutes") == 165,
            f"Expected ETA 165 minutes, received {eta.get('etaMinutes')}.",
            )

        require(
            eta.get("etaDuration") == "PT2H45M",
            f"Expected PT2H45M, received {eta.get('etaDuration')}.",
            )

        # Ensure etaAt exists and contains a valid ISO-8601 timestamp.
        eta_at = eta.get("etaAt")

        require(
            isinstance(eta_at, str) and eta_at.strip(),
            "Transit response did not include etaAt.",
            )

        try:
            parsed_time = datetime.fromisoformat(
                eta_at.replace("Z", "+00:00")
            )
        except ValueError as error:
            raise SmokeTestError(
                f"Invalid etaAt timestamp: {eta_at}"
            ) from error

        require(
            parsed_time.tzinfo is not None,
            "etaAt must include a timezone.",
            )

        print()
        print("PASS: Iteration 2 REST flow works.")
        print(f"Hub: {eta['hubId']}")
        print(f"Stage: {eta['delayStage']}")
        print(f"ETA: {eta['etaMinutes']} minutes ({eta['etaDuration']})")
        print(f"ETA timestamp: {eta_at}")

    finally:
        # Restore the original stage, including when an assertion fails.
        # This keeps the test from leaving stage 3 behind.
        try:
            update_stage(HUB_ID, previous_stage)
        except Exception as error:
            print(
                f"WARNING: Could not restore the original delay stage: "
                f"{error}",
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
