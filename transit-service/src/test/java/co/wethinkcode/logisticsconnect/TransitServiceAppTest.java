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

    @Test
    void supportsCustomEtaConfiguration() {
        // The calculation should also work with configuration other than defaults.
        assertEquals(95,
                TransitServiceApp.calculateEtaMinutes(50, 15, 3));
    }

    @Test
    void rejectsNegativeBaseDuration() {
        // A negative base duration is not a valid ETA configuration.
        assertThrows(IllegalArgumentException.class,
                () -> TransitServiceApp.calculateEtaMinutes(-1, 15, 2));
    }

    @Test
    void rejectsNegativeMinutesPerStage() {
        // Each additional stage must not reduce the ETA.
        assertThrows(IllegalArgumentException.class,
                () -> TransitServiceApp.calculateEtaMinutes(120, -1, 2));
    }

    @Test
    void rejectsStageOutsideRange() {
        // The calculation must reject a stage the system does not support.
        assertThrows(IllegalArgumentException.class,
                () -> TransitServiceApp.calculateEtaMinutes(120, 15, 9));
    }

    @Test
    void rejectsIntegerOverflow() {
        // Large values must raise an error instead of producing a wrong ETA.
        assertThrows(ArithmeticException.class,
                () -> TransitServiceApp.calculateEtaMinutes(
                        Integer.MAX_VALUE, 15, 1));
    }
}
