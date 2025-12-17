package me.bechberger.jfrredact.config;

import me.bechberger.jfrredact.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configuration inheritance (parent/child relationships and overriding).
 */
public class ConfigInheritanceTest {

    @TempDir
    Path tempDir;

    @Test
    public void testChildExtendsDefaultPreset() throws IOException {
        String childYaml = """
                parent: default
                
                properties:
                  patterns:
                    - $PARENT
                    - custom_secret
                    - my_api_key
                
                events:
                  removed_types:
                    - $PARENT
                    - jdk.CustomEvent
                """;

        File childFile = tempDir.resolve("child.yaml").toFile();
        Files.writeString(childFile.toPath(), childYaml);

        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load(childFile.getAbsolutePath());

        // Should have default patterns
        assertTrue(config.getProperties().matches("password"));
        assertTrue(config.getProperties().matches("secret"));

        // Should have child patterns
        assertTrue(config.getProperties().matches("custom_secret"));
        assertTrue(config.getProperties().matches("my_api_key"));

        // Should have default events
        assertTrue(config.getEvents().getRemovedTypes().contains("jdk.OSInformation"));

        // Should have child events
        assertTrue(config.getEvents().getRemovedTypes().contains("jdk.CustomEvent"));
    }

    @Test
    public void testTwoLevelInheritance() throws IOException {
        // Parent extends default
        String parentYaml = """
                parent: default
                
                properties:
                  patterns:
                    - $PARENT
                    - middle_secret
                """;

        File parentFile = tempDir.resolve("parent.yaml").toFile();
        Files.writeString(parentFile.toPath(), parentYaml);

        // Child extends parent
        String childYaml = "parent: " + parentFile.getAbsolutePath() + "\n\n" +
            "properties:\n" +
            "  patterns:\n" +
            "    - $PARENT\n" +
            "    - child_secret\n";

        File childFile = tempDir.resolve("child.yaml").toFile();
        Files.writeString(childFile.toPath(), childYaml);

        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load(childFile.getAbsolutePath());

        // Should have all three levels
        assertTrue(config.getProperties().matches("password"), "From default");
        assertTrue(config.getProperties().matches("middle_secret"), "From parent");
        assertTrue(config.getProperties().matches("child_secret"), "From child");
    }

    @Test
    public void testChildOverridesBoolean() throws IOException {
        String parentYaml = """
                parent: none
                
                properties:
                  enabled: true
                """;

        File parentFile = tempDir.resolve("parent.yaml").toFile();
        Files.writeString(parentFile.toPath(), parentYaml);

        String childYaml = "parent: " + parentFile.getAbsolutePath() + "\n\n" +
            "properties:\n" +
            "  enabled: false\n";

        File childFile = tempDir.resolve("child.yaml").toFile();
        Files.writeString(childFile.toPath(), childYaml);

        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load(childFile.getAbsolutePath());

        assertFalse(config.getProperties().isEnabled(), "Child should override");
    }

    @Test
    public void testListMerging() throws IOException {
        String parentYaml = """
                parent: none
                
                properties:
                  patterns:
                    - parent1
                    - parent2
                """;

        File parentFile = tempDir.resolve("parent.yaml").toFile();
        Files.writeString(parentFile.toPath(), parentYaml);

        String childYaml = "parent: " + parentFile.getAbsolutePath() + "\n\n" +
            "properties:\n" +
            "  patterns:\n" +
            "    - $PARENT\n" +
            "    - child1\n" +
            "    - child2\n";

        File childFile = tempDir.resolve("child.yaml").toFile();
        Files.writeString(childFile.toPath(), childYaml);

        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load(childFile.getAbsolutePath());

        List<String> patterns = config.getProperties().getPatterns();
        assertTrue(patterns.contains("parent1"));
        assertTrue(patterns.contains("parent2"));
        assertTrue(patterns.contains("child1"));
        assertTrue(patterns.contains("child2"));
    }

    @Test
    public void testCachingWorks() throws IOException {
        String yaml = "parent: default\n";
        File file = tempDir.resolve("cached.yaml").toFile();
        Files.writeString(file.toPath(), yaml);

        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config1 = loader.load(file.getAbsolutePath());
        RedactionConfig config2 = loader.load(file.getAbsolutePath());

        assertSame(config1, config2, "Should use cache");

        loader.clearCache();
        RedactionConfig config3 = loader.load(file.getAbsolutePath());
        assertNotSame(config1, config3, "Should be different after clear");
    }
}