package me.bechberger.jfrredact.engine;

import me.bechberger.jfrredact.config.DiscoveryConfig;
import me.bechberger.jfrredact.config.StringConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for property-based pattern discovery configuration.
 * Note: Full integration tests with actual JFR events are in separate test classes.
 */
class PropertyBasedDiscoveryTest {

    @Test
    void testPropertyExtractionConfigCreation() {
        // Setup config with property extraction
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("user_name");
        propExtraction.setKeyPattern("user\\.name");
        propExtraction.setType("USERNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(1);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        // Verify config was created correctly
        assertEquals(1, discoveryConfig.getPropertyExtractions().size());
        DiscoveryConfig.PropertyExtractionConfig config = discoveryConfig.getPropertyExtractions().get(0);
        assertEquals("user_name", config.getName());
        assertEquals("user\\.name", config.getKeyPattern());
        assertEquals("USERNAME", config.getType());
        assertThat(config.isCaseSensitive()).isFalse();
        assertEquals(1, config.getMinOccurrences());
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    void testPropertyExtractionWithEventTypeFilter() {
        // Setup config with property extraction and event type filter
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("hostname");
        propExtraction.setKeyPattern("hostname");
        propExtraction.setEventTypeFilter("jdk\\..*");  // Only JDK events
        propExtraction.setType("HOSTNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(1);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        // Verify filter is set correctly
        assertEquals("jdk\\..*", propExtraction.getEventTypeFilter());
    }

    @Test
    void testPropertyExtractionWithWhitelist() {
        // Setup config with whitelist
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("user_name");
        propExtraction.setKeyPattern("username");
        propExtraction.setType("USERNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(1);

        List<String> whitelist = new ArrayList<>();
        whitelist.add("root");
        whitelist.add("admin");
        propExtraction.setWhitelist(whitelist);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        // Verify whitelist is set correctly
        assertEquals(2, propExtraction.getWhitelist().size());
        assertThat(propExtraction.getWhitelist()).contains("root", "admin");
    }

    @Test
    void testPropertyExtractionWithMinOccurrences() {
        // Setup config with min_occurrences = 2
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("user");
        propExtraction.setKeyPattern("user");
        propExtraction.setType("USERNAME");
        propExtraction.setCaseSensitive(false);
        propExtraction.setMinOccurrences(2);
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        // Verify min occurrences is set correctly (clamped to minimum of 1)
        assertThat(propExtraction.getMinOccurrences()).isGreaterThanOrEqualTo(1);
        assertEquals(2, propExtraction.getMinOccurrences());
    }

    @Test
    void testMultiplePropertyExtractors() {
        // Setup config with multiple property extractors
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();

        // Username extractor
        DiscoveryConfig.PropertyExtractionConfig userExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        userExtraction.setName("user");
        userExtraction.setKeyPattern("user\\.name");
        userExtraction.setType("USERNAME");
        userExtraction.setMinOccurrences(1);
        userExtraction.setEnabled(true);

        // Hostname extractor
        DiscoveryConfig.PropertyExtractionConfig hostExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        hostExtraction.setName("host");
        hostExtraction.setKeyPattern("hostname");
        hostExtraction.setType("HOSTNAME");
        hostExtraction.setMinOccurrences(1);
        hostExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(userExtraction);
        discoveryConfig.getPropertyExtractions().add(hostExtraction);

        // Verify both extractors are added
        assertEquals(2, discoveryConfig.getPropertyExtractions().size());
        assertEquals("user", discoveryConfig.getPropertyExtractions().get(0).getName());
        assertEquals("host", discoveryConfig.getPropertyExtractions().get(1).getName());
    }

    @Test
    void testPatternDiscoveryEngineCompilation() {
        // Setup config with property extraction
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("test_property");
        propExtraction.setKeyPattern("test\\.property");
        propExtraction.setType("CUSTOM");
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false);

        // Should not throw an exception during compilation
        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);
        assertNotNull(engine);

        // Verify statistics can be generated
        String stats = engine.getStatistics();
        assertNotNull(stats);
        assertThat(stats).contains("Discovery Statistics");
    }

    @Test
    void testPropertyExtractionDisabled() {
        // Setup config with disabled property extraction
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("disabled_test");
        propExtraction.setKeyPattern("test");
        propExtraction.setEnabled(false);  // Disabled

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false);

        // Should compile without issues even though it's disabled
        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);
        assertNotNull(engine);
    }

    @Test
    void testInvalidPatternType() {
        // Setup config with invalid type (should default to CUSTOM)
        DiscoveryConfig discoveryConfig = new DiscoveryConfig();
        DiscoveryConfig.PropertyExtractionConfig propExtraction = new DiscoveryConfig.PropertyExtractionConfig();
        propExtraction.setName("invalid_type_test");
        propExtraction.setKeyPattern("test");
        propExtraction.setType("INVALID_TYPE");  // Invalid type
        propExtraction.setEnabled(true);

        discoveryConfig.getPropertyExtractions().add(propExtraction);

        StringConfig stringConfig = new StringConfig();
        stringConfig.setEnabled(false);

        // Should handle invalid type gracefully (logs warning, uses CUSTOM)
        PatternDiscoveryEngine engine = new PatternDiscoveryEngine(discoveryConfig, stringConfig);
        assertNotNull(engine);
    }
}