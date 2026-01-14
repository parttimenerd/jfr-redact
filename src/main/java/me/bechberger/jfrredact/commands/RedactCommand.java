package me.bechberger.jfrredact.commands;

import me.bechberger.jfrredact.Preset;
import me.bechberger.jfrredact.Version;
import me.bechberger.jfrredact.config.DiscoveryConfig;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import me.bechberger.jfr.JFRProcessor;
import me.bechberger.jfrredact.jfr.RedactionModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        "  Simple redaction with default config:",
        "    jfr-redact redact recording.jfr",
        "    (creates recording.redacted.jfr using default preset)",
        "",
        "  Specify output file:",
        "    jfr-redact redact recording.jfr output.jfr",
        "",
        "  Use strict preset:",
        "    jfr-redact redact recording.jfr --config strict",
        "",
        "  Use strict preset with pseudonymization:",
        "    jfr-redact redact recording.jfr --config strict --pseudonymize",
        "",
        "  Custom config file with additional event removal:",
        "    jfr-redact redact recording.jfr --config my-config.yaml --remove-event jdk.CustomEvent",
        "",
        "  Add custom redaction pattern:",
        "    jfr-redact redact recording.jfr --add-redaction-regex '\\b[A-Z]{3}-\\d{6}\\b'",
        ""
    }
)
public class RedactCommand extends BaseRedactCommand {

    private static final Logger commandLogger = LoggerFactory.getLogger(RedactCommand.class);

    @Option(
        names = {"--discovery-mode"},
        description = "Pattern discovery mode. Valid values: " +
                     "none (no discovery, single-pass), " +
                     "fast (on-the-fly discovery), " +
                     "default (two-pass, reads file twice for complete discovery). " +
                     "Default: default (two-pass). " +
                     "Note: Per-pattern discovery is configured in the config file via enable_discovery.",
        paramLabel = "<mode>"
    )
    private String discoveryMode;

    @Option(
        names = {"--discover-usernames"},
        description = "DEPRECATED: Discovery is now controlled per-pattern in the config file. " +
                     "Use strings.patterns.home_directories.enable_discovery instead.",
        hidden = true
    )
    private Boolean discoverUsernames;

    @Option(
        names = {"--discover-hostnames"},
        description = "DEPRECATED: Discovery is now controlled per-pattern in the config file. " +
                     "Use strings.patterns.ssh_hosts.enable_discovery or strings.patterns.hostnames.enable_discovery instead.",
        hidden = true
    )
    private Boolean discoverHostnames;

    @Option(
        names = {"--min-occurrences"},
        description = "Minimum occurrences required to redact a discovered value (prevents false positives, default: 1)",
        paramLabel = "<count>"
    )
    private Integer minOccurrences;

    @Option(
        names = {"--remove-event"},
        description = "Remove an additional event type from the output. " +
                     "This option can be specified multiple times to remove multiple event types.",
        paramLabel = "<type>"
    )
    private List<String> removeEvents = new ArrayList<>();


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
        names = {"--dry-run"},
        description = "Process the file without writing output, useful for testing configuration with --stats"
    )
    private boolean dryRun;

    @Option(
        names = {"-i", "--interactive"},
        description = "Enable interactive mode. Prompts for decisions about discovered usernames, hostnames, " +
                     "folders, and custom patterns. Decisions are saved to a file for future automatic use. " +
                     "Note: Ignores the 'ignore' list from config in interactive mode."
    )
    private boolean interactive;

    @Option(
        names = {"--decisions-file"},
        description = "Path to file for storing interactive decisions (default: <input>.decisions.yaml)",
        paramLabel = "<file>"
    )
    private File decisionsFile;

    @Override
    protected Logger getLogger() {
        return commandLogger;
    }

    @Override
    protected Preset getDefaultPreset() {
        return Preset.DEFAULT;
    }

    @Override
    protected String getDefaultExtension() {
        return ".jfr";
    }

    @Override
    protected String getCommandName() {
        return "JFR-Redact";
    }

    @Override
    protected void setOutputIfNeeded() {
        super.setOutputIfNeeded();

        // Set default decisions file if interactive mode is enabled and not specified
        if (interactive && decisionsFile == null) {
            String inputPath = inputFile.getAbsolutePath();
            String decisionsPath;

            // Find the last dot for extension
            int lastDot = inputPath.lastIndexOf('.');
            if (lastDot > 0) {
                // Insert .decisions before the extension
                decisionsPath = inputPath.substring(0, lastDot) + ".decisions.yaml";
            } else {
                // No extension, just append .decisions.yaml
                decisionsPath = inputPath + ".decisions.yaml";
            }

            decisionsFile = new File(decisionsPath);
        }
    }

    @Override
    protected void logConfiguration() {
        getLogger().info("{} v{}", getCommandName(), Version.VERSION);
        getLogger().info("================");
        getLogger().info("");
        getLogger().info("Input file:  {}", inputFile.getAbsolutePath());
        if (!dryRun) {
            getLogger().info("Output file: {}", outputFile.getAbsolutePath());
        } else {
            getLogger().info("Mode:        DRY RUN (no output will be written)");
        }

        if (configFile != null) {
            getLogger().info("Config: {}", configFile);
        }

        getLogger().info("Pseudonymize: {}", pseudonymize ? "enabled" : "disabled");

        if (!removeEvents.isEmpty()) {
            getLogger().info("Remove events: {}", removeEvents);
        }

        if (!redactionRegexes.isEmpty()) {
            getLogger().info("Custom regexes: {}", redactionRegexes);
        }

        getLogger().info("");
    }

    @Override
    protected RedactionConfig.CliOptions createCliOptions() {
        RedactionConfig.CliOptions cliOptions = super.createCliOptions();

        // Add JFR-specific options
        cliOptions.setRemoveEvents(removeEvents);
        cliOptions.setIncludeEvents(includeEvents);
        cliOptions.setExcludeEvents(excludeEvents);
        cliOptions.setIncludeCategories(includeCategories);
        cliOptions.setExcludeCategories(excludeCategories);
        cliOptions.setIncludeThreads(includeThreads);
        cliOptions.setExcludeThreads(excludeThreads);

        // Discovery options
        if (discoveryMode != null) {
            try {
                cliOptions.setDiscoveryMode(
                    DiscoveryConfig.DiscoveryMode.valueOf(discoveryMode.toUpperCase())
                );
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: Invalid discovery mode '" + discoveryMode +
                                 "'. Valid values: none, fast, default");
            }
        }

        return cliOptions;
    }

    @Override
    protected void performRedaction(RedactionConfig config) throws IOException {
        // Create redaction engine
        RedactionEngine engine = new RedactionEngine(config);

        // Determine file type and process accordingly
        if (isJfrFile(inputFile)) {
            processJfrFile(engine, config);
        } else {
            getLogger().error("Unsupported input file type: {}", inputFile.getName());
            throw new IOException("Unsupported input file type: " + inputFile.getName());
        }

        if (!dryRun) {
            getLogger().info("✓ Redaction complete!");
            getLogger().info("Output written to: {}", outputFile.getAbsolutePath());
        } else {
            getLogger().info("✓ Dry run complete!");
        }

        // Show statistics if requested
        if (showStats) {
            engine.getStats().print();
        }
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
    private void processJfrFile(RedactionEngine engine, RedactionConfig config) throws IOException {
        getLogger().info("Processing JFR file...");

        RedactionModifier modifier =
            new RedactionModifier(engine, inputFile.toPath());

        // Perform any setup (e.g., discovery passes)
        modifier.beforeProcessing();

        JFRProcessor processor = new JFRProcessor(modifier, inputFile.toPath());

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