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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores and returns the current delay stage for each hub.
 *
 * In Iteration 2, stages are stored in memory. They will be lost when the
 * service restarts. REST is used here; ActiveMQ integration belongs to Iteration 3.
 */

public class DelayStageServiceApp {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DEFAULT_HUB_URL =
            "http://localhost:7051";

    private static final int MIN_STAGE = 0;
    private static final int MAX_STAGE = 8;

    // A hub ID maps to its latest stage.
    // ConcurrentHashMap safely supports requests running at the same time.
    private static final ConcurrentMap<String, Integer> stages =
            new ConcurrentHashMap<>();

    // Reuse the HTTP client instead of creating one for every request.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void main(String[] args) {
        // The Hub Service address can be configured for different environments.
        String hubServiceUrl = System.getenv().getOrDefault(
                "HUB_SERVICE_URL", DEFAULT_HUB_URL);

        int port = Integer.parseInt(
                System.getenv().getOrDefault(
                        "DELAY_STAGE_SERVICE_PORT", "7052"));

        Javalin app = Javalin.create().start(port);

        // Report the number of hubs for which a stage has been stored.
        app.get("/health", ctx -> ctx.json(Map.of(
                "status", "UP",
                "service", "delay-stage-service",
                "storedHubCount", stages.size()
        )));

        // TODO (Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns).)
        // Add domain endpoints for delay-stage-service here.

        // Return the saved stage, or stage 0 if this hub has no saved stage.
        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = normalizeHubId(ctx.pathParam("hubId"));

            if (hubId.isBlank()) {
                error(ctx, 400, "INVALID_HUB_ID",
                        "Hub ID must not be blank.");
                return;
            }

            try {
                // Do not return stage data for a hub that does not exist.
                requireKnownHub(hubServiceUrl, hubId);

                int stage = stages.getOrDefault(hubId, 0);
                ctx.json(Map.of("hubId", hubId, "stage", stage));

            } catch (ApiException e) {
                error(ctx, e.status, e.code, e.getMessage());
            }
        });

        // Validate a new stage and save it for the requested hub.
        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = normalizeHubId(ctx.pathParam("hubId"));

            if (hubId.isBlank()) {
                error(ctx, 400, "INVALID_HUB_ID",
                        "Hub ID must not be blank.");
                return;
            }

            final JsonNode body;

            // Invalid JSON should produce a client error, not crash the route.
            try {
                body = JSON.readTree(ctx.body());
            } catch (IOException e) {
                error(ctx, 400, "INVALID_JSON",
                        "Request body must contain valid JSON.");
                return;
            }

            // The stage must exist in the body and be a whole number.
            if (body == null || !body.has("stage")
                    || !body.get("stage").isIntegralNumber()
                    || !body.get("stage").canConvertToInt()) {
                error(ctx, 400, "INVALID_STAGE",
                        "Stage must be an integer between 0 and 8.");
                return;
            }

            int stage = body.get("stage").asInt();

            // Reject stages outside the supported range.
            if (!isValidStage(stage)) {
                error(ctx, 400, "INVALID_STAGE",
                        "Stage must be an integer between 0 and 8.");
                return;
            }

            try {
                // Check hub existence before changing the stored value.
                requireKnownHub(hubServiceUrl, hubId);

                stages.put(hubId, stage);

                ctx.json(Map.of(
                        "hubId", hubId,
                        "stage", stage,
                        "message", "Delay stage updated."
                ));

            } catch (ApiException e) {
                error(ctx, e.status, e.code, e.getMessage());
            }
        });
    }

    /**
     * Returns true only for the supported stages 0 through 8.
     */
    public static boolean isValidStage(int stage) {
        return stage >= MIN_STAGE && stage <= MAX_STAGE;
    }

    /**
     * Makes hub ID comparisons consistent across services.
     */
    public static String normalizeHubId(String hubId) {
        return hubId == null
                ? ""
                : hubId.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Calls the Hub Service to confirm that this hub exists.
     * An unknown hub is different from a Hub Service connection failure.
     */
    private static void requireKnownHub(String hubServiceUrl, String hubId) {
        String url = hubServiceUrl.replaceAll("/+$", "")
                + "/hubs/" + hubId;

        HttpRequest request;

        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new ApiException(503, "HUB_SERVICE_UNAVAILABLE",
                    "Hub Service URL is invalid.");
        }

        try {
            HttpResponse<String> response = HTTP.send(
                    request, HttpResponse.BodyHandlers.ofString());

            // The Hub Service has confirmed that the ID does not exist.
            if (response.statusCode() == 404) {
                throw new ApiException(404, "HUB_NOT_FOUND",
                        "No hub exists with ID " + hubId + ".");
            }

            // Other unsuccessful responses mean the lookup could not be completed.
            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new ApiException(503, "HUB_SERVICE_UNAVAILABLE",
                        "Hub Service returned HTTP "
                                + response.statusCode() + ".");
            }

            JsonNode hub = JSON.readTree(response.body());

            // A successful status alone is not enough; the response must contain a hub.
            if (hub == null || !hub.isObject()
                    || !hub.hasNonNull("hubId")) {
                throw new ApiException(502, "INVALID_HUB_RESPONSE",
                        "Hub Service returned an invalid hub response.");
            }

        } catch (IOException e) {
            // The Hub Service could not be reached or returned unreadable JSON.
            throw new ApiException(503, "HUB_SERVICE_UNAVAILABLE",
                    "Could not connect to Hub Service.");

        } catch (InterruptedException e) {
            // Restore the thread's interrupt status before returning an error.
            Thread.currentThread().interrupt();
            throw new ApiException(503, "HUB_SERVICE_UNAVAILABLE",
                    "Hub Service request was interrupted.");
        }
    }

    /**
     * Sends API errors in the same JSON format used by the other services.
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
     * Keeps the HTTP status and error code together when a lookup fails.
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

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
