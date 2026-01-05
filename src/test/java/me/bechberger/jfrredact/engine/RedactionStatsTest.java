package me.bechberger.jfrredact.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RedactionStats.
 */
class RedactionStatsTest {

    private RedactionStats stats;

    @BeforeEach
    void setUp() {
        stats = new RedactionStats();
    }

    @Test
    void testInitialState() {
        assertEquals(0, stats.getTotalEvents(), "Initial total events should be 0");
        assertEquals(0, stats.getRemovedEvents(), "Initial removed events should be 0");
        assertEquals(0, stats.getFilteredThreadEvents(), "Initial filtered thread events should be 0");
        assertEquals(0, stats.getRedactedFields(), "Initial redacted fields should be 0");
        assertEquals(0, stats.getProcessedEvents(), "Initial processed events should be 0");
    }

    @Test
    void testRecordEvent() {
        stats.recordEvent("jdk.ThreadSleep");

        assertEquals(1, stats.getTotalEvents(), "Should record total event");
        assertEquals(1, stats.getProcessedEvents(), "Should count as processed");
        assertThat(stats.getEventTypeStats())
                .as("Should track event type")
                .containsKey("jdk.ThreadSleep");
        assertEquals(1, stats.getEventTypeStats().get("jdk.ThreadSleep").get(),
                "Should count event type occurrence");
    }

    @Test
    void testRecordMultipleEvents() {
        stats.recordEvent("jdk.ThreadSleep");
        stats.recordEvent("jdk.JavaMonitorEnter");
        stats.recordEvent("jdk.ThreadSleep");

        assertEquals(3, stats.getTotalEvents(), "Should record 3 total events");
        assertEquals(2, stats.getEventTypeStats().get("jdk.ThreadSleep").get(),
                "Should count ThreadSleep twice");
        assertEquals(1, stats.getEventTypeStats().get("jdk.JavaMonitorEnter").get(),
                "Should count JavaMonitorEnter once");
    }

    @Test
    void testRecordRemovedEvent() {
        stats.recordEvent("jdk.OSInformation");
        stats.recordRemovedEvent("jdk.OSInformation");

        assertEquals(1, stats.getTotalEvents(), "Should record total event");
        assertEquals(1, stats.getRemovedEvents(), "Should record removed event");
        assertEquals(0, stats.getProcessedEvents(), "Removed events not in processed count");
    }

    @Test
    void testRecordFilteredThreadEvent() {
        stats.recordEvent("jdk.ThreadSleep");
        stats.recordFilteredThreadEvent();

        assertEquals(1, stats.getTotalEvents(), "Should record total event");
        assertEquals(1, stats.getFilteredThreadEvents(), "Should record filtered thread event");
        assertEquals(0, stats.getProcessedEvents(), "Filtered events not in processed count");
    }

    @Test
    void testRecordRedactedField() {
        stats.recordRedactedField("password");
        stats.recordRedactedField("secret");
        stats.recordRedactedField("password");

        assertEquals(3, stats.getRedactedFields(), "Should record 3 redacted fields");
        assertThat(stats.getRedactedFieldNames()).containsKeys("password", "secret");
        assertEquals(2, stats.getRedactedFieldNames().get("password").get(),
                "Should count password field twice");
        assertEquals(1, stats.getRedactedFieldNames().get("secret").get(),
                "Should count secret field once");
    }

    @Test
    void testProcessedEventsCalculation() {
        stats.recordEvent("event1");
        stats.recordEvent("event2");
        stats.recordEvent("event3");
        stats.recordEvent("event4");
        stats.recordRemovedEvent("event1");
        stats.recordFilteredThreadEvent();

        assertEquals(4, stats.getTotalEvents(), "Should have 4 total events");
        assertEquals(1, stats.getRemovedEvents(), "Should have 1 removed event");
        assertEquals(1, stats.getFilteredThreadEvents(), "Should have 1 filtered thread event");
        assertEquals(2, stats.getProcessedEvents(),
                "Processed should be total - removed - filtered = 4 - 1 - 1 = 2");
    }

    @Test
    void testPrint() {
        // Capture stdout
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            // Add some test data
            stats.recordEvent("jdk.ThreadSleep");
            stats.recordEvent("jdk.JavaMonitorEnter");
            stats.recordEvent("jdk.GCPhasePause");
            stats.recordRemovedEvent("jdk.OSInformation");
            stats.recordFilteredThreadEvent();
            stats.recordRedactedField("password");
            stats.recordRedactedField("secret");
            stats.recordRedactedField("password");

            // Print statistics
            stats.print();

            String output = outContent.toString();

            // Verify output contains expected sections
            assertThat(output)
                    .as("Should contain title")
                    .contains("Redaction Statistics")
                    .as("Should contain Events section")
                    .contains("Events:")
                    .as("Should contain Redactions section")
                    .contains("Redactions:")
                    .as("Should show total events")
                    .contains("Total events read:")
                    .as("Should show processed events")
                    .contains("Events processed:")
                    .as("Should show removed events")
                    .contains("Events removed:")
                    .as("Should show redacted fields")
                    .contains("Total fields redacted:");

        } finally {
            // Restore stdout
            System.setOut(originalOut);
        }
    }

    @Test
    void testPrintWithEventTypeStats() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            // Add event type data
            for (int i = 0; i < 100; i++) {
                stats.recordEvent("jdk.ThreadSleep");
            }
            for (int i = 0; i < 50; i++) {
                stats.recordEvent("jdk.JavaMonitorEnter");
            }

            stats.print();

            String output = outContent.toString();
            assertThat(output)
                    .as("Should show top event types")
                    .contains("Top event types processed:")
                    .as("Should list ThreadSleep")
                    .contains("jdk.ThreadSleep")
                    .as("Should list JavaMonitorEnter")
                    .contains("jdk.JavaMonitorEnter");

        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testPrintWithRedactedFieldStats() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            // Add redacted field data
            for (int i = 0; i < 100; i++) {
                stats.recordRedactedField("password");
            }
            for (int i = 0; i < 50; i++) {
                stats.recordRedactedField("apiKey");
            }

            stats.print();

            String output = outContent.toString();
            assertThat(output)
                    .as("Should show top redacted fields")
                    .contains("Top redacted fields:")
                    .as("Should list password field")
                    .contains("password")
                    .as("Should list apiKey field")
                    .contains("apiKey");

        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        // Test concurrent access
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                stats.recordEvent("event1");
                stats.recordRedactedField("field1");
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                stats.recordEvent("event2");
                stats.recordRedactedField("field2");
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(2000, stats.getTotalEvents(),
                "Should correctly count events from multiple threads");
        assertEquals(2000, stats.getRedactedFields(),
                "Should correctly count redacted fields from multiple threads");
    }

    @Test
    void testEmptyStatsPrint() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            stats.print();

            String output = outContent.toString();
            assertThat(output)
                    .as("Should print even with no data")
                    .contains("Redaction Statistics")
                    .as("Should show zero counts")
                    .contains("0");

        } finally {
            System.setOut(originalOut);
        }
    }
}