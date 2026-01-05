package me.bechberger.jfrredact.config;

import me.bechberger.jfrredact.ConfigLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for $PARENT marker with actual YAML configuration loading.
 */
public class ParentMarkerIntegrationTest {

    @Test
    public void testStrictPreset_AppendsToDefault() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("strict");

        // Verify property patterns include both parent (default) and child (strict) patterns
        List<String> patterns = config.getProperties().getPatterns();

        // From default.yaml
        assertThat(patterns).contains("(pass(word|wort|wd)?|pwd)", "secret", "token");

        // From strict.yaml (added via $PARENT)
        assertThat(patterns).contains("user(name)?", "login");

        // Verify order: parent first, then child
        int passwordIndex = patterns.indexOf("(pass(word|wort|wd)?|pwd)");
        int usernameIndex = patterns.indexOf("user(name)?");
        assertThat(passwordIndex).as("Parent patterns should come before child patterns").isLessThan(usernameIndex);
    }

    @Test
    public void testStrictPreset_EventsAppendToDefault() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("strict");

        List<String> removedTypes = config.getEvents().getRemovedTypes();

        // From default.yaml
        assertThat(removedTypes).contains("jdk.SystemProcess");

        // From strict.yaml (added via $PARENT)
        assertThat(removedTypes).contains("jdk.SystemProperty", "jdk.NativeLibrary");
    }

    @Test
    public void testDefaultPreset_NoParentMarker() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        List<String> patterns = config.getProperties().getPatterns();

        // Should have the default patterns
        assertThat(patterns).contains("(pass(word|wort|wd)?|pwd)", "secret", "token");

        // Should NOT have strict-specific patterns
        assertThat(patterns).doesNotContain("login");
    }

    @Test
    public void testParentMarkerConfig_MixedOrder() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        // Load our test config that uses $PARENT in the middle
        RedactionConfig config = loader.load("src/test/resources/test-parent-marker.yaml");

        List<String> patterns = config.getProperties().getPatterns();

        // Verify order: custom_before, then parent patterns, then custom_after
        assertThat(patterns).contains("custom_before", "custom_after", "secret"); // from default

        int beforeIndex = patterns.indexOf("custom_before");
        int secretIndex = patterns.indexOf("secret");
        int afterIndex = patterns.indexOf("custom_after");

        assertThat(beforeIndex).isLessThan(secretIndex);
        assertThat(secretIndex).isLessThan(afterIndex);
    }

    @Test
    public void testParentMarkerConfig_Events() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("src/test/resources/test-parent-marker.yaml");

        List<String> removedTypes = config.getEvents().getRemovedTypes();

        // From default (via $PARENT)
        assertThat(removedTypes).contains("jdk.SystemProcess");

        // From test config
        assertThat(removedTypes).contains("jdk.TestEvent1", "jdk.TestEvent2");
    }

    @Test
    public void testOverrideConfig_NoParentMarker() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("src/test/resources/test-override.yaml");

        List<String> patterns = config.getProperties().getPatterns();

        // Should ONLY have the override pattern, not parent patterns
        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0)).isEqualTo("only_this_pattern");

        // Should NOT have default patterns
        assertThat(patterns).doesNotContain("secret", "password");
    }

    @Test
    public void testOverrideConfig_Events() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("src/test/resources/test-override.yaml");

        List<String> removedTypes = config.getEvents().getRemovedTypes();

        // Should ONLY have the override event, not parent events
        assertThat(removedTypes).hasSize(1);
        assertThat(removedTypes.get(0)).isEqualTo("only.this.Event");

        // Should NOT have default events
        assertThat(removedTypes).doesNotContain("jdk.OSInformation");
    }

    @Test
    public void testHserrPreset_UsesParentCorrectly() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("hserr");

        // hserr inherits from default, verify inheritance works
        List<String> patterns = config.getProperties().getPatterns();

        // Should have patterns from both default and hserr
        assertThat(patterns).as("Should have inherited patterns").isNotEmpty();

        // Verify some default patterns are present (inherited)
        assertThat(patterns.stream().anyMatch(p -> p.contains("pass"))).as("Should contain password-related pattern from default").isTrue();
    }

    @Test
    public void testParentMarkerInFilteringLists() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("src/test/resources/test-parent-marker.yaml");

        List<String> includeEvents = config.getEvents().getFiltering().getIncludeEvents();

        // Should have both test events
        assertThat(includeEvents).contains("test.Event1", "test.Event2");

        // Verify order
        int event1Index = includeEvents.indexOf("test.Event1");
        int event2Index = includeEvents.indexOf("test.Event2");
        assertThat(event1Index).isLessThan(event2Index);
    }

    @Test
    public void testMultipleLevelsOfInheritance() throws IOException {
        // Create a config that inherits from strict, which inherits from default
        // This tests that $PARENT expansion works across multiple inheritance levels
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig strictConfig = loader.load("strict");

        // strict has $PARENT which expands to default's patterns
        List<String> patterns = strictConfig.getProperties().getPatterns();

        // Should have patterns from default (grandparent)
        assertThat(patterns).contains("secret");

        // Should have patterns added by strict (parent)
        assertThat(patterns).contains("login");
    }
}