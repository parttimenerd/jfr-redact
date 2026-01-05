package me.bechberger.jfrredact.commands;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

class RedactTextCommandStdioTest {

    @Test
    void testStdinStdoutPiping() {
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
            assertThat(output).doesNotContain("dev-jsmith-01.corp.example.com");
            assertThat(output).doesNotContain("user@example.com");
            assertThat(output).doesNotContain("/home/johndoe");
            assertThat(output).isNotEmpty();

        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }
}