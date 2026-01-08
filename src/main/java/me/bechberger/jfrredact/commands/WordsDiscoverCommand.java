package me.bechberger.jfrredact.commands;

import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.words.WordDiscovery;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Discovers distinct words in JFR events or text files
 */
@CommandLine.Command(
    name = "discover",
    description = "Discover all distinct strings in JFR events or text files",
    mixinStandardHelpOptions = true,
    footer = {
        "",
        "Examples:",
        "",
        "  Discover words from JFR file and save to file:",
        "    jfr-redact words discover recording.jfr -o words.txt",
        "",
        "  Discover words from text file:",
        "    jfr-redact words discover application.log -o words.txt",
        "",
        "  Include method and class names (normally ignored):",
        "    jfr-redact words discover recording.jfr --ignore-methods=false --ignore-classes=false",
        "",
        "  Ignore specific event types:",
        "    jfr-redact words discover recording.jfr --ignore-events=jdk.GarbageCollection,jdk.ThreadSleep"
    }
)
public class WordsDiscoverCommand implements Callable<Integer> {

    @CommandLine.Parameters(
        index = "0",
        description = "Input JFR file or text file to analyze"
    )
    private Path inputFile;

    @CommandLine.Option(
        names = {"-o", "--output"},
        description = "Output file for discovered words (default: stdout)"
    )
    private Path outputFile;

    @CommandLine.Option(
        names = {"--ignore-methods"},
        description = "Ignore method names (default: true)",
        defaultValue = "true"
    )
    private boolean ignoreMethods;

    @CommandLine.Option(
        names = {"--ignore-classes"},
        description = "Ignore class names (default: true)",
        defaultValue = "true"
    )
    private boolean ignoreClasses;

    @CommandLine.Option(
        names = {"--ignore-packages"},
        description = "Ignore package names (default: true)",
        defaultValue = "true"
    )
    private boolean ignorePackages;

    @CommandLine.Option(
        names = {"--ignore-modules"},
        description = "Ignore module names (default: true)",
        defaultValue = "true"
    )
    private boolean ignoreModules;

    @CommandLine.Option(
        names = {"--ignore-events"},
        description = "Event types to ignore (comma-separated)",
        split = ","
    )
    private List<String> ignoreEventTypes = List.of();

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (!inputFile.toFile().exists()) {
            err.println("Error: Input file does not exist: " + inputFile);
            return 1;
        }

        WordDiscovery discovery = new WordDiscovery(
            ignoreMethods, ignoreClasses, ignorePackages, ignoreModules
        );

        if (!ignoreEventTypes.isEmpty()) {
            discovery.addIgnoredEventTypes(ignoreEventTypes);
        }

        // Check if it's a JFR file or text file
        String fileName = inputFile.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jfr")) {
            discoverFromJFR(discovery, out, err);
        } else {
            discoverFromText(inputFile, discovery, out, err);
        }

        // Write output
        writeOutput(discovery, outputFile, out, err);

        return 0;
    }

    private void discoverFromJFR(WordDiscovery discovery, PrintWriter out, PrintWriter err) throws Exception {

        try (RecordingFile recording = new RecordingFile(inputFile)) {
            int eventCount = 0;
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                discovery.analyzeEvent(event);
                eventCount++;
            }
        }
    }

    private static void discoverFromText(Path inputFile, WordDiscovery discovery, PrintWriter out, PrintWriter err) throws Exception {
        try (var reader = Files.newBufferedReader(inputFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                discovery.analyzeText(line);
            }
        }
    }

    private static void writeOutput(WordDiscovery discovery, Path outputFile, PrintWriter out, PrintWriter err) throws Exception {
        if (outputFile != null) {
            try (PrintWriter fileOut = new PrintWriter(outputFile.toFile())) {
                for (String word : discovery.getDiscoveredWords()) {
                    fileOut.println(word);
                }
            }
            err.println();
            err.println("Successfully wrote " + discovery.getDiscoveredWords().size() + " words to:");
            err.println("  " + outputFile.toAbsolutePath());
            err.flush();
        } else {
            for (String word : discovery.getDiscoveredWords()) {
                out.println(word);
            }
            out.flush();
        }
    }
}