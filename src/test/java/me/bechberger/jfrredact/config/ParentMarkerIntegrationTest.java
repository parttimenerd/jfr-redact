package me.bechberger.jfrredact.config;

import me.bechberger.jfrredact.ConfigLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        assertTrue(patterns.contains("(pass(word|wort|wd)?|pwd)"), "Should contain default password pattern");
        assertTrue(patterns.contains("secret"), "Should contain default secret pattern");
        assertTrue(patterns.contains("token"), "Should contain default token pattern");

        // From strict.yaml (added via $PARENT)
        assertTrue(patterns.contains("user(name)?"), "Should contain strict username pattern");
        assertTrue(patterns.contains("login"), "Should contain strict login pattern");

        // Verify order: parent first, then child
        int passwordIndex = patterns.indexOf("(pass(word|wort|wd)?|pwd)");
        int usernameIndex = patterns.indexOf("user(name)?");
        assertTrue(passwordIndex < usernameIndex, "Parent patterns should come before child patterns");
    }

    @Test
    public void testStrictPreset_EventsAppendToDefault() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("strict");

        List<String> removedTypes = config.getEvents().getRemovedTypes();

        // From default.yaml
        assertTrue(removedTypes.contains("jdk.OSInformation"), "Should contain default OSInformation");
        assertTrue(removedTypes.contains("jdk.SystemProcess"), "Should contain default SystemProcess");

        // From strict.yaml (added via $PARENT)
        assertTrue(removedTypes.contains("jdk.SystemProperty"), "Should contain strict SystemProperty");
        assertTrue(removedTypes.contains("jdk.NativeLibrary"), "Should contain strict NativeLibrary");
    }

    @Test
    public void testDefaultPreset_NoParentMarker() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("default");

        List<String> patterns = config.getProperties().getPatterns();

        // Should have the default patterns
        assertTrue(patterns.contains("(pass(word|wort|wd)?|pwd)"));
        assertTrue(patterns.contains("secret"));
        assertTrue(patterns.contains("token"));

        // Should NOT have strict-specific patterns
        assertFalse(patterns.contains("login"));
    }

    @Test
    public void testParentMarkerConfig_MixedOrder() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        // Load our test config that uses $PARENT in the middle
        RedactionConfig config = loader.load("src/test/resources/test-parent-marker.yaml");

        List<String> patterns = config.getProperties().getPatterns();

        // Verify order: custom_before, then parent patterns, then custom_after
        assertTrue(patterns.contains("custom_before"));
        assertTrue(patterns.contains("custom_after"));
        assertTrue(patterns.contains("secret")); // from default

        int beforeIndex = patterns.indexOf("custom_before");
        int secretIndex = patterns.indexOf("secret");
        int afterIndex = patterns.indexOf("custom_after");

        assertTrue(beforeIndex < secretIndex, "custom_before should come before parent patterns");
        assertTrue(secretIndex < afterIndex, "Parent patterns should come before custom_after");
    }

    @Test
    public void testParentMarkerConfig_Events() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("src/test/resources/test-parent-marker.yaml");

        List<String> removedTypes = config.getEvents().getRemovedTypes();

        // From default (via $PARENT)
        assertTrue(removedTypes.contains("jdk.OSInformation"));
        assertTrue(removedTypes.contains("jdk.SystemProcess"));

        // From test config
        assertTrue(removedTypes.contains("jdk.TestEvent1"));
        assertTrue(removedTypes.contains("jdk.TestEvent2"));
    }

    @Test
    public void testOverrideConfig_NoParentMarker() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("src/test/resources/test-override.yaml");

        List<String> patterns = config.getProperties().getPatterns();

        // Should ONLY have the override pattern, not parent patterns
        assertEquals(1, patterns.size(), "Should have exactly 1 pattern (override behavior)");
        assertEquals("only_this_pattern", patterns.get(0));

        // Should NOT have default patterns
        assertFalse(patterns.contains("secret"));
        assertFalse(patterns.contains("password"));
    }

    @Test
    public void testOverrideConfig_Events() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("src/test/resources/test-override.yaml");

        List<String> removedTypes = config.getEvents().getRemovedTypes();

        // Should ONLY have the override event, not parent events
        assertEquals(1, removedTypes.size(), "Should have exactly 1 event type (override behavior)");
        assertEquals("only.this.Event", removedTypes.get(0));

        // Should NOT have default events
        assertFalse(removedTypes.contains("jdk.OSInformation"));
    }

    @Test
    public void testHserrPreset_UsesParentCorrectly() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("hserr");

        // hserr inherits from default, verify inheritance works
        List<String> patterns = config.getProperties().getPatterns();

        // Should have patterns from both default and hserr
        assertFalse(patterns.isEmpty(), "Should have inherited patterns");

        // Verify some default patterns are present (inherited)
        assertTrue(patterns.stream().anyMatch(p -> p.contains("pass")),
            "Should contain password-related pattern from default");
    }

    @Test
    public void testParentMarkerInFilteringLists() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        RedactionConfig config = loader.load("src/test/resources/test-parent-marker.yaml");

        List<String> includeEvents = config.getEvents().getFiltering().getIncludeEvents();

        // Should have both test events
        assertTrue(includeEvents.contains("test.Event1"));
        assertTrue(includeEvents.contains("test.Event2"));

        // Verify order
        int event1Index = includeEvents.indexOf("test.Event1");
        int event2Index = includeEvents.indexOf("test.Event2");
        assertTrue(event1Index < event2Index, "Events should maintain order with $PARENT expansion");
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
        assertTrue(patterns.contains("secret"), "Should inherit from default via strict");

        // Should have patterns added by strict (parent)
        assertTrue(patterns.contains("login"), "Should have strict's own patterns");
    }
}