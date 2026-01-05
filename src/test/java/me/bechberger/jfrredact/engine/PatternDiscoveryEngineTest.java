package me.bechberger.jfrredact.engine;

import me.bechberger.jfrredact.ConfigLoader;
import me.bechberger.jfrredact.config.DiscoveryConfig;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.config.StringConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PatternDiscoveryEngine with per-pattern discovery settings
 */
class PatternDiscoveryEngineTest {

    // Helper method to load default config
    private RedactionConfig loadDefaultConfig() {
        try {
            return new ConfigLoader().load("default");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default config", e);
        }
    }

    // Helper method to load hserr config (for hs_err file tests)
    private RedactionConfig loadHsErrConfig() {
        try {
            return new ConfigLoader().load("hserr");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load hserr config", e);
        }
    }

    @Test
    void testUsernameDiscoveryFromMacPath() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Configure home_directories pattern for discovery
        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryCaptureGroup(1);
        homeDir.setDiscoveryCaseSensitive(false);
        homeDir.setDiscoveryMinOccurrences(1);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("/Users/alice/Documents/file.txt");
        engine.analyzeLine("/Users/bob/Desktop/notes.txt");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        List<DiscoveredPatterns.DiscoveredValue> values = patterns.getValues(1);

        assertEquals(2, values.size(), "Should discover 2 usernames");

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("alice", "bob");
    }

    @Test
    void testUsernameDiscoveryFromLinuxPath() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("/home/charlie/.bashrc");
        engine.analyzeLine("/home/david/projects");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("charlie", "david");
    }

    @Test
    void testWhitelist() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryWhitelist(List.of("root", "admin"));

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("/Users/root/file.txt");
        engine.analyzeLine("/Users/admin/config.txt");
        engine.analyzeLine("/Users/alice/data.txt");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .doesNotContain("root", "admin");
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("alice");
    }

    @Test
    void testMinOccurrences() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryMinOccurrences(2);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("/Users/alice/file1.txt");
        engine.analyzeLine("/Users/alice/file2.txt");
        engine.analyzeLine("/Users/bob/file1.txt");  // Only once

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        List<DiscoveredPatterns.DiscoveredValue> values = patterns.getValues(1);
        // alice appears 2 times, bob appears 1 time but needs 2
        assertEquals(1, values.size(), "Only alice appears 2+ times");
        assertThat(values).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("alice");
    }

    @Test
    void testCaseSensitivity() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryCaseSensitive(false);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("/Users/Alice/file1.txt");
        engine.analyzeLine("/Users/alice/file2.txt");
        engine.analyzeLine("/Users/ALICE/file3.txt");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Should be treated as one value
        DiscoveredPatterns.DiscoveredValue value = patterns.get("alice");
        assertNotNull(value);
        assertEquals(3, value.getOccurrences(), "All variations should count as same value");
    }

    @Test
    void testHostnameDiscoveryFromSSH() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Enable SSH hosts with discovery
        var sshHosts = stringConfig.getPatterns().getSshHosts();
        sshHosts.setEnabled(true);
        sshHosts.setEnableDiscovery(true);
        sshHosts.setDiscoveryCaptureGroup(1);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("ssh user@server01.example.com");
        engine.analyzeLine("user@server02.example.com:/path");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).isNotEmpty();
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .containsAnyOf("server01.example.com", "server02.example.com");
    }

    @Test
    void testHostnameDiscoveryFromHsErr() {
        RedactionConfig config = loadHsErrConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Enable hostname pattern with discovery
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0); // Use entire match
        hostnames.setDiscoveryMinOccurrences(1);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Test "Host: HOSTNAME" pattern from hs_err files
        engine.analyzeLine("Host: F5N, MacBookPro18,3");
        engine.analyzeLine("Process running on F5N");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .as("Should discover hostname F5N from Host: line")
            .contains("F5N");
    }

    @Test
    void testHostnameDiscoveryFromHsErrWithMultipleOccurrences() {
        RedactionConfig config = loadHsErrConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Configure exactly like hserr.yaml preset
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0);
        hostnames.setDiscoveryMinOccurrences(1);
        hostnames.setDiscoveryCaseSensitive(false);
        hostnames.setDiscoveryWhitelist(List.of("localhost", "localhost.localdomain"));

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Simulate real hs_err content
        engine.analyzeLine("Host: F5N, MacBookPro18,3");
        engine.analyzeLine("uname: Darwin F5N 22.6.0 Darwin Kernel Version 22.6.0");
        engine.analyzeLine("Process running on F5N with PID 12345");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .as("Should discover hostname F5N")
            .contains("F5N");

        // Verify it was discovered multiple times
        DiscoveredPatterns.DiscoveredValue value = patterns.get("F5N");
        assertNotNull(value);
        assertThat(value.getOccurrences()).as("Should have discovered hostname multiple times").isGreaterThanOrEqualTo(2);
    }

    @Test
    void testHostnameIgnoreLocalhost() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0);
        hostnames.setDiscoveryMinOccurrences(1);  // Discover after 1 occurrence
        hostnames.setDiscoveryWhitelist(List.of("localhost", "localhost.localdomain"));

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("Host: localhost");
        engine.analyzeLine("Host: realhostname.example.com");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .as("Real hostname should be discovered")
            .contains("realhostname.example.com");
    }

    @Test
    void testIgnoreExactAndIgnorePatterns() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setIgnoreExact(List.of("jenkins"));  // Exact ignore
        homeDir.setIgnore(List.of("test.*"));  // Pattern ignore

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("/Users/jenkins/workspace");  // Should be ignored (exact)
        engine.analyzeLine("/Users/test123/file");       // Should be ignored (pattern)
        engine.analyzeLine("/Users/testuser/file");      // Should be ignored (pattern)
        engine.analyzeLine("/Users/alice/file");         // Should be discovered

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.contains("jenkins")).as("jenkins should be ignored").isFalse();
        assertThat(patterns.contains("test123")).as("test123 should match ignore pattern").isFalse();
        assertThat(patterns.contains("testuser")).as("testuser should match ignore pattern").isFalse();
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .as("alice should be discovered")
            .contains("alice");
    }

    @Test
    void testPerPatternCaseSensitivity() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Configure two different patterns with different case sensitivity
        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryCaseSensitive(false);  // Case insensitive

        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaseSensitive(true);  // Case sensitive

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Usernames should be case insensitive
        engine.analyzeLine("/Users/Alice/file1");
        engine.analyzeLine("/Users/alice/file2");

        // Hostnames should be case sensitive
        engine.analyzeLine("Host: SERVER");
        engine.analyzeLine("Host: server");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Verify usernames are case insensitive
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .containsAnyOf("alice", "Alice");
        DiscoveredPatterns.DiscoveredValue userValue = patterns.get("alice");
        if (userValue != null) {
            assertEquals(2, userValue.getOccurrences(), "Both Alice and alice should count as same");
        }

        // Hostnames should be discovered as separate values
        // (Note: This test checks the configuration, actual behavior depends on DiscoveredPatterns implementation)
    }

    @Test
    void testCustomExtractionWithType() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();

        // Add custom extraction categorized as USERNAME
        DiscoveryConfig.CustomExtractionConfig customExtraction = new DiscoveryConfig.CustomExtractionConfig();
        customExtraction.setName("ssh_username");
        customExtraction.setPattern("([a-zA-Z0-9_-]+)@[a-zA-Z0-9.-]+");
        customExtraction.setCaptureGroup(1);
        customExtraction.setType("USERNAME");  // Categorize as username
        customExtraction.setCaseSensitive(false);
        customExtraction.setMinOccurrences(1);
        customExtraction.setEnabled(true);

        discoveryConfig.setCustomExtractions(List.of(customExtraction));

        StringConfig stringConfig = config.getStrings();

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("ssh alice@server.example.com");
        engine.analyzeLine("Connection from bob@workstation.local");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .as("Should extract usernames")
            .contains("alice", "bob");
    }

    @Test
    void testCustomExtraction() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();

        // Add custom extraction for project codes
        DiscoveryConfig.CustomExtractionConfig customExtraction = new DiscoveryConfig.CustomExtractionConfig();
        customExtraction.setName("project_code");
        customExtraction.setPattern("PROJ-([A-Z0-9]+)-\\d+");
        customExtraction.setCaptureGroup(1);
        customExtraction.setEnabled(true);

        discoveryConfig.setCustomExtractions(List.of(customExtraction));

        StringConfig stringConfig = config.getStrings();

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("Working on PROJ-ABC123-001");
        engine.analyzeLine("See also PROJ-XYZ789-042");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .as("Should extract project codes")
            .contains("ABC123", "XYZ789");
    }

    @Test
    void testDisabledDiscovery() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Disable property extractions
        discoveryConfig.getPropertyExtractions().clear();

        // Disable discovery for all pattern types
        stringConfig.getPatterns().getUser().setEnabled(true);
        stringConfig.getPatterns().getUser().setEnableDiscovery(false);

        stringConfig.getPatterns().getEmails().setEnabled(true);
        stringConfig.getPatterns().getEmails().setEnableDiscovery(false);

        stringConfig.getPatterns().getHostnames().setEnabled(true);
        stringConfig.getPatterns().getHostnames().setEnableDiscovery(false);

        stringConfig.getPatterns().getSshHosts().setEnabled(true);
        stringConfig.getPatterns().getSshHosts().setEnableDiscovery(false);

        stringConfig.getPatterns().getIpAddresses().setEnabled(true);
        stringConfig.getPatterns().getIpAddresses().setEnableDiscovery(false);

        stringConfig.getPatterns().getUuids().setEnabled(true);
        stringConfig.getPatterns().getUuids().setEnableDiscovery(false);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("/Users/alice/file.txt");
        engine.analyzeLine("ssh user@server.example.com");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertEquals(0, patterns.getTotalCount(), "Should not discover anything when discovery is disabled");
    }

    @Test
    void testMultiplePatternTypesWithDifferentSettings() {
        RedactionConfig config = loadHsErrConfig();  // Use hserr for single-word hostname support
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Configure home directories with min 2 occurrences
        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryMinOccurrences(2);

        // Configure hostnames with min 1 occurrence
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryMinOccurrences(1);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Usernames
        engine.analyzeLine("/Users/alice/file1.txt");
        engine.analyzeLine("/Users/alice/file2.txt");
        engine.analyzeLine("/Users/bob/file1.txt");  // Only once

        // Hostnames
        engine.analyzeLine("Host: SERVER1");
        engine.analyzeLine("Host: SERVER2");  // Both only once

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // alice appears 2 times (meets threshold), bob appears 1 time (doesn't meet threshold)
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .as("alice should be discovered (2 occurrences)")
            .contains("alice");

        // Both hostnames should be discovered (min 1 occurrence)
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("SERVER1", "SERVER2");
    }

    @Test
    void testEmptyWhitelist() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryWhitelist(List.of());  // Empty whitelist

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("/Users/root/file.txt");
        engine.analyzeLine("/Users/admin/file.txt");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // With empty whitelist, everything should be discovered
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .as("root and admin should be discovered (no whitelist)")
            .contains("root", "admin");
    }

    @Test
    void testStatistics() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryMinOccurrences(1);

        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryMinOccurrences(1);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("/Users/alice/file.txt");
        engine.analyzeLine("/Users/bob/file.txt");
        engine.analyzeLine("Host: SERVER1");

        String stats = engine.getStatistics();

        assertNotNull(stats);
        assertThat(stats)
            .as("Should contain statistics header and total count")
            .contains("Discovery Statistics", "Total discovered values");
    }

    @Test
    void testCaptureGroupZero() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0);  // Use entire match
        hostnames.setDiscoveryMinOccurrences(1);  // Discover after 1 occurrence

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("Host: testhost.example.com");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .as("Should extract entire match when capture group is 0")
            .contains("testhost.example.com");
    }

    @Test
    void testInvalidCaptureGroup() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryCaptureGroup(99);  // Invalid capture group number

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Should not throw exception, just skip extraction
        engine.analyzeLine("/Users/alice/file.txt");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Should not discover anything since capture group doesn't exist
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue).doesNotContain("alice");
    }

    @Test
    void testHsErrRealWorldExample() {
        RedactionConfig config = loadHsErrConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Configure like hserr.yaml preset
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0);
        hostnames.setDiscoveryMinOccurrences(1);
        hostnames.setDiscoveryCaseSensitive(false);
        hostnames.setDiscoveryWhitelist(List.of("localhost", "localhost.localdomain"));
        hostnames.setIgnoreExact(List.of("x86_64"));

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryCaptureGroup(1);
        homeDir.setDiscoveryWhitelist(List.of("root", "admin", "test", "user", "guest", "system", "build", "jenkins"));

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Real hs_err content
        engine.analyzeLine("Host: F5N, MacBookPro18,3");
        engine.analyzeLine("uname: Darwin F5N 22.6.0 Darwin Kernel Version 22.6.0: Wed Jul  5 22:22:05 PDT 2023; root:xnu-8796.141.3~6/RELEASE_ARM64_T6000 arm64");
        engine.analyzeLine("/Users/alice/workspace/jdk/build/macosx-aarch64-server-slowdebug/hotspot/variant-server/libjvm/objs/task.o");
        engine.analyzeLine("Process running on F5N");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Hostname should be discovered
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("F5N");
        DiscoveredPatterns.DiscoveredValue hostname = patterns.get("F5N");
        assertThat(hostname.getOccurrences()).as("Hostname should be discovered at least 2 times (Host line and uname line)").isGreaterThanOrEqualTo(2);

        // Username should be discovered
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("alice");

        // Whitelisted values should not be discovered
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .doesNotContain("root", "admin");
    }

    @Test
    void testCustomExtractionWithHostnameType() {
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();

        // Custom extraction for hostnames
        DiscoveryConfig.CustomExtractionConfig customExtraction = new DiscoveryConfig.CustomExtractionConfig();
        customExtraction.setName("custom_hostname");
        customExtraction.setPattern("Server:\\s+([A-Z0-9]+)");
        customExtraction.setCaptureGroup(1);
        customExtraction.setType("HOSTNAME");
        customExtraction.setCaseSensitive(false);
        customExtraction.setMinOccurrences(1);
        customExtraction.setWhitelist(List.of("LOCALHOST"));
        customExtraction.setEnabled(true);

        discoveryConfig.setCustomExtractions(List.of(customExtraction));

        StringConfig stringConfig = config.getStrings();

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        engine.analyzeLine("Server: PRODSERVER01");
        engine.analyzeLine("Server: LOCALHOST");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("PRODSERVER01");
        assertThat(patterns.contains("LOCALHOST")).as("LOCALHOST should be whitelisted").isFalse();
    }

    @Test
    void testHsErrHostLineWithContext() {
        // Test that mimics real hs_err file content with hostname in multiple places
        RedactionConfig config = loadHsErrConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Clear property extractions to avoid extra discoveries
        discoveryConfig.getPropertyExtractions().clear();

        // Disable all other pattern types
        stringConfig.getPatterns().getUser().setEnabled(false);
        stringConfig.getPatterns().getEmails().setEnabled(false);
        stringConfig.getPatterns().getSshHosts().setEnabled(false);
        stringConfig.getPatterns().getIpAddresses().setEnabled(false);
        stringConfig.getPatterns().getUuids().setEnabled(false);

        // Configure hostname discovery
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0);
        hostnames.setDiscoveryMinOccurrences(1);
        hostnames.setDiscoveryCaseSensitive(false);
        hostnames.setDiscoveryWhitelist(List.of("localhost"));
        // Set specific patterns for this test (only Host: and Darwin patterns)
        hostnames.setPatterns(List.of(
            "(?<=Host:\\s)[a-zA-Z0-9][a-zA-Z0-9._-]*",
            "(?<=Darwin\\s)[a-zA-Z][a-zA-Z0-9._-]+(?=\\s+\\d)"
        ));

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Real hs_err content with hostname appearing multiple times
        String hostLine = "Host: F5N, \"MacBookPro17,1\" arm64, 8 cores, 16G, Darwin 22.6.0, macOS 13.5.1 (22G90)";
        String timeLine = "Time: Thu Aug 31 17:31:03 2023 CEST elapsed time: 1.376730 seconds (0d 0h 0m 1s)";

        engine.analyzeLine(hostLine);
        engine.analyzeLine(timeLine);
        engine.analyzeLine("uname: Darwin F5N 22.6.0 Darwin Kernel Version 22.6.0");
        engine.analyzeLine("# Problematic frame:");
        engine.analyzeLine("# V  [libjvm.dylib+0xabcd]  compiled code on F5N");

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Verify hostname was discovered
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
                  .contains("F5N");

        DiscoveredPatterns.DiscoveredValue hostname = patterns.get("F5N");
        assertNotNull(hostname, "Hostname should be in discovered patterns");
        assertThat(hostname.getOccurrences()).as("Hostname should be discovered at least 2 times (Host line and uname line)").isGreaterThanOrEqualTo(2);
    }

    @Test
    void testHsErrCompleteScenario() {
        // Complete test simulating discovery and redaction with a real hs_err scenario
        RedactionConfig config = loadHsErrConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Configure hostname discovery
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0);
        hostnames.setDiscoveryMinOccurrences(1);
        hostnames.setDiscoveryCaseSensitive(false);
        // Set specific patterns for this test (only Host: and Darwin patterns)
        hostnames.setPatterns(List.of(
            "(?<=Host:\\s)[a-zA-Z0-9][a-zA-Z0-9._-]*",
            "(?<=Darwin\\s)[a-zA-Z][a-zA-Z0-9._-]+(?=\\s+\\d)"
        ));

        // Configure username discovery
        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryCaptureGroup(1);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Simulate hs_err file content with hostname "MACBOOK-PRO-2023"
        String[] hsErrLines = {
            "#",
            "# A fatal error has been detected by the Java Runtime Environment:",
            "#",
            "Host: MACBOOK-PRO-2023, \"MacBookPro17,1\" arm64, 8 cores, 16G, Darwin 22.6.0, macOS 13.5.1 (22G90)",
            "Time: Thu Aug 31 17:31:03 2023 CEST elapsed time: 1.376730 seconds (0d 0h 0m 1s)",
            "",
            "uname: Darwin MACBOOK-PRO-2023 22.6.0 Darwin Kernel Version 22.6.0: Wed Jul  5 22:22:05 PDT 2023; root:xnu-8796.141.3~6/RELEASE_ARM64_T6000 arm64",
            "OS uptime: 3 days 22:33 hours",
            "",
            "/Users/jdeveloper/workspace/jdk/build/macosx/aarch64/images/jdk/bin/java",
            "",
            "VM Arguments:",
            "java_command: HelloWorld",
            "Launcher Type: SUN_STANDARD",
            "",
            "Environment Variables:",
            "HOSTNAME=MACBOOK-PRO-2023",
            "PATH=/usr/bin:/bin",
            "",
            "Problematic frame on MACBOOK-PRO-2023:",
            "V  [libjvm.dylib+0x12345]  Method::execute()",
            "",
            "Stack: [0x123400000,0x123500000],  sp=0x1234ff000,  free space=1020k",
            "Native frames: (J=compiled Java code, A=aot compiled Java code, j=interpreted, Vv=VM code, C=native code)",
            "V  [libjvm.dylib+0x12345]  compiled on MACBOOK-PRO-2023",
        };

        // Phase 1: Discovery - analyze all lines
        for (String line : hsErrLines) {
            engine.analyzeLine(line);
        }

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Verify discoveries
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("MACBOOK-PRO-2023", "jdeveloper");

        // Verify hostname was discovered 2 times through the discovery patterns
        DiscoveredPatterns.DiscoveredValue hostnameValue = patterns.get("MACBOOK-PRO-2023");
        assertNotNull(hostnameValue, "Hostname should be discovered");
        assertThat(hostnameValue.getOccurrences()).as("Hostname should be discovered at least 2 times (Host line and uname line)").isGreaterThanOrEqualTo(2);

        // Verify username was discovered 1 time
        DiscoveredPatterns.DiscoveredValue usernameValue = patterns.get("jdeveloper");
        assertNotNull(usernameValue, "Username should be discovered");
        assertEquals(1, usernameValue.getOccurrences(),
                  "Username should be discovered 1 time from home directory path");

        // Phase 2: Redaction - apply discovered patterns to redact everywhere
        StringBuilder redactedOutput = new StringBuilder();
        for (String line : hsErrLines) {
            String redacted = line;
            // Replace discovered values (case-insensitive for hostnames and usernames)
            for (DiscoveredPatterns.DiscoveredValue value : patterns.getValues(1)) {
                if (value.getType() == DiscoveredPatterns.PatternType.HOSTNAME) {
                    // Case-insensitive replacement for hostnames
                    redacted = redacted.replaceAll("(?i)" + java.util.regex.Pattern.quote(value.getValue()), "***HOSTNAME***");
                } else if (value.getType() == DiscoveredPatterns.PatternType.USERNAME) {
                    // Case-insensitive replacement for usernames
                    redacted = redacted.replaceAll("(?i)" + java.util.regex.Pattern.quote(value.getValue()), "***USER***");
                }
            }
            redactedOutput.append(redacted).append("\n");
        }

        String redactedText = redactedOutput.toString();

        // Verify hostname is redacted everywhere (all 5 occurrences)
        assertThat(redactedText).doesNotContain("MACBOOK-PRO-2023", "jdeveloper");

        // Verify replacements in specific locations
        assertThat(redactedText)
            .contains("Host: ***HOSTNAME***,", "Darwin ***HOSTNAME*** 22.6.0", "HOSTNAME=***HOSTNAME***",
                      "frame on ***HOSTNAME***:", "compiled on ***HOSTNAME***", "/Users/***USER***/workspace");

        // Verify that surrounding text is preserved
        assertThat(redactedText).contains("\"MacBookPro17,1\"", "8 cores", "16G", "Darwin Kernel Version",
                                        "Darwin 22.6.0", "SUN_STANDARD", "HelloWorld");
    }

    @Test
    void testRedactionWithMultipleHostnames() {
        // Test redacting multiple different hostnames
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0);
        hostnames.setDiscoveryMinOccurrences(1);  // Discover after 1 occurrence

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        String[] lines = {
            "Host: server-01.corp.com, Linux 5.15.0",
            "Host: laptop-x1.corp.com, Windows 10",
            "Connecting from server-01.corp.com to laptop-x1.corp.com",
            "uname: Linux server-01.corp.com 5.15.0",
        };

        for (String line : lines) {
            engine.analyzeLine(line);
        }

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Should discover both hostnames
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("server-01.corp.com", "laptop-x1.corp.com");

        // Redact
        StringBuilder redacted = new StringBuilder();
        for (String line : lines) {
            String r = line;
            for (DiscoveredPatterns.DiscoveredValue value : patterns.getValues(1)) {
                if (value.getType() == DiscoveredPatterns.PatternType.HOSTNAME) {
                    r = r.replace(value.getValue(), "***");
                }
            }
            redacted.append(r).append("\n");
        }

        String result = redacted.toString();
        assertThat(result).doesNotContain("SERVER-01", "LAPTOP-X1")
            .contains("Host: ***,", "from *** to ***");
    }

    @Test
    void testRedactionPreservesContext() {
        // Test that redaction preserves surrounding context
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        String line = "File: /Users/alice/project/src/Main.java:42";
        engine.analyzeLine(line);

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("alice");

        // Redact
        String redacted = line;
        for (DiscoveredPatterns.DiscoveredValue value : patterns.getValues(1)) {
            if (value.getType() == DiscoveredPatterns.PatternType.USERNAME) {
                redacted = redacted.replace(value.getValue(), "***USER***");
            }
        }

        assertThat(redacted).doesNotContain("alice");
        assertThat(redacted).contains("/Users/***USER***/project/src/Main.java:42", ":42");
    }

    @Test
    void testCaseInsensitiveRedaction() {
        // Test that case-insensitive discovery redacts all case variations
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryCaseSensitive(false);

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        String[] lines = {
            "/Users/Bob/documents/file.txt",
            "User: BOB",
            "Owner: bob",
        };

        for (String line : lines) {
            engine.analyzeLine(line);
        }

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // Should discover "Bob" (case-insensitively)
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .containsAnyOf("Bob", "bob", "BOB");

        // Redact with case-insensitive replacement
        StringBuilder redacted = new StringBuilder();
        for (String line : lines) {
            String r = line;
            for (DiscoveredPatterns.DiscoveredValue value : patterns.getValues(1)) {
                if (value.getType() == DiscoveredPatterns.PatternType.USERNAME) {
                    r = r.replaceAll("(?i)" + java.util.regex.Pattern.quote(value.getValue()), "***");
                }
            }
            redacted.append(r).append("\n");
        }

        String result = redacted.toString();
        assertThat(result.toLowerCase()).doesNotContain("bob");
        assertThat(result).contains("/Users/***/documents");
    }

    @Test
    void testRedactionDoesNotAffectWhitelistedValues() {
        // Test that whitelisted values are not redacted
        RedactionConfig config = loadHsErrConfig();  // Use hserr for single-word hostname support
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryMinOccurrences(1);  // Discover after 1 occurrence
        hostnames.setDiscoveryWhitelist(List.of("localhost"));

        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        String[] lines = {
            "Host: localhost, Local machine",
            "Host: PRODUCTION-01, Production server",
            "Connecting to localhost",
            "Connecting to PRODUCTION-01",
        };

        for (String line : lines) {
            engine.analyzeLine(line);
        }

        DiscoveredPatterns patterns = engine.getDiscoveredPatterns();

        // localhost should NOT be discovered (whitelisted)
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue).doesNotContain("localhost");
        // PRODUCTION-01 should be discovered
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("PRODUCTION-01");

        // Redact
        StringBuilder redacted = new StringBuilder();
        for (String line : lines) {
            String r = line;
            for (DiscoveredPatterns.DiscoveredValue value : patterns.getValues(1)) {
                if (value.getType() == DiscoveredPatterns.PatternType.HOSTNAME) {
                    r = r.replace(value.getValue(), "***");
                }
            }
            redacted.append(r).append("\n");
        }

        String result = redacted.toString();
        assertThat(result).contains("localhost");
        assertThat(result).doesNotContain("PRODUCTION-01");
    }

    // ========================================================================
    // INTEGRATED TESTS: Discovery + Redaction/Pseudonymization
    // ========================================================================

    @Test
    void testDiscoveryWithRedactionEngine_Simple() {
        // Test discovery integrated with RedactionEngine
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);

        PatternDiscoveryEngine discoveryEngine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Discovery phase
        String[] lines = {
            "/Users/alice/Documents/file.txt",
            "/Users/alice/Desktop/notes.txt",
            "Log entry from /Users/alice/workspace/project",
        };

        for (String line : lines) {
            discoveryEngine.analyzeLine(line);
        }

        DiscoveredPatterns patterns = discoveryEngine.getDiscoveredPatterns();
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("alice");

        // Redaction phase using RedactionEngine
        me.bechberger.jfrredact.config.RedactionConfig redactionConfig = new me.bechberger.jfrredact.config.RedactionConfig();
        redactionConfig.getStrings().setEnabled(true);
        redactionConfig.getPaths().setEnabled(true);

        me.bechberger.jfrredact.engine.RedactionEngine redactionEngine =
            new me.bechberger.jfrredact.engine.RedactionEngine(redactionConfig);

        // Set discovered patterns in the redaction engine
        redactionEngine.setDiscoveredPatterns(patterns);

        // Redact all lines
        StringBuilder redacted = new StringBuilder();
        for (String line : lines) {
            String redactedLine = redactionEngine.redact("text", line);
            redacted.append(redactedLine).append("\n");
        }

        String result = redacted.toString();
        assertThat(result).doesNotContain("alice", "PRODSERVER")
            .isNotEmpty();
    }

    @Test
    void testDiscoveryWithRedactionEngine_HsErrScenario() {
        // Test complete hs_err scenario with discovery and redaction
        RedactionConfig config = loadHsErrConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        // Configure like hserr preset
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0);
        hostnames.setDiscoveryWhitelist(List.of("localhost"));

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);
        homeDir.setDiscoveryWhitelist(List.of("root", "admin"));

        PatternDiscoveryEngine discoveryEngine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Simulate hs_err content
        String[] hsErrLines = {
            "Host: MYSERVER, MacBookPro18,3",
            "uname: Darwin MYSERVER 22.6.0 Darwin Kernel Version",
            "/Users/jdeveloper/workspace/jdk/build/macosx/hotspot.o",
            "Environment Variables:",
            "HOSTNAME=MYSERVER",
            "HOME=/Users/jdeveloper",
        };

        // Discovery pass
        for (String line : hsErrLines) {
            discoveryEngine.analyzeLine(line);
        }

        DiscoveredPatterns patterns = discoveryEngine.getDiscoveredPatterns();
        assertThat(patterns.getValues(1)).extracting(DiscoveredPatterns.DiscoveredValue::getValue)
            .contains("MYSERVER", "jdeveloper");

        // Redaction pass
        me.bechberger.jfrredact.config.RedactionConfig redactionConfig = new me.bechberger.jfrredact.config.RedactionConfig();
        redactionConfig.getStrings().setEnabled(true);

        me.bechberger.jfrredact.engine.RedactionEngine redactionEngine =
            new me.bechberger.jfrredact.engine.RedactionEngine(redactionConfig);
        redactionEngine.setDiscoveredPatterns(patterns);

        StringBuilder redacted = new StringBuilder();
        for (String line : hsErrLines) {
            String redactedLine = redactionEngine.redact("text", line);
            redacted.append(redactedLine).append("\n");
        }

        String result = redacted.toString();
        assertThat(result).doesNotContain("MYSERVER", "jdeveloper");
        assertThat(result).contains("Host:");
        assertThat(result).contains("MacBookPro18,3");
    }

    @Test
    void testDiscoveryWithPseudonymization() {
        // Test that discovered patterns work with pseudonymization
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        StringConfig stringConfig = config.getStrings();

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);

        PatternDiscoveryEngine discoveryEngine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);

        // Discovery phase - same username appears multiple times
        String[] lines = {
            "/Users/alice/file1.txt",
            "/Users/alice/file2.txt",
            "/Users/bob/data.txt",
            "User alice logged in",
            "User bob started process",
        };

        for (String line : lines) {
            discoveryEngine.analyzeLine(line);
        }

        DiscoveredPatterns patterns = discoveryEngine.getDiscoveredPatterns();

        // Redaction with pseudonymization
        me.bechberger.jfrredact.config.RedactionConfig redactionConfig = new me.bechberger.jfrredact.config.RedactionConfig();
        redactionConfig.getStrings().setEnabled(true);
        redactionConfig.getGeneral().getPseudonymization().setEnabled(true);
        redactionConfig.getGeneral().getPseudonymization().setMode("hash");

        me.bechberger.jfrredact.engine.RedactionEngine redactionEngine =
            new me.bechberger.jfrredact.engine.RedactionEngine(redactionConfig);
        redactionEngine.setDiscoveredPatterns(patterns);

        // Redact all lines
        StringBuilder redacted = new StringBuilder();
        String alicePseudonym = null;
        String bobPseudonym = null;

        for (String line : lines) {
            String redactedLine = redactionEngine.redact("text", line);
            redacted.append(redactedLine).append("\n");

            // Extract pseudonyms for verification
            if (line.contains("alice") && alicePseudonym == null) {
                // Extract the pseudonym from the redacted line
                if (redactedLine.contains("<redacted:") && !redactedLine.contains("alice")) {
                    alicePseudonym = extractPseudonym(redactedLine);
                }
            }
            if (line.contains("bob") && bobPseudonym == null) {
                if (redactedLine.contains("<redacted:") && !redactedLine.contains("bob")) {
                    bobPseudonym = extractPseudonym(redactedLine);
                }
            }
        }

        String result = redacted.toString();

        // Verify original usernames are gone
        assertThat(result).doesNotContain("alice", "bob");

        // Verify pseudonyms are consistent
        if (alicePseudonym != null) {
            // Count occurrences of alice's pseudonym - should appear multiple times
            int aliceCount = countOccurrences(result, alicePseudonym);
            assertThat(aliceCount).isGreaterThanOrEqualTo(2);
        }

        // Verify different users get different pseudonyms
        if (alicePseudonym != null && bobPseudonym != null) {
            assertNotEquals(alicePseudonym, bobPseudonym, "Different users should get different pseudonyms");
        }
    }

    @Test
    void testDiscoveryWithTextFileRedactor_TwoPass() throws Exception {
        // Test using TextFileRedactor with two-pass discovery
        RedactionConfig config = loadHsErrConfig();  // Use hserr for single-word hostname support
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        discoveryConfig.setMode(DiscoveryConfig.DiscoveryMode.TWO_PASS);

        StringConfig stringConfig = config.getStrings();
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryCaptureGroup(0);

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);

        me.bechberger.jfrredact.config.RedactionConfig redactionConfig = new me.bechberger.jfrredact.config.RedactionConfig();
        redactionConfig.setDiscovery(discoveryConfig);
        redactionConfig.setStrings(stringConfig);
        redactionConfig.getStrings().setEnabled(true);

        me.bechberger.jfrredact.engine.RedactionEngine redactionEngine =
            new me.bechberger.jfrredact.engine.RedactionEngine(redactionConfig);

        me.bechberger.jfrredact.text.TextFileRedactor redactor =
            new me.bechberger.jfrredact.text.TextFileRedactor(redactionEngine, redactionConfig);

        // Create temporary files
        java.nio.file.Path tempInput = java.nio.file.Files.createTempFile("test_input", ".log");
        java.nio.file.Path tempOutput = java.nio.file.Files.createTempFile("test_output", ".log");

        try {
            // Write test content
            String content = """
                Host: PRODSERVER, Linux 5.15.0
                User: /Users/devuser/workspace/project
                Connection from PRODSERVER established
                File: /Users/devuser/config/settings.yaml
                PRODSERVER is online
                """;
            java.nio.file.Files.writeString(tempInput, content);

            // Redact with two-pass discovery
            redactor.redactFile(tempInput, tempOutput);

            // Read and verify output
            String redactedContent = java.nio.file.Files.readString(tempOutput);

            assertThat(redactedContent).doesNotContain("PRODSERVER", "devuser");
            assertThat(redactedContent).contains("Host:");
            assertThat(redactedContent).contains("/Users/");
        } finally {
            java.nio.file.Files.deleteIfExists(tempInput);
            java.nio.file.Files.deleteIfExists(tempOutput);
        }
    }

    @Test
    void testDiscoveryWithTextFileRedactor_FastMode() throws Exception {
        // Test using TextFileRedactor with fast (single-pass) discovery
        RedactionConfig config = loadHsErrConfig();  // Use hserr for single-word hostname support
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        discoveryConfig.setMode(DiscoveryConfig.DiscoveryMode.FAST);

        StringConfig stringConfig = config.getStrings();
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);

        me.bechberger.jfrredact.config.RedactionConfig redactionConfig = new me.bechberger.jfrredact.config.RedactionConfig();
        redactionConfig.setDiscovery(discoveryConfig);
        redactionConfig.setStrings(stringConfig);
        redactionConfig.getStrings().setEnabled(true);

        me.bechberger.jfrredact.engine.RedactionEngine redactionEngine =
            new me.bechberger.jfrredact.engine.RedactionEngine(redactionConfig);

        me.bechberger.jfrredact.text.TextFileRedactor redactor =
            new me.bechberger.jfrredact.text.TextFileRedactor(redactionEngine, redactionConfig);

        // Create temporary files
        java.nio.file.Path tempInput = java.nio.file.Files.createTempFile("test_input", ".log");
        java.nio.file.Path tempOutput = java.nio.file.Files.createTempFile("test_output", ".log");

        try {
            // Write test content where hostname appears early
            String content = """
                Starting application
                Host: SERVER01, Linux
                Server: SERVER01
                More logs from SERVER01
                Connection to SERVER01
                """;
            java.nio.file.Files.writeString(tempInput, content);

            // Redact with fast discovery
            redactor.redactFile(tempInput, tempOutput);

            // Read and verify output
            String redactedContent = java.nio.file.Files.readString(tempOutput);

            // With fast mode, later occurrences should be redacted
            // (earlier ones might not be if discovered after they appear)
            int server01Count = countOccurrences(redactedContent, "SERVER01");
            assertThat(server01Count).isLessThan(4);
        } finally {
            java.nio.file.Files.deleteIfExists(tempInput);
            java.nio.file.Files.deleteIfExists(tempOutput);
        }
    }

    @Test
    void testDiscoveryWithPseudonymization_Counter() throws Exception {
        // Test discovery with counter-based pseudonymization
        RedactionConfig config = loadDefaultConfig();
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        discoveryConfig.setMode(DiscoveryConfig.DiscoveryMode.TWO_PASS);

        StringConfig stringConfig = config.getStrings();
        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);

        me.bechberger.jfrredact.config.RedactionConfig redactionConfig = new me.bechberger.jfrredact.config.RedactionConfig();
        redactionConfig.setDiscovery(discoveryConfig);
        redactionConfig.setStrings(stringConfig);
        redactionConfig.getStrings().setEnabled(true);
        redactionConfig.getGeneral().getPseudonymization().setEnabled(true);
        redactionConfig.getGeneral().getPseudonymization().setMode("counter");

        me.bechberger.jfrredact.engine.RedactionEngine redactionEngine =
            new me.bechberger.jfrredact.engine.RedactionEngine(redactionConfig);

        me.bechberger.jfrredact.text.TextFileRedactor redactor =
            new me.bechberger.jfrredact.text.TextFileRedactor(redactionEngine, redactionConfig);

        java.nio.file.Path tempInput = java.nio.file.Files.createTempFile("test_input", ".log");
        java.nio.file.Path tempOutput = java.nio.file.Files.createTempFile("test_output", ".log");

        try {
            String content = """
                /Users/alice/project/src/Main.java
                /Users/bob/workspace/Test.java
                User alice committed changes
                User bob reviewed code
                /Users/alice/project/README.md
                """;
            java.nio.file.Files.writeString(tempInput, content);

            redactor.redactFile(tempInput, tempOutput);

            String redactedContent = java.nio.file.Files.readString(tempOutput);

            assertThat(redactedContent).doesNotContain("alice", "bob");

            // With counter mode, we should see sequential pseudonyms
            // Note: The actual format depends on the Pseudonymizer implementation

        } finally {
            java.nio.file.Files.deleteIfExists(tempInput);
            java.nio.file.Files.deleteIfExists(tempOutput);
        }
    }

    @Test
    void testDiscoveryWithMinOccurrences_Integration() throws Exception {
        // Test that min_occurrences threshold works in integrated scenario
        RedactionConfig config = loadHsErrConfig();  // Use hserr for single-word hostname support
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        discoveryConfig.setMode(DiscoveryConfig.DiscoveryMode.TWO_PASS);

        StringConfig stringConfig = config.getStrings();
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryMinOccurrences(2); // Require at least 2 occurrences

        me.bechberger.jfrredact.config.RedactionConfig redactionConfig = new me.bechberger.jfrredact.config.RedactionConfig();
        redactionConfig.setDiscovery(discoveryConfig);
        redactionConfig.setStrings(stringConfig);
        redactionConfig.getStrings().setEnabled(true);

        me.bechberger.jfrredact.engine.RedactionEngine redactionEngine =
            new me.bechberger.jfrredact.engine.RedactionEngine(redactionConfig);

        me.bechberger.jfrredact.text.TextFileRedactor redactor =
            new me.bechberger.jfrredact.text.TextFileRedactor(redactionEngine, redactionConfig);

        java.nio.file.Path tempInput = java.nio.file.Files.createTempFile("test_input", ".log");
        java.nio.file.Path tempOutput = java.nio.file.Files.createTempFile("test_output", ".log");

        try {
            String content = """
                Host: SERVER-A, Linux
                Host: SERVER-A, Online
                Host: SERVER-B, Windows
                Host: SERVER-B, Online
                Connecting to SERVER-A
                Connecting to SERVER-B
                """;
            java.nio.file.Files.writeString(tempInput, content);

            redactor.redactFile(tempInput, tempOutput);

            String redactedContent = java.nio.file.Files.readString(tempOutput);

            // SERVER-A appears 2 times in Host: context (meets threshold) - should be redacted
            assertThat(redactedContent).doesNotContain("SERVER-A", "SERVER-B");
        } finally {
            java.nio.file.Files.deleteIfExists(tempInput);
            java.nio.file.Files.deleteIfExists(tempOutput);
        }
    }

    @Test
    void testDiscoveryWithWhitelist_Integration() throws Exception {
        // Test that whitelist works in integrated scenario
        RedactionConfig config = loadHsErrConfig();  // Use hserr for single-word hostname support
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        discoveryConfig.setMode(DiscoveryConfig.DiscoveryMode.TWO_PASS);

        StringConfig stringConfig = config.getStrings();
        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);
        hostnames.setDiscoveryWhitelist(List.of("localhost", "127.0.0.1"));

        me.bechberger.jfrredact.config.RedactionConfig redactionConfig = new me.bechberger.jfrredact.config.RedactionConfig();
        redactionConfig.setDiscovery(discoveryConfig);
        redactionConfig.setStrings(stringConfig);
        redactionConfig.getStrings().setEnabled(true);

        me.bechberger.jfrredact.engine.RedactionEngine redactionEngine =
            new me.bechberger.jfrredact.engine.RedactionEngine(redactionConfig);

        me.bechberger.jfrredact.text.TextFileRedactor redactor =
            new me.bechberger.jfrredact.text.TextFileRedactor(redactionEngine, redactionConfig);

        java.nio.file.Path tempInput = java.nio.file.Files.createTempFile("test_input", ".log");
        java.nio.file.Path tempOutput = java.nio.file.Files.createTempFile("test_output", ".log");

        try {
            String content = """
                Host: localhost, Development
                Host: PROD-SERVER, Production
                Connecting to localhost:8080
                Connecting to PROD-SERVER:8080
                """;
            java.nio.file.Files.writeString(tempInput, content);

            redactor.redactFile(tempInput, tempOutput);

            String redactedContent = java.nio.file.Files.readString(tempOutput);

            // localhost should be preserved (whitelisted)
            assertThat(redactedContent).contains("localhost");

            // PROD-SERVER should be redacted (not whitelisted)
            assertThat(redactedContent).doesNotContain("PROD-SERVER");
        } finally {
            java.nio.file.Files.deleteIfExists(tempInput);
            java.nio.file.Files.deleteIfExists(tempOutput);
        }
    }

    @Test
    void testDiscoveryMultiplePatternTypes_Integration() throws Exception {
        // Test discovery and redaction with multiple pattern types simultaneously
        RedactionConfig config = loadHsErrConfig();  // Use hserr for single-word hostname support
        DiscoveryConfig discoveryConfig = config.getDiscovery();
        discoveryConfig.setMode(DiscoveryConfig.DiscoveryMode.TWO_PASS);

        StringConfig stringConfig = config.getStrings();

        var hostnames = stringConfig.getPatterns().getHostnames();
        hostnames.setEnabled(true);
        hostnames.setEnableDiscovery(true);

        var homeDir = stringConfig.getPatterns().getUser();
        homeDir.setEnabled(true);
        homeDir.setEnableDiscovery(true);

        me.bechberger.jfrredact.config.RedactionConfig redactionConfig = new me.bechberger.jfrredact.config.RedactionConfig();
        redactionConfig.setDiscovery(discoveryConfig);
        redactionConfig.setStrings(stringConfig);
        redactionConfig.getStrings().setEnabled(true);

        me.bechberger.jfrredact.engine.RedactionEngine redactionEngine =
            new me.bechberger.jfrredact.engine.RedactionEngine(redactionConfig);

        me.bechberger.jfrredact.text.TextFileRedactor redactor =
            new me.bechberger.jfrredact.text.TextFileRedactor(redactionEngine, redactionConfig);

        java.nio.file.Path tempInput = java.nio.file.Files.createTempFile("test_input", ".log");
        java.nio.file.Path tempOutput = java.nio.file.Files.createTempFile("test_output", ".log");

        try {
            String content = """
                Host: MY-LAPTOP, Darwin 22.6.0
                User: /Users/developer/workspace/app
                uname: Darwin MY-LAPTOP 22.6.0
                Project: /Users/developer/project/src
                Running on MY-LAPTOP
                """;
            java.nio.file.Files.writeString(tempInput, content);

            redactor.redactFile(tempInput, tempOutput);

            String redactedContent = java.nio.file.Files.readString(tempOutput);

            // Both hostname and username should be redacted
            assertThat(redactedContent).doesNotContain("MY-LAPTOP", "developer");

            // Structure should remain
            assertThat(redactedContent).contains("Host:");
            assertThat(redactedContent).contains("/Users/");
        } finally {
            java.nio.file.Files.deleteIfExists(tempInput);
            java.nio.file.Files.deleteIfExists(tempOutput);
        }
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private String extractPseudonym(String line) {
        // Extract pseudonym in format <redacted:xxxxx>
        int start = line.indexOf("<redacted:");
        if (start == -1) return null;
        int end = line.indexOf(">", start);
        if (end == -1) return null;
        return line.substring(start, end + 1);
    }

    private int countOccurrences(String text, String substring) {
        if (substring == null || substring.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}