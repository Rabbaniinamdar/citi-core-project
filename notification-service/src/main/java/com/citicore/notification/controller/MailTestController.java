package com.citicore.notification.controller;

import jakarta.mail.Session;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Properties;

@RestController
public class MailTestController {

    private final JavaMailSenderImpl mailSender;

    public MailTestController(JavaMailSenderImpl mailSender) {
        this.mailSender = mailSender;
    }

    @GetMapping("/debug/mail")
    public String testMailConnection() {

        Properties props = mailSender.getJavaMailProperties();

        System.out.println("========== SMTP DEBUG ==========");
        System.out.println("Host: " + mailSender.getHost());
        System.out.println("Port: " + mailSender.getPort());
        System.out.println("Username: " + mailSender.getUsername());
        System.out.println("SMTP Auth: " + props.getProperty("mail.smtp.auth"));
        System.out.println("STARTTLS: " + props.getProperty("mail.smtp.starttls.enable"));
        System.out.println("STARTTLS Required: " +
                props.getProperty("mail.smtp.starttls.required"));
        System.out.println("SSL Trust: " +
                props.getProperty("mail.smtp.ssl.trust"));
        System.out.println("===============================");

        try {
            mailSender.testConnection();
            return "SMTP CONNECTION SUCCESS";
        } catch (Exception e) {
            e.printStackTrace();
            return "SMTP CONNECTION FAILED: " + e.getMessage();
        }
    }
}