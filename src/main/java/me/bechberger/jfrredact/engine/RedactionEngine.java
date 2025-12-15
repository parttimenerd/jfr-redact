package me.bechberger.jfrredact.engine;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import me.bechberger.jfrredact.config.EventConfig;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.pseudonimyzer.Pseudonymizer;
import me.bechberger.jfrredact.util.GlobMatcher;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple redaction engine for JFR events.
 *
 * Main responsibilities:
 * 1. Determine if an event should be removed
 * 2. Redact individual field values based on patterns and configuration
 *
 * Usage:
 * <pre>
 * RedactionEngine engine = new RedactionEngine(config);
 *
 * // Step 1: Check if event should be removed
 * if (engine.shouldRemoveEvent("jdk.OSInformation")) {
 *     // Skip this event
 * }
 *
 * // Step 2: Process each field
 * String redacted = engine.redact("password", "secret123");
 * int redactedPort = engine.redact("port", 8080);
 * </pre>
 */
public class RedactionEngine {

    private static final Logger logger = LoggerFactory.getLogger(RedactionEngine.class);

    private final RedactionConfig config;
    private final Pseudonymizer pseudonymizer;
    private final RedactionStats stats;

    // Compiled patterns cache for performance
    private final Map<String, Pattern> patternCache = new HashMap<>();

    /** No-redaction engine instance for convenience - disables all redactions. */
    public static final RedactionEngine NONE = new RedactionEngine(createDisabledConfig()) {
        @Override
        public boolean shouldRemoveEvent(String eventType) {
            return false;
        }

        @Override
        public boolean shouldRemoveThread(String threadName) {
            return false;
        }

        @Override
        public String redact(String fieldName, String value) {
            return value;
        }

        @Override
        public int redact(String fieldName, int value) {
            return value;
        }

        @Override
        public long redact(String fieldName, long value) {
            return value;
        }

    };

    private static RedactionConfig createDisabledConfig() {
        RedactionConfig config = new RedactionConfig();
        config.getProperties().setEnabled(false);
        config.getEvents().setRemoveEnabled(false);
        config.getStrings().setEnabled(false);
        config.getNetwork().setEnabled(false);
        config.getPaths().setEnabled(false);
        config.getGeneral().getPseudonymization().setEnabled(false);
        return config;
    }

    public RedactionEngine(RedactionConfig config) {
        this(config, new RedactionStats());
    }

    public RedactionEngine(RedactionConfig config, RedactionStats stats) {
        this.config = config;
        this.stats = stats;
        this.pseudonymizer = config.createPseudonymizer();
        compilePatterns();

        logger.debug("RedactionEngine initialized");
        logger.debug("  Properties redaction: {}", config.getProperties().isEnabled());
        logger.debug("  String patterns: {}", config.getStrings().isEnabled());
        logger.debug("  Network redaction: {}", config.getNetwork().isEnabled());
        logger.debug("  Path redaction: {}", config.getPaths().isEnabled());
        logger.debug("  Pseudonymization: {}", config.getGeneral().getPseudonymization().isEnabled());
        logger.debug("  Event removal: {}", config.getEvents().isRemoveEnabled());
    }

    private void compilePatterns() {
        if (!config.getStrings().isEnabled()) {
            return;
        }

        var patterns = config.getStrings().getPatterns();

        // Email pattern
        if (patterns.getEmails().isEnabled()) {
            patternCache.put("email", Pattern.compile(patterns.getEmails().getRegex()));
        }

        // IP patterns
        if (patterns.getIpAddresses().isEnabled()) {
            patternCache.put("ipv4", Pattern.compile(patterns.getIpAddresses().getIpv4()));
            patternCache.put("ipv6", Pattern.compile(patterns.getIpAddresses().getIpv6()));
        }

        // UUID pattern
        if (patterns.getUuids().isEnabled()) {
            patternCache.put("uuid", Pattern.compile(patterns.getUuids().getRegex()));
        }

        // SSH host patterns
        if (patterns.getSshHosts().isEnabled()) {
            for (int i = 0; i < patterns.getSshHosts().getPatterns().size(); i++) {
                String regex = patterns.getSshHosts().getPatterns().get(i);
                patternCache.put("ssh_" + i, Pattern.compile(regex));
            }
        }

        // Home directory patterns
        if (patterns.getHomeDirectories().isEnabled()) {
            for (int i = 0; i < patterns.getHomeDirectories().getRegexes().size(); i++) {
                String regex = patterns.getHomeDirectories().getRegexes().get(i);
                patternCache.put("home_" + i, Pattern.compile(regex));
            }
        }

        // Hostname patterns (for hs_err files)
        if (patterns.getHostnames().isEnabled()) {
            for (int i = 0; i < patterns.getHostnames().getPatterns().size(); i++) {
                String regex = patterns.getHostnames().getPatterns().get(i);
                patternCache.put("hostname_" + i, Pattern.compile(regex));
            }
        }

        // Internal URL patterns
        if (patterns.getInternalUrls().isEnabled()) {
            for (int i = 0; i < patterns.getInternalUrls().getPatterns().size(); i++) {
                String regex = patterns.getInternalUrls().getPatterns().get(i);
                patternCache.put("internal_url_" + i, Pattern.compile(regex));
            }
        }

        // Custom patterns
        for (int i = 0; i < patterns.getCustom().size(); i++) {
            var customPattern = patterns.getCustom().get(i);
            if (customPattern.getRegex() != null && !customPattern.getRegex().isEmpty()) {
                String key = customPattern.getName() != null ? customPattern.getName() : "custom_" + i;
                patternCache.put(key, Pattern.compile(customPattern.getRegex()));
            }
        }
    }

    /**
     * Check if an event should be removed completely.
     *
     * @param eventType The event type name (e.g., "jdk.JavaMonitorEnter")
     * @return true if the event should be removed, false otherwise
     */
    public boolean shouldRemoveEvent(String eventType) {
        boolean shouldRemove = config.getEvents().shouldRemove(eventType);
        if (shouldRemove) {
            logger.debug("Removing event: {}", eventType);
        }
        return shouldRemove;
    }

    /**
     * Check if an event should be removed completely, using jfr scrub-style filtering.
     * Supports filtering by event name, category, and thread name.
     *
     * @param event The recorded event to check
     * @return true if the event should be removed, false otherwise
     */
    public boolean shouldRemoveEvent(RecordedEvent event) {
        // First check simple event type removal
        if (shouldRemoveEvent(event.getEventType().getName())) {
            return true;
        }

        // Then check jfr scrub-style filtering
        EventConfig.FilteringConfig filtering = config.getEvents().getFiltering();
        if (!filtering.hasAnyFilters()) {
            return false; // No filters configured
        }

        String eventName = event.getEventType().getName();
        List<String> categories = event.getEventType().getCategoryNames();
        String threadName = getThreadName(event);

        // Check thread filtering first using the dedicated method
        if (shouldRemoveThread(threadName)) {
            logger.debug("Removing event (thread filtered): {} ({})", eventName, threadName);
            return true;
        }

        String sampledThreadName = getSampledThreadName(event);
        if (shouldRemoveThread(sampledThreadName)) {
            logger.debug("Removing event (sampled thread filtered): {} ({})", eventName, sampledThreadName);
            return true;
        }

        // Apply filters in order: include, then exclude
        // If any include filter is specified, event must match at least one to be kept
        // Note: Thread includes are handled separately above, so we don't count them here

        boolean hasIncludeFilters = !filtering.getIncludeEvents().isEmpty() ||
                                   !filtering.getIncludeCategories().isEmpty();

        if (hasIncludeFilters) {
            boolean included = false;

            // Check event name includes
            if (!filtering.getIncludeEvents().isEmpty()) {
                if (GlobMatcher.matches(eventName, filtering.getIncludeEvents())) {
                    included = true;
                }
            }

            // Check category includes
            if (!included && !filtering.getIncludeCategories().isEmpty()) {
                for (String category : categories) {
                    if (GlobMatcher.matches(category, filtering.getIncludeCategories())) {
                        included = true;
                        break;
                    }
                }
            }


            if (!included) {
                logger.debug("Removing event (not included): {}", eventName);
                return true; // Event doesn't match any include filter
            }
        }

        // Check exclude filters
        if (!filtering.getExcludeEvents().isEmpty()) {
            if (GlobMatcher.matches(eventName, filtering.getExcludeEvents())) {
                logger.debug("Removing event (excluded by event name): {}", eventName);
                return true;
            }
        }

        if (!filtering.getExcludeCategories().isEmpty()) {
            for (String category : categories) {
                if (GlobMatcher.matches(category, filtering.getExcludeCategories())) {
                    logger.debug("Removing event (excluded by category): {} ({})", eventName, category);
                    return true;
                }
            }
        }

        // Note: Thread excludes are now handled by shouldFilterThread() above

        return false; // Event passes all filters
    }

    /**
     * Extract thread name from event, handling null safely.
     */
    private @Nullable String getThreadName(RecordedEvent event) {
        try {
            RecordedThread thread = event.getThread();
            return thread != null ? thread.getJavaName() : null;
        } catch (Exception e) {
            // Some events might not have a thread field
            return null;
        }
    }

    private @Nullable String getSampledThreadName(RecordedEvent event) {
        try {
            RecordedThread thread = event.getThread("sampledThread");
            return thread != null ? thread.getJavaName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if events from a given thread should be filtered out.
     *
     * @param threadName The thread name to check
     * @return true if events from this thread should be filtered out (removed), false otherwise
     */
    public boolean shouldRemoveThread(String threadName) {
        if (threadName == null) {
            return false;
        }

        EventConfig.FilteringConfig filtering = config.getEvents().getFiltering();
        if (!filtering.hasAnyFilters()) {
            return false; // No filters configured
        }

        // If include filters are specified, thread must match at least one to be kept
        if (!filtering.getIncludeThreads().isEmpty()) {
            if (!GlobMatcher.matches(threadName, filtering.getIncludeThreads())) {
                // Thread doesn't match any include filter - should be filtered
                return true;
            }
        }

        // Check exclude filters
        if (!filtering.getExcludeThreads().isEmpty()) {
            if (GlobMatcher.matches(threadName, filtering.getExcludeThreads())) {
                // Thread matches exclude filter - should be filtered
                return true;
            }
        }

        return false; // Thread should not be filtered
    }

    // ========== Redaction methods for all supported types ==========

    /**
     * Redact a String value.
     * Auto-detects if it's a property, email, IP, path, etc.
     */
    public String redact(String fieldName, String value) {
        if (value == null) {
            return null;
        }

        // 1. Check if field name matches property patterns
        if (config.getProperties().isEnabled() &&
            config.getProperties().matches(fieldName)) {
            String redacted = applyRedaction(value, "property");
            logger.debug("Redacted property '{}': {} -> {}", fieldName, value, redacted);
            stats.recordRedactedField(fieldName);
            return redacted;
        }

        // 2. Check string patterns (email, IP, UUID, etc.)
        if (config.getStrings().isEnabled()) {
            String redacted = checkStringPatterns(value);
            if (!redacted.equals(value)) {
                logger.debug("Redacted string in '{}': pattern matched", fieldName);
                stats.recordRedactedField(fieldName);
                return redacted;  // Pattern matched, value was redacted
            }
        }

        // 3. No redaction needed
        return value;
    }

    /**
     * Redact an int value (e.g., port numbers).
     */
    public int redact(String fieldName, int value) {
        if (isPortField(fieldName)) {
            int redacted = pseudonymizer.pseudonymizePort(value);
            if (redacted != value) {
                logger.debug("Redacted port '{}': {} -> {}", fieldName, value, redacted);
                stats.recordRedactedField(fieldName);
            }
            return redacted;
        }
        return value;
    }

    /**
     * Redact a long value (e.g., port numbers).
     */
    public long redact(String fieldName, long value) {
        if (isPortField(fieldName)) {
            return pseudonymizer.pseudonymizePort((int) value);
        }
        return value;
    }

    /**
     * Redact a boolean value (pass-through, no redaction).
     */
    public boolean redact(String fieldName, boolean value) {
        return value;
    }

    /**
     * Redact a byte value (pass-through, no redaction).
     */
    public byte redact(String fieldName, byte value) {
        return value;
    }

    /**
     * Redact a char value (pass-through, no redaction).
     */
    public char redact(String fieldName, char value) {
        return value;
    }

    /**
     * Redact a short value (pass-through, no redaction).
     */
    public short redact(String fieldName, short value) {
        return value;
    }

    /**
     * Redact a float value (pass-through, no redaction).
     */
    public float redact(String fieldName, float value) {
        return value;
    }

    /**
     * Redact a double value (pass-through, no redaction).
     */
    public double redact(String fieldName, double value) {
        return value;
    }

    // ========== Helper methods ==========

    private boolean isPortField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        return lower.contains("port") ||
               lower.equals("p") ||
               lower.equals("sourceport") ||
               lower.equals("destinationport");
    }

    private String checkStringPatterns(String value) {
        String result = value;

        // Check email pattern - use find() for partial matching
        Pattern emailPattern = patternCache.get("email");
        if (emailPattern != null) {
            Matcher emailMatcher = emailPattern.matcher(result);
            if (emailMatcher.find()) {
                result = replaceMatches(emailMatcher, result, "email");
            }
        }

        // Check IP patterns - use find() for partial matching (changed from matches())
        Pattern ipv4Pattern = patternCache.get("ipv4");
        if (ipv4Pattern != null) {
            Matcher ipv4Matcher = ipv4Pattern.matcher(result);
            if (ipv4Matcher.find()) {
                result = replaceMatches(ipv4Matcher, result, "ipv4");
            }
        }

        Pattern ipv6Pattern = patternCache.get("ipv6");
        if (ipv6Pattern != null) {
            Matcher ipv6Matcher = ipv6Pattern.matcher(result);
            if (ipv6Matcher.find()) {
                result = replaceMatches(ipv6Matcher, result, "ipv6");
            }
        }

        // Check UUID pattern - use find() for partial matching (changed from matches())
        Pattern uuidPattern = patternCache.get("uuid");
        if (uuidPattern != null) {
            Matcher uuidMatcher = uuidPattern.matcher(result);
            if (uuidMatcher.find()) {
                result = replaceMatches(uuidMatcher, result, "uuid");
            }
        }

        // Check SSH host patterns
        for (Map.Entry<String, Pattern> entry : patternCache.entrySet()) {
            if (entry.getKey().startsWith("ssh_")) {
                Matcher matcher = entry.getValue().matcher(result);
                if (matcher.find()) {
                    result = replaceMatches(matcher, result, "ssh_host");
                }
            }
        }

        // Check home directory patterns
        for (Map.Entry<String, Pattern> entry : patternCache.entrySet()) {
            if (entry.getKey().startsWith("home_")) {
                Matcher matcher = entry.getValue().matcher(result);
                if (matcher.find()) {
                    result = replaceMatches(matcher, result, "home_directory");
                }
            }
        }

        // Check custom patterns (including CLI-added patterns)
        for (Map.Entry<String, Pattern> entry : patternCache.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("custom_") || key.startsWith("cli_pattern_")) {
                Matcher matcher = entry.getValue().matcher(result);
                if (matcher.find()) {
                    result = replaceMatches(matcher, result, key);
                }
            }
        }

        return result;
    }

    private String replaceMatches(Matcher matcher, String value, String context) {
        // Reset matcher to scan from beginning
        matcher.reset();

        if (pseudonymizer.isEnabled()) {
            // With pseudonymization, each unique match gets its own pseudonym
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String matched = matcher.group();
                String replacement = pseudonymizer.pseudonymize(matched, config.getGeneral().getRedactionText());
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } else {
            // Simple redaction - replace all matches with redaction text
            return matcher.replaceAll(Matcher.quoteReplacement(config.getGeneral().getRedactionText()));
        }
    }

    private String applyRedaction(String value, String context) {
        // Use pseudonymization if enabled
        if (pseudonymizer.isEnabled()) {
            return pseudonymizer.pseudonymize(value, config.getGeneral().getRedactionText());
        }

        // Otherwise use simple redaction
        return config.getGeneral().getRedactionText();
    }

    /**
     * Get the pseudonymizer instance (for accessing statistics, clearing cache, etc.)
     */
    public Pseudonymizer getPseudonymizer() {
        return pseudonymizer;
    }

    /**
     * Get the statistics tracker.
     */
    public RedactionStats getStats() {
        return stats;
    }
}