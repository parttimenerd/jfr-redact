package me.bechberger.jfrredact.jfr.util;

import jdk.jfr.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static me.bechberger.jfrredact.jfr.util.JFRTestEvents.*;

/**
 * Tests for the JFR test framework itself, specifically testing the deep equality
 * verification methods to ensure they correctly detect differences in:
 * - Field values
 * - Field metadata (names, labels, descriptions, types)
 * - Field annotations and their values
 * - Event type annotations
 * - Nullability
 *
 * This also demonstrates the use of custom event classes via the test framework API.
 */
public class JFRTestFrameworkVerificationTest {

    @TempDir
    Path tempDir;

    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    // ========== Custom Test-Specific Event Classes ==========

    /**
     * Custom event with comprehensive JFR annotations for testing annotation preservation
     */
    @Name("test.FullyAnnotatedCustomEvent")
    @Label("Fully Annotated Custom Event")
    @Description("Custom event with all possible annotations for testing")
    @Category({"Test", "Verification", "Custom"})
    @StackTrace(false)
    @Threshold("100 ms")
    public static class FullyAnnotatedCustomEvent extends Event {
        @Label("String Field")
        @Description("A string field for testing")
        public String stringField;

        @Label("Integer Field")
        @Description("An integer field")
        public int intField;

        @Label("Timestamp Field")
        @Timestamp
        public long timestampField;

        @Label("Duration Field")
        @Timespan
        public long durationField;

        @Label("Memory Address")
        @MemoryAddress
        public long memoryAddress;

        @Label("Percentage")
        @Percentage
        public float percentage;

        @Label("Data Amount")
        @DataAmount
        public long dataAmount;
    }

    /**
     * Custom event specifically for testing nullable field handling
     */
    @Name("test.NullableFieldsCustomEvent")
    @Label("Custom Event with Nullable Fields")
    public static class NullableFieldsCustomEvent extends Event {
        @Label("Nullable String")
        public String nullableString;

        @Label("Non-null String")
        public String nonNullString;

        @Label("Nullable Integer")
        public Integer nullableInteger;
    }

    /**
     * Custom sensitive event for testing redaction detection
     */
    @Name("test.CustomSensitiveEvent")
    @Label("Custom Sensitive Event")
    public static class CustomSensitiveEvent extends Event {
        @Label("Password")
        public String password;

        @Label("Email")
        public String email;

        @Label("API Key")
        public String apiKey;
    }

    // ========== Tests Demonstrating Custom Event Class Support ==========

    @Test
    public void testCustomEventClass_FullyAnnotated() throws IOException {
        // Demonstrates using custom event classes with comprehensive annotations
        helper.roundtrip(() -> {
            FullyAnnotatedCustomEvent event = new FullyAnnotatedCustomEvent();
            event.stringField = "test";
            event.intField = 42;
            event.timestampField = System.currentTimeMillis();
            event.durationField = 1000000L;
            event.memoryAddress = 0x12345678L;
            event.percentage = 0.75f;
            event.dataAmount = 1024L;
            event.commit();
        }, FullyAnnotatedCustomEvent.class)  // Register custom class
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.FullyAnnotatedCustomEvent");
    }

    @Test
    public void testCustomEventClass_NullableFields() throws IOException {
        // Custom event specifically designed for nullable field testing
        helper.roundtrip(() -> {
            NullableFieldsCustomEvent event = new NullableFieldsCustomEvent();
            event.nullableString = null;
            event.nonNullString = "not null";
            event.nullableInteger = null;
            event.commit();
        }, NullableFieldsCustomEvent.class)
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.NullableFieldsCustomEvent");
    }

    @Test
    public void testCustomEventClass_MixedNullability() throws IOException {
        helper.roundtrip(() -> {
            NullableFieldsCustomEvent event1 = new NullableFieldsCustomEvent();
            event1.nullableString = "value1";
            event1.nonNullString = "always set";
            event1.nullableInteger = 123;
            event1.commit();

            NullableFieldsCustomEvent event2 = new NullableFieldsCustomEvent();
            event2.nullableString = null;
            event2.nonNullString = "always set";
            event2.nullableInteger = null;
            event2.commit();
        }, NullableFieldsCustomEvent.class)
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.NullableFieldsCustomEvent");
    }

    @Test
    public void testMixedStandardAndCustomEvents() throws IOException {
        // Test using both standard test events and custom events together
        helper.roundtrip(() -> {
            // Standard event - automatically enabled
            SimpleEvent standard = new SimpleEvent();
            standard.message = "standard";
            standard.count = 1;
            standard.flag = true;
            standard.commit();

            // Custom event - needs registration
            FullyAnnotatedCustomEvent custom = new FullyAnnotatedCustomEvent();
            custom.stringField = "custom";
            custom.intField = 2;
            custom.timestampField = System.currentTimeMillis();
            custom.durationField = 500000L;
            custom.memoryAddress = 0x1000L;
            custom.percentage = 0.5f;
            custom.dataAmount = 512L;
            custom.commit();
        }, FullyAnnotatedCustomEvent.class)  // Only need to register custom class
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent")
        .eventsOfTypeFullyPreserved("test.FullyAnnotatedCustomEvent");
    }

    // ========== Tests Using Standard Events for General Functionality ==========

    @Test
    public void testDeepEquality_SimpleEvent_NoRedaction() throws IOException {
        // Use standard SimpleEvent for basic testing
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "test";
            event.count = 42;
            event.flag = true;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void testDeepEquality_ComplexEvent_AllTypes() throws IOException {
        // Use standard ComplexEvent to test all primitive types
        helper.roundtrip(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "complex test";
            event.intField = 999;
            event.longField = 123456789L;
            event.floatField = 1.23f;
            event.doubleField = 4.56;
            event.booleanField = true;
            event.byteField = (byte) 42;
            event.shortField = (short) 1000;
            event.charField = 'R';
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    @Test
    public void testDeepEquality_MultipleEvents_SameType() throws IOException {
        // Use standard SimpleEvent for multiple event testing
        helper.roundtrip(() -> {
            for (int i = 0; i < 5; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = "Message " + i;
                event.count = i * 10;
                event.flag = i % 2 == 0;
                event.commit();
            }
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void testDeepEquality_AllEvents_NoRedaction() throws IOException {
        // Use standard events for testing allEventsFullyPreserved
        helper.roundtrip(() -> {
            SimpleEvent simple = new SimpleEvent();
            simple.message = "test";
            simple.count = 1;
            simple.flag = true;
            simple.commit();

            ComplexEvent complex = new ComplexEvent();
            complex.stringField = "value";
            complex.intField = 42;
            complex.longField = 123L;
            complex.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .allEventsFullyPreserved();
    }

    // ========== Tests for Nullability Using Standard Events ==========

    @Test
    public void testNullability_StandardEvent_NullFields() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = null;  // Null string
            event.count = 0;
            event.flag = false;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void testNullability_CustomEvent_ComprehensiveTesting() throws IOException {
        // Use custom nullable event for more thorough nullability testing
        helper.roundtrip(() -> {
            NullableFieldsCustomEvent event = new NullableFieldsCustomEvent();
            event.nullableString = null;
            event.nonNullString = "value";
            event.nullableInteger = null;
            event.commit();
        }, NullableFieldsCustomEvent.class)
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.NullableFieldsCustomEvent");
    }

    // ========== Tests for Annotation Verification Using Custom Events ==========

    @Test
    public void testAnnotations_EventTypeAnnotations() throws IOException {
        // Use custom event with comprehensive annotations to test annotation preservation
        helper.roundtrip(() -> {
            FullyAnnotatedCustomEvent event = new FullyAnnotatedCustomEvent();
            event.stringField = "annotation test";
            event.intField = 1;
            event.timestampField = System.currentTimeMillis();
            event.durationField = 1000L;
            event.memoryAddress = 0x1000L;
            event.percentage = 0.5f;
            event.dataAmount = 100L;
            event.commit();
        }, FullyAnnotatedCustomEvent.class)
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.FullyAnnotatedCustomEvent");
    }

    @Test
    public void testAnnotations_FieldAnnotations_StandardEvent() throws IOException {
        // Use standard AnnotatedEvent for basic annotation testing
        helper.roundtrip(() -> {
            AnnotatedEvent event = new AnnotatedEvent();
            event.eventTime = System.currentTimeMillis();
            event.duration2 = 1000L;
            event.dataSize = 1024L;
            event.frequency = 60L;
            event.memoryAddress = 0x1000L;
            event.percentage = 0.5f;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.AnnotatedEvent");
    }

    // ========== Tests for Field Metadata Using Standard Events ==========

    @Test
    public void testFieldMetadata_AllMetadata() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "metadata test";
            event.intField = 12345;
            event.longField = 999999L;
            event.floatField = 1.23f;
            event.doubleField = 4.56;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    // ========== Tests for Event Order Preservation ==========

    @Test
    public void testEventOrder_PreservedWithDeepEquality() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 10; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = "Event " + i;
                event.count = i;
                event.flag = i % 2 == 0;
                event.commit();
            }
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventOrderPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    // ========== Tests for Edge Cases ==========

    @Test
    public void testEdgeCase_EmptyStrings() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "";  // Empty string
            event.count = 0;
            event.flag = false;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void testEdgeCase_LargeNumbers() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "large numbers";
            event.intField = Integer.MAX_VALUE;
            event.longField = Long.MAX_VALUE;
            event.floatField = Float.MAX_VALUE;
            event.doubleField = Double.MAX_VALUE;
            event.byteField = Byte.MAX_VALUE;
            event.shortField = Short.MAX_VALUE;
            event.charField = Character.MAX_VALUE;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    @Test
    public void testEdgeCase_SpecialCharacters() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "Special: \n\t\r\"'\\";
            event.count = -1;
            event.flag = true;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void testEdgeCase_UnicodeCharacters() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "Unicode: 你好世界 🎉 αβγδ";
            event.count = 42;
            event.flag = true;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    // ========== Tests for Multiple Event Types ==========

    @Test
    public void testMultipleEventTypes_AllFullyPreserved() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent simple = new SimpleEvent();
            simple.message = "simple event";
            simple.count = 1;
            simple.flag = true;
            simple.commit();

            ComplexEvent complex = new ComplexEvent();
            complex.stringField = "complex event";
            complex.intField = 2;
            complex.longField = 200L;
            complex.commit();

            NetworkEvent network = new NetworkEvent();
            network.sourceAddress = "10.0.0.1";
            network.destinationAddress = "192.168.1.1";
            network.sourcePort = 80;
            network.destinationPort = 443;
            network.protocol = "TCP";
            network.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent")
        .eventsOfTypeFullyPreserved("test.ComplexEvent")
        .eventsOfTypeFullyPreserved("test.NetworkEvent")
        .allEventsFullyPreserved();
    }

    // ========== Tests Verifying Framework Behavior with Redaction ==========

    @Test
    public void testWithDefaultRedaction_EventCountStillPreserved() throws IOException {
        // Even with redaction, event count should be preserved
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "test message";
            event.count = 100;
            event.flag = true;
            event.commit();
        })
        .withDefaultRedaction()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.SimpleEvent");
        // Note: We don't call eventsOfTypeFullyPreserved here because redaction may change field values
    }

    @Test
    public void testWithStrictRedaction_EventCountStillPreserved() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "strict test";
            event.count = 200;
            event.flag = false;
            event.commit();
        })
        .withStrictRedaction()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.SimpleEvent");
    }

    @Test
    public void testWithPseudonymization_EventCountStillPreserved() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "pseudo test";
            event.count = 300;
            event.flag = true;
            event.commit();
        })
        .withPseudonymization()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.SimpleEvent");
    }

    // ========== NEGATIVE TESTS: Verifying Framework Detects Differences ==========

    @Test
    public void testNegative_FieldChanged_ShouldFail_CustomEvent() {
        // Use custom sensitive event to test redaction detection
        // Use AssertionError as parent of both AssertionFailedError and MultipleFailuresError
        AssertionError error = assertThrows(AssertionError.class, () -> {
            helper.roundtrip(() -> {
                CustomSensitiveEvent event = new CustomSensitiveEvent();
                event.password = "secret123";
                event.email = "user@example.com";
                event.apiKey = "sk-12345";
                event.commit();
            }, CustomSensitiveEvent.class)
            .withDefaultRedaction()
            .eventsOfTypeFullyPreserved("test.CustomSensitiveEvent");
        });

        assertTrue(error.getMessage().contains("Field value") ||
                   error.getMessage().contains("failure"),
                "Error message should indicate field value mismatch");
    }

    @Test
    public void testNegative_FieldChanged_ShouldFail_StandardEvent() {
        // Use standard SensitiveDataEvent to test redaction detection
        // Use AssertionError as parent of both AssertionFailedError and MultipleFailuresError
        AssertionError error = assertThrows(AssertionError.class, () -> {
            helper.roundtrip(() -> {
                SensitiveDataEvent event = new SensitiveDataEvent();
                event.password = "secret123";
                event.email = "user@example.com";
                event.username = "testuser";
                event.ipAddress = "192.168.1.1";
                event.commit();
            })
            .withDefaultRedaction()
            .eventsOfTypeFullyPreserved("test.SensitiveDataEvent");
        });

        assertTrue(error.getMessage().contains("Field value") ||
                   error.getMessage().contains("failure"),
                "Error message should indicate field value mismatch");
    }

    @Test
    public void testNegative_FieldChanged_WithPseudonymization() {
        // Use AssertionError as parent of both AssertionFailedError and MultipleFailuresError
        AssertionError error = assertThrows(AssertionError.class, () -> {
            helper.roundtrip(() -> {
                SimpleEvent event = new SimpleEvent();
                event.message = "user@example.com";  // Will be pseudonymized
                event.count = 100;
                event.flag = true;
                event.commit();
            })
            .withPseudonymization()
            .eventsOfTypeFullyPreserved("test.SimpleEvent");
        });

        assertTrue(error.getMessage().contains("Field value") ||
                   error.getMessage().contains("failure"),
                "Error message should indicate field mismatch due to pseudonymization");
    }

    @Test
    public void testNegative_AllEventsFullyPreserved_WithRedaction() {
        // Use AssertionError as parent of both AssertionFailedError and MultipleFailuresError
        AssertionError error = assertThrows(AssertionError.class, () -> {
            helper.roundtrip(() -> {
                SensitiveDataEvent event = new SensitiveDataEvent();
                event.password = "mypassword";
                event.email = "test@test.com";
                event.username = "admin";
                event.ipAddress = "10.0.0.1";
                event.commit();
            })
            .withDefaultRedaction()
            .allEventsFullyPreserved();
        });

        assertNotNull(error, "Should throw assertion error when fields are redacted");
    }

    // ========== POSITIVE TESTS: Verifying Correct Success Cases ==========

    @Test
    public void testPositive_NoRedaction_NoException() {
        assertDoesNotThrow(() -> {
            helper.roundtrip(() -> {
                SimpleEvent event = new SimpleEvent();
                event.message = "unchanged";
                event.count = 123;
                event.flag = true;
                event.commit();
            })
            .withoutRedaction()
            .eventsOfTypeFullyPreserved("test.SimpleEvent");
        });
    }

    @Test
    public void testPositive_CustomEvent_NoRedaction_NoException() {
        assertDoesNotThrow(() -> {
            helper.roundtrip(() -> {
                FullyAnnotatedCustomEvent event = new FullyAnnotatedCustomEvent();
                event.stringField = "test";
                event.intField = 42;
                event.timestampField = System.currentTimeMillis();
                event.durationField = 1000L;
                event.memoryAddress = 0x1000L;
                event.percentage = 0.5f;
                event.dataAmount = 100L;
                event.commit();
            }, FullyAnnotatedCustomEvent.class)
            .withoutRedaction()
            .eventsOfTypeFullyPreserved("test.FullyAnnotatedCustomEvent");
        });
    }

    @Test
    public void testPositive_NullFields_NoException() {
        assertDoesNotThrow(() -> {
            helper.roundtrip(() -> {
                NullableFieldsCustomEvent event = new NullableFieldsCustomEvent();
                event.nullableString = null;
                event.nonNullString = "value";
                event.nullableInteger = null;
                event.commit();
            }, NullableFieldsCustomEvent.class)
            .withoutRedaction()
            .eventsOfTypeFullyPreserved("test.NullableFieldsCustomEvent");
        });
    }

    @Test
    public void testPositive_AllEventsFullyPreserved_NoException() {
        assertDoesNotThrow(() -> {
            helper.roundtrip(() -> {
                SimpleEvent event1 = new SimpleEvent();
                event1.message = "first";
                event1.count = 1;
                event1.flag = true;
                event1.commit();

                SimpleEvent event2 = new SimpleEvent();
                event2.message = "second";
                event2.count = 2;
                event2.flag = false;
                event2.commit();
            })
            .withoutRedaction()
            .allEventsFullyPreserved();
        });
    }
}