package me.bechberger.jfrredact.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for path redaction.
 */
public class PathConfig {

    public enum PathMode {
        @JsonProperty("keep_filename")
        KEEP_FILENAME,

        @JsonProperty("redact_all")
        REDACT_ALL,

        @JsonProperty("keep_all")
        KEEP_ALL
    }

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("mode")
    private PathMode mode = PathMode.KEEP_FILENAME;

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public PathMode getMode() { return mode; }
    public void setMode(PathMode mode) { this.mode = mode; }

    /**
     * Merge with parent configuration
     */
    public void mergeWith(PathConfig parent) {
        // For enum and boolean, child values take precedence
        // Nothing to merge for this simple config
    }
}