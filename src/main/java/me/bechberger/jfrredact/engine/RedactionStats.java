package me.bechberger.jfrredact.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics tracking for redaction operations.
 */
public class RedactionStats {

    private final AtomicLong totalEvents = new AtomicLong(0);
    private final AtomicLong removedEvents = new AtomicLong(0);
    private final AtomicLong filteredThreadEvents = new AtomicLong(0);
    private final AtomicLong redactedFields = new AtomicLong(0);
    private final Map<String, AtomicLong> eventTypeStats = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> redactedFieldNames = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> redactionTypeStats = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> removedEventTypes = new ConcurrentHashMap<>();

    public void recordEvent(String eventType) {
        totalEvents.incrementAndGet();
        eventTypeStats.computeIfAbsent(eventType, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordRemovedEvent(String eventType) {
        removedEvents.incrementAndGet();
        removedEventTypes.computeIfAbsent(eventType, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordFilteredThreadEvent() {
        filteredThreadEvents.incrementAndGet();
    }

    public void recordRedactedField(String fieldName) {
        redactedFields.incrementAndGet();
        redactedFieldNames.computeIfAbsent(fieldName, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Record a redaction by type (email, ip, property, path, uuid, etc.)
     */
    public void recordRedactionType(String redactionType) {
        redactionTypeStats.computeIfAbsent(redactionType, k -> new AtomicLong(0)).incrementAndGet();
    }

    public long getTotalEvents() {
        return totalEvents.get();
    }

    public long getRemovedEvents() {
        return removedEvents.get();
    }

    public long getFilteredThreadEvents() {
        return filteredThreadEvents.get();
    }

    public long getRedactedFields() {
        return redactedFields.get();
    }

    public long getProcessedEvents() {
        return totalEvents.get() - removedEvents.get() - filteredThreadEvents.get();
    }

    public Map<String, AtomicLong> getEventTypeStats() {
        return eventTypeStats;
    }

    public Map<String, AtomicLong> getRedactedFieldNames() {
        return redactedFieldNames;
    }

    public Map<String, AtomicLong> getRedactionTypeStats() {
        return redactionTypeStats;
    }

    public Map<String, AtomicLong> getRemovedEventTypes() {
        return removedEventTypes;
    }

    /**
     * Print statistics to stdout.
     */
    public void print() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Redaction Statistics");
        System.out.println("=".repeat(70));
        System.out.println();

        System.out.println("Events:");
        System.out.println("  Total events read:        " + totalEvents.get());
        System.out.println("  Events processed:         " + getProcessedEvents());
        System.out.println("  Events removed:           " + removedEvents.get());
        System.out.println("  Thread filtered events:   " + filteredThreadEvents.get());
        System.out.println();

        if (!removedEventTypes.isEmpty() && removedEvents.get() > 0) {
            System.out.println("Removed event types:");
            removedEventTypes.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(10)
                .forEach(entry ->
                    System.out.printf("  %-50s %,d%n", entry.getKey(), entry.getValue().get())
                );
            System.out.println();
        }

        System.out.println("Redactions:");
        System.out.println("  Total fields redacted:    " + redactedFields.get());
        System.out.println();

        if (!redactionTypeStats.isEmpty()) {
            System.out.println("Redaction types:");
            redactionTypeStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .forEach(entry ->
                    System.out.printf("  %-30s %,d%n", entry.getKey(), entry.getValue().get())
                );
            System.out.println();
        }

        if (!redactedFieldNames.isEmpty() && redactedFields.get() > 0) {
            System.out.println("Top redacted fields:");
            redactedFieldNames.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(10)
                .forEach(entry ->
                    System.out.printf("  %-30s %,d%n", entry.getKey(), entry.getValue().get())
                );
            System.out.println();
        }

        if (!eventTypeStats.isEmpty() && totalEvents.get() > 0) {
            System.out.println("Top event types processed:");
            eventTypeStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(10)
                .forEach(entry ->
                    System.out.printf("  %-50s %,d%n", entry.getKey(), entry.getValue().get())
                );
        }

        System.out.println();
        System.out.println("=".repeat(70));
    }
}