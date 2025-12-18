package me.bechberger.jfrredact.words;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Discovers distinct words/strings in JFR events
 * Pattern: [:alnum:_-+/]+ (must contain at least one letter)
 */
public class WordDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(WordDiscovery.class);

    // Pattern for valid words: alphanumeric, underscore, dash, plus, slash
    // Must contain at least one letter
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-+/]+");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[a-zA-Z].*");
    // Pattern to detect hexadecimal values (0x followed by hex digits)
    private static final Pattern HEX_PATTERN = Pattern.compile("^0[xX][0-9a-fA-F]+$");

    private final Set<String> discoveredWords = new TreeSet<>();
    private final Set<String> ignoredEventTypes = new HashSet<>();
    private final boolean ignoreMethodNames;
    private final boolean ignoreClassNames;
    private final boolean ignorePackageNames;
    private final boolean ignoreModuleNames;

    public WordDiscovery() {
        this(true, true, true, true);
    }

    public WordDiscovery(boolean ignoreMethodNames, boolean ignoreClassNames,
                        boolean ignorePackageNames, boolean ignoreModuleNames) {
        this.ignoreMethodNames = ignoreMethodNames;
        this.ignoreClassNames = ignoreClassNames;
        this.ignorePackageNames = ignorePackageNames;
        this.ignoreModuleNames = ignoreModuleNames;
    }

    /**
     * Add event types to ignore
     */
    public void addIgnoredEventTypes(Collection<String> eventTypes) {
        ignoredEventTypes.addAll(eventTypes);
    }

    /**
     * Analyze a JFR event to discover words
     */
    public void analyzeEvent(RecordedEvent event) {
        String eventType = event.getEventType().getName();

        // Check if this event type should be ignored
        if (ignoredEventTypes.contains(eventType)) {
            return;
        }

        analyzeRecordedObject(event);
    }

    private void analyzeRecordedObject(RecordedObject obj) {
        for (var field : obj.getFields()) {
            String fieldName = field.getName();

            // Skip certain field types
            if (shouldSkipField(fieldName)) {
                continue;
            }

            Object value = obj.getValue(fieldName);

            if (value instanceof String) {
                extractWords((String) value);
            } else if (value instanceof RecordedObject) {
                analyzeRecordedObject((RecordedObject) value);
            }
        }
    }

    private boolean shouldSkipField(String fieldName) {
        if (ignoreMethodNames && (fieldName.equals("method") || fieldName.endsWith("Method"))) {
            return true;
        }
        if (ignoreClassNames && (fieldName.equals("class") || fieldName.endsWith("Class") || fieldName.equals("type"))) {
            return true;
        }
        if (ignorePackageNames && (fieldName.equals("package") || fieldName.endsWith("Package"))) {
            return true;
        }
        if (ignoreModuleNames && (fieldName.equals("module") || fieldName.endsWith("Module"))) {
            return true;
        }
        return false;
    }

    /**
     * Analyze a text line to discover words
     */
    public void analyzeText(String text) {
        extractWords(text);
    }

    private void extractWords(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        var matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();

            // Must contain at least one letter
            if (!HAS_LETTER.matcher(word).matches()) {
                continue;
            }

            // Skip hexadecimal values (0x followed by hex digits)
            if (HEX_PATTERN.matcher(word).matches()) {
                continue;
            }

            discoveredWords.add(word);
        }
    }

    /**
     * Get all discovered words (sorted)
     */
    public Set<String> getDiscoveredWords() {
        return new TreeSet<>(discoveredWords);
    }

    /**
     * Get statistics about discovered words
     */
    public String getStatistics() {
        return String.format("Discovered %d distinct words", discoveredWords.size());
    }
}