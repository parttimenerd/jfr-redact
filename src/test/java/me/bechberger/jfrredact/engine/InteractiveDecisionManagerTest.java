package me.bechberger.jfrredact.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InteractiveDecisionManager
 */
class InteractiveDecisionManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testKeepDecision() throws IOException {
        Path decisionsFile = tempDir.resolve("test.decisions.yaml");

        // Simulate user input: "k" (keep)
        String input = "k\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));
        StringWriter outputWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(outputWriter);

        InteractiveDecisionManager manager = new InteractiveDecisionManager(
            decisionsFile, true, reader, writer
        );

        DiscoveredPatterns.DiscoveredValue value = new DiscoveredPatterns.DiscoveredValue(
            "testuser",
            DiscoveredPatterns.PatternType.USERNAME
        );

        InteractiveDecisionManager.Decision decision = manager.getDecision(value);

        assertEquals(InteractiveDecisionManager.DecisionAction.KEEP, decision.getAction());
        assertNull(decision.getReplacement());
    }

    @Test
    void testRedactDecision() throws IOException {
        Path decisionsFile = tempDir.resolve("test.decisions.yaml");

        // Simulate user input: "r" (redact)
        String input = "r\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));
        StringWriter outputWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(outputWriter);

        InteractiveDecisionManager manager = new InteractiveDecisionManager(
            decisionsFile, true, reader, writer
        );

        DiscoveredPatterns.DiscoveredValue value = new DiscoveredPatterns.DiscoveredValue(
            "secret-hostname",
            DiscoveredPatterns.PatternType.HOSTNAME
        );

        InteractiveDecisionManager.Decision decision = manager.getDecision(value);

        assertEquals(InteractiveDecisionManager.DecisionAction.REDACT, decision.getAction());
    }

    @Test
    void testReplaceDecision() throws IOException {
        Path decisionsFile = tempDir.resolve("test.decisions.yaml");

        // Simulate user input: "p" (replace) followed by replacement text
        String input = "p\nreplacement-text\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));
        StringWriter outputWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(outputWriter);

        InteractiveDecisionManager manager = new InteractiveDecisionManager(
            decisionsFile, true, reader, writer
        );

        DiscoveredPatterns.DiscoveredValue value = new DiscoveredPatterns.DiscoveredValue(
            "original-value",
            DiscoveredPatterns.PatternType.USERNAME
        );

        InteractiveDecisionManager.Decision decision = manager.getDecision(value);

        assertEquals(InteractiveDecisionManager.DecisionAction.REPLACE, decision.getAction());
        assertEquals("replacement-text", decision.getReplacement());
    }

    @Test
    void testKeepAllDecision() throws IOException {
        Path decisionsFile = tempDir.resolve("test.decisions.yaml");

        // Simulate user input: "K" (keep all)
        String input = "K\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));
        StringWriter outputWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(outputWriter);

        InteractiveDecisionManager manager = new InteractiveDecisionManager(
            decisionsFile, true, reader, writer
        );

        DiscoveredPatterns.DiscoveredValue value = new DiscoveredPatterns.DiscoveredValue(
            "username1",
            DiscoveredPatterns.PatternType.USERNAME
        );

        InteractiveDecisionManager.Decision decision = manager.getDecision(value);

        assertEquals(InteractiveDecisionManager.DecisionAction.KEEP_ALL, decision.getAction());
    }

    @Test
    void testSaveAndLoadDecisions() throws IOException {
        Path decisionsFile = tempDir.resolve("test.decisions.yaml");

        // First manager - make a decision
        {
            String input = "k\n";
            BufferedReader reader = new BufferedReader(new StringReader(input));
            StringWriter outputWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(outputWriter);

            InteractiveDecisionManager manager = new InteractiveDecisionManager(
                decisionsFile, true, reader, writer
            );

            DiscoveredPatterns.DiscoveredValue value = new DiscoveredPatterns.DiscoveredValue(
                "testuser",
                DiscoveredPatterns.PatternType.USERNAME
            );

            manager.getDecision(value);
            manager.saveDecisions();
        }

        // Second manager - load previous decisions
        {
            InteractiveDecisionManager manager = new InteractiveDecisionManager(
                decisionsFile, false
            );

            assertNotNull(manager.getStorage());
            assertTrue(manager.getStorage().getUsernames().containsKey("testuser"));
            assertEquals(
                InteractiveDecisionManager.DecisionAction.KEEP,
                manager.getStorage().getUsernames().get("testuser").getAction()
            );
        }
    }

    @Test
    void testNonInteractiveMode() throws IOException {
        Path decisionsFile = tempDir.resolve("test.decisions.yaml");

        // Non-interactive mode should return REDACT without prompting
        InteractiveDecisionManager manager = new InteractiveDecisionManager(
            decisionsFile, false
        );

        DiscoveredPatterns.DiscoveredValue value = new DiscoveredPatterns.DiscoveredValue(
            "somevalue",
            DiscoveredPatterns.PatternType.USERNAME
        );

        InteractiveDecisionManager.Decision decision = manager.getDecision(value);

        assertEquals(InteractiveDecisionManager.DecisionAction.REDACT, decision.getAction());
    }

    @Test
    void testDefaultDecisionOnEmptyInput() throws IOException {
        Path decisionsFile = tempDir.resolve("test.decisions.yaml");

        // Simulate user pressing Enter (empty input) - should default to redact
        String input = "\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));
        StringWriter outputWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(outputWriter);

        InteractiveDecisionManager manager = new InteractiveDecisionManager(
            decisionsFile, true, reader, writer
        );

        DiscoveredPatterns.DiscoveredValue value = new DiscoveredPatterns.DiscoveredValue(
            "testvalue",
            DiscoveredPatterns.PatternType.HOSTNAME
        );

        InteractiveDecisionManager.Decision decision = manager.getDecision(value);

        assertEquals(InteractiveDecisionManager.DecisionAction.REDACT, decision.getAction());
    }
}