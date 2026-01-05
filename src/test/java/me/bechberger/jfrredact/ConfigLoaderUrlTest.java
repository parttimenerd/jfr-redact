package me.bechberger.jfrredact;

import me.bechberger.jfrredact.config.RedactionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigLoader URL support.
 */
public class ConfigLoaderUrlTest {

    @TempDir
    Path tempDir;

    private ConfigLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ConfigLoader();
    }

    // ========== URL Detection Tests ==========

    @Test
    public void testLoadFromFileUrl() throws IOException {
        // Create a test config file
        String configContent = """
            parent: none
            properties:
              enabled: true
              patterns:
                - test_property
            """;

        Path configFile = tempDir.resolve("test-config.yaml");
        Files.writeString(configFile, configContent);

        // Load using file:// URL
        String fileUrl = configFile.toUri().toString();
        RedactionConfig config = loader.load(fileUrl);

        assertNotNull(config);
        assertThat(config.getProperties().isEnabled()).isTrue();
        assertThat(config.getProperties().getPatterns()).contains("test_property");
    }

    @Test
    public void testLoadFromFileUrlWithParent() throws IOException {
        // Create parent config
        String parentContent = """
            parent: none
            properties:
              enabled: true
              patterns:
                - parent_property
            """;

        Path parentFile = tempDir.resolve("parent.yaml");
        Files.writeString(parentFile, parentContent);

        // Create child config that references parent via URL
        String childContent = """
            parent: %s
            properties:
              patterns:
                - $PARENT
                - child_property
            """.formatted(parentFile.toUri().toString());

        Path childFile = tempDir.resolve("child.yaml");
        Files.writeString(childFile, childContent);

        // Load child config
        RedactionConfig config = loader.load(childFile.toUri().toString());

        assertNotNull(config);
        assertThat(config.getProperties().isEnabled()).isTrue();
        assertThat(config.getProperties().getPatterns()).contains("parent_property", "child_property");
    }

    @Test
    public void testLoadFromFileUrlWithPresetParent() throws IOException {
        // Create config with preset parent
        String configContent = """
            parent: default
            properties:
              patterns:
                - $PARENT
                - custom_property
            """;

        Path configFile = tempDir.resolve("with-preset-parent.yaml");
        Files.writeString(configFile, configContent);

        // Load using file:// URL
        RedactionConfig config = loader.load(configFile.toUri().toString());

        assertNotNull(config);
        assertThat(config.getProperties().isEnabled()).isTrue();
        assertThat(config.getProperties().getPatterns()).contains("custom_property");
        // Should also have patterns from default preset
        assertThat(config.getProperties().getPatterns()).anyMatch(p -> p.contains("secret") || p.contains("password"));
    }

    @Test
    public void testLoadFromFilePath() throws IOException {
        // Create a test config file
        String configContent = """
            parent: none
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("path-config.yaml");
        Files.writeString(configFile, configContent);

        // Load using regular file path (not URL)
        RedactionConfig config = loader.load(configFile.toString());

        assertNotNull(config);
        assertThat(config.getProperties().isEnabled()).isTrue();
    }

    @Test
    public void testLoadFromPreset() throws IOException {
        RedactionConfig config = loader.load("default");

        assertNotNull(config);
        assertThat(config.getProperties().isEnabled()).isTrue();
    }

    @Test
    public void testLoadNone() throws IOException {
        RedactionConfig config = loader.load("none");

        assertNotNull(config);
    }

    @Test
    public void testLoadNull() throws IOException {
        RedactionConfig config = loader.load(null);

        assertNotNull(config);
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testLoadFromInvalidFileUrl() {
        String invalidUrl = "file:///nonexistent/path/to/config.yaml";

        assertThrows(IOException.class, () -> loader.load(invalidUrl));
    }

    @Test
    public void testLoadFromInvalidHttpUrl() {
        // This will fail quickly with connection timeout or DNS error
        String invalidUrl = "http://invalid.example.nonexistent/config.yaml";

        assertThrows(IOException.class, () -> loader.load(invalidUrl));
    }

    @Test
    public void testCircularDependencyWithUrls() throws IOException {
        // Create two configs that reference each other
        Path config1 = tempDir.resolve("config1.yaml");
        Path config2 = tempDir.resolve("config2.yaml");

        // Write config1 first with placeholder
        Files.writeString(config1, "parent: none\n");

        // Write config2 that references config1
        String config2Content = """
            parent: %s
            properties:
              enabled: true
            """.formatted(config1.toUri().toString());
        Files.writeString(config2, config2Content);

        // Update config1 to reference config2 (circular dependency)
        String config1Content = """
            parent: %s
            properties:
              enabled: false
            """.formatted(config2.toUri().toString());
        Files.writeString(config1, config1Content);

        // Loading should detect circular dependency
        String config1Url = config1.toUri().toString();
        assertThrows(IllegalArgumentException.class, () -> loader.load(config1Url));
    }

    // ========== Cache Tests ==========

    @Test
    public void testCacheWorksWithUrls() throws IOException {
        String configContent = """
            parent: none
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("cached.yaml");
        Files.writeString(configFile, configContent);
        String fileUrl = configFile.toUri().toString();

        // Load twice - should use cache
        RedactionConfig config1 = loader.load(fileUrl);
        RedactionConfig config2 = loader.load(fileUrl);

        // Should be the same instance from cache
        assertSame(config1, config2);
    }

    @Test
    public void testClearCache() throws IOException {
        String configContent = """
            parent: none
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("clearable.yaml");
        Files.writeString(configFile, configContent);
        String fileUrl = configFile.toUri().toString();

        RedactionConfig config1 = loader.load(fileUrl);
        loader.clearCache();
        RedactionConfig config2 = loader.load(fileUrl);

        // Should be different instances after cache clear
        assertNotSame(config1, config2);
    }

    // ========== Integration Tests ==========

    @Test
    public void testComplexInheritanceChain() throws IOException {
        // Create a chain: child -> parent (URL) -> grandparent (preset)

        // Grandparent is a preset (default)

        // Parent references grandparent preset
        String parentContent = """
            parent: default
            properties:
              patterns:
                - $PARENT
                - parent_pattern
            """;
        Path parentFile = tempDir.resolve("parent.yaml");
        Files.writeString(parentFile, parentContent);

        // Child references parent via URL
        String childContent = """
            parent: %s
            properties:
              patterns:
                - $PARENT
                - child_pattern
            """.formatted(parentFile.toUri().toString());
        Path childFile = tempDir.resolve("child.yaml");
        Files.writeString(childFile, childContent);

        // Load child
        RedactionConfig config = loader.load(childFile.toUri().toString());

        assertNotNull(config);
        assertThat(config.getProperties().isEnabled()).isTrue();
        assertThat(config.getProperties().getPatterns()).contains("child_pattern", "parent_pattern");
        // Should also have patterns from default preset
        assertThat(config.getProperties().getPatterns()).anyMatch(p -> p.contains("secret") || p.contains("password"));
    }

    @Test
    public void testMixedFilePathAndUrlParents() throws IOException {
        // Create parent config
        String parentContent = """
            parent: none
            properties:
              enabled: true
              patterns:
                - from_parent
            """;
        Path parentFile = tempDir.resolve("parent-mixed.yaml");
        Files.writeString(parentFile, parentContent);

        // Create child that references parent via file path (not URL)
        String childContent = """
            parent: %s
            properties:
              patterns:
                - $PARENT
                - from_child
            """.formatted(parentFile.toString());
        Path childFile = tempDir.resolve("child-mixed.yaml");
        Files.writeString(childFile, childContent);

        // Load child via URL
        RedactionConfig config = loader.load(childFile.toUri().toString());

        assertNotNull(config);
        assertThat(config.getProperties().getPatterns()).contains("from_parent", "from_child");
    }
}