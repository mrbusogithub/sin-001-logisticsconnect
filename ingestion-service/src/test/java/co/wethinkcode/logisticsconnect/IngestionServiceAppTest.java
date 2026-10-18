package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.model.Hub;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngestionServiceAppTest {

    @Test
    void baselineTestShouldPass() {
        assertTrue(true);
    }

    @Test
    // Verify the expected behaviour for this scenario.
    void shouldLoadCleanedHubData() {

        List<Hub> hubs =
                IngestionServiceApp.loadHubs();

        assertFalse(hubs.isEmpty());

        assertTrue(
                hubs.stream()
                        .anyMatch(hub ->
                                "H-500".equals(hub.getHubId()))
        );
    }
}
