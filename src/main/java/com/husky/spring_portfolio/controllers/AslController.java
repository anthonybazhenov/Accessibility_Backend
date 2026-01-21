package com.husky.spring_portfolio.controllers;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
public class AslController {

    // Serve the ASL recognition frontend at /asl
    @GetMapping(value = "/asl", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> asl() {
        try {
            Resource resource = new ClassPathResource("static/index.html");
            String html = new String(Files.readAllBytes(Paths.get(resource.getURI())));
            return ResponseEntity.ok(html);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
