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
        assertThat(output.contains("Configuration Validation") || output.contains("Configuration is valid")).as("Should show validation confirmation").isTrue();
        assertThat(output).as("Should show default preset").contains("Preset: default");
    }

    @Test
    void testValidateMode_StrictPreset() {
        int exitCode = cmd.execute("--preset", "strict");

        assertEquals(0, exitCode, "Validation should succeed");

        String output = outContent.toString();
        assertThat(output.contains("Configuration Validation") || output.contains("Configuration is valid")).as("Should show validation confirmation").isTrue();
        assertThat(output).as("Should show strict preset").contains("Preset: strict");
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
        assertThat(output.contains("Configuration Validation") || output.contains("Configuration is valid")).as("Should show validation confirmation").isTrue();
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
        assertThat(stderr.contains("Error") || stderr.contains("error")).as("Should show error message").isTrue();
    }

    @Test
    void testTestMode_EventFiltering() {
        int exitCode = cmd.execute("--event", "jdk.JavaMonitorEnter");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertThat(output).as("Should show test results").contains("Configuration Test Results");
        assertThat(output).as("Should show event type").contains("jdk.JavaMonitorEnter");
    }

    @Test
    void testTestMode_PropertyRedaction() {
        int exitCode = cmd.execute("--property", "password", "--value", "secret123");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertThat(output).as("Should show test results").contains("Configuration Test Results");
        assertThat(output).as("Should show property name").contains("password");
    }

    @Test
    void testTestMode_ThreadFiltering() {
        int exitCode = cmd.execute("--thread", "main");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertThat(output).as("Should show test results").contains("Configuration Test Results");
        assertThat(output).as("Should show thread name").contains("main");
    }

    @Test
    void testTestMode_StringRedaction() {
        int exitCode = cmd.execute("--value", "user@example.com");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertThat(output).as("Should show test results").contains("Configuration Test Results");
        assertThat(output).as("Should show value being tested").contains("user@example.com");
    }

    @Test
    void testTestMode_WithPseudonymization() {
        int exitCode = cmd.execute("--pseudonymize", "--value", "secret");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertThat(output).as("Should show pseudonymization is enabled").contains("Pseudonymize: enabled");
    }

    @Test
    void testTestMode_WithStrictPreset() {
        int exitCode = cmd.execute("--preset", "strict", "--value", "user@example.com");

        assertEquals(0, exitCode, "Test should succeed");

        String output = outContent.toString();
        assertThat(output).as("Should show strict preset").contains("Preset: strict");
        assertThat(output).as("Should show value being tested").contains("user@example.com");
    }

    @Test
    void testHelp() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should exit successfully");

        String output = outContent.toString();
        assertThat(output).as("Help should mention command name").contains("test");
        assertThat(output).as("Help should mention --property option").contains("--property");
        assertThat(output).as("Help should mention --value option").contains("--value");
        assertThat(output).as("Help should mention validate alias").contains("validate");
    }

    @Test
    void testNonexistentConfigFile() {
        int exitCode = cmd.execute("--config", "/nonexistent/path/config.yaml");

        assertNotEquals(0, exitCode, "Should fail with nonexistent file");

        String stderr = errContent.toString();
        assertThat(stderr.contains("Error") || stderr.contains("not found")).as("Should show error about missing file").isTrue();
    }
}