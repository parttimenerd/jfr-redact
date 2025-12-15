package me.bechberger.jfrredact.commands;

import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.ConfigLoader;
import me.bechberger.jfrredact.Preset;
import me.bechberger.jfrredact.Version;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import me.bechberger.jfrredact.jfr.JFRProcessor;
import me.bechberger.jfrredact.text.TextFileRedactor;
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
 * Redact command - redacts sensitive information from JFR recordings.
 */
@Command(
    name = "redact",
    description = "Redact sensitive information from Java Flight Recorder (JFR) recordings",
    mixinStandardHelpOptions = true,
    version = Version.FULL_VERSION,
    footerHeading = "%nExamples:%n",
    footer = {
        "",
        "  Simple redaction with default preset:",
        "    jfr-redact redact recording.jfr",
        "    (creates recording.redacted.jfr)",
        "",
        "  Specify output file:",
        "    jfr-redact redact recording.jfr output.jfr",
        "",
        "  Strict preset with pseudonymization:",
        "    jfr-redact redact recording.jfr --preset strict --pseudonymize",
        "",
        "  Custom config with additional event removal:",
        "    jfr-redact redact recording.jfr --config my-config.yaml --remove-event jdk.CustomEvent",
        "",
        "  Add custom redaction pattern:",
        "    jfr-redact redact recording.jfr --add-redaction-regex '\\b[A-Z]{3}-\\d{6}\\b'",
        ""
    }
)
public class RedactCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(RedactCommand.class);

    @Parameters(
        index = "0",
        description = "Input JFR file to redact",
        paramLabel = "<input.jfr>"
    )
    private File inputFile;

    @Parameters(
        index = "1",
        description = "Output JFR file with redacted data (default: <input>.redacted.jfr)",
        paramLabel = "<output.jfr>",
        arity = "0..1"
    )
    private File outputFile;

    @Option(
        names = {"--preset"},
        description = "Use a predefined configuration preset. Valid values: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})",
        defaultValue = "default"
    )
    private Preset preset;

    @Option(
        names = {"--config"},
        description = "Load configuration from a YAML file or URL",
        paramLabel = "<file|url>"
    )
    private String configFile;

    @Option(
        names = {"--pseudonymize"},
        description = "Enable pseudonymization mode. When enabled, the same sensitive value " +
                     "always maps to the same pseudonym (e.g., &lt;redacted:a1b2c3&gt;), preserving " +
                     "relationships across events. Without this flag, all values are redacted to ***."
    )
    private boolean pseudonymize;

    @Option(
        names = {"--remove-event"},
        description = "Remove an additional event type from the output. " +
                     "This option can be specified multiple times to remove multiple event types.",
        paramLabel = "<type>"
    )
    private List<String> removeEvents = new ArrayList<>();

    @Option(
        names = {"--add-redaction-regex"},
        description = "Add a custom regular expression pattern for string redaction. " +
                     "This option can be specified multiple times to add multiple patterns. " +
                     "Patterns are applied to string fields in events.",
        paramLabel = "<pattern>"
    )
    private List<String> redactionRegexes = new ArrayList<>();

    // jfr scrub-style filtering options
    // See: https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html

    @Option(
        names = {"--include-events"},
        description = "Select events matching an event name (comma-separated list, supports glob patterns). " +
                     "Similar to jfr scrub --include-events.",
        paramLabel = "<filter>"
    )
    private List<String> includeEvents = new ArrayList<>();

    @Option(
        names = {"--exclude-events"},
        description = "Exclude events matching an event name (comma-separated list, supports glob patterns). " +
                     "Similar to jfr scrub --exclude-events.",
        paramLabel = "<filter>"
    )
    private List<String> excludeEvents = new ArrayList<>();

    @Option(
        names = {"--include-categories"},
        description = "Select events matching a category name (comma-separated list, supports glob patterns). " +
                     "Similar to jfr scrub --include-categories.",
        paramLabel = "<filter>"
    )
    private List<String> includeCategories = new ArrayList<>();

    @Option(
        names = {"--exclude-categories"},
        description = "Exclude events matching a category name (comma-separated list, supports glob patterns). " +
                     "Similar to jfr scrub --exclude-categories.",
        paramLabel = "<filter>"
    )
    private List<String> excludeCategories = new ArrayList<>();

    @Option(
        names = {"--include-threads"},
        description = "Select events matching a thread name (comma-separated list, supports glob patterns). " +
                     "Similar to jfr scrub --include-threads.",
        paramLabel = "<filter>"
    )
    private List<String> includeThreads = new ArrayList<>();

    @Option(
        names = {"--exclude-threads"},
        description = "Exclude events matching a thread name (comma-separated list, supports glob patterns). " +
                     "Similar to jfr scrub --exclude-threads.",
        paramLabel = "<filter>"
    )
    private List<String> excludeThreads = new ArrayList<>();

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output (INFO level logging)"
    )
    private boolean verbose;

    @Option(
        names = {"--debug"},
        description = "Enable debug output (DEBUG level logging)"
    )
    private boolean debug;

    @Option(
        names = {"-q", "--quiet"},
        description = "Minimize output (only show errors and completion message)"
    )
    private boolean quiet;

    @Option(
        names = {"--stats"},
        description = "Show statistics after redaction (events processed, removed, redactions applied)"
    )
    private boolean showStats;

    @Option(
        names = {"--dry-run"},
        description = "Process the file without writing output, useful for testing configuration with --stats"
    )
    private boolean dryRun;

    @Override
    public Integer call() {
        // Configure logging level based on flags
        configureLogging();

        // Generate default output filename if not provided
        setOutputIfNeeded();

        // Validate input file exists
        if (!inputFile.exists()) {
            logger.error("Input file not found: {}", inputFile.getAbsolutePath());
            return 1;
        }

        logger.info("JFR-Redact v{}", Version.VERSION);
        logger.info("================");
        logger.info("");
        logger.info("Input file:  {}", inputFile.getAbsolutePath());
        if (!dryRun) {
            logger.info("Output file: {}", outputFile.getAbsolutePath());
        } else {
            logger.info("Mode:        DRY RUN (no output will be written)");
        }
        logger.info("Preset:      {}", preset.getName());

        if (configFile != null) {
            logger.info("Config: {}", configFile);
        }

        logger.info("Pseudonymize: {}", pseudonymize ? "enabled" : "disabled");

        if (!removeEvents.isEmpty()) {
            logger.info("Remove events: {}", removeEvents);
        }

        if (!redactionRegexes.isEmpty()) {
            logger.info("Custom regexes: {}", redactionRegexes);
        }

        logger.info("");

        try {
            // Load configuration
            RedactionConfig config = loadConfiguration();

            // Create redaction engine
            RedactionEngine engine = new RedactionEngine(config);

            // Determine file type and process accordingly
            if (isJfrFile(inputFile)) {
                processJfrFile(engine);
            } else {
                processTextFile(engine);
            }

            if (!dryRun) {
                logger.info("✓ Redaction complete!");
                logger.info("Output written to: {}", outputFile.getAbsolutePath());
            } else {
                logger.info("✓ Dry run complete!");
            }

            // Show statistics if requested
            if (showStats) {
                engine.getStats().print();
            }

            return 0;

        } catch (ConfigLoader.ConfigurationException e) {
            // Configuration errors - show detailed message without stack trace
            System.err.println("\n" + "=".repeat(70));
            System.err.println("Configuration Error");
            System.err.println("=".repeat(70));
            System.err.println(e.getMessage());
            System.err.println("=".repeat(70));
            System.err.println("\nFor help, see:");
            System.err.println("  - config-template.yaml in the project root");
            System.err.println("  - https://github.com/parttimenerd/jfrredact#configuration");
            logger.debug("Configuration error details", e);
            return 1;
        } catch (IOException e) {
            // I/O errors - show message without full stack trace
            logger.error("I/O Error: {}", e.getMessage());
            if (debug) {
                logger.error("Details:", e);
            }
            return 1;
        } catch (Exception e) {
            // Unexpected errors - show full details
            logger.error("Unexpected error during redaction: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Configure logging level based on flags.
     */
    private void configureLogging() {
        ch.qos.logback.classic.Logger rootLogger =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);

        if (debug) {
            rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        } else if (verbose) {
            rootLogger.setLevel(ch.qos.logback.classic.Level.INFO);
        } else if (quiet) {
            rootLogger.setLevel(ch.qos.logback.classic.Level.WARN);
        }
        // else keep default level from logback.xml
    }

    /**
     * Load configuration from preset or config file/URL, then apply CLI options.
     */
    private RedactionConfig loadConfiguration() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        // Load base configuration from preset or custom config
        RedactionConfig config;
        if (configFile != null) {
            // Load from custom config file or URL
            config = loader.load(configFile);
        } else {
            // Load from preset
            config = loader.load(preset.getName());
        }

        // Apply CLI options
        RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
        cliOptions.setPseudonymize(pseudonymize);
        cliOptions.setRemoveEvents(removeEvents);
        cliOptions.setRedactionRegexes(redactionRegexes);
        cliOptions.setIncludeEvents(includeEvents);
        cliOptions.setExcludeEvents(excludeEvents);
        cliOptions.setIncludeCategories(includeCategories);
        cliOptions.setExcludeCategories(excludeCategories);
        cliOptions.setIncludeThreads(includeThreads);
        cliOptions.setExcludeThreads(excludeThreads);

        config.applyCliOptions(cliOptions);

        return config;
    }

    /**
     * Determine if a file is a JFR file based on extension.
     */
    private boolean isJfrFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jfr");
    }

    /**
     * Process a JFR file using JFRProcessor.
     */
    private void processJfrFile(RedactionEngine engine) throws IOException {
        logger.info("Processing JFR file...");

        try (RecordingFile recordingFile = new RecordingFile(inputFile.toPath())) {
            JFRProcessor processor = new JFRProcessor(engine, recordingFile);

            if (!dryRun) {
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    processor.process(fos);
                }
            } else {
                // In dry-run mode, process to /dev/null equivalent
                try (FileOutputStream fos = new FileOutputStream(
                    System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null")) {
                    processor.process(fos);
                }
            }
        }
    }

    /**
     * Process a text file using TextFileRedactor.
     */
    private void processTextFile(RedactionEngine engine) throws IOException {
        logger.info("Processing text file...");

        TextFileRedactor redactor = new TextFileRedactor(engine);
        redactor.redactFile(inputFile, outputFile);
    }

    private void setOutputIfNeeded() {
        if (outputFile == null) {
            String inputPath = inputFile.getAbsolutePath();
            String outputPath;

            // Find the last dot for extension
            int lastDot = inputPath.lastIndexOf('.');
            if (lastDot > 0) {
                // Insert .redacted before the extension
                outputPath = inputPath.substring(0, lastDot) + ".redacted" + inputPath.substring(lastDot);
            } else {
                // No extension, just append .redacted.jfr
                outputPath = inputPath + ".redacted.jfr";
            }

            outputFile = new File(outputPath);
        }
    }
}