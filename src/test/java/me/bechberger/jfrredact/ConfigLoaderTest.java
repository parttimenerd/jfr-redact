package me.bechberger.jfrredact;

import me.bechberger.jfrredact.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configuration loading and resolution.
 */
public class ConfigLoaderTest {

    public ConfigLoaderTest() {
    }

    @Test
    public void testLoadDefaultPreset() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        assertNotNull(config);
        assertEquals("none", config.getParent(), "Default preset should have no parent");
    }

    @Test
    public void testLoadStrictPreset() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("strict");

        assertNotNull(config);
        assertEquals("default", config.getParent(), "Strict preset should extend default");
    }

    @Test
    public void testLoadNone() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("none");

        assertNotNull(config);
        assertEquals("none", config.getParent());
    }

    @Test
    public void testLoadNull() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load(null);

        assertNotNull(config);
        assertEquals("none", config.getParent());
    }

    @Test
    public void testLoadStandaloneConfig() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        URL resource = getClass().getResource("/test-standalone.yaml");
        assertNotNull(resource, "Test resource not found");

        RedactionConfig config = loader.load(resource.getPath());

        assertNotNull(config);
        assertEquals("none", config.getParent());
        assertEquals("###", config.getGeneral().getRedactionText());
        assertFalse(config.getEvents().isRemoveEnabled());
        assertTrue(config.getProperties().getPatterns().contains("test_password"));
    }

    @Test
    public void testLoadConfigWithParent() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        URL resource = getClass().getResource("/test-with-parent.yaml");
        assertNotNull(resource, "Test resource not found");

        RedactionConfig config = loader.load(resource.getPath());

        assertNotNull(config);
        assertEquals("default", config.getParent());

        // Should have both default patterns and custom ones
        assertTrue(config.getProperties().matches("password"));
        assertTrue(config.getProperties().getPatterns().contains("custom_secret"));
        assertTrue(config.getProperties().getPatterns().contains("my_password"));

        // Should have both default events and custom ones
        assertTrue(config.getEvents().getRemovedTypes().contains("jdk.OSInformation"));
        assertTrue(config.getEvents().getRemovedTypes().contains("jdk.CustomEvent"));

        // Pseudonymization settings from child config
        assertTrue(config.getGeneral().getPseudonymization().isEnabled());
        assertEquals(12, config.getGeneral().getPseudonymization().getHashLength());
    }

    @Test
    public void testCaching() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        RedactionConfig config1 = loader.load("default");
        RedactionConfig config2 = loader.load("default");

        // Should return the same instance (cached)
        assertSame(config1, config2);
    }

    @Test
    public void testClearCache() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        RedactionConfig config1 = loader.load("default");
        loader.clearCache();
        RedactionConfig config2 = loader.load("default");

        // Should be different instances after cache clear
        assertNotSame(config1, config2);
    }

    @Test
    public void testFileNotFound() {
        ConfigLoader loader = new ConfigLoader();

        assertThrows(IOException.class, () -> {
            loader.load("/nonexistent/file.yaml");
        });
    }

    @Test
    public void testPresetNotFound() {
        ConfigLoader loader = new ConfigLoader();

        assertThrows(IOException.class, () -> {
            loader.load("nonexistent-preset");
        });
    }

    @Test
    public void testPropertyMerging() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        URL resource = getClass().getResource("/test-with-parent.yaml");

        RedactionConfig config = loader.load(resource.getPath());

        // Verify patterns are merged
        assertTrue(config.getProperties().getPatterns().size() > 2);

        // Parent patterns should match
        assertTrue(config.getProperties().matches("password"), "Should match 'password' from parent");
        assertTrue(config.getProperties().matches("secret"), "Should match 'secret' from parent");

        // Child patterns should match
        assertTrue(config.getProperties().matches("custom_secret"), "Should match 'custom_secret' from child");
    }

    @Test
    public void testPseudonymizerCreation() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        // Default config has pseudonymization disabled
        var pseudonymizer = config.createPseudonymizer();
        assertFalse(pseudonymizer.isEnabled());
    }

    @Test
    public void testPseudonymizerWithEnabled() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        URL resource = getClass().getResource("/test-with-parent.yaml");

        RedactionConfig config = loader.load(resource.getPath());

        var pseudonymizer = config.createPseudonymizer();
        assertTrue(pseudonymizer.isEnabled());
    }

    @Test
    public void testCliOptionsApplication() {
        RedactionConfig config = new RedactionConfig();
        RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();

        cliOptions.setPseudonymize(true);
        cliOptions.getRemoveEvents().add("jdk.TestEvent");

        config.applyCliOptions(cliOptions);

        assertTrue(config.getGeneral().getPseudonymization().isEnabled());
        assertTrue(config.getEvents().getRemovedTypes().contains("jdk.TestEvent"));
    }

    @ParameterizedTest
    @CsvSource({
        "default, password, true",
        "default, passwort, true",
        "default, pwd, true",
        "default, secret, true",
        "default, token, true",
        "default, api_key, true",
        "default, key, false",  // Bare 'key' is NOT redacted (too generic) - use api_key, secret_key, etc.
        "default, secret_key, true",  // key as suffix IS matched
        "strict, password, true",
        "strict, user, true",
        "strict, username, true"
    })
    public void testPresetPropertyPatterns(String preset, String fieldName, boolean shouldMatch)
            throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load(preset);

        assertEquals(shouldMatch, config.getProperties().matches(fieldName),
                String.format("Preset '%s' should%s match field '%s'",
                        preset, shouldMatch ? "" : " not", fieldName));
    }

    @Test
    public void testDefaultPresetRedactsEmails() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        // Email pattern should be enabled
        assertTrue(config.getStrings().isEnabled(), "Strings should be enabled");
        assertTrue(config.getStrings().getPatterns().getEmails().isEnabled(),
                "Email redaction should be enabled in default preset");

        // Verify email patterns are configured
        assertFalse(config.getStrings().getPatterns().getEmails().getPatterns().isEmpty(),
                "Email patterns should be configured");
    }

    @Test
    public void testDefaultPresetDoesNotRedactUUIDs() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        // UUID pattern should be disabled in default
        assertFalse(config.getStrings().getPatterns().getUuids().isEnabled(),
                "UUID redaction should be disabled in default preset");
    }

    @Test
    public void testStrictPresetRedactsEmailsAndUUIDs() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("strict");

        // Email pattern should be enabled (inherited from default)
        assertTrue(config.getStrings().isEnabled(), "Strings should be enabled");
        assertTrue(config.getStrings().getPatterns().getEmails().isEnabled(),
                "Email redaction should be enabled in strict preset");

        // UUID pattern should be enabled (overridden in strict)
        assertTrue(config.getStrings().getPatterns().getUuids().isEnabled(),
                "UUID redaction should be enabled in strict preset");

        // Verify patterns are configured
        assertFalse(config.getStrings().getPatterns().getEmails().getPatterns().isEmpty(),
                "Email patterns should be configured");
        assertFalse(config.getStrings().getPatterns().getUuids().getPatterns().isEmpty(),
                "UUID patterns should be configured");
    }

    @Test
    public void testDefaultPresetEnablesSSHHostRedaction() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        // SSH host redaction should be enabled in default
        assertTrue(config.getStrings().getPatterns().getSshHosts().isEnabled(),
                "SSH host redaction should be enabled in default preset");
        assertFalse(config.getStrings().getPatterns().getSshHosts().getPatterns().isEmpty(),
                "SSH host patterns should be configured");
    }

    @Test
    public void testStrictPresetInheritsFromDefault() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("strict");

        // Verify it has the parent set correctly
        assertEquals("default", config.getParent(),
                "Strict preset should extend default");

        // Should inherit default patterns
        assertTrue(config.getStrings().getPatterns().getEmails().isEnabled(),
                "Should inherit email redaction from default");
        assertTrue(config.getStrings().getPatterns().getIpAddresses().isEnabled(),
                "Should inherit IP redaction from default");
        assertTrue(config.getStrings().getPatterns().getUser().isEnabled(),
                "Should inherit home directory redaction from default");

        // Should override UUID setting
        assertTrue(config.getStrings().getPatterns().getUuids().isEnabled(),
                "Should override UUID redaction to enabled");
    }

    @Test
    public void testDefaultPresetHasAllStringPatterns() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        // Verify all pattern categories exist
        assertNotNull(config.getStrings().getPatterns().getUser(),
                "Home directory patterns should exist");
        assertNotNull(config.getStrings().getPatterns().getEmails(),
                "Email patterns should exist");
        assertNotNull(config.getStrings().getPatterns().getUuids(),
                "UUID patterns should exist");
        assertNotNull(config.getStrings().getPatterns().getIpAddresses(),
                "IP address patterns should exist");
        assertNotNull(config.getStrings().getPatterns().getSshHosts(),
                "SSH host patterns should exist");

        // Verify enabled states
        assertTrue(config.getStrings().getPatterns().getUser().isEnabled());
        assertTrue(config.getStrings().getPatterns().getEmails().isEnabled());
        assertFalse(config.getStrings().getPatterns().getUuids().isEnabled());
        assertTrue(config.getStrings().getPatterns().getIpAddresses().isEnabled());
        assertTrue(config.getStrings().getPatterns().getSshHosts().isEnabled());
    }
}