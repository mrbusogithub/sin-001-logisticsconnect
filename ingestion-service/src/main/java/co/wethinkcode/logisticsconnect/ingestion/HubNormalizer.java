package co.wethinkcode.logisticsconnect.ingestion;

import co.wethinkcode.logisticsconnect.model.Hub;

import java.util.Locale;

/**
 * Converts inconsistent text values into one predictable, canonical format.
 */

public class HubNormalizer {

    // Return a new Hub with cleaned text; do not mutate the input object.
    public Hub normalize(Hub hub) {
        // Build a clean copy so the original parsed record is not changed.
        return new Hub(
                normalizeHubId(hub.getHubId()),
                normalizeProvince(hub.getProvince()),
                normalizeText(hub.getSortingCenter()),
                hub.getActive()
        );
    }

    // IDs are trimmed and uppercased so casing differences do not create separate IDs.
    private String normalizeHubId(String value) {
        String normalized = normalizeText(value);

        if (normalized == null) {
            return null;
        }

        // Locale.ROOT makes ID conversion consistent regardless of machine locale.
        return normalized.toUpperCase(Locale.ROOT);
    }

    // Map known province spelling/casing variants to official canonical names.
    private String normalizeProvince(String value) {
        String normalized = normalizeText(value);

        if (normalized == null) {
            return null;
        }

        // Compare province variants case-insensitively in a locale-independent way.
        String lower = normalized.toLowerCase(Locale.ROOT);

        return switch (lower) {
            case "gauteng" -> "Gauteng";
            case "western cape" -> "Western Cape";
            case "eastern cape" -> "Eastern Cape";
            case "free state" -> "Free State";

            // Accept spelling and hyphen variations of the same province.
            case "kwazulu natal",
                 "kwa-zulu natal",
                 "kwazulu-natal",
                 "kwa-zulu-natal" -> "KwaZulu-Natal";

            case "limpopo" -> "Limpopo";
            case "mpumalanga" -> "Mpumalanga";
            case "north west" -> "North West";
            case "northern cape" -> "Northern Cape";

            // Keep a consistent format for other province names.
            default -> toTitleCase(normalized);
        };
    }

    /**
     * Trims whitespace, collapses repeated spaces and converts known
     * placeholders into null so they are not treated as real data.
     */
    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        // Remove padding and normalize internal whitespace first.
        String normalized = value
                .trim()
                .replaceAll("\\s+", " ");

        if (normalized.isBlank()) {
            return null;
        }

        // Missing values must not become real province or hub names.
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "n/a", "na", "tbd", "unknown", "-", "nan" -> null;
            default -> normalized;
        };
    }

    // Fallback for an unlisted province: capitalise the first letter of each word.
    private String toTitleCase(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split(" ");

        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(" ");
            }

            result.append(
                    Character.toUpperCase(word.charAt(0))
            );

            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }

        return result.toString();
    }
}
