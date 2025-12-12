package me.bechberger.jfrredact;

import me.bechberger.jfrredact.ConfigLoader.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for improved configuration error handling.
 */
public class ConfigErrorHandlingTest {

    @TempDir
    Path tempDir;

    @Test
    public void testMissingFile() {
        ConfigLoader loader = new ConfigLoader();

        Path missingFile = tempDir.resolve("nonexistent.yaml");

        ConfigurationException exception = assertThrows(
            ConfigurationException.class,
            () -> loader.load(missingFile.toString())
        );

        assertTrue(exception.getMessage().contains("not found"));
        assertTrue(exception.getMessage().contains("config-template.yaml"));
    }

    @Test
    public void testEmptyFile() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        Path emptyFile = tempDir.resolve("empty.yaml");
        Files.createFile(emptyFile);

        ConfigurationException exception = assertThrows(
            ConfigurationException.class,
            () -> loader.load(emptyFile.toString())
        );

        assertTrue(exception.getMessage().contains("empty"));
        assertTrue(exception.getMessage().contains("preset"));
    }

    @Test
    public void testInvalidYamlSyntax() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        String invalidYaml = """
            properties:
              enabled: true
              patterns
                - password
            """;

        Path configFile = tempDir.resolve("invalid.yaml");
        Files.writeString(configFile, invalidYaml);

        ConfigurationException exception = assertThrows(
            ConfigurationException.class,
            () -> loader.load(configFile.toString())
        );

        // Should get some configuration error (exact message depends on Jackson parser)
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().length() > 0);
    }

    @Test
    public void testUnknownProperty() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        String configWithTypo = """
            parent: default
            properteis:
              enabled: true
            """;

        Path configFile = tempDir.resolve("typo.yaml");
        Files.writeString(configFile, configWithTypo);

        ConfigurationException exception = assertThrows(
            ConfigurationException.class,
            () -> loader.load(configFile.toString())
        );

        assertTrue(exception.getMessage().contains("Unknown property") ||
                   exception.getMessage().contains("properteis"));
        assertTrue(exception.getMessage().contains("config-template.yaml"));
    }

    @Test
    public void testCircularDependency() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        String config1 = """
            parent: %s
            properties:
              enabled: true
            """.formatted(tempDir.resolve("config2.yaml"));

        String config2 = """
            parent: %s
            strings:
              enabled: true
            """.formatted(tempDir.resolve("config1.yaml"));

        Path config1File = tempDir.resolve("config1.yaml");
        Path config2File = tempDir.resolve("config2.yaml");

        Files.writeString(config1File, config1);
        Files.writeString(config2File, config2);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> loader.load(config1File.toString())
        );

        // Check that we get a circular dependency error
        assertTrue(exception.getMessage().contains("Circular") ||
                   exception.getMessage().contains("circular"));
    }

    @Test
    public void testMissingParent() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        String config = """
            parent: /nonexistent/parent.yaml
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, config);

        ConfigurationException exception = assertThrows(
            ConfigurationException.class,
            () -> loader.load(configFile.toString())
        );

        assertTrue(exception.getMessage().contains("parent"));
        assertTrue(exception.getMessage().contains("/nonexistent/parent.yaml"));
    }

    @Test
    public void testSuccessfulLoad() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        String validConfig = """
            parent: default
            properties:
              enabled: true
              patterns:
                - custom_secret
            """;

        Path configFile = tempDir.resolve("valid.yaml");
        Files.writeString(configFile, validConfig);

        // Should not throw
        assertDoesNotThrow(() -> loader.load(configFile.toString()));
    }

    @Test
    public void testInvalidUrl() {
        ConfigLoader loader = new ConfigLoader();

        ConfigurationException exception = assertThrows(
            ConfigurationException.class,
            () -> loader.load("http://invalid-hostname-that-does-not-exist-12345.com/config.yaml")
        );

        assertTrue(exception.getMessage().contains("Cannot resolve hostname") ||
                   exception.getMessage().contains("URL"));
    }
}