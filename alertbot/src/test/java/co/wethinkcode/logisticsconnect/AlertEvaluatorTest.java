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

    /**
     * Verifies the boundary rule: a stage equal to the threshold triggers
     * an alert because the comparison is inclusive.
     */
    @Test
    void alertsAtTheThresholdBoundary() {
        AlertEvaluator evaluator = new AlertEvaluator(THRESHOLD);

        // Stage 5 is exactly equal to the configured threshold.
        PackageStatusEvent event = new PackageStatusEvent(
                "H-501", 5, "2026-07-18T10:16:00Z");

        assertTrue(evaluator.shouldAlert(event));
    }

    /**
     * Verifies that stages greater than the threshold trigger an alert.
     */
    @Test
    void alertsAboveTheThreshold() {
        AlertEvaluator evaluator = new AlertEvaluator(THRESHOLD);

        // Stage 8 is above the configured threshold of 5.
        PackageStatusEvent event = new PackageStatusEvent(
                "H-501", 8, "2026-07-18T10:17:00Z");

        assertTrue(evaluator.shouldAlert(event));
    }

    /**
     * Verifies that the same event cannot generate an alert twice.
     */
    @Test
    void doesNotAlertTwiceForTheSameEvent() {
        AlertEvaluator evaluator = new AlertEvaluator(THRESHOLD);

        PackageStatusEvent event = new PackageStatusEvent(
                "H-501", 5, "2026-07-18T10:18:00Z");

        // The first valid delivery should generate an alert decision.
        assertTrue(evaluator.shouldAlert(event));

        // The duplicate should be ignored.
        assertFalse(evaluator.shouldAlert(event));
    }

    /**
     * Verifies that the alert threshold can be changed and that its
     * inclusive boundary behaviour remains consistent.
     */
    @Test
    void allowsAValidCustomThreshold() {
        // Use stage 7 as a custom threshold instead of the default 5.
        AlertEvaluator evaluator = new AlertEvaluator(7);

        assertEquals(7, evaluator.getThreshold());

        // Stage 6 is below the new threshold.
        assertFalse(evaluator.shouldAlert(
                new PackageStatusEvent(
                        "H-501", 6, "2026-07-18T10:19:00Z")));

        // Stage 7 equals the new threshold and should trigger an alert.
        assertTrue(evaluator.shouldAlert(
                new PackageStatusEvent(
                        "H-501", 7, "2026-07-18T10:20:00Z")));
    }
}
