package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A single issue to be remediated (WCAG success criteria, severity, evidence, fix steps).
 */
public class RemediationIssue {

    /** Plain-text error description (LLM / corrections summary round-trip). */
    @JsonProperty("description")
    private String description;

    /** WCAG success criterion label (LLM / corrections summary). */
    @JsonProperty("criterion")
    private String criterion;

    /** Plain-text correction instructions (LLM / corrections summary). */
    @JsonProperty("correction")
    private String correction;

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

    public String getDescription() {
        return description != null && !description.isEmpty() ? description : evidence;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCriterion() {
        return criterion != null && !criterion.isEmpty() ? criterion : successCriteria;
    }

    public void setCriterion(String criterion) {
        this.criterion = criterion;
    }

    public String getCorrection() {
        if (correction != null && !correction.isEmpty()) {
            return correction;
        }
        if (fixSteps == null || fixSteps.isEmpty()) {
            return "";
        }
        return String.join("\n", fixSteps);
    }

    public void setCorrection(String correction) {
        this.correction = correction;
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
