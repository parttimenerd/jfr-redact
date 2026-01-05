package me.bechberger.jfrredact.pseudonimyzer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PatternBasedGenerator redaction mode and cardinality warnings.
 */
public class PatternBasedGeneratorRedactionModeTest {

    private static final long FIXED_SEED = 42L;

    // ========== Random Generation Tests (Redaction Mode) ==========

    @Test
    public void testGenerateRandom_ReturnsValidValue() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generateRandom("usernames");

        assertNotNull(result);
        assertThat(result).as("Should match pattern: %s", result).matches("user\\d{3}");
    }

    @Test
    public void testGenerateRandom_DifferentValuesOnEachCall() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("tokens", "[a-z]{4}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generateRandom("tokens");
        String result2 = generator.generateRandom("tokens");
        String result3 = generator.generateRandom("tokens");

        // All should be valid
        assertThat(result1).matches("[a-z]{4}");
        assertThat(result2).matches("[a-z]{4}");
        assertThat(result3).matches("[a-z]{4}");

        // Should be different (with high probability)
        // At least one pair should be different
        boolean hasVariation = !result1.equals(result2) || !result2.equals(result3) || !result1.equals(result3);
        assertThat(hasVariation).as("Random generation should produce variation").isTrue();
    }

    @Test
    public void testGenerateRandom_WithPlaceholders() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("user_path", "/home/{users}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generateRandom("user_path");

        assertNotNull(result);
        assertThat(result).as("Should start with /home/").startsWith("/home/");
        assertThat(result).as("Should replace placeholder").doesNotContain("{users}");
    }

    @Test
    public void testGenerateRandom_MultipleCallsProduceDistribution() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("colors", "(red|green|blue)");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            generated.add(generator.generateRandom("colors"));
        }

        // Should have generated multiple different values
        assertThat(generated.size()).as("Should generate multiple different values: %s", generated).isGreaterThanOrEqualTo(2);

        // All should be valid
        for (String color : generated) {
            assertThat(color).as("Invalid color: %s", color).matches("(red|green|blue)");
        }
    }

    @Test
    public void testGenerateRandom_IPAddresses() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("ips", "10\\.0\\.[0-9]{1,3}\\.[0-9]{1,3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String ip = generator.generateRandom("ips");
            generated.add(ip);
            assertThat(ip).as("Invalid IP: %s", ip).matches("10\\.0\\.\\d{1,3}\\.\\d{1,3}");
        }

        assertThat(generated.size()).as("Should generate varied IPs").isGreaterThanOrEqualTo(5);
    }

    // ========== Pseudonymization Mode (Deterministic) Tests ==========

    @Test
    public void testGenerate_ConsistentForSameInput() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generate("usernames", "john");
        String result2 = generator.generate("usernames", "john");

        assertEquals(result1, result2, "Same input should produce same output");
    }

    @Test
    public void testGenerate_DifferentForDifferentInputs() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generate("usernames", "john");
        String result2 = generator.generate("usernames", "jane");

        assertNotEquals(result1, result2, "Different inputs should produce different outputs");
    }

    // ========== Mode Comparison Tests ==========

    @Test
    public void testRandomVsPseudonymization_RandomVaries() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("ids", "[0-9]{4}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        // Random mode - should vary
        String random1 = generator.generateRandom("ids");
        String random2 = generator.generateRandom("ids");

        // May be same by chance, but check multiple calls
        Set<String> randoms = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            randoms.add(generator.generateRandom("ids"));
        }
        assertThat(randoms.size()).as("Random mode should produce variation").isGreaterThan(1);

        // Pseudonymization mode - should be consistent
        String pseudo1 = generator.generate("ids", "test");
        String pseudo2 = generator.generate("ids", "test");
        assertEquals(pseudo1, pseudo2, "Pseudonymization should be consistent");
    }

    @Test
    public void testBothModes_IndependentCaches() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("values", "[a-c]");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        // Generate in pseudonymization mode
        String pseudo1 = generator.generate("values", "input1");
        String pseudo2 = generator.generate("values", "input2");

        // Generate random values
        String random1 = generator.generateRandom("values");

        // Pseudonymization should still be consistent
        assertEquals(pseudo1, generator.generate("values", "input1"));
        assertEquals(pseudo2, generator.generate("values", "input2"));
    }

    // ========== Cardinality Warning Tests ==========

    @Test
    public void testLowCardinality_StillWorks() {
        Map<String, String> patterns = new HashMap<>();
        // Very low cardinality - only 3 possible values
        patterns.put("low", "[a-c]");

        // Should create without throwing, but may log warning
        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generate("low", "val1");
        String result2 = generator.generate("low", "val2");
        String result3 = generator.generate("low", "val3");

        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
    }

    @Test
    public void testMediumCardinality_NoWarning() {
        Map<String, String> patterns = new HashMap<>();
        // Medium cardinality - 1000 possible values
        patterns.put("medium", "user[0-9]{3}");

        // Should create without warning
        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        assertNotNull(generator);
    }

    @Test
    public void testHighCardinality_NoWarning() {
        Map<String, String> patterns = new HashMap<>();
        // High cardinality - many possible values
        patterns.put("high", "[a-z]{5}[0-9]{5}");

        // Should create without warning
        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        assertNotNull(generator);
    }

    // ========== Iterator Exhaustion Tests ==========

    @Test
    public void testIteratorExhaustion_CyclesCorrectly() {
        Map<String, String> patterns = new HashMap<>();
        // Only 2 possible values
        patterns.put("binary", "[01]");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        // Generate more values than possible
        String val1 = generator.generate("binary", "input1");
        String val2 = generator.generate("binary", "input2");
        String val3 = generator.generate("binary", "input3"); // Should cycle

        assertNotNull(val1);
        assertNotNull(val2);
        assertNotNull(val3);

        // All should be valid
        assertThat(val1).matches("[01]");
        assertThat(val2).matches("[01]");
        assertThat(val3).matches("[01]");
    }

    @Test
    public void testIteratorExhaustion_MaintainsConsistency() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("small", "[xy]");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result1 = generator.generate("small", "test1");
        String result2 = generator.generate("small", "test2");
        String result3 = generator.generate("small", "test3");

        // Should maintain consistency on re-access
        assertEquals(result1, generator.generate("small", "test1"));
        assertEquals(result2, generator.generate("small", "test2"));
        assertEquals(result3, generator.generate("small", "test3"));
    }

    // ========== Edge Cases ==========

    @Test
    public void testGenerateRandom_NonExistentPattern() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("exists", "test");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generateRandom("nonexistent");

        assertNull(result, "Should return null for non-existent pattern");
    }

    @Test
    public void testGenerate_NonExistentPattern() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("exists", "test");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        String result = generator.generate("nonexistent", "value");

        assertNull(result, "Should return null for non-existent pattern");
    }

    // ========== Real-World Scenario Tests ==========

    @Test
    public void testRealWorld_RedactionMode() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("ssh_hosts", "srv[0-9]{2}\\.test\\.local");
        patterns.put("ip_addresses", "10\\.0\\.[0-9]{1,3}\\.[0-9]{1,3}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        // Use random mode for simple redaction
        String host1 = generator.generateRandom("ssh_hosts");
        String host2 = generator.generateRandom("ssh_hosts");
        String ip1 = generator.generateRandom("ip_addresses");
        String ip2 = generator.generateRandom("ip_addresses");

        // All should be valid
        assertThat(host1).matches("srv\\d{2}\\.test\\.local");
        assertThat(host2).matches("srv\\d{2}\\.test\\.local");
        assertThat(ip1).matches("10\\.0\\.\\d{1,3}\\.\\d{1,3}");
        assertThat(ip2).matches("10\\.0\\.\\d{1,3}\\.\\d{1,3}");
    }

    @Test
    public void testRealWorld_PseudonymizationMode() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("usernames", "user[0-9]{3}");
        patterns.put("unix_home", "/home/{users}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        // Use deterministic mode for pseudonymization
        String user1 = generator.generate("usernames", "john");
        String user2 = generator.generate("usernames", "jane");
        String path1 = generator.generate("unix_home", "/home/john");
        String path2 = generator.generate("unix_home", "/home/jane");

        // Should be consistent
        assertEquals(user1, generator.generate("usernames", "john"));
        assertEquals(user2, generator.generate("usernames", "jane"));
        assertEquals(path1, generator.generate("unix_home", "/home/john"));
        assertEquals(path2, generator.generate("unix_home", "/home/jane"));

        // Different inputs should produce different outputs
        assertNotEquals(user1, user2);
        assertNotEquals(path1, path2);
    }

    @Test
    public void testRealWorld_MixedUsage() {
        Map<String, String> patterns = new HashMap<>();
        patterns.put("tokens", "tok_[a-f0-9]{8}");

        PatternBasedGenerator generator = new PatternBasedGenerator(patterns, FIXED_SEED);

        // Random for simple redaction
        String randomToken = generator.generateRandom("tokens");
        assertThat(randomToken).matches("tok_[a-f0-9]{8}");

        // Deterministic for pseudonymization
        String pseudoToken1 = generator.generate("tokens", "secret1");
        String pseudoToken2 = generator.generate("tokens", "secret1");
        assertEquals(pseudoToken1, pseudoToken2);

        // Random should still work independently
        String randomToken2 = generator.generateRandom("tokens");
        assertThat(randomToken2).matches("tok_[a-f0-9]{8}");
    }
}