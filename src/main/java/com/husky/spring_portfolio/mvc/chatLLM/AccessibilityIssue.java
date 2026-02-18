package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a WCAG accessibility issue found in the PDF
 */
public class AccessibilityIssue {
    @JsonProperty("issue")
    private String issue; // e.g., "Missing tags / reading order unknown", "Figure has no /Alt"
    
    @JsonProperty("success_criteria")
    private String successCriteria; // WCAG success criteria (e.g., "1.1.1 Non-text Content")
    
    @JsonProperty("severity")
    private String severity; // "error", "warning", "info"
    
    @JsonProperty("evidence")
    private String evidence; // Where the issue was found
    
    @JsonProperty("fix_steps")
    private String fixSteps; // How to fix the issue
    
    @JsonProperty("page_number")
    private Integer pageNumber;
    
    @JsonProperty("element_id")
    private String elementId;

    public AccessibilityIssue() {}

    public AccessibilityIssue(String issue, String successCriteria, String severity, 
                              String evidence, String fixSteps) {
        this.issue = issue;
        this.successCriteria = successCriteria;
        this.severity = severity;
        this.evidence = evidence;
        this.fixSteps = fixSteps;
    }

    // Getters and setters
    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }

    public String getSuccessCriteria() { return successCriteria; }
    public void setSuccessCriteria(String successCriteria) { this.successCriteria = successCriteria; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getFixSteps() { return fixSteps; }
    public void setFixSteps(String fixSteps) { this.fixSteps = fixSteps; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getElementId() { return elementId; }
    public void setElementId(String elementId) { this.elementId = elementId; }
}
