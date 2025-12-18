package me.bechberger.jfrredact.jfr;

import jdk.jfr.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmc.flightrecorder.writer.RecordingImpl;
import org.openjdk.jmc.flightrecorder.writer.api.Recordings;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static me.bechberger.jfrredact.jfr.util.JFRTestEvents.*;

/**
 * Comprehensive tests for JFRProcessor using the test framework utilities.
 * Tests include basic processing, redaction, and roundtrip scenarios.
 */
public class JFRProcessorTest {

    @TempDir
    Path tempDir;

    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    // ========== Basic Functionality Tests ==========

    @Test
    public void testProcessSimpleEvent() throws IOException {
        Path inputPath = helper.recording()
                .addSimpleEvent("Hello World", 42, true)
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .fileExists()
                .fileNotEmpty()
                .hasEvents()
                .findEvent("test.SimpleEvent")
                .hasString("message", "Hello World")
                .hasInt("count", 42)
                .hasBoolean("flag", true);
    }

    @Test
    public void testRedactSensitiveData() throws IOException {
        Path inputPath = helper.recording()
                .addSensitiveEvent("john_doe", "super_secret_password", "john@example.com", "192.168.1.100")
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .findEvent("test.SensitiveDataEvent")
                .hasString("password", "***")
                .stringContains("email", "***")
                .stringDoesNotContain("email", "john@example.com")
                .stringContains("ipAddress", "***")
                .stringDoesNotContain("ipAddress", "192.168.1.100");
    }

    @Test
    public void testEventRemoval() throws IOException {
        @Name("jdk.OSInformation")
        class OSInfoEvent extends Event {
            String osName = "Linux";
        }

        Path inputPath = helper.recording()
                .addEvent(() -> {
                    SimpleEvent simple = new SimpleEvent();
                    simple.message = "Keep this";
                    simple.commit();

                    OSInfoEvent osInfo = new OSInfoEvent();
                    osInfo.commit();
                })
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .hasNoEventOfType("jdk.OSInformation")
                .hasEventOfType("test.SimpleEvent", 1);
    }

    // ========== Complex Event Tests ==========

    @Test
    public void testComplexEventAllTypes() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "test string";
            event.intField = 42;
            event.longField = 9223372036854775807L;
            event.floatField = 3.14f;
            event.doubleField = 2.71828;
            event.booleanField = true;
            event.byteField = (byte) 127;
            event.shortField = (short) 32767;
            event.charField = 'X';
            event.commit();
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .findEvent("test.ComplexEvent")
                .hasString("stringField", "test string")
                .hasInt("intField", 42)
                .hasLong("longField", 9223372036854775807L)
                .hasFloat("floatField", 3.14f, 0.001f)
                .hasDouble("doubleField", 2.71828, 0.00001)
                .hasBoolean("booleanField", true)
                .hasByte("byteField", (byte) 127)
                .hasShort("shortField", (short) 32767)
                .hasChar("charField", 'X');
    }

    // ========== Network Event Tests ==========

    @Test
    public void testNetworkEventRedaction() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            NetworkEvent event = new NetworkEvent();
            event.sourceAddress = "192.168.1.10";
            event.destinationAddress = "10.0.0.5";
            event.sourcePort = 45678;
            event.destinationPort = 443;
            event.protocol = "TCP";
            event.commit();
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withPseudonymization()
                .process())
                .findEvent("test.NetworkEvent")
                .isRedacted("sourceAddress")
                .isRedacted("destinationAddress")
                .intNotEquals("sourcePort", 45678)
                .intNotEquals("destinationPort", 443)
                .hasString("protocol", "TCP");
    }

    // ========== Roundtrip Tests Without Redaction ==========

    @Test
    public void testRoundtripWithoutRedaction_SimpleEvents() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 10; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = "Message " + i;
                event.count = i;
                event.flag = i % 2 == 0;
                event.commit();
            }
        })
        .withoutRedaction()
        .eventCountPreserved()
        .testEventCountPreserved()
        .eventTypeCountPreserved("test.SimpleEvent")
        .eventsOfTypeFullyPreserved("test.SimpleEvent"); // Comprehensive verification
    }

    @Test
    public void testRoundtripWithoutRedaction_ComplexEvents() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "roundtrip test";
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
        .eventsOfTypeFullyPreserved("test.ComplexEvent"); // Comprehensive verification
    }

    @Test
    public void testRoundtripWithoutRedaction_PreservesEventOrder() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 3; i++) {
                SimpleEvent simple = new SimpleEvent();
                simple.message = "Simple " + i;
                simple.count = i;
                simple.commit();

                NetworkEvent network = new NetworkEvent();
                network.sourceAddress = "10.0.0." + i;
                network.destinationAddress = "192.168.1." + i;
                network.sourcePort = 1000 + i;
                network.destinationPort = 2000 + i;
                network.protocol = "TCP";
                network.commit();
            }
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventOrderPreserved()
        .eventTypeCountPreserved("test.SimpleEvent")
        .eventTypeCountPreserved("test.NetworkEvent")
        .eventsOfTypeFullyPreserved("test.SimpleEvent")  // Comprehensive verification
        .eventsOfTypeFullyPreserved("test.NetworkEvent"); // Comprehensive verification
    }

    // ========== Roundtrip Tests With Redaction ==========

    @Test
    public void testRoundtripWithRedaction_SensitiveData() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 5; i++) {
                SensitiveDataEvent event = new SensitiveDataEvent();
                event.username = "user" + i;
                event.password = "password" + i;
                event.email = "user" + i + "@example.com";
                event.ipAddress = "192.168.1." + i;
                event.commit();
            }
        })
        .withDefaultRedaction()
        .eventCountPreserved()
        .testEventCountPreserved()
        .eventTypeCountPreserved("test.SensitiveDataEvent")
        .fieldChanged("test.SensitiveDataEvent", "password")
        .fieldChanged("test.SensitiveDataEvent", "email")
        .fieldChanged("test.SensitiveDataEvent", "ipAddress");
    }

    @Test
    public void testRoundtripWithPseudonymization_NetworkData() throws IOException {
        helper.roundtrip(() -> {
            NetworkEvent event = new NetworkEvent();
            event.sourceAddress = "10.0.0.100";
            event.destinationAddress = "192.168.1.50";
            event.sourcePort = 12345;
            event.destinationPort = 443;
            event.protocol = "HTTPS";
            event.commit();
        })
        .withPseudonymization()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.NetworkEvent")
        .fieldChanged("test.NetworkEvent", "sourceAddress")
        .fieldChanged("test.NetworkEvent", "destinationAddress")
        .fieldPreserved("test.NetworkEvent", "protocol");
    }

    @Test
    public void testRoundtripWithStrictRedaction() throws IOException {
        helper.roundtrip(() -> {
            SensitiveDataEvent event = new SensitiveDataEvent();
            event.username = "admin";
            event.password = "supersecret";
            event.email = "admin@company.com";
            event.ipAddress = "10.20.30.40";
            event.commit();
        })
        .withStrictRedaction()
        .eventCountPreserved()
        .fieldChanged("test.SensitiveDataEvent", "password")
        .fieldChanged("test.SensitiveDataEvent", "email")
        .fieldChanged("test.SensitiveDataEvent", "ipAddress");
    }

    // ========== Edge Cases ==========

    @Test
    public void testEmptyRecording() throws IOException {
        Path emptyPath = helper.createTestRecording(() -> {
            // No events
        });

        helper.verify(helper.process()
                .from(emptyPath)
                .withDefaultEngine()
                .process())
                .fileExists()
                .hasTestEventCount(0);
    }

    @Test
    public void testNullFieldValues() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = null;  // Null string
            event.count = 0;
            event.flag = false;
            event.commit();
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .findEvent("test.SimpleEvent")
                .fieldIsNull("message");
    }

    @Test
    public void testRoundtripWithNullValues() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = null;
            event.count = 0;
            event.flag = false;
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent"); // Deep equality check
    }

    @Test
    public void testRoundtripWithMultipleEventTypes() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent simple = new SimpleEvent();
            simple.message = "Simple";
            simple.count = 1;
            simple.flag = true;
            simple.commit();

            ComplexEvent complex = new ComplexEvent();
            complex.stringField = "Complex";
            complex.intField = 42;
            complex.commit();

            NetworkEvent network = new NetworkEvent();
            network.sourceAddress = "10.0.0.1";
            network.protocol = "TCP";
            network.commit();

            // Note: ArrayEvent is not included because JFR does not persist array fields
            // in custom events. While arrays can be set on Event objects in code,
            // the JDK's JFR implementation does not serialize them to the recording file.
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventOrderPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent")     // Deep equality
        .eventsOfTypeFullyPreserved("test.ComplexEvent")    // Deep equality
        .eventsOfTypeFullyPreserved("test.NetworkEvent");   // Deep equality
    }

    // ========== Additional Deep Equality Tests ==========

    @Test
    public void testRoundtrip_AllNumericTypes() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "numeric test";
            event.intField = Integer.MIN_VALUE;
            event.longField = Long.MAX_VALUE;
            event.floatField = Float.MIN_VALUE;
            event.doubleField = Double.MAX_VALUE;
            event.booleanField = true;
            event.byteField = Byte.MAX_VALUE;
            event.shortField = Short.MIN_VALUE;
            event.charField = 'Z';
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    @Test
    public void testRoundtrip_BoundaryValues() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "";  // Empty string
            event.intField = 0;
            event.longField = 0L;
            event.floatField = 0.0f;
            event.doubleField = 0.0;
            event.booleanField = false;
            event.byteField = 0;
            event.shortField = 0;
            event.charField = '\0';
            event.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    @Test
    public void testRoundtrip_SpecialFloatingPointValues() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event1 = new ComplexEvent();
            event1.stringField = "NaN test";
            event1.floatField = Float.NaN;
            event1.doubleField = Double.NaN;
            event1.commit();

            ComplexEvent event2 = new ComplexEvent();
            event2.stringField = "Infinity test";
            event2.floatField = Float.POSITIVE_INFINITY;
            event2.doubleField = Double.NEGATIVE_INFINITY;
            event2.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    @Test
    public void testRoundtrip_MixedNullAndNonNull() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 5; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = (i % 2 == 0) ? null : "Message " + i;
                event.count = i;
                event.flag = i % 2 == 0;
                event.commit();
            }
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void testRoundtrip_UnicodeAndSpecialCharacters() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event1 = new SimpleEvent();
            event1.message = "Unicode: 你好世界 🎉 αβγδ";
            event1.count = 1;
            event1.commit();

            SimpleEvent event2 = new SimpleEvent();
            event2.message = "Special: \n\t\r\"'\\";
            event2.count = 2;
            event2.commit();

            SimpleEvent event3 = new SimpleEvent();
            event3.message = "Emoji: 😀🎨🚀🌟";
            event3.count = 3;
            event3.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void testRoundtrip_LargeEventCount() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 100; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = "Event " + i;
                event.count = i;
                event.flag = i % 3 == 0;
                event.commit();
            }
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventOrderPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void testRoundtrip_InterleavedEventTypes() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 10; i++) {
                SimpleEvent simple = new SimpleEvent();
                simple.message = "Simple " + i;
                simple.count = i;
                simple.commit();

                NetworkEvent network = new NetworkEvent();
                network.sourceAddress = "192.168.1." + i;
                network.destinationAddress = "10.0.0." + i;
                network.sourcePort = 1000 + i;
                network.destinationPort = 2000 + i;
                network.protocol = (i % 2 == 0) ? "TCP" : "UDP";
                network.commit();

                ComplexEvent complex = new ComplexEvent();
                complex.stringField = "Complex " + i;
                complex.intField = i * 100;
                complex.longField = i * 1000L;
                complex.commit();
            }
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventOrderPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent")
        .eventsOfTypeFullyPreserved("test.NetworkEvent")
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    @Test
    public void testRoundtrip_AllEventsFullyPreserved() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent simple = new SimpleEvent();
            simple.message = "test";
            simple.count = 42;
            simple.flag = true;
            simple.commit();

            ComplexEvent complex = new ComplexEvent();
            complex.stringField = "complex";
            complex.intField = 123;
            complex.longField = 456L;
            complex.floatField = 1.23f;
            complex.doubleField = 4.56;
            complex.booleanField = false;
            complex.byteField = (byte) 7;
            complex.shortField = (short) 89;
            complex.charField = 'A';
            complex.commit();
        })
        .withoutRedaction()
        .eventCountPreserved()
        .allEventsFullyPreserved(); // Verify all events at once
    }

    @Test
    public void testRoundtrip_SensitiveData_WithRedaction() throws IOException {
        helper.roundtrip(() -> {
            SensitiveDataEvent event = new SensitiveDataEvent();
            event.username = "john_doe";
            event.password = "secret123";
            event.email = "john@example.com";
            event.ipAddress = "192.168.1.100";
            event.commit();
        })
        .withDefaultRedaction()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.SensitiveDataEvent")
        .fieldChanged("test.SensitiveDataEvent", "password")
        .fieldChanged("test.SensitiveDataEvent", "email")
        .fieldChanged("test.SensitiveDataEvent", "ipAddress");
        // Note: Cannot use eventsOfTypeFullyPreserved here because redaction changes values
    }

    @Test
    public void testRoundtrip_NetworkData_WithPseudonymization() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 3; i++) {
                NetworkEvent event = new NetworkEvent();
                event.sourceAddress = "10.0.0." + i;
                event.destinationAddress = "192.168.1." + i;
                event.sourcePort = 1000 + i;
                event.destinationPort = 2000 + i;
                event.protocol = "TCP";
                event.commit();
            }
        })
        .withPseudonymization()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.NetworkEvent")
        .fieldChanged("test.NetworkEvent", "sourceAddress")
        .fieldChanged("test.NetworkEvent", "destinationAddress")
        .fieldPreserved("test.NetworkEvent", "protocol");
        // Note: Cannot use eventsOfTypeFullyPreserved because pseudonymization changes values
    }

    @Test
    public void testToStringOnParsedEvent() throws IOException {
        Path inputPath = helper.recording()
                .addSimpleEvent("Test ToString", 7, false)
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .findEvent("test.SimpleEvent")
                .run(e -> {
                    testEventStackTraceFrames(e);
                    Assertions.assertDoesNotThrow(e.getStackTrace().getFrames().getFirst()::toString);
                    Assertions.assertDoesNotThrow(e.getStackTrace()::toString);
                    Assertions.assertDoesNotThrow(e::toString);
                });
    }

    @Test
    public void testToStringOnParsedEventWithoutRoundtrip() throws IOException {
        Path inputPath = helper.recording()
                .addSimpleEvent("Test ToString", 7, false)
                .build();
        RecordedEvent event = RecordingFile.readAllEvents(inputPath).getFirst();
        testEventStackTraceFrames(event);
        RecordingFile recordingFile = new RecordingFile(inputPath);
        Path path = tempDir.resolve("output.jfr");
        recordingFile.write(path, e -> true);
        event = RecordingFile.readAllEvents(path).getFirst();
        testEventStackTraceFrames(event);
    }

    private static void testEventStackTraceFrames(RecordedEvent event) {
        Assertions.assertDoesNotThrow(event::getStackTrace);
        RecordedStackTrace stackTrace = event.getStackTrace();
        Assertions.assertDoesNotThrow(stackTrace::getFrames);
    }

    @Test
    public void testRoundtrip_EventsWithStackTracesFullyEqual() throws IOException {
        // This test will FAIL if stack frames use constant pool,
        // because the deep comparison requires exact field-by-field equality
        @Name("test.EventWithStackTrace")
        @StackTrace(true)
        class EventWithStackTrace extends Event {
            @Label("Message")
            String message;

            @Label("Value")
            int value;
        }

        helper.roundtrip(() -> {
            EventWithStackTrace event = new EventWithStackTrace();
            event.message = "Test stack trace preservation";
            event.value = 42;
            event.commit();
        }, EventWithStackTrace.class)
        .withoutRedaction()
        .verifyEventsFullyEqual(); // This will fail if stack frames aren't properly copied
    }

    @Test
    public void testEventFieldAccessAfterProcessing() throws IOException {
        Path inputPath = helper.recording()
                .addSimpleEvent("Test Field Access", 7, false)
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .findEvent("test.SimpleEvent")
                .run(e -> {
                    testEventStackTraceFrames(e);
                    Assertions.assertDoesNotThrow(e.getStackTrace().getFrames().getFirst()::toString);
                    Assertions.assertDoesNotThrow(e.getStackTrace()::toString);
                    Assertions.assertDoesNotThrow(e::toString);
                });
    }
}