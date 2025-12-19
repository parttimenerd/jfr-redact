package me.bechberger.jfrredact.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for automatic pattern discovery.
 * <p>
 * Discovery allows the tool to automatically find sensitive values in a first pass
 * (e.g., extracting usernames from /Users/username paths) and then redact all
 * occurrences of those values in a second pass.
 */
public class DiscoveryConfig {

    /**
     * Discovery mode controls how pattern discovery is performed.
     */
    public enum DiscoveryMode {
        /**
         * No pattern discovery - standard single-pass redaction only.
         * Fastest, but won't catch values that aren't explicitly in configured patterns.
         */
        @JsonProperty("none")
        NONE,

        /**
         * On-the-fly discovery - discover patterns during processing and redact
         * subsequent occurrences. Only occurrences AFTER first discovery are redacted.
         * Good balance between speed and effectiveness.
         */
        @JsonProperty("fast")
        FAST,

        /**
         * Two-pass discovery (default) - read entire input twice.
         * First pass discovers all patterns, second pass redacts everything.
         * Most thorough but uses more I/O (reads file twice).
         */
        @JsonProperty("default")
        TWO_PASS
    }

    @JsonProperty("mode")
    private DiscoveryMode mode = DiscoveryMode.TWO_PASS;

    /**
     * Custom extraction patterns for discovering values.
     */
    @JsonProperty("custom_extractions")
    private List<CustomExtractionConfig> customExtractions = new ArrayList<>();

    /**
     * Property-based extraction for discovering values from JFR event properties.
     * Extracts values based on property key names (e.g., "user.name" -> extract username).
     */
    @JsonProperty("property_extractions")
    private List<PropertyExtractionConfig> propertyExtractions = new ArrayList<>();

    /**
     * Configuration for custom value extraction.
     */
    public static class CustomExtractionConfig {
        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        /**
         * Regex pattern to match. The value to extract comes from the capture group.
         */
        @JsonProperty("pattern")
        private String pattern;

        /**
         * Which capture group contains the value to extract (0 = entire match, 1 = first group, etc.)
         */
        @JsonProperty("capture_group")
        private int captureGroup = 0;

        /**
         * Which pattern type this belongs to (USERNAME, HOSTNAME, EMAIL_LOCAL_PART, or CUSTOM).
         * Determines how the discovered values are categorized.
         */
        @JsonProperty("type")
        private String type = "CUSTOM";

        /**
         * Case sensitivity for discovered value matching.
         * If false, "Bob", "bob", and "BOB" are treated as the same value.
         */
        @JsonProperty("case_sensitive")
        private boolean caseSensitive = false;

        /**
         * Minimum occurrences required before a discovered value is redacted.
         * Helps prevent false positives from generic/common values.
         */
        @JsonProperty("min_occurrences")
        private int minOccurrences = 1;

        /**
         * Whitelist of values that should never be discovered/redacted by this pattern.
         * Useful for common/generic values.
         */
        @JsonProperty("whitelist")
        private List<String> whitelist = new ArrayList<>();

        @JsonProperty("enabled")
        private boolean enabled = true;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }

        public int getCaptureGroup() { return captureGroup; }
        public void setCaptureGroup(int captureGroup) { this.captureGroup = captureGroup; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isCaseSensitive() { return caseSensitive; }
        public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

        public int getMinOccurrences() { return minOccurrences; }
        public void setMinOccurrences(int minOccurrences) { this.minOccurrences = Math.max(1, minOccurrences); }

        public List<String> getWhitelist() { return whitelist; }
        public void setWhitelist(List<String> whitelist) { this.whitelist = whitelist; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * Configuration for extracting values from JFR event properties.
     * Example: Extract username from property with key "user.name"
     */
    public static class PropertyExtractionConfig {
        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        /**
         * Property key pattern to match (supports regex).
         * For direct field matching: matches the field name
         * For key-value matching: matches the value of the key property
         * Examples: "user\\.name", "hostname", ".*\\.user"
         */
        @JsonProperty("key_pattern")
        private String keyPattern;

        /**
         * Name of the property that contains the key in key-value pair events.
         * Default: "key"
         * Example: If your event has "propertyName" and "propertyValue", set this to "propertyName"
         */
        @JsonProperty("key_property_pattern")
        private String keyPropertyPattern = "key";

        /**
         * Pattern to match the value content (supports regex).
         * Only used for key-value pair matching.
         * Default: ".*" (matches any value)
         * Example: ".*@.*\\.com" to only extract email-like values
         */
        @JsonProperty("value_pattern")
        private String valuePattern = ".*";

        /**
         * Name of the property that contains the value in key-value pair events.
         * Default: "value"
         * Example: If your event has "propertyName" and "propertyValue", set this to "propertyValue"
         */
        @JsonProperty("value_property_pattern")
        private String valuePropertyPattern = "value";

        /**
         * Optional: Event type filter (regex). If specified, only extract from matching event types.
         * If null/empty, extract from all events.
         * Examples: "jdk\\..*", "my\\.app\\..*"
         */
        @JsonProperty("event_type_filter")
        private String eventTypeFilter;

        /**
         * Which pattern type this belongs to (USERNAME, HOSTNAME, EMAIL_LOCAL_PART, or CUSTOM).
         */
        @JsonProperty("type")
        private String type = "CUSTOM";

        /**
         * Case sensitivity for discovered value matching.
         */
        @JsonProperty("case_sensitive")
        private boolean caseSensitive = false;

        /**
         * Minimum occurrences required before a discovered value is redacted.
         */
        @JsonProperty("min_occurrences")
        private int minOccurrences = 1;

        /**
         * Whitelist of values that should never be discovered/redacted.
         */
        @JsonProperty("whitelist")
        private List<String> whitelist = new ArrayList<>();

        @JsonProperty("enabled")
        private boolean enabled = true;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getKeyPattern() { return keyPattern; }
        public void setKeyPattern(String keyPattern) { this.keyPattern = keyPattern; }

        public String getKeyPropertyPattern() { return keyPropertyPattern; }
        public void setKeyPropertyPattern(String keyPropertyPattern) { this.keyPropertyPattern = keyPropertyPattern; }

        public String getValuePattern() { return valuePattern; }
        public void setValuePattern(String valuePattern) { this.valuePattern = valuePattern; }

        public String getValuePropertyPattern() { return valuePropertyPattern; }
        public void setValuePropertyPattern(String valuePropertyPattern) { this.valuePropertyPattern = valuePropertyPattern; }

        public String getEventTypeFilter() { return eventTypeFilter; }
        public void setEventTypeFilter(String eventTypeFilter) { this.eventTypeFilter = eventTypeFilter; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isCaseSensitive() { return caseSensitive; }
        public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

        public int getMinOccurrences() { return minOccurrences; }
        public void setMinOccurrences(int minOccurrences) { this.minOccurrences = Math.max(1, minOccurrences); }

        public List<String> getWhitelist() { return whitelist; }
        public void setWhitelist(List<String> whitelist) { this.whitelist = whitelist; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    // Getters and setters
    public DiscoveryMode getMode() { return mode; }
    public void setMode(DiscoveryMode mode) { this.mode = mode; }

    public List<CustomExtractionConfig> getCustomExtractions() { return customExtractions; }
    public void setCustomExtractions(List<CustomExtractionConfig> customExtractions) {
        this.customExtractions = customExtractions;
    }

    public List<PropertyExtractionConfig> getPropertyExtractions() { return propertyExtractions; }
    public void setPropertyExtractions(List<PropertyExtractionConfig> propertyExtractions) {
        this.propertyExtractions = propertyExtractions;
    }

    public boolean isEnabled() {
        return mode != DiscoveryMode.NONE;
    }

    /**
     * Convenience method to enable/disable discovery.
     * @param enabled If true, sets mode to FAST; if false, sets mode to NONE
     */
    public void setEnabled(boolean enabled) {
        this.mode = enabled ? DiscoveryMode.FAST : DiscoveryMode.NONE;
    }

    /**
     * Set the default minimum occurrences for all extraction patterns.
     * This is a convenience method for testing.
     * If no extraction patterns exist, creates a default word-discovery pattern.
     */
    public void setMinOccurrencesDefault(int minOccurrences) {
        // Update existing patterns
        for (CustomExtractionConfig config : customExtractions) {
            config.setMinOccurrences(minOccurrences);
        }
        for (PropertyExtractionConfig config : propertyExtractions) {
            config.setMinOccurrences(minOccurrences);
        }
    }

    /**
     * Set case sensitivity for all extraction patterns.
     * This is a convenience method for testing.
     */
    public void setCaseSensitive(boolean caseSensitive) {
        for (CustomExtractionConfig config : customExtractions) {
            config.setCaseSensitive(caseSensitive);
        }
        for (PropertyExtractionConfig config : propertyExtractions) {
            config.setCaseSensitive(caseSensitive);
        }
    }
}