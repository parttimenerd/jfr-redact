package me.bechberger.jfrredact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CLI tests for Main class with parameterized test cases.
 *
 * Tests both successful executions and various failure scenarios.
 */
public class MainTest {

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        Main app = new Main();
        cmd = new CommandLine(app);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
    }

    // ========== Test Data Providers ==========

    /**
     * Provides test cases for failing command-line calls.
     * Format: (args, expectedExitCode, errorMessageSubstring)
     */
    static Stream<Arguments> failingCommandLineTests() {
        return Stream.of(
            // No arguments
            Arguments.of(
                new String[]{},
                2,  // PicoCLI missing required parameter exit code
                "Missing required parameter: '<input.jfr>'"
            ),

            // Non-existent input file
            Arguments.of(
                new String[]{"nonexistent.jfr"},
                1,
                "Input file not found"
            ),

            // Invalid preset
            Arguments.of(
                new String[]{"test.jfr", "--preset", "invalid-preset"},
                2,
                "Invalid value for option '--preset'"
            ),

            // Non-existent config file
            Arguments.of(
                new String[]{"test.jfr", "--config", "nonexistent-config.yaml"},
                1,
                "Configuration file not found"
            ),

            // Invalid URL config
            Arguments.of(
                new String[]{"test.jfr", "--config", "http://invalid.nonexistent.domain.test/config.yaml"},
                1,
                "Failed to load configuration"
            )
        );
    }

    /**
     * Provides test cases for help/version flags that should succeed.
     */
    static Stream<Arguments> helpAndVersionTests() {
        return Stream.of(
            Arguments.of(new String[]{"--help"}, 0, "Usage: jfr-redact"),
            Arguments.of(new String[]{"-h"}, 0, "Usage: jfr-redact"),
            Arguments.of(new String[]{"--version"}, 0, Version.FULL_VERSION),
            Arguments.of(new String[]{"-V"}, 0, Version.FULL_VERSION)
        );
    }

    // ========== Parameterized Failure Tests ==========

    @ParameterizedTest(name = "[{index}] CLI failure: {0}")
    @MethodSource("failingCommandLineTests")
    public void testFailingCommandLines(String[] args, int expectedExitCode, String errorMessageSubstring) {
        int exitCode = cmd.execute(args);

        assertTrue(exitCode != 0,
            "Command should fail with non-zero exit code for args: " + String.join(" ", args));

        // For PicoCLI validation errors, exit code should be 2
        // For application errors, exit code should be 1
        if (expectedExitCode == 2) {
            assertEquals(expectedExitCode, exitCode,
                "Expected PicoCLI validation error (exit code 2) for args: " + String.join(" ", args));
        } else {
            assertEquals(expectedExitCode, exitCode,
                "Expected application error (exit code 1) for args: " + String.join(" ", args));
        }
    }

    @ParameterizedTest(name = "[{index}] Help/Version: {0}")
    @MethodSource("helpAndVersionTests")
    public void testHelpAndVersion(String[] args, int expectedExitCode, String expectedOutputSubstring) {
        // PicoCLI writes help/version to its own PrintWriter
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos, true);

        cmd.setOut(pw);
        cmd.setErr(pw);

        int exitCode = cmd.execute(args);

        pw.flush();

        assertEquals(expectedExitCode, exitCode);

        String output = baos.toString();
        assertTrue(output.contains(expectedOutputSubstring),
            "Output should contain: " + expectedOutputSubstring + "\nActual output: " + output);
    }

    // ========== Preset Tests ==========

    @ParameterizedTest
    @ValueSource(strings = {"default", "strict"})
    public void testValidPresets(String presetName) throws IOException {
        Path inputFile = createDummyJfrFile();
        Path outputFile = tempDir.resolve("output.jfr");

        String[] args = {
            inputFile.toString(),
            outputFile.toString(),
            "--preset", presetName
        };

        int exitCode = cmd.execute(args);

        // May fail due to JFR parsing, but should not fail due to invalid preset
        // Exit code 2 = CLI validation error (bad preset)
        assertNotEquals(2, exitCode, "Preset should be valid: " + presetName);
    }

    // ========== Config File Tests ==========

    @Test
    public void testConfigFileOption() throws IOException {
        // Create a valid config file
        String configContent = """
            parent: default
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("test-config.yaml");
        Files.writeString(configFile, configContent);

        Path inputFile = createDummyTextFile();
        Path outputFile = tempDir.resolve("output.txt");

        String[] args = {
            inputFile.toString(),
            outputFile.toString(),
            "--config", configFile.toString()
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with valid config file");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    public void testConfigUrlOption() throws IOException {
        // Create a valid config file
        String configContent = """
            parent: default
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("url-config.yaml");
        Files.writeString(configFile, configContent);

        Path inputFile = createDummyTextFile();
        Path outputFile = tempDir.resolve("output.txt");

        // Use file:// URL
        String configUrl = configFile.toUri().toString();

        String[] args = {
            inputFile.toString(),
            outputFile.toString(),
            "--config", configUrl
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with config URL");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    // ========== Text File Redaction Tests ==========

    @Test
    public void testTextFileRedaction() throws IOException {
        Path inputFile = createDummyTextFile();
        Path outputFile = tempDir.resolve("output.txt");

        String[] args = {
            inputFile.toString(),
            outputFile.toString()
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Text file redaction should succeed");
        assertTrue(Files.exists(outputFile), "Output file should exist");

        String output = Files.readString(outputFile);
        assertFalse(output.contains("user@example.com"), "Email should be redacted");
    }

    @Test
    public void testTextFileRedactionWithDefaultOutput() throws IOException {
        Path inputFile = createDummyTextFile("input.txt");

        String[] args = {
            inputFile.toString()
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with default output");

        Path expectedOutput = inputFile.getParent().resolve("input.redacted.txt");
        assertTrue(Files.exists(expectedOutput), "Default output file should exist");
    }

    // ========== CLI Option Tests ==========

    @Test
    public void testPseudonymizeFlag() throws IOException {
        Path inputFile = createDummyTextFile();
        Path outputFile = tempDir.resolve("output.txt");

        String[] args = {
            inputFile.toString(),
            outputFile.toString(),
            "--pseudonymize"
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with pseudonymize flag");
        assertTrue(Files.exists(outputFile), "Output file should exist");
    }

    @Test
    public void testRemoveEventOption() throws IOException {
        Path inputFile = createDummyTextFile();
        Path outputFile = tempDir.resolve("output.txt");

        String[] args = {
            inputFile.toString(),
            outputFile.toString(),
            "--remove-event", "jdk.CustomEvent",
            "--remove-event", "jdk.AnotherEvent"
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with remove-event options");
    }

    @Test
    public void testAddRedactionRegexOption() throws IOException {
        Path inputFile = createDummyTextFile();
        Path outputFile = tempDir.resolve("output.txt");

        String[] args = {
            inputFile.toString(),
            outputFile.toString(),
            "--add-redaction-regex", "\\b[A-Z]{3}-\\d{6}\\b"
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with custom regex");
    }

    @Test
    public void testCombinedOptions() throws IOException {
        String configContent = """
            parent: default
            properties:
              enabled: true
            """;

        Path configFile = tempDir.resolve("combined-config.yaml");
        Files.writeString(configFile, configContent);

        Path inputFile = createDummyTextFile();
        Path outputFile = tempDir.resolve("output.txt");

        String[] args = {
            inputFile.toString(),
            outputFile.toString(),
            "--config", configFile.toString(),
            "--preset", "strict",
            "--pseudonymize",
            "--remove-event", "jdk.CustomEvent",
            "--add-redaction-regex", "PATTERN-\\d+"
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with all options combined");
        assertTrue(Files.exists(outputFile), "Output file should exist");
    }

    // ========== Edge Cases ==========

    @Test
    public void testEmptyTextFile() throws IOException {
        Path inputFile = tempDir.resolve("empty.txt");
        Files.writeString(inputFile, "");

        Path outputFile = tempDir.resolve("output.txt");

        String[] args = {
            inputFile.toString(),
            outputFile.toString()
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should handle empty file");
        assertTrue(Files.exists(outputFile), "Output file should exist");
        assertEquals("", Files.readString(outputFile), "Output should be empty");
    }

    @Test
    public void testOutputToSameDirectory() throws IOException {
        Path inputFile = createDummyTextFile("test-input.txt");
        Path outputFile = inputFile.getParent().resolve("test-output.txt");

        String[] args = {
            inputFile.toString(),
            outputFile.toString()
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed writing to same directory");
        assertTrue(Files.exists(outputFile), "Output file should exist");
    }

    // ========== Helper Methods ==========

    private Path createDummyJfrFile() throws IOException {
        // Create a minimal file that looks like JFR (has .jfr extension)
        // Actual JFR parsing may fail, but that's OK for CLI testing
        Path file = tempDir.resolve("test.jfr");
        Files.writeString(file, "dummy jfr content");
        return file;
    }

    private Path createDummyTextFile() throws IOException {
        return createDummyTextFile("test.txt");
    }

    private Path createDummyTextFile(String filename) throws IOException {
        String content = """
            This is a test file.
            Email: user@example.com
            IP: 192.168.1.100
            Normal text here.
            """;

        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }
}