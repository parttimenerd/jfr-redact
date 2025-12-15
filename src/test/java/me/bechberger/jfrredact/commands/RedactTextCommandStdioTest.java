package me.bechberger.jfrredact.commands;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class RedactTextCommandStdioTest {

    @Test
    void testStdinStdoutPiping() throws Exception {
        String input = "Host: dev-jsmith-01.corp.example.com\nUser email: user@example.com\nPath: /home/johndoe/.ssh/id_rsa\n";

        // Set System.in to the input
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes);

        System.setIn(in);
        System.setOut(out);

        try {
            RedactTextCommand cmdObj = new RedactTextCommand();
            CommandLine cmd = new CommandLine(cmdObj);

            int exitCode = cmd.execute("-", "-");
            assertEquals(0, exitCode, "Command should exit 0");

            out.flush();
            String output = outBytes.toString();

            // Expect host, email and path to be redacted
            assertFalse(output.contains("dev-jsmith-01.corp.example.com"), "Hostname should be redacted");
            assertFalse(output.contains("user@example.com"), "Email should be redacted");
            assertFalse(output.contains("/home/johndoe"), "Home path should be redacted");
            assertTrue(output.length() > 0, "There should be some output");

        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }
}