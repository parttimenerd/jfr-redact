package me.bechberger.jfrredact.pseudonimyzer;

/**
 * Pseudonymization output formats.
 */
public enum PseudonymizationFormat {
    /**
     * Format: &lt;redacted:abc123&gt;
     */
    REDACTED,

    /**
     * Format: &lt;hash:abc123&gt;
     */
    HASH,

    /**
     * Custom format using customPrefix and customSuffix
     */
    CUSTOM;

    public static PseudonymizationFormat fromString(String format) {
        if (format == null) {
            return REDACTED;
        }
        try {
            return valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            return REDACTED;
        }
    }
}