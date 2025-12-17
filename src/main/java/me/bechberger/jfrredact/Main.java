package me.bechberger.jfrredact;

import me.bechberger.jfrredact.commands.GenerateConfigCommand;
import me.bechberger.jfrredact.commands.GenerateSchemaCommand;
import me.bechberger.jfrredact.commands.RedactCommand;
import me.bechberger.jfrredact.commands.RedactTextCommand;
import me.bechberger.jfrredact.commands.WordsCommand;
import me.bechberger.jfrredact.commands.TestCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionStrategy;
import picocli.CommandLine.ParseResult;

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
        GenerateSchemaCommand.class,
        WordsCommand.class
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
        CommandLine cmd = new CommandLine(new Main());

        // Use custom execution strategy that configures logging before executing commands
        cmd.setExecutionStrategy(new LoggingAwareExecutionStrategy());

        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    /**
     * Custom execution strategy that configures logging early, before command execution.
     * This ensures that logging configuration from command-line flags (--debug, --verbose, --quiet)
     * is applied before any loggers are used.
     */
    private static class LoggingAwareExecutionStrategy implements IExecutionStrategy {
        @Override
        public int execute(ParseResult parseResult) {
            // Configure logging BEFORE executing the command
            configureLogging(parseResult);

            // Execute the command using the default strategy
            return new CommandLine.RunLast().execute(parseResult);
        }

        private void configureLogging(ParseResult parseResult) {
            // Find the actual command being executed (not the parent)
            ParseResult commandResult = parseResult;
            while (commandResult.hasSubcommand()) {
                commandResult = commandResult.subcommand();
            }

            // Check for logging flags in the command
            boolean debug = commandResult.hasMatchedOption("--debug");
            boolean verbose = commandResult.hasMatchedOption("--verbose") || commandResult.hasMatchedOption("-v");
            boolean quiet = commandResult.hasMatchedOption("--quiet");

            // Apply logging configuration
            LoggingConfig.configure(debug, verbose, quiet);
        }
    }
}