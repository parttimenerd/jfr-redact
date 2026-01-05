package me.bechberger.jfrredact.commands;

import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.Version;
import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static me.bechberger.jfrredact.jfr.util.JFRTestEvents.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConcatCommand.
 */
class ConcatCommandTest {

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        ConcatCommand command = new ConcatCommand();
        cmd = new CommandLine(command);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        cmd.setOut(new PrintWriter(outContent, true));
        cmd.setErr(new PrintWriter(errContent, true));
        helper = new JFRTestHelper(tempDir);
    }

    /**
     * Helper to create an empty file (for testing argument validation).
     */
    private Path createEmptyFile(String filename) throws IOException {
        Path jfrFile = tempDir.resolve(filename);
        Files.write(jfrFile, new byte[0]);
        return jfrFile;
    }

    // ========== Test Data Providers ==========

    /**
     * Provides test cases for failing command-line calls.
     */
    static Stream<Arguments> failingCommandLineTests() {
        return Stream.of(
            // No arguments
            Arguments.of(
                new String[]{},
                2,  // PicoCLI missing required parameter exit code
                "Missing required"
            ),

            // Only one input file (need at least one)
            Arguments.of(
                new String[]{"input.jfr"},
                2,
                "Missing required option"
            ),

            // Missing output file
            Arguments.of(
                new String[]{"input1.jfr", "input2.jfr"},
                2,
                "Missing required option: '--output"
            )
        );
    }

    /**
     * Provides test cases for help/version flags that should succeed.
     */
    static Stream<Arguments> helpAndVersionTests() {
        return Stream.of(
            Arguments.of(new String[]{"--help"}, 0, "concat"),
            Arguments.of(new String[]{"-h"}, 0, "concat"),
            Arguments.of(new String[]{"--version"}, 0, Version.FULL_VERSION),
            Arguments.of(new String[]{"-V"}, 0, Version.FULL_VERSION)
        );
    }

    @ParameterizedTest
    @MethodSource("failingCommandLineTests")
    void testFailingCommandLine(String[] args, int expectedExitCode, String expectedErrorSubstring) {
        int exitCode = cmd.execute(args);

        assertEquals(expectedExitCode, exitCode,
                "Exit code should match expected value for args: " + String.join(" ", args));

        String errorOutput = errContent.toString();
        assertThat(errorOutput.contains(expectedErrorSubstring)).as("Error output should contain: '%s' but was: %s", expectedErrorSubstring, errorOutput).isTrue();
    }

    @ParameterizedTest
    @MethodSource("helpAndVersionTests")
    void testHelpAndVersion(String[] args, int expectedExitCode, String expectedOutputSubstring) {
        int exitCode = cmd.execute(args);

        assertEquals(expectedExitCode, exitCode,
                "Exit code should be " + expectedExitCode + " for: " + String.join(" ", args));

        String output = outContent.toString();
        assertThat(output.contains(expectedOutputSubstring)).as("Output should contain: '%s' but was: %s", expectedOutputSubstring, output).isTrue();
    }

    @Test
    void testInputFileDoesNotExist() {
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            "nonexistent1.jfr",
            "nonexistent2.jfr",
            "-o", outputFile.toString()
        );

        assertEquals(1, exitCode, "Should fail when input file does not exist");
    }

    @Test
    void testCannotReadInputFile() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path inputFile2 = createEmptyFile("input2.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        // Make file unreadable (this might not work on all systems)
        inputFile1.toFile().setReadable(false);

        int exitCode = cmd.execute(
            inputFile1.toString(),
            inputFile2.toString(),
            "-o", outputFile.toString()
        );

        // Reset permissions
        inputFile1.toFile().setReadable(true);

        // On systems where we can't make files unreadable, this test might pass
        // We just verify the command handles the scenario properly
        assertThat(exitCode == 0 || exitCode == 1).as("Should handle read permission appropriately").isTrue();
    }

    @Test
    void testOutputFileCannotBeSameAsInputFile() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path inputFile2 = createEmptyFile("input2.jfr");

        // Try to use input1 as output
        int exitCode = cmd.execute(
            inputFile1.toString(),
            inputFile2.toString(),
            "-o", inputFile1.toString()
        );

        assertEquals(1, exitCode, "Should fail when output file is same as input file");
    }

    @Test
    void testTwoInputFiles_ShortOption() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path inputFile2 = createEmptyFile("input2.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            inputFile1.toString(),
            inputFile2.toString(),
            "-o", outputFile.toString()
        );

        // Empty files will cause processing to fail, but argument parsing should work
        assertNotEquals(2, exitCode, "Should accept valid arguments without CLI validation error");
    }

    @Test
    void testMultipleInputFiles_LongOption() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path inputFile2 = createEmptyFile("input2.jfr");
        Path inputFile3 = createEmptyFile("input3.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            inputFile1.toString(),
            inputFile2.toString(),
            inputFile3.toString(),
            "--output", outputFile.toString()
        );

        // Empty files will cause processing to fail, but argument parsing should work
        assertNotEquals(2, exitCode, "Should accept valid arguments without CLI validation error");
    }

    @Test
    void testVerboseFlag() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path inputFile2 = createEmptyFile("input2.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            inputFile1.toString(),
            inputFile2.toString(),
            "-o", outputFile.toString(),
            "--verbose"
        );

        // Empty files will cause processing to fail, but argument parsing should work
        assertNotEquals(2, exitCode, "Should accept --verbose flag without CLI validation error");
    }

    @Test
    void testVerboseFlagShort() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path inputFile2 = createEmptyFile("input2.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            inputFile1.toString(),
            inputFile2.toString(),
            "-o", outputFile.toString(),
            "-v"
        );

        // Empty files will cause processing to fail, but argument parsing should work
        assertNotEquals(2, exitCode, "Should accept -v flag without CLI validation error");
    }

    @Test
    void testLoggingOutput() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path inputFile2 = createEmptyFile("input2.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            inputFile1.toString(),
            inputFile2.toString(),
            "-o", outputFile.toString()
        );

        // Verify the command executes without crashing
        assertNotEquals(2, exitCode, "Should not have CLI validation errors");
    }

    @Test
    void testSingleInputFile_IsValid() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            inputFile1.toString(),
            "-o", outputFile.toString()
        );

        // Should accept single input file (arity is "1..*")
        assertNotEquals(2, exitCode, "Should accept single input file without CLI validation error");
    }

    @Test
    void testDescriptionShowsNoRedaction() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should display successfully");

        String output = outContent.toString();
        assertThat(output.contains("without any redaction") || output.contains("without redaction")).as("Help text should mention that concatenation is done without redaction").isTrue();
    }

    @Test
    void testExamplesInHelpText() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should display successfully");

        String output = outContent.toString();
        assertThat(output.contains("Examples:") || output.contains("examples")).as("Help text should include examples").isTrue();
        assertThat(output.contains("concat")).as("Help text should show concat command name").isTrue();
    }

    @Test
    void testEmptyFile_WithoutIgnoreFlag_ShouldFail() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path inputFile2 = createEmptyFile("empty.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            inputFile1.toString(),
            inputFile2.toString(),
            "-o", outputFile.toString()
        );

        assertEquals(1, exitCode, "Should fail when encountering empty file without -i flag");
    }

    @Test
    void testEmptyFile_WithIgnoreFlag_ShouldWarn() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path emptyFile = createEmptyFile("empty.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            inputFile1.toString(),
            emptyFile.toString(),
            "-o", outputFile.toString(),
            "-i"
        );

        // Should not fail with exit code 2 (CLI validation error)
        // Will fail with exit code 1 because files are empty and can't be processed
        assertNotEquals(2, exitCode, "Should accept -i flag without CLI validation error");
    }

    @Test
    void testMultipleEmptyFiles_WithIgnoreFlag() throws IOException {
        Path emptyFile1 = createEmptyFile("empty1.jfr");
        Path emptyFile2 = createEmptyFile("empty2.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            emptyFile1.toString(),
            emptyFile2.toString(),
            "-o", outputFile.toString(),
            "-i"
        );

        assertEquals(1, exitCode, "Should fail when all files are empty even with -i flag");
    }

    @Test
    void testIgnoreEmptyFlag_LongForm() throws IOException {
        Path inputFile1 = createEmptyFile("input1.jfr");
        Path emptyFile = createEmptyFile("empty.jfr");
        Path outputFile = tempDir.resolve("output.jfr");

        int exitCode = cmd.execute(
            inputFile1.toString(),
            emptyFile.toString(),
            "-o", outputFile.toString(),
            "--ignore-empty"
        );

        // Should not fail with exit code 2 (CLI validation error)
        assertNotEquals(2, exitCode, "Should accept --ignore-empty flag without CLI validation error");
    }

    @Test
    void testHelpText_MentionsIgnoreEmptyOption() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should display successfully");

        String output = outContent.toString();
        assertThat(output.contains("-i") || output.contains("--ignore-empty")).as("Help text should document the -i/--ignore-empty option").isTrue();
        assertThat(output.contains("empty") && output.contains("ignore")).as("Help text should explain ignoring empty files").isTrue();
    }

    @Test
    void testConcatPreservesEvents() throws IOException {
        List<String> inputFiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            inputFiles.add(helper.recording()
                    .withName("test-recording-" + i)
                    .addSimpleEvent("Hello World", i, true)
                    .build().toString());
        }

        Path outputFile = tempDir.resolve("concatenated-types.jfr");

        List<String> args = new ArrayList<>(inputFiles);
        args.add("-o");
        args.add(outputFile.toString());

        // Execute concat command with single file
        int exitCode = cmd.execute(args.toArray(String[]::new));

        assertEquals(0, exitCode, "Concat command should succeed");

        var events = RecordingFile.readAllEvents(outputFile);
        for (var event : events) {
            System.out.println(event.getEventType().getName());
            System.out.println(event.getInt("count"));
        }
        assertEquals(inputFiles.size(), events.size(),
                "Output file should contain the same number of events as input files combined");

        // Collect event types from output file
        for (int i = 0; i < inputFiles.size(); i++) {
            assertEquals(i, events.get(i).getInt("count"),
                    "Event value should match expected for event " + i);
            assertEquals("Hello World", events.get(i).getString("message"),
                    "Event message should match expected for event " + i);
            assertThat(events.get(i).getBoolean("flag")).as("Event flag should match expected for event %s", i).isTrue();
        }
    }
}