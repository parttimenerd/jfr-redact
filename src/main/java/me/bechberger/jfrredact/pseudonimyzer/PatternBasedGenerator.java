package me.bechberger.jfrredact.pseudonimyzer;

import com.github.curiousoddman.rgxgen.RgxGen;
import com.github.curiousoddman.rgxgen.iterators.StringIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern-based replacement generator using RgxGen.
 *
 * Supports two modes:
 * 1. Redaction mode: Generates random values from the pattern
 * 2. Pseudonymization mode: Generates consistent deterministic mappings
 *
 * Special placeholders (replaced before regex generation):
 * - {users} - Replaced with realistic user folder names from RealisticDataGenerator
 * - {emails} - Replaced with realistic email addresses
 * - {names} - Replaced with realistic names
 *
 * Examples:
 * - Pattern "emails" with regex "[a-z]{5}@example.com" -> "alice@example.com"
 * - Pattern "usernames" with regex "user[0-9]{2}" -> "user42"
 * - Pattern "paths" with regex "/home/{users}" -> "/home/alice"
 */
public class PatternBasedGenerator {

    private static final Logger log = LoggerFactory.getLogger(PatternBasedGenerator.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(users|emails|names)\\}");
    private static final int MIN_RECOMMENDED_CARDINALITY = 100;

    private final Map<String, String> patternDefinitions;
    private final Map<String, StringIterator> iteratorCache;
    private final Map<String, RgxGen> rgxGenCache;
    private final Random random;
    private final Map<String, Map<String, String>> patternValueCache;
    private final RealisticDataGenerator realisticGenerator;

    /**
     * Create a new pattern-based generator.
     *
     * @param patternDefinitions Map of pattern names to regex generation patterns
     * @param seed Random seed for deterministic generation
     */
    public PatternBasedGenerator(Map<String, String> patternDefinitions, long seed) {
        this.patternDefinitions = new HashMap<>(patternDefinitions);
        this.iteratorCache = new HashMap<>();
        this.rgxGenCache = new HashMap<>();
        this.random = new Random(seed);
        this.patternValueCache = new HashMap<>();
        this.realisticGenerator = new RealisticDataGenerator(seed);

        // Pre-compile all patterns and check cardinality
        for (Map.Entry<String, String> entry : patternDefinitions.entrySet()) {
            String patternName = entry.getKey();
            String regex = entry.getValue();

            // Preprocess placeholders to convert them to regex patterns
            String processedRegex = preprocessPlaceholders(regex, "");

            // Create RgxGen for both random and unique generation
            RgxGen rgxGen = RgxGen.parse(processedRegex);
            rgxGenCache.put(patternName, rgxGen);

            // Create unique iterator for this pattern
            StringIterator iterator = rgxGen.iterateUnique();
            iteratorCache.put(patternName, iterator);
            patternValueCache.put(patternName, new HashMap<>());

            // Check and warn about low cardinality
            checkPatternCardinality(patternName, rgxGen);
        }
    }

    /**
     * Check pattern cardinality and warn if too low for pseudonymization.
     */
    private void checkPatternCardinality(String patternName, RgxGen rgxGen) {
        try {
            // Try to estimate cardinality
            int estimatedCardinality = estimateCardinality(rgxGen);

            if (estimatedCardinality > 0 && estimatedCardinality < MIN_RECOMMENDED_CARDINALITY) {
                log.debug("Pattern '{}' has low cardinality (~{} possible values). " +
                        "This may cause collisions in pseudonymization mode. " +
                        "Recommended minimum: {} values.",
                        patternName, estimatedCardinality, MIN_RECOMMENDED_CARDINALITY);
            } else {
                log.debug("Pattern '{}' cardinality: ~{} possible values",
                         patternName, estimatedCardinality > 0 ? estimatedCardinality : "unknown (very large)");
            }
        } catch (Exception e) {
            log.debug("Could not estimate cardinality for pattern '{}': {}", patternName, e.getMessage());
        }
    }

    /**
     * Estimate the cardinality (number of possible values) of a pattern.
     * Returns -1 if cardinality is too large to calculate.
     */
    private int estimateCardinality(RgxGen rgxGen) {
        try {
            // Try to count unique values (limited to avoid hanging)
            StringIterator iterator = rgxGen.iterateUnique();
            int count = 0;
            int maxCount = 10000; // Safety limit

            while (iterator.hasNext() && count < maxCount) {
                iterator.next();
                count++;
            }

            if (count >= maxCount) {
                return -1; // Too large to count
            }

            return count;
        } catch (Exception e) {
            return -1; // Unknown
        }
    }

    /**
     * Generate a random value from the pattern (for redaction mode).
     * Each call returns a different random value from the pattern.
     *
     * @param patternName Name of the pattern to use
     * @return Random value matching the pattern, or null if pattern not found
     */
    public String generateRandom(String patternName) {
        if (!patternDefinitions.containsKey(patternName)) {
            return null;
        }

        RgxGen rgxGen = rgxGenCache.get(patternName);
        String generated = rgxGen.generate();


        return generated;
    }

    /**
     * Generate a consistent replacement for a value using the specified pattern.
     * For pseudonymization mode - same input always produces same output.
     *
     * @param patternName Name of the pattern to use (e.g., "emails", "usernames")
     * @param originalValue The original value to replace
     * @return Generated replacement, or null if pattern not found
     */
    public String generate(String patternName, String originalValue) {
        if (!patternDefinitions.containsKey(patternName)) {
            return null;
        }

        // Check cache first for consistency
        Map<String, String> cache = patternValueCache.get(patternName);
        if (cache.containsKey(originalValue)) {
            return cache.get(originalValue);
        }

        // Generate new value using unique iterator
        StringIterator iterator = iteratorCache.get(patternName);
        String generated;

        if (iterator.hasNext()) {
            generated = iterator.next();
        } else {
            // Iterator exhausted, create a new one (will cycle through values again)
            String regex = patternDefinitions.get(patternName);
            String processedRegex = preprocessPlaceholders(regex, originalValue);
            RgxGen rgxGen = RgxGen.parse(processedRegex);
            iterator = rgxGen.iterateUnique();
            iteratorCache.put(patternName, iterator);
            generated = iterator.hasNext() ? iterator.next() : originalValue;

            log.warn("Pattern '{}' iterator exhausted - cycling through values again. " +
                    "This may cause collisions. Consider using a pattern with more possible values.",
                    patternName);
        }


        // Cache it
        cache.put(originalValue, generated);

        return generated;
    }

    /**
     * Pre-process special placeholders in the pattern before regex generation.
     *
     * Placeholders are replaced with equivalent regex patterns:
     * - {users} -> (alice|bob|charlie|diana|eve|frank|grace|henry|iris|jack|kate|leo|mary|nathan|olivia|peter|quinn|rachel|sam|tina|uma|victor|wendy|xavier|yara|zoe)
     * - {emails} -> email regex pattern
     * - {names} -> name regex pattern
     *
     * This is done BEFORE passing to RgxGen, so the placeholders are converted to regex patterns.
     */
    private String preprocessPlaceholders(String pattern, String originalValue) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(pattern);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = switch (placeholder) {
                case "users" -> {
                    // Replace with alternation of realistic usernames
                    yield "(alice|bob|charlie|diana|eve|frank|grace|henry|iris|jack|kate|leo|mary|nathan|olivia|peter|quinn|rachel|sam|tina|uma|victor|wendy|xavier|yara|zoe)";
                }
                case "emails" -> {
                    // Generate a regex pattern for realistic emails
                    yield "(alice|bob|charlie|diana|eve|frank|grace|henry|iris|jack|kate|leo|mary|nathan|olivia|peter|quinn|rachel|sam|tina|uma|victor|wendy|xavier|yara|zoe)\\\\.(smith|johnson|williams|brown|jones|garcia|miller|davis)@(example|test|demo|sample)\\\\.(com|org|net|io)";
                }
                case "names" -> {
                    // Generate a regex pattern for realistic names
                    yield "(alice|bob|charlie|diana|eve|frank|grace|henry|iris|jack|kate|leo|mary|nathan|olivia|peter|quinn|rachel|sam|tina|uma|victor|wendy|xavier|yara|zoe)\\\\.(smith|johnson|williams|brown|jones|garcia|miller|davis)";
                }
                default -> Matcher.quoteReplacement(matcher.group(0));
            };

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Check if a pattern is defined.
     *
     * @param patternName Pattern name to check
     * @return true if pattern exists
     */
    public boolean hasPattern(String patternName) {
        return patternDefinitions.containsKey(patternName);
    }

    /**
     * Get all defined pattern names.
     *
     * @return Set of pattern names
     */
    public java.util.Set<String> getPatternNames() {
        return patternDefinitions.keySet();
    }

    /**
     * Clear the cache for a specific pattern.
     *
     * @param patternName Pattern to clear
     */
    public void clearPatternCache(String patternName) {
        Map<String, String> cache = patternValueCache.get(patternName);
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Clear all caches.
     */
    public void clearAllCaches() {
        for (Map<String, String> cache : patternValueCache.values()) {
            cache.clear();
        }
    }

    /**
     * Get the regex pattern for a given pattern name.
     *
     * @param patternName Pattern name
     * @return Regex pattern or null if not found
     */
    public String getPattern(String patternName) {
        return patternDefinitions.get(patternName);
    }
}