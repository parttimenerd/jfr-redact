package me.bechberger.jfrredact.words;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WordDiscovery
 */
class WordDiscoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void testExtractWordsFromText() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("username: john123, password: secret-pass");

        Set<String> words = discovery.getDiscoveredWords();
        assertTrue(words.contains("username"));
        assertTrue(words.contains("john123"));
        assertTrue(words.contains("password"));
        assertTrue(words.contains("secret-pass"));
    }

    @Test
    void testWordMustContainLetter() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("12345 abc123 456");

        Set<String> words = discovery.getDiscoveredWords();
        assertFalse(words.contains("12345")); // No letters
        assertTrue(words.contains("abc123"));  // Has letters
        assertFalse(words.contains("456"));    // No letters
    }

    @Test
    void testWordPatternWithSpecialChars() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("path/to/file user_name test-value test+plus");

        Set<String> words = discovery.getDiscoveredWords();
        assertTrue(words.contains("path/to/file"));  // Slash allowed
        assertTrue(words.contains("user_name"));     // Underscore allowed
        assertTrue(words.contains("test-value"));    // Dash allowed
        assertTrue(words.contains("test+plus"));     // Plus allowed
    }

    @Test
    void testWordsSorted() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("zebra apple monkey banana");

        Set<String> words = discovery.getDiscoveredWords();
        String[] wordsArray = words.toArray(new String[0]);

        assertEquals("apple", wordsArray[0]);
        assertEquals("banana", wordsArray[1]);
        assertEquals("monkey", wordsArray[2]);
        assertEquals("zebra", wordsArray[3]);
    }

    @Test
    void testDuplicateWordsOnlyCountedOnce() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("test test test");

        Set<String> words = discovery.getDiscoveredWords();
        assertEquals(1, words.size());
        assertTrue(words.contains("test"));
    }

    @Test
    void testEmptyText() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("");
        discovery.analyzeText(null);

        Set<String> words = discovery.getDiscoveredWords();
        assertEquals(0, words.size());
    }

    @Test
    void testMultipleLines() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("line1 word1");
        discovery.analyzeText("line2 word2");
        discovery.analyzeText("line3 word1"); // Duplicate

        Set<String> words = discovery.getDiscoveredWords();
        assertEquals(5, words.size()); // line1, line2, line3, word1, word2
        assertTrue(words.contains("line1"));
        assertTrue(words.contains("line2"));
        assertTrue(words.contains("line3"));
        assertTrue(words.contains("word1"));
        assertTrue(words.contains("word2"));
    }

    @Test
    void testStatistics() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("word1 word2 word3");

        String stats = discovery.getStatistics();
        assertTrue(stats.contains("3"));
        assertTrue(stats.contains("distinct"));
    }

    @Test
    void testHexadecimalValuesFiltered() {
        WordDiscovery discovery = new WordDiscovery();

        // Test various hex formats
        discovery.analyzeText("address: 0x000000017059e000 pointer: 0x7fff5fbff710 value: 0xDEADBEEF");
        discovery.analyzeText("Memory at 0x1234ABCD and 0xFEEDFACE");
        discovery.analyzeText("normal word 0x123abc mixed");

        Set<String> words = discovery.getDiscoveredWords();

        // Hexadecimal values should be filtered out
        assertFalse(words.contains("0x000000017059e000"), "Should filter out hex address");
        assertFalse(words.contains("0x7fff5fbff710"), "Should filter out hex pointer");
        assertFalse(words.contains("0xDEADBEEF"), "Should filter out hex value");
        assertFalse(words.contains("0x1234ABCD"), "Should filter out mixed hex");
        assertFalse(words.contains("0xFEEDFACE"), "Should filter out hex constant");
        assertFalse(words.contains("0x123abc"), "Should filter out lowercase hex");

        // Normal words should still be included
        assertTrue(words.contains("address"), "Should keep normal word 'address'");
        assertTrue(words.contains("pointer"), "Should keep normal word 'pointer'");
        assertTrue(words.contains("value"), "Should keep normal word 'value'");
        assertTrue(words.contains("Memory"), "Should keep normal word 'Memory'");
        assertTrue(words.contains("normal"), "Should keep normal word 'normal'");
        assertTrue(words.contains("word"), "Should keep normal word 'word'");
        assertTrue(words.contains("mixed"), "Should keep normal word 'mixed'");
    }

    @Test
    void testHexLikeButNotHex() {
        WordDiscovery discovery = new WordDiscovery();

        // Words that look like hex but aren't (no 0x prefix)
        discovery.analyzeText("DEADBEEF CAFEBABE test123abc");

        Set<String> words = discovery.getDiscoveredWords();

        // These should NOT be filtered (no 0x prefix)
        assertTrue(words.contains("DEADBEEF"), "Should keep hex-like word without 0x prefix");
        assertTrue(words.contains("CAFEBABE"), "Should keep hex-like word without 0x prefix");
        assertTrue(words.contains("test123abc"), "Should keep mixed alphanumeric");
    }
}