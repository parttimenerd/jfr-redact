package me.bechberger.jfrredact.commands;

import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.words.WordRedactionRule;
import me.bechberger.jfrredact.words.WordRedactor;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Applies word redaction rules to JFR events or text files
 */
@CommandLine.Command(
    name = "redact",
    description = "Apply word redaction rules to JFR events or text files",
    mixinStandardHelpOptions = true,
    footer = {
        "",
        "Rule Format (one rule per line):",
        "  - word              Redact this word (replace with ***)",
        "  + word              Keep this word (whitelist, don't redact)",
        "  - prefix*           Redact all words starting with 'prefix'",
        "  - *suffix           Redact all words ending with 'suffix'",
        "  - *contains*        Redact all words containing 'contains'",
        "  # comment           Comment line (ignored)",
        "  (empty lines)       Ignored",
        "  other lines         Ignored (no - or + prefix)",
        "",
        "Examples:",
        "",
        "  Redact using rules file:",
        "    jfr-redact words redact app.log redacted.log -r rules.txt",
        "",
        "  Redact using rules from stdin:",
        "    echo \"- secretpassword\" | jfr-redact words redact app.log redacted.log",
        "",
        "  Example rules.txt:",
        "    # Redact specific sensitive values",
        "    - secretpassword",
        "    - internalhost.corp.com",
        "    ",
        "    # Redact all words starting with 'secret'",
        "    - secret*",
        "    ",
        "    # Keep safe words (whitelist)",
        "    + localhost",
        "    + example.com"
    }
)
public class WordsRedactCommand implements Callable<Integer> {

    @CommandLine.Parameters(
        index = "0",
        description = "Input JFR file or text file to redact"
    )
    private Path inputFile;

    @CommandLine.Parameters(
        index = "1",
        description = "Output file for redacted content"
    )
    private Path outputFile;

    @CommandLine.Option(
        names = {"-r", "--rules"},
        description = "File containing redaction rules (default: stdin)"
    )
    private Path rulesFile;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        PrintWriter err = spec.commandLine().getErr();

        if (!inputFile.toFile().exists()) {
            err.println("Error: Input file does not exist: " + inputFile);
            return 1;
        }

        // Read rules
        List<WordRedactionRule> rules = readRules(err);
        if (rules.isEmpty()) {
            err.println("Warning: No redaction rules provided");
        }

        err.println("Loaded " + rules.size() + " redaction rules");

        WordRedactor redactor = new WordRedactor(rules);

        // Check if it's a JFR file or text file
        String fileName = inputFile.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jfr")) {
            redactJFR(inputFile, redactor, err);
        } else {
            redactText(inputFile, outputFile, redactor, err);
        }

        return 0;
    }

    private List<WordRedactionRule> readRules(PrintWriter err) throws IOException {
        List<WordRedactionRule> rules = new ArrayList<>();

        BufferedReader reader;
        if (rulesFile != null) {
            reader = Files.newBufferedReader(rulesFile);
        } else {
            reader = new BufferedReader(new InputStreamReader(System.in));
        }

        String line;
        int lineNum = 0;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            line = line.trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            try {
                WordRedactionRule rule = WordRedactionRule.parse(line);
                if (rule != null) {
                    rules.add(rule);
                }
                // null means the line was ignored (unknown format)
            } catch (IllegalArgumentException e) {
                err.println("Warning: Invalid rule at line " + lineNum + ": " + e.getMessage());
            }
        }

        if (rulesFile != null) {
            reader.close();
        }

        return rules;
    }

    private static void redactJFR(Path inputFile, WordRedactor redactor, PrintWriter err) throws Exception {
        err.println("Redacting JFR file: " + inputFile);
        err.println("Note: Full JFR writing is not yet fully implemented");
        err.println("This will process events and show statistics");
        err.flush();

        try (RecordingFile recording = new RecordingFile(inputFile)) {
            int eventCount = 0;
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                redactor.processEvent(event);
                eventCount++;

                if (eventCount % 10000 == 0) {
                    err.println("Processed " + eventCount + " events...");
                    err.flush();
                }
            }
            err.println("Processed " + eventCount + " events total");
        }

        err.println(redactor.getStatistics());
        err.flush();
    }

    private static void redactText(Path inputFile, Path outputFile, WordRedactor redactor, PrintWriter err) throws Exception {
        err.println("Redacting text file: " + inputFile);
        err.flush();

        int lineCount = 0;
        int redactedCount = 0;

        try (var reader = java.nio.file.Files.newBufferedReader(inputFile);
             var writer = java.nio.file.Files.newBufferedWriter(outputFile)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String redactedLine = redactor.redactText(line);
                writer.write(redactedLine);
                writer.newLine();

                if (!line.equals(redactedLine)) {
                    redactedCount++;
                }

                lineCount++;
                if (lineCount % 10000 == 0) {
                    err.println("Processed " + lineCount + " lines...");
                    err.flush();
                }
            }
        }

        err.println("Processed " + lineCount + " lines total");
        err.println("Redacted " + redactedCount + " lines");
        err.println(redactor.getStatistics());
        err.println("Wrote redacted output to: " + outputFile);
        err.flush();
    }
}