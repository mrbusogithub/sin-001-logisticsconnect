
package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class Iteration2AcceptanceTests {

    // These URLs point to the four real services running locally.
    private static final String INGESTION_URL =
            setting("ingestion.url", "INGESTION_SERVICE_URL",
                    "http://localhost:7050");

    private static final String HUB_URL =
            setting("hub.url", "HUB_SERVICE_URL",
                    "http://localhost:7051");

    private static final String DELAY_URL =
            setting("delay.url", "DELAY_STAGE_SERVICE_URL",
                    "http://localhost:7052");

    private static final String TRANSIT_URL =
            setting("transit.url", "TRANSIT_SERVICE_URL",
                    "http://localhost:7053");

    // H-500 is the sample hub used by this acceptance scenario.
    private static final String HUB_ID = "H-500";

    private static final ObjectMapper JSON = new ObjectMapper();

    // Every request has a timeout so a failed service cannot hang the test.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /**
     * Acceptance scenario:
     * Ingestion provides the data, Hub exposes it, Delay Stage stores
     * the stage, and Transit uses the hub and stage to calculate the ETA.
     */
    @Test
    void completeRestFlowCalculatesExpectedEta() throws Exception {

        // Step 1: Confirm all four services are responding.
        assertServiceResponds(INGESTION_URL, "/health", "Ingestion");
        assertServiceResponds(HUB_URL, "/health", "Hub");
        assertServiceResponds(DELAY_URL, "/health", "Delay Stage");
        assertServiceResponds(TRANSIT_URL, "/health", "Transit");

        // Step 2: Confirm Hub exposes cleaned data from Ingestion.
        JsonNode hubs = getJson(HUB_URL + "/hubs");

        assertTrue(
                hubs.isArray(),
                "Hub Service should return a JSON array of hubs."
        );

        assertFalse(
                hubs.isEmpty(),
                "Hub Service should return at least one hub."
        );

        // Hub IDs should be looked up without case sensitivity.
        JsonNode hub = getJson(HUB_URL + "/hubs/h-500");

        assertEquals(
                HUB_ID,
                hub.path("hubId").asText(),
                "Hub lookup should return the canonical hub ID."
        );

        // Step 3: Read the current stage so the test can restore it later.
        // This prevents the test from unnecessarily leaving stage 3 behind.
        JsonNode previousStageResponse =
                getJson(DELAY_URL + "/delay-stage/" + HUB_ID);

        int previousStage = previousStageResponse.path("stage").asInt(-1);

        assertTrue(
                previousStage >= 0 && previousStage <= 8,
                "The stored delay stage must be between 0 and 8."
        );

        try {
            // Step 4: Set the known hub's delay stage to 3.
            JsonNode updateResponse = postStage(HUB_ID, 3);

            assertEquals(
                    3,
                    updateResponse.path("stage").asInt(-1),
                    "The stage update should return stage 3."
            );

            // Read it again to confirm that the stage was actually stored.
            JsonNode storedStage =
                    getJson(DELAY_URL + "/delay-stage/" + HUB_ID);

            assertEquals(
                    3,
                    storedStage.path("stage").asInt(-1),
                    "A subsequent GET should return the updated stage."
            );

            // Step 5: Transit must call Hub and Delay Stage to calculate ETA.
            JsonNode eta = getJson(TRANSIT_URL + "/transit/" + HUB_ID);

            assertEquals(
                    HUB_ID,
                    eta.path("hubId").asText(),
                    "Transit should identify the requested hub."
            );

            assertEquals(
                    3,
                    eta.path("delayStage").asInt(-1),
                    "Transit should use the current delay stage."
            );

            // Default calculation: 120 base minutes + (3 x 15) = 165.
            assertEquals(
                    165,
                    eta.path("etaMinutes").asInt(-1),
                    "Stage 3 should produce an ETA of 165 minutes."
            );

            assertEquals(
                    "PT2H45M",
                    eta.path("etaDuration").asText(),
                    "The ETA should also be returned as an ISO-8601 duration."
            );

            // The ETA timestamp must be present and parse as an ISO-8601 instant.
            String etaAt = eta.path("etaAt").asText();

            assertFalse(
                    etaAt.isBlank(),
                    "Transit should include the estimated arrival timestamp."
            );

            assertDoesNotThrow(
                    () -> Instant.parse(etaAt),
                    "The ETA timestamp should be a valid ISO-8601 instant."
            );

        } finally {
            // Restore the original stage even if an assertion fails.
            // This helps keep repeated acceptance-test runs independent.
            postStage(HUB_ID, previousStage);
        }
    }

    /**
     * An invalid stage must be rejected with HTTP 400.
     */
    @Test
    void delayStageRejectsValuesOutsideAllowedRange() throws Exception {

        // The hub must exist so the test checks stage validation,
        // rather than failing because the hub is unknown.
        getJson(HUB_URL + "/hubs/" + HUB_ID);

        HttpResponse<String> response = postStageRaw(HUB_ID, 9);

        assertEquals(
                400,
                response.statusCode(),
                "A stage above 8 should return HTTP 400."
        );
    }

    /**
     * Transit must not calculate an ETA for an unknown hub.
     */
    @Test
    void transitRejectsUnknownHub() throws Exception {

        HttpResponse<String> response =
                getRaw(TRANSIT_URL + "/transit/UNKNOWN-HUB-999");

        assertEquals(
                404,
                response.statusCode(),
                "An unknown hub should return HTTP 404."
        );
    }

    // Checks that a service's health endpoint responds successfully.
    private static void assertServiceResponds(
            String baseUrl,
            String path,
            String serviceName
    ) throws Exception {

        HttpResponse<String> response = getRaw(baseUrl + path);

        assertEquals(
                200,
                response.statusCode(),
                serviceName + " health endpoint should return HTTP 200."
        );

        assertFalse(
                response.body() == null || response.body().isBlank(),
                serviceName + " health endpoint should return a response body."
        );
    }

    // Sends a GET request and returns its response.
    private static HttpResponse<String> getRaw(String url)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Sends a GET request and checks that the response is successful JSON.
    private static JsonNode getJson(String url) throws Exception {

        HttpResponse<String> response = getRaw(url);

        assertEquals(
                200,
                response.statusCode(),
                "GET " + url + " should return HTTP 200. Body: "
                        + response.body()
        );

        return JSON.readTree(response.body());
    }

    // Sends a stage update and returns the JSON response.
    private static JsonNode postStage(String hubId, int stage)
            throws Exception {

        HttpResponse<String> response = postStageRaw(hubId, stage);

        assertEquals(
                200,
                response.statusCode(),
                "Updating the stage should succeed. Body: " + response.body()
        );

        return JSON.readTree(response.body());
    }

    // Sends the actual POST request, allowing tests to inspect error statuses.
    private static HttpResponse<String> postStageRaw(
            String hubId,
            int stage
    ) throws IOException, InterruptedException {

        String body = JSON.writeValueAsString(
                java.util.Map.of("stage", stage)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DELAY_URL + "/delay-stage/" + hubId))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Allows service URLs to be overridden for a different test environment.
    // A Java system property takes precedence over an environment variable.
    private static String setting(
            String propertyName,
            String environmentName,
            String defaultValue
    ) {
        String property = System.getProperty(propertyName);

        if (property != null && !property.isBlank()) {
            return property.replaceAll("/+$", "");
        }

        String environment = System.getenv(environmentName);

        if (environment != null && !environment.isBlank()) {
            return environment.replaceAll("/+$", "");
        }

        return defaultValue;
    }
}
