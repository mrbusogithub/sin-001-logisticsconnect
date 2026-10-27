package co.wethinkcode.logisticsconnect.ingestion;

import co.wethinkcode.logisticsconnect.model.Hub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Finds records describing the same hub and keeps one canonical record.
 */

public class HubDeduplicator {
    // Group records by real-world identity and retain a deterministic winner per group.
    public List<Hub> deduplicate(List<Hub> hubs) {

        // The map stores one record per identity and preserves insertion order.
        Map<String, Hub> uniqueHubs = new LinkedHashMap<>();

        for (Hub hub : hubs) {

            // A null key means there is not enough data to safely identify a duplicate.
            String key = buildIdentityKey(hub);

            // Keep incomplete records separate rather than merging them by guesswork.
            if (key == null) {
                // Replace the candidate only when the deterministic selection rule prefers it.
                uniqueHubs.put(
                        uniqueKeyForIncompleteRecord(hub, uniqueHubs.size()),
                        hub
                );
                continue;
            }

            // Check whether this real-world hub has already appeared in the file.
            Hub existing = uniqueHubs.get(key);

            if (existing == null) {
                // First occurrence for this identity; store it as the current candidate.
                uniqueHubs.put(key, hub);
            } else {
                // Replace the candidate only when the deterministic selection rule prefers it.
                uniqueHubs.put(
                        key,
                        chooseCanonical(existing, hub)
                );
            }
        }

        // Return the chosen records as a list for the REST endpoint.
        return new ArrayList<>(uniqueHubs.values());
    }

    /**
     * Builds the identity of a real-world sorting centre.
     *
     * Incomplete records are kept separate because there is not enough
     * reliable information to conclude that they represent the same hub.
     */
    private String buildIdentityKey(Hub hub) {
        String province = hub.getProvince();
        String sortingCenter = hub.getSortingCenter();

        // Do not merge records using blank or placeholder identity fields.
        if (isMissingValue(province) || isMissingValue(sortingCenter)) {
            return null;
        }

        // Case-insensitive keys prevent casing differences from hiding duplicates.
        return normalizeIdentityPart(province)
                + "|"
                + normalizeIdentityPart(sortingCenter);
    }

    /**
     * Makes identity comparisons insensitive to casing and repeated whitespace.
     */
    private String normalizeIdentityPart(String value) {
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    // Resolve conflicting duplicates without depending on input order or active status.
    private Hub chooseCanonical(Hub first, Hub second) {

        if (first.getHubId() == null) {
            return second;
        }

        if (second.getHubId() == null) {
            return first;
        }

        // Lexicographically smallest normalised hub ID is the documented tie-breaker.
        return Comparator.comparing(Hub::getHubId)
                .compare(first, second) <= 0
                ? first
                : second;
    }

    // Give incomplete records separate keys because they cannot safely be matched.
    private String uniqueKeyForIncompleteRecord(
            Hub hub,
            int index
    ) {
        return "incomplete-" + index + "-" + hub.getHubId();
    }

    /**
     * Identifies values that should not be used as reliable identity fields.
     */
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
