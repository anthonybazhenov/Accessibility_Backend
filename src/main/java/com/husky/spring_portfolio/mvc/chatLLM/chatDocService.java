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
import java.util.Objects;
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
    
    /**
     * Default OpenAI model (fallback). Used for general calls and for per-image vision alt-text
     * when no separate vision override exists.
     */
    @Value("${openai.model:gpt-4o}")
    private String openaiModel;

    /**
     * Optional fine-tuned model for <strong>stage 2</strong>: original document content +
     * remediation plan → altered/accessible HTML (or full remediated document).
     * When empty, the pipeline uses deterministic {@link #createAccessibleHtmlFromPlan} instead of an LLM.
     */
    @Value("${openai.alt.model:}")
    private String openaiAltModel;

    /**
     * Optional fine-tuned model for <strong>stage 1</strong>: unaltered document context
     * (text, report, image summaries) → structured {@link RemediationPlan}.
     * When empty, falls back to {@link #openaiModel} inside {@link #modelForPlanGeneration()}.
     */
    @Value("${openai.plan.model:}")
    private String openaiPlanModel;

    /** Base model for plan generation: explicit plan fine-tune, else {@link #openaiModel}. */
    private String modelForPlanGeneration() {
        if (openaiPlanModel != null && !openaiPlanModel.isBlank()) {
            return openaiPlanModel.trim();
        }
        return openaiModel;
    }

    /**
     * Model for altered-document LLM step. Only non-empty values enable the future/proposed
     * chat completion that consumes document + plan and returns HTML.
     */
    private String modelForAlteredDocumentGeneration() {
        if (openaiAltModel == null || openaiAltModel.isBlank()) {
            return null;
        }
        return openaiAltModel.trim();
    }

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
        document.setPipelineStatus("UPLOADED");
        document.setTimestamp(LocalDateTime.now());
        document = documentRepository.save(document);

        try {
            // Step 1: Extract text and images
            document.setPipelineStatus("EXTRACTED");
            String originalContent = extractTextFromPdf(pdfBytes);
            document.setOriginalContent(originalContent);
            document = documentRepository.save(document);

            List<ImageInfo> images = extractImagesFromPdf(pdfBytes, originalContent);

            // Step 2: Generate alt text for images
            List<AltTextResult> altTextResults = new ArrayList<>();
            if (!images.isEmpty()) {
                document.setPipelineStatus("ALT_DONE");
                altTextResults = generateAltTextForImages(images);
                document.setAltTextJson(objectMapper.writeValueAsString(altTextResults));
                document = documentRepository.save(document);
            }

            // Step 3: Generate accessibility report
            AccessibilityReport report = generateAccessibilityReport(document, images);
            document.setAccessibilityReportJson(objectMapper.writeValueAsString(report));
            document.setPipelineStatus("REPORT_DONE");
            document = documentRepository.save(document);

            // Compliance label from report (heuristic). Set labelSource HEURISTIC only if not already HUMAN.
            document.setComplianceLabel(report.getErrors() > 0 ? "NONCOMPLIANT" : "COMPLIANT");
            if (document.getLabelSource() == null) {
                document.setLabelSource("HEURISTIC");
            }

            // Step 4: Remediation plan (stage 1: openai.plan.model → plan); then altered HTML
            // (stage 2: optional openai.alt.model LLM, else deterministic createAccessibleHtmlFromPlan)
            RemediationPlan plan = generateRemediationPlanWithModel(document, report, images);
            String accessibleHtml;
            if (plan != null) {
                document.setRemediationPlanJson(objectMapper.writeValueAsString(plan));
                String llmHtml = generateAlteredDocumentWithModel(
                    originalContent, images, altTextResults, plan);
                accessibleHtml = (llmHtml != null && !llmHtml.isBlank())
                    ? llmHtml
                    : createAccessibleHtmlFromPlan(originalContent, images, altTextResults, plan);
            } else {
                accessibleHtml = createAccessibleHtml(originalContent, images, altTextResults);
            }
            document.setAlteredContent(accessibleHtml);
            document.setPipelineStatus("HTML_DONE");
            document = documentRepository.save(document);

            // Outcome: NEEDS_REVIEW | REMEDIATED_WITH_WARNINGS | REMEDIATED
            int errors = report.getErrors();
            int warnings = report.getWarnings();
            String outcome;
            if (errors > 0) {
                outcome = "NEEDS_REVIEW";
            } else if (warnings > 0) {
                outcome = "REMEDIATED_WITH_WARNINGS";
            } else {
                outcome = "REMEDIATED";
            }
            document.setOutcomeStatus(outcome);
            document.setStatus(outcome);

            return documentRepository.save(document);

        } catch (Exception e) {
            document.setPipelineStatus("FAILED");
            document.setOutcomeStatus("FAILED");
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
        // Per-image alt text uses the default model (vision-capable). Plan/altered stages use
        // openai.plan.model / openai.alt.model via their own helpers.
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
     * Stage 1: Generate a {@link RemediationPlan} using {@link #modelForPlanGeneration()}
     * (from {@code openai.plan.model}, else {@code openai.model}). Returns null if not implemented or on failure.
     * When non-null, altered HTML uses {@link #generateAlteredDocumentWithModel} if {@code openai.alt.model}
     * is set, otherwise {@link #createAccessibleHtmlFromPlan}.
     */
    private RemediationPlan generateRemediationPlanWithModel(chatDoc document, AccessibilityReport report,
                                                             List<ImageInfo> images) {
        // Ensures plan-model resolution stays wired (openai.plan.model or openai.model).
        Objects.requireNonNull(modelForPlanGeneration(), "Plan generation model must resolve");
        // TODO: POST chat/completions with modelForPlanGeneration(), payload = original content + report summary +
        //       image summaries (see FineTuningPlanDataBuilder input shape); parse JSON → RemediationPlan.
        // For now return null so we use createAccessibleHtml without a stored plan.
        return null;
    }

    /**
     * Stage 2: Optional LLM that takes original document context + remediation plan and returns accessible HTML.
     * Uses {@code openai.alt.model} only when set; otherwise returns null and the caller uses
     * {@link #createAccessibleHtmlFromPlan}.
     */
    private String generateAlteredDocumentWithModel(String originalContent, List<ImageInfo> images,
                                                    List<AltTextResult> altTextResults, RemediationPlan plan) {
        String model = modelForAlteredDocumentGeneration();
        if (model == null) {
            return null;
        }
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            return null;
        }
        // TODO: Build user message with originalContent (truncated), plan JSON, altText summary; model = openai.alt.model;
        //       request full HTML; validate/sanitize; return string.
        return null;
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

    /**
     * Create accessible HTML by applying a RemediationPlan deterministically.
     * Uses plan actions for alt/longdesc/decorative/language; falls back to altTextResults.
     * Non-deterministic actions (FIX_LINK_TEXT, ADD_HEADING, etc.) are recorded in a notes section.
     */
    public String createAccessibleHtmlFromPlan(String originalContent, List<ImageInfo> images,
                                               List<AltTextResult> altTextResults, RemediationPlan plan) {
        if (plan == null) {
            return createAccessibleHtml(originalContent, images, altTextResults);
        }

        // Index actions by target (page, imageIndex) for images; collect others
        Map<String, List<RemediationAction>> actionsByImage = new HashMap<>();
        String lang = "en";
        List<RemediationAction> deferredActions = new ArrayList<>();

        List<RemediationAction> actions = plan.getActions();
        if (actions != null) {
            for (RemediationAction a : actions) {
                if (a == null) continue;
                switch (a.getAction() != null ? a.getAction() : "") {
                    case "SET_LANGUAGE":
                        if (a.getData() != null && a.getData().get("language") != null) {
                            lang = String.valueOf(a.getData().get("language"));
                        }
                        break;
                    case "ADD_ALT_TEXT":
                    case "ADD_LONGDESC":
                    case "MARK_DECORATIVE":
                        String key = imageTargetKey(a.getTarget());
                        if (key != null) {
                            actionsByImage.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
                        }
                        break;
                    case "FIX_LINK_TEXT":
                    case "ADD_HEADING":
                    case "ADD_TABLE_CAPTION":
                    case "ADD_LIST_STRUCTURE":
                    default:
                        deferredActions.add(a);
                        break;
                }
            }
        }

        Map<String, AltTextResult> altTextMap = altTextResults != null
            ? altTextResults.stream().collect(Collectors.toMap(AltTextResult::getImageId, r -> r))
            : new HashMap<>();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"").append(escapeHtml(lang)).append("\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Accessible Document</title>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <main>\n");

        // Extracted text content (unchanged)
        String[] paragraphs = originalContent != null ? originalContent.split("\n\n") : new String[0];
        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;
            if (para.length() < 100 && (Character.isDigit(para.charAt(0)) ||
                (Character.isUpperCase(para.charAt(0)) && !para.contains(".")))) {
                html.append("    <h2>").append(escapeHtml(para)).append("</h2>\n");
            } else {
                html.append("    <p>").append(escapeHtml(para)).append("</p>\n");
            }
        }

        // Images: apply ADD_ALT_TEXT, MARK_DECORATIVE, ADD_LONGDESC from plan
        Map<Integer, Integer> pageToNextIndex = new HashMap<>();
        for (ImageInfo image : images != null ? images : List.<ImageInfo>of()) {
            int page = image.getPageNumber();
            int imageIndexOnPage = pageToNextIndex.getOrDefault(page, 0);
            pageToNextIndex.put(page, imageIndexOnPage + 1);
            String targetKey = page + "_" + imageIndexOnPage;

            List<RemediationAction> imgActions = actionsByImage.get(targetKey);
            String alt = null;
            String longdesc = null;
            boolean decorative = false;
            if (imgActions != null) {
                for (RemediationAction a : imgActions) {
                    if ("ADD_ALT_TEXT".equals(a.getAction()) && a.getData() != null && a.getData().get("alt") != null) {
                        alt = String.valueOf(a.getData().get("alt"));
                    } else if ("ADD_LONGDESC".equals(a.getAction()) && a.getData() != null && a.getData().get("longdesc") != null) {
                        longdesc = String.valueOf(a.getData().get("longdesc"));
                    } else if ("MARK_DECORATIVE".equals(a.getAction())) {
                        decorative = true;
                    }
                }
            }
            if (alt == null) {
                AltTextResult at = altTextMap.get(image.getImageId());
                if (at != null && at.getAlt() != null) alt = at.getAlt();
            }
            if (longdesc == null && !decorative) {
                AltTextResult at = altTextMap.get(image.getImageId());
                if (at != null && at.getLongdesc() != null) longdesc = at.getLongdesc();
            }
            if (decorative) {
                alt = "";
            }
            if (alt == null) alt = "Image on page " + page;

            String longdescId = null;
            if (longdesc != null && !longdesc.isEmpty()) {
                longdescId = "longdesc-" + page + "-" + imageIndexOnPage;
            }

            html.append("    <figure>\n");
            html.append("      <img src=\"data:image/png;base64,")
                .append(Base64.getEncoder().encodeToString(image.getImageBytes()))
                .append("\" alt=\"").append(escapeHtml(alt)).append("\"");
            if (decorative) {
                html.append(" role=\"presentation\"");
            }
            if (longdescId != null) {
                html.append(" aria-describedby=\"").append(escapeHtml(longdescId)).append("\"");
            }
            html.append(" />\n");
            if (longdescId != null) {
                html.append("      <div id=\"").append(escapeHtml(longdescId)).append("\" class=\"longdesc\">\n");
                html.append("        <details><summary>Extended description</summary><p>").append(escapeHtml(longdesc)).append("</p></details>\n");
                html.append("      </div>\n");
            }
            html.append("    </figure>\n");
        }

        // Notes: proposed actions that could not be applied deterministically
        if (!deferredActions.isEmpty()) {
            html.append("  </main>\n");
            html.append("  <!-- Proposed FIX_LINK_TEXT / ADD_HEADING / ADD_TABLE_CAPTION / ADD_LIST_STRUCTURE could not be applied deterministically (no block/link anchors in extracted content). See remediation-notes below. -->\n");
            html.append("  <section class=\"remediation-notes\" aria-label=\"Proposed remediation notes\">\n");
            html.append("    <h2>Proposed outline / actions not applied automatically</h2>\n");
            html.append("    <p>The following actions require manual review (no deterministic block structure or link anchors in extracted content):</p>\n");
            html.append("    <ul>\n");
            for (RemediationAction a : deferredActions) {
                html.append("      <li>").append(escapeHtml(a.getAction()));
                if (a.getTarget() != null && !a.getTarget().isEmpty()) {
                    html.append(" — target: ").append(escapeHtml(a.getTarget().toString()));
                }
                if (a.getData() != null && !a.getData().isEmpty()) {
                    html.append(" — data: ").append(escapeHtml(a.getData().toString()));
                }
                html.append("</li>\n");
            }
            html.append("    </ul>\n");
            html.append("  </section>\n");
            html.append("</body>\n");
        } else {
            html.append("  </main>\n");
            html.append("</body>\n");
        }
        html.append("</html>\n");
        return html.toString();
    }

    /** Build key "page_imageIndex" from action target map for matching to images. */
    private static String imageTargetKey(Map<String, Object> target) {
        if (target == null) return null;
        Object p = target.get("page");
        Object i = target.get("imageIndex");
        if (p == null) return null;
        int page = p instanceof Number ? ((Number) p).intValue() : Integer.parseInt(String.valueOf(p));
        int idx = i instanceof Number ? ((Number) i).intValue() : (i != null ? Integer.parseInt(String.valueOf(i)) : 0);
        return page + "_" + idx;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Get all completed altered documents (uses outcomeStatus / pipelineStatus; falls back to legacy status).
     */
    public List<chatDoc> getAlteredDocuments() {
        return documentRepository.findAll().stream()
            .filter(doc -> {
                String out = doc.getOutcomeStatus();
                if (out != null && ("REMEDIATED".equals(out) || "REMEDIATED_WITH_WARNINGS".equals(out) || "NEEDS_REVIEW".equals(out)))
                    return true;
                String ps = doc.getPipelineStatus();
                if (ps != null && "HTML_DONE".equals(ps)) return true;
                String s = doc.getStatus();
                return s != null && ("REMEDIATED".equals(s) || "NEEDS_REVIEW".equals(s));
            })
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

    /**
     * Update compliance label by human (overrides heuristic).
     * @param id document id
     * @param complianceLabel COMPLIANT, NONCOMPLIANT, or UNKNOWN
     * @return updated document, or null if not found
     */
    public chatDoc updateHumanLabel(Long id, String complianceLabel) {
        chatDoc document = getDocumentById(id);
        if (document == null) return null;
        document.setComplianceLabel(complianceLabel);
        document.setLabelSource("HUMAN");
        return documentRepository.save(document);
    }
}
