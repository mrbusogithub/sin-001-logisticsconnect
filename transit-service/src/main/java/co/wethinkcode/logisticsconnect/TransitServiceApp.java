package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Calculates an estimated arrival time for a hub.
 *
 * For each request, Transit gets hub details from the Hub Service and the
 * current delay stage from the Delay Stage Service.
 */

public class TransitServiceApp {

    private static final ObjectMapper JSON = new ObjectMapper();

    // Default addresses of the services Transit depends on.
    private static final String DEFAULT_HUB_URL =
            "http://localhost:7051";
    private static final String DEFAULT_DELAY_URL =
            "http://localhost:7052";

    // Reuse this client for outgoing HTTP requests.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void main(String[] args) {
        // These values can be changed through environment variables.
        String hubServiceUrl = System.getenv().getOrDefault(
                "HUB_SERVICE_URL", DEFAULT_HUB_URL);
        String delayServiceUrl = System.getenv().getOrDefault(
                "DELAY_STAGE_SERVICE_URL", DEFAULT_DELAY_URL);

        // Default ETA is 120 minutes, plus 15 minutes for each delay stage.
        int baseDurationMinutes = readNonNegativeSetting(
                "TRANSIT_BASE_DURATION_MINUTES", 120);
        int minutesPerStage = readNonNegativeSetting(
                "TRANSIT_MINUTES_PER_STAGE", 15);

        int port = Integer.parseInt(
                System.getenv().getOrDefault("TRANSIT_SERVICE_PORT", "7053"));

        Javalin app = Javalin.create().start(port);

        // Show the current ETA configuration as part of the health response.
        app.get("/health", ctx -> ctx.json(Map.of(
                "status", "UP",
                "service", "transit-service",
                "baseDurationMinutes", baseDurationMinutes,
                "minutesPerStage", minutesPerStage
        )));

        // TODO (Calculates estimated arrival windows based on hub and delay stage.)
        // Add domain endpoints for transit-service here.

        // Calculate an ETA for the hub ID supplied in the URL.
        app.get("/transit/{hubId}", ctx -> {
            String hubId = normalizeHubId(ctx.pathParam("hubId"));

            if (hubId.isBlank()) {
                error(ctx, 400, "INVALID_HUB_ID",
                        "Hub ID must not be blank.");
                return;
            }

            try {
                // Step 1: confirm the hub exists and retrieve its details.
                JsonNode hub = getHub(hubServiceUrl, hubId);

                // Step 2: get the current delay stage from the Delay Stage Service.
                int stage = getStage(delayServiceUrl, hubId);

                // Step 3: calculate the ETA using the configured values.
                int etaMinutes = calculateEtaMinutes(
                        baseDurationMinutes, minutesPerStage, stage);

                // Step 4: estimate the arrival timestamp from the current UTC time.
                Instant etaAt = Instant.now()
                        .plus(Duration.ofMinutes(etaMinutes));

                // Return the hub details, delay stage, and calculated ETA.
                ctx.json(Map.of(
                        "hubId", hubId,
                        "province", textOrEmpty(hub, "province"),
                        "sortingCenter", textOrEmpty(hub, "sortingCenter"),
                        "delayStage", stage,
                        "baseDurationMinutes", baseDurationMinutes,
                        "minutesPerStage", minutesPerStage,
                        "etaMinutes", etaMinutes,
                        "etaDuration", Duration.ofMinutes(etaMinutes).toString(),
                        "etaAt", etaAt.toString()
                ));

            } catch (ApiException e) {
                // Return a controlled API error instead of exposing a stack trace.
                error(ctx, e.status, e.code, e.getMessage());
            }
        });
    }

    /**
     * Standardises hub IDs so the services use the same format.
     */
    public static String normalizeHubId(String hubId) {
        return hubId == null
                ? ""
                : hubId.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Calculates ETA minutes from the base duration and delay stage.
     *
     * Example: 120 + (15 * 3) = 165 minutes.
     */
    public static int calculateEtaMinutes(
            int baseDurationMinutes,
            int minutesPerStage,
            int stage) {

        // Negative configuration values and invalid stages are not allowed.
        if (baseDurationMinutes < 0 || minutesPerStage < 0) {
            throw new IllegalArgumentException(
                    "ETA configuration values must not be negative.");
        }

        if (stage < 0 || stage > 8) {
            throw new IllegalArgumentException(
                    "Stage must be between 0 and 8.");
        }

        // Exact arithmetic detects integer overflow instead of returning
        // an incorrect ETA if configuration values are unexpectedly large.
        return Math.addExact(
                baseDurationMinutes,
                Math.multiplyExact(minutesPerStage, stage));
    }

    /**
     * Reads a non-negative integer setting or uses its default value.
     */
    private static int readNonNegativeSetting(String name, int defaultValue) {
        String raw = System.getenv(name);

        // Use the default when the environment variable is not configured.
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }

        try {
            int value = Integer.parseInt(raw.trim());

            if (value < 0) {
                throw new IllegalArgumentException(
                        name + " must not be negative.");
            }

            return value;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    name + " must be a valid integer.", e);
        }
    }

    /**
     * Requests the hub details from the Hub Service and checks the response.
     */
    private static JsonNode getHub(String hubServiceUrl, String hubId) {
        String url = hubServiceUrl.replaceAll("/+$", "")
                + "/hubs/" + hubId;
        JsonNode hub = getJson(url, "Hub Service");

        // Check the response shape before using the hub data.
        if (!hub.isObject()
                || !hub.hasNonNull("hubId")
                || !hub.get("hubId").isTextual()) {
            throw new ApiException(502, "INVALID_HUB_RESPONSE",
                    "Hub Service returned an invalid hub response.");
        }

        return hub;
    }

    /**
     * Requests and validates the current delay stage.
     */
    private static int getStage(String delayServiceUrl, String hubId) {
        String url = delayServiceUrl.replaceAll("/+$", "")
                + "/delay-stage/" + hubId;
        JsonNode response = getJson(url, "Delay Stage Service");

        // The stage must be a whole number that Java can represent as an int.
        if (!response.isObject()
                || !response.has("stage")
                || !response.get("stage").isIntegralNumber()
                || !response.get("stage").canConvertToInt()) {
            throw new ApiException(502, "INVALID_DELAY_RESPONSE",
                    "Delay Stage Service returned an invalid stage.");
        }

        int stage = response.get("stage").asInt();

        // Do not calculate an ETA from an invalid upstream stage.
        if (stage < 0 || stage > 8) {
            throw new ApiException(502, "INVALID_DELAY_RESPONSE",
                    "Delay Stage Service returned a stage outside 0 to 8.");
        }

        return stage;
    }

    /**
     * Makes an HTTP GET request and parses the response as JSON.
     */
    private static JsonNode getJson(String url, String serviceName) {
        HttpRequest request;

        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new ApiException(503, "UPSTREAM_UNAVAILABLE",
                    serviceName + " URL is invalid.");
        }

        try {
            HttpResponse<String> response = HTTP.send(
                    request, HttpResponse.BodyHandlers.ofString());

            // A 404 means the requested hub could not be found upstream.
            if (response.statusCode() == 404) {
                throw new ApiException(404, "HUB_NOT_FOUND",
                        "No hub exists with ID " + url.substring(
                                url.lastIndexOf('/') + 1) + ".");
            }

            // Do not use a response when the upstream request failed.
            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new ApiException(503, "UPSTREAM_UNAVAILABLE",
                        serviceName + " returned HTTP "
                                + response.statusCode() + ".");
            }

            JsonNode body = JSON.readTree(response.body());

            // A successful HTTP status does not guarantee a valid JSON body.
            if (body == null || body.isNull()) {
                throw new ApiException(502, "INVALID_UPSTREAM_RESPONSE",
                        serviceName + " returned an empty response.");
            }

            return body;

        } catch (IOException e) {
            // The request failed or the upstream response was not valid JSON.
            throw new ApiException(502, "INVALID_UPSTREAM_RESPONSE",
                    serviceName + " returned invalid JSON.");

        } catch (InterruptedException e) {
            // Preserve the interruption signal for the calling thread.
            Thread.currentThread().interrupt();
            throw new ApiException(503, "UPSTREAM_UNAVAILABLE",
                    "Request to " + serviceName + " was interrupted.");
        }
    }

    /**
     * Returns a string field from a JSON object, or an empty string if absent.
     */
    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : "";
    }

    /**
     * Sends errors in a consistent JSON format.
     */
    private static void error(
            io.javalin.http.Context ctx,
            int status,
            String code,
            String message) {
        ctx.status(status).json(Map.of(
                "error", code,
                "message", message
        ));
    }

    /**
     * Carries the HTTP status and error code together when a request fails.
     */
    private static final class ApiException extends RuntimeException {
        private final int status;
        private final String code;

        private ApiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
