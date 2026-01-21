package com.husky.spring_portfolio.mvc.frames;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.io.IOException;

@RestController
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:4000", "http://localhost:5500", "http://127.0.0.1:3000", "http://127.0.0.1:4000", "http://127.0.0.1:5500"})
public class FrameApiController {

    @Autowired
    private FrameJpaRepository frameJpaRepository;

    private final RestTemplate restTemplate;

    public FrameApiController() {
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/image")
    public String processImage(@RequestBody ImageData imageData) {
        List<List<Integer>> mnistData = convertToMNIST(imageData.getImage());
        if (mnistData != null) {
            // Get prediction from the prediction service
            String prediction = postMnistData(mnistData);

            // store MNIST data in database
            Frame frame = new Frame();
            frame.setMnistData(mnistData.toString()); // Converting list of lists to string for database storage
            frameJpaRepository.save(frame);

            // Return prediction in the response
            return "{\"prediction\": \"" + prediction + "\", \"letter\": \"" + prediction + "\"}";
        } else {
            return "{\"error\": \"Failed to process image\"}";
        }
    }

    @GetMapping("/mnist")
    public List<Frame> getMnistData() {
        return frameJpaRepository.findAll();
    }

    private List<List<Integer>> convertToMNIST(String imageData) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bis);

            BufferedImage resizedImage = new BufferedImage(28, 28, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = resizedImage.createGraphics();
            g.drawImage(image, 0, 0, 28, 28, null);
            g.dispose();

            List<List<Integer>> mnistData = new ArrayList<>();
            for (int y = 0; y < 28; y++) {
                List<Integer> row = new ArrayList<>();
                for (int x = 0; x < 28; x++) {
                    int pixel = resizedImage.getRGB(x, y) & 0xFF;
                    row.add(pixel);
                }
                mnistData.add(row);
            }
            return mnistData;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String postMnistData(List<List<Integer>> mnistData) {
        String url = "http://localhost:8085/smnist";
        String prediction = restTemplate.postForObject(url, mnistData, String.class);
        return prediction != null ? prediction : "";
    }

    public static class ImageData {
        private String image;
        private int label;

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public int getLabel() {
            return label;
        }

        public void setLabel(int label) {
            this.label = label;
        }
    }

    // Assuming Frame entity and FrameJpaRepository are defined elsewhere in your code
}