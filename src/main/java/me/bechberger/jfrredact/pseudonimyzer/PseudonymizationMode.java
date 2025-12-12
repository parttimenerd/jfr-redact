package me.bechberger.jfrredact.pseudonimyzer;

/**
 * Pseudonymization modes.
 */
public enum PseudonymizationMode {
    /**
     * Hash-based mode (stateless, deterministic).
     * Same value always produces same hash.
     * No state required, best for most use cases.
     */
    HASH,

    /**
     * Counter mode (stateful, requires hash map).
     * Maps values to sequential numbers: value1→1, value2→2
     * Best for debugging and when you want smaller, readable pseudonyms.
     */
    COUNTER,

    /**
     * Realistic mode (stateful, generates plausible alternatives).
     * Replaces sensitive data with realistic-looking alternatives.
     * Examples:
     * - "john.doe@example.com" -> "alice.smith@test.com"
     * - "/home/johndoe" -> "/home/user01"
     * - "johndoe" -> "user01"
     * Best for creating shareable test data that looks realistic.
     */
    REALISTIC;

    public static PseudonymizationMode fromString(String mode) {
        if (mode == null) {
            return HASH;
        }
        try {
            return valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return HASH;
        }
    }
}