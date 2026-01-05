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
import static org.assertj.core.api.Assertions.*;
import static me.bechberger.jfrredact.testutil.PropertiesAssert.assertThatProperties;

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
        assertThatProperties(config.getProperties()).matches("password");
        assertThatProperties(config.getProperties()).matches("secret");

        // Should have child patterns
        assertThatProperties(config.getProperties()).matches("custom_secret");
        assertThatProperties(config.getProperties()).matches("my_api_key");

        // Should have default events
        assertThat(config.getEvents().getRemovedTypes()).contains("jdk.SystemProcess");

        // Should have child events
        assertThat(config.getEvents().getRemovedTypes()).contains("jdk.CustomEvent");
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
        assertThatProperties(config.getProperties()).matches("password");
        assertThatProperties(config.getProperties()).matches("middle_secret");
        assertThatProperties(config.getProperties()).matches("child_secret");
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

        assertThat(config.getProperties().isEnabled()).as("Child should override").isFalse();
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
        assertThat(patterns).contains("parent1", "parent2", "child1", "child2");
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