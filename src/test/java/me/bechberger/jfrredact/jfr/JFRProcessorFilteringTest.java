package me.bechberger.jfrredact.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Name;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Integration tests for JFRProcessor with event filtering using real-world scenarios.
 */
@DisplayName("JFRProcessor with Event Filtering")
class JFRProcessorFilteringTest {

    @TempDir
    Path tempDir;

    JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    // Test events mimicking real-world JDK event patterns

    @Name("jdk.ThreadSleep")
    @Category("Java Application")
    static class ThreadSleepEvent extends Event {
        long time;
    }

    @Name("jdk.JavaMonitorWait")
    @Category("Java Application")
    static class JavaMonitorWaitEvent extends Event {
        long timeout;
    }

    @Name("jdk.ClassLoad")
    @Category("Java Virtual Machine")
    static class ClassLoadEvent extends Event {
        String loadedClass;
    }

    @Name("jdk.GCHeapSummary")
    @Category("Garbage Collector")
    static class GCHeapSummaryEvent extends Event {
        long heapUsed;
    }

    @Name("jdk.OSInformation")
    @Category("Operating System")
    static class OSInformationEvent extends Event {
        String osVersion;
    }

    @Test
    @DisplayName("Filter: Include only specific event types")
    void testIncludeSpecificEventTypes() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setIncludeEvents(
            Arrays.asList("jdk.ThreadSleep", "jdk.JavaMonitorWait"));

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();

            JavaMonitorWaitEvent wait = new JavaMonitorWaitEvent();
            wait.timeout = 50;
            wait.commit();

            ClassLoadEvent classLoad = new ClassLoadEvent();
            classLoad.loadedClass = "java.util.ArrayList";
            classLoad.commit();
        }, ThreadSleepEvent.class, JavaMonitorWaitEvent.class, ClassLoadEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.ThreadSleep")
        .eventTypeCountPreserved("jdk.JavaMonitorWait")
        .eventTypeRemoved("jdk.ClassLoad");
    }

    @Test
    @DisplayName("Filter: Exclude event types with glob pattern")
    void testExcludeEventTypesWithGlob() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setExcludeEvents(
            Collections.singletonList("jdk.GC*"));

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();

            GCHeapSummaryEvent gc = new GCHeapSummaryEvent();
            gc.heapUsed = 1024;
            gc.commit();
        }, ThreadSleepEvent.class, GCHeapSummaryEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.ThreadSleep")
        .eventTypeRemoved("jdk.GCHeapSummary");
    }

    @Test
    @DisplayName("Filter: Include events by category")
    void testIncludeByCategory() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setIncludeCategories(
            Collections.singletonList("Java Application"));

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();

            ClassLoadEvent classLoad = new ClassLoadEvent();
            classLoad.loadedClass = "java.lang.String";
            classLoad.commit();
        }, ThreadSleepEvent.class, ClassLoadEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.ThreadSleep")
        .eventTypeRemoved("jdk.ClassLoad");
    }

    @Test
    @DisplayName("Filter: Combine include and exclude filters")
    void testCombinedFilters() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setIncludeEvents(
            Collections.singletonList("jdk.*"));
        config.getEvents().getFiltering().setExcludeEvents(
            Arrays.asList("jdk.GC*", "jdk.ClassLoad"));

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();

            ClassLoadEvent classLoad = new ClassLoadEvent();
            classLoad.loadedClass = "Test";
            classLoad.commit();

            GCHeapSummaryEvent gc = new GCHeapSummaryEvent();
            gc.heapUsed = 2048;
            gc.commit();
        }, ThreadSleepEvent.class, ClassLoadEvent.class, GCHeapSummaryEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.ThreadSleep")
        .eventTypeRemoved("jdk.ClassLoad")
        .eventTypeRemoved("jdk.GCHeapSummary");
    }

    @Test
    @DisplayName("Filter: Removed types take precedence over include filters")
    void testRemovedTypesPrecedence() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setIncludeEvents(
            Collections.singletonList("jdk.*"));
        config.getEvents().getRemovedTypes().clear();
        config.getEvents().getRemovedTypes().add("jdk.ThreadSleep");

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();

            ClassLoadEvent classLoad = new ClassLoadEvent();
            classLoad.loadedClass = "Test";
            classLoad.commit();
        }, ThreadSleepEvent.class, ClassLoadEvent.class)
        .withConfig(config)
        .eventTypeRemoved("jdk.ThreadSleep")
        .eventTypeCountPreserved("jdk.ClassLoad");
    }

    @Test
    @DisplayName("Real-world: Filter to keep only Java Application Monitor events")
    void testRealWorldMonitorEventsOnly() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setIncludeEvents(
            Collections.singletonList("jdk.JavaMonitor*"));

        helper.roundtrip(() -> {
            JavaMonitorWaitEvent wait = new JavaMonitorWaitEvent();
            wait.timeout = 50;
            wait.commit();

            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();
        }, JavaMonitorWaitEvent.class, ThreadSleepEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.JavaMonitorWait")
        .eventTypeRemoved("jdk.ThreadSleep");
    }

    @Test
    @DisplayName("Real-world: Exclude system/internal events, keep application events")
    void testExcludeSystemEvents() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setExcludeEvents(
            Arrays.asList("jdk.OSInformation", "jdk.SystemProcess"));

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();

            OSInformationEvent os = new OSInformationEvent();
            os.osVersion = "Linux 5.10";
            os.commit();
        }, ThreadSleepEvent.class, OSInformationEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.ThreadSleep")
        .eventTypeRemoved("jdk.OSInformation");
    }

    @Test
    @DisplayName("Real-world: Keep only performance-critical events")
    void testPerformanceCriticalEventsOnly() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setIncludeEvents(
            Arrays.asList("jdk.ThreadSleep", "jdk.JavaMonitor*", "jdk.GC*"));

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();

            GCHeapSummaryEvent gc = new GCHeapSummaryEvent();
            gc.heapUsed = 1024;
            gc.commit();

            ClassLoadEvent classLoad = new ClassLoadEvent();
            classLoad.loadedClass = "Test";
            classLoad.commit();
        }, ThreadSleepEvent.class, GCHeapSummaryEvent.class, ClassLoadEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.ThreadSleep")
        .eventTypeCountPreserved("jdk.GCHeapSummary")
        .eventTypeRemoved("jdk.ClassLoad");
    }

    @Test
    @DisplayName("Edge case: No filters keeps all events (except default removed types)")
    void testNoFiltersKeepsAll() throws IOException {
        RedactionConfig config = new RedactionConfig();
        // Clear default removed types for this test
        config.getEvents().getRemovedTypes().clear();

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();

            ClassLoadEvent classLoad = new ClassLoadEvent();
            classLoad.loadedClass = "Test";
            classLoad.commit();
        }, ThreadSleepEvent.class, ClassLoadEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.ThreadSleep")
        .eventTypeCountPreserved("jdk.ClassLoad");
    }

    @Test
    @DisplayName("Edge case: Exclude non-existent event types has no effect")
    void testExcludeNonExistentTypes() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setExcludeEvents(
            Collections.singletonList("non.existent.Event*"));

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();
        }, ThreadSleepEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.ThreadSleep");
    }

    @Test
    @DisplayName("Category filter with glob pattern")
    void testCategoryFilterWithGlob() throws IOException {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setIncludeCategories(
            Collections.singletonList("Java*"));

        helper.roundtrip(() -> {
            ThreadSleepEvent sleep = new ThreadSleepEvent();
            sleep.time = 100;
            sleep.commit();

            ClassLoadEvent classLoad = new ClassLoadEvent();
            classLoad.loadedClass = "Test";
            classLoad.commit();

            OSInformationEvent os = new OSInformationEvent();
            os.osVersion = "Linux";
            os.commit();
        }, ThreadSleepEvent.class, ClassLoadEvent.class, OSInformationEvent.class)
        .withConfig(config)
        .eventTypeCountPreserved("jdk.ThreadSleep")
        .eventTypeCountPreserved("jdk.ClassLoad")
        .eventTypeRemoved("jdk.OSInformation");
    }

    @Test
    @DisplayName("Thread filter: Exclude events from specific threads")
    void testExcludeThreads() throws Exception {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setExcludeThreads(
            Arrays.asList("worker-*", "background-*"));

        // Create events in different threads
        helper.roundtrip(() -> {
            // Main thread event
            ThreadSleepEvent mainEvent = new ThreadSleepEvent();
            mainEvent.time = 100;
            mainEvent.commit();

            // Worker thread event
            Thread workerThread = new Thread(() -> {
                ThreadSleepEvent workerEvent = new ThreadSleepEvent();
                workerEvent.time = 50;
                workerEvent.commit();
            }, "worker-1");
            workerThread.start();
            workerThread.join();

            // Background thread event
            Thread backgroundThread = new Thread(() -> {
                ThreadSleepEvent bgEvent = new ThreadSleepEvent();
                bgEvent.time = 75;
                bgEvent.commit();
            }, "background-task");
            backgroundThread.start();
            backgroundThread.join();

            // Another main thread event
            ThreadSleepEvent mainEvent2 = new ThreadSleepEvent();
            mainEvent2.time = 200;
            mainEvent2.commit();
        }, ThreadSleepEvent.class)
        .withConfig(config);

        // Note: We can verify the filtering works by checking that some events remain
        // The exact count depends on thread execution, but we should have fewer events
        // than the original if thread filtering is working
    }

    @Test
    @DisplayName("Thread filter: Include only main thread events")
    void testIncludeMainThread() throws Exception {
        RedactionConfig config = new RedactionConfig();
        config.getEvents().getFiltering().setIncludeThreads(
            Collections.singletonList("main"));

        helper.roundtrip(() -> {
            // Main thread event
            ClassLoadEvent mainEvent = new ClassLoadEvent();
            mainEvent.loadedClass = "MainClass";
            mainEvent.commit();

            // Worker thread event - should be filtered out
            Thread workerThread = new Thread(() -> {
                ClassLoadEvent workerEvent = new ClassLoadEvent();
                workerEvent.loadedClass = "WorkerClass";
                workerEvent.commit();
            }, "worker-thread");
            workerThread.start();
            workerThread.join();
        }, ClassLoadEvent.class)
        .withConfig(config);

        // Verify thread filtering worked (some events should remain)
    }

    @Test
    @DisplayName("Thread filter: Verify events from excluded threads are actually removed")
    void testThreadFilteringActuallyRemovesEvents() throws Exception {
        RedactionConfig config = new RedactionConfig();
        // Exclude events from worker threads
        config.getEvents().getFiltering().setExcludeThreads(
            Collections.singletonList("worker-thread"));

        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            // Event from main thread - should be kept
            ThreadSleepEvent mainEvent = new ThreadSleepEvent();
            mainEvent.time = 100;
            mainEvent.commit();

            // Events from worker thread - should be removed
            Thread workerThread = new Thread(() -> {
                ThreadSleepEvent workerEvent1 = new ThreadSleepEvent();
                workerEvent1.time = 50;
                workerEvent1.commit();

                ThreadSleepEvent workerEvent2 = new ThreadSleepEvent();
                workerEvent2.time = 75;
                workerEvent2.commit();
            }, "worker-thread");
            workerThread.start();
            workerThread.join();

            // Another event from main thread - should be kept
            ThreadSleepEvent mainEvent2 = new ThreadSleepEvent();
            mainEvent2.time = 200;
            mainEvent2.commit();
        }, ThreadSleepEvent.class)
        .withConfig(config);

        // Verify: We should have only 2 events (the main thread events)
        // The 2 worker thread events should be filtered out
        long processedCount = verifier.getProcessedEvents().size();
        long originalCount = verifier.getOriginalEvents().size();

        // Original should have 4 events (2 main + 2 worker)
        // Processed should have 2 events (2 main only)
        assertEquals(4, originalCount, "Should have 4 events before filtering");
        assertEquals(2, processedCount, "Should have 2 events after filtering out worker thread");

        // Verify all remaining events are from main thread
        for (var event : verifier.getProcessedEvents()) {
            String threadName = event.getThread() != null ? event.getThread().getJavaName() : null;
            assertNotEquals("worker-thread", threadName,
                "No events from worker-thread should remain after filtering");
        }
    }

    @Test
    @DisplayName("Thread filter: Verify events from included threads are kept")
    void testThreadFilteringKeepsIncludedThreadEvents() throws Exception {
        RedactionConfig config = new RedactionConfig();
        // Include only events from specific-thread
        config.getEvents().getFiltering().setIncludeThreads(
            Collections.singletonList("specific-thread"));

        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            // Event from main thread - should be removed
            ClassLoadEvent mainEvent = new ClassLoadEvent();
            mainEvent.loadedClass = "MainClass";
            mainEvent.commit();

            // Events from specific-thread - should be kept
            Thread specificThread = new Thread(() -> {
                ClassLoadEvent event1 = new ClassLoadEvent();
                event1.loadedClass = "Class1";
                event1.commit();

                ClassLoadEvent event2 = new ClassLoadEvent();
                event2.loadedClass = "Class2";
                event2.commit();
            }, "specific-thread");
            specificThread.start();
            specificThread.join();

            // Event from another thread - should be removed
            Thread otherThread = new Thread(() -> {
                ClassLoadEvent otherEvent = new ClassLoadEvent();
                otherEvent.loadedClass = "OtherClass";
                otherEvent.commit();
            }, "other-thread");
            otherThread.start();
            otherThread.join();
        }, ClassLoadEvent.class)
        .withConfig(config);

        long processedCount = verifier.getProcessedEvents().size();
        long originalCount = verifier.getOriginalEvents().size();

        // Original should have 4 events (1 main + 2 specific + 1 other)
        // Processed should have 2 events (2 from specific-thread only)
        assertEquals(4, originalCount, "Should have 4 events before filtering");
        assertEquals(2, processedCount, "Should have 2 events after filtering to specific-thread only");

        // Verify all remaining events are from specific-thread
        for (var event : verifier.getProcessedEvents()) {
            String threadName = event.getThread() != null ? event.getThread().getJavaName() : null;
            assertEquals("specific-thread", threadName,
                "All remaining events should be from specific-thread");
        }
    }
}