package me.bechberger.jfrredact.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EventConfig filtering configuration handling.
 */
class EventFilteringConfigTest {

    @Nested
    @DisplayName("FilteringConfig Initialization")
    class InitializationTests {

        @Test
        void testDefaultFilteringConfig() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();

            assertTrue(filtering.getIncludeEvents().isEmpty());
            assertTrue(filtering.getExcludeEvents().isEmpty());
            assertTrue(filtering.getIncludeCategories().isEmpty());
            assertTrue(filtering.getExcludeCategories().isEmpty());
            assertTrue(filtering.getIncludeThreads().isEmpty());
            assertTrue(filtering.getExcludeThreads().isEmpty());
            assertFalse(filtering.hasAnyFilters());
        }

        @Test
        void testHasAnyFilters_WithIncludeEvents() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getIncludeEvents().add("jdk.*");

            assertTrue(filtering.hasAnyFilters());
        }

        @Test
        void testHasAnyFilters_WithExcludeEvents() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getExcludeEvents().add("jdk.GC*");

            assertTrue(filtering.hasAnyFilters());
        }

        @Test
        void testHasAnyFilters_WithCategories() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getIncludeCategories().add("Application");

            assertTrue(filtering.hasAnyFilters());
        }

        @Test
        void testHasAnyFilters_WithThreads() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getExcludeThreads().add("GC Thread*");

            assertTrue(filtering.hasAnyFilters());
        }
    }

    @Nested
    @DisplayName("FilteringConfig Merging")
    class MergingTests {

        @Test
        void testMergeWithNull() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getIncludeEvents().add("test.*");

            filtering.mergeWith(null);

            assertEquals(1, filtering.getIncludeEvents().size());
            assertEquals("test.*", filtering.getIncludeEvents().get(0));
        }

        @Test
        void testMergeIncludeEvents() {
            EventConfig.FilteringConfig child = new EventConfig.FilteringConfig();
            child.getIncludeEvents().add("my.app.*");

            EventConfig.FilteringConfig parent = new EventConfig.FilteringConfig();
            parent.getIncludeEvents().add("jdk.*");

            child.mergeWith(parent);

            assertEquals(2, child.getIncludeEvents().size());
            assertTrue(child.getIncludeEvents().contains("my.app.*"));
            assertTrue(child.getIncludeEvents().contains("jdk.*"));
        }

        @Test
        void testMergeExcludeEvents() {
            EventConfig.FilteringConfig child = new EventConfig.FilteringConfig();
            child.getExcludeEvents().add("test.Debug*");

            EventConfig.FilteringConfig parent = new EventConfig.FilteringConfig();
            parent.getExcludeEvents().add("jdk.GC*");

            child.mergeWith(parent);

            assertEquals(2, child.getExcludeEvents().size());
            assertTrue(child.getExcludeEvents().contains("test.Debug*"));
            assertTrue(child.getExcludeEvents().contains("jdk.GC*"));
        }

        @Test
        void testMergeCategories() {
            EventConfig.FilteringConfig child = new EventConfig.FilteringConfig();
            child.getIncludeCategories().add("Application");
            child.getExcludeCategories().add("System");

            EventConfig.FilteringConfig parent = new EventConfig.FilteringConfig();
            parent.getIncludeCategories().add("JVM");
            parent.getExcludeCategories().add("Flight Recorder");

            child.mergeWith(parent);

            assertEquals(2, child.getIncludeCategories().size());
            assertEquals(2, child.getExcludeCategories().size());
            assertTrue(child.getIncludeCategories().contains("Application"));
            assertTrue(child.getIncludeCategories().contains("JVM"));
            assertTrue(child.getExcludeCategories().contains("System"));
            assertTrue(child.getExcludeCategories().contains("Flight Recorder"));
        }

        @Test
        void testMergeThreads() {
            EventConfig.FilteringConfig child = new EventConfig.FilteringConfig();
            child.getIncludeThreads().add("main");
            child.getExcludeThreads().add("worker-*");

            EventConfig.FilteringConfig parent = new EventConfig.FilteringConfig();
            parent.getIncludeThreads().add("test-*");
            parent.getExcludeThreads().add("GC Thread*");

            child.mergeWith(parent);

            assertEquals(2, child.getIncludeThreads().size());
            assertEquals(2, child.getExcludeThreads().size());
            assertTrue(child.getIncludeThreads().contains("main"));
            assertTrue(child.getIncludeThreads().contains("test-*"));
            assertTrue(child.getExcludeThreads().contains("worker-*"));
            assertTrue(child.getExcludeThreads().contains("GC Thread*"));
        }

        @Test
        void testMergeAllFilters() {
            EventConfig.FilteringConfig child = new EventConfig.FilteringConfig();
            child.getIncludeEvents().add("child.event.*");

            EventConfig.FilteringConfig parent = new EventConfig.FilteringConfig();
            parent.getIncludeEvents().add("parent.event.*");
            parent.getExcludeEvents().add("*.Debug");
            parent.getIncludeCategories().add("Application");
            parent.getExcludeThreads().add("GC*");

            child.mergeWith(parent);

            assertEquals(2, child.getIncludeEvents().size());
            assertEquals(1, child.getExcludeEvents().size());
            assertEquals(1, child.getIncludeCategories().size());
            assertEquals(1, child.getExcludeThreads().size());
            assertTrue(child.hasAnyFilters());
        }
    }

    @Nested
    @DisplayName("EventConfig Integration")
    class EventConfigIntegrationTests {

        @Test
        void testDefaultEventConfig() {
            EventConfig config = new EventConfig();

            assertTrue(config.isRemoveEnabled());
            assertFalse(config.getRemovedTypes().isEmpty());
            assertNotNull(config.getFiltering());
            assertFalse(config.getFiltering().hasAnyFilters());
        }

        @Test
        void testEventConfigMerge() {
            EventConfig child = new EventConfig();
            child.getRemovedTypes().clear();
            child.getRemovedTypes().add("child.Event");
            child.getFiltering().getIncludeEvents().add("child.*");

            EventConfig parent = new EventConfig();
            parent.getRemovedTypes().clear();
            parent.getRemovedTypes().add("parent.Event");
            parent.getFiltering().getExcludeEvents().add("*.Debug");

            child.mergeWith(parent);

            // Check removed types are merged
            assertEquals(2, child.getRemovedTypes().size());
            assertTrue(child.getRemovedTypes().contains("child.Event"));
            assertTrue(child.getRemovedTypes().contains("parent.Event"));

            // Check filtering is merged
            assertEquals(1, child.getFiltering().getIncludeEvents().size());
            assertEquals(1, child.getFiltering().getExcludeEvents().size());
        }

        @Test
        void testShouldRemove_SimpleTypes() {
            EventConfig config = new EventConfig();
            config.getRemovedTypes().clear();
            config.getRemovedTypes().add("jdk.OSInformation");
            config.getRemovedTypes().add("jdk.SystemProcess");

            assertTrue(config.shouldRemove("jdk.OSInformation"));
            assertTrue(config.shouldRemove("jdk.SystemProcess"));
            assertFalse(config.shouldRemove("jdk.ThreadSleep"));
        }

        @Test
        void testShouldRemove_DisabledRemoval() {
            EventConfig config = new EventConfig();
            config.setRemoveEnabled(false);
            config.getRemovedTypes().add("jdk.OSInformation");

            assertFalse(config.shouldRemove("jdk.OSInformation"));
        }
    }

    @Nested
    @DisplayName("CLI Options Integration")
    class CliOptionsTests {

        @Test
        void testApplyCliOptions_IncludeEvents() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
            cliOptions.setIncludeEvents(Arrays.asList("test.*", "my.app.*"));

            config.applyCliOptions(cliOptions);

            List<String> includeEvents = config.getEvents().getFiltering().getIncludeEvents();
            assertEquals(2, includeEvents.size());
            assertTrue(includeEvents.contains("test.*"));
            assertTrue(includeEvents.contains("my.app.*"));
        }

        @Test
        void testApplyCliOptions_ExcludeEvents() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
            cliOptions.setExcludeEvents(Arrays.asList("jdk.GC*", "*.Debug"));

            config.applyCliOptions(cliOptions);

            List<String> excludeEvents = config.getEvents().getFiltering().getExcludeEvents();
            assertEquals(2, excludeEvents.size());
            assertTrue(excludeEvents.contains("jdk.GC*"));
            assertTrue(excludeEvents.contains("*.Debug"));
        }

        @Test
        void testApplyCliOptions_Categories() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
            cliOptions.setIncludeCategories(Arrays.asList("Application", "JVM"));
            cliOptions.setExcludeCategories(Arrays.asList("Flight Recorder"));

            config.applyCliOptions(cliOptions);

            EventConfig.FilteringConfig filtering = config.getEvents().getFiltering();
            assertEquals(2, filtering.getIncludeCategories().size());
            assertEquals(1, filtering.getExcludeCategories().size());
        }

        @Test
        void testApplyCliOptions_Threads() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
            cliOptions.setIncludeThreads(Arrays.asList("main", "test-*"));
            cliOptions.setExcludeThreads(Arrays.asList("GC Thread*", "Service Thread"));

            config.applyCliOptions(cliOptions);

            EventConfig.FilteringConfig filtering = config.getEvents().getFiltering();
            assertEquals(2, filtering.getIncludeThreads().size());
            assertEquals(2, filtering.getExcludeThreads().size());
        }

        @Test
        void testApplyCliOptions_CombinedWithRemoveEvents() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();

            // Set both removal and filtering
            cliOptions.setRemoveEvents(Arrays.asList("jdk.OSInformation"));
            cliOptions.setIncludeEvents(Arrays.asList("jdk.*"));
            cliOptions.setExcludeEvents(Arrays.asList("jdk.GC*"));

            config.applyCliOptions(cliOptions);

            // Check removal types
            assertTrue(config.getEvents().getRemovedTypes().contains("jdk.OSInformation"));

            // Check filtering
            EventConfig.FilteringConfig filtering = config.getEvents().getFiltering();
            assertEquals(1, filtering.getIncludeEvents().size());
            assertEquals(1, filtering.getExcludeEvents().size());
        }

        @Test
        void testApplyCliOptions_NullOptions() {
            RedactionConfig config = new RedactionConfig();

            // Should not throw exception
            assertDoesNotThrow(() -> config.applyCliOptions(null));
        }

        @Test
        void testApplyCliOptions_EmptyLists() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();

            config.applyCliOptions(cliOptions);

            // Should not add anything
            EventConfig.FilteringConfig filtering = config.getEvents().getFiltering();
            assertFalse(filtering.hasAnyFilters());
        }

        @Test
        void testApplyCliOptions_MultipleApplications() {
            RedactionConfig config = new RedactionConfig();

            // Apply first set of options
            RedactionConfig.CliOptions options1 = new RedactionConfig.CliOptions();
            options1.setIncludeEvents(Arrays.asList("test.*"));
            config.applyCliOptions(options1);

            // Apply second set of options
            RedactionConfig.CliOptions options2 = new RedactionConfig.CliOptions();
            options2.setIncludeEvents(Arrays.asList("my.app.*"));
            config.applyCliOptions(options2);

            // Both should be present (accumulative)
            List<String> includeEvents = config.getEvents().getFiltering().getIncludeEvents();
            assertEquals(2, includeEvents.size());
            assertTrue(includeEvents.contains("test.*"));
            assertTrue(includeEvents.contains("my.app.*"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation")
    class EdgeCasesTests {

        @Test
        void testEmptyPatternLists() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.setIncludeEvents(Arrays.asList());
            filtering.setExcludeEvents(Arrays.asList());

            assertFalse(filtering.hasAnyFilters());
        }

        @Test
        void testNullSafety() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();

            // These should not throw NPE
            assertNotNull(filtering.getIncludeEvents());
            assertNotNull(filtering.getExcludeEvents());
            assertNotNull(filtering.getIncludeCategories());
            assertNotNull(filtering.getExcludeCategories());
            assertNotNull(filtering.getIncludeThreads());
            assertNotNull(filtering.getExcludeThreads());
        }

        @Test
        void testModifiableLists() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();

            // Lists should be modifiable
            filtering.getIncludeEvents().add("test.*");
            filtering.getExcludeEvents().add("debug.*");

            assertEquals(1, filtering.getIncludeEvents().size());
            assertEquals(1, filtering.getExcludeEvents().size());
        }

        @Test
        void testDuplicatePatterns() {
            EventConfig.FilteringConfig child = new EventConfig.FilteringConfig();
            child.getIncludeEvents().add("test.*");

            EventConfig.FilteringConfig parent = new EventConfig.FilteringConfig();
            parent.getIncludeEvents().add("test.*"); // Same pattern

            child.mergeWith(parent);

            // Should have duplicate (no deduplication)
            assertEquals(2, child.getIncludeEvents().size());
        }

        @Test
        void testWhitespaceInPatterns() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getIncludeEvents().add(" jdk.ThreadSleep ");

            assertEquals(" jdk.ThreadSleep ", filtering.getIncludeEvents().get(0));
            // Note: Whitespace trimming is handled by GlobMatcher, not config
        }
    }
}