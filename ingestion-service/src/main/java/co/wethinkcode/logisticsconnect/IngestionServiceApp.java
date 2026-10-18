package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import co.wethinkcode.logisticsconnect.ingestion.HubCsvParser;
import co.wethinkcode.logisticsconnect.ingestion.HubDeduplicator;
import co.wethinkcode.logisticsconnect.ingestion.HubNormalizer;
import co.wethinkcode.logisticsconnect.model.Hub;
import io.javalin.Javalin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Application entry point: loads the cleaned data and exposes it through HTTP.
 */

public class IngestionServiceApp {

    // Resource path is relative to the application's classpath, not the working directory.
    private static final String CSV_RESOURCE =
            "/hubs-global.csv";

    // Start the service and register its HTTP endpoints.
    public static void main(String[] args) {

        // Load once at startup so requests can serve the already-cleaned data.
        List<Hub> hubs = loadHubs();

        Javalin app = Javalin.create().start(7050);

        // Simple liveness endpoint used to check that the service is running.
        app.get("/health", ctx -> ctx.result("OK"));

        // Return the cleaned hub collection as JSON for downstream services.
        app.get("/hubs", ctx ->
                ctx.json(hubs)
        );

        // TODO: read and clean src/main/resources/hubs-global.csv (hubs, sorting centers, regional districts data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
    }

    public static List<Hub> loadHubs() {
        return null;
    }
}
