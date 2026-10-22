package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HubServiceAppTest {

    @Test
    void normalizesHubIdsByTrimmingAndUppercasing() {
        // IDs with different casing and spaces should match the same hub.
        assertEquals("H-501",
                HubServiceApp.normalizeHubId("  h-501 "));
    }
}
