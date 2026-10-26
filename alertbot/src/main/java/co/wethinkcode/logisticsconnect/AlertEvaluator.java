package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.PackageStatusEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Makes the alert decision independently of JMS and HTTP.
 *
 * That separation makes threshold boundaries and duplicate handling easy
 * to test without starting the web server or a real message broker.
 */

public final class AlertEvaluator {

    private final int threshold;

    // Prevent the same event from generating more than one alert in this
    // running AlertBot instance.
    private final Set<String> processedEventKeys = new HashSet<>();

    public AlertEvaluator(int threshold) {
        if (threshold < 0 || threshold > 8) {
            throw new IllegalArgumentException(
                    "Alert threshold must be between 0 and 8.");
        }

        this.threshold = threshold;
    }

    /**
     * Returns true only for a new event at or above the configured threshold.
     */
    public synchronized boolean shouldAlert(PackageStatusEvent event) {
        if (event == null) {
            return false;
        }

        if (!processedEventKeys.add(event.eventKey())) {
            return false;
        }

        // The boundary is inclusive: a stage equal to the threshold alerts.
        return event.getStage() >= threshold;
    }

    public int getThreshold() {
        return threshold;
    }
}
