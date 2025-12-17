package me.bechberger.jfrredact.words;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WordRedactor
 */
class WordRedactorTest {

    @Test
    void testRedactSingleWord() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("secret", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("secret"));
        assertEquals("public", redactor.applyRules("public"));
    }

    @Test
    void testKeepWord() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.keep("secretPublic", false),  // Keep rule should come first
            WordRedactionRule.redact("secret.*", true)
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("secret123"));
        assertEquals("secretPublic", redactor.applyRules("secretPublic"));  // Keep rule takes precedence
    }

    @Test
    void testReplaceWord() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.replace("username", "REDACTED_USER", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("REDACTED_USER", redactor.applyRules("username"));
        assertEquals("other", redactor.applyRules("other"));
    }

    @Test
    void testPrefixRedaction() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redactPrefix("secret")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("secret"));
        assertEquals("***", redactor.applyRules("secret123"));
        assertEquals("***", redactor.applyRules("secretValue"));
        assertEquals("mysecret", redactor.applyRules("mysecret"));  // Doesn't start with prefix
    }

    @Test
    void testRegexRedaction() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("user[0-9]+", true)
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("user123"));
        assertEquals("***", redactor.applyRules("user456"));
        assertEquals("userABC", redactor.applyRules("userABC"));  // Doesn't match pattern
    }

    @Test
    void testMultipleRules() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.keep("admin", false),
            WordRedactionRule.redact("user.*", true),
            WordRedactionRule.replace("password", "PWD", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("admin", redactor.applyRules("admin"));
        assertEquals("***", redactor.applyRules("user123"));
        assertEquals("PWD", redactor.applyRules("password"));
    }

    @Test
    void testCaching() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("secret", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        // Apply same rule multiple times - should use cache
        assertEquals("***", redactor.applyRules("secret"));
        assertEquals("***", redactor.applyRules("secret"));
        assertEquals("***", redactor.applyRules("secret"));

        String stats = redactor.getStatistics();
        assertTrue(stats.contains("1 unique value"));  // Only one unique value cached
    }
}