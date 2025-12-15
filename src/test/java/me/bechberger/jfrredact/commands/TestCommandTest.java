package me.bechberger.jfrredact.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestCommand (also used as validate command).
 */
class TestCommandTest {

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        TestCommand command = new TestCommand();
        cmd = new CommandLine(command);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        cmd.setOut(new PrintWriter(outContent, true));
        cmd.setErr(new PrintWriter(errContent, true));
    }

    @Test
    void testValidateMode_DefaultPreset() {
        // No test values provided = validation mode
        int exitCode = cmd.execute();

        assertEquals(0, exitCode, "Validation should succeed");

        String output = outContent.toString();
        assertTrue(output.contains("Configuration Validation") ||
                   output.contains("Configuration is valid"),
                "Should show validation confirmation");
        assertTrue(output.contains("Preset: default"),
                "Should show default preset");
    }

    @Test
    void testValidateMode_StrictPreset() {
        int exitCode = cmd.execute("--preset", "strict");

        assertEquals(0, exitCode, "Validation should succeed");

        String output = outContent.toString();
        assertTrue(output.contains("Configuration Validation") ||
                   output.contains("Configuration is valid"),
                "Should show validation confirmation");
        assertTrue(output.contains("Preset: strict"),
                "Should show strict preset");
    }

    @Test
    void testValidateMode_CustomConfig() throws Exception {
        // Create a valid config file
        Path configFile = tempDir.resolve("test-config.yaml");
        Files.writeString(configFile, """
                general:
                  redaction_text: "***"
                  pseudonymization:
                    enabled: false
                properties:
                  enabled: true
                  patterns:
                    - password
                """);

        int exitCode = cmd.execute("--config", configFile.toString());

        assertEquals(0, exitCode, "Validation should succeed");

        String output = outContent.toString();
        assertTrue(output.contains("Configuration Validation") ||
                   output.contains("Configuration is valid"),
                "Should show validation confirmation");
    }

    @Test
    void testValidateMode_InvalidConfig() throws Exception {
        // Create an invalid config file
        Path configFile = tempDir.resolve("invalid-config.yaml");
        Files.writeString(configFile, """
                invalid_yaml: [[[
                broken
                """);

        int exitCode = cmd.execute("--config", configFile.toString());

        assertNotEquals(0, exitCode, "Should fail with invalid config");

        String stderr = errContent.toString();
        assertTrue(stderr.contains("Error") || stderr.contains("error"),
                "Should show error message");
    }

    @Test
    void testTestMode_EventFiltering() {
        int exitCode = cmd.execute("--event", "jdk.JavaMonitorEnter");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertTrue(output.contains("Configuration Test Results"),
                "Should show test results");
        assertTrue(output.contains("jdk.JavaMonitorEnter"),
                "Should show event type");
    }

    @Test
    void testTestMode_PropertyRedaction() {
        int exitCode = cmd.execute("--property", "password", "--value", "secret123");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertTrue(output.contains("Configuration Test Results"),
                "Should show test results");
        assertTrue(output.contains("password"),
                "Should show property name");
    }

    @Test
    void testTestMode_ThreadFiltering() {
        int exitCode = cmd.execute("--thread", "main");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertTrue(output.contains("Configuration Test Results"),
                "Should show test results");
        assertTrue(output.contains("main"),
                "Should show thread name");
    }

    @Test
    void testTestMode_StringRedaction() {
        int exitCode = cmd.execute("--value", "user@example.com");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertTrue(output.contains("Configuration Test Results"),
                "Should show test results");
        assertTrue(output.contains("user@example.com"),
                "Should show value being tested");
    }

    @Test
    void testTestMode_WithPseudonymization() {
        int exitCode = cmd.execute("--pseudonymize", "--value", "secret");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertTrue(output.contains("Pseudonymize: enabled"),
                "Should show pseudonymization is enabled");
    }

    @Test
    void testTestMode_WithStrictPreset() {
        int exitCode = cmd.execute("--preset", "strict", "--value", "user@example.com");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertTrue(output.contains("Preset: strict"),
                "Should show strict preset");
        assertTrue(output.contains("user@example.com"),
                "Should show value being tested");
    }

    @Test
    void testHelp() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should exit successfully");

        String output = outContent.toString();
        assertTrue(output.contains("test"),
                "Help should mention command name");
        assertTrue(output.contains("--property"),
                "Help should mention --property option");
        assertTrue(output.contains("--value"),
                "Help should mention --value option");
        assertTrue(output.contains("validate"),
                "Help should mention validate alias");
    }

    @Test
    void testNonexistentConfigFile() {
        int exitCode = cmd.execute("--config", "/nonexistent/path/config.yaml");

        assertNotEquals(0, exitCode, "Should fail with nonexistent file");

        String stderr = errContent.toString();
        assertTrue(stderr.contains("Error") || stderr.contains("not found"),
                "Should show error about missing file");
    }
}