package me.bechberger.jfrredact.engine;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Action to take for a JFR event.
 */
public enum EventAction {
    /**
     * Remove the event completely from the output
     */
    @JsonProperty("remove")
    REMOVE,

    /**
     * Keep the event but redact sensitive fields
     */
    @JsonProperty("redact")
    REDACT,

    /**
     * Keep the event as-is (no redaction needed)
     */
    @JsonProperty("keep")
    KEEP
}