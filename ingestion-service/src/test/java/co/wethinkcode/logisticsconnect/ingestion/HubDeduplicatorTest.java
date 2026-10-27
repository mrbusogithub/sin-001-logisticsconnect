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

    @Test
    // Reversing the input must not change which duplicate wins.
    void deduplicationShouldBeIndependentOfInputOrder() {

        Hub first = new Hub(
                "H-515",
                "Gauteng",
                "Johannesburg Central",
                true
        );

        Hub second = new Hub(
                "H-500",
                "Gauteng",
                "Johannesburg Central",
                false
        );

        List<Hub> resultOne =
                deduplicator.deduplicate(List.of(first, second));

        List<Hub> resultTwo =
                deduplicator.deduplicate(List.of(second, first));

        assertEquals(
                resultOne.get(0).getHubId(),
                resultTwo.get(0).getHubId()
        );

        assertEquals("H-500", resultOne.get(0).getHubId());
    }

    /**
     * Verifies that casing and repeated whitespace do not hide duplicate
     * sorting-centre records.
     */
    @Test
    void shouldDeduplicateIgnoringCasingAndWhitespace() {
        List<Hub> hubs = List.of(
                new Hub(
                        "H-510",
                        "GAUTENG",
                        " johannesburg   central ",
                        false
                ),
                new Hub(
                        "H-500",
                        "Gauteng",
                        "Johannesburg Central",
                        true
                )
        );

        List<Hub> result = deduplicator.deduplicate(hubs);

        // Both records describe the same normalized province and centre.
        assertEquals(1, result.size());

        // The existing canonical-selection rule keeps the smallest ID.
        assertEquals("H-500", result.get(0).getHubId());
    }

    /**
     * Verifies that placeholder identity values cannot cause unrelated
     * incomplete records to be merged.
     */
    @Test
    void shouldNotMergeRecordsWithPlaceholderIdentityFields() {
        List<Hub> hubs = List.of(
                new Hub(
                        "H-601",
                        "unknown",
                        "Pretoria North",
                        true
                ),
                new Hub(
                        "H-602",
                        "N/A",
                        "Pretoria North",
                        false
                )
        );

        List<Hub> result = deduplicator.deduplicate(hubs);

        // Their province values do not establish a reliable identity.
        assertEquals(2, result.size());
    }
}
