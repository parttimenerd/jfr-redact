package me.bechberger.jfrredact.words;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;
class WordRedactionRuleTest {
    @Test
    void testParseRedactRule() {
        WordRedactionRule rule = WordRedactionRule.parse("- secretvalue");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertEquals("secretvalue", rule.getPattern());
        assertThat(rule.isRegex()).isFalse();
        assertThat(rule.matches("secretvalue")).isTrue();
        assertThat(rule.matches("other")).isFalse();
    }
    @Test
    void testParseKeepRule() {
        WordRedactionRule rule = WordRedactionRule.parse("+ publicvalue");
        assertEquals(WordRedactionRule.RuleType.KEEP, rule.getType());
        assertEquals("publicvalue", rule.getPattern());
        assertThat(rule.isRegex()).isFalse();
        assertThat(rule.matches("publicvalue")).isTrue();
    }
    @Test
    void testParseReplaceRule() {
        WordRedactionRule rule = WordRedactionRule.parse("! oldvalue newvalue");
        assertEquals(WordRedactionRule.RuleType.REPLACE, rule.getType());
        assertEquals("oldvalue", rule.getPattern());
        assertEquals("newvalue", rule.getReplacement());
        assertThat(rule.isRegex()).isFalse();
    }
    @Test
    void testParsePrefixRule() {
        WordRedactionRule rule = WordRedactionRule.parse("- secret*");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue(); // Now converted to regex wildcard pattern
        assertThat(rule.matches("secret123")).isTrue();
        assertThat(rule.matches("secretValue")).isTrue();
        assertThat(rule.matches("mysecret")).isFalse();
    }
    @Test
    void testParseRegexRedactRule() {
        WordRedactionRule rule = WordRedactionRule.parse("- /secret.*/");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertEquals("secret.*", rule.getPattern());
        assertThat(rule.isRegex()).isTrue();
        assertThat(rule.matches("secret123")).isTrue();
        assertThat(rule.matches("secretValue")).isTrue();
        assertThat(rule.matches("mysecret")).isFalse();
    }
    @Test
    void testParseRegexKeepRule() {
        WordRedactionRule rule = WordRedactionRule.parse("+ /public[0-9]+/");
        assertEquals(WordRedactionRule.RuleType.KEEP, rule.getType());
        assertEquals("public[0-9]+", rule.getPattern());
        assertThat(rule.isRegex()).isTrue();
        assertThat(rule.matches("public123")).isTrue();
        assertThat(rule.matches("publicABC")).isFalse();
    }
    @Test
    void testFormatRedactRule() {
        WordRedactionRule rule = WordRedactionRule.redact("test", false);
        assertEquals("- test", rule.format());
    }
    @Test
    void testFormatKeepRule() {
        WordRedactionRule rule = WordRedactionRule.keep("test", false);
        assertEquals("+ test", rule.format());
    }
    @Test
    void testFormatReplaceRule() {
        WordRedactionRule rule = WordRedactionRule.replace("old", "new", false);
        assertEquals("! old new", rule.format());
    }

    @Test
    void testFormatRegexRule() {
        WordRedactionRule rule = WordRedactionRule.redact("test.*", true);
        assertEquals("- /test.*/", rule.format());
    }
    @Test
    void testUnannotatedLinesAreIgnored() {
        // Lines without -, +, !, or # prefix should return null (ignored)
        assertNull(WordRedactionRule.parse("just some text"));
        assertNull(WordRedactionRule.parse("sdfsdf"));
        assertNull(WordRedactionRule.parse("randomword"));
        assertNull(WordRedactionRule.parse("  indented text  "));
        assertNull(WordRedactionRule.parse("word with spaces"));
        assertNull(WordRedactionRule.parse("123numbers"));
    }

    @Test
    void testInvalidReplaceFormat() {
        assertThrows(IllegalArgumentException.class, () -> {
            WordRedactionRule.parse("! onlypattern");
        });
    }

    // Wildcard pattern tests

    @Test
    void testParsePrefixWildcard() {
        WordRedactionRule rule = WordRedactionRule.parse("- prefix*");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // Test prefix matching
        assertThat(rule.matches("prefix")).isTrue();
        assertThat(rule.matches("prefixTest")).isTrue();
        assertThat(rule.matches("prefix123")).isTrue();
        assertThat(rule.matches("myprefix")).isFalse();
        assertThat(rule.matches("other")).isFalse();
    }

    @Test
    void testParseSuffixWildcard() {
        WordRedactionRule rule = WordRedactionRule.parse("- *suffix");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // Test suffix matching
        assertThat(rule.matches("suffix")).isTrue();
        assertThat(rule.matches("mysuffix")).isTrue();
        assertThat(rule.matches("testsuffix")).isTrue();
        assertThat(rule.matches("suffixother")).isFalse();
        assertThat(rule.matches("other")).isFalse();
    }

    @Test
    void testParseContainsWildcard() {
        WordRedactionRule rule = WordRedactionRule.parse("- *contains*");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // Test contains matching
        assertThat(rule.matches("contains")).isTrue();
        assertThat(rule.matches("mycontainsvalue")).isTrue();
        assertThat(rule.matches("testcontains")).isTrue();
        assertThat(rule.matches("containstest")).isTrue();
        assertThat(rule.matches("other")).isFalse();
    }

    @Test
    void testParseComplexWildcard() {
        WordRedactionRule rule = WordRedactionRule.parse("- *bla*dfg*");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // Test complex pattern matching
        assertThat(rule.matches("bladfg")).isTrue();
        assertThat(rule.matches("xxxblaxxxdfgxxx")).isTrue();
        assertThat(rule.matches("bla123dfg456")).isTrue();
        assertThat(rule.matches("testblamoreDFGend")).isFalse(); // case sensitive
        assertThat(rule.matches("blaonly")).isFalse();
        assertThat(rule.matches("dfgonly")).isFalse();
        assertThat(rule.matches("other")).isFalse();
    }

    @Test
    void testParseWildcardWithSpecialRegexChars() {
        // Test that special regex characters are properly escaped
        WordRedactionRule rule = WordRedactionRule.parse("- xnu-*");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // The '-' should be treated as literal, not as a regex range
        assertThat(rule.matches("xnu-8796.141.3")).isTrue();
        assertThat(rule.matches("xnu-8796.141.33")).isTrue();
        assertThat(rule.matches("xnu-test")).isTrue();
        assertThat(rule.matches("xnuXtest")).isFalse(); // Must have the dash
        assertThat(rule.matches("other")).isFalse();
    }

    @Test
    void testParseWildcardWithDots() {
        WordRedactionRule rule = WordRedactionRule.parse("- *xnu*");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // Dots should be treated as literals
        assertThat(rule.matches("xnu-8796.141.3")).isTrue();
        assertThat(rule.matches("xnu-8796.141.33")).isTrue();
        assertThat(rule.matches("test-xnu-value")).isTrue();
        assertThat(rule.matches("other")).isFalse();
    }

    @Test
    void testKeepRuleWithWildcard() {
        WordRedactionRule rule = WordRedactionRule.parse("+ keep*");
        assertEquals(WordRedactionRule.RuleType.KEEP, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        assertThat(rule.matches("keep")).isTrue();
        assertThat(rule.matches("keepThis")).isTrue();
        assertThat(rule.matches("keepValue123")).isTrue();
        assertThat(rule.matches("other")).isFalse();
    }

    @Test
    void testReplaceRuleWithWildcard() {
        WordRedactionRule rule = WordRedactionRule.parse("! secret* REDACTED");
        assertEquals(WordRedactionRule.RuleType.REPLACE, rule.getType());
        assertEquals("REDACTED", rule.getReplacement());
        assertThat(rule.isRegex()).isTrue();

        assertThat(rule.matches("secret")).isTrue();
        assertThat(rule.matches("secretValue")).isTrue();
        assertThat(rule.matches("secret123")).isTrue();
        assertThat(rule.matches("mysecret")).isFalse();
    }

    @Test
    void testWildcardVsRegexPriority() {
        // When a pattern has both / / delimiters AND *, the / / should take precedence
        WordRedactionRule rule = WordRedactionRule.parse("- /test.*/");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();
        assertEquals("test.*", rule.getPattern()); // Should be the regex pattern, not converted from glob
    }

    @Test
    void testMultipleConsecutiveWildcards() {
        WordRedactionRule rule = WordRedactionRule.parse("- a**b");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // Multiple wildcards should still work (treated as .*.*)
        assertThat(rule.matches("ab")).isTrue();
        assertThat(rule.matches("axb")).isTrue();
        assertThat(rule.matches("axxxb")).isTrue();
    }

    @Test
    void testWildcardInMiddle() {
        WordRedactionRule rule = WordRedactionRule.parse("- prefix*suffix");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // Should match prefix...suffix patterns
        assertThat(rule.matches("prefixsuffix")).isTrue();
        assertThat(rule.matches("prefixMIDDLEsuffix")).isTrue();
        assertThat(rule.matches("prefix123suffix")).isTrue();
        assertThat(rule.matches("prefix-test-suffix")).isTrue();

        // Should not match partial patterns
        assertThat(rule.matches("prefix")).isFalse();
        assertThat(rule.matches("suffix")).isFalse();
        assertThat(rule.matches("prefixonly")).isFalse();
        assertThat(rule.matches("onlysuffix")).isFalse();
        assertThat(rule.matches("other")).isFalse();
    }

    @Test
    void testMultipleWildcardsInMiddle() {
        WordRedactionRule rule = WordRedactionRule.parse("- a*b*c");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // Should match a...b...c patterns
        assertThat(rule.matches("abc")).isTrue();
        assertThat(rule.matches("axbxc")).isTrue();
        assertThat(rule.matches("aXXXbYYYc")).isTrue();
        assertThat(rule.matches("a123b456c")).isTrue();

        // Should not match incomplete patterns
        assertThat(rule.matches("ab")).isFalse();
        assertThat(rule.matches("bc")).isFalse();
        assertThat(rule.matches("axb")).isFalse();
        assertThat(rule.matches("bxc")).isFalse();
    }

    @Test
    void testWildcardWithSpecialCharsInMiddle() {
        // Test pattern with dots and dashes
        WordRedactionRule rule = WordRedactionRule.parse("- version-*.*");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // The dash and dot should be treated as literals
        assertThat(rule.matches("version-1.0")).isTrue();
        assertThat(rule.matches("version-2.5")).isTrue();
        assertThat(rule.matches("version-10.3.2")).isTrue();
        assertThat(rule.matches("version-test.beta")).isTrue();

        // Should not match without the literal dash
        assertThat(rule.matches("version1.0")).isFalse();
        assertThat(rule.matches("version_1.0")).isFalse();
    }

    @Test
    void testComplexRealWorldPattern() {
        // Test realistic pattern like "file-*-v*.jar"
        WordRedactionRule rule = WordRedactionRule.parse("- file-*-v*.jar");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        assertThat(rule.matches("file-myapp-v1.jar")).isTrue();
        assertThat(rule.matches("file-test-v2.0.jar")).isTrue();
        assertThat(rule.matches("file-production-v3.1.5.jar")).isTrue();

        // Should not match without the pattern parts
        assertThat(rule.matches("file-myapp.jar")).isFalse();
        assertThat(rule.matches("myapp-v1.jar")).isFalse();
        assertThat(rule.matches("file-v1.jar")).isFalse();
    }

    @Test
    void testWildcardBetweenSpecialChars() {
        // Test pattern like "192.168.*.*"
        WordRedactionRule rule = WordRedactionRule.parse("- 192.168.*.*");
        assertEquals(WordRedactionRule.RuleType.REDACT, rule.getType());
        assertThat(rule.isRegex()).isTrue();

        // Dots should be treated as literals
        assertThat(rule.matches("192.168.1.1")).isTrue();
        assertThat(rule.matches("192.168.0.100")).isTrue();
        assertThat(rule.matches("192.168.254.254")).isTrue();

        // Should not match different IP ranges
        assertThat(rule.matches("192.169.1.1")).isFalse();
        assertThat(rule.matches("10.168.1.1")).isFalse();
        assertThat(rule.matches("192X168X1X1")).isFalse(); // Dots must be literal
    }
}