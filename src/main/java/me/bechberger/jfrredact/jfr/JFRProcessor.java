package me.bechberger.jfrredact.jfr;

import jdk.jfr.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.config.DiscoveryConfig;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.DiscoveredPatterns;
import me.bechberger.jfrredact.engine.InteractiveDecisionManager;
import me.bechberger.jfrredact.engine.RedactionEngine;
import me.bechberger.jfrredact.engine.PatternDiscoveryEngine;
import org.openjdk.jmc.flightrecorder.writer.RecordingImpl;
import org.openjdk.jmc.flightrecorder.writer.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

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
    private final RedactionConfig config;
    private final Path inputPath;
    private RecordingImpl output;
    private InteractiveDecisionManager interactiveDecisionManager;

    /**
     * Create a JFR processor with file-based input (supports discovery).
     *
     * @param redactionEngine The redaction engine to use
     * @param config The redaction configuration
     * @param inputPath Path to the input JFR file (needed for re-reading during discovery)
     */
    public JFRProcessor(RedactionEngine redactionEngine, RedactionConfig config, Path inputPath) {
        this.redactionEngine = redactionEngine;
        this.config = config;
        this.inputPath = inputPath;
    }

    /**
     * Set the interactive decision manager for interactive mode
     */
    public void setInteractiveDecisionManager(me.bechberger.jfrredact.engine.InteractiveDecisionManager manager) {
        this.interactiveDecisionManager = manager;
    }


    private void initRecording(OutputStream outputStream) {
        // Initialize JDK types for content type annotations (Timestamp, etc.) to work properly
        this.output =
                (RecordingImpl)
                        Recordings.newRecording(
                                outputStream,
                                r -> {
                                });

    }

    public RecordingImpl process(OutputStream outputStream) throws IOException {
        if (config == null || inputPath == null) {
            logger.warn("Discovery disabled - processing without discovery phase");
            return processWithoutDiscovery(outputStream);
        }

        DiscoveryConfig.DiscoveryMode mode = config.getDiscovery().getMode();

        switch (mode) {
            case NONE:
                logger.info("Discovery mode: NONE - single-pass processing");
                return processWithoutDiscovery(outputStream);

            case FAST:
                logger.info("Discovery mode: FAST - on-the-fly discovery");
                return processWithFastDiscovery(outputStream);

            case TWO_PASS:
            default:
                logger.info("Discovery mode: TWO_PASS - file will be read twice");
                return processWithTwoPassDiscovery(outputStream);
        }
    }

    /**
     * Process without discovery - standard single-pass processing.
     */
    private RecordingImpl processWithoutDiscovery(OutputStream outputStream) throws IOException {
        logger.info("Starting JFR event processing (no discovery)");

        return processRecordingFile(() -> {
            try {
                return new RecordingFile(inputPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open recording file", e);
            }
        }, outputStream, null);
    }

    /**
     * Process with on-the-fly discovery - discover patterns during processing.
     * Only occurrences AFTER discovery are redacted.
     */
    private RecordingImpl processWithFastDiscovery(OutputStream outputStream) throws IOException {
        logger.info("Starting JFR event processing with fast discovery");

        PatternDiscoveryEngine discoveryEngine = new PatternDiscoveryEngine(
            config.getDiscovery(),
            config.getStrings()
        );

        return processRecordingFile(() -> {
            try {
                return new RecordingFile(inputPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open recording file", e);
            }
        }, outputStream, discoveryEngine);
    }

    /**
     * Process with two-pass discovery - read file twice for complete discovery.
     */
    private RecordingImpl processWithTwoPassDiscovery(OutputStream outputStream) throws IOException {
        logger.info("Starting JFR event processing with two-pass discovery");

        discover();

        // REDACTION PASS: Read file again to redact
        logger.info("Redaction Pass: Processing events with discovered patterns...");
        return processRecordingFile(() -> {
            try {
                return new RecordingFile(inputPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open recording file for redaction pass", e);
            }
        }, outputStream, null);
    }

    private void discover() throws IOException {
        // DISCOVERY PASS: Read file to discover patterns
        logger.info("Discovery Pass: Analyzing events for sensitive patterns...");
        PatternDiscoveryEngine discoveryEngine = new PatternDiscoveryEngine(
            config.getDiscovery(),
            config.getStrings()
        );

        // Set interactive decision manager if available
        if (interactiveDecisionManager != null) {
            discoveryEngine.setInteractiveDecisionManager(interactiveDecisionManager);
        }

        int discoveryEventCount = 0;
        try (RecordingFile discoveryInput = new RecordingFile(inputPath)) {
            while (discoveryInput.hasMoreEvents()) {
                RecordedEvent event = discoveryInput.readEvent();
                discoveryEngine.analyzeEvent(event);
                discoveryEventCount++;

                if (discoveryEventCount % 10000 == 0) {
                    logger.info("Discovery: analyzed {} events", discoveryEventCount);
                }
            }
        }

        DiscoveredPatterns patterns = discoveryEngine.getDiscoveredPatterns();
        logger.info("Discovery Pass complete: analyzed {} events", discoveryEventCount);
        logger.info(discoveryEngine.getStatistics());

        // Apply interactive decisions and save them
        if (interactiveDecisionManager != null) {
            patterns = discoveryEngine.applyInteractiveDecisions(patterns);
            interactiveDecisionManager.saveDecisions();
        }

        // Set discovered patterns in the redaction engine
        redactionEngine.setDiscoveredPatterns(patterns);
    }

    /**
     * Core processing logic - processes a RecordingFile with optional on-the-fly discovery.
     * Uses Supplier to allow re-reading the file without storing all events in memory.
     */
    private RecordingImpl processRecordingFile(java.util.function.Supplier<RecordingFile> inputSupplier,
                                               OutputStream outputStream,
                                               PatternDiscoveryEngine onTheFlyDiscovery) throws IOException {
        initRecording(outputStream);

        // PHASE 1: Optional discovery pass (if onTheFlyDiscovery provided)
        if (onTheFlyDiscovery != null) {
            logger.info("Phase 1: Running pattern discovery");
            try (RecordingFile discoveryInput = inputSupplier.get()) {
                runDiscoveryPass(discoveryInput, onTheFlyDiscovery);
            }

            // Update redaction engine with discovered patterns
            redactionEngine.setDiscoveredPatterns(onTheFlyDiscovery.getDiscoveredPatterns());
            logger.info(onTheFlyDiscovery.getStatistics());
        }

        // PHASE 2: Processing pass - register types and write events
        logger.info("Phase {}: Reading events, registering types, and writing output",
                onTheFlyDiscovery != null ? 2 : 1);

        int totalEvents = 0;
        int removedEvents = 0;
        int written = 0;

        try (RecordingFile processingInput = inputSupplier.get()) {
            while (processingInput.hasMoreEvents()) {
                var event = processingInput.readEvent();
                totalEvents++;

                String eventTypeName = event.getEventType().getName();
                redactionEngine.getStats().recordEvent(eventTypeName);

                if (redactionEngine.shouldRemoveEvent(event)) {
                    removedEvents++;
                    redactionEngine.getStats().recordRemovedEvent(eventTypeName);
                    logger.debug("Removed event #{}: {}", removedEvents, eventTypeName);
                    continue; // Skip this event
                }

                // Register event type (will be idempotent if already registered)
                registerEventType(event);

                // Write event immediately (no need to store in memory)
                writeEvent(event);
                written++;

                if (written % 10000 == 0) {
                    logger.info("Written {} events ({} removed)", written, removedEvents);
                }
            }
        }

        logger.info("JFR processing complete: {} total events, {} processed, {} removed",
                totalEvents, written, removedEvents);
        return output;
    }

    /**
     * Run discovery pass over events without storing them in memory or registering types.
     * This pass only analyzes events for pattern discovery.
     */
    private void runDiscoveryPass(RecordingFile input, PatternDiscoveryEngine discoveryEngine) throws IOException {
        int discoveryEvents = 0;
        int removedEvents = 0;

        while (input.hasMoreEvents()) {
            var event = input.readEvent();
            discoveryEvents++;

            String eventTypeName = event.getEventType().getName();

            // Only discover from events that won't be removed
            if (redactionEngine.shouldRemoveEvent(event)) {
                removedEvents++;
                continue;
            }

            // Analyze event for pattern discovery
            discoveryEngine.analyzeEvent(event);

            // Log progress periodically
            if (discoveryEvents % 100000 == 0) {
                logger.info("  Discovery: analyzed {} events ({} removed)",
                        discoveryEvents - removedEvents, removedEvents);
            }
        }

        logger.info("Discovery pass complete: analyzed {} events ({} removed)",
                discoveryEvents - removedEvents, removedEvents);
    }

    /**
     * Processes multiple recording files and concatenates them into a single output without redaction or discovery.
     * Processes multiple recording files and concatenates them into a single output.
     */
    public RecordingImpl processRecordingFilesWithoutAnyProcessing(List<RecordingFile> inputs, OutputStream outputStream) throws IOException {
        initRecording(outputStream);

        int totalEvents = 0;
        int fileIndex = 0;

        for (RecordingFile input : inputs) {
            fileIndex++;
            logger.info("Processing input recording file {}/{}", fileIndex, inputs.size());
            int fileEvents = 0;
            long startTime = System.currentTimeMillis();

            while (input.hasMoreEvents()) {
                var event = input.readEvent();
                totalEvents++;
                fileEvents++;

                // Register event type (will be idempotent if already registered)
                registerEventType(event);

                writeEvent(event);

                // Log progress every 100,000 events
                if (fileEvents % 100000 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.info("  Processed {} events", fileEvents);
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("  Completed file {}: {} events in {} ms",
                    fileIndex, fileEvents, elapsed);
        }

        logger.info("JFR concatenation complete: {} total events written from {} file(s)",
                totalEvents, inputs.size());
        return output;
    }

    /**
     * Register an event type without writing the event.
     * This is part of phase 1 of the two-pass processing.
     */
    private void registerEventType(RecordedEvent event) {
        EventType eventType = event.getEventType();
        String eventTypeName = eventType.getName();

        // Slow path: check if already registered in output
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
        // For StackFrame, we want to have it inline
        return output.getTypes().getOrAdd(typeName, null, useConstantPool(descriptor), builder -> {
            for (ValueDescriptor field : descriptor.getFields()) {
                addFieldToTypeBuilder(builder, field, typesCurrentlyAdding);
            }
            addAnnotationsToTypeBuilder(builder, descriptor.getAnnotationElements());
        });
    }

    private Method isConstantPoolMethod = null;
    private boolean isConstantPoolMethodAccessible = true;

    boolean useConstantPool(ValueDescriptor descriptor) {
        if (!isConstantPoolMethodAccessible) {
            // Previous attempt to access method failed, use fallback
            return useFallbackConstantPoolHeuristic(descriptor);
        }
        if (isConstantPoolMethod != null) {
            // Method already accessed successfully before
            try {
                return (boolean) isConstantPoolMethod.invoke(descriptor);
            } catch (InvocationTargetException | IllegalAccessException | InaccessibleObjectException e) {
                // Cannot access the method due to module restrictions or other access issues
                logger.debug("Cannot access isConstantPool() method: {}, using fallback heuristic", e.getMessage());
                isConstantPoolMethodAccessible = false;
                return useFallbackConstantPoolHeuristic(descriptor);
            }
        }
        try {
            // Try to access the package private method descriptor.isConstantPool()
            isConstantPoolMethod = ValueDescriptor.class.getDeclaredMethod("isConstantPool");
            isConstantPoolMethod.setAccessible(true);
            return (boolean) isConstantPoolMethod.invoke(descriptor);
        } catch (NoSuchMethodException e) {
            // Method doesn't exist in this JDK version
            logger.debug("Method isConstantPool() not found, using fallback heuristic");
            isConstantPoolMethodAccessible = false;
            return useFallbackConstantPoolHeuristic(descriptor);
        } catch (InvocationTargetException | IllegalAccessException | InaccessibleObjectException e) {
            // Cannot access the method due to module restrictions or other access issues
            logger.debug("Cannot access isConstantPool() method: {}, using fallback heuristic", e.getMessage());
            isConstantPoolMethodAccessible = false;
            return useFallbackConstantPoolHeuristic(descriptor);
        }
    }

    /**
     * Fallback heuristic for determining if a type should use constant pool.
     * StackFrame should NOT use constant pool (should be inline).
     * Most other types should use constant pool for deduplication.
     */
    private boolean useFallbackConstantPoolHeuristic(ValueDescriptor descriptor) {
        String typeName = descriptor.getTypeName();
        // StackFrame should be inline (not in constant pool)
        return !"jdk.types.StackFrame".equals(typeName);
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
     * Applies redaction to String values.
     */
    private void putAnnotationField(TypedValueBuilder builder, String fieldName, Object value) {
        if (value == null) {
            return; // Skip null values in annotations
        }

        // Handle arrays with pattern matching
        if (value.getClass().isArray()) {
            handleArrayValueForAnnotation(builder, fieldName, value);
        } else {
            // Handle primitives and strings
            handlePrimitiveOrWrapperValue(builder, fieldName, value);
        }
    }

    /**
     * Helper method to handle primitive and wrapper type values with redaction.
     *
     * @param builder The value builder to put the field into
     * @param fieldName The name of the field
     * @param value The primitive or wrapper value
     */
    private void handlePrimitiveOrWrapperValue(TypedValueBuilder builder, String fieldName, Object value) {
        switch (value) {
            case Byte v -> builder.putField(fieldName, redactionEngine.redact(fieldName, v));
            case Short v -> builder.putField(fieldName, redactionEngine.redact(fieldName, v));
            case Integer v -> builder.putField(fieldName, redactionEngine.redact(fieldName, v));
            case Long v -> builder.putField(fieldName, redactionEngine.redact(fieldName, v));
            case Float v -> builder.putField(fieldName, redactionEngine.redact(fieldName, v));
            case Double v -> builder.putField(fieldName, redactionEngine.redact(fieldName, v));
            case Boolean v -> builder.putField(fieldName, redactionEngine.redact(fieldName, v));
            case Character v -> builder.putField(fieldName, redactionEngine.redact(fieldName, v));
            case String v -> builder.putField(fieldName, redactionEngine.redact(fieldName, v));
            default -> {
                throw new UnsupportedOperationException(
                            "Unsupported annotation field type: " + value.getClass().getName()
                );
            }
        }
    }

    /**
     * Helper method to handle array values with redaction applied.
     * Handles primitive arrays, String arrays, and optionally Object arrays.
     *
     * @param builder The value builder to put the field into
     * @param fieldName The name of the field
     * @param value The array value
     * @param field The field descriptor (optional, only needed for Object[] handling)
     * @return true if the array was handled, false if it needs special processing
     */
    private boolean handleArrayWithRedaction(TypedValueBuilder builder, String fieldName, Object value, ValueDescriptor field) {
        switch (value) {
            case byte[] arr -> builder.putField(fieldName, redactionEngine.redact(fieldName, arr));
            case short[] arr -> builder.putField(fieldName, redactionEngine.redact(fieldName, arr));
            case int[] arr -> builder.putField(fieldName, redactionEngine.redact(fieldName, arr));
            case long[] arr -> builder.putField(fieldName, redactionEngine.redact(fieldName, arr));
            case float[] arr -> builder.putField(fieldName, redactionEngine.redact(fieldName, arr));
            case double[] arr -> builder.putField(fieldName, redactionEngine.redact(fieldName, arr));
            case boolean[] arr -> builder.putField(fieldName, redactionEngine.redact(fieldName, arr));
            case char[] arr -> builder.putField(fieldName, redactionEngine.redact(fieldName, arr));
            case String[] arr -> builder.putField(fieldName, redactionEngine.redact(fieldName, arr));
            case Object[] arr -> {
                // For annotations, convert to string; for fields, handle as complex objects
                if (field != null) {
                    builder.putField(fieldName, createComplexArrayValue(field, fieldName, arr));
                } else {
                    logger.warn("Unsupported array type in annotation: {}", value.getClass().getName());
                    builder.putField(fieldName, redactionEngine.redact(fieldName, value.toString()));
                }
            }
            default -> {
                return false; // Not handled
            }
        }
        return true; // Successfully handled
    }

    /**
     * Helper method to handle array values for annotations.
     * Simpler version that doesn't handle Object[] as complex types.
     */
    private void handleArrayValueForAnnotation(TypedValueBuilder builder, String fieldName, Object value) {
        if (!handleArrayWithRedaction(builder, fieldName, value, null)) {
            // Not handled - log warning and convert to string
            logger.warn("Unsupported array type in annotation: {}", value.getClass().getName());
            builder.putField(fieldName, redactionEngine.redact(fieldName, value.toString()));
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
        if (nonNullFields.size() == 1 && nonNullFields.getFirst().getName().equals("value")) {
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
        if (nonNullFields.size() == 1 && nonNullFields.getFirst().getName().equals("value")) {
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

        if (value instanceof RecordedObject) {
            // Complex type
            handleComplexValue(valueBuilder, descriptor, (RecordedObject) value);
            return;
        }
        handlePrimitiveOrWrapperValue(valueBuilder, fieldName, value);
    }

    private void handleArrayValue(TypedValueBuilder valueBuilder, ValueDescriptor field, Object value) {
        String fieldName = field.getName();
        // Arrays in JFR can be primitive arrays or object arrays
        if (value.getClass().isArray()) {
            // Use shared array handling method
            handleArrayWithRedaction(valueBuilder, fieldName, value, field);
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
                    handleComplexValueInArray(b, field, recordedObject);
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

    private void handleComplexValueInArray(TypedValueBuilder valueBuilder,
                                    ValueDescriptor descriptor, jdk.jfr.consumer.RecordedObject recordedObject) {
        try {
            for (ValueDescriptor field : descriptor.getFields()) {
                try {
                    Object fieldValue = recordedObject.getValue(field.getName());
                    addFieldValue(valueBuilder, field, fieldValue);
                } catch (IllegalArgumentException e) {
                    // Field might not be present in this particular recordedObject
                    // Set it to null value
                    valueBuilder.putField(field.getName(),
                            valueBuilder.getType().getField(field.getName()).getType().nullValue());
                }
            }
        } catch (IllegalStateException ex) {
            System.err.println("Warning: Could not process complex field '" + descriptor.getName() + "'");
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