package me.bechberger.jfrredact.pseudonimyzer;

import me.bechberger.jfrredact.config.RedactionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Pseudonymizer class
 */
public class PseudonymizerTest {

    @Test
    public void testBasicPseudonymization() {
        Pseudonymizer pseudonymizer = Pseudonymizer.withDefaults();

        String value = "test@example.com";
        String pseudonym = pseudonymizer.pseudonymize(value, "***");

        assertNotNull(pseudonym);
        assertNotEquals("***", pseudonym);
        assertTrue(pseudonym.startsWith("<redacted:"));
        assertTrue(pseudonym.endsWith(">"));
    }

    @Test
    public void testConsistentMapping() {
        Pseudonymizer pseudonymizer = Pseudonymizer.withDefaults();

        String value = "test@example.com";
        String pseudonym1 = pseudonymizer.pseudonymize(value, "***");
        String pseudonym2 = pseudonymizer.pseudonymize(value, "***");

        assertEquals(pseudonym1, pseudonym2, "Same value should produce same pseudonym");
    }

    @Test
    public void testDifferentValuesGetDifferentPseudonyms() {
        Pseudonymizer pseudonymizer = Pseudonymizer.withDefaults();

        String value1 = "test1@example.com";
        String value2 = "test2@example.com";

        String pseudonym1 = pseudonymizer.pseudonymize(value1, "***");
        String pseudonym2 = pseudonymizer.pseudonymize(value2, "***");

        assertNotEquals(pseudonym1, pseudonym2, "Different values should produce different pseudonyms");
    }

    @Test
    public void testDisabledPseudonymizer() {
        Pseudonymizer pseudonymizer = Pseudonymizer.disabled();

        String value = "test@example.com";
        String result = pseudonymizer.pseudonymize(value, "***");

        assertEquals("***", result, "Disabled pseudonymizer should return fallback text");
        assertFalse(pseudonymizer.isEnabled());
    }

    /**
     * Provides test data for format tests: format, expectedPrefix, expectedSuffix
     */
    static Stream<Arguments> formatProvider() {
        return Stream.of(
            Arguments.of("redacted", "<redacted:", ">"),
            Arguments.of("hash", "<hash:", ">"),
            Arguments.of("custom", "[ANON-", "]")
        );
    }

    @ParameterizedTest
    @MethodSource("formatProvider")
    public void testDifferentFormats(String format, String expectedPrefix, String expectedSuffix) {
        String customPrefix = format.equals("custom") ? "[ANON-" : null;
        String customSuffix = format.equals("custom") ? "]" : null;

        Pseudonymizer.Builder builder = Pseudonymizer.builder()
            .format(PseudonymizationFormat.fromString(format));

        if (customPrefix != null) builder.customPrefix(customPrefix);
        if (customSuffix != null) builder.customSuffix(customSuffix);

        Pseudonymizer pseudonymizer = builder.build();

        String value = "test@example.com";
        String pseudonym = pseudonymizer.pseudonymize(value, "***");

        assertTrue(pseudonym.startsWith(expectedPrefix),
            "Format '" + format + "' should start with '" + expectedPrefix + "'");
        assertTrue(pseudonym.endsWith(expectedSuffix),
            "Format '" + format + "' should end with '" + expectedSuffix + "'");
    }

    // ========== REALISTIC Mode Tests ==========

    @Test
    public void testRealisticMode_Email() {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.REALISTIC)
            .build();

        String email = "john.doe@company.com";
        String result = pseudonymizer.pseudonymize(email, "***");

        assertNotNull(result);
        assertNotEquals(email, result, "Should generate different email");
        assertNotEquals("***", result, "Should not use fallback");

        // Should look like a real email
        assertTrue(result.contains("@"), "Result should contain @");
        assertTrue(result.contains("."), "Result should contain .");
        assertFalse(result.contains("<"), "Should not have brackets");
        assertFalse(result.contains("redacted"), "Should not contain 'redacted'");
    }

    @Test
    public void testRealisticMode_Path() {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.REALISTIC)
            .build();

        String path = "/home/johndoe/documents";
        String result = pseudonymizer.pseudonymize(path, "***");

        assertNotNull(result);
        assertNotEquals(path, result, "Should generate different path");
        assertTrue(result.startsWith("/home/"), "Should preserve path structure");
        assertFalse(result.contains("redacted"), "Should not contain 'redacted'");
    }

    @Test
    public void testRealisticMode_Username() {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.REALISTIC)
            .build();

        String username = "johndoe";
        String result = pseudonymizer.pseudonymize(username, "***");

        assertNotNull(result);
        assertNotEquals(username, result, "Should generate different username");
        assertFalse(result.contains("<"), "Should not have brackets");
        assertFalse(result.contains("redacted"), "Should not contain 'redacted'");
    }

    @Test
    public void testRealisticMode_Consistency() {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.REALISTIC)
            .build();

        String email = "test@example.com";
        String result1 = pseudonymizer.pseudonymize(email, "***");
        String result2 = pseudonymizer.pseudonymize(email, "***");

        assertEquals(result1, result2, "Same input should produce same realistic output");
    }

    @Test
    public void testRealisticMode_DifferentInputs() {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.REALISTIC)
            .build();

        String email1 = "user1@test.com";
        String email2 = "user2@test.com";

        String result1 = pseudonymizer.pseudonymize(email1, "***");
        String result2 = pseudonymizer.pseudonymize(email2, "***");

        assertNotEquals(result1, result2, "Different inputs should produce different outputs");
    }

    @Test
    public void testRealisticMode_WindowsPath() {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.REALISTIC)
            .build();

        String path = "C:\\Users\\JohnDoe\\Documents";
        String result = pseudonymizer.pseudonymize(path, "***");

        assertNotNull(result);
        assertTrue(result.startsWith("C:\\Users\\"), "Should preserve Windows path structure");
        assertFalse(result.contains("JohnDoe"), "Should replace username");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SHA-256", "SHA-1", "MD5"})
    public void testHashAlgorithms(String algorithm) {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .hashAlgorithm(algorithm)
            .build();

        String value = "test@example.com";
        String pseudonym = pseudonymizer.pseudonymize(value, "***");

        assertNotNull(pseudonym);
        assertTrue(pseudonym.startsWith("<redacted:"));
        assertTrue(pseudonym.endsWith(">"));
    }

    @ParameterizedTest
    @CsvSource({
        "6, 6",
        "8, 8",
        "12, 12",
        "16, 16",
        "32, 32"
    })
    public void testHashLengths(int hashLength, int expectedMinLength) {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .hashLength(hashLength)
            .build();

        String value = "test@example.com";
        String pseudonym = pseudonymizer.pseudonymize(value, "***");

        // Extract hash part (between <redacted: and >)
        String hashPart = pseudonym.substring("<redacted:".length(), pseudonym.length() - 1);
        assertTrue(hashPart.length() >= expectedMinLength,
            "Hash part should be at least " + expectedMinLength + " characters");
    }

    @Test
    public void testHashLength() {
        Pseudonymizer p1 = Pseudonymizer.builder()
            .hashLength(6)
            .build();

        Pseudonymizer p2 = Pseudonymizer.builder()
            .hashLength(16)
            .build();

        String value = "test@example.com";
        String pseudonym1 = p1.pseudonymize(value, "***");
        String pseudonym2 = p2.pseudonymize(value, "***");

        // Extract hash part (between < > or : >)
        int hash1Length = pseudonym1.length() - "<redacted:>".length();
        int hash2Length = pseudonym2.length() - "<redacted:>".length();

        assertTrue(hash2Length > hash1Length, "Longer hash length should produce longer identifier");
    }

    @Test
    public void testCacheSize() {
        Pseudonymizer pseudonymizer = Pseudonymizer.withDefaults();

        assertEquals(0, pseudonymizer.getCacheSize());

        pseudonymizer.pseudonymize("value1", "***");
        assertEquals(1, pseudonymizer.getCacheSize());

        pseudonymizer.pseudonymize("value1", "***"); // Same value
        assertEquals(1, pseudonymizer.getCacheSize());

        pseudonymizer.pseudonymize("value2", "***"); // New value
        assertEquals(2, pseudonymizer.getCacheSize());
    }

    @Test
    public void testClearCache() {
        Pseudonymizer pseudonymizer = Pseudonymizer.withDefaults();

        pseudonymizer.pseudonymize("value1", "***");
        pseudonymizer.pseudonymize("value2", "***");
        assertEquals(2, pseudonymizer.getCacheSize());

        pseudonymizer.clearCache();
        assertEquals(0, pseudonymizer.getCacheSize());

        // After clearing, same value should still get a pseudonym
        // (though it might be different from before due to new cache)
        String pseudonym = pseudonymizer.pseudonymize("value1", "***");
        assertNotNull(pseudonym);
        assertNotEquals("***", pseudonym);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "user@example.com",
        "admin@test.org",
        "192.168.1.1",
        "secret-password-123",
        "/Users/john/documents",
        "API_KEY_12345"
    })
    public void testConsistentMappingForVariousValues(String value) {
        Pseudonymizer pseudonymizer = Pseudonymizer.withDefaults();

        String pseudonym1 = pseudonymizer.pseudonymize(value, "***");
        String pseudonym2 = pseudonymizer.pseudonymize(value, "***");

        assertEquals(pseudonym1, pseudonym2,
            "Same value '" + value + "' should produce same pseudonym");
        assertNotEquals("***", pseudonym1,
            "Value should be pseudonymized, not redacted");
    }

    static Stream<Arguments> scopeProvider() {
        return Stream.of(
            Arguments.of(true, false, true, false),
            Arguments.of(false, true, false, true),
            Arguments.of(true, true, false, false),
            Arguments.of(false, false, true, true),
            Arguments.of(true, true, true, true),
            Arguments.of(false, false, false, false)
        );
    }

    @ParameterizedTest
    @MethodSource("scopeProvider")
    public void testScopeConfigurations(boolean properties, boolean strings,
                                       boolean network, boolean paths) {
        Pseudonymizer.PseudonymizationScope scope = new Pseudonymizer.PseudonymizationScope(
            properties, strings, network, paths
        );

        assertEquals(properties, scope.shouldPseudonymizeProperties());
        assertEquals(strings, scope.shouldPseudonymizeStrings());
        assertEquals(network, scope.shouldPseudonymizeNetwork());
        assertEquals(paths, scope.shouldPseudonymizePaths());
    }

    @Test
    public void testScope() {
        Pseudonymizer.PseudonymizationScope scope = new Pseudonymizer.PseudonymizationScope(
            true, false, true, false
        );

        assertTrue(scope.shouldPseudonymizeProperties());
        assertFalse(scope.shouldPseudonymizeStrings());
        assertTrue(scope.shouldPseudonymizeNetwork());
        assertFalse(scope.shouldPseudonymizePaths());
    }

    @Test
    public void testNullValue() {
        Pseudonymizer pseudonymizer = Pseudonymizer.withDefaults();

        String result = pseudonymizer.pseudonymize(null, "***");
        assertEquals("***", result, "Null value should return fallback text");
    }

    @Test
    public void testGetStats() {
        Pseudonymizer pseudonymizer = Pseudonymizer.withDefaults();

        pseudonymizer.pseudonymize("value1", "***");
        pseudonymizer.pseudonymize("value2", "***");

        String stats = pseudonymizer.getStats();
        assertNotNull(stats);
        assertTrue(stats.contains("enabled"));
        assertTrue(stats.contains("2"));
    }

    @Test
    public void testConfigIntegration() {
        RedactionConfig config = new RedactionConfig();
        config.getGeneral().getPseudonymization().setEnabled(true);
        config.getGeneral().getPseudonymization().setFormat("hash");
        config.getGeneral().getPseudonymization().setHashLength(12);

        Pseudonymizer pseudonymizer = config.createPseudonymizer();

        assertTrue(pseudonymizer.isEnabled());

        String value = "test@example.com";
        String pseudonym = pseudonymizer.pseudonymize(value, "***");

        assertTrue(pseudonym.startsWith("<hash:"));
    }
}