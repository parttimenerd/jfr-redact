package me.bechberger.jfrredact.pseudonimyzer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PatternBasedGenerator placeholder support and iterateUnique() usage.
 */
public class PatternBasedGeneratorPlaceholderTest {

    private static final long FIXED_SEED = 42L;

    // ========== Placeholder Tests ==========

    @Test
    public void testPlaceholder_Users() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("unix_home", "/home/{users}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("unix_home", "/home/johndoe");

        assertNotNull(result);
        assertTrue(result.startsWith("/home/"), "Should start with /home/: " + result);
        assertFalse(result.contains("{users}"), "Should replace {users} placeholder: " + result);
        assertFalse(result.matches(".*/home/user\\d+"), "Should use realistic names, not numbers: " + result);
    }

    @Test
    public void testPlaceholder_Users_Windows() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("win_home", "C:\\\\Users\\\\{users}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("win_home", "C:\\Users\\JohnDoe");

        assertNotNull(result);
        assertTrue(result.startsWith("C:\\Users\\"), "Should start with C:\\Users\\: " + result);
        assertFalse(result.contains("{users}"), "Should replace {users} placeholder: " + result);
    }

    @Test
    public void testPlaceholder_Users_WithSubdirectory() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("user_docs", "/home/{users}/documents");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("user_docs", "/home/johndoe/documents");

        assertNotNull(result);
        assertTrue(result.startsWith("/home/"), "Should start with /home/");
        assertTrue(result.endsWith("/documents"), "Should end with /documents");
        assertFalse(result.contains("{users}"), "Should replace {users} placeholder");
    }

    @Test
    public void testPlaceholder_Emails() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("email_pattern", "{emails}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("email_pattern", "john.doe@company.com");

        assertNotNull(result);
        assertTrue(result.contains("@"), "Should generate email with @: " + result);
        assertFalse(result.contains("{emails}"), "Should replace {emails} placeholder");
    }

    @Test
    public void testPlaceholder_Names() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("name_pattern", "User: {names}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("name_pattern", "User: johndoe");

        assertNotNull(result);
        assertTrue(result.startsWith("User: "), "Should preserve prefix");
        assertFalse(result.contains("{names}"), "Should replace {names} placeholder");
    }

    @Test
    public void testPlaceholder_MultiplePlaceholders() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("complex", "/home/{users} owned by {names}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("complex", "original");

        assertNotNull(result);
        assertFalse(result.contains("{users}"), "Should replace {users}");
        assertFalse(result.contains("{names}"), "Should replace {names}");
        assertTrue(result.contains(" owned by "), "Should preserve text between placeholders");
    }

    // ========== IterateUnique Tests ==========

    @Test
    public void testIterateUnique_GeneratesUniqueValues() {
        Map<String, String> patterns = new HashMap<>();
        // Pattern with limited possibilities
        patterns.put("simple", "[a-c]");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generate("simple", "original1");
        String result2 = generator.generate("simple", "original2");
        String result3 = generator.generate("simple", "original3");

        // Should generate different values for different inputs
        assertNotEquals(result1, result2);
        assertNotEquals(result2, result3);
    }

    @Test
    public void testIterateUnique_Consistency() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("pattern", "test[0-9]");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generate("pattern", "original");
        String result2 = generator.generate("pattern", "original");

        assertEquals(result1, result2, "Same input should produce same output");
    }

    @Test
    public void testIterateUnique_CyclesWhenExhausted() {
        Map<String, String> patterns = new HashMap<>();
        // Very limited pattern - only 2 possibilities: "a" and "b"
        patterns.put("limited", "[ab]");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        // Generate more values than the pattern can produce uniquely
        String result1 = generator.generate("limited", "val1");
        String result2 = generator.generate("limited", "val2");
        String result3 = generator.generate("limited", "val3"); // Should cycle

        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);

        // First two should be different
        assertNotEquals(result1, result2);
    }

    // ========== Integration Tests ==========

    @Test
    public void testRealWorldScenario_UserPaths() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("unix_home", "/home/{users}");
        patterns.put("mac_home", "/Users/{users}");
        patterns.put("win_home", "C:\\\\Users\\\\{users}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String unix = generator.generate("unix_home", "/home/johndoe");
        String mac = generator.generate("mac_home", "/Users/johndoe");
        String win = generator.generate("win_home", "C:\\Users\\JohnDoe");

        // All should have placeholders replaced
        assertFalse(unix.contains("{users}"));
        assertFalse(mac.contains("{users}"));
        assertFalse(win.contains("{users}"));

        // Should maintain path structure
        assertTrue(unix.startsWith("/home/"));
        assertTrue(mac.startsWith("/Users/"));
        assertTrue(win.startsWith("C:\\Users\\"));
    }

    @Test
    public void testRealWorldScenario_MixedPatternsAndPlaceholders() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("server_log", "srv[0-9]{2}/{users}/app\\.log");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("server_log", "/var/log/server/johndoe/app.log");

        assertNotNull(result);
        assertTrue(result.matches("srv\\d{2}/[a-z]+/app\\.log"),
                   "Should match pattern with realistic username: " + result);
    }

    @Test
    public void testPlaceholder_ConsistencyAcrossCalls() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("path", "/data/{users}/files");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generate("path", "/data/johndoe/files");
        String result2 = generator.generate("path", "/data/johndoe/files");

        assertEquals(result1, result2, "Should be consistent for same input");
    }

    @Test
    public void testPlaceholder_DifferentInputsGetDifferentUsers() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("path", "/home/{users}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generate("path", "/home/alice");
        String result2 = generator.generate("path", "/home/bob");

        assertNotEquals(result1, result2, "Different inputs should likely get different users");
    }

    // ========== Edge Cases ==========

    @Test
    public void testEmptyPattern() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("empty", "");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("empty", "original");

        // Empty pattern should generate empty string
        assertEquals("", result);
    }

    @Test
    public void testPatternWithOnlyPlaceholder() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("only_user", "{users}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("only_user", "johndoe");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertFalse(result.contains("{users}"));
    }

    @Test
    public void testMultipleSamePlaceholders() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("double", "{users}-{users}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("double", "test");

        assertNotNull(result);
        assertFalse(result.contains("{users}"));
        assertTrue(result.contains("-"), "Should preserve separator");
    }
}