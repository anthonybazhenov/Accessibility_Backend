package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured remediation plan: target format, compliance, issues, and actions.
 */
public class RemediationPlan {

    @JsonProperty("target")
    private String target; // "HTML" for now

    @JsonProperty("compliance")
    private String compliance; // COMPLIANT | NONCOMPLIANT (model can propose)

    @JsonProperty("issues")
    private List<RemediationIssue> issues;

    @JsonProperty("actions")
    private List<RemediationAction> actions;

    public RemediationPlan() {}

    public RemediationPlan(String target, String compliance, List<RemediationIssue> issues, List<RemediationAction> actions) {
        this.target = target;
        this.compliance = compliance;
        this.issues = issues;
        this.actions = actions;
    }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getCompliance() { return compliance; }
    public void setCompliance(String compliance) { this.compliance = compliance; }

    public List<RemediationIssue> getIssues() { return issues; }
    public void setIssues(List<RemediationIssue> issues) { this.issues = issues; }

    public List<RemediationAction> getActions() { return actions; }
    public void setActions(List<RemediationAction> actions) { this.actions = actions; }

    /**
     * Plain-text corrections summary in the format expected by Stage 2 (fine-tuned) prompts.
     */
    public String toCorrectionsText() {
        StringBuilder sb = new StringBuilder();
        sb.append("CORRECTIONS SUMMARY — ");
        sb.append(issues != null ? issues.size() : 0);
        sb.append(" errors found.\n\n");

        if (issues != null) {
            for (int i = 0; i < issues.size(); i++) {
                RemediationIssue issue = issues.get(i);
                sb.append("---\n\n");
                sb.append("ERROR ").append(i + 1).append(": ");
                sb.append(issue.getDescription()).append("\n");
                sb.append("WCAG criterion: ").append(issue.getCriterion()).append("\n");
                sb.append("Correction: ").append(issue.getCorrection()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Plan with target HTML and empty actions if missing (for persistence / downstream use).
     */
    public static RemediationPlan forParsedAudit(List<RemediationIssue> issues) {
        RemediationPlan plan = new RemediationPlan();
        plan.setTarget("HTML");
        plan.setIssues(issues != null ? issues : new ArrayList<>());
        plan.setActions(new ArrayList<>());
        return plan;
    }
}
