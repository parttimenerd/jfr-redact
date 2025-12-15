package me.bechberger.jfrredact.commands;

import me.bechberger.jfrredact.ConfigLoader;
import me.bechberger.jfrredact.Preset;
import me.bechberger.jfrredact.Version;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * Test command - test configuration by showing how specific values would be redacted.
 * Also functions as a validate command when run without test values.
 */
@Command(
    name = "test",
    aliases = {"validate"},
    description = {
        "Test configuration by showing how specific values would be redacted",
        "Also validates configuration when run without test values"
    },
    mixinStandardHelpOptions = true,
    version = Version.FULL_VERSION,
    footerHeading = "%nExamples:%n",
    footer = {
        "",
        "  Validate a configuration:",
        "    jfr-redact test --config my-config.yaml",
        "    jfr-redact validate --config my-config.yaml",
        "",
        "  Test a property redaction:",
        "    jfr-redact test --config my-config.yaml --event jdk.JavaMonitorEnter --property address --value '0x7f8a4c001000'",
        "",
        "  Test thread name filtering:",
        "    jfr-redact test --config my-config.yaml --thread 'MyThread-1'",
        "",
        "  Test string redaction:",
        "    jfr-redact test --preset strict --value 'user@example.com'",
        ""
    }
)
public class TestCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(TestCommand.class);

    @Spec
    private CommandSpec spec;

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
        names = {"--event"},
        description = "Event type to test (e.g., jdk.JavaMonitorEnter)",
        paramLabel = "<type>"
    )
    private String eventType;

    @Option(
        names = {"--property"},
        description = "Property/field name to test (e.g., address, message)",
        paramLabel = "<name>"
    )
    private String propertyName;

    @Option(
        names = {"--thread"},
        description = "Thread name to test filtering",
        paramLabel = "<name>"
    )
    private String threadName;

    @Option(
        names = {"--value"},
        description = "Value to test redaction on",
        paramLabel = "<value>"
    )
    private String value;

    @Option(
        names = {"--pseudonymize"},
        description = "Enable pseudonymization mode"
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

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        try {
            // Load configuration
            RedactionConfig config = loadConfiguration();

            // Create redaction engine
            RedactionEngine engine = new RedactionEngine(config);

            out.println("\n" + "=".repeat(70));

            // Check if this is validation mode (no test values provided)
            boolean isValidationMode = (eventType == null && threadName == null &&
                                       propertyName == null && value == null);

            if (isValidationMode) {
                out.println("Configuration Validation");
            } else {
                out.println("Configuration Test Results");
            }
            out.println("=".repeat(70));
            out.println();

            if (configFile != null) {
                out.println("Config: " + configFile);
            } else {
                out.println("Preset: " + preset.getName());
            }
            out.println("Pseudonymize: " + (pseudonymize ? "enabled" : "disabled"));
            out.println();

            if (isValidationMode) {
                // Validation mode - show configuration summary
                out.println("✓ Configuration is valid");
                out.println();
            }

            // Test event filtering
            if (eventType != null) {
                boolean shouldRemove = engine.shouldRemoveEvent(eventType);
                out.println("Event Type: " + eventType);
                out.println("  → Event will be " + (shouldRemove ? "REMOVED" : "KEPT"));
                out.println();
            }

            // Test thread filtering
            if (threadName != null) {
                boolean shouldFilter = engine.shouldRemoveThread(threadName);
                out.println("Thread Name: " + threadName);
                out.println("  → Thread events will be " + (shouldFilter ? "FILTERED OUT" : "KEPT"));
                out.println();
            }

            // Test property redaction
            if (propertyName != null && value != null) {
                String eventTypeForTest = eventType != null ? eventType : "test.Event";

                out.println("Property: " + propertyName + " (in " + eventTypeForTest + ")");
                out.println("  Value: \"" + value + "\"");

                String redacted = engine.redact(propertyName, value);
                if (!value.equals(redacted)) {
                    out.println("  → Will be REDACTED to: \"" + redacted + "\"");
                } else {
                    out.println("  → Will be KEPT as-is");
                }
                out.println();
            } else if (value != null) {
                // Test string redaction without property context
                out.println("String Value: \"" + value + "\"");
                String redacted = engine.redact("testField", value);

                if (!value.equals(redacted)) {
                    out.println("  → Will be REDACTED to: \"" + redacted + "\"");
                } else {
                    out.println("  → Will be KEPT as-is (no matching patterns)");
                }
                out.println();
            }

            // Show some config details
            out.println("Configuration Summary:");
            out.println("  Properties redaction: " + config.getProperties().isEnabled());
            out.println("  String redaction: " + config.getStrings().isEnabled());
            out.println("  Network redaction: " + config.getNetwork().isEnabled());
            out.println("  Path redaction: " + config.getPaths().isEnabled());
            out.println("  Event removal: " + config.getEvents().isRemoveEnabled());
            out.println("  Pseudonymization: " + config.getGeneral().getPseudonymization().isEnabled());

            out.println();
            out.println("=".repeat(70));

            return 0;

        } catch (ConfigLoader.ConfigurationException e) {
            err.println("\n" + "=".repeat(70));
            err.println("Configuration Error");
            err.println("=".repeat(70));
            err.println(e.getMessage());
            err.println("=".repeat(70));
            return 1;
        } catch (IOException e) {
            err.println("I/O Error: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            logger.error("Test error", e);
            return 1;
        }
    }

    /**
     * Load configuration from preset or config file/URL.
     */
    private RedactionConfig loadConfiguration() throws IOException {
        ConfigLoader loader = new ConfigLoader();

        RedactionConfig config;
        if (configFile != null) {
            config = loader.load(configFile);
        } else {
            config = loader.load(preset.getName());
        }

        // Apply pseudonymize options
        if (pseudonymize || pseudonymizeMode != null || seed != null) {
            RedactionConfig.CliOptions cliOptions = new RedactionConfig.CliOptions();
            cliOptions.setPseudonymize(pseudonymize || pseudonymizeMode != null || seed != null);
            cliOptions.setPseudonymizeMode(pseudonymizeMode);
            cliOptions.setSeed(seed);
            config.applyCliOptions(cliOptions);
        }

        return config;
    }
}