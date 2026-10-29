package co.wethinkcode.logisticsconnect.ingestion;

import co.wethinkcode.logisticsconnect.model.Hub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds records describing the same hub and keeps one canonical record.
 */
public class HubDeduplicator {

    /**
     * Holds the surviving records and the number of duplicates removed.
     */
    public record DeduplicationResult(
            List<Hub> hubs,
            int duplicatesRemoved
    ) {
        public DeduplicationResult {
            hubs = List.copyOf(hubs);
        }
    }

    // Preserve the existing method for callers that only need the hubs.
    public List<Hub> deduplicate(List<Hub> hubs) {
        return deduplicateWithStats(hubs).hubs();
    }

    // Deduplicate records and count every record merged into an existing group.
    public DeduplicationResult deduplicateWithStats(List<Hub> hubs) {
        Map<String, Hub> uniqueHubs = new LinkedHashMap<>();
        int duplicatesRemoved = 0;

        for (Hub hub : hubs) {
            String key = buildIdentityKey(hub);

            // Do not merge records when their identity is incomplete.
            if (key == null) {
                uniqueHubs.put(
                        uniqueKeyForIncompleteRecord(
                                hub,
                                uniqueHubs.size()
                        ),
                        hub
                );
                continue;
            }

            Hub existing = uniqueHubs.get(key);

            if (existing == null) {
                uniqueHubs.put(key, hub);
            } else {
                // This record matches a hub that is already in the map.
                duplicatesRemoved++;

                // Keep the same deterministic winner regardless of input order.
                uniqueHubs.put(
                        key,
                        chooseCanonical(existing, hub)
                );
            }
        }

        return new DeduplicationResult(
                new ArrayList<>(uniqueHubs.values()),
                duplicatesRemoved
        );
    }

    // Province and sorting-centre name form the identity of a complete hub.
    private String buildIdentityKey(Hub hub) {
        if (isMissingValue(hub.getProvince())
                || isMissingValue(hub.getSortingCenter())) {
            return null;
        }

        return normalizeIdentityPart(hub.getProvince())
                + "|"
                + normalizeIdentityPart(hub.getSortingCenter());
    }

    // Make casing and repeated spaces irrelevant when comparing identities.
    private String normalizeIdentityPart(String value) {
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    // Select a winner without relying on the input order or active status.
    private Hub chooseCanonical(Hub first, Hub second) {
        if (first.getHubId() == null) {
            return second;
        }

        if (second.getHubId() == null) {
            return first;
        }

        // The lexicographically smallest normalised ID wins.
        return Comparator.comparing(Hub::getHubId)
                .compare(first, second) <= 0
                ? first
                : second;
    }

    // Keep incomplete records separate instead of guessing their identity.
    private String uniqueKeyForIncompleteRecord(Hub hub, int index) {
        return "incomplete-" + index + "-" + hub.getHubId();
    }

    // Recognise blank and placeholder values as missing identity information.
    private boolean isMissingValue(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "n/a", "na", "tbd", "unknown", "-", "nan" -> true;
            default -> false;
        };
    }
}