package me.bechberger.jfrredact.engine;
import me.bechberger.jfrredact.testutil.MockInteractiveDecisionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class InteractiveModeTest {
    @TempDir
    Path tempDir;
    @Test
    void testInteractiveDecisionManager_BasicKeep() {
        MockInteractiveDecisionManager manager = new MockInteractiveDecisionManager()
            .withDecision("john_doe", MockInteractiveDecisionManager.Decision.KEEP)
            .withDecision("secret123", MockInteractiveDecisionManager.Decision.REDACT);
        var val1 = new DiscoveredPatterns.DiscoveredValue("john_doe", DiscoveredPatterns.PatternType.USERNAME);
        var decision1 = manager.getDecision(val1);
        assertEquals(InteractiveDecisionManager.DecisionAction.KEEP, decision1.getAction());
        var val2 = new DiscoveredPatterns.DiscoveredValue("secret123", DiscoveredPatterns.PatternType.CUSTOM);
        var decision2 = manager.getDecision(val2);
        assertEquals(InteractiveDecisionManager.DecisionAction.REDACT, decision2.getAction());
        assertTrue(manager.wasAsked("john_doe"));
        assertTrue(manager.wasAsked("secret123"));
        assertEquals(2, manager.getQuestionCount());
    }
    @Test
    void testInteractiveDecisionManager_WithReplacement() {
        MockInteractiveDecisionManager manager = new MockInteractiveDecisionManager()
            .withReplacement("server01", "SERVER_A");
        var val = new DiscoveredPatterns.DiscoveredValue("server01", DiscoveredPatterns.PatternType.HOSTNAME);
        var decision = manager.getDecision(val);
        assertEquals(InteractiveDecisionManager.DecisionAction.REPLACE, decision.getAction());
        assertEquals("SERVER_A", decision.getReplacement());
    }
    @Test
    void testInteractiveDecisionManager_DefaultDecision() {
        MockInteractiveDecisionManager manager = new MockInteractiveDecisionManager()
            .withDefaultDecision(MockInteractiveDecisionManager.Decision.REDACT);
        var val = new DiscoveredPatterns.DiscoveredValue("unknown_value", DiscoveredPatterns.PatternType.CUSTOM);
        var decision = manager.getDecision(val);
        assertEquals(InteractiveDecisionManager.DecisionAction.REDACT, decision.getAction());
    }
    @Test
    void testMultipleDecisions() {
        MockInteractiveDecisionManager manager = new MockInteractiveDecisionManager()
            .withDecision("user1", MockInteractiveDecisionManager.Decision.REDACT)
            .withDecision("user2", MockInteractiveDecisionManager.Decision.KEEP)
            .withReplacement("server01", "SERVER_A")
            .withReplacement("server02", "SERVER_B");
        manager.getDecision(new DiscoveredPatterns.DiscoveredValue("user1", DiscoveredPatterns.PatternType.USERNAME));
        manager.getDecision(new DiscoveredPatterns.DiscoveredValue("user2", DiscoveredPatterns.PatternType.USERNAME));
        manager.getDecision(new DiscoveredPatterns.DiscoveredValue("server01", DiscoveredPatterns.PatternType.HOSTNAME));
        manager.getDecision(new DiscoveredPatterns.DiscoveredValue("server02", DiscoveredPatterns.PatternType.HOSTNAME));
        assertEquals(4, manager.getQuestionCount());
        assertTrue(manager.wasAsked("user1"));
        assertTrue(manager.wasAsked("user2"));
        assertTrue(manager.wasAsked("server01"));
        assertTrue(manager.wasAsked("server02"));
    }
    @Test
    void testClearQuestions() {
        MockInteractiveDecisionManager manager = new MockInteractiveDecisionManager();
        manager.getDecision(new DiscoveredPatterns.DiscoveredValue("test1", DiscoveredPatterns.PatternType.CUSTOM));
        manager.getDecision(new DiscoveredPatterns.DiscoveredValue("test2", DiscoveredPatterns.PatternType.CUSTOM));
        assertEquals(2, manager.getQuestionCount());
        manager.clear();
        assertEquals(0, manager.getQuestionCount());
        assertFalse(manager.wasAsked("test1"));
    }
    @Test
    void testDefaultDecisionAppliedToAll() {
        MockInteractiveDecisionManager manager = new MockInteractiveDecisionManager()
            .withDefaultDecision(MockInteractiveDecisionManager.Decision.KEEP);
        var d1 = manager.getDecision(new DiscoveredPatterns.DiscoveredValue("val1", DiscoveredPatterns.PatternType.CUSTOM));
        var d2 = manager.getDecision(new DiscoveredPatterns.DiscoveredValue("val2", DiscoveredPatterns.PatternType.CUSTOM));
        var d3 = manager.getDecision(new DiscoveredPatterns.DiscoveredValue("val3", DiscoveredPatterns.PatternType.CUSTOM));
        assertEquals(InteractiveDecisionManager.DecisionAction.KEEP, d1.getAction());
        assertEquals(InteractiveDecisionManager.DecisionAction.KEEP, d2.getAction());
        assertEquals(InteractiveDecisionManager.DecisionAction.KEEP, d3.getAction());
    }
    @Test
    void testReplacementOverridesDefault() {
        MockInteractiveDecisionManager manager = new MockInteractiveDecisionManager()
            .withDefaultDecision(MockInteractiveDecisionManager.Decision.KEEP)
            .withReplacement("special", "SPECIAL_VALUE");
        var d1 = manager.getDecision(new DiscoveredPatterns.DiscoveredValue("normal", DiscoveredPatterns.PatternType.CUSTOM));
        var d2 = manager.getDecision(new DiscoveredPatterns.DiscoveredValue("special", DiscoveredPatterns.PatternType.CUSTOM));
        assertEquals(InteractiveDecisionManager.DecisionAction.KEEP, d1.getAction());
        assertEquals(InteractiveDecisionManager.DecisionAction.REPLACE, d2.getAction());
        assertEquals("SPECIAL_VALUE", d2.getReplacement());
    }
}
