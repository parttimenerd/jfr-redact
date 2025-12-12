package me.bechberger.jfrredact.jfr.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static me.bechberger.jfrredact.jfr.util.JFRTestEvents.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the test framework utilities work correctly.
 */
public class JFRTestFrameworkTest {

    @TempDir
    Path tempDir;

    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    @Test
    public void testRecordingBuilder_SimpleEvent() throws IOException {
        Path recording = helper.recording()
                .addSimpleEvent("Test Message", 123, true)
                .build();

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.SimpleEvent")
                .hasString("message", "Test Message")
                .hasInt("count", 123)
                .hasBoolean("flag", true);
    }

    @Test
    public void testRecordingBuilder_MultipleEvents() throws IOException {
        Path recording = helper.recording()
                .addSimpleEvent("First", 1, true)
                .addSimpleEvent("Second", 2, false)
                .addSimpleEvent("Third", 3, true)
                .build();

        JFREventVerifier verifier = helper.verify(recording);
        verifier.fileExists()
                .hasEvents()
                .hasEventOfType("test.SimpleEvent", 3);

        var events = verifier.findAllEvents("test.SimpleEvent");
        assertEquals(3, events.size());
        events.get(0).hasString("message", "First").hasInt("count", 1).hasBoolean("flag", true);
        events.get(1).hasString("message", "Second").hasInt("count", 2).hasBoolean("flag", false);
        events.get(2).hasString("message", "Third").hasInt("count", 3).hasBoolean("flag", true);
    }

    @Test
    public void testRecordingBuilder_SensitiveEvent() throws IOException {
        Path recording = helper.recording()
                .addSensitiveEvent("admin", "secret123", "admin@test.com", "192.168.1.1")
                .build();

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.SensitiveDataEvent")
                .hasString("username", "admin")
                .hasString("password", "secret123")
                .hasString("email", "admin@test.com")
                .hasString("ipAddress", "192.168.1.1");
    }

    @Test
    public void testRecordingBuilder_ComplexEvent() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "complex";
            event.intField = 42;
            event.longField = 1000L;
            event.floatField = 3.14f;
            event.doubleField = 2.71828;
            event.booleanField = true;
            event.byteField = (byte) 127;
            event.shortField = (short) 500;
            event.charField = 'X';
            event.commit();
        });

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.ComplexEvent")
                .hasString("stringField", "complex")
                .hasInt("intField", 42)
                .hasLong("longField", 1000L)
                .hasFloat("floatField", 3.14f, 0.001f)
                .hasDouble("doubleField", 2.71828, 0.00001)
                .hasBoolean("booleanField", true)
                .hasByte("byteField", (byte) 127)
                .hasShort("shortField", (short) 500)
                .hasChar("charField", 'X');
    }

    @Test
    public void testRecordingBuilder_NetworkEvent() throws IOException {
        Path recording = helper.recording()
                .addNetworkEvent("10.0.0.1", "192.168.1.100", 8080, 443, "HTTPS")
                .build();

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.NetworkEvent")
                .hasString("sourceAddress", "10.0.0.1")
                .hasString("destinationAddress", "192.168.1.100")
                .hasInt("sourcePort", 8080)
                .hasInt("destinationPort", 443)
                .hasString("protocol", "HTTPS");
    }

    @Test
    public void testRecordingBuilder_MixedEvents() throws IOException {
        Path recording = helper.recording()
                .addSimpleEvent("Simple", 1, true)
                .addNetworkEvent("10.0.0.1", "10.0.0.2", 1234, 5678, "TCP")
                .addSensitiveEvent("user", "pass", "email@test.com", "127.0.0.1")
                .build();

        JFREventVerifier verifier = helper.verify(recording);
        verifier.fileExists()
                .hasEvents()
                .hasEventOfType("test.SimpleEvent", 1)
                .hasEventOfType("test.NetworkEvent", 1)
                .hasEventOfType("test.SensitiveDataEvent", 1);
    }

    @Test
    public void testVerifier_StringContains() throws IOException {
        Path recording = helper.recording()
                .addSimpleEvent("Hello World Test", 42, true)
                .build();

        helper.verify(recording)
                .findEvent("test.SimpleEvent")
                .stringContains("message", "World")
                .stringContains("message", "Hello")
                .stringDoesNotContain("message", "Goodbye");
    }

    @Test
    public void testVerifier_NullFields() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = null;
            event.count = 0;
            event.flag = false;
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.SimpleEvent")
                .fieldIsNull("message")
                .hasInt("count", 0)
                .hasBoolean("flag", false);
    }
}