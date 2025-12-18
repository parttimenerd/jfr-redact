package me.bechberger.jfrredact.commands;

import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.Version;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import me.bechberger.jfrredact.jfr.JFRProcessor;
import org.openjdk.jmc.flightrecorder.writer.RecordingImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Concat command - concatenates multiple JFR recordings into one without redaction.
 */
@Command(
    name = "concat",
    description = "Concatenate multiple JFR recordings into a single file without any redaction",
    mixinStandardHelpOptions = true,
    version = Version.FULL_VERSION,
    footerHeading = "%nExamples:%n",
    footer = {
        "",
        "  Concatenate two JFR files:",
        "    jfr-redact concat one.jfr two.jfr -o combined.jfr",
        "",
        "  Concatenate multiple files:",
        "    jfr-redact concat *.jfr -o all-recordings.jfr",
        "",
        "  Ignore empty files (with warning):",
        "    jfr-redact concat *.jfr -o merged.jfr -i",
        ""
    }
)
public class ConcatCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ConcatCommand.class);

    @Parameters(
        description = "Input JFR files to concatenate",
        paramLabel = "<input.jfr>",
        arity = "1..*"
    )
    private List<File> inputFiles;

    @Option(
        names = {"-o", "--output"},
        description = "Output JFR file (required)",
        paramLabel = "<output.jfr>",
        required = true
    )
    private File outputFile;

    @Option(
        names = {"--verbose", "-v"},
        description = "Enable verbose output"
    )
    private boolean verbose;

    @Option(
        names = {"-i", "--ignore-empty"},
        description = "Ignore empty files (with a warning) instead of failing"
    )
    private boolean ignoreEmpty;

    @Override
    public Integer call() {
        // Validate input files and filter out empty ones if requested
        List<File> validFiles = new ArrayList<>();
        for (File inputFile : inputFiles) {
            if (!inputFile.exists()) {
                logger.error("Input file does not exist: {}", inputFile);
                return 1;
            }
            if (!inputFile.canRead()) {
                logger.error("Cannot read input file: {}", inputFile);
                return 1;
            }

            // Check for empty files
            if (inputFile.length() == 0) {
                if (ignoreEmpty) {
                    logger.warn("Ignoring empty file: {}", inputFile);
                    continue;
                } else {
                    logger.error("Input file is empty: {}. Use -i to ignore empty files.", inputFile);
                    return 1;
                }
            }

            validFiles.add(inputFile);
        }

        // Check if we have any valid files left after filtering
        if (validFiles.isEmpty()) {
            logger.error("No valid (non-empty) files to concatenate");
            return 1;
        }

        // Update inputFiles to only contain valid files
        inputFiles = validFiles;

        // Check that we're not writing to an input file
        for (File inputFile : inputFiles) {
            try {
                if (inputFile.getCanonicalPath().equals(outputFile.getCanonicalPath())) {
                    logger.error("Output file cannot be the same as an input file: {}", outputFile);
                    return 1;
                }
            } catch (IOException e) {
                logger.error("Error checking file paths: {}", e.getMessage());
                return 1;
            }
        }

        logger.info("Concatenating {} JFR file(s) into {}", inputFiles.size(), outputFile);

        try {
            concatenate();
            logger.info("Successfully created concatenated JFR file: {}", outputFile);
            return 0;
        } catch (IOException e) {
            logger.error("Error concatenating JFR files: {}", e.getMessage());
            if (verbose) {
                logger.error("Stack trace:", e);
            }
            return 1;
        }
    }

    private void concatenate() throws IOException {
        // Create a no-op redaction engine (doesn't redact anything)
        RedactionConfig config = new RedactionConfig();
        // Disable all redaction features
        config.getStrings().setEnabled(false);
        config.getProperties().setEnabled(false);
        config.getNetwork().setEnabled(false);
        config.getPaths().setEnabled(false);
        config.getEvents().setRemoveEnabled(false);

        RedactionEngine redactionEngine = new RedactionEngine(config);

        // Create a JFRProcessor with the first input file path (just for initialization)
        JFRProcessor processor = new JFRProcessor(redactionEngine, config, inputFiles.get(0).toPath());

        // Open all input files as RecordingFile objects
        List<RecordingFile> recordingFiles = new ArrayList<>();
        try {
            for (File inputFile : inputFiles) {
                recordingFiles.add(new RecordingFile(inputFile.toPath()));
            }

            // Process all recordings into a single output file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                RecordingImpl recording = processor.processRecordingFilesWithoutAnyProcessing(recordingFiles, fos);
                // Must close the recording to flush all data to the output stream
                recording.close();
                fos.flush();
            }
        } finally {
            // Close all recording files
            for (RecordingFile rf : recordingFiles) {
                try {
                    rf.close();
                } catch (IOException e) {
                    logger.warn("Error closing recording file: {}", e.getMessage());
                }
            }
        }
    }
}