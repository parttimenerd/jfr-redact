package me.bechberger.jfrredact.commands;

import me.bechberger.jfrredact.Preset;
import me.bechberger.jfrredact.Version;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import me.bechberger.jfrredact.text.TextFileRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Redact-text command - redacts sensitive information from any text file.
 */
@Command(
    name = "redact-text",
    description = "Redact sensitive information from text files, especially hserr files, but also logs, configuration files, etc.",
    mixinStandardHelpOptions = true,
    version = Version.FULL_VERSION,
    footerHeading = "%nExamples:%n",
    footer = {
        "",
        "  Redact a log file with default config (hserr preset):",
        "    jfr-redact redact-text application.log",
        "    (creates application.redacted.log)",
        "",
        "  Redact Java crash reports (uses hserr preset by default):",
        "    jfr-redact redact-text hs_err_pid12345.log",
        "",
        "  Read from stdin, write to stdout:",
        "    cat hs_err_pid12345.log | jfr-redact redact-text - -",
        "",
        "  Use strict preset:",
        "    jfr-redact redact-text app.log --config strict",
        "",
        "  Custom config with pseudonymization:",
        "    jfr-redact redact-text app.log --config my-config.yaml --pseudonymize",
        "",
        "  Add custom redaction pattern:",
        "    jfr-redact redact-text app.log --add-redaction-regex '\\b[A-Z]{3}-\\d{6}\\b'",
        ""
    }
)
public class RedactTextCommand extends BaseRedactCommand {

    private static final Logger commandLogger = LoggerFactory.getLogger(RedactTextCommand.class);

    @Override
    protected Logger getLogger() {
        return commandLogger;
    }

    @Override
    protected Preset getDefaultPreset() {
        return Preset.HSERR;
    }

    @Override
    protected String getDefaultExtension() {
        return "";
    }

    @Override
    protected String getCommandName() {
        return "JFR-Redact Text Mode";
    }

    @Override
    protected void prepareInputOutput() {
        // If input is "-" treat as stdin; if output is "-" treat as stdout
        boolean useStdio = "-".equals(inputFile.getName()) || (outputFile != null && "-".equals(outputFile.getName()));

        if (!useStdio) {
            super.prepareInputOutput();
        }
    }

    @Override
    protected void logConfiguration() {
        // If input is "-" treat as stdin; if output is "-" treat as stdout
        boolean useStdio = "-".equals(inputFile.getName()) || (outputFile != null && "-".equals(outputFile.getName()));

        getLogger().info("{} v{}", getCommandName(), Version.VERSION);
        getLogger().info("================");
        getLogger().info("");
        getLogger().info("Input file:  {}", "-".equals(inputFile.getName()) ? "<stdin>" : inputFile.getAbsolutePath());
        getLogger().info("Output file: {}", (outputFile == null ? "<auto>" : ("-".equals(outputFile.getName()) ? "<stdout>" : outputFile.getAbsolutePath())));

        if (configFile != null) {
            getLogger().info("Config: {}", configFile);
        } else {
            getLogger().info("Config: {} preset (default)", getDefaultPreset().getName());
        }

        getLogger().info("Pseudonymize: {}", pseudonymize ? "enabled" : "disabled");

        if (!redactionRegexes.isEmpty()) {
            getLogger().info("Custom regexes: {}", redactionRegexes);
        }

        getLogger().info("");
    }

    @Override
    protected void performRedaction(RedactionConfig config) throws IOException {
        // Create redaction engine
        RedactionEngine engine = new RedactionEngine(config);

        // Process the text file
        TextFileRedactor redactor = new TextFileRedactor(engine);

        // If input is "-" treat as stdin; if output is "-" treat as stdout
        boolean useStdio = "-".equals(inputFile.getName()) || (outputFile != null && "-".equals(outputFile.getName()));

        if (useStdio) {
            // Use stdin/stdout
            InputStream in = System.in;
            OutputStream out = System.out;

            redactor.redactStream(in, out);

        } else {
            redactor.redactFile(inputFile, outputFile);

            getLogger().info("✓ Redaction complete!");
            getLogger().info("Output written to: {}", outputFile.getAbsolutePath());
        }

        if (showStats) {
            engine.getStats().print();
        }
    }
}