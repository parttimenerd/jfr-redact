package me.bechberger.jfrredact.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for pseudonymization.
 */
public class PseudonymizationConfig {

    @JsonProperty("enabled")
    private boolean enabled = false;

    /**
     * Pseudonymization mode:
     * - "hash" - Hash-based (stateless, deterministic, default)
     * - "counter" - Simple counter (stateful, requires hash map)
     * - "realistic" - Realistic alternatives
     */
    @JsonProperty("mode")
    private String mode = "hash";

    @JsonProperty("format")
    private String format = "redacted";

    @JsonProperty("custom_prefix")
    private String customPrefix = "<redacted:";

    @JsonProperty("custom_suffix")
    private String customSuffix = ">";

    @JsonProperty("hash_length")
    private int hashLength = 8;

    /**
     * Hash algorithm: SHA-256, SHA-1, MD5
     * Only used when mode is "hash"
     */
    @JsonProperty("hash_algorithm")
    private String hashAlgorithm = "SHA-256";

    @JsonProperty("scope")
    private ScopeConfig scope = new ScopeConfig();

    /**
     * Custom replacements for specific values.
     * Maps original values to their replacements.
     * Example:
     *   "johndoe" -> "alice"
     *   "/home/johndoe" -> "/home/testuser"
     *   "admin@company.com" -> "user@example.com"
     */
    @JsonProperty("replacements")
    private Map<String, String> replacements = new HashMap<>();

    /**
     * Pattern-based replacement generators.
     * Maps pattern names to regex generation patterns.
     * Used when pseudonymization mode is "realistic".
     *
     * Examples:
     *   "ssh_hosts" -> "host[0-9]{2}\\.example\\.com"
     *   "ip_addresses" -> "10\\.0\\.[0-9]{1,3}\\.[0-9]{1,3}"
     *   "usernames" -> "user[0-9]{3}"
     *   "temp_files" -> "temp_[a-z]{8}"
     */
    @JsonProperty("pattern_generators")
    private Map<String, String> patternGenerators = new HashMap<>();

    /**
     * Scope configuration for pseudonymization
     */
    public static class ScopeConfig {
        @JsonProperty("properties")
        private boolean properties = true;

        @JsonProperty("strings")
        private boolean strings = true;

        @JsonProperty("network")
        private boolean network = true;

        @JsonProperty("paths")
        private boolean paths = true;

        /**
         * Pseudonymize port numbers by mapping to 1000+ range
         * Uses counter mode always (regardless of global mode)
         */
        @JsonProperty("ports")
        private boolean ports = true;

        // Getters and setters
        public boolean isProperties() { return properties; }
        public void setProperties(boolean properties) { this.properties = properties; }

        public boolean isStrings() { return strings; }
        public void setStrings(boolean strings) { this.strings = strings; }

        public boolean isNetwork() { return network; }
        public void setNetwork(boolean network) { this.network = network; }

        public boolean isPaths() { return paths; }
        public void setPaths(boolean paths) { this.paths = paths; }

        public boolean isPorts() { return ports; }
        public void setPorts(boolean ports) { this.ports = ports; }
    }

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getCustomPrefix() { return customPrefix; }
    public void setCustomPrefix(String customPrefix) { this.customPrefix = customPrefix; }

    public String getCustomSuffix() { return customSuffix; }
    public void setCustomSuffix(String customSuffix) { this.customSuffix = customSuffix; }

    public int getHashLength() { return hashLength; }
    public void setHashLength(int hashLength) { this.hashLength = hashLength; }

    public String getHashAlgorithm() { return hashAlgorithm; }
    public void setHashAlgorithm(String hashAlgorithm) { this.hashAlgorithm = hashAlgorithm; }

    public ScopeConfig getScope() { return scope; }
    public void setScope(ScopeConfig scope) { this.scope = scope; }

    public Map<String, String> getReplacements() { return replacements; }
    public void setReplacements(Map<String, String> replacements) { this.replacements = replacements; }

    public Map<String, String> getPatternGenerators() { return patternGenerators; }
    public void setPatternGenerators(Map<String, String> patternGenerators) {
        this.patternGenerators = patternGenerators;
    }

    /**
     * Merge with parent configuration
     */
    public void mergeWith(PseudonymizationConfig parent) {
        // Child values take precedence
        // Merge replacement maps (parent first, then child overrides)
        if (parent != null && parent.getReplacements() != null) {
            Map<String, String> merged = new HashMap<>(parent.getReplacements());
            if (this.replacements != null) {
                merged.putAll(this.replacements);
            }
            this.replacements = merged;
        }

        // Merge pattern generators (parent first, then child overrides)
        if (parent != null && parent.getPatternGenerators() != null) {
            Map<String, String> mergedPatterns = new HashMap<>(parent.getPatternGenerators());
            if (this.patternGenerators != null) {
                mergedPatterns.putAll(this.patternGenerators);
            }
            this.patternGenerators = mergedPatterns;
        }
    }
}