package me.bechberger.jfrredact;

import me.bechberger.jfrredact.commands.GenerateConfigCommand;
import me.bechberger.jfrredact.commands.GenerateSchemaCommand;
import me.bechberger.jfrredact.commands.RedactCommand;
import me.bechberger.jfrredact.commands.RedactTextCommand;
import me.bechberger.jfrredact.commands.TestCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main CLI entry point with subcommands.
 */
@Command(
    name = "jfr-redact",
    version = Version.FULL_VERSION,
    description = "Redact sensitive information from Java Flight Recorder (JFR) recordings",
    mixinStandardHelpOptions = true,
    subcommands = {
        RedactCommand.class,
        RedactTextCommand.class,
        GenerateConfigCommand.class,
        TestCommand.class,
        GenerateSchemaCommand.class
    },
    commandListHeading = "%nCommands:%n",
    footerHeading = "%nExamples:%n",
    footer = {
        "",
        "  Redact a JFR file:",
        "    jfr-redact redact recording.jfr",
        "",
        "  Redact a text file:",
        "    jfr-redact redact-text application.log",
        "",
        "  Generate a configuration template:",
        "    jfr-redact generate-config -o my-config.yaml",
        "",
        "  Validate a configuration:",
        "    jfr-redact validate --config my-config.yaml",
        "",
        "  Show redaction statistics:",
        "    jfr-redact redact recording.jfr --stats",
        "",
        "For more information, see: https://github.com/parttimenerd/jfrredact"
    }
)
public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}