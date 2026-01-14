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
        assertThat(output).contains("# Custom Configuration Template", "properties:", "strings:", "general:");
    }

    @Test
    void testGenerateDefaultTemplate_ToFile() throws Exception {
        Path outputFile = tempDir.resolve("test-config.yaml");

        int exitCode = cmd.execute("-o", outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertThat(outputFile).exists();

        String content = Files.readString(outputFile);
        assertThat(content).contains("# Custom Configuration Template", "properties:");

        String stderr = errContent.toString();
        assertThat(stderr).contains("Configuration written to:");
    }

    @Test
    void testGenerateDefaultTemplate_PositionalOutput() throws Exception {
        Path outputFile = tempDir.resolve("positional-config.yaml");

        int exitCode = cmd.execute(outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertThat(outputFile).exists();

        String content = Files.readString(outputFile);
        assertThat(content).contains("# Custom Configuration Template");
    }

    @Test
    void testGenerateFromPreset_Default() {
        int exitCode = cmd.execute("default");

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        assertThat(output).isNotEmpty();

        String stderr = errContent.toString();
        assertThat(stderr).contains("Generated configuration based on preset: default");
    }

    @Test
    void testGenerateFromPreset_Strict() {
        int exitCode = cmd.execute("strict");

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        assertThat(output).isNotEmpty();

        String stderr = errContent.toString();
        assertThat(stderr).contains("Generated configuration based on preset: strict");
    }

    @Test
    void testGenerateFromPreset_ToFile() throws Exception {
        Path outputFile = tempDir.resolve("strict-config.yaml");

        int exitCode = cmd.execute("strict", "-o", outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertThat(outputFile).exists();
    }

    @Test
    void testGenerateMinimalConfig() {
        int exitCode = cmd.execute("--minimal");

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        assertThat(output).contains("# Minimal JFR Redaction Configuration", "#properties:", "#strings:");
    }

    @Test
    void testGenerateMinimalConfig_ToFile() throws Exception {
        Path outputFile = tempDir.resolve("minimal-config.yaml");

        int exitCode = cmd.execute("--minimal", "-o", outputFile.toString());

        assertEquals(0, exitCode, "Should exit successfully");
        assertThat(outputFile).exists();
    }

    @Test
    void testHelp() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should exit successfully");

        String output = outContent.toString();
        assertThat(output).contains("generate-config", "preset", "--minimal");
    }

    @Test
    void testInvalidPreset() {
        int exitCode = cmd.execute("--config", "nonexistent");

        assertNotEquals(0, exitCode, "Should fail with invalid preset");

        String stderr = errContent.toString();
        assertThat(stderr).satisfiesAnyOf(
            s -> assertThat(s).contains("Invalid value"),
            s -> assertThat(s).contains("error"),
            s -> assertThat(s).contains("nonexistent")
        );
    }

    @Test
    void testBothPresetAndMinimal_PresetTakesPrecedence() {
        // When both are specified, preset should take precedence
        int exitCode = cmd.execute("default", "--minimal");

        assertEquals(0, exitCode, "Should exit successfully");

        String output = outContent.toString();
        // Should use preset, not minimal
        assertThat(output).doesNotContain("# Minimal JFR Redaction Configuration");
    }
}