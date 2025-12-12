package me.bechberger.jfrredact.pseudonimyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Pseudonymizer provides deterministic mapping of sensitive values to pseudonyms.
 *
 * When enabled, the same input value always maps to the same output pseudonym,
 * preserving relationships while protecting sensitive data.
 *
 * Example: "user@example.com" -> "<redacted:a1b2c3>" (consistently)
 */
public class Pseudonymizer {

    private static final Logger log = LoggerFactory.getLogger(Pseudonymizer.class);

    private final boolean enabled;
    private final PseudonymizationMode mode;
    private final PseudonymizationFormat format;
    private final String customPrefix;
    private final String customSuffix;
    private final int hashLength;
    private final String hashAlgorithm;
    private final PseudonymizationScope scope;

    // Cache mapping original values to their pseudonyms
    private final Map<String, String> cache = new HashMap<>();

    // Counter for counter mode
    private int counter = 1;

    // Port number counter (always starts at 1000)
    private int portCounter = 1000;
    private final Map<Integer, Integer> portCache = new HashMap<>();

    // Realistic data generator for REALISTIC mode
    private final RealisticDataGenerator realisticGenerator;

    // Custom replacements for specific values (overrides all other modes)
    private final Map<String, String> customReplacements;

    // Pattern-based generator for regex-based replacements
    private final PatternBasedGenerator patternGenerator;

    /**
     * Configuration for what types of values should be pseudonymized
     */
    public static class PseudonymizationScope {
        private final boolean properties;
        private final boolean strings;
        private final boolean network;
        private final boolean paths;
        private final boolean ports;

        public PseudonymizationScope(boolean properties, boolean strings, boolean network, boolean paths) {
            this(properties, strings, network, paths, true);
        }

        public PseudonymizationScope(boolean properties, boolean strings, boolean network, boolean paths, boolean ports) {
            this.properties = properties;
            this.strings = strings;
            this.network = network;
            this.paths = paths;
            this.ports = ports;
        }

        public boolean shouldPseudonymizeProperties() { return properties; }
        public boolean shouldPseudonymizeStrings() { return strings; }
        public boolean shouldPseudonymizeNetwork() { return network; }
        public boolean shouldPseudonymizePaths() { return paths; }
        public boolean shouldPseudonymizePorts() { return ports; }
    }

    /**
     * Private constructor - use Builder or factory methods
     */
    private Pseudonymizer(boolean enabled, PseudonymizationMode mode, PseudonymizationFormat format,
                         String customPrefix, String customSuffix, int hashLength, String hashAlgorithm,
                         PseudonymizationScope scope, Map<String, String> customReplacements,
                         Map<String, String> patternGenerators) {
        this.enabled = enabled;
        this.mode = mode != null ? mode : PseudonymizationMode.HASH;
        this.format = format != null ? format : PseudonymizationFormat.REDACTED;
        this.customPrefix = customPrefix != null ? customPrefix : "<redacted:";
        this.customSuffix = customSuffix != null ? customSuffix : ">";
        this.hashLength = Math.max(6, Math.min(32, hashLength));
        this.hashAlgorithm = hashAlgorithm != null ? hashAlgorithm : "SHA-256";
        this.scope = scope != null ? scope : new PseudonymizationScope(true, true, true, true);
        this.customReplacements = customReplacements != null ? new HashMap<>(customReplacements) : new HashMap<>();

        // Initialize realistic generator with a deterministic seed based on format
        // This ensures consistent results across runs
        long seed = (customPrefix + customSuffix).hashCode();
        this.realisticGenerator = new RealisticDataGenerator(seed);

        // Initialize pattern-based generator
        if (patternGenerators != null && !patternGenerators.isEmpty()) {
            this.patternGenerator = new PatternBasedGenerator(patternGenerators, seed);
        } else {
            this.patternGenerator = null;
        }

        log.debug("Pseudonymizer initialized: enabled={}, mode={}, format={}, custom replacements={}, pattern generators={}",
                  enabled, this.mode, this.format, this.customReplacements.size(),
                  patternGenerators != null ? patternGenerators.size() : 0);
    }

    /**
     * Creates a disabled Pseudonymizer (passes through to simple redaction)
     */
    public static Pseudonymizer disabled() {
        return new Pseudonymizer(false, PseudonymizationMode.HASH, null, null, null, 8, null, null, null, null);
    }

    /**
     * Creates a Pseudonymizer with default settings (hash mode)
     */
    public static Pseudonymizer withDefaults() {
        return builder().build();
    }

    /**
     * Creates a new Builder for configuring a Pseudonymizer
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating Pseudonymizer instances with a fluent API
     */
    public static class Builder {
        private boolean enabled = true;
        private PseudonymizationMode mode = PseudonymizationMode.HASH;
        private PseudonymizationFormat format = PseudonymizationFormat.REDACTED;
        private String customPrefix = "<redacted:";
        private String customSuffix = ">";
        private int hashLength = 8;
        private String hashAlgorithm = "SHA-256";
        private PseudonymizationScope scope = new PseudonymizationScope(true, true, true, true);
        private Map<String, String> customReplacements = new HashMap<>();
        private Map<String, String> patternGenerators = new HashMap<>();

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder mode(PseudonymizationMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder format(PseudonymizationFormat format) {
            this.format = format;
            return this;
        }

        public Builder customPrefix(String customPrefix) {
            this.customPrefix = customPrefix;
            return this;
        }

        public Builder customSuffix(String customSuffix) {
            this.customSuffix = customSuffix;
            return this;
        }

        public Builder hashLength(int hashLength) {
            this.hashLength = hashLength;
            return this;
        }

        public Builder hashAlgorithm(String hashAlgorithm) {
            this.hashAlgorithm = hashAlgorithm;
            return this;
        }

        public Builder scope(PseudonymizationScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder customReplacements(Map<String, String> customReplacements) {
            this.customReplacements = customReplacements;
            return this;
        }

        public Builder addReplacement(String original, String replacement) {
            this.customReplacements.put(original, replacement);
            return this;
        }

        public Builder patternGenerators(Map<String, String> patternGenerators) {
            this.patternGenerators = patternGenerators;
            return this;
        }

        public Builder addPatternGenerator(String patternName, String regexPattern) {
            this.patternGenerators.put(patternName, regexPattern);
            return this;
        }

        public Pseudonymizer build() {
            return new Pseudonymizer(enabled, mode, format, customPrefix, customSuffix,
                    hashLength, hashAlgorithm, scope, customReplacements, patternGenerators);
        }
    }

    /**
     * Returns true if pseudonymization is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the scope configuration
     */
    public PseudonymizationScope getScope() {
        return scope;
    }

    /**
     * Pseudonymize a value. If pseudonymization is disabled, returns the fallback text.
     *
     * @param value The original value to pseudonymize
     * @param fallbackRedactionText The text to use if pseudonymization is disabled
     * @return The pseudonymized value or fallback text
     */
    public String pseudonymize(String value, String fallbackRedactionText) {
        if (!enabled || value == null) {
            log.trace("Pseudonymization disabled or null value, returning fallback");
            return fallbackRedactionText;
        }

        // Check for custom replacement first (highest priority)
        if (customReplacements.containsKey(value)) {
            String replacement = customReplacements.get(value);
            log.debug("Using custom replacement for value (length={})", value.length());
            return replacement;
        }

        // Check cache
        String cached = cache.get(value);
        if (cached != null) {
            log.trace("Cache hit for value (length={})", value.length());
            return cached;
        }

        // Generate new pseudonym
        log.debug("Generating new pseudonym for value (length={}) using mode={}", value.length(), mode);
        String pseudonym = generatePseudonym(value);
        cache.put(value, pseudonym);
        log.trace("Generated pseudonym: {} (cache size now: {})",
                  pseudonym.length() > 50 ? pseudonym.substring(0, 50) + "..." : pseudonym,
                  cache.size());
        return pseudonym;
    }

    /**
     * Pseudonymize a value using a specific pattern generator.
     *
     * This method is used when you know the pattern type (e.g., "ssh_hosts", "ip_addresses")
     * and want to use a pattern-based generator for that specific type.
     *
     * @param value The original value to pseudonymize
     * @param patternName The name of the pattern to use (e.g., "ssh_hosts", "usernames")
     * @param fallbackRedactionText The text to use if pseudonymization is disabled or pattern not found
     * @return The pseudonymized value or fallback text
     */
    public String pseudonymizeWithPattern(String value, String patternName, String fallbackRedactionText) {
        if (!enabled || value == null) {
            log.trace("Pseudonymization disabled or null value, returning fallback");
            return fallbackRedactionText;
        }

        // Check for custom replacement first (highest priority)
        if (customReplacements.containsKey(value)) {
            String replacement = customReplacements.get(value);
            log.debug("Using custom replacement for value (length={})", value.length());
            return replacement;
        }

        // Try pattern-based generation if available
        if (patternGenerator != null && patternGenerator.hasPattern(patternName)) {
            // Check cache first
            String cached = cache.get(value);
            if (cached != null) {
                log.trace("Cache hit for value (length={}) with pattern {}", value.length(), patternName);
                return cached;
            }

            log.debug("Generating replacement for value (length={}) using pattern: {}", value.length(), patternName);
            String generated = patternGenerator.generate(patternName, value);
            if (generated != null) {
                cache.put(value, generated);
                log.trace("Generated: {} (cache size now: {})",
                          generated.length() > 50 ? generated.substring(0, 50) + "..." : generated,
                          cache.size());
                return generated;
            }
        }

        // Fall back to normal pseudonymization
        return pseudonymize(value, fallbackRedactionText);
    }

    /**
     * Generate a pseudonym for the given value based on configured mode
     */
    private String generatePseudonym(String value) {
        switch (mode) {
            case REALISTIC:
                // Realistic mode: generate plausible-looking alternatives
                return realisticGenerator.generateReplacement(value);

            case COUNTER:
                // Simple counter mode: value1->1, value2->2, etc.
                String counterId = String.valueOf(counter++);
                return formatPseudonym(counterId);

            case HASH:
            default:
                // Hash-based mode (stateless, deterministic)
                String hash = computeHash(value);
                String hashId = hash.substring(0, Math.min(hashLength, hash.length()));
                return formatPseudonym(hashId);
        }
    }

    /**
     * Format a pseudonym identifier with the configured prefix/suffix.
     * Only used for HASH and COUNTER modes (REALISTIC generates its own format).
     */
    private String formatPseudonym(String identifier) {
        String prefix;
        String suffix = switch (format) {
            case HASH -> {
                prefix = "<hash:";
                yield ">";
            }
            case CUSTOM -> {
                prefix = customPrefix;
                yield customSuffix;
            }
            default -> {
                prefix = "<redacted:";
                yield ">";
            }
        };

        return prefix + identifier + suffix;
    }

    /**
     * Compute a hash of the input value
     */
    private String computeHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // Fallback to SHA-256 if the specified algorithm is not available
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
                return bytesToHex(hashBytes);
            } catch (NoSuchAlgorithmException e2) {
                // This should never happen as SHA-256 is always available
                throw new RuntimeException("SHA-256 algorithm not available", e2);
            }
        }
    }

    /**
     * Convert byte array to hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Pseudonymize a port number by mapping it to the 1000+ range.
     * Always uses counter mode regardless of global mode setting.
     *
     * Example: 8080 -> 1001, 443 -> 1002, 8080 -> 1001 (consistent)
     *
     * @param port The original port number
     * @return The pseudonymized port number (1000+) or original if pseudonymization disabled
     */
    public int pseudonymizePort(int port) {
        if (!enabled || !scope.shouldPseudonymizePorts()) {
            log.trace("Port pseudonymization disabled, returning original port {}", port);
            return port;
        }

        // Check cache first
        Integer cached = portCache.get(port);
        if (cached != null) {
            log.trace("Port cache hit: {} -> {}", port, cached);
            return cached;
        }

        // Assign next port number in 1000+ range
        int pseudoPort = portCounter++;
        portCache.put(port, pseudoPort);
        log.debug("Pseudonymized port: {} -> {}", port, pseudoPort);
        return pseudoPort;
    }

    /**
     * Clear the pseudonymization cache (both string and port caches)
     */
    public void clearCache() {
        int stringCacheSize = cache.size();
        int portCacheSize = portCache.size();

        cache.clear();
        portCache.clear();
        counter = 1;
        portCounter = 1000;

        log.debug("Cleared pseudonymization cache: {} string entries, {} port entries",
                  stringCacheSize, portCacheSize);
    }

    /**
     * Get the number of cached pseudonyms
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Get statistics about the pseudonymization
     */
    public String getStats() {
        return String.format("Pseudonymization: %s, Cache size: %d unique values",
                enabled ? "enabled" : "disabled", cache.size());
    }
}