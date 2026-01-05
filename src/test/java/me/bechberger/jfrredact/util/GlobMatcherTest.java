package me.bechberger.jfrredact.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

class GlobMatcherTest {

    @ParameterizedTest(name = "[{index}] pattern={0}, value={1}, expected={2}")
    @MethodSource("provideMatchTestCases")
    void testMatches(List<String> patterns, String value, boolean expectedMatch) {
        assertEquals(expectedMatch, GlobMatcher.matches(value, patterns),
            () -> String.format("Pattern '%s' matching '%s' should be %s",
                patterns, value, expectedMatch));
    }

    static Stream<Arguments> provideMatchTestCases() {
        return Stream.of(
            // Exact matches
            Arguments.of(List.of("jdk.ThreadSleep"), "jdk.ThreadSleep", true),
            Arguments.of(List.of("jdk.ThreadSleep"), "jdk.ThreadPark", false),
            Arguments.of(List.of("jdk.ThreadSleep"), "jdk.ThreadSleepEvent", false),

            // Wildcard matches (*)
            Arguments.of(List.of("jdk.*"), "jdk.ThreadSleep", true),
            Arguments.of(List.of("jdk.*"), "jdk.JavaMonitorWait", true),
            Arguments.of(List.of("jdk.*"), "jdk.", true),
            Arguments.of(List.of("jdk.*"), "my.app.Event", false),

            // Prefix wildcard
            Arguments.of(List.of("jdk.GC*"), "jdk.GCPhasePause", true),
            Arguments.of(List.of("jdk.GC*"), "jdk.GCHeapSummary", true),
            Arguments.of(List.of("jdk.GC*"), "jdk.GC", true),
            Arguments.of(List.of("jdk.GC*"), "jdk.ThreadSleep", false),

            // Question mark wildcard (?)
            Arguments.of(List.of("jdk.Thread?leep"), "jdk.ThreadSleep", true),
            Arguments.of(List.of("jdk.Thread?leep"), "jdk.ThreadXXleep", false),
            Arguments.of(List.of("jdk.Thread?leep"), "jdk.Threadleep", false),

            // Comma-separated patterns
            Arguments.of(List.of("jdk.ThreadSleep,jdk.JavaMonitorWait,my.app.*"), "jdk.ThreadSleep", true),
            Arguments.of(List.of("jdk.ThreadSleep,jdk.JavaMonitorWait,my.app.*"), "jdk.JavaMonitorWait", true),
            Arguments.of(List.of("jdk.ThreadSleep,jdk.JavaMonitorWait,my.app.*"), "my.app.CustomEvent", true),
            Arguments.of(List.of("jdk.ThreadSleep,jdk.JavaMonitorWait,my.app.*"), "jdk.GCPhasePause", false),

            // Multiple patterns
            Arguments.of(List.of("jdk.ThreadSleep", "jdk.GC*", "my.app.*"), "jdk.ThreadSleep", true),
            Arguments.of(List.of("jdk.ThreadSleep", "jdk.GC*", "my.app.*"), "jdk.GCPhasePause", true),
            Arguments.of(List.of("jdk.ThreadSleep", "jdk.GC*", "my.app.*"), "my.app.Event", true),
            Arguments.of(List.of("jdk.ThreadSleep", "jdk.GC*", "my.app.*"), "jdk.JavaMonitorWait", false),

            // Quoted patterns
            Arguments.of(List.of("\"jdk.ThreadSleep\""), "jdk.ThreadSleep", true),
            Arguments.of(List.of("\"jdk.ThreadSleep\""), "jdk.ThreadPark", false),
            Arguments.of(List.of("'jdk.ThreadSleep'"), "jdk.ThreadSleep", true),

            // Whitespace handling
            Arguments.of(List.of(" jdk.ThreadSleep , jdk.GC* "), "jdk.ThreadSleep", true),
            Arguments.of(List.of(" jdk.ThreadSleep , jdk.GC* "), "jdk.GCPhasePause", true),

            // Complex glob patterns
            Arguments.of(List.of("*.Application"), "my.app.Application", true),
            Arguments.of(List.of("*.Application"), "Java.Application", true),
            Arguments.of(List.of("*.Application"), "Application", false),

            // Special characters (dots should be literal)
            Arguments.of(List.of("my.app.Event"), "my.app.Event", true),
            Arguments.of(List.of("my.app.Event"), "myXappXEvent", false),

            // Thread name patterns
            Arguments.of(List.of("GC Thread*", "Service Thread"), "GC Thread #1", true),
            Arguments.of(List.of("GC Thread*", "Service Thread"), "GC Thread", true),
            Arguments.of(List.of("GC Thread*", "Service Thread"), "Service Thread", true),
            Arguments.of(List.of("GC Thread*", "Service Thread"), "main", false)
        );
    }

    @Test
    void testEmptyPatterns() {
        assertThat(GlobMatcher.matches("jdk.ThreadSleep", Collections.emptyList())).isFalse();
        assertThat(GlobMatcher.matches("jdk.ThreadSleep", null)).isFalse();
    }

    @Test
    void testNullValue() {
        List<String> patterns = List.of("jdk.*");
        assertThat(GlobMatcher.matches(null, patterns)).isFalse();
    }
}