package com.husky.spring_portfolio.mvc.chatLLM;

import java.util.List;

/**
 * Represents an image extracted from a PDF with its context
 */
public class ImageInfo {
    private int pageNumber;
    private String imageId;
    private byte[] imageBytes;
    private String imageFormat; // "PNG", "JPEG", etc.
    private double x;
    private double y;
    private double width;
    private double height;
    private String contextText; // Nearby text (caption, paragraph before/after)
    private String sectionHeading; // Document section where image appears

    public ImageInfo() {}

    public ImageInfo(int pageNumber, String imageId, byte[] imageBytes, String imageFormat,
                     double x, double y, double width, double height) {
        this.pageNumber = pageNumber;
        this.imageId = imageId;
        this.imageBytes = imageBytes;
        this.imageFormat = imageFormat;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // Getters and setters
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }

    public byte[] getImageBytes() { return imageBytes; }
    public void setImageBytes(byte[] imageBytes) { this.imageBytes = imageBytes; }

    public String getImageFormat() { return imageFormat; }
    public void setImageFormat(String imageFormat) { this.imageFormat = imageFormat; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public String getContextText() { return contextText; }
    public void setContextText(String contextText) { this.contextText = contextText; }

    public String getSectionHeading() { return sectionHeading; }
    public void setSectionHeading(String sectionHeading) { this.sectionHeading = sectionHeading; }
}
