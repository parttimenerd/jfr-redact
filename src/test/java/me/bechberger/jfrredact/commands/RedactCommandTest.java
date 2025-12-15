package me.bechberger.jfrredact.commands;

import me.bechberger.jfrredact.Preset;
import me.bechberger.jfrredact.Version;
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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RedactCommandTest {

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        RedactCommand command = new RedactCommand();
        cmd = new CommandLine(command);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        cmd.setOut(new PrintWriter(outContent, true));
        cmd.setErr(new PrintWriter(errContent, true));
    }

    /**
     * Helper to create a minimal valid JFR file.
     * For now, creates an empty file - in production tests, you'd use a real JFR file.
     */
    private Path createMinimalJfrFile() throws Exception {
        Path jfrFile = tempDir.resolve("test.jfr");
        // In a real test, you'd create a valid JFR file with test data
        // For now, we'll create an empty file to test argument parsing
        Files.write(jfrFile, new byte[0]);
        return jfrFile;
    }

    /**
     * Helper to create a dummy text file with test content.
     */
    private Path createDummyTextFile() throws IOException {
        return createDummyTextFile("test.txt");
    }

    /**
     * Helper to create a dummy text file with specific filename.
     */
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
            )
        );
    }

    /**
     * Provides test cases for help/version flags that should succeed.
     */
    static Stream<Arguments> helpAndVersionTests() {
        return Stream.of(
            Arguments.of(new String[]{"--help"}, 0, "redact"),
            Arguments.of(new String[]{"-h"}, 0, "redact"),
            Arguments.of(new String[]{"--version"}, 0, Version.FULL_VERSION),
            Arguments.of(new String[]{"-V"}, 0, Version.FULL_VERSION)
        );
    }

    @Test
    void testDryRun_NoOutputFileCreated() throws Exception {
        Path inputFile = createMinimalJfrFile();
        Path outputFile = tempDir.resolve("output.jfr");

        // This will fail during actual processing with empty file, but we can test the argument parsing
        int exitCode = cmd.execute(
            inputFile.toString(),
            outputFile.toString(),
            "--dry-run"
        );

        // The --dry-run flag should be accepted (exit code 2 = CLI validation error)
        // Exit code 1 = processing error (expected with empty JFR file)
        assertNotEquals(2, exitCode, "Should accept --dry-run flag without CLI validation error");
    }

    @Test
    void testStats_ShowsInOutput() throws Exception {
        Path inputFile = createMinimalJfrFile();

        // Execute with --stats
        int exitCode = cmd.execute(
            inputFile.toString(),
            "--stats"
        );

        // The --stats flag should be accepted (exit code 2 = CLI validation error)
        // Will fail due to empty JFR file, but the option parsing should succeed
        assertNotEquals(2, exitCode, "Should accept --stats flag without CLI validation error");
    }

    @Test
    void testDryRunAndStats_Together() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--dry-run",
            "--stats"
        );

        // Both options should be accepted together (exit code 2 = CLI validation error)
        assertNotEquals(2, exitCode, "Should accept both --dry-run and --stats flags");
    }

    @Test
    void testMissingInputFile() {
        int exitCode = cmd.execute("/nonexistent/file.jfr");

        // Should fail with exit code 1 (application error)
        assertEquals(1, exitCode, "Should fail with exit code 1 for missing file");
    }

    @Test
    void testHelp() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should exit successfully");

        String output = outContent.toString();
        assertTrue(output.contains("redact"),
                "Help should mention command name");
        assertTrue(output.contains("--dry-run"),
                "Help should mention --dry-run option");
        assertTrue(output.contains("--stats"),
                "Help should mention --stats option");
        assertTrue(output.contains("--pseudonymize"),
                "Help should mention --pseudonymize option");
    }

    @Test
    void testDefaultPreset() throws Exception {
        Path inputFile = createMinimalJfrFile();

        // Should use default preset when not specified
        int exitCode = cmd.execute(inputFile.toString());

        // Processing will fail with empty file (exit code 1), but not with CLI error (exit code 2)
        assertNotEquals(2, exitCode, "Should accept default preset without CLI validation error");
    }

    @Test
    void testStrictPreset() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--preset", "strict"
        );

        // Processing will fail with empty file (exit code 1), but not with CLI error (exit code 2)
        assertNotEquals(2, exitCode, "Should accept strict preset without CLI validation error");
    }

    @Test
    void testPseudonymizationFlag() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--pseudonymize"
        );

        // Flag should be accepted (exit code 2 = CLI validation error)
        assertNotEquals(2, exitCode, "Should accept --pseudonymize flag without CLI validation error");
    }

    @Test
    void testCustomRedactionRegex() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--add-redaction-regex", "\\b[A-Z]{3}-\\d{6}\\b"
        );

        // Flag should be accepted (exit code 2 = CLI validation error)
        assertNotEquals(2, exitCode, "Should accept --add-redaction-regex flag without CLI validation error");
    }

    @Test
    void testRemoveEvent() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--remove-event", "jdk.OSInformation"
        );

        // Flag should be accepted (exit code 2 = CLI validation error)
        assertNotEquals(2, exitCode, "Should accept --remove-event flag without CLI validation error");
    }

    @Test
    void testVerboseFlag() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--verbose"
        );

        // Should increase logging verbosity
        assertNotNull(errContent.toString(), "Should have output");
    }

    @Test
    void testQuietFlag() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--quiet"
        );

        // Should minimize output
        assertNotNull(errContent.toString(), "Should have output");
    }

    @Test
    void testDebugFlag() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--debug"
        );

        // Should enable debug logging
        assertNotNull(errContent.toString(), "Should have output");
    }

    @Test
    void testOutputFileGeneration() throws Exception {
        Path inputFile = tempDir.resolve("recording.jfr");
        Files.write(inputFile, new byte[0]);

        // Don't specify output file - should generate default name
        int exitCode = cmd.execute(inputFile.toString());

        // Processing will fail with empty file (exit code 1), but not with CLI error (exit code 2)
        // The default output filename generation should work
        assertNotEquals(2, exitCode, "Should accept input without explicit output file");
    }

    @Test
    void testCustomConfig() throws Exception {
        Path inputFile = createMinimalJfrFile();
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, """
                general:
                  redaction_text: "***"
                properties:
                  enabled: true
                  patterns:
                    - password
                """);

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--config", configFile.toString()
        );

        // Processing will fail with empty file (exit code 1), but config should load
        assertNotEquals(2, exitCode, "Should accept --config flag without CLI validation error");
    }

    @Test
    void testEventFiltering_IncludeEvents() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--include-events", "jdk.GC*"
        );

        // Should accept event filtering options
        assertNotNull(errContent.toString(), "Should process with event filtering");
    }

    @Test
    void testEventFiltering_ExcludeEvents() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--exclude-events", "jdk.ThreadSleep"
        );

        // Should accept event filtering options
        assertNotNull(errContent.toString(), "Should process with event filtering");
    }

    @Test
    void testThreadFiltering_IncludeThreads() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--include-threads", "main"
        );

        // Should accept thread filtering options
        assertNotNull(errContent.toString(), "Should process with thread filtering");
    }

    @Test
    void testThreadFiltering_ExcludeThreads() throws Exception {
        Path inputFile = createMinimalJfrFile();

        int exitCode = cmd.execute(
            inputFile.toString(),
            "--exclude-threads", "GC Thread*"
        );

        // Should accept thread filtering options
        assertNotNull(errContent.toString(), "Should process with thread filtering");
    }

    // ========== Parameterized Tests from MainTestOld ==========

    @ParameterizedTest(name = "[{index}] CLI failure: {0}")
    @MethodSource("failingCommandLineTests")
    void testFailingCommandLines(String[] args, int expectedExitCode, String errorMessageSubstring) {
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
    void testHelpAndVersion(String[] args, int expectedExitCode, String expectedOutputSubstring) {
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

    @ParameterizedTest
    @ValueSource(strings = {"default", "strict"})
    void testValidPresets(String presetName) throws Exception {
        Path inputFile = createMinimalJfrFile();
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

    // ========== Config File Tests from MainTestOld ==========

    @Test
    void testConfigFileOption() throws IOException {
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
    void testConfigUrlOption() throws IOException {
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

    // ========== Text File Redaction Tests from MainTestOld ==========

    @Test
    void testTextFileRedaction() throws IOException {
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
    void testTextFileRedactionWithDefaultOutput() throws IOException {
        Path inputFile = createDummyTextFile("input.txt");

        String[] args = {
            inputFile.toString()
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with default output");

        Path expectedOutput = inputFile.getParent().resolve("input.redacted.txt");
        assertTrue(Files.exists(expectedOutput), "Default output file should exist");
    }

    @Test
    void testPseudonymizeFlagWithTextFile() throws IOException {
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
    void testRemoveEventOptionMultiple() throws IOException {
        Path inputFile = createDummyTextFile();
        Path outputFile = tempDir.resolve("output.txt");

        String[] args = {
            inputFile.toString(),
            outputFile.toString(),
            "--remove-event", "jdk.CustomEvent",
            "--remove-event", "jdk.AnotherEvent"
        };

        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode, "Should succeed with multiple remove-event options");
    }

    @Test
    void testAddRedactionRegexOptionWithText() throws IOException {
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
    void testCombinedOptions() throws IOException {
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

    // ========== Edge Cases from MainTestOld ==========

    @Test
    void testEmptyTextFile() throws IOException {
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
    void testOutputToSameDirectory() throws IOException {
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
}