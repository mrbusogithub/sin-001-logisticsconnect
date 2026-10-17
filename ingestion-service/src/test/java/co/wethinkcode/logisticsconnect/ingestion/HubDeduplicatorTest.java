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

    @Test
    // Conflicting duplicates use the smallest ID as the canonical record.
    void shouldChooseSmallestHubIdDeterministically() {

        List<Hub> hubs = List.of(
                new Hub(
                        "H-515",
                        "Gauteng",
                        "Johannesburg Central",
                        true
                ),
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
                        false
                )
        );

        List<Hub> result =
                deduplicator.deduplicate(hubs);

        assertEquals(1, result.size());
        assertEquals("H-500", result.get(0).getHubId());
    }

    @Test
    // Missing identity data means the two records must remain separate.
    void shouldNotMergeRecordsWithMissingProvince() {

        List<Hub> hubs = List.of(
                new Hub(
                        "H-508",
                        null,
                        "Pretoria North",
                        true
                ),
                new Hub(
                        "H-502",
                        "Gauteng",
                        "Pretoria North",
                        false
                )
        );

        List<Hub> result =
                deduplicator.deduplicate(hubs);

        assertEquals(2, result.size());
    }
}
