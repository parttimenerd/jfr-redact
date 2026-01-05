package me.bechberger.jfrredact.pseudonimyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PatternBasedGenerator.
 */
public class PatternBasedGeneratorTest {

    private static final long FIXED_SEED = 42L;

    // ========== Test Data Providers ==========

    static Stream<Arguments> patternTestCases() {
        return Stream.of(
            Arguments.of("usernames", "user[0-9]{3}", "user\\d{3}", "Should generate user with 3 digits"),
            Arguments.of("hosts", "srv[0-9]{2}\\.local", "srv\\d{2}\\.local", "Should generate server hostname"),
            Arguments.of("ip_addresses", "10\\.0\\.[0-9]{1,3}\\.[0-9]{1,3}", "10\\.0\\.\\d{1,3}\\.\\d{1,3}", "Should generate IP address"),
            Arguments.of("tokens", "tok_[a-f0-9]{8}", "tok_[a-f0-9]{8}", "Should generate token"),
            Arguments.of("emails", "[a-z]{5}@test\\.com", "[a-z]{5}@test\\.com", "Should generate email")
        );
    }

    // ========== Basic Generation Tests ==========

    @Test
    public void testBasicGeneration() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("usernames", "original_value");

        assertThat(result).isNotNull();
        assertThat(result).matches("user\\d{3}");
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @MethodSource("patternTestCases")
    public void testVariousPatterns(String patternName, String regex, String validationRegex, String description) {
        Map<String, String> patterns = new HashMap<>();
        patterns.put(patternName, regex);

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate(patternName, "test_value");

        assertThat(result).isNotNull();
        assertThat(result).matches(validationRegex);
    }

    // ========== Consistency Tests ==========

    @Test
    public void testConsistency_SameInputProducesSameOutput() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String original = "john_doe";
        String result1 = generator.generate("usernames", original);
        String result2 = generator.generate("usernames", original);

        assertEquals(result1, result2, "Same input should produce same output");
    }

    @Test
    public void testConsistency_DifferentInputsProduceDifferentOutputs() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generate("usernames", "john");
        String result2 = generator.generate("usernames", "jane");

        // While not guaranteed, highly likely with FIXED_SEED to be different
        assertNotEquals(result1, result2, "Different inputs should likely produce different outputs");
    }

    @Test
    public void testDeterminism_SameSeedProducesSameInitialResults() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("tokens", "tok_[a-f0-9]{8}");

        PatternBasedGenerator gen1 = new PatternBasedGenerator(patterns, FIXED_SEED);
        PatternBasedGenerator gen2 = new PatternBasedGenerator(patterns, FIXED_SEED);

        String value = "test";
        String result1 = gen1.generate("tokens", value);
        String result2 = gen2.generate("tokens", value);

        assertEquals(result1, result2, "Same seed should produce same results");
    }

    // ========== Pattern Management Tests ==========

    @Test
    public void testHasPattern() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");
        patterns.put("emails", "[a-z]+@test\\.com");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        assertThat(generator.hasPattern("usernames")).isTrue();
        assertThat(generator.hasPattern("emails")).isTrue();
        assertThat(generator.hasPattern("nonexistent")).isFalse();
    }

    @Test
    public void testGetPatternNames() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("pattern1", "abc[0-9]");
        patterns.put("pattern2", "def[a-z]");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        assertEquals(2, generator.getPatternNames().size());
        assertThat(generator.getPatternNames()).contains("pattern1", "pattern2");
    }

    @Test
    public void testGetPattern() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("test", "pattern[0-9]{2}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        assertEquals("pattern[0-9]{2}", generator.getPattern("test"));
        assertNull(generator.getPattern("nonexistent"));
    }

    // ========== Cache Tests ==========

    @Test
    public void testCacheWorks() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String original = "john";
        String first = generator.generate("usernames", original);
        String second = generator.generate("usernames", original);

        assertSame(first, second, "Should return cached result");
    }

    @Test
    public void testClearPatternCache() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String original = "john";
        String first = generator.generate("usernames", original);

        generator.clearPatternCache("usernames");

        String second = generator.generate("usernames", original);

        // After clearing, might get different result
        assertNotNull(first);
        assertNotNull(second);
    }

    @Test
    public void testClearAllCaches() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("pattern1", "val[0-9]");
        patterns.put("pattern2", "val[a-z]");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        generator.generate("pattern1", "test1");
        generator.generate("pattern2", "test2");

        generator.clearAllCaches();

        // After clearing, should work fine (just regenerate)
        assertNotNull(generator.generate("pattern1", "test1"));
        assertNotNull(generator.generate("pattern2", "test2"));
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testGenerateNonExistentPattern() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("existing", "test[0-9]");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("nonexistent", "value");

        assertNull(result, "Should return null for non-existent pattern");
    }

    @Test
    public void testEmptyPatternMap() {
        Map<String, String> patterns = new HashMap<>();

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        assertThat(generator.hasPattern("anything")).isFalse();
        assertNull(generator.generate("anything", "value"));
    }

    // ========== Complex Pattern Tests ==========

    @Test
    public void testComplexIPPattern() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("ips", "10\\.0\\.[0-9]{1,3}\\.[0-9]{1,3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("ips", "192.168.1.1");

        assertNotNull(result);
        assertThat(result).startsWith("10.0.");
        assertThat(result).matches("10\\.0\\.\\d{1,3}\\.\\d{1,3}");
    }

    @Test
    public void testComplexEmailPattern() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("emails", "[a-z]{4,8}@(test|example)\\.(com|org)");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("emails", "original@company.com");

        assertNotNull(result);
        assertThat(result).contains("@");
        assertThat(result).matches("[a-z]{4,8}@(test|example)\\.(com|org)");
    }

    @Test
    public void testComplexTokenPattern() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("tokens", "tok_[A-Z]{2}[0-9]{6}[a-z]{2}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("tokens", "original_token");

        assertNotNull(result);
        assertThat(result).startsWith("tok_");
        assertThat(result).matches("tok_[A-Z]{2}\\d{6}[a-z]{2}");
    }

    // ========== Multiple Pattern Tests ==========

    @Test
    public void testMultiplePatterns() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");
        patterns.put("emails", "[a-z]+@test\\.com");
        patterns.put("tokens", "tok_[a-f0-9]{8}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String username = generator.generate("usernames", "john");
        String email = generator.generate("emails", "john@example.com");
        String token = generator.generate("tokens", "secret_token");

        assertThat(username).matches("user\\d{3}");
        assertThat(email).matches("[a-z]+@test\\.com");
        assertThat(token).matches("tok_[a-f0-9]{8}");
    }

    // ========== Real-World Scenario Tests ==========

    @Test
    public void testRealWorldScenario_MixedPatterns() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("ssh_hosts", "srv[0-9]{2}\\.test\\.local");
        patterns.put("ip_addresses", "192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}");
        patterns.put("api_keys", "key_[a-f0-9]{32}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String host = generator.generate("ssh_hosts", "prod-server-01.company.local");
        String ip = generator.generate("ip_addresses", "10.0.0.1");
        String key = generator.generate("api_keys", "original_api_key_12345");

        assertThat(host).matches("srv\\d{2}\\.test\\.local");
        assertThat(ip).matches("192\\.168\\.\\d{1,3}\\.\\d{1,3}");
        assertThat(key).matches("key_[a-f0-9]{32}");

        // Consistency check
        assertEquals(host, generator.generate("ssh_hosts", "prod-server-01.company.local"));
        assertEquals(ip, generator.generate("ip_addresses", "10.0.0.1"));
        assertEquals(key, generator.generate("api_keys", "original_api_key_12345"));
    }
}