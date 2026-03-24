package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.annotation.JsonProperty;
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
}
