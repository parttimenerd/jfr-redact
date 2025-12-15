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
 * Tests for GenerateConfigCommand.
 */
class GenerateConfigCommandTest {

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        GenerateConfigCommand command = new GenerateConfigCommand();
        cmd = new CommandLine(command);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        cmd.setOut(new PrintWriter(outContent, true));
        cmd.setErr(new PrintWriter(errContent, true));
    }

    @Test
    void testGenerateDefaultTemplate_ToStdout() {
        int exitCode = cmd.execute();

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        assertTrue(output.contains("# Custom Configuration Template"),
                "Should contain template header");
        assertTrue(output.contains("properties:"),
                "Should contain properties section");
        assertTrue(output.contains("strings:"),
                "Should contain strings section");
        assertTrue(output.contains("general:"),
                "Should contain general section");
    }

    @Test
    void testGenerateDefaultTemplate_ToFile() throws Exception {
        Path outputFile = tempDir.resolve("test-config.yaml");

        int exitCode = cmd.execute("-o", outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        String content = Files.readString(outputFile);
        assertTrue(content.contains("# Custom Configuration Template"),
                "Should contain template header");
        assertTrue(content.contains("properties:"),
                "Should contain properties section");

        String stderr = errContent.toString();
        assertTrue(stderr.contains("Configuration written to:"),
                "Should show confirmation message");
    }

    @Test
    void testGenerateDefaultTemplate_PositionalOutput() throws Exception {
        Path outputFile = tempDir.resolve("positional-config.yaml");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        String content = Files.readString(outputFile);
        assertTrue(content.contains("# Custom Configuration Template"),
                "Should contain template header");
    }

    @Test
    void testGenerateFromPreset_Default() {
        int exitCode = cmd.execute("--preset", "default");

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        assertFalse(output.isEmpty(), "Should generate output");

        String stderr = errContent.toString();
        assertTrue(stderr.contains("Generated configuration based on preset: default"),
                "Should show preset message");
    }

    @Test
    void testGenerateFromPreset_Strict() {
        int exitCode = cmd.execute("--preset", "strict");

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        assertFalse(output.isEmpty(), "Should generate output");

        String stderr = errContent.toString();
        assertTrue(stderr.contains("Generated configuration based on preset: strict"),
                "Should show preset message");
    }

    @Test
    void testGenerateFromPreset_ToFile() throws Exception {
        Path outputFile = tempDir.resolve("strict-config.yaml");

        int exitCode = cmd.execute("--preset", "strict", "-o", outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        String content = Files.readString(outputFile);
        assertFalse(content.isEmpty(), "File should not be empty");
    }

    @Test
    void testGenerateMinimalConfig() {
        int exitCode = cmd.execute("--minimal");

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        assertTrue(output.contains("# Minimal JFR Redaction Configuration"),
                "Should contain minimal config header");
        assertTrue(output.contains("#properties:"),
                "Should contain commented properties section");
        assertTrue(output.contains("#strings:"),
                "Should contain commented strings section");
    }

    @Test
    void testGenerateMinimalConfig_ToFile() throws Exception {
        Path outputFile = tempDir.resolve("minimal-config.yaml");

        int exitCode = cmd.execute("--minimal", "-o", outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertTrue(Files.exists(outputFile), "Output file should be created");

        String content = Files.readString(outputFile);
        assertTrue(content.contains("# Minimal JFR Redaction Configuration"),
                "Should contain minimal config header");
    }

    @Test
    void testHelp() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should exit successfully");

        String output = outContent.toString();
        assertTrue(output.contains("generate-config"),
                "Help should mention command name");
        assertTrue(output.contains("--preset"),
                "Help should mention --preset option");
        assertTrue(output.contains("--minimal"),
                "Help should mention --minimal option");
    }

    @Test
    void testInvalidPreset() {
        int exitCode = cmd.execute("--preset", "nonexistent");

        assertNotEquals(0, exitCode, "Should fail with invalid preset");

        String stderr = errContent.toString();
        assertTrue(stderr.contains("Invalid value") || stderr.contains("error") || stderr.contains("nonexistent"),
                "Should show error message about invalid preset, got: " + stderr);
    }

    @Test
    void testBothPresetAndMinimal_PresetTakesPrecedence() {
        // When both are specified, preset should take precedence
        int exitCode = cmd.execute("--preset", "default", "--minimal");

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        // Should use preset, not minimal
        assertFalse(output.contains("# Minimal JFR Redaction Configuration"),
                "Should not generate minimal config when preset is specified");
    }
}