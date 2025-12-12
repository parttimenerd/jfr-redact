package me.bechberger.jfrredact.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Configuration for property redaction.
 *
 * Patterns are regex patterns that match anywhere in the property key name (case-insensitive by default).
 * For example, pattern "pass(word|wort)" matches:
 * - "password"
 * - "passwort"
 * - "user_password"
 * - "PASSWORD_HASH"
 * - "MyPasswordField"
 */
public class PropertyConfig {

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("case_sensitive")
    private boolean caseSensitive = false;

    /**
     * If true, patterns must match the entire field name.
     * If false (default), patterns can match anywhere in the field name.
     *
     * Example with pattern "password":
     * - fullMatch=false: matches "password", "user_password", "myPasswordField"
     * - fullMatch=true: matches only "password" (exact match)
     */
    @JsonProperty("full_match")
    private boolean fullMatch = false;

    @JsonProperty("patterns")
    private List<String> patterns = new ArrayList<>(List.of(
        "(pass(word|wort|wd)?|pwd)",  // Matches: password, passwort, passwd, pwd
        "secret",
        "token",
        "(api[_-]?)?key",       // Matches: key, api_key, api-key, apikey
        "auth",
        "credential"
    ));

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

    public boolean isFullMatch() { return fullMatch; }
    public void setFullMatch(boolean fullMatch) { this.fullMatch = fullMatch; }

    public List<String> getPatterns() { return patterns; }
    public void setPatterns(List<String> patterns) { this.patterns = patterns; }

    /**
     * Check if a property key matches any of the configured patterns.
     * Patterns are regex and match anywhere in the key name by default, or exact match if fullMatch=true.
     *
     * @param key The property key to check
     * @return true if the key matches any pattern
     */
    public boolean matches(String key) {
        if (key == null || !enabled) {
            return false;
        }

        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;

        for (String patternStr : patterns) {
            try {
                Pattern pattern;
                if (fullMatch) {
                    // Exact match required - anchor the pattern
                    pattern = Pattern.compile("^" + patternStr + "$", flags);
                } else {
                    // Partial match (default) - pattern can match anywhere
                    pattern = Pattern.compile(patternStr, flags);
                }

                if (pattern.matcher(key).find()) {
                    return true;
                }
            } catch (Exception e) {
                // If regex is invalid, fall back to literal string matching
                String keyToMatch = caseSensitive ? key : key.toLowerCase();
                String literalPattern = caseSensitive ? patternStr : patternStr.toLowerCase();

                if (fullMatch) {
                    if (keyToMatch.equals(literalPattern)) {
                        return true;
                    }
                } else {
                    if (keyToMatch.contains(literalPattern)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Merge with parent configuration
     */
    public void mergeWith(PropertyConfig parent) {
        if (parent == null) return;

        // Add parent patterns that we don't have
        for (String pattern : parent.getPatterns()) {
            if (!patterns.contains(pattern)) {
                patterns.add(pattern);
            }
        }
    }
}