package me.bechberger.jfrredact.commands;

import me.bechberger.jfrredact.ConfigLoader;
import me.bechberger.jfrredact.Preset;
import me.bechberger.jfrredact.Version;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Config generation command - generates configuration templates.
 */
@Command(
    name = "generate-config",
    description = "Generate a configuration template for JFR redaction",
    mixinStandardHelpOptions = true,
    version = Version.FULL_VERSION,
    footerHeading = "%nExamples:%n",
    footer = {
        "",
        "  Generate default template to stdout:",
        "    jfr-redact generate-config",
        "",
        "  Generate template to file:",
        "    jfr-redact generate-config -o my-config.yaml",
        "",
        "  Generate from preset:",
        "    jfr-redact generate-config --preset strict -o my-config.yaml",
        "",
        "  Generate minimal config:",
        "    jfr-redact generate-config --minimal -o minimal-config.yaml",
        ""
    }
)
public class GenerateConfigCommand implements Callable<Integer> {

    @Spec
    private CommandLine.Model.CommandSpec spec;

    @Parameters(
        index = "0",
        description = "Output file for the configuration (default: stdout)",
        paramLabel = "<output.yaml>",
        arity = "0..1"
    )
    private String outputFile;

    @Option(
        names = {"-o", "--output"},
        description = "Output file for the configuration",
        paramLabel = "<file>"
    )
    private String outputFileOption;

    @Option(
        names = {"--preset"},
        description = "Base the configuration on a preset. Valid values: ${COMPLETION-CANDIDATES}",
        paramLabel = "<preset>"
    )
    private Preset preset;

    @Option(
        names = {"--minimal"},
        description = "Generate minimal configuration template"
    )
    private boolean minimal;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        try {
            String config;

            if (preset != null) {
                // Generate from preset
                config = generateFromPreset(err);
            } else if (minimal) {
                // Generate minimal config
                config = generateMinimal();
            } else {
                // Generate full template
                config = generateTemplate();
            }

            // Determine output location
            String output = outputFileOption != null ? outputFileOption : outputFile;

            if (output != null) {
                // Write to file
                Path path = Paths.get(output);
                Files.writeString(path, config);
                err.println("Configuration written to: " + path.toAbsolutePath());
            } else {
                // Write to stdout
                out.println(config);
            }

            return 0;

        } catch (IOException e) {
            err.println("Error generating configuration: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private String generateFromPreset(PrintWriter err) throws IOException {
        ConfigLoader loader = new ConfigLoader();
        String presetYaml = loader.loadRawYaml(preset.getName());

        err.println("Generated configuration based on preset: " + preset.getName());
        err.println("You can modify this configuration to suit your needs.");

        return presetYaml;
    }

    /**
     * Generate the full configuration template from config-template.yaml resource.
     */
    private String generateTemplate() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/config-template.yaml")) {
            if (is == null) {
                throw new IOException("config-template.yaml not found in JAR resources");
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String generateMinimal() {
        return """
                # Minimal JFR Redaction Configuration
                # Uncomment and customize sections as needed
                
                general:
                  redaction_text: "***"
                  pseudonymization:
                    enabled: false
                
                # Uncomment to enable property redaction
                #properties:
                #  enabled: true
                #  patterns:
                #    - password
                #    - secret
                #    - token
                
                # Uncomment to enable string pattern redaction
                #strings:
                #  enabled: true
                #  patterns:
                #    emails:
                #      enabled: true
                #    ip_addresses:
                #      enabled: true
                
                # Uncomment to remove specific events
                #events:
                #  remove_enabled: true
                #  removed_types:
                #    - jdk.OSInformation
                """;
    }
}