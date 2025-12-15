package me.bechberger.jfrredact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;

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
        assertTrue(output.contains("Commands:"),
                "Help should show commands section");
        assertTrue(output.contains("redact"),
                "Help should list redact command");
        assertTrue(output.contains("generate-config"),
                "Help should list generate-config command");
    }

    @Test
    void testVersion() {
        int exitCode = cmd.execute("--version");

        assertEquals(0, exitCode, "Version should exit successfully");

        String output = outContent.toString();
        assertTrue(output.contains("jfr-redact") || output.contains("version"),
                "Should show version information");
    }

    @Test
    void testUnknownCommand() {
        int exitCode = cmd.execute("unknown-command");

        assertNotEquals(0, exitCode, "Should fail with unknown command");

        String stderr = errContent.toString();
        assertTrue(stderr.contains("Unknown") || stderr.contains("not found") ||
                   stderr.contains("Unmatched"),
                "Should show error about unknown command");
    }

    @Test
    void testNoArguments_ShowsHelp() {
        cmd.execute();

        // Should show usage/help information
        String output = outContent.toString() + errContent.toString();
        assertTrue(output.contains("Usage") || output.contains("Commands"),
                "Should show usage information");
    }
}