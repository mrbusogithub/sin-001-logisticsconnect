package co.wethinkcode.logisticsconnect.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Public message contract shared by the Delay Stage, Transit and AlertBot
 * applications. Keep the field names and validation rules consistent.
 */
public final class PackageStatusEvent {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String hubId;
    private final int stage;
    private final String timestamp;

    public PackageStatusEvent(String hubId, int stage, String timestamp) {
        // Use a canonical hub ID so all consumers deduplicate the same event.
        String normalizedHubId = hubId == null
                ? ""
                : hubId.trim().toUpperCase(Locale.ROOT);

        if (normalizedHubId.isBlank()) {
            throw new IllegalArgumentException("hubId must not be blank.");
        }

        if (stage < 0 || stage > 8) {
            throw new IllegalArgumentException(
                    "stage must be an integer between 0 and 8.");
        }

        if (timestamp == null || timestamp.isBlank()) {
            throw new IllegalArgumentException(
                    "timestamp must not be blank.");
        }

        try {
            // Reject timestamps that are not parseable ISO-8601 instants.
            this.timestamp = Instant.parse(timestamp).toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "timestamp must be a valid ISO-8601 instant.",
                    exception);
        }

        this.hubId = normalizedHubId;
        this.stage = stage;
    }

    /**
     * Creates a new valid event for a real delay-stage change.
     */
    public static PackageStatusEvent create(String hubId, int stage) {
        return new PackageStatusEvent(
                hubId,
                stage,
                Instant.now().toString());
    }

    /**
     * Serializes only the three public contract fields to JSON.
     */
    public String toJson() throws JsonProcessingException {
        return JSON.writeValueAsString(this);
    }

    /**
     * Strictly validates untrusted JSON instead of allowing missing fields
     * to become Java's default values.
     */
    public static PackageStatusEvent fromJson(String payload)
            throws IOException {

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException(
                    "Event payload must not be blank.");
        }

        JsonNode root = JSON.readTree(payload);

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "Event payload must be a JSON object.");
        }

        JsonNode hubIdNode = root.get("hubId");
        JsonNode stageNode = root.get("stage");
        JsonNode timestampNode = root.get("timestamp");

        if (hubIdNode == null || !hubIdNode.isTextual()) {
            throw new IllegalArgumentException(
                    "hubId must be a JSON string.");
        }

        if (stageNode == null
                || !stageNode.isIntegralNumber()
                || !stageNode.canConvertToInt()) {
            throw new IllegalArgumentException(
                    "stage must be an integer between 0 and 8.");
        }

        if (timestampNode == null || !timestampNode.isTextual()) {
            throw new IllegalArgumentException(
                    "timestamp must be an ISO-8601 string.");
        }

        // The constructor performs the common range and timestamp checks.
        return new PackageStatusEvent(
                hubIdNode.asText(),
                stageNode.asInt(),
                timestampNode.asText());
    }

    /**
     * A stable key for suppressing exact duplicate deliveries.
     */
    public String eventKey() {
        return hubId + "|" + stage + "|" + timestamp;
    }

    public String getHubId() {
        return hubId;
    }

    public int getStage() {
        return stage;
    }

    public String getTimestamp() {
        return timestamp;
    }
}