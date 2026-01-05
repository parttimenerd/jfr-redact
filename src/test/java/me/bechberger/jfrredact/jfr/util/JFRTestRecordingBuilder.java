package me.bechberger.jfrredact.jfr.util;

import jdk.jfr.Event;
import jdk.jfr.Recording;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static me.bechberger.jfrredact.jfr.util.JFRTestEvents.*;

/**
 * Builder for creating test JFR recordings with a fluent API.
 * Supports both standard test events and custom event classes.
 */
public class JFRTestRecordingBuilder {
    private final List<RunnableWithException> eventGenerators = new ArrayList<>();
    private final Set<Class<? extends Event>> customEventClasses = new HashSet<>();
    private final Path tempDir;
    private String name = "test-recording";

    public JFRTestRecordingBuilder(Path tempDir) {
        this.tempDir = tempDir;
    }

    public JFRTestRecordingBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Register a custom event class to be enabled during recording.
     * This allows tests to define their own event types.
     *
     * @param eventClass the custom event class to enable
     * @return this builder for chaining
     */
    public JFRTestRecordingBuilder withEventClass(Class<? extends Event> eventClass) {
        customEventClasses.add(eventClass);
        return this;
    }

    /**
     * Register multiple custom event classes to be enabled during recording.
     *
     * @param eventClasses the custom event classes to enable
     * @return this builder for chaining
     */
    @SafeVarargs
    public final JFRTestRecordingBuilder withEventClasses(Class<? extends Event>... eventClasses) {
        customEventClasses.addAll(Arrays.asList(eventClasses));
        return this;
    }

    public JFRTestRecordingBuilder addEvent(RunnableWithException eventGenerator) {
        eventGenerators.add(eventGenerator);
        return this;
    }

    public JFRTestRecordingBuilder addSimpleEvent(String message, int count, boolean flag) {
        return addEvent(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = message;
            event.count = count;
            event.flag = flag;
            event.commit();
        });
    }

    public JFRTestRecordingBuilder addSensitiveEvent(String username, String password, String email, String ip) {
        return addEvent(() -> {
            SensitiveDataEvent event = new SensitiveDataEvent();
            event.username = username;
            event.password = password;
            event.email = email;
            event.ipAddress = ip;
            event.commit();
        });
    }

    public JFRTestRecordingBuilder addNetworkEvent(String srcAddr, String dstAddr, int srcPort, int dstPort, String protocol) {
        return addEvent(() -> {
            NetworkEvent event = new NetworkEvent();
            event.sourceAddress = srcAddr;
            event.destinationAddress = dstAddr;
            event.sourcePort = srcPort;
            event.destinationPort = dstPort;
            event.protocol = protocol;
            event.commit();
        });
    }

    public JFRTestRecordingBuilder addComplexEvent(String stringField, int intField, long longField,
                                                     float floatField, double doubleField, boolean booleanField,
                                                     byte byteField, short shortField, char charField) {
        return addEvent(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = stringField;
            event.intField = intField;
            event.longField = longField;
            event.floatField = floatField;
            event.doubleField = doubleField;
            event.booleanField = booleanField;
            event.byteField = byteField;
            event.shortField = shortField;
            event.charField = charField;
            event.commit();
        });
    }

    public JFRTestRecordingBuilder addArrayEvent(String[] stringArray, int[] intArray, long[] longArray) {
        return addEvent(() -> {
            ArrayEvent event = new ArrayEvent();
            event.stringArray = stringArray;
            event.intArray = intArray;
            event.longArray = longArray;
            event.commit();
        });
    }

    public Path build() throws IOException {
        Path recordingPath = tempDir.resolve(name + ".jfr");

        try (Recording recording = new Recording()) {
            // Enable all standard test event types with threshold = 0 to capture all events
            recording.enable(SimpleEvent.class).withoutThreshold();
            recording.enable(SensitiveDataEvent.class).withoutThreshold();
            recording.enable(ComplexEvent.class).withoutThreshold();
            recording.enable(ArrayEvent.class).withoutThreshold();
            recording.enable(NetworkEvent.class).withoutThreshold();
            recording.enable(AnnotatedEvent.class).withoutThreshold();
            recording.enable(TimestampEvent.class).withoutThreshold();
            recording.enable(DataAmountEvent.class).withoutThreshold();
            recording.enable(RelationalEvent.class).withoutThreshold();
            recording.enable(ThreadEvent.class).withoutThreshold();
            recording.enable(PerformanceEvent.class).withoutThreshold();
            recording.enable(EnabledEvent.class).withoutThreshold();
            recording.enable(DisabledEvent.class).withoutThreshold();
            recording.enable(PeriodEvent.class).withoutThreshold();
            recording.enable(ContentTypeEvent.class).withoutThreshold();
            recording.enable(ComprehensiveDataAmountEvent.class).withoutThreshold();
            recording.enable(AllContentTypesEvent.class).withoutThreshold();

            // Enable custom event classes registered by tests
            for (Class<? extends Event> eventClass : customEventClasses) {
                recording.enable(eventClass).withoutThreshold();
            }

            recording.setToDisk(true);
            recording.setDestination(recordingPath);
            recording.start();

            for (RunnableWithException generator : eventGenerators) {
                try {
                    generator.run();
                } catch (Exception e) {
                    throw new IOException("Error generating event", e);
                }
            }

            recording.stop();
        }

        return recordingPath;
    }
}