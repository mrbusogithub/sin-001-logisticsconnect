package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.ingestion.HubCsvParser;
import co.wethinkcode.logisticsconnect.ingestion.HubDataset;
import co.wethinkcode.logisticsconnect.ingestion.HubDeduplicator;
import co.wethinkcode.logisticsconnect.ingestion.HubNormalizer;
import co.wethinkcode.logisticsconnect.model.Hub;
import io.javalin.Javalin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads, cleans, and exposes hub data through HTTP.
 */
public class IngestionServiceApp {

    // Read the CSV from the packaged application resources.
    private static final String CSV_RESOURCE = "/hubs-global.csv";

    public static void main(String[] args) {
        // Load the cleaned data and its statistics at startup.
        HubDataset dataset = loadHubDataset();

        System.out.printf(
                "Ingestion: parsed %d row(s), kept %d hub(s), "
                        + "dropped %d invalid row(s), "
                        + "removed %d duplicate(s).%n",
                dataset.parsedRows(),
                dataset.hubs().size(),
                dataset.invalidDropped(),
                dataset.duplicatesRemoved()
        );

        Javalin app = Javalin.create().start(7050);

        // Use the same JSON health-response format as the other services.
        app.get("/health", ctx -> ctx.json(Map.of(
                "status", "UP",
                "service", "ingestion-service",
                "hubCount", dataset.hubs().size()
        )));

        // Keep the existing contract: this endpoint returns a JSON array.
        app.get("/hubs", ctx -> ctx.json(dataset.hubs()));

        // Expose the results of the data-cleaning process.
        app.get("/stats", ctx -> ctx.json(Map.of(
                "parsedRows", dataset.parsedRows(),
                "hubCount", dataset.hubs().size(),
                "invalidDropped", dataset.invalidDropped(),
                "duplicatesRemoved", dataset.duplicatesRemoved()
        )));
    }

    // Preserve the existing method used by tests and other callers.
    static List<Hub> loadHubs() {
        return loadHubDataset().hubs();
    }

    // Run the parsing, validation, normalisation, and deduplication pipeline.
    static HubDataset loadHubDataset() {
        HubCsvParser parser = new HubCsvParser();
        HubNormalizer normalizer = new HubNormalizer();
        HubDeduplicator deduplicator = new HubDeduplicator();

        try (InputStream inputStream =
                     IngestionServiceApp.class
                             .getResourceAsStream(CSV_RESOURCE)) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Could not find " + CSV_RESOURCE
                );
            }

            Reader reader = new InputStreamReader(
                    inputStream,
                    StandardCharsets.UTF_8
            );

            // Parse the source rows and count malformed rows.
            HubCsvParser.ParseResult parseResult =
                    parser.parseWithStats(reader);

            // Normalise records before validating their IDs.
            List<Hub> normalisedHubs = parseResult.hubs()
                    .stream()
                    .map(normalizer::normalize)
                    .toList();

            int invalidDropped = parseResult.invalidRowsDropped();
            List<Hub> validHubs = new ArrayList<>();

            // Reject records that cannot be identified by a usable hub ID.
            for (Hub hub : normalisedHubs) {
                if (isMissingHubId(hub.getHubId())) {
                    invalidDropped++;
                    continue;
                }

                validHubs.add(hub);
            }

            // Deduplicate the validated records and capture the count.
            HubDeduplicator.DeduplicationResult deduplication =
                    deduplicator.deduplicateWithStats(validHubs);

            return new HubDataset(
                    deduplication.hubs(),
                    parseResult.hubs().size(),
                    invalidDropped,
                    deduplication.duplicatesRemoved()
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to load hub data",
                    exception
            );
        }
    }

    // Recognise blank IDs and common placeholder values as invalid.
    static boolean isMissingHubId(String hubId) {
        if (hubId == null || hubId.isBlank()) {
            return true;
        }

        return switch (hubId.trim().toLowerCase(Locale.ROOT)) {
            case "n/a", "na", "tbd", "unknown", "-", "nan" -> true;
            default -> false;
        };
    }
}