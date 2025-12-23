package me.bechberger.jfrredact.jfr;

import jdk.jfr.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.config.StringConfig;
import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        @Name("jdk.SystemProcess")
        class SystemProcessEvent extends Event {
            String processName = "test-process";
        }

        Path inputPath = helper.recording()
                .addEvent(() -> {
                    SimpleEvent simple = new SimpleEvent();
                    simple.message = "Keep this";
                    simple.commit();

                    SystemProcessEvent systemProcess = new SystemProcessEvent();
                    systemProcess.commit();
                })
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .hasNoEventOfType("jdk.SystemProcess")
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

    // ========== On-The-Fly Discovery Tests ==========
    // NOTE: On-the-fly discovery requires proper configuration of extraction patterns.

    @Test
    public void testFastDiscoveryMode_BasicFunctionality() throws IOException {
        // Verify that fast discovery mode processes files without errors
        Path inputPath = helper.recording()
                .addSimpleEvent("Hello World", 42, true)
                .addSimpleEvent("Goodbye World", 43, false)
                .build();

        // Process with fast discovery enabled (should complete without errors)
        helper.verify(helper.process()
                        .from(inputPath)
                        .withFastDiscovery(null)
                        .process())
                .fileExists()
                .fileNotEmpty()
                .hasEvents()
                .hasEventOfType("test.SimpleEvent", 2);
    }

    @Test
    public void testComprehensiveDiscoveryMode() throws IOException {
        // Test comprehensive (two-pass) discovery mode
        Path inputPath = helper.recording()
                .addSimpleEvent("Processing item_123", 1, true)
                .addSimpleEvent("Processing item_456", 2, true)
                .build();

        // Process with comprehensive discovery
        helper.verify(helper.process()
                        .from(inputPath)
                        .withComprehensiveDiscovery(null)
                        .process())
                .fileExists()
                .hasEvents();
    }

    @Test
    public void testOnTheFlyDiscovery_EmailsAndIPs() throws IOException {
        // Create events with emails and IPs
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    for (int i = 0; i < 3; i++) {
                        SensitiveDataEvent e = new SensitiveDataEvent();
                        e.username = "user" + i;
                        e.password = "pass" + i;
                        e.email = "admin@internal.com"; // Same email repeated
                        e.ipAddress = "10.0.0." + i;
                        e.commit();
                    }
                })
                .build();

        // Process with on-the-fly discovery
        helper.verify(helper.process()
                .from(inputPath)
                .withFastDiscovery(config -> {
                    config.getDiscovery().setEnabled(true);
                    config.getDiscovery().setMinOccurrencesDefault(2);
                })
                .process())
                .allEvents(events -> {
                    // Email appears 3 times, should be discovered and redacted
                    boolean emailRedacted = events.stream()
                            .allMatch(e -> {
                                String email = e.getString("email");
                                return email != null && !email.contains("admin@internal.com");
                            });
                    Assertions.assertTrue(emailRedacted, "Email should be redacted");
                });
    }

    @Test
    public void testOnTheFlyDiscovery_MinOccurrencesThreshold() throws IOException {
        // Create events where some values appear frequently, others don't
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    // "frequent_value" appears 5 times
                    for (int i = 0; i < 5; i++) {
                        SimpleEvent e = new SimpleEvent();
                        e.message = "Log: frequent_value detected";
                        e.commit();
                    }

                    // "rare_value" appears only once
                    SimpleEvent rare = new SimpleEvent();
                    rare.message = "Log: rare_value detected";
                    rare.commit();
                })
                .build();

        // Set min_occurrences to 3
        helper.verify(helper.process()
                .from(inputPath)
                .withFastDiscovery(config -> {
                    config.getDiscovery().setEnabled(true);
                    config.getDiscovery().setMinOccurrencesDefault(3);
                })
                .process())
                .allEvents(events -> {
                    long frequentRedacted = events.stream()
                            .filter(e -> {
                                String msg = e.getString("message");
                                return msg != null && !msg.contains("frequent_value");
                            })
                            .count();

                    long rareNotRedacted = events.stream()
                            .filter(e -> {
                                String msg = e.getString("message");
                                return msg != null && msg.contains("rare_value");
                            })
                            .count();

                    Assertions.assertTrue(frequentRedacted > 0, "frequent_value should be redacted");
                    Assertions.assertEquals(1, rareNotRedacted, "rare_value should NOT be redacted");
                });
    }

    @Test
    public void testOnTheFlyDiscovery_MixedPatternTypes() throws IOException {
        // Create events with various pattern types
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    SimpleEvent e1 = new SimpleEvent();
                    e1.message = "Contact admin@example.com or visit 10.0.0.5";
                    e1.commit();

                    SimpleEvent e2 = new SimpleEvent();
                    e2.message = "User alice logged in from 10.0.0.5";
                    e2.commit();

                    SimpleEvent e3 = new SimpleEvent();
                    e3.message = "Email admin@example.com sent notification";
                    e3.commit();
                })
                .build();

        // With on-the-fly discovery
        helper.verify(helper.process()
                .from(inputPath)
                .withFastDiscovery(config -> {
                    config.getDiscovery().setEnabled(true);
                    config.getDiscovery().setMinOccurrencesDefault(2);
                    // Also enable configured patterns
                    config.getStrings().setEnabled(true);
                })
                .process())
                .allEvents(events -> {
                    // Both configured patterns (email, IP) and discovered patterns should work
                    boolean allRedacted = events.stream()
                            .allMatch(e -> {
                                String msg = e.getString("message");
                                if (msg == null) return true;
                                // Should not contain the original values
                                return !msg.contains("admin@example.com") &&
                                       !msg.contains("10.0.0.5");
                            });
                    Assertions.assertTrue(allRedacted, "All sensitive data should be redacted");
                });
    }

    @Test
    public void testOnTheFlyDiscovery_PathsAndFilenames() throws IOException {
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    SimpleEvent e1 = new SimpleEvent();
                    e1.message = "File /home/john_doe/project/data.txt modified";
                    e1.commit();

                    SimpleEvent e2 = new SimpleEvent();
                    e2.message = "File /home/john_doe/project/config.xml created";
                    e2.commit();

                    SimpleEvent e3 = new SimpleEvent();
                    e3.message = "File /home/alice/document.pdf opened";
                    e3.commit();
                })
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withFastDiscovery(config -> {
                    config.getDiscovery().setEnabled(true);
                    config.getPaths().setEnabled(true);
                })
                .process())
                .allEvents(events -> {
                    // john_doe appears twice in paths, should be redacted
                    boolean johnRedacted = events.stream()
                            .filter(e -> e.getString("message") != null)
                            .filter(e -> e.getString("message").contains("/home/"))
                            .allMatch(e -> !e.getString("message").contains("john_doe"));
                    Assertions.assertTrue(johnRedacted, "john_doe in paths should be redacted");
                });
    }

    @Test
    public void testOnTheFlyDiscovery_CustomPatterns() throws IOException {
        // Test that custom patterns work with on-the-fly discovery
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    // Create events with custom identifiers
                    SimpleEvent e1 = new SimpleEvent();
                    e1.message = "Transaction ID: TXN-12345-ABC processed";
                    e1.commit();

                    SimpleEvent e2 = new SimpleEvent();
                    e2.message = "Transaction ID: TXN-12345-ABC completed";
                    e2.commit();

                    SimpleEvent e3 = new SimpleEvent();
                    e3.message = "Transaction ID: TXN-67890-XYZ started";
                    e3.commit();
                })
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withFastDiscovery(config -> {
                    config.getDiscovery().setEnabled(true);
                    config.getDiscovery().setMinOccurrencesDefault(2);
                    // Add custom pattern for transaction IDs
                    var customPattern = new me.bechberger.jfrredact.config.StringConfig.CustomPatternConfig();
                    customPattern.setName("transaction_id");
                    customPattern.getPatterns().add("TXN-[0-9A-Z-]+");
                    config.getStrings().getPatterns().getCustom().add(customPattern);
                })
                .process())
                .allEvents(events -> {
                    // TXN-12345-ABC appears twice, should be redacted by custom pattern
                    boolean txnRedacted = events.stream()
                            .filter(e -> e.getString("message") != null)
                            .filter(e -> e.getString("message").contains("Transaction"))
                            .allMatch(e -> !e.getString("message").contains("TXN-12345-ABC"));
                    Assertions.assertTrue(txnRedacted, "Transaction ID should be redacted by custom pattern");
                });
    }

    @Test
    public void testOnTheFlyDiscovery_CustomPatternWithDiscovery() throws IOException {
        // Test combining custom patterns with discovered patterns
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    // Events with both custom pattern matches and discoverable values
                    SimpleEvent e1 = new SimpleEvent();
                    e1.message = "API Key: api_key_abc123 used by service_alpha";
                    e1.commit();

                    SimpleEvent e2 = new SimpleEvent();
                    e2.message = "API Key: api_key_xyz789 used by service_alpha";
                    e2.commit();

                    SimpleEvent e3 = new SimpleEvent();
                    e3.message = "Request from service_alpha completed";
                    e3.commit();
                })
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withFastDiscovery(config -> {
                    config.getDiscovery().setEnabled(true);
                    config.getDiscovery().setMinOccurrencesDefault(2);
                    // Add custom pattern for API keys
                    var customPattern = new StringConfig.CustomPatternConfig();
                    customPattern.setName("api_key");
                    customPattern.getPatterns().add("api_key_[a-z0-9]+");
                    config.getStrings().getPatterns().getCustom().add(customPattern);
                    // Add custom pattern for service identifiers with discovery enabled
                    var servicePattern = new StringConfig.CustomPatternConfig();
                    servicePattern.setName("service_identifier");
                    servicePattern.getPatterns().add("service_[a-z_]+");
                    servicePattern.setEnableDiscovery(true);
                    servicePattern.setDiscoveryMinOccurrences(2);
                    config.getStrings().getPatterns().getCustom().add(servicePattern);
                })
                .process())
                .allEvents(events -> {
                    // Both API keys should be redacted by custom pattern
                    boolean apiKeysRedacted = events.stream()
                            .filter(e -> e.getString("message") != null)
                            .allMatch(e -> {
                                String msg = e.getString("message");
                                return !msg.contains("api_key_abc123") && !msg.contains("api_key_xyz789");
                            });
                    Assertions.assertTrue(apiKeysRedacted, "API keys should be redacted by custom pattern");

                    // service_alpha appears 3 times, should be discovered and redacted
                    boolean serviceRedacted = events.stream()
                            .filter(e -> e.getString("message") != null)
                            .filter(e -> e.getString("message").contains("service"))
                            .allMatch(e -> !e.getString("message").contains("service_alpha"));
                    Assertions.assertTrue(serviceRedacted, "service_alpha should be discovered and redacted");
                });
    }

    @Test
    public void testOnTheFlyDiscovery_CustomPatternIgnoreList() throws IOException {
        // Test that custom patterns respect ignore lists during discovery
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    SimpleEvent e1 = new SimpleEvent();
                    e1.message = "Server localhost responded";
                    e1.commit();

                    SimpleEvent e2 = new SimpleEvent();
                    e2.message = "Server localhost timeout";
                    e2.commit();

                    SimpleEvent e3 = new SimpleEvent();
                    e3.message = "Server prod-server-01 responded";
                    e3.commit();
                })
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withFastDiscovery(config -> {
                    config.getDiscovery().setEnabled(true);
                    config.getDiscovery().setMinOccurrencesDefault(2);
                    // Add custom pattern for server names with ignore list
                    var customPattern = new StringConfig.CustomPatternConfig();
                    customPattern.setName("server_name");
                    customPattern.getPatterns().add("(localhost|[a-z0-9-]+)");
                    customPattern.getIgnoreExact().add("localhost"); // Don't redact localhost
                    config.getStrings().getPatterns().getCustom().add(customPattern);
                })
                .process())
                .allEvents(events -> {
                    // localhost should NOT be redacted (in ignore list)
                    long localhostCount = events.stream()
                            .filter(e -> e.getString("message") != null)
                            .filter(e -> e.getString("message").contains("localhost"))
                            .count();
                    Assertions.assertEquals(2, localhostCount, "localhost should not be redacted");
                });
    }

    @Test
    public void testOnTheFlyDiscovery_MultipleConcurrentPatterns() throws IOException {
        // Test that multiple pattern types work together in discovery
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    for (int i = 0; i < 4; i++) {
                        SensitiveDataEvent e = new SensitiveDataEvent();
                        e.username = "admin_user"; // Repeated 4 times
                        e.password = "secret_" + i; // Different each time
                        e.email = "admin@company.com"; // Repeated 4 times
                        e.ipAddress = "10.0.0." + (i % 2); // Two IPs, each repeated twice
                        e.commit();
                    }
                })
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withFastDiscovery(config -> {
                    config.getDiscovery().setEnabled(true);
                    config.getDiscovery().setMinOccurrencesDefault(2);
                    config.getStrings().setEnabled(true); // Enable built-in patterns
                    // Add custom pattern
                    var customPattern = new StringConfig.CustomPatternConfig();
                    customPattern.setName("secret");
                    customPattern.getPatterns().add("secret_[0-9]+");
                    config.getStrings().getPatterns().getCustom().add(customPattern);
                })
                .process())
                .allEvents(events -> {
                    Assertions.assertEquals(4, events.size(), "Should have 4 events");

                    // Check all redactions happened
                    for (RecordedEvent e : events) {
                        String username = e.getString("username");
                        String password = e.getString("password");
                        String email = e.getString("email");
                        String ip = e.getString("ipAddress");

                        // admin_user appears 4 times - should be discovered
                        Assertions.assertNotNull(username);
                        Assertions.assertNotEquals("admin_user", username, "Username should be redacted");

                        // Passwords matched by custom pattern
                        Assertions.assertNotNull(password);
                        Assertions.assertFalse(password.startsWith("secret_"), "Password should be redacted");

                        // Email matched by built-in pattern
                        Assertions.assertNotNull(email);
                        Assertions.assertNotEquals("admin@company.com", email, "Email should be redacted");

                        // IP matched by built-in pattern
                        Assertions.assertNotNull(ip);
                        Assertions.assertFalse(ip.startsWith("10.0.0."), "IP should be redacted");
                    }
                });
    }

    @Test
    public void testOnTheFlyDiscovery_CaseSensitivity() throws IOException {
        // Test case sensitivity in discovery
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    SimpleEvent e1 = new SimpleEvent();
                    e1.message = "User ADMIN logged in";
                    e1.commit();

                    SimpleEvent e2 = new SimpleEvent();
                    e2.message = "User admin logged out";
                    e2.commit();

                    SimpleEvent e3 = new SimpleEvent();
                    e3.message = "User Admin created file";
                    e3.commit();
                })
                .build();

        // Case-insensitive discovery
        helper.verify(helper.process()
                .from(inputPath)
                .withFastDiscovery(config -> {
                    config.getDiscovery().setEnabled(true);
                    config.getDiscovery().setCaseSensitive(false);
                    config.getDiscovery().setMinOccurrencesDefault(2);
                    // Add custom pattern to extract usernames
                    var customPattern = new StringConfig.CustomPatternConfig();
                    customPattern.setName("username");
                    customPattern.getPatterns().add("User (\\w+) ");
                    customPattern.setEnableDiscovery(true);
                    customPattern.setDiscoveryCaseSensitive(false);
                    customPattern.setDiscoveryMinOccurrences(2);
                    config.getStrings().getPatterns().getCustom().add(customPattern);
                })
                .process())
                .allEvents(events -> {
                    // All variants of "admin" should be treated as the same value
                    // and since there are 3 occurrences total, should be redacted
                    boolean allRedacted = events.stream()
                            .allMatch(e -> {
                                String msg = e.getString("message");
                                return msg != null &&
                                       !msg.toLowerCase().contains("admin logged") &&
                                       !msg.toLowerCase().contains("admin created");
                            });
                    Assertions.assertTrue(allRedacted, "All case variants of admin should be redacted");
                });
    }
}