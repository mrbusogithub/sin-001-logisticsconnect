package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DelayStageServiceAppTest {

    @Test
    void acceptsStageZero() {
        // Stage zero is the default when no delay has been recorded.
        assertTrue(DelayStageServiceApp.isValidStage(0));
    }
}
