package co.wethinkcode.logisticsconnect.ingestion;

import co.wethinkcode.logisticsconnect.model.Hub;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for whitespace, casing, and missing-text normalization.
 */

public class HubNormalizerTest {

    private final HubNormalizer normalizer = new HubNormalizer();

    @Test
        // Verify leading and trailing spaces are removed from text fields.
    void shouldTrimWhitespace() {
        Hub input = new Hub(
                " H-502 ",
                " Gauteng ",
                " Pretoria North ",
                false
        );

        Hub result = normalizer.normalize(input);

        assertEquals("H-502", result.getHubId());
        assertEquals("Gauteng", result.getProvince());
        assertEquals("Pretoria North", result.getSortingCenter());
    }
}
