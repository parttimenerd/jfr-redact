package me.bechberger.jfrredact.jfr;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfr.JFREventModifier;
import me.bechberger.jfrredact.config.DiscoveryConfig;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.DiscoveredPatterns;
import me.bechberger.jfrredact.engine.PatternDiscoveryEngine;
import me.bechberger.jfrredact.engine.RedactionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Implementation of JFREventModifier that uses RedactionEngine and supports pattern discovery.
 * This class bridges the standalone JFRProcessor with the redaction-specific logic.
 */
public class RedactionModifier implements JFREventModifier {

    private static final Logger logger = LoggerFactory.getLogger(RedactionModifier.class);

    private final RedactionEngine redactionEngine;
    private final RedactionConfig config;
    private final Path inputPath;
    private final DiscoveryConfig.DiscoveryMode discoveryMode;

    /**
     * Create a redaction modifier with the given configuration.
     *
     * @param redactionEngine The redaction engine to use
     * @param inputPath Path to the input JFR file (needed for discovery passes)
     */
    public RedactionModifier(RedactionEngine redactionEngine, Path inputPath) {
        this.redactionEngine = redactionEngine;
        this.config = redactionEngine.getConfig();
        this.inputPath = inputPath;
        this.discoveryMode = config != null ? config.getDiscovery().getMode() : DiscoveryConfig.DiscoveryMode.NONE;
    }

    /**
     * Called before processing begins. Performs pattern discovery if needed.
     */
    public void beforeProcessing() throws IOException {
        if (discoveryMode == DiscoveryConfig.DiscoveryMode.TWO_PASS) {
            performTwoPassDiscovery();
        }
        // FAST mode discovery happens during analyzeEvent() calls
        // NONE mode doesn't need discovery
    }

    /**
     * Perform two-pass discovery: read the file once to discover patterns,
     * then the main processing pass will use the discovered patterns.
     */
    private void performTwoPassDiscovery() throws IOException {
        logger.info("Discovery Pass: Analyzing events for sensitive patterns...");

        PatternDiscoveryEngine discoveryEngine = new PatternDiscoveryEngine(
            config.getDiscovery(),
            config.getStrings()
        );

        int discoveryEventCount = 0;
        try (RecordingFile discoveryInput = new RecordingFile(inputPath)) {
            while (discoveryInput.hasMoreEvents()) {
                RecordedEvent event = discoveryInput.readEvent();

                // Only discover from events that won't be removed
                if (!redactionEngine.shouldRemoveEvent(event)) {
                    discoveryEngine.analyzeEvent(event);
                    discoveryEventCount++;

                    if (discoveryEventCount % 10000 == 0) {
                        logger.info("Discovery: analyzed {} events", discoveryEventCount);
                    }
                }
            }
        }

        DiscoveredPatterns patterns = discoveryEngine.getDiscoveredPatterns();
        logger.info("Discovery Pass complete: analyzed {} events", discoveryEventCount);
        logger.info(discoveryEngine.getStatistics());

        // Set discovered patterns in the redaction engine
        redactionEngine.setDiscoveredPatterns(patterns);
    }

    @Override
    public boolean shouldRemoveEvent(RecordedEvent event) {
        return redactionEngine.shouldRemoveEvent(event);
    }

    @Override
    public String process(String fieldName, String value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public int process(String fieldName, int value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public long process(String fieldName, long value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public boolean process(String fieldName, boolean value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public byte process(String fieldName, byte value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public char process(String fieldName, char value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public short process(String fieldName, short value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public float process(String fieldName, float value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public double process(String fieldName, double value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public String[] process(String fieldName, String[] value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public int[] process(String fieldName, int[] value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public long[] process(String fieldName, long[] value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public byte[] process(String fieldName, byte[] value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public short[] process(String fieldName, short[] value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public float[] process(String fieldName, float[] value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public double[] process(String fieldName, double[] value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public boolean[] process(String fieldName, boolean[] value) {
        return redactionEngine.redact(fieldName, value);
    }

    @Override
    public char[] process(String fieldName, char[] value) {
        return redactionEngine.redact(fieldName, value);
    }
}