package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TransitServiceAppTest {

    @Test
    void calculatesEtaForStageZero() {
        // No delay means the ETA is just the base duration.
        assertEquals(120,
                TransitServiceApp.calculateEtaMinutes(120, 15, 0));
    }
}
