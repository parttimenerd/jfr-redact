package me.bechberger.jfrredact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple tests for the CLI main entry point.
 * Detailed command-specific tests are in the individual CommandTest classes.
 */
class MainTest {

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        Main cli = new Main();
        cmd = new CommandLine(cli);
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        cmd.setOut(new PrintWriter(outContent, true));
        cmd.setErr(new PrintWriter(errContent, true));
    }

    @Test
    void testHelp_ShowsSubcommands() {
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode, "Help should exit successfully");

        String output = outContent.toString();
        assertThat(output)
                .as("Help should show commands section")
                .contains("Commands:")
                .as("Help should list redact command")
                .contains("redact")
                .as("Help should list generate-config command")
                .contains("generate-config");
    }

    @Test
    void testVersion() {
        int exitCode = cmd.execute("--version");

        assertEquals(0, exitCode, "Version should exit successfully");

        String output = outContent.toString();
        assertThat(output)
                .as("Should show version information")
                .containsAnyOf("jfr-redact", "version");
    }

    @Test
    void testUnknownCommand() {
        int exitCode = cmd.execute("unknown-command");

        assertNotEquals(0, exitCode, "Should fail with unknown command");

        String stderr = errContent.toString();
        assertThat(stderr)
                .as("Should show error about unknown command")
                .containsAnyOf("Unknown", "not found", "Unmatched");
    }

    @Test
    void testNoArguments_ShowsHelp() {
        cmd.execute();

        // Should show usage/help information
        String output = outContent.toString() + errContent.toString();
        assertThat(output)
                .as("Should show usage information")
                .containsAnyOf("Usage", "Commands");
    }
}