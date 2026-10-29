package co.wethinkcode.logisticsconnect.ingestion;

import co.wethinkcode.logisticsconnect.model.Hub;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for converting CSV text into Hub objects.
 */
public class HubCsvParserTest {

    private final HubCsvParser parser = new HubCsvParser();

    @Test
        // Arrange sample CSV, parse it, then verify each mapped field.
    void shouldParseValidCsvRecord() throws Exception {
        String csv = """
                hub_id,Province,sorting_center,active
                H-500,Gauteng,Johannesburg Central,Y
                """;

        List<Hub> hubs = parser.parse(new StringReader(csv));

        assertEquals(1, hubs.size());

        Hub hub = hubs.get(0);

        assertEquals("H-500", hub.getHubId());
        assertEquals("Gauteng", hub.getProvince());
        assertEquals("Johannesburg Central", hub.getSortingCenter());
        assertTrue(hub.getActive());
    }

    @Test
        // Ensure a recognized false value is not mistaken for true or unknown.
    void shouldParseFalseBoolean() throws Exception {
        String csv = """
                hub_id,Province,sorting_center,active
                H-500,Gauteng,Johannesburg Central,N
                """;

        List<Hub> hubs = parser.parse(new StringReader(csv));

        assertFalse(hubs.get(0).getActive());
    }

    @Test
        // Unknown source values remain unknown rather than being guessed.
    void shouldRepresentUnknownBooleanAsNull() throws Exception {
        String csv = """
                hub_id,Province,sorting_center,active
                H-511,Limpopo,Polokwane Hub,unknown
                """;

        List<Hub> hubs = parser.parse(new StringReader(csv));

        assertNull(hubs.get(0).getActive());
    }

    // -------------- issue #4 ------------------------
    @Test
    // Every supported true spelling should produce Boolean.TRUE.
    void shouldNormaliseTrueRepresentations() throws Exception {
        String csv = """
            hub_id,Province,sorting_center,active
            H-500,Gauteng,Johannesburg Central,Y
            H-501,Gauteng,Johannesburg Central,yes
            H-502,Gauteng,Johannesburg Central,1
            H-503,Gauteng,Johannesburg Central,true
            """;

        List<Hub> hubs = parser.parse(new StringReader(csv));

        assertTrue(hubs.get(0).getActive());
        assertTrue(hubs.get(1).getActive());
        assertTrue(hubs.get(2).getActive());
        assertTrue(hubs.get(3).getActive());
    }

    @Test
    // Every supported false spelling should produce Boolean.FALSE.
    void shouldNormaliseFalseRepresentations() throws Exception {
        String csv = """
            hub_id,Province,sorting_center,active
            H-500,Gauteng,Johannesburg Central,N
            H-501,Gauteng,Johannesburg Central,no
            H-502,Gauteng,Johannesburg Central,0
            H-503,Gauteng,Johannesburg Central,false
            """;

        List<Hub> hubs = parser.parse(new StringReader(csv));

        assertFalse(hubs.get(0).getActive());
        assertFalse(hubs.get(1).getActive());
        assertFalse(hubs.get(2).getActive());
        assertFalse(hubs.get(3).getActive());
    }

    @Test
    void shouldCountMalformedRowsDropped() throws Exception {
        String csv = """
            hub_id,Province,sorting_center,active
            H-500,Gauteng,Johannesburg Central,Y
            H-501,Western Cape,Cape Town Port,N
            H-502,only-two-columns
            """;

        HubCsvParser.ParseResult result =
                parser.parseWithStats(new StringReader(csv));

        assertEquals(2, result.hubs().size());
        assertEquals(1, result.invalidRowsDropped());
    }
}
