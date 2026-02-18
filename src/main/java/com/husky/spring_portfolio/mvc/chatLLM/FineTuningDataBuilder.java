package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.cos.COSName;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds OpenAI vision fine-tuning JSONL from non-compliant PDFs and a labels file.
 * Run from project root: mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningDataBuilder"
 * Or with template generation: mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningDataBuilder" -Dexec.args="--template-only"
 */
public class FineTuningDataBuilder {

    private static final String TRAINING_DIR = "training";
    private static final String NON_COMPLIANT_DIR = "non_compliant";
    private static final String LABELS_FILE = "labels.json";
    private static final String LABELS_TEMPLATE_FILE = "labels_template.json";
    private static final String OUTPUT_JSONL = "openai_training.jsonl";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final int MAX_CONTEXT_LENGTH = 1500;
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024; // 8 MB to stay under 10 MB

    public static void main(String[] args) throws IOException {
        boolean templateOnly = args.length > 0 && "--template-only".equals(args[0]);
        Path trainingPath = Paths.get(TRAINING_DIR);
        Path nonCompliantPath = trainingPath.resolve(NON_COMPLIANT_DIR);

        if (!Files.isDirectory(nonCompliantPath)) {
            System.err.println("Missing folder: " + nonCompliantPath.toAbsolutePath());
            System.err.println("Create it and put your non-compliant PDFs inside.");
            System.exit(1);
        }

        List<Path> pdfs = Files.list(nonCompliantPath)
            .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
            .sorted()
            .toList();

        if (pdfs.isEmpty()) {
            System.err.println("No PDF files found in " + nonCompliantPath.toAbsolutePath());
            System.exit(1);
        }

        List<Map<String, Object>> allLabels = new ArrayList<>();
        List<TrainingExample> examples = new ArrayList<>();

        for (Path pdfPath : pdfs) {
            String filename = pdfPath.getFileName().toString();
            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            List<ImageWithContext> images = extractImagesWithContext(pdfBytes);
            for (int i = 0; i < images.size(); i++) {
                ImageWithContext img = images.get(i);
                if (templateOnly) {
                    Map<String, Object> label = new LinkedHashMap<>();
                    label.put("filename", filename);
                    label.put("page", img.pageNumber);
                    label.put("imageIndex", i);
                    label.put("alt", "");
                    label.put("longdesc", "");
                    label.put("decorative", false);
                    allLabels.add(label);
                } else {
                    examples.add(new TrainingExample(filename, img.pageNumber, i, img.contextText, img.imageBytesPng, img.imageId));
                }
            }
        }

        if (templateOnly) {
            Path out = trainingPath.resolve(LABELS_TEMPLATE_FILE);
            MAPPER.writeValue(out.toFile(), allLabels);
            System.out.println("Wrote " + out.toAbsolutePath());
            System.out.println("Fill in 'alt' and 'longdesc' (and 'decorative' if needed) for each image, then save as " + LABELS_FILE + " and re-run without --template-only.");
            return;
        }

        Path labelsPath = trainingPath.resolve(LABELS_FILE);
        if (!Files.isRegularFile(labelsPath)) {
            System.err.println("Missing " + labelsPath.toAbsolutePath());
            System.err.println("Run with --template-only first to generate " + LABELS_TEMPLATE_FILE + ", fill it in, save as " + LABELS_FILE + ", then run again.");
            System.exit(1);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> labels = MAPPER.readValue(labelsPath.toFile(), List.class);
        Map<String, Map<String, Object>> labelMap = new HashMap<>();
        for (Map<String, Object> L : labels) {
            String key = key((String) L.get("filename"), ((Number) L.get("page")).intValue(), ((Number) L.get("imageIndex")).intValue());
            labelMap.put(key, L);
        }

        Path outputPath = trainingPath.resolve(OUTPUT_JSONL);
        int written = 0;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8))) {
            for (TrainingExample ex : examples) {
                String key = key(ex.filename, ex.page, ex.imageIndex);
                Map<String, Object> label = labelMap.get(key);
                if (label == null) continue;
                String alt = stringVal(label, "alt");
                String longdesc = stringVal(label, "longdesc");
                boolean decorative = Boolean.TRUE.equals(label.get("decorative"));
                if (alt == null) alt = "";
                if (longdesc == null) longdesc = "";

                String userPrompt = buildPrompt(ex.imageId, ex.page, ex.contextText);
                String assistantContent = buildAssistantJson(decorative, alt, longdesc, ex.imageId);
                Map<String, Object> line = buildOneJsonlLine(userPrompt, ex.imageBytesPng, assistantContent);
                w.println(MAPPER.writeValueAsString(line));
                written++;
            }
        }
        System.out.println("Wrote " + written + " examples to " + outputPath.toAbsolutePath());
    }

    private static String key(String filename, int page, int imageIndex) {
        return filename + "|" + page + "|" + imageIndex;
    }

    private static String stringVal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    private static String buildPrompt(String imageId, int pageNumber, String contextText) {
        String ctx = contextText != null ? contextText.substring(0, Math.min(MAX_CONTEXT_LENGTH, contextText.length())) : "No surrounding text.";
        return "You are an accessibility expert. For this image from a PDF (page " + pageNumber + "), "
            + "provide alt text. Use the surrounding context to describe the image accurately. "
            + "Never repeat text that is already in the context. "
            + "Respond with ONLY a single JSON object, no other text, with these exact keys: "
            + "\"decorative\" (boolean), \"alt\" (string, short 1-sentence description), "
            + "\"longdesc\" (string, extended description for charts/diagrams or empty string), "
            + "\"confidence\" (number 0-1), \"needs_human_review\" (boolean). "
            + "If the image is purely decorative, set decorative to true and alt to empty string. "
            + "Context from document:\n" + ctx;
    }

    private static String buildAssistantJson(boolean decorative, String alt, String longdesc, String imageId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decorative", decorative);
        m.put("alt", alt);
        m.put("longdesc", longdesc);
        m.put("confidence", 1.0);
        m.put("needs_human_review", false);
        m.put("image_id", imageId);
        try {
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildOneJsonlLine(String userPrompt, byte[] imagePngBytes, String assistantContent) {
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(imagePngBytes);
        Map<String, Object> userContent1 = Map.of("type", "text", "text", userPrompt);
        Map<String, Object> userContent2 = Map.of("type", "image_url", "image_url", Map.of("url", dataUrl));
        Map<String, Object> userMsg = Map.of(
            "role", "user",
            "content", List.of(userContent1, userContent2)
        );
        Map<String, Object> assistantMsg = Map.of("role", "assistant", "content", assistantContent);
        return Map.of("messages", List.of(userMsg, assistantMsg));
    }

    private static List<ImageWithContext> extractImagesWithContext(byte[] pdfBytes) throws IOException {
        List<ImageWithContext> list = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pageNum = 0;
            for (PDPage page : doc.getPages()) {
                pageNum++;
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(doc);
                PDResources resources = page.getResources();
                int imageIndex = 0;
                for (COSName name : resources.getXObjectNames()) {
                    PDXObject xo = resources.getXObject(name);
                    if (!(xo instanceof PDImageXObject)) continue;
                    PDImageXObject img = (PDImageXObject) xo;
                    try {
                        BufferedImage bi = img.getImage();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(bi, "PNG", baos);
                        byte[] png = baos.toByteArray();
                        if (png.length > MAX_IMAGE_BYTES) continue;
                        String imageId = "img_" + pageNum + "_" + imageIndex;
                        list.add(new ImageWithContext(pageNum, imageId, pageText, png));
                        imageIndex++;
                    } catch (Exception e) {
                        // skip
                    }
                }
            }
        }
        return list;
    }

    private static class ImageWithContext {
        final int pageNumber;
        final String imageId;
        final String contextText;
        final byte[] imageBytesPng;

        ImageWithContext(int pageNumber, String imageId, String contextText, byte[] imageBytesPng) {
            this.pageNumber = pageNumber;
            this.imageId = imageId;
            this.contextText = contextText;
            this.imageBytesPng = imageBytesPng;
        }
    }

    private static class TrainingExample {
        final String filename;
        final int page;
        final int imageIndex;
        final String contextText;
        final byte[] imageBytesPng;
        final String imageId;

        TrainingExample(String filename, int page, int imageIndex, String contextText, byte[] imageBytesPng, String imageId) {
            this.filename = filename;
            this.page = page;
            this.imageIndex = imageIndex;
            this.contextText = contextText;
            this.imageBytesPng = imageBytesPng;
            this.imageId = imageId;
        }
    }
}
