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
}
