package me.bechberger.jfrredact.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for matching strings against glob patterns.
 * Supports simple glob patterns with * and ? wildcards.
 */
public class GlobMatcher {

    /**
     * Check if a value matches any pattern in the list.
     * Patterns can be:
     * - Simple strings (exact match)
     * - Quoted strings (exact match)
     * - Glob patterns with * and ? wildcards
     *
     * @param value The value to match
     * @param patterns List of patterns (can be comma-separated)
     * @return true if the value matches any pattern
     */
    public static boolean matches(String value, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }

        for (String pattern : patterns) {
            // Split comma-separated patterns
            for (String p : pattern.split(",")) {
                p = p.trim();
                if (p.isEmpty()) continue;

                // Remove quotes if present
                if ((p.startsWith("\"") && p.endsWith("\"")) ||
                    (p.startsWith("'") && p.endsWith("'"))) {
                    p = p.substring(1, p.length() - 1);
                }

                if (matchesPattern(value, p)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a value matches a single pattern.
     *
     * @param value The value to match
     * @param pattern The pattern (can contain * and ? wildcards)
     * @return true if the value matches the pattern
     */
    private static boolean matchesPattern(String value, String pattern) {
        if (value == null) {
            return false;
        }

        // If no wildcards, do exact match
        if (!pattern.contains("*") && !pattern.contains("?")) {
            return value.equals(pattern);
        }

        // Convert glob pattern to regex
        String regex = globToRegex(pattern);
        return Pattern.matches(regex, value);
    }

    /**
     * Convert a glob pattern to a regular expression.
     *
     * @param glob The glob pattern
     * @return The equivalent regex pattern
     */
    public static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                case '.':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '+':
                case '^':
                case '$':
                case '|':
                case '\\':
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
            }
        }

        regex.append("$");
        return regex.toString();
    }
}