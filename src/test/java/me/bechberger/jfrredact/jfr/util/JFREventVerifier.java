package me.bechberger.jfrredact.jfr.util;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fluent API for verifying events in JFR recordings.
 */
public class JFREventVerifier {
    private final Path recordingPath;
    private final List<RecordedEvent> allEvents;

    public JFREventVerifier(Path recordingPath) throws IOException {
        this.recordingPath = recordingPath;
        this.allEvents = RecordingFile.readAllEvents(recordingPath);
    }

    public JFREventVerifier fileExists() {
        assertTrue(Files.exists(recordingPath), "Output file should exist");
        return this;
    }

    public JFREventVerifier fileNotEmpty() throws IOException {
        assertTrue(Files.size(recordingPath) > 0, "Output file should not be empty");
        return this;
    }

    public JFREventVerifier hasEvents() {
        assertFalse(allEvents.isEmpty(), "Output should contain events");
        return this;
    }

    public JFREventVerifier hasTestEventCount(long expectedCount) {
        long actualCount = allEvents.stream()
                .filter(e -> e.getEventType().getName().startsWith("test."))
                .count();
        assertEquals(expectedCount, actualCount, "Test event count mismatch");
        return this;
    }

    public JFREventVerifier hasEventOfType(String eventTypeName, int expectedCount) {
        long actualCount = allEvents.stream()
                .filter(e -> e.getEventType().getName().equals(eventTypeName))
                .count();
        assertEquals(expectedCount, actualCount,
                "Expected " + expectedCount + " events of type " + eventTypeName);
        return this;
    }

    public JFREventVerifier hasNoEventOfType(String eventTypeName) {
        boolean hasEvent = allEvents.stream()
                .anyMatch(e -> e.getEventType().getName().equals(eventTypeName));
        assertFalse(hasEvent, "Should not have events of type " + eventTypeName);
        return this;
    }

    public SingleEventVerifier findEvent(String eventTypeName) {
        RecordedEvent event = allEvents.stream()
                .filter(e -> e.getEventType().getName().equals(eventTypeName))
                .findFirst()
                .orElse(null);
        assertNotNull(event, "Should find event of type " + eventTypeName);
        return new SingleEventVerifier(event, this);
    }

    public List<SingleEventVerifier> findAllEvents(String eventTypeName) {
        return allEvents.stream()
                .filter(e -> e.getEventType().getName().equals(eventTypeName))
                .map(e -> new SingleEventVerifier(e, this))
                .toList();
    }

    public JFREventVerifier verifyAllEvents(String eventTypeName, java.util.function.Consumer<SingleEventVerifier> verifier) {
        findAllEvents(eventTypeName).forEach(verifier);
        return this;
    }

    /**
     * Run custom assertions on all events.
     * Useful for complex assertions that need to examine multiple events together.
     */
    public JFREventVerifier allEvents(java.util.function.Consumer<List<RecordedEvent>> assertions) {
        assertions.accept(allEvents);
        return this;
    }

    public List<RecordedEvent> getAllEvents() {
        return allEvents;
    }

    public Path getRecordingPath() {
        return recordingPath;
    }

    /**
     * Verifier for individual event assertions.
     */
    public static class SingleEventVerifier {
        private final RecordedEvent event;
        private final JFREventVerifier parent;

        public SingleEventVerifier(RecordedEvent event) {
            this(event, null);
        }

        public SingleEventVerifier(RecordedEvent event, JFREventVerifier parent) {
            this.event = event;
            this.parent = parent;
        }

        /**
         * Return to the parent verifier to continue chaining assertions on other events.
         */
        public JFREventVerifier and() {
            if (parent == null) {
                throw new IllegalStateException("No parent verifier available. Use findEvent() on JFREventVerifier to enable .and() chaining.");
            }
            return parent;
        }

        public SingleEventVerifier hasString(String fieldName, String expectedValue) {
            assertEquals(expectedValue, event.getString(fieldName),
                    "Field " + fieldName + " should be " + expectedValue);
            return this;
        }

        public SingleEventVerifier hasInt(String fieldName, int expectedValue) {
            assertEquals(expectedValue, event.getInt(fieldName),
                    "Field " + fieldName + " should be " + expectedValue);
            return this;
        }

        public SingleEventVerifier hasLong(String fieldName, long expectedValue) {
            assertEquals(expectedValue, event.getLong(fieldName),
                    "Field " + fieldName + " should be " + expectedValue);
            return this;
        }

        public SingleEventVerifier hasBoolean(String fieldName, boolean expectedValue) {
            assertEquals(expectedValue, event.getBoolean(fieldName),
                    "Field " + fieldName + " should be " + expectedValue);
            return this;
        }

        public SingleEventVerifier hasFloat(String fieldName, float expectedValue, float delta) {
            assertEquals(expectedValue, event.getFloat(fieldName), delta,
                    "Field " + fieldName + " should be " + expectedValue);
            return this;
        }

        public SingleEventVerifier hasDouble(String fieldName, double expectedValue, double delta) {
            assertEquals(expectedValue, event.getDouble(fieldName), delta,
                    "Field " + fieldName + " should be " + expectedValue);
            return this;
        }

        public SingleEventVerifier hasByte(String fieldName, byte expectedValue) {
            assertEquals(expectedValue, event.getByte(fieldName),
                    "Field " + fieldName + " should be " + expectedValue);
            return this;
        }

        public SingleEventVerifier hasShort(String fieldName, short expectedValue) {
            assertEquals(expectedValue, event.getShort(fieldName),
                    "Field " + fieldName + " should be " + expectedValue);
            return this;
        }

        public SingleEventVerifier hasChar(String fieldName, char expectedValue) {
            assertEquals(expectedValue, event.getChar(fieldName),
                    "Field " + fieldName + " should be " + expectedValue);
            return this;
        }

        public SingleEventVerifier fieldIsNull(String fieldName) {
            assertNull(event.getString(fieldName), "Field " + fieldName + " should be null");
            return this;
        }

        public SingleEventVerifier fieldNotNull(String fieldName) {
            assertNotNull(event.getValue(fieldName), "Field " + fieldName + " should not be null");
            return this;
        }

        public SingleEventVerifier stringContains(String fieldName, String substring) {
            String value = event.getString(fieldName);
            assertTrue(value.contains(substring),
                    "Field " + fieldName + " should contain '" + substring + "' but was: " + value);
            return this;
        }

        public SingleEventVerifier stringDoesNotContain(String fieldName, String substring) {
            String value = event.getString(fieldName);
            assertFalse(value.contains(substring),
                    "Field " + fieldName + " should not contain '" + substring + "'");
            return this;
        }

        public SingleEventVerifier isRedacted(String fieldName) {
            String value = event.getString(fieldName);
            assertTrue(value.contains("***") || value.contains("<redacted:"),
                    "Field " + fieldName + " should be redacted");
            return this;
        }

        public SingleEventVerifier intNotEquals(String fieldName, int notExpectedValue) {
            assertNotEquals(notExpectedValue, event.getInt(fieldName),
                    "Field " + fieldName + " should not be " + notExpectedValue);
            return this;
        }

        public SingleEventVerifier run(Consumer<RecordedEvent> consumer) {
            consumer.accept(event);
            return this;
        }

        public RecordedEvent getEvent() {
            return event;
        }
    }
}