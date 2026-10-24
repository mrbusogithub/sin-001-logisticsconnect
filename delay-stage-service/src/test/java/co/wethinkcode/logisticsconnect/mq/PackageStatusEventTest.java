package co.wethinkcode.logisticsconnect.mq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PackageStatusEventTest {

    /**
     * Verifies that a valid event can be converted to JSON and restored.
     * The hub ID should also be normalized to uppercase.
     */
    @Test
    void serializesAndDeserializesTheContract() throws Exception {
        // Include whitespace and lowercase characters to test normalization.
        PackageStatusEvent original = new PackageStatusEvent(
                " h-501 ",
                5,
                "2026-07-18T10:15:00Z");

        // Convert the event to JSON and then deserialize it.
        String json = original.toJson();
        PackageStatusEvent restored = PackageStatusEvent.fromJson(json);

        // Check that all contract fields retain their expected values.
        assertEquals("H-501", restored.getHubId());
        assertEquals(5, restored.getStage());
        assertEquals(
                "2026-07-18T10:15:00Z",
                restored.getTimestamp());

        // Ensure serialization does not change the event's deduplication key.
        assertEquals(original.eventKey(), restored.eventKey());
    }

    /**
     * Verifies that malformed JSON is rejected instead of creating an event.
     */
    @Test
    void rejectsMalformedJson() {
        assertThrows(
                Exception.class,
                // This string is not valid JSON.
                () -> PackageStatusEvent.fromJson("{not-json"));
    }

    /**
     * Verifies that an event cannot omit a required contract field.
     */
    @Test
    void rejectsMissingRequiredFields() {
        assertThrows(
                IllegalArgumentException.class,
                // The timestamp field is missing from this JSON message.
                () -> PackageStatusEvent.fromJson(
                        "{\"hubId\":\"H-501\",\"stage\":5}"));
    }

    /**
     * Verifies that stages outside the supported range of 0 through 8
     * are rejected.
     */
    @Test
    void rejectsStagesOutsideTheContractRange() {
        assertThrows(
                IllegalArgumentException.class,
                // Stage 9 is outside the contract's allowed range.
                () -> PackageStatusEvent.fromJson(
                        """
                        {
                          "hubId": "H-501",
                          "stage": 9,
                          "timestamp": "2026-07-18T10:15:00Z"
                        }
                        """));
    }

    /**
     * Verifies that a timestamp must be a valid ISO-8601 instant.
     */
    @Test
    void rejectsInvalidTimestamps() {
        assertThrows(
                IllegalArgumentException.class,
                // "not-a-date" cannot be parsed as a valid timestamp.
                () -> PackageStatusEvent.fromJson(
                        """
                        {
                          "hubId": "H-501",
                          "stage": 5,
                          "timestamp": "not-a-date"
                        }
                        """));
    }
}
