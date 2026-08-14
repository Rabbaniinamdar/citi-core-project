package com.citicore.notification.kafka;

import com.citicore.events.otp.VerificationOtpEvent;
import com.citicore.notification.service.EmailService;
import com.citicore.notification.template.OtpEmailTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class OtpEventConsumer {

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public OtpEventConsumer(ObjectMapper objectMapper, EmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    /**
     * Consumes OTP events from otp-topic.
     *
     * Published by: auth-service when a user registers or requests OTP verification.
     * Action: Send OTP email with the one-time password.
     *
     * Critical: OTP emails must be delivered quickly.
     * If email fails, message is NOT acked → retried with backoff.
     * After max retries → routed to otp-topic.DLT.
     */
    @KafkaListener(
            topics = "otp-topic",
            groupId = "notification-group"
    )
    public void handleOtp(String payload, Acknowledgment ack) {

        VerificationOtpEvent event;
        try {
            event = objectMapper.readValue(payload, VerificationOtpEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [OTP] Parse failed: " + e.getMessage());
            ack.acknowledge(); // bad data — skip
            return;
        }

        System.out.println("📥 [OTP] email=" + event.getEmail());

        try {
            emailService.sendHtml(
                    event.getEmail(),
                    "CitiCore — Your Verification OTP",
                    OtpEmailTemplate.otpTemplate(event.getEmail(), event.getOtp())
            );

            ack.acknowledge(); // ✅ offset committed only after email delivered

        } catch (Exception e) {
            System.out.println("❌ [OTP] Email failed: " + e.getMessage());
            // No ack → retry → DLT
        }
    }
}