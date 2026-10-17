package co.wethinkcode.logisticsconnect.ingestion;

import co.wethinkcode.logisticsconnect.model.Hub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // Province plus sorting-centre name is the chosen identity for a complete record.
    private String buildIdentityKey(Hub hub) {
        if (hub.getProvince() == null ||
                hub.getSortingCenter() == null) {
            return null;
        }

        // Separator keeps the two identity fields distinct in the composite key.
        return hub.getProvince() + "|" + hub.getSortingCenter();
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
}
