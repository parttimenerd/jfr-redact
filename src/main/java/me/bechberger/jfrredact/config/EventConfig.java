package me.bechberger.jfrredact.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for event removal and filtering.
 * Supports both simple event removal and jfr scrub-style filtering.
 */
public class EventConfig {

    @JsonProperty("remove_enabled")
    private boolean removeEnabled = true;

    @JsonProperty("removed_types")
    private List<String> removedTypes = new ArrayList<>(List.of(
        "jdk.OSInformation",
        "jdk.SystemProcess",
        "jdk.InitialEnvironmentVariable",
        "jdk.ProcessStart"
    ));

    /**
     * Filtering options similar to jfr scrub command.
     * See: https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html
     */
    @JsonProperty("filtering")
    private FilteringConfig filtering = new FilteringConfig();

    /**
     * Filtering configuration (jfr scrub-style).
     */
    public static class FilteringConfig {
        /** Select events matching an event name (comma-separated list, supports glob patterns) */
        @JsonProperty("include_events")
        private List<String> includeEvents = new ArrayList<>();

        /** Exclude events matching an event name (comma-separated list, supports glob patterns) */
        @JsonProperty("exclude_events")
        private List<String> excludeEvents = new ArrayList<>();

        /** Select events matching a category name (comma-separated list, supports glob patterns) */
        @JsonProperty("include_categories")
        private List<String> includeCategories = new ArrayList<>();

        /** Exclude events matching a category name (comma-separated list, supports glob patterns) */
        @JsonProperty("exclude_categories")
        private List<String> excludeCategories = new ArrayList<>();

        /** Select events matching a thread name (comma-separated list, supports glob patterns) */
        @JsonProperty("include_threads")
        private List<String> includeThreads = new ArrayList<>();

        /** Exclude events matching a thread name (comma-separated list, supports glob patterns) */
        @JsonProperty("exclude_threads")
        private List<String> excludeThreads = new ArrayList<>();

        public List<String> getIncludeEvents() { return includeEvents; }
        public void setIncludeEvents(List<String> includeEvents) { this.includeEvents = includeEvents; }

        public List<String> getExcludeEvents() { return excludeEvents; }
        public void setExcludeEvents(List<String> excludeEvents) { this.excludeEvents = excludeEvents; }

        public List<String> getIncludeCategories() { return includeCategories; }
        public void setIncludeCategories(List<String> includeCategories) { this.includeCategories = includeCategories; }

        public List<String> getExcludeCategories() { return excludeCategories; }
        public void setExcludeCategories(List<String> excludeCategories) { this.excludeCategories = excludeCategories; }

        public List<String> getIncludeThreads() { return includeThreads; }
        public void setIncludeThreads(List<String> includeThreads) { this.includeThreads = includeThreads; }

        public List<String> getExcludeThreads() { return excludeThreads; }
        public void setExcludeThreads(List<String> excludeThreads) { this.excludeThreads = excludeThreads; }

        public boolean hasAnyFilters() {
            return !includeEvents.isEmpty() || !excludeEvents.isEmpty() ||
                   !includeCategories.isEmpty() || !excludeCategories.isEmpty() ||
                   !includeThreads.isEmpty() || !excludeThreads.isEmpty();
        }

        public void mergeWith(FilteringConfig parent) {
            if (parent == null) return;

            // Filtering lists always append (no $PARENT marker needed)
            // This is different from other configs - filters are meant to be additive
            includeEvents.addAll(parent.getIncludeEvents());
            excludeEvents.addAll(parent.getExcludeEvents());
            includeCategories.addAll(parent.getIncludeCategories());
            excludeCategories.addAll(parent.getExcludeCategories());
            includeThreads.addAll(parent.getIncludeThreads());
            excludeThreads.addAll(parent.getExcludeThreads());
        }
    }

    // Getters and setters
    public boolean isRemoveEnabled() { return removeEnabled; }
    public void setRemoveEnabled(boolean removeEnabled) { this.removeEnabled = removeEnabled; }

    public List<String> getRemovedTypes() { return removedTypes; }
    public void setRemovedTypes(List<String> removedTypes) { this.removedTypes = removedTypes; }

    public FilteringConfig getFiltering() { return filtering; }
    public void setFiltering(FilteringConfig filtering) { this.filtering = filtering; }

    /**
     * Check if an event type should be removed.
     */
    public boolean shouldRemove(String eventType) {
        return removeEnabled && removedTypes.contains(eventType);
    }

    /**
     * Merge with parent configuration.
     * Uses $PARENT marker to control list inheritance.
     */
    public void mergeWith(EventConfig parent) {
        if (parent == null) return;

        // Expand $PARENT markers if present in removed_types
        List<String> expandedTypes = RedactionConfig.expandParentMarkers(removedTypes, parent.getRemovedTypes());
        if (expandedTypes != removedTypes) {
            // $PARENT was found and expanded
            removedTypes.clear();
            removedTypes.addAll(expandedTypes);
        }
        // If no $PARENT marker, child list overrides parent (no merge)

        // Merge filtering configuration (always additive)
        filtering.mergeWith(parent.getFiltering());
    }
}