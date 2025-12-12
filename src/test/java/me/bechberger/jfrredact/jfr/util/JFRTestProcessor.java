package me.bechberger.jfrredact.jfr.util;

import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import me.bechberger.jfrredact.jfr.JFRProcessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper for processing JFR recordings with a fluent API.
 * Supports roundtrip testing: record -> process -> read back.
 */
public class JFRTestProcessor {
    private final Path tempDir;
    private Path inputPath;
    private RedactionEngine engine = createDefaultEngine();
    private String outputName = "output";

    public JFRTestProcessor(Path tempDir) {
        this.tempDir = tempDir;
    }

    public JFRTestProcessor from(Path inputPath) {
        this.inputPath = inputPath;
        return this;
    }

    public JFRTestProcessor withEngine(RedactionEngine engine) {
        this.engine = engine;
        return this;
    }

    public JFRTestProcessor withDefaultEngine() {
        this.engine = createDefaultEngine();
        return this;
    }

    public JFRTestProcessor withStrictEngine() throws IOException {
        this.engine = createStrictEngine();
        return this;
    }

    public JFRTestProcessor withNoRedaction() {
        this.engine = RedactionEngine.NONE;
        return this;
    }

    public JFRTestProcessor withPseudonymization() {
        RedactionConfig config = new RedactionConfig();
        config.getGeneral().getPseudonymization().setEnabled(true);
        this.engine = new RedactionEngine(config);
        return this;
    }

    /**
     * Process with a custom RedactionConfig.
     */
    public JFRTestProcessor withConfig(RedactionConfig config) {
        this.engine = new RedactionEngine(config);
        return this;
    }

    public JFRTestProcessor outputTo(String name) {
        this.outputName = name;
        return this;
    }

    public Path process() throws IOException {
        Path outputPath = tempDir.resolve(outputName + ".jfr");
        try (RecordingFile input = new RecordingFile(inputPath);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            JFRProcessor processor = new JFRProcessor(engine, input);
            var recording = processor.process(output);
            recording.close(); // Must close to flush data to output stream
            Files.write(outputPath, output.toByteArray());
        }
        return outputPath;
    }

    public JFREventVerifier processAndVerify() throws IOException {
        return new JFREventVerifier(process());
    }

    private static RedactionEngine createDefaultEngine() {
        return new RedactionEngine(new RedactionConfig());
    }

    private static RedactionEngine createStrictEngine() throws IOException {
        RedactionConfig config = new me.bechberger.jfrredact.ConfigLoader().load("strict");
        return new RedactionEngine(config);
    }
}