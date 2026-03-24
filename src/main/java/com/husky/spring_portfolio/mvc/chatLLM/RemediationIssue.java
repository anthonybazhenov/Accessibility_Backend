package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A single issue to be remediated (WCAG success criteria, severity, evidence, fix steps).
 */
public class RemediationIssue {

    @JsonProperty("successCriteria")
    private String successCriteria; // e.g. "1.1.1"

    @JsonProperty("severity")
    private String severity; // "error" | "warning"

    @JsonProperty("evidence")
    private String evidence;

    @JsonProperty("fixSteps")
    private List<String> fixSteps;

    public RemediationIssue() {}

    public RemediationIssue(String successCriteria, String severity, String evidence, List<String> fixSteps) {
        this.successCriteria = successCriteria;
        this.severity = severity;
        this.evidence = evidence;
        this.fixSteps = fixSteps;
    }

    public String getSuccessCriteria() { return successCriteria; }
    public void setSuccessCriteria(String successCriteria) { this.successCriteria = successCriteria; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public List<String> getFixSteps() { return fixSteps; }
    public void setFixSteps(List<String> fixSteps) { this.fixSteps = fixSteps; }
}
