package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Schema and documented structure for RemediationPlan (for validation / parsing).
 */
public final class RemediationPlanSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private RemediationPlanSchema() {}

    /**
     * Returns the JSON Schema for RemediationPlan as a Map (can be serialized to JSON).
     */
    public static Map<String, Object> getJsonSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("title", "RemediationPlan");
        schema.put("type", "object");
        schema.put("required", List.of("target", "compliance"));
        schema.put("properties", Map.of(
            "target", Map.of(
                "type", "string",
                "description", "Output target; use 'HTML' for now.",
                "enum", List.of("HTML")
            ),
            "compliance", Map.of(
                "type", "string",
                "description", "Proposed compliance: COMPLIANT or NONCOMPLIANT.",
                "enum", List.of("COMPLIANT", "NONCOMPLIANT")
            ),
            "issues", Map.of(
                "type", "array",
                "description", "List of issues to remediate.",
                "items", getRemediationIssueSchema()
            ),
            "actions", Map.of(
                "type", "array",
                "description", "List of concrete remediation actions.",
                "items", getRemediationActionSchema()
            )
        ));
        return schema;
    }

    private static Map<String, Object> getRemediationIssueSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", Map.of(
            "successCriteria", Map.of("type", "string", "description", "e.g. 1.1.1"),
            "severity", Map.of("type", "string", "enum", List.of("error", "warning")),
            "evidence", Map.of("type", "string"),
            "fixSteps", Map.of("type", "array", "items", Map.of("type", "string"))
        ));
        return item;
    }

    private static Map<String, Object> getRemediationActionSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("required", List.of("action"));
        item.put("properties", Map.of(
            "action", Map.of(
                "type", "string",
                "enum", List.of(
                    "ADD_ALT_TEXT", "ADD_LONGDESC", "SET_LANGUAGE", "FIX_LINK_TEXT",
                    "ADD_HEADING", "MARK_DECORATIVE", "ADD_TABLE_CAPTION", "ADD_LIST_STRUCTURE"
                )
            ),
            "target", Map.of(
                "type", "object",
                "description", "Identifies where: page, imageIndex, blockId, etc."
            ),
            "data", Map.of(
                "type", "object",
                "description", "Payload: alt, longdesc, language, text, etc."
            )
        ));
        return item;
    }

    /**
     * Returns the JSON Schema as a JSON string.
     */
    public static String getJsonSchemaString() {
        try {
            return MAPPER.writeValueAsString(getJsonSchema());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize schema", e);
        }
    }

    /**
     * Documented expected structure (for prompts or docs).
     */
    public static String getDocumentedStructure() {
        return """
            RemediationPlan (JSON):
              - target (string): "HTML"
              - compliance (string): "COMPLIANT" | "NONCOMPLIANT"
              - issues (array of RemediationIssue):
                  - successCriteria (string): e.g. "1.1.1"
                  - severity (string): "error" | "warning"
                  - evidence (string)
                  - fixSteps (array of string)
              - actions (array of RemediationAction):
                  - action (string): ADD_ALT_TEXT | ADD_LONGDESC | SET_LANGUAGE | FIX_LINK_TEXT | ADD_HEADING | MARK_DECORATIVE | ADD_TABLE_CAPTION | ADD_LIST_STRUCTURE
                  - target (object): e.g. { "page": 1, "imageIndex": 0 } or { "blockId": "..." }
                  - data (object): e.g. { "alt": "..." } or { "longdesc": "..." } or { "language": "en" }
            """;
    }
}
