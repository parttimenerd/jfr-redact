package me.bechberger.jfrredact.pseudonimyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for counter mode and port pseudonymization.
 */
public class PseudonymizerCounterModeTest {

    @Test
    public void testCounterModeSequential() {
        Pseudonymizer p = Pseudonymizer.builder()
            .mode(PseudonymizationMode.COUNTER)
            .build();

        String pseudo1 = p.pseudonymize("value1", "***");
        String pseudo2 = p.pseudonymize("value2", "***");
        String pseudo3 = p.pseudonymize("value3", "***");

        assertEquals("<redacted:1>", pseudo1);
        assertEquals("<redacted:2>", pseudo2);
        assertEquals("<redacted:3>", pseudo3);
    }

    @Test
    public void testCounterModeConsistency() {
        Pseudonymizer p = Pseudonymizer.builder()
            .mode(PseudonymizationMode.COUNTER)
            .build();

        String pseudo1a = p.pseudonymize("value1", "***");
        String pseudo2 = p.pseudonymize("value2", "***");
        String pseudo1b = p.pseudonymize("value1", "***");

        assertEquals("<redacted:1>", pseudo1a);
        assertEquals("<redacted:2>", pseudo2);
        assertEquals("<redacted:1>", pseudo1b, "Same value should return same counter");
    }

    @Test
    public void testHashModeStillWorks() {
        Pseudonymizer p = Pseudonymizer.builder()
            .mode(PseudonymizationMode.HASH)
            .build();

        String pseudo = p.pseudonymize("test", "***");

        assertNotEquals("<redacted:1>", pseudo, "Hash mode should not use counter");
        assertThat(pseudo).startsWith("<redacted:");
        assertThat(pseudo).endsWith(">");
        assertThat(pseudo.length()).as("Hash should be longer than counter").isGreaterThan(15);
    }

    @ParameterizedTest
    @CsvSource({
        "8080, 1000",
        "443, 1001",
        "3306, 1002",
        "5432, 1003"
    })
    public void testPortPseudonymizationSequential(int originalPort, int expectedPseudo) {
        Pseudonymizer p = Pseudonymizer.withDefaults();

        // Process ports in order
        int port1 = p.pseudonymizePort(8080);
        int port2 = p.pseudonymizePort(443);
        int port3 = p.pseudonymizePort(3306);
        int port4 = p.pseudonymizePort(5432);

        assertEquals(1000, port1);
        assertEquals(1001, port2);
        assertEquals(1002, port3);
        assertEquals(1003, port4);
    }

    @Test
    public void testPortPseudonymizationConsistency() {
        Pseudonymizer p = Pseudonymizer.withDefaults();

        int first = p.pseudonymizePort(8080);
        int second = p.pseudonymizePort(443);
        int againFirst = p.pseudonymizePort(8080);
        int third = p.pseudonymizePort(3306);
        int againSecond = p.pseudonymizePort(443);

        assertEquals(1000, first);
        assertEquals(1001, second);
        assertEquals(1000, againFirst, "Same port should return same pseudonym");
        assertEquals(1002, third);
        assertEquals(1001, againSecond, "Same port should return same pseudonym");
    }

    @Test
    public void testPortPseudonymizationDisabled() {
        Pseudonymizer p = Pseudonymizer.builder()
            .scope(new Pseudonymizer.PseudonymizationScope(true, true, true, true, false))  // ports disabled
            .build();

        int result = p.pseudonymizePort(8080);
        assertEquals(8080, result, "Port should not be pseudonymized when disabled");
    }

    @Test
    public void testPortPseudonymizationWhenGloballyDisabled() {
        Pseudonymizer p = Pseudonymizer.disabled();

        int result = p.pseudonymizePort(8080);
        assertEquals(8080, result, "Port should not be pseudonymized when globally disabled");
    }

    @ParameterizedTest
    @ValueSource(ints = {80, 443, 8080, 3000, 5432, 27017, 6379})
    public void testVariousPortsGetDifferentPseudonyms(int port) {
        Pseudonymizer p = Pseudonymizer.withDefaults();

        int pseudo = p.pseudonymizePort(port);

        assertThat(pseudo).as("Pseudonymized port should be >= 1000").isGreaterThanOrEqualTo(1000);
        assertThat(pseudo).as("Pseudonymized port should be < 2000 for reasonable test").isLessThan(2000);
    }

    @Test
    public void testClearCacheResetsCounters() {
        Pseudonymizer p = Pseudonymizer.builder()
            .mode(PseudonymizationMode.COUNTER)
            .build();

        p.pseudonymize("value1", "***");
        p.pseudonymize("value2", "***");
        p.pseudonymizePort(8080);
        p.pseudonymizePort(443);

        assertEquals(2, p.getCacheSize());

        p.clearCache();

        assertEquals(0, p.getCacheSize());

        // After clear, counters should reset
        String newPseudo = p.pseudonymize("value3", "***");
        int newPort = p.pseudonymizePort(3306);

        assertEquals("<redacted:1>", newPseudo, "Counter should reset to 1");
        assertEquals(1000, newPort, "Port counter should reset to 1000");
    }

    @Test
    public void testCounterModeWithDifferentFormats() {
        Pseudonymizer hashFormat = Pseudonymizer.builder()
            .mode(PseudonymizationMode.COUNTER)
            .format(PseudonymizationFormat.HASH)
            .build();

        Pseudonymizer customFormat = Pseudonymizer.builder()
            .mode(PseudonymizationMode.COUNTER)
            .format(PseudonymizationFormat.CUSTOM)
            .customPrefix("[ID:")
            .customSuffix("]")
            .build();

        assertEquals("<hash:1>", hashFormat.pseudonymize("test", "***"));
        assertEquals("[ID:1]", customFormat.pseudonymize("test", "***"));
    }

    @Test
    public void testPortsAlwaysUseCounter() {
        // Even with hash mode globally, ports should use counter
        Pseudonymizer hashMode = Pseudonymizer.builder()
            .mode(PseudonymizationMode.HASH)
            .build();

        int port1 = hashMode.pseudonymizePort(8080);
        int port2 = hashMode.pseudonymizePort(443);
        int port3 = hashMode.pseudonymizePort(8080);  // Repeat

        assertEquals(1000, port1);
        assertEquals(1001, port2);
        assertEquals(1000, port3, "Ports should use counter even in hash mode");
    }
}