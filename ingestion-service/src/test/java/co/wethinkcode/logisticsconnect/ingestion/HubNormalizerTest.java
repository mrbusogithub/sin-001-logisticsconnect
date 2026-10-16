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

    @Test
        // Verify repeated spaces inside a name become one space.
    void shouldCollapseInternalWhitespace() {
        Hub input = new Hub(
                "H-505",
                "Western Cape",
                "Cape Town  Port",
                true
        );

        Hub result = normalizer.normalize(input);

        assertEquals("Cape Town Port", result.getSortingCenter());
    }

    @Test
        // Verify case variants map to the canonical province name.
    void shouldNormaliseProvinceCasing() {
        Hub input = new Hub(
                "H-502",
                "gauteng",
                "Pretoria North",
                false
        );

        Hub result = normalizer.normalize(input);

        assertEquals("Gauteng", result.getProvince());
    }

    @Test
        // Blank text is treated as missing data, not a real province name.
    void shouldConvertBlankTextToNull() {
        Hub input = new Hub(
                "H-508",
                "   ",
                "Pretoria North",
                true
        );

        Hub result = normalizer.normalize(input);

        assertNull(result.getProvince());
    }
}
