package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * A single remediation action to apply (e.g. ADD_ALT_TEXT, ADD_LONGDESC).
 * target: identifies where (page, imageIndex, blockId, etc.).
 * data: payload (alt, longdesc, language, text, etc.).
 */
public class RemediationAction {

    @JsonProperty("action")
    private String action; // ADD_ALT_TEXT, ADD_LONGDESC, SET_LANGUAGE, FIX_LINK_TEXT, ADD_HEADING, MARK_DECORATIVE, ADD_TABLE_CAPTION, ADD_LIST_STRUCTURE

    @JsonProperty("target")
    private Map<String, Object> target; // page, imageIndex, blockId, etc.

    @JsonProperty("data")
    private Map<String, Object> data;   // alt, longdesc, language, text, etc.

    public RemediationAction() {}

    public RemediationAction(String action, Map<String, Object> target, Map<String, Object> data) {
        this.action = action;
        this.target = target;
        this.data = data;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getTarget() { return target; }
    public void setTarget(Map<String, Object> target) { this.target = target; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
