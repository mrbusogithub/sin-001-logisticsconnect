package co.wethinkcode.logisticsconnect.ingestion;

import co.wethinkcode.logisticsconnect.model.Hub;

import java.util.List;

/**
 * The cleaned ingestion output, plus counters describing what the
 * pipeline did. Exposed through GET /stats so duplicate removal is
 * observable rather than silent.
 */

public record HubDataset(
        List<Hub> hubs,
        int parsedRows,
        int invalidDropped,
        int duplicatesRemoved
) {
    public HubDataset {
        // Prevent callers from modifying the stored hub list.
        hubs = List.copyOf(hubs);
    }
}
