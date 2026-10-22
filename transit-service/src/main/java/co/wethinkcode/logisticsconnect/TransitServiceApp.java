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

    public static int calculateEtaMinutes(int i, int i1, int i2) {
        return 0;
    }

    public static String normalizeHubId(String s) {
        return null;
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
