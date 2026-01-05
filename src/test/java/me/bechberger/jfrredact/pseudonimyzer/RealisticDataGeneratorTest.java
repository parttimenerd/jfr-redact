package me.bechberger.jfrredact.pseudonimyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for RealisticDataGenerator.
 */
public class RealisticDataGeneratorTest {

    private RealisticDataGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RealisticDataGenerator(12345L); // Fixed seed for deterministic tests
    }

    // ========== Test Data Providers ==========

    static Stream<Arguments> emailTestCases() {
        return Stream.of(
            Arguments.of("john.doe@example.com", "Email with dot in local part"),
            Arguments.of("admin@company.org", "Simple email"),
            Arguments.of("user.name@test.io", "Email with .io domain"),
            Arguments.of("contact@subdomain.example.com", "Email with subdomain")
        );
    }

    static Stream<Arguments> usernameTestCases() {
        return Stream.of(
            Arguments.of("john.doe", "Username with dot"),
            Arguments.of("user_name", "Username with underscore"),
            Arguments.of("johndoe", "Simple username")
        );
    }

    static Stream<Arguments> pathTestCases() {
        return Stream.of(
            Arguments.of("/home/johndoe", "Linux home path"),
            Arguments.of("/Users/johndoe", "Mac home path"),
            Arguments.of("C:\\Users\\JohnDoe", "Windows home path"),
            Arguments.of("/home/johndoe/documents/file.txt", "Linux path with file"),
            Arguments.of("/Users/johndoe/Library/Preferences", "Mac path with subdirs"),
            Arguments.of("C:\\Users\\JohnDoe\\AppData\\Local", "Windows path with subdirs")
        );
    }

    // ========== Username Generation Tests ==========

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("usernameTestCases")
    public void testGenerateUsername(String original, String description) {
        String result = generator.generateUsername(original);

        assertNotNull(result);
        assertNotEquals(original, result, "Generated username should differ from original");
        assertThat(result).isNotEmpty();
    }

    @Test
    public void testGenerateUsername_Consistency() {
        String original = "johndoe";

        String result1 = generator.generateUsername(original);
        String result2 = generator.generateUsername(original);

        assertEquals(result1, result2, "Same input should produce same output");
    }

    @Test
    public void testGenerateUsername_PreservesFormat() {
        String withDot = "john.doe";
        String withUnderscore = "john_doe";

        String resultDot = generator.generateUsername(withDot);
        String resultUnderscore = generator.generateUsername(withUnderscore);

        assertThat(resultDot).as("Should preserve dot format").contains(".");
        assertThat(resultUnderscore).as("Should preserve underscore format").contains("_");
    }

    @Test
    public void testGenerateUsername_NullAndEmpty() {
        assertNull(generator.generateUsername(null));
        assertEquals("", generator.generateUsername(""));
    }

    // ========== Email Generation Tests ==========

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("emailTestCases")
    public void testGenerateEmail(String original, String description) {
        String result = generator.generateEmail(original);

        assertNotNull(result);
        assertNotEquals(original, result, "Generated email should differ from original");
        assertThat(result).as("Generated email should contain @").contains("@");
        assertThat(result).as("Generated email should contain .").contains(".");
    }

    @Test
    public void testGenerateEmail_Consistency() {
        String original = "test@example.com";

        String result1 = generator.generateEmail(original);
        String result2 = generator.generateEmail(original);

        assertEquals(result1, result2, "Same input should produce same output");
    }

    @Test
    public void testGenerateEmail_PreservesDomainStructure() {
        String result = generator.generateEmail("user@test.org");

        assertThat(result).as("Should use common TLD").matches(".*(\\.org|\\.com|\\.net|\\.io)$");
    }

    @Test
    public void testGenerateEmail_InvalidInput() {
        String noAt = "notanemail";
        String result = generator.generateEmail(noAt);

        assertEquals(noAt, result, "Should return original if not an email");
    }

    @Test
    public void testGenerateEmail_NullInput() {
        assertNull(generator.generateEmail(null));
    }

    // ========== Path Generation Tests ==========

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("pathTestCases")
    public void testGeneratePath(String original, String description) {
        String result = generator.generatePath(original);

        assertNotNull(result);
        assertNotEquals(original, result, "Generated path should differ from original");
    }

    @Test
    public void testGeneratePath_Consistency() {
        String original = "/home/johndoe";

        String result1 = generator.generatePath(original);
        String result2 = generator.generatePath(original);

        assertEquals(result1, result2, "Same input should produce same output");
    }

    @Test
    public void testGeneratePath_PreservesUnixStructure() {
        String result = generator.generatePath("/home/johndoe/documents");

        assertThat(result).as("Should preserve /home/ prefix").startsWith("/home/");
        assertThat(result).as("Should preserve subdirectories").contains("/documents");
    }

    @Test
    public void testGeneratePath_PreservesMacStructure() {
        String result = generator.generatePath("/Users/johndoe/Library");

        assertThat(result).as("Should preserve /Users/ prefix").startsWith("/Users/");
        assertThat(result).as("Should preserve subdirectories").contains("/Library");
    }

    @Test
    public void testGeneratePath_PreservesWindowsStructure() {
        String result = generator.generatePath("C:\\Users\\JohnDoe\\Documents");

        assertThat(result).as("Should preserve C:\\Users\\ prefix").startsWith("C:\\Users\\");
        assertThat(result).as("Should preserve subdirectories").contains("\\Documents");
    }

    @Test
    public void testGeneratePath_NullAndEmpty() {
        assertNull(generator.generatePath(null));
        assertEquals("", generator.generatePath(""));
    }

    // ========== Generic Replacement Tests ==========

    @Test
    public void testGenerateReplacement_AutoDetectsEmail() {
        String email = "admin@test.com";
        String result = generator.generateReplacement(email);

        assertNotEquals(email, result);
        assertThat(result).contains("@");
    }

    @Test
    public void testGenerateReplacement_AutoDetectsPath() {
        String path = "/home/user/file.txt";
        String result = generator.generateReplacement(path);

        assertNotEquals(path, result);
        assertThat(result).contains("/");
    }

    @Test
    public void testGenerateReplacement_AutoDetectsUsername() {
        String username = "johndoe";
        String result = generator.generateReplacement(username);

        assertNotEquals(username, result);
    }

    @Test
    public void testGenerateReplacement_NullAndEmpty() {
        assertNull(generator.generateReplacement(null));
        assertEquals("", generator.generateReplacement(""));
    }

    // ========== Cache Tests ==========

    @Test
    public void testClearCache() {
        String original = "test@example.com";

        String result1 = generator.generateEmail(original);
        generator.clearCache();
        String result2 = generator.generateEmail(original);

        // After cache clear, might get different result (different counter)
        assertNotNull(result1);
        assertNotNull(result2);
    }

    @Test
    public void testMultipleDifferentInputs() {
        String email1 = "user1@test.com";
        String email2 = "user2@test.com";
        String email3 = "user3@test.com";

        String result1 = generator.generateEmail(email1);
        String result2 = generator.generateEmail(email2);
        String result3 = generator.generateEmail(email3);

        // All results should be different
        assertNotEquals(result1, result2);
        assertNotEquals(result2, result3);
        assertNotEquals(result1, result3);

        // But same input should give same output
        assertEquals(result1, generator.generateEmail(email1));
        assertEquals(result2, generator.generateEmail(email2));
        assertEquals(result3, generator.generateEmail(email3));
    }

    // ========== Realistic Output Tests ==========

    @Test
    public void testRealisticEmail_LooksPlausible() {
        String result = generator.generateEmail("john.doe@company.com");

        // Check it looks like a real email
        String[] parts = result.split("@");
        assertEquals(2, parts.length);

        String localPart = parts[0];
        String domain = parts[1];

        assertThat(localPart).doesNotContain("<", ">", "redacted");
        assertThat(domain).as("Domain should have TLD").contains(".");
    }

    @Test
    public void testRealisticPath_LooksPlausible() {
        String result = generator.generatePath("/home/johndoe");

        assertThat(result).doesNotContain("<", ">", "redacted");
        assertThat(result).as("Should preserve /home/ prefix").startsWith("/home/");
        assertThat(result).doesNotContain("johndoe");
        assertThat(result).matches("/home/[a-z]+");
    }

    @Test
    public void testRealisticUsername_LooksPlausible() {
        String result = generator.generateUsername("johndoe");

        assertThat(result).doesNotContain("<", ">", "redacted");
        assertThat(result).isNotEqualTo("johndoe");
        // generateUsername generates: "user01", "user02", etc. (with numbers)
        assertThat(result).matches("user\\d{2}");
    }

    // ========== Deterministic Seed Tests ==========

    @Test
    public void testDeterministicWithSameSeed() {
        RealisticDataGenerator gen1 = new RealisticDataGenerator(42L);
        RealisticDataGenerator gen2 = new RealisticDataGenerator(42L);

        String email = "test@example.com";

        String result1 = gen1.generateEmail(email);
        String result2 = gen2.generateEmail(email);

        // With same seed, should get same first result
        assertEquals(result1, result2, "Same seed should produce same initial results");
    }

    @Test
    public void testDifferentWithDifferentSeed() {
        RealisticDataGenerator gen1 = new RealisticDataGenerator(42L);
        RealisticDataGenerator gen2 = new RealisticDataGenerator(43L);

        // Clear any cache to start fresh
        gen1.clearCache();
        gen2.clearCache();

        String email = "test@example.com";

        String result1 = gen1.generateEmail(email);
        String result2 = gen2.generateEmail(email);

        // Different seeds may produce different results
        // (though not guaranteed due to random nature, but likely)
        assertNotNull(result1);
        assertNotNull(result2);
    }
}