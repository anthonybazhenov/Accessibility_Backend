package com.husky.spring_portfolio.mvc.person;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class AccountEmailService {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.frontend.base-url:http://localhost:5500}")
    private String frontendBaseUrl;

    @Value("${app.mail.from:noreply@localhost}")
    private String mailFrom;

    public static String newSecureToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public void sendPasswordResetEmail(Person person, String token) {
        String link = trimBase(frontendBaseUrl) + "/reset-password.html?token=" + encodeTokenForQuery(token);
        String subject = "Reset your password";
        String body = ""
                + "<p>Hi " + escapeHtml(person.getName()) + ",</p>"
                + "<p>We received a request to reset your password. Open this link to choose a new password:</p>"
                + "<p><a href=\"" + link + "\">Reset password</a></p>"
                + "<p>This link expires in one hour. If you did not request a reset, ignore this email.</p>";
        sendHtml(person.getEmail(), subject, body);
    }

    private static String encodeTokenForQuery(String token) {
        return java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String trimBase(String base) {
        if (base == null || base.isBlank()) {
            return "http://localhost:5500";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void sendHtml(String to, String subject, String htmlBody) {
        if (mailSender == null) {
            log.warn("Mail is not configured (spring.mail.host missing). Would send to {} subject \"{}\"", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Sent email to {} subject \"{}\"", to, subject);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
