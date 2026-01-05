package me.bechberger.jfrredact.testutil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mock console for testing interactive commands.
 * Simulates user input and captures output.
 */
public class MockConsole {

    private final List<String> inputLines = new ArrayList<>();
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
    private final InputStream originalIn;
    private final PrintStream originalOut;
    private final PrintStream originalErr;

    private int currentInputLine = 0;

    public MockConsole() {
        this.originalIn = System.in;
        this.originalOut = System.out;
        this.originalErr = System.err;
    }

    /**
     * Add input lines that will be "typed" by the user
     */
    public MockConsole withInput(String... lines) {
        inputLines.addAll(Arrays.asList(lines));
        return this;
    }

    /**
     * Add a single input line
     */
    public MockConsole withInputLine(String line) {
        inputLines.add(line);
        return this;
    }

    /**
     * Install this mock console to replace System.in/out/err
     */
    public void install() {
        // Prepare input stream
        StringBuilder input = new StringBuilder();
        for (String line : inputLines) {
            input.append(line).append("\n");
        }
        System.setIn(new ByteArrayInputStream(input.toString().getBytes()));

        // Redirect output streams
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(errorStream));
    }

    /**
     * Restore original System streams
     */
    public void restore() {
        System.setIn(originalIn);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    /**
     * Get all output written to stdout
     */
    public String getOutput() {
        return outputStream.toString();
    }

    /**
     * Get all output written to stderr
     */
    public String getError() {
        return errorStream.toString();
    }

    /**
     * Get output lines
     */
    public List<String> getOutputLines() {
        return List.of(getOutput().split("\n"));
    }

    /**
     * Get error lines
     */
    public List<String> getErrorLines() {
        return List.of(getError().split("\n"));
    }

    /**
     * Clear all captured output
     */
    public void clear() {
        outputStream.reset();
        errorStream.reset();
    }

    /**
     * Check if output contains a string
     */
    public boolean outputContains(String text) {
        return getOutput().contains(text);
    }

    /**
     * Check if error contains a string
     */
    public boolean errorContains(String text) {
        return getError().contains(text);
    }
}