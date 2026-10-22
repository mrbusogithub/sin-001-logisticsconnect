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

import static jdk.internal.org.jline.utils.Log.error;

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
}
