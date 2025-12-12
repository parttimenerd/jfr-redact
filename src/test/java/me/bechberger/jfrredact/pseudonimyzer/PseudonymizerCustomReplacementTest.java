package me.bechberger.jfrredact.pseudonimyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for custom replacement functionality in Pseudonymizer.
 */
public class PseudonymizerCustomReplacementTest {

    // ========== Test Data Providers ==========

    static Stream<Arguments> customReplacementCases() {
        return Stream.of(
            Arguments.of("johndoe", "alice", "Username replacement"),
            Arguments.of("admin", "user01", "Admin replacement"),
            Arguments.of("john.doe@company.com", "user@example.com", "Email replacement"),
            Arguments.of("/home/johndoe", "/home/testuser", "Linux path replacement"),
            Arguments.of("C:\\Users\\JohnDoe", "C:\\Users\\TestUser", "Windows path replacement"),
            Arguments.of("/Users/johndoe", "/Users/testuser", "Mac path replacement")
        );
    }

    // ========== Basic Custom Replacement Tests ==========

    @Test
    public void testCustomReplacement_Simple() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("johndoe", "alice");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.HASH)
            .customReplacements(replacements)
            .build();

        String result = pseudonymizer.pseudonymize("johndoe", "***");
        assertEquals("alice", result);
    }

    @Test
    public void testCustomReplacement_MultipleValues() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("johndoe", "alice");
        replacements.put("admin", "user01");
        replacements.put("root", "superuser");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .customReplacements(replacements)
            .build();

        assertEquals("alice", pseudonymizer.pseudonymize("johndoe", "***"));
        assertEquals("user01", pseudonymizer.pseudonymize("admin", "***"));
        assertEquals("superuser", pseudonymizer.pseudonymize("root", "***"));
    }

    @Test
    public void testCustomReplacement_ValueNotInMap() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("johndoe", "alice");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.HASH)
            .customReplacements(replacements)
            .build();

        // Value not in replacement map should use normal pseudonymization
        String result = pseudonymizer.pseudonymize("janedoe", "***");
        assertNotEquals("***", result);
        assertNotEquals("janedoe", result);
        assertTrue(result.startsWith("<redacted:"));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("customReplacementCases")
    public void testCustomReplacement_VariousTypes(String original, String expected, String description) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put(original, expected);

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .customReplacements(replacements)
            .build();

        String result = pseudonymizer.pseudonymize(original, "***");
        assertEquals(expected, result, description + " should use custom replacement");
    }

    // ========== Priority Tests ==========

    @Test
    public void testCustomReplacement_OverridesHashMode() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("test@example.com", "custom@test.org");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.HASH)
            .customReplacements(replacements)
            .build();

        String result = pseudonymizer.pseudonymize("test@example.com", "***");

        // Should use custom replacement, not hash
        assertEquals("custom@test.org", result);
        assertFalse(result.contains("<redacted:"));
    }

    @Test
    public void testCustomReplacement_OverridesRealisticMode() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("johndoe", "CUSTOM_USER");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.REALISTIC)
            .customReplacements(replacements)
            .build();

        String result = pseudonymizer.pseudonymize("johndoe", "***");

        // Should use custom replacement, not realistic generation
        assertEquals("CUSTOM_USER", result);
    }

    @Test
    public void testCustomReplacement_OverridesCounterMode() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("value1", "CUSTOM1");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.COUNTER)
            .customReplacements(replacements)
            .build();

        String result = pseudonymizer.pseudonymize("value1", "***");

        // Should use custom replacement, not counter
        assertEquals("CUSTOM1", result);
        assertFalse(result.contains("<redacted:"));
    }

    // ========== Consistency Tests ==========

    @Test
    public void testCustomReplacement_Consistency() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("johndoe", "alice");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .customReplacements(replacements)
            .build();

        String result1 = pseudonymizer.pseudonymize("johndoe", "***");
        String result2 = pseudonymizer.pseudonymize("johndoe", "***");

        assertEquals(result1, result2, "Same input should always produce same output");
        assertEquals("alice", result1);
    }

    @Test
    public void testCustomReplacement_MixedWithNormalPseudonymization() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("known_user", "alice");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.HASH)
            .customReplacements(replacements)
            .build();

        // Custom replacement
        String result1 = pseudonymizer.pseudonymize("known_user", "***");
        assertEquals("alice", result1);

        // Normal pseudonymization
        String result2 = pseudonymizer.pseudonymize("unknown_user", "***");
        assertNotEquals("unknown_user", result2);
        assertTrue(result2.startsWith("<redacted:"));

        // Another custom replacement query
        String result3 = pseudonymizer.pseudonymize("known_user", "***");
        assertEquals("alice", result3);
    }

    // ========== Builder Tests ==========

    @Test
    public void testBuilder_AddReplacement() {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .addReplacement("user1", "alice")
            .addReplacement("user2", "bob")
            .build();

        assertEquals("alice", pseudonymizer.pseudonymize("user1", "***"));
        assertEquals("bob", pseudonymizer.pseudonymize("user2", "***"));
    }

    @Test
    public void testBuilder_CustomReplacementsAndAddReplacement() {
        Map<String, String> initial = new HashMap<>();
        initial.put("user1", "alice");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .customReplacements(initial)
            .addReplacement("user2", "bob")
            .addReplacement("user3", "charlie")
            .build();

        assertEquals("alice", pseudonymizer.pseudonymize("user1", "***"));
        assertEquals("bob", pseudonymizer.pseudonymize("user2", "***"));
        assertEquals("charlie", pseudonymizer.pseudonymize("user3", "***"));
    }

    // ========== Edge Cases ==========

    @Test
    public void testCustomReplacement_NullValue() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("test", "replacement");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .customReplacements(replacements)
            .build();

        String result = pseudonymizer.pseudonymize(null, "***");
        assertEquals("***", result);
    }

    @Test
    public void testCustomReplacement_EmptyString() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("", "EMPTY_REPLACEMENT");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .customReplacements(replacements)
            .build();

        String result = pseudonymizer.pseudonymize("", "***");
        assertEquals("EMPTY_REPLACEMENT", result);
    }

    @Test
    public void testCustomReplacement_DisabledPseudonymizer() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("test", "replacement");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .enabled(false)
            .customReplacements(replacements)
            .build();

        // When pseudonymizer is disabled, should return fallback even with custom replacements
        String result = pseudonymizer.pseudonymize("test", "***");
        assertEquals("***", result);
    }

    @Test
    public void testCustomReplacement_NullReplacementMap() {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .customReplacements(null)
            .mode(PseudonymizationMode.HASH)
            .build();

        // Should work normally, just using standard pseudonymization
        String result = pseudonymizer.pseudonymize("test", "***");
        assertNotEquals("***", result);
        assertNotEquals("test", result);
    }

    @Test
    public void testCustomReplacement_EmptyReplacementMap() {
        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .customReplacements(new HashMap<>())
            .mode(PseudonymizationMode.HASH)
            .build();

        // Should work normally, just using standard pseudonymization
        String result = pseudonymizer.pseudonymize("test", "***");
        assertNotEquals("***", result);
        assertNotEquals("test", result);
    }

    // ========== Real-World Scenarios ==========

    @Test
    public void testCustomReplacement_RealWorldScenario() {
        // Real-world scenario: replacing specific known users and paths
        Map<String, String> replacements = new HashMap<>();

        // Replace specific usernames
        replacements.put("johndoe", "testuser01");
        replacements.put("admin", "testadmin");

        // Replace specific email addresses
        replacements.put("john.doe@company.com", "user01@example.com");
        replacements.put("admin@company.com", "admin@example.com");

        // Replace specific paths
        replacements.put("/home/johndoe", "/home/testuser01");
        replacements.put("C:\\Users\\JohnDoe", "C:\\Users\\TestUser01");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .mode(PseudonymizationMode.REALISTIC) // Use realistic for unknown values
            .customReplacements(replacements)
            .build();

        // Test known replacements
        assertEquals("testuser01", pseudonymizer.pseudonymize("johndoe", "***"));
        assertEquals("user01@example.com", pseudonymizer.pseudonymize("john.doe@company.com", "***"));
        assertEquals("/home/testuser01", pseudonymizer.pseudonymize("/home/johndoe", "***"));

        // Test unknown values fall back to realistic mode
        String unknownEmail = pseudonymizer.pseudonymize("jane.smith@company.com", "***");
        assertTrue(unknownEmail.contains("@"));
        assertNotEquals("jane.smith@company.com", unknownEmail);
        assertFalse(unknownEmail.contains("<redacted:"));
    }

    @Test
    public void testCustomReplacement_PathsWithSubdirectories() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("/home/johndoe/documents", "/home/testuser/documents");
        replacements.put("/home/johndoe", "/home/testuser");

        Pseudonymizer pseudonymizer = Pseudonymizer.builder()
            .customReplacements(replacements)
            .build();

        // Exact match on full path
        assertEquals("/home/testuser/documents",
                     pseudonymizer.pseudonymize("/home/johndoe/documents", "***"));

        // Exact match on partial path
        assertEquals("/home/testuser",
                     pseudonymizer.pseudonymize("/home/johndoe", "***"));

        // Different path should use normal pseudonymization
        String result = pseudonymizer.pseudonymize("/home/johndoe/downloads", "***");
        assertNotEquals("/home/johndoe/downloads", result);
    }
}