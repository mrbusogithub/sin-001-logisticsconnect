package co.wethinkcode.logisticsconnect.mq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PackageStatusEventProcessorTest {

    /**
     * Verifies that a valid event updates the stored stage and timestamp.
     */
    @Test
    void appliesValidEvent() {
        PackageStatusEventProcessor processor =
                new PackageStatusEventProcessor();

        // Create a valid event for a hub.
        PackageStatusEvent event = new PackageStatusEvent(
                "H-501", 5, "2026-07-18T10:15:00Z");

        // The processor should report that the event was applied.
        assertEquals(
                PackageStatusEventProcessor.Result.APPLIED,
                processor.process(event));

        // Hub ID lookups should be case-insensitive.
        assertEquals(5, processor.stageFor("h-501"));

        // The timestamp should be saved with the event's stage.
        assertEquals(
                "2026-07-18T10:15:00Z",
                processor.timestampFor("H-501"));
    }

    /**
     * Verifies that processing the exact same event twice does not update
     * the state a second time.
     */
    @Test
    void ignoresAnExactDuplicateEvent() {
        PackageStatusEventProcessor processor =
                new PackageStatusEventProcessor();

        // Both deliveries represent the same hub, stage and timestamp.
        PackageStatusEvent event = new PackageStatusEvent(
                "H-501", 5, "2026-07-18T10:15:00Z");

        // The first delivery should update the state.
        assertEquals(
                PackageStatusEventProcessor.Result.APPLIED,
                processor.process(event));

        // The second delivery should be identified as a duplicate.
        assertEquals(
                PackageStatusEventProcessor.Result.DUPLICATE,
                processor.process(event));

        // The valid stage remains unchanged after the duplicate.
        assertEquals(5, processor.stageFor("H-501"));
    }

    /**
     * Verifies that an older event cannot overwrite a newer event.
     */
    @Test
    void ignoresAnOlderEventForTheSameHub() {
        PackageStatusEventProcessor processor =
                new PackageStatusEventProcessor();

        // This event has the newer timestamp and should be applied first.
        PackageStatusEvent newer = new PackageStatusEvent(
                "H-501", 6, "2026-07-18T10:16:00Z");

        // This event arrived later but describes an older state.
        PackageStatusEvent older = new PackageStatusEvent(
                "H-501", 3, "2026-07-18T10:15:00Z");

        assertEquals(
                PackageStatusEventProcessor.Result.APPLIED,
                processor.process(newer));

        // A stale event should not replace the newer state.
        assertEquals(
                PackageStatusEventProcessor.Result.STALE,
                processor.process(older));

        // The stage must remain at the value from the newer event.
        assertEquals(6, processor.stageFor("H-501"));
    }

    /**
     * Verifies that malformed JSON is rejected without changing Transit state.
     */
    @Test
    void malformedJsonDoesNotChangeState() {
        PackageStatusEventProcessor processor =
                new PackageStatusEventProcessor();

        // This string cannot be parsed as a valid JSON event.
        assertEquals(
                PackageStatusEventProcessor.Result.INVALID,
                processor.processJson("{not-json"));

        // No stage should have been stored for this hub.
        assertEquals(0, processor.stageFor("H-501"));

        // No hub should be tracked after the invalid event.
        assertEquals(0, processor.trackedHubCount());
    }

    /**
     * Verifies that missing required fields are handled safely.
     */
    @Test
    void missingFieldsDoNotChangeState() {
        PackageStatusEventProcessor processor =
                new PackageStatusEventProcessor();

        // The timestamp is missing, so this is not a valid contract message.
        assertEquals(
                PackageStatusEventProcessor.Result.INVALID,
                processor.processJson(
                        "{\"hubId\":\"H-501\",\"stage\":5}"));

        // Invalid messages must not create or update hub state.
        assertEquals(0, processor.trackedHubCount());
    }

    /**
     * Verifies that a stage outside the allowed range is rejected.
     */
    @Test
    void invalidStageDoesNotChangeState() {
        PackageStatusEventProcessor processor =
                new PackageStatusEventProcessor();

        // Stage 99 is outside the documented range of 0 through 8.
        assertEquals(
                PackageStatusEventProcessor.Result.INVALID,
                processor.processJson(
                        """
                        {
                          "hubId": "H-501",
                          "stage": 99,
                          "timestamp": "2026-07-18T10:15:00Z"
                        }
                        """));

        // The rejected event must not be stored.
        assertEquals(0, processor.trackedHubCount());
    }

    /**
     * Verifies that an event with an invalid timestamp cannot update state.
     */
    @Test
    void invalidTimestampDoesNotChangeState() {
        PackageStatusEventProcessor processor =
                new PackageStatusEventProcessor();

        // "not-a-date" is not a valid ISO-8601 instant.
        assertEquals(
                PackageStatusEventProcessor.Result.INVALID,
                processor.processJson(
                        """
                        {
                          "hubId": "H-501",
                          "stage": 5,
                          "timestamp": "not-a-date"
                        }
                        """));

        // The processor must ignore the invalid event.
        assertEquals(0, processor.trackedHubCount());
    }
}
