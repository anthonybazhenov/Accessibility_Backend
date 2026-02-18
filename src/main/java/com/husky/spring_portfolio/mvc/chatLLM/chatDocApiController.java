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

import java.io.IOException;
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
            response.put("status", document.getStatus());
            
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
