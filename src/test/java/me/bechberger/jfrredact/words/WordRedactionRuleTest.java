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
        WordRedactionRule rule = WordRedactionRule.parse("-$ secret");
        assertEquals(WordRedactionRule.RuleType.REDACT_PREFIX, rule.getType());
        assertEquals("secret", rule.getPattern());
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
    void testFormatPrefixRule() {
        WordRedactionRule rule = WordRedactionRule.redactPrefix("prefix");
        assertEquals("-$ prefix", rule.format());
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
}