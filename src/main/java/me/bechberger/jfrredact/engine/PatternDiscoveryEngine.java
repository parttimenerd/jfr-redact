package me.bechberger.jfrredact.engine;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import me.bechberger.jfrredact.config.DiscoveryConfig;
import me.bechberger.jfrredact.config.StringConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine for discovering sensitive patterns from JFR events and text content.
 * <p>
 * Extracts values from known patterns (e.g., usernames from /Users/username)
 * to build a set of patterns that should be redacted.
 * <p>
 * Each pattern type has its own DiscoveredPatterns instance with individual settings
 * for min_occurrences, case_sensitive, and whitelist.
 */
public class PatternDiscoveryEngine {

    private static final Logger logger = LoggerFactory.getLogger(PatternDiscoveryEngine.class);

    private final DiscoveryConfig discoveryConfig;
    private final StringConfig stringConfig;
    private InteractiveDecisionManager interactiveDecisionManager;

    // Per-pattern discovered patterns with their own settings
    private final List<PatternExtractor> extractors = new ArrayList<>();

    // Property-based extractors for JFR event properties
    private final List<PropertyExtractor> propertyExtractors = new ArrayList<>();

    /**
     * Represents a property-based extractor for JFR event properties
     */
    private static class PropertyExtractor {
        final String name;
        final DiscoveredPatterns.PatternType type;
        final Pattern keyPattern;
        final String keyPropertyPattern;  // Property name for key in key-value pairs
        final Pattern valuePattern;  // Pattern to match value content
        final String valuePropertyPattern;  // Property name for value in key-value pairs
        final Pattern eventTypeFilter;  // null means all events
        final DiscoveredPatterns discoveredPatterns;
        final int minOccurrences;
        final List<String> whitelist;

        PropertyExtractor(String name, DiscoveredPatterns.PatternType type, Pattern keyPattern,
                         String keyPropertyPattern, Pattern valuePattern, String valuePropertyPattern,
                         Pattern eventTypeFilter, boolean caseSensitive, List<String> whitelist,
                         int minOccurrences) {
            this.name = name;
            this.type = type;
            this.keyPattern = keyPattern;
            this.keyPropertyPattern = keyPropertyPattern != null ? keyPropertyPattern : "key";
            this.valuePattern = valuePattern;
            this.valuePropertyPattern = valuePropertyPattern != null ? valuePropertyPattern : "value";
            this.eventTypeFilter = eventTypeFilter;
            this.discoveredPatterns = new DiscoveredPatterns(caseSensitive, whitelist != null ? whitelist : List.of());
            this.minOccurrences = minOccurrences;
            this.whitelist = whitelist != null ? whitelist : List.of();
        }
    }

    /**
     * Represents a pattern extractor with its own discovery settings and discovered patterns
     */
    private static class PatternExtractor {
        final String name;
        final DiscoveredPatterns.PatternType type;
        final Pattern pattern;
        final int captureGroup;
        final DiscoveredPatterns discoveredPatterns;
        final int minOccurrences;
        final List<String> ignoreExact;  // From ignore_exact in pattern config
        final List<Pattern> ignorePatterns;  // From ignore in pattern config

        PatternExtractor(String name, DiscoveredPatterns.PatternType type, Pattern pattern,
                        int captureGroup, boolean caseSensitive, List<String> whitelist,
                        int minOccurrences, List<String> ignoreExact, List<String> ignoreRegexes) {
            this.name = name;
            this.type = type;
            this.pattern = pattern;
            this.captureGroup = captureGroup;
            this.discoveredPatterns = new DiscoveredPatterns(caseSensitive, whitelist);
            this.minOccurrences = minOccurrences;
            this.ignoreExact = ignoreExact != null ? ignoreExact : List.of();
            this.ignorePatterns = ignoreRegexes != null ?
                ignoreRegexes.stream().map(Pattern::compile).toList() : List.of();
        }
    }

    public PatternDiscoveryEngine(DiscoveryConfig discoveryConfig, StringConfig stringConfig) {
        this.discoveryConfig = discoveryConfig;
        this.stringConfig = stringConfig;
        compilePatterns();
    }

    /**
     * Set interactive decision manager for interactive mode
     */
    public void setInteractiveDecisionManager(InteractiveDecisionManager manager) {
        this.interactiveDecisionManager = manager;
    }

    private void compilePatterns() {
        // Compile property-based extractors (always, independent of string config)
        for (DiscoveryConfig.PropertyExtractionConfig propExtraction : discoveryConfig.getPropertyExtractions()) {
            if (propExtraction.isEnabled() && propExtraction.getKeyPattern() != null &&
                !propExtraction.getKeyPattern().isEmpty()) {
                try {
                    Pattern keyPattern = Pattern.compile(propExtraction.getKeyPattern());
                    Pattern valuePattern = Pattern.compile(
                        propExtraction.getValuePattern() != null ? propExtraction.getValuePattern() : ".*"
                    );
                    Pattern eventTypeFilter = null;

                    if (propExtraction.getEventTypeFilter() != null &&
                        !propExtraction.getEventTypeFilter().isEmpty()) {
                        eventTypeFilter = Pattern.compile(propExtraction.getEventTypeFilter());
                    }

                    // Parse the pattern type
                    DiscoveredPatterns.PatternType patternType;
                    try {
                        patternType = DiscoveredPatterns.PatternType.valueOf(propExtraction.getType().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid type '{}' for property extraction '{}', using CUSTOM",
                                   propExtraction.getType(), propExtraction.getName());
                        patternType = DiscoveredPatterns.PatternType.CUSTOM;
                    }

                    propertyExtractors.add(new PropertyExtractor(
                        propExtraction.getName(),
                        patternType,
                        keyPattern,
                        propExtraction.getKeyPropertyPattern(),
                        valuePattern,
                        propExtraction.getValuePropertyPattern(),
                        eventTypeFilter,
                        propExtraction.isCaseSensitive(),
                        propExtraction.getWhitelist(),
                        propExtraction.getMinOccurrences()
                    ));
                    logger.debug("Compiled property extraction '{}' for key pattern '{}' (type: {}, kv: {}/{}, value pattern: {}, event filter: {})",
                               propExtraction.getName(), propExtraction.getKeyPattern(),
                               patternType, propExtraction.getKeyPropertyPattern(),
                               propExtraction.getValuePropertyPattern(),
                               propExtraction.getValuePattern(), propExtraction.getEventTypeFilter());
                } catch (Exception e) {
                    logger.warn("Failed to compile property extraction '{}': {}",
                               propExtraction.getName(), e.getMessage());
                }
            }
        }

        logger.info("Compiled {} property extractors total", propertyExtractors.size());

        // Compile string-based patterns
        if (!stringConfig.isEnabled()) {
            return;
        }

        var patterns = stringConfig.getPatterns();

        // User name patterns (USERNAME discovery from user directories)
        if (patterns.getUser().isEnabled() && patterns.getUser().isEnableDiscovery()) {
            addPatternExtractors(
                patterns.getUser(),
                patterns.getUser().getPatterns(),
                "user",
                DiscoveredPatterns.PatternType.USERNAME
            );
        }

        // SSH host patterns (HOSTNAME discovery)
        if (patterns.getSshHosts().isEnabled() && patterns.getSshHosts().isEnableDiscovery()) {
            addPatternExtractors(
                patterns.getSshHosts(),
                patterns.getSshHosts().getPatterns(),
                "ssh_host",
                DiscoveredPatterns.PatternType.HOSTNAME
            );
        }

        // Hostname patterns (HOSTNAME discovery)
        if (patterns.getHostnames().isEnabled() && patterns.getHostnames().isEnableDiscovery()) {
            addPatternExtractors(
                patterns.getHostnames(),
                patterns.getHostnames().getPatterns(),
                "hostname",
                DiscoveredPatterns.PatternType.HOSTNAME
            );
        }

        // Email patterns (EMAIL_LOCAL_PART discovery)
        if (patterns.getEmails().isEnabled() && patterns.getEmails().isEnableDiscovery()) {
            String emailRegex = "([a-zA-Z0-9._%+-]+)@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";
            addPatternExtractor(
                patterns.getEmails(),
                emailRegex,
                "email",
                DiscoveredPatterns.PatternType.EMAIL_LOCAL_PART
            );
        }

        // UUID patterns (CUSTOM discovery)
        if (patterns.getUuids().isEnabled() && patterns.getUuids().isEnableDiscovery()) {
            addPatternExtractors(
                patterns.getUuids(),
                patterns.getUuids().getPatterns(),
                "uuid",
                DiscoveredPatterns.PatternType.CUSTOM
            );
        }

        // IP address patterns (CUSTOM discovery)
        if (patterns.getIpAddresses().isEnabled() && patterns.getIpAddresses().isEnableDiscovery()) {
            List<String> ipPatterns = patterns.getIpAddresses().getPatterns();
            if (ipPatterns.size() >= 1 && !ipPatterns.get(0).isEmpty()) {
                addPatternExtractor(
                    patterns.getIpAddresses(),
                    ipPatterns.get(0),
                    "ipv4",
                    DiscoveredPatterns.PatternType.CUSTOM
                );
            }
            if (ipPatterns.size() >= 2 && !ipPatterns.get(1).isEmpty()) {
                addPatternExtractor(
                    patterns.getIpAddresses(),
                    ipPatterns.get(1),
                    "ipv6",
                    DiscoveredPatterns.PatternType.CUSTOM
                );
            }
        }

        // Internal URL patterns (CUSTOM discovery)
        if (patterns.getInternalUrls().isEnabled() && patterns.getInternalUrls().isEnableDiscovery()) {
            addPatternExtractors(
                patterns.getInternalUrls(),
                patterns.getInternalUrls().getPatterns(),
                "internal_url",
                DiscoveredPatterns.PatternType.CUSTOM
            );
        }

        // Custom string patterns
        for (StringConfig.CustomPatternConfig custom : patterns.getCustom()) {
            if (!custom.getPatterns().isEmpty() && custom.isEnableDiscovery()) {
                addPatternExtractors(
                    custom,
                    custom.getPatterns(),
                    custom.getName() != null ? custom.getName() : "custom_pattern",
                    DiscoveredPatterns.PatternType.CUSTOM
                );
            }
        }

        // Custom extraction patterns from DiscoveryConfig
        for (DiscoveryConfig.CustomExtractionConfig custom : discoveryConfig.getCustomExtractions()) {
            if (custom.isEnabled() && custom.getPattern() != null && !custom.getPattern().isEmpty()) {
                try {
                    Pattern pattern = Pattern.compile(custom.getPattern());

                    // Parse the pattern type
                    DiscoveredPatterns.PatternType patternType;
                    try {
                        patternType = DiscoveredPatterns.PatternType.valueOf(custom.getType().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid type '{}' for custom extraction '{}', using CUSTOM",
                                   custom.getType(), custom.getName());
                        patternType = DiscoveredPatterns.PatternType.CUSTOM;
                    }

                    extractors.add(new PatternExtractor(
                        custom.getName(),
                        patternType,
                        pattern,
                        custom.getCaptureGroup(),
                        custom.isCaseSensitive(),
                        custom.getWhitelist(),
                        custom.getMinOccurrences(),
                        List.of(), // no ignore_exact (not applicable to custom extractions)
                        List.of()  // no ignore patterns
                    ));
                    logger.debug("Compiled custom extraction pattern '{}' (type: {}, case sensitive: {}, min occurrences: {})",
                               custom.getName(), patternType, custom.isCaseSensitive(), custom.getMinOccurrences());
                } catch (Exception e) {
                    logger.warn("Failed to compile custom extraction pattern '{}': {}",
                               custom.getName(), e.getMessage());
                }
            }
        }

        logger.info("Compiled {} extraction patterns total", extractors.size());
    }

    /**
     * Helper to add multiple pattern extractors from a list of regex patterns
     */
    private void addPatternExtractors(StringConfig.BasePatternConfig config, List<String> regexes,
                                     String baseName, DiscoveredPatterns.PatternType type) {
        for (int i = 0; i < regexes.size(); i++) {
            String name = regexes.size() > 1 ? baseName + "_" + i : baseName;
            addPatternExtractor(config, regexes.get(i), name, type);
        }
    }

    /**
     * Helper to add a single pattern extractor
     */
    private void addPatternExtractor(StringConfig.BasePatternConfig config, String regex,
                                     String name, DiscoveredPatterns.PatternType type) {
        try {
            Pattern pattern = Pattern.compile(regex);
            extractors.add(new PatternExtractor(
                name,
                type,
                pattern,
                config.getDiscoveryCaptureGroup(),
                config.isDiscoveryCaseSensitive(),
                config.getDiscoveryWhitelist(),
                config.getDiscoveryMinOccurrences(),
                config.getIgnoreExact(),
                config.getIgnore()
            ));
            logger.debug("Compiled pattern '{}' for {} discovery (capture group: {}, min occurrences: {}, case sensitive: {})",
                        name, type, config.getDiscoveryCaptureGroup(),
                        config.getDiscoveryMinOccurrences(), config.isDiscoveryCaseSensitive());
        } catch (Exception e) {
            logger.warn("Failed to compile pattern '{}': {}", name, e.getMessage());
        }
    }

    /**
     * Analyze a JFR event for discoverable patterns
     */
    public void analyzeEvent(RecordedEvent event) {
        // Extract from properties first
        if (!propertyExtractors.isEmpty()) {
            analyzeEventProperties(event);
        }

        // Analyze all string fields in the event
        analyzeRecordedObject(event);
    }

    /**
     * Analyze JFR event properties for discoverable patterns.
     * Looks for properties with keys matching configured patterns and extracts their values.
     *
     * Supports two modes:
     * 1. Direct field matching: event.userName = "john"
     * 2. Key-value pair matching: event.key = "user.name", event.value = "john"
     */
    private void analyzeEventProperties(RecordedEvent event) {
        String eventType = event.getEventType().getName();

        for (PropertyExtractor extractor : propertyExtractors) {
            // Check if event type matches filter (if specified)
            if (extractor.eventTypeFilter != null &&
                !extractor.eventTypeFilter.matcher(eventType).matches()) {
                continue;  // Skip this extractor for this event type
            }

            // Mode 1: Direct field matching - look through all fields to find matching property keys
            for (var field : event.getFields()) {
                String fieldName = field.getName();

                if (extractor.keyPattern.matcher(fieldName).matches()) {
                    Object value = event.getValue(fieldName);

                    // Only process string values
                    if (value instanceof String) {
                        String stringValue = (String) value;

                        if (stringValue != null && !stringValue.isEmpty()) {
                            // Check whitelist
                            if (!isWhitelisted(stringValue, extractor)) {
                                extractor.discoveredPatterns.addValue(
                                    stringValue,
                                    extractor.type,
                                    extractor.type == DiscoveredPatterns.PatternType.CUSTOM ? extractor.name : null
                                );
                                logger.trace("Discovered {} value '{}' from property '{}' in event '{}'",
                                           extractor.type, stringValue, fieldName, eventType);
                            }
                        }
                    }
                }
            }

            // Mode 2: Key-value pair matching - check if event has key and value fields
            try {
                Object keyObj = event.getValue(extractor.keyPropertyPattern);
                Object valueObj = event.getValue(extractor.valuePropertyPattern);

                if (keyObj instanceof String && valueObj instanceof String) {
                    String keyStr = (String) keyObj;
                    String valueStr = (String) valueObj;

                    // Check if the key matches the key pattern and value matches value pattern
                    if (extractor.keyPattern.matcher(keyStr).matches() &&
                        extractor.valuePattern.matcher(valueStr).matches()) {
                        if (valueStr != null && !valueStr.isEmpty()) {
                            // Check whitelist
                            if (!isWhitelisted(valueStr, extractor)) {
                                extractor.discoveredPatterns.addValue(
                                    valueStr,
                                    extractor.type,
                                    extractor.type == DiscoveredPatterns.PatternType.CUSTOM ? extractor.name : null
                                );
                                logger.trace("Discovered {} value '{}' from key-value pair ({}='{}', {}='{}') in event '{}'",
                                           extractor.type, valueStr, extractor.keyPropertyPattern, keyStr,
                                           extractor.valuePropertyPattern, valueStr, eventType);
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                // No key or value fields - that's fine, skip mode 2
            }
        }
    }

    /**
     * Check if a value is whitelisted
     */
    private boolean isWhitelisted(String value, PropertyExtractor extractor) {
        for (String whitelistItem : extractor.whitelist) {
            if (value.equalsIgnoreCase(whitelistItem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively analyze a RecordedObject (event or nested object)
     */
    private void analyzeRecordedObject(RecordedObject obj) {
        if (obj == null) {
            return;
        }

        for (var field : obj.getFields()) {
            Object value = obj.getValue(field.getName());

            if (value instanceof String) {
                analyzeLine((String) value);
            } else if (value instanceof RecordedObject) {
                analyzeRecordedObject((RecordedObject) value);
            } else if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof RecordedObject) {
                        analyzeRecordedObject((RecordedObject) item);
                    } else if (item instanceof String) {
                        analyzeLine((String) item);
                    }
                }
            }
        }
    }

    /**
     * Analyze a text line for discoverable patterns
     */
    public void analyzeLine(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }

        // Apply all extraction patterns
        for (PatternExtractor extractor : extractors) {
            Matcher matcher = extractor.pattern.matcher(line);
            while (matcher.find()) {
                try {
                    String extracted;
                    // If capture group is 0 or no groups, use entire match
                    if (extractor.captureGroup == 0 || matcher.groupCount() == 0) {
                        extracted = matcher.group();
                    } else if (matcher.groupCount() >= extractor.captureGroup) {
                        extracted = matcher.group(extractor.captureGroup);
                    } else {
                        // Capture group doesn't exist, skip
                        logger.trace("Pattern '{}' has no capture group {}, skipping",
                                   extractor.name, extractor.captureGroup);
                        continue;
                    }

                    if (extracted != null && !extracted.isEmpty()) {
                        // Check if this value should be ignored based on ignore_exact and ignore patterns
                        if (shouldIgnoreValue(extracted, extractor)) {
                            logger.trace("Ignoring value '{}' from pattern '{}' (matches ignore list)",
                                       extracted, extractor.name);
                            continue;
                        }

                        extractor.discoveredPatterns.addValue(
                            extracted,
                            extractor.type,
                            extractor.type == DiscoveredPatterns.PatternType.CUSTOM ? extractor.name : null
                        );
                        logger.trace("Discovered {} value '{}' via pattern '{}'",
                                   extractor.type, extracted, extractor.name);
                    }
                } catch (IndexOutOfBoundsException e) {
                    logger.warn("Invalid capture group {} for pattern '{}'",
                               extractor.captureGroup, extractor.name);
                }
            }
        }
    }

    /**
     * Check if a value should be ignored based on the pattern's ignore lists
     */
    private boolean shouldIgnoreValue(String value, PatternExtractor extractor) {
        // Check ignore_exact (case-insensitive)
        for (String exact : extractor.ignoreExact) {
            if (value.equalsIgnoreCase(exact)) {
                return true;
            }
        }

        // Check ignore patterns (regex)
        for (Pattern ignorePattern : extractor.ignorePatterns) {
            if (ignorePattern.matcher(value).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the discovered patterns (merged from all extractors)
     */
    public DiscoveredPatterns getDiscoveredPatterns() {
        // Merge all discovered patterns, respecting min_occurrences for each
        // We create a combined DiscoveredPatterns with case_sensitive=false (most permissive)
        // Each extractor's patterns are filtered by their own min_occurrences before merging
        DiscoveredPatterns combined = new DiscoveredPatterns(false, List.of());

        for (PatternExtractor extractor : extractors) {
            // Get values that meet this extractor's min_occurrences threshold
            List<DiscoveredPatterns.DiscoveredValue> values =
                extractor.discoveredPatterns.getValues(extractor.minOccurrences);

            // Add them to the combined set
            for (DiscoveredPatterns.DiscoveredValue value : values) {
                for (int i = 0; i < value.getOccurrences(); i++) {
                    combined.addValue(value.getValue(), value.getType(), value.getCustomTypeName());
                }
            }
        }

        // Also merge property extractors
        for (PropertyExtractor extractor : propertyExtractors) {
            List<DiscoveredPatterns.DiscoveredValue> values =
                extractor.discoveredPatterns.getValues(extractor.minOccurrences);

            for (DiscoveredPatterns.DiscoveredValue value : values) {
                for (int i = 0; i < value.getOccurrences(); i++) {
                    combined.addValue(value.getValue(), value.getType(), value.getCustomTypeName());
                }
            }
        }

        return combined;
    }

    /**
     * Get discovery statistics
     */
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("Discovery Statistics:\n");

        int totalValues = 0;
        for (PatternExtractor extractor : extractors) {
            int count = extractor.discoveredPatterns.getValues(extractor.minOccurrences).size();
            if (count > 0) {
                totalValues += count;
                sb.append(String.format("  %s (%s): %d values (min occurrences: %d)\n",
                                       extractor.name, extractor.type, count, extractor.minOccurrences));
            }
        }

        for (PropertyExtractor extractor : propertyExtractors) {
            int count = extractor.discoveredPatterns.getValues(extractor.minOccurrences).size();
            if (count > 0) {
                totalValues += count;
                sb.append(String.format("  %s [property] (%s): %d values (min occurrences: %d)\n",
                                       extractor.name, extractor.type, count, extractor.minOccurrences));
            }
        }

        sb.append(String.format("  Total discovered values: %d\n", totalValues));

        if (logger.isDebugEnabled()) {
            sb.append("\nDiscovered values by pattern:\n");
            for (PatternExtractor extractor : extractors) {
                List<DiscoveredPatterns.DiscoveredValue> values =
                    extractor.discoveredPatterns.getValues(extractor.minOccurrences);
                if (!values.isEmpty()) {
                    sb.append(String.format("  %s:\n", extractor.name));
                    for (DiscoveredPatterns.DiscoveredValue value : values) {
                        sb.append(String.format("    - %s\n", value));
                    }
                }
            }

            for (PropertyExtractor extractor : propertyExtractors) {
                List<DiscoveredPatterns.DiscoveredValue> values =
                    extractor.discoveredPatterns.getValues(extractor.minOccurrences);
                if (!values.isEmpty()) {
                    sb.append(String.format("  %s [property]:\n", extractor.name));
                    for (DiscoveredPatterns.DiscoveredValue value : values) {
                        sb.append(String.format("    - %s\n", value));
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * Apply interactive decisions to discovered patterns.
     * Prompts user for each discovered value and filters based on their decisions.
     * In interactive mode, ignore lists from config are ignored.
     *
     * @param patterns The discovered patterns to filter
     * @return A new DiscoveredPatterns with only values the user wants to redact
     */
    public DiscoveredPatterns applyInteractiveDecisions(DiscoveredPatterns patterns) {
        if (interactiveDecisionManager == null || !interactiveDecisionManager.isInteractive()) {
            return patterns;
        }

        logger.info("Entering interactive mode - prompting for redaction decisions...");
        logger.info("Note: Ignore lists from config are bypassed in interactive mode");

        DiscoveredPatterns filtered = new DiscoveredPatterns(patterns.isCaseSensitive(), List.of());
        List<DiscoveredPatterns.DiscoveredValue> allValues = patterns.getAllValues();

        // Track global policies
        boolean keepAllUsernames = false;
        boolean redactAllUsernames = false;
        boolean keepAllHostnames = false;
        boolean redactAllHostnames = false;

        for (DiscoveredPatterns.DiscoveredValue value : allValues) {
            // Check for global policies
            if (value.getType() == DiscoveredPatterns.PatternType.USERNAME) {
                if (keepAllUsernames) {
                    continue; // Skip - keeping all usernames
                }
                if (redactAllUsernames) {
                    // Add to filtered list for redaction
                    for (int i = 0; i < value.getOccurrences(); i++) {
                        filtered.addValue(value.getValue(), value.getType(), value.getCustomTypeName());
                    }
                    continue;
                }
            } else if (value.getType() == DiscoveredPatterns.PatternType.HOSTNAME) {
                if (keepAllHostnames) {
                    continue; // Skip - keeping all hostnames
                }
                if (redactAllHostnames) {
                    // Add to filtered list for redaction
                    for (int i = 0; i < value.getOccurrences(); i++) {
                        filtered.addValue(value.getValue(), value.getType(), value.getCustomTypeName());
                    }
                    continue;
                }
            }

            // Get decision for this value
            InteractiveDecisionManager.Decision decision = interactiveDecisionManager.getDecision(value);

            // Handle global policy decisions
            if (decision.getAction() == InteractiveDecisionManager.DecisionAction.KEEP_ALL) {
                if (value.getType() == DiscoveredPatterns.PatternType.USERNAME) {
                    keepAllUsernames = true;
                    logger.info("Policy set: Keep all usernames");
                } else if (value.getType() == DiscoveredPatterns.PatternType.HOSTNAME) {
                    keepAllHostnames = true;
                    logger.info("Policy set: Keep all hostnames");
                }
                continue; // Don't add to redaction list
            }

            if (decision.getAction() == InteractiveDecisionManager.DecisionAction.REDACT_ALL) {
                if (value.getType() == DiscoveredPatterns.PatternType.USERNAME) {
                    redactAllUsernames = true;
                    logger.info("Policy set: Redact all usernames");
                } else if (value.getType() == DiscoveredPatterns.PatternType.HOSTNAME) {
                    redactAllHostnames = true;
                    logger.info("Policy set: Redact all hostnames");
                }
                // Add this one and continue
                for (int i = 0; i < value.getOccurrences(); i++) {
                    filtered.addValue(value.getValue(), value.getType(), value.getCustomTypeName());
                }
                continue;
            }

            // Handle individual decisions
            switch (decision.getAction()) {
                case KEEP:
                    // Don't add to redaction list
                    logger.debug("Keeping: {}", value.getValue());
                    break;
                case REDACT:
                case REPLACE:
                    // Add to redaction list (replacement is handled elsewhere)
                    for (int i = 0; i < value.getOccurrences(); i++) {
                        filtered.addValue(value.getValue(), value.getType(), value.getCustomTypeName());
                    }
                    logger.debug("Will redact: {}", value.getValue());
                    break;
            }
        }

        logger.info("Interactive decisions complete: {} values to redact (from {} discovered)",
                   filtered.getTotalCount(), allValues.size());

        return filtered;
    }
}