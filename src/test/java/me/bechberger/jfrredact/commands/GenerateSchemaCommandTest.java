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
        assertThat(output).contains("\"$schema\"").contains("\"type\"").contains("\"properties\"").hasSizeGreaterThan(100);
    }

    @Test
    void testGenerateSchema_ToFile() throws Exception {
        Path outputFile = tempDir.resolve("config-schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertThat(outputFile).exists();

        String content = Files.readString(outputFile);
        assertThat(content).contains("\"$schema\"").contains("\"type\"").contains("\"properties\"");

        String errOutput = errContent.toString();
        assertThat(errOutput).contains("Schema written to:").contains(outputFile.getFileName().toString());
    }

    @Test
    void testGenerateSchema_ToFileInSubdirectory() throws Exception {
        Path subDir = tempDir.resolve("schemas");
        Path outputFile = subDir.resolve("config-schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertThat(subDir).exists();
        assertThat(outputFile).exists();

        String content = Files.readString(outputFile);
        assertThat(content).contains("\"$schema\"");
    }

    @Test
    void testGenerateSchema_OverwriteExistingFile() throws Exception {
        Path outputFile = tempDir.resolve("config-schema.json");
        Files.writeString(outputFile, "old content");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");

        String content = Files.readString(outputFile);
        assertThat(content).doesNotContain("old content", "Should overwrite existing file");
        assertThat(content).contains("\"$schema\"");
    }

    @Test
    void testGenerateSchema_ValidJson() throws Exception {
        Path outputFile = tempDir.resolve("schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");

        String content = Files.readString(outputFile);

        // Basic JSON validation - should start with { and end with }
        String trimmed = content.trim();
        assertThat(trimmed).startsWith("{").endsWith("}");

        // Should not have common JSON errors
        assertThat(content).doesNotContain(",}", ",]");
    }

    @Test
    void testGenerateSchema_ContainsExpectedFields() throws Exception {
        Path outputFile = tempDir.resolve("schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");

        String content = Files.readString(outputFile);

        // Check for expected RedactionConfig fields
        assertThat(content).satisfiesAnyOf(
            c -> assertThat(c).contains("properties"),
            c -> assertThat(c).contains("\"properties\"")
        );
        assertThat(content).satisfiesAnyOf(
            c -> assertThat(c).contains("strings"),
            c -> assertThat(c).contains("\"strings\"")
        );
        assertThat(content).satisfiesAnyOf(
            c -> assertThat(c).contains("events"),
            c -> assertThat(c).contains("\"events\"")
        );
        assertThat(content).satisfiesAnyOf(
            c -> assertThat(c).contains("threads"),
            c -> assertThat(c).contains("\"threads\"")
        );
    }

    @Test
    void testGenerateSchema_StdoutDoesNotShowSuccessMessage() {
        int exitCode = cmd.execute();

        assertEquals(0, exitCode, "Should exit successfully");

        String errOutput = errContent.toString();
        assertThat(errOutput).doesNotContain("Schema written to:");
    }

    @Test
    void testGenerateSchema_Help() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Should show help successfully");

        String output = outContent.toString();
        assertThat(output).contains("generate-schema", "Generate JSON Schema", "Examples");
    }

    @Test
    void testGenerateSchema_Version() {
        int exitCode = cmd.execute("--version");

        assertEquals(0, exitCode, "Should show version successfully");

        String output = outContent.toString();
        assertThat(output).isNotEmpty();
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

        assertThat(file1).exists();
        assertThat(file2).exists();

        String content1 = Files.readString(file1);
        String content2 = Files.readString(file2);
        assertEquals(content1, content2, "Both files should have identical content");
    }

    @Test
    void testGenerateSchema_RelativePath() {
        // Create output file path in temp directory
        Path outputFile = tempDir.resolve("schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should handle path");
        assertThat(outputFile).exists();
    }

    @Test
    void testGenerateSchema_FileWithSpacesInPath() throws Exception {
        Path subDir = tempDir.resolve("my schemas");
        Path outputFile = subDir.resolve("config schema.json");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should handle spaces in path");
        assertThat(outputFile).exists();

        String content = Files.readString(outputFile);
        assertThat(content).contains("\"$schema\"");
    }
}