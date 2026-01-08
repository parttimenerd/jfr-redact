package me.bechberger.jfrredact.jfr.util;

import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfrredact.ConfigLoader;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import me.bechberger.jfr.JFRProcessor;
import me.bechberger.jfrredact.jfr.RedactionModifier;

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
        try {
            RedactionConfig config = new ConfigLoader().load("default");
            config.getGeneral().getPseudonymization().setEnabled(true);
            this.engine = new RedactionEngine(config);
            return this;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default config", e);
        }
    }

    /**
     * Process with a custom RedactionConfig.
     */
    public JFRTestProcessor withConfig(RedactionConfig config) {
        this.engine = new RedactionEngine(config);
        return this;
    }

    /**
     * Process with fast (on-the-fly) discovery enabled.
     * @param configurer Optional configurer to customize the config before processing
     */
    public JFRTestProcessor withFastDiscovery(java.util.function.Consumer<RedactionConfig> configurer) {
        try {
            RedactionConfig config = new ConfigLoader().load("default");
            config.getDiscovery().setMode(me.bechberger.jfrredact.config.DiscoveryConfig.DiscoveryMode.FAST);
            if (configurer != null) {
                configurer.accept(config);
            }
            this.engine = new RedactionEngine(config);
            return this;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default config", e);
        }
    }

    /**
     * Process with comprehensive discovery enabled.
     * @param configurer Optional configurer to customize the config before processing
     */
    public JFRTestProcessor withComprehensiveDiscovery(java.util.function.Consumer<RedactionConfig> configurer) {
        try {
            RedactionConfig config = new ConfigLoader().load("default");
            config.getDiscovery().setMode(me.bechberger.jfrredact.config.DiscoveryConfig.DiscoveryMode.TWO_PASS);
            if (configurer != null) {
                configurer.accept(config);
            }
            this.engine = new RedactionEngine(config);
            return this;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default config", e);
        }
    }

    public JFRTestProcessor outputTo(String name) {
        this.outputName = name;
        return this;
    }

    public Path process() throws IOException {
        Path outputPath = tempDir.resolve(outputName + ".jfr");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            RedactionModifier modifier = new RedactionModifier(engine, inputPath);

            // Perform any setup (e.g., discovery passes)
            modifier.beforeProcessing();

            JFRProcessor processor = new JFRProcessor(modifier, inputPath);
            var recording = processor.process(output);
            recording.close(); // Must close to flush data to output stream

            Files.write(outputPath, output.toByteArray());
        }
        return outputPath;
    }

    public JFREventVerifier processAndVerify() throws IOException {
        return new JFREventVerifier(process());
    }

    private static RedactionConfig createDefaultConfig() {
        try {
            return new ConfigLoader().load("default");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default config", e);
        }
    }

    private static RedactionEngine createDefaultEngine() {
        return new RedactionEngine(createDefaultConfig());
    }

    private static RedactionEngine createStrictEngine() throws IOException {
        RedactionConfig config = new ConfigLoader().load("strict");
        return new RedactionEngine(config);
    }
}