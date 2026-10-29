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

    // Preserve the existing method for callers that only need the hub list.
    public List<Hub> parse(Reader reader) throws IOException {
        return parseWithStats(reader).hubs();
    }

    // Parse the CSV and report how many malformed rows were skipped.
    public ParseResult parseWithStats(Reader reader) throws IOException {
        List<Hub> hubs = new ArrayList<>();
        int invalidRowsDropped = 0;

        // Close the CSV reader and underlying reader when finished.
        try (CSVReader csvReader = new CSVReader(reader)) {

            // The first row contains the column headings.
            String[] headers = csvReader.readNext();

            // An empty file contains no data rows.
            if (headers == null) {
                return new ParseResult(List.of(), 0);
            }

            String[] row;

            while ((row = csvReader.readNext()) != null) {

                // A valid hub row must contain all four expected columns.
                if (row.length < 4) {
                    invalidRowsDropped++;
                    continue;
                }

                Hub hub = new Hub(
                        row[0],
                        row[1],
                        row[2],
                        parseBoolean(row[3])
                );

                hubs.add(hub);
            }

        } catch (CsvValidationException exception) {
            throw new IOException("Invalid CSV format", exception);
        }

        return new ParseResult(hubs, invalidRowsDropped);
    }

    // Return the parsed records and the number of malformed rows skipped.
    public record ParseResult(
            List<Hub> hubs,
            int invalidRowsDropped
    ) {
        public ParseResult {
            hubs = List.copyOf(hubs);
        }
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
