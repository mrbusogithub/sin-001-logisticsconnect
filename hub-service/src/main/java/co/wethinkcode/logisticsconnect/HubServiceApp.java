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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provides hub information to other LogisticsConnect services.
 *
 * Hub data is loaded from the Ingestion Service when this application starts.
 * The data is then cached so that every hub request does not need another
 * request to the Ingestion Service.
 */

public class HubServiceApp {

    // Jackson converts JSON responses into objects we can work with.
    private static final ObjectMapper JSON = new ObjectMapper();

    // Default address of the service that provides cleaned hub data.
    private static final String DEFAULT_INGESTION_URL =
            "http://localhost:7050";

    public static void main(String[] args) {
        // Allow the Ingestion Service address to be changed without editing code.
        String ingestionUrl = System.getenv().getOrDefault(
                "INGESTION_SERVICE_URL", DEFAULT_INGESTION_URL);

        // Load the hub data before accepting requests.
        // If this fails, the application stops instead of starting without data.
        HubDirectory directory = HubDirectory.load(ingestionUrl);

        // Use the configured port, or 7051 when no setting is provided.
        int port = Integer.parseInt(
                System.getenv().getOrDefault("HUB_SERVICE_PORT", "7051"));

        Javalin app = Javalin.create().start(port);

        // Report whether the service is running and how many hubs are cached.
        app.get("/health", ctx -> ctx.json(Map.of(
                "status", "UP",
                "service", "hub-service",
                "hubCount", directory.all().size()
        )));

        // TODO (Serves provinces and sorting centers (place-name source of truth).)
        // Add domain endpoints for hub-service here.

        // Return every cached hub as a JSON array.
        app.get("/hubs", ctx -> ctx.json(directory.all()));

        // Return one hub using its ID from the URL.
        app.get("/hubs/{hubId}", ctx -> {
            String hubId = normalizeHubId(ctx.pathParam("hubId"));

            // Reject a missing or blank ID before searching the cache.
            if (hubId.isBlank()) {
                error(ctx, 400, "INVALID_HUB_ID",
                        "Hub ID must not be blank.");
                return;
            }

            JsonNode hub = directory.find(hubId);

            // A valid ID can still refer to a hub that does not exist.
            if (hub == null) {
                error(ctx, 404, "HUB_NOT_FOUND",
                        "No hub exists with ID " + hubId + ".");
                return;
            }

            // Javalin serialises the hub JSON object into the HTTP response.
            ctx.json(hub);
        });
    }

    /**
     * Trims whitespace and converts IDs to uppercase.
     * This lets "h-501", "H-501", and " H-501 " match the same hub.
     */
    public static String normalizeHubId(String hubId) {
        if (hubId == null) {
            return "";
        }
        return hubId.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Sends a consistent JSON structure for API errors.
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
     * Holds the hub records loaded from Ingestion.
     * The map uses a normalised hub ID as its key for fast lookups.
     */
    static final class HubDirectory {
        private final Map<String, JsonNode> hubsById;

        HubDirectory(List<JsonNode> records) {
            Map<String, JsonNode> indexed = new LinkedHashMap<>();

            for (JsonNode record : records) {
                JsonNode idNode = record.get("hubId");

                // Every hub needs a usable ID before it can be cached.
                if (idNode == null || !idNode.isTextual()
                        || normalizeHubId(idNode.asText()).isBlank()) {
                    throw new IllegalArgumentException(
                            "Ingestion returned a hub without a valid hubId.");
                }

                String id = normalizeHubId(idNode.asText());

                // Do not silently overwrite a hub if the same ID appears twice.
                if (indexed.putIfAbsent(id, record) != null) {
                    throw new IllegalArgumentException(
                            "Ingestion returned duplicate hub ID: " + id);
                }
            }

            // Prevent changes to the index after the directory is constructed.
            this.hubsById = Collections.unmodifiableMap(indexed);
        }

        /**
         * Requests the cleaned hub list from Ingestion and builds the cache.
         */
        static HubDirectory load(String ingestionUrl) {
            // Remove trailing slashes to avoid creating a URL with "//hubs".
            String url = ingestionUrl.replaceAll("/+$", "") + "/hubs";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = client.send(
                        request, HttpResponse.BodyHandlers.ofString());

                // Only successful HTTP responses can be used as hub data.
                if (response.statusCode() < 200
                        || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "Ingestion returned HTTP "
                                    + response.statusCode());
                }

                JsonNode root = JSON.readTree(response.body());

                // The ingestion contract requires a JSON array of hubs.
                if (root == null || !root.isArray()) {
                    throw new IllegalStateException(
                            "Ingestion /hubs must return a JSON array.");
                }

                List<JsonNode> records = new ArrayList<>();
                root.forEach(records::add);

                // An empty cache could make every valid hub appear missing.
                if (records.isEmpty()) {
                    throw new IllegalStateException(
                            "Ingestion returned no hubs; refusing to start.");
                }

                return new HubDirectory(records);

            } catch (IOException e) {
                // Covers network errors and JSON parsing errors.
                throw new IllegalStateException(
                        "Could not parse hub data from " + url, e);

            } catch (InterruptedException e) {
                // Restore the interrupt flag so the interruption is not lost.
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while loading hubs from " + url, e);

            } catch (IllegalArgumentException e) {
                // Includes an invalid URL or invalid hub records.
                throw new IllegalStateException(
                        "Invalid ingestion URL or hub data: " + e.getMessage(),
                        e);
            }
        }

        /**
         * Returns a read-only list of all cached hubs.
         */
        List<JsonNode> all() {
            return List.copyOf(hubsById.values());
        }

        /**
         * Finds a hub using a normalised ID.
         * Returns null when the ID is not present in the cache.
         */
        JsonNode find(String hubId) {
            return hubsById.get(normalizeHubId(hubId));
        }
    }
}
