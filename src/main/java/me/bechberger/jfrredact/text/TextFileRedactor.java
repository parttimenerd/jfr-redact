package me.bechberger.jfrredact.text;

import me.bechberger.jfrredact.config.DiscoveryConfig;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.DiscoveredPatterns;
import me.bechberger.jfrredact.engine.PatternDiscoveryEngine;
import me.bechberger.jfrredact.engine.RedactionEngine;
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
    private final RedactionConfig config;
    private me.bechberger.jfrredact.engine.InteractiveDecisionManager interactiveDecisionManager;

    public TextFileRedactor(RedactionEngine redactionEngine) {
        this(redactionEngine, null);
    }

    public TextFileRedactor(RedactionEngine redactionEngine, RedactionConfig config) {
        this.redactionEngine = redactionEngine;
        this.config = config;
    }

    /**
     * Set the interactive decision manager for interactive mode
     */
    public void setInteractiveDecisionManager(me.bechberger.jfrredact.engine.InteractiveDecisionManager manager) {
        this.interactiveDecisionManager = manager;
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
        if (config == null || config.getDiscovery().getMode() == DiscoveryConfig.DiscoveryMode.NONE) {
            logger.info("Starting text file redaction (no discovery)");
            redactFileWithoutDiscovery(inputPath, outputPath);
            return;
        }

        DiscoveryConfig.DiscoveryMode mode = config.getDiscovery().getMode();

        switch (mode) {
            case FAST:
                logger.info("Starting text file redaction with fast discovery");
                redactFileWithFastDiscovery(inputPath, outputPath);
                break;

            case TWO_PASS:
            default:
                logger.info("Starting text file redaction with two-pass discovery");
                redactFileWithTwoPassDiscovery(inputPath, outputPath);
                break;
        }
    }

    /**
     * Redact without discovery - standard single-pass processing.
     */
    private void redactFileWithoutDiscovery(Path inputPath, Path outputPath) throws IOException {
        logger.debug("Input:  {}", inputPath);
        logger.debug("Output: {}", outputPath);

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            redactStream(reader, writer);
        }
    }

    /**
     * Redact with on-the-fly discovery - discover and redact in one pass.
     */
    private void redactFileWithFastDiscovery(Path inputPath, Path outputPath) throws IOException {
        logger.debug("Input:  {}", inputPath);
        logger.debug("Output: {}", outputPath);

        PatternDiscoveryEngine discoveryEngine = new PatternDiscoveryEngine(
            config.getDiscovery(),
            config.getStrings()
        );

        int linesProcessed = 0;
        int linesRedacted = 0;

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {

            String line;
            while ((line = reader.readLine()) != null) {
                // Analyze for discovery
                discoveryEngine.analyzeLine(line);

                // Update discovered patterns periodically
                if (linesProcessed % 100 == 0) {
                    redactionEngine.setDiscoveredPatterns(discoveryEngine.getDiscoveredPatterns());
                }

                // Redact the line
                String redactedLine = redactLine(line);
                writer.write(redactedLine);
                writer.newLine();

                linesProcessed++;
                if (!line.equals(redactedLine)) {
                    linesRedacted++;
                }

                if (linesProcessed % 1000 == 0) {
                    logger.info("Processed {} lines ({} redacted)", linesProcessed, linesRedacted);
                }
            }

            // Final update
            redactionEngine.setDiscoveredPatterns(discoveryEngine.getDiscoveredPatterns());
            writer.flush();
        }

        logger.info(discoveryEngine.getStatistics());
        logger.info("Text file redaction complete: {} lines processed, {} lines contained redactions",
                    linesProcessed, linesRedacted);
    }

    /**
     * Redact with two-pass discovery - read file twice for complete discovery.
     */
    private void redactFileWithTwoPassDiscovery(Path inputPath, Path outputPath) throws IOException {
        logger.debug("Input:  {}", inputPath);
        logger.debug("Output: {}", outputPath);

        // DISCOVERY PASS: Read file to discover patterns
        logger.info("Discovery Pass: Analyzing file for sensitive patterns...");
        PatternDiscoveryEngine discoveryEngine = new PatternDiscoveryEngine(
            config.getDiscovery(),
            config.getStrings()
        );

        // Set interactive decision manager if available
        if (interactiveDecisionManager != null) {
            discoveryEngine.setInteractiveDecisionManager(interactiveDecisionManager);
        }

        int discoveryLines = 0;
        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                discoveryEngine.analyzeLine(line);
                discoveryLines++;

                if (discoveryLines % 10000 == 0) {
                    logger.info("Discovery: analyzed {} lines", discoveryLines);
                }
            }
        }

        DiscoveredPatterns patterns = discoveryEngine.getDiscoveredPatterns();
        logger.info("Discovery Pass complete: analyzed {} lines", discoveryLines);
        logger.info(discoveryEngine.getStatistics());

        // Apply interactive decisions and save them
        if (interactiveDecisionManager != null) {
            patterns = discoveryEngine.applyInteractiveDecisions(patterns);
            interactiveDecisionManager.saveDecisions();
        }

        // Set discovered patterns in the redaction engine
        redactionEngine.setDiscoveredPatterns(patterns);

        // REDACTION PASS: Read file again to redact
        logger.info("Redaction Pass: Processing file with discovered patterns...");
        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            redactStream(reader, writer);
        }
    }

    /**
     * Redact text from an input stream and write to an output stream.
     * Useful for stdin/stdout processing.
     *
     * @param input  The input stream to read from
     * @param output The output stream to write to
     * @throws IOException If I/O operations fail
     */
    public void redactStream(InputStream input, OutputStream output) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            redactStream(reader, writer);
        }
    }

    /**
     * Redact text from a reader and write to a writer.
     *
     * @param reader The reader to read from
     * @param writer The writer to write to
     * @throws IOException If I/O operations fail
     */
    public void redactStream(BufferedReader reader, BufferedWriter writer) throws IOException {
        int linesProcessed = 0;
        int linesRedacted = 0;

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

        writer.flush();
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