package me.bechberger.jfrredact.words;
import me.bechberger.jfrredact.util.GlobMatcher;
import java.util.regex.Pattern;

public class WordRedactionRule {
    public enum RuleType {
        REDACT, KEEP, REPLACE
    }

    private final RuleType type;
    private final String pattern;
    private final String replacement;
    private final boolean isRegex;
    private final Pattern compiledPattern; // Cache compiled pattern

    public WordRedactionRule(RuleType type, String pattern, String replacement, boolean isRegex) {
        this.type = type;
        this.pattern = pattern;
        this.replacement = replacement;
        this.isRegex = isRegex;
        // Compile pattern once during construction if it's a regex
        this.compiledPattern = isRegex ? Pattern.compile(pattern) : null;
    }

    public static WordRedactionRule redact(String pattern, boolean isRegex) {
        return new WordRedactionRule(RuleType.REDACT, pattern, null, isRegex);
    }

    public static WordRedactionRule keep(String pattern, boolean isRegex) {
        return new WordRedactionRule(RuleType.KEEP, pattern, null, isRegex);
    }

    public static WordRedactionRule replace(String pattern, String replacement, boolean isRegex) {
        return new WordRedactionRule(RuleType.REPLACE, pattern, replacement, isRegex);
    }

    public RuleType getType() { return type; }
    public String getPattern() { return pattern; }
    public String getReplacement() { return replacement; }
    public boolean isRegex() { return isRegex; }

    public boolean matches(String word) {
        if (isRegex) {
            // Use cached compiled pattern
            return compiledPattern.matcher(word).matches();
        } else {
            return word.equals(pattern);
        }
    }

    /**
     * Process a pattern string and convert wildcards to regex if needed.
     * Handles both /regex/ format and wildcard patterns with *.
     *
     * @return array where [0] is the processed pattern and [1] is "true"/"false" for isRegex
     */
    private static String[] processPattern(String pattern) {
        pattern = pattern.trim();

        // Check for explicit regex format: /pattern/
        if (pattern.startsWith("/") && pattern.endsWith("/") && pattern.length() > 2) {
            String regexPattern = pattern.substring(1, pattern.length() - 1);
            return new String[]{regexPattern, "true"};
        }

        // Check for wildcard pattern and convert to regex
        if (pattern.contains("*")) {
            String regexPattern = GlobMatcher.globToRegex(pattern);
            return new String[]{regexPattern, "true"};
        }

        // Plain literal pattern
        return new String[]{pattern, "false"};
    }

    public static WordRedactionRule parse(String line) {
        line = line.trim();

        if (line.startsWith("-")) {
            String[] processed = processPattern(line.substring(1));
            return redact(processed[0], Boolean.parseBoolean(processed[1]));

        } else if (line.startsWith("+")) {
            String[] processed = processPattern(line.substring(1));
            return keep(processed[0], Boolean.parseBoolean(processed[1]));

        } else if (line.startsWith("!")) {
            String rest = line.substring(1).trim();
            int spaceIdx = rest.indexOf(' ');
            if (spaceIdx < 0) {
                throw new IllegalArgumentException("Replace rule must have format: ! pattern replacement");
            }

            String patternPart = rest.substring(0, spaceIdx);
            String replacement = rest.substring(spaceIdx + 1).trim();
            String[] processed = processPattern(patternPart);

            return replace(processed[0], replacement, Boolean.parseBoolean(processed[1]));

        } else {
            // Unknown line format - return null to ignore
            return null;
        }
    }
    public String format() {
        String patternStr = isRegex ? "/" + pattern + "/" : pattern;
        if (type == RuleType.REDACT) {
            return "- " + patternStr;
        } else if (type == RuleType.KEEP) {
            return "+ " + patternStr;
        } else {
            return "! " + patternStr + " " + replacement;
        }
    }
    public String toString() {
        return format();
    }
}