package me.bechberger.jfrredact.commands;

import me.bechberger.jfrredact.ConfigLoader;
import me.bechberger.jfrredact.Preset;
import me.bechberger.jfrredact.Version;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import me.bechberger.jfrredact.text.TextFileRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Redact-text command - redacts sensitive information from any text file.
 */
@Command(
    name = "redact-text",
    description = "Redact sensitive information from text files (logs, configuration files, etc.)",
    mixinStandardHelpOptions = true,
    version = Version.FULL_VERSION,
    footerHeading = "%nExamples:%n",
    footer = {
        "",
        "  Redact a log file with default preset:",
        "    jfr-redact redact-text application.log",
        "    (creates application.redacted.log)",
        "",
        "  Use hserr preset for Java crash reports:",
        "    jfr-redact redact-text hs_err_pid12345.log --preset hserr",
        "",
        "  Read from stdin, write to stdout:",
        "    cat hs_err_pid12345.log | jfr-redact redact-text - -",
        "",
        "  Use strict preset:",
        "    jfr-redact redact-text application.log --preset strict",
        "",
        "  Custom config with pseudonymization:",
        "    jfr-redact redact-text app.log --config my-config.yaml --pseudonymize",
        "",
        "  Add custom redaction pattern:",
        "    jfr-redact redact-text app.log --add-redaction-regex '\\b[A-Z]{3}-\\d{6}\\b'",
        ""
    }
)
public class RedactTextCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(RedactTextCommand.class);

    @Parameters(
        index = "0",
        description = "Input text file to redact",
        paramLabel = "<input-file>"
    )
    private File inputFile;

    @Parameters(
        index = "1",
        description = "Output file with redacted data (default: <input>.redacted.<ext>)",
        paramLabel = "<output-file>",
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
                     "relationships across lines. Without this flag, all values are redacted to ***."
    )
    private boolean pseudonymize;

    @Option(
        names = {"--pseudonymize-mode"},
        description = "Pseudonymization mode (requires --pseudonymize). Valid values: " +
                     "hash (default, stateless deterministic), " +
                     "counter (sequential numbers), " +
                     "realistic (plausible alternatives like alice@example.com)",
        paramLabel = "<mode>"
    )
    private String pseudonymizeMode;

    @Option(
        names = {"--seed"},
        description = "Seed for reproducible pseudonymization (only with --pseudonymize)",
        paramLabel = "<seed>"
    )
    private Long seed;

    @Option(
        names = {"--add-redaction-regex"},
        description = "Add a custom regular expression pattern for string redaction. " +
                     "This option can be specified multiple times to add multiple patterns.",
        paramLabel = "<pattern>"
    )
    private List<String> redactionRegexes = new ArrayList<>();

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
        description = "Show statistics after redaction"
    )
    private boolean showStats;

    @Override
    public Integer call() {
        // Note: Logging is configured early in Main.LoggingAwareExecutionStrategy
        // before this method is called, so all loggers will use the correct level

        // If input is "-" treat as stdin; if output is "-" treat as stdout
        boolean useStdio = "-".equals(inputFile.getName()) || (outputFile != null && "-".equals(outputFile.getName()));

        // Generate default output filename if not provided and not using stdout
        if (!useStdio) {
            setOutputIfNeeded();
        }

        // Validate input file exists (skip when stdin is used)
        if (!"-".equals(inputFile.getName()) && !inputFile.exists()) {
            logger.error("Input file not found: {}", inputFile.getAbsolutePath());
            return 1;
        }

        logger.info("JFR-Redact Text Mode v{}", Version.VERSION);
        logger.info("================");
        logger.info("");
        logger.info("Input file:  {}", "-".equals(inputFile.getName()) ? "<stdin>" : inputFile.getAbsolutePath());
        logger.info("Output file: {}", (outputFile == null ? "<auto>" : ("-".equals(outputFile.getName()) ? "<stdout>" : outputFile.getAbsolutePath())));
        logger.info("Preset:      {}", preset.getName());

        if (configFile != null) {
            logger.info("Config: {}", configFile);
        }

        logger.info("Pseudonymize: {}", pseudonymize ? "enabled" : "disabled");

        if (!redactionRegexes.isEmpty()) {
            logger.info("Custom regexes: {}", redactionRegexes);
        }

        logger.info("");

        try {
            // Load configuration
            RedactionConfig config = loadConfiguration();

            // Create redaction engine
            RedactionEngine engine = new RedactionEngine(config);

            // Process the text file
            TextFileRedactor redactor = new TextFileRedactor(engine);

            if (useStdio) {
                // Use stdin/stdout
                InputStream in = System.in;
                OutputStream out = System.out;

                redactor.redactStream(in, out);

            } else {
                redactor.redactFile(inputFile, outputFile);

                logger.info("✓ Redaction complete!");
                logger.info("Output written to: {}", outputFile.getAbsolutePath());

                // Show statistics if requested

            }
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
        cliOptions.setPseudonymizeMode(pseudonymizeMode);
        cliOptions.setSeed(seed);
        cliOptions.setRedactionRegexes(redactionRegexes);

        config.applyCliOptions(cliOptions);

        return config;
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
                // No extension, just append .redacted
                outputPath = inputPath + ".redacted";
            }

            outputFile = new File(outputPath);
        }
    }
}