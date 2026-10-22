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

    @Test
    void calculatesEtaForStageThree() {
        // Three stages add 45 minutes to the 120-minute base duration.
        assertEquals(165,
                TransitServiceApp.calculateEtaMinutes(120, 15, 3));
    }

    @Test
    void calculatesEtaForMaximumStage() {
        // Eight stages add 120 minutes, giving a total of 240 minutes.
        assertEquals(240,
                TransitServiceApp.calculateEtaMinutes(120, 15, 8));
    }
}
