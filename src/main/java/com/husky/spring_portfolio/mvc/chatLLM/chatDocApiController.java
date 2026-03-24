package com.husky.spring_portfolio.mvc.chatLLM;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:4000", "http://localhost:5500", 
                       "http://127.0.0.1:3000", "http://127.0.0.1:4000", "http://127.0.0.1:5500"})
public class chatDocApiController {
    private final chatDocService documentService;

    @Autowired
    public chatDocApiController(chatDocService documentService) {
        this.documentService = documentService;
    }

    /**
     * POST endpoint to upload PDF documents
     * Accepts multipart/form-data with a file parameter
     */
    @PostMapping("/inputDocuments")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file
            if (file.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File is empty");
                return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
            }

            if (!file.getContentType().equals("application/pdf")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File must be a PDF");
                return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
            }

            // Process the PDF document
            chatDoc document = documentService.processPdfDocument(file);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Document processed successfully");
            response.put("documentId", document.getId());
            response.put("filename", document.getOriginalFilename());
            response.put("pipelineStatus", document.getPipelineStatus());
            response.put("outcomeStatus", document.getOutcomeStatus());
            response.put("status", document.getOutcomeStatus() != null ? document.getOutcomeStatus() : document.getStatus());
            
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to process document: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET endpoint to retrieve all completed altered documents
     */
    @GetMapping("/alteredDocuments")
    public ResponseEntity<List<chatDoc>> getAlteredDocuments() {
        try {
            List<chatDoc> alteredDocuments = documentService.getAlteredDocuments();
            return new ResponseEntity<>(alteredDocuments, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET endpoint to return full stored fields for a document.
     * By default excludes alteredContent (HTML); use includeHtml=true to include it.
     */
    @GetMapping("/alteredDocuments/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable Long id,
                                             @RequestParam(required = false, defaultValue = "false") boolean includeHtml) {
        chatDoc document = documentService.getDocumentById(id);
        if (document == null) {
            return new ResponseEntity<>(Map.of("error", "Document not found"), HttpStatus.NOT_FOUND);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("id", document.getId());
        body.put("originalFilename", document.getOriginalFilename());
        body.put("originalPdfPath", document.getOriginalPdfPath());
        body.put("alteredPdfPath", document.getAlteredPdfPath());
        body.put("originalContent", document.getOriginalContent());
        body.put("altTextJson", document.getAltTextJson());
        body.put("accessibilityReportJson", document.getAccessibilityReportJson());
        body.put("pipelineStatus", document.getPipelineStatus());
        body.put("outcomeStatus", document.getOutcomeStatus());
        body.put("status", document.getOutcomeStatus() != null ? document.getOutcomeStatus() : document.getStatus());
        body.put("timestamp", document.getTimestamp());
        body.put("complianceLabel", document.getComplianceLabel());
        body.put("labelSource", document.getLabelSource());
        body.put("auditJson", document.getAuditJson());
        body.put("remediationPlanJson", document.getRemediationPlanJson());
        if (includeHtml) {
            body.put("alteredContent", document.getAlteredContent());
        } else {
            body.put("alteredContentIncluded", false);
        }
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    /**
     * PATCH endpoint to set human compliance label (overrides heuristic).
     * value=COMPLIANT|NONCOMPLIANT|UNKNOWN
     */
    @PatchMapping("/alteredDocuments/{id}/label")
    public ResponseEntity<?> setHumanLabel(@PathVariable Long id,
                                           @RequestParam("value") String value) {
        String v = value != null ? value.toUpperCase().trim() : "";
        if (!v.equals("COMPLIANT") && !v.equals("NONCOMPLIANT") && !v.equals("UNKNOWN")) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "value must be COMPLIANT, NONCOMPLIANT, or UNKNOWN");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
        chatDoc document = documentService.updateHumanLabel(id, v);
        if (document == null) {
            return new ResponseEntity<>(Map.of("error", "Document not found"), HttpStatus.NOT_FOUND);
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", document.getId());
        summary.put("filename", document.getOriginalFilename());
        summary.put("complianceLabel", document.getComplianceLabel());
        summary.put("labelSource", document.getLabelSource());
        summary.put("pipelineStatus", document.getPipelineStatus());
        summary.put("outcomeStatus", document.getOutcomeStatus());
        summary.put("status", document.getOutcomeStatus() != null ? document.getOutcomeStatus() : document.getStatus());
        return new ResponseEntity<>(summary, HttpStatus.OK);
    }

    /**
     * GET endpoint to retrieve accessibility report for a specific document
     */
    @GetMapping("/alteredDocuments/{id}/report")
    public ResponseEntity<?> getAccessibilityReport(@PathVariable Long id) {
        try {
            AccessibilityReport report = documentService.getAccessibilityReport(id);
            if (report == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Document not found or report not available");
                return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<>(report, HttpStatus.OK);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to retrieve report: " + e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET endpoint to return JSON Schema for RemediationPlan (for validation / parsing).
     */
    @GetMapping("/remediation-plan/schema")
    public ResponseEntity<?> getRemediationPlanSchema() {
        return new ResponseEntity<>(RemediationPlanSchema.getJsonSchema(), HttpStatus.OK);
    }

    /**
     * GET endpoint to download remediated document (HTML or PDF)
     */
    @GetMapping("/alteredDocuments/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id,
                                                      @RequestParam(required = false, defaultValue = "html") String format) {
        try {
            chatDoc document = documentService.getDocumentById(id);
            if (document == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            Resource resource;
            String filename;
            MediaType mediaType;

            if ("pdf".equalsIgnoreCase(format) && document.getAlteredPdfPath() != null) {
                // Serve remediated PDF if available
                resource = new FileSystemResource(Paths.get(document.getAlteredPdfPath()));
                filename = document.getOriginalFilename();
                mediaType = MediaType.APPLICATION_PDF;
            } else {
                // Serve accessible HTML version
                if (document.getAlteredContent() == null) {
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
                
                // Create temporary file for HTML content
                java.nio.file.Path tempFile = Files.createTempFile("accessible_", ".html");
                Files.write(tempFile, document.getAlteredContent().getBytes());
                resource = new FileSystemResource(tempFile);
                filename = document.getOriginalFilename().replace(".pdf", ".html");
                mediaType = MediaType.TEXT_HTML;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
