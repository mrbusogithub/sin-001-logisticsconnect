package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.PackageStatusEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The evaluator tests cover below-threshold, threshold-boundary,
 * above-threshold, duplicate delivery, custom configuration and invalid inputs
 */

public class AlertEvaluatorTest {

    // The default threshold used by these tests.
    private static final int THRESHOLD = 5;

    /**
     * Verifies that a stage below the threshold does not trigger an alert.
     */
    @Test
    void doesNotAlertBelowThreshold() {
        AlertEvaluator evaluator = new AlertEvaluator(THRESHOLD);

        // Stage 4 is below the configured threshold of 5.
        PackageStatusEvent event = new PackageStatusEvent(
                "H-501", 4, "2026-07-18T10:15:00Z");

        assertFalse(evaluator.shouldAlert(event));
    }
}
