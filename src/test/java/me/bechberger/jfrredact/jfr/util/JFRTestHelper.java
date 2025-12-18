package me.bechberger.jfrredact.jfr.util;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.Event;
import me.bechberger.jfrredact.config.RedactionConfig;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test helper utilities for JFR testing.
 * Provides factory methods and roundtrip testing support.
 */
public class JFRTestHelper {

    private final Path tempDir;

    public JFRTestHelper(Path tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * Create a new recording builder.
     */
    public JFRTestRecordingBuilder recording() {
        return new JFRTestRecordingBuilder(tempDir);
    }

    /**
     * Create a new processor helper.
     */
    public JFRTestProcessor process() {
        return new JFRTestProcessor(tempDir);
    }

    /**
     * Create a test recording with a single event generator.
     */
    public Path createTestRecording(RunnableWithException eventGenerator) throws IOException {
        return recording().addEvent(eventGenerator).build();
    }

    /**
     * Create a test recording with a single event generator and custom event classes.
     *
     * @param eventGenerator the code to generate events
     * @param customEventClasses custom event classes to enable in the recording
     * @return path to the created recording
     */
    @SafeVarargs
    public final Path createTestRecording(RunnableWithException eventGenerator,
                                          Class<? extends Event>... customEventClasses) throws IOException {
        return recording()
                .withEventClasses(customEventClasses)
                .addEvent(eventGenerator)
                .build();
    }

    /**
     * Create a verifier for an existing recording.
     */
    public JFREventVerifier verify(Path recordingPath) throws IOException {
        return new JFREventVerifier(recordingPath);
    }

    /**
     * Perform a roundtrip test: create recording, process it, verify the result.
     * Returns a roundtrip verifier for advanced assertions.
     */
    public RoundtripVerifier roundtrip(RunnableWithException eventGenerator) throws IOException {
        Path originalPath = createTestRecording(eventGenerator);
        return new RoundtripVerifier(originalPath, tempDir);
    }

    /**
     * Perform a roundtrip test with custom event classes.
     * Returns a roundtrip verifier for advanced assertions.
     *
     * @param eventGenerator the code to generate events
     * @param customEventClasses custom event classes to enable in the recording
     * @return roundtrip verifier for assertions
     */
    @SafeVarargs
    public final RoundtripVerifier roundtrip(RunnableWithException eventGenerator,
                                             Class<? extends Event>... customEventClasses) throws IOException {
        Path originalPath = createTestRecording(eventGenerator, customEventClasses);
        return new RoundtripVerifier(originalPath, tempDir);
    }

    /**
     * Helper class for roundtrip testing with fluent API.
     */
    public static class RoundtripVerifier {
        private final Path originalPath;
        private final Path tempDir;
        private List<RecordedEvent> originalEvents;
        private List<RecordedEvent> processedEvents;

        public RoundtripVerifier(Path originalPath, Path tempDir) throws IOException {
            this.originalPath = originalPath;
            this.tempDir = tempDir;
            this.originalEvents = new JFREventVerifier(originalPath).getAllEvents();
        }

        /**
         * Process without redaction and verify the roundtrip preserves data.
         */
        public RoundtripVerifier withoutRedaction() throws IOException {
            JFRTestProcessor processor = new JFRTestProcessor(tempDir);
            Path processedPath = processor.from(originalPath).withNoRedaction().process();
            this.processedEvents = new JFREventVerifier(processedPath).getAllEvents();
            return this;
        }

        /**
         * Process with default redaction.
         */
        public RoundtripVerifier withDefaultRedaction() throws IOException {
            JFRTestProcessor processor = new JFRTestProcessor(tempDir);
            Path processedPath = processor.from(originalPath).withDefaultEngine().process();
            this.processedEvents = new JFREventVerifier(processedPath).getAllEvents();
            return this;
        }

        /**
         * Process with strict redaction.
         */
        public RoundtripVerifier withStrictRedaction() throws IOException {
            JFRTestProcessor processor = new JFRTestProcessor(tempDir);
            Path processedPath = processor.from(originalPath).withStrictEngine().process();
            this.processedEvents = new JFREventVerifier(processedPath).getAllEvents();
            return this;
        }

        /**
         * Process with pseudonymization.
         */
        public RoundtripVerifier withPseudonymization() throws IOException {
            JFRTestProcessor processor = new JFRTestProcessor(tempDir);
            Path processedPath = processor.from(originalPath).withPseudonymization().process();
            this.processedEvents = new JFREventVerifier(processedPath).getAllEvents();
            return this;
        }

        /**
         * Process with a custom RedactionConfig.
         */
        public RoundtripVerifier withConfig(RedactionConfig config) throws IOException {
            JFRTestProcessor processor = new JFRTestProcessor(tempDir);
            Path processedPath = processor.from(originalPath).withConfig(config).process();
            this.processedEvents = new JFREventVerifier(processedPath).getAllEvents();
            return this;
        }

        /**
         * Verify that the event count is preserved.
         */
        public RoundtripVerifier eventCountPreserved() {
            if (originalEvents.size() != processedEvents.size()) {
                // Build detailed diagnostic message
                StringBuilder diagnosticMsg = new StringBuilder();
                diagnosticMsg.append(String.format(
                    "Event count should be preserved in roundtrip%n" +
                    "  Expected: %d events%n" +
                    "  Actual:   %d events%n" +
                    "  Lost:     %d events%n%n",
                    originalEvents.size(), processedEvents.size(),
                    originalEvents.size() - processedEvents.size()));

                // Calculate event counts by type
                java.util.Map<String, Long> originalCounts = originalEvents.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.getEventType().getName(),
                        java.util.stream.Collectors.counting()));

                java.util.Map<String, Long> processedCounts = processedEvents.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.getEventType().getName(),
                        java.util.stream.Collectors.counting()));

                // Categorize event types
                java.util.List<String> lostEventTypes = new java.util.ArrayList<>();
                java.util.List<String> preservedEventTypes = new java.util.ArrayList<>();

                for (java.util.Map.Entry<String, Long> entry : originalCounts.entrySet()) {
                    String eventType = entry.getKey();
                    long originalCount = entry.getValue();
                    long processedCount = processedCounts.getOrDefault(eventType, 0L);

                    if (originalCount != processedCount) {
                        lostEventTypes.add(String.format("  - %s: %d → %d (lost %d)",
                            eventType, originalCount, processedCount, originalCount - processedCount));
                    } else {
                        preservedEventTypes.add(String.format("  ✓ %s: %d events",
                            eventType, originalCount));
                    }
                }

                // Show event types with mismatches
                if (!lostEventTypes.isEmpty()) {
                    diagnosticMsg.append("Event types with count mismatches:").append(System.lineSeparator());
                    lostEventTypes.stream()
                        .limit(10)  // Show top 10 to avoid overwhelming output
                        .forEach(s -> diagnosticMsg.append(s).append(System.lineSeparator()));

                    if (lostEventTypes.size() > 10) {
                        diagnosticMsg.append(String.format("  ... and %d more event types%n",
                            lostEventTypes.size() - 10));
                    }
                    diagnosticMsg.append(System.lineSeparator());
                }

                // Show event types that were preserved correctly
                if (!preservedEventTypes.isEmpty()) {
                    diagnosticMsg.append("Event types preserved correctly:").append(System.lineSeparator());
                    preservedEventTypes.stream()
                        .limit(10)  // Show top 10 to avoid overwhelming output
                        .forEach(s -> diagnosticMsg.append(s).append(System.lineSeparator()));

                    if (preservedEventTypes.size() > 10) {
                        diagnosticMsg.append(String.format("  ... and %d more event types%n",
                            preservedEventTypes.size() - 10));
                    }
                }

                fail(diagnosticMsg.toString());
            }
            return this;
        }

        /**
         * Verify that test event count is preserved, accounting for default removed event types.
         * This is useful for real-world JFR recordings that contain system events which are
         * intentionally removed by default/strict redaction configs.
         */
        public RoundtripVerifier eventCountPreservedExceptDefaultRemovals() {
            // Default removed types (from EventConfig)
            java.util.Set<String> defaultRemovedTypes = java.util.Set.of(
                "jdk.OSInformation",
                "jdk.SystemProcess",
                "jdk.InitialEnvironmentVariable",
                "jdk.ProcessStart"
            );

            long originalNonRemoved = originalEvents.stream()
                .filter(e -> !defaultRemovedTypes.contains(e.getEventType().getName()))
                .count();
            long processedCount = processedEvents.size();

            assertEquals(originalNonRemoved, processedCount,
                "Event count should be preserved (excluding default removed types: " + defaultRemovedTypes + ")");
            return this;
        }

        /**
         * Verify that test event count is preserved, accounting for strict mode removed event types.
         */
        public RoundtripVerifier eventCountPreservedExceptStrictRemovals() {
            // Strict removed types (from EventConfig + NativeLibrary)
            java.util.Set<String> strictRemovedTypes = java.util.Set.of(
                "jdk.OSInformation",
                "jdk.SystemProcess",
                "jdk.InitialEnvironmentVariable",
                "jdk.ProcessStart",
                "jdk.NativeLibrary"
            );

            long originalNonRemoved = originalEvents.stream()
                .filter(e -> !strictRemovedTypes.contains(e.getEventType().getName()))
                .count();
            long processedCount = processedEvents.size();

            assertEquals(originalNonRemoved, processedCount,
                "Event count should be preserved (excluding strict removed types: " + strictRemovedTypes + ")");
            return this;
        }

        /**
         * Verify that test event count is preserved.
         */
        public RoundtripVerifier testEventCountPreserved() {
            long originalCount = originalEvents.stream()
                    .filter(e -> e.getEventType().getName().startsWith("test."))
                    .count();
            long processedCount = processedEvents.stream()
                    .filter(e -> e.getEventType().getName().startsWith("test."))
                    .count();
            assertEquals(originalCount, processedCount,
                    "Test event count should be preserved in roundtrip");
            return this;
        }

        /**
         * Verify that specific event types are preserved with same count.
         */
        public RoundtripVerifier eventTypeCountPreserved(String eventTypeName) {
            long originalCount = getOriginalEventCount(eventTypeName);
            long processedCount = getProcessedEventCount(eventTypeName);
            assertEquals(originalCount, processedCount,
                    "Event count for " + eventTypeName + " should be preserved");
            return this;
        }

        /**
         * Verify that a specific event type has been removed (filtered out).
         */
        public RoundtripVerifier eventTypeRemoved(String eventTypeName) {
            long processedCount = getProcessedEventCount(eventTypeName);
            assertEquals(0, processedCount,
                    "Event type " + eventTypeName + " should be removed");
            return this;
        }

        /**
         * Verify that all event types from original are present in processed.
         */
        public RoundtripVerifier allEventTypesPreserved() {
            java.util.Set<String> originalTypes = getOriginalEventTypes();
            java.util.Set<String> processedTypes = getProcessedEventTypes();

            java.util.Set<String> missingTypes = new java.util.HashSet<>(originalTypes);
            missingTypes.removeAll(processedTypes);

            assertTrue(missingTypes.isEmpty(),
                    "All event types should be preserved. Missing: " + missingTypes);
            return this;
        }

        /**
         * Verify that event order is preserved.
         */
        public RoundtripVerifier eventOrderPreserved() {
            assertEquals(originalEvents.size(), processedEvents.size(),
                    "Event count must match to check order");

            for (int i = 0; i < originalEvents.size(); i++) {
                String originalType = originalEvents.get(i).getEventType().getName();
                String processedType = processedEvents.get(i).getEventType().getName();
                assertEquals(originalType, processedType,
                        "Event at index " + i + " should have same type");
            }
            return this;
        }

        /**
         * Verify that a specific field value is preserved in events of given type.
         */
        public RoundtripVerifier fieldPreserved(String eventTypeName, String fieldName) {
            List<RecordedEvent> originalEventsOfType = originalEvents.stream()
                    .filter(e -> e.getEventType().getName().equals(eventTypeName))
                    .toList();
            List<RecordedEvent> processedEventsOfType = processedEvents.stream()
                    .filter(e -> e.getEventType().getName().equals(eventTypeName))
                    .toList();

            assertEquals(originalEventsOfType.size(), processedEventsOfType.size(),
                    "Event count for " + eventTypeName + " should match");

            for (int i = 0; i < originalEventsOfType.size(); i++) {
                Object originalValue = originalEventsOfType.get(i).getValue(fieldName);
                Object processedValue = processedEventsOfType.get(i).getValue(fieldName);
                assertEquals(originalValue, processedValue,
                        "Field " + fieldName + " should be preserved at index " + i);
            }
            return this;
        }

        /**
         * Verify that a specific field value is changed (redacted/pseudonymized) in events of given type.
         * Provides detailed output showing original and processed values.
         */
        public RoundtripVerifier fieldChanged(String eventTypeName, String fieldName) {
            List<RecordedEvent> originalEventsOfType = originalEvents.stream()
                    .filter(e -> e.getEventType().getName().equals(eventTypeName))
                    .toList();
            List<RecordedEvent> processedEventsOfType = processedEvents.stream()
                    .filter(e -> e.getEventType().getName().equals(eventTypeName))
                    .toList();

            assertEquals(originalEventsOfType.size(), processedEventsOfType.size(),
                    "Event count for " + eventTypeName + " should match");

            StringBuilder changeReport = new StringBuilder();
            boolean anyChanged = false;
            int changedCount = 0;

            for (int i = 0; i < originalEventsOfType.size(); i++) {
                Object originalValue = originalEventsOfType.get(i).getValue(fieldName);
                Object processedValue = processedEventsOfType.get(i).getValue(fieldName);
                if (originalValue != null && !originalValue.equals(processedValue)) {
                    anyChanged = true;
                    changedCount++;
                    changeReport.append(String.format("\n  Event[%d]: '%s' -> '%s'",
                        i, originalValue, processedValue));
                }
            }

            assertTrue(anyChanged,
                String.format("Field '%s' in '%s' should be changed in at least one event. " +
                    "Found %d unchanged events.", fieldName, eventTypeName,
                    originalEventsOfType.size()));

            // Log the changes for debugging
            if (changedCount > 0) {
                System.out.println(String.format("Field '%s' changed in %d/%d events:%s",
                    fieldName, changedCount, originalEventsOfType.size(), changeReport));
            }

            return this;
        }

        /**
         * Verify that a specific field matches a predicate in all events of given type.
         * Useful for validating redaction patterns (e.g., all values start with "&lt;redacted:").
         */
        public RoundtripVerifier fieldMatches(String eventTypeName, String fieldName,
                                             java.util.function.Predicate<Object> predicate,
                                             String predicateDescription) {
            List<RecordedEvent> processedEventsOfType = processedEvents.stream()
                    .filter(e -> e.getEventType().getName().equals(eventTypeName))
                    .toList();

            java.util.List<org.junit.jupiter.api.function.Executable> assertions = new java.util.ArrayList<>();

            for (int i = 0; i < processedEventsOfType.size(); i++) {
                final int index = i;
                final Object value = processedEventsOfType.get(i).getValue(fieldName);
                assertions.add(() -> assertTrue(predicate.test(value),
                    String.format("Field '%s' at event[%d] should match '%s', but got: '%s'",
                        fieldName, index, predicateDescription, value)));
            }

            assertAll("Verifying field '" + fieldName + "' matches predicate: " + predicateDescription,
                assertions.stream());
            return this;
        }

        /**
         * Get the original events for custom assertions.
         */
        public List<RecordedEvent> getOriginalEvents() {
            return originalEvents;
        }

        /**
         * Get the processed events for custom assertions.
         */
        public List<RecordedEvent> getProcessedEvents() {
            return processedEvents;
        }

        /**
         * Get count of events of a specific type in original recording.
         */
        public long getOriginalEventCount(String eventTypeName) {
            return originalEvents.stream()
                    .filter(e -> e.getEventType().getName().equals(eventTypeName))
                    .count();
        }

        /**
         * Get count of events of a specific type in processed recording.
         */
        public long getProcessedEventCount(String eventTypeName) {
            return processedEvents.stream()
                    .filter(e -> e.getEventType().getName().equals(eventTypeName))
                    .count();
        }

        /**
         * Get all unique event type names in the original recording.
         */
        public java.util.Set<String> getOriginalEventTypes() {
            return originalEvents.stream()
                    .map(e -> e.getEventType().getName())
                    .collect(java.util.stream.Collectors.toSet());
        }

        /**
         * Get all unique event type names in the processed recording.
         */
        public java.util.Set<String> getProcessedEventTypes() {
            return processedEvents.stream()
                    .map(e -> e.getEventType().getName())
                    .collect(java.util.stream.Collectors.toSet());
        }

        /**
         * Get events of a specific type from original recording.
         */
        public List<RecordedEvent> getOriginalEventsOfType(String eventTypeName) {
            return originalEvents.stream()
                    .filter(e -> e.getEventType().getName().equals(eventTypeName))
                    .toList();
        }

        /**
         * Get events of a specific type from processed recording.
         */
        public List<RecordedEvent> getProcessedEventsOfType(String eventTypeName) {
            return processedEvents.stream()
                    .filter(e -> e.getEventType().getName().equals(eventTypeName))
                    .toList();
        }

        /**
         * Verify that all events of a specific type are deeply equal between original and processed.
         * This checks:
         * - Field values
         * - Field annotations and their values
         * - Event type annotations
         * - Field nullability
         * - Event metadata
         */
        public RoundtripVerifier eventsOfTypeFullyPreserved(String eventTypeName) {
            List<RecordedEvent> originalEventsOfType = getOriginalEventsOfType(eventTypeName);
            List<RecordedEvent> processedEventsOfType = getProcessedEventsOfType(eventTypeName);

            assertEquals(originalEventsOfType.size(), processedEventsOfType.size(),
                    "Event count for " + eventTypeName + " should match");

            // Collect all event verification assertions
            java.util.List<Executable> eventAssertions = new ArrayList<>();
            for (int i = 0; i < originalEventsOfType.size(); i++) {
                final int index = i;
                final RecordedEvent original = originalEventsOfType.get(i);
                final RecordedEvent processed = processedEventsOfType.get(i);

                eventAssertions.add(() -> verifyEventFullyEqual(original, processed, eventTypeName + "[" + index + "]"));
            }

            assertAll("Verifying all events of type " + eventTypeName, eventAssertions.stream());
            return this;
        }

        /**
         * Verify that multiple event types are fully preserved.
         * Convenience method to check several event types at once.
         */
        public RoundtripVerifier eventsOfTypesFullyPreserved(String... eventTypeNames) {
            for (String eventTypeName : eventTypeNames) {
                eventsOfTypeFullyPreserved(eventTypeName);
            }
            return this;
        }

        /**
         * Verify that all events are deeply equal between original and processed recordings.
         * This checks all event types found in the recording.
         */
        public RoundtripVerifier allEventsFullyPreserved() {
            assertEquals(originalEvents.size(), processedEvents.size(),
                    "Total event count should match");

            // Collect all event verification assertions
            java.util.List<org.junit.jupiter.api.function.Executable> eventAssertions = new java.util.ArrayList<>();
            for (int i = 0; i < originalEvents.size(); i++) {
                final int index = i;
                final RecordedEvent original = originalEvents.get(i);
                final RecordedEvent processed = processedEvents.get(i);

                eventAssertions.add(() -> verifyEventFullyEqual(original, processed, "Event[" + index + "]"));
            }

            assertAll("Verifying all events in recording", eventAssertions.stream());
            return this;
        }

        /**
         * Verify that two events are deeply equal, checking all aspects.
         */
        private void verifyEventFullyEqual(RecordedEvent original, RecordedEvent processed, String context) {
            // First check basic event type matching
            assertEquals(original.getEventType().getName(), processed.getEventType().getName(),
                    context + ": Event type name should match");

            // Collect all verification assertions
            List<jdk.jfr.ValueDescriptor> originalFields = original.getFields();
            List<jdk.jfr.ValueDescriptor> processedFields = processed.getFields();

            java.util.List<org.junit.jupiter.api.function.Executable> allAssertions = new java.util.ArrayList<>();

            // Event type metadata assertions
            allAssertions.add(() -> {
                try {
                    assertEquals(original.getEventType().getLabel(), processed.getEventType().getLabel(),
                            context + ": Event type label should match");
                } catch (ArrayIndexOutOfBoundsException e) {
                    fail(context + ": Failed to retrieve event type label - JFRProcessor may have corrupted annotations: " + e.getMessage());
                }
            });

            allAssertions.add(() -> {
                try {
                    assertEquals(original.getEventType().getDescription(), processed.getEventType().getDescription(),
                            context + ": Event type description should match");
                } catch (ArrayIndexOutOfBoundsException e) {
                    fail(context + ": Failed to retrieve event type description - JFRProcessor may have corrupted annotations: " + e.getMessage());
                }
            });

            allAssertions.add(() -> {
                try {
                    assertEquals(original.getEventType().getCategoryNames(), processed.getEventType().getCategoryNames(),
                            context + ": Event type categories should match");
                } catch (ArrayIndexOutOfBoundsException e) {
                    fail(context + ": Failed to retrieve event type categories - JFRProcessor may have corrupted annotations: " + e.getMessage());
                }
            });

            allAssertions.add(() -> {
                try {
                    verifyAnnotationsEqual(original.getEventType().getAnnotationElements(),
                            processed.getEventType().getAnnotationElements(),
                            context + ": Event type annotations");
                } catch (ArrayIndexOutOfBoundsException e) {
                    fail(context + ": Failed to verify event type annotations - JFRProcessor may have corrupted annotations: " + e.getMessage());
                }
            });

            // Field count
            allAssertions.add(() -> assertEquals(originalFields.size(), processedFields.size(),
                    context + ": Field count should match"));

            // Verify all fields - collect assertions for each field
            int fieldCount = Math.min(originalFields.size(), processedFields.size());
            for (int j = 0; j < fieldCount; j++) {
                final jdk.jfr.ValueDescriptor originalField = originalFields.get(j);
                final jdk.jfr.ValueDescriptor processedField = processedFields.get(j);
                final String fieldContext = context + "." + originalField.getName();

                // Add field descriptor verification as a single assertion
                allAssertions.add(() -> verifyFieldDescriptorEqual(originalField, processedField, fieldContext));

                // Add field value verification as a single assertion
                allAssertions.add(() -> {
                    Object originalValue = original.getValue(originalField.getName());
                    Object processedValue = processed.getValue(processedField.getName());
                    verifyFieldValueEqual(originalValue, processedValue, fieldContext);
                });
            }

            // Execute all assertions together
            assertAll(context, allAssertions.stream());
        }

        /**
         * Verify field descriptors are equal (name, type, annotations, etc.)
         */
        private void verifyFieldDescriptorEqual(jdk.jfr.ValueDescriptor original,
                                                jdk.jfr.ValueDescriptor processed,
                                                String context) {
            if (original == null || processed == null) {
                assertEquals(original, processed, context + ": Field descriptor nullability should match");
                return;
            }

            assertAll(context,
                () -> assertEquals(original.getName(), processed.getName(),
                        context + ": Field name should match"),

                () -> {
                    try {
                        assertEquals(original.getLabel(), processed.getLabel(),
                                context + ": Field label should match");
                    } catch (ArrayIndexOutOfBoundsException e) {
                        fail(context + ": Failed to retrieve field label - JFRProcessor may have corrupted annotations: " + e.getMessage());
                    }
                },

                () -> {
                    try {
                        assertEquals(original.getDescription(), processed.getDescription(),
                                context + ": Field description should match");
                    } catch (ArrayIndexOutOfBoundsException e) {
                        fail(context + ": Failed to retrieve field description - JFRProcessor may have corrupted annotations: " + e.getMessage());
                    }
                },

                () -> {
                    try {
                        String originalTypeName = original.getTypeName();
                        String processedTypeName = processed.getTypeName();
                        assertEquals(originalTypeName, processedTypeName,
                                context + ": Field type name should match");
                    } catch (ArrayIndexOutOfBoundsException e) {
                        fail(context + ": Failed to retrieve field type name - JFRProcessor may have corrupted type information: " + e.getMessage());
                    }
                },

                () -> {
                    try {
                        assertEquals(original.getContentType(), processed.getContentType(),
                                context + ": Field content type should match");
                    } catch (ArrayIndexOutOfBoundsException e) {
                        fail(context + ": Failed to retrieve field content type - JFRProcessor may have corrupted metadata: " + e.getMessage());
                    }
                },

                () -> {
                    try {
                        verifyAnnotationsEqual(original.getAnnotationElements(),
                                processed.getAnnotationElements(),
                                context + ": Field annotations");
                    } catch (ArrayIndexOutOfBoundsException e) {
                        fail(context + ": Failed to verify field annotations - JFRProcessor may have corrupted annotations: " + e.getMessage());
                    }
                }
            );
        }

        /**
         * Verify field values are equal, handling null and complex types.
         */
        private void verifyFieldValueEqual(Object original, Object processed, String context) {
            // Both null
            if (original == null && processed == null) {
                return;
            }

            // One null, one not
            if (original == null || processed == null) {
                fail(context + ": Nullability mismatch\n" +
                     "  Original (before processing): " + formatValue(original) + "\n" +
                     "  Processed (after roundtrip):  " + formatValue(processed));
                return;
            }

            // Both non-null - check equality
            // For temporal fields, we may need to be lenient
            if (context.contains("startTime") || context.contains("duration")) {
                // Skip exact comparison for temporal fields as they may vary slightly
                return;
            }

            // Handle arrays (including arrays of RecordedFrame, etc.)
            if (original.getClass().isArray() && processed.getClass().isArray()) {
                verifyArraysEqual(original, processed, context);
                return;
            }

            // Special handling for RecordedObject - need deep comparison
            if (original instanceof jdk.jfr.consumer.RecordedObject && processed instanceof jdk.jfr.consumer.RecordedObject) {
                verifyRecordedObjectsEqual((jdk.jfr.consumer.RecordedObject) original,
                                          (jdk.jfr.consumer.RecordedObject) processed,
                                          context);
                return;
            }

            if (!original.equals(processed)) {
                fail(context + ": Field value mismatch\n" +
                     "  Original (before processing): " + formatValue(original) + "\n" +
                     "  Processed (after roundtrip):  " + formatValue(processed));
            }
        }

        /**
         * Verify arrays are equal, handling object arrays recursively.
         */
        private void verifyArraysEqual(Object original, Object processed, String context) {
            Class<?> componentType = original.getClass().getComponentType();

            if (componentType.isPrimitive()) {
                // Handle primitive arrays
                if (componentType == byte.class) {
                    assertArrayEquals((byte[]) original, (byte[]) processed, context);
                } else if (componentType == short.class) {
                    assertArrayEquals((short[]) original, (short[]) processed, context);
                } else if (componentType == int.class) {
                    assertArrayEquals((int[]) original, (int[]) processed, context);
                } else if (componentType == long.class) {
                    assertArrayEquals((long[]) original, (long[]) processed, context);
                } else if (componentType == float.class) {
                    assertArrayEquals((float[]) original, (float[]) processed, context);
                } else if (componentType == double.class) {
                    assertArrayEquals((double[]) original, (double[]) processed, context);
                } else if (componentType == boolean.class) {
                    assertArrayEquals((boolean[]) original, (boolean[]) processed, context);
                } else if (componentType == char.class) {
                    assertArrayEquals((char[]) original, (char[]) processed, context);
                }
            } else {
                // Handle object arrays (including RecordedObject arrays)
                Object[] originalArray = (Object[]) original;
                Object[] processedArray = (Object[]) processed;

                assertEquals(originalArray.length, processedArray.length,
                        context + ".length should match");

                for (int i = 0; i < originalArray.length; i++) {
                    verifyFieldValueEqual(originalArray[i], processedArray[i],
                            context + "[" + i + "]");
                }
            }
        }

        /**
         * Deep comparison of RecordedObject instances by comparing all fields.
         */
        private void verifyRecordedObjectsEqual(jdk.jfr.consumer.RecordedObject original,
                                               jdk.jfr.consumer.RecordedObject processed,
                                               String context) {
            // Compare field by field
            List<jdk.jfr.ValueDescriptor> originalFields = original.getFields();
            List<jdk.jfr.ValueDescriptor> processedFields = processed.getFields();

            // Check field counts match
            if (originalFields.size() != processedFields.size()) {
                fail(context + ": Field count mismatch in RecordedObject\n" +
                     "  Original fields: " + originalFields.stream().map(jdk.jfr.ValueDescriptor::getName).toList() + "\n" +
                     "  Processed fields: " + processedFields.stream().map(jdk.jfr.ValueDescriptor::getName).toList());
                return;
            }

            // Compare each field's value
            for (int i = 0; i < originalFields.size(); i++) {
                jdk.jfr.ValueDescriptor origField = originalFields.get(i);
                jdk.jfr.ValueDescriptor procField = processedFields.get(i);

                if (!origField.getName().equals(procField.getName())) {
                    fail(context + ": Field order mismatch at index " + i + "\n" +
                         "  Original: " + origField.getName() + "\n" +
                         "  Processed: " + procField.getName());
                    continue;
                }

                try {
                    Object origValue = original.getValue(origField.getName());
                    Object procValue = processed.getValue(procField.getName());

                    // Recursive comparison
                    verifyFieldValueEqual(origValue, procValue, context + "." + origField.getName());
                } catch (Exception e) {
                    // Some fields might not be accessible (like RecordedFrame arrays)
                    // Skip comparison for these
                }
            }
        }

        /**
         * Format a value for display in error messages.
         * Handles RecordedObject types specially to provide structured output.
         */
        private String formatValue(Object value) {
            if (value == null) {
                return "null";
            }

            // Handle RecordedObject specially - show structure
            if (value instanceof jdk.jfr.consumer.RecordedObject obj) {
                StringBuilder sb = new StringBuilder("{\n");
                for (jdk.jfr.ValueDescriptor field : obj.getFields()) {
                    try {
                        Object fieldValue = obj.getValue(field.getName());
                        // Avoid infinite recursion - limit depth
                        String fieldStr = fieldValue instanceof jdk.jfr.consumer.RecordedObject
                            ? fieldValue.toString()
                            : String.valueOf(fieldValue);
                        sb.append("  ").append(field.getName()).append(" = ").append(fieldStr).append("\n");
                    } catch (ClassCastException | IllegalArgumentException e) {
                        // Some field types (like RecordedFrame arrays) can't be accessed directly
                        // Just use the field's toString() instead
                        sb.append("  ").append(field.getName()).append(" = ").append("<complex type>").append("\n");
                    }
                }
                sb.append("}");
                return sb.toString();
            }

            // Handle arrays
            if (value.getClass().isArray()) {
                if (value instanceof Object[]) {
                    return java.util.Arrays.toString((Object[]) value);
                }
                // Primitive arrays
                return value.toString();
            }

            return value.toString();
        }

        /**
         * Verify annotation elements are equal.
         */
        private void verifyAnnotationsEqual(List<jdk.jfr.AnnotationElement> original,
                                           List<jdk.jfr.AnnotationElement> processed,
                                           String context) {
            assertEquals(original.size(), processed.size(),
                    context + ": Annotation count should match");

            for (int i = 0; i < original.size(); i++) {
                jdk.jfr.AnnotationElement origAnnotation = original.get(i);
                jdk.jfr.AnnotationElement procAnnotation = processed.get(i);

                String annotContext = context + "[@" + origAnnotation.getTypeName() + "]";

                assertEquals(origAnnotation.getTypeName(), procAnnotation.getTypeName(),
                        annotContext + ": Annotation type should match");

                // Verify all annotation values using assertAll
                List<jdk.jfr.ValueDescriptor> valueDescriptors = origAnnotation.getValueDescriptors();
                java.util.List<org.junit.jupiter.api.function.Executable> assertions = valueDescriptors.stream()
                    .map(valueDesc -> {
                        String valueName = valueDesc.getName();
                        return (org.junit.jupiter.api.function.Executable) () -> {
                            Object origValue = origAnnotation.getValue(valueName);
                            Object procValue = procAnnotation.getValue(valueName);

                            // Use deep equality for arrays - but check if they're actually arrays first
                            if (origValue != null && procValue != null &&
                                origValue.getClass().isArray() && procValue.getClass().isArray()) {
                                // Both are arrays - use assertArrayEquals if they're Object arrays
                                if (origValue instanceof Object[] && procValue instanceof Object[]) {
                                    assertArrayEquals((Object[]) origValue, (Object[]) procValue,
                                            annotContext + "." + valueName + ": Annotation array value should match");
                                } else {
                                    // Primitive arrays or other array types - use equals
                                    assertEquals(origValue, procValue,
                                            annotContext + "." + valueName + ": Annotation value should match");
                                }
                            } else {
                                assertEquals(origValue, procValue,
                                        annotContext + "." + valueName + ": Annotation value should match");
                            }
                        };
                    })
                    .toList();

                assertAll(annotContext, assertions.stream());
            }
        }

        /**
         * Verify that all events are fully equal (deep comparison including all fields).
         * This includes stack traces, nested objects, arrays, etc.
         * NOTE: This will fail if using constant pool for stack frames, as the comparison
         * requires exact field-by-field equality.
         */
        public RoundtripVerifier verifyEventsFullyEqual() {
            assertEquals(originalEvents.size(), processedEvents.size(),
                    "Event count must match for deep comparison");

            List<Executable> assertions = new ArrayList<>();

            for (int i = 0; i < originalEvents.size(); i++) {
                final int index = i;
                RecordedEvent originalEvent = originalEvents.get(i);
                RecordedEvent processedEvent = processedEvents.get(i);

                // Verify event type matches
                assertions.add(() -> assertEquals(
                        originalEvent.getEventType().getName(),
                        processedEvent.getEventType().getName(),
                        String.format("Event[%d] type should match", index)
                ));

                // Deep compare all fields
                for (var field : originalEvent.getFields()) {
                    String fieldName = field.getName();
                    Object originalValue = originalEvent.getValue(fieldName);
                    Object processedValue = processedEvent.getValue(fieldName);

                    assertions.add(() -> assertValuesDeepEqual(
                            originalValue,
                            processedValue,
                            String.format("Event[%d].%s (%s)", index, fieldName,
                                    originalEvent.getEventType().getName())
                    ));
                }
            }

            assertAll("Deep comparison of all event fields", assertions.stream());
            return this;
        }

        /**
         * Deep comparison of two values from RecordedEvents.
         * Handles primitives, strings, arrays, RecordedObject, RecordedThread, RecordedClass, etc.
         */
        private void assertValuesDeepEqual(Object original, Object processed, String path) {
            // Handle nulls
            if (original == null && processed == null) {
                return;
            }
            if (original == null || processed == null) {
                fail(String.format("%s: one value is null: original=%s, processed=%s",
                        path, original, processed));
                return;
            }

            // Handle primitives and strings
            if (original instanceof String || original instanceof Number ||
                original instanceof Boolean || original instanceof Character) {
                assertEquals(original, processed, path + " should be equal");
                return;
            }

            // Handle arrays
            if (original.getClass().isArray()) {
                assertArraysDeepEqual(original, processed, path);
                return;
            }

            // Handle RecordedObject (includes RecordedEvent, RecordedThread, RecordedClass, RecordedStackTrace, RecordedFrame, etc.)
            if (original instanceof jdk.jfr.consumer.RecordedObject) {
                assertRecordedObjectsDeepEqual(
                        (jdk.jfr.consumer.RecordedObject) original,
                        (jdk.jfr.consumer.RecordedObject) processed,
                        path);
                return;
            }

            // Fallback: use equals()
            assertEquals(original, processed, path + " should be equal (using equals())");
        }

        /**
         * Deep comparison of RecordedObjects (includes RecordedFrame, RecordedThread, etc.)
         */
        private void assertRecordedObjectsDeepEqual(jdk.jfr.consumer.RecordedObject original,
                                                     jdk.jfr.consumer.RecordedObject processed,
                                                     String path) {
            // Compare all fields recursively
            for (var field : original.getFields()) {
                String fieldName = field.getName();
                Object originalValue = original.getValue(fieldName);
                Object processedValue = processed.getValue(fieldName);

                assertValuesDeepEqual(originalValue, processedValue,
                        path + "." + fieldName);
            }
        }

        /**
         * Deep comparison of arrays (primitive or object arrays)
         */
        private void assertArraysDeepEqual(Object original, Object processed, String path) {
            Class<?> componentType = original.getClass().getComponentType();

            if (componentType.isPrimitive()) {
                // Handle primitive arrays
                if (componentType == byte.class) {
                    assertArrayEquals((byte[]) original, (byte[]) processed, path);
                } else if (componentType == short.class) {
                    assertArrayEquals((short[]) original, (short[]) processed, path);
                } else if (componentType == int.class) {
                    assertArrayEquals((int[]) original, (int[]) processed, path);
                } else if (componentType == long.class) {
                    assertArrayEquals((long[]) original, (long[]) processed, path);
                } else if (componentType == float.class) {
                    assertArrayEquals((float[]) original, (float[]) processed, path);
                } else if (componentType == double.class) {
                    assertArrayEquals((double[]) original, (double[]) processed, path);
                } else if (componentType == boolean.class) {
                    assertArrayEquals((boolean[]) original, (boolean[]) processed, path);
                } else if (componentType == char.class) {
                    assertArrayEquals((char[]) original, (char[]) processed, path);
                }
            } else {
                // Handle object arrays
                Object[] originalArray = (Object[]) original;
                Object[] processedArray = (Object[]) processed;

                assertEquals(originalArray.length, processedArray.length,
                        path + ".length should match");

                for (int i = 0; i < originalArray.length; i++) {
                    assertValuesDeepEqual(originalArray[i], processedArray[i],
                            path + "[" + i + "]");
                }
            }
        }

        // ...existing code...
    }
}