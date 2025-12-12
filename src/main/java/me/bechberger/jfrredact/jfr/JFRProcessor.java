package me.bechberger.jfrredact.jfr;

import jdk.jfr.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.engine.RedactionEngine;
import org.openjdk.jmc.flightrecorder.writer.RecordingImpl;
import org.openjdk.jmc.flightrecorder.writer.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Processor for JFR recordings that applies redaction rules.
 * <p>
 * Partially based on {@link RecordingImpl} of JMC
 * <p>
 * <b>Important Note on Array Fields:</b>
 * JFR does not persist array fields in custom events when they are written to a recording file.
 * While array fields can be set on Event objects in code (e.g., {@code event.stringArray = new String[]{"a", "b", "c"}}),
 * the JDK's JFR implementation does not include these fields when serializing events to disk.
 * When the recording is read back, array fields will not be present in the RecordedEvent.
 * This is a limitation of the JFR framework itself, not this processor.
 * <p>
 * Example:
 * <pre>
 * ArrayEvent event = new ArrayEvent();
 * event.stringArray = new String[]{"one", "two", "three"};  // Set in code
 * event.commit();
 * // After writing to JFR file and reading back:
 * // RecordedEvent will NOT contain the stringArray field
 * </pre>
 * <p>
 * Standard JDK events may include arrays (like stack trace frames), but custom user-defined
 * array fields are not supported in the JFR serialization format.
 */
public class JFRProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JFRProcessor.class);


    private final RedactionEngine redactionEngine;
    private final RecordingFile input;
    private RecordingImpl output;

    public JFRProcessor(RedactionEngine redactionEngine, RecordingFile input) {
        this.redactionEngine = redactionEngine;
        this.input = input;
    }

    private void initRecording(OutputStream outputStream) {
        // Initialize JDK types for content type annotations (Timestamp, etc.) to work properly
        this.output =
                (RecordingImpl)
                        Recordings.newRecording(
                                outputStream,
                                r -> {
                                    //r.withStartTicks(1);
                                    //r.withTimestamp(1);
                                    //r.withJdkTypeInitialization();
                                });

    }


    /**
     * Register an annotation type with a single boolean value field.
     */
    private void registerAnnotationWithBooleanValue(String annotationTypeName) {
        output.getTypes().getOrAdd(
                annotationTypeName,
                Annotation.ANNOTATION_SUPER_TYPE_NAME,
                builder -> builder.addField("value", Types.Builtin.BOOLEAN)
        );
    }

    public RecordingImpl process(OutputStream outputStream) throws IOException {
        logger.info("Starting JFR event processing with two-pass approach");
        initRecording(outputStream);

        // PHASE 1: Collect events and register all types
        logger.info("Phase 1: Reading events and registering types");
        List<RecordedEvent> eventsToWrite = new ArrayList<>();
        int totalEvents = 0;
        int removedEvents = 0;

        while (input.hasMoreEvents()) {
            var event = input.readEvent();
            totalEvents++;

            if (redactionEngine.shouldRemoveEvent(event)) {
                removedEvents++;
                logger.debug("Removed event #{}: {}", removedEvents, event.getEventType().getName());
                continue; // Skip this event
            }

            // Collect event for later writing
            eventsToWrite.add(event);

            // Register event type (will be idempotent if already registered)
            registerEventType(event);

            if (eventsToWrite.size() % 1000 == 0) {
                logger.info("Collected {} events ({} removed)", eventsToWrite.size(), removedEvents);
            }
        }

        logger.info("Phase 1 complete: {} total events read, {} to process, {} removed",
                totalEvents, eventsToWrite.size(), removedEvents);

        // PHASE 2: Resolve all types ONCE
        logger.info("Phase 2: Resolving all registered types");
        output.getTypes().resolveAll();
        logger.info("Type resolution complete");

        // PHASE 3: Write all collected events
        logger.info("Phase 3: Writing {} events", eventsToWrite.size());
        int written = 0;
        for (RecordedEvent event : eventsToWrite) {
            writeEvent(event);
            written++;

            if (written % 1000 == 0) {
                logger.info("Written {} events", written);
            }
        }

        logger.info("JFR processing complete: {} total events, {} processed, {} removed",
                totalEvents, written, removedEvents);
        return output;
    }

    /**
     * Register an event type without writing the event.
     * This is part of phase 1 of the two-pass processing.
     */
    private void registerEventType(RecordedEvent event) {
        EventType eventType = event.getEventType();
        String eventTypeName = eventType.getName();

        // Check if already registered
        if (output.getTypes().getType(eventTypeName, false) != null) {
            return;
        }

        output.registerType(eventTypeName, "jdk.jfr.Event", builder -> {
            ImplicitFieldTracker implicitFields = new ImplicitFieldTracker();

            // Register all fields from the event
            for (ValueDescriptor field : eventType.getFields()) {
                implicitFields.trackField(field.getName());
                addFieldToTypeBuilder(builder, field, new ArrayList<>());
            }

            // Add missing implicit fields
            addMissingImplicitFields(builder, eventType, implicitFields);

            // Add event-level annotations
            addAnnotationsToTypeBuilder(builder, eventType.getAnnotationElements());
        });
    }

    /**
     * Write an event to the output.
     * This is part of phase 3 of the two-pass processing.
     */
    private void writeEvent(RecordedEvent event) {
        Type type = output.getType(event.getEventType().getName());
        output.writeEvent(type.asValue(b -> createEventTypedValue(b, event)));
    }

    /**
     * Handles registration of complex (non-primitive) types.
     *
     * @param typesCurrentlyAdding Stack of types currently being registered to detect circular references
     * @param descriptor           The descriptor of the complex type to register
     * @return The registered Type
     */
    private Type handleComplexType(List<String> typesCurrentlyAdding, ValueDescriptor descriptor) {
        String typeName = descriptor.getTypeName();

        // Check if type already exists
        Type existingType = output.getTypes().getType(typeName, false);
        if (existingType != null) {
            // Check if existing type's fields match what we need
            boolean fieldsMatch = checkFieldsMatch(existingType, descriptor);
            if (fieldsMatch) {
                return existingType;
            }
            throw new AssertionError("Type name conflict for '" + typeName +
                                     "': existing type fields do not match descriptor fields.");
        }

        typesCurrentlyAdding.add(typeName);

        // Use getOrAdd which will either use existing type or register new one
        return output.getTypes().getOrAdd(typeName, null, builder -> {
            for (ValueDescriptor field : descriptor.getFields()) {
                addFieldToTypeBuilder(builder, field, typesCurrentlyAdding);
            }
            addAnnotationsToTypeBuilder(builder, descriptor.getAnnotationElements());
        });
    }

    /**
     * Check if existing type has matching fields with the descriptor.
     */
    private boolean checkFieldsMatch(Type existingType, ValueDescriptor descriptor) {
        // Get field names from descriptor
        List<String> sourceFields = descriptor.getFields().stream()
                .map(ValueDescriptor::getName)
                .toList();

        // Get field names from existing type - this is a heuristic check
        // If existing type has all the fields we need, we can use it
        for (String fieldName : sourceFields) {
            if (existingType.getField(fieldName) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds a field to a type builder, handling both primitive and complex types.
     */
    private void addFieldToTypeBuilder(TypeStructureBuilder builder, ValueDescriptor field,
                                       List<String> typesCurrentlyAdding) {
        String fieldName = field.getName();
        Types.Predefined predefinedType = mapJFRTypeToJMCType(field.getTypeName());

        if (predefinedType != null) {
            builder.addField(fieldName, predefinedType, fb -> configureFieldBuilder(fb, field, fieldName));
        } else {
            Type complexFieldType = resolveComplexFieldType(field, typesCurrentlyAdding);
            builder.addField(fieldName, complexFieldType, fb -> configureFieldBuilder(fb, field, fieldName));
        }
    }

    /**
     * Resolves the type for a complex field, handling circular references.
     */
    private Type resolveComplexFieldType(ValueDescriptor field, List<String> typesCurrentlyAdding) {
        if (typesCurrentlyAdding.contains(field.getTypeName())) {
            // Circular reference detected - return already registered type
            return output.getType(field.getTypeName());
        }
        return handleComplexType(typesCurrentlyAdding, field);
    }

    /**
     * Configures a field builder with annotations and array settings.
     */
    private void configureFieldBuilder(TypedFieldBuilder fieldBuilder, ValueDescriptor field, String fieldName) {
        List<AnnotationElement> annotations = field.getAnnotationElements();

        // Process all annotations on the field
        processFieldAnnotations(fieldBuilder, annotations);

        // Handle array fields
        if (field.isArray()) {
            fieldBuilder.asArray();
        }
    }

    /**
     * Helper class to track which special annotations were found.
     */
    private static class AnnotationTracker {
        boolean hasTimestamp = false;
        boolean hasTimespan = false;
    }

    /**
     * Processes all annotations for a field and returns tracker of which special annotations were found.
     * Does not assume any annotations are predefined - creates all annotation types generically.
     */
    private AnnotationTracker processFieldAnnotations(TypedFieldBuilder fieldBuilder, List<AnnotationElement> annotations) {
        AnnotationTracker tracker = new AnnotationTracker();
        for (AnnotationElement annotation : annotations) {
            String typeName = annotation.getTypeName();

            // Track special annotation types for the tracker
            if ("jdk.jfr.Timestamp".equals(typeName)) {
                tracker.hasTimestamp = true;
            } else if ("jdk.jfr.Timespan".equals(typeName)) {
                tracker.hasTimespan = true;
            }

            // Process all annotations generically - don't assume anything is predefined
            processAnnotation(fieldBuilder, annotation);
        }
        return tracker;
    }

    /**
     * Adds annotations to a type builder.
     */
    private void addAnnotationsToTypeBuilder(TypeStructureBuilder builder, List<AnnotationElement> annotations) {
        for (AnnotationElement annotation : annotations) {
            processEventAnnotation(builder, annotation);
        }
    }

    /**
     * Creates and registers a JFR event type.
     */
    private Type createEventType(RecordedEvent event) {
        EventType eventType = event.getEventType();
        String eventTypeName = eventType.getName();

        return output.registerType(eventTypeName, "jdk.jfr.Event", builder -> {
            ImplicitFieldTracker implicitFields = new ImplicitFieldTracker();

            // Register all fields from the event
            for (ValueDescriptor field : eventType.getFields()) {
                implicitFields.trackField(field.getName());
                addFieldToTypeBuilder(builder, field, new ArrayList<>());
            }

            // Add missing implicit fields
            addMissingImplicitFields(builder, eventType, implicitFields);

            // Add event-level annotations
            addAnnotationsToTypeBuilder(builder, eventType.getAnnotationElements());
        });
    }

    /**
     * Adds implicit fields (startTime, eventThread, stackTrace) if they were not already present.
     */
    private void addMissingImplicitFields(TypeStructureBuilder builder, EventType eventType,
                                          ImplicitFieldTracker tracker) {
        if (!tracker.hasStartTime()) {
            // Get the startTime field descriptor from the event type to copy its annotations
            ValueDescriptor startTimeDescriptor = eventType.getField("startTime");
            if (startTimeDescriptor != null) {
                builder.addField("startTime", Types.Builtin.LONG, field -> {
                    // Process annotations from the original field descriptor
                    processFieldAnnotations(field, startTimeDescriptor.getAnnotationElements());
                });
            } else {
                // Fallback: create a basic startTime field with Timestamp annotation
                /*builder.addField("startTime", Types.Builtin.LONG, field -> {
                    // Create a minimal Timestamp annotation type and add it
                    Type timestampType = output.getTypes().getOrAdd(
                        "jdk.jfr.Timestamp",
                        Annotation.ANNOTATION_SUPER_TYPE_NAME,
                        timestampBuilder -> timestampBuilder.addField("value", Types.Builtin.STRING)
                    );
                    field.addAnnotation(timestampType, "NANOSECONDS_SINCE_EPOCH");
                });*/
            }
        }

        if (!tracker.hasEventThread()) {
            ValueDescriptor descriptor = eventType.getField("eventThread");
            if (descriptor != null) {
                builder.addField("eventThread",
                        handleComplexType(new ArrayList<>(), descriptor));
            }
        }

        if (!tracker.hasStackTrace() && hasStackTrace(eventType)) {
            ValueDescriptor descriptor = eventType.getField("stackTrace");
            if (descriptor != null) {
                builder.addField("stackTrace",
                        handleComplexType(new ArrayList<>(), descriptor));
            }
        }
    }

    /**
     * Helper class to track which implicit JFR fields were found during event processing.
     */
    private static class ImplicitFieldTracker {
        private boolean startTime = false;
        private boolean eventThread = false;
        private boolean stackTrace = false;

        void trackField(String fieldName) {
            switch (fieldName) {
                case "startTime" -> startTime = true;
                case "eventThread" -> eventThread = true;
                case "stackTrace" -> stackTrace = true;
            }
        }

        boolean hasStartTime() {
            return startTime;
        }

        boolean hasEventThread() {
            return eventThread;
        }

        boolean hasStackTrace() {
            return stackTrace;
        }
    }

    private boolean hasStackTrace(EventType type) {
        StackTrace stAnnotation = type.getAnnotation(StackTrace.class);
        if (stAnnotation != null) {
            return stAnnotation.value();
        }
        return false;
    }

    private Types.Predefined mapJFRTypeToJMCType(String jfrTypeName) {
        // Map JFR type names to JMC Types
        return switch (jfrTypeName) {
            case "byte" -> Types.Builtin.BYTE;
            case "short" -> Types.Builtin.SHORT;
            case "int" -> Types.Builtin.INT;
            case "long" -> Types.Builtin.LONG;
            case "float" -> Types.Builtin.FLOAT;
            case "double" -> Types.Builtin.DOUBLE;
            case "boolean" -> Types.Builtin.BOOLEAN;
            case "char" -> Types.Builtin.CHAR;
            case "java.lang.String" -> Types.Builtin.STRING;
            default -> null;
        };
    }

    /**
     * Helper method to put a field value into an annotation builder, handling arrays and primitives.
     */
    private void putAnnotationField(TypedValueBuilder builder, String fieldName, Object value) {
        if (value == null) {
            return; // Skip null values in annotations
        }

        // Handle arrays with pattern matching
        if (value.getClass().isArray()) {
            switch (value) {
                case byte[] arr -> builder.putField(fieldName, arr);
                case short[] arr -> builder.putField(fieldName, arr);
                case int[] arr -> builder.putField(fieldName, arr);
                case long[] arr -> builder.putField(fieldName, arr);
                case float[] arr -> builder.putField(fieldName, arr);
                case double[] arr -> builder.putField(fieldName, arr);
                case boolean[] arr -> builder.putField(fieldName, arr);
                case char[] arr -> builder.putField(fieldName, arr);
                case String[] arr -> builder.putField(fieldName, arr);
                default -> {
                    // For unsupported array types, convert to string
                    logger.warn("Unsupported array type in annotation: {}", value.getClass().getName());
                    builder.putField(fieldName, value.toString());
                }
            }
        } else {
            // Handle primitives and strings
            switch (value) {
                case Byte v -> builder.putField(fieldName, v);
                case Short v -> builder.putField(fieldName, v);
                case Integer v -> builder.putField(fieldName, v);
                case Long v -> builder.putField(fieldName, v);
                case Float v -> builder.putField(fieldName, v);
                case Double v -> builder.putField(fieldName, v);
                case Boolean v -> builder.putField(fieldName, v);
                case Character v -> builder.putField(fieldName, v);
                case String v -> builder.putField(fieldName, v);
                default -> builder.putField(fieldName, value.toString());
            }
        }
    }

    /**
     * Generic helper to register or retrieve an annotation type definition.
     * Also processes meta-annotations (annotations on the annotation type).
     */
    private Type ensureAnnotationType(AnnotationElement annotation) {
        String annotationTypeName = annotation.getTypeName();

        // Check if type already exists
        Type existingType = output.getTypes().getType(annotationTypeName, false);
        if (existingType != null) {
            return existingType;
        }

        // Get the annotation element to check for meta-annotations
        List<AnnotationElement> metaAnnotations = annotation.getAnnotationElements();

        logger.debug("Creating annotation type: {} with {} meta-annotations",
                annotationTypeName, metaAnnotations.size());
        for (AnnotationElement meta : metaAnnotations) {
            logger.debug("  Meta-annotation: {}", meta.getTypeName());
        }

        // Create new annotation type
        return output.getTypes().getOrAdd(
                annotationTypeName,
                Annotation.ANNOTATION_SUPER_TYPE_NAME,
                builder -> {
                    // First, process meta-annotations (annotations on this annotation type)
                    for (AnnotationElement metaAnnotation : metaAnnotations) {
                        try {
                            logger.debug("Processing meta-annotation {} for {}",
                                    metaAnnotation.getTypeName(), annotationTypeName);
                            // Recursively ensure meta-annotation types exist
                            Type metaAnnotationType = ensureAnnotationType(metaAnnotation);
                            // Add the meta-annotation to this annotation type
                            addEventAnnotationWithExistingType(builder, metaAnnotationType, metaAnnotation);
                        } catch (Exception e) {
                            logger.debug("Could not process meta-annotation {} on {}: {}",
                                    metaAnnotation.getTypeName(), annotationTypeName, e.getMessage());
                        }
                    }

                    // Add ALL fields that the annotation type supports
                    // Note: We add all fields to the type definition, even if some instances may have null values
                    // This is important because different annotation instances may have different fields populated
                    for (ValueDescriptor valueDescriptor : annotation.getValueDescriptors()) {
                        String fieldName = valueDescriptor.getName();

                        // Add field to type definition
                        Types.Predefined fieldType = mapJFRTypeToJMCType(valueDescriptor.getTypeName());
                        if (fieldType != null) {
                            logger.debug("Adding field '{}' ({}) to annotation type '{}'",
                                    fieldName, fieldType, annotationTypeName);
                            builder.addField(fieldName, fieldType, fb -> {
                                if (valueDescriptor.isArray()) {
                                    fb.asArray();
                                }
                            });
                        } else {
                            throw new UnsupportedOperationException(
                                    "Unsupported annotation field type for annotation '" + annotationTypeName +
                                    "', field '" + fieldName + "': " + valueDescriptor.getTypeName()
                            );
                        }
                    }
                }
        );
    }

    private void processAnnotation(TypedFieldBuilder fieldBuilder, jdk.jfr.AnnotationElement annotation) {
        try {
            Type annotationType = ensureAnnotationType(annotation);
            addAnnotationWithExistingType(fieldBuilder, annotationType, annotation);
        } catch (IllegalStateException | UnsupportedOperationException e) {
            // Skip annotations that can't be processed
            logger.debug("Could not process annotation {}: {}", annotation.getTypeName(), e.getMessage());
        }
    }

    /**
     * Helper method to add an annotation using an existing type.
     */
    private void addAnnotationWithExistingType(TypedFieldBuilder fieldBuilder, Type annotationType, jdk.jfr.AnnotationElement annotation) {
        // Check if this is a true marker annotation (no value descriptors at all)
        if (annotation.getValueDescriptors().isEmpty()) {
            // True marker annotation (no fields defined)
            fieldBuilder.addAnnotation(annotationType);
            return;
        }

        // Annotation has fields - collect all non-null values
        List<ValueDescriptor> nonNullFields = new ArrayList<>();
        for (ValueDescriptor vd : annotation.getValueDescriptors()) {
            try {
                Object val = annotation.getValue(vd.getName());
                if (val != null) {
                    nonNullFields.add(vd);
                }
            } catch (IllegalArgumentException e) {
                // Field not present or inaccessible - skip it
            }
        }

        // If all fields are null, skip the annotation entirely
        if (nonNullFields.isEmpty()) {
            logger.debug("Skipping annotation {} - all fields are null", annotation.getTypeName());
            return;
        }

        // Add annotation with only non-null fields
        if (nonNullFields.size() == 1 && nonNullFields.get(0).getName().equals("value")) {
            // Single "value" field - use simplified syntax
            Object value = annotation.getValue("value");
            if (value.getClass().isArray()) {
                fieldBuilder.addAnnotation(annotationType, ab -> putAnnotationField(ab, "value", value));
            } else {
                fieldBuilder.addAnnotation(annotationType, value.toString());
            }
        } else {
            // Multiple fields or non-"value" field - use builder pattern
            fieldBuilder.addAnnotation(annotationType, ab -> {
                for (ValueDescriptor vd : nonNullFields) {
                    Object val = annotation.getValue(vd.getName());
                    putAnnotationField(ab, vd.getName(), val);
                }
            });
        }
    }

    private void processEventAnnotation(TypeStructureBuilder builder, jdk.jfr.AnnotationElement annotation) {
        try {
            Type annotationType = ensureAnnotationType(annotation);
            addEventAnnotationWithExistingType(builder, annotationType, annotation);
        } catch (IllegalStateException | UnsupportedOperationException e) {
            // Skip annotations that can't be processed
            logger.debug("Could not process event annotation {}: {}", annotation.getTypeName(), e.getMessage());
        }
    }

    /**
     * Helper method to add an event annotation using an existing type.
     */
    private void addEventAnnotationWithExistingType(TypeStructureBuilder builder, Type annotationType, jdk.jfr.AnnotationElement annotation) {
        // Check if this is a true marker annotation (no value descriptors at all)
        if (annotation.getValueDescriptors().isEmpty()) {
            // True marker annotation (no fields defined)
            builder.addAnnotation(annotationType);
            return;
        }

        // Annotation has fields - collect all non-null values
        List<ValueDescriptor> nonNullFields = new ArrayList<>();
        for (ValueDescriptor vd : annotation.getValueDescriptors()) {
            try {
                Object val = annotation.getValue(vd.getName());
                if (val != null) {
                    nonNullFields.add(vd);
                }
            } catch (IllegalArgumentException e) {
                // Field not present or inaccessible - skip it
            }
        }

        // If all fields are null, skip the annotation entirely
        if (nonNullFields.isEmpty()) {
            logger.debug("Skipping event annotation {} - all fields are null", annotation.getTypeName());
            return;
        }

        // Add annotation with only non-null fields
        if (nonNullFields.size() == 1 && nonNullFields.get(0).getName().equals("value")) {
            // Single "value" field - use simplified syntax
            Object value = annotation.getValue("value");
            if (value.getClass().isArray()) {
                builder.addAnnotation(annotationType, ab -> putAnnotationField(ab, "value", value));
            } else {
                builder.addAnnotation(annotationType, value.toString());
            }
        } else {
            // Multiple fields or non-"value" field - use builder pattern
            builder.addAnnotation(annotationType, ab -> {
                for (ValueDescriptor vd : nonNullFields) {
                    Object val = annotation.getValue(vd.getName());
                    putAnnotationField(ab, vd.getName(), val);
                }
            });
        }
    }

    private void addFieldValue(TypedValueBuilder valueBuilder, ValueDescriptor descriptor, Object value) {
        String fieldName = descriptor.getName();

        // Handle null values - skip them or set as null string
        if (value == null) {
            valueBuilder.putField(fieldName, valueBuilder.getType().getField(fieldName).getType().nullValue());
            return;
        }

        // Handle arrays
        if (descriptor.isArray()) {
            handleArrayValue(valueBuilder, descriptor, value);
            return;
        }

        // Handle different value types with redaction applied
        String typeName = descriptor.getTypeName();

        switch (typeName) {
            case "byte" -> valueBuilder.putField(fieldName, redactionEngine.redact(fieldName, (byte) value));
            case "short" -> valueBuilder.putField(fieldName, redactionEngine.redact(fieldName, (short) value));
            case "int" -> valueBuilder.putField(fieldName, redactionEngine.redact(fieldName, (int) value));
            case "long" -> valueBuilder.putField(fieldName, redactionEngine.redact(fieldName, (long) value));
            case "float" -> valueBuilder.putField(fieldName, redactionEngine.redact(fieldName, (float) value));
            case "double" -> valueBuilder.putField(fieldName, redactionEngine.redact(fieldName, (double) value));
            case "boolean" -> valueBuilder.putField(fieldName, redactionEngine.redact(fieldName, (boolean) value));
            case "char" -> valueBuilder.putField(fieldName, redactionEngine.redact(fieldName, (char) value));
            case "java.lang.String" ->
                    valueBuilder.putField(fieldName, redactionEngine.redact(fieldName, value.toString()));
            default -> {
                // Handle complex types (classes, threads, stack traces, etc.)
                if (value instanceof jdk.jfr.consumer.RecordedObject) {
                    handleComplexValue(valueBuilder, descriptor, (jdk.jfr.consumer.RecordedObject) value);
                } else {
                    throw new UnsupportedOperationException(
                            "Unsupported complex value type for field '" + fieldName + "': " + value.getClass().getName()
                    );
                }
            }
        }
    }

    private void handleArrayValue(TypedValueBuilder valueBuilder, ValueDescriptor field, Object value) {
        String fieldName = field.getName();
        // Arrays in JFR can be primitive arrays or object arrays
        if (value.getClass().isArray()) {
            // Handle primitive arrays
            switch (value) {
                case byte[] byteArray -> valueBuilder.putField(fieldName, byteArray);
                case short[] shortArray -> valueBuilder.putField(fieldName, shortArray);
                case int[] intArray -> valueBuilder.putField(fieldName, intArray);
                case long[] longArray -> valueBuilder.putField(fieldName, longArray);
                case float[] floatArray -> valueBuilder.putField(fieldName, floatArray);
                case double[] doubleArray -> valueBuilder.putField(fieldName, doubleArray);
                case boolean[] booleanArray -> valueBuilder.putField(fieldName, booleanArray);
                case char[] charArray -> valueBuilder.putField(fieldName, charArray);
                case String[] stringArray -> valueBuilder.putField(fieldName, stringArray);
                case Object[] recordedArray ->
                    // Handle arrays of complex objects
                        valueBuilder.putField(fieldName, createComplexArrayValue(field, fieldName, recordedArray));
                default -> {
                }
            }
        } else if (value instanceof List) {
            // Unsupported list type - throw exception
            throw new UnsupportedOperationException(
                    "Unsupported List type for field '" + fieldName + "': " + value.getClass().getName()
            );
        } else {
            // Unsupported value type - throw exception
            throw new UnsupportedOperationException(
                    "Unsupported value type for field '" + fieldName + "': " + value.getClass().getName()
            );
        }
    }

    private TypedValue[] createComplexArrayValue(ValueDescriptor field, String fieldName, Object[] recordedArray) {
        List<TypedValue> typedValues = new ArrayList<>();
        Type type = output.getType(field.getTypeName().replace("[]", ""));

        for (Object element : recordedArray) {
            if (element instanceof jdk.jfr.consumer.RecordedObject recordedObject) {
                // Build the complex object value
                typedValues.add(type.asValue(b -> {
                    handleComplexValue(b, field, recordedObject);
                }));
            } else {
                throw new UnsupportedOperationException("Object array contains non-RecordedObject element for field '" + fieldName + "'");
            }
        }

        return typedValues.toArray(new TypedValue[0]);
    }

    private void handleComplexValue(TypedValueBuilder valueBuilder,
                                    ValueDescriptor descriptor, jdk.jfr.consumer.RecordedObject recordedObject) {
        String fieldName = descriptor.getName();

        try {
            // Recursively build the complex value
            // IMPORTANT: Use descriptor.getFields() to ensure we write all registered fields,
            // not just the fields available in recordedObject.getFields()
            valueBuilder.putField(fieldName, complexValueBuilder -> {
                for (ValueDescriptor field : descriptor.getFields()) {
                    try {
                        Object fieldValue = recordedObject.getValue(field.getName());
                        addFieldValue(complexValueBuilder, field, fieldValue);
                    } catch (IllegalArgumentException e) {
                        // Field might not be present in this particular recordedObject
                        // Set it to null value
                        complexValueBuilder.putField(field.getName(),
                                complexValueBuilder.getType().getField(field.getName()).getType().nullValue());
                    }
                }
            });
        } catch (IllegalStateException ex) {
            System.err.println("Warning: Could not process complex field '" + fieldName + "'");
            throw ex;
        }
    }


    private void createEventTypedValue(TypedValueBuilder builder, RecordedEvent event) {
        // Process fields of the event, skipping complex JDK types
        for (ValueDescriptor field : event.getEventType().getFields()) {
            String fieldName = field.getName();

            Object fieldValue = event.getValue(fieldName);

            addFieldValue(builder, field, fieldValue);
        }
    }
}