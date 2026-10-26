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
}
