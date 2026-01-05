package me.bechberger.jfrredact.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import static org.assertj.core.api.Assertions.*;

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

            assertThat(filtering.getIncludeEvents()).isEmpty();
            assertThat(filtering.getExcludeEvents()).isEmpty();
            assertThat(filtering.getIncludeCategories()).isEmpty();
            assertThat(filtering.getExcludeCategories()).isEmpty();
            assertThat(filtering.getIncludeThreads()).isEmpty();
            assertThat(filtering.getExcludeThreads()).isEmpty();
            assertThat(filtering.hasAnyFilters()).isFalse();
        }

        @Test
        void testHasAnyFilters_WithIncludeEvents() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getIncludeEvents().add("jdk.*");

            assertThat(filtering.hasAnyFilters()).isTrue();
        }

        @Test
        void testHasAnyFilters_WithExcludeEvents() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getExcludeEvents().add("jdk.GC*");

            assertThat(filtering.hasAnyFilters()).isTrue();
        }

        @Test
        void testHasAnyFilters_WithCategories() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getIncludeCategories().add("Application");

            assertThat(filtering.hasAnyFilters()).isTrue();
        }

        @Test
        void testHasAnyFilters_WithThreads() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.getExcludeThreads().add("GC Thread*");

            assertThat(filtering.hasAnyFilters()).isTrue();
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
            assertEquals("test.*", filtering.getIncludeEvents().getFirst());
        }

        @Test
        void testMergeIncludeEvents() {
            EventConfig.FilteringConfig child = new EventConfig.FilteringConfig();
            child.getIncludeEvents().add("my.app.*");

            EventConfig.FilteringConfig parent = new EventConfig.FilteringConfig();
            parent.getIncludeEvents().add("jdk.*");

            child.mergeWith(parent);

            assertThat(child.getIncludeEvents()).hasSize(2).contains("my.app.*", "jdk.*");
        }

        @Test
        void testMergeExcludeEvents() {
            EventConfig.FilteringConfig child = new EventConfig.FilteringConfig();
            child.getExcludeEvents().add("test.Debug*");

            EventConfig.FilteringConfig parent = new EventConfig.FilteringConfig();
            parent.getExcludeEvents().add("jdk.GC*");

            child.mergeWith(parent);

            assertThat(child.getExcludeEvents()).hasSize(2).contains("test.Debug*", "jdk.GC*");
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

            assertThat(child.getIncludeCategories()).hasSize(2).containsExactlyInAnyOrder("Application", "JVM");
            assertThat(child.getExcludeCategories()).hasSize(2).containsExactlyInAnyOrder("System", "Flight Recorder");
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

            assertThat(child.getIncludeThreads()).hasSize(2).containsExactlyInAnyOrder("main", "test-*");
            assertThat(child.getExcludeThreads()).hasSize(2).containsExactlyInAnyOrder("worker-*", "GC Thread*");
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

            assertThat(child.getIncludeEvents()).hasSize(2);
            assertThat(child.getExcludeEvents()).hasSize(1);
            assertThat(child.getIncludeCategories()).hasSize(1);
            assertThat(child.getExcludeThreads()).hasSize(1);
            assertThat(child.hasAnyFilters()).isTrue();
        }
    }

    @Nested
    @DisplayName("EventConfig Integration")
    class EventConfigIntegrationTests {

        @Test
        void testDefaultEventConfig() {
            EventConfig config = new EventConfig();

            assertThat(config.isRemoveEnabled()).isTrue();
            assertThat(config.getRemovedTypes()).isNotEmpty();
            assertThat(config.getFiltering()).isNotNull();
            assertThat(config.getFiltering().hasAnyFilters()).isFalse();
        }

        @Test
        void testEventConfigMerge() {
            EventConfig child = new EventConfig();
            child.getRemovedTypes().clear();
            child.getRemovedTypes().add(RedactionConfig.PARENT_MARKER);  // Use $PARENT to include parent types
            child.getRemovedTypes().add("child.Event");
            child.getFiltering().getIncludeEvents().add("child.*");

            EventConfig parent = new EventConfig();
            parent.getRemovedTypes().clear();
            parent.getRemovedTypes().add("parent.Event");
            parent.getFiltering().getExcludeEvents().add("*.Debug");

            child.mergeWith(parent);

            // Check removed types are merged (parent.Event from $PARENT expansion + child.Event)
            assertEquals(2, child.getRemovedTypes().size());
            assertThat(child.getRemovedTypes()).contains("child.Event", "parent.Event");

            // Check filtering is merged (always additive)
            assertEquals(1, child.getFiltering().getIncludeEvents().size());
            assertEquals(1, child.getFiltering().getExcludeEvents().size());
        }

        @Test
        void testShouldRemove_SimpleTypes() {
            EventConfig config = new EventConfig();
            config.getRemovedTypes().clear();
            config.getRemovedTypes().add("jdk.OSInformation");
            config.getRemovedTypes().add("jdk.SystemProcess");

            assertThat(config.shouldRemove("jdk.OSInformation")).isTrue();
            assertThat(config.shouldRemove("jdk.SystemProcess")).isTrue();
            assertThat(config.shouldRemove("jdk.ThreadSleep")).isFalse();
        }

        @Test
        void testShouldRemove_DisabledRemoval() {
            EventConfig config = new EventConfig();
            config.setRemoveEnabled(false);
            config.getRemovedTypes().add("jdk.OSInformation");

            assertThat(config.shouldRemove("jdk.OSInformation")).isFalse();
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
            assertThat(includeEvents).hasSize(2).contains("test.*", "my.app.*");
        }

        @Test
        void testApplyCliOptions_ExcludeEvents() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
            cliOptions.setExcludeEvents(Arrays.asList("jdk.GC*", "*.Debug"));

            config.applyCliOptions(cliOptions);

            List<String> excludeEvents = config.getEvents().getFiltering().getExcludeEvents();
            assertThat(excludeEvents).hasSize(2).contains("jdk.GC*", "*.Debug");
        }

        @Test
        void testApplyCliOptions_Categories() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
            cliOptions.setIncludeCategories(Arrays.asList("Application", "JVM"));
            cliOptions.setExcludeCategories(List.of("Flight Recorder"));

            config.applyCliOptions(cliOptions);

            EventConfig.FilteringConfig filtering = config.getEvents().getFiltering();
            assertThat(filtering.getIncludeCategories()).hasSize(2);
            assertThat(filtering.getExcludeCategories()).hasSize(1);
        }

        @Test
        void testApplyCliOptions_Threads() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
            cliOptions.setIncludeThreads(Arrays.asList("main", "test-*"));
            cliOptions.setExcludeThreads(Arrays.asList("GC Thread*", "Service Thread"));

            config.applyCliOptions(cliOptions);

            EventConfig.FilteringConfig filtering = config.getEvents().getFiltering();
            assertThat(filtering.getIncludeThreads()).hasSize(2);
            assertThat(filtering.getExcludeThreads()).hasSize(2);
        }

        @Test
        void testApplyCliOptions_CombinedWithRemoveEvents() {
            RedactionConfig config = new RedactionConfig();
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();

            // Set both removal and filtering
            cliOptions.setRemoveEvents(List.of("jdk.OSInformation"));
            cliOptions.setIncludeEvents(List.of("jdk.*"));
            cliOptions.setExcludeEvents(List.of("jdk.GC*"));

            config.applyCliOptions(cliOptions);

            // Check removal types
            assertThat(config.getEvents().getRemovedTypes()).contains("jdk.OSInformation");

            // Check filtering
            EventConfig.FilteringConfig filtering = config.getEvents().getFiltering();
            assertThat(filtering.getIncludeEvents()).hasSize(1);
            assertThat(filtering.getExcludeEvents()).hasSize(1);
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
            assertThat(filtering.hasAnyFilters()).isFalse();
        }

        @Test
        void testApplyCliOptions_MultipleApplications() {
            RedactionConfig config = new RedactionConfig();

            // Apply first set of options
            RedactionConfig.CliOptions options1 = new RedactionConfig.CliOptions();
            options1.setIncludeEvents(List.of("test.*"));
            config.applyCliOptions(options1);

            // Apply second set of options
            RedactionConfig.CliOptions options2 = new RedactionConfig.CliOptions();
            options2.setIncludeEvents(List.of("my.app.*"));
            config.applyCliOptions(options2);

            // Both should be present (accumulative)
            List<String> includeEvents = config.getEvents().getFiltering().getIncludeEvents();
            assertThat(includeEvents).hasSize(2).contains("test.*", "my.app.*");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation")
    class EdgeCasesTests {

        @Test
        void testEmptyPatternLists() {
            EventConfig.FilteringConfig filtering = new EventConfig.FilteringConfig();
            filtering.setIncludeEvents(List.of());
            filtering.setExcludeEvents(List.of());

            assertThat(filtering.hasAnyFilters()).isFalse();
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

            assertEquals(" jdk.ThreadSleep ", filtering.getIncludeEvents().getFirst());
            // Note: Whitespace trimming is handled by GlobMatcher, not config
        }
    }
}