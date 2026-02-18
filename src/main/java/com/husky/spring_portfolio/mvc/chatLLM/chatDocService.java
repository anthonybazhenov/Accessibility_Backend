package com.husky.spring_portfolio.mvc.chatLLM;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

@Service
public class chatDocService {
    private final chatDocRepository documentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${app.upload.dir:volumes/uploads}")
    private String uploadDir;
    
    @Value("${openai.api.key:}")
    private String openaiApiKey;
    
    @Value("${openai.model:gpt-4o}")
    private String openaiModel; // Can be changed to fine-tuned model: "ft:gpt-4o:your-org:pdf-alttext:..."

    @Autowired
    public chatDocService(chatDocRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Process PDF file: extract images, generate alt text, create accessibility report
     */
    public chatDoc processPdfDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("File must be a PDF");
        }

        // Read PDF bytes
        byte[] pdfBytes = file.getBytes();
        
        // Save original PDF
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
        String originalPdfPath = uploadPath.resolve(filename).toString();
        Files.write(Paths.get(originalPdfPath), pdfBytes);
        
        // Create document entity
        chatDoc document = new chatDoc();
        document.setOriginalFilename(filename);
        document.setOriginalPdfPath(originalPdfPath);
        document.setStatus("UPLOADED");
        document.setTimestamp(LocalDateTime.now());
        document = documentRepository.save(document);
        
        try {
            // Step 1: Extract text and images
            document.setStatus("EXTRACTED");
            String originalContent = extractTextFromPdf(pdfBytes);
            document.setOriginalContent(originalContent);
            
            List<ImageInfo> images = extractImagesFromPdf(pdfBytes, originalContent);
            document = documentRepository.save(document);
            
            // Step 2: Generate alt text for images
            List<AltTextResult> altTextResults = new ArrayList<>();
            if (!images.isEmpty()) {
                document.setStatus("ALT_DONE");
                altTextResults = generateAltTextForImages(images);
                document.setAltTextJson(objectMapper.writeValueAsString(altTextResults));
                document = documentRepository.save(document);
            }
            
            // Step 3: Generate accessibility report
            AccessibilityReport report = generateAccessibilityReport(document, images);
            document.setAccessibilityReportJson(objectMapper.writeValueAsString(report));
            
            // Step 4: Create accessible HTML version
            String accessibleHtml = createAccessibleHtml(originalContent, images, altTextResults);
            document.setAlteredContent(accessibleHtml);
            
            // Determine final status
                // Determine compliance label from the report (heuristic)
            if (report.getErrors() > 0) {
                document.setComplianceLabel("NONCOMPLIANT");
            } else {
                document.setComplianceLabel("COMPLIANT");
            }
            document.setLabelSource("HEURISTIC");

            // Determine final outcome status (optional)
            if (report.getErrors() > 0) {
                document.setStatus("NEEDS_REVIEW");
            } else if (report.getWarnings() > 0) {
                document.setStatus("REMEDIATED_WITH_WARNINGS");
            } else {
                document.setStatus("REMEDIATED");
            }

            
            return documentRepository.save(document);
            
        } catch (Exception e) {
            document.setStatus("FAILED");
            documentRepository.save(document);
            throw new IOException("Failed to process PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text content from PDF
     */
    private String extractTextFromPdf(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Extract images from PDF with context (page number, bounding box, nearby text)
     */
    private List<ImageInfo> extractImagesFromPdf(byte[] pdfBytes, String fullText) throws IOException {
        List<ImageInfo> images = new ArrayList<>();
        
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageNum = 0;
            for (PDPage page : document.getPages()) {
                pageNum++;
                PDResources resources = page.getResources();
                
                // Extract text from this page for context
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document);
                
                // Extract images from resources
                int imageIndex = 0;
                for (COSName xObjectName : resources.getXObjectNames()) {
                    PDXObject xObject = resources.getXObject(xObjectName);
                    if (xObject instanceof PDImageXObject) {
                        PDImageXObject image = (PDImageXObject) xObject;
                        
                        try {
                            BufferedImage bufferedImage = image.getImage();
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(bufferedImage, "PNG", baos);
                            byte[] imageBytes = baos.toByteArray();
                            
                            ImageInfo imageInfo = new ImageInfo();
                            imageInfo.setPageNumber(pageNum);
                            imageInfo.setImageId("img_" + pageNum + "_" + imageIndex);
                            imageInfo.setImageBytes(imageBytes);
                            imageInfo.setImageFormat("PNG");
                            
                            // Get image position (simplified - PDFBox doesn't always provide this easily)
                            PDRectangle pageSize = page.getMediaBox();
                            imageInfo.setX(0); // Would need more complex parsing for exact position
                            imageInfo.setY(0);
                            imageInfo.setWidth(bufferedImage.getWidth());
                            imageInfo.setHeight(bufferedImage.getHeight());
                            
                            // Extract context text (nearby text on page)
                            imageInfo.setContextText(pageText);
                            
                            images.add(imageInfo);
                            imageIndex++;
                        } catch (Exception e) {
                            System.err.println("Error extracting image: " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        return images;
    }

    /**
     * Generate alt text for images using OpenAI API
     */
    private List<AltTextResult> generateAltTextForImages(List<ImageInfo> images) throws IOException {
        List<AltTextResult> results = new ArrayList<>();
        
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            return buildPlaceholderAltText(images);
        }

        HttpClient client = HttpClient.newBuilder().build();
        String imageBaseUrl = "data:image/png;base64,";

        for (ImageInfo image : images) {
            try {
                String base64Image = Base64.getEncoder().encodeToString(image.getImageBytes());
                String contextText = image.getContextText() != null
                    ? image.getContextText().substring(0, Math.min(1500, image.getContextText().length()))
                    : "No surrounding text.";
                String prompt = buildAltTextPrompt(image.getImageId(), image.getPageNumber(), contextText);

                String requestBody;
                try {
                    requestBody = buildOpenAIVisionRequest(prompt, imageBaseUrl + base64Image);
                } catch (Exception e) {
                    results.add(placeholderForImage(image));
                    continue;
                }
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    results.add(placeholderForImage(image));
                    continue;
                }
                AltTextResult parsed = parseAltTextResponse(response.body(), image.getImageId());
                results.add(parsed != null ? parsed : placeholderForImage(image));
            } catch (Exception e) {
                results.add(placeholderForImage(image));
            }
        }
        return results;
    }

    private static String buildAltTextPrompt(String imageId, int pageNumber, String contextText) {
        return "You are an accessibility expert. For this image from a PDF (page " + pageNumber + "), "
            + "provide alt text. Use the surrounding context to describe the image accurately. "
            + "Never repeat text that is already in the context. "
            + "Respond with ONLY a single JSON object, no other text, with these exact keys: "
            + "\"decorative\" (boolean), \"alt\" (string, short 1-sentence description), "
            + "\"longdesc\" (string, extended description for charts/diagrams or empty string), "
            + "\"confidence\" (number 0-1), \"needs_human_review\" (boolean). "
            + "If the image is purely decorative, set decorative to true and alt to empty string. "
            + "Context from document:\n" + contextText;
    }

    private String buildOpenAIVisionRequest(String prompt, String imageDataUrl) throws IOException {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt != null ? prompt : ""));
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl != null ? imageDataUrl : "")));
        message.put("content", content);
        Map<String, Object> body = new HashMap<>();
        body.put("model", openaiModel);
        body.put("max_tokens", 500);
        body.put("messages", List.of(message));
        return objectMapper.writeValueAsString(body);
    }

    private AltTextResult parseAltTextResponse(String responseBody, String imageId) {
        try {
            Map<?, ?> root = objectMapper.readValue(responseBody, Map.class);
            List<?> choices = (List<?>) root.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            String content = (String) message.get("content");
            if (content == null) return null;
            content = content.trim();
            if (content.startsWith("```")) {
                int start = content.indexOf("{");
                int end = content.lastIndexOf("}");
                if (start >= 0 && end > start) content = content.substring(start, end + 1);
            }
            AltTextResult result = objectMapper.readValue(content, AltTextResult.class);
            result.setImageId(imageId);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private List<AltTextResult> buildPlaceholderAltText(List<ImageInfo> images) {
        List<AltTextResult> results = new ArrayList<>();
        for (ImageInfo image : images) {
            results.add(placeholderForImage(image));
        }
        return results;
    }

    private static AltTextResult placeholderForImage(ImageInfo image) {
        AltTextResult result = new AltTextResult();
        result.setImageId(image.getImageId());
        result.setDecorative(false);
        result.setAlt("Image on page " + image.getPageNumber());
        result.setLongdesc(image.getContextText() != null
            ? image.getContextText().substring(0, Math.min(200, image.getContextText().length()))
            : "Image extracted from PDF.");
        result.setConfidence(0.5);
        result.setNeedsHumanReview(true);
        return result;
    }

    /**
     * Generate WCAG-style accessibility report
     */
    private AccessibilityReport generateAccessibilityReport(chatDoc document, List<ImageInfo> images) {
        List<AccessibilityIssue> issues = new ArrayList<>();
        
        // Check if PDF is tagged
        boolean isTagged = false;
        try {
            byte[] pdfBytes = Files.readAllBytes(Paths.get(document.getOriginalPdfPath()));
            try (PDDocument pdfDoc = Loader.loadPDF(pdfBytes)) {
                // Check for structure tree (simplified check)
                isTagged = pdfDoc.getDocumentCatalog().getStructureTreeRoot() != null;
            }
        } catch (Exception e) {
            // PDF might not be readable or not tagged
        }
        
        if (!isTagged) {
            AccessibilityIssue issue = new AccessibilityIssue();
            issue.setIssue("PDF is not tagged - missing structure tree");
            issue.setSuccessCriteria("WCAG 1.3.1 Info and Relationships");
            issue.setSeverity("error");
            issue.setEvidence("Document structure tree is missing");
            issue.setFixSteps("Tag the PDF using Adobe Acrobat Pro or PDF remediation tools");
            issues.add(issue);
            
            issue = new AccessibilityIssue();
            issue.setIssue("Reading order cannot be determined");
            issue.setSuccessCriteria("WCAG 1.3.2 Meaningful Sequence");
            issue.setSeverity("error");
            issue.setEvidence("No structure tree to determine reading order");
            issue.setFixSteps("Add structure tree and logical reading order to PDF");
            issues.add(issue);
        }
        
        // Check images for alt text
        List<AltTextResult> altTextResults = new ArrayList<>();
        try {
            if (document.getAltTextJson() != null) {
                altTextResults = objectMapper.readValue(document.getAltTextJson(), 
                    new TypeReference<List<AltTextResult>>() {});
            }
        } catch (Exception e) {
            // Alt text JSON not available
        }
        
        int imagesWithAlt = 0;
        for (AltTextResult altText : altTextResults) {
            if (altText.getAlt() != null && !altText.getAlt().isEmpty() && !altText.isDecorative()) {
                imagesWithAlt++;
            } else if (!altText.isDecorative()) {
                AccessibilityIssue issue = new AccessibilityIssue();
                issue.setIssue("Image missing alt text");
                issue.setSuccessCriteria("WCAG 1.1.1 Non-text Content");
                issue.setSeverity("error");
                issue.setEvidence("Image " + altText.getImageId() + " has no alt text");
                issue.setFixSteps("Add descriptive alt text to image");
                issue.setElementId(altText.getImageId());
                issues.add(issue);
            }
        }
        
        AccessibilityReport report = new AccessibilityReport();
        report.setDocumentId(document.getId());
        report.setFilename(document.getOriginalFilename());
        report.setIssues(issues);
        report.setTagged(isTagged);
        report.setHasReadingOrder(isTagged);
        report.setImagesWithAltText(imagesWithAlt);
        report.setTotalImages(images.size());
        
        return report;
    }

    /**
     * Create accessible HTML version of the document
     */
    private String createAccessibleHtml(String originalContent, List<ImageInfo> images, 
                                        List<AltTextResult> altTextResults) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Accessible Document</title>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <main>\n");
        
        // Convert text content to HTML with proper structure
        String[] paragraphs = originalContent.split("\n\n");
        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;
            
            // Simple heuristic: if starts with number or uppercase, might be heading
            if (para.length() < 100 && (Character.isDigit(para.charAt(0)) || 
                (para.length() > 0 && Character.isUpperCase(para.charAt(0)) && !para.contains(".")))) {
                html.append("    <h2>").append(escapeHtml(para)).append("</h2>\n");
            } else {
                html.append("    <p>").append(escapeHtml(para)).append("</p>\n");
            }
        }
        
        // Add images with alt text
        Map<String, AltTextResult> altTextMap = altTextResults.stream()
            .collect(Collectors.toMap(AltTextResult::getImageId, r -> r));
        
        for (ImageInfo image : images) {
            AltTextResult altText = altTextMap.get(image.getImageId());
            html.append("    <figure>\n");
            html.append("      <img src=\"data:image/png;base64,")
                .append(Base64.getEncoder().encodeToString(image.getImageBytes()))
                .append("\" alt=\"");
            
            if (altText != null && altText.getAlt() != null) {
                html.append(escapeHtml(altText.getAlt()));
            } else {
                html.append("Image on page ").append(image.getPageNumber());
            }
            
            html.append("\" />\n");
            
            if (altText != null && altText.getLongdesc() != null && !altText.getLongdesc().isEmpty()) {
                html.append("      <figcaption>").append(escapeHtml(altText.getLongdesc())).append("</figcaption>\n");
            }
            
            html.append("    </figure>\n");
        }
        
        html.append("  </main>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Get all completed altered documents
     */
    public List<chatDoc> getAlteredDocuments() {
        return documentRepository.findAll().stream()
            .filter(doc -> doc.getStatus() != null && 
                ("REMEDIATED".equals(doc.getStatus()) || "NEEDS_REVIEW".equals(doc.getStatus())))
            .filter(doc -> doc.getAlteredContent() != null && !doc.getAlteredContent().isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * Get document by ID
     */
    public chatDoc getDocumentById(Long id) {
        return documentRepository.findById(id).orElse(null);
    }

    /**
     * Get accessibility report for a document
     */
    public AccessibilityReport getAccessibilityReport(Long documentId) throws IOException {
        chatDoc document = getDocumentById(documentId);
        if (document == null) {
            return null;
        }
        
        if (document.getAccessibilityReportJson() != null) {
            return objectMapper.readValue(document.getAccessibilityReportJson(), AccessibilityReport.class);
        }
        
        return null;
    }
}
