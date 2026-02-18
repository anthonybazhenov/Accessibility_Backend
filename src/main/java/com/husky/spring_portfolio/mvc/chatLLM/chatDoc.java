package com.husky.spring_portfolio.mvc.chatLLM;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

@Entity
public class chatDoc {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    private String originalFilename;
    
    @Column(columnDefinition = "TEXT")
    private String originalContent; // Extracted text from PDF
    
    @Column(columnDefinition = "TEXT")
    private String alteredContent; // HTML accessible version or processed content
    
    private String originalPdfPath; // Path to stored original PDF
    
    private String alteredPdfPath; // Path to remediated PDF (if created)
    
    @Column(columnDefinition = "TEXT")
    private String altTextJson; // JSON array of AltTextResult objects
    
    @Column(columnDefinition = "TEXT")
    private String accessibilityReportJson; // JSON AccessibilityReport
    
    // Status enum: UPLOADED | EXTRACTED | ALT_DONE | REMEDIATED | NEEDS_REVIEW | FAILED
    private String status;
    
    private LocalDateTime timestamp;

    // Pipeline stage: UPLOADED | EXTRACTED | ALT_DONE | REPORT_DONE | HTML_DONE | FAILED
    private String pipelineStatus;

    // Ground-truth / dataset label: COMPLIANT | NONCOMPLIANT | UNKNOWN
    private String complianceLabel;

    // Where the label came from: HEURISTIC | HUMAN
    private String labelSource;

    // Constructors
    public chatDoc() {}

    public chatDoc(String originalFilename, String originalContent, String alteredContent, String status) {
        this.originalFilename = originalFilename;
        this.originalContent = originalContent;
        this.alteredContent = alteredContent;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    
    public String getOriginalContent() { return originalContent; }
    public void setOriginalContent(String originalContent) { this.originalContent = originalContent; }
    
    public String getAlteredContent() { return alteredContent; }
    public void setAlteredContent(String alteredContent) { this.alteredContent = alteredContent; }
    
    public String getOriginalPdfPath() { return originalPdfPath; }
    public void setOriginalPdfPath(String originalPdfPath) { this.originalPdfPath = originalPdfPath; }
    
    public String getAlteredPdfPath() { return alteredPdfPath; }
    public void setAlteredPdfPath(String alteredPdfPath) { this.alteredPdfPath = alteredPdfPath; }
    
    public String getAltTextJson() { return altTextJson; }
    public void setAltTextJson(String altTextJson) { this.altTextJson = altTextJson; }
    
    public String getAccessibilityReportJson() { return accessibilityReportJson; }
    public void setAccessibilityReportJson(String accessibilityReportJson) { 
        this.accessibilityReportJson = accessibilityReportJson; 
    }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getPipelineStatus() { return pipelineStatus; }
    public void setPipelineStatus(String pipelineStatus) { this.pipelineStatus = pipelineStatus; }

    public String getComplianceLabel() { return complianceLabel; }
    public void setComplianceLabel(String complianceLabel) { this.complianceLabel = complianceLabel; }

    public String getLabelSource() { return labelSource; }
    public void setLabelSource(String labelSource) { this.labelSource = labelSource; }

}
