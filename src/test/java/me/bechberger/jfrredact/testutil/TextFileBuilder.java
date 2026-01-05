package me.bechberger.jfrredact.testutil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builder for creating test text files.
 * Simplifies test setup for text-based tests.
 */
public class TextFileBuilder {

    private final List<String> lines = new ArrayList<>();
    private Path outputPath;

    /**
     * Create a new text file builder
     */
    public static TextFileBuilder create() {
        return new TextFileBuilder();
    }

    /**
     * Set the output path for the file
     */
    public TextFileBuilder outputTo(Path path) {
        this.outputPath = path;
        return this;
    }

    /**
     * Add a line to the file
     */
    public TextFileBuilder withLine(String line) {
        lines.add(line);
        return this;
    }

    /**
     * Add multiple lines to the file
     */
    public TextFileBuilder withLines(String... lines) {
        this.lines.addAll(Arrays.asList(lines));
        return this;
    }

    /**
     * Add multiple lines to the file
     */
    public TextFileBuilder withLines(List<String> lines) {
        this.lines.addAll(lines);
        return this;
    }

    /**
     * Build and write the file
     */
    public Path build() throws IOException {
        if (outputPath == null) {
            outputPath = Files.createTempFile("test-text-", ".txt");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }

        return outputPath;
    }
}