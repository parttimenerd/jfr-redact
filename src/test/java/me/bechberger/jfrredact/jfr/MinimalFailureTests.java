package me.bechberger.jfrredact.jfr;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Minimal test cases to reproduce each category of failures.
 * Each test focuses on a single failure mode for easy debugging.
 */
public class MinimalFailureTests {

    @TempDir
    Path tempDir;

    // ========== Category 1: Thread Field Data Loss ==========

    /**
     * Category 1: Simplest test to reproduce thread field data loss.
     * ALL JFR events have an implicit 'eventThread' field added by the JFR runtime.
     * The issue is that javaThreadId and virtual fields are missing after roundtrip.
     */
    @Name("test.MinimalThreadEvent")
    static class MinimalThreadEvent extends Event {
        // No explicit thread field needed - JFR adds eventThread implicitly
        String message;
    }

    @Test
    public void testCategory1_ThreadFieldDataLoss() throws IOException {
        JFRTestHelper helper = new JFRTestHelper(tempDir);

        helper.roundtrip(() -> {
            MinimalThreadEvent event = new MinimalThreadEvent();
            event.message = "test";
            event.commit();
        })
        .withoutRedaction()
        .eventsOfTypeFullyPreserved("test.MinimalThreadEvent");

        // Current failures (not data loss, but metadata issues):
        // 1. startTime missing @Timestamp annotation
        // 2. duration missing @Timespan annotation
        // 3. stackTrace.frames representation mismatch
    }

    // ========== Category 2: Null Annotation Values ==========

    /**
     * Category 2: Simplest test to reproduce null annotation value error.
     * Uses @DataAmount annotation without a value parameter.
     */
    @Name("test.MinimalAnnotatedEvent")
    static class MinimalAnnotatedEvent extends Event {
        @jdk.jfr.DataAmount
        long dataSize;

        @jdk.jfr.Frequency
        long frequency;

        @jdk.jfr.MemoryAddress
        long memoryAddress;

        @jdk.jfr.Percentage
        float percentage;
    }

    @Test
    public void testCategory2_NullAnnotationValues() throws IOException {
        JFRTestHelper helper = new JFRTestHelper(tempDir);

        helper.roundtrip(() -> {
            MinimalAnnotatedEvent event = new MinimalAnnotatedEvent();
            event.dataSize = 1024L;
            event.frequency = 60L;
            event.memoryAddress = 0x1000L;
            event.percentage = 0.5f;
            event.commit();
        })
        .withoutRedaction()
        .eventsOfTypeFullyPreserved("test.MinimalAnnotatedEvent");

        // This test will FAIL until Category 2 is fixed
        // Expected error: IllegalArgumentException: Annotation value can't be null
    }

    // ========== Category 3: Null ValueDescriptor ==========

    /**
     * Category 3: Simplest test to reproduce null descriptor error.
     * Uses a real JDK event that has implicit fields.
     */
    @Test
    public void testCategory3_NullValueDescriptor() throws IOException {
        JFRTestHelper helper = new JFRTestHelper(tempDir);

        helper.roundtrip(() -> {
            // Use Recording API to enable JDK events which have implicit fields
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
                recording.start();

                // Generate JDK ThreadSleep event which has implicit fields
                Thread.sleep(10);

                recording.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        })
        .withoutRedaction()
        .eventCountPreserved();

        // This test will FAIL until Category 3 is fixed
        // Expected error: NullPointerException: Cannot invoke "jdk.jfr.ValueDescriptor.getTypeName()"
        //   because "descriptor" is null
        // This happens when processing JDK events with implicit fields that have null descriptors
    }

    // ========== Combined Test: All Three Categories ==========

    /**
     * Combined test that should fail with all three categories of errors.
     * Useful for verifying that all fixes are in place.
     */
    @Name("test.CombinedEvent")
    static class CombinedEvent extends Event {
        // Category 1: eventThread is implicit - all JFR events have it

        @jdk.jfr.DataAmount          // Category 2: Null annotation
        long dataSize;
    }

    @Test
    public void testAllCategories_Combined() throws IOException {
        JFRTestHelper helper = new JFRTestHelper(tempDir);

        helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
                recording.start();

                // Category 3: Real JDK event with implicit fields
                Thread.sleep(10);

                recording.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Categories 1 & 2: Custom event with implicit thread field and annotations
            CombinedEvent event = new CombinedEvent();
            event.dataSize = 1024L;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.CombinedEvent");

        // This test exercises all three failure categories
        // It will fail if ANY of the three categories is not fixed
    }
}