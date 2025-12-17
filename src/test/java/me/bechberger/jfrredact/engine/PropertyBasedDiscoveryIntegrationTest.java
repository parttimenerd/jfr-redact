package me.bechberger.jfrredact.engine;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.config.DiscoveryConfig;
import me.bechberger.jfrredact.config.StringConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for property-based pattern discovery using actual JFR events.
 */
class PropertyBasedDiscoveryIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Custom JFR event for testing with a user.name property
     */
    static class UserEvent extends Event {
        @jdk.jfr.Label("User Name")
        public String userName;
    }

    /**
     * Custom JFR event for testing with a hostname property
     */
    static class HostEvent extends Event {
        @jdk.jfr.Label("Hostname")
        public String hostname;
    }

    /**
     * Custom JFR event for testing with multiple properties
     */
    static class SystemEvent extends Event {
        @jdk.jfr.Label("User")
        public String user;

        @jdk.jfr.Label("Host")
        public String host;

        @jdk.jfr.Label("Process ID")
        public int pid;
    }

    /**
     * Custom JFR event for testing key-value pair extraction
     */
    static class KeyValueEvent extends Event {
        @jdk.jfr.Label("Key")
        public String key;

        @jdk.jfr.Label("Value")
        public String value;
    }

    @Test
    void testPropertyExtractionBasic() throws IOException {
        Path jfrFile = tempDir.resolve("test.jfr");

        // Create JFR recording with user events
        try (Recording recording = new Recording()) {
            recording.start();

            UserEvent event1 = new UserEvent();
            event1.userName = "i560383";
            event1.commit();

            UserEvent event2 = new UserEvent();
            event2.userName = "i560383";  // Same user again
            event2.commit();

            UserEvent event3 = new UserEvent();
            event3.userName = "john.doe";
            event3.commit();

            recording.stop();
            recording.dump(jfrFile);
        }

        // Setup discovery config to extract from userName property
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("user_name");
        propExtraction.setKeyPattern("userName");
        propExtraction.setType("USERNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(1);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false); // Disable string patterns to isolate property extraction

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Process the JFR file
        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                engine.analyzeEvent(event);
            }
        }

        // Get discovered patterns
        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Debug output
        System.out.println("Discovery statistics:");
        System.out.println(engine.getStatistics());
        System.out.println("Total patterns found: " + patterns.getTotalCount());
        System.out.println("All discovered values: " + patterns.getAllValues());

        // Verify both usernames were discovered
        assertTrue(patterns.contains("i560383"), "Should contain i560383");
        assertTrue(patterns.contains("john.doe"), "Should contain john.doe");

        assertEquals(2, patterns.get("i560383").getOccurrences());
        assertEquals(1, patterns.get("john.doe").getOccurrences());
        assertEquals(DiscoveredPatterns.PatternType.USERNAME, patterns.get("i560383").getType());
    }

    @Test
    void testPropertyExtractionWithEventTypeFilter() throws IOException {
        Path jfrFile = tempDir.resolve("test-filter.jfr");

        // Create JFR recording with different event types
        try (Recording recording = new Recording()) {
            recording.start();

            HostEvent event1 = new HostEvent();
            event1.hostname = "server01";
            event1.commit();

            HostEvent event2 = new HostEvent();
            event2.hostname = "server02";
            event2.commit();

            recording.stop();
            recording.dump(jfrFile);
        }

        // Setup discovery config with event type filter
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("hostname");
        propExtraction.setKeyPattern("hostname");
        propExtraction.setEventTypeFilter(".*HostEvent");  // Only HostEvent events
        propExtraction.setType("HOSTNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(1);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Process the JFR file
        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                engine.analyzeEvent(event);
            }
        }

        // Get discovered patterns
        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Verify hostnames were discovered
        assertTrue(patterns.contains("server01"));
        assertTrue(patterns.contains("server02"));
    }

    @Test
    void testPropertyExtractionWithWhitelist() throws IOException {
        Path jfrFile = tempDir.resolve("test-whitelist.jfr");

        // Create JFR recording
        try (Recording recording = new Recording()) {
            recording.start();

            UserEvent event1 = new UserEvent();
            event1.userName = "john";
            event1.commit();

            UserEvent event2 = new UserEvent();
            event2.userName = "root";  // Should be whitelisted
            event2.commit();

            UserEvent event3 = new UserEvent();
            event3.userName = "admin";  // Should be whitelisted
            event3.commit();

            UserEvent event4 = new UserEvent();
            event4.userName = "jane";
            event4.commit();

            recording.stop();
            recording.dump(jfrFile);
        }

        // Setup discovery config with whitelist
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("user_name");
        propExtraction.setKeyPattern("userName");
        propExtraction.setType("USERNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(1);

        List<String> whitelist = new ArrayList<>();
        whitelist.add("root");
        whitelist.add("admin");
        propExtraction.setWhitelist(whitelist);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Process the JFR file
        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                engine.analyzeEvent(event);
            }
        }

        // Get discovered patterns
        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Verify whitelisted usernames were NOT discovered
        assertTrue(patterns.contains("john"));
        assertTrue(patterns.contains("jane"));
        assertFalse(patterns.contains("root"));
        assertFalse(patterns.contains("admin"));
    }

    @Test
    void testPropertyExtractionWithMinOccurrences() throws IOException {
        Path jfrFile = tempDir.resolve("test-min-occurrences.jfr");

        // Create JFR recording
        try (Recording recording = new Recording()) {
            recording.start();

            UserEvent event1 = new UserEvent();
            event1.userName = "alice";  // 1 occurrence
            event1.commit();

            UserEvent event2 = new UserEvent();
            event2.userName = "bob";    // 1st occurrence
            event2.commit();

            UserEvent event3 = new UserEvent();
            event3.userName = "bob";    // 2nd occurrence
            event3.commit();

            recording.stop();
            recording.dump(jfrFile);
        }

        // Setup discovery config with min_occurrences = 2
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("user");
        propExtraction.setKeyPattern("userName");
        propExtraction.setType("USERNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(2);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Process the JFR file
        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                engine.analyzeEvent(event);
            }
        }

        // Get discovered patterns
        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Verify only "bob" was discovered (2 occurrences)
        assertFalse(patterns.contains("alice"));
        assertTrue(patterns.contains("bob"));
        assertEquals(2, patterns.get("bob").getOccurrences());
    }

    @Test
    void testMultiplePropertyExtractors() throws IOException {
        Path jfrFile = tempDir.resolve("test-multiple.jfr");

        // Create JFR recording with events containing multiple properties
        try (Recording recording = new Recording()) {
            recording.start();

            SystemEvent event1 = new SystemEvent();
            event1.user = "testuser";
            event1.host = "testhost";
            event1.pid = 12345;
            event1.commit();

            SystemEvent event2 = new SystemEvent();
            event2.user = "anotheruser";
            event2.host = "anotherhost";
            event2.pid = 67890;
            event2.commit();

            recording.stop();
            recording.dump(jfrFile);
        }

        // Setup discovery config with multiple property extractors
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();

        // Username extractor
        DiscoveryConfig.PropertyExtractionConfig userExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        userExtraction.setName("user");
        userExtraction.setKeyPattern("user");
        userExtraction.setType("USERNAME");
        userExtraction.setMinOccurrences(1);
        userExtraction.setEnabled(true);

        // Hostname extractor
        DiscoveryConfig.PropertyExtractionConfig hostExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        hostExtraction.setName("host");
        hostExtraction.setKeyPattern("host");
        hostExtraction.setType("HOSTNAME");
        hostExtraction.setMinOccurrences(1);
        hostExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(userExtraction);
        discoveryConfig.getPropertyExtractions().add(hostExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Process the JFR file
        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                engine.analyzeEvent(event);
            }
        }

        // Get discovered patterns
        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Verify both users and hosts were discovered with correct types
        assertTrue(patterns.contains("testuser"));
        assertTrue(patterns.contains("anotheruser"));
        assertTrue(patterns.contains("testhost"));
        assertTrue(patterns.contains("anotherhost"));

        assertEquals(DiscoveredPatterns.PatternType.USERNAME, patterns.get("testuser").getType());
        assertEquals(DiscoveredPatterns.PatternType.HOSTNAME, patterns.get("testhost").getType());
    }

    @Test
    void testPropertyExtractionCaseInsensitive() throws IOException {
        Path jfrFile = tempDir.resolve("test-case.jfr");

        // Create JFR recording
        try (Recording recording = new Recording()) {
            recording.start();

            HostEvent event1 = new HostEvent();
            event1.hostname = "Server";
            event1.commit();

            HostEvent event2 = new HostEvent();
            event2.hostname = "server";  // Different case
            event2.commit();

            HostEvent event3 = new HostEvent();
            event3.hostname = "SERVER";  // Different case
            event3.commit();

            recording.stop();
            recording.dump(jfrFile);
        }

        // Setup discovery config with case_sensitive = false
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("hostname");
        propExtraction.setKeyPattern("hostname");
        propExtraction.setType("HOSTNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(1);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Process the JFR file
        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                engine.analyzeEvent(event);
            }
        }

        // Get discovered patterns
        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // All should be treated as the same value (case-insensitive)
        assertTrue(patterns.contains("server"));
        assertEquals(3, patterns.get("server").getOccurrences());
    }

    @Test
    void testKeyValuePairExtraction() throws IOException {
        Path jfrFile = tempDir.resolve("test-keyvalue.jfr");

        // Create JFR recording with key-value events
        try (Recording recording = new Recording()) {
            recording.start();

            KeyValueEvent event1 = new KeyValueEvent();
            event1.key = "user.name";
            event1.value = "i560383";
            event1.commit();

            KeyValueEvent event2 = new KeyValueEvent();
            event2.key = "user.name";
            event2.value = "i560383";  // Same value
            event2.commit();

            KeyValueEvent event3 = new KeyValueEvent();
            event3.key = "user.name";
            event3.value = "john.doe";
            event3.commit();

            KeyValueEvent event4 = new KeyValueEvent();
            event4.key = "hostname";  // Different key, should not match
            event4.value = "server01";
            event4.commit();

            recording.stop();
            recording.dump(jfrFile);
        }

        // Setup discovery config to extract from key-value pairs where key matches user.name pattern
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("user_name_kv");
        propExtraction.setKeyPattern("user\\.name");  // Match "user.name" key
        propExtraction.setType("USERNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(1);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Process the JFR file
        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                engine.analyzeEvent(event);
            }
        }

        // Get discovered patterns
        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Debug output
        System.out.println("Key-Value Test - Discovery statistics:");
        System.out.println(engine.getStatistics());
        System.out.println("Total patterns found: " + patterns.getTotalCount());
        System.out.println("All discovered values: " + patterns.getAllValues());

        // Verify usernames were discovered from key-value pairs
        assertTrue(patterns.contains("i560383"), "Should contain i560383 from key-value pair");
        assertTrue(patterns.contains("john.doe"), "Should contain john.doe from key-value pair");
        assertFalse(patterns.contains("server01"), "Should NOT contain server01 (different key)");

        assertEquals(2, patterns.get("i560383").getOccurrences());
        assertEquals(1, patterns.get("john.doe").getOccurrences());
        assertEquals(DiscoveredPatterns.PatternType.USERNAME, patterns.get("i560383").getType());
    }
}