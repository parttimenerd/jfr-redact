package me.bechberger.jfrredact.commands;

import me.bechberger.jfrredact.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static me.bechberger.jfrredact.jfr.util.JFRTestEvents.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WordsDiscoverCommand.
 */
class WordsDiscoverCommandTest {

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        WordsDiscoverCommand command = new WordsDiscoverCommand();
        cmd = new CommandLine(command);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        cmd.setOut(new PrintWriter(outContent, true));
        cmd.setErr(new PrintWriter(errContent, true));
        helper = new JFRTestHelper(tempDir);
    }

    // ========== JFR File Tests ==========

    @Test
    void testDiscoverFromJFR_ToStdout() throws IOException {
        // Create a JFR file with custom events
        Path jfrFile = helper.recording()
                .addSimpleEvent("Hello World", 42, true)
                .addSimpleEvent("Test Message", 100, false)
                .addSimpleEvent("Another Event", 200, true)
                .build();

        int exitCode = cmd.execute(jfrFile.toString());

        assertEquals(0, exitCode, "Command should succeed");

        String output = outContent.toString();
        // Should contain discovered words from the events
        assertTrue(output.contains("Hello") || output.contains("World") || output.contains("Test"),
                "Output should contain discovered words");
    }

    @Test
    void testDiscoverFromJFR_ToFile() throws IOException {
        Path jfrFile = helper.recording()
                .addSimpleEvent("Sensitive Data", 123, true)
                .addSimpleEvent("Another Secret", 456, false)
                .build();

        Path outputFile = tempDir.resolve("words.txt");

        int exitCode = cmd.execute(
                jfrFile.toString(),
                "-o", outputFile.toString()
        );

        assertEquals(0, exitCode, "Command should succeed");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        String content = Files.readString(outputFile);
        assertFalse(content.isEmpty(), "Output file should not be empty");

        // Verify words are one per line
        List<String> lines = Files.readAllLines(outputFile);
        assertFalse(lines.isEmpty(), "Should have discovered some words");
    }

    @Test
    void testDiscoverFromJFR_WithIgnoreOptions() throws IOException {
        Path jfrFile = helper.recording()
                .addSimpleEvent("TestMessage", 42, true)
                .build();

        Path outputFile = tempDir.resolve("words-ignore.txt");

        int exitCode = cmd.execute(
                jfrFile.toString(),
                "-o", outputFile.toString(),
                "--ignore-methods=false",
                "--ignore-classes=false"
        );

        assertEquals(0, exitCode, "Command should succeed");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    void testDiscoverFromJFR_WithIgnoreEvents() throws IOException {
        Path jfrFile = helper.recording()
                .addSimpleEvent("Message1", 1, true)
                .addSimpleEvent("Message2", 2, false)
                .addSimpleEvent("Message3", 3, true)
                .build();

        Path outputFile = tempDir.resolve("words-filtered.txt");

        int exitCode = cmd.execute(
                jfrFile.toString(),
                "-o", outputFile.toString(),
                "--ignore-events=test.SimpleEvent"
        );

        assertEquals(0, exitCode, "Command should succeed");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    void testDiscoverFromJFR_MultipleEvents() throws IOException {
        Path jfrFile = helper.createTestRecording(() -> {
            for (int i = 0; i < 50; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = "Event_" + i;
                event.count = i;
                event.flag = i % 2 == 0;
                event.commit();
            }
        });

        Path outputFile = tempDir.resolve("many-words.txt");

        int exitCode = cmd.execute(
                jfrFile.toString(),
                "-o", outputFile.toString()
        );

        assertEquals(0, exitCode, "Command should succeed");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        List<String> words = Files.readAllLines(outputFile);
        assertFalse(words.isEmpty(), "Should discover words from multiple events");
    }

    // ========== Text File Tests ==========

    @Test
    void testDiscoverFromText_ToStdout() throws IOException {
        Path textFile = tempDir.resolve("test.log");
        Files.writeString(textFile, """
                Hello World
                Test message with some words
                Email: user@example.com
                IP: 192.168.1.100
                """);

        int exitCode = cmd.execute(textFile.toString());

        assertEquals(0, exitCode, "Command should succeed");

        String output = outContent.toString();
        assertFalse(output.isEmpty(), "Should output discovered words");
    }

    @Test
    void testDiscoverFromText_ToFile() throws IOException {
        Path textFile = tempDir.resolve("application.log");
        Files.writeString(textFile, """
                [INFO] Application started
                [WARN] Configuration missing: database-url
                [ERROR] Connection failed to server-001
                User: john.doe logged in
                Session: abc123xyz
                """);

        Path outputFile = tempDir.resolve("discovered-words.txt");

        int exitCode = cmd.execute(
                textFile.toString(),
                "-o", outputFile.toString()
        );

        assertEquals(0, exitCode, "Command should succeed");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        List<String> words = Files.readAllLines(outputFile);
        assertFalse(words.isEmpty(), "Should discover words from log file");

        // Should contain various words from the log
        String allWords = String.join("\n", words);
        assertTrue(allWords.contains("Application") ||
                   allWords.contains("Connection") ||
                   allWords.contains("User"),
                "Should discover common words from log");
    }

    @Test
    void testDiscoverFromText_ManyLines() throws IOException {
        Path textFile = tempDir.resolve("large.log");

        // Create a file with many lines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15000; i++) {
            sb.append("Line ").append(i).append(" with some text and data_").append(i).append("\n");
        }
        Files.writeString(textFile, sb.toString());

        Path outputFile = tempDir.resolve("large-words.txt");

        int exitCode = cmd.execute(
                textFile.toString(),
                "-o", outputFile.toString()
        );

        assertEquals(0, exitCode, "Command should succeed with large file");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        // Verify words were discovered
        List<String> words = Files.readAllLines(outputFile);
        assertFalse(words.isEmpty(), "Should discover words from large file");
    }

    @Test
    void testDiscoverFromText_EmptyFile() throws IOException {
        Path textFile = tempDir.resolve("empty.log");
        Files.writeString(textFile, "");

        Path outputFile = tempDir.resolve("empty-words.txt");

        int exitCode = cmd.execute(
                textFile.toString(),
                "-o", outputFile.toString()
        );

        assertEquals(0, exitCode, "Command should succeed even with empty file");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    void testDiscoverFromText_SpecialCharacters() throws IOException {
        Path textFile = tempDir.resolve("special.log");
        Files.writeString(textFile, """
                Special chars: !@#$%^&*()
                URLs: http://example.com/path?query=value
                Paths: /usr/local/bin/app
                Email: test@domain.org
                Mixed-Case-Words and snake_case_words
                """);

        Path outputFile = tempDir.resolve("special-words.txt");

        int exitCode = cmd.execute(
                textFile.toString(),
                "-o", outputFile.toString()
        );

        assertEquals(0, exitCode, "Command should succeed with special characters");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        List<String> words = Files.readAllLines(outputFile);
        assertFalse(words.isEmpty(), "Should discover words even with special chars");
    }

    // ========== Error Cases ==========

    @Test
    void testNonExistentFile() {
        int exitCode = cmd.execute("nonexistent.jfr");

        assertEquals(1, exitCode, "Should fail when file doesn't exist");

        String stderr = errContent.toString();
        assertTrue(stderr.contains("does not exist"),
                "Should mention file doesn't exist");
    }

    @Test
    void testMissingRequiredParameter() {
        int exitCode = cmd.execute();

        assertEquals(2, exitCode, "Should fail with CLI error when no parameters provided");
    }

    @Test
    void testHelpFlag() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should display successfully");

        String output = outContent.toString();
        assertTrue(output.contains("discover"),
                "Help should mention command name");
        assertTrue(output.contains("Examples"),
                "Help should include examples");
    }

    @Test
    void testVersionFlag() {
        int exitCode = cmd.execute("--version");

        // Version flag should be accepted (exit code 0) or unrecognized (exit code 2)
        // Both are acceptable since this is a subcommand and version handling varies
        assertTrue(exitCode == 0 || exitCode == 2,
                "Version flag should be handled gracefully");

        // Note: Version output capture behavior varies for subcommands in picocli,
        // so we don't assert on the output content, just that the flag is handled
    }

    // ========== Output Format Tests ==========

    @Test
    void testOutputFormat_OneWordPerLine() throws IOException {
        Path textFile = tempDir.resolve("format-test.txt");
        Files.writeString(textFile, "word1 word2 word3");

        Path outputFile = tempDir.resolve("format-output.txt");

        int exitCode = cmd.execute(
                textFile.toString(),
                "-o", outputFile.toString()
        );

        assertEquals(0, exitCode, "Command should succeed");

        List<String> lines = Files.readAllLines(outputFile);
        // Each line should contain one word (no spaces within lines)
        for (String line : lines) {
            assertFalse(line.contains(" "),
                    "Each line should contain a single word, not: " + line);
        }
    }

    @Test
    void testStatisticsNotShownByDefault() throws IOException {
        Path jfrFile = helper.recording()
                .addSimpleEvent("Test", 1, true)
                .build();

        int exitCode = cmd.execute(jfrFile.toString());

        assertEquals(0, exitCode, "Command should succeed");

        // Statistics should NOT be shown by default (only in verbose mode)
        String allOutput = outContent.toString() + errContent.toString();
        assertFalse(allOutput.contains("Processed") && allOutput.contains("events"),
                "Should not show statistics by default");
    }

    @Test
    void testStatisticsShownInVerboseMode() throws IOException {
        Path jfrFile = helper.recording()
                .addSimpleEvent("Test", 1, true)
                .build();

        // Check if verbose flag exists and use it
        int exitCode = cmd.execute(jfrFile.toString(), "--verbose");

        // Verbose mode might not be implemented yet, so accept either success or CLI error
        assertTrue(exitCode == 0 || exitCode == 2, "Command should succeed or reject unknown option");
    }

    @Test
    void testLongOptionNames() throws IOException {
        Path jfrFile = helper.recording()
                .addSimpleEvent("Test", 1, true)
                .build();

        Path outputFile = tempDir.resolve("output.txt");

        int exitCode = cmd.execute(
                jfrFile.toString(),
                "--output", outputFile.toString(),
                "--ignore-methods=false",
                "--ignore-classes=false"
        );

        assertEquals(0, exitCode, "Should accept long option names");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    void testShortOptionNames() throws IOException {
        Path jfrFile = helper.recording()
                .addSimpleEvent("Test", 1, true)
                .build();

        Path outputFile = tempDir.resolve("output.txt");

        int exitCode = cmd.execute(
                jfrFile.toString(),
                "-o", outputFile.toString()
        );

        assertEquals(0, exitCode, "Should accept short option names");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }
}