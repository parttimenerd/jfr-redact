package me.bechberger.jfrredact.jfr;

import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;

import static me.bechberger.jfrredact.jfr.util.JFRTestEvents.*;

/**
 * Complex integration and scenario tests for JFR processing.
 * Tests realistic workflows and edge cases.
 */
public class JFRProcessorComplexTest {

    @TempDir
    Path tempDir;

    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    @Test
    public void testLargeRecordingWithMultipleEventTypes() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            // Create 100 events of each type
            for (int i = 0; i < 100; i++) {
                SimpleEvent simple = new SimpleEvent();
                simple.message = "Message " + i;
                simple.count = i;
                simple.flag = i % 2 == 0;
                simple.commit();

                SensitiveDataEvent sensitive = new SensitiveDataEvent();
                sensitive.username = "user" + i;
                sensitive.password = "password" + i;
                sensitive.email = "user" + i + "@example.com";
                sensitive.ipAddress = "192.168.1." + (i % 256);
                sensitive.commit();

                NetworkEvent network = new NetworkEvent();
                network.sourceAddress = "10.0.0." + (i % 256);
                network.destinationAddress = "172.16.0." + (i % 256);
                network.sourcePort = 1024 + i;
                network.destinationPort = 80;
                network.protocol = "TCP";
                network.commit();
            }
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .fileExists()
                .fileNotEmpty()
                .hasEvents()
                .hasEventOfType("test.SimpleEvent", 100)
                .hasEventOfType("test.SensitiveDataEvent", 100)
                .hasEventOfType("test.NetworkEvent", 100);
    }

    @Test
    public void testPseudonymizationConsistency() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            // Same sensitive values across multiple events
            for (int i = 0; i < 5; i++) {
                SensitiveDataEvent event = new SensitiveDataEvent();
                event.username = "alice";  // Same username
                event.password = "secret123";  // Same password
                event.email = "alice@example.com";  // Same email
                event.ipAddress = "192.168.1.100";  // Same IP
                event.commit();
            }

            // Different values
            for (int i = 0; i < 5; i++) {
                SensitiveDataEvent event = new SensitiveDataEvent();
                event.username = "bob";
                event.password = "different";
                event.email = "bob@example.com";
                event.ipAddress = "192.168.1.200";
                event.commit();
            }
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withPseudonymization()
                .process())
                .hasEventOfType("test.SensitiveDataEvent", 10)
                // All alice events should have same redacted values
                .findEvent("test.SensitiveDataEvent")
                .isRedacted("password");
    }

    @Test
    public void testMixedSensitiveAndNonSensitiveData() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            SensitiveDataEvent event = new SensitiveDataEvent();
            event.username = "normaluser";  // Not sensitive by itself
            event.password = "super_secret_password_123!";  // Sensitive
            event.email = "user@company.com";  // Sensitive
            event.ipAddress = "Production server at 192.168.1.50";  // Contains IP
            event.commit();

            SimpleEvent simple = new SimpleEvent();
            simple.message = "This message contains no secrets";
            simple.count = 42;
            simple.flag = true;
            simple.commit();
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .findEvent("test.SensitiveDataEvent")
                .hasString("password", "***")
                .isRedacted("email")
                .isRedacted("ipAddress")
                .and()
                .findEvent("test.SimpleEvent")
                .hasString("message", "This message contains no secrets")
                .hasInt("count", 42)
                .hasBoolean("flag", true);
    }

    @Test
    public void testEventRemovalPreservesOtherEvents() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            // Events that should be kept
            for (int i = 0; i < 10; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = "Keep this " + i;
                event.count = i;
                event.flag = true;
                event.commit();
            }

            // Sensitive events that might be removed
            for (int i = 0; i < 5; i++) {
                SensitiveDataEvent event = new SensitiveDataEvent();
                event.username = "user" + i;
                event.password = "password" + i;
                event.email = "user" + i + "@example.com";
                event.ipAddress = "192.168.1." + i;
                event.commit();
            }
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .hasEventOfType("test.SimpleEvent", 10)
                .hasEventOfType("test.SensitiveDataEvent", 5);
    }

    @Test
    public void testComplexEventWithAllFieldTypes() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "complex test";
            event.intField = Integer.MAX_VALUE;
            event.longField = Long.MAX_VALUE;
            event.floatField = Float.MAX_VALUE;
            event.doubleField = Double.MAX_VALUE;
            event.booleanField = true;
            event.byteField = Byte.MAX_VALUE;
            event.shortField = Short.MAX_VALUE;
            event.charField = 'Z';
            event.commit();
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .findEvent("test.ComplexEvent")
                .hasString("stringField", "complex test")
                .hasInt("intField", Integer.MAX_VALUE)
                .hasLong("longField", Long.MAX_VALUE)
                .hasBoolean("booleanField", true)
                .hasByte("byteField", Byte.MAX_VALUE)
                .hasShort("shortField", Short.MAX_VALUE)
                .hasChar("charField", 'Z');
    }

    @Test
    public void testRoundtripWithLargeDataset() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 50; i++) {
                SimpleEvent simple = new SimpleEvent();
                simple.message = "Test message number " + i;
                simple.count = i * 10;
                simple.flag = i % 3 == 0;
                simple.commit();

                ComplexEvent complex = new ComplexEvent();
                complex.stringField = "Complex " + i;
                complex.intField = i;
                complex.longField = i * 1000L;
                complex.floatField = i * 0.5f;
                complex.doubleField = i * 0.25;
                complex.booleanField = i % 2 == 0;
                complex.byteField = (byte) (i % 128);
                complex.shortField = (short) i;
                complex.charField = (char) ('A' + (i % 26));
                complex.commit();
            }
        })
        .withoutRedaction()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.SimpleEvent")
        .eventTypeCountPreserved("test.ComplexEvent")
        .fieldPreserved("test.SimpleEvent", "message")
        .fieldPreserved("test.SimpleEvent", "count")
        .fieldPreserved("test.ComplexEvent", "stringField")
        .fieldPreserved("test.ComplexEvent", "intField");
    }

    @Test
    public void testRoundtripWithDefaultRedaction() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 20; i++) {
                SensitiveDataEvent event = new SensitiveDataEvent();
                event.username = "user" + i;
                event.password = "secret" + i;
                event.email = "user" + i + "@test.com";
                event.ipAddress = "10.0.0." + i;
                event.commit();
            }
        })
        .withDefaultRedaction()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.SensitiveDataEvent");
    }

    @Test
    public void testRoundtripWithStrictRedaction() throws IOException {
        helper.roundtrip(() -> {
            SensitiveDataEvent event = new SensitiveDataEvent();
            event.username = "testuser";
            event.password = "supersecret";
            event.email = "test@example.com";
            event.ipAddress = "192.168.1.1";
            event.commit();

            NetworkEvent network = new NetworkEvent();
            network.sourceAddress = "10.0.0.1";
            network.destinationAddress = "172.16.0.1";
            network.sourcePort = 12345;
            network.destinationPort = 443;
            network.protocol = "HTTPS";
            network.commit();
        })
        .withStrictRedaction()
        .eventCountPreserved();
    }

    @Test
    public void testPasswordPatternRedaction() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            SensitiveDataEvent event = new SensitiveDataEvent();
            event.username = "user";
            event.password = "sensitive_value_123";
            event.email = "test@example.com";
            event.ipAddress = "192.168.1.1";
            event.commit();
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .findEvent("test.SensitiveDataEvent")
                .hasString("password", "***");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "192.168.1.1",
        "10.0.0.1",
        "172.16.0.1",
        "255.255.255.255"
    })
    public void testIPAddressRedaction(String ipAddress) throws IOException {
        Path inputPath = helper.recording()
                .addEvent(() -> {
                    SensitiveDataEvent event = new SensitiveDataEvent();
                    event.username = "user";
                    event.password = "pass";
                    event.email = "test@example.com";
                    event.ipAddress = ipAddress;
                    event.commit();
                })
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .findEvent("test.SensitiveDataEvent")
                .isRedacted("ipAddress");
    }

    @Test
    public void testNestedEventProcessing() throws IOException {
        Path inputPath = helper.createTestRecording(() -> {
            // Interleaved event types
            for (int i = 0; i < 30; i++) {
                if (i % 3 == 0) {
                    SimpleEvent event = new SimpleEvent();
                    event.message = "Simple " + i;
                    event.count = i;
                    event.flag = true;
                    event.commit();
                } else if (i % 3 == 1) {
                    SensitiveDataEvent event = new SensitiveDataEvent();
                    event.username = "user" + i;
                    event.password = "pass" + i;
                    event.email = "user" + i + "@test.com";
                    event.ipAddress = "192.168.1." + i;
                    event.commit();
                } else {
                    NetworkEvent event = new NetworkEvent();
                    event.sourceAddress = "10.0.0." + i;
                    event.destinationAddress = "172.16.0." + i;
                    event.sourcePort = 1000 + i;
                    event.destinationPort = 80;
                    event.protocol = "TCP";
                    event.commit();
                }
            }
        });

        helper.verify(helper.process()
                .from(inputPath)
                .withPseudonymization()
                .process())
                .hasEventOfType("test.SimpleEvent", 10)
                .hasEventOfType("test.SensitiveDataEvent", 10)
                .hasEventOfType("test.NetworkEvent", 10);
    }

    @Test
    public void testEmptyRecordingProcessing() throws IOException {
        Path inputPath = helper.recording().build();

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .fileExists()
                .fileNotEmpty();
    }

    @Test
    public void testSingleEventProcessing() throws IOException {
        Path inputPath = helper.recording()
                .addSimpleEvent("Single event", 1, true)
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withDefaultEngine()
                .process())
                .hasEventOfType("test.SimpleEvent", 1)
                .findEvent("test.SimpleEvent")
                .hasString("message", "Single event")
                .hasInt("count", 1)
                .hasBoolean("flag", true);
    }
}