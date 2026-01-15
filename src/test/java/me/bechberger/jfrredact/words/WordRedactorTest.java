package me.bechberger.jfrredact.words;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

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
    void testRedactSingleComplexWord() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.redact("xnu-8796.141.3", false)
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("xnu-8796.141.3"));
        assertEquals("xnu-8796.141.33", redactor.applyRules("xnu-8796.141.33"));
        assertEquals("public", redactor.applyRules("public"));
    }

    @Test
    void testRedactSingleComplexWordContains() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- *xnu*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("xnu-8796.141.3"));
        assertEquals("***", redactor.applyRules("xnu-8796.141.33"));
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
            WordRedactionRule.parse("- secret*")
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
        assertThat(stats).contains("1 unique value");  // Only one unique value cached
    }

    // Wildcard pattern tests

    @Test
    void testPrefixWildcardPattern() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- prefix*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("prefix"));
        assertEquals("***", redactor.applyRules("prefixTest"));
        assertEquals("***", redactor.applyRules("prefix123"));
        assertEquals("myprefix", redactor.applyRules("myprefix"));
        assertEquals("other", redactor.applyRules("other"));
    }

    @Test
    void testSuffixWildcardPattern() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- *suffix")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("suffix"));
        assertEquals("***", redactor.applyRules("mysuffix"));
        assertEquals("***", redactor.applyRules("testsuffix"));
        assertEquals("suffixother", redactor.applyRules("suffixother"));
        assertEquals("other", redactor.applyRules("other"));
    }

    @Test
    void testContainsWildcardPattern() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- *contains*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("contains"));
        assertEquals("***", redactor.applyRules("mycontainsvalue"));
        assertEquals("***", redactor.applyRules("testcontains"));
        assertEquals("***", redactor.applyRules("containstest"));
        assertEquals("other", redactor.applyRules("other"));
    }

    @Test
    void testComplexWildcardPattern() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- *bla*dfg*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("bladfg"));
        assertEquals("***", redactor.applyRules("xxxblaxxxdfgxxx"));
        assertEquals("***", redactor.applyRules("bla123dfg456"));
        assertEquals("blaonly", redactor.applyRules("blaonly"));
        assertEquals("dfgonly", redactor.applyRules("dfgonly"));
        assertEquals("other", redactor.applyRules("other"));
    }

    @Test
    void testWildcardWithSpecialCharsInText() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- *xnu*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        // Test with the exact case from the bug report
        assertEquals("***", redactor.redactText("xnu-8796.141.3"));
        assertEquals("***", redactor.redactText("xnu-8796.141.33"));

        // Test in context
        assertEquals("OS: ***", redactor.redactText("OS: xnu-8796.141.3"));
        assertEquals("Kernel: ***", redactor.redactText("Kernel: xnu-8796.141.33"));
    }

    @Test
    void testMultipleWildcardRules() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("+ keep*"),           // Keep anything starting with "keep"
                WordRedactionRule.parse("- secret*"),         // Redact anything starting with "secret"
                WordRedactionRule.parse("- *password*"),      // Redact anything containing "password"
                WordRedactionRule.parse("! *token* REDACTED") // Replace anything containing "token"
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("keepThis", redactor.applyRules("keepThis"));
        assertEquals("***", redactor.applyRules("secret123"));
        assertEquals("***", redactor.applyRules("secretValue"));
        assertEquals("***", redactor.applyRules("mypassword"));
        assertEquals("***", redactor.applyRules("passwordfile"));
        assertEquals("REDACTED", redactor.applyRules("authtoken"));
        assertEquals("REDACTED", redactor.applyRules("tokenValue"));
        assertEquals("normal", redactor.applyRules("normal"));
    }

    @Test
    void testWildcardInMiddleOfPattern() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- prefix*suffix")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("prefixsuffix"));
        assertEquals("***", redactor.applyRules("prefixMIDDLEsuffix"));
        assertEquals("***", redactor.applyRules("prefix-test-suffix"));
        assertEquals("prefix", redactor.applyRules("prefix"));
        assertEquals("suffix", redactor.applyRules("suffix"));
        assertEquals("other", redactor.applyRules("other"));
    }

    @Test
    void testMultipleWildcardsInMiddle() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- server-*-port-*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("server-prod-port-8080"));
        assertEquals("***", redactor.applyRules("server-dev-port-3000"));
        assertEquals("***", redactor.applyRules("server-staging-port-9090"));
        assertEquals("server-prod", redactor.applyRules("server-prod"));
        assertEquals("port-8080", redactor.applyRules("port-8080"));
    }

    @Test
    void testIPAddressPattern() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- 192.168.*.*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("192.168.1.1"));
        assertEquals("***", redactor.applyRules("192.168.0.100"));
        assertEquals("***", redactor.applyRules("192.168.254.254"));
        assertEquals("10.0.0.1", redactor.applyRules("10.0.0.1"));
        assertEquals("192.169.1.1", redactor.applyRules("192.169.1.1"));
    }

    @Test
    void testVersionPattern() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- version-*.*")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("***", redactor.applyRules("version-1.0"));
        assertEquals("***", redactor.applyRules("version-2.5.3"));
        assertEquals("***", redactor.applyRules("version-beta.1"));
        assertEquals("version1.0", redactor.applyRules("version1.0")); // Missing dash
    }

    @Test
    void testWildcardInTextRedaction() {
        List<WordRedactionRule> rules = List.of(
                WordRedactionRule.parse("- user-*-id")
        );

        WordRedactor redactor = new WordRedactor(rules);

        assertEquals("Server: ***", redactor.redactText("Server: user-john-id"));
        assertEquals("ID: *** active", redactor.redactText("ID: user-admin-id active"));
        assertEquals("Found: *** and user-jane", redactor.redactText("Found: user-bob-id and user-jane"));
    }
}