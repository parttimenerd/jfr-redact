package me.bechberger.jfrredact.config;

import me.bechberger.jfrredact.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StringConfig pattern parsing including SSH hosts.
 */
public class StringConfigTest {

    private RedactionConfig defaultConfig;

    @BeforeEach
    void setUp() throws IOException {
        // Load default preset for tests that need default patterns
        defaultConfig = new ConfigLoader().load("default");
    }

    @Test
    public void testDefaultStringConfig() {
        StringConfig config = defaultConfig.getStrings();

        assertTrue(config.isEnabled());
        assertFalse(config.isRedactInMethodNames());
        assertFalse(config.isRedactInClassNames());
        assertFalse(config.isRedactInThreadNames());

        // Verify default patterns
        assertNotNull(config.getPatterns());
        assertTrue(config.getPatterns().getUser().isEnabled());
        assertTrue(config.getPatterns().getEmails().isEnabled());
        assertTrue(config.getPatterns().getIpAddresses().isEnabled());
        assertFalse(config.getPatterns().getUuids().isEnabled());  // Disabled by default
        assertTrue(config.getPatterns().getSshHosts().isEnabled());  // Enabled in default preset
    }

    @Test
    public void testSshHostPatternsDefault() {
        StringConfig config = defaultConfig.getStrings();
        var sshConfig = config.getPatterns().getSshHosts();

        assertTrue(sshConfig.isEnabled());  // Enabled in default preset
        assertEquals(4, sshConfig.getPatterns().size());

        // Verify default patterns are present (patterns have capture groups for hostname extraction)
        assertTrue(sshConfig.getPatterns().stream().anyMatch(p -> p.contains("ssh://")));
    }

    @Test
    public void testHomeDirectoryPatternsDefault() {
        StringConfig config = defaultConfig.getStrings();
        var homeConfig = config.getPatterns().getUser();

        assertTrue(homeConfig.isEnabled());
        assertEquals(3, homeConfig.getPatterns().size());

        // Verify macOS, Windows, and Linux patterns
        assertTrue(homeConfig.getPatterns().stream().anyMatch(r -> r.contains("/Users/")));
        assertTrue(homeConfig.getPatterns().stream().anyMatch(r -> r.contains("C:")));
        assertTrue(homeConfig.getPatterns().stream().anyMatch(r -> r.contains("/home/")));
    }

    @Test
    public void testEmailPattern() {
        StringConfig config = defaultConfig.getStrings();
        var emailConfig = config.getPatterns().getEmails();

        assertTrue(emailConfig.isEnabled());
        assertFalse(emailConfig.getPatterns().isEmpty());
        assertTrue(emailConfig.getPatterns().get(0).contains("@"));
    }

    @Test
    public void testIpAddressPatterns() {
        StringConfig config = defaultConfig.getStrings();
        var ipConfig = config.getPatterns().getIpAddresses();

        assertTrue(ipConfig.isEnabled());
        assertFalse(ipConfig.getPatterns().isEmpty());
    }

    @Test
    public void testUuidPattern() {
        StringConfig config = defaultConfig.getStrings();
        var uuidConfig = config.getPatterns().getUuids();

        assertFalse(uuidConfig.isEnabled());  // Disabled by default
        assertFalse(uuidConfig.getPatterns().isEmpty());
        assertTrue(uuidConfig.getPatterns().get(0).contains("-"));
    }

    @Test
    public void testCustomPatterns() {
        StringConfig config = defaultConfig.getStrings();
        var customPatterns = config.getPatterns().getCustom();

        assertNotNull(customPatterns);
        assertTrue(customPatterns.isEmpty());  // Empty by default
    }

    @Test
    public void testAddCustomPattern() {
        StringConfig config = new StringConfig();

        StringConfig.CustomPatternConfig customPattern = new StringConfig.CustomPatternConfig();
        customPattern.setName("aws_keys");
        customPattern.setPatterns(List.of("AKIA[0-9A-Z]{16}"));

        config.getPatterns().getCustom().add(customPattern);

        assertEquals(1, config.getPatterns().getCustom().size());
        assertEquals("aws_keys", config.getPatterns().getCustom().getFirst().getName());
    }

    @Test
    public void testMergeWithParent() {
        StringConfig parent = new StringConfig();
        parent.getPatterns().getSshHosts().getPatterns().add("custom_ssh_pattern");

        StringConfig child = new StringConfig();
        child.getPatterns().getSshHosts().getPatterns().clear();
        child.getPatterns().getSshHosts().getPatterns().add("child_ssh_pattern");

        child.mergeWith(parent);

        // Should have both parent and child patterns
        assertTrue(child.getPatterns().getSshHosts().getPatterns().contains("custom_ssh_pattern"));
        assertTrue(child.getPatterns().getSshHosts().getPatterns().contains("child_ssh_pattern"));
    }

    @Test
    public void testLoadDefaultPresetWithStringPatterns() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        assertNotNull(config.getStrings());
        assertTrue(config.getStrings().isEnabled());

        // Verify string patterns are loaded
        assertNotNull(config.getStrings().getPatterns());
        assertNotNull(config.getStrings().getPatterns().getSshHosts());
    }

    @Test
    public void testCliCustomRegexPatterns() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        // Simulate CLI options with custom regex patterns
        RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
        cliOptions.getRedactionRegexes().add("\\b[A-Z]{3}-\\d{6}\\b");  // Ticket ID pattern
        cliOptions.getRedactionRegexes().add("AKIA[0-9A-Z]{16}");       // AWS access key pattern

        // Apply CLI options
        config.applyCliOptions(cliOptions);

        // Verify custom patterns were added
        var customPatterns = config.getStrings().getPatterns().getCustom();
        assertEquals(2, customPatterns.size());

        assertEquals("cli_pattern_0", customPatterns.get(0).getName());
        assertEquals("\\b[A-Z]{3}-\\d{6}\\b", customPatterns.get(0).getPatterns().get(0));

        assertEquals("cli_pattern_1", customPatterns.get(1).getName());
        assertEquals("AKIA[0-9A-Z]{16}", customPatterns.get(1).getPatterns().get(0));
    }

    @Test
    public void testCliCustomRegexPatternsWithExistingCustomPatterns() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        // Add a custom pattern via config
        StringConfig.CustomPatternConfig existingPattern = new StringConfig.CustomPatternConfig();
        existingPattern.setName("jwt_tokens");
        existingPattern.setPatterns(List.of("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+"));
        config.getStrings().getPatterns().getCustom().add(existingPattern);

        // Now add CLI patterns
        RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
        cliOptions.getRedactionRegexes().add("\\b[A-Z]{3}-\\d{6}\\b");

        config.applyCliOptions(cliOptions);

        // Verify both existing and CLI patterns are present
        var customPatterns = config.getStrings().getPatterns().getCustom();
        assertEquals(2, customPatterns.size());

        // Existing pattern still there
        assertEquals("jwt_tokens", customPatterns.get(0).getName());
        assertEquals("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+", customPatterns.get(0).getPatterns().get(0));

        // CLI pattern added with correct name (size was 1 when added)
        assertEquals("cli_pattern_1", customPatterns.get(1).getName());
        assertEquals("\\b[A-Z]{3}-\\d{6}\\b", customPatterns.get(1).getPatterns().get(0));
    }

    // ========== Ignore Feature Tests ==========

    @Nested
    class IgnoreExactTests {
        @Test
        public void testEmailIgnoreExact() {
            var config = new StringConfig.EmailsConfig();

            config.getIgnoreExact().add("noreply@example.com");
            config.getIgnoreExact().add("postmaster@localhost");

            assertEquals(2, config.getIgnoreExact().size());
            assertTrue(config.getIgnoreExact().contains("noreply@example.com"));
            assertTrue(config.getIgnoreExact().contains("postmaster@localhost"));
        }

        @Test
        public void testIpAddressIgnoreExact() {
            var config = new StringConfig.IpAddressesConfig();

            config.getIgnoreExact().add("127.0.0.1");
            config.getIgnoreExact().add("0.0.0.0");
            config.getIgnoreExact().add("::1");
            config.getIgnoreExact().add("::");

            assertEquals(4, config.getIgnoreExact().size());
            assertTrue(config.getIgnoreExact().contains("127.0.0.1"));
            assertTrue(config.getIgnoreExact().contains("::1"));
        }

        @Test
        public void testHostnameIgnoreExactDefaults() throws IOException {
            // Load from default preset since defaults are in YAML
            var defaultConfig = new ConfigLoader().load("default");
            var config = defaultConfig.getStrings().getPatterns().getHostnames();

            // Check default values from default.yaml - should be in ignore_exact for literal values
            var ignoreExact = config.getIgnoreExact();
            assertTrue(ignoreExact.contains("localhost"));
            assertTrue(ignoreExact.contains("localhost.localdomain"));
            assertTrue(ignoreExact.contains("127.0.0.1"));
        }

        @Test
        public void testHomeDirectoryIgnoreExact() {
            var config = new StringConfig.UserConfig();

            config.getIgnoreExact().add("/Users/Public");
            config.getIgnoreExact().add("C:\\Users\\Public");
            config.getIgnoreExact().add("/home/buildbot");

            assertEquals(3, config.getIgnoreExact().size());
        }

        @Test
        public void testCustomPatternIgnoreExact() {
            var config = new StringConfig.CustomPatternConfig();
            config.setName("api_keys");
            config.setPatterns(List.of("sk_[a-zA-Z0-9]{32}"));

            config.getIgnoreExact().add("sk_test_12345678901234567890123456789012");
            config.getIgnoreExact().add("sk_example_value_do_not_redact");

            assertEquals(2, config.getIgnoreExact().size());
        }
    }

    @Nested
    class IgnorePatternTests {
        @Test
        public void testEmailIgnorePattern() {
            var config = new StringConfig.EmailsConfig();

            // Ignore all emails ending with @example.com
            config.getIgnore().add(".*@example\\.com");
            // Ignore all no-reply addresses
            config.getIgnore().add("no-?reply@.*");

            assertEquals(2, config.getIgnore().size());
            assertTrue(config.getIgnore().contains(".*@example\\.com"));
        }

        @Test
        public void testIpAddressIgnorePattern() {
            var config = new StringConfig.IpAddressesConfig();

            // Ignore localhost range
            config.getIgnore().add("127\\.0\\.0\\..*");
            // Ignore link-local
            config.getIgnore().add("169\\.254\\..*");
            // Ignore private networks
            config.getIgnore().add("192\\.168\\..*");
            config.getIgnore().add("10\\..*");

            assertEquals(4, config.getIgnore().size());
        }

        @Test
        public void testHostnameIgnorePattern() {
            var config = new StringConfig.HostnamesConfig();

            // Ignore all .example.com domains
            config.getIgnore().add(".*\\.example\\.com");
            // Ignore all .test domains
            config.getIgnore().add(".*\\.test");

            assertEquals(2, config.getIgnore().size());
        }

        @Test
        public void testSshHostIgnorePattern() {
            var config = new StringConfig.SshHostsConfig();

            // Ignore known public Git hosts
            config.getIgnore().add("git@github\\.com.*");
            config.getIgnore().add("git@gitlab\\.com.*");
            config.getIgnore().add(".*@bitbucket\\.org.*");

            assertEquals(3, config.getIgnore().size());
        }

        @Test
        public void testCustomPatternIgnorePattern() {
            var config = new StringConfig.CustomPatternConfig();
            config.setName("aws_keys");
            config.setPatterns(List.of("AKIA[0-9A-Z]{16}"));

            // Ignore test/example keys
            config.getIgnore().add("AKIA.*EXAMPLE.*");
            config.getIgnore().add("AKIA.*TEST.*");

            assertEquals(2, config.getIgnore().size());
        }
    }

    @Nested
    class IgnoreAfterTests {
        @Test
        public void testEmailIgnoreAfter() {
            var config = new StringConfig.EmailsConfig();

            // Don't redact emails after "mailto:"
            config.getIgnoreAfter().add("mailto:");
            // Don't redact emails after "From: "
            config.getIgnoreAfter().add("From:\\s*");

            assertEquals(2, config.getIgnoreAfter().size());
            assertTrue(config.getIgnoreAfter().contains("mailto:"));
        }

        @Test
        public void testIpAddressIgnoreAfter() {
            var config = new StringConfig.IpAddressesConfig();

            // Don't redact IPs after specific prefixes
            config.getIgnoreAfter().add("bind\\s+");
            config.getIgnoreAfter().add("listen\\s+");
            config.getIgnoreAfter().add("--host=");

            assertEquals(3, config.getIgnoreAfter().size());
        }

        @Test
        public void testHostnameIgnoreAfter() {
            var config = new StringConfig.HostnamesConfig();

            // Don't redact hostnames after specific contexts
            config.getIgnoreAfter().add("Host:\\s*");
            config.getIgnoreAfter().add("uname:\\s*Darwin\\s+");

            assertEquals(2, config.getIgnoreAfter().size());
        }

        @Test
        public void testHomeDirectoryIgnoreAfter() {
            var config = new StringConfig.UserConfig();

            // Don't redact after installation prefix flags
            config.getIgnoreAfter().add("--prefix=");
            config.getIgnoreAfter().add("--install-dir=");
            config.getIgnoreAfter().add("HOME=");

            assertEquals(3, config.getIgnoreAfter().size());
        }

        @Test
        public void testCustomPatternIgnoreAfter() {
            var config = new StringConfig.CustomPatternConfig();
            config.setName("jwt_tokens");
            config.setPatterns(List.of("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+"));

            // Don't redact JWTs in example/documentation contexts
            config.getIgnoreAfter().add("Example:\\s*");
            config.getIgnoreAfter().add("Sample token:\\s*");

            assertEquals(2, config.getIgnoreAfter().size());
        }
    }

    @Nested
    class CombinedIgnoreTests {
        @Test
        public void testMultipleIgnoreTypesOnSameConfig() {
            var config = new StringConfig.IpAddressesConfig();

            // Exact matches
            config.getIgnoreExact().add("127.0.0.1");
            config.getIgnoreExact().add("::1");

            // Pattern matches
            config.getIgnore().add("192\\.168\\..*");
            config.getIgnore().add("10\\..*");

            // Context-based ignores
            config.getIgnoreAfter().add("--bind-address=");
            config.getIgnoreAfter().add("listen\\s+");

            assertEquals(2, config.getIgnoreExact().size());
            assertEquals(2, config.getIgnore().size());
            assertEquals(2, config.getIgnoreAfter().size());
        }

        @Test
        public void testCustomPatternWithAllIgnoreTypes() {
            var config = new StringConfig.CustomPatternConfig();
            config.setName("ticket_ids");
            config.setPatterns(List.of("TICKET-\\d{6}"));

            config.getIgnoreExact().add("TICKET-000000");
            config.getIgnore().add("TICKET-999.*");
            config.getIgnoreAfter().add("Test ticket:\\s*");

            assertEquals(1, config.getIgnoreExact().size());
            assertEquals(1, config.getIgnore().size());
            assertEquals(1, config.getIgnoreAfter().size());
        }

        @Test
        public void testIgnoreFieldsAcrossAllPatternTypes() {
            StringConfig config = new StringConfig();

            // Configure ignore fields for each pattern type
            config.getPatterns().getEmails().getIgnoreExact().add("noreply@example.com");
            config.getPatterns().getIpAddresses().getIgnoreExact().add("127.0.0.1");
            config.getPatterns().getUser().getIgnoreExact().add("/usr/local");
            config.getPatterns().getHostnames().getIgnoreExact().add("example.com");
            config.getPatterns().getSshHosts().getIgnoreExact().add("git@github.com");
            config.getPatterns().getInternalUrls().getIgnoreExact().add("https://docs.oracle.com");
            config.getPatterns().getUuids().getIgnoreExact().add("00000000-0000-0000-0000-000000000000");

            // Verify all were set
            assertTrue(config.getPatterns().getEmails().getIgnoreExact().contains("noreply@example.com"));
            assertTrue(config.getPatterns().getIpAddresses().getIgnoreExact().contains("127.0.0.1"));
            assertTrue(config.getPatterns().getUser().getIgnoreExact().contains("/usr/local"));
            assertTrue(config.getPatterns().getHostnames().getIgnoreExact().contains("example.com"));
            assertTrue(config.getPatterns().getSshHosts().getIgnoreExact().contains("git@github.com"));
            assertTrue(config.getPatterns().getInternalUrls().getIgnoreExact().contains("https://docs.oracle.com"));
            assertTrue(config.getPatterns().getUuids().getIgnoreExact().contains("00000000-0000-0000-0000-000000000000"));
        }
    }

    @Nested
    class BasePatternConfigInheritanceTests {
        @Test
        public void testAllConfigsExtendBasePattern() {
            // Verify all configs have ignore methods
            var email = new StringConfig.EmailsConfig();
            var ip = new StringConfig.IpAddressesConfig();
            var home = new StringConfig.UserConfig();
            var hostname = new StringConfig.HostnamesConfig();
            var ssh = new StringConfig.SshHostsConfig();
            var internalUrl = new StringConfig.InternalUrlsConfig();
            var uuid = new StringConfig.UuidsConfig();

            // All should have ignore methods
            assertNotNull(email.getIgnoreExact());
            assertNotNull(ip.getIgnore());
            assertNotNull(home.getIgnoreAfter());
            assertNotNull(hostname.getIgnoreExact());
            assertNotNull(ssh.getIgnore());
            assertNotNull(internalUrl.getIgnoreAfter());
            assertNotNull(uuid.getIgnoreExact());
        }

        @Test
        public void testDefaultEnabledStates() {
            // Test that enabled defaults are preserved
            assertTrue(new StringConfig.EmailsConfig().isEnabled());
            assertTrue(new StringConfig.IpAddressesConfig().isEnabled());
            assertTrue(new StringConfig.UserConfig().isEnabled());

            assertFalse(new StringConfig.UuidsConfig().isEnabled());
            assertFalse(new StringConfig.SshHostsConfig().isEnabled());
            assertFalse(new StringConfig.HostnamesConfig().isEnabled());
            assertFalse(new StringConfig.InternalUrlsConfig().isEnabled());
        }
    }

    @Nested
    class InternalUrlsIgnoreTests {
        @Test
        public void testInternalUrlIgnoreExact() {
            var config = new StringConfig.InternalUrlsConfig();

            config.getIgnoreExact().add("https://bugreport.java.com/bugreport/crash.jsp");
            config.getIgnoreExact().add("https://docs.oracle.com/");

            assertEquals(2, config.getIgnoreExact().size());
            assertTrue(config.getIgnoreExact().contains("https://bugreport.java.com/bugreport/crash.jsp"));
        }

        @Test
        public void testInternalUrlIgnorePattern() {
            var config = new StringConfig.InternalUrlsConfig();

            // Don't redact well-known public URLs
            config.getIgnore().add("https?://.*\\.oracle\\.com/.*");
            config.getIgnore().add("https?://.*\\.openjdk\\.org/.*");
            config.getIgnore().add("https?://github\\.com/.*");

            assertEquals(3, config.getIgnore().size());
        }
    }

    @Nested
    class UuidIgnoreTests {
        @Test
        public void testUuidIgnoreExact() {
            var config = new StringConfig.UuidsConfig();

            // Well-known UUIDs that shouldn't be redacted
            config.getIgnoreExact().add("00000000-0000-0000-0000-000000000000");
            config.getIgnoreExact().add("ffffffff-ffff-ffff-ffff-ffffffffffff");

            assertEquals(2, config.getIgnoreExact().size());
        }

        @Test
        public void testUuidIgnorePattern() {
            var config = new StringConfig.UuidsConfig();

            // Ignore nil UUID pattern
            config.getIgnore().add("0{8}-0{4}-0{4}-0{4}-0{12}");

            assertEquals(1, config.getIgnore().size());
        }
    }
}