package me.bechberger.jfrredact.words;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies word redaction rules to JFR events and text
 */
public class WordRedactor {

    private static final Logger logger = LoggerFactory.getLogger(WordRedactor.class);

    // Pattern for valid words: alphanumeric, underscore, dash, plus, slash
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-+/]+");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[a-zA-Z].*");

    private final List<WordRedactionRule> rules;
    private final Map<String, String> redactionCache = new HashMap<>();

    public WordRedactor(List<WordRedactionRule> rules) {
        this.rules = new ArrayList<>(rules);
    }

    /**
     * Process a recorded event and apply redaction rules
     */
    public void processEvent(RecordedEvent event) {
        processRecordedObject(event);
    }

    private void processRecordedObject(RecordedObject obj) {
        for (var field : obj.getFields()) {
            Object value = obj.getValue(field.getName());

            if (value instanceof String) {
                String original = (String) value;
                String redacted = applyRules(original);
                if (!original.equals(redacted)) {
                    // Note: We can't directly modify RecordedEvent fields
                    // This will be used in conjunction with JFR writing
                    logger.trace("Would redact '{}' to '{}'", original, redacted);
                }
            } else if (value instanceof RecordedObject) {
                processRecordedObject((RecordedObject) value);
            }
        }
    }

    /**
     * Redact words in a text line while preserving structure
     */
    public String redactText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = WORD_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            String word = matcher.group();

            // Only process words that contain at least one letter
            if (HAS_LETTER.matcher(word).matches()) {
                // Append text before the word
                result.append(text, lastEnd, matcher.start());

                // Apply rules to the word
                String redactedWord = applyRulesToWord(word);
                result.append(redactedWord);

                lastEnd = matcher.end();
            }
        }

        // Append remaining text
        if (lastEnd < text.length()) {
            result.append(text.substring(lastEnd));
        }

        return result.toString();
    }

    /**
     * Apply redaction rules to a single word
     */
    private String applyRulesToWord(String word) {
        // Check cache first
        if (redactionCache.containsKey(word)) {
            return redactionCache.get(word);
        }

        String result = word;

        // Apply rules in order
        for (WordRedactionRule rule : rules) {
            // Check if this is a keep rule that matches
            if (rule.getType() == WordRedactionRule.RuleType.KEEP && rule.matches(result)) {
                // Keep the value as-is, don't apply any more rules
                redactionCache.put(word, result);
                return result;
            }

            result = applyRuleToWord(result, rule);
        }

        redactionCache.put(word, result);
        return result;
    }

    private String applyRuleToWord(String value, WordRedactionRule rule) {
        switch (rule.getType()) {
            case KEEP:
                // Keep rule already handled in applyRulesToWord
                return value;

            case REDACT:
                if (rule.matches(value)) {
                    return "***";
                }
                break;

            case REPLACE:
                if (rule.matches(value)) {
                    return rule.getReplacement();
                }
                break;

            case REDACT_PREFIX:
                if (value.startsWith(rule.getPattern())) {
                    return "***";
                }
                break;
        }

        return value;
    }

    /**
     * Apply redaction rules to a string value
     */
    public String applyRules(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Check cache first
        if (redactionCache.containsKey(value)) {
            return redactionCache.get(value);
        }

        String result = value;

        // Apply rules in order
        for (WordRedactionRule rule : rules) {
            // Check if this is a keep rule that matches
            if (rule.getType() == WordRedactionRule.RuleType.KEEP && rule.matches(result)) {
                // Keep the value as-is, don't apply any more rules
                redactionCache.put(value, result);
                return result;
            }

            result = applyRule(result, rule);
        }

        redactionCache.put(value, result);
        return result;
    }

    private String applyRule(String value, WordRedactionRule rule) {
        switch (rule.getType()) {
            case KEEP:
                // If it matches a keep rule, don't apply further redaction
                if (rule.matches(value)) {
                    return value;
                }
                break;

            case REDACT:
                if (rule.matches(value)) {
                    return "***";
                }
                break;

            case REPLACE:
                if (rule.matches(value)) {
                    return rule.getReplacement();
                }
                break;

            case REDACT_PREFIX:
                if (value.startsWith(rule.getPattern())) {
                    return "***";
                }
                break;
        }

        return value;
    }

    /**
     * Get statistics about redaction
     */
    public String getStatistics() {
        long redacted = redactionCache.values().stream()
            .filter(v -> v.equals("***"))
            .count();

        long replaced = redactionCache.values().stream()
            .filter(v -> !v.equals("***"))
            .count();

        return String.format("Processed %d unique values: %d redacted, %d replaced, %d kept",
            redactionCache.size(), redacted, replaced,
            redactionCache.size() - redacted - replaced);
    }
}