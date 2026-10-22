package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DelayStageServiceAppTest {

    @Test
    void acceptsStageZero() {
        // Stage zero is the default when no delay has been recorded.
        assertTrue(DelayStageServiceApp.isValidStage(0));
    }

    @Test
    void acceptsStageEight() {
        // Eight is the highest supported delay stage.
        assertTrue(DelayStageServiceApp.isValidStage(8));
    }

    @Test
    void acceptsStagesWithinRange() {
        // Every whole-number stage from zero through eight is valid.
        for (int stage = 0; stage <= 8; stage++) {
            assertTrue(DelayStageServiceApp.isValidStage(stage));
        }
    }

    @Test
    void rejectsNegativeStage() {
        // A negative stage is outside the permitted range.
        assertFalse(DelayStageServiceApp.isValidStage(-1));
    }

    @Test
    void rejectsStageAboveEight() {
        // Values greater than eight must not be accepted.
        assertFalse(DelayStageServiceApp.isValidStage(9));
    }
}
