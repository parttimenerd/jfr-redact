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

/**
 * Tests for GenerateSchemaCommand.
 */
class GenerateSchemaCommandTest {

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        GenerateSchemaCommand command = new GenerateSchemaCommand();
        cmd = new CommandLine(command);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        cmd.setOut(new PrintWriter(outContent, true));
        cmd.setErr(new PrintWriter(errContent, true));
    }

    @Test
    void testGenerateSchema_ToStdout() {
        int exitCode = cmd.execute();

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        assertTrue(output.contains("\"$schema\""), "Should contain schema version");
        assertTrue(output.contains("\"type\""), "Should contain type definitions");
        assertTrue(output.contains("\"properties\""), "Should contain properties");
        assertTrue(output.length() > 100, "Schema output should be substantial");
    }

    @Test
    void testGenerateSchema_ToFile() throws Exception {
        Path outputFile = tempDir.resolve("config-schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        String content = Files.readString(outputFile);
        assertTrue(content.contains("\"$schema\""), "File should contain schema version");
        assertTrue(content.contains("\"type\""), "File should contain type definitions");
        assertTrue(content.contains("\"properties\""), "File should contain properties");

        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Schema written to:"), "Should show success message");
        assertTrue(errOutput.contains(outputFile.getFileName().toString()),
                "Should mention output file");
    }

    @Test
    void testGenerateSchema_ToFileInSubdirectory() throws Exception {
        Path subDir = tempDir.resolve("schemas");
        Path outputFile = subDir.resolve("config-schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertTrue(Files.exists(subDir), "Subdirectory should be created");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        String content = Files.readString(outputFile);
        assertTrue(content.contains("\"$schema\""), "File should contain valid schema");
    }

    @Test
    void testGenerateSchema_OverwriteExistingFile() throws Exception {
        Path outputFile = tempDir.resolve("config-schema.json");
        Files.writeString(outputFile, "old content");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");

        String content = Files.readString(outputFile);
        assertFalse(content.contains("old content"), "Should overwrite existing file");
        assertTrue(content.contains("\"$schema\""), "Should contain new schema");
    }

    @Test
    void testGenerateSchema_ValidJson() throws Exception {
        Path outputFile = tempDir.resolve("schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");

        String content = Files.readString(outputFile);

        // Basic JSON validation - should start with { and end with }
        String trimmed = content.trim();
        assertTrue(trimmed.startsWith("{"), "Schema should be valid JSON object");
        assertTrue(trimmed.endsWith("}"), "Schema should be valid JSON object");

        // Should not have common JSON errors
        assertFalse(content.contains(",}"), "Should not have trailing commas");
        assertFalse(content.contains(",]"), "Should not have trailing commas in arrays");
    }

    @Test
    void testGenerateSchema_ContainsExpectedFields() throws Exception {
        Path outputFile = tempDir.resolve("schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");

        String content = Files.readString(outputFile);

        // Check for expected RedactionConfig fields
        assertTrue(content.contains("properties") || content.contains("\"properties\""),
                "Should contain properties configuration");
        assertTrue(content.contains("strings") || content.contains("\"strings\""),
                "Should contain strings configuration");
        assertTrue(content.contains("events") || content.contains("\"events\""),
                "Should contain events configuration");
        assertTrue(content.contains("threads") || content.contains("\"threads\""),
                "Should contain threads configuration");
    }

    @Test
    void testGenerateSchema_StdoutDoesNotShowSuccessMessage() {
        int exitCode = cmd.execute();

        assertEquals(0, exitCode, "Should exit successfully");

        String errOutput = errContent.toString();
        assertFalse(errOutput.contains("Schema written to:"),
                "Should not show file message when outputting to stdout");
    }

    @Test
    void testGenerateSchema_Help() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Should show help successfully");

        String output = outContent.toString();
        assertTrue(output.contains("generate-schema"), "Help should mention command name");
        assertTrue(output.contains("Generate JSON Schema"), "Help should describe command");
        assertTrue(output.contains("Examples"), "Help should show examples");
    }

    @Test
    void testGenerateSchema_Version() {
        int exitCode = cmd.execute("--version");

        assertEquals(0, exitCode, "Should show version successfully");

        String output = outContent.toString();
        assertFalse(output.isEmpty(), "Version output should not be empty");
    }

    @Test
    void testGenerateSchema_ConsistentOutput() {
        // Generate schema twice and verify they're identical
        int exitCode1 = cmd.execute();
        assertEquals(0, exitCode1, "First execution should succeed");
        String output1 = outContent.toString();

        // Reset streams
        outContent.reset();
        errContent.reset();

        int exitCode2 = cmd.execute();
        assertEquals(0, exitCode2, "Second execution should succeed");
        String output2 = outContent.toString();

        assertEquals(output1, output2, "Schema should be consistent across runs");
    }

    @Test
    void testGenerateSchema_MultipleFilesInSequence() throws Exception {
        // Test generating to multiple files in sequence
        Path file1 = tempDir.resolve("schema1.json");
        Path file2 = tempDir.resolve("schema2.json");

        int exitCode1 = cmd.execute(file1.toString());
        assertEquals(0, exitCode1, "First file generation should succeed");

        // Reset command for second execution
        setUp();

        int exitCode2 = cmd.execute(file2.toString());
        assertEquals(0, exitCode2, "Second file generation should succeed");

        assertTrue(Files.exists(file1), "First file should exist");
        assertTrue(Files.exists(file2), "Second file should exist");

        String content1 = Files.readString(file1);
        String content2 = Files.readString(file2);
        assertEquals(content1, content2, "Both files should have identical content");
    }

    @Test
    void testGenerateSchema_RelativePath() throws Exception {
        // Create output file path in temp directory
        Path outputFile = tempDir.resolve("schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should handle path");
        assertTrue(Files.exists(outputFile),
                "File should be created");
    }

    @Test
    void testGenerateSchema_FileWithSpacesInPath() throws Exception {
        Path subDir = tempDir.resolve("my schemas");
        Path outputFile = subDir.resolve("config schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should handle spaces in path");
        assertTrue(Files.exists(outputFile), "File should be created despite spaces in path");

        String content = Files.readString(outputFile);
        assertTrue(content.contains("\"$schema\""), "Should contain valid schema");
    }
}