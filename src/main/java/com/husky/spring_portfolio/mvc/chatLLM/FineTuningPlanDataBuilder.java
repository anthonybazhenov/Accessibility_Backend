package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

/**
 * Exports training data for remediation-plan fine-tuning from the DB.
 * Reads docs where labelSource=HUMAN and complianceLabel is set; writes training/openai_plan_training.jsonl.
 *
 * Run from project root:
 *   mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningPlanDataBuilder"
 *   mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningPlanDataBuilder" -Dexec.args="--template-only 1"
 *
 * DB URL: TRAINING_DB_URL env or default jdbc:sqlite:volumes/sqlite.db
 */
public class FineTuningPlanDataBuilder {

    private static final String TRAINING_DIR = "training";
    private static final String OUTPUT_JSONL = "openai_plan_training.jsonl";
    private static final String PLAN_TEMPLATE_PREFIX = "plan_template_";
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    private static final int MAX_ORIGINAL_CONTENT_LENGTH = 12_000;

    public static void main(String[] args) throws Exception {
        String dbUrl = System.getenv("TRAINING_DB_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:sqlite:volumes/sqlite.db";
        }

        boolean templateOnly = args.length >= 2 && "--template-only".equals(args[0]);
        Long templateDocId = templateOnly ? Long.parseLong(args[1]) : null;

        Path trainingPath = Paths.get(TRAINING_DIR);
        Files.createDirectories(trainingPath);

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            if (templateOnly) {
                exportTemplate(conn, trainingPath, templateDocId);
            } else {
                exportTrainingJsonl(conn, trainingPath);
            }
        }
    }

    private static void exportTemplate(Connection conn, Path trainingPath, long docId) throws Exception {
        Map<String, Object> row = findDocById(conn, docId);
        if (row == null) {
            System.err.println("Document not found: " + docId);
            System.exit(1);
        }
        Map<String, Object> inputPayload = buildInputPayload(row);
        Map<String, Object> emptyPlan = new LinkedHashMap<>();
        emptyPlan.put("target", "HTML");
        emptyPlan.put("compliance", "");
        emptyPlan.put("issues", new ArrayList<Map<String, Object>>());
        emptyPlan.put("actions", new ArrayList<Map<String, Object>>());

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("documentId", docId);
        template.put("inputPayload", inputPayload);
        template.put("remediationPlan", emptyPlan);
        template.put("_instructions", "Fill in remediationPlan (issues and actions); then use as assistant output for training.");

        Path out = trainingPath.resolve(PLAN_TEMPLATE_PREFIX + docId + ".json");
        MAPPER.writeValue(out.toFile(), template);
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    private static void exportTrainingJsonl(Connection conn, Path trainingPath) throws Exception {
        List<Map<String, Object>> rows = findHumanLabeledDocsWithPlan(conn);
        if (rows.isEmpty()) {
            System.out.println("No documents with labelSource=HUMAN, complianceLabel set, and remediation_plan_json present.");
            return;
        }

        Path outputPath = trainingPath.resolve(OUTPUT_JSONL);
        int written = 0;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8))) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> inputPayload = buildInputPayload(row);
                String userContent = MAPPER.writeValueAsString(inputPayload);
                String assistantContent = (String) row.get("remediation_plan_json");
                if (assistantContent == null || assistantContent.trim().isEmpty()) continue;

                Map<String, Object> userMsg = Map.of("role", "user", "content", userContent);
                Map<String, Object> assistantMsg = Map.of("role", "assistant", "content", assistantContent);
                Map<String, Object> line = Map.of("messages", List.of(userMsg, assistantMsg));
                w.println(MAPPER.writeValueAsString(line));
                written++;
            }
        }
        System.out.println("Wrote " + written + " examples to " + outputPath.toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildInputPayload(Map<String, Object> row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String originalContent = (String) row.get("original_content");
        payload.put("originalContent", truncate(originalContent, MAX_ORIGINAL_CONTENT_LENGTH));
        payload.put("originalFilename", row.get("original_filename"));

        String reportJson = (String) row.get("accessibility_report_json");
        if (reportJson != null && !reportJson.isEmpty()) {
            try {
                Map<String, Object> report = MAPPER.readValue(reportJson, Map.class);
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("errors", report.getOrDefault("errors", 0));
                summary.put("warnings", report.getOrDefault("warnings", 0));
                summary.put("total_issues", report.getOrDefault("total_issues", 0));
                payload.put("reportSummary", summary);
            } catch (Exception e) {
                payload.put("reportSummary", Map.of("errors", 0, "warnings", 0, "total_issues", 0));
            }
        } else {
            payload.put("reportSummary", Map.of("errors", 0, "warnings", 0, "total_issues", 0));
        }

        String altTextJson = (String) row.get("alt_text_json");
        if (altTextJson != null && !altTextJson.isEmpty()) {
            try {
                List<Map<String, Object>> altList = MAPPER.readValue(altTextJson, List.class);
                List<Map<String, Object>> imagesSummary = new ArrayList<>();
                for (Map<String, Object> a : altList) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    if (a.containsKey("image_id")) m.put("imageId", a.get("image_id"));
                    imagesSummary.add(m);
                }
                payload.put("imagesSummary", imagesSummary);
            } catch (Exception e) {
                payload.put("imagesSummary", List.of());
            }
        } else {
            payload.put("imagesSummary", List.of());
        }

        String auditJson = (String) row.get("audit_json");
        if (auditJson != null && !auditJson.trim().isEmpty()) {
            payload.put("auditJson", auditJson);
        }

        return payload;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "\n...[truncated]";
    }

    private static Map<String, Object> findDocById(Connection conn, long id) throws SQLException {
        String sql = "SELECT id, original_filename, original_content, accessibility_report_json, alt_text_json, audit_json, remediation_plan_json " +
            "FROM chat_doc WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rowToMap(rs);
            }
        }
        return null;
    }

    private static List<Map<String, Object>> findHumanLabeledDocsWithPlan(Connection conn) throws SQLException {
        String sql = "SELECT id, original_filename, original_content, accessibility_report_json, alt_text_json, audit_json, remediation_plan_json " +
            "FROM chat_doc WHERE label_source = ? AND compliance_label IS NOT NULL AND compliance_label != '' " +
            "AND remediation_plan_json IS NOT NULL AND remediation_plan_json != ''";
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "HUMAN");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToMap(rs));
            }
        }
        return list;
    }

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getObject("id"));
        m.put("original_filename", rs.getString("original_filename"));
        m.put("original_content", rs.getString("original_content"));
        m.put("accessibility_report_json", rs.getString("accessibility_report_json"));
        m.put("alt_text_json", rs.getString("alt_text_json"));
        m.put("audit_json", rs.getString("audit_json"));
        m.put("remediation_plan_json", rs.getString("remediation_plan_json"));
        return m;
    }
}
