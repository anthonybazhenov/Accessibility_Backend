package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * WCAG-style accessibility report for a PDF document
 */
public class AccessibilityReport {
    @JsonProperty("document_id")
    private Long documentId;
    
    @JsonProperty("filename")
    private String filename;
    
    @JsonProperty("total_issues")
    private int totalIssues;
    
    @JsonProperty("errors")
    private int errors;
    
    @JsonProperty("warnings")
    private int warnings;
    
    @JsonProperty("info")
    private int info;
    
    @JsonProperty("issues")
    private List<AccessibilityIssue> issues;
    
    @JsonProperty("is_tagged")
    private boolean isTagged;
    
    @JsonProperty("has_reading_order")
    private boolean hasReadingOrder;
    
    @JsonProperty("images_with_alt_text")
    private int imagesWithAltText;
    
    @JsonProperty("total_images")
    private int totalImages;

    public AccessibilityReport() {}

    public AccessibilityReport(Long documentId, String filename, List<AccessibilityIssue> issues) {
        this.documentId = documentId;
        this.filename = filename;
        this.issues = issues;
        this.totalIssues = issues != null ? issues.size() : 0;
        
        if (issues != null) {
            this.errors = (int) issues.stream().filter(i -> "error".equals(i.getSeverity())).count();
            this.warnings = (int) issues.stream().filter(i -> "warning".equals(i.getSeverity())).count();
            this.info = (int) issues.stream().filter(i -> "info".equals(i.getSeverity())).count();
        }
    }

    // Getters and setters
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public int getTotalIssues() { return totalIssues; }
    public void setTotalIssues(int totalIssues) { this.totalIssues = totalIssues; }

    public int getErrors() { return errors; }
    public void setErrors(int errors) { this.errors = errors; }

    public int getWarnings() { return warnings; }
    public void setWarnings(int warnings) { this.warnings = warnings; }

    public int getInfo() { return info; }
    public void setInfo(int info) { this.info = info; }

    public List<AccessibilityIssue> getIssues() { return issues; }
    public void setIssues(List<AccessibilityIssue> issues) { 
        this.issues = issues;
        if (issues != null) {
            this.totalIssues = issues.size();
            this.errors = (int) issues.stream().filter(i -> "error".equals(i.getSeverity())).count();
            this.warnings = (int) issues.stream().filter(i -> "warning".equals(i.getSeverity())).count();
            this.info = (int) issues.stream().filter(i -> "info".equals(i.getSeverity())).count();
        }
    }

    public boolean isTagged() { return isTagged; }
    public void setTagged(boolean tagged) { isTagged = tagged; }

    public boolean isHasReadingOrder() { return hasReadingOrder; }
    public void setHasReadingOrder(boolean hasReadingOrder) { this.hasReadingOrder = hasReadingOrder; }

    public int getImagesWithAltText() { return imagesWithAltText; }
    public void setImagesWithAltText(int imagesWithAltText) { this.imagesWithAltText = imagesWithAltText; }

    public int getTotalImages() { return totalImages; }
    public void setTotalImages(int totalImages) { this.totalImages = totalImages; }
}
