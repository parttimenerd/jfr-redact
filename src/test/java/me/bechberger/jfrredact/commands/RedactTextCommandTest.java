package me.bechberger.jfrredact.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Simple tests for RedactTextCommand.
 */
class RedactTextCommandTest {

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        RedactTextCommand command = new RedactTextCommand();
        cmd = new CommandLine(command);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        cmd.setOut(new PrintWriter(outContent, true));
        cmd.setErr(new PrintWriter(errContent, true));
    }

    @Test
    void testHelp() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should exit successfully");

        String output = outContent.toString();
        assertThat(output).contains("redact-text", "Redact sensitive information from text files", "--config", "--pseudonymize");
    }

    @Test
    void testRedactTextFile_WithDefaultOutput() throws Exception {
        // Create a test input file with sensitive data
        Path inputFile = tempDir.resolve("test.log");
        String content = """
                Application started
                User email: john.doe@example.com
                IP Address: 192.168.1.100
                Normal log line
                Another email: jane@test.org
                """;
        Files.writeString(inputFile, content);

        // Execute command
        int exitCode = cmd.execute(inputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");

        // Check that output file was created with default name
        Path expectedOutput = tempDir.resolve("test.redacted.log");
        assertThat(expectedOutput).exists();

        // Verify redaction occurred
        String redactedContent = Files.readString(expectedOutput);
        assertThat(redactedContent).doesNotContain("john.doe@example.com").doesNotContain("192.168.1.100");
        assertThat(redactedContent).contains("Application started");
    }

    @Test
    void testRedactTextFile_WithCustomOutput() throws Exception {
        // Create a test input file
        Path inputFile = tempDir.resolve("input.txt");
        String content = """
                Secret IP: 192.168.1.100
                Public info
                Email: test@example.com
                """;
        Files.writeString(inputFile, content);

        // Specify custom output file
        Path outputFile = tempDir.resolve("clean.txt");

        // Execute command
        int exitCode = cmd.execute(inputFile.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");

        // Check that custom output file was created
        assertThat(outputFile).exists();

        // Verify redaction
        String redactedContent = Files.readString(outputFile);
        assertThat(redactedContent).doesNotContain("192.168.1.100", "test@example.com");
        assertThat(redactedContent).contains("Public info");
    }

    @Test
    void testRedactTextFile_WithPreset() throws Exception {
        Path inputFile = tempDir.resolve("test.log");
        Files.writeString(inputFile, "Email: user@example.com\nData\n");

        int exitCode = cmd.execute(inputFile.toString(), "--config", "strict");

        assertEquals(0, exitCode, "Should exit successfully with strict preset");

        Path expectedOutput = tempDir.resolve("test.redacted.log");
        assertThat(expectedOutput).exists();
    }

    @Test
    void testRedactTextFile_UuidWithStrictPreset() throws Exception {
        // UUIDs are only redacted in strict preset, not default
        Path inputFile = tempDir.resolve("test.log");
        String content = """
                Secret UUID: 550e8400-e29b-41d4-a716-446655440000
                Normal data
                """;
        Files.writeString(inputFile, content);

        int exitCode = cmd.execute(inputFile.toString(), "--config", "strict");

        assertEquals(0, exitCode, "Should exit successfully");

        Path expectedOutput = tempDir.resolve("test.redacted.log");
        String redactedContent = Files.readString(expectedOutput);

        assertThat(redactedContent).doesNotContain("550e8400-e29b-41d4-a716-446655440000");
        assertThat(redactedContent).contains("Normal data");
    }

    @Test
    void testRedactTextFile_WithPseudonymization() throws Exception {
        Path inputFile = tempDir.resolve("test.log");
        String content = """
                Email: alice@example.com appears twice
                Email: alice@example.com appears here too
                Email: bob@example.com appears once
                """;
        Files.writeString(inputFile, content);

        int exitCode = cmd.execute(inputFile.toString(), "--pseudonymize");

        assertEquals(0, exitCode, "Should exit successfully");

        Path expectedOutput = tempDir.resolve("test.redacted.log");
        String redactedContent = Files.readString(expectedOutput);

        // With pseudonymization, same email should get same pseudonym
        assertThat(redactedContent).doesNotContain("alice@example.com", "bob@example.com");
    }

    @Test
    void testRedactTextFile_WithCustomRegex() throws Exception {
        Path inputFile = tempDir.resolve("test.log");
        String content = """
                Ticket: ABC-123456
                Another ticket: XYZ-789012
                Normal text
                """;
        Files.writeString(inputFile, content);

        // Add custom regex to redact ticket numbers
        int exitCode = cmd.execute(
            inputFile.toString(),
            "--add-redaction-regex", "\\b[A-Z]{3}-\\d{6}\\b"
        );

        assertEquals(0, exitCode, "Should exit successfully");

        Path expectedOutput = tempDir.resolve("test.redacted.log");
        String redactedContent = Files.readString(expectedOutput);

        assertThat(redactedContent).doesNotContain("ABC-123456", "XYZ-789012");
        assertThat(redactedContent).contains("Normal text");
    }

    @Test
    void testRedactTextFile_FileNotFound() {
        Path nonExistentFile = tempDir.resolve("does-not-exist.log");

        int exitCode = cmd.execute(nonExistentFile.toString());

        assertNotEquals(0, exitCode, "Should fail when input file doesn't exist");
    }

    @Test
    void testRedactTextFile_NoExtension() throws Exception {
        Path inputFile = tempDir.resolve("logfile");
        Files.writeString(inputFile, "Email: test@example.com\n");

        int exitCode = cmd.execute(inputFile.toString());

        assertEquals(0, exitCode, "Should handle files without extension");

        Path expectedOutput = tempDir.resolve("logfile.redacted");
        assertThat(expectedOutput).exists();
    }

    @Test
    void testRedactTextFile_WithStats() throws Exception {
        Path inputFile = tempDir.resolve("test.log");
        Files.writeString(inputFile, "Email: user@example.com\n");

        int exitCode = cmd.execute(inputFile.toString(), "--stats");

        assertEquals(0, exitCode, "Should exit successfully with stats flag");

        // Stats should be printed to output (could verify output contains "Statistics" if needed)
    }

    @Test
    void testRedactTextFile_QuietMode() throws Exception {
        Path inputFile = tempDir.resolve("test.log");
        Files.writeString(inputFile, "Email: user@example.com\n");

        int exitCode = cmd.execute(inputFile.toString(), "--quiet");

        assertEquals(0, exitCode, "Should exit successfully in quiet mode");

        Path expectedOutput = tempDir.resolve("test.redacted.log");
        assertThat(expectedOutput).exists();
    }
}