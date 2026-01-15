package me.bechberger.jfrredact.words;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for text redaction functionality
 */
class WordRedactorTextTest {

    @Test
    void testRedactTextBasic() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("secret", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "The secret is hidden in the file";
        String redacted = redactor.redactText(text);

        assertEquals("The *** is hidden in the file", redacted);
    }

    @Test
    void testRedactTextMultipleWords() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("john", false),
            WordRedactionRule.redact("password123", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "User john logged in with password123";
        String redacted = redactor.redactText(text);

        assertEquals("User *** logged in with ***", redacted);
    }

    @Test
    void testRedactTextPreservesStructure() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("secret", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "  secret: value, other: data  ";
        String redacted = redactor.redactText(text);

        assertEquals("  ***: value, other: data  ", redacted);
    }

    @Test
    void testRedactTextWithPaths() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("/Users/john/document.txt", false)  // Note: dots are not in word pattern
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "File: /Users/john/document.txt";
        String redacted = redactor.redactText(text);

        assertEquals("File: ***", redacted);
    }

    @Test
    void testRedactTextWithRegex() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("user[0-9]+", true)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "Users: user123, user456, admin";
        String redacted = redactor.redactText(text);

        assertEquals("Users: ***, ***, admin", redacted);
    }

    @Test
    void testRedactTextWithKeepRule() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.keep("localhost", false),
            WordRedactionRule.redact("host.*", true)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "Server: localhost, DB: hostname123";
        String redacted = redactor.redactText(text);

        assertEquals("Server: localhost, DB: ***", redacted);
    }

    @Test
    void testRedactTextWithReplace() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.replace("server01", "SERVER_A", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "Connected to server01 on port 8080";
        String redacted = redactor.redactText(text);

        assertEquals("Connected to SERVER_A on port 8080", redacted);
    }

    @Test
    void testRedactTextWithPrefix() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.parse("- secret*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "Found: secret123, secretValue, mysecret";
        String redacted = redactor.redactText(text);

        assertEquals("Found: ***, ***, mysecret", redacted);
    }

    @Test
    void testRedactTextIgnoresNumbersOnly() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("12345", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "Port: 12345, Code: 12345";
        String redacted = redactor.redactText(text);

        // Numbers-only words are not redacted (must contain letters)
        assertEquals("Port: ***, Code: ***", redacted);
    }

    @Test
    void testRedactTextWithSpecialChars() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("user_name", false),
            WordRedactionRule.redact("test-value", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "Config: user_name=john, test-value=abc";
        String redacted = redactor.redactText(text);

        assertEquals("Config: ***=john, ***=abc", redacted);
    }

    @Test
    void testRedactTextMultipleRulesInOrder() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.keep("admin", false),
            WordRedactionRule.redact("adm.*", true),
            WordRedactionRule.replace("server", "HOST", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "Users: admin, admin123, server";
        String redacted = redactor.redactText(text);

        assertEquals("Users: admin, ***, HOST", redacted);
    }

    @Test
    void testRedactTextEmptyLine() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("test", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("", redactor.redactText(""));
        assertEquals("   ", redactor.redactText("   "));
    }

    @Test
    void testRedactTextNoMatches() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("secret", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "This is a public message";
        String redacted = redactor.redactText(text);

        assertEquals(text, redacted); // Unchanged
    }

    @Test
    void testRedactTextComplexLine() {
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("john_doe", false),
            WordRedactionRule.parse("- secret_*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        String text = "User john_doe logged in from 192.168.1.100 with key secret_api_key_12345";
        String redacted = redactor.redactText(text);

        // Note: IP addresses (no letters) are not matched by word pattern
        assertEquals("User *** logged in from 192.168.1.100 with key ***", redacted);
    }
}