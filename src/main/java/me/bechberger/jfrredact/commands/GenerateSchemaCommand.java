package me.bechberger.jfrredact.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import me.bechberger.jfrredact.Version;
import me.bechberger.jfrredact.config.RedactionConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Generate JSON Schema command - generates JSON schema for the YAML configuration files.
 */
@Command(
    name = "generate-schema",
    description = "Generate JSON Schema for the YAML configuration files",
    mixinStandardHelpOptions = true,
    version = Version.FULL_VERSION,
    footerHeading = "%nExamples:%n",
    footer = {
        "",
        "  Generate schema to stdout:",
        "    jfr-redact generate-schema",
        "",
        "  Generate schema to a file:",
        "    jfr-redact generate-schema config-schema.json",
        ""
    }
)
public class GenerateSchemaCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Parameters(
        index = "0",
        description = "Output file for the JSON schema (default: stdout)",
        paramLabel = "<output.json>",
        arity = "0..1"
    )
    private String outputFile;

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
        try {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            JsonNode schema = generateSchema();
            String schemaJson = schema.toPrettyString();

            // Optionally write to file if path is provided
            if (outputFile != null) {
                Path outputPath = Paths.get(outputFile).toAbsolutePath();
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, schemaJson);
                err.println("\n✓ Schema written to: " + outputPath.toAbsolutePath());
            } else {
                out.println(schemaJson);
            }

            return 0;
        } catch (IOException e) {
            PrintWriter err = spec.commandLine().getErr();
            err.println("Error generating schema: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Generate JSON Schema for RedactionConfig
     */
    public static JsonNode generateSchema() {
        JacksonModule module = new JacksonModule();
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON)
                .with(module)
                .with(com.github.victools.jsonschema.generator.Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                .with(com.github.victools.jsonschema.generator.Option.VALUES_FROM_CONSTANT_FIELDS)
                .with(com.github.victools.jsonschema.generator.Option.FLATTENED_ENUMS_FROM_TOSTRING);

        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator =
                new SchemaGenerator(config);

        return generator.generateSchema(RedactionConfig.class);
    }
}