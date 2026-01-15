package me.bechberger.jfrredact.engine;

import java.util.*;

/**
 * Container for patterns discovered during the discovery phase.
 * <p>
 * Stores values extracted from known patterns (like usernames from /Users/username)
 * that should be redacted in the redaction phase.
 */
public class DiscoveredPatterns {

    /**
     * Type of discovered pattern
     */
    public enum PatternType {
        USERNAME,
        HOSTNAME,
        EMAIL_LOCAL_PART,
        CUSTOM
    }

    /**
     * A discovered value with its type and occurrence count
     */
    public static class DiscoveredValue {
        private final String value;
        private final PatternType type;
        private final String customTypeName; // For CUSTOM type
        private int occurrences;

        public DiscoveredValue(String value, PatternType type) {
            this(value, type, null);
        }

        public DiscoveredValue(String value, PatternType type, String customTypeName) {
            this.value = value;
            this.type = type;
            this.customTypeName = customTypeName;
            this.occurrences = 1;
        }

        public void incrementOccurrences() {
            this.occurrences++;
        }

        public String getValue() { return value; }
        public PatternType getType() { return type; }
        public String getCustomTypeName() { return customTypeName; }
        public int getOccurrences() { return occurrences; }

        @Override
        public String toString() {
            if (type == PatternType.CUSTOM && customTypeName != null) {
                return String.format("%s (%s, %d occurrences)", value, customTypeName, occurrences);
            }
            return String.format("%s (%s, %d occurrences)", value, type, occurrences);
        }
    }

    // Map from normalized value to discovered value
    // Normalization depends on case sensitivity setting
    private final Map<String, DiscoveredValue> patterns = new HashMap<>();

    // Case sensitivity setting
    private final boolean caseSensitive;

    // Allowlist of values to never redact
    private final Set<String> allowlist;

    public DiscoveredPatterns(boolean caseSensitive, Collection<String> allowlist) {
        this.caseSensitive = caseSensitive;
        this.allowlist = new HashSet<>();
        for (String value : allowlist) {
            this.allowlist.add(normalize(value));
        }
    }

    /**
     * Normalize a value for storage/lookup based on case sensitivity setting
     */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return caseSensitive ? value : value.toLowerCase(Locale.ROOT);
    }

    /**
     * Add a discovered value
     */
    public void addValue(String value, PatternType type) {
        addValue(value, type, null);
    }

    /**
     * Add a discovered value with custom type name
     */
    public void addValue(String value, PatternType type, String customTypeName) {
        if (value == null || value.isEmpty()) {
            return;
        }

        String normalized = normalize(value);

        // Check allowlist
        if (allowlist.contains(normalized)) {
            return;
        }

        DiscoveredValue existing = patterns.get(normalized);
        if (existing != null) {
            existing.incrementOccurrences();
        } else {
            patterns.put(normalized, new DiscoveredValue(value, type, customTypeName));
        }
    }

    /**
     * Get all discovered values that meet the minimum occurrence threshold
     */
    public List<DiscoveredValue> getValues(int minOccurrences) {
        List<DiscoveredValue> result = new ArrayList<>();
        for (DiscoveredValue value : patterns.values()) {
            if (value.getOccurrences() >= minOccurrences) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * Get all discovered values (regardless of occurrence count)
     */
    public List<DiscoveredValue> getAllValues() {
        return new ArrayList<>(patterns.values());
    }

    /**
     * Check if a value has been discovered
     */
    public boolean contains(String value) {
        return patterns.containsKey(normalize(value));
    }

    /**
     * Get the discovered value for a given string
     */
    public DiscoveredValue get(String value) {
        return patterns.get(normalize(value));
    }

    /**
     * Get count of discovered values by type
     */
    public Map<PatternType, Integer> getCountByType(int minOccurrences) {
        Map<PatternType, Integer> counts = new EnumMap<>(PatternType.class);
        for (DiscoveredValue value : getValues(minOccurrences)) {
            counts.merge(value.getType(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Get total number of discovered values (meeting min occurrences)
     */
    public int getTotalCount(int minOccurrences) {
        return getValues(minOccurrences).size();
    }

    /**
     * Get total number of all discovered values (regardless of occurrences)
     */
    public int getTotalCount() {
        return patterns.size();
    }

    /**
     * Merge another DiscoveredPatterns into this one
     */
    public void merge(DiscoveredPatterns other) {
        for (DiscoveredValue value : other.getAllValues()) {
            String normalized = normalize(value.getValue());
            DiscoveredValue existing = patterns.get(normalized);
            if (existing != null) {
                for (int i = 0; i < value.getOccurrences(); i++) {
                    existing.incrementOccurrences();
                }
            } else {
                patterns.put(normalized, value);
            }
        }
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }
}