package co.wethinkcode.logisticsconnect.ingestion;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import co.wethinkcode.logisticsconnect.model.Hub;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Responsible only for reading CSV rows and converting them into Hub objects.
 */

public class HubCsvParser {

    // Read the CSV and return one Hub object for each structurally valid data row.
    public List<Hub> parse(Reader reader) throws IOException {
        // Accumulate parsed records in source-file order.
        List<Hub> hubs = new ArrayList<>();

        // try-with-resources closes the CSV reader even if parsing fails.
        try (CSVReader csvReader = new CSVReader(reader)) {

            // Consume the first row as column headings, not as a hub record.
            String[] headers = csvReader.readNext();

            // An empty file has no header or data rows, so return an empty result.
            if (headers == null) {
                return hubs;
            }

            String[] row;

            // Process each remaining CSV row until the end of the file.
            while ((row = csvReader.readNext()) != null) {
                // Ignore malformed rows that do not contain all four expected columns.
                if (row.length < 4) {
                    continue;
                }

                // CSV columns map by position to the Hub fields defined by this project.
                Hub hub = new Hub(
                        row[0],
                        row[1],
                        row[2],
                        parseBoolean(row[3])
                );

                // Keep the parsed record for later normalisation and deduplication.
                hubs.add(hub);
            }

        } catch (CsvValidationException e) {
            throw new IOException("Invalid CSV format", e);
        }

        return hubs;
    }

    // Convert common legacy boolean spellings into true/false; unknown values become null.
    private Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        // Use a locale-independent conversion for legacy flag values.
        String normalised = value.trim().toLowerCase(Locale.ROOT);

        // Accept known true/false variants; any unrecognised value is unknown.
        return switch (normalised) {
            case "y", "yes", "1", "true" -> true;
            case "n", "no", "0", "false" -> false;
            default -> null;
        };
    }
}
