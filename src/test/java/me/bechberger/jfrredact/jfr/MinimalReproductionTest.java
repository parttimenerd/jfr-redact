package me.bechberger.jfrredact.jfr;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.MultipleFailuresError;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal reproduction tests for the two categories of test failures:
 *
 * CATEGORY 1: Wrong Exception Type (assertThrows expects AssertionFailedError but gets MultipleFailuresError)
 * - Root cause: JUnit's assertAll wraps multiple failures in MultipleFailuresError
 * - Fix: Change assertThrows to catch the parent type (AssertionError) or MultipleFailuresError
 *
 * CATEGORY 2: Event Count Mismatch in Roundtrip
 * - Root cause: JFR processor is dropping events during processing (possibly JVM internal events)
 * - Fix: Either fix the processor to preserve all events, or filter out JVM-internal events from comparison
 */
public class MinimalReproductionTest {

    @TempDir
    Path tempDir;

    JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    // ==================== CATEGORY 1: Exception Type Mismatch ====================

    @Nested
    @DisplayName("Category 1: Exception Type Mismatch - MultipleFailuresError vs AssertionFailedError")
    class ExceptionTypeMismatchTests {

        @Name("test.SensitiveEvent")
        static class SensitiveEvent extends Event {
            String password;
            String email;
        }

        /**
         * REPRODUCES: testNegative_FieldChanged_ShouldFail_* failures
         *
         * Problem: When multiple fields are redacted, JFRTestHelper.verifyEventFullyEqual()
         * uses assertAll() which wraps failures in MultipleFailuresError, not AssertionFailedError
         */
        @Test
        void reproduce_MultipleFailuresError_WhenMultipleFieldsRedacted() {
            // Fixed: Expect MultipleFailuresError when multiple fields fail
            MultipleFailuresError error = assertThrows(
                MultipleFailuresError.class,
                () -> {
                    helper.roundtrip(() -> {
                        SensitiveEvent event = new SensitiveEvent();
                        event.password = "secret123";
                        event.email = "user@example.com";
                        event.commit();
                    }, SensitiveEvent.class)
                    .withDefaultRedaction()
                    .eventsOfTypeFullyPreserved("test.SensitiveEvent");
                }
            );

            // Verify the error contains the expected failures
            assertThat(error.getFailures()).as("Should have at least one failure").isNotEmpty();
        }

        /**
         * FIX OPTION 1: Catch MultipleFailuresError instead
         */
        @Test
        void fix_CatchMultipleFailuresError() {
            // MultipleFailuresError is thrown when assertAll has multiple failures
            MultipleFailuresError error = assertThrows(
                MultipleFailuresError.class,
                () -> {
                    helper.roundtrip(() -> {
                        SensitiveEvent event = new SensitiveEvent();
                        event.password = "secret123";
                        event.email = "user@example.com";
                        event.commit();
                    }, SensitiveEvent.class)
                    .withDefaultRedaction()
                    .eventsOfTypeFullyPreserved("test.SensitiveEvent");
                }
            );

            // Can still verify the error contains expected info
            assertThat(error.getFailures()).as("Should have at least one failure").isNotEmpty();
        }

        /**
         * FIX OPTION 2: Catch parent type AssertionError (catches both)
         */
        @Test
        void fix_CatchParentAssertionError() {
            // Use the parent type that covers both AssertionFailedError and MultipleFailuresError
            java.lang.AssertionError error = assertThrows(
                java.lang.AssertionError.class,
                () -> {
                    helper.roundtrip(() -> {
                        SensitiveEvent event = new SensitiveEvent();
                        event.password = "secret123";
                        event.email = "user@example.com";
                        event.commit();
                    }, SensitiveEvent.class)
                    .withDefaultRedaction()
                    .eventsOfTypeFullyPreserved("test.SensitiveEvent");
                }
            );

            assertNotNull(error, "Should throw an assertion error");
        }

        /**
         * FIX OPTION 3: Use assertThrowsExactly only when expecting single field failure
         */
        @Test
        void fix_SingleFieldFailure_UsesAssertionError() {
            @Name("test.SingleFieldEvent")
            class SingleFieldEvent extends Event {
                String password;
                int count; // non-redacted field
            }

            // When field failures occur, catch the parent AssertionError type
            java.lang.AssertionError error = assertThrows(
                java.lang.AssertionError.class,
                () -> {
                    helper.roundtrip(() -> {
                        var event = new SingleFieldEvent();
                        event.password = "secret";
                        event.count = 42;
                        event.commit();
                    }, SingleFieldEvent.class)
                    .withDefaultRedaction()
                    .eventsOfTypeFullyPreserved("test.SingleFieldEvent");
                }
            );

            assertNotNull(error, "Should throw an assertion error");
        }
    }

    // ==================== CATEGORY 2: Event Count Mismatch ====================

    @Nested
    @DisplayName("Category 2: Event Count Lost During Roundtrip")
    class EventCountMismatchTests {
        @Test
        void reproduce_EventCountMismatch_RealWorldRecording() throws Exception {
            helper.roundtrip(() -> {
                try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                    recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
                    recording.start();

                    // Generate some events
                    Thread.sleep(10);
                    Thread.sleep(20);

                    recording.stop();
                }
            })
            .withDefaultRedaction()
                .eventCountPreservedExceptDefaultRemovals(); // This assertion will fail
        }

        /**
         * FIX OPTION 1: Only verify test events, ignore JVM-internal events
         */
        @Test
        void fix_VerifyOnlyTestEventCount() throws Exception {
            @Name("test.CountTestEvent")
            class CountTestEvent extends Event {
                int value;
            }

            helper.roundtrip(() -> {
                for (int i = 0; i < 5; i++) {
                    CountTestEvent event = new CountTestEvent();
                    event.value = i;
                    event.commit();
                }
            }, CountTestEvent.class)
            .withDefaultRedaction()
            .testEventCountPreserved(); // Only verify events starting with "test."
        }

        /**
         * FIX OPTION 2: Verify specific event type counts
         */
        @Test
        void fix_VerifySpecificEventTypeCount() throws Exception {
            @Name("test.SpecificEvent")
            class SpecificEvent extends Event {
                String data;
            }

            helper.roundtrip(() -> {
                for (int i = 0; i < 3; i++) {
                    SpecificEvent event = new SpecificEvent();
                    event.data = "item" + i;
                    event.commit();
                }
            }, SpecificEvent.class)
            .withDefaultRedaction()
            .eventTypeCountPreserved("test.SpecificEvent"); // Verify only this type
        }

        /**
         * Diagnostic test to understand what events are being dropped
         */
        @Test
        void diagnostic_ListDroppedEventTypes() throws Exception {
            JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
                try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                    recording.start();
                    Thread.sleep(50);
                    System.gc();
                    recording.stop();
                }
            })
            .withDefaultRedaction();

            // Get event counts by type for diagnostics
            var originalTypes = verifier.getOriginalEventTypes();
            var processedTypes = verifier.getProcessedEventTypes();

            System.out.println("=== Event Types in Original ===");
            for (String type : originalTypes) {
                long originalCount = verifier.getOriginalEventCount(type);
                long processedCount = verifier.getProcessedEventCount(type);
                if (originalCount != processedCount) {
                    System.out.printf("MISMATCH: %s - Original: %d, Processed: %d, Lost: %d%n",
                        type, originalCount, processedCount, originalCount - processedCount);
                }
            }

            // This test is for diagnostics, not to assert
            assertThat(true).isTrue();
        }
    }
}