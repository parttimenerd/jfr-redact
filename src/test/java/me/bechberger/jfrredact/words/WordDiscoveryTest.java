package me.bechberger.jfrredact.words;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

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
        assertThat(words).contains("username", "john123", "password", "secret-pass");
    }

    @Test
    void testWordMustContainLetter() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("12345 abc123 456");

        Set<String> words = discovery.getDiscoveredWords();
        assertThat(words).doesNotContain("12345", "456");
        assertThat(words).contains("abc123");
    }

    @Test
    void testWordPatternWithSpecialChars() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("path/to/file user_name test-value test+plus");

        Set<String> words = discovery.getDiscoveredWords();
        assertThat(words).contains("path/to/file", "user_name", "test-value", "test+plus");
    }

    @Test
    void testWordsSorted() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("zebra apple monkey banana");

        Set<String> words = discovery.getDiscoveredWords();
        String[] wordsArray = words.toArray(new String[0]);

        assertThat(wordsArray).containsExactly("apple", "banana", "monkey", "zebra");
    }

    @Test
    void testDuplicateWordsOnlyCountedOnce() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("test test test");

        Set<String> words = discovery.getDiscoveredWords();
        assertThat(words).hasSize(1).contains("test");
    }

    @Test
    void testEmptyText() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("");
        discovery.analyzeText(null);

        Set<String> words = discovery.getDiscoveredWords();
        assertThat(words).hasSize(0);
    }

    @Test
    void testMultipleLines() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("line1 word1");
        discovery.analyzeText("line2 word2");
        discovery.analyzeText("line3 word1"); // Duplicate

        Set<String> words = discovery.getDiscoveredWords();
        assertThat(words).hasSize(5).contains("line1", "line2", "line3", "word1", "word2");
    }

    @Test
    void testStatistics() {
        WordDiscovery discovery = new WordDiscovery();

        discovery.analyzeText("word1 word2 word3");

        String stats = discovery.getStatistics();
        assertThat(stats).contains("3", "distinct");
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
        assertThat(words).doesNotContain("0x000000017059e000", "0x7fff5fbff710", "0xDEADBEEF", "0x1234ABCD", "0xFEEDFACE", "0x123abc");

        // Normal words should still be included
        assertThat(words).contains("address", "pointer", "value", "Memory", "normal", "word", "mixed");
    }

    @Test
    void testHexLikeButNotHex() {
        WordDiscovery discovery = new WordDiscovery();

        // Words that look like hex but aren't (no 0x prefix)
        discovery.analyzeText("DEADBEEF CAFEBABE test123abc");

        Set<String> words = discovery.getDiscoveredWords();

        // These should NOT be filtered (no 0x prefix)
        assertThat(words).contains("DEADBEEF", "CAFEBABE", "test123abc");
    }
}