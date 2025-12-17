package me.bechberger.jfrredact.testutil;

import jdk.jfr.Event;
import jdk.jfr.Recording;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating test JFR recordings with custom events.
 * Simplifies test setup for JFR-based tests.
 */
public class JFRRecordingBuilder {

    private final List<Event> events = new ArrayList<>();
    private Path outputPath;

    /**
     * Create a new JFR recording builder
     */
    public static JFRRecordingBuilder create() {
        return new JFRRecordingBuilder();
    }

    /**
     * Set the output path for the recording
     */
    public JFRRecordingBuilder outputTo(Path path) {
        this.outputPath = path;
        return this;
    }

    /**
     * Add an event to the recording
     */
    public JFRRecordingBuilder withEvent(Event event) {
        events.add(event);
        return this;
    }

    /**
     * Build and write the recording to a file
     */
    public Path build() throws IOException {
        if (outputPath == null) {
            outputPath = Files.createTempFile("test-recording-", ".jfr");
        }

        try (Recording recording = new Recording()) {
            recording.start();

            // Commit all events
            for (Event event : events) {
                event.commit();
            }

            recording.stop();
            recording.dump(outputPath);
        }

        return outputPath;
    }

    /**
     * Helper to create a simple key-value event
     */
    public static class KeyValueEvent extends Event {
        @jdk.jfr.Label("Key")
        public String key;

        @jdk.jfr.Label("Value")
        public String value;

        public KeyValueEvent(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Helper to create a user activity event
     */
    public static class UserActivityEvent extends Event {
        @jdk.jfr.Label("Username")
        public String username;

        @jdk.jfr.Label("Action")
        public String action;

        @jdk.jfr.Label("Resource")
        public String resource;

        public UserActivityEvent(String username, String action, String resource) {
            this.username = username;
            this.action = action;
            this.resource = resource;
        }
    }
}