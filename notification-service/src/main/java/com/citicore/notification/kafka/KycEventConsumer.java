package com.citicore.notification.kafka;

import com.citicore.events.kyc.KycEvent;
import com.citicore.notification.service.EmailService;
import com.citicore.notification.template.KycEmailTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class KycEventConsumer {

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public KycEventConsumer(ObjectMapper objectMapper, EmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    /**
     * Consumes KYC status change events from kyc-topic.
     *
     * Published by: user-service when KYC is approved or rejected.
     * Action: Send KYC status update email (approved = green, rejected = red).
     */
    @KafkaListener(
            topics = "kyc-topic",
            groupId = "notification-group"
    )
    public void handleKyc(String payload, Acknowledgment ack) {

        KycEvent event;
        try {
            event = objectMapper.readValue(payload, KycEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [KYC] Parse failed: " + e.getMessage());
            ack.acknowledge();
            return;
        }

        System.out.println("📥 [KYC] email=" + event.getEmail()
                + " | status=" + event.getStatus());

        try {
            String subject = "APPROVED".equalsIgnoreCase(event.getStatus())
                    ? "CitiCore — KYC Approved ✅"
                    : "CitiCore — KYC Rejected ❌";

            emailService.sendHtml(
                    event.getEmail(),
                    subject,
                    KycEmailTemplate.kycTemplate(event.getEmail(), event.getStatus())
            );

            ack.acknowledge();

        } catch (Exception e) {
            System.out.println("❌ [KYC] Email failed: " + e.getMessage());
            // No ack → retry → DLT
        }
    }
}