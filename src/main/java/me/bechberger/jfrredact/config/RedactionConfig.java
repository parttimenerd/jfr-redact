package me.bechberger.jfrredact.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.bechberger.jfrredact.pseudonimyzer.Pseudonymizer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main redaction configuration with parent inheritance support.
 *
 * <p>List Inheritance Control:</p>
 * <p>By default, when a child config defines a list, it completely overrides the parent's list.
 * However, you can use the special marker <code>$PARENT</code> to include the parent's list items:</p>
 *
 * <pre>
 * # parent.yaml
 * properties:
 *   patterns:
 *     - password
 *     - secret
 *
 * # child.yaml (override - replaces parent completely)
 * properties:
 *   patterns:
 *     - apikey  # Only apikey, no password/secret
 *
 * # child.yaml (append - adds to parent)
 * properties:
 *   patterns:
 *     - $PARENT  # Expands to: password, secret
 *     - apikey   # Result: password, secret, apikey
 *
 * # child.yaml (prepend and append)
 * properties:
 *   patterns:
 *     - customkey
 *     - $PARENT  # Expands to: password, secret
 *     - apikey   # Result: customkey, password, secret, apikey
 * </pre>
 */
public class RedactionConfig {

    /** Marker for list inheritance - replaced with parent's list items */
    public static final String PARENT_MARKER = "$PARENT";

    @JsonProperty("parent")
    private String parent = "none";

    // ...existing code...
    private PropertyConfig properties = new PropertyConfig();

    @JsonProperty("events")
    private EventConfig events = new EventConfig();

    @JsonProperty("strings")
    private StringConfig strings = new StringConfig();

    @JsonProperty("paths")
    private PathConfig paths = new PathConfig();

    @JsonProperty("general")
    private GeneralConfig general = new GeneralConfig();

    @JsonProperty("discovery")
    private DiscoveryConfig discovery = new DiscoveryConfig();

    /**
     * General configuration options
     */
    public static class GeneralConfig {
        @JsonProperty("redaction_text")
        private String redactionText = "***";

        /**
         * Partial redaction shows some information while hiding sensitive parts.
         *
         * When false (default): "my_secret_password" -> "***"
         * When true: "my_secret_password" -> "my***" (shows prefix/suffix)
         *
         * Use cases:
         * - Debugging: Identify which value is being redacted without exposing the actual secret
         * - Compliance: Show the format/structure of data without revealing actual values
         * - Log analysis: Distinguish between different redacted values in the same recording
         * - Pattern detection: Understand data patterns while maintaining privacy
         *
         * Example with partial redaction enabled:
         * - "admin_password_123" -> "ad***"
         * - "user_secret_xyz" -> "us***"
         * - "api_key_production" -> "ap***"
         *
         * This allows you to see that three different values were redacted,
         * which can be crucial for debugging issues where the wrong credential is being used.
         */
        @JsonProperty("partial_redaction")
        private boolean partialRedaction = false;

        @JsonProperty("pseudonymization")
        private PseudonymizationConfig pseudonymization = new PseudonymizationConfig();

        // Getters and setters
        public String getRedactionText() { return redactionText; }
        public void setRedactionText(String redactionText) { this.redactionText = redactionText; }

        public boolean isPartialRedaction() { return partialRedaction; }
        public void setPartialRedaction(boolean partialRedaction) {
            this.partialRedaction = partialRedaction;
        }

        public PseudonymizationConfig getPseudonymization() { return pseudonymization; }
        public void setPseudonymization(PseudonymizationConfig pseudonymization) {
            this.pseudonymization = pseudonymization;
        }
    }

    // Getters and setters
    public String getParent() { return parent; }
    public void setParent(String parent) { this.parent = parent; }

    public PropertyConfig getProperties() { return properties; }
    public void setProperties(PropertyConfig properties) { this.properties = properties; }

    public EventConfig getEvents() { return events; }
    public void setEvents(EventConfig events) { this.events = events; }

    public StringConfig getStrings() { return strings; }
    public void setStrings(StringConfig strings) { this.strings = strings; }

    public PathConfig getPaths() { return paths; }
    public void setPaths(PathConfig paths) { this.paths = paths; }

    public GeneralConfig getGeneral() { return general; }
    public void setGeneral(GeneralConfig general) { this.general = general; }

    public DiscoveryConfig getDiscovery() { return discovery; }
    public void setDiscovery(DiscoveryConfig discovery) { this.discovery = discovery; }

    /**
     * Merge this configuration with a parent configuration.
     * Child values override parent values. Lists are combined.
     *
     * @param parentConfig The parent configuration
     */
    public void mergeWith(RedactionConfig parentConfig) {
        if (parentConfig == null) return;

        // Merge each section
        properties.mergeWith(parentConfig.getProperties());
        events.mergeWith(parentConfig.getEvents());
        strings.mergeWith(parentConfig.getStrings());
        paths.mergeWith(parentConfig.getPaths());
        general.getPseudonymization().mergeWith(
            parentConfig.getGeneral().getPseudonymization()
        );
    }

    /**
     * Apply CLI options to override configuration values
     */
    public void applyCliOptions(CliOptions cliOptions) {
        if (cliOptions == null) return;

        // Apply pseudonymize flag and mode
        if (cliOptions.isPseudonymize()) {
            general.getPseudonymization().setEnabled(true);
        }
        if (cliOptions.getPseudonymizeMode() != null) {
            general.getPseudonymization().setMode(cliOptions.getPseudonymizeMode());
        }
        if (cliOptions.getSeed() != null) {
            general.getPseudonymization().setSeed(cliOptions.getSeed());
        }

        // Add additional events to remove
        if (cliOptions.getRemoveEvents() != null) {
            events.getRemovedTypes().addAll(cliOptions.getRemoveEvents());
        }

        // Apply jfr scrub-style filtering options
        EventConfig.FilteringConfig filtering = events.getFiltering();
        if (cliOptions.getIncludeEvents() != null && !cliOptions.getIncludeEvents().isEmpty()) {
            filtering.getIncludeEvents().addAll(cliOptions.getIncludeEvents());
        }
        if (cliOptions.getExcludeEvents() != null && !cliOptions.getExcludeEvents().isEmpty()) {
            filtering.getExcludeEvents().addAll(cliOptions.getExcludeEvents());
        }
        if (cliOptions.getIncludeCategories() != null && !cliOptions.getIncludeCategories().isEmpty()) {
            filtering.getIncludeCategories().addAll(cliOptions.getIncludeCategories());
        }
        if (cliOptions.getExcludeCategories() != null && !cliOptions.getExcludeCategories().isEmpty()) {
            filtering.getExcludeCategories().addAll(cliOptions.getExcludeCategories());
        }
        if (cliOptions.getIncludeThreads() != null && !cliOptions.getIncludeThreads().isEmpty()) {
            filtering.getIncludeThreads().addAll(cliOptions.getIncludeThreads());
        }
        if (cliOptions.getExcludeThreads() != null && !cliOptions.getExcludeThreads().isEmpty()) {
            filtering.getExcludeThreads().addAll(cliOptions.getExcludeThreads());
        }

        // Add additional redaction regexes
        if (cliOptions.getRedactionRegexes() != null && !cliOptions.getRedactionRegexes().isEmpty()) {
            List<StringConfig.CustomPatternConfig> customPatterns = strings.getPatterns().getCustom();
            for (String regex : cliOptions.getRedactionRegexes()) {
                StringConfig.CustomPatternConfig customPattern = new StringConfig.CustomPatternConfig();
                customPattern.setName("cli_pattern_" + customPatterns.size());
                customPattern.setPatterns(List.of(regex));
                customPatterns.add(customPattern);
            }
        }

        // Apply discovery options
        if (cliOptions.getDiscoveryMode() != null) {
            discovery.setMode(cliOptions.getDiscoveryMode());
        }
    }

    /**
     * Create a Pseudonymizer from this configuration
     */
    public Pseudonymizer createPseudonymizer() {
        PseudonymizationConfig pConfig = general.getPseudonymization();

        if (!pConfig.isEnabled()) {
            return Pseudonymizer.disabled();
        }

        Pseudonymizer.PseudonymizationScope scope = new Pseudonymizer.PseudonymizationScope(
            pConfig.getScope().isProperties(),
            pConfig.getScope().isStrings(),
            pConfig.getScope().isPaths(),
            pConfig.getScope().isPorts()
        );

        return Pseudonymizer.builder()
            .enabled(true)
            .mode(me.bechberger.jfrredact.pseudonimyzer.PseudonymizationMode.fromString(pConfig.getMode()))
            .format(me.bechberger.jfrredact.pseudonimyzer.PseudonymizationFormat.fromString(pConfig.getFormat()))
            .customPrefix(pConfig.getCustomPrefix())
            .customSuffix(pConfig.getCustomSuffix())
            .hashLength(pConfig.getHashLength())
            .hashAlgorithm(pConfig.getHashAlgorithm())
            .scope(scope)
            .customReplacements(pConfig.getReplacements())
            .patternGenerators(pConfig.getPatternGenerators())
            .build();
    }

    /**
     * CLI options to be applied on top of configuration
     */
    public static class CliOptions {
        private boolean pseudonymize;
        private String pseudonymizeMode;  // "hash", "counter", or "realistic"
        private Long seed;  // Seed for reproducible pseudonymization
        private List<String> removeEvents = new ArrayList<>();
        private List<String> redactionRegexes = new ArrayList<>();

        // Discovery options
        private DiscoveryConfig.DiscoveryMode discoveryMode;

        // jfr scrub-style filtering options
        private List<String> includeEvents = new ArrayList<>();
        private List<String> excludeEvents = new ArrayList<>();
        private List<String> includeCategories = new ArrayList<>();
        private List<String> excludeCategories = new ArrayList<>();
        private List<String> includeThreads = new ArrayList<>();
        private List<String> excludeThreads = new ArrayList<>();

        public boolean isPseudonymize() { return pseudonymize; }
        public void setPseudonymize(boolean pseudonymize) { this.pseudonymize = pseudonymize; }

        public String getPseudonymizeMode() { return pseudonymizeMode; }
        public void setPseudonymizeMode(String pseudonymizeMode) { this.pseudonymizeMode = pseudonymizeMode; }

        public Long getSeed() { return seed; }
        public void setSeed(Long seed) { this.seed = seed; }

        public List<String> getRemoveEvents() { return removeEvents; }
        public void setRemoveEvents(List<String> removeEvents) { this.removeEvents = removeEvents; }

        public List<String> getRedactionRegexes() { return redactionRegexes; }
        public void setRedactionRegexes(List<String> redactionRegexes) {
            this.redactionRegexes = redactionRegexes;
        }

        public DiscoveryConfig.DiscoveryMode getDiscoveryMode() { return discoveryMode; }
        public void setDiscoveryMode(DiscoveryConfig.DiscoveryMode discoveryMode) { this.discoveryMode = discoveryMode; }

        public List<String> getIncludeEvents() { return includeEvents; }
        public void setIncludeEvents(List<String> includeEvents) { this.includeEvents = includeEvents; }

        public List<String> getExcludeEvents() { return excludeEvents; }
        public void setExcludeEvents(List<String> excludeEvents) { this.excludeEvents = excludeEvents; }

        public List<String> getIncludeCategories() { return includeCategories; }
        public void setIncludeCategories(List<String> includeCategories) { this.includeCategories = includeCategories; }

        public List<String> getExcludeCategories() { return excludeCategories; }
        public void setExcludeCategories(List<String> excludeCategories) { this.excludeCategories = excludeCategories; }

        public List<String> getIncludeThreads() { return includeThreads; }
        public void setIncludeThreads(List<String> includeThreads) { this.includeThreads = includeThreads; }

        public List<String> getExcludeThreads() { return excludeThreads; }
        public void setExcludeThreads(List<String> excludeThreads) { this.excludeThreads = excludeThreads; }
    }

    /**
     * Expand $PARENT markers in a child list with items from the parent list.
     *
     * <p>If the child list contains "$PARENT", each occurrence is replaced with all items
     * from the parent list (in order). This allows fine-grained control over list inheritance:</p>
     *
     * <pre>
     * parent: [A, B, C]
     * child: [X, $PARENT, Y] -> result: [X, A, B, C, Y]
     * child: [$PARENT]       -> result: [A, B, C] (same as default merge)
     * child: [X, Y]          -> result: [X, Y] (override, no parent items)
     * child: [$PARENT, $PARENT] -> result: [A, B, C, A, B, C] (duplicate parent items)
     * </pre>
     *
     * @param childList  The child list that may contain $PARENT markers
     * @param parentList The parent list whose items will replace $PARENT
     * @return A new list with $PARENT markers expanded, or the original childList if no markers found
     */
    public static List<String> expandParentMarkers(List<String> childList, List<String> parentList) {
        if (childList == null || !childList.contains(PARENT_MARKER)) {
            return childList;
        }

        List<String> result = new ArrayList<>();
        for (String item : childList) {
            if (PARENT_MARKER.equals(item)) {
                // Replace $PARENT with all parent items
                if (parentList != null) {
                    result.addAll(parentList);
                }
            } else {
                result.add(item);
            }
        }
        return result;
    }
}