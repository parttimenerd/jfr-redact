package me.bechberger.jfrredact.jfr.util;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

import static org.assertj.core.api.Assertions.*;
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
        assertThat(Files.exists(recordingPath)).as("Output file should exist").isTrue();
        return this;
    }

    public JFREventVerifier fileNotEmpty() throws IOException {
        assertThat(Files.size(recordingPath)).as("Output file should not be empty").isGreaterThan(0);
        return this;
    }

    public JFREventVerifier hasEvents() {
        assertThat(allEvents).as("Output should contain events").isNotEmpty();
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
        assertThat(hasEvent).as("Should not have events of type %s", eventTypeName).isFalse();
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
            assertThat(event.getString(fieldName)).as("Field %s should be %s", fieldName, expectedValue).isEqualTo(expectedValue);
            return this;
        }

        public SingleEventVerifier hasInt(String fieldName, int expectedValue) {
            assertThat(event.getInt(fieldName)).as("Field %s should be %d", fieldName, expectedValue).isEqualTo(expectedValue);
            return this;
        }

        public SingleEventVerifier hasLong(String fieldName, long expectedValue) {
            assertThat(event.getLong(fieldName)).as("Field %s should be %d", fieldName, expectedValue).isEqualTo(expectedValue);
            return this;
        }

        public SingleEventVerifier hasBoolean(String fieldName, boolean expectedValue) {
            assertThat(event.getBoolean(fieldName)).as("Field %s should be %s", fieldName, expectedValue).isEqualTo(expectedValue);
            return this;
        }

        public SingleEventVerifier hasFloat(String fieldName, float expectedValue, float delta) {
            assertThat(event.getFloat(fieldName)).as("Field %s should be %s", fieldName, expectedValue).isCloseTo(expectedValue, within(delta));
            return this;
        }

        public SingleEventVerifier hasDouble(String fieldName, double expectedValue, double delta) {
            assertThat(event.getDouble(fieldName)).as("Field %s should be %s", fieldName, expectedValue).isCloseTo(expectedValue, within(delta));
            return this;
        }

        public SingleEventVerifier hasByte(String fieldName, byte expectedValue) {
            assertThat(event.getByte(fieldName)).as("Field %s should be %s", fieldName, expectedValue).isEqualTo(expectedValue);
            return this;
        }

        public SingleEventVerifier hasShort(String fieldName, short expectedValue) {
            assertThat(event.getShort(fieldName)).as("Field %s should be %s", fieldName, expectedValue).isEqualTo(expectedValue);
            return this;
        }

        public SingleEventVerifier hasChar(String fieldName, char expectedValue) {
            assertThat(event.getChar(fieldName)).as("Field %s should be %s", fieldName, expectedValue).isEqualTo(expectedValue);
            return this;
        }

        public SingleEventVerifier fieldIsNull(String fieldName) {
            assertThat(event.getString(fieldName)).as("Field %s should be null", fieldName).isNull();
            return this;
        }

        public SingleEventVerifier fieldNotNull(String fieldName) {
            assertThat((Object)event.getValue(fieldName)).as("Field %s should not be null", fieldName).isNotNull();
            return this;
        }

        public SingleEventVerifier stringContains(String fieldName, String substring) {
            String value = event.getString(fieldName);
            assertThat(value).as("Field %s should contain '%s'", fieldName, substring).contains(substring);
            return this;
        }

        public SingleEventVerifier stringDoesNotContain(String fieldName, String substring) {
            String value = event.getString(fieldName);
            assertThat(value).as("Field %s should not contain '%s'", fieldName, substring).doesNotContain(substring);
            return this;
        }

        public SingleEventVerifier isRedacted(String fieldName) {
            String value = event.getString(fieldName);
            assertThat(value).as("Field %s should be redacted", fieldName).containsAnyOf("***", "<redacted:");
            return this;
        }

        public SingleEventVerifier intNotEquals(String fieldName, int notExpectedValue) {
            assertThat(event.getInt(fieldName)).as("Field %s should not be %d", fieldName, notExpectedValue).isNotEqualTo(notExpectedValue);
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