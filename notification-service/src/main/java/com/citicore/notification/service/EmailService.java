package com.citicore.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an HTML email.
     *
     * @param to        recipient email address
     * @param subject   email subject line
     * @param htmlBody  full HTML body content
     * @throws RuntimeException if sending fails (caller decides whether to ack Kafka offset)
     */
    public void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            // true = multipart, "UTF-8" = encoding
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("noreply@citicore.com");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = isHtml

            mailSender.send(message);

            System.out.println("✅ [EMAIL SENT] to=" + to + " | subject=" + subject);

        } catch (MessagingException e) {
            System.out.println("❌ [EMAIL FAILED] to=" + to + " | error=" + e.getMessage());
            // Rethrow as RuntimeException — caller (Kafka listener) will NOT ack
            // the offset, so Kafka redelivers the message and email is retried
            throw new RuntimeException("Failed to send email to: " + to, e);
        }
    }
}