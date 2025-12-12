package me.bechberger.jfrredact.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for network event redaction.
 */
public class NetworkConfig {

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("redact_ports")
    private boolean redactPorts = true;

    @JsonProperty("redact_addresses")
    private boolean redactAddresses = true;

    @JsonProperty("keep_local_addresses")
    private boolean keepLocalAddresses = false;

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isRedactPorts() { return redactPorts; }
    public void setRedactPorts(boolean redactPorts) { this.redactPorts = redactPorts; }

    public boolean isRedactAddresses() { return redactAddresses; }
    public void setRedactAddresses(boolean redactAddresses) {
        this.redactAddresses = redactAddresses;
    }

    public boolean isKeepLocalAddresses() { return keepLocalAddresses; }
    public void setKeepLocalAddresses(boolean keepLocalAddresses) {
        this.keepLocalAddresses = keepLocalAddresses;
    }

    /**
     * Merge with parent configuration
     */
    public void mergeWith(NetworkConfig parent) {
        // For boolean flags, child values take precedence
        // Nothing to merge for this simple config
    }
}