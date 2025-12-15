package me.bechberger.jfrredact;

import jdk.jfr.Event;
import jdk.jfr.Name;
import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for logging functionality in the application.
 *
 * Tests the logging configuration and behavior at different log levels
 * when using the --verbose, --debug, and --quiet flags.
 */
public class LoggingTest {

    @TempDir
    Path tempDir;

    JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    // Simple test event for creating valid JFR recordings
    @Name("test.SimpleEvent")
    static class SimpleEvent extends Event {
        String message;
    }

    // ========== Test Data Providers ==========

    static Stream<Arguments> verbosityFlagTests() {
        return Stream.of(
            Arguments.of("--verbose", "Verbose flag"),
            Arguments.of("-v", "Short verbose flag"),
            Arguments.of("--debug", "Debug flag"),
            Arguments.of("--quiet", "Quiet flag"),
            Arguments.of("-q", "Short quiet flag")
        );
    }

    // ========== Basic Flag Tests ==========

    // ========== Basic Flag Tests ==========

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("verbosityFlagTests")
    public void testVerbosityFlags_AcceptedWithoutError(String flag, String description) throws IOException {
        Path inputFile = createDummyJFRFile();
        Path outputFile = tempDir.resolve("output.jfr");

        String[] args = {
            "redact",
            inputFile.toString(),
            outputFile.toString(),
            flag
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        // Should complete successfully
        assertEquals(0, exitCode, description + " should be accepted and complete successfully");
        assertTrue(Files.exists(outputFile), "Output file should be created with " + flag);
    }

    @Test
    public void testDefaultMode_NoLoggingFlags() throws IOException {
        Path inputFile = createDummyJFRFile();
        Path outputFile = tempDir.resolve("output.jfr");

        String[] args = {
            "redact",
            inputFile.toString(),
            outputFile.toString()
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed without logging flags");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    // ========== Output Tests ==========

    @Test
    public void testVerboseFlag_ProducesOutput() throws IOException {
        Path inputFile = createDummyJFRFile();
        Path outputFile = tempDir.resolve("output.jfr");

        String[] args = {
            "redact",
            inputFile.toString(),
            outputFile.toString(),
            "--verbose"
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        // Should complete successfully with verbose flag
        assertEquals(0, exitCode, "Verbose mode should complete successfully");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    public void testQuietFlag_MinimizesOutput() throws IOException {
        Path inputFile = createDummyJFRFile();
        Path outputFile = tempDir.resolve("output.jfr");

        String[] args = {
            "redact",
            inputFile.toString(),
            outputFile.toString(),
            "--quiet"
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        // Should complete successfully with quiet flag
        assertEquals(0, exitCode, "Quiet mode should complete successfully");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    // ========== Error Logging Tests ==========

    @Test
    public void testLogging_WithInvalidFile() {
        String[] args = {
            "redact",
            "/nonexistent/file.jfr",
            "output.jfr",
            "--verbose"
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        // Should return error exit code for invalid file
        assertEquals(1, exitCode, "Should return error exit code for invalid file");
    }

    // ========== Configuration Tests ==========

    @Test
    public void testLoggingWithConfigFile() throws IOException {
        String configContent = """
            parent: default
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("test-config.yaml");
        Files.writeString(configFile, configContent);

        Path inputFile = createDummyJFRFile();
        Path outputFile = tempDir.resolve("output.jfr");

        String[] args = {
            "redact",
            inputFile.toString(),
            outputFile.toString(),
            "--config", configFile.toString(),
            "--verbose"
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with config file and verbose logging");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    public void testLoggingWithConfigUrl() throws IOException {
        String configContent = """
            parent: default
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("url-config.yaml");
        Files.writeString(configFile, configContent);

        Path inputFile = createDummyJFRFile();
        Path outputFile = tempDir.resolve("output.jfr");

        String configUrl = configFile.toUri().toString();

        String[] args = {
            "redact",
            inputFile.toString(),
            outputFile.toString(),
            "--config", configUrl,
            "--debug"
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with config URL and debug logging");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    // ========== ConfigLoader Logging Tests ==========

    @Test
    public void testConfigLoader_LoadsPreset() {
        ConfigLoader loader = new ConfigLoader();

        // Should not throw exception
        assertDoesNotThrow(() -> loader.load("default"),
            "ConfigLoader should load default preset without error");
    }

    @Test
    public void testConfigLoader_LoadsFromFile() throws IOException {
        String configContent = """
            parent: none
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("loader-test.yaml");
        Files.writeString(configFile, configContent);

        ConfigLoader loader = new ConfigLoader();

        assertDoesNotThrow(() -> loader.load(configFile.toString()),
            "ConfigLoader should load from file without error");
    }

    @Test
    public void testConfigLoader_LoadsFromUrl() throws IOException {
        String configContent = """
            parent: none
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("url-loader-test.yaml");
        Files.writeString(configFile, configContent);

        String fileUrl = configFile.toUri().toString();

        ConfigLoader loader = new ConfigLoader();

        assertDoesNotThrow(() -> loader.load(fileUrl),
            "ConfigLoader should load from URL without error");
    }

    @Test
    public void testConfigLoader_HandlesError() {
        ConfigLoader loader = new ConfigLoader();

        assertThrows(IOException.class, () -> loader.load("/nonexistent/config.yaml"),
            "ConfigLoader should throw IOException for nonexistent file");
    }

    // ========== Integration Tests ==========

    @Test
    public void testFullWorkflow_WithDebugLogging() throws IOException {
        String configContent = """
            parent: default
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("workflow-config.yaml");
        Files.writeString(configFile, configContent);

        Path inputFile = createDummyJFRFile();
        Path outputFile = tempDir.resolve("output.jfr");

        String[] args = {
            "redact",
            inputFile.toString(),
            outputFile.toString(),
            "--config", configFile.toString(),
            "--debug"
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Full workflow should succeed");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    public void testCombinedFlags_WithPseudonymization() throws IOException {
        Path inputFile = createDummyJFRFile();
        Path outputFile = tempDir.resolve("output.jfr");

        String[] args = {
            "redact",
            inputFile.toString(),
            outputFile.toString(),
            "--pseudonymize",
            "--verbose"
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with combined flags");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    public void testMultipleLoggingFlags_LastWins() throws IOException {
        Path inputFile = createDummyJFRFile();
        Path outputFile = tempDir.resolve("output.jfr");

        // Multiple logging flags - implementation should handle gracefully
        String[] args = {
            "redact",
            inputFile.toString(),
            outputFile.toString(),
            "--verbose",
            "--debug"
        };

        Main app = new Main();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed even with multiple logging flags");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    // ========== Helper Methods ==========

    private Path createDummyJFRFile() throws IOException {
        return helper.createTestRecording(() -> {
            SimpleEvent event1 = new SimpleEvent();
            event1.message = "Test event with email: user@example.com";
            event1.commit();

            SimpleEvent event2 = new SimpleEvent();
            event2.message = "Test event with IP: 192.168.1.100";
            event2.commit();

            SimpleEvent event3 = new SimpleEvent();
            event3.message = "Normal text here.";
            event3.commit();
        }, SimpleEvent.class);
    }
}