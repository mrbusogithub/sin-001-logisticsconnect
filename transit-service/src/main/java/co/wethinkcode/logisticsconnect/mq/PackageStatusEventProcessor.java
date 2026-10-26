package co.wethinkcode.logisticsconnect.mq;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Applies validated package-status events to Transit-owned local state.
 *
 * Transit no longer fetches the latest stage from Delay Stage over REST.
 */

public class PackageStatusEventProcessor {

    public enum Result {
        APPLIED,
        DUPLICATE,
        STALE,
        INVALID
    }

    private final ConcurrentMap<String, Integer> stages =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, String> latestTimestamps =
            new ConcurrentHashMap<>();

    // Keeps exact duplicate deliveries from being applied twice.
    private final Set<String> processedEventKeys = new HashSet<>();

    /**
     * Apply one event in timestamp order.
     *
     * synchronized protects the deduplication set and the multi-map update.
     */
    public synchronized Result process(PackageStatusEvent event) {
        if (event == null) {
            return Result.INVALID;
        }

        if (!processedEventKeys.add(event.eventKey())) {
            return Result.DUPLICATE;
        }

        String previousTimestamp =
                latestTimestamps.get(event.getHubId());

        // A delayed older message must not roll state backwards.
        if (previousTimestamp != null
                && !Instant.parse(event.getTimestamp()).isAfter(
                Instant.parse(previousTimestamp))) {
            return Result.STALE;
        }

        stages.put(event.getHubId(), event.getStage());
        latestTimestamps.put(event.getHubId(), event.getTimestamp());

        System.out.printf(
                "[Transit] Applied stage %d for hub %s at %s%n",
                event.getStage(),
                event.getHubId(),
                event.getTimestamp());

        return Result.APPLIED;
    }

    /**
     * Test seam for malformed-payload scenarios. The real JMS consumer uses
     * the same event parser before calling process().
     */
    public Result processJson(String payload) {
        try {
            return process(PackageStatusEvent.fromJson(payload));
        } catch (IOException | IllegalArgumentException exception) {
            return Result.INVALID;
        }
    }

    /**
     * If no event has been received yet, stage zero is the documented default.
     */
    public int stageFor(String hubId) {
        return stages.getOrDefault(normalizeHubId(hubId), 0);
    }

    public String timestampFor(String hubId) {
        return latestTimestamps.getOrDefault(
                normalizeHubId(hubId),
                "");
    }

    public int trackedHubCount() {
        return stages.size();
    }

    private static String normalizeHubId(String hubId) {
        return hubId == null
                ? ""
                : hubId.trim().toUpperCase(Locale.ROOT);
    }
}
