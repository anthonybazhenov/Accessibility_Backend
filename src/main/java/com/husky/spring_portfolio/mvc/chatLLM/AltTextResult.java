package com.husky.spring_portfolio.mvc.chatLLM;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the alt text result for an image from OpenAI API
 */
public class AltTextResult {
    @JsonProperty("decorative")
    private boolean decorative;
    
    @JsonProperty("alt")
    private String alt; // Short alt text (1 sentence)
    
    @JsonProperty("longdesc")
    private String longdesc; // Extended description for charts/diagrams
    
    @JsonProperty("confidence")
    private double confidence; // 0.0 to 1.0
    
    @JsonProperty("needs_human_review")
    private boolean needsHumanReview;
    
    @JsonProperty("image_id")
    private String imageId;

    public AltTextResult() {}

    public AltTextResult(boolean decorative, String alt, String longdesc, double confidence, 
                        boolean needsHumanReview, String imageId) {
        this.decorative = decorative;
        this.alt = alt;
        this.longdesc = longdesc;
        this.confidence = confidence;
        this.needsHumanReview = needsHumanReview;
        this.imageId = imageId;
    }

    // Getters and setters
    public boolean isDecorative() { return decorative; }
    public void setDecorative(boolean decorative) { this.decorative = decorative; }

    public String getAlt() { return alt; }
    public void setAlt(String alt) { this.alt = alt; }

    public String getLongdesc() { return longdesc; }
    public void setLongdesc(String longdesc) { this.longdesc = longdesc; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public boolean isNeedsHumanReview() { return needsHumanReview; }
    public void setNeedsHumanReview(boolean needsHumanReview) { this.needsHumanReview = needsHumanReview; }

    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
}
