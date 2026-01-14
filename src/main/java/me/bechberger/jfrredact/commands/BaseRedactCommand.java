package me.bechberger.jfrredact.commands;

import me.bechberger.jfrredact.ConfigLoader;
import me.bechberger.jfrredact.Preset;
import me.bechberger.jfrredact.config.RedactionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Base class for redaction commands with common options and utilities.
 */
public abstract class BaseRedactCommand implements Callable<Integer> {

    protected static final Logger logger = LoggerFactory.getLogger(BaseRedactCommand.class);

    @Parameters(
        index = "0",
        description = "Input file to redact",
        paramLabel = "<input-file>"
    )
    protected File inputFile;

    @Parameters(
        index = "1",
        description = "Output file with redacted data (default: auto-generated)",
        paramLabel = "<output-file>",
        arity = "0..1"
    )
    protected File outputFile;

    @Option(
        names = {"--config"},
        description = "Load configuration from a preset name (default, strict, hserr), YAML file, or URL. " +
                     "If not specified, uses the default preset. " +
                     "You can also create a config file that inherits from a preset using 'parent: <preset-name>'.",
        paramLabel = "<preset|file|url>",
        defaultValue = "default"
    )
    protected String configFile;

    @Option(
        names = {"--pseudonymize"},
        description = "Enable pseudonymization mode. When enabled, the same sensitive value " +
                     "always maps to the same pseudonym (e.g., <redacted:a1b2c3>), preserving " +
                     "relationships. Without this flag, all values are redacted to ***."
    )
    protected boolean pseudonymize;

    @Option(
        names = {"--pseudonymize-mode"},
        description = "Pseudonymization mode (requires --pseudonymize). Valid values: " +
                     "hash (default, stateless deterministic), " +
                     "counter (sequential numbers), " +
                     "realistic (plausible alternatives like alice@example.com)",
        paramLabel = "<mode>"
    )
    protected String pseudonymizeMode;

    @Option(
        names = {"--seed"},
        description = "Seed for reproducible pseudonymization (only with --pseudonymize)",
        paramLabel = "<seed>"
    )
    protected Long seed;

    @Option(
        names = {"--add-redaction-regex"},
        description = "Add a custom regular expression pattern for string redaction. " +
                     "This option can be specified multiple times to add multiple patterns.",
        paramLabel = "<pattern>"
    )
    protected List<String> redactionRegexes = new ArrayList<>();

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output (INFO level logging)"
    )
    protected boolean verbose;

    @Option(
        names = {"--debug"},
        description = "Enable debug output (DEBUG level logging)"
    )
    protected boolean debug;

    @Option(
        names = {"-q", "--quiet"},
        description = "Minimize output (only show errors and completion message)"
    )
    protected boolean quiet;

    @Option(
        names = {"--stats"},
        description = "Show statistics after redaction"
    )
    protected boolean showStats;

    /**
     * Get the logger for the specific subclass.
     */
    protected abstract Logger getLogger();

    /**
     * Get the default preset for this command.
     */
    protected abstract Preset getDefaultPreset();

    /**
     * Get the default file extension for the redacted output (e.g., ".jfr", ".log").
     */
    protected abstract String getDefaultExtension();

    /**
     * Perform the actual redaction operation.
     */
    protected abstract void performRedaction(RedactionConfig config) throws IOException;

    /**
     * Get the command name for logging.
     */
    protected abstract String getCommandName();

    @Override
    public Integer call() {
        // Note: Logging is configured early in Main.LoggingAwareExecutionStrategy
        // before this method is called, so all loggers will use the correct level

        try {

            // Prepare input/output
            prepareInputOutput();

            // Log configuration
            logConfiguration();

            // Load configuration
            RedactionConfig config = loadConfiguration();

            // Perform the redaction
            performRedaction(config);

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
            getLogger().debug("Configuration error details", e);
            return 1;
        } catch (IOException e) {
            // I/O errors - show message without full stack trace
            getLogger().error("I/O Error: {}", e.getMessage());
            if (debug) {
                getLogger().error("Details:", e);
            }
            return 1;
        } catch (Exception e) {
            // Unexpected errors - show full details
            getLogger().error("Unexpected error during redaction: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Prepare input/output files (validate input, generate output if needed).
     * Can be overridden by subclasses for special handling (e.g., stdin/stdout).
     */
    protected void prepareInputOutput() {
        setOutputIfNeeded();

        // Validate input file exists
        if (!inputFile.exists()) {
            getLogger().error("Input file not found: {}", inputFile.getAbsolutePath());
            throw new RuntimeException("Input file not found: " + inputFile.getAbsolutePath());
        }
    }

    /**
     * Log the configuration for this redaction operation.
     * Can be overridden by subclasses to add command-specific logging.
     */
    protected void logConfiguration() {
        getLogger().info("{} v{}", getCommandName(), me.bechberger.jfrredact.Version.VERSION);
        getLogger().info("================");
        getLogger().info("");
        getLogger().info("Input file:  {}", inputFile.getAbsolutePath());
        getLogger().info("Output file: {}", outputFile.getAbsolutePath());

        getLogger().info("Config: {}", configFile);
        getLogger().info("Pseudonymize: {}", pseudonymize ? "enabled" : "disabled");

        if (!redactionRegexes.isEmpty()) {
            getLogger().info("Custom regexes: {}", redactionRegexes);
        }

        getLogger().info("");
    }

    /**
     * Load configuration from preset or config file/URL, then apply CLI options.
     * Can be overridden by subclasses to add command-specific CLI options.
     */
    protected RedactionConfig loadConfiguration() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        // Load base configuration from custom config or default preset
        RedactionConfig config = loader.load(configFile);

        // Apply CLI options
        RedactionConfig.CliOptions cliOptions = createCliOptions();
        config.applyCliOptions(cliOptions);

        return config;
    }

    /**
     * Create CliOptions object with common options.
     * Can be overridden by subclasses to add command-specific options.
     */
    protected RedactionConfig.CliOptions createCliOptions() {
        RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
        cliOptions.setPseudonymize(pseudonymize);
        cliOptions.setPseudonymizeMode(pseudonymizeMode);
        cliOptions.setSeed(seed);
        cliOptions.setRedactionRegexes(redactionRegexes);
        return cliOptions;
    }

    /**
     * Generate default output filename if not provided.
     */
    protected void setOutputIfNeeded() {
        if (outputFile == null) {
            String inputPath = inputFile.getAbsolutePath();
            String outputPath;

            // Find the last dot for extension
            int lastDot = inputPath.lastIndexOf('.');
            if (lastDot > 0) {
                // Insert .redacted before the extension
                outputPath = inputPath.substring(0, lastDot) + ".redacted" + inputPath.substring(lastDot);
            } else {
                // No extension, just append .redacted with default extension
                outputPath = inputPath + ".redacted" + getDefaultExtension();
            }

            outputFile = new File(outputPath);
        }
    }
}