package co.wethinkcode.logisticsconnect.ingestion;

import co.wethinkcode.logisticsconnect.model.Hub;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for duplicate detection and deterministic conflict resolution.
 */

public class HubDeduplicatorTest {

    private final HubDeduplicator deduplicator =
            new HubDeduplicator();

    @Test
        // Two records with the same identity should produce one output record.
    void shouldRemoveDuplicateHubRecords() {

        List<Hub> hubs = List.of(
                new Hub(
                        "H-500",
                        "Gauteng",
                        "Johannesburg Central",
                        true
                ),
                new Hub(
                        "H-504",
                        "Gauteng",
                        "Johannesburg Central",
                        true
                )
        );

        List<Hub> result =
                deduplicator.deduplicate(hubs);

        assertEquals(1, result.size());
        assertEquals("H-500", result.get(0).getHubId());
    }
}
