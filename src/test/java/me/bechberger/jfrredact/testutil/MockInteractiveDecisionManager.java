package me.bechberger.jfrredact.testutil;
import me.bechberger.jfrredact.engine.DiscoveredPatterns;
import me.bechberger.jfrredact.engine.InteractiveDecisionManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class MockInteractiveDecisionManager {
    private final Map<String, InteractiveDecisionManager.Decision> decisions = new HashMap<>();
    private final List<String> questionsAsked = new ArrayList<>();
    private InteractiveDecisionManager.DecisionAction defaultAction = InteractiveDecisionManager.DecisionAction.KEEP;
    public MockInteractiveDecisionManager withDecision(String value, InteractiveDecisionManager.DecisionAction action) {
        decisions.put(value, new InteractiveDecisionManager.Decision(action, null));
        return this;
    }
    public MockInteractiveDecisionManager withReplacement(String value, String replacement) {
        decisions.put(value, new InteractiveDecisionManager.Decision(
            InteractiveDecisionManager.DecisionAction.REPLACE, replacement));
        return this;
    }
    public MockInteractiveDecisionManager withDefaultAction(InteractiveDecisionManager.DecisionAction action) {
        this.defaultAction = action;
        return this;
    }
    public InteractiveDecisionManager.Decision getDecision(DiscoveredPatterns.DiscoveredValue value) {
        String val = value.getValue();
        questionsAsked.add(val);
        InteractiveDecisionManager.Decision decision = decisions.get(val);
        if (decision != null) {
            return decision;
        }
        return new InteractiveDecisionManager.Decision(defaultAction, null);
    }
    public List<String> getQuestionsAsked() {
        return new ArrayList<>(questionsAsked);
    }
    public boolean wasAsked(String value) {
        return questionsAsked.contains(value);
    }
    public int getQuestionCount() {
        return questionsAsked.size();
    }
    public void clear() {
        questionsAsked.clear();
    }
    public enum Decision {
        KEEP(InteractiveDecisionManager.DecisionAction.KEEP),
        REDACT(InteractiveDecisionManager.DecisionAction.REDACT),
        REPLACE(InteractiveDecisionManager.DecisionAction.REPLACE);
        final InteractiveDecisionManager.DecisionAction action;
        Decision(InteractiveDecisionManager.DecisionAction action) {
            this.action = action;
        }
        public InteractiveDecisionManager.DecisionAction getAction() {
            return action;
        }
    }
    public MockInteractiveDecisionManager withDecision(String value, Decision decision) {
        return withDecision(value, decision.getAction());
    }
    public MockInteractiveDecisionManager withDefaultDecision(Decision decision) {
        return withDefaultAction(decision.getAction());
    }
}
