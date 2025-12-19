package me.bechberger.jfrredact.engine;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import me.bechberger.jfrredact.config.EventConfig;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.pseudonimyzer.Pseudonymizer;
import me.bechberger.jfrredact.util.GlobMatcher;
import me.bechberger.jfrredact.util.RegexCache;
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

    /**
     * Types of patterns supported by the redaction engine.
     */
    private enum PatternKind {
        EMAIL("email"),
        IP("ip"),
        UUID("uuid"),
        SSH("ssh_host"),
        USER("home_directory"),
        INTERNAL_URL("internal_url"),
        HOSTNAME("hostname"),
        CUSTOM("custom");

        private final String redactionType;

        PatternKind(String redactionType) {
            this.redactionType = redactionType;
        }

        String getRedactionType() {
            return redactionType;
        }
    }

    /**
     * Interface for pattern matching - supports both String.contains() and regex Pattern.
     */
    private sealed interface PatternMatcher {
        boolean find(String value);
        String getKey();
        Matcher getMatcher(String value);
    }

    /**
     * Simple string matcher using contains() for fast substring matching.
     */
    private static final class StringMatcher implements PatternMatcher {
        private final String substring;
        private final String key;
        private final Pattern pattern; // Lazy-initialized pattern for getMatcher()
        private Matcher matcher;

        StringMatcher(String substring, String key) {
            this.substring = substring;
            this.key = key;
            this.pattern = Pattern.compile(Pattern.quote(substring));
        }

        @Override
        public boolean find(String value) {
            return value.contains(substring);
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Matcher getMatcher(String value) {
            if (matcher == null) {
                matcher = pattern.matcher(value);
            } else {
                matcher.reset(value);
            }
            return matcher;
        }
    }

    /**
     * Regex pattern matcher for complex patterns.
     */
    private static final class RegexMatcher implements PatternMatcher {
        private final Pattern pattern;
        private final String key;
        private Matcher matcher;

        RegexMatcher(Pattern pattern, String key) {
            this.pattern = pattern;
            this.key = key;
        }

        @Override
        public boolean find(String value) {
            if (matcher == null) {
                matcher = pattern.matcher(value);
            } else {
                matcher.reset(value);
            }
            return matcher.find();
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Matcher getMatcher(String value) {
            if (matcher == null) {
                matcher = pattern.matcher(value);
            } else {
                matcher.reset(value);
            }
            return matcher;
        }
    }

    private final RedactionConfig config;
    private final Pseudonymizer pseudonymizer;
    private final RedactionStats stats;

    // Two-level compiled patterns cache for performance: PatternKind -> key -> PatternMatcher
    // This avoids iterating through all patterns and checking prefixes
    private final Map<PatternKind, Map<String, PatternMatcher>> patternCacheByKind = new HashMap<>();

    // Ignore lists cache - maps pattern key to IgnoreLists object
    private final Map<String, IgnoreLists> ignoreListCache = new HashMap<>();

    // Discovered patterns from discovery phase (lower priority than configured patterns)
    private DiscoveredPatterns discoveredPatterns = null;

    // Helper class to store all three types of ignore lists
    private static class IgnoreLists {
        final List<String> ignoreExact;
        final List<Pattern> ignorePatterns;
        final List<String> ignoreAfter;

        IgnoreLists(List<String> ignoreExact, List<String> ignoreRegexes, List<String> ignoreAfter) {
            this.ignoreExact = ignoreExact != null ? ignoreExact : List.of();
            this.ignorePatterns = ignoreRegexes != null ?
                ignoreRegexes.stream().map(Pattern::compile).collect(java.util.stream.Collectors.toList()) :
                List.of();
            this.ignoreAfter = ignoreAfter != null ? ignoreAfter : List.of();
        }
    }

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

        // Email patterns
        if (patterns.getEmails().isEnabled()) {
            IgnoreLists emailIgnore = new IgnoreLists(
                patterns.getEmails().getIgnoreExact(),
                patterns.getEmails().getIgnore(),
                patterns.getEmails().getIgnoreAfter()
            );
            for (int i = 0; i < patterns.getEmails().getPatterns().size(); i++) {
                String regex = patterns.getEmails().getPatterns().get(i);
                addPattern(PatternKind.EMAIL, "email_" + i, regex);
                ignoreListCache.put("email_" + i, emailIgnore);
            }
        }

        // IP patterns
        if (patterns.getIpAddresses().isEnabled()) {
            IgnoreLists ipIgnore = new IgnoreLists(
                patterns.getIpAddresses().getIgnoreExact(),
                patterns.getIpAddresses().getIgnore(),
                patterns.getIpAddresses().getIgnoreAfter()
            );
            for (int i = 0; i < patterns.getIpAddresses().getPatterns().size(); i++) {
                String regex = patterns.getIpAddresses().getPatterns().get(i);
                addPattern(PatternKind.IP, "ip_" + i, regex);
                ignoreListCache.put("ip_" + i, ipIgnore);
            }
        }

        // UUID patterns
        if (patterns.getUuids().isEnabled()) {
            IgnoreLists uuidIgnore = new IgnoreLists(
                patterns.getUuids().getIgnoreExact(),
                patterns.getUuids().getIgnore(),
                patterns.getUuids().getIgnoreAfter()
            );
            for (int i = 0; i < patterns.getUuids().getPatterns().size(); i++) {
                String regex = patterns.getUuids().getPatterns().get(i);
                addPattern(PatternKind.UUID, "uuid_" + i, regex);
                ignoreListCache.put("uuid_" + i, uuidIgnore);
            }
        }

        // SSH host patterns
        if (patterns.getSshHosts().isEnabled()) {
            IgnoreLists sshIgnore = new IgnoreLists(
                patterns.getSshHosts().getIgnoreExact(),
                patterns.getSshHosts().getIgnore(),
                patterns.getSshHosts().getIgnoreAfter()
            );
            for (int i = 0; i < patterns.getSshHosts().getPatterns().size(); i++) {
                String regex = patterns.getSshHosts().getPatterns().get(i);
                addPattern(PatternKind.SSH, "ssh_" + i, regex);
                ignoreListCache.put("ssh_" + i, sshIgnore);
            }
        }

        // User name patterns
        if (patterns.getUser().isEnabled()) {
            IgnoreLists userIgnore = new IgnoreLists(
                patterns.getUser().getIgnoreExact(),
                patterns.getUser().getIgnore(),
                patterns.getUser().getIgnoreAfter()
            );
            for (int i = 0; i < patterns.getUser().getPatterns().size(); i++) {
                String regex = patterns.getUser().getPatterns().get(i);
                addPattern(PatternKind.USER, "user_" + i, regex);
                ignoreListCache.put("user_" + i, userIgnore);
            }
        }

        // Hostname patterns (for hs_err files)
        if (patterns.getHostnames().isEnabled()) {
            for (int i = 0; i < patterns.getHostnames().getPatterns().size(); i++) {
                String regex = patterns.getHostnames().getPatterns().get(i);
                addPattern(PatternKind.HOSTNAME, "hostname_" + i, regex);
            }
            // Note: hostnames use getIgnoreExact() for safe hostnames
            // handled separately in replaceHostnameMatches
        }

        // Internal URL patterns
        if (patterns.getInternalUrls().isEnabled()) {
            IgnoreLists urlIgnore = new IgnoreLists(
                patterns.getInternalUrls().getIgnoreExact(),
                patterns.getInternalUrls().getIgnore(),
                patterns.getInternalUrls().getIgnoreAfter()
            );
            for (int i = 0; i < patterns.getInternalUrls().getPatterns().size(); i++) {
                String regex = patterns.getInternalUrls().getPatterns().get(i);
                addPattern(PatternKind.INTERNAL_URL, "internal_url_" + i, regex);
                ignoreListCache.put("internal_url_" + i, urlIgnore);
            }
        }

        // Custom patterns
        for (int i = 0; i < patterns.getCustom().size(); i++) {
            var customPattern = patterns.getCustom().get(i);
            if (!customPattern.getPatterns().isEmpty()) {
                IgnoreLists customIgnore = new IgnoreLists(
                    customPattern.getIgnoreExact(),
                    customPattern.getIgnore(),
                    customPattern.getIgnoreAfter()
                );
                String baseName = customPattern.getName() != null ? customPattern.getName() : "pattern_" + i;
                for (int j = 0; j < customPattern.getPatterns().size(); j++) {
                    String regex = customPattern.getPatterns().get(j);
                    String key = "custom_" + baseName + (customPattern.getPatterns().size() > 1 ? "_" + j : "");
                    addPattern(PatternKind.CUSTOM, key, regex);
                    ignoreListCache.put(key, customIgnore);
                }
            }
        }
    }

    /**
     * Helper method to add a pattern to the two-level cache.
     * Automatically detects if the pattern is a simple string or a regex.
     * @param kind The kind of pattern (EMAIL, IP, UUID, etc.)
     * @param key The full key including the kind prefix
     * @param regex The regex pattern string (or simple string)
     */
    private void addPattern(PatternKind kind, String key, String regex) {
        PatternMatcher matcher;

        // Check if this is a simple string (no regex metacharacters)
        if (isSimpleString(regex)) {
            // Use fast StringMatcher for simple strings
            matcher = new StringMatcher(regex, key);
        } else {
            // Use RegexMatcher for complex patterns
            matcher = new RegexMatcher(Pattern.compile(regex), key);
        }

        patternCacheByKind
            .computeIfAbsent(kind, (PatternKind k) -> new HashMap<>())
            .put(key, matcher);
    }

    /**
     * Check if a pattern string is a simple literal string (no regex metacharacters).
     * Simple strings can use fast String.contains() instead of regex matching.
     */
    private boolean isSimpleString(String pattern) {
        // Check for common regex metacharacters
        // If the pattern contains any of these, it's not a simple string
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '.', '*', '+', '?', '[', ']', '(', ')', '{', '}',
                     '^', '$', '|', '\\' -> {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if an event should be removed completely.
     *
     * @param eventType The event type name (e.g., "jdk.JavaMonitorEnter")
     * @return true if the event should be removed, false otherwise
     */
    public boolean shouldRemoveEvent(String eventType) {
        return config.getEvents().shouldRemove(eventType);
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
            // Thread matches exclude filter - should be filtered
            return GlobMatcher.matches(threadName, filtering.getExcludeThreads());
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
            logger.debug("Redacted property '{}': '{}' -> '{}'", fieldName, truncateForLog(value), truncateForLog(redacted));
            stats.recordRedactedField(fieldName);
            stats.recordRedactionType("property");
            return redacted;
        }

        // 2. Check string patterns (email, IP, UUID, etc.)
        if (config.getStrings().isEnabled()) {
            RedactionResult result = checkStringPatternsWithType(value);
            if (result.wasRedacted()) {
                logger.debug("Redacted {} in '{}': '{}' -> '{}'",
                    result.getRedactionType(), fieldName,
                    truncateForLog(value), truncateForLog(result.getRedactedValue()));
                stats.recordRedactedField(fieldName);
                stats.recordRedactionType(result.getRedactionType());
                return result.getRedactedValue();
            }
        }

        // 3. No redaction needed
        return value;
    }

    /**
     * Truncate a value for logging to avoid excessively long log lines.
     */
    private String truncateForLog(String value) {
        if (value == null) return "null";
        if (value.length() <= 100) return value;
        return value.substring(0, 97) + "...";
    }

    /**
     * Result of a redaction operation, including the type of redaction applied.
     */
    private static class RedactionResult {
        private final String redactedValue;
        private final String redactionType;
        private final boolean wasRedacted;

        RedactionResult(String redactedValue, String redactionType, boolean wasRedacted) {
            this.redactedValue = redactedValue;
            this.redactionType = redactionType;
            this.wasRedacted = wasRedacted;
        }

        static RedactionResult noChange(String value) {
            return new RedactionResult(value, null, false);
        }

        static RedactionResult redacted(String value, String type) {
            return new RedactionResult(value, type, true);
        }

        String getRedactedValue() { return redactedValue; }
        String getRedactionType() { return redactionType; }
        boolean wasRedacted() { return wasRedacted; }
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
                stats.recordRedactionType("port");
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

    // ========== Array redaction methods ==========

    /**
     * Redact a byte array by calling redact on each element.
     */
    public byte[] redact(String fieldName, byte[] array) {
        if (array == null) return null;
        byte[] result = new byte[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = redact(fieldName, array[i]);
        }
        return result;
    }

    /**
     * Redact a short array by calling redact on each element.
     */
    public short[] redact(String fieldName, short[] array) {
        if (array == null) return null;
        short[] result = new short[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = redact(fieldName, array[i]);
        }
        return result;
    }

    /**
     * Redact an int array by calling redact on each element.
     */
    public int[] redact(String fieldName, int[] array) {
        if (array == null) return null;
        int[] result = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = redact(fieldName, array[i]);
        }
        return result;
    }

    /**
     * Redact a long array by calling redact on each element.
     */
    public long[] redact(String fieldName, long[] array) {
        if (array == null) return null;
        long[] result = new long[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = redact(fieldName, array[i]);
        }
        return result;
    }

    /**
     * Redact a float array by calling redact on each element.
     */
    public float[] redact(String fieldName, float[] array) {
        if (array == null) return null;
        float[] result = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = redact(fieldName, array[i]);
        }
        return result;
    }

    /**
     * Redact a double array by calling redact on each element.
     */
    public double[] redact(String fieldName, double[] array) {
        if (array == null) return null;
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = redact(fieldName, array[i]);
        }
        return result;
    }

    /**
     * Redact a boolean array by calling redact on each element.
     */
    public boolean[] redact(String fieldName, boolean[] array) {
        if (array == null) return null;
        boolean[] result = new boolean[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = redact(fieldName, array[i]);
        }
        return result;
    }

    /**
     * Redact a char array by calling redact on each element.
     */
    public char[] redact(String fieldName, char[] array) {
        if (array == null) return null;
        char[] result = new char[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = redact(fieldName, array[i]);
        }
        return result;
    }

    /**
     * Redact a String array by calling redact on each element.
     */
    public String[] redact(String fieldName, String[] array) {
        if (array == null) return null;
        String[] result = new String[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = redact(fieldName, array[i]);
        }
        return result;
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

    /**
     * Check string patterns and return the redaction result with type information.
     * Uses two-level cache for optimal performance with early exit optimization.
     */
    private RedactionResult checkStringPatternsWithType(String value) {
        String result = value;
        String redactionType = null;

        // Check patterns in order of priority (most common first for early exit)
        // IP patterns (very common in JFR)
        String after = checkPatternKind(PatternKind.IP, result, this::replaceMatches);
        if (after != result) { // Reference comparison is faster than equals()
            if (redactionType == null) {
                redactionType = PatternKind.IP.getRedactionType();
            }
            result = after;
        }

        // Email patterns
        after = checkPatternKind(PatternKind.EMAIL, result, this::replaceMatches);
        if (after != result) {
            if (redactionType == null) {
                redactionType = PatternKind.EMAIL.getRedactionType();
            }
            result = after;
        }

        // User/home directory patterns (common in file paths)
        after = checkPatternKind(PatternKind.USER, result, (matcher, val, key) ->
            replaceCaptureGroup(matcher, val, 1, key));
        if (after != result) {
            if (redactionType == null) {
                redactionType = PatternKind.USER.getRedactionType();
            }
            result = after;
        }

        // Internal URL patterns BEFORE hostnames (URLs may contain hostnames)
        after = checkPatternKind(PatternKind.INTERNAL_URL, result, this::replaceMatches);
        if (after != result) {
            if (redactionType == null) {
                redactionType = PatternKind.INTERNAL_URL.getRedactionType();
            }
            result = after;
        }

        // Hostname patterns (with safe hostname filtering)
        after = checkPatternKind(PatternKind.HOSTNAME, result, this::replaceHostnameMatches);
        if (after != result) {
            if (redactionType == null) {
                redactionType = PatternKind.HOSTNAME.getRedactionType();
            }
            result = after;
        }

        // UUID patterns (less common)
        after = checkPatternKind(PatternKind.UUID, result, this::replaceMatches);
        if (after != result) {
            if (redactionType == null) {
                redactionType = PatternKind.UUID.getRedactionType();
            }
            result = after;
        }

        // SSH host patterns (less common)
        after = checkPatternKind(PatternKind.SSH, result, this::replaceMatches);
        if (after != result) {
            if (redactionType == null) {
                redactionType = PatternKind.SSH.getRedactionType();
            }
            result = after;
        }

        // Custom patterns (including CLI-added patterns)
        Map<String, PatternMatcher> customPatterns = patternCacheByKind.get(PatternKind.CUSTOM);
        if (customPatterns != null && !customPatterns.isEmpty()) {
            for (Map.Entry<String, PatternMatcher> entry : customPatterns.entrySet()) {
                if (entry.getValue().find(result)) {
                    Matcher matcher = entry.getValue().getMatcher(result);
                    after = replaceMatches(matcher, result, entry.getKey());
                    if (after != result) {
                        if (redactionType == null) {
                            redactionType = entry.getKey().startsWith("cli_") ? "custom_cli" : "custom";
                        }
                        result = after;
                    }
                }
            }
        }

        // Check discovered patterns (lower priority - only if not already redacted)
        if (discoveredPatterns != null && result == value) { // Reference comparison
            after = applyDiscoveredPatterns(result);
            if (after != result) {
                result = after;
                if (redactionType == null) redactionType = "discovered";
            }
        }

        // Final check: did anything change?
        if (result != value) { // Reference comparison
            return RedactionResult.redacted(result, redactionType);
        }
        return RedactionResult.noChange(value);
    }

    /**
     * Functional interface for pattern replacement strategies.
     */
    @FunctionalInterface
    private interface ReplacementStrategy {
        String replace(Matcher matcher, String value, String key);
    }

    /**
     * Check all patterns of a given kind and apply replacements.
     * Returns the same string reference if no changes were made (for fast comparison).
     */
    private String checkPatternKind(PatternKind kind, String value, ReplacementStrategy replacer) {
        Map<String, PatternMatcher> patterns = patternCacheByKind.get(kind);
        if (patterns == null || patterns.isEmpty()) {
            return value; // Return same reference for fast identity comparison
        }

        String result = value;
        for (Map.Entry<String, PatternMatcher> entry : patterns.entrySet()) {
            if (entry.getValue().find(result)) {
                Matcher matcher = entry.getValue().getMatcher(result);
                String replaced = replacer.replace(matcher, result, entry.getKey());
                if (replaced != result) {
                    result = replaced;
                }
            }
        }
        return result;
    }


    private String checkStringPatterns(String value) {
        // Reuse the optimized checkStringPatternsWithType and just extract the value
        return checkStringPatternsWithType(value).getRedactedValue();
    }

    private String replaceMatches(Matcher matcher, String value, String patternKey) {
        // Reset matcher to scan from beginning
        matcher.reset();

        // Early exit: if no match found, return original value
        if (!matcher.find()) {
            return value;
        }

        // Reset again to start from beginning for replacement
        matcher.reset();

        // Get global no_redact list
        List<String> noRedact = config.getStrings().getNoRedact();

        // Get pattern-specific ignore lists
        IgnoreLists ignoreLists = ignoreListCache.getOrDefault(patternKey, new IgnoreLists(null, null, null));

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String matched = matcher.group();
            int matchStart = matcher.start();

            // Check if this string should not be redacted
            boolean shouldNotRedact = false;

            // 1. Check global no_redact list
            for (String safe : noRedact) {
                if (matched.contains(safe)) {
                    shouldNotRedact = true;
                    break;
                }
            }

            // 2. Check pattern-specific ignore_exact
            if (!shouldNotRedact) {
                for (String exact : ignoreLists.ignoreExact) {
                    if (matched.equalsIgnoreCase(exact)) {
                        shouldNotRedact = true;
                        break;
                    }
                }
            }

            // 3. Check pattern-specific ignore (regex patterns)
            if (!shouldNotRedact) {
                for (Pattern ignorePattern : ignoreLists.ignorePatterns) {
                    if (ignorePattern.matcher(matched).matches()) {
                        shouldNotRedact = true;
                        break;
                    }
                }
            }

            // 4. Check pattern-specific ignore_after (prefix before the match)
            if (!shouldNotRedact && matchStart > 0) {
                for (String prefix : ignoreLists.ignoreAfter) {
                    // Check if the text before the match ends with this prefix
                    String before = value.substring(Math.max(0, matchStart - prefix.length()), matchStart);
                    if (before.equals(prefix) || before.matches(prefix)) {
                        shouldNotRedact = true;
                        break;
                    }
                }
            }

            if (shouldNotRedact) {
                // Don't redact - keep original
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matched));
            } else if (pseudonymizer.isEnabled()) {
                // With pseudonymization, each unique match gets its own pseudonym
                String replacement = pseudonymizer.pseudonymize(matched, config.getGeneral().getRedactionText());
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                // Simple redaction
                matcher.appendReplacement(sb, Matcher.quoteReplacement(config.getGeneral().getRedactionText()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String replaceHostnameMatches(Matcher matcher, String value, String patternKey) {
        // Reset matcher to scan from beginning
        matcher.reset();

        // Get safe hostnames list (using ignore_exact)
        List<String> safeHostnames = config.getStrings().getPatterns().getHostnames().getIgnoreExact();

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String matched = matcher.group();

            // Check if this is a safe hostname
            boolean isSafe = false;
            for (String safe : safeHostnames) {
                if (matched.equalsIgnoreCase(safe)) {
                    isSafe = true;
                    break;
                }
            }

            if (isSafe) {
                // Don't redact safe hostnames - keep original
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matched));
            } else {
                // Redact this hostname
                String replacement = pseudonymizer.isEnabled()
                    ? pseudonymizer.pseudonymize(matched, config.getGeneral().getRedactionText())
                    : config.getGeneral().getRedactionText();
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String replaceSafeIpMatches(Matcher matcher, String value) {
        // Reset matcher to scan from beginning
        matcher.reset();

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String matched = matcher.group();

            // Check if this is localhost/loopback IP
            boolean isSafe = matched.equals("127.0.0.1") || matched.startsWith("127.") ||
                            matched.equals("::1");

            if (isSafe) {
                // Don't redact safe IPs - keep original
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matched));
            } else {
                // Redact this IP
                String replacement = pseudonymizer.isEnabled()
                    ? pseudonymizer.pseudonymize(matched, config.getGeneral().getRedactionText())
                    : config.getGeneral().getRedactionText();
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Replace only a specific capture group in matches, preserving the rest of the match.
     * This is used for patterns like /Users/([^/]+) where we want to redact only the username,
     * not the entire path structure.
     */
    private String replaceCaptureGroup(Matcher matcher, String value, int groupNum, String patternKey) {
        // Reset matcher to scan from beginning
        matcher.reset();

        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            // Check if the capture group exists
            if (matcher.groupCount() < groupNum) {
                continue;
            }

            String capturedValue = matcher.group(groupNum);
            if (capturedValue == null || capturedValue.isEmpty()) {
                continue;
            }

            // Get the redaction replacement
            String replacement = pseudonymizer.isEnabled()
                ? pseudonymizer.pseudonymize(capturedValue, config.getGeneral().getRedactionText())
                : config.getGeneral().getRedactionText();

            // Append text before this match
            sb.append(value, lastEnd, matcher.start());

            // Build the replacement keeping the parts before and after the capture group
            String fullMatch = matcher.group();
            int groupStartInMatch = matcher.start(groupNum) - matcher.start();
            int groupEndInMatch = matcher.end(groupNum) - matcher.start();

            sb.append(fullMatch, 0, groupStartInMatch);
            sb.append(replacement);
            sb.append(fullMatch, groupEndInMatch, fullMatch.length());

            lastEnd = matcher.end();
        }

        // Append remaining text
        sb.append(value, lastEnd, value.length());
        return sb.toString();
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

    /**
     * Get the configuration used by this redaction engine.
     */
    public RedactionConfig getConfig() {
        return config;
    }

    // ========== Discovered Patterns Support ==========

    /**
     * Set discovered patterns to be used for redaction.
     * Discovered patterns have lower priority than configured patterns.
     *
     * @param patterns The discovered patterns from the discovery phase (already filtered by min_occurrences)
     */
    public void setDiscoveredPatterns(DiscoveredPatterns patterns) {
        this.discoveredPatterns = patterns;
        if (patterns != null) {
            int count = patterns.getTotalCount();
            logger.info("Loaded {} discovered patterns for redaction", count);
        }
    }

    /**
     * Get the currently set discovered patterns.
     */
    public DiscoveredPatterns getDiscoveredPatterns() {
        return discoveredPatterns;
    }

    /**
     * Apply discovered patterns to a string value.
     * Only redacts values that were discovered in the discovery phase.
     * The patterns are already filtered by their individual min_occurrences thresholds.
     */
    private String applyDiscoveredPatterns(String value) {
        if (discoveredPatterns == null || value == null || value.isEmpty()) {
            return value;
        }

        List<DiscoveredPatterns.DiscoveredValue> values = discoveredPatterns.getValues(1);

        if (values.isEmpty()) {
            return value;
        }

        String result = value;
        boolean caseSensitive = discoveredPatterns.isCaseSensitive();

        // Cache toLowerCase() result if case-insensitive (avoid repeated allocations)
        String lowerValue = caseSensitive ? null : value.toLowerCase();

        // Note: values are already sorted by length (longest first) from discovery phase
        // No need to sort again on every call

        for (DiscoveredPatterns.DiscoveredValue discovered : values) {
            String toFind = discovered.getValue();

            // Fast check before replacement - avoid toLowerCase if case-sensitive
            boolean found;
            if (caseSensitive) {
                found = result.contains(toFind);
            } else {
                // Use cached lowercase for comparison
                if (lowerValue == null) {
                    lowerValue = result.toLowerCase();
                }
                found = lowerValue.contains(toFind.toLowerCase());
            }

            if (found) {
                // Replace all occurrences
                String replacement = pseudonymizer.isEnabled()
                    ? pseudonymizer.pseudonymize(toFind, config.getGeneral().getRedactionText())
                    : config.getGeneral().getRedactionText();

                // Use case-sensitive or case-insensitive replacement
                if (caseSensitive) {
                    result = result.replace(toFind, replacement);
                } else {
                    // Case-insensitive replacement
                    result = replaceCaseInsensitive(result, toFind, replacement);
                    // Update cached lowercase since result changed
                    lowerValue = result.toLowerCase();
                }

                logger.trace("Redacted discovered {} value '{}' in text",
                           discovered.getType(), toFind);
            }
        }

        return result;
    }

    /**
     * Replace all occurrences of toFind in text with replacement, case-insensitively.
     */
    private String replaceCaseInsensitive(String text, String toFind, String replacement) {
        if (toFind.isEmpty()) return text;

        String lowerText = text.toLowerCase();
        String lowerToFind = toFind.toLowerCase();
        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        int index;

        while ((index = lowerText.indexOf(lowerToFind, lastIndex)) != -1) {
            result.append(text, lastIndex, index);
            result.append(replacement);
            lastIndex = index + toFind.length();
        }
        result.append(text.substring(lastIndex));

        return result.toString();
    }
}