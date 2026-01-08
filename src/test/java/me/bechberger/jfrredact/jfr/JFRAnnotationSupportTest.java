package me.bechberger.jfrredact.jfr;

import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import me.bechberger.jfrredact.jfr.util.RunnableWithException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static me.bechberger.jfrredact.jfr.util.JFRTestEvents.*;

/**
 * Comprehensive tests for all JFR event annotations and RedactionEngine.NONE.
 * Tests verify that all JFR annotations are properly supported during roundtrip processing.
 */
public class JFRAnnotationSupportTest {

    @TempDir
    Path tempDir;

    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }
    
    // ========== Annotation Support Tests ==========

    @Test
    public void testAnnotatedEvent_AllAnnotations() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            AnnotatedEvent event = new AnnotatedEvent();
            event.eventTime = System.currentTimeMillis();
            event.duration2 = 1000000L; // 1ms in nanos
            event.dataSize = 1024 * 1024; // 1MB
            event.frequency = 60; // 60 Hz
            event.memoryAddress = 0x7fff5fbff000L;
            event.percentage = 0.95f; // 95%
            event.unsignedValue = Integer.MAX_VALUE;
            event.thread = Thread.currentThread();
            event.clazz = AnnotatedEvent.class;
            event.experimentalFlag = true;
            event.commit();
        });

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.AnnotatedEvent")
                .fieldNotNull("eventTime")
                .fieldNotNull("duration2")
                .fieldNotNull("dataSize")
                .fieldNotNull("frequency")
                .fieldNotNull("memoryAddress")
                .fieldNotNull("percentage")
                .fieldNotNull("unsignedValue")
                .fieldNotNull("thread")
                .fieldNotNull("clazz")
                .hasBoolean("experimentalFlag", true);
    }

    @Test
    public void testTimestampEvent_AllTimestampAnnotations() throws IOException {
        long now = System.nanoTime();
        Path recording = helper.createTestRecording(() -> {
            TimestampEvent event = new TimestampEvent();
            event.timestampStart = now;
            event.timestampEnd = now + 1000000000L; // +1 second
            event.durationNanos = 1000L;
            event.durationMicros = 500L;
            event.durationMillis = 250L;
            event.durationSeconds = 10L;
            event.commit();
        });

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.TimestampEvent")
                .hasLong("timestampStart", now)
                .hasLong("timestampEnd", now + 1000000000L)
                .hasLong("durationNanos", 1000L)
                .hasLong("durationMicros", 500L)
                .hasLong("durationMillis", 250L)
                .hasLong("durationSeconds", 10L);
    }

    @Test
    public void testDataAmountEvent_AllDataAnnotations() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            DataAmountEvent event = new DataAmountEvent();
            event.bytes = 1024 * 1024; // 1MB
            event.bits = 8 * 1024; // 8Kb
            event.hertz = 2400000000L; // 2.4 GHz
            event.address = 0x00007fff5fbff5a0L;
            event.commit();
        });

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.DataAmountEvent")
                .hasLong("bytes", 1024 * 1024)
                .hasLong("bits", 8 * 1024)
                .hasLong("hertz", 2400000000L)
                .hasLong("address", 0x00007fff5fbff5a0L);
    }

    @Test
    public void testRelationalEvent_RelationalAnnotations() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            RelationalEvent parent = new RelationalEvent();
            parent.eventId = 1L;
            parent.parentId = 0L;
            parent.operation = "parent";
            parent.result = 100;
            parent.commit();

            RelationalEvent child = new RelationalEvent();
            child.eventId = 2L;
            child.parentId = 1L; // Points to parent
            child.operation = "child";
            child.result = 200;
            child.commit();
        });

        var events = helper.verify(recording)
                .hasEventOfType("test.RelationalEvent", 2)
                .findAllEvents("test.RelationalEvent");

        assertEquals(2, events.size());
        events.get(0)
                .hasLong("eventId", 1L)
                .hasLong("parentId", 0L)
                .hasString("operation", "parent")
                .hasInt("result", 100);

        events.get(1)
                .hasLong("eventId", 2L)
                .hasLong("parentId", 1L)
                .hasString("operation", "child")
                .hasInt("result", 200);
    }

    @Test
    public void testThreadEvent_ThreadAndClassAnnotations() throws IOException {
        Thread currentThread = Thread.currentThread();
        Path recording = helper.createTestRecording(() -> {
            ThreadEvent event = new ThreadEvent();
            event.thread = Thread.currentThread();
            event.clazz = ThreadEvent.class;
            event.threadName = Thread.currentThread().getName();
            event.threadPriority = Thread.currentThread().getPriority();
            event.isDaemon = Thread.currentThread().isDaemon();
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ThreadEvent")
                .fieldNotNull("thread")
                .fieldNotNull("clazz")
                .hasString("threadName", currentThread.getName())
                .hasInt("threadPriority", currentThread.getPriority())
                .hasBoolean("isDaemon", currentThread.isDaemon());
    }

    @Test
    public void testPerformanceEvent_PerformanceAnnotations() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            PerformanceEvent event = new PerformanceEvent();
            event.operation = "database_query";
            event.duration2 = 150L; // 150ms
            event.cpuTime = 120000000L; // 120ms in nanos
            event.bytesAllocated = 512 * 1024; // 512KB
            event.successRate = 0.98f; // 98%
            event.opsPerSecond = 1000L;
            Thread.sleep(100);
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.PerformanceEvent")
                .hasString("operation", "database_query")
                .hasLong("duration2", 150L)
                .hasLong("cpuTime", 120000000L)
                .hasLong("bytesAllocated", 512 * 1024)
                .hasFloat("successRate", 0.98f, 0.001f)
                .hasLong("opsPerSecond", 1000L);
    }

    @Test
    public void testEnabledEvent_DefaultEnabled() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            EnabledEvent event = new EnabledEvent();
            event.message = "This should appear";
            event.counter = 42;
            event.commit();
        });

        helper.verify(recording)
                .hasEventOfType("test.EnabledEvent", 1)
                .findEvent("test.EnabledEvent")
                .hasString("message", "This should appear")
                .hasInt("counter", 42);
    }

    @Test
    public void testContentTypeEvent_ContentTypeAnnotations() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ContentTypeEvent event = new ContentTypeEvent();
            event.bytes = 2048;
            event.percentage = 0.75f;
            event.memoryAddress = 0x123456789ABCDEFL;
            event.unsigned = 4294967295L; // Max unsigned 32-bit
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ContentTypeEvent")
                .hasLong("bytes", 2048)
                .hasFloat("percentage", 0.75f, 0.001f)
                .hasLong("memoryAddress", 0x123456789ABCDEFL)
                .hasLong("unsigned", 4294967295L);
    }

    // ========== Roundtrip Tests with Complex Annotations ==========

    @Test
    public void testRoundtrip_CompareWithDefaultEngine() throws IOException {
        // Create same events twice and process with different engines
        RunnableWithException eventGenerator = () -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "test";
            event.count = 100;
            event.flag = true;
            event.commit();
        };

        Path recording1 = helper.createTestRecording(eventGenerator);
        Path recording2 = helper.createTestRecording(eventGenerator);

        // Process with NONE engine
        Path processedNone = helper.process()
                .from(recording1)
                .withNoRedaction()
                .process();

        // Process with default engine
        Path processedDefault = helper.process()
                .from(recording2)
                .withDefaultEngine()
                .process();

        // Both should have same number of events
        assertEquals(
                helper.verify(processedNone).getAllEvents().size(),
                helper.verify(processedDefault).getAllEvents().size(),
                "Event count should be same regardless of engine"
        );

        // NONE should preserve exact values
        helper.verify(processedNone)
                .findEvent("test.SimpleEvent")
                .hasString("message", "test")
                .hasInt("count", 100)
                .hasBoolean("flag", true);
    }

    @Test
    public void testMultipleEventsWithDifferentAnnotations() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            // Create multiple different event types
            for (int i = 0; i < 3; i++) {
                TimestampEvent ts = new TimestampEvent();
                ts.timestampStart = System.nanoTime() + i;
                ts.timestampEnd = System.nanoTime() + i + 1000;
                ts.durationNanos = i * 100L;
                ts.durationMicros = i * 10L;
                ts.durationMillis = i;
                ts.durationSeconds = i;
                ts.commit();

                PerformanceEvent perf = new PerformanceEvent();
                perf.operation = "op_" + i;
                perf.duration2 = i * 50L;
                perf.cpuTime = i * 1000000L;
                perf.bytesAllocated = i * 1024L;
                perf.successRate = 0.9f + (i * 0.01f);
                perf.opsPerSecond = i * 100L;
                perf.commit();
            }
        });

        helper.verify(recording)
                .hasEventOfType("test.TimestampEvent", 3)
                .hasEventOfType("test.PerformanceEvent", 3);

        // Verify first of each type
        helper.verify(recording)
                .findAllEvents("test.TimestampEvent").getFirst()
                .hasLong("durationNanos", 0L)
                .hasLong("durationMicros", 0L);

        helper.verify(recording)
                .findAllEvents("test.PerformanceEvent").get(1)
                .hasString("operation", "op_1")
                .hasLong("duration2", 50L);
    }


    // ========== Comprehensive DataAmount Tests ==========

    @Test
    public void testComprehensiveDataAmountEvent_AllSizes() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ComprehensiveDataAmountEvent event = new ComprehensiveDataAmountEvent();
            event.bytesValue = 1024L; // 1KB
            event.bitsValue = 8192L; // 1KB in bits
            event.kilobytes = 1024L * 10; // 10KB
            event.megabytes = 1024L * 1024 * 5; // 5MB
            event.gigabytes = 1024L * 1024 * 1024 * 2; // 2GB
            event.transferRate = 1000000L; // 1MB/s
            event.bandwidthUsage = 0.75f; // 75%
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ComprehensiveDataAmountEvent")
                .hasLong("bytesValue", 1024L)
                .hasLong("bitsValue", 8192L)
                .hasLong("kilobytes", 10 * 1024L)
                .hasLong("megabytes", 5L * 1024 * 1024)
                .hasLong("gigabytes", 2L * 1024 * 1024 * 1024)
                .hasLong("transferRate", 1000000L)
                .hasFloat("bandwidthUsage", 0.75f, 0.001f);
    }

    @Test
    public void testDataAmountEvent_BytesAndBits() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            DataAmountEvent event = new DataAmountEvent();
            event.bytes = 2048; // 2KB
            event.bits = 16384; // 2KB in bits
            event.hertz = 3000000000L; // 3 GHz
            event.address = 0x7FFF5FBFF000L;
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.DataAmountEvent")
                .hasLong("bytes", 2048)
                .hasLong("bits", 16384)
                .hasLong("hertz", 3000000000L)
                .hasLong("address", 0x7FFF5FBFF000L);
    }

    @Test
    public void testAllContentTypesEvent_Comprehensive() throws IOException {
        long now = System.currentTimeMillis();
        Thread currentThread = Thread.currentThread();

        Path recording = helper.createTestRecording(() -> {
            AllContentTypesEvent event = new AllContentTypesEvent();
            // Data amounts
            event.bytes = 8192L;
            event.bits = 65536L;
            // Time-related
            event.timestamp = now;
            event.timespanNanos = 1000L;
            event.timespanMicros = 500L;
            event.timespanMillis = 250L;
            event.timespanSeconds = 10L;
            // Numeric content types
            event.frequency = 2400000000L;
            event.memoryAddress = 0xDEADBEEFCAFEBABEL;
            event.percentage = 0.85f;
            event.unsigned = 4294967295L;
            // Reference types
            event.thread = currentThread;
            event.clazz = AllContentTypesEvent.class;
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.AllContentTypesEvent")
                .hasLong("bytes", 8192L)
                .hasLong("bits", 65536L)
                .hasLong("timestamp", now)
                .hasLong("timespanNanos", 1000L)
                .hasLong("timespanMicros", 500L)
                .hasLong("timespanMillis", 250L)
                .hasLong("timespanSeconds", 10L)
                .hasLong("frequency", 2400000000L)
                .hasLong("memoryAddress", 0xDEADBEEFCAFEBABEL)
                .hasFloat("percentage", 0.85f, 0.001f)
                .hasLong("unsigned", 4294967295L)
                .fieldNotNull("thread")
                .fieldNotNull("clazz");
    }

    @Test
    public void testDataAmount_ZeroValues() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ComprehensiveDataAmountEvent event = new ComprehensiveDataAmountEvent();
            event.bytesValue = 0L;
            event.bitsValue = 0L;
            event.kilobytes = 0L;
            event.megabytes = 0L;
            event.gigabytes = 0L;
            event.transferRate = 0L;
            event.bandwidthUsage = 0.0f;
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ComprehensiveDataAmountEvent")
                .hasLong("bytesValue", 0L)
                .hasLong("bitsValue", 0L)
                .hasFloat("bandwidthUsage", 0.0f, 0.001f);
    }

    @Test
    public void testDataAmount_MaxValues() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ComprehensiveDataAmountEvent event = new ComprehensiveDataAmountEvent();
            event.bytesValue = Long.MAX_VALUE;
            event.bitsValue = Long.MAX_VALUE;
            event.kilobytes = Long.MAX_VALUE;
            event.megabytes = Long.MAX_VALUE / 1024; // Avoid overflow
            event.gigabytes = Long.MAX_VALUE / (1024 * 1024); // Avoid overflow
            event.transferRate = Long.MAX_VALUE;
            event.bandwidthUsage = 1.0f;
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ComprehensiveDataAmountEvent")
                .hasLong("bytesValue", Long.MAX_VALUE)
                .hasLong("bitsValue", Long.MAX_VALUE)
                .hasFloat("bandwidthUsage", 1.0f, 0.001f);
    }
}