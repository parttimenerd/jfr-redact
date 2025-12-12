package me.bechberger.jfrredact.text;

import me.bechberger.jfrredact.engine.RedactionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Redacts sensitive information from text files.
 * <p>
 * Applies the same redaction patterns used for JFR string fields
 * to arbitrary text files line by line.
 */
public class TextFileRedactor {

    private static final Logger logger = LoggerFactory.getLogger(TextFileRedactor.class);

    private final RedactionEngine redactionEngine;

    public TextFileRedactor(RedactionEngine redactionEngine) {
        this.redactionEngine = redactionEngine;
    }

    /**
     * Redact a text file and write the result to an output file.
     *
     * @param inputFile  The input text file to redact
     * @param outputFile The output file for redacted content
     * @throws IOException If file operations fail
     */
    public void redactFile(File inputFile, File outputFile) throws IOException {
        redactFile(inputFile.toPath(), outputFile.toPath());
    }

    /**
     * Redact a text file and write the result to an output file.
     *
     * @param inputPath  The input text file to redact
     * @param outputPath The output file for redacted content
     * @throws IOException If file operations fail
     */
    public void redactFile(Path inputPath, Path outputPath) throws IOException {
        logger.info("Starting text file redaction");
        logger.debug("Input:  {}", inputPath);
        logger.debug("Output: {}", outputPath);

        int linesProcessed = 0;
        int linesRedacted = 0;

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String redactedLine = redactLine(line);
                writer.write(redactedLine);
                writer.newLine();

                linesProcessed++;
                if (!line.equals(redactedLine)) {
                    linesRedacted++;
                    logger.debug("Redacted line {}", linesProcessed);
                }

                if (linesProcessed % 1000 == 0) {
                    logger.info("Processed {} lines ({} redacted)", linesProcessed, linesRedacted);
                }
            }
        }

        logger.info("Text file redaction complete: {} lines processed, {} lines contained redactions",
                    linesProcessed, linesRedacted);
    }

    /**
     * Redact a single line of text.
     * <p>
     * Uses the RedactionEngine to apply all configured string patterns
     * (emails, IPs, UUIDs, etc.) to the line.
     *
     * @param line The line to redact
     * @return The redacted line
     */
    public String redactLine(String line) {
        if (line == null) {
            return null;
        }

        // Use the RedactionEngine's string redaction with a generic field name
        // This will apply all string pattern matching (emails, IPs, UUIDs, etc.)
        return redactionEngine.redact("text", line);
    }

    /**
     * Redact a string of text.
     *
     * @param text The text to redact
     * @return The redacted text
     */
    public String redactText(String text) {
        if (text == null) {
            return null;
        }
        return redactionEngine.redact("text", text);
    }
}