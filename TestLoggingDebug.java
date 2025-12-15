import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import me.bechberger.jfrredact.Main;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestLoggingDebug {

    @Name("test.SimpleEvent")
    static class SimpleEvent extends Event {
        String message;
    }

    public static void main(String[] args) throws Exception {
        // Create a temp directory
        Path tempDir = Files.createTempDirectory("jfr-test");

        // Create a JFR recording
        Path inputFile = tempDir.resolve("test-input.jfr");

        try (Recording recording = new Recording()) {
            recording.enable(SimpleEvent.class);
            recording.start();

            SimpleEvent event = new SimpleEvent();
            event.message = "Test";
            event.commit();

            recording.stop();
            recording.dump(inputFile);
        }

        System.out.println("Created JFR file: " + inputFile);
        System.out.println("File exists: " + Files.exists(inputFile));
        System.out.println("File size: " + Files.size(inputFile));

        // Try to run the command
        Path outputFile = tempDir.resolve("output.jfr");

        String[] cmdArgs = {
            inputFile.toString(),
            outputFile.toString()
        };

        System.out.println("\nRunning command with args:");
        for (String arg : cmdArgs) {
            System.out.println("  " + arg);
        }

        // Capture output
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;

        try {
            System.setOut(new PrintStream(baos));
            System.setErr(new PrintStream(baos));

            Main app = new Main();
            CommandLine cmd = new CommandLine(app);
            int exitCode = cmd.execute(cmdArgs);

            System.setOut(oldOut);
            System.setErr(oldErr);

            System.out.println("\nExit code: " + exitCode);
            System.out.println("\nCommand output:");
            System.out.println(baos.toString());

        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }

        // Cleanup
        Files.deleteIfExists(inputFile);
        Files.deleteIfExists(outputFile);
        Files.deleteIfExists(tempDir);
    }
}